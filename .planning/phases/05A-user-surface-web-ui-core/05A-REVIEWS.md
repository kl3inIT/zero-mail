---
phase: 5A
reviewers: [codex, opencode]
reviewed_at: 2026-05-12T05:52:57Z
plans_reviewed: [05A-01-PLAN.md, 05A-02-PLAN.md, 05A-03-PLAN.md, 05A-04-PLAN.md, 05A-05-PLAN.md, 05A-06-PLAN.md]
---

# Cross-AI Plan Review — Phase 5A: User Surface — Web UI Core

> Independent peer review by external AI CLIs. `claude` skipped (this session is Claude Code). OpenCode ran on `nemotron-3-super-free`; Codex on its default model.

## Codex Review

**Overall Summary**  
The plans are thorough and mostly well-sequenced for a frontend-only phase, with strong attention to design contracts, i18n, typed OpenAPI usage, Playwright coverage, and explicit handling of the three confirmed backend gaps. The biggest risks are not design quality; they are execution feasibility: a few plans test routes before those routes exist, rely on server-layout route detection that Next App Router does not make straightforward, and sometimes mock behavior that the production gap-stubs cannot actually exercise.

## 05A-01 — Foundations

**Summary**  
Strong Wave 0 plan. It sets up the primitives, shared states, query keys, billing skeleton, i18n scan scope, and test placeholders needed downstream. Main risks are generated i18n bundle handling and dependency/file ownership around shadcn installs.

**Strengths**
- Good foundation-first ordering.
- Explicitly preserves frontend-only scope and avoids fake backend endpoints.
- Correctly flags billing ledger / intent-status gaps early.
- Adds 320px viewport and e2e placeholders before feature work.

**Concerns**
- **MEDIUM:** `i18n:build` output is intentionally not committed until Plan 06. That can leave intermediate commits dependent on generated-but-uncommitted JSON.
- **MEDIUM:** shadcn install may modify `package.json` / lockfile depending on generated dependencies, but those files are not listed.
- **MEDIUM:** `useLedgerHistory` returning an empty page can blur “no transactions” vs “backend history unavailable.”
- **LOW:** Adding all future paths to `EN_SCAN_FILES` up front is brittle if later filenames change.

**Suggestions**
- Include `apps/web/package.json` and lockfile as conditional owned files for shadcn changes.
- Make ledger/audit gap stubs return an explicit `unavailable` state, not just empty data.
- Either commit generated i18n JSON per plan or document that every plan commit is not independently green until Plan 06.

**Risk Assessment:** **MEDIUM**  
Good scaffold, but some generated-file and dependency ownership issues could cause noisy execution.

## 05A-02 — Shell + Chrome

**Summary**  
This is the highest-risk plan. The shell/chrome design is correct, but the implementation plan assumes the parent server layout can reliably branch on the active `onboarding` segment, and its e2e specs navigate to `/triage` and `/billing` before Plans 03/04 create those routes.

**Strengths**
- Strong D-13 pause-state single-source design.
- Good hydration/prefetch plan for chrome data.
- Correct focus on 320px chrome behavior and persistent shell.
- Good threat model around CSRF, XSS, and reconnect URLs.

**Concerns**
- **HIGH:** Plan 02 e2e hits `/triage` and `/billing`, but those pages are created in Plans 03/04. This can fail wave 2.
- **HIGH:** Branching in `app/(protected)/layout.tsx` based on the active `onboarding` segment is not straightforward in a Server Component layout. A route-group split is safer.
- **HIGH:** Plan 02 requires `/settings` pause toggle to share `triageKeys.pauseState()`, but `settings/page.tsx` is not modified until Plan 05.
- **MEDIUM:** If `useTriagePauseState` reads `/me`, invalidating only `triageKeys.pauseState()` may leave `accountQueryKeys.me()` stale for non-pause user data consumers.
- **MEDIUM:** Reusing `ReconnectPrompt` directly in a compact chrome header may produce cramped UI at 320px.

