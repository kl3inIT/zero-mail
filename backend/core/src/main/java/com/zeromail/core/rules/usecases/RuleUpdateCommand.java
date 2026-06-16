package com.zeromail.core.rules.usecases;

import java.util.UUID;

public record RuleUpdateCommand(
        UUID tenantId,
        UUID gmailConnectionId,
        UUID ruleId,
        String displayName,
        String sourceText,
        RuleCompileResult compileResult,
        Integer expectedEntityVersion) {

    public RuleUpdateCommand {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (gmailConnectionId == null) {
            throw new IllegalArgumentException("gmailConnectionId must not be null");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        displayName = RuleCreateCommand.requireBoundedText(displayName, "displayName", 160);
        sourceText = RuleCreateCommand.requireBoundedText(sourceText, "sourceText", 4_000);
        RuleCreateCommand.requireCompiled(compileResult);
        if (expectedEntityVersion == null || expectedEntityVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedEntityVersion must be a non-negative integer");
        }
    }
}
