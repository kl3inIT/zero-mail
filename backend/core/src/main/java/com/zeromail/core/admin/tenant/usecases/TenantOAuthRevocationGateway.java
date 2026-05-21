package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.gmail.usecases.GmailConnectionService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantOAuthRevocationGateway {

    private final AdminTenantAccess adminTenantAccess;
    private final GmailConnectionService gmailConnectionService;

    public TenantOAuthRevocationGateway(
            AdminTenantAccess adminTenantAccess, GmailConnectionService gmailConnectionService) {
        this.adminTenantAccess =
                Objects.requireNonNull(adminTenantAccess, "adminTenantAccess must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
    }

    public void revoke(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        adminTenantAccess.runTenantScoped(
                targetTenantId,
                () -> {
                    gmailConnectionService.disconnect(targetTenantId);
                    return null;
                });
    }
}
