---
title: "How we build our first startup for EXE202"
lane: startup-journey
status: draft
platforms: LinkedIn, Facebook
---

# How We Build Our First Startup For EXE202

Chúng mình là một team 6 người tại Đại học FPT, đang build startup đầu tiên trong khuôn khổ EXE202 / Trải nghiệm khởi nghiệp 2.

Team gồm 3 developers, 1 marketing member, và 2 IB members. Dev phụ trách product engineering, Gmail integration, AI workflow, frontend/backend và deployment. Marketing phụ trách beta recruitment, content, communication channel. IB phụ trách PM/BA, business testing, user feedback, startup validation, documentation và stakeholder communication.

Tên sản phẩm hiện tại là Zero Mail: một AI Gmail assistant giúp người dùng tiến gần hơn tới inbox zero bằng cách tự động triage email, gắn label, archive, và tạo draft reply.

Ban đầu idea nghe khá đơn giản:

kết nối Gmail, để AI đọc email mới, rồi tự động xử lý inbox.

Nhưng càng đi sâu, team càng nhận ra bài toán này không chỉ là "AI có thông minh không".

Bài toán thật sự là trust.

Nếu AI gửi nhầm email, archive nhầm một email quan trọng, leak nội dung riêng tư, hoặc lưu dữ liệu Gmail thiếu cẩn thận, user sẽ không cho mình cơ hội thứ hai. Họ chỉ cần revoke quyền Gmail là xong.

Vì vậy v1 của Zero Mail có một số ràng buộc khá cứng:

- không auto-send email
- không lưu raw email body dài hạn
- không lưu LLM prompt/completion
- v1 chỉ tập trung Gmail
- chỉ cho phép các write action an toàn hơn: label, archive, save draft
- thiết kế multi-tenant ngay từ đầu vì đây là SaaS
- privacy và safety ảnh hưởng trực tiếp tới architecture

Về tech stack, team đang build bằng Java 25, Spring Boot 4, Spring AI, PostgreSQL, Redis và Next.js.

Nhưng series này sẽ không chỉ nói về tech stack.

Nó sẽ là hành trình team sinh viên build startup từ con số 0: chọn scope, ra quyết định kiến trúc, tuyển beta users, nhận feedback thật, gặp lỗi, sửa hướng, và học xem build một SaaS thật khác gì so với làm một assignment bình thường.

Mục tiêu của team không chỉ là có một demo chạy được. Chúng mình muốn kiểm chứng xem một AI Gmail assistant có thể trở thành một SaaS mà user đủ tin để dùng với inbox thật hay không.

Đây sẽ là build log của Zero Mail.

Câu hỏi: nếu bạn build một AI product có quyền chạm vào Gmail thật của user, safety rule đầu tiên của bạn sẽ là gì?

## Short Version

Chúng mình là team 6 người tại Đại học FPT, đang build startup đầu tiên cho EXE202 / Trải nghiệm khởi nghiệp 2.

Zero Mail là một AI Gmail assistant giúp người dùng tiến gần hơn tới inbox zero.

Bài học bất ngờ nhất đến hiện tại: phần khó nhất không phải là làm AI phân loại email. Phần khó nhất là khiến user đủ tin để cho sản phẩm chạm vào inbox thật của họ.

Vì vậy v1 không auto-send, không lưu raw email body dài hạn, và chỉ cho phép các action an toàn hơn như label, archive, save draft.

Series này sẽ ghi lại hành trình build: product decisions, architecture tradeoffs, mistakes, và những bài học khi team lần đầu build một SaaS từ số 0.
