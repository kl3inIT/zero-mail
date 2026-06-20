package com.zeromail.api.controllers.onboarding;

import com.zeromail.api.dto.onboarding.SelectTemplateRequest;
import com.zeromail.api.security.ReferralAttributionCookie;
import com.zeromail.core.onboarding.usecases.OnboardingService;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingService onboardingService;
    private final ReferralCampaignService referralCampaignService;
    private final Clock clock;

    public OnboardingController(
            OnboardingService onboardingService,
            ReferralCampaignService referralCampaignService,
            Clock clock) {
        this.onboardingService =
                Objects.requireNonNull(onboardingService, "onboardingService must not be null");
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @PostMapping("/api/onboarding/select-template")
    public void selectTemplate(@Valid @RequestBody SelectTemplateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.selectTemplate(tenantId, request.templateKey());
    }

    @PostMapping("/api/onboarding/complete")
    public void complete(HttpServletRequest request, HttpServletResponse response) {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.complete(tenantId);
        qualifyReferralIfPresent(request, response, tenantId, clock.instant());
    }

    private void qualifyReferralIfPresent(
            HttpServletRequest request,
            HttpServletResponse response,
            UUID tenantId,
            Instant qualifiedAt) {
        ReferralAttributionCookie.read(request)
                .ifPresent(
                        referralAttribution -> {
                            try {
                                referralCampaignService.qualifyConversion(
                                        referralAttribution.code(),
                                        tenantId,
                                        referralAttribution.attributedAt(),
                                        qualifiedAt);
                            } catch (RuntimeException referralQualificationFailure) {
                                log.warn(
                                        "event=referral_qualification_failed tenantId={} failureClass={}",
                                        tenantId,
                                        referralQualificationFailure.getClass().getSimpleName());
                            } finally {
                                ReferralAttributionCookie.clear(response, request.isSecure());
                            }
                        });
    }
}
