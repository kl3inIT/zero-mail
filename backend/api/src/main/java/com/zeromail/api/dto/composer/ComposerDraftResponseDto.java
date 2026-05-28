package com.zeromail.api.dto.composer;

import com.zeromail.core.composer.usecases.ComposerDraftSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Snapshot of the user's currently-cached composer draft on a Gmail thread.
 *
 * <p>Returned by {@code GET /api/composer/drafts?gmailThreadId=...} and {@code PUT
 * /api/composer/drafts}. The body is plain text — the composer textarea is plain text today, and
 * Gmail accepts an empty body for newly-started drafts.
 */
@Schema(
        requiredProperties = {
            "draftId",
            "gmailThreadId",
            "toAddresses",
            "ccAddresses",
            "bccAddresses",
            "subject",
            "body"
        })
public record ComposerDraftResponseDto(
        String draftId,
        String gmailThreadId,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> bccAddresses,
        String subject,
        String body) {

    public ComposerDraftResponseDto {
        toAddresses = List.copyOf(toAddresses);
        ccAddresses = List.copyOf(ccAddresses);
        bccAddresses = List.copyOf(bccAddresses);
    }

    public static ComposerDraftResponseDto from(ComposerDraftSnapshot snapshot) {
        return new ComposerDraftResponseDto(
                snapshot.draftId(),
                snapshot.gmailThreadId(),
                snapshot.toAddresses(),
                snapshot.ccAddresses(),
                snapshot.bccAddresses(),
                snapshot.subject(),
                snapshot.body());
    }
}
