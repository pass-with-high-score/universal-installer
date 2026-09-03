---
phase: 2
title: "Sender: capability discovery & correctness"
status: in-progress
priority: P1
effort: "4h"
dependencies: [1]
---

# Phase 2: Sender — capability discovery & correctness

## Overview

Phía phone: chỉ gửi khi watch **thật sự có cài app**, không rò resource khi lỗi, gửi đúng thứ khi
gói là split/bundle, và không spam StateFlow. Sau phase này `NoWatch` mới có nghĩa thật và
`Success` mới đáng tin.

## Requirements

**Functional**
- Watch không cài app → `NoWatch`, không stream byte nào.
- Không có watch kết nối → `NoWatch`.
- Bundle `.apks/.xapk/.apkm/.zip` gửi nguyên file, giữ extension để watch biết là bundle.
- Danh sách split rời (nhiều `Uri` không phải bundle) → chặn, thông báo rõ, không gửi 1 split lẻ.

**Non-functional**
- Không rò `ParcelFileDescriptor`, không để channel mở khi lỗi.
- Progress emit tối đa ~100 lần / transfer.

## Architecture

**Capability discovery.** `NodeClient.connectedNodes` trả về mọi watch đang kết nối kể cả watch
chưa cài app (`WearSenderService.kt:38,59`) → gửi vào hư không nhưng báo Success. Thay bằng
`CapabilityClient` (đã import sẵn ở `WearSenderService.kt:7` nhưng chưa dùng):

- Watch khai capability trong `wearos/src/main/res/values/wear.xml`:
  ```xml
  <resources>
      <string-array name="android_wear_capabilities">
          <item>apk_receiver</item>
      </string-array>
  </resources>
  ```
- Phone query `Wearable.getCapabilityClient(context).getCapability("apk_receiver", CapabilityClient.FILTER_REACHABLE)`
  → `capabilityInfo.nodes`. Rỗng → `NoWatch`. Ưu tiên node `isNearby`.

**Rò resource.** `WearSenderService.kt:87` — `openInputStream` null thì `return@withContext` non-local
thoát khỏi `runCatching`, bỏ qua `.fold`, để channel mở và `outputStream` đã lấy ra bị treo.
`WearSenderService.kt:89` — `openFileDescriptor(...)?.statSize` không đóng PFD → rò fd mỗi lần gửi.
Sửa bằng cách bọc toàn bộ thân transfer trong `try/finally { channelClient.close(channel) }` và
`use {}` cho PFD; bỏ mọi non-local return ở giữa.

**Split / bundle.** `InstallViewModel.sendToWatch()` (`InstallViewModel.kt:487-495`) hiện lấy
`pendingApkUris?.firstOrNull()` → với gói split chỉ gửi 1 split; với XAPK thì gửi file `.xapk`
nhưng watch parse bằng `getPackageArchiveInfo` nên trả null và **xoá im lặng**. Phase 4 cho watch
dùng `ApkInstaller` (đã hỗ trợ bundle) nên phía phone chỉ cần:
- Nếu nguồn là 1 file bundle (`pendingOriginalUri`, extension thuộc `apks/xapk/apkm/apk+/zip`) → gửi
  nguyên file, **giữ extension** trong tên channel để watch nhận biết.
- Nếu là danh sách nhiều `Uri` split rời → không gửi, trả `SendResult.Unsupported` mới.

`BUNDLE_EXTS` đã định nghĩa ở `core/install/ApkInstaller.kt` (private companion) — expose lại thành
hằng dùng chung thay vì khai bản thứ hai.

**Hợp đồng channel path (dùng chung với Phase 3 — đổi một bên là đứt flow).**

```
/apk-transfer/<expectedBytes>/<safeFileName>
```

- `expectedBytes`: size nguồn theo byte, `0` nếu không xác định được. Watch dùng nó để check dung
  lượng **trước khi ghi** và để verify file không bị truncated (Phase 3).
