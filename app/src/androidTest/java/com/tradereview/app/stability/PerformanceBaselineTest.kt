package com.tradereview.app.stability

import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.tradereview.app.backup.*
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Same deterministic corpus, one warm-up and three samples; no timing pass threshold. */
@RunWith(AndroidJUnit4::class)
class PerformanceBaselineTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today=LocalDate.of(2026,1,31)
    private val metrics=linkedMapOf<String,Any>()
    private fun rows(count:Int,lossRun:Boolean=false)=List(count){i->Trade(id=(i+1).toLong(),recordKey=(i+1).toString(16).padStart(32,'0'),
        date=today.toEpochDay()-(if(lossRun) i%7 else i%365),market=if(i%2==0)"futures"else "stock",symbol=if(i%2==0)"螺纹钢"else "沪深300",
        currency=if(lossRun)"CNY"else listOf("CNY","USD","USDT")[i%3],netPnl=if(lossRun)"-10.01"else when(i%5){0->"-25.35";1->"0.00";else->"100.10"},
        strategy=if(i%2==0)"trend"else "breakout",mistakes=if(i%4==0)listOf("chase")else emptyList(),reviewed=i%2==0,
        professional=if(i%2==0)ProfessionalTrade(riskMode="amount",manualRisk="200",planStatus=if(i%4==0)"in"else "out")else null)}
    private fun measure(name:String,block:()->Unit){
        block()
        val samples=List(3){val start=SystemClock.elapsedRealtimeNanos();block();(SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0}
        metrics[name]=mapOf("samplesMs" to samples,"medianMs" to samples.sorted()[1],"minimumMs" to samples.min(),"maximumMs" to samples.max())
    }
    private fun write(name:String){
        metrics["environment"]=mapOf("api" to Build.VERSION.SDK_INT,"model" to Build.MODEL,"abis" to Build.SUPPORTED_ABIS.toList(),"warmups" to 1,"samples" to 3,"datasetDate" to today.toString(),"measurement" to "elapsed wall time; disposable CI emulator, not phone")
        val json=GsonBuilder().setPrettyPrinting().create().toJson(metrics)
        V08Metrics.write("$name-api${Build.VERSION.SDK_INT}.json",json)
        println("V08_METRICS_FILE=$name-api${Build.VERSION.SDK_INT}.json")
    }
    @Test fun deterministicLargeDataAnalyticsRoomJsonAndZipBaseline(){runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        val root=File(context.cacheDir,"performance-${newKey()}").apply{mkdirs()}
        try {
            for(count in listOf(1000,5000,10000)) {
                val values=rows(count)
                val streakRows=rows(count,true)
                measure("analytics-$count"){assertEquals(count/3+(if(count%3>0)1 else 0),AdvancedAnalytics.calculate(values,emptyList(),"CNY",TradeFilter(),today).home.count)}
                measure("calendar-$count"){val daily=TradingCalendar.daily(values);assertEquals(count,daily.values.flatten().sumOf{it.count});repeat(12){TradingCalendar.monthCells(YearMonth.from(today).minusMonths(it.toLong()))}}
                measure("week-report-$count"){assertNotNull(ReviewReports.calculate(values,emptyList(),ReviewPeriod.at("week",today),today.toEpochDay()))}
                measure("month-report-$count"){assertNotNull(ReviewReports.calculate(values,emptyList(),ReviewPeriod.at("month",today),today.toEpochDay()))}
                measure("insights-$count"){SmartInsights.calculate(values,emptyList(),emptyList(),SmartSettings(minimumSamples=3),CatalogSeeds.tags,today.toEpochDay())}
                measure("loss-run-insights-$count"){assertTrue(SmartInsights.calculate(streakRows,emptyList(),emptyList(),SmartSettings(minimumSamples=3),CatalogSeeds.tags,today.toEpochDay()).any{it.id=="loss-frequency:CNY"})}
                db.withTransaction{db.trades().clearTrades();values.forEach{db.trades().insert(it)}}
                measure("room-list-$count"){runBlocking{assertEquals(count,db.trades().allTrades().size)}}
                val data=BackupRepository(db).export(full=true)
                measure("json-roundtrip-$count"){assertEquals(count,BackupCodec.decode(BackupCodec.encode(data)).trades.size)}
                if(count==10000){
                    val target=File(root,"corpus.zip")
                    measure("zip-write-10000"){FullZipArchive.write(data,{error("No images in this corpus")},target)}
                    measure("zip-read-10000"){target.inputStream().use{FullZipArchive.read(it,root).use{parsed->assertEquals(count,parsed.data.trades.size)}}}
                    metrics["structuredZipBytes"]=target.length()
                }
            }
            metrics["complete"]=true
        }finally{write("performance");db.close();root.deleteRecursively()}
    }}
    @Test fun privateImageRepeatedThumbnailBaseline(){
        val root=File(context.cacheDir,"thumbnail-bench-${newKey()}");val store=AttachmentStore(root)
        try {
            val image=store.importImage(context.contentResolver,TestImageProvider.uri("ocr.png"),"a".repeat(32),"analysis","合成 OCR 图片",EvidenceSettings(),0)
            val values=List(10){image.copy(id=newKey(),recordKey=(it+1).toString(16).padStart(32,'0'),sortOrder=0).also{a->store.publish(store.file(image),a)}}
            measure("thumbnail-10-revisits"){values.forEach{store.bitmap(it,256).recycle()}}
            metrics["physicalImages"]=values.size;metrics["originalImageBytes"]=image.byteCount
            write("images")
        }finally{root.deleteRecursively()}
    }
}
