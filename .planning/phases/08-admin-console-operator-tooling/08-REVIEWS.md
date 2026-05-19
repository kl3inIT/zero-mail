---
phase: 8
cycle: 3
reviewers: [codex, opencode]
reviewed_at: 2026-05-19T17:45:00Z
plans_reviewed:
  - 8A-PLAN.md
  - 8B-PLAN.md
  - 8C-PLAN.md
  - 8D-PLAN.md
  - 8E-PLAN.md
  - 8F-PLAN.md
prior_cycle_high_count: 7
cycle3_unresolved_high_count: 2
---

# Cross-AI Plan Review — Phase 8 (Cycle 3)

## Context

Cycle 2 reconciled 7 unresolved HIGH-severity concerns across the six Phase-8 plans:

- HIGH-2 (catalog versioned cache missing in 8D)
- HIGH-3 (cross-plan shared-file ownership undocumented)
- HIGH-4 (8D/8E/8F autonomous without cross-plan integration test)
- HIGH-8 (Spring Session API freeze conditional / unverified)
- HIGH-18 (BYOK historical backfill applied wrong heuristic)
- NEW-HIGH-1 (`kek_version` overloaded as cipher selector AND cache-bust counter)
- NEW-HIGH-2 (PostgreSQL subquery-in-index DDL is not implementable)

The 6 plans (8A-8F) were amended in commit `c3552704` with `## Cycle 3 reviews-pass addendum` sections targeting all 7 residuals. This cycle 3 review asks Codex and OpenCode to re-classify each prior HIGH as FULLY RESOLVED, PARTIALLY RESOLVED, or UNRESOLVED, and to flag any new HIGHs introduced by the cycle-3 edits themselves.

The two reviewers disagree on residual risk count:

- **OpenCode:** 7 FULLY / 0 PARTIALLY / 0 UNRESOLVED / 0 NEW HIGH — recommends proceeding to execution.
- **Codex:** 6 FULLY / 1 PARTIALLY / 0 UNRESOLVED / 1 NEW HIGH — flags `Phase8E2ESmokeTest` step 2 not being executable as written (depends on an undefined `WebAuthnTestHarness` artifact) and a new schema-integrity HIGH in 8D R-H11 (`feature_default_provider.provider` and `model_id` can disagree because the schema declares independent FKs).

Per the cycle-2 precedent, Codex's findings are concrete and verifiable, so we treat them as the stricter (and load-bearing) reading. The reconciled unresolved-HIGH count for cycle 3 is **2**.

---

## Codex Review

**Summary**
Cycle 3 closes most of the cycle-2 gaps with concrete schema, cache-key, ownership, and UI changes. I would not call it a clean reviews-pass yet: HIGH-4 is still only partially resolved because the capstone smoke test depends on an undefined `WebAuthnTestHarness`, and cycle-3 introduces one new HIGH in 8D: `feature_default_provider.provider` and `model_id` can disagree because the schema uses independent FKs.

**Per-HIGH Disposition Table**

| Prior HIGH | Status | Evidence |
|---|---:|---|
| HIGH-2 catalog versioned cache | FULLY RESOLVED | 8D adds `catalog_version BIGINT NOT NULL DEFAULT 1` and extends `CacheKey(... providerSecretVersion, providerCatalogVersion)`. Quote: "A request after a catalog change naturally MISSES on the new `providerCatalogVersion` regardless of whether the async `ChatModelCacheEvictionListener` has fired yet." |
| HIGH-3 ownership matrix | FULLY RESOLVED | 8A adds `docs/ops/admin-shared-file-ownership.md` with required shared artifacts, and 8C/8E/8F propagate it. Quote: "declares, for every file touched by more than one plan in Phase 8, a single owning plan and a contribution protocol." |
| HIGH-4 cross-plan integration test | PARTIALLY RESOLVED | The capstone test/gates are the right shape, but step 2 is not executable as specified. Quote: "drive WebAuthn registration ceremony via `WebAuthnTestHarness` (mock authenticator)." I found no existing harness in the repo, and Context7 Spring Security docs did not surface a built-in WebAuthn test harness. Add an explicit test utility artifact/design, or use a supported test-profile/admin-session bypass for the smoke test. |
| HIGH-8 Spring Session API freeze | FULLY RESOLVED | 8A now requires a Context7-backed freeze and fallback. Quote: "if NO, the chosen strategy is single-repository + cookie-path scoping." Context7 confirms documented Spring Session extension points include `RedisIndexedSessionRepository`, `CookieSerializer`, `DefaultCookieSerializer`, and the standard `springSessionRepositoryFilter`; the addendum correctly avoids assuming the two-repository variant without verification. |
| HIGH-18 BYOK historical backfill | FULLY RESOLVED | 8F replaces the heuristic with `UNKNOWN` and exposes it honestly. Quote: "Drop the heuristic UPDATE entirely" and "historical rows stay honest as `'UNKNOWN'`." |
| NEW-HIGH-1 `kek_version` overload | FULLY RESOLVED | 8B separates `provider_secret_version` from cipher KEK metadata. Quote: "`CacheKey` record ... carries `long providerSecretVersion` field — NOT `kek_version`." |
| NEW-HIGH-2 subquery-in-index | FULLY RESOLVED | 8D removes the invalid partial-expression-index design. Quote: "No partial index needed; no subquery; no triggers." |

