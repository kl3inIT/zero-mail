package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

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
        String openInGmailUrl) {

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
                "https://mail.google.com/mail/u/0/#inbox/" + message.gmailThreadId());
    }
}
