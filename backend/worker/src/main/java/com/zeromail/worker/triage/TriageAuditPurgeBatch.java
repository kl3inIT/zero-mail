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
public class TriageAuditPurgeBatch {

  private static final Duration AUDIT_RETENTION = Duration.ofDays(30);

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public TriageAuditPurgeBatch(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public int purgeExpiredOnce(int batchLimit) {
    if (batchLimit <= 0) {
      throw new IllegalArgumentException("batchLimit must be positive");
    }

    Instant cutoff = clock.instant().minus(AUDIT_RETENTION);
    return jdbcTemplate.update(
        """
        WITH expired_audit AS (
          SELECT audit_id
            FROM triage_audit
           WHERE decided_at < ?
             AND decision IN (
               'APPLIED',
               'REVERTED',
               'SHADOW_LOGGED',
               'REJECTED_BY_SAFETY_NET',
               'REJECTED_BY_SAFETY_POLICY',
               'FAILED'
             )
           ORDER BY decided_at ASC, audit_id ASC
           LIMIT ?
             FOR UPDATE SKIP LOCKED
        )
        DELETE FROM triage_audit
         WHERE audit_id IN (SELECT audit_id FROM expired_audit)
        """,
        Timestamp.from(cutoff),
        batchLimit);
  }
}
