package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.OnboardingSelectionRepository;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class AccountDeletionController {

    private final OnboardingSelectionRepository onboarding;
    private final GmailConnectionRepository conns;
    private final UserRepository users;
    private final TenantRepository tenants;

    public AccountDeletionController(OnboardingSelectionRepository onboarding,
                                     GmailConnectionRepository conns,
                                     UserRepository users,
                                     TenantRepository tenants) {
        this.onboarding = onboarding;
        this.conns = conns;
        this.users = users;
        this.tenants = tenants;
    }

    @DeleteMapping("/me/account")
    @Transactional
    public void deleteAccount() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboarding.deleteAll(onboarding.findByTenantId(tenantId));
        conns.findByTenantId(tenantId).ifPresent(conns::delete);
        users.findFirstByTenantId(tenantId).ifPresent(users::delete);
        tenants.findById(tenantId).ifPresent(tenants::delete);
    }
}
