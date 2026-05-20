package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.usecases.MasterKeyEditSessionService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"token", "expiresAt"})
public record MasterKeyEditSessionResponse(String token, Instant expiresAt) {

    public static MasterKeyEditSessionResponse from(
            MasterKeyEditSessionService.EditSession editSession) {
        return new MasterKeyEditSessionResponse(editSession.token(), editSession.expiresAt());
    }
}
