package com.tradereview.app

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        setTheme(com.tradereview.app.R.style.Theme_TradeReview)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as TradeReviewApplication).database
        val evidence = (application as TradeReviewApplication).evidenceStore
        val experience=(application as TradeReviewApplication).deviceExperience
        lifecycleScope.launch {
            try{withContext(Dispatchers.IO){
                @Suppress("DEPRECATION") val install=packageManager.getPackageInfo(packageName,0)
                val dao=database.trades()
                // Existing empty installations also skip: package metadata survives an in-place upgrade.
                val returning=install.lastUpdateTime>install.firstInstallTime || dao.hasUserRecords()
                experience.initialize(returning)
            }}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;experience.initializationFailed()}
        }
        setContent {
                val model: TradeViewModel = viewModel(factory = viewModelFactory {
                    initializer { TradeViewModel(database, createSavedStateHandle(),evidence) }
                })
                val backup: BackupViewModel = viewModel(factory = viewModelFactory {
                    initializer { BackupViewModel(database, application.contentResolver,evidence,experience) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                val darkTheme=when (state.settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
                SideEffect { WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=!darkTheme }
                TradeReviewTheme(darkTheme = darkTheme) {
                    TradeReviewApp(model, backup,experience=experience)
                }
        }
    }
}
