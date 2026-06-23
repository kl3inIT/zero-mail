---
phase: 12-calendar-connection-triage-foundation
plan: G1
subsystem: inbox-calendar-triage
tags: [calendar, inbox, projection, openapi, badge, gap-closure]
requires:
  - "W4 gmail_inbox_projection.message_class + event_dt columns + Optional getters"
  - "W4 InboxProjectionPinningTest (pin ORDER BY)"
provides:
  - "messageClass + eventDt threaded projection -> InboxProjectionMessage -> RecentInboxMessage -> GmailInboxMessageResponse"
  - "GmailInboxMessageResponse @Schema(allowableValues) calendar enum on the OpenAPI doc"
  - "InboxMessageRow exported + Vitest proof of the CANCEL/RESCHEDULE badge branch"
  - "Playwright durable gate e2e/inbox-calendar-badge.spec.ts"
affects:
  - "apps/web inbox calendar badge (CAL-TRIAGE-02 badge half)"
tech-stack:
  added: []
  patterns:
    - "Optional<T> on use-case read records; nullable refs on wire-adjacent records"
    - "@Schema(allowableValues) mirrors IdentifiedEnum.id() exactly, guarded by an enum-exhaustiveness unit test"
key-files:
  created:
    - backend/api/src/test/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponseTest.java
    - apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx
    - apps/web/e2e/inbox-calendar-badge.spec.ts
  modified:
    - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java
    - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java
    - apps/web/features/inbox/components/InboxPageClient.tsx
decisions:
  - "New backend test landed in InboxProjectionReadServiceTest (which autowires the read service and exercises the real toInboxProjectionMessage mapper) rather than InboxProjectionPinningTest (a repository-slice test that never wires the service); InboxProjectionPinningTest left untouched + green."
  - "InboxMessageRow exported (Rule 3) so the badge branch is unit-testable; full-page virtualized render yields no rows in jsdom. Badge branch logic byte-identical."
  - "OpenAPI regen + inbox-api.ts type dedup deferred behind an Authentication Gate (dev SSH tunnel localhost:5555 down). schema.d.ts NOT hand-edited (CLAUDE.md §11)."
  - "Task 4 (W3 calendar type swap) skipped — regen-dependent, and the regen did not land."
  - "REQUIREMENTS.md CAL-TRIAGE-02 left [ ] — the badge does not yet render through the regenerated type chain end-to-end."
metrics:
  duration: "~50m"
  completed: "2026-06-23"
status: blocked-on-gate
---

# Phase 12 Plan G1: CAL-TRIAGE-02 Badge Wiring Summary

Threaded W4's `message_class` + `event_dt` projection columns through the read-side chain (projection record -> RecentInboxMessage -> wire DTO) and proved the previously-dead inbox calendar Badge branch with Vitest, but the OpenAPI regen that would make the frontend consume the new wire fields is blocked on the dev SSH tunnel — so CAL-TRIAGE-02 stays open pending the gate.

## What shipped (committed)

| # | Commit | Scope | Summary |
|---|--------|-------|---------|
| 1 | `99afa8c0` | backend/core | `InboxProjectionMessage` + `RecentInboxReadService.RecentInboxMessage` carry `messageClass`/`eventDt`; projection mapper populates from W4 getters; live-Gmail mapper passes null. Read-service test proves the projection->record map. |
| 2 | `c8a6b3d9` | backend/api | `GmailInboxMessageResponse` exposes the two fields with `@Schema(allowableValues={INVITE,CANCEL,RESCHEDULE,RSVP})` + `@JsonInclude(NON_NULL)`; `from(...)` threads them; 3 DTO tests incl. enum-exhaustiveness tripwire. |
| 3 | `b91cf47e` | apps/web (tests) | `InboxMessageRow` exported; Vitest proves CANCEL/RESCHEDULE/null badge branches (3/3 green); Playwright durable gate spec authored. |

