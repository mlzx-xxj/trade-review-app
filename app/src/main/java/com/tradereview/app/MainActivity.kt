package com.tradereview.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tradereview.app.ui.BackupViewModel
import com.tradereview.app.ui.TradeReviewTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var storageReady by mutableStateOf(false)
    private var storageError by mutableStateOf<String?>(null)
    private fun initializeStorage() {
        storageError=null
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val app=application as TradeReviewApplication
                    app.initializeEvidenceStorage()
                    @Suppress("DEPRECATION") val install=packageManager.getPackageInfo(packageName,0)
                    val returning=install.lastUpdateTime>install.firstInstallTime || app.database.trades().hasUserRecords()
                    app.deviceExperience.initialize(returning)
                }
                storageReady=true
            } catch(e:Exception) {
                if(e is kotlinx.coroutines.CancellationException)throw e
                storageError="本机文件检查失败，业务记录未清空。可重试或从完整备份恢复。"
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TradeReview)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deliver IME insets instead of panning the app header outside the window.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val app=application as TradeReviewApplication
        initializeStorage()
        setContent {
            val model:com.tradereview.app.v200.V200ViewModel=viewModel(factory=viewModelFactory {
                initializer { com.tradereview.app.v200.V200ViewModel(app.database,createSavedStateHandle()) }
            })
            val backup:BackupViewModel=viewModel(factory=viewModelFactory {
                initializer { BackupViewModel(app.database,application.contentResolver,app.evidenceStore,app.deviceExperience) }
            })
            val dark=isSystemInDarkTheme()
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=!dark }
            TradeReviewTheme(darkTheme=dark) {
                if(storageReady)com.tradereview.app.v200.V200App(model,backup,app.evidenceStore)
                else Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                        if(storageError==null){LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在检查本机数据与图片")} else {
                            Text(storageError!!)
                            Button({initializeStorage()},Modifier.heightIn(min=48.dp)){Text("重新检查")}
                            Box(Modifier.weight(1f)){com.tradereview.app.v200.V200BackupPage(backup)}
                        }
                    }
                }
            }
        }
    }
}
