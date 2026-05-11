package com.zeromail.api.controllers.rules;

import com.zeromail.api.dto.rules.CompiledPayloadRequest;
import com.zeromail.api.dto.rules.RuleCompileRequest;
import com.zeromail.api.dto.rules.RuleCompileResponse;
import com.zeromail.api.dto.rules.RuleCompileStatus;
import com.zeromail.api.dto.rules.RuleCreateRequest;
import com.zeromail.api.dto.rules.RuleDraftPreviewRequest;
import com.zeromail.api.dto.rules.RuleEnabledRequest;
import com.zeromail.api.dto.rules.RulePreviewRequest;
import com.zeromail.api.dto.rules.RulePreviewResponse;
import com.zeromail.api.dto.rules.RuleReorderRequest;
import com.zeromail.api.dto.rules.RuleResponse;
import com.zeromail.api.dto.rules.RuleTemplateMaterializationResponse;
import com.zeromail.api.dto.rules.RuleTemplateResponse;
import com.zeromail.api.dto.rules.RuleUpdateRequest;
import com.zeromail.api.dto.rules.RulesListResponse;
import com.zeromail.api.error.RuleApiException;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.service.RuleCompilerService;
import com.zeromail.core.rules.service.RuleManagementService;
import com.zeromail.core.rules.service.RulePreviewService;
import com.zeromail.core.rules.service.RuleTemplateCatalogService;
import com.zeromail.core.rules.service.RuleTemplateMaterializationService;
import com.zeromail.core.rules.usecases.RuleCompileCommand;
import com.zeromail.core.rules.usecases.RuleCompileResult;
import com.zeromail.core.rules.usecases.RuleCreateCommand;
import com.zeromail.core.rules.usecases.RuleOrderEntry;
import com.zeromail.core.rules.usecases.RuleReorderCommand;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult;
import com.zeromail.core.rules.usecases.RuleUpdateCommand;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "rules")
@RequestMapping("/api/rules")
public class RulesController {

    private final RuleCompilerService ruleCompilerService;
    private final RuleManagementService ruleManagementService;
    private final RulePreviewService rulePreviewService;
    private final RuleTemplateCatalogService ruleTemplateCatalogService;
    private final RuleTemplateMaterializationService ruleTemplateMaterializationService;

    public RulesController(
            RuleCompilerService ruleCompilerService,
            RuleManagementService ruleManagementService,
            RulePreviewService rulePreviewService,
            RuleTemplateCatalogService ruleTemplateCatalogService,
            RuleTemplateMaterializationService ruleTemplateMaterializationService) {
        this.ruleCompilerService = ruleCompilerService;
        this.ruleManagementService = ruleManagementService;
        this.rulePreviewService = rulePreviewService;
        this.ruleTemplateCatalogService = ruleTemplateCatalogService;
        this.ruleTemplateMaterializationService = ruleTemplateMaterializationService;
    }

    @GetMapping
    public ResponseEntity<RulesListResponse> listRules() {
        UUID tenantId = currentTenantId();
        // Locked decision D-C2: GET /api/rules is the source of truth and
        // must materialize selected templates idempotently. Other clients
        // (mobile, CLI, future worker bootstrap) must observe template-derived
        // rules without depending on a frontend-only POST.
        RuleTemplateMaterializationResult materializationResult =
                ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);
        RulesListResponse response =
                new RulesListResponse(
                        ruleManagementService.listOrdered(tenantId).stream()
                                .map(RuleResponse::from)
                                .toList(),
                        ruleTemplateCatalogService.listActiveTemplates(tenantId).stream()
                                .map(RuleTemplateResponse::from)
                                .toList(),
                        RuleTemplateMaterializationResponse.from(materializationResult));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/{ruleId}")
    public RuleResponse getRule(@PathVariable UUID ruleId) {
        return RuleResponse.from(ruleManagementService.get(currentTenantId(), ruleId));
    }

    @PostMapping("/compile")
    public RuleCompileResponse compile(@Valid @RequestBody RuleCompileRequest request) {
        UUID tenantId = currentTenantId();
        RuleCompileResult compileResult =
                ruleCompilerService.compile(
                        new RuleCompileCommand(
                                tenantId,
                                request.sourceText(),
                                request.clarificationAnswer(),
                                request.priorCompileContext()));
        return RuleCompileResponse.from(compileResult);
    }

    @PostMapping
    public RuleResponse createRule(@Valid @RequestBody RuleCreateRequest request) {
        UUID tenantId = currentTenantId();
        RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
        try {
            return RuleResponse.from(
                    ruleManagementService.create(
                            new RuleCreateCommand(
                                    tenantId,
                                    request.displayName(),
                                    request.sourceText(),
                                    compileResult)));
        } catch (IllegalArgumentException invalidCompilePayload) {
            throw RuleApiException.invalidCompileOutput();
        }
    }

    @PutMapping("/{ruleId}")
    public RuleResponse updateRule(
            @PathVariable UUID ruleId, @Valid @RequestBody RuleUpdateRequest request) {
        UUID tenantId = currentTenantId();
        RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
        try {
            return RuleResponse.from(
                    ruleManagementService.update(
                            new RuleUpdateCommand(
                                    tenantId,
                                    ruleId,
                                    request.displayName(),
                                    request.sourceText(),
                                    compileResult,
                                    request.entityVersion())));
        } catch (IllegalArgumentException invalidCompilePayload) {
            throw RuleApiException.invalidCompileOutput();
        }
    }

