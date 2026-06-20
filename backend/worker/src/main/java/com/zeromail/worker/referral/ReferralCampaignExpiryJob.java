package com.zeromail.worker.referral;

import com.zeromail.core.referral.usecases.ReferralCampaignService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReferralCampaignExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReferralCampaignExpiryJob.class);

    private final ReferralCampaignService referralCampaignService;
    private final Counter endedCampaignsCounter;

    public ReferralCampaignExpiryJob(
            ReferralCampaignService referralCampaignService, MeterRegistry meterRegistry) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        endedCampaignsCounter =
                Counter.builder("zero_mail.referral.campaign_expiry.ended_total")
                        .description("Referral campaigns automatically ended after their end time")
                        .register(meterRegistry);
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    @SchedulerLock(
            name = "referralCampaignExpiry",
            lockAtLeastFor = "PT30S",
            lockAtMostFor = "PT2M")
    public void scheduledEndExpiredCampaigns() {
        endExpiredCampaigns();
    }

    public int endExpiredCampaigns() {
        int endedCampaignCount = referralCampaignService.endExpiredActiveCampaigns();
        if (endedCampaignCount > 0) {
            endedCampaignsCounter.increment(endedCampaignCount);
            log.info(
                    "event=referral_campaigns_expired tenantId=system endedCampaignCount={}",
                    endedCampaignCount);
        }
        return endedCampaignCount;
    }
}
