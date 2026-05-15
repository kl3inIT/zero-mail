---
phase: 03-rules-engine
reviewed: 2026-05-10T10:35:08Z
depth: standard
files_reviewed: 99
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
  - backend/core/src/main/java/com/zeromail/core/rules/model/PreviewSampleSize.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleAstJsonValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleClarificationQuestion.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleConflictType.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCreateCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationInput.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleOrderEntry.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleReorderCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleStatusView.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateMaterializationResult.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateView.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleUpdateCommand.java
  - backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java
  - backend/core/src/main/java/com/zeromail/core/rules/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateEntity.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateRepository.java
  - backend/core/src/main/java/com/zeromail/core/rules/persistence/lowlevel/RuleNativeStateUpdater.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompileResultValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompilerService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleManagementService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java
  - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java
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
  blocker: 3
  warning: 10
  info: 8
  total: 21
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-05-10T10:35:08Z
**Depth:** standard
**Files Reviewed:** 99
**Status:** issues_found

## Summary

Phase 03 ships a thorough rules-engine slice with strong architectural discipline: tenant-scoped queries throughout, rules domain isolated from Spring AI (verified by `RulesBoundaryArchTest`), preview path isolated from Gmail write boundaries (`RulePreviewWriteBoundaryTest`), JSON validators with bounded depth/length, RE2J for regex matching, and a comprehensive test surface (privacy, tenant isolation, optimistic locking, materialization race). Privacy invariants look good — DTOs and ProblemDetail bodies do not echo raw user content; logs use the `event=… tenantId={}` shape consistently.

The blockers below are correctness/safety regressions rather than architectural drift:

1. `entityManager.clear()` inside native rule-state updates can detach entities still in use by the outer transaction (the `RulePreviewService.preview` flow loads `orderedRules` before calling `markPreviewSucceeded`, and the call to `clear()` invalidates every other entity in that session).
2. The rule-compile path runs the structured JSON payload through `JsoupHtmlStripSanitizer`, which interprets `<` / `>` characters in the user's `sourceText` as HTML tags and silently strips them — the LLM receives a payload missing user words, with no audit trail of the deletion.
3. The frontend POSTs `templates/materialize-selected` on every page load to compensate for `RulesController.listRules()` not materializing templates server-side, contradicting locked decision D-C2 ("API is the source of truth … do not make materialization a frontend-only side effect").

