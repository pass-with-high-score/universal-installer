---
title: "Bộ ảnh screenshot Wear OS cho Play listing"
status: pending
created: 2026-09-03
scope: project
mode: normal
blockedBy: []
blocks: []
---

# Bộ ảnh screenshot Wear OS cho Play listing

## Vấn đề

Commit `092dc65` gộp `:tv` và `:wearos` vào chung một Play listing với `:app`. Listing giờ có ba
form factor, nhưng **chỉ hai form factor có ảnh**:

| Form factor | Thư mục | Tình trạng |
|---|---|---|
| Phone | `images/phoneScreenshots/` | 6 ảnh, 576×1280 JPG |
| Android TV | `images/tvScreenshots/` | 5 ảnh, 1920×1080 PNG |
| **Wear OS** | `images/wearScreenshots/` | **chưa có** |

Không có ảnh thì form factor Wear OS không qua được review, và bản watch trong release sẽ không
tới được người dùng dù AAB đã upload đúng.

## Ràng buộc đã biết

**fastlane.** `supply` đọc key `wearScreenshots` từ
`fastlane/metadata/android/en-US/images/wearScreenshots/`. Locale duy nhất hiện có là `en-US`.
Lane `production` đang để `skip_upload_screenshots: false` nên ảnh sẽ được đẩy lên cùng release.

**House style.** Ảnh phone và TV đều là **capture thô từ thiết bị** — không khung máy, không
caption, không mockup. Đặt tên bằng số (`1.jpg`…`6.jpg`). Bộ Wear phải theo cùng quy ước, nếu
không listing sẽ trông chắp vá.

**Thiết bị.** AVD `sdk_gwear_arm64` đang dùng để test cho ra ảnh **384×384** — đúng bằng cạnh nhỏ
nhất Play chấp nhận. Một AVD tròn độ phân giải cao hơn (Pixel Watch, 450×450) cho ảnh đẹp hơn hẳn
trong Play Store.

**Trạng thái UI có sẵn.** Mọi màn hình watch đã có `@WearPreviewDevices` sau `ea85486`, nên không
có trạng thái nào là không dựng được. Nhưng dựng trên **thiết bị thật** thì bốn trạng thái cần
dàn dựng: `Receiving` (phải có transfer đang chạy), `Installing` (phải có install đang chạy),
`Incompatible` (phải có APK điện thoại trong hàng đợi), `Empty` (phải xoá sạch hàng đợi).

## Quyết định cần chốt trước khi làm

**1. Capture thật hay render từ `@Preview`?**

| | Capture từ emulator | Render từ `@Preview` |
|---|---|---|
| Khớp house style | Có | Không (thiếu chrome hệ thống) |
| Dàn dựng trạng thái | Phải thao tác tay, canh giờ | Không cần, deterministic |
| Hạ tầng mới | Không | `enableScreenshotTest` hoặc Roborazzi |
| Lợi ích kèm theo | Không | Thành regression test cho UI |

→ **Khuyến nghị: capture từ emulator** cho bộ ảnh store, để đồng bộ với phone và TV. Hạ tầng
screenshot test là việc đáng làm nhưng là task riêng, không nên gộp vào đây.

**2. Độ phân giải.** 384×384 (AVD hiện có) hay tạo AVD tròn 450×450+?
→ **Khuyến nghị 450×450+**. Play scale ảnh xuống chứ không lên; 384 là sàn.

## Bộ ảnh đề xuất

Thứ tự quan trọng — Play hiển thị theo thứ tự file, hai ảnh đầu là thứ người dùng thực sự nhìn.

| # | Màn hình | Nội dung phải thấy | Cách dàn dựng |
|---|---|---|---|
| 1 | Home có dữ liệu | 2–3 gói, icon thật, `v… · … MB`, badge | Gửi sẵn 3 APK khác nhau từ phone |
| 2 | Detail — Idle | Icon 40dp, tên, version·size, package, nút Install cam | Mở một gói tương thích |
| 3 | Home — đang nhận | `ReceivingCard` có tên file + thanh progress | Gửi file lớn từ phone, chụp giữa chừng |
| 4 | Detail — Incompatible | "This is a phone app, not a Wear OS app." + Install anyway | Gửi `magisk-test.apk`, mở nó |
| 5 | Detail — Installing | `CircularProgressIndicator` + "Installing…" | Bấm Install, chụp ngay |
| 6 | Home — Empty | Icon + "Nothing here yet" + câu hướng dẫn | Xoá sạch `wear_apk_cache` |

