# Phase 2 + 3 — Ghi âm trên Android + Màn hình xem

Trạng thái: **chờ Loi duyệt** · 2026-10-11

Loi yêu cầu làm Phase 2 và 3 cùng lúc (2026-10-11). Hai phase dùng chung nền (Room, Retrofit, Repository, Settings), nên làm nền 1 lần rồi tách nhánh ghi âm / màn hình xem. Checklist vẫn đánh dấu riêng theo SPEC.

Checklist SPEC tương ứng:

- Phase 2
  - [ ] `RecordActivity` + phát hiện im lặng + hàng đợi Room + `UploadWorker` → Task 4–7
  - [ ] Notification kết quả + Hoàn tác → Task 8
  - [ ] Trigger: nút nguồn, Quick Settings tile, widget → Task 9
  - [ ] Bật chế độ máy bay, nói 3 lần, tắt chế độ máy bay → cả 3 lên server, không trùng → Task 13
- Phase 3
  - [ ] Hôm nay: danh sách, tổng, sửa/xóa → Task 10
  - [ ] Tháng này: tổng, so với tháng trước, danh mục kèm thanh tỷ lệ → Task 11

Backend: **không cần sửa** — dùng nguyên các endpoint Phase 1.

---

## Task 0 — Chuẩn bị (Loi làm, ~30 phút)

1. Cài **Android Studio** bản stable mới nhất (đi kèm JDK và SDK). Trong SDK Manager cài Android SDK Platform bản mới nhất mà Fold 8 chạy.
2. Fold 8: bật Tùy chọn nhà phát triển → **Gỡ lỗi không dây** → `adb pair <ip:port>` rồi `adb connect <ip:port>` (theo SPEC mục "Build và cài lên Fold 8").
3. Báo em khi `adb devices` thấy máy. Em sẽ đọc `ro.build.version.sdk` để chốt `minSdk`.

Em build bằng `gradlew` dùng JDK đi kèm Android Studio; Loi mở project bằng Android Studio khi muốn xem.

## Nguyên tắc kỹ thuật

- Package `com.sitpro.expensevoice`. 1 module `app`. Gradle Kotlin DSL + version catalog.
- `minSdk` = API của Fold 8 (đọc từ máy), `targetSdk`/`compileSdk` = bản mới nhất có trong SDK. Chỉ 1 thiết bị → không viết code tương thích bản cũ.
- **Không dùng Hilt/DI framework**: 1 `AppContainer` tạo sẵn Room, ApiService, Repository. Ít phụ thuộc, dễ đọc.
- Thư viện: Compose (Material 3), Navigation Compose, Room (KSP), WorkManager, Retrofit + OkHttp + kotlinx.serialization, Glance, `androidx.security:security-crypto` (EncryptedSharedPreferences theo SPEC).
- **Thời gian hiển thị theo `Asia/Ho_Chi_Minh`** (ghi rõ zone, không phụ thuộc cài đặt máy). Tiền là `Long` VND, hiển thị `20.000đ`.
- Thông báo cho người dùng bằng tiếng Việt; code tiếng Anh.
- Không log token, nội dung audio, `raw_text`.

## Test

- **JVM unit test (TDD)** cho mọi logic thuần: phát hiện im lặng, format tiền/giờ/phương thức, nội dung notification, phân loại kết quả upload, parse số tiền người dùng gõ, tính khoảng ngày/tháng.
- **Robolectric + MockWebServer + Room in-memory** cho `ApiService`, DAO, `UploadWorker`, `UndoWorker` (không cần điện thoại).
- **Thử trên Fold 8** cho phần phần cứng/hệ thống: ghi âm, màn hình khóa, nút nguồn, tile, widget, chế độ máy bay. Ghi kết quả vào cuối file này.

## Cấu trúc (theo SPEC)

