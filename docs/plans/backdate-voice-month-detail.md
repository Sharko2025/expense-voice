# Ghi âm cho ngày cũ + Xem chi tiết danh mục trong Tháng này

Trạng thái: **xong** · 2026-10-27 (giờ 12:00, phương án A: mở rộng tại chỗ). Đã deploy EC2, Loi kiểm tra trên Fold 8: đạt.

Loi yêu cầu (2026-10-27), ngoài SPEC hiện tại:

1. Đang xem một ngày cũ trên màn Hôm nay → bấm micro → giao dịch ghi vào **ngày đó**, không phải hôm nay.
2. Màn Tháng này: xem được **từng giao dịch** trong mỗi danh mục, không chỉ tổng.
3. Bấm icon **Hôm nay** ở thanh dưới → về ngay ngày hôm nay.

---

## Task 1 — Ghi âm bổ sung cho ngày đang xem

### Hành vi

- Màn **Hôm nay** đang ở ngày khác hôm nay (đã bấm ‹) → nút micro đổi màu và hiện ngày bên dưới icon.
- Bấm → màn ghi âm hiện thêm dòng **"Ghi cho Thứ Bảy, 25/10"** để biết chắc đang ghi cho ngày nào.
- Giao dịch lưu với **ngày đã chọn, giờ cố định 12:00** (Loi chốt 2026-10-27; nói lúc nào cũng thành `25/10 12:00`). Sửa giờ sau bằng màn sửa như bình thường.
- Notification kết quả có thêm ngày khi không phải hôm nay: `✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt — 25/10`.
- Giao dịch hiện ngay trong danh sách của ngày đang xem.
- **Chỉ nút micro trong app** mang theo ngày. Nút nguồn, Quick Settings tile, widget, và micro ở tab Tháng này luôn ghi cho hôm nay (không biết người dùng đang "đứng" ở ngày nào).

```
  Hôm nay (bình thường)          Đang xem 25/10
┌──────────────────────┐     ┌──────────────────────┐
│ ‹  Hôm nay, 27/10  › │     │ ‹  T7, 25/10       › │
│ ...                  │     │ ...                  │
│        ( 🎤 )        │     │       ( 🎤 )         │  ← màu khác
│                      │     │       25/10          │  ← ngày dưới icon
└──────────────────────┘     └──────────────────────┘
```

### Kỹ thuật

**Backend** — thêm 1 trường form tùy chọn cho `POST /voice`:

- `occurred_on` (`YYYY-MM-DD`, theo giờ VN). Không gửi → như cũ (`occurred_at = recorded_at`).
- Có gửi, và trước ngày của `recorded_at` → `occurred_at = occurred_on 12:00 (giờ VN)`. `recorded_at` vẫn giữ đúng giờ thật lúc nói (cột `voice_entries.recorded_at` không đổi nghĩa).
- `occurred_on` **bằng** ngày của `recorded_at` → bỏ qua, dùng `recorded_at` như cũ (tránh 12:00 hôm nay nằm ở tương lai khi nói buổi sáng).
- `occurred_on` sau ngày của `recorded_at` → 422 "Không ghi được cho ngày tương lai".
- Không cần migration DB. Idempotent không đổi (gửi lại cùng `entry_id` trả kết quả cũ).
- **Test** (`test_voice.py`, fake STT/parser): không có `occurred_on`; có `occurred_on` hôm qua → `occurred_at` = hôm qua 12:00 VN; `occurred_on` = hôm nay → `recorded_at`; ngày tương lai → 422; sai định dạng → 422; gửi lại → không trùng.

**Android**

- `RecordActivity`: nhận extra `EXTRA_DAY` (`yyyy-MM-dd`); có thì hiện dòng "Ghi cho …" trên card.
- `PendingUpload`: thêm cột `occurredOn: String?` → **Room migration 1→2** (`ALTER TABLE ADD COLUMN`, không xóa hàng đợi đang chờ). Test migration.
- `UploadProcessor` gửi `occurred_on` khi có. **Unit test** (MockWebServer): có/không có trường này.
- `MainActivity`: FAB lấy `todayVm.day`; khác hôm nay và đang ở tab Hôm nay → truyền `EXTRA_DAY`, đổi màu (`tertiaryContainer`) + hiện `dd/MM`.
- `ResultNotifier`: ngày của giao dịch khác hôm nay → thêm ` — dd/MM`. Hàm thuần, có unit test.

Không đụng tới: prompt parse (không cho LLM tự hiểu "hôm qua" — ngoài yêu cầu).

## Task 2 — Tháng này: chạm danh mục để xem từng giao dịch

### Hành vi

- Chạm 1 dòng danh mục → **mở rộng ngay tại chỗ**, hiện các giao dịch của danh mục đó trong tháng, mới nhất trước. Chạm lại → thu gọn. Mở được nhiều danh mục cùng lúc.
- Mỗi giao dịch: `dd/MM · món · số tiền`, dòng phụ: phương thức · nơi mua (giống màn Hôm nay).
- Chạm 1 giao dịch → mở **đúng màn sửa** đang dùng ở Hôm nay (sửa / xóa / đổi danh mục). Sửa xong → tổng tháng và danh sách tự cập nhật.
- Đổi tháng → thu gọn hết.
- Mất mạng → hiện dữ liệu cache gần nhất (như tổng tháng hiện nay).

