package com.tradereview.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.backup.*
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TradeDatabase
    private lateinit var model: BackupViewModel
    private lateinit var file: File
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        model = BackupViewModel(db, context.contentResolver)
        file = File(context.cacheDir, "backup-ui-test.json")
    }
    @After fun close() { db.close(); file.delete() }

    @Test fun previewAndSecondConfirmationAreRequiredBeforeRestore() = runBlocking {
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 7, date = 20000, symbol = "确认测试")), emptyList())
        file.writeText(BackupCodec.encode(data))
        compose.setContent { TradeReviewTheme { SettingsPage(model, false) } }
        compose.runOnIdle { model.readBackup(Uri.fromFile(file)) }
        compose.waitUntil(10000) { model.preview.value != null && !model.busy.value }
        assertTrue(db.trades().allTrades().isEmpty())
        compose.onNodeWithText("继续确认恢复").performScrollTo().performClick()
        compose.onNodeWithText("确认去重合并？").assertIsDisplayed()
        assertTrue(db.trades().allTrades().isEmpty())
        compose.onNodeWithText("返回预览").performClick()
        assertTrue(db.trades().allTrades().isEmpty())
        compose.onNodeWithText("继续确认恢复").performScrollTo().performClick()
        compose.onNodeWithText("确认恢复").performClick()
        compose.waitUntil(10000) { model.preview.value == null && !model.busy.value }
        assertEquals("确认测试", db.trades().allTrades().single().symbol)
    }
    @Test fun contentResolverJsonExportImportAndCsvWorkOffline() = runBlocking {
        val source = Trade(date = 20000, symbol = "中文,品种", netPnl = "-1.23", mistakes = listOf("chase"))
        val id = db.trades().insert(source)
        db.trades().saveReview(DailyReview(date = 20000, planSaved = true, reviewSaved = true))
        compose.setContent { TradeReviewTheme { SettingsPage(model, false) } }
        compose.runOnIdle { model.exportJson(Uri.fromFile(file)) }
        compose.waitUntil(10000) { !model.busy.value }
        val decoded = BackupCodec.read(file.inputStream())
        assertEquals(source.copy(id = id), decoded.trades.single())
        assertTrue(decoded.dailyReviews.single().reviewSaved)
        compose.runOnIdle { model.exportCsv(Uri.fromFile(file)) }
        compose.waitUntil(10000) { !model.busy.value }
        assertTrue(file.readText().contains("\"中文,品种\""))
    }
    @Test fun invalidFileShowsVisibleErrorWithoutChangingExistingData() = runBlocking {
        val trade = Trade(date = 20000, symbol = "保留原记录")
        val id = db.trades().insert(trade)
        file.writeText("{}")
        compose.setContent { TradeReviewTheme { SettingsPage(model, false) } }
        compose.onNodeWithText("应用信息").performScrollTo()
        compose.runOnIdle { model.readBackup(Uri.fromFile(file)) }
        compose.waitUntil(10000) { model.message.value != null && !model.busy.value }
        compose.onNodeWithText(requireNotNull(model.message.value)).assertIsDisplayed()
        assertNull(model.preview.value)
        assertEquals(listOf(trade.copy(id = id)), db.trades().allTrades())
    }
}
