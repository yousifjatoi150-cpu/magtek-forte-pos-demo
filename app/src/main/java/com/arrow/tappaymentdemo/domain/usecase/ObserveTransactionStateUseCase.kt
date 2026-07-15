package com.arrow.tappaymentdemo.domain.usecase

import com.arrow.tappaymentdemo.domain.model.TransactionState
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveTransactionStateUseCase(
    private val paymentRepository: PaymentRepository
) {
    operator fun invoke(): StateFlow<TransactionState> = paymentRepository.transactionState
}

