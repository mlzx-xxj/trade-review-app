package com.tradereview.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import com.tradereview.app.data.TradeDao
import com.tradereview.app.domain.Analytics
import com.tradereview.app.domain.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JournalState(
    val trades: List<Trade> = emptyList(),
    val reviews: List<DailyReview> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class TradeViewModel(private val dao: TradeDao, private val savedState: SavedStateHandle) : ViewModel() {
    val currency = savedState.getStateFlow("currency", "CNY")
    fun selectCurrency(value: String) { savedState["currency"] = value }
    val state = combine(dao.observeTrades(), dao.observeReviews()) { trades, reviews ->
        JournalState(trades, reviews, loading = false)
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(JournalState(loading = false, error = "本地数据库读取失败，请重启应用后重试。"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

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
                _message.value = "保存失败，请检查手机存储空间后重试。"
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveTrade(trade: Trade, onSuccess: () -> Unit) {
        val error = Analytics.validate(trade)
        if (error != null) { _message.value = error; return }
        write("交易已保存", onSuccess) {
            val normalized = trade.copy(symbol = trade.symbol.trim(), netPnl = requireNotNull(Money.parse(trade.netPnl)).toPlainString())
            if (normalized.id == 0L) dao.insert(normalized) else dao.save(normalized)
        }
    }

    fun deleteTrade(trade: Trade, onSuccess: () -> Unit) = write("交易已删除", onSuccess) { dao.delete(trade) }

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
