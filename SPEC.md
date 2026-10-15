# Spec MVP – App ghi chi tiêu bằng giọng nói

Oct 10, 2026 · @Hello

## Mục tiêu và phạm vi MVP

Mỗi giao dịch được ghi bằng một thao tác: nhấn → nói → xong, phản hồi trong khoảng 3 giây. App chỉ dùng cho 1 người (anh), 1 thiết bị (Samsung Galaxy Z Fold 8), không phát hành lên Play Store.

Ví dụ câu nói: *"bánh mì ăn sáng 20k tiền mặt, cafe 27k FamilyMart cà thẻ"* → 2 giao dịch.

**Phải có (MVP)**

- Trigger 1 chạm: nhấn đúp nút nguồn, Quick Settings tile, widget màn hình chính.
- Ghi âm, tự dừng khi im lặng khoảng 1,5 giây.
- OpenAI chuyển giọng nói thành chữ, sau đó parse thành 1 hoặc nhiều giao dịch.
- Lưu vào Postgres trên server; hàng đợi offline khi mất sóng.
- Notification xác nhận kèm nút Hoàn tác.
- Hai màn hình xem: **Hôm nay** và **Tháng này**; chạm vào giao dịch để sửa hoặc xóa.

**Nice to have (sau MVP)**

- Nhắc cuối ngày bổ sung phương thức thanh toán còn trống, chọn ngay trên notification.
- Gắn tọa độ GPS lúc nói để thống kê nơi mua.
- Lịch sử có lọc và tìm kiếm; báo cáo tuần; Metabase cho phân tích sâu.

**Ngoài phạm vi**

- Đối soát với ngân hàng, Google Wallet, đọc notification bank.
- Theo dõi thu nhập, ngân sách, nhiều người dùng.

## Tech stack

Stack gồm 3 khối: app Android, backend trên EC2 và OpenAI. OpenAI key chỉ nằm trên server; app chỉ giữ token để gọi backend.

