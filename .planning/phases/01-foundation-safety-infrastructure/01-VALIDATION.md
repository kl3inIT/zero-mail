---
phase: 1
slug: foundation-safety-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-24
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (backend) + Testcontainers (Postgres 17.6, Redis 7.2) + ArchUnit 1.3.x; Vitest/Playwright TBD for `apps/web` (minimal UI this phase) |
| **Config file** | `backend/*/build.gradle.kts` (Gradle test tasks), `buildSrc/src/main/kotlin/zeromail.archunit-conventions.gradle.kts` |
| **Quick run command** | `./gradlew :backend:core:test :backend:api:test --tests '*UnitTest'` |
| **Full suite command** | `./gradlew check` (runs unit + integration + ArchUnit + modulith-verify) |
| **Estimated runtime** | Quick: ~20s; Full: ~3–5 min (Testcontainers bootstrap dominates) |

---

## Sampling Rate

- **After every task commit:** Run quick command (unit + ArchUnit on touched module)
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green, including Spring Modulith `ApplicationModules.verify()` and concurrent multi-tenant leak test
- **Max feedback latency:** 30s for quick, 5 min for full

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|--------|
| TBD — populated by planner | — | — | — | — | — | — | ⬜ pending |

*Filled in during planning. Every task must map to a requirement and an automated verify command (or declare a Wave 0 dependency).*
*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Gradle multi-project skeleton (`backend/core`, `backend/api`, `backend/worker`, `apps/web`) with Java 25 toolchain
- [ ] `buildSrc/` convention plugins: `zeromail.java-conventions`, `zeromail.spring-boot-conventions`, `zeromail.archunit-conventions`, `zeromail.modulith-conventions`
- [ ] `libs.versions.toml` with all locked versions from CLAUDE.md
- [ ] Liquibase 5.0.2 baseline changelog at `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`
- [ ] Testcontainers JUnit 5 base classes for Postgres 17.6 + Redis 7.2
- [ ] ArchUnit test scaffolding (JUnit 5 engine, `@AnalyzeClasses` targeting `com.zeromail` root)
- [ ] Spring Modulith `ApplicationModulesTest` scaffolding in `backend/core`
- [ ] Test stub files for each FND-0X / AUTH-0X requirement (tagged `@Tag("requirement:FND-01")` etc.)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CASA restricted-scope submission filed with external lab | AUTH-05 (implicit — success criterion #5) | External portal; no API to assert against | Screenshot submission confirmation; attach to phase SUMMARY.md |
| Google OAuth consent screen configured in Testing tier with two-scope incremental flow | AUTH-01, AUTH-02 | Configured in Google Cloud Console UI | Reviewer follows `/login` → `/onboarding` → "Connect Gmail" end-to-end in a browser against a real Google test account |
| Grep-for-bodies check across synthetic traffic run log | FND-03 (success criterion #3) | Requires a traffic generator + log file produced by a running app | Run synthetic request suite, tail app logs to file, `grep -E "body\|prompt\|completion\|refresh_token\|Sensitive\(" app.log` returns 0 matches |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags (Gradle runs are one-shot)
- [ ] Feedback latency < 30s quick / < 5 min full
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
