package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.onboarding.service.OnboardingService;
import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantRepository;

/**
 * TRANSITIONAL ORCHESTRATION (Plan 01.2-03 through 01.2-05).
 *
 * <p>AccountService no longer holds cross-domain repository injections (D-D1 + CL-2).
 * Until Plan 01.2-06 finalizes the orchestration via per-domain service calls,
 * this controller temporarily bridges the gap by holding the cross-domain repo
 * references itself. backend/api transitively depends on every core domain, so
 * this is permissible at the API tier (the boundary it would violate — repos in core
 * services — is honored).
 *
 * <p>Plan 04 replaced the OnboardingSelectionRepository call with OnboardingService (done).
 * Plan 05 replaces the GmailConnectionRepository call with GmailConnectionService and
 * the TenantRepository call with TenantService.
 * Plan 06 cleans up the remaining repository imports + adds final orchestration shape.
 */
@RestController
public class AccountDeletionController {

    private final OnboardingService onboardingService;
    private final GmailConnectionRepository gmailRepo;
    private final AccountService accountService;
    private final TenantRepository tenantRepo;

    public AccountDeletionController(OnboardingService onboardingService,
                                     GmailConnectionRepository gmailRepo,
                                     AccountService accountService,
                                     TenantRepository tenantRepo) {
        this.onboardingService = onboardingService;
        this.gmailRepo = gmailRepo;
        this.accountService = accountService;
        this.tenantRepo = tenantRepo;
    }

    @DeleteMapping("/me/account")
    @Transactional
    public void deleteAccount() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboardingService.deleteSelectionsForCurrentTenant(tenantId);
        gmailRepo.findByTenantId(tenantId).ifPresent(gmailRepo::delete);
        accountService.deleteCurrentUser(tenantId);
        tenantRepo.findById(tenantId).ifPresent(tenantRepo::delete);
    }
}
