---
title: "Fix flow truyền APK phone → Wear OS"
status: pending
created: 2026-09-03
scope: project
mode: hard
blockedBy: []
blocks: []
---

# Fix flow truyền APK phone → Wear OS

## Vấn đề

Flow gửi APK từ phone sang watch (commit `8e94d64` + `2474022`) **chưa chạy được end-to-end**, và
khi thất bại nó vẫn báo `Success` cho user. Bốn nguyên nhân chặn:

1. `applicationId` hai bên khác nhau → Wearable Data Layer không route channel tới watch app.
2. `session.commit()` là async nhưng watch coi commit-return là cài xong; `STATUS_PENDING_USER_ACTION`
   không ai xử lý nên dialog xác nhận không bao giờ hiện.
3. Danh sách APK nhận được chỉ nằm trong RAM → process watch bị kill là mất hết, file rác nằm lại.
4. `POST_NOTIFICATIONS` khai báo nhưng không xin runtime → notification bị nuốt im lặng.

Cộng thêm 7 lỗi đúng đắn/UX: `connectedNodes` không phản ánh "watch có cài app", rò channel + fd
trong sender, split/XAPK bị gửi sai, Cancel không cancel, transfer chạy trong `viewModelScope`,
progress emit mỗi 8 KB, không check dung lượng watch / `canRequestPackageInstalls` / tương thích APK.

## Scope đã chốt

- **Full 11 items** (user chọn, 2026-09-03). Không giảm scope ở các phase sau.
- **wearos depend `:core`**, bật `isMinifyEnabled` + `isShrinkResources` cho wearos release.

## Transport: giữ nguyên `ChannelClient`

Không đổi transport. `ChannelClient` là client duy nhất của Wearable Data Layer thiết kế cho file lớn:

| | `MessageClient` | `DataClient` + `Asset` | `ChannelClient` |
|---|---|---|---|
| Size | tối đa 100 KB | > 100 KB cần `Asset` | file lớn |
| Cần kết nối trực tiếp | Có | Không (store-and-forward) | Có |
| Bền qua mất kết nối | Không | Có | **Không** |
| Disk | — | copy asset ra local trước khi sync | không copy |

Tài liệu Google: ChannelClient để *"reliably send a file that's too large to send using a
MessageClient"* và *"saves disk space over DataClient, which creates a copy of the assets on the local
device"*. Với APK vài chục MB, việc `DataClient` nhân đôi file trên cả hai máy là không chấp nhận được.

Đường truyền vật lý: *"Bluetooth preferred, but can use Wi-Fi if it's the only type of connection
available"* — Play services tự chọn, app không điều khiển.

**Đánh đổi phải chấp nhận:** không bền qua mất kết nối, không resume. Ra khỏi tầm BT giữa lúc truyền
là mất, gửi lại từ đầu. Phase 3 verify size để không nhận file dở; Phase 5 giữ transfer sống khi user
rời màn hình.

**`DataClient` + `Asset` là đường lùi đã được kiểm chứng.** Đọc source WearLoad (`mobile/WearableConnector.kt`)
xác nhận họ **không dùng `ChannelClient`**, mà dùng `DataClient` + `Asset`:
`Asset.createFromFd(ParcelFileDescriptor.open(file, MODE_READ_ONLY))` →
`dataMap.putAsset("apk_$index", asset)` trên path `/apk/<timestamp>`, kèm `MessageClient` cho tín hiệu
điều khiển. Split APK = **nhiều asset trong cùng một DataItem**.

Hệ quả nếu đổi sang `DataClient`: **Phase 5 gần như thừa toàn bộ.** Store-and-forward do Play services
lo → app phone chết giữa chừng vẫn gửi tiếp, ra khỏi tầm BT rồi quay lại thì tiếp tục. Đúng ba thứ
Phase 5 sinh ra để giải quyết (foreground service, cancel, mất kết nối). Phía mobile của WearLoad
không có foreground service nào cho việc truyền.

Giá phải trả: Play services copy file ra chỗ của nó (tốn disk tạm), và DataItem là *state đồng bộ*
chứ không phải hàng đợi — phải chủ động xoá sau khi tiêu thụ, không thì nó tồn tại và giao lại.

**Quyết định (2026-09-03):** giữ `ChannelClient`, làm Phase 1 trước rồi **đo throughput + tỉ lệ đứt
giữa chừng** (Phase 1 bước 8). Phase 1 không phụ thuộc transport nên không phí công. Có số thật rồi
mới quyết Phase 2/5 — không viết lại plan dựa trên suy đoán.

## Các app cùng loại làm thế nào

| App | Truyền | Cài | Cần gì trên watch |
|---|---|---|---|
| **Wear Installer 2** (freepoc) | ADB qua Wi-Fi, phone nhúng sẵn ADB client nối tới wireless debugging của watch | `adb install` | Developer options + Wireless debugging. **Không cần app trên watch** |
| **WearLoad** | 2 đường: Data Layer qua BT (mặc định) **+ Wi-Fi/HTTP server mode** cho "maximum transfer speed" | native installer | "Install unknown apps" — app tự cảnh báo là **"often hidden"** |
| **Wear APK Install** | HTTP server chạy **trên watch**, phone upload qua browser cùng Wi-Fi | native Package Manager | — |
| **AnExplorer** | Wireless ADB remote install từ phone | cả hai đường | tùy đường |

