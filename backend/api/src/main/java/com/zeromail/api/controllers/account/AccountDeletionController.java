package com.zeromail.api.controllers.account;

import com.zeromail.core.account.usecases.AccountService;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.notification.usecases.DigestDeliveryService;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.onboarding.usecases.OnboardingService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import java.util.UUID;
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

    @DeleteMapping("/me/account")
    @Transactional
    public void deleteAccount() {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.deleteSelectionsForCurrentTenant(tenantId);
        gmailConnectionService.deleteForCurrentTenant(tenantId);
        // Defensive cleanup; both tables also cascade from tenants(id) on successful tenant delete.
        notificationPreferenceService.deleteForTenant(tenantId);
        digestDeliveryService.deleteForTenant(tenantId);
        accountService.deleteCurrentUser(tenantId);
        tenantService.deleteCurrentTenant(tenantId);
    }
}