| Lớp | Công nghệ | Ghi chú |
| --- | --- | --- |
| Android UI | Kotlin + Jetpack Compose | minSdk theo Fold 8, chỉ 1 Activity chính |
| Trigger | Activity ghi âm (nút nguồn), `TileService`, widget Jetpack Glance | Widget dùng được trên màn hình ngoài |
| Ghi âm | `MediaRecorder` → AAC/m4a, 16 kHz mono | Tự dừng khi im lặng \~1,5 giây, tối đa 30 giây |
| Lưu trên máy | Room (SQLite) | Hàng đợi upload + cache giao dịch gần đây |
| Đồng bộ | WorkManager + Retrofit/OkHttp | Tự gửi lại khi có mạng |
| Bảo mật trên máy | EncryptedSharedPreferences | Lưu device token |
| Backend | Python 3.12 + FastAPI + SQLAlchemy + Alembic | Chạy trong Docker |
| Database | PostgreSQL 16 | Container riêng, không publish port |
| Reverse proxy | nginx + certbot (Let's Encrypt) | Đã có trên EC2 |
| Speech-to-text | OpenAI transcribe (`gpt-4o-mini-transcribe` hoặc bản mới nhất) | `language=vi` |
| Parse | OpenAI model mini + Structured Outputs | JSON luôn đúng schema |
| Hạ tầng | AWS EC2 công ty, Docker Compose | Sẽ chuyển sang server riêng sau |

## Luồng hoạt động

Một lần nói đi từ điện thoại lên server, qua OpenAI 2 lần, lưu vào Postgres rồi trả kết quả về thành notification.

&#91;embedded content: luồng 1 lần nói · điện thoại, server, OpenAI\]

Mất mạng thì file nằm trong hàng đợi Room và tự gửi khi có mạng; `entry_id` giúp server không tạo bản ghi trùng.

## Schema DB

Hai bảng chính: `voice_entries` lưu mỗi lần nói, `transactions` lưu từng giao dịch tách ra từ đó. Thêm bảng `categories` cố định để LLM chỉ chọn trong danh sách.

```sql
CREATE TABLE categories (
  id          SERIAL PRIMARY KEY,
  name        TEXT UNIQUE NOT NULL,      -- 'Ăn sáng', 'Cafe', 'Gửi xe'...
  sort_order  INT DEFAULT 0,
  is_active   BOOLEAN DEFAULT TRUE
);

CREATE TABLE voice_entries (
  id           UUID PRIMARY KEY,           -- app sinh ra, chống gửi trùng
  recorded_at  TIMESTAMPTZ NOT NULL,       -- giờ anh nói (trên máy)
  received_at  TIMESTAMPTZ DEFAULT now(),
  raw_text     TEXT,                       -- câu gốc sau transcribe
  status       TEXT NOT NULL,              -- received | transcribed | parsed | failed
  error        TEXT,
  latitude     DOUBLE PRECISION,           -- nice to have
  longitude    DOUBLE PRECISION,
  stt_ms       INT,                        -- thời gian transcribe
  parse_ms     INT,                        -- thời gian parse
  total_ms     INT                         -- tổng thời gian xử lý trên server
);

CREATE TABLE transactions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,
  occurred_at     TIMESTAMPTZ NOT NULL,    -- mặc định = recorded_at
  item            TEXT NOT NULL,           -- 'bánh mì'
  amount_vnd      BIGINT NOT NULL CHECK (amount_vnd > 0),
  category_id     INT REFERENCES categories(id),
  method          TEXT CHECK (method IN ('cash','card','transfer')), -- NULL = chưa rõ
  merchant        TEXT,                    -- 'FamilyMart', NULL nếu không nói
  note            TEXT,
  deleted_at      TIMESTAMPTZ,             -- xóa mềm, phục vụ Hoàn tác
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX ON transactions (occurred_at) WHERE deleted_at IS NULL;
```

- Danh mục khởi tạo: Ăn sáng, Ăn trưa, Ăn tối, Cafe/Đồ uống, Gửi xe, Xăng/Di chuyển, Đi chợ/Siêu thị, Mua sắm, Hóa đơn, Giải trí, Sức khỏe, Khác.
- Tiền lưu bằng số nguyên VND, không dùng số thực.
- File audio không lưu vào DB; xóa khỏi đĩa ngay sau khi transcribe xong.

## API backend

Mọi endpoint đều yêu cầu header `Authorization: Bearer <DEVICE_TOKEN>`; token đặt trong `.env`. Prefix `/api/v1`.

| Method | Endpoint | Việc làm | Trả về |
| --- | --- | --- | --- |
| POST | `/voice` | Nhận audio (multipart: `file`, `entry_id`, `recorded_at`, `lat?`, `lng?`), transcribe, parse, lưu | `entry_id`, `raw_text`, danh sách giao dịch đã tạo |
| GET | `/transactions?from=&to=` | Danh sách giao dịch trong khoảng ngày, mới nhất trước | mảng giao dịch |
| PATCH | `/transactions/{id}` | Sửa item, amount, category, method, merchant, occurred\_at | giao dịch sau khi sửa |
| DELETE | `/transactions/{id}` | Xóa mềm (đặt `deleted_at`) | 204 |
| POST | `/transactions/{id}/restore` | Hoàn tác xóa | giao dịch |
| POST | `/voice/{entry_id}/undo` | Hoàn tác cả lần nói (xóa mềm mọi giao dịch của nó) | 204 |
| GET | `/summary?month=YYYY-MM` | Tổng tháng, tổng theo danh mục, so với tháng trước | object tổng hợp |
| GET | `/categories` | Danh mục đang dùng | mảng |
| GET | `/health` | Kiểm tra sống (không cần token) | `ok` |

**Quy tắc xử lý**

- `POST /voice` **idempotent** theo `entry_id`: app gửi lại cùng `entry_id` thì trả kết quả cũ, không tạo trùng.
- Xử lý đồng bộ trong request (mục tiêu dưới 3 giây). Timeout OpenAI 20 giây; lỗi thì lưu `status=failed`, trả 502 để app thử lại sau.
- Parse ra 0 giao dịch (câu không có số tiền) → trả 422 kèm `raw_text`, app hiện thông báo "Không hiểu, nói lại nhé".
- `GET /summary` tính theo múi giờ `Asia/Ho_Chi_Minh`, bỏ các giao dịch đã xóa mềm.

## Pipeline AI

Hai lần gọi OpenAI: transcribe audio thành chữ, rồi parse chữ thành JSON bằng Structured Outputs. Phương thức thanh toán không nói thì để `null`, **không đoán**.

**Bước 1: Transcribe**

- Model: `gpt-4o-mini-transcribe` (đổi qua biến `OPENAI_STT_MODEL`). Không cố định `language` để nhận cả tiếng Việt và tiếng Anh (biến `OPENAI_STT_LANGUAGE`, rỗng = tự nhận, `vi` = chỉ tiếng Việt). Đã đo 2026-10-15: bỏ `language="vi"` không làm kém 7 audio tiếng Việt.
- Tham số `prompt` gợi ý từ vựng: `"k, nghìn, ngàn, ca, cà thẻ, chuyển khoản, tiền mặt, FamilyMart, Circle K, GrabFood, gửi xe, thousand, million, cash, card, bank transfer, parking, coffee, breakfast"` để nhận đúng cách nói tắt.

**Bước 2: Parse**

- Model: model mini của OpenAI (biến `OPENAI_PARSE_MODEL`), `temperature=0`, `response_format` = JSON Schema strict bên dưới.
- Danh sách danh mục lấy từ bảng `categories`, chèn vào prompt mỗi lần gọi.

System prompt:

```text
Bạn tách câu nói tiếng Việt hoặc tiếng Anh về chi tiêu thành danh sách giao dịch.
Quy tắc:
- Mỗi món có số tiền là 1 giao dịch. Một câu có thể có nhiều giao dịch.
- Số tiền ra số nguyên VND: "20k" = "20 nghìn" = "20 ngàn" = "20 ca" = "hai chục" = 20000; "1 triệu 2" = 1200000; "20 000 vnd" = 20000. Số trơn dưới 1000 ("cafe 27") hiểu là nghìn. Đọc số kiểu tiếng Việt: "1 triệu không trăm năm chục ngàn" = 1050000; "1 triệu 2" = "1 triệu hai trăm ngàn" = 1200000; "hai trăm rưỡi" = 250000. "X mươi chục" là cách nghe nhầm của "X chục" ("hai mươi chục ngàn" = 20000, "bốn trăm hai mươi chục ngàn" = 420000), không nhân thêm 10.
- Tiếng Anh: "27k" = "27 thousand" = 27000; "1.2 million" = "1 million 2" = 1200000; "20,000 dong" = 20000.
- "gửi xe 2 lần 5k" = 1 giao dịch 10000, item ghi "gửi xe (2 lần)".
- method: "tiền mặt"/"cash" -> cash; "cà thẻ"/"quẹt thẻ"/"chạm"/"thẻ" -> card; "chuyển khoản"/"CK"/"quét QR"/"QR"/"bank transfer"/"transfer" -> transfer; "by card"/"credit card"/"tap" -> card. Không nói -> null. Không đoán.
- merchant: tên cửa hàng/nơi mua nếu được nói ("FamilyMart", "bánh mì cô Hai", "ở Vincom" -> "Vincom"). Không nói -> null.
- category: chọn đúng 1 tên trong danh sách: {categories}. Không chắc -> "Khác".
- item: tên món ngắn gọn, giữ nguyên ngôn ngữ người nói.
- Không có số tiền -> trả transactions rỗng.
```

JSON Schema (strict):

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["transactions"],
  "properties": {
    "transactions": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["item", "amount_vnd", "category", "method", "merchant"],
        "properties": {
          "item":       {"type": "string"},
          "amount_vnd": {"type": "integer"},
          "category":   {"type": "string"},
          "method":     {"type": ["string", "null"], "enum": ["cash", "card", "transfer", null]},
          "merchant":   {"type": ["string", "null"]}
        }
      }
    }
  }
}
```

**Bộ test bắt buộc** (pytest, gọi API thật, chạy tay trước mỗi lần đổi prompt):

| Câu nói | Kết quả mong đợi |
| --- | --- |
| bánh mì ăn sáng 20k tiền mặt | bánh mì · 20000 · Ăn sáng · cash · null |
| cafe 27k FamilyMart cà thẻ | cafe · 27000 · Cafe/Đồ uống · card · FamilyMart |
| gửi xe 5 nghìn | gửi xe · 5000 · Gửi xe · null · null |
| nước cam 15 ca, gửi xe 2 lần 5k ở Vincom | 2 giao dịch: 15000; 10000 · merchant Vincom |
| đi siêu thị 1 triệu 2 chuyển khoản | 1200000 · Đi chợ/Siêu thị · transfer |
| siêu thị Coopmart một triệu không trăm năm chục ngàn | 1050000 · Đi chợ/Siêu thị · null · Coopmart |
| hôm nay trời đẹp | 0 giao dịch → 422 |
| mua đồ trên Shopee, bốn trăm hai mươi chục ngàn, cà thẻ | 420000 · card (STT đôi khi nghe nhầm "hai chục" thành "hai mươi chục") |
| cafe ba mươi ngàn | 30000 (chữ "mươi" đúng nghĩa không bị ảnh hưởng) |
| coffee 27k at FamilyMart by card | coffee · 27000 · Cafe/Đồ uống · card · FamilyMart |
| breakfast banh mi 20 thousand cash | 20000 · Ăn sáng · cash |
| parking 5 thousand | 5000 · Gửi xe · null · null |
| groceries 1.2 million bank transfer | 1200000 · Đi chợ/Siêu thị · transfer |
| the weather is nice today | 0 giao dịch → 422 |

## Android app

App có 2 phần: luồng ghi âm (chạy được từ màn hình khóa, không cần mở giao diện chính) và 2 màn hình xem đơn giản.

**Tên và ngôn ngữ** (bổ sung 2026-10-14): tên app **Ví Cá Mập** / **Sharko Wallet** (đổi 2026-10-15). Giao diện song ngữ, **mặc định tiếng Việt** khi mở app lần đầu (kể cả máy để English); nút **VI / EN** trên thanh trên cùng để đổi ngay, đồng bộ với Settings → Apps → Language của Android (đổi 2026-10-15). Danh mục lưu tiếng Việt trên server, app dịch khi hiển thị. Tiền luôn hiển thị dạng `20.000đ`.

**Cấu trúc project**

```text
app/src/main/java/.../
  capture/   RecordActivity, VoiceTileService, QuickWidget (Glance)
  audio/     Recorder (MediaRecorder + phát hiện im lặng)
  data/      Room: PendingUpload, CachedTransaction; Repository
  sync/      UploadWorker (WorkManager)
  api/       Retrofit ApiService, AuthInterceptor
  ui/        TodayScreen, MonthScreen, EditSheet, SettingsScreen
  notify/    ResultNotifier (notification + nút Hoàn tác)
