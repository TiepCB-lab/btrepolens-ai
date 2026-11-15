# Trạng thái dự án

Cập nhật: 15/11/2026. Phạm vi hiện tại là MVP sử dụng local cho một lập trình viên; chưa coi đây là sản phẩm sẵn sàng triển khai công khai.

## Đã triển khai

- Kết nối repository GitHub bằng URL trên giao diện, kiểm tra quyền truy cập, chọn repository active và xem issue, pull request cùng overview.
- Assistant dùng OpenAI hoặc Gemini qua cấu hình backend; có lịch sử hội thoại, tool đọc dữ liệu theo repository active và nhật ký tên/kết quả tool.
- Assistant đề xuất issue; chỉ tạo issue trên GitHub sau khi người dùng phê duyệt. Kết quả thực thi không chắc chắn cần được kiểm tra thủ công trên GitHub.
- Tool đọc tối đa 30 file thay đổi trong PR, gồm metadata và số dòng thêm/xóa; trả dấu hiệu khi danh sách chưa đầy đủ. Tool không đọc diff hoặc nội dung file.
- GitHub client xử lý phản hồi rỗng bằng lỗi upstream có kiểm soát. JaCoCo tạo báo cáo coverage backend; workflow CI được cấu hình cho backend, PostgreSQL test và frontend.

## Kiểm chứng gần nhất

- Ngày 15/11/2026: 36 test backend đạt, gồm test tích hợp với PostgreSQL tạm; không có test bị bỏ qua. Tool file PR được kiểm thử với GitHub giả lập, chưa được xác nhận qua một cuộc hội thoại Gemini và PR thật.
- Ngày 13/11/2026: frontend đạt lint, 15 test và build. Kết quả này không thay thế kiểm thử trình duyệt cho các thay đổi sau ngày đó.
- SonarQube local đã phân tích backend và Quality Gate đạt; còn 5 cảnh báo SQL cần rà soát. Lượt quét chưa bao gồm frontend. Báo cáo và tỷ lệ coverage có thể thay đổi theo phiên bản mã và bộ test đã chạy.
- Workflow GitHub Actions đã có trong repository nhưng chưa được xác minh bằng một lượt chạy trên GitHub.

## Việc tiếp theo

1. Xác minh trên PR thật rằng Gemini gọi đúng tool, mô tả giới hạn và không khẳng định đã review nội dung code.
2. Thiết kế khả năng đọc diff có giới hạn, chú ý dữ liệu repository được gửi tới nhà cung cấp AI và nội dung GitHub là dữ liệu không đáng tin cậy.
3. Rà soát 5 cảnh báo SQL và bổ sung phân tích frontend vào SonarQube; không mặc định coi cảnh báo là lỗi hay false positive.
4. Chạy CI trên GitHub sau khi nhánh được đẩy lên; đánh giá thêm kiểm thử end-to-end và bảo vệ truy cập trước khi cho phép sử dụng từ xa.
