---
phase: 5
title: "Transfer lifecycle trên phone"
status: in-progress
priority: P2
effort: "5h"
dependencies: [2]
---

# Phase 5: Transfer lifecycle trên phone

## Overview

Transfer chạy trong `viewModelScope` nên rời màn Install là đứt, và nút Cancel không cancel gì cả.
Phase này chuyển transfer sang foreground service sở hữu vòng đời riêng, cancel thật, và dọn lại
`WatchSendDialog` cho đúng rule composable.

> **Điều kiện scope:** phần foreground service chỉ cần thiết nếu số đo throughput ở Phase 1 bước 8
> cho thấy transfer kéo dài (> ~20s). Nếu đo ra nhanh, phase này rút còn "cancel thật + tách
> `WatchSendDialog`" và bỏ `WearTransferService`/`WearTransferState`. Quyết định dựa trên số đo,
> không dựa trên ước lượng.

## Requirements

**Functional**
- Rời màn Install / khoá màn hình → transfer tiếp tục, notification hiện progress.
- Cancel ở bất kỳ giai đoạn nào → channel đóng, service stop, watch xoá temp file.
- Quay lại màn Install giữa lúc đang truyền → thấy đúng progress hiện tại.
- Chỉ một transfer chạy tại một thời điểm; bấm gửi lần hai khi đang chạy → không tạo transfer thứ hai.

**Non-functional**
- Không composable public nào thiếu `@Preview`; không file UI nào > ~150 dòng.

## Architecture

**Vấn đề.** `InstallWearDelegate` launch trong `viewModelScope` (`InstallWearDelegate.kt:19,25`) →
ViewModel chết là transfer chết. `WatchSendDialog` nút Cancel ở `CheckingWatch` chỉ set `Idle`
(`WatchSendDialog.kt:69`), coroutine vẫn chạy; ở `Sending` thì `onDismissRequest = {}` và không có nút
cancel nào — user bị kẹt cho tới khi xong.

**Mẫu có sẵn để bám theo.** `core/receiver/TvReceiverState.kt` là singleton process-wide làm cầu giữa
foreground service (sở hữu HTTP server) và Compose UI (render trạng thái). Wear transfer cần đúng
hình dạng đó — dùng lại pattern, không phát minh cái mới:

```
WearTransferState (object, wear-side của phone app)
  ├── state: StateFlow<WatchSendState>
  └── update()/reset()  ← chỉ WearTransferService gọi

WearTransferService : Service (foreground, type dataSync)
  ├── onStartCommand(ACTION_SEND, uri, fileName) → job = scope.launch { WearSenderService.send(...) }
  ├── onStartCommand(ACTION_CANCEL) → job.cancel() → finally đóng channel → stopSelf()
  └── notification progress, đã có mẫu ở InstallProgressNotifier

InstallWearDelegate
  ├── watchSendState = WearTransferState.state   (không còn MutableStateFlow riêng)
  ├── sendToWatch() → context.startForegroundService(ACTION_SEND)
  └── cancel()      → context.startService(ACTION_CANCEL)
```

`InstallWearDelegate` thành lớp mỏng chuyển tiếp intent — `InstallUiStateBuilder.kt:43` vẫn đọc
`watchSendState` như cũ nên tầng UI không đổi hợp đồng.

**Cancel thật.** `WearSenderService.send()` chạy trong coroutine hủy được; `Tasks.await()` là blocking
nên phải kiểm `ensureActive()` trong vòng lặp ghi để cancel có hiệu lực trong ≤ 1 chunk. Phase 2 đã
đưa `channelClient.close(channel)` vào `finally` → cancel tự động đóng channel, watch thấy stream đứt
và xoá temp (Phase 3).

**Permission.** `AndroidManifest.xml` của `:app` cần `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_DATA_SYNC`, và service khai `android:foregroundServiceType="dataSync"`.
targetSdk 36 → phải gọi `startForeground()` trong vòng 5s kể từ `startForegroundService()`.

**Dọn `WatchSendDialog`.** File 207 dòng, một composable public, không `@Preview` nào — vi phạm cả
rule "một file một thứ" lẫn rule "@Composable bắt buộc có @Preview". Tách mỗi trạng thái thành một
private composable trong `install/component/watchsend/`, `WatchSendDialog` chỉ còn `when` điều phối,
mỗi file kèm `@Preview` bọc theme.

## Related Code Files

- Create: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/wear/WearTransferState.kt`
- Create: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/wear/WearTransferService.kt`
- Move: `.../install/WearSenderService.kt` → `.../install/wear/WearApkSender.kt` (là `object` thuần, không phải Service — tên hiện tại gây hiểu nhầm)
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/util/InstallWearDelegate.kt`
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/InstallViewModel.kt` (thêm `cancelWatchSend()`)
- Rewrite: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/WatchSendDialog.kt` (chỉ còn `when`)
- Create: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/component/watchsend/*.kt` (mỗi trạng thái 1 file + `@Preview`)
- Modify: `app/src/main/java/app/pwhs/universalinstaller/presentation/install/InstallScreen.kt` (truyền `onCancelWatchSend`)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Reference: `core/receiver/TvReceiverState.kt`, `app/.../install/InstallProgressNotifier.kt`

## Implementation Steps

