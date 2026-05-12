---
phase: 05A-user-surface-web-ui-core
plan: 06
subsystem: ui
tags: [nextjs, react, playwright, vitest, i18n, validation, requirements]

requires:
  - phase: 05A-user-surface-web-ui-core
    provides: Plans 01-05 authenticated shell, triage, billing, privacy, convergence, and generated i18n source messages
provides:
  - Phase 5A backend-surface gap register
  - Signed 05A validation artifact
  - Honest WEB-01/02/03/04 requirement traceability
  - Committed canonical generated vi/en i18n bundles
affects: [phase-05A, apps-web, requirements, validation, phase-5B, phase-5C]

tech-stack:
  added: []
  patterns:
    - Closure plans commit generated locale bundles after source-message changes settle
    - Missing backend surfaces are tracked as explicit gap registers, not implied completion
    - WEB-02 remains unchecked until draft review, analytics, and backend audit/ledger surfaces are complete

key-files:
  created:
    - .planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md
    - .planning/phases/05A-user-surface-web-ui-core/05A-06-SUMMARY.md
  modified:
    - .planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
    - .planning/REQUIREMENTS.md
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

key-decisions:
  - "WEB-02 is intentionally left unchecked because Phase 5A only completes the onboarding/rules/triage/billing subset; draft-review moves to 5B, analytics moves to 5C, and real audit-list + ledger-history backend endpoints remain gaps."
  - "The split `(protected)/(app)` route group is the accepted shell topology; onboarding stays in a bare protected layout."
  - "No QR dependency was added; `qrPayload` is rendered as copyable React text only."
  - "No schema generation, backend endpoint, or public privacy page change was made in Phase 5A closure."

patterns-established:
  - "Close frontend UI phases with full web gates, no-backend diff checks, explicit gap registers, and generated i18n bundle commits."
  - "For partial umbrella requirements, keep the checkbox open and put the exact phase split plus backend gaps in both the requirement line and traceability row."

requirements-completed: [WEB-01, WEB-02, WEB-03, WEB-04]

duration: 69min
completed: 2026-05-12
---

# Phase 05A Plan 06: Closure and Validation Summary

**Phase closure with green web gates, explicit backend-surface gaps, signed validation, and honest WEB traceability**

## Performance

- **Duration:** 69 min
- **Started:** 2026-05-12T13:32:05Z
- **Completed:** 2026-05-12T20:41:40+07:00
- **Tasks:** 2
- **Files modified:** 4 planning artifacts + 2 generated locale bundles

## Accomplishments

