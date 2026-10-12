package com.tradereview.app.evidence

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.tradereview.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AttachmentRepository(private val db: TradeDatabase, private val store: AttachmentStore) {
    private val dao get()=db.trades()
    suspend fun importImage(resolver: ContentResolver,uri: Uri,key: String,kind: String,caption: String) = withContext(Dispatchers.IO) {
        EvidenceLock.mutex.withLock {
            try {
                require(dao.attachmentCount()<EvidenceLimits.MAX_IMAGES) { "图片总数已达 10000 张上限，请先完整备份并整理" }
                val rows=dao.attachmentsForTrade(key)
                require(rows.size<EvidenceLimits.MAX_PER_TRADE) { "每笔交易最多 10 张截图" }
                require(dao.findTradeByKey(key)!=null) { "请先保存交易，再添加截图" }
                val a=store.importImage(resolver,uri,key,kind,caption,dao.evidenceSettings() ?: EvidenceSettings(),(rows.maxOfOrNull { it.sortOrder } ?: -1)+1)
                db.withTransaction {
                    require(dao.findTradeByKey(key)!=null && dao.attachmentCountForTrade(key)<10) { "交易已变化，请重新打开" }
                    dao.saveAttachment(a)
                };a
            } finally { withContext(NonCancellable) { store.cleanup(dao.allAttachments()) } }
        }
    }
    suspend fun delete(value: Attachment) = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        db.withTransaction { require(dao.findAttachment(value.id)==value) { "附件已变化，请重新打开" };dao.deleteAttachment(value) }
        withContext(NonCancellable) { store.cleanup(dao.allAttachments()) }
    } }
    suspend fun caption(value: Attachment,caption: String) = EvidenceLock.mutex.withLock { db.withTransaction {
        val updated=value.copy(caption=caption.trim());EvidenceLimits.validate(updated)
        require(dao.findAttachment(value.id)==value) { "附件已变化，请重新打开" };dao.saveAttachment(updated)
    } }
    suspend fun deleteTrade(value: Trade) = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        EntryRepository(db).delete(value)
        withContext(NonCancellable) { store.cleanup(dao.allAttachments()) }
    } }
    suspend fun startupCleanup() = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock { store.cleanup(dao.allAttachments(),clearWork=true) } }
}
