package com.hoangnt.aichatbot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)

        MethodChannel(
            engine.dartExecutor.binaryMessenger,
            "aichatbot/native"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "openOverlaySettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                    result.success(true)
                }

                "openAccessibility" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(true)
                }

                "startAssistant" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(this)
                    ) {
                        result.error(
                            "OVERLAY_PERMISSION",
                            "Chưa cấp quyền Hiển thị trên ứng dụng khác.",
                            null
                        )
                        return@setMethodCallHandler
                    }

                    try {
                        startService(Intent(this, OverlayService::class.java))
                        Toast.makeText(this, "AiChatBot đã sẵn sàng", Toast.LENGTH_SHORT).show()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("START_FAILED", e.message, null)
                    }
                }

                else -> result.notImplemented()
            }
        }
    }
}
