package com.arrow.tappaymentdemo.presentation.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.ReaderConnectionState
import com.arrow.tappaymentdemo.domain.model.ReaderDevice
import com.arrow.tappaymentdemo.domain.model.TransactionState
import com.arrow.tappaymentdemo.domain.usecase.CancelPaymentUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveConnectionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveTransactionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.StartPaymentUseCase
import com.arrow.tappaymentdemo.sdk.magtek.MagTekGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class PaymentViewModel(
    private val startPaymentUseCase: StartPaymentUseCase,
    private val cancelPaymentUseCase: CancelPaymentUseCase,
    observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    observeTransactionStateUseCase: ObserveTransactionStateUseCase,
    private val magTekGateway: MagTekGateway
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()
    private val prettyJson = Json {
        prettyPrint = true
    }

    val readerConnectionState: StateFlow<ReaderConnectionState> = magTekGateway.readerConnectionState

    val discoveredDevices: StateFlow<List<ReaderDevice>> = magTekGateway.discoveredDevices

    init {
        viewModelScope.launch {
            observeConnectionStateUseCase().collect { connectionState ->
                mutableUiState.update { it.copy(connectionState = connectionState) }
            }
        }
        viewModelScope.launch {
            observeTransactionStateUseCase().collect { transactionState ->
                mutableUiState.update {
                    it.copy(
                        transactionState = transactionState,
                        isProcessing = transactionState is TransactionState.WaitingForCard ||
                            transactionState is TransactionState.ReadingCard ||
                            transactionState is TransactionState.PinEntry ||
                            transactionState is TransactionState.Authorizing
                    )
                }
            }
        }
    }

    fun onEvent(event: PaymentUiEvent) {
        when (event) {
            is PaymentUiEvent.InvoiceNumberChanged -> mutableUiState.update { it.copy(invoiceNumber = event.value) }
            is PaymentUiEvent.AmountChanged -> mutableUiState.update { it.copy(amount = event.value) }
            is PaymentUiEvent.CurrencyChanged -> mutableUiState.update { it.copy(currency = event.value) }
            is PaymentUiEvent.MerchantLocationChanged -> mutableUiState.update { it.copy(merchantLocation = event.value) }
            is PaymentUiEvent.CashierChanged -> mutableUiState.update { it.copy(cashier = event.value) }
            is PaymentUiEvent.ReferenceNumberChanged -> mutableUiState.update { it.copy(referenceNumber = event.value) }
            is PaymentUiEvent.TerminalIdChanged -> mutableUiState.update { it.copy(terminalId = event.value) }
            PaymentUiEvent.StartPayment -> startPayment()
            PaymentUiEvent.CancelPayment -> cancelPayment()
            PaymentUiEvent.DismissMessage -> mutableUiState.update { it.copy(userMessage = null) }
        }
    }

    fun startDeviceDiscovery() {
        Timber.d("🔎 Starting device discovery from ViewModel")
        magTekGateway.startDiscovery()
    }

    fun stopDeviceDiscovery() {
        Timber.d("🛑 Stopping device discovery from ViewModel")
        magTekGateway.stopDiscovery()
    }

    fun connectToDevice(device: ReaderDevice) {
        Timber.d("🔗 Connecting to device: ${device.name}")
        magTekGateway.connectToDevice(device)
    }

    fun disconnectDevice() {
        Timber.d("🔌 Disconnecting device")
        magTekGateway.disconnectDevice()
    }

    private fun startPayment() {
        mutableUiState.update {
            it.copy(
                paymentResultHeadline = null,
                paymentResultSummaryJson = null
            )
        }
        val currentState = uiState.value
        val amount = currentState.amount.toBigDecimalOrNull()
        if (amount == null) {
            mutableUiState.update { it.copy(userMessage = "Enter a valid amount") }
            return
        }

        val requestContext = PaymentRequestContext(
            invoiceNumber = currentState.invoiceNumber,
            amount = amount,
            currency = currentState.currency,
            merchantLocation = currentState.merchantLocation,
            cashier = currentState.cashier,
            referenceNumber = currentState.referenceNumber,
            terminalId = currentState.terminalId
        )

        viewModelScope.launch {
            when (val result = startPaymentUseCase(requestContext)) {
                is AppResult.Success -> {
                    val message = if (result.data.approved) {
                        "Approved: ${result.data.transactionId}"
                    } else {
                        "Declined: ${result.data.declineReason ?: "Unknown reason"}"
                    }
                    val headline = if (result.data.approved) "Payment approved" else "Payment declined"
                    mutableUiState.update {
                        it.copy(
                            userMessage = message,
                            paymentResultHeadline = headline,
                            paymentResultSummaryJson = result.data.humanReadableSummaryJson
                                ?: fallbackResultJson(message = message, approved = result.data.approved)
                        )
                    }
                }

                is AppResult.Failure -> {
                    val message = result.error.userMessage
                    mutableUiState.update {
                        it.copy(
                            userMessage = message,
                            paymentResultHeadline = "Payment could not be completed",
                            paymentResultSummaryJson = fallbackResultJson(message = message, approved = false)
                        )
                    }
                }
            }
        }
    }

    private fun cancelPayment() {
        viewModelScope.launch {
            cancelPaymentUseCase()
            val message = "Payment cancelled"
            mutableUiState.update {
                it.copy(
                    userMessage = message,
                    paymentResultHeadline = message,
                    paymentResultSummaryJson = fallbackResultJson(message = message, approved = false)
                )
            }
        }
    }

    private fun fallbackResultJson(message: String, approved: Boolean): String {
        val jsonObject = buildJsonObject {
            put("status", if (approved) "Approved" else "Not approved")
            put("simpleMessage", message)
            put(
                "nextStep",
                if (approved) "Share receipt with customer and complete checkout."
                else "Retry payment or use another payment method."
            )
        }
        return prettyJson.encodeToString(JsonObject.serializer(), jsonObject)
    }
}



