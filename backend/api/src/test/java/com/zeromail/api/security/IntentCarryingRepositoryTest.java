package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

class IntentCarryingRepositoryTest {

    @Test
    void removeAuthorizationRequest_copies_intent_into_session_with_fresh_setAttribute() {
        IntentCarryingAuthorizationRequestRepository repository =
                new IntentCarryingAuthorizationRequestRepository();
        DirtyTrackingSession session = new DirtyTrackingSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID targetMailboxId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OAuth2AuthorizationRequest authorizationRequest =
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .clientId("client-id")
                        .redirectUri("http://localhost/login/oauth2/code/google")
                        .state("state-1")
                        .attributes(
                                attributes -> {
                                    attributes.put("intent", "reconnect_mailbox");
                                    attributes.put("targetMailboxId", targetMailboxId);
                                    attributes.put("initiatingTenantId", tenantId);
                                })
                        .build();

        repository.saveAuthorizationRequest(authorizationRequest, request, response);
        request.setParameter(OAuth2ParameterNames.STATE, "state-1");
        OAuth2AuthorizationRequest removedAuthorizationRequest =
                repository.removeAuthorizationRequest(request, response);

        assertThat(removedAuthorizationRequest).isNotNull();
        Object snapshot =
                session.getAttribute(
                        IntentCarryingAuthorizationRequestRepository.INTENT_SESSION_ATTRIBUTE);
        assertThat(snapshot)
                .isEqualTo(new OAuthIntentSnapshot("reconnect_mailbox", targetMailboxId, tenantId));
        assertThat(session.intentSetAttributeCount())
                .as("Redis-backed Spring Session must be dirtied with a fresh setAttribute call")
                .isEqualTo(1);
    }

    private static final class DirtyTrackingSession extends MockHttpSession {

        private int intentSetAttributeCount;

        @Override
        public void setAttribute(String name, Object value) {
            super.setAttribute(name, value);
            if (IntentCarryingAuthorizationRequestRepository.INTENT_SESSION_ATTRIBUTE.equals(
                    name)) {
                intentSetAttributeCount++;
            }
        }

        int intentSetAttributeCount() {
            return intentSetAttributeCount;
        }
    }
}
