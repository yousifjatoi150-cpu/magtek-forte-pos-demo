package com.arrow.tappaymentdemo.domain.model

data class PaymentAuthorizationResult(
    val approved: Boolean,
    val transactionId: String,
    val authorizationCode: String?,
    val declineReason: String?
)

