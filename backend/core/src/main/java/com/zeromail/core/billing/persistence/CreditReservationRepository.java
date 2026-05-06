package com.zeromail.core.billing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditReservationRepository
        extends JpaRepository<CreditReservationEntity, UUID>, CreditReservationStaleScanFragment {
}
