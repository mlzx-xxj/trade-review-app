package com.tradereview.app.stability

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.data.*
import com.tradereview.app.evidence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageCacheTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun boundedRevisitsDoNotRecyclePixelsStillHeldByUiAndCorruptionCannotHideBehindCache()=runBlocking {
        val root=File(context.cacheDir,"cache-test-${newKey()}");val store=AttachmentStore(root)
        try {
            val a=store.importImage(context.contentResolver,TestImageProvider.uri("ocr.png"),newKey(),"analysis","合成缓存测试",EvidenceSettings(),0)
            val cache=AttachmentImageCache(store,128*1024)
            val first=cache.load(a,256);assertSame(first,cache.load(a.copy(caption="仅改说明"),256));assertEquals(1L,cache.hitCount)
            repeat(10){val next=a.copy(id=newKey());store.publish(store.file(a),next);cache.load(next,256);assertTrue(cache.bytes<=cache.maximumBytes)}
            assertFalse(first.isRecycled);cache.clear();assertEquals(0,cache.bytes);assertFalse(first.isRecycled)
            cache.load(a,256);val file=store.file(a);val time=file.lastModified();val bytes=file.readBytes();bytes[bytes.lastIndex/2]=(bytes[bytes.lastIndex/2].toInt() xor 1).toByte();file.writeBytes(bytes);file.setLastModified(time)
            try{cache.load(a,256);fail("Cache must not hide same-size/timestamp corruption")}catch(_:IllegalArgumentException){}
        }finally{root.deleteRecursively()}
    }
    @Test fun missingFileAndWrongDimensionsAreRejectedAndLargeViewIsNotRetainedInThumbnailCache()=runBlocking {
        val root=File(context.cacheDir,"cache-missing-${newKey()}");val store=AttachmentStore(root)
        try {
            val a=store.importImage(context.contentResolver,TestImageProvider.uri(),newKey(),"analysis","",EvidenceSettings(),0)
            val cache=store.imageCache;cache.load(a,2048);assertEquals(0,cache.bytes)
            try{cache.load(a.copy(width=a.width-1),256);fail()}catch(_:IllegalArgumentException){}
            cache.load(a,256);store.file(a).delete();try{cache.load(a,256);fail()}catch(_:IllegalArgumentException){}
        }finally{root.deleteRecursively()}
    }
}
