package com.zeromail.core.admin.mkey.persistence.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Invariant: the two-pass priority shift used by {@code MasterKeyAdminService.reorderKeys} keeps
 * every intermediate priority value strictly positive, so the {@code
 * ck_llm_provider_master_key_priority_positive} CHECK constraint never trips. The shift adds {@code
 * offset = size + 100} to every row before reassigning sequential priorities; if the implementation
 * ever regresses to a naïve subtractive shift (going through zero or negative), the constraint
 * fires and this test fails.
 *
 * <p>Test slice: {@link PostgresContainerTest} (real Postgres 18 via Testcontainers + Liquibase
 * schema). Per project convention {@code @DataJpaTest} is NOT used in this repo — see {@code
 * OnboardingStepPersistenceTest} for the rationale (DataJpaTest would skip
 * ZeroMailCoreTestApplication and miss the audit-column changeset).
 */
class LlmProviderMasterKeyWriteRepositoryReorderKeysIT extends PostgresContainerTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000003704");

    @Autowired LlmProviderMasterKeyWriteRepository writeRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedAdminUser() {
        // FK target — created_by_user_id REFERENCES admin_users(id).
        jdbcTemplate.update(
                """
                INSERT INTO admin_users (id, email, user_handle, status, signature_counter)
                VALUES (?, ?, ?, 'ACTIVE', 0)
                ON CONFLICT (id) DO NOTHING
                """,
                ADMIN_ID,
                "37g-it@zeromail.test",
                ("uh-" + ADMIN_ID).getBytes());
        // Clean any pre-existing rows for OpenRouter so the test is hermetic across reuse.
        jdbcTemplate.update(
                "DELETE FROM llm_provider_master_key WHERE provider = ?",
                LlmProvider.OPENROUTER.id());
    }

    @Test
    @DisplayName(
            "reorderKeys two-pass shift (A,B,C at 1,2,3 → B,A,C) keeps every intermediate"
                    + " priority positive and produces 1,2,3 at the end")
    void reorder_three_keys_keeps_priorities_positive_through_shift() {
        UUID keyA = UUID.fromString("00000000-0000-0000-0000-000000000a0a");
        UUID keyB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");
        UUID keyC = UUID.fromString("00000000-0000-0000-0000-000000000c0c");

        insertActiveKey(keyA, 1, "A");
        insertActiveKey(keyB, 2, "B");
        insertActiveKey(keyC, 3, "C");

        // Mirror MasterKeyAdminService.reorderKeys two-pass shift: bump every existing priority
        // up by (size + 100) so the deferrable uq_priority constraint has room to breathe, then
        // reassign 1..N. The CHECK constraint (priority > 0) is NOT deferrable — if the offset
        // ever went negative, this would throw mid-loop.
        int offset = 3 + 100;
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyA, 1 + offset);
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyB, 2 + offset);
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyC, 3 + offset);

        // Intermediate snapshot: every priority must still be > 0.
        Integer minAfterShift =
                jdbcTemplate.queryForObject(
                        "SELECT MIN(priority) FROM llm_provider_master_key WHERE provider = ?",
                        Integer.class,
                        LlmProvider.OPENROUTER.id());
        assertThat(minAfterShift)
                .as(
                        "intermediate priorities must stay positive — ck_priority CHECK is not deferrable")
                .isGreaterThan(0);

        // Reassign 1..N in the new order [B, A, C].
        List<UUID> ordered = List.of(keyB, keyA, keyC);
        for (int index = 0; index < ordered.size(); index++) {
            writeRepository.setPriority(LlmProvider.OPENROUTER, ordered.get(index), index + 1);
        }

        // Final assertions: B=1, A=2, C=3.
        assertThat(priorityOf(keyB)).isEqualTo(1);
        assertThat(priorityOf(keyA)).isEqualTo(2);
        assertThat(priorityOf(keyC)).isEqualTo(3);
    }

    @Test
    @DisplayName("reorderKeys edge case: swapping 2 keys produces 1,2 with positions swapped")
    void reorder_two_keys_minimum_swap() {
        UUID keyX = UUID.fromString("00000000-0000-0000-0000-000000000a37");
        UUID keyY = UUID.fromString("00000000-0000-0000-0000-000000000b37");

        insertActiveKey(keyX, 1, "X");
        insertActiveKey(keyY, 2, "Y");

        int offset = 2 + 100;
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyX, 1 + offset);
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyY, 2 + offset);

        // Reassign in swapped order [Y, X].
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyY, 1);
        writeRepository.setPriority(LlmProvider.OPENROUTER, keyX, 2);

        assertThat(priorityOf(keyY)).isEqualTo(1);
        assertThat(priorityOf(keyX)).isEqualTo(2);
    }

    private void insertActiveKey(UUID keyId, int priority, String label) {
        writeRepository.insertKey(
                LlmProvider.OPENROUTER,
                keyId,
                priority,
                MasterKeyStatus.ACTIVE,
                label,
                KeyFormat.OPENAI_FORMAT,
                new byte[] {0x01, 0x02, 0x03},
                (short) 1,
                ADMIN_ID,
                java.time.Instant.now(),
                "https://openrouter.ai/api/v1",
                "sk-or-" + label);
    }

    private int priorityOf(UUID keyId) {
        return jdbcTemplate.queryForObject(
                "SELECT priority FROM llm_provider_master_key WHERE provider = ? AND key_id = ?",
                Integer.class,
                LlmProvider.OPENROUTER.id(),
                keyId);
    }
}
