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

---

## Codex output (cycle 1) — LATE delivery

> Codex did finish reasoning ~6 min after the cycle-1 replan was triggered. The replan therefore did NOT see Codex's findings. Recording them here so cycle 2 can compare them against the current plan state. Codex raised **8 HIGH concerns** that overlap only partially with OpenCode's.

## Summary

The Phase 6 plan set is directionally strong: it maps the launch-readiness goal into concrete artifacts, separates Wave 1 test seams from Wave 2 validation harnesses and Wave 3 release evidence, and keeps CASA correctly deferred. However, several HIGH-risk issues could prevent the plans from actually satisfying the locked SPEC acceptance criteria. The biggest gaps are around false or incomplete E2E coverage, load-test invariant correctness, CI ordering/tag evidence circularity, and test-profile isolation mechanics that may not work as written.

## Strengths

- Clear dependency structure: 06-01 enables 06-02 and 06-03; 06-04 wires both into release CI; 06-05 captures decision artifacts.
- Good security posture for test-only profiles: `@Profile` + `@ConditionalOnProperty` + ArchUnit/YAML guards is the right shape.
- CASA boundary is handled correctly: OAuth remains in Testing mode, with a deferred SEED-012 track.
- CI single-sourcing via `gates.yml` is well-designed and directly addresses release/daily-gate drift.
- Load test focuses on invariants instead of p95/throughput vanity metrics, matching the SPEC.
- Trust-story phrases are treated as load-bearing evidence and wired into release checks.

## Concerns

### HIGH

- **06-01: `GmailClient` interface may not actually swap production Gmail use.** If existing services inject `GmailApiClientFactory` concretely, adding an interface and a `@Primary GmailClient` fake will not affect them. The plan explicitly avoids changing consumers, which likely means the fake is unused.

- **06-01: `TokenVerifier` fake strategy is fragile.** Subclassing Google’s `TokenVerifier` may not be possible, and using Mockito from `src/main/java` is not acceptable unless Mockito is a production dependency, which it should not be.

- **06-03: Golden path does not truly cover Google OAuth sign-up.** `seedAuthenticatedSession(page)` bypasses OAuth and may not create a real Spring Session in Redis. SPEC requires sign-up via stub, not a hand-written browser cookie.

- **06-03: Draft acceptance is weakened below SPEC.** The plan allows asserting “draft queued” because `spring.ai.model.chat: none`, but SPEC requires request AI draft → confirm draft saved in stub Gmail. This needs an e2e stub `ChatModel` and Gmail draft fake, or the acceptance criterion is not met.

- **06-03 / 06-04: Global Playwright `webServer` change likely breaks existing E2E CI.** If `playwright.config.ts` always starts Spring Boot, then the normal `gates.yml` E2E job now needs Postgres/Redis, but 06-04’s copied E2E job does not start `docker compose`.

- **06-02: Load-test tenant ID invariant may not match schema reality.** Querying `tenant_id::text LIKE 'loadtest-tenant-%'` is likely invalid if tenant IDs are UUIDs. This could make the load test impossible or produce misleading results.

- **06-02: Ledger invariant is oversimplified.** `SUM(amount_credits) = 0` may not represent “ledger balance + reserves + settlements reconcile to zero drift.” It risks false failures or false passes depending on how reserve/settle/release entries are modeled.

- **06-02: `loadtestVerify` JDBC may fail from Gradle script classpath.** Project runtime dependencies are not automatically available to `build.gradle.kts` task code. `DriverManager` may not find the PostgreSQL driver.

- **06-02 / 06-05: committed load-test result artifact is unresolved.** `06-LOAD-TEST-RESULT.md` is generated in CI after a tag push, but the SPEC requires the report committed. The current flow does not clearly commit the actual result before the RC tag.

