package com.arrow.tappaymentdemo.domain.model

data class SecurePaymentData(
    val maskedPan: String,
    val encryptedTrackData: String,
    val emvTlvData: String,
    val pinBlock: String,
    val deviceSerial: String,
    val entryMethod: String
)

