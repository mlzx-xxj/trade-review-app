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

/** Same real MainActivity, inputs and screenshots on the V2.0 tag and V2.1.
 * Only the empty synthetic ledger and device clock are injected. No execution inserts.
 * Profile changes happen in the external runner BEFORE launching this process.
 */
@RunWith(AndroidJUnit4::class)
class V210VisualJourneyTest {
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
        override fun failed(error:Throwable,description:org.junit.runner.Description){runCatching{File(output(),"v210-$profile-failure.txt").writeText(error.stackTraceToString())};runCatching{capture("failure")}}
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
    private fun output()=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v210-visual").apply{check(isDirectory||mkdirs())}
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
        val name="v210-$profile-$scene.png"
        File(output(),name).outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        val bounds=tag?.let{ui.onNodeWithTag(it).getUnclippedBoundsInRoot()}
        collection+=mapOf("scene" to scene,"file" to name,"pixels" to listOf(bitmap.width,bitmap.height),"componentDp" to bounds?.let{listOf(it.left.value,it.top.value,it.right.value,it.bottom.value)})
        bitmap.recycle()
    }
    private fun begin(market:String,code:String,action:String,price:String,quantity:String,fee:String,margin:String?=null){
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

    /** Two additional real-form trades; never mixed into the five baseline operation counts. */
    private fun saveDirectionFixture(fee:String):LedgerExecution{
        val before=facts();tap("save-execution")
        waitFor{!model.busy.value&&!model.editor.value&&model.state.value.ledger?.executions?.size==before.executions.size+1}
        assertNull(model.pending.value)
        val e=facts().executions.single{v->before.executions.none{it.id==v.id}}
        assertEquals(fee,e.fees)
        ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("execution-${e.id}"));ui.onNodeWithTag("execution-${e.id}").assertIsDisplayed()
        return e
    }
    private fun renderedColor(tag:String,expected:Color):String{
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag(tag,useUnmergedTree=true).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult){assertTrue(it(layouts))}
        assertEquals(1,layouts.size)
        val color=layouts.single().layoutInput.style.color
        assertEquals("Actual rendered color: $tag",expected.toArgb(),color.toArgb())
        return String.format("#%08X",color.toArgb())
    }
    private fun directionDensity():Map<String,Any?>{
        begin("futures","rb2701","sell-open","4000","2","¥8.01","预计新增保证金占用：¥5,600.00")
        saveDirectionFixture("8.01")
        begin("futures","rb2701","buy-close","4010","1","¥4.02","平仓后预计剩余占用：¥5,257.00")
        saveDirectionFixture("4.02")
        val ledger=facts();assertEquals(7,ledger.executions.size)
        val current=ledger.cycles.filter{decimal(it.remaining)>BigDecimal.ZERO};assertEquals(4,current.size)
        val long=current.single{it.code=="rb2701"&&it.direction=="long"};val short=current.single{it.code=="rb2701"&&it.direction=="short"}
        assertEquals("1",long.remaining);assertEquals("1",short.remaining)
        assertEquals(0,BigDecimal("971.833333333333").compareTo(decimal(requireNotNull(long.realized))))
        assertEquals(0,BigDecimal("-108.025").compareTo(decimal(requireNotNull(short.realized))))
        val night=app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val direction=if(night)Color(0xFFB4CBF7)else Color(0xFF304C75)
        val profit=if(night)Color(0xFFFFB4B0)else Color(0xFFAF273C)
        val loss=if(night)Color(0xFF80D5AF)else Color(0xFF087044)
        page(1);ui.onNodeWithText("当前持仓").performClick();waitFor{model.message.value==null}
        ui.onNodeWithTag("v2-trades").performScrollToIndex(0);capture("density-top",collection=densityScreens)
        val rows=current.map{c->
            val tag="position-${c.id}"
            ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag(tag))
            val bounds=ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            if(profile!="suite"){
                val expected=if(c.accountId=="stock")if(profile.startsWith("narrow"))293.5 else 236.5 else if(profile.startsWith("narrow"))258.5 else 208.5
                assertEquals("No density regression: ${c.code} ${c.direction}",expected,(bounds.bottom.value-bounds.top.value).toDouble(),.6)
            }
            val dirTag="position-direction-${c.id}"
            ui.onNodeWithTag(dirTag,useUnmergedTree=true).assertTextEquals(directionLabel(c))
            val actual=renderedColor(dirTag,direction)
            listOf("quantity","price","net","fees").forEach{field->ui.onNodeWithTag("position-$field-${c.id}",useUnmergedTree=true).performScrollTo().assertIsDisplayed()}
            if(c.id==long.id){ui.onNodeWithTag("position-net-${c.id}").assertTextEquals("￥971.83");renderedColor("position-net-${c.id}",profit);capture("density-long",tag,densityScreens)}
            if(c.id==short.id){ui.onNodeWithTag("position-net-${c.id}").assertTextEquals("￥-108.03");renderedColor("position-net-${c.id}",loss);capture("density-short",tag,densityScreens)}
            if(c.code=="i2701")capture("density-pending",tag,densityScreens)
            mapOf("code" to c.code,"direction" to c.direction,"directionLabel" to directionLabel(c),"directionArgb" to actual,"remaining" to c.remaining,"averagePrice" to c.averagePrice,"realized" to c.realized,"displayedNet" to moneyText(c.realized?.let(::decimal)),"fees" to c.fees,"referenceMargin" to c.referenceMargin,"heightDp" to bounds.bottom.value-bounds.top.value)
        }
        return mapOf("allExecutionsCreatedViaRealForm" to true,"additionalExecutions" to 2,"executionCount" to 7,"positionCount" to 4,"positions" to rows,"screenshots" to densityScreens,"result" to "PASS")
    }

    @Test fun sameRealFormDataAndDeviceVisualEvidence(){
        assertEquals("Disposable emulator only","1",shell("getprop ro.kernel.qemu").trim())
        app=ui.activity.application as TradeReviewApplication
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        val empty=LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")))
        runBlocking{app.database.withTransaction{model.repository.write(empty,true)}}
        ui.runOnIdle{model.page(1);model.closeEditor();model.handle["v2-entry-fields"]=null;model.entryClock=Clock.fixed(LocalDate.of(2026,10,14).atTime(10,0).atZone(AutomaticEntry.beijing).toInstant(),ZoneOffset.UTC)}
        waitFor{model.state.value.ledger==facts()}
        ui.onNodeWithText("当前持仓").performClick();capture("empty")
        begin("stock","600000","buy","10","1000","¥1.00")
        ui.onNodeWithTag("execution-quantity").performImeAction();capture("stock-filled")
        val stock=save("600000","1.00","1000");position(stock,"stock-position")
        begin("futures","rb2701","buy-open","3500","2","¥7.01","预计新增保证金占用：¥4,900.00")
        ui.onNodeWithTag("execution-quantity").performImeAction();capture("futures-filled")
        // A real IME window, not a mocked keyboard or static screenshot.
        tap("execution-quantity");ui.onNodeWithTag("execution-quantity").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        ui.runOnIdle{(ui.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(ui.activity.currentFocus,android.view.inputmethod.InputMethodManager.SHOW_FORCED)}
        waitFor{instrumentation.uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}}
        ui.onNodeWithTag("save-execution").assertIsDisplayed();ui.onNodeWithTag("entry-estimated-fee").assertIsDisplayed();ui.onNodeWithTag("entry-estimated-margin").assertIsDisplayed();capture("keyboard")
        ui.onNodeWithTag("execution-quantity").performImeAction()
        val future=save("rb2701","7.01","2");position(future,"futures-position")
        begin("futures","rb2701","buy-open","3510","3","¥10.54","预计新增保证金占用：¥7,371.00");save("rb2701","10.54","5")
        begin("futures","rb2701","sell-close","3530","4","¥14.13","平仓后预计剩余占用：¥2,457.00")
        val close=save("rb2701","14.13","1")
        val net=completeTotal(facts().matches.filter{it.closeExecutionId==close.id}.map{it.net})
        assertEquals(0,BigDecimal("971.833333333333").compareTo(net));assertEquals("￥971.83",moneyText(net))
        ui.onNode(hasText("￥971.83") and hasAnyAncestor(hasTestTag("execution-${close.id}")),useUnmergedTree=true).assertIsDisplayed();capture("saved-list")
        position(future,"partial-position")
        begin("futures","i2701","buy-open","800","2","待估算","预计新增保证金占用：待估算")
        ui.onNodeWithTag("entry-pending-note").assertTextEquals("可保存，费用待估算，净盈亏待计算")
        ui.onNodeWithTag("execution-quantity").performImeAction();capture("pending-filled")
        val pending=save("i2701",null,"2");position(pending,"pending-position")
        assertNull(facts().cycles.single{it.code=="i2701"}.referenceMargin)
        page(3);ui.onNodeWithTag("statistics-period-month").performClick();waitFor{ui.onAllNodesWithTag("period-realized-net").fetchSemanticsNodes().isNotEmpty()};capture("statistics")
        val density=directionDensity()
        val conf=app.resources.configuration
        if(profile!="suite"){
            assertEquals(profile.contains("dark"),conf.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)
            assertEquals(if(profile.startsWith("narrow"))1.3f else 1f,conf.fontScale,0.01f)
            assertTrue("Expected narrow/regular portrait device",conf.screenWidthDp in (if(profile.startsWith("narrow"))318..322 else 358..362))
        }
        val proof=mapOf("profile" to profile,"api" to Build.VERSION.SDK_INT,"device" to Build.MODEL,"screenWidthDp" to conf.screenWidthDp,"screenHeightDp" to conf.screenHeightDp,"fontScale" to conf.fontScale,"night" to (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES),"syntheticOnly" to true,"clockBeijing" to "2026-10-14T10:00:00+08:00","allExecutionsCreatedViaRealForm" to true,"ledger" to LedgerJson.element(facts()),"observations" to results,"screenshots" to screens,"directionDensity" to density,"realPhone" to "not-executed","result" to "PASS")
        File(output(),"v210-visual-$profile-api${Build.VERSION.SDK_INT}.json").writeText(GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(proof))
    }
}
