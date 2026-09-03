---
phase: 1
title: "Pairing & build config"
status: in-progress
priority: P1
effort: "2h"
dependencies: []
---

# Phase 1: Pairing & build config

## Overview

Làm cho Wearable Data Layer route được channel giữa hai app: đồng nhất `applicationId` và signing
certificate, và cho wearos depend `:core` để các phase sau reuse `ApkInstaller` / `ApkMetadataReader`.
Không phase nào chạy được nếu phase này chưa xong.

## Requirements

**Functional**
- `openChannel()` từ phone làm `WearReceiverService.onChannelOpened` fire trên watch (debug và release).
- wearos release ký bằng đúng keystore trong `key.properties` như `:app`.
- `:core` có mặt trên classpath của wearos.

**Non-functional**
- wearos release qua R8 không crash (Koin reflection + Wear Compose + PackageInstaller).
- Kích thước wearos release APK đo được trước/sau khi thêm `:core`.

## Architecture

Data Layer resolve target trên node kia **theo package name**, và Google Play services verify
**signing certificate**. Package khác nhau → `openChannel` vẫn trả về Channel (kênh tới node) nên
phone ghi hết bytes và báo Success, nhưng watch không nhận event nào. Đây là nguyên nhân gốc.

- `applicationId` = `app.pwhs.universalinstaller` cho cả hai module.
- `namespace` giữ nguyên `app.pwhs.universalinstaller.wearos` → R class và package Kotlin không đổi,
  không phải sửa import nào.
- `:app` không có `applicationIdSuffix` ở flavor hay buildType nào (`opensource`/`play` cùng
  applicationId, debug không suffix) → wear app duy nhất pair được với cả 4 variant của phone.
- Debug: cả hai dùng `~/.android/debug.keystore` → pair được ngay khi dev.
- Release: wearos phải đọc `key.properties` giống `:app` (`app/build.gradle.kts:63-79`).

**Bằng chứng ngoài:** WearLoad (app cùng loại) để `applicationId = "com.serhio.wearload"` **giống hệt
nhau** ở cả `mobile/build.gradle.kts` lẫn `wear/build.gradle.kts`, cùng `namespace`. Yêu cầu này là
thật, không phải suy đoán.

`versionCode` — **chưa chốt, phụ thuộc mô hình phân phối:**
- Publish **multi-APK rời** trên Play: mỗi APK phải có `versionCode` **duy nhất** → cần band riêng
  (vd `phoneVersionCode + 1000`). Wear APK phân biệt bằng
  `<uses-feature android:name="android.hardware.type.watch" />` (đã có ở `AndroidManifest.xml:4`).
- Một **AAB** chứa cả hai, hoặc phone app tự cài wear app: dùng **cùng** `versionCode` được.
  WearLoad để cả hai bên `versionCode = 16` → họ theo mô hình thứ hai (repo họ có `mobile/WearUpdate.kt`).

Repo này hiện có `:app` và `:wearos` là **hai application module rời** → mặc định rơi vào trường hợp
thứ nhất. Chốt mô hình phân phối trước rồi mới đặt số, đừng sửa `versionCode` một cách máy móc.

## Related Code Files

- Modify: `wearos/build.gradle.kts`
- Modify: `wearos/proguard-rules.pro`
- Reference (không sửa): `app/build.gradle.kts:63-92` — logic đọc `key.properties`
- Reference: `core/src/main/AndroidManifest.xml` — rỗng, không merge permission

## Implementation Steps

1. `wearos/build.gradle.kts` — `defaultConfig`:
   - `applicationId = "app.pwhs.universalinstaller"`
   - `versionName = "1.12.0"` cho khớp phone. `versionCode`: chốt mô hình phân phối trước (xem
     Architecture) — multi-APK rời thì `1035`, một AAB / phone tự cài wear thì `35`
2. `wearos/build.gradle.kts` — thêm signing config, copy đúng pattern của `:app` (đọc
   `rootProject.file("key.properties")`, `useReleaseKeystore` guard để build được khi không có file):
   ```kotlin
   val keyPropertiesFile = rootProject.file("key.properties")
   val useReleaseKeystore = keyPropertiesFile.exists()
   if (useReleaseKeystore) {
       val keyProperties = Properties().apply { load(keyPropertiesFile.inputStream()) }
       signingConfigs {
           create("release") { /* storeFile / storePassword / keyAlias / keyPassword */ }
       }
   }
   ```
   Cần `import java.util.Properties` ở đầu file.
3. `buildTypes.release`: `isMinifyEnabled = true`, `isShrinkResources = true`,
   `signingConfig = signingConfigs.getByName("release")` trong guard `useReleaseKeystore`.
4. `dependencies`: thêm `implementation(project(":core"))`.
5. `wearos/proguard-rules.pro` — keep rules tối thiểu:
   - Koin: giữ constructor của `WearApkRepository`, `HomeViewModel`, `DetailViewModel`
     (`-keep class app.pwhs.universalinstaller.wearos.** { <init>(...); }` hoặc keep hẹp hơn từng class)
   - Ackpine (`:core` dùng cho split parsing) — kiểm tra `core/consumer-rules.pro` đã cover chưa,
     nếu có rồi thì không lặp lại
   - `WearableListenerService` là entry point từ manifest nên AGP tự keep, không cần rule
