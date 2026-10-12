package com.tradereview.app.stability

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ocr.BundledOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic, labeled evaluation. Missing/wrong values are measured, never guessed or auto-saved. */
@RunWith(AndroidJUnit4::class)
class OcrEvaluationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val expected=linkedMapOf("date" to "2024-02-29","market" to "futures","symbol" to "螺纹钢","contractCode" to "rb2701",
        "direction" to "short","currency" to "CNY","entryPrice" to "3500.25","exitPrice" to "3490.125","quantity" to "2.5","fees" to "3.25","realizedPnl" to "-25.35")
    private val mixed=listOf("交易日期: 2024-02-29","市场: 国内期货","品种: 螺纹钢","合约: rb2701","方向: 做空","币种: CNY",
        "Entry price: 3500.25","Exit price: 3490.125","Lots: 2.5","Fees: 3.25","Realized net P&L: -25.35")
    private val english=listOf("Date: 2024-02-29","Market: futures","Symbol: RB","Contract: rb2701","Direction: short","Currency: USD",
        "Entry price: 3500.25","Exit price: 3490.125","Lots: 2.5","Fees: 3.25","Realized net P&L: -25.35")
    private data class Sample(val name:String,val lines:List<String>,val truth:List<Map<String,String>>,val scale:Float=1f,val blur:Boolean=false,val rotated:Boolean=false,val forbidden:Boolean=false)
    private fun bitmap(lines:List<String>):Bitmap {
        val image=Bitmap.createBitmap(1280,maxOf(1440,lines.size*100+100),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=48f}
        lines.forEachIndexed{i,line->canvas.drawText(line,40f,90f+i*100f,paint)}
        canvas.drawText("Synthetic test only / 合成样本",40f,image.height-35f,paint)
        return image
    }
    @Test fun evaluatePackagedChineseLatinNumbersRotationAndUnsafeScreenshotsWithoutNetwork()=runBlocking {
        val root=File(context.cacheDir,"ocr-evaluation-${newKey()}").apply{mkdirs()};val store=AttachmentStore(File(root,"private"))
        val second=english.map{it.replace("2024-02-29","2024-02-28").replace("-25.35","100.10")}
        val engTruth=expected+mapOf("symbol" to "RB","currency" to "USD")
        val samples=listOf(
            Sample("mixed-clear",mixed,listOf(expected)),Sample("english-clear",english,listOf(engTruth)),
            Sample("mixed-half-resolution",mixed,listOf(expected),scale=.5f),
            Sample("mixed-blurred",mixed,listOf(expected),blur=true),Sample("mixed-exif-rotation",mixed,listOf(expected),rotated=true),
            Sample("two-closed-trades",listOf("Trade 1")+english+listOf("Trade 2")+second,listOf(engTruth,engTruth+mapOf("date" to "2024-02-28","realizedPnl" to "100.10"))),
            Sample("order-record",listOf("Order history")+english,emptyList(),forbidden=true),
            Sample("position-floating",listOf("Position history")+english.filterNot{it.startsWith("Realized")}+"Unrealized P&L: -25.35",emptyList(),forbidden=true),
            Sample("chart-only",listOf("Candlestick chart","3500.25","3490.125","2.5","-25.35"),emptyList(),forbidden=true))
        val reports=mutableListOf<Map<String,Any>>()
        try {
            for(sample in samples) {
                var image=bitmap(sample.lines)
                if(sample.scale!=1f || sample.blur){val small=Bitmap.createScaledBitmap(image,if(sample.blur)320 else (image.width*sample.scale).toInt(),if(sample.blur)image.height/4 else (image.height*sample.scale).toInt(),true);image.recycle();image=small
                    if(sample.blur){val expanded=Bitmap.createScaledBitmap(image,1280,image.height*4,true);image.recycle();image=expanded}}
                if(sample.rotated){val turned=Bitmap.createBitmap(image,0,0,image.width,image.height,Matrix().apply{setRotate(90f)},true);image.recycle();image=turned}
                val source=File(root,"${sample.name}.${if(sample.rotated)"jpg"else "png"}")
                source.outputStream().use{assertTrue(image.compress(if(sample.rotated)Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,95,it))};image.recycle()
                if(sample.rotated){val exif=ExifInterface(source.path);exif.setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_270.toString());exif.saveAttributes()}
                val imported=withContext(Dispatchers.IO){store.importImage(context.contentResolver,Uri.fromFile(source),newKey(),"analysis","合成 OCR 评估",EvidenceSettings(maxInputMiB=20),0)}
                val decoded=withContext(Dispatchers.IO){store.bitmap(imported,2048)}
                val start=SystemClock.elapsedRealtimeNanos()
                val raw=try{withContext(Dispatchers.Default){BundledOcr().recognize(decoded)}}finally{decoded.recycle()}
                val elapsed=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
                val candidates=OcrTradeParser.parse(raw)
                assertTrue(candidates.none{OcrTradeParser.canCreate(it,emptySet(),false)})
                var correct=0;var missing=0;var wrong=0;var extra=0
                sample.truth.forEachIndexed{index,truth->val actual=candidates.getOrNull(index)?.fields.orEmpty();truth.forEach{(key,value)->when{actual[key]==null->missing++;actual[key]==value->correct++;else->wrong++}};extra+=actual.keys.count{it !in truth}}
                val unsafeReady=if(sample.forbidden)candidates.count{OcrTradeParser.canCreate(it,it.fields.keys,true)}else 0
                // Clear unsafe fixtures must never pass machine eligibility even with hypothetical confirmation.
                if(sample.forbidden)assertEquals(sample.name+" / "+raw,0,unsafeReady)
                reports+=mapOf("sample" to sample.name,"expectedCandidates" to sample.truth.size,"actualCandidates" to candidates.size,"expectedFields" to sample.truth.sumOf{it.size},
                    "correctFields" to correct,"missingFields" to missing,"wrongFields" to wrong,"extraFields" to extra,"unsafeReadyCandidates" to unsafeReady,
                    "recognizedFields" to candidates.map{it.fields},"elapsedMs" to elapsed,"sourceWidthAfterNormalization" to imported.width,"sourceHeightAfterNormalization" to imported.height)
                store.cleanup(emptyList())
            }
            V08Metrics.write("ocr-evaluation-api${Build.VERSION.SDK_INT}.json",GsonBuilder().setPrettyPrinting().create().toJson(mapOf("api" to Build.VERSION.SDK_INT,"samples" to reports,
                "definition" to "exact normalized fields / labeled fields; missing separately; wrong and extra are false fields; unsafe eligibility assumes confirmation but no trade is saved; synthetic corpus only")))
        }finally{root.deleteRecursively()}
    }
}
