package com.tradereview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradereview.app.BuildConfig
import com.tradereview.app.backup.RestoreMode
import java.time.LocalDate

@Composable
fun SettingsPage(model: BackupViewModel, journalBusy: Boolean, modifier: Modifier = Modifier,
    onTags: () -> Unit = {}, onSymbols: () -> Unit = {}, theme: String = "system", onTheme: (String) -> Unit = {},
    palette: String = "green_profit", onPalette: (String) -> Unit = {}) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(message) { if (message != null) listState.scrollToItem(0) }
    var mode by rememberSaveable { mutableStateOf(RestoreMode.MERGE.name) }
    var confirm by remember(preview) { mutableStateOf(false) }
    val locked = busy || journalBusy
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::exportJson) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(model::exportCsv) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::readBackup) }
    LazyColumn(modifier.fillMaxSize().testTag("settings-list"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在处理，请等待完成") }
        message?.let { item { SectionCard("处理结果") { Text(it, style = MaterialTheme.typography.bodyMedium) } } }
        item { SectionCard("数据安全", "数据保存在本机；文件由你选择位置，应用不联网上传。") {
            Text("JSON 包含全部币种交易、每日日志、标签、品种、草稿、常用筛选和配色偏好。备份为明文，请选择你信任的位置。")
            Button(enabled = !locked && preview == null, onClick = { json.launch("交易复盘-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("保存完整 JSON 备份") }
            OutlinedButton(enabled = !locked, onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("选择 JSON 备份恢复") }
            Text("卸载会删除本机数据。建议定期备份；系统文件选择器也可能提供你自行配置的云盘。", style = MaterialTheme.typography.bodySmall)
        } }
        preview?.let { p -> item { SectionCard("恢复预览", "文件已校验，尚未修改本地数据。") {
            Text("导出时间：${p.backup.exportedAt}")
            Text("备份：${p.backup.trades.size} 笔交易 · ${p.backup.dailyReviews.size} 个日志日期")
            Text("盘前计划 ${p.backup.planCount} 份 · 盘后复盘 ${p.backup.reviewCount} 份（含全部标签）")
            Text("格式版本 ${p.backup.sourceSchemaVersion} · 标签 ${p.backup.tags.size} 个 · 品种 ${p.backup.instruments.size} 个 · 草稿 ${p.backup.drafts.size} 份")
            Text("常用筛选 ${p.backup.savedFilters.size} 份 · 盈亏配色 ${if(p.backup.settings.pnlPalette == "red_profit") "红盈绿亏" else "绿盈红亏"}")
            Text("合并将保留本地标签名称、收藏、排序与设置，补入新的稳定 ID 和草稿；覆盖替换全部数据。")
            Text("当前本地：${p.before.trades.size} 笔交易 · ${p.before.reviews.size} 个日志日期")
            Row { RadioButton(selected = mode == RestoreMode.MERGE.name, enabled = !locked, onClick = { mode = RestoreMode.MERGE.name }); Text("去重合并（保留本地冲突内容）", Modifier.padding(top = 12.dp)) }
            Text("新增 ${p.summary.newTrades} 笔交易；相同记录跳过 ${p.summary.repeatedTrades} 笔，交易冲突 ${p.summary.conflictingTrades} 笔。")
            Text("新增 ${p.summary.newDays} 个日期，补入 ${p.summary.addedPlans} 份计划、${p.summary.addedReviews} 份复盘；计划冲突 ${p.summary.conflictingPlans} 份、复盘冲突 ${p.summary.conflictingReviews} 份。")
            Row { RadioButton(selected = mode == RestoreMode.REPLACE.name, enabled = !locked, onClick = { mode = RestoreMode.REPLACE.name }); Text("覆盖恢复（替换全部本地记录）", Modifier.padding(top = 12.dp)) }
            Text("覆盖会删除不在此备份中的本地记录，建议先另存当前完整备份。", color = MaterialTheme.colorScheme.error)
            Button(enabled = !locked, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("继续确认恢复") }
            Row { TextButton(enabled = !locked, onClick = model::refreshPreview) { Text("重新预览") }; TextButton(enabled = !locked, onClick = model::cancelPreview) { Text("取消恢复") } }
        } } }
        item { SectionCard("交易明细导出", "CSV 用于查看或分析，不能用于完整恢复。") {
            OutlinedButton(enabled = !locked && preview == null, onClick = { csv.launch("交易明细-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("导出全部交易 CSV") }
            Text("UTF-8 中文，按币种保留金额，支持逗号、引号与换行。", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard("偏好与管理") {
            OutlinedButton(enabled = !locked && preview == null, onClick = onTags, modifier = Modifier.fillMaxWidth()) { Text("管理自定义标签") }
            OutlinedButton(enabled = !locked && preview == null, onClick = onSymbols, modifier = Modifier.fillMaxWidth()) { Text("管理常用品种") }
            Text("显示模式")
            SingleChoice(listOf(com.tradereview.app.data.Option("system", "跟随系统"), com.tradereview.app.data.Option("light", "浅色"), com.tradereview.app.data.Option("dark", "深色")), theme) { if(!locked && preview == null) onTheme(it) }
            Text("盈亏配色 · 日历、金额卡片与图表同步")
            SingleChoice(listOf(com.tradereview.app.data.Option("green_profit", "绿盈红亏"), com.tradereview.app.data.Option("red_profit", "红盈绿亏")), palette) { if(!locked && preview == null) onPalette(it) }
        } }
        item { SectionCard("应用信息") {
            Text("交易复盘 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text("本地数据库版本 4 · JSON 备份格式版本 3（兼容 V0.2 / V0.3）")
            Text("升级安装需要相同应用标识及签名；应用不会自动下载或上传数据。", style = MaterialTheme.typography.bodySmall)
        } }
    }
    if (confirm && preview != null) AlertDialog(
        onDismissRequest = { if (!locked) confirm = false },
        title = { Text(if (mode == RestoreMode.REPLACE.name) "确认覆盖全部本地数据？" else "确认去重合并？") },
        text = { Text(if (mode == RestoreMode.REPLACE.name) "将用备份中的 ${preview!!.backup.trades.size} 笔交易及 ${preview!!.backup.dailyReviews.size} 个日志日期替换本地全部记录。此操作不能在应用内撤销。" else "将补入预览中的新记录与缺少的计划／复盘，重复记录跳过，冲突保留本地内容。失败会回滚整个恢复。") },
        confirmButton = { TextButton(enabled = !locked, onClick = { confirm = false; model.confirmRestore(RestoreMode.valueOf(mode)) }) { Text("确认恢复") } },
        dismissButton = { TextButton(enabled = !locked, onClick = { confirm = false }) { Text("返回预览") } },
    )
}
