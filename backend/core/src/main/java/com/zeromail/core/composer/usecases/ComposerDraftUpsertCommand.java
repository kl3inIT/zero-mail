package com.zeromail.core.composer.usecases;

import com.zeromail.core.composer.domain.ComposerMode;
import java.util.Objects;

/**
 * Inputs for upserting the inbox composer draft on a given thread.
 *
 * <p>{@code sourceMessageId} is the Gmail message-id of the email the user is replying to /
 * forwarding from. It is used only when {@code mode} is reply / reply-all to set the {@code
 * In-Reply-To} header so Gmail can thread the draft correctly. For forward it is ignored.
 *
 * <p>{@code rfc822MessageId} is the original {@code Message-ID} header (with angle brackets) of the
 * source message. Gmail uses this — not the Gmail-internal hex id — when threading via {@code
 * In-Reply-To}. When this is blank for a reply-mode call we still update the draft body / headers,
 * but the {@code In-Reply-To} header is omitted (Gmail will infer threading from {@code threadId}).
 */
public record ComposerDraftUpsertCommand(
        String gmailThreadId,
        String draftId,
        String sourceGmailMessageId,
        String rfc822MessageId,
        String priorReferences,
        ComposerMode mode,
        String toAddresses,
        String ccAddresses,
        String bccAddresses,
        String subject,
        String body) {

    public ComposerDraftUpsertCommand {
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        draftId = trimNullable(draftId);
        Objects.requireNonNull(mode, "mode must not be null");
        toAddresses = toAddresses == null ? "" : toAddresses;
        ccAddresses = ccAddresses == null ? "" : ccAddresses;
        bccAddresses = bccAddresses == null ? "" : bccAddresses;
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
        sourceGmailMessageId = trimNullable(sourceGmailMessageId);
        rfc822MessageId = trimNullable(rfc822MessageId);
        priorReferences = trimNullable(priorReferences);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isBlank() ? null : trimmedValue;
    }
}