## Closure proof status for CAL-TRIAGE-02

**NOT yet closed.** The badge half requires the full chain to light up end-to-end through the *generated* type. Status by layer:

- Backend chain wired + tested green: **DONE** (commits 1+2).
- OpenAPI regen (`pnpm --filter web run generate:api`): **DEFERRED — Authentication Gate** (dev SSH tunnel `localhost:5555` down; Liquibase fails at backend boot without it, so `/v3/api-docs` cannot serve the new DTO fields). `grep -c messageClass apps/web/lib/api/schema.d.ts` = **0** (unchanged; regen did not run).
- `inbox-api.ts` hand-typed dedup (delete `InboxMessageClass` literal union + `messageClass?`/`eventDt?`, re-export from `components['schemas']`): **DEFERRED** — depends on the regen landing first; doing it now would dangle a `components['schemas']['GmailInboxMessageResponse']['messageClass']` reference against a schema that lacks the field, breaking typecheck.
- Vitest badge proof: **DONE** (3/3 green) — runs against the existing hand-typed `InboxMessage.messageClass`; the regen swaps the *type source*, not the runtime values, so the assertions transfer unchanged after the gate clears.
- Playwright e2e: **authored as a durable gate** (auto-run deferred — no dev server in this session; precedent W3).
- `.planning/REQUIREMENTS.md` line 53: **left `[ ]`** — `grep -n "CAL-TRIAGE-02"` still shows the deferred-closure note. Flipping to `[x]` now would re-commit the exact silent-overclaim that created this gap plan.

## Authentication Gate (blocking)

**Resume signal: "tunnel up"**

The OpenAPI regen needs a freshly-booted `ZeroMailApi` (with the commit-1/2 DTO compiled in) serving `/v3/api-docs`. That boot needs the dev Postgres tunnel for Liquibase. To resume:

1. Start the tunnel in a separate terminal: `ssh -L 5555:localhost:5555 dat@72.62.193.33`
2. Ensure `apps/web/.env.local` carries `INBOX_PROJECTION_KEY_BASE64` + `INBOX_PROJECTION_SENDER_HASH_KEY_BASE64`.
3. Boot backend: `./gradlew :backend:api:bootRun` (wait for `Started ZeroMailApiApplication`).
4. Regen: `pnpm --filter web run generate:api`; verify `grep -c messageClass apps/web/lib/api/schema.d.ts` >= 1.
5. Apply the `inbox-api.ts` dedup, run `pnpm --filter web run typecheck && pnpm --filter web exec eslint features/inbox` + Vitest.
6. (Optional) Task 4 W3 calendar type swap.
7. Flip `.planning/REQUIREMENTS.md` line 53 to `[x]` with the closure note, then mark the requirement complete.

Package installs: **none** — this plan adds zero npm/Maven deps, so no package-legitimacy gate applies.

## Whether Task 4 ran

**Skipped.** Task 4 (collapse the W3 `TODO(12-W3)` deferral by swapping `calendar/types.ts` to the generated schema + promoting `calendar-api.ts` to the typed client) is regen-dependent. Per the plan's decision rule ("Skip if Task 3 deferred the regen"), it is not attempted. The 8 `TODO(12-W3)` markers remain.

## Regenerated schema.d.ts symbol delta

**None yet** — regen blocked. Once the gate clears, `GmailInboxMessageResponse` in `schema.d.ts` should gain exactly:
- `messageClass?: ("INVITE" | "CANCEL" | "RESCHEDULE" | "RSVP") | null`
- `eventDt?: string | null` (`format: date-time`)

## W4 pin invariant — unchanged

`InboxProjectionPinningTest` left byte-untouched and green (3 cases). The ORDER BY pin predicate in `GmailInboxProjectionRepository.findInboxPage` and the partial index were not modified. New backend coverage was added in `InboxProjectionReadServiceTest` (`findInboxPage_carriesMessageClassAndEventDt` + a non-calendar empty-pair case) rather than in the pinning test, to exercise the real read-service mapper.

