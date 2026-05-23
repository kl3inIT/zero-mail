package com.zeromail.worker.waitlist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WaitlistInviteDispatchSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    private WaitlistEmailRepository repository;
    private WaitlistInviteDispatchWorker dispatchWorker;
    private WaitlistInviteDispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(WaitlistEmailRepository.class);
        dispatchWorker = mock(WaitlistInviteDispatchWorker.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new WaitlistInviteDispatchScheduler(repository, dispatchWorker, clock);
        // LockAssert.assertLocked() is wired into ShedLock production runtime; bypass for unit
        // tests.
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void empty_due_list_does_nothing() {
        when(repository.findDueInviteIds(eq(NOW), anyInt())).thenReturn(List.of());

        scheduler.dispatchPendingInvites();

        verify(dispatchWorker, never()).dispatchOne(any(), any());
    }

    @Test
    void fans_out_dispatch_for_each_due_id() {
        UUID idOne = UUID.randomUUID();
        UUID idTwo = UUID.randomUUID();
        UUID idThree = UUID.randomUUID();
        when(repository.findDueInviteIds(eq(NOW), eq(WaitlistInviteDispatchScheduler.BATCH_SIZE)))
                .thenReturn(List.of(idOne, idTwo, idThree));

        scheduler.dispatchPendingInvites();

        verify(dispatchWorker, times(1)).dispatchOne(idOne, NOW);
        verify(dispatchWorker, times(1)).dispatchOne(idTwo, NOW);
        verify(dispatchWorker, times(1)).dispatchOne(idThree, NOW);
    }

    @Test
    void one_row_failure_does_not_abort_remaining_rows() {
        UUID idOne = UUID.randomUUID();
        UUID idTwo = UUID.randomUUID();
        UUID idThree = UUID.randomUUID();
        when(repository.findDueInviteIds(eq(NOW), anyInt()))
                .thenReturn(List.of(idOne, idTwo, idThree));
        doThrow(new RuntimeException("row 2 blew up"))
                .when(dispatchWorker)
                .dispatchOne(eq(idTwo), eq(NOW));

        scheduler.dispatchPendingInvites();

        verify(dispatchWorker).dispatchOne(idOne, NOW);
        verify(dispatchWorker).dispatchOne(idTwo, NOW);
        verify(dispatchWorker).dispatchOne(idThree, NOW);
    }
}
