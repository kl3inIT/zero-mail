/**
 * Admin queue-health HTTP wire DTOs. AdminPathBodyBanTest gates this package against any field name
 * matching the forbidden body/payload/prompt/completion/content regex — the DTO contract is the
 * load-bearing privacy gate (SPEC OPS-QUEUE-02 + T-08-45).
 */
@org.springframework.modulith.NamedInterface("admin.queue")
package com.zeromail.api.dto.admin.queue;
