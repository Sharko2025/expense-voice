# Pending decisions

Định dạng: ngày · vấn đề · đề xuất (phương án an toàn đang dùng)

- 2026-10-10 · `POST /voice` gửi lại `entry_id` đã `failed`: SPEC vừa nói "trả kết quả cũ" vừa nói "502 để app thử lại" · Xử lý lại từ đầu khi `status=failed`; chỉ trả kết quả cũ khi `status=parsed`.
- 2026-10-10 · Gửi lại `entry_id` từng ra 0 giao dịch · Trả lại 422 kèm `raw_text` cũ, không gọi OpenAI lần nữa.
- 2026-10-10 · Request trùng `entry_id` tới khi bản ghi còn `received`/`transcribed` (đang xử lý) · Trả 409, app retry sau.
- 2026-10-10 · Câu test "nước cam 15 ca, gửi xe 2 lần 5k ở Vincom": merchant Vincom gán cho giao dịch nào? · Test chỉ bắt buộc giao dịch "gửi xe" có merchant Vincom; "nước cam" chấp nhận Vincom hoặc null.
- 2026-10-10 · `PATCH /transactions` không có `note` dù bảng có cột `note` · Giữ đúng SPEC (không sửa được `note`); app MVP không có ô ghi chú.
- 2026-10-10 · `OPENAI_PARSE_MODEL` chưa chọn · Kiểm tra danh sách model mini hiện có trên tài khoản khi làm Task 6; nếu là reasoning model không hỗ trợ `temperature=0` thì bỏ tham số đó và ghi lại. Loi chọn model cuối cùng.
- 2026-10-10 · Format response `GET /summary` chưa có trong SPEC · Dùng format trong `docs/plans/phase-1.md` Task 9.
- 2026-10-10 · `GET /transactions` thiếu `from`/`to` · Mặc định hôm nay (giờ VN); `from`/`to` bao gồm cả 2 đầu.
- 2026-10-10 · `recorded_at` không có múi giờ · Trả 422 thay vì tự đoán múi giờ.
