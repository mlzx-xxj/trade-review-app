package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private data class CalculatedGoal(val goal: ReviewGoal,val period: ReviewPeriod,val trades: List<Trade>,val reviews: List<DailyReview>,
    val progress: GoalProgress,val history: List<Pair<ReviewPeriod,GoalProgress>>)
@Composable
fun GoalProgressCard(goal: ReviewGoal,state: JournalState,period: ReviewPeriod,showHistory: Boolean = false) {
    val calculated by produceState<CalculatedGoal?>(null,goal,period,state.trades,state.reviews) {
        value=withContext(Dispatchers.Default) { CalculatedGoal(goal,period,state.trades,state.reviews,ReviewGoals.progress(goal,state.trades,state.reviews,period),ReviewGoals.history(goal,state.trades,state.reviews,period)) }
    }
    val data=calculated?.takeIf { it.goal==goal && it.period==period && it.trades===state.trades && it.reviews===state.reviews }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(goal.title,style=MaterialTheme.typography.titleSmall)
        goal.mistakeId?.let { Text("错误标签：${LocalCatalog.current.label("mistake",it)}",style=MaterialTheme.typography.bodySmall) }
        if(data==null)LinearProgressIndicator(Modifier.fillMaxWidth()) else {
            val p=data.progress
            fun label(progress: GoalProgress) = progress.value?.let { if(goal.kind.endsWith("ratio"))it.setScale(2,RoundingMode.HALF_UP).toPlainString()+"%"else ProfessionalMath.plain(it) } ?: "不可自动计算"
            Text("实际 ${label(p)} · ${if(goal.kind=="text") "文字目标" else "阈值 ${goal.threshold}${if(goal.kind.endsWith("ratio")) "%" else ""}"}",modifier=Modifier.testTag("goal-progress-${goal.id}"))
            Text("${if(p.reached==true) "本期达到阈值" else if(p.reached==false) "本期尚未达到阈值" else "缺少可计算数据"} · 有效分母 ${p.denominator?.toString() ?: "—"} · 缺失 ${p.missing}")
            Text(p.explanation,style=MaterialTheme.typography.bodySmall)
            val value=p.value;val threshold=goal.threshold?.toBigDecimalOrNull()
            if(value!=null && threshold!=null) {
                val ratio=if(goal.kind.endsWith("count")) { if(p.reached==true)BigDecimal.ONE else if(value.signum()==0)BigDecimal.ZERO else threshold.divide(value,8,RoundingMode.HALF_UP) }
                    else if(threshold.signum()==0)BigDecimal.ONE else value.divide(threshold,8,RoundingMode.HALF_UP)
                LinearProgressIndicator(progress={ratio.coerceIn(BigDecimal.ZERO,BigDecimal.ONE).toFloat()},modifier=Modifier.fillMaxWidth())
            }
            if(p.denominator!=null && p.denominator<20 && goal.kind!="review_days")Text("有效样本少于 20，进度比较可能不稳定。",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
            if(showHistory) { Text("最近四个周期的真实进度",style=MaterialTheme.typography.labelLarge)
                if(data.history.any { it.second.value!=null })GoalHistoryChart(goal,data.history)
                else Text("尚无可量化的历史进度；文字目标不自动打分。",style=MaterialTheme.typography.bodySmall)
                data.history.forEach { (range,progress) -> Text("${dayLabel(range.start)}：${label(progress)} · 分母 ${progress.denominator?.toString() ?: "—"}",style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
@Composable
private fun GoalHistoryChart(goal: ReviewGoal,history: List<Pair<ReviewPeriod,GoalProgress>>) {
    var selected by remember(history) { mutableIntStateOf(history.lastIndex) }
    val maximum=history.mapNotNull { it.second.value }.maxOrNull()?.max(BigDecimal.ONE) ?: BigDecimal.ONE
    val color=MaterialTheme.colorScheme.primary;val outline=MaterialTheme.colorScheme.outlineVariant
    val p=history[selected]
    Text("${dayLabel(p.first.start)}：${p.second.value?.let { if(goal.kind.endsWith("ratio"))it.setScale(2,RoundingMode.HALF_UP).toPlainString()+"%"else ProfessionalMath.plain(it) } ?: "不可计算"} · 分母 ${p.second.denominator?.toString() ?: "—"}",style=MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().testTag("goal-history-${goal.id}"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        history.forEachIndexed { index,(period,progress) -> Column(Modifier.weight(1f).clickable { selected=index }) {
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                drawLine(outline,Offset(0f,size.height-2),Offset(size.width,size.height-2),1.dp.toPx())
                val v=progress.value
                if(v!=null) {
                    val height=v.divide(maximum,8,RoundingMode.HALF_UP).toFloat()*(size.height-4)
                    if(v.signum()==0)drawLine(color,Offset(size.width*.2f,size.height-2),Offset(size.width*.8f,size.height-2),3.dp.toPx())
                    else drawRect(color.copy(alpha=if(index==selected)1f else .55f),Offset(size.width*.2f,size.height-2-height),Size(size.width*.6f,height))
                }
            }
            Text(LocalDate.ofEpochDay(period.start).let { "${it.monthValue}/${it.dayOfMonth}" },style=MaterialTheme.typography.labelSmall)
            if(progress.value==null)Text("—",style=MaterialTheme.typography.labelSmall)
        } }
    }
    val maximumLabel=if(goal.kind.endsWith("ratio"))maximum.setScale(2,RoundingMode.HALF_UP).toPlainString()+"%"else ProfessionalMath.plain(maximum)
    Text("纵轴 0—$maximumLabel；— 表示不可计算，点击柱形查看值。",style=MaterialTheme.typography.bodySmall)
}
@Composable
fun ReviewGoalsPage(state: JournalState,model: TradeViewModel,busy: Boolean,modifier: Modifier = Modifier) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var anchor by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var archiving by remember { mutableStateOf<ReviewGoal?>(null) }
    val rows=state.goals.filter { (it.archivedDay!=null)==archived }
    LazyColumn(modifier.fillMaxSize().testTag("goals-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { SectionCard("个人纪律改善目标","参考进度不改变交易事实；已保存规则保持稳定，调整规则请新建目标。") {
            SingleChoice(listOf(Option("active","进行中"),Option("archived","已归档")),if(archived)"archived"else "active"){archived=it=="archived"}
            DateControl(anchor,{anchor=it})
            Button(enabled=!busy,onClick={creating=true},modifier=Modifier.fillMaxWidth().testTag("new-review-goal")){Text("新增改善目标")}
            Text("目标 ${rows.size} 个 · 周/月历史从真实交易和日志计算；文字目标不自动打分。")
        } }
        if(rows.isEmpty())item { Text(if(archived)"暂无已归档目标"else "暂无进行中目标，可选择预设目标和阈值。") }
        items(rows,key={it.id}) { goal -> SectionCard(if(goal.archivedDay==null)"进行中"else "已归档 · ${dayLabel(goal.archivedDay)}") {
            val period=ReviewPeriod.at(goal.period,LocalDate.ofEpochDay(anchor))
            Text("${dayLabel(period.start)} — ${dayLabel(period.end)} · 自 ${dayLabel(goal.startDate)} 起")
            GoalProgressCard(goal,state,period,showHistory=true)
            if(goal.archivedDay==null)TextButton(enabled=!busy,onClick={archiving=goal},modifier=Modifier.testTag("archive-goal-${goal.id}")){Text("归档目标，保留历史")}
            else TextButton(onClick={anchor=goal.archivedDay}){Text("查看归档前历史")}
        } }
    }
    if(creating)ReviewGoalEditor(state,busy,{creating=false}){g->model.saveGoal(g){creating=false}}
    archiving?.let { g -> AlertDialog(onDismissRequest={archiving=null},title={Text("归档这项目标？")},text={Text("目标与规则保留，历史仍可查看；归档后的日期不纳入进度。不会修改交易记录。")},
        confirmButton={TextButton(enabled=!busy,onClick={archiving=null;model.archiveGoal(g)}){Text("确认归档")}},dismissButton={TextButton(onClick={archiving=null}){Text("取消")}}) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewGoalEditor(state: JournalState,busy: Boolean,onDismiss: ()->Unit,onSave: (ReviewGoal)->Unit) {
    val id=remember { newKey() }
    var kind by rememberSaveable { mutableStateOf("mistake_count") };var period by rememberSaveable { mutableStateOf("week") }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var mistake by rememberSaveable { mutableStateOf(state.tags.firstOrNull { it.kind=="mistake" }?.id ?: "chase") }
    var title by rememberSaveable { mutableStateOf("") };var threshold by rememberSaveable { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    val catalog=LocalCatalog.current
    LocalFullScreenDialog(onDismiss) {
        val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
        Scaffold(modifier=Modifier.imePadding(),topBar={TopAppBar(title={Text("新增改善目标")},navigationIcon={IconButton(enabled=!busy,onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回目标列表")}})},
        bottomBar={Surface(shadowElevation=4.dp){Column(Modifier.fillMaxWidth().padding(16.dp)) {
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("goal-error")) }
            Button(enabled=!busy,onClick={
                try { val goal=ReviewGoal(id=id,title=title.trim().ifBlank { if(kind=="text") "" else Presets.label(EvidenceOptions.goals,kind) },kind=kind,period=period,
                    startDate=ReviewPeriod.at(period,LocalDate.ofEpochDay(date)).start,threshold=if(kind=="text")null else threshold.toBigDecimalOrNull()?.let(ProfessionalMath::plain),mistakeId=if(kind=="mistake_count")mistake else null)
                    ReviewGoals.validate(goal,state.tags);error=null;keyboard?.hide();focus.clearFocus();onSave(goal)
                } catch(e: IllegalArgumentException) { error="请核对名称、目标阈值与生效日期；文字目标需填写名称" }
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("save-review-goal")){Text("保存改善目标")}
        } } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("goal-editor-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { SectionCard("选择目标") {
                SingleChoice(EvidenceOptions.goals,kind) { kind=it;threshold="" }
                SingleChoice(EvidenceOptions.periods,period) { period=it;threshold="" }
                DateControl(date,{date=it});Text("从所选日期所在周/月开始，每周期独立追踪。")
                OutlinedTextField(title,{if(it.length<=120)title=it},enabled=!busy,label={Text(if(kind=="text")"文字改善目标（必填）"else "目标名称（可选）")},modifier=Modifier.fillMaxWidth().testTag("goal-title"),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}))
                if(kind=="mistake_count") { Text("选择错误标签（含历史隐藏标签）")
                    SingleChoice(catalog.options("mistake",state.tags.filter { it.kind=="mistake" }.map { it.id }),mistake){mistake=it} }
                if(kind!="text") {
                    val presets=when { kind.endsWith("ratio") -> listOf("50","80","90","100");kind=="review_days" && period=="month" -> listOf("10","15","20","30");kind=="review_days" -> listOf("3","5","7");else -> listOf("0","1","3","5") }
                    Text(if(kind.endsWith("count"))"每周期至多次数"else if(kind.endsWith("ratio"))"每周期至少比例（%）"else "每周期至少复盘天数")
                    SingleChoice(presets.map { Option(it,it) },threshold){threshold=it}
                    OutlinedTextField(threshold,{if(it.length<=8)threshold=it},enabled=!busy,label={Text("自定阈值")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("goal-threshold"))
                } else Text("仅作为复盘提醒，不自动量化或宣称已完成。")
            } }
        } }
    }
}
