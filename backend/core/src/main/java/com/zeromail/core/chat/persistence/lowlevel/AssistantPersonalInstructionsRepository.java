package com.zeromail.core.chat.persistence.lowlevel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads + upsert against {@code assistant_settings.personal_instructions} for the assistant
 * personal-instructions admin use case. Per CONVENTIONS Section 1, the use-case service must not
 * embed SQL.
 */
@Repository
public class AssistantPersonalInstructionsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssistantPersonalInstructionsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public Optional<String> findPersonalInstructions(UUID tenantId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT personal_instructions
                          FROM assistant_settings
                         WHERE tenant_id = ?
                        """,
                        (resultSet, _) -> resultSet.getString("personal_instructions"),
                        tenantId)
                .stream()
                .findFirst();
    }

    public void upsertPersonalInstructions(UUID tenantId, String normalizedInstructions) {
        jdbcTemplate.update(
                """
                INSERT INTO assistant_settings (
                    assistant_settings_id,
                    tenant_id,
                    personal_instructions,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (gen_random_uuid(), ?, ?, now(), now(), 0)
                ON CONFLICT (tenant_id)
                DO UPDATE SET
                    personal_instructions = EXCLUDED.personal_instructions,
                    updated_at = now(),
                    version = assistant_settings.version + 1
                """,
                tenantId,
                normalizedInstructions);
    }
}
