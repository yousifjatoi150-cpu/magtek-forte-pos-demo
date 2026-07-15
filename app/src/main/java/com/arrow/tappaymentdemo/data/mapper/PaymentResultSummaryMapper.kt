package com.arrow.tappaymentdemo.data.mapper

import com.arrow.tappaymentdemo.network.DynaFlexPaymentResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PaymentResultSummaryMapper {

    private val prettyJson = Json {
        prettyPrint = true
    }

    fun toHumanReadableJson(response: DynaFlexPaymentResponse): String {
        val status = if (response.success) "Approved" else "Declined"
        val reason = response.data?.responseDescription
            ?: response.message
            ?: if (response.success) "Payment completed successfully" else "Payment was not approved"

        val nextStep = if (response.success) {
            "Share receipt with customer and complete checkout."
        } else {
            "Retry the payment or ask for another payment method."
        }

        val jsonObject = buildJsonObject {
            put("status", status)
            put("simpleMessage", reason)
            put("nextStep", nextStep)
            put("transactionId", response.data?.paymentId ?: response.data?.gatewayTransactionId ?: "N/A")
            put("authorizationCode", response.data?.authorizationCode ?: "N/A")
            put("receiptNumber", response.data?.receiptNo ?: "N/A")
            put("amount", response.data?.amount?.toString() ?: "N/A")
            put("paymentDate", response.data?.paymentDate ?: "N/A")
            put("backendCode", response.data?.responseCode ?: "N/A")
        }

        return prettyJson.encodeToString(JsonObject.serializer(), jsonObject)
    }
}

