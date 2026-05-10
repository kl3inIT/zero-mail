package com.zeromail.api.dto.rules;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.zeromail.core.rules.model.RuleCompileResult;
import com.zeromail.core.rules.model.RulePreviewResult;
import com.zeromail.core.rules.model.RuleStatusView;
import com.zeromail.core.rules.model.RuleTemplateMaterializationResult;
import com.zeromail.core.rules.model.RuleTemplateView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class RuleDtos {

  public static final String STATUS_COMPILED = "compiled";
  public static final String STATUS_CLARIFICATION_REQUIRED = "clarificationRequired";
  public static final String STATUS_INVALID = "invalid";

  private RuleDtos() {}

  public record RulesListResponse(
      List<RuleResponse> rules,
      List<RuleTemplateResponse> templates,
      RuleTemplateMaterializationResponse materialization) {

    public RulesListResponse {
      rules = List.copyOf(rules);
      templates = List.copyOf(templates);
    }
  }

  public record RuleCompileRequest(
      @NotBlank @Size(max = 4000) String sourceText,
      @Size(max = 1000) String clarificationAnswer,
      @Size(max = 2000) String priorCompileContext) {}

  public record RuleCreateRequest(
      @NotBlank @Size(max = 160) String displayName,
      @NotBlank @Size(max = 4000) String sourceText,
      @Valid @NotNull CompiledPayloadRequest compiled) {}

  public record RuleUpdateRequest(
      @NotBlank @Size(max = 160) String displayName,
      @NotBlank @Size(max = 4000) String sourceText,
      @Valid @NotNull CompiledPayloadRequest compiled) {}

  public record RuleEnabledRequest(@NotNull Boolean enabled) {}

  public record RuleReorderRequest(@NotEmpty @Valid List<RuleOrderEntryRequest> entries) {

    public RuleReorderRequest {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }
  }

  public record RuleOrderEntryRequest(
      @NotNull UUID ruleId, @NotNull @PositiveOrZero Integer entityVersion) {}

  public record RulePreviewRequest(Integer sampleSize) {}

  public record RuleDraftPreviewRequest(
      @Valid @NotNull CompiledPayloadRequest compiled, Integer sampleSize) {}

  public record CompiledPayloadRequest(
      @NotBlank String status,
      String sourceLanguage,
      String schemaVersion,
      String matcherAst,
      String actionIntents) {}

  public record RuleResponse(
      UUID ruleId,
      String displayName,
      String sourceText,
      boolean enabled,
      int orderIndex,
      String sourceLanguage,
      String schemaVersion,
      String matcherAst,
      String actionIntents,
      Integer entityVersion,
      Integer lastPreviewedEntityVersion,
      Instant lastPreviewedAt,
      String templateKey,
      Integer templateVersion,
      boolean customized) {

    public static RuleResponse from(RuleStatusView ruleStatusView) {
      return new RuleResponse(
          ruleStatusView.ruleId().value(),
          ruleStatusView.displayName(),
          ruleStatusView.sourceText(),
          ruleStatusView.enabled(),
          ruleStatusView.orderIndex(),
          ruleStatusView.sourceLanguage().id(),
          ruleStatusView.schemaVersion().id(),
          ruleStatusView.matcherAst(),
          ruleStatusView.actionIntents(),
          ruleStatusView.entityVersion(),
          ruleStatusView.lastPreviewedEntityVersion(),
          ruleStatusView.lastPreviewedAt(),
          ruleStatusView.templateKey(),
          ruleStatusView.templateVersion(),
          ruleStatusView.customized());
    }
  }

  public record RuleCompileResponse(
      String status,
      CompiledPayloadResponse compiled,
      ClarificationResponse clarification,
      InvalidCompileResponse invalid) {

    public static RuleCompileResponse from(RuleCompileResult compileResult) {
      return switch (compileResult.status()) {
        case COMPILED ->
            new RuleCompileResponse(
                STATUS_COMPILED, CompiledPayloadResponse.from(compileResult), null, null);
        case CLARIFICATION_REQUIRED ->
            new RuleCompileResponse(
                STATUS_CLARIFICATION_REQUIRED,
                null,
                ClarificationResponse.from(compileResult),
                null);
        case INVALID ->
            new RuleCompileResponse(
                STATUS_INVALID, null, null, InvalidCompileResponse.from(compileResult));
      };
    }
  }

  public record CompiledPayloadResponse(
      String status,
      String sourceLanguage,
      String displayName,
      String schemaVersion,
      String matcherAst,
      String actionIntents) {

    private static CompiledPayloadResponse from(RuleCompileResult compileResult) {
      return new CompiledPayloadResponse(
          STATUS_COMPILED,
          compileResult.sourceLanguage().id(),
          compileResult.displayName(),
          compileResult.schemaVersion().id(),
          compileResult.matcherAst(),
          compileResult.actionIntents());
    }
  }

  public record ClarificationResponse(String language, String question) {

    private static ClarificationResponse from(RuleCompileResult compileResult) {
      return new ClarificationResponse(
          compileResult.clarificationQuestion().language().id(),
          compileResult.clarificationQuestion().question());
    }
  }

  public record InvalidCompileResponse(String reason) {

    private static InvalidCompileResponse from(RuleCompileResult compileResult) {
      return new InvalidCompileResponse(compileResult.failureReason());
    }
  }

  public record RuleTemplateResponse(
      String templateKey,
      int templateVersion,
      String displayName,
      String localizedCopyKey,
      String sourceText,
      String actionSummary,
      String status,
      boolean sourcedFromOnboarding,
      boolean materialized,
      boolean customized) {

    public static RuleTemplateResponse from(RuleTemplateView ruleTemplateView) {
      return new RuleTemplateResponse(
          ruleTemplateView.templateKey(),
          ruleTemplateView.templateVersion(),
          ruleTemplateView.displayName(),
          ruleTemplateView.localizedCopyKey(),
          ruleTemplateView.sourceText(),
          ruleTemplateView.actionSummary(),
          ruleTemplateView.status().id(),
          ruleTemplateView.sourcedFromOnboarding(),
          ruleTemplateView.materialized(),
          ruleTemplateView.customized());
    }
  }

  public record RuleTemplateMaterializationResponse(
      int createdCount,
      int skippedCount,
      int customizedPreservedCount,
      List<RuleResponse> createdRules,
      List<SkippedTemplateResponse> skippedTemplates) {

    public static RuleTemplateMaterializationResponse empty() {
      return new RuleTemplateMaterializationResponse(0, 0, 0, List.of(), List.of());
    }

    public static RuleTemplateMaterializationResponse from(
        RuleTemplateMaterializationResult materializationResult) {
      return new RuleTemplateMaterializationResponse(
          materializationResult.createdCount(),
          materializationResult.skippedCount(),
          materializationResult.customizedPreservedCount(),
          materializationResult.createdRules().stream().map(RuleResponse::from).toList(),
          materializationResult.skippedTemplates().stream()
              .map(SkippedTemplateResponse::from)
              .toList());
    }

    public RuleTemplateMaterializationResponse {
      createdRules = List.copyOf(createdRules);
      skippedTemplates = List.copyOf(skippedTemplates);
    }
  }

  public record SkippedTemplateResponse(String templateKey, String reason) {

    private static SkippedTemplateResponse from(
        RuleTemplateMaterializationResult.SkippedTemplate skippedTemplate) {
      return new SkippedTemplateResponse(
          skippedTemplate.templateKey(), skippedTemplate.reason().name());
    }
  }

  public record RulePreviewResponse(
      ImpactSummaryResponse impactSummary,
      List<PreviewRowResponse> rows,
      boolean savedRuleMarkedPreviewed) {

    public static RulePreviewResponse from(RulePreviewResult previewResult) {
      return new RulePreviewResponse(
          ImpactSummaryResponse.from(previewResult.impactSummary()),
          previewResult.rows().stream().map(PreviewRowResponse::from).toList(),
          previewResult.savedRuleMarkedPreviewed());
    }

    public RulePreviewResponse {
      rows = List.copyOf(rows);
    }
  }

  public record ImpactSummaryResponse(
      int sampleSize,
      int sampledMessageCount,
      int matchedCount,
      Map<String, Integer> proposedActionCounts,
      int deferredCount,
      int conflictCount,
      boolean noWriteNotice,
      String noWriteNoticeKey) {

    private static ImpactSummaryResponse from(RulePreviewResult.ImpactSummary impactSummary) {
      return new ImpactSummaryResponse(
          impactSummary.sampleSize(),
          impactSummary.sampledMessageCount(),
          impactSummary.matchedCount(),
          impactSummary.proposedActionCounts(),
          impactSummary.deferredCount(),
          impactSummary.conflictCount(),
          impactSummary.noWriteNotice(),
          impactSummary.noWriteNoticeKey());
    }

    public ImpactSummaryResponse {
      proposedActionCounts = Map.copyOf(proposedActionCounts);
    }
  }

  public record PreviewRowResponse(
      String gmailMessageId,
      String gmailThreadId,
      String sanitizedSenderEmail,
      String sanitizedSenderDomain,
      String sanitizedSubjectExcerpt,
      Instant internalDate,
      List<String> gmailLabelIds,
      boolean matched,
      List<ActionChipResponse> proposedActionChips,
      List<EvidenceChipResponse> matchedEvidenceChips,
      List<EvidenceChipResponse> deferredEvidenceChips,
      List<ConflictChipResponse> conflictChips) {

    private static PreviewRowResponse from(RulePreviewResult.PreviewRow previewRow) {
      return new PreviewRowResponse(
          previewRow.gmailMessageId(),
          previewRow.gmailThreadId(),
          previewRow.sanitizedSenderEmail(),
          previewRow.sanitizedSenderDomain(),
          previewRow.sanitizedSubjectExcerpt(),
          previewRow.internalDate(),
          previewRow.gmailLabelIds(),
          previewRow.matched(),
          previewRow.proposedActionChips().stream().map(ActionChipResponse::from).toList(),
          previewRow.matchedEvidenceChips().stream().map(EvidenceChipResponse::from).toList(),
          previewRow.deferredEvidenceChips().stream().map(EvidenceChipResponse::from).toList(),
          previewRow.conflictChips().stream().map(ConflictChipResponse::from).toList());
    }

    public PreviewRowResponse {
      gmailLabelIds = List.copyOf(gmailLabelIds);
      proposedActionChips = List.copyOf(proposedActionChips);
      matchedEvidenceChips = List.copyOf(matchedEvidenceChips);
      deferredEvidenceChips = List.copyOf(deferredEvidenceChips);
      conflictChips = List.copyOf(conflictChips);
    }
  }

  public record ActionChipResponse(
      String actionTypeId,
      String safeLabel,
      List<UUID> contributingRuleIds,
      List<String> evidenceIds) {

    private static ActionChipResponse from(RulePreviewResult.ActionChip actionChip) {
      return new ActionChipResponse(
          actionChip.actionTypeId(),
          actionChip.safeLabel(),
          actionChip.contributingRuleIds(),
          actionChip.evidenceIds());
    }

    public ActionChipResponse {
      contributingRuleIds = List.copyOf(contributingRuleIds);
      evidenceIds = List.copyOf(evidenceIds);
    }
  }

  public record EvidenceChipResponse(String matcherNodeId, String reasonKey) {

    private static EvidenceChipResponse from(RulePreviewResult.EvidenceChip evidenceChip) {
      return new EvidenceChipResponse(evidenceChip.matcherNodeId(), evidenceChip.reasonKey());
    }
  }

  public record ConflictChipResponse(
      String conflictTypeId, List<UUID> contributingRuleIds, Map<String, String> metadata) {

    private static ConflictChipResponse from(RulePreviewResult.ConflictChip conflictChip) {
      return new ConflictChipResponse(
          conflictChip.conflictTypeId(),
          conflictChip.contributingRuleIds(),
          conflictChip.metadata());
    }

    public ConflictChipResponse {
      contributingRuleIds = List.copyOf(contributingRuleIds);
      metadata = Map.copyOf(metadata);
    }
  }
}
