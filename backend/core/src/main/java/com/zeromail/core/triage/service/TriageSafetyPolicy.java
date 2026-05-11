package com.zeromail.core.triage.service;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zeromail.core.rules.domain.ActionProposal;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.exception.TriageSafetyViolationException;

/**
 * Runtime backstop for the architectural "auto-send forbidden" guarantee.
 *
 * <p>{@code NoGmailSendAllowedTest} is the compile-time twin; this policy is the last runtime
 * allow-list gate before any proposal can reach Gmail writes.
 */
@Component
public class TriageSafetyPolicy {

  private static final Logger log = LoggerFactory.getLogger(TriageSafetyPolicy.class);

  private static final EnumSet<RuleActionType> ALLOW_LIST =
      EnumSet.of(RuleActionType.LABEL, RuleActionType.ARCHIVE, RuleActionType.SAVE_DRAFT);

  public RuleActionType gate(ActionProposal actionProposal) {
    RuleActionType actionType = actionProposal == null ? null : actionProposal.type();
    if (actionType == null || !ALLOW_LIST.contains(actionType)) {
      logRejectedProposal(actionProposal, actionType);
      throw new TriageSafetyViolationException();
    }
    return actionType;
  }

  private static void logRejectedProposal(ActionProposal actionProposal, RuleActionType actionType) {
    String tenantId = TenantContext.currentOptional().orElse("unbound");
    UUID ruleId = firstContributingRuleId(actionProposal);
    log.warn(
        "event=triage_safety_violation tenantId={} ruleId={} actionType={}",
        tenantId,
        ruleId,
        actionType);
  }

  private static UUID firstContributingRuleId(ActionProposal actionProposal) {
    if (actionProposal == null) {
      return null;
    }
    List<UUID> contributingRuleIds = actionProposal.contributingRuleIds();
    return contributingRuleIds.isEmpty() ? null : contributingRuleIds.getFirst();
  }
}
