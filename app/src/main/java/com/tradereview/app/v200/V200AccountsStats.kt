package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.newKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.math.BigDecimal

@Composable fun V200Accounts(model:V200ViewModel,state:LedgerSnapshot){var selected by rememberSaveable{mutableStateOf("stock")};var flow by remember{mutableStateOf<LedgerCashFlow?>(null)};var showFlow by rememberSaveable{mutableStateOf(false)};var configuring by rememberSaveable{mutableStateOf(false)};val account=state.accounts.first{it.id==selected};val executions=state.executions.filter{it.accountId==selected};val executionIds=executions.map{it.id}.toSet();val matches=state.matches.filter{it.closeExecutionId in executionIds};val positions=state.cycles.filter{it.accountId==selected && decimal(it.remaining)>BigDecimal.ZERO};val cash=state.cashFlows.filter{it.accountId==selected};val incoming=sum(cash.filter{it.kind=="in"}.map{decimal(it.amount)});val outgoing=sum(cash.filter{it.kind=="out"}.map{decimal(it.amount)})
    LazyColumn(Modifier.fillMaxSize().testTag("v2-accounts"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ChoiceRow(selected,listOf("stock" to "A股账户","futures" to "国内期货账户"),{selected=it},"account-")}
        item{LedgerPanel(account.name){Text("累计入金 ${amount(exact(incoming))} · 累计出金 ${amount(exact(outgoing))}");Text("资金净流入 ${amount(exact(incoming-outgoing))}");Text("累计实际手续费 ${amount(exact(sum(executions.map{decimal(it.fees)})))}");Text("累计已实现净盈亏");NetText(sum(matches.map{decimal(it.net)}));if(selected=="stock"){Text("累计买入金额 ${amount(exact(sum(executions.filter{it.side=="buy"}.map{decimal(it.price)*decimal(it.quantity)})))}");Text("累计卖出金额 ${amount(exact(sum(executions.filter{it.side=="sell"}.map{decimal(it.price)*decimal(it.quantity)})))}");Text("当前 ${exact(sum(positions.map{decimal(it.remaining)}))}股 · 持仓成本 ${amount(exact(sum(positions.map{decimal(it.openCost)})))}（非市值）");Text("参考可用现金 ${amount(LedgerStatistics.cash(state,account))}");if(!account.historyComplete || account.initialCapital==null)Text("期初和全部历史未完整确认，现金保持未知。",style=MaterialTheme.typography.bodySmall)}else{Text("当前 ${exact(sum(positions.map{decimal(it.remaining)}))}手");Text("参考保证金 ${if(positions.all{it.referenceMargin!=null})amount(exact(sum(positions.map{decimal(it.referenceMargin!!)})))else"未知（参数不完整）"}");Text("不推算真实可用资金；期货结算涉及逐日盯市。",style=MaterialTheme.typography.bodySmall)};TextButton({configuring=true},modifier=Modifier.heightIn(min=48.dp).testTag("configure-account")){Text(if(selected=="stock")"期初资金与历史完整性"else"设置期初资金（可选）")}}}
        item{Button({flow=null;showFlow=true},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("new-cashflow")){Text("记录入金／出金")}}
        item{Text("资金流水",style=MaterialTheme.typography.titleMedium)}
        if(cash.isEmpty())item{EmptyLedger("尚无出入金记录")};items(cash.sortedByDescending{it.day},key={it.id}){f->LedgerPanel("${LocalDate.ofEpochDay(f.day)} · ${if(f.kind=="in")"入金"else"出金"}"){Text(amount(f.amount));if(f.note.isNotBlank())Text(f.note);TextButton({flow=f;showFlow=true},modifier=Modifier.heightIn(min=48.dp)){Text("编辑流水")}}}
    }
    if(configuring){var capital by rememberSaveable(account.id){mutableStateOf(account.initialCapital.orEmpty())};var complete by rememberSaveable(account.id){mutableStateOf(account.historyComplete)};var error by remember{mutableStateOf<String?>(null)};AlertDialog(onDismissRequest={configuring=false},title={Text("${account.name}配置")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){error?.let{Text(it,color=MaterialTheme.colorScheme.error)};EntryField("期初资金（可选，元）",capital,{capital=it},"account-capital",true);if(account.id=="stock"){Text("新成交综合费用：买入万一、卖出万六；不另加税费。旧成交保留原快照。",style=MaterialTheme.typography.bodySmall);Row{Checkbox(complete,{complete=it},modifier=Modifier.testTag("account-complete"));Text("期初、所有买卖及出入金历史已完整核对",Modifier.weight(1f).padding(top=12.dp))}}}},confirmButton={TextButton({val a=account.copy(initialCapital=capital.trim().ifBlank{null},historyComplete=complete);try{LedgerValidation.account(a);model.account(a);configuring=false}catch(e:IllegalArgumentException){error=e.message}},modifier=Modifier.heightIn(min=48.dp).testTag("save-account")){Text("保存账户配置")}},dismissButton={TextButton({configuring=false}){Text("取消")}})}
    if(showFlow){var kind by rememberSaveable(flow?.id){mutableStateOf(flow?.kind?:"in")};var day by rememberSaveable(flow?.id){mutableLongStateOf(flow?.day?:LocalDate.now().toEpochDay())};var amount by rememberSaveable(flow?.id){mutableStateOf(flow?.amount.orEmpty())};var note by rememberSaveable(flow?.id){mutableStateOf(flow?.note.orEmpty())};var error by remember{mutableStateOf<String?>(null)};AlertDialog(onDismissRequest={showFlow=false},title={Text("${account.name}流水")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){error?.let{Text(it,color=MaterialTheme.colorScheme.error)};ChoiceRow(kind,listOf("in" to "入金","out" to "出金"),{kind=it});DateControl(day,{day=it});EntryField("金额（元）",amount,{amount=it},"cashflow-amount",true);EntryField("简短备注",note,{note=it},"cashflow-note");if(flow!=null)Text("原流水：${if(flow!!.kind=="in")"入金"else"出金"} ${flow!!.amount}元，保存为明确更正。",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton({val f=LedgerCashFlow(flow?.id?:newKey(),selected,day,kind,amount.trim(),note);try{LedgerValidation.cash(f);model.cash(f);showFlow=false}catch(e:IllegalArgumentException){error=e.message}},modifier=Modifier.heightIn(min=48.dp).testTag("save-cashflow")){Text("保存流水")}},dismissButton={TextButton({showFlow=false}){Text("取消")}})}
}

