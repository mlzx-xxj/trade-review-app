package com.tradereview.app.domain

import com.tradereview.app.data.Trade
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.MathContext

data class RSample(val trade: Trade, val r: BigDecimal)
data class RPerformance(val total: Int = 0, val valid: Int = 0, val average: BigDecimal? = null,
    val cumulative: BigDecimal? = null, val winningAverage: BigDecimal? = null, val losingAverage: BigDecimal? = null,
    val winningSamples: Int = 0, val losingSamples: Int = 0, val expectationNet: BigDecimal? = null,
    val money: Performance = Performance(), val allMoney: Performance = Performance())
data class RGroup(val key: String, val performance: RPerformance, val market: String = "")
data class RiskBucket(val label: String, val trades: List<Trade>)
data class RiskReport(val r: RPerformance = RPerformance(), val strategies: List<RGroup> = emptyList(),
    val instruments: List<RGroup> = emptyList(), val plans: List<RGroup> = emptyList(),
    val checked: Int = 0, val violations: List<Trade> = emptyList(), val violationGroups: List<RiskBucket> = emptyList(),
    val plannedPositions: Int = 0, val exceededPositions: List<Trade> = emptyList(),
    val lossRuns: Int = 0, val longestLoss: Int = 0, val rBuckets: List<RiskBucket> = emptyList(), val pnlBuckets: List<RiskBucket> = emptyList())

object RiskAnalytics {
    private fun average(values: List<BigDecimal>): BigDecimal? = if(values.isEmpty()) null else values.fold(BigDecimal.ZERO, BigDecimal::add).divide(values.size.toBigDecimal(), MathContext(24, RoundingMode.HALF_UP))
    fun performance(trades: List<Trade>): RPerformance {
        require(trades.map { it.currency }.distinct().size <= 1) { "R 与净收益分析需按币种分别查看" }
        val samples = trades.mapNotNull { t -> ProfessionalMath.r(t)?.let { RSample(t, it) } }
        val wins = samples.filter { Money.value(it.trade).signum() > 0 }; val losses = samples.filter { Money.value(it.trade).signum() < 0 }
        val money = AdvancedAnalytics.performance(samples.map { it.trade })
        return RPerformance(trades.size, samples.size, average(samples.map { it.r }), samples.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { s, t -> s + t.r },
            average(wins.map { it.r }), average(losses.map { it.r }), wins.size, losses.size, money.averageNet, money, AdvancedAnalytics.performance(trades))
    }
    fun report(trades: List<Trade>): RiskReport {
        val result = performance(trades)
        fun groups(key: (Trade) -> String) = trades.groupBy(key).map { (k, rows) -> RGroup(k, performance(rows)) }
            .sortedWith(compareByDescending<RGroup> { it.performance.average }.thenBy { it.key })
        val instruments = trades.groupBy { it.market to it.symbol }.map { (key, rows) -> RGroup(key.second, performance(rows), key.first) }
            .sortedWith(compareByDescending<RGroup> { it.performance.average }.thenBy { it.key })
        val violations = trades.filter { it.riskRecord?.violations?.isNotEmpty() == true }
        val checked = trades.count { t -> t.riskRecord?.events?.any(RiskEngine::evaluated) == true }
        val planned = trades.filter { it.professional?.plannedPositionPercent != null }
        val exceeded = planned.filter { it.positionPercent.toBigDecimal() > it.professional!!.plannedPositionPercent!!.toBigDecimal() }
        var longest = 0; var streak = 0; var runs = 0
        trades.sortedWith(compareBy<Trade> { it.date }.thenBy { it.id }).forEach { t ->
            if(Money.value(t).signum() < 0) { streak++; if(streak == 2) runs++; longest = maxOf(longest, streak) } else streak = 0
        }
        val rs = trades.mapNotNull { t -> ProfessionalMath.r(t)?.let { t to it } }
        val rb = listOf("小于 -2R", "-2R 至小于 -1R", "-1R 至小于 0R", "0R", "大于 0R 至小于 1R", "1R 至小于 2R", "2R 及以上")
        fun rBin(v: BigDecimal) = when { v < BigDecimal("-2") -> 0; v < BigDecimal("-1") -> 1; v.signum() < 0 -> 2; v.signum() == 0 -> 3; v < BigDecimal.ONE -> 4; v < BigDecimal("2") -> 5; else -> 6 }
        val pb = listOf("小于 -1000", "-1000 至小于 -100", "-100 至小于 0", "0", "大于 0 至小于 100", "100 至小于 1000", "1000 及以上")
        fun pBin(v: BigDecimal) = when { v < BigDecimal("-1000") -> 0; v < BigDecimal("-100") -> 1; v.signum() < 0 -> 2; v.signum() == 0 -> 3; v < BigDecimal("100") -> 4; v < BigDecimal("1000") -> 5; else -> 6 }
        return RiskReport(result, groups { it.strategy }, instruments, groups { it.professional?.planStatus ?: "unknown" }, checked,
            violations, RiskEngine.labels.map { (k, v) -> RiskBucket(v, trades.filter { k in it.riskRecord?.violations.orEmpty() }) },
            planned.size, exceeded, runs, longest, rb.mapIndexed { i, label -> RiskBucket(label, rs.filter { rBin(it.second) == i }.map { it.first }) },
            pb.mapIndexed { i, label -> RiskBucket(label, trades.filter { pBin(Money.value(it)) == i }) })
    }
    fun percent(count: Int, total: Int): String = if(total == 0) "—" else count.toBigDecimal().multiply(BigDecimal("100")).divide(total.toBigDecimal(), 2, RoundingMode.HALF_UP).toPlainString() + "%"
}
