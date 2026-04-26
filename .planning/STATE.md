---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 1.2 plan 02 complete; plan 03 (account domain move) ready
last_updated: "2026-04-26T08:51:00.000Z"
last_activity: 2026-04-26 -- Plan 01.2-02 complete; tenant persistence relocated, FND-05 + Modulith verify both green
progress:
  total_phases: 11
  completed_phases: 3
  total_plans: 23
  completed_plans: 19
  percent: 83
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-24)

**Core value:** AI auto-triage that users trust with their real Gmail inbox — triage quality, safety (no destructive or silently-sent actions), and reliability are non-negotiable.
**Current focus:** Phase 1.2 — Domain-owned persistence restructuring (INSERTED)

## Current Position

Phase: 1.2 (Domain-owned persistence restructuring (INSERTED)) — EXECUTING
Plan: 2 of 6
Status: Plans 01-02 complete; ready for Plan 03
Last activity: 2026-04-26 -- Plan 01.2-02 complete; TenantEntity/Repository relocated to core.tenant.persistence; FND-05 leak gate green

Progress: [████████░░] 83%

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

### Roadmap Evolution

- Phase 1.1 inserted after Phase 1: Vietnamese-first i18n and error-handling foundation — default Vietnamese / secondary English, language switcher, stable frontend-localizable API error contract; references local JHipster patterns; preserves all Phase 1 privacy/safety constraints (URGENT)
- Phase 1.2 inserted after Phase 1.1: Domain-owned persistence restructuring — refactor `backend/core` into domain-owned service/persistence/model packages; add a small shared package for stable cross-cutting infrastructure; preserve schema and safety constraints; enforce boundaries with Modulith or ArchUnit (URGENT)
- Phase 1.3 inserted after Phase 1.2: Frontend Architecture Refactor and Public Content Foundation — reorganize `apps/web` with Next.js route groups, feature folders (`api/`, `components/`, `hooks/`), typed OpenAPI boundaries, workspace cleanup, Prettier/Husky/lint-staged gates, and landing/docs scaffolding without final content design (URGENT)

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

Last session: 2026-04-26T08:51:00.000Z
Stopped at: Plan 01.2-02 complete; ready for Plan 01.2-03 (account domain move + AccountService CL-2 reshape)
Resume file: .planning/phases/01.2-domain-owned-persistence-restructuring/01.2-03-PLAN.md
