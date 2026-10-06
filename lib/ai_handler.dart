import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'config.dart';
import 'session_manager.dart';

class AiHandler {
  static const _prompt = '''
Bạn đang nhắn tin như một người Việt bình thường.
Trả lời ngắn gọn 1-2 câu, tự nhiên, đời thường, không trang trọng, không dài dòng.
Không nói mình là AI. Chỉ trả về câu có thể gửi ngay.
''';

  Future<String> reply(String incoming) async {
    await Future<void>.delayed(const Duration(seconds: AUTO_REPLY_DELAY_SECONDS));
    final history = await SessionManager().getHistory();
    final messages = <Map<String, String>>[
      {'role': 'system', 'content': _prompt},
      ...history,
      {'role': 'user', 'content': incoming},
    ];
    final response = await http.post(
      Uri.parse(API_URL),
      headers: {
        'Authorization': 'Bearer ${API_KEY.trim()}',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'model': MODEL,
        'messages': messages,
        'temperature': 0.8,
        'max_tokens': 120,
      }),
    ).timeout(const Duration(seconds: 30));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('OpenAI HTTP ${response.statusCode}');
    }
    final data = jsonDecode(response.body);
    final text = data['choices']?[0]?['message']?['content']?.toString().trim();
    if (text == null || text.isEmpty) throw Exception('AI không trả về nội dung');
    await SessionManager().addMessage(role: 'user', content: incoming);
    await SessionManager().addMessage(role: 'assistant', content: text);
    return text;
  }
}
