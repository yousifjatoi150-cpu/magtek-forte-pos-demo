package com.arrow.tappaymentdemo.di

import com.arrow.tappaymentdemo.data.mapper.BackendPaymentRequestMapper
import com.arrow.tappaymentdemo.data.mapper.PaymentResultSummaryMapper
import com.arrow.tappaymentdemo.data.repository.PaymentRepositoryImpl
import com.arrow.tappaymentdemo.domain.repository.PaymentRepository
import com.arrow.tappaymentdemo.domain.usecase.CancelPaymentUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveConnectionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.ObserveTransactionStateUseCase
import com.arrow.tappaymentdemo.domain.usecase.StartPaymentUseCase
import com.arrow.tappaymentdemo.network.BackendPaymentApi
import com.arrow.tappaymentdemo.network.RealBackendPaymentApiRetrofit
import com.arrow.tappaymentdemo.presentation.payment.PaymentViewModel
import com.arrow.tappaymentdemo.sdk.magtek.MagTekCallbackToTransactionStateMapper
import com.arrow.tappaymentdemo.sdk.magtek.MagTekGateway
import com.arrow.tappaymentdemo.sdk.magtek.RealMagTekGateway
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { MagTekCallbackToTransactionStateMapper() }
    single<MagTekGateway> {
        RealMagTekGateway(
            appContext = get<android.content.Context>(),
            callbackMapper = get()
        )
    }
    single<BackendPaymentApi> {
        RealBackendPaymentApiRetrofit.create(
            baseUrl = "https://mmsapiapp-dev.azurewebsites.net"
        )
    }
    single { BackendPaymentRequestMapper() }
    single { PaymentResultSummaryMapper() }
    single<PaymentRepository> {
        PaymentRepositoryImpl(
            magTekGateway = get(),
            backendPaymentApi = get(),
            backendPaymentRequestMapper = get(),
            paymentResultSummaryMapper = get()
        )
    }

    factory { StartPaymentUseCase(get()) }
    factory { CancelPaymentUseCase(get()) }
    factory { ObserveConnectionStateUseCase(get()) }
    factory { ObserveTransactionStateUseCase(get()) }

    viewModel {
        PaymentViewModel(
            startPaymentUseCase = get(),
            cancelPaymentUseCase = get(),
            observeConnectionStateUseCase = get(),
            observeTransactionStateUseCase = get(),
            magTekGateway = get<MagTekGateway>()
        )
    }
}