6. Build kiểm chứng:
   ```bash
   ./gradlew :wearos:assembleDebug :wearos:assembleRelease
   ```
7. Đo size: `ls -la wearos/build/outputs/apk/release/*.apk`, ghi lại vào phase này.
8. **Đo throughput + độ bền** ngay khi channel đã tới được watch. Gửi 1 APK ~30 MB, log timestamp lúc
   `openChannel` và lúc `close`, tính KB/s. Lặp 5 lần, ghi cả số lần đứt giữa chừng. Ghi kết quả vào
   đây. Hai quyết định treo vào số này:
   - **transfer < ~20s** → Phase 5 giảm còn "cancel thật + tách `WatchSendDialog`", bỏ foreground service.
   - **tỉ lệ đứt cao** (đi lại bình thường trong nhà cũng đứt) → cân nhắc đổi sang `DataClient` + `Asset`
     (store-and-forward, xem mục Transport trong `plan.md`); lúc đó Phase 2 viết lại và Phase 5 bỏ gần hết.

   Chưa đo thì không được kết luận cả hai.

## Success Criteria

- [x] `./gradlew :wearos:assembleDebug :wearos:assembleRelease` pass
- [x] `aapt2 dump badging` (hoặc `./gradlew :wearos:assembleDebug` + `apkanalyzer manifest print`)
      cho thấy `package: name='app.pwhs.universalinstaller'` và `versionCode` đúng theo mô hình đã chốt — ✅ `1035`
- [ ] (chờ máy có keystore) `apksigner verify --print-certs` trên wearos release cho cùng SHA-256 với `:app` release
- [ ] (chờ thiết bị) Sau khi **uninstall package cũ `app.pwhs.universalinstaller.wearos`** trên watch và cài bản mới:
      gửi 1 APK từ phone → `adb logcat -s WearReceiverService` trên watch in
      `Receiving APK channel: <file>`
- [ ] (chờ thiết bị) Bản release wearos mở được app, list render, không crash (R8 smoke test)
- [x] Size APK release ghi lại trước/sau khi thêm `:core`; nếu tăng > 3 MB thì báo lại trước khi qua phase sau

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Package cũ còn trên watch → hai app trùng tên gây lẫn khi test | Bước verify bắt buộc uninstall `...wearos` trước |
| R8 strip Koin constructor → crash release | Keep rules ở bước 5 + smoke test release là success criteria |
| `key.properties` không có trên máy dev → build release fail | Guard `useReleaseKeystore` giống `:app`, release không ký vẫn build được |
| Đổi `versionCode` ảnh hưởng CI/Jenkins đang parse version | Grep `Jenkinsfile` + `scripts/` xem có hard-code version wearos trước khi đổi |

## Kết quả đo (2026-09-03)

| Hạng mục | Kết quả |
|---|---|
| `:wearos:assembleDebug` | ✅ pass |
| `:wearos:assembleRelease` (R8 + shrinkResources, lần đầu bật) | ✅ pass |
| `:wearos:lintDebug` | ✅ pass, không lỗi mới |
| `aapt2 dump badging` release APK | `package: name='app.pwhs.universalinstaller' versionCode='1035' versionName='1.12.0'`, `minSdkVersion:'30'` |
| Size release **không** có `:core` | 2 967 370 B (2.83 MB) |
| Size release **có** `:core` | 3 556 746 B (3.39 MB) |
| **Delta do `:core`** | **+0.56 MB** — dưới ngưỡng 3 MB rất xa, R8 cắt sạch nanohttpd + compose mobile |

### Lệch so với plan

- **Không thêm keep rules cho Koin vào `wearos/proguard-rules.pro`.** `:app` đã minify release với
  đúng bộ Koin + ackpine đó và **không có keep rule nào** cho chúng (`app/proguard-rules.pro` chỉ giữ
  libsu, Shizuku, một `-dontwarn` của gms). Koin dùng constructor DSL (`singleOf`/`viewModelOf`) là
  compile-time, không reflection → thêm rule là cargo-cult. Giữ nguyên file, dựa vào smoke test bản
  release để bắt nếu sai.
- **Rủi ro "CI/Jenkins parse version" không tồn tại** — repo không có `Jenkinsfile`, `scripts/` chỉ có
  script Node xử lý string resources.

### Chưa verify được trên máy này

- **Chữ ký release**: `key.properties` không có trên máy này → output là
  `wearos-release-unsigned.apk`. Tiêu chí "`apksigner verify --print-certs` khớp SHA-256 với `:app`"
  **chưa chạy được**, phải verify trên máy/CI có keystore.
- **Smoke test bản release (R8)** và **verify channel tới được watch** cần cài lên thiết bị → chờ
  user đồng ý.
- **Bước 8 (đo throughput + tỉ lệ đứt)** cũng chờ thiết bị.
