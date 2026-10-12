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
    palette: String = "green_profit", onPalette: (String) -> Unit = {}, onRisk: () -> Unit = {},
    imageLimit: Int = 10,onImageLimit: (Int)->Unit = {},onGoals: ()->Unit = {},onSmart:()->Unit = {},onHelp:()->Unit = {}) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val operations by (model.experience?.state ?: remember{ kotlinx.coroutines.flow.MutableStateFlow(com.tradereview.app.device.ExperienceState()) }).collectAsStateWithLifecycle()
    fun time(millis:Long)=if(millis<=0)"尚无记录"else java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    val listState = rememberLazyListState()
    LaunchedEffect(message, preview, busy) {
        if (!busy) {
            if (message != null) listState.scrollToItem(0)
            else if (preview != null) listState.scrollToItem(1)
        }
    }
    var mode by rememberSaveable { mutableStateOf(RestoreMode.MERGE.name) }
    var confirm by remember(preview) { mutableStateOf(false) }
    val locked = busy || journalBusy
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::exportJson) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(model::exportCsv) }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(model::exportZip) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::readBackup) }
    LazyColumn(modifier.fillMaxSize().testTag("settings-list"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在处理，请等待完成") }
        message?.let { item { SectionCard("处理结果") { Text(it, style = MaterialTheme.typography.bodyMedium) } } }
        item { SectionCard("完整备份与恢复", "卸载与换机前，先确认可读的完整 ZIP。") {
            Text("完整 ZIP 包含全部结构化数据、设置、目标及截图文件和校验清单。JSON 仅包含结构化数据与目标，不含图片。两种备份均为明文，请选择你信任的位置。")
            Button(enabled = !locked && preview == null, onClick = { zip.launch("交易复盘-完整-${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("complete-backup")) { Text("保存完整 ZIP 备份（含截图）") }
            Text("上次完整 ZIP 已写出：${time(operations.zipWrittenMillis)}",modifier=Modifier.testTag("zip-written-time"))
            Text("上次完整 ZIP 已回读校验：${time(operations.verifiedZipMillis)}",modifier=Modifier.testTag("zip-verified-time"))
            if(operations.zipVerification=="unavailable")Text("最近写出的ZIP未能回读，不代表完整性已验证。",color=MaterialTheme.colorScheme.error)
            OutlinedButton(enabled = !locked && preview == null, onClick = { json.launch("交易复盘-数据-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("保存 JSON 备份（不含图片）") }
            OutlinedButton(enabled = !locked, onClick = { restore.launch(arrayOf("application/zip","application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("选择 ZIP / JSON 备份恢复") }
            Text("卸载会删除本机数据。建议定期备份；系统文件选择器也可能提供你自行配置的云盘。", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(operations.remindersEnabled,{model.backupReminders(it)},enabled=!locked && model.experience!=null,modifier=Modifier.testTag("backup-reminders"));Text("本机备份建议 · 7天无已校验ZIP时显示")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(enabled=!locked && model.experience!=null,onClick={model.snoozeBackup(7)},modifier=Modifier.heightIn(min=48.dp)){Text("延后7天")}
                TextButton(enabled=!locked && model.experience!=null,onClick={model.snoozeBackup(30)},modifier=Modifier.heightIn(min=48.dp)){Text("延后30天")}
            }
            if(operations.snoozedUntilMillis>System.currentTimeMillis())Text("提醒延后至 ${time(operations.snoozedUntilMillis)}",style=MaterialTheme.typography.bodySmall)
            Text("提醒、导出历史与引导状态仅属于这台设备，不随恢复冒充另一台手机已备份。",style=MaterialTheme.typography.bodySmall)
        } }
        preview?.let { p -> item { SectionCard("恢复预览", "文件已校验，尚未修改本地数据。") {
            Text("导出时间：${p.backup.exportedAt}")
            Text("备份：${p.backup.trades.size} 笔交易 · ${p.backup.dailyReviews.size} 个日志日期")
            Text("盘前计划 ${p.backup.planCount} 份 · 盘后复盘 ${p.backup.reviewCount} 份（含全部标签）")
            Text("格式版本 ${p.backup.sourceSchemaVersion} · 标签 ${p.backup.tags.size} 个 · 品种 ${p.backup.instruments.size} 个 · 草稿 ${p.backup.drafts.size} 份")
            Text("常用筛选 ${p.backup.savedFilters.size} 份 · 盈亏配色 ${if(p.backup.settings.pnlPalette == "red_profit") "红盈绿亏" else "绿盈红亏"}")
            Text("专业记录 ${p.backup.trades.count { it.professional != null }} 笔 · 风险规则 ${p.backup.riskRules.size} 个币种 · 合约模板 ${p.backup.contractTemplates.size} 份")
            Text("智能配置 1 份 · 个人盘前项目 ${p.backup.preflightItems.size} · 检查历史 ${p.backup.preflightChecks.size} · OCR 草稿 ${p.backup.ocrDrafts.size}")
            Text("改善目标 ${p.backup.goals.size} 个 · 截图 ${p.backup.attachments.size} 张 · 原图上限 ${p.backup.evidenceSettings.maxInputMiB} MiB")
            Text(if(p.backup.backupScope=="with-attachment-files") "备份范围：完整 ZIP，全部截图已校验" else "备份范围：仅结构化数据，不含图片；覆盖将清理本机截图。",color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("backup-scope"))
            Text("当前本机截图 ${p.before.attachments.size} 张 · 目标 ${p.before.goals.size} 个；冲突交易的截图保留本地，不拼接不一致证据。")
            Text("冲突交易保留本地专业信息及纪律快照；覆盖会完整替换。")
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
            Text("上次JSON已写出：${time(operations.jsonWrittenMillis)} · 上次已校验：${time(operations.verifiedJsonMillis)}",style=MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard("偏好与管理") {
            OutlinedButton(enabled = !locked && preview == null, onClick = onTags, modifier = Modifier.fillMaxWidth()) { Text("管理自定义标签") }
            OutlinedButton(enabled = !locked && preview == null, onClick = onSymbols, modifier = Modifier.fillMaxWidth()) { Text("管理常用品种") }
            OutlinedButton(enabled = !locked && preview == null, onClick = onRisk, modifier = Modifier.fillMaxWidth()) { Text("风险规则与合约模板") }
            OutlinedButton(enabled=!locked && preview==null,onClick=onSmart,modifier=Modifier.fillMaxWidth()){Text("智能洞察与规则设置")}
            OutlinedButton(enabled = !locked && preview == null, onClick = onGoals, modifier = Modifier.fillMaxWidth()) { Text("纪律改善目标") }
            Text("原图大小上限 · 私有压缩图最长边 2048，单张最多 4 MiB，每笔最多 10 张")
            SingleChoice(listOf(5,10,20).map { com.tradereview.app.data.Option(it.toString(),"$it MiB") },imageLimit.toString()) { if(!locked && preview==null)onImageLimit(it.toInt()) }
        } }
        item { SectionCard("显示与盈亏配色") {
            Text("显示模式")
            SingleChoice(listOf(com.tradereview.app.data.Option("system", "跟随系统"), com.tradereview.app.data.Option("light", "浅色"), com.tradereview.app.data.Option("dark", "深色")), theme) { if(!locked && preview == null) onTheme(it) }
            Text("盈亏配色 · 日历、金额卡片与图表同步")
            SingleChoice(listOf(com.tradereview.app.data.Option("green_profit", "绿盈红亏"), com.tradereview.app.data.Option("red_profit", "红盈绿亏")), palette) { if(!locked && preview == null) onPalette(it) }
        } }
        item{PrivacyDataCard()}
        item { SectionCard("应用信息") {
            Text("交易复盘 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text(if(BuildConfig.APPLICATION_ID.endsWith(".rc"))"RC测试包 · 与正式应用数据隔离；不代表固定签名已就绪"else"正式身份的稳定状态以发布签名说明与真机验收为准")
            Text("应用标识：${BuildConfig.APPLICATION_ID}")
            Text("本地数据库版本 7 · JSON 格式 6 / 完整 ZIP 格式 1（兼容 V0.2–V0.8 受支持备份）")
            Text("升级安装需要相同应用标识及签名；应用不会自动下载或上传数据。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onHelp,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("show-help")){Text("使用指南与备份说明")}
        } }
    }
    if (confirm && preview != null) AlertDialog(
        onDismissRequest = { if (!locked) confirm = false },
        title = { Text(if (mode == RestoreMode.REPLACE.name) "确认覆盖全部本地数据？" else "确认去重合并？") },
        text = { Text(if (mode == RestoreMode.REPLACE.name) "将用备份中的 ${preview!!.backup.trades.size} 笔交易、${preview!!.backup.dailyReviews.size} 个日志日期、${preview!!.backup.attachments.size} 张截图和 ${preview!!.backup.goals.size} 个目标替换本地全部记录。${if(preview!!.backup.backupScope=="structured-data-only") "此 JSON 不含图片，覆盖会清理本机所有截图。" else "图片校验通过后才提交数据。"}此操作不能在应用内撤销。" else "将补入新记录、计划／复盘和目标。重复记录跳过，冲突交易及其截图保留本地，图片超过每笔上限会拒绝合并。失败不会留下半恢复数据。") },
        confirmButton = { TextButton(enabled = !locked, onClick = { confirm = false; model.confirmRestore(RestoreMode.valueOf(mode)) }) { Text("确认恢复") } },
        dismissButton = { TextButton(enabled = !locked, onClick = { confirm = false }) { Text("返回预览") } },
    )
}
