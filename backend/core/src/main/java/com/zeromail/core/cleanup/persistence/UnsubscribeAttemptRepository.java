package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnsubscribeAttemptRepository
        extends JpaRepository<UnsubscribeAttemptEntity, UUID> {

    List<UnsubscribeAttemptEntity> findByCampaignIdOrderBySenderEmailAsc(UUID campaignId);

    Optional<UnsubscribeAttemptEntity> findByCampaignIdAndSenderEmail(
            UUID campaignId, String senderEmail);

    long countByCampaignIdAndState(UUID campaignId, UnsubscribeAttemptState state);
}
