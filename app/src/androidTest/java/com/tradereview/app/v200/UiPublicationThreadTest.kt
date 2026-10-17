package com.tradereview.app.v200

import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.GsonBuilder
import com.tradereview.app.backup.RestoreMode
import com.tradereview.app.data.CatalogSeeds
import com.tradereview.app.data.TradeDatabase
import com.tradereview.app.evidence.AttachmentStore
import com.tradereview.app.release.BackupDocumentProvider
import com.tradereview.app.stability.V08Metrics
import com.tradereview.app.ui.BackupViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Deterministic emitter-thread regression; the original MainActivity UI journeys still run.
 * Unconfined collectors expose the publishing thread rather than silently hopping to main.
 * All databases, documents and values here are disposable synthetic fixtures.
 */
@RunWith(AndroidJUnit4::class)
class UiPublicationThreadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TradeDatabase
    private lateinit var model: V200ViewModel
    private lateinit var backup: BackupViewModel
    private lateinit var store: ViewModelStore
    private lateinit var directory: File
    private val recorder = EmitterRecorder()

    @Before fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, TradeDatabase::class.java)
            .addCallback(CatalogSeeds.callback).build()
        directory = File(context.cacheDir, "ui-publication-${java.util.UUID.randomUUID()}")
        db.withTransaction {
            LedgerRepository(db).write(LedgerSnapshot(
                accounts = listOf(LedgerAccount("stock", "stock", "A股账户"), LedgerAccount("futures", "futures", "国内期货账户")),
                risks = listOf(MarketRisk("stock"), MarketRisk("futures"))
            ), true)
        }
        withContext(Dispatchers.Main.immediate) {
            store = ViewModelStore()
            val provider = ViewModelProvider(store, viewModelFactory {
                initializer { V200ViewModel(db, SavedStateHandle()) }
                initializer { BackupViewModel(db, context.contentResolver, AttachmentStore(directory)) }
            })
            model = provider[V200ViewModel::class.java]
            backup = provider[BackupViewModel::class.java]
        }
    }

    @After fun close() = runBlocking {
        recorder.close()
        withContext(Dispatchers.Main.immediate) { store.clear() }
        db.close()
        directory.deleteRecursively()
        Unit
    }

    private suspend fun ledgerIdle() = withTimeout(30000) { while (model.busy.value) delay(10) }
    private suspend fun backupIdle() = withTimeout(30000) { while (backup.busy.value) delay(10) }
    private fun opening() = AutomaticEntry.today().minusDays(1).toEpochDay().let { day ->
        LedgerExecution("synthetic_main_thread", "stock", "600000", "合成股票", day, 600, day, 0,
            "buy", "open", "10", "1000", "1", second = 1)
    }

    @Test fun emitterProbeDetectsWorkerAndMainWithoutMaskingThePublisher() = runBlocking {
        val sample = MutableStateFlow(0)
        recorder.watch("calibration", sample) { it.toString() }
        withContext(Dispatchers.IO) { sample.value = 1 }
        withContext(Dispatchers.Main.immediate) { sample.value = 2 }
        val events = recorder.rows()
        assertTrue(events.single { it.value == "0" }.main)
        assertFalse("The observer must detect a real IO publication", events.single { it.value == "1" }.main)
        assertTrue(events.single { it.value == "2" }.main)
        evidence("calibration", mapOf("workerEmissionDetected" to true))
    }

    @Test fun executionEditDeleteAndFailurePublishOnMainWithoutChangingFactsBeforeConfirmation() = runBlocking {
        recorder.watch("pending", model.pending) { it?.let { change -> if (change.deleting) "delete" else "edit" } ?: "none" }
        recorder.watch("message", model.message) { if (it == null) "empty" else "present" }
        recorder.watch("problem", model.problem) { if (it == null) "empty" else "present" }
        recorder.watch("busy", model.busy) { it.toString() }
        withContext(Dispatchers.Main.immediate) { model.execution(opening()) }
        ledgerIdle()
        val original = model.repository.read().executions.single()
        assertEquals("10", original.price)
        assertEquals("1", original.fees)

        withContext(Dispatchers.Main.immediate) { model.execution(original.copy(price = "11", fees = "1.10")) }
        ledgerIdle()
        assertNotNull(model.pending.value)
        assertEquals(listOf(original), model.repository.read().executions)
        withContext(Dispatchers.Main.immediate) { model.confirmChange() }
        ledgerIdle()
        val edited = model.repository.read().executions.single()
        assertEquals("11", edited.price)
        assertEquals("1.10", edited.fees)
        assertEquals(original.ordinal, edited.ordinal)
        assertNull(model.pending.value)

        withContext(Dispatchers.Main.immediate) { model.deleteExecution(edited) }
        ledgerIdle()
        assertTrue(requireNotNull(model.pending.value).deleting)
        assertEquals(listOf(edited), model.repository.read().executions)
        withContext(Dispatchers.Main.immediate) { model.confirmChange() }
        ledgerIdle()
        val after = model.repository.read()
        assertTrue(after.executions.isEmpty())
        assertTrue(after.lots.isEmpty())
        assertTrue(after.cycles.isEmpty())
        assertEquals(2, after.audits.size)
        assertEquals(setOf(original.id), after.audits.map { it.executionId }.toSet())
        withContext(Dispatchers.Main.immediate) { model.execution(opening().copy(price = "-1")) }
        ledgerIdle()
        assertNotNull(model.problem.value)
        assertEquals(after, model.repository.read())
        assertEquals(listOf("none", "edit", "none", "delete", "none"), recorder.rows().filter { it.flow == "pending" }.map { it.value })
        recorder.assertAllMain()
        evidence("ledger", mapOf("confirmedEditAndDelete" to true, "previewKeptFacts" to true, "failedWriteKeptFacts" to true, "auditCount" to 2))
    }

    @Test fun jsonZipPreviewRefreshRestoreCancelAndErrorsPublishOnMain() = runBlocking {
        recorder.watch("preview", backup.preview) { if (it == null) "empty" else "present" }
        recorder.watch("message", backup.message) { if (it == null) "empty" else "present" }
        recorder.watch("busy", backup.busy) { it.toString() }
        withContext(Dispatchers.Main.immediate) { model.execution(opening()) }
        ledgerIdle()
        val original = model.repository.read()
        for (kind in listOf("json", "zip")) {
            val uri = BackupDocumentProvider.uri()
            withContext(Dispatchers.Main.immediate) { if (kind == "json") backup.exportJson(uri) else backup.exportZip(uri) }
            backupIdle()
            assertTrue(requireNotNull(backup.message.value).contains("校验"))
            assertNull(backup.preview.value)
            withContext(Dispatchers.Main.immediate) { backup.readBackup(uri) }
            backupIdle()
            assertNotNull(backup.preview.value)
            assertEquals(original, model.repository.read())
            withContext(Dispatchers.Main.immediate) { backup.refreshPreview() }
            backupIdle()
            assertNotNull(backup.preview.value)
            assertEquals(original, model.repository.read())
            withContext(Dispatchers.Main.immediate) { backup.confirmRestore(RestoreMode.REPLACE) }
            backupIdle()
            assertNull(backup.preview.value)
            assertEquals("覆盖恢复完成", backup.message.value)
            assertEquals(original, model.repository.read())
            withContext(Dispatchers.Main.immediate) { backup.readBackup(uri) }
            backupIdle()
            assertNotNull(backup.preview.value)
            withContext(Dispatchers.Main.immediate) { backup.cancelPreview() }
            assertNull(backup.preview.value)
            assertEquals(original, model.repository.read())
        }
        for (ledgerCsv in listOf(false, true)) {
            withContext(Dispatchers.Main.immediate) {
                if (ledgerCsv) backup.exportLedgerCsv(BackupDocumentProvider.uri()) else backup.exportCsv(BackupDocumentProvider.uri())
            }
            backupIdle()
            assertTrue(requireNotNull(backup.message.value).contains("CSV"))
        }
        withContext(Dispatchers.Main.immediate) { backup.exportZip(BackupDocumentProvider.uri("write-fail")) }
        backupIdle()
        assertTrue(requireNotNull(backup.message.value).contains("失败"))
        assertEquals(original, model.repository.read())
        assertTrue(recorder.rows().count { it.flow == "preview" && it.value == "present" } >= 4)
        recorder.assertAllMain()
        evidence("backup", mapOf("jsonAndZipReadback" to true, "previewRefreshCancel" to true, "restoreKeptFacts" to true, "writeFailureKeptFacts" to true, "csvBothKinds" to true))
    }

    private fun evidence(name: String, checks: Map<String, Any>) {
        V08Metrics.write("ui-publication-$name-api${Build.VERSION.SDK_INT}.json", GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf("api" to Build.VERSION.SDK_INT, "syntheticOnly" to true, "result" to "PASS", "checks" to checks, "emissions" to recorder.rows())))
    }

    private data class Emission(val flow: String, val value: String, val main: Boolean, val thread: String)
    private class EmitterRecorder {
        private val values = Collections.synchronizedList(mutableListOf<Emission>())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        suspend fun <T> watch(name: String, flow: StateFlow<T>, label: (T) -> String) {
            withContext(Dispatchers.Main.immediate) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    flow.collect { values += Emission(name, label(it), Looper.myLooper() == Looper.getMainLooper(), Thread.currentThread().name) }
                }
            }
        }
        fun rows(): List<Emission> = synchronized(values) { values.toList() }
        fun assertAllMain() { assertTrue("UI state published from a worker: ${rows()}", rows().all { it.main }) }
        fun close() { scope.cancel() }
    }
}
