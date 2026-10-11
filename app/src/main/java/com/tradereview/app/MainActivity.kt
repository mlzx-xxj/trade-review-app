package com.tradereview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tradereview.app.ui.TradeReviewApp
import com.tradereview.app.ui.TradeReviewTheme
import com.tradereview.app.ui.TradeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as TradeReviewApplication).database
        setContent {
            TradeReviewTheme {
                val model: TradeViewModel = viewModel(factory = viewModelFactory {
                    initializer { TradeViewModel(database.trades(), createSavedStateHandle()) }
                })
                TradeReviewApp(model)
            }
        }
    }
}