- **06-04 / 06-05: RC tag and sign-off flow is circular.** The release run URL is only known after the tag exists, but `LAUNCH-GO-NOGO.md` needs that URL and sign-off committed. The current Task 4 appears to tag a commit before the signed file exists, then sign a later commit, leaving the tag not pointing at the signed launch decision.

### MEDIUM

- **06-01: YAML grep acceptance conflicts with nested YAML.** `zeromail.e2e-stub.enabled: true` will not appear literally if written as nested YAML, so the acceptance checks will fail despite valid configuration.

- **06-01: “exactly three Java files” conflicts with optional sibling `FakeMessage` record.** Keep the fake record nested or update the acceptance criteria.

- **06-02: app image healthcheck uses `curl`.** Buildpack images may not contain `curl`, causing Compose health checks to fail even when the app is healthy.

- **06-02: `handleSummary` writes to `loadtest/run/summary.json` before ensuring `loadtest/run/` exists.** CI should `mkdir -p loadtest/run` before `k6 run`.

- **06-02: log-bleed regex is both too broad and too weak.** It may flag harmless startup logs containing “prompt” while missing actual leaked synthetic body text. Canary strings are safer.

- **06-04: deleting `e2e.yml` can break existing branch protection/status-check names.** The plan should include updating required checks or preserving a compatibility workflow.

- **06-04: golden-path job starts root compose but does not tear it down.** Runners are ephemeral, but an `if: always()` teardown keeps logs and failures cleaner.

- **06-05: retagging by deleting and force-recreating `v1.0.0-rc1` is risky.** Annotated release tags should be immutable once published unless the process explicitly allows replacement.

### LOW

- Some verification commands use Unix tools (`grep`, `wc`, `test`, `yq`) in plans for a Windows-based repo context. GitHub runners are Linux, but local executor instructions should be cross-platform or clearly CI-only.
- Several “manual negative-control” checks are useful but should not be required for every execution unless the workflow explicitly supports scratch branches.
- `LAUNCH-GO-NOGO.md` placeholder uses a non-ASCII checkmark, but this is acceptable because the SPEC requires that exact sign-off shape.

## Suggestions

- Refactor all Gmail outbound consumers to inject `GmailClient`, or instead make the e2e fake override the concrete bean type that production code currently injects.
- Replace Google `TokenVerifier` direct faking with a project-owned interface, e.g. `PubSubTokenVerifier`, returning a small internal verified-token record. Keep Google library details inside the production adapter.
- Add an explicit e2e auth stub endpoint or OAuth stub flow that creates a real server session, instead of relying on `seedAuthenticatedSession`.
- Add an `E2eStubChatModel` with canned draft output and make the stub Gmail client support draft creation/readback. Then assert draft saved, not merely queued.
- Split Playwright configs: keep existing `playwright.config.ts` for normal UI specs and add `playwright.golden.config.ts` or an env-gated backend webServer for the golden path only.
- Redesign load-test tenant modeling around actual tenant IDs. Use fixed UUIDs or seed tenants in DB, then verify against an allow-list and a `loadtest_run_id`/timestamp window.
- Replace ledger `SUM(amount_credits)=0` with the real accounting invariant from the billing model: opening balance + top-ups - settled charges - active reserves = closing balance per tenant.
- Implement `loadtestVerify` as a small Java/Kotlin class run with project runtime classpath, or add an explicit Gradle configuration for the PostgreSQL JDBC driver.
- Use privacy canaries in synthetic message bodies/prompts/drafts and assert those canaries do not appear in logs, in addition to keyword regexes.
- Resolve the RC evidence cycle. A cleaner approach is:
  - Commit launch doc with stable evidence links that do not require a run ID, or link to the tag workflow query page.
  - Cut `v1.0.0-rc1` on the final signed commit.
  - Let `release.yml` validate that exact commit.
  - Avoid retagging after publication.
- Decide whether `06-LOAD-TEST-RESULT.md` is committed pre-tag from a local/staging run or treated as a CI artifact. If SPEC requires committed evidence, add an explicit task before tagging.

