package com.tradereview.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest
import com.tradereview.app.evidence.digestHex

class ExportReadbackTest {
    private val bytes="中文备份\n盈亏-1.23,USDT".toByteArray(Charsets.UTF_8)
    private val hash get()=digestHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    @Test fun hashesTheExactWrittenUtf8BytesIncludingMixedWriteMethods(){val target=ByteArrayOutputStream();val output=ExportDigest(target);output.write(bytes[0].toInt());output.write(bytes,1,bytes.size-1);output.close();assertEquals(bytes.size.toLong(),output.bytes);assertEquals(hash,output.finish());assertArrayEquals(bytes,target.toByteArray());assertEquals(Readback.VERIFIED,ExportReadback.verify({ByteArrayInputStream(target.toByteArray())},bytes.size.toLong(),hash))}
    @Test fun sameSizeTamperingAndTruncationRejectRatherThanClaimSuccess(){for(value in listOf(bytes.copyOf().apply{this[0]=0},bytes.dropLast(1).toByteArray())){try{ExportReadback.verify({ByteArrayInputStream(value)},bytes.size.toLong(),hash);fail("damaged export accepted")}catch(e:BackupException){assertTrue(e.message!!.contains("校验失败"))}}}
    @Test fun oversizedProviderReadStopsAtTheExpectedExportBound(){val value=bytes+byteArrayOf(1);try{ExportReadback.verify({ByteArrayInputStream(value)},bytes.size.toLong(),hash);fail("oversized export accepted")}catch(e:BackupException){assertTrue(e.message!!.contains("大小不一致"))}}
    @Test fun unavailableProviderIsExplicitlyNotVerified(){assertEquals(Readback.UNAVAILABLE,ExportReadback.verify({throw SecurityException("synthetic permission denial")},bytes.size.toLong(),hash));assertEquals(Readback.UNAVAILABLE,ExportReadback.verify({null},bytes.size.toLong(),hash));assertEquals(Readback.UNAVAILABLE,ExportReadback.verify({throw IOException("synthetic provider denial")},bytes.size.toLong(),hash))}
}
