package com.tradereview.app.v200

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** A strategy's old records remain associated, without fabricating CNY executions. */
@Composable fun StrategyHistory(model:V200ViewModel,state:LedgerSnapshot,id:String,onExecution:(String)->Unit,onArchive:()->Unit) {
    val legacy by model.legacy.collectAsState()
    val current=MatchingEngine.ordered(state.executions.filter{it.strategyId==id}).reversed()
    val old=legacy.filter{it.strategy==id}
    LazyColumn(Modifier.fillMaxSize().testTag("v2-strategy-history"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{Text(strategyLabel(state,id),style=MaterialTheme.typography.titleLarge);Text("V2实际成交",style=MaterialTheme.typography.titleMedium)}
        if(current.isEmpty())item{EmptyLedger("暂无关联V2成交")}
        items(current,key={"execution-${it.id}"}){e->TextButton({onExecution(e.id)},Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("${LocalDate.ofEpochDay(e.date)} ${e.name} ${e.code} ${actionLabel(e)}")}}
        item{Text("兼容历史（账户未推定、不计入V2统计）",style=MaterialTheme.typography.titleMedium)}
        if(old.isEmpty())item{EmptyLedger("暂无关联旧档案")}
        items(old,key={"legacy-${it.recordKey}"}){t->LedgerPanel(t.symbol){Text("${LocalDate.ofEpochDay(t.date)} · 原币种${t.currency} · 原净盈亏${t.netPnl}");Text("稳定ID ${t.recordKey}",style=MaterialTheme.typography.labelSmall)}}
        if(old.isNotEmpty())item{OutlinedButton(onArchive,Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("查看只读旧档案与原截图")}}
    }
}
