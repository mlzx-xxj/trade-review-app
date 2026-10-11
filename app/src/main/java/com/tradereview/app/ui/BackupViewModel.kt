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

class BackupViewModel(database: TradeDatabase, private val resolver: ContentResolver) : ViewModel() {
    private val repository = BackupRepository(database)
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
        viewModelScope.launch {
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
        _message.value = "JSON 完整备份已保存到你选择的位置"
    }
    fun exportCsv(uri: Uri) = operation("CSV 导出失败，请检查所选位置后重新导出") {
        write(uri, TradeCsv.encode(repository.export().trades))
        _message.value = "交易明细 CSV 已保存（UTF-8 中文）"
    }
    private fun write(uri: Uri, text: String) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("无法打开目标文件")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
    fun readBackup(uri: Uri) {
        if (_busy.value) return
        _preview.value = null
        operation("无法读取备份文件，请检查文件权限；未恢复任何数据") {
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("无法打开备份")
            val data = input.use { BackupCodec.read(it) }
            _preview.value = repository.preview(data)
        }
    }
    fun refreshPreview() {
        val data = _preview.value?.backup ?: return
        operation("重新预览失败，未恢复任何数据") { _preview.value = repository.preview(data) }
    }
    fun cancelPreview() { if (!_busy.value) { _preview.value = null; _message.value = null } }
    // Called only from the second confirmation dialog, never from file selection or preview.
    fun confirmRestore(mode: RestoreMode) {
        val pending = _preview.value ?: return
        operation("恢复失败，事务已回滚；原有数据未改变") {
            repository.restore(pending, mode)
            _preview.value = null
            _message.value = if (mode == RestoreMode.REPLACE) "覆盖恢复完成" else "去重合并完成，冲突内容保留本地版本"
        }
    }
}
