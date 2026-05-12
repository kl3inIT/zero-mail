---
phase: 05A-user-surface-web-ui-core
plan: 01
subsystem: ui
tags: [nextjs, react, shadcn, tanstack-query, playwright, vitest, i18n]

requires:
  - phase: 01.6-brand-identity-design-tokens-and-landing-page
    provides: Phase 1.6 teal design tokens and frontend visual baseline
  - phase: 04-triage-core
    provides: Triage pause and billing API surfaces consumed by Phase 5A UI
provides:
  - Shared shadcn UI primitives for Phase 5A
  - Shared LoadingState, EmptyState, and ErrorState components
  - triageKeys cache-key factory
  - Billing feature API, query keys, hooks, and source messages
  - Phase 5A Playwright and Vitest validation scaffolding
  - Phase 5A i18n scan-path registry
affects: [phase-05A, apps-web, billing, triage, shell, privacy]

tech-stack:
  added:
    - sonner
    - shadcn runtime dependency updates from primitive install
  patterns:
    - Typed openapi-fetch wrappers for billing APIs
    - Explicit unavailable sentinel for backend-surface gaps
    - Per-spec 320px Playwright viewport coverage
    - Source-message ownership with generated bundle commit deferred

key-files:
  created:
    - apps/web/components/states/LoadingState.tsx
    - apps/web/components/states/EmptyState.tsx
    - apps/web/components/states/ErrorState.tsx
    - apps/web/features/triage/query-keys.ts
    - apps/web/features/billing/api/billing-api.ts
    - apps/web/features/billing/query-keys.ts
    - apps/web/features/billing/hooks/useBillingBalance.ts
    - apps/web/features/billing/hooks/useCreateTopupIntent.ts
    - apps/web/features/billing/hooks/useTopupCreditWatch.ts
    - apps/web/features/billing/hooks/useLedgerHistory.ts
    - apps/web/features/billing/messages.ts
    - apps/web/features/privacy/messages.ts
    - apps/web/features/shell/messages.ts
  modified:
    - apps/web/package.json
    - pnpm-lock.yaml
    - apps/web/playwright.config.ts
    - apps/web/scripts/check-i18n.ts
    - apps/web/__tests__/architecture/feature-folders.test.ts

key-decisions:
  - "SIDEBAR_COOKIE_NAME in the generated sidebar primitive is `sidebar_state`; shadcn 4.7 generated it as a private const, so downstream code should use the literal or add a wrapper rather than editing the copied primitive."
  - "Billing ledger history stays an explicit `{ unavailable: true }` sentinel until a backend endpoint exists; it is not represented as an empty list."
  - "Top-up credited state is inferred from `/api/billing/balance` rising because there is no intent-status endpoint or intentId in the committed OpenAPI schema."
  - "320px Playwright coverage stays per spec with `page.setViewportSize(...)`; no global mobile project was added because it would rerun desktop-first flows at 320px."
  - "Generated `apps/web/i18n/messages/{vi,en}.json` were rebuilt for verification but intentionally not committed in Plan 01; Plan 06 owns the canonical generated bundle commit."

patterns-established:
  - "Feature query keys: keep `triageKeys` and `billingKeys` beside feature folders, not inside api/."
  - "Backend gaps: return an explicit unavailable sentinel and document the missing endpoint in code instead of fabricating typed empty data."
  - "Poll-sensitive hooks: override the global QueryProvider 5 minute staleTime when the product decision requires a shorter interval."

requirements-completed: [WEB-01]

duration: 48min
completed: 2026-05-12
---

# Phase 05A Plan 01: Web UI Foundation Summary

**Phase 5A UI foundations with shadcn primitives, shared state components, billing/triage data scaffolding, and validation gates**

## Performance

- **Duration:** 48 min
- **Started:** 2026-05-12T08:45:28Z
- **Completed:** 2026-05-12T09:33:42Z
- **Tasks:** 3
- **Files modified:** 36 committed files

## Accomplishments

- Installed the missing shadcn primitives: sidebar, sheet, table, alert-dialog, switch, sonner, and dropdown-menu.
- Added `LoadingState`, `EmptyState`, and `ErrorState` using token-bound layout and caller-provided copy.
- Added `triageKeys` plus the billing API/query/hook skeleton against only the existing `/api/billing/balance` and `/api/billing/topup/intent` endpoints.
- Added explicit unavailable sentinel handling for ledger history and documented the missing ledger, intent-status, intentId, and top-up bank-field backend gaps.
- Added all eight Phase 5A Playwright spec stubs and billing hook Vitest coverage for polling/stale-time behavior.
- Extended `EN_SCAN_FILES` up front with the Phase 5A route-group paths so downstream plans do not need to edit the scanner.

## Frontend Design Note