Ảnh 6 để cuối vì màn hình trống bán hàng kém; giữ lại vì nó nói rõ app hoạt động cùng điện thoại.

## Các bước

1. **Xác minh spec trong Play Console** — số ảnh tối thiểu cho form factor Wear OS và dải kích
   thước được chấp nhận. Đừng tin trí nhớ, mở Console đọc. Nếu tối thiểu > 6 thì bổ sung thêm
   ảnh swipe-to-delete và notification "APK received".
2. **Dựng AVD** Wear OS tròn 450×450 (Pixel Watch), pair với AVD phone, xác nhận
   `pm query-services -a com.google.android.gms.wearable.CHANNEL_EVENT -d "wear://x/apk-transfer/1/a.apk"`
   trả về `WearReceiverService`.
3. **Chuẩn bị 3 APK Wear OS** có icon và tên khác nhau rõ rệt, cộng 1 APK điện thoại cho ảnh 4.
   APK của chính dự án không dùng được cho ảnh 1 vì tên trùng nhau.
4. **Cài bản `:wearos` mới nhất** lên watch, `:app` lên phone.
5. **Chụp** theo bảng trên bằng `adb exec-out screencap -p`. Ảnh 3 và 5 cần canh thời điểm —
   dùng vòng lặp chụp liên tục rồi chọn khung đẹp nhất, đừng cố chụp một phát.
6. **Hậu kỳ**: xác nhận đúng 1:1, không viền đen thừa, đặt tên `1.png`…`6.png`, đặt vào
   `fastlane/metadata/android/en-US/images/wearScreenshots/`.
7. **Kiểm tra** `bundle exec fastlane supply --skip_upload_apk --skip_upload_aab --validate_only`
   (hoặc tương đương) thấy `wearScreenshots` được nhận.

## Bẫy đã gặp, đừng dẫm lại

**Đừng bấm Install trên `wearos-debug.apk` trong hàng đợi.** `:wearos` dùng chung
`applicationId` và chung chữ ký debug với `:app`, nên cài file đó từ trong app **đè luôn app watch
bằng chính bản trong hàng đợi**. Nếu bản đó cũ hơn bản đang chạy thì mọi thứ hỏng ngược. Chuyện này
đã xảy ra một lần trong session 2026-09-03 và mất khá lâu mới truy ra. Ảnh 5 (Installing) nên
chụp trên một APK Wear khác, không phải APK của chính dự án.

## Success Criteria

- [ ] `fastlane/metadata/android/en-US/images/wearScreenshots/` có ≥ 6 ảnh, tỉ lệ 1:1, cạnh ≥ 450px
- [ ] Cả 6 trạng thái trong bảng đều xuất hiện, không ảnh nào là màn hình loading hay lỗi ngoài ý muốn
- [ ] Không ảnh nào có khung máy, caption, hay watermark — khớp house style của phone/TV
- [ ] Ảnh 1 hiện đủ icon thật của gói (không rơi về icon fallback)
- [ ] `supply` nhận `wearScreenshots` khi chạy validate
- [ ] Play Console chấp nhận bộ ảnh cho form factor Wear OS

## Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Ảnh 3 và 5 khó canh đúng khoảnh khắc | Chụp liên tục theo vòng lặp rồi chọn, không chụp một phát |
| AVD 384×384 cho ảnh mờ trong Play | Dựng AVD 450×450 ở bước 2 |
| Icon rơi về fallback nếu Coil chưa kịp load | Mở app, đợi list ổn định rồi mới chụp |
| Cài nhầm APK trong hàng đợi làm hỏng app watch | Xem mục "Bẫy đã gặp" |
| Play đổi yêu cầu số lượng/kích thước | Bước 1 xác minh trước khi chụp |

## Ngoài phạm vi

- Hạ tầng screenshot test (`enableScreenshotTest` / Roborazzi) — task riêng, có giá trị nhưng
  không cần cho việc lên store
- Ảnh cho locale khác `en-US` — listing hiện chỉ có một locale
- Store listing text riêng cho form factor Wear OS — Play giữ trong Console, `supply` không ghi được
