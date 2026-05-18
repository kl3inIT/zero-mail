package com.zeromail.core.chat.usecases;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("SqlResolve")
public class AssistantPersonalInstructionsService {

    private static final int MAX_PERSONAL_INSTRUCTIONS_LENGTH = 2_000;

    private final JdbcTemplate jdbcTemplate;

    public AssistantPersonalInstructionsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public InstructionUpdateResult overwrite(UUID tenantId, String personalInstructions) {
        String normalizedInstructions =
                AssistantMemoryService.requireBoundedText(
                        personalInstructions,
                        "personalInstructions",
                        MAX_PERSONAL_INSTRUCTIONS_LENGTH);
        String previousInstructions =
                jdbcTemplate
                        .query(
                                """
                                SELECT personal_instructions
                                  FROM assistant_settings
                                 WHERE tenant_id = ?
                                """,
                                (resultSet, _) -> resultSet.getString("personal_instructions"),
                                tenantId)
                        .stream()
                        .findFirst()
                        .orElse(null);
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
        return new InstructionUpdateResult(
                previousInstructions == null ? 0 : previousInstructions.length(),
                normalizedInstructions.length());
    }

    public record InstructionUpdateResult(int beforeLength, int afterLength) {

        public Map<String, Object> toSummary() {
            return Map.of("before_length", beforeLength, "after_length", afterLength);
        }
    }
}