**Suggestions**
- Split protected routes into route groups such as `(protected)/(app)/...` and `(protected)/onboarding/...`, putting `AppShell` only in `(app)/layout.tsx`.
- Move `/triage` and `/billing` route smoke coverage to Plans 03/04, or create minimal placeholder pages in Plan 02.
- Update `settings/page.tsx` in Plan 02 or defer cross-settings pause assertions to Plan 05.
- Allow `useToggleTriagePause` to also invalidate `accountQueryKeys.me()` while keeping `triageKeys.pauseState()` as the only pause read source.

**Risk Assessment:** **HIGH**  
The shell is central, and the current route/layout and e2e ordering assumptions are likely to break execution.

## 05A-03 — Triage UI

**Summary**  
The triage UI plan is functionally rich and aligned with the trust-focused design contract. The main issue is an internal mismatch: the audit list is a production gap-stub, but the e2e plan expects mocked list data to flow through it.

**Strengths**
- Excellent UX requirements for reason visibility, undo window clarity, and 30-day boundary.
- Good deep-linkable tab design.
- Correctly refuses to create a backend audit-list endpoint.
- Strong security posture for rendered backend strings and `?tab=` allow-listing.

**Concerns**
- **HIGH:** If `getAuditLog` does not call a real endpoint and just returns an empty stub, Playwright network mocks cannot exercise populated audit rows.
- **MEDIUM:** Production cannot truly provide audit log + undo without a list endpoint; Plan 06 must avoid overclaiming this part.
- **MEDIUM:** Sender email in a path param must be encoded through typed path params, never string interpolation.
- **MEDIUM:** Toast text from hooks can create i18n friction; hooks should receive translated messages or keep to cache behavior only.

**Suggestions**
- Make `AuditLog` accept optional injected data for tests, or use component tests for populated rows and keep e2e focused on the real degraded state.
- Record “audit list unavailable in production” distinctly from “empty audit log.”
- Keep toast invocation in components where `useTranslations` is naturally available.

**Risk Assessment:** **MEDIUM-HIGH**  
The UI design is strong, but the mocked e2e strategy needs to match the actual gap-stub architecture.

## 05A-04 — Billing UI

**Summary**  
The billing plan handles the known backend limitations pragmatically, especially `?code=` + sessionStorage and balance-rise polling. The biggest missing piece is bank-transfer field availability: the known response shape may not include the account number or other copyable fields the UI contract expects.

**Strengths**
- Good dedicated `/billing/top-up` route instead of modal.
- Clear degraded handling for no ledger-history endpoint.
- Good expiry and refresh-resume thinking.
- Good XSS treatment for `qrPayload`.

**Concerns**
- **HIGH:** `TopupIntentResponse` is listed as `code`, `amountVnd`, `expiresAt`, `qrPayload`; the plan still requires account number / bank fields. Those may not exist.
- **HIGH:** Like audit, mocked populated ledger e2e may be impossible if `useLedgerHistory` always returns an empty stub.
- **MEDIUM:** Balance-rise-as-credit-signal can produce false positives if another credit event changes balance.
- **MEDIUM:** `sessionStorage` supports refresh in the same tab, but not a true later return in another tab/window.
- **MEDIUM:** Optional QR dependency would require `pnpm-lock.yaml` ownership and current-version verification.

**Suggestions**
- Confirm whether bank account fields are encoded, configured, or absent. If absent, degrade explicitly to `qrPayload`, code, amount, and expiry only.
- Prefer component tests for populated `LedgerTable`; keep e2e on real empty/unavailable production behavior.
- Avoid adding a QR dependency unless required; copyable transfer fields plus raw payload may be enough for 5A.

**Risk Assessment:** **MEDIUM-HIGH**  
The flow is workable, but expected display fields may exceed the backend contract.

## 05A-05 — Privacy + Convergence

