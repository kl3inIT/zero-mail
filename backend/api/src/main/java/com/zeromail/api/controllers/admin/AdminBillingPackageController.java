package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.billing.BillingPackageAdminCreateRequest;
import com.zeromail.api.dto.admin.billing.BillingPackageAdminListResponse;
import com.zeromail.api.dto.admin.billing.BillingPackageAdminResponse;
import com.zeromail.api.dto.admin.billing.BillingPackageAdminUpdateRequest;
import com.zeromail.api.dto.admin.billing.BillingPackageReorderRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.billing.usecases.BillingPackageAdminService;
import com.zeromail.core.admin.billing.usecases.BillingPackageAdminService.PackageDisplayOrderUpdate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-billing-packages")
@RequestMapping("/api/admin/billing/packages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingPackageController {

    private final BillingPackageAdminService billingPackageAdminService;

    public AdminBillingPackageController(BillingPackageAdminService billingPackageAdminService) {
        this.billingPackageAdminService = billingPackageAdminService;
    }

    @GetMapping({"", "/"})
    public BillingPackageAdminListResponse list() {
        AdminContext.currentOrThrow();
        return BillingPackageAdminListResponse.from(billingPackageAdminService.listPackages());
    }

    @GetMapping("/{packageId}")
    public BillingPackageAdminResponse get(@PathVariable UUID packageId) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminResponse.from(billingPackageAdminService.getPackage(packageId));
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public BillingPackageAdminResponse create(
            @Valid @RequestBody BillingPackageAdminCreateRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminResponse.from(
                billingPackageAdminService.createPackage(
                        request.code(),
                        request.name(),
                        request.priceVnd(),
                        request.description(),
                        request.active(),
                        request.displayOrder(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID()));
    }

    @PatchMapping("/{packageId}")
    public BillingPackageAdminResponse update(
            @PathVariable UUID packageId,
            @Valid @RequestBody BillingPackageAdminUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminResponse.from(
                billingPackageAdminService.updatePackage(
                        packageId,
                        request.name(),
                        request.priceVnd(),
                        request.description(),
                        request.active(),
                        request.displayOrder(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID()));
    }

    @PostMapping("/{packageId}/activate")
    public BillingPackageAdminResponse activate(
            @PathVariable UUID packageId, HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminResponse.from(
                billingPackageAdminService.activatePackage(
                        packageId, httpServletRequest.getRemoteAddr(), UUID.randomUUID()));
    }

    @PostMapping("/{packageId}/deactivate")
    public BillingPackageAdminResponse deactivate(
            @PathVariable UUID packageId, HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminResponse.from(
                billingPackageAdminService.deactivatePackage(
                        packageId, httpServletRequest.getRemoteAddr(), UUID.randomUUID()));
    }

    @PostMapping("/reorder")
    public BillingPackageAdminListResponse reorder(
            @Valid @RequestBody BillingPackageReorderRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return BillingPackageAdminListResponse.from(
                billingPackageAdminService.reorderPackages(
                        request.items().stream()
                                .map(
                                        item ->
                                                new PackageDisplayOrderUpdate(
                                                        item.packageId(), item.displayOrder()))
                                .toList(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID()));
    }
}
