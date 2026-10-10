# Phase 1 — Backend + AI

Trạng thái: **chờ Loi duyệt** · 2026-10-10

Mục tiêu: backend chạy trên `https://expense-voice.s-itpro.com`, `curl` gửi audio thật → nhận đúng danh sách giao dịch; đủ mọi endpoint trong SPEC mục "API backend".

Checklist SPEC tương ứng:

- [ ] Khởi tạo repo, Docker Compose, Alembic migration, seed danh mục → Task 1–3
- [ ] `POST /voice`: transcribe + parse + lưu, idempotent theo `entry_id` → Task 4–7
- [ ] Bộ test parse 6 câu ở mục Pipeline AI đều qua → Task 6
- [ ] Các endpoint giao dịch, summary, health → Task 8–10
- [ ] Deploy lên EC2, nginx + SSL, `curl` từ ngoài gửi audio ra kết quả đúng → Task 11–12

---

## Nguyên tắc kỹ thuật

- **Mọi thứ chạy trong Docker** (máy Windows đang có Python 3.11, SPEC yêu cầu 3.12). Image `python:3.12-slim`.
- **SQLAlchemy 2.0 sync + psycopg 3.** Endpoint dạng `def` (chạy trong threadpool) — đủ cho 1 người dùng, code đơn giản hơn async.
- **Test chạy trên Postgres thật** (database `expense_test` trong cùng container `db`), không dùng SQLite — vì cần `TIMESTAMPTZ`, `gen_random_uuid()`, partial index, `ON CONFLICT`. Mỗi test chạy trong 1 transaction rồi rollback.
- **OpenAI được tách sau 2 interface** `Transcriber` và `TransactionParser`. Test thường (`-m "not openai"`) dùng fake qua `app.dependency_overrides`; test `-m openai` gọi API thật.
- Client OpenAI: `timeout=20`, `max_retries=0` (app Android tự retry). Model đọc từ `OPENAI_STT_MODEL`, `OPENAI_PARSE_MODEL`.
- Logging: 1 middleware ghi `method path status ms` (+ `entry_id` ở `/voice`). Không log body, header, token, `raw_text`.

## Cấu trúc backend

```
backend/
  Dockerfile
  pyproject.toml            # deps + cấu hình pytest (marker "openai")
  alembic.ini
  alembic/versions/0001_initial.py
  app/
    main.py                 # create_app(), middleware log, mount router /api/v1
    config.py               # Settings (pydantic-settings) từ .env
    db.py                   # engine, SessionLocal, get_db
    models.py               # Category, VoiceEntry, Transaction
    schemas.py              # Pydantic request/response
    auth.py                 # require_token (hmac.compare_digest)
    timeutil.py             # VN_TZ, day_range(), month_range()
    services/
      stt.py                # Transcriber protocol + OpenAITranscriber
      parser.py             # TransactionParser protocol + OpenAIParser, SYSTEM_PROMPT, JSON_SCHEMA
      normalize.py          # hậu kiểm kết quả LLM → danh sách giao dịch hợp lệ
      voice.py              # process_voice(): idempotency + pipeline + đo thời gian
    routers/
      health.py  voice.py  transactions.py  summary.py  categories.py
  tests/
    conftest.py             # DB test, client, fake transcriber/parser, token
    test_auth.py  test_migration.py  test_normalize.py  test_voice.py
    test_transactions.py  test_summary.py  test_categories.py
    test_parse_openai.py    # @pytest.mark.openai — 6 câu bắt buộc
    test_voice_openai.py    # @pytest.mark.openai — audio thật end-to-end
```

---

## Task 1 — Khởi tạo repo

1. `git init`, nhánh `main`.
2. `.gitignore`: `.env`, `*.jks`, `keystore.properties`, `__pycache__/`, `.pytest_cache/`, `samples/`, Android build output (`android/.gradle/`, `android/build/`, `android/app/build/`, `local.properties`).
3. `.env.example` đúng như SPEC mục `.env` (giá trị giả) + `TEST_DATABASE_URL`.
4. `docs/pending-decisions.md`.
5. Kiểm tra `git status` không có file bí mật → commit đầu tiên.
6. **Chưa push** lên `github.com/Sharko2025/expense-voice` cho tới khi Loi xác nhận.

## Task 2 — Docker Compose + skeleton FastAPI + health

- `deploy/docker-compose.yml` theo SPEC. Thêm `deploy/docker-compose.override.yml` **chỉ dùng local**: mount `../backend` vào container + `uvicorn --reload` để chạy test không cần rebuild.
- `db`: thêm healthcheck `pg_isready`; script init `deploy/initdb/create-test-db.sql` tạo database `expense_test` (chỉ mount ở override local).
- Test trước (`test_health`): `GET /api/v1/health` không cần token → 200, body `ok`.
- Code: `create_app()`, router health.
- Xong khi: `docker compose up -d --build` lên, `curl 127.0.0.1:8000/api/v1/health` → `ok`.

## Task 3 — Model, migration, seed danh mục

