package com.tradereview.app.backup

import androidx.room.withTransaction
import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import com.tradereview.app.data.*
import java.time.Instant
import com.tradereview.app.evidence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

enum class RestoreMode { MERGE, REPLACE }
data class DatabaseSnapshot(val trades: List<Trade>, val reviews: List<DailyReview>, val tags: List<ManagedTag>,
    val instruments: List<Instrument>, val settings: AppSettings, val drafts: List<EntryDraft>, val savedFilters: List<SavedFilter> = emptyList(),
    val riskRules: List<RiskRules> = emptyList(), val contractTemplates: List<ContractTemplate> = emptyList(),
    val attachments: List<Attachment> = emptyList(), val goals: List<ReviewGoal> = emptyList(), val evidenceSettings: EvidenceSettings = EvidenceSettings(),
    val smartSettings: SmartSettings = SmartSettings(), val preflightItems: List<PreflightItem> = emptyList(),
    val preflightChecks: List<PreflightCheck> = emptyList(), val ocrDrafts: List<OcrDraft> = emptyList(), val ledger:com.tradereview.app.v200.LedgerSnapshot?=null)
data class MergeSummary(val newTrades: Int, val repeatedTrades: Int, val conflictingTrades: Int,
    val newDays: Int, val addedPlans: Int, val addedReviews: Int, val conflictingPlans: Int, val conflictingReviews: Int)
data class RestorePreview(val backup: BackupData, val before: DatabaseSnapshot, val summary: MergeSummary)

class BackupRepository(private val database: TradeDatabase, private val store: AttachmentStore? = null) {
    private val ledger=com.tradereview.app.v200.LedgerRepository(database)
    private val dao get() = database.trades()
    private suspend fun snapshot() = DatabaseSnapshot(dao.allTrades(), dao.allReviews(), dao.allTags(), dao.allInstruments(), dao.settings() ?: AppSettings(), dao.allDrafts(), dao.allFilters(), dao.allRiskRules(), dao.allTemplates(), dao.allAttachments(), dao.allGoals(), dao.evidenceSettings() ?: EvidenceSettings(), dao.smartSettings() ?: SmartSettings(), dao.allPreflightItems(), dao.allPreflightChecks(), dao.allOcrDrafts(),ledger.read())

