package com.zeromail.core.rules.domain;

import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.shared.validation.EmailRecipientValidator;
import java.util.List;

public sealed interface ActionIntent
        permits ActionIntent.Label,
                ActionIntent.Archive,
                ActionIntent.SaveDraft,
                ActionIntent.MarkRead,
                ActionIntent.Star,
                ActionIntent.AddToDigest,
                ActionIntent.MarkSpam,
                ActionIntent.SendReply,
                ActionIntent.ForwardEmail,
                ActionIntent.SendEmail {

    int MAX_ACTION_TEXT_LENGTH = 500;
    int MAX_ACTION_BODY_LENGTH = 4000;

    RuleActionType type();

    record Label(String labelName) implements ActionIntent {
        public Label {
            labelName = requiredText(labelName, "labelName", MAX_ACTION_TEXT_LENGTH);
        }

        @Override
        public RuleActionType type() {
            return RuleActionType.LABEL;
        }
    }

    record Archive() implements ActionIntent {
        @Override
        public RuleActionType type() {
            return RuleActionType.ARCHIVE;
        }
    }

    record SaveDraft(String instruction) implements ActionIntent {
        public SaveDraft {
            instruction = requiredText(instruction, "instruction", MAX_ACTION_TEXT_LENGTH);
        }

        @Override
        public RuleActionType type() {
            return RuleActionType.SAVE_DRAFT;
        }
    }

    record MarkRead() implements ActionIntent {
        @Override
        public RuleActionType type() {
            return RuleActionType.MARK_READ;
        }
    }

    record Star() implements ActionIntent {
        @Override
        public RuleActionType type() {
            return RuleActionType.STAR;
        }
    }

    record AddToDigest() implements ActionIntent {
        @Override
        public RuleActionType type() {
            return RuleActionType.ADD_TO_DIGEST;
        }
    }

    record MarkSpam() implements ActionIntent {
        @Override
        public RuleActionType type() {
            return RuleActionType.MARK_SPAM;
        }
    }

    record SendReply(String instruction) implements ActionIntent {
        public SendReply {
            instruction = requiredText(instruction, "instruction", MAX_ACTION_TEXT_LENGTH);
        }

        @Override
        public RuleActionType type() {
            return RuleActionType.SEND_REPLY;
        }
    }

    record ForwardEmail(List<String> recipients, String instruction) implements ActionIntent {
        public ForwardEmail {
            recipients = requiredRecipients(recipients, "recipients");
            instruction = optionalText(instruction, MAX_ACTION_TEXT_LENGTH);
        }

        @Override
        public RuleActionType type() {
            return RuleActionType.FORWARD_EMAIL;
        }
    }

    record SendEmail(
            List<String> to, List<String> cc, List<String> bcc, String subject, String draftBody)
            implements ActionIntent {
        public SendEmail {
            to = requiredRecipients(to, "to");
            cc = optionalRecipients(cc, "cc");
            bcc = optionalRecipients(bcc, "bcc");
            subject = requiredText(subject, "subject", MAX_ACTION_TEXT_LENGTH);
            draftBody = requiredText(draftBody, "draftBody", MAX_ACTION_BODY_LENGTH);
        }

        @Override
        public RuleActionType type() {
            return RuleActionType.SEND_EMAIL;
        }
    }

    static ActionIntent fromAction(Action action) {
        return switch (RuleActionType.fromAction(action)) {
            case LABEL -> new Label("Unspecified");
            case ARCHIVE -> new Archive();
            case SAVE_DRAFT -> new SaveDraft("Draft a safe reply for user review");
            case MARK_READ -> new MarkRead();
            case STAR -> new Star();
            case ADD_TO_DIGEST -> new AddToDigest();
            case MARK_SPAM -> new MarkSpam();
            case SEND_REPLY -> new SendReply("Send a safe reply");
            case FORWARD_EMAIL -> new ForwardEmail(List.of("recipient@example.com"), null);
            case SEND_EMAIL ->
                    new SendEmail(
                            List.of("recipient@example.com"),
                            List.of(),
                            List.of(),
                            "Message",
                            "Draft message");
        };
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalizedValue = optionalText(value, maxLength);
        if (normalizedValue == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.isBlank()) {
            return null;
        }
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException("action text is too long");
        }
        return normalizedValue;
    }

    private static List<String> requiredRecipients(List<String> recipients, String fieldName) {
        return EmailRecipientValidator.required(recipients, fieldName);
    }

    private static List<String> optionalRecipients(List<String> recipients, String fieldName) {
        return EmailRecipientValidator.optional(recipients, fieldName);
    }
}
