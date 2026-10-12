package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private data class CalculatedPeriod(val period: ReviewPeriod,val trades: List<Trade>,val reviews: List<DailyReview>,val report: PeriodReport)
@Composable
fun ReviewReportPage(kind: String,state: JournalState,currency: String,onCurrency: (String)->Unit,onKind: (String)->Unit,
    onGoals: ()->Unit,onTrades: (List<Trade>,String)->Unit,modifier: Modifier = Modifier) {
    var anchor by rememberSaveable(kind) { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val today=LocalDate.now();val period=ReviewPeriod.at(kind,LocalDate.ofEpochDay(anchor));val catalog=LocalCatalog.current
    val calculated by produceState<CalculatedPeriod?>(null,state.trades,state.reviews,period,today) {
        value=withContext(Dispatchers.Default) { CalculatedPeriod(period,state.trades,state.reviews,ReviewReports.calculate(state.trades,state.reviews,period,today.toEpochDay())) }
    }
    val report=calculated?.takeIf { it.period==period && it.trades===state.trades && it.reviews===state.reviews }?.report
    LazyColumn(modifier.fillMaxSize().testTag("period-report-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { SectionCard(if(kind=="week")"每周复盘报告"else "每月复盘报告","本地规则汇总，不接 AI；不作盈利预测或因果推断。") {
            SingleChoice(listOf(Option("week","周报"),Option("month","月报")),kind,onKind)
            Text("${dayLabel(period.start)} — ${dayLabel(period.end)}",modifier=Modifier.testTag("report-period"))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(enabled=period.start>LocalDate.of(1900,1,1).toEpochDay(),onClick={anchor=period.shift(-1).start},modifier=Modifier.testTag("report-previous")){Text("上一${if(kind=="week") "周" else "月"}")}
                TextButton(onClick={anchor=today.toEpochDay()}){Text("当前周期")}
                TextButton(enabled=period.start<ReviewPeriod.at(kind,today).start,onClick={anchor=period.shift(1).start}){Text("下一${if(kind=="week") "周" else "月"}")}
            }
            Text("当前周期截至今天；比较期为完整上一周期，记录天数可能不同。",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onGoals,modifier=Modifier.fillMaxWidth()){Text("管理纪律改善目标")}
        } }
        if(report==null)item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在本地计算报告") }
        else {
            item { SectionCard("交易表现总览") {
                Text("记录 ${report.trades.size} 笔 · 上期 ${report.previousCount} 笔",modifier=Modifier.testTag("report-total"))
                Text("盈利 ${report.wins} · 亏损 ${report.losses} · 持平 ${report.flats} · 胜率 ${RiskAnalytics.percent(report.wins,report.trades.size)}")
                Row { TextButton(onClick={onTrades(report.trades,"本期全部交易")}){Text("全部明细")}
                    TextButton(onClick={onTrades(report.winningTrades,"本期盈利交易")}){Text("盈利明细")}
                    TextButton(onClick={onTrades(report.losingTrades,"本期亏损交易")}){Text("亏损明细")} }
                if(report.trades.size<20)Text("样本量 ${report.trades.size} 笔，结论可能不稳定。",color=MaterialTheme.colorScheme.primary)
                Text(ReviewReports.summary(report,catalog),modifier=Modifier.testTag("report-summary"))
            } }
            item { SectionCard("各币种净盈亏与 R", "币种独立核算；未提供风险不当作 0R。") {
                report.currencies.forEach { c -> val p=c.analysis.performance;val r=c.risk.r;val old=report.previousCurrencies.first { it.currency==c.currency }.analysis.performance
                    Text("${c.currency} · ${p.count} 笔 · ${if(p.count==0) "无交易" else Money.format(p.netPnl,true)}",color=if(p.count==0)MaterialTheme.colorScheme.onSurface else pnlColor(p.netPnl),modifier=Modifier.testTag("report-net-${c.currency}"))
                    Text("平均 ${ProfessionalMath.formatR(r.average)} · 累计 ${ProfessionalMath.formatR(r.cumulative)} · 有效 R ${r.valid}/${r.total} 笔")
                    Text("上期 ${old.count} 笔 · ${if(old.count==0) "无可比金额样本" else Money.format(old.netPnl,true)}；${if(old.count>0 && p.count>0) "净额差 ${Money.format(p.netPnl-old.netPnl,true)} ${c.currency}" else "缺少可比交易样本"}",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={onTrades(report.trades.filter { it.currency==c.currency },"${c.currency} 本期明细")}){Text("查看 ${c.currency} 明细")}
                }
            } }
            item { SectionCard("计划、风险与执行纪律") {
                val known=report.plansIn+report.plansOut
                Text("计划内 ${report.plansIn} · 计划外 ${report.plansOut} · 未知 ${report.plansUnknown}")
                Text("明确计划状态 $known 笔：计划内 ${RiskAnalytics.percent(report.plansIn,known)} / 计划外 ${RiskAnalytics.percent(report.plansOut,known)}；未知不纳入分母。")
                Row { TextButton(onClick={onTrades(report.trades.filter { it.professional?.planStatus=="in" },"本期计划内交易")}){Text("计划内明细")}
                    TextButton(onClick={onTrades(report.trades.filter { it.professional?.planStatus=="out" },"本期计划外交易")}){Text("计划外明细")} }
                Text("风险违规 ${report.riskViolations} / 可检查 ${report.riskChecked} 笔 · 未提供检查 ${report.trades.size-report.riskChecked} 笔",modifier=Modifier.testTag("report-risk"))
                TextButton(onClick={onTrades(report.trades.filter { it.riskRecord?.violations?.isNotEmpty()==true },"本期风险违规交易")}){Text("风险违规明细")}
                Text("明确勾选纪律 ${report.disciplineProvided} 笔，未提供 ${report.trades.size-report.disciplineProvided} 笔；空白不视为守规。")
                report.discipline.forEach { (id,count) -> TextButton(onClick={onTrades(report.trades.filter { id in it.discipline },catalog.label("discipline",id))}){Text("${catalog.label("discipline",id)}：显式勾选 $count 笔")} }
                Text("交易复盘 ${report.trades.count { it.reviewed }}/${report.trades.size} 笔；盘后日志 ${report.reviews.count { it.reviewSaved }} 天")
            } }
            item { SectionCard("常见错误排行", "按稳定标签 ID 统计；隐藏或重命名保留历史。") {
                if(report.errors.isEmpty())Text("没有已记录的错误标签，不能据此认定已遵守全部纪律。")
                report.errors.forEach { (id,count) -> TextButton(onClick={onTrades(report.trades.filter { id in it.mistakes },catalog.label("mistake",id))}){Text("${catalog.label("mistake",id)}：$count 笔 · 上期 ${report.previousErrors[id] ?: 0} 笔")} }
            } }
            item { ReportInsights(state,period,onTrades) }
            item { SectionCard("图表币种") { SingleChoice(Presets.currencies,currency,onCurrency) } }
            val c=report.currencies.first { it.currency==currency }
            item { SectionCard("每日净盈亏趋势 · $currency") { AmountBarChart(c.analysis.daily,currency,onDay={day -> onTrades(report.trades.filter { it.date==day && it.currency==currency },dayLabel(day))}) } }
            if(kind=="month")item { SectionCard("月度净盈亏日历 · $currency","— 表示无交易，0 表示有持平交易；点击日期查看真实记录。") {
                ReportMonth(YearMonth.from(LocalDate.ofEpochDay(period.start)),c.analysis.daily,currency) { day -> onTrades(report.trades.filter { it.date==day && it.currency==currency },dayLabel(day)) }
            } }
            item { SectionCard("策略与品种表现 · $currency") {
                if(c.risk.strategies.isEmpty())Text("本币种没有交易样本")
                c.risk.strategies.forEach { g -> TextButton(onClick={onTrades(report.trades.filter { it.currency==currency && it.strategy==g.key },catalog.label("strategy",g.key))}) {
                    Text("${catalog.label("strategy",g.key)} · ${g.performance.total} 笔 · ${Money.format(g.performance.allMoney.netPnl,true)} · 平均 ${ProfessionalMath.formatR(g.performance.average)}（有效 ${g.performance.valid} 笔）")
                } }
                c.risk.instruments.forEach { g -> TextButton(onClick={onTrades(report.trades.filter { it.currency==currency && it.market==g.market && it.symbol==g.key },g.key)}) {
                    Text("${g.key} · ${g.performance.total} 笔 · ${Money.format(g.performance.allMoney.netPnl,true)} · 平均 ${ProfessionalMath.formatR(g.performance.average)}（有效 ${g.performance.valid} 笔）")
                } }
            } }
            item { SectionCard("纪律评分趋势","仅已保存盘后复盘，缺失日期不填 0 分。") { RatingTrend(report.reviews.filter { it.reviewSaved }.sortedBy { it.date }.map { RatingPoint(it.date,it.rating) }) { day -> onTrades(report.trades.filter { it.date==day },dayLabel(day)) } } }
            item { SectionCard("本期改善目标") {
                val goals=state.goals.filter { it.period==kind && it.startDate<=period.start && (it.archivedDay==null || period.start<=it.archivedDay) }
                if(goals.isEmpty())Text("本周期暂无生效目标")
                goals.take(10).forEach { g -> GoalProgressCard(g,state,period) }
                if(goals.size>10)Text("共 ${goals.size} 个目标，本报告预览前 10 个；全部进度请打开目标管理。")
            } }
        }
    }
}
@Composable
private fun ReportMonth(month: YearMonth,points: List<AmountPoint>,currency: String,onDay: (Long)->Unit) {
    val values=points.associateBy { it.date }
    Row { listOf("一","二","三","四","五","六","日").forEach { Text(it,Modifier.weight(1f)) } }
    TradingCalendar.monthCells(month).chunked(7).forEach { week -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        week.forEach { day -> if(day==null)Spacer(Modifier.weight(1f)) else {
            val p=values[day]
            Surface(onClick={onDay(day)},shape=MaterialTheme.shapes.small,color=if(p==null)MaterialTheme.colorScheme.surfaceVariant else pnlColor(p.amount).copy(alpha=.12f),modifier=Modifier.weight(1f).heightIn(min=68.dp).testTag("report-calendar-$day")) {
                Column(Modifier.padding(2.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(LocalDate.ofEpochDay(day).dayOfMonth.toString());Text(p?.let { Money.format(it.amount,true) } ?: "—",style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(p!=null)Text("${p.count}笔",style=MaterialTheme.typography.labelSmall)
                }
            }
        } }
    } }
    Text("金额单位 $currency；窄屏金额可点击查看完整值。",style=MaterialTheme.typography.bodySmall)
}
