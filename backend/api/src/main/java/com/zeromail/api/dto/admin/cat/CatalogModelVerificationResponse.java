package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of {@code POST /api/admin/catalog/models/{modelId}/verify}. Mirrors the freshly-recorded
 * row on {@code model_catalog}.
 */
public record CatalogModelVerificationResponse(
        @Schema(
                        description = "Verification lifecycle after the probe.",
                        allowableValues = {"UNTESTED", "VERIFIED", "STALE", "FAILED"})
                ModelVerificationStatus status,
        @Schema(description = "Probe wall-clock duration in milliseconds, when measured.")
                Integer latencyMs,
        @Schema(description = "Provider-side error indicator on FAILED outcomes.") String error) {

    public static CatalogModelVerificationResponse from(
            ModelVerificationStatus status, Integer latencyMs, String error) {
        return new CatalogModelVerificationResponse(status, latencyMs, error);
    }
}
