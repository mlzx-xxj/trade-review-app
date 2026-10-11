package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Option
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.data.Catalog
import com.tradereview.app.domain.Analytics
import com.tradereview.app.domain.Money
import java.time.LocalDate

private data class Page(val title: String, val icon: ImageVector)
private val pages = listOf(Page("首页", Icons.Default.Home), Page("交易记录", Icons.AutoMirrored.Filled.ReceiptLong),
    Page("勾选复盘", Icons.Default.Checklist), Page("统计分析", Icons.Default.Assessment))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeReviewApp(model: TradeViewModel, backup: BackupViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val currency by model.currency.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val backupBusy by backup.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var management by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = settings) { if (!backupBusy && !busy) { if(management != null) management = null else settings = false } }
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snack = remember { SnackbarHostState() }
    // Material components (including the calendar) use Chinese even on an English phone.
    val activityResultOwner = requireNotNull(LocalActivityResultRegistryOwner.current) { "ActivityResult owner is required for local file access" }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val chineseConfiguration = remember(configuration) {
        android.content.res.Configuration(configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) }
    }
    val chineseContext = remember(context, chineseConfiguration) { context.createConfigurationContext(chineseConfiguration) }
    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); model.clearMessage() }
    }
    CompositionLocalProvider(LocalContext provides chineseContext, LocalConfiguration provides chineseConfiguration, LocalActivityResultRegistryOwner provides activityResultOwner, LocalCatalog provides Catalog(state.tags)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text(when(management) { "tags" -> "标签管理"; "symbols" -> "常用品种"; else -> if(settings) "设置" else pages[page].title }) }, navigationIcon = {
                if (settings) IconButton(enabled = !backupBusy && !busy, onClick = { if(management != null) management = null else settings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回主页面") }
            }, actions = {
                if (!settings && (page == 0 || page == 1)) IconButton(enabled = !state.loading && state.error == null, onClick = { editorId = 0 }) { Icon(Icons.Default.Add, "新增交易") }
                if (!settings) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "设置与备份") }
            }) },
            bottomBar = { if (!settings) NavigationBar {
                pages.forEachIndexed { index, item ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) })
                }
            } },
            snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            if (settings) {
                when(management) {
                    "tags" -> TagManagement(state, busy, model, Modifier.padding(padding))
                    "symbols" -> InstrumentManagement(state, busy, model, Modifier.padding(padding))
                    else -> SettingsPage(backup, busy, Modifier.padding(padding), onTags = { management = "tags" }, onSymbols = { management = "symbols" }, theme = state.settings.theme, onTheme = model::selectTheme)
                }
            } else if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            } else if (state.error != null) {
                Text(requireNotNull(state.error), Modifier.padding(padding).padding(24.dp), color = MaterialTheme.colorScheme.error)
            } else {
                when (page) {
                    0 -> HomePage(state, currency, model::selectCurrency, Modifier.padding(padding),
                        onAdd = { editorId = 0 }, onReview = { page = 2 }, onOpen = { editorId = it.id }, onDemo = model::addDemo, busy = busy)
                    1 -> TradesPage(state.trades, currency, model::selectCurrency, Modifier.padding(padding), { editorId = 0 }, { editorId = it.id })
                    2 -> DailyReviewPage(state.reviews, busy, model::savePlan, model::saveReview, Modifier.padding(padding))
                    3 -> StatisticsPage(state.trades, currency, model::selectCurrency, Modifier.padding(padding))
                }
                if (editorId != null) {
                    val original = state.trades.firstOrNull { it.id == editorId }
                    TradeEditor(original, state, busy, saveError = message?.takeIf { it.startsWith("保存失败") }, onDismiss = { editorId = null },
                        onDraft = model::persistDraft, onSave = model::saveEntry, onDelete = { model.deleteTrade(it) { editorId = null } }, notify = model::notify)
                }
            }
        }
    }
}

