package com.tradereview.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ocr.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import java.io.File

data class OcrSession(val candidates:List<OcrCandidate> = emptyList(),val raw:String="",val image:Attachment?=null,
    val store:AttachmentStore?=null,val busy:Boolean=false,val error:String?=null,val draftId:String?=null)
class OcrViewModel(private val context:Context,private val recognizer:OfflineRecognizer=BundledOcr()):ViewModel() {
    val state=MutableStateFlow(OcrSession())
    private val sessionRoot=File(context.cacheDir,"ocr-${newKey()}")
    private var job:Job?=null;private var generation=0
    fun read(uri:Uri,settings:EvidenceSettings,source:Attachment?=null,sourceStore:AttachmentStore?=null) {
        val old=state.value.store;val g=++generation;job?.cancel();state.value=OcrSession(busy=true)
        job=viewModelScope.launch {
            var store:AttachmentStore?=null
            try {
                withContext(Dispatchers.IO) { old?.root?.deleteRecursively() }
                val image=withContext(Dispatchers.IO) {
                    store=AttachmentStore(File(sessionRoot,newKey()))
                    if(source!=null) EvidenceLock.mutex.withLock {
                        val origin=requireNotNull(sourceStore);origin.verify(origin.file(source),source)
                        val copy=source.copy(id=newKey(),recordKey=newKey());store!!.publish(origin.file(source),copy);copy
                    } else store!!.importImage(context.contentResolver,uri,newKey(),"analysis","OCR 临时图",settings,0)
                }
                val bitmap=withContext(Dispatchers.IO){store!!.bitmap(image,2048)}
                val raw=try { withContext(Dispatchers.Default){recognizer.recognize(bitmap)} }finally { bitmap.recycle() }
                val parsed=withContext(Dispatchers.Default){OcrTradeParser.parse(raw)}
                if(g==generation)state.value=OcrSession(parsed,raw,image,store,error=if(raw.isBlank())"未识别到清晰文字，请选择成交明细截图"else null)
                else withContext(Dispatchers.IO){store!!.root.deleteRecursively()}
            } catch(e:Exception) {
                withContext(NonCancellable+Dispatchers.IO){store?.root?.deleteRecursively()}
                if(e is CancellationException)throw e
                if(g==generation)state.value=OcrSession(error="识别失败：${if(e is IllegalArgumentException)e.message else "图片损坏、权限失效或 OCR 无法处理；历史交易未修改"}")
            } catch(_:OutOfMemoryError) { if(g==generation)state.value=OcrSession(error="图片内存不足，请选择较小截图");withContext(NonCancellable+Dispatchers.IO){store?.root?.deleteRecursively()} }
        }
    }
    fun restore(draft:OcrDraft) { clear();state.value=OcrSession(candidates=listOf(OcrCandidate(SmartJson.fields(draft.fields),blocked=draft.blocked,closedEvidence=draft.closedEvidence)),draftId=draft.id) }
    fun clear() { val old=state.value.store;generation++;job?.cancel();state.value=OcrSession();viewModelScope.launch(Dispatchers.IO) { old?.root?.deleteRecursively() } }
    // Private cache is transient; cancel never leaves raw text in Room or backups.
    override fun onCleared() { job?.cancel();java.util.concurrent.Executors.newSingleThreadExecutor().let { executor->executor.execute { sessionRoot.deleteRecursively() };executor.shutdown() } }
}
