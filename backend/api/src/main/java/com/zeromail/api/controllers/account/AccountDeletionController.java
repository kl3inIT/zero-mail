package com.zeromail.api.controllers.account;

import com.zeromail.core.account.usecases.AccountService;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.notification.usecases.DigestDeliveryService;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.onboarding.usecases.OnboardingService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orchestrates the cross-domain delete cascade for the current tenant. Each domain service performs
 * only its own single-domain delete (D-D1 / CL-2). Order matters: children (onboarding selections,
 * gmail connections) → user → tenant is FK-safe and prevents orphan rows.
 *
 * <p>Controller-level {@code @Transactional} provides atomicity across the four calls; each
 * delegated service method is itself {@code @Transactional} so propagation joins the controller's
 * transaction (Spring default {@code REQUIRED}).
 */
@RestController
public class AccountDeletionController {

    private final OnboardingService onboardingService;
    private final GmailConnectionService gmailConnectionService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final DigestDeliveryService digestDeliveryService;
    private final AccountService accountService;
    private final TenantService tenantService;

    public AccountDeletionController(
            OnboardingService onboardingService,
            GmailConnectionService gmailConnectionService,
            NotificationPreferenceService notificationPreferenceService,
            DigestDeliveryService digestDeliveryService,
            AccountService accountService,
            TenantService tenantService) {
        this.onboardingService = onboardingService;
        this.gmailConnectionService = gmailConnectionService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.digestDeliveryService = digestDeliveryService;
        this.accountService = accountService;
        this.tenantService = tenantService;
    }

    @DeleteMapping("/api/me/account")
    @Transactional
    public void deleteAccount(HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.deleteSelectionsForCurrentTenant(tenantId);
        // Revoke the OAuth grant + stop the Gmail watch at Google BEFORE dropping the row, so the
        // grant does not linger in the user's Google account and Pub/Sub stops pushing. Best-effort
        // (never throws); the row delete proceeds regardless.
        gmailConnectionService.revokeGrantForCurrentTenant(tenantId);
        gmailConnectionService.deleteForCurrentTenant(tenantId);
        // Defensive cleanup; both tables also cascade from tenants(id) on successful tenant delete.
        notificationPreferenceService.deleteForTenant(tenantId);
        digestDeliveryService.deleteForTenant(tenantId);
        accountService.deleteCurrentUser(tenantId);
        tenantService.deleteCurrentTenant(tenantId);
        // The account is gone; drop the caller's session so its now-orphaned blob does not linger
        // in
        // Redis until TTL. Other-device sessions are already inert (TenantBindingFilter finds no
        // user
        // → 401) and a full multi-session purge would require an indexed session repository.
        invalidateCurrentSession(request);
    }

    private static void invalidateCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
