package com.tradereview.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tradereview.app.data.Option
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.domain.Analytics
import com.tradereview.app.domain.Money
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateControl(date: Long, onDate: (Long) -> Unit, allowFuture: Boolean = false) {
    var show by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    OutlinedButton(onClick = { show = true }) { Text("日期：${dayLabel(date)}") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onDate(today) }) { Text("今天") }
        TextButton(onClick = { onDate(today - 1) }) { Text("昨天") }
        if (allowFuture) TextButton(onClick = { onDate(today + 1) }) { Text("明天") }
    }
    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowFuture || utcTimeMillis / 86_400_000L <= today
            },
        )
        DatePickerDialog(onDismissRequest = { show = false }, confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onDate(it / 86_400_000L) }
                show = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } }) {
            DatePicker(state, title = { Text("选择日期", Modifier.padding(16.dp)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeEditor(original: Trade?, busy: Boolean, saveError: String?, onDismiss: () -> Unit, onSave: (Trade) -> Unit, onDelete: (Trade) -> Unit) {
    val initial = original ?: Trade()
    val key = initial.id
    var date by rememberSaveable(key) { mutableLongStateOf(initial.date) }
    var market by rememberSaveable(key) { mutableStateOf(initial.market) }
    var symbol by rememberSaveable(key) { mutableStateOf(initial.symbol) }
    var direction by rememberSaveable(key) { mutableStateOf(initial.direction) }
    var currency by rememberSaveable(key) { mutableStateOf(initial.currency) }
    var sign by rememberSaveable(key) { mutableStateOf(if (Money.value(initial).signum() < 0) "loss" else "gain") }
    var amount by rememberSaveable(key) { mutableStateOf(if (original == null) "" else Money.value(initial).abs().toPlainString()) }
    var position by rememberSaveable(key) { mutableFloatStateOf(initial.positionPercent.toFloat()) }
    var strategy by rememberSaveable(key) { mutableStateOf(initial.strategy) }
    var emotion by rememberSaveable(key) { mutableStateOf(initial.emotion) }
    var mistakes by rememberSaveable(key) { mutableStateOf(initial.mistakes) }
    var discipline by rememberSaveable(key) { mutableStateOf(initial.discipline) }
    var reviewed by rememberSaveable(key) { mutableStateOf(initial.reviewed) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy)) {
        BackHandler(busy) { }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                modifier = Modifier.imePadding(),
                topBar = {
                    TopAppBar(title = { Text(if (original == null) "记录一笔交易" else "编辑交易与复盘") },
                        navigationIcon = { IconButton(enabled = !busy, onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                        actions = { if (original != null) IconButton(enabled = !busy, onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除交易") } })
                },
                bottomBar = {
                    Surface(shadowElevation = 4.dp) {
                        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), onClick = {
                            val input = (if (sign == "loss") "-" else "") + amount.trim()
                            val trade = initial.copy(date = date, market = market, symbol = symbol, direction = direction,
                                currency = currency, netPnl = input, positionPercent = position.toInt(), strategy = strategy,
                                emotion = emotion, mistakes = mistakes, discipline = discipline, reviewed = reviewed)
                            error = if (Money.parse(amount)?.signum() == -1) "请输入正数金额，通过上方选项选择盈利或亏损" else Analytics.validate(trade)
                            if (error == null) onSave(trade)
                        }) { Text(if (busy) "正在保存…" else "保存交易") }
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionCard("交易信息", "记录已平仓交易，净盈亏请扣除手续费。") {
                        DateControl(date, { date = it })
                        Text("市场")
                        SingleChoice(Presets.markets, market) { value ->
                            market = value
                            symbol = Presets.symbols(value).first()
                            currency = when (value) { "crypto" -> "USDT"; "forex" -> "USD"; else -> "CNY" }
                        }
                        Text("常用品种")
                        SingleChoice(Presets.symbols(market).map { Option(it, it) }, symbol) { symbol = it }
                        OutlinedTextField(symbol, { if (it.length <= 30) symbol = it }, label = { Text("品种（可自定义）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("方向")
                        SingleChoice(Presets.directions, direction) { direction = it }
                        Text("结算币种")
                        SingleChoice(Presets.currencies, currency) { currency = it }
                        SingleChoice(listOf(Option("gain", "盈利／持平"), Option("loss", "亏损")), sign) { sign = it }
                        OutlinedTextField(amount, { amount = it }, label = { Text("净盈亏金额（$currency）") },
                            supportingText = { Text("输入正数金额，亏损请选择上方“亏损”。") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Text("本笔仓位：${position.toInt()}%")
                        Slider(value = position, onValueChange = { position = it }, valueRange = 1f..100f, steps = 98)
                        Text("使用策略")
                        SingleChoice(Presets.strategies, strategy) { strategy = it }
                    }
                    SectionCard("本笔复盘", "勾选适用项；没有错误时可留空。") {
                        Text("交易时的情绪")
                        SingleChoice(Presets.emotions, emotion) { emotion = it }
                        Text("常见错误 · 可多选")
                        ChoiceChips(Presets.mistakes, mistakes) { mistakes = toggleTag(mistakes, it) }
                        Text("已执行的纪律 · 可多选")
                        ChoiceChips(Presets.discipline, discipline) { discipline = toggleTag(discipline, it) }
                        Row {
                            Checkbox(checked = reviewed, onCheckedChange = { reviewed = it })
                            Text("本笔已完成复盘", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    if (error != null) Text(requireNotNull(error), color = MaterialTheme.colorScheme.error)
                    if (saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (confirmDelete && original != null) {
            AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false }, title = { Text("删除这笔交易？") },
                text = { Text("${original.symbol} · ${dayLabel(original.date)}\n删除后无法恢复。") },
                confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(original) }) { Text("删除") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") } })
        }
    }
}
