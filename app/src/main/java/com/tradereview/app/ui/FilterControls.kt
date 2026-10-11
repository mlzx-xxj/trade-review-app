package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.time.LocalDate
import java.time.ZoneOffset

val FilterPeriods = listOf(Option("all", "全部日期"), Option("week", "本周"), Option("month", "本月"), Option("30", "近30天"), Option("custom", "自定义日期"))
val OutcomeOptions = listOf(Option("gain", "盈利"), Option("loss", "亏损"), Option("flat", "持平"))

fun filterDescription(filter: TradeFilter, catalog: Catalog): String {
    val labels = mutableListOf<String>()
    if (filter.period != "all") labels += if (filter.period == "custom") "${LocalDate.ofEpochDay(filter.startDay)} 至 ${LocalDate.ofEpochDay(filter.endDay)}" else Presets.label(FilterPeriods, filter.period)
    fun choices(prefix: String, values: List<String>, label: (String) -> String) { if (values.isNotEmpty()) labels += "$prefix：${values.joinToString("、", transform = label)}" }
    choices("市场", filter.markets) { Presets.label(Presets.markets, it) }; choices("品种", filter.symbols) { it }
    choices("方向", filter.directions) { Presets.label(Presets.directions, it) }; choices("结果", filter.outcomes) { Presets.label(OutcomeOptions, it) }
    choices("策略", filter.strategies) { catalog.label("strategy", it) }; choices("错误", filter.mistakes) { catalog.label("mistake", it) }
    choices("纪律", filter.discipline) { catalog.label("discipline", it) }; choices("情绪", filter.emotions) { catalog.label("emotion", it) }
    if (filter.review != "all") labels += if (filter.review == "reviewed") "已复盘" else "待复盘"
    if (filter.errorPresence != "all") labels += if (filter.errorPresence == "with") "有错误标签" else "无错误标签"
    if (listOf(filter.strategies, filter.mistakes, filter.discipline, filter.emotions).any { it.isNotEmpty() }) labels += if (filter.tagMatch == "all") "标签匹配全部" else "标签匹配任意"
    return if (labels.isEmpty()) "全部记录 · 尚未设置额外条件" else labels.joinToString(" · ")
}

