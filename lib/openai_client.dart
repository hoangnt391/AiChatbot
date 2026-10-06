import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'config.dart';

class OpenAIClient {
  static const Duration timeout = Duration(seconds: 30);

  static const String systemPrompt = '''
Bạn đang giúp người dùng soạn tin nhắn như một người Việt bình thường.
- Chỉ trả lời ngắn gọn 1-2 câu.
- Giọng văn tự nhiên, đời thường, giống người Việt nhắn tin hằng ngày.
- Có thể dùng "nè", "nhỉ", "đấy", "à", "ừ" khi phù hợp.
- Có thể thêm biểu cảm nhẹ như 😊 hoặc 😉 khi phù hợp.
- Không trang trọng, không dài dòng.
- Không nói mình là AI.
- Chỉ trả về câu có thể gửi ngay.
''';

  Future<String> getReply({
    required String message,
    List<Map<String, String>> history = const [],
  }) async {
    final key = API_KEY.trim();
    if (key.isEmpty) {
      throw const OpenAIException('Chưa cấu hình API_KEY trong lib/config.dart.');
    }

    final messages = <Map<String, String>>[
      {'role': 'system', 'content': systemPrompt},
      ...history,
      {'role': 'user', 'content': message},
    ];

    try {
      final response = await http.post(
        Uri.parse(API_URL),
        headers: {
          'Authorization': 'Bearer $key',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': MODEL,
          'messages': messages,
          'temperature': 0.8,
          'max_tokens': 120,
        }),
      ).timeout(timeout);

      if (response.statusCode < 200 || response.statusCode >= 300) {
        var detail = 'HTTP ${response.statusCode}';
        try {
          final data = jsonDecode(response.body);
          final apiMessage = data['error']?['message'];
          if (apiMessage is String && apiMessage.isNotEmpty) {
            detail = '$detail: $apiMessage';
          }
        } catch (_) {}
        throw OpenAIException(detail);
      }

      final data = jsonDecode(response.body);
      final content = data['choices']?[0]?['message']?['content']?.toString().trim();

      if (content == null || content.isEmpty) {
        throw const OpenAIException('OpenAI không trả về nội dung câu trả lời.');
      }
      return content;
    } on TimeoutException {
      throw const OpenAIException('Kết nối OpenAI quá lâu. Kiểm tra mạng rồi thử lại.');
    } on http.ClientException catch (e) {
      throw OpenAIException('Lỗi kết nối OpenAI: ${e.message}');
    } on OpenAIException {
      rethrow;
    } on FormatException {
      throw const OpenAIException('Phản hồi từ OpenAI không hợp lệ.');
    } catch (e) {
      throw OpenAIException('Không thể gọi OpenAI: $e');
    }
  }
}

class OpenAIException implements Exception {
  final String message;
  const OpenAIException(this.message);
  @override
  String toString() => message;
}