```
android/app/src/main/java/com/sitpro/expensevoice/
  ExpenseApp.kt, AppContainer.kt, MainActivity.kt
  capture/   RecordActivity, VoiceTileService, QuickWidget (Glance)
  audio/     Recorder (MediaRecorder), SilenceDetector (logic thuần)
  data/      Room: PendingUpload, CachedTransaction, CachedSummary, CachedCategory; Repository; SettingsStore
  sync/      UploadWorker, UndoWorker, UploadOutcome
  api/       ApiService, AuthInterceptor, DTO
  ui/        TodayScreen, MonthScreen, EditSheet, SettingsScreen, Format.kt
  notify/    ResultNotifier, UndoReceiver
```

---

## Task 1 — Khởi tạo project Android

- `android/` với Gradle wrapper, version catalog, app module, Compose, `MainActivity` trống.
- Kiểm tra `.gitignore` đã chặn `local.properties`, build output, `*.jks`, `keystore.properties`.
- Xong khi: `./gradlew testDebugUnitTest assembleDebug` qua, `installDebug` lên Fold 8 mở được app.

## Task 2 — Nền: format, settings, API client

- `Format.kt` (TDD): `20000 → "20.000đ"`, `1200000 → "1.200.000đ"`; method → `Tiền mặt` / `Thẻ` / `Chuyển khoản` / `?`; giờ `HH:mm` theo giờ VN; dòng notification `✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt`.
- `SettingsStore`: URL server (mặc định `https://expense-voice.s-itpro.com`), device token — lưu bằng EncryptedSharedPreferences.
- `ApiService` (Retrofit) cho đủ endpoint Phase 1; `AuthInterceptor` gắn `Bearer`. Đổi URL/token trong Cài đặt → AppContainer tạo lại client.
- Test (MockWebServer): header Authorization đúng; parse JSON giao dịch/summary/lỗi 422 có `raw_text`; timeout OkHttp 30 s (server chờ OpenAI tối đa 20 s).

## Task 3 — Room

- `PendingUpload(entryId PK, filePath, recordedAt, createdAt, attempts, lastError)`.
- `CachedTransaction` (cache giao dịch theo ngày cho màn Hôm nay), `CachedSummary(month PK, json, fetchedAt)`, `CachedCategory`.
- Test DAO: thêm/xóa pending, đếm pending dạng `Flow`, thay cache theo ngày.

## Task 4 — Ghi âm + phát hiện im lặng

- `SilenceDetector` (TDD, logic thuần): nhận biên độ mỗi 100 ms →
  - chưa có tiếng nói: chờ;
  - đã có tiếng nói, im lặng liên tục 1,5 s → **dừng**;
  - tổng 30 s → **dừng**;
  - 8 s đầu không có tiếng nói nào → **hủy** (không upload; báo "Không nghe thấy gì") — *đề xuất, xem pending-decisions*.
  - Ngưỡng biên độ là hằng số, chỉnh sau khi thử thật trên Fold 8.
- `Recorder`: `MediaRecorder` AAC trong MPEG-4 (`.m4a`), 16 kHz, mono, 32 kbps; poll `getMaxAmplitude()` mỗi 100 ms.

## Task 5 — `RecordActivity`

- Theme trong suốt, `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`, `singleTask`.
- Mở lên: rung nhẹ, ghi ngay. UI nhỏ ở giữa màn hình: micro nhấp nháy, đồng hồ, nút **Dừng** / **Hủy**.
- Dừng → lưu file vào `filesDir/pending/<entryId>.m4a`, tạo `PendingUpload` (`entryId` = UUID, `recordedAt` = lúc bắt đầu nói, có offset +07:00), xếp `UploadWorker`, **đóng Activity ngay**.
- Chưa có quyền `RECORD_AUDIO` → mở `MainActivity` để xin quyền (không xin được quyền trên màn hình khóa).

## Task 6 — `UploadWorker`

