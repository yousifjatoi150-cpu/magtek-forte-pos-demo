package com.arrow.tappaymentdemo.domain.model

sealed interface TransactionState {
    data object Idle : TransactionState
    data object WaitingForCard : TransactionState
    data object ReadingCard : TransactionState
    data object PinEntry : TransactionState
    data object Authorizing : TransactionState
    data class Approved(val transactionId: String, val authorizationCode: String) : TransactionState
    data class Declined(val reason: String) : TransactionState
    data object Cancelled : TransactionState
    data object Timeout : TransactionState
    data class DeviceError(val message: String) : TransactionState
    data class NetworkError(val message: String) : TransactionState
}

