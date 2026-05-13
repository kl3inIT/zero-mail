package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.core.notification.usecases.DigestComposer;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.notification.usecases.NotificationChannel;
import com.zeromail.worker.PostgresContainerTest;
import com.zeromail.worker.notification.email.ResendEmailGateway;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

@Import(DigestDispatchWithNoopChannelTest.NoopChannelConfiguration.class)
@ResourceLock("digest-dispatch-db")
class DigestDispatchWithNoopChannelTest extends PostgresContainerTest {

    private static final Instant REFERENCE_INSTANT = Instant.parse("2026-05-13T13:05:00Z");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DigestDispatchScheduler scheduler;

    @MockitoBean DigestComposer digestComposer;
    @MockitoBean ResendEmailGateway resendEmailGateway;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        DigestDispatchTestData.resetDigestTables(jdbcTemplate);
        tenantId = DigestDispatchTestData.seedTenant(jdbcTemplate, "Asia/Ho_Chi_Minh");
        DigestDispatchTestData.seedUser(jdbcTemplate, tenantId, "noop@example.test", "vi");
        DigestDispatchTestData.seedEmailPreference(jdbcTemplate, tenantId, true, 20);
        LockAssert.TestHelper.makeAllAssertsPass(true);
        when(digestComposer.compose(
                        any(UUID.class),
                        any(ZoneId.class),
                        any(Locale.class),
                        any(LocalDate.class),
                        any(Instant.class),
                        any(URI.class)))
                .thenReturn(
                        new DigestPayload(
                                Locale.forLanguageTag("vi"),
                                tenantId,
                                LocalDate.parse("2026-05-13"),
                                new DigestTotals(1, 1, 60),
                                List.of(),
                                List.of(),
                                URI.create(
                                        "https://zero-mail.test/analytics?source=digest&window=7d"),
                                URI.create(
                                        "https://zero-mail.test/settings?section=notifications&source=digest"),
                                false));
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void noop_channel_can_dispatch_without_resend_gateway() {
        invokeScheduledDispatch();

        verify(resendEmailGateway, never())
                .send(
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class));
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, tenantId)).isEqualTo("SENT");
        assertThat(DigestDispatchTestData.deliveryExternalRef(jdbcTemplate, tenantId))
                .isEqualTo("noop-ref");
    }

    private void invokeScheduledDispatch() {
        DigestDispatchScheduler targetScheduler = AopTestUtils.getTargetObject(scheduler);
        targetScheduler.scheduledDispatch();
    }

    @TestConfiguration
    static class NoopChannelConfiguration {

        @Bean
        @Primary
        Supplier<Instant> fixedCurrentInstant() {
            return () -> REFERENCE_INSTANT;
        }

        @Bean
        @Primary
        NotificationChannel noopNotificationChannel() {
            return (payload, recipientAddress) -> new DispatchOutcome.Success("noop-ref");
        }
    }
}
