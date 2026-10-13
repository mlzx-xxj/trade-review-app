package com.tradereview.app.release

import android.content.Context
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
import com.tradereview.app.data.*
import com.tradereview.app.device.DeviceExperienceStore
import com.tradereview.app.evidence.*
import com.tradereview.app.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReleaseExperienceUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var folder:File;private lateinit var model:TradeViewModel;private lateinit var backup:BackupViewModel
    private lateinit var ops:DeviceExperienceStore
    private val name="v100-ui-${newKey()}"
    private var chosenRequest:Int?=null
    private val document=BackupDocumentProvider.uri()
    private val registry=object:ActivityResultRegistry(){
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){
            val intent=contract.createIntent(context,input)
            when(intent.action){
                android.content.Intent.ACTION_CREATE_DOCUMENT->{assertEquals("application/zip",intent.type);chosenRequest=requestCode}
                android.content.Intent.ACTION_OPEN_DOCUMENT->{
                    if(intent.getStringArrayExtra(android.content.Intent.EXTRA_MIME_TYPES)?.any{it.startsWith("image/") }==true)dispatchResult(requestCode,TestImageProvider.uri())
                    else dispatchResult(requestCode,document)
                }
                else->fail("unexpected external action")
            }
        }
    }
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();folder=File(context.cacheDir,"v100-ui-${newKey()}");ops=DeviceExperienceStore(context,name);ops.initialize(false);val store=AttachmentStore(folder);model=TradeViewModel(db,SavedStateHandle(),store);backup=BackupViewModel(db,context.contentResolver,store,ops)}
    @After fun close(){ops.close();context.deleteSharedPreferences(name);db.close();folder.deleteRecursively()}
    private fun show(){val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry};compose.setContent{val state by model.state.collectAsStateWithLifecycle();CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){TradeReviewTheme(darkTheme=state.settings.theme=="dark"){TradeReviewApp(model,backup,experience=ops)}}};compose.waitUntil(20000){!model.state.value.loading && !model.analytics.value.loading}}
    private fun returned(){ops.finishOnboarding();show()}
    @Test fun firstUseCanSkipDoesNotInventDataAndGuideCanBeReopened(){show();compose.onNodeWithTag("first-use").assertIsDisplayed();capture("27-v100-onboarding");compose.onNodeWithTag("onboarding-skip").performClick();compose.waitUntil(10000){ops.state.value.onboardingDone};compose.onNodeWithTag("first-use").assertDoesNotExist();runBlocking{assertTrue(db.trades().allTrades().isEmpty())};compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("show-help"));compose.onNodeWithTag("show-help").performClick();compose.onNodeWithText("使用指南").assertIsDisplayed();compose.onNodeWithTag("onboarding-start").performClick();assertTrue(ops.state.value.onboardingDone)}
    @Test fun homeHasOneFastEntryAndDirectPlanPostReviewAndCalendarNavigation(){returned();compose.onNodeWithTag("home-fast-entry").assertIsDisplayed().assertHasClickAction();assertTrue(compose.onNodeWithTag("home-fast-entry").fetchSemanticsNode().boundsInRoot.height>=48*context.resources.displayMetrics.density);compose.onNodeWithTag("home-today-pnl").assertTextEquals("暂无交易");capture("28-v100-home");compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-plan"));compose.onNodeWithTag("home-plan").performClick();compose.onNodeWithText("保存盘前计划").performScrollTo().assertIsDisplayed();compose.onNodeWithTag("main-page-0").performClick();compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-post-review"));compose.onNodeWithTag("home-post-review").performClick();compose.onNodeWithText("保存盘后复盘").performScrollTo().assertIsDisplayed();compose.onNodeWithTag("main-page-4").performClick();compose.onNodeWithTag("calendar-month").assertIsDisplayed()}
    @Test fun documentSelectionAloneNeverAdvancesBackupTimeAndPrivacyIsVisible(){returned();compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithTag("complete-backup").performClick();assertNotNull(chosenRequest);assertEquals(0L,ops.state.value.zipWrittenMillis);compose.runOnIdle{registry.dispatchResult(requireNotNull(chosenRequest),document)};compose.waitUntil(30000){!backup.busy.value && ops.state.value.verifiedZipMillis>0};compose.onNodeWithTag("zip-verified-time").performScrollTo().assertIsDisplayed();capture("29-v100-backup-readback");compose.onNodeWithTag("settings-list").performScrollToNode(hasText("隐私与数据说明"));compose.onNodeWithText("隐私与数据说明").assertIsDisplayed();capture("30-v100-privacy")}
    @Test fun completeManualEvidenceReviewCalendarReportsZipRestoreChainKeepsStableKeyAndImage(){
        returned();compose.onNodeWithTag("home-fast-entry").performClick();compose.onNodeWithTag("entry-amount").performScrollTo().performTextReplacement("123.45");compose.onNodeWithText("保存交易").performClick();compose.waitUntil(20000){!model.busy.value && model.state.value.trades.size==1}
        val key=model.state.value.trades.single().recordKey
        compose.onNodeWithTag("main-page-1").performClick();compose.onNodeWithText("沪深300").performClick();compose.onNodeWithTag("open-trade-evidence").performScrollTo().performClick();compose.onNodeWithTag("pick-evidence").performScrollTo().performClick();compose.waitUntil(20000){!model.busy.value && model.state.value.attachments.size==1};val image=model.state.value.attachments.single();capture("31-v100-trade-evidence")
        compose.onNodeWithContentDescription("返回交易详情").performClick();compose.onNodeWithContentDescription("返回").performClick();compose.onNodeWithTag("main-page-0").performClick();compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-post-review"));compose.onNodeWithTag("home-post-review").performClick();compose.onNodeWithText("保存盘后复盘").performScrollTo().assertIsEnabled().assertHasClickAction().performClick();
        try { compose.waitUntil(20000){model.state.value.reviews.any{it.reviewSaved} && !model.busy.value} }
        catch(failure:Throwable){capture("v100-post-review-save-failure");val out=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v100-post-review-failure.txt");out.writeText("Synthetic fixture only\nmessage=${model.message.value}\nbusy=${model.busy.value}\nreviews=${model.state.value.reviews}\n"+compose.onRoot(useUnmergedTree=true).printToString());throw failure}
        compose.onNodeWithTag("main-page-4").performClick();compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-day-${LocalDate.now().toEpochDay()}"));compose.onNodeWithTag("calendar-day-${LocalDate.now().toEpochDay()}").performClick();compose.onNodeWithTag("day-pnl-CNY").assertTextEquals("CNY：+123.45 · 1 笔");compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithTag("main-page-3").performClick();compose.onNodeWithTag("open-week-report").performClick();compose.onNodeWithTag("period-report-list").performScrollToNode(hasTestTag("report-net-CNY"));compose.onNodeWithTag("report-net-CNY").assertTextEquals("CNY · 1 笔 · +123.45");compose.onNodeWithTag("period-report-list").performScrollToNode(hasText("月报"));compose.onNodeWithText("月报").performClick();compose.onNodeWithText("每月复盘报告").assertIsDisplayed();compose.waitUntil(15000){compose.onAllNodesWithText("正在本地计算报告").fetchSemanticsNodes().isEmpty()};compose.onNodeWithTag("period-report-list").performScrollToNode(hasTestTag("report-net-CNY"));compose.onNodeWithTag("report-net-CNY").assertTextEquals("CNY · 1 笔 · +123.45");compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithTag("complete-backup").performClick();compose.runOnIdle{registry.dispatchResult(requireNotNull(chosenRequest),document)};compose.waitUntil(30000){!backup.busy.value && ops.state.value.verifiedZipMillis>0}
        compose.onNodeWithContentDescription("返回主页面").performClick();compose.onNodeWithTag("main-page-1").performClick();compose.onNodeWithText("沪深300").performClick();compose.onNodeWithContentDescription("删除交易").performClick();compose.onNodeWithText("删除").performClick();compose.waitUntil(20000){!model.busy.value && model.state.value.trades.isEmpty() && model.state.value.attachments.isEmpty()};assertFalse(model.evidenceStore!!.file(image).exists())
        compose.onNodeWithContentDescription("设置与备份").performClick();compose.onNodeWithText("选择 ZIP / JSON 备份恢复").performScrollTo().performClick();compose.waitUntil(30000){backup.preview.value!=null && !backup.busy.value};assertEquals(1,backup.preview.value!!.backup.attachments.size);compose.onNodeWithTag("settings-list").performScrollToNode(hasText("继续确认恢复"));compose.onNodeWithText("继续确认恢复").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick();compose.onNodeWithText("确认去重合并？").assertIsDisplayed();capture("32-v100-restore-confirmation");compose.onNodeWithText("确认恢复").performClick();compose.waitUntil(30000){!backup.busy.value && backup.preview.value==null && model.state.value.trades.size==1 && model.state.value.attachments.size==1};assertEquals(key,model.state.value.trades.single().recordKey);assertEquals("123.45",model.state.value.trades.single().netPnl);runBlocking{model.evidenceStore!!.verifyAll(db.trades().allAttachments());assertTrue(db.trades().allReviews().single().reviewSaved)}
    }
    private fun capture(name:String){compose.waitForIdle();android.os.SystemClock.sleep(250);val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());val out=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v100-screenshots").apply{mkdirs()};File(out,"$name.png").outputStream().use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()}
}
