package com.tradereview.app.v200

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val ZERO=BigDecimal.ZERO
fun decimal(value:String?)=BigDecimal(requireNotNull(value){"待估算金额不能作为零参与计算"})
fun exact(value:BigDecimal)=value.stripTrailingZeros().toPlainString()
fun exactOrNull(value:BigDecimal?)=value?.let(::exact)
/** A total is unknown if ANY constituent is unknown; never coerce missing fees to zero. */
fun completeTotal(values:Iterable<String?>):BigDecimal? {
    val list=values.toList();return if(list.any{it==null})null else sum(list.map(::decimal))
}
fun sum(values:Iterable<BigDecimal?>)=values.fold(ZERO){a,b->a+requireNotNull(b){"待估算金额不可作为零参与合计"}}
fun ratio(a:BigDecimal,b:BigDecimal)=a.divide(b,12,RoundingMode.HALF_EVEN)
class LedgerException(message:String):IllegalArgumentException(message)
fun ledgerRequire(test:Boolean,message:String){if(!test)throw LedgerException(message)}

data class MatchResult(val lots:List<LedgerLot>,val matches:List<LedgerMatch>,val cycles:List<LedgerCycle>)
/** Pure deterministic FIFO replay. Facts never change; generated IDs derive from execution IDs. */
object MatchingEngine {
    private data class Lot(val open:LedgerExecution,val cycle:String,var remaining:BigDecimal,var allocated:BigDecimal?=if(open.fees==null)null else ZERO)
    private fun direction(e:LedgerExecution)=if(e.effect=="open") {if(e.side=="buy")"long"else"short"}else if(e.side=="sell")"long"else"short"
    private fun key(e:LedgerExecution)=listOf(e.accountId,e.code,direction(e))
    fun timeBucket(e:LedgerExecution)=listOf(e.date.toString(),e.minute.toString(),e.accountId,e.code)
    fun uncertainTimes(values:List<LedgerExecution>)=values.filter{it.feeSource!=AutomaticEntry.SOURCE}.groupBy(::timeBucket).values.flatMap{group->
        if(group.size<=1)emptyList()else if(group.any{it.second==null})listOf(group)else group.groupBy{it.second}.values.filter{it.size>1}
    }
    fun ordered(values:List<LedgerExecution>):List<LedgerExecution>{
        val legacy=values.filter{it.feeSource!=AutomaticEntry.SOURCE}
        val automatic=values.filter{it.feeSource==AutomaticEntry.SOURCE}
        val unknown=legacy.groupBy(::timeBucket).filterValues{group->group.size>1 && group.any{it.second==null}}.keys
        return legacy.sortedWith(compareBy<LedgerExecution>{it.date}.thenBy{it.minute}.thenBy{if(timeBucket(it) in unknown)it.ordinal else (it.second?:0).toLong()}.thenBy{it.ordinal}.thenBy{it.id})+automatic.sortedWith(compareBy<LedgerExecution>{it.ordinal}.thenBy{it.id})
    }
    fun replay(executions:List<LedgerExecution>):MatchResult {
        ledgerRequire(executions.map{it.id}.distinct().size==executions.size,"成交ID重复")
        val automatic=executions.filter{it.feeSource==AutomaticEntry.SOURCE}
        ledgerRequire(automatic.all{it.ordinal>0} && automatic.map{it.ordinal}.distinct().size==automatic.size,"自动保存顺序冲突，未修改数据")
        val lots=mutableListOf<Lot>();val groups=mutableMapOf<List<String>,MutableList<Lot>>();val matches=mutableListOf<LedgerMatch>();val cycleFacts=linkedMapOf<String,MutableList<LedgerExecution>>()
        ordered(executions).forEach { e->
            LedgerValidation.execution(e)
            val qty=decimal(e.quantity);val fee=e.fees?.let(::decimal)
            if(e.effect=="open") {
                val group=groups.getOrPut(key(e)){mutableListOf()};val cycle=group.firstOrNull()?.cycle?:"cycle_${e.id}"
                val lot=Lot(e,cycle,qty);lots+=lot;group+=lot;cycleFacts.getOrPut(cycle){mutableListOf()}.add(e)
            } else {
                val group=groups[key(e)].orEmpty();val eligible=group.filter { it.remaining>ZERO && when {
                    e.accountId=="stock"->it.open.tradingDay<e.tradingDay
                    e.effect=="close_today"->it.open.tradingDay==e.tradingDay
                    e.effect=="close_yesterday"->it.open.tradingDay<e.tradingDay
                    else->if(e.feeSource==AutomaticEntry.SOURCE)it.open.date<=e.date else it.open.tradingDay<=e.tradingDay
                } }
                ledgerRequire(sum(eligible.map{it.remaining})>=qty,if(e.accountId=="stock")"可卖数量不足：普通A股T+1，当日买入不可当日卖出；未知期初持仓请补录真实买入"else"符合平今/平昨条件的持仓不足；禁止过量平仓，未知期初持仓须补录真实开仓")
                ledgerRequire(e.accountId=="stock" || eligible.takeWhileCumulative(qty).all{decimal(it.open.multiplier!!).compareTo(decimal(e.multiplier!!))==0},"开平合约乘数不一致，请核对真实历史参数")
                var remaining=qty;var closeAllocated=ZERO;var closedQty=ZERO
                for(lot in eligible) {
                    if(remaining.signum()==0)break
                    val amount=remaining.min(lot.remaining);val original=decimal(lot.open.quantity)
                    val totalClosed=original-lot.remaining+amount
                    val cumulative=lot.open.fees?.let{if(totalClosed.compareTo(original)==0)decimal(it) else ratio(decimal(it)*totalClosed,original)}
                    val openingFee=cumulative?.let{it-requireNotNull(lot.allocated)}
                    closedQty+=amount
                    val cumulativeClose=fee?.let{if(closedQty.compareTo(qty)==0)it else ratio(it*closedQty,qty)}
                    val closingFee=cumulativeClose?.minus(closeAllocated)
                    val priceDifference=if(direction(e)=="long")decimal(e.price)-decimal(lot.open.price)else decimal(lot.open.price)-decimal(e.price)
                    val gross=priceDifference*amount*(if(e.accountId=="stock")BigDecimal.ONE else decimal(requireNotNull(lot.open.multiplier)))
                    matches+=LedgerMatch("match_${e.id}_${lot.open.id}",lot.open.id,e.id,lot.cycle,exact(amount),exact(gross),exactOrNull(openingFee),exactOrNull(closingFee),if(openingFee!=null && closingFee!=null)exact(gross-openingFee-closingFee)else null,e.tradingDay)
                    lot.remaining-=amount;lot.allocated=cumulative;remaining-=amount;closeAllocated=cumulativeClose?:closeAllocated
                    cycleFacts.getValue(lot.cycle).let{if(it.none{fact->fact.id==e.id})it.add(e)}
                }
                groups[key(e)]?.removeAll{it.remaining.signum()==0}
            }
        }
        val lotsByCycle=lots.groupBy{it.cycle};val matchesByCycle=matches.groupBy{it.cycleId}
        val cycles=cycleFacts.map { (id,facts)->
            val own=lotsByCycle.getValue(id);val open=own.filter{it.remaining>ZERO};val remaining=sum(open.map{it.remaining})
            val costs=sum(open.map{decimal(it.open.price)*it.remaining});val average=if(remaining>ZERO)ratio(costs,remaining)else ratio(sum(own.map{decimal(it.open.price)*decimal(it.open.quantity)}),sum(own.map{decimal(it.open.quantity)}))
            val margin=if(open.isNotEmpty() && open.all{it.open.parametersConfirmed && it.open.multiplier!=null && it.open.marginRate!=null})sum(open.map{decimal(it.open.price)*it.remaining*decimal(it.open.multiplier!!)*decimal(it.open.marginRate!!)})else null
            val last=facts.last();val first=facts.first()
            LedgerCycle(id,first.accountId,first.code,first.name,direction(first),first.date,if(remaining.signum()==0)last.date else null,exact(remaining),exact(average),exactOrNull(completeTotal(open.map{lot->lot.open.fees?.let{exact(decimal(it)-requireNotNull(lot.allocated))}})?.plus(costs)),exactOrNull(completeTotal(facts.map{it.fees})),exactOrNull(completeTotal(matchesByCycle[id].orEmpty().map{it.net})),facts.lastOrNull{it.takeProfit!=null}?.takeProfit,facts.lastOrNull{it.stopLoss!=null}?.stopLoss,margin?.let(::exact))
        }
        return MatchResult(lots.map{LedgerLot("lot_${it.open.id}",it.open.id,it.cycle,it.open.quantity,exact(it.remaining),exactOrNull(it.allocated))}.sortedBy{it.id},matches.sortedBy{it.id},cycles.sortedBy{it.id})
    }
    private fun List<Lot>.takeWhileCumulative(qty:BigDecimal):List<Lot>{var left=qty;return filter{if(left>ZERO){left-=it.remaining;true}else false}}
    fun available(executions:List<LedgerExecution>,account:String,code:String,side:String,day:Long,effect:String):BigDecimal {
        val result=replay(executions);val facts=executions.associateBy{it.id};val dir=if(side=="sell")"long"else"short"
        return sum(result.lots.filter{l->val e=facts.getValue(l.executionId);e.accountId==account && e.code==code && direction(e)==dir && when{account=="stock"->e.tradingDay<day;effect=="close_today"->e.tradingDay==day;effect=="close_yesterday"->e.tradingDay<day;else->e.tradingDay<=day}}.map{decimal(it.remaining)})
    }
}

