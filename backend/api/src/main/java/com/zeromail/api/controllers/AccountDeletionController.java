package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.onboarding.service.OnboardingService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.service.TenantService;

/**
 * TRANSITIONAL ORCHESTRATION (Plan 01.2-03 through 01.2-05).
 *
 * <p>AccountService no longer holds cross-domain repository injections (D-D1 + CL-2).
 * Until Plan 01.2-06 finalizes the orchestration shape, this controller bridges the
 * cross-domain delete cascade by chaining four single-domain service calls in
 * FK-safe order. backend/api transitively depends on every core domain, so this
 * orchestration is permissible at the API tier (the boundary it would violate —
 * repos in core services — is honored).
 *
 * <p>Plan 04 replaced the OnboardingSelectionRepository call with OnboardingService (done).
 * Plan 05 replaced the GmailConnectionRepository call with GmailConnectionService and
 * the TenantRepository call with TenantService (done).
 * Plan 06 cleans up the remaining JavaDoc + adds final orchestration shape.
 */
@RestController
public class AccountDeletionController {

    private final OnboardingService onboardingService;
    private final GmailConnectionService gmailConnectionService;
    private final AccountService accountService;
    private final TenantService tenantService;

    public AccountDeletionController(OnboardingService onboardingService,
                                     GmailConnectionService gmailConnectionService,
                                     AccountService accountService,
                                     TenantService tenantService) {
        this.onboardingService = onboardingService;
        this.gmailConnectionService = gmailConnectionService;
        this.accountService = accountService;
        this.tenantService = tenantService;
    }

    @DeleteMapping("/me/account")
    @Transactional
    public void deleteAccount() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboardingService.deleteSelectionsForCurrentTenant(tenantId);
        gmailConnectionService.deleteForCurrentTenant(tenantId);
        accountService.deleteCurrentUser(tenantId);
        tenantService.deleteCurrentTenant(tenantId);
    }
}
