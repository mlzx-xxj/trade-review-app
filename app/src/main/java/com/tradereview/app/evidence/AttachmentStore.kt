package com.tradereview.app.evidence

import android.graphics.*
import android.media.ExifInterface
import android.content.ContentResolver
import android.net.Uri
import com.tradereview.app.data.*
import kotlinx.coroutines.sync.Mutex
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Serializes publication/cleanup with exports/restores. Financial edits remain Room transactions. */
object EvidenceLock { val mutex = Mutex() }

class AttachmentStore(val root: File, private val availableBytes: () -> Long = { root.usableSpace }) {
    init { require(root.exists() || root.mkdirs()) { "无法创建私有图片目录" } }
    val work get() = File(root,".work").apply { require(exists() || mkdirs()) }
    private val imageCacheDelegate=lazy { AttachmentImageCache(this) }
    val imageCache by imageCacheDelegate
    fun clearImageCache(){if(imageCacheDelegate.isInitialized())imageCacheDelegate.value.clear()}
    @Volatile var cleanupPending = false
        private set
    fun ensureSpace(bytes: Long) {
        require(bytes >= 0 && availableBytes() >= bytes + 8L*1024*1024) { "手机存储空间不足，未修改记录；请释放空间后重试" }
    }
    fun file(a: Attachment): File { EvidenceLimits.validate(a); return File(root,attachmentFileName(a)) }
    fun verify(file: File, a: Attachment, decode: Boolean = true) {
        EvidenceLimits.validate(a)
        require(file.isFile && file.canRead()) { "附件文件缺失或不可读取，请从完整 ZIP 备份恢复" }
        require(file.length() == a.byteCount && sha256(file) == a.sha256) { "附件图片损坏或摘要不匹配" }
        if(decode) {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outMimeType == "image/jpeg" && bounds.outWidth == a.width && bounds.outHeight == a.height) { "附件图片类型或尺寸不正确" }
            val image=BitmapFactory.decodeFile(file.path) ?: throw EvidenceException("图片内容损坏，无法解码")
            image.recycle()
            val exif=ExifInterface(file.path)
            require(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)==null && exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)==null) { "图片包含不需要的位置元数据，拒绝恢复" }
        }
    }
    fun verifyAll(values: List<Attachment>) { values.forEach { verify(file(it),it) } }
    fun publish(source: File, a: Attachment) {
        verify(source,a)
        val target=file(a)
        if(target.exists()) { verify(target,a);return }
        ensureSpace(a.byteCount)
        val temp=File(work,"publish-${newKey()}.tmp")
        try {
            FileOutputStream(temp).use { out -> source.inputStream().use { copyBounded(it,out,EvidenceLimits.MAX_IMAGE_BYTES) };out.fd.sync() }
            verify(temp,a)
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
            // O_RDONLY can open a known directory; O_DIRECTORY is not in the public Android SDK.
            val descriptor=android.system.Os.open(root.path,android.system.OsConstants.O_RDONLY,0)
            try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
        } finally { temp.delete() }
    }
    /** Immutable blobs are published before rows. On failure only orphan blobs are removed. */
    fun cleanup(referenced: List<Attachment>, clearWork: Boolean = false) {
        val names=referenced.map(::attachmentFileName).toSet();var pending=false
        val files=root.listFiles();if(files==null)pending=true
        files?.filter { it.isFile && it.name !in names }?.forEach { if(!it.delete())pending=true }
        if(clearWork) work.listFiles()?.forEach { if(!it.deleteRecursively())pending=true }
        cleanupPending=pending
    }
    fun importImage(resolver: ContentResolver, uri: Uri, key: String, kind: String, caption: String, settings: EvidenceSettings, order: Int): Attachment {
        EvidenceLimits.settings(settings)
        val maximum=settings.maxInputMiB.toLong()*1024*1024
        ensureSpace(maximum+EvidenceLimits.MAX_IMAGE_BYTES)
        val input=File(work,"source-${newKey()}.tmp");val output=File(work,"image-${newKey()}.jpg")
        try {
            (resolver.openInputStream(uri) ?: throw EvidenceException("无法读取所选图片；文件权限可能已失效")).use { src -> FileOutputStream(input).use { out -> copyBounded(src,out,maximum);out.fd.sync() } }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(input.path,bounds)
            require(bounds.outMimeType in listOf("image/jpeg","image/png","image/webp") && bounds.outWidth>0 && bounds.outHeight>0) { "请选择有效 JPEG、PNG 或 WebP 图片" }
            require(bounds.outWidth.toLong()*bounds.outHeight.toLong() <= 40_000_000) { "原图超过 4000 万像素，请先缩小" }
            var sample=1;while((maxOf(bounds.outWidth,bounds.outHeight)+sample-1)/sample > EvidenceLimits.MAX_EDGE)sample*=2
            val decoded=BitmapFactory.decodeFile(input.path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: throw EvidenceException("图片内容损坏，无法读取")
            val matrix=Matrix()
            if(bounds.outMimeType=="image/jpeg") {
                when(ExifInterface(input.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f);matrix.postScale(-1f,1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f);matrix.postScale(-1f,1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                }
            }
            var rotated: Bitmap?=null;var flattened: Bitmap?=null
            try {
                val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);rotated=oriented
                val flat=Bitmap.createBitmap(oriented.width,oriented.height,Bitmap.Config.ARGB_8888);flattened=flat
                Canvas(flat).apply { drawColor(Color.WHITE);drawBitmap(oriented,0f,0f,null) }
                FileOutputStream(output).use { out -> require(flat.compress(Bitmap.CompressFormat.JPEG,85,out)) { "图片压缩失败" };out.fd.sync() }
                require(output.length() in 1..EvidenceLimits.MAX_IMAGE_BYTES) { "压缩后的图片超过 4 MiB，请选择较小图片" }
                val value=Attachment(recordKey=key,kind=kind,caption=caption.trim(),sha256=sha256(output),byteCount=output.length(),width=flat.width,height=flat.height,sortOrder=order)
                publish(output,value);return value
            } finally { flattened?.recycle();if(rotated!==decoded)rotated?.recycle();decoded.recycle() }
        } catch(e: SecurityException) { throw EvidenceException("所选图片权限失效，请重新选择；未添加附件") }
        catch(e: IOException) { throw EvidenceException("图片读取或写入失败，请检查文件与存储空间；未添加附件") }
        catch(e: OutOfMemoryError) { throw EvidenceException("图片解码内存不足，请选择较小图片；未添加附件") }
        finally { input.delete();output.delete() }
    }
    fun bitmap(a: Attachment, edge: Int): Bitmap {
        require(edge in 64..EvidenceLimits.MAX_EDGE)
        val source=file(a);verify(source,a,decode=false)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(source.path,bounds)
        require(bounds.outMimeType=="image/jpeg" && bounds.outWidth==a.width && bounds.outHeight==a.height) { "图片尺寸或类型与记录不一致" }
        var sample=1;while(maxOf(a.width,a.height)/sample>edge)sample*=2
        return BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: throw EvidenceException("图片损坏，无法显示")
    }
}
