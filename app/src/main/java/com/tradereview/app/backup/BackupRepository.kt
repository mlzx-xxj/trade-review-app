package com.tradereview.app.backup

import androidx.room.withTransaction
import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import com.tradereview.app.data.*
import java.time.Instant

enum class RestoreMode { MERGE, REPLACE }
data class DatabaseSnapshot(val trades: List<Trade>, val reviews: List<DailyReview>, val tags: List<ManagedTag>,
    val instruments: List<Instrument>, val settings: AppSettings, val drafts: List<EntryDraft>, val savedFilters: List<SavedFilter> = emptyList())
data class MergeSummary(val newTrades: Int, val repeatedTrades: Int, val conflictingTrades: Int,
    val newDays: Int, val addedPlans: Int, val addedReviews: Int, val conflictingPlans: Int, val conflictingReviews: Int)
data class RestorePreview(val backup: BackupData, val before: DatabaseSnapshot, val summary: MergeSummary)

class BackupRepository(private val database: TradeDatabase) {
    private val dao get() = database.trades()
    private suspend fun snapshot() = DatabaseSnapshot(dao.allTrades(), dao.allReviews(), dao.allTags(), dao.allInstruments(), dao.settings() ?: AppSettings(), dao.allDrafts(), dao.allFilters())

    suspend fun export(): BackupData = database.withTransaction {
        val state = snapshot()
        BackupData(Instant.now().toString(), state.trades, state.reviews, state.tags, state.instruments, state.settings, state.drafts, savedFilters = state.savedFilters)
    }

    suspend fun preview(data: BackupData): RestorePreview {
        BackupCodec.validate(data)
        return database.withTransaction {
            val before = snapshot()
            val existingTrades = before.trades.associateBy { it.recordKey }
            val existingReviews = before.reviews.associateBy { it.date }
            RestorePreview(data, before, MergeSummary(
                data.trades.count { it.recordKey !in existingTrades },
                data.trades.count { t -> existingTrades[t.recordKey]?.let { it.copy(id = t.id) == t } == true },
                data.trades.count { t -> existingTrades[t.recordKey]?.let { it.copy(id = t.id) != t } == true },
                data.dailyReviews.count { it.date !in existingReviews },
                data.dailyReviews.count { it.planSaved && existingReviews[it.date]?.planSaved != true },
                data.dailyReviews.count { it.reviewSaved && existingReviews[it.date]?.reviewSaved != true },
                data.dailyReviews.count { r -> r.planSaved && existingReviews[r.date]?.let { it.planSaved && !samePlan(it, r) } == true },
                data.dailyReviews.count { r -> r.reviewSaved && existingReviews[r.date]?.let { it.reviewSaved && !sameReview(it, r) } == true },
            ))
        }
    }

    /** All deletes/inserts/updates commit together; cancellation and SQLite failures roll back. */
    suspend fun restore(preview: RestorePreview, mode: RestoreMode) {
        BackupCodec.validate(preview.backup)
        database.withTransaction {
            if (snapshot() != preview.before) throw BackupException("本地数据在预览后已变化，请重新预览再确认")
            if (mode == RestoreMode.REPLACE) {
                dao.clearTrades()
                dao.clearReviews()
                dao.clearDrafts(); dao.clearFilters(); dao.clearTags(); dao.clearInstruments(); dao.clearSettings()
                preview.backup.tags.forEach { dao.saveTag(it) }
                preview.backup.instruments.forEach { dao.saveInstrument(it) }
                dao.saveSettings(preview.backup.settings)
                preview.backup.trades.forEach { dao.insert(it) }
                preview.backup.dailyReviews.forEach { dao.insertReview(it) }
                preview.backup.drafts.forEach { dao.saveDraft(it) }
                preview.backup.savedFilters.forEach { dao.saveFilter(it) }
            } else {
                val tagKeys = preview.before.tags.map { it.kind to it.id }.toSet()
                preview.backup.tags.filter { (it.kind to it.id) !in tagKeys }.forEach { dao.saveTag(it) }
                // Keep local names, favorite flags, ordering and settings on conflicts.
                val instrumentIds = preview.before.instruments.map { it.id }.toSet()
                val names = preview.before.instruments.map { it.market to it.name }.toSet()
                preview.backup.instruments.filter { it.id !in instrumentIds && (it.market to it.name) !in names }.forEach { dao.saveInstrument(it) }
                val keys = preview.before.trades.map { it.recordKey }.toSet()
                // SQLite row IDs belong to each database. Cross-device identity uses recordKey.
                preview.backup.trades.filter { it.recordKey !in keys }.forEach { dao.insert(it.copy(id = 0)) }
                val days = preview.before.reviews.associateBy { it.date }
                preview.backup.dailyReviews.forEach { incoming ->
                    val existing = days[incoming.date]
                    if (existing == null) dao.insertReview(incoming)
                    else {
                        var merged = existing
                        if (!existing.planSaved && incoming.planSaved) merged = merged.copy(outlook = incoming.outlook,
                            strategies = incoming.strategies, preparations = incoming.preparations, maxPosition = incoming.maxPosition, planSaved = true)
                        if (!existing.reviewSaved && incoming.reviewSaved) merged = merged.copy(emotion = incoming.emotion,
                            discipline = incoming.discipline, mistakes = incoming.mistakes, lessons = incoming.lessons,
                            rating = incoming.rating, reviewSaved = true)
                        if (merged != existing) dao.saveReview(merged)
                    }
                }
                val draftKeys = preview.before.drafts.map { it.key }.toSet()
                val restoredTrades = dao.allTrades().associateBy { it.recordKey }
                preview.backup.drafts.filter { it.key !in draftKeys }.forEach { d ->
                    if (d.key == "new") { if(d.recordKey !in restoredTrades) dao.saveDraft(d) }
                    else restoredTrades[d.recordKey]?.let { dao.saveDraft(d.copy(tradeId = it.id)) }
                }
                val filterIds = preview.before.savedFilters.map { it.id }.toSet()
                preview.backup.savedFilters.filter { it.id !in filterIds }.forEach { dao.saveFilter(it) }
            }
        }
    }

    private fun samePlan(a: DailyReview, b: DailyReview) = a.outlook == b.outlook && a.strategies == b.strategies &&
        a.preparations == b.preparations && a.maxPosition == b.maxPosition
    private fun sameReview(a: DailyReview, b: DailyReview) = a.emotion == b.emotion && a.discipline == b.discipline &&
        a.mistakes == b.mistakes && a.lessons == b.lessons && a.rating == b.rating
}
