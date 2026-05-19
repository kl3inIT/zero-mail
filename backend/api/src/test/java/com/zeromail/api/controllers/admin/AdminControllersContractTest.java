package com.zeromail.api.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminControllersContractTest {

    private static final Path ADMIN_AUDIT_CONTROLLER =
            Path.of("src/main/java/com/zeromail/api/controllers/admin/AdminAuditController.java");
    private static final Path ADMIN_ROLE_GRANTS_CONTROLLER =
            Path.of(
                    "src/main/java/com/zeromail/api/controllers/admin/AdminRoleGrantsController.java");
    private static final Path ADMIN_AUDIT_PAGE_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/audit/AdminAuditPageResponse.java");
    private static final Path ADMIN_USER_SUMMARY_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/grants/AdminUserSummaryResponse.java");
    private static final Path GRANT_ADMIN_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/grants/GrantAdminResponse.java");
    private static final Path NO_SENTINEL_LEAK_VALIDATOR =
            Path.of(
                    "src/main/java/com/zeromail/api/security/validation/NoSentinelLeakValidator.java");
    private static final Path ADMIN_ERROR_ADVICE =
            Path.of("src/main/java/com/zeromail/api/error/AdminErrorAdvice.java");

    @Test
    void admin_controllers_are_pre_authorized_and_use_admin_context() throws IOException {
        assertThat(Files.exists(ADMIN_AUDIT_CONTROLLER)).isTrue();
        assertThat(Files.exists(ADMIN_ROLE_GRANTS_CONTROLLER)).isTrue();

        String auditController = Files.readString(ADMIN_AUDIT_CONTROLLER);
        String grantsController = Files.readString(ADMIN_ROLE_GRANTS_CONTROLLER);

        assertThat(auditController)
                .contains("@RequestMapping(\"/api/admin/audit\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("writeReadEvent")
                .contains("StreamingResponseBody");
        assertThat(grantsController)
                .contains("@RequestMapping(\"/api/admin\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("@PostMapping(\"/grant-admin\")")
                .contains("@PostMapping(\"/admins/{adminUserId}/revoke\")");
    }

    @Test
    void admin_dtos_keep_credential_bytes_out_of_wire_shape() throws IOException {
        assertThat(Files.exists(ADMIN_AUDIT_PAGE_RESPONSE)).isTrue();
        assertThat(Files.exists(ADMIN_USER_SUMMARY_RESPONSE)).isTrue();
        assertThat(Files.exists(GRANT_ADMIN_RESPONSE)).isTrue();

        String adminUserSummaryResponse = Files.readString(ADMIN_USER_SUMMARY_RESPONSE);
        String grantAdminResponse = Files.readString(GRANT_ADMIN_RESPONSE);

        assertThat(adminUserSummaryResponse)
                .contains("hasCredential")
                .doesNotContain("credentialId")
                .doesNotContain("publicKeyCose")
                .doesNotContain("userHandle");
        assertThat(grantAdminResponse)
                .contains("enrollmentUrl")
                .contains("expiresAt")
                .doesNotContain("credential");
    }

    @Test
    void sentinel_guard_and_admin_error_mapping_are_declared() throws IOException {
        assertThat(Files.exists(NO_SENTINEL_LEAK_VALIDATOR)).isTrue();
        assertThat(Files.exists(ADMIN_ERROR_ADVICE)).isTrue();

        String validator = Files.readString(NO_SENTINEL_LEAK_VALIDATOR);
        String adminErrorAdvice = Files.readString(ADMIN_ERROR_ADVICE);

        for (String sentinelPrefix : List.of("sk-", "sk-ant-", "AIza", "sk-or-")) {
            assertThat(validator).contains(sentinelPrefix);
        }
        assertThat(adminErrorAdvice)
                .contains("AdminAuthException")
                .contains("error.admin.reason_sentinel_leak")
                .contains("error.admin.audit_export_too_large");
    }
}
