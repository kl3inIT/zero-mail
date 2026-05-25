package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.waitlist.AdminWaitlistEntryResponse;
import com.zeromail.api.dto.admin.waitlist.AdminWaitlistListResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.waitlist.application.WaitlistAdminService;
import com.zeromail.core.waitlist.domain.WaitlistStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-waitlist")
@RequestMapping("/api/admin/waitlist")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWaitlistController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final WaitlistAdminService waitlistAdminService;

    public AdminWaitlistController(WaitlistAdminService waitlistAdminService) {
        this.waitlistAdminService = waitlistAdminService;
    }

    @GetMapping({"", "/"})
    public AdminWaitlistListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AdminContext.currentOrThrow();
        WaitlistStatus statusFilter = parseStatus(status);
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int boundedPage = Math.max(0, page);
        return AdminWaitlistListResponse.from(
                waitlistAdminService.list(statusFilter, boundedPage, boundedSize));
    }

    @GetMapping("/{waitlistId}")
    public AdminWaitlistEntryResponse get(@PathVariable UUID waitlistId) {
        AdminContext.currentOrThrow();
        return AdminWaitlistEntryResponse.from(waitlistAdminService.get(waitlistId));
    }

    @PostMapping("/{waitlistId}/approve")
    public AdminWaitlistEntryResponse approve(@PathVariable UUID waitlistId) {
        AdminUser admin = AdminContext.currentOrThrow();
        return AdminWaitlistEntryResponse.from(
                waitlistAdminService.approve(waitlistId, admin.id()));
    }

    @PostMapping("/{waitlistId}/reject")
    public AdminWaitlistEntryResponse reject(@PathVariable UUID waitlistId) {
        AdminUser admin = AdminContext.currentOrThrow();
        return AdminWaitlistEntryResponse.from(waitlistAdminService.reject(waitlistId, admin.id()));
    }

    private static WaitlistStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WaitlistStatus.fromId(status);
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }
}
