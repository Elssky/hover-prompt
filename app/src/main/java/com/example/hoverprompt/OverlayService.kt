package com.example.hoverprompt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

private data class OverlayConfig(
    val text: String,
    val mode: PromptMode,
    val speed: Float,
    val fontSize: Float,
    val lineSpacing: Float,
    val textColor: Int,
    val backgroundColor: Int,
    val opacity: Float,
    val loop: Boolean
)

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: PromptOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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

        val config = OverlayConfig(
            text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "输入一段台词，开始你的表达。" },
            mode = runCatching { PromptMode.valueOf(intent?.getStringExtra(EXTRA_MODE).orEmpty()) }.getOrDefault(PromptMode.PACE),
            speed = intent?.getFloatExtra(EXTRA_SPEED, 24f) ?: 24f,
            fontSize = intent?.getFloatExtra(EXTRA_FONT_SIZE, 23f) ?: 23f,
            lineSpacing = intent?.getFloatExtra(EXTRA_LINE_SPACING, 1.28f) ?: 1.28f,
            textColor = intent?.getIntExtra(EXTRA_TEXT_COLOR, 0xFFF0EEE5.toInt()) ?: 0xFFF0EEE5.toInt(),
            backgroundColor = intent?.getIntExtra(EXTRA_BACKGROUND_COLOR, 0xFF111821.toInt()) ?: 0xFF111821.toInt(),
            opacity = intent?.getFloatExtra(EXTRA_OPACITY, .92f) ?: .92f,
            loop = intent?.getBooleanExtra(EXTRA_LOOP, true) ?: true
        )

        if (overlayView == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                dp(340),
                dp(310),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(22)
                y = dp(150)
            }
            layoutParams = params
            overlayView = PromptOverlayView(
                context = this,
                config = config,
                onClose = { stopSelf() },
                onMove = { dx, dy -> moveOverlay(dx, dy) },
                onResize = { width, height -> resizeOverlay(width, height) }
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
        windowManager.updateViewLayout(view, params)
    }

    private fun resizeOverlay(width: Int, height: Int) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.width = width
        params.height = height
        windowManager.updateViewLayout(view, params)
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            if (::windowManager.isInitialized) windowManager.removeView(view)
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        const val ACTION_STOP = "com.example.hoverprompt.STOP"
        private const val CHANNEL_ID = "hover_prompt_running"
        private const val NOTIFICATION_ID = 1001
    }
}

