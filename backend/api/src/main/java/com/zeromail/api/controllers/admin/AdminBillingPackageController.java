package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.billing.AdminBillingFeatureCreditCostUpdateRequest;
import com.zeromail.api.dto.admin.billing.AdminBillingFeaturePermissionUpdateRequest;
import com.zeromail.api.dto.admin.billing.AdminBillingPackageResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.billing.usecases.AdminBillingPackageCommandService;
import com.zeromail.core.admin.billing.usecases.AdminBillingPackageQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-billing-packages")
@RequestMapping("/api/admin/billing-packages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingPackageController {

    private final AdminBillingPackageQueryService adminBillingPackageQueryService;
    private final AdminBillingPackageCommandService adminBillingPackageCommandService;

    public AdminBillingPackageController(
            AdminBillingPackageQueryService adminBillingPackageQueryService,
            AdminBillingPackageCommandService adminBillingPackageCommandService) {
        this.adminBillingPackageQueryService =
                Objects.requireNonNull(
                        adminBillingPackageQueryService,
                        "adminBillingPackageQueryService must not be null");
        this.adminBillingPackageCommandService =
                Objects.requireNonNull(
                        adminBillingPackageCommandService,
                        "adminBillingPackageCommandService must not be null");
    }

    @GetMapping({"", "/"})
    public AdminBillingPackageResponse overview() {
        AdminContext.currentOrThrow();
        return AdminBillingPackageResponse.from(adminBillingPackageQueryService.snapshot());
    }

    @PatchMapping("/features/{featureCode}/plans/{planCode}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePlanFeaturePermissionEnabled(
            @PathVariable String featureCode,
            @PathVariable String planCode,
            @Valid @RequestBody AdminBillingFeaturePermissionUpdateRequest request) {
        AdminContext.currentOrThrow();
        adminBillingPackageCommandService.setPlanFeaturePermissionEnabled(
                featureCode, planCode, request.enabledValue());
    }

    @PatchMapping("/features/{featureCode}/credit-cost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateFeatureCreditCost(
            @PathVariable String featureCode,
            @Valid @RequestBody AdminBillingFeatureCreditCostUpdateRequest request) {
        AdminContext.currentOrThrow();
        adminBillingPackageCommandService.setFeatureCreditCost(
                featureCode, request.fixedCreditCostValue());
    }
}
