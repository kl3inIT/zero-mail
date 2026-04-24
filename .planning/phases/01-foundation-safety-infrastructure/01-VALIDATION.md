---
phase: 1
slug: foundation-safety-infrastructure
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-24
updated: 2026-04-24
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

Every code-producing task in phase 1 maps to a secure behavior, a requirement ID, and an automated verify command. Wave-0 scaffolding tasks (plans 01, 02 task 1, 04 task 1) are prerequisites that create the test fixtures other tasks depend on — all listed as `prereq` so every later row has a real automated command.

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|--------|
| 01-T1 | 01 | 1 | FND-01..06 (scaffold) | Reproducible Gradle 9.4.1 + Java 25 build, pinned Spring Modulith version | prereq (build) | `./gradlew --version \| grep -E "Gradle 9\.4\.1" && ./gradlew projects` | ⬜ pending |
| 01-T2 | 01 | 1 | FND-01..06 (scaffold) | Runnable api + worker Spring Boot 4.0.6 shells compile | prereq (build) | `./gradlew :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava` | ⬜ pending |
| 02-T1 | 02 | 2 | FND-01, FND-02 | ScopedValue-backed tenant primitives + Hibernate resolver + Modulith packages compile | prereq (build) | `./gradlew :backend:core:compileJava :backend:api:compileJava` | ⬜ pending |
| 02-T2 | 02 | 2 | FND-01, FND-02, FND-05 | ArchUnit bans ThreadLocal + raw virtual threads + native SQL; `ApplicationModules.verify()` passes | ArchUnit + Modulith unit | `./gradlew :backend:core:test --tests "com.zeromail.core.tenant.*" --tests "com.zeromail.core.arch.*" :backend:api:test --tests "com.zeromail.api.ApplicationModulesTest"` | ⬜ pending |
| 03-T1 | 03 | 2 | FND-03, FND-04 | `@Sensitive<T>` wrapper + Jackson serializer + Logback TurboFilter compile | prereq (build) | `./gradlew :backend:core:compileJava` | ⬜ pending |
| 03-T2 | 03 | 2 | FND-03, FND-04 | Sensitive toString redacts; TurboFilter replaces `Sensitive(` with `[REDACTED](` and stamps MDC | Unit | `./gradlew :backend:core:test --tests "com.zeromail.core.privacy.*"` | ⬜ pending |
| 03-T3 | 03 | 2 | FND-03, FND-04 | ArchUnit rule `sensitive_names_wrapped` fails on String-typed deny-listed fields; `no_sensitive_in_logger` fails on Logger call with Sensitive arg | ArchUnit | `./gradlew :backend:core:test --tests "com.zeromail.core.arch.SafetyContractArchTests"` | ⬜ pending |
| 04-T1 | 04 | 2 | FND-05, AUTH-02 | Liquibase YAML changelogs package into core resources; runtime config in api/application.yml only | prereq (resources) | `./gradlew :backend:core:processResources && test -f backend/core/build/resources/main/db/changelog/db.changelog-master.yaml && test ! -f backend/core/src/main/resources/application.yml` | ⬜ pending |
| 04-T2 | 04 | 2 | FND-05, AUTH-02 | JPA entities carry `@TenantId`; `UserRepository.findFirstByTenantId(UUID)` exists | prereq (build) | `./gradlew :backend:core:compileJava` | ⬜ pending |
| 04-T3 | 04 | 2 | FND-05, AUTH-02 | Liquibase applies schema against real Postgres 17.6; `uq_gmail_connections_tenant_id` rejects duplicate insert | Integration (Testcontainers) | `./gradlew :backend:core:test --tests "com.zeromail.core.persistence.LiquibaseMigrationTest" --tests "com.zeromail.core.persistence.GmailConnectionUniquenessTest"` | ⬜ pending |
| 05-T1 | 05 | 3 | AUTH-01, AUTH-04, FND-01 | Spring Security 7 dual OAuth2 client regs + TenantBindingFilter + CSRF cookie compile | prereq (build) | `./gradlew :backend:api:compileJava` | ⬜ pending |
| 05-T2 | 05 | 3 | AUTH-05 | `invalid_grant` → `OAuth2TokenRefreshFailed` event → `GmailAccessGuard` flips status to DISCONNECTED (single `UUID tenant` declaration) | prereq (build) | `./gradlew :backend:api:compileJava` | ⬜ pending |
| 05-T3 | 05 | 3 | FND-05, AUTH-05, AUTH-04, AUTH-02 | 100+ concurrent virtual-thread requests with real Spring-Session-minted cookies never cross tenant; session cookie is HttpOnly + SameSite=Lax; invalid_grant flips row | Integration (Testcontainers + TestSessionSupport) | `./gradlew :backend:api:test --tests "com.zeromail.api.security.*"` | ⬜ pending |
| 06-T1 | 06 | 3 | AUTH-03 | AES-GCM-256 envelope round-trips; tenantId AAD mismatch fails; unknown key_version rejected; 10,000 unique nonces (explicit `java.security.GeneralSecurityException` import) | Unit | `./gradlew :backend:core:test --tests "com.zeromail.core.crypto.*"` | ⬜ pending |
| 07-T1 | 07 | 4 | FND-06, AUTH-02, AUTH-03, AUTH-05, AUTH-06 | springdoc publishes OpenAPI 3.1 with `info.version=0.1.0`; controllers use `UserRepository.findFirstByTenantId` (no `findAll().stream().filter`) | prereq (build) | `./gradlew :backend:api:compileJava` | ⬜ pending |
| 07-T2 | 07 | 4 | FND-06, AUTH-03, AUTH-06 | `/v3/api-docs` contains Phase 1 paths; DELETE /me/account cascades to zero rows; onboarding state machine is forward-only | Integration (Testcontainers) | `./gradlew :backend:api:test --tests "com.zeromail.api.OpenApiSchemaTest" --tests "com.zeromail.api.AccountDeletionE2ETest" --tests "com.zeromail.api.OnboardingStateMachineTest"` | ⬜ pending |
| 08-T1 | 08 | 5 | FND-06, AUTH-02, AUTH-03, AUTH-05, AUTH-06 | Next.js scaffold + pnpm workspace + shadcn + openapi-typescript/openapi-fetch builds | Frontend build | `cd apps/web && pnpm install && pnpm lint && pnpm build` | ⬜ pending |
| 08-T2 | 08 | 5 | AUTH-02, AUTH-03, AUTH-05, AUTH-06 | `/login`, `/onboarding`, `/settings` render per UI-SPEC; ConnectionHealthBadge and DeleteAccountDialog match copy | Frontend build | `cd apps/web && pnpm build` | ⬜ pending |
| 08-T3 | 08 | 5 | FND-06 | End-to-end codegen: real bootRun api → `/v3/api-docs` → `openapi-typescript` → `schema.d.ts` contains every Phase 1 path (placeholder stub replaced) | Shell + codegen | `bash scripts/verify-codegen.sh` | ⬜ pending |
| 09-T1 | 09 | 5 | FND-03 | Real authenticated request traffic (`/me`, `/tenant/status`, `/onboarding/select-template`) with sentinel seed data (`leak-probe-12345`, `LEAK-REFRESH-TOKEN-ABC`) produces a log stream with zero sentinel occurrences; `scrubbed=true` MDC key observed at least once | Integration (Testcontainers + TestSessionSupport + ListAppender) | `./gradlew :backend:api:test --tests "com.zeromail.api.LogScrubSyntheticTrafficTest"` | ⬜ pending |
| 09-T2 | 09 | 5 | FND-07, AUTH-05 | CASA package drafted (`submission-log.md`, `privacy-policy-draft.md`, `scopes-justification.md`, `data-handling-attestation.md`) with AUTH-05 invalid_grant narrative cross-referenced | File presence | `test -f docs/casa/submission-log.md && test -f docs/casa/scopes-justification.md && test -f docs/casa/data-handling-attestation.md && test -f docs/casa/privacy-policy-draft.md && grep -q "AUTH-05" docs/casa/scopes-justification.md` | ⬜ pending |
| 09-T3 | 09 | 5 | FND-07 | CASA filing captured in submission-log.md with real submission ID + lab name | Manual (human checkpoint) | Human verification: `grep -v TBD docs/casa/submission-log.md \| grep -i submission-id` returns a real value | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*Every task maps to a requirement and an automated verify command (or a Wave 0 prereq that creates its fixtures).*

