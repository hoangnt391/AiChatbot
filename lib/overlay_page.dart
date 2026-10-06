import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'session_manager.dart';

class OverlayPage extends StatefulWidget {
  final String suggestion;
  final bool loading;
  final bool autoMode;
  final String status;
  final VoidCallback? onClose;
  final VoidCallback? onStop;
  final ValueChanged<bool>? onAutoChanged;

  const OverlayPage({
    super.key,
    this.suggestion = '',
    this.loading = false,
    this.autoMode = false,
    this.status = 'Đang chờ...',
    this.onClose,
    this.onStop,
    this.onAutoChanged,
  });

  @override
  State<OverlayPage> createState() => _OverlayPageState();
}

class _OverlayPageState extends State<OverlayPage> {
  late String suggestion;

  @override
  void initState() {
    super.initState();
    suggestion = widget.suggestion;
  }

  Future<void> _copy() async {
    if (suggestion.trim().isEmpty) return;
    await Clipboard.setData(ClipboardData(text: suggestion));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã sao chép')),
      );
    }
  }

  Future<void> _edit() async {
    final controller = TextEditingController(text: suggestion);
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sửa câu trả lời'),
        content: TextField(controller: controller, maxLines: 5, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text.trim()),
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (result != null && result.isNotEmpty) setState(() => suggestion = result);
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        width: 360,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(20),
          boxShadow: const [
            BoxShadow(blurRadius: 20, offset: Offset(0, 8), color: Color(0x55000000)),
          ],
        ),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            const Expanded(
              child: Text('💡 AiChatBot',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            ),
            IconButton(onPressed: widget.onClose, icon: const Icon(Icons.close)),
          ]),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Tự động trả lời'),
            subtitle: Text(widget.autoMode ? 'BẬT — tự điền và gửi' : 'TẮT — chỉ gợi ý'),
            value: widget.autoMode,
            onChanged: widget.onAutoChanged,
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: Text(widget.status, style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
          const SizedBox(height: 10),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
            ),
            child: SelectableText(suggestion.isEmpty ? 'Chưa có gợi ý.' : suggestion),
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _copy, icon: const Icon(Icons.copy), label: const Text('Sao chép'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: _edit, icon: const Icon(Icons.edit), label: const Text('Sửa'),
              ),
            ),
          ]),
          const SizedBox(height: 6),
          TextButton.icon(
            onPressed: widget.onStop,
            icon: const Icon(Icons.stop_circle_outlined),
            label: const Text('Dừng'),
          ),
          TextButton.icon(
            onPressed: () async {
              await SessionManager().clearHistory();
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Đã xóa lịch sử')),
                );
              }
            },
            icon: const Icon(Icons.delete_outline),
            label: const Text('Xóa lịch sử'),
          ),
        ]),
      ),
    );
  }
}
