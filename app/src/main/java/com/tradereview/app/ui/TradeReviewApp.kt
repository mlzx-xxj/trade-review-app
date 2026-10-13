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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private data class Page(val title: String, val icon: ImageVector,val label:String=title)
private val pages = listOf(Page("首页", Icons.Default.Home), Page("交易记录", Icons.AutoMirrored.Filled.ReceiptLong,"交易"),
    Page("勾选复盘", Icons.Default.Checklist,"复盘"), Page("统计分析", Icons.Default.Assessment,"统计"), Page("交易日历", Icons.Default.CalendarMonth,"日历"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeReviewApp(model: TradeViewModel, backup: BackupViewModel, ocrModel: OcrViewModel? = null,
    experience:com.tradereview.app.device.DeviceExperienceStore?=null) {
    val applicationContext=LocalContext.current.applicationContext
    val ocr=ocrModel ?: androidx.lifecycle.viewmodel.compose.viewModel<OcrViewModel>(factory=object:androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T=OcrViewModel(applicationContext) as T
    })
    var ocrShown by rememberSaveable { mutableStateOf(false) }
    var preflightDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val state by model.state.collectAsStateWithLifecycle()
    val experienceState by (experience?.state ?: remember{ kotlinx.coroutines.flow.MutableStateFlow(com.tradereview.app.device.ExperienceState()) }).collectAsStateWithLifecycle()
    val experienceError by (experience?.error ?: remember{ kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()
    var helpShown by rememberSaveable{mutableStateOf(false)}
    val experienceScope=rememberCoroutineScope()
    fun finishGuide(){helpShown=false;experience?.let {store->experienceScope.launch{try{withContext(Dispatchers.IO){store.finishOnboarding()}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;model.notify("引导状态保存失败，请检查手机空间")}}}}
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
    var feedbackHeightPx by remember { mutableIntStateOf(0) }
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
    LaunchedEffect(experienceError){experienceError?.let{snack.showSnackbar(it)}}
    CompositionLocalProvider(LocalContext provides chineseContext, LocalConfiguration provides chineseConfiguration, LocalActivityResultRegistryOwner provides activityResultOwner, LocalCatalog provides Catalog(state.tags), LocalPnlColors provides paletteColors(state.settings.pnlPalette)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text(when(management) { "tags" -> "标签管理"; "symbols" -> "常用品种"; "risk" -> "风险规则与合约模板"; "goals" -> "纪律改善目标"; else -> if(settings) "设置" else if(reports!=null) { if(reportKeys!=null)reportTitle else when(reports){"week"->"每周复盘";"month"->"每月复盘";"insights"->"智能洞察";"preflight"->"盘前纪律检查";else->"纪律改善目标"} } else if(selectedDay != null) "每日详情" else pages[page].title }) }, navigationIcon = {
                if (settings || selectedDay != null || reports!=null) IconButton(enabled = !backupBusy && !busy, onClick = { if(settings) { if(management != null) management = null else settings = false } else if(reports!=null)backReport() else selectedDay = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回主页面") }
            }, actions = {
                if (!settings && reports==null && page == 1) IconButton(enabled = !state.loading && state.error == null, onClick = { editorId = 0 }) { Icon(Icons.Default.Add, "新增交易") }
                if (!settings && reports==null && page != 4) IconButton(onClick = { selectedDay = null; page = 4 }) { Icon(Icons.Default.CalendarMonth, "打开交易日历") }
                if (!settings && reports==null) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "设置与备份") }
            }) },
            bottomBar = { if (!settings && reports==null) NavigationBar {
                pages.forEachIndexed { index, item ->
                    NavigationBarItem(modifier=Modifier.testTag("main-page-$index"),selected = page == index, onClick = { selectedDay = null; page = index }, icon = { Icon(item.icon, item.title) }, label = { Text(item.label,maxLines=1) })
                }
            } },
            snackbarHost = { SnackbarHost(snack,Modifier.onSizeChanged { feedbackHeightPx=it.height }) },
        ) { padding ->
            // Floating feedback must not intercept the last form action on a small screen.
            val direction=LocalLayoutDirection.current
            val feedbackInset=with(LocalDensity.current){feedbackHeightPx.toDp()} + if(feedbackHeightPx>0)8.dp else 0.dp
            val pagePadding=PaddingValues(start=padding.calculateStartPadding(direction),top=padding.calculateTopPadding(),
                end=padding.calculateEndPadding(direction),bottom=padding.calculateBottomPadding()+feedbackInset)
            if (settings) {
                when(management) {
                    "tags" -> TagManagement(state, busy, model, Modifier.padding(pagePadding))
                    "symbols" -> InstrumentManagement(state, busy, model, Modifier.padding(pagePadding))
                    "risk" -> RiskSettingsPage(state, model, busy, Modifier.padding(pagePadding))
                    "goals" -> ReviewGoalsPage(state,model,busy,Modifier.padding(pagePadding))
                    else -> SettingsPage(backup, busy, Modifier.padding(pagePadding), onTags = { management = "tags" }, onSymbols = { management = "symbols" }, theme = state.settings.theme, onTheme = model::selectTheme,
                        palette = state.settings.pnlPalette, onPalette = model::selectPalette, onRisk = { management = "risk" },imageLimit=state.evidenceSettings.maxInputMiB,onImageLimit=model::imageLimit,onGoals={management="goals"},onSmart={settings=false;reports="insights"},onHelp={helpShown=true})
                }
            } else if (state.loading || (state.error == null && analytics.loading)) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            } else if (state.error != null) {
                Text(requireNotNull(state.error), Modifier.padding(pagePadding).padding(24.dp), color = MaterialTheme.colorScheme.error)
            } else {
                if(reports!=null) {
                    if(reportKeys!=null)LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("report-trades-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        val keys=reportKeys.orEmpty().toHashSet()
                        val rows=state.trades.filter { it.recordKey in keys }
                        item { Text("${rows.size} 笔真实交易；各笔标明币种，不混合金额。") }
                        if(rows.isEmpty())item { Text("此项暂无交易记录。") }
                        items(rows,key={it.recordKey}) { t -> TradeCard(t){editorId=t.id} }
                    } else pageState.SaveableStateProvider("reports:$reports") {
                        if(reports=="insights")SmartReviewPage(state,model,busy,onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},onPreflight={reportReturn="insights";reports="preflight";preflightDate=LocalDate.now().toEpochDay()},modifier=Modifier.padding(pagePadding))
                        else if(reports=="preflight")PreflightPage(preflightDate,state,model,busy,{preflightDate=it},onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},modifier=Modifier.padding(pagePadding))
                        else if(reports=="goals")ReviewGoalsPage(state,model,busy,Modifier.padding(pagePadding))
                        else ReviewReportPage(requireNotNull(reports),state,currency,model::selectCurrency,{reports=it},
                            onGoals={reportReturn=requireNotNull(reports);reports="goals"},onTrades={rows,title->reportKeys=rows.map { it.recordKey };reportTitle=title},modifier=Modifier.padding(pagePadding))
                    }
                } else if (selectedDay != null) DailyDetailsPage(requireNotNull(selectedDay), state, analytics.calendar[requireNotNull(selectedDay)].orEmpty(),
                    onOpen = { editorId = it.id }, onJournal = { section -> pageState.removeState("page:2"); reviewDate = requireNotNull(selectedDay); reviewSection = section; selectedDay = null; page = 2 }, modifier = Modifier.padding(pagePadding))
                else pageState.SaveableStateProvider("page:$page") { when (page) {
                    0 -> HomePage(state, analytics, currency, model::selectCurrency, Modifier.padding(pagePadding),
                        onAdd = { editorId = 0 }, onReview = { section->pageState.removeState("page:2"); reviewDate = LocalDate.now().toEpochDay(); reviewSection = section; page = 2 }, onOpen = { editorId = it.id }, onDemo = model::addDemo, busy = busy, onCalendar = { page = 4 },onSmart={reports="insights"},onOcr={ocrShown=true},experience=experienceState,onBackup={settings=true},onSnooze={backup.snoozeBackup(7)})
                    1 -> TradesPage(state, analytics.filtered, currency, filter, model, busy, Modifier.padding(pagePadding), { editorId = 0 }, { editorId = it.id })
                    2 -> DailyReviewPage(state.reviews, busy, model::savePlan, model::saveReview, Modifier.padding(pagePadding), reviewDate, reviewSection,onPreflight={date->preflightDate=date;reportReturn="plan";reports="preflight"})
                    3 -> StatisticsPage(state, analytics, currency, filter, model, busy, onDay = { selectedDay = it }, onDrill = { model.updateFilter(it); page = 1 }, modifier = Modifier.padding(pagePadding),onReports={reports=it;reportReturn="week"})
                    4 -> CalendarPage(state, analytics.calendar, currency, model::selectCurrency, { selectedDay = it }, Modifier.padding(pagePadding))
                } }
            }
            // Derived statistics invalidate independently of Room writes. Keep an open editor/viewer
            // mounted while they recalculate, so save callbacks and local input keep their state.
            if (!state.loading && state.error == null) {
                if (editorId != null) {
                    val original = state.trades.firstOrNull { it.id == editorId }
                    TradeEditor(original, state, busy, saveError = message?.takeIf { it.startsWith("保存失败") }, onDismiss = { editorId = null },
                        onDraft = model::persistDraft, onSave = { draft, approved, done -> model.saveEntry(draft, done, approved) }, onDelete = { model.deleteTrade(it) { editorId = null } }, notify = model::notify, onTemplate = model::saveTemplate,
                        evidenceStore=model.evidenceStore,onEvidence={t->editorId=null;evidenceKey=t.recordKey},onOcr={ocrShown=true})
                }
                evidenceKey?.let { key -> EvidencePage(key,state,model,busy,onOcr={a->ocrShown=true;ocr.read(android.net.Uri.EMPTY,state.evidenceSettings,a,model.evidenceStore)}) { evidenceKey=null;editorId=state.trades.firstOrNull { it.recordKey==key }?.id } }
            }
            if(ocrShown)OcrPage(ocr,state,model){ocrShown=false}
            if(helpShown || (experienceState.initialized && !experienceState.onboardingDone))FirstUsePage(!experienceState.onboardingDone,::finishGuide)
        }
    }
}

