package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.newKey
import java.time.LocalDate

@Composable fun V200RiskPage(model:V200ViewModel,state:LedgerSnapshot,onRules:()->Unit,onStrategyHistory:(String)->Unit){var tab by rememberSaveable{mutableStateOf("risk")};var editingId by rememberSaveable{mutableStateOf<String?>(null)};var creating by rememberSaveable{mutableStateOf(false)};val editing=state.strategies.firstOrNull{it.id==editingId};LazyColumn(Modifier.fillMaxSize().imePadding().testTag("v2-risk-strategy"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    item{ChoiceRow(tab,listOf("risk" to "风险配置","strategies" to "策略管理"),{tab=it},"risk-section-")}
    if(tab=="risk"){items(state.risks,key={it.id}){r->MarketRiskForm(model,r)};item{OutlinedButton(onRules,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("contract-rules-entry")){Text("期货合约参数与手续费")}}}
    else{item{Button({creating=true;editingId=null},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("new-strategy")){Text("新建策略")}};items(state.strategies.sortedWith(compareBy<LedgerStrategy>{it.sortOrder}.thenBy{it.id}),key={it.id}){s->LedgerPanel(s.name+if(s.active)""else"（停用）"){if(s.description.isNotBlank())Text(s.description);Row{TextButton({editingId=s.id;creating=true}){Text("编辑")};TextButton({model.strategy(s.copy(active=!s.active))}){Text(if(s.active)"停用"else"启用")};TextButton({onStrategyHistory(s.id)}){Text("历史")}};Row{TextButton({model.moveStrategy(s.id,-1)},modifier=Modifier.heightIn(min=48.dp)){Text("上移")};TextButton({model.moveStrategy(s.id,1)},modifier=Modifier.heightIn(min=48.dp)){Text("下移")}}}}}
    }
    if(creating){
        var name by rememberSaveable(editingId){mutableStateOf(editing?.name.orEmpty())}
        var description by rememberSaveable(editingId){mutableStateOf(editing?.description.orEmpty())}
        ReviewForm(if(editing==null)"新建策略"else"编辑策略","保存","save-strategy",null,false,{creating=false},{
            model.strategy((editing?:LedgerStrategy(newKey(),name,sortOrder=(state.strategies.maxOfOrNull{it.sortOrder}?:0)+1)).copy(name=name.trim(),description=description))
            if(name.isNotBlank())creating=false
        }){
            EntryField("名称",name,{name=it},"strategy-name")
            EntryField("简短说明",description,{description=it.take(200)},"strategy-description",lines=2)
        }
    }
}
@Composable private fun MarketRiskForm(model:V200ViewModel,value:MarketRisk){var fund by rememberSaveable(value){mutableStateOf(value.riskFund.orEmpty())};var mode by rememberSaveable(value){mutableStateOf(value.limitMode)};var limit by rememberSaveable(value){mutableStateOf(value.limitValue.orEmpty())};var defaultLimit by rememberSaveable(value){mutableStateOf(value.defaultLimit.orEmpty())};var warn by rememberSaveable(value){mutableStateOf(value.warnEnabled)};LedgerPanel(if(value.id=="stock")"股票风险配置"else"国内期货风险配置"){
    Text("风险金是风险基准，与账户资金分开。",style=MaterialTheme.typography.bodySmall)
    EntryField("全局风险金（人民币元）",fund,{fund=it},"risk-fund-${value.id}",true)
    ChoiceRow(mode,listOf("amount" to "固定金额","percent" to "风险金百分比"),{mode=it})
    EntryField(if(mode=="amount")"单笔最大风险（元）"else"单笔最大风险（%）",limit,{limit=it},"risk-limit-${value.id}",true)
    EntryField("默认风险上限（元，可选）",defaultLimit,{defaultLimit=it},number=true)
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=warn,role=Role.Switch,onValueChange={warn=it}),verticalAlignment=Alignment.CenterVertically){Switch(warn,null);Text("超风险提示",Modifier.padding(12.dp))}
    Button({model.risk(value.copy(riskFund=fund.trim().ifBlank{null},limitMode=mode,limitValue=limit.trim().ifBlank{null},defaultLimit=defaultLimit.trim().ifBlank{null},warnEnabled=warn))},enabled=!model.busy.collectAsState().value,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("save-risk-${value.id}")){Text("保存${if(value.id=="stock")"股票"else"期货"}风险配置")}
}}
internal fun contractRuleMatches(rule:LedgerContractRule,query:String):Boolean {
    val text=query.trim()
    return text.isEmpty() || rule.code.contains(text,true) || rule.name.contains(text,true)
}
internal fun contractCodePrefill(query:String):String = query.trim().lowercase().takeIf{Regex("[a-z]{1,4}[0-9]{3,4}").matches(it)}.orEmpty()

internal fun contractRuleSummary(rule:LedgerContractRule):List<String> {
    val mode=when(rule.feeMode){"per_lot"->"元/手";"turnover_rate"->"成交额费率";else->"未知（待估算）"}
    val unit=if(rule.feeMode=="per_lot")" 元/手"else if(rule.feeMode=="turnover_rate")"（成交额比例）"else ""
    fun fee(value:String?)=value?.let{it+unit}?:"未设置（待估算）"
    val margin=rule.marginRate?.let{java.math.BigDecimal(it).movePointRight(2).stripTrailingZeros().toPlainString()+"%"}?:"未设置（待估算）"
    val extra=when(rule.addMode){"none"->"无";"per_lot"->"${rule.addValue?:"未设置"} 元/手";"per_execution"->"${rule.addValue?:"未设置"} 元/笔";else->"${rule.addValue?:"未设置"}（成交额比例）"}
    return listOf("生效 ${LocalDate.ofEpochDay(rule.effectiveDay)} · ${if(rule.verified)"用户已核实"else"未核实"}",
        "乘数 ${rule.multiplier?:"未设置"} · 保证金率 $margin","手续费类型：$mode",
        "开仓：${fee(rule.openFee)}","平昨/普通平仓：${fee(rule.closeFee)}","平今：${fee(rule.todayFee)}","旧加收参数（兼容）：$extra")
}