**Cảnh báo license — đọc được, copy thì không.** Source WearLoad ở `github.com/wearload/WearLoad`
là **All Rights Reserved**, cấm dùng thương mại và **cấm phân phối lại**. Repo này là **GPL-3.0** →
không tương thích theo cả hai chiều. Được đọc để hiểu kiến trúc (repo họ tự nói publish cho mục đích
học tập), **không được copy bất kỳ dòng code nào vào đây**. Mọi thứ trong plan phải viết lại từ đầu
hoặc reuse từ `:core`.

Ba điều rút ra:

1. Kiến trúc của plan này (Data Layer + native installer trên watch) **trùng đường mặc định của
   WearLoad** → không sai hướng.
2. **Không app nghiêm túc nào chỉ làm một đường truyền.** Đường Wi-Fi/HTTP là chuẩn ngành cho tốc độ,
   không phải phương án dự phòng. Repo đã có sẵn `core/receiver/ApkReceiverServer.kt` (NanoHTTPD, đang
   phục vụ flow TV) → chi phí thêm đường này thấp hơn tưởng. Xét sau khi có số đo ở Phase 1 bước 8.
3. **ADB trong các app này là công cụ *cấp quyền một lần*, không phải transport** — dùng để grant
   `REQUEST_INSTALL_PACKAGES` khi Settings của watch ẩn toggle. Xem rủi ro ở Phase 4.

## Nguyên tắc: reuse trước khi viết mới

Repo đã có sẵn đúng những thứ đang bị viết lại sai. Không viết bản thứ hai:

| Đã có | Ở đâu | Thay cho |
|---|---|---|
| `ApkInstaller` — PackageInstaller + broadcast receiver + `STATUS_PENDING_USER_ACTION` + launch confirm intent (đã remediate intent-redirection) + split bundle + progress + suspend tới terminal | `core/install/ApkInstaller.kt` | `DetailViewModel.commitInstallSession()` |
| `ApkMetadataReader` — parse APK/bundle → `PackageMetadata` | `core/data/ApkMetadataReader.kt` | `WearApkRepository.extractApkInfo()` |
| `StorageUtil.hasSufficientStorage()` | `core/util/StorageUtil.kt` | check dung lượng watch |
| `TvReceiverState` — singleton bridge process-wide giữa foreground service và Compose UI | `core/receiver/TvReceiverState.kt` | pattern cho `WearTransferState` (Phase 5) |

`core/src/main/AndroidManifest.xml` rỗng → depend `:core` không merge thêm permission nào.

## Phases

| # | Phase | Priority | Chặn bởi | File |
|---|---|---|---|---|
| 1 | Pairing & build config | P1 | — | [phase-01-pairing-build-config.md](phase-01-pairing-build-config.md) |
| 2 | Sender: capability discovery & correctness | P1 | 1 | [phase-02-sender-correctness.md](phase-02-sender-correctness.md) |
| 3 | Watch receive & persistence | P1 | 1 | [phase-03-receive-persistence.md](phase-03-receive-persistence.md) |
| 4 | Watch install path | P1 | 1, 3 | [phase-04-watch-install.md](phase-04-watch-install.md) |
| 5 | Transfer lifecycle trên phone | P2 | 2 | [phase-05-transfer-lifecycle.md](phase-05-transfer-lifecycle.md) |

Phase 2, 3 độc lập file với nhau (phone vs wearos) → chạy song song được sau Phase 1.
Phase 4 sửa `DetailViewModel` + dùng repository của Phase 3 → tuần tự sau 3.

### Mapping 11 items → phase

| Item | Phase |
|---|---|
| 1. applicationId + signing config | 1 |
| 2. PackageInstaller status callback thật | 4 |
| 3. Persist/scan lại cacheDir, id ổn định | 3 |
| 4. POST_NOTIFICATIONS runtime | 3 |
| 5. CapabilityClient thay `connectedNodes` | 2 |
| 6. Rò channel/PFD trong sender | 2 |
| 7. Split APK / XAPK | 2 (gửi) + 4 (cài, do `ApkInstaller` lo) |
| 8. Cancel transfer thật | 5 |
| 9. Foreground service thay `viewModelScope` | 5 |
| 10. Throttle progress | 2 |
| 11. Dung lượng watch + `canRequestPackageInstalls` + tương thích APK | 3 (dung lượng) + 4 (còn lại) |

## Kiến trúc sau khi fix

