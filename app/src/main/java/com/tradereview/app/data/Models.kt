package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

data class Option(val id: String, val label: String)

object Presets {
    val markets = listOf(Option("stock", "股票"), Option("futures", "期货"), Option("forex", "外汇"), Option("crypto", "数字资产"))
    val directions = listOf(Option("long", "做多"), Option("short", "做空"))
    val currencies = listOf(Option("CNY", "人民币"), Option("USD", "美元"), Option("USDT", "USDT"))
    val strategies = listOf(Option("trend", "趋势跟随"), Option("breakout", "突破交易"), Option("pullback", "回调入场"), Option("range", "区间交易"))
    val emotions = listOf(Option("calm", "平静"), Option("confident", "自信"), Option("anxious", "焦虑"), Option("impatient", "急躁"), Option("excited", "兴奋"))
    val mistakes = listOf(Option("chase", "追涨杀跌"), Option("no_stop", "未设止损"), Option("early_exit", "提前止盈"), Option("oversize", "仓位过重"), Option("countertrend", "逆势交易"), Option("overtrade", "频繁交易"))
    val discipline = listOf(Option("follow_plan", "遵守入场计划"), Option("respect_stop", "按计划止损"), Option("size_control", "控制仓位"), Option("no_impulse", "避免冲动交易"))
    val outlooks = listOf(Option("bullish", "偏多"), Option("bearish", "偏空"), Option("range", "震荡"), Option("wait", "观望"))
    val preparations = listOf(Option("levels", "确认关键价位"), Option("risk", "设定止损与风险"), Option("news", "检查重要事件"), Option("size", "确定最大仓位"))
    val lessons = listOf(Option("wait", "等待信号确认"), Option("smaller", "降低单笔仓位"), Option("stop", "严格执行止损"), Option("rest", "情绪不稳时休息"), Option("keep", "保持当前纪律"))
    fun label(options: List<Option>, id: String): String = options.firstOrNull { it.id == id }?.label ?: id
    fun symbols(market: String) = when (market) {
        "stock" -> listOf("沪深300", "中证500", "上证50", "AAPL", "NVDA")
        "futures" -> listOf("螺纹钢", "黄金", "原油", "沪铜")
        "forex" -> listOf("EUR/USD", "GBP/USD", "USD/JPY")
        else -> listOf("BTC/USDT", "ETH/USDT", "SOL/USDT")
    }
}

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = LocalDate.now().toEpochDay(),
    val market: String = "stock",
    val symbol: String = "沪深300",
    val direction: String = "long",
    val currency: String = "CNY",
    val netPnl: String = "0.00",
    val positionPercent: Int = 10,
    val strategy: String = "trend",
    val emotion: String = "calm",
    val mistakes: List<String> = emptyList(),
    val discipline: List<String> = emptyList(),
    val reviewed: Boolean = false,
)

@Entity(tableName = "daily_reviews")
data class DailyReview(
    @PrimaryKey val date: Long = LocalDate.now().toEpochDay(),
    val outlook: String = "wait",
    val strategies: List<String> = emptyList(),
    val preparations: List<String> = emptyList(),
    val maxPosition: Int = 20,
    val planSaved: Boolean = false,
    val emotion: String = "calm",
    val discipline: List<String> = emptyList(),
    val mistakes: List<String> = emptyList(),
    val lessons: List<String> = emptyList(),
    val rating: Int = 3,
    val reviewSaved: Boolean = false,
)
