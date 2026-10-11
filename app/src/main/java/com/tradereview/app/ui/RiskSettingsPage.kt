package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*

@Composable
fun RiskSettingsPage(state: JournalState, model: TradeViewModel, busy: Boolean, modifier: Modifier = Modifier) {
    var currency by rememberSaveable { mutableStateOf("CNY") }
    var edit by remember { mutableStateOf<ContractTemplate?>(null) }
    var remove by remember { mutableStateOf<ContractTemplate?>(null) }
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("risk-settings-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("风险规则", "用户自行设置的参考纪律，不连接账户，也不发出交易指令。") {
            SingleChoice(Presets.currencies, currency) { currency = it }
            Text("不同币种独立检查，不进行换汇；账户权益为你手动填写的参考值。")
        } }
        item { key(currency) { RuleFields(state.riskRules.firstOrNull { it.currency == currency } ?: RiskRules(currency), busy, model::saveRiskRules) } }
        item { SectionCard("用户合约参数模板", "不提供未经核验的交易所数字；每次复用都须确认适用于本次合约。") {
            Button(enabled = !busy, onClick = { edit = ContractTemplate(name = "", market = "futures", symbol = "螺纹钢", quantityUnit = "futures_lot") }) { Text("新增合约参数模板") }
            if(state.contractTemplates.isEmpty()) Text("尚无模板；也可在专业录入区保存当前参数。")
        } }
        items(state.contractTemplates, key = { it.id }) { value -> SectionCard(value.name, "${value.symbol} · ${Presets.label(Presets.markets, value.market)}") {
            Text("${Presets.label(ProfessionalMath.units, value.quantityUnit)} · 乘数 ${value.multiplier ?: "未提供"} · 最小变动 ${value.minTick ?: "未提供"}")
            Text(if(value.userVerified) "由用户核实，复用仍须确认当前合约" else "尚未核实，不自动用于交易")
            Row { TextButton(enabled = !busy, onClick = { edit = value }) { Text("编辑模板") }; TextButton(enabled = !busy, onClick = { remove = value }) { Text("删除模板") } }
        } }
    }
    edit?.let { value -> TemplateEditor(value, busy, onDismiss = { edit = null }) { model.saveTemplate(it); edit = null } }
    remove?.let { value -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("删除参数模板？") }, text = { Text("仅删除“${value.name}”，历史交易中的参数和专业信息保留。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { model.deleteTemplate(value); remove = null }) { Text("删除模板") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } }) }
}

@Composable
private fun RuleFields(initial: RiskRules, busy: Boolean, onSave: (RiskRules) -> Unit) {
    var equity by rememberSaveable { mutableStateOf(initial.accountEquity.orEmpty()) }
    var risk by rememberSaveable { mutableStateOf(initial.maxRiskPercent.orEmpty()) }
    var position by rememberSaveable { mutableStateOf(initial.maxPositionPercent.orEmpty()) }
    var streak by rememberSaveable { mutableStateOf(initial.lossStreakThreshold?.toString().orEmpty()) }
    var daily by rememberSaveable { mutableStateOf(initial.dailyLossLimit.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard("${initial.currency} 参考纪律", "空白表示未设置，不填成 0。规则修改不重写旧交易的检查快照。") {
        DecimalField("账户权益（${initial.currency}，可选）", equity, "rule-equity", busy) { equity = it }
        DecimalField("单笔最大计划风险比例（%，可选）", risk, "rule-risk-percent", busy) { risk = it }
        DecimalField("最大仓位比例（%，可选）", position, "rule-position-percent", busy) { position = it }
        DecimalField("连续亏损提醒（笔，可选）", streak, "rule-streak", busy) { streak = it }
        DecimalField("每日净亏损提醒金额（${initial.currency}，可选）", daily, "rule-daily-loss", busy) { daily = it }
        Text("风险比例 = 初始计划风险 / 本币种账户权益。仓位比例单独检查，不视作风险比例。连亏按日期与录入次序；持平中断。每日金额包含本笔，按净亏损检查。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, onClick = { try {
            fun money(v: String) = if(v.isBlank()) null else (Money.parse(v)?.takeIf { it.signum() > 0 } ?: throw IllegalArgumentException("权益和日亏损需为正金额，最多两位小数")).toPlainString()
            fun percent(v: String) = if(v.isBlank()) null else (ProfessionalMath.decimal(v) ?: throw IllegalArgumentException("比例数字无效")).let(ProfessionalMath::plain)
            val count = if(streak.isBlank()) null else streak.toIntOrNull() ?: throw IllegalArgumentException("连续亏损阈值需为整数")
            val r = RiskRules(initial.currency, money(equity), percent(risk), percent(position), count, money(daily))
            ProfessionalMath.validateRule(r); onSave(r); error = null
        } catch(e: IllegalArgumentException) { error = e.message } }, modifier = Modifier.fillMaxWidth().testTag("save-risk-rules")) { Text("保存当前币种规则") }
    }
}

@Composable
private fun TemplateEditor(initial: ContractTemplate, busy: Boolean, onDismiss: () -> Unit, onSave: (ContractTemplate) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }; var market by remember { mutableStateOf(initial.market) }
    var symbol by remember { mutableStateOf(initial.symbol) }; var unit by remember { mutableStateOf(initial.quantityUnit) }
    var multiplier by remember { mutableStateOf(initial.multiplier.orEmpty()) }; var tick by remember { mutableStateOf(initial.minTick.orEmpty()) }
    var note by remember { mutableStateOf(initial.note) }; var verified by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("用户合约参数模板") }, text = { LazyColumn(Modifier.heightIn(max = 480.dp).testTag("template-editor-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(name, { name = it.take(30) }, label = { Text("模板名称") }, singleLine = true) }
        item { SingleChoice(Presets.markets, market) { market = it; unit = "unknown" } }
        item { OutlinedTextField(symbol, { symbol = it.take(30) }, label = { Text("品种名称，与交易品种一致") }, singleLine = true) }
        item { SingleChoice(ProfessionalMath.units(market), unit) { unit = it } }
        item { DecimalField("已核实乘数（可留空）", multiplier, "template-multiplier", busy) { multiplier = it } }
        item { DecimalField("最小价格变动（可选）", tick, "template-tick", busy) { tick = it } }
        item { OutlinedTextField(note, { note = it.take(300) }, label = { Text("适用合约及参数来源备注") }) }
        item { Row { Checkbox(verified, { verified = it }, modifier = Modifier.testTag("template-user-verified")); Text("本次填写参数已经核实", Modifier.padding(top = 12.dp)) } }
        item { Text("未勾选也可保存为待核实模板；交易中不会自动应用。") }
        item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    } }, confirmButton = { TextButton(enabled = !busy, onClick = { try {
        fun numeric(v: String) = if(v.isBlank()) null else (ProfessionalMath.decimal(v) ?: throw IllegalArgumentException("参数数字无效")).let(ProfessionalMath::plain)
        val t = initial.copy(name = name.trim(), market = market, symbol = symbol.trim(), quantityUnit = unit, multiplier = numeric(multiplier), minTick = numeric(tick), note = note, userVerified = verified)
        ProfessionalMath.validateTemplate(t); onSave(t)
    } catch(e: IllegalArgumentException) { error = e.message ?: "模板字段无效" } }) { Text("保存模板") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
