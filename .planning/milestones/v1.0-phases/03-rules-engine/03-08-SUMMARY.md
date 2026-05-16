---
phase: 03-rules-engine
plan: "08"
subsystem: rules-ui
tags: [rules-engine, frontend, nextjs, react, tanstack-query, playwright, i18n]

requires:
  - phase: 03-rules-engine
    provides: Typed `/api/rules` OpenAPI schema, DTOs, stable rules error codes, and preview/template endpoints
  - phase: 03-rules-engine
    provides: UI design contract for the authenticated rules workspace
provides:
  - Protected `/rules` operational workspace for natural-language rule authoring
  - Generated-schema rules API functions, query keys, and TanStack Query hooks
  - Inline clarification flow that preserves source text and blocks save until compiled
  - Safe preview panel with no-write notice, evidence chips, deferred semantic chips, conflicts, and preview-before-enable gating
  - Desktop and mobile Playwright browser coverage for the rules workflow
affects: [03-rules-engine, 04-triage-convergence, apps-web-rules]

tech-stack:
  added: [shadcn-textarea]
  patterns:
    - Feature-owned API functions and hooks under `apps/web/features/rules`
    - Tagged compile result mapping for compiled, clarificationRequired, and invalid states
    - Rules reorder optimistic update with rollback and settled invalidation
    - Browser-level route mocking for rules API contracts in Playwright

key-files:
  created:
    - apps/web/app/(protected)/rules/page.tsx
    - apps/web/components/ui/textarea.tsx
    - apps/web/features/rules/api/rules-api.ts
    - apps/web/features/rules/query-keys.ts
    - apps/web/features/rules/hooks/use-rules.ts
    - apps/web/features/rules/messages.ts
    - apps/web/features/rules/components/RulesWorkspace.tsx
    - apps/web/features/rules/components/RuleComposer.tsx
    - apps/web/features/rules/components/RuleList.tsx
    - apps/web/features/rules/components/RulePreviewPanel.tsx
    - apps/web/features/rules/components/RuleTemplateGallery.tsx
  modified:
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json
    - apps/web/scripts/check-i18n.ts
    - apps/web/__tests__/rules-feature-contract.test.ts
    - apps/web/features/rules/components/RulesWorkspace.test.tsx
    - apps/web/e2e/rules.spec.ts

key-decisions:
  - "Rules frontend consumes generated OpenAPI schema aliases through a feature-owned API module rather than hand-rolled response types."
  - "Clarification-required compile output remains inline composer state, not a toast-style API error."
  - "Enablement stays blocked until a saved rule version has a successful safe preview."
  - "Rules Playwright tests mock backend routes in-memory and run serially because the global Playwright config is fully parallel."
  - "STATE.md and ROADMAP.md were left untouched per orchestrator single-writer constraint."

patterns-established:
  - "Rules feature query keys expose all/list/detail/templates and mutation hooks invalidate the smallest relevant caches."
  - "Rule preview UI renders sanitized subject/sender/evidence/action/conflict chips only; raw HTML/body/header content is never displayed."
  - "Rules E2E specs document the dev-server command and mocked backend route contract at the top of the spec."

requirements-completed: [RULE-01, RULE-05, RULE-06, RULE-07]

duration: continued execution after handoff
completed: 2026-05-10
---

# Phase 03 Plan 08: Rules Frontend Summary

**Authenticated rules workspace with typed rules API hooks, inline clarification, safe preview-before-enable gating, and browser-verified desktop/mobile flows.**

## Performance

- **Duration:** Continued execution after handoff; original predecessor start time was not available in this resumed context.
- **Started:** Not recorded by predecessor executor.
- **Completed:** 2026-05-09T22:38:07Z
- **Tasks:** 3 completed
- **Files modified:** 17

## Accomplishments

- Added `features/rules` API, query keys, mutation hooks, and i18n copy for the generated rules API surface.
- Built the protected `/rules` workspace with composer, rule list, template gallery, and safe preview panel following the Phase 03 UI-SPEC.
- Preserved original source text through one-question clarification and kept save disabled until a successful compiled result.
- Enforced preview-before-enable in the UI using `entityVersion` and `lastPreviewedEntityVersion`.
- Replaced the skipped Wave 0 Playwright scaffold with full Chromium coverage for desktop flow, templates, mobile usability, and error placement.

## Clarification and Error Placement

- Clarification questions render inline directly under the source textarea with a single answer field and `Answer clarification` CTA.
- The original source textarea value stays editable and is sent again with the clarification answer.
- Compile insufficient-credit errors render in the composer.
- Gmail preview unavailable errors render in the preview panel.
- Clarification-required compile responses are not treated as ProblemDetail errors or toast failures.

## Verification

- `pnpm --filter web lint` - PASS
- `pnpm --filter web typecheck` - PASS
- `pnpm --filter web i18n:check` - PASS (`445` leaf keys, VI/EN parity, backend ErrorCodes coverage)
- `pnpm --filter web test -- --run features/rules __tests__/rules-feature-contract.test.ts` - PASS, 2 files passed, 9 tests passed
- `pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` - PASS, 4 Chromium tests passed

Browser verification was completed through Playwright Chromium. The mobile test asserts no horizontal overflow at `375x812`; no persistent screenshots were captured because the final browser run passed.

