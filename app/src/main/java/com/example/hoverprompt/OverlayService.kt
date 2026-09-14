package com.example.hoverprompt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.min

private data class OverlayConfig(
    val text: String,
    val mode: PromptMode,
    val speed: Float,
    val fontSize: Float,
    val lineSpacing: Float,
    val textColor: Int,
    val backgroundColor: Int,
    val opacity: Float,
    val loop: Boolean,
    val countdown: Boolean
)

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: PromptOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var configurationCallbacks: ComponentCallbacks? = null
    private var rotationQuarterTurns = 0
    private var expandedFrame: OverlayFrame? = null
    private var resizeStartX = 0
    private var resizeStartY = 0

    private data class OverlayFrame(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        configurationCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                relayoutForCurrentDisplay()
            }

            override fun onLowMemory() = Unit
        }
        configurationCallbacks?.let(::registerComponentCallbacks)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val savedSettings = PromptSettingsStore.load(this)
        val config = OverlayConfig(
            text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "输入一段台词，开始你的表达。" },
            mode = runCatching {
                intent?.getStringExtra(EXTRA_MODE)?.let(PromptMode::valueOf) ?: savedSettings.mode
            }.getOrDefault(savedSettings.mode),
            speed = intent?.getFloatExtra(EXTRA_SPEED, savedSettings.speed) ?: savedSettings.speed,
            fontSize = intent?.getFloatExtra(EXTRA_FONT_SIZE, savedSettings.fontSize) ?: savedSettings.fontSize,
            lineSpacing = intent?.getFloatExtra(EXTRA_LINE_SPACING, savedSettings.lineSpacing) ?: savedSettings.lineSpacing,
            textColor = intent?.getIntExtra(EXTRA_TEXT_COLOR, savedSettings.textColor.toArgb())
                ?: savedSettings.textColor.toArgb(),
            backgroundColor = intent?.getIntExtra(EXTRA_BACKGROUND_COLOR, savedSettings.backgroundColor.toArgb())
                ?: savedSettings.backgroundColor.toArgb(),
            opacity = intent?.getFloatExtra(EXTRA_OPACITY, savedSettings.opacity) ?: savedSettings.opacity,
            loop = intent?.getBooleanExtra(EXTRA_LOOP, savedSettings.loop) ?: savedSettings.loop,
            countdown = intent?.getBooleanExtra(EXTRA_COUNTDOWN, savedSettings.countdown) ?: savedSettings.countdown
        )
        persistConfig(config)

        if (overlayView == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val frame = calculateOverlayFrame()
            expandedFrame = frame
            val params = WindowManager.LayoutParams(
                frame.width,
                frame.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = frame.x
                y = frame.y
            }
            layoutParams = params
            overlayView = PromptOverlayView(
                context = this,
                config = config,
                onCollapse = { collapseOverlay() },
                onMove = { dx, dy -> moveOverlay(dx, dy) },
                onResizeStart = { beginResize() },
                onResize = { width, height, dx, dy -> resizeOverlay(width, height, dx, dy) },
                onRotate = { rotateOverlay() },
                onExpand = { expandOverlay() },
                onConfigChanged = { persistConfig(it) }
            )
            windowManager.addView(overlayView, params)
        } else {
            overlayView?.updateConfig(config)
        }

        return START_STICKY
    }

    private fun moveOverlay(dx: Int, dy: Int) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.x += dx
        params.y += dy
        clampWindowPosition(params)
        windowManager.updateViewLayout(view, params)
        expandedFrame = OverlayFrame(params.width, params.height, params.x, params.y)
    }

    private fun beginResize() {
        val params = layoutParams ?: return
        resizeStartX = params.x
        resizeStartY = params.y
    }

    private fun resizeOverlay(width: Int, height: Int, dx: Int, dy: Int) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.width = width
        params.height = height
        params.x = resizeStartX + dx
        params.y = resizeStartY + dy
        clampWindowPosition(params)
        windowManager.updateViewLayout(view, params)
        expandedFrame = OverlayFrame(params.width, params.height, params.x, params.y)
    }

    private fun clampWindowPosition(params: WindowManager.LayoutParams) {
        val (screenWidth, screenHeight) = currentDisplaySize()
        params.x = params.x.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
    }

    private fun persistConfig(config: OverlayConfig) {
        PromptSettingsStore.save(
            this,
            PromptSettings(
                mode = config.mode,
                speed = config.speed,
                fontSize = config.fontSize,
                lineSpacing = config.lineSpacing,
                textColor = Color(config.textColor),
                backgroundColor = Color(config.backgroundColor),
                opacity = config.opacity,
                loop = config.loop,
                countdown = config.countdown
            )
        )
    }

    private fun rotateOverlay() {
        rotationQuarterTurns = (rotationQuarterTurns + 1) % 4
        overlayView?.setRotationQuarterTurns(rotationQuarterTurns)
        relayoutForCurrentDisplay()
    }

    private fun collapseOverlay() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (view.isCollapsed) return

        expandedFrame = OverlayFrame(params.width, params.height, params.x, params.y)
        val bubble = calculateCollapsedFrame()
        params.width = bubble.width
        params.height = bubble.height
        params.x = bubble.x
        params.y = bubble.y
        view.setCollapsed(true)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun expandOverlay() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (!view.isCollapsed) return

        val frame = expandedFrame ?: calculateOverlayFrame()
        params.width = frame.width
        params.height = frame.height
        params.x = frame.x
        params.y = frame.y
        view.setCollapsed(false)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun relayoutForCurrentDisplay() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val frame = if (view.isCollapsed) calculateCollapsedFrame() else calculateOverlayFrame()
        params.width = frame.width
        params.height = frame.height
        params.x = frame.x
        params.y = frame.y
        if (!view.isCollapsed) expandedFrame = frame
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun calculateOverlayFrame(): OverlayFrame {
        val (screenWidth, screenHeight) = currentDisplaySize()
        val displayLandscape = screenWidth > screenHeight
        val landscape = if (rotationQuarterTurns % 2 == 0) displayLandscape else !displayLandscape
        val horizontalMargin = dp(24f)
        val verticalMargin = dp(24f)

        return if (landscape) {
            val width = min(dp(520f), (screenWidth - horizontalMargin).coerceAtLeast(1))
            val height = min(dp(250f), (screenHeight - verticalMargin).coerceAtLeast(1))
            OverlayFrame(
                width = width,
                height = height,
                x = horizontalMargin.coerceAtMost((screenWidth - width).coerceAtLeast(0)),
                y = ((screenHeight - height) / 2).coerceAtLeast(0)
            )
        } else {
            val width = min(dp(340f), (screenWidth - horizontalMargin).coerceAtLeast(1))
            val height = min(dp(310f), (screenHeight - verticalMargin).coerceAtLeast(1))
            OverlayFrame(
                width = width,
                height = height,
                x = dp(22f).coerceAtMost((screenWidth - width).coerceAtLeast(0)),
                y = dp(150f).coerceAtMost((screenHeight - height).coerceAtLeast(0))
            )
        }
    }

    private fun calculateCollapsedFrame(): OverlayFrame {
        val (screenWidth, screenHeight) = currentDisplaySize()
        val size = dp(58f).coerceAtMost(min(screenWidth, screenHeight).coerceAtLeast(1))
        return OverlayFrame(
            width = size,
            height = size,
            x = (screenWidth - size).coerceAtLeast(0),
            y = ((screenHeight - size) / 2).coerceAtLeast(0)
        )
    }

    private fun currentDisplaySize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    override fun onDestroy() {
        configurationCallbacks?.let { unregisterComponentCallbacks(it) }
        configurationCallbacks = null
        overlayView?.let { view ->
            if (::windowManager.isInitialized) windowManager.removeView(view)
        }
        overlayView = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮提词",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持悬浮提词窗口运行" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prompt)
            .setContentTitle("悬浮提词正在运行")
            .setContentText("可以切换到相机、抖音或其他录屏应用")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopIntent)
            .build()
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_FONT_SIZE = "extra_font_size"
        const val EXTRA_LINE_SPACING = "extra_line_spacing"
        const val EXTRA_TEXT_COLOR = "extra_text_color"
        const val EXTRA_BACKGROUND_COLOR = "extra_background_color"
        const val EXTRA_OPACITY = "extra_opacity"
        const val EXTRA_LOOP = "extra_loop"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val ACTION_UPDATE = "com.example.hoverprompt.UPDATE"
        const val ACTION_STOP = "com.example.hoverprompt.STOP"
        private const val CHANNEL_ID = "hover_prompt_running"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
    }
}