data class RiskDecision(val amount:String?,val fund:String?,val percent:String?,val limit:String?,val exceeded:Boolean)
object LedgerRisk {
    fun evaluate(e:LedgerExecution,config:MarketRisk):RiskDecision {
        val fund=config.riskFund?.let(::decimal);val entry=decimal(e.price)
        val amount=if(e.effect!="open" || e.stopLoss==null || (e.accountId=="futures" && (!e.parametersConfirmed || e.multiplier==null)))null else {
            val stop=decimal(e.stopLoss)
            ledgerRequire(if(e.side=="buy")stop<entry else stop>entry,"做多止损应低于入场价，做空止损应高于入场价")
            (entry-stop).abs()*decimal(e.quantity)*(if(e.accountId=="stock")BigDecimal.ONE else decimal(e.multiplier!!))
        }
        val limit=when(config.limitMode){"percent"->if(fund!=null && config.limitValue!=null)(fund*decimal(config.limitValue)).movePointLeft(2)else config.defaultLimit?.let(::decimal);else->config.limitValue?.let(::decimal)?:config.defaultLimit?.let(::decimal)}
        return RiskDecision(amount?.let(::exact),fund?.let(::exact),if(amount!=null && fund!=null)exact(ratio(amount*BigDecimal(100),fund))else null,limit?.let(::exact),amount!=null && limit!=null && amount>limit)
    }
}