```

**Luồng ghi âm**

1. Trigger mở `RecordActivity` (trong suốt, `showWhenLocked`, `turnScreenOn`): rung nhẹ, bắt đầu ghi ngay.
2. Phát hiện im lặng qua `getMaxAmplitude()` mỗi 100 ms; im lặng 1,5 giây sau khi đã có tiếng thì dừng. Có nút Dừng / Hủy.
3. Lưu file vào bộ nhớ app, tạo `PendingUpload` (`entry_id` = UUID, `recorded_at`), đóng Activity ngay.
4. `UploadWorker` (ràng buộc có mạng, retry backoff) gửi `POST /voice`, xong thì xóa file.
5. `ResultNotifier` hiện `✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt` (nhiều giao dịch thì gộp 1 notification), nút **Hoàn tác** gọi `/voice/{entry_id}/undo`. Lỗi 422 thì hiện "Không hiểu, nói lại nhé" kèm câu đã nghe được.

**Màn hình Hôm nay** (màn hình mặc định)

- Tổng chi hôm nay ở trên cùng.
- Danh sách giao dịch mới nhất trước: giờ · món · số tiền · danh mục · phương thức (để trống thì hiện dấu `?`).
- Chạm vào 1 dòng → bottom sheet sửa (item, số tiền, ngày, giờ, danh mục, phương thức, nơi mua) hoặc xóa. Ngày giờ theo giờ Việt Nam, không cho chọn tương lai; chuyển sang ngày khác thì báo "Đã chuyển sang dd/MM".
- Kéo xuống để tải lại; chuyển ngày bằng mũi tên trái/phải.
- Nút micro nổi để ghi âm ngay trong app.

**Màn hình Tháng này**

- Tổng chi tháng, chênh lệch so với tháng trước (số tiền và %).
- Danh sách danh mục xếp theo số tiền giảm dần, mỗi dòng có thanh ngang tỷ lệ vẽ bằng Compose (không dùng thư viện biểu đồ).
- Chuyển tháng bằng mũi tên trái/phải.
- Dữ liệu từ `GET /summary`; mất mạng thì hiện cache gần nhất kèm dòng "cập nhật lúc …".

**Cài đặt** (màn hình phụ): URL server, device token, nút "Gửi lại hàng đợi", số mục đang chờ.

**Quyền cần xin:** `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `INTERNET`. Vị trí chỉ thêm khi làm phần nice to have.

