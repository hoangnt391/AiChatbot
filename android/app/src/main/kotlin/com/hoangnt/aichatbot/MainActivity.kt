package com.hoangnt.aichatbot

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val AI_CHANNEL = "aichatbot/native"

        @Volatile
        private var aiChannel: MethodChannel? = null

        fun requestAiReply(message: String): String {
            val activeChannel = aiChannel
                ?: throw IllegalStateException("Flutter MethodChannel chưa sẵn sàng")

            val future = java.util.concurrent.CompletableFuture<String>()

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                activeChannel.invokeMethod(
                    "generateAiReply",
                    mapOf("message" to message),
                    object : MethodChannel.Result {
                        override fun success(result: Any?) {
                            future.complete(result?.toString().orEmpty())
                        }

                        override fun error(
                            errorCode: String,
                            errorMessage: String?,
                            errorDetails: Any?
                        ) {
                            future.completeExceptionally(
                                IllegalStateException(errorMessage ?: errorCode)
                            )
                        }

                        override fun notImplemented() {
                            future.completeExceptionally(
                                IllegalStateException("Flutter chưa triển khai generateAiReply")
                            )
                        }
                    }
                )
            }

            return future.get(4500, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)

        val nativeChannel = MethodChannel(engine.dartExecutor.binaryMessenger, "aichatbot/native")
        aiChannel = nativeChannel
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openOverlaySettings" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            startActivity(Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + packageName)
                            ))
                        }
                        result.success(true)
                    }

                    "openAccessibility" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(true)
                    }

                    "isAccessibilityEnabled" -> {
                        result.success(isAccessibilityEnabled())
                    }

                    "setApiKey" -> {
                        val key = call.argument<String>("apiKey").orEmpty().trim()
                        getSharedPreferences("aichatbot", MODE_PRIVATE)
                            .edit().putString("api_key", key).apply()
                        result.success(true)
                    }

                    "setAutoMode" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        getSharedPreferences("aichatbot", MODE_PRIVATE)
                            .edit().putBoolean("auto_mode", enabled).apply()
                        AutoReplyService.setAutoMode(enabled)
                        result.success(true)
                    }

                    "getAutoMode" -> {
                        result.success(
                            getSharedPreferences("aichatbot", MODE_PRIVATE)
                                .getBoolean("auto_mode", false)
                        )
                    }

                    "getAutoStatus" -> {
                        result.success(AutoReplyService.getStatus())
                    }

                    "startAssistant" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(this)) {
                            result.error(
                                "OVERLAY_PERMISSION",
                                "Chưa cấp quyền Hiển thị trên ứng dụng khác.",
                                null
                            )
                            return@setMethodCallHandler
                        }

                        if (!isAccessibilityEnabled()) {
                            result.error(
                                "ACCESSIBILITY_DISABLED",
                                "Chưa bật Trợ năng cho AiChatBot.",
                                null
                            )
                            return@setMethodCallHandler
                        }

                        startService(Intent(this, OverlayService::class.java))
                        AutoReplyService.setAutoMode(
                            getSharedPreferences("aichatbot", MODE_PRIVATE)
                                .getBoolean("auto_mode", false)
                        )
                        Toast.makeText(this, "AiChatBot đã sẵn sàng", Toast.LENGTH_SHORT).show()
                        result.success(true)
                    }

                    "stopAssistant" -> {
                        stopService(Intent(this, OverlayService::class.java))
                        AutoReplyService.setAutoMode(false)
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val expected = ComponentName(this, AutoReplyService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