**Summary**  
Good scope for closing the authenticated product surface, but it risks becoming a broad restyle. It also depends on Plan 02 behavior that may need `settings/page.tsx` changes earlier.

**Strengths**
- Correct `/settings/privacy` route avoids public `/privacy` collision.
- Clear mandatory privacy content.
- Good protection against touching public marketing pages.
- Keeps onboarding flow structure unchanged.

**Concerns**
- **HIGH:** Plan 02 already needs settings pause single-sourcing, but Plan 05 owns `settings/page.tsx`.
- **MEDIUM:** “No type size outside {12,14,20,28}” and “no ad-hoc colors” across existing screens may create scope creep.
- **MEDIUM:** `AppSidebar.tsx` is referenced as a privacy navigation integration point but not listed in files modified.
- **LOW:** Prohibiting `EN_SCAN_FILES` edits here is rigid if convergence adds or renames files.

**Suggestions**
- Move the settings pause refactor to Plan 02, or remove Plan 02’s settings consistency assertion until Plan 05.
- Limit token/type-size enforcement to touched files and obvious authenticated-screen violations.
- Add `components/shell/AppSidebar.tsx` to files if direct privacy nav changes are required; otherwise state Settings-page link is sufficient.

**Risk Assessment:** **MEDIUM**  
Mostly safe, but needs tighter boundaries to avoid restyle drift and dependency mismatch.

## 05A-06 — Closure

**Summary**  
The closure plan is valuable: full suite, gap register, validation sign-off, and requirements update. The main risk is overclaiming WEB-02 and using closure as a place for production fixes.

**Strengths**
- Good explicit gap register requirement.
- Correctly rolls up frontend-design notes instead of inventing them.
- Full suite gate is appropriate.
- Keeps backend gaps visible for later phases.

**Concerns**
- **HIGH:** WEB-02 should not be fully checked if draft-review, analytics, real audit-list, and real ledger-history remain incomplete. It needs a partial/sub-requirement annotation.
- **MEDIUM:** Closure says “fix minimal issue” if suite is red; that can hide scope creep. Production fixes should route back unless trivial and owned.
- **MEDIUM:** Windows/PowerShell environment makes `grep` in the verify command questionable.
- **LOW:** `schema.d.ts unchanged` needs an explicit `git diff --exit-code -- apps/web/lib/api/schema.d.ts` style verification.

**Suggestions**
- Do not mark WEB-02 fully complete unless requirements are split. Use “5A portion done; 5B/5C and backend gaps remain.”
- Replace `grep` with a cross-platform Node script or PowerShell `Select-String`.
- Add explicit diff checks for `schema.d.ts`, public privacy page, and backend directories.

**Risk Assessment:** **MEDIUM**  
Good closure discipline, but requirement status must stay honest.

## Final Risk Assessment

**Overall risk: MEDIUM-HIGH.**  
The plans are carefully designed and mostly aligned with the phase goals, but three execution risks need correction before implementation: Plan 02 route/layout ordering, test strategies for mocked data behind production gap-stubs, and WEB-02 overclaiming at closure. Fixing those would bring the phase down to **MEDIUM** risk.

---

## OpenCode Review

Let me review each plan for Phase 5A: User Surface — Web UI Core.

## 05A-01-PLAN.md Review

**Summary**: This plan establishes the foundational Wave-0 work: installing missing shadcn primitives (sidebar, table, alert-dialog, switch, sonner, dropdown-menu), creating the shared loading/empty/error state trio, setting up triage and billing query key factories, scaffolding the billing feature folder, adding Playwright/Vitest stubs, configuring 320px viewport, and extending i18n scan files. It's purely scaffolding work that enables subsequent plans.

**Strengths**:
- Properly sequences work by doing foundational setup first
- Correctly identifies and installs all required shadcn primitives
- Creates the essential shared state components (LoadingState/EmptyState/ErrorState)
- Sets up proper i18n infrastructure from the start
- Creates appropriate test stubs that won't block execution
- Follows the established patterns for query key factories
- Correctly flags billing ledger history and top-up intent status as gaps without attempting to build non-existent backend endpoints