## Risk Assessment

**Overall risk: HIGH.**

The plan architecture is good, but several execution details can make the launch gates false-green or fail to run at all. The most important SPEC gaps are the incomplete golden path, the draft-saved downgrade, incorrect assumptions about tenant IDs and ledger reconciliation, and the circular tag/sign-off evidence flow. Fixing those should bring the plan set down to MEDIUM risk, mostly normal release-infrastructure complexity.
---

# Phase 6 — Cycle 2 Review

**Reviewers run:** opencode (✓ success, 9509 bytes), codex (✗ no markdown — process killed before producing structured output, only reasoning streamed to stderr; same failure mode as cycle 1).

## Cycle 2 — OpenCode Verdict

I've analyzed all 5 revised plans. Here's my Cycle 2 review:

# Cross-AI Plan Review — Phase 6 CYCLE 2

---

## 1. HIGH Issue Verification

### HIGH-1: MockHttpTransport classpath issue — **FULLY RESOLVED**

Plan 06-01 Task 3 specifies a concrete hand-rolled `HttpTransport` anonymous subclass:
- `E2eStubGmailClient` uses `new com.google.api.client.http.HttpTransport() { ... }` with `buildRequest()` throwing `IOException` — `HttpTransport` is on the compile classpath via `google-http-client`
- `E2eStubPubsubVerifierConfig` uses a hand-written anonymous `TokenVerifier` subclass — no Mockito
- `LoadtestPubsubVerifierConfig` copies the same pattern

Acceptance criteria include executable grep gates that will fail the build:
- `grep -RnE 'org\.mockito|MockHttpTransport|google-http-client-testing' backend/api/src/main/java/com/zeromail/api/{e2estub,loadtest}/` → 0
- `grep -c 'MockHttpTransport' backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubGmailClient.java` → 0
- `grep -c 'new com.google.api.client.http.HttpTransport()'` → ≥ 1

The `must_haves` truths also assert the no-test-scope-grep property. The `Task 2` action explicitly forbids Mockito or `google-http-client-testing` on the prod classpath. This is well-covered.

### HIGH-2: SeedMessageRequest shape mismatch — **FULLY RESOLVED**

Both plans canonically agree on the 6-field record `(tenantId, messageId, threadId, from, subject, body)`:

- **Plan 06-01** `SeedMessageRequest.java` declares exactly these 6 fields with clear javadoc: *"Body shape is locked: Plan 06-03 launch-golden-path.spec.ts MUST post a JSON object with exactly these 6 keys."*
- **Plan 06-01** `E2eStubGmailClient.seedMessage(...)` consumes the single-arg record form — the legacy 4-arg `(tenantId, emailAddress, historyId, snippet)` is dropped entirely
- **Plan 06-03** Step 4 constant and `data:` literal use all 6 field names; acceptance criteria greps for each field name (≥ 6 matches)
- Both plans carry sync-comments (`"SeedMessageRequest record in"` / `"Sync both sides if either changes"`)
- Plan 06-01 `must_haves` explicitly states "this is the SAME shape Plan 06-03 posts (HIGH-2 reconciliation)"

---

## 2. Cycle 1 MEDIUM/LOW Spot-Check

I cycled through all previously raised concerns against the revised plans:

