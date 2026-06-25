package com.zeromail.core.referral.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralConversionRepository
        extends JpaRepository<ReferralConversionEntity, UUID> {

    boolean existsByCampaignIdAndReferredTenantId(UUID campaignId, UUID referredTenantId);
}