object LedgerFees {
    fun stock(price:String,quantity:String,rate:String)=exact(decimal(price)*decimal(quantity)*decimal(rate))
    private fun cost(rule:LedgerContractRule,e:LedgerExecution,q:BigDecimal,rate:String?):BigDecimal {
        ledgerRequire(rate!=null && rule.feeMode!="unknown","本次开平手续费规则缺失")
        val turnover=decimal(e.price)*q*decimal(requireNotNull(rule.multiplier))
        val base=if(rule.feeMode=="per_lot")decimal(rate!!)*q else decimal(rate!!)*turnover
        val additional=when(rule.addMode){"per_lot"->decimal(requireNotNull(rule.addValue))*q;"turnover_rate"->decimal(requireNotNull(rule.addValue))*turnover;else->ZERO}
        return base+additional
    }
    fun fromMatches(rule:LedgerContractRule,e:LedgerExecution,matches:List<LedgerMatch>,facts:Map<String,LedgerExecution>):String? {
        LedgerValidation.rule(rule);ledgerRequire(rule.effectiveDay<=e.tradingDay,"规则生效日晚于成交交易日，请核实规则或录入实际费用");ledgerRequire(rule.verified && rule.multiplier!=null,"合约参数未核实，不能自动推算手续费")
        ledgerRequire(e.feeSource!=AutomaticEntry.SOURCE || rule.addMode=="per_execution" && rule.addValue?.let{decimal(it).compareTo(decimal("0.01"))==0}==true,"自动手续费快照须为整笔加收0.01元")
        if(e.effect=="open")return rule.openFee?.let{finish(rule,e,cost(rule,e,decimal(e.quantity),it))}
        if(matches.any{m->val o=facts.getValue(m.openExecutionId);(if(if(e.feeSource==AutomaticEntry.SOURCE)o.date==e.date else o.tradingDay==e.tradingDay)rule.todayFee else rule.closeFee)==null})return null
        return finish(rule,e,sum(matches.map{m->val open=facts.getValue(m.openExecutionId);cost(rule,e,decimal(m.quantity),if(if(e.feeSource==AutomaticEntry.SOURCE)open.date==e.date else open.tradingDay==e.tradingDay)rule.todayFee else rule.closeFee)}))
    }
    private fun finish(rule:LedgerContractRule,e:LedgerExecution,base:BigDecimal):String = if(e.feeSource==AutomaticEntry.SOURCE)AutomaticEntry.cents(base+decimal("0.01")) else if(rule.addMode=="per_execution")exact(base+decimal(requireNotNull(rule.addValue))) else exact(base)
    fun futures(rule:LedgerContractRule,e:LedgerExecution,prior:List<LedgerExecution>):String? {
        val matches=if(e.effect=="open")emptyList()else MatchingEngine.replay(prior+e.copy(fees=if(e.feeSource==AutomaticEntry.SOURCE)null else "0")).matches.filter{it.closeExecutionId==e.id}
        return fromMatches(rule,e,matches,prior.associateBy{it.id})
    }

}

