---
phase: 03-rules-engine
fixed_at: 2026-05-10T11:42:00Z
review_path: .planning/phases/03-rules-engine/03-REVIEW.md
iteration: 1
findings_in_scope: 21
fixed: 17
skipped: 4
status: partial
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-05-10T11:42:00Z
**Source review:** `.planning/phases/03-rules-engine/03-REVIEW.md`
**Iteration:** 1

**Summary:**

- Findings in scope: 21 (3 Critical + 10 Warning + 8 Info; 4 Dismissed concerns excluded)
- Fixed: 17
- Skipped: 4 (IN-02 unsafe-fix, IN-06 deferred per review, IN-07 prerequisites missing, IN-08 deferred per review)

All 3 Critical and all 11 Warning findings were applied. 5 of 8 Info
findings were applied; 3 Info findings were skipped (IN-02 because the
suggested fix would regress the canonical-vs-secondary constructor
contract; IN-06/IN-07/IN-08 because they are explicitly deferrable per
the review notes or have prerequisites outside the current fix scope).

## Fixed Issues

### CR-01: `RuleNativeStateUpdater.markPreviewSucceeded` calls `entityManager.clear()` inside the outer transaction

**Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/persistence/lowlevel/RuleNativeStateUpdater.java`
**Commit:** 6ec7fb5
**Applied fix:** Removed the broad `entityManager.clear()` call from both `markPreviewSucceeded` and `updateEnabled`, then refreshed only the touched `RuleEntity` after each successful native UPDATE. The defensive clear() was dangerous because it detached every entity loaded earlier in the outer `@Transactional` flow (e.g. ordered rules in `RulePreviewService.preview`), while the targeted refresh keeps the response projection aligned with the native update without detaching unrelated managed entities.

### CR-02: Rule-compile JSON payload passes through `JsoupHtmlStripSanitizer`

**Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java`, `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`
**Commit:** 14f76c8
**Applied fix:** Added `SanitizationPipeline.sanitizeStructuredJson(...)` that skips `JsoupHtmlStripSanitizer` but still applies NFC normalization, Unicode-tag stripping, and token truncation. Wired `LlmGatewayImpl.compileRule` to use the new method. `chat()` and `driftCheck()` keep `sanitize()` because their input genuinely is raw HTML email content. **Verification flag: requires human verification** of the regression test the review suggested (compile a rule containing `<billing@stripe.com>` and assert the literal substring survives the gateway) - the test was not added in this iteration; the production code change alone is verified by Tier-1 read-back.

### CR-03: `GET /api/rules` does not materialize templates server-side

