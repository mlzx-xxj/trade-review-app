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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun bottomNavigationShowsEachPage() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("让每一笔交易都有收获").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("让每一笔交易都有收获").assertIsDisplayed()
        compose.onAllNodesWithText("交易记录").onFirst().performClick()
        compose.onNodeWithText("交易筛选").assertIsDisplayed()
        compose.onAllNodesWithText("勾选复盘").onFirst().performClick()
        compose.onNodeWithText("每日交易日志").assertIsDisplayed()
        compose.onAllNodesWithText("统计分析").onFirst().performClick()
        compose.onNodeWithText("分析范围").assertIsDisplayed()
        compose.onAllNodesWithText("首页").onFirst().performClick()
        compose.onNodeWithText("让每一笔交易都有收获").assertIsDisplayed()
    }

    @Test fun userCanCreateEditAndDeleteATrade() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("让每一笔交易都有收获").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("新增交易").performClick()
        compose.onNodeWithText("品种（可自定义）").performScrollTo().performTextClearance()
        compose.onNodeWithText("品种（可自定义）").performTextInput("MVP测试品种")
        compose.onNodeWithText("净盈亏金额（CNY）").performScrollTo().performTextInput("123.45")
        compose.onNodeWithText("保存交易").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录一笔交易").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("交易记录").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("MVP测试品种").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("MVP测试品种").assertIsDisplayed().performClick()
        compose.onNodeWithText("亏损").performScrollTo().performClick()
        compose.onNodeWithText("保存交易").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑交易与复盘").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("-123.45").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("-123.45").assertIsDisplayed()
        compose.onNodeWithText("MVP测试品种").performClick()
        compose.onNodeWithContentDescription("删除交易").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("MVP测试品种").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("MVP测试品种").assertDoesNotExist()
    }
}
