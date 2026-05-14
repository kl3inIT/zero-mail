# Phase 6: Polish & CASA-Verified Launch - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 6 is launch-readiness validation, not feature work. It produces a tagged `v1.0.0-rc1` release candidate of Zero Mail anchored by five committed artifacts: (1) one Playwright golden-path end-to-end spec, (2) one 50-tenant × ~10 msg/min concurrency load-test result with three invariant post-checks, (3) a green CI run of all existing regression suites (prompt-injection, ArchUnit, Spring Modulith `ApplicationModulesTest`, LLM golden-set drift) on the RC tag commit, (4) `.planning/LAUNCH-GO-NOGO.md` with all checklist items checked + sign-off line committed, and (5) a post-launch CASA tracking seed. Phase 6 launches into OAuth "Testing" mode (100-user cap); the consent-screen move to Production is deferred to a post-launch CASA track.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**6 requirements are locked.** See `06-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `06-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- One Playwright end-to-end spec covering the full v1 golden path with Gmail + Pub/Sub stubbed.
- One 50-tenant × ~10 msg/min load test (tool: k6 — see D-01) with three invariant post-checks (cross-tenant isolation, ledger drift, log-bleed).
- Wiring the four existing regression gates (prompt-injection, ArchUnit, Modulith, golden-set drift) into a single RC-tag CI run.
- Cutting an annotated `v1.0.0-rc1` tag on `main`.
- Authoring `.planning/LAUNCH-GO-NOGO.md` (pass/fail checklist + sign-off line + linked evidence).
- Authoring a post-launch CASA tracking artifact (location decided as D-09).
- Stubs / fixtures needed by Playwright and the load test (mock Gmail responses, mock Pub/Sub OIDC tokens for the load tool, fixed-clock helpers).

**Out of scope (from SPEC.md):**
- CASA restricted-scope verification (submission, lab engagement, Letter of Assessment, consent-screen Production move) — deferred to a post-launch track.
- Production runbook (on-call, Pub/Sub backlog recovery, `users.watch` renewal incident playbook, ledger reconciliation playbook) — deferred to a post-launch ops track.
- Throughput SLO / p95 latency target — the load test gates on invariants only.
- Authoring new test suites for prompt-injection / ArchUnit / golden-set drift — these exist already; Phase 6 only wires them into the RC gate.
- Authoring new application code or product features — zero new REQ-IDs.
- Multi-region / HA / staging infrastructure provisioning beyond a single staging-like environment.
- Auto-send, RAG, embeddings, Outlook, team plans, mobile, enterprise SSO.

</spec_lock>

<decisions>
## Implementation Decisions

### Load-test tool & infrastructure

- **D-01:** Load tool is **k6** (Grafana). JS scripts live under `loadtest/` (top-level directory, not inside `apps/web` or `backend/api`). k6 binary installed in the GHA workflow via `grafana/setup-k6-action` or equivalent. Tool choice pairs deliberately with D-02 (docker-compose env) — k6 drives the external HTTP boundary while invariant assertions run as a separate Gradle task. Rationale: invariants are query-shaped (SQL + log scan), not throughput-shaped; SPEC waives p95 SLO; k6 + JVM Gradle task gives a clean two-stage gate (generate load → assert invariants) with no second-process query duplication.
- **D-02:** Load-test environment is **ephemeral docker-compose on the GHA runner**. New file: `loadtest/compose.loadtest.yml` brings up postgres-17 + redis-7 + the prod `backend/api` image + the prod `backend/worker` image (built from the same Dockerfile that produces production images). Run-then-teardown via `docker compose down -v` so synthetic data never leaks into a long-lived store. Rationale: this is the only option that loads the **prod `logback-spring.xml`** (Testcontainers + `@SpringBootTest` would load the test logback and false-green invariant (c) — zero log-bleed). Staging VPS rejected as overkill for a single launch checkpoint with no recurring use justification.
- **D-03:** OIDC verification for Pub/Sub-push under load test is bypassed via a new **`@Profile("loadtest")` `PubsubVerifier` stub** that accepts any token. The stub MUST be guarded by:
  1. `@ConditionalOnProperty("zeromail.loadtest.enabled", havingValue = "true")` so it never activates outside the explicit loadtest profile + env var.
  2. An ArchUnit rule that fails the build if any production class references `LoadtestPubsubVerifier` or the `loadtest` profile is added to a production `application.yml`.
  3. Property `zeromail.loadtest.enabled` set ONLY in `loadtest/compose.loadtest.yml`, never in `backend/api/src/main/resources/application.yml` or `backend/worker/src/main/resources/application.yml`.
