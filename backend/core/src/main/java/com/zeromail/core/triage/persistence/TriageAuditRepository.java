package com.zeromail.core.triage.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TriageAuditRepository extends JpaRepository<TriageAuditEntity, UUID> {

    @Query(
            value =
                    """
          INSERT INTO triage_audit (
            audit_id, tenant_id, gmail_message_id, gmail_thread_id,
            sanitized_subject, sanitized_sender_email,
            rule_id, rule_name_snapshot,
            action_type, args_hash, action_args_json, gmail_change_token, reason, decision,
            attempt_count, last_attempt_at, lease_owner, decided_at, created_at, updated_at, version
          )
          VALUES (
            gen_random_uuid(), :tenantId, :gmailMessageId, :gmailThreadId,
            :sanitizedSubject, :sanitizedSenderEmail,
            :ruleId, :ruleNameSnapshot, :actionType, :argsHash, CAST(:actionArgsJson AS jsonb), NULL,
            :reason, 'PENDING', 0, NULL, NULL, NOW(), NOW(), NOW(), 0
          )
          ON CONFLICT (tenant_id, gmail_message_id, rule_id, action_type, args_hash) DO NOTHING
          RETURNING audit_id
          """,
            nativeQuery = true)
    @Transactional
    Optional<UUID> insertAuditPendingIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("gmailMessageId") String gmailMessageId,
            @Param("gmailThreadId") String gmailThreadId,
            @Param("sanitizedSubject") String sanitizedSubject,
            @Param("sanitizedSenderEmail") String sanitizedSenderEmail,
            @Param("ruleId") UUID ruleId,
            @Param("ruleNameSnapshot") String ruleNameSnapshot,
            @Param("actionType") String actionType,
            @Param("argsHash") byte[] argsHash,
            @Param("actionArgsJson") String actionArgsJson,
            @Param("reason") String reason);

    @Query(
            value =
                    """
          INSERT INTO triage_audit (
            audit_id, tenant_id, gmail_message_id, gmail_thread_id,
            sanitized_subject, sanitized_sender_email,
            rule_id, rule_name_snapshot,
            action_type, args_hash, action_args_json, gmail_change_token, reason, decision,
            attempt_count, last_attempt_at, lease_owner, decided_at, created_at, updated_at, version
          )
          VALUES (
            gen_random_uuid(), :tenantId, :gmailMessageId, :gmailThreadId,
            :sanitizedSubject, :sanitizedSenderEmail,
            :ruleId, :ruleNameSnapshot, :actionType, :argsHash, CAST(:actionArgsJson AS jsonb), NULL,
            :reason, :decision, 0, NULL, NULL, NOW(), NOW(), NOW(), 0
          )
          ON CONFLICT (tenant_id, gmail_message_id, rule_id, action_type, args_hash) DO NOTHING
          RETURNING audit_id
          """,
            nativeQuery = true)
    @Transactional
    Optional<UUID> insertAuditTerminalIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("gmailMessageId") String gmailMessageId,
            @Param("gmailThreadId") String gmailThreadId,
            @Param("sanitizedSubject") String sanitizedSubject,
            @Param("sanitizedSenderEmail") String sanitizedSenderEmail,
            @Param("ruleId") UUID ruleId,
            @Param("ruleNameSnapshot") String ruleNameSnapshot,
            @Param("actionType") String actionType,
            @Param("argsHash") byte[] argsHash,
            @Param("actionArgsJson") String actionArgsJson,
            @Param("reason") String reason,
            @Param("decision") String decision);

    @Query(
            value =
                    """
          SELECT audit_id
          FROM triage_audit
          WHERE tenant_id = :tenantId
            AND gmail_message_id = :gmailMessageId
            AND rule_id = :ruleId
            AND action_type = :actionType
            AND args_hash = :argsHash
            AND decision = 'PENDING'
          """,
            nativeQuery = true)
    Optional<UUID> findPendingAuditIdByKey(
            @Param("tenantId") UUID tenantId,
            @Param("gmailMessageId") String gmailMessageId,
            @Param("ruleId") UUID ruleId,
            @Param("actionType") String actionType,
            @Param("argsHash") byte[] argsHash);

    @Modifying
    @Query(
            value =
                    """
          UPDATE triage_audit
          SET attempt_count = attempt_count + 1,
              last_attempt_at = NOW(),
              lease_owner = :leaseOwner,
              updated_at = NOW()
          WHERE audit_id = :auditId
            AND tenant_id = :tenantId
            AND decision = 'PENDING'
            AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - INTERVAL '2 minutes')
          """,
            nativeQuery = true)
    @Transactional
    int reclaimStalePending(
            @Param("auditId") UUID auditId,
            @Param("tenantId") UUID tenantId,
            @Param("leaseOwner") String leaseOwner);

    @Modifying
    @Query(
            value =
                    """
          UPDATE triage_audit
          SET applied_at = NOW(),
              decided_at = NOW(),
              decision = 'APPLIED',
              external_ref = :externalRef,
              action_args_json =
                CASE
                  WHEN :resolvedActionArgsJson IS NULL THEN action_args_json
                  ELSE CAST(:resolvedActionArgsJson AS jsonb)
                END,
              gmail_change_token =
                CASE
                  WHEN :gmailChangeToken IS NULL THEN NULL
                  ELSE CAST(:gmailChangeToken AS jsonb)
                END,
              lease_owner = NULL,
              updated_at = NOW()
          WHERE audit_id = :auditId
            AND tenant_id = :tenantId
            AND decision = 'PENDING'
          """,
            nativeQuery = true)
    @Transactional
    int markApplied(
            @Param("auditId") UUID auditId,
            @Param("tenantId") UUID tenantId,
            @Param("externalRef") String externalRef,
            @Param("gmailChangeToken") String gmailChangeToken,
            @Param("resolvedActionArgsJson") String resolvedActionArgsJson);

    @Modifying
    @Query(
            value =
                    """
          UPDATE triage_audit
          SET decision = 'FAILED',
              decided_at = NOW(),
              failure_reason = :failureReason,
              lease_owner = NULL,
              updated_at = NOW()
          WHERE audit_id = :auditId
            AND tenant_id = :tenantId
            AND decision = 'PENDING'
          """,
            nativeQuery = true)
    @Transactional
    int markFailed(
            @Param("auditId") UUID auditId,
            @Param("tenantId") UUID tenantId,
            @Param("failureReason") String failureReason);

    @Modifying
    @Query(
            value =
                    """
          UPDATE triage_audit
          SET decision = 'REVERT_PENDING',
              last_attempt_at = NOW(),
              updated_at = NOW()
          WHERE audit_id = :auditId
            AND tenant_id = :tenantId
            AND decision = 'APPLIED'
          """,
            nativeQuery = true)
    @Transactional
    int markRevertPending(@Param("auditId") UUID auditId, @Param("tenantId") UUID tenantId);

    @Modifying
    @Query(
            value =
                    """
          UPDATE triage_audit
          SET decision = 'REVERTED',
              reverted_at = :revertedAt,
              updated_at = NOW()
          WHERE audit_id = :auditId
            AND tenant_id = :tenantId
            AND decision = 'REVERT_PENDING'
          """,
            nativeQuery = true)
    @Transactional
    int markReverted(
            @Param("auditId") UUID auditId,
            @Param("tenantId") UUID tenantId,
            @Param("revertedAt") Instant revertedAt);

    @Query(
            """
      SELECT triageAudit
      FROM TriageAuditEntity triageAudit
      WHERE triageAudit.id = :auditId
        AND triageAudit.tenantId = :tenantId
      """)
    Optional<TriageAuditEntity> findByAuditIdAndTenantId(
            @Param("auditId") UUID auditId, @Param("tenantId") UUID tenantId);

    @Query(
            """
      SELECT triageAudit
      FROM TriageAuditEntity triageAudit
      WHERE triageAudit.decision = 'PENDING'
        AND (
          triageAudit.lastAttemptAt < :cutoff
          OR (triageAudit.lastAttemptAt IS NULL AND triageAudit.createdAt < :cutoff)
        )
      ORDER BY triageAudit.createdAt ASC
      """)
    List<TriageAuditEntity> findStuckPendingForReaping(
            @Param("cutoff") Instant cutoff, Pageable batchLimit);

    List<TriageAuditEntity> findByTenantIdAndGmailMessageIdOrderByDecidedAtDesc(
            UUID tenantId, String gmailMessageId);
}