## Hạ tầng

Backend gồm 2 container trong 1 file Docker Compose, đứng sau nginx đã có sẵn trên EC2. Thiết kế để sau này chuyển server chỉ cần copy repo, `.env` và bản backup.

**Thông tin triển khai**

- Domain: `expense-voice.s-itpro.com` → A record `54.251.52.235` (EC2 Singapore, ap-southeast-1)
- Repo: [github.com/Sharko2025/expense-voice](https://github.com/Sharko2025/expense-voice)

**Cấu trúc repo**

```text
expense-voice/
  backend/        FastAPI app, alembic/, tests/, Dockerfile
  android/        Android Studio project
  deploy/         docker-compose.yml, nginx/expense-voice.conf, backup.sh
  .env.example
  CLAUDE.md
```

**docker-compose.yml**

```yaml
services:
  api:
    build: ../backend
    env_file: ../.env
    ports: ["127.0.0.1:8000:8000"]   # chỉ nginx gọi được
    depends_on: [db]
    restart: unless-stopped
  db:
    image: postgres:16
    env_file: ../.env
    volumes: ["pgdata:/var/lib/postgresql/data"]   # không publish 5432
    restart: unless-stopped
volumes:
  pgdata:
```

**.env**

```text
DEVICE_TOKEN=<chuỗi ngẫu nhiên 32+ ký tự>
OPENAI_API_KEY=sk-...
OPENAI_STT_MODEL=gpt-4o-mini-transcribe
OPENAI_PARSE_MODEL=<model mini>
POSTGRES_DB=expense
POSTGRES_USER=expense
POSTGRES_PASSWORD=<mạnh>
DATABASE_URL=postgresql+psycopg://expense:<pw>@db:5432/expense
TZ=Asia/Ho_Chi_Minh
```

**nginx** (`/etc/nginx/sites-available/expense-voice.conf`): server block HTTPS cho domain (`expense-voice.s-itpro.com`), `client_max_body_size 10M`, `proxy_read_timeout 60s`, `proxy_pass http://127.0.0.1:8000`, cổng 80 chuyển hướng sang 443. Cấp SSL bằng `sudo certbot --nginx -d expense-voice.s-itpro.com`.

**Bảo mật**

- OpenAI: dùng API key riêng cho project, đặt giới hạn chi tiêu tháng (vài USD).
- So sánh token bằng `hmac.compare_digest`; giới hạn tốc độ ở nginx (`limit_req`, ví dụ 30 request/phút).
- Không ghi log nội dung audio hay token; log chỉ giữ `entry_id`, status, thời gian xử lý.
- Database riêng cho app, không chung với hệ thống công ty.

**Backup**

- `backup.sh` chạy cron 02:00 mỗi đêm: `pg_dump` nén gzip, đẩy lên S3, giữ 30 bản.
- Thử khôi phục 1 lần ngay khi xong MVP.

**Chuyển server sau này**

1. Trên server mới: clone repo, copy `.env`, `docker compose up -d`.
2. Khôi phục bản `pg_dump` mới nhất.
3. Cấu hình nginx + certbot.
4. Đổi DNS của domain. App Android không cần build lại.

## Build và cài lên Fold 8

Không phát hành lên Play Store; trong lúc dev thì cài qua Wireless debugging, khi dùng thật thì cài bằng APK đã ký.

**Khi dev**

1. Trên máy: Cài đặt → Thông tin điện thoại → Thông tin phần mềm → chạm 7 lần vào "Số hiệu bản tạo" để bật Tùy chọn nhà phát triển.
2. Bật **Gỡ lỗi không dây**, ghép đôi với máy tính bằng `adb pair <ip:port>`, rồi `adb connect <ip:port>`.
3. Chạy `./gradlew installDebug` hoặc bấm Run trong Android Studio.

**Bản dùng thật**

1. Tạo keystore **1 lần**: `keytool -genkey -v -keystore expense.jks -keyalg RSA -keysize 2048 -validity 10000 -alias expense`. Cất keystore và mật khẩu vào password manager; mất keystore thì không cài đè bản mới được.
2. Khai báo `signingConfigs` trong Gradle, đọc mật khẩu từ biến môi trường hoặc `keystore.properties` (không commit).
3. `./gradlew assembleRelease` ra `app-release.apk`.
4. Cài: `adb install -r app-release.apk`, hoặc gửi file vào máy rồi mở.

**Cấu hình Samsung**

- **Auto Blocker** (Bảo mật và quyền riêng tư) chặn cài APK ngoài Store: tắt khi cài, cài xong bật lại.
- **Nhấn đúp nút nguồn:** Cài đặt → Tính năng nâng cao → Phím bên → Nhấn hai lần → Mở ứng dụng → chọn app.
- **Pin:** đặt app ở chế độ **Không hạn chế**, để hàng đợi offline gửi đúng lúc có mạng.
- **Widget:** thêm widget ghi âm vào màn hình chính và màn hình ngoài.
- **Quick Settings:** sửa bảng nút nhanh, kéo nút ghi âm vào.

Google đang áp dụng xác minh nhà phát triển cho app cài ngoài Store theo từng nước (chưa áp dụng tại Việt Nam, dự kiến toàn cầu năm 2027). Khi đó `adb install` vẫn dùng được.

## Thứ tự triển khai

Làm backend trước và test bằng `curl` với file audio thu sẵn, sau đó mới làm app. Mỗi phase xong mới sang phase sau.

**Phase 1: Backend + AI**

- [x] Khởi tạo repo, Docker Compose, Alembic migration, seed danh mục
- [x] `POST /voice`: transcribe + parse + lưu, idempotent theo `entry_id`
- [x] Bộ test parse 6 câu ở mục Pipeline AI đều qua
- [x] Các endpoint giao dịch, summary, health
- [x] Deploy lên EC2, nginx + SSL, `curl` từ ngoài gửi audio ra kết quả đúng

**Phase 2: Ghi âm trên Android**

- [ ] `RecordActivity` + phát hiện im lặng + hàng đợi Room + `UploadWorker`
- [ ] Notification kết quả + Hoàn tác
- [ ] Trigger: nút nguồn, Quick Settings tile, widget
- [ ] Bật chế độ máy bay, nói 3 lần, tắt chế độ máy bay → cả 3 lên server, không trùng

**Phase 3: Màn hình xem**

- [ ] Hôm nay: danh sách, tổng, sửa/xóa
- [ ] Tháng này: tổng, so với tháng trước, danh mục kèm thanh tỷ lệ

**Phase 4: Dùng thật**

- [ ] Build APK đã ký, cài lên Fold 8, cấu hình Samsung
- [ ] Backup hằng đêm chạy, thử khôi phục 1 lần
- [ ] Dùng 1 tuần, ghi lại các câu parse sai để chỉnh prompt

**Tiêu chí hoàn thành MVP**

- Từ lúc nhấn đến lúc thấy notification ✓ dưới 3 giây khi có 4G/Wi-Fi.
- Ít nhất 9/10 câu nói thường ngày parse đúng số tiền và món, không cần sửa.
- Mất mạng không mất giao dịch nào, không tạo bản ghi trùng.

## Quyết định đã chốt

Tóm tắt các trao đổi dẫn tới spec này, để sau này không phải bàn lại.

| Chủ đề | Quyết định | Lý do |
| --- | --- | --- |
| Cách nhập liệu | Nói bằng giọng, mỗi giao dịch 1 lần nhấn | Nỗi đau chính là hay quên, lười nhập; chi tiêu lặt vặt ở VN quá ngẫu nhiên để định nghĩa trước |
| Đối soát bank / Google Wallet | Không làm trong MVP | Giữ app đơn giản, không cần chạy nền, tránh lỗi Samsung tắt app |
| Phương thức thanh toán | Anh tự nói (tiền mặt / cà thẻ / chuyển khoản); không nói thì để trống | Không để AI đoán; nhắc bổ sung là nice to have |
| Nơi mua | Ghi nếu anh có nói; GPS là nice to have | Phục vụ thống kê sau này |
| Thiết bị | Samsung Galaxy Z Fold 8, Android native (Kotlin) | Cần trigger nút nguồn, widget màn hình ngoài, ghi âm từ màn hình khóa |
| Phát hành app | Cài APK tự ký, không lên Play Store | Chỉ 1 người dùng |
| AI | Chỉ OpenAI: transcribe + parse | 1 key, 1 hóa đơn; Claude API không nhận audio |
| Hạ tầng | EC2 công ty, Docker Compose, nginx + certbot | Có sẵn, làm nhanh; thiết kế để chuyển sang server riêng sau |
| Hiển thị | MVP chỉ 2 màn hình: Hôm nay, Tháng này | Đủ dùng; Metabase và báo cáo tuần để sau |
| Dữ liệu | Postgres trên server, cache Room trên máy, xóa audio sau khi xử lý | Riêng tư, dễ backup và chuyển server |

**Vị trí server:** giữ EC2 Singapore, không chuyển sang VPS Việt Nam. Audio mỗi lần nói chỉ khoảng 20–40 KB; thời gian xử lý chủ yếu nằm ở 2 lần gọi OpenAI, và Singapore có đường quốc tế ổn định hơn. Đo bằng `stt_ms`, `parse_ms`, `total_ms` sau 1 tuần dùng rồi mới tối ưu.
