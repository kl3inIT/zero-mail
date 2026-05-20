---
phase: 08
slug: admin-console-operator-tooling
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-19
---

# Phase 08 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (backend) + Vitest (apps/admin) + Playwright (apps/admin e2e) |
| **Config file** | `backend/build.gradle.kts`, `apps/admin/vite.config.ts`, `apps/admin/playwright.config.ts` |
| **Quick run command** | `./gradlew test --tests <fully-qualified-test>` (single test) or `pnpm --filter @zeromail/admin test:unit -- <pattern>` |
| **Full suite command** | `./gradlew test && pnpm -r test && pnpm --filter @zeromail/admin e2e` |
| **Estimated runtime** | ~{N} seconds — populated by planner per plan |

---

## Sampling Rate

- **After every task commit:** Run focused quick command for the task's package or feature
- **After every plan wave:** Run plan-scoped suite (`./gradlew :backend:api:test :backend:core:test` or `pnpm --filter @zeromail/admin test`)
- **Before `/gsd:verify-work`:** Full suite must be green + ArchUnit gates green + `MasterKeySentinelLeakTest` green
- **Max feedback latency:** {N} seconds — populated by planner

---

## Per-Task Verification Map

> Populated by gsd-planner per plan. One row per task across 08-01 through 08-06.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | OPS-INFRA-01 | T-08-XX / — | placeholder | unit | `placeholder` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

> Test scaffolding the planner emits BEFORE Wave 1 starts. Populated per-plan during planning.

- [ ] `backend/api/src/test/java/com/zeromail/admin/AdminChainCookieIsolationTest.java` — chain-isolation acceptance test (Q1 findings)
- [ ] `backend/api/src/test/java/com/zeromail/admin/AdminPathBodyBanTest.java` — ArchUnit rule
- [ ] `backend/core/src/test/java/com/zeromail/audit/AuditChainIntegrityTest.java` — HMAC chain validation
- [ ] `backend/core/src/test/java/com/zeromail/llm/MasterKeySentinelLeakTest.java` — leak scan
- [ ] `apps/admin/src/test-setup.ts` — Vitest + Testing Library setup
- [ ] `apps/admin/playwright.config.ts` — Playwright config for admin SPA

*Additional Wave 0 items populated by planner per sub-plan.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| WebAuthn enrollment ceremony with a real authenticator (YubiKey / platform passkey) | ADMIN-10 | Hardware-bound credential — emulator covers protocol round-trip but not real authenticator UX | Bootstrap admin user, retrieve STDOUT enrollment URL, complete ceremony on physical device, verify `admin_users.passkey_credential_id` row exists |
| docker compose + NPM subdomain cert provisioning on real VPS | OPS-INFRA-01, OPS-INFRA-02 | Live Let's Encrypt issuance requires public DNS | Run `docs/ops/v1.2-deploy.md` runbook on staging VPS |
| Admin response body leak under concurrent load | ARCH-09 | `AdminResponseBodyBanFilter` integration test covers single-request; concurrent leak detection needs k6/Gatling load probe | Run load test scenario in `docs/ops/admin-load-probe.md` (planner will create) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < {N}s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
