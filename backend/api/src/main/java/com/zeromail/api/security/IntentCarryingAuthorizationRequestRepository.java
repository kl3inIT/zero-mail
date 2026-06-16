package com.zeromail.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class IntentCarryingAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String INTENT_SESSION_ATTRIBUTE =
            OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE;

    private static final Logger log =
            LoggerFactory.getLogger(IntentCarryingAuthorizationRequestRepository.class);

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest removedAuthorizationRequest =
                delegate.removeAuthorizationRequest(request, response);
        if (removedAuthorizationRequest == null) {
            return null;
        }

        OAuthIntentSnapshot intentSnapshot = snapshotFrom(removedAuthorizationRequest);
        if (intentSnapshot == null) {
            return removedAuthorizationRequest;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return removedAuthorizationRequest;
        }

        session.setAttribute(
                OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE,
                new OAuthIntentSnapshot(
                        intentSnapshot.intent(),
                        intentSnapshot.targetMailboxId(),
                        intentSnapshot.initiatingTenantId()));
        log.info("event=oauth_intent_captured");
        return removedAuthorizationRequest;
    }

    private static OAuthIntentSnapshot snapshotFrom(
            OAuth2AuthorizationRequest authorizationRequest) {
        Object intentValue =
                authorizationRequest.getAttributes().get(OAuthIntentSnapshot.ATTRIBUTE_INTENT);
        if (!(intentValue instanceof String intent) || intent.isBlank()) {
            return null;
        }
        UUID initiatingTenantId =
                uuidAttribute(
                        authorizationRequest
                                .getAttributes()
                                .get(OAuthIntentSnapshot.ATTRIBUTE_INITIATING_TENANT_ID));
        if (initiatingTenantId == null) {
            return null;
        }
        UUID targetMailboxId =
                uuidAttribute(
                        authorizationRequest
                                .getAttributes()
                                .get(OAuthIntentSnapshot.ATTRIBUTE_TARGET_MAILBOX_ID));
        return new OAuthIntentSnapshot(intent, targetMailboxId, initiatingTenantId);
    }

    private static UUID uuidAttribute(Object attributeValue) {
        if (attributeValue == null) {
            return null;
        }
        if (attributeValue instanceof UUID uuid) {
            return uuid;
        }
        if (attributeValue instanceof String uuidText && !uuidText.isBlank()) {
            try {
                return UUID.fromString(uuidText);
            } catch (IllegalArgumentException invalidUuidException) {
                return null;
            }
        }
        return null;
    }
}
