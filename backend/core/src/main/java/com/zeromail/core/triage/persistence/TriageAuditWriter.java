package com.zeromail.core.triage.persistence;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.usecases.TriageActionArgsCanonicalizer;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The validation seam for native triage-audit inserts.
 *
 * <p>Repository native SQL bypasses entity lifecycle validation. Every creator of {@code
 * triage_audit} rows must go through this component so action JSON is validated, canonicalized, and
 * hashed before a row can be inserted.
 */
@Component
public class TriageAuditWriter {

    private static final Logger logger = LoggerFactory.getLogger(TriageAuditWriter.class);

    private static final EnumSet<TriageDecision> DIRECT_TERMINAL_DECISIONS =
            EnumSet.of(
                    TriageDecision.REJECTED_BY_SAFETY_NET,
                    TriageDecision.REJECTED_BY_SAFETY_POLICY);

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final String CLEANUP_ACTION_ARGS_JSON = "{\"type\":\"archive\"}";
    private static final String RULE_TEST_APPLY_REASON = "rule_test_apply";

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
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence) {
        return triageAuditRepository.insertAuditPendingIfAbsent(
                tenantId,
                gmailMessageId,
                gmailThreadId,
                sanitizedSubject,
                sanitizedSenderEmail,
                ruleId,
                ruleNameSnapshot,
                actionType.id(),
                canonicalHash(actionType, preWriteIntent),
                actionResultJsonValidator.toJson(preWriteIntent),
                reasonEvidence,
                null);
    }

    public Optional<UUID> insertPending(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence,
            String blockedBySafetyNetPattern) {
        return triageAuditRepository.insertAuditPendingIfAbsent(
                tenantId,
                gmailMessageId,
                gmailThreadId,
                sanitizedSubject,
                sanitizedSenderEmail,
                ruleId,
                ruleNameSnapshot,
                actionType.id(),
                canonicalHash(actionType, preWriteIntent),
                actionResultJsonValidator.toJson(preWriteIntent),
                reasonEvidence,
                blockedBySafetyNetPattern);
    }

    public Optional<UUID> insertTerminal(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence,
            TriageDecision terminalDecision) {
        if (!DIRECT_TERMINAL_DECISIONS.contains(terminalDecision)) {
            throw new IllegalArgumentException(
                    "terminalDecision must be a direct terminal insert state");
        }
        return triageAuditRepository.insertAuditTerminalIfAbsent(
                tenantId,
                gmailMessageId,
                gmailThreadId,
                sanitizedSubject,
                sanitizedSenderEmail,
                ruleId,
                ruleNameSnapshot,
                actionType.id(),
                canonicalHash(actionType, preWriteIntent),
                actionResultJsonValidator.toJson(preWriteIntent),
                reasonEvidence,
                terminalDecision.id(),
                null);
    }

    public Optional<UUID> insertTerminal(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence,
            TriageDecision terminalDecision,
            String blockedBySafetyNetPattern) {
        if (!DIRECT_TERMINAL_DECISIONS.contains(terminalDecision)) {
            throw new IllegalArgumentException(
                    "terminalDecision must be a direct terminal insert state");
        }
        return triageAuditRepository.insertAuditTerminalIfAbsent(
                tenantId,
                gmailMessageId,
                gmailThreadId,
                sanitizedSubject,
                sanitizedSenderEmail,
                ruleId,
                ruleNameSnapshot,
                actionType.id(),
                canonicalHash(actionType, preWriteIntent),
                actionResultJsonValidator.toJson(preWriteIntent),
                reasonEvidence,
                terminalDecision.id(),
                blockedBySafetyNetPattern);
    }

    public Optional<UUID> findPendingAuditId(
            UUID tenantId,
            String gmailMessageId,
            UUID ruleId,
            RuleActionType actionType,
            TriageActionResult preWriteIntent) {
        return triageAuditRepository.findPendingAuditIdByKey(
                tenantId,
                gmailMessageId,
                ruleId,
                actionType.id(),
                canonicalHash(actionType, preWriteIntent));
    }

    /**
     * H-3 Path A: persist a cleanup-campaign archive audit row. Called by {@code
     * UnsubscribeCampaignHandler} (Plan 06 Task 3) after the worker archives a history message on
     * behalf of an unsubscribe campaign.
     *
     * <p>Writes through {@link TriageAuditRepository#insertCleanupArchiveAudit} with:
     *
     * <ul>
     *   <li>{@code source='CLEANUP_CAMPAIGN'} — distinguishes cleanup writes from rule-driven
     *       triage
     *   <li>{@code action_type='ARCHIVE'} — only archive action exists for cleanup writes
     *   <li>{@code decision='APPLIED'} — cleanup writes go straight to terminal APPLIED
     *   <li>{@code external_ref=attemptId.toString()} — links audit row back to {@code
     *       unsubscribe_attempt.id} for the undo path
     *   <li>{@code gmail_change_token={"appliedLabelIds":["<labelId>"]}} — records the Gmail label
     *       added so {@code restoreToInbox + removeLabel} can reverse it on undo
     *   <li>{@code sanitized_sender_email=senderEmail} — must equal the canonicalized form {@code
     *       CandidateQueryService} returns so the undo JOIN against {@code
     *       unsubscribe_attempt.sender_email} is sound
     * </ul>
     *
     * <p>Privacy invariant (UNS-09): log line records {@code senderDomain} only — extracted post-@
     * — never the full email address.
     */
    public Optional<UUID> recordCleanupArchive(
            UUID tenantId,
            String gmailMessageId,
            UUID campaignAttemptId,
            String labelId,
            String senderEmail) {
        requireText(gmailMessageId, "gmailMessageId");
        requireText(labelId, "labelId");
        requireText(senderEmail, "senderEmail");
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (campaignAttemptId == null) {
            throw new IllegalArgumentException("campaignAttemptId must not be null");
        }

        String gmailChangeToken = "{\"appliedLabelIds\":[\"" + labelId + "\"]}";
        byte[] argsHash = sha256OfCleanupKey(campaignAttemptId, labelId, gmailMessageId);

        Optional<UUID> insertedAuditId =
                triageAuditRepository.insertCleanupArchiveAudit(
                        tenantId,
                        gmailMessageId,
                        senderEmail,
                        argsHash,
                        CLEANUP_ACTION_ARGS_JSON,
                        gmailChangeToken,
                        campaignAttemptId.toString());

        logger.info(
                "event=triage_audit_cleanup_archive_recorded tenantId={} attemptId={}"
                        + " senderDomain={}",
                tenantId,
                campaignAttemptId,
                extractDomain(senderEmail));

        return insertedAuditId;
    }

    public Optional<UUID> recordRuleTestAppliedLabel(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            String labelName,
            String gmailLabelId) {
        requireText(gmailMessageId, "gmailMessageId");
        requireText(gmailThreadId, "gmailThreadId");
        requireText(labelName, "labelName");
        requireText(gmailLabelId, "gmailLabelId");
        requireText(ruleNameSnapshot, "ruleNameSnapshot");
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }

        TriageActionResult.Label resolvedLabel =
                new TriageActionResult.Label(gmailLabelId, labelName);
        String actionArgsJson = actionResultJsonValidator.toJson(resolvedLabel);
        Optional<UUID> insertedAuditId =
                triageAuditRepository.insertRuleTestAppliedLabelAudit(
                        tenantId,
                        gmailMessageId,
                        gmailThreadId,
                        blankToNull(sanitizedSubject),
                        blankToNull(sanitizedSenderEmail),
                        ruleId,
                        ruleNameSnapshot,
                        RuleActionType.LABEL.id(),
                        canonicalHash(RuleActionType.LABEL, resolvedLabel),
                        actionArgsJson,
                        changeToken(Map.of("addedLabelId", gmailLabelId)),
                        RULE_TEST_APPLY_REASON,
                        gmailMessageId);

        logger.info(
                "event=triage_audit_rule_test_label_recorded tenantId={} gmailMessageId={} ruleId={}",
                tenantId,
                gmailMessageId,
                ruleId);
        return insertedAuditId;
    }

    private static byte[] sha256OfCleanupKey(
            UUID campaignAttemptId, String labelId, String gmailMessageId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(campaignAttemptId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(labelId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(gmailMessageId.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException(
                    "SHA-256 must be available on every JVM", noSuchAlgorithmException);
        }
    }

    private static String extractDomain(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "unknown";
        }
        return senderEmail.substring(atIndex + 1);
    }

    private static String changeToken(Map<String, ?> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Unable to serialize Gmail change token", jacksonException);
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
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
            case TriageActionResult.Label _ -> RuleActionType.LABEL;
            case TriageActionResult.Archive _ -> RuleActionType.ARCHIVE;
            case TriageActionResult.SaveDraft _ -> RuleActionType.SAVE_DRAFT;
            case TriageActionResult.MarkRead _ -> RuleActionType.MARK_READ;
            case TriageActionResult.Star _ -> RuleActionType.STAR;
            case TriageActionResult.AddToDigest _ -> RuleActionType.ADD_TO_DIGEST;
            case TriageActionResult.MarkSpam _ -> RuleActionType.MARK_SPAM;
            case TriageActionResult.SendReply _ -> RuleActionType.SEND_REPLY;
            case TriageActionResult.ForwardEmail _ -> RuleActionType.FORWARD_EMAIL;
            case TriageActionResult.SendEmail _ -> RuleActionType.SEND_EMAIL;
        };
    }
}
