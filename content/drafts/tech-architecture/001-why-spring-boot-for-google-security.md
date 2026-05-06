---
title: "Vì sao backend của Zero Mail dùng Spring Boot thay vì full TypeScript"
lane: tech-architecture
status: draft
platforms: LinkedIn, Facebook
cover: "../../assets/tech-001-spring-boot-security-cover.png"
---

# Vì sao backend của Zero Mail dùng Spring Boot thay vì full TypeScript

Đây là bài đầu tiên trong tuyến tech của Zero Mail.

Và có một chuyện team phải nói từ rất sớm: Zero Mail không chỉ là build xong app rồi deploy.

Đến Phase 6, team còn phải chuẩn bị cho Google verification, privacy/data-handling docs, kiểm tra lại Gmail scope, và security readiness trước khi sản phẩm có thể đi xa hơn beta.

Phần căng nhất không phải là bấm deploy.

Phần căng là nếu app dùng restricted scope như `gmail.modify`, Google có thể yêu cầu đi qua security assessment. Trong đó có những việc như rà soát cách app xử lý dữ liệu, kiểm tra endpoint, và quét code/app theo CASA trước khi được release rộng hơn.

Với Zero Mail, phần bị soi kỹ nhất sẽ nằm ở backend: OAuth flow, nơi giữ refresh token, tenant isolation, Gmail action flow, Pub/Sub webhook, worker jobs và privacy logging.

Nên ngay từ đầu, backend không thể chỉ chọn theo tiêu chí "cái gì code nhanh hơn".

Ban đầu team mình cũng nghĩ khá đơn giản: đã dùng Next.js cho frontend thì làm luôn backend bằng TypeScript cho nhanh.

Một ngôn ngữ, một ecosystem, dễ chia việc hơn.

Nhưng Zero Mail không phải một SaaS CRUD bình thường.

Sản phẩm này phải xin quyền Gmail của user, xử lý OAuth, giữ refresh token, gọi Gmail API, rồi thực hiện các action như gắn label, archive hoặc tạo draft.

Chỉ riêng việc chạm vào Gmail thật đã làm backend trở thành phần nhạy cảm nhất của hệ thống.

Với Google, scope như `gmail.modify` thuộc nhóm restricted scope. Nghĩa là team không chỉ cần làm OAuth chạy được, mà còn phải nghĩ tới verification, data handling, privacy policy, và các yêu cầu security trước khi launch thật.

Bài riêng cho phần này sẽ là: **Phase 6: Google verification và bài code scan khó nhất trước release**.

Nói cách khác, một phần khó của Zero Mail không nằm ở việc gọi Gmail API.

Phần khó là làm sao để Google, user, và chính team mình tin rằng hệ thống xử lý Gmail data đủ cẩn thận.

Backend của Zero Mail là nơi giữ những thứ dễ gây mất trust nhất:

- OAuth và refresh token
- tenant isolation
- Gmail access
- Pub/Sub webhook
- billing credits
- worker xử lý Gmail event
- privacy logging

Nếu làm ẩu, lỗi không chỉ là "API bị bug".

Nó có thể là token bị log nhầm, user này bị xử lý sang tenant khác, hoặc một email quan trọng bị automation động vào sai cách.

Vì vậy team chọn Spring Boot cho backend.

Không phải vì TypeScript backend không làm được. Làm được.

Nhưng với bài toán này, team muốn backend có security flow rõ, transaction rõ, service boundary rõ, test dễ enforce, và đủ nền để đi tới Google verification sau này.

Next.js vẫn được dùng, nhưng dùng đúng chỗ: frontend, landing page, onboarding, settings, dashboard, typed API client.

Còn phần chạm vào Gmail security, token, tenant, billing và worker jobs thì team để Spring Boot xử lý.

Các quyết định như Gradle multi-module, Spring Modulith, PostgreSQL-backed queue thay vì Kafka/RabbitMQ sẽ tách thành bài riêng. Bài này chỉ mở đầu bằng câu hỏi quan trọng nhất:

với một AI product đụng vào Gmail thật, backend nên được chọn theo tốc độ build, hay theo mức độ kiểm soát rủi ro?

Với Zero Mail, team chọn hướng thứ hai.

## Short caption

Zero Mail dùng Next.js cho frontend, nhưng backend security-critical đi qua Spring Boot.

Lý do là sản phẩm đụng vào Gmail thật của user, và từ sớm đã phải tính tới Phase 6: Google verification, privacy/data handling, restricted scopes, security assessment/code scan, refresh token, tenant isolation, billing credits và worker jobs.

Với kiểu sản phẩm này, backend không chỉ là nơi trả JSON.

Backend là nơi giữ trust.

## Image idea

Generated cover:

`content/assets/tech-001-spring-boot-security-cover.png`

Ảnh nên là một nhóm sinh viên đang nhìn vào whiteboard kiến trúc, không phải ảnh code hoặc logo framework.

Nên có cảm giác:

- startup/university workspace
- security-first backend
- architecture decision
- Gmail/OAuth chỉ biểu diễn trừu tượng, không dùng logo Google/Gmail/FPT/Spring

Prompt:

```text
Editorial article cover for a Vietnamese student startup building a security-first AI Gmail SaaS.
Scene: modern university startup workspace, a small student team reviewing a clean whiteboard architecture diagram.
Whiteboard: abstract blocks and arrows representing Frontend, API, Worker, Core, OAuth, Gmail API, Billing, PostgreSQL, Security Review. Keep text minimal and clean.
Style: polished semi-realistic editorial illustration, warm but professional, suitable for LinkedIn and Facebook.
Composition: 1200x630 landscape, team on the left, architecture/security board on the right, clear negative space for title overlay.
Mood: thoughtful, focused, pragmatic, early startup energy.
Color palette: teal, white, graphite, muted blue, small warm amber highlights.
Avoid: official Google logo, Gmail logo, FPT logo, Spring logo, trademarks, fake UI screenshots, cyberpunk hacker aesthetic, dark hoodie hacker style, dense unreadable text, watermark.
```

## Fact-check notes

- Gmail `gmail.modify` is a restricted scope.
- Google restricted-scope apps may need OAuth verification and, depending on data handling, security assessment.
- Google/CASA security assessment can involve app/API review, static source code scanning, and dynamic application scanning.
- Spring Security supports OAuth2 login and OAuth2 client flows.
- Spring Boot Actuator provides production monitoring/management features such as health and metrics.