Shared state trio visual review: the components stay quiet and utilitarian, using neutral card surfaces, dashed empty-state framing, skeleton rows/cards, and outline retry affordances so dense SaaS screens remain scannable without introducing a new visual theme.

## Task Commits

1. **Task 1: Install missing shadcn primitives + shared states** - `756e0cd` (feat)
2. **Task 2: Triage keys + billing skeleton** - `f10aea5` (feat)
3. **Task 3: Validation scaffolding** - `ee5f1e7` (test)
4. **Deviation fix: Update stale feature architecture contract** - `5d8b927` (test)

## Files Created/Modified

- `apps/web/components/states/LoadingState.tsx` - shared skeleton row/card loading primitive.
- `apps/web/components/states/EmptyState.tsx` - shared empty-state primitive with optional CTA.
- `apps/web/components/states/ErrorState.tsx` - shared retryable error-state primitive.
- `apps/web/features/triage/query-keys.ts` - canonical triage query key factory including `pauseState`, `auditLog`, `shadowMode`, and `senderSafetyNet`.
- `apps/web/features/billing/api/billing-api.ts` - typed billing API wrappers and unavailable ledger sentinel.
- `apps/web/features/billing/hooks/useBillingBalance.ts` - 45 second polling balance hook with 30 second stale time.
- `apps/web/features/billing/hooks/useTopupCreditWatch.ts` - balance-rise polling watch for top-up credit detection.
- `apps/web/features/billing/hooks/useLedgerHistory.ts` - `useInfiniteQuery`-shaped unavailable ledger hook.
- `apps/web/features/billing/messages.ts`, `apps/web/features/privacy/messages.ts`, `apps/web/features/shell/messages.ts` - vi/en source message maps.
- `apps/web/e2e/*05A*.spec.ts` equivalents - eight skipped Playwright stubs owned by later plans.
- `apps/web/scripts/check-i18n.ts` - Phase 5A scan paths using `(protected)/(app)/...` route-group locations.
- `apps/web/playwright.config.ts` - documented per-spec 320px viewport approach.
- `apps/web/__tests__/architecture/feature-folders.test.ts` - architecture contract updated for billing feature root and new `triageKeys`.

## Decisions Made

- `SIDEBAR_COOKIE_NAME` value is `sidebar_state`. The generated shadcn primitive did not export the const; the primitive was left unedited per project rules.
- `apps/web/package.json` and root `pnpm-lock.yaml` changed because shadcn primitive installation added/runtime-aligned dependencies. There is no `apps/web/pnpm-lock.yaml` in this pnpm workspace.
- Billing source messages intentionally do not include bank-account-number, bank-name, or account-holder-name labels because `TopupIntentResponse` does not expose those fields.
- `apps/web/lib/api/schema.d.ts` remained unchanged; no backend endpoint was added or client regenerated.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated stale feature architecture contract**
- **Found during:** Plan-level `pnpm --filter web test`
- **Issue:** `apps/web/__tests__/architecture/feature-folders.test.ts` still asserted that `features/triage/query-keys.ts` must not exist, directly conflicting with Plan 01's required `triageKeys` artifact.
- **Fix:** Updated the contract to include the new `billing` feature root and assert that `triage/query-keys.ts` exists with `pauseState` and `auditLog`.
- **Files modified:** `apps/web/__tests__/architecture/feature-folders.test.ts`
- **Verification:** `pnpm --filter web test` passed: 35 files, 214 tests.
- **Committed in:** `5d8b927`

---

**Total deviations:** 1 auto-fixed blocking test-contract drift.
**Impact on plan:** No scope expansion. The fix aligned an old architecture guard with the new planned Phase 5A structure.

## Issues Encountered

- A repeated parallel Playwright run hit two existing `byok.spec.ts` timing failures. The same BYOK specs passed in the first full run and passed when isolated; the full suite passed with `--workers=1` afterward. Recorded as a non-Phase-5A flaky parallelism issue, not a product-code failure.
- `apps/web/i18n/messages/{vi,en}.json` are modified locally after `i18n:build` so `i18n:check` can validate the new source messages. They were not committed by this plan by design.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test -- features/billing/hooks` - passed, 2 files / 5 tests.
- `pnpm --filter web test` - passed, 35 files / 214 tests.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 30 passed / 9 skipped.
- `apps/web/lib/api/schema.d.ts` - unchanged.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02. Downstream shell work can consume the shadcn sidebar primitive, shared state trio, `triageKeys.pauseState()`, billing balance hook, shell/privacy/billing source messages, and pre-registered i18n scan paths.

## Self-Check: PASSED

- Required Plan 01 artifacts exist.
- Task commits are present.
- Generated i18n bundles are not committed.
- Verification gates listed above passed.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
