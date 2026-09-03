---
phase: 4
title: "Watch install path"
status: in-progress
priority: P1
effort: "4h"
dependencies: [1, 3]
---

# Phase 4: Watch install path

## Overview

Thay đường cài tự viết bằng `core.install.ApkInstaller` để `STATUS_PENDING_USER_ACTION` được xử lý
thật, `Success` chỉ báo khi hệ thống thật sự cài xong, và bundle split cài trong một session.
Kèm hai cửa chặn trước khi cài: `canRequestPackageInstalls()` và tương thích APK với đồng hồ.

## Requirements

**Functional**
- Bấm Install → dialog xác nhận của hệ thống hiện lên trên watch.
- User hủy ở dialog → `Failed` với message thật, APK **không** bị xoá khỏi cache.
- Cài xong → `Success`, cache mới bị xoá.
- Chưa bật "install unknown apps" → màn hình hướng dẫn + nút mở Settings, không hiện `Installing` treo.
- APK không tương thích watch → cảnh báo trước, user vẫn được quyền cài tiếp.

**Non-functional**
- Không còn `commitInstallSession()` tự viết trong `DetailViewModel`.
- `DetailViewModel` không giữ logic PackageInstaller nào.

## Architecture

**Vấn đề hiện tại** (`DetailViewModel.kt:88-105`): `PendingIntent.getActivity` trỏ tới
`Intent(ACTION_MAIN).setPackage(...)` — không component nào đọc `PackageInstaller.EXTRA_STATUS`.
App không phải privileged installer nên status đầu tiên **luôn** là `STATUS_PENDING_USER_ACTION`
mang `EXTRA_INTENT`; không ai launch intent đó → dialog xác nhận không hiện, install đứng im.
Trong khi đó `commit()` là async nhưng code set `InstallState.Success` (`:57`) và `deleteById` (`:58`)
ngay khi commit return → UI báo cài xong trong khi chưa cài gì, và xoá luôn file.

**Thay bằng `core/install/ApkInstaller.kt`** — đã làm đúng tất cả những chỗ trên:
- `PendingIntent.getBroadcast` + `FLAG_MUTABLE`, receiver đăng ký `RECEIVER_NOT_EXPORTED`, action
  gắn `sessionId` nên nhiều session không đè nhau
- `STATUS_PENDING_USER_ACTION` → strip URI grant flags, verify target không phải component nội bộ,
  `startActivity(confirm)` — đã remediate Google Play Intent Redirection
- suspend tới trạng thái terminal, trả `Result.Success` / `Result.Failure(message)` với
  `EXTRA_STATUS_MESSAGE` thật
- `writeBundle()` cài split `.apks/.xapk/.apkm` trong **một** session (Ackpine, fallback ZIP thủ công)
- `onProgress(written, total)` → hiện được progress lúc ghi session, thay `CircularProgressIndicator`
  vô định hiện tại

API dùng: `ApkInstaller(context).install(source: File)` — overload tiện cho file đã staged, tự suy
`isBundle` từ extension. `WearApkInfo.cachedFilePath` giữ nguyên extension (Phase 2 + 3) nên overload
này chạy đúng, không cần truyền tay.

**Cửa chặn 1 — `canRequestPackageInstalls()`.** Không có quyền này thì session commit sẽ fail sau khi
đã ghi hết bytes. Check trước, hiện màn hướng dẫn với nút mở
`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` kèm `package:` uri.

Đây **không** phải rủi ro lý thuyết: WearLoad (app cùng loại, cùng kiến trúc Data Layer + native
installer) cảnh báo thẳng trong tài liệu rằng toggle này **"often hidden"** trên Wear OS, và ship hẳn
một **ADB mode chỉ để grant quyền đó từ xa** — ADB ở đó là công cụ cấp quyền một lần, không phải
đường truyền file. Đường lùi khi Settings ẩn toggle:

```
adb shell appops set app.pwhs.universalinstaller REQUEST_INSTALL_PACKAGES allow
```

