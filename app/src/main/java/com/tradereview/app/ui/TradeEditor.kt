package com.tradereview.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import com.tradereview.app.data.*
import com.tradereview.app.domain.FastEntry
import com.tradereview.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tradereview.app.data.Option
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateControl(date: Long, onDate: (Long) -> Unit, allowFuture: Boolean = false) {
    var show by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    OutlinedButton(enabled = LocalChoicesEnabled.current, onClick = { show = true }) { Text("日期：${dayLabel(date)}") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = LocalChoicesEnabled.current, onClick = { onDate(today) }) { Text("今天") }
        TextButton(enabled = LocalChoicesEnabled.current, onClick = { onDate(today - 1) }) { Text("昨天") }
        if (allowFuture) TextButton(enabled = LocalChoicesEnabled.current, onClick = { onDate(today + 1) }) { Text("明天") }
    }
    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowFuture || utcTimeMillis / 86_400_000L <= today
            },
        )
        DatePickerDialog(onDismissRequest = { show = false }, confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onDate(it / 86_400_000L) }
                show = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } }) {
            DatePicker(state, title = { Text("选择日期", Modifier.padding(16.dp)) })
        }
    }
}

private data class RiskPreview(val trade: Trade, val rules: List<RiskRules>, val history: List<Trade>, val event: RiskEvent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeEditor(original: Trade?, state: JournalState, busy: Boolean, saveError: String?, onDismiss: () -> Unit,
    onDraft: (EntryDraft) -> Unit, onSave: (EntryDraft, String?, () -> Unit) -> Unit, onDelete: (Trade) -> Unit,
    notify: (String) -> Unit, onTemplate: (ContractTemplate) -> Unit = {}) {
    val draftKey = original?.recordKey ?: "new"
    val restored = state.drafts.firstOrNull { it.key == draftKey }
    val initial = remember(draftKey) {
        restored ?: original?.let(EntryDraft::from) ?: run {
            val market = state.settings.lastMarket
            EntryDraft(market = market, symbol = orderedInstruments(state.instruments, market).firstOrNull()?.name ?: Presets.symbols(market).first(),
                currency = when(market) { "crypto" -> "USDT"; "forex" -> "USD"; else -> "CNY" },
                strategy = Catalog(state.tags).options("strategy").firstOrNull()?.id ?: "trend")
        }
    }
    val saver = remember { Saver<EntryDraft, String>(save = { Gson().toJson(it) }, restore = { Gson().fromJson(it, EntryDraft::class.java) }) }
    var draft by rememberSaveable(draftKey, stateSaver = saver) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf(if (restored != null) "已恢复未保存草稿" else "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingRisk by remember { mutableStateOf<Triple<EntryDraft, Boolean, RiskEvent>?>(null) }
    var chooseSymbol by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val catalog = LocalCatalog.current
    val focus = LocalFocusManager.current
    LaunchedEffect(busy) { if(busy) focus.clearFocus() }
    LaunchedEffect(draft) { onDraft(draft); if(error != null) error = FastEntry.error(draft) }
    val current = runCatching { FastEntry.trade(draft) }.getOrNull()
    val riskPreview by produceState<RiskPreview?>(null, current, state.riskRules, state.trades) {
        value = withContext(Dispatchers.Default) { current?.let { RiskPreview(it, state.riskRules, state.trades, RiskEngine.check(it, state.riskRules.firstOrNull { r -> r.currency == it.currency }, state.trades)) } }
    }
    fun leave() { onDraft(draft); onDismiss() }
    fun commit(saving: EntryDraft, keepOpen: Boolean, approved: String? = null) {
        onSave(saving, approved) {
            if (keepOpen) {
                draft = EntryDraft.copyReusable(FastEntry.trade(saving)).copy(outcome = "gain")
                feedback = "交易已保存，可以继续记录下一笔"; error = null
            } else onDismiss()
        }
    }
    fun save(keepOpen: Boolean) {
        error = FastEntry.error(draft)
        if(error != null) return
        val candidate = FastEntry.trade(draft)
        val check = RiskEngine.check(candidate, state.riskRules.firstOrNull { it.currency == draft.currency }, state.trades)
        if(RiskEngine.financialFactsChanged(original, candidate) && check.violations.isNotEmpty()) pendingRisk = Triple(draft, keepOpen, check)
        else commit(draft, keepOpen)
    }
    Dialog(onDismissRequest = { if (!busy) leave() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        BackHandler(busy) { }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(modifier = Modifier.imePadding(), topBar = {
                TopAppBar(title = { Text(if (original == null) "快速记录交易" else "编辑交易与复盘") },
                    navigationIcon = { IconButton(enabled = !busy, onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = { if (original != null) IconButton(enabled = !busy, onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除交易") } })
            }, bottomBar = {
                Surface(shadowElevation = 4.dp) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("entry-error")) }
                        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { save(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if(busy) "正在保存…" else "保存交易") }
                            if (original == null) OutlinedButton(enabled = !busy, onClick = { save(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("保存并继续") }
                        }
                    }
                }
            }) { padding ->
                CompositionLocalProvider(LocalChoicesEnabled provides !busy) {
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已平仓交易 · 手动默认，自动模式需核实成本 · 未保存内容自动留为本地草稿", style = MaterialTheme.typography.bodySmall)
                    if (original == null && state.trades.isNotEmpty()) OutlinedButton(enabled = !busy, onClick = {
                        draft = EntryDraft.copyReusable(state.trades.maxBy { it.id }); feedback = "已复制上一笔，盈亏及复盘状态已清空"; notify("已复制上一笔交易")
                    }, modifier = Modifier.fillMaxWidth()) { Text("复制上一笔交易") }
                    SectionCard("快速录入") {
                        DateControl(draft.date, { draft = draft.copy(date = it) })
                        Text("市场")
                        val markets = Presets.markets.sortedBy { if(it.id == state.settings.lastMarket) 0 else 1 }
                        SingleChoice(markets, draft.market) { market -> draft = draft.copy(market = market,
                            symbol = orderedInstruments(state.instruments, market).firstOrNull()?.name ?: Presets.symbols(market).first(),
                            currency = when(market) { "crypto" -> "USDT"; "forex" -> "USD"; else -> "CNY" }) }
                        Text("常用品种 · 最近使用优先")
                        val instruments = orderedInstruments(state.instruments, draft.market)
                        val common = (instruments.filter { it.lastUsed > 0 || it.favorite } + instruments).distinctBy { it.id }.take(6)
                        val visible = common.map { Option(it.name, it.name) }.let { if (it.none { o -> o.id == draft.symbol }) listOf(Option(draft.symbol, draft.symbol)) + it else it }
                        SingleChoice(visible, draft.symbol) { draft = draft.copy(symbol = it) }
                        TextButton(enabled = !busy, onClick = { chooseSymbol = true }) { Text("搜索 / 更多品种") }
                        SingleChoice(Presets.directions, draft.direction) { draft = draft.copy(direction = it) }
                        if(draft.professional?.pnlMode != "auto") {
                        Text("盈亏结果")
                        SingleChoice(listOf(Option("gain", "盈利"), Option("loss", "亏损"), Option("flat", "持平")), draft.outcome) {
                            draft = draft.copy(outcome = it, amount = if(it == "flat") "" else draft.amount)
                        }
                        if (draft.outcome != "flat") OutlinedTextField(enabled = !busy, value = draft.amount, onValueChange = { if(it.length <= 32) draft = draft.copy(amount = it) },
                            label = { Text("净盈亏金额（${draft.currency}）") }, singleLine = true,
                            supportingText = { Text("输入大于 0 的金额，持平请选择“持平”。") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("entry-amount"))
                        else Text("已明确选择持平：净盈亏 0.00 ${draft.currency}")
                        } else Text("使用专业区的自动净盈亏；原手工金额保留，未静默覆盖。")
                        Text("交易策略")
                        SingleChoice(catalog.options("strategy", listOf(draft.strategy)), draft.strategy) { draft = draft.copy(strategy = it) }
                    }
                    OutlinedButton(enabled = !busy, onClick = { draft = draft.copy(details = !draft.details) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (draft.details) "收起详细选项" else "展开详细选项（仓位 / 复盘）")
                    }
                    if (draft.details) SectionCard("详细选项") {
                        Text("结算币种"); SingleChoice(Presets.currencies, draft.currency) { draft = draft.copy(currency = it) }
                        Text("本笔仓位：${draft.positionPercent}%")
                        Slider(enabled = !busy, value = draft.positionPercent.toFloat(), onValueChange = { draft = draft.copy(positionPercent = it.toInt()) }, valueRange = 1f..100f, steps = 98)
                        Text("交易情绪"); SingleChoice(catalog.options("emotion", listOf(draft.emotion)), draft.emotion) { draft = draft.copy(emotion = it) }
                        Text("常见错误 · 可多选"); ChoiceChips(catalog.options("mistake", draft.mistakes), draft.mistakes) { draft = draft.copy(mistakes = toggleTag(draft.mistakes, it)) }
                        Text("执行纪律 · 可多选"); ChoiceChips(catalog.options("discipline", draft.discipline), draft.discipline) { draft = draft.copy(discipline = toggleTag(draft.discipline, it)) }
                        Row { Checkbox(enabled = !busy, checked = draft.reviewed, onCheckedChange = { draft = draft.copy(reviewed = it) }); Text("本笔已完成复盘", Modifier.padding(top = 12.dp)) }
                        OutlinedTextField(enabled = !busy, value = draft.note, onValueChange = { if(it.length <= 300) draft = draft.copy(note = it) }, label = { Text("简短补充备注（可选）") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(enabled = !busy, onClick = { draft = draft.copy(professional = (draft.professional ?: ProfessionalDraft()).let { it.copy(expanded = !it.expanded) }) }, modifier = Modifier.fillMaxWidth().testTag("expand-professional")) {
                        Text(if(draft.professional?.expanded == true) "收起专业交易信息" else "展开专业交易信息（可选）")
                    }
                    if(draft.professional?.expanded == true) ProfessionalFields(requireNotNull(draft.professional), draft.market, draft.symbol, draft.direction, draft.currency,
                        state.contractTemplates, busy, { draft = draft.copy(professional = it) }, onTemplate)
                    val r = current?.let(ProfessionalMath::r)
                    Text("本笔 R：${ProfessionalMath.formatR(r)}", modifier = Modifier.testTag("entry-r"), color = r?.let { pnlColor(it) } ?: MaterialTheme.colorScheme.onSurface)
                    current?.let { t -> riskPreview?.takeIf { it.trade == t && it.rules == state.riskRules && it.history == state.trades }?.event?.let { check ->
                        if(check.violations.isNotEmpty()) Text(check.violations.joinToString("；") { RiskEngine.labels.getValue(it) }, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("entry-risk-warning"))
                        check.notChecked.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    } }
                    original?.riskRecord?.takeIf { it.events.isNotEmpty() }?.let { record -> Text("历史纪律检查 ${record.events.size} 次；曾触发：${record.violations.joinToString("、") { RiskEngine.labels.getValue(it) }.ifEmpty { "无违规" }}。修改规则不删除历史检查。", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                }
                }
            }
        }
        pendingRisk?.let { pending -> AlertDialog(onDismissRequest = { if(!busy) pendingRisk = null }, title = { Text("风险纪律提醒，仍保存真实记录？") },
            text = { Column { pending.third.violations.forEach { Text(RiskEngine.labels.getValue(it), color = MaterialTheme.colorScheme.error) }
                Text("净盈亏 ${pending.third.netPnl} ${pending.third.currency} · 初始风险 ${pending.third.initialRisk ?: "未提供"}\n本日净盈亏 ${pending.third.dailyNet} · 连亏 ${pending.third.lossStreak} 笔")
                Text("这是自定参考纪律，不是交易指令。确认后如实保存金额与违规快照，不修改成交事实。")
            } }, confirmButton = { TextButton(enabled = !busy, onClick = { pendingRisk = null; commit(pending.first, pending.second, RiskEngine.fingerprint(pending.third)) }) { Text("仍保存真实记录") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingRisk = null }) { Text("返回核对") } }) }
        if (confirmDelete && original != null) AlertDialog(onDismissRequest = { if(!busy) confirmDelete = false }, title = { Text("删除这笔交易？") },
            text = { Text("${original.symbol} · ${dayLabel(original.date)}\n删除后无法恢复。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(original) }) { Text("删除") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") } })
        if (chooseSymbol) InstrumentPicker(state.instruments, draft.market, onDismiss = { chooseSymbol = false }) {
            draft = draft.copy(symbol = it.name); chooseSymbol = false
        }
    }
}

@Composable
fun InstrumentPicker(values: List<Instrument>, market: String, onDismiss: () -> Unit, onSelect: (Instrument) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择品种") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("搜索名称或代码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val matched = orderedInstruments(values, market, query)
                if (matched.isEmpty()) item { Text("暂无结果，可到设置添加自定义品种") }
                items(matched, key = { it.id }) { item -> TextButton(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if(item.favorite) "★ " else ""}${item.name} · ${item.code}${if(item.lastUsed > 0) " · 最近" else ""}")
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
