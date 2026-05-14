---
phase: 6
slug: polish-casa-verified-launch
cycle: 1
reviewers: [opencode]
codex_status: failed (no markdown written; reasoning streamed to stderr but tool exited before completion)
high_count: 2
medium_count: 8
low_count: 8
overall_risk: MEDIUM
created: 2026-05-14
---

# Phase 6 — Cross-AI Plan Reviews (cycle 1)

> Convergence review per `/gsd-plan-review-convergence 6 --codex --opencode`.
> Codex CLI failed to produce a review artifact this cycle (process exited before writing `codex.md`); only OpenCode (model: `deepseek-v4-flash-free`) produced output. Codex retry is recommended once the next cycle starts.

## Reviewer status

| Reviewer | Status | Output bytes | Notes |
|----------|--------|--------------|-------|
| OpenCode (`deepseek-v4-flash-free`) | ✓ complete | 25680 | Full structured review per plan |
| Codex (`cx/gpt-5.5` via 9router) | ✗ no output | 0 | Reasoning streamed for ~5 min then process ended without writing markdown |

## Summary of HIGH concerns (count = 2)

1. **[opencode] HIGH — `MockHttpTransport` classpath issue in 06-01 Task 3** — `E2eStubGmailClient` lives under `backend/api/src/main/java/` but uses `com.google.api.client.testing.http.MockHttpTransport`, which ships in the **test-scope** artifact `google-http-client-testing`. The class will not compile from `src/main/java`. Fix: switch to `NetHttpTransport()` (already on the compile classpath) with a guard that rethrows any outbound connection attempt — the seed-message + in-memory map means the stub never actually issues an HTTP call.
2. **[opencode] HIGH — Seed-message shape mismatch between 06-01 and 06-03** — Plan 06-01 Task 3 declares `seedMessage(String tenantId, String emailAddress, String historyId, String snippet)` for the in-memory store, but the same plan's `SeedMessageRequest` record (the body of `POST /api/test/e2e-stub/seed-message`) is `(String messageId, String threadId, String from, String subject, String body)`. Plan 06-03 Task 2 posts the **second** shape. Without reconciliation, the request will deserialize incorrectly or the stub helper will be called with mismatched arguments. Fix: pick a single shape (recommended: the HTTP-side `(messageId, threadId, from, subject, body)`) and update the stub method signature + `E2eStubGmailClient` internal data model to match.

---

# Cross-AI Plan Review: Phase 6 (Polish & CASA-Verified Launch)

---

## Overall Assessment

