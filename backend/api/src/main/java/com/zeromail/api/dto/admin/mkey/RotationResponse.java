package com.zeromail.api.dto.admin.mkey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.mkey.usecases.MasterKeyAdminService;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"result"})
public record RotationResponse(
        @Schema(allowableValues = {"OK", "TEST_FAILED"}) String result,
        MasterKeyTestResult testResult,
        Long providerSecretVersion) {

    public static RotationResponse from(MasterKeyAdminService.MasterKeyRotationResult result) {
        return new RotationResponse(
                result.result(), result.testResult(), result.providerSecretVersion());
    }
}
