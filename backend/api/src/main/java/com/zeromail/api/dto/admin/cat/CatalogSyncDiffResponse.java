package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.usecases.CatalogSyncOrchestrator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"jobId", "status", "added", "removed", "changed"})
public record CatalogSyncDiffResponse(
        UUID jobId,
        String status,
        List<CatalogModelResponse> added,
        List<CatalogModelResponse> removed,
        List<CatalogModelResponse> changed) {

    public CatalogSyncDiffResponse {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        changed = List.copyOf(changed);
    }

    public static CatalogSyncDiffResponse from(
            CatalogSyncOrchestrator.CatalogSyncDiffResult catalogSyncDiffResult) {
        return new CatalogSyncDiffResponse(
                catalogSyncDiffResult.jobId(),
                catalogSyncDiffResult.status(),
                catalogSyncDiffResult.catalogDiff().added().stream()
                        .map(CatalogModelResponse::from)
                        .toList(),
                catalogSyncDiffResult.catalogDiff().removed().stream()
                        .map(CatalogModelResponse::from)
                        .toList(),
                catalogSyncDiffResult.catalogDiff().changed().stream()
                        .map(CatalogModelResponse::from)
                        .toList());
    }
}