- Mỗi `entryId` 1 unique work (`KEEP`), ràng buộc có mạng, backoff mũ từ 10 s, **expedited** để chạy ngay (mục tiêu 3 s).
- `UploadOutcome` (TDD) phân loại phản hồi:
  - 200 → lưu giao dịch vào cache, xóa file + pending, notify ✓.
  - 422 có `raw_text` → xóa file + pending, notify "Không hiểu, nói lại nhé" + câu đã nghe.
  - 422 khác (file rỗng/lỗi định dạng) → xóa, notify lỗi.
  - 409 / 5xx / mất mạng / timeout → retry (giữ file).
  - 401 → giữ pending, notify "Token không hợp lệ — mở Cài đặt", dừng retry tới khi bấm "Gửi lại hàng đợi".
- Test Robolectric + MockWebServer cho từng nhánh; kiểm file bị xóa đúng lúc.

## Task 7 — Gửi lại hàng đợi

- Khi app mở / khi có mạng lại: xếp lại work cho mọi `PendingUpload` (phòng trường hợp work bị hệ thống hủy).
- Nút "Gửi lại hàng đợi" trong Cài đặt gọi cùng hàm này.

## Task 8 — Notification + Hoàn tác

- Ngay khi dừng ghi: notification im lặng "⏳ Đang gửi…" (không mạng → "Đã lưu, sẽ gửi khi có mạng"). Kết quả thay thế notification này (cùng id theo `entryId`).
- 1 giao dịch: `✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt`. Nhiều giao dịch: tiêu đề `✓ 2 giao dịch · 47.000đ`, mỗi dòng 1 giao dịch (InboxStyle).
- Nút **Hoàn tác** → `UndoReceiver` → `UndoWorker` gọi `POST /voice/{entry_id}/undo` (retry khi mất mạng) → notification đổi thành "Đã hoàn tác", cache cập nhật.
- Xin quyền `POST_NOTIFICATIONS` lần đầu mở app.

## Task 9 — Trigger

- **Nút nguồn:** Samsung "Nhấn hai lần → Mở ứng dụng" mở *activity launcher* của app được chọn. Nên app có **2 icon**: "Chi tiêu" (`MainActivity`) và "Ghi chi tiêu" (`RecordActivity`); Loi chọn "Ghi chi tiêu" trong cài đặt phím bên. *Cần thử trên Fold 8.*
- **Quick Settings tile** `VoiceTileService`: chạm → `startActivityAndCollapse(PendingIntent)` mở `RecordActivity`, chạy được khi khóa màn hình.
- **Widget Glance** 1 nút micro → mở `RecordActivity`. Màn hình ngoài (cover screen) của Samsung có thể cần khai báo riêng → thử trên Fold 8, nếu cần thì thêm meta-data Samsung.

## Task 10 — Màn hình Hôm nay (mặc định)

- Trên cùng: ngày (◀ ▶ đổi ngày) + **tổng chi**.
- Danh sách mới nhất trước: `giờ · món · số tiền · danh mục · phương thức` (trống → `?`).
- Kéo xuống để tải lại. Room là nguồn hiển thị → giao dịch mới từ `UploadWorker` tự hiện, mất mạng vẫn xem được cache + dòng "cập nhật lúc …".
- Chạm 1 dòng → **EditSheet** (bottom sheet): món, số tiền (bàn phím số, hiển thị `20.000`), danh mục (từ `/categories`), phương thức (Tiền mặt / Thẻ / Chuyển khoản / Chưa rõ), nơi mua → `PATCH`. Nút **Xóa** → `DELETE` + Snackbar "Đã xóa · Hoàn tác" → `restore`.
- Nút micro nổi → mở `RecordActivity`.
- Test: logic ViewModel (tổng, đổi ngày, parse số tiền nhập tay "20.000" → 20000, chặn ≤ 0).

## Task 11 — Màn hình Tháng này

