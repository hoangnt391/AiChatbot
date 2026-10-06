package com.hoangnt.aichatbot

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        private const val TAG = "AiChatBotOverlay"
        private const val ACTION_NEW_MESSAGE = "NEW_MESSAGE"
        private const val PREFS = "aichatbot"
        private const val AUTO_MODE = "auto_mode"
        private const val API_KEY = "api_key"
        private const val DELAY_MS = 5_000L

        fun enqueueAiReply(context: Context, reply: String, platform: String = "") {
            if (reply.trim().isEmpty()) return
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = "AI_REPLY"
                putExtra("reply", reply.trim())
                putExtra("platform", platform)
            })
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var answer: TextView? = null
    private var autoSwitch: Switch? = null

    private var pendingReply: String? = null
    private var countdown: Runnable? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }

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
            ACTION_NEW_MESSAGE -> {
                val message = intent.getStringExtra("message").orEmpty()
                val platform = intent.getStringExtra("platform").orEmpty()
                if (message.isNotBlank()) {
                    // AI layer can call this service with the generated answer.
                    Log.d(TAG, "Nhận tin mới từ $platform: $message")
                }
            }

            "AI_REPLY" -> {
                val reply = intent.getStringExtra("reply").orEmpty().trim()
                if (reply.isNotEmpty()) {
                    pendingReply = reply
                    answer?.text = reply

                    if (autoMode) {
                        scheduleAutomaticSend()
                    } else {
                        updateStatus("TẮT — chỉ gợi ý")
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun scheduleAutomaticSend() {
        countdown?.let(handler::removeCallbacks)

        if (!autoMode) {
            updateStatus("TẮT — chỉ gợi ý")
            return
        }

        var seconds = 5
        updateStatus("Đang chờ... 5s")

        val task = object : Runnable {
            override fun run() {
                if (!autoMode) {
                    updateStatus("Đã dừng tự động")
                    countdown = null
                    return
                }

                if (seconds > 1) {
                    seconds--
                    updateStatus("Đang chờ... ${seconds}s")
                    handler.postDelayed(this, 1_000L)
                    return
                }

                countdown = null
                sendReplyThroughAccessibility(pendingReply.orEmpty())
            }
        }

        countdown = task
        handler.postDelayed(task, 1_000L)
    }

    private fun sendReplyThroughAccessibility(reply: String) {
        if (!autoMode) {
            updateStatus("Đã dừng tự động")
            return
        }

        if (reply.isBlank()) {
            Log.e(TAG, "Không có nội dung AI để gửi")
            updateStatus("Không có nội dung trả lời")
            return
        }

        val service = MyAccessibilityService.instance

        if (service == null) {
            Log.e(TAG, "Không tìm thấy AccessibilityService")
            updateStatus("Không tìm thấy Trợ năng")
            return
        }

        updateStatus("Đang tìm ô nhập...")

        Thread {
            val result = service.fillInputAndSend(reply)

            handler.post {
                if (result) {
                    updateStatus("Đã gửi: $reply")
                    Log.d(TAG, "Tự động gửi thành công")
                    pendingReply = null
                } else {
                    updateStatus("Không tìm thấy ô nhập/nút Gửi")
                    Log.e(TAG, "fillInputAndSend() thất bại")
                }
            }
        }.start()
    }

    private fun showBubble() {
        if (bubble != null) return

        val b = TextView(this).apply {
            text = "AI"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(30, 110, 220))
            setOnClickListener { showPanel() }
        }

        val lp = WindowManager.LayoutParams(
            72, 72,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 30
        lp.y = 300

        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0

        b.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = event.rawX
                    sy = event.rawY
                    ox = lp.x
                    oy = lp.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (event.rawX - sx).toInt()
                    lp.y = oy + (event.rawY - sy).toInt()
                    wm.updateViewLayout(view, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        bubble = b
        wm.addView(b, lp)
    }

    private fun showPanel() {
        panel?.let { runCatching { wm.removeView(it) } }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "AiChatBot"
            textSize = 20f
            setTextColor(Color.BLACK)
        }
        box.addView(title)

        autoSwitch = Switch(this).apply {
            text = "Tự động trả lời"
            isChecked = autoMode
            setOnCheckedChangeListener { _, checked ->
                autoMode = checked
                if (!checked) {
                    countdown?.let(handler::removeCallbacks)
                    countdown = null
                    updateStatus("TẮT — chỉ gợi ý")
                } else {
                    updateStatus("BẬT — đang chờ...")
                    if (!pendingReply.isNullOrBlank()) scheduleAutomaticSend()
                }
            }
        }
        box.addView(autoSwitch)

        status = TextView(this).apply {
            text = if (autoMode) "Đang chờ..." else "TẮT — chỉ gợi ý"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }
        box.addView(status)

        answer = TextView(this).apply {
            text = pendingReply ?: "Chưa có gợi ý"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(12, 18, 12, 18)
        }
        box.addView(answer)

        val copy = Button(this).apply {
            text = "Sao chép"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("AiChatBot", pendingReply.orEmpty())
                )
                updateStatus("Đã sao chép")
            }
        }
        box.addView(copy)

        val stop = Button(this).apply {
            text = "Dừng"
            setOnClickListener {
                countdown?.let(handler::removeCallbacks)
                countdown = null
                updateStatus("Đã dừng")
                Log.d(TAG, "Người dùng nhấn Dừng")
            }
        }
        box.addView(stop)

        val close = Button(this).apply {
            text = "Đóng"
            setOnClickListener {
                panel?.let { runCatching { wm.removeView(it) } }
                panel = null
            }
        }
        box.addView(close)

        val lp = WindowManager.LayoutParams(
            760,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER

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
        countdown?.let(handler::removeCallbacks)
        countdown = null

        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }

        panel = null
        bubble = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