---

## Wave 0 Requirements

- [x] Gradle multi-project skeleton (`backend/core`, `backend/api`, `backend/worker`, `apps/web`) with Java 25 toolchain — covered by plan 01 task 1
- [x] `buildSrc/` convention plugins: `zeromail.java-conventions`, `zeromail.spring-boot-conventions`, `zeromail.archunit-conventions`, `zeromail.modulith-conventions` — covered by plan 01 task 1
- [x] `libs.versions.toml` with all locked versions from CLAUDE.md (Spring Modulith pinned to `2.0.7-SNAPSHOT`) — covered by plan 01 task 1
- [x] Liquibase 5.0.2 baseline changelog at `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — covered by plan 04 task 1
- [x] Testcontainers JUnit 5 base classes for Postgres 17.6 + Redis 7.2 — covered by plan 04 task 3 (`PostgresContainerTest`)
- [x] ArchUnit test scaffolding (JUnit 5 engine, `@AnalyzeClasses` targeting `com.zeromail` root) — covered by plan 02 task 2
- [x] Spring Modulith `ApplicationModulesTest` scaffolding in `backend/api` (not core — avoids reversed module dep) — covered by plan 02 task 2
- [x] Test stub files for each FND-0X / AUTH-0X requirement — covered by per-task rows above (every row has an automated command)
- [x] TestSessionSupport `@TestConfiguration` minting real Spring Sessions for tenant-scoped integration tests — covered by plan 05 task 3

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CASA restricted-scope submission filed with external lab | AUTH-05 (implicit — success criterion #5) / FND-07 | External portal; no API to assert against | Screenshot submission confirmation; attach to phase SUMMARY.md |
| Google OAuth consent screen configured in Testing tier with two-scope incremental flow | AUTH-01, AUTH-02 | Configured in Google Cloud Console UI | Reviewer follows `/login` → `/onboarding` → "Connect Gmail" end-to-end in a browser against a real Google test account |

The log-stream grep for FND-03 is automated in `LogScrubSyntheticTrafficTest` (plan 09 task 1) against real request paths; it is no longer a manual-only verification.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags (Gradle runs are one-shot)
- [x] Feedback latency < 30s quick / < 5 min full
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** signed off for execution (revision 1, 2026-04-24)
