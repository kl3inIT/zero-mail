# EXE202 Startup Content

Thư mục này dùng để lưu draft content public cho hành trình build Zero Mail trong khuôn khổ môn EXE202.

Framing chính:

> How we build our first startup for EXE202.

Zero Mail không nên được kể như một công ty đã hoàn thiện. Nên kể như hành trình một team sinh viên lần đầu build startup: chọn bài toán, validate, ra quyết định kiến trúc, gặp lỗi, đổi hướng, và học cách biến một ý tưởng thành SaaS thật.

## Hai Tuyến Nội Dung

### 1. Startup Journey

Mục tiêu: giúp người đọc hiểu câu chuyện và quá trình của team.

Dùng tuyến này cho:

- vì sao chọn bài toán email overload
- team EXE202 scope sản phẩm v1 như thế nào
- weekly build log
- bài học validation
- sai lầm, pivot, quyết định khó
- build startup khác làm assignment bình thường ở đâu

Giọng viết: thật, gần gũi, dùng "chúng mình" thay vì chỉ nói "tôi".

### 2. Tech Architecture

Mục tiêu: biến các quyết định kỹ thuật thành insight có ích.

Dùng tuyến này cho:

- vì sao backend dùng Spring Boot thay vì full TypeScript
- vì sao không dùng Lombok
- vì sao multi-tenant từ ngày đầu
- vì sao chọn Java 25 và Spring Boot 4
- vì sao dùng PostgreSQL-backed queue thay vì Kafka hoặc RabbitMQ
- vì sao không lưu raw email body dài hạn
- vì sao không lưu prompt/completion
- vì sao v1 không cho auto-send
- vì sao backend tách `core`, `api`, `worker`
- vì sao privacy và trust ảnh hưởng trực tiếp đến kiến trúc

Giọng viết: thực tế, giải thích tradeoff, không biến thành tutorial thuần kỹ thuật.

## Thứ Tự Đăng Gợi Ý

1. `startup-journey/001-how-we-build-our-first-startup.md`
2. `startup-journey/002-who-we-are-as-a-team.md`
3. `tech-architecture/001-why-spring-boot-for-google-security.md`
4. `tech-architecture/002-why-multi-tenant-from-day-one.md`
5. `tech-architecture/003-why-postgres-queue-not-kafka.md`
6. `tech-architecture/004-why-we-do-not-use-lombok.md`
7. Weekly update: tuần này build gì, kẹt gì, quyết định gì

## Quy Tắc Draft

- Trước khi viết bài mới, đọc `content/context/group-and-exe202-overview.md` để thống nhất context về team, EXE202, target beta, và safety boundaries.
- Một bài chỉ nên xoay quanh một ý.
- Mở bài bằng vấn đề, không mở bằng công nghệ.
- Dùng "chúng mình" vì đây là hành trình team build startup cho EXE202.
- Viết như một người trong team đang kể lại decision thật; tránh giọng tổng kết quá sạch, quá giống AI, hoặc quá nhiều câu "bài học là".
- Không nói sản phẩm đã launch nếu thực tế chưa launch.
- Không đưa email thật, token, prompt, private key, dữ liệu người dùng, hoặc nội dung nhạy cảm vào bài viết.
- Mỗi bài nên có một bài học rõ ràng để người ngoài project vẫn đọc được.

## Cấu Trúc Thư Mục

- `drafts/startup-journey/` - bài về hành trình EXE202, founder/product/team
- `drafts/tech-architecture/` - bài về quyết định kỹ thuật và kiến trúc
- `context/` - context chuẩn về nhóm, môn EXE202, project assumptions, source notes
- `templates/` - template để viết nhanh
- `topic-bank.md` - backlog ý tưởng bài viết
