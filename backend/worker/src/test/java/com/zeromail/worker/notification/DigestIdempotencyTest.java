package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.core.notification.usecases.DigestComposer;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.notification.usecases.NotificationChannel;
import com.zeromail.worker.PostgresContainerTest;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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

@Import(DigestIdempotencyTest.CurrentInstantTestConfiguration.class)
@ResourceLock("digest-dispatch-db")
class DigestIdempotencyTest extends PostgresContainerTest {

    private static final Instant REFERENCE_INSTANT = Instant.parse("2026-05-13T13:05:00Z");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DigestDispatchScheduler scheduler;
    @Autowired MutableCurrentInstant currentInstant;

    @MockitoBean DigestComposer digestComposer;
    @MockitoBean NotificationChannel notificationChannel;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        DigestDispatchTestData.resetDigestTables(jdbcTemplate);
        currentInstant.set(REFERENCE_INSTANT);
        tenantId = DigestDispatchTestData.seedTenant(jdbcTemplate, "Asia/Ho_Chi_Minh");
        DigestDispatchTestData.seedUser(jdbcTemplate, tenantId, "idempotent@example.test", "vi");
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
        when(notificationChannel.dispatch(any(DigestPayload.class), anyString()))
                .thenReturn(new DispatchOutcome.Success("res_idempotent"));
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void repeated_dispatch_for_same_tenant_and_day_sends_once() {
        invokeScheduledDispatch();
        invokeScheduledDispatch();

        verify(notificationChannel).dispatch(any(DigestPayload.class), anyString());
        assertThat(DigestDispatchTestData.deliveryCount(jdbcTemplate, tenantId)).isEqualTo(1);
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, tenantId)).isEqualTo("SENT");
    }

    @Test
    void transient_failure_stays_pending_and_retries_after_next_attempt() {
        when(notificationChannel.dispatch(any(DigestPayload.class), anyString()))
                .thenReturn(new DispatchOutcome.TransientFailure("resend_transient_500"))
                .thenReturn(new DispatchOutcome.Success("res_retry"));

        invokeScheduledDispatch();
        invokeScheduledDispatch();

        verify(notificationChannel).dispatch(any(DigestPayload.class), anyString());
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, tenantId))
                .isEqualTo("PENDING");
        assertThat(DigestDispatchTestData.deliveryFailureReason(jdbcTemplate, tenantId))
                .isEqualTo("resend_transient_500");
        assertThat(DigestDispatchTestData.deliveryNextAttemptAt(jdbcTemplate, tenantId))
                .isEqualTo(REFERENCE_INSTANT.plusSeconds(15 * 60));
        assertThat(DigestDispatchTestData.deliveryAttemptCount(jdbcTemplate, tenantId))
                .isEqualTo(1);

        currentInstant.set(REFERENCE_INSTANT.plusSeconds(66 * 60));
        invokeScheduledDispatch();

        verify(notificationChannel, times(2)).dispatch(any(DigestPayload.class), anyString());
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, tenantId)).isEqualTo("SENT");
        assertThat(DigestDispatchTestData.deliveryExternalRef(jdbcTemplate, tenantId))
                .isEqualTo("res_retry");
        assertThat(DigestDispatchTestData.deliveryAttemptCount(jdbcTemplate, tenantId))
                .isEqualTo(2);
    }

    private void invokeScheduledDispatch() {
        DigestDispatchScheduler targetScheduler = AopTestUtils.getTargetObject(scheduler);
        targetScheduler.scheduledDispatch();
    }

    @TestConfiguration
    static class CurrentInstantTestConfiguration {

        @Bean
        @Primary
        MutableCurrentInstant mutableCurrentInstant() {
            return new MutableCurrentInstant();
        }
    }

    static class MutableCurrentInstant implements Supplier<Instant> {

        private final AtomicReference<Instant> instant = new AtomicReference<>(REFERENCE_INSTANT);

        @Override
        public Instant get() {
            return instant.get();
        }

        void set(Instant nextInstant) {
            instant.set(nextInstant);
        }
    }
}
