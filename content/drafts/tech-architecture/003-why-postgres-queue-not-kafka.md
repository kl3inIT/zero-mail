---
title: "Why we chose a PostgreSQL-backed queue instead of Kafka"
lane: tech-architecture
status: draft
platforms: LinkedIn, Facebook, Threads
---

# Why We Chose A PostgreSQL-Backed Queue Instead Of Kafka

Zero Mail cần background processing.

Gmail gửi push notification. Hệ thống cần xử lý message history, apply rule, reserve billing credits, gọi LLM gateway, và sau này thực hiện các Gmail action an toàn như label, archive, save draft.

Một câu trả lời kiến trúc rất dễ xuất hiện là:

"Dùng Kafka đi."

Nhưng ở stage hiện tại, team chọn PostgreSQL-backed queue.

Không phải vì Kafka không tốt. Kafka rất mạnh khi cần high-throughput event streaming, nhiều consumer tách biệt, replay workflow phức tạp, và một team đủ kinh nghiệm để vận hành nó.

Nhưng Zero Mail v1 đang theo hướng self-hosted SaaS trên một VPS. PostgreSQL đã là primary datastore. Điều team cần hơn là reliability, transactional consistency, và operational simplicity.

Với Postgres-backed queue, team có thể giữ outbox, processing jobs, billing reservations, và tenant data gần cùng một transactional boundary.

Điều này quan trọng vì email automation khá nhạy cảm:

- không xử lý trùng cùng một Gmail event
- không double-charge credits
- không chạy action khi thiếu tenant context
- không làm mất job âm thầm
- không thêm infrastructure mà team chưa tự tin vận hành

Kafka hoặc RabbitMQ có thể hợp lý ở giai đoạn sau.

Nhưng với v1, lựa chọn boring lại là lựa chọn mạnh hơn.

Bài học của team:

architecture nên khớp với stage của sản phẩm, không phải với độ "xịn" của tool.

Câu hỏi: có tool nào bạn sẽ tránh dùng ở v1 dù nó rất phổ biến không?
