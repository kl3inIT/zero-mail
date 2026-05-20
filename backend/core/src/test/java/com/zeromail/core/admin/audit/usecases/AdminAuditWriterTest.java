package com.zeromail.core.admin.audit.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AdminAuditWriterTest extends PostgresContainerTest {

    private static final UUID ADMIN_USER_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000861");
    private static final AdminUser ADMIN_USER =
            new AdminUser(
                    ADMIN_USER_ID,
                    "audit-writer-admin@example.com",
                    AdminStatus.ACTIVE,
                    Optional.of("Audit Writer"));

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private AdminAuditWriter adminAuditWriter;

    @Autowired private AdminAuditChainVerifier adminAuditChainVerifier;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetAdminAuditTables() {
        jdbcTemplate.execute("DELETE FROM admin_read_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_audit_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_users");
        adminUserRepository.save(
                new AdminUserEntity(
                        ADMIN_USER_ID,
                        ADMIN_USER.email(),
                        "Audit Writer",
                        new byte[] {0x71},
                        AdminStatus.ACTIVE));
    }

    @Test
    void append_requires_admin_context() {
        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        _ ->
                                                adminAuditWriter.append(
                                                        AdminAuditAction.ADMIN_GRANTED,
                                                        "ADMIN_USER",
                                                        UUID.randomUUID(),
                                                        "{}",
                                                        "{}",
                                                        "grant reason",
                                                        "127.0.0.1",
                                                        UUID.randomUUID())));
    }

    @Test
    void append_chains_rows_and_verifier_detects_first_tampered_chain_index() {
        transactionTemplate.executeWithoutResult(
                _ ->
                        AdminContext.run(
                                ADMIN_USER,
                                () -> {
                                    for (int rowIndex = 1; rowIndex <= 100; rowIndex++) {
                                        adminAuditWriter.append(
                                                AdminAuditAction.ADMIN_GRANTED,
                                                "ADMIN_USER",
                                                UUID.randomUUID(),
                                                "{\"before\":\"hidden\"}",
                                                "{\"after\":\"hidden\"}",
                                                "grant reason " + rowIndex,
                                                "127.0.0.1",
                                                UUID.randomUUID());
                                    }
                                }));

        assertThat(adminAuditChainVerifier.verifyOnce()).isEmpty();

        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.update(
                "UPDATE admin_audit_event SET reason = ? WHERE chain_index = ?",
                "tampered reason",
                50L);
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");

        OptionalLong mismatch = adminAuditChainVerifier.verifyOnce();
        assertThat(mismatch).hasValue(50L);
    }

    @Test
    void surrounding_transaction_rollback_removes_audit_row() {
        Long beforeCount =
                jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_event", Long.class);

        transactionTemplate.executeWithoutResult(
                transactionStatus -> {
                    AdminContext.run(
                            ADMIN_USER,
                            () ->
                                    adminAuditWriter.append(
                                            AdminAuditAction.ADMIN_GRANTED,
                                            "ADMIN_USER",
                                            UUID.randomUUID(),
                                            "{}",
                                            "{}",
                                            "rollback reason",
                                            "127.0.0.1",
                                            UUID.randomUUID()));
                    transactionStatus.setRollbackOnly();
                });

        Long afterCount =
                jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_event", Long.class);
        assertThat(afterCount).isEqualTo(beforeCount);
    }

    @Test
    void read_events_are_written_without_hmac_chain() {
        UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000000862");

        transactionTemplate.executeWithoutResult(
                _ ->
                        adminAuditWriter.writeReadEvent(
                                ADMIN_USER, "TENANT_DETAIL_VIEWED", "TENANT", tenantId));

        Long readEventCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM admin_read_event WHERE target_id = ?",
                        Long.class,
                        tenantId);
        assertThat(readEventCount).isEqualTo(1L);
    }
}
