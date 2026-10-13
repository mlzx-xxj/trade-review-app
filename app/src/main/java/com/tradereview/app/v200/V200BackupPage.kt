package com.tradereview.app.v200

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.ui.BackupViewModel
import com.tradereview.app.backup.RestoreMode
import com.tradereview.app.BuildConfig
import java.time.LocalDate

@Composable fun V200BackupPage(backup:BackupViewModel){val busy by backup.busy.collectAsState();val preview by backup.preview.collectAsState();val result by backup.message.collectAsState();var exportChoice by rememberSaveable{mutableStateOf(false)};var csvChoice by rememberSaveable{mutableStateOf(false)};var mode by rememberSaveable{mutableStateOf("merge")};var confirming by rememberSaveable{mutableStateOf(false)}
    val zip=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let(backup::exportZip)}
    val json=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(backup::exportJson)}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){it?.let(backup::exportLedgerCsv)}
    val oldCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){it?.let(backup::exportCsv)}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(backup::readBackup)}
    val date=LocalDate.now().toString()
    LazyColumn(Modifier.fillMaxSize().testTag("v2-backup"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Button({exportChoice=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("v2-export-backup")){Text("保存完整 ZIP 备份")}}
        item{OutlinedButton({restore.launch(arrayOf("application/zip","application/json","application/octet-stream"))},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("v2-import-backup")){Text("从 ZIP／JSON 恢复")}}
        item{OutlinedButton({csvChoice=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("v2-export-csv")){Text("导出交易 CSV")}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在验证或处理本地文件，请稍候")}
        result?.let{item{LedgerPanel("处理结果"){Text(it)}}}
        preview?.let{p->item{LedgerPanel("恢复预览（尚未写入）"){
            Text("导出时间 ${p.backup.exportedAt}")
            val incoming=p.backup.ledger;val local=p.before.ledger
            if(incoming==null)Text("旧版档案备份，不含V2成交账本；本机V2成交及资金保持原样，可靠旧策略可加入列表。")else{
                Text("V2：${incoming.executions.size}笔成交、${incoming.cycles.size}个周期、${incoming.plans.size}个标的计划、${incoming.reviews.size}条持仓复盘、${incoming.cashFlows.size}笔资金流水")
                Text("账户${incoming.accounts.size}、策略${incoming.strategies.size}、风险配置${incoming.risks.size}、合约规则${incoming.rules.size}、匹配${incoming.matches.size}、原始成交修订${incoming.audits.size}")
                val existing=local?.executions.orEmpty().associateBy{it.id};val repeated=incoming.executions.count{existing[it.id]==it};val conflicts=incoming.executions.count{existing[it.id]?.let{old->old!=it}==true}
                Text("成交新增${incoming.executions.count{it.id !in existing}} · 重复$repeated · 冲突$conflicts；合并保留本地冲突记录，并重新校验配对。")
            }
            Text("旧档案：交易${p.backup.trades.size}、日记${p.backup.dailyReviews.size}、标签${p.backup.tags.size}、截图${p.backup.attachments.size}")
            Text(if(p.backup.backupScope=="with-attachment-files")"完整ZIP含旧截图文件，SHA校验通过"else"JSON仅结构化数据，不含截图文件")
            Text("当前：V2成交${local?.executions?.size?:0}、旧交易${p.before.trades.size}、旧截图${p.before.attachments.size}")
            ChoiceRow(mode,listOf("merge" to "去重合并","replace" to "覆盖备份范围"),{mode=it})
            Text(if(mode=="replace")"将替换备份覆盖的本机数据；JSON覆盖旧档案时旧截图可能被清除。请先保存当前完整ZIP。"else"冲突保留本地。合并后如违反T+1、超量平仓或复盘关联规则，会整体拒绝。",color=MaterialTheme.colorScheme.error)
            Row{TextButton({backup.refreshPreview()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("重新核对")};TextButton({backup.cancelPreview()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("取消")};Button({confirming=true},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("v2-restore-continue")){Text("继续确认恢复")}}
        }}}
        item{Text("${BuildConfig.VERSION_NAME} · 本机离线\nZIP/JSON为明文，SHA-256不是加密。CSV仅导出，不能作为完整恢复备份。",style=MaterialTheme.typography.bodySmall)}
    }
    if(exportChoice)AlertDialog(onDismissRequest={exportChoice=false},title={Text("选择备份范围")},text={Column{Text("完整 ZIP 包含新账本、旧档案及全部旧截图；JSON不含图片。");TextButton({exportChoice=false;json.launch("交易复盘-V2-结构化-$date.json")},modifier=Modifier.heightIn(min=48.dp)){Text("仅保存 JSON（不含截图）")}}},confirmButton={Button({exportChoice=false;zip.launch("交易复盘-V2-完整-$date.zip")},Modifier.heightIn(min=48.dp)){Text("完整 ZIP（推荐）")}},dismissButton={TextButton({exportChoice=false}){Text("取消")}})
    if(csvChoice)AlertDialog(onDismissRequest={csvChoice=false},title={Text("导出交易 CSV")},text={Column{TextButton({csvChoice=false;oldCsv.launch("交易复盘-旧档案-$date.csv")},modifier=Modifier.heightIn(min=48.dp)){Text("导出旧档案原明细（不换算）")}}},confirmButton={Button({csvChoice=false;csv.launch("交易复盘-V2-成交-$date.csv")},Modifier.heightIn(min=48.dp)){Text("V2真实成交")}},dismissButton={TextButton({csvChoice=false}){Text("取消")}})
    if(confirming && preview!=null)AlertDialog(onDismissRequest={if(!busy)confirming=false},title={Text(if(mode=="replace")"确认覆盖备份范围？"else"确认去重合并？")},text={Text("确认后才写入，失败会回滚数据库及文件引用。覆盖可能丢失本机内容，建议先完整ZIP备份。不会把旧净盈亏记录伪造成开平成交。")},confirmButton={TextButton({backup.confirmRestore(if(mode=="replace")RestoreMode.REPLACE else RestoreMode.MERGE);confirming=false},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("v2-confirm-restore")){Text("确认恢复")}},dismissButton={TextButton({confirming=false},enabled=!busy){Text("返回预览")}})
}
