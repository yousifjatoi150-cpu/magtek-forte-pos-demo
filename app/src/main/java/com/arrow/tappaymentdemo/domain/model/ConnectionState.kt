package com.arrow.tappaymentdemo.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Reconnecting : ConnectionState
    data class Error(val message: String) : ConnectionState
}

