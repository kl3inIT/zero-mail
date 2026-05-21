package com.zeromail.api.security;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Emits an {@link AdminAuditAction#ADMIN_LOGIN} audit row on every successful WebAuthn
 * authentication. {@code AbstractAuthenticationProcessingFilter} (parent of {@code
 * WebAuthnAuthenticationFilter}) publishes {@link InteractiveAuthenticationSuccessEvent} directly
 * from {@code successfulAuthentication()}; the plain {@code AuthenticationSuccessEvent} route via
 * {@code DefaultAuthenticationEventPublisher} does not fire for WebAuthn because its authentication
 * class is not registered in the publisher's class-to-event mapping. We filter to {@link
 * WebAuthnAuthentication} so the Google-OAuth user chain (which uses a different Authentication
 * type) does not produce admin audit noise.
 *
 * <p>The {@code AdminContext} scoped value is not bound yet at event-publish time — {@code
 * AdminBindingFilter} runs later in the chain — so we resolve the actor identity from {@link
 * AdminUserRepository} using the authentication's name (admin email) and call {@link
 * AdminAuditWriter#appendForActor} directly.
 */
@Component
public class AdminWebAuthnAuditListener {

    private final AdminAuditWriter adminAuditWriter;
    private final AdminUserRepository adminUserRepository;

    public AdminWebAuthnAuditListener(
            AdminAuditWriter adminAuditWriter, AdminUserRepository adminUserRepository) {
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
        this.adminUserRepository =
                Objects.requireNonNull(adminUserRepository, "adminUserRepository must not be null");
    }

    @EventListener
    public void onAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (!(authentication instanceof WebAuthnAuthentication)) {
            return;
        }
        String email = resolveEmail(authentication);
        if (email == null || email.isBlank()) {
            return;
        }
        adminUserRepository
                .findByEmailIgnoreCase(email)
                .ifPresent(
                        adminUser ->
                                adminAuditWriter.appendForActor(
                                        adminUser.getId(),
                                        adminUser.getEmail(),
                                        AdminAuditAction.ADMIN_LOGIN,
                                        "admin_user",
                                        adminUser.getId(),
                                        null,
                                        Map.of(
                                                "factor",
                                                "FACTOR_WEBAUTHN",
                                                "authentication_class",
                                                authentication.getClass().getSimpleName()),
                                        "Admin signed in via WebAuthn",
                                        currentRequestIp(),
                                        null));
    }

    private static String resolveEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof PublicKeyCredentialUserEntity userEntity) {
            String name = userEntity.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return authentication.getName();
    }

    private static String currentRequestIp() {
        RequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes()
                                instanceof ServletRequestAttributes servletRequestAttributes
                        ? servletRequestAttributes
                        : null;
        if (requestAttributes == null) {
            return null;
        }
        HttpServletRequest httpServletRequest =
                ((ServletRequestAttributes) requestAttributes).getRequest();
        return Optional.ofNullable(httpServletRequest.getHeader("X-Forwarded-For"))
                .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                .filter(forwarded -> !forwarded.isBlank())
                .orElseGet(httpServletRequest::getRemoteAddr);
    }
}