- **D-04:** Invariant assertions run as a **new Gradle task** `:backend:api:loadtestVerify` (or a top-level convenience task) that connects via JDBC to the compose `postgres-loadtest` container after k6 finishes and runs three checks: (a) `SELECT COUNT(*) FROM audit_log WHERE tenant_id NOT LIKE 'loadtest-tenant-%'` must be zero across the loadtest window (timestamp filter); (b) per-tenant ledger reconciliation `SUM(credits) - SUM(reserves_settled) - SUM(release_settled) = 0` for every loadtest tenant; (c) regex scan of the captured docker logs (`docker logs zeromail-api zeromail-worker > loadtest/run.log`) for `email_body|prompt|completion|raw_html` patterns must return zero matches. Task fails non-zero on any drift; output written to `.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md` and committed.
- **D-05:** Synthetic tenants use the prefix **`loadtest-tenant-<1..50>`**. Volume teardown via `docker compose down -v` guarantees zero contamination of any other environment.

### Playwright golden-path stub strategy

- **D-06:** Gmail and Pub/Sub stubs use a dedicated **`e2e-stub` Spring profile** that swaps `GmailClient` and the OIDC `PubsubVerifier` beans via `@Profile("e2e-stub") @Primary` deterministic Java fakes. Real backend code runs end-to-end except the two outbound adapters. The Pub/Sub leg is a real HTTP POST from Playwright to the real `PubsubPushController` with a synthetic envelope (the stubbed verifier accepts any token). Two-tier test taxonomy: the 5 existing `page.route`-based specs (`billing-topup`, `byok`, `legal-stubs`, `onboarding-routes`, plus any future UI-state specs) STAY as-is for narrow UI-state coverage; `launch-golden-path.spec.ts` is the only spec that uses the `e2e-stub` profile. Rationale: SPEC acceptance says "fail if any step regresses" — that requires real backend code to execute, which `page.route`-only cannot deliver.
- **D-07:** The `e2e-stub` profile lives at `backend/api/src/main/resources/application-e2e-stub.yml`. Stub beans live in `backend/api/src/main/java/com/zeromail/api/e2estub/` (single package; not under `core/` because the stubs override adapter beans defined in `backend/api`/`backend/worker`). Stub state is in-memory only (e.g., a `ConcurrentHashMap<String, FakeGmailMessage>`); reset on each spec via a `/api/test/e2e-stub/reset` endpoint guarded by `@ConditionalOnProperty("zeromail.e2e-stub.enabled")`. Production `application.yml` MUST NOT set this property; ArchUnit rule mirrors D-03's guard for the loadtest profile.
- **D-08:** The Playwright spec drives the journey by issuing real HTTP POSTs to `PubsubPushController` for the "incoming message" step — the spec uses `request.fetch()` (Playwright's API-context client) rather than `page.route`. This is intentional: Pub/Sub push is a server-to-server flow and never traverses the browser, so `page.route` literally cannot intercept it.

### Post-launch CASA artifact (auto-decided)

- **D-09:** Post-launch CASA tracking lives at **`.planning/seeds/SEED-012-casa-restricted-scope-verification.md`**. Auto-decided to match the established project pattern (11 existing seeds: `SEED-001-...md` through `SEED-011-...md` in identical structure). Seed must capture: required evidence package (privacy policy URL, demo video showing every restricted scope in use, data-flow diagram, MFA evidence, key-rotation evidence, employee-access policy), CASA lab options (CREST, Bishop Fox, etc. — TBD), expected 4–12 week timeline, the consent-screen Production move as the closure trigger, and FND-07 → status change once the Letter of Assessment is filed. The seed is committed as part of Phase 6 so `LAUNCH-GO-NOGO.md` item (h) can link to it.

### RC-tag CI gate wiring

- **D-10:** CI gate uses a **reusable `gates.yml` workflow** (`workflow_call`) called by both the existing `ci.yml` (on PR + push) and a new `release.yml` (on `v*.*.*-rc*` tag push). `gates.yml` defines the 4 existing gate jobs single-sourced: backend Gradle check, AI eval `-PdeterministicOnly`, frontend lint/typecheck/unit/build, Playwright e2e (Chromium). `release.yml` additionally runs (a) the Playwright golden-path spec under the `e2e-stub` profile, and (b) the docker-compose load-test job that ends with `:backend:api:loadtestVerify`. `release.yml` aggregates all gate-job results into a single `release-gates-summary` job whose check link is what `LAUNCH-GO-NOGO.md` references. Rationale: zero drift between daily CI and release CI is the user's explicit frustration trigger; new gates added in `gates.yml` propagate to both pipelines automatically.
- **D-11:** Branch protection on `refs/tags/v*.*.*-rc*` is configured so `release-gates-summary` is a required check before the tag can be considered "valid for launch". The tag itself can be pushed without the check (GitHub limitation), but `LAUNCH-GO-NOGO.md` item (b) is unchecked until the summary job goes green.
- **D-12:** Cutting `v1.0.0-rc2` (if needed) requires zero workflow edits — just `git tag -a v1.0.0-rc2 <sha>` and `git push --tags`. The annotated tag's message records the SHA + date + four-suite gate URL (filled in after the gate run completes; the operator runs `gh run view <run-id> --log` to obtain the URL).
- **D-13:** Existing workflows `ci.yml`, `e2e.yml`, `i18n-check.yml`, and `ai-eval` job semantics are preserved. The migration is: (a) extract the 4 existing gate jobs into `gates.yml` with `workflow_call`; (b) slim `ci.yml` to a thin caller (`uses: ./.github/workflows/gates.yml`); (c) preserve `i18n-check.yml` as-is (not in the launch gate); (d) `e2e.yml` either folds into `gates.yml` or is also called via `workflow_call` — planner decides.

### LAUNCH-GO-NOGO.md structure

- **D-14:** `LAUNCH-GO-NOGO.md` lives at `.planning/LAUNCH-GO-NOGO.md` (repo-root planning tree, NOT inside the phase directory). 8 pass/fail checkboxes match SPEC requirement #5 verbatim: (a) Playwright golden-path spec green on RC tag — link to CI run; (b) 50-tenant load test invariants all PASS — link to `06-LOAD-TEST-RESULT.md`; (c) prompt-injection regression suite green on RC tag; (d) ArchUnit suite green on RC tag; (e) Spring Modulith `ApplicationModulesTest` green on RC tag; (f) LLM golden-set drift check green on RC tag; (g) trust-story re-affirmed in writing — auto-send forbidden, no stored bodies/prompts/completions, every triage action undoable; (h) launch mode = OAuth "Testing" (Production move deferred to `SEED-012-casa-restricted-scope-verification.md`). Sign-off line: `✓ signed-off by @<user> on <ISO date>` — committed as the last edit.

### Worker yml verification (resolved-during-discuss)

- **D-15:** The Phase-01.5 follow-up todo "Apply :? fail-fast to backend/worker application.yml refresh-token-key (CR-04 parity)" was inspected during discussion and confirmed **already fixed** at `backend/worker/src/main/resources/application.yml:63`. Phase 6 implementation work: none. Phase 6 verification: D-02's docker-compose stack must inject `REFRESH_TOKEN_KEY_BASE64` into BOTH api and worker containers before bringing them up; missing → fail-fast crash → load test aborts with a clear error rather than silently producing corrupted AES-GCM ciphertext. Todo file should be moved from `.planning/todos/pending/` to `.planning/todos/done/` (or equivalent archive path the project uses) as part of Phase 6 housekeeping.

### Claude's Discretion

- Specific k6 script file layout (one `.js` file vs. per-scenario files), HTML report retention policy, and Playwright spec internal structure (page-object split vs. inline) — planner decides based on existing repo conventions.
- Exact wording of the ArchUnit rules guarding D-03 and D-07 — planner / researcher chooses idiomatic ArchUnit predicates.
- Whether `e2e.yml` folds into `gates.yml` or stays separate as a `workflow_call`-callable workflow — planner decides based on the actual extraction diff.
- Exact CASA lab pick for SEED-012 — defer to the post-launch track when budget + timeline are committed.
- Exact `loadtest` directory structure (`loadtest/scripts/`, `loadtest/compose.loadtest.yml`, `loadtest/README.md`) — planner decides.

### Folded Todos

None. All matched todos were reviewed but not folded — see Reviewed Todos below.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Locked phase requirements
- `.planning/phases/06-polish-casa-verified-launch/06-SPEC.md` — Locked requirements (6 items) + boundaries + 9 acceptance criteria — MUST read before planning.

### Project-level constraints
- `CLAUDE.md` — backend code style (no Lombok, enterprise readability), tech-stack TL;DR, no-spring-cloud-gcp constraint, no real-LLM calls in `./gradlew test`.
- `.planning/REQUIREMENTS.md` — 61 v1 requirements, traceability matrix; FND-07 (CASA) explicitly stays Pending because Phase 6 defers it.
- `.planning/ROADMAP.md` §"Phase 6: Polish & CASA-Verified Launch" — original 5 success criteria; note that SPEC.md narrows the scope (CASA + runbook DEFERRED).
- `.planning/PROJECT.md` — Core value + key-decisions log; the trust story restated in `LAUNCH-GO-NOGO.md` item (g) comes from here.
- `.planning/STATE.md` — 56 plans completed, all phases prior to 6 closed.
- `CONVENTIONS.md` — controllers thin, service-owned `@Transactional`, records for DTOs, Lombok-free, privacy logging format, direct-vs-Modulith-events rule.
- `TESTING.md` — invariants over code shape, Spring Boot 4 slice ladder, three-layer Spring AI testing, `@Tag("llm-eval")` for real-LLM tests.

### Architecture & risk
- `.planning/research/STACK.md` — current stack versions (Spring Boot 4.0.6, Spring AI 2.0.0-M6, Postgres 17.6, k6 / Gatling research already done in this discussion).
- `.planning/research/PITFALLS.md` §"Pitfall 1: Restricted-scope OAuth verification" — explains why CASA matters and what evidence the eventual submission needs; SEED-012 (D-09) must reference this.
- `.planning/research/ARCHITECTURE.md` — current module topology; load-test compose stack mirrors this.

### Phase-1 / phase-2C constraints relevant to load test invariants
- `backend/api/src/test/java/.../MultiTenantLeakIntegrationTest.java` (FND-05) — invariant (a) is the prod-traffic analog of this test.
- Logback scrub filter + `@Sensitive` annotation + ArchUnit FND-04 rule — invariant (c) validates these on real traffic.
- Phase-2B ledger reserve/settle/release flow (BILL-02/03/04) — invariant (b) reconciles against this.

### Inbox-zero reference repo (per project memory)
- `D:\study materials summer 2026\EXE202\inbox-zero` — local clone of the architectural reference repo. Useful only if planner needs Gmail-API stubbing inspiration; do NOT copy patterns verbatim (different stack).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **Playwright config + 22 existing specs** at `apps/web/e2e/` with `playwright.config.ts` already configured (Chromium, baseURL `http://localhost:3000`, `pnpm dev` webServer auto-start). `launch-golden-path.spec.ts` can reuse this config unchanged.
- **`page.route` stub pattern** in 5 specs (`billing-topup`, `byok`, `legal-stubs`, `onboarding-routes`, plus chrome-test-utils helpers). These stay for UI-state coverage per D-06's two-tier taxonomy.
- **`apps/web/e2e/chrome-test-utils.ts`** — likely contains shared chrome/AuthTopBar helpers; check before duplicating session-setup logic.
- **Backend `RestClient + LocalServerPort` test harness** — established project preference for integration tests (per project memory: ScopedValue + TenantContext binding does NOT work with MockMvc). The `e2e-stub` profile beans must NOT break this — they're swapping Gmail/PubsubVerifier, not the test transport.
- **`-PdeterministicOnly` AI eval mode** in `ci.yml` "ai-eval" job — the golden-set drift suite Phase 6 wires into the RC gate is already triggered via this Gradle property. Reuse the same flag in `release.yml`.
- **GitHub Actions concurrency groups + cancel-in-progress** — every existing workflow uses this pattern. `release.yml` should match for consistency.
- **`@TenantId` + Hibernate 7 multi-tenant filter** (Phase 1.2 closure) — load-test invariant (a) leans on this; the SQL query in D-04 just verifies that filter is engaged at the storage layer.
- **`@Sensitive` annotation + Logback scrub filter + ArchUnit FND-04 rule** — load-test invariant (c) validates these run on real prod traffic, not just in tests.

### Established Patterns

- **No `spring-cloud-gcp` anywhere** (project policy). The `loadtest` profile and `e2e-stub` profile MUST NOT pull in `spring-cloud-gcp-*` for OIDC verification — they substitute the verifier bean directly with a Java fake.
- **`@ConditionalOnProperty` + ArchUnit guard** for test-only beans — established in Phase 1.5 P08 (CR-04). Both D-03 and D-07's profile guards follow this pattern.
- **Liquibase YAML changelogs** (`db/changelog/`) — no schema changes expected in Phase 6. If a load-test fixture needs a new column, that's a scope violation (raise as SPEC update first).
- **`docs(<phase>):` commit convention** for planning artifacts, `feat(<area>):` for code, `chore(<area>):` for tooling. `.github/workflows/release.yml` + `gates.yml` introductions go under `ci(release):` per existing repo style.
- **Eclipse Temurin 25 JRE base image** + Spring Boot CDS/AOT layered builds — `compose.loadtest.yml` reuses the same Dockerfile so the prod image is what's tested.

### Integration Points

- **`backend/api/src/main/java/.../security/PubsubVerifier`** (or equivalent class) — bean swap point for the `loadtest` and `e2e-stub` profiles.
- **`backend/api/src/main/java/.../gmail/GmailClient`** (or equivalent adapter) — second bean swap point for `e2e-stub` only (load test exercises Pub/Sub push without calling Gmail back).
- **`backend/api/src/main/java/.../pubsub/PubsubPushController`** — entry point both for Playwright's `request.fetch()` (D-08) and for k6's load traffic.
- **`backend/core/.../ledger/*Service`** — invariant (b) queries against the ledger tables this service writes.
- **`backend/core/.../audit/*Service`** + the `audit_log` table — invariant (a) queries this.
- **`apps/web/e2e/launch-golden-path.spec.ts`** — new file; the only frontend artifact Phase 6 adds.
- **`.github/workflows/gates.yml`** — new file; reusable workflow extracted from `ci.yml`.
- **`.github/workflows/release.yml`** — new file; trigger `on: push: tags: ['v*.*.*-rc*']`.
- **`loadtest/compose.loadtest.yml`** + **`loadtest/scripts/*.js`** — new top-level directory.
- **`.planning/LAUNCH-GO-NOGO.md`** — new file; the launch decision artifact.
- **`.planning/seeds/SEED-012-casa-restricted-scope-verification.md`** — new file; the post-launch CASA tracking seed.

</code_context>

<specifics>
## Specific Ideas

- The launch gate is **invariant-shaped, not throughput-shaped** — every Phase 6 acceptance criterion can be answered with a pass/fail check (a query, a scan, a checkbox), never with a "feels reasonable" judgment. Plan and verify against this property.
- **OAuth Testing mode is a feature, not a workaround**. Vietnam beta is intentionally <100 users; CASA submission is heavyweight (4–12 weeks + lab fees). Phase 6 ships into Testing mode by design. `LAUNCH-GO-NOGO.md` item (h) records this affirmatively.
- The Phase 6 trust story restatement (item (g)) must use the EXACT language from `CLAUDE.md` and `REQUIREMENTS.md`: "auto-send forbidden", "no stored bodies / prompts / completions", "every triage action undoable". Don't paraphrase — verifier matches on these phrases.

</specifics>

<deferred>
## Deferred Ideas

- **Production runbook** (on-call rotation, Pub/Sub backlog recovery playbook, `users.watch` renewal incident playbook, ledger reconciliation playbook) — explicitly deferred from SPEC.md to a post-launch ops track. Becomes a new phase once incident volume justifies it.
- **CASA Tier 2 submission + evidence package + LoA closure + OAuth-consent-screen Production move** — deferred to the post-launch CASA track tracked in `SEED-012-casa-restricted-scope-verification.md` (D-09). FND-07 stays `Pending` until this seed closes.
- **Throughput SLO / p95 latency gate** — not a v1 concern. If Phase 7+ introduces a real perf gate, the natural graduation is Gatling Java DSL on top of the same `compose.loadtest.yml` env.
- **Multi-region / HA / staging VPS** — single-VPS launch is locked. Revisit when traffic justifies it.

### Reviewed Todos (not folded)

- **WR-06 — test-profile SecurityConfig slice for OAuth filter chain coverage** (`.planning/todos/pending/2026-04-28-wr-06-test-profile-securityconfig-slice.md`) — deferred. Adds new test infrastructure; SPEC locks "no new test suites being authored" in Phase 6. Belongs in a Phase 7+ security-hardening track or as a Phase 1.5 follow-up plan.
- **Apply :? fail-fast to backend/worker application.yml refresh-token-key** (`.planning/todos/pending/2026-04-28-worker-application-yml-fail-fast-parity.md`) — **already fixed** (verified at `backend/worker/src/main/resources/application.yml:63` during discussion). Todo file should be moved to the done archive as part of Phase 6 housekeeping (see D-15). Phase 6 verification only: ensure the docker-compose stack supplies `REFRESH_TOKEN_KEY_BASE64` to both api + worker containers.
- **Make backend/core context API surfaces explicit with @NamedInterface** (`.planning/todos/pending/2026-05-12-make-backend-core-context-api-surfaces-explicit-with-namedin.md`) — deferred. Architecture refactor work; not launch-validation. Belongs in a post-launch architecture-hardening phase.

</deferred>

---

*Phase: 6-polish-casa-verified-launch*
*Context gathered: 2026-05-14*
