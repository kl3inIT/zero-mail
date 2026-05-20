package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.me.AdminMeResponse;
import com.zeromail.core.admin.auth.AdminContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-check endpoint consumed by the {@code _authenticated} route guard in {@code apps/admin}.
 * Returns the currently bound admin's identity for the React router's {@code beforeLoad} hook so it
 * can decide whether to render the dashboard or redirect to {@code /login}.
 */
@RestController
@Tag(name = "admin-me")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMeController {

    @GetMapping("/api/admin/me")
    AdminMeResponse me() {
        return AdminMeResponse.from(AdminContext.currentOrThrow());
    }
}
