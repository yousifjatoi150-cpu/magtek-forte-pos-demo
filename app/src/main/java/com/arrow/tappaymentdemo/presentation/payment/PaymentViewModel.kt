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
import com.arrow.tappaymentdemo.sdk.magtek.RealMagTekGateway
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PaymentViewModel(
    private val startPaymentUseCase: StartPaymentUseCase,
    private val cancelPaymentUseCase: CancelPaymentUseCase,
    observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    observeTransactionStateUseCase: ObserveTransactionStateUseCase,
    private val magTekGateway: Any? = null  // Accept any MagTekGateway type for testing flexibility
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()

    val readerConnectionState: StateFlow<ReaderConnectionState>
        get() = if (magTekGateway is RealMagTekGateway) {
            magTekGateway.readerConnectionState
        } else {
            MutableStateFlow(ReaderConnectionState.Idle).asStateFlow()
        }

    val discoveredDevices: StateFlow<List<ReaderDevice>>
        get() = if (magTekGateway is RealMagTekGateway) {
            magTekGateway.discoveredDevices
        } else {
            MutableStateFlow(emptyList<ReaderDevice>()).asStateFlow()
        }

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
        if (magTekGateway is RealMagTekGateway) {
            magTekGateway.startDiscovery()
        }
    }

    fun stopDeviceDiscovery() {
        Timber.d("🛑 Stopping device discovery from ViewModel")
        if (magTekGateway is RealMagTekGateway) {
            magTekGateway.stopDiscovery()
        }
    }

    fun connectToDevice(device: ReaderDevice) {
        Timber.d("🔗 Connecting to device: ${device.name}")
        if (magTekGateway is RealMagTekGateway) {
            magTekGateway.connectToDevice(device)
        }
    }

    fun disconnectDevice() {
        Timber.d("🔌 Disconnecting device")
        if (magTekGateway is RealMagTekGateway) {
            magTekGateway.disconnectDevice()
        }
    }

    private fun startPayment() {
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
                    mutableUiState.update { it.copy(userMessage = message) }
                }

                is AppResult.Failure -> {
                    mutableUiState.update { it.copy(userMessage = result.error.userMessage) }
                }
            }
        }
    }

    private fun cancelPayment() {
        viewModelScope.launch {
            cancelPaymentUseCase()
            mutableUiState.update { it.copy(userMessage = "Payment cancelled") }
        }
    }
}