## Deviations from Plan

### Rule 3 — InboxMessageRow exported to unblock a non-flaky unit test
- **Found during:** Task 3 Vitest authoring.
- **Issue:** The plan said "render the smaller InboxMessageRow if it is exportable; otherwise render the full page client." It is NOT exported, and the full `InboxPageClient` renders zero rows in jsdom (the virtualizer's scroll container measures 0px), so the badge branch could not be reached.
- **Fix:** Added `export` to `InboxMessageRow` with a one-line rationale comment. The CANCEL/RESCHEDULE branch logic (lines ~593-610) is byte-identical.
- **Files modified:** `apps/web/features/inbox/components/InboxPageClient.tsx`
- **Commit:** `b91cf47e`
- **Note vs plan:** The plan's Task 3 acceptance criterion "InboxPageClient.tsx UNCHANGED" is intentionally not met for this minimal export. This is the smallest change that yields a deterministic, non-flaky badge test.

### Test-file placement: backend mapper test in InboxProjectionReadServiceTest, not InboxProjectionPinningTest
- **Found during:** Task 1.
- **Issue:** The plan's item 6 said to extend `InboxProjectionPinningTest`, but that is a pure repository-slice test that never wires `InboxProjectionReadService` — it could only re-assert the entity getters, not the projection->record mapper the must_have targets.
- **Fix:** Added `findInboxPage_carriesMessageClassAndEventDt` + a non-calendar empty-pair case to `InboxProjectionReadServiceTest`, which autowires the read service and exercises the real `toInboxProjectionMessage` mapper end-to-end. `InboxProjectionPinningTest` stays untouched + green.
- **Commit:** `99afa8c0`

### Test-ctor fan-out for widened records
- Three existing test call sites (`RecentInboxReadServiceOrchestratorTest`, `BackfillNeedsReplyServiceTest`, and the same orchestrator test's `InboxProjectionMessage` builder) needed the two new trailing args (`null`/`Optional.empty()`). Updated to keep compilation green. Commit `99afa8c0`.

### gradlew path
- The plan's verify blocks said `cd backend && ./gradlew`, but the wrapper lives at the repo root. Ran from root (`./gradlew :backend:core:test ...`). No functional impact.

## Pre-existing issues touched in passing

None. The known `apps/web/lib/content/frontmatter.ts(1,36): TS2307 Cannot find module 'yaml'` error is the *only* typecheck failure and is explicitly out of scope (separate one-line PR: `pnpm --filter web add yaml`). It was present before this plan and is unchanged.

## Files modified (for commit grouping)

- **backend/core:** `InboxProjectionMessage.java`, `InboxProjectionReadService.java`, `RecentInboxReadService.java` (+ 3 test ctor fixes, 1 new test method) — commit `99afa8c0`
- **backend/api:** `GmailInboxMessageResponse.java` + `GmailInboxMessageResponseTest.java` — commit `c8a6b3d9`
- **apps/web:** `InboxPageClient.tsx` (export) + `inbox-page-client-cancel-badge.test.tsx` + `inbox-calendar-badge.spec.ts` — commit `b91cf47e`
- **Deferred behind gate:** `apps/web/lib/api/schema.d.ts`, `apps/web/openapi/zero-mail-spec.json`, `apps/web/features/inbox/api/inbox-api.ts`, `.planning/REQUIREMENTS.md`, and all of Task 4's calendar files.

## Self-Check: PASSED

- Created files exist: `GmailInboxMessageResponseTest.java`, `inbox-page-client-cancel-badge.test.tsx`, `inbox-calendar-badge.spec.ts` — all present.
- Commits exist: `99afa8c0`, `c8a6b3d9`, `b91cf47e` — all in `git log`.
- Backend tests green; web Vitest 3/3 green; web typecheck clean except the pre-existing out-of-scope `frontmatter.ts` yaml error.
