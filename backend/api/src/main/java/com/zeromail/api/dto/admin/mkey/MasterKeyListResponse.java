package com.zeromail.api.dto.admin.mkey;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"rows"})
public record MasterKeyListResponse(List<MasterKeyMaskedResponse> rows) {

    public MasterKeyListResponse {
        rows = List.copyOf(rows);
    }
}
