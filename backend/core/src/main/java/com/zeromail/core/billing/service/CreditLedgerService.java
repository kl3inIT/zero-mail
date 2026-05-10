package com.zeromail.core.billing.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditBalance;
import com.zeromail.core.billing.domain.CreditReservationStatus;
import com.zeromail.core.billing.exception.IllegalLedgerStateException;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.CreditReservationEntity;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.lowlevel.AdvisoryLockJdbcHelper;

@Service
class CreditLedgerService implements CreditLedger {

    private static final Logger log = LoggerFactory.getLogger(CreditLedgerService.class);

    private final CreditLedgerEntryRepository entryRepository;
    private final CreditReservationRepository reservationRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;

    CreditLedgerService(
            CreditLedgerEntryRepository entryRepository,
            CreditReservationRepository reservationRepository,
            AdvisoryLockJdbcHelper advisoryLockHelper) {
        this.entryRepository = entryRepository;
        this.reservationRepository = reservationRepository;
        this.advisoryLockHelper = advisoryLockHelper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
        advisoryLockHelper.acquireTenantLock(tenantId);

        int availableCredits = Math.toIntExact(entryRepository.sumAvailableCreditsForTenant(tenantId));
        if (availableCredits < callSite.cost()) {
            throw new InsufficientCreditsException();
        }

        UUID reservationUuid = UUID.randomUUID();
        CreditReservationEntity reservation = new CreditReservationEntity(
                reservationUuid, tenantId, callSite.cost(), callSite, CreditReservationStatus.PENDING);
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity reserveEntry = CreditLedgerEntryEntity.reserve(
                UUID.randomUUID(), tenantId, callSite.cost(), reservationUuid);
        entryRepository.save(reserveEntry);

        log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationUuid);
        return new ReservationId(reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void settle(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation = reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            throw new IllegalLedgerStateException("Cannot settle a RELEASED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            return;
        }

        reservation.markSettled();
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity settleEntry = CreditLedgerEntryEntity.settle(
                UUID.randomUUID(), reservation.getTenantId(), reservationUuid);
        try {
            entryRepository.saveAndFlush(settleEntry);
        } catch (DataIntegrityViolationException duplicateSettleEntry) {
            // UNIQUE(ref_type, ref_id, kind) makes repeat SETTLE journal writes idempotent.
        }

        log.info("event=credit_settled tenantId={} reservationId={}",
                reservation.getTenantId(), reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void release(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation = reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            throw new IllegalLedgerStateException("Cannot release a SETTLED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            return;
        }

        reservation.markReleased();
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity releaseEntry = CreditLedgerEntryEntity.release(
                UUID.randomUUID(), reservation.getTenantId(), reservation.getAmountCredits(), reservationUuid);
        try {
            entryRepository.saveAndFlush(releaseEntry);
        } catch (DataIntegrityViolationException duplicateReleaseEntry) {
            // UNIQUE(ref_type, ref_id, kind) makes repeat RELEASE journal writes idempotent.
        }

        log.info("event=credit_released tenantId={} reservationId={}",
                reservation.getTenantId(), reservationUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditBalance balance(UUID tenantId) {
        int availableCredits = Math.toIntExact(entryRepository.sumAvailableCreditsForTenant(tenantId));
        int heldCredits = Math.toIntExact(entryRepository.sumHeldCreditsForTenant(tenantId));
        return new CreditBalance(availableCredits, heldCredits);
    }
}
