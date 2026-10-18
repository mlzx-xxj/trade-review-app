package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.newKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.math.BigDecimal

@Composable fun V200Accounts(model:V200ViewModel,state:LedgerSnapshot){
    var selected by rememberSaveable{mutableStateOf("stock")}
    // Preserve the editing identity across Activity recreation; form values alone
    // must never turn an existing correction into an unrelated new cash flow.
    var flowId by rememberSaveable{mutableStateOf<String?>(null)}
    var showFlow by rememberSaveable{mutableStateOf(false)};var configuring by rememberSaveable{mutableStateOf(false)}
    val account=state.accounts.first{it.id==selected};val executions=state.executions.filter{it.accountId==selected};val executionIds=executions.map{it.id}.toSet();val matches=state.matches.filter{it.closeExecutionId in executionIds};val positions=state.cycles.filter{it.accountId==selected && decimal(it.remaining)>BigDecimal.ZERO};val cash=state.cashFlows.filter{it.accountId==selected};val incoming=sum(cash.filter{it.kind=="in"}.map{decimal(it.amount)});val outgoing=sum(cash.filter{it.kind=="out"}.map{decimal(it.amount)})
    Column(Modifier.fillMaxSize()){
        Box(Modifier.fillMaxWidth().padding(horizontal=LedgerUi.inset,vertical=8.dp)){
            SegmentedChoices(selected,listOf("stock" to "A股账户","futures" to "国内期货账户"),{selected=it},"account-")
        }
        LazyColumn(Modifier.weight(1f).testTag("v2-accounts"),contentPadding=PaddingValues(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.inset)){
            item{
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                    Column(Modifier.padding(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        OverviewHeading(account.name,action="记流水",onAction={flowId=null;showFlow=true},actionTag="new-cashflow")
                        OverviewMoney("累计已实现净盈亏",completeTotal(matches.map{it.net}),"account-net-$selected",large=true,empty=matches.isEmpty())
                        PositionMoneyLine("累计已记录手续费",moneyText(completeTotal(executions.map{it.fees})),"account-fees-$selected")
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        if(selected=="stock"){
                            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                                PositionMetric("当前股数",exact(sum(positions.map{decimal(it.remaining)}))+"股","account-quantity-stock",Modifier.weight(1f))
                                PositionMetric("持仓成本（非市值）",moneyText(completeTotal(positions.map{it.openCost})),"account-cost-stock",Modifier.weight(1f))
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                                PositionMetric("累计买入金额",amount(exact(sum(executions.filter{it.side=="buy"}.map{decimal(it.price)*decimal(it.quantity)}))),"account-buys",Modifier.weight(1f))
                                PositionMetric("累计卖出金额",amount(exact(sum(executions.filter{it.side=="sell"}.map{decimal(it.price)*decimal(it.quantity)}))),"account-sells",Modifier.weight(1f))
                            }
                        }else{
                            PositionMetric("当前持仓",exact(sum(positions.map{decimal(it.remaining)}))+"手","account-quantity-futures")
                            PositionMoneyLine("参考保证金",if(positions.all{it.referenceMargin!=null})amount(exact(sum(positions.map{decimal(it.referenceMargin!!)})))else"待估算","account-margin-futures")
                            Text("不推算真实可用资金；参考保证金不代表可用资金。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item{LedgerPanel("资金记录"){
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    PositionMetric("累计入金",amount(exact(incoming)),"account-in-$selected",Modifier.weight(1f))
                    PositionMetric("累计出金",amount(exact(outgoing)),"account-out-$selected",Modifier.weight(1f))
                }
                PositionMoneyLine("资金净流入",amount(exact(incoming-outgoing)),"account-flow-net-$selected")
                if(selected=="stock"){
                    PositionMoneyLine("参考可用现金",amount(LedgerStatistics.cash(state,account)),"account-cash-stock")
                    if(!account.historyComplete || account.initialCapital==null)Text("期初和全部历史未完整确认，参考现金不可计算。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({configuring=true},Modifier.heightIn(min=48.dp).testTag("configure-account")){Text(if(selected=="stock")"期初资金与历史完整性"else"设置期初资金（可选）")}
            }}
            item{OverviewHeading("资金流水","${cash.size}条记录")}
            if(cash.isEmpty())item{EmptyLedger("尚无出入金记录。可从上方“记流水”录入，资金流动不会计入交易盈亏。")}
            items(cash.sortedByDescending{it.day},key={it.id}){f->
                LedgerPanel("${LocalDate.ofEpochDay(f.day)} · ${if(f.kind=="in")"入金"else"出金"}"){
                    NumericText(amount(f.amount),style=MaterialTheme.typography.titleLarge)
                    if(f.note.isNotBlank())Text(f.note,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({flowId=f.id;showFlow=true},Modifier.heightIn(min=48.dp).testTag("edit-cashflow-${f.id}")){Text("编辑流水")}
                }
            }
        }
    }
    val busy by model.busy.collectAsState()
    if(configuring){
        var capital by rememberSaveable(account.id){mutableStateOf(account.initialCapital.orEmpty())};var complete by rememberSaveable(account.id){mutableStateOf(account.historyComplete)};var error by remember{mutableStateOf<String?>(null)}
        ReviewForm("${account.name}配置","保存账户配置","save-account",error,busy,{configuring=false},{
            val a=account.copy(initialCapital=capital.trim().ifBlank{null},historyComplete=complete)
            try{LedgerValidation.account(a);model.account(a);configuring=false}catch(e:IllegalArgumentException){error=e.message}
        }){
            EntryField("期初资金（可选，元）",capital,{capital=it;error=null},"account-capital",true)
            if(account.id=="stock"){
                Text("新成交综合费用：买入万一、卖出万六；旧成交保留原快照。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("account-complete").toggleable(value=complete,role=Role.Checkbox,onValueChange={complete=it}),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(complete,null)
                    Text("期初、所有买卖及出入金历史已完整核对",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                }
                Text("仅在资料完整时计算参考可用现金，持仓成本不代表实时市值。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(showFlow){
        val flow=state.cashFlows.firstOrNull{it.id==flowId}
        var kind by rememberSaveable(flow?.id){mutableStateOf(flow?.kind?:"in")};var day by rememberSaveable(flow?.id){mutableLongStateOf(flow?.day?:AutomaticEntry.today().toEpochDay())};var amount by rememberSaveable(flow?.id){mutableStateOf(flow?.amount.orEmpty())};var note by rememberSaveable(flow?.id){mutableStateOf(flow?.note.orEmpty())};var error by remember{mutableStateOf<String?>(null)}
        ReviewForm("${account.name}流水","保存流水","save-cashflow",error,busy,{showFlow=false},{
            val f=LedgerCashFlow(flow?.id?:newKey(),selected,day,kind,amount.trim(),note)
            try{LedgerValidation.cash(f);model.cash(f);showFlow=false}catch(e:IllegalArgumentException){error=e.message}
        }){
            SegmentedChoices(kind,listOf("in" to "入金","out" to "出金"),{kind=it})
            DateControl(day,{day=it})
            EntryField("金额（元）",amount,{amount=it;error=null},"cashflow-amount",true)
            EntryField("简短备注",note,{note=it;error=null},"cashflow-note",lines=3)
            flow?.let{Text("原流水：${if(it.kind=="in")"入金"else"出金"} ${amount(it.amount)}，保存为明确更正。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

private data class CalculatedStatistics(val period:LedgerPeriod,val market:String,val source:List<ClosedEvent>,val numbers:PeriodNumbers)
@Composable fun V200Statistics(state:LedgerScreenState){
    var type by rememberSaveable{mutableStateOf("week")};var market by rememberSaveable{mutableStateOf("all")};var offset by rememberSaveable(type){mutableIntStateOf(0)}
    val period=if(type=="week")LedgerPeriod.week(AutomaticEntry.today(),offset)else LedgerPeriod.month(AutomaticEntry.today(),offset)
    val calculated by produceState<CalculatedStatistics?>(null,state.closed,period,market){value=withContext(Dispatchers.Default){CalculatedStatistics(period,market,state.closed,periodNumbers(state.closed,period,market))}}
    val numbers=calculated?.takeIf{it.period==period&&it.market==market&&it.source==state.closed}?.numbers
    LazyColumn(Modifier.fillMaxSize().testTag("v2-statistics"),contentPadding=PaddingValues(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.inset)){
        item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            BoxWithConstraints(Modifier.fillMaxWidth()){
                if(maxWidth>=460.dp)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.weight(.4f)){SegmentedChoices(type,listOf("week" to "每周","month" to "每月"),{type=it},"statistics-period-")}
                    Box(Modifier.weight(.6f)){SegmentedChoices(market,listOf("all" to "合计","stock" to "A股","futures" to "国内期货"),{market=it},"statistics-market-")}
                }else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    SegmentedChoices(type,listOf("week" to "每周","month" to "每月"),{type=it},"statistics-period-")
                    SegmentedChoices(market,listOf("all" to "合计","stock" to "A股","futures" to "国内期货"),{market=it},"statistics-market-")
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                TextButton({offset--},Modifier.heightIn(min=48.dp).testTag("statistics-previous"),contentPadding=PaddingValues(4.dp)){Text("上一${if(type=="week")"周"else"月"}")}
                Text("${LocalDate.ofEpochDay(period.start)} — ${LocalDate.ofEpochDay(period.end)}",Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.labelMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({offset++},Modifier.heightIn(min=48.dp).testTag("statistics-next"),contentPadding=PaddingValues(4.dp)){Text("下一${if(type=="week")"周"else"月"}")}
            }
        }}
        if(numbers==null)item{LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在计算已实现结果",style=MaterialTheme.typography.bodySmall)}
        numbers?.let{n->
            item{Card(Modifier.fillMaxWidth().testTag("statistics-results-$type-$market"),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(n.records.isEmpty())EmptyLedger("本周期暂无平仓成交")else{
                    OverviewMoney(if(n.pending>0)"已知已实现净盈亏"else"已实现净盈亏",n.net,"period-realized-net",large=true)
                    if(n.pending>0)Text("${n.pending} 笔费用待估算，未计入净盈亏、胜率和图表；有效样本 ${n.records.size-n.pending}",Modifier.testTag("statistics-pending"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                        PositionMetric("有效样本 / 全部","${n.records.size-n.pending} / ${n.records.size}","statistics-samples",Modifier.weight(1f))
                        PositionMetric("胜率（含持平）",n.winRate?.let{AutomaticEntry.cents(it)+"%"}?:"不可计算","statistics-win-rate",Modifier.weight(1f))
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        StatisticsCount("盈利",n.wins,profitColor(),Modifier.weight(1f))
                        StatisticsCount("亏损",n.losses,lossColor(),Modifier.weight(1f))
                        StatisticsCount("持平",n.flats,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("单笔平均净盈亏",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f)){NumericText(moneyText(n.average),Modifier.testTag("statistics-average"),style=MaterialTheme.typography.bodyLarge,color=n.average?.let{pnlColor(it)}?:MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                    Text("一次平仓成交计一笔；仅统计已实现结果。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }}}
            item{DailyClosedBars(period.days,n.daily,n.records.filter{it.net==null}.map{it.execution.tradingDay}.toSet())}
            if(type=="month")item{ContractDistribution(n.distribution)}
        }
    }
}
@Composable private fun StatisticsCount(label:String,count:Int,color:androidx.compose.ui.graphics.Color,modifier:Modifier){
    Row(modifier,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label,style=MaterialTheme.typography.labelMedium,color=color)
        Box(Modifier.weight(1f)){NumericText(count.toString(),style=MaterialTheme.typography.titleMedium,color=color)}
    }
}
