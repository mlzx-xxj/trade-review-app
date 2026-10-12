package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.math.RoundingMode

@Composable
fun DecimalField(label: String, value: String, tag: String, busy: Boolean, onValue: (String) -> Unit) {
    OutlinedTextField(enabled = !busy, value = value, onValueChange = { if(it.length <= 40) onValue(it) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
fun ProfessionalFields(d: ProfessionalDraft, market: String, symbol: String, direction: String, currency: String,
    templates: List<ContractTemplate>, busy: Boolean, onValue: (ProfessionalDraft) -> Unit, onTemplate: (ContractTemplate) -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf<ContractTemplate?>(null) }
    var verified by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var templateError by remember { mutableStateOf<String?>(null) }
    val parsed = runCatching { ProfessionalMath.professional(d).also { ProfessionalMath.validate(it, market) } }
    val p = parsed.getOrNull()
    SectionCard("专业交易信息", "可留空补录。参数须自行核实；手动净盈亏不会因补录价格而改变。") {
        Text("盈亏计算模式")
        SingleChoice(listOf(Option("manual", "手动盈亏"), Option("auto", "自动计算")), d.pnlMode) { if(it != d.pnlMode) mode = it }
        Text(if(d.pnlMode == "manual") "手动金额是本笔权威净盈亏，专业字段仅供记录与风险计算。" else "自动净盈亏入账保留两位小数；手续费须明确录入，其他未录入成本不重复扣除。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(enabled = !busy, value = d.contractCode, onValueChange = { if(it.length <= 30) onValue(d.copy(contractCode = it)) },
            label = { Text("合约代码（可选，如 rb2701、i2701）") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("professional-contract"))
        Text("品种：$symbol · ${Presets.label(Presets.directions, direction)}")
        Text("数量单位（不同市场不互换）")
        SingleChoice(ProfessionalMath.units(market), d.quantityUnit) { onValue(d.copy(quantityUnit = it)) }
        Row { Checkbox(enabled = !busy, checked = d.linearConfirmed, onCheckedChange = { onValue(d.copy(linearConfirmed = it)) }, modifier = Modifier.testTag("professional-linear-confirmed")); Text("已核实线性计价，价差 × 数量 × 乘数为 $currency", Modifier.padding(top = 12.dp)) }
        Text("需换汇或反向 / 非线性合约请用手动盈亏与手填风险；不自动推测换算规则。", style = MaterialTheme.typography.bodySmall)
        DecimalField("入场成交价", d.entryPrice, "professional-entry", busy) { onValue(d.copy(entryPrice = it)) }
        DecimalField("出场成交价", d.exitPrice, "professional-exit", busy) { onValue(d.copy(exitPrice = it)) }
        DecimalField("数量 / 手数", d.quantity, "professional-quantity", busy) { onValue(d.copy(quantity = it)) }
        DecimalField("每单位 / 每手合约乘数（自行核实）", d.multiplier, "professional-multiplier", busy) { onValue(d.copy(multiplier = it)) }
        Text("股、手、基础单位或币 / 张均按“数量 × 已核实乘数”换算，不预填真实参数。")
        DecimalField("最小价格变动单位（可选）", d.minTick, "professional-tick", busy) { onValue(d.copy(minTick = it)) }
        Text("价格、数量与乘数最多 8 位小数；历史负价可如实记录。最小变动单位仅供参考。", style = MaterialTheme.typography.bodySmall)
        val matching = templates.filter { it.market == market && it.symbol == symbol }
        matching.forEach { t -> OutlinedButton(enabled = !busy, onClick = { selectedTemplate = t; verified = false }, modifier = Modifier.fillMaxWidth()) {
            Text("复用 ${t.name} · 乘数 ${t.multiplier ?: "未提供"}${if(!t.userVerified) " · 待核实" else ""}")
        } }
        OutlinedButton(enabled = !busy, onClick = { templateName = "$symbol 参数".take(30); templateError = null; verified = false; saveTemplate = true }) { Text("保存当前参数模板") }
        DecimalField("手续费（$currency，确无费用请填 0）", d.fees, "professional-fees", busy) { onValue(d.copy(fees = it)) }
        DecimalField("其他明确实际成本（$currency，可选）", d.otherCosts, "professional-costs", busy) { onValue(d.copy(otherCosts = it)) }
        Row { Checkbox(enabled = !busy, checked = d.costsConfirmed, onCheckedChange = { onValue(d.copy(costsConfirmed = it)) }, modifier = Modifier.testTag("professional-costs-confirmed")); Text("已确认实际成本完整，空白其他成本不计入", Modifier.padding(top = 12.dp)) }
        Text("成交价若已反映滑点，不重复扣除滑点。手续费与其他实际成本以本笔结算币种填写。", style = MaterialTheme.typography.bodySmall)
        val gross = p?.let { ProfessionalMath.gross(it, direction) }; val net = p?.let { ProfessionalMath.net(it, direction) }
        Text("毛盈亏：${gross?.let(ProfessionalMath::plain) ?: "必要数据未提供"} $currency", modifier = Modifier.testTag("professional-gross"))
        Text("手续费：${p?.fees ?: "未提供"} · 其他成本：${p?.otherCosts ?: "未录入，不扣除"} $currency")
        Text("自动净盈亏：${net?.let { Money.format(it, true) } ?: "必要数据或成本确认未提供"} $currency", modifier = Modifier.testTag("professional-net"))
        if(d.pnlMode == "auto" && net != null) Text("毛盈亏和成本使用完整精度；入账按 HALF_UP 舍入到两位小数。", style = MaterialTheme.typography.bodySmall)
        Text("初始计划风险口径")
        SingleChoice(listOf(Option("price", "按初始止损价格"), Option("amount", "手填最大亏损"), Option("none", "未提供")), d.riskMode) { onValue(d.copy(riskMode = it)) }
        DecimalField("初始计划止损价", d.initialStop, "professional-stop", busy) { onValue(d.copy(initialStop = it)) }
        Text("做多初始止损应低于入场，做空应高于入场；方向或距离无效可如实记录，但不计算价格风险和 R。", style = MaterialTheme.typography.bodySmall)
        DecimalField("计划止盈价（可选）", d.takeProfit, "professional-target", busy) { onValue(d.copy(takeProfit = it)) }
        if(d.riskMode == "price") DecimalField("计划止损执行成本（$currency，可选）", d.stopExecutionCost, "professional-stop-cost", busy) { onValue(d.copy(stopExecutionCost = it)) }
        if(d.riskMode == "amount") DecimalField("计划最大亏损金额（$currency，正数）", d.manualRisk, "professional-risk", busy) { onValue(d.copy(manualRisk = it)) }
        val risk = ProfessionalMath.risk(p, direction)
        Text("初始计划风险：${risk?.let(ProfessionalMath::plain) ?: "未提供"} $currency", modifier = Modifier.testTag("professional-initial-risk"))
        Text(ProfessionalMath.riskExplanation(p, direction), style = MaterialTheme.typography.bodySmall)
        DecimalField("本笔计划仓位百分比（可选，不等于风险比例）", d.plannedPositionPercent, "professional-plan-position", busy) { onValue(d.copy(plannedPositionPercent = it)) }
        Text("计划状态（未知单独统计）")
        SingleChoice(listOf(Option("unknown", "未说明"), Option("in", "计划内"), Option("out", "计划外")), d.planStatus) { onValue(d.copy(planStatus = it)) }
        OutlinedTextField(enabled = !busy, value = d.reason, onValueChange = { if(it.length <= 300) onValue(d.copy(reason = it)) }, label = { Text("交易理由（可选）") }, modifier = Modifier.fillMaxWidth().testTag("professional-reason"))
        OutlinedTextField(enabled = !busy, value = d.executionNote, onValueChange = { if(it.length <= 300) onValue(d.copy(executionNote = it)) }, label = { Text("执行备注（可选）") }, modifier = Modifier.fillMaxWidth())
        parsed.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    mode?.let { next -> AlertDialog(onDismissRequest = { mode = null }, title = { Text("确认切换盈亏模式？") },
        text = { Text(if(next == "auto") "保存时将使用专业字段计算的净盈亏。手工金额留在草稿中，不会现在覆盖；缺少有效参数或成本确认时不能保存自动结果。" else "恢复使用快速区的手工金额。专业信息保留，保存前请核对净盈亏。") },
        confirmButton = { TextButton(onClick = { onValue(d.copy(pnlMode = next)); mode = null }) { Text("确认切换") } }, dismissButton = { TextButton(onClick = { mode = null }) { Text("取消") } }) }
    selectedTemplate?.let { t -> AlertDialog(onDismissRequest = { selectedTemplate = null }, title = { Text("核实本次合约参数") }, text = { Column {
        Text("${t.name} · $symbol\n单位 ${Presets.label(ProfessionalMath.units, t.quantityUnit)} · 乘数 ${t.multiplier ?: "未提供"} · 最小变动 ${t.minTick ?: "未提供"}\n${t.note}")
        Row { Checkbox(verified, { verified = it }); Text("我已核实适用于本次合约", Modifier.padding(top = 12.dp)) }
        Text("不自动填价格、手数、费用或改动净盈亏。")
    } }, confirmButton = { TextButton(enabled = verified && !busy, onClick = { onValue(ProfessionalMath.applyTemplate(d, t, market, symbol, verified)); selectedTemplate = null }) { Text("确认复用") } }, dismissButton = { TextButton(onClick = { selectedTemplate = null }) { Text("取消") } }) }
    if(saveTemplate) AlertDialog(onDismissRequest = { saveTemplate = false }, title = { Text("保存用户合约参数") }, text = { Column {
        OutlinedTextField(templateName, { templateName = it.take(30) }, label = { Text("模板名称") })
        Text("只保存当前品种、单位、乘数和最小变动单位，不包含成交结果。未来复用仍需核实。")
        Row { Checkbox(verified, { verified = it }); Text("参数由我填写并核实", Modifier.padding(top = 12.dp)) }
        templateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(enabled = verified && !busy, onClick = { try {
        val value = ProfessionalMath.professional(d)
        val t = ContractTemplate(name = templateName.trim(), market = market, symbol = symbol, quantityUnit = d.quantityUnit, multiplier = value.multiplier, minTick = value.minTick, userVerified = true)
        ProfessionalMath.validateTemplate(t); onTemplate(t); saveTemplate = false
    } catch(e: IllegalArgumentException) { templateError = e.message ?: "请核对名称、单位与参数" } }) { Text("保存模板") } }, dismissButton = { TextButton(onClick = { saveTemplate = false }) { Text("取消") } })
}
