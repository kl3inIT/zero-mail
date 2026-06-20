package com.zeromail.api.controllers.referral;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.api.security.ReferralAttributionCookie;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Hidden
public class ReferralRedirectController {

    private final ReferralCampaignService referralCampaignService;
    private final ApiProperties apiProperties;
    private final Clock clock;

    public ReferralRedirectController(
            ReferralCampaignService referralCampaignService,
            ApiProperties apiProperties,
            Clock clock) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.apiProperties =
                Objects.requireNonNull(apiProperties, "apiProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> acceptReferral(
            @PathVariable String code, HttpServletRequest request, HttpServletResponse response) {
        Instant acceptedAt = clock.instant();
        if (referralCampaignService.canAcceptReferralCode(code, acceptedAt)) {
            ReferralAttributionCookie.write(response, code, acceptedAt, request.isSecure());
        }
        URI loginUri =
                UriComponentsBuilder.fromUri(apiProperties.web().baseUrl())
                        .path("/login")
                        .build()
                        .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(loginUri).build();
    }
}
