package com.zeromail.core.admin.auth.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRoleGrantServiceDeleteTest {

    @Test
    void delete_removes_admin_user_and_writes_hard_delete_audit_snapshot() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000001101");
        UUID requestId = UUID.fromString("00000000-0000-4000-8000-000000001102");
        AdminUserEntity adminUser =
                new AdminUserEntity(
                        adminUserId,
                        "delete-admin@example.com",
                        "Delete Admin",
                        new byte[] {0x11},
                        AdminStatus.ACTIVE);
        AdminUserRepository adminUserRepository = mock(AdminUserRepository.class);
        EnrollmentTokenService enrollmentTokenService = mock(EnrollmentTokenService.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        AdminRoleGrantService adminRoleGrantService =
                new AdminRoleGrantService(
                        adminUserRepository,
                        enrollmentTokenService,
                        adminAuditWriter,
                        Clock.fixed(Instant.parse("2026-06-20T08:00:00Z"), ZoneOffset.UTC));
        when(adminUserRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));

        adminRoleGrantService.delete(
                adminUserId, "Remove stale admin account", "127.0.0.1", requestId);

        verify(adminAuditWriter)
                .append(
                        eq(AdminAuditAction.ADMIN_DELETED),
                        eq("admin_user"),
                        eq(adminUserId),
                        contains("\"email\":\"delete-admin@example.com\""),
                        isNull(),
                        eq("Remove stale admin account"),
                        eq("127.0.0.1"),
                        eq(requestId));
        verify(adminUserRepository).delete(adminUser);
        verify(adminUserRepository).flush();
    }

    @Test
    void delete_rejects_system_audit_actor() {
        UUID systemAdminUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AdminUserRepository adminUserRepository = mock(AdminUserRepository.class);
        EnrollmentTokenService enrollmentTokenService = mock(EnrollmentTokenService.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        AdminRoleGrantService adminRoleGrantService =
                new AdminRoleGrantService(
                        adminUserRepository,
                        enrollmentTokenService,
                        adminAuditWriter,
                        Clock.fixed(Instant.parse("2026-06-20T08:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(
                        () ->
                                adminRoleGrantService.delete(
                                        systemAdminUserId,
                                        "Remove stale admin account",
                                        "127.0.0.1",
                                        UUID.fromString("00000000-0000-4000-8000-000000001103")))
                .isInstanceOf(AdminAuthException.class)
                .hasMessageContaining("System admin user cannot be deleted");
        verify(adminUserRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
