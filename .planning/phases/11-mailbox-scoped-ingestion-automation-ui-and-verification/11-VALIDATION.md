---
phase: 11
slug: mailbox-scoped-ingestion-automation-ui-and-verification
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-09
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot 4 test slices) + Vitest 4 / Playwright 1.60 (apps/web) |
| **Config file** | Gradle `build.gradle.kts` per module; `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test --tests "*Mailbox*"` (focused scope — avoids the ~15-context Postgres connection ceiling) |
| **Full suite command** | `./gradlew :backend:core:test :backend:api:test` + `pnpm --filter web test` |
| **Estimated runtime** | ~focused: 60–120s · full backend: flakes on connection exhaustion (run focused scopes) |

---

## Sampling Rate

- **After every task commit:** Run the focused quick command for the touched scope
- **After every plan wave:** Run the relevant module suite (focused, not full `:backend:core:test`)
- **Before `/gsd-verify-work`:** Focused mailbox-scope suites + ArchUnit boundary tests must be green; Playwright VER-04 flows green
- **Max feedback latency:** ~120 seconds (focused)

---

## Per-Task Verification Map

> Derived from the 6 phase plans (Wave 0 RED scaffolds in Plan 01; production in Waves 2-5). Each row carries the task's own `<automated>` command. "File Exists" = the test/scaffold the task's verify drives (W0 = created by Wave 0 Plan 01).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | AUD-05 | T-11-01-04 | findByTenantId-forbidding ArchUnit rule added with draining allow-list; shim cannot silently route a mailbox-scoped write to primary | arch | `./gradlew :backend:core:test --tests "*GmailClientLookupBoundary*"` | ✅ self (W0) | ⬜ pending |
| 11-01-02 | 01 | 1 | VER-03, ING-01, ING-03, ING-06, AUTO-04, AUTO-06, AUD-02 | T-11-01-02 / T-11-01-03 | Six RED invariant scaffolds + two-mailbox fixture compile and fail for the documented reason (collision/AAD/rules/outbound) | compile/RED | `./gradlew :backend:core:compileTestJava` | ✅ self (W0) | ⬜ pending |
| 11-01-03 | 01 | 1 | AUD-06, VER-01 | T-11-01-01 | Single-context two-CONNECTED-mailbox isolation harness compiles; RED until Wave 3/4 | compile/RED | `./gradlew :backend:api:compileTestJava` | ✅ self (W0) | ⬜ pending |
| 11-02-01 | 02 | 2 | WSP-03, ING-02, ING-03, ING-06, VER-01 | T-11-02-01 / T-11-02-02 / T-11-02-04 | Ingestion tables (120-124) backfill-to-primary + PK swaps include gmail_connection_id; HALT on tenant-without-connection; projection AAD untouched | integration (Liquibase) | `./gradlew :backend:core:test --tests "*Migration12*"` | ✅ W0 (Migration12xBackfillTest) | ⬜ pending |
| 11-02-02 | 02 | 2 | AUD-01, AUTO-01, AUTO-02, VER-03 | T-11-02-05 | triage_audit source/executing mailbox + rules gmail_connection_id; idempotency + template-key indexes widened by mailbox | integration (Liquibase) | `./gradlew :backend:core:test --tests "*Migration12*" --tests "*RuleEntity*"` | ✅ W0 (Migration12xBackfillTest) | ⬜ pending |
| 11-02-03 | 02 | 2 | ING-03, ING-06 | T-11-02-04 | MailMessageObserved + MailOutboundObserved carry gmailConnectionId; privacy Javadoc preserved; no api↔worker event coupling | compile | `./gradlew :backend:core:compileJava` | ✅ self | ⬜ pending |
| 11-03-01 | 03 | 3 | ING-01, ING-05, AUD-07 | T-11-03-01 / T-11-03-04 | Pub/Sub resolves (tenant, mailbox); unknown mailbox drops safely; drop-path log emits no email/subject | integration | `./gradlew :backend:core:test --tests "*PubSubMailboxLookup*"` | ✅ W0 (PubSubMailboxLookupTest) | ⬜ pending |
| 11-03-02 | 03 | 3 | ING-02, ING-03, ING-06, ING-05, AUD-07 | T-11-03-02 / T-11-03-03 / T-11-03-05 | Delivery builds buildClientForMailbox; per-connection cursor; mailbox-keyed observed/projection; AAD unchanged; allow-list entry left for Plan 05 | integration (tdd) | `./gradlew :backend:core:test --tests "*ObservedMailboxPk*" --tests "*ProjectionAadContinuity*"` | ✅ W0 (ObservedMailboxPkTest, ProjectionAadContinuityTest) | ⬜ pending |
| 11-04-01 | 04 | 3 | AUTO-01, AUTO-02, AUTO-03, AUTO-04 | T-11-04-02 / T-11-04-04 | Runtime rule load filters WHERE gmail_connection_id = :sourceMailbox AND enabled=true; copy-rules clones When/Then enabled=false | integration (tdd) | `./gradlew :backend:core:test --tests "*MailboxOwnedRules*"` | ✅ W0 (MailboxOwnedRulesRuntimeTest) | ⬜ pending |
| 11-04-02 | 04 | 3 | AUTO-04, AUTO-05, AUD-01, AUD-03, AUD-07 | T-11-04-03 / T-11-04-05 | Triage dispatch carries source+executing mailbox; writes via buildClientForMailbox; audit records both mailboxes; safety-net metadata-only | integration (tdd) | `./gradlew :backend:core:test --tests "*MailboxOwnedRules*"` | ✅ W0 (MailboxOwnedRulesRuntimeTest) | ⬜ pending |
| 11-04-03 | 04 | 3 | AUTO-06, AUD-02 | T-11-04-01 / T-11-04-06 | Outbound send routes by command.mailboxRef() through the single gateway; undo same mailbox; blocked/failed → failed audit, no draft fallback | integration (tdd) | `./gradlew :backend:core:test --tests "*OutboundMailbox*"` | ✅ W0 (OutboundMailboxRoutingTest) | ⬜ pending |
| 11-05-01 | 05 | 4 | WSP-05, WSP-06 | T-11-05-02 / T-11-05-03 | MailboxContext ScopedValue + MailboxBindingFilter (after tenant, before Hibernate, not @Transactional); resolver re-validates ownership, primary fallback | integration (api) | `./gradlew :backend:api:test --tests "*MailboxBinding*" --tests "*CrossAccountIsolation*"` | ✅ W0 (CrossAccountIsolationTest) | ⬜ pending |
| 11-05-02 | 05 | 4 | ING-04, ING-06, AUD-02, AUD-05, AUD-06 | T-11-05-01 / T-11-05-04 / T-11-05-05 | Active-mailbox get/set endpoint (404/409 ownership); read consumers via MailboxContext; invalid-grant disconnects failing mailbox; BOTH allow-lists drained (own + Wave 3 entries); cross-account isolation green | integration (api) + arch | `./gradlew :backend:api:test --tests "*CrossAccountIsolation*" --tests "*GmailClientLookupBoundary*"` | ✅ W0 (CrossAccountIsolationTest, GmailClientLookupBoundaryTest) | ⬜ pending |
| 11-06-01 | 06 | 5 | VER-02, UX-01, UX-02 | T-11-06-01 / T-11-06-03 | schema.d.ts regenerated (never hand-edited); mailbox feature triad; switch invalidates all mailbox-scoped query keys | unit (Vitest) | `pnpm --filter web test -- mailbox-hooks && pnpm --filter web exec tsc --noEmit` | ✅ self (mailbox-hooks.test.ts) | ⬜ pending |
| 11-06-02 | 06 | 5 | UX-02, UX-03, UX-04, UX-05, UX-06 | T-11-06-02 / T-11-06-05 | AccountMenu switcher distinct from workspace identity; copy-rules dialog; previews show source+executing mailbox; token classes only | typecheck/lint + browser | `pnpm --filter web exec tsc --noEmit && pnpm --filter web exec eslint apps/web/components/shell/AppSidebar.tsx apps/web/features/rules/components/CopyRulesDialog.tsx` | ✅ self | ⬜ pending |
| 11-06-03 | 06 | 5 | VER-04 | T-11-06-01 / T-11-06-02 | Playwright connect/list/switch/rules/send-from flows green (mocked); switcher reachable at 320px | e2e (Playwright) | `pnpm --filter web exec playwright test e2e/mailbox-switch.spec.ts e2e/mailbox-rules.spec.ts e2e/mailbox-send-from.spec.ts` | ✅ self (3 specs) | ⬜ pending |
| 11-06-04 | 06 | 5 | VER-04 | T-11-06-04 | Real-Gmail multi-mailbox smoke: connect → ingest → switch → send-from, no cross-account bleed, AUD-07-clean logs | manual (human-verify) | `<human-check>` — see Manual-Only Verifications | n/a (live VPS) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Note: every code-producing task carries an `<automated>` command. The two compile-only Wave 0 tasks (11-01-02, 11-01-03) are RED-by-design scaffolds that turn green via the production tasks above (Migration/PubSub/Observed/Rules/Outbound/CrossAccount). The single manual task (11-06-04) is the live real-Gmail smoke that cannot run in CI; it is preceded by 11-06-03 (automated Playwright) so the sampling chain has no 3-consecutive-without-automated gap.*

