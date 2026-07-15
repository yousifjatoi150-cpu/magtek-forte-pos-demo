package com.arrow.tappaymentdemo.fakes

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.PaymentAuthorizationResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.TransactionState
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePaymentRepository : PaymentRepository {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)

    private val mutableTransactionState = MutableStateFlow<TransactionState>(TransactionState.Idle)
    override val transactionState: StateFlow<TransactionState> = mutableTransactionState

    var nextStartResult: AppResult<PaymentAuthorizationResult> = AppResult.Success(
        PaymentAuthorizationResult(
            approved = true,
            transactionId = "TXN-1",
            authorizationCode = "123456",
            declineReason = null
        )
    )

    var capturedContext: PaymentRequestContext? = null

    override suspend fun startPayment(context: PaymentRequestContext): AppResult<PaymentAuthorizationResult> {
        capturedContext = context
        mutableTransactionState.value = TransactionState.Authorizing
        val result = nextStartResult
        if (result is AppResult.Success && result.data.approved) {
            mutableTransactionState.value = TransactionState.Approved("TXN-1", "123456")
        }
        return result
    }

    override suspend fun cancelPayment(): AppResult<Unit> {
        mutableTransactionState.value = TransactionState.Cancelled
        return AppResult.Success(Unit)
    }
}