**New HIGH Concerns**

| New HIGH | Evidence | Severity rationale | Required fix |
|---|---|---|---|
| NEW-HIGH-3: `feature_default_provider.provider` can disagree with `model_id` provider | 8D R-H11 creates independent FKs: `provider → provider_catalog(provider)` and `model_id → model_catalog(model_id)`, while saying provider is "reachable via the FK chain." There is no composite FK tying the two columns together. | A row can say `provider='OPENAI'` with an Anthropic `model_id`. If routing/cache/billing trusts the stored provider, the app can build the wrong ChatModel, use the wrong provider key, or misattribute spend. This is the same class of DB integrity problem cycle 2 was trying to eliminate. | Either remove `provider` from `feature_default_provider` and always derive it by joining `model_catalog`, or add a composite FK `(provider, model_id) REFERENCES model_catalog(provider, model_id)` with a supporting unique constraint. |

**Other Strengths / Suggestions**

- Correct the ownership row path: `backend/core/src/main/resources/application.yml (api module)` conflicts with the project convention. It should likely be `backend/api/src/main/resources/application.yml`, with worker config separate if needed.
- Broaden R-8A-H14 stale-text scrub beyond the six `8*-PLAN.md` files. Current phase docs still contain stale `EnrollmentTokenGate`, old `051/052/053`, and old audit JSON key guidance in `08-SPEC.md`, `08-PATTERNS.md`, `08-RESEARCH.md`, and `08-CONTEXT.md`.
- Make `Phase8E2ESmokeTest` step filtering explicit: define how `-Dphase8.smoke.steps=1-5` skips later assertions without failing on missing fixtures.

**Final Disposition (Codex)**

| Category | Count |
|---|---:|
| FULLY RESOLVED | 6 |
| PARTIALLY RESOLVED | 1 |
| UNRESOLVED | 0 |
| NEW HIGH | 1 |

Verdict: not quite reviews-pass. Fix the WebAuthn smoke-test executability gap and the `feature_default_provider` composite integrity hole.

Sources checked via Context7: Spring Session docs for Redis/session repository and cookie customization, and Spring Security 7 WebAuthn reference.

---

## OpenCode Review

### 1. Summary

All 7 prior HIGHs are **FULLY RESOLVED**. The cycle-3 addendums are precise, implementable, and architecturally sound. Each fix directly addresses the cited residual with a concrete mechanism (schema change, code change, test artifact, or document), proper enforcement (CI gates, ArchUnit, acceptance criteria), and cross-plan coherence verification. No new HIGH concerns are introduced.

### 2. Per-HIGH Disposition Table

