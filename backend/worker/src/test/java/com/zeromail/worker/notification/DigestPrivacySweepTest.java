package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestRuleHit;
import com.zeromail.core.notification.domain.DigestTopSender;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.worker.notification.config.NotificationProperties;
import com.zeromail.worker.notification.email.EmailNotificationChannel;
import com.zeromail.worker.notification.email.ResendEmailGateway;
import com.zeromail.worker.notification.email.ThymeleafDigestRenderer;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DigestPrivacySweepTest {

    private static final String RECIPIENT_SENTINEL = "digest-sentinel-to-address@example.com";
    private static final String BODY_SENTINEL = "body-sentinel-05C";
    private static final String SENDER_SENTINEL = "sender-sentinel-05C@example.com";

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
    void digest_dispatch_logs_never_expose_recipient_body_or_sender_content() {
        ThymeleafDigestRenderer digestRenderer = mock(ThymeleafDigestRenderer.class);
        DigestPayload payload = sentinelPayload();
        when(digestRenderer.subject(payload)).thenReturn("Digest subject");
        when(digestRenderer.renderHtml(payload)).thenReturn("<p>" + BODY_SENTINEL + "</p>");
        when(digestRenderer.renderText(payload)).thenReturn(BODY_SENTINEL);
        ResendEmailGateway resendEmailGateway = mock(ResendEmailGateway.class);
        when(resendEmailGateway.send(
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)))
                .thenReturn(new DispatchOutcome.Success("res_sentinel_05c"));
        EmailNotificationChannel channel =
                new EmailNotificationChannel(digestRenderer, resendEmailGateway, properties());

        channel.dispatch(payload, RECIPIENT_SENTINEL);

        String capturedDigestLogs =
                logAppender.list.stream()
                        .map(loggingEvent -> loggingEvent.getFormattedMessage())
                        .filter(capturedLine -> capturedLine.contains("digest_"))
                        .reduce("", (left, right) -> left + "\n" + right);
        assertThat(capturedDigestLogs).contains("event=digest_dispatched");
        assertThat(capturedDigestLogs)
                .doesNotContain(RECIPIENT_SENTINEL)
                .doesNotContain(BODY_SENTINEL)
                .doesNotContain(SENDER_SENTINEL);
    }

    private static DigestPayload sentinelPayload() {
        return new DigestPayload(
                Locale.ENGLISH,
                UUID.fromString("00000000-0000-0000-0000-000000005c05"),
                LocalDate.parse("2026-05-13"),
                new DigestTotals(2, 2, 60),
                List.of(new DigestTopSender(SENDER_SENTINEL, 2)),
                List.of(new DigestRuleHit(BODY_SENTINEL, 2, 0)),
                URI.create("https://zero-mail.test/analytics?source=digest&window=7d"),
                URI.create("https://zero-mail.test/settings?section=notifications&source=digest"),
                false);
    }

    private static NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.EmailProperties(
                        new NotificationProperties.ResendProperties("test-resend-key"),
                        "notifications@zero-mail.test"),
                URI.create("https://zero-mail.test"));
    }
}
