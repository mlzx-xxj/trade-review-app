package com.tradereview.app.v200

import com.tradereview.app.backup.TradeCsv
import java.time.LocalDate
object LedgerCsv {
    private fun safe(value:String)=if(value.trimStart().firstOrNull() in listOf('=','+','-','@'))"'$value"else value
    fun encode(state:LedgerSnapshot)=buildString {
        append('\uFEFF');append(TradeCsv.row(listOf("成交稳定ID","账户","代码","名称","自然日期","时间","成交秒（空为未知）","同时间成交序号","交易日","买卖","开平","成交价格","成交数量","合约乘数","手续费","费用来源","参数快照","策略稳定ID","策略名称","人工关联计划","计划止盈","计划止损","风险金额","当时风险金","当时单笔上限","风险超限","已确认超风险","已实现毛盈亏","已实现净盈亏","币种")))
        val byClose=state.matches.groupBy{it.closeExecutionId}
        MatchingEngine.ordered(state.executions).forEach{e->val matches=byClose[e.id].orEmpty();append(TradeCsv.row(listOf(e.id,state.accounts.first{it.id==e.accountId}.name,e.code,safe(e.name),LocalDate.ofEpochDay(e.date).toString(),"%02d:%02d".format(e.minute/60,e.minute%60),e.second?.toString().orEmpty(),e.ordinal.toString(),LocalDate.ofEpochDay(e.tradingDay).toString(),if(e.side=="buy")"买入"else"卖出",if(e.accountId=="stock")""else when(e.effect){"open"->"开仓";"close_today"->"平今";"close_yesterday"->"平昨";else->"平仓"},e.price,e.quantity,e.multiplier.orEmpty(),e.fees?:"待估算",if(e.fees==null)"费用待估算"else if(e.feeSource=="actual")"实际金额"else"当笔确认规则",safe(e.ruleSnapshot.orEmpty()),e.strategyId.orEmpty(),safe(strategyLabel(state,e.strategyId)),e.planId.orEmpty(),e.takeProfit.orEmpty(),e.stopLoss.orEmpty(),e.riskAmount.orEmpty(),e.riskFundSnapshot.orEmpty(),e.riskLimitSnapshot.orEmpty(),if(e.riskExceeded)"是"else"否",if(e.riskExceeded && e.riskConfirmed)"是"else"否",if(matches.isEmpty())""else exact(sum(matches.map{decimal(it.gross)})),if(matches.isEmpty())""else exactOrNull(completeTotal(matches.map{it.net}))?:"待计算","CNY")))}
    }
}
