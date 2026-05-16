# Phase 6: Polish & CASA-Verified Launch — Specification

**Created:** 2026-05-14
**Ambiguity score:** 0.12 (gate: ≤ 0.20)
**Requirements:** 6 locked

## Goal

Produce a tagged `v1.0.0-rc1` release candidate of Zero Mail that proves five launch-readiness invariants — automated golden-path E2E, 50-tenant concurrency without cross-tenant or ledger drift, all existing regression suites green on the RC commit, and a signed `LAUNCH-GO-NOGO.md` — then move the OAuth consent screen from Testing to Production **only after CASA is closed in a separate post-launch track** (Phase 6 itself launches in OAuth "Testing" mode, capped at 100 users).

## Background

All product phases (1, 1.1, 1.2, 1.2.1, 1.3, 1.4, 1.5, 2A, 2B, 2C, 3, 4, 5A, 5B, 5C) are complete or near-complete — 56 plans shipped, 61 of 61 v1 requirements mapped to phases. The bones of the product are in: Gmail ingestion (Pub/Sub + `users.watch`), Spring AI 2.0.0-M6 gateway with sanitization/Unicode-strip/truncation/per-tenant cap, rules engine with template gallery and preview, triage convergence with allow-list + audit + undo, draft replies, analytics, daily digest, billing ledger, and a Next.js 16 / React 19 UI covering all surfaces.

What does **not** exist today, relative to the phase goal:

- **No golden-path E2E automation.** Playwright is installed at workspace root (Phase 1.1 P07), Playwright route-smoke specs landed in Phase 1.3 P05, and per-feature Playwright specs accumulated through 5A–5C — but no single spec walks sign-up → connect Gmail → enable template rule → receive message → triage → undo → draft → analytics end-to-end against a single environment.
- **No concurrency / load test.** Multi-tenant correctness tests exist (`MultiTenantLeakIntegrationTest` from Phase 1, `FND-05`), but they do not sustain throughput across N concurrent tenants. No `k6` / `Gatling` infrastructure is checked in.
- **No release-candidate convention.** The repo has no `v1.0.0-rc*` tag, no documented suite-of-suites that must pass on a single SHA, and no aggregator that the verifier can point at as "the launch artifact passed everything."
- **No `LAUNCH-GO-NOGO.md`.** PROJECT.md tracks decisions and STATE.md tracks per-phase progress, but no checklist artifact represents the launch decision itself.
- **CASA is still `FND-07: Pending`.** REQUIREMENTS.md line 27 + line 157 show the verification was never initiated; the ROADMAP "External Track" entry assumed it was filed in Phase 1, but it was not. Phase 6 **does not** unblock CASA — instead, Phase 6 launches into Google's OAuth "Testing" mode (100-user cap) and CASA closure becomes a separate post-launch track.

The prompt-injection regression suite, ArchUnit suite, and golden-set drift check already exist from prior phases — Phase 6 wires them into the RC-tag gate, it does not author them.

## Requirements

1. **Automated golden-path Playwright E2E**: A single Playwright spec exercises the full v1 user journey end-to-end against a staging-like environment, with Gmail and Pub/Sub stubbed so the spec is deterministic.
   - Current: No spec covers the full journey; existing Playwright specs are per-feature route smokes and per-page interaction tests.
   - Target: `apps/web/e2e/launch-golden-path.spec.ts` (single spec file) drives: Google OAuth sign-up via stub → connect Gmail via stub → enable one template rule from gallery → simulate Pub/Sub-pushed message → observe triage action in audit log → exercise undo → request AI draft → confirm draft saved in stubbed Gmail → load analytics dashboard. Spec runs as part of `pnpm e2e` and is wired into the RC gate.
   - Acceptance: Spec is green on the `v1.0.0-rc1` tag commit; spec fails (red) if any step in the journey breaks (verified by introducing a deliberate regression in a scratch branch before merge).

