package com.zeromail.core.billing.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeaturePermissionRepository
        extends JpaRepository<PlanFeaturePermissionEntity, UUID> {

    Optional<PlanFeaturePermissionEntity> findByPlanIdAndFeatureCode(
            UUID planId, String featureCode);

    List<PlanFeaturePermissionEntity> findByPlanId(UUID planId);

    List<PlanFeaturePermissionEntity> findByFeatureCode(String featureCode);
}
