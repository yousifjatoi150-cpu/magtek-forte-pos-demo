package com.arrow.tappaymentdemo.domain.model

import java.math.BigDecimal

data class PaymentRequestContext(
    val invoiceNumber: String,
    val amount: BigDecimal,
    val currency: String,
    val merchantLocation: String,
    val cashier: String,
    val referenceNumber: String,
    val terminalId: String
)

