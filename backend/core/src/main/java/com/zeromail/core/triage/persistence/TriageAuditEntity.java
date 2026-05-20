package com.zeromail.core.triage.persistence;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import com.zeromail.core.triage.domain.CleanupAuditSource;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "triage_audit")
@AttributeOverride(name = "id", column = @Column(name = "audit_id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class TriageAuditEntity extends AbstractTenantOwnedEntity {

    private static final TriageActionResultJsonValidator ACTION_RESULT_JSON_VALIDATOR =
            new TriageActionResultJsonValidator();
    private static final int SHA_256_HASH_BYTES = 32;

    @Column(name = "gmail_message_id", nullable = false, length = 255)
    private String gmailMessageId;

    @Column(name = "gmail_thread_id", length = 255)
    private String gmailThreadId;

    @Column(name = "sanitized_subject", length = 200)
    private String sanitizedSubject;

    @Column(name = "sanitized_sender_email", length = 320)
    private String sanitizedSenderEmail;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name_snapshot", length = 160)
    private String ruleNameSnapshot;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "args_hash", nullable = false)
    private byte[] argsHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
    private String actionArgsJson;

    /**
     * Stores what Zero Mail changed, not a full Gmail label snapshot. This is set after a Gmail
     * write succeeds and remains null for save-draft, rejected, and failed decisions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gmail_change_token", columnDefinition = "jsonb")
    private String gmailChangeToken;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "lease_owner", length = 255)
    private String leaseOwner;

    /**
     * H-3 Path A discriminator (changelog 046). Defaults to {@link CleanupAuditSource#TRIAGE} for
     * every existing rule-driven write site; cleanup-campaign writes go through {@link
     * TriageAuditWriter#recordCleanupArchive(java.util.UUID, String, java.util.UUID, String,
     * String)} which sets {@link CleanupAuditSource#CLEANUP_CAMPAIGN}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private CleanupAuditSource source = CleanupAuditSource.TRIAGE;

    protected TriageAuditEntity() {
        // Hibernate
    }

    public TriageAuditEntity(
            UUID auditId,
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            byte[] argsHash,
            String actionArgsJson,
            String gmailChangeToken,
            String reason,
            TriageDecision decision,
            String externalRef,
            String failureReason,
            Instant decidedAt,
            Instant appliedAt,
            Instant revertedAt,
            int attemptCount,
            Instant lastAttemptAt,
            String leaseOwner) {
        super(auditId, tenantId);
        this.gmailMessageId = requireText(gmailMessageId, "gmailMessageId");
        this.gmailThreadId = gmailThreadId;
        this.ruleId = ruleId;
        this.ruleNameSnapshot = ruleNameSnapshot;
        this.actionType = requireActionType(actionType).id();
        this.argsHash = copyHash(argsHash);
        this.actionArgsJson = requireText(actionArgsJson, "actionArgsJson");
        this.gmailChangeToken = gmailChangeToken;
        this.reason = reason;
        this.decision = requireDecision(decision).id();
        this.externalRef = externalRef;
        this.failureReason = failureReason;
        this.decidedAt = decidedAt == null ? Instant.now() : decidedAt;
        this.appliedAt = appliedAt;
        this.revertedAt = revertedAt;
        this.attemptCount = attemptCount;
        this.lastAttemptAt = lastAttemptAt;
        this.leaseOwner = leaseOwner;
        this.source = CleanupAuditSource.TRIAGE;
        validateBeforeWrite();
    }

    public UUID getAuditId() {
        return getId();
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public String getGmailThreadId() {
        return gmailThreadId;
    }

    public String getSanitizedSubject() {
        return sanitizedSubject;
    }

    public String getSanitizedSenderEmail() {
        return sanitizedSenderEmail;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public String getRuleNameSnapshot() {
        return ruleNameSnapshot;
    }

    public RuleActionType getActionType() {
        return RuleActionType.fromId(actionType);
    }

    public String getActionTypeId() {
        return actionType;
    }

    public byte[] getArgsHash() {
        return Arrays.copyOf(argsHash, argsHash.length);
    }

    public String getActionArgsJson() {
        ACTION_RESULT_JSON_VALIDATOR.validateActionArgsJson(actionArgsJson);
        return actionArgsJson;
    }

    public String getGmailChangeToken() {
        return gmailChangeToken;
    }

    public String getReason() {
        return reason;
    }

    public TriageDecision getDecision() {
        return TriageDecision.fromId(decision);
    }

    public String getDecisionId() {
        return decision;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getRevertedAt() {
        return revertedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public CleanupAuditSource getSource() {
        return source;
    }

    @PrePersist
    @PreUpdate
    private void validateBeforeWrite() {
        ACTION_RESULT_JSON_VALIDATOR.validateActionArgsJson(actionArgsJson);
        if (argsHash == null || argsHash.length != SHA_256_HASH_BYTES) {
            throw new IllegalArgumentException("argsHash must contain 32 raw SHA-256 bytes");
        }
        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException("decision must not be blank");
        }
        TriageDecision.fromId(decision);
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType must not be blank");
        }
        RuleActionType.fromId(actionType);
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
        if (source == null) {
            source = CleanupAuditSource.TRIAGE;
        }
    }

    private static byte[] copyHash(byte[] argsHash) {
        if (argsHash == null) {
            throw new IllegalArgumentException("argsHash must not be null");
        }
        return Arrays.copyOf(argsHash, argsHash.length);
    }

    private static RuleActionType requireActionType(RuleActionType actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("actionType must not be null");
        }
        return actionType;
    }

    private static TriageDecision requireDecision(TriageDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        return decision;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
