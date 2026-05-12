---
phase: 05A-user-surface-web-ui-core
plan: 05
type: execute
wave: 3
depends_on: [01, 02]
files_modified:
  - apps/web/app/(protected)/(app)/settings/privacy/page.tsx
  - apps/web/features/privacy/components/PrivacySections.tsx
  - apps/web/features/privacy/messages.ts
  - apps/web/features/rules/components/RuleList.tsx
  - apps/web/features/rules/components/RulePreviewPanel.tsx
  - apps/web/features/rules/components/RulesWorkspace.tsx
  - apps/web/features/rules/components/RuleComposer.tsx
  - apps/web/features/rules/components/RuleTemplateGallery.tsx
  - apps/web/app/(protected)/(app)/rules/page.tsx
  - apps/web/app/(protected)/(app)/settings/page.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/app/(protected)/onboarding/gmail-connect/page.tsx
  - apps/web/app/(protected)/onboarding/template-select/page.tsx
  - apps/web/app/(protected)/onboarding/complete/page.tsx
  - apps/web/e2e/privacy-page.spec.ts
  - apps/web/e2e/rules.spec.ts
  - apps/web/e2e/onboarding-routes.spec.ts
  - apps/web/e2e/byok.spec.ts
  - apps/web/__tests__/i18n/messages.contract.test.ts
autonomous: true
requirements: [WEB-01, WEB-02, WEB-03]
user_setup: []

must_haves:
  truths:
    - "A distinct authenticated /settings/privacy route (at app/(protected)/(app)/settings/privacy/page.tsx) exists inside the app shell (a prominent /settings section / dedicated /settings/privacy segment — not a (protected)/privacy route that would collide with the public legal page), is linked from the shell navigation (a 'Privacy & data handling' link in the Settings page and, if AppSidebar has the affordance, a nav entry), renders in both vi and en, and explicitly states no-stored-bodies, no-auto-send, and BYOK (D-08)"
    - "The existing rules + settings pages (now under (protected)/(app)/ per Plan 02's route-group split) render inside the app shell automatically, and the convergence pass (Phase 1.6 base teal tokens — no .zm-proto/.zm-auth clay skin — + shared @/components/states loading/empty/error primitives + a 320px-no-horizontal-scroll sanity pass) is applied to each (D-09)"
    - "The onboarding (3 routes) screens keep their minimal nested chrome-suppressed layout (Plan 02's (protected)/onboarding/layout.tsx) and do not render inside the full sidebar shell; only tokens / shared-states / responsive convergence applies — no flow redesign (D-05, D-09)"
    - "No flow redesign anywhere — only shell/token/state/responsive integration on the existing screens"
    - "The /settings page's pause control is NOT touched here — Plan 02 already rebased it onto the single triageKeys.pauseState() source; this plan only applies tokens/shared-states/responsive convergence to settings/page.tsx and confirms the pause control still single-sources"
    - "The public (public)/privacy marketing page is untouched"
  artifacts:
    - path: "apps/web/app/(protected)/(app)/settings/privacy/page.tsx"
      provides: "Authenticated privacy page (in-shell, linked from settings/shell, distinct /settings/privacy segment per D-08), vi+en, states the three mandatory points"
    - path: "apps/web/features/privacy/components/PrivacySections.tsx"
      provides: "Privacy-page sections: 'What we never store' / 'What Zero Mail can and can't do' / 'Using your own AI key (BYOK)' + link to public /privacy (D-08)"
    - path: "apps/web/features/rules/components/RuleList.tsx"
      provides: "Rules list converged onto @/components/states primitives + 1.6 base teal tokens + 320px-safe — convergence only, no flow redesign (D-09)"
    - path: "apps/web/app/(protected)/(app)/settings/page.tsx"
      provides: "Settings page converged onto 1.6 tokens + shared states; BYOK stays here; pause control already single-sourced by Plan 02 (untouched here beyond tokens/states/responsive) (D-09)"
  key_links:
    - from: "apps/web/components/shell/AppSidebar.tsx"
      to: "/settings/privacy"
      via: "settings nav entry / Settings-page link (privacy reachable from settings/shell per D-08)"
      pattern: "settings"
    - from: "apps/web/app/(protected)/(app)/rules/page.tsx & app/(protected)/(app)/settings/page.tsx"
      to: "(protected)/(app)/layout.tsx shell + @/components/states"
      via: "auto-slot under the shell layout + LoadingState/EmptyState/ErrorState convergence (D-09)"
      pattern: "components/states"
