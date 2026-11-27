# Widget Âm lịch Việt Nam (4x2)

Widget màn hình chính: hàng đầu có nút ‹ (trái), tên tháng/năm đang xem (giữa), nút › (phải); ở giữa là lịch tháng có cả số âm; dưới đáy là thanh thông tin hôm nay (thứ, ngày dương, âm lịch, can chi năm và ngày, ngày lễ).

- Nút ‹ › xem tháng trước/sau, nút "Hôm nay" hiện ra khi đang xem tháng khác. Chạm chỗ khác không mở app.
- Ngày nghỉ lễ Việt Nam hiện màu đỏ. Mùng 1 và Rằm có số âm màu xanh lá.
- Âm lịch tính ngay trên máy (không cần mạng, đúng cho mọi năm 1901–2099).
- Ngày nghỉ lễ tải từ file `app/src/main/assets/holidays.json` trong repo của bạn, tự làm mới mỗi ngày.

## Build ra APK bằng GitHub (không cần cài gì)

1. Tạo repo mới trên github.com. **Để chế độ Public** (nếu Private thì widget không tải được ngày nghỉ mới, vẫn chạy nhưng chỉ dùng dữ liệu có sẵn).
2. Upload toàn bộ nội dung thư mục này lên repo, giữ nguyên cấu trúc thư mục (nhớ cả thư mục `.github` và file `app/release.keystore`).
   Trên máy tính: giải nén zip, kéo thả tất cả vào trang repo ("Add file" → "Upload files"), rồi Commit.
3. Vào tab **Actions**, workflow "Build APK" tự chạy (3–5 phút). Nếu chưa chạy: chọn workflow → **Run workflow**.
4. Chạy xong (dấu tick xanh), vào mục **Releases** bên phải trang repo, tải `app-release.apk` ở bản `latest`.
5. Mở APK trên điện thoại để cài. Các bản build sau cài đè lên được, không cần gỡ app.
   (Nếu máy đang cài bản cũ từ lần trước, gỡ bản cũ một lần vì khóa ký đã đổi.)

## Thêm widget lên màn hình

Nhấn giữ chỗ trống trên màn hình chính → Widget → "Âm lịch Việt Nam" → kéo ra. Kéo viền để đổi kích thước (kéo cao hơn thì ô lịch rộng hơn).

## Widget đồng hồ & thời tiết (4x2)

- Đồng hồ có giây (do ô đồng hồ của hệ thống tự nhảy mỗi giây), ngày dương, ngày âm, thời tiết hiện tại và dự báo 5 ngày.
- Thời tiết lấy từ Open-Meteo (miễn phí cho dùng cá nhân, không cần khóa API), làm mới khoảng 30 phút một lần.
- Lần đầu: mở app → bấm "1. Cấp quyền vị trí". Muốn widget tự cập nhật khi bạn đi nơi khác thì bấm tiếp "2. Cho phép luôn luôn" (hệ thống sẽ mở màn hình cài đặt quyền để bạn chọn). Không cấp bước 2 thì widget dùng vị trí lần gần nhất bạn mở app.
- Thêm widget: nhấn giữ màn hình chính → Widget → "Đồng hồ & thời tiết".
- Tên khu vực lấy từ dịch vụ của máy (cần Google Play Services). Nếu máy không có thì hiện "Vị trí hiện tại".

## Cập nhật ngày nghỉ lễ mỗi năm

Khi Chính phủ công bố lịch nghỉ năm mới (thường vào tháng 10–11 của năm trước), sửa file `app/src/main/assets/holidays.json` ngay trên GitHub:

- Thêm một mục cho năm đó, liệt kê các ngày nghỉ dạng `"yyyy-MM-dd"` (cả ngày nghỉ bù).
- `"complete": true` nghĩa là danh sách đã đủ, app chỉ dùng đúng danh sách. `false` thì app tự bổ sung thêm các ngày lễ theo quy tắc (1/1, 30/4, 1/5, 2/9, Giỗ Tổ, Tết, rơi vào T7/CN thì nghỉ bù thứ Hai).
- Commit xong, widget tự cập nhật trong vòng 24 giờ. Muốn thấy ngay thì mở app → "Cập nhật ngày nghỉ ngay".

Sửa file này không làm GitHub build lại APK.

## Build bằng Android Studio (nếu thích)

Mở thư mục này bằng Android Studio, chờ sync xong, chọn Build → Build APK(s). Build ở máy riêng thì app không biết repo nào để tải ngày nghỉ, sẽ chỉ dùng dữ liệu đi kèm.
