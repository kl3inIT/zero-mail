package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.usecases.MasterKeyAdminService;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        name = "CreateProviderResponse",
        requiredProperties = {"provider", "keyId", "priority", "testResult"})
public record CreateProviderResponse(
        String provider, UUID keyId, int priority, MasterKeyTestResult testResult) {

    public static CreateProviderResponse from(
            String provider, MasterKeyAdminService.ProviderKeyAddResult result) {
        return new CreateProviderResponse(
                provider, result.keyId(), result.priority(), MasterKeyTestResult.OK);
    }
}
