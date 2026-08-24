package com.luics415.biogesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.math.max
import java.util.concurrent.atomic.AtomicInteger

/**
 * CameraX -> MediaPipe boundary. It owns all frame throttling and reuses its pixel buffers.
 * VIDEO mode is intentionally synchronous so a bitmap is never reused while MediaPipe reads it.
 */
class HandTrackingPipeline(
    context: Context,
    profile: PerformanceProfile,
    private val listener: Listener,
) : ImageAnalysis.Analyzer, Closeable {
    private val applicationContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var requestedFps = profile.targetFps

    @Volatile
    private var paused = false

    @Volatile
    private var thermalStatus = PowerManager.THERMAL_STATUS_NONE

    private var handLandmarker: HandLandmarker? = null
    private var bitmapBuffer: Bitmap? = null
    private var packedBuffer: ByteBuffer? = null
    private var lastAcceptedFrameMs = 0L
    private var lastPreviewFrameMs = 0L
    private var closed = false

    @Volatile
    private var previewEnabled = false

    @Volatile
    private var coordinateEpoch = 0L

    private val framesToSkipAfterEpoch = AtomicInteger(EPOCH_WARMUP_FRAMES)

    override fun analyze(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }

        val skipped = framesToSkipAfterEpoch.getAndUpdate { remaining ->
            if (remaining > 0) remaining - 1 else 0
        }
        if (skipped > 0) {
            image.close()
            return
        }

        val now = SystemClock.uptimeMillis()
        val frameCoordinateEpoch = coordinateEpoch
        val intervalMs = 1_000L / effectiveFps().coerceAtLeast(1)
        if (now - lastAcceptedFrameMs < intervalMs) {
            image.close()
            return
        }
        lastAcceptedFrameMs = now

        try {
            val landmarker = ensureLandmarker()
            val bitmap = ensureBitmap(image.width, image.height)
            copyRgbaFrame(image, bitmap)

            val mpImage = BitmapImageBuilder(bitmap).build()
            try {
                val processingOptions = ImageProcessingOptions.builder()
                    .setRotationDegrees(image.imageInfo.rotationDegrees)
                    .build()
                val startedAt = SystemClock.uptimeMillis()
                val result = landmarker.detectForVideo(mpImage, processingOptions, now)
                val previewBitmap = if (previewEnabled && now - lastPreviewFrameMs >= PREVIEW_INTERVAL_MS) {
                    lastPreviewFrameMs = now
                    createPreviewBitmap(bitmap, image.imageInfo.rotationDegrees)
                } else {
                    null
                }
                listener.onResult(
                    result,
                    FrameMetadata(
                        timestampMs = now,
                        sourceWidth = image.width,
                        sourceHeight = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        inferenceTimeMs = SystemClock.uptimeMillis() - startedAt,
                        previewBitmap = previewBitmap,
                        coordinateEpoch = frameCoordinateEpoch,
                    ),
                )
            } finally {
                mpImage.close()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Hand tracking frame failed", error)
            listener.onError(error)
        } finally {
            image.close()
        }
    }

    fun setProfile(profile: PerformanceProfile) {
        requestedFps = profile.targetFps
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun setThermalStatus(status: Int) {
        thermalStatus = status
    }

    fun setPreviewEnabled(enabled: Boolean) {
        previewEnabled = enabled
    }

    fun setCoordinateEpoch(epoch: Long) {
        coordinateEpoch = epoch
        framesToSkipAfterEpoch.set(EPOCH_WARMUP_FRAMES)
    }

    fun effectiveFps(): Int {
        if (paused) return PAUSED_FPS
        return when {
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> SEVERE_THERMAL_FPS
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> max(MODERATE_THERMAL_FPS, requestedFps / 2)
            else -> requestedFps
        }
    }

    private fun ensureLandmarker(): HandLandmarker = synchronized(lock) {
        check(!closed) { "HandTrackingPipeline is closed" }
        handLandmarker ?: run {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.CPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .build()
            HandLandmarker.createFromOptions(applicationContext, options).also {
                handLandmarker = it
            }
        }
    }

    private fun ensureBitmap(width: Int, height: Int): Bitmap {
        val existing = bitmapBuffer
        if (existing != null && existing.width == width && existing.height == height && !existing.isRecycled) {
            return existing
        }
        existing?.recycle()
        packedBuffer = null
        return createBitmap(width, height).also {
            bitmapBuffer = it
        }
    }

    private fun copyRgbaFrame(image: ImageProxy, bitmap: Bitmap) {
        val plane = image.planes.first()
        val source = plane.buffer
        source.rewind()
        val packedRowBytes = image.width * RGBA_BYTES_PER_PIXEL

        if (plane.pixelStride == RGBA_BYTES_PER_PIXEL && plane.rowStride == packedRowBytes) {
            bitmap.copyPixelsFromBuffer(source)
            return
        }

        val requiredSize = packedRowBytes * image.height
        var packed = packedBuffer
        if (packed == null || packed.capacity() < requiredSize) {
            packed = ByteBuffer.allocateDirect(requiredSize)
            packedBuffer = packed
        }
        packed.clear()

        for (row in 0 until image.height) {
            val rowStart = row * plane.rowStride
            if (plane.pixelStride == RGBA_BYTES_PER_PIXEL) {
                val rowView = source.duplicate()
                rowView.position(rowStart)
                rowView.limit(rowStart + packedRowBytes)
                packed.put(rowView)
            } else {
                for (column in 0 until image.width) {
                    val pixelStart = rowStart + column * plane.pixelStride
                    packed.put(source.get(pixelStart))
                    packed.put(source.get(pixelStart + 1))
                    packed.put(source.get(pixelStart + 2))
                    packed.put(source.get(pixelStart + 3))
                }
            }
        }
        packed.flip()
        bitmap.copyPixelsFromBuffer(packed)
    }

    private fun createPreviewBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        val copy = source.copy(Bitmap.Config.ARGB_8888, false)
        if (rotationDegrees == 0) return copy
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(copy, 0, 0, copy.width, copy.height, matrix, true).also {
            copy.recycle()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            handLandmarker?.close()
            handLandmarker = null
            bitmapBuffer?.recycle()
            bitmapBuffer = null
            packedBuffer = null
        }
    }

    interface Listener {
        fun onResult(result: HandLandmarkerResult, metadata: FrameMetadata)
        fun onError(error: Throwable)
    }

    companion object {
        private const val TAG = "HandTrackingPipeline"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val RGBA_BYTES_PER_PIXEL = 4
        private const val PAUSED_FPS = 6
        private const val MODERATE_THERMAL_FPS = 10
        private const val SEVERE_THERMAL_FPS = 6
        // Detection starts conservatively, then tracking is allowed to retain a partially
        // occluded or fast-moving hand instead of dropping the complete landmark set.
        private const val MIN_DETECTION_CONFIDENCE = 0.55f
        private const val MIN_PRESENCE_CONFIDENCE = 0.55f
        private const val MIN_TRACKING_CONFIDENCE = 0.50f
        private const val PREVIEW_INTERVAL_MS = 100L
        private const val EPOCH_WARMUP_FRAMES = 2
    }
}

data class FrameMetadata(
    val timestampMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int,
    val inferenceTimeMs: Long,
    val previewBitmap: Bitmap? = null,
    val coordinateEpoch: Long = 0L,
)
