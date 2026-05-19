---
phase: 8
cycle: 2
reviewers: [codex, opencode]
reviewed_at: 2026-05-19T16:01:12Z
plans_reviewed:
  - 8A-PLAN.md
  - 8B-PLAN.md
  - 8C-PLAN.md
  - 8D-PLAN.md
  - 8E-PLAN.md
  - 8F-PLAN.md
prior_cycle_high_count: 16
cycle2_unresolved_high_count: 7
---

# Cross-AI Plan Review — Phase 8 (Cycle 2)

## Context

Cycle 1 raised 16 HIGH-severity concerns (a few enumerated as 18 plan-level rows because two were split across plans). The 6 plans (8A–8F) were amended with `<reviews_addendum_8X>` blocks and new `(reviews-pass)` success criteria. This cycle 2 review asks Codex and OpenCode to classify each prior HIGH as FULLY RESOLVED, PARTIALLY RESOLVED, or UNRESOLVED, and to identify any new HIGHs.

The two reviewers agree that the addendum discipline is strong (concrete tests, gates, and migration steps), but disagree on the residual risk count:

- **OpenCode:** 15 FULLY / 1 PARTIALLY / 0 UNRESOLVED / 0 NEW HIGH — recommends proceeding to execution.
- **Codex:** 13 FULLY / 5 PARTIALLY / 0 UNRESOLVED / 2 NEW HIGH — flags additional residuals in cache versioning, cookie isolation, scope checkpoints, BYOK historical backfill, and two newly-spotted implementation problems (`kek_version` overload and Postgres partial unique index using a subquery, which Postgres does not support).

Codex's findings are concrete and verifiable, so we treat them as the stricter (and load-bearing) reading. The reconciled unresolved-HIGH count for cycle 2 is **7**.

---

## Codex Review

**Summary**
Cycle 2 is a substantial improvement. Most original HIGHs now have concrete mitigations plus tests, gates, or explicit artifacts. The full plan is not converged yet: cache consistency is only fully fixed for master-key rotation, session isolation remains conditional on an unproven Spring Session shape, cross-plan ownership is still loose, and the spend attribution migration still uses the old incorrect heuristic for historical rows.

**Per-HIGH Disposition**

