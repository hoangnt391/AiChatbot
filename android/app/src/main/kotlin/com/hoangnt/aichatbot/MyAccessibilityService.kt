package com.hoangnt.aichatbot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import kotlin.math.abs

class MyAccessibilityService : AccessibilityService() {

    data class Candidate(val text: String, val top: Int, val bottom: Int, val centerX: Int)

    companion object {
        private const val TAG = "AiChatAccessibility"

        @Volatile
        var instance: MyAccessibilityService? = null
            private set
    }

    private var initialized = false
    private var previousCounts: Map<String, Int> = emptyMap()
    private val processedKeys = LinkedHashSet<String>()
    private val incomingRightLimit = 0.58f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.zing.zalo" && packageName != "com.facebook.orca") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            initialized = false
            previousCounts = emptyMap()
            processedKeys.clear()
        }

        val root = rootInActiveWindow ?: return

        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)

            val candidates = mutableListOf<Candidate>()
            collectCandidates(root, candidates)

            val incoming = candidates
                .distinctBy { normalize(it.text) + "|" + it.top + "|" + it.bottom + "|" + it.centerX }
                .filter { isIncomingMessage(it, metrics.widthPixels) }
                .sortedBy { it.top }

            if (incoming.isEmpty()) return

            val currentCounts = incoming.groupingBy { normalize(it.text) }.eachCount()

            if (!initialized) {
                initialized = true
                previousCounts = currentCounts
                Log.d(TAG, "Baseline: " + incoming.size + " tin")
                return
            }

            val newCandidates = incoming.filter {
                val key = normalize(it.text)
                currentCounts[key]!! > (previousCounts[key] ?: 0)
            }

            previousCounts = currentCounts

            val newest = newCandidates.maxByOrNull { it.bottom } ?: return
            val message = newest.text.trim()
            if (message.isEmpty()) return

            val processedKey =
                packageName + "|" + normalize(message) + "|" +
                    newest.top + "|" + newest.bottom

            if (!processedKeys.add(processedKey)) return

            while (processedKeys.size > 100) {
                processedKeys.remove(processedKeys.first())
            }

            Log.d(TAG, "Phát hiện tin mới: " + message)

            OverlayService.enqueueIncoming(
                this,
                if (packageName == "com.zing.zalo") "Zalo" else "Messenger",
                message
            )
        } finally {
            root.recycle()
        }
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        output: MutableList<Candidate>
    ) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && isMessageText(text)) {
            output.add(Candidate(text, rect.top, rect.bottom, rect.centerX()))
        }

        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty() && isMessageText(description)) {
            output.add(Candidate(description, rect.top, rect.bottom, rect.centerX()))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectCandidates(child, output)
            child.recycle()
        }
    }

    private fun isIncomingMessage(candidate: Candidate, screenWidth: Int): Boolean {
        return screenWidth > 0 && candidate.centerX < screenWidth * incomingRightLimit
    }

    private fun isMessageText(text: String): Boolean {
        val value = normalize(text)
        if (value.length !in 1..1000) return false

        val lower = value.lowercase()
        val ignored = setOf(
            "gửi", "send", "tin nhắn", "nhắn tin", "message",
            "search", "tìm kiếm", "zalo", "messenger",
            "trực tuyến", "online", "đã xem", "seen",
            "thêm", "more", "quay lại", "back"
        )

        if (lower in ignored) return false
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        return true
    }

    private fun normalize(value: String): String =
        value.replace("\\s+".toRegex(), " ").trim()

    fun fillInputAndSend(reply: String): Boolean {
        val text = reply.trim()

        if (text.isEmpty()) {
            Log.e(TAG, "Không có nội dung để gửi")
            OverlayService.reportAccessibilityResult(this, "Không có nội dung để gửi")
            return false
        }

        val root = rootInActiveWindow
        if (root == null) {
            Log.e(TAG, "rootInActiveWindow = null")
            OverlayService.reportAccessibilityResult(
                this,
                "Không đọc được màn hình Zalo/Messenger"
            )
            return false
        }

        try {
            val input = findInput(root)

            if (input == null) {
                Log.e(TAG, "KHÔNG TÌM THẤY Ô NHẬP")
                OverlayService.reportAccessibilityResult(
                    this,
                    "Không tìm thấy ô nhập liệu"
                )
                dumpInteractiveNodes(root)
                return false
            }

            Log.d(TAG, "ĐÃ TÌM THẤY Ô NHẬP: " + describeNode(input))

            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

            val filled = input.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )

            if (!filled) {
                Log.e(TAG, "ACTION_SET_TEXT thất bại")
                OverlayService.reportAccessibilityResult(
                    this,
                    "Đã tìm thấy ô nhập nhưng không thể điền nội dung"
                )
                return false
            }

            Log.d(TAG, "ĐÃ ĐIỀN NỘI DUNG VÀO Ô NHẬP")
            Thread.sleep(200)

            val updatedRoot = rootInActiveWindow
            if (updatedRoot == null) {
                Log.e(TAG, "Không lấy lại được màn hình sau khi điền")
                OverlayService.reportAccessibilityResult(
                    this,
                    "Không đọc lại được nút Gửi"
                )
                return false
            }

            try {
                val send = findSendButton(updatedRoot, input)

                if (send == null) {
                    Log.e(TAG, "KHÔNG TÌM THẤY NÚT GỬI")
                    OverlayService.reportAccessibilityResult(
                        this,
                        "Không tìm thấy nút Gửi"
                    )
                    dumpInteractiveNodes(updatedRoot)
                    return false
                }

                Log.d(TAG, "ĐÃ TÌM THẤY NÚT GỬI: " + describeNode(send))

                val clicked = send.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                if (!clicked) {
                    Log.e(TAG, "ACTION_CLICK nút Gửi thất bại")
                    OverlayService.reportAccessibilityResult(
                        this,
                        "Đã tìm thấy nút Gửi nhưng không nhấn được"
                    )
                    return false
                }

                Log.d(TAG, "ĐÃ NHẤN NÚT GỬI THÀNH CÔNG")
                OverlayService.reportAccessibilityResult(
                    this,
                    "Đã điền và nhấn Gửi thành công"
                )
                return true
            } finally {
                updatedRoot.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi fillInputAndSend", e)
            OverlayService.reportAccessibilityResult(
                this,
                "Lỗi Trợ năng: " + (e.message ?: "không xác định")
            )
            return false
        } finally {
            root.recycle()
        }
    }

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isVisibleToUser && node.isEnabled && node.isEditable) {
                candidates.add(node)
                Log.d(TAG, "INPUT CANDIDATE: " + describeNode(node))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { scoreInput(it) }
    }

    private fun scoreInput(node: AccessibilityNodeInfo): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val hint =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.lowercase().orEmpty()
            } else {
                ""
            }

        val className = node.className?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()

        val label = "$text $desc $hint $className $viewId"
        var score = 0

        if ("tin nhắn" in label || "nhắn tin" in label || "message" in label) score += 120
        if ("chat" in label || "composer" in label || "input" in label) score += 70
        if ("edittext" in className) score += 40
        if ("message" in viewId || "input" in viewId || "composer" in viewId) score += 60

        if (rect.bottom > resources.displayMetrics.heightPixels * 0.65) score += 100
        if (rect.width() > 120) score += 20
        if (rect.height() in 30..250) score += 15

        return score
    }

    private fun findSendButton(
        root: AccessibilityNodeInfo,
        input: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        val inputRect = Rect()
        input.getBoundsInScreen(inputRect)

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isVisibleToUser && node.isEnabled && node.isClickable) {
                candidates.add(node)
                Log.d(TAG, "SEND CANDIDATE: " + describeNode(node))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        val ranked = candidates
            .map { it to scoreSendButton(it, inputRect) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        ranked.take(10).forEach {
            Log.d(TAG, "SEND RANK score=" + it.second + " " + describeNode(it.first))
        }

        return ranked.firstOrNull()?.first
    }

    private fun scoreSendButton(
        node: AccessibilityNodeInfo,
        inputRect: Rect
    ): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val className = node.className?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val label = "$text $desc $viewId"

        var score = 0

        if ("gửi" in label || "send" in label) score += 180
        if ("send_message" in label || "sendmessage" in label) score += 120
        if ("btn_send" in label || "button_send" in label) score += 100
        if ("imagebutton" in className || "button" in className) score += 25

        val sameRow =
            rect.bottom >= inputRect.top - 100 &&
                rect.top <= inputRect.bottom + 100

        if (sameRow) score += 100
        if (rect.centerX() >= inputRect.right - 30) score += 80
        if (rect.top > resources.displayMetrics.heightPixels * 0.60) score += 60
        if (rect.width() in 25..300 && rect.height() in 25..300) score += 20

        val distance = abs(rect.centerX() - inputRect.right)
        if (distance < resources.displayMetrics.widthPixels * 0.35) score += 50

        return score
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()?.replace("\\s+".toRegex(), " ").orEmpty()
        val desc = node.contentDescription?.toString()?.replace("\\s+".toRegex(), " ").orEmpty()
        val hint =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.replace("\\s+".toRegex(), " ").orEmpty()
            } else {
                ""
            }

        return "class=" + node.className +
            "; id=" + node.viewIdResourceName +
            "; text=" + text +
            "; desc=" + desc +
            "; hint=" + hint +
            "; bounds=" + rect +
            "; clickable=" + node.isClickable +
            "; editable=" + node.isEditable
    }

    private fun dumpInteractiveNodes(root: AccessibilityNodeInfo) {
        Log.e(TAG, "===== DUMP INTERACTIVE NODES =====")

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var count = 0

        while (queue.isNotEmpty() && count < 150) {
            val node = queue.removeFirst()

            if (node.isVisibleToUser && (node.isClickable || node.isEditable)) {
                Log.e(TAG, "#" + count + " " + describeNode(node))
                count++
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        Log.e(TAG, "===== END DUMP (" + count + " nodes) =====")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
    }
}
