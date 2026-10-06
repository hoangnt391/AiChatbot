package com.hoangnt.aichatbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    private var lastMessage = ""
    private var lastSignature = ""
    private var lastEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.zing.zalo" && packageName != "com.facebook.orca") return

        val root = rootInActiveWindow ?: return
        val latest = extractLatestMessage(root) ?: return
        root.recycle()

        val normalized = latest.trim()
        if (normalized.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        val signature = "$packageName|$normalized"

        // Accessibility có thể phát cùng một nội dung nhiều lần.
        if (signature == lastSignature && now - lastEventAt < 1500) return

        lastSignature = signature
        lastEventAt = now
        lastMessage = normalized

        val intent = Intent(this, OverlayService::class.java).apply {
            action = "NEW_MESSAGE"
            putExtra(
                "platform",
                if (packageName == "com.zing.zalo") "Zalo" else "Messenger"
            )
            putExtra("message", normalized)
        }

        startService(intent)
    }

    /**
     * Trích xuất tin nhắn mới nhất.
     *
     * Ưu tiên node có text/contentDescription và nằm trong vùng cuối màn hình.
     * Nếu Zalo/Messenger thay đổi UI, đây là hàm cần điều chỉnh đầu tiên.
     */
    private fun extractLatestMessage(root: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<Pair<Int, String>>()
        collectTextNodes(root, candidates)

        if (candidates.isEmpty()) return null

        // Chọn nội dung ở vị trí thấp nhất trên màn hình.
        candidates.sortBy { it.first }
        return candidates.lastOrNull()?.second
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<Pair<Int, String>>
    ) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && isMessageLike(text)) {
            output.add(nodeTop(node) to text)
        }

        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty() && isMessageLike(description)) {
            output.add(nodeTop(node) to description)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, output)
            child.recycle()
        }
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        return rect.top
    }

    private fun isMessageLike(text: String): Boolean {
        val value = text.trim()
        if (value.length < 1 || value.length > 1000) return false

        // Loại bỏ một số text giao diện phổ biến.
        val ignored = setOf(
            "Gửi", "Send", "Tin nhắn", "Nhắn tin",
            "Message", "Search", "Tìm kiếm", "Zalo", "Messenger"
        )
        return value !in ignored
    }

    override fun onInterrupt() {
        // Bắt buộc bởi AccessibilityService.
    }
}
