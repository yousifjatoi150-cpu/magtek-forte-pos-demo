package com.arrow.tappaymentdemo.data.mapper

import com.arrow.tappaymentdemo.domain.model.PaymentRequestContext
import com.arrow.tappaymentdemo.domain.model.SecurePaymentData
import com.arrow.tappaymentdemo.network.DynaFlexPaymentRequest
import java.util.UUID

class BackendPaymentRequestMapper {
    fun toRequest(context: PaymentRequestContext, securePaymentData: SecurePaymentData): DynaFlexPaymentRequest {
        return DynaFlexPaymentRequest(
            appDeviceId = "59e6cc40-7cc8-4cf9-9c6e-93be6be05551",
            devicePaymentConfigurationId = null,
            organizationId = "5eaab13e-c11b-4f97-b7fc-06e265fc5f89",
            memberId = null,
            amount = context.amount.toDouble(),
            serviceFeeAmount = null,
            currencyCode = context.currency,
            sourceApp = "Kiosk",
            paymentPurpose = "Donation",
            referenceId = UUID.randomUUID().toString().lowercase(),
            donationId = "b155f057-2ab8-493f-a1d7-f4ec8ac273cb",
            campaignId = "3f1bc79b-b670-46a7-b2be-84a21772c3f7",
            eventId = null,
            eventRegistrationId = null,
            eventTicketOrderId = null,
            quantity = null,
            extraTicketQuantity = null,
            unitPrice = null,
            deviceId = context.terminalId,
            readerType = "MagTekDynaFlexIIGo",
            readerSerialNumber = securePaymentData.deviceSerial,
            billingAddress = DynaFlexPaymentRequest.BillingAddress(
                firstName = "Guest",
                lastName = "Donor",
                phone = "444-444-4444",
                physicalAddress = DynaFlexPaymentRequest.PhysicalAddress(
                    streetLine1 = "8003 Clock Tower Ln",
                    streetLine2 = "Suite 200",
                    locality = "Hill Valley",
                    region = "CA",
                    postalCode = "46203"
                )
            ),
            cardEmvData = DynaFlexPaymentRequest.CardEmvData(
                transactionOutput = DynaFlexPaymentRequest.TransactionOutput(
                    ksn = "KSN_PLACEHOLDER",
                    deviceSerialNumber = securePaymentData.deviceSerial,
                    emvSredData = securePaymentData.emvTlvData,
                    cardType = "05"
                )
            ),
            remarks = "Kiosk DynaFlex donation test payment"
        )
    }
}


