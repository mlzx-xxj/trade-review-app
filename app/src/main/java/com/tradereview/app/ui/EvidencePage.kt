package com.tradereview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.tradereview.app.data.*
import com.tradereview.app.evidence.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LocalFullScreenDialog(onDismiss: ()->Unit,content: @Composable ()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply {
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,android.view.WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } }
        Surface(Modifier.fillMaxSize()) { content() }
    }
}
private data class EvidenceImage(val bitmap: android.graphics.Bitmap?,val error: String?)
@Composable
fun LocalEvidenceImage(attachment: Attachment,store: AttachmentStore?,modifier: Modifier,edge: Int = 256) {
    val result by produceState<EvidenceImage?>(null,attachment.id,attachment.sha256,store,edge) {
        value=withContext(Dispatchers.IO) {
            try { EvidenceImage(requireNotNull(store).bitmap(attachment,edge),null) }
            catch(_: Exception) { EvidenceImage(null,"文件缺失或图片损坏") }
            catch(_: OutOfMemoryError) { EvidenceImage(null,"图片内存不足") }
        }
    }
    val bitmap=result?.bitmap
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Box(modifier,contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap.asImageBitmap(),attachment.caption.ifBlank { Presets.label(EvidenceOptions.kinds,attachment.kind) },Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else if(result==null) CircularProgressIndicator(Modifier.size(24.dp))
        else Text(result!!.error!!,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
}
@Composable
fun EvidenceThumbnails(values: List<Attachment>,store: AttachmentStore?,onClick: ()->Unit) {
    if(values.isEmpty())Text("暂无截图，可添加入场前、持仓中、出场后与复盘证据。")
    else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(values,key={it.id}) { a -> Column(Modifier.width(100.dp).clickable(onClick=onClick)) {
            LocalEvidenceImage(a,store,Modifier.size(100.dp).testTag("evidence-thumb-${a.id}"))
            Text(Presets.label(EvidenceOptions.kinds,a.kind),style=MaterialTheme.typography.labelSmall)
        } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidencePage(key: String,state: JournalState,model: TradeViewModel,busy: Boolean,onDismiss: ()->Unit) {
    val rows=state.attachments.filter { it.recordKey==key }
    val context=LocalContext.current
    val feedback by model.message.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("analysis") }
    var caption by rememberSaveable { mutableStateOf("") }
    var viewer by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Attachment?>(null) }
    var editing by remember { mutableStateOf<Attachment?>(null) }
    var updated by remember(editing) { mutableStateOf(editing?.caption.orEmpty()) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.importAttachment(context.contentResolver,it,key,kind,caption) }
    }
    LocalFullScreenDialog(onDismiss) {
        Scaffold(modifier=Modifier.imePadding(),topBar={ TopAppBar(title={Text("截图与证据")},navigationIcon={IconButton(enabled=!busy,onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回交易详情")}}) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("evidence-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { SectionCard("本笔截图 ${rows.size} / 10", "图片复制到本机；不依赖外部 URI、不上传、不保留照片位置。") {
                    SingleChoice(EvidenceOptions.kinds,kind) { if(!busy)kind=it }
                    OutlinedTextField(caption,{if(it.length<=200)caption=it},enabled=!busy,label={Text("简短截图说明（可选）")},modifier=Modifier.fillMaxWidth().testTag("image-caption"),maxLines=3)
                    Button(enabled=!busy && rows.size<10 && state.trades.any { it.recordKey==key },onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))},modifier=Modifier.fillMaxWidth().testTag("pick-evidence")) { Text("从系统文件 / 照片选择截图") }
                    Text("原图最多 ${state.evidenceSettings.maxInputMiB} MiB / 4000 万像素，压缩到最长边 2048、单张最多 4 MiB。透明背景转白色；动图只保存首帧。",style=MaterialTheme.typography.bodySmall)
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    feedback?.let { Text(it,color=if(it.startsWith("保存失败"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("evidence-feedback")) }
                } }
                items(rows,key={it.id}) { a -> SectionCard(Presets.label(EvidenceOptions.kinds,a.kind)) {
                    LocalEvidenceImage(a,model.evidenceStore,Modifier.fillMaxWidth().height(180.dp).clickable { viewer=a.id }.testTag("evidence-image-${a.id}"),512)
                    Text(a.caption.ifBlank { "未填写说明" });Text("${a.width} × ${a.height} · ${a.byteCount/1024} KiB",style=MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick={viewer=a.id},modifier=Modifier.testTag("view-evidence-${a.id}")){Text("查看大图")}
                        TextButton(enabled=!busy,onClick={editing=a}){Text("修改说明")}
                        TextButton(enabled=!busy,onClick={deleting=a},modifier=Modifier.testTag("delete-evidence-${a.id}")){Text("删除附件")} }
                } }
            }
        }
    }
    viewer?.let { id -> if(rows.isEmpty())LaunchedEffect(rows){viewer=null} else EvidenceViewer(rows,rows.indexOfFirst { it.id==id }.coerceAtLeast(0),model.evidenceStore){viewer=null} }
    deleting?.let { a -> AlertDialog(onDismissRequest={if(!busy)deleting=null},title={Text("删除这张截图？")},text={Text("仅删除图片附件，不改变交易记录。删除后无法在应用内撤销。")},
        confirmButton={TextButton(enabled=!busy,onClick={deleting=null;model.deleteAttachment(a)}){Text("确认删除附件")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}) }
    editing?.let { a -> AlertDialog(onDismissRequest={editing=null},title={Text("修改截图说明")},text={OutlinedTextField(updated,{if(it.length<=200)updated=it},label={Text("说明（最多 200 字）")})},
        confirmButton={TextButton(enabled=!busy,onClick={editing=null;model.updateCaption(a,updated)}){Text("保存说明")}},dismissButton={TextButton(onClick={editing=null}){Text("取消")}}) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceViewer(values: List<Attachment>,initial: Int,store: AttachmentStore?,onDismiss: ()->Unit) {
    val pager=rememberPagerState(initialPage=initial,pageCount={values.size});val scope=rememberCoroutineScope()
    LocalFullScreenDialog(onDismiss) { Scaffold(topBar={TopAppBar(title={Text("截图大图 ${pager.currentPage+1} / ${values.size}",modifier=Modifier.testTag("evidence-viewer-count"))},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"关闭大图")}}) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth().weight(1f)) { index -> LocalEvidenceImage(values[index],store,Modifier.fillMaxSize(),2048) }
            val a=values[pager.currentPage.coerceAtMost(values.lastIndex)]
            Text("${Presets.label(EvidenceOptions.kinds,a.kind)} · ${a.caption.ifBlank { "未填写说明" }}")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(enabled=pager.currentPage>0,onClick={scope.launch { pager.scrollToPage(pager.currentPage-1) }}){Text("上一张")}
                OutlinedButton(enabled=pager.currentPage<values.lastIndex,onClick={scope.launch { pager.scrollToPage(pager.currentPage+1) }}){Text("下一张")}
            };Text("可左右滑动切换；图片仅从本机私有目录读取。",style=MaterialTheme.typography.bodySmall)
        }
    } }
}
