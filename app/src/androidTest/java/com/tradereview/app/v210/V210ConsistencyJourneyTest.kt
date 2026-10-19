package com.tradereview.app.v210

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.DatePicker
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.tradereview.app.backup.BackupCodec
import com.tradereview.app.backup.BackupRepository
import com.tradereview.app.stability.V08Metrics
import com.tradereview.app.ui.BackupViewModel
import com.tradereview.app.v200.*
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.regex.Pattern

/** Every financial/configuration write and SAF operation uses the production UI. */
@RunWith(AndroidJUnit4::class)
class V210ConsistencyJourneyTest : ConsistencyUiProbe() {
    private val anchor = LocalDate.of(2026, 10, 17)
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val transportProfile get() = args.getString("visualProfile") ?: "suite"
    private val documents = Pattern.compile("com(?:\\.google)?\\.android\\.documentsui")
    private val operations = mutableListOf<Map<String, Any?>>()
    private val displayedMoney = mutableListOf<Map<String, Any?>>()
    private var originalTrading: LedgerSnapshot? = null
    private var wholeBackupAtExport: com.google.gson.JsonObject? = null
    private fun wholeFacts(): com.google.gson.JsonObject {
        val data = runBlockingExport()
        return JsonParser.parseString(BackupCodec.encode(data)).asJsonObject.apply {
            // Only transport time/checksum vary between read-only exports.
            remove("exportedAt")
            remove("checksumSha256")
        }
    }
    private fun runBlockingExport() = kotlinx.coroutines.runBlocking {
        BackupRepository(app.database, app.evidenceStore).export(full = true)
    }
    private fun step(label: String, action: () -> Unit) {
        lastStep = label
        operations += mapOf("step" to label, "event" to "begin", "elapsedRealtimeMs" to android.os.SystemClock.elapsedRealtime())
        android.util.Log.i("TestRunner", "Consistency begin: " + label)
        flushOperations()
        action()
        operations += mapOf("step" to label, "event" to "complete", "elapsedRealtimeMs" to android.os.SystemClock.elapsedRealtime())
        android.util.Log.i("TestRunner", "Consistency complete: " + label)
        flushOperations()
    }
    private fun flushOperations() {
        val phase = args.getString("consistencyPhase") ?: "journey"
        File(output(), "v210-consistency-operations-" + transportProfile + "-" + phase + "-api" + Build.VERSION.SDK_INT + ".json")
            .writeText(GsonBuilder().setPrettyPrinting().create().toJson(operations))
    }
    private fun snap(scene: String, systemWindow: Boolean = false) {
        // Keep first-run and reopened financial screens as distinct original files.
        val phase = args.getString("consistencyPhase") ?: "journey"
        capture(scene + "-" + transportProfile + "-" + phase, !systemWindow)
    }
    private fun orientation(requested: Int, expected: Int) {
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<com.tradereview.app.MainActivity>().single().requestedOrientation = requested
        }
        waitFor {
            var ready = false
            instrumentation.runOnMainSync {
                ready = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<com.tradereview.app.MainActivity>().any { it.resources.configuration.orientation == expected }
            }
            ready
        }
        ui.waitForIdle()
    }
    private fun settled() { waitFor { !model.busy.value && model.message.value == null && model.state.value.ledger == facts() } }
    private fun select(tag: String) {
        if (!ui.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }) tap(tag)
    }
    private fun riskTab(id: String) {
        ui.onNodeWithTag("v2-risk-strategy").performScrollToIndex(0)
        tap("risk-section-" + id)
    }
    private fun unchanged(before: LedgerSnapshot = requireNotNull(originalTrading)) {
        val after = facts()
        assertEquals(before.executions, after.executions)
        assertEquals(before.lots, after.lots)
        assertEquals(before.matches, after.matches)
        assertEquals(before.cycles, after.cycles)
        assertEquals(before.audits, after.audits)
        assertEquals(before.plans, after.plans)
        assertEquals(before.reviews, after.reviews)
    }
    private fun expectedFinance() {
        val s = facts()
        val stock = s.cycles.single { it.accountId == "stock" }
        assertEquals("500", stock.remaining)
        assertEquals(0, BigDecimal("2487.65").compareTo(BigDecimal(stock.realized)))
        assertEquals(0, BigDecimal("5500.55").compareTo(BigDecimal(stock.openCost)))
        val unknown = s.cycles.single { it.accountId == "futures" }
        assertEquals("1", unknown.remaining)
        assertNull(unknown.realized)
        assertNull(unknown.fees)
        assertNull(unknown.referenceMargin)
        assertTrue(s.executions.filter { it.accountId == "futures" }.all { it.fees == null && it.marginRate == null })
        assertEquals(3, s.matches.size)
    }
    private fun execution(day: LocalDate, market: String, code: String, action: String, price: String, quantity: String, fee: String?, strategy: String? = null) {
        val count = facts().executions.size
        ui.runOnIdle {
            model.entryClock = Clock.fixed(day.atTime(10, 0, count).atZone(AutomaticEntry.beijing).toInstant(), ZoneOffset.UTC)
        }
        page(1)
        tap("new-execution")
        select("entry-market-" + market)
        field("execution-code", code)
        select(if (market == "stock") "execution-side-" + action else "execution-action-" + action)
        field("execution-price", price)
        field("execution-quantity", quantity)
        if (strategy != null) {
            tap("entry-options")
            tap("execution-strategy")
            ui.onNode(hasText(strategy) and hasAnyAncestor(isPopup())).assertIsDisplayed().performClick()
        }
        waitFor {
            ui.onNodeWithTag("entry-estimated-fee").fetchSemanticsNode().config[SemanticsProperties.Text]
                .joinToString("") { it.text } == "预计手续费：" + (fee?.let { "¥" + it } ?: "待估算")
        }
        tap("save-execution")
        waitFor { !model.busy.value && !model.editor.value && facts().executions.size == count + 1 }
        assertNull(model.problem.value)
        assertNull(model.pending.value)
        val saved = facts().executions.single { it.ordinal == (count + 1).toLong() }
        assertEquals(code, saved.code)
        assertEquals(fee, saved.fees)
        waitFor { model.state.value.ledger == facts() }
    }
    private fun saveControl(label: String, tag: String? = null, inputTag: String? = null): Map<String, Any?> {
        waitFor { instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        if (tag != null) visible(ui.onNodeWithTag(tag))
        var previous: List<Float>? = null
        var stableSince = android.os.SystemClock.elapsedRealtime()
        waitFor {
            val keyboard = instrumentation.uiAutomation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
            val target = (inputTag ?: tag)?.let { runCatching { ui.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }.getOrNull() }
            val geometry = listOf(keyboard?.top?.toFloat() ?: -1f, target?.top ?: -1f, target?.bottom ?: -1f)
            val now = android.os.SystemClock.elapsedRealtime()
            if (geometry != previous) { previous = geometry; stableSince = now }
            keyboard != null && now - stableSince >= 150
        }
        ui.waitForIdle()
        val ime = instrumentation.uiAutomation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }.forEach { it.root?.let(queue::add) }
        var found: AccessibilityNodeInfo? = null
        var inspected = 0
        while (queue.isNotEmpty() && found == null) {
            check(++inspected <= 8000)
            val node = queue.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                var candidate: AccessibilityNodeInfo? = node
                var depth = 0
                while (candidate != null && !candidate.isClickable && depth++ < 20) candidate = candidate.parent
                if (candidate?.isClickable == true && candidate.isVisibleToUser) found = candidate
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        val b = found?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
        val height = b?.height()?.div(app.resources.displayMetrics.density)
        val input = inputTag?.let { runCatching { ui.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }.getOrNull() }
        // A multiline field may scroll beneath the keyboard. Measure its actual readable
        // intersection above IME; the separate Save target must remain wholly above IME.
        val inputVisibleBounds = input?.let {
            androidx.compose.ui.geometry.Rect(it.left, maxOf(0f, it.top), it.right,
                maxOf(maxOf(0f, it.top), minOf(it.bottom, ime?.top?.toFloat() ?: it.bottom)))
        }
        val inputVisibleHeight = inputVisibleBounds?.height?.div(app.resources.displayMetrics.density)
        val inputVisible = inputTag?.let {
            inputVisibleBounds != null && runCatching { ui.onNodeWithTag(it).assertIsDisplayed() }.isSuccess &&
                inputVisibleBounds.width > 0f && inputVisibleHeight != null && inputVisibleHeight >= 24f
        }
        val value = mapOf("label" to label, "orientation" to app.resources.configuration.orientation,
            "keyboardVisible" to (ime != null), "imeTopPx" to ime?.top,
            "buttonBoundsPx" to b?.let { listOf(it.left, it.top, it.right, it.bottom) }, "touchHeightDp" to height,
            "inputTag" to inputTag, "inputVisible" to inputVisible,
            "inputBoundsPx" to input?.let { listOf(it.left, it.top, it.right, it.bottom) },
            "inputVisibleBoundsPx" to inputVisibleBounds?.let { listOf(it.left, it.top, it.right, it.bottom) },
            "inputVisibleHeightDp" to inputVisibleHeight,
            "aboveKeyboard" to (b != null && b.top >= 0 && (ime == null || b.bottom <= ime.top)))
        observations += value
        assertTrue("Actual keyboard expected for " + label, ime != null)
        assertTrue("Accessible save control is visible and above IME: " + value, value["aboveKeyboard"] == true)
        assertTrue("At least 48dp actual touch target: " + value, height != null && height >= 48f)
        assertTrue("Focused dialog input must retain a visible text line above IME: " + value, inputTag == null || inputVisible == true)
        return value
    }
    private fun ruleEffectiveDate(day: LocalDate) {
        hideKeyboard()
        val button = ui.onNode(hasText("生效日期", substring = true) and hasClickAction() and hasAnyAncestor(isDialog()))
        visible(button).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Choose the synthetic effective date in the real system DatePicker"
            override fun perform(controller: UiController, view: View) {
                (view as DatePicker).updateDate(day.year, day.monthValue - 1, day.dayOfMonth)
                controller.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        ui.onNode(hasText("生效日期 " + day) and hasAnyAncestor(isDialog())).assertExists()
    }
    private fun createRule() {
        tap("contract-rules-entry")
        tapText("新增合约规则")
        field("rule-code", "i2701")
        field("rule-name", "合成合约参数")
        field("rule-multiplier", "100")
        field("rule-margin", "0.1")
        field("rule-open-fee", "1")
        field("rule-close-fee", "2")
        field("rule-today-fee", "3")
        // Cover the old executions: new reference fees must never fill their null snapshots.
        ruleEffectiveDate(anchor.minusDays(2))
        tap("rule-verified")
        saveAndWait("save-contract-rule") { facts().rules.size == 1 }
        settled()
        assertEquals(anchor.minusDays(2).toEpochDay(), facts().rules.single().effectiveDay)
        val id = facts().rules.single().id
        tapText("编辑，不改写历史")
        field("rule-name", "合成合约参数已更正")
        recreate()
        ui.onNodeWithTag("rule-name").assertTextContains("合成合约参数已更正")
        orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        ui.onNodeWithTag("rule-name").assertTextContains("合成合约参数已更正")
        field("rule-name", "合成合约参数已更正")
        saveControl("保存", inputTag = "rule-name")
        snap("rule-landscape-keyboard")
        orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        ui.onNodeWithTag("rule-name").assertTextContains("合成合约参数已更正")
        field("rule-name", "合成合约参数已更正")
        saveControl("保存", inputTag = "rule-name")
        snap("rule-keyboard")
        saveAndWait("save-contract-rule") { facts().rules.singleOrNull()?.name == "合成合约参数已更正" }
        assertEquals(id, facts().rules.single().id)
        unchanged()
    }
    @Suppress("DEPRECATION") // Recycle framework snapshots on API29; API33+ no longer pools them.
    private fun documentsWindow(kind: String) {
        var actualPackage: String? = null
        var actualWindowId: Int? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        do {
            // DocumentsUI can replace its loading root. Read package data from a
            // fresh framework snapshot rather than refreshing a cached UiObject2.
            for (window in instrumentation.uiAutomation.windows) {
                val root = window.root ?: continue
                val candidate = try { root.packageName?.toString() } finally { root.recycle() }
                if (candidate != null && documents.matcher(candidate).matches()) {
                    actualPackage = candidate
                    actualWindowId = window.id
                    break
                }
            }
            if (actualPackage == null) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root != null) {
                    val candidate = root.packageName?.toString()
                    val windowId = root.windowId
                    root.recycle()
                    if (candidate != null && documents.matcher(candidate).matches()) {
                        actualPackage = candidate
                        actualWindowId = windowId
                    }
                }
            }
            if (actualPackage == null) android.os.SystemClock.sleep(50)
        } while (actualPackage == null && android.os.SystemClock.elapsedRealtime() < deadline)
        val packageName = requireNotNull(actualPackage) { "Real DocumentsUI " + kind + " must open" }
        device.waitForIdle()
        operations += mapOf("step" to "DocumentsUI", "kind" to kind, "package" to packageName,
            "windowId" to actualWindowId, "packageProbe" to "UiAutomation window root snapshot",
            "foregroundPackage" to device.currentPackageName)
        flushOperations()
    }
    private fun downloadsRoot() {
        val drawer = requireNotNull(device.wait(Until.findObject(By.desc("Show roots")), 10000))
        drawer.click()
        device.waitForIdle()
        val roots = requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile(".*:id/roots_list"))), 10000))
        requireNotNull(roots.findObject(By.text("Downloads"))).click()
        device.waitForIdle()
    }
    private fun chooseBackup(fileName: String) {
        tap("v2-import-backup")
        documentsWindow("OpenDocument")
        downloadsRoot()
        // A ZIP's preview button also mentions its filename in contentDescription.
        // Select the exact visible title instead; preview does not return a document URI.
        device.findObject(By.desc(Pattern.compile("(?i)(List view|列表视图)")))?.let {
            it.click()
            device.waitForIdle()
        }
        var file = device.findObject(By.text(fileName))
        if (file == null) {
            val list = UiScrollable(UiSelector().resourceIdMatches(".*:id/dir_list").scrollable(true))
                .setAsVerticalList().setSwipeDeadZonePercentage(.2)
            val found = runCatching { list.scrollIntoView(UiSelector().text(fileName)) }
                .onFailure { extraEvidence["documentsScrollError"] = it.stackTraceToString() }.getOrDefault(false)
            file = if (found) device.findObject(By.text(fileName)) else null
        }
        fun retainPickerFailure(reason: String) {
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            extraEvidence["documentsHierarchy"] = hierarchy.toString("UTF-8")
            extraEvidence["documentsFailure"] = reason
            snap("system-open-failure", true)
        }
        if (file == null) retainPickerFailure("Exact exported filename not found after actual directory scrolling")
        val title = requireNotNull(file) { "Exported ZIP title not shown after actual DocumentsUI scrolling" }
        assertEquals(fileName, title.text)
        val bounds = title.visibleBounds
        assertTrue("Document title must have a visible click target", bounds.width() > 0 && bounds.height() > 0)
        operations += mapOf("step" to "DocumentsUI title selection", "fileName" to fileName,
            "selectedText" to title.text, "resource" to title.resourceName,
            "boundsPx" to listOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            "elapsedRealtimeMs" to android.os.SystemClock.elapsedRealtime())
        flushOperations()
        title.click()
        val dismissed = device.wait(Until.gone(By.pkg(documents)), 20000)
        if (!dismissed) retainPickerFailure("Picker did not return after exact filename click")
        assertTrue("Actual filename selection must return to MainActivity", dismissed)
        var backup: BackupViewModel? = null
        ui.runOnIdle { backup = ViewModelProvider(ui.activity)[BackupViewModel::class.java] }
        waitFor { backup!!.preview.value != null && !backup!!.busy.value }
        ui.onNodeWithText("恢复预览（尚未写入）").assertExists()
    }
    private fun backupRoundTrip(): Pair<String, LedgerSnapshot> {
        hideKeyboard()
        settled()
        tap("open-backup")
        val exported = facts()
        val allExported = wholeFacts()
        wholeBackupAtExport = allExported
        extraEvidence["wholeBackupAtExport"] = allExported
        tap("v2-export-backup")
        tapText("完整 ZIP（推荐）", true)
        documentsWindow("CreateDocument")
        downloadsRoot()
        val fileName = "v210-consistency-" + Build.VERSION.SDK_INT + "-" + transportProfile + "-" + System.currentTimeMillis() + ".zip"
        val title = requireNotNull(device.wait(Until.findObject(By.res("android:id/title").clazz("android.widget.EditText")), 10000))
        title.text = fileName
        device.waitForIdle()
        assertEquals(fileName, title.text)
        snap("system-save", true)
        requireNotNull(device.findObject(By.res("android:id/button1"))).click()
        assertTrue(device.wait(Until.gone(By.pkg(documents)), 20000))
        lateinit var backup: BackupViewModel
        ui.runOnIdle { backup = ViewModelProvider(ui.activity)[BackupViewModel::class.java] }
        waitFor { !backup.busy.value && backup.message.value?.contains("已重新读取并通过 SHA-256") == true }
        assertEquals(exported, facts())
        assertEquals(allExported, wholeFacts())
        chooseBackup(fileName)
        snap("restore-preview")
        ui.onNodeWithTag("v2-backup").performScrollToNode(hasText("取消") and hasClickAction())
        tapText("取消")
        waitFor { backup.preview.value == null }
        assertEquals(exported, facts())
        assertEquals(allExported, wholeFacts())
        // Change a configuration through UI, then restore the actual exported ZIP.
        tap("open-risk-strategy")
        riskTab("risk")
        field("risk-fund-stock", "20000")
        saveAndWait("save-risk-stock") { facts().risks.single { it.id == "stock" }.riskFund == "20000" }
        unchanged()
        hideKeyboard()
        tap("open-backup")
        chooseBackup(fileName)
        ui.onNodeWithTag("v2-backup").performScrollToNode(hasText("覆盖备份范围"))
        tapText("覆盖备份范围")
        ui.onNodeWithTag("v2-backup").performScrollToNode(hasTestTag("v2-restore-continue"))
        tap("v2-restore-continue")
        snap("restore-confirm")
        tap("v2-confirm-restore")
        waitFor { !backup.busy.value && backup.preview.value == null && backup.message.value == "覆盖恢复完成" }
        assertEquals(exported, facts())
        assertEquals(allExported, wholeFacts())
        unchanged()
        expectedFinance()
        snap("restored")
        return fileName to exported
    }
    private fun writeProof(name: String, value: Map<String, Any?>) {
        val json = GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(value)
        File(output(), name).writeText(json)
        // Native-suite files use a separate name, preventing duplicate profile data.
        if (args.getString("visualProfile") == null) V08Metrics.write(name, json)
    }
    private fun inspectAndCancelSharedEditors(before: LedgerSnapshot, whole: com.google.gson.JsonObject) {
        fun inspect(tag: String, text: String, saveLabel: String, scene: String) {
            field(tag, text)
            orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            ui.onNodeWithTag(tag).assertTextContains(text)
            field(tag, text)
            saveControl(saveLabel, inputTag = tag)
            snap(scene)
            orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            ui.onNodeWithTag(tag).assertTextContains(text)
            tapText("取消", true)
            assertEquals(before, facts())
            assertEquals(whole, wholeFacts())
        }
        page(2)
        tap("review-section-plan")
        tap("new-plan")
        field("plan-code", "603999")
        inspect("plan-note", "合成横屏计划：取消后不得保存。", "保存计划", "plan-landscape-keyboard")
        tap("review-section-post")
        val stock = before.cycles.single { it.accountId == "stock" }
        val reviewTag = "review-cycle-" + stock.id
        ui.onNodeWithTag("v2-reviews").performScrollToNode(hasTestTag(reviewTag))
        tap(reviewTag)
        inspect("position-review-note", "合成横屏复盘：取消后不得写入。", "保存复盘", "review-landscape-keyboard")
        page(4)
        select("account-stock")
        tap("configure-account")
        inspect("account-capital", "12345", "保存账户配置", "account-landscape-keyboard")
        ui.onNodeWithTag("v2-accounts").performScrollToIndex(0)
        tap("new-cashflow")
        field("cashflow-amount", "10")
        inspect("cashflow-note", "合成横屏流水：取消后不得入库。", "保存流水", "cashflow-landscape-keyboard")
    }
    private fun money(tag: String, value: String, list: String) {
        ui.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        visible(ui.onNodeWithTag(tag)).assertTextEquals(value)
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertTrue(layout.layoutInput.style.fontSize.value >= 14f)
        assertFalse(layout.didOverflowWidth)
        assertFalse(layout.didOverflowHeight)
        displayedMoney += mapOf("tag" to tag, "value" to value, "fontSp" to layout.layoutInput.style.fontSize.value,
            "overflowWidth" to layout.didOverflowWidth, "overflowHeight" to layout.didOverflowHeight,
            "argb" to String.format("#%08X", layout.layoutInput.style.color.toArgb()), "visible" to true)
    }
    private fun financialUi() {
        settled()
        page(4)
        select("account-stock")
        money("account-net-stock", "￥2487.65", "v2-accounts")
        money("account-fees-stock", "￥12.90", "v2-accounts")
        money("account-cost-stock", "￥5500.55", "v2-accounts")
        ui.onNodeWithTag("v2-accounts").performScrollToIndex(0)
        snap("stock-account")
        money("account-cash-stock", "待估算", "v2-accounts")
        select("account-futures")
        money("account-net-futures", "待估算", "v2-accounts")
        money("account-fees-futures", "待估算", "v2-accounts")
        money("account-margin-futures", "待估算", "v2-accounts")
        ui.onNodeWithTag("v2-accounts").performScrollToIndex(0)
        snap("unknown-futures-account")
        page(3)
        tap("statistics-period-month")
        select("statistics-market-all")
        waitFor { ui.onAllNodesWithTag("statistics-results-month-all").fetchSemanticsNodes().isNotEmpty() }
        money("period-realized-net", "￥2487.65", "v2-statistics")
        snap("known-statistics")
    }
    @Test fun realConfigurationBackupAndReopenPreserveFinancialFacts() {
        require(visualProfile in listOf("regular-light", "regular-dark", "narrow-light", "narrow-dark"))
        val firstName = "v210-consistency-" + transportProfile + "-api" + Build.VERSION.SDK_INT + ".json"
        if (args.getString("consistencyPhase") == "reopen") {
            val old = JsonParser.parseString(File(output(), firstName).readText()).asJsonObject
            val previousPid = old["pid"].asInt
            assertNotEquals(previousPid, android.os.Process.myPid())
            waitFor { model.state.value.ledger == facts() }
            assertEquals(old["ledger"], LedgerJson.element(facts()))
            assertEquals(old["wholeBackupAtExport"], wholeFacts())
            step("read saved strategy and risk from real UI") {
                tap("open-risk-strategy")
                riskTab("strategies")
                tapText("编辑")
                ui.onNodeWithTag("strategy-name").assertTextContains("合成复核策略已更正")
                tapText("取消", true)
                riskTab("risk")
                ui.onNodeWithTag("v2-risk-strategy").performScrollToNode(hasTestTag("risk-fund-stock"))
                visible(ui.onNodeWithTag("risk-fund-stock")).assertTextContains("10000")
                snap("reopened-risk")
                tap("contract-rules-entry")
                tapText("编辑，不改写历史")
                ui.onNodeWithTag("rule-name").assertTextContains("合成合约参数已更正")
                tapText("取消", true)
                back()
                back()
                page(1)
                tap("trade-mode-positions")
                val stock = facts().cycles.single { it.accountId == "stock" }
                ui.onNodeWithTag("v2-trades").performScrollToNode(hasTestTag("position-" + stock.id))
                ui.onNodeWithTag("position-quantity-" + stock.id, useUnmergedTree = true).assertIsDisplayed().assertTextEquals("500 股")
                snap("reopened-position")
            }
            step("read reopened financial UI") { financialUi() }
            expectedFinance()
            assertEquals(old["ledger"], LedgerJson.element(facts()))
            writeProof("v210-consistency-reopen-" + transportProfile + "-api" + Build.VERSION.SDK_INT + ".json",
                mapOf("api" to Build.VERSION.SDK_INT, "profile" to visualProfile, "previousPid" to previousPid,
                    "pid" to android.os.Process.myPid(), "allFactsIdentical" to true, "savedFieldsReadViaUi" to true,
                    "wholeBackupFactsIdentical" to true, "wholeBackupFacts" to wholeFacts(),
                    "ledger" to LedgerJson.element(facts()), "operations" to operations, "displayedMoney" to displayedMoney,
                    "screenshots" to pictures, "result" to "PASS"))
            return
        }
        step("create strategy through UI") {
            tap("open-risk-strategy")
            riskTab("strategies")
            tap("new-strategy")
            field("strategy-name", "合成复核策略")
            field("strategy-description", "隔离模拟器合成配置")
            saveAndWait("save-strategy") { facts().strategies.size == 1 }
            settled()
            back()
        }
        step("save five real-form executions") {
            execution(anchor.minusDays(2), "stock", "603999", "buy", "10", "1000", "1.00", "合成复核策略")
            execution(anchor.minusDays(1), "stock", "603999", "buy", "11", "1000", "1.10")
            execution(anchor, "stock", "603999", "sell", "12", "1500", "10.80")
            execution(anchor, "futures", "i2701", "buy-open", "800", "2", null)
            execution(anchor, "futures", "i2701", "sell-close", "810", "1", null)
            settled()
            expectedFinance()
            originalTrading = facts()
            extraEvidence["financialBeforeConfiguration"] = LedgerJson.element(requireNotNull(originalTrading))
            snap("saved-executions")
        }
        step("correct linked strategy and recreate editor") {
            tap("open-risk-strategy")
            riskTab("strategies")
            val id = facts().strategies.single().id
            tapText("编辑")
            field("strategy-name", "合成复核策略已更正")
            val note = "合成更正：历史成交仍保留原始数量、费用和稳定策略关联。".repeat(5).take(200)
            field("strategy-description", note)
            recreate()
            ui.onNodeWithTag("strategy-name").assertTextContains("合成复核策略已更正")
            ui.onNodeWithTag("strategy-description").assertTextContains(note)
            orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            ui.onNodeWithTag("strategy-name").assertTextContains("合成复核策略已更正")
            ui.onNodeWithTag("strategy-description").assertTextContains(note)
            field("strategy-description", note)
            saveControl("保存", inputTag = "strategy-description")
            snap("strategy-landscape-keyboard")
            orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            ui.onNodeWithTag("strategy-name").assertTextContains("合成复核策略已更正")
            ui.onNodeWithTag("strategy-description").assertTextContains(note)
            field("strategy-description", note)
            saveControl("保存", inputTag = "strategy-description")
            snap("strategy-keyboard")
            saveAndWait("save-strategy") { facts().strategies.singleOrNull()?.name == "合成复核策略已更正" }
            assertEquals(id, facts().strategies.single().id)
            assertEquals(id, facts().executions.minBy { it.ordinal }.strategyId)
            unchanged()
            settled()
        }
        step("save independent stock risk through keyboard UI") {
            riskTab("risk")
            field("risk-fund-stock", "10000")
            field("risk-limit-stock", "500")
            saveControl("保存股票风险配置", "save-risk-stock")
            snap("risk-keyboard")
            saveAndWait("save-risk-stock") { facts().risks.single { it.id == "stock" }.riskFund == "10000" }
            assertNull(facts().risks.single { it.id == "futures" }.riskFund)
            unchanged()
            hideKeyboard()
        }
        step("save verified future rule without repricing old unknown fees") { createRule() }
        lateinit var exported: Pair<String, LedgerSnapshot>
        step("real DocumentsUI ZIP export preview cancel and replace restore") { exported = backupRoundTrip() }
        step("inspect and cancel shared editors with landscape keyboard") {
            back()
            inspectAndCancelSharedEditors(exported.second, requireNotNull(wholeBackupAtExport))
        }
        step("read restored financial UI") {
            financialUi()
            assertEquals(exported.second, facts())
            assertEquals(wholeBackupAtExport, wholeFacts())
        }
        val conf = app.resources.configuration
        writeProof(firstName, mapOf("api" to Build.VERSION.SDK_INT, "profile" to visualProfile,
            "transportProfile" to transportProfile, "syntheticOnly" to true, "dateAnchor" to anchor.toString(),
            "activity" to com.tradereview.app.MainActivity::class.java.name, "pid" to android.os.Process.myPid(),
            "screenWidthDp" to conf.screenWidthDp, "fontScale" to conf.fontScale,
            "night" to (conf.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES),
            "allExecutionsCreatedViaRealForm" to true, "configurationSavedViaUi" to true, "systemDocumentsUiUsed" to true,
            "zipExportedAndReadbackVerified" to true, "previewCancelPreservedFacts" to true, "replaceRestoreMatchedExport" to true,
            "cancelledSharedEditorsPreservedFacts" to true,
            "backupFileName" to exported.first, "financialBeforeConfiguration" to LedgerJson.element(requireNotNull(originalTrading)),
            "wholeBackupAtExport" to requireNotNull(wholeBackupAtExport), "wholeBackupFactsIdentical" to true,
            "ledger" to LedgerJson.element(facts()), "operations" to operations, "controls" to observations,
            "displayedMoney" to displayedMoney, "screenshots" to pictures, "result" to "PASS"))
    }
}
