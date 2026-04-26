---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 01.2.1 Plan 02 complete (core.shared.lang Modulith leaf module — IdentifiedEnum + OrderedEnum interface contract; documents D-C2 id()==name() invariant + D-C3 AttributeConverter migration trigger; check green)
last_updated: "2026-04-26T13:08:00Z"
last_activity: 2026-04-26 -- Phase 01.2.1 Plan 02 complete
progress:
  total_phases: 12
  completed_phases: 3
  total_plans: 35
  completed_plans: 27
  percent: 77
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-24)

**Core value:** AI auto-triage that users trust with their real Gmail inbox — triage quality, safety (no destructive or silently-sent actions), and reliability are non-negotiable.
**Current focus:** Phase 01.2.1 — shared-base-entity-and-enum-standard

## Current Position

Phase: 01.2.1 (shared-base-entity-and-enum-standard) — EXECUTING
Plan: 3 of 4
Status: Executing Phase 01.2.1 — Plan 02 complete; Plan 03 next
Last activity: 2026-04-26 -- Phase 01.2.1 Plan 02 complete (core.shared.lang Modulith leaf module — IdentifiedEnum + OrderedEnum)

Progress: [█████████████] 2/4 plans of Phase 01.2.1 (27/35 total plans = 77%)

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
| Phase 01.3 P01 | 6min | 2 tasks | 6 files |
| Phase 01.3 P02 | 6min | 2 tasks | 7 files |
| Phase 01.2.1 P01 | 14min | 3 tasks | 15 files |
| Phase 01.2.1 P02 | 5min | 1 task | 3 files |

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
- [Phase ?]: [Phase 1.3]: Plan 01 — Wave 0 architecture/cleanup test spine landed (6 vitest files, 56 assertions: 45 RED + 10 GREEN + 1 SKIP). Static existsSync predicate at module load (REVIEWS Revision 4) replaces broken beforeAll+skipIf permanent-skip pattern. Non-literal dynamic-import spec defers Vite import-analysis to runtime — fixes ERR_MODULE_NOT_FOUND that @vite-ignore alone does not solve. Pattern locked: pure-fs/regex Wave 0 tests with no @/features/ runtime imports survive every refactor wave.
- [Phase ?]: [Phase 1.3]: Plan 02 — pre-commit gate live (Husky 9 + lint-staged + Prettier 3 root-level). Empirically verified: hooks fired on Task 1 + Task 2 commits. lint-staged in WARN-ONLY i18n:check mode (`|| true`) until Plan 07 final task; `!(messages)` extglob de-duplicates Prettier across overlapping globs (REVIEWS Revisions 3 + 5).
- [Phase ?]: [Phase 1.3]: Plan 02 — `transpilePackages: ['next-mdx-remote']` pre-declared in apps/web/next.config.ts. Pattern locked: pre-declare Turbopack-transpile entries when the runtime dep lands in a future plan; entry is a no-op until first import (Pitfall 6 mitigation, Plan 06 activates).
- [Phase 01.2.1]: Plan 01 — 3-tier @MappedSuperclass hierarchy (AbstractEntity → AbstractAuditableEntity → AbstractTenantOwnedEntity) landed in core.shared.persistence with @EntityListeners(AuditingEntityListener.class) on Tier 2. JpaAuditingConfig wires Clock-bean → DateTimeProvider → @EnableJpaAuditing. FND-05 MultiTenantLeakIntegrationTest still passes (PROVES @TenantId binding survives @MappedSuperclass relocation in Hibernate 7).
- [Phase 01.2.1]: Plan 01 — Liquibase changeset 007 deviation captured: gmail_connections table (created in changeset 003) was missing created_at column. Fix prepended to changeset 007: addColumn created_at + backfill from COALESCE(connected_at, NOW()) + promote NOT NULL. Pattern locked: defaultValueComputed: now() on all new audit columns (created_at, updated_at) so raw-SQL inserts in tests still pass NOT NULL constraint; AuditingEntityListener still wins for entity-managed paths.
- [Phase 01.2.1]: Plan 01 — UserEntity.advanceTo() retains ordinal()-based body. Plan 03 swaps to weight() once OrderedEnum lands in Plan 02 (per CONTEXT D-B5 + WR-02).
- [Phase 01.2.1]: Plan 02 — core.shared.lang Modulith leaf module landed (IdentifiedEnum + OrderedEnum interfaces + package-info @ApplicationModule(displayName="Lang", allowedDependencies={})). Pure interface introduction, zero consumers; Plan 03 wires OnboardingStep (implements OrderedEnum, weights 10/20/30/40) and GmailConnectionStatus (implements IdentifiedEnum, no weight per D-B5). Locks D-B1 two-interface split, D-B2 String id type, D-B3 labelKey default = `<ClassSimpleName>.<id>`, D-B4 fromId fail-loud (NoSuchElementException not IllegalArgumentException). Documents D-C2 id()==name() invariant (enforced via convention + @Enumerated(STRING) + Plan 03 OnboardingStepPersistenceTest; ArchTest enforcement OUT of scope) and D-C3 AttributeConverter migration trigger (deferred to first per-enum decoupling need).
- [Phase 01.2.1]: Plan 02 — JetBrains MCP file-problem check unavailable in sequential-executor agent tool set (upstream issue anthropics/claude-code#13898). Fallback: `./gradlew :backend:core:check :backend:api:check` BUILD SUCCESSFUL is a strict superset for pure-interface files (javac + ArchUnit + ApplicationModulesTest covers correctness). Documented in 01.2.1-02-SUMMARY.md as tooling deviation; no code deviation.

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

Last session: 2026-04-26T13:08:00Z
Stopped at: Phase 01.2.1 Plan 02 complete (core.shared.lang Modulith leaf module — IdentifiedEnum + OrderedEnum interfaces; check green; Plan 03 next will apply interfaces to OnboardingStep + GmailConnectionStatus)
Resume file: None
