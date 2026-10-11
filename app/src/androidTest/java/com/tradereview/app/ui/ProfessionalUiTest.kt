package com.tradereview.app.ui

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProfessionalUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase
    private lateinit var model:TradeViewModel
    private val raw=ProfessionalDraft(quantityUnit="futures_lot",entryPrice="3500",exitPrice="3480",quantity="2",multiplier="10",initialStop="3510",fees="3",otherCosts="2",costsConfirmed=true,linearConfirmed=true,expanded=true,contractCode="rb2701")
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        runBlocking { db.trades().saveDraft(EntryDraft(market="futures",symbol="螺纹钢",direction="short",amount="42",professional=raw)) }
        model=TradeViewModel(db,SavedStateHandle());val backup=BackupViewModel(db,context.contentResolver)
        compose.setContent { val state by model.state.collectAsStateWithLifecycle(); TradeReviewTheme(darkTheme=state.settings.theme=="dark") { TradeReviewApp(model,backup) } }
        compose.waitUntil(15000) { !model.state.value.loading }
    }
    @After fun close() { db.close() }
    private fun entry() { compose.onNodeWithContentDescription("新增交易").performClick() }
    private fun save() { compose.onNodeWithText("保存交易").assertIsDisplayed().performClick();compose.waitUntil(15000) { !model.busy.value && model.state.value.trades.isNotEmpty() && compose.onAllNodesWithText("快速记录交易").fetchSemanticsNodes().isEmpty() } }
    private fun feedbackGone() { compose.waitUntil(15000) { model.message.value==null && !model.busy.value } }
    @Test fun automaticModeNeedsConfirmationPreservesManualDraftAndSavesExactResult() {
        entry();compose.onNodeWithText("自动计算").performScrollTo().performClick()
        compose.onNodeWithText("确认切换盈亏模式？").assertIsDisplayed()
        runBlocking { assertEquals("42",db.trades().allDrafts().single().amount) }
        compose.onNodeWithText("确认切换").performClick()
        compose.onNodeWithTag("professional-net").performScrollTo().assertTextEquals("自动净盈亏：+395.00 CNY")
        compose.onNodeWithTag("professional-fees").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("保存交易").performClick();compose.onNodeWithTag("entry-error").assertIsDisplayed()
        runBlocking { assertTrue(db.trades().allTrades().isEmpty()) }
        compose.onNodeWithTag("professional-fees").performTextReplacement("3")
        compose.onNodeWithTag("professional-net").performScrollTo().assertTextEquals("自动净盈亏：+395.00 CNY")
        compose.onNodeWithText("保存交易").assertIsDisplayed();capture("09-professional-auto")
        compose.onNodeWithTag("professional-exit").performScrollTo().performTextReplacement("3479.5")
        compose.onNodeWithText("保存交易").assertIsDisplayed();save()
        runBlocking { val t=db.trades().allTrades().single();assertEquals("405.00",t.netPnl);assertEquals("auto",t.professional!!.pnlMode);assertEquals("rb2701",t.professional!!.contractCode) }
    }
    @Test fun riskWarningCannotSilentlySaveOrChangeFactsAndConsentIsRecorded() {
        runBlocking {
            db.trades().saveRiskRules(RiskRules(accountEquity="10000.00",maxRiskPercent="1"))
            db.trades().saveDraft(EntryDraft(amount="400",professional=ProfessionalDraft(riskMode="amount",manualRisk="200",expanded=true)))
        }
        compose.waitUntil(15000) { model.state.value.riskRules.isNotEmpty() && model.state.value.drafts.single().amount=="400" }
        entry();compose.onNodeWithText("保存交易").performClick()
        compose.onNodeWithText("风险纪律提醒，仍保存真实记录？").assertIsDisplayed()
        runBlocking { assertTrue(db.trades().allTrades().isEmpty()) };capture("10-risk-confirmation")
        compose.onNodeWithText("仍保存真实记录").performClick()
        compose.waitUntil(15000) { !model.busy.value && model.state.value.trades.size==1 }
        runBlocking { val t=db.trades().allTrades().single();assertEquals("400.00",t.netPnl);assertEquals(listOf("single_risk"),t.riskRecord!!.violations) }
    }
    @Test fun professionalDraftRestoreAndHistoricalManualEditsKeepMoneyAndIdentity() {
        entry();compose.onNodeWithTag("professional-entry").performScrollTo().performTextReplacement("3499.75")
        compose.waitUntil(15000) { model.state.value.drafts.single().professional!!.entryPrice=="3499.75" }
        compose.onNodeWithContentDescription("返回").performClick();entry()
        compose.onNodeWithText("已恢复未保存草稿").assertIsDisplayed()
        compose.onNodeWithTag("professional-entry").performScrollTo().assertTextContains("3499.75")
        save();val first=runBlocking { db.trades().allTrades().single() };assertEquals("42.00",first.netPnl)
        feedbackGone();compose.onAllNodesWithText("交易记录").onFirst().performClick()
        compose.onNodeWithTag("trades-list").performScrollToNode(hasText("+42.00"));compose.onNodeWithText("+42.00").performClick()
        compose.onNodeWithTag("expand-professional").performScrollTo().performClick()
        compose.onNodeWithTag("professional-entry").performScrollTo().performTextReplacement("3498")
        compose.onNodeWithText("保存交易").performClick();compose.waitUntil(15000) { !model.busy.value && model.state.value.trades.single().professional!!.entryPrice=="3498" }
        runBlocking { val t=db.trades().allTrades().single();assertEquals(first.recordKey,t.recordKey);assertEquals("42.00",t.netPnl);assertEquals("3.00",t.professional!!.fees) }
    }
    @Test fun ruleAndUserTemplateSettingsPersistWithoutSupplyingGuessedParameters() {
        compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithTag("settings-list").performScrollToNode(hasText("风险规则与合约模板"))
        compose.onNodeWithText("风险规则与合约模板").performClick()
        compose.onNodeWithTag("risk-settings-list").performScrollToNode(hasTestTag("rule-equity"));compose.onNodeWithTag("rule-equity").performScrollTo().performTextInput("10000")
        compose.onNodeWithTag("rule-risk-percent").performScrollTo().performTextInput("1")
        compose.onNodeWithTag("save-risk-rules").performScrollTo().performClick();compose.waitUntil(15000) { model.state.value.riskRules.size==1 && !model.busy.value };feedbackGone()
        compose.onNodeWithTag("risk-settings-list").performScrollToNode(hasText("新增合约参数模板"));compose.onNodeWithText("新增合约参数模板").performClick()
        compose.onNodeWithText("模板名称").performTextInput("用户螺纹参数");compose.onNodeWithTag("template-multiplier").performScrollTo().performTextInput("10")
        compose.onNodeWithTag("template-user-verified").performScrollTo().performClick();compose.onNodeWithText("保存模板").performClick()
        compose.waitUntil(15000) { model.state.value.contractTemplates.size==1 && !model.busy.value };feedbackGone()
        runBlocking { assertEquals("10000.00",db.trades().allRiskRules().single().accountEquity);assertEquals("10",db.trades().allTemplates().single().multiplier);assertTrue(db.trades().allTemplates().single().userVerified) }
        compose.onNodeWithTag("risk-settings-list").performScrollToNode(hasTestTag("rule-equity"));capture("11-risk-rules")
    }
    @Test fun riskStatisticsUseOnlyValidSamplesAndDarkProfessionalScreenStillWorks() {
        runBlocking { listOf(Trade(netPnl="400.00",professional=ProfessionalTrade(riskMode="amount",manualRisk="200.00",planStatus="in")),
            Trade(netPnl="-100.00",strategy="breakout",professional=ProfessionalTrade(riskMode="amount",manualRisk="100.00",planStatus="out")),Trade(netPnl="99.00")).forEach { db.trades().insert(it) } }
        compose.waitUntil(15000) { model.analytics.value.risk.r.valid==2 }
        compose.onAllNodesWithText("统计分析").onFirst().performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("r-valid-samples"));compose.onNodeWithTag("r-valid-samples").assertTextEquals("有效 R 样本 2 / 3 笔")
        compose.onNodeWithTag("r-average").assertTextEquals("平均 R：+0.500 R");capture("12-r-analytics")
        compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithTag("settings-list").performScrollToNode(hasText("深色"));compose.onNodeWithText("深色").performClick()
        compose.waitUntil(15000) { model.state.value.settings.theme=="dark" && !model.busy.value };feedbackGone()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("红盈绿亏"));compose.onNodeWithText("红盈绿亏").performClick()
        compose.waitUntil(15000) { model.state.value.settings.pnlPalette=="red_profit" && !model.busy.value };feedbackGone()
        compose.onNodeWithContentDescription("返回主页面").performClick();compose.onAllNodesWithText("首页").onFirst().performClick();entry()
        compose.onNodeWithTag("professional-entry").performScrollTo();capture("13-professional-dark")
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val frames=CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { frames.countDown() } } }
        assertTrue("Android render frames",frames.await(5,TimeUnit.SECONDS))
        // The compositor captures all windows, including dialogs; the frame barrier
        // and short presentation wait avoid the previous page's system frame.
        SystemClock.sleep(250)
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val root=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v05-screenshots").apply { mkdirs() }
        File(root,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        if(Build.VERSION.SDK_INT>=29) {
            val values=ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,"$name.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/TradeReview-v05-ui");put(MediaStore.MediaColumns.IS_PENDING,1) }
            val uri=requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
        }
        bitmap.recycle()
    }
}