    suspend fun export(full: Boolean = false): BackupData = database.withTransaction {
        val state = snapshot()
        BackupData(Instant.now().toString(), state.trades, state.reviews, state.tags, state.instruments, state.settings, state.drafts, savedFilters = state.savedFilters, riskRules = state.riskRules, contractTemplates = state.contractTemplates,
            attachments=if(full)state.attachments else emptyList(),goals=state.goals,evidenceSettings=state.evidenceSettings,backupScope=if(full)"with-attachment-files"else "structured-data-only",smartSettings=state.smartSettings,preflightItems=state.preflightItems,preflightChecks=state.preflightChecks,ocrDrafts=state.ocrDrafts,ledger=state.ledger)
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
    suspend fun restore(preview: RestorePreview, mode: RestoreMode) = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        try { restoreLocked(preview,mode) } finally { cleanupFiles() }
    } }
    internal suspend fun cleanupFiles() { withContext(NonCancellable) { runCatching { store?.cleanup(dao.allAttachments()) } } }
    internal fun incomingAttachments(preview: RestorePreview, mode: RestoreMode): List<Attachment> {
        if(mode==RestoreMode.REPLACE)return preview.backup.attachments
        val old=preview.before.trades.associateBy { it.recordKey };val ids=preview.before.attachments.map { it.id }.toSet()
        val coherent=preview.backup.trades.filter { t -> old[t.recordKey]?.let { it.copy(id=t.id)==t } != false }.map { it.recordKey }.toSet()
        return preview.backup.attachments.filter { it.id !in ids && it.recordKey in coherent }
    }
    internal suspend fun restoreLocked(preview: RestorePreview, mode: RestoreMode) {
        val restoreContext=kotlinx.coroutines.currentCoroutineContext()
        restoreContext.ensureActive()
        BackupCodec.validate(preview.backup)
        val targetLedger=try {preview.backup.ledger?.let{if(mode==RestoreMode.REPLACE)it else com.tradereview.app.v200.LedgerRepository.merge(requireNotNull(preview.before.ledger),it)}?:preview.before.ledger?.let{local->
            val ids=local.strategies.map{it.id}.toSet()
            val reliable=preview.backup.tags.filter{it.kind=="strategy" && it.id !in ids}.map{com.tradereview.app.v200.LedgerStrategy(it.id,it.name,"旧备份策略，稳定身份保留",it.sortOrder,!it.hidden)}
            local.copy(strategies=(local.strategies+reliable).sortedBy{it.id})
        }} catch(e:com.tradereview.app.v200.LedgerException){throw BackupException(e.message?:"合并账本不符合配对规则，未恢复")}
        val incoming=incomingAttachments(preview,mode)
        val finalAttachments=if(mode==RestoreMode.REPLACE)incoming else preview.before.attachments+incoming
        require(finalAttachments.size<=EvidenceLimits.MAX_IMAGES && finalAttachments.groupBy { it.recordKey }.values.all { it.size<=10 }) { "合并后图片超过上限，未恢复；可核对后选择覆盖" }
        if(finalAttachments.isNotEmpty())requireNotNull(store) { "含截图备份必须通过完整 ZIP 恢复" }.verifyAll(finalAttachments)
        database.withTransaction {
            restoreContext.ensureActive()
            if (snapshot() != preview.before) throw BackupException("本地数据在预览后已变化，请重新预览再确认")
            if (mode == RestoreMode.REPLACE) {
                dao.clearSmartSettings();dao.clearPreflightItems();dao.clearPreflightChecks();dao.clearOcrDrafts()
                dao.clearAttachments();dao.clearGoals();dao.clearEvidenceSettings()
                dao.clearTrades()
                dao.clearReviews()
                dao.clearDrafts(); dao.clearRiskRules(); dao.clearTemplates(); dao.clearFilters(); dao.clearTags(); dao.clearInstruments(); dao.clearSettings()
                preview.backup.tags.forEach { dao.saveTag(it) }
                preview.backup.instruments.forEach { dao.saveInstrument(it) }
                dao.saveSettings(preview.backup.settings)
                preview.backup.trades.forEach { dao.insert(it) }
                preview.backup.dailyReviews.forEach { dao.insertReview(it) }
                preview.backup.drafts.forEach { dao.saveDraft(it) }
                preview.backup.savedFilters.forEach { dao.saveFilter(it) }
                preview.backup.riskRules.forEach { dao.saveRiskRules(it) }
                preview.backup.contractTemplates.forEach { dao.saveTemplate(it) }
                incoming.forEach { dao.saveAttachment(it) }
                preview.backup.goals.forEach { dao.saveGoal(it) }
                dao.saveEvidenceSettings(preview.backup.evidenceSettings)
                dao.saveSmartSettings(preview.backup.smartSettings)
                preview.backup.preflightItems.forEach { dao.savePreflightItem(it) };preview.backup.preflightChecks.forEach { dao.savePreflightCheck(it) };preview.backup.ocrDrafts.forEach { dao.saveOcrDraft(it) }
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
                val currencies = preview.before.riskRules.map { it.currency }.toSet()
                preview.backup.riskRules.filter { it.currency !in currencies }.forEach { dao.saveRiskRules(it) }
                val templateIds = preview.before.contractTemplates.map { it.id }.toSet()
                preview.backup.contractTemplates.filter { it.id !in templateIds }.forEach { dao.saveTemplate(it) }
                incoming.forEach { dao.saveAttachment(it) }
                val goalIds=preview.before.goals.map { it.id }.toSet()
                val goals=preview.backup.goals.filter { it.id !in goalIds }
                require(preview.before.goals.size+goals.size<=500) { "合并后目标超过 500 个上限" }
                goals.forEach { dao.saveGoal(it) }
                val itemIds=preview.before.preflightItems.map{it.id}.toSet()
                val checkKeys=preview.before.preflightChecks.map{it.date to it.itemKey}.toSet()
                val draftIds=preview.before.ocrDrafts.map{it.id}.toSet()
                val items=preview.backup.preflightItems.filter { it.id !in itemIds }
                val checks=preview.backup.preflightChecks.filter { (it.date to it.itemKey) !in checkKeys }
                val drafts=preview.backup.ocrDrafts.filter { it.id !in draftIds }
                require(preview.before.preflightItems.size+items.size<=100 && preview.before.preflightChecks.size+checks.size<=10000 && preview.before.ocrDrafts.size+drafts.size<=100) { "合并后的智能清单或草稿超限" }
                items.forEach { dao.savePreflightItem(it) };checks.forEach { dao.savePreflightCheck(it) };drafts.forEach { dao.saveOcrDraft(it) }
            }
            // Legacy-only backups do not erase a V2 ledger they do not contain.
            targetLedger?.let{ledger.write(it,clear=true)}
            // Room can execute synchronous DAO calls without another suspension. Check the caller
            // explicitly before commit, so cancellation during a blocked SQLite call rolls back.
            restoreContext.ensureActive()
        }
    }

    private fun samePlan(a: DailyReview, b: DailyReview) = a.outlook == b.outlook && a.strategies == b.strategies &&
        a.preparations == b.preparations && a.maxPosition == b.maxPosition
    private fun sameReview(a: DailyReview, b: DailyReview) = a.emotion == b.emotion && a.discipline == b.discipline &&
        a.mistakes == b.mistakes && a.lessons == b.lessons && a.rating == b.rating
}