@Composable fun ContractRulesPage(model:V200ViewModel,state:LedgerSnapshot,initialCode:String="") {
    var query by rememberSaveable(initialCode){mutableStateOf(initialCode)}
    var editingId by rememberSaveable{mutableStateOf<String?>(null)}
    var show by rememberSaveable{mutableStateOf(false)}
    val edit=state.rules.firstOrNull{it.id==editingId}
    val visible=state.rules.filter{contractRuleMatches(it,query)}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("v2-contract-rules"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{EntryField("合约代码或名称，如 rb、rb2701",query,{query=it},"contract-rule-search",trailing={if(query.isNotEmpty())TextButton({query=""}){Text("清除")}})}
        item{
            Text("自定义覆盖规则（${visible.size}/${state.rules.size}）",style=MaterialTheme.typography.titleMedium)
            Text("内置44个品种参考仍用于成交录入；这里仅管理自定义覆盖规则。新成交仍按既有规则整笔加收0.01元，不改写历史。",style=MaterialTheme.typography.bodySmall)
        }
        item{Button({editingId=null;show=true},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("new-contract-rule")){Text("新增合约规则")}}
        if(visible.isEmpty())item{EmptyLedger(if(query.isBlank())"暂无自定义覆盖规则；内置参考参数仍然保留。"else"没有匹配的自定义规则；可清除搜索，或为此合约新增规则。")}
        items(visible,key={it.id}){r->LedgerPanel("${r.code} · ${r.name}",Modifier.testTag("contract-rule-${r.id}")){
            contractRuleSummary(r).forEach{Text(it)}
            TextButton({editingId=r.id;show=true},modifier=Modifier.heightIn(min=48.dp)){Text("编辑，不改写历史")}
        }}
    }
    if(show)ContractRuleDialog(edit,state.rules,{model.rule(it);show=false},{show=false},contractCodePrefill(query))
}
@Composable private fun ContractRuleDialog(value:LedgerContractRule?,rules:List<LedgerContractRule>,onSave:(LedgerContractRule)->Unit,onDismiss:()->Unit,initialCode:String=""){var code by rememberSaveable(value?.id,initialCode){mutableStateOf(value?.code?:initialCode)};var name by rememberSaveable(value?.id){mutableStateOf(value?.name.orEmpty())};var multiplier by rememberSaveable(value?.id){mutableStateOf(value?.multiplier.orEmpty())};var margin by rememberSaveable(value?.id){mutableStateOf(value?.marginRate.orEmpty())};var feeMode by rememberSaveable(value?.id){mutableStateOf(value?.feeMode?:"per_lot")};var open by rememberSaveable(value?.id){mutableStateOf(value?.openFee.orEmpty())};var close by rememberSaveable(value?.id){mutableStateOf(value?.closeFee.orEmpty())};var today by rememberSaveable(value?.id){mutableStateOf(value?.todayFee.orEmpty())};val addMode=value?.addMode?:"none";val add=value?.addValue.orEmpty();var day by rememberSaveable(value?.id){mutableLongStateOf(value?.effectiveDay?:AutomaticEntry.today().toEpochDay())};var verified by rememberSaveable(value?.id){mutableStateOf(value?.verified?:false)};var error by remember{mutableStateOf<String?>(null)}
    ReviewForm("合约参数（离线，须自行核实）","保存","save-contract-rule",error,false,onDismiss,{
        val candidate=LedgerContractRule(value?.id?:newKey(),code.trim().lowercase(),name.trim(),multiplier.trim().ifBlank{null},margin.trim().ifBlank{null},feeMode,open.trim().ifBlank{null},close.trim().ifBlank{null},today.trim().ifBlank{null},addMode,if(addMode=="none")null else add.trim().ifBlank{null},day,verified)
        try{LedgerValidation.rule(candidate);ledgerRequire(rules.none{it.id!=candidate.id && it.code==candidate.code && it.effectiveDay==candidate.effectiveDay},"同合约同生效日已有规则，请编辑原规则或选择其他日期");onSave(candidate)}catch(e:IllegalArgumentException){error=e.message}
    }){
        EntryField("完整合约代码，如rb2701",code,{code=it.lowercase()},"rule-code");EntryField("品种名称",name,{name=it},"rule-name");EntryField("每手合约乘数",multiplier,{multiplier=it},"rule-multiplier",true);EntryField("保证金率（0至1，可选）",margin,{margin=it},"rule-margin",true)
        ChoiceRow(feeMode,listOf("per_lot" to "元/手","turnover_rate" to "成交额费率"),{feeMode=it})
        EntryField("开仓手续费",open,{open=it},"rule-open-fee",true);EntryField("平昨/普通平仓手续费",close,{close=it},"rule-close-fee",true);EntryField("平今手续费",today,{today=it},"rule-today-fee",true)
        Text("新成交的个人加收固定为整笔0.01元；旧规则中的加收字段保留兼容，不改写历史费用。",style=MaterialTheme.typography.bodySmall)
        DateControl(day,{day=it},"生效日期");Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("rule-verified").toggleable(value=verified,role=Role.Checkbox,onValueChange={verified=it}),verticalAlignment=Alignment.CenterVertically){Checkbox(verified,null);Text("我已核实此合约参数",Modifier.weight(1f))}
    }
}
