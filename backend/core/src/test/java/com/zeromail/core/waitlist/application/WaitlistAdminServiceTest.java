package com.zeromail.core.waitlist.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.waitlist.domain.WaitlistStatus;
import com.zeromail.core.waitlist.exception.WaitlistEntryNotFoundException;
import com.zeromail.core.waitlist.exception.WaitlistEntryStateException;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import com.zeromail.core.waitlist.projection.WaitlistEntryProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WaitlistAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");

    private WaitlistEmailRepository repository;
    private WaitlistAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(WaitlistEmailRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new WaitlistAdminService(repository, clock);
    }

    @Test
    void approve_pending_row_transitions_to_approved() {
        UUID waitlistId = UUID.randomUUID();
        WaitlistEmailEntity entity = newPendingEntity(waitlistId, "alice@example.com");
        when(repository.findByIdForUpdate(waitlistId)).thenReturn(Optional.of(entity));

        WaitlistEntryProjection result = service.approve(waitlistId, ADMIN_ID);

        assertThat(result.status()).isEqualTo(WaitlistStatus.APPROVED);
        assertThat(result.approvedAt()).isEqualTo(NOW);
        assertThat(result.approvedByAdminId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void approve_already_approved_row_throws_state_exception() {
        UUID waitlistId = UUID.randomUUID();
        WaitlistEmailEntity entity = newPendingEntity(waitlistId, "bob@example.com");
        entity.approve(ADMIN_ID, NOW.minusSeconds(60));
        when(repository.findByIdForUpdate(waitlistId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approve(waitlistId, ADMIN_ID))
                .isInstanceOf(WaitlistEntryStateException.class)
                .extracting(throwable -> ((WaitlistEntryStateException) throwable).currentStatus())
                .isEqualTo("APPROVED");
    }

    @Test
    void approve_missing_row_throws_not_found() {
        UUID waitlistId = UUID.randomUUID();
        when(repository.findByIdForUpdate(waitlistId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(waitlistId, ADMIN_ID))
                .isInstanceOf(WaitlistEntryNotFoundException.class);
    }

    @Test
    void reject_pending_row_transitions_to_rejected() {
        UUID waitlistId = UUID.randomUUID();
        WaitlistEmailEntity entity = newPendingEntity(waitlistId, "carol@example.com");
        when(repository.findByIdForUpdate(waitlistId)).thenReturn(Optional.of(entity));

        WaitlistEntryProjection result = service.reject(waitlistId, ADMIN_ID);

        assertThat(result.status()).isEqualTo(WaitlistStatus.REJECTED);
        assertThat(result.approvedByAdminId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void reject_non_pending_row_throws_state_exception() {
        UUID waitlistId = UUID.randomUUID();
        WaitlistEmailEntity entity = newPendingEntity(waitlistId, "dave@example.com");
        entity.approve(ADMIN_ID, NOW.minusSeconds(30));
        when(repository.findByIdForUpdate(waitlistId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reject(waitlistId, ADMIN_ID))
                .isInstanceOf(WaitlistEntryStateException.class);
    }

    private static WaitlistEmailEntity newPendingEntity(UUID id, String email) {
        return new WaitlistEmailEntity(id, email, "landing_page", "hash", "agent");
    }
}