user chạy một lần qua wireless debugging của watch. Màn `NeedsUnknownSourcesPermission` cần:
- nút mở Settings (đường chính)
- nếu `resolveActivity` null (toggle bị ẩn) → hiện hướng dẫn ADB thay vì thông báo cụt

Việc nhúng ADB client vào app phone (như Wear Installer 2 làm) **ngoài scope plan này**.

**Cửa chặn 2 — tương thích.** APK phone thường không cài được lên watch. Check rẻ, không cần parse thêm:
- `applicationInfo.minSdkVersion > Build.VERSION.SDK_INT` → chắc chắn fail
- APK không khai `android.hardware.type.watch` → cài được nhưng UI hỏng; cảnh báo mức "có thể không
  chạy đúng", vẫn cho cài tiếp

`minSdk` đã có sẵn từ `PackageMetadata` của `:core` (map ở Phase 3). Chỉ còn `declaresWatchFeature`
phải đọc thêm: `getPackageArchiveInfo(path, GET_CONFIGURATIONS).reqFeatures` → tìm
`android.hardware.type.watch`. Bổ sung 1 field vào `WearApkInfo` + mapper của Phase 3.

**Trạng thái.** `InstallState` thêm `NeedsUnknownSourcesPermission` và `Incompatible(reason)`.
`ApkDetailScreen` hiện đang navigate ngay khi thấy `Success` (`ApkDetailScreen.kt:49-52`) — gọi
`onInstallSuccess()` trong composition body là side-effect sai chỗ, chuyển sang `LaunchedEffect`.

## Related Code Files

- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/detail/DetailViewModel.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/detail/ApkDetailScreen.kt`
- Create: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/detail/InstallState.kt` (tách khỏi ViewModel — một file một thứ)
- Create: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/domain/ApkCompatibilityCheck.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/data/WearApkInfo.kt` (`declaresWatchFeature`)
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/di/WearModule.kt` (`ApkInstaller`)
- Modify: `wearos/src/main/res/values/strings.xml`
- Reference (không sửa): `core/install/ApkInstaller.kt`, `tv/presentation/receive/ReceiveViewModel.kt` (mẫu dùng `ApkInstaller` + progress)

## Implementation Steps

1. Tách `sealed interface InstallState` ra file riêng, thêm `NeedsUnknownSourcesPermission` và
   `Incompatible(val reason: String)`; `Installing` mang thêm `progress: Float?`.
2. `WearApkInfo` + mapper (Phase 3): thêm `declaresWatchFeature: Boolean` (`minSdk` đã có từ Phase 3).
3. Tạo `ApkCompatibilityCheck` — hàm thuần, nhận `WearApkInfo` trả `Incompatible?`. Không đụng Android
   API ngoài `Build.VERSION.SDK_INT` để test được.
4. `WearModule`: `single { ApkInstaller(get()) }`.
5. `DetailViewModel.install()`:
   - kiểm `apkFile.exists()` (giữ)
   - `context.packageManager.canRequestPackageInstalls()` false → `NeedsUnknownSourcesPermission`, dừng
   - `ApkCompatibilityCheck` trả non-null → `Incompatible`, chờ user xác nhận qua `installAnyway()`
   - `installer.install(apkFile)` với `onProgress` → cập nhật `Installing(progress)`
   - `Result.Success` → `Success` + `repository.deleteById(apkId)`
   - `Result.Failure(msg)` → `Failed(msg)`, **không** xoá cache
   - xoá hoàn toàn `commitInstallSession()`
6. `ApkDetailScreen`:
   - `LaunchedEffect(installState)` cho điều hướng khi `Success`, bỏ side-effect trong body
   - nhánh mới: `NeedsUnknownSourcesPermission` (nút mở Settings), `Incompatible` (cảnh báo + "Cài tiếp")
   - `Installing` dùng progress xác định khi có `progress`
   - thêm `@Preview` cho mỗi trạng thái đáng nhìn: Idle / Installing / Failed / NeedsUnknownSources /
     Incompatible (file sẽ vượt ~150 dòng → tách các composable trạng thái ra `detail/component/`)