**Concerns**:
- The plan doesn't explicitly verify that the shadcn primitives were installed correctly (though it does check typecheck/lint)
- No explicit verification that the shared state components follow the UI-SPEC guidelines
- The plan assumes the billing feature folder structure but doesn't detail what specific files should be created

**Suggestions**:
- Add explicit verification that the shared state components match the UI-SPEC descriptions
- Consider adding a basic smoke test for the shared state components
- Verify that the billing feature folder follows the exact structure outlined in the patterns documentation

**Risk Assessment**: LOW - This is foundational scaffolding work that follows established patterns and doesn't attempt to implement business logic.

## 05A-02-PLAN.md Review

**Summary**: This plan builds the authenticated app shell with persistent chrome region (pause toggle, credit balance, Gmail health indicator), refactors the pause toggle to single source of truth, and implements the chrome-related Playwright specs. It handles the layout rewrites, onboarding chrome suppression, and creates the shell components.

**Strengths**:
- Correctly implements the persistent app shell pattern with SidebarProvider/SidebarInset
- Properly handles onboarding chrome suppression via layout segment branching
- Implements the single source of truth for pause state with optimistic updates
- Correctly prefetches chrome data in layout and hydrates into client shell
- Implements all required chrome components (balance pill, health dot, pause switch)
- Properly handles the 320px responsive behavior using shadcn's built-in offcanvas
- Includes comprehensive test coverage for chrome behaviors

**Concerns**:
- The plan doesn't explicitly verify that the pause state single source of truth is working correctly across all access points (chrome, settings toggle, PauseBanner)
- No explicit verification that the credit balance polling interval is actually ~45s with proper staleTime
- The plan relies on the existing useToggleTriagePause test but doesn't specify what exactly needs to be changed for the D-13 implementation

**Suggestions**:
- Add explicit verification that all three pause toggle access points (chrome, settings, PauseBanner) stay in sync
- Consider adding a test that verifies the credit balance actually polls at the specified interval
- Be more specific about the exact changes needed to useToggleTriagePause for D-13 compliance

**Risk Assessment**: MEDIUM - This implements critical trust UI elements (pause/balance/health) that must be correct for user trust. Mistakes here could lead to serious UX or correctness issues.

## 05A-03-PLAN.md Review

**Summary**: This plan builds the /triage page with tabs for audit log/shadow mode/sender safety net, implements the responsive hybrid audit log renderer (Table/card list), undo functionality with AlertDialog confirm, shadow mode toggle, and sender safety net list with opt-in. It correctly handles the missing triage-audit list endpoint as a flagged gap.

**Strengths**:
- Correctly implements the single /triage page with ?tab= deep-linking
- Properly implements the responsive hybrid renderer (Table ≥ md / card list < md)
- Never truncates the Reason field into invisibility (shows full text in card view)
- Implements proper undo UX with AlertDialog confirming inverse Gmail change
- Handles out-of-window undo with muted "Undo window closed" label + tooltip
- Correctly implements shadow mode toggle with turn-off confirmation
- Properly implements sender safety net list with opt-in functionality
- Correctly flags the missing triage-audit list endpoint and documents degradation path
- Uses useInfiniteQuery for cursor-based pagination as specified

**Concerns**:
- The plan doesn't explicitly verify that the Reason field is never truncated in the Table view (it says it may be truncated-with-expand, which is acceptable per UI-SPEC)
- No explicit verification that the 30-day boundary divider is rendered correctly
- The plan doesn't specify how the "audit history not yet available" state will be implemented

**Suggestions**:
- Add explicit verification that the Reason field is visible in full in the card renderer
- Consider adding a test that verifies the 30-day boundary appears at the correct location
- Be more specific about the implementation of the "audit history not yet available" degradation state

