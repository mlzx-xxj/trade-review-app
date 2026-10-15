package com.tradereview.app.v200

import androidx.room.withTransaction
import com.tradereview.app.data.TradeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

data class LedgerChange(val before:LedgerSnapshot,val after:LedgerSnapshot,val executionId:String?,val deleting:Boolean,val risk:RiskDecision?,val impact:String,val orderWarning:Boolean=false,val affectsHistory:Boolean=false)
class LedgerRepository(private val db:TradeDatabase) {
    private val dao get()=db.ledger()
    suspend fun read()=LedgerSnapshot(dao.accounts(),dao.strategies(),dao.risks(),dao.rules(),dao.plans(),dao.executions(),dao.cycles(),dao.lots(),dao.matches(),dao.reviews(),dao.cashFlows(),dao.audits())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe()=dao.observeChanges().mapLatest{db.withTransaction{read()}}.flowOn(Dispatchers.IO)
    suspend fun preview(e:LedgerExecution?,deleteId:String?=null):LedgerChange=withContext(Dispatchers.Default) {
        val before=db.withTransaction{read()};val id=e?.id?:requireNotNull(deleteId)
        val existing=before.executions.firstOrNull{it.id==id}
        ledgerRequire(e!=null || existing!=null,"成交不存在")
        val prior=before.executions.filter{it.id!=id}
        val candidate=e?.let { input->
            val account=before.accounts.firstOrNull{it.id==input.accountId}?:throw LedgerException("账户不存在")
            ledgerRequire(input.strategyId==null || before.strategies.any{it.id==input.strategyId && (it.active || existing?.strategyId==it.id)},"策略已停用，请选择有效策略")
            val auditHigh=before.audits.maxOfOrNull{maxOf(LedgerJson.execution(it.beforeJson).ordinal,it.afterJson?.let(LedgerJson::execution)?.ordinal?:0)}?:0
            val lastOrdinal=maxOf(before.executions.maxOfOrNull{it.ordinal}?:0,auditHigh)
            ledgerRequire(existing!=null || lastOrdinal<Long.MAX_VALUE,"保存顺序已到上限，未修改数据")
            val ordinal=existing?.ordinal?:if(input.feeSource==AutomaticEntry.SOURCE)lastOrdinal+1 else if(input.ordinal>0)input.ordinal else lastOrdinal+1
            LedgerValidation.number(input.price,true);LedgerValidation.number(input.quantity,true);input.stopLoss?.let{LedgerValidation.number(it,true)};input.multiplier?.let{LedgerValidation.number(it,true)};input.marginRate?.let{LedgerValidation.number(it)}
            var prepared=input.copy(ordinal=ordinal)
            if(existing!=null)prepared=prepared.copy(date=existing.date,minute=existing.minute,second=existing.second,tradingDay=existing.tradingDay,feeSource=existing.feeSource)
            if(prepared.feeSource==AutomaticEntry.SOURCE)prepared=AutomaticEntry.prepare(prepared,existing,prior,before.rules)
            if(prepared.feeSource=="rule") {
                if(input.accountId=="stock") {
                    val rate=input.feeRateSnapshot?:if(input.side=="buy")account.buyRate else account.sellRate
                    prepared=prepared.copy(fees=LedgerFees.stock(input.price,input.quantity,rate),feeRateSnapshot=rate)
                } else {
                    val rule=input.ruleSnapshot?.let(LedgerJson::rule)?:before.rules.filter{it.code==input.code && it.effectiveDay<=input.tradingDay}.maxWithOrNull(compareBy<LedgerContractRule>{it.effectiveDay}.thenBy{it.id})?:throw LedgerException("先核实合约规则，或逐笔填写实际费用和已确认乘数")
                    ledgerRequire(rule.verified,"手续费规则未核实，请先在合约参数确认或录入实际费用")
                    val changedMultiplier=input.multiplier!=null && (rule.multiplier==null || decimal(input.multiplier).compareTo(decimal(rule.multiplier))!=0)
                    val changedMargin=input.marginRate!=null && (rule.marginRate==null || decimal(input.marginRate).compareTo(decimal(rule.marginRate))!=0)
                    ledgerRequire(!(changedMultiplier || changedMargin) || input.parametersConfirmed,"本笔修改的合约参数尚未核实")
                    val effective=rule.copy(multiplier=input.multiplier?:rule.multiplier,marginRate=input.marginRate?:rule.marginRate)
                    prepared=prepared.copy(multiplier=effective.multiplier,marginRate=effective.marginRate,parametersConfirmed=effective.verified,ruleSnapshot=LedgerJson.text(effective),fees=LedgerFees.futures(effective,prepared.copy(multiplier=effective.multiplier,parametersConfirmed=effective.verified),prior))
                }
            }
            val config=before.risks.firstOrNull{it.id==input.accountId}?:MarketRisk(input.accountId)
            val risk=LedgerRisk.evaluate(prepared,config)
            prepared.copy(orderConfirmed=false,riskAmount=risk.amount,riskFundSnapshot=risk.fund,riskLimitSnapshot=risk.limit,riskExceeded=risk.exceeded,riskWarningEnabled=config.warnEnabled,riskConfirmed=if(existing==input)existing?.riskConfirmed?:false else false)
        }
        val originalFacts=(prior+listOfNotNull(candidate)).sortedBy{it.id}
        MatchingEngine.uncertainTimes(originalFacts).forEach{group->ledgerRequire(group.map{it.ordinal}.distinct().size==group.size,"历史成交顺序冲突；未修改记录")}
        val initialResult=MatchingEngine.replay(originalFacts)
        val byClose=initialResult.matches.groupBy{it.closeExecutionId};val byId=originalFacts.associateBy{it.id}
        // Explicit history edits may change today/yesterday allocation. Reprice only
        // automatic closes using THEIR saved snapshot, never the updated catalog.
        val facts=originalFacts.map{fact->if(fact.feeSource==AutomaticEntry.SOURCE && fact.accountId=="futures" && fact.effect!="open")fact.copy(fees=LedgerFees.fromMatches(LedgerJson.rule(requireNotNull(fact.ruleSnapshot)),fact,byClose[fact.id].orEmpty(),byId))else fact}
        val result=if(facts==originalFacts)initialResult else MatchingEngine.replay(facts)
        val repriced=before.executions.mapNotNull{old->facts.firstOrNull{it.id==old.id && it.id!=id && it.fees!=old.fees}?.let{fresh->ExecutionAudit(com.tradereview.app.data.newKey(),old.id,"edit",LedgerJson.text(old),LedgerJson.text(fresh),System.currentTimeMillis())}}
        ledgerRequire(before.reviews.all{r->result.cycles.any{it.id==r.cycleId}},"此次更改会使已有持仓复盘失去关联，请先处理该复盘；未修改任何成交")
        val audit=existing?.takeIf{it!=candidate}?.let{ExecutionAudit(com.tradereview.app.data.newKey(),it.id,if(candidate==null)"delete"else"edit",LedgerJson.text(it),candidate?.let(LedgerJson::text),System.currentTimeMillis())}
        val after=before.copy(executions=facts,lots=result.lots,matches=result.matches,cycles=result.cycles,audits=before.audits+listOfNotNull(audit)+repriced)
        val oldNet=sum(before.matches.map{decimal(it.net)});val newNet=sum(after.matches.map{decimal(it.net)})
        val changed=before.cycles.count{old->after.cycles.firstOrNull{it.id==old.id}!=old}+after.cycles.count{new->before.cycles.none{it.id==new.id}}
        val byExecution=after.executions.associateBy{it.id}
        val cash=after.accounts.joinToString("\n"){a->"${a.name}：已实现 ${exact(sum(after.matches.filter{m->byExecution.getValue(m.closeExecutionId).accountId==a.id}.map{decimal(it.net)}))}；${if(a.id=="stock")"参考现金 ${LedgerStatistics.cash(after,a)?:"未知（期初/历史未完整确认）"}"else "不推算真实可用资金"}"}
        val risk=candidate?.let{LedgerRisk.evaluate(it,before.risks.first{r->r.id==it.accountId})}
        val uncertain=MatchingEngine.uncertainTimes(facts).any{group->group.any{it.id==candidate?.id}}
        val historyChanged=before.matches.any{m->after.matches.firstOrNull{it.id==m.id}!=m}
        LedgerChange(before,after,candidate?.id,deleteId!=null,risk,(if(uncertain)"本组秒数不完整或同秒无法区分，按明确序号FIFO；请核对先后顺序。\n"else "")+"后续 ${repriced.size} 笔费用按各自原快照重算（不使用新费率）。\n影响 $changed 个持仓周期、${after.matches.count{m->before.matches.firstOrNull{it.id==m.id}!=m}} 项匹配。\n已实现净盈亏：${exact(oldNet)} → ${exact(newNet)}\n$cash\n费用不会重复扣除；确认后事务重算。",uncertain,historyChanged)
    }
    suspend fun confirm(change:LedgerChange,riskAcknowledged:Boolean=false)=withContext(Dispatchers.IO) {
        val context=kotlinx.coroutines.currentCoroutineContext()
        db.withTransaction {
            ledgerRequire(read()==change.before,"数据在预览后变化，请重新预览；未保存")
            val executions=change.after.executions.map{if(it.id==change.executionId && riskAcknowledged)it.copy(riskConfirmed=true)else it}
            ledgerRequire(!change.orderWarning || riskAcknowledged,"同分钟成交顺序尚未确认，未保存")
            val confirmedExecutions=executions.map{if(it.id==change.executionId && riskAcknowledged)it.copy(orderConfirmed=true)else it}
            val after=change.after.copy(executions=confirmedExecutions,audits=change.after.audits.map{a->if(a.id !in change.before.audits.map{it.id} && a.operation=="edit")a.copy(afterJson=LedgerJson.text(confirmedExecutions.first{it.id==a.executionId}))else a})
            LedgerValidation.snapshot(after)
            write(after,clear=false,before=change.before);context.ensureActive()
        }
    }
    /** Caller supplies a Room transaction; foreign-key-safe order and generated caches remain coherent. */
    suspend fun write(value:LedgerSnapshot,clear:Boolean,before:LedgerSnapshot?=null) {
        LedgerValidation.snapshot(value)
        if(clear){dao.clearAudits();dao.clearReviews();dao.clearMatches();dao.clearLots();dao.clearCycles();dao.clearExecutions();dao.clearPlans();dao.clearCashFlows();dao.clearRisks();dao.clearAccounts();dao.clearStrategies();dao.clearRules()}
        value.accounts.forEach{dao.save(it)};value.strategies.forEach{dao.save(it)};value.risks.forEach{dao.save(it)};value.rules.forEach{dao.save(it)};value.plans.forEach{dao.save(it)}
        if(!clear){dao.clearMatches();dao.clearLots();(before?.executions.orEmpty().map{it.id}-value.executions.map{it.id}.toSet()).forEach{dao.removeExecution(it)}}
        value.executions.forEach{if(clear || before?.executions?.firstOrNull{old->old.id==it.id}!=it)dao.save(it)}
        value.cycles.forEach{dao.save(it)};if(!clear)dao.removeCyclesExcept(value.cycles.map{it.id})
        value.lots.forEach{dao.save(it)};value.matches.forEach{dao.save(it)};value.reviews.forEach{dao.save(it)};value.cashFlows.forEach{dao.save(it)};value.audits.forEach{dao.save(it)}
    }
    suspend fun account(a:LedgerAccount){LedgerValidation.account(a);dao.save(a)}
    suspend fun risk(r:MarketRisk){LedgerValidation.risk(r);dao.save(r)}
    suspend fun strategy(s:LedgerStrategy){LedgerValidation.strategy(s);dao.save(s)}
    suspend fun rule(r:LedgerContractRule){LedgerValidation.rule(r);db.withTransaction{ledgerRequire(dao.rules().none{it.id!=r.id && it.code==r.code && it.effectiveDay==r.effectiveDay},"同一合约同一生效日已有规则，请编辑原规则或选择不同生效日");dao.save(r)}}
    suspend fun plan(p:LedgerPlan){LedgerValidation.plan(p);dao.save(p)}
    suspend fun review(r:LedgerReview){LedgerValidation.review(r);dao.save(r)}
    suspend fun cash(f:LedgerCashFlow){LedgerValidation.cash(f);dao.save(f)}
    companion object {
        fun merge(local:LedgerSnapshot,incoming:LedgerSnapshot):LedgerSnapshot {
            fun <T> union(a:List<T>,b:List<T>,id:(T)->String)=(a+b.filter{v->a.none{id(it)==id(v)}}).sortedBy(id)
            val executions=union(local.executions,incoming.executions){it.id};val result=MatchingEngine.replay(executions)
            val merged=local.copy(strategies=union(local.strategies,incoming.strategies){it.id},rules=union(local.rules,incoming.rules){it.id},plans=union(local.plans,incoming.plans){it.id},executions=executions,cycles=result.cycles,lots=result.lots,matches=result.matches,reviews=union(local.reviews,incoming.reviews){it.id},cashFlows=union(local.cashFlows,incoming.cashFlows){it.id},audits=union(local.audits,incoming.audits){it.id})
            LedgerValidation.snapshot(merged);return merged
        }
    }
}