## Success Criteria

- [ ] Bấm Install → dialog xác nhận hệ thống hiện lên trên watch
- [ ] Hủy ở dialog → `Failed` với message từ `EXTRA_STATUS_MESSAGE`, `adb shell ls` cho thấy file cache **còn**
- [ ] Cài xong → `Success`, file cache bị xoá, `pm list packages` có package mới
- [ ] Cài `.xapk` nhiều split → cài thành công trong một session
- [ ] Tắt "install unknown apps" → màn hướng dẫn, nút mở đúng trang Settings
- [ ] Watch **ẩn** toggle unknown apps (`resolveActivity` null) → hiện hướng dẫn `adb shell appops set ... allow`, không phải thông báo cụt
- [ ] Gửi APK phone (minSdk cao hơn / không có `type.watch`) → cảnh báo trước, vẫn cài tiếp được
- [ ] `grep -c "PackageInstaller" wearos/src` = 0
- [ ] Không file nào trong `wearos/presentation/detail/` vượt ~150 dòng
- [ ] `./gradlew :wearos:assembleDebug :wearos:assembleRelease` pass

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| `ApkInstaller` mở confirm intent bằng `FLAG_ACTIVITY_NEW_TASK` — trên Wear có thể không nổi lên trên watch face | Test trên watch thật; nếu không nổi, thêm notification full-screen intent (đã có pattern trong repo cho TV) |
| `canRequestPackageInstalls` trả true nhưng watch retail chặn ở tầng khác | `Failed` hiện message thật của hệ thống thay vì message tự chế → user còn manh mối |
| Ackpine parse bundle cần cache trống trên watch | Check dung lượng ở Phase 3 phải tính cả chỗ giải nén (~2x size bundle) |
| `ApkInstaller` viết cho TV/phone, đổi để chiều wear sẽ ảnh hưởng `:tv` và `:app` | Không sửa `ApkInstaller` trong phase này; nếu buộc phải sửa thì build + smoke test cả `:tv` và `:app` |

## Kết quả (2026-09-03)

Làm sớm hơn kế hoạch: Phase 3 đổi `WearApkInfo` và DI, để `DetailViewModel` cũ lại thì cây code không
build được. Code xong, build + lint pass. Chưa verify trên thiết bị.

| Thay đổi | Chi tiết |
|---|---|
| Bỏ `commitInstallSession()` | Thay bằng `core.install.ApkInstaller` — `getBroadcast` + `FLAG_MUTABLE`, xử lý `STATUS_PENDING_USER_ACTION`, suspend tới terminal, cài split bundle trong một session |
| `Success` thật | Chỉ set khi `ApkInstaller.Result.Success`; `Failure` giữ nguyên file cache để retry |
| Progress | `Installing(progress: Float?)` — progress xác định khi ghi session |
| `canRequestPackageInstalls()` | Check trước, ra `NeedsUnknownSources` |
| Toggle bị ẩn | `resolveActivity` null → không crash; màn hình luôn hiện kèm hướng dẫn `adb shell appops set ... allow` |
| Tương thích | `ApkCompatibilityCheck` — `minSdk` vs `Build.VERSION.SDK_INT`, và `reqFeatures` có `android.hardware.type.watch` không. Cảnh báo rồi vẫn cho "Install anyway" |
| Side-effect trong composition | `ApkDetailScreen` gọi `onInstallSuccess()` thẳng trong body → chuyển sang `LaunchedEffect` |
| `InstallState` | Tách ra file riêng, thêm `NeedsUnknownSources` + `Incompatible` |

### Lệch so với plan

- **Không thêm `declaresWatchFeature` vào `WearApkInfo`.** Đọc `reqFeatures` ngay lúc check thay vì
  lưu trong model — tránh phình model cho một field chỉ dùng đúng một lần.
- `ApkDetailScreen` viết lại data-driven (`Action` enum + hàm map state → text/nút) thay vì `when`
  lồng nhiều nhánh; 5 `@Preview` cho 5 trạng thái. Không cần tách `detail/component/` như plan dự tính.