@Composable
private fun HomePage(state: JournalState, analytics: AnalyticsState, currency: String, onCurrency: (String) -> Unit, modifier: Modifier,
    onAdd: () -> Unit, onReview: (String) -> Unit, onOpen: (Trade) -> Unit, onDemo: () -> Unit, busy: Boolean, onCalendar: () -> Unit,onSmart:()->Unit,onOcr:()->Unit,
    experience:com.tradereview.app.device.ExperienceState,onBackup:()->Unit,onSnooze:()->Unit) {
    val today = LocalDate.now().toEpochDay()
    val stats = analytics.home
    val todayStats = analytics.today
    val todayReview = state.reviews.firstOrNull { it.date == today }
    LazyColumn(modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("记录交易，复盘执行", style = MaterialTheme.typography.headlineSmall,modifier=Modifier.testTag("home-title"))
            Text(dayLabel(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp).testTag("home-fast-entry").semantics{contentDescription="新增交易"}) { Text(if(state.drafts.any { it.key == "new" }) "快捷记账 · 恢复草稿" else "快捷记账") }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onOcr,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("open-ocr")){Text("从截图识别交易")}
                OutlinedButton(onClick=onSmart,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("open-smart-insights")){Text("智能洞察与盘前提醒")}
            }
        }
        item { SectionCard("今日表现", "按选定币种统计已录入的平仓记录。") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            Text("今日净盈亏 · $currency",style=MaterialTheme.typography.labelLarge)
            Text(if(todayStats.count==0)"暂无交易"else Money.format(todayStats.netPnl,true),color=if(todayStats.count==0)MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(todayStats.netPnl),style=MaterialTheme.typography.headlineMedium,modifier=Modifier.testTag("home-today-pnl"))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric("今日交易","${todayStats.count} 笔",Modifier.weight(1f))
                Metric("已复盘",if(todayStats.count==0)"—"else "${todayStats.reviewed} / ${todayStats.count}",Modifier.weight(1f))
            }
            val todayRows=state.trades.filter{it.date==today && it.currency==currency}
            Text("有风险检查记录 ${todayRows.count{it.riskRecord!=null}} 笔 · 含违规 ${todayRows.count{it.riskRecord?.violations?.isNotEmpty()==true}} 笔；无检查记录不视为遵守纪律。",style=MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard("今天的复盘", "计划、复盘和执行评分按日期保存，不区分币种。") {
            Text("盘前计划：${if (todayReview?.planSaved == true) "已保存" else "待制定"} · 盘后复盘：${if (todayReview?.reviewSaved == true) "已保存" else "待复盘"}")
            Text(if(todayReview?.reviewSaved==true)"执行评分 ${todayReview.rating} / 5 · 已勾选 ${todayReview.discipline.size} 项纪律"else "执行评分：未提供")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onReview("plan")},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("home-plan")){Text("盘前计划")}
                Button(onClick={onReview("review")},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("home-post-review")){Text("盘后复盘")}
            }
        } }
        if(experience.reminderDue(System.currentTimeMillis()))item {SectionCard("备份建议","距离本机最近已校验完整备份或首次使用已满7天。"){
            Text("卸载与换机前，请先保存并确认完整ZIP；它包含截图。")
            Button(onClick=onBackup,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("home-backup")){Text("打开完整备份")}
            TextButton(onClick=onSnooze,modifier=Modifier.heightIn(min=48.dp)){Text("7天后提醒")}
        }}
        item { SectionCard("累计表现", "所有金额按选定币种分别统计。") {
            Text("累计净盈亏 · $currency", style = MaterialTheme.typography.labelMedium)
            Text(if(stats.count==0)"暂无记录"else Money.format(stats.netPnl, signed = true), color = pnlColor(stats.netPnl), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric("胜率", stats.winRate?.let(::percent) ?: "—", Modifier.weight(1f))
                Metric("交易笔数", "${stats.count}", Modifier.weight(1f))
                Metric("复盘完成", stats.reviewRate?.let(::percent) ?: "—", Modifier.weight(1f))
            }
        } }
        if (state.trades.isEmpty()) item { SectionCard("从第一笔开始", "数据只保存在这台手机上，不上传真实交易记录。") {
            Text("先记录一笔已平仓交易。示例仅在你主动点击后添加，不代表真实交易。")
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
        Slider(maxPosition, { maxPosition = it }, valueRange = 1f..100f, steps = 98,modifier=Modifier.semantics{contentDescription="最大单笔仓位百分比";stateDescription="${maxPosition.toInt()}%"})
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
        Slider(rating, { rating = it }, valueRange = 1f..5f, steps = 3,modifier=Modifier.semantics{contentDescription="盘后执行评分";stateDescription="${rating.toInt()} / 5"})
        Button(enabled = !busy && !future, onClick = { onSave(saved.copy(emotion = emotion, discipline = discipline, mistakes = mistakes, lessons = lessons, rating = rating.toInt())) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在保存…" else "保存盘后复盘")
        }
        Text("单笔交易的复盘与错误请在“交易记录”中补充，供统计分析使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
