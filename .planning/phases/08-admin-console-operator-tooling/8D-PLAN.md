---
phase: 08-admin-console-operator-tooling
plan: 8D
type: execute
wave: 3
depends_on:
  - 08-8A
  - 08-8B
files_modified:
  - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/Feature.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/domain/event/CatalogChangedEvent.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/CatalogSyncJobRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogModelRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/PerFeatureCatalog.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogDiff.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogSyncJob.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobConsumer.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogAdminService.java
  - backend/core/src/main/java/com/zeromail/core/admin/cat/package-info.java
  - backend/core/src/main/resources/db/changelog/changes/052-catalog-tables.yaml
  - backend/core/src/main/resources/db/changelog/changes/053-anthropic-catalog-seed.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminCatalogController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsCatalogController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogListResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncFetchResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncDiffResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncConfirmRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelCreateRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelDisableRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/settings/CuratedCatalogResponse.java
  - apps/admin/src/routes/catalog.tsx
  - apps/admin/src/routes/catalog-provider.tsx
  - apps/admin/src/routes/catalog-sync.tsx
  - apps/admin/src/features/catalog/catalog-api.ts
  - apps/admin/src/features/catalog/query-keys.ts
  - apps/admin/src/features/catalog/use-catalog.ts
  - apps/admin/src/features/catalog/use-sync-fetch.ts
  - apps/admin/src/features/catalog/use-sync-diff.ts
  - apps/admin/src/features/catalog/use-sync-confirm.ts
  - apps/admin/src/features/catalog/use-disable-model.ts
  - apps/admin/src/features/catalog/use-create-model.ts
  - apps/admin/e2e/catalog.spec.ts

autonomous: true
requirements:
  - CAT-01
  - CAT-02
  - CAT-03
  - CAT-04
  - CAT-05
  - CAT-06
  - CAT-07

must_haves:
  truths:
    - "3-table normalized catalog (`provider_catalog`, `model_catalog`, `feature_binding`) deployed via Liquibase 052; FK + UNIQUE partial indexes prevent stale-pin failures from `assistant_settings.{chat|triage|draft}_model_id`."
    - "Liquibase 053 seeds Anthropic Claude family (Claude 4.7 Opus, Claude 4.6 Sonnet, Claude 4.5 Haiku); Sync button disabled for Anthropic with manual-entry tooltip."
    - "Operator can run 3-step Sync-from-/models per provider (Fetch via processing_job SKIP LOCKED with 60s Redis debounce → Diff review → Confirm); auto-apply forbidden."
    - "Model IDs validated against regex `^[a-zA-Z0-9._:/\\-]{1,128}$` AND per-provider JSON Schema."
    - "Disabling a model with pinned tenants requires confirm-twice + reason; soft-delete sets `deprecated_at`; pinned tenants keep working until they pick a new model."
    - "`GET /api/settings/catalog` returns per-feature curated list for users (different DTO than admin); `GroupedOpenApi` places it in `public` group."
    - "Successful Sync Confirm emits `CatalogChangedEvent`; `@ApplicationModuleListener` evicts cached ChatModels for affected (tenantId, feature, provider, model_id) tuples."
    - "MasterKeyRotatedEvent (from 8B) ALSO evicts all ChatModels for that provider — wiring exists from 8B; this plan adds CatalogChangedEvent listener as a sibling."
    - "ProviderMasterKeyResolver (from 8B) provides decrypted key + base_url to `/models` HTTP probe during Sync Fetch — Sync reuses 8B's models-probe client; never sends a message during Sync."
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/052-catalog-tables.yaml"
      provides: "provider_catalog + model_catalog + feature_binding + FKs + UNIQUE partial index `one_default_per_feature_per_provider` + FK from assistant_settings.{chat|triage|draft}_model_id → model_catalog.model_id."
    - path: "backend/core/src/main/resources/db/changelog/changes/053-anthropic-catalog-seed.yaml"
      provides: "Anthropic provider row + 3 Claude model rows + 3 feature_binding rows (one default per feature for Anthropic) via Liquibase `<insert>` (visible to rollback)."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java"
      provides: "Fetch → Diff → Confirm state machine over processing_job SKIP LOCKED with 60s Redis debounce."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java"
      provides: "Public read-side serving `GET /api/settings/catalog` with Redis ETag cache; admin-only fields excluded."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java"
      provides: "Regex + per-provider JSON Schema validation of model IDs and /models response shapes."
  key_links:
    - from: "CatalogSyncOrchestrator#fetch"
      to: "ProviderMasterKeyResolver"
      via: "resolves provider key for /models HTTP probe"
      pattern: "ProviderMasterKeyResolver"
    - from: "CatalogSyncOrchestrator#confirm"
      to: "CatalogChangedEvent"
      via: "ApplicationEventPublisher.publishEvent"
      pattern: "CatalogChangedEvent"
    - from: "core.llm.gateway.springai.admin.ChatModelCacheEvictionListener"
      to: "CatalogChangedEvent"
      via: "@ApplicationModuleListener"
      pattern: "@ApplicationModuleListener.*CatalogChangedEvent"
