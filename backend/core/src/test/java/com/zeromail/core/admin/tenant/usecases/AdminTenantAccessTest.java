package com.zeromail.core.admin.tenant.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminTenantAccessTest {

    @Test
    void read_only_writes_read_event_before_tenant_scope_supplier_runs() {
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        AdminTenantAccess adminTenantAccess = new AdminTenantAccess(adminAuditWriter);
        UUID adminId = UUID.fromString("00000000-0000-4000-8000-000000000801");
        UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000000901");
        AdminUser adminUser =
                new AdminUser(
                        adminId, "admin@example.com", AdminStatus.ACTIVE, Optional.of("Admin"));

        String scopedTenant =
                AdminContext.run(
                        adminUser,
                        () ->
                                adminTenantAccess.readOnly(
                                        tenantId,
                                        () -> TenantContext.currentOptional().orElseThrow()));

        assertThat(scopedTenant).isEqualTo(tenantId.toString());
        verify(adminAuditWriter)
                .writeReadEvent(adminUser, "TENANT_INSPECTION", "TENANT_INSPECTION", tenantId);
    }
}
