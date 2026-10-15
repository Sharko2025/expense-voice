# Pending decisions

Định dạng: ngày · vấn đề · đề xuất (phương án an toàn đang dùng)

- 2026-10-10 · `POST /voice` gửi lại `entry_id` đã `failed`: SPEC vừa nói "trả kết quả cũ" vừa nói "502 để app thử lại" · Xử lý lại từ đầu khi `status=failed`; chỉ trả kết quả cũ khi `status=parsed`.
- 2026-10-10 · Gửi lại `entry_id` từng ra 0 giao dịch · Trả lại 422 kèm `raw_text` cũ, không gọi OpenAI lần nữa.
- 2026-10-10 · Request trùng `entry_id` tới khi bản ghi còn `received`/`transcribed` (đang xử lý) · Trả 409, app retry sau.
- 2026-10-10 · Câu test "nước cam 15 ca, gửi xe 2 lần 5k ở Vincom": merchant Vincom gán cho giao dịch nào? · Test chỉ bắt buộc giao dịch "gửi xe" có merchant Vincom; "nước cam" chấp nhận Vincom hoặc null.
- 2026-10-10 · `PATCH /transactions` không có `note` dù bảng có cột `note` · Giữ đúng SPEC (không sửa được `note`); app MVP không có ô ghi chú.
- 2026-10-11 · Chọn `OPENAI_PARSE_MODEL` · Đo 6 model trên 13 câu (6 câu SPEC + 7 audio thật): độ đúng tương đương (24/26), tốc độ ~0,9–1 s; chọn `gpt-5.4-mini` (mới nhất, nhận `temperature=0`, đúng 39/39 khi có thêm quy tắc đọc số bên dưới).
- 2026-10-10 · Format response `GET /summary` chưa có trong SPEC · Dùng format trong `docs/plans/phase-1.md` Task 9.
- 2026-10-10 · `GET /transactions` thiếu `from`/`to` · Mặc định hôm nay (giờ VN); `from`/`to` bao gồm cả 2 đầu.
- 2026-10-10 · `recorded_at` không có múi giờ · Trả 422 thay vì tự đoán múi giờ.
- 2026-10-11 · Entry kẹt ở `received`/`transcribed` (server restart giữa chừng) sẽ trả 409 mãi · Sau 2 phút cho phép xử lý lại; khi xử lý lại thì `received_at` được cập nhật thành thời điểm bắt đầu lần xử lý mới.
- 2026-10-11 · `backup.sh` cần S3 bucket + quyền ghi trên EC2 (SPEC chưa có tên bucket) · Thêm biến `BACKUP_S3_URI` vào `.env`; Loi cung cấp bucket ở Phase 4.
- 2026-10-11 · Thứ tự giao dịch trong 1 lần nói (không có cột thứ tự) · Ghi `created_at = clock_timestamp()` từng dòng để giữ đúng thứ tự nói khi trả lại.
- 2026-10-11 · Mọi model đọc "một triệu không trăm năm chục ngàn" thành 1150000 (đúng: 1050000), không ổn định · Đề xuất thêm vào system prompt trong SPEC, sau câu "Số trơn dưới 1000…": `Đọc số kiểu tiếng Việt: "1 triệu không trăm năm chục ngàn" = 1050000; "1 triệu 2" = "1 triệu hai trăm ngàn" = 1200000; "hai trăm rưỡi" = 250000.` Đã đo: gpt-5.4-mini 39/39. **Đã duyệt 2026-10-11**, đã cập nhật SPEC, code, bộ test.
- 2026-10-11 · Chưa có file audio thật cho câu không có số tiền (test 422 bằng audio đang skip) · Ghi thêm 1 file, ví dụ "hôm nay trời đẹp", đặt tên `troi-dep.m4a`.
- 2026-10-11 · Nhấn đúp nút nguồn: danh sách "Open app" của Samsung chỉ hiện 1 dòng mỗi app (không thấy icon "Ghi chi tiêu") · Đặt `RecordActivity` là launcher activity đầu tiên trong manifest → chọn "Chi tiêu" cho phím bên sẽ mở thẳng màn ghi âm (giả định Samsung mở launcher activity đầu tiên). **Đã thử 2026-10-15: chạy đúng** — danh sách hiện "Record expense", mở thẳng màn ghi âm trên màn hình khóa.
- 2026-10-11 · Mở ghi âm nhưng không nói gì · 8 s đầu không có tiếng nói → hủy, không upload, báo "Không nghe thấy gì".
- 2026-10-11 · Phản hồi khi chưa có kết quả (đang gửi / mất mạng) · Notification im lặng "⏳ Đang gửi…" / "Đã lưu, sẽ gửi khi có mạng", được thay bằng kết quả.
- 2026-10-11 · Widget màn hình ngoài Fold 8 có thể cần meta-data riêng của Samsung · Thử trên máy, thêm nếu cần.
- 2026-10-11 · `EncryptedSharedPreferences` đã bị Google đánh dấu deprecated · Vẫn dùng theo SPEC (chạy tốt, chỉ lưu 1 token); đổi sau nếu bị gỡ.
- 2026-10-11 · Song ngữ Việt/English (yêu cầu mới, chưa có trong SPEC) · Loi chốt: giao diện theo ngôn ngữ máy, nói được cả 2 thứ tiếng, danh mục dịch khi hiển thị. Kế hoạch `docs/plans/bilingual.md`; cập nhật SPEC sau khi xong.
- 2026-10-14 · Màn sửa giao dịch chưa có ngày/giờ (SPEC chỉ liệt kê món, tiền, danh mục, phương thức, nơi mua; API đã hỗ trợ `occurred_at`) · Thêm DatePicker + TimePicker theo giờ VN, không cho chọn tương lai. Gộp vào `docs/plans/bilingual.md` mục D.
- 2026-10-15 · STT đôi khi nghe "bốn trăm hai chục ngàn" thành "bốn trăm hai *mươi* chục ngàn" (~1/8 lần, có cả khi `language=vi`); parse câu đó ra 4200000 ~3/10 lần → sai gấp 10 · Đề xuất thêm vào prompt: `"X mươi chục" là cách nghe nhầm của "X chục" (ví dụ "hai mươi chục ngàn" = 20000, không phải 200000).` **Đã sửa 2026-10-15** (luật trong prompt + test; 26/26 OpenAI x3).
- 2026-10-15 · Đổi tên app · Loi đổi thành **Ví Cá Mập** (tiếng Việt) / **Sharko Wallet** (English), thay "Cá Mập Ví" / "Shark Wallet".