```
┌──────────────────────────────────────────┐
│ ‹  Tháng 10/2026                      ›  │
│ 4.250.000đ   ▲ 320.000đ (8%)             │
├──────────────────────────────────────────┤
│ Đi chợ/Siêu thị     ▾       1.800.000đ   │  ← đã mở
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓             │
│ 3 giao dịch                              │
│   24/10  Coopmart           1.050.000đ   │
│          Tiền mặt · Coopmart             │
│   18/10  Đi siêu thị          450.000đ   │
│   05/10  Rau củ               300.000đ   │
├──────────────────────────────────────────┤
│ Cafe/Đồ uống        ▸         640.000đ   │  ← đang đóng
│ ▓▓▓▓▓▓▓▓▓▓                               │
│ 22 giao dịch                             │
└──────────────────────────────────────────┘
```

### Kỹ thuật

- **Backend: không cần sửa.** Dùng `GET /transactions?from=YYYY-MM-01&to=<ngày cuối tháng>` đã có.
- `MonthViewModel.refresh()` tải summary **và** danh sách giao dịch của tháng (1 request, vài trăm dòng là cùng).
- Cache: dùng lại bảng Room `cached_transactions` — thêm `replaceRange(from, to, rows)` và `observeRange(from, to)`. Màn Hôm nay dùng chung bảng nên cũng được cập nhật.
- Lọc theo `categoryId` (giao dịch không có danh mục khớp dòng "Khác" `null`, đúng như summary đang nhóm).
- Hàm thuần `transactionsByCategory(list)` trong `ScreenLogic.kt` → **unit test** (nhóm đúng, `null`, thứ tự mới nhất trước).
- Trạng thái mở/đóng: `Set` khóa danh mục trong `MonthViewModel` (giữ khi gập/xoay máy).
- `EditSheet` + `save/delete` đang nằm trong `TodayViewModel` → tách phần dùng chung (`TransactionEditor`) để `MonthViewModel` gọi được; xóa ở Tháng này cũng có snackbar Hoàn tác. Sau sửa/xóa → refresh summary.
- **Test** repository (MockWebServer): `refreshMonth` thay đúng khoảng ngày, không đụng ngày ngoài tháng.

## Task 3 — Bấm tab "Hôm nay" là về ngay hôm nay

### Hành vi

- Bấm icon **Hôm nay** ở thanh dưới → luôn nhảy về **ngày hôm nay**, dù đang ở tab Tháng này hay đang xem ngày cũ trong chính tab Hôm nay.
- Đang ở chế độ chọn nhiều → thoát chọn (giống khi đổi ngày bằng mũi tên).
- Đã ở hôm nay → chỉ tải lại như hiện nay.

### Kỹ thuật

- `TodayViewModel.goToToday()`: `day = today()`, bỏ chọn, `refresh()`. **Unit test**: từ ngày cũ → về hôm nay, lựa chọn bị xóa.
- `MainActivity.onTab(Tab.TODAY)` gọi `goToToday()` thay vì `refresh()`.
- Nút Back từ Cài đặt về Hôm nay **không** reset ngày (chỉ bấm icon tab mới reset).

## Kiểm chứng trên Fold 8 (audio thật)

1. Hôm nay → ‹ về hôm qua → micro có ngày → nói "cafe 30k" → notification `— 26/10` → giao dịch nằm ở 26/10 12:00; kiểm tra bằng `GET /transactions` trên server.
2. Bật máy bay, ghi cho hôm qua, tắt máy bay → vẫn vào đúng hôm qua (cột `occurredOn` đi qua hàng đợi).
3. Widget / nút nguồn trong lúc app đang ở ngày cũ → vẫn ghi cho hôm nay.
4. Cài đè APK lúc còn bản ghi chờ trong hàng đợi → migration giữ nguyên, gửi được.
5. Tháng này → chạm "Cafe/Đồ uống" → thấy từng mục → chạm 1 mục → đổi danh mục → nó chuyển sang danh mục mới, tổng cập nhật.
6. Màn ngoài (cover screen): dòng giao dịch trong danh mục không tràn chữ.
7. Ở tab Tháng này hoặc đang xem 20/10 → bấm icon Hôm nay → thấy ngay 27/10.

## Sau khi xong

- SPEC.md: thêm `occurred_on` vào bảng API (`POST /voice`); màn Hôm nay thêm "micro ở ngày cũ ghi cho ngày đó"; màn Tháng này thêm "chạm danh mục để xem/sửa từng giao dịch".
- Deploy backend lên EC2 trước khi cài APK mới (APK mới gửi `occurred_on`; server cũ bỏ qua trường lạ nên vẫn ghi vào hôm nay — không lỗi, chỉ sai ngày).
- Ghi vào `docs/pending-decisions.md` là đã duyệt.
