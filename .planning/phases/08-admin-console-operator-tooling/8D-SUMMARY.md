---
phase: 08-admin-console-operator-tooling
plan: 8D
subsystem: admin-curated-catalog
tags: [spring-boot, spring-modulith, liquibase, postgres, spring-data-redis, spring-data-jpa, vite, tanstack-router, tanstack-query, playwright]
requires:
  - phase: 08-8A
    provides: admin auth chain, AdminContext, admin audit/read events, apps/admin shell, admin OpenAPI group, processing_job SKIP LOCKED infrastructure
  - phase: 08-8B
    provides: ProviderMasterKeyResolver, ModelsProbeClient, ChatModelCacheEvictionListener, providerSecretVersion-keyed ChatModel cache, llm_provider_master_key + AES-GCM encryption
provides:
  - 3-table normalized catalog (provider_catalog + model_catalog + feature_binding) with FKs against assistant_settings.{chat,triage,draft}_model_id
  - Single-row feature_default_provider table (PRIMARY KEY on feature) replacing 8B per-row default booleans
  - 3-step Fetch -> Diff -> Confirm Sync state machine over processing_job SKIP LOCKED with 60s Redis SETNX debounce
  - Stuck-job janitor that releases the Redis debounce after a 5 minute lock timeout
  - Per-provider catalog_version BIGINT counter that participates in the SpringAiChatModelFactory CacheKey
  - ModelsProbeClient.fetchModelCatalog typed list alongside the 8B probeConnection enum
  - ModelSchemaValidator regex + per-provider JSON Schema gates on /models payloads and manual entries
  - Anthropic Liquibase seed (3 Claude models) with manual-entry UX; Sync rejected at backend and disabled in UI
  - Disable-with-pinned-tenants soft delete (sets deprecated_at) requiring confirmedPinned + reason
  - Public GET /api/settings/catalog (CuratedCatalogQueryService) with Redis ETag cache and per-provider catalog_version slicing
  - Admin /catalog route with per-provider browser, 3-step Sync wizard, manual-entry form, disable-with-pins ConfirmTwice flow, and Playwright coverage
affects: [08E-queue-health, 08F-spend-dashboard, 09-user-settings-ui, chat-platform-llm-routing, triage-platform-llm-routing, draft-platform-llm-routing]
tech-stack:
  added: [CatalogSyncOrchestrator, CatalogSyncJobConsumer, CatalogSyncJobJanitor, ModelSchemaValidator, CuratedCatalogQueryService, FeatureDefaultProviderService, FeatureAttributeConverter, AdminCatalogController, SettingsCatalogController]
  patterns:
    - Sync step tracking via processing_job.payload_json->>'step' (FETCH / FETCHING / DIFF_READY / CONFIRMING / CONFIRMED / CANCELLED / ABANDONED) without extending the existing status CHECK constraint.
    - provider_catalog.catalog_version BIGINT bumped in the same @Transactional as model_catalog / feature_binding mutations; CacheKey carries providerCatalogVersion for request-bound cache invalidation.
    - feature_default_provider PRIMARY KEY on feature plus INSERT ... ON CONFLICT(feature) DO UPDATE replaces 8B's per-provider boolean flags and avoids partial UNIQUE indexes with subqueries.
    - ModelsProbeClient splits probeConnection (enum) and fetchModelCatalog (typed list) on the same RestClient with shared log scrubbing.
    - CatalogChangedEvent carries newCatalogVersion; ChatModelCacheEvictionListener evicts both by affectedModelIds and by stale providerCatalogVersion slots.
    - CuratedCatalogResponse + ETag derived from per-provider catalog_version map plus SHA-256 of payload bytes; 304 on If-None-Match.
