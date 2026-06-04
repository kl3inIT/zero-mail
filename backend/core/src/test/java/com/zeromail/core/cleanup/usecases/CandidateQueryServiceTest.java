package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UNS-01 — Candidate query (Wave 2 / Plan 04, flipped from Wave 0 RED stub to GREEN).
 *
 * <p>Three sender fixture (1 one-click + 1 mailto + 1 no-header) plus 1 sender in suppression list.
 * Expected behavior of {@link CandidateQueryService}:
 *
 * <ul>
 *   <li>returns the two senders that have {@code list_unsubscribe_url IS NOT NULL OR
 *       list_unsubscribe_mailto IS NOT NULL}
 *   <li>excludes the no-header sender
 *   <li>excludes the suppressed sender (both by sender_email and by sender_domain)
 *   <li>maps {@code list_unsubscribe_one_click=true} → {@code unsubscribeMethod = ONE_CLICK},
 *       mailto-only → {@code MAILTO}
 * </ul>
 */
@SuppressWarnings("SqlResolve")
class CandidateQueryServiceTest extends PostgresContainerTest {

    private static final Duration WINDOW = Duration.ofDays(30);
    private static final int LIMIT = 50;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired CandidateQueryService candidateQueryService;

    @Test
    void future_candidate_query_service_type_is_present() {
        assertThatCode(
                        () ->
                                Class.forName(
                                        "com.zeromail.core.cleanup.usecases.CandidateQueryService"))
                .as("Production type must exist")
                .doesNotThrowAnyException();
    }

    @Test
    void returnsCandidatesWithOneClickAndMailto() {
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "newsletter-a@provider.test", "provider.test");
        seedMailtoSender(tenantId, "newsletter-b@b.test", "b.test");
        seedNoHeaderSender(tenantId, "newsletter-c@c.test", "c.test");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates)
                .as("two senders with List-Unsubscribe headers should be returned")
                .hasSize(2)
                .extracting(UnsubscribeCandidateProjection::senderEmail)
                .containsExactlyInAnyOrder("newsletter-a@provider.test", "newsletter-b@b.test");
        assertThat(candidates)
                .filteredOn(c -> c.senderEmail().equals("newsletter-a@provider.test"))
                .extracting(UnsubscribeCandidateProjection::unsubscribeMethod)
                .containsExactly(UnsubscribeMethod.ONE_CLICK);
        assertThat(candidates)
                .filteredOn(c -> c.senderEmail().equals("newsletter-b@b.test"))
                .extracting(UnsubscribeCandidateProjection::unsubscribeMethod)
                .containsExactly(UnsubscribeMethod.MAILTO);
    }

    @Test
    void excludesSenderWithoutListUnsubscribeHeader() {
        UUID tenantId = seedTenant();
        seedNoHeaderSender(tenantId, "no-header@nh.test", "nh.test");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).as("no-header sender must be excluded").isEmpty();
    }

    @Test
    void excludesSenderInSuppressionList() {
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "suppressed@d.test", "d.test");
        seedSuppressedSenderEmail(tenantId, "suppressed@d.test");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).as("suppressed sender must be excluded").isEmpty();
    }

    @Test
    void excludesSuppressedDomain() {
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "any-sender@blocked.test", "blocked.test");
        seedSuppressedSenderDomain(tenantId, "blocked.test");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).as("suppressed domain must be excluded").isEmpty();
    }

    @Test
    void senderNameAggregatesIntoCandidateRow() {
        UUID tenantId = seedTenant();
        seedObservedRow(
                tenantId, "john@brand.test", "John from Brand", "https://brand.test/u", null, true);
        seedObservedRow(tenantId, "john@brand.test", null, "https://brand.test/u", null, true);

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().senderName())
                .as("display-name flows through MAX aggregate even when other rows have NULL")
                .isEqualTo("John from Brand");
    }

    @Test
    void senderRowWithoutDisplayNameReturnsNull() {
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "noname@brand.test", "brand.test");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().senderName()).isNull();
    }

    @Test
    void excludesSenderAlreadyQueuedForUnsubscribe() {
        UUID tenantId = seedTenant();
        seedOneClickSender(tenantId, "queued@provider.test", "provider.test");
        seedUnsubscribeAttempt(tenantId, "queued@provider.test", "provider.test", "PENDING");

        List<UnsubscribeCandidateProjection> candidates =
                candidateQueryService.findCandidates(tenantId, WINDOW, LIMIT);

        assertThat(candidates).as("queued unsubscribe sender must be hidden from list").isEmpty();
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
        seedObservedRow(tenantId, senderEmail, "https://" + senderDomain + "/u/a1", null, true);
    }

    private void seedMailtoSender(UUID tenantId, String senderEmail, String senderDomain) {
        seedObservedRow(tenantId, senderEmail, null, "mailto:unsub@" + senderDomain, false);
    }

    private void seedNoHeaderSender(UUID tenantId, String senderEmail, String senderDomain) {
        seedObservedRow(tenantId, senderEmail, null, null, false);
    }

    private void seedObservedRow(
            UUID tenantId,
            String senderEmail,
            String listUnsubscribeUrl,
            String listUnsubscribeMailto,
            boolean listUnsubscribeOneClick) {
        seedObservedRow(
                tenantId,
                senderEmail,
                null,
                listUnsubscribeUrl,
                listUnsubscribeMailto,
                listUnsubscribeOneClick);
    }

    private void seedObservedRow(
            UUID tenantId,
            String senderEmail,
            String senderName,
            String listUnsubscribeUrl,
            String listUnsubscribeMailto,
            boolean listUnsubscribeOneClick) {
        jdbcTemplate.update(
                """
                        insert into mail_message_observed(
                            tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids,
                            sender_email, sender_name, list_unsubscribe_url, list_unsubscribe_mailto,
                            list_unsubscribe_one_click, observed_at)
                        values (?, ?, ?, ?, ARRAY[]::text[], ?, ?, ?, ?, ?, ?)
                        """,
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                System.currentTimeMillis(),
                senderEmail,
                senderName,
                listUnsubscribeUrl,
                listUnsubscribeMailto,
                listUnsubscribeOneClick,
                java.sql.Timestamp.from(Instant.now()));
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
                java.sql.Timestamp.from(Instant.now()));
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
                java.sql.Timestamp.from(Instant.now()));
    }

    private void seedUnsubscribeAttempt(
            UUID tenantId, String senderEmail, String senderDomain, String state) {
        UUID campaignId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into unsubscribe_campaign(
                            id, tenant_id, status, total_sender_count, total_history_message_count)
                        values (?, ?, ?, ?, ?)
                        """,
                campaignId,
                tenantId,
                state.equals("PENDING") ? "QUEUED" : "COMPLETED",
                1,
                1);
        jdbcTemplate.update(
                """
                        insert into unsubscribe_attempt(
                            id, campaign_id, sender_email, sender_domain, unsubscribe_method,
                            state, archived_message_count)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                campaignId,
                senderEmail,
                senderDomain,
                "ONE_CLICK",
                state,
                state.equals("OK") ? 1 : 0);
    }
}
