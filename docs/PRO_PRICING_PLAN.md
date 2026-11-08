# Kế hoạch Định giá & Mô hình Gói Pro (Universal Installer Pro)

Tài liệu này tổng hợp chiến lược định giá, bài toán quota VirusTotal (Unit Economics), cấu trúc gói bán hàng trên Google Play Billing, và các lưu ý tuân thủ chính sách khi triển khai **Universal Installer Pro**.

---

## 1. Hai phương thức mở khóa Unlimited VirusTotal

Để vừa thân thiện với cộng đồng mã nguồn mở (FOSS / Power Users), vừa tạo nguồn thu bền vững từ người dùng phổ thông trên Google Play:

| Tiêu chí | Bản Miễn phí (Mặc định) | Mở khóa Cách 1: Custom Key (BYOK) | Mở khóa Cách 2: Gói Pro (Zero-Config) |
| :--- | :--- | :--- | :--- |
| **Chi phí** | **Miễn phí** | **Miễn phí 100%** | **Trả phí (Mua 1 lần hoặc Thuê bao)** |
| **Cấu hình** | Không cần cấu hình | Tự đăng ký VirusTotal & dán API key 64 ký tự | **Tự động 100%**, không cần tạo tài khoản VT |
| **Quota mỗi ngày** | **3 APK / ngày** (điều chỉnh qua Remote Config `virustotal_trial_daily_limit`) | **Unlimited** (500 req/ngày theo quota key cá nhân của user) | **Unlimited** (dùng pool 10–20 key tốc độ cao của hệ thống, tự động xoay vòng né lỗi 429) |
| **Đối tượng phù hợp** | Người dùng cài 1–2 APK/ngày | Dân công nghệ, vọc sĩ sẵn sàng tạo key | Người dùng muốn sự tiện lợi tức thì + ủng hộ nhà phát triển |

---

## 2. Bài toán Quota & Công suất chịu tải (10–20 Keys trên Remote Config)

- **Giới hạn mỗi Free API Key của VirusTotal**:
  - `4 requests / phút`
  - `500 requests / ngày`
- **Tổng công suất khi cấu hình 15–20 key trên Firebase Remote Config (`virustotal_fallback_keys`)**:
  - **7.500 – 10.000 requests / ngày** (~`60 – 80 requests / phút`).
- **Mức tiêu hao thực tế trên mỗi lần kiểm tra APK**:
  1. **Tra cứu mã băm SHA-256 (`checkFile`)**: Chỉ tốn **1 request**. Khoảng **80–90% các APK phổ biến** đã có sẵn kết quả trên VirusTotal nên trả về ngay sau 1 request.
  2. **Tải lên file mới & chờ phân tích (`uploadFile` + `pollAnalysis`)**: Tốn trung bình **4 – 7 requests** (1 request lấy upload URL/upload + 3–6 lần poll mỗi 15 giây).
- **Khả năng phục vụ**:
  - Với cơ chế đã cài đặt trong `VirusTotalKeyManager.kt`:
    - Cùng 1 mã SHA-256 trong ngày chỉ tính 1 lượt.
    - Tự động trộn ngẫu nhiên (`shuffled()`) và đưa key dính HTTP `429 RATE_LIMITED` vào trạng thái nghỉ 65 giây rồi chuyển sang key khác ngay lập tức.
  - Pool **15–20 key** có thể phục vụ ổn định **2.000 – 4.000 lượt quét APK mỗi ngày** trên toàn hệ thống.
- **Bảo vệ Pool khỏi lạm dụng (Fair-Use Guardrail cho Pro)**:
  - Tra cứu mã băm (`checkFile`): **Không giới hạn (Unlimited)** cho user Pro.
  - Upload file APK mới hoàn toàn (`uploadFile`): Có thể đặt ngưỡng Fair-Use ẩn (ví dụ `20–30 file upload mới/ngày/thiết bị`) để tránh 1 thiết bị chạy bot đốt sạch quota của cả pool.

---

## 3. Đề xuất Bảng giá (Google Play In-App Purchase / Billing)

Đối với ứng dụng tiện ích công cụ (Utility / Installer) trên Android, người dùng có xu hướng **ưu tiên gói Mua đứt (One-time Purchase / Lifetime)** hơn gói đăng ký định kỳ, nhưng việc đặt cả 2 lựa chọn sẽ tạo hiệu ứng **Neo giá (Price Anchoring)** giúp tăng tỷ lệ mua gói Lifetime.

### Phương án A: Gói Mua đứt Trọn đời (Lifetime Pro) — **Khuyên dùng làm gói chủ lực**
- **Product Type trên Play Console**: `In-app product` (Non-consumable, mua 1 lần gắn liền với tài khoản Google).
- **ID gợi ý**: `universal_installer_pro_lifetime`
- **Mức giá đề xuất theo khu vực**:
  - **Quốc tế (Tier 1 - Mỹ, Châu Âu, Nhật, Hàn, Úc)**: **`$3.99` – `$4.99`** (Giá khuyến mãi ra mắt: **`$2.99`**).
  - **Việt Nam & Đông Nam Á / Ấn Độ (Purchasing Power Parity)**: **`49.000đ` – `69.000đ`** (~`$1.99`).

### Phương án B: Kết hợp Thuê bao Năm + Mua đứt Trọn đời (Tối ưu doanh thu)
Nếu muốn có thêm dòng tiền định kỳ từ nhóm người dùng thử ngắn hạn:
1. **Gói Năm (`universal_installer_pro_yearly`)**:
   - Quốc tế: **`$1.99 / năm`** | Việt Nam: **`25.000đ / năm`**
2. **Gói Trọn đời (`universal_installer_pro_lifetime` - Gắn nhãn "Best Value / Phổ biến nhất")**:
   - Quốc tế: **`$4.99` (Mua 1 lần)** | Việt Nam: **`59.000đ` (Mua 1 lần)**

---

## 4. Lưu ý Pháp lý & Định vị tính năng để tuân thủ VirusTotal ToS & Google Play

1. **Không bán trực tiếp "Lượt gọi API VirusTotal"**:
   - Điều khoản của VirusTotal Public API không cho phép bán lại (resell) quyền truy cập API miễn phí như một dịch vụ thương mại độc lập.
   - **Cách truyền thông chuẩn trên UI & Play Store**:
     - Gói **Universal Installer Pro** là gói **Ủng hộ nhà phát triển & Mở khóa tiện ích cao cấp (Pro Supporter Pack)**, bao gồm:
       - Tích hợp quét bảo mật đám mây tự động không cần cấu hình (Zero-config Cloud Security Check).
       - Các quyền lợi Pro của ứng dụng (huy hiệu Pro, giao diện/chủ đề mở rộng, ưu tiên tính năng mới).
     - Luôn hiển thị công khai lựa chọn **1. Nhập API Key cá nhân (Miễn phí 100%)** ngay phía trên để người dùng thấy rõ tính năng VirusTotal hoàn toàn miễn phí nếu họ tự dùng key riêng.
2. **Chỉ gắn Google Play Billing vào flavor `play`**:
   - Tương tự Firebase (`docs/FIREBASE.md`), thư viện `com.android.billingclient:billing-ktx` khi tích hợp sẽ chỉ khai báo trong `"playImplementation"` để bản `opensource` (GitHub / F-Droid) giữ nguyên tính chất 100% FOSS.
