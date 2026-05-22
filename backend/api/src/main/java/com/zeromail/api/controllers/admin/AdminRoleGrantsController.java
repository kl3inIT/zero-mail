package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.grants.AdminUserSummaryResponse;
import com.zeromail.api.dto.admin.grants.GrantAdminRequest;
import com.zeromail.api.dto.admin.grants.GrantAdminResponse;
import com.zeromail.api.dto.admin.grants.RevokeAdminRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.usecases.AdminRoleGrantService;
import com.zeromail.core.admin.auth.usecases.AdminRoleGrantService.AdminRoleGrantResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-grants")
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleGrantsController {

    private final AdminRoleGrantService adminRoleGrantService;

    public AdminRoleGrantsController(AdminRoleGrantService adminRoleGrantService) {
        this.adminRoleGrantService = adminRoleGrantService;
    }

    @GetMapping("/admins")
    public List<AdminUserSummaryResponse> admins() {
        AdminContext.currentOrThrow();
        return adminRoleGrantService.listAdmins().stream()
                .map(AdminUserSummaryResponse::from)
                .toList();
    }

    @PostMapping("/grant-admin")
    public GrantAdminResponse grantAdmin(
            @Valid @RequestBody GrantAdminRequest grantAdminRequest,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        AdminRoleGrantResult adminRoleGrantResult =
                adminRoleGrantService.grant(
                        grantAdminRequest.email(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID());
        return GrantAdminResponse.from(adminRoleGrantResult);
    }

    @PostMapping("/admins/{adminUserId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAdmin(
            @PathVariable UUID adminUserId,
            @Valid @RequestBody RevokeAdminRequest revokeAdminRequest,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        adminRoleGrantService.revoke(
                adminUserId,
                revokeAdminRequest.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }
}
