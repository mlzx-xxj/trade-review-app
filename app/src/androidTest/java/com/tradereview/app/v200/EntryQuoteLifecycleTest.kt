package com.tradereview.app.v200

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.stability.V08Metrics
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Real form regression for main 6776: T+1 rejection -> cancel -> reopen with a
 * new injected date and the same input. All facts come from UI on a disposable AVD.
 */
@RunWith(AndroidJUnit4::class)
class EntryQuoteLifecycleTest {
    @get:Rule(order=0) val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private lateinit var model:V200ViewModel
    private val observations=mutableListOf<Map<String,Any?>>()
    @get:Rule(order=1) val evidence=object:org.junit.rules.TestWatcher(){
        private var service:AccessibilityServiceInfo?=null
        private var keyboard="null"
        override fun starting(description:org.junit.runner.Description){
            assertEquals("Never mutate a physical phone","1",shell("getprop ro.kernel.qemu").trim())
            service=instrumentation.uiAutomation.serviceInfo
            instrumentation.uiAutomation.serviceInfo=instrumentation.uiAutomation.serviceInfo.apply{flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            keyboard=shell("settings get secure show_ime_with_hard_keyboard").trim()
            shell("settings put secure show_ime_with_hard_keyboard 1")
        }
        override fun failed(error:Throwable,description:org.junit.runner.Description){
            runCatching{val data=mapOf("syntheticOnly" to true,"error" to error.stackTraceToString(),"draft" to model.restoredDraft(),"clock" to model.entryNow().toString(),"observations" to observations,"semantics" to runCatching{ui.onAllNodes(isRoot(),useUnmergedTree=true).printToString()}.getOrElse{it.javaClass.simpleName});V08Metrics.write("entry-quote-failure-api${Build.VERSION.SDK_INT}.json",GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(data))}
            runCatching{val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val directory=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"entry-quote").apply{mkdirs()};File(directory,"entry-quote-failure.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        }
        override fun finished(description:org.junit.runner.Description){
            if(keyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard")else shell("settings put secure show_ime_with_hard_keyboard $keyboard")
            service?.let{instrumentation.uiAutomation.setOnAccessibilityEventListener(null);instrumentation.uiAutomation.serviceInfo=it}
        }
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun waitFor(block:()->Boolean)=ui.waitUntil(45000){block()}
    private fun facts()=runBlocking{model.repository.read()}
    private fun clock(day:LocalDate){ui.runOnIdle{model.entryClock=Clock.fixed(day.atTime(10,0).atZone(AutomaticEntry.beijing).toInstant(),ZoneOffset.UTC)}}
    private fun selected(tag:String){val node=ui.onNodeWithTag(tag);if(!node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected){false})node.performClick()}
    private fun settled(){
        var previous:androidx.compose.ui.geometry.Rect?=null;var since=android.os.SystemClock.elapsedRealtime()
        ui.waitUntil(10000){val nodes=ui.onAllNodesWithTag("entry-scroll").fetchSemanticsNodes();if(nodes.isEmpty())true else{val bounds=nodes.single().boundsInRoot;val now=android.os.SystemClock.elapsedRealtime();if(bounds!=previous){previous=bounds;since=now};now-since>=150}}
    }
    private fun field(tag:String,value:String){
        settled();val node=ui.onNodeWithTag(tag);node.performScrollTo();settled();node.performClick().performTextReplacement(value)
        assertEquals(value,node.fetchSemanticsNode().config[SemanticsProperties.EditableText].text);settled()
    }
    private fun quote(side:String,price:String,fee:String,label:String){
        ui.onNodeWithTag("new-execution").performClick();selected("entry-market-stock")
        field("execution-code","600000");selected("execution-side-$side");field("execution-price",price);field("execution-quantity","1000")
        waitFor{ui.onNodeWithTag("entry-estimated-fee").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}=="预计手续费：¥$fee"}
        ui.onNodeWithTag("save-execution").assertIsDisplayed().assertIsEnabled()
        observations+=mapOf("phase" to label,"side" to side,"price" to price,"quantity" to "1000","fee" to fee,"displayed" to ui.onNodeWithTag("entry-estimated-fee").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text},"clock" to model.entryNow().toString())
    }
    private fun cancel(){ui.onNodeWithTag("entry-cancel").performClick();waitFor{!model.editor.value&&ui.onAllNodesWithTag("save-execution").fetchSemanticsNodes().isEmpty()}}
    @Test fun repeatedT1CancelAndReopenKeepTheQuoteCurrentAndFactsUnchanged(){
        val app=ui.activity.application as TradeReviewApplication
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java];model.closeEditor();model.handle["v2-entry-fields"]=null;model.page(1)}
        runBlocking{app.database.withTransaction{model.repository.write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures"))),true)}}
        waitFor{model.state.value.ledger==facts()}
        val today=AutomaticEntry.today();clock(today.minusDays(1))
        quote("buy","10","1.00","initial-buy");ui.onNodeWithTag("save-execution").performClick()
        waitFor{!model.editor.value&&!model.busy.value&&model.state.value.ledger?.executions?.size==1}
        val before=facts();assertEquals("1.00",before.executions.single().fees)
        repeat(10){index->
            clock(today.minusDays(1));quote("sell","10","6.00","rejected-$index")
            ui.onNodeWithTag("save-execution").performClick()
            waitFor{!model.busy.value&&model.problem.value?.contains("T+1")==true}
            assertEquals(before,facts());cancel()
            clock(today);quote("sell",if(index%2==0)"10"else"11",if(index%2==0)"6.00"else"6.60","reopened-$index")
            assertEquals(before,facts());assertNotEquals(before.executions.single().id,model.restoredDraft()["id"]);cancel()
        }
        clock(today);quote("sell","10","6.00","final-sell");ui.onNodeWithTag("save-execution").performClick()
        waitFor{!model.editor.value&&!model.busy.value&&model.state.value.ledger?.executions?.size==2}
        val after=facts();assertEquals("6.00",after.executions.single{it.side=="sell"}.fees)
        assertEquals("-7",exact(requireNotNull(completeTotal(after.matches.map{it.net}))))
        assertEquals("0",after.cycles.single().remaining);assertTrue(after.audits.isEmpty())
        V08Metrics.write("entry-quote-lifecycle-api${Build.VERSION.SDK_INT}.json",GsonBuilder().setPrettyPrinting().create().toJson(mapOf("api" to Build.VERSION.SDK_INT,"syntheticOnly" to true,"quotes" to observations,"reopenCycles" to 10,"t1RejectedWithoutMutation" to true,"confirmedExecutions" to 2,"finalNet" to "-7","finalRemaining" to "0","result" to "PASS")))
    }
}
