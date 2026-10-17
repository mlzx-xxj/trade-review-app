package com.tradereview.app.v210

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.v200.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Real production UI: thirteen synthetic executions, independent period assertions, a saved
 * position review and an externally force-stopped reopen. Only the empty ledger and clock
 * and an explicitly synthetic historical contract rule are injected; no execution inserts.
 * Screenshots are actual UiAutomation PNGs. No user database is accessed.
 */
@RunWith(AndroidJUnit4::class)
class V210HomeStatisticsJourneyTest {
    @get:Rule(order=0) val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val evidence=object:org.junit.rules.TestWatcher(){
        private var service:AccessibilityServiceInfo?=null
        private var keyboard="null"
        override fun starting(description:org.junit.runner.Description){
            service=instrumentation.uiAutomation.serviceInfo
            instrumentation.uiAutomation.serviceInfo=instrumentation.uiAutomation.serviceInfo.apply{flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            keyboard=shell("settings get secure show_ime_with_hard_keyboard").trim()
            shell("settings put secure show_ime_with_hard_keyboard 1")
        }
        override fun failed(error:Throwable,description:org.junit.runner.Description){
            runCatching{File(output(),"v210-full-$profile-failure.txt").writeText(error.stackTraceToString())}
            runCatching{
                val diagnostics=mapOf("syntheticOnly" to true,"profile" to profile,"lastStep" to lastStep,"error" to error.stackTraceToString(),"draft" to model.restoredDraft(),"clock" to model.entryNow().toString(),"semantics" to runCatching{ui.onAllNodes(isRoot(),useUnmergedTree=true).printToString()}.getOrElse{it.javaClass.simpleName})
                com.tradereview.app.stability.V08Metrics.write("v210-full-failure-api${Build.VERSION.SDK_INT}.json",GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(diagnostics))
            }
            runCatching{capture("failure")}
        }
        override fun finished(description:org.junit.runner.Description){
            if(keyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard")else shell("settings put secure show_ime_with_hard_keyboard $keyboard")
            service?.let{instrumentation.uiAutomation.setOnAccessibilityEventListener(null);instrumentation.uiAutomation.serviceInfo=it}
        }
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val profile=InstrumentationRegistry.getArguments().getString("visualProfile")?:"suite"
    private lateinit var app:TradeReviewApplication
    private lateinit var model:V200ViewModel
    private val results=mutableListOf<Map<String,Any?>>()
    private val screens=mutableListOf<Map<String,Any?>>()
    private val densityScreens=mutableListOf<Map<String,Any?>>()
    private var taps=0;private var scrolls=0;private val fields=linkedSetOf<String>()
    private var lastStep="initial"
    private fun output()=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v210-full").apply{check(isDirectory||mkdirs())}
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun facts()=runBlocking{model.repository.read()}
    private fun waitFor(predicate:()->Boolean)=ui.waitUntil(45000){predicate()}
    private fun page(index:Int){ui.onNodeWithTag("v2-main-page-$index").performClick()}
    private fun settledViewport(){
        var previous:androidx.compose.ui.geometry.Rect?=null
        var stableSince=android.os.SystemClock.elapsedRealtime()
        ui.waitUntil(10000){
            val nodes=ui.onAllNodesWithTag("entry-scroll").fetchSemanticsNodes()
            if(nodes.isEmpty())true else{
                val bounds=nodes.single().boundsInRoot;val now=android.os.SystemClock.elapsedRealtime()
                if(bounds!=previous){previous=bounds;stableSince=now}
                now-stableSince>=150
            }
        }
    }
    private fun visible(node:SemanticsNodeInteraction):SemanticsNodeInteraction{settledViewport();if(runCatching{node.assertIsDisplayed()}.isFailure){node.performScrollTo();scrolls++;settledViewport()};return node.assertIsDisplayed()}
    private fun tap(tag:String){visible(ui.onNodeWithTag(tag)).assertIsEnabled().performClick();taps++}
    private fun type(tag:String,value:String){tap(tag);ui.onNodeWithTag(tag).performTextReplacement(value);fields+=tag;waitFor{instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}};settledViewport()}
    private fun select(tag:String){if(!ui.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected){false})tap(tag)}
    private fun shown(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}
    private fun capture(scene:String,tag:String?=null,collection:MutableList<Map<String,Any?>> = screens){
        ui.waitForIdle();android.os.SystemClock.sleep(200)
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val name="v210-full-$profile-$scene.png"
        File(output(),name).outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        val bounds=tag?.let{ui.onNodeWithTag(it).getUnclippedBoundsInRoot()}
        collection+=mapOf("scene" to scene,"file" to name,"pixels" to listOf(bitmap.width,bitmap.height),"componentDp" to bounds?.let{listOf(it.left.value,it.top.value,it.right.value,it.bottom.value)})
        bitmap.recycle()
    }
    private fun begin(market:String,code:String,action:String,price:String,quantity:String,fee:String,margin:String?=null){
        lastStep="$market $code $action price=$price quantity=$quantity expectedFee=$fee"
        page(1);taps=0;scrolls=0;fields.clear();tap("new-execution");select("entry-market-$market")
        type("execution-code",code);select(if(market=="stock")"execution-side-$action"else"execution-action-$action")
        type("execution-price",price);type("execution-quantity",quantity)
        waitFor{shown("entry-estimated-fee")=="预计手续费：$fee"}
        margin?.let{waitFor{shown("entry-estimated-margin")==it}}
        ui.onNodeWithTag("save-execution").assertIsDisplayed().assertIsEnabled()
        for(tag in listOf("execution-second","execution-sequence","execution-multiplier","execution-margin","execution-trading-day","execution-parameters"))ui.onNodeWithTag(tag).assertDoesNotExist()
    }
    private fun save(code:String,fee:String?,remaining:String):LedgerExecution{
        val before=facts();val keyboardAtSave=instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD};tap("save-execution")
        waitFor{!model.busy.value&&!model.editor.value&&model.state.value.ledger?.executions?.size==before.executions.size+1}
        assertNull("普通新增无额外确认",model.pending.value)
        val after=facts();val e=after.executions.single{v->before.executions.none{it.id==v.id}}
        assertEquals(code,e.code);assertEquals(fee,e.fees)
        val c=after.cycles.single{it.code==code};assertEquals(remaining,c.remaining)
        ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("execution-${e.id}"));ui.onNodeWithTag("execution-${e.id}").assertIsDisplayed()
        results+=mapOf("code" to code,"action" to actionLabel(e),"price" to e.price,"quantity" to e.quantity,"savedFees" to e.fees,"remaining" to c.remaining,"realized" to c.realized,"tapsIncludingEntryAndSave" to taps,"fieldsTyped" to fields.toList(),"requiredScrollCommands" to scrolls,"extraConfirmation" to false,"saveVisible" to true,"keyboardVisibleAtSave" to keyboardAtSave,"result" to "PASS")
        return e
    }
    private fun position(e:LedgerExecution,scene:String){page(1);ui.onNodeWithText("当前持仓").performClick();val tag="position-cycle_${e.id}";ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed();capture(scene,tag)}

    private val today=AutomaticEntry.today()
    private data class Expected(val day:Long,val market:String,val code:String,val net:String?)
    private val expected=mutableListOf<Expected>()
    private val checks=mutableListOf<Map<String,Any?>>()
    private val moneyLayouts=mutableListOf<Map<String,Any?>>()
    private val toolbarSafety=mutableListOf<Map<String,Any?>>()
    private fun safeToolbar(){
        ui.waitForIdle()
        var safeLeft=0;var safeRight=0;var safeTop=0
        instrumentation.runOnMainSync{
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val decor=activity.window.decorView
            val insets=requireNotNull(androidx.core.view.ViewCompat.getRootWindowInsets(decor)).getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            safeLeft=insets.left;safeRight=decor.width-insets.right;safeTop=insets.top
        }
        val buttons=listOf("open-risk-strategy","open-backup").map{tag->
            val node=ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().fetchSemanticsNode()
            val bounds=node.boundsInWindow
            assertTrue("$tag must stay outside system navigation/cutouts: $bounds within $safeLeft..$safeRight",bounds.left>=safeLeft-1 && bounds.right<=safeRight+1 && bounds.top>=safeTop-1)
            mapOf("tag" to tag,"left" to bounds.left,"right" to bounds.right,"top" to bounds.top)
        }
        toolbarSafety+=mapOf("orientation" to app.resources.configuration.orientation,"safeLeft" to safeLeft,"safeRight" to safeRight,"safeTop" to safeTop,"buttons" to buttons,"result" to "PASS")
    }
    private fun moneyLayout(tag:String,value:BigDecimal?){
        val node=ui.onNodeWithTag(tag,useUnmergedTree=true);val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){assertTrue(it(layouts))}
        val layout=layouts.single();val color=layout.layoutInput.style.color.toArgb()
        val night=app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val profit=(if(night)Color(0xFFFFB4B0)else Color(0xFFAF273C)).toArgb()
        val loss=(if(night)Color(0xFF80D5AF)else Color(0xFF087044)).toArgb()
        when(value?.signum()){1->assertEquals(profit,color);-1->assertEquals(loss,color);else->assertTrue(color!=profit&&color!=loss)}
        moneyLayouts+=mapOf("tag" to tag,"text" to shown(tag),"fontSp" to layout.layoutInput.style.fontSize.value,"lines" to layout.lineCount,"overflowWidth" to layout.didOverflowWidth,"overflowHeight" to layout.didOverflowHeight,"visible" to runCatching{node.assertIsDisplayed()}.isSuccess,"argb" to String.format("#%08X",color))
    }
    private fun orientation(value:Int,expected:Int){
        ui.runOnIdle{ui.activity.requestedOrientation=value}
        waitFor{var ready=false;instrumentation.runOnMainSync{ready=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().any{it.resources.configuration.orientation==expected}};ready}
    }
    private fun enter(day:LocalDate,market:String,code:String,action:String,price:String,quantity:String,fee:String?,remaining:String):LedgerExecution{
        ui.runOnIdle{model.entryClock=Clock.fixed(day.atTime(10,0).atZone(AutomaticEntry.beijing).toInstant(),ZoneOffset.UTC)}
        begin(market,code,action,price,quantity,fee?.let{"¥$it"}?:"待估算")
        ui.onNodeWithTag("execution-quantity").performImeAction()
        return save(code,fee,remaining)
    }
    private fun top(tag:String){ui.onNodeWithTag(tag).performScrollToIndex(0)}
    private fun statistic(type:String,market:String,offset:Int=0,captureScene:String?=null){
        page(3);ui.onNodeWithTag("statistics-period-$type").performClick();ui.onNodeWithTag("statistics-market-$market").performClick()
        if(offset!=0)ui.onNodeWithTag(if(offset<0)"statistics-previous"else"statistics-next").performClick()
        val anchor=if(type=="week")today.minusDays((today.dayOfWeek.value-1).toLong()).plusWeeks(offset.toLong())else today.withDayOfMonth(1).plusMonths(offset.toLong())
        val end=if(type=="week")anchor.plusDays(6)else anchor.withDayOfMonth(anchor.lengthOfMonth())
        val chosen=expected.filter{it.day in anchor.toEpochDay()..end.toEpochDay()&&(market=="all"||it.market==market)}
        waitFor{ui.onAllNodesWithTag("statistics-results-$type-$market").fetchSemanticsNodes().isNotEmpty()}
        val known=chosen.mapNotNull{it.net?.let(::BigDecimal)}
        if(chosen.isNotEmpty()){
            val net=if(known.isEmpty())null else known.fold(BigDecimal.ZERO,BigDecimal::add)
            ui.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("period-realized-net"))
            ui.onNodeWithTag("period-realized-net").assertTextEquals(moneyText(net))
            moneyLayout("period-realized-net",net)
            val pending=chosen.count{it.net==null}
            ui.onNodeWithTag("statistics-samples").assertTextEquals("${known.size} / ${chosen.size}")
            val expectedWinRate=if(known.isEmpty())"不可计算"else AutomaticEntry.cents(BigDecimal(known.count{it.signum()>0}).multiply(BigDecimal(100)).divide(BigDecimal(known.size),12,java.math.RoundingMode.HALF_UP))+"%"
            ui.onNodeWithTag("statistics-win-rate").assertTextEquals(expectedWinRate)
            if(pending>0)ui.onNodeWithTag("statistics-pending").assertTextEquals("$pending 笔费用待估算，未计入净盈亏、胜率和图表；有效样本 ${known.size}")
            checks+=mapOf("type" to type,"market" to market,"offset" to offset,"start" to anchor.toString(),"end" to end.toString(),"closed" to chosen.size,"valid" to known.size,"pending" to pending,"net" to net?.toPlainString(),"displayed" to shown("period-realized-net"),"sourceExecutionCount" to facts().executions.size,"result" to "PASS")
        }else ui.onNodeWithText("本周期暂无平仓成交").assertExists()
        top("v2-statistics");captureScene?.let{capture(it)}
        // Exact values remain available via the chart's real accessibility actions.
        ui.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("v2-daily-chart"))
        val target=chosen.firstOrNull()?.day?:anchor.toEpochDay()
        val actions=ui.onNodeWithTag("v2-daily-chart").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        ui.runOnIdle{assertTrue(actions.single{it.label=="查看${LocalDate.ofEpochDay(target)}准确数值"}.action())}
        val daily=chosen.filter{it.day==target}.mapNotNull{it.net?.let(::BigDecimal)}
        if(daily.isNotEmpty())ui.onNode(hasText(moneyText(daily.fold(BigDecimal.ZERO,BigDecimal::add))) and hasAnyAncestor(isDialog())).assertExists()
        else ui.onNodeWithText(if(chosen.any{it.day==target&&it.net==null})"费用待估算，净盈亏待计算"else"没有平仓成交").assertExists()
        ui.onNodeWithText("关闭").performClick()
        if(captureScene!=null){ui.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("v2-daily-chart"));capture("$captureScene-chart")}
        if(type=="month"&&chosen.any{it.net!=null}){
            val code=chosen.first{it.net!=null}.code
            ui.onNodeWithTag("v2-statistics").performScrollToNode(hasTestTag("distribution-$code"));captureScene?.let{capture("$it-distribution")}
        }
        top("v2-statistics")
        if(offset!=0)ui.onNodeWithTag(if(offset<0)"statistics-next"else"statistics-previous").performClick()
    }
    private fun home(scene:String){
        page(0);top("v2-home")
        val ranges=listOf("week" to today.minusDays((today.dayOfWeek.value-1).toLong()).toEpochDay(),"month" to today.withDayOfMonth(1).toEpochDay(),"all" to Long.MIN_VALUE)
        ranges.forEach{(key,start)->
            val items=expected.filter{it.day>=start&&(key=="all"||it.day<=if(key=="week")start+6 else today.withDayOfMonth(today.lengthOfMonth()).toEpochDay())}
            val net=if(items.any{it.net==null})null else items.fold(BigDecimal.ZERO){a,v->a+BigDecimal(requireNotNull(v.net))}
            ui.onNodeWithTag("home-realized-$key").assertTextEquals(moneyText(net))
            moneyLayout("home-realized-$key",net)
        }
        capture(scene)
        ui.onNodeWithTag("v2-home").performScrollToNode(hasTestTag("calendar-expand"))
        // The containing summary card is taller than the landscape viewport.
        // Finding its composed item is not enough: scroll the actual child into view.
        ui.onNodeWithTag("calendar-expand").performScrollTo().assertIsDisplayed().performClick()
        val monthTitle="${today.year}年${today.monthValue}月"
        waitFor{ui.onAllNodesWithText(monthTitle).fetchSemanticsNodes().size==1}
        val title=ui.onNodeWithText(monthTitle)
        title.performScrollTo().assertIsDisplayed()
        val dy=(title.fetchSemanticsNode().boundsInRoot.top-ui.onNodeWithTag("v2-home").fetchSemanticsNode().boundsInRoot.top).coerceAtLeast(0f)
        ui.onNodeWithTag("v2-home").performSemanticsAction(SemanticsActions.ScrollBy){it(0f,dy)}
        capture("$scene-calendar")
        ui.onNodeWithTag("calendar-expand").performScrollTo().assertIsDisplayed().performClick();top("v2-home")
    }
    private fun homeRoutes(){
        val before=facts()
        page(0);ui.onNodeWithTag("v2-home").performScrollToNode(hasText("查看交易"));ui.onNodeWithText("查看交易").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("v2-main-page-1").assertIsSelected()
        page(0);ui.onNodeWithTag("v2-home").performScrollToNode(hasText("去复盘"));ui.onNodeWithText("去复盘").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("v2-main-page-2").assertIsSelected()
        page(0);val last=before.executions.single{it.code=="i2701"&&it.effect!="open"}
        val tag="home-recent-${last.id}"
        waitFor{ui.onAllNodesWithTag("v2-home").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("v2-home").performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("成交详情").assertIsDisplayed();ui.onNodeWithTag("edit-execution").assertExists()
        ui.onNodeWithContentDescription("返回主页面").performClick()
        assertEquals(before,facts());ui.onNodeWithTag("v2-main-page-0").assertIsSelected()
    }
    private fun review(){
        val before=facts();val cycle=before.cycles.single{it.code=="rb2701"}
        page(2);ui.onNodeWithTag("review-section-post").performClick()
        ui.onNodeWithTag("v2-reviews").performScrollToNode(hasTestTag("review-cycle-${cycle.id}"));ui.onNodeWithTag("review-cycle-${cycle.id}").performClick()
        ui.onNodeWithTag("position-review-note").performScrollTo().performTextReplacement("合成复盘：分批开仓后部分平仓，核对费用与剩余一手。".repeat(3))
        ui.onNodeWithTag("save-position-review").assertIsDisplayed().performClick()
        waitFor{!model.busy.value&&model.state.value.ledger?.reviews?.size==1}
        val after=facts();assertEquals(before.executions,after.executions);assertEquals(before.matches,after.matches)
        assertEquals(cycle.id,after.reviews.single().cycleId)
    }
    @Test fun realHomeStatisticsDataNavigationAndProcessReopen(){
        assertEquals("Disposable emulator only","1",shell("getprop ro.kernel.qemu").trim())
        app=ui.activity.application as TradeReviewApplication
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        val phase=InstrumentationRegistry.getArguments().getString("fullUiPhase")?:"journey"
        val saved=File(output(),"v210-full-$profile-api${Build.VERSION.SDK_INT}.json")
        if(phase=="reopen"){
            val previous=com.google.gson.JsonParser.parseString(saved.readText()).asJsonObject
            assertNotEquals(previous["pid"].asInt,android.os.Process.myPid())
            waitFor{model.state.value.ledger==facts()}
            assertEquals(previous["ledger"],LedgerJson.element(facts()))
            expected+=previous["expectedClosed"].asJsonArray.map{v->v.asJsonObject.let{Expected(it["day"].asLong,it["market"].asString,it["code"].asString,it["net"].takeUnless{n->n.isJsonNull}?.asString)}}
            home("reopened-home");statistic("week","all");statistic("month","all")
            assertEquals(1,facts().reviews.size)
            File(output(),"v210-full-reopen-$profile-api${Build.VERSION.SDK_INT}.json").writeText(GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(mapOf("api" to Build.VERSION.SDK_INT,"profile" to profile,"pid" to android.os.Process.myPid(),"previousPid" to previous["pid"].asInt,"allFactsIdentical" to true,"reviewPreserved" to true,"screenshots" to screens,"result" to "PASS")))
            return
        }
        // The production catalog intentionally refuses dates before 2026-10-14.
        // Explicit test configuration supplies a synthetic past rule for the previous-week
        // sample. Do not backdate the production catalog or invent historical exchange fees.
        val historical=LedgerContractRule("synthetic_al_history","al2701","合成历史参数（非交易所资料）","5","0.11","per_lot","3","3","3","per_execution","0.01",today.minusMonths(3).toEpochDay(),true)
        runBlocking{app.database.withTransaction{model.repository.write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),rules=listOf(historical)),true)}}
        ui.runOnIdle{model.closeEditor();model.handle["v2-entry-fields"]=null}
        waitFor{model.state.value.ledger==facts()};home("empty-home");statistic("week","all",captureScene="empty-statistics")
        val pm=today.withDayOfMonth(1).minusDays(1);val pw=today.minusDays((today.dayOfWeek.value-1).toLong()).minusDays(1)
        enter(pm.minusDays(1),"stock","600001","buy","10","1000","1.00","1000")
        enter(pm,"stock","600001","sell","11","1000","6.60","0");expected+=Expected(pm.toEpochDay(),"stock","600001","992.4")
        enter(pw,"futures","al2701","buy-open","20000","1","3.01","1")
        enter(pw,"futures","al2701","sell-close","20010","1","3.01","0");expected+=Expected(pw.toEpochDay(),"futures","al2701","43.98")
        enter(today.minusDays(1),"stock","600000","buy","10","1000","1.00","1000")
        // Reproduce the existing main journey's cancel/date-boundary case, not a
        // fresh form that would miss a stale asynchronous quote or reused draft.
        val beforeRejected=facts()
        begin("stock","600000","sell","10","1000","¥6.00");tap("save-execution")
        waitFor{!model.busy.value&&model.problem.value?.contains("T+1")==true}
        assertEquals(beforeRejected,facts());tap("entry-cancel")
        waitFor{!model.editor.value&&ui.onAllNodesWithTag("save-execution").fetchSemanticsNodes().isEmpty()}
        enter(today,"stock","600000","sell","10","1000","6.00","0");expected+=Expected(today.toEpochDay(),"stock","600000","-7")
        enter(today,"futures","rb2701","buy-open","3500","2","7.01","2")
        enter(today,"futures","rb2701","buy-open","3510","3","10.54","5")
        enter(today,"futures","rb2701","sell-close","3530","4","14.13","1");expected+=Expected(today.toEpochDay(),"futures","rb2701","971.833333333333")
        enter(today,"futures","rb2705","sell-open","4000","1","4.01","1")
        // Synthetic flat-result fixture: gross 8.02; opening/closing fees 4.01 each.
        enter(today,"futures","rb2705","buy-close","3999.198","1","4.01","0");expected+=Expected(today.toEpochDay(),"futures","rb2705","0")
        home("known-home");statistic("week","all",captureScene="known-statistics")
        enter(today,"futures","i2701","buy-open","800","2",null,"2")
        enter(today,"futures","i2701","sell-close","810","1",null,"1");expected+=Expected(today.toEpochDay(),"futures","i2701",null)
        assertEquals(13,facts().executions.size)
        expected.forEach{v->val e=facts().executions.single{it.code==v.code&&it.effect!="open"};val matches=facts().matches.filter{it.closeExecutionId==e.id};val net=if(matches.any{it.net==null})null else matches.fold(BigDecimal.ZERO){a,m->a+BigDecimal(requireNotNull(m.net))}.stripTrailingZeros().toPlainString();assertEquals(v.net?.let{BigDecimal(it).stripTrailingZeros().toPlainString()},net)}
        review();home("pending-home");homeRoutes()
        for(type in listOf("week","month"))for(market in listOf("all","stock","futures"))statistic(type,market,captureScene=if(market=="all")"$type-statistics"else null)
        statistic("week","all",-1);statistic("month","all",-1)
        page(3);ui.onNodeWithTag("statistics-period-month").performClick();top("v2-statistics")
        ui.activityRule.scenario.recreate();waitFor{model.state.value.ledger==facts()}
        ui.onNodeWithTag("statistics-period-month").assertIsSelected();capture("recreated-statistics")
        orientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE)
        safeToolbar();top("v2-statistics");capture("landscape-statistics");home("landscape-home")
        orientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        safeToolbar()
        page(3);ui.onNodeWithTag("statistics-period-month").assertIsSelected()
        val conf=app.resources.configuration
        val proof=mapOf("api" to Build.VERSION.SDK_INT,"profile" to profile,"pid" to android.os.Process.myPid(),"dateAnchor" to today.toString(),"screenWidthDp" to conf.screenWidthDp,"fontScale" to conf.fontScale,"night" to (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES),"syntheticOnly" to true,"allExecutionsCreatedViaRealForm" to true,"expectedClosed" to expected,"observations" to results,"checks" to checks,"moneyLayouts" to moneyLayouts,"toolbarSafety" to toolbarSafety,"orientationRestored" to true,"ledger" to LedgerJson.element(facts()),"screenshots" to screens,"reviewSavedViaUi" to true,"result" to "PASS")
        saved.writeText(GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(proof))
    }
}

