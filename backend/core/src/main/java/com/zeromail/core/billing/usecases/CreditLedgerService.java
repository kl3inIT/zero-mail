package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditBalance;
import com.zeromail.core.billing.domain.CreditReservationStatus;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.exception.IllegalLedgerStateException;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.CreditReservationEntity;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.lowlevel.AdvisoryLockJdbcHelper;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreditLedgerService implements CreditLedger {

    private static final Logger log = LoggerFactory.getLogger(CreditLedgerService.class);

    private final CreditLedgerEntryRepository entryRepository;
    private final CreditGrantRepository grantRepository;
    private final CreditGrantService grantService;
    private final CreditReservationRepository reservationRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;
    private final ZeroMailCoreProperties coreProperties;

    CreditLedgerService(
            CreditLedgerEntryRepository entryRepository,
            CreditGrantRepository grantRepository,
            CreditGrantService grantService,
            CreditReservationRepository reservationRepository,
            AdvisoryLockJdbcHelper advisoryLockHelper,
            ZeroMailCoreProperties coreProperties) {
        this.entryRepository = entryRepository;
        this.grantRepository = grantRepository;
        this.grantService = grantService;
        this.reservationRepository = reservationRepository;
        this.advisoryLockHelper = advisoryLockHelper;
        this.coreProperties = coreProperties;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
        int requiredCredits = callSite.cost();
        if (requiredCredits < 0) {
            throw new IllegalLedgerStateException("Call-site cost cannot be negative: " + callSite);
        }

        if (requiredCredits > 0) {
            grantService.grantCurrentBetaCredits(tenantId);
        }

        advisoryLockHelper.acquireTenantLock(tenantId);

        if (requiredCredits > 0) {
            enforceDailyHardCap(tenantId, requiredCredits);
        }

        UUID selectedGrantId = null;
        if (requiredCredits > 0) {
            selectedGrantId = findGrantWithAvailableCredits(tenantId, requiredCredits).orElse(null);
            if (selectedGrantId == null) {
                int availableUnscopedCredits =
                        Math.toIntExact(
                                entryRepository.sumAvailableUnscopedCreditsForTenant(tenantId));
                if (availableUnscopedCredits < requiredCredits) {
                    throw new InsufficientCreditsException();
                }
            }
        }

        UUID reservationUuid = UUID.randomUUID();
        CreditReservationEntity reservation =
                new CreditReservationEntity(
                        reservationUuid,
                        tenantId,
                        requiredCredits,
                        selectedGrantId,
                        callSite,
                        CreditReservationStatus.PENDING);
        reservationRepository.save(reservation);

        if (requiredCredits > 0) {
            CreditLedgerEntryEntity reserveEntry =
                    CreditLedgerEntryEntity.reserve(
                            UUID.randomUUID(),
                            tenantId,
                            requiredCredits,
                            reservationUuid,
                            selectedGrantId);
            entryRepository.save(reserveEntry);
        }

        log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationUuid);
        return new ReservationId(reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void settle(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation =
                reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            throw new IllegalLedgerStateException(
                    "Cannot settle a RELEASED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            return;
        }

        reservation.markSettled();
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity settleEntry =
                CreditLedgerEntryEntity.settle(
                        UUID.randomUUID(), reservation.getTenantId(), reservationUuid);
        try {
            entryRepository.saveAndFlush(settleEntry);
        } catch (DataIntegrityViolationException duplicateSettleEntry) {
            // UNIQUE(ref_type, ref_id, kind) makes repeat SETTLE journal writes idempotent.
        }

        log.info(
                "event=credit_settled tenantId={} reservationId={}",
                reservation.getTenantId(),
                reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void release(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation =
                reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            throw new IllegalLedgerStateException(
                    "Cannot release a SETTLED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            return;
        }

        reservation.markReleased();
        reservationRepository.save(reservation);

        if (reservation.getAmountCredits() > 0) {
            CreditLedgerEntryEntity releaseEntry =
                    CreditLedgerEntryEntity.release(
                            UUID.randomUUID(),
                            reservation.getTenantId(),
                            reservation.getAmountCredits(),
                            reservationUuid,
                            reservation.getGrantId());
            try {
                entryRepository.saveAndFlush(releaseEntry);
            } catch (DataIntegrityViolationException duplicateReleaseEntry) {
                // UNIQUE(ref_type, ref_id, kind) makes repeat RELEASE journal writes idempotent.
            }
        }

        log.info(
                "event=credit_released tenantId={} reservationId={}",
                reservation.getTenantId(),
                reservationUuid);
    }

    @Override
    public CreditBalance balance(UUID tenantId) {
        grantService.grantCurrentBetaCredits(tenantId);
        int availableCredits =
                Math.toIntExact(entryRepository.sumAvailableCreditsForTenant(tenantId));
        int heldCredits = Math.toIntExact(entryRepository.sumHeldCreditsForTenant(tenantId));
        return new CreditBalance(availableCredits, heldCredits);
    }

    private Optional<UUID> findGrantWithAvailableCredits(UUID tenantId, int requiredCredits) {
        List<CreditGrantEntity> spendableGrants =
                grantRepository.findSpendableGrants(tenantId, Instant.now(), requiredCredits);
        return spendableGrants.stream().map(CreditGrantEntity::getId).findFirst();
    }

    private void enforceDailyHardCap(UUID tenantId, int requiredCredits) {
        ZeroMailCoreProperties.BillingProperties.BillingBetaProperties betaProperties =
                coreProperties.billing().beta();
        if (!betaProperties.enabled() || betaProperties.dailyHardCap() <= 0) {
            return;
        }
        LocalDate currentDate = LocalDate.now(CreditGrantService.BETA_CREDIT_ZONE);
        Instant dayStart =
                currentDate.atStartOfDay(CreditGrantService.BETA_CREDIT_ZONE).toInstant();
        int reservedCreditsToday =
                Math.toIntExact(entryRepository.sumReservedCreditsSince(tenantId, dayStart));
        if (reservedCreditsToday + requiredCredits > betaProperties.dailyHardCap()) {
            throw new InsufficientCreditsException();
        }
    }
}
