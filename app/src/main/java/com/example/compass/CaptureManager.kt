package com.example.compass

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

object CaptureManager {

    sealed class CaptureResult {
        data class Success(val uri: Uri, val displayName: String) : CaptureResult()
        data class Error(val message: String, val throwable: Throwable? = null) : CaptureResult()
    }

    /**
     * Captures a raw camera frame, renders the compass overlays directly onto the bitmap,
     * and saves it to MediaStore (Pictures/CompassApp) without including the bottom bar.
     */
    fun capturePhotoWithCompassOverlay(
        context: Context,
        imageCapture: ImageCapture,
        executor: Executor,
        currentAzimuth: Float,
        onResult: (CaptureResult) -> Unit
    ) {
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rawBitmap = imageProxy.toBitmap()
                        val rotation = imageProxy.imageInfo.rotationDegrees

                        // Adjust rotation if needed to match portrait orientation
                        val orientedBitmap = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            Bitmap.createBitmap(
                                rawBitmap,
                                0,
                                0,
                                rawBitmap.width,
                                rawBitmap.height,
                                matrix,
                                true
                            )
                        } else {
                            rawBitmap
                        }

                        // Create mutable copy for drawing overlays
                        val compositeBitmap = orientedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(compositeBitmap)

                        val bmpWidth = compositeBitmap.width.toFloat()
                        val bmpHeight = compositeBitmap.height.toFloat()

                        // Calculate scale and top padding proportional to 1080x2400 resolution
                        val scale = (bmpWidth / 1080f).coerceAtLeast(1f)
                        val density = 3f * scale
                        val topPaddingPx = 52f * density

                        // Draw only the vertical line and graduated ruler (NO bottom bar)
                        CompassOverlayDrawer.drawOverlay(
                            canvas = canvas,
                            width = bmpWidth,
                            height = bmpHeight,
                            azimuth = currentAzimuth,
                            density = density,
                            topPaddingPx = topPaddingPx,
                            isCapture = true
                        )

                        // Save composite PNG to MediaStore
                        val savedUri = saveBitmapToPictures(context, compositeBitmap)
                        if (savedUri != null) {
                            onResult(
                                CaptureResult.Success(
                                    uri = savedUri,
                                    displayName = "Pictures/CompassApp"
                                )
                            )
                        } else {
                            onResult(CaptureResult.Error("No se pudo guardar la imagen en MediaStore"))
                        }
                    } catch (e: Exception) {
                        onResult(CaptureResult.Error("Error procesando captura: ${e.localizedMessage}", e))
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(CaptureResult.Error("Error de cámara: ${exception.localizedMessage}", exception))
                }
            }
        )
    }

    private fun saveBitmapToPictures(context: Context, bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "COMPASS_$timeStamp.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/CompassApp"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openOutputStream(itemUri)?.use { outStream: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            return itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            return null
        }
    }
}
