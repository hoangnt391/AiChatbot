import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'session_manager.dart';

class OverlayPage extends StatefulWidget {
  final String suggestion;
  final bool loading;
  final VoidCallback? onClose;
  final VoidCallback? onEdit;

  const OverlayPage({
    super.key,
    this.suggestion = '',
    this.loading = false,
    this.onClose,
    this.onEdit,
  });

  @override
  State<OverlayPage> createState() => _OverlayPageState();
}

class _OverlayPageState extends State<OverlayPage> {
  late String _suggestion;
  late bool _loading;

  @override
  void initState() {
    super.initState();
    _suggestion = widget.suggestion;
    _loading = widget.loading;
  }

  Future<void> _copySuggestion() async {
    if (_suggestion.trim().isEmpty) return;

    await Clipboard.setData(ClipboardData(text: _suggestion));

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Đã sao chép câu trả lời'),
        duration: Duration(seconds: 1),
      ),
    );
  }

  Future<void> _clearHistory() async {
    await SessionManager().clearHistory();

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Đã xóa lịch sử hội thoại'),
        duration: Duration(seconds: 1),
      ),
    );
  }

  Future<void> _editSuggestion() async {
    final controller = TextEditingController(text: _suggestion);

    final result = await showDialog<String>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Sửa câu trả lời'),
          content: TextField(
            controller: controller,
            autofocus: true,
            maxLines: 5,
            decoration: const InputDecoration(
              hintText: 'Nhập câu trả lời...',
              border: OutlineInputBorder(),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, controller.text.trim()),
              child: const Text('Lưu'),
            ),
          ],
        );
      },
    );

    controller.dispose();

    if (result == null || result.isEmpty || !mounted) return;

    setState(() {
      _suggestion = result;
      _loading = false;
    });

    widget.onEdit?.call();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        constraints: const BoxConstraints(
          minWidth: 280,
          maxWidth: 380,
          minHeight: 120,
          maxHeight: 520,
        ),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(20),
          boxShadow: const [
            BoxShadow(
              blurRadius: 20,
              spreadRadius: 2,
              offset: Offset(0, 8),
              color: Color(0x55000000),
            ),
          ],
          border: Border.all(
            color: Color(0x22000000),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '💡 Gợi ý trả lời',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Đóng',
                    onPressed: widget.onClose ?? () => Navigator.maybePop(context),
                    icon: const Icon(Icons.close),
                    visualDensity: VisualDensity.compact,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Flexible(
                child: _loading
                    ? const Padding(
                        padding: EdgeInsets.symmetric(vertical: 28),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            SizedBox(
                              width: 28,
                              height: 28,
                              child: CircularProgressIndicator(strokeWidth: 3),
                            ),
                            SizedBox(height: 12),
                            Text(
                              'Đang soạn...',
                              style: TextStyle(fontSize: 15),
                            ),
                          ],
                        ),
                      )
                    : Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: SelectableText(
                          _suggestion.isEmpty
                              ? 'Chưa có gợi ý trả lời.'
                              : _suggestion,
                          style: const TextStyle(
                            fontSize: 16,
                            height: 1.4,
                          ),
                        ),
                      ),
              ),
              const SizedBox(height: 14),
              if (!_loading && _suggestion.trim().isNotEmpty)
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _copySuggestion,
                        icon: const Icon(Icons.copy, size: 18),
                        label: const Text('Sao chép'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: _editSuggestion,
                        icon: const Icon(Icons.edit, size: 18),
                        label: const Text('Sửa'),
                      ),
                    ),
                  ],
                ),
              const SizedBox(height: 8),
              TextButton.icon(
                onPressed: _clearHistory,
                icon: const Icon(Icons.delete_outline, size: 18),
                label: const Text('Xóa lịch sử hội thoại'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