@Composable
private fun HomePage(state: JournalState, currency: String, onCurrency: (String) -> Unit, modifier: Modifier,
    onAdd: () -> Unit, onReview: () -> Unit, onOpen: (Trade) -> Unit, onDemo: () -> Unit, busy: Boolean) {
    val today = LocalDate.now().toEpochDay()
    val stats = remember(state.trades, currency) { Analytics.calculate(state.trades, currency) }
    val todayStats = Analytics.calculate(state.trades.filter { it.date == today }, currency)
    val todayReview = state.reviews.firstOrNull { it.date == today }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("让每一笔交易都有收获", style = MaterialTheme.typography.headlineSmall)
            Text(dayLabel(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) { Text(if(state.drafts.any { it.key == "new" }) "快捷记账 · 恢复草稿" else "快捷记账") }
        }
        item { SectionCard("账户概览", "所有金额按选定币种分别统计。") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            Metric("累计净盈亏 · $currency", Money.format(stats.netPnl, signed = true))
            Row(Modifier.fillMaxWidth()) {
                Metric("胜率", percent(stats.winRate), Modifier.weight(1f))
                Metric("交易笔数", "${stats.count}", Modifier.weight(1f))
                Metric("复盘完成", percent(stats.reviewRate), Modifier.weight(1f))
            }
            Text("今日 ${todayStats.count} 笔 · 净盈亏 ${Money.format(todayStats.netPnl, true)} $currency", style = MaterialTheme.typography.bodyMedium)
        } }
        item { SectionCard("今天的交易节奏") {
            Text("盘前计划：${if (todayReview?.planSaved == true) "已保存" else "待制定"}    盘后复盘：${if (todayReview?.reviewSaved == true) "已保存" else "待复盘"}")
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("制定计划 / 勾选复盘") }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("记录交易") }
        } }
        if (state.trades.isEmpty()) item { SectionCard("从第一笔开始", "数据只保存在这台手机上，不上传真实交易记录。") {
            Text("先记录一笔已平仓交易，或添加示例体验统计。")
            TextButton(enabled = !busy, onClick = onDemo) { Text("添加示例交易（人民币）") }
        } }
        else {
            item { Text("最近交易 · $currency", style = MaterialTheme.typography.titleMedium) }
            val recent = state.trades.filter { it.currency == currency }.take(3)
            if (recent.isEmpty()) item { Text("这个币种暂无交易。") }
            items(recent, key = { it.id }) { TradeCard(it) { onOpen(it) } }
        }
        item { Text("离线本地存储 · 无需登录 · 卸载会删除数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TradesPage(trades: List<Trade>, currency: String, onCurrency: (String) -> Unit, modifier: Modifier, onAdd: () -> Unit, onOpen: (Trade) -> Unit) {
    var market by rememberSaveable { mutableStateOf("all") }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    val filtered = trades.filter { it.currency == currency && (market == "all" || it.market == market) && (!onlyPending || !it.reviewed) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("交易筛选") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            SingleChoice(listOf(Option("all", "全部市场")) + Presets.markets, market) { market = it }
            FilterChip(selected = onlyPending, onClick = { onlyPending = !onlyPending }, label = { Text("只看待复盘") })
            Text("${filtered.size} 笔 · 点击卡片编辑、复盘或删除", style = MaterialTheme.typography.bodySmall)
        } }
        if (filtered.isEmpty()) item { SectionCard("暂无符合条件的记录") {
            Text("可以切换市场或币种，也可以记录新的交易。")
            Button(onClick = onAdd) { Text("新增交易") }
        } }
        items(filtered, key = { it.id }) { trade -> TradeCard(trade) { onOpen(trade) } }
    }
}

@Composable
private fun DailyReviewPage(reviews: List<DailyReview>, busy: Boolean, onPlan: (DailyReview) -> Unit, onReview: (DailyReview) -> Unit, modifier: Modifier) {
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var section by rememberSaveable { mutableStateOf("plan") }
    val existing = reviews.firstOrNull { it.date == date } ?: DailyReview(date = date)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("每日交易日志", "计划和复盘按日期保存在本地，可查看历史记录。") {
            DateControl(date, { date = it }, allowFuture = section == "plan")
            SingleChoice(listOf(Option("plan", "盘前计划"), Option("review", "盘后复盘")), section) { section = it }
            Text("盘前：${if (existing.planSaved) "已保存" else "待制定"} · 盘后：${if (existing.reviewSaved) "已保存" else "待复盘"}", style = MaterialTheme.typography.bodySmall)
        } }
        item {
            if (section == "plan") PlanForm(existing, busy, onPlan)
            else PostReviewForm(existing, busy, onReview)
        }
    }
}