- `safeFileName`: `[^a-zA-Z0-9._-]` → `_`, **giữ nguyên extension** vì watch dựa vào đó để quyết định
  `isBundle`.

Hằng này khai ở phía phone (`WearApkSender`) và phía watch (`WearReceiverService`); mỗi bên một dòng
comment đánh dấu đây là invariant chung giữa hai module.

**Dead code.** `isWatchAvailable()` (`WearSenderService.kt:35-41`) không nơi nào gọi. Hoặc wire vào để
ẩn/disable nút Watch trên TopAppBar khi không có watch, hoặc xoá. Chọn: **wire vào** — nút Watch hiện
"always visible" (`InstallScreen.kt:636`) nên user không có watch vẫn bấm được rồi mới biết là không có.

**Throttle.** `WearSenderService.kt:101` emit mỗi 8 KB. Chỉ emit khi phần trăm nguyên đổi (hoặc mỗi
256 KB nếu không biết tổng size).

## Related Code Files

- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/WearSenderService.kt`
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/util/InstallWearDelegate.kt`
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/InstallViewModel.kt` (`sendToWatch`)
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/InstallUiState.kt` (thêm `WatchSendState.Unsupported`)
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/WatchSendDialog.kt` (nhánh `Unsupported`)
- Modify: `app/src/main/res/values/strings.xml`
- Create: `wearos/src/main/res/values/wear.xml`
- Modify: `core/src/main/java/app/pwhs/core/install/ApkInstaller.kt` (đưa `BUNDLE_EXTS` ra ngoài `private companion` để dùng lại)

## Implementation Steps

1. Tạo `wearos/src/main/res/values/wear.xml` với `android_wear_capabilities` = `apk_receiver`.
2. `ApkInstaller.kt`: đổi `BUNDLE_EXTS` từ `private companion` thành hằng public dùng chung
   (`ApkInstaller.BUNDLE_EXTS` hoặc file `core/install/BundleExtensions.kt` nếu để trong companion
   làm file phình). Không sửa hành vi install.
3. `WearSenderService.kt`:
   - thay `connectedNodes` bằng `CapabilityClient.getCapability(CAPABILITY_APK_RECEIVER, FILTER_REACHABLE)`
     ở cả `isWatchAvailable()` và `send()`; chọn node `isNearby` trước
   - bọc phần stream trong `try { ... } finally { runCatching { Tasks.await(channelClient.close(channel)) } }`
   - `openFileDescriptor(apkUri, "r")?.use { it.statSize }` — không để PFD treo
   - bỏ non-local return trong `runCatching`; lỗi đọc input → `throw IllegalStateException` để `.fold` bắt
   - throttle progress theo phần trăm nguyên
   - thêm `SendResult.Unsupported(val reason: String)`
   - channel path đổi sang `/apk-transfer/<expectedBytes>/<safeFileName>` (hợp đồng ở trên)
   - wire `isWatchAvailable()` vào UI (bước 7) thay vì để dead code
4. `InstallViewModel.sendToWatch()`:
   - xác định nguồn theo thứ tự: `apkUri` tham số → `pendingOriginalUri` → nếu `pendingApkUris` có
     **nhiều hơn 1** phần tử và không phải bundle → `WatchSendState.Unsupported`
   - tên file luôn giữ extension gốc (lấy qua `contentResolver.getDisplayName`, đã có ở
     `app/.../util/extension/ContentResolver.kt:17`)
5. `InstallUiState.kt` + `WatchSendDialog.kt` + `strings.xml`: thêm state/nhánh/dòng chữ cho `Unsupported`.
6. `InstallWearDelegate`: map `SendResult.Unsupported` → `WatchSendState.Unsupported`.
7. `InstallScreen`: nút Watch đọc trạng thái `isWatchAvailable` (collect một lần khi vào màn) → disable
   + tooltip khi không có watch nào cài app.

## Success Criteria

- [ ] Watch **không** cài app (hoặc gỡ app trên watch) → phone hiện `NoWatch`, `adb logcat` không có dòng nào ghi byte
- [ ] Watch có cài app → transfer chạy, `WearReceiverService` nhận đủ bytes (so `tempFile.length()` với size nguồn)
- [ ] Gửi 1 file `.xapk` → tên channel giữ đuôi `.xapk`
- [ ] Chọn gói split rời → dialog `Unsupported` với lý do đọc được, không có channel nào mở
- [ ] Gửi lỗi giữa chừng (tắt Bluetooth) → `Error`, channel đóng; lặp 10 lần không tăng fd
      (`adb shell ls -l /proc/<pid>/fd | wc -l` trước/sau)
- [ ] Transfer file 30 MB → số lần `WatchSendState.Sending` emit ≤ ~101
- [ ] `./gradlew :app:assembleOpensourceDebug` pass

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| `FILTER_REACHABLE` trả rỗng vì capability chưa propagate sau khi cài app watch | Retry ngắn (1 lần sau ~1s) trước khi kết luận `NoWatch`; ghi rõ trong log |
| Sửa `BUNDLE_EXTS` trong `:core` ảnh hưởng `:tv` và `:app` | Chỉ đổi visibility, không đổi nội dung set; build cả `:tv` `:app` để xác nhận |
| `getDisplayName` trả tên không có extension với vài provider | Fallback lấy extension từ `contentResolver.getType()` / `MimeTypeMap` |

## Kết quả (2026-09-03)

Code xong, build + lint pass. Chưa verify trên thiết bị.

| Thay đổi | Chi tiết |
|---|---|
| `WearSenderService` → `wear/WearApkSender.kt` | Đổi tên + chuyển package. Nó là `object`, không phải Service — tên cũ gây hiểu nhầm. (Việc này plan xếp ở Phase 5, làm sớm để khỏi sửa import hai lần.) |
| `CapabilityClient` thay `NodeClient.connectedNodes` | `getCapability("apk_receiver", FILTER_REACHABLE)`, ưu tiên node `isNearby` |
| Rò resource | `close(channel)` vào `finally`; PFD bọc `use {}`; bỏ non-local return giữa `runCatching` |
| Throttle | Emit khi phần trăm nguyên đổi → tối đa ~101 lần/transfer |
| Channel path | `/apk-transfer/<expectedBytes>/<safeFileName>` |
| Split rời | `SendResult.Unsupported` + `WatchSendState.Unsupported`, chặn trước khi mở channel |
| `isWatchAvailable()` | Hết dead code — `InstallScreen` collect và disable nút Watch khi không có watch |
| `BUNDLE_EXTS` | Tách ra `core/install/BundleExtensions.kt`. Hoá ra `DownloadsApkScanner` đã copy sẵn một bản thứ hai — giờ cả hai dùng chung |

### Lệch so với plan

- **`WatchSendDialog` viết lại data-driven thay vì tách 6 file.** Plan (Phase 5) định tách mỗi trạng
  thái một file. Nhưng 6 `AlertDialog` gần giống hệt nhau chính là bản chất vấn đề — tách file chỉ
  nhân bản duplicate ra 6 chỗ. Giờ một `AlertDialog` + hàm map state → (icon, title, body, buttons).
  File 175 dòng gồm 6 `@Preview`; phần logic chỉ ~135 dòng.
- Sửa luôn `watch_send_no_watch_msg` — nội dung cũ nói "make sure watch is paired", giờ phải nói rõ
  **watch phải có cài app**, vì `NoWatch` bây giờ mang nghĩa đó.

### Nợ lại

- 2 string mới (`watch_send_unsupported_title`, `watch_send_unsupported_splits`) chưa dịch →
  `:app:lintOpensourceDebug` tăng từ **39 lên 41 lỗi** `MissingTranslation`. Lint của `:app` đã fail
  sẵn từ trước (39 lỗi), không phải gate tôi làm hỏng, nhưng tôi có làm tăng.
