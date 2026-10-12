package com.tradereview.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class JournalState(
    val trades: List<Trade> = emptyList(),
    val reviews: List<DailyReview> = emptyList(),
    val tags: List<ManagedTag> = emptyList(),
    val instruments: List<Instrument> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val drafts: List<EntryDraft> = emptyList(),
    val savedFilters: List<SavedFilter> = emptyList(),
    val riskRules: List<RiskRules> = emptyList(),
    val contractTemplates: List<ContractTemplate> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

private data class AnalysisInput(val trades: List<Trade>, val reviews: List<DailyReview>, val loading: Boolean, val error: String?, val currency: String, val filter: TradeFilter)

class TradeViewModel(private val database: TradeDatabase, private val savedState: SavedStateHandle) : ViewModel() {
    private val dao = database.trades()
    private val entries = EntryRepository(database)
    val currency = savedState.getStateFlow("currency", "CNY")
    fun selectCurrency(value: String) { savedState["currency"] = value }
    private val _filter = MutableStateFlow(runCatching { FilterCodec.decode(savedState.get<String>("filter") ?: FilterCodec.encode(TradeFilter())) }.getOrDefault(TradeFilter()))
    val filter = _filter.asStateFlow()
    fun updateFilter(value: TradeFilter) {
        try { FilterCodec.validate(value); savedState["filter"] = FilterCodec.encode(value); _filter.value = value }
        catch (e: IllegalArgumentException) { notify(e.message ?: "筛选条件无效") }
    }
    fun clearFilters() = updateFilter(TradeFilter())
    private val journal = combine(dao.observeTrades(), dao.observeReviews()) { t, r -> t to r }
    private val catalog = combine(dao.observeTags(), dao.observeInstruments(), dao.observeSettings(), dao.observeDrafts()) { tags, instruments, settings, drafts ->
        JournalState(tags = tags, instruments = instruments, settings = settings ?: AppSettings(), drafts = drafts, loading = false)
    }
    private val riskSettings = combine(dao.observeRiskRules(), dao.observeTemplates()) { rules, templates -> rules to templates }
    val state = combine(journal, catalog, dao.observeFilters(), riskSettings) { (trades, reviews), catalog, filters, risk -> catalog.copy(trades = trades, reviews = reviews, savedFilters = filters, riskRules = risk.first, contractTemplates = risk.second) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(JournalState(loading = false, error = "本地数据库读取失败，请重启应用后重试。"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val analytics = combine(state, currency, filter) { j, c, f -> AnalysisInput(j.trades, j.reviews, j.loading, j.error, c, f) }
        .distinctUntilChanged()
        .mapLatest { input ->
            if (input.loading || input.error != null) AnalyticsState()
            else withContext(Dispatchers.Default) { AdvancedAnalytics.calculate(input.trades, input.reviews, input.currency, input.filter) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    fun clearMessage() { _message.value = null }

    private fun write(successMessage: String, onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                _message.value = successMessage
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = if(error is IllegalArgumentException && !error.message.isNullOrBlank()) "保存失败：${error.message}" else "保存失败，请检查手机存储空间后重试。"
            } finally {
                _busy.value = false
            }
        }
    }

    private var draftJob: Job? = null
    // The editor persists each change immediately; serialization avoids stale writes after save.
    private val draftMutex = kotlinx.coroutines.sync.Mutex()
    fun persistDraft(draft: EntryDraft) {
        draftJob = viewModelScope.launch {
            draftMutex.lock()
            try { entries.saveDraft(draft) }
            catch (error: Exception) { if (error is CancellationException) throw error; _message.value = "草稿保存失败，请检查存储空间" }
            finally { draftMutex.unlock() }
        }
    }
    fun saveEntry(draft: EntryDraft, onSuccess: () -> Unit, approvedRisk: String? = null) = write("交易已保存", onSuccess) {
        draftJob?.join()
        draftMutex.lock()
        try { entries.save(draft, approvedRisk) } finally { draftMutex.unlock() }
    }
    fun deleteTrade(trade: Trade, onSuccess: () -> Unit) = write("交易已删除", onSuccess) { entries.delete(trade) }
    fun saveTag(tag: ManagedTag) = write("标签已保存") { entries.saveTag(tag) }
    fun moveTag(tag: ManagedTag, delta: Int) = write("标签排序已更新") { entries.moveTag(tag, delta) }
    fun saveInstrument(value: Instrument) = write("品种已保存") { entries.saveInstrument(value) }
    fun moveInstrument(value: Instrument, delta: Int) = write("品种排序已更新") { entries.moveInstrument(value, delta) }
    fun selectTheme(theme: String) = write("显示模式已更新") { dao.saveSettings((dao.settings() ?: AppSettings()).copy(theme = theme)) }
    fun selectPalette(value: String) = write("盈亏配色已更新") {
        require(value in listOf("green_profit", "red_profit"))
        dao.saveSettings((dao.settings() ?: AppSettings()).copy(pnlPalette = value))
    }
    fun saveFilter(name: String) = write("筛选方案已保存") {
        database.withTransaction {
            val values = dao.allFilters(); require(values.size < 100) { "最多保存 100 个筛选方案" }
            val value = SavedFilter(name = name.trim(), currency = currency.value, criteria = filter.value)
            validateSavedFilter(value, dao.allTags()); dao.saveFilter(value)
        }
    }
    fun applyFilter(value: SavedFilter) { selectCurrency(value.currency); updateFilter(value.criteria); notify("已应用筛选方案：${value.name}") }
    fun deleteFilter(value: SavedFilter) = write("筛选方案已删除") { dao.deleteFilter(value) }
    fun notify(message: String) { _message.value = message }
    fun saveRiskRules(value: RiskRules) = write("风险规则已保存，仅影响后续检查") { ProfessionalMath.validateRule(value); dao.saveRiskRules(value) }
    fun saveTemplate(value: ContractTemplate) = write("合约参数模板已保存") {
        database.withTransaction { ProfessionalMath.validateTemplate(value); require(dao.allTemplates().size < 500 || dao.allTemplates().any { it.id == value.id }); dao.saveTemplate(value) }
    }
    fun deleteTemplate(value: ContractTemplate) = write("模板已删除，历史交易参数保留") { dao.deleteTemplate(value) }

    fun savePlan(draft: DailyReview) = write("盘前计划已保存") {
        val current = dao.findReview(draft.date) ?: DailyReview(date = draft.date)
        dao.saveReview(current.copy(outlook = draft.outlook, strategies = draft.strategies,
            preparations = draft.preparations, maxPosition = draft.maxPosition, planSaved = true))
    }

    fun saveReview(draft: DailyReview) = write("盘后复盘已保存") {
        val current = dao.findReview(draft.date) ?: DailyReview(date = draft.date)
        dao.saveReview(current.copy(emotion = draft.emotion, discipline = draft.discipline,
            mistakes = draft.mistakes, lessons = draft.lessons, rating = draft.rating, reviewSaved = true))
    }

    fun addDemo() = write("已添加 4 笔示例交易，可在交易记录中删除") {
        val today = java.time.LocalDate.now().toEpochDay()
        listOf(
            Trade(date = today - 3, netPnl = "260.00", positionPercent = 15, reviewed = true, discipline = listOf("follow_plan", "respect_stop", "size_control", "no_impulse")),
            Trade(date = today - 2, symbol = "中证500", netPnl = "-90.00", strategy = "breakout", emotion = "anxious", mistakes = listOf("chase", "oversize"), reviewed = true),
            Trade(date = today - 1, netPnl = "180.00", strategy = "pullback", reviewed = true, discipline = listOf("follow_plan", "size_control")),
            Trade(date = today, symbol = "上证50", netPnl = "-50.00", mistakes = listOf("early_exit")),
        ).forEach { dao.insert(it) }
    }
}
