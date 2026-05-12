---
phase: 05A-user-surface-web-ui-core
plan: 05
subsystem: ui
tags: [nextjs, react, shadcn, playwright, privacy, app-shell, onboarding]

requires:
  - phase: 05A-user-surface-web-ui-core
    provides: Plans 01-02 app shell, shared states, design tokens, i18n pipeline, and protected route groups
provides:
  - Authenticated /settings/privacy route inside the app shell with vi/en privacy copy
  - Settings-page privacy reachability without adding nested sidebar navigation
  - Rules list and preview convergence onto shared state primitives
  - Protected onboarding routes moved off the zm-auth clay-skin class path while keeping chrome suppression
  - Browser coverage for privacy reachability, shell convergence, 320px overflow, and no clay-skin classes
affects: [phase-05A, apps-web, privacy, rules, settings, onboarding, e2e]

tech-stack:
  added: []
  patterns:
    - Settings subpages stay reachable from the flat Settings page rather than nested sidebar navigation
    - Protected onboarding can reuse AuthTopBar through a protected surface variant that avoids auth-only skin classes
    - E2E convergence assertions are centralized in chrome-test-utils

key-files:
  created:
    - apps/web/app/(protected)/(app)/settings/privacy/page.tsx
    - apps/web/features/privacy/components/PrivacySections.tsx
  modified:
    - apps/web/features/privacy/messages.ts
    - apps/web/app/(protected)/(app)/settings/page.tsx
    - apps/web/__tests__/i18n/messages.contract.test.ts
    - apps/web/features/rules/components/RuleList.tsx
    - apps/web/features/rules/components/RulePreviewPanel.tsx
    - apps/web/features/rules/components/RuleTemplateGallery.tsx
    - apps/web/app/(protected)/onboarding/gmail-connect/page.tsx
    - apps/web/app/(protected)/onboarding/template-select/page.tsx
    - apps/web/app/(protected)/onboarding/complete/page.tsx
    - apps/web/app/(protected)/onboarding/gmail-connect/GmailConnectClient.tsx
    - apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx
    - apps/web/app/(protected)/onboarding/complete/CompleteClient.tsx
    - apps/web/features/auth/components/AuthTopBar.tsx
    - apps/web/features/onboarding/components/TemplateCard.tsx
    - apps/web/e2e/chrome-test-utils.ts
    - apps/web/e2e/privacy-page.spec.ts
    - apps/web/e2e/rules.spec.ts
    - apps/web/e2e/onboarding-routes.spec.ts
    - apps/web/e2e/byok.spec.ts

key-decisions:
  - "Privacy reachability uses the existing flat Settings sidebar entry plus a Settings-page 'Privacy & data handling' link; AppSidebar stays unchanged to preserve Plan 02's flat-nav decision."
  - "Generated apps/web/i18n/messages/{en,vi}.json were rebuilt for verification but intentionally not committed; Plan 06 owns canonical bundle regeneration."
  - "Protected onboarding uses AuthTopBar surface='protected' plus token classes so those routes keep minimal chrome without rendering zm-auth/zm-proto classes."
  - "No extra authenticated-shell CSS media guard was needed; 320px safety was achieved with component-level responsive classes and verified in Playwright."

patterns-established:
  - "Use chrome-test-utils assertions for app-shell presence, chrome suppression, clay-skin class absence, and horizontal overflow checks."
  - "When adding authenticated policy pages, keep public legal pages untouched and link to them through constant React Link hrefs."
  - "For protected onboarding convergence, adjust token classes in place and avoid flow redesign or sidebar shell insertion."

requirements-completed: [WEB-01, WEB-02, WEB-03]

duration: 41min
completed: 2026-05-12
---

# Phase 05A Plan 05: Privacy and Convergence Summary

**In-shell privacy page plus authenticated rules, settings, and onboarding convergence with 320px browser coverage**

## Performance

- **Duration:** 41 min
- **Started:** 2026-05-12T12:49:00Z
- **Completed:** 2026-05-12T13:29:00Z
- **Tasks:** 3
- **Files modified:** 21 app/test files + 1 summary

## Accomplishments

- Added `/settings/privacy` under `(protected)/(app)` with static vi/en privacy content covering no stored bodies/prompts/replies/embeddings, no auto-send, and BYOK.
- Added a `Privacy & data handling` Settings-page link to the in-product privacy route while leaving the public `(public)/privacy` page untouched.
- Converged rules loading/empty states onto shared `LoadingState` and `EmptyState` primitives.
- Moved protected onboarding route wrappers and client panels off the `zm-auth` clay-skin class path, keeping the focused chrome-suppressed layout.
- Added Playwright coverage for privacy page reachability, shell/chrome assertions, 320px overflow, and no `zm-auth`/`zm-proto` classes.

## Frontend Design Notes

- Privacy page: compact in-shell policy page, with "What we never store" visually first and accented; the two supporting sections sit beneath it without becoming a marketing hero.
- Rules workspace: stayed dense and operational inside the app shell; shared state primitives now give loading and empty states the same neutral token treatment as other 5A surfaces.
- Settings page: preserved the existing section-card stack and pause-control behavior; privacy reachability is a secondary action inside the privacy card, not a sidebar expansion.
- Onboarding Gmail connect: kept the two-column proof-panel layout on desktop and stacked 320px layout, but replaced the auth clay wrapper with base app tokens.
- Onboarding template select: preserved radio-card flow and CTA behavior; 44px button height and token headings improve mobile ergonomics without changing flow.
- Onboarding complete: kept the final checklist and completion CTA, with base tokens, shared loading skeleton, and no horizontal overflow at 320px.
- Light/dark review: changed source uses token utilities (`bg-card`, `text-foreground`, `text-muted-foreground`, semantic green/accent), with no ad-hoc hex/rgb colors in touched protected/rules/onboarding files.