| Cycle-2 HIGH | Status | Evidence from cycle-3 addendum |
|---|---|:---|
| **HIGH-2** (catalog versioned cache) missing in 8D | **FULLY RESOLVED** | 8D R-H12: `catalog_version BIGINT NOT NULL DEFAULT 1` column on `provider_catalog`, monotonic counter incremented in same `@Transactional` as data change. `CacheKey` extended with `providerCatalogVersion`. Request-bound version guarantee mirrors 8B's `provider_secret_version` exactly — "A request after a catalog change naturally MISSES on the new `providerCatalogVersion` regardless of whether the async `ChatModelCacheEvictionListener` has fired yet." Listener demoted to memory-reclaim optimization. ETag becomes SHA-256 of `(catalog_version_per_provider_map \|\| payload_bytes)`. Parallel-read acceptance test specified. |
| **HIGH-3** (ownership matrix) undocumented | **FULLY RESOLVED** | 8A R-H11: `docs/ops/admin-shared-file-ownership.md` with 6 mandatory rows (`SecurityConfig`, `ChatModelCacheEvictionListener`, `db.changelog-master.yaml`, `apps/admin/src/routes/__root.tsx`, `OpenApiConfig.java`, `application.yml`). Each row has owning plan, contributors, and contribution protocol with specific prohibitions (e.g. "later plans MUST NOT add new chains"). CI-verifiable: `wc -l ≥ 60`, grep gates. Propagated to 8C R-H9 (MUST NOT edit `__root.tsx`, listener limited), 8E R-H8 (changelog header cites matrix), 8F R-H11 (route file NEW only, nav merge owned by 8A). |
| **HIGH-4** (cross-plan integration test) absent | **FULLY RESOLVED** | 8A R-H13: `Phase8E2ESmokeTest` capstone — 8-step `@SpringBootTest(webEnvironment=RANDOM_PORT)` walking bootstrap→enroll→login→master-key→catalog-sync→tenant-inspect→requeue→spend. Each step has a mocked external dependency, a fixture seeder per contributor plan, and a verification assertion. Contributor plans gated on relevant step (8D step 5, 8E step 7, 8F all 8). Tagged `@Tag("phase8-e2e")`. Intermediate failures tolerated until dependency chain completes. This is the integration checkpoint the cycle-1 reviewers asked for. |
| **HIGH-8** (Spring Session API freeze) conditional | **FULLY RESOLVED** | 8A R-H12: `admin-interface-freeze.md` gains `§Spring Session API` section. Freeze pins: (1) whether two `SessionRepository` beans permitted ([if NO, collapses to cookie-path scoping]), (2) exact class names + qualifiers, (3) cookie/Redis-key namespace, (4) `Last verified: 2026-05-19` timestamp. `AdminChainCookieIsolationTest` covers BOTH outcomes. CI gate `grep -c '## Spring Session API' docs/ops/admin-interface-freeze.md ≥ 1`. Same artifact that closed HIGH-1/HIGH-6. |
| **HIGH-18** (BYOK historical backfill) uses wrong heuristic | **FULLY RESOLVED** | 8F R-H9: Heuristic UPDATE dropped entirely. Backfill as literal `'UNKNOWN'` (via column default or explicit UPDATE). CHECK expanded to `PLATFORM\|BYOK\|UNKNOWN`. `SpendBucket` gains `BigDecimal unknownCost`. Query groups by 3 buckets. UI renders 3-segment stacked bar (gray = unknown) with persistent caption showing `unknownCost / totalCost * 100%`. Caption hides when `unknownCost=0`. Top-20 includes `unknownPct` column. Configurable boundary property `zeromail.admin.spend.row-level-classification-since`. Write-path ArchUnit gate still enforces PLATFORM/BYOK only. |
| **NEW-HIGH-1** (`kek_version` overload) | **FULLY RESOLVED** | 8B R-H10: New column `provider_secret_version BIGINT NOT NULL DEFAULT 1` — monotonic cache-bust counter. `kek_version` retains cipher-KEK-selector semantics (written once at row creation, bumped only on KEK ring rotation). `CacheKey` renamed to `providerSecretVersion` (not `kekVersion`). `ProviderMasterKeyResolver.resolve()` exposes `ResolvedKey(..., long providerSecretVersion)`. Rotate increments version by 1, leaves `kek_version` unchanged. `CacheKeyShapeTest` ArchUnit asserts record has `providerSecretVersion` and excludes `kekVersion`. Separation rationale documented: KEK re-wrap preserves caches; secret change busts caches. |
| **NEW-HIGH-2** (subquery-in-index) | **FULLY RESOLVED** | 8D R-H11: Option (c) — uniqueness moved entirely into `feature_default_provider` table with PRIMARY KEY on `feature` (max 3 rows). No `feature_binding.provider` reinstatement. No trigger-maintained derived column. No service-only enforcement. `feature_binding` simplified to `(id PK, model_id FK, feature VARCHAR(16), enabled BOOLEAN)`. `INSERT ... ON CONFLICT(feature) DO UPDATE` for `FeatureDefaultProviderService.set()`. FK `RESTRICT` blocks model deletion when pinned as default. Provider reachable via FK chain `feature_default_provider → model_catalog.provider`. DDL is plain PostgreSQL 17. |

