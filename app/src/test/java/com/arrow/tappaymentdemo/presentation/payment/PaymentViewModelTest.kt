package com.arrow.tappaymentdemo.presentation.payment

import com.arrow.tappaymentdemo.domain.usecase.CancelPaymentUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveConnectionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveTransactionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.StartPaymentUseCase
import com.arrow.tappaymentdemo.fakes.FakePaymentRepository
import com.arrow.tappaymentdemo.sdk.magtek.FakeMagTekGateway
import com.arrow.tappaymentdemo.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `start payment updates message with approved text`() = runTest {
        val repository = FakePaymentRepository()
        val magTekGateway = FakeMagTekGateway()
        val viewModel = PaymentViewModel(
            startPaymentUseCase = StartPaymentUseCase(repository),
            cancelPaymentUseCase = CancelPaymentUseCase(repository),
            observeConnectionStateUseCase = ObserveConnectionStateUseCase(repository),
            observeTransactionStateUseCase = ObserveTransactionStateUseCase(repository),
            magTekGateway = magTekGateway
        )

        viewModel.onEvent(PaymentUiEvent.StartPayment)

        assertTrue(viewModel.uiState.value.userMessage?.contains("Approved") == true)
    }

    @Test
    fun `cancel payment updates state with cancelled message`() = runTest {
        val repository = FakePaymentRepository()
        val magTekGateway = FakeMagTekGateway()
        val viewModel = PaymentViewModel(
            startPaymentUseCase = StartPaymentUseCase(repository),
            cancelPaymentUseCase = CancelPaymentUseCase(repository),
            observeConnectionStateUseCase = ObserveConnectionStateUseCase(repository),
            observeTransactionStateUseCase = ObserveTransactionStateUseCase(repository),
            magTekGateway = magTekGateway
        )

        viewModel.onEvent(PaymentUiEvent.CancelPayment)

        assertTrue(viewModel.uiState.value.userMessage?.contains("cancelled", ignoreCase = true) == true)
    }
}



