package com.arrow.tappaymentdemo.presentation.payment

import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.TransactionState

data class PaymentUiState(
    val invoiceNumber: String = "INV-1001",
    val amount: String = "125.00",
    val currency: String = "USD",
    val merchantLocation: String = "Main Counter",
    val cashier: String = "Cashier-1",
    val referenceNumber: String = "REF-1001",
    val terminalId: String = "PED-001",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val transactionState: TransactionState = TransactionState.Idle,
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
    val paymentResultHeadline: String? = null,
    val paymentResultSummaryJson: String? = null
)

sealed interface PaymentUiEvent {
    data class InvoiceNumberChanged(val value: String) : PaymentUiEvent
    data class AmountChanged(val value: String) : PaymentUiEvent
    data class CurrencyChanged(val value: String) : PaymentUiEvent
    data class MerchantLocationChanged(val value: String) : PaymentUiEvent
    data class CashierChanged(val value: String) : PaymentUiEvent
    data class ReferenceNumberChanged(val value: String) : PaymentUiEvent
    data class TerminalIdChanged(val value: String) : PaymentUiEvent
    data object StartPayment : PaymentUiEvent
    data object CancelPayment : PaymentUiEvent
    data object DismissMessage : PaymentUiEvent
}