### 3. New HIGH Concerns

**None.**

The cycle-3 edits are thorough and do not introduce production-blocking issues. Specific risk areas I scrutinized and dismissed:

- **Phase8E2ESmokeTest WebAuthn mocking feasibility** — The addendum references a `WebAuthnTestHarness` (mock authenticator) to be built in 8A. Spring Security 7.x provides `@WithMockUser` and test utilities for WebAuthn-flavored authentication; a `RelyingPartyRegistrationService` mock plus direct cookie injection provides a well-understood fallback. Not HIGH — the test can seed the session directly if the ceremony mock proves intractable.
- **Liquibase 068/069 ordering for `feature_binding` columns** — The addendum clearly overrides column creation: "068 will not have created them" and acceptance criteria verify `(id, model_id, feature, enabled)` only. The 069 defensive DROP handles rollback leftovers. Clean.
- **`CacheKey` component growth** — Now holds 6 components `(tenantId, feature, provider, modelId, providerSecretVersion, providerCatalogVersion)`. Standard cache-key pattern; no HIGH.
- **3-bucket spend query performance** — `GROUP BY bucket, credential_source` on an indexed `llm_call_audit` is well within Postgres capability. Not a concern.
- **Division-by-zero in spend percentage** — Trivial guard at render time; consistent with prior frontend code patterns. Not HIGH.

### 4. Other Strengths / Suggestions

**Strength — Addendum discipline is strong and getting stronger.** Each cycle-3 block names the HIGH, explains the residual, specifies a locked decision with code/schema/test artifacts, and ends with concrete acceptance criteria and CI gates. Cross-plan references are explicit (e.g., 8D R-H14 "grep gate `grep -RnE 'kekVersion|kek_version' backend/core/src/main/java/com/zeromail/core/admin/cat/` returns 0 hits").

**Strength — `provider_secret_version` separation reasoning is excellent.** The KEK re-wrap vs cache-bust distinction is clearly explained with a concrete ops scenario.