- Test trước (`test_migration.py`): sau `alembic upgrade head` có đủ 3 bảng; `categories` có đúng 12 danh mục theo thứ tự SPEC (`sort_order` 1..12); `amount_vnd <= 0` bị CHECK chặn; `method='momo'` bị CHECK chặn; `alembic downgrade base` rồi `upgrade head` chạy sạch.
- Code: `models.py` khớp 100% DDL trong SPEC (kể cả partial index `WHERE deleted_at IS NULL`), migration `0001_initial` gồm DDL + seed. Bật extension không cần (`gen_random_uuid()` có sẵn từ PG13).
- `updated_at` cập nhật trong code ORM (`onupdate=func.now()`), không dùng trigger.

## Task 4 — Auth

- Test trước: thiếu header → 401; sai token → 401; token đúng → qua. Thông báo lỗi tiếng Việt (`"Token không hợp lệ"`).
- Code: dependency `require_token` dùng `hmac.compare_digest`, gắn cho mọi router trừ health.

## Task 5 — Hậu kiểm kết quả parse (`normalize.py`)

Hàm thuần, không gọi mạng — test kỹ bằng unit test:

- `category` không có trong danh sách active → đổi thành `Khác`.
- `amount_vnd <= 0` → bỏ giao dịch đó (DB có CHECK, không để vỡ cả request).
- `item` rỗng/khoảng trắng → bỏ giao dịch.
- `merchant` rỗng → `None`; `method` giữ nguyên (schema strict đã đảm bảo chỉ `cash|card|transfer|null`). **Không bao giờ điền method khi LLM trả null.**
- Trim khoảng trắng ở `item`, `merchant`.

## Task 6 — Pipeline AI (STT + parse)

- `stt.py`: `OpenAITranscriber.transcribe(path) -> str`, `language="vi"`, `prompt` gợi ý từ vựng đúng như SPEC.
- `parser.py`: `SYSTEM_PROMPT` và `JSON_SCHEMA` **chép nguyên văn từ SPEC**, `{categories}` thay bằng danh sách tên từ DB; `temperature=0`; `response_format={"type":"json_schema","json_schema":{"name":"transactions","strict":true,"schema":...}}`.
- Test `-m openai` (`test_parse_openai.py`): 6 câu bắt buộc, kiểm `item` (chứa từ khóa), `amount_vnd`, `category`, `method`, `merchant` đúng bảng SPEC. Câu "hôm nay trời đẹp" → 0 giao dịch.
- Test `-m openai` (`test_voice_openai.py`): gửi file audio thật trong `samples/` qua `POST /voice`, kiểm số tiền.
- **Cần Loi:** ghi âm 6 câu bằng chính Fold 8 (app Ghi âm, xuất m4a), đặt vào `samples/` với tên `banh-mi.m4a`, `cafe-familymart.m4a`, `gui-xe.m4a`, `nuoc-cam-vincom.m4a`, `sieu-thi.m4a`, `troi-dep.m4a`. Thư mục `samples/` không commit (giọng nói cá nhân).

## Task 7 — `POST /voice` + idempotency

Request multipart: `file`, `entry_id` (UUID), `recorded_at` (ISO 8601 **bắt buộc có offset**, thiếu → 422), `lat?`, `lng?`.

Luồng `process_voice()`:

1. `INSERT voice_entries ... ON CONFLICT (id) DO NOTHING` (status `received`) — an toàn khi 2 request trùng tới cùng lúc.
2. Nếu bản ghi đã tồn tại:
   - `parsed` có giao dịch → 200, trả kết quả cũ (không gọi OpenAI).
   - `parsed` 0 giao dịch → 422 kèm `raw_text` cũ (không gọi OpenAI).
   - `failed` → xử lý lại từ đầu (đề xuất, xem pending-decisions).
   - `received`/`transcribed` (request khác đang xử lý) → 409 `"Đang xử lý, thử lại sau"`; app retry sau.
3. Lưu file vào thư mục tạm, transcribe, **xóa file trong `finally`** (cả khi lỗi). Đo `stt_ms`. Cập nhật `raw_text`, status `transcribed`.
4. Parse + normalize, đo `parse_ms`. Lưu giao dịch với `occurred_at = recorded_at`. Status `parsed`, ghi `total_ms`.
5. 0 giao dịch → status `parsed`, trả 422 `{"detail":"Không hiểu, nói lại nhé","raw_text":...}`.
6. Lỗi OpenAI (timeout, API error) → status `failed`, `error` = loại lỗi (không chứa nội dung audio), trả 502 `"Lỗi xử lý giọng nói, sẽ thử lại"`.
7. File > 10 MB hoặc rỗng → 422 trước khi gọi OpenAI.

Response 200: `{"entry_id", "raw_text", "transactions": [Transaction]}`.

