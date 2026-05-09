---
phase: 03-rules-engine
reviewed: 2026-05-09T23:21:09Z
depth: standard
files_reviewed: 120
files_reviewed_list:
  - apps/web/__tests__/rules-feature-contract.test.ts
  - apps/web/app/(protected)/rules/page.tsx
  - apps/web/components/ui/textarea.tsx
  - apps/web/e2e/rules.spec.ts
  - apps/web/features/rules/api/rules-api.ts
  - apps/web/features/rules/components/RuleComposer.tsx
  - apps/web/features/rules/components/RuleList.tsx
  - apps/web/features/rules/components/RulePreviewPanel.tsx
  - apps/web/features/rules/components/RuleTemplateGallery.tsx
  - apps/web/features/rules/components/RulesWorkspace.test.tsx
  - apps/web/features/rules/components/RulesWorkspace.tsx
  - apps/web/features/rules/hooks/use-rules.ts
  - apps/web/features/rules/messages.ts
  - apps/web/features/rules/query-keys.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/lib/api/schema.d.ts
  - apps/web/openapi/openapi.json
  - apps/web/scripts/check-i18n.ts
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java
  - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleDtos.java
  - backend/api/src/main/java/com/zeromail/api/dto/rules/package-info.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - backend/api/src/main/java/com/zeromail/api/error/RuleApiException.java
  - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerPrivacyTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerTenantIsolationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerWave0Test.java
  - backend/core/build.gradle.kts
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/LlmToolProfile.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/RuleCompileGatewayResult.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/SystemPrompts.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/AllowListedTools.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/RuleCompileToolValidator.java
  - backend/core/src/main/java/com/zeromail/core/onboarding/service/OnboardingService.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntent.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntentJsonValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/ActionProposal.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/GmailPreviewUnavailableException.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/MatcherEvaluationState.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/MatcherNode.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/MatcherType.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/PreviewSampleSize.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleActionType.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleAstJsonValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleClarificationQuestion.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleConflictType.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCreateCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationInput.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleId.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleLanguage.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleOrderEntry.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleReorderCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleSchemaVersion.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleStatusView.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateMaterializationResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateStatus.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateView.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleUpdateCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/SemanticIntentMatcher.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleRepository.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateEntity.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateRepository.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/lowlevel/RuleNativeStateUpdater.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/lowlevel/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompileResultValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompilerService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleManagementService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/package-info.java
  - backend/core/src/main/resources/db/changelog/changes/021-rules-engine-schema.yaml
  - backend/core/src/main/resources/db/changelog/changes/022-rule-template-catalog-seed.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/main/resources/prompts/rule-compile-system-prompt.txt
  - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
  - backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/model/RuleCompileSystemPromptTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/model/ToolCallResultCompatibilityTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileGatewayContractTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileToolProfileTest.java
  - backend/core/src/test/java/com/zeromail/core/onboarding/service/OnboardingServiceSelectedTemplatesTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/ai/RuleCompileReferenceDatasetTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/model/RuleModelTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java
  - backend/core/src/test/java/com/zeromail/core/rules/persistence/RuleTemplateCatalogTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/privacy/RulePreviewPrivacyTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/ActionProposalMergerTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceWave0Test.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorWave0Test.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleManagementServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewDataServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceWave0Test.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewWriteBoundaryTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java
  - gradle/libs.versions.toml
findings:
  critical: 3
  warning: 3
  info: 0
  total: 6
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-05-09T23:21:09Z
**Depth:** standard
**Files Reviewed:** 120
**Status:** issues_found

## Summary

Reviewed the Phase 03 rules engine source changes with focus on server-side rule safety, privacy, tenant isolation, transaction boundaries, API contract consistency, generated client consistency, frontend flow defects, and test coverage. The implementation has multiple release-blocking issues: client-supplied compiled rule JSON is not fully validated before persistence, rules mutations can miss CSRF headers in fresh sessions, and a GET endpoint performs durable writes.