| Cycle 1 HIGH | Status | Evidence |
|---|---:|---|
| HIGH-1 WebAuthn endpoint/routing assumptions | FULLY RESOLVED | 8A R-H1 adds `docs/ops/admin-interface-freeze.md`; R-H3 moves token validation to `/api/admin/enrollment/session`; Spring Security docs confirm endpoint freeze is necessary because default auth-options path is `/webauthn/authenticate/options`, not the plan's earlier assumed path. |
| HIGH-2 Async Modulith listeners assumed synchronous | PARTIALLY RESOLVED | 8B R-H4 fixes master-key rotation with versioned cache keys and synchronous/202 fallback. 8D catalog still relies mainly on `CatalogChangedEvent` eviction without the same request-bound version guarantee. |
| HIGH-3 Cross-plan file ownership conflicts | PARTIALLY RESOLVED | Liquibase ranges and `Phase8AdminArchTestSuite` help, but shared files remain contested: `SecurityConfig`, `ChatModelCacheEvictionListener`, `db.changelog-master.yaml`, route/nav wiring. No ownership matrix or route registry is specified. |
| HIGH-4 8D/8E/8F too large for autonomous execution | PARTIALLY RESOLVED | 8A manual checkpoint and Phase 8 Arch suite improve gates, but 8D/8E/8F remain `autonomous: true` with no explicit human/integration stop after 8B/8C. |
| HIGH-5 `/enroll` SPA/backend collision | FULLY RESOLVED | 8A R-H3 makes `/enroll` SPA-only and moves backend validation to `POST /api/admin/enrollment/session`; acceptance includes NPM split and 410 tests. |
| HIGH-6 WebAuthn endpoint names mismatch | FULLY RESOLVED | 8A R-H1 requires Context7-verified endpoint freeze and matcher superset before coding; code must cite the freeze doc. |
| HIGH-7 HMAC chain ordering nondeterministic | FULLY RESOLVED | 8A R-H2 adds `chain_index BIGSERIAL UNIQUE`, `canonical_timestamp_ms`, `FOR UPDATE` latest-hash lookup, and 1000-row concurrent verification. |
| HIGH-8 Per-chain Spring Session cookie isolation | PARTIALLY RESOLVED | 8A R-H4 adds a strategy and `AdminChainCookieIsolationTest`, but the exact Spring Session shape is still conditional. Spring Session docs primarily describe a single `CookieSerializer` bean and namespace property, so this needs the freeze result before it is fully closed. |
| HIGH-9 `encrypted_key NOT NULL` conflicts with seed rows | FULLY RESOLVED | 8B R-H1 makes `encrypted_key`, `kek_version`, `last_rotated_at`, `key_format` nullable for unconfigured providers with a paired-key CHECK. |
| HIGH-10 Feature defaults on master-key table | FULLY RESOLVED | 8B R-H2 plus 8D R-H8 migrate to `feature_default_provider` and drop the temporary 8B columns. Full-phase completion closes it. |
| HIGH-11 Sentinel test conflicts with masked keys | FULLY RESOLVED | 8B R-H3 bans raw key shapes while allowing exact masked forms such as `sk-****abc1`; fixtures cover raw, masked, and base64 raw forms. |
| HIGH-12 Rotation returns before cache eviction | FULLY RESOLVED | 8B R-H4 adds versioned cache keys and requires observable eviction before success, or `202 Accepted` if synchronous semantics cannot be guaranteed. |
| HIGH-13 `appendAsSystem` violates FK | FULLY RESOLVED | 8C R-H1 seeds a real system actor row and `appendAsSystem` uses that FK target. |
| HIGH-14 Manual cascade deletion can miss tables | FULLY RESOLVED | 8C R-H3 adds `TenantDeletionRegistry` plus `TenantDeletionCoverageTest` against Postgres FK introspection. |
| HIGH-15 Body-ban key matching too narrow | FULLY RESOLVED | 8C R-H4 centralizes substring/case-insensitive regex in `AdminBodyBanRegex` and tests `postBodyText` style variants. |
| HIGH-16 Catalog FK migration can fail existing tenants | FULLY RESOLVED | 8D R-H1 adds pre-FK audit/backfill, logs affected counts, and requires operator sign-off before FK addition. |
| HIGH-17 Requeue semantics conflict | FULLY RESOLVED | 8E R-H1 separates semantics: `attempts=0` for worker retry budget, `admin_requeue_count += 1` for intervention tracking. |
| HIGH-18 Platform-vs-BYOK tenant-level classification | PARTIALLY RESOLVED | 8F R-H1 adds row-level `credential_source` and coverage for future writes, but historical backfill still uses the old tenant/provider heuristic. That should be `UNKNOWN` or explicitly caveated, not forced into PLATFORM/BYOK. |

**New HIGH Concerns**

| New HIGH | Evidence | Required fix |
|---|---|---|
| NEW-HIGH-1: `kek_version` is overloaded as cache key version | 8B R-H4 says `providerKeyVersion` is `llm_provider_master_key.kek_version` and is bumped on every successful rotation. But `kek_version` is the cryptographic KEK selector; bumping it without a new KEK can break decryption or corrupt key metadata. | Add a separate `provider_secret_version BIGINT` / `rotation_version` column for cache keys. Keep `kek_version` only as cipher KEK version. |
| NEW-HIGH-2: 8D default uniqueness index is not implementable as written | 8D R-H2 removes `feature_binding.provider` and proposes a partial unique index using a subquery into `model_catalog`. PostgreSQL does not allow subqueries in index expressions; the fallback is service-only enforcement. | Choose a DB-enforced design: keep provider with composite FK consistency, add a trigger-maintained provider column, or move defaults entirely to `feature_default_provider`. |

**Other Strengths / Suggestions**

The addendums are strongest where they convert ambiguity into executable gates: `chain_index`, Postgres Testcontainers, system actor, deletion registry, SQL spy tests, row-level `credential_source`, and direct test-connection rate limiting.

Before execution, scrub stale task text that contradicts addendums. Examples: old `/enroll` filter references, old H2 Liquibase verify commands, old `051/052/053` changelog names, and second-boot bootstrap criteria. The addendums say they are authoritative, but stale acceptance text is exactly how executor drift happens.