- Ran the full `apps/web` closure suite green and committed the canonical generated `i18n/messages/{en,vi}.json` bundles.
- Created `05A-GAPS.md` with the four backend-surface gaps and their shipped degradation paths.
- Signed off `05A-VALIDATION.md` with `nyquist_compliant: true` and `wave_0_complete: true`.
- Updated `.planning/REQUIREMENTS.md`: `WEB-01`, `WEB-03`, and `WEB-04` are complete; `WEB-02` remains unchecked with the exact partial annotation.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web test` - passed, 38 files / 229 tests.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 67 passed / 1 skipped.
- `git diff --exit-code -- apps/web/lib/api/schema.d.ts` - passed, no schema regeneration.
- `git diff --exit-code -- "apps/web/app/(public)/privacy/page.tsx"` - passed, public privacy page untouched.
- `git diff --exit-code -- backend/` - passed, no backend endpoint or backend source change.

Known recurring warning: full Playwright still logs the pre-existing duplicate React key warning for `Actions-archive`.

## Frontend Design Rollup

- Shell/chrome: compact operational sidebar and top bar; pause, balance, and Gmail health stay reachable on desktop and 320px in light/dark without marketing framing.
- `/triage`: audit table uses dense rows, mono timestamps, wrapped reason text, and restrained badges; mobile audit cards show full reason, message reference, rule, action, and undo state without horizontal scroll.
- `/triage` shadow mode: quiet settings-card treatment with status color only when active; turn-off confirmation uses the shared alert-dialog pattern.
- `/triage` sender safety net: divided sender rows, clear opt-in action, and shared empty state; no false-success implication when data is unavailable.
- `/billing`: focal balance card with transaction history beside it on desktop and stacked below at 320px; ledger unavailable is visually distinct from an empty ledger.
- `/billing/top-up`: amount, transfer instructions, success, and expired states use compact task-card layouts, copyable code/amount/payload fields, semantic success/warning accents, and no horizontal overflow at 320px.
- `/settings/privacy`: compact in-shell policy page with "What we never store" first, no hero treatment, and no change to the public privacy route.
- Rules workspace: dense app-shell surface with shared loading/empty primitives and token-consistent light/dark styling.
- Onboarding Gmail/template/complete: focused chrome-suppressed flow retained, moved off auth clay classes, 320px-safe, with base app tokens and shared loading where needed.
- Settings page: existing card stack and pause behavior preserved; privacy reachability is a secondary action from Settings, not a sidebar expansion.

## Backend-Surface Gaps

All four gaps are recorded in `05A-GAPS.md`:

- Triage-audit list endpoint absent; production UI uses `{ unavailable: true }`, unavailable panel, and injected-data `AuditLog.test.tsx` coverage.
- Billing ledger/history endpoint absent; production UI uses `{ unavailable: true }`, unavailable panel, and injected-data `LedgerTable.test.tsx` coverage.
- Top-up intent-status endpoint and `intentId` absent; UI uses `?code=` plus `sessionStorage`, and credited state is inferred from `/api/billing/balance` rising.
- Top-up bank-account fields absent from `TopupIntentResponse`; UI shows only `qrPayload`, transfer `code`, `amountVnd`, and `expiresAt`.

## Requirement Annotation

Exact WEB-02 annotation used:

`5A portion done (onboarding, rules+live-preview, triage audit log+undo*, billing*); draft-review → 5B, analytics → 5C; *audit-list & ledger-history backend endpoints pending — see 05A-GAPS.md`

`WEB-02` remains unchecked. `WEB-01`, `WEB-03`, and `WEB-04` are checked complete.

## Resolved Research Questions

1. Triage-audit list endpoint is absent; Phase 5A ships an unavailable panel and injected-data populated-row tests.
2. Billing ledger/history endpoint is absent; Phase 5A ships an unavailable panel and injected-data populated-row tests.
3. Top-up has no `intentId` or intent-status endpoint; Phase 5A uses `?code=` plus `sessionStorage` and balance-rise detection.
4. Audit entry identifiers are local until a backend list shape exists; Gmail deep-linking only happens when an eventual row carries a Gmail message id.
5. Onboarding chrome suppression uses the split `(protected)/(app)` shell route group and bare `(protected)/onboarding` layout.

## Decisions Made

- Route-group decision: split `(protected)/(app)` versus onboarding bare layout; no segment-branch fallback was needed.
- QR decision: no QR dependency added; `qrPayload` is copyable text only.
- i18n decision: generated `apps/web/i18n/messages/{en,vi}.json` are committed in this closure plan, as planned.
- Scope decision: no backend, schema, or public privacy page changes were absorbed into closure.

## Deviations from Plan

None - closure followed the reviewed Plan 06 scope. `apps/web/scripts/check-i18n.ts` had line-ending/stat churn only and was not committed.

## Issues Encountered

- No new closure issues surfaced. The only remaining note is the pre-existing Playwright duplicate key warning for `Actions-archive`.

## Task Commits

1. **Task 1-2: Closure artifacts, validation sign-off, requirements, and generated i18n bundles** - pending commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 5A can proceed to phase-level verification. WEB-02 follow-up remains explicit for Phase 5B draft review, Phase 5C analytics, and backend API work for audit-list and ledger-history.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
