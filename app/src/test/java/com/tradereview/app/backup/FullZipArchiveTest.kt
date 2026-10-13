package com.tradereview.app.backup

import com.tradereview.app.data.*
import com.tradereview.app.evidence.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.*

class FullZipArchiveTest {
    @get:Rule val tmp=TemporaryFolder()
    private fun fixture():Pair<BackupData,File> {
        val t=Trade(id=1,date=20000,netPnl="12.34");val f=tmp.newFile().apply { writeBytes(byteArrayOf(1,2,3,4)) }
        val a=Attachment(recordKey=t.recordKey,sha256=sha256(f),byteCount=4,width=2,height=2)
        return BackupData("2025-01-01T00:00:00Z",listOf(t),emptyList(),attachments=listOf(a),backupScope="with-attachment-files") to f
    }
    private fun archive(entries:List<Pair<String,ByteArray>>):ByteArray = ByteArrayOutputStream().apply { ZipOutputStream(this).use { z -> entries.forEach { (n,b)->z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry() } } }.toByteArray()
    private fun read(bytes:ByteArray)=FullZipArchive.read(ByteArrayInputStream(bytes),tmp.root)
    @Test fun archiveRoundTripChecksEveryMetadataHashAndClosesTemporaryFiles() {
        val (data,image)=fixture();val file=tmp.newFile();FullZipArchive.write(data,{image},file)
        val parsed=read(file.readBytes());assertEquals(data,parsed.data);assertArrayEquals(image.readBytes(),parsed.image(data.attachments.single()).readBytes());val directory=parsed.directory;parsed.close();assertFalse(directory.exists())
    }
    @Test fun legacyV06SchemaFiveZipRetainsItsImagesAndSuppliesSmartDefaults() {
        val (data,image)=fixture();val file=tmp.newFile();FullZipArchive.write(data,{image},file)
        val entries=linkedMapOf<String,ByteArray>();ZipInputStream(file.inputStream()).use { z->while(true){val e=z.nextEntry ?: break;entries[e.name]=z.readBytes()} }
        val gson=com.google.gson.GsonBuilder().serializeNulls().disableHtmlEscaping().create()
        fun digest(bytes:ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val root=com.google.gson.JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject
        root.addProperty("schemaVersion",5);root.remove("checksumSha256")
        listOf("smartSettings","preflightItems","preflightChecks","ocrDrafts","ledger").forEach { root.remove(it) }
        listOf("preflightItems","preflightChecks","ocrDrafts","ledgerRecords").forEach { root.getAsJsonObject("counts").remove(it) }
        root.addProperty("checksumSha256",digest(gson.toJson(root).toByteArray(Charsets.UTF_8)))
        val payload=gson.toJson(root).toByteArray(Charsets.UTF_8);entries["data.json"]=payload
        val manifest=com.google.gson.JsonParser.parseString(entries.getValue("manifest.json").toString(Charsets.UTF_8)).asJsonObject
        manifest.getAsJsonObject("data").addProperty("bytes",payload.size);manifest.getAsJsonObject("data").addProperty("sha256",digest(payload));entries["manifest.json"]=gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        val parsed=read(archive(entries.entries.map { it.key to it.value }))
        try {assertEquals(data.copy(sourceSchemaVersion=5),parsed.data);assertArrayEquals(image.readBytes(),parsed.image(data.attachments.single()).readBytes())}finally {parsed.close()}
    }
    @Test fun missingOrTamperedImageCannotBeSilentlyOmittedFromExport() {
        val (data,image)=fixture();image.writeBytes(byteArrayOf(9));assertThrows(IllegalArgumentException::class.java){FullZipArchive.write(data,{image},tmp.newFile())};image.delete()
        assertThrows(IllegalArgumentException::class.java){FullZipArchive.write(data,{image},tmp.newFile())}
    }
    @Test fun changingSourceBetweenValidationAndCopyStopsExport() {
        val (data,image)=fixture();val other=tmp.newFile().apply { writeBytes(byteArrayOf(9,9,9,9)) };var calls=0;val target=tmp.newFile()
        assertThrows(IllegalArgumentException::class.java){FullZipArchive.write(data,{if(++calls==1)image else other},target)};assertFalse(target.exists())
    }
    @Test fun traversalAbsolutePathsBackslashesAndExtraFilesAreRejected() {
        listOf("../escape.jpg","/absolute.jpg","images/../escape.jpg","images\\escape.jpg","data.json/extra","unknown.txt").forEach { path ->
            assertThrows(BackupException::class.java){read(archive(listOf(path to byteArrayOf(1))))}
        };assertFalse(File(tmp.root.parentFile,"escape.jpg").exists())
    }
    @Test fun duplicateCentralNamesAreRejectedBeforeExtraction() {
        val bytes=archive(listOf("data.json" to byteArrayOf(1),"xata.json" to byteArrayOf(2)))
        val from="xata.json".toByteArray();val to="data.json".toByteArray()
        for(i in 0..bytes.size-from.size)if(bytes.sliceArray(i until i+from.size).contentEquals(from))to.copyInto(bytes,i)
        assertThrows(BackupException::class.java){read(bytes)}
    }
    @Test fun compressedOversizeAndExpandedBombAreBoundedAndLeaveNoStage() {
        val input=object:InputStream(){var left=FullZipArchive.MAX_ZIP_BYTES+1;override fun read():Int=if(left-- >0)0 else -1;override fun read(b:ByteArray,off:Int,len:Int):Int { if(left<=0)return -1;val count=minOf(left,len.toLong()).toInt();java.util.Arrays.fill(b,off,off+count,0.toByte());left-=count;return count } }
        assertThrows(BackupException::class.java){FullZipArchive.read(input,tmp.root)}
        assertTrue(tmp.root.listFiles()!!.none { it.name.startsWith("zip-") })
        val path="images/${"a".repeat(32)}-${"b".repeat(64)}.jpg"
        assertThrows(BackupException::class.java){read(archive(listOf(path to ByteArray((EvidenceLimits.MAX_IMAGE_BYTES+1).toInt()))))}
    }
    @Test fun invalidManifestChecksumAndTruncatedZipAreRejected() {
        val (data,image)=fixture();val file=tmp.newFile();FullZipArchive.write(data,{image},file);val entries=mutableListOf<Pair<String,ByteArray>>()
        ZipInputStream(file.inputStream()).use { z->while(true){val e=z.nextEntry ?: break;entries+=e.name to z.readBytes()} }
        val broken=entries.map { (n,b)-> n to if(n=="data.json")b.toString(Charsets.UTF_8).replace("12.34","12.35").toByteArray() else b }
        assertThrows(BackupException::class.java){read(archive(broken))};assertThrows(BackupException::class.java){read(file.readBytes().dropLast(20).toByteArray())}
    }
    @Test fun metadataReferencesUnknownTradeAndJsonScopeCannotPretendFullBackup() {
        val (data,_)=fixture();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(data.copy(backupScope="structured-data-only"))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(data.copy(attachments=data.attachments.map { it.copy(recordKey="f".repeat(32)) }))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(data.copy(attachments=List(11){data.attachments.single().copy(id=newKey())}))}
    }
    @Test fun excessiveCentralFileCountIsRejectedBeforeReadingPayload() {
        val files=(0..FullZipArchive.MAX_FILES).map { "images/${it.toString(16).padStart(32,'0')}-${"b".repeat(64)}.jpg" to byteArrayOf(1) }
        val error=assertThrows(BackupException::class.java){read(archive(files))};assertTrue(error.message!!.contains("文件过多"))
    }
    @Test fun inflatedDeclaredTotalIsRejectedWithoutAllocatingExpandedFiles() {
        val files=(0..128).map { "images/${it.toString(16).padStart(32,'0')}-${"b".repeat(64)}.jpg" to byteArrayOf(1) }
        val bytes=archive(files)
        // Central header uncompressed size lives at byte 24; actual payload stays one byte.
        for(i in 0..bytes.size-28)if(bytes[i]==0x50.toByte() && bytes[i+1]==0x4b.toByte() && bytes[i+2]==0x01.toByte() && bytes[i+3]==0x02.toByte()) {
            val size=EvidenceLimits.MAX_IMAGE_BYTES.toInt();for(j in 0..3)bytes[i+24+j]=(size ushr (j*8)).toByte()
        }
        val error=assertThrows(BackupException::class.java){read(bytes)};assertTrue(error.message!!.contains("声明总量"))
    }
}
