package com.tradereview.app.v200

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tradereview.app.data.TradeDatabase
import com.tradereview.app.data.Instrument
import com.tradereview.app.data.newKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LedgerScreenState(val ledger:LedgerSnapshot?=null,val closed:List<ClosedEvent> = emptyList(),val daily:Map<Long,java.math.BigDecimal> = emptyMap(),val error:String?=null)
class V200ViewModel(val database:TradeDatabase,val handle:SavedStateHandle):ViewModel() {
    val repository=LedgerRepository(database)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state=repository.observe().mapLatest{s->withContext(Dispatchers.Default){val closed=LedgerStatistics.closed(s);LedgerScreenState(s,closed,LedgerStatistics.days(closed))}}.catch{e->emit(LedgerScreenState(error="账本读取失败，未修改数据"))}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),LedgerScreenState())
    val busy=MutableStateFlow(false);val problem=MutableStateFlow<String?>(null);val message=MutableStateFlow<String?>(null);val pending=MutableStateFlow<LedgerChange?>(null)
    val instruments=database.trades().observeInstruments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val legacy=database.trades().observeTrades().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val attachments=database.trades().observeAttachments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val legacyTags=database.trades().observeTags().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val page=handle.getStateFlow("v2-page",0)
    val editor=handle.getStateFlow("v2-editor",false)
    fun page(index:Int){handle["v2-page"]=index}
    fun openEditor(){problem.value=null;handle["v2-editor"]=true}
    fun closeEditor(){handle["v2-editor"]=false}
    private fun action(success:String?,block:suspend()->Unit){if(busy.value)return;busy.value=true;problem.value=null;viewModelScope.launch{try{withContext(Dispatchers.IO){block()};if(success!=null)message.value=success}catch(e:Exception){if(e is CancellationException)throw e;problem.value=e.message?.takeIf{e is IllegalArgumentException}?:"操作失败，未修改数据";message.value=problem.value}finally{busy.value=false}}}
    fun execution(value:LedgerExecution){action(null){
        val preview=repository.preview(value)
        val existing=preview.before.executions.any{it.id==value.id}
        if(existing || preview.orderWarning || preview.affectsHistory || preview.risk?.exceeded==true && preview.after.executions.first{it.id==value.id}.riskWarningEnabled){pending.value=preview;message.value=null}
        else{repository.confirm(preview);withContext(Dispatchers.Main){closeEditor();handle["v2-entry-fields"]=null;message.value="成交已保存，持仓与统计已重算"}}
    }}
    fun deleteExecution(value:LedgerExecution)=action("请核对成交删除影响"){pending.value=repository.preview(null,value.id)}
    fun confirmChange(){val value=pending.value?:return;action(if(value.deleting)"成交已删除，原事实修订已保留"else"成交已保存并事务重算"){
        repository.confirm(value,riskAcknowledged=true);pending.value=null;withContext(Dispatchers.Main){closeEditor();handle["v2-entry-fields"]=null}
    }}
    fun cancelChange(){if(!busy.value)pending.value=null}
    fun account(value:LedgerAccount)=action("账户配置已保存，风险金未改变"){repository.account(value)}
    fun risk(value:MarketRisk)=action("对应市场风险配置已保存"){repository.risk(value)}
    fun strategy(value:LedgerStrategy)=action("策略已保存，历史关联保持"){repository.strategy(value)}
    fun moveStrategy(id:String,delta:Int)=action("策略排序已保存"){
        database.withTransaction {val values=database.ledger().strategies().sortedWith(compareBy<LedgerStrategy>{it.sortOrder}.thenBy{it.id}).toMutableList();val index=values.indexOfFirst{it.id==id};val next=index+delta;if(index>=0 && next in values.indices){val old=values[index];values[index]=values[next];values[next]=old;values.forEachIndexed{i,v->repository.strategy(v.copy(sortOrder=i))}}}
    }
    fun rule(value:LedgerContractRule)=action("合约规则已保存，不改写历史费用或参数"){repository.rule(value)}
    fun plan(value:LedgerPlan)=action("标的盘前计划已保存，不会变成成交"){repository.plan(value)}
    fun review(value:LedgerReview)=action("持仓复盘已保存，成交事实未变"){repository.review(value)}
    fun cash(value:LedgerCashFlow)=action("资金流水已保存，未计入交易盈亏"){repository.cash(value)}
    fun favorite(account:String,code:String,name:String)=action("已保存为常用，可搜索复用"){
        LedgerValidation.code(account,code);val old=database.trades().allInstruments().firstOrNull{it.market==account && it.code==code}
        database.trades().saveInstrument(old?.copy(favorite=true,lastUsed=System.currentTimeMillis())?:Instrument("ledger_${newKey()}",account,"${name.take(16)} $code",code,1000,true,System.currentTimeMillis()))
    }
    fun draft(values:Map<String,String>){handle["v2-entry-fields"]=HashMap(values)}
    fun restoredDraft():Map<String,String> = handle.get<HashMap<String,String>>("v2-entry-fields")?:emptyMap()
}
