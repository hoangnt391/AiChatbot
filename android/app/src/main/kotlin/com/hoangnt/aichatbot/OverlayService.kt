package com.hoangnt.aichatbot

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kotlin.math.hypot

class OverlayService : Service() {
    companion object {
        private const val TAG = "AiChatBotOverlay"
        private const val PREFS = "aichatbot"
        private const val AUTO_MODE = "auto_mode"

        fun enqueueAiReply(context: Context, reply: String, platform: String = "") {
            if (reply.trim().isEmpty()) return
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = "AI_REPLY"
                putExtra("reply", reply.trim())
                putExtra("platform", platform)
            })
        }

        fun reportAccessibilityResult(context: Context, message: String) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = "ACCESSIBILITY_RESULT"
                putExtra("message", message)
            })
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var deleteTarget: TextView? = null
    private var status: TextView? = null
    private var answer: TextView? = null
    private var autoSwitch: Switch? = null
    private var pendingReply: String? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var autoMode: Boolean
        get() = prefs.getBoolean(AUTO_MODE, false)
        set(value) = prefs.edit().putBoolean(AUTO_MODE, value).apply()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACCESSIBILITY_RESULT" -> {
                val message = intent.getStringExtra("message").orEmpty()
                if (message.isNotBlank()) updateStatus(message)
            }
            "AI_REPLY" -> {
                val reply = intent.getStringExtra("reply").orEmpty().trim()
                if (reply.isNotEmpty()) {
                    pendingReply = reply
                    answer?.text = reply
                }
            }
        }
        return START_STICKY
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun showBubble() {
        if (bubble != null) return
        val d = resources.displayMetrics.density
        val size = (54 * d).toInt()

        val b = TextView(this).apply {
            text = "AI"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(30, 110, 220), 27f)
            elevation = 8 * d
            setOnClickListener { showPanel() }
        }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * d).toInt()
            y = (220 * d).toInt()
        }

        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false

        b.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = event.rawX; sy = event.rawY; ox = lp.x; oy = lp.y
                    moved = false
                    showDeleteTarget()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - sx
                    val dy = event.rawY - sy
                    if (hypot(dx.toDouble(), dy.toDouble()) > 8 * d) moved = true
                    lp.x = ox + dx.toInt()
                    lp.y = oy + dy.toInt()
                    wm.updateViewLayout(view, lp)
                    updateDeleteTarget(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val delete = isOverDelete(event.rawX, event.rawY)
                    hideDeleteTarget()
                    if (delete) {
                        stopSelf()
                    } else if (!moved) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        bubble = b
        wm.addView(b, lp)
    }

    private fun showDeleteTarget() {
        if (deleteTarget != null) return
        val d = resources.displayMetrics.density
        val target = TextView(this).apply {
            text = "✕  Kéo vào đây để tắt"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((20*d).toInt(), (12*d).toInt(), (20*d).toInt(), (12*d).toInt())
            background = rounded(Color.argb(225, 65, 65, 65), 28f)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (32*d).toInt()
        }
        deleteTarget = target
        wm.addView(target, lp)
    }

    private fun updateDeleteTarget(x: Float, y: Float) {
        deleteTarget?.background = rounded(
            if (isOverDelete(x, y)) Color.rgb(200, 45, 45) else Color.argb(225, 65, 65, 65),
            28f
        )
    }

    private fun isOverDelete(x: Float, y: Float): Boolean {
        val v = deleteTarget ?: return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val pad = 30 * resources.displayMetrics.density
        return x >= loc[0] - pad && x <= loc[0] + v.width + pad &&
            y >= loc[1] - pad && y <= loc[1] + v.height + pad
    }

    private fun hideDeleteTarget() {
        deleteTarget?.let { runCatching { wm.removeView(it) } }
        deleteTarget = null
    }

    private fun showPanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18*d).toInt(), (16*d).toInt(), (18*d).toInt(), (16*d).toInt())
            background = rounded(Color.WHITE, 18f)
        }
        box.addView(TextView(this).apply {
            text = "AiChatBot"; textSize = 19f; setTextColor(Color.BLACK)
        })
        autoSwitch = Switch(this).apply {
            text = "Tự động trả lời"
            isChecked = autoMode
            setOnCheckedChangeListener { _, checked ->
                autoMode = checked
                AutoReplyService.setAutoMode(checked)
                updateStatus(if (checked) "BẬT — đang chờ tin mới" else "TẮT")
            }
        }
        box.addView(autoSwitch)
        status = TextView(this).apply {
            text = AutoReplyService.getStatus(); textSize = 14f; setTextColor(Color.DKGRAY)
        }
        box.addView(status)
        answer = TextView(this).apply {
            text = pendingReply ?: "Chưa có gợi ý"; textSize = 15f; setTextColor(Color.BLACK)
            setPadding(0, (10*d).toInt(), 0, (10*d).toInt())
        }
        box.addView(answer)
        box.addView(Button(this).apply {
            text = "Sao chép"
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("AiChatBot", pendingReply.orEmpty()))
                updateStatus("Đã sao chép")
            }
        })
        box.addView(Button(this).apply {
            text = "Đóng"
            setOnClickListener {
                panel?.let { runCatching { wm.removeView(it) } }
                panel = null
            }
        })
        box.addView(Button(this).apply {
            text = "Tắt bong bóng"
            setOnClickListener { stopSelf() }
        })

        val maxWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        val lp = WindowManager.LayoutParams(
            maxWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        panel = box
        wm.addView(box, lp)
    }

    private fun updateStatus(text: String) {
        handler.post {
            status?.text = text
            Log.d(TAG, text)
        }
    }

    override fun onDestroy() {
        hideDeleteTarget()
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        panel = null
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
