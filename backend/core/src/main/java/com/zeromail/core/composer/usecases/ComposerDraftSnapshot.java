package com.zeromail.core.composer.usecases;

import java.util.List;
import java.util.Objects;

/**
 * Snapshot of a Gmail composer draft as it currently lives in Gmail.
 *
 * <p>{@code toAddresses}, {@code ccAddresses}, {@code bccAddresses} are normalized (trimmed, no
 * blanks). {@code body} is the plain-text body the user last wrote.
 *
 * <p>Returned from {@code ComposerDraftService.find} when an existing draft is found, and from
 * {@code ComposerDraftService.upsert} so callers can confirm what landed in Gmail.
 */
public record ComposerDraftSnapshot(
        String draftId,
        String gmailThreadId,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> bccAddresses,
        String subject,
        String body) {

    public ComposerDraftSnapshot {
        Objects.requireNonNull(draftId, "draftId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        toAddresses = toAddresses == null ? List.of() : List.copyOf(toAddresses);
        ccAddresses = ccAddresses == null ? List.of() : List.copyOf(ccAddresses);
        bccAddresses = bccAddresses == null ? List.of() : List.copyOf(bccAddresses);
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
    }
}
