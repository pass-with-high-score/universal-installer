---
phase: 3
title: "Watch receive & persistence"
status: in-progress
priority: P1
effort: "5h"
dependencies: [1]
---

# Phase 3: Watch receive & persistence

## Overview

APK nhận được phải sống sót qua việc process watch bị kill, phải có id ổn định, phải được từ chối
sớm khi watch hết dung lượng, và user phải biết là nó đã tới — kể cả khi chưa grant notification.

## Requirements

**Functional**
- Kill process watch sau khi nhận → mở lại app vẫn thấy APK trong list.
- File hỏng/truncated không bao giờ xuất hiện trong list và bị xoá.
- Watch không đủ chỗ → từ chối trước khi ghi, không ghi nửa file rồi mới phát hiện.
- Notification mở thẳng vào `detail/{apkId}` của đúng APK vừa nhận.
- `POST_NOTIFICATIONS` được xin runtime; chưa grant vẫn dùng được app bình thường.

**Non-functional**
- Không có bản parse APK thứ hai: dùng `ApkMetadataReader` của `:core`.
- Repository là nguồn sự thật duy nhất cho cả `WearReceiverService` và UI.

## Architecture

**Persistence.** `WearApkRepository` hiện chỉ giữ `MutableStateFlow<List<WearApkInfo>>`
(`WearApkRepository.kt:33`). Service nhận file → add vào list → post notification → process bị kill
(rất hay trên đồng hồ) → list rỗng nhưng file vẫn nằm trong `filesDir/wear_apk_cache` vĩnh viễn.

Cách rẻ nhất, không cần DataStore/Room: **thư mục cache là nguồn sự thật**, list chỉ là view.
- `init` của repository: scan `cacheDir`, parse từng file, dựng lại list.
- `WearApkInfo.id` = tên file trên đĩa (đã unique nhờ prefix UUID lúc tạo temp) thay cho
  `UUID.randomUUID()` sinh mới mỗi lần parse (`WearApkRepository.kt:63`) → id ổn định qua các
  lần khởi động, deep-link notification dùng được.
- Scan chạy lazy trong coroutine, expose `isLoading` để `HomeScreen` không nháy "empty" lúc khởi động.

**Parse.** Bỏ `extractApkInfo()` tự viết, dùng `ApkMetadataReader(context).readMetadata(uri, isBundle)`
của `:core` — đã xử lý cả bundle (`.xapk/.apks`) mà bản tự viết trả `null` rồi xoá im lặng.
`isBundle` suy ra từ extension file (Phase 2 đã đảm bảo extension được giữ nguyên khi truyền).
`WearApkInfo` map từ `PackageMetadata`; giữ `WearApkInfo` làm model của tầng wear, không để
`PackageMetadata` rò lên UI.

**Dung lượng.** Trước khi ghi, `StorageUtil.hasSufficientStorage(expectedBytes)` của `:core`.
ChannelClient không mang theo size, nên size đi kèm trong channel path — **hợp đồng đã chốt ở Phase 2**:

```
/apk-transfer/<expectedBytes>/<safeFileName>
```

Watch parse `expectedBytes` **trước khi mở stream**. Không dùng thêm `MessageClient` gửi metadata
riêng: thêm một đường đồng bộ nữa chỉ tạo thêm chỗ để lệch. Đổi format ở một bên là đứt flow → mỗi
bên một dòng comment đánh dấu invariant.

**Wake lock.** `WearReceiverService` hiện **không acquire wake lock nào**, dù manifest đã khai
`WAKE_LOCK` (`AndroidManifest.xml:7`). Đồng hồ doze giữa lúc nhận là đứt transfer. WearLoad giữ
`PARTIAL_WAKE_LOCK` với timeout 10 phút quanh toàn bộ đoạn nhận rồi release ở cuối — làm y vậy:
acquire khi `onChannelOpened`, release trong `finally` (kể cả nhánh lỗi), luôn có timeout để không
kẹt wake lock nếu service chết bất thường.

**Ghi file an toàn.** `WearReceiverService.kt:62` `input.copyTo(output)` không phân biệt "phone đóng
channel vì xong" với "channel đứt". Sau khi copy xong, so `tempFile.length()` với size khai báo
trong path; lệch → coi là hỏng, xoá, không add vào list, log rõ.

