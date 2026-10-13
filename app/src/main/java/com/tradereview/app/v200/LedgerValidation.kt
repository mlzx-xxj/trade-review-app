package com.tradereview.app.v200

import com.google.gson.*
import java.math.BigDecimal
import java.time.LocalDate

data class KeyPrice(val label:String,val price:String)
object LedgerJson {
    private val gson=GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    fun canonical(value:JsonElement):JsonElement=when {
        value.isJsonObject->JsonObject().apply{value.asJsonObject.keySet().sorted().forEach{add(it,canonical(value.asJsonObject[it]))}}
        value.isJsonArray->JsonArray().apply{value.asJsonArray.forEach{add(canonical(it))}}
        else->value
    }
    fun element(value:Any):JsonElement=canonical(gson.toJsonTree(value))
    fun text(value:Any)=gson.toJson(element(value))
    fun parse(input:JsonElement):LedgerSnapshot {
        val parsed=gson.fromJson(input,LedgerSnapshot::class.java)
        ledgerRequire(element(parsed)==canonical(input),"成交账本字段缺失、类型错误或包含未知字段")
        LedgerValidation.snapshot(parsed)
        return parsed
    }
    fun rule(text:String):LedgerContractRule {
        val input=com.tradereview.app.backup.BackupCodec.strictObject(text)
        val value=gson.fromJson(input,LedgerContractRule::class.java)
        ledgerRequire(element(value)==canonical(input),"合约快照格式错误")
        LedgerValidation.rule(value);return value
    }
    fun execution(text:String):LedgerExecution {val input=com.tradereview.app.backup.BackupCodec.strictObject(text);val value=gson.fromJson(input,LedgerExecution::class.java);ledgerRequire(element(value)==canonical(input),"成交修订事实格式无效");LedgerValidation.execution(value);return value}
    fun levels(text:String):List<KeyPrice> {
        val wrapped=com.tradereview.app.backup.BackupCodec.strictObject("{\"levels\":$text}")
        ledgerRequire(wrapped["levels"].isJsonArray && wrapped["levels"].asJsonArray.size()<=30,"关键价位最多30项")
        return wrapped["levels"].asJsonArray.map{e->
            val v=gson.fromJson(e,KeyPrice::class.java);ledgerRequire(element(v)==canonical(e),"关键价位字段错误")
            LedgerValidation.word(v.label,40);LedgerValidation.number(v.price,true);v
        }
    }
}