## Task Commits

1. **Task 1: Build the /settings/privacy page** - `fc7d2f3` (feat)
2. **Task 2: Convergence pass** - `dd0cb1a` (feat)
3. **Task 3: Privacy and convergence Playwright coverage** - `bfb9af3` (test)

## Files Created/Modified

- `apps/web/app/(protected)/(app)/settings/privacy/page.tsx` - thin in-shell privacy route.
- `apps/web/features/privacy/components/PrivacySections.tsx` - three privacy cards and public `/privacy` link.
- `apps/web/features/privacy/messages.ts` - vi/en `privacy.*` source messages.
- `apps/web/app/(protected)/(app)/settings/page.tsx` - Settings-page privacy link.
- `apps/web/__tests__/i18n/messages.contract.test.ts` - privacy message parity contract.
- `RuleList.tsx`, `RulePreviewPanel.tsx`, `RuleTemplateGallery.tsx` - shared state primitive and type-tier convergence.
- `onboarding/*/page.tsx`, `*Client.tsx`, `AuthTopBar.tsx`, `TemplateCard.tsx` - protected onboarding token/chrome convergence.
- `privacy-page.spec.ts`, `rules.spec.ts`, `onboarding-routes.spec.ts`, `byok.spec.ts`, `chrome-test-utils.ts` - browser assertions for privacy, shell, chrome suppression, 320px, and no clay-skin classes.

## Decisions Made

- Sidebar reachability: `AppSidebar.tsx` was left unchanged because Plan 02 intentionally kept a flat nav; `/settings/privacy` is reached from the existing Settings sidebar item plus the Settings-page link.
- No extra i18n scan registration was needed; Plan 01 had already registered the privacy page and component paths.
- No additional CSS media guard was needed for authenticated screens; 320px Playwright checks pass with local responsive classes.
- No new runtime dependency was added.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Protected onboarding still rendered auth-skin classes through child panels**
- **Found during:** Task 2 source scan.
- **Issue:** Removing the outer `zm-auth` wrapper was not enough; protected onboarding client panels still used `zm-auth-panel`, `zm-auth-title`, and `zm-auth-sub`.
- **Fix:** Replaced those with token-based Tailwind classes and shared `LoadingState`, and added a protected `AuthTopBar` surface variant so onboarding keeps focused top chrome without rendering `zm-auth*` classes.
- **Files modified:** `AuthTopBar.tsx`, onboarding route pages, onboarding client components, `TemplateCard.tsx`.
- **Verification:** source scan for `zm-proto|zm-auth|dangerouslySetInnerHTML|z-1` over protected/rules/onboarding paths returned zero matches; lint/typecheck/i18n/test/e2e passed.
- **Committed in:** `dd0cb1a`.

**2. [Rule 3 - Blocking] Playwright selectors over-specified implementation semantics**
- **Found during:** Task 3 targeted E2E run.
- **Issue:** Privacy card titles were visible section titles but not heading-role elements, and the rules page heading selector also matched the empty-state heading containing "rules".
- **Fix:** Adjusted privacy assertions to visible exact text and made rules heading assertions exact.
- **Files modified:** `privacy-page.spec.ts`, `rules.spec.ts`.
- **Verification:** targeted and full Playwright suites passed.
- **Committed in:** `bfb9af3`.

**Total deviations:** 2 auto-fixed implementation/test-contract issues.
**Impact on plan:** Both fixes tightened the planned convergence contract; no backend scope, dependency, or flow redesign was added.

## Issues Encountered

- Full Playwright still prints the pre-existing duplicate React key warning for `Actions-archive`; this also appeared during Plan 04 and is not introduced by Plan 05.
- `apps/web/i18n/messages/{en,vi}.json` were regenerated during verification and intentionally left uncommitted for Plan 06.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test -- __tests__/i18n` - passed during Task 1.
- `pnpm --filter web test` - passed, 38 files / 229 tests.
- `pnpm --filter web test:e2e -- privacy-page rules onboarding-routes byok --workers=1 --reporter=dot` - passed, 19 tests.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 67 passed / 1 skipped.
- Source checks: no `zm-proto`, `zm-auth`, `dangerouslySetInnerHTML`, or invalid `z-1` in protected/rules/onboarding sources.
- `apps/web/lib/api/schema.d.ts` unchanged; no backend endpoints added; public `(public)/privacy/page.tsx` untouched.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 06. Plan 05 leaves the generated locale bundles dirty by design so Plan 06 can regenerate and commit the canonical bundles with the final 05A closure artifacts.

## Self-Check: PASSED

- `/settings/privacy` exists under `(protected)/(app)` and renders in the app shell.
- The privacy page states no long-term storage, no auto-send, and BYOK, and links to public `/privacy`.
- Rules use shared state primitives for loading/empty states.
- Onboarding remains chrome-suppressed and 320px-safe.
- Settings pause control still uses `useTriagePauseState` / `useToggleTriagePause`; it was not rewired in this plan.
- Targeted and full browser verification passed.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
