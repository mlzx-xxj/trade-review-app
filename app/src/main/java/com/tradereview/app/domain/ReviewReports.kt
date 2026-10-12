package com.tradereview.app.domain

import com.tradereview.app.data.*
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ReviewPeriod(val kind: String, val start: Long, val end: Long) {
    fun shift(delta: Long): ReviewPeriod = at(kind, if(kind == "week") LocalDate.ofEpochDay(start).plusWeeks(delta) else LocalDate.ofEpochDay(start).plusMonths(delta))
    companion object {
        fun at(kind: String, date: LocalDate): ReviewPeriod {
            require(kind in listOf("week", "month"))
            val first = if(kind == "week") date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else date.withDayOfMonth(1)
            return ReviewPeriod(kind, first.toEpochDay(), (if(kind == "week") first.plusDays(6) else first.with(TemporalAdjusters.lastDayOfMonth())).toEpochDay())
        }
    }
}
data class PeriodCurrency(val currency: String, val analysis: AnalysisReport, val risk: RiskReport)
data class PeriodReport(val period: ReviewPeriod, val through: Long, val trades: List<Trade>, val reviews: List<DailyReview>,
    val currencies: List<PeriodCurrency>, val errors: List<Pair<String, Int>>, val discipline: List<Pair<String, Int>>,
    val plansIn: Int, val plansOut: Int, val plansUnknown: Int, val riskChecked: Int, val riskViolations: Int,
    val disciplineProvided: Int, val previousCount: Int, val previousErrors: Map<String, Int>, val previousCurrencies: List<PeriodCurrency>) {
    val winningTrades = trades.filter { Money.value(it).signum() > 0 }
    val losingTrades = trades.filter { Money.value(it).signum() < 0 }
    val wins get() = winningTrades.size
    val losses get() = losingTrades.size
    val flats get() = trades.size - wins - losses
}
object ReviewReports {
    fun calculate(all: List<Trade>, logs: List<DailyReview>, period: ReviewPeriod, today: Long = LocalDate.now().toEpochDay()): PeriodReport {
        val through = minOf(period.end, today)
        val rows = all.filter { it.date in period.start..through }
        val reviews = logs.filter { it.date in period.start..through }
        val previous = period.shift(-1)
        val priorRows = all.filter { it.date in previous.start..minOf(previous.end, today) }
        val priorLogs = logs.filter { it.date in previous.start..minOf(previous.end, today) }
        fun currencies(trades: List<Trade>, daily: List<DailyReview>, p: ReviewPeriod) = Presets.currencies.map { c ->
            val currencyRows = trades.filter { it.currency == c.id }
            PeriodCurrency(c.id, AdvancedAnalytics.report(currencyRows, daily,
                TradeFilter(period="custom",startDay=p.start,endDay=p.end), LocalDate.ofEpochDay(today)), RiskAnalytics.report(currencyRows))
        }
        fun counts(values: List<Trade>, get: (Trade) -> List<String>) = values.flatMap { get(it).distinct() }.groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String,Int>> { it.second }.thenBy { it.first })
        return PeriodReport(period, through, rows, reviews, currencies(rows, reviews, period), counts(rows) { it.mistakes }, counts(rows) { it.discipline },
            rows.count { it.professional?.planStatus == "in" }, rows.count { it.professional?.planStatus == "out" }, rows.count { it.professional?.planStatus !in listOf("in", "out") },
            rows.count { it.riskRecord?.events?.any(RiskEngine::evaluated) == true }, rows.count { it.riskRecord?.violations?.isNotEmpty() == true },
            rows.count { it.discipline.isNotEmpty() }, priorRows.size, counts(priorRows) { it.mistakes }.toMap(), currencies(priorRows, priorLogs, previous))
    }
    fun summary(report: PeriodReport, catalog: Catalog): String {
        val prefix = if(report.period.kind == "week") "本周" else "本月"
        val error = report.errors.firstOrNull() ?: return "$prefix 记录 ${report.trades.size} 笔交易，未记录错误标签；空白不代表已遵守纪律。上期 ${report.previousCount} 笔。"
        val prior = report.previousErrors[error.first] ?: 0
        val delta = error.second - prior
        return "$prefix 记录 ${report.trades.size} 笔交易，其中 ${error.second} 笔带有${catalog.label("mistake",error.first)}标签，较上期${if(delta<0) "减少 ${-delta}" else if(delta>0) "增加 $delta" else "不变 0"} 笔（上期 $prior 笔）。"
    }
}
data class GoalProgress(val value: BigDecimal?, val denominator: Int?, val missing: Int, val reached: Boolean?, val explanation: String)
object ReviewGoals {
    fun validate(g: ReviewGoal, tags: List<ManagedTag>, today: Long = LocalDate.now().toEpochDay()) {
        require(Regex("[0-9a-f]{32}").matches(g.id) && g.title.isNotBlank() && g.title.length <= 120 && '\u0000' !in g.title)
        require(g.kind in EvidenceOptions.goals.map { it.id } && g.period in listOf("week", "month"))
        require(g.startDate in LocalDate.of(1900,1,1).toEpochDay()..today && ReviewPeriod.at(g.period, LocalDate.ofEpochDay(g.startDate)).start == g.startDate)
        require(g.archivedDay == null || g.archivedDay in g.startDate..today)
        if(g.kind == "text") require(g.threshold == null && g.mistakeId == null)
        else {
            val value = g.threshold?.toBigDecimalOrNull()
            require(value != null && ProfessionalMath.plain(value) == g.threshold && value >= BigDecimal.ZERO)
            if(g.kind.endsWith("ratio")) require(value <= BigDecimal("100") && value.scale() <= 2)
            else require(value.scale() <= 0 && value <= BigDecimal(if(g.kind == "review_days") (if(g.period == "week") "7" else "31") else "10000"))
            require(if(g.kind == "mistake_count") tags.any { it.kind == "mistake" && it.id == g.mistakeId } else g.mistakeId == null)
        }
    }
    fun progress(g: ReviewGoal, all: List<Trade>, logs: List<DailyReview>, p: ReviewPeriod, today: Long = LocalDate.now().toEpochDay()): GoalProgress {
        if(g.kind == "text") return GoalProgress(null,null,0,null,"文字目标需自行判断，不自动量化")
        if(p.kind != g.period || p.start < g.startDate || p.start > today || g.archivedDay?.let { p.start > it } == true)
            return GoalProgress(null,null,0,null,"该周期不在目标生效范围")
        val through = minOf(p.end, today, g.archivedDay ?: Long.MAX_VALUE)
        val rows = all.filter { it.date in p.start..through }
        var denominator = rows.size; var missing = 0
        val value: BigDecimal? = when(g.kind) {
            "mistake_count" -> if(rows.isEmpty()) null else rows.count { g.mistakeId in it.mistakes }.toBigDecimal()
            "risk_count" -> { val checked = rows.filter { it.riskRecord?.events?.any(RiskEngine::evaluated) == true }; denominator = checked.size; missing=rows.size-checked.size
                if(checked.isEmpty()) null else checked.count { it.riskRecord?.violations?.isNotEmpty() == true }.toBigDecimal() }
            "review_days" -> { denominator=(through-p.start+1).toInt();val days=logs.count { it.reviewSaved && it.date in p.start..through };missing=denominator-days;days.toBigDecimal() }
            "review_ratio" -> if(rows.isEmpty()) null else rows.count { it.reviewed }.toBigDecimal().multiply(BigDecimal("100")).divide(rows.size.toBigDecimal(),java.math.MathContext(24,java.math.RoundingMode.HALF_UP))
            "plan_ratio" -> { val known=rows.filter { it.professional?.planStatus in listOf("in","out") };denominator=known.size;missing=rows.size-known.size
                if(known.isEmpty()) null else known.count { it.professional?.planStatus == "in" }.toBigDecimal().multiply(BigDecimal("100")).divide(known.size.toBigDecimal(),java.math.MathContext(24,java.math.RoundingMode.HALF_UP)) }
            else -> null
        }
        val reached = value?.let { if(g.kind.endsWith("count")) it <= g.threshold!!.toBigDecimal() else it >= g.threshold!!.toBigDecimal() }
        return GoalProgress(value,denominator,missing,reached,when(g.kind) {
            "plan_ratio" -> "分母仅明确计划内/外交易，未知 $missing 笔不计入"
            "risk_count" -> "仅可执行纪律检查的交易；未知检查 $missing 笔不当作守规"
            "review_days" -> "已保存盘后复盘日期 / 周期已过自然日；未保存 $missing 天"
            "review_ratio" -> "已复盘交易 / 全部交易；零交易不可计算"
            else -> "仅已记录的该错误标签；零交易不可计算，未标记不代表守规"
        })
    }
    fun history(g: ReviewGoal, trades: List<Trade>, logs: List<DailyReview>, ending: ReviewPeriod, today: Long = LocalDate.now().toEpochDay()) =
        (3 downTo 0).map { ending.shift(-it.toLong()) }.map { it to progress(g,trades,logs,it,today) }
}
