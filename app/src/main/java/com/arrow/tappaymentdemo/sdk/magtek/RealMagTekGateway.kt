package com.arrow.tappaymentdemo.sdk.magtek

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.arrow.tappaymentdemo.core.result.AppError
import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.ReaderConnectionState
import com.arrow.tappaymentdemo.domain.model.ReaderDevice
import com.arrow.tappaymentdemo.domain.model.SecurePaymentData
import com.arrow.tappaymentdemo.domain.model.TransactionState
import com.magtek.mobile.android.mtusdk.CoreAPI
import com.magtek.mobile.android.mtusdk.DeviceType
import com.magtek.mobile.android.mtusdk.EventType
import com.magtek.mobile.android.mtusdk.IDevice
import com.magtek.mobile.android.mtusdk.IDeviceListCallback
import com.magtek.mobile.android.mtusdk.IEventSubscriber
import com.magtek.mobile.android.mtusdk.PaymentMethod
import com.magtek.mobile.android.mtusdk.Transaction
import java.math.RoundingMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class RealMagTekGateway(
    private val appContext: Context,
    private val callbackMapper: MagTekCallbackToTransactionStateMapper
) : MagTekGateway {

    private val gatewayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    private val mutableReaderConnectionState = MutableStateFlow<ReaderConnectionState>(ReaderConnectionState.Idle)
    override val readerConnectionState: StateFlow<ReaderConnectionState> = mutableReaderConnectionState.asStateFlow()

    private val mutableDiscoveredDevices = MutableStateFlow<List<ReaderDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<ReaderDevice>> = mutableDiscoveredDevices.asStateFlow()

    private var activeDevice: IDevice? = null
    private var activeSubscriber: IEventSubscriber? = null
    private val deviceMap = mutableMapOf<String, IDevice>()
    private var discoverySessionId: Long = 0L

    // Auto-reconnect state
    private var isAutoReconnecting = false
    private var autoReconnectTimeout: Job? = null
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("magtek_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val SAVED_DEVICE_ID_KEY = "saved_magtek_device_id"
        private const val AUTO_RECONNECT_TIMEOUT_MS = 15000L
        private const val DISCOVERY_TIMEOUT_MS = 15000
        private const val TERMINAL_TRANSACTION_TIMEOUT_SECONDS: Byte = 60
        private const val APP_AUTHORIZATION_WAIT_BUFFER_MS = 15000L
    }

    init {
        Timber.d("🔌 MagTek Gateway initialized - waiting for Bluetooth/permissions before discovery")
    }

    // MARK: - Auto-Reconnect

    fun startAutoReconnect() {
        val savedDeviceId = getSavedDeviceId()
        if (savedDeviceId == null || isAutoReconnecting || mutableReaderConnectionState.value.isConnected) {
            Timber.d("🔁 Auto-reconnect skipped: savedId=$savedDeviceId, autoRecon=$isAutoReconnecting, connected=${mutableReaderConnectionState.value.isConnected}")
            return
        }

        val discoveryError = validateDiscoveryPrerequisites()
        if (discoveryError != null) {
            Timber.d("🔁 Auto-reconnect deferred until prerequisites are met: $discoveryError")
            return
        }

        Timber.d("🔁 Starting auto-reconnect for device: $savedDeviceId")
        isAutoReconnecting = true
        mutableReaderConnectionState.value = ReaderConnectionState.Scanning

        // Cancel any existing timeout
        autoReconnectTimeout?.cancel()

        // Start discovery
        startDiscovery()

        // Set timeout - if device not found in time, stop scanning
        autoReconnectTimeout = gatewayScope.launch {
            try {
                delay(AUTO_RECONNECT_TIMEOUT_MS)
                if (isAutoReconnecting) {
                    Timber.d("🔁 Auto-reconnect timeout - device not found")
                    isAutoReconnecting = false
                    stopDiscovery()
                    if (mutableReaderConnectionState.value is ReaderConnectionState.Scanning) {
                        mutableReaderConnectionState.value = ReaderConnectionState.Idle
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Auto-reconnect timeout error")
            }
        }
    }

    private fun getSavedDeviceId(): String? {
        val saved = prefs.getString(SAVED_DEVICE_ID_KEY, null)
        return if (saved.isNullOrEmpty()) null else saved
    }

    private fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(SAVED_DEVICE_ID_KEY, deviceId).apply()
        Timber.d("💾 Saved device ID: $deviceId")
    }

    private fun clearSavedDeviceId() {
        prefs.edit().remove(SAVED_DEVICE_ID_KEY).apply()
        Timber.d("🗑️ Cleared saved device ID")
    }

    // MARK: - Device Discovery

    override fun startDiscovery() {
        val discoveryError = validateDiscoveryPrerequisites()
        if (discoveryError != null) {
            Timber.w("⚠️ Discovery prerequisites not met: $discoveryError")
            mutableReaderConnectionState.value = ReaderConnectionState.Error(discoveryError)
            mutableConnectionState.value = ConnectionState.Error(discoveryError)
            isAutoReconnecting = false
            autoReconnectTimeout?.cancel()
            return
        }

        Timber.d("🔍 Starting device discovery")
        val sessionId = ++discoverySessionId
        mutableReaderConnectionState.value = ReaderConnectionState.Scanning
        mutableDiscoveredDevices.value = emptyList()
        deviceMap.clear()

        val callback = IDeviceListCallback { deviceList ->
            if (sessionId != discoverySessionId) {
                Timber.d("🔍 Ignoring stale discovery callback for session $sessionId")
                return@IDeviceListCallback
            }
            Timber.d("📡 Device discovery callback: found ${deviceList?.size ?: 0} devices")
            publishDiscoveredDevices(deviceList.orEmpty())
        }

        try {
            val deviceTypes = DeviceType.values().toList()
            val initialDevices = CoreAPI.getDeviceList(
                appContext,
                deviceTypes,
                callback,
                DISCOVERY_TIMEOUT_MS
            )
            Timber.d("📡 Initial discovery list size=${initialDevices.size} for session=$sessionId")
            publishDiscoveredDevices(initialDevices)
        } catch (securityException: SecurityException) {
            val message = "Missing nearby devices permission for discovery"
            Timber.e(securityException, "❌ $message")
            mutableReaderConnectionState.value = ReaderConnectionState.Error(message)
            mutableConnectionState.value = ConnectionState.Error(message)
            isAutoReconnecting = false
            autoReconnectTimeout?.cancel()
        } catch (exception: Exception) {
            val message = "Failed to start device discovery"
            Timber.e(exception, "❌ $message")
            mutableReaderConnectionState.value = ReaderConnectionState.Error(message)
            mutableConnectionState.value = ConnectionState.Error(message)
            isAutoReconnecting = false
            autoReconnectTimeout?.cancel()
        }
    }

    private fun validateDiscoveryPrerequisites(): String? {
        val hasRequiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasRequiredPermissions) {
            return "Required Bluetooth permissions are not granted"
        }

        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            return "Bluetooth adapter unavailable on this device"
        }
        if (!adapter.isEnabled) {
            return "Bluetooth is turned off"
        }

        return null
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun publishDiscoveredDevices(devices: List<IDevice>) {
        val discoveredReaders = devices.mapIndexed { index, device ->
            val readerDevice = mapReaderDevice(device, index)
            deviceMap[readerDevice.id] = device
            readerDevice
        }
            .distinctBy { it.id }
            .sortedByDescending { it.isLikelyReader }

        mutableDiscoveredDevices.value = discoveredReaders
        Timber.d("📡 Discovered devices: ${discoveredReaders.map { it.name }}")

        val savedDeviceId = getSavedDeviceId()
        if (isAutoReconnecting && savedDeviceId != null) {
            discoveredReaders.firstOrNull { it.id == savedDeviceId }?.let { savedDevice ->
                Timber.d("🔁 Found saved device during auto-reconnect: ${savedDevice.name}")
                autoReconnectTimeout?.cancel()
                isAutoReconnecting = false
                connectToDevice(savedDevice)
            }
        }
    }

    private fun mapReaderDevice(device: IDevice, index: Int): ReaderDevice {
        val deviceName = safeGetDeviceName(device, index)
        val deviceId = safeGetDeviceId(device, deviceName)
        return ReaderDevice(id = deviceId, name = deviceName)
    }

    private fun safeGetDeviceName(device: IDevice): String {
        return try {
            listOf(
                device.Name(),
                device.getDeviceInfo()?.getName(),
                device.getDeviceInfo()?.getModel(),
                device.getConnectionInfo()?.getAddress()
            ).firstOrNull { !it.isNullOrBlank() } ?: "Unknown Device"
        } catch (e: Exception) {
            Timber.w("Failed to get device name: ${e.message}")
            "Device-${System.currentTimeMillis() % 1000}"
        }
    }

    private fun safeGetDeviceName(device: IDevice, index: Int): String {
        return safeGetDeviceName(device).ifBlank { "Device-$index" }
    }

    private fun safeGetDeviceId(device: IDevice, fallbackName: String): String {
        return try {
            listOf(
                device.getConnectionInfo()?.getAddress(),
                device.getDeviceInfo()?.getSerial(),
                device.Name(),
                fallbackName
            ).firstOrNull { !it.isNullOrBlank() } ?: fallbackName
        } catch (e: Exception) {
            Timber.w("Failed to get device ID: ${e.message}")
            fallbackName
        }
    }

    override fun stopDiscovery() {
        Timber.d("🔍 Stopped device discovery")
        discoverySessionId++
        isAutoReconnecting = false
        autoReconnectTimeout?.cancel()
        if (mutableReaderConnectionState.value is ReaderConnectionState.Scanning) {
            mutableReaderConnectionState.value = ReaderConnectionState.Idle
        }
    }

    override fun connectToDevice(device: ReaderDevice) {
        Timber.d("🔗 Connecting to device: ${device.name}")
        val idevice = deviceMap[device.id]
        if (idevice == null) {
            Timber.e("❌ Device not found in map: ${device.id}")
            mutableReaderConnectionState.value = ReaderConnectionState.Error("Device not found")
            return
        }

        stopDiscovery()
        mutableReaderConnectionState.value = ReaderConnectionState.Connecting(device.id)
        activeDevice = idevice

        val opened = idevice.getDeviceControl().open()
        if (opened) {
            mutableReaderConnectionState.value = ReaderConnectionState.Connected(device.id, device.name)
            mutableConnectionState.value = ConnectionState.Connected
            // Save the paired device ID for future auto-reconnect
            saveDeviceId(device.id)
            isAutoReconnecting = false
            autoReconnectTimeout?.cancel()
            Timber.d("✅ Device connected and saved: ${device.name}")
        } else {
            mutableReaderConnectionState.value = ReaderConnectionState.Error("Failed to connect to device")
            mutableConnectionState.value = ConnectionState.Error("Failed to connect to device")
            Timber.e("❌ Failed to open device")
        }
    }

    override fun disconnectDevice() {
        Timber.d("🔌 Disconnecting device")
        // Clear the saved device ID on manual disconnect
        clearSavedDeviceId()
        isAutoReconnecting = false
        autoReconnectTimeout?.cancel()

        if (activeDevice != null) {
            activeDevice?.getDeviceControl()?.close()
        }
        activeDevice = null
        mutableReaderConnectionState.value = ReaderConnectionState.Idle
        mutableConnectionState.value = ConnectionState.Disconnected
    }

    // MARK: - EMV Transaction

    override suspend fun startEmvTransaction(
        context: PaymentRequestContext,
        onStateChanged: (TransactionState) -> Unit
    ): AppResult<SecurePaymentData> {
        if (activeDevice == null) {
            return AppResult.Failure(AppError.Device("No device connected. Connect a reader first."))
        }

        val device = activeDevice ?: return AppResult.Failure(AppError.Device("Device disconnected"))
        val payloadCollector = SecurePayloadCollector()
        val completion = CompletableDeferred<AppResult<SecurePaymentData>>()
        var sawCardDataEvent = false
        var sawInputRequestEvent = false
        var sawAuthorizationRequestEvent = false
        var lastEventType: String = "none"

        val subscriber = IEventSubscriber { eventType, data ->
            val event = MagTekSdkEvent(
                eventTypeName = eventType.name,
                payload = data?.StringValue() ?: ""
            )
            lastEventType = event.eventTypeName
            if (event.eventTypeName == "CardData") sawCardDataEvent = true
            if (event.eventTypeName == "InputRequest") sawInputRequestEvent = true
            if (event.eventTypeName == "AuthorizationRequest") sawAuthorizationRequestEvent = true

            // Do not log payload because card artifacts can be sensitive.
            Timber.d("📨 Terminal event=%s payloadLength=%d", event.eventTypeName, event.payload.length)
            payloadCollector.capture(event)
            callbackMapper.toTransactionState(event)?.let(onStateChanged)
            when {
                eventType == EventType.AuthorizationRequest -> {
                    sawAuthorizationRequestEvent = true
                    completion.complete(AppResult.Success(payloadCollector.toSecurePaymentData()))
                }
                eventType == EventType.TransactionStatus && event.payload.contains("timedout", ignoreCase = true) -> {
                    completion.complete(AppResult.Failure(AppError.Device("Transaction timed out on terminal")))
                }
                eventType == EventType.TransactionStatus && event.payload.contains("cancel", ignoreCase = true) -> {
                    completion.complete(AppResult.Failure(AppError.Device("Transaction cancelled on terminal")))
                }
                eventType == EventType.TransactionStatus &&
                    (event.payload.contains("error", ignoreCase = true) ||
                        event.payload.contains("failed", ignoreCase = true)) -> {
                    completion.complete(AppResult.Failure(AppError.Device("Terminal reported transaction failure")))
                }
            }
        }

        activeSubscriber = subscriber
        device.subscribeAll(subscriber)

        onStateChanged(TransactionState.WaitingForCard)
        val started = device.startTransaction(buildTransaction(context))
        if (!started) {
            unsubscribeCurrentSubscriber()
            return AppResult.Failure(AppError.Device("Unable to start transaction on terminal"))
        }

        val appWaitTimeoutMs = (TERMINAL_TRANSACTION_TIMEOUT_SECONDS.toLong() * 1000L) + APP_AUTHORIZATION_WAIT_BUFFER_MS

        val result = withTimeoutOrNull(appWaitTimeoutMs) {
            completion.await()
        } ?: run {
            val timeoutMessage = when {
                sawAuthorizationRequestEvent -> "Authorization request was received but transaction did not complete"
                sawCardDataEvent -> "Card was detected but terminal did not request authorization"
                sawInputRequestEvent -> "Terminal is waiting for customer input (card/PIN)"
                else -> "No card activity received from terminal"
            }
            Timber.w(
                "⏱️ Authorization wait timed out. lastEvent=%s, cardData=%s, inputRequest=%s, authRequest=%s",
                lastEventType,
                sawCardDataEvent,
                sawInputRequestEvent,
                sawAuthorizationRequestEvent
            )
            AppResult.Failure(AppError.Device(timeoutMessage))
        }

        unsubscribeCurrentSubscriber()
        return result
    }

    override suspend fun cancelTransaction(): AppResult<Unit> {
        val device = activeDevice
            ?: return AppResult.Failure(AppError.Device("No active terminal session"))

        return if (device.cancelTransaction()) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(AppError.Device("Terminal did not accept cancellation"))
        }
    }

    private fun buildTransaction(context: PaymentRequestContext): Transaction {
        val paymentMethods = listOf(
            PaymentMethod.Contact,
            PaymentMethod.Contactless,
            PaymentMethod.MSR
        )

        return Transaction().apply {
            setAmount(context.amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
            setCashBack("0.00")
            setPaymentMethods(paymentMethods)
            setEMVOnly(false)
            setQuickChip(false)
            setSuppressThankYouMessage(true)
            setTimeout(TERMINAL_TRANSACTION_TIMEOUT_SECONDS)
        }
    }

    private fun unsubscribeCurrentSubscriber() {
        val device = activeDevice
        val subscriber = activeSubscriber
        if (device != null && subscriber != null) {
            device.unsubscribeAll(subscriber)
        }
        activeSubscriber = null
    }

    private class SecurePayloadCollector {
        private var encryptedTrackData: String = ""
        private var emvTlvData: String = ""
        private var pinBlock: String = ""
        private var deviceSerial: String = "UNKNOWN"
        private var entryMethod: String = "UNKNOWN"
        private var last4: String = "1111"

        fun capture(event: MagTekSdkEvent) {
            when (event.eventTypeName) {
                "CardData" -> {
                    encryptedTrackData = event.payload
                    last4 = extractLast4(event.payload)
                }
                "AuthorizationRequest", "TransactionResult" -> {
                    emvTlvData = event.payload
                }
                "PINBlock", "PINData" -> {
                    pinBlock = event.payload
                }
                "ConnectionState" -> {
                    if (event.payload.contains("usb", ignoreCase = true)) {
                        entryMethod = "USB"
                    }
                }
                "TransactionStatus" -> {
                    if (event.payload.contains("contactless", ignoreCase = true)) {
                        entryMethod = "CONTACTLESS"
                    } else if (event.payload.contains("insert", ignoreCase = true)) {
                        entryMethod = "CONTACT"
                    } else if (event.payload.contains("swipe", ignoreCase = true)) {
                        entryMethod = "MSR"
                    }
                }
                "DeviceEvent" -> {
                    deviceSerial = extractDeviceSerial(event.payload)
                }
            }
        }

        fun toSecurePaymentData(): SecurePaymentData {
            return SecurePaymentData(
                maskedPan = "************$last4",
                encryptedTrackData = encryptedTrackData.ifEmpty { "encrypted_track_payload" },
                emvTlvData = emvTlvData.ifEmpty { "emv_tlv_payload" },
                pinBlock = pinBlock.ifEmpty { "encrypted_pin_block" },
                deviceSerial = deviceSerial,
                entryMethod = entryMethod
            )
        }

        private fun extractLast4(payload: String): String {
            val digits = payload.filter { it.isDigit() }
            return if (digits.length >= 4) digits.takeLast(4) else "1111"
        }

        private fun extractDeviceSerial(payload: String): String {
            if (payload.isBlank()) {
                return "UNKNOWN"
            }
            return payload.take(32)
        }
    }
}






