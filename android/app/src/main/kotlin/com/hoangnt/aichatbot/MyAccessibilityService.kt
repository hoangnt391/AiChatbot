package com.hoangnt391.aichatbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    data class Candidate(val text: String, val top: Int, val bottom: Int, val centerX: Int)

    companion object {
        @Volatile var instance: MyAccessibilityService? = null
    }

    private var initialized = false
    private var previousCounts: Map<String, Int> = emptyMap()
    private val processedKeys = LinkedHashSet<String>()
    private val incomingRightLimit = 0.58f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AiChatAccessibility", "AccessibilityService connected")
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
        val candidates = mutableListOf<Candidate>()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        collectCandidates(root, candidates)
        root.recycle()

        val incoming = candidates
            .distinctBy { "${normalize(it.text)}|${it.top}|${it.bottom}|${it.centerX}" }
            .filter { isIncomingMessage(it, metrics.widthPixels) }
            .sortedBy { it.top }

        if (incoming.isEmpty()) return

        val currentCounts = incoming.groupingBy { normalize(it.text) }.eachCount()
        if (!initialized) {
            initialized = true
            previousCounts = currentCounts
            return
        }

        val newCandidates = incoming.filter {
            currentCounts[normalize(it.text)]!! > (previousCounts[normalize(it.text)] ?: 0)
        }
        previousCounts = currentCounts
        val newest = newCandidates.maxByOrNull { it.bottom } ?: return

        val text = newest.text.trim()
        if (text.isEmpty()) return
        val key = packageName + "|" + normalize(text) + "|" + newest.top + "|" + newest.bottom
        if (!processedKeys.add(key)) return
        while (processedKeys.size > 100) {
            processedKeys.remove(processedKeys.first())
        }

        Log.d("AiChatAccessibility", "Phát hiện tin mới: $message")

        OverlayService.enqueueIncoming(
            this,
            if (packageName == "com.zing.zalo") "Zalo" else "Messenger",
            text
        )
    }

    private fun collectCandidates(node: AccessibilityNodeInfo, output: MutableList<Candidate>) {
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

    private fun normalize(value: String): String = value.replace("\\s+".toRegex(), " ").trim()

    fun fillAndSend(reply: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val input = findInput(root)
        if (input == null) {
            root.recycle()
            return false
        }

        val filled = input.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    reply
                )
            }
        )
        if (!filled) {
            root.recycle()
            return false
        }

        Thread.sleep(150)
        val send = findSendButton(root)
        val sent = send?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!sent) {
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
        }
        root.recycle()
        return sent
    }

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isVisibleToUser && node.isEnabled) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val label = "$text $desc"
            if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                ("gửi" in label || "send" in label || "send message" in label)) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    override fun onInterrupt() {}
}
