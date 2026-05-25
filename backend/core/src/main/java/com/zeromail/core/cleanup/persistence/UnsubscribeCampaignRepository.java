package com.zeromail.core.cleanup.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnsubscribeCampaignRepository
        extends JpaRepository<UnsubscribeCampaignEntity, UUID> {

    Optional<UnsubscribeCampaignEntity> findByIdAndTenantId(UUID campaignId, UUID tenantId);

    List<UnsubscribeCampaignEntity> findByTenantIdOrderByCreatedAtDesc(
            UUID tenantId, Pageable pageable);
}
