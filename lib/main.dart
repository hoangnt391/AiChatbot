import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'config.dart';
import 'openai_client.dart';

void main() {
  runApp(const AiChatBotApp());
}

class AiChatBotApp extends StatelessWidget {
  const AiChatBotApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AiChatBot',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  static const MethodChannel _channel = MethodChannel('aichatbot/native');

  bool _autoMode = false;
  bool _accessibilityEnabled = false;
  bool _running = false;
  String _status = 'Chưa khởi động';
  Timer? _statusTimer;
  final TextEditingController _apiKeyController = TextEditingController();
  String _apiKey = '';

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleNativeCall);
    _loadApiKey();
    _refreshState();
    _statusTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) => _refreshState(),
    );
  }

  @override
  void dispose() {
    _statusTimer?.cancel();
    _apiKeyController.dispose();
    super.dispose();
  }

  Future<dynamic> _handleNativeCall(MethodCall call) async {
    if (call.method == 'generateAiReply') {
      final message = call.arguments is Map
          ? (call.arguments['message']?.toString() ?? '')
          : '';
      if (message.trim().isEmpty) {
        throw PlatformException(
          code: 'EMPTY_MESSAGE',
          message: 'Tin nhắn đầu vào rỗng',
        );
      }

      try {
        final history = <Map<String, String>>[];
        final reply = await OpenAIClient().getReply(
          message: message.trim(),
          apiKey: _apiKey,
          history: history,
        );
        return reply;
      } on OpenAIException catch (e) {
        throw PlatformException(code: 'OPENAI_ERROR', message: e.message);
      } catch (e) {
        throw PlatformException(
          code: 'AI_ERROR',
          message: e.toString(),
        );
      }
    }
    return null;
  }

  Future<void> _loadApiKey() async {
    try {
      final saved = await _channel.invokeMethod<String>('getApiKey') ?? '';
      if (!mounted) return;
      setState(() {
        _apiKey = saved;
        _apiKeyController.text = saved;
      });
    } catch (_) {}
  }

  Future<void> _saveApiKey() async {
    final key = _apiKeyController.text.trim();
    if (key.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('API Key không được để trống')));
      return;
    }
    await _channel.invokeMethod('setApiKey', {'apiKey': key});
    if (!mounted) return;
    setState(() => _apiKey = key);
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã lưu API Key')));
  }

  Future<void> _refreshState() async {
    try {
      final enabled =
          await _channel.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
      final auto =
          await _channel.invokeMethod<bool>('getAutoMode') ?? false;
      final status =
          await _channel.invokeMethod<String>('getAutoStatus') ?? _status;

      if (!mounted) return;
      setState(() {
        _accessibilityEnabled = enabled;
        _autoMode = auto;
        _status = status;
      });
    } catch (_) {}
  }

  Future<void> _openOverlaySettings() async {
    await _channel.invokeMethod('openOverlaySettings');
  }

  Future<void> _openAccessibilitySettings() async {
    await _channel.invokeMethod('openAccessibility');
  }

  Future<void> _setAutoMode(bool value) async {
    try {
      await _channel.invokeMethod('setAutoMode', {'enabled': value});
      if (mounted) {
        setState(() {
          _autoMode = value;
          _status = value
              ? 'Tự động BẬT — đang chờ tin mới'
              : 'Tự động TẮT';
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không đổi được chế độ tự động: $e')),
        );
      }
    }
  }

  Future<void> _startAssistant() async {
    if (_apiKey.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Chưa nhập API Key. Hãy nhập và bấm Lưu trước.'),
        ),
      );
      return;
    }

    final accessibility = await _channel.invokeMethod<bool>(
          'isAccessibilityEnabled',
        ) ??
        false;

    if (!accessibility) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Hãy bật Trợ năng cho AiChatBot trước.'),
          ),
        );
      }
      await _openAccessibilitySettings();
      return;
    }

    try {
      await _channel.invokeMethod('setApiKey', {'apiKey': _apiKey});
      await _channel.invokeMethod('startAssistant');

      if (!mounted) return;
      setState(() {
        _running = true;
        _status = 'Đã bắt đầu — đang chờ tin mới';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('AiChatBot đã bắt đầu')),
      );
    } on PlatformException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message ?? 'Không thể bắt đầu Trợ lý')),
      );
    }
  }

  Future<void> _stopAssistant() async {
    await _channel.invokeMethod('stopAssistant');
    if (!mounted) return;
    setState(() {
      _running = false;
      _autoMode = false;
      _status = 'Đã dừng';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AiChatBot'),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Icon(Icons.smart_toy_outlined, size: 64),
          const SizedBox(height: 12),
          const Text(
            'AiChatBot',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Package: $packageName',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12),
          ),
          const SizedBox(height: 20),

          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('API Key', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _apiKeyController,
                    obscureText: false,
                    autocorrect: false,
                    enableSuggestions: false,
                    decoration: const InputDecoration(
                      hintText: 'Nhập OpenAI API Key (sk-...)',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (value) => _apiKey = value,
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _saveApiKey,
                      icon: const Icon(Icons.save_outlined),
                      label: const Text('Lưu API Key'),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(_apiKey.trim().isEmpty ? 'Chưa lưu API Key' : 'Đã có API Key được lưu trên máy'),
                ],
              ),
            ),
          ),

          const SizedBox(height: 10),

          Card(
            child: ListTile(
              leading: Icon(
                _accessibilityEnabled
                    ? Icons.check_circle
                    : Icons.error_outline,
              ),
              title: const Text('Quyền Trợ năng'),
              subtitle: Text(
                _accessibilityEnabled ? 'Đã bật' : 'Chưa bật',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: _openAccessibilitySettings,
            ),
          ),

          const SizedBox(height: 10),

          Card(
            child: SwitchListTile(
              title: const Text(
                'Tự động trả lời',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: Text(
                _autoMode
                    ? 'BẬT — tự động AI → điền → nhấn Gửi'
                    : 'TẮT — không tự động gửi',
              ),
              value: _autoMode,
              onChanged: _setAutoMode,
            ),
          ),

          const SizedBox(height: 10),

          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(
                    _running ? Icons.radio_button_checked : Icons.pause_circle,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      _status,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 18),

          FilledButton.icon(
            onPressed: _openOverlaySettings,
            icon: const Icon(Icons.layers_outlined),
            label: const Text('Hiển thị trên ứng dụng khác'),
          ),

          const SizedBox(height: 10),

          FilledButton.icon(
            onPressed: _openAccessibilitySettings,
            icon: const Icon(Icons.accessibility_new),
            label: const Text('Mở cài đặt Trợ năng'),
          ),

          const SizedBox(height: 10),

          FilledButton.icon(
            onPressed: _running ? null : _startAssistant,
            icon: const Icon(Icons.play_arrow),
            label: const Text('Bắt đầu Trợ lý'),
          ),

          const SizedBox(height: 10),

          OutlinedButton.icon(
            onPressed: _running ? _stopAssistant : null,
            icon: const Icon(Icons.stop),
            label: const Text('Dừng Trợ lý'),
          ),

          const SizedBox(height: 24),

          const Card(
            child: Padding(
              padding: EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Luồng tự động',
                    style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 12),
                  Text(
                    '1. Mở Zalo hoặc Messenger và vào đúng cuộc trò chuyện.\n\n'
                    '2. AutoReplyService chỉ đọc trực tiếp màn hình của hai ứng dụng này.\n\n'
                    '3. Khi có tin mới và Tự động đang BẬT, bắt đầu bộ đếm 5 giây.\n\n'
                    '4. AI dùng GPT-4o-mini tạo câu trả lời.\n\n'
                    '5. Đủ 5 giây: tìm ô nhập → điền câu trả lời → tìm nút Gửi → mô phỏng nhấn.\n\n'
                    '6. Mọi bước tìm ô nhập/nút Gửi đều ghi log rõ ràng với tag AiChatAutoReply.',
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
