package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class AssistantActionAuditJpaRepositoryIT extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired AssistantActionAuditJpaRepository assistantActionAuditRepository;

    @Test
    void unified_audit_table_accepts_confirmed_send_and_write_reversible_rows() {
        SeedData seedData = seedChat("audit-unified");

        AssistantActionAuditEntity sendAudit =
                withTenant(
                        seedData.tenantId(),
                        () ->
                                assistantActionAuditRepository.saveAndFlush(
                                        audit(
                                                seedData,
                                                "tool-send",
                                                "confirmed-send",
                                                "sendEmail",
                                                "gmail-message-1",
                                                Instant.now())));
        AssistantActionAuditEntity writeAudit =
                withTenant(
                        seedData.tenantId(),
                        () ->
                                assistantActionAuditRepository.saveAndFlush(
                                        audit(
                                                seedData,
                                                "tool-write",
                                                "write-reversible",
                                                "applyLabel",
                                                null,
                                                Instant.now())));

        assertThat(sendAudit.getGmailMessageId()).isEqualTo("gmail-message-1");
        assertThat(writeAudit.getToolCategory()).isEqualTo("write-reversible");
    }

    @Test
    void duplicate_chat_tool_call_is_rejected_across_categories() {
        SeedData seedData = seedChat("audit-unique");
        withTenant(
                seedData.tenantId(),
                () ->
                        assistantActionAuditRepository.saveAndFlush(
                                audit(
                                        seedData,
                                        "tool-duplicate",
                                        "confirmed-send",
                                        "sendEmail",
                                        "gmail-message-2",
                                        Instant.now())));

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        seedData.tenantId(),
                                        () ->
                                                assistantActionAuditRepository.saveAndFlush(
                                                        audit(
                                                                seedData,
                                                                "tool-duplicate",
                                                                "write-reversible",
                                                                "applyLabel",
                                                                null,
                                                                Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AssistantActionAuditEntity audit(
            SeedData seedData,
            String toolCallId,
            String toolCategory,
            String toolName,
            String gmailMessageId,
            Instant sentAt) {
        return new AssistantActionAuditEntity(
                UUID.randomUUID(),
                seedData.tenantId(),
                seedData.chatId(),
                toolCallId,
                toolCategory,
                toolName,
                "COMMITTED",
                null,
                null,
                gmailMessageId,
                "{}",
                "{}",
                null,
                sentAt);
    }

    private SeedData seedChat(String label) {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, label);
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)", chatId, tenantId, label);
        return new SeedData(tenantId, chatId);
    }

    private static <T> T withTenant(UUID tenantId, TenantOperation<T> tenantOperation) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantOperation::run);
    }

    @FunctionalInterface
    private interface TenantOperation<T> {
        T run();
    }

    private record SeedData(UUID tenantId, UUID chatId) {}
}
