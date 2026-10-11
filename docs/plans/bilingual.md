# Song ngữ Việt / English cho MVP

Trạng thái: **chờ Loi duyệt** · 2026-10-11 · ưu tiên làm 2026-10-12 (Loi cần demo với người nước ngoài)

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
- Tên app: "Expense Voice" / "Chi tiêu"; icon ghi âm: "Record expense" / "Ghi chi tiêu".
- Gồm: màn Hôm nay / Tháng này / Cài đặt, màn ghi âm, notification (✓, Hoàn tác, Đang gửi…, Không hiểu…), Snackbar, thông báo lỗi, tile, widget.

### B2. Dữ liệu hiển thị

- **Danh mục:** bảng dịch theo tên tiếng Việt → chuỗi resource (`Ăn sáng` → Breakfast, `Cafe/Đồ uống` → Coffee/Drinks, `Gửi xe` → Parking, `Đi chợ/Siêu thị` → Groceries…). Tên lạ → giữ nguyên.
- **Phương thức:** Cash / Card / Transfer / `?`.
- **Ngày:** "Today" / "Yesterday" / "Thu, 08 Oct" theo ngôn ngữ máy. Giờ vẫn theo giờ Việt Nam.
- **Tiền:** giữ một kiểu `20.000đ` cho cả 2 ngôn ngữ (tiền là VND, người nước ngoài ở VN quen cách viết này). *Đề xuất, Loi có thể đổi thành `20,000₫` khi English.*
- **Lỗi từ server:** app không hiện nguyên câu tiếng Việt của server. App tự hiện câu theo mã lỗi (401, 422, 5xx…), riêng `raw_text` (câu đã nghe) vẫn hiện nguyên văn.

### B3. Test (TDD)

- Unit test format/logic chạy với cả 2 ngôn ngữ (Robolectric `@Config(qualifiers = "en")` và `"vi"`).
- Kiểm tra mọi chuỗi trong `values/` đều có bản trong `values-vi/` (test tự động).

## C. Kiểm chứng trên Fold 8

1. Máy để English: toàn bộ app, notification, tile, widget là English.
2. Đổi riêng app sang Tiếng Việt: toàn bộ chuyển sang tiếng Việt.
3. Nói tiếng Anh: "coffee 27k by card" → ✓ notification đúng.
4. Nói tiếng Việt: vẫn đúng như trước.

---

## Cần Loi

1. **Duyệt plan** (đặc biệt: định dạng tiền giữ `20.000đ`; tên app English "Expense Voice").
2. **Ghi 5 câu tiếng Anh** bằng Fold 8 (app Voice Recorder, xuất m4a) vào `samples/`:
   - `en-coffee.m4a`: "coffee twenty seven thousand at FamilyMart by card"
   - `en-breakfast.m4a`: "breakfast banh mi twenty thousand cash"
   - `en-parking.m4a`: "parking five thousand"
   - `en-groceries.m4a`: "groceries one point two million bank transfer"
   - `en-weather.m4a`: "the weather is nice today"
   (nếu có thể, nhờ thêm 1–2 câu bằng giọng người nước ngoài sẽ demo)
