package com.zeromail.core.gmail.persistence.lowlevel;

import com.zeromail.core.gmail.projection.ObservedPreviewMessage;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads against {@code mail_message_observed} for the Gmail preview-read use case. Per
 * CONVENTIONS Section 1, the use-case service must not embed SQL.
 */
@Repository
public class MailMessageObservedReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailMessageObservedReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<ObservedPreviewMessage> findRecentObservedMessages(UUID tenantId, int sampleSize) {
        return jdbcTemplate.query(
                """
                SELECT gmail_message_id, gmail_thread_id, label_ids, internal_date, observed_at
                FROM mail_message_observed
                WHERE tenant_id = ?
                ORDER BY internal_date DESC NULLS LAST, observed_at DESC
                LIMIT ?
                """,
                (resultSet, _) -> {
                    Array labelIdsArray = resultSet.getArray("label_ids");
                    String[] labelIds =
                            labelIdsArray == null
                                    ? new String[0]
                                    : (String[]) labelIdsArray.getArray();
                    Timestamp observedAtTimestamp = resultSet.getTimestamp("observed_at");
                    Instant observedAt =
                            observedAtTimestamp == null
                                    ? Instant.EPOCH
                                    : observedAtTimestamp.toInstant();
                    return new ObservedPreviewMessage(
                            resultSet.getString("gmail_message_id"),
                            resultSet.getString("gmail_thread_id"),
                            labelIds,
                            resultSet.getObject("internal_date", Long.class),
                            observedAt);
                },
                tenantId,
                sampleSize);
    }
}