1. Tạo `WearTransferState` theo mẫu `TvReceiverState` (chỉ `StateFlow<WatchSendState>` + `update`/`reset`).
2. Đổi tên `WearSenderService` → `WearApkSender` (object thuần), cập nhật import. Thêm
   `coroutineContext.ensureActive()` trong vòng lặp ghi.
3. Tạo `WearTransferService`:
   - `ACTION_SEND` (extras: uri, fileName) / `ACTION_CANCEL`
   - `startForeground()` ngay đầu `onStartCommand` với notification progress
   - giữ `job`; `ACTION_SEND` khi `job?.isActive == true` → bỏ qua, không tạo transfer thứ hai
   - kết thúc (mọi nhánh) → `WearTransferState.update(...)` + `stopSelf()`
4. Manifest `:app`: khai service + `foregroundServiceType="dataSync"`, thêm 2 permission.
5. `InstallWearDelegate`: bỏ `MutableStateFlow` nội bộ, chuyển tiếp intent; thêm `cancel()`.
6. `InstallViewModel`: `cancelWatchSend()` gọi `wearDelegate.cancel()`; `dismissWatchSend()` giữ nguyên
   nghĩa "đóng dialog" (chỉ hợp lệ ở trạng thái terminal).
7. Tách `WatchSendDialog` thành các file trạng thái + `@Preview` từng cái; thêm nút Cancel cho
   `Sending` (gọi `onCancelWatchSend`), `CheckingWatch` cũng gọi cancel thật thay vì chỉ đóng.
8. `InstallScreen`: truyền `onCancelWatchSend = viewModel::cancelWatchSend`.

## Success Criteria

- [ ] Bắt đầu transfer 30 MB → thoát app hẳn (back về launcher) → notification vẫn chạy, watch nhận đủ file
- [ ] Bấm Cancel giữa lúc `Sending` → trong ≤ 1s: notification biến mất, `adb logcat` cho thấy channel đóng
- [ ] Sau khi cancel, `adb shell ls` trên watch: không còn temp file
- [ ] Quay lại màn Install giữa lúc truyền → dialog hiện đúng progress đang chạy (không reset về 0)
- [ ] Bấm nút Watch hai lần liên tiếp → chỉ một transfer, log không có hai lần `openChannel`
- [ ] `WatchSendDialog.kt` < 60 dòng; mỗi file trạng thái có `@Preview` bọc theme
- [ ] `./gradlew :app:assembleOpensourceDebug :app:lintOpensourceDebug` pass, không lint warning mới về foreground service

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| targetSdk 36: `startForegroundService` không gọi `startForeground()` trong 5s → ANR/crash | `startForeground()` là dòng đầu tiên của `onStartCommand`, trước mọi việc I/O |
| Android 14+ hạn chế start foreground service từ background | Transfer luôn khởi phát từ tương tác UI (foreground) → hợp lệ; không thêm đường start nào khác |
| Đổi tên `WearSenderService` → `WearApkSender` chạm nhiều import | Đổi trong một commit riêng, chạy build ngay sau |
| Singleton `WearTransferState` giữ state cũ sau khi process bị kill và dựng lại | `reset()` trong `Application.onCreate` của `:app`, giống cách `TvReceiverState` được dùng |

## Kết quả (2026-09-03)

Làm full theo plan, **chưa có số đo throughput** — user quyết định làm trước. Nếu đo ra transfer nhanh
thì `WearTransferService` + `WearTransferState` gỡ được, phần cancel và dialog vẫn giữ.

| Thay đổi | Chi tiết |
|---|---|
| `WearTransferState` | Object process-wide giữ `StateFlow<WatchSendState>`, mirror `SyncManager` |
| `WearTransferService` | Foreground service `dataSync`, `ACTION_SEND` / `ACTION_CANCEL`, notification progress + nút Cancel; `startForeground()` là việc đầu tiên trong `onStartCommand` |
| Cancel thật | `WearApkSender.copyWithProgress` gọi `ensureActive()` mỗi chunk → cancel ăn trong ≤ 8 KB; `finally` của `send()` đóng channel |
| `InstallWearDelegate` | Thành lớp mỏng: đọc từ `WearTransferState`, gửi intent tới service. Không còn `MutableStateFlow` riêng |
| Dialog | Nút Cancel cho **mọi** trạng thái chưa terminal (trước đó `Sending` không có đường thoát) |
| Manifest | Khai service; `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` đã có sẵn từ trước |

### Lệch so với plan

- **Không tách `WatchSendDialog` thành nhiều file** — đã gộp data-driven ở Phase 2, không còn gì để tách.
- **Không đổi tên file `WearSenderService` ở phase này** — đã làm ở Phase 2.
- **Không gọi `WearTransferState.reset()` trong `Application.onCreate`.** Plan lo state cũ sót lại sau
  khi process bị kill. Nhưng `object` của Kotlin khởi tạo lại từ đầu mỗi lần process start, nên
  `state` đã là `Idle` sẵn — thêm `reset()` là code thừa.

### Nợ lại

- `watch_send_channel_name` chưa dịch → tổng `:app` lint **39 (baseline) → 42**, cả 3 lỗi thêm đều là
  `MissingTranslation` của string mới. Không có lỗi lint loại khác.
- Toàn bộ tiêu chí của phase này cần thiết bị, chưa tick được cái nào.
