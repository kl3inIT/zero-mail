package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

class IntentCarryingAuthorizationRequestRepositoryTest {

    @Test
    void removeAuthorizationRequestCarriesReferralAttributionToCallbackSession() {
        IntentCarryingAuthorizationRequestRepository repository =
                new IntentCarryingAuthorizationRequestRepository();
        String state = "state-with-referral";
        String referralCode = "ZME9XXKQX1ZL8K";
        Instant attributedAt = Instant.parse("2026-06-22T02:45:22Z");
        OAuth2AuthorizationRequest authorizationRequest =
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .clientId("google-client")
                        .redirectUri("https://zeromail.test/login/oauth2/code/google")
                        .state(state)
                        .attributes(
                                attributes -> {
                                    attributes.put(
                                            ReferralAttributionSnapshot.ATTRIBUTE_CODE,
                                            referralCode);
                                    attributes.put(
                                            ReferralAttributionSnapshot
                                                    .ATTRIBUTE_ATTRIBUTED_AT_EPOCH_MILLIS,
                                            attributedAt.toEpochMilli());
                                })
                        .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authorizationRequest, request, response);
        request.setParameter(OAuth2ParameterNames.STATE, state);

        repository.removeAuthorizationRequest(request, response);

        assertThat(
                        request.getSession()
                                .getAttribute(
                                        ReferralAttributionSnapshot.CALLBACK_SESSION_ATTRIBUTE))
                .isEqualTo(new ReferralAttributionSnapshot(referralCode, attributedAt));
    }
}
