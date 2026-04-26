package com.zeromail.api.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.service.OAuthProvisioningService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Thin transport-layer adapter: delegates first-login provisioning to
 * {@link OAuthProvisioningService} so that tenant + user creation happen atomically inside
 * a single transaction (with explicit duplicate-Google-subject race handling). Keeping the
 * handler free of repository access means the privacy/transaction invariants live in one
 * place — the service.
 */
@Component
public class GoogleOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuthProvisioningService provisioning;

    public GoogleOAuthSuccessHandler(
            OAuthProvisioningService provisioning,
            ZeroMailApiProperties properties) {
        this.provisioning = provisioning;
        setDefaultTargetUrl(stripTrailingSlash(properties.web().baseUrl().toString()) + "/onboarding");
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException, ServletException {
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            provisioning.findOrCreateGoogleUser(oidc.getSubject(), oidc.getEmail());
        }
        super.onAuthenticationSuccess(req, res, auth);
    }
}
