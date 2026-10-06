package com.hoangnt.aichatbot
import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity(){
 override fun configureFlutterEngine(e:FlutterEngine){
  super.configureFlutterEngine(e)
  MethodChannel(e.dartExecutor.binaryMessenger,"aichatbot/native").setMethodCallHandler{call,result->
   if(call.method=="openAccessibility"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));result.success(true)}else result.notImplemented()
  }
 }
}
