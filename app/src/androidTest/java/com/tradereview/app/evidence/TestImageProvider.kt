package com.tradereview.app.evidence

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import java.io.*

class TestImageProvider:ContentProvider() {
    companion object { fun uri(name: String="good.png"):Uri=Uri.parse("content://com.tradereview.app.test.images/$name") }
    override fun onCreate()=true
    private fun fixture(name: String):File {
        require(name in listOf("good.png","second.png","gps.jpg","bad.jpg","huge.png","missing.png","denied.png"))
        if(name=="denied.png")throw SecurityException("Synthetic revoked permission")
        if(name=="missing.png")throw FileNotFoundException("Synthetic missing image")
        val root=File(requireNotNull(context).cacheDir,"synthetic-images").apply { mkdirs() };val file=File(root,name)
        if(file.exists())return file
        if(name=="bad.jpg")file.writeText("not an image")
        else if(name=="huge.png")FileOutputStream(file).use { out->repeat(6){out.write(ByteArray(1024*1024))} }
        else {
            val bitmap=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.DKGRAY;textSize=27f };canvas.drawText("合成截图：仅用于自动化测试",25f,50f,paint)
            paint.color=Color.LTGRAY;paint.strokeWidth=1f
            for(y in 90..360 step 45)canvas.drawLine(20f,y.toFloat(),620f,y.toFloat(),paint)
            for(x in 60..580 step 65)canvas.drawLine(x.toFloat(),80f,x.toFloat(),360f,paint)
            paint.color=if(name=="second.png")Color.rgb(190,45,60)else Color.rgb(0,125,90);paint.strokeWidth=5f
            val points=listOf(120f,180f,150f,240f,200f,285f,265f,310f)
            points.zipWithNext().forEachIndexed { index,(a,b)->canvas.drawLine(55f+index*75,390f-a,130f+index*75,390f-b,paint) }
            FileOutputStream(file).use { bitmap.compress(if(name.endsWith("jpg"))Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,90,it) };bitmap.recycle()
            if(name=="gps.jpg")ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_GPS_LATITUDE,"1/1,0/1,0/1");setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");setAttribute(ExifInterface.TAG_GPS_LONGITUDE,"1/1,0/1,0/1");setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,"E");setAttribute(ExifInterface.TAG_ORIENTATION,"6");saveAttributes() }
        };return file
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor { require(mode=="r");return ParcelFileDescriptor.open(fixture(requireNotNull(uri.lastPathSegment)),ParcelFileDescriptor.MODE_READ_ONLY) }
    override fun getType(uri:Uri)=if(uri.lastPathSegment?.endsWith("jpg")==true)"image/jpeg"else "image/png"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val f=fixture(requireNotNull(uri.lastPathSegment));return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply { addRow(arrayOf(f.name,f.length())) }
    }
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle? { if(method=="erase")File(File(requireNotNull(context).cacheDir,"synthetic-images"),requireNotNull(arg)).delete();return null }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
