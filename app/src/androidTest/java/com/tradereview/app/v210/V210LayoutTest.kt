package com.tradereview.app.v210

import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.MainActivity
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.v200.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Large amounts and long identities entered through real UI, with actual layout results. */
@RunWith(AndroidJUnit4::class)
class V210LayoutTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    @get:Rule(order=0) val profile=object:org.junit.rules.ExternalResource(){
        private var font="1.0";private var night="1";private var size:String?=null;private var density:String?=null
        override fun before(){
            assertEquals("Only disposable AVDs","1",shell("getprop ro.kernel.qemu").trim())
            font=shell("settings get system font_scale").trim();night=shell("settings get secure ui_night_mode").trim()
            size=Regex("Override size: ([0-9]+x[0-9]+)").find(shell("wm size"))?.groupValues?.get(1)
            density=Regex("Override density: ([0-9]+)").find(shell("wm density"))?.groupValues?.get(1)
            shell("wm size 640x1136");shell("wm density 320");shell("settings put system font_scale 1.3")
            shell("su 0 cmd uimode night yes");shell("input keyevent KEYCODE_SLEEP");android.os.SystemClock.sleep(1000);shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
        }
        override fun after(){
            shell("wm size ${size?:"reset"}");shell("wm density ${density?:"reset"}");shell("settings put system font_scale ${font.toFloatOrNull()?:1f}")
            shell("su 0 cmd uimode night ${if(night=="2")"yes"else"no"}");shell("input keyevent KEYCODE_SLEEP");android.os.SystemClock.sleep(1000);shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
        }
    }
    @get:Rule(order=1) val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var model:V200ViewModel
    private fun facts()=runBlocking{model.repository.read()}
    private fun waitFor(predicate:()->Boolean)=ui.waitUntil(45000){predicate()}
    private fun field(tag:String,value:String){ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
    private fun capture(name:String){ui.waitForIdle();val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val out=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v210-layout").apply{mkdirs()};File(out,"v210-layout-$name.png").outputStream().use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()}
    private fun numericFits(tag:String){
        val results=mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag(tag,useUnmergedTree=true).performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult){assertTrue(it(results))}
        assertEquals(1,results.size);assertFalse("Numeric width overflow: $tag",results.single().didOverflowWidth);assertFalse("Numeric height overflow: $tag",results.single().didOverflowHeight)
        assertTrue("Numbers remain readable",results.single().layoutInput.style.fontSize.value>=14f)
    }
    @Test fun largeAmountsRemainReadableOnNarrowDarkLargeFontAndRecreation(){
        val app=ui.activity.application as TradeReviewApplication
        assertEquals(320,app.resources.configuration.screenWidthDp)
        assertEquals(1.3f,app.resources.configuration.fontScale,.01f)
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        ui.runOnIdle{model=ViewModelProvider(ui.activity)[V200ViewModel::class.java]}
        runBlocking{app.database.withTransaction{model.repository.write(LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户"),LedgerAccount("futures","futures","国内期货账户")),risks=listOf(MarketRisk("stock"),MarketRisk("futures"))),true)}}
        ui.runOnIdle{model.page(1);model.closeEditor();model.handle["v2-entry-fields"]=null}
        waitFor{model.state.value.ledger==facts()}
        ui.onNodeWithTag("new-execution").performClick();ui.onNodeWithTag("entry-market-stock").performClick()
        listOf("entry-market-stock","entry-market-futures","save-execution","entry-instrument-picker").forEach{tag->val bounds=ui.onNodeWithTag(tag).getUnclippedBoundsInRoot();assertTrue("48dp target: $tag",bounds.bottom.value-bounds.top.value>=48f&&bounds.right.value-bounds.left.value>=48f)}
        field("execution-code","688888");field("execution-price","123456.78");field("execution-quantity","123456")
        ui.onNodeWithTag("entry-options").performScrollTo().performClick();field("execution-name","超长名称合成股票用于验证大字体和持仓信息的完整呈现")
        waitFor{ui.onNodeWithTag("entry-estimated-fee").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""){it.text}=="预计手续费：¥1,524,148.02"}
        ui.onNodeWithTag("save-execution").assertIsDisplayed().performClick();waitFor{!model.editor.value&&!model.busy.value&&model.state.value.ledger?.executions?.size==1}
        assertEquals("1524148.02",facts().executions.single().fees)
        ui.onNodeWithText("当前持仓").performClick();val c=facts().cycles.single();val card="position-${c.id}"
        waitFor{model.message.value==null};ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag(card))
        listOf("quantity","price","net","fees","cost").forEach{numericFits("position-$it-${c.id}")}
        capture("long-name-large-money-portrait")
        ui.runOnIdle{ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        waitFor{var landscape=false;instrumentation.runOnMainSync{landscape=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().any{it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}};landscape}
        ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag(card));numericFits("position-price-${c.id}");numericFits("position-cost-${c.id}");capture("long-name-large-money-landscape")
        ui.activityRule.scenario.recreate();waitFor{ui.onAllNodesWithTag("v2-trades").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag(card));numericFits("position-quantity-${c.id}");assertEquals("1524148.02",facts().executions.single().fees)
        ui.runOnIdle{ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        waitFor{var portrait=false;instrumentation.runOnMainSync{portrait=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().any{it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}};portrait}
    }
}