**Final Disposition (Codex)**

Across the 18 labeled HIGH rows: **13 FULLY RESOLVED**, **5 PARTIALLY RESOLVED**, **0 UNRESOLVED**, **2 NEW HIGHs**.

---

## OpenCode Review

### 1. Summary

Substantial progress. All 16 HIGH concerns from Cycle 1 have been systematically addressed through explicit `reviews_addendum` blocks in each plan. The addendums are precise, referential to the specific concern, and include concrete verification artifacts (interface freeze doc, BIGSERIAL chain ordering, versioned cache keys, NULLABLE columns, dedicated tables, registry patterns, ArchUnit gates, row-level credential_source). The review team's adversarial approach in Cycle 1 surfaced genuine risks, and the plan authors have responded with commensurate rigor. **15 of 16 HIGHs are FULLY RESOLVED**; 1 is PARTIALLY RESOLVED. No new HIGH concerns were introduced.

### 2. Per-HIGH Disposition Table

| # | Cycle 1 HIGH | Status | Evidence |
|---|---|---|---|
| **HIGH-1** | WebAuthn endpoint/routing assumptions unstable | **FULLY RESOLVED** | R-8A-H1 produces `docs/ops/admin-interface-freeze.md` pinning exact Spring Security 7.0.5 `.webAuthn(...)` paths via Context7 lookup. R-8A-H3 splits `/enroll` to SPA-only & backend at `/api/admin/enrollment/session`. SecurityConfig & apps/admin cite the freeze. |
| **HIGH-2** | Async Modulith listeners give false sync cache-safety | **FULLY RESOLVED** | R-8B-H4: Versioned cache keys (`providerKeyVersion` bumped per rotation) + `@TransactionalEventListener(phase=AFTER_COMMIT)` synchronous eviction. A request with new key version naturally misses stale entries regardless of async timing. Fallback: 202 Accepted + `eviction_pending_until`. |
| **HIGH-3** | Cross-plan file ownership conflicts | **FULLY RESOLVED** | R-8A-H10: Liquibase numbering offsets (8A=048-057, 8B=058-067, 8D=068-077, 8E=078+). R-8A-H8: Turborepo outputs, ESLint import blocks. R-8C-H8: `Phase8AdminArchTestSuite` aggregates all 10+ ArchUnit tests. Shared-file modifications explicitly coordinated. |
| **HIGH-4** | Scope too large without integration checkpoints | **PARTIALLY RESOLVED** | R-8C-H8 adds `Phase8AdminArchTestSuite` (code-level integration gate). 8A-08 is a blocking human checkpoint. 8D/8E/8F still marked `autonomous: true` with no cross-plan end-to-end integration test specified beyond the ArchUnit suite. Mitigated by dependency chain (8D→8B, 8F→8E) but not fully resolved. |
| **HIGH-5** | `/enroll` is both SPA & backend-filtered route | **FULLY RESOLVED** | R-8A-H3: `/enroll` is exclusively SPA. Backend token validation at `POST /api/admin/enrollment/session`. `EnrollmentTokenGate` filter removed. NPM routing documented in runbook. |
| **HIGH-6** | Passkey endpoint names don't match `/login/webauthn/options` | **FULLY RESOLVED** | R-8A-H1: Interface freeze doc pinning exact stock paths from Context7 docs. Replaces assumed paths with verified Spring Security 7.0.5 exports. |
| **HIGH-7** | HMAC chain ordering not deterministic under concurrency | **FULLY RESOLVED** | R-8A-H2: Added `chain_index BIGSERIAL UNIQUE` + `canonical_timestamp_ms BIGINT NOT NULL`. Chain ordering by `chain_index`, not `created_at`. Concurrency test (4 threads × 1000 rows) verifies deterministic re-derive. |
| **HIGH-8** | Per-chain Spring Session cookie isolation unresolved | **FULLY RESOLVED** | R-8A-H4: Two `SpringSessionRepositoryFilter` registrations, separate `RedisIndexedSessionRepository` beans, distinct cookie names (`SESSION_ADMIN`/`SESSION_USER`), namespaced Redis keys. `AdminChainCookieIsolationTest` verifies cross-cookie 401. Fallback documented. |
| **HIGH-9** | `encrypted_key NOT NULL` conflicts with seeded pre-key rows | **FULLY RESOLVED** | R-8B-H1: `encrypted_key` NULLABLE. All 6 rows seeded with NULL key, NULL kek_version. CHECK constraint pairs NULL/non-NULL. UI shows `status: NOT_SET`. |
| **HIGH-10** | `feature_default_provider_*` on master-key table is wrong location | **FULLY RESOLVED** | R-8B-H2: Two-phase plan — 8B stub columns marked DEPRECATED, 8D Liquibase 069 creates dedicated `feature_default_provider` table + migrates data + DROPs columns. Enforcement via transactional clear-all-then-set-one. |
| **HIGH-11** | Sentinel test banning `sk-` conflicts with masked display `sk-****abc1` | **FULLY RESOLVED** | R-8B-H3: Banned regex refined to `sk-[A-Za-z0-9]{16,}` (raw shapes). Masked `sk-\*{4}[A-Za-z0-9]{4}` allowed. Fixture: `sk-proj-abcdefghij1234567890` fails; `sk-****abc1` passes. Base64-encoded forms also caught. |
| **HIGH-12** | Rotation response returns before cache eviction finishes | **FULLY RESOLVED** | R-8B-H4: Versioned cache keys + synchronous AFTER_COMMIT eviction. `providerKeyVersion` in cache key ensures natural cache miss on new version. Fallback mechanism specified. |
| **HIGH-13** | `appendAsSystem` ZERO_UUID violates `admin_users` FK | **FULLY RESOLVED** | R-8C-H1: Seeded system actor `id='00000000-0000-0000-0000-000000000001'`, `email='<system>'`, `status='ACTIVE'`. `appendAsSystem` uses this UUID. FK satisfied. |
| **HIGH-14** | Manual cascade deletion misses future tenant-owned tables | **FULLY RESOLVED** | R-8C-H3: `TenantDeletionRegistry` pattern with startup bean registration. `TenantDeletionCoverageTest` introspection of `information_schema` FKs against `tenants(id)` → fails CI when registry misses a table. |
| **HIGH-15** | Body-ban key matching too narrow (exact names vs substring) | **FULLY RESOLVED** | R-8C-H4: Shared `AdminBodyBanRegex.FORBIDDEN_FIELD_NAME` = `(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*`. Substring/case-insensitive matching. Filter and ArchUnit reference same constant. Fixtures verify. |
| **HIGH-16** | FKs on existing `assistant_settings.*_model_id` can fail without backfill | **FULLY RESOLVED** | R-8D-H1: Two-step Liquibase: 068a NULLs orphan model_ids + logs count, 068b adds FKs. Runbook documents count sign-off gate. Operator must approve before FK addition. |
| **HIGH-17 (8E)** | Requeue semantics conflict (increment vs reset) | **FULLY RESOLVED** | R-8E-H1: `attempts` RESET to 0 + `admin_requeue_count` INCREMENT by 1. Both requirements satisfied. 6th KPI surfaces admin-requeued count. |
| **HIGH-18 (8F)** | Platform-vs-BYOK classified by tenant-level not row-level | **FULLY RESOLVED** | R-8F-H1: `credential_source VARCHAR(16) NOT NULL CHECK IN ('PLATFORM','BYOK')` column added to `llm_call_audit`. Liquibase 079 + backfill. Write-time enforcement via `LlmCallAuditCredentialSourceCoverageTest` ArchUnit. |

