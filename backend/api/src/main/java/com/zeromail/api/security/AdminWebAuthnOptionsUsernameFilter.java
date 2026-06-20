package com.zeromail.api.security;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Bridges the admin login email field into Spring Security's WebAuthn options filter.
 *
 * <p>{@code PublicKeyCredentialRequestOptionsFilter} does not parse the request body. It builds
 * {@code allowCredentials} from the current {@link Authentication}. The admin SPA posts an email
 * before calling {@code navigator.credentials.get(...)}, so this filter creates a request-scoped
 * authentication only for the options request. The real login still happens at {@code
 * /login/webauthn} through the WebAuthn assertion.
 */
@Component
public class AdminWebAuthnOptionsUsernameFilter extends OncePerRequestFilter {

    private static final String AUTHENTICATE_OPTIONS_PATH = "/webauthn/authenticate/options";

    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public AdminWebAuthnOptionsUsernameFilter(
            AdminUserRepository adminUserRepository, ObjectMapper objectMapper) {
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !AUTHENTICATE_OPTIONS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication existingAuthentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (existingAuthentication != null
                && existingAuthentication.isAuthenticated()
                && !(existingAuthentication instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        AdminWebAuthnOptionsRequest optionsRequest;
        try {
            optionsRequest =
                    objectMapper.readValue(
                            request.getInputStream(), AdminWebAuthnOptionsRequest.class);
        } catch (IOException malformedRequestBody) {
            filterChain.doFilter(request, response);
            return;
        }

        AdminUserEntity adminUser =
                adminUserRepository.findByEmailIgnoreCase(optionsRequest.email()).orElse(null);
        if (!canAuthenticateWithPasskey(adminUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext temporaryContext = SecurityContextHolder.createEmptyContext();
        temporaryContext.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        adminUser.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(temporaryContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private static boolean canAuthenticateWithPasskey(AdminUserEntity adminUser) {
        return adminUser != null
                && adminUser.getStatus() == AdminStatus.ACTIVE
                && adminUser.getCredentialId() != null
                && adminUser.getPublicKeyCose() != null
                && adminUser.getUserHandle() != null;
    }

    private record AdminWebAuthnOptionsRequest(String email) {}
}
