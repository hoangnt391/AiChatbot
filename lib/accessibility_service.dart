import 'package:flutter/services.dart';

/// Cầu nối điều khiển Trợ năng Android.
/// Việc quét node, điền ô nhập và thao tác nút Gửi được thực hiện bởi
/// MyAccessibilityService ở Android native.
class AccessibilityServiceBridge {
  static const MethodChannel _channel = MethodChannel('aichatbot/native');

  Future<void> start() => _channel.invokeMethod('startAssistant');

  Future<void> stop() => _channel.invokeMethod('stopAssistant');

  Future<void> setApiKey(String key) =>
      _channel.invokeMethod('setApiKey', {'apiKey': key});
}
