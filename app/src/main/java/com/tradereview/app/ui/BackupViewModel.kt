package com.tradereview.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradereview.app.backup.*
import com.tradereview.app.data.TradeDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tradereview.app.evidence.AttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private object BackupCleanup { val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO) }

class BackupViewModel(database: TradeDatabase, private val resolver: ContentResolver, store: AttachmentStore? = null) : ViewModel() {
    private val repository = BackupRepository(database,store)
    private val zip = store?.let { FullZipRepository(database,it) }
    private var pendingZip: FullZipPreview? = null
    private var operationJob:kotlinx.coroutines.Job?=null
    private val _preview = MutableStateFlow<RestorePreview?>(null)
    val preview = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private fun operation(failure: String, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        operationJob=viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = if (e is BackupException) e.message else failure
            } finally { _busy.value = false }
        }
    }

    fun exportJson(uri: Uri) = operation("导出失败，请检查存储空间与所选位置；不完整文件需重新导出") {
        val text = BackupCodec.encode(repository.export())
        write(uri, text)
        _message.value = "结构化 JSON 已保存；不含截图文件，含图片的完整备份请使用 ZIP"
    }
    fun exportZip(uri: Uri) = operation("完整 ZIP 导出失败，图片可能缺失、损坏或空间不足；请重新导出") {
        val service=requireNotNull(zip) { "私有图片存储未初始化" }
        val output=resolver.openOutputStream(uri,"wt") ?: throw java.io.IOException("无法打开目标文件")
        output.use { service.export(it) }
        _message.value="完整 ZIP 已保存，包含结构化数据、全部截图和逐文件校验清单"
    }
    fun exportCsv(uri: Uri) = operation("CSV 导出失败，请检查所选位置后重新导出") {
        write(uri, repository.export().let { TradeCsv.encode(it.trades, com.tradereview.app.data.Catalog(it.tags)) })
        _message.value = "交易明细 CSV 已保存（UTF-8 中文）"
    }
    private fun write(uri: Uri, text: String) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("无法打开目标文件")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
    fun readBackup(uri: Uri) {
        if (_busy.value) return
        val old=pendingZip;pendingZip=null
        _preview.value = null
        operation("无法读取备份文件，请检查文件权限；未恢复任何数据") {
            old?.archive?.close()
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("无法打开备份")
            input.buffered().use { stream ->
                stream.mark(4);val first=stream.read();val second=stream.read();stream.reset()
                if(first==80 && second==75) {
                    val parsed=requireNotNull(zip) { "请在完整应用中恢复 ZIP" }.read(stream)
                    pendingZip=parsed;_preview.value=parsed.preview
                } else {
                    val data=BackupCodec.read(stream)
                    if(data.backupScope!="structured-data-only")throw BackupException("这份 JSON 来自完整 ZIP，必须选择原 ZIP 一起恢复图片")
                    _preview.value=repository.preview(data)
                }
            }
        }
    }
    fun refreshPreview() {
        val data = _preview.value?.backup ?: return
        operation("重新预览失败，未恢复任何数据") {
            val full=pendingZip
            if(full!=null) { val refreshed=requireNotNull(zip).refresh(full);pendingZip=refreshed;_preview.value=refreshed.preview }
            else _preview.value = repository.preview(data)
        }
    }
    fun cancelPreview() { if (!_busy.value) { val old=pendingZip;pendingZip=null;_preview.value = null; _message.value = null;old?.let { BackupCleanup.scope.launch { it.archive.close() } } } }
    // Called only from the second confirmation dialog, never from file selection or preview.
    fun confirmRestore(mode: RestoreMode) {
        val pending = _preview.value ?: return
        operation("恢复失败，事务已回滚；原有数据未改变") {
            val full=pendingZip
            if(full!=null)requireNotNull(zip).restore(full,mode)else repository.restore(pending, mode)
            full?.archive?.let { runCatching { it.close() } };pendingZip=null
            _preview.value = null
            _message.value = if (mode == RestoreMode.REPLACE) "覆盖恢复完成" else "去重合并完成，冲突内容保留本地版本"
        }
    }
    override fun onCleared() {
        val active=operationJob
        active?.cancel()
        // Do not delete a staged archive while an in-flight IO/rollback is still using it.
        BackupCleanup.scope.launch {active?.join();com.tradereview.app.evidence.EvidenceLock.mutex.lock()
            try{pendingZip?.archive?.close();pendingZip=null}finally{com.tradereview.app.evidence.EvidenceLock.mutex.unlock()}}
        super.onCleared()
    }
}