**Risk Assessment**: MEDIUM - This implements the core audit log functionality which is key to user trust. However, the missing backend endpoint is properly handled as a flagged gap.

## 05A-04-PLAN.md Review

**Summary**: This plan builds the billing UI including the /billing page (balance + ledger) and /billing/top-up flow (amount → intent → instructions → poll → success/expired). It correctly handles the missing billing ledger-history and top-up intent-status endpoints as flagged gaps.

**Strengths**:
- Correctly implements /billing as its own route (not under /settings)
- Makes the credit balance figure the focal Display-type element on /billing
- Properly implements the top-up flow as a dedicated route (not modal)
- Correctly handles ?code= rehydration via sessionStorage (fallback for missing intentId)
- Implements proper polling for credit detection via balance rising
- Handles top-up expiry with clear "intent expired" panel
- Correctly flags the missing billing ledger-history endpoint and documents degradation path
- Never renders qrPayload as HTML (only as text or via QR component)
- Properly handles copyable fields with transient "Copied" feedback

**Concerns**:
- The plan doesn't explicitly verify that the sessionStorage rehydration actually works correctly
- No explicit verification that the top-up flow properly handles concurrent attempts
- The plan doesn't specify how the "transaction history coming soon" state will be implemented for the ledger gap

**Suggestions**:
- Add explicit verification that sessionStorage rehydration preserves intent state across refreshes
- Consider adding a test that verifies the top-up flow properly blocks new attempts while one is pending
- Be more specific about the implementation of the ledger gap degradation state

**Risk Assessment**: MEDIUM - This handles financial flows (top-up) which requires correctness, but the missing endpoints are properly handled as flagged gaps.

## 05A-05-PLAN.md Review

**Summary**: This plan builds the in-product privacy page (/settings/privacy) and executes the convergence pass on existing authenticated screens (rules, onboarding ×3, settings) to render inside the new app shell, use Phase 1.6 design tokens, shared loading/empty/error primitives, and be 320px-safe with no flow redesign.

**Strengths**:
- Correctly implements distinct authenticated /settings/privacy route (not colliding with public /privacy)
- Properly implements the three mandatory privacy points with vi/en lock-step
- Correctly converges existing authenticated screens onto the new app shell
- Properly applies Phase 1.6 base teal tokens (avoiding .zm-proto/.zm-auth clay skin)
- Correctly implements shared loading/empty/error primitives convergence
- Ensures 320px no-horizontal-scroll on converged screens
- Preserves onboarding chrome-suppressed layout as required
- Maintains existing functionality during convergence (no flow redesign)

**Concerns**:
- The plan doesn't explicitly verify that the privacy page is reachable from shell navigation
- No explicit verification that all converged screens actually use the shared state primitives
- The plan doesn't specify how to verify that no .zm-proto/.zm-auth classes are applied to authenticated screens

**Suggestions**:
- Add explicit verification that the privacy page is reachable from shell navigation (via Settings link)
- Consider adding a test that verifies converged screens use the shared state primitives
- Be more specific about the verification process for ensuring no clay skin classes are used

**Risk Assessment**: LOW - This is primarily convergence and styling work that doesn't introduce new business logic.

## 05A-06-PLAN.md Review

**Summary**: This is the phase closure plan that runs the full test suite, rolls up visual-review notes, creates the backend-surface gap register, signs off validation, and flips requirements to done.

**Strengths**:
- Properly runs the full green suite validation (typecheck, lint, test, i18n:check, test:e2e)
- Correctly creates the 05A-GAPS.md register documenting the three backend-surface gaps
- Appropriately signs off 05A-VALIDATION.md with nyquist_compliant: true
- Correctly flips WEB-01, WEB-03, WEB-04 to done in REQUIREMENTS.md
- Handles WEB-02 appropriately by noting the 5A portion is done while deferring 5B/5C
- Includes visual-review note rollup for all authenticated screens

