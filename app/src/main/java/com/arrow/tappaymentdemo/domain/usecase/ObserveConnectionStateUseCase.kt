package com.arrow.tappaymentdemo.domain.usecase

import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveConnectionStateUseCase(
    private val paymentRepository: PaymentRepository
) {
    operator fun invoke(): StateFlow<ConnectionState> = paymentRepository.connectionState
}

