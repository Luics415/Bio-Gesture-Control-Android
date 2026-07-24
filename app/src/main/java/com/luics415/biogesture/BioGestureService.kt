package com.luics415.biogesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class BioGestureService : AccessibilityService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayLayout: FrameLayout
    private lateinit var cursorView: ImageView
    private lateinit var radialMenuView: RadialMenuView
    private lateinit var skeletonView: SkeletonView
    private lateinit var audioManager: AudioManager

    private var handLandmarker: HandLandmarker? = null

    // Dimensiones
    private var screenWidth = 0
    private var screenHeight = 0

    // Cursores y Suavizado
    private var prevX = 0f
    private var prevY = 0f
    private val smoothing = 5f

    // Tiempos
    private var lastClickTime = 0L
    private var lastActionTime = 0L

    // Estados
    private var isPaused = false
    private var victoryStartTime = 0L
    private var showSkeleton = false

    // Arrastre
    private var isDragging = false
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastDragPacketTime = 0L
    private var dragStartX = 0f
    private var dragStartY = 0f

    // Menú
    private var isMenuOpen = false
    private var currentMenuLevel = "PRINCIPAL"
    private var menuSectorStartTime = 0L
    private var currentSectorIndex = -1
    private var menuAnchorX = 0f
    private var menuAnchorY = 0f

    // MUTE MEMORIA
    private var volumeBeforeMute = 0

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundService()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        updateScreenDimensions()

        try {
            setupOverlay()
            setupMediaPipe()
            startCamera()
            showToast("BioGesture: Listo")
        } catch (e: Exception) {
            Log.e("BioGesture", "Error fatal", e)
        }
    }

    // --- FIX API 29/30: COMPATIBILIDAD DE PANTALLA ---
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        if (::overlayLayout.isInitialized) {
            windowManager.updateViewLayout(overlayLayout, overlayLayout.layoutParams)
        }
    }

    private fun updateScreenDimensions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Para Android 11+ (API 30+)
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            // Para Android 10 (API 29) y anteriores
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }
    // ---------------------------------------------------

    private fun startForegroundService() {
        val channelId = "biogesture_channel"
        val channel = NotificationChannel(channelId, "BioGesture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BioGesture Activo")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        try { startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) }
        catch (e: Exception) { startForeground(1, notification) }
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayLayout = FrameLayout(this)

        skeletonView = SkeletonView(this)
        overlayLayout.addView(skeletonView)

        radialMenuView = RadialMenuView(this)
        radialMenuView.visibility = View.GONE
        overlayLayout.addView(radialMenuView)

        cursorView = ImageView(this)
        cursorView.setImageResource(android.R.drawable.presence_online)
        cursorView.layoutParams = FrameLayout.LayoutParams(50, 50)
        overlayLayout.addView(cursorView)

        windowManager.addView(overlayLayout, params)
    }

    private fun recognizeAsync(imageProxy: ImageProxy) {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val buffer = imageProxy.planes[0].buffer
        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val matrix = Matrix(); matrix.postRotate(rotation.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        imageProxy.close()
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
    }

    private fun processHand(result: HandLandmarkerResult) {
        val t = System.currentTimeMillis()

        Handler(Looper.getMainLooper()).post {
            if (showSkeleton && result.landmarks().isNotEmpty()) {
                skeletonView.visibility = View.VISIBLE
                skeletonView.setResults(result)
            } else {
                skeletonView.visibility = View.GONE
                skeletonView.clear()
            }
        }

        if (result.landmarks().isEmpty()) return

        val hand = result.landmarks()[0]
        val thumb = hand[4]; val index = hand[8]; val middle = hand[12]; val ring = hand[16]; val pinky = hand[20]

        // --- MODO REPOSO (SOLICITADO) ---
        val isVictory = index.y() < hand[6].y() && middle.y() < hand[10].y() && ring.y() > hand[14].y() && pinky.y() > hand[18].y()

        if (isVictory) {
            if (victoryStartTime == 0L) victoryStartTime = t
            if (t - victoryStartTime > 3000) {
                isPaused = !isPaused
                victoryStartTime = t + 2000
                Handler(Looper.getMainLooper()).post {
                    val estado = if (isPaused) "MODO REPOSO" else "SISTEMA ACTIVO"
                    showToast(estado)
                    cursorView.alpha = if (isPaused) 0.2f else 1.0f
                }
            }
        } else {
            victoryStartTime = 0L
        }

        if (isPaused) return

        // --- COORDENADAS ---
        val cursorTargetX = (1f - index.x()) * screenWidth
        val cursorTargetY = index.y() * screenHeight
        val thumbX = (1f - thumb.x()) * screenWidth
        val thumbY = thumb.y() * screenHeight

        val cx = prevX + (cursorTargetX - prevX) / smoothing
        val cy = prevY + (cursorTargetY - prevY) / smoothing

        val isThumbUp = thumb.y() < index.y() && index.y() > hand[6].y()

        if (isThumbUp && !isDragging) {
            // --- MENÚ RADIAL ---
            if (!isMenuOpen) {
                isMenuOpen = true
                menuAnchorX = thumbX
                menuAnchorY = thumbY
                Handler(Looper.getMainLooper()).post {
                    cursorView.visibility = View.GONE
                    radialMenuView.visibility = View.VISIBLE
                    radialMenuView.openMenu(currentMenuLevel, menuAnchorX, menuAnchorY)
                }
            }
            val dx = thumbX - menuAnchorX
            val dy = thumbY - menuAnchorY
            Handler(Looper.getMainLooper()).post { radialMenuView.updateThumbPosition(thumbX, thumbY) }

            if (sqrt(dx*dx + dy*dy) > 50) {
                val angle = atan2(dy, dx) * (180 / PI)
                var correctedAngle = angle + 90
                if (correctedAngle < 0) correctedAngle += 360
                val sector = ((correctedAngle + 22.5) / 45).toInt() % 8
                Handler(Looper.getMainLooper()).post { radialMenuView.highlightSector(sector) }

                if (sector != currentSectorIndex) {
                    currentSectorIndex = sector
                    menuSectorStartTime = t
                } else {
                    if (t - menuSectorStartTime > 1200 && t - lastActionTime > 1000) {
                        executeMenuAction(currentSectorIndex)
                        menuSectorStartTime = t + 5000
                        lastActionTime = t
                    }
                }
            }
        } else {
            // --- CERRAR MENÚ ---
            if (isMenuOpen) {
                isMenuOpen = false
                currentSectorIndex = -1
                Handler(Looper.getMainLooper()).post {
                    radialMenuView.visibility = View.GONE
                    cursorView.visibility = View.VISIBLE
                }
            }

            Handler(Looper.getMainLooper()).post { cursorView.x = cx; cursorView.y = cy }

            val distDrag = sqrt((thumb.x() - middle.x()).pow(2) + (thumb.y() - middle.y()).pow(2))

            // --- ARRASTRE ---
            if (isDragging) {
                if (distDrag > 0.15) {
                    isDragging = false
                    Handler(Looper.getMainLooper()).post { cursorView.setColorFilter(null) }
                } else {
                    if (t - lastDragPacketTime > 70) {
                        performSwipe(dragStartX, dragStartY, cx, cy, 400L)
                        lastDragPacketTime = t
                        dragStartX = cx
                        dragStartY = cy
                    }
                }
            } else {
                if (distDrag < 0.04) {
                    isDragging = true
                    dragStartX = cx
                    dragStartY = cy
                    lastDragPacketTime = t
                    Handler(Looper.getMainLooper()).post { cursorView.setColorFilter(android.graphics.Color.BLUE) }
                }
            }

            prevX = cx; prevY = cy

            // --- CLICK ---
            if (!isDragging) {
                val distPinch = sqrt((thumb.x() - index.x()).pow(2) + (thumb.y() - index.y()).pow(2))
                if (distPinch < 0.04) {
                    if (t - lastClickTime > 400) {
                        performTap(cx, cy)
                        lastClickTime = t
                        Handler(Looper.getMainLooper()).post { cursorView.alpha = 0.5f }
                        Handler(Looper.getMainLooper()).postDelayed({ cursorView.alpha = 1.0f }, 150)
                    }
                }
            }
        }
    }

    private fun executeMenuAction(index: Int) {
        val option = radialMenuView.getOption(index)
        if (option == "NULL" || option == "") return

        showToast(option)

        when (option) {
            "VOLVER" -> currentMenuLevel = "PRINCIPAL"
            "CONFIG", "EDIT", "WEB", "MEDIA", "VOLUME", "NAV" -> currentMenuLevel = option

            "GESTOS" -> { showSkeleton = !showSkeleton; showToast("Esqueleto: $showSkeleton") }
            "AJUSTES" -> { val i = Intent(android.provider.Settings.ACTION_SETTINGS); i.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(i) }
            "PERMISOS" -> { val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.data = Uri.parse("package:$packageName"); i.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(i) }

            "COPIAR" -> performEditAction(AccessibilityNodeInfo.ACTION_COPY)
            "PEGAR" -> performEditAction(AccessibilityNodeInfo.ACTION_PASTE)
            "CORTAR" -> performEditAction(AccessibilityNodeInfo.ACTION_CUT)
            "TODO" -> performEditAction(AccessibilityNodeInfo.ACTION_SELECT)

            "INICIO", "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "ATRAS", "REGRESAR" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "ADELANTE" -> injectMediaKey(KeyEvent.KEYCODE_FORWARD)
            "RECARGAR" -> injectMediaKey(KeyEvent.KEYCODE_REFRESH)
            "CERRAR T" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "NUEVA T" -> { val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")); i.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(i) }
            "SCROLL UP" -> performScroll(true)
            "SCROLL DN" -> performScroll(false)

            "PLAY", "PLAY/PAUSE" -> injectMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
            "FULLSCREEN" -> performFullscreenGesture()
            "SIGUIENTE" -> injectMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "ANTERIOR" -> injectMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "ADELAN 10s" -> performDoubleTapSide(true)
            "ATRAS 10s" -> performDoubleTapSide(false)

            "SUBIR", "VOL+" -> forceAdjustVolume(AudioManager.ADJUST_RAISE)
            "BAJAR", "VOL-" -> forceAdjustVolume(AudioManager.ADJUST_LOWER)
            "MUTE" -> performManualMute()

            "RECIENTES" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIF" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }

        if (isMenuOpen) {
            Handler(Looper.getMainLooper()).post { radialMenuView.openMenu(currentMenuLevel, menuAnchorX, menuAnchorY) }
        }
    }

    private fun forceAdjustVolume(direction: Int) {
        Handler(Looper.getMainLooper()).post {
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_ACCESSIBILITY, direction, 0)
            } catch (e: Exception) { Log.e("BioGesture", "Fallo volumen", e) }
        }
    }

    private fun performManualMute() {
        Handler(Looper.getMainLooper()).post {
            try {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current > 0) {
                    volumeBeforeMute = current
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                } else {
                    val target = if (volumeBeforeMute > 0) volumeBeforeMute else 5
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                }
            } catch (e: Exception) { Log.e("BioGesture", "Error mute", e) }
        }
    }

    private fun performEditAction(action: Int) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (focus != null) {
            var result = focus.performAction(action)
            if (action == AccessibilityNodeInfo.ACTION_SELECT && !result) {
                val args = android.os.Bundle()
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 10000)
                result = focus.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
            }
            if (!result) showToast("Acción no permitida")
            focus.recycle()
        } else {
            showToast("Sin foco de texto")
        }
    }

    private fun performTap(x: Float, y: Float) {
        Handler(Looper.getMainLooper()).post {
            val path = Path(); path.moveTo(x, y)
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            dispatchGesture(builder.build(), null, null)
        }
    }

    private fun performDoubleTapSide(isRight: Boolean) {
        val cy = screenHeight / 2f
        val cx = if (isRight) screenWidth * 0.8f else screenWidth * 0.2f

        Handler(Looper.getMainLooper()).post {
            val path = Path(); path.moveTo(cx, cy)
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            val gesture = builder.build()
            dispatchGesture(gesture, null, null)
            Handler(Looper.getMainLooper()).postDelayed({
                dispatchGesture(gesture, null, null)
            }, 100)
        }
    }

    private fun performFullscreenGesture() {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        performSwipe(cx, cy + 200, cx, cy - 300, 200L)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        if (abs(x1 - x2) < 2 && abs(y1 - y2) < 2) return
        Handler(Looper.getMainLooper()).post {
            val path = Path(); path.moveTo(x1, y1); path.lineTo(x2, y2)
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            dispatchGesture(builder.build(), null, null)
        }
    }

    private fun performScroll(up: Boolean) {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val startY = if (up) cy - 300 else cy + 300
        val endY = if (up) cy + 300 else cy - 300
        performSwipe(cx, startY, cx, endY, 400L)
    }

    private fun injectMediaKey(keyCode: Int) {
        Handler(Looper.getMainLooper()).post {
            try {
                val eventDown = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
                val eventUp = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
                audioManager.dispatchMediaKeyEvent(eventDown)
                audioManager.dispatchMediaKeyEvent(eventUp)
            } catch (e: Exception) {
                val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                i.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                sendOrderedBroadcast(i, null)
                i.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                sendOrderedBroadcast(i, null)
            }
        }
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun setupMediaPipe() {
        val baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions).setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
            .setResultListener { result, _ -> processHand(result) }.build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy -> recognizeAsync(imageProxy) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalyzer)
            } catch (e: Exception) { Log.e("Bio", "Error cam", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}