Test trước (fake transcriber/parser): tạo đúng N giao dịch; gửi lại cùng `entry_id` → cùng kết quả, fake chỉ bị gọi 1 lần, DB không trùng; gửi lại sau `failed` → xử lý lại; 0 giao dịch → 422 + `raw_text`, gửi lại vẫn 422 không gọi fake; fake ném timeout → 502 + `status=failed`; file tạm không còn sau request (cả nhánh lỗi); `stt_ms/parse_ms/total_ms` được ghi; `recorded_at` không có offset → 422.

## Task 8 — Endpoint giao dịch

Schema `Transaction` trả về: `id, voice_entry_id, occurred_at, item, amount_vnd, category_id, category, method, merchant, note, created_at, updated_at`.

- `GET /transactions?from=YYYY-MM-DD&to=YYYY-MM-DD` — ngày theo `Asia/Ho_Chi_Minh`, **bao gồm cả 2 đầu**; bỏ đã xóa mềm; `occurred_at` giảm dần. Thiếu tham số → mặc định hôm nay.
- `PATCH /transactions/{id}` — chỉ các trường SPEC liệt kê (`item, amount_vnd, category_id, method, merchant, occurred_at`); `method` cho phép set `null`; `amount_vnd <= 0` → 422; `category_id` không tồn tại → 422; giao dịch đã xóa → 404.
- `DELETE /transactions/{id}` → đặt `deleted_at`, 204; xóa lần 2 vẫn 204.
- `POST /transactions/{id}/restore` → `deleted_at = NULL`, trả giao dịch.
- `POST /voice/{entry_id}/undo` → xóa mềm mọi giao dịch của lần nói, 204; `entry_id` không tồn tại → 404.

Test trước cho từng trường hợp trên, đặc biệt ranh giới ngày: giao dịch lúc 23:30 giờ VN (16:30 UTC) thuộc đúng ngày VN, giao dịch 00:10 giờ VN thuộc ngày hôm sau.

## Task 9 — `GET /summary?month=YYYY-MM`

Response đề xuất (SPEC chỉ ghi "object tổng hợp"):

```json
{
  "month": "2026-10",
  "total_vnd": 3450000,
  "count": 87,
  "prev_month": "2026-09",
  "prev_total_vnd": 3000000,
  "diff_vnd": 450000,
  "diff_pct": 15.0,
  "by_category": [
    {"category_id": 1, "category": "Ăn sáng", "total_vnd": 600000, "count": 28}
  ]
}
```

- Tháng tính theo `Asia/Ho_Chi_Minh`, bỏ xóa mềm; `by_category` giảm dần theo tiền, chỉ gồm danh mục có chi.
- `diff_pct = null` khi tháng trước bằng 0. Tiền luôn là số nguyên; chỉ `diff_pct` là số thực (làm tròn 1 chữ số, chỉ để hiển thị).
- Thiếu `month` → tháng hiện tại. Test: ranh giới tháng (23:59 ngày 31 giờ VN), xóa mềm không tính, tháng trước rỗng.

## Task 10 — `GET /categories`

Trả danh mục `is_active` theo `sort_order`: `[{"id","name","sort_order"}]`. Test trước.

## Task 11 — Chuẩn bị deploy (làm trên máy local)

- `deploy/nginx/expense-voice.conf`: HTTPS, `client_max_body_size 10M`, `proxy_read_timeout 60s`, `proxy_pass http://127.0.0.1:8000`, 80 → 443, `limit_req` 30r/m (burst 10) cho `/api/`.
- `deploy/backup.sh` (viết sẵn, cron để Phase 4).
- `deploy/README.md`: các bước deploy từng lệnh.
- Chạy toàn bộ `pytest -m "not openai"` và `pytest -m openai` local đều qua.

## Task 12 — Deploy lên EC2 (**từng bước cần Loi xác nhận**)

1. Kiểm tra DNS `expense-voice.s-itpro.com` → `54.251.52.235`.
2. SSH, trong `tmux`: clone repo, tạo `.env` trên server (Loi cung cấp OpenAI key + sinh `DEVICE_TOKEN`), `docker compose up -d --build`, `alembic upgrade head`.
3. Kiểm tra `ss -ltnp`: API chỉ ở `127.0.0.1:8000`, không có 5432.
4. **Hỏi Loi trước:** copy nginx conf, `nginx -t`, reload, `certbot --nginx -d expense-voice.s-itpro.com`.
5. Từ máy local: `curl` health + gửi 6 file audio thật → kết quả đúng; gửi lại cùng `entry_id` → không trùng; ghi lại `stt_ms/parse_ms/total_ms`.
6. Đánh dấu checklist Phase 1 trong SPEC.md.

---

## Những thứ cần Loi

1. Duyệt plan này (đặc biệt: response `/summary`, hành vi 409 / gửi lại sau `failed`).
2. Chọn `OPENAI_PARSE_MODEL` — xem pending-decisions.
3. OpenAI API key riêng cho project (đặt giới hạn chi tiêu tháng) — dùng ở `.env` local khi tới Task 6.
4. 6 file audio ghi bằng Fold 8 (Task 6).
5. Xác nhận trước khi push GitHub, và trước mỗi thao tác nginx/certbot trên server.
