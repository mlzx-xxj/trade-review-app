package com.tradereview.app.backup

import com.tradereview.app.data.*
import com.tradereview.app.evidence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*

data class FullZipPreview(val archive: ParsedZip, val preview: RestorePreview)
class FullZipRepository(private val db: TradeDatabase,private val store: AttachmentStore) {
    private val data=BackupRepository(db,store)
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        val target=File(store.work,"export-${newKey()}.zip")
        try {
            store.ensureSpace(1024*1024)
            val snapshot=data.export(full=true);store.verifyAll(snapshot.attachments)
            FullZipArchive.write(snapshot,store::file,target)
            target.inputStream().use { copyBounded(it,output,FullZipArchive.MAX_ZIP_BYTES) };output.flush()
        } finally { target.delete() }
    } }
    suspend fun read(input: InputStream): FullZipPreview {
        var owned:ParsedZip?=null
        try{return withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        store.ensureSpace(1024*1024)
        val archive=FullZipArchive.read(input,store.work)
        owned=archive
        try { archive.data.attachments.forEach { store.verify(archive.image(it),it) };FullZipPreview(archive,data.preview(archive.data)) }
        catch(e: Exception) { archive.close();throw e }
        } }}catch(e:Exception){withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO){owned?.close()};throw e}
    }
    suspend fun refresh(old: FullZipPreview) = old.copy(preview=data.preview(old.archive.data))
    suspend fun restore(pending: FullZipPreview,mode: RestoreMode) = withContext(Dispatchers.IO) { EvidenceLock.mutex.withLock {
        try {
            // Validate again after preview; publish immutable, synced files before the Room commit.
            pending.archive.data.attachments.forEach { store.verify(pending.archive.image(it),it) }
            store.ensureSpace(pending.archive.data.attachments.filter { !store.file(it).exists() }.sumOf { it.byteCount })
            pending.archive.data.attachments.forEach { store.publish(pending.archive.image(it),it) }
            data.restoreLocked(pending.preview,mode)
        } finally { data.cleanupFiles() }
    } }
}