data class ClosedEvent(val execution:LedgerExecution,val net:BigDecimal?)
object LedgerStatistics {
    fun closed(state:LedgerSnapshot):List<ClosedEvent> {val facts=state.executions.associateBy{it.id};return state.matches.groupBy{it.closeExecutionId}.map{(id,m)->ClosedEvent(facts.getValue(id),completeTotal(m.map{it.net}))}}
    fun days(events:List<ClosedEvent>)=events.filter{it.net!=null}.groupBy{it.execution.tradingDay}.mapValues{(_,v)->sum(v.map{requireNotNull(it.net)})}
    fun cash(state:LedgerSnapshot,account:LedgerAccount):String? {
        if(account.market!="stock" || account.initialCapital==null || !account.historyComplete || state.executions.any{it.accountId==account.id && it.fees==null})return null
        val flows=sum(state.cashFlows.filter{it.accountId==account.id}.map{decimal(it.amount)*if(it.kind=="in")BigDecimal.ONE else BigDecimal.ONE.negate()})
        val movements=sum(state.executions.filter{it.accountId==account.id}.map{decimal(it.price)*decimal(it.quantity)*if(it.side=="sell")BigDecimal.ONE else BigDecimal.ONE.negate()})
        return exact(decimal(account.initialCapital)+flows+movements-sum(state.executions.filter{it.accountId==account.id}.map{decimal(it.fees)}))
    }
}
