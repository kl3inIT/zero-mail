package com.zeromail.api.security;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantBindingFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public TenantBindingFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidc)) {
            chain.doFilter(req, res);
            return;
        }
        var user = users.findByGoogleSubject(oidc.getSubject()).orElse(null);
        if (user == null) {
            chain.doFilter(req, res);
            return;
        }
        final String tenantId = user.getTenantId().toString();
        try {
            ScopedValue.where(TenantContext.TENANT, tenantId).run(() -> {
                try {
                    chain.doFilter(req, res);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof IOException io) throw io;
            if (re.getCause() instanceof ServletException se) throw se;
            throw re;
        }
    }
}
