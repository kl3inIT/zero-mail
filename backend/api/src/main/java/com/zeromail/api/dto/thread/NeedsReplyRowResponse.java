package com.zeromail.api.dto.thread;

import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailThreadDisplay;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.projection.NeedsReplyRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        requiredProperties = {
            "gmailThreadId",
            "subject",
            "otherParty",
            "lastActivityAt",
            "draftStatus",
            "resolved",
            "openInGmailUrl"
        })
public record NeedsReplyRowResponse(
        String gmailThreadId,
        @Schema(nullable = true) String subject,
        @Schema(nullable = true) String otherParty,
        @Schema(nullable = true) Instant lastActivityAt,
        String draftStatus,
        boolean resolved,
        String openInGmailUrl,
        @Schema(
                        nullable = true,
                        description =
                                "Short preview of the latest message in the thread, fetched live"
                                        + " from Gmail (never persisted).")
                String snippet,
        @Schema(
                        nullable = true,
                        description =
                                "Gmail message id of the latest message in the thread, used to open"
                                        + " an in-place reader. Null when the live Gmail display"
                                        + " fetch was unavailable.")
                String latestMessageId,
        @Schema(
                        nullable = true,
                        description =
                                "Gmail draft id of the saved reply draft for this thread, used to"
                                        + " preview the draft body in-place. Null when the thread"
                                        + " has no draft.")
                String draftId) {

    public static NeedsReplyRowResponse from(NeedsReplyRow row, GmailThreadDisplay display) {
        return new NeedsReplyRowResponse(
                row.gmailThreadId(),
                display == null ? null : display.subject(),
                display == null ? null : display.otherParty(),
                display == null ? null : display.lastActivityAt(),
                draftStatus(row),
                row.resolved(),
                "https://mail.google.com/mail/u/0/#all/" + row.gmailThreadId(),
                display == null ? null : display.snippet(),
                display == null ? null : display.latestMessageId(),
                row.hasDraft() ? row.draftId() : null);
    }

    private static String draftStatus(NeedsReplyRow row) {
        if (!row.hasDraft()) {
            return "NO_DRAFT";
        }
        if (ThreadReplyBucket.AWAITING_THEIR_REPLY.publicSlug().equals(row.bucket())) {
            return "DRAFT_SENT";
        }
        return "DRAFT_READY";
    }
}
