package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.tradereview.app.ui.icons.ReceiptLong
import androidx.compose.material.icons.filled.Add
import com.tradereview.app.ui.icons.Assessment
import com.tradereview.app.ui.icons.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import com.tradereview.app.ui.icons.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.tradereview.app.domain.Money
import com.tradereview.app.domain.TradeFilter
import com.tradereview.app.domain.AnalyticsState
import java.time.LocalDate

private data class Page(val title: String, val icon: ImageVector)
private val pages = listOf(Page("首页", Icons.Default.Home), Page("交易记录", Icons.AutoMirrored.Filled.ReceiptLong),
    Page("勾选复盘", Icons.Default.Checklist), Page("统计分析", Icons.Default.Assessment), Page("交易日历", Icons.Default.CalendarMonth))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeReviewApp(model: TradeViewModel, backup: BackupViewModel, ocrModel: OcrViewModel? = null) {
    val applicationContext=LocalContext.current.applicationContext
    val ocr=ocrModel ?: androidx.lifecycle.viewmodel.compose.viewModel<OcrViewModel>(factory=object:androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T=OcrViewModel(applicationContext) as T
    })
    var ocrShown by rememberSaveable { mutableStateOf(false) }
    var preflightDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val state by model.state.collectAsStateWithLifecycle()
    val currency by model.currency.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val calculated by model.analytics.collectAsStateWithLifecycle()
    // Room/worker flows update independently. Never label an old CNY report as a newly selected USD ledger.
    val analytics=if(calculated.source?.matches(state.trades,state.reviews,currency,filter)==true)calculated else AnalyticsState()
    val journalBusy by model.busy.collectAsStateWithLifecycle()
    val backupBusy by backup.busy.collectAsStateWithLifecycle()
    val busy = journalBusy || backupBusy
    val message by model.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var management by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var reviewDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var reviewSection by rememberSaveable { mutableStateOf("plan") }
    val pageState = rememberSaveableStateHolder()
    BackHandler(enabled = selectedDay != null && !settings) { selectedDay = null }
    BackHandler(enabled = settings) { if (!backupBusy && !busy) { if(management != null) management = null else settings = false } }
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var evidenceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var reports by rememberSaveable { mutableStateOf<String?>(null) }
    var reportReturn by rememberSaveable { mutableStateOf("week") }
    var reportKeys by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var reportTitle by rememberSaveable { mutableStateOf("报告交易明细") }
    fun backReport() { if(reportKeys!=null)reportKeys=null else if(reports=="goals")reports=reportReturn else if(reports=="preflight" && reportReturn=="insights")reports="insights" else reports=null }
    BackHandler(enabled=reports!=null && !settings && editorId==null && evidenceKey==null) { backReport() }
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
    CompositionLocalProvider(LocalContext provides chineseContext, LocalConfiguration provides chineseConfiguration, LocalActivityResultRegistryOwner provides activityResultOwner, LocalCatalog provides Catalog(state.tags), LocalPnlColors provides paletteColors(state.settings.pnlPalette)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text(when(management) { "tags" -> "标签管理"; "symbols" -> "常用品种"; "risk" -> "风险规则与合约模板"; "goals" -> "纪律改善目标"; else -> if(settings) "设置" else if(reports!=null) { if(reportKeys!=null)reportTitle else when(reports){"week"->"每周复盘";"month"->"每月复盘";"insights"->"智能洞察";"preflight"->"盘前纪律检查";else->"纪律改善目标"} } else if(selectedDay != null) "每日详情" else pages[page].title }) }, navigationIcon = {
                if (settings || selectedDay != null || reports!=null) IconButton(enabled = !backupBusy && !busy, onClick = { if(settings) { if(management != null) management = null else settings = false } else if(reports!=null)backReport() else selectedDay = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回主页面") }
            }, actions = {
                if (!settings && reports==null && (page == 0 || page == 1)) IconButton(enabled = !state.loading && state.error == null, onClick = { editorId = 0 }) { Icon(Icons.Default.Add, "新增交易") }
                if (!settings && reports==null && page != 4) IconButton(onClick = { selectedDay = null; page = 4 }) { Icon(Icons.Default.CalendarMonth, "打开交易日历") }
                if (!settings && reports==null) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "设置与备份") }
            }) },
            bottomBar = { if (!settings && reports==null) NavigationBar {
                pages.forEachIndexed { index, item ->
                    NavigationBarItem(modifier=Modifier.testTag("main-page-$index"),selected = page == index, onClick = { selectedDay = null; page = index }, icon = { Icon(item.icon, item.title) }, label = { Text(if(index == 4) "日历" else item.title) })
                }
            } },
            snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            if (settings) {
                when(management) {
                    "tags" -> TagManagement(state, busy, model, Modifier.padding(padding))
                    "symbols" -> InstrumentManagement(state, busy, model, Modifier.padding(padding))
                    "risk" -> RiskSettingsPage(state, model, busy, Modifier.padding(padding))
                    "goals" -> ReviewGoalsPage(state,model,busy,Modifier.padding(padding))
                    else -> SettingsPage(backup, busy, Modifier.padding(padding), onTags = { management = "tags" }, onSymbols = { management = "symbols" }, theme = state.settings.theme, onTheme = model::selectTheme,
                        palette = state.settings.pnlPalette, onPalette = model::selectPalette, onRisk = { management = "risk" },imageLimit=state.evidenceSettings.maxInputMiB,onImageLimit=model::imageLimit,onGoals={management="goals"},onSmart={settings=false;reports="insights"})
                }
            } else if (state.loading || (state.error == null && analytics.loading)) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            } else if (state.error != null) {
                Text(requireNotNull(state.error), Modifier.padding(padding).padding(24.dp), color = MaterialTheme.colorScheme.error)
            } else {
                if(reports!=null) {
                    if(reportKeys!=null)LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("report-trades-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        val keys=reportKeys.orEmpty().toHashSet()
                        val rows=state.trades.filter { it.recordKey in keys }
                        item { Text("${rows.size} 笔真实交易；各笔标明币种，不混合金额。") }
                        if(rows.isEmpty())item { Text("此项暂无交易记录。") }
                        items(rows,key={it.recordKey}) { t -> TradeCard(t){editorId=t.id} }
                    } else pageState.SaveableStateProvider("reports:$reports") {
                        if(reports=="insights")SmartReviewPage(state,model,busy,onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},onPreflight={reportReturn="insights";reports="preflight";preflightDate=LocalDate.now().toEpochDay()},modifier=Modifier.padding(padding))
                        else if(reports=="preflight")PreflightPage(preflightDate,state,model,busy,{preflightDate=it},onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},modifier=Modifier.padding(padding))
                        else if(reports=="goals")ReviewGoalsPage(state,model,busy,Modifier.padding(padding))
                        else ReviewReportPage(requireNotNull(reports),state,currency,model::selectCurrency,{reports=it},
                            onGoals={reportReturn=requireNotNull(reports);reports="goals"},onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},modifier=Modifier.padding(padding))
                    }
                } else if (selectedDay != null) DailyDetailsPage(requireNotNull(selectedDay), state, analytics.calendar[requireNotNull(selectedDay)].orEmpty(),
                    onOpen = { editorId = it.id }, onJournal = { section -> pageState.removeState("page:2"); reviewDate = requireNotNull(selectedDay); reviewSection = section; selectedDay = null; page = 2 }, modifier = Modifier.padding(padding))
                else pageState.SaveableStateProvider("page:$page") { when (page) {
                    0 -> HomePage(state, analytics, currency, model::selectCurrency, Modifier.padding(padding),
                        onAdd = { editorId = 0 }, onReview = { pageState.removeState("page:2"); reviewDate = LocalDate.now().toEpochDay(); reviewSection = "plan"; page = 2 }, onOpen = { editorId = it.id }, onDemo = model::addDemo, busy = busy, onCalendar = { page = 4 },onSmart={reports="insights"},onOcr={ocrShown=true})
                    1 -> TradesPage(state, analytics.filtered, currency, filter, model, busy, Modifier.padding(padding), { editorId = 0 }, { editorId = it.id })
                    2 -> DailyReviewPage(state.reviews, busy, model::savePlan, model::saveReview, Modifier.padding(padding), reviewDate, reviewSection,onPreflight={date->preflightDate=date;reportReturn="plan";reports="preflight"})
                    3 -> StatisticsPage(state, analytics, currency, filter, model, busy, onDay = { selectedDay = it }, onDrill = { model.updateFilter(it); page = 1 }, modifier = Modifier.padding(padding),onReports={reports=it;reportReturn="week"})
                    4 -> CalendarPage(state, analytics.calendar, currency, model::selectCurrency, { selectedDay = it }, Modifier.padding(padding))
                } }
                if (editorId != null) {
                    val original = state.trades.firstOrNull { it.id == editorId }
                    TradeEditor(original, state, busy, saveError = message?.takeIf { it.startsWith("保存失败") }, onDismiss = { editorId = null },
                        onDraft = model::persistDraft, onSave = { draft, approved, done -> model.saveEntry(draft, done, approved) }, onDelete = { model.deleteTrade(it) { editorId = null } }, notify = model::notify, onTemplate = model::saveTemplate,
                        evidenceStore=model.evidenceStore,onEvidence={t->editorId=null;evidenceKey=t.recordKey},onOcr={ocrShown=true})
                }
                evidenceKey?.let { key -> EvidencePage(key,state,model,busy,onOcr={a->ocrShown=true;ocr.read(android.net.Uri.EMPTY,state.evidenceSettings,a,model.evidenceStore)}) { evidenceKey=null;editorId=state.trades.firstOrNull { it.recordKey==key }?.id } }
            }
            if(ocrShown)OcrPage(ocr,state,model){ocrShown=false}
        }
    }
}

