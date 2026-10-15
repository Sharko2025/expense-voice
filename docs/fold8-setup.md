# Hướng dẫn cài đặt trên Galaxy Z Fold 8

App: **Sharko Wallet** (English) / **Ví Cá Mập** (tiếng Việt).

Máy: SM-F971B · Android 17 · One UI 9 · **giao diện tiếng Anh** (tên menu bên dưới giữ nguyên tiếng Anh).

---

## 1. Bật Developer options (làm 1 lần)

**Settings → About phone → Software information** → chạm 7 lần vào **Build number** → nhập mã khóa máy.
Xuất hiện thêm mục **Settings → Developer options**.

## 2. Kết nối máy với laptop qua Wi-Fi (Wireless debugging)

Điều kiện:
- Điện thoại và laptop **cùng mạng Wi-Fi**.
- **Tắt FortiClient VPN** trên laptop (VPN chặn mạng LAN → laptop không thấy điện thoại).

### 2a. Ghép nối (chỉ làm 1 lần, hoặc khi đổi laptop)

1. **Settings → Developer options** → bật **Wireless debugging**.
2. Chạm vào chữ **Wireless debugging** (không phải công tắc) → **Pair device with pairing code**.
3. Giữ hộp thoại mở. Hộp thoại hiện `IP address & Port` (port riêng cho ghép nối) và `Wi-Fi pairing code` (6 số).
4. Trên laptop (PowerShell, hoặc gõ `!` trước lệnh trong Claude Code):
   ```
   adb pair 192.168.1.2:<port-trong-hộp-thoại> <mã-6-số>
   ```
   IP, port và mã **cách nhau bằng dấu cách**. Thấy `Successfully paired` là xong.

### 2b. Kết nối (mỗi lần dùng)

1. Bật **Wireless debugging** (tự tắt khi đổi Wi-Fi hoặc khóa máy lâu).
2. Xem `IP address & Port` ở **màn hình Wireless debugging chính** (khác port lúc ghép nối).
3. Trên laptop:
   ```
   adb connect 192.168.1.2:<port>
   adb devices          # phải thấy dòng "... device"
   ```

Mất kết nối? Kiểm tra lần lượt: VPN đã tắt chưa → Wireless debugging còn bật không → port có đổi không → `ping 192.168.1.2`.

Mẹo: khi thử app lâu, tăng **Settings → Display → Screen timeout** lên 10 phút (mặc định trên máy đang là 15 giây), thử xong trả lại.

## 3. Cài app

Bản dev (Claude Code chạy giúp):
```
cd android
./gradlew installDebug
```
Bản dùng thật (APK đã ký, Phase 4): xem SPEC mục "Build và cài lên Fold 8". Khi cài APK ngoài Store: tạm tắt **Settings → Security and privacy → Auto Blocker**, cài xong bật lại.

Sau khi cài, app có **2 icon** (tên theo ngôn ngữ của app: English / tiếng Việt):
- **Sharko Wallet** / **Ví Cá Mập**: màn hình Hôm nay / Tháng này / Cài đặt.
- **Record expense** / **Ghi chi tiêu**: mở là ghi âm ngay.

## 4. Mở app lần đầu

1. Mở **Sharko Wallet** → cho phép **Microphone** và **Notifications**.
2. Màn **Cài đặt** hiện ra: URL máy chủ đã điền sẵn `https://expense-voice.s-itpro.com`; nhập **Device token**.
   - Lấy token trên server: `grep DEVICE_TOKEN ~/expense-voice/.env` (Claude Code có thể nhập hộ qua adb).
3. Bấm **Lưu và kiểm tra** → phải thấy **Kết nối OK**.

## 5. Nhấn đúp nút nguồn để ghi âm

**Settings → Advanced features → Side button → Double press** → bật → **Open app** → chọn **Sharko Wallet**.

Danh sách của Samsung chỉ hiện 1 dòng mỗi app; app được cấu hình để lựa chọn này mở thẳng **màn ghi âm** (kể cả khi đang khóa màn hình).

## 6. Nút ghi âm trong Quick Settings

1. Vuốt từ mép trên xuống **2 lần** để mở toàn bộ Quick Settings.
2. Bấm **✏️ (Edit)**.
3. Tìm nút **Record expense** (hoặc **Ghi chi tiêu**) trong danh sách nút có sẵn → giữ và kéo lên bảng nút đang dùng.
4. Bấm **Done**. Từ giờ: vuốt xuống → chạm **Record expense** → nói.

## 7. Widget ghi âm

**Màn hình chính (máy mở):**
1. Giữ tay vào chỗ trống trên màn hình chính → **Widgets**.
2. Tìm **Sharko Wallet** → chọn widget **Record expense** (nút micro tròn) → **Add**.

**Màn hình ngoài (máy gập):** gập máy, làm lại 2 bước trên trên màn hình ngoài.

## 8. Pin: để hàng đợi offline gửi đúng lúc

**Settings → Apps → Sharko Wallet → Battery → Unrestricted**.

Không đặt Unrestricted, Samsung có thể hoãn việc gửi lại các bản ghi lúc mất mạng.

## 8b. Đổi ngôn ngữ của app

**Settings → Apps → Sharko Wallet → Language** → English hoặc Tiếng Việt (không cần đổi ngôn ngữ cả máy).

## 9. Cách dùng hằng ngày

- Ghi: nhấn đúp nút nguồn / chạm tile / chạm widget / nút micro lớn giữa màn hình trong app → nói, ví dụ *"bánh mì ăn sáng 20 nghìn tiền mặt"* → im lặng ~1,5 giây là tự dừng.
- Kết quả hiện ở notification: `✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt`. Sai thì bấm **Hoàn tác**.
- Không nói phương thức thanh toán → hiện `?`, sửa sau trên màn Hôm nay.
- Mất mạng: notification "Đã lưu, sẽ gửi khi có mạng"; có mạng lại sẽ tự gửi.
- Sửa / xóa: màn **Hôm nay** → chạm vào dòng giao dịch.
- Báo "Token không hợp lệ": vào **Cài đặt** nhập lại token → **Lưu và kiểm tra** (các bản ghi đang chờ sẽ tự gửi lại).