## Critical Issues

### CR-01: BLOCKER - Client-supplied compiled JSON can persist invalid or private rule metadata

**File:** `backend/core/src/main/java/com/zeromail/core/rules/model/RuleAstJsonValidator.java:36`

**Issue:** `RuleAstJsonValidator` validates only `schemaVersion`, matcher `type`, boolean children shape, and semantic `deferred`; it never validates required leaf fields like `domain`, `email`, `text`, `operator`, or `days`, and it does not reject unknown fields. `ActionIntentJsonValidator` similarly validates only the action type at `ActionIntentJsonValidator.java:14-25`, not required `labelName` / `instruction`, unknown fields, or bounds. Because `RulesController.compiledPayload(...)` accepts client-provided `matcherAst` and `actionIntents` directly at `RulesController.java:225-232`, and `RuleEntity.replaceDefinition(...)` persists after these shallow validators at `RuleEntity.java:187-190`, a tampered request can store malformed JSON or arbitrary fields such as `prompt`, `completion`, or raw mail snippets in durable JSONB rule columns. Later preview parsing requires these fields (`RulePreviewService.java:320-322` and `RulePreviewService.java:374-385`) and will fail after bad data is already saved.

**Fix:** Make the persistence/API validation use the same strict schema as compilation before saving. Reject unknown fields, enforce all leaf requirements, action-specific payloads, size/depth limits, and return `RuleApiException.invalidCompileOutput()` without writing.

```java
// In RulesController.compiledPayload(...) or a dedicated service validator:
ruleAstJsonValidator.validateMatcherJson(compiledPayload.matcherAst()); // full leaf validation
actionIntentJsonValidator.validateActionIntentsJson(compiledPayload.actionIntents()); // full action validation
return RuleCompileResult.compiled(...);
```

Also add API tests that POST compiled payloads missing `domain`, missing `labelName`, containing unknown `prompt` fields, and deeply nested/unbounded matcher trees, asserting 400 and no row persisted.

### CR-02: BLOCKER - Rules mutations can send stale or missing CSRF tokens

**File:** `apps/web/features/rules/api/rules-api.ts:49`

**Issue:** `JSON_HEADERS` captures `xsrfHeader()` once at module load. In the SPA CSRF flow, an initial authenticated GET can issue or rotate the `XSRF-TOKEN` cookie after this module is imported; every JSON mutation then reuses the stale object at call sites such as `compileRule` and `createRule` (`rules-api.ts:106-119`). Fresh sessions landing directly on `/rules` can load the list successfully but have compile/create/update/reorder/preview PATCH/POST requests rejected for missing CSRF. Other feature API modules call `xsrfHeader()` per request, so this file is inconsistent with the existing pattern.

**Fix:** Build headers at request time.

```ts
function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

await api.POST('/api/rules/compile', {
  body: payload,
  headers: jsonHeaders(),
});
```

Add a rules API/client test that imports the module before setting `document.cookie`, then verifies a later mutation includes the new `X-XSRF-TOKEN`.

### CR-03: BLOCKER - GET /api/rules performs durable writes and bypasses unsafe-method CSRF protections

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java:64`

**Issue:** `listRules()` is a GET endpoint, but it calls `materializeSelectedTemplates(...)` before returning the list (`RulesController.java:67-69`). That service opens `REQUIRES_NEW` transactions and can `saveAndFlush(...)` new rules (`RuleTemplateMaterializationService.java:48-60` and `RuleTemplateMaterializationService.java:155`). Because Spring CSRF protection is applied to unsafe methods, not GET, any authenticated navigation/prefetch/crawler-style GET can mutate a tenant's rules. This violates HTTP safety and turns a read endpoint into a state-changing action.

**Fix:** Keep `GET /api/rules` read-only. Move selected-template materialization behind an explicit POST such as `/api/rules/templates/materialize-selected`, require the XSRF header, and have the frontend call it intentionally before invalidating the rules list.

```java
@GetMapping
public ResponseEntity<RulesListResponse> listRules() {
  UUID tenantId = currentTenantId();
  return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(toRulesList(tenantId, RuleTemplateMaterializationResult.empty()));
}

