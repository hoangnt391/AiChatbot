package com.hoangnt.aichatbot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.abs

class AutoReplyService : AccessibilityService() {
    companion object {
        private const val TAG = "AiChatAutoReply"
        private const val PREFS = "aichatbot"
        private const val AUTO_MODE = "auto_mode"
        private const val API_KEY = "api_key"
        private const val DELAY_MS = 5000L
        private const val ZALO = "com.zing.zalo"
        private const val MESSENGER = "com.facebook.orca"

        @Volatile var instance: AutoReplyService? = null
            private set
        @Volatile var statusText: String = "Chưa khởi động"

        fun setAutoMode(enabled: Boolean) {
            instance?.apply {
                prefs.edit().putBoolean(AUTO_MODE, enabled).apply()
                setStatus(if (enabled) "Tự động BẬT — đang chờ tin mới" else "Tự động TẮT")
                Log.d(TAG, "AUTO_MODE=" + enabled)
            }
        }
        fun getStatus(): String = statusText
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val seen = LinkedHashSet<String>()
    private var currentPackage: String? = null
    private var lastKey: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setStatus("Trợ năng đã kết nối — Tự động " + if (autoEnabled()) "BẬT" else "TẮT")
        Log.d(TAG, "Connected: com.hoangnt.aichatbot")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != ZALO && pkg != MESSENGER) return

        if (pkg != currentPackage) {
            currentPackage = pkg
            seen.clear()
            lastKey = null
            Log.d(TAG, "Theo dõi gói: " + pkg)
        }

