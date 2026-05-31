package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTestMessageList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = "messages")
public record RuleTestMessagesResponse(List<Message> messages) {

    public static RuleTestMessagesResponse from(RuleTestMessageList messageList) {
        return new RuleTestMessagesResponse(
                messageList.messages().stream().map(Message::from).toList());
    }

    public RuleTestMessagesResponse {
        messages = List.copyOf(messages);
    }

    @Schema(
            requiredProperties = {
                "gmailMessageId",
                "gmailThreadId",
                "sanitizedSenderEmail",
                "sanitizedSenderDomain",
                "sanitizedSubjectExcerpt",
                "internalDate",
                "gmailLabelIds"
            })
    public record Message(
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSenderEmail,
            String sanitizedSenderDomain,
            String sanitizedSubjectExcerpt,
            Instant internalDate,
            List<String> gmailLabelIds) {

        static Message from(RuleTestMessageList.Message message) {
            return new Message(
                    message.gmailMessageId(),
                    message.gmailThreadId(),
                    message.sanitizedSenderEmail(),
                    message.sanitizedSenderDomain(),
                    message.sanitizedSubjectExcerpt(),
                    message.internalDate(),
                    message.gmailLabelIds());
        }

        public Message {
            gmailLabelIds = List.copyOf(gmailLabelIds);
        }
    }
}
