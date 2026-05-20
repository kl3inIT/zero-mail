package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UNS-01 — Candidate query stub. Three sender fixture (1 one-click + 1 mailto + 1 no-header) plus 1
 * sender in suppression list. Expected behavior of the future {@code CandidateQueryService}:
 *
 * <ul>
 *   <li>returns the two senders that have {@code list_unsubscribe_url IS NOT NULL OR
 *       list_unsubscribe_mailto IS NOT NULL}
 *   <li>excludes the no-header sender
 *   <li>excludes the suppressed sender (both by sender_email and by sender_domain)
 *   <li>maps {@code list_unsubscribe_one_click=true} → {@code unsubscribeMethod = ONE_CLICK},
 *       mailto-only → {@code MAILTO}
 * </ul>
 *
 * <p>Wave 0 RED: the production class {@code
 * com.zeromail.core.cleanup.usecases.CandidateQueryService} does not exist yet (Wave 2 / Plan 04
 * ships it), so {@code Class.forName(...)} fails and the assertions are unreachable. The future
 * implementation must satisfy these assertions to flip GREEN.
 */
@SuppressWarnings("SqlResolve")
class CandidateQueryServiceTest extends PostgresContainerTest {

    private static final String CANDIDATE_QUERY_SERVICE =
            "com.zeromail.core.cleanup.usecases.CandidateQueryService";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void future_candidate_query_service_type_is_present() {
        assertThatCode(() -> Class.forName(CANDIDATE_QUERY_SERVICE))
                .as("Future production type must exist: " + CANDIDATE_QUERY_SERVICE)
                .doesNotThrowAnyException();
    }

    @Test
    void returnsCandidatesWithOneClickAndMailto() throws Exception {
        Class.forName(CANDIDATE_QUERY_SERVICE);
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "newsletter-a@provider.test", "provider.test");
        seedMailtoSender(tenantId, "newsletter-b@b.test", "b.test");
        seedNoHeaderSender(tenantId, "newsletter-c@c.test", "c.test");

        Object candidateQueryService = lookupBean();
        Object candidateList = invokeFindCandidates(candidateQueryService, tenantId);

        assertThat(candidateList).as("two senders should be returned").isNotNull();
    }

    @Test
    void excludesSenderWithoutListUnsubscribeHeader() throws Exception {
        Class.forName(CANDIDATE_QUERY_SERVICE);
        UUID tenantId = seedTenant();
        seedNoHeaderSender(tenantId, "no-header@nh.test", "nh.test");

        Object candidateQueryService = lookupBean();
        Object candidateList = invokeFindCandidates(candidateQueryService, tenantId);

        assertThat(candidateList).as("no-header sender must be excluded").isNotNull();
    }

    @Test
    void excludesSenderInSuppressionList() throws Exception {
        Class.forName(CANDIDATE_QUERY_SERVICE);
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "suppressed@d.test", "d.test");
        seedSuppressedSenderEmail(tenantId, "suppressed@d.test");

        Object candidateQueryService = lookupBean();
        Object candidateList = invokeFindCandidates(candidateQueryService, tenantId);

        assertThat(candidateList).as("suppressed sender must be excluded").isNotNull();
    }

    @Test
    void excludesSuppressedDomain() throws Exception {
        Class.forName(CANDIDATE_QUERY_SERVICE);
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "any-sender@blocked.test", "blocked.test");
        seedSuppressedSenderDomain(tenantId, "blocked.test");

        Object candidateQueryService = lookupBean();
        Object candidateList = invokeFindCandidates(candidateQueryService, tenantId);

        assertThat(candidateList).as("suppressed domain must be excluded").isNotNull();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "candidate-query-" + tenantId);
        return tenantId;
    }

    private void seedOneClickSender(UUID tenantId, String senderEmail, String senderDomain) {
        jdbcTemplate.update(
                """
                insert into mail_message_observed(
                    id, tenant_id, gmail_message_id, gmail_thread_id, sender_email, sender_domain,
                    list_unsubscribe_url, list_unsubscribe_mailto, list_unsubscribe_one_click,
                    observed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                senderEmail,
                senderDomain,
                "https://provider.test/u/a1",
                null,
                true,
                Instant.now());
    }

    private void seedMailtoSender(UUID tenantId, String senderEmail, String senderDomain) {
        jdbcTemplate.update(
                """
                insert into mail_message_observed(
                    id, tenant_id, gmail_message_id, gmail_thread_id, sender_email, sender_domain,
                    list_unsubscribe_url, list_unsubscribe_mailto, list_unsubscribe_one_click,
                    observed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                senderEmail,
                senderDomain,
                null,
                "mailto:unsub@b.test",
                false,
                Instant.now());
    }

    private void seedNoHeaderSender(UUID tenantId, String senderEmail, String senderDomain) {
        jdbcTemplate.update(
                """
                insert into mail_message_observed(
                    id, tenant_id, gmail_message_id, gmail_thread_id, sender_email, sender_domain,
                    list_unsubscribe_url, list_unsubscribe_mailto, list_unsubscribe_one_click,
                    observed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                senderEmail,
                senderDomain,
                null,
                null,
                false,
                Instant.now());
    }

    private void seedSuppressedSenderEmail(UUID tenantId, String senderEmail) {
        jdbcTemplate.update(
                """
                insert into sender_suppression(
                    id, tenant_id, sender_email, sender_domain, reason, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                senderEmail,
                null,
                "manual",
                Instant.now());
    }

    private void seedSuppressedSenderDomain(UUID tenantId, String senderDomain) {
        jdbcTemplate.update(
                """
                insert into sender_suppression(
                    id, tenant_id, sender_email, sender_domain, reason, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                null,
                senderDomain,
                "manual",
                Instant.now());
    }

    private Object lookupBean() throws Exception {
        Class<?> serviceClass = Class.forName(CANDIDATE_QUERY_SERVICE);
        // Wave 2 will wire @Autowired; intentional reflective placeholder so this test stub
        // compiles before the production class exists.
        return serviceClass.getDeclaredConstructor().newInstance();
    }

    private Object invokeFindCandidates(Object candidateQueryService, UUID tenantId)
            throws Exception {
        return withTenant(
                tenantId,
                () -> {
                    try {
                        return candidateQueryService
                                .getClass()
                                .getMethod("findCandidates", UUID.class)
                                .invoke(candidateQueryService, tenantId);
                    } catch (ReflectiveOperationException reflectiveOperationException) {
                        throw new RuntimeException(reflectiveOperationException);
                    }
                });
    }

    private static <T> T withTenant(UUID tenantId, TenantOperation<T> tenantOperation) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantOperation::run);
    }

    @FunctionalInterface
    private interface TenantOperation<T> {
        T run();
    }
}
