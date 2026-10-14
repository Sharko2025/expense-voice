# Song ngữ Việt / English cho MVP + sửa ngày giờ giao dịch

Trạng thái: **đã duyệt 2026-10-14**, đang làm · viết 2026-10-11 · ưu tiên làm 2026-10-12 (Loi cần demo với người nước ngoài)

Đây là yêu cầu mới, chưa có trong SPEC. Phạm vi Loi đã chốt (2026-10-11):

1. **Giao diện theo ngôn ngữ máy.** Máy để English → app English; máy để tiếng Việt → app tiếng Việt. Đổi riêng cho app ở **Settings → Apps → Chi tiêu → Language**.
2. **Nói được cả 2 thứ tiếng.** Server tự nhận câu tiếng Việt hay tiếng Anh.
3. **Danh mục được dịch khi hiển thị** (Breakfast, Coffee/Drinks…). DB vẫn giữ tên tiếng Việt.

Sau khi xong: cập nhật SPEC (mục Pipeline AI, Android app) và đánh dấu trong file này.

---

## A. Backend: nhận giọng nói tiếng Anh

### A1. Đo trước khi đổi (an toàn cho tiếng Việt)

Bỏ `language="vi"` có thể làm tiếng Việt kém đi. Cách làm:
- Loi ghi thêm **4–5 câu tiếng Anh** bằng Fold 8 vào `samples/` (danh sách ở mục "Cần Loi").
- Chạy so sánh 2 cách trên **7 file tiếng Việt + các file tiếng Anh**:
  1. Không truyền `language` (để model tự nhận).
  2. Không truyền `language` + `prompt` gợi ý từ vựng cả 2 thứ tiếng.
- Chọn cách cho tiếng Việt **không kém hơn** hiện tại. Nếu cả 2 cách đều làm tiếng Việt kém đi → báo Loi trước khi đổi.
- Thêm biến `OPENAI_STT_LANGUAGE` (rỗng = tự nhận; `vi` = như cũ) để quay lại cách cũ không cần sửa code.

### A2. Prompt parse hiểu tiếng Anh

Thêm vào system prompt (nguyên tắc cũ giữ nguyên):
- Số tiền tiếng Anh: `"27k"` = `"27 thousand"` = 27000; `"1.2 million"` = `"1 million 2"` = 1200000; `"20,000 dong"` = 20000.
- Phương thức: `"cash"` → cash; `"card"` / `"by card"` / `"credit card"` / `"tap"` → card; `"bank transfer"` / `"transfer"` / `"QR"` → transfer. Không nói → null, không đoán.
- `item`: giữ nguyên ngôn ngữ người nói ("coffee" vẫn là "coffee").
- `category`: vẫn chọn trong danh sách tên tiếng Việt (model tự hiểu "breakfast" → "Ăn sáng").

### A3. Test (TDD)

Thêm vào `test_parse_openai.py`:

| Câu nói | Kết quả mong đợi |
| --- | --- |
| coffee 27k at FamilyMart by card | coffee · 27000 · Cafe/Đồ uống · card · FamilyMart |
| breakfast banh mi 20 thousand cash | 20000 · Ăn sáng · cash |
| parking 5 thousand | 5000 · Gửi xe · null · null |
| groceries 1.2 million bank transfer | 1200000 · Đi chợ/Siêu thị · transfer |
| the weather is nice today | 0 giao dịch → 422 |

- 7 câu tiếng Việt + 7 audio cũ phải **vẫn qua**.
- Test audio tiếng Anh thêm vào `test_voice_openai.py`.
- Deploy lại EC2 (`git pull` + build, như Phase 1), `curl` audio tiếng Anh từ ngoài.

## B. Android: giao diện song ngữ

### B1. Chuỗi hiển thị

- Mọi chuỗi đang viết thẳng trong code chuyển vào resource:
  - `res/values/strings.xml`: **English** (mặc định cho mọi máy không phải tiếng Việt).
  - `res/values-vi/strings.xml`: tiếng Việt (giữ nguyên chữ hiện tại).
- Khai báo `locales_config.xml` → hiện mục **Language** riêng cho app trong Settings của Android.
- Tên app: **"Cá Mập Ví"** (tiếng Việt) / **"Shark Wallet"** (English) — theo biệt danh Sharko của Loi (duyệt 2026-10-14). Icon ghi âm: "Ghi chi tiêu" / "Record expense".
- Gồm: màn Hôm nay / Tháng này / Cài đặt, màn ghi âm, notification (✓, Hoàn tác, Đang gửi…, Không hiểu…), Snackbar, thông báo lỗi, tile, widget.

### B2. Dữ liệu hiển thị

- **Danh mục:** bảng dịch theo tên tiếng Việt → chuỗi resource (`Ăn sáng` → Breakfast, `Cafe/Đồ uống` → Coffee/Drinks, `Gửi xe` → Parking, `Đi chợ/Siêu thị` → Groceries…). Tên lạ → giữ nguyên.
- **Phương thức:** Cash / Card / Transfer / `?`.
- **Ngày:** "Today" / "Yesterday" / "Thu, 08 Oct" theo ngôn ngữ máy. Giờ vẫn theo giờ Việt Nam.
- **Tiền:** giữ một kiểu `20.000đ` cho cả 2 ngôn ngữ (tiền là VND, người nước ngoài ở VN quen cách viết này). **Loi duyệt 2026-10-14.**
- **Lỗi từ server:** app không hiện nguyên câu tiếng Việt của server. App tự hiện câu theo mã lỗi (401, 422, 5xx…), riêng `raw_text` (câu đã nghe) vẫn hiện nguyên văn.

