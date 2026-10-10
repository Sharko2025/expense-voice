# CLAUDE.md — expense-voice

App ghi chi tiêu cá nhân bằng giọng nói: nhấn → nói → xong. Một người dùng (Loi), một thiết bị (Samsung Galaxy Z Fold 8). Không phát hành Play Store.

**Nguồn sự thật duy nhất: `SPEC.md`.** Đọc SPEC.md trước mọi việc. Code mâu thuẫn với SPEC → dừng lại và hỏi, không tự sửa SPEC.

## Quy trình làm việc

1. Thiết kế đã chốt trong SPEC.md (đã qua brainstorming) — không mở lại các quyết định trong mục "Quyết định đã chốt" trừ khi Loi yêu cầu.
2. Mỗi phase bắt đầu bằng **writing-plans**: viết kế hoạch vào `docs/plans/phase-N.md`, chờ Loi duyệt rồi mới code.
3. Code theo TDD: test trước, code sau.
4. Kiểm chứng trên dữ liệu thật (audio thật ghi từ điện thoại), không chỉ fixture.
5. Làm **đúng từng phase** theo mục "Thứ tự triển khai". Phase hiện tại xong hết checklist mới sang phase sau.
6. Gặp quyết định chưa có trong SPEC → ghi 1 dòng vào `docs/pending-decisions.md` (ngày · vấn đề · đề xuất), tiếp tục với phương án an toàn nhất, báo Loi cuối phiên.

**Ngoài phạm vi MVP — không làm dù thấy hữu ích:** đối soát bank/Google Wallet, đọc notification ngân hàng, GPS, nhắc cuối ngày, báo cáo tuần, Metabase, đa người dùng, thu nhập/ngân sách.

## Cấu trúc repo

```
backend/    FastAPI, SQLAlchemy, Alembic, tests/
android/    Kotlin + Jetpack Compose (mở bằng Android Studio trên Windows)
deploy/     docker-compose.yml, nginx/expense-voice.conf, backup.sh
docs/       plans/, pending-decisions.md
SPEC.md     spec MVP
```

## Lệnh thường dùng

```bash
# Backend (local)
cd deploy && docker compose up -d --build
docker compose exec api alembic upgrade head
docker compose exec api pytest -m "not openai"        # test nhanh, không gọi OpenAI
docker compose exec api pytest -m openai              # test parse thật, tốn tiền — chỉ chạy khi đổi prompt

# Thử endpoint
curl -H "Authorization: Bearer $DEVICE_TOKEN" \
  -F file=@samples/banh-mi.m4a -F entry_id=$(uuidgen) -F recorded_at=$(date -Iseconds) \
  https://expense-voice.s-itpro.com/api/v1/voice

# Android
./gradlew installDebug        # cài qua wireless debugging
./gradlew assembleRelease     # APK đã ký
```

## Quy ước bắt buộc

- **Tiền:** số nguyên VND (`BIGINT`), không bao giờ dùng float.
- **Thời gian:** lưu `TIMESTAMPTZ` (UTC); tính ngày/tháng theo `Asia/Ho_Chi_Minh`.
- **Phương thức thanh toán:** chỉ `cash` | `card` | `transfer` | `NULL`. Người dùng không nói → `NULL`. Không bao giờ để AI hay code đoán.
- **Danh mục:** LLM chỉ được chọn trong bảng `categories`; không chắc → `Khác`.
- **Idempotency:** `POST /voice` theo `entry_id` — gửi lại không tạo bản ghi trùng.
- **Xóa:** luôn xóa mềm (`deleted_at`) để hỗ trợ Hoàn tác.
- **Đo thời gian:** mỗi `voice_entry` ghi `stt_ms`, `parse_ms`, `total_ms`.
- **Model OpenAI:** đọc từ biến môi trường (`OPENAI_STT_MODEL`, `OPENAI_PARSE_MODEL`), không hardcode.
- Thông báo lỗi hiển thị cho người dùng bằng tiếng Việt; code, tên biến, commit message bằng tiếng Anh.

## Bảo mật

- Không commit `.env`, keystore (`*.jks`), `keystore.properties`. Kiểm tra `.gitignore` trước commit đầu tiên.
- OpenAI key chỉ nằm trên server, không bao giờ xuất hiện trong app Android.
- Không log nội dung audio, token hay raw request body. Log chỉ gồm `entry_id`, status, thời gian xử lý.
- So sánh token bằng `hmac.compare_digest`.
- File audio xóa ngay sau khi transcribe xong.

## Triển khai

- Server: EC2 Singapore `54.251.52.235`, domain `expense-voice.s-itpro.com`.
- Hạ tầng tạm dùng của công ty → app phải tự chứa trong Docker Compose; không phụ thuộc dịch vụ nào khác trên máy đó. Postgres riêng, không publish port 5432; API chỉ bind `127.0.0.1:8000`.
- Thao tác chạy lâu qua SSH (build image, restore DB, copy dữ liệu) → chạy trong `tmux`.
- Không chạy lệnh thay đổi nginx/certbot/cron trên server khi chưa được Loi xác nhận.

## Định nghĩa "xong" cho một task

- Test liên quan đều qua.
- Đã chạy thử với dữ liệu thật (curl với audio thật, hoặc trên Fold 8).
- Checklist tương ứng trong SPEC.md được đánh dấu.
- Không có TODO bỏ lửng; việc chưa xong ghi vào `docs/pending-decisions.md`.