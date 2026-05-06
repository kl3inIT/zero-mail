---
title: "Why Zero Mail is multi-tenant from day one"
lane: tech-architecture
status: draft
platforms: LinkedIn, Facebook, Threads
---

# Why Zero Mail Is Multi-Tenant From Day One

Với một project startup sinh viên, cách dễ nhất là build cho một user trước rồi "sau này thêm multi-tenant".

Team mình quyết định không làm vậy.

Zero Mail là SaaS. Điều đó có nghĩa sản phẩm không chỉ là "một user connect Gmail". Nó là nhiều user, mỗi người có Gmail account riêng, rule riêng, billing credits riêng, inbox events riêng, và automation settings riêng.

Nếu tenant isolation được thêm sau, nó sẽ trở thành một trong những refactor rủi ro nhất của toàn hệ thống.

Failure mode rất nghiêm trọng:

- Gmail event của user này bị xử lý dưới tenant của user khác
- billing credits bị charge sai
- settings của user này ảnh hưởng automation của user khác
- logs hoặc error payload vô tình leak metadata riêng tư

Với một AI email product, đây không phải bug nhỏ. Đây là trust-breaking bug.

Vì vậy team xem multi-tenancy là foundation, không phải feature để thêm sau.

Ở backend, điều này nghĩa là tenant context phải đi cùng request và cả background worker. Persistence phải tenant-aware. Test phải có case bắt tenant leak. Các job xử lý email cũng cần tenant binding, vì không phải việc gì cũng chạy trong HTTP request.

Điều này làm project phức tạp hơn ngay từ đầu.

Nhưng nó tránh một vấn đề lớn hơn: build một sản phẩm demo được, nhưng không đủ an toàn khi có user thật.

Bài học của team:

nếu business model là SaaS, tenant boundary không phải implementation detail. Nó là một phần của safety model.

Câu hỏi: bạn sẽ thêm multi-tenancy vào SaaS ở thời điểm nào: day one, user trả tiền đầu tiên, hay sau khi product ổn hơn?
