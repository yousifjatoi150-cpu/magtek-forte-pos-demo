package com.arrow.tappaymentdemo.data.repository

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.data.mapper.BackendPaymentRequestMapper
import com.arrow.tappaymentdemo.domain.model.ConnectionState
import com.arrow.tappaymentdemo.domain.model.PaymentAuthorizationResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.TransactionState
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository
import com.arrow.tappaymentdemo.network.BackendPaymentApi
import com.arrow.tappaymentdemo.sdk.magtek.MagTekGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import timber.log.Timber

class PaymentRepositoryImpl(
    private val magTekGateway: MagTekGateway,
    private val backendPaymentApi: BackendPaymentApi,
    private val backendPaymentRequestMapper: BackendPaymentRequestMapper
) : PaymentRepository {

    override val connectionState: StateFlow<ConnectionState> = magTekGateway.connectionState

    private val mutableTransactionState = MutableStateFlow<TransactionState>(TransactionState.Idle)
    override val transactionState: StateFlow<TransactionState> = mutableTransactionState.asStateFlow()

    override suspend fun startPayment(context: PaymentRequestContext): AppResult<PaymentAuthorizationResult> {
        if (context.amount <= BigDecimal.ZERO) {
            mutableTransactionState.value = TransactionState.Declined("Amount must be greater than zero")
            return AppResult.Failure(com.arrow.tappaymentdemo.core.result.AppError.Validation("Invalid amount"))
        }

        mutableTransactionState.value = TransactionState.WaitingForCard

        val securePayloadResult = magTekGateway.startEmvTransaction(context) { gatewayState ->
            mutableTransactionState.value = gatewayState
        }
        if (securePayloadResult is AppResult.Failure) {
            mutableTransactionState.value = TransactionState.DeviceError(securePayloadResult.error.userMessage)
            return securePayloadResult
        }

        val securePayload = (securePayloadResult as AppResult.Success).data
        mutableTransactionState.value = TransactionState.Authorizing

        val backendRequest = backendPaymentRequestMapper.toRequest(context, securePayload)
        Timber.d("🟡 Starting backend payment: refId=${backendRequest.referenceId}, amount=${backendRequest.amount}")
        val backendResult = backendPaymentApi.processPayment(backendRequest)

        return when (backendResult) {
            is AppResult.Success -> {
                val response = backendResult.data
                Timber.d("✅ Backend response: success=${response.success}, msg=${response.message}")
                val authorizationResult = PaymentAuthorizationResult(
                    approved = response.success,
                    transactionId = response.data?.paymentId ?: response.data?.gatewayTransactionId.orEmpty(),
                    authorizationCode = response.data?.authorizationCode,
                    declineReason = if (!response.success) response.message ?: "Declined" else null
                )
                mutableTransactionState.value = if (authorizationResult.approved) {
                    TransactionState.Approved(
                        transactionId = authorizationResult.transactionId,
                        authorizationCode = authorizationResult.authorizationCode.orEmpty()
                    )
                } else {
                    TransactionState.Declined(authorizationResult.declineReason ?: "Declined")
                }
                AppResult.Success(authorizationResult)
            }

            is AppResult.Failure -> {
                mutableTransactionState.value = TransactionState.NetworkError(backendResult.error.userMessage)
                Timber.e("❌ Backend error: ${backendResult.error.userMessage}")
                backendResult
            }
        }
    }

    override suspend fun cancelPayment(): AppResult<Unit> {
        val cancelResult = magTekGateway.cancelTransaction()
        mutableTransactionState.value = TransactionState.Cancelled
        return cancelResult
    }
}



