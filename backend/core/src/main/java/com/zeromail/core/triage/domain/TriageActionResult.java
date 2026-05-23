package com.zeromail.core.triage.domain;

import java.util.List;

/**
 * Persisted result of a triage action after it has been resolved for Gmail execution.
 *
 * <p>This is intentionally separate from rules-domain action intent. Triage audit rows must
 * round-trip Gmail response state for undo, including the resolved label id and the draft id
 * returned after a Gmail draft is created.
 *
 * <p>The idempotency {@code args_hash} must be computed from the pre-write intent. For {@link
 * SaveDraft}, that means {@code draftId == null}; retry code that derives the hash before calling
 * Gmail must still match the existing PENDING row after Gmail later returns a draft id.
 */
public sealed interface TriageActionResult
        permits TriageActionResult.Label,
                TriageActionResult.Archive,
                TriageActionResult.SaveDraft,
                TriageActionResult.MarkRead,
                TriageActionResult.Star,
                TriageActionResult.AddToDigest,
                TriageActionResult.MarkSpam,
                TriageActionResult.SendReply,
                TriageActionResult.ForwardEmail,
                TriageActionResult.SendEmail {

    record Label(String labelId, String labelName) implements TriageActionResult {
        public Label {
            requireText(labelId, "labelId");
            requireText(labelName, "labelName");
        }
    }

    record Archive() implements TriageActionResult {}

    record SaveDraft(String instruction, String draftId, String threadId)
            implements TriageActionResult {
        public SaveDraft {
            requireText(instruction, "instruction");
            if (draftId != null && draftId.isBlank()) {
                throw new IllegalArgumentException("draftId must not be blank");
            }
            requireText(threadId, "threadId");
        }
    }

    record MarkRead() implements TriageActionResult {}

    record Star() implements TriageActionResult {}

    record AddToDigest() implements TriageActionResult {}

    record MarkSpam() implements TriageActionResult {}

    record SendReply(String draftBody, String gmailMessageId, String gmailThreadId)
            implements TriageActionResult {
        public SendReply {
            requireText(draftBody, "draftBody");
            requireText(gmailMessageId, "gmailMessageId");
            requireText(gmailThreadId, "gmailThreadId");
        }
    }

    record ForwardEmail(List<String> recipients, String draftBody) implements TriageActionResult {
        public ForwardEmail {
            recipients = requireRecipients(recipients, "recipients");
            requireText(draftBody, "draftBody");
        }
    }

    record SendEmail(
            List<String> to, List<String> cc, List<String> bcc, String subject, String draftBody)
            implements TriageActionResult {
        public SendEmail {
            to = requireRecipients(to, "to");
            cc = optionalRecipients(cc);
            bcc = optionalRecipients(bcc);
            requireText(subject, "subject");
            requireText(draftBody, "draftBody");
        }
    }

    private static void requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static List<String> requireRecipients(List<String> recipients, String fieldName) {
        List<String> normalizedRecipients = optionalRecipients(recipients);
        if (normalizedRecipients.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return normalizedRecipients;
    }

    private static List<String> optionalRecipients(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .map(recipient -> recipient == null ? "" : recipient.trim())
                .filter(recipient -> !recipient.isBlank())
                .toList();
    }
}