| Cycle-1 Tag | Status in Rev 2 | Notes |
|---|---|---|
| **MED-3** worker drain race (06-02) | ✅ Fixed | `wait-for-worker-drain.sh` polls `processing_job`; documented in `release.yml` ordered between k6 and `loadtestVerify` |
| **MED-4** stale compose state (06-02) | ✅ Fixed | `docker compose ... down -v \|\| true` is first step in runbook + `release.yml` |
| **MED-5** Rule C hard-coded paths (06-01) | ✅ Fixed | `production_ymls_do_not_activate_test_profiles` globs via `Files.list`, not hard-coded paths |
| **MED-6** working-directory pins (06-04) | ✅ Fixed | Every `run:` step in golden-path + loadtest jobs sets `working-directory: ${{ github.workspace }}` |
| **MED-7** tag delete/re-push (06-05) | ✅ Fixed | Operator runbook: "push EXACTLY ONCE" with pre-enriched annotation; no delete-and-re-push |
| **MED-8** secrets:inherit warning (06-04) | ✅ Fixed | All 3 workflow files carry inline warning comment |
| **MED-10** git_artifacts block (06-05) | ✅ Fixed | `git_artifacts` block on Task 4 captures the tag mutation |
| **LOW-9** no `test.step()` blocks (06-03) | ✅ Fixed | Exactly 9 `test.step()` blocks required (grep-count gate) |
| **LOW-11** `Date.now()` historyId (06-03) | ✅ Fixed | `crypto.randomUUID()` required (grep-gate: `Date.now()` → 0, `crypto.randomUUID()` → ≥ 1) |
| **LOW-12** no retry on first POST (06-03) | ✅ Fixed | `expect.poll(...).toBeTruthy()` with 3 × 500ms intervals and 3s timeout on reset POST |
| **LOW-13** cookie-sharing assumption (06-03) | ✅ Fixed | File-header comment block documents `request` fixture doesn't share cookies with `page` |
| **LOW-14** draft assertion strategy (06-03) | ✅ Fixed | Strategy (b): assert QUEUED/202 state, not preview text |

No regressions found. All addressed concerns check out.

---

## 3. NEW Concerns

### [NEW][MEDIUM] Data-flow gap: how the triage pipeline reads seeded messages through the offline transport

- **Plan 06-01:Task 3** — `E2eStubGmailClient.buildGmailClient(accessToken)` returns a `Gmail` object backed by `OFFLINE_TRANSPORT`, an `HttpTransport` subclass whose `buildRequest()` always throws `IOException`. The seeded messages are stored in an internal `ConcurrentHashMap<String, SeededMessage>`.
- **Problem**: The production triage pipeline presumably calls `buildGmailClient(token).users().messages().get(msgId).execute()` to fetch message content. The offline transport throws `IOException` on every outbound attempt. The plan asserts *"every triage read path consults the in-memory `seededMessages` map directly (the offline transport's only role is satisfying the `Gmail.Builder` constructor — it is a fail-loud guard, never invoked on the happy path)"*, but **no code is added in this plan that reads from the map** — only `seedMessage(...)` and `reset()` are implemented. There is no `getMessage(...)` or `findMessage(...)` method on `E2eStubGmailClient`, and `buildGmailClient()` returns a `Gmail` whose transport throws.
- **Fix suggestion**: Either (a) add a `getMessage(String messageId)` method to the `GmailClient` interface that the stub implements from the map, and verify the production triage code calls it; OR (b) in the e2e-stub, return a `Gmail` object whose API calls are overridden per-method to read from the in-memory map (e.g., a subclass of `Gmail` that intercepts `users().messages().get(id).execute()`); OR (c) provide an integration note explaining how existing production code reads from the stub map without calling the Gmail API. Without one of these, the golden-path spec in 06-03 will likely fail at Step 5 (observe triage action in audit log) because the message fetch crashes with `IOException` from the offline transport.

### [NEW][LOW] application-e2e-stub.yml placeholder key length mismatch

- **Plan 06-01:Task 3:File 4** — The base64 placeholder `ZTJlLXN0dWItcmVmcmVzaC1rZXktMzItYnl0ZXMtcGFkZGluZw==` decodes to 37 bytes (`e2e-stub-refresh-key-32-bytes-padding`), not 32. The comment claims *"32-byte base64 AES-GCM placeholder"*. AES-256 requires exactly 32 bytes — a `SecretKeySpec` wrapping 37 bytes will cause `InvalidKeyException` at first `Cipher.init()`. If the key validation is eager (e.g., during a `@PostConstruct` or `@Bean` factory method), the `bootRun --spring.profiles.active=e2e-stub` acceptance test will fail.
- **Fix suggestion**: Replace with a valid 32-byte base64 string, e.g.: `QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=` (32 × `A` = 32 bytes), or generate one via `openssl rand -base64 32`.

