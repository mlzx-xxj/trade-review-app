package com.tradereview.app.ocr
import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.evidence.TestImageProvider
import com.tradereview.app.domain.OcrTradeParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class BundledOcrTest {
    @Test fun bundledChineseLatinDigitsWorkWithoutInternetPermission()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION") val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
        val bitmap=context.contentResolver.openInputStream(TestImageProvider.uri("ocr.png"))!!.use { BitmapFactory.decodeStream(it)!! }
        val text=try { withTimeout(60000){BundledOcr().recognize(bitmap)} }finally {bitmap.recycle()}
        println("SYNTHETIC_OFFLINE_OCR="+text.replace('\n',';'))
        val compact=text.replace(Regex("\\s"),"")
        assertTrue(text,compact.contains("螺纹钢"));assertTrue(text,compact.contains("rb2701"));assertTrue(text,compact.contains("3500.25"));assertTrue(text,compact.contains("-25.35"))
        assertTrue(OcrTradeParser.parse(text).any { it.closedEvidence })
    }
}
