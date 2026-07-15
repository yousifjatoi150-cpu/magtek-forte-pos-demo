package com.arrow.tappaymentdemo.domain.usecase

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.PaymentAuthorizationResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository

class StartPaymentUseCase(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(context: PaymentRequestContext): AppResult<PaymentAuthorizationResult> {
        return paymentRepository.startPayment(context)
    }
}

