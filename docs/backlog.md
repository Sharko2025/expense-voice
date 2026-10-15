# Backlog

Việc đã biết nhưng **chưa làm**, Loi quyết định để sau. Khi lấy ra làm: viết plan / xin duyệt như thường lệ, xong thì xóa khỏi đây.

| Ngày thêm | Việc | Ghi chú |
| --- | --- | --- |
| 2026-10-15 | Nút chuyển ngôn ngữ trong app; **mặc định tiếng Việt** | Loi yêu cầu. Nút "EN" / "VI" trên thanh trên cùng cạnh ⚙, bấm là đổi ngay. Dùng per-app language của Android (`LocaleManager.applicationLocales`) nên đồng bộ với Settings → Apps → Language; notification/tile/widget đổi theo. **Mặc định tiếng Việt**: lần đầu mở app (chưa chọn gì) đặt ngôn ngữ app = `vi`, kể cả khi máy để English — thay quyết định cũ "theo ngôn ngữ máy" (2026-10-11). Giọng nói không bị ảnh hưởng (server tự nhận ngôn ngữ). Cập nhật SPEC mục "Tên và ngôn ngữ". |
| 2026-10-15 | Kiểm chứng giọng nói tiếng Anh bằng audio thật | Loi nhờ người Mỹ ghi 5 câu (danh sách trong `docs/plans/bilingual.md`), thêm vào `test_voice_openai.py`. |
| 2026-10-11 | File audio câu không có số tiền (`samples/troi-dep.m4a`) | Để test 422 bằng audio không còn bị skip. |
