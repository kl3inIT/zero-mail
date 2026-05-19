---
phase: 8
reviewers: [codex, opencode]
reviewed_at: 2026-05-19T15:32:36Z
plans_reviewed:
  - 8A-PLAN.md
  - 8B-PLAN.md
  - 8C-PLAN.md
  - 8D-PLAN.md
  - 8E-PLAN.md
  - 8F-PLAN.md
---

# Cross-AI Plan Review — Phase 8 (Admin Console & Operator Tooling)

Two independent AI reviewers (Codex and OpenCode) were asked to review Phase 8's
6 plans (8A foundation, 8B master keys, 8C tenant inspection, 8D catalog,
8E queue health, 8F spend dashboard) against PROJECT.md, the roadmap section,
the 42 phase-8 requirements, 08-CONTEXT.md, 08-SPEC.md, and 08-UI-SPEC.md.

The two reviews differ in posture: **Codex flags the phase as HIGH risk** ("approve
the phase structure, not the executable plans as-is") with multiple production-blocking
contradictions to resolve before execution. **OpenCode flags the phase as MEDIUM risk**
("none of the concerns are blockers for planning, but items tagged HIGH in individual
plans must be resolved before the corresponding task is coded"). Codex's review is
more adversarial and catches contradictions OpenCode missed (sentinel-vs-mask conflict,
FK-NOT-NULL backfill, requeue semantics conflict, BYOK classification). OpenCode's
review catches several items Codex missed (heap-dump exposure, filter ordering vs
compression, body-ban scope vs ArchUnit forbidden regex consistency at runtime,
processing_job.status enum extension).

---

## Codex Review

**Docs Checked**
I verified the highest-risk library assumptions with Context7: Spring Security 7 WebAuthn/securityMatcher docs, Spring Modulith event semantics, and `@simplewebauthn/browser`. Key implication: `.webAuthn(...)` and `securityMatcher(...)` are valid directions, `startRegistration({ optionsJSON })` / `startAuthentication({ optionsJSON })` matches the frontend plan, and `@ApplicationModuleListener` is after-commit but asynchronous, so cache eviction cannot be assumed complete when an HTTP rotation response returns.

**Whole Phase**
Summary: The phase is well decomposed and unusually rigorous on privacy/audit gates, but it is still a very high-risk phase because it combines admin auth, infra migration, cryptographic secret handling, catalog mutation, tenant destructive actions, and a new SPA. The plan quality is strong, but several correctness conflicts need resolution before execution.

Strengths:
- Clear 8A foundation gate before feature plans.
- Strong defense-in-depth posture: chain isolation, `@PreAuthorize`, ArchUnit, body-ban filter, audit rows, sentinel tests.
- UI contract is specific enough to avoid vague implementation.
- Privacy boundaries are consistently repeated across backend, frontend, and tests.

Concerns:
- **HIGH:** WebAuthn endpoint/routing assumptions are not yet stable against Spring Security's stock passkey endpoint shapes.
- **HIGH:** Several plans assume asynchronous Modulith listeners give synchronous cache-safety after key/catalog changes.
- **HIGH:** Cross-plan file ownership conflicts are likely in `apps/admin` routing/nav, ArchUnit tests, `SecurityConfig`, and shared DTO/test gates.
- **HIGH:** Scope is too large for "autonomous" execution on 8D/8E/8F without more integration checkpoints.
- **MEDIUM:** Some acceptance criteria are impossible or brittle: H2 Liquibase for Postgres triggers/grants, "superuser cannot mutate," bundle size checks, grep-based import checks.

Suggestions:
- Add a pre-execution "interface freeze" doc for WebAuthn paths, session cookie behavior, admin API routes, and shared `apps/admin` route registry.
- Add integration checkpoints after 8A, 8B, and 8C before allowing 8D/8E/8F.
- Convert Postgres-specific DB verification to Testcontainers/Postgres, not H2.
- Treat cache eviction as versioned/synchronous at the request boundary, not only event-driven.

Risk Assessment: **HIGH**. The design is strong, but auth/session, audit-chain, master-key, and destructive tenant paths contain production-blocking ambiguities.

**8A Foundation**
Summary: 8A has the right foundation scope, but it currently carries the most dangerous unresolved assumptions: WebAuthn endpoint wiring, SPA/backend `/enroll` routing, session cookie isolation, and audit-chain correctness.

Strengths:
- Correctly front-loads audit, RBAC, OpenAPI split, ArchUnit gates, and admin SPA scaffold.
- Good focus on explicit `@PreAuthorize` and chain-level 401 behavior.
- Strong runbook and manual WebAuthn checkpoint.

Concerns:
- **HIGH:** `/enroll` is both a SPA route and a backend-filtered route. NPM cannot serve both without an explicit API split.
- **HIGH:** Spring Security passkey endpoint names in the docs do not obviously match `/login/webauthn/options` and `/webauthn/register/options`.
- **HIGH:** HMAC chain ordering by `created_at` with UUID IDs is not deterministic under concurrency; DB-generated `created_at` also makes pre-insert hashing awkward.
- **HIGH:** Per-chain Spring Session cookie isolation is unresolved; a single JVM typically has one cookie serializer unless custom host-aware handling is implemented.
- **MEDIUM:** "Liquibase seed from config" is conceptually wrong; dynamic bootstrap emails belong in the runner.
- **MEDIUM:** Bootstrap idempotency conflicts with the spec: PENDING rows should get a fresh startup enrollment URL, but the plan says second boot prints none.

Suggestions:
- Use `/api/admin/enrollment/session` for token validation and keep `/enroll` purely SPA.
- Add `admin_audit_event.chain_index BIGSERIAL` or monotonic sequence and hash app-chosen canonical timestamps.
- Replace H2 Liquibase verification with Postgres Testcontainers for triggers, grants, `INET`, `BYTEA`, JSONB.
- Define dev WebAuthn RP config separately for localhost/admin domain testing.

Risk Assessment: **HIGH**. Do not execute until WebAuthn pathing, cookie strategy, and audit-chain mechanics are corrected.

**8B Master Keys**
Summary: Good security intent, but the schema and sentinel policy conflict with requirements and could block implementation.

Strengths:
- Strong single-resolver boundary for key access.
- Good edit-session and rate-limit concept.
- Provider AAD for AES-GCM is the right row-swap defense.
- Rotation failure preserves old key.

Concerns:
- **HIGH:** `llm_provider_master_key.encrypted_key NOT NULL` conflicts with seeded/default provider rows before keys exist.
- **HIGH:** `feature_default_provider_*` on the master-key table conflates provider defaults with secret storage; partial unique indexes as described do not enforce one default globally.
- **HIGH:** `MasterKeySentinelLeakTest` banning `sk-` conflicts with required masked display/audit values like `sk-****abc1`.
- **HIGH:** Rotation response may return before async cache eviction finishes.
- **MEDIUM:** `test-connection` is still an oracle unless rate-limited directly, not only via edit-session mint.
- **MEDIUM:** 9Router/Google `KeyFormat` mapping is not fully resolved.

Suggestions:
- Move feature defaults to catalog/provider binding or a dedicated `feature_default_provider` table.
- Reconcile sentinel policy: ban raw/full key patterns, allow exact masked forms only where explicitly permitted.
- Use versioned key/cache entries or synchronous after-commit eviction before returning rotation success.
- Add direct test-connection rate limiting and audit throttling.

Risk Assessment: **HIGH**. Secret handling is close, but schema and policy contradictions are production-blocking.

**8C Tenant Inspection**
Summary: Privacy goals are strong, but destructive tenant operations and the response filter need sharper correctness guarantees.

Strengths:
- `AdminTenantAccess.readOnly` is a solid audit-before-read pattern.
- Metadata-only projections are well scoped.
- OAuth revocation gateway avoids exposing token bytes to admin callers.
- Body-ban filter is a useful runtime failsafe.

Concerns:
- **HIGH:** `appendAsSystem` with `actor_user_id=ZERO_UUID` will violate the `admin_users` FK unless a system actor exists or FK is nullable.
- **HIGH:** Manual cascade deletion can miss future tenant-owned tables and cause irreversible partial deletion.
- **HIGH:** Body-ban key matching is too narrow if it checks exact names instead of substring/regex.
- **MEDIUM:** OPS-TENANT-04 requires Spring Data JDBC repository-style projections; the plan leans toward service/JdbcTemplate.
- **MEDIUM:** External OAuth revocation inside a DB transaction risks inconsistent rollback semantics.

Suggestions:
- Seed a real system admin actor or allow nullable/system actor fields for system audit events.
- Implement tenant deletion through an explicit deletion registry or database FK cascade audit, not hand-maintained table lists.
- Make body-ban scanning use the same forbidden regex semantics as ArchUnit.
- Separate OAuth revoke into staged workflow or record compensating state if external revoke succeeds and DB rollback happens.

Risk Assessment: **HIGH** because tenant deletion and privacy-filter behavior can violate core trust invariants.

**8D Catalog**
Summary: The catalog plan is thoughtfully staged and mostly complete, but schema integrity and provider sync assumptions need tightening.

Strengths:
- 3-step Fetch/Diff/Confirm directly addresses auto-apply risk.
- Anthropic manual-only handling is correctly explicit.
- Public `/api/settings/catalog` split from admin DTOs is good.
- CatalogChangedEvent integration is the right architectural hook.

Concerns:
- **HIGH:** Adding FKs to existing `assistant_settings.*_model_id` can fail or break existing tenants without a verified backfill plan.
- **MEDIUM:** `feature_binding.provider` can diverge from `model_catalog.provider` unless enforced with composite constraints.
- **MEDIUM:** Reusing 8B `ModelsProbeClient` is underspecified; test-connection enum probing is not the same as full model-list fetch.
- **MEDIUM:** Async catalog cache eviction has the same stale-cache issue as master-key rotation.
- **LOW:** Hardcoded Anthropic seed names should be reverified immediately before merge.

Suggestions:
- Add a pre-migration data audit and explicit backfill changelog for existing settings.
- Use composite FK or remove duplicated provider from `feature_binding`.
- Split `ModelsProbeClient` into `probeConnection` and `fetchModelCatalog`.
- Store catalog version and include it in ChatModel cache keys.

Risk Assessment: **MEDIUM-HIGH**. The product flow is strong, but migration and cache consistency need fixes.

**8E Queue Health**
Summary: This is the lowest-risk backend plan and well scoped, but one requirements conflict must be resolved.

Strengths:
- Aggregate-only queries protect `payload_json`.
- Requeue endpoint accepts only `jobId + reason`.
- Shared `KpiCard` and `AutoRefreshIndicator` are justified.
- 10s refresh with pause/background handling is appropriate.

Concerns:
- **HIGH:** Requirements conflict on requeue semantics: one source says increment retry counter, the plan resets attempts to 0.
- **MEDIUM:** `last_failure_reason` may contain sensitive exception text unless sanitized/truncated at write time.
- **MEDIUM:** Grep-only "no payload" verification is weaker than SQL/DTO contract tests.
- **LOW:** Trend chart stub may disappoint relative to UI prototype, though not core acceptance.

Suggestions:
- Decide requeue semantics: reset worker attempts but increment a separate `admin_requeue_count`.
- Ensure workers store failure reason as enum/sanitized short code only.
- Add tests that assert SQL select lists exclude `payload_json`.

Risk Assessment: **MEDIUM**. Mostly safe once retry semantics and failure-reason sanitization are clarified.

**8F Spend Dashboard**
Summary: Useful and well bounded, but the platform-vs-BYOK classification is likely wrong and would corrupt the dashboard.

Strengths:
- Aggregate-only approach aligns with privacy constraints.
- 90-day cap is a good performance guard.
- ArchUnit prompt/completion accessor ban is appropriate.
- CSV export stays aggregate-only.

Concerns:
- **HIGH:** Splitting platform-vs-BYOK by tenant-level BYOK presence is incorrect; classification must be row-level from `llm_call_audit`.
- **MEDIUM:** Plan omits a formal dependency on 8E despite reusing `KpiCard` and `AutoRefreshIndicator`.
- **MEDIUM:** HMAC tenant hashes reduce operational usefulness and may conflict with "top-20 tenants" expectations.
- **MEDIUM:** 60s auto-refresh could create noisy `admin_read_event` rows unless range-read debounce is explicit.
- **LOW:** CSV "max 10k rows" needs an estimate query, not post-stream failure.

Suggestions:
- Use an explicit `credential_source` / `billing_source` column from `llm_call_audit`; add migration if missing.
- Make 8F depend on 8E or move shared components to 8A.
- Show active tenant email/ID where allowed, reserve `[deleted]`/rollups for deleted tenants per OPS-SPEND-02.
- Debounce read-audit rows per range and session.

Risk Assessment: **MEDIUM-HIGH** due to spend misclassification risk.

**Bottom Line**
Approve the phase structure, not the executable plans as-is. Required blockers before execution: fix 8A WebAuthn/session/enrollment routing, redesign audit-chain ordering, resolve 8B master-key/default-provider schema and sentinel-mask contradiction, define synchronous/versioned cache invalidation, and correct 8F BYOK/platform attribution.

---

## OpenCode Review

# Cross-AI Plan Review: Phase 8 — Admin Console & Operator Tooling

---

## Overall Phase Assessment

| Dimension | Score |
|-----------|-------|
| Completeness | HIGH (42 reqs mapped, 0 gaps identified) |
| Security posture | HIGH (multiple defense layers per invariant) |
| Dependency ordering | HIGH (8A hard gate sequenced correctly) |
| Concision | MEDIUM (some tasks over-specified) |

**Overall Risk: MEDIUM** — The scope is large (6 plans, ~170+ files) but well-structured. The two HIGH concerns (WebAuthn DSL API uncertainty, HMAC chain verification job scope clarity) are manageable with pre-coding Context7 research and tighter task definitions.

---

## 8A — Foundation

### Summary
Solid foundation across 7 tasks covering the hardest parts: Liquibase, Spring Security 7 WebAuthn chain, append-only audit, bootstrap ceremony, ArchUnit gates, and the full `apps/admin` Vite SPA. 22 files planned.

### Strengths
- **Append-only audit defense is layered**: DB trigger (SQLSTATE 23514) + REVOKE UPDATE/DELETE on `admin_audit_event` + HMAC-SHA256 chain + nightly verify job. No single layer is relied upon.
- **Dual `SecurityFilterChain` separation is correct**: `@Order(1) adminChain` with `securityMatcher` + `.webAuthn()` vs `@Order(2)` catch-all with `.oauth2Login()`. ArchUnit `admin_chain_does_not_use_oauth2login` is the right enforcement.
- **Bootstrap enrollment token is never persisted**: `System.out.println(...)` bypasses SLF4J entirely; in-memory `ConcurrentHashMap` with 10-min TTL + one-time consumption. Correctly avoids log file leakage.
- **AdminContext/TenantContext mutex is the right belt-and-suspenders**: codepath-level on top of chain isolation.
- **Task 8A-01 (Wave 0) wisely uses `allowEmptyShould(true)`** so ArchUnit tests compile before admin packages exist — prevents early-bird CI failures.

### Concerns
- **HIGH: WebAuthn `.webAuthn(...)` DSL API surface may not match plan assumptions**. The plan references `WebAuthnRelyingPartyOperations`, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`, and `@simplewebauthn/browser v11` — but mandates Context7 lookup before coding. If the actual DSL differs (e.g., Spring Security 7.0.5 has different builder patterns than expected), the entire 8A-04 SecurityConfig task may need rework mid-execution. The plan acknowledges this but doesn't provide a contingency pattern.
- **MEDIUM: Task 8A-01's `AdminChainNoOauth2LoginTest` performs source-code parsing of `SecurityConfig` to detect missing `.oauth2Login()` / `.webAuthn()` calls**. Java source analysis via ArchUnit's `JavaClass.getMethodCallsFromSelf()` is fragile; a refactor that extracts the chain builder to a private method would make the rule vacuously green while allowing cross-contamination. Consider a stronger integration test instead: assert that a request with a valid user-session cookie to `/api/admin/*` returns 401.
- **MEDIUM: `apps/admin` pnpm workspace addition requires Turborepo pipeline configuration**. The plan mentions `turbo.json` + `pnpm-workspace.yaml` updates but doesn't specify the `outputs` for `@zeromail/admin#build` (e.g., `outputs: ['dist/**']`). Missing this causes downstream cache misses.
- **LOW: Task 8A-07 runbook references "AWS CLI" backup pattern but VPS is single-host Linux on an unknown cloud — `aws s3 cp` may not be installed**. Replace with `rsync` or `scp` as the default, with AWS S3 as an optional variant.

### Suggestions
1. **Add `@Tag("webauthn-smoke")` to a lightweight integration test** that verifies the WebAuthn RP config parses without throwing at startup (even if no browser is available). Catches config-typo regressions early.
2. **Replace the ArchUnit source-parsing test (AdminChainNoOauth2LoginTest) with a stronger integration test** using `MockMvc` against an in-memory security config that confirms the admin chain rejects user cookies. Keep the ArchUnit test as a lightweight complement but acknowledge its limitation in Javadoc.
3. **Add explicit Turborepo outputs in `turbo.json` for `@zeromail/admin#build`**: `"outputs": ["dist/**"]`.
4. **Make the runbook backup section platform-agnostic**: default to `tar | gpg | rsync`, mention `aws s3 cp` as optional.

---

## 8B — Master Keys

### Summary
Strong, security-conscious plan for the highest-blast-radius subsystem. Every key operation is guarded by edit-session tokens, rate limits, same-transaction audit, and the sentinel-leak CI gate.

### Strengths
- **`PlatformSecretCipher` AAD binding provider name** — `"platform:master_key:" + provider.id()` ensures that even if two key rows swap, GCM tag verification fails. This is excellent defense-in-depth.
- **`MasterKeySentinelLeakTest` scans multiple artifact types**: logs, test outputs, audit JSON, YAML, and base64-encoded forms. Scanning base64-encoded sentinels is a subtle and important inclusion.
- **Edit-session token + rate-limit + `@NoSentinelLeak` on `reason`** — three independent controls before any master-key mutation.
- **ModelsProbeClient with 50ms constant jitter** — prevents timing-based oracle attacks even though the plan enumerates 5 enum return values. Good practical touch.
- **`ChatModelCacheEvictionListener` uses AFTER_COMMIT semantics** — prevents cache eviction on rolled-back rotations.

### Concerns
- **HIGH: `ProviderMasterKeyResolver` caches decrypted plaintext in-memory with 60-min TTL**. If the JVM process is compromised (e.g., heap dump via `/actuator/heapdump` or JMX), all 6 provider keys leak. The plan doesn't mention any protections: no `maxAge` refresh-on-read, no mlock (JVM doesn't support), no `char[]` clearing. For v1.2 single-VPS this may be acceptable ("once you have heap dump access you've already lost"), but it should be documented.
- **MEDIUM: `feature_default_provider_*` columns live on `llm_provider_master_key` rather than a separate normalization**. This couples the provider-credential table to the feature-default routing table. When 8D lands and the catalog has its own `feature_binding` table, there will be two sources of truth for "which provider is the default for chat." The plan acknowledges this as a "stub" but risks update conflicts.
- **MEDIUM: Master-key audit row `after_state_json` schema is "enumerated" but enforcement is via MasterKeySentinelLeakTest scanning for `sk-` substrings**. A rogue field like `encrypted_key: "AABBCCDD"` (hex encoding of a valid key — no `sk-` prefix) would pass the sentinel test while leaking key material. Consider also scanning for hex-encoded byte arrays > 16 bytes with entropy > 7 bits/byte (heuristic).
- **LOW: Rate-limiter uses `epoch_hour` discriminator — 11 requests at 11:59:59 and 11 more at 12:00:00 both succeed**. Using a sliding-window Redis Sorted Set or `cl.throttle` (if Redis Stack available) would be more precise. Not critical for v1.2 scale.

### Suggestions
1. **Document the heap-dump risk in `docs/ops/v1.2-deploy.md` §Security Considerations**: "Master-key plaintext lives in JVM heap. Disable `/actuator/heapdump` in production, restrict JMX to loopback, and run `api` container with `--memory-swap` limits to prevent swap-based extraction."
2. **Consider merging `feature_default_provider_*` into 8D's `feature_binding` table** before 8D deletes/renames these columns. Alternatively, add a Liquibase changeset in 8D that migrates the defaults to `feature_binding` and drops the columns from `llm_provider_master_key`.
3. **Extend `MasterKeySentinelLeakTest` to also scan for hex-encoded byte arrays** matching `^[0-9a-f]{32,}$` (32+ hex chars = 16+ bytes) within audit `after_state_json` values. Use a statistical entropy heuristic to reduce false positives.

---

## 8C — Tenant Inspection

### Summary
Privacy-first plan that correctly layers projection design, ArchUnit gates, audit-before-read, and a response-body failsafe filter. The most privacy-sensitive plan in the phase.

### Strengths
- **`AdminTenantAccess.readOnly` writes `admin_read_event` BEFORE binding TenantContext** — not after. This prevents un-audited reads even if the supplier throws.
- **`TenantOAuthRevocationGateway` is the sole admin bridge to OAuth token services**, and it takes only `tenantId` — never exposes token bytes. ArchUnit whitelist by FQN is correctly narrow.
- **`AdminResponseBodyBanFilter` uses Jackson 3 streaming parser** rather than `ObjectMapper.readTree()`. The former doesn't buffer the full tree and is resistant to OOM on large responses.
- **Projection records have zero fields matching the forbidden regex** — verified in the plan by explicit grep statement.
- **Tab-level `admin_read_event` granularity** (1 per tab visit, max 5 per session) provides useful audit granularity without excessive overhead.

### Concerns
- **HIGH: `AdminResponseBodyBanFilter.appendAsSystem()` insert path creates a dedicated system-audit bypass**. The plan acknowledges the problem (AdminContext may be out of scope when the filter runs after chain) and adds a `ZERO_UUID` / `"<system>"` actor. This is acceptable for the body-ban safety valve, but must be documented as the ONLY exception to the "every audit write requires AdminContext" rule. An ArchUnit rule should enforce that `appendAsSystem` is only callable from `AdminResponseBodyBanFilter`.
- **MEDIUM: Body-ban filter runs after controller serialization but BEFORE response compression**. If a compression filter wraps the response before the body-ban filter reads it, `ContentCachingResponseWrapper` won't work. Verify filter ordering in `SecurityConfig` — the ban filter must run before any `GzipFilter` or compression interceptor.
- **MEDIUM: Tenant deletion cascade order is list-documented but not enforced in the task behavior**. The `TenantDeletionService.delete()` method's cascade order (byok_credential → chat_message → ... → tenants row) is critical for FK safety. If any step is missed or out of order, the transaction will fail at the first FK violation. The cascade order should be explicitly authored as a list comprehension, not implicitly correct.
- **LOW: `cloudTenantList` endpoint writes a single `admin_read_event` with `target_kind=TENANT_LIST` but no `target_id`**. This is permissive — every tenant list view writes one row regardless of how many tenant IDs are scanned. Fine for v1.2, but consider a note about v1.3+ granularity if per-tenant-read logging requirements tighten.

### Suggestions
1. **Add an ArchUnit rule `OnlyBodyBanFilterCanCallAppendAsSystem`** that enforces the singleton exception: `noClasses().that().areNotAssignableTo(AdminResponseBodyBanFilter.class).should().callMethod(AdminAuditWriter.class, "appendAsSystem", ...)`.
2. **Document the filter-ordering constraint** in `AdminResponseBodyBanFilter.java` Javadoc: "This filter MUST run before any response compression/wrapping filter. See `SecurityConfig` ordering."
3. **Make the deletion cascade explicit in code**: compute the list of table names in order and iterate, or use a unit test that introspects FK dependencies from `information_schema` and asserts the cascade list covers them in valid order.

---

## 8D — Catalog

### Summary
Well-structured catalog plan with the correct 3-table normalized schema. The Sync state machine (Fetch → Diff → Confirm) is properly designed with SKIP LOCKED queuing and Redis debounce. Anthropic seed approach is pragmatic.

### Strengths
- **3-table normalized (`provider_catalog`, `model_catalog`, `feature_binding`) with FKs and partial unique indexes** — avoids the JSONB pitfalls and ensures referential integrity with `assistant_settings`.
- **Sync auto-apply is forbidden by design** — `CatalogSyncOrchestrator` has separate `fetch`, `diff`, `confirm` methods; no path auto-applies.
- **ModelSchemaValidator validates at BOTH fetch and confirm steps** (defense in depth for supply-chain attacks from provider `/models` endpoints).
- **Configurable Anthropic seed via Liquibase `<insert>`** — supports `liquibase rollback` without requiring custom SQL.
- **`CatalogChangedEvent` evicts by `affectedModelIds`**, not `provider` — finer-grained than `MasterKeyRotatedEvent`, reducing unnecessary cache churn.

### Concerns
- **HIGH: `processing_job.status` enum may not have `AWAITING_CONFIRM`, `CONFIRMED`, `CANCELLED` values**. The plan acknowledges this as "TBD — existing status enum may need extension." If the status column is a fixed CHECK constraint, altering it requires a multi-step Liquibase changeset (drop CHECK, add new values, re-add CHECK). This is doable but risky in-flight if any worker is concurrently reading rows. The fallback plan (payload-based step tracking with status=COMPLETED) is safer but less discoverable. **Lock this decision now**: recommend payload-based step discriminator within the existing `processing_job.status` + `processing_job.payload_json->>'step'` pattern, avoiding the need to alter the status constraint.
- **MEDIUM: No discussion of stale SKIP LOCKED jobs**. If a Sync worker crashes mid-fetch, the `processing_job` row stays stuck in `PENDING` with a 60s Redis lease. The queue consumer should have a LOCK_TIMEOUT handler that marks such rows as `FAILED` after the 60s lease expires (using `locked_until < NOW()` in the SKIP LOCKED claim query). The plan doesn't mention this.
- **MEDIUM: `CatalogSyncOrchestrator.confirm` validates `actor matches initiating admin`**. This is specified but the processing_job payload only stores `{provider, actorId, jobId, step}`. The match check requires reading `actorId` from `payload_json` and comparing to `AdminContext.currentOrThrow()`. If the initiating admin's session has expired and a new admin picks up the job, confirm will fail. This may be intentional (only the initiator can confirm) or a UX PITA. Consider making confirm work for any ADMIN user to avoid session-expiry frustration.
- **LOW: `CuratedCatalogQueryService` Redis ETag cache is described but not detailed**: cache key, TTL, invalidation trigger, and concurrent-update handling (two simultaneous CatalogChangedEvents). A short TTL (30s) with stale-while-revalidate would simplify.

### Suggestions
1. **Lock the `processing_job.status` decision**: use payload-based step tracking (`step: 'FETCHING'|'DIFF_READY'|'CONFIRMED'|'CANCELLED'` in `payload_json`), avoid altering the status CHECK constraint.
2. **Add `locked_until < NOW()` guard to the SKIP LOCKED claim** and a max-duration check: if `age(locked_at) > interval '5 minutes'`, treat as abandoned and reschedule.
3. **Relax the admin-matching rule in `CatalogSyncOrchestrator.confirm`**: any ACTIVE admin should be able to confirm a Sync, not just the initiator. Document this decision in the code.
4. **Specify the Redis ETag cache parameters**: key = `catalog:etag:sha256`, TTL = 30s, invalidation on `CatalogChangedEvent` via `CacheEvict`.

---

## 8E — Queue Health

### Summary
Focused, minimal-scope plan that correctly avoids scope creep. The payload-exclusion design (DeadLetterRow has no payloadJson) and audit-before-requeue patterns correctly follow the privacy-first approach.

### Strengths
- **DeadLetterRow DTO explicitly excludes `payloadJson`** — contract-level enforcement. SQL never selects the column.
- **Re-queue audit row excludes payload** — `before_state_json` contains only `{jobId, jobType, attemptsBeforeRequeue, lastFailureReason}`, never `payloadJson`.
- **`KpiCard` and `AutoRefreshIndicator` justified by 3+ call sites** — correctly follows the project's component-composition convention.
- **10s auto-refresh pauses on `document.hidden`** — respects user's bandwidth/attention.
- **Re-queue idempotent** — returns 0 rows affected on second call, no duplicate audit.

### Concerns
- **MEDIUM: "Failure rate" and "oldest-unleased age" are computed over the full `processing_job` table** without any 24h/7d time window on the denominator. This means "failure rate" is a lifetime rate that will asymptotically approach 0% as the system accumulates successful jobs. The plan correctly specifies `WHERE last_failed_at >= NOW() - INTERVAL '24h'` for the numerator, but the denominator should also be gated to `created_at >= NOW() - INTERVAL '24h'`. Otherwise a flood of old successes renders the 24h failure rate meaningless.
- **LOW: No per-job-type breakdown on failure rate**. `QueueHealthSnapshot.retryHistogram` is aggregated across all job types. A stuck `SEND_EMAIL` job and a healthy `CATALOG_SYNC` worker will share the same histogram. If this matters, add per-job-type to the bucket.
- **LOW: Trend chart stub described as "replaced with Skeleton placeholder"** — acceptable for v1.2 but the placeholder should clearly indicate "Coming in v1.3: time-series trend" rather than a broken-looking component.

### Suggestions
1. **Fix failure rate denominator to 24h**: `COUNT(*) FILTER (WHERE status='FAILED' AND last_failed_at >= NOW() - INTERVAL '24h') * 1.0 / NULLIF(COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '24h'), 0)`.
2. **Add a per-type option** to `QueueHealthQueryService` for the retry histogram if the task is trivially implementable within 8E scope.

---

## 8F — Spend Dashboard

### Summary
Correctly scoped metadata-only plan. The k-anonymity enforcement, 90-day range cap, and ArchUnit prompt-accessor ban form the three-layer defense. Reuses `KpiCard` and `AutoRefreshIndicator` from 8E.

### Strengths
- **k-anonymity correctly applied at TWO levels**: small tenant buckets (k<5) return aggregated rollup, and deleted-tenant rows are collapsed. This prevents re-identification.
- **`AdminSpendPromptAccessorBanTest` uses custom `ArchCondition` with `getMethodCallsFromSelf()`** — inspects actual bytecode call sites, not just imports. More precise than package-level bans.
- **SQL query spy in test** verifies no `prompt`/`completion` token appears in the actual query string. This is a strong guarantee beyond ArchUnit.
- **Spend CSV enumerates only 6 columns**: `bucketDate, provider, feature, platformCost, byokCost, callCount`. No identifier columns (no tenantId, no adminId).
- **60s auto-refresh (vs 10s for queue)** — appropriately slower for the more expensive aggregate query.

### Concerns
- **MEDIUM: `tenantLabelHash` uses HMAC-SHA256 with the same KEK as `admin_audit_event`**. Two concerns: (a) share of KEK between audit and display reduces key rotation independence; (b) the hash is deterministic per tenantId — an admin could correlate tenant activity across `/spend`, `/tenants`, and `/activity` by matching the 8-char prefix. This is a feature for admin usability but a privacy leak if a display is shared. Document explicitly that this is intentional and the hash is not reversible.
- **LOW: k-anonymity footer note says "k≥5" but code uses hardcoded threshold**. If a future policy changes this to k≥10, it requires a code change. Consider `application.yml` property `zeromail.admin.spend.k-anonymity-threshold: 5` with a descriptive comment.

### Suggestions
1. **Document the `tenantLabelHash` design choice** in Javadoc: "Stable HMAC-SHA256 prefix for cross-screen admin correlation. Not reversible. If screenshare/presentation mode is needed in v1.3+, add a salt that changes per-session."
2. **Make `kAnonymityThreshold` configurable** via `ZeroMailCoreProperties` with a sensible default of 5, even if not exposed in UI.
3. **Add a query timeout guard** for the spend aggregate query: `.queryTimeout(15, TimeUnit.SECONDS)` on the `JdbcTemplate`. The `llm_call_audit` table could grow large over months.

---

## Cross-Cutting Concerns

### Strengths Across Plans

1. **Privacy architecture is layered consistently** across all 6 plans: field-name ArchUnit → projection/DTO design → response-body filter → sentinel-leak CI gate. Every plan references each layer.

2. **Same-transaction audit coupling** is correctly implemented everywhere: audit row insert happens inside the caller's `@Transactional`, meaning rollback removes both the state mutation and the audit trail. No dangling audit rows.

3. **`AdminAuditWriter` is the single audit entry point** — no raw INSERT bypasses it (except the body-ban filter's `appendAsSystem`, which is a documented exception).

4. **`@NoSentinelLeak` validator is applied consistently** across all 8B/8C/8D/8E request DTOs that carry a `reason` field.

5. **GroupedOpenApi split** is handled once in 8A-04 and correctly referenced by 8D-03 for the catalog endpoints.

### Concerns

1. **HIGH: No plan addresses the `AdminContext.TENANT.isBound()` check's thread-safety**. `ScopedValue` is bound per-virtual-thread (per-request), not globally. The mutex = both bindings checking each other IS correct for virtual threads (they're request-scoped). BUT: if the admin filter binds `AdminContext` AND the same request later enters a block in `TenantInspectionService` that calls `AdminTenantAccess.readOnly` which in turn binds `TenantContext` — `TenantContext.currentOrThrow()` will now succeed (because it was just bound by `readOnly`), and `AdminContext.currentOrThrow()` will also succeed (because the outer filter still has it bound). The mutex is only checked at entry time, not at the moment of the cross-scope read. This is fine — the intent is "you can't be in admin scope and read TenantContext unless you explicitly enter TenantContext through readOnly." But the plan's wording "mutex enforced" oversimplifies and may confuse an executor.

2. **MEDIUM: No cross-plan ArchUnit integration test** ensures that admin packages from 8B (mkey), 8C (tenant), 8D (cat), 8E (queue), 8F (spend) all pass the shared ArchUnit gates (AdminPathBodyBanTest, AdminContextMutexTest, AdminSendBanTest). Each plan tests its own package, but the shared tests must be run with all admin packages on the classpath. Ensure there's a Phase-8-scoped test task that runs the shared tests against the complete admin module.

3. **MEDIUM: `AdminBootstrapRunner` prints enrollment URL to STDOUT**, which works for `docker compose up` but NOT for `docker compose run api` (detached mode). In detached mode, STDOUT goes to the container log, which is captured by Docker's logging driver and potentially persisted to a log aggregator. The runbook (`docs/ops/v1.2-deploy.md`) must warn the operator: "Run `docker compose up api` in interactive mode for the first admin bootstrap; after the URL is captured, `Ctrl+C` and restart in detached mode." Otherwise the token lands in Docker log files, violating the "never in log file" invariant.

4. **LOW: Liquibase changelog numbering collision risk**. 8A uses 048/049/050, 8B uses 051, 8D uses 052/053. If 8A-01 and 8B-01 are developed in parallel (they are, since 8B wave 2 starts after 8A wave 0), there's a merge conflict on `db.changelog-master.yaml`. The 048-051 range is contiguous between plans. This requires wave ordering: 8A-01 (W0, 048-050) → 8B-01 (W2, 051) → 8D-01 (W3, 052-053). The plan claims 8B depends on 8A, so this should be fine, but if any parallelization happens these numbers collide. Recommend offsetting by 10s to avoid: 8A=048-050, 8B=058, 8D=068-069. Or just rely on the dependency ordering in `depends_on`.

### Suggestions

1. **Add a Phase-8 module-level test class** `Phase8ArchTestSuite` that runs all shared ArchUnit gates (`AdminPathBodyBanTest`, `AdminContextMutexTest`, `AdminSendBanTest`) against the full `core.admin` module classpath.

2. **Update the runbook's bootstrap section**: add a warning about detached Docker mode and instructions for interactive capture.

3. **Offset Liquibase changelog numbers** between plans: 8A=048-050, 8B=058, 8D=068-069 to avoid merge collisions even if parallelization happens.

4. **Add a `@Tag("integration")` test suite** that covers the happy path end-to-end: bootstrap → enrollment → login → grant admin → set master key → catalog sync → view tenant detail → re-queue dead letter → view spend. This integration test catches regressions across plan boundaries.

---

## Summary Table

| Plan | Risk Level | Key Strength | Key Concern |
|------|-----------|--------------|-------------|
| 8A | MEDIUM | Append-only audit + dual chain + bootstrap security | WebAuthn DSL surface uncertainty; source-parsing ArchUnit test fragility |
| 8B | MEDIUM | PlatformSecretCipher AAD + sentinel-leak scan + edit-session + rate-limit | Heap-dump master-key leak unaddressed; dual-source-of-truth for feature defaults |
| 8C | MEDIUM | AdminTenantAccess audit-before-read + body-ban fail-safe filter | appendAsSystem bypass is an exception without ArchUnit enforcement |
| 8D | MEDIUM | 3-step Sync with double-validation + per-model eviction | processing_job.status enum extension risk; no stale-job timeout |
| 8E | LOW | Payload exclusion at DTO contract + re-queue idempotent | Failure rate denominator unbounded (lifetime rate, not 24h) |
| 8F | LOW | k-anonymity at 2 levels + query-spy verification | tenantLabelHash KEK collision risk with audit KEK |

**Final Assessment**: MEDIUM risk — well-architected with appropriate defense layers, but the WebAuthn API surface uncertainty and heap-dump master-key exposure warrant attention before execution. None of the concerns are blockers for planning, but items tagged HIGH in individual plans must be resolved before the corresponding task is coded.

---

## Consensus Summary

### Agreed Strengths (raised by both reviewers)

- **Dependency ordering is correct**: 8A foundation gates 8B/8C/8D/8E/8F.
- **Defense in depth across privacy invariants**: dual `SecurityFilterChain` + `@PreAuthorize` + ArchUnit (`AdminPathBodyBanTest`, `admin_chain_does_not_use_oauth2login`, every-admin-controller-must-have-preauthorize) + `AdminResponseBodyBanFilter` runtime fail-safe + `MasterKeySentinelLeakTest` CI gate.
- **Append-only audit is layered**: HMAC-SHA256 chain + DB trigger + REVOKE UPDATE/DELETE + nightly verify job; no single layer is load-bearing.
- **`AdminTenantAccess.readOnly` audit-before-read pattern** correctly prevents un-audited tenant reads even on supplier exceptions.
- **`PlatformSecretCipher` AAD binds provider id** — correct row-swap defense.
- **Catalog Sync forbids auto-apply by design** with separate Fetch/Diff/Confirm methods.
- **8E and 8F payload exclusion at DTO contract** plus aggregate-only queries protect `payload_json` / per-prompt content.
- **`AdminContext` ↔ `TenantContext` mutex** is correctly layered as codepath-level defense in depth on top of chain isolation.

### Agreed HIGH Concerns (production-blocking; both reviewers flagged the same risk surface)

1. **WebAuthn DSL / Spring Security 7 API surface uncertainty (8A).** Both reviewers flag that the plan's references to `WebAuthnRelyingPartyOperations`, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`, and the endpoint shapes (`/login/webauthn/options`, `/webauthn/register/options`) may not match Spring Security 7.0.5's actual `.webAuthn(...)` DSL. Codex adds that the SPA-vs-backend `/enroll` route split is unresolved (NPM cannot serve both). Codex notes per-chain Spring Session cookie isolation is also unsolved. → **Lock interface freeze for WebAuthn paths and cookie strategy before 8A-04 coding starts.**

2. **Async cache eviction vs synchronous response (8B, 8D).** Codex (whole-phase HIGH + 8B-specific HIGH) and OpenCode (8B-specific note in concerns table) both flag that `@ApplicationModuleListener` after-commit semantics are asynchronous — the HTTP rotation/catalog-change response can return before every cached `ChatModel` is evicted, allowing stale-key reuse for tenants whose request lands in the gap. → **Move to versioned/synchronous after-commit cache invalidation at the request boundary, or include catalog/key version in `ChatModel` cache keys.**

### Divergent HIGH Concerns (raised by only one reviewer — investigate before dismissing)

Codex-only HIGHs:
- **8A audit-chain ordering is non-deterministic under concurrency** (UUID id + `created_at` ordering; pre-insert hashing awkward). → Add `chain_index BIGSERIAL` + app-chosen canonical timestamps.
- **8A bootstrap-emails belong in startup runner, not Liquibase seed** (dynamic data); idempotency conflict on PENDING rows.
- **8B `encrypted_key NOT NULL` conflicts with seeded provider rows pre-key.**
- **8B `feature_default_provider_*` on the master-key table is wrong location** + partial unique indexes do not enforce a single global default.
- **8B sentinel ban on `sk-` conflicts with required masked display `sk-****abc1`** in audit rows / responses. → Reconcile sentinel policy: ban raw key shapes, whitelist exact masked forms.
- **8C `appendAsSystem` with `actor_user_id=ZERO_UUID` will violate the `admin_users` FK** unless a seeded system actor exists or the FK is nullable.
- **8C manual cascade deletion list is hand-maintained** — future tenant-owned tables will be silently missed, causing irreversible partial deletion.
- **8C body-ban filter exact-name matching is narrower than ArchUnit forbidden regex** — substring/regex parity needed.
- **8D adding FKs to existing `assistant_settings.*_model_id`** can fail or break existing tenants without a verified backfill changelog.
- **8E requirements conflict on requeue semantics**: OPS-QUEUE-02 says "increments retry counter," plan says reset attempts to 0. → Decide: separate `admin_requeue_count`.
- **8F platform-vs-BYOK classification by tenant-level BYOK presence is incorrect** — must be row-level on `llm_call_audit.credential_source`.

OpenCode-only HIGHs:
- **8B `ProviderMasterKeyResolver` 60-min in-memory plaintext cache** is exposed to heap-dump / `/actuator/heapdump` / JMX leak — undocumented and unmitigated. → Disable heap-dump endpoint, restrict JMX to loopback, document threat in runbook.
- **8C `appendAsSystem` is the only audit-bypass exception** — needs an ArchUnit rule pinning the singleton call site to `AdminResponseBodyBanFilter` so future code cannot copy the pattern.
- **8D `processing_job.status` enum extension** for `AWAITING_CONFIRM`/`CONFIRMED`/`CANCELLED` requires risky in-flight Liquibase CHECK constraint surgery. → Use `payload_json->>'step'` discriminator and keep status enum unchanged.

### Cross-Cutting Items Both Reviewers Raised (Medium severity)

- **Bootstrap STDOUT vs detached Docker logs** (OpenCode) and **dev RP-config / localhost separation** (Codex): the runbook must instruct interactive `docker compose up api` for first bootstrap, then restart detached. Otherwise the one-time enrollment URL lands in container log files, violating the "never log file, never DB" invariant in ADMIN-03.
- **Liquibase changelog numbering collisions** (OpenCode) and **Cross-plan file ownership conflicts in `apps/admin`, `SecurityConfig`, ArchUnit tests** (Codex): both note that the parallel-after-8A wave structure (8B/8C/8E/8F simultaneous) creates merge-conflict risk on shared registries. → Offset Liquibase numbers by 10s; add an `apps/admin` route registry / nav registry; add interface freeze doc.

### Recommended Pre-Execution Actions

1. **Resolve all 4 whole-phase HIGHs from Codex before 8A-04 coding begins**: WebAuthn endpoint freeze, audit-chain ordering, cache eviction sync, scope/checkpoint review.
2. **Fix 8B schema and sentinel policy conflicts in-plan** (encrypted_key nullability, sentinel-vs-mask, feature defaults table location).
3. **Address OpenCode's heap-dump risk in the v1.2 deploy runbook** (disable `/actuator/heapdump`, restrict JMX, document threat model).
4. **Decide processing_job step tracking strategy now** (recommended: payload-based, not status-enum extension).
5. **Add an interface freeze doc** covering WebAuthn paths, session cookie behavior, admin API routes, `apps/admin` route/nav registry, and Liquibase numbering offsets — to be merged before any 8B/8C/8D/8E/8F task starts.
6. **Add integration checkpoints** after 8A, 8B, and 8C completion before allowing 8D/8E/8F to start.
7. **Resolve OPS-QUEUE-02 requeue semantics in REQUIREMENTS.md** (increment retry counter vs reset).
8. **Add a `credential_source` (or `billing_source`) column to `llm_call_audit`** if not present — required for 8F platform-vs-BYOK row-level classification.

### Net Recommendation

**Do not proceed to execute Phase 8 plans as-is.** Both reviewers agree the phase
architecture is sound and the defense-in-depth posture is unusually rigorous. The
plan structure can be approved; the executable plans require a tightening pass on
the items above. Suggested next step: `/gsd:plan-phase 8 --reviews` to incorporate
this feedback, then re-review for convergence before execution.

To incorporate feedback into planning:
  `/gsd:plan-phase 8 --reviews`
