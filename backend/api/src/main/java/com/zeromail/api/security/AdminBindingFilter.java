package com.zeromail.api.security;

import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminBindingFilter extends OncePerRequestFilter {

    private final AdminUserRepository adminUserRepository;

    public AdminBindingFilter(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Permit-all admin endpoints (e.g. POST /api/admin/enrollment/session and
            // the WebAuthn ceremony filters) run as anonymous. AnonymousAuthenticationToken
            // reports isAuthenticated() == true, so we must explicitly skip them here --
            // otherwise the admin_users lookup below resolves "anonymousUser" to null and
            // every public admin endpoint returns 401.
            filterChain.doFilter(request, response);
            return;
        }

        AdminUserEntity adminUserEntity =
                adminUserRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (adminUserEntity == null || adminUserEntity.getStatus() != AdminStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        AdminUser adminUser =
                new AdminUser(
                        adminUserEntity.getId(),
                        adminUserEntity.getEmail(),
                        adminUserEntity.getStatus(),
                        Optional.ofNullable(adminUserEntity.getDisplayName()));
        try {
            AdminContext.run(
                    adminUser,
                    () -> {
                        try {
                            filterChain.doFilter(request, response);
                        } catch (IOException | ServletException filterException) {
                            throw new RuntimeException(filterException);
                        }
                    });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) throw ioException;
            if (runtimeException.getCause() instanceof ServletException servletException) {
                throw servletException;
            }
            throw runtimeException;
        }
    }
}
