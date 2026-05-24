package com.zeromail.api.dto.admin.waitlist;

import com.zeromail.core.waitlist.projection.WaitlistEntryPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "totalElements", "page", "size"})
public record AdminWaitlistListResponse(
        List<AdminWaitlistEntryResponse> items, long totalElements, int page, int size) {

    public static AdminWaitlistListResponse from(WaitlistEntryPage page) {
        return new AdminWaitlistListResponse(
                page.items().stream().map(AdminWaitlistEntryResponse::from).toList(),
                page.totalElements(),
                page.page(),
                page.size());
    }
}
