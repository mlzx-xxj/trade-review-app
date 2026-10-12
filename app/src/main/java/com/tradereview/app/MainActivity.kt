package com.tradereview.app

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tradereview.app.ui.BackupViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as TradeReviewApplication).database
        val evidence = (application as TradeReviewApplication).evidenceStore
        setContent {
                val model: TradeViewModel = viewModel(factory = viewModelFactory {
                    initializer { TradeViewModel(database, createSavedStateHandle(),evidence) }
                })
                val backup: BackupViewModel = viewModel(factory = viewModelFactory {
                    initializer { BackupViewModel(database, application.contentResolver,evidence) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                TradeReviewTheme(darkTheme = when (state.settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }) {
                    TradeReviewApp(model, backup)
                }
        }
    }
}