    @PatchMapping("/{ruleId}/enabled")
    public RuleResponse updateEnabled(
            @PathVariable UUID ruleId, @Valid @RequestBody RuleEnabledRequest request) {
        UUID tenantId = currentTenantId();
        if (request.enabled()) {
            return RuleResponse.from(ruleManagementService.enable(tenantId, ruleId));
        }
        return RuleResponse.from(ruleManagementService.disable(tenantId, ruleId));
    }

    @PutMapping("/reorder")
    public List<RuleResponse> reorderRules(@Valid @RequestBody RuleReorderRequest request) {
        UUID tenantId = currentTenantId();
        try {
            return ruleManagementService
                    .reorder(
                            new RuleReorderCommand(
                                    tenantId,
                                    request.entries().stream()
                                            .map(
                                                    orderedEntry ->
                                                            new RuleOrderEntry(
                                                                    orderedEntry.ruleId(),
                                                                    orderedEntry.entityVersion()))
                                            .toList()))
                    .stream()
                    .map(RuleResponse::from)
                    .toList();
        } catch (IllegalArgumentException invalidReorderRequest) {
            throw RuleApiException.invalidReorder();
        }
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable UUID ruleId) {
        ruleManagementService.delete(currentTenantId(), ruleId);
    }

    @PostMapping("/{ruleId}/preview")
    public RulePreviewResponse previewSavedRule(
            @PathVariable UUID ruleId, @Valid @RequestBody RulePreviewRequest request) {
        try {
            return RulePreviewResponse.from(
                    rulePreviewService.previewSavedRule(
                            currentTenantId(), ruleId, request.sampleSize()));
        } catch (IllegalArgumentException invalidSampleSize) {
            throw RuleApiException.invalidSampleSize();
        }
    }

    @PostMapping("/preview")
    public RulePreviewResponse previewDraftRule(
            @Valid @RequestBody RuleDraftPreviewRequest request) {
        Integer normalizedSampleSize = normalizedPreviewSampleSize(request.sampleSize());
        RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
        try {
            return RulePreviewResponse.from(
                    rulePreviewService.previewDraft(
                            currentTenantId(),
                            compileResult.matcherAst(),
                            compileResult.actionIntents(),
                            normalizedSampleSize));
        } catch (IllegalArgumentException invalidPreviewPayload) {
            throw RuleApiException.invalidCompileOutput();
        }
    }

    @GetMapping("/templates")
    public List<RuleTemplateResponse> listTemplates() {
        UUID tenantId = currentTenantId();
        return ruleTemplateCatalogService.listActiveTemplates(tenantId).stream()
                .map(RuleTemplateResponse::from)
                .toList();
    }

    @PostMapping("/templates/{templateKey}/materialize")
    public RuleTemplateMaterializationResponse materializeTemplate(
            @PathVariable String templateKey) {
        return RuleTemplateMaterializationResponse.from(
                ruleTemplateMaterializationService.materializeTemplate(
                        currentTenantId(), templateKey));
    }

    @PostMapping("/templates/materialize-selected")
    public RuleTemplateMaterializationResponse materializeSelectedTemplates() {
        return RuleTemplateMaterializationResponse.from(
                ruleTemplateMaterializationService.materializeSelectedTemplates(currentTenantId()));
    }

    private static UUID currentTenantId() {
        return UUID.fromString(TenantContext.currentOrThrow());
    }

    private Integer normalizedPreviewSampleSize(Integer requestedSampleSize) {
        try {
            return rulePreviewService.normalizeSampleSize(requestedSampleSize);
        } catch (IllegalArgumentException invalidSampleSize) {
            throw RuleApiException.invalidSampleSize();
        }
    }

    private static RuleCompileResult compiledPayloadOrThrow(
            CompiledPayloadRequest compiledPayload) {
        if (compiledPayload.status() == null) {
            throw RuleApiException.invalidCompileOutput();
        }
        return switch (compiledPayload.status()) {
            case RuleCompileStatus.COMPILED -> compiledPayload(compiledPayload);
            case RuleCompileStatus.CLARIFICATION_REQUIRED ->
                    throw RuleApiException.clarificationRequired();
            default -> throw RuleApiException.invalidCompileOutput();
        };
    }

    private static RuleCompileResult compiledPayload(CompiledPayloadRequest compiledPayload) {
        try {
            return RuleCompileResult.compiled(
                    RuleLanguage.fromId(compiledPayload.sourceLanguage()),
                    "Compiled rule",
                    RuleSchemaVersion.fromId(compiledPayload.schemaVersion()),
                    compiledPayload.matcherAst(),
                    compiledPayload.actionIntents());
        } catch (IllegalArgumentException
                | java.util.NoSuchElementException invalidCompilePayload) {
            // Narrow catch: only client-driven payload errors map to a 400.
            // Unexpected runtime errors (NPE, ISE) propagate to
            // GlobalExceptionHandler so operators see them in logs and the
            // client receives a generic 5xx instead of a misleading 400.
            throw RuleApiException.invalidCompileOutput();
        }
    }
}