2. **50-tenant concurrency load test with invariant assertions**: An automated load test sustains 50 concurrent tenants × ~10 Pub/Sub messages/minute each for at least 10 minutes against a staging-like environment.
   - Current: No load-test infrastructure is checked in. `MultiTenantLeakIntegrationTest` proves the invariant under a small number of in-process tenants but not under sustained external load.
   - Target: A `k6` or `Gatling` script (tool decided in discuss-phase) drives 50 simulated tenants. After the run, three invariants are asserted by an automated post-check: (a) zero cross-tenant data in audit log rows; (b) ledger balance + reserves + settlements reconcile to zero drift per tenant; (c) zero `tenantId=…` log lines contain email body / prompt / completion content (Logback scrub filter validated on real traffic). Aggregate throughput SLO is **not** required — the test gates on invariants, not p95.
   - Acceptance: Load-test run on the `v1.0.0-rc1` tag produces a saved report (`.planning/phases/06-…/06-LOAD-TEST-RESULT.md` or equivalent) with the three invariant checks all PASS. Report is committed.

3. **Existing regression suites green on the RC tag**: The prompt-injection regression suite, ArchUnit suite (including all `FND-02` / `FND-04` log-bleed and `ThreadLocal` rules), Spring Modulith `ApplicationModulesTest`, and the LLM golden-set drift check all pass on the exact `v1.0.0-rc1` commit.
   - Current: These suites exist and pass on `main` continuously, but no single artifact records "all green at SHA X."
   - Target: A CI job (or a documented `./gradlew check && pnpm -r test && pnpm -r e2e` invocation) runs every gate suite on the RC tag and its output is linked from `LAUNCH-GO-NOGO.md`.
   - Acceptance: Green CI run is linked from `LAUNCH-GO-NOGO.md`; tampering with any of the four suites' output produces a failed RC gate.

4. **Release-candidate tag `v1.0.0-rc1`**: An annotated git tag identifies the exact commit on `main` that the launch decision is taken against.
   - Current: No `v1.0.0-rc*` or `v1.*` tag exists.
   - Target: `git tag -a v1.0.0-rc1 <sha>` on `main` after Phase 6's work lands; tag message records the SHA, the date, and the four-suite gate result.
   - Acceptance: `git tag -l 'v1.0.0-rc*'` lists `v1.0.0-rc1`; the tag is annotated (not lightweight) and points to a commit on `main`.

5. **`LAUNCH-GO-NOGO.md` decision artifact**: A single markdown checklist captures the launch decision and the evidence it was taken against.
   - Current: No launch decision artifact exists. PROJECT.md decision log and per-phase SUMMARY.md files are not designed to record cross-phase launch readiness.
   - Target: `.planning/LAUNCH-GO-NOGO.md` contains a pass/fail checklist: (a) Playwright golden-path spec green on RC tag — link to CI run; (b) 50-tenant load test invariants all PASS — link to result file; (c) prompt-injection regression suite green on RC tag; (d) ArchUnit suite green on RC tag; (e) Spring Modulith `ApplicationModulesTest` green on RC tag; (f) LLM golden-set drift check green on RC tag; (g) trust-story re-affirmed in writing — auto-send forbidden, no stored bodies/prompts/completions, every triage action undoable; (h) launch mode = OAuth "Testing" (Production move deferred to post-launch CASA track). Document is signed off by the project owner (a committed `✓ signed-off by @<user> on <date>`).
   - Acceptance: File exists at `.planning/LAUNCH-GO-NOGO.md`, every checkbox is checked, every linked evidence URL/path resolves, sign-off line is present and committed.

6. **OAuth consent screen stays in "Testing" mode**: Phase 6 does **not** move the Google OAuth consent screen to "Production" — that move is gated on CASA, which is deferred to a separate post-launch track.
   - Current: `FND-07` (CASA verification) is still `Pending`. Consent screen is in "Testing" mode by default.
   - Target: Consent screen remains "Testing" through Phase 6 launch. `LAUNCH-GO-NOGO.md` explicitly records this as the launch mode. A post-Phase-6 backlog entry (`POST-LAUNCH-CASA.md` or a tracked seed/issue) captures the deferred CASA work — submission, evidence package, Letter of Assessment, consent-screen Production move.
   - Acceptance: A post-launch CASA tracking artifact exists in `.planning/` (location decided in discuss-phase). `LAUNCH-GO-NOGO.md` checklist item (h) is checked with launch mode = Testing.

