package com.zeromail.core.triage.persistence.lowlevel;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.domain.TriageUndoPolicy;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.AuditLogRow;
import com.zeromail.core.triage.projection.DigestSourceItem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC read access to {@code triage_audit} for the user-facing audit log. Pure SQL execution +
 * row mapping — pagination cursor parsing/encoding stays in {@code AuditLogQueryService}.
 */
@Repository
public class AuditLogReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<AuditLogRow> findPage(
            UUID tenantId, AuditLogPageQuery pageQuery, Optional<KeysetCursor> decodedCursor) {
        ArrayList<Object> parameters = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        """
                        select audit_id, gmail_thread_id, gmail_message_id,
                               sanitized_subject, sanitized_sender_email,
                               rule_name_snapshot,
                               action_type, reason, decision, external_ref, created_at, applied_at,
                               blocked_by_safety_net_pattern
                        from triage_audit
                        where tenant_id = ?
                        """);
        parameters.add(tenantId);
        if (pageQuery.action() != null) {
            sql.append(" and action_type = ?");
            parameters.add(pageQuery.action());
        }
        if (pageQuery.since() != null) {
            sql.append(" and created_at >= ?");
            parameters.add(Timestamp.from(pageQuery.since()));
        }
        if (pageQuery.until() != null) {
            sql.append(" and created_at < ?");
            parameters.add(Timestamp.from(pageQuery.until()));
        }
        if (decodedCursor.isPresent()) {
            KeysetCursor cursor = decodedCursor.orElseThrow();
            sql.append(" and (created_at, audit_id) < (?, ?)");
            parameters.add(Timestamp.from(cursor.timestamp()));
            parameters.add(UUID.fromString(cursor.id()));
        }
        sql.append(" order by created_at desc, audit_id desc limit ?");
        parameters.add(pageQuery.limit() + 1);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, _) -> {
                    Timestamp appliedAtTimestamp = resultSet.getTimestamp("applied_at");
                    return new AuditLogRow(
                            UUID.fromString(resultSet.getString("audit_id")),
                            resultSet.getString("gmail_thread_id"),
                            resultSet.getString("gmail_message_id"),
                            resultSet.getString("sanitized_subject"),
                            resultSet.getString("sanitized_sender_email"),
                            resultSet.getString("rule_name_snapshot"),
                            resultSet.getString("action_type"),
                            resultSet.getString("reason"),
                            resultSet.getString("decision"),
                            resultSet.getTimestamp("created_at").toInstant(),
                            appliedAtTimestamp == null
                                    ? null
                                    : TriageUndoPolicy.undoableUntil(
                                            appliedAtTimestamp.toInstant()),
                            resultSet.getString("external_ref"),
                            resultSet.getString("blocked_by_safety_net_pattern"));
                },
                parameters.toArray());
    }

    /**
     * Reads the privacy-clean digest pointers a tenant accumulated within a window: every {@code
     * add_to_digest} rule write that was applied and not later reverted. Selects only sanitized
     * audit metadata (subject excerpt, sender email, rule-name snapshot) — never {@code
     * action_args_json} or any body column — so the weekly content digest can group messages by
     * rule and fetch their bodies fresh from Gmail at send time.
     */
    public List<DigestSourceItem> findDigestSourceItems(
            UUID tenantId, Instant since, Instant until, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(since, "since must not be null");
        Objects.requireNonNull(until, "until must not be null");
        String sql =
                """
                select gmail_message_id, gmail_thread_id, sanitized_subject,
                       sanitized_sender_email, rule_name_snapshot, applied_at
                from triage_audit
                where tenant_id = ?
                  and action_type = ?
                  and decision = ?
                  and reverted_at is null
                  and applied_at >= ?
                  and applied_at < ?
                order by applied_at desc
                limit ?
                """;
        return jdbcTemplate.query(
                sql,
                (resultSet, _) ->
                        new DigestSourceItem(
                                resultSet.getString("gmail_message_id"),
                                resultSet.getString("gmail_thread_id"),
                                resultSet.getString("sanitized_subject"),
                                resultSet.getString("sanitized_sender_email"),
                                resultSet.getString("rule_name_snapshot"),
                                resultSet.getTimestamp("applied_at").toInstant()),
                tenantId,
                RuleActionType.ADD_TO_DIGEST.id(),
                TriageDecision.APPLIED.id(),
                Timestamp.from(since),
                Timestamp.from(until),
                limit);
    }
}
