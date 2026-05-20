package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.usecases.CatalogSyncOrchestrator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"jobId", "status"})
public record CatalogSyncFetchResponse(UUID jobId, String status) {

    public static CatalogSyncFetchResponse from(
            CatalogSyncOrchestrator.CatalogSyncFetchResult catalogSyncFetchResult) {
        return new CatalogSyncFetchResponse(
                catalogSyncFetchResult.jobId(), catalogSyncFetchResult.status());
    }
}
