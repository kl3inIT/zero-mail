package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.tenant.TenantActionRequest;
import com.zeromail.api.dto.admin.tenant.TenantActivityResponse;
import com.zeromail.api.dto.admin.tenant.TenantBillingResponse;
import com.zeromail.api.dto.admin.tenant.TenantDeletionPreviewResponse;
import com.zeromail.api.dto.admin.tenant.TenantDetailResponse;
import com.zeromail.api.dto.admin.tenant.TenantHealthResponse;
import com.zeromail.api.dto.admin.tenant.TenantListResponse;
import com.zeromail.api.dto.admin.tenant.TenantSpendResponse;
import com.zeromail.api.error.TenantConfirmEmailMismatchException;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.usecases.AdminTenantAccess;
import com.zeromail.core.admin.tenant.usecases.TenantDeletionService;
import com.zeromail.core.admin.tenant.usecases.TenantDisconnectService;
import com.zeromail.core.admin.tenant.usecases.TenantInspectionService;
import com.zeromail.core.admin.tenant.usecases.TenantNotFoundException;
import com.zeromail.core.admin.tenant.usecases.TenantPauseService;
import com.zeromail.core.shared.exception.ErrorClass;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-tenants")
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTenantController {

    private final AdminTenantAccess adminTenantAccess;
    private final TenantInspectionService tenantInspectionService;
    private final TenantPauseService tenantPauseService;
    private final TenantDisconnectService tenantDisconnectService;
    private final TenantDeletionService tenantDeletionService;

    public AdminTenantController(
            AdminTenantAccess adminTenantAccess,
            TenantInspectionService tenantInspectionService,
            TenantPauseService tenantPauseService,
            TenantDisconnectService tenantDisconnectService,
            TenantDeletionService tenantDeletionService) {
        this.adminTenantAccess =
                Objects.requireNonNull(adminTenantAccess, "adminTenantAccess must not be null");
        this.tenantInspectionService =
                Objects.requireNonNull(
                        tenantInspectionService, "tenantInspectionService must not be null");
        this.tenantPauseService =
                Objects.requireNonNull(tenantPauseService, "tenantPauseService must not be null");
        this.tenantDisconnectService =
                Objects.requireNonNull(
                        tenantDisconnectService, "tenantDisconnectService must not be null");
        this.tenantDeletionService =
                Objects.requireNonNull(
                        tenantDeletionService, "tenantDeletionService must not be null");
    }

    @GetMapping({"", "/"})
    public TenantListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        AdminContext.currentOrThrow();
        TenantListQuery tenantListQuery =
                new TenantListQuery(limit, parseOffset(cursor), status, from, to);
        return TenantListResponse.from(
                adminTenantAccess.crossTenantList(
                        () -> tenantInspectionService.listTenants(tenantListQuery)));
    }

    @GetMapping("/{tenantId}/overview")
    public TenantDetailResponse overview(@PathVariable UUID tenantId) {
        return TenantDetailResponse.from(
                adminTenantAccess.readOnly(
                        tenantId, () -> tenantInspectionService.getOverview(tenantId)));
    }

    @GetMapping("/{tenantId}/health")
    public TenantHealthResponse health(@PathVariable UUID tenantId) {
        return TenantHealthResponse.from(
                adminTenantAccess.readOnly(
                        tenantId, () -> tenantInspectionService.getHealth(tenantId)));
    }

    @GetMapping("/{tenantId}/billing")
    public TenantBillingResponse billing(@PathVariable UUID tenantId) {
        return TenantBillingResponse.from(
                adminTenantAccess.readOnly(
                        tenantId, () -> tenantInspectionService.getBilling(tenantId)));
    }

    @GetMapping("/{tenantId}/spend")
    public TenantSpendResponse spend(@PathVariable UUID tenantId) {
        return TenantSpendResponse.from(
                adminTenantAccess.readOnly(
                        tenantId, () -> tenantInspectionService.getSpend(tenantId)));
    }

    @GetMapping("/{tenantId}/activity")
    public TenantActivityResponse activity(@PathVariable UUID tenantId) {
        return TenantActivityResponse.from(
                adminTenantAccess.readOnly(
                        tenantId, () -> tenantInspectionService.getActivity(tenantId)));
    }

    @GetMapping("/{tenantId}/deletion-preview")
    public TenantDeletionPreviewResponse deletionPreview(@PathVariable UUID tenantId) {
        return TenantDeletionPreviewResponse.from(
                adminTenantAccess.deletionPreview(
                        tenantId, () -> tenantDeletionService.preview(tenantId)));
    }

    @PostMapping("/{tenantId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantActionRequest request,
            HttpServletRequest httpServletRequest) {
        tenantPauseService.pause(
                tenantId, request.reason(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    @PostMapping("/{tenantId}/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantActionRequest request,
            HttpServletRequest httpServletRequest) {
        tenantDisconnectService.disconnect(
                tenantId, request.reason(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    @PostMapping("/{tenantId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantActionRequest request,
            HttpServletRequest httpServletRequest) {
        String gmailAccountEmail = tenantInspectionService.getGmailAccountEmail(tenantId);
        if (gmailAccountEmail == null) {
            throw new TenantNotFoundException(tenantId);
        }
        if (!gmailAccountEmail.equalsIgnoreCase(request.confirmEmail())) {
            throw new TenantConfirmEmailMismatchException();
        }
        tenantDeletionService.delete(
                tenantId, request.reason(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    private static int parseOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        // WR-14: a malformed cursor used to silently fall through to offset 0, which made
        // copy-paste / stale-cursor bugs invisible. Reject with 400 instead so the FE can
        // surface a clear error and reset to page 1. A future fix will replace the integer
        // offset with a keyset cursor over (created_at DESC, tenant_id ASC).
        try {
            int parsedOffset = Integer.parseInt(cursor);
            if (parsedOffset < 0) {
                throw new InvalidTenantCursorException();
            }
            return parsedOffset;
        } catch (NumberFormatException numberFormatException) {
            throw new InvalidTenantCursorException();
        }
    }

    static final class InvalidTenantCursorException extends AdminBusinessException {

        InvalidTenantCursorException() {
            super("Invalid tenant pagination cursor");
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.invalid_cursor";
        }

        @Override
        public String logEvent() {
            return "admin_tenant_invalid_cursor";
        }

        @Override
        public String detail() {
            return "The supplied pagination cursor is not a valid value.";
        }
    }
}