```
PHONE                                          WATCH
─────                                          ─────
InstallScreen (nút Watch)
  → InstallViewModel.sendToWatch()
  → InstallWearDelegate
      • đọc state từ WearTransferState         [Phase 5]
      • startForegroundService()
  → WearTransferService (foreground, dataSync) [Phase 5]
      • CapabilityClient.getCapability(         [Phase 2]
          "apk_receiver", FILTER_REACHABLE)
      • openChannel("/apk-transfer/<name>")
      • stream, throttle progress 1%            [Phase 2]
      • cancel = close channel + stop service   [Phase 5]
                          │
              Wearable Data Layer
        (cùng applicationId + cùng cert)        [Phase 1]
                          │
                          ▼
                                     WearReceiverService.onChannelOpened
                                       • StorageUtil check trước khi ghi  [Phase 3]
                                       • ghi vào filesDir/wear_apk_cache
                                       • WearApkRepository.addApk()
                                           → ApkMetadataReader (:core)    [Phase 3]
                                           → id ổn định theo tên file
                                       • notification deep-link detail/{id}
                                                    │
                                     HomeScreen (list rebuild từ cacheDir khi init)
                                       → ApkDetailScreen
                                       → DetailViewModel.install()
                                           → ApkInstaller (:core)         [Phase 4]
                                               → STATUS_PENDING_USER_ACTION
                                                 → startActivity(confirm)
                                               → STATUS_SUCCESS/FAILURE
                                           → xoá cache CHỈ khi Success
```

## KHÔNG nằm trong scope

- Gửi ngược watch → phone.
- Gửi nhiều APK cùng lúc / hàng đợi transfer.
- Tile + complication hiển thị số APK đang chờ (đã có class, không sửa trong plan này).
- Đổi transport sang Wi-Fi Direct / `WearableListenerService` DataItem.
- Publish wear APK lên Play (chỉ ghi chú versionCode band ở Phase 1, không làm release).

## Rủi ro toàn cục

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Đổi `applicationId` wearos làm hỏng install hiện có trên máy test | Thấp | Package cũ `...wearos` phải uninstall tay trước khi cài bản mới; ghi vào success criteria Phase 1 |
| Play multi-APK từ chối vì `versionCode` trùng phone | Trung bình | Phase 1 chốt band riêng cho wear, verify bằng `bundletool`/Play internal trước khi publish |
| Bật R8 cho wearos lần đầu → crash Koin/Wear Compose ở release | Trung bình | Phase 1 có keep rules + bắt buộc smoke test bản release, không chỉ debug |
| `:core` kéo compose material3 mobile + nanohttpd vào wear APK | Thấp | R8 + `shrinkResources` cắt; đo size trước/sau, ghi vào Phase 1 success criteria |
| Không có thiết bị Wear thật → chỉ test emulator | Trung bình | Emulator Wear + phone emulator pair được qua Wear OS companion; `canRequestPackageInstalls` phải test trên watch thật vì retail watch có thể ẩn toggle |

## Verification tổng

Chạy sau khi cả 5 phase xong:

```bash
./gradlew :app:assembleOpensourceDebug :wearos:assembleDebug
./gradlew :wearos:assembleRelease   # R8 phải pass
./gradlew :app:lintOpensourceDebug :wearos:lintDebug
```

Test matrix end-to-end (cần user cho phép cài lên thiết bị):

| Case | Kỳ vọng |
|---|---|
| APK đơn, watch có cài app | Transfer → notification → cài thành công, cache bị xoá |
| Watch không cài app | Phone hiện `NoWatch`, **không** báo Success |
| Không có watch nào kết nối | Phone hiện `NoWatch` |
| Bundle `.xapk` / `.apks` | Transfer nguyên bundle, watch cài split trong 1 session |
| Danh sách split rời (nhiều Uri) | Phone chặn với thông báo rõ, không gửi 1 split lẻ |
| Cancel giữa lúc transfer | Channel đóng, service stop, watch xoá temp file |
| Rời màn Install giữa lúc transfer | Transfer tiếp tục, notification progress còn |
| Watch hết dung lượng | Từ chối nhận trước khi ghi, báo lại phone |
| Chưa bật "install unknown apps" trên watch | Detail screen hướng dẫn, không hiện Installing vô hạn |
| User bấm Cancel ở dialog xác nhận cài | `Failed` với message thật, cache **không** bị xoá |
| Kill process watch sau khi nhận, mở lại app | APK vẫn trong list |
| Chưa grant POST_NOTIFICATIONS | Vẫn thấy APK trong list khi mở app tay |
| APK phone (không phải wear app) | Cảnh báo tương thích trước khi cài |

## Nguồn tham chiếu

- [Choose a client type — Wear OS](https://developer.android.com/training/wearables/data/client-types)
- [Handle Data Layer events on Wear](https://developer.android.com/training/wearables/data/events)
- [ChannelClient — Play services reference](https://developers.google.com/android/reference/com/google/android/gms/wearable/ChannelClient)
- Yêu cầu cùng package name + cùng signature để Data Layer route được: xác nhận qua tài liệu
  Data Layer và các báo cáo triển khai thực tế ([Using Android ChannelClient](https://medium.com/@shoaibsaikat/using-android-channelclient-f5b4fd346374)).
