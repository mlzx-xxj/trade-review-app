package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import com.tradereview.app.data.newKey
import java.time.LocalDate
import java.time.LocalTime
import java.math.BigDecimal

@Composable fun V200Trades(state:LedgerSnapshot,onCycle:(String)->Unit,onExecution:(String)->Unit,onArchive:()->Unit,savedId:String?=null){var tab by rememberSaveable{mutableStateOf("positions")};LaunchedEffect(savedId){if(savedId!=null)tab="history"};Column(Modifier.fillMaxSize()){Box(Modifier.padding(horizontal=12.dp,vertical=8.dp)){SegmentedChoices(tab,listOf("positions" to "当前持仓","history" to "历史成交"),{tab=it},"trade-mode-")};LazyColumn(Modifier.weight(1f).testTag("v2-trades"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    if(tab=="positions"){val current=state.cycles.filter{decimal(it.remaining)>BigDecimal.ZERO};if(current.isEmpty())item{EmptyLedger("暂无持仓，请在右上角记录真实买入或开仓")};items(current,key={it.id}){PositionCard(state,it,onCycle)}}
    else{val history=MatchingEngine.ordered(state.executions).reversed();if(history.isEmpty())item{EmptyLedger("尚未记录实际成交")};items(history,key={it.id}){e->LedgerPanel("${e.name} ${e.code}",Modifier.clickable{onExecution(e.id)}.testTag("execution-${e.id}")){Text("${LocalDate.ofEpochDay(e.date)} %02d:%02d · ${actionLabel(e)}".format(e.minute/60,e.minute%60));Text("${e.quantity}${if(e.accountId=="stock")"股"else"手"} × ${amount(e.price)} · 费用 ${amount(e.fees)}");Text(strategyLabel(state,e.strategyId));if(e.effect!="open")NetText(completeTotal(state.matches.filter{it.closeExecutionId==e.id}.map{it.net}))}}}
    item{TextButton(onArchive,modifier=Modifier.heightIn(min=48.dp).testTag("legacy-archive")){Text("兼容历史档案（只读，不计入新统计）",style=MaterialTheme.typography.bodySmall)}}
}}}
@Composable fun PositionDetails(state:LedgerSnapshot,id:String){val c=state.cycles.firstOrNull{it.id==id}?:return;val ids=state.lots.filter{it.cycleId==id}.map{it.executionId}.toSet()+state.matches.filter{it.cycleId==id}.map{it.closeExecutionId};val executions=MatchingEngine.ordered(state.executions.filter{it.id in ids})
    LazyColumn(Modifier.fillMaxSize().testTag("v2-position-details"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{PositionCard(state,c,{})};item{LedgerPanel("持仓周期事实"){Text("${state.accounts.first{it.id==c.accountId}.name} · ${directionLabel(c)}");Text("开仓 ${LocalDate.ofEpochDay(c.firstDay)} · 平仓 ${c.closedDay?.let{LocalDate.ofEpochDay(it).toString()}?:"仍持有"}");Text("已记录费用 ${amount(c.fees)}");Text("止盈 ${c.takeProfit?.let(::amount)?:"未设置"} · 止损 ${c.stopLoss?.let(::amount)?:"未设置"}",style=MaterialTheme.typography.bodyMedium)}};items(executions,key={it.id}){e->LedgerPanel(actionLabel(e)){Text("${LocalDate.ofEpochDay(e.date)} %02d:%02d".format(e.minute/60,e.minute%60));if(e.accountId=="futures")Text("交易日 ${LocalDate.ofEpochDay(e.tradingDay)}");Text("价格 ${amount(e.price)} · 数量 ${e.quantity} · 已记录费用 ${amount(e.fees)}");Text("策略 ${strategyLabel(state,e.strategyId)}");e.planId?.let{p->Text("人工关联计划 ${state.plans.firstOrNull{it.id==p}?.let{LocalDate.ofEpochDay(it.day).toString()}?:p}")};state.matches.filter{it.closeExecutionId==e.id && it.cycleId==id}.forEach{m->Text("FIFO 匹配 ${m.openExecutionId}：${m.quantity} · 净盈亏 ${amount(m.net)}")}}}}
}
@Composable fun ExecutionDetails(state:LedgerSnapshot,e:LedgerExecution,onEdit:()->Unit,onDelete:()->Unit){EditorBody{LedgerPanel("${e.name} ${e.code} · ${actionLabel(e)}"){Text("${state.accounts.first{it.id==e.accountId}.name} · ${LocalDate.ofEpochDay(e.date)} %02d:%02d".format(e.minute/60,e.minute%60));Text("价格 ${amount(e.price)} · 数量 ${e.quantity} · 费用 ${amount(e.fees)}");Text("费用${if(e.fees==null)"待估算；净盈亏不计入已知统计"else if(e.feeSource=="actual")"为实际录入"else"按当笔保存规则计算"}");if(e.accountId=="futures"){Text("交易日 ${LocalDate.ofEpochDay(e.tradingDay)} · 乘数 ${e.multiplier}");Text("保证金率 ${e.marginRate?:"未知"}")};Text("策略 ${strategyLabel(state,e.strategyId)}");Text("止盈 ${e.takeProfit?.let(::amount)?:"未设置"} · 止损 ${e.stopLoss?.let(::amount)?:"未设置"}");Text("计划风险 ${e.riskAmount?.let(::amount)?:"风险无法计算"} · 当时风险金 ${amount(e.riskFundSnapshot)}");if(e.effect!="open")NetText(completeTotal(state.matches.filter{it.closeExecutionId==e.id}.map{it.net}));if(e.riskExceeded)Text("曾超过单笔风险上限；${if(e.riskConfirmed)"用户已确认记录"else"当时关闭提示"}")};OutlinedButton(onEdit,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("edit-execution")){Text("修改成交（先预览影响）")};OutlinedButton(onDelete,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("delete-execution")){Text("删除成交（先预览影响）")}}}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExecutionEditor(model:V200ViewModel,state:LedgerSnapshot,value:LedgerExecution?,onRisk:()->Unit){
    val key=value?.id?:"new"
    val draft=remember(key){model.restoredDraft().takeIf{it["mode"]==key}.orEmpty()}
    var stamp by remember(key){mutableStateOf(model.entryNow())}
    val currentStamp=model.entryNow()
    SideEffect{if(value==null && stamp.toLocalDate()!=currentStamp.toLocalDate())stamp=currentStamp}
    val id=rememberSaveable(key){value?.id?:draft["id"]?:newKey()}
    var account by rememberSaveable(key){mutableStateOf(draft["account"]?:value?.accountId?:model.handle.get<String>("v2-last-account")?:"stock")}
    var code by rememberSaveable(key){mutableStateOf(draft["code"]?:value?.code?:"")}
    var name by rememberSaveable(key){mutableStateOf(draft["name"]?:value?.name?:"")}
    var side by rememberSaveable(key){mutableStateOf(draft["side"]?:value?.side?:"buy")}
    var effect by rememberSaveable(key){mutableStateOf(draft["effect"]?:value?.effect?:"open")}
    var price by rememberSaveable(key){mutableStateOf(draft["price"]?:value?.price?:"")}
    var quantity by rememberSaveable(key){mutableStateOf(draft["quantity"]?:value?.quantity?:"")}
    var strategy by rememberSaveable(key){mutableStateOf(draft["strategy"]?.takeIf{it.isNotEmpty()}?:value?.strategyId)}
    var plan by rememberSaveable(key){mutableStateOf(draft["plan"]?.takeIf{it.isNotEmpty()}?:value?.planId)}
    var tp by rememberSaveable(key){mutableStateOf(draft["tp"]?:value?.takeProfit?:"")}
    var stop by rememberSaveable(key){mutableStateOf(draft["stop"]?:value?.stopLoss?:"")}
    var details by rememberSaveable(key){mutableStateOf(false)}
    var picker by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var planPicker by remember{mutableStateOf(false)}
    val catalog by model.instruments.collectAsState()
    val reference=if(account=="futures")FuturesReferenceCatalog.find(code)else null
    val displayName=name.ifBlank{catalog.firstOrNull{it.market==account && it.code.equals(code,true)}?.name?.removeSuffix(" "+code)?:reference?.name?:code}
    val fields=mapOf("mode" to key,"id" to id,"account" to account,"code" to code,"name" to name,"side" to side,"effect" to effect,"price" to price,"quantity" to quantity,"strategy" to strategy.orEmpty(),"plan" to plan.orEmpty(),"tp" to tp,"stop" to stop)
    LaunchedEffect(fields){model.draft(fields)}
    val initial=value?:LedgerExecution(id,account,code,displayName,stamp.toLocalDate().toEpochDay(),stamp.hour*60+stamp.minute,stamp.toLocalDate().toEpochDay(),0,side,effect,price,quantity,"0",feeSource=AutomaticEntry.SOURCE,second=stamp.second)
    val candidate=initial.copy(accountId=account,code=code.trim().lowercase(),name=displayName.trim(),side=side,effect=if(account=="stock")if(side=="buy")"open"else"close"else effect,price=price.trim(),quantity=quantity.trim(),strategyId=strategy,planId=plan,takeProfit=tp.trim().ifBlank{null},stopLoss=stop.trim().ifBlank{null})
    val estimate by produceState(AutomaticEntry.Estimate(null,null,"预计保证金占用"),candidate,state.executions,state.rules){
        this.value=AutomaticEntry.Estimate(null,null,"预计保证金占用")
        this.value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default){AutomaticEntry.estimate(candidate,state)}
    }
    val busy by model.busy.collectAsState();val problem by model.problem.collectAsState()
    BackHandler{model.closeEditor()}
    Dialog(onDismissRequest={model.closeEditor()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        val view=LocalView.current;val dark=isSystemInDarkTheme()
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{w->w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowCompat.getInsetsController(w,view).isAppearanceLightStatusBars=!dark}}
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),color=MaterialTheme.colorScheme.background){Column{
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                TextButton({model.closeEditor()},Modifier.heightIn(min=48.dp).testTag("entry-cancel")){Text("返回")}
                Text(if(value==null)"记一笔"else"修改成交",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onRisk,Modifier.heightIn(min=48.dp)){Text("风险与策略",style=MaterialTheme.typography.labelMedium)}
            }
            problem?.let{Text(it,Modifier.padding(horizontal=12.dp).testTag("entry-error"),color=MaterialTheme.colorScheme.error)}
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=12.dp).testTag("entry-scroll"),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(value==null)SegmentedChoices(account,listOf("stock" to "A股","futures" to "国内期货"),{if(account!=it){account=it;code="";name="";plan=null;side="buy";effect="open"}},"entry-market-")
                EntryField(if(account=="stock")"股票代码"else"合约代码",code,{code=it.trim().lowercase();name="";plan=null},"execution-code",keyboardType=if(account=="stock")androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Ascii,trailing={
                    TextButton({picker=true},Modifier.heightIn(min=48.dp).testTag("entry-instrument-picker")){Text("常用",style=MaterialTheme.typography.labelLarge)}
                })
                Text(displayName.ifBlank{if(account=="stock")"输入代码，或选择常用股票"else"输入合约代码，如 rb2701"},Modifier.padding(start=4.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(account=="stock")SegmentedChoices(side,listOf("buy" to "买入","sell" to "卖出"),{side=it},"execution-side-")
                else SegmentedChoices("$side-${if(effect=="open")"open"else"close"}",listOf("buy-open" to "买开","sell-open" to "卖开","sell-close" to "卖平","buy-close" to "买平"),{val current="$side-${if(effect=="open")"open"else"close"}";if(it!=current){side=it.substringBefore('-');effect=it.substringAfter('-')}},"execution-action-")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    EntryField("成交价格",price,{price=it},"execution-price",true,Modifier.weight(1f))
                    EntryField(if(account=="stock")"数量（股）"else"数量（手）",quantity,{quantity=it},"execution-quantity",true,Modifier.weight(1f),keyboardType=androidx.compose.ui.text.input.KeyboardType.Number)
                }
                if(account=="futures" && reference?.multiplier==null && code.isNotBlank() && state.rules.none{it.code==code && it.verified})Text("该品种乘数尚未核实，暂不能保存",Modifier.testTag("entry-unverified"),color=MaterialTheme.colorScheme.error)
                TextButton({details=!details},Modifier.heightIn(min=48.dp).testTag("entry-options")){Text(if(details)"收起可选信息"else"策略、止盈止损（可选）")}
                if(details){
                    EntryField("名称（可选）",name,{name=it},"execution-name")
                    StrategySelect(state,strategy,{strategy=it})
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        EntryField("止盈价（可选）",tp,{tp=it},"execution-tp",true,Modifier.weight(1f))
                        EntryField("止损价（可选）",stop,{stop=it},"execution-stop",true,Modifier.weight(1f))
                    }
                    TextButton({planPicker=true},Modifier.heightIn(min=48.dp)){Text(if(plan==null)"关联已有计划"else"更换已关联计划")}
                    TextButton({model.favorite(account,code,displayName)},Modifier.heightIn(min=48.dp)){Text("保存为常用")}
                    if(account=="futures")Text("费用按当笔保存规则计算，参考值不等于期货公司实际标准；新成交整笔加收0.01元，按分四舍五入。",style=MaterialTheme.typography.bodySmall)
                }
                if(draft.isNotEmpty())Text("已恢复未保存内容",style=MaterialTheme.typography.labelMedium)
            }
            if(androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    EntryAmounts(account,estimate,Modifier.weight(1f),compact=true)
                    Button({model.execution(candidate)},enabled=!busy,modifier=Modifier.heightIn(min=52.dp).testTag("save-execution"),shape=MaterialTheme.shapes.small){Text(if(busy)"正在保存…"else"保存成交")}
                }
            }else{
                Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){EntryAmounts(account,estimate,Modifier.padding(12.dp))}
                Button({model.execution(candidate)},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp).heightIn(min=52.dp).testTag("save-execution"),shape=MaterialTheme.shapes.small){Text(if(busy)"正在保存…"else"保存成交")}
            }
        }}
    }
    if(picker)AlertDialog(onDismissRequest={picker=false},title={Text("常用品种")},text={Column{
        EntryField("名称或代码",query,{query=it},"instrument-search")
        val recent=MatchingEngine.ordered(state.executions.filter{it.accountId==account}).reversed().map{it.code to it.name}
        val saved=catalog.filter{it.market==account}.sortedWith(compareByDescending<com.tradereview.app.data.Instrument>{it.favorite}.thenByDescending{it.lastUsed}).map{it.code to it.name.removeSuffix(" "+it.code)}
        val searched=query.trim().lowercase()
        val searchedPrefix=if(Regex("[a-z]{1,4}[0-9]{3,4}").matches(searched))FuturesReferenceCatalog.find(searched)?.prefix else null
        val builtins=if(account=="futures")FuturesReferenceCatalog.products.map{(if(it.prefix==searchedPrefix)searched else it.prefix) to it.name+if(it.multiplier==null)"（未核实）"else""}else emptyList()
        val options=(recent+saved+builtins).distinctBy{it.first}.filter{(c,n)->query.isBlank() || c.contains(query,true) || n.contains(query,true)}
        LazyColumn(Modifier.heightIn(max=280.dp)){items(options,key={it.first}){(c,n)->TextButton({code=c.lowercase();name=n.removeSuffix("（未核实）");picker=false},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("instrument-$c")){
            Column(Modifier.weight(1f)){Text(n,style=MaterialTheme.typography.bodyLarge);Text(c,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(recent.any{it.first==c})Text("最近使用",style=MaterialTheme.typography.labelSmall)
        }}}
    }},confirmButton={TextButton({picker=false}){Text("关闭")}})
    if(planPicker)AlertDialog(onDismissRequest={planPicker=false},title={Text("关联已有计划")},text={LazyColumn(Modifier.heightIn(max=300.dp)){
        item{TextButton({plan=null;planPicker=false}){Text("不关联")}}
        items(state.plans.filter{it.accountId==account && it.code==code}.sortedByDescending{it.day}){p->TextButton({plan=p.id;if(stop.isBlank())stop=p.stopLoss.orEmpty();if(tp.isBlank())tp=p.takeProfit.orEmpty();planPicker=false},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("${LocalDate.ofEpochDay(p.day)} ${p.note.take(24)}")}}
    }},confirmButton={TextButton({planPicker=false}){Text("关闭")}})
}

@Composable private fun EntryAmounts(account:String,estimate:AutomaticEntry.Estimate,modifier:Modifier=Modifier,compact:Boolean=false){
    val fee=if(estimate.fees==null && estimate.ready)"待估算"else AutomaticEntry.money(estimate.fees)
    val margin=if(estimate.margin==null && estimate.ready)"待估算"else AutomaticEntry.money(estimate.margin)
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            EstimateMetric("预计手续费",fee,"entry-estimated-fee",Modifier.weight(1f),compact)
            if(account=="futures")EstimateMetric(estimate.marginLabel,margin,"entry-estimated-margin",Modifier.weight(1f),compact)
        }
        if(estimate.ready && account=="futures" && estimate.fees==null)Text("可保存，费用待估算，净盈亏待计算",Modifier.testTag("entry-pending-note"),style=MaterialTheme.typography.bodySmall)
    }
}
/** One accessible field reads the full label and value; visual children can have distinct type sizes. */
@Composable private fun EstimateMetric(label:String,value:String,tag:String,modifier:Modifier,compact:Boolean){
    Column(modifier.clearAndSetSemantics{text=AnnotatedString("$label：$value");testTag=tag},verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(when(label){"预计新增保证金占用"->"预计新增保证金";"平仓后预计剩余占用"->"平仓后剩余保证金";else->label},style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        NumericText(value,style=if(compact)MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold)else MaterialTheme.typography.titleMedium,color=if(value=="待估算" || value=="暂无法估算")MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}
