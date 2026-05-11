package com.zeromail.core.rules.application;

import com.zeromail.core.rules.projection.RuleStatusProjection;
import java.util.List;
import java.util.Objects;

public record RuleTemplateMaterializationResult(
        int createdCount,
        int skippedCount,
        int customizedPreservedCount,
        List<RuleStatusProjection> createdRules,
        List<SkippedTemplate> skippedTemplates) {

    public RuleTemplateMaterializationResult {
        if (createdCount < 0 || skippedCount < 0 || customizedPreservedCount < 0) {
            throw new IllegalArgumentException("materialization counts must not be negative");
        }
        createdRules = List.copyOf(Objects.requireNonNull(createdRules, "createdRules"));
        skippedTemplates =
                List.copyOf(Objects.requireNonNull(skippedTemplates, "skippedTemplates"));
    }

    public record SkippedTemplate(String templateKey, SkippedTemplateReason reason) {
        public SkippedTemplate {
            if (templateKey == null || templateKey.isBlank()) {
                throw new IllegalArgumentException("templateKey must not be blank");
            }
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum SkippedTemplateReason {
        ALREADY_MATERIALIZED,
        CONCURRENTLY_MATERIALIZED,
        CUSTOMIZED_PRESERVED,
        UNKNOWN_OR_DEPRECATED
    }
}
