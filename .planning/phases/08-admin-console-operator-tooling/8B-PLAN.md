---
phase: 08-admin-console-operator-tooling
plan: 8B
type: execute
wave: 2
depends_on:
  - 08-8A
files_modified:
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/LlmProvider.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/KeyFormat.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/event/MasterKeyRotatedEvent.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyMasker.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyEditSessionService.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyRateLimiter.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/projection/MasterKeyMaskedRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/projection/MasterKeyDependentsCount.java
  - backend/core/src/main/java/com/zeromail/core/admin/mkey/package-info.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClient.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/package-info.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiChatModelFactory.java
  - backend/core/src/main/java/com/zeromail/core/shared/crypto/PlatformSecretCipher.java
  - backend/core/src/main/resources/db/changelog/changes/051-llm-provider-master-key.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeyMaskedResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeySetRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeyEditSessionResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/TestConnectionResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/RotateMasterKeyRequest.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeyResolverConfinementTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeySentinelLeakTest.java
  - apps/admin/src/routes/master-keys.tsx
  - apps/admin/src/routes/master-keys-provider.tsx
  - apps/admin/src/features/master-keys/master-keys-api.ts
  - apps/admin/src/features/master-keys/query-keys.ts
  - apps/admin/src/features/master-keys/use-master-keys.ts
  - apps/admin/src/features/master-keys/use-edit-session.ts
  - apps/admin/src/features/master-keys/use-test-connection.ts
  - apps/admin/src/features/master-keys/use-rotate-master-key.ts
  - apps/admin/src/components/MaskedSecretField.tsx

autonomous: false
requirements:
  - MKEY-01
  - MKEY-02
  - MKEY-03
  - MKEY-04
  - MKEY-05
  - MKEY-06
  - MKEY-07
  - MKEY-08
  - ARCH-11

must_haves:
  truths:
    - "Operator can set the master key for OpenAI, Anthropic, Google, DeepSeek, OpenRouter, and 9Router via `/master-keys/<provider>` form."
    - "On save, the key is AES-GCM-encrypted via `PlatformSecretCipher` (relocated `RefreshTokenCipher` with provider AAD) and written to `llm_provider_master_key`."
    - "Subsequent GET returns masked-only (`sk-****abc1`) — full plaintext never returned in API response, log line, or DOM after save."
    - "Editing a key requires a 5-minute edit-session token issued by POST `/api/admin/master-keys/{provider}/edit-session`; missing/expired token returns HTTP 400."
    - "Edit-session minting rate-limited via Redis to 10 req/hour/admin; >10 returns HTTP 429."
    - "Test-connection calls provider `/v1/models` and returns ONLY enum `OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT`; provider error bodies stripped server-side."
    - "Rotation flow: enter new key → test-connection → on OK write new row + emit `MasterKeyRotatedEvent` → @ApplicationModuleListener evicts every cached ChatModel for that provider; on test failure old key preserved + audit row MASTER_KEY_ROTATION_FAILED."
    - "9Router master-key entry has `key_format` toggle between `OPENAI_FORMAT` and `ANTHROPIC_FORMAT`; ProviderMasterKeyResolver routes to OpenAI vs Anthropic Spring AI adapter accordingly; other 5 providers have fixed adapter."
    - "Master-key list UI surfaces per-provider dependents count badge + 90-day-old `Rotation recommended` tag."
    - "`ProviderMasterKeyResolver` is the SOLE class reading `llm_provider_master_key`; ArchUnit `MasterKeyResolverConfinementTest` green."
    - "Master-key audit rows contain only `{masked_key, kek_version, last_rotated_at}` — never plaintext, never encrypted bytes, never `sk-` prefix."
    - "`MasterKeySentinelLeakTest` CI gate green: no `sk-`, `sk-ant-`, `AIza`, `sk-or-` substring in test build's logs, response payloads, exception messages, audit JSON, or YAML."
    - "Admin can pick per-feature default provider for `chat`, `triage`, `draft` (default `OpenRouter` preserved at launch) via a per-feature selector that writes to a column on the matching `feature_binding` row when 8D lands; in 8B the default-provider selector is a stub returning current value from a `feature_default_provider` column added in this plan to `llm_provider_master_key`."
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/051-llm-provider-master-key.yaml"
      provides: "`llm_provider_master_key` (provider PK, key_format, encrypted_key BYTEA, kek_version SMALLINT, created_by_user_id UUID, created_at, last_rotated_at) — provider is enum CHECK in 6 values."
    - path: "backend/core/src/main/java/com/zeromail/core/shared/crypto/PlatformSecretCipher.java"
      provides: "Relocated/wrapped `RefreshTokenCipher` exposing `encrypt(byte[] plaintext, String associatedData)` so master keys can use AAD `platform:master_key:{provider.id()}` instead of tenantId."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java"
      provides: "set / get-masked / mint-edit-session / test-connection / rotate flows with same-tx audit + Modulith event emit."
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java"
      provides: "Sole reader of llm_provider_master_key; resolves provider → decrypted plaintext + adapter (OpenAI/Anthropic/Google/DeepSeek) for downstream Spring AI calls."
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java"
      provides: "@ApplicationModuleListener on `MasterKeyRotatedEvent` evicts cached ChatModels for that provider across tenants."
    - path: "backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeySentinelLeakTest.java"
      provides: "CI gate scanning build/ + logs + admin_audit_event JSON for sk-/sk-ant-/AIza/sk-or- substrings."
    - path: "apps/admin/src/components/MaskedSecretField.tsx"
      provides: "Input that never round-trips plaintext after save; integrates with edit-session mint + test-PASS pill + 5-min countdown."
  key_links:
    - from: "core.llm.gateway.springai.SpringAiChatModelFactory"
      to: "ProviderMasterKeyResolver"
      via: "factory consults resolver for provider key before constructing ChatModel"
      pattern: "ProviderMasterKeyResolver\\.resolve"
    - from: "MasterKeyAdminService.rotate"
      to: "ChatModelCacheEvictionListener"
      via: "ApplicationEventPublisher.publishEvent(new MasterKeyRotatedEvent(provider))"
      pattern: "MasterKeyRotatedEvent"
    - from: "apps/admin/src/routes/master-keys-provider.tsx"
      to: "POST /api/admin/master-keys/{provider}/test-connection"
      via: "useTestConnection hook calls api.POST"
      pattern: "test-connection"