---

<objective>
Deliver the curated catalog: 3-table normalized schema (provider_catalog, model_catalog, feature_binding), 3-step Sync-from-/models (Fetch → Diff → Confirm) with SKIP LOCKED + Redis debounce, model-ID regex + per-provider JSON Schema validation, Anthropic Liquibase seed + Sync-disabled with manual-entry, disable-with-pinned-tenants confirm-twice, CuratedCatalogQueryService + `GET /api/settings/catalog` (Phase 9 user-side consumer ready), CatalogChangedEvent → ChatModel cache eviction.

Output: Operator curates 6-provider × 3-feature catalog through Sync wizard; users (Phase 9) read curated entries via `/api/settings/catalog`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@CONVENTIONS.md
@TESTING.md
@.planning/phases/08-admin-console-operator-tooling/08-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md
@.planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md
@.planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html
@.planning/phases/08-admin-console-operator-tooling/08-8A-SUMMARY.md
@.planning/phases/08-admin-console-operator-tooling/08-8B-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
@backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java
@backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
</context>

<documentation_lookup>
Context7 mandatory:
- `/spring-projects/spring-ai` for verifying provider `/models` endpoint shapes (OpenAI, Google GenAI, DeepSeek, OpenRouter).
- `/networknt/json-schema-validator` or built-in `com.networknt:json-schema-validator` already on classpath (verify via `./gradlew :backend:core:dependencies | grep json-schema`).
- `/spring-projects/spring-data-redis` for SETNX-based debounce lease pattern.
</documentation_lookup>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8D-01: Liquibase 052 (catalog tables + FKs) + 053 (Anthropic seed) + entities + repositories + Feature enum + projection records</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/052-catalog-tables.yaml,
    backend/core/src/main/resources/db/changelog/changes/053-anthropic-catalog-seed.yaml,
    backend/core/src/main/resources/db/changelog/db.changelog-master.yaml,
    backend/core/src/main/java/com/zeromail/core/admin/cat/domain/Feature.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogModelRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/projection/PerFeatureCatalog.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogDiff.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogSyncJob.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/domain/event/CatalogChangedEvent.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/package-info.java
  </files>
  <read_first>
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml (createTable + addCheckConstraint idiom),
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java (entire — projection record idiom),
    backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java (IdentifiedEnum attribute converter),
    backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java (event record idiom),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C9,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §CAT-01/04/06
  </read_first>
  <behavior>
    - `provider_catalog`: `provider VARCHAR(32) PK CHECK IN ('OPENAI','ANTHROPIC','GOOGLE','DEEPSEEK','OPENROUTER','ROUTER_9R')`, `enabled BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `last_synced_at TIMESTAMPTZ`.
    - `model_catalog`: `model_id VARCHAR(128) PK`, `provider VARCHAR(32) NOT NULL FK provider_catalog(provider)`, `display_name VARCHAR(200) NOT NULL`, `cost_per_1k_input NUMERIC(10,6)`, `cost_per_1k_output NUMERIC(10,6)`, `deprecated_at TIMESTAMPTZ`, `is_recommended BOOLEAN NOT NULL DEFAULT FALSE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`. Model_id format CHECK matches regex `^[a-zA-Z0-9._:/\-]{1,128}$`.
    - `feature_binding`: `id UUID PK`, `model_id VARCHAR(128) NOT NULL FK model_catalog(model_id)`, `provider VARCHAR(32) NOT NULL`, `feature VARCHAR(16) NOT NULL CHECK IN ('CHAT','TRIAGE','DRAFT')`, `enabled BOOLEAN NOT NULL DEFAULT TRUE`, `is_default BOOLEAN NOT NULL DEFAULT FALSE`, UNIQUE(model_id, feature). Partial UNIQUE index `one_default_per_feature_per_provider ON feature_binding(provider, feature) WHERE is_default = TRUE`.
    - FKs added to existing `assistant_settings`: `chat_model_id`, `triage_model_id`, `draft_model_id` columns gain `FOREIGN KEY ... REFERENCES model_catalog(model_id) ON DELETE RESTRICT`. (Existing data backfill: ensure pre-existing model IDs are present in model_catalog seed; if not, set columns to NULL via pre-FK update statement.)
    - `Feature` IdentifiedEnum: CHAT(1), TRIAGE(2), DRAFT(3); static `fromId`.
    - Entities: standard JPA `class` per CONVENTIONS §3; IdentifiedEnum AttributeConverters for `provider` (reuse LlmProvider converter from 8B if accessible across packages, otherwise create local converter `FeatureAttributeConverter`).
    - 053-anthropic-catalog-seed.yaml: `<insert tableName="provider_catalog">` for ANTHROPIC row + 3 `<insert tableName="model_catalog">` rows (`anthropic/claude-4.7-opus`, `anthropic/claude-4.6-sonnet`, `anthropic/claude-4.5-haiku` with display_name "Claude 4.7 Opus" etc., cost values from RESEARCH.md FEATURES or null for now) + 3 `<insert tableName="feature_binding">` rows pairing each Claude model with one feature (Opus→CHAT default, Sonnet→TRIAGE default, Haiku→DRAFT default — per RESEARCH guidance, executor confirms exact pairings against Anthropic recommended use). Use Liquibase `<insert>` so seed rows are visible to `liquibase rollback` (per PATTERNS §C9 deviation).
    - Projection records:
      - `CatalogModelRow(String provider, String modelId, String displayName, boolean isDefault, boolean isRecommended, BigDecimal costPer1kInput, BigDecimal costPer1kOutput, Instant deprecatedAt)` — per SPEC CAT-06 line 248.
      - `PerFeatureCatalog(Feature feature, List<CatalogModelRow> models, String defaultModelId)`.
      - `CatalogDiff(List<CatalogModelRow> added, List<CatalogModelRow> removed, List<CatalogModelRow> changed)`.
      - `CatalogSyncJob(UUID jobId, LlmProvider provider, String status, Instant createdAt, Instant lastUpdatedAt, JsonNode payloadJson)` — payload contains fetched models list + diff but NEVER raw provider error bodies.
    - `CatalogChangedEvent` record: `record CatalogChangedEvent(LlmProvider provider, List<String> affectedModelIds, Set<Feature> affectedFeatures, Instant occurredAt) {}`.
  </behavior>
  <action>
    Create Liquibase 052 with createTable for 3 tables + addForeignKeyConstraint for FKs from assistant_settings columns + raw `<sql>` for partial UNIQUE index (Liquibase doesn't natively support partial indexes — use `<sql>` block per PATTERNS §C9 excerpt). Append CHECK constraint for model_id regex via `<sql>`. 053-anthropic-catalog-seed.yaml uses `<insert>` rows (NOT `<sql>`) so Liquibase rollback removes them properly per PATTERNS §C9 deviation. Backfill consideration: if `assistant_settings` already has model IDs not in model_catalog, the FK addition will fail; the pre-FK migration step is to NULL out unknown model IDs via `<update>` against assistant_settings filtered by `chat_model_id NOT IN (SELECT model_id FROM model_catalog)` — executor confirms via `mcp__postgres__execute_sql` against dev DB whether any pre-existing rows need backfill. Per CAT-04 acceptance: post-Liquibase, Anthropic catalog has exactly 3 models (executor verifies via SELECT COUNT). Append 052/053 includes to db.changelog-master.yaml.
  </action>
  <verify>
    <automated>./gradlew :backend:core:liquibaseUpdate -Pdb=local && ./gradlew :backend:core:test --tests "com.zeromail.core.admin.cat.persistence.*"</automated>
  </verify>
  <done>
    3 tables deploy + FKs + partial UNIQUE + Anthropic 3-model seed + entities/repos compile + Feature enum + projection records validate.
  </done>
  <acceptance_criteria>
    - `mcp__postgres__execute_sql "SELECT count(*) FROM model_catalog WHERE provider='ANTHROPIC'"` returns 3.
    - `mcp__postgres__execute_sql "SELECT model_id FROM model_catalog WHERE provider='ANTHROPIC' ORDER BY model_id"` returns 3 expected Claude model IDs.
    - `INSERT INTO model_catalog (model_id, provider, display_name) VALUES ('bad model id!', 'OPENAI', 'X')` fails CHECK constraint.
    - `INSERT INTO feature_binding (...) VALUES (..., true)` twice for same (provider, feature) fails partial UNIQUE index (where is_default=true).
    - `UPDATE assistant_settings SET chat_model_id = 'nonexistent/model'` fails FK constraint.
    - `DELETE FROM model_catalog WHERE model_id = ...` with active assistant_settings reference fails ON DELETE RESTRICT.
    - Repository `findEnabledModelsByProvider(LlmProvider.ANTHROPIC)` returns 3 rows.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8D-02: CatalogSyncOrchestrator (Fetch → Diff → Confirm state machine) + CatalogSyncJobConsumer (SKIP LOCKED) + ModelSchemaValidator + Redis 60s debounce lease + CatalogChangedEvent listener wiring</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobConsumer.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogAdminService.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/CatalogSyncJobRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java (lines 14-41 — SKIP LOCKED claim pattern),
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java + ModelsProbeClient.java (from 8B — Sync reuses /models probe),
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java (from 8B — extend with CatalogChangedEvent handler),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C10,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §CAT-02/03/05/07,
    .planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md §Pitfall 9 (processing_job discriminator) + §Pitfall 10 (cache race)
  </read_first>
  <behavior>
    - `CatalogSyncOrchestrator.startFetch(LlmProvider provider, AdminUser actor) -> UUID jobId`:
      1. Check Redis debounce: `SETNX zeromail:catalog:sync:debounce:{provider} {jobId} EX 60` → if FALSE return existing in-progress jobId (with status SYNC_IN_PROGRESS).
      2. Reject if provider == ANTHROPIC (Sync disabled per CAT-04; throw `error.admin.catalog_sync_anthropic_disabled`).
      3. Insert row into `processing_job` table with `job_type='CATALOG_SYNC'`, `payload_json={provider, actorId, jobId, step:'FETCH'}`, `status='PENDING'`. CatalogSyncJobRepository extends processing_job claim queries for CATALOG_SYNC discriminator.
      4. Return jobId; worker side picks up.
    - `CatalogSyncJobConsumer` (in backend/worker module — `@Scheduled(fixedDelay=2000) @Profile("!test")`):
      1. claim batch of CATALOG_SYNC processing_job rows via SKIP LOCKED (60s lock).
      2. For each: call `ProviderMasterKeyResolver.resolve(provider)` for key + base_url + adapter; call `ModelsProbeClient.fetchModels(provider, key)` returning typed list of `{model_id, display_name?}` (NOT raw JSON; client maps response shape).
      3. ModelSchemaValidator validates each model_id against regex `^[a-zA-Z0-9._:/\-]{1,128}$` + per-provider JSON Schema (load schemas from classpath `/catalog-schemas/{provider}.schema.json`). Reject batch on schema mismatch with reason enum `SCHEMA_MISMATCH` stored in payload.
      4. Compute diff against existing model_catalog rows for this provider: added/removed/changed.
      5. Update processing_job row payload `{step:'DIFF_READY', added: [...], removed: [...], changed: [...]}` + status='AWAITING_CONFIRM' (custom status; processing_job status enum extended).
      6. Release Redis lease (`DEL` debounce key — allow next Sync after 60s OR after this job confirms).
    - `CatalogSyncOrchestrator.diff(UUID jobId) -> CatalogDiff`: reads processing_job payload; returns diff.
    - `CatalogSyncOrchestrator.confirm(UUID jobId, AdminUser actor) -> CatalogChangedEvent`:
      1. validate job exists + step=DIFF_READY + actor matches initiating admin.
      2. apply diff inside @Transactional: insert added model_catalog rows + soft-delete removed (`deprecated_at=NOW()`) + update changed display_name/cost.
      3. ALL operations validated by ModelSchemaValidator one more time (defense in depth).
      4. set processing_job status='CONFIRMED'.
      5. write CATALOG_SYNC_CONFIRMED audit row.
      6. publish CatalogChangedEvent(provider, affectedModelIds, affectedFeatures, occurredAt).
    - `CatalogSyncOrchestrator.cancel(UUID jobId)`: sets status='CANCELLED'; releases Redis lease.
    - `CatalogAdminService.createManualModel(LlmProvider provider, String modelId, String displayName, BigDecimal costIn, BigDecimal costOut, boolean isRecommended)`:
      1. validates model_id against regex.
      2. inserts model_catalog row.
      3. writes audit row MODEL_CREATED.
      4. publishes CatalogChangedEvent (single-model).
    - `CatalogAdminService.disableModel(String modelId, String reason)`:
      1. computes pinned-tenant count: COUNT distinct tenantId WHERE assistant_settings.{chat|triage|draft}_model_id = modelId.
      2. if >0: requires confirm-twice flag in request (server validates client passed `confirmedPinned: true` boolean); else 400 `error.admin.catalog_disable_pins_unconfirmed`.
      3. UPDATE model_catalog SET deprecated_at=NOW() WHERE model_id=...; pinned tenants keep working until they pick a new model (no auto-migration per CAT-05).
      4. writes MODEL_DISABLED audit row with reason + pinned_count.
      5. publishes CatalogChangedEvent.
    - `CuratedCatalogQueryService.getCatalog() -> Map<Feature, PerFeatureCatalog>`:
      1. read provider_catalog + model_catalog + feature_binding (READ COMMITTED) for non-deprecated entries.
      2. shape DTO with only user-facing fields (`{provider, model_id, display_name, is_default, is_recommended, cost_per_1k_input, cost_per_1k_output, deprecated_at}`).
      3. cache in Redis with ETag keyed by max(last_synced_at, model_catalog.created_at); invalidate on CatalogChangedEvent.
    - `ChatModelCacheEvictionListener` (extending 8B): add second `@ApplicationModuleListener void on(CatalogChangedEvent event)` → SpringAiChatModelFactory.evictByModelIds(event.affectedModelIds()) + CuratedCatalogQueryService.invalidateCache().
  </behavior>
  <action>
    Implement Fetch/Diff/Confirm state machine per PATTERNS §C10. processing_job `status` enum may need extension to include `AWAITING_CONFIRM`, `CONFIRMED`, `CANCELLED` — if existing status enum is rigid, add via Liquibase 052b changeset OR use the existing `status='COMPLETED'` + payload_json step discriminator (per Pitfall 9 — verify via mcp__postgres__execute_sql against current processing_job schema before deciding; the lower-risk option is payload-based step tracking). Redis debounce uses StringRedisTemplate.opsForValue().setIfAbsent(key, jobId, Duration.ofSeconds(60)). Per CAT-03 acceptance: ModelSchemaValidator loads `/catalog-schemas/openai.schema.json`, `/catalog-schemas/google.schema.json`, `/catalog-schemas/deepseek.schema.json`, `/catalog-schemas/openrouter.schema.json` (Anthropic Sync disabled so no schema needed; 9Router uses OpenAI schema if format=OPENAI_FORMAT else Anthropic schema). Schemas verify the `/models` response shape from each provider — check Context7 `/spring-projects/spring-ai` for current OpenAI `/v1/models` response shape (Spring AI M6 may have a Java client that already returns typed models; if so, schema validation is redundant — executor decides). com.networknt:json-schema-validator already on classpath via Spring Cloud or similar; if not, add to libs.versions.toml. CuratedCatalogQueryService Redis ETag: store JSON payload + sha-256 ETag; HTTP layer (SettingsCatalogController in 8D-03) returns 304 on If-None-Match match. Per Pitfall 10: CatalogChangedEvent listener evicts ChatModels keyed by affectedModelIds (not just provider) — finer-grained eviction than MasterKeyRotatedEvent. Audit rows for catalog actions follow same `AdminAuditWriter.append` pattern from 8A.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.cat.usecases.*" --tests "com.zeromail.core.admin.cat.persistence.*"</automated>
  </verify>
  <done>
    Fetch enqueues processing_job + Redis lease set; worker drains + computes diff; Confirm applies + emits CatalogChangedEvent + cache evicts; Anthropic Sync rejected; model-ID regex + schema enforced; manual create works; disable with pins requires confirmedPinned; ChatModelCacheEvictionListener fires on event.
  </done>
  <acceptance_criteria>
    - `CatalogSyncOrchestrator.startFetch(OPENAI, admin)` returns jobId; second call within 60s returns same jobId with status SYNC_IN_PROGRESS.
    - `CatalogSyncOrchestrator.startFetch(ANTHROPIC, admin)` throws with `error.admin.catalog_sync_anthropic_disabled`.
    - Mock ModelsProbeClient returning `[{model_id:"gpt-4o", display_name:"GPT-4o"}]` + worker consumer drains job + Orchestrator.diff(jobId) returns CatalogDiff(added=[gpt-4o], removed=[], changed=[]).
    - ModelSchemaValidator rejects `{model_id: "bad model id!"}` with regex violation; rejects `{not_an_array: true}` with schema violation.
    - `CatalogSyncOrchestrator.confirm(jobId, admin)` applies diff + writes 1 CATALOG_SYNC_CONFIRMED audit row + publishes 1 CatalogChangedEvent + ChatModelCacheEvictionListener invoked.
    - `CatalogAdminService.disableModel("anthropic/claude-4.7-opus", "deprecated")` with 5 pinned tenants returns 400 `error.admin.catalog_disable_pins_unconfirmed` when `confirmedPinned=false`; with `true` succeeds + writes MODEL_DISABLED row with pinned_count=5; pinned tenants still get LLM completion via this model (deprecated_at set but model_catalog row still exists for FK).
    - `CuratedCatalogQueryService.getCatalog()` returns PerFeatureCatalog map for CHAT/TRIAGE/DRAFT with default model IDs; second call within ETag window returns cached value.
    - CatalogChangedEvent triggers `evictByModelIds(["anthropic/claude-4.7-opus"])` on SpringAiChatModelFactory.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8D-03: AdminCatalogController + SettingsCatalogController + DTOs + apps/admin /catalog list + per-provider browser + 3-step Sync wizard + manual-entry form + disable-with-pins confirm</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminCatalogController.java,
    backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsCatalogController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogListResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncFetchResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncDiffResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogSyncConfirmRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelCreateRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/cat/CatalogModelDisableRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/settings/CuratedCatalogResponse.java,
    apps/admin/src/routes/catalog.tsx,
    apps/admin/src/routes/catalog-provider.tsx,
    apps/admin/src/routes/catalog-sync.tsx,
    apps/admin/src/features/catalog/catalog-api.ts,
    apps/admin/src/features/catalog/query-keys.ts,
    apps/admin/src/features/catalog/use-catalog.ts,
    apps/admin/src/features/catalog/use-sync-fetch.ts,
    apps/admin/src/features/catalog/use-sync-diff.ts,
    apps/admin/src/features/catalog/use-sync-confirm.ts,
    apps/admin/src/features/catalog/use-disable-model.ts,
    apps/admin/src/features/catalog/use-create-model.ts,
    apps/admin/e2e/catalog.spec.ts
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java (controller idiom),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C14, §C15, §C16,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md §`/catalog` + §Catalog Sync diff page step labels,
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (catalog screens visual reference),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §CAT-01..07,
    apps/web/components/ui/tabs.tsx + table.tsx + button.tsx + accordion.tsx (primitives copied in 8A)
  </read_first>
  <behavior>
    - `AdminCatalogController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/catalog")`:
      - GET `/{provider}` → CatalogListResponse (models for provider, grouped by feature, with default flag + pin count).
      - POST `/{provider}/sync/fetch` → CatalogSyncFetchResponse{jobId, status: 'IN_PROGRESS'|'AWAITING_CONFIRM'} (Anthropic returns 400 with `error.admin.catalog_sync_anthropic_disabled`).
      - GET `/sync/{jobId}/diff` → CatalogSyncDiffResponse{added, removed, changed, status}.
      - POST `/sync/{jobId}/confirm` → 204; calls CatalogSyncOrchestrator.confirm.
      - POST `/sync/{jobId}/cancel` → 204.
      - POST `/{provider}/models` body CatalogModelCreateRequest{modelId, displayName, costIn, costOut, isRecommended} → 201; calls CatalogAdminService.createManualModel.
      - POST `/models/{modelId}/disable` body CatalogModelDisableRequest{reason, confirmedPinned, pinnedCountAcknowledged} → 204.
      - PUT `/{provider}/{feature}/default` body `{modelId}` → 204; sets default for (provider, feature).
    - `SettingsCatalogController @RequestMapping("/api/settings/catalog")` (user-facing, no @PreAuthorize ADMIN — uses user chain authentication only):
      - GET `/` → CuratedCatalogResponse (per-feature {provider, model_id, display_name, is_default, is_recommended, cost_per_1k_input, cost_per_1k_output, deprecated_at} list); ETag support via If-None-Match.
    - `CatalogModelCreateRequest`: `@NotBlank @Pattern(regexp="^[a-zA-Z0-9._:/\\-]{1,128}$") String modelId; @NotBlank @Size(max=200) String displayName; @DecimalMin("0") BigDecimal costPer1kInput; @DecimalMin("0") BigDecimal costPer1kOutput; boolean isRecommended`.
    - `CatalogModelDisableRequest`: `@NotBlank @Size(min=8,max=500) @NoSentinelLeak String reason; boolean confirmedPinned; int pinnedCountAcknowledged`.
    - apps/admin `/catalog` route: provider tabs (6 providers); inside each tab a 3-feature sub-tab (CHAT/TRIAGE/DRAFT) showing models table (model_id mono + display_name + is_default chip + is_recommended star + pin count badge + Disable button per row) + `Sync from /models` button top-right (disabled w/ tooltip for Anthropic per UI-SPEC line 144 / CAT-04 + tooltip copy `Anthropic has no public /models endpoint — add new models via manual entry`). Anthropic provider also shows `Add model manually` form (modelId mono input + display_name + cost fields).
    - `/catalog-sync/{jobId}` (or modal inside /catalog/{provider}): 3-step wizard (`1. Fetch`, `2. Diff`, `3. Confirm` — connected stepper per UI-SPEC line 210). Step 2 renders `<JsonDiffViewer>` from 8A with added/removed/changed groups. Step 3 has explicit `Confirm sync` button.
    - Disable flow: clicking Disable on a model with pin count >0 opens `<ConfirmTwiceDialog>` with consequence list `{pinnedCount} tenants are currently using this model; they will continue using it until they pick a different one`. Step-2 token: model_id literal + count display (per UI-SPEC line 202 `Disable {N} pinned model`).
    - Playwright `catalog.spec.ts`: login → /catalog → OpenAI tab → click Sync from /models (mocked /models response with 1 new model) → step 2 renders diff → click Confirm → toast `Sync OK` → table refreshes with new model.
  </behavior>
  <action>
    Implement controllers per PATTERNS §C14 + §C15. SettingsCatalogController is the FIRST user-side controller that mirrors admin-curated state to user UX (Phase 9 will consume it heavily). It belongs in `api/controllers/settings/` (sibling of `controllers/admin/`); @PreAuthorize is NOT `ADMIN` — instead `@PreAuthorize("isAuthenticated()")` since user chain requires auth. GroupedOpenApi `publicApi` (from 8A-04) includes `/api/settings/catalog`; `adminApi` includes `/api/admin/catalog/**`. Frontend per CONVENTIONS §8: typed `api.GET("/api/admin/catalog/{provider}",...)` from admin-schema.d.ts. Catalog Sync wizard: when fetch returns AWAITING_CONFIRM, frontend polls diff endpoint every 2s until populated; when populated render diff page. Stepper component composed from raw shadcn primitives (no new wrapper per UI-SPEC §rule line 226). Manual entry form for Anthropic visible inline (NOT in modal) because it's the primary action for that provider per CAT-04. `<ConfirmTwiceDialog>` reused from 8A. Playwright route interceptor stubs `/api/admin/catalog/OPENAI/sync/fetch` returning canned jobId then `/sync/{jobId}/diff` returning canned diff (no real provider call).
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.AdminCatalogController*" --tests "com.zeromail.api.controllers.settings.SettingsCatalogController*" && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/admin e2e -- --grep "catalog"</automated>
  </verify>
  <done>
    Catalog list + 3-step Sync wizard + manual entry + disable-with-pins flows work end-to-end with mocked /models; `/api/settings/catalog` returns curated public DTO with ETag; Anthropic Sync rejected at backend + UI button disabled; ChatModel cache eviction observed after Confirm.
  </done>
  <acceptance_criteria>
    - `GET /api/admin/catalog/ANTHROPIC` returns 3 Claude models seeded by 053; Sync button in UI is disabled with tooltip.
    - `POST /api/admin/catalog/ANTHROPIC/sync/fetch` returns 400 with `error.admin.catalog_sync_anthropic_disabled`.
    - `POST /api/admin/catalog/OPENAI/sync/fetch` returns 200 with jobId; second call within 60s returns the same jobId (debounced).
    - `POST /api/admin/catalog/OPENAI/models {modelId:"openai/test-model", displayName:"Test"}` returns 201 + 1 MODEL_CREATED audit + 1 CatalogChangedEvent.
    - `POST /api/admin/catalog/OPENAI/models {modelId:"bad id!", ...}` returns 400 `error.admin.catalog_model_id_invalid`.
    - `POST /api/admin/catalog/models/anthropic/claude-4.7-opus/disable {reason:"deprecated", confirmedPinned:false}` with 5 pinned tenants returns 400; with `confirmedPinned:true` returns 204 + sets deprecated_at + writes MODEL_DISABLED audit row.
    - `GET /api/settings/catalog` returns CuratedCatalogResponse with per-feature lists; no admin-only field (sync_history, dependents_count) present in response; ETag header returned; second GET with `If-None-Match: <etag>` returns 304.
    - Playwright `catalog.spec.ts`: Sync wizard step 1 → step 2 (diff visible) → step 3 (Confirm) toast OK; table re-renders with new model row.
    - `GET /v3/api-docs/public` includes `/api/settings/catalog`; excludes `/api/admin/catalog/**`. `GET /v3/api-docs/admin` includes admin catalog paths.
  </acceptance_criteria>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin browser → /api/admin/catalog/** | Catalog edits; Sync triggers outbound /models probe |
| backend/worker → provider /models | Outbound HTTPS using master key; schema validation before DB commit |
| Catalog state → user-facing /api/settings/catalog | Read-side projection with admin-only fields stripped |
| processing_job payload_json | Contains model IDs + diff; never raw provider error bodies |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-35 | Tampering | Provider serves malicious /models payload (supply chain) | mitigate | ModelSchemaValidator per-provider JSON Schema + regex on model_id; defense in depth at Fetch AND Confirm steps |
| T-08-36 | Information Disclosure | Provider error response body persisted to processing_job.payload_json | mitigate | ModelsProbeClient strips response body; payload_json stores only enum reason (INVALID_KEY/RATE_LIMITED/NETWORK_ERROR/TIMEOUT/SCHEMA_MISMATCH) |
| T-08-37 | Denial of Service | Rapid Sync clicks flood worker queue | mitigate | Redis SETNX 60s debounce per provider; per-admin rate-limit inherits MKEY pattern if needed |
| T-08-38 | Tampering | Auto-apply Sync silently changing catalog | mitigate | 3-step Fetch → Diff → Confirm with explicit Confirm action; cancel rolls back; CAT-02 acceptance forbids auto-apply |
| T-08-39 | Information Disclosure | User-facing /api/settings/catalog leaks admin-only fields | mitigate | CuratedCatalogResponse DTO has explicit field allowlist; admin-only fields (sync_history, dependents_count) live in admin DTO only |
| T-08-40 | Elevation of Privilege | User triggers Sync via /api/settings endpoint | mitigate | Settings controller has no Sync endpoint; Sync is admin-chain only; ArchUnit verifies no cross-controller call |
| T-08-41 | Tampering | Confirm replayed after diff stale (catalog changed underneath) | mitigate | Confirm validates job status=DIFF_READY + actor match; @Transactional + SELECT FOR UPDATE on small lock table per PATTERNS §C10 |
| T-08-42 | Information Disclosure | Schema files leak provider internals | accept | Per-provider JSON Schemas at /catalog-schemas/*.schema.json contain only well-known public API shapes |
| T-08-43 | Tampering | Disable model with pinned tenants without confirmation | mitigate | confirmedPinned boolean required in CatalogModelDisableRequest when pinned count >0; server validates against current count (re-fetched in same tx) |
| T-08-44 | Information Disclosure | LLM cache holds stale ChatModel after catalog change | mitigate | CatalogChangedEvent @ApplicationModuleListener evicts by affectedModelIds; eviction runs on every commit via AFTER_COMMIT semantics |
| T-08-SC | Tampering | `json-schema-validator` library inclusion | mitigate | Verify com.networknt:json-schema-validator already on Spring Cloud / Boot classpath via `./gradlew dependencies | grep json-schema`; if introducing, treat as [VERIFIED] (npmjs/maven central established maintainer) — no [ASSUMED] markers |

</threat_model>

<verification>

```bash
./gradlew :backend:core:test :backend:api:test :backend:worker:test --tests "*Catalog*"
pnpm --filter @zeromail/admin e2e -- --grep "catalog"

mcp__postgres__execute_sql "SELECT count(*) FROM model_catalog WHERE provider='ANTHROPIC'"  # expect 3
mcp__postgres__execute_sql "SELECT count(*) FROM feature_binding WHERE provider='ANTHROPIC' AND is_default=true"  # expect 3 (one per feature)

curl -s http://localhost:8080/v3/api-docs/public | jq '.paths | keys[]' | grep '/api/settings/catalog'  # expect present
curl -s http://localhost:8080/v3/api-docs/admin  | jq '.paths | keys[]' | grep '/api/admin/catalog'   # expect present
```

</verification>

<success_criteria>
- [ ] Liquibase 052 + 053 deploy with FK + UNIQUE partial index + Anthropic seed
- [ ] FK on assistant_settings.*_model_id → model_catalog.model_id enforced
- [ ] CatalogSyncOrchestrator 3-step Fetch/Diff/Confirm with SKIP LOCKED + Redis debounce
- [ ] Anthropic Sync disabled at backend + UI tooltip
- [ ] ModelSchemaValidator regex + per-provider JSON Schema enforcement
- [ ] Disable-with-pins requires confirmedPinned=true + soft-delete; pinned tenants keep working
- [ ] CuratedCatalogQueryService serves /api/settings/catalog with ETag cache
- [ ] CatalogChangedEvent triggers ChatModel cache eviction
- [ ] Admin catalog flows via Playwright e2e
- [ ] GroupedOpenApi places /api/settings/catalog in public; admin catalog endpoints in admin
- [ ] Stepper wizard renders with shadcn primitives composed raw (no new wrapper)
</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8D-SUMMARY.md` when done.
</output>