**Notification.** `WearReceiverService.kt:102` dùng `nm.notify(apkInfo.id.hashCode(), ...)` nhưng
PendingIntent lại luôn `requestCode = 0` với `FLAG_UPDATE_CURRENT` (`:87`) → hai notification khác
nhau chia chung một PendingIntent, extras đè nhau. Sửa: requestCode = cùng giá trị với notification
id, intent mang `EXTRA_APK_ID`, `MainActivity` đọc và điều hướng thẳng tới `detail/{apkId}`.

**Runtime permission.** minSdk 30 / targetSdk 36 → trên Wear OS 4+ (API 33+) `POST_NOTIFICATIONS` là
runtime permission và chưa chỗ nào xin. Xin trong `MainActivity` bằng
`rememberLauncherForActivityResult(RequestPermission())` khi mở app lần đầu. Không chặn UI nếu từ
chối — list vẫn là đường vào chính.

## Related Code Files

- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/data/WearApkRepository.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/data/WearReceiverService.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/data/WearApkInfo.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/MainActivity.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/home/HomeViewModel.kt`
- Modify: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/home/HomeScreen.kt` (state loading/empty)
- Create: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/data/WearApkInfoMapper.kt` (`PackageMetadata` → `WearApkInfo`)
- Create: `wearos/src/main/java/app/pwhs/universalinstaller/wearos/presentation/NotificationPermissionRequest.kt`
- Modify: `wearos/src/main/res/values/strings.xml`
- Reference: `core/data/ApkMetadataReader.kt`, `core/util/StorageUtil.kt`

## Implementation Steps

1. `WearApkInfo`: `id` đổi ý nghĩa thành tên file trên đĩa (thêm comment 1 dòng nói rõ invariant này).
2. Tạo `WearApkInfoMapper.kt`: `PackageMetadata.toWearApkInfo(file: File): WearApkInfo`.
   `PackageMetadata` (`core/domain/PackageMetadata.kt`) đã có `packageName`, `appName`, `versionName`,
   `versionCode`, `minSdk`, `targetSdk`, `isBundle`, `icon: Bitmap?` → map thẳng, `WearApkInfo` thêm
   `minSdk` ngay ở phase này (Phase 4 chỉ thêm `declaresWatchFeature`).
   `icon` là bonus: Coil đã có trong deps wearos, `HomeScreen`/`ApkDetailScreen` hiện được icon thật.
3. `WearApkRepository`:
   - nhận thêm `ApkMetadataReader` qua constructor (Koin `singleOf` tự resolve nếu khai trong module)
   - `suspend fun refresh()` — scan `cacheDir`, parse song song, sort theo `receivedAt` giảm dần
   - gọi `refresh()` một lần khi khởi tạo (trong `WearApp.onCreate` hoặc lazily từ `HomeViewModel.init`)
   - `_isLoading: StateFlow<Boolean>`
   - bỏ `extractApkInfo()`
4. `WearReceiverService`:
   - acquire `PARTIAL_WAKE_LOCK` (timeout 10 phút) đầu `onChannelOpened`, release trong `finally`
   - parse `expectedBytes` + `fileName` từ channel path mới
   - `StorageUtil.hasSufficientStorage(expectedBytes)` → không đủ thì đóng channel ngay, log, không ghi
   - sau khi copy: verify `tempFile.length() == expectedBytes`, lệch → `delete()` + log + return
   - notification: requestCode riêng, intent mang `EXTRA_APK_ID`
5. `MainActivity`:
   - đọc `EXTRA_APK_ID` từ intent (cả `onCreate` lẫn `onNewIntent`) → `navController.navigate(detail(id))`
   - `LaunchedEffect` xin `POST_NOTIFICATIONS` khi `SDK_INT >= 33`, tách thành composable riêng
     `NotificationPermissionRequest` (một file một thứ)
6. `HomeViewModel` expose `isLoading`; `HomeScreen` render loading ≠ empty, kèm `@Preview` cho từng
   trạng thái (loading / empty / có data) đúng rule composable.
7. `WearModule`: khai `singleOf(::ApkMetadataReader)` hoặc `single { ApkMetadataReader(get()) }`.

## Success Criteria

- [ ] Nhận 1 APK → `adb shell am force-stop app.pwhs.universalinstaller` trên watch → mở lại app: APK vẫn trong list
- [ ] `adb shell ls filesDir/wear_apk_cache` và list trên UI khớp nhau 1-1 sau mọi thao tác
- [ ] Ngắt Bluetooth giữa lúc truyền → file truncated bị xoá, list không có mục rác
- [ ] Để màn hình watch tắt suốt lúc truyền file lớn → vẫn nhận đủ (wake lock giữ được)
- [ ] `adb shell dumpsys power | grep -A5 "Wake Locks"` sau khi transfer xong: không còn wake lock treo
- [ ] Watch còn < size APK → phone nhận `Error`/`NoSpace`, watch không ghi file nào
- [ ] Gửi `.xapk` → parse ra đúng appName/packageName (bản cũ trả null)
- [ ] Bấm notification → vào thẳng detail đúng APK đó (thử 2 APK khác nhau liên tiếp)
- [ ] Từ chối `POST_NOTIFICATIONS` → app vẫn mở được, APK vẫn vào list
- [ ] `./gradlew :wearos:assembleDebug :wearos:lintDebug` pass
- [ ] Mọi composable public sửa/thêm trong phase này đều có `@Preview` cùng file

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Đổi format `CHANNEL_PATH_PREFIX` là hợp đồng chung 2 module, lệch version phone/watch là đứt flow | Ghi hằng ở một chỗ + comment 1 dòng đánh dấu invariant; verify bằng test case gửi thật ở Phase 2 |
| `ApkMetadataReader` giải nén bundle vào `context.cacheDir` — watch chật | Xoá temp ngay sau parse; giới hạn size bundle nhận ở bước check dung lượng |
| Scan cacheDir lúc khởi động làm app watch mở chậm | Parse trong `Dispatchers.IO`, UI hiện loading; nếu > 300 ms với 10 file thì cache metadata ra file JSON kèm bên cạnh APK |
| `filesDir` không dọn tự động → rác tích tụ nếu user không xoá | Ngoài scope; ghi vào backlog `docs/ISSUE_BACKLOG.md` |

## Kết quả (2026-09-03)

Code xong, `:wearos:assembleDebug/Release` + `lintDebug` pass. Chưa verify trên thiết bị.

| Thay đổi | Chi tiết |
|---|---|
| Persistence | `WearApkRepository.refresh()` scan `cacheDir` khi `HomeViewModel` khởi tạo; thư mục cache là nguồn sự thật, list chỉ là view |
| `id` ổn định | `WearApkInfo.id` = tên file trên đĩa, bỏ `UUID.randomUUID()` sinh mới mỗi lần parse |
| Parse | Bỏ `extractApkInfo()` tự viết, dùng `ApkMetadataReader` của `:core` → `.xapk`/`.apks` giờ parse được (bản cũ trả null rồi xoá im lặng) |
| Wake lock | `PARTIAL_WAKE_LOCK` timeout 10 phút, acquire đầu `receive()`, release trong `finally` |
| Dung lượng | `StorageUtil.hasSufficientStorage(expectedBytes)` trước khi ghi byte nào |
| Chống truncated | So `written` với `expectedBytes`, lệch thì xoá file, không vào list |
| Notification | `requestCode` riêng theo `id.hashCode()` (trước đó mọi PendingIntent dùng requestCode 0 nên extras đè nhau), intent mang `EXTRA_APK_ID`, `MainActivity` điều hướng thẳng vào detail |
| `POST_NOTIFICATIONS` | `NotificationPermissionRequest.kt` — xin một lần, từ chối vẫn dùng app bình thường |
| Loading state | `HomeScreen` phân biệt loading và empty; thêm 3 `@Preview` (data / loading / empty) |

### Lệch so với plan

- **Không đưa `icon: Bitmap?` vào `WearApkInfo`.** Plan gọi đây là bonus. Nhưng scan cacheDir sẽ decode
  bitmap cho mọi APK và giữ trong `StateFlow` — trên đồng hồ đó là chi phí bộ nhớ thật để đổi lấy
  thẩm mỹ. Bỏ. Nếu muốn thì load lazy theo từng item sau.
- Thêm `isBundle` vào `WearApkInfo` (plan không nêu) — Phase 4 cần nó để `ApkInstaller` biết giải nén split.
