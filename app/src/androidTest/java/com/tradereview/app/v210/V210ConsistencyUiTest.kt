package com.tradereview.app.v210

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import com.tradereview.app.v200.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Shared test transport, using the production Activity and disposable AVDs only. */
abstract class ConsistencyUiProbe {
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val args = InstrumentationRegistry.getArguments()
    protected val visualProfile = args.getString("visualProfile") ?: "narrow-dark"
    protected lateinit var app: TradeReviewApplication
    protected lateinit var model: V200ViewModel
    protected var lastStep = "initialization"
    protected val observations = mutableListOf<Map<String, Any?>>()
    protected val pictures = mutableListOf<String>()
    private var caseName = "initial"

    protected fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    @get:Rule(order = 0) val deviceProfile = object : org.junit.rules.ExternalResource() {
        private var changed = false
        private var font = "1.0"
        private var night = "1"
        private var size: String? = null
        private var density: String? = null
        override fun before() {
            assertEquals("Never operate on a phone", "1", shell("getprop ro.kernel.qemu").trim())
            if (args.getString("visualProfile") != null) return
            font = shell("settings get system font_scale").trim()
            night = shell("settings get secure ui_night_mode").trim()
            size = Regex("Override size: ([0-9]+x[0-9]+)").find(shell("wm size"))?.groupValues?.get(1)
            density = Regex("Override density: ([0-9]+)").find(shell("wm density"))?.groupValues?.get(1)
            changed = true
            shell("wm size 640x1136")
            shell("wm density 320")
            shell("settings put system font_scale 1.3")
            shell("su 0 cmd uimode night yes")
            shell("input keyevent KEYCODE_SLEEP")
            android.os.SystemClock.sleep(1000)
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        override fun after() {
            if (!changed) return
            shell("wm size " + (size ?: "reset"))
            shell("wm density " + (density ?: "reset"))
            shell("settings put system font_scale " + (font.toFloatOrNull() ?: 1f))
            shell("su 0 cmd uimode night " + if (night == "2") "yes" else "no")
            shell("input keyevent KEYCODE_SLEEP")
            android.os.SystemClock.sleep(1000)
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
    }
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2) val evidence = object : org.junit.rules.TestWatcher() {
        private var service: AccessibilityServiceInfo? = null
        private var keyboard = "null"
        override fun starting(description: org.junit.runner.Description) {
            caseName = description.methodName.lowercase()
            service = instrumentation.uiAutomation.serviceInfo
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            keyboard = shell("settings get secure show_ime_with_hard_keyboard").trim()
            shell("settings put secure show_ime_with_hard_keyboard 1")
        }
        override fun failed(error: Throwable, description: org.junit.runner.Description) {
            runCatching { capture("failure", false) }
            runCatching { report("FAIL", error.stackTraceToString()) }
        }
        override fun finished(description: org.junit.runner.Description) {
            if (keyboard == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard " + keyboard)
            service?.let { instrumentation.uiAutomation.serviceInfo = it }
        }
    }

    @Before fun initializeProbe() {
        app = ui.activity.application as TradeReviewApplication
        ui.runOnIdle {
            model = ViewModelProvider(ui.activity)[V200ViewModel::class.java]
            model.page(0)
            model.closeEditor()
            model.handle["v2-entry-fields"] = null
        }
        waitFor { model.state.value.ledger != null }
        if (args.getString("consistencyPhase") != "reopen") {
            val empty = LedgerSnapshot(
                accounts = listOf(LedgerAccount("stock", "stock", "A股账户"), LedgerAccount("futures", "futures", "国内期货账户")),
                risks = listOf(MarketRisk("stock"), MarketRisk("futures"))
            )
            runBlocking { app.database.withTransaction { model.repository.write(empty, true) } }
            waitFor { model.state.value.ledger == empty }
        }
        val conf = app.resources.configuration
        assertEquals(if (visualProfile.endsWith("dark")) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
            conf.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(if (visualProfile.startsWith("narrow")) 1.3f else 1f, conf.fontScale, .01f)
        assertTrue(conf.screenWidthDp in if (visualProfile.startsWith("narrow")) 318..322 else 358..362)
    }
    protected fun facts() = runBlocking { model.repository.read() }
    protected fun waitFor(predicate: () -> Boolean) = ui.waitUntil(45000) { predicate() }
    protected fun page(index: Int) { ui.onNodeWithTag("v2-main-page-" + index).performClick() }
    protected fun back() { ui.onNodeWithContentDescription("返回主页面").performClick() }
    protected fun output() = File(requireNotNull(args.getString("additionalTestOutputDir")), "v210-consistency").apply { mkdirs() }
    protected fun visible(n: SemanticsNodeInteraction): SemanticsNodeInteraction {
        if (runCatching { n.assertIsDisplayed() }.isFailure) {
            val node = n.fetchSemanticsNode()
            val lazy = generateSequence(node.parent) { it.parent }.firstOrNull { it.config.contains(SemanticsActions.ScrollToIndex) }
            if (lazy == null) n.performScrollTo() else {
                val tag = requireNotNull(lazy.config.getOrNull(SemanticsProperties.TestTag))
                val targetTag = node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.isNotBlank() }
                val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text
                val list = ui.onNodeWithTag(tag)
                list.performScrollToNode(targetTag?.let(::hasTestTag) ?: hasText(requireNotNull(text)))
                var attempts = 0
                while (runCatching { n.assertIsDisplayed() }.isFailure && attempts++ < 8) {
                    val current = n.fetchSemanticsNode()
                    val viewport = list.fetchSemanticsNode().boundsInRoot
                    val y = current.positionInRoot.y
                    val delta = if (y < viewport.top) y - viewport.top - 12f else y + current.size.height - viewport.bottom + 12f
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { action -> check(action(0f, delta)) }
                    ui.waitForIdle()
                }
            }
        }
        return n.assertIsDisplayed()
    }
    protected fun tap(tag: String) {
        if (tag == "contract-rules-entry") ui.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasTestTag(tag))
        if (tag == "configure-account") ui.onNodeWithTag("v2-accounts").performScrollToNode(hasTestTag(tag))
        visible(ui.onNodeWithTag(tag)).assertIsEnabled().performClick()
    }
    protected fun tapText(text: String, modal: Boolean = false) {
        val matcher = hasText(text) and hasClickAction()
        visible(ui.onNode(if (modal) matcher and hasAnyAncestor(isDialog()) else matcher)).assertIsEnabled().performClick()
    }
    protected fun field(tag: String, value: String) {
        lastStep = "input " + tag
        tap(tag)
        ui.onNodeWithTag(tag).performTextReplacement(value)
        ui.onNodeWithTag(tag).assertTextContains(value)
    }
    protected fun hideKeyboard() {
        if (instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            shell("input keyevent KEYCODE_BACK")
            ui.waitForIdle()
        }
    }
    protected fun recreate() {
        lastStep = "recreate with unsaved editor"
        ui.activityRule.scenario.recreate()
        waitFor {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().isNotEmpty()
            }
            resumed
        }
        ui.waitForIdle()
    }
    protected fun saveAndWait(tag: String, predicate: () -> Boolean) {
        tap(tag)
        waitFor { !model.busy.value && predicate() }
        assertNull(model.problem.value)
        waitFor { model.state.value.ledger == facts() }
    }
    protected fun capture(scene: String, settle: Boolean = true) {
        if (settle) ui.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val bytes = ByteArrayOutputStream().also { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        bitmap.recycle()
        val name = "v210-consistency-" + caseName + "-" + visualProfile + "-" + scene + ".png"
        File(output(), name).writeBytes(bytes)
        // Persist original PNG bytes through AGP uninstall, without storage permission.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TradeReview-v210-consistency")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        pictures += name
    }
    protected fun accessibleText(label: String): Map<String, Any?> {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }.forEach { it.root?.let(queue::add) }
        var inspected = 0
        while (queue.isNotEmpty()) {
            check(++inspected <= 8000)
            val node = queue.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                return mapOf("label" to label, "visible" to node.isVisibleToUser, "boundsPx" to listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return mapOf("label" to label, "visible" to false, "boundsPx" to null)
    }
    protected fun report(result: String, error: String? = null) {
        val conf = app.resources.configuration
        val tree = runCatching { ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString() }.getOrElse { it.toString() }
        val value = mapOf(
            "api" to Build.VERSION.SDK_INT, "profile" to visualProfile, "case" to caseName,
            "syntheticOnly" to true, "activity" to MainActivity::class.java.name, "pid" to android.os.Process.myPid(),
            "screenWidthDp" to conf.screenWidthDp, "fontScale" to conf.fontScale,
            "night" to (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES),
            "lastStep" to lastStep, "observations" to observations, "screenshots" to pictures,
            "ledger" to LedgerJson.element(facts()), "semantics" to tree, "error" to error, "result" to result
        )
        V08Metrics.write("v210-consistency-" + caseName + "-api" + Build.VERSION.SDK_INT + ".json",
            GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(value))
    }
}

@RunWith(AndroidJUnit4::class)
class V210ConsistencyUiTest : ConsistencyUiProbe() {
    @Test fun strategyEditorKeepsIdentityAndDraftAfterRecreation() {
        tap("open-risk-strategy")
        tap("risk-section-strategies")
        tap("new-strategy")
        field("strategy-name", "合成趋势策略")
        field("strategy-description", "仅用于隔离模拟器")
        saveAndWait("save-strategy") { facts().strategies.size == 1 }
        val original = facts().strategies.single()
        waitFor { model.message.value == null }
        tapText("编辑")
        val name = "合成趋势策略已更正"
        val note = "合成说明：只核对编辑身份与文字保存，不改动历史交易。".repeat(5).take(200)
        field("strategy-name", name)
        field("strategy-description", note)
        capture("before-recreate")
        recreate()
        capture("after-recreate")
        ui.onNodeWithTag("strategy-name").assertTextContains(name)
        ui.onNodeWithTag("strategy-description").assertTextContains(note)
        saveAndWait("save-strategy") { facts().strategies.singleOrNull()?.name == name }
        assertEquals(original.id, facts().strategies.single().id)
        assertEquals(note, facts().strategies.single().description)
        capture("saved")
        report("PASS")
    }

    @Test fun contractRuleEditorKeepsIdentityAndDraftAfterRecreation() {
        tap("open-risk-strategy")
        tap("contract-rules-entry")
        tapText("新增合约规则")
        field("rule-code", "i2701")
        field("rule-name", "合成合约规则")
        field("rule-multiplier", "100")
        field("rule-margin", "0.1")
        field("rule-open-fee", "1")
        field("rule-close-fee", "2")
        field("rule-today-fee", "3")
        tap("rule-verified")
        saveAndWait("save-contract-rule") { facts().rules.size == 1 }
        val original = facts().rules.single()
        waitFor { model.message.value == null }
        tapText("编辑，不改写历史")
        field("rule-name", "合成合约规则已更正")
        capture("before-recreate")
        recreate()
        capture("after-recreate")
        ui.onNodeWithTag("rule-name").assertTextContains("合成合约规则已更正")
        ui.onNodeWithTag("rule-code").assertTextContains("i2701")
        saveAndWait("save-contract-rule") { facts().rules.singleOrNull()?.name == "合成合约规则已更正" }
        assertEquals(original.id, facts().rules.single().id)
        assertEquals(original.copy(name = "合成合约规则已更正"), facts().rules.single())
        capture("saved")
        report("PASS")
    }

    @Test fun riskInputKeepsHeaderVisibleWithRealKeyboard() {
        val before = facts()
        tap("open-risk-strategy")
        field("risk-fund-stock", "10000")
        field("risk-limit-stock", "500")
        waitFor { instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        val header = accessibleText("风险与策略")
        observations += header
        capture("keyboard-header")
        val bounds = header["boundsPx"] as? List<*>
        assertTrue("Risk title must stay actually visible above the IME: " + header,
            header["visible"] == true && bounds != null && (bounds[1] as Int) >= 0 && (bounds[3] as Int) > (bounds[1] as Int))
        tap("save-risk-stock")
        waitFor { !model.busy.value && facts().risks.single { it.id == "stock" }.riskFund == "10000" }
        assertNull(model.problem.value)
        assertEquals(before.risks.single { it.id == "futures" }, facts().risks.single { it.id == "futures" })
        assertEquals(before.executions, facts().executions)
        capture("saved-risk")
        report("PASS")
    }

    @Test fun riskRuleAndAccountControlsExposeNamesAndState() {
        val before = facts()
        fun named(label: String, initial: Boolean, tag: String? = null) {
            val matcher = hasText(label) and isToggleable()
            val nodes = ui.onAllNodes(if (tag == null) matcher else matcher and hasTestTag(tag))
            val matches = nodes.fetchSemanticsNodes()
            val displayed = matches.indices.filter { runCatching { nodes[it].assertIsDisplayed() }.isSuccess }
            val found = displayed.size == 1
            observations += mapOf("label" to label, "namedToggleCount" to matches.size, "namedVisibleToggleCount" to displayed.size, "named" to found)
            if (found) {
                val n = nodes[displayed.single()]
                visible(n).assertIsEnabled()
                if (initial) n.assertIsOn() else n.assertIsOff()
                n.performClick()
                if (initial) n.assertIsOff() else n.assertIsOn()
                n.performClick()
            }
        }
        tap("open-risk-strategy")
        ui.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasText("超风险提示"))
        named("超风险提示", true)
        capture("risk-control")
        back()
        page(4)
        tap("configure-account")
        named("期初、所有买卖及出入金历史已完整核对", false, "account-complete")
        capture("account-control")
        tapText("取消", true)
        tap("open-risk-strategy")
        tap("contract-rules-entry")
        tapText("新增合约规则")
        visible(ui.onNodeWithTag("rule-verified"))
        named("我已核实此合约参数", false, "rule-verified")
        capture("rule-control")
        tapText("取消", true)
        assertEquals(before, facts())
        val unnamed = observations.filter { it["named"] != true }
        assertTrue("Toggle names must be attached to the actual stateful controls: " + unnamed, unnamed.isEmpty())
        report("PASS")
    }
}