object LedgerValidation {
    private val first=LocalDate.of(1900,1,1).toEpochDay();private val last=LocalDate.of(2100,12,31).toEpochDay()
    fun id(v:String){ledgerRequire(Regex("[A-Za-z0-9_]{1,80}").matches(v),"稳定ID无效")}
    fun word(v:String,max:Int=60){ledgerRequire(v.isNotBlank() && v.length<=max && '\u0000' !in v,"名称无效")}
    fun note(v:String){ledgerRequire(v.length<=1000 && '\u0000' !in v,"备注最多1000字")}
    fun day(v:Long){ledgerRequire(v in first..last,"日期范围无效")}
    fun number(v:String,positive:Boolean=false,max:BigDecimal=BigDecimal("1000000000000000")) {
        ledgerRequire(Regex("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,12})?").matches(v),"金额、价格或数量必须为非负精确数值，最多12位小数")
        val n=decimal(v);ledgerRequire(n<=max && (!positive || n>BigDecimal.ZERO),"数值范围无效")
    }
    private fun optional(v:String?,positive:Boolean=false){v?.let{number(it,positive)}}
    fun code(account:String,code:String) {
        ledgerRequire(account in listOf("stock","futures"),"仅支持A股与国内期货账户")
        ledgerRequire(if(account=="stock")Regex("(?:60[0135]|00[0123]|30[01]|688)[0-9]{3}").matches(code)else Regex("[a-z]{1,4}[0-9]{3,4}").matches(code),if(account=="stock")"仅支持普通A股六位代码；不支持ETF、债券、B股和T+0品种"else"合约代码应包含品种与到期年月，如rb2701（不自动推测合约参数）")
    }
    fun account(a:LedgerAccount){ledgerRequire(a.id==a.market && a.id in listOf("stock","futures"),"账户身份无效");word(a.name);optional(a.initialCapital);number(a.buyRate);number(a.sellRate);ledgerRequire(decimal(a.buyRate)<=BigDecimal.ONE && decimal(a.sellRate)<=BigDecimal.ONE,"股票综合费率应在0至1之间")}
    fun strategy(s:LedgerStrategy){id(s.id);word(s.name);note(s.description);ledgerRequire(s.sortOrder in 0..100000,"策略排序范围无效")}
    fun risk(r:MarketRisk){ledgerRequire(r.id in listOf("stock","futures") && r.limitMode in listOf("amount","percent"),"风险配置无效");optional(r.riskFund,true);optional(r.limitValue,true);optional(r.defaultLimit,true);if(r.limitMode=="percent" && r.limitValue!=null)ledgerRequire(decimal(r.limitValue)<=BigDecimal(100),"风险金比例应大于0且不超过100%")}
    fun rule(r:LedgerContractRule){id(r.id);code("futures",r.code);word(r.name);day(r.effectiveDay);optional(r.multiplier,true);optional(r.marginRate);ledgerRequire(r.marginRate==null || decimal(r.marginRate)<=BigDecimal.ONE,"保证金率应在0至1之间");ledgerRequire(r.feeMode in listOf("per_lot","turnover_rate") && r.addMode in listOf("none","per_lot","turnover_rate"),"必须明确按手金额或成交额费率，不能猜测+0.01单位");listOf(r.openFee,r.closeFee,r.todayFee,r.addValue).forEach{optional(it)};ledgerRequire(r.addMode=="none" && r.addValue==null || r.addMode!="none" && r.addValue!=null,"手续费加收单位及金额不完整");if(r.feeMode=="turnover_rate")listOf(r.openFee,r.closeFee,r.todayFee).forEach{ledgerRequire(it==null || decimal(it)<=BigDecimal.ONE,"成交额费率应在0至1之间")};if(r.addMode=="turnover_rate")ledgerRequire(decimal(r.addValue!!)<=BigDecimal.ONE,"加收费率应在0至1之间");if(r.verified)ledgerRequire(r.multiplier!=null,"核实的合约规则必须包含乘数")}
    fun execution(e:LedgerExecution) {
        id(e.id);code(e.accountId,e.code);word(e.name);day(e.date);day(e.tradingDay)
        ledgerRequire(e.minute in 0..1439 && e.ordinal in 0..Long.MAX_VALUE,"成交时间/顺序无效")
        ledgerRequire(e.side in listOf("buy","sell") && e.effect in listOf("open","close","close_today","close_yesterday"),"买卖及开平选项无效")
        if(e.accountId=="stock")ledgerRequire(e.tradingDay==e.date && (e.side=="buy" && e.effect=="open" || e.side=="sell" && e.effect=="close"),"A股只支持买入/卖出，不允许卖空或期货开平类型")
        else ledgerRequire(e.tradingDay in e.date..(e.date+7),"夜盘交易日需人工确认，与自然日期相隔不超过七天")
        number(e.price,true);number(e.quantity,true,BigDecimal("1000000000"));ledgerRequire(decimal(e.quantity).stripTrailingZeros().scale()<=0,"数量必须为整数股或整数手")
        number(e.fees);optional(e.feeRateSnapshot);ledgerRequire(e.feeSource in listOf("actual","rule"),"费用来源无效")
        optional(e.multiplier,true);optional(e.marginRate);ledgerRequire(e.marginRate==null || decimal(e.marginRate)<=BigDecimal.ONE,"保证金率无效")
        if(e.accountId=="futures")ledgerRequire(e.parametersConfirmed && e.multiplier!=null,"期货合约乘数未核实；请先配置或确认本笔参数，不能虚构已实现盈亏")
        e.ruleSnapshot?.let{ledgerRequire(it.length<=4096,"参数快照超长");LedgerJson.rule(it)}
        listOf(e.strategyId,e.planId).forEach{it?.let(::id)}
        listOf(e.takeProfit,e.stopLoss).forEach{optional(it,true)}
        listOf(e.riskAmount,e.riskFundSnapshot,e.riskLimitSnapshot).forEach{optional(it,true)}
        ledgerRequire(!e.riskExceeded || e.riskAmount!=null && e.riskLimitSnapshot!=null && decimal(e.riskAmount)>decimal(e.riskLimitSnapshot),"风险超限快照不一致")
        if(e.effect=="open" && e.stopLoss!=null){val amount=(decimal(e.price)-decimal(e.stopLoss)).abs()*decimal(e.quantity)*(if(e.accountId=="stock")BigDecimal.ONE else decimal(e.multiplier!!));ledgerRequire(if(e.side=="buy")decimal(e.stopLoss)<decimal(e.price)else decimal(e.stopLoss)>decimal(e.price),"止损方向无效");e.riskAmount?.let{ledgerRequire(decimal(it).compareTo(amount)==0,"风险快照与止损价格不一致")}}
    }
    fun plan(p:LedgerPlan){id(p.id);code(p.accountId,p.code);word(p.name);day(p.day);p.strategyId?.let(::id);listOf(p.entryPrice,p.takeProfit,p.stopLoss).forEach{optional(it,true)};LedgerJson.levels(p.levels);note(p.note)}
    fun review(r:LedgerReview){id(r.id);id(r.cycleId);day(r.day);listOf(r.takeProfit,r.stopLoss).forEach{optional(it,true)};LedgerJson.levels(r.levels);note(r.note)}
    fun cash(f:LedgerCashFlow){id(f.id);ledgerRequire(f.accountId in listOf("stock","futures") && f.kind in listOf("in","out"),"出入金类型无效");day(f.day);number(f.amount,true);note(f.note)}
    fun snapshot(s:LedgerSnapshot) {
        ledgerRequire(s.accounts.map{it.id}.toSet()==setOf("stock","futures") && s.accounts.size==2 && s.risks.map{it.id}.toSet()==setOf("stock","futures") && s.risks.size==2,"备份须包含两账户和两套风险配置")
        fun <T> unique(list:List<T>,key:(T)->String){ledgerRequire(list.size<=10000 && list.map(key).distinct().size==list.size,"数据过多或稳定ID重复")}
        unique(s.strategies){it.id};unique(s.rules){it.id};unique(s.plans){it.id};unique(s.executions){it.id};unique(s.cycles){it.id};unique(s.lots){it.id};unique(s.matches){it.id};unique(s.reviews){it.id};unique(s.cashFlows){it.id};unique(s.audits){it.id}
        s.accounts.forEach(::account);s.risks.forEach(::risk);s.strategies.forEach(::strategy);s.rules.forEach(::rule);s.plans.forEach(::plan);s.reviews.forEach(::review);s.cashFlows.forEach(::cash)
        val strategies=s.strategies.map{it.id}.toSet();val plans=s.plans.associateBy{it.id}
        s.plans.forEach{ledgerRequire(it.strategyId==null || it.strategyId in strategies,"计划策略不存在")}
        s.executions.forEach{e->execution(e);ledgerRequire(!e.riskExceeded || !e.riskWarningEnabled || e.riskConfirmed,"超风险真实成交需要二次确认");ledgerRequire(e.strategyId==null || e.strategyId in strategies,"成交策略不存在");e.planId?.let{p->ledgerRequire(plans[p]?.let{it.accountId==e.accountId && it.code==e.code}==true,"关联计划的账户或标的不一致")}}
        val replay=MatchingEngine.replay(s.executions)
        ledgerRequire(s.lots.sortedBy{it.id}==replay.lots && s.matches.sortedBy{it.id}==replay.matches && s.cycles.sortedBy{it.id}==replay.cycles,"持仓/匹配缓存与真实成交不一致，拒绝恢复")
        s.audits.forEach{a->id(a.id);id(a.executionId);ledgerRequire(a.operation in listOf("edit","delete") && a.createdAt in 0..4133980799999L,"修订信息无效");ledgerRequire(LedgerJson.execution(a.beforeJson).id==a.executionId,"修订事实ID不一致");if(a.operation=="delete")ledgerRequire(a.afterJson==null,"删除修订不应含新成交");else ledgerRequire(a.afterJson!=null && LedgerJson.execution(a.afterJson).id==a.executionId,"编辑修订缺少相同ID事实")}
        val cycles=s.cycles.map{it.id}.toSet();s.reviews.forEach{ledgerRequire(it.cycleId in cycles,"复盘关联持仓不存在")}
    }
}
