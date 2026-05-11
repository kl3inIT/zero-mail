package com.zeromail.core.triage.persistence;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionArgsCanonicalizer;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageActionResultJsonValidator;
import com.zeromail.core.triage.domain.TriageDecision;

/**
 * The validation seam for native triage-audit inserts.
 *
 * <p>Repository native SQL bypasses entity lifecycle validation. Every creator of
 * {@code triage_audit} rows must go through this component so action JSON is validated,
 * canonicalized, and hashed before a row can be inserted.
 */
@Component
public class TriageAuditWriter {

  private static final EnumSet<TriageDecision> DIRECT_TERMINAL_DECISIONS =
      EnumSet.of(
          TriageDecision.SHADOW_LOGGED,
          TriageDecision.REJECTED_BY_SAFETY_NET,
          TriageDecision.REJECTED_BY_SAFETY_POLICY);

  private final TriageAuditRepository triageAuditRepository;
  private final TriageActionResultJsonValidator actionResultJsonValidator;
  private final TriageActionArgsCanonicalizer actionArgsCanonicalizer;

  public TriageAuditWriter(
      TriageAuditRepository triageAuditRepository,
      TriageActionResultJsonValidator actionResultJsonValidator,
      TriageActionArgsCanonicalizer actionArgsCanonicalizer) {
    this.triageAuditRepository = triageAuditRepository;
    this.actionResultJsonValidator = actionResultJsonValidator;
    this.actionArgsCanonicalizer = actionArgsCanonicalizer;
  }

  public Optional<UUID> insertPending(
      UUID tenantId,
      String gmailMessageId,
      String gmailThreadId,
      UUID ruleId,
      String ruleNameSnapshot,
      RuleActionType actionType,
      TriageActionResult preWriteIntent,
      String reasonEvidence) {
    return triageAuditRepository.insertAuditPendingIfAbsent(
        tenantId,
        gmailMessageId,
        gmailThreadId,
        ruleId,
        ruleNameSnapshot,
        actionType.id(),
        canonicalHash(actionType, preWriteIntent),
        actionResultJsonValidator.toJson(preWriteIntent),
        reasonEvidence);
  }

  public Optional<UUID> insertTerminal(
      UUID tenantId,
      String gmailMessageId,
      String gmailThreadId,
      UUID ruleId,
      String ruleNameSnapshot,
      RuleActionType actionType,
      TriageActionResult preWriteIntent,
      String reasonEvidence,
      TriageDecision terminalDecision) {
    if (!DIRECT_TERMINAL_DECISIONS.contains(terminalDecision)) {
      throw new IllegalArgumentException("terminalDecision must be a direct terminal insert state");
    }
    return triageAuditRepository.insertAuditTerminalIfAbsent(
        tenantId,
        gmailMessageId,
        gmailThreadId,
        ruleId,
        ruleNameSnapshot,
        actionType.id(),
        canonicalHash(actionType, preWriteIntent),
        actionResultJsonValidator.toJson(preWriteIntent),
        reasonEvidence,
        terminalDecision.id());
  }

  private byte[] canonicalHash(RuleActionType actionType, TriageActionResult preWriteIntent) {
    actionResultJsonValidator.validate(preWriteIntent);
    if (actionTypeFor(preWriteIntent) != actionType) {
      throw new IllegalArgumentException("actionType must match preWriteIntent");
    }
    return actionArgsCanonicalizer.canonicalHash(preWriteIntent);
  }

  private static RuleActionType actionTypeFor(TriageActionResult actionResult) {
    return switch (actionResult) {
      case TriageActionResult.Label ignored -> RuleActionType.LABEL;
      case TriageActionResult.Archive ignored -> RuleActionType.ARCHIVE;
      case TriageActionResult.SaveDraft ignored -> RuleActionType.SAVE_DRAFT;
    };
  }
}
