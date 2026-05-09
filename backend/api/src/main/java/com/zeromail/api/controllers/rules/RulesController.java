package com.zeromail.api.controllers.rules;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
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

import com.zeromail.api.dto.rules.RuleDtos;
import com.zeromail.api.error.RuleApiException;
import com.zeromail.core.rules.model.RuleCompileCommand;
import com.zeromail.core.rules.model.RuleCompileResult;
import com.zeromail.core.rules.model.RuleCreateCommand;
import com.zeromail.core.rules.model.RuleLanguage;
import com.zeromail.core.rules.model.RuleOrderEntry;
import com.zeromail.core.rules.model.RuleReorderCommand;
import com.zeromail.core.rules.model.RuleSchemaVersion;
import com.zeromail.core.rules.model.RuleUpdateCommand;
import com.zeromail.core.rules.service.RuleCompilerService;
import com.zeromail.core.rules.service.RuleManagementService;
import com.zeromail.core.rules.service.RulePreviewService;
import com.zeromail.core.rules.service.RuleTemplateCatalogService;
import com.zeromail.core.rules.service.RuleTemplateMaterializationService;
import com.zeromail.core.tenant.TenantContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

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
  public ResponseEntity<RuleDtos.RulesListResponse> listRules() {
    UUID tenantId = currentTenantId();
    RuleDtos.RuleTemplateMaterializationResponse materialization =
        RuleDtos.RuleTemplateMaterializationResponse.from(
            ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId));
    RuleDtos.RulesListResponse response =
        new RuleDtos.RulesListResponse(
            ruleManagementService.listOrdered(tenantId).stream()
                .map(RuleDtos.RuleResponse::from)
                .toList(),
            ruleTemplateCatalogService.listActiveTemplates(tenantId).stream()
                .map(RuleDtos.RuleTemplateResponse::from)
                .toList(),
            materialization);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
  }

  @GetMapping("/{ruleId}")
  public RuleDtos.RuleResponse getRule(@PathVariable UUID ruleId) {
    return RuleDtos.RuleResponse.from(ruleManagementService.get(currentTenantId(), ruleId));
  }

  @PostMapping("/compile")
  public RuleDtos.RuleCompileResponse compile(
      @Valid @RequestBody RuleDtos.RuleCompileRequest request) {
    UUID tenantId = currentTenantId();
    RuleCompileResult compileResult =
        ruleCompilerService.compile(
            new RuleCompileCommand(
                tenantId,
                request.sourceText(),
                request.clarificationAnswer(),
                request.priorCompileContext()));
    return RuleDtos.RuleCompileResponse.from(compileResult);
  }

  @PostMapping
  public RuleDtos.RuleResponse createRule(@Valid @RequestBody RuleDtos.RuleCreateRequest request) {
    UUID tenantId = currentTenantId();
    RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
    try {
      return RuleDtos.RuleResponse.from(
          ruleManagementService.create(
              new RuleCreateCommand(
                  tenantId, request.displayName(), request.sourceText(), compileResult)));
    } catch (IllegalArgumentException invalidCompilePayload) {
      throw RuleApiException.invalidCompileOutput();
    }
  }

  @PutMapping("/{ruleId}")
  public RuleDtos.RuleResponse updateRule(
      @PathVariable UUID ruleId, @Valid @RequestBody RuleDtos.RuleUpdateRequest request) {
    UUID tenantId = currentTenantId();
    RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
    try {
      return RuleDtos.RuleResponse.from(
          ruleManagementService.update(
              new RuleUpdateCommand(
                  tenantId, ruleId, request.displayName(), request.sourceText(), compileResult)));
    } catch (IllegalArgumentException invalidCompilePayload) {
      throw RuleApiException.invalidCompileOutput();
    }
  }

  @PatchMapping("/{ruleId}/enabled")
  public RuleDtos.RuleResponse updateEnabled(
      @PathVariable UUID ruleId, @Valid @RequestBody RuleDtos.RuleEnabledRequest request) {
    UUID tenantId = currentTenantId();
    if (request.enabled()) {
      return RuleDtos.RuleResponse.from(ruleManagementService.enable(tenantId, ruleId));
    }
    return RuleDtos.RuleResponse.from(ruleManagementService.disable(tenantId, ruleId));
  }

  @PutMapping("/reorder")
  public List<RuleDtos.RuleResponse> reorderRules(
      @Valid @RequestBody RuleDtos.RuleReorderRequest request) {
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
                                  orderedEntry.ruleId(), orderedEntry.entityVersion()))
                      .toList()))
          .stream()
          .map(RuleDtos.RuleResponse::from)
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
  public RuleDtos.RulePreviewResponse previewSavedRule(
      @PathVariable UUID ruleId, @Valid @RequestBody RuleDtos.RulePreviewRequest request) {
    try {
      return RuleDtos.RulePreviewResponse.from(
          rulePreviewService.previewSavedRule(currentTenantId(), ruleId, request.sampleSize()));
    } catch (IllegalArgumentException invalidSampleSize) {
      throw RuleApiException.invalidSampleSize();
    }
  }

  @PostMapping("/preview")
  public RuleDtos.RulePreviewResponse previewDraftRule(
      @Valid @RequestBody RuleDtos.RuleDraftPreviewRequest request) {
    RuleCompileResult compileResult = compiledPayloadOrThrow(request.compiled());
    try {
      return RuleDtos.RulePreviewResponse.from(
          rulePreviewService.previewDraft(
              currentTenantId(),
              compileResult.matcherAst(),
              compileResult.actionIntents(),
              request.sampleSize()));
    } catch (IllegalArgumentException invalidPreviewPayload) {
      throw RuleApiException.invalidCompileOutput();
    }
  }

  @GetMapping("/templates")
  public List<RuleDtos.RuleTemplateResponse> listTemplates() {
    UUID tenantId = currentTenantId();
    return ruleTemplateCatalogService.listActiveTemplates(tenantId).stream()
        .map(RuleDtos.RuleTemplateResponse::from)
        .toList();
  }

  @PostMapping("/templates/{templateKey}/materialize")
  public RuleDtos.RuleTemplateMaterializationResponse materializeTemplate(
      @PathVariable String templateKey) {
    return RuleDtos.RuleTemplateMaterializationResponse.from(
        ruleTemplateMaterializationService.materializeTemplate(currentTenantId(), templateKey));
  }

  private static UUID currentTenantId() {
    return UUID.fromString(TenantContext.currentOrThrow());
  }

  private static RuleCompileResult compiledPayloadOrThrow(
      RuleDtos.CompiledPayloadRequest compiledPayload) {
    return switch (compiledPayload.status()) {
      case RuleDtos.STATUS_COMPILED -> compiledPayload(compiledPayload);
      case RuleDtos.STATUS_CLARIFICATION_REQUIRED -> throw RuleApiException.clarificationRequired();
      case RuleDtos.STATUS_INVALID -> throw RuleApiException.invalidCompileOutput();
      default -> throw RuleApiException.invalidCompileOutput();
    };
  }

  private static RuleCompileResult compiledPayload(RuleDtos.CompiledPayloadRequest compiledPayload) {
    try {
      return RuleCompileResult.compiled(
          RuleLanguage.fromId(compiledPayload.sourceLanguage()),
          "Compiled rule",
          RuleSchemaVersion.fromId(compiledPayload.schemaVersion()),
          compiledPayload.matcherAst(),
          compiledPayload.actionIntents());
    } catch (RuntimeException invalidCompilePayload) {
      throw RuleApiException.invalidCompileOutput();
    }
  }
}