private class PromptOverlayView(
    context: Context,
    private var config: OverlayConfig,
    private val onCollapse: () -> Unit,
    private val onMove: (Int, Int) -> Unit,
    private val onResizeStart: () -> Unit,
    private val onResize: (Int, Int, Int, Int) -> Unit,
    private val onRotate: () -> Unit,
    private val onExpand: () -> Unit,
    private val onConfigChanged: (OverlayConfig) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelRect = RectF()
    private val settingsPanelRect = RectF()
    private val speedTrackRect = RectF()
    private val fontTrackRect = RectF()
    private val countdownToggleRect = RectF()
    private val settingsDoneRect = RectF()
    private var isPlaying = false
    private var isCountingDown = false
    private var countdownRemaining = 0
    private var countdownEndsAt = 0L
    private var showSettings = false
    var isCollapsed: Boolean = false
        private set
    private var rotationQuarterTurns = 0
    private var scrollOffset = 0f
    private var lastFrameTime = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downWidth = 0
    private var downHeight = 0
    private var scrollAtTouchStart = 0f
    private var didDragScript = false
    private var dragMode = DragMode.NONE

    private enum class DragMode {
        NONE,
        MOVE,
        RESIZE_LEFT,
        RESIZE_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM,
        CLOSE,
        SETTINGS_CLOSE,
        SETTINGS_SPEED,
        SETTINGS_FONT,
        SETTINGS_COUNTDOWN,
        SCRIPT_SCROLL
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        postInvalidateOnAnimation()
    }

    fun updateConfig(newConfig: OverlayConfig) {
        if (config.text != newConfig.text) scrollOffset = 0f
        config = newConfig
        invalidate()
    }

    fun setRotationQuarterTurns(value: Int) {
        rotationQuarterTurns = value % 4
        invalidate()
    }

    fun setCollapsed(value: Boolean) {
        isCollapsed = value
        if (value) {
            showSettings = false
            dragMode = DragMode.NONE
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isCountingDown) updateCountdown()

        if (isCollapsed) {
            drawCollapsedBubble(canvas)
            advancePlayback()
            return
        }

        canvas.save()
        applyContentTransform(canvas)
        val radius = dp(22f)
        panelRect.set(0f, 0f, contentWidth, contentHeight)

        backgroundPaint.color = withAlpha(config.backgroundColor, config.opacity)
        canvas.drawRoundRect(panelRect, radius, radius, backgroundPaint)

        drawTopBar(canvas)
        drawScript(canvas)
        drawBottomBar(canvas)
        if (showSettings) {
            drawSettingsPanel(canvas)
        } else if (isCountingDown) {
            drawCountdown(canvas)
        }
        canvas.restore()

        advancePlayback()
    }

    private fun advancePlayback() {
        if (isPlaying) {
            val now = System.nanoTime()
            if (lastFrameTime != 0L) {
                val deltaSeconds = (now - lastFrameTime) / 1_000_000_000f
                scrollOffset += config.speed * density * deltaSeconds
            }
            lastFrameTime = now
            postInvalidateOnAnimation()
        } else if (isCountingDown) {
            lastFrameTime = 0L
            postInvalidateOnAnimation()
        } else {
            lastFrameTime = 0L
        }
    }

    private fun drawCollapsedBubble(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        backgroundPaint.color = withAlpha(0xFF1A2A31.toInt(), .96f)
        canvas.drawCircle(centerX, centerY, min(width, height) / 2f - dp(3f), backgroundPaint)
        labelPaint.typeface = Typeface.create("sans", Typeface.BOLD)
        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(18f)
        drawCenteredText(canvas, "H", centerX, centerY + dp(6f), labelPaint)
        linePaint.color = 0x667EAD40
        linePaint.strokeWidth = dp(2f)
        canvas.drawCircle(centerX, centerY, min(width, height) / 2f - dp(5f), linePaint)
    }

    private fun applyContentTransform(canvas: Canvas) {
        when (rotationQuarterTurns) {
            1 -> {
                canvas.translate(width / 2f, height / 2f)
                canvas.rotate(90f)
                canvas.translate(-contentWidth / 2f, -contentHeight / 2f)
            }
            2 -> canvas.rotate(180f, width / 2f, height / 2f)
            3 -> {
                canvas.translate(width / 2f, height / 2f)
                canvas.rotate(-90f)
                canvas.translate(-contentWidth / 2f, -contentHeight / 2f)
            }
        }
    }

    private fun updateCountdown() {
        val remaining = ((countdownEndsAt - System.nanoTime() + 999_999_999L) / 1_000_000_000L).toInt()
        if (remaining <= 0) {
            isCountingDown = false
            isPlaying = true
            lastFrameTime = 0L
        } else {
            countdownRemaining = remaining
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB4BEC0.toInt() }
        val startX = contentWidth / 2f - dp(13f)
        for (row in 0..1) {
            for (column in 0..2) {
                canvas.drawCircle(startX + column * dp(13f), dp(14f) + row * dp(8f), dp(1.7f), dotPaint)
            }
        }

        labelPaint.typeface = Typeface.create("sans", Typeface.BOLD)
        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(12f)
        canvas.drawText("直播台词", dp(16f), dp(53f), labelPaint)

        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(9f)
        canvas.drawRoundRect(RectF(dp(86f), dp(40f), dp(142f), dp(59f)), dp(9f), dp(9f), labelPaint)
        labelPaint.color = 0xFF0B1014.toInt()
        canvas.drawText(
            when {
                isCountingDown -> "准备中"
                isPlaying -> "正在读"
                else -> config.mode.label
            },
            dp(91f),
            dp(53f),
            labelPaint
        )

        labelPaint.color = 0xFFD7DEDE.toInt()
        labelPaint.textSize = sp(23f)
        canvas.drawText("×", contentWidth - dp(28f), dp(27f), labelPaint)
        linePaint.color = 0x334E5C5E
        linePaint.strokeWidth = dp(1f)
        canvas.drawLine(dp(14f), dp(68f), contentWidth - dp(14f), dp(68f), linePaint)
    }

    private fun drawScript(canvas: Canvas) {
        val top = dp(82f)
        val bottom = contentHeight - dp(69f)
        canvas.save()
        canvas.clipRect(dp(14f), top, contentWidth - dp(14f), bottom)

        linePaint.color = 0xD6C4F76A.toInt()
        linePaint.strokeWidth = dp(1f)
        val guideY = top + (bottom - top) * .52f
        canvas.drawLine(dp(10f), guideY, contentWidth - dp(10f), guideY, linePaint)
        canvas.drawCircle(dp(10f), guideY, dp(3f), linePaint)

        textPaint.color = config.textColor
        textPaint.textSize = sp(config.fontSize)
        textPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        val lineHeight = textPaint.textSize * config.lineSpacing
        val lines = wrapLines(config.text.replace("\r", "").split("\n"), contentWidth - dp(28f))
        val maxScroll = maxScriptScrollOffset(lineHeight, lines.size, guideY, bottom)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        val startY = guideY - lineHeight * .55f - scrollOffset
        lines.forEachIndexed { index, line ->
            val y = startY + index * lineHeight
            if (y > top - lineHeight && y < bottom + lineHeight) {
                canvas.drawText(line, dp(14f), y, textPaint)
            }
        }
        if (!isPlaying && !isCountingDown) {
            val pillWidth = dp(132f)
            val pillHeight = dp(42f)
            val pillLeft = contentWidth / 2f - pillWidth / 2f
            val pillTop = guideY - pillHeight / 2f
            backgroundPaint.color = 0xFFC4F76A.toInt()
            canvas.drawRoundRect(
                RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight),
                dp(21f),
                dp(21f),
                backgroundPaint
            )
            labelPaint.color = 0xFF0B1014.toInt()
            labelPaint.textSize = sp(13f)
            labelPaint.typeface = Typeface.create("sans", Typeface.BOLD)
            canvas.drawText("▶  开始提词", pillLeft + dp(24f), guideY + dp(5f), labelPaint)
        }
        canvas.restore()

        if (isPlaying && maxScroll > 0f && scrollOffset >= maxScroll) {
            if (config.loop) {
                scrollOffset = 0f
            } else {
                isPlaying = false
            }
        }
    }

    private fun drawCountdown(canvas: Canvas) {
        val boxWidth = min(dp(180f), contentWidth - dp(32f))
        val boxHeight = dp(104f)
        val left = contentWidth / 2f - boxWidth / 2f
        val top = contentHeight / 2f - boxHeight / 2f
        backgroundPaint.color = withAlpha(config.backgroundColor, .96f)
        canvas.drawRoundRect(RectF(left, top, left + boxWidth, top + boxHeight), dp(22f), dp(22f), backgroundPaint)

        labelPaint.typeface = Typeface.create("sans", Typeface.BOLD)
        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(34f)
        drawCenteredText(canvas, countdownRemaining.toString(), contentWidth / 2f, top + dp(54f), labelPaint)
        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(11f)
        drawCenteredText(canvas, "即将开始", contentWidth / 2f, top + dp(82f), labelPaint)
    }

    private fun wrapLines(sourceLines: List<String>, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        sourceLines.forEach { sourceLine ->
            if (sourceLine.isEmpty()) {
                result += ""
                return@forEach
            }
            var current = ""
            sourceLine.forEach { character ->
                val candidate = current + character
                if (current.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) {
                    result += current
                    current = character.toString()
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) result += current
        }
        return result.ifEmpty { listOf("输入一段台词，开始你的表达。") }
    }

    private fun maxScriptScrollOffset(
        lineHeight: Float,
        lineCount: Int,
        guideY: Float,
        bottom: Float
    ): Float {
        val lastBaselineAtStart = guideY - lineHeight * .55f + (lineCount - 1) * lineHeight
        val lastBaselineLimit = bottom - lineHeight * .5f
        return (lastBaselineAtStart - lastBaselineLimit).coerceAtLeast(0f)
    }

    private fun currentMaxScriptScrollOffset(): Float {
        textPaint.textSize = sp(config.fontSize)
        val lineHeight = textPaint.textSize * config.lineSpacing
        val top = dp(82f)
        val bottom = contentHeight - dp(69f)
        val guideY = top + (bottom - top) * .52f
        val lines = wrapLines(config.text.replace("\r", "").split("\n"), contentWidth - dp(28f))
        return maxScriptScrollOffset(lineHeight, lines.size, guideY, bottom)
    }

    private fun drawBottomBar(canvas: Canvas) {
        linePaint.color = 0x334E5C5E
        canvas.drawLine(dp(14f), contentHeight - dp(57f), contentWidth - dp(14f), contentHeight - dp(57f), linePaint)

        val left = dp(14f)
        val right = contentWidth - dp(14f)
        val icons = listOf(
            "↶",
            "☷",
            if (isPlaying) "Ⅱ" else "▶",
            if (rotationQuarterTurns % 2 == 0) "↻" else "↺",
            "∞"
        )
        val cellWidth = (right - left) / icons.size
        val centers = icons.indices.map { index -> left + cellWidth * (index + .5f) }
        labelPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        labelPaint.color = 0xFFC7D0D0.toInt()
        labelPaint.textSize = sp(19f)
        icons.forEachIndexed { index, icon ->
            drawCenteredText(canvas, icon, centers[index], contentHeight - dp(24f), labelPaint)
        }
    }

    private fun drawSettingsPanel(canvas: Canvas) {
        val panel = settingsPanel()
        val speedTrack = settingsSpeedTrack(panel)
        val fontTrack = settingsFontTrack(panel)
        val countdownToggle = settingsCountdownToggle(panel)
        settingsDoneRect.set(panel.left + dp(24f), panel.bottom - dp(40f), panel.right - dp(24f), panel.bottom - dp(12f))

        backgroundPaint.color = withAlpha(config.backgroundColor, .98f)
        canvas.drawRoundRect(panel, dp(22f), dp(22f), backgroundPaint)

        labelPaint.typeface = Typeface.create("sans", Typeface.BOLD)
        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(16f)
        canvas.drawText("提词设置", panel.left + dp(22f), panel.top + dp(38f), labelPaint)

        labelPaint.color = 0xFFD7DEDE.toInt()
        labelPaint.textSize = sp(22f)
        canvas.drawText("×", panel.right - dp(30f), panel.top + dp(32f), labelPaint)

        drawSettingSlider(canvas, "速度", "${config.speed.toInt()} dp/s", speedTrack, config.speed, 8f..60f)
        drawSettingSlider(canvas, "字号", "${config.fontSize.toInt()} sp", fontTrack, config.fontSize, 16f..36f)

        labelPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(11f)
        canvas.drawText("播放前倒计时", panel.left + dp(24f), countdownToggle.top + dp(16f), labelPaint)
        backgroundPaint.color = if (config.countdown) 0xFFC4F76A.toInt() else 0xFF354047.toInt()
        canvas.drawRoundRect(countdownToggle, dp(10f), dp(10f), backgroundPaint)
        labelPaint.color = if (config.countdown) 0xFF0B1014.toInt() else 0xFFD7DEDE.toInt()
        labelPaint.textSize = sp(9f)
        drawCenteredText(canvas, if (config.countdown) "3 秒" else "关闭", countdownToggle.centerX(), countdownToggle.centerY() + dp(3f), labelPaint)

        backgroundPaint.color = 0xFFC4F76A.toInt()
        canvas.drawRoundRect(settingsDoneRect, dp(14f), dp(14f), backgroundPaint)
        labelPaint.color = 0xFF0B1014.toInt()
        labelPaint.textSize = sp(11f)
        drawCenteredText(canvas, "完成", settingsDoneRect.centerX(), settingsDoneRect.centerY() + dp(4f), labelPaint)
    }

    private fun drawSettingSlider(
        canvas: Canvas,
        label: String,
        value: String,
        track: RectF,
        current: Float,
        range: ClosedFloatingPointRange<Float>
    ) {
        labelPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(12f)
        canvas.drawText(label, track.left, track.top - dp(13f), labelPaint)
        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(11f)
        canvas.drawText(value, track.right - labelPaint.measureText(value), track.top - dp(13f), labelPaint)

        backgroundPaint.color = 0xFF354047.toInt()
        canvas.drawRoundRect(track, dp(5f), dp(5f), backgroundPaint)
        val fraction = ((current - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val activeRight = track.left + track.width() * fraction
        backgroundPaint.color = 0xFFC4F76A.toInt()
        canvas.drawRoundRect(RectF(track.left, track.top, activeRight, track.bottom), dp(5f), dp(5f), backgroundPaint)
        canvas.drawCircle(activeRight, track.centerY(), dp(7f), backgroundPaint)
    }

    private fun settingsPanel(): RectF {
        val panelHeight = min(dp(264f), (contentHeight - dp(24f)).coerceAtLeast(dp(1f)))
        val top = (contentHeight - panelHeight) / 2f
        settingsPanelRect.set(dp(12f), top, contentWidth - dp(12f), top + panelHeight)
        return settingsPanelRect
    }

    private fun settingsSpeedTrack(panel: RectF): RectF {
        speedTrackRect.set(panel.left + dp(24f), panel.top + dp(94f), panel.right - dp(24f), panel.top + dp(106f))
        return speedTrackRect
    }

    private fun settingsFontTrack(panel: RectF): RectF {
        fontTrackRect.set(panel.left + dp(24f), panel.top + dp(158f), panel.right - dp(24f), panel.top + dp(170f))
        return fontTrackRect
    }

    private fun settingsCountdownToggle(panel: RectF): RectF {
        countdownToggleRect.set(panel.right - dp(94f), panel.top + dp(190f), panel.right - dp(24f), panel.top + dp(214f))
        return countdownToggleRect
    }

    private fun updateSpeedFromX(x: Float) {
        val track = settingsSpeedTrack(settingsPanel())
        val fraction = ((x - track.left) / track.width()).coerceIn(0f, 1f)
        config = config.copy(speed = 8f + fraction * (60f - 8f))
        invalidate()
    }

    private fun updateFontFromX(x: Float) {
        val track = settingsFontTrack(settingsPanel())
        val fraction = ((x - track.left) / track.width()).coerceIn(0f, 1f)
        config = config.copy(fontSize = 16f + fraction * (36f - 16f))
        invalidate()
    }

    private fun saveConfigAfterInteraction() {
        onConfigChanged(config)
    }

    private fun handleBottomAction(x: Float) {
        val left = dp(14f)
        val right = contentWidth - dp(14f)
        if (x !in left..right) return
        val index = (((x - left) / ((right - left) / 5f)).toInt()).coerceIn(0, 4)
        when (index) {
            0 -> scrollOffset = 0f
            1 -> {
                isPlaying = false
                isCountingDown = false
                showSettings = true
            }
            2 -> togglePlayback()
            3 -> onRotate()
            4 -> {
                config = config.copy(loop = !config.loop)
                saveConfigAfterInteraction()
            }
        }
        invalidate()
    }

    private fun togglePlayback() {
        if (isPlaying) {
            isPlaying = false
            return
        }
        if (isCountingDown) {
            isCountingDown = false
            return
        }
        if (config.countdown) {
            countdownRemaining = 3
            countdownEndsAt = System.nanoTime() + 3_000_000_000L
            isCountingDown = true
        } else {
            isPlaying = true
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, centerX: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCollapsed) {
            return when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    onExpand()
                    true
                }
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWidth = width
                downHeight = height
                val point = contentPoint(event.x, event.y)
                downX = point.x
                downY = point.y

                if (showSettings) {
                    val panel = settingsPanel()
                    val speedTrack = settingsSpeedTrack(panel)
                    val fontTrack = settingsFontTrack(panel)
                    val countdownToggle = settingsCountdownToggle(panel)
                    settingsDoneRect.set(panel.left + dp(24f), panel.bottom - dp(40f), panel.right - dp(24f), panel.bottom - dp(12f))
                    dragMode = when {
                        point.x > panel.right - dp(62f) && point.y < panel.top + dp(58f) -> DragMode.SETTINGS_CLOSE
                        settingsDoneRect.contains(point.x, point.y) -> DragMode.SETTINGS_CLOSE
                        speedTrack.contains(point.x, point.y) -> DragMode.SETTINGS_SPEED
                        fontTrack.contains(point.x, point.y) -> DragMode.SETTINGS_FONT
                        countdownToggle.contains(point.x, point.y) -> DragMode.SETTINGS_COUNTDOWN
                        else -> DragMode.NONE
                    }
                    when (dragMode) {
                        DragMode.SETTINGS_SPEED -> updateSpeedFromX(point.x)
                        DragMode.SETTINGS_FONT -> updateFontFromX(point.x)
                        DragMode.SETTINGS_COUNTDOWN -> {
                            config = config.copy(countdown = !config.countdown)
                            saveConfigAfterInteraction()
                        }
                        else -> Unit
                    }
                    return true
                }

                val resizeEdge = dp(14f)
                val moveHandleHalfWidth = dp(44f)
                val isMoveHandle = point.y < dp(36f) &&
                    point.x in (contentWidth / 2f - moveHandleHalfWidth)..(contentWidth / 2f + moveHandleHalfWidth)
                dragMode = when {
                    point.y < dp(64f) && point.x > contentWidth - dp(70f) -> DragMode.CLOSE
                    isMoveHandle -> DragMode.MOVE
                    event.x <= resizeEdge -> DragMode.RESIZE_LEFT
                    event.x >= width - resizeEdge -> DragMode.RESIZE_RIGHT
                    event.y <= resizeEdge -> DragMode.RESIZE_TOP
                    event.y >= height - resizeEdge -> DragMode.RESIZE_BOTTOM
                    point.y < dp(36f) -> DragMode.MOVE
                    point.y in dp(78f)..(contentHeight - dp(70f)) -> {
                        scrollAtTouchStart = scrollOffset
                        didDragScript = false
                        DragMode.SCRIPT_SCROLL
                    }
                    else -> DragMode.NONE
                }
                if (dragMode == DragMode.RESIZE_LEFT ||
                    dragMode == DragMode.RESIZE_RIGHT ||
                    dragMode == DragMode.RESIZE_TOP ||
                    dragMode == DragMode.RESIZE_BOTTOM
                ) {
                    onResizeStart()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (showSettings) {
                    val point = contentPoint(event.x, event.y)
                    when (dragMode) {
                        DragMode.SETTINGS_SPEED -> updateSpeedFromX(point.x)
                        DragMode.SETTINGS_FONT -> updateFontFromX(point.x)
                        else -> Unit
                    }
                    return true
                }

                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                when (dragMode) {
                    DragMode.SCRIPT_SCROLL -> {
                        val point = contentPoint(event.x, event.y)
                        val dragDistance = point.y - downY
                        if (!didDragScript && abs(dragDistance) > touchSlop) {
                            didDragScript = true
                        }
                        if (didDragScript) {
                            scrollOffset = (scrollAtTouchStart - dragDistance)
                                .coerceIn(0f, currentMaxScriptScrollOffset())
                            invalidate()
                        }
                    }
                    DragMode.MOVE -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        onMove(dx, dy)
                    }
                    DragMode.RESIZE_LEFT,
                    DragMode.RESIZE_RIGHT,
                    DragMode.RESIZE_TOP,
                    DragMode.RESIZE_BOTTOM -> {
                        val totalDx = (event.rawX - downRawX).toInt()
                        val totalDy = (event.rawY - downRawY).toInt()
                        val minWidth = dp(250f).toInt()
                        val maxWidth = dp(720f).toInt()
                        val minHeight = dp(180f).toInt()
                        val maxHeight = dp(560f).toInt()
                        var newWidth = downWidth
                        var newHeight = downHeight
                        var moveX = 0
                        var moveY = 0
                        when (dragMode) {
                            DragMode.RESIZE_LEFT -> {
                                newWidth = (downWidth - totalDx).coerceIn(minWidth, maxWidth)
                                moveX = downWidth - newWidth
                            }
                            DragMode.RESIZE_RIGHT -> {
                                newWidth = (downWidth + totalDx).coerceIn(minWidth, maxWidth)
                            }
                            DragMode.RESIZE_TOP -> {
                                newHeight = (downHeight - totalDy).coerceIn(minHeight, maxHeight)
                                moveY = downHeight - newHeight
                            }
                            DragMode.RESIZE_BOTTOM -> {
                                newHeight = (downHeight + totalDy).coerceIn(minHeight, maxHeight)
                            }
                            else -> Unit
                        }
                        onResize(newWidth, newHeight, moveX, moveY)
                    }
                    DragMode.CLOSE,
                    DragMode.SETTINGS_CLOSE,
                    DragMode.SETTINGS_SPEED,
                    DragMode.SETTINGS_FONT,
                    DragMode.SETTINGS_COUNTDOWN,
                    DragMode.NONE -> Unit
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (showSettings) {
                    if (event.actionMasked == MotionEvent.ACTION_UP && dragMode == DragMode.SETTINGS_CLOSE) {
                        showSettings = false
                    }
                    if (dragMode == DragMode.SETTINGS_SPEED || dragMode == DragMode.SETTINGS_FONT) {
                        saveConfigAfterInteraction()
                    }
                    dragMode = DragMode.NONE
                    invalidate()
                    return true
                }

                if (event.actionMasked == MotionEvent.ACTION_UP && dragMode == DragMode.CLOSE) {
                    onCollapse()
                } else if (dragMode == DragMode.SCRIPT_SCROLL && event.actionMasked == MotionEvent.ACTION_UP && !didDragScript) {
                    togglePlayback()
                    invalidate()
                } else if (dragMode == DragMode.NONE && event.actionMasked == MotionEvent.ACTION_UP) {
                    when {
                        downY > contentHeight - dp(64f) -> handleBottomAction(downX)
                    }
                }
                dragMode = DragMode.NONE
                return true
            }
        }
        return true
    }

    private val contentWidth: Float
        get() = if (rotationQuarterTurns % 2 == 0) width.toFloat() else height.toFloat()

    private val contentHeight: Float
        get() = if (rotationQuarterTurns % 2 == 0) height.toFloat() else width.toFloat()

    private fun contentPoint(x: Float, y: Float): PointF = when (rotationQuarterTurns) {
        1 -> PointF(y, contentHeight - x)
        2 -> PointF(contentWidth - x, contentHeight - y)
        3 -> PointF(contentWidth - y, x)
        else -> PointF(x, y)
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity
    private fun withAlpha(color: Int, opacity: Float): Int =
        (color and 0x00FFFFFF) or ((opacity.coerceIn(.2f, 1f) * 255).toInt() shl 24)
}