**Concerns**:
- The plan doesn't specify what constitutes a "trivial fix" versus something requiring a follow-up plan
- No explicit verification that all visual-review notes were actually collected from previous plans
- The plan doesn't specify how to handle discrepancies in the visual-review notes

**Suggestions**:
- Add more specific criteria for what constitutes a "trivial fix" in the closure plan
- Consider adding a verification step that all expected visual-review notes are present
- Be more specific about how to handle any missing or inconsistent visual-review notes

**Risk Assessment**: LOW - This is a closure/validation plan that doesn't implement new functionality.

## Overall Risk Assessment

**Overall Risk Level**: MEDIUM

**Justification**: 
While individual plans have mostly LOW to MEDIUM risk, the phase as a whole has MEDIUM risk because:
1. It implements critical trust UI elements (pause/balance/health chrome) where mistakes could seriously impact user trust
2. It handles financial flows (top-up) where correctness is important
3. It implements the core audit log functionality which is key to the system's value proposition
4. However, the phase correctly handles all missing backend endpoints as flagged gaps with documented degradation paths, rather than attempting to build non-existent functionality
5. The plans follow established patterns and conventions from the codebase
6. The validation strategy is comprehensive with proper test coverage

The phase successfully delivers on its goals of creating a coherent authenticated product surface while respecting the frontend-only constraint and properly handling missing backend endpoints as explicit gaps.

---

## Consensus Summary

Both reviewers agree the plans are well-structured, correctly scoped to a frontend-only phase, and honest about the three backend-surface gaps (no triage-audit list endpoint, no billing ledger-history endpoint, no top-up intent-status/`intentId`). Codex is the harsher reviewer (overall **MEDIUM-HIGH**, several HIGH concerns); OpenCode is gentler (overall **MEDIUM**, mostly "add explicit verification" suggestions). No reviewer found a reason to block — but Codex's HIGH items are worth a `--reviews` replan pass.

### Agreed Strengths
- Foundation-first wave ordering (Wave 0 = primitives, shared states, query-key factories, billing skeleton, test stubs, 320px viewport, i18n scan list before any consumer).
- Explicit, disciplined handling of the 3 backend gaps as flagged degradation paths — no fake endpoints, `schema.d.ts` untouched.
- Strong trust-UI design: D-13 single-source pause toggle with optimistic update; chrome data prefetch + hydration; 320px chrome behavior.
- Good security posture in the `<threat_model>` blocks (CSRF/session, XSS on rendered backend strings, `qrPayload` never rendered as HTML, `?tab=`/`?code=` allow-listing).
- Closure plan rolls up real `frontend-design` notes and gates on the full suite + gap register + VALIDATION sign-off.