@Composable
fun FilterCard(title: String, state: JournalState, currency: String, filter: TradeFilter, count: Int,
    onCurrency: (String) -> Unit, onFilter: (TradeFilter) -> Unit, onSave: (String) -> Unit,
    onApply: (SavedFilter) -> Unit, onDelete: (SavedFilter) -> Unit, busy: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    var save by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<SavedFilter?>(null) }
    val catalog = LocalCatalog.current
    SectionCard(title, "列表与统计共用这些条件；金额仅汇总所选币种。") {
        SingleChoice(Presets.currencies, currency, onCurrency)
        Text(filterDescription(filter, catalog), modifier = Modifier.testTag("active-filter-summary"), style = MaterialTheme.typography.bodySmall)
        Text("匹配 $count 笔 · $currency", modifier = Modifier.testTag("filtered-count"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open = true }, modifier = Modifier.testTag("open-filters")) { Text("组合筛选") }
            TextButton(onClick = { onFilter(TradeFilter()) }, enabled = filter.active, modifier = Modifier.testTag("clear-filters")) { Text("清除筛选") }
        }
        OutlinedButton(enabled = !busy, onClick = { name = ""; save = true }, modifier = Modifier.testTag("save-filter-preset")) { Text("保存常用筛选") }
        if (state.savedFilters.isNotEmpty()) {
            Text("常用方案 · 相对日期随当天更新", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.savedFilters.forEach { value ->
                    InputChip(selected = currency == value.currency && filter == value.criteria, onClick = { onApply(value) },
                        label = { Text(value.name) }, trailingIcon = { TextButton(enabled = !busy, onClick = { remove = value }, contentPadding = PaddingValues(0.dp)) { Text("删除") } },
                        modifier = Modifier.testTag("preset-${value.id}"))
                }
            }
        }
    }
    if (open) FilterDialog(state, filter, onDismiss = { open = false }, onApply = { onFilter(it); open = false })
    if (save) AlertDialog(onDismissRequest = { save = false }, title = { Text("保存筛选方案") }, text = {
        OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("方案名称") }, singleLine = true, modifier = Modifier.testTag("filter-preset-name"))
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { onSave(name); save = false }) { Text("保存方案") } },
        dismissButton = { TextButton(onClick = { save = false }) { Text("取消") } })
    remove?.let { value -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("删除筛选方案？") }, text = { Text("仅删除“${value.name}”方案，交易记录保持完整。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(value); remove = null }) { Text("删除方案") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(state: JournalState, current: TradeFilter, onDismiss: () -> Unit, onApply: (TradeFilter) -> Unit) {
    var draft by remember { mutableStateOf(current) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun toggle(values: List<String>, id: String) = toggleTag(values, id)
    fun options(kind: String, selected: List<String>): List<Option> {
        val used = state.trades.flatMap { t -> when (kind) { "strategy" -> listOf(t.strategy); "emotion" -> listOf(t.emotion); "mistake" -> t.mistakes; else -> t.discipline } }.toSet()
        return state.tags.filter { it.kind == kind && (!it.hidden || it.id in used || it.id in selected) }.sortedWith(compareBy<ManagedTag> { it.sortOrder }.thenBy { it.id })
            .map { Option(it.id, it.name + if (it.hidden) "（已隐藏）" else "") }
    }
    val symbols = remember(state.trades, state.instruments, draft.markets, query) {
        val catalog = state.instruments.filter { draft.markets.isEmpty() || it.market in draft.markets }
        val historical = state.trades.filter { draft.markets.isEmpty() || it.market in draft.markets }.map { it.symbol }
        (catalog.map { it.name } + historical).distinct().filter { name -> query.isBlank() || name.contains(query, true) || catalog.any { it.name == name && it.code.contains(query, true) } }
            .map { Option(it, it) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(modifier = Modifier.imePadding(), topBar = { TopAppBar(title = { Text("多维组合筛选") }, navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭筛选") }
            }, actions = { TextButton(onClick = { draft = TradeFilter(); error = null }) { Text("重置") } }) }, bottomBar = {
                Surface(shadowElevation = 4.dp) { Column(Modifier.navigationBarsPadding().padding(12.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { try { FilterCodec.validate(draft); onApply(draft) } catch (e: IllegalArgumentException) { error = e.message } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("apply-filters")) { Text("应用筛选") }
                } }
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("filter-options"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { SectionCard("日期范围") {
                        SingleChoice(FilterPeriods, draft.period) { value -> draft = if (value == "custom" && draft.period != "custom") draft.copy(period = value, startDay = LocalDate.now().minusDays(29).toEpochDay(), endDay = LocalDate.now().toEpochDay()) else draft.copy(period = value) }
                        if (draft.period == "custom") {
                            FilterDatePicker("开始日期", draft.startDay) { draft = draft.copy(startDay = it) }
                            FilterDatePicker("结束日期", draft.endDay) { draft = draft.copy(endDay = it) }
                        }
                    } }
                    item { SectionCard("市场 · 可多选") { ChoiceChips(Presets.markets, draft.markets) { draft = draft.copy(markets = toggle(draft.markets, it)) } } }
                    item { SectionCard("品种 · 可多选", "历史品种按原名称保留，可按名称或代码查找。") {
                        OutlinedTextField(query, { query = it }, label = { Text("筛选品种名称或代码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        ChoiceChips(symbols, draft.symbols) { draft = draft.copy(symbols = toggle(draft.symbols, it)) }
                    } }
                    item { SectionCard("方向与结果") {
                        ChoiceChips(Presets.directions, draft.directions) { draft = draft.copy(directions = toggle(draft.directions, it)) }
                        ChoiceChips(OutcomeOptions, draft.outcomes) { draft = draft.copy(outcomes = toggle(draft.outcomes, it)) }
                    } }
                    item { SectionCard("标签匹配方式", "每类标签内部使用任意或全部；不同条件之间必须同时满足。单笔只含一个策略和一种情绪，匹配全部且选择多个时不会匹配。") {
                        SingleChoice(listOf(Option("any", "匹配任意"), Option("all", "匹配全部")), draft.tagMatch) { draft = draft.copy(tagMatch = it) }
                    } }
                    item { SectionCard("交易策略 · 可多选") { ChoiceChips(options("strategy", draft.strategies), draft.strategies) { draft = draft.copy(strategies = toggle(draft.strategies, it)) } } }
                    item { SectionCard("常见错误 · 可多选") { ChoiceChips(options("mistake", draft.mistakes), draft.mistakes) { draft = draft.copy(mistakes = toggle(draft.mistakes, it)) } } }
                    item { SectionCard("执行纪律 · 可多选") { ChoiceChips(options("discipline", draft.discipline), draft.discipline) { draft = draft.copy(discipline = toggle(draft.discipline, it)) } } }
                    item { SectionCard("交易情绪 · 可多选") { ChoiceChips(options("emotion", draft.emotions), draft.emotions) { draft = draft.copy(emotions = toggle(draft.emotions, it)) } } }
                    item { SectionCard("复盘与错误状态") {
                        SingleChoice(listOf(Option("all", "全部复盘状态"), Option("reviewed", "已复盘"), Option("pending", "待复盘")), draft.review) { draft = draft.copy(review = it) }
                        SingleChoice(listOf(Option("all", "全部错误状态"), Option("with", "有错误标签"), Option("without", "无错误标签")), draft.errorPresence) { draft = draft.copy(errorPresence = it) }
                    } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDatePicker(label: String, day: Long, onDate: (Long) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label：${dayLabel(day)}") }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            picker.selectedDateMillis?.let { onDate(Math.floorDiv(it, 86_400_000L)) }; open = false
        }) { Text("确定") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } }) { DatePicker(picker, title = { Text(label, Modifier.padding(16.dp)) }) }
    }
}
