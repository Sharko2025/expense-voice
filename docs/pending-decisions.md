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
- 2026-10-11 · Nhấn đúp nút nguồn trên Samsung chỉ mở activity launcher của app · App có 2 icon: "Chi tiêu" (xem) và "Ghi chi tiêu" (ghi âm ngay); chọn "Ghi chi tiêu" cho phím bên. Thử trên Fold 8.
- 2026-10-11 · Mở ghi âm nhưng không nói gì · 8 s đầu không có tiếng nói → hủy, không upload, báo "Không nghe thấy gì".
- 2026-10-11 · Phản hồi khi chưa có kết quả (đang gửi / mất mạng) · Notification im lặng "⏳ Đang gửi…" / "Đã lưu, sẽ gửi khi có mạng", được thay bằng kết quả.
- 2026-10-11 · Widget màn hình ngoài Fold 8 có thể cần meta-data riêng của Samsung · Thử trên máy, thêm nếu cần.
- 2026-10-11 · `EncryptedSharedPreferences` đã bị Google đánh dấu deprecated · Vẫn dùng theo SPEC (chạy tốt, chỉ lưu 1 token); đổi sau nếu bị gỡ.
