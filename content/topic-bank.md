# Topic Bank

Đây là backlog ý tưởng. Mỗi lần chọn một topic, biến thành một bài riêng rồi đăng hoặc schedule.

## Startup Journey

| # | Topic | Góc viết | Trạng thái |
|---|-------|----------|------------|
| 1 | How we build our first startup for EXE202 | Bài mở series và giới thiệu context sản phẩm | Drafted |
| 2 | Chúng mình là ai trong team Zero Mail | 6 thành viên: 3 dev, 1 marketing, 2 IB | Drafted |
| 3 | Vì sao team chọn bài toán email overload | Pain point và target user | Idea |
| 4 | Vì sao Zero Mail không chỉ là một AI wrapper | Trust, automation, rủi ro Gmail | Idea |
| 5 | Scope v1 thay đổi như thế nào | Từ idea lớn đến sản phẩm nhỏ hơn nhưng an toàn hơn | Idea |
| 6 | Build startup khác làm assignment ở đâu | Mơ hồ, tradeoff, ownership | Idea |
| 7 | Điều team đánh giá thấp khi build AI SaaS | Độ phức tạp phía sau UX tưởng đơn giản | Idea |
| 8 | Quy tắc đầu tiên: không auto-send | Safety trước wow factor | Idea |
| 9 | Vì sao trust là core feature | User sẽ revoke Gmail access nếu mất niềm tin | Idea |
| 10 | Weekly build log | Tuần này build gì, kẹt gì, quyết định gì | Repeat |
| 11 | Giải thích Zero Mail cho người không technical | Product story không dùng nhiều jargon | Idea |

## Tech Architecture

| # | Topic | Góc viết | Trạng thái |
|---|-------|----------|------------|
| 1 | Vì sao backend dùng Spring Boot thay vì full TypeScript | Google/Gmail security, OAuth, restricted scopes, backend trust boundary | Drafted |
| 2 | Vì sao multi-tenant từ ngày đầu | SaaS boundary, tenant isolation, tests | Drafted |
| 3 | Vì sao dùng PostgreSQL queue thay vì Kafka | Kiến trúc phù hợp với stage hiện tại | Drafted |
| 4 | Vì sao không dùng Lombok | JDK compatibility, readability, explicit domain code | Drafted |
| 5 | Vì sao backend tách core/api/worker | Domain logic, HTTP surface, background jobs | Idea |
| 6 | Vì sao dùng Spring Modulith và ArchUnit | Enforce boundary sớm | Idea |
| 7 | Vì sao không lưu raw email body dài hạn | Privacy by architecture | Idea |
| 8 | Vì sao không log prompt/completion | AI privacy và tradeoff debugging | Idea |
| 9 | Vì sao Redis không phải queue | Cache/session/rate-limit only | Idea |
| 10 | Vì sao Gmail Pub/Sub push thay vì polling | Reliability và platform fit | Idea |
| 11 | Vì sao v1 chỉ Gmail | Focus và integration depth | Idea |
| 12 | Vì sao OpenRouter đứng sau Spring AI | Model routing và BYOK direction | Idea |
| 13 | Vì sao cấm auto-send | AI action safety | Idea |
| 14 | Vì sao billing dùng prepaid credits | Pay-as-you-go experimentation | Idea |
| 15 | Vì sao tests là một phần của architecture | Guardrails cho tenant leak và privacy regression | Idea |
| 16 | Phase 6: Google verification và bài code scan khó nhất trước release | Gmail restricted scopes, privacy docs, CASA/security assessment, SAST/DAST scan; backend OAuth, token, tenant, Pub/Sub webhook, worker jobs | Idea |

## Hook Có Thể Tái Sử Dụng

- "Một ý tưởng sản phẩm đơn giản nhanh chóng biến thành bài toán kiến trúc."
- "Phần khó nhất của AI email app không phải là AI."
- "Team mình bỏ qua một tool Java rất phổ biến vì một lý do khá boring."
- "Với startup đầu tiên, chúng mình chọn kiến trúc fail-loud."
- "Đây là quyết định team đưa ra trước khi build feature AI hào nhoáng."
- "Chúng mình build trust trước khi build scale."

## Weekly Build Log Template

```text
This week in Zero Mail:

Chúng mình đã build:
- ...

Khó hơn dự kiến:
- ...

Quyết định quan trọng:
- ...

Bài học:
- ...

Tiếp theo:
- ...
```