---

## Wave 0 Requirements

- [x] Cross-account isolation test harness — two-CONNECTED-mailbox fixture (covers Phase 10 CR-01 shim gap, AUD-06) — created by Plan 01 Task 3 (`CrossAccountIsolationTest`) + Task 2 (`OldTwoMailboxFixture`); turned green by Plan 05 Task 2
- [x] ArchUnit boundary rules — `buildClientForTenant` allow-list drain + complementary `findByTenantId` rule (AUD-05) — `findByTenantId` rule added by Plan 01 Task 1; both lists drained to residual by Plan 05 Task 2
- [x] Playwright real-browser fixtures for connect/list/switch/mailbox-owned-rules/send-from/audit (VER-04) — three specs created by Plan 06 Task 3; real-Gmail smoke is Plan 06 Task 4 manual checkpoint

*Wave 0 complete: Plan 01 (Wave 1) creates all RED scaffolds + the two-mailbox fixture + the new ArchUnit rule before any production code lands.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-Gmail multi-mailbox smoke (connect → ingest → switch → send-from) | VER-04 | Requires live Google OAuth + real Pub/Sub push on dev VPS; cannot run in CI | Plan 06 Task 4 checkpoint: connect 2nd Gmail mailbox on dev, observe ingestion, switch active mailbox, send from each, confirm operational isolation + AUD-07-clean logs |

*Preceded by Plan 06 Task 3 (automated Playwright e2e) so no 3-consecutive-without-automated gap precedes the manual step.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — every code task carries an `<automated>` command; the one manual task (11-06-04) is immediately preceded by automated Playwright (11-06-03)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — the only manual task is the final phase task, preceded by automated e2e
- [x] Wave 0 covers all MISSING references — Plan 01 creates `CrossAccountIsolationTest`, `OldTwoMailboxFixture`, the `findByTenantId` ArchUnit rule, and the six RED invariant scaffolds before Waves 2-5
- [x] No watch-mode flags — all commands are one-shot (`./gradlew ... --tests`, `pnpm ... test --`, `playwright test`, `tsc --noEmit`); none use `--watch`
- [x] Feedback latency < 120s — focused `--tests` scopes run in ~60–120s; the full suite is avoided per the connection-ceiling note
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-09
</content>
</invoke>
