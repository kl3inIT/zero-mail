package com.zeromail.core.billing.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlanEntity, UUID> {

    Optional<BillingPlanEntity> findByCode(String code);

    Optional<BillingPlanEntity> findByLemonSqueezyVariantId(Long lemonSqueezyVariantId);

    List<BillingPlanEntity> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
