---
phase: 11
slug: mailbox-scoped-ingestion-automation-ui-and-verification
status: draft
nyquist_compliant: false
wave_0_complete: false
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

> Derived during planning/execution from RESEARCH.md § "Validation Architecture" (12-row requirement→test map). Populate per plan task.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | ING-01 | T-11-01 / — | Pub/Sub delivery resolves (tenant, mailbox); unknown mailbox fails/drops safely | integration | `./gradlew :backend:core:test --tests "*PubSub*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Cross-account isolation test harness — two-CONNECTED-mailbox fixture (covers Phase 10 CR-01 shim gap, AUD-06)
- [ ] ArchUnit boundary rules — `buildClientForTenant` allow-list drain + complementary `findByTenantId` rule (AUD-05)
- [ ] Playwright real-browser fixtures for connect/list/switch/mailbox-owned-rules/send-from/audit (VER-04)

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-Gmail multi-mailbox smoke (connect → ingest → switch → send-from) | VER-04 | Requires live Google OAuth + real Pub/Sub push on dev VPS; cannot run in CI | Connect 2nd Gmail mailbox on dev, observe ingestion, switch active mailbox, send from each, confirm operational isolation |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
