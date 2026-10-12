package com.tradereview.app.evidence

import com.tradereview.app.data.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class EvidenceException(message: String) : IllegalArgumentException(message)
object EvidenceLimits {
    const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
    const val MAX_IMAGES = 10_000
    const val MAX_PER_TRADE = 10
    const val MAX_EDGE = 2048
    fun settings(value: EvidenceSettings) { require(value.id == 1 && value.maxInputMiB in listOf(5,10,20)) { "原图上限仅支持 5/10/20 MiB" } }
    fun validate(a: Attachment) {
        require(Regex("[0-9a-f]{32}").matches(a.id) && Regex("[0-9a-f]{32}").matches(a.recordKey)) { "附件标识无效" }
        require(Regex("[0-9a-f]{64}").matches(a.sha256) && a.byteCount in 1..MAX_IMAGE_BYTES) { "附件摘要或大小无效" }
        require(a.width in 1..MAX_EDGE && a.height in 1..MAX_EDGE && a.sortOrder in 0..100000 && a.createdAt in 0..4133980799999L)
        require(a.kind in EvidenceOptions.kinds.map { it.id } && a.caption.length <= 200 && '\u0000' !in a.caption) { "附件分类或说明无效" }
    }
}
fun sha256(input: InputStream): String {
    val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
    while(true) { val count = input.read(buffer); if(count < 0) break; hash.update(buffer,0,count) }
    return digestHex(hash.digest())
}
/** Equivalent lower-case, zero-padded SHA text without constructing 32 Formatters per file. */
fun digestHex(bytes:ByteArray):String {
    val digits="0123456789abcdef";val chars=CharArray(bytes.size*2)
    bytes.forEachIndexed{index,byte->val value=byte.toInt() and 255;chars[index*2]=digits[value ushr 4];chars[index*2+1]=digits[value and 15]}
    return String(chars)
}
fun sha256(file: File) = file.inputStream().use(::sha256)
fun copyBounded(input: InputStream, output: OutputStream, maximum: Long): Long {
    val buffer=ByteArray(8192);var size=0L
    while(true) { val count=input.read(buffer);if(count<0)break;size+=count
        if(size>maximum)throw EvidenceException("文件超过允许大小，操作已停止")
        output.write(buffer,0,count)
    };return size
}
