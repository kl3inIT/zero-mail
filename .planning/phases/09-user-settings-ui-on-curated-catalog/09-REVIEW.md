---
phase: "09"
slug: "user-settings-ui-on-curated-catalog"
status: "issues_found"
depth: "standard"
files_reviewed: 32
findings:
  critical: 2
  warning: 1
  info: 0
  total: 3
reviewed_at: "2026-05-29"
---

# Phase 09 Code Review

## Scope

Standard inline review of the Phase 9 source changes, focused on BYOK routing, chat streaming, semantic intent evaluation, connection-test state, AI settings UI, voice-generation privacy, and the relevant backend/frontend tests. The Codex runtime did not expose the reviewer subagent, so this review was completed inline using the same GSD scope.

Primary files reviewed included:

- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java`
- `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokProviderResolver.java`
- `backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokService.java`
- `backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokKeyEntity.java`
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java`
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentEvaluator.java`
- `backend/api/src/main/java/com/zeromail/api/controllers/byok/UserByokController.java`
- `apps/web/features/ai/components/AiProviderSection.tsx`
- `apps/web/e2e/ai-settings.spec.ts`

## Findings

### CR-09-01 - Critical - Streaming chat ignores the active Phase 9 BYOK row

**Files:**

- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java:90`
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java:49`
- `backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantSettingsEntity.java:24`
- `backend/api/src/main/java/com/zeromail/api/controllers/byok/UserByokController.java:59`

**Problem:**

The Phase 9 contract says the single `user_byok_key.active` row is the on/off switch for every AI feature, including chat. `UserByokController.activate()` only updates `user_byok_key` through `UserByokService.activate(...)`. However, the streaming chat path calls `SpringAiChatModelFactory.forTenant(...)`, and that factory chooses BYOK only when `assistant_settings.provider_id` is not `platform`:

- `SpringAiStreamingChatModelClient.streamChat(...)` resolves a client via `chatModelFactory.forTenant(...)`.
- `SpringAiChatModelFactory.forTenant(...)` loads `AssistantSettingsEntity` and branches on `providerId(assistantSettings)`.
- `AssistantSettingsEntity.providerId` has a getter but no setter or Phase 9 write path; repository search found no code updating it.

An active, tested row in `user_byok_key` is therefore not consulted by the streaming chat surface. Unless some legacy data happened to set `assistant_settings.provider_id`, chat continues to use the platform runtime router.

**Impact:**

Users can activate BYOK in the Phase 9 UI while chat assistant completions still run on the platform key/model. This violates SET-AI-01, misroutes cost/privacy-sensitive traffic, and can make the manual BYOK checkpoint pass only for non-streaming gateway paths while the real chat surface remains on platform credentials.

**Recommendation:**

Make `SpringAiChatModelFactory` resolve `ByokProviderResolver.resolveForChat(tenantUuid, requestedModelId)` first and fall back to `platformCredential(...)` when empty. Do not gate Phase 9 chat BYOK on `assistant_settings.provider_id` unless activation also writes that field consistently. Add a streaming-chat factory test that seeds/returns an active BYOK credential and asserts `ResolvedChatClient.credentialSource() == BYOK`, provider/base URL/model match the BYOK row, and the platform router is not used.

### CR-09-02 - Critical - Semantic intent BYOK path skips credits but still calls platform credentials

**Files:**

- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java:565`
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java:981`
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java:1031`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentEvaluator.java:91`

**Problem:**

`evaluateSemanticIntents(...)` detects an active BYOK credential, but then calls `evaluateSemanticIntentsWithoutCreditLedger(...)` without passing that credential. That method calls `evaluateSemanticIntentRoutes(...)`, which loops through platform `PlatformRoute`s, resolves `routeCredentials(route)`, and records usage with credential source `"PLATFORM"` and `chargedCredits=0`.

The Spring semantic evaluator implementation only has an override for `PlatformLlmRouteCredentials`; there is no BYOK `LlmProviderCredential` path. The current branch therefore means "do not charge credits" rather than "call the user's BYOK provider".

**Impact:**

Semantic rule/triage checks for an active BYOK tenant still hit the platform key and platform model, while the ledger records zero charged credits. This breaks both the routing contract and billing/cost attribution for semantic intent evaluation.

**Recommendation:**

Add a true BYOK semantic evaluation path. Either extend `SemanticIntentEvaluator` to accept `LlmProviderCredential` or reuse `LlmProviderChatExecutor` with the same structured-output contract. Record provider/model/source as `BYOK`, keep charged credits at zero, and wipe credential material in a `finally` path. Add a BYOK routing test for `evaluateSemanticIntents(...)` that verifies platform route credentials are not used when `user_byok_key.active=true`.

### WR-09-01 - Warning - BYOK retest can leave an invalid row looking active

**Files:**

- `backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokService.java:123`
- `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokProviderResolver.java:49`
- `apps/web/features/ai/components/AiProviderSection.tsx:103`
- `apps/web/features/ai/components/AiProviderSection.tsx:123`

**Problem:**

`UserByokService.testConnection(...)` records the new test result and model list but never deactivates the row or clears the selected model when the retest fails. It also leaves the selected model untouched when a successful retest returns a model list that no longer contains that model. `ByokProviderResolver.isActivationEligible(...)` only checks `active`, non-blank `modelId`, and `lastTestResult == OK`; it does not check membership in the latest stored model list.

The frontend tries to paper over failed tests locally by setting `active=false`, but the API response for `GET /api/byok` still returns the persisted `active` flag. After reload, the switch can render checked but disabled for a failed retest. In the stale-model case, the resolver can still use a model that the latest provider catalog no longer returned.

**Impact:**

The UI and resolver can disagree about whether BYOK is effectively active. Users may see an active switch while calls fall back to platform, or the gateway may keep calling an old model after a retest proved the provider's model list changed.

**Recommendation:**

On failed retest, persistently deactivate the row and clear or require reselecting the model. On successful retest, if the currently selected model is not in the returned model list, clear the selected model and set `active=false`. Alternatively, make `ByokProviderResolver` validate the selected model against `last_test_models_json` before returning credentials. Add backend coverage for active row -> failed retest -> reload summary and active row -> OK retest without selected model -> resolver fallback.

## Test Gaps

- `apps/web/e2e/ai-settings.spec.ts` covers Save -> reload for BYOK, but it does not exercise the promised Test connection -> pick Model -> Activate -> reload path. That gap is why CR-09-01 can survive even though the Phase 9 e2e passes.
- Full Docker/Testcontainers suites were not rerun during this review pass; previous phase closeout noted local Docker availability constraints.

## Non-Findings Checked

- The generate-from-sent path reads Gmail sent bodies in memory, calls the LLM through `generatePreviewText(...)`, and has a sentinel integration test asserting raw mail prompt/completion content is not persisted or logged.
- `SpringAiProviderChatExecutor` wipes the passed `LlmProviderCredential` in a `finally` block, so the initial concern about the non-streaming BYOK gateway path retaining that credential was not raised as a finding.
