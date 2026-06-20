package com.zeromail.api.controllers.referral;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.api.dto.referral.ReferralMeResponse;
import com.zeromail.core.referral.projection.ReferralCampaignBannerImage;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import com.zeromail.core.referral.projection.ReferralTenantRankingSnapshot;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.referral.usecases.ReferralDashboardQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Tag(name = "referrals")
@RequestMapping("/api/referrals")
@PreAuthorize("isAuthenticated()")
public class ReferralController {

    private final ReferralCampaignService referralCampaignService;
    private final ReferralDashboardQueryService referralDashboardQueryService;
    private final ApiProperties apiProperties;

    public ReferralController(
            ReferralCampaignService referralCampaignService,
            ReferralDashboardQueryService referralDashboardQueryService,
            ApiProperties apiProperties) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.referralDashboardQueryService =
                Objects.requireNonNull(
                        referralDashboardQueryService,
                        "referralDashboardQueryService must not be null");
        this.apiProperties =
                Objects.requireNonNull(apiProperties, "apiProperties must not be null");
    }

    @GetMapping({"/me", "/me/"})
    public ReferralMeResponse me() {
        UUID ownerTenantId = TenantContext.currentTenantUuid();
        Optional<ReferralCampaignSnapshot> maybeReferralPageCampaign =
                referralCampaignService.referralPageCampaign();
        if (maybeReferralPageCampaign.isEmpty()) {
            return ReferralMeResponse.inactive();
        }
        ReferralCampaignSnapshot referralPageCampaign = maybeReferralPageCampaign.get();
        Optional<ReferralTenantCode> maybeReferralTenantCode =
                referralTenantCodeForOpenCampaign(referralPageCampaign, ownerTenantId);
        String referralCode = maybeReferralTenantCode.map(ReferralTenantCode::code).orElse(null);
        String referralUrl =
                maybeReferralTenantCode
                        .map(
                                referralTenantCode ->
                                        UriComponentsBuilder.fromUri(apiProperties.web().baseUrl())
                                                .path("/r/{code}")
                                                .build(referralTenantCode.code())
                                                .toString())
                        .orElse(null);
        int successfulReferrals =
                referralDashboardQueryService.successfulReferralsForTenant(
                        referralPageCampaign.campaignId(), ownerTenantId);
        ReferralTenantRankingSnapshot rankingSnapshot =
                referralDashboardQueryService.tenantRanking(
                        new ReferralDashboardQuery(
                                referralPageCampaign.campaignId(),
                                referralPageCampaign.startsAt(),
                                referralPageCampaign.endsAt(),
                                referralPageCampaign.leaderboardLimit()),
                        ownerTenantId);
        return ReferralMeResponse.active(
                referralPageCampaign.campaignId(),
                referralPageCampaign.name(),
                referralPageCampaign.description(),
                referralPageCampaign.startsAt(),
                referralPageCampaign.endsAt(),
                referralPageCampaign.webBannerEnabled(),
                referralPageCampaign.countdownEnabled(),
                referralPageCampaign.leaderboardEnabled(),
                referralPageCampaign.bannerImageAvailable(),
                referralCode,
                referralUrl,
                successfulReferrals,
                rankingSnapshot,
                ownerTenantId);
    }

    @GetMapping("/campaigns/{campaignId}/banner")
    public ResponseEntity<byte[]> campaignBanner(@PathVariable UUID campaignId) {
        ReferralCampaignBannerImage bannerImage =
                referralCampaignService
                        .campaignBanner(campaignId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bannerImage.contentType()))
                .contentLength(bannerImage.sizeBytes())
                .cacheControl(CacheControl.noCache())
                .body(bannerImage.bytes());
    }

    @PostMapping("/campaigns/{campaignId}/end-if-expired")
    public ResponseEntity<Void> endCampaignIfExpired(@PathVariable UUID campaignId) {
        referralCampaignService.endCampaignIfExpired(campaignId);
        return ResponseEntity.noContent().build();
    }

    private Optional<ReferralTenantCode> referralTenantCodeForOpenCampaign(
            ReferralCampaignSnapshot referralPageCampaign, UUID ownerTenantId) {
        return referralCampaignService
                .activeCampaign()
                .filter(
                        activeCampaign ->
                                activeCampaign
                                        .campaignId()
                                        .equals(referralPageCampaign.campaignId()))
                .map(
                        _ ->
                                referralCampaignService.getOrCreateTenantCode(
                                        referralPageCampaign.campaignId(), ownerTenantId));
    }
}