The warnings below cover narrower correctness gaps: missing client-supplied entity version on `PUT /api/rules/{ruleId}`, off-by-one in optimistic frontend reorder, a template seed referencing a Gmail category id the conflict-detector cannot recognize, NPE risk in the JDBC row mapper for null `label_ids` / `observed_at`, asymmetric safety-violation logging between BYOK and platform rule-compile paths, and Jackson 2.x annotation imports across rules-domain enums (in conflict with the project's "no Jackson 2.x" hard rule).

A second-pass walkthrough added two more warnings: **WR-10** captures a now-obsolete `LooseClient` cast in `apps/web/lib/api/client.ts` that strips the generated OpenAPI typing now that `pnpm generate:api` produced a real `paths` and forces every feature/api callsite to reintroduce types via `as ApiMethodResult<T>` boilerplate; and **WR-11** flags that the load-bearing `messages.ts` → `i18n/messages/{vi,en}.json` build pipeline (via `pnpm i18n:build` chained into `pnpm build`) is undocumented in `CLAUDE.md` / `CONVENTIONS.md` / `apps/web/AGENTS.md`. Four additional Info entries (IN-05 through IN-08) cover error-code constant centralization, deferred lib/api boilerplate hoisting, an unused RSC prefetch+hydration opportunity in the rules route, and the `features/llm/` BYOK-only rename. Four false-positive concerns are documented in the **Dismissed Concerns** section so a future `--fix` run does not re-raise them.

## Blocker Issues

### CR-01: `RuleNativeStateUpdater.markPreviewSucceeded` calls `entityManager.clear()` inside the outer transaction

**File:** `backend/core/src/main/java/com/zeromail/core/rules/persistence/lowlevel/RuleNativeStateUpdater.java:39,60`
**Issue:** Both `markPreviewSucceeded` and `updateEnabled` invoke `entityManager.clear()` after `executeUpdate()`. They are called from within the outer `@Transactional` flow of `RulePreviewService.previewSavedRule`, which has already loaded `orderedRules` via `ruleRepository.findOrderedByTenantId` to construct preview candidates and then loaded the row again in `RuleManagementService.markPreviewSucceeded`. `entityManager.clear()` detaches every managed entity from the persistence context — every entity loaded earlier in the transaction is now detached, including the previously loaded `RuleEntity` whose state we just updated. The subsequent `findRuleOrThrow` in `markPreviewSucceeded` (line 109 of `RuleManagementService`) re-issues a fresh SELECT against the DB, so it works by accident, but any downstream code that touches a previously loaded entity in the same transaction will silently re-fetch or NPE on a detached lazy proxy. The defensive `clear()` is also unnecessary — Hibernate's first-level cache is stale only for the row that the native UPDATE just touched; selectively evicting that one entity (`entityManager.detach(loadedRuleEntity)`) is the correct fix.
**Fix:**
```java
public boolean markPreviewSucceeded(
    UUID tenantId, UUID ruleId, Integer entityVersion, Instant previewedAt) {
  int updatedRows =
      entityManager
          .createNativeQuery(
              """
              update rules
              set last_previewed_entity_version = ?,
                  last_previewed_at = ?,
                  updated_at = now()
              where tenant_id = ?
                and id = ?
                and version = ?
              """)
          .setParameter(1, entityVersion)
          .setParameter(2, Timestamp.from(previewedAt))
          .setParameter(3, tenantId)
          .setParameter(4, ruleId)
          .setParameter(5, entityVersion)
          .executeUpdate();
  // Do NOT clear the whole persistence context. The caller will reload
  // the row whose version+timestamp we just bumped via native SQL with a
  // fresh findByIdAndTenantId.
  return updatedRows == 1;
}
```
Apply the same change to `updateEnabled`.

### CR-02: Rule-compile JSON payload passes through `JsoupHtmlStripSanitizer`, silently deleting `<…>` substrings from the user's rule text

**File:** `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java:264`
**Issue:** `compileRule` builds a JSON payload in `RuleCompilerService.buildCompilerPayload` (containing the user's raw `sourceText`, the schema version, allowed matcher/action ids, and optional clarification answer) and passes it as the `rawHtml` argument to `sanitizationPipeline.sanitize(...)`. The sanitization pipeline's `JsoupHtmlStripSanitizer` interprets the input as HTML — any `<` or `>` characters in `sourceText` (very common in real rules: "subject contains `<reply requested>`", quoted angle-bracket addresses like `<billing@stripe.com>`, regex literals containing `<`/`>`, etc.) are parsed as malformed HTML tags and silently stripped. The downstream LLM then receives a payload with the user's words deleted, the model produces a wrong matcher AST or asks for clarification on text it never saw, and the user has no way to detect that the rule they typed differs from the rule the model compiled. This is silent prompt corruption that bypasses the compile validator (which only checks the model's output, not the prompt). `chat()` at least feeds in `rawHtml` from real Gmail messages, so HTML stripping is appropriate; `compileRule` does not.
**Fix:** Either (a) introduce a separate sanitization profile for rule-compile that skips the Jsoup HTML-strip stage and only runs NFC normalization + Unicode-tag strip + token truncation, or (b) base64- or JSON-string-encode the payload such that user text is in a string field and HTML stripping inside a properly quoted JSON string value is a no-op. The simplest immediate fix is (a):
```java
// In SanitizationPipeline, add:
public SanitizationContext sanitizeStructuredJson(String compilerPayload) {
  // Skip Jsoup HTML strip — this is a JSON envelope; user text inside
  // it must reach the LLM character-for-character. Still apply NFC
  // normalization, unicode-tag strip, and token truncation.
}
// In LlmGatewayImpl.compileRule:
SanitizationContext sanitizedContext =
    sanitizationPipeline.sanitizeStructuredJson(compilerPayload);
```
Add a regression test that compiles a rule containing `<billing@stripe.com>` and asserts the literal substring survives the gateway.

### CR-03: `GET /api/rules` does not materialize templates; frontend issues a separate POST instead, contradicting locked decision D-C2

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java:79-92` and `apps/web/features/rules/components/RulesWorkspace.tsx:82-88`
**Issue:** `03-CONTEXT.md` decision D-C2 (locked): "First `GET /api/rules` materializes templates idempotently. The API is the source of truth. The first rules API read should initialize template-derived rules so all consumers see the same state; do not make materialization a frontend-only side effect." `RulesController.listRules()` builds `RulesListResponse` with `RuleTemplateMaterializationResponse.empty()` and never invokes `ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId)`. To compensate, `RulesWorkspace.tsx` fires a `POST /api/rules/templates/materialize-selected` after every successful list query, gated only by a `useRef` boolean that resets across page reloads. This violates the locked decision in two concrete ways: (1) other clients (mobile, CLI, future Phase 4 worker that boots from rules state) will not see template-derived rules until they too POST; (2) the network sequence becomes `GET 200 → POST 200` for every fresh page render, doubling the round-trip and making the frontend the only path that can heal a tenant whose templates were selected but never materialized.
**Fix:** Move the materialization call into `RulesController.listRules()` so the GET is the source of truth, and remove the frontend `useEffect` that POSTs `materialize-selected`:
```java
@GetMapping
public ResponseEntity<RulesListResponse> listRules() {
  UUID tenantId = currentTenantId();
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
```
And in `RulesWorkspace.tsx`, drop `selectedTemplatesMaterializationStarted` and the surrounding `useEffect`.

## Warning Issues

### WR-01: `PUT /api/rules/{ruleId}` accepts no client-supplied entity version — silent overwrite of concurrent edits

**File:** `backend/api/src/main/java/com/zeromail/api/dto/rules/RuleUpdateRequest.java:8`
**Issue:** `RuleUpdateRequest` has no `entityVersion` field, so the controller cannot reject a stale update before mutating. The reorder endpoint correctly requires `entityVersion` per entry (`RuleOrderEntryRequest`) and `RuleNativeStateUpdater` performs server-side optimistic locking on enable/disable/preview, but plain `PUT /api/rules/{ruleId}` only relies on Hibernate's `@Version` after the row is loaded. Two clients editing the same rule from separate tabs both load the same version; the first PUT wins and bumps to v+1, the second PUT submits its mutation against the now-stale entity; Hibernate detects the version mismatch on flush and throws `OptimisticLockingFailureException`, which `GlobalExceptionHandler` translates to a 409. The flow eventually rejects the conflict, but only after the user has filled in the form and pressed Save — surfacing `entityVersion` in the request lets the client detect and explain the conflict before submitting, matching the contract the reorder endpoint already uses.
**Fix:** Add `entityVersion` to `RuleUpdateRequest`, validate it inside `RuleManagementService.update` before mutating (throwing `RuleValidationException.versionMismatch()` on mismatch), and have the controller surface it as 409.

### WR-02: `useReorderRules` optimistic update uses 1-based `orderIndex`, server uses 0-based

**File:** `apps/web/features/rules/hooks/use-rules.ts:113`
**Issue:** The optimistic update sets `orderIndex: index + 1`, but `RuleManagementService.reorder` writes 0-based indices (`for (int orderIndex = 0; orderIndex < command.orderedEntries().size(); orderIndex++)`, line 164 of `RuleManagementService`). After the mutation completes, `onSuccess` caches the server response (0-based) but the optimistic state was 1-based, so until the list refetch fires the UI shows mixed 1-based optimistic data and 0-based server data — the `compareRulesByOrder` sort in `RulesWorkspace.tsx:334-336` produces a brief visible re-shuffle, and any code that reads `orderIndex + 1` from the optimistic state will display a row count one higher than reality.
**Fix:**
```ts
queryClient.setQueryData<RuleListResponse>(rulesKeys.list(), (currentList) => {
  if (!currentList) return currentList;
  return {
    ...currentList,
    rules: orderedRules.map((rule, index) => ({ ...rule, orderIndex: index })),
  };
});
```

### WR-03: `pin-calendar` template seed uses `CATEGORY_PERSONAL`, which the conflict detector cannot recognize

**File:** `backend/core/src/main/resources/db/changelog/changes/022-rule-template-catalog-seed.yaml:47`
**Issue:** The `pin-calendar` row emits `{"type":"GMAIL_CATEGORY_PRESENT","category":"CATEGORY_PERSONAL"}`. `ActionProposalMerger.normalizeCategoryLabel` strips a `category_` prefix and matches against the hard-coded set `Set.of("primary","promotions","social","updates","forums")` — `CATEGORY_PERSONAL` normalizes to `personal`, which is NOT in that set. Result: the conflict detector silently skips category-vs-label warnings for any rule that touches Personal. Separately, Gmail's actual category taxonomy in the inbox UI is Primary / Social / Promotions / Updates / Forums — there is no PERSONAL category as a Gmail-side label id, so the matcher will never fire on real messages either. The template will materialize for every onboarding tenant and silently never match anything.
**Fix:** Replace `CATEGORY_PERSONAL` with `CATEGORY_UPDATES` (or another category that exists in `GMAIL_CATEGORY_NAMES`) and align the seed matcher with what `ActionProposalMerger.GMAIL_CATEGORY_NAMES` recognizes. Long-term, source the allowed category set from a single domain enum so the seed and the merger cannot drift (see IN-03).

### WR-04: `GmailPreviewReadService.findRecentObservedMessages` NPEs on null `label_ids` or `observed_at`

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java:151,153`
**Issue:** The row mapper does `(String[]) resultSet.getArray("label_ids").getArray()` and `resultSet.getTimestamp("observed_at").toInstant()` with no null guard. If a `mail_message_observed` row has a NULL `label_ids` (e.g., from a Pub/Sub watch that arrived before any classification ran) or a NULL `observed_at`, the mapper throws `NullPointerException`, which propagates up through `RulePreviewService.preview` and `GlobalExceptionHandler`, surfacing as a 500 with a generic `error.fallback` code. Even if the schema enforces NOT NULL today, defensive null-handling would degrade gracefully and reduce the blast radius of any future schema relaxation.
**Fix:**
```java
java.sql.Array labelIdsArray = resultSet.getArray("label_ids");
String[] labelIds =
    labelIdsArray == null ? new String[0] : (String[]) labelIdsArray.getArray();
java.sql.Timestamp observedAtTimestamp = resultSet.getTimestamp("observed_at");
Instant observedAt =
    observedAtTimestamp == null ? Instant.EPOCH : observedAtTimestamp.toInstant();
```

### WR-05: BYOK `compileRule` path silently swallows `SafetyViolationException` without symmetric logging

**File:** `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java:267-276,438-484`
**Issue:** The platform `callPlatformModelClientWithCreditLedger` path catches `SafetyViolationException` and emits `event=llm_safety_violation tenantId={} callSite={} reason={}` (line 397-401). The BYOK `callViaByokModelClient` path has only a `finally` block that zero-fills the decrypted key — no `try { ... } catch (SafetyViolationException safetyViolation) { log; throw; }`. When a BYOK-bound user's tool call is rejected by `RuleCompileToolValidator`, the safety violation propagates up to `GlobalExceptionHandler.onSafetyViolation` (which does log it once, but with no `callSite=` label), and the structured per-call-site log line emitted by the platform path is missing for BYOK. Operators correlating safety-violation rates by call-site will under-count BYOK violations and miss patterns specific to BYOK providers.
**Fix:** Wrap the BYOK call body in the same try/catch shape as the platform path and emit `event=llm_safety_violation tenantId={} callSite={} reason={}` before rethrowing.

### WR-06: Rules-domain enums import Jackson 2 annotations (`com.fasterxml.jackson.annotation.*`) in conflict with project hard rule

**File:** `backend/core/src/main/java/com/zeromail/core/rules/domain/RuleConflictType.java:6-7` (and `RuleLanguage`, `RuleSchemaVersion`, `RuleTemplateStatus`, `RuleActionType`, `MatcherType`, `MatcherEvaluationState` — 7 files total)
**Issue:** `CLAUDE.md` "Hard 'do not use' list" includes "Jackson 2.x assumptions (Boot 4 ships Jackson 3.x)". All seven rules-domain enums use `import com.fasterxml.jackson.annotation.JsonCreator;` and `JsonValue` instead of `tools.jackson.annotation.*`. Jackson 3 (`tools.jackson.databind`) is what every service in `core.rules.service` uses for tree parsing, but the annotations stay on the legacy package. While Jackson 3 currently retains binary compatibility for these annotations via the `jackson-annotations` shim, this is exactly the "Boot 3 / Jackson 2 assumption" `feedback_spring_boot_4_breaking_changes.md` warns about. If the shim is removed in a future Boot 4 minor release, the annotations stop being honored — `MatcherType.SENDER_DOMAIN` keeps roundtripping by accident (Jackson default uses enum names), but `RuleConflictType.MULTIPLE_DIFFERENT_LABELS` would serialize as the enum name in uppercase rather than the lowercase `multiple_different_labels` id, breaking the API contract silently.
**Fix:** Replace all `import com.fasterxml.jackson.annotation.*` in `core.rules.domain.*` (and the parallel `core.llm.domain.*` files surfaced by the grep) with `import tools.jackson.annotation.*`. Verify with `mcp__jetbrains__get_file_problems` afterward.

### WR-07: `RuleTemplateCatalogService.toView` issues an N+1 lookup when listing templates inside a single read-only transaction

**File:** `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java:85-99`
**Issue:** `listActiveTemplates` calls `ruleRepository.findByTenantIdAndTemplateKey(tenantId, …)` once per template inside a single `@Transactional(readOnly=true)` boundary. For the v1 starter set of 4 templates this is 4 extra SELECTs per `GET /api/rules/templates`; for any future template growth (the SPEC notes admin/template-catalog management is deferred but planned) this becomes a per-request hot path. While performance is out of v1 review scope, this also has a subtle correctness angle: the read-only transaction commits at the end of the controller request, but inside that transaction any `update`/`enable` mutation arriving on a sibling rule is invisible until the next request, so two consecutive `GET /api/rules/templates` calls separated by a `POST /api/rules/{id}/preview` may show stale `materialized` / `customized` flags depending on the isolation level. Since the writes use `@Transactional` (REQUIRED) and the reads use `@Transactional(readOnly=true)`, the transactional isolation on PostgreSQL (READ_COMMITTED by default) means the second read picks up the commit — but only after the first read's transaction also commits.
**Fix:** Replace per-template lookups with a single `findByTenantIdAndTemplateKeyIn(...)` query and build a `Map<String, RuleEntity>` lookup once before mapping templates.

### WR-08: `RulesController.compiledPayload` rethrows generic `RuntimeException`, masking validation failures

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java:262`
**Issue:** `compiledPayload(...)` catches `RuntimeException` and converts to `RuleApiException.invalidCompileOutput()`. This swallows specific failures from `RuleLanguage.fromId` / `RuleSchemaVersion.fromId` (which throw `NoSuchElementException` per project convention), JSON parsing failures, and any unexpected NPE. Each of those has a distinct privacy/operator interpretation: an unexpected NPE inside the controller is an internal bug worth alerting on, but it currently surfaces to the client as a generic 400 `error.rules.compile.invalid`. Tighten the catch to `IllegalArgumentException | NoSuchElementException` so unexpected runtime errors propagate to `GlobalExceptionHandler.onIllegalState` (or are caught by an explicit handler) and reach operator logs.
**Fix:**
```java
private static RuleCompileResult compiledPayload(CompiledPayloadRequest compiledPayload) {
  try {
    return RuleCompileResult.compiled(
        RuleLanguage.fromId(compiledPayload.sourceLanguage()),
        "Compiled rule",
        RuleSchemaVersion.fromId(compiledPayload.schemaVersion()),
        compiledPayload.matcherAst(),
        compiledPayload.actionIntents());
  } catch (IllegalArgumentException | NoSuchElementException invalidCompilePayload) {
    throw RuleApiException.invalidCompileOutput();
  }
}
```

### WR-09: `RuleCompilerService.callGateway` rethrow pattern downgrades any future checked exception to `IllegalStateException`

**File:** `backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompilerService.java:58-67`
**Issue:** The `try { return ScopedValue.where(...).call(callable); } catch (RuntimeException) { throw; } catch (Exception) { wrap; }` shape is correct for the current `LlmGateway.compileRule` signature (which declares no checked exceptions), but it relies on a hand-written rethrow-or-wrap because `Carrier.call(Callable)` declares `throws Exception`. The problem: any future checked exception added to the gateway interface is silently wrapped in `IllegalStateException` here, losing its concrete type and breaking `GlobalExceptionHandler` routing. Use `ScopedValue.where(...).get(Supplier)` (Java 25's unchecked variant) so the compiler enforces unchecked-only at the call site.
**Fix:**
```java
private RuleCompileGatewayResult callGateway(RuleCompileCommand command, String compilerPayload) {
  return ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
      .get(() -> llmGateway.compileRule(CallSite.PREVIEW, compilerPayload));
}
```
Apply the same change to `OnboardingService.selectedEnabledTemplateKeys` (lines 50-65) and `RuleTemplateMaterializationService.executeInTenantScope` (lines 187-195) for the same reason.

### WR-10: `LooseClient` cast in `lib/api/client.ts` defeats the generated OpenAPI typing now that `schema.d.ts` is real

**File:** `apps/web/lib/api/client.ts:15-38`
**Issue:** The placeholder cast was introduced when `schema.d.ts` had no `paths` and route components needed an ergonomic untyped surface. Phase 03 generated a real 2,851-line `schema.d.ts` (`pnpm generate:api`) that includes every `/api/rules*` operation. The `as unknown as LooseClient` cast still strips the `paths` generic from `typedApi`, so every callsite must reintroduce types manually — see `apps/web/features/rules/api/rules-api.ts` where every `await api.GET/POST/PUT/PATCH/DELETE(...)` is post-cast as `ApiMethodResult<RuleListResponse>` / `<RuleResponse>` / etc., losing path-parameter validation and request-body shape checking. Other features (`account`, `gmail`, `llm`, `onboarding`, `triage`) hide the same loss behind ad-hoc `as ResultType` casts at the return statement. The generated `paths` would already provide all of this for free.
**Fix:** Remove `LooseClient` and export `typedApi` directly:
```ts
import createClient from 'openapi-fetch';
import { getApiBase } from './base-url';
import type { paths } from './schema';

export const api = createClient<paths>({
  baseUrl: getApiBase(),
  credentials: 'include',
});
```
Then sweep callsites:
- `features/rules/api/rules-api.ts` — drop the local `ApiMethodResult<T>` type and every `as ApiMethodResult<...>` cast; `api.GET('/api/rules', {})` will return the correctly typed `{ data: RulesListResponse | undefined; error: ApiError | undefined; response: Response }` directly. Replace `throwIfFailed(result, ...)` with the same shape but without the cast.
- Other features whose `as ResponseType` casts wrap successful-data return statements should also be removable (verify per-file). Anywhere the generated typing diverges from a real server contract, fix the OpenAPI doc, not the cast.
- `i18n/components/LanguageSwitcher.tsx` and the architecture tests in `__tests__/architecture/feature-folders.test.ts` already use the typed surface, so no change needed there.

### WR-11: `messages.ts` ↔ `i18n/messages/{vi,en}.json` build pipeline is undocumented

**File:** `apps/web/scripts/merge-feature-i18n.ts`, `apps/web/package.json` (build script), `CONVENTIONS.md`
**Issue:** Per-feature `messages.ts` (e.g. `features/rules/messages.ts`) is the source-of-truth for new strings, but next-intl reads from `i18n/messages/{vi,en}.json` at runtime. The merge happens via `pnpm i18n:build` chained into `pnpm build`, but this pipeline is not documented anywhere — `CLAUDE.md`, `CONVENTIONS.md`, and `apps/web/AGENTS.md` are silent on it. A developer who edits `vi.json` directly to fix a translation will see the change reverted on the next build. A developer who forgets to add new keys to `messages.ts` will see them disappear from the bundle. There is no parity test that fails CI when the two diverge.
**Fix:** Add a short section to `CONVENTIONS.md` (front-end conventions block) documenting:
1. Where to add new keys (`features/<feature>/messages.ts`).
2. Why JSON files are generated artifacts (do not edit by hand outside the merge script's output).
3. The `pnpm i18n:build` step and when it runs (`prebuild`).
4. The `pnpm i18n:check` script's contract (drift detector — is it currently invoked in CI?).
Also verify `i18n:check` is wired into CI / pre-commit, otherwise drift is only caught on build failure.

## Info Issues

### IN-01: `RulePreviewService.preview` is annotated `@Transactional` but only ever called via self-invocation

**File:** `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java:110`
**Issue:** `preview(RulePreviewCommand)` is `@Transactional`, but its only callers are `previewSavedRule` and `previewDraft` on the same bean. Self-invocation bypasses Spring's transactional proxy, so the inner annotation has no runtime effect — the transaction comes from the public entry point. Either drop the annotation (it's misleading) or make `preview` `private` so it can't be misused by a future external caller expecting a transaction.
**Fix:** Drop `@Transactional` from `preview` and consider making it `private`.

### IN-02: `RuleCreateCommand` does not validate `ruleId` non-null in its compact constructor

**File:** `backend/core/src/main/java/com/zeromail/core/rules/model/RuleCreateCommand.java`
**Issue:** `RuleManagementService.create` consumes `command.ruleId()` directly when building the `RuleEntity`. If `RuleCreateCommand` is ever instantiated without a generated id (e.g., a future API path that forgets to pre-allocate one), the entity constructor will fail later with a less-specific message. Add `Objects.requireNonNull(ruleId, "ruleId")` in the record's compact constructor for fail-loud behavior consistent with the rest of the codebase.
**Fix:** Add the requireNonNull guard inside the `RuleCreateCommand` compact constructor.

### IN-03: `ActionProposalMerger.GMAIL_CATEGORY_NAMES` duplicates Gmail's external taxonomy as a magic literal

**File:** `backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java:25-26`
**Issue:** `Set.of("primary","promotions","social","updates","forums")` is a magic literal that lives outside any enum or shared constant. The same taxonomy is referenced implicitly in the `pin-calendar` seed (WR-03), in `RuleEvaluationInput.hasGmailCategory` normalization, and in the Gmail label-id case-sensitivity behavior. Centralize this as a `GmailCategory` enum (with `IdentifiedEnum`/`fromId`) so the seed, the matcher validator, and the merger cannot drift.
**Fix:** Add `GmailCategory` enum in `core.gmail.domain` with the five canonical category ids and import it from both the merger and the seed-generation path.

### IN-04: `summarizeCompiledJson` in `RuleComposer` falls back to raw JSON slice on parse failure

**File:** `apps/web/features/rules/components/RuleComposer.tsx:204-213`
**Issue:** `try { JSON.parse(jsonText) } catch { return [jsonText.slice(0, 80)]; }` — if the compiler ever returns a string that is not valid JSON (today the validator prevents this, but the OpenAPI client typing does not), the user sees the first 80 characters of the raw payload as a chip in the "What Zero Mail understood" review card. The fallback chip will render visually broken (fragments of `{"schemaVersion":"rules.v1"...`) and confuses the affordance. Render a generic fallback string instead. Not a privacy leak (the rule's own author wrote `sourceText` and the matcherAst is server-validated), but it is a UX regression and a misuse of the affordance.
**Fix:**
```ts
} catch {
  return [fallback];
}
```

### IN-05: Rules feature duplicates server error-code string literals instead of using a shared constant module

**File:** `apps/web/features/rules/components/RulesWorkspace.tsx:392-398`, `apps/web/e2e/rules.spec.ts:164`
**Issue:** `'error.billing.insufficient'`, `'error.rules.insufficient_credits'`, and `'error.rules.gmail.unavailable'` appear as bare string literals in `isInsufficientCredit` / `isGmailUnavailable` and are also re-typed in the e2e spec's mocked-response builder. Backend `ErrorCodes.java` is the source of truth; frontend has `lib/api/errors.ts` for code → translation-key mapping but no constant module. A typo in one branch (e.g. `'error.rules.insufficentCredits'`) would silently flow through to the unknown-fallback path with no test failure. Add `apps/web/lib/api/error-codes.ts` exporting `RuleErrorCode` / `BillingErrorCode` enums (or a `const ErrorCode = { ... } as const` object) generated from the OpenAPI `ApiError.code` enum if the schema declares one, otherwise hand-mirrored with a contract test against the backend constants file.
**Fix:**
```ts
// apps/web/lib/api/error-codes.ts
export const ErrorCode = {
  BillingInsufficient: 'error.billing.insufficient',
  RulesInsufficientCredits: 'error.rules.insufficient_credits',
  RulesGmailUnavailable: 'error.rules.gmail.unavailable',
  RulesVersionMismatch: 'error.rules.version_mismatch',
  // ...
} as const;
export type ErrorCodeValue = typeof ErrorCode[keyof typeof ErrorCode];
```
Then `RulesWorkspace.tsx` becomes `code === ErrorCode.BillingInsufficient || code === ErrorCode.RulesInsufficientCredits`. Add a Vitest contract test that walks both this constant and `backend/api/.../ErrorCodes.java` (read as text) to fail when one drifts.

### IN-06: `rules-api.ts` reimplements `ApiMethodResult<T>`, `jsonHeaders`, `throwIfFailed` locally instead of in `lib/api/`

**File:** `apps/web/features/rules/api/rules-api.ts:43-64`
**Issue:** Today only `rules-api.ts` defines this trio; `account-api.ts`, `gmail-api.ts`, `llm-api.ts`, `onboarding-api.ts`, and `triage-api.ts` inline `if (error || !response.ok) throw …` per call. Hoisting `throwIfFailed` and `jsonHeaders` into `lib/api/client.ts` would let those features drop ~3 lines per endpoint, but it is not strictly a duplicate yet — only `rules-api.ts` uses the helpers. Note the symmetry with WR-10: removing the `LooseClient` cast eliminates `ApiMethodResult<T>` entirely (the typed `api` already returns the correct discriminated shape), so do that sweep first and revisit hoisting `throwIfFailed`/`jsonHeaders` only if a second feature converts.
**Fix:** Defer until a second feature reaches for the same helper. Apply WR-10 first.

### IN-07: `app/(protected)/rules/page.tsx` is a Server Component but performs no server-side prefetch + hydration

**File:** `apps/web/app/(protected)/rules/page.tsx`
**Issue:** The route file is a synchronous Server Component that renders only the `<RulesWorkspace />` client shell. The first paint shows the `RuleList` "loading" skeleton + `RulesWorkspace`'s empty state until `useRules()` and `useRuleTemplates()` resolve client-side. Since the route is already protected (cookie auth checked in middleware) and the Spring API is co-deployed with same-origin credentials, a server-side `prefetchQuery({ queryKey: rulesKeys.list, queryFn: () => listRules() })` + `<HydrationBoundary state={dehydrate(queryClient)}>` would eliminate the loading flash entirely on the first navigation. Optional Phase 04 polish; not a correctness bug.
**Fix:** Convert page to async and prefetch:
```tsx
export default async function RulesPage() {
  const queryClient = new QueryClient();
  await Promise.all([
    queryClient.prefetchQuery({ queryKey: rulesKeys.list(), queryFn: listRules }),
    queryClient.prefetchQuery({ queryKey: rulesKeys.templates(), queryFn: listRuleTemplates }),
  ]);
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <main className="mx-auto w-full max-w-6xl p-4 md:p-6">
        <RulesWorkspace />
      </main>
    </HydrationBoundary>
  );
}
```
Note: `listRules`/`listRuleTemplates` currently call the typed `api` with `credentials: 'include'`, which on the server would need to forward the request cookies via `cookies()` from `next/headers`. Verify this works under Next 16's RSC fetch model before committing.

### IN-08: `ActionProposalMerger` and frontend reuse `features/llm/` for BYOK-only UI without a clearer feature name

**File:** `apps/web/features/llm/{api,components,hooks,messages.ts}`
**Issue:** The `features/llm/` folder exclusively hosts BYOK key validation, save, and current-status flows (`llm-api.ts` calls `/api/llm/byok/*` only; the components are `ByokForm`-shaped). "LLM" is the platform's internal capability name, not a user-facing surface — users navigate to a "Bring Your Own Key" / "API Keys" / "Settings → BYOK" section. Renaming to `features/byok/` (or nesting under `features/settings/byok/`) makes intent obvious and avoids confusion with future features like rule-compile or triage that also use the LLM gateway. Out of phase 03 scope (folder predates phase 03), tracked here for future cleanup.
**Fix:** Defer. Schedule as a refactor task once a second LLM-adjacent feature surface lands so the rename is coordinated.

---

## Dismissed Concerns (verification-time review)

The following concerns surfaced during a follow-up walkthrough but were investigated and dismissed; recorded here so a future `/gsd-code-review --fix` run does not re-introduce them as findings.

### DM-01: "`features/rules/messages.ts` is a dead source of truth — runtime reads from `i18n/messages/{vi,en}.json`"

**Verdict:** False positive. `apps/web/scripts/merge-feature-i18n.ts` walks every `features/**/messages.ts`, projects the `{vi, en}` shape into the per-locale JSON bundles, and writes `i18n/messages/{vi,en}.json` with a `DO NOT EDIT MANUALLY` marker. The merge runs as the `i18n:build` script chained into `pnpm build`. Both files contain matching `rules.*` (77 keys) and `errors.rules.*` (15 keys) at runtime. The `messages.ts` source-of-truth pattern is real, working, and load-bearing. The remaining gap (no documentation in CONVENTIONS.md) is captured separately as **WR-11**.

### DM-02: "Helper functions live at the bottom of `RulesWorkspace.tsx`"

**Verdict:** Not a defect. `compareRulesByOrder`, `compiledResultFromRule`, `fallbackDisplayName`, `canPreviewRule`, `isDirtySelectedDraft`, `apiErrorCode`, `isInsufficientCredit`, and `isGmailUnavailable` are all file-local with a single consumer (`RulesWorkspace`). Co-locating them in the same file is the standard React idiom — extracting them to a separate `rules-workspace-helpers.ts` would add a file boundary with no consumer benefit. If a second component reaches for any of these, hoist that one into `features/rules/lib/`.

### DM-03: "`RulesWorkspace` should split state into two hooks"

**Verdict:** Subjective. The 11 `useState` slots are cohesive (selection + composer state on one hand, preview state on the other) and there are real dependencies between them (selecting a new rule resets composer + preview together, compile result feeds the preview gate, etc.). A `useReducer` with a typed action shape might compress lines, but two extracted custom hooks would either need to share a parent reducer or pass setters back and forth — net negative. Re-evaluate when a second component needs to drive the same workflow.

### DM-04: "`features/llm/` should be renamed `features/byok/`"

**Verdict:** Real, but out of phase 03 scope (the folder predates this phase and was not in any 03 SUMMARY's `key-files.created` or `modified`). Tracked as a future refactor in **IN-08**.

---

_Reviewed: 2026-05-10T10:35:08Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