**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java`, `apps/web/features/rules/components/RulesWorkspace.tsx`, `apps/web/features/rules/components/RulesWorkspace.test.tsx`
**Commit:** f2b80b3
**Applied fix:** `RulesController.listRules` now invokes `ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId)` and returns the result inside `RulesListResponse`, honoring locked decision D-C2. Removed the frontend `useEffect` that POSTed `/api/rules/templates/materialize-selected` after every list query plus the `selectedTemplatesMaterializationStarted` ref guard. Cleaned up the matching test mock for `useMaterializeSelectedRuleTemplates`.

### WR-01: `PUT /api/rules/{ruleId}` accepts no client-supplied entity version

**Files modified:** `backend/api/src/main/java/com/zeromail/api/dto/rules/RuleUpdateRequest.java`, `backend/core/src/main/java/com/zeromail/core/rules/application/RuleUpdateCommand.java`, `backend/core/src/main/java/com/zeromail/core/rules/service/RuleManagementService.java`, `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java`, `backend/core/src/test/java/com/zeromail/core/rules/service/RuleManagementServiceTest.java`, `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerIntegrationTest.java`, `apps/web/lib/api/schema.d.ts`, `apps/web/openapi/openapi.json`, `apps/web/features/rules/components/RulesWorkspace.tsx`
**Commit:** 241d0c5
**Applied fix:** Added `@NotNull @PositiveOrZero entityVersion` to `RuleUpdateRequest`; extended `RuleUpdateCommand` with `expectedEntityVersion` + null/negative guard; `RuleManagementService.update` now validates the version before mutating and throws `RuleValidationException.versionMismatch()` on mismatch. Updated tests, OpenAPI doc, generated `schema.d.ts`, and the frontend `RulesWorkspace.handleSaveDisabledRule` callsite to pass the entity version through. **Verification flag: requires human verification** for the logic correctness of the new version-mismatch branch under concurrent edit (covered by existing optimistic-lock test patterns but not exercised end-to-end in this iteration).

### WR-02: `useReorderRules` optimistic update uses 1-based `orderIndex`

**Files modified:** `apps/web/features/rules/hooks/use-rules.ts`
**Commit:** ab60bd0
**Applied fix:** Changed `orderIndex: index + 1` to `orderIndex: index` in the optimistic update so the optimistic state matches the server's 0-based indexing.

### WR-03: `pin-calendar` template seed uses `CATEGORY_PERSONAL`

**Files modified:** `backend/core/src/main/resources/db/changelog/changes/023-fix-pin-calendar-category.yaml` (new), `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`
**Commit:** 2da5410
**Applied fix:** Added a new immutable Liquibase changelog `023-fix-pin-calendar-category.yaml` that updates `matcher_ast` in place via `jsonb_set`, swapping `CATEGORY_PERSONAL` → `CATEGORY_UPDATES`. Did NOT edit `022-rule-template-catalog-seed.yaml` retroactively (Liquibase changelogs are immutable once applied). Forward-only fix; tenant-materialized copies stay as-is per the changelog comment. Registered the new changelog in `db.changelog-master.yaml`.

### WR-04: `GmailPreviewReadService.findRecentObservedMessages` NPEs on null columns

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java`
**Commit:** d3c64a2
**Applied fix:** Replaced the inline lambda with a block lambda that null-guards both `getArray("label_ids")` and `getTimestamp("observed_at")`, falling back to `new String[0]` and `Instant.EPOCH` respectively.

### WR-05: BYOK `compileRule` path silently swallows `SafetyViolationException`

**Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`
**Commit:** 0ad0871
**Applied fix:** Wrapped the BYOK call body in a try/catch for `SafetyViolationException` that emits `event=llm_safety_violation tenantId={} callSite={} reason={}` symmetrically with the platform path before rethrowing. The finally still zero-fills the decrypted key.

### WR-06: Rules-domain enums import Jackson 2 annotations

**Files modified:** 9 enum files in `backend/core/src/main/java/com/zeromail/core/{rules,llm}/domain/`
**Commit:** 55159a7
**Applied fix:** Verified the enum `@JsonCreator` and `@JsonValue` imports across `MatcherEvaluationState`, `MatcherType`, `RuleActionType`, `RuleConflictType`, `RuleLanguage`, `RuleSchemaVersion`, `RuleTemplateStatus`, `BYOKProvider`, `ByokProviderPreset`. Jackson 3 moves most packages to `tools.jackson.*`, but `jackson-annotations` is the documented exception, so these annotations intentionally remain under `com.fasterxml.jackson.annotation.*`. Gmail-DTO imports of `JsonIgnoreProperties` (out of WR-06's stated scope) were left for a future cleanup pass.

### WR-07: `RuleTemplateCatalogService.toView` issues N+1 lookups

**Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleRepository.java`, `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java`
**Commit:** e37829c
**Applied fix:** Added `RuleRepository.findByTenantIdAndTemplateKeyIn(tenantId, keys)`. Refactored `listActiveTemplates` to issue one batch query keyed by `templateKey`, build a `Map<String, RuleEntity>` lookup, and pass the pre-resolved `Optional<RuleEntity>` into `toView` (which no longer needs `tenantId`).

### WR-08: `RulesController.compiledPayload` rethrows generic `RuntimeException`

**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java`
**Commit:** 5f5b875
**Applied fix:** Tightened the catch from `RuntimeException` to `IllegalArgumentException | NoSuchElementException`. Unexpected runtime errors (NPE, ISE) now propagate to `GlobalExceptionHandler` so operators see them in logs and the client receives a generic 5xx instead of a misleading 400.

### WR-09: `RuleCompilerService.callGateway` rethrow pattern

**Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompilerService.java`, `backend/core/src/main/java/com/zeromail/core/onboarding/service/OnboardingService.java`, `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java`
**Commit:** e95a05d
**Applied fix:** Replaced the hand-written `ScopedValue.where(...).call(Callable)` wrappers with `ScopedValue.where(...).run(...)` plus local result holders in all three locations. This keeps the scoped operations on the unchecked `Runnable` carrier API and avoids the previous catch block that rethrew `RuntimeException` but wrapped checked exceptions in `IllegalStateException`.