### Agreed Concerns (highest priority — candidates for `/gsd-plan-phase 5A --reviews`)
1. **[HIGH — Codex] Gap-stub vs. mocked-e2e mismatch (Plans 03, 04).** If `getAuditLog` / `useLedgerHistory` are pure empty stubs (no real endpoint), Playwright network mocks cannot exercise *populated* audit rows / ledger rows. Fix: use Vitest component tests for populated `AuditLog`/`LedgerTable`, and keep e2e focused on the real degraded ("not yet available") state — OR have the components accept optional injected data for tests. Also: make the gap stubs surface an explicit `unavailable` state, distinct from "empty".
2. **[HIGH — Codex] Cross-plan `settings/page.tsx` ownership conflict (Plans 02 ↔ 05).** Plan 02 requires the `/settings` pause toggle to share `triageKeys.pauseState()`, but `settings/page.tsx` isn't modified until Plan 05 (a different wave). Fix: move the settings pause refactor into Plan 02, or defer Plan 02's cross-settings pause assertion to Plan 05.
3. **[HIGH — Codex] Onboarding chrome-suppression via server-layout segment branching is fragile.** Branching inside `app/(protected)/layout.tsx` on the active `onboarding` segment is awkward in a Server Component layout. A route-group split (`(protected)/(app)/layout.tsx` with `<AppShell>` vs `(protected)/onboarding/layout.tsx` bare) is the standard, safer pattern.
4. **[HIGH — Codex] Plan 02 e2e navigates to `/triage` and `/billing` before Plans 03/04 create them.** Move that smoke coverage to Plans 03/04, or create minimal placeholder pages in Plan 02.
5. **[HIGH — Codex] `TopupIntentResponse` may lack bank-transfer fields the UI contract wants.** The known shape is `code` / `amountVnd` / `expiresAt` / `qrPayload` (raw EMV string). Plan 04 still expects an account number + bank fields. Confirm against `backend/api` whether those are encoded/configured/absent; if absent, degrade explicitly to `qrPayload` + code + amount + expiry only — and treat it as one of the flagged gaps.
6. **[HIGH — Codex] WEB-02 overclaiming at closure (Plan 06).** Don't flip WEB-02 fully — draft-review (5B), analytics (5C), and the real audit-list/ledger-history endpoints remain. Use a "5A portion done; 5B/5C + backend gaps remain" annotation. (OpenCode notes Plan 06 already does something like this — verify the annotation is explicit, not a full flip.)
7. **[MEDIUM — both] Generated i18n bundles / shadcn install file ownership.** `i18n:build` output isn't committed until Plan 06 → intermediate commits depend on generated-but-uncommitted JSON; `pnpm dlx shadcn add` may touch `package.json`/`pnpm-lock.yaml` which aren't in any plan's `files_modified`. Either commit per-plan or document the dependency; add the lockfile/`package.json` as conditionally-owned in Plan 01.
8. **[MEDIUM — both] "Explicit verification" gaps (OpenCode, across all plans).** Several behaviors lack a verifiable acceptance criterion: pause single-source actually in sync across chrome/settings/PauseBanner; balance `refetchInterval ≈ 45s` actually fires (not swallowed by the global 5-min `staleTime`); 30-day audit boundary divider position; privacy page reachable from shell nav; converged screens actually use the shared state primitives; no `.zm-proto`/`.zm-auth` classes on authenticated screens.
9. **[MEDIUM — Codex] Toast i18n friction.** Toast strings raised from hooks bypass `useTranslations`; keep toast invocation in components, or pass translated messages into the hook.
10. **[MEDIUM — Codex] Closure "fix minimal issue if suite red" + `grep` on Windows/PowerShell.** "Trivial fix" is undefined → can hide scope creep; route non-trivial fixes back to a plan. Replace `grep` in verify commands with PowerShell `Select-String` or a Node script. Add explicit `git diff --exit-code -- apps/web/lib/api/schema.d.ts` (and public `/privacy`, backend dirs) checks.
11. **[MEDIUM — Codex] `useTriagePauseState` reading `/me`.** Invalidating only `triageKeys.pauseState()` may leave `accountQueryKeys.me()` stale for other `/me` consumers — have the pause mutation also invalidate `accountQueryKeys.me()` while keeping `pauseState()` as the only pause *read* source.

### Divergent Views
- **Overall risk:** Codex says MEDIUM-HIGH (driven by the route/layout and gap-stub/e2e items); OpenCode says MEDIUM (sees the patterns as solid, the gaps as well-managed). The gap is mostly Codex weighting *execution-ordering* risks (which plan owns which file, which route exists when) that OpenCode didn't dig into.
- **Plan 01 risk:** OpenCode LOW; Codex MEDIUM (generated-file / dependency ownership noise).
- **Plan 02 risk:** OpenCode MEDIUM; Codex HIGH (the route-group + cross-plan settings + premature-e2e cluster).
- Codex raised the `TopupIntentResponse` bank-fields concern (#5) that OpenCode didn't surface at all — worth a backend check before executing Plan 04.