### 3. New HIGH Concerns

**None.** The addendums are thorough and do not introduce production-blocking issues. No novel risk surfaces.

### 4. Other Strengths / Suggestions

**Strength — Addendum discipline is excellent.** Each `reviews_addendum_*` block is self-contained, referential to the specific HIGH, and includes precise decision language. The "executor MUST apply these corrections" framing is unambiguous.

**Strength — Concrete verification artifacts.** Every resolution specifies a test, gate, or document that demonstrably proves the fix (e.g., concurrency test for chain_index, Phase8AdminArchTestSuite, AdminChainCookieIsolationTest, TenantDeletionCoverageTest, WorkerFailureReasonEnumOnlyTest, QueueHealthQueryServiceSqlSpyTest, LlmCallAuditCredentialSourceCoverageTest).

**Suggestion — Consider adding a lightweight cross-plan end-to-end spy test** for HIGH-4's residual concern. Even a single `@SpringBootTest` that walks the bootstrap→enroll→login→set-master-key→catalog-sync→view-tenant→requeue→view-spend path against mocked external dependencies would provide the integration checkpoint the concern was asking for. This could be a Phase-8 capstone test in `Phase8E2ESmokeTest` rather than requiring it per-plan.

**Suggestion — Add persistence timestamp to the interface freeze doc.** `admin-interface-freeze.md` should record when it was last verified against Context7 (e.g., `Last verified: 2026-05-19 against spring-security-refdoc 7.0.5`). This lets a Phase 9+ executor know whether re-verification is needed after a Spring Security upgrade.

