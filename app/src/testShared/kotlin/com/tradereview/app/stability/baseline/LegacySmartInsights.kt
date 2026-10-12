// Test-only frozen V0.7 implementation for exact parity and same-process timing.
package com.tradereview.app.stability.baseline

import com.tradereview.app.domain.*

import com.tradereview.app.data.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Pure local rules. Financial results reuse the existing per-currency calculators. */
object LegacySmartInsights {
    private fun ratio(n:Int,d:Int):BigDecimal?=if(d==0)null else n.toBigDecimal().multiply(BigDecimal("100")).divide(d.toBigDecimal(),MathContext(24))
    private fun pct(n:Int,d:Int)=RiskAnalytics.percent(n,d)
    fun calculate(all:List<Trade>,logs:List<DailyReview>,goals:List<ReviewGoal>,settings:SmartSettings,
        tags:List<ManagedTag>,today:Long=LocalDate.now().toEpochDay(),period:ReviewPeriod?=null):List<Insight> {
        SmartJson.validate(settings)
        val start=period?.start ?: today-settings.lookbackDays+1;val end=minOf(period?.end ?: today,today)
        val previous=period?.shift(-1);val ps=previous?.start ?: start-settings.lookbackDays;val pe=previous?.end ?: start-1
        val now=all.filter { it.date in start..end };val old=all.filter { it.date in ps..pe };val min=settings.minimumSamples;val catalog=Catalog(tags);val out=mutableListOf<Insight>()
        fun add(id:String,category:String,priority:Int,title:String,a:List<Trade>,b:List<Trade>,evidence:String,formula:String,suggestion:String,aStart:Long=start,aEnd:Long=end,bStart:Long=ps,bEnd:Long=pe,aSample:Int=a.size,bSample:Int=b.size) {
            if(category in settings.enabledCategories)out+=Insight(id,category,priority,title,aStart,aEnd,bStart,bEnd,evidence,formula,suggestion,aSample,bSample,(a+b).map { it.recordKey }.distinct())
        }
        if(now.size>=min) {
            val errors=now.flatMap { t->t.mistakes.map { it to t } }.groupBy({it.first},{it.second})
            errors.forEach { (id,rows)->if(rows.size>=2) {
                val before=old.filter { id in it.mistakes }
                add("error:$id","discipline",95,"重复记录：${catalog.label("mistake",id)}",now,old,
                    "本期 ${rows.size}/${now.size} 笔（${pct(rows.size,now.size)}）；上期 ${before.size}/${old.size} 笔（${pct(before.size,old.size)}）。${if(old.size<min) "上期样本不足，不判断升降。" else "按实际标签次数统计。"}",
                    "含该稳定错误 ID 的交易数 / 本期全部交易；未填写标签不等于没有错误。","逐笔核对入场条件，在下次盘前检查该错误。")
            } }
            now.groupBy { it.market to it.symbol }.forEach { (k,rows)->
                val most=rows.flatMap { it.mistakes }.groupingBy { it }.eachCount().maxByOrNull { it.value }
                if(rows.size>=min && most!=null && most.value>=2)add("symbol:${k.first}:${k.second}:${most.key}","discipline",90,"${k.second} 的重复错误",rows,old.filter { it.market==k.first && it.symbol==k.second },
                    "${most.value}/${rows.size} 笔带 ${catalog.label("mistake",most.key)} 标签（${pct(most.value,rows.size)}）。","同市场、同品种、同错误稳定 ID；缺失标签未知。","查看该品种的相关证据，核对是否反复遗漏同一检查。")
            }
            val improved=errors.keys.plus(old.flatMap { it.mistakes }).distinct().map { id -> Triple(id,now.count { id in it.mistakes },old.count { id in it.mistakes }) }
                .filter { old.size>=min && it.third>it.second && it.third>=2 && ratio(it.second,now.size)!!<ratio(it.third,old.size)!! }.maxByOrNull { it.third-it.second }
            improved?.let { (id,n,o)->add("improved:$id","discipline",55,"改善最多的已记录错误：${catalog.label("mistake",id)}",now,old,
                "次数由 $o 降至 $n，减少 ${o-n} 笔；占比 ${pct(o,old.size)} → ${pct(n,now.size)}。","只比较两期均达最低样本、次数和占比均下降的标签；按次数降幅排序。","下期继续检查该项；标签漏填和样本差异可能影响对照。") }
        }
        val known=now.filter { it.professional?.planStatus in listOf("in","out") };val knownOld=old.filter { it.professional?.planStatus in listOf("in","out") }
        val nOut=known.count { it.professional?.planStatus=="out" };val oOut=knownOld.count { it.professional?.planStatus=="out" }
        if(known.size>=min && knownOld.size>=min && ratio(nOut,known.size)!!-ratio(oOut,knownOld.size)!!>=BigDecimal("10"))add("plan-out","discipline",88,"计划外交易比例上升",known,knownOld,
            "${pct(oOut,knownOld.size)}（$oOut/${knownOld.size}）→ ${pct(nOut,known.size)}（$nOut/${known.size}）；本期未知 ${now.size-known.size} 笔。","计划外 / 明确计划内外交易；增加至少 10 个百分点，未知不计分母。","核对计划外交易理由，并在盘前确认允许的策略。")
        val checked=now.filter { it.riskRecord?.events?.any(RiskEngine::evaluated)==true };val checkedOld=old.filter { it.riskRecord?.events?.any(RiskEngine::evaluated)==true }
        val violations=checked.count { it.riskRecord!!.violations.isNotEmpty() };val previousViolations=checkedOld.count { it.riskRecord!!.violations.isNotEmpty() }
        if(checked.size>=min && violations>=2)add("risk","risk",100,if(checkedOld.size>=min && violations>previousViolations)"风险违规次数增加"else "近期有重复风险违规",checked,checkedOld,
            "本期 $violations/${checked.size} 笔（${pct(violations,checked.size)}）；上期 $previousViolations/${checkedOld.size} 笔（${pct(previousViolations,checkedOld.size)}）；未检查 ${now.size-checked.size} 笔。${if(checkedOld.size<min) "上期样本不足，不判断变化。"else "两期天数/交易量可能不同。"}",
            "按已有风险快照；同笔多次违规计一笔。未知不计作遵守。","核对个人风险上限、计划风险和已记录的确认原因。")
        if(now.size>=min && old.size>=min && ratio(old.count { it.reviewed },old.size)!!-ratio(now.count { it.reviewed },now.size)!!>=BigDecimal("10"))add("review-rate","habits",85,"交易复盘完成率下降",now,old,
            "${pct(old.count { it.reviewed },old.size)} → ${pct(now.count { it.reviewed },now.size)}；本期已复盘 ${now.count { it.reviewed }}/${now.size} 笔。","已复盘 / 全部交易；下降至少 10 个百分点。","先补齐待复盘交易，再检查截图证据和执行备注。")
        Presets.currencies.forEach { currency ->
            val cn=now.filter { it.currency==currency.id };val co=old.filter { it.currency==currency.id }
            cn.groupBy { it.strategy }.forEach { (strategy,rows)->val before=co.filter { it.strategy==strategy }
                if(rows.size>=min && before.size>=min) {
                    val n=RiskAnalytics.performance(rows);val o=RiskAnalytics.performance(before)
                    val nw=rows.count { Money.value(it).signum()>0 };val ow=before.count { Money.value(it).signum()>0 }
                    val winChange=(ratio(nw,rows.size)!!-ratio(ow,before.size)!!).abs()>=BigDecimal("20")
                    val rChange=n.valid>=min && o.valid>=min && (n.average!!-o.average!!).abs()>=BigDecimal("0.5")
                    if(winChange || rChange)add("strategy:$strategy:${currency.id}","strategy",72,"${catalog.label("strategy",strategy)}近期表现变化 · ${currency.id}",rows,before,
                        "胜率 ${pct(ow,before.size)} → ${pct(nw,rows.size)}；平均 R ${ProfessionalMath.formatR(o.average)}（有效 ${o.valid}）→ ${ProfessionalMath.formatR(n.average)}（有效 ${n.valid}）。${if(!rChange) "R 样本不足或变化未达到规则阈值。"else "R 只含有效初始风险。"}",
                        "同策略/币种；胜率变化至少 20 个百分点或有效平均 R 变化至少 0.5R；持平计入胜率分母。","核对两期交易条件与样本构成，观察变化，不推断策略将来的收益。")
                }
            }
            if(cn.size>=min) {
                cn.groupBy { it.emotion }.forEach { (emotion,rows)->val losses=rows.count { Money.value(it).signum()<0 }
                    if(rows.size>=min && ratio(losses,rows.size)!!>=BigDecimal("60"))add("emotion:$emotion:${currency.id}","emotion",60,"${catalog.label("emotion",emotion)}标签与亏损较多同时出现 · ${currency.id}",rows,co.filter { it.emotion==emotion },
                        "$losses/${rows.size} 笔亏损（${pct(losses,rows.size)}），同期同币种全部亏损 ${pct(cn.count { Money.value(it).signum()<0 },cn.size)}（${cn.size} 笔）。","情绪组亏损数 / 情绪组交易数；至少 60%，不代表情绪造成亏损。","核对情绪、市场条件与风险执行，避免仅凭相关性下结论。")
                }
                val ordered=cn.sortedWith(compareBy<Trade> { it.date }.thenBy { it.id });var run=mutableListOf<Trade>();var longest=listOf<Trade>()
                ordered.forEach { t->if(Money.value(t).signum()<0) { run.add(t);if(run.size>longest.size)longest=run.toList() }else run=mutableListOf() }
                if(longest.size>=3) {
                    val s=longest.first().date;val e=longest.last().date;val inside=cn.filter { it.date in s..e };val outside=cn.filter { it.date !in s..e }
                    val days=(e-s+1).toInt();val otherDays=(end-start+1-days).toInt()
                    if(otherDays>0)add("loss-frequency:${currency.id}","habits",76,"连续亏损期间的记录频次 · ${currency.id}",cn,co,
                        "最长连续亏损 ${longest.size} 笔；对应 $days 个自然日 ${inside.size} 笔（每日 ${inside.size.toBigDecimal().divide(days.toBigDecimal(),2,java.math.RoundingMode.HALF_UP)}）；其余 $otherDays 日 ${outside.size} 笔（每日 ${outside.size.toBigDecimal().divide(otherDays.toBigDecimal(),2,java.math.RoundingMode.HALF_UP)}）。",
                        "按交易日期、同日数据库 ID 排序，不表示精确成交时序；频次按自然日计算，区间内包括所有交易。","核对连续亏损附近是否改变记录频次；缺少成交时间，不能推断行为原因。")
                }
                val inside=cn.filter { it.professional?.planStatus=="in" };val outside=cn.filter { it.professional?.planStatus=="out" }
                if(inside.isNotEmpty() && outside.isNotEmpty())add("plan-compare:${currency.id}","habits",50,"计划内外表现对照 · ${currency.id}",inside,outside,
                    "计划内 ${inside.size} 笔净额 ${Money.format(AdvancedAnalytics.performance(inside).netPnl,true)}；计划外 ${outside.size} 笔净额 ${Money.format(AdvancedAnalytics.performance(outside).netPnl,true)}。未知 ${cn.size-inside.size-outside.size} 笔。${if(minOf(inside.size,outside.size)<min) "子组样本不足，比较可能不稳定。"else "描述性对照。"}",
                    "本期同币种，计划内外各组独立汇总；此卡两组都来自本期，非前期对照。","核对交易理由，关注计划执行；不能据此认定某种行为导致盈亏。",bStart=start,bEnd=end)
            }
        }
        goals.filter { it.archivedDay==null && it.kind!="text" }.forEach { goal ->
            val p=ReviewPeriod.at(goal.period,LocalDate.ofEpochDay(end));val current=ReviewGoals.progress(goal,all,logs,p,today);val previousGoal=ReviewGoals.progress(goal,all,logs,p.shift(-1),today)
            val a=all.filter { it.date in p.start..minOf(p.end,today) };val b=all.filter { it.date in p.shift(-1).start..p.shift(-1).end }
            fun goalValue(v:BigDecimal)=if(goal.kind.endsWith("ratio"))v.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()+"%"else ProfessionalMath.plain(v)
            if(current.value!=null && previousGoal.value!=null && current.reached==false && (current.denominator ?: 0)>=min && (previousGoal.denominator ?: 0)>=min &&
                (if(goal.kind.endsWith("count"))current.value>=previousGoal.value else current.value<=previousGoal.value))
                add("goal:${goal.id}","discipline",82,"目标尚未改善：${goal.title}",a,b,
                    "上周期 ${goalValue(previousGoal.value)}（分母 ${previousGoal.denominator ?: "未知"}）；本周期 ${goalValue(current.value)}（分母 ${current.denominator ?: "未知"}）；阈值 ${goal.threshold}。",
                    "目标自己的周/月口径；${current.explanation}。目标生效前不计算；未知不计作成功。","查看目标历史，选一个具体检查项加入盘前清单。",aStart=p.start,aEnd=minOf(p.end,today),bStart=p.shift(-1).start,bEnd=p.shift(-1).end,aSample=current.denominator ?: 0,bSample=previousGoal.denominator ?: 0)
        }
        return out.distinctBy { it.id }.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.id })
    }
}
