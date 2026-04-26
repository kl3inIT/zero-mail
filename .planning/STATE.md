---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 1.2 Plan 06 complete (DomainBoundaryArchTests + 2 cross-domain decompositions + canonical Pattern 8 cleanup; full ./gradlew clean check green)
last_updated: "2026-04-26T10:12:58.249Z"
last_activity: 2026-04-26 -- Phase 1.3 planning complete
progress:
  total_phases: 11
  completed_phases: 3
  total_plans: 31
  completed_plans: 23
  percent: 74
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-24)

**Core value:** AI auto-triage that users trust with their real Gmail inbox — triage quality, safety (no destructive or silently-sent actions), and reliability are non-negotiable.
**Current focus:** Phase 1.3 — Frontend Architecture Refactor and Public Content Foundation (INSERTED)

## Current Position

Phase: 1.3 (Frontend Architecture Refactor and Public Content Foundation (INSERTED)) — READY
Plan: — (Phase 1.2 complete; awaiting Phase 1.3 plan/discuss)
Status: Ready to execute
Last activity: 2026-04-26 -- Phase 1.3 planning complete

Progress: [████████████] 100% of Phase 1.2 (24/30 total plans = 80%)

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: (none yet)
- Trend: —

*Updated after each plan completion*
| Phase 01.1 P04 | 30min | 2 tasks | 5 files |
| Phase 01.1 P05 | 30min | 3 tasks | 13 files |
| Phase 1.1 P06 | 24min | 2 tasks | 13 files |
| Phase 1.1 P07 | 45min | 2 tasks | 11 files |
| Phase 1.1 P8 | 14min | 4 tasks | 5 files |
| Phase 1.2 P01 | 9min | 3 tasks | 11 files |
| Phase 1.2 P02 | 17min | 2 tasks | 16 files |
| Phase 1.2 PP03 | 9min | 3 tasks | 27 files |
| Phase 1.2 P04 | 6min | 3 tasks | 18 files |
| Phase 1.2 P05 | 11min | 3 tasks | 30 files |
| Phase 01.2 P06 | 25min | 3 tasks | 9 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: 8-phase topology with parallel sub-phases 2A/2B/2C (standard granularity, research-aligned)
- Roadmap: Phase 2C (LLM Gateway) hard-gated by Phase 1 safety infrastructure — prompt injection + log bleed are product-killing
- Roadmap: Phase 4 (Triage) hard-gated by Phase 2C — no triage without sanitization, Unicode strip, allow-list
- Roadmap: CASA restricted-scope verification tracked as external parallel track, initiated in Phase 1 (FND-07), closed before Phase 6 launch
- [Phase ?]: Use RestClient + LocalServerPort (not MockMvc.webAppContextSetup) for backend tests requiring TenantContext ScopedValue — MockMvc skips servlet filters and the test auth filter never binds the ScopedValue
- [Phase ?]: Phase 1.1 P06: Vitest dedupes react/react-dom + LanguageSwitcher inlines SVG/native button to escape pnpm's duplicate React install
- [Phase ?]: Phase 1.1 P07: Playwright must live at workspace root because Next.js declares @playwright/test as an optional peer dep — installing under apps/web doubles the next install on disk and breaks tsc at the next-intl/middleware boundary
- [Phase 1.2]: CL-3 Spring Modulith naming form locked = `"shared.privacy"` (dotted-nested literal). Probe A passed first try in `core.tenant/package-info.java`; Probes B (`"privacy"`) and C (`"shared :: privacy"`) not run. Plans 02-05 MUST use this exact literal in `allowedDependencies`. Documented in `core/shared/privacy/package-info.java` JavaDoc.
- [Phase 1.2]: Pitfall 1 closure protocol confirmed in practice — every string-typed FQN reference (logback XML `<filter class="..."/>`, ArchUnit literal constants, build-script `approvedPkg`, integration-test imports) MUST update in the same plan as Java class moves. Surface scan via `grep -rn 'old.fqn' backend/ apps/ buildSrc/` is the verification gate.
- [Phase 1.2]: Plan 02 confirmed the per-domain `persistence/` + `persistence/lowlevel/` shape (intra-domain marker, no `@ApplicationModule`); Plans 03/04/05 reuse this exact shape for account/onboarding/gmail. The proactive `lowlevel/` package-info marker prevents Plan 06's regex-update from silent-no-oping.
- [Phase 1.2]: Plan 02 sweep folded 10 stale-import sites (2 production, 8 test) into the same commit as the `git mv` — no fix-up commit needed (vs Plan 01's `9769dd7`). Discipline: Edit the working tree of renamed files BEFORE staging, never after the first commit.
- [Phase ?]: [Phase 1.2]: Plan 03 confirmed CL-2 reshape pattern — AccountService dropped 4 cross-domain repos, kept only UserRepository; new deleteCurrentUser is single-domain. Multi-domain orchestration moves to AccountDeletionController as transitional bridge across Plans 03→06.
- [Phase ?]: [Phase 1.2]: Forward-decl deferral protocol locked: never declare a Modulith allowedDependencies edge to a non-existent module. core.account/package-info.java declares {tenant, shared.privacy} now; Plan 04 amends to add 'onboarding' once that module exists on disk.
- [Phase 1.2]: Plan 04 closed Pitfall 5 (enum-name persistence drift) via OnboardingStepEnumPersistenceTest — pure-JVM unit test asserting OnboardingStep.{...}.name() match stable strings. Pattern: when relocating @Enumerated(EnumType.STRING) enums, ship a name() literal-assert test in the same plan.
- [Phase 1.2]: Plan 04 confirmed atomic bidirectional Modulith edge protocol: when introducing a new module that an existing module already depends on, declare BOTH edges in the same commit as the new package-info.java (account ↔ onboarding both landed in commit 2f25214).
- [Phase 1.2]: Plan 05 completed CL-2 single-domain delete pattern across all 4 domains: GmailConnectionService.deleteForCurrentTenant + new TenantService.deleteCurrentTenant (first occupant of core.tenant.service). AccountDeletionController bridge now FK-safe 4-call orchestration with zero direct repo injections.
- [Phase 1.2]: Plan 05 collapsed @EntityScan to single root "com.zeromail.core" in both Application.java + CoreTestApplication.java per RESEARCH.md primary recommendation. Forward-compatible for Phase 2A/2B/2C/3/4 — no further entity-scan edits needed.
- [Phase 1.2]: Plan 05 honored D-D4 explicitly: gmail/package-info.java allowedDependencies = {tenant, shared.privacy} — NO account edge. Verified by ApplicationModulesTest.
- [Phase 1.2]: Plan 05 deviation captured: concurrent user activity on STATE.md/01.3 docs auto-staged executor's pending Java edits into commits 03b5652 + eabbdca. No functional impact (all tests pass), but Task 3's Java work appears in those commits rather than a dedicated executor commit. Documented in 01.2-05-SUMMARY.md.
- [Phase ?]: [Phase 1.2]: Plan 06 closed Wave 0 ArchUnit gap with DomainBoundaryArchTests (4 explicit per-domain rules). Predicate composition pattern locked at DescribedPredicate level (DSL-level .and() chain unsupported in ArchUnit 1.4.x — confirmed via sources jar). Pattern documented in class-level JavaDoc for future-domain authors.
- [Phase ?]: [Phase 1.2]: Plan 06 caught + decomposed 2 lingering cross-domain repo injections that Plans 03/04 silently shipped (T-01.2-H regression). Pattern: extend CL-2 service-to-service primitive shape to non-delete writes — TenantService.createTenant + AccountService.advanceOnboardingStep mirror the delete-cascade primitives. ArchUnit threat-test-as-defect-finder confirmed working on first execution.
- [Phase ?]: [Phase 1.2]: Phase 1.2 structurally complete. Source tree contains exactly {account, gmail, onboarding, shared, tenant} per RESEARCH.md target. Full ./gradlew clean check green: 115 tests / 30 classes / 0 failures. Byte-identical contract preserved (zero diff in db/changelog, libs.versions.toml, apps/web/openapi).

### Roadmap Evolution

- Phase 1.1 inserted after Phase 1: Vietnamese-first i18n and error-handling foundation — default Vietnamese / secondary English, language switcher, stable frontend-localizable API error contract; references local JHipster patterns; preserves all Phase 1 privacy/safety constraints (URGENT)
- Phase 1.2 inserted after Phase 1.1: Domain-owned persistence restructuring — refactor `backend/core` into domain-owned service/persistence/model packages; add a small shared package for stable cross-cutting infrastructure; preserve schema and safety constraints; enforce boundaries with Modulith or ArchUnit (URGENT)
- Phase 1.3 inserted after Phase 1.2: Frontend Architecture Refactor and Public Content Foundation — reorganize `apps/web` with Next.js route groups, feature folders (`api/`, `components/`, `hooks/`), typed OpenAPI boundaries, workspace cleanup, Prettier/Husky/lint-staged gates, and landing/docs scaffolding without final content design (URGENT)
- Phase 1.2.1 inserted after Phase 1.2: Shared base entity hierarchy (`AbstractEntity`/`AbstractAuditableEntity`/`AbstractTenantOwnedEntity` in `core.shared.persistence`) + `IdentifiedEnum` standard (id/weight/labelKey, applied to `OnboardingStep` + `GmailConnectionStatus`) + `backend/api/dto/` group-by-domain (with `TenantStatusResponse` → `GmailConnectionStatusResponse` rename) + close code-review WR-01/WR-02/WR-03; closes structural-cleanup gaps Phase 1.2 intentionally deferred (URGENT)

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- Phase 2A and Phase 2C are both flagged for `/gsd-research-phase` before planning — do not skip (Gmail watch/history + OIDC verification for 2A; Spring AI 2.0.0-M4 BYOK builder API + tokenizer for 2C).
- CASA verification is a 4–12 week external clock — must be initiated during Phase 1 execution, not deferred.
- Open decisions deferred to phase execution: credit unit economics (Phase 2B), tokenizer choice (Phase 2C), payment provider Stripe vs LemonSqueezy (Phase 2B), observability vendor (any), CASA tier (Phase 1/6).

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260426-a5s | Add Spring Boot Docker Compose support to backend/api so dev startup auto-launches Postgres + Redis from docker-compose.yml | 2026-04-26 | 1219ec8 | [260426-a5s-add-spring-boot-docker-compose-support-t](./quick/260426-a5s-add-spring-boot-docker-compose-support-t/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — project init)* | | | |

## Session Continuity

Last session: 2026-04-26T09:54:10.379Z
Stopped at: Phase 1.2 Plan 06 complete (DomainBoundaryArchTests + 2 cross-domain decompositions + canonical Pattern 8 cleanup; full ./gradlew clean check green)
Resume file: None
