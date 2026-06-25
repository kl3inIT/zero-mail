package com.zeromail.core.referral.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCodeRepository extends JpaRepository<ReferralCodeEntity, UUID> {

    Optional<ReferralCodeEntity> findByCampaignIdAndOwnerTenantId(
            UUID campaignId, UUID ownerTenantId);

    Optional<ReferralCodeEntity> findByCode(String code);

    boolean existsByCode(String code);
}
