package com.zeromail.api.controllers.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.api.security.ReferralAttributionCookie;
import com.zeromail.api.security.ReferralAttributionSnapshot;
import com.zeromail.api.security.ReferralAttributionTokenCodec;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;

class ReferralRedirectControllerTest {

    @Test
    void acceptReferralSupportsProductionApiProxyPath() throws Exception {
        Method acceptReferralMethod =
                ReferralRedirectController.class.getDeclaredMethod(
                        "acceptReferral",
                        String.class,
                        HttpServletRequest.class,
                        HttpServletResponse.class);

        GetMapping getMapping = acceptReferralMethod.getAnnotation(GetMapping.class);

        assertThat(getMapping.value()).contains("/r/{code}", "/api/r/{code}");
    }

    @Test
    void acceptReferralRedirectsToLoginWithSignedAttributionToken() {
        String referralCode = "ZME9XXKQX1ZL8K";
        Instant acceptedAt = Instant.parse("2026-06-22T02:45:22Z");
        ReferralCampaignService referralCampaignService = mock(ReferralCampaignService.class);
        given(referralCampaignService.canAcceptReferralCode(referralCode, acceptedAt))
                .willReturn(true);
        ReferralAttributionTokenCodec tokenCodec = tokenCodec();
        ReferralRedirectController controller =
                new ReferralRedirectController(
                        referralCampaignService,
                        tokenCodec,
                        apiProperties(),
                        Clock.fixed(acceptedAt, ZoneOffset.UTC));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/r/" + referralCode);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<Void> response =
                controller.acceptReferral(referralCode, request, servletResponse);

        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).startsWith("https://zeromail.test/login?ref=");
        String referralToken =
                UriComponentsBuilder.fromUri(location)
                        .build()
                        .getQueryParams()
                        .getFirst(ReferralAttributionSnapshot.QUERY_PARAMETER);
        assertThat(tokenCodec.decode(referralToken))
                .hasValue(new ReferralAttributionSnapshot(referralCode, acceptedAt));
        assertThat(servletResponse.getHeader("Set-Cookie"))
                .contains(ReferralAttributionCookie.COOKIE_NAME);
    }

    private static ReferralAttributionTokenCodec tokenCodec() {
        return new ReferralAttributionTokenCodec("test-referral-signing-secret");
    }

    private static ApiProperties apiProperties() {
        return new ApiProperties(
                new ApiProperties.WebProperties(URI.create("https://zeromail.test")), null, null);
    }
}
