package com.arrow.tappaymentdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arrow.tappaymentdemo.presentation.payment.PaymentScreen
import com.arrow.tappaymentdemo.ui.theme.TapPaymentDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TapPaymentDemoTheme {
                PaymentScreen()
            }
        }
    }
}