---

<objective>
Deliver the 6-provider master-key admin surface: encrypted storage with AES-GCM via relocated `PlatformSecretCipher` (provider AAD), masked-only display + 5-min edit-session token + 10/hr rate-limit, enum-only test-connection oracle calling `GET /v1/models`, transactional rotation with `MasterKeyRotatedEvent` + ChatModel cache eviction, 9Router dual-mode key_format toggle, dependents count + 90-day rotation tag, single-resolver confinement (ArchUnit), and the `MasterKeySentinelLeakTest` CI gate. Frontend ships `/master-keys` list + per-provider edit page with `<MaskedSecretField>`.

Purpose: Master keys are the highest-blast-radius secret in Zero Mail. The whole sentinel-leak invariant (ARCH-11) hinges on this plan. Every other LLM-touching subsystem (8D catalog Sync, runtime chat/triage/draft) consults `ProviderMasterKeyResolver` after this plan lands.

Output: Operator can set + rotate keys for all 6 providers; rotation atomically swaps the cached ChatModel; the platform never leaks a `sk-` byte to log, response, audit, or YAML.
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
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java
@backend/core/src/main/java/com/zeromail/core/llm/usecases/ByokService.java
@backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java
@backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/
@backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java
@backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java
@backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java
</context>

<documentation_lookup>
Context7 mandatory before coding:
- `/spring-projects/spring-ai` for `StreamingChatModel` / `ChatModel` cache eviction patterns + provider adapter (OpenAI, Anthropic, Google GenAI, DeepSeek) configuration in M6.
- `/spring-projects/spring-modulith` for `@ApplicationModuleListener` event semantics + same-process delivery guarantees.
- `/spring-projects/spring-data-redis` for atomic INCR-with-TTL rate-limit idiom (used by MasterKeyRateLimiter).
</documentation_lookup>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8B-01: Liquibase 051 + LlmProvider/KeyFormat enums + LlmProviderMasterKeyEntity/Repository + PlatformSecretCipher relocation + MasterKeyMasker + MasterKeyResolverConfinement ArchUnit + MasterKeySentinelLeak CI gate</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/051-llm-provider-master-key.yaml,
    backend/core/src/main/resources/db/changelog/db.changelog-master.yaml,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/LlmProvider.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/KeyFormat.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyRepository.java,
    backend/core/src/main/java/com/zeromail/core/shared/crypto/PlatformSecretCipher.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyMasker.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeyResolverConfinementTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeySentinelLeakTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java (lines 1-100 — AES-GCM with AAD, KEK versioning),
    backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java (lines 1-70 — byte[] columns + IdentifiedEnum attribute converter),
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml (YAML changeset idiom),
    backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java (repo-confinement pattern),
    backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java (repo-wide grep/scan gate pattern for sentinel scan),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C7, §C17,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §MKEY-01/07/08 + §ARCH-11
  </read_first>
  <behavior>
    - 051-llm-provider-master-key.yaml: `provider VARCHAR(32) PRIMARY KEY CHECK IN ('OPENAI','ANTHROPIC','GOOGLE','DEEPSEEK','OPENROUTER','ROUTER_9R'), key_format VARCHAR(32) NOT NULL CHECK IN ('OPENAI_FORMAT','ANTHROPIC_FORMAT'), encrypted_key BYTEA NOT NULL, kek_version SMALLINT NOT NULL, created_by_user_id UUID NOT NULL FK admin_users.id, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), last_rotated_at TIMESTAMPTZ, base_url VARCHAR(500), feature_default_provider_chat BOOLEAN, feature_default_provider_triage BOOLEAN, feature_default_provider_draft BOOLEAN`. CHECK that for 5 single-mode providers `key_format` matches their adapter (OPENAI/OPENROUTER/DEEPSEEK→OPENAI_FORMAT, ANTHROPIC→ANTHROPIC_FORMAT, GOOGLE→GOOGLE_FORMAT — extend KeyFormat enum if Google needs separate adapter or use OPENAI_FORMAT for Google GenAI per Spring AI adapter mapping; verify via Context7 `/spring-projects/spring-ai`).
    - `LlmProvider` IdentifiedEnum: OPENAI(1), ANTHROPIC(2), GOOGLE(3), DEEPSEEK(4), OPENROUTER(5), ROUTER_9R(6); static `fromId(int)`.
    - `KeyFormat` IdentifiedEnum: OPENAI_FORMAT(1), ANTHROPIC_FORMAT(2), GOOGLE_FORMAT(3) (if needed); static `fromId`.
    - `LlmProviderMasterKeyEntity`: JPA entity with byte[] encrypted_key + provider as PK + IdentifiedEnum converters; explicit getters/setters; no Lombok.
    - `LlmProviderMasterKeyRepository`: standard JpaRepository<LlmProviderMasterKeyEntity, LlmProvider> + custom `upsertWithRotation(LlmProvider, KeyFormat, byte[] encryptedKey, short kekVersion, UUID actorId, String baseUrl)` native query INSERT…ON CONFLICT(provider) DO UPDATE.
    - `PlatformSecretCipher`: relocate / wrap RefreshTokenCipher into `core.shared.crypto`. Decision (per PATTERNS §C7 Pitfall 3): create `PlatformSecretCipher` class that delegates to RefreshTokenCipher and overloads `encrypt(byte[] plaintext, String associatedData)` and `decrypt(byte[] envelope, String associatedData)` so master keys pass `"platform:master_key:" + provider.id()` as AAD. Keep RefreshTokenCipher in place (tenant OAuth callers unchanged) — this is the lower-risk option.
    - `MasterKeyMasker`: pure function `mask(byte[] plaintext, LlmProvider provider)` returns `sk-****abc1` for OpenAI-shape and `sk-ant-****abc1` for Anthropic-shape; never emits first 4 chars.
    - `MasterKeyResolverConfinementTest`: ArchUnit `noClasses().that().resideOutsidePackage("..core.llm.gateway.springai.admin..").should().dependOnClassesThat().areAssignableTo(LlmProviderMasterKeyRepository.class).allowEmptyShould(true)` — only ProviderMasterKeyResolver can inject the repo.
    - `MasterKeySentinelLeakTest`: scans `build/reports/tests/`, `build/test-results/`, `build/logs/`, plus admin_audit_event JSONB exports (test fixture dumps to temp file) for substrings `sk-`, `sk-ant-`, `AIza`, `sk-or-` (and their base64 / hex-encoded forms). Fixture inserts `sk-test123` into a log line + audit row to assert the test fails on that input; production code green.
  </behavior>
  <action>
    Implement entity + repo + cipher relocation per PATTERNS §C7. Cipher AAD must include provider.id() so row-swap (ciphertext OPENAI decrypted as ANTHROPIC) fails GCM tag verification. Per CONVENTIONS §3+§4: entity is class (not record); enums use IdentifiedEnum + fromId fail-loud; column converters mirror BYOKProviderAttributeConverter shape. Liquibase 051 uses `<sql splitStatements:false>` for the CHECK constraints across enum values. The `feature_default_provider_*` columns are platform-wide defaults for chat/triage/draft features (per success criterion #8 of ROADMAP) — only one provider can have each flag = TRUE (enforced by partial UNIQUE index `CREATE UNIQUE INDEX one_default_per_feature ON llm_provider_master_key(provider) WHERE feature_default_provider_chat IS TRUE` and similarly for triage/draft). Seed migration sets `OPENROUTER` row with all 3 defaults=TRUE per ROADMAP "default OpenRouter preserved at launch". MasterKeySentinelLeakTest runs as a JUnit 5 `@Test` in `:backend:core` test source; uses `Files.walk(buildDir).filter(p -> p.toString().matches(".*(log|out|json|xml)$"))` + `String.contains(sentinel)` + Base64.getEncoder().encodeToString(sentinel.getBytes()) for encoded form. Test is annotated `@Tag("ci-gate")` so CI invokes via `./gradlew test --tests "*SentinelLeak*"`. MasterKeyResolverConfinementTest extends DraftPathArchUnitTest-style `noClasses().that().resideOutsidePackage(...)` rule pinned to `core.llm.gateway.springai.admin`.
  </action>
  <verify>
    <automated>./gradlew :backend:core:liquibaseUpdate -Pdb=h2 && ./gradlew :backend:core:test --tests "com.zeromail.core.admin.mkey.*" --tests "com.zeromail.core.admin.arch.MasterKeyResolverConfinementTest" --tests "com.zeromail.core.admin.arch.MasterKeySentinelLeakTest"</automated>
  </verify>
  <done>
    051 deploys; PlatformSecretCipher round-trips with provider AAD; MasterKeyMasker emits last-4-only; ArchUnit rejects fixture injecting LlmProviderMasterKeyRepository outside `core.llm.gateway.springai.admin`; sentinel-leak gate fails on injected `sk-test123` fixture, green on production code.
  </done>
  <acceptance_criteria>
    - `mcp__postgres__list_objects` shows `llm_provider_master_key` with PK on `provider`.
    - `INSERT INTO llm_provider_master_key (provider, ...) VALUES ('UNKNOWN', ...)` returns CHECK violation.
    - `MasterKeyMasker.mask("sk-proj-abcdef1234".getBytes(), OPENAI)` returns `sk-****1234` (never `sk-pr*`).
    - `PlatformSecretCipher.decrypt(encryptOpenAi, "platform:master_key:2")` (ANTHROPIC AAD) throws GeneralSecurityException (AAD mismatch).
    - MasterKeyResolverConfinementTest: ArchUnit fixture class in test sources autowiring `LlmProviderMasterKeyRepository` from outside `core.llm.gateway.springai.admin` fails the rule.
    - MasterKeySentinelLeakTest: temp fixture writing `sk-test123` to a build log makes test red; removing the fixture makes test green; same for base64-encoded form `c2stdGVzdDEyMw==`.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8B-02: MasterKeyAdminService (set/get-masked/edit-session/test-connection/rotate) + MasterKeyEditSessionService + MasterKeyRateLimiter (Redis) + MasterKeyRotatedEvent + ProviderMasterKeyResolver + ModelsProbeClient + ChatModelCacheEvictionListener</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyEditSessionService.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyRateLimiter.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/event/MasterKeyRotatedEvent.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/projection/MasterKeyMaskedRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/projection/MasterKeyDependentsCount.java,
    backend/core/src/main/java/com/zeromail/core/admin/mkey/package-info.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClient.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/package-info.java,
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiChatModelFactory.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/llm/usecases/ByokService.java (lines 1-100 — service-with-cipher shape + AdminContext analog for tenantId),
    backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java (lines 60-90 — @ApplicationModuleListener idiom),
    backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java (event record idiom),
    backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ (entire directory — existing ChatModel factory + adapters),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C7, §C8,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §MKEY-02/03/04/05/06/07/08,
    .planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md §Pitfall 2 (master-key oracle) + §Pitfall 10 (cache race)
  </read_first>
  <behavior>
    - `MasterKeyAdminService.set(provider, keyFormat, plaintextBytes, baseUrlOptional, editSessionToken)`:
      1. validate edit-session token (MasterKeyEditSessionService.consume).
      2. check rate-limit (MasterKeyRateLimiter.allow(actorId)).
      3. encrypt via PlatformSecretCipher with AAD `"platform:master_key:" + provider.id()`.
      4. upsert row in same @Transactional + write `MASTER_KEY_SET` audit row with `after_state_json={masked_key, kek_version, last_rotated_at:null, provider, key_format}` (NO encrypted_bytes, NO plaintext).
      5. emit `MasterKeyRotatedEvent(provider)` after-commit (Spring Modulith).
    - `MasterKeyAdminService.testConnection(provider, plaintextBytes)`:
      1. call `ModelsProbeClient.probe(provider, plaintextBytes)` which performs HTTP GET `/v1/models` against provider base_url (per-provider equivalent for Google GenAI / DeepSeek per Spring AI docs).
      2. map result to enum: OK | INVALID_KEY (401/403) | RATE_LIMITED (429) | NETWORK_ERROR (connection failure) | TIMEOUT (>5s).
      3. NEVER return raw response body; NEVER log raw body; logs format `event=master_key_test provider={} result={}` only.
      4. write `MASTER_KEY_TESTED` audit row with `after_state_json={result_enum, tested_at}` — NEVER provider body.
    - `MasterKeyAdminService.rotate(provider, newPlaintextBytes, editSessionToken, reason)`:
      1. testConnection(provider, newPlaintextBytes); if not OK → write `MASTER_KEY_ROTATION_FAILED` audit + return failure; old row preserved.
      2. on OK: update row with new encrypted_key + last_rotated_at=NOW + write `MASTER_KEY_ROTATED` audit row.
      3. emit `MasterKeyRotatedEvent(provider)` after-commit.
    - `MasterKeyEditSessionService`: Redis-backed `SETEX zeromail:mkey:edit-session:{actorId}:{provider} {randomToken} 300` on mint; `GETDEL` on consume; returns Optional.empty if missing or expired.
    - `MasterKeyRateLimiter`: Redis `INCR zeromail:mkey:edits:{actorId}:{epoch_hour}` + `EXPIRE 3600`; allow if count ≤ 10 else throw RateLimitedException → HTTP 429.
    - `ProviderMasterKeyResolver`: sole class injecting LlmProviderMasterKeyRepository; caches decrypted plaintext in `ConcurrentHashMap<LlmProvider, CachedKey(plaintext, kekVersion, fetchedAt)>` with TTL synced to ChatModel cache lifetime (60 min default, override via property); listener clears on `MasterKeyRotatedEvent`. Exposes `resolve(LlmProvider) → ResolvedKey(plaintext, keyFormat, baseUrl)`.
    - `ChatModelCacheEvictionListener`: `@ApplicationModuleListener` on `MasterKeyRotatedEvent` → calls `SpringAiChatModelFactory.evictByProvider(provider)` AND `ProviderMasterKeyResolver.invalidate(provider)`. Logs `event=chat_model_cache_evicted reason=master_key_rotated provider={}`.
    - `SpringAiChatModelFactory` (existing file — modify): add `evictByProvider(LlmProvider)` method that clears any cached ChatModel instances for that provider across all tenants; integrate ProviderMasterKeyResolver into the factory's key-resolution path so platform-default features pull from master key when tenant has no BYOK pin.
    - `MasterKeyMaskedRow`: projection record (provider, masked_key, key_format, kek_version, last_rotated_at, dependents_count, is_rotation_recommended, base_url, feature_default_provider_chat, feature_default_provider_triage, feature_default_provider_draft).
    - `MasterKeyDependentsCount`: query joining `byok_credential` + `assistant_settings.*_model_id` → integer count of tenants using this provider through platform path.
    - 9Router-specific behavior: when `provider=ROUTER_9R`, `MasterKeyAdminService.set` accepts `key_format` argument; ProviderMasterKeyResolver routes ROUTER_9R+OPENAI_FORMAT → OpenAiChatModel at base_url; ROUTER_9R+ANTHROPIC_FORMAT → AnthropicChatModel at base_url. Other 5 providers: key_format is fixed (validated against expected value in `MasterKeyAdminService.set`).
  </behavior>
  <action>
    Implement service + resolver + listener per PATTERNS §C7/§C8 excerpts. Same-transaction audit row via AdminAuditWriter (from 8A). After-commit event publish via Spring's `ApplicationEventPublisher` — Modulith listener fires after the transaction commits, so cache eviction only happens on confirmed rotation. Use `@TransactionalEventListener(phase=AFTER_COMMIT)` semantics that `@ApplicationModuleListener` provides. ModelsProbeClient uses `RestClient` (Spring 6.1+) with 5s timeout, NO request/response body logging (custom interceptor that logs only status code + duration). Per RESEARCH §Pitfall 2 (master-key oracle): test-connection responses MUST NOT distinguish RATE_LIMITED vs INVALID_KEY in any way except the enum value (no different status codes leaked, no different headers, no different timings — add a constant 50ms jitter sleep before returning to obscure timing). Rate-limit Redis key TTL is whole hour (epoch_hour discriminator), not sliding — simpler, sufficient. Per RESEARCH §Pitfall 10: ChatModelCacheEvictionListener also evicts BYOK ChatModels routed through platform if platform-default model bound to this provider rolls forward (look up affected tenants via `byok_credential` join). SpringAiChatModelFactory modification: introduce `Map<CacheKey, ChatModel>` where CacheKey = (tenantId, feature, provider, modelId); `evictByProvider` removes all entries matching provider; existing chat/triage/draft call sites continue to use the factory unchanged. Audit row's `after_state_json` SCHEMA EXPLICITLY enumerated: only `{masked_key, kek_version, last_rotated_at, provider, key_format}` keys allowed — MasterKeySentinelLeakTest scans these JSONB columns specifically.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.mkey.usecases.*" --tests "com.zeromail.core.llm.gateway.springai.admin.*"</automated>
  </verify>
  <done>
    set/test/rotate flows green under mocked HTTP probe; edit-session + rate-limit enforced; ProviderMasterKeyResolver cache hits after first call; event-driven cache eviction triggers ChatModel factory eviction; 9Router dual-mode routes correctly; no sentinel byte in any log line / audit row / response.
  </done>
  <acceptance_criteria>
    - `MasterKeyAdminService.set(OPENAI, OPENAI_FORMAT, "sk-proj-test".bytes, null, validToken)` succeeds; subsequent `getMasked(OPENAI)` returns `sk-****test`.
    - `MasterKeyAdminService.set(...)` without valid edit-session token throws + maps to HTTP 400.
    - `MasterKeyAdminService.testConnection(OPENAI, "invalid".bytes)` returns INVALID_KEY when MockServer returns 401; response body contains only `{"result":"INVALID_KEY"}`.
    - `MasterKeyAdminService.rotate(OPENAI, badBytes, ...)` writes MASTER_KEY_ROTATION_FAILED audit + does NOT update encrypted_key + does NOT publish MasterKeyRotatedEvent.
    - `MasterKeyAdminService.rotate(OPENAI, goodBytes, ...)` updates row + publishes event; ChatModelCacheEvictionListener.on(event) fires and SpringAiChatModelFactory.evictByProvider(OPENAI) called once.
    - 11 sequential edit-session mints in same hour: req 1-10 return token; req 11 returns HTTP 429.
    - 9Router OPENAI_FORMAT: `ProviderMasterKeyResolver.resolve(ROUTER_9R).adapter()` returns OpenAiChatModel-class adapter; ANTHROPIC_FORMAT returns Anthropic adapter.
    - MasterKeySentinelLeakTest still green (no `sk-` byte leaked from any service method in 8B-02).
    - Log inspection: `grep -rE 'sk-[a-zA-Z0-9]{4,}' build/reports/tests/` returns no matches.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8B-03: AdminMasterKeyController + DTOs (masked / set / edit-session / test-connection / rotate) + apps/admin /master-keys list + per-provider edit page + MaskedSecretField</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeyMaskedResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeyListResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeySetRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeyEditSessionResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/TestConnectionRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/TestConnectionResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/RotateMasterKeyRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/mkey/SetFeatureDefaultRequest.java,
    apps/admin/src/routes/master-keys.tsx,
    apps/admin/src/routes/master-keys-provider.tsx,
    apps/admin/src/features/master-keys/master-keys-api.ts,
    apps/admin/src/features/master-keys/query-keys.ts,
    apps/admin/src/features/master-keys/use-master-keys.ts,
    apps/admin/src/features/master-keys/use-edit-session.ts,
    apps/admin/src/features/master-keys/use-test-connection.ts,
    apps/admin/src/features/master-keys/use-rotate-master-key.ts,
    apps/admin/src/features/master-keys/use-set-feature-default.ts,
    apps/admin/src/components/MaskedSecretField.tsx,
    apps/admin/e2e/master-keys.spec.ts
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java (entire — controller + Tag + @PreAuthorize),
    backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java (DTO record idiom),
    apps/web/components/ui/input.tsx + sheet.tsx + button.tsx + alert-dialog.tsx + badge.tsx + tooltip.tsx + sonner.tsx (primitives reused via apps/admin copy),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C14, §C16,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md §`/master-keys` + §Destructive action confirmations + §MaskedSecretField + §Microcopy,
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (visual reference for master-keys screen),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §MKEY-01..08
  </read_first>
  <behavior>
    - `AdminMasterKeyController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/master-keys")`:
      - GET `/` → `MasterKeyListResponse` with 6 rows (one per LlmProvider): masked_key (or "Not set"), key_format, last_rotated_at, dependents_count, is_rotation_recommended (true if last_rotated_at older than 90d), base_url, feature_defaults_chat/triage/draft.
      - GET `/{provider}` → `MasterKeyMaskedResponse`.
      - POST `/{provider}/edit-session` → `MasterKeyEditSessionResponse {token, expiresAt}` (5-min validity). Rate-limit applied here.
      - POST `/{provider}/test-connection` body `{plaintextKey, baseUrl?, keyFormat?}` → `TestConnectionResponse {result: enum}`. NOTE: this endpoint accepts plaintext key only over HTTPS + requires valid edit-session token. NEVER echoes plaintext back.
      - PUT `/{provider}` body `{plaintextKey, keyFormat, baseUrl?, editSessionToken, reason}` → 204 No Content. Calls MasterKeyAdminService.set(...) (which also runs test-connection internally; on fail returns 400 with enum). Audit row written.
      - POST `/{provider}/rotate` body `{newPlaintextKey, keyFormat, editSessionToken, reason}` → `RotationResponse {result: 'OK'|'TEST_FAILED', testResult?: enum}`. Calls MasterKeyAdminService.rotate(...).
      - PUT `/feature-default` body `{feature: CHAT|TRIAGE|DRAFT, provider}` → 204. Updates the `feature_default_provider_{feature}` boolean across rows (sets selected provider's column to TRUE, all others to FALSE) inside @Transactional + audit row.
    - All requests require admin session (via @Order(1) chain); @PreAuthorize redundancy on controller.
    - DTOs: `MasterKeySetRequest` has `@NotBlank @Size(min=10,max=500) String plaintextKey` (server-side enforcement only; client should never persist or echo) + `@NotBlank String editSessionToken` + `@NotNull KeyFormat keyFormat` + `@NotBlank @Size(min=8,max=500) @NoSentinelLeak String reason`. `TestConnectionResponse.result` uses `@Schema(allowableValues={"OK","INVALID_KEY","RATE_LIMITED","NETWORK_ERROR","TIMEOUT"})`.
    - apps/admin `/master-keys` route: table of 6 providers (icon, masked_key as monospace, key_format badge, dependents count badge, 90-day amber tag if rotation recommended, last_rotated_at). Row click → `/master-keys/{provider}` edit page. Below table: 3 segmented controls (chat/triage/draft) with provider dropdown bound to `use-set-feature-default`.
    - `/master-keys/{provider}` edit page: `<MaskedSecretField>` + key_format toggle (only enabled for ROUTER_9R) + base_url input (only for ROUTER_9R + OPENROUTER) + Test connection button → result pill (OK green / others amber/red) + Save button (disabled until Test OK + valid edit session) + Rotate button (opens `<ConfirmTwiceDialog>` with provider name as step-2 token + amber strip variant).
    - `<MaskedSecretField>`: stateful component. Initial render: shows masked value from server (read-only). Click "Edit" → calls use-edit-session hook to mint token; input becomes editable with type=password; pasted plaintext NEVER stored in any React ref beyond the controlled input value; on submit value is POSTed and form clears + masked value re-fetched. Sentinel-leak client-side validator: if pasted value contains forbidden patterns AT WRONG LOCATIONS (e.g. value starts with `sk-` for an Anthropic field — wrong shape), shows warning but does NOT block (server is authoritative).
    - Playwright `master-keys.spec.ts`: login as seeded admin → navigate /master-keys → confirm 6 rows render → click OpenAI row → enter `sk-proj-test-1234` (mock provider returns 200 on /models) → click Test → pill shows OK → click Save → toast "OpenAI key saved" → refresh → masked-only `sk-****1234` displayed.
  </behavior>
  <action>
    Implement per PATTERNS §C14 (controller) + §C16 (frontend). Per CONVENTIONS §3, DTO records use `@JsonInclude(NON_NULL)` + `@Schema(requiredProperties)`. NoSentinelLeak validator from 8A applies to `reason` fields. `MasterKeySetRequest.plaintextKey` is the ONLY field carrying plaintext into the server — it has the privacy carve-out (like 8A's enrollmentUrl): NOT in the body-ban regex shape because it is incoming write-data, but server logs it as `plaintextKey={LENGTH:N}` only (never the bytes); JSON request body access in any logger is forbidden by SLF4J configuration already in place (verify). AdminPathBodyBanTest excludes write-request DTOs from the field-name scan (the rule scans projections + DTOs that flow OUT — verify the regex in 8A-01 only applies to response DTOs by adding `..api.dto.admin..` filter `that().areAnnotatedWith(@JsonView responses)` OR scoping the rule to `..response..` package suffix; if the existing rule cannot distinguish request/response, add explicit class-level annotation `@MasterKeyRequestCarve` to MasterKeySetRequest/RotateMasterKeyRequest/TestConnectionRequest and update AdminPathBodyBanTest to ignore classes carrying that marker). Frontend `<MaskedSecretField>` uses controlled `<input type="password">` whose React state is cleared on submit via `setValue("")` in onSuccess; ref to DOM input cleared via `inputRef.current.value=""` defensively. UI strings per UI-SPEC §Microcopy (`Tested OK · 2s ago` micro-pill; `Save blocked — run Test connection first and wait for PASS` blocked-save copy; `Connection test failed against {provider} /models. The previous key is preserved. Provider returned: {enum reason}.` error toast). Playwright spec stubs network for /v1/models via Playwright route interceptor.
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.AdminMasterKeyController*" && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/admin e2e -- --grep "master-keys"</automated>
  </verify>
  <done>
    Set/test/rotate endpoints respond per contract; edit-session token required; rate-limit returns 429; client always renders masked-only after save; rotation triggers cache eviction; feature-default selector flips column across rows; Playwright e2e green.
  </done>
  <acceptance_criteria>
    - `GET /api/admin/master-keys/` returns 6 rows; rows with no key show `masked_key: null` + `keyFormat: null`.
    - `POST /api/admin/master-keys/OPENAI/edit-session` returns 200 with `{token, expiresAt}`; 11th call within an hour returns 429.
    - `PUT /api/admin/master-keys/OPENAI` without editSessionToken returns HTTP 400 `error.admin.master_key_edit_session_required`.
    - `PUT /api/admin/master-keys/OPENAI` with invalid key (mock 401 on /models) returns 400 with `{result:"INVALID_KEY"}` and writes MASTER_KEY_TESTED audit row (no MASTER_KEY_SET row).
    - `PUT /api/admin/master-keys/OPENAI` with valid key writes 1 MASTER_KEY_TESTED + 1 MASTER_KEY_SET audit row + 1 MasterKeyRotatedEvent published.
    - `PUT /api/admin/master-keys/feature-default {feature:"CHAT", provider:"ANTHROPIC"}` updates `feature_default_provider_chat=TRUE` on ANTHROPIC row + FALSE on all 5 others + writes audit row.
    - Playwright spec: typing `sk-proj-test` + Test pill OK + Save → table refresh shows `sk-****test` masked.
    - Frontend network panel during Save submission shows POST body with plaintextKey field; response body contains no plaintext bytes; HTML/DOM after save shows only `sk-****test` (no first-4 characters visible).
    - MasterKeySentinelLeakTest still green after 8B-03 (audit JSON inspected).
  </acceptance_criteria>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin browser → `/api/admin/master-keys/**` | Plaintext key crosses TLS once per set/test/rotate; never echoed |
| backend/api → llm_provider_master_key | Same-JVM JPA; row insert in same @Transactional as audit |
| backend/api → provider `/v1/models` | Outbound HTTPS; response body discarded server-side, enum returned |
| ProviderMasterKeyResolver in-memory cache | Decrypted plaintext lives in heap; cleared on MasterKeyRotatedEvent |
| Redis (edit-session + rate-limit) | Token + counter stored TTL'd; not a credential store |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-14 | Spoofing | Edit-session token forgery | mitigate | 32-byte random token via SecureRandom; stored Redis SETEX 300s; GETDEL atomic consume; 10/hr/admin rate-limit caps brute force |
| T-08-15 | Tampering | Encrypted key row swap (OPENAI ciphertext as ANTHROPIC) | mitigate | PlatformSecretCipher AAD binds provider name (`platform:master_key:{provider.id()}`); GCM tag verification fails on AAD mismatch |
| T-08-16 | Repudiation | Master-key changes without audit | mitigate | MasterKeyAdminService.set/rotate write audit row in same @Transactional; rollback removes both |
| T-08-17 | Information Disclosure | Plaintext key in log | mitigate | Privacy logging format enforced + custom SLF4J filter scanning for `sk-`/`sk-ant-`/`AIza`/`sk-or-` substrings; MasterKeySentinelLeakTest CI gate scans all build artifacts |
| T-08-18 | Information Disclosure | Test-connection oracle leak (distinguishing invalid vs rate-limited) | mitigate | Enum-only response (no status code, no headers, no body, no timing distinction beyond constant 50ms jitter per RESEARCH §Pitfall 2) |
| T-08-19 | Information Disclosure | Provider error response body containing key fragment | mitigate | ModelsProbeClient discards response body server-side; only HTTP status + duration logged; never persisted |
| T-08-20 | Information Disclosure | Master-key audit row contains plaintext bytes | mitigate | Audit `after_state_json` schema enumerates allowed keys only (`{masked_key, kek_version, last_rotated_at, provider, key_format}`); ArchUnit + MasterKeySentinelLeakTest scan JSONB column |
| T-08-21 | Information Disclosure | DOM/network panel exposes plaintext during edit | mitigate | `<MaskedSecretField>` uses `<input type="password">`; React state cleared on submit; ref defensively cleared; no plaintext persisted in browser storage or query cache |
| T-08-22 | Denial of Service | Rapid rotation triggering cache thrash | accept | Rate-limit 10/hr/admin makes thrash impractical; ChatModel rebuild on next request is acceptable |
| T-08-23 | Elevation of Privilege | Non-resolver code reads llm_provider_master_key | mitigate | MasterKeyResolverConfinementTest ArchUnit pins repository access to `core.llm.gateway.springai.admin.ProviderMasterKeyResolver` only |
| T-08-24 | Tampering | KEK rotation in flight while reads occur | mitigate | kek_version stored per row; PlatformSecretCipher decrypt selects key by version; KEK rotation re-wraps in background batch (out-of-scope for 8B; rely on existing RefreshTokenCipher rotation path) |
| T-08-SC | Tampering | npm install for apps/admin master-keys feature (no new deps) | accept | No new dependencies introduced in 8B-03; uses primitives already vetted in 8A |

</threat_model>

<verification>

```bash
./gradlew :backend:core:test :backend:api:test --tests "*MasterKey*" --tests "*SentinelLeak*" --tests "*MasterKeyResolverConfinement*"
./gradlew :backend:core:liquibaseUpdate -Pdb=local
pnpm --filter @zeromail/admin test:unit
pnpm --filter @zeromail/admin e2e -- --grep "master-keys"

# Sentinel scan after full test run
grep -rE '(sk-[a-zA-Z0-9]{8,}|sk-ant-[a-zA-Z0-9]{8,}|AIza[a-zA-Z0-9]{8,}|sk-or-[a-zA-Z0-9]{8,})' build/reports/ build/test-results/ build/logs/ 2>/dev/null | grep -v '^#' | wc -l  # expect 0

# OpenAPI codegen
pnpm --filter @zeromail/admin generate-api  # admin-schema.d.ts should include /api/admin/master-keys/* paths
```

</verification>

<success_criteria>
- [ ] Liquibase 051 deploys `llm_provider_master_key` with 6-value provider CHECK + feature_default_provider_* partial unique indexes + OPENROUTER seed row defaults
- [ ] PlatformSecretCipher binds provider AAD; row-swap fails GCM verification
- [ ] MasterKeyAdminService set/test/rotate flows write audit rows with no plaintext
- [ ] MasterKeyEditSessionService Redis-backed 5-min token; MasterKeyRateLimiter 10/hr/admin
- [ ] ModelsProbeClient returns enum only; no body logging; constant timing jitter
- [ ] ProviderMasterKeyResolver is sole reader of llm_provider_master_key (ArchUnit green)
- [ ] ChatModelCacheEvictionListener evicts per-provider on MasterKeyRotatedEvent
- [ ] 9Router dual key_format toggle routes to OpenAI vs Anthropic Spring AI adapter
- [ ] AdminMasterKeyController endpoints honor edit-session + rate-limit + audit
- [ ] `<MaskedSecretField>` never persists plaintext beyond submission
- [ ] Playwright e2e green on master-keys flow
- [ ] MasterKeySentinelLeakTest green; sentinel scan over build artifacts returns 0 hits
- [ ] Feature-default selector flips provider per feature with audit + UNIQUE partial index enforcement
</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8B-SUMMARY.md` when done.
</output>