@Composable
private fun HomePage(state: JournalState, analytics: AnalyticsState, currency: String, onCurrency: (String) -> Unit, modifier: Modifier,
    onAdd: () -> Unit, onReview: () -> Unit, onOpen: (Trade) -> Unit, onDemo: () -> Unit, busy: Boolean, onCalendar: () -> Unit,onSmart:()->Unit,onOcr:()->Unit) {
    val today = LocalDate.now().toEpochDay()
    val stats = analytics.home
    val todayStats = analytics.today
    val todayReview = state.reviews.firstOrNull { it.date == today }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("让每一笔交易都有收获", style = MaterialTheme.typography.headlineSmall)
            Text(dayLabel(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) { Text(if(state.drafts.any { it.key == "new" }) "快捷记账 · 恢复草稿" else "快捷记账") }
            OutlinedButton(onClick = onCalendar, modifier = Modifier.fillMaxWidth()) { Text("交易日历与热力图") }
            OutlinedButton(onClick=onSmart,modifier=Modifier.fillMaxWidth().testTag("open-smart-insights")){Text("智能洞察与盘前提醒")}
            TextButton(onClick=onOcr,modifier=Modifier.fillMaxWidth().testTag("open-ocr")){Text("从截图识别交易")}
        }
        item { SectionCard("账户概览", "所有金额按选定币种分别统计。") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            Text("累计净盈亏 · $currency", style = MaterialTheme.typography.labelMedium)
            Text(Money.format(stats.netPnl, signed = true), color = pnlColor(stats.netPnl), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth()) {
                Metric("胜率", stats.winRate?.let(::percent) ?: "—", Modifier.weight(1f))
                Metric("交易笔数", "${stats.count}", Modifier.weight(1f))
                Metric("复盘完成", stats.reviewRate?.let(::percent) ?: "—", Modifier.weight(1f))
            }
            Text("今日 ${todayStats.count} 笔 · 净盈亏 ${Money.format(todayStats.netPnl, true)} $currency", color = pnlColor(todayStats.netPnl), style = MaterialTheme.typography.bodyMedium)
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
private fun TradesPage(state: JournalState, filtered: List<Trade>, currency: String, filter: TradeFilter, model: TradeViewModel,
    busy: Boolean, modifier: Modifier, onAdd: () -> Unit, onOpen: (Trade) -> Unit) {
    LazyColumn(modifier.fillMaxSize().testTag("trades-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FilterCard("交易筛选", state, currency, filter, filtered.size, model::selectCurrency, model::updateFilter, model::saveFilter, model::applyFilter, model::deleteFilter, busy) }
        if (filtered.isEmpty()) item { SectionCard("暂无符合条件的记录") {
            Text("可以切换市场或币种，也可以记录新的交易。")
            Button(onClick = onAdd) { Text("新增交易") }
        } }
        items(filtered, key = { it.id }) { trade -> TradeCard(trade) { onOpen(trade) } }
    }
}

@Composable
private fun DailyReviewPage(reviews: List<DailyReview>, busy: Boolean, onPlan: (DailyReview) -> Unit, onReview: (DailyReview) -> Unit, modifier: Modifier,
    initialDate: Long = LocalDate.now().toEpochDay(), initialSection: String = "plan",onPreflight:(Long)->Unit = {}) {
    var date by rememberSaveable(initialDate) { mutableLongStateOf(initialDate) }
    var section by rememberSaveable(initialSection, initialDate) { mutableStateOf(initialSection) }
    val existing = reviews.firstOrNull { it.date == date } ?: DailyReview(date = date)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("每日交易日志", "计划和复盘按日期保存在本地，可查看历史记录。") {
            DateControl(date, { date = it }, allowFuture = section == "plan")
            SingleChoice(listOf(Option("plan", "盘前计划"), Option("review", "盘后复盘")), section) { section = it }
            Text("盘前：${if (existing.planSaved) "已保存" else "待制定"} · 盘后：${if (existing.reviewSaved) "已保存" else "待复盘"}", style = MaterialTheme.typography.bodySmall)
        } }
        item {
            if (section == "plan") Column { OutlinedButton(onClick={onPreflight(date)},modifier=Modifier.fillMaxWidth()){Text("盘前纪律检查清单")};PlanForm(existing, busy, onPlan) }
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
