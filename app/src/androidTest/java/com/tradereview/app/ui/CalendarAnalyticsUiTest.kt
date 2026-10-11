package com.tradereview.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CalendarAnalyticsUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TradeDatabase
    private lateinit var model: TradeViewModel
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.now()
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        runBlocking {
            db.trades().saveTag(ManagedTag("strategy", "custom_v04", "历史策略", hidden = true))
            listOf(Trade(date = today.toEpochDay(), netPnl = "0.10", reviewed = true, mistakes = listOf("chase", "oversize")),
                Trade(date = today.toEpochDay(), netPnl = "0.20", reviewed = true),
                Trade(date = today.toEpochDay(), currency = "USD", market = "forex", symbol = "EUR/USD", netPnl = "99.00"),
                Trade(date = today.toEpochDay(), currency = "USDT", market = "crypto", symbol = "BTC/USDT", netPnl = "-5.00"),
                Trade(date = today.minusDays(1).toEpochDay(), market = "futures", symbol = "螺纹钢", direction = "short", strategy = "custom_v04", netPnl = "-10.00", mistakes = listOf("no_stop")),
                Trade(date = today.minusDays(2).toEpochDay(), netPnl = "0.00")).forEach { db.trades().insert(it) }
            db.trades().saveReview(DailyReview(date = today.toEpochDay(), planSaved = true, reviewSaved = true, rating = 4, preparations = listOf("risk"), discipline = listOf("respect_stop"), mistakes = listOf("chase"), lessons = listOf("stop")))
            db.trades().saveReview(DailyReview(date = today.plusDays(1).toEpochDay(), planSaved = true, preparations = listOf("risk")))
        }
        model = TradeViewModel(db, SavedStateHandle())
        val backup = BackupViewModel(db, context.contentResolver)
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            TradeReviewTheme(darkTheme = state.settings.theme == "dark") { TradeReviewApp(model, backup) }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("让每一笔交易都有收获").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun close() { db.close() }
    private fun nav(title: String) { compose.onAllNodesWithText(title).onFirst().performClick() }
    private fun calendar() { compose.onNodeWithContentDescription("打开交易日历").performClick() }
    private fun openDay(day: LocalDate) {
        val month = YearMonth.from(day)
        if (month > YearMonth.from(today)) compose.onNodeWithTag("calendar-next").performClick()
        if (month < YearMonth.from(today)) compose.onNodeWithTag("calendar-previous").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-day-${day.toEpochDay()}"))
        compose.onNodeWithTag("calendar-day-${day.toEpochDay()}").performScrollTo().performClick()
    }
    private fun outcomeFilter() {
        compose.onNodeWithTag("open-filters").performClick()
        compose.onNodeWithTag("filter-options").performScrollToNode(hasText("股票")); compose.onNodeWithText("股票").performClick()
        compose.onNodeWithTag("filter-options").performScrollToNode(hasText("盈利")); compose.onNodeWithText("盈利").performClick()
        compose.onNodeWithTag("apply-filters").performClick()
        compose.waitUntil(10000) { model.analytics.value.filtered.size == 2 }
    }
    @Test fun monthNavigationTodayAndDailyDetailsKeepCurrenciesSeparateAndEditTrade() {
        calendar(); compose.onNodeWithTag("calendar-next").performClick()
        val next = YearMonth.from(today).plusMonths(1)
        compose.onNodeWithTag("calendar-month").assertTextEquals("${next.year}年${next.monthValue}月")
        compose.onNodeWithTag("calendar-previous").performClick(); compose.onNodeWithTag("calendar-today").performClick()
        openDay(today)
        compose.onNodeWithTag("day-trade-count").assertTextEquals("4 笔交易")
        compose.onNodeWithTag("day-pnl-CNY").assertTextEquals("CNY：+0.30 · 2 笔")
        compose.onNodeWithTag("day-pnl-USD").assertTextEquals("USD：+99.00 · 1 笔")
        compose.onNodeWithTag("day-pnl-USDT").assertTextEquals("USDT：-5.00 · 1 笔")
        compose.onNodeWithTag("day-details-list").performScrollToNode(hasText("+0.20")); compose.onNodeWithText("+0.20").performClick()
        compose.onNodeWithText("编辑交易与复盘").assertIsDisplayed()
    }
    @Test fun planOnlyDayAndFlatDayAreNotNoDataOrFakePnl() {
        calendar(); openDay(today.plusDays(1))
        compose.onNodeWithTag("day-trade-count").assertTextEquals("0 笔交易")
        compose.onNodeWithTag("day-pnl-CNY").assertDoesNotExist()
        compose.onNodeWithText("判断：观望 · 最大仓位 20%").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithTag("calendar-today").performScrollTo().performClick()
        openDay(today.minusDays(2))
        compose.onNodeWithTag("day-pnl-CNY").assertTextEquals("CNY：0.00 · 1 笔")
    }
    @Test fun calendarRestoresSelectedMonthAndScrollAfterDailyDetail() {
        calendar(); compose.onNodeWithTag("calendar-previous").performClick()
        val month = YearMonth.from(today).minusMonths(1); val day = month.atDay(1).toEpochDay()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-day-$day"))
        compose.onNodeWithTag("calendar-day-$day").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回主页面").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-month"))
        compose.onNodeWithTag("calendar-month").assertTextEquals("${month.year}年${month.monthValue}月")
        compose.onNodeWithTag("calendar-today").performClick()
    }
    @Test fun editingJournalFromCalendarUsesSelectedDayInsteadOfPreviouslyViewedDay() {
        nav("勾选复盘"); nav("首页"); calendar(); openDay(today.plusDays(1))
        compose.onNodeWithTag("day-details-list").performScrollToNode(hasText("编辑盘前计划"))
        compose.onNodeWithText("编辑盘前计划").performClick()
        compose.onNodeWithText("日期：${dayLabel(today.plusDays(1).toEpochDay())}").assertIsDisplayed()
    }
    @Test fun combinationFiltersAreSharedByListAndStatisticsAndPresetRestoresThem() {
        nav("交易记录"); outcomeFilter()
        compose.onNodeWithTag("filtered-count").assertTextEquals("匹配 2 笔 · CNY")
        compose.onNodeWithTag("save-filter-preset").performClick()
        compose.onNodeWithTag("filter-preset-name").performTextInput("股票盈利")
        compose.onNodeWithText("保存方案").performClick()
        compose.waitUntil(10000) { model.state.value.savedFilters.size == 1 && !model.busy.value }
        nav("统计分析")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-trade-count"))
        compose.onNodeWithTag("statistics-trade-count").assertTextEquals("总交易 2 笔")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("clear-filters")); compose.onNodeWithTag("clear-filters").performClick()
        compose.waitUntil(10000) { model.analytics.value.filtered.size == 4 }
        val id = model.state.value.savedFilters.single().id
        compose.onNodeWithTag("preset-$id").performScrollTo().performClick()
        compose.waitUntil(10000) { model.analytics.value.filtered.size == 2 }
        nav("交易记录"); compose.onNodeWithTag("filtered-count").assertTextEquals("匹配 2 笔 · CNY")
    }
    @Test fun hiddenHistoricalTagCanBeSelectedAndEmptyFilterHasFriendlyState() {
        nav("交易记录"); compose.onNodeWithTag("open-filters").performClick()
        compose.onNodeWithTag("filter-options").performScrollToNode(hasText("历史策略（已隐藏）"))
        compose.onNodeWithText("历史策略（已隐藏）").performClick(); compose.onNodeWithTag("apply-filters").performClick()
        compose.waitUntil(10000) { model.analytics.value.filtered.size == 1 }
        compose.onNodeWithTag("filtered-count").assertTextEquals("匹配 1 笔 · CNY")
        compose.runOnIdle { model.updateFilter(model.filter.value.copy(symbols = listOf("没有记录的品种"))) }
        compose.waitUntil(10000) { model.analytics.value.filtered.isEmpty() }
        compose.onNodeWithTag("trades-list").performScrollToNode(hasText("暂无符合条件的记录"))
        compose.onNodeWithText("暂无符合条件的记录").assertIsDisplayed()
    }
    @Test fun chartsExposeExactTapValuesAndPalettePersists() {
        nav("统计分析")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("daily-bar-${today.toEpochDay()}"))
        compose.onNodeWithTag("daily-bar-${today.toEpochDay()}").performScrollTo().performClick()
        compose.onNodeWithTag("daily-chart-value").assertTextEquals("${dayLabel(today.toEpochDay())}：+0.30 CNY · 2 笔")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("interactive-pnl-curve"))
        compose.onNodeWithTag("interactive-pnl-curve").performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x * 1.9f, center.y)) }
        compose.onNodeWithTag("curve-chart-value").assertTextEquals("${dayLabel(today.toEpochDay())}：累计 -9.70 CNY")
        compose.onNodeWithContentDescription("设置与备份").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("红盈绿亏")); compose.onNodeWithText("红盈绿亏").performClick()
        compose.waitUntil(10000) { model.state.value.settings.pnlPalette == "red_profit" && !model.busy.value }
        runBlocking { assertEquals("red_profit", db.trades().settings()!!.pnlPalette) }
    }
    @Test fun captureMajorScreensWithClearlySyntheticFixtures() {
        capture("01-home")
        calendar(); compose.onNodeWithText("盈亏热力图").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-day-${today.toEpochDay()}"))
        compose.onNodeWithTag("calendar-day-${today.toEpochDay()}").performScrollTo(); capture("02-calendar-heatmap")
        compose.onNodeWithTag("calendar-day-${today.toEpochDay()}").performClick(); capture("03-daily-details")
        nav("交易记录"); compose.onNodeWithTag("open-filters").performClick(); capture("04-combined-filters")
        compose.onNodeWithContentDescription("关闭筛选").performClick(); nav("统计分析")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-trade-count")); capture("05-statistics")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("interactive-pnl-curve")); capture("06-pnl-chart")
        compose.onNodeWithContentDescription("设置与备份").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("深色")); compose.onNodeWithText("深色").performClick()
        compose.waitUntil(10000) { model.state.value.settings.theme == "dark" && !model.busy.value }; capture("07-dark-settings")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // AGP copies this supported output directory before uninstalling test APKs.
        val output = requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")) { "AGP additional test output is not configured" }
        val directory = File(output, "v04-screenshots").apply { mkdirs() }
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