@PostMapping("/templates/materialize-selected")
public RuleTemplateMaterializationResponse materializeSelectedTemplates() {
  return RuleTemplateMaterializationResponse.from(
      ruleTemplateMaterializationService.materializeSelectedTemplates(currentTenantId()));
}
```

## Warnings

### WR-01: WARNING - Draft preview reports invalid sample sizes as compile failures

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java:181`

**Issue:** Saved preview catches `IllegalArgumentException` and maps it to `error.rules.preview.invalid_sample_size` (`RulesController.java:170-176`), but draft preview catches every `IllegalArgumentException` from parsing or `PreviewSampleSize.normalize(...)` and maps it to `error.rules.compile.invalid` (`RulesController.java:181-192`). A request to `POST /api/rules/preview` with a valid compiled payload and `sampleSize: 51` therefore returns the wrong stable API code.

**Fix:** Validate sample size separately before parsing compiled payload, then reserve `invalidCompileOutput` for matcher/action parsing failures.

```java
try {
  rulePreviewService.normalizeSampleSize(request.sampleSize());
} catch (IllegalArgumentException invalidSampleSize) {
  throw RuleApiException.invalidSampleSize();
}
```

Add an integration test for `/api/rules/preview` with `sampleSize: 51`.

### WR-02: WARNING - Editing an existing rule then clicking Preview previews the old saved rule

**File:** `apps/web/features/rules/components/RulesWorkspace.tsx:150`

**Issue:** `handlePreview()` chooses saved-rule preview whenever `selectedRule?.ruleId !== undefined` (`RulesWorkspace.tsx:155-160`). If a user selects an existing rule, edits text, recompiles, and clicks Preview before saving, the UI calls `/api/rules/{ruleId}/preview` for the persisted old definition instead of `/api/rules/preview` for the newly compiled draft (`RulesWorkspace.tsx:161-165`). The panel can show impact for stale logic while the composer contains different compiled logic.

**Fix:** Track whether the composer differs from the selected rule. Either require saving before previewing an existing rule edit, or call draft preview when the current compiled payload is dirty.

```ts
const dirtyCompiledDraft =
  compileResult?.status === 'compiled' &&
  (sourceText !== (selectedRule?.sourceText ?? '') ||
    compileResult.compiled.matcherAst !== selectedRule?.matcherAst ||
    compileResult.compiled.actionIntents !== selectedRule?.actionIntents);

const result = dirtyCompiledDraft
  ? await previewDraftRuleMutation.mutateAsync({ compiled: compiledResponseToRequest(compileResult.compiled), sampleSize })
  : selectedRule?.ruleId
    ? await previewSavedRuleMutation.mutateAsync({ ruleId: selectedRule.ruleId, payload: { sampleSize } })
    : null;
```

### WR-03: WARNING - Customized materialized templates expose a no-op "Use starter rule" action

**File:** `apps/web/features/rules/components/RuleTemplateGallery.tsx:42`

**Issue:** The gallery disables "Use starter rule" only when `template.materialized && !template.customized`. For a template that is already materialized and customized, the button remains enabled (`RuleTemplateGallery.tsx:41-42`), but the backend unique key prevents another materialization and returns only a skipped result. `RulesWorkspace.handleUseTemplate()` then selects only `createdRules?.[0]` and otherwise does nothing (`RulesWorkspace.tsx:229-235`), so the user gets a clickable action that silently no-ops.

**Fix:** Disable any materialized template action unless there is a real "reset from starter" flow. If a skipped result is expected, surface it explicitly.

```tsx
const disabled = Boolean(template.materialized);
```

Add a component test for `materialized: true, customized: true` verifying the button is disabled or a clear preserved/customized state is shown.

---

_Reviewed: 2026-05-09T23:21:09Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
