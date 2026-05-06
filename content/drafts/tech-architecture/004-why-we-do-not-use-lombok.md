---
title: "Why we do not use Lombok in Zero Mail"
lane: tech-architecture
status: draft
platforms: LinkedIn, Facebook, Threads
---

# Why We Do Not Use Lombok In Zero Mail

Một quyết định backend khá sớm trong Zero Mail là:

team không dùng Lombok.

Điều này có thể hơi lạ, vì Lombok rất phổ biến trong Java. Nó giúp giảm boilerplate, generate getter, constructor, builder, và làm entity class ngắn hơn nhiều.

Nhưng Zero Mail là một AI Gmail SaaS, nơi trust, privacy, và maintainability quan trọng hơn việc file ngắn hơn vài chục dòng.

Team chọn explicit code vì vài lý do.

Thứ nhất, Java hiện đại đã có record. Với DTO, API request/response, hoặc value object immutable, record đủ gọn và rõ.

Thứ hai, entity không chỉ là data bag. Hibernate entity cần cẩn thận với constructor, proxy, equality, tenant ownership, audit fields, và lifecycle behavior. Team muốn nhìn thấy các quyết định này trực tiếp trong code.

Thứ ba, đây là project học thật. Nếu mọi thứ bị ẩn sau annotation/code generation, thành viên mới sẽ khó hiểu backend đang làm gì.

Thứ tư, codebase của team có convention rõ: tên biến, field, method phải domain-revealing. Ngắn hơn không đồng nghĩa với dễ hiểu hơn, đặc biệt trong hệ thống xử lý Gmail access, billing credits, tenant isolation, và AI actions.

Vì vậy rule hiện tại là:

- dùng record cho DTO và value object
- dùng class cho JPA entity
- viết explicit constructor/method khi cần
- không dùng Lombok

Tradeoff là code dài hơn.

Lợi ích là người đọc tương lai không phải đoán code generation đã làm gì.

Với startup đầu tiên của team, đó là một tradeoff hợp lý.

Câu hỏi: nếu bạn build một SaaS backend dài hạn, bạn sẽ ưu tiên ít boilerplate hơn hay explicit code hơn?
