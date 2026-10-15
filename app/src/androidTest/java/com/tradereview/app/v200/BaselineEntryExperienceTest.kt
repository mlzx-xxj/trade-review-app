package com.tradereview.app.v200

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import com.google.gson.GsonBuilder

/** Before-change baseline. Every execution below is created through the real form. */
@RunWith(AndroidJUnit4::class)
class BaselineEntryExperienceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var model:V200ViewModel
    private var taps=0;private var scrolls=0;private val fields=linkedSetOf<String>()
    private fun tap(tag:String){val n=ui.onNodeWithTag(tag);if(runCatching{n.assertIsDisplayed()}.isFailure){n.performScrollTo();scrolls++};n.assertIsDisplayed().performClick();taps++}
    private fun enter(tag:String,value:String){tap(tag);ui.onNodeWithTag(tag).performTextReplacement(value);fields+=tag}
    @Test fun observeStockAndFuturesBeforeSimplification() {
        val app=ui.activity.application as TradeReviewApplication
        runBlocking {app.database.withTransaction {LedgerRepository(app.database).write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures"))),true)}}
        ui.runOnIdle {model=ViewModelProvider(ui.activity)[V200ViewModel::class.java];model.page(1);model.closeEditor();model.handle["v2-entry-fields"]=null}
        ui.waitUntil(30000){model.state.value.ledger?.executions?.isEmpty()==true}
        val observations=mutableListOf<Map<String,Any>>()
        for(market in listOf("stock","futures")) {
            taps=0;scrolls=0;fields.clear();tap("new-execution")
            if(market=="futures")tap("entry-market-futures")
            enter("execution-code",if(market=="stock")"600000"else"rb2701")
            enter("execution-name","合成基线样本")
            enter("execution-price",if(market=="stock")"10"else"3500")
            enter("execution-quantity",if(market=="stock")"1000"else"2")
            if(market=="futures") {
                tap("execution-parameters");enter("execution-multiplier","10");enter("execution-margin","0.12");enter("execution-fees","4")
                ui.onNodeWithText("本笔合约参数已经核实").performScrollTo()
                ui.onNode(isToggleable() and hasAnyAncestor(hasAnyDescendant(hasText("本笔合约参数已经核实")))).performClick();taps++
                ui.onAllNodesWithText("核实夜盘交易日").fetchSemanticsNodes().firstOrNull()?.let{ui.onNodeWithText("核实夜盘交易日").performClick();taps++}
            }
            tap("save-execution")
            val expected=if(market=="stock")1 else 2
            ui.waitUntil(30000){!model.busy.value && model.state.value.ledger?.executions?.size==expected}
            assertFalse(model.editor.value)
            val saved=model.state.value.ledger!!.executions.first{it.accountId==market}
            assertEquals(if(market=="stock")"1"else"4",saved.fees)
            observations+=mapOf("market" to market,"controlTapsIncludingEntry" to taps,"textFields" to fields.toList(),"requiredScrollCommands" to scrolls,"savedFees" to saved.fees,"feeAndMarginLiveQuoteAvailable" to false,"syntheticFuturesMultiplier" to "10","syntheticFuturesMarginRate" to "0.12","syntheticFuturesActualFees" to "4","result" to "PASS")
        }
        val folder=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))).apply{mkdirs()}
        File(folder,"entry-baseline-api${android.os.Build.VERSION.SDK_INT}.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(observations))
    }
}
