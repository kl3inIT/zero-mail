package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.account.AccountService;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class AccountDeletionController {

    private final AccountService accountService;

    public AccountDeletionController(AccountService accountService) {
        this.accountService = accountService;
    }

    @DeleteMapping("/me/account")
    public void deleteAccount() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        accountService.deleteCurrentTenantAccount(tenantId);
    }
}
