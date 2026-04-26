package com.zeromail.core.onboarding.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingSelectionRepository extends JpaRepository<OnboardingSelectionEntity, UUID> {

    List<OnboardingSelectionEntity> findByTenantId(UUID tenantId);
}
