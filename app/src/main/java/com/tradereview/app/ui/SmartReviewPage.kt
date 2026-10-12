package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager

private data class InsightSnapshot(val state:JournalState,val period:ReviewPeriod?,val rows:List<Insight>)
private data class PreflightSnapshot(val date:Long,val state:JournalState,val rows:List<PreflightReminder>)
@Composable
private fun calculatedInsights(state:JournalState,period:ReviewPeriod?=null):List<Insight>? {
    val snapshot by produceState<InsightSnapshot?>(null,state.trades,state.reviews,state.goals,state.tags,state.smartSettings,period) {
        value=withContext(Dispatchers.Default){InsightSnapshot(state,period,SmartInsights.calculate(state.trades,state.reviews,state.goals,state.smartSettings,state.tags,period=period))}
    }
    return snapshot?.takeIf { it.state.trades===state.trades && it.state.reviews===state.reviews && it.state.goals===state.goals && it.state.tags===state.tags && it.state.smartSettings==state.smartSettings && it.period==period }?.rows
}
@Composable
fun InsightCard(row:Insight,state:JournalState,onTrades:(List<Trade>,String)->Unit) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    SectionCard(row.title) {
        Text(Presets.label(SmartOptions.categories,row.category),style=MaterialTheme.typography.labelLarge)
        Text("${dayLabel(row.start)} — ${dayLabel(row.end)}",style=MaterialTheme.typography.bodySmall)
        Text(row.evidence)
        Text("有效样本 ${row.sample} · 对照 ${row.previousSample} · 相关记录 ${row.tradeKeys.size}",modifier=Modifier.testTag("insight-sample-${row.id}"))
        if(minOf(row.sample,row.previousSample)<20)Text("样本较少，观察可能不稳定。",color=MaterialTheme.colorScheme.primary)
        Text(row.suggestion)
        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起依据"else "展开计算依据")}
        if(expanded) { Text("对照：${dayLabel(row.previousStart)} — ${dayLabel(row.previousEnd)}");Text(row.formula);Text("仅为本地描述性复盘，不作收益预测或因果推断。") }
        TextButton(onClick={val keys=row.tradeKeys.toHashSet();onTrades(state.trades.filter { it.recordKey in keys },row.title)},modifier=Modifier.testTag("insight-trades-${row.id}")){Text("查看涉及的交易")}
    }
}
@Composable
fun ReportInsights(state:JournalState,period:ReviewPeriod,onTrades:(List<Trade>,String)->Unit) {
    val rows=calculatedInsights(state,period)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("复盘洞察",style=MaterialTheme.typography.titleMedium)
        Text("本期与上一完整周期比较；目标使用自身周/月口径。",style=MaterialTheme.typography.bodySmall)
        if(rows==null)LinearProgressIndicator(Modifier.fillMaxWidth())else if(rows.isEmpty())Text("暂无足够记录或没有达到已启用规则的阈值。")
        else rows.take(6).forEach { InsightCard(it,state,onTrades) }
        if(rows!=null && rows.size>6)Text("本报告展示优先级最高的 6 项；完整近期洞察见首页入口。")
    }
}
@Composable
fun SmartReviewPage(state:JournalState,model:TradeViewModel,busy:Boolean,onTrades:(List<Trade>,String)->Unit,onPreflight:()->Unit,modifier:Modifier=Modifier) {
    val rows=calculatedInsights(state);var settings by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().testTag("smart-insights-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { SectionCard("智能洞察","只使用本地真实记录与规则，不接 AI；不提供具体买卖建议。") {
            Text("近 ${state.smartSettings.lookbackDays} 天，对照此前等长区间；最低有效样本 ${state.smartSettings.minimumSamples}。")
            OutlinedButton(onClick=onPreflight,modifier=Modifier.fillMaxWidth()){Text("盘前纪律检查清单")}
            TextButton(onClick={settings=!settings}){Text("洞察规则设置")}
            if(settings) {
                Text("提示类别 · 可多选")
                ChoiceChips(SmartOptions.categories,state.smartSettings.enabledCategories) { id->if(!busy)model.smartSettings(state.smartSettings.copy(enabledCategories=toggleTag(state.smartSettings.enabledCategories,id))) }
                Text("最低有效样本量")
                SingleChoice(listOf(3,10,20,30,50,100).map { Option(it.toString(),it.toString()) },state.smartSettings.minimumSamples.toString()){if(!busy)model.smartSettings(state.smartSettings.copy(minimumSamples=it.toInt()))}
                Text("近期观察窗口")
                SingleChoice(listOf(7,14,30).map { Option(it.toString(),"$it 天") },state.smartSettings.lookbackDays.toString()){if(!busy)model.smartSettings(state.smartSettings.copy(lookbackDays=it.toInt()))}
                Row { Checkbox(state.smartSettings.automaticReminders,{if(!busy)model.smartSettings(state.smartSettings.copy(automaticReminders=it))},enabled=!busy);Text("自动生成盘前提醒") }
                Text("样本阈值只影响洞察；历史事实和已检查记录保留。")
            }
        } }
        if(rows==null)item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在本地计算") }
        else if(rows.isEmpty())item { SectionCard("暂无足够记录") { Text("请积累真实记录，或检查已启用类别和样本阈值；也可能暂未触发规则。") } }
        else items(rows,key={it.id}) { InsightCard(it,state,onTrades) }
    }
}
@Composable
fun PreflightPage(date:Long,state:JournalState,model:TradeViewModel,busy:Boolean,onDate:(Long)->Unit,onTrades:(List<Trade>,String)->Unit,modifier:Modifier=Modifier) {
    val reminderSnapshot by produceState<PreflightSnapshot?>(null,date,state.trades,state.reviews,state.goals,state.riskRules,state.smartSettings,state.tags) {
        value=withContext(Dispatchers.Default){PreflightSnapshot(date,state,PreflightRules.reminders(date,state.trades,state.reviews,state.goals,state.riskRules,state.smartSettings,state.tags))}
    }
    val reminders=reminderSnapshot?.takeIf { it.date==date && it.state.trades===state.trades && it.state.reviews===state.reviews && it.state.goals===state.goals && it.state.riskRules===state.riskRules && it.state.tags===state.tags && it.state.smartSettings==state.smartSettings }?.rows
    var newText by rememberSaveable { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    val manual=state.preflightItems.filter { !it.archived }.map { PreflightReminder("manual:${it.id}",it.text,emptyList()) }
    val saved=state.preflightChecks.filter { it.date==date }
    val rows=(saved.map { PreflightReminder(it.itemKey,it.label,it.sourceRefs,state.trades.filter { t->"trade:${t.recordKey}" in it.sourceRefs }) }+manual+reminders.orEmpty()).distinctBy { it.key }
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("preflight-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { SectionCard("盘前纪律检查","勾选代表已检查，不代表交易已遵守；与每日计划共用日期，不替代计划或阻止记账。") {
            DateControl(date,onDate)
            Row { Checkbox(state.smartSettings.automaticReminders,{if(!busy)model.smartSettings(state.smartSettings.copy(automaticReminders=it))},enabled=!busy);Text("自动历史提醒") }
            if(!state.smartSettings.automaticReminders)Text("已关闭新提醒生成；本日已保存检查历史继续保留。",style=MaterialTheme.typography.bodySmall)
            Text("${saved.count { it.checked }} 项已检查；历史依据按检查时保存，修改交易不会改写检查记录。")
        } }
        if(reminders==null)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(rows.isEmpty() && reminders!=null)item { Text("暂无历史提醒，可添加自己的盘前项目。") }
        items(rows,key={it.key}) { row->val snapshot=saved.firstOrNull { it.itemKey==row.key }
            SectionCard(if(row.key.startsWith("manual:"))"个人检查"else "历史与目标提醒") {
                Row { Checkbox(snapshot?.checked==true,onCheckedChange={checked->model.preflightCheck(PreflightCheck(date,row.key,row.text,checked,if(row.key.startsWith("manual:"))"manual"else "auto",row.refs))},enabled=!busy && date<=LocalDate.now().toEpochDay(),modifier=Modifier.testTag("check-${row.key}"));Text(row.text,Modifier.weight(1f)) }
                if(row.refs.isNotEmpty()) { Text("依据：${row.refs.count { it.startsWith("trade:") }} 条交易引用 · ${row.refs.count { it.startsWith("goal:") }} 个个人目标 · ${row.refs.count { it.startsWith("review:") }} 个日志日期 · ${row.refs.count { it.startsWith("rule:") }} 组个人风险规则。已删除的交易不会改写检查历史。",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={onTrades(row.trades,"盘前提醒相关交易")}){Text("查看相关交易（${row.trades.size} 笔）")} }
            }
        }
        item { SectionCard("添加个人盘前项目") {
            OutlinedTextField(newText,{if(it.length<=120)newText=it},label={Text("简短检查事项")},modifier=Modifier.fillMaxWidth().testTag("preflight-new-text"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}))
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Button(enabled=!busy,onClick={if(newText.isBlank())error="请输入检查事项"else { error=null;keyboard?.hide();focus.clearFocus();model.preflightItem(PreflightItem(text=newText.trim())){newText=""} }},modifier=Modifier.testTag("preflight-add")){Text("添加检查项目")}
            Text("归档保留已检查历史")
            state.preflightItems.filter { !it.archived }.forEach { item->TextButton(enabled=!busy,onClick={model.preflightItem(item.copy(archived=true))}){Text("归档：${item.text}")} }
        } }
    }
}
