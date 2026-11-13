# BTRepoLens AI

Trợ lý quản lý công việc GitHub hướng tới khả năng phân tích repository và hỗ trợ lập kế hoạch phát triển bằng ngôn ngữ tự nhiên. Dự án được thiết kế cho một lập trình viên sử dụng trong môi trường local, với kiến trúc modular monolith và GitHub là nguồn dữ liệu chính cho issue và pull request.

> **Trạng thái:** MVP local hỗ trợ quản lý repository, chat với AI, truy xuất GitHub và đề xuất tạo issue. Việc tạo issue chỉ được thực hiện sau khi người dùng xem và phê duyệt.

## Khả năng hiện tại

- Đăng ký repository sau khi xác minh quyền truy cập qua GitHub; ngăn đăng ký trùng.
- Chọn một repository active và lưu lựa chọn trong PostgreSQL.
- Xem metadata, số issue và pull request đang mở, cùng các mục cập nhật gần đây.
- Xem danh sách và chi tiết issue, pull request theo repository đã đăng ký.
- Sử dụng giao diện web để đăng ký, chọn repository và duyệt dữ liệu GitHub.
- Lưu hội thoại và nhật ký tên/kết quả công cụ; AI đề xuất issue để con người duyệt trước khi ghi.
- Cung cấp REST API có validation, xử lý lỗi tập trung và health check.

Overview dùng số liệu do GitHub Search báo cáo; kết quả có thể chưa đầy đủ hoặc chậm cập nhật. Danh sách issue loại bỏ pull request và chỉ quét tối đa 10 trang GitHub. Chi tiết tại [API specification](docs/04-api-specification.md).

## Công nghệ

| Thành phần | Công nghệ | Trạng thái |
|---|---|---|
| Backend | Java 25, Spring Boot 4.1.2, Spring Data JPA | Đã triển khai |
| Database | PostgreSQL 17, Flyway | Đã cấu hình |
| Tích hợp | GitHub REST API, Spring `RestClient` | Đọc; tạo issue sau phê duyệt |
| AI | Spring AI 2.0.1, OpenAI | Cấu hình tùy chọn |
| Frontend | React, TypeScript, Vite, React Router, TanStack Query | Đã triển khai giao diện cơ bản |

## Cấu trúc repository

```text
btrepolens-ai/
├── backend/                 # Spring Boot, Maven Wrapper, migration và tests
├── frontend/                # React SPA, API client và frontend tests
├── docs/                    # Yêu cầu, kiến trúc, database, API và hướng dẫn
├── docker-compose.yml       # PostgreSQL cho phát triển local
├── .env.example             # Mẫu cấu hình, không chứa credential thật
└── README.md
```

## Chạy dự án local

### 1. Chuẩn bị

- JDK 25 và Docker Desktop có Docker Compose.
- Node.js 24 LTS và npm để chạy frontend.
- GitHub fine-grained token nếu cần truy cập private repository hoặc giới hạn rate cao hơn. Cấp quyền đọc Metadata, Issues và Pull requests cho các repository cần dùng. Nếu muốn thực thi tạo issue sau phê duyệt, cấp thêm Issues: write. Public repository có thể đọc không cần token, với giới hạn GitHub áp dụng cho truy cập ẩn danh.
- Maven Wrapper đã có trong `backend/`; không bắt buộc cài Maven riêng.

### 2. Cấu hình và chạy PostgreSQL

Tại thư mục gốc, dùng PowerShell:

```powershell
Copy-Item .env.example .env
# Sửa .env: đặt POSTGRES_PASSWORD và GITHUB_TOKEN nếu sử dụng token.
docker compose up -d
docker compose ps
```

PostgreSQL mặc định lắng nghe tại `127.0.0.1:15432`; dữ liệu được lưu trong Docker named volume. Không commit `.env`. Nếu thay đổi mật khẩu sau khi volume đã được khởi tạo, cần cập nhật credential trong PostgreSQL tương ứng; sửa `.env` không tự đổi mật khẩu trong volume cũ. Nếu đang dùng `.env` cũ, cập nhật `DB_URL` sang cổng `15432`.

### 3. Chạy backend

