package com.tradereview.app.ui

import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Choreographer
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.backup.*
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReviewEvidenceUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var root:File;private lateinit var store:AttachmentStore
    private lateinit var model:TradeViewModel;private lateinit var backup:BackupViewModel
    private var nextImage="good.png";private var pickerLaunches=0
    private val registry=object:ActivityResultRegistry() {
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            val intent=contract.createIntent(context,input);assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.action);assertEquals("*/*",intent.type)
            assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("image/png"));pickerLaunches++
            dispatchResult(requestCode,TestImageProvider.uri(nextImage))
        }
    }
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        root=File(context.cacheDir,"review-ui-${newKey()}");store=AttachmentStore(root)
        val today=LocalDate.now().toEpochDay()
        runBlocking {
            db.trades().insert(Trade(date=today,netPnl="400.00",reviewed=true,mistakes=listOf("chase"),discipline=listOf("follow_plan"),professional=ProfessionalTrade(riskMode="amount",manualRisk="200.00",planStatus="in")))
            db.trades().insert(Trade(date=today,netPnl="-100.00",symbol="中证500"))
            db.trades().insert(Trade(date=today,currency="USD",symbol="AAPL",netPnl="99.00"))
            db.trades().insert(Trade(date=today-7,netPnl="-40.00",mistakes=listOf("chase")))
            db.trades().saveReview(DailyReview(date=today,reviewSaved=true,rating=4))
        }
        model=TradeViewModel(db,SavedStateHandle(),store);backup=BackupViewModel(db,context.contentResolver,store)
        val owner=object:ActivityResultRegistryOwner { override val activityResultRegistry=registry }
        compose.setContent { val state by model.state.collectAsStateWithLifecycle();CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            TradeReviewTheme(darkTheme=state.settings.theme=="dark") { TradeReviewApp(model,backup) }
        } }
        compose.waitUntil(15000){!model.state.value.loading && !model.analytics.value.loading}
    }
    @After fun close(){db.close();root.deleteRecursively()}
    private fun nav(label:String){compose.onAllNodesWithText(label).onFirst().performClick()}
    private fun openImages() {
        nav("交易记录");compose.onNodeWithTag("trades-list").performScrollToNode(hasText("+400.00"))
        compose.onNodeWithText("+400.00").performClick();compose.onNodeWithTag("open-trade-evidence").performScrollTo().performClick()
        compose.onNodeWithTag("pick-evidence").assertIsDisplayed()
    }
    private fun hideKeyboard() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.serviceInfo=info
        if(automation.windows.any { it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD })automation.executeShellCommand("input keyevent 4").close()
        compose.waitForIdle()
    }
    @Test fun systemPickerResultPrivateThumbnailsViewerSwitchCaptionAndDeleteWork() {
        openImages();compose.onNodeWithTag("image-caption").performTextInput("合成入场记录")
        compose.onNodeWithTag("pick-evidence").performScrollTo().performClick();compose.waitUntil(15000){model.state.value.attachments.size==1 && !model.busy.value}
        nextImage="second.png";compose.onNodeWithText("出场后").performScrollTo().performClick();compose.onNodeWithTag("pick-evidence").performScrollTo().performClick()
        compose.waitUntil(15000){model.state.value.attachments.size==2 && !model.busy.value};assertEquals(2,pickerLaunches);hideKeyboard()
        val a=model.state.value.attachments.first();compose.onNodeWithTag("evidence-list").performScrollToNode(hasTestTag("evidence-image-${a.id}"));capture("14-trade-evidence")
        compose.onNodeWithTag("view-evidence-${a.id}").performScrollTo().performClick();compose.onNodeWithTag("evidence-viewer-count").assertTextEquals("截图大图 1 / 2")
        compose.onNodeWithText("下一张").performClick();compose.onNodeWithTag("evidence-viewer-count").assertTextEquals("截图大图 2 / 2")
        compose.onNodeWithText("上一张").performClick();compose.onNodeWithTag("evidence-viewer-count").assertTextEquals("截图大图 1 / 2")
        compose.onNodeWithContentDescription("关闭大图").performClick()
        compose.onNodeWithTag("delete-evidence-${a.id}").performScrollTo().performClick();compose.onNodeWithText("确认删除附件").performClick()
        compose.waitUntil(15000){model.state.value.attachments.size==1 && !model.busy.value};assertFalse(store.file(a).exists())
        compose.onNodeWithContentDescription("返回交易详情").performClick();val remaining=model.state.value.attachments.single();compose.onNodeWithTag("evidence-thumb-${remaining.id}").performScrollTo().assertIsDisplayed()
        runBlocking { assertEquals("400.00",db.trades().findTradeByKey(remaining.recordKey)!!.netPnl) }
    }
    @Test fun weeklyMonthlyReportsUseAllCurrenciesAllowHistoryAndDrillIntoRealTrades() {
        nav("统计分析");compose.onNodeWithTag("open-week-report").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithTag("report-total").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("report-total").assertTextEquals("记录 3 笔 · 上期 1 笔")
        compose.onNodeWithTag("period-report-list").performScrollToNode(hasTestTag("report-net-CNY"));compose.onNodeWithTag("report-net-CNY").assertTextEquals("CNY · 2 笔 · +300.00")
        compose.onNodeWithTag("report-net-USD").performScrollTo().assertTextEquals("USD · 1 笔 · +99.00")
        capture("15-week-report")
        compose.onNodeWithText("查看 USD 明细").performScrollTo().performClick();compose.onNodeWithTag("report-trades-list").assertIsDisplayed();compose.onNodeWithText("AAPL").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回主页面").performClick();compose.onNodeWithTag("period-report-list").performScrollToNode(hasTestTag("report-previous"));compose.onNodeWithTag("report-previous").performClick()
        compose.waitUntil(15000){compose.onAllNodes(hasTestTag("report-total") and hasText("记录 1 笔 · 上期 0 笔")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("当前周期").performClick();compose.onNodeWithText("月报").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("每月复盘报告").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("report-total").fetchSemanticsNodes().isNotEmpty()}
        val current=ReviewReports.calculate(model.state.value.trades,model.state.value.reviews,ReviewPeriod.at("month",LocalDate.now()))
        compose.onNodeWithTag("report-total").assertTextEquals("记录 ${current.trades.size} 笔 · 上期 ${current.previousCount} 笔")
        compose.onNodeWithTag("period-report-list").performScrollToNode(hasTestTag("report-calendar-${LocalDate.now().toEpochDay()}"));compose.onNodeWithTag("report-calendar-${LocalDate.now().toEpochDay()}").performScrollTo();capture("16-month-report")
        compose.onNodeWithTag("report-calendar-${LocalDate.now().toEpochDay()}").performClick()
        compose.onNodeWithTag("report-trades-list").assertIsDisplayed();compose.onNodeWithText("+400.00").performClick();compose.onNodeWithText("编辑交易与复盘").assertIsDisplayed()
    }
    @Test fun predefinedAndTextGoalsCreateTrackArchiveAndKeepFactsUnchanged() {
        val before=runBlocking{db.trades().allTrades()};nav("统计分析");compose.onNodeWithText("纪律改善目标").performClick()
        compose.onNodeWithTag("new-review-goal").performClick();compose.onNodeWithText("提高交易复盘比例").performScrollTo().performClick();compose.onNodeWithText("80").performScrollTo().performClick()
        compose.onNodeWithTag("save-review-goal").performClick();compose.waitUntil(15000){model.state.value.goals.size==1 && !model.busy.value}
        val ratio=model.state.value.goals.single();compose.onNodeWithTag("goals-list").performScrollToNode(hasTestTag("goal-progress-${ratio.id}"));compose.onNodeWithTag("goal-progress-${ratio.id}").assertTextEquals("实际 33.33% · 阈值 80%")
        compose.onNodeWithTag("goals-list").performScrollToNode(hasTestTag("new-review-goal"));compose.onNodeWithTag("new-review-goal").performClick()
        compose.onNodeWithText("文字改善目标").performScrollTo().performClick();compose.onNodeWithTag("goal-title").performScrollTo().performTextInput("人工核对交易理由")
        compose.onNodeWithTag("save-review-goal").assertIsDisplayed().performClick();compose.waitUntil(15000){model.state.value.goals.size==2 && !model.busy.value};hideKeyboard()
        val text=model.state.value.goals.first { it.kind=="text" };compose.onNodeWithTag("goals-list").performScrollToNode(hasTestTag("goal-progress-${text.id}"));compose.onNodeWithTag("goal-progress-${text.id}").assertTextEquals("实际 不可自动计算 · 文字目标")
        compose.onNodeWithTag("goals-list").performScrollToNode(hasTestTag("archive-goal-${ratio.id}"));compose.onNodeWithTag("archive-goal-${ratio.id}").performScrollTo().performClick();compose.onNodeWithText("确认归档").performClick()
        compose.waitUntil(15000){model.state.value.goals.first { it.id==ratio.id }.archivedDay!=null && !model.busy.value}
        compose.onNodeWithTag("goals-list").performScrollToNode(hasText("已归档"));compose.onNodeWithText("已归档").performClick()
        compose.onNodeWithTag("goals-list").performScrollToNode(hasTestTag("goal-history-${ratio.id}"));compose.onNodeWithTag("goal-history-${ratio.id}").performScrollTo().assertIsDisplayed();capture("17-review-goals")
        runBlocking { assertEquals(before,db.trades().allTrades());assertEquals(2,db.trades().allGoals().size) }
    }
    @Test fun fullZipPreviewNeedsSecondConsentAndJsonScopeNeverClaimsImageBackup()=runBlocking {
        val t=db.trades().allTrades().first();AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","ZIP 证据")
        db.trades().saveGoal(ReviewGoal(title="复盘提醒",kind="text",startDate=ReviewPeriod.at("week",LocalDate.now()).start))
        val backupFile=File(context.cacheDir,"zip-ui-${newKey()}.zip")
        compose.runOnIdle { backup.exportZip(Uri.fromFile(backupFile)) };compose.waitUntil(15000){!backup.busy.value}
        try {
            compose.onNodeWithContentDescription("设置与备份").performClick();compose.runOnIdle { backup.readBackup(Uri.fromFile(backupFile)) }
            compose.waitUntil(15000){backup.preview.value!=null && !backup.busy.value};assertEquals(1,backup.preview.value!!.backup.attachments.size);assertEquals(1,backup.preview.value!!.backup.goals.size)
            val before=BackupRepository(db,store).export(full=true)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("backup-scope"));compose.onNodeWithTag("backup-scope").assertTextEquals("备份范围：完整 ZIP，全部截图已校验");capture("18-full-zip-preview")
            compose.onNodeWithText("继续确认恢复").performScrollTo().performClick();compose.onNodeWithText("确认去重合并？").assertIsDisplayed()
            assertEquals(before.trades,db.trades().allTrades());compose.onNodeWithText("返回预览").performClick();assertEquals(1,db.trades().allAttachments().size)
            compose.onNodeWithText("继续确认恢复").performScrollTo().performClick();compose.onNodeWithText("确认恢复").performClick();compose.waitUntil(15000){backup.preview.value==null && !backup.busy.value}
            assertEquals(1,db.trades().allAttachments().size);store.verifyAll(db.trades().allAttachments())
            compose.runOnIdle { backup.exportJson(Uri.fromFile(backupFile)) };compose.waitUntil(15000){!backup.busy.value};assertTrue(BackupCodec.read(backupFile.inputStream()).attachments.isEmpty())
            compose.runOnIdle { backup.readBackup(Uri.fromFile(backupFile)) };compose.waitUntil(15000){backup.preview.value!=null && !backup.busy.value}
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("backup-scope"));compose.onNodeWithTag("backup-scope").assertTextContains("不含图片",substring=true);assertEquals(1,db.trades().allAttachments().size)
        } finally { backupFile.delete() }
    }
    private fun capture(name:String) {
        compose.waitForIdle();val instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync()
        val frames=CountDownLatch(1);instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { frames.countDown() } } };assertTrue(frames.await(5,TimeUnit.SECONDS));SystemClock.sleep(250)
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val folder=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v06-screenshots").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        val values=ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,"$name.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/TradeReview-v06-ui");put(MediaStore.MediaColumns.IS_PENDING,1) }
        val uri=requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values));requireNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };context.contentResolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);bitmap.recycle()
    }
}
