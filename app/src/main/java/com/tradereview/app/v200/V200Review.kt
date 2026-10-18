package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.newKey
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable fun V200Reviews(model:V200ViewModel,state:LedgerSnapshot){
    var tab by rememberSaveable{mutableStateOf("plan")};var showPlan by rememberSaveable{mutableStateOf(false)}
    var planId by rememberSaveable{mutableStateOf<String?>(null)};var selectedCycle by rememberSaveable{mutableStateOf<String?>(null)}
    var reviewId by rememberSaveable{mutableStateOf<String?>(null)};var showReview by rememberSaveable{mutableStateOf(false)}
    val list=rememberLazyListState()
    LaunchedEffect(tab){list.scrollToItem(0)}
    Column(Modifier.fillMaxSize()){
        Box(Modifier.fillMaxWidth().padding(horizontal=LedgerUi.inset,vertical=8.dp)){
            SegmentedChoices(tab,listOf("plan" to "盘前计划","post" to "盘后复盘"),{tab=it},"review-section-")
        }
        LazyColumn(Modifier.weight(1f).testTag("v2-reviews"),state=list,contentPadding=PaddingValues(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.inset)){
            if(tab=="plan"){
                item{Button({planId=null;showPlan=true},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("new-plan"),shape=MaterialTheme.shapes.small){Text("新增具体标的计划")}}
                if(state.plans.isEmpty())item{EmptyLedger("先选择股票或合约，再写下计划。同一天可保存多条，计划不会生成真实成交。")}
                items(state.plans.sortedByDescending{it.day},key={it.id}){p->
                    LedgerPanel(p.name,Modifier.testTag("plan-card-${p.id}")){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(p.code,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusPill(if(p.accountId=="stock")"A股"else"期货",MaterialTheme.colorScheme.primary)
                        }
                        Text("${LocalDate.ofEpochDay(p.day)} · ${strategyLabel(state,p.strategyId)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(p.entryPrice!=null||p.takeProfit!=null||p.stopLoss!=null){
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                p.entryPrice?.let{Text("入场 $it",style=MaterialTheme.typography.bodyMedium)}
                                p.takeProfit?.let{Text("止盈 $it",style=MaterialTheme.typography.bodyMedium)}
                                p.stopLoss?.let{Text("止损 $it",style=MaterialTheme.typography.bodyMedium)}
                            }
                        }
                        if(p.note.isNotEmpty())Text(p.note,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                        TextButton({planId=p.id;showPlan=true},Modifier.heightIn(min=48.dp)){Text("查看与编辑计划")}
                    }
                }
            }else{
                if(state.cycles.isEmpty())item{EmptyLedger("真实成交建立持仓后，可在这里复盘。持有中与已平仓周期都能继续记录。")}
                items(state.cycles.sortedByDescending{it.firstDay},key={it.id}){c->
                    LedgerPanel(c.name){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(c.code,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusPill(directionLabel(c),MaterialTheme.colorScheme.primary)
                            StatusPill(if(c.closedDay==null)"持有中"else"已平仓")
                        }
                        Text("${state.accounts.first{it.id==c.accountId}.name} · ${LocalDate.ofEpochDay(c.firstDay)} 开仓",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        c.closedDay?.let{Text("${LocalDate.ofEpochDay(it)} 平仓",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                            PositionMetric("剩余数量",c.remaining+if(c.accountId=="stock")"股"else"手","review-remaining-${c.id}",Modifier.weight(1f),large=true)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                Text("已实现净盈亏",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                NetText(c.realized?.let(::decimal),Modifier.testTag("review-net-${c.id}"))
                            }
                        }
                        TextButton({selectedCycle=c.id;reviewId=null;showReview=true},Modifier.heightIn(min=48.dp).testTag("review-cycle-${c.id}")){Text("新增本次复盘")}
                        val history=state.reviews.filter{it.cycleId==c.id}.sortedByDescending{it.day}
                        if(history.isNotEmpty()){
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Text("历史复盘 · ${history.size}条",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            history.forEach{r->
                                TextButton({selectedCycle=c.id;reviewId=r.id;showReview=true},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("edit-review-${r.id}"),contentPadding=PaddingValues(vertical=8.dp)){
                                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                        Text("${LocalDate.ofEpochDay(r.day)} · 查看与编辑",style=MaterialTheme.typography.labelMedium)
                                        Text(r.note.ifBlank{"已有复盘"},maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(showPlan)PlanDialog(model,state,state.plans.firstOrNull{it.id==planId},{showPlan=false})
    if(showReview)state.cycles.firstOrNull{it.id==selectedCycle}?.let{c->ReviewDialog(model,state,c,state.reviews.firstOrNull{it.id==reviewId},{showReview=false})}
}
private val KeyTextSaver=listSaver<List<String>,String>(save={it},restore={it.toList()})
@Composable private fun KeyPrices(labels:List<String>,prices:List<String>,onChange:(List<String>,List<String>)->Unit){
    Text("关键价位（空价格不保存）",style=MaterialTheme.typography.labelLarge)
    labels.indices.forEach{i->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            EntryField("名称",labels[i],{v->onChange(labels.toMutableList().apply{set(i,v)},prices)},modifier=Modifier.weight(1f))
            EntryField("价位",prices[i],{v->onChange(labels,prices.toMutableList().apply{set(i,v)})},number=true,modifier=Modifier.weight(1f))
            IconButton({onChange(labels.filterIndexed{n,_->n!=i},prices.filterIndexed{n,_->n!=i})},Modifier.size(48.dp)){Icon(Icons.Filled.Close,"删除关键价位${i+1}")}
        }
    }
    if(labels.size<30)TextButton({onChange(labels+"",prices+"")},Modifier.heightIn(min=48.dp)){Text("增加关键价位")}
}
private fun levelText(labels:List<String>,prices:List<String>)=LedgerJson.text(labels.indices.filter{prices[it].isNotBlank()}.map{KeyPrice(labels[it].trim(),prices[it].trim())})
@Composable private fun PlanDialog(model:V200ViewModel,state:LedgerSnapshot,value:LedgerPlan?,onDismiss:()->Unit){
    var account by rememberSaveable(value?.id){mutableStateOf(value?.accountId?:"stock")};var code by rememberSaveable(value?.id){mutableStateOf(value?.code.orEmpty())};var name by rememberSaveable(value?.id){mutableStateOf(value?.name.orEmpty())}
    var day by rememberSaveable(value?.id){mutableLongStateOf(value?.day?:AutomaticEntry.today().toEpochDay())};var strategy by rememberSaveable(value?.id){mutableStateOf(value?.strategyId)}
    var entry by rememberSaveable(value?.id){mutableStateOf(value?.entryPrice.orEmpty())};var tp by rememberSaveable(value?.id){mutableStateOf(value?.takeProfit.orEmpty())};var stop by rememberSaveable(value?.id){mutableStateOf(value?.stopLoss.orEmpty())};var note by rememberSaveable(value?.id){mutableStateOf(value?.note.orEmpty())}
    val original=remember(value?.id){value?.let{LedgerJson.levels(it.levels)}?:listOf(KeyPrice("支撑",""),KeyPrice("压力",""))}
    var labels by rememberSaveable(value?.id,stateSaver=KeyTextSaver){mutableStateOf(original.map{it.label})};var prices by rememberSaveable(value?.id,stateSaver=KeyTextSaver){mutableStateOf(original.map{it.price})};var error by remember{mutableStateOf<String?>(null)}
    val busy by model.busy.collectAsState()
    ReviewForm(if(value==null)"新建盘前计划"else"编辑盘前计划","保存计划","save-plan",error,busy,onDismiss,{
        val p=LedgerPlan(value?.id?:newKey(),account,code.trim().lowercase(),name.trim(),day,strategy,entry.trim().ifBlank{null},tp.trim().ifBlank{null},stop.trim().ifBlank{null},levelText(labels,prices),note)
        try{LedgerValidation.plan(p);model.plan(p);onDismiss()}catch(e:IllegalArgumentException){error=e.message}
    }){
        SegmentedChoices(account,listOf("stock" to "A股账户","futures" to "期货账户"),{account=it})
        DateControl(day,{day=it},"计划日期")
        EntryField("股票/合约代码",code,{code=it.lowercase();error=null},"plan-code")
        EntryField("名称",name,{name=it;error=null},"plan-name")
        EntryField("计划入场价（可选）",entry,{entry=it;error=null},"plan-entry",true)
        ReviewNote("自由文字计划",note,{note=it;error=null},"plan-note")
        ReviewSection("策略与价位 · 可选")
        StrategySelect(state,strategy,{strategy=it})
        EntryField("计划止盈价（可选）",tp,{tp=it;error=null},number=true)
        EntryField("计划止损价（可选）",stop,{stop=it;error=null},number=true)
        KeyPrices(labels,prices,{l,p->labels=l.toList();prices=p.toList()})
    }
}
@Composable private fun ReviewDialog(model:V200ViewModel,state:LedgerSnapshot,cycle:LedgerCycle,value:LedgerReview?,onDismiss:()->Unit){
    var day by rememberSaveable(value?.id,cycle.id){mutableLongStateOf(value?.day?:AutomaticEntry.today().toEpochDay())};var tp by rememberSaveable(value?.id,cycle.id){mutableStateOf(value?.takeProfit.orEmpty())};var stop by rememberSaveable(value?.id,cycle.id){mutableStateOf(value?.stopLoss.orEmpty())};var note by rememberSaveable(value?.id,cycle.id){mutableStateOf(value?.note.orEmpty())}
    val original=remember(value?.id){value?.let{LedgerJson.levels(it.levels)}?:emptyList()}
    var labels by rememberSaveable(value?.id,cycle.id,stateSaver=KeyTextSaver){mutableStateOf(original.map{it.label})};var prices by rememberSaveable(value?.id,cycle.id,stateSaver=KeyTextSaver){mutableStateOf(original.map{it.price})};var error by remember{mutableStateOf<String?>(null)}
    var expanded by rememberSaveable(value?.id,cycle.id){mutableStateOf(false)};val busy by model.busy.collectAsState()
    val executionIds=state.lots.filter{it.cycleId==cycle.id}.map{it.executionId}.toSet()+state.matches.filter{it.cycleId==cycle.id}.map{it.closeExecutionId};val facts=MatchingEngine.ordered(state.executions.filter{it.id in executionIds})
    ReviewForm(if(value==null)"新增盘后复盘"else"编辑盘后复盘","保存复盘","save-position-review",error,busy,onDismiss,{
        val r=LedgerReview(value?.id?:newKey(),cycle.id,day,tp.trim().ifBlank{null},stop.trim().ifBlank{null},levelText(labels,prices),note)
        try{LedgerValidation.review(r);model.review(r);onDismiss()}catch(e:IllegalArgumentException){error=e.message}
    }){
        LedgerPanel("${cycle.name} ${cycle.code}"){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatusPill(directionLabel(cycle),MaterialTheme.colorScheme.primary);StatusPill(if(cycle.closedDay==null)"持有中"else"已平仓")
            }
            Text("${state.accounts.first{it.id==cycle.accountId}.name} · ${LocalDate.ofEpochDay(cycle.firstDay)} 开仓",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            cycle.closedDay?.let{Text("${LocalDate.ofEpochDay(it)} 平仓",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                PositionMetric("剩余数量",cycle.remaining+if(cycle.accountId=="stock")"股"else"手","review-editor-remaining",Modifier.weight(1f))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("已实现净盈亏",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    NetText(cycle.realized?.let(::decimal),Modifier.testTag("review-editor-net"))
                }
            }
        }
        DateControl(day,{day=it},"复盘日期")
        ReviewNote("自由文字复盘",note,{note=it;error=null},"position-review-note")
        ReviewSection("复盘价位 · 可选")
        EntryField("复盘记录的止盈价（可选）",tp,{tp=it;error=null},number=true)
        EntryField("复盘记录的止损价（可选）",stop,{stop=it;error=null},number=true)
        KeyPrices(labels,prices,{l,p->labels=l.toList();prices=p.toList()})
        ReviewSection("成交事实 · 只读")
        TextButton({expanded=!expanded},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("review-readonly-facts")){Text(if(expanded)"收起成交事实"else"查看${facts.size}笔成交与当前参数")}
        if(expanded){
            PositionMoneyLine("平均开仓价",amount(cycle.averagePrice),"review-average")
            PositionMoneyLine("已记录手续费",amount(cycle.fees),"review-fees")
            if(cycle.accountId=="futures")PositionMoneyLine("参考保证金",amount(cycle.referenceMargin),"review-margin")
            Text("当前止盈 ${cycle.takeProfit?.let(::amount)?:"未设置"} · 止损 ${cycle.stopLoss?.let(::amount)?:"未设置"}",style=MaterialTheme.typography.bodyMedium)
            facts.forEach{e->Text("${LocalDate.ofEpochDay(e.date)} ${actionLabel(e)} · ${e.quantity} × ${amount(e.price)} · 费用${amount(e.fees)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
