/**
 * Admin billing-package HTTP wire DTOs. These expose pricing, feature-credit metadata, plan
 * enablement, and payment status only; they must not expose payment-provider raw payloads or
 * customer mail content.
 */
@org.springframework.modulith.NamedInterface("admin.billing")
package com.zeromail.api.dto.admin.billing;
