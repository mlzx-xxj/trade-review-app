package com.tradereview.app.v210

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.MainActivity
import com.tradereview.app.v200.V200ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Reproduce a real same-text feedback race without altering any ledger facts. */
@RunWith(AndroidJUnit4::class)
class V210FeedbackLifecycleTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Test fun repeatedSameTextBeforeNextFrameIsShownAndDrainedWithoutChangingLedger(){
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val qemu=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("getprop ro.kernel.qemu")).bufferedReader().use{it.readText().trim()}
        assertEquals("Disposable emulator only","1",qemu)
        ui.waitUntil(45000){ui.onAllNodesWithTag("v2-main-page-0").fetchSemanticsNodes().isNotEmpty()}
        lateinit var model:V200ViewModel
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        val before=runBlocking{model.repository.read()}
        val text="合成反馈回归：相同内容再次保存"
        val reissued=AtomicBoolean(false)
        val activity=ui.activity
        lateinit var watcher:Job
        ui.runOnIdle{
            watcher=activity.lifecycleScope.launch(Dispatchers.Main.immediate){
                // Re-emit on the natural clear, before the next Compose frame can
                // observe null. No test clears or dismisses the application's notice.
                model.message.dropWhile{it!=text}.drop(1).first{it==null}
                model.message.value=text
                reissued.set(true)
            }
        }
        try{
            ui.runOnIdle{model.message.value=text}
            ui.onNodeWithText(text).assertIsDisplayed()
            ui.waitUntil(45000){reissued.get()}
            ui.onNodeWithText(text).assertIsDisplayed()
            ui.waitUntil(45000){reissued.get()&&model.message.value==null}
            ui.waitForIdle()
            ui.onNodeWithText(text).assertDoesNotExist()
            assertEquals(before,runBlocking{model.repository.read()})
        }finally{watcher.cancel()}
    }
}
