package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"last30dRuleFireCount", "chatSessionCount"})
public record TenantActivityResponse(
        int last30dRuleFireCount,
        int chatSessionCount,
        Instant lastChatSessionAt,
        String lastChatModelSelection) {

    public static TenantActivityResponse from(TenantActivitySnapshot tenantActivitySnapshot) {
        return new TenantActivityResponse(
                tenantActivitySnapshot.last30dRuleFireCount(),
                tenantActivitySnapshot.chatSessionCount(),
                tenantActivitySnapshot.lastChatSessionAt(),
                tenantActivitySnapshot.lastChatModelSelection());
    }
}
