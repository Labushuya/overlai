package de.overlai.feature.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// CHANGE-MARKER v0.1.0: OCR (siehe CHANGELOG.md)
// ML Kit Text Recognition v2 (gebündeltes Latin-Modell, offline, kein GMS-Zwang).
// Wandelt ein Bitmap in erkannten Text. suspend-API über die Task-Callbacks.
class MlKitOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer
                .process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
            cont.invokeOnCancellation { recognizer.close() }
        }
}
