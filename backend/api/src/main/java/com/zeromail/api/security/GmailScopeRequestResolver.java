package com.zeromail.api.security;

import java.util.HashMap;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class GmailScopeRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String GMAIL_REGISTRATION_ID = "google-gmail";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GmailScopeRequestResolver(ClientRegistrationRepository repo) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest req) {
        return customize(delegate.resolve(req));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest req, String id) {
        return customize(delegate.resolve(req, id));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest r) {
        if (r == null) return null;
        Object regId = r.getAttribute("registration_id");
        if (regId == null || !GMAIL_REGISTRATION_ID.equals(regId.toString())) return r;
        var extra = new HashMap<>(r.getAdditionalParameters());
        extra.put("include_granted_scopes", "true");
        extra.put("prompt", "consent");
        extra.put("access_type", "offline");
        return OAuth2AuthorizationRequest.from(r).additionalParameters(extra).build();
    }
}