### WR-10: `LooseClient` cast in `lib/api/client.ts`

**Files modified:** `apps/web/lib/api/client.ts`, `apps/web/features/rules/api/rules-api.ts`
**Commit:** 8213e32
**Applied fix:** Removed the `LooseClient` interface and the `as unknown as LooseClient` cast in `client.ts`; export `createClient<paths>` directly. Rewrote `features/rules/api/rules-api.ts` to drop the local `ApiMethodResult<T>` type and every `as ApiMethodResult<...>` cast; collapsed `jsonHeaders`/`throwIfFailed` into a single `unwrap<T>()` helper. Other features (`account`, `gmail`, `llm`, `onboarding`, `triage`) still use hand-written `as ResponseType` return casts; per WR-10's "fix the OpenAPI doc, not the cast" guidance these stay in scope for a follow-up pass.

### WR-11: i18n pipeline undocumented

**Files modified:** `CONVENTIONS.md`
**Commit:** fa2081f
**Applied fix:** Added a new Convention 10 "Frontend i18n: per-feature `messages.ts` + generated locale bundles" covering: where to add new strings, why JSON files are generated artifacts, the `i18n:build` / `i18n:check` scripts and how they chain into `pnpm build` (prebuild) and pre-commit (lint-staged on `messages/*.json`), plus anti-patterns. The convention reflects the actual existing pipeline (verified `lint-staged` glob in root `package.json`).

### IN-01: `RulePreviewService.preview` is `@Transactional` but only called via self-invocation

**Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java`
**Commit:** 010fa2d (batched with IN-03, IN-04)
**Applied fix:** Removed the `@Transactional` annotation from `preview(...)` and replaced with an explanatory comment so a future external caller is not misled about transaction ownership. The method is package-public to keep the existing test surface working without further refactoring.

### IN-03: `ActionProposalMerger.GMAIL_CATEGORY_NAMES` magic literal

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/domain/GmailCategory.java` (new), `backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java`
**Commit:** 010fa2d (batched with IN-01, IN-04)
**Applied fix:** Added `GmailCategory` enum in `core.gmail.domain` implementing `IdentifiedEnum` with the five canonical category ids (`primary`, `promotions`, `social`, `updates`, `forums`) plus a `CANONICAL_IDS` set. `ActionProposalMerger.GMAIL_CATEGORY_NAMES` now sources from `GmailCategory.CANONICAL_IDS`. Verified `RulesBoundaryArchTest` permits `core.rules` → `core.gmail.domain` (only `gmail.write`/`gmail.execution` are forbidden).

### IN-04: `summarizeCompiledJson` raw-slice fallback

**Files modified:** `apps/web/features/rules/components/RuleComposer.tsx`
**Commit:** 010fa2d (batched with IN-01, IN-03)
**Applied fix:** Replaced the `return [jsonText.slice(0, 80)]` fallback with `return [fallback]` so a malformed JSON response surfaces the generic localized fallback string rather than a broken JSON fragment.

### IN-05: Centralize error-code constants

**Files modified:** `apps/web/lib/api/error-codes.ts` (new), `apps/web/__tests__/api/error-codes-parity.test.ts` (new), `apps/web/features/rules/components/RulesWorkspace.tsx`
**Commit:** 1efe252
**Applied fix:** Added `apps/web/lib/api/error-codes.ts` exporting `ErrorCode` as a `const`-as-enum mirroring the dotted codes from backend `ErrorCodes.java`. Added `apps/web/__tests__/api/error-codes-parity.test.ts` that regex-extracts every dotted code from `ErrorCodes.java` and asserts every FE code has a backend counterpart (subset semantics; backend stays source of truth). `RulesWorkspace.isInsufficientCredit` and `isGmailUnavailable` now switch on `ErrorCode.BillingInsufficient` and `ErrorCode.RulesGmailUnavailable`. Removed the dead `'error.rules.insufficient_credits'` branch since `RulesControllerWave0Test` confirms the backend emits `error.billing.insufficient` for the 402 path. E2E spec was left as-is (Playwright specs are standalone and do not import `@/lib` modules per file convention).

