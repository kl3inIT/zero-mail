package com.zeromail.core.referral.persistence;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCampaignRepository extends JpaRepository<ReferralCampaignEntity, UUID> {

    List<ReferralCampaignEntity> findAllByOrderByStartsAtDescCreatedAtDesc();

    boolean existsByStatus(ReferralCampaignStatus status);

    boolean existsByStatusAndIdNot(ReferralCampaignStatus status, UUID campaignId);

    List<ReferralCampaignEntity> findAllByStatusAndEndsAtLessThanEqualOrderByEndsAtAsc(
            ReferralCampaignStatus status, Instant endsAt);

    Optional<ReferralCampaignEntity>
            findFirstByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtDesc(
                    ReferralCampaignStatus status, Instant startsAt, Instant endsAt);

    Optional<ReferralCampaignEntity>
            findFirstByStatusInAndEndsAtLessThanEqualOrderByEndsAtDescCreatedAtDesc(
                    Collection<ReferralCampaignStatus> statuses, Instant endsAt);
}
