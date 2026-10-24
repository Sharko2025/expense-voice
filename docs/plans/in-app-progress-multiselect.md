# Thanh tiến trình trong app + Chọn nhiều để xóa

Trạng thái: **đã duyệt** · 2026-10-24

Loi yêu cầu (2026-10-24), ngoài SPEC hiện tại:

1. Thanh tiến trình **trong app** (không chỉ notification) khi đang xử lý ghi âm.
2. Chọn nhiều giao dịch trong app, ví dụ để xóa nhiều mục một lần.

Backend: **không cần sửa**. Xóa nhiều = gọi `DELETE /transactions/{id}` lần lượt; Hoàn tác = `POST /transactions/{id}/restore` lần lượt. Một người dùng, mỗi lần vài mục → đủ nhanh, không cần endpoint mới.

---

## Task 1 — Dải "Đang xử lý" trong app

Nguồn dữ liệu: bảng Room `pending_uploads` (đã có). Còn dòng nào là còn bản ghi âm chưa xong.

Hiện ngay dưới thanh trên cùng, trên **cả 3 tab** (Hôm nay, Tháng này, Cài đặt). Không có gì chờ → ẩn hẳn, không chiếm chỗ.

```
┌──────────────────────────────────────────┐
│ Ví Cá Mập                      VI/EN  ⚙  │
├──────────────────────────────────────────┤
│ Đang xử lý 1 bản ghi âm…                 │  ← dải mỏng
│ ▓▓▓▓░░░░░░▓▓▓▓░░░░░░▓▓▓▓░░  (chạy liên tục)│
├──────────────────────────────────────────┤
│ ‹  Hôm nay, 24/10                     ›  │
│ 185.000đ                                 │
│ ...                                      │
```

Ba trạng thái:

| Trạng thái | Dải hiện | Thanh chạy |
|---|---|---|
| Có mạng, đang gửi | "Đang xử lý n bản ghi âm…" | Có |
| Mất mạng | "n bản ghi âm chờ mạng" | Không |
| Lỗi token (401, `blocked`) | "n bản ghi âm chờ sửa token — chạm để mở Cài đặt" | Không |

Xử lý xong → dòng `pending_uploads` bị xóa → dải tự ẩn, giao dịch mới tự hiện trong danh sách Hôm nay (đã có sẵn qua Room flow).

Kỹ thuật:
- `PendingDao`: thêm `observeAll(): Flow<List<PendingUpload>>`.
- Hàm thuần `pendingBanner(rows, online): PendingBanner?` trong `ScreenLogic.kt` → **unit test** đủ 3 trạng thái + rỗng.
- Trạng thái mạng: `ConnectivityManager.registerDefaultNetworkCallback` → `StateFlow<Boolean>` trong `AppContainer`.
- Compose: `LinearProgressIndicator` (không truyền progress = chạy liên tục).

## Task 2 — Chọn nhiều + Xóa trên màn Hôm nay

Chỉ màn **Hôm nay** (màn Tháng này không có danh sách giao dịch).

- **Nhấn giữ** 1 dòng → vào chế độ chọn, dòng đó được chọn.
- Trong chế độ chọn: **chạm** dòng = chọn/bỏ chọn (không mở màn sửa).
- Thanh trên cùng đổi thành thanh chọn. Nút **Back** hoặc **✕** → thoát, bỏ chọn hết.
- Nút micro ẩn khi đang chọn (tránh bấm nhầm).

```
┌──────────────────────────────────────────┐
│ ✕   3 đã chọn          Chọn tất cả   🗑  │  ← thay thanh trên cùng
├──────────────────────────────────────────┤
│ ‹  Hôm nay, 24/10                     ›  │
│ 185.000đ · 5 giao dịch                   │
├──────────────────────────────────────────┤
│ ☑ 07:10  Bánh mì              20.000đ   │  ← nền tô màu
│ ☑ 08:30  Cà phê               30.000đ   │
│ ☐ 12:05  Cơm trưa             60.000đ   │
│ ☑ 15:00  Trà sữa              45.000đ   │
│ ☐ 19:20  Gửi xe                5.000đ   │
└──────────────────────────────────────────┘
```

Bấm 🗑 → hộp xác nhận "Xóa 3 giao dịch?" [Hủy] [Xóa] → xóa → thoát chế độ chọn → snackbar:

```
┌──────────────────────────────────────────┐
│ Đã xóa 3 giao dịch            HOÀN TÁC   │
└──────────────────────────────────────────┘
```

- **Hoàn tác** khôi phục cả 3 (xóa mềm như SPEC, nên khôi phục được).
- Lỗi giữa chừng (mất mạng): giữ lại các mục chưa xóa được, báo "Đã xóa 2/3 — lỗi mạng"; Hoàn tác vẫn khôi phục 2 mục đã xóa.
- Xoay màn hình / gập máy (Fold): giữ nguyên lựa chọn.

Kỹ thuật:
- Trạng thái chọn (`Set<String>` id) đặt trong `TodayViewModel`; khi đổi ngày thì bỏ chọn.
- `TodayViewModel.deleteMany(ids)` / `restoreMany(ids)` → **unit test** với MockWebServer: xóa hết, lỗi giữa chừng, hoàn tác.
- `UiMessage.undoTransactionId: String?` → đổi thành `undoTransactionIds: List<String>` (xóa 1 mục dùng list 1 phần tử).

## Kiểm chứng trên Fold 8

1. Ghi âm từ nút micro trong app → thấy dải "Đang xử lý…" + thanh chạy → dải biến mất, giao dịch hiện ra.
2. Bật chế độ máy bay, ghi âm → dải "1 bản ghi âm chờ mạng" (không chạy) → tắt máy bay → chuyển sang "Đang xử lý…" → xong.
3. Nhấn giữ → chọn 3 mục → xóa → Hoàn tác → cả 3 quay lại; kiểm tra trên server bằng `GET /transactions`.
4. Màn ngoài (cover screen) và màn trong: thanh chọn không bị tràn chữ.

## Sau khi xong

- SPEC.md: thêm dải "Đang xử lý" vào mục Android app, thêm "nhấn giữ để chọn nhiều, xóa nhiều + Hoàn tác" vào màn Hôm nay.
- Ghi vào `docs/pending-decisions.md` là đã duyệt.
