package com.zeromail.api.dto.waitlist;

import com.zeromail.core.waitlist.domain.WaitlistSubscribeResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for {@code POST /api/waitlist/subscribe}. Always HTTP 200 — the {@code status} field
 * distinguishes outcomes (account-enumeration protection: server does not return 409 for
 * already-registered emails).
 */
@Schema(requiredProperties = "status")
public record WaitlistSubscribeResponse(
        @Schema(allowableValues = {"ADDED", "ALREADY_REGISTERED", "ALREADY_USER"}) String status) {

    public static WaitlistSubscribeResponse from(WaitlistSubscribeResult result) {
        return new WaitlistSubscribeResponse(result.name());
    }
}
