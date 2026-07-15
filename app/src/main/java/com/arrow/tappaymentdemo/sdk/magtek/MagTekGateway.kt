package com.arrow.tappaymentdemo.sdk.magtek

import com.arrow.tappaymentdemo.core.result.AppError
import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.SecurePaymentData
import com.arrow.tappaymentdemo.domain.model.TransactionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MagTekGateway {
    val connectionState: StateFlow<ConnectionState>

    suspend fun startEmvTransaction(
        context: PaymentRequestContext,
        onStateChanged: (TransactionState) -> Unit
    ): AppResult<SecurePaymentData>

    suspend fun cancelTransaction(): AppResult<Unit>
}

class FakeMagTekGateway : MagTekGateway {
    private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)

    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    override suspend fun startEmvTransaction(
        context: PaymentRequestContext,
        onStateChanged: (TransactionState) -> Unit
    ): AppResult<SecurePaymentData> {
        if (context.invoiceNumber.contains("DEVICE_FAIL", ignoreCase = true)) {
            return AppResult.Failure(AppError.Device("Device failed to read card. Please retry."))
        }

        onStateChanged(TransactionState.WaitingForCard)
        delay(150)
        onStateChanged(TransactionState.ReadingCard)
        delay(150)
        onStateChanged(TransactionState.PinEntry)

        delay(300)
        return AppResult.Success(
            SecurePaymentData(
                maskedPan = "************1111",
                encryptedTrackData = "encrypted_track_payload",
                emvTlvData = "emv_tlv_payload",
                pinBlock = "encrypted_pin_block",
                deviceSerial = "DFX001",
                entryMethod = "CONTACTLESS"
            )
        )
    }

    override suspend fun cancelTransaction(): AppResult<Unit> {
        delay(150)
        return AppResult.Success(Unit)
    }
}


