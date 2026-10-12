package com.tradereview.app.stability

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.tradereview.app.*
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ui.TradeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Actual exported MainActivity and its persistent database; disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class ActivityCompatibilityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val application get()=compose.activity.application as TradeReviewApplication
    private val db get()=application.database
    private val key="00000000000000000000000000000808"
    private lateinit var model:TradeViewModel
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    @Before fun seed(){
        require(Build.HARDWARE in listOf("ranchu","goldfish")){"This fixture only runs on a disposable emulator"}
        runBlocking {
            db.trades().findTradeByKey(key)?.let{AttachmentRepository(db,application.evidenceStore).deleteTrade(it)}
            EntryRepository(db).save(EntryDraft(key="v08-fixture",recordKey=key,amount="123.45",professional=ProfessionalDraft(riskMode="amount",manualRisk="200",planStatus="in")))
            AttachmentRepository(db,application.evidenceStore).importImage(application.contentResolver,TestImageProvider.uri(),key,"before","V0.8 重启合成证据")
            val item=PreflightItem(id="00000000000000000000000000000810",text="重启后核对数据")
            db.trades().savePreflightItem(item);db.trades().savePreflightCheck(PreflightCheck(LocalDate.now().toEpochDay(),"manual:${item.id}",item.text,true,"manual",listOf("trade:$key")))
            db.trades().saveGoal(ReviewGoal(id="00000000000000000000000000000809",title="重启验收目标",kind="text",startDate=ReviewPeriod.at("week",LocalDate.now()).start))
            db.trades().saveOcrDraft(OcrDraft(id="00000000000000000000000000000811",fields=SmartJson.fields(mapOf("quantity" to "2.")),blocked=true,closedEvidence=false,createdDay=LocalDate.now().toEpochDay()))
            db.trades().saveDraft(EntryDraft(amount="12."))
        }
        compose.runOnIdle{model=ViewModelProvider(compose.activity)[TradeViewModel::class.java]}
        compose.waitUntil(30000){!model.state.value.loading && !model.analytics.value.loading && model.state.value.trades.any{it.recordKey==key}}
    }
    @Test fun actualActivityDraftSurvivesRecreationAndBackgroundResume(){
        compose.onNodeWithContentDescription("新增交易").performClick()
        compose.onNodeWithTag("entry-amount").performScrollTo().performTextReplacement("77.77")
        compose.waitUntil(15000){model.state.value.drafts.any{it.key=="new" && it.amount=="77.77"}}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20000){compose.onAllNodesWithTag("entry-amount").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("entry-amount").performScrollTo().assertTextContains("77.77")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("entry-amount").performScrollTo().assertTextContains("77.77")
        compose.onNodeWithContentDescription("返回").performClick()
        runBlocking{assertEquals("123.45",db.trades().findTradeByKey(key)!!.netPnl);application.evidenceStore.verifyAll(db.trades().allAttachments())}
    }
    @Test fun largeFontDarkThemeKeyboardAndLandscapeKeepSaveUsable(){
        val oldScale=shell("settings get system font_scale").trim()
        val oldKeyboard=shell("settings get secure show_ime_with_hard_keyboard").trim()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val oldFlags=automation.serviceInfo.flags
        try {
            automation.serviceInfo=automation.serviceInfo.apply{flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            runBlocking{db.trades().saveSettings(AppSettings(theme="dark",pnlPalette="red_profit"))}
            shell("settings put system font_scale 1.3")
            compose.waitUntil(20000){compose.activity.resources.configuration.fontScale>=1.29f && model.state.value.settings.theme=="dark"}
            compose.onNodeWithContentDescription("新增交易").performClick()
            compose.onNodeWithTag("entry-amount").performScrollTo().performClick().performTextReplacement("77.77")
            // Emulator hardware keyboards can suppress the soft IME; require real
            // window insets rather than calling a focused field a keyboard test.
            shell("settings put secure show_ime_with_hard_keyboard 1")
            showKeyboard()
            compose.onNodeWithText("保存交易").assertIsDisplayed();capture("23-v08-large-font-keyboard")
            compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(20000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            compose.onNodeWithTag("entry-amount").performScrollTo().assertTextContains("77.77")
            compose.onNodeWithText("保存交易").assertIsDisplayed();capture("24-v08-landscape")
            compose.onNodeWithText("保存交易").performClick()
            compose.waitUntil(15000){!model.busy.value && compose.onAllNodesWithTag("entry-amount").fetchSemanticsNodes().isEmpty()}
            runBlocking{assertEquals("123.45",db.trades().findTradeByKey(key)!!.netPnl)}
        }finally{
            if(automation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD})shell("input keyevent KEYCODE_BACK")
            automation.serviceInfo=automation.serviceInfo.apply{flags=oldFlags}
            if(oldKeyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard")
            shell("settings put system font_scale ${oldScale.toFloatOrNull() ?: 1f}")
            compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            compose.waitUntil(20000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
            runBlocking{db.trades().saveSettings(AppSettings())}
        }
    }
    private fun showKeyboard(){
        // The editor lives in a separate Dialog window, so Activity.currentFocus
        // and the Activity's insets do not describe its text input. Tap the actual
        // editor and require a real visible system IME window instead.
        compose.onNodeWithTag("entry-amount").performTouchInput{click()}
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(15000){automation.windows.any{window->
            val bounds=android.graphics.Rect();window.getBoundsInScreen(bounds)
            window.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD && !bounds.isEmpty
        }}
    }
    @Test fun realThousandFiveThousandTenThousandListsAndCalendarRemainInteractive(){
        val result=linkedMapOf<String,Any>("api" to Build.VERSION.SDK_INT,"definition" to "test-framework end-to-end scroll-to-index and idle, not frame-jank/phone latency")
        compose.onNodeWithTag("main-page-1").performClick()
        for(count in listOf(1000,5000,10000)) {
            val trades=List(count){Trade(recordKey=newKey(),date=LocalDate.now().toEpochDay()-it%365,netPnl=if(it%3==0)"-25.35"else "100.10")}
            try {
                val oldCount=runBlocking{db.trades().allTrades().size}
                runBlocking{db.withTransaction{trades.forEach{db.trades().insert(it)}}}
                compose.waitUntil(30000){model.analytics.value.filtered.size==oldCount+count}
                compose.waitForIdle()
                val samples=List(3){
                    val start=SystemClock.elapsedRealtimeNanos()
                    compose.onNodeWithTag("trades-list").performScrollToIndex(count)
                    compose.waitForIdle();compose.onNodeWithTag("trades-list").performScrollToIndex(0);compose.waitForIdle()
                    (SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
                }
                result["list-$count"]=mapOf("samplesMs" to samples,"medianMs" to samples.sorted()[1],"actualRecords" to model.analytics.value.filtered.size)
                if(count==10000)capture("25-v08-large-list")
                val start=SystemClock.elapsedRealtimeNanos();compose.onNodeWithContentDescription("打开交易日历").performClick()
                compose.onNodeWithTag("calendar-next").performClick();compose.onNodeWithTag("calendar-previous").performClick()
                result["calendar-switch-$count-ms"]=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
                compose.onNodeWithTag("main-page-1").performClick()
            }finally{runBlocking{db.withTransaction{trades.forEach{t->db.trades().findTradeByKey(t.recordKey)?.let{db.trades().delete(it)}}}}}
        }
        V08Metrics.write("activity-api${Build.VERSION.SDK_INT}.json",GsonBuilder().setPrettyPrinting().create().toJson(result))
    }
    private fun capture(name:String){
        compose.waitForIdle();SystemClock.sleep(250)
        val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val dir=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v08-screenshots").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
}
