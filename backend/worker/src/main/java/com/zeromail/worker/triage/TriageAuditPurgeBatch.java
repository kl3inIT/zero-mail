package com.zeromail.worker.triage;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional batch collaborator for the triage audit retention purge. */
@Component
@SuppressWarnings("SqlResolve")
public class TriageAuditPurgeBatch {

    private static final Duration AUDIT_RETENTION = Duration.ofDays(30);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public TriageAuditPurgeBatch(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PurgeBatchResult purgeExpiredOnce(int batchLimit) {
        if (batchLimit <= 0) {
            throw new IllegalArgumentException("batchLimit must be positive");
        }

        Instant cutoff = clock.instant().minus(AUDIT_RETENTION);
        return jdbcTemplate.queryForObject(
                """
        WITH expired_audit AS (
          SELECT audit_id
            FROM triage_audit
           WHERE decided_at < ?
             AND decision IN (
               'APPLIED',
               'REVERTED',
               'REJECTED_BY_SAFETY_NET',
               'REJECTED_BY_SAFETY_POLICY',
               'FAILED'
             )
           ORDER BY decided_at ASC, audit_id ASC
           LIMIT ?
             FOR UPDATE SKIP LOCKED
        ),
        deleted_audit AS (
          DELETE FROM triage_audit
           WHERE audit_id IN (SELECT audit_id FROM expired_audit)
           RETURNING audit_id
        )
        SELECT
          (SELECT COUNT(*) FROM expired_audit) AS selected_count,
          (SELECT COUNT(*) FROM deleted_audit) AS deleted_count
        """,
                (resultSet, rowNumber) ->
                        new PurgeBatchResult(
                                resultSet.getInt("selected_count"),
                                resultSet.getInt("deleted_count")),
                Timestamp.from(cutoff),
                batchLimit);
    }

    public record PurgeBatchResult(int selectedCount, int deletedCount) {}
}
