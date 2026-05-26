package com.zeromail.core.billing.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPackageRepository extends JpaRepository<BillingPackageEntity, UUID> {

    List<BillingPackageEntity> findByActiveTrueOrderByDisplayOrderAscCodeAsc();

    Optional<BillingPackageEntity> findByCodeIgnoreCaseAndActiveTrue(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<BillingPackageEntity> findAllByOrderByDisplayOrderAscCodeAsc();
}
