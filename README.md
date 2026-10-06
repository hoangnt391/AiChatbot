# AiChatBot

Ứng dụng Flutter Android hỗ trợ gợi ý trả lời tin nhắn Messenger và Zalo.

## Chức năng
- Bong bóng nổi kéo thả.
- Accessibility Service theo dõi giao diện Messenger/Zalo.
- Gọi OpenAI GPT-4o-mini.
- Gợi ý trả lời: Sao chép / Sửa / Đóng.
- Lưu lịch sử hội thoại cục bộ.
- Văn phong tiếng Việt ngắn gọn, tự nhiên.

## Cấu trúc chính
- `lib/main.dart` — giao diện, lưu lịch sử và OpenAI client.
- `android/app/src/main/kotlin/com/hoangnt/aichatbot/MessageAccessibilityService.kt` — nhận sự kiện Messenger/Zalo.
- `android/app/src/main/kotlin/com/hoangnt/aichatbot/OverlayService.kt` — bong bóng nổi.
- `android/app/src/main/AndroidManifest.xml` — quyền và service Android.

## Chạy
```bash
flutter pub get
flutter run
```

Sau khi cài: nhập API key, cấp quyền Trợ năng và quyền hiển thị trên ứng dụng khác.

**Không commit API key thật vào Git.**
