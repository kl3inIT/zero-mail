package com.zeromail.api.controllers.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.api.dto.referral.ReferralMeResponse;
import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import com.zeromail.core.referral.projection.ReferralTenantRankingSnapshot;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.referral.usecases.ReferralDashboardQueryService;
import com.zeromail.core.tenant.TenantContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ReferralControllerTest {

    @Test
    void end_campaign_if_expired_delegates_to_referral_service_and_returns_no_content() {
        ReferralCampaignService referralCampaignService = mock(ReferralCampaignService.class);
        ReferralController referralController =
                new ReferralController(
                        referralCampaignService,
                        mock(ReferralDashboardQueryService.class),
                        apiProperties());
        UUID campaignId = UUID.fromString("00000000-0000-4000-8000-000000013600");

        ResponseEntity<Void> response = referralController.endCampaignIfExpired(campaignId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        then(referralCampaignService).should().endCampaignIfExpired(campaignId);
    }

    @Test
    void me_does_not_mint_or_return_share_link_for_ended_campaign() {
        ReferralCampaignService referralCampaignService = mock(ReferralCampaignService.class);
        ReferralDashboardQueryService referralDashboardQueryService =
                mock(ReferralDashboardQueryService.class);
        ReferralController referralController =
                new ReferralController(
                        referralCampaignService, referralDashboardQueryService, apiProperties());
        UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000013601");
        UUID campaignId = UUID.fromString("00000000-0000-4000-8000-000000013602");
        Instant startsAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant endsAt = Instant.parse("2026-09-03T23:59:00Z");
        given(referralCampaignService.referralPageCampaign())
                .willReturn(
                        Optional.of(
                                new ReferralCampaignSnapshot(
                                        campaignId,
                                        "Referral Ended",
                                        "REF-ENDED",
                                        "ref-ended",
                                        "Ended campaign",
                                        ReferralCampaignStatus.ENDED,
                                        startsAt,
                                        endsAt,
                                        true,
                                        true,
                                        true,
                                        20,
                                        5,
                                        "Top 5 tenants receive reward instructions by email.",
                                        false,
                                        startsAt,
                                        endsAt)));
        given(referralCampaignService.getOrCreateTenantCode(campaignId, tenantId))
                .willReturn(
                        new ReferralTenantCode(
                                UUID.fromString("00000000-0000-4000-8000-000000013603"),
                                campaignId,
                                tenantId,
                                "DTH12345",
                                "ACTIVE",
                                startsAt));
        given(referralDashboardQueryService.successfulReferralsForTenant(campaignId, tenantId))
                .willReturn(0);
        given(
                        referralDashboardQueryService.tenantRanking(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(tenantId)))
                .willReturn(new ReferralTenantRankingSnapshot(List.of(), null, 0, endsAt));

        ReferralMeResponse response =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(referralController::me);

        assertThat(response.active()).isTrue();
        assertThat(response.code()).isNull();
        assertThat(response.url()).isNull();
        assertThat(response.rewardRankCutoff()).isEqualTo(5);
        assertThat(response.rewardNotificationText())
                .isEqualTo("Top 5 tenants receive reward instructions by email.");
        then(referralCampaignService).should(never()).getOrCreateTenantCode(campaignId, tenantId);
    }

    private static ApiProperties apiProperties() {
        return new ApiProperties(
                new ApiProperties.WebProperties(URI.create("https://zeromail.test")), null, null);
    }
}
