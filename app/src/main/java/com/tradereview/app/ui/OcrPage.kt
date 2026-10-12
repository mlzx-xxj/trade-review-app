package com.tradereview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.time.LocalDate
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class OcrRiskPreview(val draft:EntryDraft,val rules:List<RiskRules>,val history:List<Trade>,val event:RiskEvent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPage(ocr:OcrViewModel,state:JournalState,model:TradeViewModel,onDismiss:()->Unit) {
    val session by ocr.state.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val feedback by model.message.collectAsStateWithLifecycle()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)ocr.read(uri,state.evidenceSettings)}
    var selected by remember(session.sessionId) { mutableStateOf<Set<Int>>(emptySet()) };var index by remember(session.sessionId){mutableIntStateOf(0)}
    var saved by remember(session.sessionId){mutableStateOf<Set<Int>>(emptySet())}
    var changed by remember(session.sessionId){mutableStateOf<Map<Int,OcrCandidate>>(emptyMap())}
    var confirmations by remember(session.sessionId){mutableStateOf<Map<Int,Set<String>>>(emptyMap())}
    var closed by remember(session.sessionId){mutableStateOf<Set<Int>>(emptySet())}
    val keys=remember(session.sessionId){session.candidates.indices.associateWith { newKey() }}
    var draftIds by remember(session.sessionId){mutableStateOf<Map<Int,String>>(session.draftId?.let { mapOf(0 to it) } ?: emptyMap())}
    var keepImage by remember(session.sessionId){mutableStateOf(false)};var rawVisible by remember(session.sessionId){mutableStateOf(false)}
    var imageViewer by remember(session.sessionId){mutableStateOf(false)}
    var strategy by remember { mutableStateOf("trend") };var position by remember { mutableIntStateOf(10) };var emotion by remember { mutableStateOf("calm") }
    var duplicateDialog by remember(session.sessionId,index) { mutableStateOf<List<Trade>?>(null) };var approvedDuplicates by remember(session.sessionId,index) { mutableStateOf<Set<String>>(emptySet()) }
    var riskDialog by remember(session.sessionId,index) { mutableStateOf<RiskEvent?>(null) };var approvedRisk by remember(session.sessionId,index) { mutableStateOf<String?>(null) }
    var error by remember(session.sessionId,index) { mutableStateOf<String?>(null) }
    val candidate=session.candidates.getOrNull(index)?.let { changed[index] ?: it };val verified=confirmations[index].orEmpty()
    val valid=candidate?.let { index in selected && index !in saved && OcrTradeParser.canCreate(it,verified,index in closed) }==true
    val currentDraft=candidate?.let { OcrTradeParser.draft(it,strategy,position,keys.getValue(index)).copy(emotion=emotion) }
    val riskResult by produceState<OcrRiskPreview?>(null,currentDraft,state.riskRules,state.trades) {
        value=withContext(Dispatchers.Default) { currentDraft?.let { d->runCatching { val t=FastEntry.trade(d);OcrRiskPreview(d,state.riskRules,state.trades,RiskEngine.check(t,state.riskRules.firstOrNull { it.currency==t.currency },state.trades)) }.getOrNull() } }
    }
    val riskPreview=riskResult?.takeIf { it.draft==currentDraft && it.rules===state.riskRules && it.history===state.trades }
    fun resetApproval(){approvedDuplicates=emptySet();approvedRisk=null;error=null}
    fun update(key:String,value:String) { val c=candidate ?: return;changed=changed+(index to c.copy(fields=if(value.isBlank())c.fields-key else c.fields+(key to value),conflicts=c.conflicts-key));confirmations=confirmations+(index to (verified-key));closed=closed-index;resetApproval() }
    fun save() {
        val c=candidate ?: return
        val duplicates=OcrTradeParser.duplicates(c,state.trades)
        if(duplicates.any { it.recordKey !in approvedDuplicates }) { duplicateDialog=duplicates;return }
        val key=keys.getValue(index)
        val check=riskPreview?.event ?: run { error="请核对字段，或等待本地风险检查完成";return }
        if(check.violations.isNotEmpty() && approvedRisk!=RiskEngine.fingerprint(check)){riskDialog=check;return}
        val savingIndex=index
        model.saveOcr(c,verified,index in closed,strategy,position,key,session.copy(draftId=draftIds[index]),keepImage,approvedDuplicates,approvedRisk,emotion) { saved=saved+savingIndex;selected=selected-savingIndex;error=null;approvedDuplicates=emptySet();approvedRisk=null }
    }
    LocalFullScreenDialog({if(!busy){ocr.clear();onDismiss()}}) {
        val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
        Scaffold(modifier=Modifier.imePadding(),topBar={TopAppBar(title={Text("截图识别与人工确认")},navigationIcon={IconButton(enabled=!busy,onClick={ocr.clear();onDismiss()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"取消截图识别")}})},
            bottomBar={Surface(shadowElevation=4.dp) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                if(candidate!=null) {
                    Button(enabled=valid && riskPreview!=null && !busy && !session.busy,onClick={focus.clearFocus();keyboard?.hide();save()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("ocr-create")){Text(if(index in saved)"该候选已保存"else "确认并创建交易")}
                    TextButton(enabled=!busy && index !in saved,onClick={val id=draftIds[index] ?: newKey().also { draftIds=draftIds+(index to it) };model.ocrDraft(requireNotNull(candidate),id){};keyboard?.hide();focus.clearFocus()},modifier=Modifier.testTag("ocr-save-draft")){Text("保存待核实草稿")}
                }
            } } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("ocr-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { SectionCard("离线 OCR 辅助录入","识别字段可能错误；只有核实的已平仓成交才能保存，盈亏必须确认为最终净已实现值。") {
                    Button(enabled=!session.busy && !busy,onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))},modifier=Modifier.fillMaxWidth().testTag("ocr-pick")){Text("从系统文件选择截图")}
                    if(session.busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在本机识别，模型已随 APK 打包…",Modifier.testTag("ocr-working"))}
                    session.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("ocr-error")) }
                    feedback?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                    session.image?.let { LocalEvidenceImage(it,session.store,Modifier.fillMaxWidth().height(160.dp).clickable { imageViewer=true }.testTag("ocr-thumbnail"),512);Text("点击图片放大核对",style=MaterialTheme.typography.bodySmall) }
                } }
                if(session.candidates.isEmpty() && state.ocrDrafts.isNotEmpty())item { SectionCard("待核实 OCR 草稿","只存字段，不保存原始 OCR 文本；恢复后须重新逐项确认。") {
                    state.ocrDrafts.forEach { d->Row { TextButton(enabled=!busy,onClick={ocr.restore(d)},modifier=Modifier.testTag("ocr-restore-${d.id}")){Text("恢复 ${dayLabel(d.createdDay)} 的草稿")};TextButton(enabled=!busy,onClick={model.deleteOcrDraft(d)}){Text("删除草稿")} } }
                } }
                if(session.candidates.isNotEmpty())item { SectionCard("识别候选（${session.candidates.size} 条）","选择所需候选，逐条核实并创建；不能把委托或持仓自动当成交易。") {
                    session.candidates.forEachIndexed { i,c->Row { Checkbox(i in selected,{selected=if(it)selected+i else selected-i;index=i;resetApproval()},enabled=i !in saved && !busy,modifier=Modifier.testTag("ocr-select-$i"));TextButton(enabled=!busy,onClick={index=i;resetApproval()}){Text("第 ${i+1} 条 · ${c.fields["symbol"] ?: "品种待确认"}${if(i in saved) "（已保存）" else ""}")} } }
                } }
                if(candidate!=null) {
                    item { SectionCard("第 ${index+1} 条待确认字段") {
                        candidate.problems.forEach { Text(it,color=MaterialTheme.colorScheme.error) }
                        if(candidate.blocked || !candidate.closedEvidence)Text("该截图缺少可靠已平仓依据，仅允许保存草稿。补齐事实后可通过原快捷记账录入。",color=MaterialTheme.colorScheme.error)
                        if(valid && currentDraft!=null)FastEntry.error(currentDraft)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                        Text("逐项勾选代表已与真实成交核对；编辑后会清除对应确认。")
                    } }
                    items(OcrTradeParser.labels.entries.toList(),key={it.key}) { (key,label)->
                        val value=candidate.fields[key].orEmpty();val normalized=OcrTradeParser.normalize(key,value)
                        SectionCard(label) {
                            if(key in candidate.conflicts)Text("存在冲突，需人工核对",color=MaterialTheme.colorScheme.error)
                            if(value.isBlank())Text(if(key in OcrTradeParser.required)"待确认：缺少必要字段"else "未提供（可留空）",style=MaterialTheme.typography.bodySmall)
                            when(key) {
                                "market" -> SingleChoice(Presets.markets,value){if(!busy)update(key,it)}
                                "direction" -> SingleChoice(Presets.directions,value){if(!busy)update(key,it)}
                                "currency" -> SingleChoice(Presets.currencies,value){if(!busy)update(key,it)}
                                else -> OutlinedTextField(value,{if(it.length<=80)update(key,it)},enabled=!busy,label={Text(label)},singleLine=true,
                                    keyboardOptions=KeyboardOptions(keyboardType=if(key in setOf("entryPrice","exitPrice","buyPrice","sellPrice","quantity","fees","realizedPnl"))KeyboardType.Decimal else KeyboardType.Text,imeAction=ImeAction.Done),
                                    keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}),modifier=Modifier.fillMaxWidth().testTag("ocr-field-$key"))
                            }
                            if(value.isNotBlank())Row { Checkbox(key in verified,{checked->confirmations=confirmations+(index to if(checked)verified+key else verified-key);resetApproval()},enabled=normalized==value && !busy,modifier=Modifier.testTag("ocr-verify-$key"));Text(if(normalized==value)"已核实该字段"else "格式需修正，尚不能确认") }
                            if(normalized!=null && normalized!=value)TextButton(enabled=!busy,onClick={update(key,normalized)}){Text("使用规范格式：$normalized")}
                        }
                    }
                    item { SectionCard("最终成交核实") {
                        Text("策略、情绪、仓位为手动选项，请核对默认值；不来自 OCR。")
                        SingleChoice(LocalCatalog.current.options("strategy",listOf(strategy)),strategy){if(!busy){strategy=it;resetApproval()}}
                        SingleChoice(LocalCatalog.current.options("emotion",listOf(emotion)),emotion){if(!busy){emotion=it;resetApproval()}}
                        Text("实际仓位比例（不是风险比例）")
                        SingleChoice(listOf(5,10,20,30,50,100).map { Option(it.toString(),"$it%") },position.toString()){if(!busy){position=it.toInt();resetApproval()}}
                        Row { Checkbox(index in closed,{closed=if(it)closed+index else closed-index;resetApproval()},enabled=!candidate.blocked && candidate.closedEvidence && !busy,modifier=Modifier.testTag("ocr-confirm-closed"));Text("已核实为完整已平仓成交，盈亏是最终净已实现金额；未把委托、浮盈或毛盈亏当作净盈亏。",Modifier.weight(1f)) }
                        if(session.image!=null)Row { Checkbox(keepImage,{keepImage=it},enabled=!busy,modifier=Modifier.testTag("ocr-keep-image"));Text("保留当前图片作为交易证据（压缩副本）") }
                        if("buyPrice" in candidate.fields || "sellPrice" in candidate.fields)Text("买卖价仅用于核对，未填写入场/出场价时不会自动存成这些专业字段。")
                    } }
                }
                if(session.raw.isNotBlank())item { SectionCard("原始识别文本（仅本次会话）") { TextButton(onClick={rawVisible=!rawVisible}){Text(if(rawVisible)"收起识别文本"else "展开识别文本")};if(rawVisible)Text(session.raw) } }
            } }
    }
    if(imageViewer)session.image?.let { EvidenceViewer(listOf(it),0,session.store){imageViewer=false} }
    duplicateDialog?.let { rows->AlertDialog(onDismissRequest={duplicateDialog=null},title={Text("发现疑似重复交易")},text={Column {
        Text("同日期、市场、品种、方向、币种及净盈亏的 ${rows.size} 条记录；请查看后决定是否仍新增，不自动合并。")
        rows.take(5).forEach { TradeCard(it){} };if(rows.size>5)Text("其余 ${rows.size-5} 条请在交易记录中核对。")
    }},confirmButton={TextButton(onClick={approvedDuplicates=rows.map { it.recordKey }.toSet();duplicateDialog=null;save()}){Text("已查看，仍新增此真实交易")}},dismissButton={TextButton(onClick={duplicateDialog=null}){Text("返回核对")}}) }
    riskDialog?.let { event->AlertDialog(onDismissRequest={riskDialog=null},title={Text("风险纪律提醒")},text={Text(event.violations.joinToString("\n"){RiskEngine.labels[it] ?: it})},
        confirmButton={TextButton(onClick={approvedRisk=RiskEngine.fingerprint(event);riskDialog=null;save()}){Text("确认真实记录，仍保存")}},dismissButton={TextButton(onClick={riskDialog=null}){Text("返回核对")}}) }
}
