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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
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

@Composable fun V200Trades(state:LedgerSnapshot,onCycle:(String)->Unit,onExecution:(String)->Unit,onArchive:()->Unit){var tab by rememberSaveable{mutableStateOf("positions")};LazyColumn(Modifier.fillMaxSize().testTag("v2-trades"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    item{ChoiceRow(tab,listOf("positions" to "当前持仓","history" to "历史成交"),{tab=it});TextButton(onArchive,modifier=Modifier.heightIn(min=48.dp).testTag("legacy-archive")){Text("兼容历史档案（只读，不计入新统计）")}}
    if(tab=="positions"){val current=state.cycles.filter{decimal(it.remaining)>BigDecimal.ZERO};if(current.isEmpty())item{EmptyLedger("暂无持仓，请在右上角记录真实买入或开仓")};items(current,key={it.id}){PositionCard(state,it,onCycle)}}
    else{val history=MatchingEngine.ordered(state.executions).reversed();if(history.isEmpty())item{EmptyLedger("尚未记录实际成交")};items(history,key={it.id}){e->LedgerPanel("${e.name} ${e.code}",Modifier.clickable{onExecution(e.id)}.testTag("execution-${e.id}")){Text("${LocalDate.ofEpochDay(e.date)} %02d:%02d · ${actionLabel(e)}".format(e.minute/60,e.minute%60));Text("${e.quantity}${if(e.accountId=="stock")"股"else"手"} × ${e.price} · 费用 ${amount(e.fees)}");Text(strategyLabel(state,e.strategyId));if(e.effect!="open")NetText(sum(state.matches.filter{it.closeExecutionId==e.id}.map{decimal(it.net)}))}}}
}}
@Composable fun PositionDetails(state:LedgerSnapshot,id:String){val c=state.cycles.firstOrNull{it.id==id}?:return;val ids=state.lots.filter{it.cycleId==id}.map{it.executionId}.toSet()+state.matches.filter{it.cycleId==id}.map{it.closeExecutionId};val executions=MatchingEngine.ordered(state.executions.filter{it.id in ids})
    LazyColumn(Modifier.fillMaxSize().testTag("v2-position-details"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{PositionCard(state,c,{})};item{LedgerPanel("持仓周期事实"){Text("${state.accounts.first{it.id==c.accountId}.name} · ${directionLabel(c)}");Text("开仓 ${LocalDate.ofEpochDay(c.firstDay)} · 平仓 ${c.closedDay?.let{LocalDate.ofEpochDay(it).toString()}?:"仍持有"}");Text("实际费用 ${amount(c.fees)}");NetText(decimal(c.realized))}};items(executions,key={it.id}){e->LedgerPanel(actionLabel(e)){Text("${LocalDate.ofEpochDay(e.date)} %02d:%02d".format(e.minute/60,e.minute%60));if(e.accountId=="futures")Text("交易日 ${LocalDate.ofEpochDay(e.tradingDay)}");Text("价格 ${e.price} · 数量 ${e.quantity} · 实际费用 ${amount(e.fees)}");Text("策略 ${strategyLabel(state,e.strategyId)}");e.planId?.let{p->Text("人工关联计划 ${state.plans.firstOrNull{it.id==p}?.let{LocalDate.ofEpochDay(it.day).toString()}?:p}")};state.matches.filter{it.closeExecutionId==e.id && it.cycleId==id}.forEach{m->Text("FIFO 匹配 ${m.openExecutionId}：${m.quantity} · 净盈亏 ${amount(m.net)}")}}}}
}
@Composable fun ExecutionDetails(state:LedgerSnapshot,e:LedgerExecution,onEdit:()->Unit,onDelete:()->Unit){EditorBody{LedgerPanel("${e.name} ${e.code} · ${actionLabel(e)}"){Text("${state.accounts.first{it.id==e.accountId}.name} · ${LocalDate.ofEpochDay(e.date)} %02d:%02d".format(e.minute/60,e.minute%60));Text("价格 ${e.price} · 数量 ${e.quantity} · 费用 ${amount(e.fees)}");Text("费用${if(e.feeSource=="actual")"为实际录入"else"按当笔保存规则计算"}");if(e.accountId=="futures"){Text("交易日 ${LocalDate.ofEpochDay(e.tradingDay)} · 乘数 ${e.multiplier}");Text("保证金率 ${e.marginRate?:"未知"}")};Text("策略 ${strategyLabel(state,e.strategyId)}");Text("止盈 ${e.takeProfit?:"未设置"} · 止损 ${e.stopLoss?:"未设置"}");Text("计划风险 ${e.riskAmount?.let(::amount)?:"风险无法计算"} · 当时风险金 ${amount(e.riskFundSnapshot)}");if(e.effect!="open")NetText(sum(state.matches.filter{it.closeExecutionId==e.id}.map{decimal(it.net)}));if(e.riskExceeded)Text("曾超过单笔风险上限；${if(e.riskConfirmed)"用户已确认记录"else"当时关闭提示"}")};OutlinedButton(onEdit,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("edit-execution")){Text("修改成交（先预览影响）")};OutlinedButton(onDelete,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("delete-execution")){Text("删除成交（先预览影响）")}}}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExecutionEditor(model:V200ViewModel,state:LedgerSnapshot,value:LedgerExecution?,onRisk:()->Unit){
    val draft=model.restoredDraft().takeIf{it["mode"]==(value?.id?:"new")}.orEmpty()
    val key=value?.id?:"new"
    var id by rememberSaveable(key){mutableStateOf(value?.id?:draft["id"]?:newKey())}
    var account by rememberSaveable(key){mutableStateOf(draft["account"]?:value?.accountId?:"stock")}
    var code by rememberSaveable(key){mutableStateOf(draft["code"]?:value?.code?:"")};var name by rememberSaveable(key){mutableStateOf(draft["name"]?:value?.name?:"")}
    var date by rememberSaveable(key){mutableLongStateOf(draft["date"]?.toLongOrNull()?:value?.date?:LocalDate.now().toEpochDay())};var minute by rememberSaveable(key){mutableIntStateOf(draft["minute"]?.toIntOrNull()?:value?.minute?:LocalTime.now().let{it.hour*60+it.minute})}
    var tradingDay by rememberSaveable(key){mutableLongStateOf(draft["day"]?.toLongOrNull()?:value?.tradingDay?:date)};var dayConfirmed by rememberSaveable(key){mutableStateOf(value!=null || draft["dayConfirmed"]=="true")}
    var side by rememberSaveable(key){mutableStateOf(draft["side"]?:value?.side?:"buy")};var effect by rememberSaveable(key){mutableStateOf(draft["effect"]?:value?.effect?:"open")}
    var price by rememberSaveable(key){mutableStateOf(draft["price"]?:value?.price?:"")};var quantity by rememberSaveable(key){mutableStateOf(draft["quantity"]?:value?.quantity?:"")}
    var strategy by rememberSaveable(key){mutableStateOf(draft["strategy"]?.takeIf{it.isNotEmpty()}?:value?.strategyId)};var plan by rememberSaveable(key){mutableStateOf(draft["plan"]?.takeIf{it.isNotEmpty()}?:value?.planId)}
    var tp by rememberSaveable(key){mutableStateOf(draft["tp"]?:value?.takeProfit?:"")};var stop by rememberSaveable(key){mutableStateOf(draft["stop"]?:value?.stopLoss?:"")}
    var multiplier by rememberSaveable(key){mutableStateOf(draft["multiplier"]?:value?.multiplier?:"")};var margin by rememberSaveable(key){mutableStateOf(draft["margin"]?:value?.marginRate?:"")}
    var fee by rememberSaveable(key){mutableStateOf(draft["fee"]?:value?.fees?.takeIf{value.feeSource=="actual"}?:"")};var confirmed by rememberSaveable(key){mutableStateOf(draft["confirmed"]?.toBooleanStrictOrNull()?:value?.parametersConfirmed?:false)}
    var ruleSnapshot by rememberSaveable(key){mutableStateOf(draft["rule"]?.takeIf{it.isNotEmpty()}?:value?.ruleSnapshot)};var parameterDirty by rememberSaveable(key){mutableStateOf(draft["parameterDirty"]=="true")};var parameterCode by rememberSaveable(key){mutableStateOf(code)}
    var secondText by rememberSaveable(key){mutableStateOf(draft["second"]?:value?.second?.toString().orEmpty())};var sequenceText by rememberSaveable(key){mutableStateOf(draft["sequence"]?:value?.ordinal?.toString().orEmpty())}
    var details by rememberSaveable(key){mutableStateOf(false)};var picker by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var planPicker by remember{mutableStateOf(false)}
    val rule=state.rules.filter{it.code==code.lowercase() && it.effectiveDay<=tradingDay}.maxWithOrNull(compareBy<LedgerContractRule>{it.effectiveDay}.thenBy{it.id})
    LaunchedEffect(account,code,rule){if(account=="futures" && (code!=parameterCode || !parameterDirty && value==null)){multiplier=rule?.multiplier.orEmpty();margin=rule?.marginRate.orEmpty();confirmed=rule?.verified==true;ruleSnapshot=rule?.let(LedgerJson::text)};if(code!=parameterCode){parameterDirty=false;parameterCode=code}}
    val fields=mapOf("mode" to key,"id" to id,"account" to account,"code" to code,"name" to name,"date" to date.toString(),"minute" to minute.toString(),"day" to tradingDay.toString(),"dayConfirmed" to dayConfirmed.toString(),"side" to side,"effect" to effect,"price" to price,"quantity" to quantity,"strategy" to strategy.orEmpty(),"plan" to plan.orEmpty(),"tp" to tp,"stop" to stop,"multiplier" to multiplier,"margin" to margin,"fee" to fee,"confirmed" to confirmed.toString(),"rule" to ruleSnapshot.orEmpty(),"parameterDirty" to parameterDirty.toString(),"second" to secondText,"sequence" to sequenceText)
    LaunchedEffect(fields){model.draft(fields)}
    val isNight=account=="futures" && (minute>=20*60 || minute<4*60)
    fun candidate()=LedgerExecution(id,account,if(account=="futures")code.trim().lowercase()else code.trim(),name.trim(),date,minute,if(account=="stock")date else tradingDay,sequenceText.toLongOrNull()?:value?.ordinal?:0,side,if(account=="stock")if(side=="buy")"open"else"close"else effect,price.trim(),quantity.trim(),fee.trim().ifBlank{"0"},feeSource=if(fee.isBlank())"rule"else"actual",feeRateSnapshot=value?.feeRateSnapshot,multiplier=if(account=="futures")multiplier.trim().ifBlank{null}else null,marginRate=if(account=="futures")margin.trim().ifBlank{null}else null,parametersConfirmed=confirmed,ruleSnapshot=if(account=="futures")ruleSnapshot else null,strategyId=strategy,planId=plan,takeProfit=tp.trim().ifBlank{null},stopLoss=stop.trim().ifBlank{null},second=secondText.toIntOrNull())
    val risk=runCatching{LedgerRisk.evaluate(candidate(),state.risks.first{it.id==account})}.getOrNull()
    BackHandler{model.closeEditor()}
    Dialog(onDismissRequest={model.closeEditor()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){val view=LocalView.current;val dark=isSystemInDarkTheme();SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{w->w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowCompat.getInsetsController(w,view).isAppearanceLightStatusBars=!dark}};Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),color=MaterialTheme.colorScheme.background){Column{
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton({model.closeEditor()},modifier=Modifier.heightIn(min=48.dp)){Text("返回")};Text(if(value==null)"记录真实成交"else"修改成交",Modifier.padding(top=14.dp),style=MaterialTheme.typography.titleMedium);TextButton(onRisk,modifier=Modifier.heightIn(min=48.dp)){Text("风险与策略")}}
        (model.problem.collectAsState().value?:model.message.collectAsState().value)?.let{Text(it,Modifier.padding(horizontal=12.dp),color=if(model.problem.collectAsState().value!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            ChoiceRow(account,listOf("stock" to "A股","futures" to "国内期货"),{account=it;code="";name="";plan=null;fee="";side="buy";effect="open"},"entry-market-")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){DateControl(date,{date=it;if(account=="stock")tradingDay=it;dayConfirmed=false});TimeControl(minute,{minute=it;dayConfirmed=false})}
            if(account=="futures"){FlowRow{DateControl(tradingDay,{tradingDay=it;dayConfirmed=true},"交易日","execution-trading-day");if(isNight && !dayConfirmed)TextButton({dayConfirmed=true},modifier=Modifier.heightIn(min=48.dp)){Text("核实夜盘交易日")}}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){EntryField(if(account=="stock")"股票代码"else"合约代码",code,{code=it.trim().lowercase();plan=null},"execution-code",modifier=Modifier.weight(1f));EntryField("名称",name,{name=it},"execution-name",modifier=Modifier.weight(1f))}
            FlowRow{TextButton({picker=true},modifier=Modifier.heightIn(min=48.dp)){Text("搜索常用品种")};TextButton({model.favorite(account,code,name)},modifier=Modifier.heightIn(min=48.dp)){Text("保存为常用")}}
            ChoiceRow(side,listOf("buy" to "买入","sell" to "卖出"),{side=it},"execution-side-")
            if(account=="futures")ChoiceRow(effect,listOf("open" to "开仓","close" to "平仓","close_today" to "平今","close_yesterday" to "平昨"),{effect=it},"execution-effect-")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){EntryField("成交价格",price,{price=it},"execution-price",true,Modifier.weight(1f));EntryField(if(account=="stock")"数量（股）"else"数量（手）",quantity,{quantity=it},"execution-quantity",true,Modifier.weight(1f))}
            StrategySelect(state,strategy,{strategy=it})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){EntryField("止盈价（可选）",tp,{tp=it},"execution-tp",true,Modifier.weight(1f));EntryField("止损价（可选）",stop,{stop=it},"execution-stop",true,Modifier.weight(1f))}
            Text("计划风险 ${risk?.amount?.let(::amount)?:"风险无法计算"} · 风险金 ${amount(risk?.fund)}\n使用比例 ${risk?.percent?.plus("%")?:"无法计算"} · 单笔上限 ${amount(risk?.limit)}",color=if(risk?.exceeded==true)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({planPicker=true},modifier=Modifier.heightIn(min=48.dp)){Text(if(plan==null)"人工关联已有标的计划（可选）"else"已关联计划，可更换")}
            TextButton({details=!details},modifier=Modifier.heightIn(min=48.dp).testTag("execution-parameters")){Text(if(details)"收起参数与费用"else"展开参数与费用")}
            if(details){EntryField("成交秒（可选，0至59）",secondText,{secondText=it},"execution-second",true);EntryField("同分钟先后序号（可选，自动按录入递增）",sequenceText,{sequenceText=it},"execution-sequence",true);if(account=="futures"){EntryField("每手合约乘数",multiplier,{multiplier=it;parameterDirty=true},"execution-multiplier",true);EntryField("保证金率（可选，0至1）",margin,{margin=it;parameterDirty=true},"execution-margin",true);Row{Checkbox(confirmed,{confirmed=it;parameterDirty=true});Text("本笔合约参数已经核实",Modifier.padding(top=14.dp))}};EntryField("实际手续费（留空按已确认规则）",fee,{fee=it},"execution-fees",true);Text(if(account=="stock")"综合费率已含税费，不重复扣费。"else"规则不完整时需录入实际费用；加收单位必须明确。",style=MaterialTheme.typography.bodySmall)}
            if(draft.isNotEmpty())Text("已恢复未保存内容",style=MaterialTheme.typography.labelMedium)
        }
        Button(onClick={if(secondText.isNotBlank() && secondText.toIntOrNull()==null || sequenceText.isNotBlank() && (sequenceText.toLongOrNull()==null || sequenceText.toLongOrNull()!!<=0)){model.problem.value="成交秒或序号无效，未保存"}else model.execution(candidate())},enabled=!model.busy.collectAsState().value && (!isNight || dayConfirmed),modifier=Modifier.fillMaxWidth().padding(12.dp).heightIn(min=52.dp).testTag("save-execution")){Text("保存真实成交")}
    }}}
    if(picker)AlertDialog(onDismissRequest={picker=false},title={Text("搜索与复用品种")},text={Column{EntryField("名称或代码",query,{query=it},"instrument-search");val catalog=model.instruments.collectAsState().value;val options=(MatchingEngine.ordered(state.executions.filter{it.accountId==account}).reversed().map{it.code to it.name}+state.rules.filter{account=="futures"}.map{it.code to it.name}+catalog.filter{it.market==account}.sortedByDescending{it.lastUsed}.map{it.code to it.name.removeSuffix(" "+it.code)}).distinctBy{it.first}.filter{(c,n)->runCatching{LedgerValidation.code(account,c.lowercase())}.isSuccess && (query.isBlank() || c.contains(query,true) || n.contains(query,true))};LazyColumn(Modifier.heightIn(max=260.dp)){items(options){(c,n)->TextButton({code=c.lowercase();name=n;picker=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("$n $c")}}}}},confirmButton={TextButton({picker=false}){Text("关闭")}})
    if(planPicker)AlertDialog(onDismissRequest={planPicker=false},title={Text("选择已有标的计划")},text={LazyColumn(Modifier.heightIn(max=300.dp)){item{TextButton({plan=null;planPicker=false}){Text("不关联")}};items(state.plans.filter{it.accountId==account && it.code==code}.sortedByDescending{it.day}){p->TextButton({plan=p.id;if(stop.isBlank())stop=p.stopLoss.orEmpty();if(tp.isBlank())tp=p.takeProfit.orEmpty();planPicker=false},modifier=Modifier.heightIn(min=48.dp)){Text("${LocalDate.ofEpochDay(p.day)} ${p.note.take(24)}")}}}},confirmButton={TextButton({planPicker=false}){Text("关闭")}})
}
