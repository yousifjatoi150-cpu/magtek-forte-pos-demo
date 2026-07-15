package com.arrow.tappaymentdemo

import android.app.Application
import com.arrow.tappaymentdemo.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class TapPaymentDemoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for structured logging
        try {
            val field = Class.forName("com.arrow.tappaymentdemo.BuildConfig").getDeclaredField("DEBUG")
            if (field.getBoolean(null)) {
                Timber.plant(Timber.DebugTree())
            }
        } catch (e: Exception) {
            // Fallback: always plant on debug builds
            Timber.plant(Timber.DebugTree())
        }

        // Initialize Koin DI
        startKoin {
            androidContext(this@TapPaymentDemoApp)
            modules(appModule)
        }

        Timber.d("✅ TapPaymentDemo app initialized")
    }
}



