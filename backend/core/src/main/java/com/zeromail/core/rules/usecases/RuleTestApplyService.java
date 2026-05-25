package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RuleTestApplyService {

    private static final String LABEL_ACTION_TYPE_ID = "label";
    private static final String LABEL_SAFE_LABEL_PREFIX = "label:";
    private static final int MAX_LABEL_WRITES_PER_REQUEST = 300;

    private final RulePreviewService rulePreviewService;
    private final TriageGmailWriter triageGmailWriter;
    private final TriageAuditWriter triageAuditWriter;
    private final RuleRepository ruleRepository;

    public RuleTestApplyService(
            RulePreviewService rulePreviewService,
            TriageGmailWriter triageGmailWriter,
            TriageAuditWriter triageAuditWriter,
            RuleRepository ruleRepository) {
        this.rulePreviewService =
                Objects.requireNonNull(rulePreviewService, "rulePreviewService must not be null");
        this.triageGmailWriter =
                Objects.requireNonNull(triageGmailWriter, "triageGmailWriter must not be null");
        this.triageAuditWriter =
                Objects.requireNonNull(triageAuditWriter, "triageAuditWriter must not be null");
        this.ruleRepository =
                Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
    }

    public RuleTestApplyResult applyLabelsForEnabledRules(
            UUID tenantId, Integer requestedSampleSize, boolean evaluateSemanticIntents) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        RulePreviewResult previewResult =
                rulePreviewService.previewAllEnabled(
                        tenantId, requestedSampleSize, evaluateSemanticIntents);
        List<LabelTarget> labelTargets = collectLabelTargets(previewResult);
        if (labelTargets.size() > MAX_LABEL_WRITES_PER_REQUEST) {
            throw new RuleTestApplyException(RuleTestApplyFailureReason.TOO_MANY_LABEL_WRITES);
        }
        Map<UUID, String> ruleNamesById = ruleNamesById(tenantId, labelTargets);

        ArrayList<AppliedLabel> appliedLabels = new ArrayList<>();
        for (LabelTarget labelTarget : labelTargets) {
            try {
                String gmailLabelId =
                        triageGmailWriter.applyLabel(
                                tenantId, labelTarget.gmailMessageId(), labelTarget.labelName());
                appliedLabels.add(
                        new AppliedLabel(
                                labelTarget.gmailMessageId(),
                                labelTarget.gmailThreadId(),
                                labelTarget.labelName(),
                                gmailLabelId));
                recordAppliedLabelAudits(tenantId, labelTarget, gmailLabelId, ruleNamesById);
            } catch (IOException ioException) {
                throw new RuleTestApplyException(
                        RuleTestApplyFailureReason.GMAIL_UNAVAILABLE, ioException);
            }
        }

        return new RuleTestApplyResult(previewResult, List.copyOf(appliedLabels));
    }

    private static List<LabelTarget> collectLabelTargets(RulePreviewResult previewResult) {
        LinkedHashMap<String, LabelTargetAccumulator> labelTargetsByKey = new LinkedHashMap<>();
        for (RulePreviewResult.PreviewRow previewRow : previewResult.rows()) {
            if (!previewRow.matched()) {
                continue;
            }
            for (RulePreviewResult.ActionChip actionChip : previewRow.proposedActionChips()) {
                String labelName = labelNameFrom(actionChip);
                if (labelName.isBlank()) {
                    continue;
                }
                String targetKey = previewRow.gmailMessageId() + "\n" + labelName;
                LabelTargetAccumulator accumulator =
                        labelTargetsByKey.computeIfAbsent(
                                targetKey,
                                _ ->
                                        new LabelTargetAccumulator(
                                                previewRow.gmailMessageId(),
                                                previewRow.gmailThreadId(),
                                                previewRow.sanitizedSubjectExcerpt(),
                                                previewRow.sanitizedSenderEmail(),
                                                labelName));
                accumulator.addRuleIds(actionChip.contributingRuleIds());
            }
        }
        return labelTargetsByKey.values().stream().map(LabelTargetAccumulator::toTarget).toList();
    }

    private static String labelNameFrom(RulePreviewResult.ActionChip actionChip) {
        if (!LABEL_ACTION_TYPE_ID.equals(actionChip.actionTypeId())) {
            return "";
        }
        String safeLabel = actionChip.safeLabel();
        if (safeLabel == null || !safeLabel.startsWith(LABEL_SAFE_LABEL_PREFIX)) {
            return "";
        }
        return safeLabel.substring(LABEL_SAFE_LABEL_PREFIX.length()).trim();
    }

    private Map<UUID, String> ruleNamesById(UUID tenantId, List<LabelTarget> labelTargets) {
        LinkedHashSet<UUID> targetRuleIds = new LinkedHashSet<>();
        for (LabelTarget labelTarget : labelTargets) {
            targetRuleIds.addAll(labelTarget.ruleIds());
        }
        if (targetRuleIds.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<UUID, String> ruleNamesById = new LinkedHashMap<>();
        for (RuleEntity ruleEntity : ruleRepository.findOrderedByTenantId(tenantId)) {
            if (targetRuleIds.contains(ruleEntity.getId())) {
                ruleNamesById.put(ruleEntity.getId(), ruleEntity.getDisplayName());
            }
        }
        return Map.copyOf(ruleNamesById);
    }

    private void recordAppliedLabelAudits(
            UUID tenantId,
            LabelTarget labelTarget,
            String gmailLabelId,
            Map<UUID, String> ruleNamesById) {
        for (UUID ruleId : labelTarget.ruleIds()) {
            String ruleNameSnapshot = ruleNamesById.getOrDefault(ruleId, "Rule " + ruleId);
            triageAuditWriter.recordRuleTestAppliedLabel(
                    tenantId,
                    labelTarget.gmailMessageId(),
                    labelTarget.gmailThreadId(),
                    labelTarget.sanitizedSubject(),
                    labelTarget.sanitizedSenderEmail(),
                    ruleId,
                    ruleNameSnapshot,
                    labelTarget.labelName(),
                    gmailLabelId);
        }
    }

    public record RuleTestApplyResult(
            RulePreviewResult previewResult, List<AppliedLabel> appliedLabels) {

        public RuleTestApplyResult {
            Objects.requireNonNull(previewResult, "previewResult must not be null");
            appliedLabels =
                    List.copyOf(
                            Objects.requireNonNull(
                                    appliedLabels, "appliedLabels must not be null"));
        }

        public int appliedLabelCount() {
            return appliedLabels.size();
        }

        public int affectedMessageCount() {
            return (int)
                    appliedLabels.stream().map(AppliedLabel::gmailMessageId).distinct().count();
        }
    }

    public record AppliedLabel(
            String gmailMessageId, String gmailThreadId, String labelName, String gmailLabelId) {

        public AppliedLabel {
            Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
            Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            Objects.requireNonNull(labelName, "labelName must not be null");
            Objects.requireNonNull(gmailLabelId, "gmailLabelId must not be null");
        }
    }

    public enum RuleTestApplyFailureReason {
        GMAIL_UNAVAILABLE,
        TOO_MANY_LABEL_WRITES
    }

    public static class RuleTestApplyException extends RuntimeException {

        private final RuleTestApplyFailureReason reason;

        public RuleTestApplyException(RuleTestApplyFailureReason reason) {
            this(reason, null);
        }

        public RuleTestApplyException(RuleTestApplyFailureReason reason, Throwable cause) {
            super("Rule test apply failed: " + reason, cause);
            this.reason = Objects.requireNonNull(reason, "reason must not be null");
        }

        public RuleTestApplyFailureReason reason() {
            return reason;
        }
    }

    private record LabelTarget(
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            String labelName,
            List<UUID> ruleIds) {

        private LabelTarget {
            Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
            Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            sanitizedSubject = Objects.requireNonNullElse(sanitizedSubject, "");
            sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
            Objects.requireNonNull(labelName, "labelName must not be null");
            ruleIds = List.copyOf(Objects.requireNonNull(ruleIds, "ruleIds must not be null"));
        }
    }

    private static final class LabelTargetAccumulator {

        private final String gmailMessageId;
        private final String gmailThreadId;
        private final String sanitizedSubject;
        private final String sanitizedSenderEmail;
        private final String labelName;
        private final Set<UUID> ruleIds = new LinkedHashSet<>();

        private LabelTargetAccumulator(
                String gmailMessageId,
                String gmailThreadId,
                String sanitizedSubject,
                String sanitizedSenderEmail,
                String labelName) {
            this.gmailMessageId =
                    Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
            this.gmailThreadId =
                    Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            this.sanitizedSubject = Objects.requireNonNullElse(sanitizedSubject, "");
            this.sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
            this.labelName = Objects.requireNonNull(labelName, "labelName must not be null");
        }

        private void addRuleIds(List<UUID> contributingRuleIds) {
            if (contributingRuleIds == null) {
                return;
            }
            ruleIds.addAll(contributingRuleIds);
        }

        private LabelTarget toTarget() {
            return new LabelTarget(
                    gmailMessageId,
                    gmailThreadId,
                    sanitizedSubject,
                    sanitizedSenderEmail,
                    labelName,
                    List.copyOf(ruleIds));
        }
    }
}