## Task Commits

Each task was committed atomically:

1. **Task 1: Feature API, query keys, hooks, and i18n** - `3f181d9` (feat)
2. **Task 2: Rules page components** - `79d0f82` (feat)
3. **Task 3: Browser verification** - `2132e63` (test)

**Plan metadata:** recorded in final docs commit.

## Files Created/Modified

- `apps/web/app/(protected)/rules/page.tsx` - Protected Rules page shell with `RulesWorkspace`.
- `apps/web/components/ui/textarea.tsx` - Official shadcn textarea primitive used by the composer.
- `apps/web/features/rules/api/rules-api.ts` - Generated-schema rules API aliases and endpoint functions.
- `apps/web/features/rules/query-keys.ts` - Rules TanStack Query key factory.
- `apps/web/features/rules/hooks/use-rules.ts` - Rules queries and mutations with reorder optimistic rollback.
- `apps/web/features/rules/messages.ts` - Source i18n keys for rules copy.
- `apps/web/features/rules/components/RulesWorkspace.tsx` - Client orchestrator for selection, composer, list, templates, preview, and version gating.
- `apps/web/features/rules/components/RuleComposer.tsx` - Natural-language input, clarification, compile review, and disabled-save flow.
- `apps/web/features/rules/components/RuleList.tsx` - Dense ordered rules list with accessible icon controls.
- `apps/web/features/rules/components/RulePreviewPanel.tsx` - Safe preview summary, no-write notice, evidence, deferred semantic, and conflict UI.
- `apps/web/features/rules/components/RuleTemplateGallery.tsx` - Starter template materialization UI.
- `apps/web/i18n/messages/en.json` - English rules and rules-error copy.
- `apps/web/i18n/messages/vi.json` - Vietnamese rules and rules-error copy.
- `apps/web/scripts/check-i18n.ts` - Rules files added to scan coverage.
- `apps/web/__tests__/rules-feature-contract.test.ts` - Rules source/i18n/API contract coverage.
- `apps/web/features/rules/components/RulesWorkspace.test.tsx` - Rules workspace behavior and escaping coverage.
- `apps/web/e2e/rules.spec.ts` - Full browser workflow coverage with mocked `/api/rules` routes.

## Decisions Made

- Used generated OpenAPI schema aliases in `rules-api.ts` and kept endpoint calls inside the rules feature API module.
- Modeled compile output as typed UI states so clarification remains a first-class authoring flow.
- Kept rules disabled after save and required safe preview of the current entity version before enablement.
- Used official shadcn `textarea` instead of local ad hoc textarea markup.
- Serialized the rules Playwright file to avoid cross-test instability under the repo-wide fully parallel Playwright configuration.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added official shadcn textarea primitive**
- **Found during:** Task 2 (Rules page components)
- **Issue:** The local UI primitive set did not include `textarea`, while the composer needed a shared textarea control.
- **Fix:** Added the official shadcn `textarea` primitive and composed the rule source input around it.
- **Files modified:** `apps/web/components/ui/textarea.tsx`, `apps/web/features/rules/components/RuleComposer.tsx`
- **Verification:** `pnpm --filter web lint`, `pnpm --filter web typecheck`, and rules Vitest coverage passed.
- **Committed in:** `79d0f82`

**2. [Rule 1 - Bug] Stabilized rules Playwright browser flow under the shared dev server**
- **Found during:** Task 3 (Browser verification)
- **Issue:** The exact Playwright command could time out on the desktop flow while the compile button was visible but still considered unstable.
- **Fix:** Added file-level serial execution and waited for `networkidle` after opening `/rules` before interacting with hydrated controls.
- **Files modified:** `apps/web/e2e/rules.spec.ts`
- **Verification:** `pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` passed with 4 Chromium tests.
- **Committed in:** `2132e63`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes were required for the planned composer and repeatable browser verification. No unplanned product surface was added.

## Issues Encountered

- The rules browser spec initially passed, then exposed intermittent instability on a repeated exact-command run. The stabilization was committed with Task 3.
- `.planning/STATE.md` was already modified in the worktree and stayed unstaged/uncommitted, per the explicit orchestrator constraint that shared tracking writes are owned elsewhere.

## Known Stubs

None. Stub scan found no plan-blocking TODO/FIXME/placeholder UI stubs. Matches were legitimate textarea/BYOK placeholders, pre-existing legal placeholder copy, test defaults, and null checks.

## Threat Flags

None. This plan introduced the planned frontend `/api/rules` client calls and test route mocks only; no new backend endpoint, auth path, schema boundary, file access path, Gmail write surface, or raw HTML rendering was added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for downstream triage-convergence planning. The web app now has a typed rules authoring and preview surface that consumes the Phase 03 API contracts and verifies the core user workflow in Chromium.

## Self-Check: PASSED

- Verified summary file exists at `.planning/phases/03-rules-engine/03-08-SUMMARY.md`.
- Verified key created/modified files exist, including the protected rules page, rules feature API/hooks/components, textarea primitive, and Playwright spec.
- Verified task commits `3f181d9`, `79d0f82`, and `2132e63` exist in git history.
- Verified `.planning/STATE.md` remains unstaged and uncommitted per orchestrator constraint.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
