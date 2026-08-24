package com.luics415.biogesture

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.luics415.biogesture.gesture.GestureEffect
import com.luics415.biogesture.gesture.GestureEngine
import com.luics415.biogesture.gesture.HandPose
import com.luics415.biogesture.gesture.Point3
import com.luics415.biogesture.menu.MenuActionId
import com.luics415.biogesture.menu.MenuLevelId
import com.luics415.biogesture.menu.RadialMenuController
import com.luics415.biogesture.menu.RadialMenuEvent
import com.luics415.biogesture.menu.RadialMenuInput
import com.luics415.biogesture.menu.RadialMenuPhase
import com.luics415.biogesture.menu.RadialMenuSnapshot
import com.luics415.biogesture.menu.ScalarInsets
import com.luics415.biogesture.menu.ScalarPoint
import com.luics415.biogesture.menu.ScalarViewport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BioGestureService : AccessibilityService(), LifecycleOwner, HandTrackingPipeline.Listener {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BioGesture-Vision").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var preferences: BioGesturePreferences
    private lateinit var overlayLayout: FrameLayout
    private lateinit var calibrationPreviewView: ImageView
    private lateinit var anchorView: ImageView
    private lateinit var radialMenuView: RadialMenuView
    private lateinit var skeletonView: SkeletonView
    private lateinit var statusView: TextView
    private lateinit var gestureDispatcher: AccessibilityGestureDispatcher

    private val coordinateMapper = ScreenCoordinateMapper()
    private val pointerFilter = AdaptivePointerFilter()
    private val menuPointerFilter = AdaptivePointerFilter()
    private val gestureEngine = GestureEngine()
    private val radialMenuController = RadialMenuController()

    private var trackingPipeline: HandTrackingPipeline? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var supportedCaptureRanges: List<CaptureFpsRange>? = null
    private var boundCaptureRange: CaptureFpsRange? = null
    private var lastCaptureBudget = -1
    private var cameraRetryAttempt = 0
    private var pendingCameraRetry: Runnable? = null
    private var orientationListener: OrientationEventListener? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var serviceGeneration = 0
    private var destroyed = false
    private var cameraTargetRotation = Surface.ROTATION_0

    @Volatile
    private var coordinateEpoch = 0L

    private var screenWidth = 1
    private var screenHeight = 1
    private var screenInsets = ScreenInsets()
    private var userPaused = false
    private var handVisible = false
    private var volumeBeforeMute = 0
    @Volatile
    private var calibrationSession: CalibrationAccumulator? = null
    private var calibrationPreviewBitmap: android.graphics.Bitmap? = null
    private var calibrationPreviewWidth = 0
    private var calibrationPreviewHeight = 0

    private var isMenuOpen = false
    private var renderedMenuLevel: MenuLevelId? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        preferences = BioGesturePreferences(this)
        gestureDispatcher = AccessibilityGestureDispatcher(this, mainHandler) {
            handleUnexpectedDragEnd()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceGeneration += 1
        destroyed = false
        startForegroundNotification()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        try {
            clearCameraRetryState()
            updateCameraTargetRotation(currentDisplayRotation())
            updateScreenGeometry(cancelActiveInteraction = false)
            setupOverlay()
            setupTrackingPipeline()
            registerOrientationListener()
            registerThermalListener()
            startCamera(serviceGeneration)
            showToast("BioGesture listo")
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start BioGesture", error)
            showToast("No fue posible iniciar BioGesture")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraTargetRotation(currentDisplayRotation())
        updateScreenGeometry(cancelActiveInteraction = true)
    }

    private fun setupOverlay() {
        if (::overlayLayout.isInitialized && overlayLayout.isAttachedToWindow) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

        overlayLayout = FrameLayout(this)
        calibrationPreviewView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            contentDescription = getString(R.string.calibration_preview_description)
        }
        overlayLayout.addView(
            calibrationPreviewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        skeletonView = SkeletonView(this).apply { visibility = View.GONE }
        overlayLayout.addView(
            skeletonView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        radialMenuView = RadialMenuView(this).apply { visibility = View.GONE }
        overlayLayout.addView(
            radialMenuView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 13, 92, 189))
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
        }
        overlayLayout.addView(
            statusView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(24) },
        )

        val anchorSize = dp(ANCHOR_SIZE_DP)
        anchorView = ImageView(this).apply {
            setImageResource(R.drawable.ic_anchor_pointer)
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.INVISIBLE
            contentDescription = getString(R.string.anchor_preview_description)
        }
        overlayLayout.addView(anchorView, FrameLayout.LayoutParams(anchorSize, anchorSize))
        windowManager.addView(overlayLayout, params)
    }

    private fun setupTrackingPipeline() {
        trackingPipeline?.close()
        trackingPipeline = HandTrackingPipeline(
            context = this,
            profile = preferences.performanceProfile,
            listener = this,
        ).also { pipeline ->
            pipeline.setPaused(userPaused)
            pipeline.setThermalStatus(powerManager.currentThermalStatus)
            pipeline.setCoordinateEpoch(coordinateEpoch)
        }
        coordinateMapper.setMirrored(preferences.mirrorMovement)
        coordinateMapper.updateInputBounds(preferences.calibrationBounds(isLandscape()))
    }

    private fun startCamera(generation: Int) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (destroyed || generation != serviceGeneration) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                lastCaptureBudget = -1
                bindCameraAnalysis(provider)
            } catch (error: Throwable) {
                handleCameraBindingFailure("Camera initialization failed", error)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraAnalysis(provider: ProcessCameraProvider) {
        val pipeline = trackingPipeline ?: return
        val budget = pipeline.effectiveFps()
        val requestedRange = CaptureFrameRateSelector.select(budget, captureRanges())
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        boundCaptureRange = null
        provider.unbindAll()

        try {
            imageAnalysis = bindCameraAnalysisOnce(provider, pipeline, requestedRange)
            boundCaptureRange = requestedRange
        } catch (rangeError: Throwable) {
            if (requestedRange == null) throw rangeError
            Log.w(TAG, "Camera rejected FPS range $requestedRange; using device default", rangeError)
            provider.unbindAll()
            imageAnalysis = bindCameraAnalysisOnce(provider, pipeline, null)
            boundCaptureRange = null
        }
        lastCaptureBudget = budget
        clearCameraRetryState()
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraAnalysisOnce(
        provider: ProcessCameraProvider,
        pipeline: HandTrackingPipeline,
        captureRange: CaptureFpsRange?,
    ): ImageAnalysis {
        val builder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .setTargetRotation(cameraTargetRotation)
        captureRange?.let { fps ->
            Camera2Interop.Extender(builder).setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(fps.lower, fps.upper),
            )
        }
        val analyzer = builder.build()
        analyzer.setAnalyzer(analysisExecutor, pipeline)
        return try {
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analyzer)
            analyzer
        } catch (error: Throwable) {
            analyzer.clearAnalyzer()
            throw error
        }
    }

    private fun captureRanges(): List<CaptureFpsRange> {
        supportedCaptureRanges?.let { return it }
        val ranges = runCatching {
            val manager = getSystemService(CameraManager::class.java)
            val frontId = manager.cameraIdList.firstOrNull { cameraId ->
                manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return@runCatching emptyList()
            manager.getCameraCharacteristics(frontId)
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                .orEmpty()
                .map { range -> CaptureFpsRange(range.lower, range.upper) }
                .distinct()
        }.getOrElse { error ->
            Log.w(TAG, "Unable to query camera FPS ranges", error)
            emptyList()
        }
        supportedCaptureRanges = ranges
        return ranges
    }

    private fun ensureCaptureFrameRateBudget() {
        val provider = cameraProvider ?: return
        val pipeline = trackingPipeline ?: return
        val budget = pipeline.effectiveFps()
        if (budget == lastCaptureBudget) return
        val desiredRange = CaptureFrameRateSelector.select(budget, captureRanges())
        if (desiredRange == boundCaptureRange) {
            lastCaptureBudget = budget
            return
        }
        runCatching { bindCameraAnalysis(provider) }
            .onFailure { error ->
                handleCameraBindingFailure("Unable to update camera FPS budget", error)
            }
    }

    private fun handleCameraBindingFailure(message: String, error: Throwable) {
        Log.e(TAG, message, error)
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        boundCaptureRange = null
        lastCaptureBudget = -1
        gestureDispatcher.endDrag()
        resetGestureTracking()
        handVisible = false
        if (::anchorView.isInitialized) {
            anchorView.clearColorFilter()
            anchorView.visibility = if (userPaused) View.VISIBLE else View.INVISIBLE
        }
        if (::skeletonView.isInitialized) {
            skeletonView.clear()
            skeletonView.visibility = View.GONE
        }
        if (cameraRetryAttempt == 0) {
            showToast("Cámara interrumpida · reintentando")
        }
        scheduleCameraRetry()
    }

    private fun scheduleCameraRetry() {
        if (destroyed || pendingCameraRetry != null) return
        if (cameraRetryAttempt >= MAX_CAMERA_RETRY_ATTEMPTS) {
            showToast("No se pudo recuperar la cámara frontal")
            return
        }

        val generation = serviceGeneration
        val attempt = cameraRetryAttempt + 1
        cameraRetryAttempt = attempt
        val retry = Runnable {
            pendingCameraRetry = null
            if (destroyed || generation != serviceGeneration) return@Runnable
            val provider = cameraProvider
            if (provider == null) {
                startCamera(generation)
            } else {
                runCatching { bindCameraAnalysis(provider) }
                    .onFailure { error ->
                        handleCameraBindingFailure("Camera retry $attempt failed", error)
                    }
            }
        }
        pendingCameraRetry = retry
        mainHandler.postDelayed(
            retry,
            CAMERA_RETRY_BASE_DELAY_MS * (1L shl (attempt - 1)),
        )
    }

    private fun clearCameraRetryState() {
        pendingCameraRetry?.let(mainHandler::removeCallbacks)
        pendingCameraRetry = null
        cameraRetryAttempt = 0
    }

    override fun onResult(result: HandLandmarkerResult, metadata: FrameMetadata) {
        if (destroyed || metadata.coordinateEpoch != coordinateEpoch) {
            metadata.previewBitmap?.recycle()
            return
        }
        val landmarkList = result.landmarks().firstOrNull()
        val pose = landmarkList
            ?.takeIf { it.size == HandPose.LANDMARK_COUNT }
            ?.map { landmark ->
                LandmarkCoordinateTransform.toDisplay(
                    Point3(landmark.x(), landmark.y(), landmark.z()),
                    metadata.rotationDegrees,
                )
            }
            ?.let { points -> HandPose(points, metadata.timestampMs) }
        val calibrationWasActive = calibrationSession != null
        val effects = when {
            calibrationWasActive -> emptyList()
            pose == null -> gestureEngine.onHandLost(metadata.timestampMs)
            else -> gestureEngine.onHandPose(pose)
        }
        val handedness = if (pose == null) "Mano" else localizedHandedness(result)
        mainHandler.post {
            if (destroyed || metadata.coordinateEpoch != coordinateEpoch) {
                metadata.previewBitmap?.recycle()
                return@post
            }
            refreshRuntimePreferences()
            if (preferences.consumeCalibrationRequest() && calibrationSession == null) {
                startCalibration(metadata.timestampMs)
            }
            if (calibrationSession != null) {
                updateCalibrationPreview(metadata)
                if (pose == null) {
                    handVisible = false
                    skeletonView.clear()
                    skeletonView.visibility = View.GONE
                } else {
                    handVisible = true
                    renderDiagnostics(pose, handedness, metadata, forceVisible = true)
                }
                updateCalibration(pose, metadata.timestampMs)
                return@post
            }
            metadata.previewBitmap?.recycle()
            if (pose != null) renderDiagnostics(pose, handedness, metadata)
            applyEffects(effects, metadata.timestampMs)
        }
    }

    override fun onError(error: Throwable) {
        Log.e(TAG, "Vision pipeline error", error)
    }

    private fun renderDiagnostics(
        pose: HandPose,
        handedness: String,
        metadata: FrameMetadata,
        forceVisible: Boolean = false,
    ) {
        if ((!preferences.showLandmarks && !forceVisible) || (userPaused && !forceVisible)) {
            skeletonView.visibility = View.GONE
            skeletonView.clear()
            return
        }
        val mapped = if (forceVisible && calibrationPreviewWidth > 0 && calibrationPreviewHeight > 0) {
            pose.landmarks.map(::mapToCalibrationPreview)
        } else {
            pose.landmarks.map(coordinateMapper::map)
        }
        skeletonView.setPoints(mapped, handedness, metadata.inferenceTimeMs)
        skeletonView.visibility = View.VISIBLE
    }

    private fun applyEffects(effects: List<GestureEffect>, timestampMs: Long) {
        effects.forEach { effect ->
            when (effect) {
                is GestureEffect.CursorMoved -> moveAnchor(effect.indexTip, timestampMs)
                is GestureEffect.Tap -> {
                    val point = coordinateMapper.map(effect.position)
                    gestureDispatcher.tap(point)
                    flashAnchor(CURSOR_CLICK_COLOR)
                }
                is GestureEffect.DragStarted -> {
                    val point = coordinateMapper.map(effect.position)
                    pointerFilter.reset(point, timestampMs)
                    if (gestureDispatcher.startDrag(point)) {
                        anchorView.setColorFilter(CURSOR_DRAG_COLOR)
                    } else {
                        anchorView.clearColorFilter()
                        cancelGestureEngineInteraction()
                    }
                }
                is GestureEffect.DragMoved -> {
                    val point = pointerFilter.filter(coordinateMapper.map(effect.position), timestampMs)
                    positionAnchor(point)
                    gestureDispatcher.moveDrag(point)
                }
                is GestureEffect.DragEnded -> {
                    val point = coordinateMapper.map(effect.position)
                    if (effect.reason == com.luics415.biogesture.gesture.DragEndReason.EXTERNAL_CANCEL) {
                        gestureDispatcher.endDrag()
                    } else {
                        gestureDispatcher.endDrag(point)
                    }
                    anchorView.clearColorFilter()
                }
                is GestureEffect.ContextClick -> {
                    val point = coordinateMapper.map(effect.position)
                    performContextAction(point)
                    flashAnchor(CURSOR_CONTEXT_COLOR)
                }
                is GestureEffect.MenuOpened -> openRadialMenu(
                    coordinateMapper.map(effect.anchor),
                    timestampMs,
                )
                is GestureEffect.MenuPointerMoved -> updateRadialMenu(
                    coordinateMapper.map(effect.thumbTip),
                    timestampMs,
                )
                is GestureEffect.MenuClosed -> closeRadialMenu()
                GestureEffect.PauseActivated -> enterUserPause()
                GestureEffect.PauseDeactivated -> exitUserPause()
                GestureEffect.HandTrackingLost -> {
                    handVisible = false
                    anchorView.visibility = if (userPaused) View.VISIBLE else View.INVISIBLE
                    skeletonView.clear()
                    skeletonView.visibility = View.GONE
                }
                GestureEffect.HandTrackingResumed -> {
                    handVisible = true
                    if (!userPaused && !isMenuOpen) anchorView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun moveAnchor(point: Point3, timestampMs: Long) {
        if (userPaused || isMenuOpen) return
        val mapped = pointerFilter.filter(coordinateMapper.map(point), timestampMs)
        positionAnchor(mapped)
        if (handVisible) anchorView.visibility = View.VISIBLE
    }

    private fun positionAnchor(point: PointF) {
        val size = anchorView.layoutParams.width.toFloat()
        anchorView.x = point.x - size * ANCHOR_HOTSPOT_X
        anchorView.y = point.y - size * ANCHOR_HOTSPOT_Y
    }

    private fun flashAnchor(color: Int) {
        anchorView.setColorFilter(color)
        mainHandler.postDelayed({
            if (::anchorView.isInitialized && !gestureDispatcher.isDragging) {
                anchorView.clearColorFilter()
            }
        }, CURSOR_FEEDBACK_MS)
    }

    private fun handleUnexpectedDragEnd() {
        anchorView.clearColorFilter()
        cancelGestureEngineInteraction()
    }

    private fun cancelGestureEngineInteraction() {
        if (destroyed || analysisExecutor.isShutdown) return
        analysisExecutor.execute {
            val timestamp = SystemClock.uptimeMillis()
            val effects = gestureEngine.cancelActiveInteraction(timestamp)
            mainHandler.post {
                if (!destroyed) applyEffects(effects, timestamp)
            }
        }
    }

    private fun resetGestureTracking() {
        if (destroyed || analysisExecutor.isShutdown) return
        analysisExecutor.execute {
            val timestamp = SystemClock.uptimeMillis()
            val effects = gestureEngine.resetTracking(timestamp)
            mainHandler.post {
                if (!destroyed) applyEffects(effects, timestamp)
            }
        }
    }

    private fun enterUserPause() {
        userPaused = true
        trackingPipeline?.setPaused(true)
        ensureCaptureFrameRateBudget()
        closeRadialMenu()
        gestureDispatcher.endDrag()
        anchorView.alpha = 0.28f
        anchorView.visibility = View.VISIBLE
        skeletonView.visibility = View.GONE
        showToast("Modo reposo · mantén V 3 s para reactivar")
    }

    private fun exitUserPause() {
        userPaused = false
        trackingPipeline?.setPaused(false)
        ensureCaptureFrameRateBudget()
        anchorView.alpha = 1f
        anchorView.visibility = if (handVisible) View.VISIBLE else View.INVISIBLE
        pointerFilter.reset()
        showToast("BioGesture activo")
    }

    private fun openRadialMenu(anchor: PointF, timestampMs: Long) {
        gestureDispatcher.endDrag()
        isMenuOpen = true
        renderedMenuLevel = null
        menuPointerFilter.reset(anchor, timestampMs)
        anchorView.visibility = View.GONE
        radialMenuView.visibility = View.VISIBLE
        val snapshot = radialMenuController.openPrincipal(
            timestampMillis = timestampMs,
            anchor = ScalarPoint(anchor.x.toDouble(), anchor.y.toDouble()),
            viewport = currentMenuViewport(),
        )
        renderRadialMenu(snapshot, anchor)
    }

    private fun updateRadialMenu(thumb: PointF, timestampMs: Long) {
        if (!isMenuOpen) return
        val filteredThumb = menuPointerFilter.filter(thumb, timestampMs)
        val snapshot = radialMenuController.update(
            RadialMenuInput(
                timestampMillis = timestampMs,
                activationPoseDetected = true,
                pointer = ScalarPoint(filteredThumb.x.toDouble(), filteredThumb.y.toDouble()),
                viewport = currentMenuViewport(),
            ),
        )
        renderRadialMenu(snapshot, filteredThumb)
    }

    private fun closeRadialMenu(releaseGestureEngine: Boolean = false) {
        if (!::radialMenuView.isInitialized) return
        isMenuOpen = false
        renderedMenuLevel = null
        menuPointerFilter.reset()
        radialMenuController.reset()
        radialMenuView.highlightSector(-1)
        radialMenuView.setInteractionState(0f, 0f, selectionArmed = true)
        radialMenuView.visibility = View.GONE
        anchorView.visibility = if (handVisible && !userPaused) View.VISIBLE else View.INVISIBLE
        if (releaseGestureEngine) finishMenuGestureInteraction()
    }

    private fun finishMenuGestureInteraction() {
        if (destroyed || analysisExecutor.isShutdown) return
        analysisExecutor.execute {
            gestureEngine.finishMenuInteraction(SystemClock.uptimeMillis())
        }
    }

    private fun renderRadialMenu(snapshot: RadialMenuSnapshot, pointer: PointF) {
        if (snapshot.phase != RadialMenuPhase.OPEN) {
            if (snapshot.event is RadialMenuEvent.Closed) {
                closeRadialMenu(releaseGestureEngine = true)
            }
            return
        }
        val layout = snapshot.layout ?: return
        val level = snapshot.level ?: return
        radialMenuView.setMenuRadius(layout.radius.toFloat())
        if (renderedMenuLevel != level) {
            renderedMenuLevel = level
            radialMenuView.openMenu(
                level,
                layout.center.x.toFloat(),
                layout.center.y.toFloat(),
            )
        }
        radialMenuView.updateThumbPosition(pointer.x, pointer.y)
        radialMenuView.highlightSector(snapshot.highlightedIndex ?: -1)
        radialMenuView.setInteractionState(
            deadZoneRadius = layout.deadZoneRadius.toFloat(),
            dwellProgress = snapshot.dwellProgress.toFloat(),
            selectionArmed = snapshot.selectionArmed,
        )

        when (val event = snapshot.event) {
            is RadialMenuEvent.ActionSelected -> executeMenuAction(event.action)
            is RadialMenuEvent.Closed -> closeRadialMenu(releaseGestureEngine = true)
            else -> Unit
        }
    }

    private fun executeMenuAction(action: MenuActionId) {
        when (action) {
            MenuActionId.TOGGLE_HAND_SKELETON -> {
                preferences.showLandmarks = !preferences.showLandmarks
                showToast(if (preferences.showLandmarks) "Diagnóstico activo" else "Diagnóstico oculto")
            }
            MenuActionId.OPEN_SETTINGS -> startActivity(Intent(Settings.ACTION_SETTINGS).newTask())
            MenuActionId.OPEN_APP_PERMISSIONS -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:$packageName".toUri())
                    .newTask(),
            )
            MenuActionId.RESET_CALIBRATION -> startCalibration(SystemClock.uptimeMillis())
            MenuActionId.COPY -> performEditAction(AccessibilityNodeInfo.ACTION_COPY)
            MenuActionId.PASTE -> performEditAction(AccessibilityNodeInfo.ACTION_PASTE)
            MenuActionId.SELECT_ALL -> selectAllText()
            MenuActionId.CUT -> performEditAction(AccessibilityNodeInfo.ACTION_CUT)
            MenuActionId.WEB_BACK,
            MenuActionId.SYSTEM_BACK,
            -> performGlobalAction(GLOBAL_ACTION_BACK)
            MenuActionId.WEB_FORWARD -> showToast("Adelante depende de la aplicación")
            MenuActionId.SCROLL_UP -> performScroll(up = true)
            MenuActionId.SCROLL_DOWN -> performScroll(up = false)
            MenuActionId.NEW_TAB -> startActivity(
                Intent(Intent.ACTION_VIEW, "https://google.com".toUri()).newTask(),
            )
            MenuActionId.REFRESH -> clickNodeByText(setOf("Recargar", "Actualizar", "Reload", "Refresh"))
            MenuActionId.CLOSE_TAB -> performGlobalAction(GLOBAL_ACTION_BACK)
            MenuActionId.MEDIA_PLAY_PAUSE -> injectMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MenuActionId.MEDIA_NEXT -> injectMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            MenuActionId.MEDIA_PREVIOUS -> injectMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MenuActionId.MEDIA_FULLSCREEN -> performFullscreenGesture()
            MenuActionId.MEDIA_FORWARD_10_SECONDS -> performDoubleTapSide(isRight = true)
            MenuActionId.MEDIA_BACK_10_SECONDS -> performDoubleTapSide(isRight = false)
            MenuActionId.MEDIA_PLAY -> injectMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            MenuActionId.MEDIA_PAUSE -> injectMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            MenuActionId.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            MenuActionId.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            MenuActionId.VOLUME_MUTE -> toggleMute()
            MenuActionId.SYSTEM_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            MenuActionId.SYSTEM_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            MenuActionId.SYSTEM_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    private fun currentMenuViewport(): ScalarViewport = ScalarViewport(
        width = screenWidth.toDouble(),
        height = screenHeight.toDouble(),
        insets = ScalarInsets(
            left = screenInsets.left.toDouble(),
            top = screenInsets.top.toDouble(),
            right = screenInsets.right.toDouble(),
            bottom = screenInsets.bottom.toDouble(),
        ),
    )

    private fun performContextAction(point: PointF) {
        val root = rootInActiveWindow
        val target = root?.let { findBestNodeAt(it, point) }
        val accepted = when {
            target == null -> false
            target.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK) ->
                target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id)
            target.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK) ->
                target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            else -> false
        }
        if (!accepted) gestureDispatcher.longPress(point)
    }

    private fun findBestNodeAt(root: AccessibilityNodeInfo, point: PointF): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.contains(point.x.toInt(), point.y.toInt())) {
                val supportsAction = node.actionList.any { action ->
                    action == AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK ||
                        action == AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK
                }
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (supportsAction && area in 1 until bestArea) {
                    best = node
                    bestArea = area
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return best
    }

    private fun performEditAction(action: Int) {
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (focus == null) {
            showToast("Sin foco de texto")
        } else if (!focus.performAction(action)) {
            showToast("Acción no permitida")
        }
    }

    private fun selectAllText() {
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val length = focus?.text?.length ?: 0
        if (focus == null || length == 0) {
            showToast("Sin texto para seleccionar")
            return
        }
        val arguments = android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
        }
        if (!focus.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)) {
            showToast("Selección no permitida")
        }
    }

    private fun clickNodeByText(labels: Set<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        labels.forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.isClickable && it.isVisibleToUser }
                ?.let { return it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }
        showToast("Acción no disponible en esta aplicación")
        return false
    }

    private fun adjustVolume(direction: Int) {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val delta = if (direction == AudioManager.ADJUST_RAISE) 1 else -1
        val target = MediaVolumeLevel.stepTarget(current, minimum, maximum, delta)
        setMediaVolume(target, current, direction)
    }

    private fun toggleMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (current > minimum) {
            volumeBeforeMute = current
            setMediaVolume(minimum, current, AudioManager.ADJUST_MUTE)
        } else {
            val target = MediaVolumeLevel.restoreTarget(
                saved = volumeBeforeMute,
                minimum = minimum,
                maximum = maximum,
            )
            setMediaVolume(target, current, AudioManager.ADJUST_UNMUTE)
        }
    }

    private fun setMediaVolume(target: Int, previous: Int, fallbackDirection: Int) {
        if (audioManager.isVolumeFixed) {
            showToast("El volumen está bloqueado por el sistema")
            return
        }
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target,
                AudioManager.FLAG_SHOW_UI,
            )
            mainHandler.postDelayed({
                if (destroyed) return@postDelayed
                var actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (actual == previous && target != previous) {
                    audioManager.adjustSuggestedStreamVolume(
                        fallbackDirection,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
                val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val percentage = MediaVolumeLevel.percentage(actual, minimum, maximum)
                if (actual == previous && target != previous) {
                    Log.w(TAG, "Android did not apply media volume target=$target current=$actual")
                    showToast("Android no permitió cambiar el volumen")
                } else {
                    showToast("Volumen multimedia $percentage %")
                }
            }, VOLUME_VERIFY_DELAY_MS)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to change media volume", error)
            showToast("Android no permitió cambiar el volumen")
        }
    }

    private fun injectMediaKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(downTime, downTime, android.view.KeyEvent.ACTION_DOWN, keyCode, 0),
        )
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(downTime, SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, keyCode, 0),
        )
    }

    private fun performDoubleTapSide(isRight: Boolean) {
        val bounds = coordinateMapper.bounds()
        gestureDispatcher.doubleTap(
            PointF(
                if (isRight) bounds.left + bounds.width() * 0.80f else bounds.left + bounds.width() * 0.20f,
                bounds.centerY(),
            ),
        )
    }

    private fun performFullscreenGesture() {
        val bounds = coordinateMapper.bounds()
        gestureDispatcher.swipe(
            PointF(bounds.centerX(), bounds.centerY() + dp(120f)),
            PointF(bounds.centerX(), bounds.centerY() - dp(180f)),
            240L,
        )
    }

    private fun performScroll(up: Boolean) {
        val bounds = coordinateMapper.bounds()
        val offset = bounds.height() * 0.28f
        val from = PointF(bounds.centerX(), bounds.centerY() + if (up) -offset else offset)
        val to = PointF(bounds.centerX(), bounds.centerY() + if (up) offset else -offset)
        gestureDispatcher.swipe(from, to, 420L)
    }

    private fun localizedHandedness(result: HandLandmarkerResult): String {
        val raw = result.handedness().firstOrNull()?.firstOrNull()?.categoryName().orEmpty()
        return HandednessLabel.inSpanish(raw)
    }

    private fun startCalibration(timestampMs: Long) {
        calibrationSession = CalibrationAccumulator(
            startedAtMs = timestampMs,
            landscape = isLandscape(),
        )
        trackingPipeline?.setPreviewEnabled(true)
        closeRadialMenu()
        gestureDispatcher.endDrag()
        cancelGestureEngineInteraction()
        pointerFilter.reset()
        statusView.setText(R.string.calibration_overlay_start)
        statusView.visibility = View.VISIBLE
        calibrationPreviewView.scaleX = if (preferences.mirrorMovement) -1f else 1f
        calibrationPreviewView.visibility = View.VISIBLE
        anchorView.visibility = View.VISIBLE
        showToast("Mueve el índice por las cuatro esquinas durante 6 segundos")
    }

    private fun updateCalibration(pose: HandPose?, timestampMs: Long) {
        val session = calibrationSession ?: return
        if (pose != null) {
            val index = pose[com.luics415.biogesture.gesture.HandLandmark.INDEX_TIP]
            session.include(index)
            val rawMapped = if (calibrationPreviewWidth > 0 && calibrationPreviewHeight > 0) {
                mapToCalibrationPreview(index)
            } else {
                coordinateMapper.map(index)
            }
            val mapped = pointerFilter.filter(rawMapped, timestampMs)
            positionAnchor(mapped)
            anchorView.visibility = View.VISIBLE
        } else {
            anchorView.visibility = View.INVISIBLE
        }
        val elapsed = timestampMs - session.startedAtMs
        val secondsLeft = ((CALIBRATION_DURATION_MS - elapsed).coerceAtLeast(0L) + 999L) / 1_000L
        statusView.text = if (pose == null) {
            getString(R.string.calibration_overlay_no_hand, secondsLeft)
        } else {
            getString(R.string.calibration_overlay_progress, secondsLeft)
        }
        if (elapsed < CALIBRATION_DURATION_MS) return

        cancelCalibration()
        val observed = session.robustBounds(CALIBRATION_OUTLIER_FRACTION)
        val horizontalSpan = observed?.let { it.maxX - it.minX } ?: 0f
        val verticalSpan = observed?.let { it.maxY - it.minY } ?: 0f
        if (
            observed != null &&
            session.sampleCount >= CALIBRATION_MIN_SAMPLES &&
            horizontalSpan >= CALIBRATION_MIN_SPAN &&
            verticalSpan >= CALIBRATION_MIN_SPAN
        ) {
            preferences.saveCalibration(
                InputCalibrationBounds(
                    minX = (observed.minX - CALIBRATION_PADDING).coerceIn(0f, 1f),
                    maxX = (observed.maxX + CALIBRATION_PADDING).coerceIn(0f, 1f),
                    minY = (observed.minY - CALIBRATION_PADDING).coerceIn(0f, 1f),
                    maxY = (observed.maxY + CALIBRATION_PADDING).coerceIn(0f, 1f),
                ),
                landscape = session.landscape,
            )
            refreshRuntimePreferences()
            showToast("Calibración guardada")
        } else {
            refreshRuntimePreferences()
            showToast("Movimiento insuficiente; se conservó la calibración anterior")
        }
        anchorView.visibility = if (userPaused || handVisible) View.VISIBLE else View.INVISIBLE
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.execute {
                val cancelEffects = gestureEngine.cancelActiveInteraction(SystemClock.uptimeMillis())
                mainHandler.post { applyEffects(cancelEffects, SystemClock.uptimeMillis()) }
            }
        }
    }

    private fun refreshRuntimePreferences() {
        coordinateMapper.setMirrored(preferences.mirrorMovement)
        coordinateMapper.updateInputBounds(preferences.calibrationBounds(isLandscape()))
        trackingPipeline?.setProfile(preferences.performanceProfile)
        ensureCaptureFrameRateBudget()
    }

    private fun updateCalibrationPreview(metadata: FrameMetadata) {
        val preview = metadata.previewBitmap ?: return
        val previous = calibrationPreviewBitmap
        calibrationPreviewBitmap = preview
        calibrationPreviewWidth = preview.width
        calibrationPreviewHeight = preview.height
        calibrationPreviewView.scaleX = if (preferences.mirrorMovement) -1f else 1f
        calibrationPreviewView.setImageBitmap(preview)
        previous?.recycle()
    }

    private fun cancelCalibration(message: String? = null) {
        calibrationSession = null
        trackingPipeline?.setPreviewEnabled(false)
        if (::statusView.isInitialized) statusView.visibility = View.GONE
        if (::calibrationPreviewView.isInitialized) {
            calibrationPreviewView.setImageDrawable(null)
            calibrationPreviewView.visibility = View.GONE
        }
        calibrationPreviewBitmap?.recycle()
        calibrationPreviewBitmap = null
        calibrationPreviewWidth = 0
        calibrationPreviewHeight = 0
        if (::skeletonView.isInitialized) {
            skeletonView.clear()
            skeletonView.visibility = View.GONE
        }
        if (::anchorView.isInitialized) {
            anchorView.visibility = if (userPaused || handVisible) View.VISIBLE else View.INVISIBLE
        }
        message?.let(::showToast)
    }

    private fun mapToCalibrationPreview(point: Point3): PointF {
        val imageWidth = calibrationPreviewWidth.coerceAtLeast(1).toFloat()
        val imageHeight = calibrationPreviewHeight.coerceAtLeast(1).toFloat()
        val scale = minOf(screenWidth / imageWidth, screenHeight / imageHeight)
        val renderedWidth = imageWidth * scale
        val renderedHeight = imageHeight * scale
        val offsetX = (screenWidth - renderedWidth) / 2f
        val offsetY = (screenHeight - renderedHeight) / 2f
        val normalizedX = if (preferences.mirrorMovement) 1f - point.x else point.x
        return PointF(
            offsetX + normalizedX * renderedWidth,
            offsetY + point.y * renderedHeight,
        )
    }

    private fun updateScreenGeometry(cancelActiveInteraction: Boolean) {
        if (cancelActiveInteraction) {
            gestureDispatcher.endDrag()
            if (isMenuOpen) closeRadialMenu()
            if (calibrationSession != null) {
                cancelCalibration("La pantalla giró; inicia nuevamente la calibración")
            }
        }
        coordinateEpoch += 1L
        trackingPipeline?.setCoordinateEpoch(coordinateEpoch)

        val insets: ScreenInsets
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            val systemInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            insets = ScreenInsets(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            insets = ScreenInsets()
        }
        screenInsets = insets
        coordinateMapper.updateScreen(screenWidth, screenHeight, insets)
        coordinateMapper.updateInputBounds(preferences.calibrationBounds(isLandscape()))
        pointerFilter.reset()

        if (::overlayLayout.isInitialized && overlayLayout.isAttachedToWindow) {
            windowManager.updateViewLayout(overlayLayout, overlayLayout.layoutParams)
        }
        if (cancelActiveInteraction && !analysisExecutor.isShutdown) {
            analysisExecutor.execute {
                val effects = gestureEngine.cancelActiveInteraction(SystemClock.uptimeMillis())
                mainHandler.post { applyEffects(effects, SystemClock.uptimeMillis()) }
            }
        }
    }

    private fun registerOrientationListener() {
        orientationListener?.disable()
        orientationListener = object : OrientationEventListener(this) {
            private var lastRotation = currentDisplayRotation()

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = currentDisplayRotation()
                if (rotation != lastRotation) {
                    lastRotation = rotation
                    updateCameraTargetRotation(rotation)
                    updateScreenGeometry(cancelActiveInteraction = true)
                }
            }
        }.also { it.enable() }
    }

    private fun updateCameraTargetRotation(rotation: Int) {
        cameraTargetRotation = rotation
        imageAnalysis?.targetRotation = rotation
    }

    private fun currentDisplayRotation(): Int {
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    private fun registerThermalListener() {
        thermalListener?.let(powerManager::removeThermalStatusListener)
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trackingPipeline?.setThermalStatus(status)
            ensureCaptureFrameRateBudget()
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                mainHandler.post {
                    skeletonView.visibility = View.GONE
                    if (status >= PowerManager.THERMAL_STATUS_CRITICAL) {
                        showToast("Temperatura alta · carga reducida")
                    }
                }
            }
        }
        thermalListener = listener
        powerManager.addThermalStatusListener(ContextCompat.getMainExecutor(this), listener)
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "BioGesture",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle("BioGesture activo")
            .setContentText("Control por gestos usando la cámara frontal")
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                startForeground(1, notification)
                return
            }
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } catch (_: Throwable) {
            startForeground(1, notification)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun Intent.newTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun isLandscape(): Boolean = screenWidth > screenHeight

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        gestureDispatcher.endDrag()
        cancelGestureEngineInteraction()
        cancelCalibration()
        closeRadialMenu()
    }

    override fun onDestroy() {
        destroyed = true
        serviceGeneration += 1
        clearCameraRetryState()
        orientationListener?.disable()
        orientationListener = null
        thermalListener?.let(powerManager::removeThermalStatusListener)
        thermalListener = null

        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        boundCaptureRange = null
        lastCaptureBudget = -1
        gestureDispatcher.endDrag()
        cancelCalibration()

        val pipeline = trackingPipeline
        trackingPipeline = null
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.execute { pipeline?.close() }
            analysisExecutor.shutdown()
        } else {
            pipeline?.close()
        }

        if (::overlayLayout.isInitialized && overlayLayout.isAttachedToWindow) {
            windowManager.removeView(overlayLayout)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    companion object {
        private const val TAG = "BioGestureService"
        private const val NOTIFICATION_CHANNEL = "biogesture_channel"
        private const val ANCHOR_SIZE_DP = 28
        private const val ANCHOR_HOTSPOT_X = 44f / 48f
        private const val ANCHOR_HOTSPOT_Y = 23.5f / 48f
        private const val CURSOR_FEEDBACK_MS = 160L
        private const val VOLUME_VERIFY_DELAY_MS = 120L
        private const val CURSOR_CLICK_COLOR = Color.CYAN
        private val CURSOR_DRAG_COLOR = Color.rgb(33, 150, 243)
        private val CURSOR_CONTEXT_COLOR = Color.rgb(156, 39, 176)
        private const val CALIBRATION_DURATION_MS = 6_000L
        private const val CALIBRATION_MIN_SPAN = 0.35f
        private const val CALIBRATION_MIN_SAMPLES = 24
        private const val CALIBRATION_OUTLIER_FRACTION = 0.05f
        private const val CALIBRATION_PADDING = 0.02f
        private const val MAX_CAMERA_RETRY_ATTEMPTS = 3
        private const val CAMERA_RETRY_BASE_DELAY_MS = 500L
    }
}
