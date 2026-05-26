package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.usecases.ProtectedSenderListItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = "senders")
public record ProtectedSendersResponse(List<ProtectedSenderResponse> senders) {

    public static ProtectedSendersResponse from(List<ProtectedSenderListItem> protectedSenders) {
        return new ProtectedSendersResponse(
                protectedSenders.stream().map(ProtectedSenderResponse::from).toList());
    }

    @Schema(
            requiredProperties = {
                "id",
                "pattern",
                "patternKind",
                "createdByUser",
                "createdAt",
                "senderEmail",
                "optedIn"
            })
    public record ProtectedSenderResponse(
            java.util.UUID id,
            String pattern,
            @Schema(allowableValues = {"EMAIL", "DOMAIN"}) String patternKind,
            boolean createdByUser,
            Instant createdAt,
            String senderEmail,
            boolean optedIn) {

        public static ProtectedSenderResponse from(ProtectedSenderListItem protectedSender) {
            return new ProtectedSenderResponse(
                    protectedSender.id(),
                    protectedSender.pattern(),
                    protectedSender.patternKind(),
                    protectedSender.createdByUser(),
                    protectedSender.createdAt(),
                    protectedSender.pattern(),
                    protectedSender.createdByUser());
        }
    }
}
