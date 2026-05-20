package com.zeromail.core.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
 * sender email) and assert no log line, audit row, or metric tag leaks them. {@link
 * SensitiveMarkerScrubFilter} is attached to the {@link ListAppender} to mimic production logback
 * scrubbing.
 *
 * <p>Wave 0 RED: the production classes {@code CampaignExecuteService} + {@code
 * UnsubscribeCampaignHandler} do not exist yet; this test compiles via reflective lookup but fails
 * at runtime until Wave 4 ships them.
 */
@Import(CleanupPrivacySweepTest.MeterRegistryTestConfiguration.class)
@SuppressWarnings("SqlResolve")
class CleanupPrivacySweepTest extends PostgresContainerTest {

    private static final String CAMPAIGN_EXECUTE_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignExecuteService";

    private static final String RAW_SENDER_EMAIL = "newsletter.sender@cleanup.test";
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
    void campaignExecution_doesNotLeakSensitiveTokensInLogs() throws Exception {
        Class.forName(CAMPAIGN_EXECUTE_SERVICE);
        UUID tenantId = seedTenant();
        seedSensitiveSender(tenantId);

        Object campaignExecuteService = lookupExecuteBean();
        withTenant(
                tenantId,
                () -> invokeExecute(campaignExecuteService, tenantId, List.of(RAW_SENDER_EMAIL)));

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
                    id, tenant_id, gmail_message_id, gmail_thread_id, sender_email, sender_domain,
                    list_unsubscribe_url, list_unsubscribe_mailto, list_unsubscribe_one_click,
                    observed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "gmail-msg-" + UUID.randomUUID(),
                "gmail-thread-" + UUID.randomUUID(),
                RAW_SENDER_EMAIL,
                "cleanup.test",
                "https://provider.test/unsub?token=" + RAW_UNSUBSCRIBE_URL_TOKEN,
                "mailto:unsub@provider.test?subject=" + RAW_MAILTO_SUBJECT_TOKEN + "&body=unsub",
                true,
                Instant.now());
    }

    private Object lookupExecuteBean() throws Exception {
        return Class.forName(CAMPAIGN_EXECUTE_SERVICE).getDeclaredConstructor().newInstance();
    }

    private static Object invokeExecute(
            Object campaignExecuteService, UUID tenantId, List<String> senderEmails) {
        try {
            return campaignExecuteService
                    .getClass()
                    .getMethod("execute", UUID.class, List.class)
                    .invoke(campaignExecuteService, tenantId, senderEmails);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
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
                        .map(tag -> tag.getKey() + "=" + tag.getValue())
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

    private static void withTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }

    @TestConfiguration
    static class MeterRegistryTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
