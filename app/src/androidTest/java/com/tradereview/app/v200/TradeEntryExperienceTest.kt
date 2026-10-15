package com.tradereview.app.v200

import android.os.Build
import android.os.Process
import android.os.ParcelFileDescriptor
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.GsonBuilder
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.ui.BackupViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Every business execution is entered through production MainActivity. Only reset/clock are injected. */
@RunWith(AndroidJUnit4::class)
class TradeEntryExperienceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val gson=GsonBuilder().serializeNulls().setPrettyPrinting().create()
    private lateinit var app:TradeReviewApplication
    private lateinit var model:V200ViewModel
    private val observations=mutableListOf<Map<String,Any?>>()
    private var taps=0;private var scrolls=0;private val fields=linkedSetOf<String>();private val steps=mutableListOf<String>()
    private fun waitFor(label:String,p:()->Boolean){try{ui.waitUntil(45000){p()}}catch(e:ComposeTimeoutException){throw AssertionError("$label; busy=${model.busy.value}; problem=${model.problem.value}",e)}}
    private fun facts()=runBlocking{model.repository.read()}
    private fun visible(node:SemanticsNodeInteraction):SemanticsNodeInteraction {if(runCatching{node.assertIsDisplayed()}.isFailure){node.performScrollTo();scrolls++};return node.assertIsDisplayed()}
    private fun tap(tag:String){visible(ui.onNodeWithTag(tag)).assertIsEnabled().performClick();taps++;steps+=tag}
    private fun text(tag:String,value:String){tap(tag);ui.onNodeWithTag(tag).performTextReplacement(value);fields+=tag}
    private fun selected(tag:String){val n=ui.onNodeWithTag(tag);if(n.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected){false}!=true)tap(tag)}
    private fun displayed(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}
    private fun equal(expected:String,actual:String)=assertEquals(expected,0,BigDecimal(expected).compareTo(BigDecimal(actual)))
    private fun clock(day:LocalDate){ui.runOnIdle{model.entryClock=Clock.fixed(day.atTime(10,0).atZone(AutomaticEntry.beijing).toInstant(),ZoneOffset.UTC)}}
    private fun page(index:Int){ui.onNodeWithTag("v2-main-page-$index").performClick()}
    private fun screenshot(name:String){ui.waitForIdle();android.os.SystemClock.sleep(150);val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot());File(output(),"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    private fun output()=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))).apply{mkdirs()}
    private fun row(e:LedgerExecution){ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("execution-${e.id}"));ui.onNodeWithTag("execution-${e.id}").assertIsDisplayed();ui.onNode(hasText("${e.quantity}${if(e.accountId=="stock")"股"else"手"} × ${e.price} · 费用 ${amount(e.fees)}") and hasAnyAncestor(hasTestTag("execution-${e.id}")),useUnmergedTree=true).assertIsDisplayed()}
    private fun begin(market:String,code:String,action:String,price:String,quantity:String){
        page(1);taps=0;scrolls=0;fields.clear();steps.clear();tap("new-execution");selected("entry-market-$market")
        text("execution-code",code)
        selected(if(market=="stock")"execution-side-$action"else"execution-action-$action")
        text("execution-price",price);text("execution-quantity",quantity)
        for(tag in listOf("execution-second","execution-sequence","execution-multiplier","execution-margin","execution-trading-day","execution-parameters"))ui.onNodeWithTag(tag).assertDoesNotExist()
        ui.onNodeWithTag("save-execution").assertIsDisplayed()
    }
    private fun enter(market:String,code:String,action:String,price:String,quantity:String,fee:String,margin:String?,remaining:String,net:String="0",capture:String?=null):LedgerExecution {
        val before=facts();begin(market,code,action,price,quantity)
        val expectedFee="预计手续费：${AutomaticEntry.money(fee)}"
        waitFor("实时报价 $code"){displayed("entry-estimated-fee")==expectedFee}
        val shownFee=displayed("entry-estimated-fee")
        val shownMargin=if(market=="futures"){waitFor("实时保证金"){displayed("entry-estimated-margin").endsWith(AutomaticEntry.money(margin))};displayed("entry-estimated-margin")}else null
        capture?.let(::screenshot)
        tap("save-execution")
        waitFor("无多余确认的正常保存"){!model.busy.value && !model.editor.value && model.state.value.ledger?.executions?.size==before.executions.size+1}
        assertNull("普通录入不应弹顺序/参数确认",model.pending.value)
        val after=facts();val e=after.executions.single{it.id !in before.executions.map{v->v.id}}
        equal(fee,e.fees);equal(net,exact(sum(after.matches.filter{it.closeExecutionId==e.id}.map{decimal(it.net)})))
        equal(remaining,exact(sum(after.cycles.filter{it.accountId==market && it.code==code && it.direction==(if(action.startsWith("sell-open") || action.startsWith("buy-close"))"short"else"long")}.map{decimal(it.remaining)})))
        row(e)
        observations+=mapOf("id" to e.id,"market" to market,"code" to code,"action" to action,"price" to price,"quantity" to quantity,"expectedFees" to fee,"displayedFees" to shownFee,"savedFees" to e.fees,"expectedMargin" to margin,"displayedMargin" to shownMargin,"expectedRemaining" to remaining,"expectedRealizedNet" to net,"actualRealizedNet" to exact(sum(after.matches.filter{it.closeExecutionId==e.id}.map{decimal(it.net)})),"controlTapsIncludingEntryAndSave" to taps,"fieldsTyped" to fields.toList(),"requiredScrollCommands" to scrolls,"extraConfirmation" to false,"saveVisible" to true,"steps" to steps.toList(),"result" to "PASS")
        return e
    }
    private fun history(){page(1);ui.onNodeWithText("历史成交").performClick()}
    private fun openDetails(e:LedgerExecution){history();row(e);ui.onNodeWithTag("execution-${e.id}").performClick()}
    private fun back(){ui.onNodeWithContentDescription("返回主页面").performClick()}
    private fun delete(e:LedgerExecution){openDetails(e);ui.onNodeWithTag("delete-execution").performScrollTo().performClick();waitFor("删除预览"){model.pending.value!=null && !model.busy.value};val before=facts();ui.onNodeWithTag("confirm-execution-change").performClick();waitFor("删除事务提交"){!model.busy.value && model.pending.value==null && model.state.value.ledger!!.executions.size==before.executions.size-1};assertFalse(facts().executions.any{it.id==e.id});back()}
    private fun statistics(expected:String){page(3);ui.onNodeWithTag("v2-statistics").performScrollToIndex(0);ui.onNodeWithTag("statistics-period-month").performClick();waitFor("统计刷新"){displayed("period-realized-net")=="￥$expected"};ui.onNodeWithTag("period-realized-net").assertTextEquals("￥$expected")}

    @Test fun actualFormJourneyAndReopenEvidence(){
        app=ui.activity.application as TradeReviewApplication
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        if(InstrumentationRegistry.getArguments().getString("entryPhase")=="reopen"){verifyAfterProcessRestart();return}
        val empty=LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")))
        runBlocking{app.database.withTransaction{model.repository.write(empty,true)}}
        ui.runOnIdle{model.page(1);model.closeEditor();model.handle["v2-entry-fields"]=null}
        waitFor("空账本开始"){model.state.value.ledger==empty}
        val today=AutomaticEntry.today();clock(today.minusDays(1))
        enter("stock","600000","buy","10","1000","1.00",null,"1000",capture="70-entry-stock-filled")
        // The SAME real form rejects T+1; the test changes only the injected clock afterwards.
        begin("stock","600000","sell","10","1000");tap("save-execution")
        waitFor("T+1拒绝"){!model.busy.value && model.problem.value?.contains("T+1")==true}
        assertEquals(1,facts().executions.size);tap("entry-cancel");clock(today)
        enter("stock","600000","sell","10","1000","6.00",null,"0","-7")
        val a=enter("futures","rb2701","buy-open","3500","2","7.01","4900.00","2",capture="71-entry-futures-filled")
        val b=enter("futures","rb2701","buy-open","3510","3","10.54","7371.00","5")
        val close=enter("futures","rb2701","sell-close","3530","4","14.13","2457.00","1","971.833333333333")
        screenshot("72-entry-saved-list")
        page(1);ui.onNodeWithText("当前持仓").performClick();ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("position-cycle_${a.id}"));screenshot("73-entry-partial-position")
        val partial=facts();assertEquals("1",partial.lots.first{it.executionId==b.id}.remaining);assertEquals("0",partial.lots.first{it.executionId==a.id}.remaining)
        enter("futures","rb2701","sell-open","4000","1","4.01","2800.00","1")
        enter("futures","rb2701","buy-close","3990","1","4.00","2457.00","0","91.99")
        enter("futures","al2701","buy-open","20000","2","6.01","22000.00","2")
        openDetails(close);ui.onNodeWithTag("edit-execution").performScrollTo().performClick();text("execution-price","3540");text("execution-quantity","3")
        waitFor("修改实时报价"){displayed("entry-estimated-fee")=="预计手续费：¥10.63"}
        tap("save-execution");waitFor("修改影响预览"){model.pending.value!=null && !model.busy.value};assertEquals("3530",facts().executions.first{it.id==close.id}.price)
        ui.onNodeWithTag("confirm-execution-change").performClick();waitFor("修改确认"){!model.busy.value && model.pending.value==null && model.state.value.ledger!!.executions.first{it.id==close.id}.price=="3540"}
        val changed=facts().executions.first{it.id==close.id};assertEquals(close.ordinal,changed.ordinal);equal("10.63",changed.fees);equal("1078.846666666667",exact(sum(facts().matches.filter{it.closeExecutionId==close.id}.map{decimal(it.net)})));equal("2",facts().cycles.first{it.id=="cycle_${a.id}"}.remaining)
        observations+=mapOf("operation" to "edit","id" to close.id,"price" to "3540","quantity" to "3","expectedFees" to "10.63","displayedFees" to "预计手续费：¥10.63","savedFees" to changed.fees,"expectedRemaining" to "2","actualRealizedNet" to "1078.846666666667","positionPreserved" to (changed.ordinal==close.ordinal),"extraConfirmation" to true,"result" to "PASS")
        back();delete(changed);equal("5",facts().cycles.first{it.id=="cycle_${a.id}"}.remaining)
        observations+=mapOf("operation" to "delete-close","expectedRemaining" to "5","result" to "PASS")
        delete(b);equal("2",facts().cycles.first{it.id=="cycle_${a.id}"}.remaining);assertEquals(3,facts().audits.size)
        observations+=mapOf("operation" to "delete-open","expectedRemaining" to "2","result" to "PASS")
        val beforeUnknown=facts();begin("futures","zz2701","buy-open","100","1")
        waitFor("未知品种提示"){ui.onAllNodesWithTag("entry-unverified").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("entry-estimated-fee").assertTextEquals("预计手续费：暂无法估算");screenshot("76-entry-unknown-contract")
        tap("save-execution");waitFor("未知品种拒绝保存"){!model.busy.value && model.problem.value!=null};assertEquals(beforeUnknown,facts());tap("entry-cancel")
        statistics("84.99");screenshot("74-entry-statistics")
        exerciseKeyboard();exerciseRealSafBackup()
        val expected=facts();assertEquals(6,expected.executions.size);assertEquals("84.99",exact(sum(expected.matches.map{decimal(it.net)})))
        val proof=mapOf("api" to Build.VERSION.SDK_INT,"pid" to Process.myPid(),"ledger" to LedgerJson.element(expected),"observations" to observations,"allExecutionsCreatedViaRealForm" to true,"syntheticOnly" to true,"baselineInputCountsExcludeKeyboardKeyStrokes" to true,"zipSafExportReadbackAndConfirmedReplace" to true,"result" to "PASS")
        val json=gson.toJson(proof)
        File(app.filesDir,"entry-experience-expected.json").writeText(json)
        val reportName=if(InstrumentationRegistry.getArguments().getString("entryPhase")=="journey")"trade-entry-experience"else"entry-regression"
        File(output(),"$reportName-api${Build.VERSION.SDK_INT}.json").writeText(json)
    }

    private fun exerciseKeyboard(){
        val automation=instrumentation.uiAutomation;val flags=automation.serviceInfo.flags
        val prior=shell("settings get secure show_ime_with_hard_keyboard").trim()
        try{
            automation.serviceInfo=automation.serviceInfo.apply{this.flags=this.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            shell("settings put secure show_ime_with_hard_keyboard 1")
            begin("stock","000001","buy","10","100")
            ui.onNodeWithTag("execution-price").performScrollTo().performTouchInput{click()}
            waitFor("真实软键盘"){automation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}}
            ui.onNodeWithTag("save-execution").assertIsDisplayed();screenshot("75-entry-keyboard")
            tap("entry-cancel")
        }finally{if(prior=="null")shell("settings delete secure show_ime_with_hard_keyboard")else shell("settings put secure show_ime_with_hard_keyboard $prior");automation.serviceInfo=automation.serviceInfo.apply{this.flags=flags}}
    }

    private fun exerciseRealSafBackup(){
        val before=facts();val device=UiDevice.getInstance(instrumentation)
        lateinit var backup:BackupViewModel
        ui.runOnIdle{backup=ViewModelProvider(ui.activity)[BackupViewModel::class.java]}
        ui.onNodeWithTag("open-backup").performClick();ui.onNodeWithTag("v2-export-backup").performClick();ui.onNodeWithText("完整 ZIP（推荐）").performClick()
        val filename="entry-proof-${Build.VERSION.SDK_INT}-${System.nanoTime()}.zip"
        requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),20000)){"系统保存文件名输入框未出现"}.text=filename
        requireNotNull(device.wait(Until.findObject(By.textMatches("(?i)(save|保存)")),15000)){"系统保存按钮未出现"}.click()
        waitFor("真实SAF导出并回读校验"){!backup.busy.value && backup.message.value?.contains("已重新读取并通过 SHA-256")==true}
        back();val removable=before.executions.first{it.code=="al2701"};delete(removable);assertEquals(5,facts().executions.size)
        ui.onNodeWithTag("open-backup").performClick();ui.onNodeWithTag("v2-import-backup").performClick()
        var file=device.wait(Until.findObject(By.text(filename)),15000)
        if(file==null){requireNotNull(device.findObject(By.descMatches("(?i)(Show roots|Open navigation drawer|显示根目录|打开导航抽屉)"))){"文件选择器根目录入口缺失"}.click();requireNotNull(device.wait(Until.findObject(By.textMatches("(?i)(Downloads|下载)")),10000)).click();file=device.wait(Until.findObject(By.text(filename)),15000)}
        requireNotNull(file){"未在系统选择器找到刚导出的ZIP"}.click()
        waitFor("ZIP校验预览"){!backup.busy.value && backup.preview.value!=null}
        ui.onNodeWithTag("v2-backup").performScrollToNode(hasText("覆盖备份范围"));ui.onNodeWithText("覆盖备份范围").performClick()
        ui.onNodeWithTag("v2-backup").performScrollToNode(hasTestTag("v2-restore-continue"));ui.onNodeWithTag("v2-restore-continue").performClick()
        assertEquals(5,facts().executions.size);ui.onNodeWithTag("v2-confirm-restore").performClick()
        waitFor("ZIP二次确认事务恢复"){!backup.busy.value && backup.preview.value==null && model.state.value.ledger?.executions?.size==6}
        assertEquals(before,facts());back();statistics("84.99")
    }

    private fun verifyAfterProcessRestart(){
        val proof=com.google.gson.JsonParser.parseString(File(app.filesDir,"entry-experience-expected.json").readText()).asJsonObject
        assertNotEquals("必须是真正的新进程",proof["pid"].asInt,Process.myPid())
        val expected=LedgerJson.parse(proof["ledger"])
        waitFor("重启后本机记录载入"){model.state.value.ledger==expected}
        assertEquals(expected,facts());history();expected.executions.forEach(::row)
        page(1);ui.onNodeWithText("当前持仓").performClick()
        expected.cycles.filter{decimal(it.remaining)>BigDecimal.ZERO}.forEach{c->ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("position-${c.id}"));ui.onNodeWithTag("position-${c.id}").assertIsDisplayed()}
        statistics("84.99");screenshot("77-entry-after-process-restart")
        File(output(),"entry-reopen-api${Build.VERSION.SDK_INT}.json").writeText(gson.toJson(mapOf("api" to Build.VERSION.SDK_INT,"previousPid" to proof["pid"].asInt,"pid" to Process.myPid(),"records" to expected.executions.size,"allFactsIdentical" to true,"listPositionsAndStatisticsVerifiedViaUi" to true,"result" to "PASS")))
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
}
