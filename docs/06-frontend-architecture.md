# Kiến trúc frontend

Trạng thái: **quyết định cho giai đoạn frontend; chưa triển khai**. Thư mục `frontend/` hiện chưa có ứng dụng. Phạm vi đầu tiên là giao diện cho API backend Phase 1; chat, agent và phê duyệt là các chức năng về sau.

## Công nghệ đã chọn

| Mục đích | Lựa chọn | Lý do |
|---|---|---|
| Giao diện | React + TypeScript, bật strict mode | Component rõ ràng, hợp đồng dữ liệu có kiểu và thống nhất định hướng dự án. |
| Build và dev server | Vite, ứng dụng SPA | Phù hợp ứng dụng nội bộ chạy local; backend Spring Boot đã chịu trách nhiệm API, chưa cần SSR hay SEO. |
| Điều hướng | React Router, declarative mode | Điều hướng các màn hình repository và trang chi tiết. |
| Server state | TanStack Query | Quản lý tải dữ liệu, cache, trạng thái lỗi và làm mới sau mutation. |
| HTTP | `fetch` của trình duyệt, bọc trong một API client nhỏ | Đủ cho API REST hiện tại; xử lý envelope và lỗi tập trung. |
| UI và CSS | CSS Modules, CSS variables, HTML semantic | CSS có phạm vi theo component; dùng token chung và component tái sử dụng khi có nhu cầu thực tế. |
| State giao diện | React state và Context khi thật sự cần chia sẻ | Không thêm Redux hoặc store toàn cục cho Phase 1. |
| Chất lượng | ESLint, Prettier, TypeScript | Kiểm tra kiểu, quy tắc và định dạng thống nhất. |
| Kiểm thử | Vitest + React Testing Library; Playwright cho luồng end-to-end cần thiết | Unit/component test tập trung vào hành vi; kiểm tra luồng tích hợp bằng trình duyệt. |
| Package manager | npm và `package-lock.json` | Cài đặt nhất quán, không cần thêm công cụ quản lý package. |

Khi khởi tạo, chọn các phiên bản ổn định tương thích tại thời điểm đó, ghi rõ yêu cầu Node.js LTS trong `package.json` và commit lockfile. Tài liệu này chưa xác nhận phiên bản package cụ thể vì chưa cài đặt hoặc kiểm thử frontend. React khuyến nghị cân nhắc framework cho dự án mới; SPA dùng Vite ở đây là quyết định có chủ đích cho ứng dụng local có API riêng, và cần đánh giá lại nếu xuất hiện nhu cầu SSR/SEO. Tham khảo [React](https://react.dev/learn/build-a-react-app-from-scratch), [React Router](https://reactrouter.com/start/declarative/installation) và [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview).

## Cấu trúc dự kiến

Chỉ tạo thư mục và file khi triển khai chức năng tương ứng; cây dưới đây là quy ước, không phải các file đã tồn tại.

```text
frontend/
├── public/
├── src/
│   ├── app/                    # Khởi tạo router, QueryClient, layout chung
│   │   ├── App.tsx
│   │   ├── providers.tsx
│   │   └── router.tsx
│   ├── features/
│   │   ├── repositories/       # Đăng ký, danh sách, chọn repository, overview
│   │   ├── issues/              # Danh sách và chi tiết issue
│   │   └── pull-requests/       # Danh sách và chi tiết pull request
│   │       # Mỗi feature sở hữu components, hooks, api, types khi cần
│   ├── shared/
│   │   ├── api/                 # fetch client, envelope và lỗi API
│   │   ├── components/          # Component dùng ở nhiều feature
│   │   ├── styles/              # CSS variables, reset và kiểu toàn cục
│   │   └── utils/               # Hàm dùng chung thực sự cần thiết
│   ├── test/                    # Thiết lập test chung
│   └── main.tsx
├── index.html
├── package.json
├── package-lock.json
├── tsconfig.json
├── vite.config.ts
└── eslint.config.js
```

Trong mỗi feature, đặt API calls và kiểu response gần nơi sử dụng; component trình bày không gọi GitHub trực tiếp. Không tạo sẵn package `chat`, `agent`, `approval` hoặc abstraction rỗng. Khi các chức năng tương ứng được triển khai, bổ sung feature riêng theo cùng quy ước.

## Điều hướng và dữ liệu

| Đường dẫn dự kiến | Chức năng | API backend |
|---|---|---|
| `/repositories` | Danh sách và đăng ký | `GET/POST /api/v1/repositories` |
| `/repositories/:id` | Overview, chọn active | `GET .../:id/overview`, `PUT .../:id/activate` |
| `/repositories/:id/issues` | Danh sách issue, lọc state | `GET .../:id/issues` |
| `/repositories/:id/issues/:number` | Chi tiết issue | `GET .../:id/issues/:number` |
| `/repositories/:id/pull-requests` | Danh sách PR, lọc state | `GET .../:id/pull-requests` |
| `/repositories/:id/pull-requests/:number` | Chi tiết PR | `GET .../:id/pull-requests/:number` |

`/` điều hướng tới `/repositories`. Repository active do backend quản lý và là nguồn xác thực; ID trên URL xác định repository của màn hình đang xem. API hiện tại cho phép đọc bất kỳ repository đã đăng ký theo ID, kể cả khi chưa active. Sau khi đăng ký hoặc kích hoạt, invalidate query danh sách repository; query dữ liệu GitHub có ID repository, state và limit trong query key để tránh lẫn dữ liệu giữa repository. Thể hiện loading, empty và error states; không biến lỗi thành danh sách rỗng. Thể hiện trạng thái `incomplete` của GitHub Search và không suy ra tổng từ số phần tử đã tải. Giới hạn issue scan theo mô tả API.

## Kết nối và bảo mật

Dev server Vite bind `127.0.0.1`, dùng proxy cùng origin cho `/api` và `/actuator` tới `http://127.0.0.1:8080`, cấu hình được đích proxy khi backend đổi cổng. Client gọi đường dẫn tương đối `/api/v1/...`; không cần bật CORS backend cho luồng local qua proxy. Build tĩnh về sau cần được phục vụ cùng origin hoặc có reverse proxy; SPA hosting cần fallback về `index.html` cho deep link. Các thiết lập này sẽ được tạo và kiểm tra khi triển khai frontend.

Token GitHub, mật khẩu database và AI key chỉ ở backend; không đưa vào biến `VITE_*`, bundle, localStorage hoặc log trình duyệt. Coi nội dung issue/PR là dữ liệu không tin cậy: render như text, không dùng `dangerouslySetInnerHTML`; nếu bổ sung Markdown sau này phải xử lý an toàn trước khi render. Dùng HTML semantic, điều hướng bằng bàn phím, trạng thái focus và nhãn truy cập được. Remote deployment cần authentication và hardening bổ sung ở backend và tầng phục vụ frontend.

## Trình tự triển khai đề xuất

1. Khởi tạo Vite React TypeScript trong `frontend/`, cấu hình proxy, routing, lint và test.
2. Xây dựng API client và màn hình repository: danh sách, đăng ký, activation, overview.
3. Thêm danh sách và chi tiết issue/PR, xử lý trạng thái và bộ lọc.
4. Kiểm tra component và luồng local với backend; chỉ sau đó mở rộng giao diện cho agent và approval theo API thực tế của các phase sau.
