package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "name"})
public record GmailInboxLabelResponse(String id, String name) {

    public static GmailInboxLabelResponse from(RecentInboxReadService.RecentInboxLabel label) {
        return new GmailInboxLabelResponse(label.id(), label.name());
    }
}
