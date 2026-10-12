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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagement(state: JournalState, busy: Boolean, model: TradeViewModel, modifier: Modifier = Modifier) {
    var kind by rememberSaveable { mutableStateOf("strategy") }
    var edit by remember { mutableStateOf<ManagedTag?>(null) }
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("tag-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("标签管理", "隐藏仅影响新选择，已有记录仍显示当前名称。ID 不随重命名改变。") {
            SingleChoice(TagKind.names.map { Option(it.key, it.value) }, kind) { kind = it }
            Button(enabled = !busy, onClick = { edit = ManagedTag(kind, "custom_${newKey()}", "", state.tags.count { it.kind == kind }) }) { Text("新增标签") }
            Text("最近使用、收藏优先；同优先级按手动排序显示。", style = MaterialTheme.typography.bodySmall)
        } }
        val tags = state.tags.filter { it.kind == kind }.sortedWith(compareBy<ManagedTag> { it.sortOrder }.thenBy { it.id })
        items(tags, key = { it.id }) { tag -> SectionCard(tag.name + if(tag.hidden) "（已隐藏）" else "") {
            Text("${if(tag.favorite) "已收藏" else "未收藏"} · 稳定 ID ${tag.id}", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { edit = tag }, modifier = Modifier.testTag("rename-${tag.id}")) { Text("重命名") }
                TextButton(enabled = !busy, onClick = { model.saveTag(tag.copy(favorite = !tag.favorite)) }, modifier = Modifier.testTag("favorite-tag-${tag.id}")) { Text(if(tag.favorite) "取消收藏" else "收藏") }
                TextButton(enabled = !busy, onClick = { model.saveTag(tag.copy(hidden = !tag.hidden)) }, modifier = Modifier.testTag("hide-${tag.id}")) { Text(if(tag.hidden) "显示" else "隐藏") }
                TextButton(enabled = !busy && tags.firstOrNull()?.id != tag.id, onClick = { model.moveTag(tag, -1) }, modifier = Modifier.testTag("up-tag-${tag.id}")) { Text("上移") }
                TextButton(enabled = !busy && tags.lastOrNull()?.id != tag.id, onClick = { model.moveTag(tag, 1) }) { Text("下移") }
            }
        } }
    }
    edit?.let { tag -> NameDialog(if(tag.name.isBlank()) "新增标签" else "重命名标签", tag.name, onDismiss = { edit = null }) { name ->
        model.saveTag(tag.copy(name = name)); edit = null
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstrumentManagement(state: JournalState, busy: Boolean, model: TradeViewModel, modifier: Modifier = Modifier) {
    var market by rememberSaveable { mutableStateOf(state.settings.lastMarket) }
    var query by rememberSaveable { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("symbol-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("常用品种管理", if(query.isBlank())"收藏与最近使用优先。排序影响同优先级品种；不会改变历史交易。"else "${Presets.label(Presets.markets,market)} · 本机搜索") {
            if(query.isBlank())SingleChoice(Presets.markets, market) { market = it }
            OutlinedTextField(query, { query = it }, label = { Text("搜索名称或代码") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("symbol-search"),
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus()}),
                trailingIcon={if(query.isNotBlank())TextButton(onClick={query="";keyboard?.hide();focus.clearFocus()}){Text("清除")}})
            if(query.isBlank())Button(enabled = !busy, onClick = { add = true }) { Text("添加自定义品种") }
        } }
        val all = state.instruments.filter { it.market == market }.sortedWith(compareBy<Instrument> { it.sortOrder }.thenBy { it.id })
        val matched = all.filter { query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) }
        if (matched.isEmpty()) item { Text("暂无匹配品种") }
        items(matched, key = { it.id }) { value -> SectionCard("${value.name} · ${value.code}") {
            Text(if(value.lastUsed > 0) "最近使用" else "尚未使用", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { model.saveInstrument(value.copy(favorite = !value.favorite)) }, modifier = Modifier.testTag("favorite-symbol-${value.id}")) { Text(if(value.favorite) "取消收藏" else "收藏") }
                TextButton(enabled = !busy && all.firstOrNull()?.id != value.id, onClick = { model.moveInstrument(value, -1) }, modifier = Modifier.testTag("up-symbol-${value.id}")) { Text("上移") }
                TextButton(enabled = !busy && all.lastOrNull()?.id != value.id, onClick = { model.moveInstrument(value, 1) }) { Text("下移") }
            }
        } }
    }
    if (add) InstrumentDialog(market, onDismiss = { add = false }) { value -> model.saveInstrument(value); add = false }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { if(it.length <= 30) name = it }, label = { Text("标签名称") }, singleLine = true, modifier = Modifier.testTag("tag-name-field"))
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && '\u0000' !in name, onClick = { onSave(name.trim()) }) { Text("保存标签") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
@Composable
private fun InstrumentDialog(market: String, onDismiss: () -> Unit, onSave: (Instrument) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加自定义品种") }, text = { Column {
        OutlinedTextField(name, { if(it.length <= 30) name = it }, label = { Text("品种名称") }, singleLine = true)
        OutlinedTextField(code, { if(it.length <= 30) code = it }, label = { Text("品种代码") }, singleLine = true)
    } }, confirmButton = { TextButton(enabled = name.isNotBlank() && code.isNotBlank(), onClick = {
        onSave(Instrument("custom_${newKey()}", market, name.trim(), code.trim(), 1000, favorite = true))
    }) { Text("保存品种") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
