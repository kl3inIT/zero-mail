package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.MeResponse;
import com.zeromail.core.account.AccountService;
import com.zeromail.core.account.CurrentUserView;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class MeController {

    private final AccountService accountService;

    public MeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CurrentUserView user = accountService.requireCurrentUser(tenantId);
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep());
    }
}