---

<objective>
Add the in-product privacy page (D-08: a distinct authenticated `/settings/privacy` route at `app/(protected)/(app)/settings/privacy/page.tsx` inside the app shell, linked from the shell/settings nav, vi+en, explicitly stating no-stored-bodies / no-auto-send / BYOK — the public `(public)/privacy` marketing page stays untouched), and run the convergence pass on the existing authenticated screens (D-09/D-05): rules / onboarding ×3 / settings render inside the new app shell (rules + settings are now under `(app)/` per Plan 02's route-group split; onboarding stays under its bare `(protected)/onboarding/layout.tsx`), on Phase 1.6 base teal design tokens (no `.zm-proto`/`.zm-auth` clay skin), using the shared `@/components/states` loading/empty/error primitives, with no horizontal scroll at 320px — no flow redesign. The `/settings` pause control is NOT touched here (Plan 02 already rebased it onto the single `triageKeys.pauseState()` source); this plan only applies tokens/states/responsive to `settings/page.tsx`.

Purpose: WEB-03 (the privacy page) + WEB-02 (bringing the existing screens onto the new shell at a consistent bar — WEB-02 stays partial after 5A; draft-review → 5B, analytics → 5C).
Output: `/settings/privacy` page + `PrivacySections`, the converged rules/onboarding/settings screens, the `privacy-page` Playwright spec, extended `e2e/rules.spec.ts` / `e2e/onboarding-routes.spec.ts` / `e2e/byok.spec.ts` (320px + in-shell assertions), the i18n contract test extension.
</objective>

<reviewer_response>
Cross-AI review:
- #2 (Codex HIGH — settings/page.tsx ownership): the `/settings` pause-toggle rebase is moved to Plan 02. This plan still touches `app/(protected)/(app)/settings/page.tsx` for tokens/shared-states/responsive convergence ONLY — it does NOT touch the pause control (Plan 02 owns that). Plan 02 is Wave 2 and this plan is Wave 3, so the sequential `settings/page.tsx` handoff is clean (this plan `depends_on: [01, 02]`); there is no same-wave overlap.
- #3 (Codex HIGH — route-group split): rules + settings (and their pages) are now under `app/(protected)/(app)/...`; onboarding stays under `app/(protected)/onboarding/...`. Paths updated throughout this plan; `05A-PATTERNS.md` analog references that predate the split should be applied to the `(app)/` locations.
- #8 (OpenCode MEDIUM): explicit acceptance criteria added — the privacy page is reachable from a real shell nav element/link (Playwright assertion on the Settings-page "Privacy & data handling" link); converged screens actually consume `@/components/states/*` (a source/component assertion); no `.zm-proto`/`.zm-auth` classes present on authenticated screens (a `Select-String` source assertion + a Playwright class check).
- #10 (Codex MEDIUM — Windows/PowerShell): the "no `.zm-proto`/`.zm-auth`" source check uses PowerShell `Select-String` (or a Node script), not `grep`.
- Note: `components/shell/AppSidebar.tsx` is added to `files_modified` because the privacy nav reachability per D-08 may require a Settings sub-link / entry in the sidebar; if the sidebar is strictly flat (Plan 02's D-02 flat nav) and the Settings-page link suffices, the `AppSidebar.tsx` edit is a no-op — note which in the SUMMARY.
</reviewer_response>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05A-user-surface-web-ui-core/05A-SPEC.md
@.planning/phases/05A-user-surface-web-ui-core/05A-CONTEXT.md
@.planning/phases/05A-user-surface-web-ui-core/05A-PATTERNS.md
@.planning/phases/05A-user-surface-web-ui-core/05A-UI-SPEC.md
@.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
@.planning/phases/05A-user-surface-web-ui-core/05A-01-SUMMARY.md
@.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md
@CLAUDE.md
@CONVENTIONS.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Build the /settings/privacy page (in-shell, linked, vi+en, three mandatory points)</name>
  <behavior>
    - The /settings/privacy route renders inside the app shell, is reachable via the shell/settings navigation (a "Privacy & data handling" link in the Settings page and/or a nav entry per D-08), and contains three sections: "What we never store" (no email bodies, no AI prompts, no AI replies, no embeddings — content sanitized/truncated/prompt-injection-hardened then discarded), "What Zero Mail can and can't do" (can label/archive/save drafts; cannot send email — ever), "Using your own AI key (BYOK)" (bring your own model key in Settings), plus a link to the public legal /privacy page. (test: messages.contract.test.ts extension asserts the privacy.* keys exist in both vi and en with lock-step parity; the Playwright spec asserts all three points render, that the page is reachable from the Settings-page link, and that switching locale renders the vi text.)
  </behavior>
  <read_first>
    - apps/web/app/(public)/privacy/page.tsx (structure idiom for the public legal page — and the path NOT to collide with: D-08 uses /settings/privacy, not (protected)/privacy)
    - apps/web/app/(protected)/(app)/settings/page.tsx (the Card-chain in-shell section layout — note settings is now under (app)/; where to add the link to /settings/privacy)
    - apps/web/components/shell/AppSidebar.tsx (Plan 02 — check whether the Settings nav entry can carry a sub-link or whether a flat-nav + Settings-page link is the reachability path)
    - apps/web/components/ui/{card,separator,button}.tsx, apps/web/components/states/* (Plan 01)
    - apps/web/features/privacy/messages.ts (the seeded `privacy.*` keys from Plan 01 — extend), apps/web/__tests__/i18n/messages.contract.test.ts (the parity-contract test to extend)
    - apps/web/i18n/messages/{vi,en}.json + apps/web/scripts/merge-feature-i18n.ts + apps/web/scripts/check-i18n.ts (the i18n pipeline — note Plan 01 already registered the privacy page path `app/(protected)/(app)/settings/privacy/page.tsx` + `PrivacySections.tsx` in EN_SCAN_FILES; do not edit it; do not commit generated bundles)
    - 05A-CONTEXT.md D-08; 05A-UI-SPEC.md sections Copywriting (the exact privacy-page section headings/bodies — three mandatory points + the link to public /privacy), Color (accent for inline text links; base teal token contract, no clay skin), Typography (12/14/20/28; 20px section headings differentiated by weight/color), Visual Hierarchy ("What we never store" is the focal section), Spacing
    - 05A-PATTERNS.md section "app/(protected)/settings/privacy/page.tsx (new — static i18n copy)" (apply to the `(app)/` location)
    - node_modules/next/dist/docs/ — App Router nested route segments under a route group (read before writing the route)
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing UI; record a `frontend-design` visual-review note (desktop + 320px, light + dark) for the privacy page in the SUMMARY.
    Create `app/(protected)/(app)/settings/privacy/page.tsx` — a thin page rendering `<PrivacySections/>` inside the standard in-shell content container (idiom from `(app)/settings/page.tsx`). Create `features/privacy/components/PrivacySections.tsx` — three `Card` sections with 20px headings (weight/color-differentiated, not extra sizes) carrying the UI-SPEC copy for "What we never store" (focal), "What Zero Mail can and can't do", "Using your own AI key (BYOK)" — every visible string via `next-intl` `privacy.*` keys — plus an accent-colored inline text link to the public `/privacy` page. Add a "Privacy & data handling" link to `/settings/privacy` from `app/(protected)/(app)/settings/page.tsx` (convergence-only edit aside from this link). If `AppSidebar.tsx` can carry a Settings sub-link / a dedicated entry without violating Plan 02's flat-nav (D-02), add `/settings/privacy` there too; if not, the Settings-page link satisfies "reachable from the shell navigation" — note which in the SUMMARY (and leave `AppSidebar.tsx` unchanged if the no-op applies). Extend `apps/web/features/privacy/messages.ts` with all `privacy.*` keys (vi + en lock-step), run `pnpm --filter web i18n:build` locally. Extend `apps/web/__tests__/i18n/messages.contract.test.ts` to assert `privacy.*` vi/en parity. Do NOT edit `apps/web/scripts/check-i18n.ts` — Plan 01 already registered the privacy page + `PrivacySections.tsx`. Do NOT touch `app/(public)/privacy/page.tsx`. Do NOT commit the generated i18n bundles (Plan 06 owns them).
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- __tests__/i18n</automated>
  </verify>
  <acceptance_criteria>
    - `app/(protected)/(app)/settings/privacy/page.tsx` exists, renders inside the app shell, and renders `<PrivacySections/>`.
    - `features/privacy/components/PrivacySections.tsx` renders three sections explicitly stating: (1) no long-term storage of email bodies / AI prompts / AI replies / embeddings, (2) no auto-send (can label/archive/save drafts only), (3) the BYOK option — all via `next-intl` `privacy.*` keys, plus an accent inline link to the public `/privacy`.
    - `app/(protected)/(app)/settings/page.tsx` has a "Privacy & data handling" link to `/settings/privacy`; the privacy page is reachable from the shell navigation (via the Settings entry/page) per D-08; the SUMMARY states whether `AppSidebar.tsx` got a sub-link or whether the Settings-page link is the reachability path.
    - `apps/web/__tests__/i18n/messages.contract.test.ts` asserts `privacy.*` keys exist in both `vi` and `en` with parity; it passes under `pnpm --filter web test`.
    - `app/(public)/privacy/page.tsx` is unchanged.
    - No hardcoded English literals in the new privacy files (via `pnpm --filter web i18n:check`).
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review note for the privacy page and the reachability decision.
  </acceptance_criteria>
  <done>The in-product privacy page exists, is linked + in-shell + vi/en, states the three mandatory points; the public privacy page is untouched; gates green.</done>
</task>

<task type="auto">
  <name>Task 2: Convergence pass — rules / onboarding ×3 / settings onto the shell + 1.6 tokens + shared states + 320px (pause control NOT touched here)</name>
  <read_first>
    - apps/web/app/(protected)/layout.tsx + apps/web/app/(protected)/(app)/layout.tsx + apps/web/app/(protected)/onboarding/layout.tsx + apps/web/components/shell/* (Plan 02 — the shell these screens now render inside; onboarding stays under its bare layout)
    - apps/web/features/rules/components/{RulesWorkspace,RuleComposer,RuleList,RulePreviewPanel,RuleTemplateGallery}.tsx, apps/web/app/(protected)/(app)/rules/page.tsx (the existing rules workspace, now under (app)/ — convergence only, NO flow redesign)
    - apps/web/app/(protected)/(app)/settings/page.tsx (the existing settings page, now under (app)/ — convergence only; the new /settings/privacy link from Task 1 is already there; the pause control is ALREADY single-sourced by Plan 02 — do NOT touch it)
    - apps/web/app/(protected)/onboarding/{gmail-connect,template-select,complete}/page.tsx (the 3 onboarding routes — structure unchanged per D-05; only tokens/shared-states/responsive)
    - apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx (Plan 01 — replace the ad-hoc inline trio in RuleList.tsx / RulePreviewPanel.tsx with these)
    - apps/web/app/globals.css (the Phase 1.6 base teal `:root`/`.dark` tokens — the styling source of truth; the `.zm-proto`/`.zm-auth` clay skin classes that must NOT be applied to authenticated screens; the existing `@media (max-width:360px)` public-nav guard the authenticated shell needs an equivalent of)
    - 05A-CONTEXT.md D-05, D-09; 05A-UI-SPEC.md sections "Scope note" (authenticated screens use the base teal token contract, NOT the .zm-proto/.zm-auth clay skin), Color, Typography (12/14/20/28 only — flag any screen using other sizes), Spacing (8-pt scale; 40/44px touch targets), Responsive (the 320px hard floor — no horizontal scroll), and the "Notes for the Planner" convergence bullets
    - 05A-PATTERNS.md section "Existing pages (rules, settings, onboarding/*) — Convergence pass only"; section "components/states/{...} (new — extract & consolidate)" (the RuleList.tsx ad-hoc trio is the consolidation source)
    - 05A-RESEARCH.md Anti-Pattern "Applying the .zm-proto / .zm-auth clay skin to authenticated screens"
    - node_modules/next/dist/docs/ — relevant Next 16 notes if any touched file uses async APIs
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE restyling any screen; record `frontend-design` visual-review notes (desktop + 320px, light + dark) for the converged rules workspace, the three onboarding routes, and the settings page in the SUMMARY.
    For the rules workspace (`RulesWorkspace`, `RuleComposer`, `RuleList`, `RulePreviewPanel`, `RuleTemplateGallery`, `(app)/rules/page.tsx`): (a) confirm it renders correctly inside the new app shell (it does automatically via `(protected)/(app)/layout.tsx` — verify no double-padding/double-header collisions and adjust the page container if needed); (b) replace the ad-hoc inline loading/empty markup in `RuleList.tsx` (and the loading/empty markup in `RulePreviewPanel.tsx`) with `<LoadingState/>` / `<EmptyState heading body cta?/>` / `<ErrorState heading body onRetry/>` from `@/components/states` (keeping the existing copy via the existing `rules.*` i18n keys); (c) swap any ad-hoc hex/rgb colors or arbitrary-px layout gaps for the Phase 1.6 base teal tokens / the 8-pt Tailwind spacing scale — do NOT apply `.zm-proto`/`.zm-auth`; (d) ensure no horizontal scroll at 320px (stack/wrap as needed, ≥44px touch targets). NO flow redesign — the rule composer + preview + list + template gallery behave exactly as before.
    For the settings page (`(app)/settings/page.tsx`): convergence ONLY — in-shell sanity, 1.6 tokens, shared states for any list, 320px safe. Do NOT touch the pause control — Plan 02 already rebased it onto `useTriagePauseState`/`useToggleTriagePause` (the single `triageKeys.pauseState()` cache entry); just confirm (a quick read) that it still single-sources and leave it. The BYOK form (`features/llm/ByokForm`) stays on `/settings` per D-07 — convergence only.
    For the three onboarding routes (`gmail-connect`, `template-select`, `complete`): structure unchanged (D-05) — they render inside the minimal chrome-suppressed onboarding layout (Plan 02), not the full shell; apply only 1.6 tokens, the shared loading/empty/error primitives where they currently inline their own, and a 320px no-horizontal-scroll pass.
    If the shell/chrome needs an authenticated-shell `@media (max-width:360px)` guard and Plan 02 didn't already add one, add a small guard in the shell CSS (not in `globals.css`'s public-nav block) — coordinate with Plan 02's `ChromeHeader`. The convergence pass should reuse existing `rules.*` keys and add no new English literals; do NOT edit `EN_SCAN_FILES` (Plan 01 owns it — if a converged screen genuinely needs a new key, flag it in the SUMMARY for a Plan-06 reconciliation, do not add it here). Do NOT commit the generated i18n bundles.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test</automated>
  </verify>
  <acceptance_criteria>
    - The rules workspace renders inside the app shell with no layout collisions; `RuleList.tsx` and `RulePreviewPanel.tsx` import and use `@/components/states` primitives (`LoadingState`/`EmptyState`/`ErrorState`) instead of ad-hoc inline loading/empty markup; no `.zm-proto`/`.zm-auth` class is applied to any authenticated screen (verified by a PowerShell `Select-String` over the touched files — zero matches); the rule composer/preview/list/template-gallery behavior is unchanged.
    - `app/(protected)/(app)/settings/page.tsx` still consumes the `triageKeys.pauseState()` cache entry via `useTriagePauseState`/`useToggleTriagePause` (Plan 02's rebase, untouched here); it renders on 1.6 tokens; the BYOK form stays on `/settings`.
    - The three onboarding routes render inside the minimal chrome-suppressed layout (no sidebar), on 1.6 tokens, with no horizontal scroll at 320px; their flow/structure is unchanged.
    - No authenticated screen uses a type size outside {12,14,20,28} or an ad-hoc hex/rgb color after the pass (spot-check the touched files; flag any unavoidable exception in the SUMMARY).
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test` exit 0.
    - SUMMARY contains the `frontend-design` visual-review notes for the converged rules workspace, the three onboarding routes, and the settings page.
  </acceptance_criteria>
  <done>Existing authenticated screens render inside the new shell on 1.6 tokens with shared state primitives and a 320px-safe layout; no flow redesign; the pause control is untouched (already single-sourced by Plan 02); gates green; visual reviews recorded.</done>
</task>

<task type="auto">
  <name>Task 3: Implement the privacy-page Playwright spec + extend rules / onboarding / byok specs for 320px + in-shell</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (serial mode; `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me`; session+locale cookies; horizontal-overflow check `document.documentElement.scrollWidth > window.innerWidth`) — and the existing rules golden path to extend, not break
    - apps/web/e2e/onboarding-routes.spec.ts, apps/web/e2e/byok.spec.ts (the existing specs to extend with 320px + in-shell / chrome-suppressed assertions)
    - apps/web/e2e/mobile-topbar.spec.ts (320px viewport pattern); apps/web/playwright.config.ts (the 320px approach from 05A-01-SUMMARY)
    - apps/web/e2e/privacy-page.spec.ts (the Plan 01 stub to fill in)
    - 05A-VALIDATION.md section "Per-Task Verification Map" rows for "In-product privacy" and "Convergence pass"
    - 05A-UI-SPEC.md section Copywriting (the three mandatory privacy points to assert)
  </read_first>
  <action>
    Fill in `e2e/privacy-page.spec.ts` (the Plan 01 stub) using the `e2e/rules.spec.ts` harness: visit `/settings/privacy` (with `/me` mocked + session+locale cookies); assert the page renders inside the app shell (sidebar + chrome present); assert all three mandatory points render (no-stored-bodies / no-auto-send / BYOK — match on the UI-SPEC headings/key phrases); assert there is a link to the public `/privacy`; switch the `NEXT_LOCALE` cookie to `vi`, reload, and assert the Vietnamese text renders (match on a `vi`-bundle phrase). Assert the page is reachable from the shell navigation: navigate from `/settings`, click the "Privacy & data handling" link (a real nav element), assert the URL is `/settings/privacy` and the privacy content renders. Run at 1280px and 320px (no horizontal scroll). Add a Playwright class assertion that the privacy page (and `/settings`) carry no `.zm-proto`/`.zm-auth` class on the document/root elements.
    Extend `e2e/rules.spec.ts`: add assertions that the rules workspace renders inside the app shell (sidebar + chrome visible), has no horizontal scroll at 320px, and carries no `.zm-proto`/`.zm-auth` class — without breaking the existing golden path.
    Extend `e2e/onboarding-routes.spec.ts`: add assertions that the onboarding routes do NOT show the sidebar (chrome-suppressed per D-05) and have no horizontal scroll at 320px — without breaking the existing flow assertions.
    Extend `e2e/byok.spec.ts`: add assertions that `/settings` (with the BYOK form) renders inside the app shell, has no horizontal scroll at 320px, and carries no `.zm-proto`/`.zm-auth` class.
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- privacy-page rules onboarding-routes byok</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/privacy-page.spec.ts` contains real assertions: in-shell render, all three mandatory points present, a link to public `/privacy`, vi-locale render, reachable from `/settings` via the "Privacy & data handling" link, no `.zm-proto`/`.zm-auth` class, at 1280px and 320px.
    - `e2e/rules.spec.ts` / `e2e/onboarding-routes.spec.ts` / `e2e/byok.spec.ts` are extended with in-shell (or chrome-suppressed, for onboarding) + 320px-no-horizontal-scroll + no-clay-skin-class assertions and still pass their existing assertions.
    - `pnpm --filter web test:e2e` passes (including all four of these specs).
  </acceptance_criteria>
  <done>The privacy page + the convergence bar are covered by passing Playwright specs at desktop + 320px, including reachability and no-clay-skin checks.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | The converged screens still use the existing typed-client calls (rules CRUD, `/me`, BYOK, onboarding) — no new endpoints; the privacy page makes no backend calls. |
| backend response strings → React render | Rule names / preview text / sender fields already rendered by the rules workspace (unchanged); the privacy page renders only static `next-intl` copy. |
| URL searchParams → app state | No new searchParam handling in this plan. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-17 | XSS via rendered backend strings | `RuleList` / `RulePreviewPanel` rendering rule names / preview output (touched by the convergence swap to `@/components/states`) | mitigate | The swap only replaces the loading/empty wrapper markup — rule names and preview text remain rendered as React text children, auto-escaped; no dangerously-set-inner-HTML React prop introduced; verified by a `Select-String` source check on the touched files. |
| T-05A-18 | Information disclosure | privacy page content | accept | Static, owner-agnostic policy copy — no tenant data, no email content, no tokens. |
| T-05A-19 | Reflected state / open redirect | privacy page | accept | No searchParams read or reflected on the privacy page; the only outbound link is a constant href to the public `/privacy`. |
| T-05A-20 | State drift (correctness) | `(app)/settings/page.tsx` pause toggle | mitigate | Not touched here — Plan 02 already single-sourced it on `triageKeys.pauseState()`; this plan only confirms it still reads/writes that one cache entry; re-asserted by the pause-toggle e2e (Plan 02) which checks chrome ↔ settings consistency. |

No high-severity threats — frontend-only; the convergence pass introduces no new backend access; rendered backend strings stay React-escaped; the privacy page reflects no state and has no unvalidated redirect; no dangerously-set-inner-HTML React prop.
</threat_model>

<verification>
- `pnpm --filter web i18n:build` is run as part of the gate but the generated `i18n/messages/{vi,en}.json` are NOT in this plan's `files_modified` and must not be committed here — Plan 06 regenerates and commits the canonical bundles. The per-feature `messages.ts` files (which ARE owned here) are the source of truth.
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged; `app/(public)/privacy/page.tsx` unchanged; no new backend endpoint.
- No new runtime dependency in `apps/web/package.json`.
- Manual: load `/settings/privacy` (vi + en), `/rules`, `/settings`, `/onboarding/gmail-connect|template-select|complete` in a real browser at 1280px and 320px, light + dark — privacy page in-shell + linked + states all three points; rules/settings in-shell on 1.6 tokens; onboarding chrome-suppressed; no `.zm-proto`/`.zm-auth` on any authenticated screen; no horizontal scroll at 320px anywhere.
</verification>

<success_criteria>
- A distinct authenticated `/settings/privacy` page exists in-shell (under `(app)/`), reachable from a real shell nav element/link, vi+en, stating no-stored-bodies / no-auto-send / BYOK; the public privacy page is untouched; rules / onboarding ×3 / settings render inside the new shell (onboarding chrome-suppressed) on 1.6 base teal tokens with shared loading/empty/error primitives, 320px-safe, no flow redesign; the `/settings` pause control is untouched (already single-sourced by Plan 02); no `.zm-proto`/`.zm-auth` on any authenticated screen; all gates green; visual reviews recorded.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-05-SUMMARY.md` (record: the `frontend-design` visual-review notes for the privacy page + converged rules/onboarding/settings; the privacy-nav reachability decision (AppSidebar sub-link vs. Settings-page link); any new i18n key the convergence pass needed (flagged for Plan 06); any token/type-size exception that couldn't be resolved; whether the shell needed an extra 320px CSS guard beyond Plan 02).
</output>
