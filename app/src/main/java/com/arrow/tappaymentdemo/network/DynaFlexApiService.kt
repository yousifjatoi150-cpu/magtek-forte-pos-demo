package com.arrow.tappaymentdemo.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import com.arrow.tappaymentdemo.core.result.AppError
import com.arrow.tappaymentdemo.core.result.AppResult
import com.arrow.tappaymentdemo.domain.model.PaymentAuthorizationResult
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

interface DynaFlexApiService {
    @POST("/api/Kiosk/dynaflex/payment")
    suspend fun processPayment(@Body request: DynaFlexPaymentRequest): DynaFlexPaymentResponse

    @GET("/api/payments/receipt/{id}")
    suspend fun getReceipt(@Path("id") transactionId: String): Receipt?
}

data class Receipt(
    val transaction_id: String? = null,
    val approved: Boolean = false,
    val status: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val authorization_code: String? = null,
    val response_code: String? = null,
    val message: String? = null,
    val date: String? = null,
    val card: ReceiptCard? = null,
    val avs_result: String? = null,
    val cvv_result: String? = null,
    val merchant_name: String? = null
) {
    data class ReceiptCard(
        val type: String? = null,
        val last4: String? = null,
        val masked: String? = null,
        val name_on_card: String? = null
    )
}

class RealBackendPaymentApiRetrofit(
    private val apiService: DynaFlexApiService,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : BackendPaymentApi {

    override suspend fun processPayment(request: DynaFlexPaymentRequest): AppResult<DynaFlexPaymentResponse> {
        return withContext(ioContext) {
            try {
                Timber.d("🔵 Sending DynaFlex payment request: referenceId=${request.referenceId}, amount=${request.amount}")
                val response = apiService.processPayment(request)
                Timber.d("🟢 DynaFlex payment response: success=${response.success}, message=${response.message}")
                AppResult.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "❌ DynaFlex payment failed: ${e.message}")
                AppResult.Failure(AppError.Network("Payment processing failed: ${e.localizedMessage}"))
            }
        }
    }

    companion object {
        fun create(baseUrl: String = "https://mmsapiapp-dev.azurewebsites.net"): RealBackendPaymentApiRetrofit {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val httpClient = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor { message ->
                    Timber.d("🌐 HTTP: $message")
                }.apply { setLevel(HttpLoggingInterceptor.Level.BODY) })
                .connectTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(30), java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(30), java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(30), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val service = retrofit.create(DynaFlexApiService::class.java)
            return RealBackendPaymentApiRetrofit(service)
        }
    }
}