**Risk: MEDIUM** — The 5 plans are thorough, well-researched, and properly sequenced. The major concern is a classpath issue with `MockHttpTransport` in `src/main/java/`, plus a few cross-plan coordination gaps that could cause CI failures at gate time rather than at plan-merge time. The wave dependencies (1→2→3) are correct; the SPEC coverage is near-complete (8/9 ACs covered directly, AC #8 is re-verified by existing ArchUnit).

---

## Plan 06-01 — Test-only Spring Profile Scaffolding

### Summary
Extracts `GmailClient` interface and `TokenVerifier` @Bean, then creates four stub beans under `e2e-stub` and `loadtest` profiles with belt-and-suspenders guards. ArchUnit rules and YAML-scan test provide the third and fourth guard layers. Well-structured but has a classpath blind spot.

### Strengths
- **Belt-and-suspenders is correctly designed**: `@Profile` + `@ConditionalOnProperty` + ArchUnit rule A/B + YAML-scan Rule C. Four independent guards must all fail for a stub to load in prod.
- **HIGH-4 self-sufficiency**: `application-e2e-stub.yml` explicitly declares literal placeholders for every `:?` env var, avoiding host-side env-var dependencies. The grep-gate `grep -c '\${.*:?.*}'` returning 0 is a clear, verifiable criterion.
- **OQ-2/OQ-3 resolution is pragmatic**: Extracting `TokenVerifier` as a @Bean and `GmailClient` as an interface are small refactors with clean seams.
- **Task separation is clean**: 5 tasks that can be committed independently without breaking intermediate compilation states (if ordered correctly: interface → TokenVerifier → e2e-stub beans → loadtest beans → ArchUnit).

### Concerns

| Severity | Issue |
|----------|-------|
| **HIGH** | **`MockHttpTransport` classpath issue**: Task 3 puts `E2eStubGmailClient` under `src/main/java/` but uses `com.google.api.client.testing.http.MockHttpTransport` — a **test-scope** artifact (`google-http-client-testing`). This will NOT compile because `src/main/java` code cannot see test dependencies. The plan must either (a) add `google-http-client-testing` as an `implementation` dependency (pollutes prod artifact), (b) use `NetHttpTransport()` from the main `google-http-client` jar (risk of outbound calls), or (c) use a different approach entirely (e.g., a Gmail client that returns 200 without any transport). |
| **MEDIUM** | **`E2eStubPubsubVerifierConfig` Mockito dependency**: The note "If subclassing is awkward... use Mockito.mock(...)" has the same classpath problem — Mockito is test-scope, `src/main/java` can't import it. Recommend committing to the subclass approach from the start; `TokenVerifier` is NOT final and its `verify(String)` is public+non-final, so subclassing works. |
| **MEDIUM** | **`LaunchProfileArchUnitTest` references `application-prod.yml`** in the YAML-scan Rule C but notes "may not exist in all environments; skip silently." If `application-prod.yml` is ever added later with a test-profile activation, the silent-skip means the rule won't catch it. Recommend explicitly listing which `application-*.yml` files exist (verifiable by glob at build time). |
| **LOW** | **`E2eStubGmailClient` seedMessage helper signature**: The plan defines `seedMessage(String tenantId, String emailAddress, String historyId, String snippet)` but the `FakeMessage` record is a static nested type that's never defined as a standalone data type. The `seed-message` endpoint accepts `SeedMessageRequest(messageId, threadId, from, subject, body)` — the parameter counts mismatch. Plan 06-03 calls seed with `messageId, threadId, from, subject, body`, but the `E2eStubGmailClient.seedMessage(...)` takes `tenantId, emailAddress, historyId, snippet`. These need to be reconciled. |

### Suggestions
1. **Replace `MockHttpTransport` with `NetHttpTransport()`** — it's on the compile classpath and the e2e-stub Gmail client won't be called for outbound requests (pre-seeding + stub data means all data comes from the in-memory map). Add a belt: wrap in a try-catch that rethrows any connection-exception with a clear message that e2e-stub shouldn't make outbound calls.
2. **Pin the `TokenVerifier` subclass approach** — remove the Mockito fallback text. A 30-line inner class that extends `TokenVerifier` and overrides `verify(String)` to return a synthetic `JsonWebSignature` is simpler and avoids the classpath issue. The plan's own research §7.9 supports this.
3. **Reconcile the `SeedMessageRequest` shape**: The e2e-stub reset controller's `POST /api/test/e2e-stub/seed-message` accepts `(messageId, threadId, from, subject, body)` but the GmailClient stub's `seedMessage(...)` takes `(tenantId, emailAddress, historyId, snippet)`. These must match. Recommend making the HTTP endpoint accept the same fields and have the stub map them to the Gmail message structure internally.
4. **Make Rule C scan all existing `application-*.yml` files via glob** rather than hard-coding paths, so newly added profile ymls are automatically covered.

---

## Plan 06-02 — k6 + Docker-Compose Load Harness

### Summary
Creates a complete load-harness: k6 script with `constant-arrival-rate` for 50 tenants × ~10 msg/min, a 4-service docker-compose stack with healthcheck gating, a `loadtestVerify` Gradle task with three invariant assertions, and operator documentation. OQ-1 is resolved to `bootBuildImage`.

### Strengths
- **Invariant-driven, not throughput-driven**: The load test gates on three attestations (cross-tenant audit, ledger drift, log-bleed) rather than a p95 SLO. This is the right approach per SPEC decision.
- **Prod-config log verification**: The `docker compose logs` capture loads the prod `logback-spring.xml`, which is the only way to validate the scrub filter under real traffic — Testcontainers would load the test logback and false-green invariant (c).
- **Compose healthcheck chain is correct**: `postgres → healthy → api → healthy → worker`. The Liquibase-double-run pitfall (§7.1) is mitigated by `worker depends_on: api: condition: service_healthy`.
- **D-15 fail-fast validation**: The REFRESH_TOKEN_KEY_BASE64 injection + the negative-control section in README provides both proactive (env var set) and reactive (unset = fail) verification.
- **Three-invariant queries are correct SQL**: The `triage_audit` tenant filter, `credit_ledger_entry` sum-group-by, and regex log scan are verified against the actual entity classes in the repo.

### Concerns

| Severity | Issue |
|----------|-------|
| **MEDIUM** | **No pre-run cleanup of compose state**: If the previous load test run crashed before the `docker compose -f loadtest/compose.loadtest.yml down -v` step (e.g., GHA runner OOM), the next run on a reused runner would find stale containers. GHA runners are ephemeral, but if this runs on a self-hosted or non-ephemeral runner, it's a gap. |
| **MEDIUM** | **Worker processing not explicitly awaited**: The k6 script fires Pub/Sub pushes at 500 iter/min. The api ingests these and enqueues work via the outbox. The worker processes asynchronously. The invariant queries run after k6 finishes, but there is no explicit wait for the worker to drain. If the worker is still processing when `loadtestVerify` runs, the `triage_audit` rows may not have been written yet, producing a false PASS. |
| **LOW** | **45-minute timeout is optimistic for GHA**: Building 2 OCI images via `bootBuildImage` (paketobuildpacks cold-start = 3-8 min each), compose pull + health checks (2 min), 10-min k6 run, log capture + JDBC queries (30s) = ~20-30 min sustained. 45 min is sufficient, but `release.yml` should also set `timeout-minutes: 60` for safety margin. |
| **LOW** | **`loadtest/run/run.log` path assumption**: The Gradle task reads `rootProject.file("loadtest/run/run.log")`. The release.yml job creates this via `mkdir -p loadtest/run` then `docker compose logs ... > loadtest/run/run.log`. But the `working-directory` for the `mkdir` step isn't specified, and default GHA workspace is the repo root. If the job happens to be configured with a different working directory, the path breaks. The `mkdir` step should use an explicit `working-directory: ${{ github.workspace }}`. |

### Suggestions
1. **Add a pre-run cleanup step**: `docker compose -f loadtest/compose.loadtest.yml down -v || true` before the up step. Harmless on fresh runners, essential on non-ephemeral ones.
2. **Add an explicit worker-drain wait**: After k6 completes and before `loadtestVerify`, run a polling loop: `while ./gradlew :backend:api:loadtest:checkOutbox; do sleep 3; done` (or a simpler `curl` against an actuator endpoint that reports queue depth). Without this, an unlucky race between worker completion and invariant query could produce a false PASS.
3. **Rename `loadtestVerify` job to `loadtest-verify` in `release.yml`**: The hyphen convention is more consistent with existing GHA job names. (Minor, but consistency matters.)
4. **Pin `working-directory` on the `mkdir` and log-capture steps** in `release.yml` to avoid path assumptions.

---

## Plan 06-03 — Playwright Golden-Path Spec

### Summary
Adds `launch-golden-path.spec.ts` — a single Playwright spec covering the 9-step v1 golden path under the e2e-stub Spring profile. Migrates `playwright.config.ts` `webServer` to a two-entry array. Uses `request.fetch()` for the Pub/Sub leg per D-08.

### Strengths
- **Two-tier taxonomy preserved**: Existing 22 specs continue with `page.route()` for UI-state coverage; only `launch-golden-path.spec.ts` uses the real-backend-under-e2e-stub approach. This is the design called for in D-06.
- **Pre-seed before Pub/Sub push**: The spec seeds the stub Gmail client with `POST /api/test/e2e-stub/seed-message` BEFORE posting the synthetic Pub/Sub envelope. This is the correct sequencing — the controller fetches the message from Gmail, which returns the pre-seeded data.
- **LOW-14 draft path acknowledged**: The spec asserts the draft was QUEUED (202/queued-state) rather than asserting preview text, since `spring.ai.model.chat: none` means no ChatModel is wired. This is a documented, considered tradeoff.
- **Hard-coded absolute URL for backend**: `http://localhost:8080/...` avoids the `use.baseURL` trap (which points at the frontend's 3000).
- **Negative-control criterion**: SPEC AC #1 requires verification by introducing a deliberate regression. The plan includes this as a manual step in Task 2's acceptance criteria.

### Concerns

| Severity | Issue |
|----------|-------|
| **HIGH** | **Reconciled `seed-message` shape gap with Plan 06-01**: Plan 06-03 calls `request.post('/api/test/e2e-stub/seed-message', { data: { messageId: 'gmail-msg-1', threadId: 't1', from: 'sender@example.com', subject: 'Welcome', body: 'Hello tester' } })` but Plan 06-01 Task 3's `E2eStubResetController` defines the endpoint as accepting `SeedMessageRequest(String messageId, String threadId, String from, String subject, String body)` — the field names DON'T match: Plan 06-01 uses `messageId` but the stub seed method is `seedMessage(String tenantId, String emailAddress, String historyId, String snippet)`. The plan MUST resolve this shape mismatch before execution. |
| **MEDIUM** | **No explicit `await` on backend readiness before `request.post('/api/test/e2e-stub/reset')`**: Playwright's `webServer` array polls `/actuator/health/readiness` for the backend, but the very first `request.post(...)` in the spec happens inside a `test()` callback, not a `beforeAll`. If the webServer health check passes but the e2e-stub beans haven't finished wiring (e.g., Liquibase still running on a cold start), the reset POST could get a 503/404. The spec should add a retry loop (or a `beforeAll` that polls a simpler endpoint). |
| **MEDIUM** | **`seedAuthenticatedSession` shares cookies between browser and `request`?**: The `request` fixture's `APIRequestContext` does NOT share cookies with `page` by default (documented in research §2.3). But `seedAuthenticatedSession` adds cookies via `page.context().addCookies(...)`, which only affects browser-bound requests. The `request` POSTs to `/internal/pubsub/gmail` and `/api/test/e2e-stub/` do NOT carry the session cookie. This is OK for the Pub/Sub endpoint (it uses OIDC Bearer auth, not cookies), and OK for the e2e-stub endpoints (they're unauthenticated behind the profile guard). But if any of these endpoints later gain cookie-based auth, the spec will break silently. Recommend adding an explicit comment in the spec explaining this design assumption. |
| **LOW** | **No `test.step('...')` blocks**: The criteria require ≥5 step markers, but the spec body only uses comment blocks. Playwright's `test.step(name, async () => { ... })` provides structured reporting in the HTML report and trace viewer. Recommend using `test.step` for each of the 9 journey steps rather than comments. |
| **LOW** | **`historyId` from `Date.now()` is non-monotonic under high concurrency on single runner**: Not an issue for a single spec run, but worth noting that the e2e-stub doesn't deduplicate by historyId (the real Pub/Sub does). If the spec is retried rapidly, a stale historyId could collide. Recommend using a UUIDv4 or incrementing counter instead. |

### Suggestions
1. **Resolve the seed-message shape inconsistency with Plan 06-01**: The two plans speak different shapes for the same endpoint. This must be reconciled before either is executed.
2. **Add a retry wrapper on the first `request.post('/api/test/e2e-stub/reset')`**: Use `expect.poll(async () => { ... }).toPass()` or a manual loop with 3 retries + 500ms backoff. The backend should be ready after webServer, but defensive coding costs nothing.
3. **Add `test.step()` wrappers for each of the 9 journey steps** — this provides structured reporting and makes the spec self-documenting.
4. **Document the cookie-sharing assumption** in a comment at the top of the spec, explaining why `request.fetch()` works without session cookies for the e2e-stub and Pub/Sub endpoints.

---

## Plan 06-04 — GHA CI Refactor + Release Pipeline

### Summary
Extracts 4 gate jobs into a reusable `gates.yml` (`workflow_call`), slims `ci.yml` to a thin caller, deletes `e2e.yml`, creates `release.yml` with tag-triggered gating that adds golden-path + loadtest + trust-story-grep + aggregator jobs. D-13 resolved: `e2e.yml` is folded in.

### Strengths
- **Zero drift design**: Single-sourced `gates.yml` means both daily CI and release CI run identical job definitions. Any change to `gates.yml` propagates automatically — exactly what D-10 / D-13 mandates.
- **`cancel-in-progress: false` on release.yml is correct**: Tagged runs are explicit launch checkpoints; cancelling one because a newer rc tag was pushed would corrupt the launch record.
- **`trust-story-grep` is a welcome safety net**: Adding literal grepping of the three verbatim phrases from LAUNCH-GO-NOGO.md as a 4th gate job prevents accidental paraphrase at RC time.
- **Tag filter `'v*.*.*-rc*'` is correct syntax**: Verified by research §3.2. Matches rc1, rc2, rc12 but not v1.0.0 (final) or v1.0.0-beta.
- **Aggregator pattern is idiomatic**: `needs: [...] + if: always()` + `needs.*.result` check is the documented GitHub best practice.

### Concerns

| Severity | Issue |
|----------|-------|
| **MEDIUM** | **`secrets: inherit` on `gates.yml` callers**: Both `ci.yml` and `release.yml` use `secrets: inherit`. This exposes ALL repo secrets to the reusable workflow. Most of these secrets are harmless (OAuth client IDs, API keys that are already accessible via the existing `ci.yml`), but the `REFRESH_TOKEN_KEY_BASE64`-equivalent secret would also be inherited. This is the same risk surface as the current `ci.yml`, so it's not a regression, but it should be documented. |
| **MEDIUM** | **Release.yml golden-path job starts Spring Boot TWICE**: The job runs `docker compose up -d --wait` (dev Postgres + Redis), THEN runs `playwright test launch-golden-path.spec.ts` which triggers the Playwright `webServer` array that starts Spring Boot via `bootRun`. Spring Boot also starts Liquibase against the same Postgres. If the dev compose is still initializing when Playwright starts Spring Boot, the Liquibase connection could fail. The webServer health check (`/actuator/health/readiness`) handles this by design (it waits for readiness), but the race condition is less tight if the compose `--wait` completes first. The ordering is correct as written, but worth noting in the SUMMARY. |
| **LOW** | **`trust-story-grep` runs even when `loadtest` or `golden-path` already failed**: The aggregator uses `if: always()` on `trust-story-grep`, which is redundant — the aggregator ALREADY runs on `if: always()` and checks the result of all upstream jobs. The `trust-story-grep` job could use a conditional like `if: ${{ always() && needs.loadtest.result == 'success' }}` to skip if the main loadtest gate already failed (saving runner time). Minor nitpick. |
| **LOW** | **Dependency: 06-04 `depends_on: [06-02, 06-03]` but neither plan has a SUMMARY-derived output that 06-04 consumes**. The dependency is correct conceptually (the jobs and scripts that release.yml references must exist), but neither Plan 06-02 SUMMARY nor 06-03 SUMMARY produces a machine-readable contract. If 06-02's Gradle task name changes during execution, 06-04 won't know. This is inherent in the GSD model (plans are text artifacts, not code), but worth noting. |

### Suggestions
1. **Add `working-directory: ${{ github.workspace }}` on each `run:` step in the golden-path and loadtest jobs** that assumes `cwd == repo root`, to prevent path resolution issues.
2. **Document the `secrets: inherit` scope** in an inline comment in both `ci.yml` and `release.yml`.
3. **Remove the redundant `if: always()` on `trust-story-grep`** — let it fail fast if gates already failed, saving runner minutes.
4. **Add a `pull_request` trigger for `release.yml`** that runs on PRs modifying `.github/workflows/release.yml` or `gates.yml`, so pipeline changes are validated in PR before tag-push time. Currently, the only way to validate release.yml is to push a tag, which is high-friction.

---

## Plan 06-05 — Launch Artifacts

### Summary
Creates LAUNCH-GO-NOGO.md with 8 unchecked boxes and verbatim trust-story phrases, SEED-012 CASA tracking seed, archives the resolved Phase-1.5 todo, and provides a detailed operator runbook for cutting the v1.0.0-rc1 tag. Task 4 is a checkpoint (human action).

### Strengths
- **Trust-story phrase verification is thorough**: The acceptance criteria use a Node verifier script that checks the THREE phrases co-occur INSIDE item (g)'s block, not just file-wide. This prevents a scenario where the phrases appear somewhere else in the document but not in item (g).
- **SEED-012 is comprehensive**: 8 H2 sections covering why, trigger, scope, evidence package, CASA labs, safety rules, closure trigger, and breadcrumbs. The `scope: large` and `trigger_when` are correctly set based on research §4.1.
- **Runbook is comprehensive but not over-prescriptive**: The 9-step operator procedure covers tag cut, workflow monitoring, URL capture, box-checking, sign-off commit, tag annotation enrichment, and post-hoc verification. It also handles the failure case (`no-go: <reason>` resume signal).
- **Todo archive via `git mv` preserves history**: The resolved Phase-1.5 follow-up is moved, not copy-then-delete, preserving attribution and original commit date.

### Concerns

| Severity | Issue |
|----------|-------|
| **LOW** | **No STATE.md update after sign-off**: After Task 4 completes, Phase 6 should mark itself complete in `.planning/STATE.md`. The plan doesn't include this step. Minor oversight — `STATE.md` updates are typically handled by the overall workflow, but the operator runbook should mention it. |
| **LOW** | **Tag annotation enrichment (Step 8) is risky**: Deleting and re-pushing an annotated tag (even pointing at the same SHA) creates a window where a downstream consumer could have pulled the old tag. If someone pulls between `git push origin :refs/tags/v1.0.0-rc1` and `git push origin v1.0.0-rc1`, they have a stale tag. Recommend enriching the tag before the first push (Step 3), not after. The gate URL can be recorded in LAUNCH-GO-NOGO.md item (a) without being in the tag annotation. |
| **LOW** | **Sign-off line uses Unicode `✓`**: Cross-platform compatibility — `✓` renders differently on Windows vs macOS vs terminal vs browser. The `grep` assertion uses `grep -F` which matches binary bytes, so this works at the automation level. But for human readability, recommend documenting this choice. |

### Suggestions
1. **Skip the tag deletion/re-push (Step 8)**: Instead, push the annotated tag ONCE with the SHA + date, and record the gate URL ONLY in `LAUNCH-GO-NOGO.md`. This is simpler, lower-risk, and still satisfies D-12 (tag message records SHA + date; D-12 doesn't mandate the gate URL in the tag message).
2. **Add a `STATE.md` update step** to the operator runbook: `docs(06): close Phase 6 — v1.0.0-rc1 launched in OAuth Testing mode`.
3. **Add a `--grep` note in the sign-off Run: If this is executed on Windows, confirm the ✓ character renders correctly in the terminal.

---

## Cross-Plan Analysis

### SPEC AC Coverage

| AC | Plans | Status |
|----|-------|--------|
| AC #1 (golden-path spec green) | 06-03, 06-04 | ✅ |
| AC #2 (load test + 3 invariants PASS) | 06-02, 06-04 | ✅ |
| AC #3 (4 regression suites green) | 06-04 | ✅ |
| AC #4 (annotated tag on main) | 06-05 Task 4 | ✅ (operator action) |
| AC #5 (LAUNCH-GO-NOGO.md with 8 checks + sign-off) | 06-05 Task 1, 4 | ✅ |
| AC #6 (item (h) = OAuth Testing) | 06-05 Task 1 | ✅ |
| AC #7 (post-launch CASA artifact) | 06-05 Task 2 | ✅ |
| AC #8 (no auto-send) | 06-01 (ArchUnit re-verifies) | ✅ (implicit) |
| AC #9 (no new long-term storage) | 06-02 (invariant c), 06-01 (ArchUnit) | ✅ |

**Coverage: 9/9 — complete.**

### Wave Dependency Correctness

```
Wave 1: 06-01 (spring profile infra)
           ├──> Wave 2: 06-02 (k6 + compose load harness)  [depends_on: 06-01]
           └──> Wave 2: 06-03 (Playwright golden-path)     [depends_on: 06-01]
                       └──> Wave 3: 06-04 (GHA CI)         [depends_on: 06-02, 06-03]
                                   └──> Wave 3: 06-05 (launch artifacts) [depends_on: 06-04]
```

This is correct. 06-01 must land before 06-02 and 06-03. Both 06-02 and 06-03 must land before 06-04. 06-04 must land before 06-05 (since the gate URL references the release.yml workflow).

### Inter-Plan Coordination Gaps

1. **HIGH: Classpath dependency between 06-01 and test-scope libraries** — `MockHttpTransport` (06-01 Task 3) needs `google-http-client-testing` on the `implementation` classpath for the `src/main/java` stub to compile. This is the single highest-risk issue across all 5 plans.

2. **MEDIUM: Seed-message shape inconsistency between 06-01 and 06-03** — The `POST /api/test/e2e-stub/seed-message` endpoint's field names differ between the two plans. This will produce a runtime 400 or deserialization failure.

3. **MEDIUM: No explicit worker-drain wait before invariant queries** (06-02 + 06-04). The load test triggers async processing in the worker. If the invariant queries run before the worker finishes, the `triage_audit` table won't have all rows.

4. **LOW: No CI-path validation for `release.yml`** — Changes to `release.yml` or `gates.yml` cannot be validated before merge without pushing a tag. A `pull_request` trigger on path changes would catch this.

### Security Assessment

**No test beans in prod** — The four-guard system (`@Profile` + `@ConditionalOnProperty` + ArchUnit Rule A/B + YAML-scan Rule C) is robust. The classpath issue above would be a compile-time failure, not a runtime escape, so even in the worst case, the stub classes wouldn't load.

**D-15 fail-fast verified** — The worker's `:?` property for `REFRESH_TOKEN_KEY_BASE64` at `application.yml:63` is confirmed present. The compose stack injects it to both containers. The negative-control test (unset → boot crash) demonstrates the guard actively asserting.

**Auto-send remains architecturally blocked** — AC #8 is re-verified by the existing `DraftPathArchUnitTest` which runs as part of `Gates / backend`. No new code paths touch the Gmail send API.

**Trust-story coverage** — 3 verbatim phrases, checked at both plan-merge time (06-05 acceptance criteria) and at RC-tag time (`trust-story-grep` job in `release.yml`).

---

## Final Verdict

**Overall Risk: MEDIUM**

The plans are structurally sound, well-researched, and properly sequenced. The two HIGH-severity concerns (classpath issue in 06-01, shape mismatch between 06-01 and 06-03) are fixable before execution — they're coordination gaps, not design flaws. The MEDIUM concerns (worker drain timing, CI validation gaps) are real but have straightforward mitigations.

**Recommend approving all 5 plans with the following conditions:**

1. **06-01 must use a classpath-safe Gmail transport** (replace `MockHttpTransport` with `NetHttpTransport()` or comparable, dropping the test-scope dependency).
2. **06-01 and 06-03 must be executed sequentially** (not in parallel), with 06-01's `seed-message` endpoint shape finalized before 06-03's spec references it.
3. **06-02 must add a worker-drain polling loop** before the invariant queries.
4. **06-04 must pin `working-directory`** on all `run:` steps in golden-path and loadtest jobs.
5. **06-05 must skip the tag deletion/re-push** in Step 8 and enrich the tag annotation before the first push.

---

## Codex output (cycle 1) — empty

Codex CLI was launched against the same prompt (`/tmp/gsd-review-phase6/prompt.md`) but never produced output to `codex.md` before the spawning agent's monitor timeout. Codex's reasoning stream (`codex.err`, ~330KB) shows it was actively analysing the plans at termination — implying the time budget was exhausted rather than a hard failure. Codex retry is recommended for cycle 2.