Docker Compose đọc `.env`, nhưng Spring Boot **không tự nạp** file này. Trên Windows, chạy script từ thư mục gốc để nạp cấu hình và khởi động backend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
# Nếu cổng 8080 đã được sử dụng:
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1 -Port 8081
```

Script chỉ nạp các biến backend từ `.env` vào tiến trình hiện tại và không in credential. Không chạy đồng thời cả hai lệnh trên. Bạn cũng có thể tự khai báo biến trong cùng terminal, dùng giá trị khớp với `.env`:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:15432/btrepolens'
$env:DB_USERNAME = 'btrepolens'
$env:DB_PASSWORD = '<POSTGRES_PASSWORD trong .env>'
$env:GITHUB_TOKEN = '<token GitHub, hoặc để trống cho public repository>'

Set-Location backend
.\mvnw.cmd spring-boot:run
```

Backend mặc định chạy tại `http://127.0.0.1:8080`. Nếu cổng đã được sử dụng, đặt `$env:SERVER_PORT = '8081'` trước khi chạy. Không cần AI API key cho chức năng xem repository. Để bật assistant, thiết lập các biến sau trước khi chạy backend:

```powershell
$env:AI_CHAT_MODEL = 'openai'
$env:AI_API_KEY = '<OpenAI API key>'
$env:AI_MODEL = '<model hỗ trợ tool calling>'
```

Lựa chọn model và các lần gọi API có thể phát sinh chi phí. Tin nhắn và dữ liệu GitHub mà agent đọc được gửi tới nhà cung cấp model; chỉ dùng repository và dữ liệu bạn được phép chia sẻ. Trên macOS/Linux, dùng `./mvnw` và thiết lập biến môi trường theo shell đang sử dụng.

#### Dùng Gemini thay cho OpenAI

