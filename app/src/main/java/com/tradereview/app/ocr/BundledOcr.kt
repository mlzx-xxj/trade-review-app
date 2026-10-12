package com.tradereview.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface OfflineRecognizer { suspend fun recognize(bitmap:Bitmap):String }
class BundledOcr:OfflineRecognizer {
    override suspend fun recognize(bitmap:Bitmap):String {
        val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        // Task has no cancellation API: retain bitmap/client until completion, even if the screen closes.
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try { suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener { if(continuation.isActive)continuation.resume(it.text) }
                    .addOnFailureListener { if(continuation.isActive)continuation.resumeWithException(it) }
            } } finally { recognizer.close() }
        }
    }
}
