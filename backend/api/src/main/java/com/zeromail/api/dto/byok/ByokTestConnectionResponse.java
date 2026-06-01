package com.zeromail.api.dto.byok;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"result"})
public record ByokTestConnectionResponse(
        @Schema(allowableValues = {"OK", "INVALID_KEY", "RATE_LIMITED", "NETWORK_ERROR", "TIMEOUT"})
                MasterKeyTestResult result,
        List<String> models) {

    public ByokTestConnectionResponse {
        models = models == null ? null : List.copyOf(models);
    }

    public static ByokTestConnectionResponse from(ConnectionTestResult connectionTestResult) {
        List<String> models =
                connectionTestResult.result() == MasterKeyTestResult.OK
                        ? connectionTestResult.models()
                        : null;
        return new ByokTestConnectionResponse(connectionTestResult.result(), models);
    }
}