### [NEW][LOW] SeedAuthenticatedSession tenant ID coordination gap

- **Plan 06-03:Task 2** — The spec hard-codes `SEED_TENANT_ID = 'e2e-tenant-1'` but calls `seedAuthenticatedSession(page)` which presumably logs in as a user with some tenant ID. If the authed session's tenant ID doesn't match `e2e-tenant-1`, the pre-seeded message won't be visible to that session's triage queries.
- **Fix suggestion**: Document in the spec how `seedAuthenticatedSession` sets the tenant, AND add a verification step after sign-up that reads the current tenant ID from the session (e.g., via a `GET /api/user/profile` endpoint) and uses that value in the seed-message POST, or make `seedAuthenticatedSession` accept an explicit `tenantId` parameter and set it in the seed.

### [NEW][LOW] E2eStubResetController double-gated — no ability to call from k6 (intentional, but undocumented limitation)

- **Plan 06-01:Task 3:File 3** — The controller requires `@Profile("e2e-stub")` AND `@ConditionalOnProperty`. A developer manually running the load test might need to reset stub state mid-test; the `loadtest` profile has no reset controller. Not a real problem for the RC gate (the compose stack is fresh per run), but documentation in `loadtest/README.md` would help future operators debugging locally.

---

## 4. Overall Verdict

**NEEDS CYCLE 3** — One new MEDIUM concern (data-flow gap for seeded-message retrieval) could prevent the golden-path spec from passing at RC time.

The HIGH-1 and HIGH-2 fixes are solidly resolved. The Cycle 1 MEDIUM/LOW concerns are well-addressed. But the plan's fundamental assumption that *"every triage read path consults the in-memory seededMessages map directly"* is unsubstantiated — no code path is described or added that reads from `seededMessages` map when `buildGmailClient()` returns a transport that throws on every call.

This needs clarification or a fix before execution:
- If existing production code already calls the `GmailClient` differently than I'm assuming, state the contract explicitly in 06-01's interfaces section.
- If a new method needs to be added to `GmailClient` for message retrieval, add it now (breaking change to the interface in Task 1).
- If the e2e-stub `Gmail` object needs to be a subclass that intercepts API calls, specify that in Task 3.

The remaining two LOW issues (base64 key length, tenant ID coordination) are genuine but minor and could be flagged as "fix at execution time."