@Composable
private fun PlanForm(saved: DailyReview, busy: Boolean, onSave: (DailyReview) -> Unit) {
    val catalog = LocalCatalog.current
    var outlook by rememberSaveable(saved.date, saved.outlook) { mutableStateOf(saved.outlook) }
    var strategies by rememberSaveable(saved.date, saved.strategies) { mutableStateOf(saved.strategies) }
    var preparations by rememberSaveable(saved.date, saved.preparations) { mutableStateOf(saved.preparations) }
    var maxPosition by rememberSaveable(saved.date, saved.maxPosition) { mutableFloatStateOf(saved.maxPosition.toFloat()) }
    SectionCard("盘前计划", "先设定规则，再等待符合条件的交易。") {
        Text("市场判断")
        SingleChoice(Presets.outlooks, outlook) { outlook = it }
        Text("今天允许的策略 · 多选")
        ChoiceChips(catalog.options("strategy", strategies), strategies) { strategies = toggleTag(strategies, it) }
        Text("最大单笔仓位：${maxPosition.toInt()}%")
        Slider(maxPosition, { maxPosition = it }, valueRange = 1f..100f, steps = 98)
        Text("准备清单")
        catalog.options("preparation", preparations).forEach { option ->
            Row {
                Checkbox(option.id in preparations, { preparations = toggleTag(preparations, option.id) })
                Text(option.label, Modifier.padding(top = 12.dp))
            }
        }
        Button(enabled = !busy, onClick = { onSave(saved.copy(outlook = outlook, strategies = strategies, preparations = preparations, maxPosition = maxPosition.toInt())) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在保存…" else "保存盘前计划")
        }
    }
}

@Composable
private fun PostReviewForm(saved: DailyReview, busy: Boolean, onSave: (DailyReview) -> Unit) {
    val catalog = LocalCatalog.current
    var emotion by rememberSaveable(saved.date, saved.emotion) { mutableStateOf(saved.emotion) }
    var discipline by rememberSaveable(saved.date, saved.discipline) { mutableStateOf(saved.discipline) }
    var mistakes by rememberSaveable(saved.date, saved.mistakes) { mutableStateOf(saved.mistakes) }
    var lessons by rememberSaveable(saved.date, saved.lessons) { mutableStateOf(saved.lessons) }
    var rating by rememberSaveable(saved.date, saved.rating) { mutableFloatStateOf(saved.rating.toFloat()) }
    val future = saved.date > LocalDate.now().toEpochDay()
    SectionCard("盘后复盘", "评分关注执行质量，不只看盈亏。") {
        if (future) Text("未来日期只能制定计划，请选择今天或历史日期复盘。", color = MaterialTheme.colorScheme.error)
        Text("主要情绪")
        SingleChoice(catalog.options("emotion", listOf(emotion)), emotion) { emotion = it }
        Text("今天执行的纪律")
        ChoiceChips(catalog.options("discipline", discipline), discipline) { discipline = toggleTag(discipline, it) }
        Text("出现的错误 · 没有可留空")
        ChoiceChips(catalog.options("mistake", mistakes), mistakes) { mistakes = toggleTag(mistakes, it) }
        Text("明天的改进方向")
        ChoiceChips(catalog.options("lesson", lessons), lessons) { lessons = toggleTag(lessons, it) }
        Text("执行评分：${rating.toInt()} / 5")
        Slider(rating, { rating = it }, valueRange = 1f..5f, steps = 3)
        Button(enabled = !busy && !future, onClick = { onSave(saved.copy(emotion = emotion, discipline = discipline, mistakes = mistakes, lessons = lessons, rating = rating.toInt())) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在保存…" else "保存盘后复盘")
        }
        Text("单笔交易的复盘与错误请在“交易记录”中补充，供统计分析使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatisticsPage(trades: List<Trade>, currency: String, onCurrency: (String) -> Unit, modifier: Modifier) {
    val catalog = LocalCatalog.current
    var period by rememberSaveable { mutableStateOf("all") }
    val today = LocalDate.now().toEpochDay()
    val since = when (period) { "7" -> today - 6; "30" -> today - 29; else -> null }
    val stats = remember(trades, currency, since) { Analytics.calculate(trades, currency, since) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("分析范围") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            SingleChoice(listOf(Option("all", "全部"), Option("7", "近7天"), Option("30", "近30天")), period) { period = it }
        } }
        item { SectionCard("交易表现", "胜率 = 盈利笔数 / 全部笔数，包含持平交易。") {
            Row(Modifier.fillMaxWidth()) {
                Metric("胜率", percent(stats.winRate), Modifier.weight(1f))
                Metric("盈利 / 亏损", "${stats.wins} / ${stats.losses}", Modifier.weight(1f))
            }
            Metric("净盈亏 · $currency", Money.format(stats.netPnl, true))
            Text("持平 ${stats.count - stats.wins - stats.losses} 笔 · 已复盘 ${stats.reviewed} / ${stats.count}", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard("盈亏曲线", "显示所选时间范围内的累计净盈亏。") { PnlChart(stats.curve, currency) } }
        item { SectionCard("常见错误分布", "来自单笔交易的错误标签；一笔可出现多个错误。") {
            if (stats.mistakes.isEmpty()) Text("暂无错误标签。复盘时可在交易详情中勾选。")
            val maxCount = stats.mistakes.maxOfOrNull { it.second } ?: 1
            stats.mistakes.forEach { (error, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(catalog.label("mistake", error))
                    Text("$count 笔")
                }
                LinearProgressIndicator(progress = { count.toFloat() / maxCount }, modifier = Modifier.fillMaxWidth())
            }
        } }
        item { SectionCard("策略表现", "按净盈亏排序，每笔只归属一个策略。") {
            if (stats.strategies.isEmpty()) Text("记录交易并选择策略后即可查看。")
            stats.strategies.forEach { result ->
                Text(catalog.label("strategy", result.strategy), style = MaterialTheme.typography.titleSmall)
                Text("${result.count} 笔 · 胜率 ${percent(result.wins * 100.0 / result.count)} · 净盈亏 ${Money.format(result.netPnl, true)} $currency", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        } }
    }
}
