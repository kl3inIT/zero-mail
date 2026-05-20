package com.zeromail.core.triage.persistence.lowlevel;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.triage.domain.TriageUndoPolicy;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.AuditLogRow;
import java.sql.Timestamp;
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
                               action_type, reason, decision, external_ref, created_at, applied_at
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
                            resultSet.getString("external_ref"));
                },
                parameters.toArray());
    }
}
