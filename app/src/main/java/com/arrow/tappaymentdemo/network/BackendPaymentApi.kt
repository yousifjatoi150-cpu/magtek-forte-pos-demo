package com.arrow.tappaymentdemo.network

import com.arrow.tappaymentdemo.core.result.AppError
import com.arrow.tappaymentdemo.core.result.AppResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// MARK: - Request Models (matching iOS DynaFlexPaymentRequest)

@Serializable
data class DynaFlexPaymentRequest(
    val appDeviceId: String,
    val devicePaymentConfigurationId: String? = null,
    val organizationId: String,
    val memberId: String? = null,
    val amount: Double,
    val serviceFeeAmount: Double? = null,
    val currencyCode: String,
    val sourceApp: String,
    val paymentPurpose: String,
    val referenceId: String,
    val donationId: String? = null,
    val campaignId: String? = null,
    val eventId: String? = null,
    val eventRegistrationId: String? = null,
    val eventTicketOrderId: String? = null,
    val quantity: Int? = null,
    val extraTicketQuantity: Int? = null,
    val unitPrice: Double? = null,
    val deviceId: String,
    val readerType: String,
    val readerSerialNumber: String,
    val billingAddress: BillingAddress,
    val cardEmvData: CardEmvData,
    val remarks: String
) {
    @Serializable
    data class BillingAddress(
        val firstName: String,
        val lastName: String,
        val phone: String? = null,
        val physicalAddress: PhysicalAddress? = null
    )

    @Serializable
    data class PhysicalAddress(
        val streetLine1: String? = null,
        val streetLine2: String? = null,
        val locality: String? = null,
        val region: String? = null,
        val postalCode: String? = null
    )

    @Serializable
    data class CardEmvData(
        val transactionOutput: TransactionOutput
    )

    @Serializable
    data class TransactionOutput(
        val ksn: String,
        val deviceSerialNumber: String,
        val emvSredData: String,
        val cardType: String
    )
}

// MARK: - Response Models (matching iOS DynaFlexPaymentResponse)

@Serializable
data class DynaFlexPaymentResponse(
    val success: Boolean,
    val message: String? = null,
    val data: PaymentData? = null
) {
    @Serializable
    data class PaymentData(
        val paymentId: String? = null,
        val gatewayTransactionId: String? = null,
        val authorizationCode: String? = null,
        val status: String? = null,
        val responseCode: String? = null,
        val responseDescription: String? = null,
        val receiptNo: String? = null,
        val amount: Double? = null,
        val paymentDate: String? = null
    )
}

// MARK: - Interface

interface BackendPaymentApi {
    suspend fun processPayment(request: DynaFlexPaymentRequest): AppResult<DynaFlexPaymentResponse>
}

// Fallback fake for testing
class FakeBackendPaymentApi : BackendPaymentApi {
    override suspend fun processPayment(request: DynaFlexPaymentRequest): AppResult<DynaFlexPaymentResponse> {
        if (request.referenceId.contains("NETWORK_FAIL", ignoreCase = true)) {
            return AppResult.Failure(AppError.Network("Unable to reach payment backend. Check network."))
        }

        kotlinx.coroutines.delay(350)
        val approved = request.amount <= 1000.00
        return if (approved) {
            AppResult.Success(
                DynaFlexPaymentResponse(
                    success = true,
                    message = null,
                    data = DynaFlexPaymentResponse.PaymentData(
                        paymentId = request.referenceId,
                        gatewayTransactionId = "TXN-${request.referenceId}",
                        authorizationCode = "123456",
                        status = "Approved",
                        responseCode = "A01",
                        responseDescription = "Transaction Approved",
                        receiptNo = "RCP-${request.referenceId}"
                    )
                )
            )
        } else {
            AppResult.Success(
                DynaFlexPaymentResponse(
                    success = false,
                    message = "Amount exceeds configured test limit",
                    data = null
                )
            )
        }
    }
}



