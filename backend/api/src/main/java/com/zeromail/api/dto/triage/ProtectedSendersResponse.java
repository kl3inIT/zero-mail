package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.usecases.ProtectedSenderListItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = "senders")
public record ProtectedSendersResponse(List<ProtectedSenderResponse> senders) {

    public static ProtectedSendersResponse from(List<ProtectedSenderListItem> protectedSenders) {
        return new ProtectedSendersResponse(
                protectedSenders.stream().map(ProtectedSenderResponse::from).toList());
    }

    @Schema(requiredProperties = {"senderEmail", "optedIn"})
    public record ProtectedSenderResponse(String senderEmail, boolean optedIn) {

        private static ProtectedSenderResponse from(ProtectedSenderListItem protectedSender) {
            return new ProtectedSenderResponse(
                    protectedSender.senderEmail(), protectedSender.optedIn());
        }
    }
}
