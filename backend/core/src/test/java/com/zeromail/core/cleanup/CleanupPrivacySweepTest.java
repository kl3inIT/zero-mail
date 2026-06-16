package com.zeromail.core.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.cleanup.usecases.CampaignExecuteService;
import com.zeromail.core.cleanup.usecases.CampaignExecuteService.CampaignExecuteResult;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UNS-09 — Privacy sweep for the cleanup module. Mirrors {@code TriagePrivacySweepTest}: seed
 * sensitive sentinel tokens through the unsubscribe campaign flow (URL token, mailto subject, full
 * sender email) and assert no log line or metric tag leaks them. {@link SensitiveMarkerScrubFilter}
 * is attached to the {@link ListAppender} to mimic production logback scrubbing.
 *
 * <p>Wave 8 (Plan 09) — flips GREEN. The {@link CampaignExecuteService} bean now exists, so we use
 * Spring DI instead of reflection. The test seeds one observed mail row whose {@code
 * list_unsubscribe_url}, {@code list_unsubscribe_mailto}, and {@code sender_email} contain raw
 * sentinel tokens, then calls {@code execute(...)} and asserts no captured log line or Micrometer
 * tag echoes them.
 *
 * <p>The privacy contract is the strictest in the codebase — only {@code senderDomain} is allowed
 * to appear in logs (per CONVENTIONS §5). Full {@code senderEmail}, full unsubscribe URL token,
 * mailto subject token, raw subject body, raw body — all forbidden everywhere a user might see
 * (logs, audit, metrics).
 */
@Import(CleanupPrivacySweepTest.MeterRegistryTestConfiguration.class)
@SuppressWarnings("SqlResolve")
class CleanupPrivacySweepTest extends PostgresContainerTest {

    private static final String CAMPAIGN_EXECUTE_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignExecuteService";

    private static final String RAW_SENDER_EMAIL = "newsletter.sender@private.test";
    private static final String SENDER_DISPLAY_NAME_SENTINEL =
            "CLEANUP_SENDER_DISPLAY_NAME_SENTINEL_08_01";
    private static final String EMAIL_SUBJECT_SENTINEL = "CLEANUP_EMAIL_SUBJECT_SENTINEL_08_01";
    private static final String EMAIL_BODY_SENTINEL = "CLEANUP_EMAIL_BODY_SENTINEL_08_01";
    private static final String RAW_UNSUBSCRIBE_URL_TOKEN = "URL_TOKEN_SENTINEL_08_01";
    private static final String RAW_MAILTO_SUBJECT_TOKEN = "MAILTO_SUBJECT_SENTINEL_08_01";
    private static final List<String> FORBIDDEN_CONTENT_TOKENS =
            List.of(
                    SENDER_DISPLAY_NAME_SENTINEL,
                    EMAIL_SUBJECT_SENTINEL,
                    EMAIL_BODY_SENTINEL,
                    RAW_SENDER_EMAIL,
                    RAW_UNSUBSCRIBE_URL_TOKEN,
                    RAW_MAILTO_SUBJECT_TOKEN);

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;
    @Autowired CampaignExecuteService campaignExecuteService;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogCapture() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.addFilter(new SensitiveMarkerScrubFilter());
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void future_campaign_execute_service_is_present() {
        assertThatCode(() -> Class.forName(CAMPAIGN_EXECUTE_SERVICE))
                .as("Future production type must exist: " + CAMPAIGN_EXECUTE_SERVICE)
                .doesNotThrowAnyException();
    }

    @Test
    void campaignExecution_doesNotLeakSensitiveTokensInLogs() {
        UUID tenantId = seedTenant();
        seedSensitiveSender(tenantId);

        CampaignExecuteResult campaignExecuteResult =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        campaignExecuteService.execute(
                                                tenantId, List.of(RAW_SENDER_EMAIL)));

        assertThat(campaignExecuteResult).isNotNull();
        assertThat(campaignExecuteResult.campaignId()).isNotNull();
        assertThat(campaignExecuteResult.jobId()).isNotNull();

        assertCapturedLogsAreContentFree();
        assertMetricTagsAreContentFree();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "cleanup-privacy-" + tenantId);
        return tenantId;
    }

    private void seedSensitiveSender(UUID tenantId) {
        jdbcTemplate.update(
                """
                insert into mail_message_observed(
                    tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id, history_id,
                    label_ids,
                    sender_email, list_unsubscribe_url, list_unsubscribe_mailto,
                    list_unsubscribe_one_click, observed_at)
                values (?, '00000000-0000-4000-8000-0000000000c1', ?, ?, ?,
                    ARRAY['INBOX']::text[], ?, ?, ?, ?, ?)
                """,
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                System.currentTimeMillis(),
                RAW_SENDER_EMAIL,
                "https://provider.test/unsub?token=" + RAW_UNSUBSCRIBE_URL_TOKEN,
                "mailto:unsub@provider.test?subject=" + RAW_MAILTO_SUBJECT_TOKEN + "&body=unsub",
                true,
                Timestamp.from(Instant.now()));
    }

    private void assertCapturedLogsAreContentFree() {
        List<String> capturedCleanupLogs =
                logAppender.list.stream()
                        .map(
                                loggingEvent ->
                                        loggingEvent.getFormattedMessage()
                                                + " "
                                                + loggingEvent.getMDCPropertyMap())
                        .filter(capturedLine -> capturedLine.contains("cleanup"))
                        .toList();
        assertNoForbiddenContent("cleanup log lines", String.join("\n", capturedCleanupLogs));
    }

    private void assertMetricTagsAreContentFree() {
        List<String> cleanupMetricTags =
                meterRegistry.getMeters().stream()
                        .filter(meter -> meter.getId().getName().startsWith("cleanup."))
                        .flatMap(meter -> meter.getId().getTags().stream())
                        .map(metricTag -> metricTag.getKey() + "=" + metricTag.getValue())
                        .toList();
        assertNoForbiddenContent("cleanup Micrometer tags", String.join("\n", cleanupMetricTags));
    }

    private static void assertNoForbiddenContent(String surfaceName, String surface) {
        for (String forbiddenContentToken : FORBIDDEN_CONTENT_TOKENS) {
            assertThat(surface)
                    .as("%s leaked forbidden token: %s", surfaceName, forbiddenContentToken)
                    .doesNotContain(forbiddenContentToken);
        }
    }

    @TestConfiguration
    static class MeterRegistryTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
