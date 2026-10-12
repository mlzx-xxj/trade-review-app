package com.tradereview.app.evidence

import android.graphics.Bitmap
import android.util.LruCache
import com.tradereview.app.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Bounded decoded thumbnails. Integrity is rechecked even on a hit; never recycle a displayed bitmap. */
class AttachmentImageCache(private val store:AttachmentStore,val maximumBytes:Int=8*1024*1024) {
    init{require(maximumBytes in 64*1024..16*1024*1024)}
    private val loader=Mutex()
    private val generation=AtomicInteger()
    private val memoryLock=Any()
    private val hits=AtomicLong()
    private val misses=AtomicLong()
    private val cache=object:LruCache<String,Bitmap>(maximumBytes){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    val bytes get()=cache.size()
    val hitCount get()=hits.get()
    val missCount get()=misses.get()
    fun clear(){synchronized(memoryLock){generation.incrementAndGet();cache.evictAll()}}
    suspend fun load(value:Attachment,edge:Int):Bitmap=withContext(Dispatchers.IO){loader.withLock {
        require(edge in 64..2048)
        val file=store.file(value)
        // File length + SHA are checked every time, including same-size/timestamp corruptions.
        store.verify(file,value,decode=false)
        val key="${value.id}:${value.sha256}:${value.width}:${value.height}:$edge"
        cache.get(key)?.takeUnless{it.isRecycled}?.let{hits.incrementAndGet();return@withLock it}
        val current=generation.get();val bitmap=store.bitmap(value,edge);misses.incrementAndGet()
        synchronized(memoryLock){if(edge<=512 && bitmap.allocationByteCount<=maximumBytes && current==generation.get())cache.put(key,bitmap)}
        bitmap
    }}
}
