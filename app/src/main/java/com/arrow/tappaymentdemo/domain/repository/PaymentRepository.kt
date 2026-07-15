package com.arrow.tappaymentdemo.domain.repository

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.PaymentAuthorizationResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.TransactionState
import kotlinx.coroutines.flow.StateFlow

interface PaymentRepository {
    val connectionState: StateFlow<ConnectionState>
    val transactionState: StateFlow<TransactionState>

    suspend fun startPayment(context: PaymentRequestContext): AppResult<PaymentAuthorizationResult>
    suspend fun cancelPayment(): AppResult<Unit>
}

