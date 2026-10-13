package com.tradereview.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.LegacyCompatibilityActivity
import org.junit.Rule
import org.junit.Before
import kotlinx.coroutines.runBlocking
import com.tradereview.app.TradeReviewApplication
import com.tradereview.app.data.AppSettings
import org.junit.Test
import org.junit.runner.RunWith

/** Original UI assertions retained on the Debug compatibility host; V200MainUiTest covers real Main. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<LegacyCompatibilityActivity>()

    @Before fun resetData() = runBlocking {
        require(android.os.Build.HARDWARE in listOf("ranchu","goldfish")){"Disposable emulator fixtures only"}
        val application=compose.activity.application as TradeReviewApplication
        application.deviceExperience.initialize(true);application.deviceExperience.finishOnboarding()
        val dao = application.database.trades()
        dao.clearTrades(); dao.clearDrafts(); dao.saveSettings(AppSettings())
    }
    @Test fun bottomNavigationShowsEachPage() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录交易，复盘执行").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记录交易，复盘执行").assertIsDisplayed()
        compose.onNodeWithTag("main-page-1").performClick()
        compose.onNodeWithText("交易筛选").assertIsDisplayed()
        compose.onNodeWithTag("main-page-2").performClick()
        compose.onNodeWithText("每日交易日志").assertIsDisplayed()
        compose.onNodeWithTag("main-page-3").performClick()
        compose.onNodeWithText("分析范围").assertIsDisplayed()
        compose.onNodeWithTag("main-page-0").performClick()
        compose.onNodeWithText("记录交易，复盘执行").assertIsDisplayed()
    }

    @Test fun userCanCreateEditAndDeleteATrade() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录交易，复盘执行").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("新增交易").performClick()
        compose.onNodeWithText("净盈亏金额（CNY）").performScrollTo().performTextInput("123.45")
        compose.onNodeWithText("保存交易").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("快速记录交易").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("main-page-1").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("沪深300").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("沪深300").assertIsDisplayed().performClick()
        compose.onNodeWithText("亏损").performScrollTo().performClick()
        compose.onNodeWithText("保存交易").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑交易与复盘").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("-123.45").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("-123.45").assertIsDisplayed()
        compose.onNodeWithText("沪深300").performClick()
        compose.onNodeWithContentDescription("删除交易").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("沪深300").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("沪深300").assertDoesNotExist()
    }
}
