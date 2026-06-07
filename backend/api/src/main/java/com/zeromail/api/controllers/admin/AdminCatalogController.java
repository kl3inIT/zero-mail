package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.cat.CatalogFeatureDefaultRequest;
import com.zeromail.api.dto.admin.cat.CatalogListResponse;
import com.zeromail.api.dto.admin.cat.CatalogModelCreateRequest;
import com.zeromail.api.dto.admin.cat.CatalogModelDisableRequest;
import com.zeromail.api.dto.admin.cat.CatalogModelEnableRequest;
import com.zeromail.api.dto.admin.cat.CatalogModelVerificationResponse;
import com.zeromail.api.dto.admin.cat.CatalogModelVerifyRequest;
import com.zeromail.api.dto.admin.cat.CatalogSyncConfirmRequest;
import com.zeromail.api.dto.admin.cat.CatalogSyncDiffResponse;
import com.zeromail.api.dto.admin.cat.CatalogSyncFetchResponse;
import com.zeromail.api.dto.admin.cat.FeatureDefaultMatrixResponse;
import com.zeromail.api.dto.admin.cat.SetFeatureDefaultTierRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.usecases.CatalogAdminService;
import com.zeromail.core.admin.cat.usecases.CatalogSyncOrchestrator;
import com.zeromail.core.admin.cat.usecases.FeatureDefaultTierService;
import com.zeromail.core.admin.cat.usecases.ModelVerificationService;
import com.zeromail.core.llm.domain.LlmProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-catalog")
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;
    private final CatalogSyncOrchestrator catalogSyncOrchestrator;
    private final ModelVerificationService modelVerificationService;
    private final FeatureDefaultTierService featureDefaultTierService;

    public AdminCatalogController(
            CatalogAdminService catalogAdminService,
            CatalogSyncOrchestrator catalogSyncOrchestrator,
            ModelVerificationService modelVerificationService,
            FeatureDefaultTierService featureDefaultTierService) {
        this.catalogAdminService = catalogAdminService;
        this.catalogSyncOrchestrator = catalogSyncOrchestrator;
        this.modelVerificationService = modelVerificationService;
        this.featureDefaultTierService = featureDefaultTierService;
    }

    @GetMapping("/feature-defaults")
    public FeatureDefaultMatrixResponse listFeatureDefaultMatrix() {
        AdminContext.currentOrThrow();
        return FeatureDefaultMatrixResponse.from(
                featureDefaultTierService.listMatrix(),
                featureDefaultTierService.listAllTierModelsAcrossFeatures());
    }

    @PutMapping("/feature-defaults")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignFeatureDefaultTier(
            @Valid @RequestBody SetFeatureDefaultTierRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        featureDefaultTierService.assign(
                request.feature(),
                request.tier(),
                request.provider(),
                request.modelIds(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @GetMapping("/{provider}")
    public CatalogListResponse list(@PathVariable LlmProvider provider) {
        AdminContext.currentOrThrow();
        return CatalogListResponse.from(provider, catalogAdminService.listProvider(provider));
    }

    @PostMapping("/{provider}/sync/fetch")
    public CatalogSyncFetchResponse startFetch(
            @PathVariable LlmProvider provider, HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return CatalogSyncFetchResponse.from(
                catalogSyncOrchestrator.startFetch(
                        provider, httpServletRequest.getRemoteAddr(), UUID.randomUUID()));
    }

    @GetMapping("/sync/{jobId}/diff")
    public CatalogSyncDiffResponse diff(@PathVariable UUID jobId) {
        AdminContext.currentOrThrow();
        return CatalogSyncDiffResponse.from(catalogSyncOrchestrator.diffResult(jobId));
    }

    @PostMapping("/sync/{jobId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(
            @PathVariable UUID jobId,
            @RequestBody(required = false) CatalogSyncConfirmRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogSyncOrchestrator.confirm(
                jobId,
                request == null ? null : request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PostMapping("/sync/{jobId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID jobId, HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogSyncOrchestrator.cancel(
                jobId, httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    @PostMapping("/{provider}/models")
    @ResponseStatus(HttpStatus.CREATED)
    public void createManualModel(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody CatalogModelCreateRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogAdminService.createManualModel(
                provider,
                request.modelId(),
                request.displayName(),
                request.costPer1kInput(),
                request.costPer1kOutput(),
                request.recommended(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PostMapping("/models/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableModel(
            @Valid @RequestBody CatalogModelDisableRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogAdminService.disableModel(
                request.modelId(),
                request.reason(),
                request.confirmedPinned(),
                request.pinnedCountAcknowledged(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PostMapping("/models/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableModel(
            @Valid @RequestBody CatalogModelEnableRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogAdminService.enableModel(
                request.modelId(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    @PostMapping("/models/verify")
    public CatalogModelVerificationResponse verifyModel(
            @Valid @RequestBody CatalogModelVerifyRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ModelVerificationStatus status =
                modelVerificationService.verify(
                        request.modelId(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
        // The service has already persisted the latency + error onto the row;
        // we re-read minimally by exposing only the status here. Callers that
        // need richer detail can hit the read endpoint that lists models.
        return CatalogModelVerificationResponse.from(status, null, null);
    }

    @PutMapping("/{provider}/{feature}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(
            @PathVariable LlmProvider provider,
            @PathVariable Feature feature,
            @Valid @RequestBody CatalogFeatureDefaultRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        catalogAdminService.setDefault(
                provider,
                feature,
                request.modelId(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }
}
