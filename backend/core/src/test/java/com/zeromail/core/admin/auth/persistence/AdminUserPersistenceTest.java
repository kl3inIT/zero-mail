package com.zeromail.core.admin.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminUserPersistenceTest extends PostgresContainerTest {

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void pending_admin_can_be_marked_active_with_webauthn_credential() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000831");
        AdminUserEntity pendingAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "pending-admin@example.com",
                        "Pending Admin",
                        new byte[] {0x21},
                        AdminStatus.PENDING_ENROLLMENT);
        adminUserRepository.save(pendingAdminUser);

        adminUserRepository.markActive(
                adminUserId,
                new byte[] {0x31},
                new byte[] {0x41},
                new byte[] {0x51},
                new byte[] {0x61},
                7L,
                UUID.fromString("00000000-0000-4000-8000-000000000832"),
                "none");

        AdminUserEntity activeAdminUser = adminUserRepository.findById(adminUserId).orElseThrow();
        assertThat(activeAdminUser.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(activeAdminUser.getCredentialId()).containsExactly(0x31);
        assertThat(activeAdminUser.getPublicKeyCose()).containsExactly(0x41);
        assertThat(activeAdminUser.getAttestationObject()).containsExactly(0x51);
        assertThat(activeAdminUser.getAttestationClientDataJson()).containsExactly(0x61);
        assertThat(activeAdminUser.getSignatureCounter()).isEqualTo(7L);
    }

    @Test
    void signature_counter_increment_is_monotonic() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000833");
        AdminUserEntity activeAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "counter-admin@example.com",
                        "Counter Admin",
                        new byte[] {0x22},
                        AdminStatus.ACTIVE);
        activeAdminUser.activate(
                new byte[] {0x32},
                new byte[] {0x42},
                new byte[] {0x52},
                new byte[] {0x62},
                10L,
                UUID.fromString("00000000-0000-4000-8000-000000000834"),
                "none");
        adminUserRepository.save(activeAdminUser);

        int updatedRows =
                adminUserRepository.incrementSignCounter(
                        adminUserId, 11L, Instant.parse("2026-05-19T18:36:17Z"));
        Optional<AdminUserEntity> reloadedAdminUser =
                adminUserRepository.findByCredentialId(new byte[] {0x32});

        assertThat(updatedRows).isEqualTo(1);
        assertThat(reloadedAdminUser).isPresent();
        assertThat(reloadedAdminUser.orElseThrow().getSignatureCounter()).isEqualTo(11L);
    }

    @Test
    void hard_delete_keeps_admin_history_without_blocking_the_admin_user_row_delete() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000835");
        AdminUserEntity activeAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "delete-history-admin@example.com",
                        "Delete History Admin",
                        new byte[] {0x23},
                        AdminStatus.ACTIVE);
        adminUserRepository.saveAndFlush(activeAdminUser);
        jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET created_by_user_id = ? WHERE provider = 'OPENAI'",
                adminUserId);
        jdbcTemplate.update(
                """
                INSERT INTO admin_read_event(
                    id, actor_user_id, actor_email, action, target_kind, target_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString("00000000-0000-4000-8000-000000000836"),
                adminUserId,
                "delete-history-admin@example.com",
                "TENANT_DETAIL_VIEWED",
                "TENANT",
                UUID.fromString("00000000-0000-4000-8000-000000000837"));
        jdbcTemplate.update(
                """
                INSERT INTO admin_audit_event(
                    id,
                    actor_user_id,
                    actor_email,
                    action,
                    target_kind,
                    target_id,
                    before_state_json,
                    after_state_json,
                    reason,
                    request_ip,
                    request_id,
                    canonical_timestamp_ms,
                    hmac_chain_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::inet, ?, ?, ?)
                """,
                UUID.fromString("00000000-0000-4000-8000-000000000838"),
                adminUserId,
                "delete-history-admin@example.com",
                "ADMIN_DELETED",
                "admin_user",
                adminUserId,
                "{\"email\":\"delete-history-admin@example.com\"}",
                null,
                "hard delete test",
                "127.0.0.1",
                UUID.fromString("00000000-0000-4000-8000-000000000839"),
                1L,
                new byte[32]);

        adminUserRepository.deleteById(adminUserId);
        adminUserRepository.flush();

        assertThat(adminUserRepository.findById(adminUserId)).isEmpty();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT actor_user_id FROM admin_audit_event WHERE target_id = ?",
                                UUID.class,
                                adminUserId))
                .isEqualTo(adminUserId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT actor_email FROM admin_audit_event WHERE target_id = ?",
                                String.class,
                                adminUserId))
                .isEqualTo("delete-history-admin@example.com");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT actor_user_id IS NULL
                                FROM admin_read_event
                                WHERE actor_email = 'delete-history-admin@example.com'
                                """,
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT created_by_user_id IS NULL
                                FROM llm_provider_master_key
                                WHERE provider = 'OPENAI'
                                """,
                                Boolean.class))
                .isTrue();
    }
}
