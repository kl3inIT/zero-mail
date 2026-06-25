package com.zeromail.worker.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.referral.usecases.ReferralCampaignService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ReferralCampaignExpiryJobTest {

    @Test
    void scheduled_method_runs_every_five_minutes_with_shedlock() throws NoSuchMethodException {
        Method scheduledMethod =
                ReferralCampaignExpiryJob.class.getMethod("scheduledEndExpiredCampaigns");

        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        SchedulerLock schedulerLock = scheduledMethod.getAnnotation(SchedulerLock.class);

        assertThat(scheduled.cron()).isEqualTo("0 */5 * * * *");
        assertThat(scheduled.zone()).isEqualTo("UTC");
        assertThat(schedulerLock.name()).isEqualTo("referralCampaignExpiry");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT2M");
    }

    @Test
    void job_delegates_to_service_and_records_metric() {
        ReferralCampaignService referralCampaignService = mock(ReferralCampaignService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReferralCampaignExpiryJob referralCampaignExpiryJob =
                new ReferralCampaignExpiryJob(referralCampaignService, meterRegistry);
        when(referralCampaignService.endExpiredActiveCampaigns()).thenReturn(2);

        int endedCampaignCount = referralCampaignExpiryJob.endExpiredCampaigns();

        assertThat(endedCampaignCount).isEqualTo(2);
        assertThat(meterRegistry.counter("zero_mail.referral.campaign_expiry.ended_total").count())
                .isEqualTo(2.0);
        verify(referralCampaignService).endExpiredActiveCampaigns();
    }
}