- Tổng tháng; chênh lệch so với tháng trước: số tiền + % (tăng màu đỏ, giảm màu xanh; `diff_pct = null` → chỉ hiện số tiền).
- Danh mục giảm dần theo tiền, mỗi dòng có **thanh ngang tỷ lệ vẽ bằng Compose** (không thư viện biểu đồ).
- ◀ ▶ đổi tháng. Dữ liệu `GET /summary`; mất mạng → cache + "cập nhật lúc …".

## Task 12 — Cài đặt

- URL server, device token (ẩn ký tự), số mục đang chờ, nút **Gửi lại hàng đợi**.
- Lưu → gọi thử `GET /categories`: báo "Kết nối OK" hoặc lỗi tiếng Việt (sai token / không tới được server).
- Điều hướng: 2 tab **Hôm nay** / **Tháng này** + icon ⚙ mở Cài đặt.

## Task 13 — Kiểm chứng trên Fold 8

1. Mở app lần đầu: cấp quyền, nhập token production.
2. Ghi từ: icon "Ghi chi tiêu", nút nguồn nhấn đúp, tile, widget màn hình chính, widget màn hình ngoài, từ màn hình khóa.
3. Đo thời gian từ lúc dừng nói tới notification ✓ (mục tiêu < 3 s, 4G và Wi-Fi), so với `total_ms` trên server.
4. **Chế độ máy bay:** nói 3 lần → bật mạng → cả 3 lên server, kiểm DB không trùng.
5. Hoàn tác từ notification; sửa/xóa/hoàn tác trên màn Hôm nay; xem Tháng này; tắt mạng xem cache.
6. Ghi kết quả + các câu parse sai (nếu có) vào cuối file này, đánh dấu checklist SPEC.

---

## Những thứ cần Loi

1. Duyệt plan (đặc biệt: 2 icon cho nút nguồn, hủy khi 8 s không nói, notification "Đang gửi…").
2. Task 0: cài Android Studio + kết nối Fold 8 qua Wireless debugging.
3. Thử trên máy ở Task 9 và 13 (em hướng dẫn từng bước).

## Task bổ sung (Loi yêu cầu 2026-10-11)

- [x] Nút ghi âm trong app: tròn 80dp, đặt giữa phía dưới (trên thanh tab), hiện ở cả Hôm nay và Tháng này. Chờ Loi xem trên máy.
- [x] Tài liệu hướng dẫn cài đặt trên Fold 8: `docs/fold8-setup.md` (ghép nối adb, nút nguồn, Quick Settings, widget, pin).

## Nhật ký thử trên Fold 8

**2026-10-11** (SM-F971B, Android 17 / API 37, One UI 9)

- Kết nối adb qua Wi-Fi chỉ được khi tắt FortiClient VPN trên laptop (VPN chặn mạng LAN).
- Cài debug APK, nhập token production qua adb → Cài đặt báo "Kết nối OK"; màn Hôm nay tải được.
- ✅ Ghi từ nút micro trong app: "tiền xe Grab hai trăm mười bốn ngàn" → tiền xe Grab · 214.000đ · method null. Server: `stt_ms` 1463, `parse_ms` 1932, `total_ms` 3408. File audio đã bị xóa khỏi `files/pending`.
- ✅ Quick Settings tile "Ghi chi tiêu": chạy được.
- ❌ Nút nguồn: danh sách "Open app" của Samsung không có icon "Ghi chi tiêu" (1 dòng mỗi app). Đã đổi manifest để `RecordActivity` là launcher activity đầu tiên; cài lại, chờ thử.
- Chưa thử: Hoàn tác từ notification, ghi trên màn hình khóa, widget (màn hình chính + màn hình ngoài), chế độ máy bay, sửa/xóa trên Hôm nay, Tháng này, thời gian từ lúc ngừng nói tới notification ✓.

**2026-10-15** — Loi thử trên Fold 8: ✅ Hoàn tác từ notification · ✅ Lưu thay đổi ngày/giờ (màn sửa).
