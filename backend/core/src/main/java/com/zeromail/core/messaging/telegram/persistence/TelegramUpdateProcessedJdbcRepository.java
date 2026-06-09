package com.zeromail.core.messaging.telegram.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelegramUpdateProcessedJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TelegramUpdateProcessedJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markProcessedIfAbsent(long updateId) {
        int inserted =
                jdbcTemplate.update(
                        """
                        INSERT INTO telegram_update_processed (update_id, processed_at)
                        VALUES (?, now())
                        ON CONFLICT (update_id) DO NOTHING
                        """,
                        updateId);
        return inserted == 1;
    }
}
