package com.zeromail.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.controllers.account.AccountDeletionController;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionEntity;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class AccountDeletionE2ETest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired GmailConnectionRepository conns;
    @Autowired OnboardingSelectionRepository onboarding;
    @Autowired AccountDeletionController deletion;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void delete_cascades_all_tenant_rows() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            users.save(
                                    new UserEntity(
                                            UUID.randomUUID(), tenantId, "gs-1", "a@example.com"));
                            onboarding.save(
                                    new OnboardingSelectionEntity(
                                            UUID.randomUUID(), tenantId, "archive-receipts"));
                            UUID gmailConnectionId = UUID.randomUUID();
                            var gc =
                                    new GmailConnectionEntity(
                                            gmailConnectionId,
                                            tenantId,
                                            "a@example.com",
                                            GmailConnectionStatus.CONNECTED);
                            gc.setConnectedAt(Instant.now());
                            gc.setRefreshTokenEncrypted(new byte[] {1, 2, 3});
                            conns.save(gc);
                            seedTenantRuntimeRows(tenantId, gmailConnectionId);

                            deletion.deleteAccount(new MockHttpServletRequest());

                            assertThat(onboarding.findByTenantId(tenantId)).isEmpty();
                            assertThat(conns.findByTenantId(tenantId)).isEmpty();
                            assertThat(users.findFirstByTenantId(tenantId)).isEmpty();
                            assertTenantRowsDeleted(tenantId);
                        });
        assertThat(tenants.findById(tenantId)).isEmpty();
    }

    private void seedTenantRuntimeRows(UUID tenantId, UUID gmailConnectionId) {
        jdbcTemplate.update(
                """
                INSERT INTO rules(
                    id, tenant_id, gmail_connection_id, display_name, source_text, source_language,
                    schema_version, matcher_ast, action_intents, enabled, order_index)
                VALUES (?, ?, ?, 'Delete test rule', 'archive receipts', 'en', 'rules.v1',
                        '{}'::jsonb, '[]'::jsonb, true, 0)
                """,
                UUID.randomUUID(),
                tenantId,
                gmailConnectionId);
        jdbcTemplate.update(
                """
                INSERT INTO mail_message_observed(
                    tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id, history_id,
                    label_ids)
                VALUES (?, ?, 'message-1', 'thread-1', 100, ARRAY['INBOX']::text[])
                """,
                tenantId,
                gmailConnectionId);
        jdbcTemplate.update(
                """
                INSERT INTO gmail_inbox_projection(
                    tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id,
                    sender_email_hash, sender_email_ciphertext, received_at, source_history_id,
                    expires_at)
                VALUES (?, ?, 'message-1', 'thread-1', ?, ?, now(), 100, now() + INTERVAL '7 days')
                """,
                tenantId,
                gmailConnectionId,
                new byte[] {1},
                new byte[] {2});
        jdbcTemplate.update(
                """
                INSERT INTO llm_call_audit(
                    id, tenant_id, provider, feature, model_id, credential_source)
                VALUES (?, ?, 'OPENROUTER', 'CHAT', 'test-model', 'PLATFORM')
                """,
                UUID.randomUUID(),
                tenantId);
    }

    private void assertTenantRowsDeleted(UUID tenantId) {
        assertThat(countRows("rules", tenantId)).isZero();
        assertThat(countRows("mail_message_observed", tenantId)).isZero();
        assertThat(countRows("gmail_inbox_projection", tenantId)).isZero();
        assertThat(countRows("llm_call_audit", tenantId)).isZero();
    }

    private int countRows(String tableName, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE tenant_id = ?",
                Integer.class,
                tenantId);
    }
}
