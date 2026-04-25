package com.zeromail.api.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.zeromail.core.persistence.TenantEntity;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.persistence.UserEntity;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GoogleOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository users;
    private final TenantRepository tenants;

    public GoogleOAuthSuccessHandler(UserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
        setDefaultTargetUrl("/onboarding");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException, ServletException {
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            users.findByGoogleSubject(oidc.getSubject()).orElseGet(() -> {
                UUID tenantId = UUID.randomUUID();
                tenants.save(new TenantEntity(tenantId, oidc.getEmail()));
                return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> users.save(new UserEntity(
                                UUID.randomUUID(), tenantId, oidc.getSubject(), oidc.getEmail())));
            });
        }
        super.onAuthenticationSuccess(req, res, auth);
    }
}
