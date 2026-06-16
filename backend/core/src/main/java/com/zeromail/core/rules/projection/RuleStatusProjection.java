package com.zeromail.core.rules.projection;

import com.zeromail.core.rules.domain.RuleId;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import java.time.Instant;
import java.util.UUID;

public record RuleStatusProjection(
        RuleId ruleId,
        UUID gmailConnectionId,
        String displayName,
        String sourceText,
        boolean enabled,
        int orderIndex,
        RuleLanguage sourceLanguage,
        RuleSchemaVersion schemaVersion,
        String matcherAst,
        String actionIntents,
        Integer entityVersion,
        Integer lastPreviewedEntityVersion,
        Instant lastPreviewedAt,
        String templateKey,
        Integer templateVersion,
        boolean customized) {}
