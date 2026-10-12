package com.tradereview.app.backup

import com.google.gson.*
import com.tradereview.app.data.*
import com.tradereview.app.evidence.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.*
import java.security.MessageDigest
import java.security.DigestInputStream

data class ZipItem(val path: String, val bytes: Long, val sha256: String)
class ParsedZip(val directory: File, val data: BackupData) : Closeable {
    fun image(a: Attachment) = File(directory,"images/${attachmentFileName(a)}")
    override fun close() { directory.deleteRecursively() }
}
/** Strict, streaming and bounded ZIP parsing. Never extracts an arbitrary entry name. */
object FullZipArchive {
    const val MAX_ZIP_BYTES = 256L*1024*1024
    const val MAX_EXPANDED_BYTES = 512L*1024*1024
    const val MAX_MANIFEST_BYTES = 4L*1024*1024
    const val MAX_FILES = EvidenceLimits.MAX_IMAGES+2
    private val imagePath=Regex("images/[0-9a-f]{32}-[0-9a-f]{64}\\.jpg")
    private fun allowed(path: String) = path=="data.json" || path=="manifest.json" || imagePath.matches(path)
    private fun limit(path: String) = when(path) { "data.json" -> BackupCodec.MAX_BYTES.toLong();"manifest.json" -> MAX_MANIFEST_BYTES;else -> EvidenceLimits.MAX_IMAGE_BYTES }
    private fun utf8(file: File): String = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.readBytes())).toString()
    fun write(data: BackupData, image: (Attachment)->File, target: File) {
        require(data.backupScope=="with-attachment-files") { "ZIP 必须包含完整截图范围" }
        val bytes=BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        val items=mutableListOf(ZipItem("data.json",bytes.size.toLong(),sha256(ByteArrayInputStream(bytes))))
        // Validate every file before creating an archive. Missing images never silently disappear.
        data.attachments.forEach { a -> val f=image(a);require(f.isFile && f.canRead() && f.length()==a.byteCount && sha256(f)==a.sha256) { "截图缺失或损坏，无法生成完整备份" }
            items+=ZipItem("images/${attachmentFileName(a)}",a.byteCount,a.sha256) }
        require(items.sumOf { it.bytes }<=MAX_EXPANDED_BYTES-MAX_MANIFEST_BYTES) { "完整备份解压数据超过 512 MiB 上限" }
        val manifest=JsonObject().apply {
            addProperty("format","trade-review-full-backup");addProperty("zipVersion",1);addProperty("exportedAt",data.exportedAt)
            fun item(i: ZipItem)=JsonObject().apply { addProperty("path",i.path);addProperty("bytes",i.bytes);addProperty("sha256",i.sha256) }
            add("data",item(items.first()));add("images",JsonArray().apply { items.drop(1).forEach { add(item(it)) } })
        }.toString().toByteArray(Charsets.UTF_8)
        require(manifest.size<=MAX_MANIFEST_BYTES)
        val bounded=object: FilterOutputStream(FileOutputStream(target)) {
            var size=0L
            override fun write(b: Int) { size++;require(size<=MAX_ZIP_BYTES) { "ZIP 超过 256 MiB 上限" };out.write(b) }
            override fun write(b: ByteArray,off: Int,len: Int) { size+=len;require(size<=MAX_ZIP_BYTES) { "ZIP 超过 256 MiB 上限" };out.write(b,off,len) }
        }
        try { ZipOutputStream(bounded).use { zip ->
            fun entry(name: String,input: InputStream,max: Long,expectedBytes: Long,expectedHash: String) {
                zip.putNextEntry(ZipEntry(name));val digest=MessageDigest.getInstance("SHA-256")
                val count=DigestInputStream(input,digest).use { copyBounded(it,zip,max) }
                require(count==expectedBytes && digest.digest().joinToString("") { "%02x".format(it) }==expectedHash) { "备份期间文件变化，已停止导出" };zip.closeEntry()
            }
            entry("data.json",ByteArrayInputStream(bytes),BackupCodec.MAX_BYTES.toLong(),items.first().bytes,items.first().sha256)
            data.attachments.forEach { entry("images/${attachmentFileName(it)}",image(it).inputStream(),EvidenceLimits.MAX_IMAGE_BYTES,it.byteCount,it.sha256) }
            entry("manifest.json",ByteArrayInputStream(manifest),MAX_MANIFEST_BYTES,manifest.size.toLong(),sha256(ByteArrayInputStream(manifest)))
        } } catch(e: Exception) { target.delete();throw e }
    }
    fun read(input: InputStream, temporaryRoot: File): ParsedZip {
        val dir=File(temporaryRoot,"zip-${newKey()}");require(dir.mkdirs()) { "无法创建备份校验临时目录" }
        val archive=File(dir,"archive.zip")
        try {
            FileOutputStream(archive).use { out -> copyBounded(input,out,MAX_ZIP_BYTES);out.fd.sync() }
            val central=linkedMapOf<String,Long>()
            ZipFile(archive).use { zip -> zip.entries().asSequence().forEach { e ->
                require(!e.isDirectory && allowed(e.name) && e.method in listOf(ZipEntry.STORED,ZipEntry.DEFLATED)) { "ZIP 包含非法路径或类型" }
                require(central.put(e.name,e.size)==null && central.size<=MAX_FILES) { "ZIP 含重复文件名或文件过多" }
                require(e.size in 0..limit(e.name) && e.compressedSize in 0..MAX_ZIP_BYTES) { "ZIP 单文件超过上限或长度无效" }
            } }
            require(central.values.sum()<=MAX_EXPANDED_BYTES) { "ZIP 解压声明总量超过 512 MiB 上限" }
            require(central.keys.containsAll(listOf("data.json","manifest.json"))) { "ZIP 缺少数据或校验清单" }
            val seen=mutableSetOf<String>();var total=0L
            ZipInputStream(archive.inputStream()).use { zip -> while(true) {
                val entry=zip.nextEntry ?: break
                require(allowed(entry.name) && entry.name in central && seen.add(entry.name) && !entry.isDirectory) { "ZIP 路径、目录或重复本地条目无效" }
                val destination=if(entry.name.startsWith("images/")) File(File(dir,"images").apply { mkdirs() },entry.name.removePrefix("images/")) else File(dir,entry.name)
                val count=FileOutputStream(destination).use { out -> copyBounded(zip,out,minOf(limit(entry.name),MAX_EXPANDED_BYTES-total));out.fd.sync();destination.length() }
                total+=count;require(count==central.getValue(entry.name) && total<=MAX_EXPANDED_BYTES) { "ZIP 长度不一致或解压总量超过上限" };zip.closeEntry()
            } }
            require(seen==central.keys) { "ZIP 中央目录与实际文件不一致" }
            val root=BackupCodec.strictObject(utf8(File(dir,"manifest.json")))
            require(root.keySet()==setOf("format","zipVersion","exportedAt","data","images")) { "校验清单字段无效" }
            fun text(o: JsonObject,k: String): String { val v=o[k];require(v is JsonPrimitive && v.isString);return v.asString }
            val version=root["zipVersion"];require(version is JsonPrimitive && version.isNumber && version.asString=="1" && text(root,"format")=="trade-review-full-backup") { "不支持此 ZIP 备份版本" }
            fun parse(e: JsonElement): ZipItem {
                require(e.isJsonObject);val o=e.asJsonObject;require(o.keySet()==setOf("path","bytes","sha256"))
                val size=o["bytes"];require(size is JsonPrimitive && size.isNumber && Regex("[0-9]+").matches(size.asString))
                return ZipItem(text(o,"path"),size.asString.toLong(),text(o,"sha256")).also { require(allowed(it.path) && it.bytes in 1..limit(it.path) && Regex("[0-9a-f]{64}").matches(it.sha256)) }
            }
            require(root["images"].isJsonArray)
            val dataItem=parse(root["data"]);require(dataItem.path=="data.json")
            val images=root["images"].asJsonArray.map(::parse);require(images.all { imagePath.matches(it.path) })
            val manifestItems=listOf(dataItem)+images
            require(manifestItems.map { it.path }.distinct().size==manifestItems.size && manifestItems.map { it.path }.toSet()+"manifest.json"==seen) { "ZIP 文件与清单不一致" }
            manifestItems.forEach { item -> val f=File(dir,item.path);require(f.length()==item.bytes && sha256(f)==item.sha256) { "ZIP 文件 SHA-256 校验失败" } }
            val data=File(dir,"data.json").inputStream().use(BackupCodec::read)
            require(data.sourceSchemaVersion in 5..BackupCodec.SCHEMA_VERSION && data.backupScope=="with-attachment-files" && data.exportedAt==text(root,"exportedAt")) { "完整 ZIP 的数据版本或范围不正确" }
            require(images.associate { it.path to (it.bytes to it.sha256) }==data.attachments.associate { "images/${attachmentFileName(it)}" to (it.byteCount to it.sha256) }) { "截图元数据与校验清单不一致" }
            archive.delete();return ParsedZip(dir,data)
        } catch(e: Exception) { dir.deleteRecursively();if(e is BackupException)throw e
            if(e is IOException)throw BackupException("ZIP 文件损坏或存储空间不足，未恢复任何数据")
            if(e is SecurityException)throw BackupException("备份文件权限失效，请重新选择；未恢复任何数据")
            throw BackupException(e.message?.takeUnless { it=="Failed requirement." } ?: "ZIP 字段或格式无效，未恢复任何数据")
        }
    }
}
