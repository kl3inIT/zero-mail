package com.zeromail.core.rules.usecases;

import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CopyRulesService {

    private final RuleRepository ruleRepository;
    private final GmailConnectionService gmailConnectionService;

    public CopyRulesService(
            RuleRepository ruleRepository, GmailConnectionService gmailConnectionService) {
        this.ruleRepository =
                Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
    }

    @Transactional
    public CopyRulesResult copyRules(
            UUID tenantId, UUID sourceGmailConnectionId, UUID targetGmailConnectionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(sourceGmailConnectionId, "sourceGmailConnectionId must not be null");
        Objects.requireNonNull(targetGmailConnectionId, "targetGmailConnectionId must not be null");
        if (sourceGmailConnectionId.equals(targetGmailConnectionId)) {
            throw new IllegalArgumentException("source and target mailboxes must differ");
        }

        gmailConnectionService.resolveOwnedConnectionOrThrow(tenantId, sourceGmailConnectionId);
        gmailConnectionService.resolveOwnedConnectionOrThrow(tenantId, targetGmailConnectionId);

        List<RuleEntity> sourceRules =
                ruleRepository.findOrderedByTenantIdAndGmailConnectionId(
                        tenantId, sourceGmailConnectionId);
        int nextOrderIndex =
                (int)
                        ruleRepository.countByTenantIdAndGmailConnectionId(
                                tenantId, targetGmailConnectionId);
        ArrayList<RuleEntity> copiedRules = new ArrayList<>(sourceRules.size());
        for (RuleEntity sourceRule : sourceRules) {
            RuleEntity copiedRule =
                    new RuleEntity(
                            UUID.randomUUID(),
                            tenantId,
                            targetGmailConnectionId,
                            sourceRule.getDisplayName(),
                            sourceRule.getSourceText(),
                            sourceRule.getSourceLanguage(),
                            sourceRule.getSchemaVersion(),
                            sourceRule.getMatcherAst(),
                            sourceRule.getActionIntents(),
                            nextOrderIndex++,
                            sourceRule.getTemplateKey(),
                            sourceRule.getTemplateVersion());
            copiedRules.add(copiedRule);
        }

        List<RuleStatusProjection> createdRules =
                ruleRepository.saveAllAndFlush(copiedRules).stream()
                        .map(RuleEntity::toStatusProjection)
                        .toList();
        return new CopyRulesResult(createdRules.size(), createdRules);
    }

    public record CopyRulesResult(int copiedCount, List<RuleStatusProjection> copiedRules) {

        public CopyRulesResult {
            copiedRules =
                    List.copyOf(
                            Objects.requireNonNull(copiedRules, "copiedRules must not be null"));
        }
    }
}