Tạo API key tại [Google AI Studio](https://aistudio.google.com/app/apikey), sau đó thêm các dòng sau vào `.env` ở thư mục gốc:

```dotenv
SPRING_PROFILES_ACTIVE=gemini
GEMINI_API_KEY=<Gemini API key>
GEMINI_MODEL=gemini-3.5-flash-lite
```

Khởi động lại backend bằng `scripts/run-backend.ps1` như hướng dẫn ở trên. Profile `gemini` dùng Gemini qua API tương thích OpenAI của Google; các biến OpenAI cũ được giữ nguyên. Muốn quay lại OpenAI, bỏ `gemini` khỏi `SPRING_PROFILES_ACTIVE` rồi khởi động lại. Khi chạy backend không qua script, xuất ba biến môi trường trên trong terminal trước khi chạy Maven.

Hạn mức miễn phí tùy tài khoản và model. Khi dùng Gemini, nội dung hội thoại cùng dữ liệu GitHub mà agent đọc được sẽ gửi đến Google; [chính sách của Google cho free tier](https://ai.google.dev/gemini-api/docs/pricing) cho biết nội dung có thể được dùng để cải thiện sản phẩm. Không đưa key vào chat, source code hay Git.

### 4. Kiểm tra

Trong terminal khác:

```powershell
curl.exe http://127.0.0.1:8080/api/v1/health
curl.exe http://127.0.0.1:8080/actuator/health/readiness
curl.exe http://127.0.0.1:8080/api/v1/repositories
```

`/api/v1/health` kiểm tra liveness; readiness còn kiểm tra database. Từ `backend/`, chạy test và tạo package:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Để dừng database mà giữ dữ liệu, chạy `docker compose down` tại thư mục gốc. Xem [development guide](docs/05-development-guide.md) để xử lý lỗi kết nối, cổng và credential.

### 5. Chạy frontend

Trong một terminal khác, từ thư mục gốc:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Mở `http://127.0.0.1:5173`. Vite proxy `/api` tới backend tại `http://127.0.0.1:8080`; nếu backend dùng cổng khác, đặt `$env:BACKEND_URL = 'http://127.0.0.1:8081'` trước khi chạy frontend. Chạy `npm test`, `npm run lint` và `npm run build` trong `frontend/` để kiểm tra. Giao diện hiện hiển thị tiếng Anh và lấy dữ liệu thật từ backend.

## REST API

Base URL: `http://127.0.0.1:8080/api/v1`.

| Method | Endpoint | Chức năng |
|---|---|---|
| `GET` | `/health` | Liveness |
| `GET` | `/repositories` | Danh sách repository đã đăng ký |
| `POST` | `/repositories` | Xác minh và đăng ký repository |
| `PUT` | `/repositories/{id}/activate` | Chọn repository active |
| `GET` | `/repositories/{id}/overview` | Metadata, thống kê và mục gần đây |
| `GET` | `/repositories/{id}/issues` | Danh sách issue |
| `GET` | `/repositories/{id}/issues/{number}` | Chi tiết issue |
| `GET` | `/repositories/{id}/pull-requests` | Danh sách pull request |
| `GET` | `/repositories/{id}/pull-requests/{number}` | Chi tiết pull request |

Ví dụ đăng ký repository trong PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/v1/repositories' `
  -ContentType 'application/json' `
  -Body '{"owner":"octocat","name":"Hello-World"}'
```

Các endpoint đọc dùng ID của repository đã đăng ký; repository được chỉ định không bắt buộc đang active. Tham số và response được mô tả trong [API specification](docs/04-api-specification.md).

Assistant chỉ làm việc với repository đang active. Mở tab **Assistant**, tạo cuộc hội thoại và gửi yêu cầu. Một đề xuất xuất hiện ở mục **Issue proposals**; xem toàn bộ nội dung, tiêu chí và labels trước khi chọn **Approve and create** hoặc **Reject**. Approval được ghi nhận trước khi gửi GitHub. Nếu mất kết nối hoặc tiến trình dừng trong khi gửi, trạng thái `UNKNOWN` hoặc `EXECUTING` có nghĩa là chưa xác định được kết quả: kiểm tra GitHub thủ công; hệ thống không tự thử lại proposal đó.

## Quy trình phát triển cho một người

Giữ `main` ở trạng thái có thể build; tạo **nhánh ngắn hạn theo từng tính năng hoặc bản sửa lỗi**, không duy trì một nhánh cá nhân lâu dài. Với thay đổi nhỏ, có thể commit trực tiếp lên `main` nếu không cần PR; với tính năng mới, thay đổi có rủi ro hoặc cần lưu lịch sử quyết định, dùng branch và PR để tự review.

1. Chia công việc thành issue nhỏ có kết quả và tiêu chí hoàn thành rõ ràng. Có thể dùng GitHub Projects với `Backlog`, `In progress`, `Done`; chỉ cần một việc chính đang thực hiện tại một thời điểm.
2. Cập nhật `main` rồi tạo nhánh, ví dụ `feat/repository-dashboard` hoặc `fix/github-timeout`:

   ```bash
   git switch main
   git pull --ff-only origin main
   git switch -c feat/repository-dashboard
   ```

3. Commit theo từng thay đổi có ý nghĩa, chạy test phù hợp, rồi push branch và mở PR. Tự xem diff, kết quả test và liên kết issue trước khi merge. Dùng merge commit hoặc squash merge tùy mức độ cần giữ lịch sử; nếu squash, đặt tên commit thể hiện kết quả cuối cùng.
4. Sau khi xác nhận PR đã merge, xóa branch trên GitHub. Cập nhật `main` local rồi xóa branch local:

   ```bash
   git switch main
   git pull --ff-only origin main
   git branch -d feat/repository-dashboard
   ```

   Với **squash merge**, Git có thể không nhận branch gốc là đã merge vì commit ID thay đổi. Sau khi đã xác nhận PR được merge và không còn thay đổi cần giữ, dùng `git branch -D feat/repository-dashboard`. Bắt đầu tính năng tiếp theo bằng một branch mới từ `main` cập nhật. Chỉ rebase branch đang làm nếu cần cập nhật với `main`; tránh viết lại lịch sử đã chia sẻ mà người khác đang sử dụng.

## Tài liệu

- [Yêu cầu và phạm vi](docs/01-requirements.md)
- [Kiến trúc backend](docs/02-architecture.md)
- [Thiết kế database](docs/03-database-design.md)
- [Đặc tả API](docs/04-api-specification.md)
- [Hướng dẫn phát triển](docs/05-development-guide.md)
- [Kiến trúc frontend](docs/06-frontend-architecture.md)

## Bảo mật và giấy phép

Ứng dụng hiện phục vụ môi trường local tin cậy và chưa có authentication. Triển khai từ xa cần bổ sung xác thực và các biện pháp bảo vệ phù hợp. Không đưa GitHub token, mật khẩu hoặc AI key vào source code, log hay frontend. Nội dung GitHub là dữ liệu không tin cậy đối với các tính năng AI trong tương lai.

Giấy phép: xem [LICENSE](LICENSE).
