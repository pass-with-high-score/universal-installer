# Project Instructions

## Workflows & Rules
- **Build Verification:** Bắt buộc build test xong (`./gradlew assembleDebug`) mới được báo "done" hoặc xác nhận hoàn thành tác vụ. Đảm bảo project compile thành công và không có lỗi hồi quy.
- **File Size & Reusability:** No source code file should exceed 500 lines. Proactively split files into logical components. Always prefer reusing or updating existing logic over writing new redundant code.
- **Temporary Scripts Cleanup:** Nếu tạo file Python hoặc script tạm để code/parse data, bắt buộc phải xóa nó đi sau khi dùng xong để tránh rác project.
- **Explicit Instruction Only:** Đảm bảo không tự ý làm hoặc thay đổi các logic không liên quan khi chưa nhận được yêu cầu rõ ràng từ user.
- **Safe Modifications:** Luôn kiểm tra kỹ diff change (so sánh thay đổi) trước và sau khi sửa file để đảm bảo không xóa mất code đang có của user.
- **Pre-Release CI & Fastlane Verification:** Trước khi release/bump version mới:
  - Bắt buộc kiểm tra tính đồng bộ giữa `fastlane/Fastfile` và `.github/workflows/publish-release.yml` (tasks build của các module thay đổi như `:app:bundlePlayRelease`, `:tv:bundlePlayRelease`, `:wearos:bundleRelease`, đường dẫn artifact output).
  - Bắt buộc chạy build test các task release của module có thay đổi (kèm `./gradlew assembleDebug`) trước khi push tag để đảm bảo GitHub Actions hoàn toàn không bị fail.
  - Changelog release trên GitHub phải tổng hợp tất cả các form factor có thay đổi (Phone, Android TV, Wear OS).

## AI Guidance & Custom Skills
This project uses specialized Gemini CLI skills to automate complex tasks. When you encounter the following scenarios, you SHOULD activate and follow the corresponding skill:

- **Implementing New Features:** Use the `cook` skill. It provides a structured "kitchen" workflow (Research -> Recipe -> Cook -> Taste Test).
  - Location: `.gemini/skills/cook/SKILL.md`
- **Releasing New Versions:** Use the `upgrade-app` skill. It automates version bumping, changelog generation, tagging, and GitHub releases.
  - Location: `.gemini/skills/upgrade-app/SKILL.md`
- **Translating CSV Files:** Use the `csv-translator` skill. It handles splitting large files, translation, and auto-importing strings into Android resources.
  - Location: `.gemini/skills/csv-translator/SKILL.md`

Always refer to the project-specific memory in `.gemini/tmp/universal-installer/memory/MEMORY.md` for private local context.
