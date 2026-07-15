package com.arrow.tappaymentdemo.domain.model

data class ReaderDevice(
    val id: String,
    val name: String,
    val rssi: Int? = null
) {
    val isLikelyReader: Boolean
        get() = name.contains("dynaflex", ignoreCase = true) ||
                name.contains("magtek", ignoreCase = true) ||
                name.contains("mtusdk", ignoreCase = true)
}

sealed interface ReaderConnectionState {
    data object Idle : ReaderConnectionState
    data object Scanning : ReaderConnectionState
    data class Connecting(val deviceId: String) : ReaderConnectionState
    data class Connected(val deviceId: String, val name: String) : ReaderConnectionState
    data class Error(val message: String) : ReaderConnectionState

    val isConnected: Boolean
        get() = this is Connected

    val isScanning: Boolean
        get() = this is Scanning
}