**Minor — Liquibase gap.** The offset ranges (8A=048-057, 8B=058-067, 8D=068-077, 8E=078+) leave 051-057 (7 numbers) and 071-077 (7 numbers) unused. Not a problem, but a note in the addendum that reserved-but-unused numbers are available for correctional changesets would be tidy.

### 5. Final Disposition (OpenCode)

| Category | Count |
|---|---|
| **FULLY RESOLVED** | **15** |
| **PARTIALLY RESOLVED** | **1** (HIGH-4) |
| **UNRESOLVED** | **0** |
| **NEW HIGH** | **0** |

**Verdict:** The addendums are comprehensive and directly responsive. The 16 HIGH concerns from Cycle 1 are effectively closed with concrete, verifiable mitigations. HIGH-4 (integration checkpoints for autonomous execution) is the only residual — mitigated but not fully resolved — and could be closed with a single cross-plan end-to-end test. No new HIGH concerns were introduced. Proceed to execution.

---

## Cycle 2 Consensus Summary

### Agreement (both reviewers)

These 13 prior HIGHs are FULLY RESOLVED by both reviewers:

- HIGH-1 WebAuthn endpoint freeze (8A R-H1)
- HIGH-5 `/enroll` SPA/backend split (8A R-H3)
- HIGH-6 Passkey endpoint freeze (8A R-H1)
- HIGH-7 HMAC chain `chain_index BIGSERIAL` ordering (8A R-H2)
- HIGH-9 `encrypted_key` NULLABLE for seed rows (8B R-H1)
- HIGH-10 `feature_default_provider` dedicated table (8B R-H2 + 8D R-H8)
- HIGH-11 Sentinel raw-vs-masked regex (8B R-H3)
- HIGH-12 Rotation cache-eviction observability or 202 fallback (8B R-H4)
- HIGH-13 Seeded system actor row (8C R-H1)
- HIGH-14 `TenantDeletionRegistry` + coverage test (8C R-H3)
- HIGH-15 Centralized `AdminBodyBanRegex` substring matcher (8C R-H4)
- HIGH-16 Two-step Liquibase backfill before FKs (8D R-H1)
- HIGH-17 `attempts=0` + `admin_requeue_count++` (8E R-H1)

Both also agree that **HIGH-4 is PARTIALLY RESOLVED** — the autonomous flag on 8D/8E/8F lacks a cross-plan integration test; OpenCode suggests a `Phase8E2ESmokeTest` capstone.

### Codex-only residual concerns (treated as authoritative for the unresolved count)

These are concrete, verifiable gaps Codex flagged that OpenCode classified as resolved. They are kept in the unresolved count because Codex cites specific evidence the addendum is incomplete:

- **HIGH-2 (PARTIALLY RESOLVED)** — Cache versioning is only specified for master-key rotation (8B R-H4). 8D catalog still leans on `CatalogChangedEvent` async eviction without a request-bound version guarantee.
- **HIGH-3 (PARTIALLY RESOLVED)** — No ownership matrix or route registry for shared files: `SecurityConfig`, `ChatModelCacheEvictionListener`, `db.changelog-master.yaml`, frontend route/nav wiring. Liquibase number offsets help but do not cover code merge collisions.
- **HIGH-8 (PARTIALLY RESOLVED)** — Per-chain Spring Session cookie isolation strategy is conditional on a Spring Session shape that the docs primarily describe with a single `CookieSerializer` bean. Should be closed by the same interface-freeze artifact (8A R-H1) the WebAuthn endpoints used, with the actual Spring Session API names pinned.
- **HIGH-18 (PARTIALLY RESOLVED)** — Row-level `credential_source` is correct for new writes (8F R-H1), but the historical backfill still applies the old tenant-level heuristic. Should backfill as `UNKNOWN` (or otherwise explicitly caveat) rather than retroactively force PLATFORM/BYOK.

