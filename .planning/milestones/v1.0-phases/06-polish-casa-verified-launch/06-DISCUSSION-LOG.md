# Phase 6: Polish & CASA-Verified Launch - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 6-polish-casa-verified-launch
**Areas discussed:** Load-test tool selection, Playwright golden-path stub strategy, RC-tag CI gate wiring, Load-test environment provisioning
**Mode:** Advisor (full_maturity calibration tier, vendor_philosophy=thorough-evaluator)

---

## Load-test tool selection

Synthesized from `gsd-advisor-researcher` agent output (6 options researched, 4 surfaced after dominated-options trim).

| Option | Description | Selected |
|--------|-------------|----------|
| **k6 (Grafana)** | JS scripts, low overhead, native CI threshold checks, aligns with existing Grafana OTLP stack, OIDC stubbed via @Profile('loadtest') bypass in backend, invariants run as separate Gradle task | ✓ |
| JUnit @SpringBootTest + virtual threads | All-Java, reuses RestClient+LocalServerPort harness and JdbcTemplate for invariants; pairs only with Testcontainers env (which fails invariant-c) | |
| Gatling (Java DSL) + gatling-gradle-plugin | JVM-native, mature HTML report; separate source set, can't reuse Spring context | |
| JMeter | XML/GUI-first, diff-hostile, ecosystem stagnant | |

**User's choice:** k6 (Recommended, pairs with docker-compose env)
**Notes:** Locked together with D-02 (docker-compose env). k6 chosen specifically because the load test gates on invariants (SQL + log scan), not throughput; JUnit @SpringBootTest would have been the "language fit" choice but Agent #4 surfaced that it loads test logback (not prod logback), making invariant (c) false-green — fatal flaw for a "Logback scrub filter validated on real traffic" gate.

---

## Playwright golden-path stub strategy

Synthesized from `gsd-advisor-researcher` agent output (5 options researched, 4 surfaced after dominated-options trim).

| Option | Description | Selected |
|--------|-------------|----------|
| **Dedicated `e2e-stub` Spring profile (@Profile + @Primary)** | Swap GmailClient + PubsubVerifier with deterministic Java fakes; real backend code runs end-to-end; Pub/Sub leg is a real HTTP POST from Playwright to the real controller | ✓ |
| page.route only (existing pattern) | Frontend fetch interception; zero backend infra but spec becomes UI theatre — fails SPEC acceptance ("fail if any step regresses") | |
| WireMock at HTTP boundary | Intercept *.googleapis.com calls; extra service in CI; doesn't help inbound Pub/Sub leg | |
| MSW (Mock Service Worker) | Node-side intercepts; falls back to page.route in Playwright; doesn't help Pub/Sub | |

**User's choice:** Dedicated `e2e-stub` Spring profile (Recommended)
**Notes:** Two-tier test taxonomy established: existing 5 `page.route` specs stay for narrow UI-state coverage; new `launch-golden-path.spec.ts` is the only spec that uses the `e2e-stub` profile. Pub/Sub-push leg specifically requires this approach because it's server-to-server and `page.route` literally cannot intercept it (Playwright issue #23277). Hybrid option (page.route for Pub/Sub) was dropped as incoherent.

---

## RC-tag CI gate wiring

Synthesized from `gsd-advisor-researcher` agent output (4 options, full set surfaced).

| Option | Description | Selected |
|--------|-------------|----------|
| **Reusable `gates.yml` (workflow_call) called by both ci.yml and new release.yml** | Single source of truth; release.yml thin (~30 LOC); new gate = 1 file edit; both pipelines pick it up; tag-only gates layer on top | ✓ |
| Extend ci.yml with tag-conditional aggregator | Zero drift — same job defs; fattens ci.yml with conditional sprawl | |
| New release.yml duplicating gate definitions | Cleaner separation but drift between daily and release CI — conflicts with user's "no drift" frustration trigger | |
| Documented manual invocation | Cheapest, but Playwright golden-path + load-test have no CI home — fails the SPEC tamper-resistance acceptance criterion | |

**User's choice:** Reusable `gates.yml` called by both ci.yml and new release.yml (Recommended)
**Notes:** Zero-drift property explicitly resolves the user's `frustration-triggers: instruction-adherence` profile signal (Vendor Philosophy thorough-evaluator + no-drift requirement). Branch protection on `refs/tags/v*.*.*-rc*` requires the `release-gates-summary` check, making the gate non-bypassable. Cutting rc2 requires zero workflow edits.

---

## Load-test environment provisioning

Synthesized from `gsd-advisor-researcher` agent output (5 options researched, 4 surfaced after dominated-options trim).

| Option | Description | Selected |
|--------|-------------|----------|
| **Ephemeral docker-compose on GHA runner (prod images + k6 driver, run-then-teardown)** | Real prod logback-spring.xml + real JVM + real Hibernate + real SKIP_LOCKED; reusable for Phase 7; zero infra cost; trivial data hygiene | ✓ |
| Always-on staging VPS mirroring prod | Real prod sizing; recurring monthly cost for a single checkpoint | |
| In-CI Testcontainers + @SpringBootTest(RANDOM_PORT) | Test logback ≠ prod logback — invariant (c) false-greens; fatal realism gap | |
| Shared dev with `loadtest-tenant-<n>` prefix | No new infra; append-only audit table makes cleanup non-recoverable; ledger contamination risk | |

**User's choice:** Ephemeral docker-compose on GHA runner (Recommended)
**Notes:** This choice is the keystone that locked D-01 (k6, because Testcontainers env was the only path to keep JUnit @SpringBootTest viable) and D-04 (Gradle `loadtestVerify` task running invariant SQL via JDBC against the compose Postgres). The "prod image loads prod logback-spring.xml" property is what makes invariant (c) — zero log lines containing email body / prompt / completion — meaningful at all.

---

## Claude's Discretion

Areas the user delegated to planner / researcher decisions (recorded in CONTEXT.md `<decisions>` → "Claude's Discretion"):
- Specific k6 script file layout (one .js file vs. per-scenario files).
- HTML report retention policy for k6 + Playwright + load-test result.
- Playwright spec internal structure (page-object split vs. inline).
- Exact wording of the ArchUnit rules guarding D-03 (`loadtest` profile) and D-07 (`e2e-stub` profile).
- Whether `e2e.yml` folds into `gates.yml` or stays separate as a workflow_call-callable workflow.
- Exact CASA lab pick for SEED-012 (deferred to post-launch track).
- Exact `loadtest/` directory structure.

## Pre-decided (not asked)

- **Post-launch CASA artifact location** = `.planning/seeds/SEED-012-casa-restricted-scope-verification.md`. Auto-decided because the seeds directory already contains 11 entries in identical structure — established project pattern, low-stakes choice.

## Deferred Ideas

Captured during discussion — see CONTEXT.md `<deferred>`:
- Production runbook (post-launch ops track).
- CASA Tier 2 submission + evidence package + LoA closure + Production move (tracked in SEED-012).
- Throughput SLO / p95 latency gate (graduation path: Gatling Java DSL on the same compose stack).
- Multi-region / HA / staging VPS.

## Reviewed Todos

- **WR-06 — test-profile SecurityConfig slice** — deferred (new test infra, out of Phase 6 scope).
- **Worker yml :? fail-fast parity** — RESOLVED (already fixed at `backend/worker/.../application.yml:63`; user clarified during discussion). Todo file should be archived as part of Phase 6 housekeeping (see D-15).
- **@NamedInterface for backend/core context API surfaces** — deferred (architecture work, not launch-validation).
