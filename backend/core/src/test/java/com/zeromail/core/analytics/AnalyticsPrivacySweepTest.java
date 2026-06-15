package com.zeromail.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.core.support.PostgresContainerTest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class AnalyticsPrivacySweepTest extends PostgresContainerTest {

    private static final String SENDER_EMAIL_SENTINEL = "audit-sentinel-05C-01@example.com";

    @Autowired AnalyticsSummaryQueryService analyticsSummaryQueryService;

    @Autowired JdbcTemplate jdbcTemplate;

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
    void analytics_logs_never_expose_sender_email() {
        UUID tenantId = seedTenant();
        Instant observedAt = Instant.now().minus(Duration.ofHours(1));
        insertObserved(tenantId, observedAt);

        AnalyticsSummaryProjection summary =
                analyticsSummaryQueryService.summarize(
                        tenantId, TimeWindow.endingAt(Instant.now(), Duration.ofDays(7)));

        assertThat(summary.topSenders()).hasSize(1);
        assertThat(summary.topSenders().getFirst().senderEmail()).isEqualTo(SENDER_EMAIL_SENTINEL);
        List<String> capturedAnalyticsLines =
                logAppender.list.stream()
                        .map(
                                loggingEvent ->
                                        loggingEvent.getFormattedMessage()
                                                + " "
                                                + loggingEvent.getMDCPropertyMap())
                        .filter(capturedLine -> capturedLine.contains("analytics_summary"))
                        .toList();
        assertThat(capturedAnalyticsLines)
                .anySatisfy(
                        capturedLine ->
                                assertThat(capturedLine)
                                        .contains("event=analytics_summary_computed")
                                        .contains("tenantId="));
        assertThat(String.join("\n", capturedAnalyticsLines)).doesNotContain(SENDER_EMAIL_SENTINEL);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "analytics-privacy-" + tenantId);
        return tenantId;
    }

    private void insertObserved(UUID tenantId, Instant observedAt) {
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement preparedStatement =
                            connection.prepareStatement(
                                    """
                                    insert into mail_message_observed(
                                        tenant_id, gmail_connection_id, gmail_message_id,
                                        gmail_thread_id, history_id, label_ids, internal_date,
                                        sender_email, observed_at
                                    )
                                    values (?, '00000000-0000-4000-8000-0000000000c1',
                                        ?, ?, ?, ?, ?, ?, ?)
                                    """);
                    preparedStatement.setObject(1, tenantId);
                    preparedStatement.setString(2, "analytics-privacy-message-" + tenantId);
                    preparedStatement.setString(3, "analytics-privacy-thread-" + tenantId);
                    preparedStatement.setLong(4, 50_001L);
                    preparedStatement.setArray(
                            5, connection.createArrayOf("text", new String[] {"INBOX"}));
                    preparedStatement.setLong(6, observedAt.toEpochMilli());
                    preparedStatement.setString(7, SENDER_EMAIL_SENTINEL);
                    preparedStatement.setTimestamp(8, Timestamp.from(observedAt));
                    return preparedStatement;
                });
    }
}