### New HIGH concerns (Codex; OpenCode did not raise these)

- **NEW-HIGH-1: `kek_version` overload.** 8B R-H4 promotes `kek_version` to also serve as the cache-key version, bumped on every rotation. `kek_version` is the cryptographic KEK selector; bumping it without rotating the actual KEK breaks decryption or corrupts key metadata. Fix: introduce a separate `provider_secret_version BIGINT` (or `rotation_version`) column and keep `kek_version` strictly for cipher KEK selection.
- **NEW-HIGH-2: PostgreSQL does not support subqueries in index expressions.** 8D R-H2 proposes a partial unique index whose predicate subqueries `model_catalog`. This DDL will not parse. Choose one of: (a) reinstate `feature_binding.provider` and rely on composite FK consistency, (b) maintain a derived `provider` column via trigger, or (c) move feature-default uniqueness entirely into the dedicated `feature_default_provider` table where a plain unique index works.

### Other agreed strengths

- Addendum discipline is excellent — each block is referential, names the concern, and ends with a concrete gate or test artifact.
- Verification artifacts are concrete: `chain_index` concurrency test, `Phase8AdminArchTestSuite`, `AdminChainCookieIsolationTest`, `TenantDeletionCoverageTest`, `WorkerFailureReasonEnumOnlyTest`, `QueueHealthQueryServiceSqlSpyTest`, `LlmCallAuditCredentialSourceCoverageTest`.

### Suggestions to fold into Cycle 3

1. Resolve the **2 new HIGHs** in 8B and 8D — both are concrete schema/DDL fixes, not redesigns.
2. Extend the `admin-interface-freeze.md` artifact to also pin Spring Session cookie / repository / namespacing API names (closes HIGH-8).
3. Mirror 8B's versioned cache-key strategy in 8D for `CatalogChangedEvent` consumers (closes HIGH-2).
4. Specify the historical backfill rule for `llm_call_audit.credential_source` — either `UNKNOWN` literal or a documented heuristic with caveat exposed in the spend UI (closes HIGH-18).
5. Add an ownership matrix and/or route registry for shared files; declare which plan "owns" `SecurityConfig`, `ChatModelCacheEvictionListener`, `db.changelog-master.yaml`, and the admin nav module (closes HIGH-3).
6. Add a single `Phase8E2ESmokeTest` capstone covering bootstrap→enroll→login→set-master-key→catalog-sync→view-tenant→requeue→view-spend (closes HIGH-4).
7. Scrub stale acceptance text in each plan that contradicts the addendums (Codex's executor-drift warning).

### Reconciled cycle-2 unresolved HIGH count

Counted per the gsd-review counting rules (PARTIALLY RESOLVED + UNRESOLVED + NEW HIGH; FULLY RESOLVED excluded):

| Category | Items | Count |
|---|---|---:|
| PARTIALLY RESOLVED (still in progress, not verified) | HIGH-2, HIGH-3, HIGH-4, HIGH-8, HIGH-18 | 5 |
| UNRESOLVED | none | 0 |
| NEW HIGH | NEW-HIGH-1 (`kek_version` overload), NEW-HIGH-2 (subquery-in-index) | 2 |
| **Total unresolved HIGH for Cycle 2** | | **7** |

Cycle-over-cycle trend: 16 → 7 unresolved HIGHs (−56%). Significant progress; one more replan cycle should converge.

---

## Cycle 1 Review (archived)

> The original cycle 1 review content has been superseded by this cycle 2 update. Cycle 1 raised 16 HIGH-severity concerns across the whole-phase and per-plan dimensions. All 16 are addressed in Cycle 2 above (13 fully, 5 partially per Codex; 15 fully, 1 partially per OpenCode), with 2 new HIGHs introduced by the addendums themselves. See git history for the verbatim cycle 1 REVIEWS.md (`git log --diff-filter=M -- .planning/phases/08-admin-console-operator-tooling/08-REVIEWS.md`).
