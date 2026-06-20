package com.zeromail.core.calendar.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.calendar.usecases.CalendarConnectionService;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AFTER_COMMIT firing test for the {@link
 * CalendarConnectionService#onCalendarConnectionDisconnected} listener. Drives the verification
 * through Spring's real event publication pipeline so the {@code @TransactionalEventListener(phase
 * = AFTER_COMMIT)} wiring is exercised (a pure-unit test could call the method directly but would
 * not prove the AFTER_COMMIT contract).
 *
 * <p>Pins:
 *
 * <ol>
 *   <li>Publishing the event inside a transaction triggers the listener exactly once after commit.
 *   <li>Publishing inside a transaction that rolls back does NOT trigger the listener (T-12-02
 *       invariant — no stale token eviction on a rollback).
 * </ol>
 */
class CalendarConnectionDisconnectedListenerTest extends PostgresContainerTest {

    @MockitoBean CalendarApiClientFactory calendarApiClientFactory;
    @Autowired ApplicationEventPublisher applicationEventPublisher;
    @Autowired PlatformTransactionManager platformTransactionManager;

    @Test
    void listener_fires_after_commit_and_evicts_access_token_cache() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionDisconnected event =
                new CalendarConnectionDisconnected(tenantId, calendarConnectionId, Instant.now());

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(platformTransactionManager);
        transactionTemplate.executeWithoutResult(
                _ -> applicationEventPublisher.publishEvent(event));

        verify(calendarApiClientFactory).evictAccessToken(eq(calendarConnectionId));
    }

    @Test
    void listener_does_not_fire_when_publishing_transaction_rolls_back() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionDisconnected event =
                new CalendarConnectionDisconnected(tenantId, calendarConnectionId, Instant.now());

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(platformTransactionManager);
        try {
            transactionTemplate.executeWithoutResult(
                    transactionStatus -> {
                        applicationEventPublisher.publishEvent(event);
                        transactionStatus.setRollbackOnly();
                    });
        } catch (RuntimeException rollbackPropagation) {
            // Some PlatformTransactionManager configurations rethrow on rollback-only commit;
            // either path is acceptable here — we only care that the listener was not fired.
        }

        verify(calendarApiClientFactory, never()).evictAccessToken(eq(calendarConnectionId));
        assertThat(calendarApiClientFactory).as("mock still in place").isNotNull();
    }
}
