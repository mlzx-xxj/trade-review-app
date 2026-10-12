package com.tradereview.app.ui

import android.content.*
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.SystemClock
import android.view.Choreographer
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
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ocr.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SmartReviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var root:File;private lateinit var model:TradeViewModel;private lateinit var ocr:OcrViewModel
    private var pickerCount=0
    private val registry=object:ActivityResultRegistry() {
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            val intent=contract.createIntent(context,input);assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.action);assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("image/png"));pickerCount++
            dispatchResult(requestCode,TestImageProvider.uri("ocr.png"))
        }
    }
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();root=File(context.cacheDir,"smart-ui-${newKey()}");val store=AttachmentStore(root)
        val today=LocalDate.now().toEpochDay()
        runBlocking {
            db.trades().saveSmartSettings(SmartSettings(minimumSamples=3))
            repeat(3){i->db.trades().insert(Trade(date=today-i-1,netPnl="-100.00",mistakes=if(i<2)listOf("chase")else emptyList()))}
            repeat(3){i->db.trades().insert(Trade(date=today-i-16,netPnl="50.00",reviewed=true))}
            db.trades().saveDraft(EntryDraft(amount="12.",note="原快捷草稿"))
            db.trades().saveGoal(ReviewGoal(title="核对信号",kind="text",startDate=ReviewPeriod.at("week",LocalDate.now()).start))
        }
        model=TradeViewModel(db,SavedStateHandle(),store);ocr=OcrViewModel(context)
        val backup=BackupViewModel(db,context.contentResolver,store);val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry}
        compose.setContent { val state by model.state.collectAsStateWithLifecycle();CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){TradeReviewTheme(darkTheme=state.settings.theme=="dark"){TradeReviewApp(model,backup,ocr)}} }
        compose.waitUntil(15000){!model.state.value.loading && !model.analytics.value.loading}
    }
    @After fun close(){ocr.clear();db.close();root.deleteRecursively()}
    @Test fun realBundledOcrPickerConfirmationCreatesOnlyVerifiedTradeAndKeepsPrivateEvidence() {
        compose.onNodeWithTag("open-ocr").performScrollTo().performClick();compose.onNodeWithTag("ocr-pick").performClick()
        compose.waitUntil(60000){!ocr.state.value.busy && ocr.state.value.candidates.isNotEmpty()}
        val c=ocr.state.value.candidates.single();println("SYNTHETIC_UI_OCR="+ocr.state.value.raw.replace('\n',';'))
        assertTrue(c.fields.toString(),OcrTradeParser.canCreate(c,c.fields.keys,true));assertEquals(1,pickerCount)
        compose.onNodeWithTag("ocr-create").assertIsNotEnabled();capture("19-ocr-recognition")
        compose.onNodeWithTag("ocr-thumbnail").performScrollTo().performClick();compose.onNodeWithTag("evidence-viewer-count").assertTextEquals("截图大图 1 / 1");compose.onNodeWithContentDescription("关闭大图").performClick()
        compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-select-0"));compose.onNodeWithTag("ocr-select-0").performClick()
        // Real OCR may leave the contract label unknown. Simulate checking the enlarged synthetic
        // source and entering that fact; never make the production parser guess the contract.
        if(c.fields["contractCode"]!="rb2701") {
            compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-field-contractCode"))
            compose.onNodeWithTag("ocr-field-contractCode").performTextReplacement("rb2701");compose.onNodeWithTag("ocr-field-contractCode").performImeAction()
        }
        (c.fields.keys+"contractCode").forEach { key->compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-verify-$key"));compose.onNodeWithTag("ocr-verify-$key").performScrollTo().performClick() }
        compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-confirm-closed"));compose.onNodeWithTag("ocr-confirm-closed").performScrollTo().performClick()
        compose.onNodeWithTag("ocr-keep-image").performScrollTo().performClick();compose.onNodeWithTag("ocr-keep-image").assertIsOn();compose.onNodeWithTag("ocr-create").assertIsEnabled();capture("20-ocr-confirmation")
        compose.onNodeWithTag("ocr-create").performClick();compose.waitUntil(15000){model.state.value.trades.size==7 && !model.busy.value}
        val t=model.state.value.trades.first { it.netPnl=="-25.35" };assertEquals("futures",t.market);assertEquals("short",t.direction);assertEquals("rb2701",t.professional!!.contractCode)
        runBlocking{assertEquals(1,db.trades().allAttachments().count{it.recordKey==t.recordKey})}
        compose.waitUntil(15000){model.state.value.attachments.any{it.recordKey==t.recordKey}}
        assertEquals(1,model.state.value.attachments.count { it.recordKey==t.recordKey });assertEquals("12.",model.state.value.drafts.single().amount)
        compose.onNodeWithTag("ocr-create").assertIsNotEnabled();compose.onNodeWithContentDescription("取消截图识别").performClick()
    }
    @Test fun insightsExplainAndDrillRealRecordsAndPreflightTracksGoalAndPersonalCheckHistory() {
        compose.onNodeWithTag("open-smart-insights").performScrollTo().performClick();compose.waitUntil(15000){compose.onAllNodesWithTag("insight-sample-error:chase").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("smart-insights-list").performScrollToNode(hasTestTag("insight-sample-error:chase"));capture("21-smart-insights")
        compose.onNodeWithTag("smart-insights-list").performScrollToNode(hasTestTag("insight-trades-error:chase"));compose.onNodeWithTag("insight-trades-error:chase").performScrollTo().performClick()
        compose.onNodeWithTag("report-trades-list").assertIsDisplayed();compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithTag("smart-insights-list").performScrollToNode(hasText("盘前纪律检查清单"));compose.onNodeWithText("盘前纪律检查清单").performClick()
        compose.onNodeWithTag("preflight-list").performScrollToNode(hasTestTag("check-auto:mistake:chase"));compose.onNodeWithTag("check-auto:mistake:chase").performScrollTo().performClick()
        compose.waitUntil(15000){model.state.value.preflightChecks.any { it.itemKey=="auto:mistake:chase" && it.checked }}
        compose.onNodeWithTag("preflight-list").performScrollToNode(hasTestTag("check-auto:goal:${model.state.value.goals.single().id}"));capture("22-preflight-checklist")
        compose.onNodeWithTag("preflight-list").performScrollToNode(hasTestTag("preflight-new-text"));compose.onNodeWithTag("preflight-new-text").performTextInput("核对最大计划风险")
        compose.onNodeWithTag("preflight-add").performScrollTo().performClick();compose.waitUntil(15000){model.state.value.preflightItems.size==1 && !model.busy.value}
        val item=model.state.value.preflightItems.single();compose.onNodeWithTag("preflight-list").performScrollToNode(hasTestTag("check-manual:${item.id}"));compose.onNodeWithTag("check-manual:${item.id}").performScrollTo().performClick()
        compose.waitUntil(15000){model.state.value.preflightChecks.size==2 && !model.busy.value};compose.waitUntil(15000){model.message.value==null}
        compose.onNodeWithTag("preflight-list").performScrollToNode(hasText("归档：核对最大计划风险"));compose.onNodeWithText("归档：核对最大计划风险").performClick()
        compose.waitUntil(15000){model.state.value.preflightItems.single().archived && !model.busy.value};assertEquals(2,model.state.value.preflightChecks.size)
        runBlocking{assertEquals(6,db.trades().allTrades().size);assertTrue(db.trades().allReviews().isEmpty())}
    }
    @Test fun blockedScreenshotStaysDraftAndRestoreRequiresFreshConfirmation() {
        compose.onNodeWithTag("open-ocr").performScrollTo().performClick()
        val c=OcrCandidate(mapOf("symbol" to "螺纹钢","realizedPnl" to "-25.35"),blocked=true,closedEvidence=false)
        compose.runOnIdle {ocr.state.value=OcrSession(candidates=listOf(c))}
        compose.onNodeWithTag("ocr-create").assertIsNotEnabled();compose.onNodeWithTag("ocr-save-draft").performClick();compose.waitUntil(15000){model.state.value.ocrDrafts.size==1 && !model.busy.value}
        val d=model.state.value.ocrDrafts.single();compose.runOnIdle{ocr.clear()}
        compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-restore-${d.id}"));compose.onNodeWithTag("ocr-restore-${d.id}").performClick()
        compose.onNodeWithTag("ocr-create").assertIsNotEnabled();assertEquals(6,model.state.value.trades.size);assertTrue(ocr.state.value.raw.isEmpty())
    }
    @Test fun duplicateWarningShowsExistingFactsAndNeedsExplicitConsentBeforeNewRecord() {
        val t=model.state.value.trades.first()
        val c=OcrTradeParser.parse("Date: ${LocalDate.ofEpochDay(t.date)}\nMarket: ${t.market}\nSymbol: ${t.symbol}\nDirection: ${t.direction}\nCurrency: ${t.currency}\nRealized P&L: ${t.netPnl}").single()
        compose.onNodeWithTag("open-ocr").performScrollTo().performClick();compose.runOnIdle{ocr.state.value=OcrSession(candidates=listOf(c))}
        compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-select-0"));compose.onNodeWithTag("ocr-select-0").performClick()
        c.fields.keys.forEach{key->compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-verify-$key"));compose.onNodeWithTag("ocr-verify-$key").performScrollTo().performClick()}
        compose.onNodeWithTag("ocr-list").performScrollToNode(hasTestTag("ocr-confirm-closed"));compose.onNodeWithTag("ocr-confirm-closed").performScrollTo().performClick()
        compose.onNodeWithTag("ocr-create").performClick();compose.onNodeWithText("发现疑似重复交易").assertIsDisplayed();assertEquals(6,model.state.value.trades.size)
        compose.onNodeWithText("已查看，仍新增此真实交易").performClick()
        compose.waitUntil(15000){model.state.value.trades.size==7 && !model.busy.value}
        runBlocking{assertEquals(t,db.trades().findTradeByKey(t.recordKey))}
    }
    private fun capture(name:String) {
        compose.waitForIdle();val instrumentation=InstrumentationRegistry.getInstrumentation();val frames=CountDownLatch(1)
        instrumentation.runOnMainSync{Choreographer.getInstance().postFrameCallback{Choreographer.getInstance().postFrameCallback{frames.countDown()}}};assertTrue(frames.await(5,TimeUnit.SECONDS));SystemClock.sleep(250)
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val folder=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v07-screenshots").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"$name.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/TradeReview-v07-ui");put(MediaStore.MediaColumns.IS_PENDING,1)}
        val uri=requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values));requireNotNull(context.contentResolver.openOutputStream(uri)).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);bitmap.recycle()
    }
}
