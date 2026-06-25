package com.zeromail.api.controllers.referral;

import com.zeromail.api.dto.referral.ReferralActiveCampaignResponse;
import com.zeromail.core.referral.projection.ReferralCampaignBannerImage;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unauthenticated referral surface for the marketing landing page. Distinct from {@link
 * ReferralController} (which is {@code @PreAuthorize("isAuthenticated()")}) because the landing
 * banner is served to logged-out visitors. Both endpoints are gated on a currently-active campaign
 * whose admin enabled {@code webBannerEnabled}, and expose only published marketing copy — no
 * tenant data, codes, or leaderboard. Paths are allow-listed in {@code SecurityConfig} (user chain
 * {@code permitAll}).
 */
@RestController
@Tag(name = "referrals-public")
@RequestMapping("/api/referrals/active-campaign")
public class ReferralPublicController {

    private final ReferralCampaignService referralCampaignService;

    public ReferralPublicController(ReferralCampaignService referralCampaignService) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
    }

    @GetMapping
    public ReferralActiveCampaignResponse activeCampaign() {
        return webBannerCampaign()
                .map(ReferralActiveCampaignResponse::active)
                .orElseGet(ReferralActiveCampaignResponse::inactive);
    }

    @GetMapping("/banner")
    public ResponseEntity<byte[]> activeCampaignBanner() {
        ReferralCampaignSnapshot campaign =
                webBannerCampaign()
                        .filter(ReferralCampaignSnapshot::bannerImageAvailable)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ReferralCampaignBannerImage bannerImage =
                referralCampaignService
                        .campaignBanner(campaign.campaignId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bannerImage.contentType()))
                .contentLength(bannerImage.sizeBytes())
                .cacheControl(CacheControl.noCache())
                .body(bannerImage.bytes());
    }

    private java.util.Optional<ReferralCampaignSnapshot> webBannerCampaign() {
        return referralCampaignService
                .activeCampaign()
                .filter(ReferralCampaignSnapshot::webBannerEnabled);
    }
}