### B3. Test (TDD)

- Unit test format/logic chạy với cả 2 ngôn ngữ (Robolectric `@Config(qualifiers = "en")` và `"vi"`).
- Kiểm tra mọi chuỗi trong `values/` đều có bản trong `values-vi/` (test tự động).

## D. Sửa ngày / giờ giao dịch (Loi phát hiện 2026-10-14)

Hiện màn sửa không có ô ngày giờ, nên giao dịch ghi muộn (ví dụ tối mới nhớ khoản ăn trưa) không chỉnh được thời điểm. API `PATCH /transactions/{id}` đã nhận `occurred_at`, **backend không cần sửa**.

- Màn sửa thêm 2 ô: **Ngày** (DatePicker Material 3) và **Giờ** (TimePicker, 24h). Hiển thị và chọn theo **giờ Việt Nam**.
- Gửi `occurred_at` dạng ISO có offset `+07:00`, chỉ khi ngày hoặc giờ thật sự đổi.
- Không cho chọn thời điểm **ở tương lai** (chặn trong picker và trong kiểm tra trước khi lưu). **Loi duyệt 2026-10-14.**
- Đổi sang ngày khác → giao dịch rời khỏi danh sách ngày đang xem (cache đã xử lý sẵn); Snackbar "Đã chuyển sang 12/10" để không tưởng là bị mất. **Loi duyệt 2026-10-14.**
- Song ngữ ngay từ đầu (chữ trên ô, nút OK/Hủy theo ngôn ngữ máy).
- Test (TDD): `buildPatch` có `occurred_at` khi đổi ngày/giờ, không có khi giữ nguyên; ghép ngày + giờ thành đúng mốc UTC (ví dụ 11/10 23:30 giờ VN = 16:30Z); chặn tương lai.
- Cập nhật SPEC mục "Màn hình Hôm nay" (bottom sheet sửa thêm ngày giờ).

## C. Kiểm chứng trên Fold 8

1. Máy để English: toàn bộ app, notification, tile, widget là English.
2. Đổi riêng app sang Tiếng Việt: toàn bộ chuyển sang tiếng Việt.
3. Nói tiếng Anh: "coffee 27k by card" → ✓ notification đúng.
4. Nói tiếng Việt: vẫn đúng như trước.
5. Sửa giờ một giao dịch trong cùng ngày; chuyển một giao dịch sang hôm qua → biến khỏi Hôm nay, có ở Hôm qua, tổng 2 ngày đúng; kiểm tra lại trên server.

---

## Cần Loi

1. **Duyệt plan** (đặc biệt: định dạng tiền giữ `20.000đ`; tên app English "Expense Voice"; không cho chọn giờ ở tương lai).
2. **Ghi 5 câu tiếng Anh** bằng Fold 8 (app Voice Recorder, xuất m4a) vào `samples/`:
   - `en-coffee.m4a`: "coffee twenty seven thousand at FamilyMart by card"
   - `en-breakfast.m4a`: "breakfast banh mi twenty thousand cash"
   - `en-parking.m4a`: "parking five thousand"
   - `en-groceries.m4a`: "groceries one point two million bank transfer"
   - `en-weather.m4a`: "the weather is nice today"
   (nếu có thể, nhờ thêm 1–2 câu bằng giọng người nước ngoài sẽ demo)

## Kết quả (2026-10-15)

- Backend song ngữ đã deploy lên EC2; audio tiếng Việt qua HTTPS vẫn đúng (E6: cà phê · 120.000đ · Trung Nguyên).
- Android: 70 unit test qua (gồm `TextsEnglishTest`, `TextsVietnameseTest`, `StringsCompletenessTest`).
- Trên Fold 8 (máy để en-GB):
  - English: "Shark Wallet", "Today", "Sun, 11 Oct", "October 2026", "+698.000đ vs last month", danh mục Breakfast / Fuel/Transport ✅
  - Đổi riêng app sang tiếng Việt (`cmd locale set-app-locales … vi`): "Cá Mập Ví", "Thứ Ba, 13/10", "Tháng 10/2026", "so với tháng trước" ✅ — đổi qua lại có hiệu lực ngay.
  - Màn sửa: có ô Date/Time; mở hết chiều cao (trước đó nửa chừng, phải kéo mới thấy Save) và cuộn được; DatePicker mở đúng ngày.
- Còn lại: audio tiếng Anh thật (Loi nhờ người Mỹ ghi sau); thử lưu đổi ngày/giờ trên máy (để Loi tự làm trên dữ liệu thật).
- Ghi chú: adb trong `C:\Users\loi\platform-tools` quá cũ, không nhận Fold 8 qua USB → dùng `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
