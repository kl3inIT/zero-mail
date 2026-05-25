package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.cleanup.persistence.SenderSuppressionEntity;
import com.zeromail.core.cleanup.persistence.SenderSuppressionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-add heuristic for the sender suppression list (UNS-02, D-09).
 *
 * <p>Policy: a sender the user has actively replied to within the last 90 days should never be
 * cleanup-archived again, even if rules later mark them as bulk. This service scans {@code
 * triage_audit} for {@code action_type = 'SAVE_DRAFT'} rows in {@code APPLIED} state inside the
 * 90-day window and inserts a {@code sender_suppression} row with {@code reason = REPLIED} for
 * every distinct {@code sanitized_sender_email} not already on the suppression list.
 *
 * <p>SAVE_DRAFT as the reply signal: the v1 write surface limits us to {@code label}, {@code
 * archive}, {@code save_draft} (CLAUDE.md). A drafted reply is the strongest local proxy for "user
 * engaged with this sender" — auto-send is forbidden, so reply intent surfaces as a saved draft
 * followed by user-initiated send-from-Gmail. The 90-day window matches the product CONTEXT D-09 /
 * UNS-02 lookback policy.
 *
 * <p>Idempotency: the inner SQL anti-joins {@code sender_suppression} so previously auto-suppressed
 * senders are skipped. Re-running on the same tenant adds zero new rows.
 *
 * <p>Privacy invariant: this service never logs raw {@code senderEmail} values. Only the inserted
 * count + tenantId scoped to the {@code event=cleanup_suppression_auto_added} log line.
 */
@Service
@Transactional
public class SuppressionAutoAddService {

    private static final Logger log = LoggerFactory.getLogger(SuppressionAutoAddService.class);

    private static final Duration REPLY_LOOKBACK_WINDOW = Duration.ofDays(90);

    /**
     * Distinct senders the tenant has replied to (via SAVE_DRAFT/APPLIED) inside the 90-day window,
     * excluding senders already on the suppression list.
     *
     * <p>The {@code action_type = 'SAVE_DRAFT'} + {@code decision = 'APPLIED'} pair is the
     * canonical "user reacted by drafting a reply" signal — see class Javadoc.
     */
    private static final String REPLIED_SENDERS_SQL =
            """
            SELECT DISTINCT ta.sanitized_sender_email
            FROM triage_audit ta
            WHERE ta.tenant_id = ?
              AND ta.action_type = 'SAVE_DRAFT'
              AND ta.decision = 'APPLIED'
              AND ta.decided_at >= ?
              AND ta.sanitized_sender_email IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM sender_suppression ss
                  WHERE ss.tenant_id = ta.tenant_id
                    AND ss.sender_email = ta.sanitized_sender_email
              )
            """;

    private final SenderSuppressionRepository senderSuppressionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SuppressionAutoAddService(
            SenderSuppressionRepository senderSuppressionRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.senderSuppressionRepository =
                Objects.requireNonNull(
                        senderSuppressionRepository,
                        "senderSuppressionRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Scan the tenant's recent reply audit + insert {@code reason = REPLIED} suppression rows for
     * every newly-detected reply target. Returns the number of new rows persisted.
     */
    public int scanAndAutoAdd(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Instant lookbackCutoff = clock.instant().minus(REPLY_LOOKBACK_WINDOW);
        List<String> repliedSenderEmails =
                jdbcTemplate.query(
                        REPLIED_SENDERS_SQL,
                        (resultSet, rowNumber) -> resultSet.getString("sanitized_sender_email"),
                        tenantId,
                        Timestamp.from(lookbackCutoff));
        if (repliedSenderEmails.isEmpty()) {
            log.info(
                    "event=cleanup_suppression_auto_added tenantId={} count=0 reason=replied",
                    tenantId);
            return 0;
        }

        int insertedCount = 0;
        for (String repliedSenderEmail : repliedSenderEmails) {
            if (repliedSenderEmail == null || repliedSenderEmail.isBlank()) {
                continue;
            }
            SenderSuppressionEntity entity =
                    new SenderSuppressionEntity(
                            UUID.randomUUID(),
                            tenantId,
                            repliedSenderEmail,
                            null,
                            SuppressionReason.REPLIED);
            senderSuppressionRepository.save(entity);
            insertedCount++;
        }
        log.info(
                "event=cleanup_suppression_auto_added tenantId={} count={} reason=replied",
                tenantId,
                insertedCount);
        return insertedCount;
    }
}
