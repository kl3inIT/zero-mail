package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"sourceGmailConnectionId", "targetGmailConnectionId"})
public record CopyRulesRequest(
        @NotNull UUID sourceGmailConnectionId, @NotNull UUID targetGmailConnectionId) {}
