package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.core.notification.usecases.DigestComposer;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.notification.usecases.NotificationChannel;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.PostgresContainerTest;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

@Import(DigestDispatchSchedulerTest.CurrentInstantTestConfiguration.class)
@ResourceLock("digest-dispatch-db")
class DigestDispatchSchedulerTest extends PostgresContainerTest {

    private static final Instant REFERENCE_INSTANT = Instant.parse("2026-05-13T13:05:00Z");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DIGEST_DAY_LOCAL = LocalDate.parse("2026-05-13");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DigestDispatchScheduler scheduler;
    @Autowired MutableCurrentInstant currentInstant;

    @MockitoBean DigestComposer digestComposer;
    @MockitoBean NotificationChannel notificationChannel;

    @BeforeEach
    void setUp() {
        DigestDispatchTestData.resetDigestTables(jdbcTemplate);
        currentInstant.set(REFERENCE_INSTANT);
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void scheduled_dispatch_claims_only_due_enabled_tenants_and_anchors_window_to_send_hour() {
        UUID dueTenantId = seedTenant("due@example.test", true, 20, "vi");
        UUID disabledTenantId = seedTenant("disabled@example.test", false, 20, "vi");
        UUID wrongHourTenantId = seedTenant("wrong-hour@example.test", true, 8, "en");
        stubComposerPayloads();
        when(notificationChannel.dispatch(any(DigestPayload.class), anyString()))
                .thenAnswer(
                        invocation -> {
                            DigestPayload payload = invocation.getArgument(0);
                            assertThat(TenantContext.currentOrThrow())
                                    .isEqualTo(payload.tenantId().toString());
                            return new DispatchOutcome.Success("res_test_abc");
                        });
        assertThat(scheduler.findTenantsDueForDigest(REFERENCE_INSTANT))
                .extracting(DigestDueTenant::tenantId)
                .containsExactly(dueTenantId);

        invokeScheduledDispatch();

        ArgumentCaptor<DigestPayload> payloadCaptor = ArgumentCaptor.forClass(DigestPayload.class);
        verify(notificationChannel).dispatch(payloadCaptor.capture(), eq("due@example.test"));
        assertThat(payloadCaptor.getValue().tenantId()).isEqualTo(dueTenantId);
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, dueTenantId))
                .isEqualTo("SENT");
        assertThat(DigestDispatchTestData.deliveryExternalRef(jdbcTemplate, dueTenantId))
                .isEqualTo("res_test_abc");
        assertThat(DigestDispatchTestData.deliveryDay(jdbcTemplate, dueTenantId))
                .isEqualTo(DIGEST_DAY_LOCAL);
        assertThat(DigestDispatchTestData.deliveryCount(jdbcTemplate, disabledTenantId)).isZero();
        assertThat(DigestDispatchTestData.deliveryCount(jdbcTemplate, wrongHourTenantId)).isZero();
        assertThat(currentInstant.callCount()).isEqualTo(1);

        ArgumentCaptor<Instant> sendMomentCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(digestComposer)
                .compose(
                        eq(dueTenantId),
                        eq(VIETNAM_ZONE),
                        eq(Locale.forLanguageTag("vi")),
                        eq(DIGEST_DAY_LOCAL),
                        sendMomentCaptor.capture(),
                        eq(URI.create("https://zero-mail.test")));
        assertThat(sendMomentCaptor.getValue()).isEqualTo(Instant.parse("2026-05-13T13:00:00Z"));
    }

    @Test
    void one_tenant_failure_does_not_roll_back_other_tenant_dispatches() {
        UUID firstTenantId = seedTenant("first@example.test", true, 20, "vi");
        UUID failingTenantId = seedTenant("failing@example.test", true, 20, "vi");
        UUID thirdTenantId = seedTenant("third@example.test", true, 20, "vi");
        stubComposerPayloads();
        when(notificationChannel.dispatch(any(DigestPayload.class), anyString()))
                .thenAnswer(
                        invocation -> {
                            DigestPayload payload = invocation.getArgument(0);
                            if (payload.tenantId().equals(failingTenantId)) {
                                throw new IllegalStateException("simulated channel failure");
                            }
                            return new DispatchOutcome.Success("res_" + payload.tenantId());
                        });

        invokeScheduledDispatch();

        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, firstTenantId))
                .isEqualTo("SENT");
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, failingTenantId))
                .isEqualTo("FAILED");
        assertThat(DigestDispatchTestData.deliveryFailureReason(jdbcTemplate, failingTenantId))
                .isEqualTo("dispatch_exception");
        assertThat(DigestDispatchTestData.deliveryStatus(jdbcTemplate, thirdTenantId))
                .isEqualTo("SENT");
    }

    @Test
    void scheduled_dispatch_contract_uses_cron_and_shedlock_without_transaction_annotation()
            throws NoSuchMethodException {
        java.lang.reflect.Method scheduledDispatch =
                DigestDispatchScheduler.class.getMethod("scheduledDispatch");

        assertThat(scheduledDispatch.getAnnotation(Scheduled.class).cron())
                .isEqualTo("0 5 * * * *");
        assertThat(scheduledDispatch.getAnnotation(SchedulerLock.class).name())
                .isEqualTo("digestDispatchScheduler");
        assertThat(
                        scheduledDispatch.getAnnotation(
                                org.springframework.transaction.annotation.Transactional.class))
                .isNull();
        assertThat(
                        DigestDispatchTenantWorker.class
                                .getMethod("dispatchOne", DigestDueTenant.class, Instant.class)
                                .getAnnotation(
                                        org.springframework.transaction.annotation.Transactional
                                                .class))
                .isNull();
    }

    private UUID seedTenant(
            String emailAddress, boolean enabled, int sendHourLocal, String locale) {
        UUID tenantId = DigestDispatchTestData.seedTenant(jdbcTemplate, VIETNAM_ZONE.getId());
        DigestDispatchTestData.seedUser(jdbcTemplate, tenantId, emailAddress, locale);
        DigestDispatchTestData.seedEmailPreference(jdbcTemplate, tenantId, enabled, sendHourLocal);
        return tenantId;
    }

    private void invokeScheduledDispatch() {
        DigestDispatchScheduler targetScheduler = AopTestUtils.getTargetObject(scheduler);
        targetScheduler.scheduledDispatch();
    }

    private void stubComposerPayloads() {
        when(digestComposer.compose(
                        any(UUID.class),
                        any(ZoneId.class),
                        any(Locale.class),
                        any(LocalDate.class),
                        any(Instant.class),
                        any(URI.class)))
                .thenAnswer(
                        invocation ->
                                new DigestPayload(
                                        invocation.getArgument(2),
                                        invocation.getArgument(0),
                                        invocation.getArgument(3),
                                        new DigestTotals(1, 1, 60),
                                        List.of(),
                                        List.of(),
                                        URI.create(
                                                "https://zero-mail.test/analytics?source=digest&window=7d"),
                                        URI.create(
                                                "https://zero-mail.test/settings?section=notifications&source=digest"),
                                        false));
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
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public Instant get() {
            callCount.incrementAndGet();
            return instant.get();
        }

        void set(Instant nextInstant) {
            instant.set(nextInstant);
            callCount.set(0);
        }

        int callCount() {
            return callCount.get();
        }
    }
}
