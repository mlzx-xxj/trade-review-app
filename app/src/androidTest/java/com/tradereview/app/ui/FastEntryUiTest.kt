package com.tradereview.app.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TradeDatabase
    private lateinit var model: TradeViewModel
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        model = TradeViewModel(db, SavedStateHandle())
        val backup = BackupViewModel(db, context.contentResolver)
        compose.setContent { TradeReviewTheme { TradeReviewApp(model, backup) } }
        compose.waitUntil(10000) { !model.state.value.loading }
    }
    @After fun close() { db.close() }
    private fun entry() { compose.onNodeWithContentDescription("新增交易").performClick() }
    private fun saved() { compose.waitUntil(10000) { !model.busy.value && compose.onAllNodesWithText("快速记录交易").fetchSemanticsNodes().isEmpty() } }

    @Test fun emptyOrZeroAmountRejectedThenExplicitFlatSavesWithoutKeyboard() {
        entry(); compose.onNodeWithText("保存交易").performClick()
        compose.onNodeWithTag("entry-error").assertIsDisplayed()
        compose.onNodeWithTag("entry-amount").performScrollTo().performTextInput("0")
        compose.onNodeWithText("保存交易").performClick()
        compose.onNodeWithText("金额必须大于 0；持平请明确选择“持平”").assertIsDisplayed()
        compose.onNodeWithText("持平").performScrollTo().performClick()
        compose.onNodeWithText("保存交易").performClick(); saved()
        runBlocking { assertEquals("0.00", db.trades().allTrades().single().netPnl) }
    }
    @Test fun closingAndReopeningRestoresDraftAndSaveContinueClearsAmount() {
        entry(); compose.onNodeWithTag("entry-amount").performScrollTo().performTextInput("123.45")
        compose.waitUntil(10000) { model.state.value.drafts.any { it.amount == "123.45" } }
        compose.onNodeWithContentDescription("返回").performClick()
        entry(); compose.onNodeWithText("已恢复未保存草稿").assertIsDisplayed()
        compose.onNodeWithTag("entry-amount").performScrollTo().assertTextContains("123.45")
        compose.onNodeWithText("保存并继续").performClick()
        compose.waitUntil(10000) { !model.busy.value && model.state.value.trades.size == 1 }
        compose.onNodeWithTag("entry-amount").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("保存交易").performClick(); compose.onNodeWithTag("entry-error").assertIsDisplayed()
        compose.onNodeWithText("亏损").performScrollTo().performClick()
        compose.onNodeWithTag("entry-amount").performTextInput("5")
        compose.onNodeWithText("保存交易").performClick(); saved()
        runBlocking { assertEquals(2, db.trades().allTrades().size) }
    }
    @Test fun copyPreviousClearsAmountAndCompletionWithVisibleFeedback() {
        runBlocking { db.trades().insert(Trade(date = 20000, netPnl = "99.00", reviewed = true)) }
        compose.waitUntil(10000) { model.state.value.trades.size == 1 }
        entry(); compose.onNodeWithText("复制上一笔交易").performScrollTo().performClick()
        compose.onNodeWithText("已复制上一笔，盈亏及复盘状态已清空").assertIsDisplayed()
        compose.onNodeWithText("保存交易").performClick()
        compose.onNodeWithText("请选择盈利、亏损或持平").assertIsDisplayed()
        compose.onNodeWithText("持平").performScrollTo().performClick(); compose.onNodeWithText("保存交易").performClick(); saved()
        runBlocking { val trades = db.trades().allTrades(); assertEquals(2, trades.size); assertFalse(trades.maxBy { it.id }.reviewed) }
    }
    @Test fun manageNewTagRenameHideAndFavoriteSymbolThroughSettings() {
        compose.onNodeWithContentDescription("设置与备份").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("管理自定义标签"))
        compose.onNodeWithText("管理自定义标签").performClick()
        compose.onNodeWithText("新增标签").performClick()
        compose.onNodeWithTag("tag-name-field").performTextInput("快速策略")
        compose.onNodeWithText("保存标签").performClick()
        compose.waitUntil(10000) { !model.busy.value && model.state.value.tags.any { it.name == "快速策略" } }
        val id = model.state.value.tags.first { it.name == "快速策略" }.id
        compose.onNodeWithTag("tag-list").performScrollToNode(hasTestTag("rename-$id"))
        compose.onNodeWithTag("rename-$id").assertIsEnabled().performClick()
        compose.onNodeWithText("重命名标签").assertIsDisplayed()
        compose.onNodeWithTag("tag-name-field").performTextClearance(); compose.onNodeWithTag("tag-name-field").performTextInput("改名策略")
        compose.onNodeWithText("保存标签").performClick()
        compose.waitUntil(10000) { !model.busy.value && model.state.value.tags.any { it.id == id && it.name == "改名策略" } }
        compose.onNodeWithTag("hide-$id").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000) { !model.busy.value && model.state.value.tags.any { it.id == id && it.hidden } }
        compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("管理常用品种"))
        compose.onNodeWithText("管理常用品种").performClick()
        compose.onNodeWithTag("favorite-symbol-preset_stock_0").performScrollTo().performClick()
        compose.waitUntil(10000) { !model.busy.value && model.state.value.instruments.any { it.id == "preset_stock_0" && !it.favorite } }
        compose.onNodeWithText("搜索名称或代码").performTextInput("NVDA")
        compose.onNodeWithText("NVDA · NVDA").assertIsDisplayed()
    }
}
