package com.tradereview.app.v200

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** `auto` facts reuse the persisted ordinal; legacy facts keep their original replay order. */
object AutomaticEntry {
    const val SOURCE = "auto"
    const val STOCK_BUY_RATE = "0.0001"
    const val STOCK_SELL_RATE = "0.0006"
    val beijing: ZoneId = ZoneId.of("Asia/Shanghai")
    fun now(clock:Clock):ZonedDateTime = clock.instant().atZone(beijing)
    fun today()=now(Clock.systemUTC()).toLocalDate()
    fun cents(value:BigDecimal):String = value.setScale(2,RoundingMode.HALF_UP).toPlainString()
    fun money(value:String?):String = value?.let { "¥"+DecimalFormat("#,##0.00",DecimalFormatSymbols(Locale.CHINA)).apply{roundingMode=RoundingMode.HALF_UP}.format(decimal(it)) } ?: "暂无法估算"
    fun stockFee(e:LedgerExecution):String = cents(decimal(e.price)*decimal(e.quantity)*decimal(requireNotNull(e.feeRateSnapshot)))

    /** Same preparation is used for the live quote and persisted facts. No current prices. */
    fun prepare(input:LedgerExecution,existing:LedgerExecution?,prior:List<LedgerExecution>,rules:List<LedgerContractRule>):LedgerExecution {
        LedgerValidation.number(input.price,true);LedgerValidation.number(input.quantity,true)
        ledgerRequire(decimal(input.quantity).stripTrailingZeros().scale()<=0,"数量必须为整数股或整数手")
        if(input.accountId=="stock") {
            val rate=existing?.feeRateSnapshot?.takeIf{existing.side==input.side}
                ?:if(input.side=="buy")STOCK_BUY_RATE else STOCK_SELL_RATE
            val e=input.copy(feeRateSnapshot=rate)
            return e.copy(fees=stockFee(e))
        }
        val saved=input.ruleSnapshot?.let(LedgerJson::rule)?.takeIf{it.code==input.code}
        val selected=saved ?: rules.filter{it.code==input.code && it.verified && it.effectiveDay<=input.date}
            .maxWithOrNull(compareBy<LedgerContractRule>{it.effectiveDay}.thenBy{it.id})
            ?: FuturesReferenceCatalog.rule(input.code,input.date)
            ?:throw LedgerException("该品种合约乘数尚未核实，暂不能保存；未编造持仓或盈亏")
        val rule=selected.copy(addMode="per_execution",addValue="0.01")
        ledgerRequire(rule.multiplier!=null && rule.verified,"缺少已核实合约乘数，暂无法估算")
        val e=input.copy(multiplier=rule.multiplier,marginRate=rule.marginRate,parametersConfirmed=true,ruleSnapshot=LedgerJson.text(rule))
        return e.copy(fees=LedgerFees.futures(rule,e,prior))
    }

    data class Estimate(val fees:String?,val margin:String?,val marginLabel:String,val problem:String?=null,val ready:Boolean=false)
    fun estimate(input:LedgerExecution?,state:LedgerSnapshot):Estimate {
        if(input==null || input.price.isBlank() || input.quantity.isBlank())return Estimate(null,null,"预计保证金占用")
        return try {
            val previous=state.executions.firstOrNull{it.id==input.id}
            val prior=state.executions.filter{it.id!=input.id}
            val orderedInput=if(input.feeSource==SOURCE && previous==null)input.copy(ordinal=(state.executions.maxOfOrNull{it.ordinal}?:0)+1)else input
            val e=when {
                input.feeSource==SOURCE->prepare(orderedInput,previous,prior,state.rules)
                input.feeSource=="actual"->input
                input.accountId=="stock"->input.copy(fees=LedgerFees.stock(input.price,input.quantity,requireNotNull(input.feeRateSnapshot)))
                else->input.copy(fees=LedgerFees.futures(LedgerJson.rule(requireNotNull(input.ruleSnapshot)),input,prior))
            }
            val margin=if(e.accountId!="futures")null else if(e.effect=="open")
                e.marginRate?.let{cents(decimal(e.price)*decimal(e.quantity)*decimal(e.multiplier!!)*decimal(it))}
            else {
                val after=MatchingEngine.replay(prior+e)
                val own=after.cycles.filter{it.accountId==e.accountId && it.code==e.code && decimal(it.remaining)>BigDecimal.ZERO}
                if(own.all{it.referenceMargin!=null})cents(sum(own.map{decimal(it.referenceMargin!!)})) else null
            }
            Estimate(e.fees,margin,if(e.effect=="open")"预计新增保证金占用"else"平仓后预计剩余占用",ready=true)
        } catch(e:IllegalArgumentException) { Estimate(null,null,"预计保证金占用",e.message) }
    }
}