## Boundaries

**In scope:**

- One Playwright end-to-end spec covering the full v1 golden path with Gmail + Pub/Sub stubbed.
- One 50-tenant × ~10 msg/min load test (tool: k6 or Gatling, picked in discuss-phase) with three invariant post-checks (cross-tenant isolation, ledger drift, log-bleed).
- Wiring the four existing regression gates (prompt-injection, ArchUnit, Modulith, golden-set drift) into a single RC-tag CI run.
- Cutting an annotated `v1.0.0-rc1` tag on `main`.
- Authoring `.planning/LAUNCH-GO-NOGO.md` (pass/fail checklist + sign-off line + linked evidence).
- Authoring a post-launch CASA tracking artifact (one file/seed/issue, location decided in discuss-phase).
- Stubs / fixtures needed by Playwright and the load test (mock Gmail responses, mock Pub/Sub OIDC tokens for the load tool, fixed-clock helpers).

**Out of scope:**

- **CASA restricted-scope verification** (submission, lab engagement, Letter of Assessment, consent-screen Production move) — explicitly deferred to a post-launch track. Phase 6 launches into OAuth "Testing" mode (100-user cap), which is sufficient for the Vietnam beta.
- **Production runbook** (on-call rotation, Pub/Sub backlog recovery, `users.watch` renewal incident playbook, ledger reconciliation playbook) — explicitly deferred. The user owns operations directly for the initial beta; runbook becomes a separate post-launch track once incident volume justifies it.
- **Throughput SLO / p95 latency target** — the load test gates on invariants (no leakage, no drift, no bleed), not on a numeric throughput floor. SLO-style perf gates are a v2 concern.
- **Authoring new test suites for prompt-injection / ArchUnit / golden-set drift** — these suites already exist from Phase 1 / 2C / etc. Phase 6 only wires them into the RC gate.
- **Authoring new application code or product features** — Phase 6 ships zero new REQ-IDs. Any product bug surfaced during Phase 6 testing is either fixed in-line (small) or routed to a separate hotfix plan.
- **Multi-region / HA / staging infrastructure provisioning beyond a single staging-like environment** — single-VPS launch is locked.
- **Auto-send, RAG, embeddings, Outlook, team plans, mobile, enterprise SSO** — all v2 or permanently out of scope per REQUIREMENTS.md "Out of Scope" table.

## Constraints

- **Launch in OAuth "Testing" mode only.** OAuth consent screen MUST NOT be moved to Production in this phase. CASA is the gate for that move and CASA is deferred.
- **No new REQ-IDs.** Phase 6 validates existing requirements; it does not introduce new ones in REQUIREMENTS.md.
- **No new long-term data.** Test fixtures, load-test data, and Playwright stubs MUST respect the privacy constraint — no real email bodies, prompts, or completions persisted anywhere. Synthetic content only.
- **Playwright stubs Gmail + Pub/Sub.** The golden-path E2E does not call the real Gmail API or real Pub/Sub. This keeps the spec deterministic and CI-runnable.
- **Load test runs against a staging-like environment**, not a developer workstation. Real PostgreSQL + Redis + virtual threads enabled. Tool choice (k6 vs Gatling) is deferred to discuss-phase.
- **RC tag is annotated** (`git tag -a`), not lightweight. Tag message records the SHA, date, and four-suite gate result.
- **`LAUNCH-GO-NOGO.md` lives at `.planning/LAUNCH-GO-NOGO.md`** (repo root of planning tree), not inside the phase directory — the launch decision spans the whole project and outlives the phase.
- **No `spring-cloud-gcp` and no real LLM calls in `./gradlew test`.** Load-test invocation of the system must respect both project rules (Gmail Pub/Sub arrives as plain HTTP POSTs; LLM calls use the mocked `ChatModel` path or the `@Tag("llm-eval")` opt-in path).

