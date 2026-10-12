package com.tradereview.app.stability

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Test-only synthetic metrics. Also export to Downloads because AGP does not pull JSON on all APIs. */
object V08Metrics {
    fun write(name:String,json:String) {
        require(name.matches(Regex("[a-z0-9-]+\\.json")))
        val output=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"v08-metrics").apply{mkdirs()}
        File(output,name).writeText(json)
        val resolver=ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"application/json");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/TradeReview-v08-metrics");put(MediaStore.MediaColumns.IS_PENDING,1)}
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use{it.write(json)}
        resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
    }
}