**HIGH-1: FULLY RESOLVED**
**HIGH-2: FULLY RESOLVED**
**NEW HIGHs: 0** (the data-flow gap is MEDIUM, not HIGH — it's an integration concern that the executor can resolve with additional context from the codebase)

OVERALL_HIGH_COUNT: 0

---

## Cycle 2 — Codex Status

Codex cycle-2 process started at 00:13, reasoned for ~5 min on the revised plans (462KB of stderr reasoning observed reading 06-01..06-05 in detail), then was terminated before flushing `codex-c2.md`. Only Windows process-termination text reached the markdown file. The cycle-1 codex review (delivered late, captured above) remains the canonical Codex signal for this convergence run.

---

## Cumulative HIGH Tally

| Source | HIGH count | Status after cycle-1 replan |
|--------|------------|------------------------------|
| OpenCode cycle 1 (HIGH-1 MockHttpTransport, HIGH-2 SeedMessageRequest shape) | 2 | ✅ FULLY RESOLVED per OpenCode cycle 2 |
| Codex cycle 1 (late delivery, 8 HIGH concerns) | 8 | ⚠ NOT FED to cycle-1 replan; status against revised plans unverified |
| OpenCode cycle 2 NEW concerns | 0 HIGH (1 MEDIUM data-flow gap closely related to codex's "GmailClient interface may not swap production use") | new |

### Codex cycle-1 HIGHs (still applicable to revised plans):

1. **06-01 — `GmailClient` interface may not swap production usage.** Existing services likely inject `GmailApiClientFactory` concretely; an interface + `@Primary` fake won't intercept. Cycle-1 replan did not audit Gmail consumer sites.
2. **06-01 — `TokenVerifier` fake fragility.** Cycle-1 replan committed to a hand-written subclass. Codex notes the subclass may need `TokenVerifier.newBuilder()` to seed parent state — accepted in the revised plan, but reviewer hasn't re-confirmed.
3. **06-03 — Golden path bypasses real Google OAuth flow.** `seedAuthenticatedSession(page)` writes browser cookies; doesn't create a real Spring Session in Redis. Cycle-1 replan did not address.
4. **06-03 — Draft acceptance weakened below SPEC.** Cycle-1 replan explicitly chose LOW-14 path (b) (assert 202/queued) instead of wiring `E2eStubChatModel` to allow asserting "draft saved in stub Gmail". Codex flags this as a SPEC-coverage gap.
5. **06-03/06-04 — Global Playwright `webServer` change likely breaks existing E2E CI.** If `playwright.config.ts` always starts Spring Boot under `e2e-stub`, the daily `gates.yml` Playwright job now needs Postgres/Redis — which it doesn't start. Cycle-1 replan did not address.
6. **06-02 — Load-test tenant-ID query `loadtest-tenant-%` may not match schema reality.** If tenant IDs are UUIDs, the invariant query is structurally invalid. Cycle-1 replan did not address.
7. **06-02 — Ledger invariant `SUM(amount_credits) = 0` is oversimplified.** Real reconciliation = opening balance + top-ups − settled charges − active reserves = closing balance per tenant. Cycle-1 replan did not address.
8. **06-02 — `loadtestVerify` JDBC may fail from Gradle script classpath.** PostgreSQL driver is not on Gradle's buildscript classpath by default; `DriverManager.getConnection(...)` from `build.gradle.kts` task code may fail to find a driver. Cycle-1 replan did not address.
9. **06-02/06-05 — Committed load-test result artifact unresolved.** `06-LOAD-TEST-RESULT.md` is generated in CI after a tag push, but SPEC requires the report committed before the gate is green. Cycle-1 replan did not address.
10. **06-04/06-05 — RC tag and sign-off flow is partially circular.** Cycle-1 replan removed the dangerous delete-and-re-push (MED-7) but the gate-URL-needed-before-tag chicken-and-egg remains: tag is on the pre-sign-off commit, sign-off is committed AFTER the tag exists.

### CYCLE_SUMMARY (cycle 2)

```
CYCLE_SUMMARY: current_high=10
```

Counts: 8 codex cycle-1 HIGHs still unresolved + 1 partial (TokenVerifier fragility, accepted but unverified) + 1 partial (RC tag circularity, partially addressed via MED-7).

OpenCode cycle 2's NEW concerns: 0 HIGH, 1 MEDIUM (Gmail data-flow gap — overlaps with Codex HIGH-1 above).

### Stall analysis

| Cycle | Reported HIGH count | Notes |
|-------|---------------------|-------|
| 1 | 2 (opencode only) | Codex cycle-1 output arrived late, after cycle-1 replan |
| 2 | 10 (opencode-confirmed-2-resolved + 8 codex-cycle-1-not-yet-addressed + 2 partials) | Codex cycle-2 did not produce structured output |

The cycle-2 number went UP not because of regressions but because the codex cycle-1 findings (8 HIGHs) became visible only after cycle-1 was committed. This is **late-signal stall**, not regression stall. The remaining cycle budget is 1.