## Acceptance Criteria

- [ ] `apps/web/e2e/launch-golden-path.spec.ts` exists and is green on the `v1.0.0-rc1` tag commit.
- [ ] Load-test script (k6 or Gatling) exists in the repo and a load-test result file exists in `.planning/phases/06-…/` with the three invariant checks all PASS, committed.
- [ ] A CI run on the `v1.0.0-rc1` tag shows prompt-injection regression suite, ArchUnit suite, `ApplicationModulesTest`, and LLM golden-set drift check all green; link is recorded in `LAUNCH-GO-NOGO.md`.
- [ ] `git tag -l v1.0.0-rc1` returns the tag; the tag is annotated and points to a commit on `main`.
- [ ] `.planning/LAUNCH-GO-NOGO.md` exists with all 8 checklist items (a–h) checked, every evidence link/path resolves, and a sign-off line of the form `✓ signed-off by @<user> on <date>` is committed.
- [ ] `LAUNCH-GO-NOGO.md` item (h) explicitly records launch mode = OAuth "Testing" (Production move deferred).
- [ ] A post-launch CASA tracking artifact exists in `.planning/` capturing the deferred CASA submission/evidence/LoA/Production-move work.
- [ ] No new code path is added that auto-sends mail; the existing `TRG-03` safeguard remains in force on the RC commit (re-verified as part of the ArchUnit suite or an equivalent check).
- [ ] No new long-term storage of email bodies, prompts, or completions is introduced by load-test fixtures or Playwright stubs (re-verified by `FND-04` ArchUnit + manual schema review).

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                                       |
|--------------------|-------|------|--------|---------------------------------------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | RC tag + 5 concrete deliverables; CASA + runbook explicitly deferred                        |
| Boundary Clarity   | 0.90  | 0.70 | ✓      | CASA OUT, runbook OUT, throughput SLO OUT, new REQ-IDs OUT                                  |
| Constraint Clarity | 0.85  | 0.65 | ✓      | 50 tenants × ~10 msg/min, Playwright, Testing mode launch; k6-vs-Gatling deferred to discuss |
| Acceptance Criteria| 0.85  | 0.70 | ✓      | 9 pass/fail criteria, every one verifier-checkable                                          |
| **Ambiguity**      | 0.12  | ≤0.20| ✓      |                                                                                             |

## Interview Log

| Round | Perspective       | Question summary                                                          | Decision locked                                                                                       |
|-------|-------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| 1     | Researcher        | Load/concurrency scope — what does "N concurrent tenants" mean?           | 50 tenants × ~10 msg/min, k6 or Gatling against staging, invariant-based (no throughput SLO)         |
| 1     | Researcher        | Runbook format and location?                                              | Runbook is OUT of scope for Phase 6 (deferred to post-launch ops track)                              |
| 1     | Researcher        | E2E golden-path — manual checklist or automated Playwright spec?         | Automated Playwright only; no manual checklist                                                        |
| 2     | Boundary Keeper   | CASA close-out — what does "completed" mean for Phase 6 acceptance?     | CASA is DEFERRED — Phase 6 launches in OAuth "Testing" mode (100-user cap); CASA is a separate track |
| 2     | Boundary Keeper   | "Release candidate commit" — how is it defined?                          | Annotated git tag `v1.0.0-rc1` on `main`                                                              |
| 2     | Seed Closer       | What IS the go/no-go deliverable, if runbook is out?                     | `.planning/LAUNCH-GO-NOGO.md` pass/fail checklist with linked evidence + sign-off line               |

---

*Phase: 06-polish-casa-verified-launch*
*Spec created: 2026-05-14*
*Next step: /gsd-discuss-phase 6 — implementation decisions (k6 vs Gatling, Playwright stub strategy for Gmail/Pub-Sub, RC CI wiring, post-launch CASA artifact location)*
