package com.tradereview.app.v200

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.ui.BackupViewModel
import com.tradereview.app.ui.LocalEvidenceImage
import com.tradereview.app.ui.LocalFullScreenDialog
import com.tradereview.app.ui.icons.*
import com.tradereview.app.evidence.AttachmentStore
import com.tradereview.app.data.Presets
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable fun V200App(model:V200ViewModel,backup:BackupViewModel,store:AttachmentStore){val screen by model.state.collectAsState();val ledger=screen.ledger;val page by model.page.collectAsState();val editor by model.editor.collectAsState();val pending by model.pending.collectAsState();val busy by model.busy.collectAsState();var route by rememberSaveable{mutableStateOf("")};var selected by rememberSaveable{mutableStateOf("")};var editing by rememberSaveable{mutableStateOf<String?>(null)};var resumeEntry by rememberSaveable{mutableStateOf(false)};var day by rememberSaveable{mutableStateOf<Long?>(null)};val snack=remember{SnackbarHostState()};var snackHeight by remember{mutableIntStateOf(0)};val density=LocalDensity.current;val holder=rememberSaveableStateHolder();val message by model.message.collectAsState();val labels=listOf("首页","交易","复盘","统计","账户")
    LaunchedEffect(message){message?.let{snack.showSnackbar(it);model.message.value=null}}
    fun back(){if(route=="rules" || route=="strategy"){route="risk"}else if(resumeEntry){route="";resumeEntry=false;model.openEditor()}else route=""}
    BackHandler(enabled=route.isNotEmpty() && !editor){back()}
    Scaffold(topBar={Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal=8.dp).heightIn(min=56.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        if(route.isNotEmpty())IconButton({back()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回主页面")}
        Text(when(route){"risk"->"风险与策略";"rules"->"合约参数";"backup"->"备份与导出";"position"->"持仓详情";"execution"->"成交详情";"archive"->"兼容历史";"strategy"->"策略关联历史";else->labels.getOrElse(page){"首页"}},Modifier.weight(1f).padding(vertical=14.dp),style=MaterialTheme.typography.titleLarge)
        FlowRow(Modifier.widthIn(max=250.dp),horizontalArrangement=Arrangement.End){
            if(route!="risk" && route!="rules")TextButton({route="risk"},modifier=Modifier.heightIn(min=48.dp).testTag("open-risk-strategy"),contentPadding=PaddingValues(horizontal=8.dp)){Text("风险与策略",style=MaterialTheme.typography.labelMedium)}
            if(route.isEmpty() && page==1)FilledTonalButton({editing=null;model.openEditor()},modifier=Modifier.heightIn(min=48.dp).testTag("new-execution"),contentPadding=PaddingValues(horizontal=8.dp),shape=MaterialTheme.shapes.small){Text("记一笔")}
            IconButton({route="backup"},modifier=Modifier.testTag("open-backup")){Icon(Icons.Default.Settings,"备份与导出")}
        }
    }},bottomBar={if(route.isEmpty())NavigationBar(containerColor=MaterialTheme.colorScheme.surface){labels.forEachIndexed{i,label->NavigationBarItem(selected=page==i,onClick={model.page(i)},icon={when(i){0->Icon(Icons.Default.Home,label);1->Icon(Icons.AutoMirrored.Filled.ReceiptLong,label);2->Icon(Icons.Default.Checklist,label);3->Icon(Icons.Default.Assessment,label);else->Icon(Icons.Default.Person,label)}},label={Text(label)},modifier=Modifier.testTag("v2-main-page-$i"))}}},snackbarHost={SnackbarHost(snack,Modifier.onSizeChanged{snackHeight=it.height})}){padding->Box(Modifier.fillMaxSize().padding(padding).padding(bottom=with(density){snackHeight.toDp()}+if(snackHeight>0)8.dp else 0.dp)){
        if(screen.error!=null)Text(screen.error!!,Modifier.padding(16.dp))else if(ledger==null)Column(Modifier.padding(16.dp)){LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在读取本机账本")}else holder.SaveableStateProvider(if(route.isEmpty())"page-$page"else if(route in listOf("position","execution","strategy"))"$route-$selected"else route){
            when(route){
                "risk"->V200RiskPage(model,ledger,{route="rules"},{selected=it;route="strategy"})
                "rules"->ContractRulesPage(model,ledger)
                "backup"->V200BackupPage(backup)
                "position"->PositionDetails(ledger,selected)
                "execution"->ledger.executions.firstOrNull{it.id==selected}?.let{e->ExecutionDetails(ledger,e,{editing=e.id;model.openEditor()},{model.deleteExecution(e)})}?:EmptyLedger("成交已不存在")
                "archive"->LegacyArchive(model,store)
                "strategy"->StrategyHistory(model,ledger,selected,{selected=it;route="execution"},{route="archive"})
                else->when(page){0->V200Home(screen,{selected=it;route="position"},{day=it},onTrades={model.page(1)},onReviews={model.page(2)},onExecution={selected=it;route="execution"});1->V200Trades(ledger,{selected=it;route="position"},{selected=it;route="execution"},{route="archive"},model.savedExecution.collectAsState().value);2->V200Reviews(model,ledger);3->V200Statistics(screen);else->V200Accounts(model,ledger)}
            }
        }
    }}
    // Keep the editor mounted while derived data refreshes, preserving its state and save callback.
    if(editor && ledger!=null)ExecutionEditor(model,ledger,ledger.executions.firstOrNull{it.id==editing},{resumeEntry=true;model.closeEditor();route="risk"})
    if(pending!=null){val change=pending!!;AlertDialog(onDismissRequest={model.cancelChange()},title={Text(if(change.deleting)"确认删除成交并重算？"else if(change.risk?.exceeded==true)"单笔计划风险超限"else if(change.orderWarning)"确认成交先后顺序？"else"确认成交影响并重算？")},text={Column{change.risk?.takeIf{it.exceeded}?.let{Text("风险 ${amount(it.amount)}，上限 ${amount(it.limit)}；可确认记录真实成交，不篡改事实。",color=MaterialTheme.colorScheme.error)};Text(change.impact)}},confirmButton={TextButton({model.confirmChange()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("confirm-execution-change"),colors=ButtonDefaults.textButtonColors(contentColor=if(change.deleting)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)){Text("确认真实记录并重算")}},dismissButton={TextButton({model.cancelChange()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("取消")}})}
    day?.let{ClosedDayDialog(screen,it,{day=null})}
}
@Composable private fun LegacyArchive(model:V200ViewModel,store:AttachmentStore){val trades by model.legacy.collectAsState();val images by model.attachments.collectAsState();val tags by model.legacyTags.collectAsState();var viewing by rememberSaveable{mutableStateOf<String?>(null)};val strategies=model.state.collectAsState().value.ledger?.strategies.orEmpty();LazyColumn(Modifier.fillMaxSize().testTag("v2-legacy-archive"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    item{Text("旧版单笔盈亏档案，只读保留原金额、币种、ID与截图；不推断账户，不生成持仓，不计入V2统计。",style=MaterialTheme.typography.bodySmall)}
    if(trades.isEmpty())item{EmptyLedger("没有旧版档案")};items(trades,key={it.recordKey}){t->LedgerPanel("${t.symbol} · ${Presets.label(Presets.markets,t.market)}"){Text("${LocalDate.ofEpochDay(t.date)} · 原币种 ${t.currency} · 原净盈亏 ${historicalMoney(t.currency,t.netPnl)}");Text("策略 ${strategies.firstOrNull{it.id==t.strategy}?.name?:tags.firstOrNull{it.kind=="strategy" && it.id==t.strategy}?.name?:t.strategy}");if(t.note.isNotBlank())Text(t.note);val own=images.filter{it.recordKey==t.recordKey};if(own.isNotEmpty())LazyRow{items(own,key={it.id}){a->LocalEvidenceImage(a,store,Modifier.size(88.dp).clickable{viewing=a.id})}};Text("稳定ID ${t.recordKey}",style=MaterialTheme.typography.labelSmall)}}
    }
    images.firstOrNull{it.id==viewing}?.let{a->LocalFullScreenDialog({viewing=null}){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){val own=images.filter{it.recordKey==a.recordKey};val index=own.indexOfFirst{it.id==a.id};Row{TextButton({viewing=null}){Text("返回档案")};TextButton({viewing=own[(index-1+own.size)%own.size].id}){Text("上一张")};TextButton({viewing=own[(index+1)%own.size].id}){Text("下一张")}};LocalEvidenceImage(a,store,Modifier.weight(1f).fillMaxWidth(),1600);Text(a.caption,Modifier.padding(12.dp))}}}
}
