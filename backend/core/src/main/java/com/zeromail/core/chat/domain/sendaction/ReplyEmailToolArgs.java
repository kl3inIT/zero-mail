package com.zeromail.core.chat.domain.sendaction;

import org.springframework.ai.tool.annotation.ToolParam;

@SuppressWarnings("unused")
public record ReplyEmailToolArgs(
        String sourceMessageId,
        @ToolParam(
                        description =
                                "Recipient email address in the form name@example.com (normally"
                                        + " the original sender). Must be a real email address, never"
                                        + " a person's display name or nickname. Derive it from the"
                                        + " replied-to message via getMessage, or ask the user -- do"
                                        + " not put a name here.")
                String to,
        @ToolParam(
                        description =
                                "Optional CC recipients as comma-separated email addresses (e.g."
                                        + " a@x.com, b@y.com). Real email addresses only, never display"
                                        + " names.")
                String cc,
        String subject,
        String gmailThreadId,
        String body) {

    public ReplyEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        cc = optionalText(cc);
        // Blank-tolerant: Gmail renders an empty subject as "(no subject)". The model
        // normally derives a "Re:" subject from the thread, but an empty value must send.
        subject = blankToEmpty(subject);
        gmailThreadId = optionalText(gmailThreadId);
        body = requireText(body, "body");
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static String optionalText(String text) {
        if (text == null) {
            return null;
        }
        String trimmedText = text.trim();
        return trimmedText.isBlank() ? null : trimmedText;
    }

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