        val root = rootInActiveWindow ?: return
        try {
            val messages = collectMessages(root)

            // Lần quét đầu chỉ tạo baseline, không trả lời tin cũ đang nằm trên màn hình.
            if (seen.isEmpty()) {
                messages.forEach { seen.add(key(pkg, it)) }
                Log.d(TAG, "Baseline màn hình: " + messages.size + " node tin nhắn")
                return
            }

            val newMessage = messages.firstOrNull {
                val k = key(pkg, it)
                !seen.contains(k) && incoming(it)
            }

            messages.forEach { seen.add(key(pkg, it)) }
            while (seen.size > 250) seen.iterator().next().let(seen::remove)

            if (newMessage == null) return
            val k = key(pkg, newMessage)
            if (k == lastKey) return
            lastKey = k

            Log.d(TAG, "PHÁT HIỆN TIN MỚI: " + newMessage.text)
            if (!autoEnabled()) {
                setStatus("Có tin mới — Tự động TẮT")
                Log.d(TAG, "AUTO OFF: không gọi AI/gửi")
                return
            }
            processNewMessage(pkg, newMessage.text)
        } finally {
            root.recycle()
        }
    }

    private fun processNewMessage(platform: String, incoming: String) {
        val started = System.currentTimeMillis()
        setStatus("Đang chờ... 5s")
        Log.d(TAG, "Bắt đầu đếm chính xác 5 giây")

        var reply: String? = null
        var error: String? = null
        var ready = false

        worker.execute {
            try {
                reply = callOpenAI(incoming)
                ready = true
                Log.d(TAG, "AI OK: " + reply)
            } catch (e: Exception) {
                error = e.message ?: "Lỗi AI"
                Log.e(TAG, "AI ERROR", e)
            }
        }

        val tick = object : Runnable {
            override fun run() {
                if (!autoEnabled()) {
                    setStatus("Đã dừng — Tự động TẮT")
                    return
                }
                val remaining = DELAY_MS - (System.currentTimeMillis() - started)
                if (remaining > 0) {
                    setStatus("Đang chờ... " + ((remaining + 999) / 1000) + "s")
                    handler.postDelayed(this, minOf(remaining, 100L))
                    return
                }
                if (!ready) {
                    setStatus("Đã đủ 5s — đang chờ AI")
                    handler.postDelayed(this, 100L)
                    return
                }
                if (!error.isNullOrBlank()) {
                    setStatus("Lỗi AI: " + error)
                    return
                }
                val text = reply.orEmpty().trim()
                if (text.isEmpty()) {
                    setStatus("AI không trả về nội dung")
                    return
                }
                sendReply(platform, text)
            }
        }
        handler.post(tick)
    }

    private fun sendReply(platform: String, reply: String) {
        if (!autoEnabled()) return
        setStatus("Đang tìm ô nhập...")
        worker.execute {
            val root = rootInActiveWindow
            if (root == null) {
                fail("Không đọc được màn hình " + platform)
                return@execute
            }
            try {
                val input = findInput(root)
                if (input == null) {
                    Log.e(TAG, "KHÔNG TÌM THẤY Ô NHẬP")
                    dumpNodes(root)
                    fail("Không tìm thấy ô nhập liệu")
                    return@execute
                }
                Log.d(TAG, "TÌM THẤY Ô NHẬP: " + describe(input))
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
                }
                if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    fail("Không thể điền nội dung vào ô nhập")
                    return@execute
                }
                Log.d(TAG, "ĐIỀN NỘI DUNG THÀNH CÔNG")
                Thread.sleep(200)

                val updated = rootInActiveWindow
                if (updated == null) {
                    fail("Không đọc lại được màn hình sau khi điền")
                    return@execute
                }
                try {
                    setStatus("Đã điền — đang tìm nút Gửi")
                    val send = findSend(updated, input)
                    if (send == null) {
                        Log.e(TAG, "KHÔNG TÌM THẤY NÚT GỬI")
                        dumpNodes(updated)
                        fail("Không tìm thấy nút Gửi")
                        return@execute
                    }
                    Log.d(TAG, "TÌM THẤY NÚT GỬI: " + describe(send))
                    if (!send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        fail("Đã tìm thấy nút Gửi nhưng không nhấn được")
                        return@execute
                    }
                    Log.d(TAG, "NHẤN GỬI THÀNH CÔNG")
                    setStatus("Đã gửi: " + reply)
                } finally {
                    updated.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tự động gửi", e)
                fail("Lỗi Trợ năng: " + (e.message ?: "không xác định"))
            } finally {
                root.recycle()
            }
        }
    }

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        val list = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isVisibleToUser && n.isEnabled && n.isEditable) {
                val s = inputScore(n)
                Log.d(TAG, "INPUT CANDIDATE score=" + s + " " + describe(n))
                list += n to s
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::addLast)
        }
        return list.maxByOrNull { it.second }?.first
    }

    private fun inputScore(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        val label = listOf(
            n.text, n.contentDescription,
            if (Build.VERSION.SDK_INT >= 26) n.hintText else null,
            n.className, n.viewIdResourceName
        ).joinToString(" ").lowercase()
        var s = 0
        if ("tin nhắn" in label || "nhắn tin" in label || "message" in label) s += 180
        if ("composer" in label || "chat" in label || "input" in label) s += 100
        if ("edittext" in n.className.toString().lowercase()) s += 70
        if ("message" in n.viewIdResourceName.orEmpty().lowercase() ||
            "input" in n.viewIdResourceName.orEmpty().lowercase()) s += 90
        if ("search" in label || "tìm kiếm" in label) s -= 180
        if (r.bottom > resources.displayMetrics.heightPixels * .62) s += 100
        if (r.width() > 120) s += 30
        if (r.height() in 30..250) s += 20
        return s
    }

    private fun findSend(root: AccessibilityNodeInfo, input: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ir = Rect()
        input.getBoundsInScreen(ir)
        val q = ArrayDeque<AccessibilityNodeInfo>()
        val list = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isVisibleToUser && n.isEnabled && n.isClickable) {
                val s = sendScore(n, ir)
                Log.d(TAG, "SEND CANDIDATE score=" + s + " " + describe(n))
                if (s >= 120) list += n to s
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::addLast)
        }
        list.sortedByDescending { it.second }.take(12).forEach {
            Log.d(TAG, "SEND RANK score=" + it.second + " " + describe(it.first))
        }
        return list.maxByOrNull { it.second }?.first
    }

    private fun sendScore(n: AccessibilityNodeInfo, ir: Rect): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        val label = listOf(n.text, n.contentDescription, n.viewIdResourceName)
            .joinToString(" ").lowercase()
        val cls = n.className?.toString()?.lowercase().orEmpty()
        var s = 0
        if ("gửi" in label || "send" in label) s += 260
        if ("send_message" in label || "sendmessage" in label) s += 160
        if ("btn_send" in label || "button_send" in label) s += 150
        if ("button" in cls || "imagebutton" in cls) s += 35
        if (r.bottom >= ir.top - 100 && r.top <= ir.bottom + 100) s += 120
        if (r.centerX() >= ir.right - 20) s += 110
        if (r.top > resources.displayMetrics.heightPixels * .60) s += 70
        if (r.width() in 25..300 && r.height() in 25..300) s += 25
        if (abs(r.centerX() - ir.right) < resources.displayMetrics.widthPixels * .35) s += 60
        return s
    }

    private fun collectMessages(root: AccessibilityNodeInfo): List<Candidate> {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        val result = mutableListOf<Candidate>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val text = n.text?.toString()?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()
            if (n.isVisibleToUser && text.length in 1..1000 && isMessageText(text)) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() > 20 && r.height() > 10) result += Candidate(text, r, n)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::addLast)
        }
        return result.distinctBy { it.text + "|" + it.rect.toString() }.takeLast(30)
    }

    private fun incoming(c: Candidate): Boolean =
        c.rect.centerX() < resources.displayMetrics.widthPixels * .62f

    private fun isMessageText(text: String): Boolean {
        val x = text.lowercase()
        val ignored = setOf(
            "gửi","send","tin nhắn","nhắn tin","message","search","tìm kiếm",
            "zalo","messenger","trực tuyến","online","đã xem","seen",
            "thêm","more","quay lại","back","hủy","cancel","đóng","close"
        )
        return x !in ignored && !x.startsWith("http://") && !x.startsWith("https://")
    }

    private fun key(pkg: String, c: Candidate): String =
        pkg + "|" + c.text + "|" + c.rect.left + "|" + c.rect.top + "|" +
            c.rect.right + "|" + c.rect.bottom

    private fun callOpenAI(incoming: String): String {
        val key = prefs.getString(API_KEY, "").orEmpty().trim()
        if (key.isEmpty()) error("Chưa cấu hình API_KEY")
        val history = runCatching {
            JSONArray(prefs.getString("history", "[]"))
        }.getOrElse { JSONArray() }

        val messages = JSONArray().put(
            JSONObject().put("role","system").put("content",
                "Bạn đang nhắn tin như một người Việt bình thường. Trả lời ngắn gọn 1-2 câu, tự nhiên, đời thường, không trang trọng, không dài dòng. Không nói mình là AI. Chỉ trả về câu có thể gửi ngay.")
        )

        for (i in maxOf(0, history.length()-10) until history.length()) {
            val x = history.optJSONObject(i) ?: continue
            if (x.optString("user").isNotEmpty()) {
                messages.put(JSONObject().put("role","user").put("content",x.optString("user")))
            }
            if (x.optString("assistant").isNotEmpty()) {
                messages.put(JSONObject().put("role","assistant").put("content",x.optString("assistant")))
            }
        }
        messages.put(JSONObject().put("role","user").put("content",incoming))

        val body = JSONObject().put("model","gpt-4o-mini")
            .put("messages",messages).put("temperature",.8)
            .put("max_tokens",120).toString()

        val c = URL("https://api.openai.com/v1/chat/completions")
            .openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST"
            c.connectTimeout=15000
            c.readTimeout=30000
            c.doOutput=true
            c.setRequestProperty("Authorization","Bearer " + key)
            c.setRequestProperty("Content-Type","application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code=c.responseCode
            val stream=if(code in 200..299)c.inputStream else c.errorStream
            val response=stream.bufferedReader().use{it.readText()}
            if(code !in 200..299) {
                val detail=runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull()
                error("HTTP " + code + if(detail.isNullOrEmpty()) "" else ": " + detail)
            }

            val answer=JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
            if(answer.isEmpty()) error("AI không trả về nội dung")

            val updated=JSONArray(history.toString())
                .put(JSONObject().put("user",incoming).put("assistant",answer))
            val trimmed=JSONArray()
            for(i in maxOf(0,updated.length()-10) until updated.length()) {
                trimmed.put(updated.getJSONObject(i))
            }
            prefs.edit().putString("history",trimmed.toString()).apply()
            return answer
        } finally {
            c.disconnect()
        }
    }

    private fun autoEnabled() = prefs.getBoolean(AUTO_MODE, false)
    private fun setStatus(s: String) { statusText=s; Log.d(TAG,s) }
    private fun fail(s: String) { setStatus(s); Log.e(TAG,s) }

    private fun describe(n: AccessibilityNodeInfo): String {
        val r=Rect()
        n.getBoundsInScreen(r)
        val hint=if(Build.VERSION.SDK_INT>=26)n.hintText?.toString().orEmpty() else ""
        return "class=" + n.className + "; id=" + n.viewIdResourceName +
            "; text=" + n.text + "; desc=" + n.contentDescription +
            "; hint=" + hint + "; bounds=" + r +
            "; clickable=" + n.isClickable + "; editable=" + n.isEditable
    }

    private fun dumpNodes(root: AccessibilityNodeInfo) {
        Log.e(TAG,"===== INTERACTIVE NODES =====")
        val q=ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var i=0
        while(q.isNotEmpty() && i<150) {
            val n=q.removeFirst()
            if(n.isVisibleToUser && (n.isClickable || n.isEditable)) {
                Log.e(TAG,"#" + i + " " + describe(n))
                i++
            }
            for(j in 0 until n.childCount) n.getChild(j)?.let(q::addLast)
        }
        Log.e(TAG,"===== END NODES: " + i + " =====")
    }

    override fun onInterrupt() { setStatus("Trợ năng bị gián đoạn") }

    override fun onDestroy() {
        if(instance===this) instance=null
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        setStatus("Trợ năng đã dừng")
        super.onDestroy()
    }

    private data class Candidate(
        val text:String,
        val rect:Rect,
        val node:AccessibilityNodeInfo
    )
}
