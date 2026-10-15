package com.tradereview.app.v200

import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.ActivityInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.*
import com.tradereview.app.data.*
import com.tradereview.app.backup.*
import com.tradereview.app.evidence.*
import com.tradereview.app.release.BackupDocumentProvider
import com.tradereview.app.ui.BackupViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Actual production MainActivity. Fixtures are synthetic and only run on disposable emulators. */
@RunWith(AndroidJUnit4::class)
class V200MainUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val failureEvidence=object:org.junit.rules.TestWatcher(){override fun failed(error:Throwable,description:org.junit.runner.Description){runCatching{val root=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v200-failures").apply{mkdirs()};val detail=File(root,"${description.methodName}.txt");detail.writeText("Synthetic fixtures only\n"+error.stackTraceToString());runCatching{detail.appendText("\n"+compose.onRoot(useUnmergedTree=true).printToString())}.onFailure{detail.appendText("\nSemantics unavailable: ${it.javaClass.simpleName}")};val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();image?.let{File(root,"${description.methodName}.png").outputStream().use{out->it.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)};it.recycle()}}}}
    private val app get()=compose.activity.application as TradeReviewApplication
    private val db get()=app.database
    private lateinit var model:V200ViewModel
    private val today=LocalDate.now().toEpochDay()
    private val first=LocalDate.now().withDayOfMonth(1).toEpochDay()-1
    private val lossDay=maxOf(today-1,LedgerPeriod.week(LocalDate.now()).start,first+1)
    private val flatDay=first+1
    private val todayNet get()=if(lossDay==today)"134.696"else"137.696"
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    @Before fun reset(){require(Build.HARDWARE in listOf("ranchu","goldfish"));runBlocking{db.withTransaction{LedgerRepository(db).write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),strategies=listOf(LedgerStrategy("synthetic","合成策略"))),true)}};compose.runOnIdle{model=ViewModelProvider(compose.activity)[V200ViewModel::class.java];model.page(0);model.closeEditor();model.handle["v2-entry-fields"]=null};val expected=runBlocking{model.repository.read()};wait{model.state.value.ledger==expected};compose.onNodeWithTag("v2-home").assertIsDisplayed()}
    private fun wait(predicate:()->Boolean)=compose.waitUntil(30000){predicate()}
    private fun page(n:Int){compose.onNodeWithTag("v2-main-page-$n").performClick()}
    private fun field(tag:String,text:String,scroll:Boolean=true){val node=compose.onNodeWithTag(tag);if(scroll)node.performScrollTo()else node.assertIsDisplayed();node.performTextReplacement(text)}
    private fun start(){page(1);compose.onNodeWithTag("new-execution").performClick();compose.onNodeWithTag("execution-code").assertExists()}
    private fun fill(price:String="10",quantity:String="100"){field("execution-code","600000");field("execution-name","合成股票");field("execution-price",price);field("execution-quantity",quantity)}
    private suspend fun save(e:LedgerExecution){val p=model.repository.preview(e);model.repository.confirm(p,true)}
    private fun open(id:String="a",day:Long=first,code:String="600000",price:String="10",quantity:String="100",fees:String="0.1")=LedgerExecution(id,"stock",code,"合成$code",day,600,day,0,"buy","open",price,quantity,fees,second=1,strategyId="synthetic")
    private fun sample(){runBlocking{save(open());save(open("close",today,price="11",quantity="40",fees="0.264").copy(side="sell",effect="close",second=2));save(open("loss_open",code="000001",quantity="1",fees="1"));save(open("loss_close",lossDay,"000001","9","1","1").copy(side="sell",effect="close",second=3));save(open("flat_open",code="000002",quantity="1",fees="0"));save(open("flat_close",flatDay,"000002","10","1","0").copy(side="sell",effect="close",second=4));save(LedgerExecution("future_open","futures","rb2701","合成螺纹钢",first,1260,first+1,0,"buy","open","3500","2","2",multiplier="10",marginRate="0.1",parametersConfirmed=true,stopLoss="3490",second=1));save(LedgerExecution("future_close","futures","rb2701","合成螺纹钢",today,610,today,0,"sell","close","3510","1","1",multiplier="10",marginRate="0.1",parametersConfirmed=true,second=1));model.repository.cash(LedgerCashFlow("in","stock",today,"in","1000","合成入金"))};wait{model.state.value.ledger?.executions?.size==8};assertEquals("134.696",exact(sum(model.state.value.closed.map{it.net})))}
    private fun back(){compose.onNodeWithContentDescription("返回主页面").performClick()}
    private fun capture(name:String){compose.waitForIdle();android.os.SystemClock.sleep(200);val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());val folder=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v200-screenshots").apply{mkdirs()};File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}

    @Test fun fivePagesAndFixedRiskEntryHaveNoHomeTransactionOrOldFeatureButtons(){compose.onNodeWithTag("new-execution").assertDoesNotExist();compose.onNodeWithText("智能洞察").assertDoesNotExist();compose.onNodeWithText("识别截图").assertDoesNotExist();compose.onNodeWithTag("open-risk-strategy").assertIsDisplayed();capture("v200-home-empty");page(1);compose.onNodeWithTag("new-execution").assertIsDisplayed();page(2);compose.onNodeWithTag("new-plan").assertIsDisplayed();page(3);compose.onNodeWithTag("statistics-period-week").assertIsDisplayed();page(4);compose.onNodeWithTag("account-stock").assertIsDisplayed();compose.onNodeWithTag("open-backup").performClick();compose.onNodeWithTag("v2-export-backup").assertIsDisplayed();compose.onNodeWithTag("v2-import-backup").assertIsDisplayed();compose.onNodeWithTag("v2-export-csv").assertIsDisplayed()}

    @Test fun stockEntryEditDeleteRequirePreviewAndPreserveActualAudit(){start();fill();compose.onNodeWithTag("save-execution").performClick();wait{model.state.value.ledger?.executions?.size==1 && !model.busy.value};val original=model.state.value.ledger!!.executions.single();assertEquals("0.1",original.fees);assertEquals("0",sellable(model.state.value.ledger!!,model.state.value.ledger!!.cycles.single(),today));compose.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("position-cycle_${original.id}"));capture("41-v200-positions");compose.onNodeWithText("历史成交").performClick();compose.onNodeWithTag("execution-${original.id}").performClick();compose.onNodeWithTag("edit-execution").performScrollTo().performClick();field("execution-price","12");compose.onNodeWithTag("save-execution").performClick();wait{model.pending.value!=null};assertEquals(original,runBlocking{model.repository.read().executions.single()});compose.onNodeWithTag("confirm-execution-change").performClick();wait{!model.busy.value && model.pending.value==null && model.state.value.ledger!!.executions.single().price=="12"};compose.onNodeWithTag("delete-execution").performScrollTo().performClick();wait{model.pending.value!=null};compose.onNodeWithTag("confirm-execution-change").performClick();wait{!model.busy.value && model.state.value.ledger!!.executions.isEmpty()};val after=runBlocking{model.repository.read()};assertEquals(2,after.audits.size);assertTrue(after.cycles.isEmpty());assertEquals(original.id,after.audits.first().executionId)}

    @Test fun separateRiskFundsAndCustomStrategyRequireExplicitExceededRiskConfirmation() {
        fun step(label:String,predicate:()->Boolean) {
            try { wait { predicate() } } catch(error:androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("$label: busy=${model.busy.value}, problem=${model.problem.value}, pending=${model.pending.value!=null}, executions=${model.state.value.ledger?.executions?.size}",error)
            }
        }
        compose.onNodeWithTag("open-risk-strategy").performClick()
        compose.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasTestTag("risk-fund-stock"))
        field("risk-fund-stock","10000");field("risk-limit-stock","5")
        compose.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasTestTag("save-risk-stock"))
        compose.onNodeWithTag("save-risk-stock").assertIsEnabled().performClick()
        step("保存独立风险金") { model.state.value.ledger!!.risks.first { it.id=="stock" }.riskFund=="10000" && !model.busy.value }
        assertNull(model.state.value.ledger!!.risks.first { it.id=="futures" }.riskFund)
        capture("45-v200-risk-strategy")
        compose.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasTestTag("risk-section-strategies"))
        compose.onNodeWithTag("risk-section-strategies").performClick()
        compose.onNodeWithTag("new-strategy").performClick()
        field("strategy-name","手机合成策略");field("strategy-description","可编辑，稳定ID")
        compose.onNodeWithTag("save-strategy").assertIsEnabled().performClick()
        step("保存自定义策略") { !model.busy.value && model.state.value.ledger!!.strategies.any { it.name=="手机合成策略" } }
        back();start();fill();field("execution-stop","9")
        compose.onNodeWithTag("execution-strategy").performScrollTo().performClick()
        compose.onNodeWithText("手机合成策略").performClick()
        compose.onNodeWithTag("save-execution").assertIsEnabled().performClick()
        step("生成超限确认预览") { model.pending.value!=null && !model.busy.value }
        assertTrue(runBlocking { model.repository.read().executions.isEmpty() })
        compose.onNodeWithTag("confirm-execution-change").assertIsDisplayed().assertIsEnabled()
        capture("53-v200-risk-confirmation")
        compose.onNodeWithTag("confirm-execution-change").performClick()
        step("确认真实成交入库") { !model.busy.value && model.state.value.ledger!!.executions.size==1 }
        val e=model.state.value.ledger!!.executions.single()
        assertTrue(e.riskConfirmed);assertEquals("100",e.riskAmount)
        assertEquals("10000",e.riskFundSnapshot);assertNotNull(e.strategyId)
    }


    @Test fun symbolPlanAndOpenAndClosedPositionReviewsKeepFactsSeparate(){page(2);compose.onNodeWithTag("new-plan").performClick();field("plan-code","000001");field("plan-name","未持仓合成股票");field("plan-entry","9");field("plan-note","计划不能伪造成交");compose.onNodeWithTag("save-plan").performClick();wait{!model.busy.value && model.state.value.ledger!!.plans.size==1};assertTrue(model.state.value.ledger!!.executions.isEmpty());runBlocking{save(open())};wait{model.state.value.ledger!!.cycles.size==1};compose.onNodeWithText("盘后复盘").performClick();compose.onNodeWithTag("v2-reviews").performScrollToNode(hasTestTag("review-cycle-cycle_a"));compose.onNodeWithTag("review-cycle-cycle_a").performClick();field("position-review-note","仍持有时复盘");compose.onNodeWithTag("save-position-review").performClick();wait{!model.busy.value && model.state.value.ledger!!.reviews.size==1};runBlocking{save(open("full_close",today,price="11",fees="0.66").copy(side="sell",effect="close",second=2))};wait{model.state.value.ledger!!.cycles.single().closedDay!=null};val facts=model.state.value.ledger!!.executions;compose.onNodeWithTag("review-cycle-cycle_a").performClick();field("position-review-note","完全平仓后继续复盘");compose.onNodeWithTag("save-position-review").performClick();wait{!model.busy.value && model.state.value.ledger!!.reviews.size==2};assertEquals(facts,model.state.value.ledger!!.executions);capture("42-v200-review")}

    @Test fun realizedCalendarWeeklyMonthlyChartsAccountsAndFullZipRestoreUseRealFacts(){sample();capture("40-v200-home");compose.onNodeWithTag("calendar-expand").performClick();capture("46-v200-calendar");page(3);wait{compose.onAllNodesWithTag("period-realized-net").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("period-realized-net").assertTextEquals("￥134.696");capture("43-v200-statistics");compose.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("v2-daily-chart"));capture("47-v200-weekly-bars");compose.onNodeWithTag("v2-daily-chart").performTouchInput{click(androidx.compose.ui.geometry.Offset(20f,100f))};val firstDay=LedgerPeriod.week(LocalDate.now()).start;val firstValue=when(firstDay){today->todayNet;lossDay->"-3";flatDay->"0";else->null};if(firstValue==null)compose.onNodeWithText("没有平仓成交").assertIsDisplayed()else compose.onNode(hasText("￥$firstValue") and hasAnyAncestor(isDialog())).assertIsDisplayed();compose.onNodeWithText("关闭").performClick();val actions=compose.onNodeWithTag("v2-daily-chart").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions];compose.runOnIdle{assertTrue(actions.first{it.label.contains(LocalDate.now().toString())}.action())};compose.onNode(hasText("￥$todayNet") and hasAnyAncestor(isDialog())).assertIsDisplayed();compose.onNodeWithText("关闭").performClick();compose.onNodeWithTag("v2-statistics").performScrollToIndex(0);compose.onNodeWithTag("statistics-period-month").performClick();wait{compose.onAllNodesWithTag("statistics-results-month-all").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("v2-daily-chart"));capture("57-v200-month-flat-and-empty");compose.onNodeWithTag("v2-daily-chart-scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){scroll->scroll((LocalDate.now().dayOfMonth-4).coerceAtLeast(0)*52f*app.resources.displayMetrics.density,0f)};capture("48-v200-monthly-bars");compose.onNodeWithTag("v2-daily-chart-scroll").performTouchInput{swipeLeft()};compose.onNodeWithTag("v2-statistics").performScrollToNode(hasText("本月品种／合约净盈亏",substring=true));compose.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("distribution-000001"));capture("49-v200-contract-distribution");page(4);capture("44-v200-accounts");compose.onNodeWithTag("v2-accounts").performScrollToNode(hasTestTag("new-cashflow"));compose.onNodeWithTag("new-cashflow").assertIsDisplayed().performClick();compose.onNodeWithTag("cashflow-amount").performScrollTo().performTextReplacement("200");compose.onNodeWithTag("save-cashflow").performClick();wait{!model.busy.value && model.state.value.ledger!!.cashFlows.size==2};assertEquals("134.696",exact(sum(model.state.value.closed.map{it.net})));runBlocking{val legacyKey="00000000000000000000000000002000";if(db.trades().findTradeByKey(legacyKey)==null){db.trades().insert(Trade(recordKey=legacyKey,date=first,market="forex",symbol="合成旧美元",currency="USD",netPnl="-123.45",note="旧档案，不生成新持仓"));AttachmentRepository(db,app.evidenceStore).importImage(app.contentResolver,TestImageProvider.uri(),legacyKey,"before","旧档案合成截图")}};assertEquals("134.696",exact(sum(model.state.value.closed.map{it.net})));page(1);compose.onNodeWithTag("legacy-archive").performClick();wait{compose.onAllNodesWithText("USD",substring=true).fetchSemanticsNodes().isNotEmpty()};capture("54-v200-legacy-archive");back();val service=FullZipRepository(db,app.evidenceStore);val uri=BackupDocumentProvider.uri();runBlocking{app.contentResolver.openOutputStream(uri,"wt")!!.use{service.export(it)}};val before=runBlocking{model.repository.read()};compose.onNodeWithTag("open-backup").performClick();lateinit var backup:BackupViewModel;compose.runOnIdle{backup=ViewModelProvider(compose.activity)[BackupViewModel::class.java];backup.readBackup(uri)};wait{backup.preview.value!=null && !backup.busy.value};compose.onNodeWithTag("v2-backup").performScrollToNode(hasTestTag("v2-restore-continue"));compose.onNodeWithTag("v2-restore-continue").assertIsDisplayed().performClick();wait{compose.onAllNodesWithTag("v2-confirm-restore").fetchSemanticsNodes().size==1};compose.onNodeWithTag("v2-confirm-restore").assertIsDisplayed();capture("55-v200-restore-confirmation");compose.onNodeWithTag("v2-confirm-restore").performClick();wait{!backup.busy.value && backup.preview.value==null};assertEquals(before,runBlocking{model.repository.read()})}

    // Configuration changes temporarily leave ActivityScenario without an Activity.
    // Inspect the lifecycle registry atomically on main, never call compose.activity
    // from a polling predicate in that gap.
    private fun awaitResumedConfiguration(predicate:(MainActivity)->Boolean = { true }) {
        wait {
            var ready=false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activities=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                ready=activities.filterIsInstance<MainActivity>().any(predicate)
            }
            ready
        }
        compose.waitForIdle()
    }
    private fun resumedActivityAction(action:(MainActivity)->Unit) {
        awaitResumedConfiguration()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            action(activity)
        }
    }

    private fun systemNight(mode:String) {
        // Use the public service API on both releases; `cmd uimode` shell support
        // differs across system images. Grant only this test operation temporarily.
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MODIFY_DAY_NIGHT_MODE")
        try {
            val manager=InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
            manager.setNightMode(if(mode=="yes")android.app.UiModeManager.MODE_NIGHT_YES else android.app.UiModeManager.MODE_NIGHT_NO)
        } finally { automation.dropShellPermissionIdentity() }
        // Android 10 can defer a system night-mode change until screen-off.
        // Exercise that real transition, then require the Activity's new config.
        if(Build.VERSION.SDK_INT==29) {
            shell("input keyevent KEYCODE_SLEEP")
            wait { shell("dumpsys power").contains("mWakefulness=Asleep") }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        val expected=if(mode=="yes")android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        awaitResumedConfiguration { it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==expected }
    }

    @Test fun realMainDraftLargeFontDarkKeyboardAndRecreationKeepSaveVisible() {
        val display=shell("wm size")
        val scale=shell("settings get system font_scale").trim()
        val keyboard=shell("settings get secure show_ime_with_hard_keyboard").trim()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val flags=automation.serviceInfo.flags
        val database=db // Application-owned; independent of Activity recreation.
        try {
            automation.serviceInfo=automation.serviceInfo.apply { this.flags=this.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            shell("wm size 640x1136")
            awaitResumedConfiguration { it.resources.displayMetrics.widthPixels==640 }
            systemNight("yes")
            shell("settings put system font_scale 1.3")
            awaitResumedConfiguration { it.resources.configuration.fontScale>=1.29f }
            start();fill("12.34")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            compose.onNodeWithTag("execution-price").performScrollTo().performTouchInput { click() }
            wait { automation.windows.any { it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            compose.onNodeWithTag("save-execution").assertIsDisplayed()
            capture("52-v200-large-font-keyboard")
            compose.activityRule.scenario.recreate()
            awaitResumedConfiguration()
            wait { compose.onAllNodesWithTag("execution-price").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("execution-price").performScrollTo().assertTextContains("12.34")
            resumedActivityAction { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitResumedConfiguration { it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("save-execution").assertIsDisplayed().performClick()
            wait { !model.busy.value && runBlocking { LedgerRepository(database).read().executions.size }==1 }
            assertEquals("12.34",runBlocking { LedgerRepository(database).read().executions.single().price })
        } finally {
            // BACK could finish MainActivity if the IME is already hidden. Hide the IME
            // explicitly; keep lifecycle assertions active instead of swallowing errors.
            resumedActivityAction {
                (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(it.window.decorView.windowToken,0)
                it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitResumedConfiguration { it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT }
            shell("settings put system font_scale ${scale.toFloatOrNull()?:1f}")
            awaitResumedConfiguration { kotlin.math.abs(it.resources.configuration.fontScale-(scale.toFloatOrNull()?:1f))<0.01f }
            systemNight("no")
            val previousSize=Regex("Override size: ([0-9]+x[0-9]+)").find(display)?.groupValues?.get(1)
            shell("wm size ${previousSize?:"reset"}")
            awaitResumedConfiguration()
            if(keyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard") else shell("settings put secure show_ime_with_hard_keyboard $keyboard")
            automation.serviceInfo=automation.serviceInfo.apply { this.flags=flags }
        }
    }
}