private class PromptOverlayView(
    context: Context,
    private var config: OverlayConfig,
    private val onClose: () -> Unit,
    private val onMove: (Int, Int) -> Unit,
    private val onResize: (Int, Int) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelRect = RectF()
    private var isPlaying = false
    private var scrollOffset = 0f
    private var lastFrameTime = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragMode = DragMode.NONE

    private enum class DragMode { NONE, MOVE, RESIZE, CLOSE }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        postInvalidateOnAnimation()
    }

    fun updateConfig(newConfig: OverlayConfig) {
        config = newConfig
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = dp(22f)
        panelRect.set(0f, 0f, width.toFloat(), height.toFloat())

        backgroundPaint.color = withAlpha(config.backgroundColor, config.opacity)
        canvas.drawRoundRect(panelRect, radius, radius, backgroundPaint)

        drawTopBar(canvas)
        drawScript(canvas)
        drawBottomBar(canvas)

        if (isPlaying) {
            val now = System.nanoTime()
            if (lastFrameTime != 0L) {
                val deltaSeconds = (now - lastFrameTime) / 1_000_000_000f
                scrollOffset += config.speed * density * deltaSeconds
            }
            lastFrameTime = now
            postInvalidateOnAnimation()
        } else {
            lastFrameTime = 0L
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        // Six-dot handle: the most important affordance when the overlay sits over another app.
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB4BEC0.toInt() }
        val startX = width / 2f - dp(13f)
        for (row in 0..1) {
            for (column in 0..2) {
                canvas.drawCircle(startX + column * dp(13f), dp(14f) + row * dp(8f), dp(1.7f), dotPaint)
            }
        }

        labelPaint.color = 0xFFE9E8E1.toInt()
        labelPaint.textSize = sp(12f)
        canvas.drawText("直播台词", dp(16f), dp(53f), labelPaint)

        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(9f)
        canvas.drawRoundRect(RectF(dp(86f), dp(40f), dp(142f), dp(59f)), dp(9f), dp(9f), labelPaint)
        labelPaint.color = 0xFF0B1014.toInt()
        canvas.drawText(if (isPlaying) "正在读" else config.mode.label, dp(91f), dp(53f), labelPaint)

        labelPaint.color = 0xFFD7DEDE.toInt()
        labelPaint.textSize = sp(23f)
        canvas.drawText("×", width - dp(28f), dp(27f), labelPaint)
        linePaint.color = 0x334E5C5E
        linePaint.strokeWidth = dp(1f)
        canvas.drawLine(dp(14f), dp(68f), width - dp(14f), dp(68f), linePaint)
    }

    private fun drawScript(canvas: Canvas) {
        val top = dp(82f)
        val bottom = height - dp(69f)
        canvas.save()
        canvas.clipRect(dp(14f), top, width - dp(14f), bottom)

        linePaint.color = 0xD6C4F76A.toInt()
        linePaint.strokeWidth = dp(1f)
        val guideY = top + (bottom - top) * .52f
        canvas.drawLine(dp(10f), guideY, width - dp(10f), guideY, linePaint)
        canvas.drawCircle(dp(10f), guideY, dp(3f), linePaint)

        textPaint.color = config.textColor
        textPaint.textSize = sp(config.fontSize)
        textPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        val lineHeight = textPaint.textSize * config.lineSpacing
        val lines = wrapLines(config.text.replace("\r", "").split("\n"), width - dp(28f))
        val startY = guideY - lineHeight * .55f - scrollOffset
        lines.forEachIndexed { index, line ->
            val y = startY + index * lineHeight
            if (y > top - lineHeight && y < bottom + lineHeight) {
                canvas.drawText(line, dp(14f), y, textPaint)
            }
        }
        if (!isPlaying) {
            val pillWidth = dp(132f)
            val pillHeight = dp(42f)
            val pillLeft = width / 2f - pillWidth / 2f
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

        if (isPlaying && scrollOffset > lineHeight * lines.size + (bottom - top)) {
            if (config.loop) {
                scrollOffset = 0f
            } else {
                isPlaying = false
            }
        }
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

    private fun drawBottomBar(canvas: Canvas) {
        linePaint.color = 0x334E5C5E
        canvas.drawLine(dp(14f), height - dp(57f), width - dp(14f), height - dp(57f), linePaint)

        labelPaint.color = 0xFFC7D0D0.toInt()
        labelPaint.textSize = sp(19f)
        canvas.drawText("↶", dp(28f), height - dp(24f), labelPaint)
        canvas.drawText("☷", dp(91f), height - dp(24f), labelPaint)
        canvas.drawText("↻", dp(154f), height - dp(24f), labelPaint)
        canvas.drawText("▤", dp(218f), height - dp(24f), labelPaint)

        labelPaint.color = 0xFFC4F76A.toInt()
        labelPaint.textSize = sp(24f)
        canvas.drawText("⌟", width - dp(34f), height - dp(22f), labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downX = event.x
                downY = event.y
                dragMode = when {
                    event.y < dp(38f) && event.x > width - dp(55f) -> DragMode.CLOSE
                    event.y < dp(36f) -> DragMode.MOVE
                    event.x > width - dp(58f) && event.y > height - dp(64f) -> DragMode.RESIZE
                    else -> DragMode.NONE
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                when (dragMode) {
                    DragMode.MOVE -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        onMove(dx, dy)
                    }
                    DragMode.RESIZE -> {
                        val newWidth = (width + dx).coerceIn(dp(250f), dp(480f))
                        val newHeight = (height + dy).coerceIn(dp(220f), dp(560f))
                        downRawX = event.rawX
                        downRawY = event.rawY
                        onResize(newWidth, newHeight)
                    }
                    DragMode.CLOSE -> Unit
                    DragMode.NONE -> Unit
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && dragMode == DragMode.CLOSE) {
                    onClose()
                } else if (dragMode == DragMode.NONE && event.actionMasked == MotionEvent.ACTION_UP) {
                    when {
                        downY > height - dp(60f) && downX in dp(65f)..dp(135f) -> {
                            scrollOffset = 0f
                            invalidate()
                        }
                        downY > height - dp(60f) && downX in dp(135f)..dp(205f) -> {
                            isPlaying = !isPlaying
                            invalidate()
                        }
                        downY in dp(78f)..(height - dp(70f)) -> {
                            isPlaying = !isPlaying
                            invalidate()
                        }
                    }
                }
                dragMode = DragMode.NONE
                return true
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity
    private fun withAlpha(color: Int, opacity: Float): Int =
        (color and 0x00FFFFFF) or ((opacity.coerceIn(.2f, 1f) * 255).toInt() shl 24)
}
