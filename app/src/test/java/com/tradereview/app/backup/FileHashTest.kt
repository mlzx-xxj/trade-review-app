package com.tradereview.app.backup

import com.tradereview.app.evidence.*
import org.junit.Assert.*
import org.junit.Test

class FileHashTest {
    @Test fun allByteValuesKeepOriginalDigestEncodingIncludingLeadingZeroAndNegativeBytes(){
        val bytes=ByteArray(256){it.toByte()};assertEquals(bytes.joinToString(""){"%02x".format(it)},digestHex(bytes))
        assertEquals("00010f10ff",digestHex(byteArrayOf(0,1,15,16,-1)))
    }
    @Test fun knownSha256StillMatchesPublishedAlgorithmAndBackupChecksums(){
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",sha256("abc".byteInputStream()))
    }
}
