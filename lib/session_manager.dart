import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class SessionMessage {
  final String role;
  final String content;

  const SessionMessage({required this.role, required this.content});

  Map<String, dynamic> toJson() => {
        'role': role,
        'content': content,
      };

  factory SessionMessage.fromJson(Map<String, dynamic> json) {
    return SessionMessage(
      role: json['role']?.toString() ?? 'user',
      content: json['content']?.toString() ?? '',
    );
  }
}

class SessionManager {
  static const String _storageKey = 'aichatbot_conversation_history';

  // 10 cặp hội thoại = tối đa 20 tin nhắn (10 user + 10 assistant).
  static const int _maxMessages = 20;

  Future<List<SessionMessage>> getMessages() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);

    if (raw == null || raw.isEmpty) return [];

    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];

      return decoded
          .whereType<Map>()
          .map((item) => SessionMessage.fromJson(
                Map<String, dynamic>.from(item),
              ))
          .where((item) => item.content.isNotEmpty)
          .toList();
    } catch (_) {
      return [];
    }
  }

  Future<List<Map<String, String>>> getHistory() async {
    final messages = await getMessages();
    return messages
        .map((m) => <String, String>{
              'role': m.role,
              'content': m.content,
            })
        .toList();
  }

  Future<void> addMessage({
    required String role,
    required String content,
  }) async {
    final text = content.trim();
    if (text.isEmpty) return;

    final messages = await getMessages();
    messages.add(SessionMessage(role: role, content: text));

    final start = messages.length > _maxMessages
        ? messages.length - _maxMessages
        : 0;

    final limited = messages.sublist(start);
    final prefs = await SharedPreferences.getInstance();

    await prefs.setString(
      _storageKey,
      jsonEncode(limited.map((m) => m.toJson()).toList()),
    );
  }

  Future<void> clearHistory() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_storageKey);
  }
}