**Strength — `Phase8E2ESmokeTest` is the right artifact.** A single capstone test that the full dependency chain must pass before closeout is precisely the checkpoint the cycle-1 reviewers wanted. The fixture-seeder pattern per plan (8B's `MasterKeyFixtureSeeder`, 8C's `TenantFixtureSeeder`, 8F's `SpendFixtureSeeder`) cleanly distributes responsibility.

**Suggestion — Specify the `-Dphase8.smoke.steps` filtering mechanism.** The addendums use `-Dphase8.smoke.steps=1-5` to gate intermediate results but don't say how the test implements this. Suggest: an `assumeTrue(Integer.getInteger("phase8.smoke.maxStep", 8) >= currentStepNumber)` guard at the top of each step block, or a `@EnabledIfSystemProperty(named = "phase8.smoke.maxStep", matches = "[5-8]")` annotation pattern. Choose one before the executor starts coding.

**Suggestion — Move 8B's `ProviderMasterKeyResolver.resolve()` query of `provider_catalog.catalog_version` into a shared `CatalogVersionResolver`.** 8B owns `ProviderMasterKeyResolver`; adding a cross-table query to `provider_catalog` during a `resolve()` call (which is also consumed by catalog-side code per 8D R-H12) creates a circular dependency: 8B codes a catalog-version query, but 8D owns `provider_catalog`. Either (a) 8B's resolver reads `catalog_version` from `provider_catalog` directly (acceptable — it's a read-only FK-like lookup by provider PK), or (b) resolve returns only `providerSecretVersion` and 8D's `CacheKey` assembly separately reads `catalog_version`. Not a HIGH — either choice works — but commit to one in the addendum text so the 8B/8D executors don't deadlock.

### 5. Final Disposition (OpenCode)

| Category | Count |
|---|---|
| **FULLY RESOLVED** | **7** |
| **PARTIALLY RESOLVED** | **0** |
| **UNRESOLVED** | **0** |
| **NEW HIGH** | **0** |

**Verdict:** The 7 unresolved HIGHs from cycle 2 are **all fully resolved** with concrete, verifiable, cross-plan-coherent fixes. Cycle-over-cycle trend: 16 → 7 → 0 unresolved HIGHs. No new production-blocking issues are introduced. The plans are ready to proceed to execution.

---

## Cycle 3 Consensus Summary

### Agreement (both reviewers)

These 6 prior HIGHs are FULLY RESOLVED by both reviewers:

- **HIGH-2** (catalog versioned cache) — closed by 8D R-H12 (`provider_catalog.catalog_version` + `CacheKey.providerCatalogVersion`)
- **HIGH-3** (ownership matrix) — closed by 8A R-H11 (`docs/ops/admin-shared-file-ownership.md` with 6 rows) + 8C/8E/8F propagation
- **HIGH-8** (Spring Session API freeze) — closed by 8A R-H12 (§Spring Session API section in `admin-interface-freeze.md` + Last-verified timestamp + both-variants test)
- **HIGH-18** (BYOK historical backfill) — closed by 8F R-H9 (literal `'UNKNOWN'` backfill + 3-bucket SUM + UI caveat caption)
- **NEW-HIGH-1** (`kek_version` overload) — closed by 8B R-H10 (separate `provider_secret_version BIGINT` column + `CacheKey.providerSecretVersion` + `CacheKeyShapeTest`)
- **NEW-HIGH-2** (subquery-in-index) — closed by 8D R-H11 (`feature_default_provider` PK on `feature`, no partial index, no subquery)

### Disagreement — HIGH-4 (capstone integration test)

OpenCode classifies HIGH-4 as FULLY RESOLVED. Codex classifies it as PARTIALLY RESOLVED on the grounds that `Phase8E2ESmokeTest` step 2 ("drive WebAuthn registration ceremony via `WebAuthnTestHarness`") depends on an artifact that does not exist in the repo and is not specified anywhere in the cycle-3 addendums; Context7 lookup of Spring Security 7 did not surface a built-in WebAuthn test harness. OpenCode argues the same gap is closable with a `RelyingPartyRegistrationService` mock + direct session-cookie injection as a fallback, but acknowledges no specific artifact name was committed in the addendum text.

We treat Codex's reading as load-bearing: an integration capstone whose first authenticated step references an undefined test utility is not yet executable. **HIGH-4 stays PARTIALLY RESOLVED** in the reconciled count.

### New HIGH (Codex; OpenCode did not raise this)

- **NEW-HIGH-3: `feature_default_provider.provider` and `model_id` can disagree.** 8D R-H11 designed two independent foreign keys (`provider → provider_catalog(provider)` and `model_id → model_catalog(model_id)`) and stated provider is "reachable via the FK chain `feature_default_provider → model_catalog.provider`," but the schema as written does not actually enforce that the stored `provider` matches the `model_catalog.provider` of the stored `model_id`. A row could legally store `provider='OPENAI', model_id='anthropic/claude-4.7-opus'`, and any code path that trusts the stored `provider` would route through the wrong ChatModel adapter, decrypt with the wrong master key, or misattribute spend. This is the same class of integrity bug NEW-HIGH-2's redesign was meant to eliminate.

  **Required fix (per Codex):** Either (a) drop the `provider` column from `feature_default_provider` and always join `model_catalog` to derive it, or (b) add a composite FK `(provider, model_id) REFERENCES model_catalog(provider, model_id)` with a supporting unique constraint on `model_catalog(provider, model_id)`.

### Codex-only other concerns (kept as MEDIUM, not counted toward HIGH)

- Ownership-matrix row mis-cites `backend/core/src/main/resources/application.yml (api module)` while the project convention is `backend/api/src/main/resources/application.yml`. Subproject-owned configuration files convention (CLAUDE.md item 9) is violated by the matrix path. Fix: correct the path in 8A R-H11.
- R-8A-H14 stale-text scrub is scoped to the six `8*-PLAN.md` files only. Stale tokens (`EnrollmentTokenGate`, old `051/052/053` changeset numbers, old audit JSON key guidance) still appear in `08-SPEC.md`, `08-PATTERNS.md`, `08-RESEARCH.md`, and `08-CONTEXT.md`. Fix: broaden the scrub to all of `.planning/phases/08-admin-console-operator-tooling/`.
- `Phase8E2ESmokeTest` `-Dphase8.smoke.steps=1-5` filtering mechanism is not specified. OpenCode also flagged this as a suggestion. Fix: commit to either an `assumeTrue(...)` guard pattern or a `@EnabledIfSystemProperty` annotation pattern in the 8A R-H13 addendum.
- OpenCode-only suggestion: clarify ownership of the `provider_catalog.catalog_version` read inside `ProviderMasterKeyResolver.resolve()` — 8B owns the resolver but 8D owns the table; pick one of "resolver reads catalog_version directly" or "8D code reads catalog_version separately during CacheKey assembly" before the executor starts.

### Suggestions to fold into Cycle 4 (if attempted) or execute-phase carve-outs

1. **(HIGH)** Resolve NEW-HIGH-3 — choose composite-FK or derive-on-read for `feature_default_provider.provider`, amend 8D R-H11.
2. **(HIGH)** Resolve HIGH-4 residual — specify the `WebAuthnTestHarness` artifact (file location, signature, dependency) or replace step 2 with a documented session-cookie-injection bypass for the smoke test profile only.
3. **(MEDIUM)** Fix the ownership-matrix `application.yml` path to `backend/api/src/main/resources/application.yml`.
4. **(MEDIUM)** Broaden the R-8A-H14 stale-text scrub to all Phase 8 docs, not just the six PLAN.md files.
5. **(MEDIUM)** Specify the `Phase8E2ESmokeTest` step-filtering mechanism (`assumeTrue` vs `@EnabledIfSystemProperty`).
6. **(LOW)** Commit to a single owner for the `provider_catalog.catalog_version` read in CacheKey assembly to avoid 8B/8D executor circular-dependency confusion.

### Reconciled cycle-3 unresolved HIGH count

Counted per the gsd-review counting rules (PARTIALLY RESOLVED + UNRESOLVED + NEW HIGH; FULLY RESOLVED excluded):

| Category | Items | Count |
|---|---|---:|
| PARTIALLY RESOLVED | HIGH-4 (Phase8E2ESmokeTest WebAuthn step 2 references undefined `WebAuthnTestHarness`) | 1 |
| UNRESOLVED | none | 0 |
| NEW HIGH | NEW-HIGH-3 (`feature_default_provider` independent FKs allow provider/model_id mismatch) | 1 |
| **Total unresolved HIGH for Cycle 3** | | **2** |

Cycle-over-cycle trend: 16 → 7 → 2 unresolved HIGHs (-71% from cycle 2, -88% from cycle 1). The two remaining items are both narrow, concrete schema/test fixes — one more replan cycle should converge.

---

## Cycle 2 Review (archived)

> The cycle 2 review content has been superseded by this cycle 3 update. Cycle 2 reconciled 7 unresolved HIGH-severity concerns (5 PARTIALLY RESOLVED from cycle 1 + 2 NEW HIGH introduced by cycle-1 addendums). All 7 are re-classified in Cycle 3 above (6 FULLY + 1 PARTIALLY per Codex; 7 FULLY per OpenCode), with 1 new HIGH introduced by the cycle-3 edits themselves. See git history for the verbatim cycle 2 REVIEWS.md (`git log --diff-filter=M -- .planning/phases/08-admin-console-operator-tooling/08-REVIEWS.md`).

---

## Cycle 1 Review (archived)

> Originally archived in cycle 2. Cycle 1 raised 16 HIGH-severity concerns; all addressed across cycles 2 and 3. See `git log` for the verbatim cycle 1 REVIEWS.md.
