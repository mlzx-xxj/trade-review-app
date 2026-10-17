package com.tradereview.app.v210

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.DatePicker
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.v200.*
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** The same user operations run against the untouched baseline and the redesigned UI.
 * Only an empty ledger and one named synthetic strategy are setup configuration.
 * Every execution, plan, review, account setting and cash flow is saved through MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class V210PlanReviewJourneyTest {
    @get:Rule(order=0) val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val args=InstrumentationRegistry.getArguments()
    private val profile=args.getString("visualProfile")?:"suite"
    private val anchor=LocalDate.of(2026,10,17)
    private lateinit var app:TradeReviewApplication
    private lateinit var model:V200ViewModel
    private val operations=mutableListOf<Map<String,Any?>>()
    private val screens=mutableListOf<Map<String,Any?>>()
    private val layouts=mutableListOf<Map<String,Any?>>()
    private val displayedMoney=mutableListOf<Map<String,Any?>>()
    private var cashEditTargetRetained=true
    private var lastStep="initial"
    private var taps=0;private var scrolls=0;private var dateTaps=0;private var backKeys=0
    private val typed=linkedSetOf<String>()
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
            runCatching{val tree=runCatching{ui.onAllNodes(isRoot(),useUnmergedTree=true).printToString()}.getOrElse{"Semantics unavailable: ${it.message}"};File(output(),"v210-review-$profile-failure.txt").writeText("$lastStep\n"+error.stackTraceToString()+"\n"+tree)}
            runCatching{capture("failure")}
        }
        override fun finished(description:org.junit.runner.Description){
            if(keyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard")else shell("settings put secure show_ime_with_hard_keyboard $keyboard")
            service?.let{instrumentation.uiAutomation.serviceInfo=it}
        }
    }
    private fun output()=File(requireNotNull(args.getString("additionalTestOutputDir")),"v210-review").apply{check(isDirectory||mkdirs())}
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun facts()=runBlocking{model.repository.read()}
    private fun waitFor(p:()->Boolean)=ui.waitUntil(45000){p()}
    private fun page(index:Int){ui.onNodeWithTag("v2-main-page-$index").performClick();ui.waitForIdle()}
    private fun reset(){taps=0;scrolls=0;dateTaps=0;backKeys=0;typed.clear()}
    private fun visible(n:SemanticsNodeInteraction):SemanticsNodeInteraction{
        if(runCatching{n.assertIsDisplayed()}.isFailure){n.performScrollTo();scrolls++}
        return n.assertIsDisplayed()
    }
    private fun tap(tag:String){
        if(tag.startsWith("review-section-")&&ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()){top("v2-reviews");scrolls++}
        if(ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() && tag in listOf("new-cashflow","configure-account","new-plan")){
            ui.onNodeWithTag(if(tag=="new-plan")"v2-reviews"else"v2-accounts").performScrollToNode(hasTestTag(tag));scrolls++
        }
        visible(ui.onNodeWithTag(tag)).assertIsEnabled().performClick();taps++
    }
    private fun tapText(text:String,modal:Boolean=false){
        val matcher=hasText(text) and hasClickAction()
        visible(ui.onNode(if(modal)matcher and hasAnyAncestor(isDialog())else matcher)).performClick();taps++
    }
    private fun field(tag:String,value:String){tap(tag);ui.onNodeWithTag(tag).performTextReplacement(value);typed+=tag;ui.waitForIdle()}
    private fun labeled(label:String,value:String,index:Int=0){
        val nodes=ui.onAllNodes(hasSetTextAction() and hasText(label) and hasAnyAncestor(isDialog()))
        visible(nodes[index]).performClick();taps++;nodes[index].performTextReplacement(value);typed+=label;ui.waitForIdle()
    }
    private fun select(tag:String){if(tag.startsWith("account-")&&ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()){top("v2-accounts");scrolls++};if(!ui.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected){false})tap(tag)}
    private fun date(label:String,day:LocalDate){
        val n=ui.onNode(hasText(label,substring=true) and hasClickAction() and hasAnyAncestor(isDialog()))
        visible(n).performClick();taps++;dateTaps++
        onView(isAssignableFrom(DatePicker::class.java)).perform(object:ViewAction{
            override fun getConstraints():Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription()="Select the pinned synthetic fixture date in the real system DatePicker"
            override fun perform(controller:UiController,view:View){(view as DatePicker).updateDate(day.year,day.monthValue-1,day.dayOfMonth);controller.loopMainThreadUntilIdle()}
        })
        onView(withId(android.R.id.button1)).perform(click());taps++;dateTaps++
        ui.onNode(hasText("$label $day") and hasAnyAncestor(isDialog())).assertExists()
    }
    private fun hideKeyboard(){if(instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}){shell("input keyevent KEYCODE_BACK");backKeys++;ui.waitForIdle()}}
    private fun capture(scene:String){
        ui.waitForIdle();android.os.SystemClock.sleep(150)
        val b=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val name="v210-review-$profile-$scene.png"
        File(output(),name).outputStream().use{assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        screens+=mapOf("scene" to scene,"file" to name,"pixels" to listOf(b.width,b.height));b.recycle()
    }
    private fun controls(saveTag:String,label:String):Map<String,Any?>{
        ui.waitForIdle()
        val ime=instrumentation.uiAutomation.windows.filter{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}.map{w->android.graphics.Rect().also{w.getBoundsInScreen(it)}}.firstOrNull()
        val found=instrumentation.uiAutomation.windows.filter{it.type==AccessibilityWindowInfo.TYPE_APPLICATION}.flatMap{w->w.root?.findAccessibilityNodeInfosByText(label).orEmpty()}.firstOrNull{it.isClickable&&it.isVisibleToUser}
        val bounds=found?.let{n->android.graphics.Rect().also{n.getBoundsInScreen(it)}}
        return mapOf("tag" to saveTag,"composeVisible" to runCatching{ui.onNodeWithTag(saveTag).assertIsDisplayed()}.isSuccess,"keyboardVisible" to (ime!=null),"imeTopPx" to ime?.top,"buttonBoundsPx" to bounds?.let{listOf(it.left,it.top,it.right,it.bottom)},"fullyAboveKeyboard" to (bounds!=null&&(ime==null||bounds.bottom<=ime.top)),"touchHeightDp" to bounds?.let{it.height()/app.resources.displayMetrics.density})
    }
    private fun save(tag:String,label:String,kind:String){
        val measured=controls(tag,label)
        // A real baseline user can hide the keyboard when necessary. Record that
        // operation, never pretend an obstructed baseline button was usable.
        if(measured["composeVisible"]!=true||measured["fullyAboveKeyboard"]!=true)hideKeyboard()
        tap(tag);waitFor{!model.busy.value&&ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()}
        assertNull(model.problem.value);waitFor{model.state.value.ledger==facts()}
        operations+=mapOf("operation" to kind,"taps" to taps,"fixtureDateTaps" to dateTaps,"normalTaps" to taps-dateTaps,"typedFields" to typed.toList(),"scrollCommands" to scrolls,"keyboardBackKeys" to backKeys,"saveControl" to measured,"result" to "PASS")
    }
    private fun layout(scene:String){
        val matcher=SemanticsMatcher("Currency or unknown text with layout"){n->n.config.contains(SemanticsActions.GetTextLayoutResult)&&n.config.getOrElse(SemanticsProperties.Text){emptyList()}.any{it.text.contains("￥")||it.text=="待估算"}}
        val nodes=ui.onAllNodes(matcher,useUnmergedTree=true)
        nodes.fetchSemanticsNodes().indices.forEach{i->
            val values=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(values)}
            values.forEach{r->layouts+=mapOf("scene" to scene,"text" to r.layoutInput.text.text,"fontSp" to r.layoutInput.style.fontSize.value,"overflowWidth" to r.didOverflowWidth,"overflowHeight" to r.didOverflowHeight,"visible" to runCatching{nodes[i].assertIsDisplayed()}.isSuccess,"argb" to String.format("#%08X",r.layoutInput.style.color.toArgb()))}
        }
    }
    private fun unchanged(before:LedgerSnapshot){val after=facts();assertEquals(before.executions,after.executions);assertEquals(before.lots,after.lots);assertEquals(before.matches,after.matches);assertEquals(before.cycles,after.cycles);assertEquals(before.audits,after.audits);assertEquals(before.risks,after.risks);assertEquals(before.rules,after.rules)}
    private fun top(tag:String){ui.onNodeWithTag(tag).performScrollToIndex(0)}
    private fun openPlan(id:String){
        val p=facts().plans.single{it.id==id};top("v2-reviews")
        ui.onNodeWithTag("v2-reviews").performScrollToNode(hasText(p.code,substring=true))
        val title=ui.onAllNodes(hasText(p.code,substring=true)).fetchSemanticsNodes().filter{it.boundsInRoot.height>0}.minBy{it.boundsInRoot.top}
        val buttons=ui.onAllNodesWithText("查看与编辑计划").fetchSemanticsNodes()
        val index=buttons.indices.filter{buttons[it].boundsInRoot.top>=title.boundsInRoot.top}.minBy{buttons[it].boundsInRoot.top}
        visible(ui.onAllNodesWithText("查看与编辑计划")[index]).performClick();taps++
        ui.onNodeWithTag("plan-code").assertTextContains(p.code)
    }
    private fun observeMoney(label:String,value:String,tag:String,legacyCombined:Boolean=true){
        val tagged=ui.onAllNodesWithTag(tag).fetchSemanticsNodes()
        val n=if(tagged.isNotEmpty())ui.onNodeWithTag(tag)else if(legacyCombined)ui.onNode(hasText("$label $value",substring=true))else ui.onNodeWithText(value)
        visible(n)
        if(tagged.isNotEmpty())n.assertTextEquals(value)else n.assertTextContains(value,substring=true)
        displayedMoney+=mapOf("label" to label,"displayed" to value,"tag" to tag,"result" to "PASS")
    }
    private fun quickPlan(account:String,code:String,name:String,note:String):LedgerPlan{
        page(2);tap("review-section-plan");top("v2-reviews");reset();tap("new-plan")
        if(account=="futures")tapText("期货账户",true)
        date("计划日期",anchor.minusDays(1));field("plan-code",code);field("plan-name",name);field("plan-note",note)
        waitFor{instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}}
        capture("$account-plan-keyboard")
        save("save-plan","保存计划","new-$account-plan")
        return facts().plans.single{it.code==code}
    }
    private fun execution(day:LocalDate,market:String,code:String,action:String,price:String,qty:String,fee:String?):LedgerExecution{
        lastStep="execution $code $action";page(1)
        ui.runOnIdle{model.entryClock=Clock.fixed(day.atTime(10,0).atZone(AutomaticEntry.beijing).toInstant(),ZoneOffset.UTC)}
        tap("new-execution");select("entry-market-$market");field("execution-code",code);select(if(market=="stock")"execution-side-$action"else"execution-action-$action");field("execution-price",price);field("execution-quantity",qty)
        waitFor{ui.onNodeWithTag("entry-estimated-fee").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}=="预计手续费：${fee?.let{"¥$it"}?:"待估算"}"}
        val previous=facts();tap("save-execution");waitFor{!model.busy.value&&!model.editor.value&&model.state.value.ledger?.executions?.size==previous.executions.size+1}
        assertNull(model.pending.value);assertNull(model.problem.value)
        return facts().executions.single{e->previous.executions.none{it.id==e.id}}.also{assertEquals(fee,it.fees)}
    }
    private fun openReview(cycle:LedgerCycle){page(2);tap("review-section-post");ui.onNodeWithTag("v2-reviews").performScrollToNode(hasTestTag("review-cycle-${cycle.id}"));tap("review-cycle-${cycle.id}")}
    private fun orientation(requested:Int,expected:Int){
        instrumentation.runOnMainSync{androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single().requestedOrientation=requested}
        waitFor{app.resources.configuration.orientation==expected};ui.waitForIdle()
    }
    private val longPlan="合成盘前计划：先核对标的与计划价位，再记录实际成交。计划价格不代表真实成交。\n".repeat(10)
    private val longReview="合成复盘：核对分批成交与费用归属，不修改历史价格、数量或净盈亏。\n保留下一次观察的关键价位。\n".repeat(10)
    @Test fun realPlansReviewsCashAndProcessReopen(){
        assertEquals("Disposable emulator only","1",shell("getprop ro.kernel.qemu").trim())
        app=ui.activity.application as TradeReviewApplication;ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        val saved=File(output(),"v210-review-$profile-api${Build.VERSION.SDK_INT}.json")
        if(args.getString("reviewUiPhase")=="reopen"){
            val before=JsonParser.parseString(saved.readText()).asJsonObject
            assertNotEquals(before["pid"].asInt,android.os.Process.myPid());waitFor{model.state.value.ledger==facts()};assertEquals(before["ledger"],LedgerJson.element(facts()))
            page(2);tap("review-section-plan");openPlan(facts().plans.single{it.code=="603999"}.id);ui.onNodeWithTag("plan-note").assertTextContains(longPlan+"已修改计划");tapText("取消",true)
            page(4);capture("reopened-accounts")
            File(output(),"v210-review-reopen-$profile-api${Build.VERSION.SDK_INT}.json").writeText(GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(mapOf("api" to Build.VERSION.SDK_INT,"profile" to profile,"pid" to android.os.Process.myPid(),"previousPid" to before["pid"].asInt,"allFactsIdentical" to true,"planTextPreserved" to true,"ledger" to LedgerJson.element(facts()),"result" to "PASS","screenshots" to screens)))
            return
        }
        runBlocking{app.database.withTransaction{model.repository.write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),strategies=listOf(LedgerStrategy("review_fixture_strategy","合成趋势策略","仅用于离线UI验证"))),true)}}
        ui.runOnIdle{model.closeEditor();model.handle["v2-entry-fields"]=null};waitFor{model.state.value.ledger==facts()}
        page(2);capture("empty-plans");tap("review-section-post");capture("empty-reviews");tap("review-section-plan")
        val stock=quickPlan("stock","603999","合成股票计划",longPlan)
        assertTrue(facts().executions.isEmpty());openPlan(stock.id);reset()
        field("plan-entry","9.5");labeled("计划止盈价（可选）","12");labeled("计划止损价（可选）","9")
        tap("execution-strategy");ui.onNode(hasText("合成趋势策略") and hasAnyAncestor(isPopup())).performClick();taps++
        // The first save deliberately had blank optional prices: those rows
        // are omitted by the existing protocol. Reopening is therefore empty.
        // Add each row through the actual UI, rather than assuming defaults.
        listOf("支撑" to "9","压力" to "11","观察" to "10.5").forEach{(label,price)->
            tapText("增加关键价位",true)
            val names=ui.onAllNodes(hasSetTextAction() and hasText("名称") and hasAnyAncestor(isDialog()))
            val lastName=names.fetchSemanticsNodes().lastIndex
            visible(names[lastName]).performClick();taps++;names[lastName].performTextReplacement(label);typed+="关键价位名称"
            val prices=ui.onAllNodes(hasSetTextAction() and hasText("价位") and hasAnyAncestor(isDialog()))
            val lastPrice=prices.fetchSemanticsNodes().lastIndex
            labeled("价位",price,lastPrice)
        }
        val deletes=ui.onAllNodes(hasText("删") and hasClickAction() and hasAnyAncestor(isDialog()))
        if(deletes.fetchSemanticsNodes().isNotEmpty())visible(deletes[1]).performClick()else visible(ui.onNodeWithContentDescription("删除关键价位2")).performClick()
        taps++;field("plan-note",longPlan+"已修改计划");hideKeyboard();capture("plan-editor-long");orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);capture("plan-landscape");orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT);save("save-plan","保存计划","edit-stock-plan")
        val updated=facts().plans.single();assertEquals(stock.id,updated.id);assertEquals("review_fixture_strategy",updated.strategyId);assertEquals(listOf(KeyPrice("支撑","9"),KeyPrice("观察","10.5")),LedgerJson.levels(updated.levels))
        quickPlan("futures","rb2701","合成螺纹钢计划","合成期货计划，不生成持仓。")
        assertEquals(2,facts().plans.size);assertEquals(1,facts().plans.map{it.day}.toSet().size);assertTrue(facts().executions.isEmpty());top("v2-reviews");capture("plans-list")
        tap("new-plan");val beforeInvalid=facts();tap("save-plan");ui.onNodeWithTag("save-plan").assertExists();assertEquals(beforeInvalid,facts());capture("plan-error");tapText("取消",true)
        execution(anchor.minusDays(1),"stock","603999","buy","10","1000","1.00")
        execution(anchor,"stock","603999","sell","11","1000","6.60")
        execution(anchor,"futures","rb2701","buy-open","3500","2","7.01")
        execution(anchor,"futures","rb2701","buy-open","3510","3","10.54")
        execution(anchor,"futures","rb2701","sell-close","3490","4","13.97")
        execution(anchor,"futures","i2701","buy-open","800","2",null)
        execution(anchor,"futures","i2701","sell-close","810","1",null)
        val immutable=facts();assertEquals(7,immutable.executions.size)
        val rb=immutable.cycles.single{it.code=="rb2701"};assertEquals("1",rb.remaining);assertEquals("-628.006666666667",rb.realized)
        val closed=immutable.cycles.single{it.code=="603999"};assertNotNull(closed.closedDay);assertEquals("992.4",closed.realized)
        val pending=immutable.cycles.single{it.code=="i2701"};assertNull(pending.realized);assertNull(pending.fees)
        openReview(rb);reset();date("复盘日期",anchor);field("position-review-note",longReview);waitFor{instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}};capture("review-keyboard")
        save("save-position-review","保存复盘","new-open-review")
        val first=facts().reviews.single();unchanged(immutable)
        page(2);top("v2-reviews");capture("review-cycles");ui.onNodeWithTag("v2-reviews").performScrollToNode(hasTestTag("review-cycle-${rb.id}"))
        val history=if(ui.onAllNodesWithTag("edit-review-${first.id}").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("edit-review-${first.id}")else ui.onNodeWithText("$anchor ${longReview.take(24)}")
        visible(history).performClick();visible(ui.onNodeWithTag("position-review-note"));capture("review-editor-long");layout("review-editor");reset();field("position-review-note",longReview+"已修改复盘")
        orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);capture("review-landscape");orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        ui.onNodeWithTag("position-review-note").assertTextContains(longReview+"已修改复盘");save("save-position-review","保存复盘","edit-open-review");assertEquals(first.id,facts().reviews.single().id)
        openReview(closed);reset();date("复盘日期",anchor);field("position-review-note","合成已平仓复盘，成交事实只读。");save("save-position-review","保存复盘","closed-review")
        openReview(pending);reset();date("复盘日期",anchor);field("position-review-note","费用尚未提供，净盈亏保持待估算。");capture("pending-review");save("save-position-review","保存复盘","pending-review")
        unchanged(immutable);assertEquals(3,facts().reviews.size)
        page(4);top("v2-accounts");capture("stock-account-unknown");layout("stock-account-unknown");observeMoney("参考可用现金","待估算","account-cash-stock")
        configure("stock","10000",true);flow("stock","in","1000","合成入金");flow("stock","out","200","合成出金")
        page(4);top("v2-accounts");reset()
        val incoming=facts().cashFlows.single{it.accountId=="stock"&&it.kind=="in"}
        ui.onNodeWithTag("v2-accounts").performScrollToNode(hasText("合成入金"));val edits=ui.onAllNodesWithText("编辑流水");val title=ui.onNodeWithText("合成入金").fetchSemanticsNode().boundsInRoot
        val index=edits.fetchSemanticsNodes().indices.filter{edits.fetchSemanticsNodes()[it].boundsInRoot.top>=title.top}.minBy{edits.fetchSemanticsNodes()[it].boundsInRoot.top};visible(edits[index]).performClick()
        field("cashflow-amount","1200");field("cashflow-note","合成入金已更正")
        orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);capture("cash-edit-landscape")
        cashEditTargetRetained=ui.onAllNodesWithText("原流水：",substring=true).fetchSemanticsNodes().isNotEmpty()
        orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        if(!cashEditTargetRetained){
            // Observed legacy UI loses its edit target after recreation. Cancel
            // that unsaved form and reopen the real existing flow, never save a
            // duplicate or silently repair the baseline database.
            tapText("取消",true);ui.onNodeWithTag("v2-accounts").performScrollToNode(hasText("合成入金"))
            val old=ui.onNodeWithText("合成入金").fetchSemanticsNode().boundsInRoot
            val buttons=ui.onAllNodesWithText("编辑流水");val found=buttons.fetchSemanticsNodes()
            visible(buttons[found.indices.filter{found[it].boundsInRoot.top>=old.top}.minBy{found[it].boundsInRoot.top}]).performClick();taps++
            field("cashflow-amount","1200");field("cashflow-note","合成入金已更正")
        }
        save("save-cashflow","保存流水","edit-cashflow");assertEquals(incoming.id,facts().cashFlows.single{it.accountId=="stock"&&it.kind=="in"}.id)
        top("v2-accounts");capture("stock-account-known");layout("stock-account-known");assertEquals("11992.4",LedgerStatistics.cash(facts(),facts().accounts.single{it.id=="stock"}))
        observeMoney("累计已实现净盈亏","￥992.40","account-net-stock",false);observeMoney("累计已记录手续费","￥7.60","account-fees-stock");observeMoney("参考可用现金","￥11992.40","account-cash-stock")
        orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);top("v2-accounts");capture("account-landscape");orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        tap("account-futures");top("v2-accounts");capture("futures-account-pending");layout("futures-account-pending")
        observeMoney("累计已记录手续费","待估算","account-fees-futures");observeMoney("累计已实现净盈亏","待估算","account-net-futures",false)
        configure("futures","20000",false);flow("futures","in","3000","合成期货入金");flow("futures","out","500","合成期货出金")
        page(4);top("v2-accounts");tap("new-cashflow");field("cashflow-amount","0");tap("save-cashflow");ui.onNodeWithTag("save-cashflow").assertExists();assertEquals(4,facts().cashFlows.size);capture("cash-error");tapText("取消",true)
        unchanged(immutable);assertEquals(4,facts().cashFlows.size);ui.onNodeWithTag("v2-accounts").performScrollToNode(hasText("合成期货入金"));capture("cashflow-list")
        val config=app.resources.configuration
        val result=mapOf("api" to Build.VERSION.SDK_INT,"profile" to profile,"pid" to android.os.Process.myPid(),"dateAnchor" to anchor.toString(),"screenWidthDp" to config.screenWidthDp,"fontScale" to config.fontScale,"night" to (config.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES),"syntheticOnly" to true,"allExecutionsCreatedViaRealForm" to true,"allPlansReviewsCashSavedViaUi" to true,"immutableTradingFactsPreserved" to true,"immutableBeforeReview" to LedgerJson.element(immutable),"operations" to operations,"layouts" to layouts,"displayedMoney" to displayedMoney,"cashEditTargetRetained" to cashEditTargetRetained,"screenshots" to screens,"ledger" to LedgerJson.element(facts()),"result" to "PASS")
        saved.writeText(GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(result))
    }
    private fun configure(account:String,capital:String,complete:Boolean){
        page(4);top("v2-accounts");select("account-$account");top("v2-accounts");reset();tap("configure-account");field("account-capital",capital)
        if(account=="stock"&&complete)tap("account-complete")
        capture("$account-config-keyboard");save("save-account","保存账户配置","configure-$account")
        assertEquals(capital,facts().accounts.single{it.id==account}.initialCapital)
    }
    private fun flow(account:String,kind:String,amount:String,note:String){
        page(4);top("v2-accounts");select("account-$account");top("v2-accounts");reset();tap("new-cashflow")
        if(kind=="out")tapText("出金",true)
        date("日期",anchor);field("cashflow-amount",amount);field("cashflow-note",note);capture("$account-$kind-cash-keyboard");save("save-cashflow","保存流水","$account-$kind-cash")
        assertTrue(facts().cashFlows.any{it.accountId==account&&it.kind==kind&&it.amount==amount&&it.note==note})
    }
}
