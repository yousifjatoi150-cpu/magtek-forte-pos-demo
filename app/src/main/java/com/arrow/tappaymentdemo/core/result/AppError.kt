package com.arrow.tappaymentdemo.core.result

sealed interface AppError {
    val userMessage: String

    data class Validation(override val userMessage: String) : AppError
    data class Device(override val userMessage: String) : AppError
    data class Network(override val userMessage: String) : AppError
    data class Unknown(override val userMessage: String) : AppError
}

