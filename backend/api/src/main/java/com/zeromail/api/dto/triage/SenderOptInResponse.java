package com.zeromail.api.dto.triage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"senderEmail", "optedIn"})
public record SenderOptInResponse(String senderEmail, boolean optedIn) {

    public static SenderOptInResponse from(String senderEmail, boolean optedIn) {
        return new SenderOptInResponse(senderEmail, optedIn);
    }
}
