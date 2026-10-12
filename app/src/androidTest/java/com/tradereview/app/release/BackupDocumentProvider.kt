package com.tradereview.app.release

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import com.tradereview.app.data.newKey

/** Synthetic SAF-style provider: test APK only. It never sees personal records/files. */
class BackupDocumentProvider:ContentProvider(){
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/octet-stream"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(arrayOf("_display_name","_size"))
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        val kind=uri.pathSegments[0];val id=uri.pathSegments[1];require(Regex("[0-9a-f]{32}").matches(id))
        val file=File(requireNotNull(context).cacheDir,"v100-document-$id")
        if(mode.contains("w") && kind=="write-fail")throw FileNotFoundException("synthetic output failure")
        if(mode=="r" && kind=="no-read")throw FileNotFoundException("synthetic write-only provider")
        if(mode=="r" && kind=="corrupt"){val data=file.readBytes();data[0]=(data[0].toInt() xor 1).toByte();file.writeBytes(data)}
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode))
    }
    companion object{fun uri(kind:String="normal")=Uri.parse("content://com.tradereview.app.test.v100backup/$kind/${newKey()}")}
}
