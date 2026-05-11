package com.zeromail.core.account.projection;

import java.util.UUID;

/**
 * Read-side projection of the current user. Returned by the service layer so controllers never have
 * to import {@code UserEntity} (or any persistence-package type) directly — keeps the WR-01
 * controllers-do-not-touch-entities arch rule clean and avoids leaking Hibernate-managed instances
 * across the transaction boundary.
 */
public record CurrentUserProjection(
        UUID userId,
        UUID tenantId,
        String email,
        String onboardingStep,
        String preferredLanguage) {}