private data class CalculatedStatistics(val period:LedgerPeriod,val market:String,val source:List<ClosedEvent>,val numbers:PeriodNumbers)
@Composable fun V200Statistics(state:LedgerScreenState){var type by rememberSaveable{mutableStateOf("week")};var market by rememberSaveable{mutableStateOf("all")};var offset by rememberSaveable(type){mutableIntStateOf(0)};val period=if(type=="week")LedgerPeriod.week(LocalDate.now(),offset)else LedgerPeriod.month(LocalDate.now(),offset)
    val calculated by produceState<CalculatedStatistics?>(null,state.closed,period,market){value=withContext(Dispatchers.Default){CalculatedStatistics(period,market,state.closed,periodNumbers(state.closed,period,market))}}
    val numbers=calculated?.takeIf{it.period==period && it.market==market && it.source==state.closed}?.numbers
    LazyColumn(Modifier.fillMaxSize().testTag("v2-statistics"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ChoiceRow(type,listOf("week" to "每周","month" to "每月"),{type=it},"statistics-period-");ChoiceRow(market,listOf("all" to "合计","stock" to "A股","futures" to "国内期货"),{market=it},"statistics-market-");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({offset--},modifier=Modifier.heightIn(min=48.dp).testTag("statistics-previous")){Text("上一${if(type=="week")"周"else"月"}")};Text("${LocalDate.ofEpochDay(period.start)}\n至 ${LocalDate.ofEpochDay(period.end)}",style=MaterialTheme.typography.labelMedium);TextButton({offset++},modifier=Modifier.heightIn(min=48.dp).testTag("statistics-next")){Text("下一${if(type=="week")"周"else"月"}")}}}
        if(numbers==null)item{LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在计算已实现结果")}
        numbers?.let{n->item{LedgerPanel("已实现交易摘要",Modifier.testTag("statistics-results-$type-$market")){
            Text("平仓成交 ${n.records.size} · 盈利 ${n.wins} · 亏损 ${n.losses} · 持平 ${n.flats}")
            Text("胜率 ${n.winRate?.let{exact(it.setScale(2,java.math.RoundingMode.HALF_UP))+"%"}?:"不可计算（无样本）"}")
            if(n.records.isEmpty())EmptyLedger("本周期暂无平仓成交")else{Text("已实现净盈亏");NetText(n.net,Modifier.testTag("period-realized-net"));Text("单笔平均净盈亏 ${n.average?.let{amount(exact(it))}}");Text("一次平仓成交计一笔；胜率含持平样本。",style=MaterialTheme.typography.bodySmall)}
        }};item{DailyClosedBars(period.days,n.daily)};if(type=="month")item{ContractDistribution(n.distribution)}}
    }
}
