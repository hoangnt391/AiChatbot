import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'config.dart';

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
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: Colors.blue,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  static const MethodChannel _channel = MethodChannel('aichatbot/native');

  Future<void> _openOverlaySettings() async {
    try {
      await _channel.invokeMethod('openOverlaySettings');
    } catch (_) {
      // Native side can handle devices where the setting is unavailable.
    }
  }

  Future<void> _openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibility');
    } catch (_) {}
  }

  Future<void> _startAssistant(BuildContext context) async {
    if (API_KEY.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Bạn chưa điền API_KEY trong lib/config.dart'),
        ),
      );
      return;
    }

    try {
      await _channel.invokeMethod('startAssistant');
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã bắt đầu Trợ lý')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể bắt đầu Trợ lý: $e')),
        );
      }
    }
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
          const Text(
            'Đọc trực tiếp nội dung hội thoại đang mở bằng Trợ năng và gợi ý trả lời cho Messenger/Zalo.',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 28),

          _SettingButton(
            icon: Icons.layers_outlined,
            title: 'Hiển thị trên ứng dụng khác',
            subtitle: 'Cho phép AiChatBot hiển thị bong bóng nổi',
            onPressed: _openOverlaySettings,
          ),

          const SizedBox(height: 12),

          _SettingButton(
            icon: Icons.accessibility_new,
            title: 'Trợ năng',
            subtitle: 'Đọc trực tiếp văn bản trên màn hình hội thoại',
            onPressed: _openAccessibilitySettings,
          ),

          const SizedBox(height: 20),

          SizedBox(
            height: 54,
            child: FilledButton.icon(
              onPressed: () => _startAssistant(context),
              icon: const Icon(Icons.play_arrow),
              label: const Text(
                'Bắt đầu Trợ lý',
                style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
              ),
            ),
          ),

          const SizedBox(height: 28),

          const Card(
            child: Padding(
              padding: EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Hướng dẫn sử dụng',
                    style: TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  SizedBox(height: 12),
                  Text(
                    '1. Mở "Hiển thị trên ứng dụng khác" và cấp quyền cho AiChatBot.\n\n'
                    '2. Mở "Trợ năng", tìm AiChatBot và bật dịch vụ.\n\n'
                    '3. Điền API_KEY trong lib/config.dart rồi build ứng dụng.\n\n'
                    '4. Nhấn "Bắt đầu Trợ lý". Bong bóng AiChatBot sẽ xuất hiện trên màn hình.\n\n'
                    '5. Khi có tin nhắn mới trên Messenger hoặc Zalo, Trợ lý sẽ dùng ngữ cảnh hội thoại để tạo câu trả lời gợi ý.\n\n'
                    '6. Trong bong bóng, bạn có thể xem gợi ý, sửa nội dung hoặc sao chép để gửi.',
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

class _SettingButton extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onPressed;

  const _SettingButton({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(child: Icon(icon)),
        title: Text(
          title,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onPressed,
      ),
    );
  }
}
