package com.tradereview.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
}
