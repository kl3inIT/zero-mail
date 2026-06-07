package com.zeromail.api.controllers.account;

import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantDataDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/** Orchestrates account deletion for the current tenant. */
@RestController
public class AccountDeletionController {

    private final GmailConnectionService gmailConnectionService;
    private final TenantDataDeletionService tenantDataDeletionService;

    public AccountDeletionController(
            GmailConnectionService gmailConnectionService,
            TenantDataDeletionService tenantDataDeletionService) {
        this.gmailConnectionService = gmailConnectionService;
        this.tenantDataDeletionService = tenantDataDeletionService;
    }

    @DeleteMapping("/api/me/account")
    public void deleteAccount(HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        // Stop external Google state first while the refresh-token row still exists.
        gmailConnectionService.revokeGrantForCurrentTenant(tenantId);
        tenantDataDeletionService.deleteTenantData(tenantId);
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
