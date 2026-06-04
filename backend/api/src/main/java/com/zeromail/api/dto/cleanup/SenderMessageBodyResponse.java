package com.zeromail.api.dto.cleanup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.cleanup.projection.SenderMessageBody;
import java.time.Instant;

public record SenderMessageBodyResponse(
        String gmailMessageId,
        String subject,
        String fromHeader,
        Instant internalDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String htmlBody,
        @JsonInclude(JsonInclude.Include.NON_NULL) String plainBody) {

    public static SenderMessageBodyResponse from(SenderMessageBody body) {
        return new SenderMessageBodyResponse(
                body.gmailMessageId(),
                body.subject(),
                body.fromHeader(),
                body.internalDate(),
                body.htmlBody(),
                body.plainBody());
    }
}
