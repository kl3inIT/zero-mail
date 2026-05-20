package com.zeromail.api.security;

import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bridges {@link EnrollmentSessionController}-issued enrollment sessions into Spring Security's
 * WebAuthn registration filters.
 *
 * <p>{@link EnrollmentSessionController#createEnrollmentSession} consumes a one-time token and
 * stores the bound {@code adminUserId} in the HttpSession. The downstream WebAuthn registration
 * options filter ({@code PublicKeyCredentialCreationOptionsFilter}) looks up the user via {@code
 * SecurityContextHolder} — anonymous principals make it return 400 because Spring cannot resolve
 * {@code PublicKeyCredentialUserEntity} by username.
 *
 * <p>This filter runs only for the WebAuthn ceremony paths during an active enrollment session,
 * looks up the admin user, and sets a request-scoped {@link UsernamePasswordAuthenticationToken}
 * carrying the admin's email as the principal. The auth is NOT persisted into the session — once
 * the WebAuthn ceremony finishes and the credential is bound, normal admin-login flow takes over.
 */
@Component
public class EnrollmentSessionAuthFilter extends OncePerRequestFilter {

    private final AdminUserRepository adminUserRepository;

    public EnrollmentSessionAuthFilter(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/webauthn/") && !path.startsWith("/login/webauthn/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous =
                existing == null
                        || !existing.isAuthenticated()
                        || existing instanceof AnonymousAuthenticationToken;
        if (!anonymous) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Object sessionAttribute =
                httpSession.getAttribute(
                        EnrollmentSessionController.ENROLLMENT_ADMIN_USER_ID_SESSION_ATTRIBUTE);
        if (!(sessionAttribute instanceof String adminUserIdString)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID adminUserId;
        try {
            adminUserId = UUID.fromString(adminUserIdString);
        } catch (IllegalArgumentException malformedUuid) {
            filterChain.doFilter(request, response);
            return;
        }

        AdminUserEntity adminUser = adminUserRepository.findById(adminUserId).orElse(null);
        if (adminUser == null) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext temporaryContext = SecurityContextHolder.createEmptyContext();
        temporaryContext.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        adminUser.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_ENROLLMENT"))));
        SecurityContextHolder.setContext(temporaryContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }
}