key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/068-catalog-tables-prep.yaml
    - backend/core/src/main/resources/db/changelog/changes/068b-catalog-tables-fk.yaml
    - backend/core/src/main/resources/db/changelog/changes/069-feature-default-provider-migration.yaml
    - backend/core/src/main/resources/db/changelog/changes/070-anthropic-catalog-seed.yaml
    - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/Feature.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/event/CatalogChangedEvent.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureAttributeConverter.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingEntity.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingRepository.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureDefaultProviderEntity.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureDefaultProviderRepository.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogRepository.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogEntity.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogRepository.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogDiff.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogModelRow.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogSyncJob.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/PerFeatureCatalog.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogAdminService.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobConsumer.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobJanitor.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobRepository.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/FeatureDefaultProviderService.java
    - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java
    - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminCatalogController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsCatalogController.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/*.java (8 DTOs + package-info)
    - backend/api/src/main/java/com/zeromail/api/dto/settings/CuratedCatalogResponse.java
    - apps/admin/src/routes/_authenticated/catalog.tsx
    - apps/admin/src/routes/_authenticated/catalog-sync.$jobId.tsx
    - apps/admin/src/features/catalog/*.ts (catalog-api + query-keys + 8 hooks)
    - apps/admin/e2e/catalog.spec.ts
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/admin/audit/domain/AdminAuditAction.java
    - backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantSettingsEntity.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClient.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java
    - backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
    - apps/admin/src/components/AdminLayout.tsx
    - apps/admin/src/lib/api/admin-schema.d.ts
    - apps/admin/openapi/admin-spec.json
key-decisions:
  - "feature_binding final shape is (id, model_id, feature, enabled) with UNIQUE(model_id, feature) only; no is_default column and no provider column. The 'default model for feature X' lives exclusively in feature_default_provider, reachable to provider via the FK chain feature_default_provider -> model_catalog.provider."
  - "feature_default_provider uses PRIMARY KEY on feature instead of a partial UNIQUE index with a subquery into model_catalog (Postgres rejects subqueries in index expressions). At most 3 rows ever exist (CHAT/TRIAGE/DRAFT)."
  - "Catalog sync step tracking lives in processing_job.payload_json->>'step'; the existing status CHECK constraint is untouched. Worker drains CATALOG_SYNC jobs only in steps FETCH and DIFF_READY; DIFF_READY rows wait for explicit operator Confirm."
  - "CacheKey for ChatModel extends the 8B (tenantId, feature, provider, modelId, providerSecretVersion) shape with providerCatalogVersion. A Sync Confirm bumps provider_catalog.catalog_version in the same @Transactional, so any subsequent request misses cache regardless of when the async ChatModelCacheEvictionListener fires."
  - "Anthropic provider has no /models endpoint; Sync is rejected at AdminCatalogController + CatalogSyncOrchestrator with error.admin.catalog_sync_anthropic_disabled, and the UI Sync button is disabled with a manual-entry tooltip. Initial 3 Claude models ship via Liquibase 070 inserts."
  - "Any ACTIVE admin can Confirm a DIFF_READY job (not only the initiator). The audit row records both payload_json.actorId (initiator) and AdminContext.currentOrThrow().id() (confirmer), preventing UX dead-ends when a session expires."
  - "ModelsProbeClient was split into probeConnection (enum from 8B) and fetchModelCatalog (typed RawModel list for 8D) on the same RestClient instance + interceptor for log scrubbing, instead of overloading the 8B enum-only method."
  - "Pre-FK backfill changeset 068 NULLs out orphan assistant_settings.*_model_id rows before 068b adds the FKs; the count is logged via Liquibase precondition reporting so operators can decide whether to seed missing model_catalog rows manually or accept the NULL backfill."
  - "CuratedCatalogQueryService Redis ETag is derived from the per-provider catalog_version map + SHA-256 of payload bytes, so a single provider's version bump invalidates only its slice; cross-provider reads stay warm."
patterns-established:
  - "Sync state machines that share the existing processing_job table track sub-steps in payload_json->>'step' and add a janitor (@Scheduled fixedDelay=60s) to release SKIP LOCKED + Redis debounce after a 5 minute lock timeout."
  - "Per-aggregate monotonic version counters (catalog_version BIGINT) participate in cache keys so request-bound reads naturally MISS after a write commits, regardless of async eviction listeners."
  - "Liquibase pre-FK backfill: a separate <sql> changeset NULLs out about-to-be-orphaned references before the addForeignKeyConstraint changeset; the count is surfaced in the deploy runbook."
  - "Per-feature defaults that need to be globally unique live in a tiny single-row-per-feature table with feature as PRIMARY KEY, not in a partial index on the parent binding table."
  - "Settings-side admin-curated read APIs live under api.controllers.settings.* with @PreAuthorize(\"isAuthenticated()\") and join the public GroupedOpenApi group; admin-only fields stay in admin DTOs and are not reused for the public response."
requirements-completed:
  - CAT-01
  - CAT-02
  - CAT-03
  - CAT-04
  - CAT-05
  - CAT-06
  - CAT-07
duration: "single-commit production execution completed 2026-05-20T13:49:37+07:00"
completed: 2026-05-20
---

# Phase 08 Plan 8D: Curated Catalog Summary

**3-table normalized catalog, 3-step Sync-from-/models wizard, model-ID validation, Anthropic Liquibase seed, disable-with-pins soft delete, and the public GET /api/settings/catalog endpoint that Phase 9 settings UI will consume.**

## Performance

- **Duration:** Single-commit production execution completed at 2026-05-20T13:49:37+07:00.
- **Tasks:** 3/3 plan tasks covered (Liquibase + entities + repos, Sync orchestrator + validator + curated read-side, controllers + apps/admin UI + Playwright).
- **Files changed:** 71 files (5300 insertions, 121 deletions).

## Accomplishments

- Added Liquibase 068 (catalog-tables-prep with pre-FK backfill), 068b (catalog-tables-fk + UNIQUE(model_id, feature)), 069 (feature_default_provider table + 8B boolean-column migration + drops), and 070 (Anthropic 3-Claude seed via `<insert>` so rollback removes the rows).
- Added `core.admin.cat` Modulith package: `Feature` IdentifiedEnum + AttributeConverter, three persistence entities + repositories, four projection records, `CatalogChangedEvent` (with `newCatalogVersion`), and a public package-info marker.
- Added `CatalogSyncOrchestrator` (Fetch -> Diff -> Confirm), `CatalogSyncJobConsumer` (SKIP LOCKED worker poller filtered to FETCH + DIFF_READY steps), `CatalogSyncJobJanitor` (5 minute lock timeout + Redis debounce release), `CatalogSyncJobRepository`, `ModelSchemaValidator`, `FeatureDefaultProviderService` (ON CONFLICT(feature) DO UPDATE + audit row), and `CatalogAdminService` (manual create + disable-with-pins soft delete).
- Added `CuratedCatalogQueryService` reading non-deprecated catalog entries + Redis ETag keyed `catalog:etag:v1` (TTL 30s) derived from per-provider `catalog_version` + payload SHA-256; ETag returned by `SettingsCatalogController`, 304 on `If-None-Match`.
- Extended `ModelsProbeClient` with `fetchModelCatalog(provider, key)` returning typed `RawModel` list alongside the 8B `probeConnection(provider, key)` enum, sharing the RestClient + scrub interceptor.
- Extended `ProviderMasterKeyResolver` so `ResolvedKey` carries both `providerSecretVersion` (from 8B) and `providerCatalogVersion` (read in the same call); extended `SpringAiChatModelFactory.CacheKey` to include `providerCatalogVersion`.
- Extended `ChatModelCacheEvictionListener` with a `CatalogChangedEvent` `@ApplicationModuleListener` that evicts ChatModels both by `affectedModelIds` and by stale `providerCatalogVersion` slots; the versioned cache key makes ordering moot, so the listener is a memory-reclaim optimization rather than the correctness mechanism.
- Added `AdminCatalogController` (`/api/admin/catalog/**`) with GET list, Sync `fetch`/`diff`/`confirm`/`cancel`, manual `models` POST, disable POST, and `PUT /{provider}/{feature}/default`; added new `CATALOG_*` actions to `AdminAuditAction`.
- Added `SettingsCatalogController` (`GET /api/settings/catalog`) under `@PreAuthorize("isAuthenticated()")` joining the public GroupedOpenApi group; admin-only fields (sync_history, dependents_count) are not present in `CuratedCatalogResponse`.
- Added DTOs in `api.dto.admin.cat.*` and `api.dto.settings.*` with `@NamedInterface` exposure; extended `AdminErrorAdvice` for the new catalog error codes.
- Added apps/admin `/catalog` route (provider tabs + 3-feature sub-tabs + Sync button disabled with tooltip for Anthropic + manual entry form for Anthropic + Disable per row), `/catalog-sync/$jobId` 3-step wizard, 9 TanStack Query hooks (use-catalog, use-sync-fetch, use-sync-diff, use-sync-confirm, use-sync-cancel, use-create-model, use-disable-model, use-set-default-model), regenerated `admin-schema.d.ts`, and Playwright `catalog.spec.ts` covering the mocked Sync golden path.
- Migrated 8B's `llm_provider_master_key.feature_default_provider_{chat,triage,draft}` BOOLEAN columns into the new `feature_default_provider` table (3 rows from prior TRUE flags) and dropped the columns in the same Liquibase 069 changeset.

## Task Commits

1. **Task 8D production (single commit):** `04b76df4` — `feat(08-8D): add curated catalog` (71 files, +5300 / -121).

## Verification

- Context7 docs checked: `/spring-projects/spring-ai` provider `/models` adapter shapes (OpenAI / Google GenAI / DeepSeek / OpenRouter), `/networknt/json-schema-validator` for `ModelSchemaValidator`, and `/spring-projects/spring-data-redis` for SETNX-based debounce.
- `./gradlew :backend:core:test --tests "*Catalog*"` passed (catalog persistence, orchestrator, sync consumer, schema validator, curated query service, feature default provider service).
- `./gradlew :backend:core:test :backend:api:test --tests "*ChatModelCacheEvictionListener*" --tests "*ModelsProbeClient*" --tests "*ProviderMasterKeyResolver*"` passed after extending the CacheKey + probe + resolver shapes to carry `providerCatalogVersion`.
- `./gradlew :backend:api:test --tests "*AdminCatalogController*" --tests "*SettingsCatalogController*"` passed.
- `./gradlew :backend:api:test --tests "*ZeroMailApiApplicationModulesTest*"` passed; `api.dto.admin.cat` + `api.dto.settings` `@NamedInterface`s exposed.
- `pnpm --filter @zeromail/admin build` passed.
- `pnpm --filter @zeromail/admin test:unit` passed.
- `pnpm --filter @zeromail/admin e2e -- --grep "catalog"` passed (mocked `/api/admin/catalog/OPENAI/sync/fetch` + `/sync/{jobId}/diff` + Confirm).
- JetBrains file problem checks returned no errors on the new catalog Java files and admin route files.
- `mcp__postgres__execute_sql "SELECT count(*) FROM model_catalog WHERE provider='ANTHROPIC'"` returned 3.
- `mcp__postgres__execute_sql "SELECT column_name FROM information_schema.columns WHERE table_name='feature_binding'"` returned exactly the locked shape (id, model_id, feature, enabled, created_at).
- `mcp__postgres__execute_sql "SELECT count(*) FROM feature_default_provider"` returned 3 after the 069 migration (CHAT / TRIAGE / DRAFT).
- `grep -RnE 'kekVersion|kek_version' backend/core/src/main/java/com/zeromail/core/admin/cat/` returned 0 hits (catalog package does not touch cipher KEK metadata).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Correctness] Liquibase changeset renumbering propagated to 068/068b/069/070**
- **Found during:** Task 8D-01 pre-flight against 8A R-H10 numbering policy.
- **Issue:** The plan originally referenced `052-catalog-tables.yaml` and `053-anthropic-catalog-seed.yaml`. 8A R-H10 had renumbered earlier slots, so 052/053 collided.
- **Fix:** Renamed changesets to `068-catalog-tables-prep.yaml`, `068b-catalog-tables-fk.yaml`, `069-feature-default-provider-migration.yaml`, `070-anthropic-catalog-seed.yaml`. Pre-FK backfill split out from the FK addition into 068 vs 068b so the orphan-row count is observable before the FK ships.
- **Files modified:** new Liquibase YAMLs + `db.changelog-master.yaml` includes.
- **Verification:** Liquibase contract tests + `mcp__postgres__execute_sql` counts above.
- **Committed in:** `04b76df4`

**2. [Rule 1 - Correctness] feature_binding shape collapsed to (id, model_id, feature, enabled)**
- **Found during:** Reviews-pass addendum R-8D-H11 (cycle-3 NEW-HIGH-2): the cycle-2 partial UNIQUE index proposal `CREATE UNIQUE INDEX ... ON feature_binding(feature, (SELECT provider FROM model_catalog ...)) WHERE is_default = TRUE` is not implementable because Postgres rejects subqueries in index expressions.
- **Fix:** Removed `provider` and `is_default` from `feature_binding`. The "default model for feature X" lives in `feature_default_provider` with `feature` as PRIMARY KEY; provider is reachable via the FK chain to `model_catalog.provider`. `FeatureDefaultProviderService.set(feature, modelId, adminId)` is a single `INSERT ... ON CONFLICT(feature) DO UPDATE` plus one audit row.
- **Files modified:** `068-catalog-tables-prep.yaml`, `069-feature-default-provider-migration.yaml`, `FeatureBindingEntity.java`, `FeatureDefaultProviderEntity.java`, `FeatureDefaultProviderService.java`.
- **Verification:** Postgres MCP column-list query above; duplicate-PK insert returns SQLSTATE 23505; second `set("CHAT", ...)` upserts to a single row.
- **Committed in:** `04b76df4`

**3. [Rule 1 - Correctness] processing_job status untouched, sub-steps via payload_json**
- **Found during:** Reviews-pass addendum R-8D-H4: extending the existing status CHECK constraint to add `AWAITING_CONFIRM` / `CONFIRMED` / `CANCELLED` would require in-flight schema surgery on a hot table.
- **Fix:** Sync steps tracked in `payload_json->>'step'` (FETCH / FETCHING / DIFF_READY / CONFIRMING / CONFIRMED / CANCELLED / ABANDONED). Worker poller filters `job_type='CATALOG_SYNC' AND status='PENDING' AND step IN ('FETCH','DIFF_READY')`; DIFF_READY rows wait for an explicit Confirm rather than auto-applying.
- **Files modified:** `CatalogSyncOrchestrator.java`, `CatalogSyncJobConsumer.java`, `CatalogSyncJobRepository.java`, `CatalogSyncJobJanitor.java`.
- **Verification:** `./gradlew :backend:core:test --tests "*CatalogSyncOrchestrator*"` passed.
- **Committed in:** `04b76df4`

**4. [Rule 1 - Architecture] Request-bound cache invalidation via providerCatalogVersion**
- **Found during:** Reviews-pass addendum R-8D-H12 (cycle-2 HIGH-2 residual): the original `CatalogChangedEvent` async eviction did not have the request-bound version guarantee that 8B added for `providerSecretVersion`.
- **Fix:** Added `provider_catalog.catalog_version BIGINT NOT NULL DEFAULT 1`; bumped in the same `@Transactional` as catalog mutations; carried on `CatalogChangedEvent`; added to `SpringAiChatModelFactory.CacheKey` and `ProviderMasterKeyResolver.ResolvedKey`. The async `ChatModelCacheEvictionListener` becomes a memory-reclaim optimization rather than the correctness mechanism.
- **Files modified:** `068-catalog-tables-prep.yaml`, `ProviderCatalogEntity.java`, `CatalogChangedEvent.java`, `SpringAiChatModelFactory.java`, `ProviderMasterKeyResolver.java`, `ChatModelCacheEvictionListener.java`, `ChatModelCacheEvictionListenerTest.java`.
- **Verification:** `CacheKey` shape test asserts `providerCatalogVersion`; integration test where parallel reads straddle a Sync Confirm (catalog_version 5 → 6) shows the second read rebuilds the ChatModel even if the listener has not yet executed.
- **Committed in:** `04b76df4`

**5. [Rule 1 - Architecture] ModelsProbeClient split into probeConnection + fetchModelCatalog**
- **Found during:** Reviews-pass addendum R-8D-H3: 8B's `probe(provider, key)` returned an enum, which is insufficient for Sync Fetch which needs the full model list.
- **Fix:** Kept `probeConnection(provider, key) -> ProbeResult` unchanged from 8B; added `fetchModelCatalog(provider, key) -> List<RawModel>` throwing `ProbeFailedException(reason)` mapped to the same enum. Both methods share the RestClient + log-scrub interceptor.
- **Files modified:** `ModelsProbeClient.java`, `ModelsProbeClientTest.java`.
- **Verification:** Existing 8B master-key test-connection tests still pass; new 8D fetch tests assert the typed list and the failure mapping.
- **Committed in:** `04b76df4`

**6. [Rule 1 - Correctness] CatalogSyncOrchestrator.confirm accepts any ACTIVE admin**
- **Found during:** Reviews-pass addendum R-8D-H6: requiring `actor matches initiating admin` creates a UX dead-end if the initiator's session expires.
- **Fix:** Any active admin can Confirm a DIFF_READY job. The audit row records both `payload_json.actorId` (initiator) and `AdminContext.currentOrThrow().id()` (confirmer).
- **Files modified:** `CatalogSyncOrchestrator.java`.
- **Verification:** Confirm-by-different-admin test passes with both actor IDs in the audit row.
- **Committed in:** `04b76df4`

**Total deviations:** 6 reviews-pass-driven shape changes from the original plan body. **Impact:** All six are explicitly required by the locked reviews-pass addendum (R-8D-H3 / H4 / H6 / H10 / H11 / H12); no new runtime dependencies were added, and no scope expanded beyond CAT-01..07.

## Issues Encountered

- `ModelSchemaValidator` per-provider JSON Schema files (`/catalog-schemas/{openai,google,deepseek,openrouter}.schema.json`) describe well-known public response shapes only; no provider-internal fields are encoded. The validator is wired both at Fetch and at Confirm steps (defense in depth) per T-08-35.
- `pnpm --filter @zeromail/admin generate-api` still requires a running backend at `localhost:8080`; the regenerated `admin-schema.d.ts` in this commit was produced against a live API booted with Docker Postgres 18.4 (same pattern used by 8C). No further codegen drift expected until 8E/8F add new admin endpoints.
- Liquibase 068 pre-FK NULL backfill logged the orphan-row count via precondition reporting; the count belongs in the v1.2 deploy runbook before FK 068b ships against prod.

## User Setup Required

None for local code execution. The admin dev server is still running at `http://localhost:5174`. Production deploy must:

1. Run `068-catalog-tables-prep.yaml` and review the precondition-reported orphan count before applying `068b-catalog-tables-fk.yaml` (operator decides: seed missing `model_catalog` rows manually to preserve pins, or accept the NULL backfill so tenants re-pick a model in the UI).
2. Apply `069-feature-default-provider-migration.yaml` to migrate 8B's `llm_provider_master_key.feature_default_provider_{chat,triage,draft}` BOOLEAN columns into the new `feature_default_provider` table and drop the columns.
3. Apply `070-anthropic-catalog-seed.yaml` to seed the 3 Claude models.

## Next Phase Readiness

- 8E/8F can reuse the typed `api.GET` / `api.POST` admin client pattern, the `@NamedInterface` DTO exposure rule (`api.dto.admin.cat` + `api.dto.settings`), the `processing_job.payload_json->>'step'` discriminator pattern for any new long-running admin operations, and the per-aggregate `*_version BIGINT` cache-key extension for any new cacheable resolver paths.
- Phase 9 user-settings AI tab can consume `GET /api/settings/catalog` directly; the response is already shaped per feature with `is_default` / `is_recommended` flags and BYOK-eligible providers are reachable via `provider_catalog` rows that 8B + 8D have populated.

---
*Phase: 08-admin-console-operator-tooling*
*Completed: 2026-05-20*
