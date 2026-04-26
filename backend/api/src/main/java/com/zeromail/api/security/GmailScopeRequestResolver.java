package com.zeromail.api.security;

import java.util.HashMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
        // D-A2: pre-fill Google chooser với email của user đang đăng nhập để leg-2 default
        // về cùng tài khoản họ vừa sign-in. Graceful-degrade: silently omit nếu session
        // không có authenticated principal hoặc principal không expose email — subject
        // check ở GmailOAuthSuccessHandler vẫn là authoritative guard (Pitfall 7: login_hint
        // chỉ là advisory). KHÔNG throw, KHÔNG log email (T-1.4-03-login-hint-xss/PII).
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof OAuth2AuthenticationToken oauthToken
                && oauthToken.getPrincipal() instanceof OidcUser oidc
                && oidc.getEmail() != null
                && !oidc.getEmail().isBlank()) {
            extra.put("login_hint", oidc.getEmail());
        }
        return OAuth2AuthorizationRequest.from(r).additionalParameters(extra).build();
    }
}
