package com.arrow.tappaymentdemo.sdk.magtek

import com.arrow.tappaymentdemo.domain.model.TransactionState

class MagTekCallbackToTransactionStateMapper {

    fun toTransactionState(event: MagTekSdkEvent): TransactionState? {
        val payload = event.payload.lowercase()
        return when {
            event.eventTypeName == "InputRequest" && payload.contains("pin") -> TransactionState.PinEntry
            event.eventTypeName == "PINData" || event.eventTypeName == "PINBlock" -> TransactionState.PinEntry
            event.eventTypeName == "AuthorizationRequest" -> TransactionState.Authorizing
            event.eventTypeName == "CardData" -> TransactionState.ReadingCard
            event.eventTypeName == "DisplayMessage" -> {
                if (payload.contains("insert") || payload.contains("tap") || payload.contains("swipe")) {
                    TransactionState.WaitingForCard
                } else {
                    null
                }
            }
            event.eventTypeName == "TransactionStatus" && payload.contains("timedout") -> TransactionState.Timeout
            event.eventTypeName == "TransactionStatus" && payload.contains("cancel") -> TransactionState.Cancelled
            event.eventTypeName == "TransactionStatus" &&
                (payload.contains("error") || payload.contains("failed")) -> {
                TransactionState.DeviceError("Device reported transaction failure")
            }
            else -> null
        }
    }
}


