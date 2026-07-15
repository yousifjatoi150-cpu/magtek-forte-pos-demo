package com.arrow.tappaymentdemo.domain.usecase

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository

class CancelPaymentUseCase(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(): AppResult<Unit> {
        return paymentRepository.cancelPayment()
    }
}

