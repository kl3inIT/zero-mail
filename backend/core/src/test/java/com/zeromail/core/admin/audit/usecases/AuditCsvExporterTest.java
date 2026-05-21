package com.zeromail.core.admin.audit.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.support.PostgresContainerTest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AuditCsvExporterTest extends PostgresContainerTest {

    private static final UUID ADMIN_USER_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000871");
    private static final AdminUser ADMIN_USER =
            new AdminUser(
                    ADMIN_USER_ID,
                    "audit-csv-admin@example.com",
                    AdminStatus.ACTIVE,
                    Optional.empty());

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private AdminAuditWriter adminAuditWriter;

    @Autowired private AuditCsvExporter auditCsvExporter;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetAdminAuditTables() {
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
                        "Audit Csv",
                        new byte[] {0x72},
                        AdminStatus.ACTIVE));
    }

    @Test
    void csv_exporter_streams_header_and_rows_without_json_payloads() throws Exception {
        transactionTemplate.executeWithoutResult(
                _ ->
                        AdminContext.run(
                                ADMIN_USER,
                                () -> {
                                    for (int rowIndex = 0; rowIndex < 5; rowIndex++) {
                                        adminAuditWriter.append(
                                                AdminAuditAction.ADMIN_GRANTED,
                                                "ADMIN_USER",
                                                UUID.randomUUID(),
                                                "{\"secret\":\"before-json-payload-should-not-export\"}",
                                                "{\"secret\":\"after-json-payload-should-not-export\"}",
                                                "csv reason",
                                                "127.0.0.1",
                                                UUID.randomUUID());
                                    }
                                }));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        auditCsvExporter.streamCsv(AdminAuditPageQuery.firstPage(10), outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv.lines()).hasSize(6);
        assertThat(csv)
                .contains(
                        "audit_id,actor_email,action,target_kind,target_id,reason,request_ip,created_at_iso");
        assertThat(csv).doesNotContain("before-json-payload").doesNotContain("after-json-payload");
    }
}
