package com.arrow.tappaymentdemo.domain.usecase

import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.fakes.FakePaymentRepository
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPaymentUseCaseTest {

    @Test
    fun `invoke passes request context to repository`() = runTest {
        val repository = FakePaymentRepository()
        val useCase = StartPaymentUseCase(repository)
        val request = PaymentRequestContext(
            invoiceNumber = "INV-101",
            amount = BigDecimal("45.50"),
            currency = "USD",
            merchantLocation = "Front Desk",
            cashier = "Cashier-01",
            referenceNumber = "REF-101",
            terminalId = "PED-001"
        )

        val result = useCase(request)

        assertTrue(result is AppResult.Success)
        assertEquals("INV-101", repository.capturedContext?.invoiceNumber)
        assertEquals(BigDecimal("45.50"), repository.capturedContext?.amount)
    }
}

