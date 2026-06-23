package com.zeromail.api.dto.gmail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "gmailMessageId",
            "gmailThreadId",
            "subject",
            "snippet",
            "from",
            "to",
            "cc",
            "receivedAt",
            "labelIds",
            "labels",
            "unread",
            "hasAttachment",
            "openInGmailUrl"
        })
public record GmailInboxMessageResponse(
        String gmailMessageId,
        String gmailThreadId,
        String subject,
        String snippet,
        String from,
        List<String> to,
        List<String> cc,
        Instant receivedAt,
        List<String> labelIds,
        List<GmailInboxLabelResponse> labels,
        boolean unread,
        boolean hasAttachment,
        String openInGmailUrl,
        @Schema(
                        description =
                                "Calendar classification when this message is a"
                                        + " text/calendar invite/cancel/reschedule/RSVP — populated by"
                                        + " the worker AFTER_COMMIT classifier (W4). NULL for"
                                        + " non-calendar messages and for rows served from the"
                                        + " live-Gmail fallback before backfill completes.",
                        allowableValues = {"INVITE", "CANCEL", "RESCHEDULE", "RSVP"},
                        nullable = true)
                String messageClass,
        @Schema(
                        description =
                                "Calendar event datetime extracted from VEVENT DTSTART when"
                                        + " messageClass is non-null. NULL when messageClass is NULL."
                                        + " Drives the 24-hour top-of-inbox pin window (W4).",
                        nullable = true,
                        format = "date-time")
                Instant eventDt) {

    public GmailInboxMessageResponse {
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        labelIds = List.copyOf(labelIds);
        labels = List.copyOf(labels);
    }

    public static GmailInboxMessageResponse from(RecentInboxMessage message) {
        return new GmailInboxMessageResponse(
                message.gmailMessageId(),
                message.gmailThreadId(),
                message.subject(),
                message.snippet(),
                message.from(),
                message.to(),
                message.cc(),
                message.receivedAt(),
                message.labelIds(),
                message.labels().stream().map(GmailInboxLabelResponse::from).toList(),
                message.unread(),
                message.hasAttachment(),
                "https://mail.google.com/mail/u/0/#inbox/" + message.gmailThreadId(),
                message.messageClass() == null ? null : message.messageClass().id(),
                message.eventDt());
    }
}
