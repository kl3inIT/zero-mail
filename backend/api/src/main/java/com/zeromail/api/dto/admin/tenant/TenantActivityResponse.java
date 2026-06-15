package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        requiredProperties = {
            "last30dRuleFireCount",
            "chatSessionCount",
            "totalActivity7dCount",
            "events"
        })
public record TenantActivityResponse(
        int last30dRuleFireCount,
        int chatSessionCount,
        Instant lastChatSessionAt,
        String lastChatModelSelection,
        int totalActivity7dCount,
        Instant lastLoginAt,
        Integer totalAppDurationSeconds,
        List<TenantActivityEventResponse> events) {

    public static TenantActivityResponse from(TenantActivitySnapshot tenantActivitySnapshot) {
        return new TenantActivityResponse(
                tenantActivitySnapshot.last30dRuleFireCount(),
                tenantActivitySnapshot.chatSessionCount(),
                tenantActivitySnapshot.lastChatSessionAt(),
                tenantActivitySnapshot.lastChatModelSelection(),
                tenantActivitySnapshot.totalActivity7dCount(),
                tenantActivitySnapshot.lastLoginAt(),
                tenantActivitySnapshot.totalAppDurationSeconds(),
                tenantActivitySnapshot.events().stream()
                        .map(TenantActivityEventResponse::from)
                        .toList());
    }
}
