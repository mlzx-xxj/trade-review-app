package com.tradereview.app.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import com.tradereview.app.evidence.digestHex

/** Hash the exact provider bytes, then reopen when supported; never assume picker selection succeeded. */
class ExportDigest(output:OutputStream):java.security.DigestOutputStream(output,MessageDigest.getInstance("SHA-256")) {
    var bytes:Long=0;private set
    override fun write(value:Int){super.write(value);bytes++}
    override fun write(buffer:ByteArray,offset:Int,length:Int){super.write(buffer,offset,length);bytes+=length}
    fun finish():String=digestHex(messageDigest.digest())
}

enum class Readback { VERIFIED, UNAVAILABLE }
object ExportReadback {
    fun verify(open:()->InputStream?,expectedBytes:Long,expectedSha:String):Readback {
        require(expectedBytes>0 && expectedBytes<=FullZipArchive.MAX_ZIP_BYTES)
        val input=try{open()}catch(e:SecurityException){return Readback.UNAVAILABLE}catch(e:java.io.IOException){return Readback.UNAVAILABLE}
            ?:return Readback.UNAVAILABLE
        input.use {
            val hash=MessageDigest.getInstance("SHA-256");var count=0L;val buffer=ByteArray(16*1024)
            while(true){
                val n=try{it.read(buffer)}catch(e:java.io.IOException){return Readback.UNAVAILABLE}
                if(n<0)break
                if(n==0){val b=it.read();if(b<0)break;count++;hash.update(b.toByte())}
                else{count+=n;hash.update(buffer,0,n)}
                if(count>expectedBytes)throw BackupException("导出文件回读大小不一致；不能作为有效备份，上次备份时间未更新")
            }
            if(count!=expectedBytes || digestHex(hash.digest())!=expectedSha)throw BackupException("导出文件回读校验失败；不能作为有效备份，上次备份时间未更新")
        }
        return Readback.VERIFIED
    }
}