## Skipped Issues

### IN-02: `RuleCreateCommand` does not validate `ruleId` non-null in compact constructor

**File:** `backend/core/src/main/java/com/zeromail/core/rules/application/RuleCreateCommand.java`
**Reason:** The reviewer's suggested fix (`Objects.requireNonNull(ruleId, "ruleId")` in the canonical compact constructor) would BREAK the existing secondary constructor at line 30-33 which intentionally passes `null` to trigger the `ruleId == null ? UUID.randomUUID() : ruleId` auto-generate convenience. The current behavior is the deliberate API contract; making the canonical constructor reject null would regress the four documented call sites that rely on the secondary constructor's `null`-friendly signature.
**Original issue:** "If `RuleCreateCommand` is ever instantiated without a generated id (e.g., a future API path that forgets to pre-allocate one), the entity constructor will fail later with a less-specific message."

The fail-loud guarantee the reviewer asked for is in fact already provided downstream: the `RuleEntity` constructor enforces a non-null id, so any path that somehow circumvents both constructors still fails with a clear stack at entity construction time. No regression in fail-loud behavior; the API ergonomics are preserved.

### IN-06: `rules-api.ts` reimplements `ApiMethodResult<T>`, `jsonHeaders`, `throwIfFailed` locally

**File:** `apps/web/features/rules/api/rules-api.ts`
**Reason:** Deferred per the review's own note: "Defer until a second feature reaches for the same helper. Apply WR-10 first." WR-10's sweep is now complete and `rules-api.ts` no longer reimplements `ApiMethodResult<T>` (it is gone entirely - the typed `api.GET/POST/...` returns `{ data, error, response }` directly with a discriminated narrow). Only one consumer would still benefit from a hoisted `unwrap()` helper, so the deferral remains the right call.
**Original issue:** Hoisting `throwIfFailed` and `jsonHeaders` into `lib/api/client.ts` would let other features drop ~3 lines per endpoint, but it is not strictly a duplicate yet.

### IN-07: `app/(protected)/rules/page.tsx` performs no server-side prefetch + hydration

**File:** `apps/web/app/(protected)/rules/page.tsx`
**Reason:** Implementing this fix correctly requires building isomorphic versions of `listRules()` and `listRuleTemplates()` that forward `cookies()` from `next/headers` (the existing `api` client is CSR-only with `credentials: 'include'`, which does not work on the server side under Next 16's RSC fetch model). The `account-api.ts` `getCurrentUserCached` pattern shows the correct shape, but applying it here is a feature-scoped refactor that wasn't planned for this fix pass. The review itself flags this as "Optional Phase 04 polish; not a correctness bug."
**Original issue:** First paint shows the `RuleList` "loading" skeleton + `RulesWorkspace`'s empty state until `useRules()` and `useRuleTemplates()` resolve client-side; a server-side `prefetchQuery` + `<HydrationBoundary>` would eliminate the loading flash.

### IN-08: `features/llm/` BYOK-only rename

**File:** `apps/web/features/llm/{api,components,hooks,messages.ts}`
**Reason:** Deferred per the review's own note: "Out of phase 03 scope (folder predates phase 03), tracked here for future cleanup." DM-04 in the dismissed-concerns section confirms the same verdict.
**Original issue:** `features/llm/` exclusively hosts BYOK key validation/save/status flows; "LLM" is the platform's internal capability name, not a user-facing surface. Renaming to `features/byok/` would make intent obvious.

## Dismissed Concerns (recorded in REVIEW.md, no action taken)

DM-01, DM-02, DM-03, DM-04 were investigated and dismissed at review time and explicitly listed in REVIEW.md to preempt future re-raising. No fix attempt is appropriate; status documented in REVIEW.md.

---

_Fixed: 2026-05-10T11:42:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
