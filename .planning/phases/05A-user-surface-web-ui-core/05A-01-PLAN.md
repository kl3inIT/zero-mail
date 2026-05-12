---
phase: 05A-user-surface-web-ui-core
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - apps/web/components.json
  - apps/web/components/ui/sidebar.tsx
  - apps/web/components/ui/sheet.tsx
  - apps/web/components/ui/table.tsx
  - apps/web/components/ui/alert-dialog.tsx
  - apps/web/components/ui/switch.tsx
  - apps/web/components/ui/sonner.tsx
  - apps/web/components/ui/dropdown-menu.tsx
  - apps/web/package.json        # CONDITIONAL — only if `pnpm dlx shadcn add` adds a runtime dep
  - apps/web/pnpm-lock.yaml      # CONDITIONAL — same; commit only if the shadcn install changed it
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
  - apps/web/scripts/check-i18n.ts
  - apps/web/playwright.config.ts
  - apps/web/e2e/app-shell.spec.ts
  - apps/web/e2e/pause-toggle.spec.ts
  - apps/web/e2e/billing-balance.spec.ts
  - apps/web/e2e/connection-health.spec.ts
  - apps/web/e2e/triage-audit.spec.ts
  - apps/web/e2e/triage-shadow-senders.spec.ts
  - apps/web/e2e/billing-topup.spec.ts
  - apps/web/e2e/privacy-page.spec.ts
  - apps/web/features/billing/hooks/useBillingBalance.test.tsx
  - apps/web/features/billing/hooks/useTopupCreditWatch.test.tsx
autonomous: true
requirements: [WEB-01]
user_setup: []

must_haves:
  truths:
    - "shadcn sidebar/sheet/table/alert-dialog/switch/sonner/dropdown-menu primitives exist under apps/web/components/ui/"
    - "A shared Loading/Empty/Error state trio exists and is importable from @/components/states"
    - "features/triage/query-keys.ts exports triageKeys with pauseState(), auditLog(...), shadowMode(), senderSafetyNet()"
    - "features/billing/ is a complete feature folder (api, query-keys, hooks, messages) consuming only the existing OpenAPI surface"
    - "The gap-stub hooks (useLedgerHistory, getAuditLog) surface an explicit `{ unavailable: true }`-style state distinct from an empty list, so the UI can render 'not yet available' differently from 'no entries yet'"
    - "All eight new Playwright spec files exist (at minimum a skipped/stub describe) and the suite still runs green"
    - "pnpm --filter web typecheck && lint && i18n:check pass after this plan"
  artifacts:
    - path: "apps/web/components/states/EmptyState.tsx"
      provides: "Shared empty-state primitive (heading/body/optional CTA)"
    - path: "apps/web/features/triage/query-keys.ts"
      provides: "triageKeys factory — single source of cached-key shapes for triage"
      contains: "pauseState"
    - path: "apps/web/features/billing/hooks/useBillingBalance.ts"
      provides: "Polled credit-balance read hook (refetchInterval 45s, staleTime 30s)"
    - path: "apps/web/scripts/check-i18n.ts"
      provides: "EN_SCAN_FILES extended with every new Phase 5A component/page path (route-group (app)/ paths included)"
  key_links:
    - from: "apps/web/features/billing/api/billing-api.ts"
      to: "/api/billing/balance"
      via: "typed openapi-fetch api.GET"
      pattern: "api/billing/balance"
---

<objective>
Build the Wave-0 foundations that every Phase 5A feature plan consumes: install the missing shadcn primitives, create the shared loading/empty/error trio, create `features/triage/query-keys.ts`, scaffold the entire `features/billing/` feature folder (api + query-keys + hooks + messages, against the existing OpenAPI surface only), add the eight new Playwright spec files (as stubs), the new Vitest hook specs, a 320px Playwright viewport, and extend `scripts/check-i18n.ts` `EN_SCAN_FILES` with every new Phase 5A file path (including the `(protected)/(app)/...` route-group paths Plan 02 introduces).

Purpose: downstream plans (shell, triage, billing UI, privacy/convergence) must not be blocked on missing scaffolding, missing primitives, or a broken i18n gate.
Output: installed primitives, `components/states/*`, `features/triage/query-keys.ts`, `features/billing/**`, e2e + vitest stubs, updated `check-i18n.ts` + `playwright.config.ts`.
</objective>

<reviewer_response>
Cross-AI review (#7 / Codex+OpenCode MEDIUM): `package.json` + `pnpm-lock.yaml` are now listed as CONDITIONALLY-owned (only if `pnpm dlx shadcn add` adds a runtime dep). Generated i18n bundles (`i18n/messages/{vi,en}.json`) stay owned only by Plan 06 — this plan runs `pnpm --filter web i18n:build` locally for its own `i18n:check` gate and does NOT commit the generated bundles; its `<verify>` asserts only that `i18n:check` passes against the per-feature `messages.ts` (the source of truth), never that the committed bundle is up to date.
Review #1 (Codex HIGH): the gap stubs now surface an explicit `unavailable` state distinct from "empty".
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
@.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
@CLAUDE.md
@CONVENTIONS.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Install missing shadcn primitives + create shared loading/empty/error trio</name>
  <read_first>
    - apps/web/components.json (preset base-nova, baseColor neutral, rsc true)
    - apps/web/components/ui/skeleton.tsx, apps/web/components/ui/alert.tsx, apps/web/components/ui/button.tsx (token-bound primitives to mirror)
    - apps/web/features/rules/components/RuleList.tsx (the inline isLoading/empty trio that LoadingState/EmptyState/ErrorState are extracted from — see 05A-PATTERNS.md "components/states")
    - 05A-PATTERNS.md section "components/states/{LoadingState,EmptyState,ErrorState}.tsx" and section "shadcn primitive selection"
    - 05A-UI-SPEC.md section Copywriting (error state "Couldn't load this" / "Try again"; loading = Skeleton rows, never bare spinner) and section "Primitives to install this phase"
    - apps/web/AGENTS.md (read node_modules/next/dist/docs/ before any Next code; components/ui/** is copied source, ESLint/Prettier-excluded)
  </read_first>
  <action>
    From `apps/web`, run `pnpm dlx shadcn@latest add sidebar table alert-dialog switch sonner dropdown-menu` (this also pulls `sheet`). Do not edit the generated `components/ui/*.tsx` beyond what the CLI writes; they stay ESLint/Prettier-excluded copied source. Confirm the sidebar cookie name is exported (`SIDEBAR_COOKIE_NAME`) in the generated `components/ui/sidebar.tsx` — note its value in the SUMMARY for Plan 02. If the install changed `apps/web/package.json` and/or `apps/web/pnpm-lock.yaml` (a new runtime dep), commit those changes with this plan and record it in the SUMMARY; if it did not, leave them untouched (they are conditionally-owned).
    Then create `apps/web/components/states/LoadingState.tsx`, `EmptyState.tsx`, `ErrorState.tsx`, generalising the ad-hoc trio currently inlined in `features/rules/components/RuleList.tsx`: `LoadingState` renders shadcn `Skeleton` rows (a `variant?: 'rows' | 'cards'` prop, default `rows`, configurable count, default 3) — never a bare spinner; `EmptyState` takes `heading`, `body`, optional `cta?: ReactNode` and renders a dashed-border centered block (8-pt spacing per UI-SPEC); `ErrorState` takes `heading`, `body`, `onRetry: () => void` and renders a "Try again" `Button` (outline) wired to `onRetry`. All copy is passed in by callers (no hardcoded English in these primitives — they receive already-translated strings), so no i18n keys live here. Use only design-token classes (`bg-muted`, `text-muted-foreground`, `border-dashed`, etc.) — no ad-hoc colors. Invoke the `frontend-design` skill before writing these components; record a one-line `frontend-design` visual-review note for the states trio in the SUMMARY.
  </action>
  <verify>
    <automated>cd apps/web && pnpm typecheck && pnpm lint</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/components/ui/{sidebar,sheet,table,alert-dialog,switch,sonner,dropdown-menu}.tsx` all exist.
    - `apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx` exist, export named React components, contain no English string literals (callers pass copy), and import from `@/components/ui/*` only for shadcn primitives.
    - If `pnpm dlx shadcn add` modified `package.json`/`pnpm-lock.yaml`, those changes are committed and noted in the SUMMARY; otherwise both files are unchanged.
    - `cd apps/web && pnpm typecheck && pnpm lint` exit 0.
    - SUMMARY records the value of `SIDEBAR_COOKIE_NAME` from the generated sidebar primitive and whether the install touched `package.json`/lockfile.
  </acceptance_criteria>
  <done>Missing primitives installed; shared loading/empty/error trio exists; typecheck + lint green.</done>
</task>

<task type="auto">
  <name>Task 2: Create features/triage/query-keys.ts and the full features/billing/ skeleton (gap stubs surface an explicit `unavailable` state)</name>
  <read_first>
    - apps/web/features/rules/query-keys.ts, apps/web/features/gmail/query-keys.ts, apps/web/features/account/query-keys.ts (the `as const` nested-factory idiom)
    - apps/web/features/rules/api/rules-api.ts (the `unwrap`/`jsonHeaders`/`unsafeHeaders` idiom — copy verbatim)
    - apps/web/lib/api/client.ts (api, xsrfHeader) and apps/web/lib/api/schema.d.ts (grep for `/api/billing/balance`, `/api/billing/topup/intent` — confirm exact path strings and the BillingBalanceResponse / TopupIntentResponse / TopupIntentRequest component shapes; NOTE: `TopupIntentResponse` carries ONLY `code?`, `amountVnd?`, `expiresAt?`, `qrPayload?` — no `intentId`, no `accountNumber`/`accountName`/`bankName`/`transferContent`. `accountNumber` in the schema belongs to `SepayWebhookPayload`, NOT to the topup response.)
    - apps/web/features/gmail/hooks/useTenantStatus.ts (base read-hook shape) and apps/web/features/llm/hooks/use-byok.ts (bare mutation shape)
    - apps/web/lib/query-client.tsx (global staleTime is 5min — useBillingBalance MUST override staleTime≈30s + refetchInterval≈45000 + refetchIntervalInBackground:false per D-11)
    - 05A-CONTEXT.md D-11, D-13, D-15; 05A-RESEARCH.md Pitfall 5 (global staleTime swallows the 45s refetch)
    - 05A-PATTERNS.md sections "features/triage/query-keys.ts & features/billing/query-keys.ts", "useBillingBalance", "useCreateTopupIntent", "useTriageAuditLog / useLedgerHistory (BLOCKED)"
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (confirms only `/balance` GET + `/topup/intent` POST exist — NO ledger-history list, NO intent-status, NO intentId)
  </read_first>
  <action>
    Create `apps/web/features/triage/query-keys.ts` exporting `triageKeys` as an `as const` nested factory with `all: ['triage']`, `pauseState()`, `auditLog(filters?)`, `shadowMode()`, `senderSafetyNet()`. Per D-13, `triageKeys.pauseState()` is the single canonical key for the pause toggle.
    Create the `apps/web/features/billing/` folder:
      - `api/billing-api.ts` — mirror `rules-api.ts`'s `unwrap` helper. Export `getBillingBalance({ signal }?)` calling `api.GET('/api/billing/balance', ...)` and `createTopupIntent(amountVnd: number)` calling `api.POST('/api/billing/topup/intent', { body: { amountVnd }, headers: jsonHeaders() })`. Add a `getLedgerHistory` that is GAP-FLAGGED: it MUST NOT call a non-existent endpoint — export it returning a typed sentinel `{ unavailable: true }` page object (NOT a typed-empty list — the screen must distinguish "backend not available" from "available but no entries"), with a `// GAP: no backend ledger-history list endpoint as of 05A — see 05A-RESEARCH.md A4; do NOT add an endpoint or regenerate schema.d.ts` comment. Document the gap(s) in a top-of-file comment.
      - `query-keys.ts` — `billingKeys` as an `as const` factory with `all: ['billing']`, `balance()`, `ledger()`, `topupIntent(code: string)`.
      - `hooks/useBillingBalance.ts` — `useQuery` keyed on `billingKeys.balance()` with queryFn `getBillingBalance`, `refetchInterval: 45_000`, `refetchIntervalInBackground: false`, `staleTime: 30_000`.
      - `hooks/useCreateTopupIntent.ts` — bare `useMutation` with mutationFn `createTopupIntent`.
      - `hooks/useTopupCreditWatch.ts` — `useQuery` polling `getBillingBalance` with a `refetchInterval` callback that returns `false` once a passed-in `expiresAt` is past OR the balance has risen above a passed-in `baselineCredits`; expose the polled balance + `credited` + `expired` booleans. GAP NOTE in a comment: no intent-status endpoint, so "credited" is inferred from the balance rising (05A-RESEARCH.md A6).
      - `hooks/useLedgerHistory.ts` — GAP-FLAGGED stub: a `useInfiniteQuery`-shaped hook (shape per Context7 TanStack `useInfiniteQuery`) whose `queryFn` returns `getLedgerHistory()`'s `{ unavailable: true }` sentinel until a backend ledger-history endpoint exists; expose to callers a way to tell "unavailable" apart from "empty list" (e.g. the first page's `unavailable` flag). A top-of-file comment states the gap and that the screen must render the "not yet available" state, distinct from the empty state. Do NOT add a backend endpoint or regenerate `schema.d.ts`.
      - `messages.ts` — flat `Record<key, {vi,en}> as const` named `billingMessages`, namespace `billing.*`, seeded with the keys UI-SPEC section Copywriting requires (balance label, "Top up credits", ledger empty heading/body "No transactions yet", ledger-unavailable heading/body "Transaction history isn't available yet", top-up amount CTA, top-up waiting/success/expired copy; QR/code/amount/expiry instruction copy — NO bank-account-number/bank-name labels, since `TopupIntentResponse` carries none) — author vi + en in lock-step.
    Also create `apps/web/features/privacy/messages.ts` (`privacyMessages`, namespace `privacy.*`, seeded with the section-Copywriting privacy-page section headings/bodies + "What we never store" / "What Zero Mail can and can't do" / "Using your own AI key (BYOK)" + link-to-public-privacy label, vi + en lock-step) and `apps/web/features/shell/messages.ts` (`shellMessages`, namespaces `nav.*` + `shell.*`, seeded with nav labels Triage/Rules/Billing/Settings, pause toggle label + states + confirm copy, balance pill label, connection-health labels — vi + en lock-step). Do NOT touch `features/triage/messages.ts` in this plan (Plans 02/03 extend it).
    Run `pnpm --filter web i18n:build` locally so `i18n:check` can validate against the regenerated bundles, but do NOT commit the generated `i18n/messages/{vi,en}.json` here — Plan 06 owns the canonical commit. No UI components are written in this task, so no `frontend-design` invocation needed here.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/features/triage/query-keys.ts` exports `triageKeys` with `pauseState`, `auditLog`, `shadowMode`, `senderSafetyNet`.
    - `apps/web/features/billing/{api/billing-api.ts,query-keys.ts,hooks/useBillingBalance.ts,hooks/useCreateTopupIntent.ts,hooks/useTopupCreditWatch.ts,hooks/useLedgerHistory.ts,messages.ts}` all exist.
    - `billing-api.ts` calls only `/api/billing/balance` and `/api/billing/topup/intent` (no other `/api/billing/...` path string); a top-of-file comment documents the ledger-history / intent-status / bank-fields gaps.
    - `useBillingBalance.ts` sets `refetchInterval: 45_000`, `refetchIntervalInBackground: false`, `staleTime: 30_000`.
    - `useLedgerHistory.ts` and `getLedgerHistory` are gap-flagged, return a `{ unavailable: true }` sentinel (NOT a typed-empty list), reference 05A-RESEARCH.md A4, and call no non-existent endpoint; `apps/web/lib/api/schema.d.ts` is unchanged.
    - `features/billing/messages.ts` has NO bank-account-number / bank-name / account-holder-name labels (the topup response carries none); it does have a ledger-empty AND a ledger-unavailable copy pair.
    - `features/privacy/messages.ts` and `features/shell/messages.ts` exist with vi+en lock-step entries.
    - The generated `apps/web/i18n/messages/{vi,en}.json` are NOT committed by this plan.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
  </acceptance_criteria>
  <done>triageKeys + the complete billing feature skeleton + privacy/shell message bundles exist; gap stubs surface an explicit `unavailable` state; gates green; no backend endpoint added or schema regenerated; no committed i18n bundles.</done>
</task>

<task type="auto">
  <name>Task 3: Add Playwright spec stubs, Vitest hook specs, 320px viewport, and extend EN_SCAN_FILES (route-group (app)/ paths)</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (the canonical e2e harness: serial mode, `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me`, `fulfillJson`/`fulfillProblem`, session+locale cookies, golden path)
    - apps/web/e2e/mobile-topbar.spec.ts (the 320px viewport pattern: `page.setViewportSize({ width: 320, ... })`)
    - apps/web/playwright.config.ts (currently a single chromium Desktop project — check whether a mobile project exists)
    - apps/web/scripts/check-i18n.ts (the explicit `EN_SCAN_FILES` array; STRICT lint-staged gate)
    - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx (the Vitest harness: `vi.hoisted` mocks for the api module + `@tanstack/react-query`, `renderHook` + `act`)
    - 05A-VALIDATION.md section "Wave 0 Requirements" and section "Per-Task Verification Map" (the eight e2e spec names; the Vitest spec list — note `AuditLog.test.tsx`, `LedgerTable.test.tsx`, `SenderSafetyNetList.test.tsx`, `useToggleTriagePause.test.tsx` rewrite are owned by Plans 02/03/04, not here)
    - 05A-RESEARCH.md section "Validation Architecture" Test Map (which e2e spec covers which requirement)
  </read_first>
  <action>
    Create the eight Playwright spec files listed in 05A-VALIDATION.md as STUBS — each is a real Playwright spec with `test.describe.configure({ mode: 'serial' })` and at least one `test.skip(...)` so the suite still passes; a top-of-file comment names the owning plan: `e2e/app-shell.spec.ts` (Plan 02), `e2e/pause-toggle.spec.ts` (Plan 02), `e2e/billing-balance.spec.ts` (Plan 02 chrome + Plan 04), `e2e/connection-health.spec.ts` (Plan 02), `e2e/triage-audit.spec.ts` (Plan 03), `e2e/triage-shadow-senders.spec.ts` (Plan 03), `e2e/billing-topup.spec.ts` (Plan 04), `e2e/privacy-page.spec.ts` (Plan 05). Do NOT implement assertions here.
    Create Vitest specs `apps/web/features/billing/hooks/useBillingBalance.test.tsx` and `apps/web/features/billing/hooks/useTopupCreditWatch.test.tsx` as real tests against the Task-2 hooks: `useBillingBalance.test.tsx` asserts the hook is configured with `refetchInterval: 45000` / `staleTime: 30000` (mocking `useQuery` the way `useToggleTriagePause.test.tsx` mocks `useMutation`) AND — per review #8 — uses fake timers (`vi.useFakeTimers()`) to assert that a refetch actually fires after ~45s and is NOT swallowed by the global 5-min `staleTime` (advance time, assert the queryFn was re-invoked); `useTopupCreditWatch.test.tsx` asserts the `refetchInterval` callback returns `false` once `expiresAt` is past and once the balance rises above the baseline. (The `AuditLog.test.tsx`, `LedgerTable.test.tsx`, `SenderSafetyNetList.test.tsx`, and the `useToggleTriagePause.test.tsx` rewrite are owned by Plans 02/03/04.)
    In `apps/web/playwright.config.ts`: confirm a 320px viewport path exists; if not, add a `projects` entry `mobile-320` with `use: { viewport: { width: 320, height: 740 } }`, OR commit to the per-spec `page.setViewportSize({ width: 320 })` pattern from `mobile-topbar.spec.ts` and add a comment. Pick one approach and apply it consistently; record the choice in the SUMMARY.
    In `apps/web/scripts/check-i18n.ts`: extend `EN_SCAN_FILES` with every new Phase 5A English-literal-bearing file path, USING THE ROUTE-GROUP PATHS Plan 02 introduces: `app/(protected)/(app)/triage/page.tsx`, `app/(protected)/(app)/billing/page.tsx`, `app/(protected)/(app)/billing/top-up/page.tsx`, `app/(protected)/(app)/settings/privacy/page.tsx`, `components/shell/{AppShell,AppSidebar,ChromeHeader}.tsx`, all planned `features/triage/components/*.tsx` (`AuditLog`, `AuditTable`, `AuditCardList`, `AuditRow`, `UndoButton`, `ShadowModeCard`, `SenderSafetyNetList`, `SenderRow`), all planned `features/billing/components/*.tsx` (`BalanceCard`, `LedgerHistory`, `LedgerTable`, `TopupAmountForm`, `TopupInstructions`, `CopyableField`, `TopupSuccess`, `TopupExpired`), and `features/privacy/components/PrivacySections.tsx`. ALSO keep/migrate the existing converged-screen paths to their post-route-group locations: `app/(protected)/(app)/rules/page.tsx`, `app/(protected)/(app)/settings/page.tsx` (Plan 02 moves `rules` and `settings` under `(app)/`); leave `app/(protected)/onboarding/{gmail-connect,template-select,complete}/page.tsx` as-is (onboarding does NOT move). Add ALL of these paths NOW, unconditionally — `check-i18n.ts` already silently skips not-yet-existing paths (verify this once), so downstream plans (02–05) MUST NOT touch `EN_SCAN_FILES`. The list owned here is the single source of truth for which files the i18n gate scans across Phase 5A; if you discover a Phase 5A converged-screen file that gains a new English literal, add it here too. NOTE: if Plan 02's investigation of `node_modules/next/dist/docs/` concludes the route-group move is unsafe and falls back to keeping `rules`/`settings`/`triage`/`billing` directly under `(protected)/`, Plan 02 will flag it and Plan 06's closure does a final `EN_SCAN_FILES` reconciliation — but plan first for the `(app)/` paths.
  </action>
  <verify>
    <automated>cd apps/web && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- features/billing/hooks && pnpm test:e2e</automated>
  </verify>
  <acceptance_criteria>
    - All eight `apps/web/e2e/{app-shell,pause-toggle,billing-balance,connection-health,triage-audit,triage-shadow-senders,billing-topup,privacy-page}.spec.ts` exist; `pnpm --filter web test:e2e` exits 0.
    - `apps/web/features/billing/hooks/useBillingBalance.test.tsx` (incl. the fake-timer ~45s-refetch-actually-fires assertion) and `useTopupCreditWatch.test.tsx` exist and pass under `pnpm --filter web test`.
    - `apps/web/playwright.config.ts` has a documented 320px viewport approach; SUMMARY states which.
    - `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` is extended with every Phase 5A component/page path up front using the `(protected)/(app)/...` route-group paths for `triage`/`billing`/`settings`/`settings/privacy`/`rules`/`settings`, and leaves the onboarding paths under `(protected)/onboarding/...`; downstream plans do not edit it.
    - `cd apps/web && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
  </acceptance_criteria>
  <done>e2e + vitest stubs exist; 320px viewport path decided; EN_SCAN_FILES extended with every Phase 5A path using the route-group layout; all gates green.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | All new `features/billing` calls cross this boundary via the typed `openapi-fetch` client + server-issued session cookie + XSRF header. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-01 | Tampering/Spoofing | `features/billing/api/billing-api.ts` mutating call (`POST /api/billing/topup/intent`) | mitigate | Call goes through `lib/api/client.ts` which attaches `xsrfHeader()`; no raw cross-origin `fetch`; verified by the typed-client-only convention. |
| T-05A-02 | Information disclosure | gap-flagged `getLedgerHistory` / `useLedgerHistory` stubs | accept | Stubs return an `{ unavailable: true }` sentinel and call no endpoint; no PII rendered or logged; the real ledger surface is gap-deferred. |
| T-05A-03 | Information disclosure | i18n message bundles | accept | Static UI copy only — no tenant data, no email content, no tokens. |

No high-severity threats — this plan is scaffolding only; all backend access goes through the typed client; no rendered backend strings, no use of the dangerously-set-inner-HTML React prop, no qrPayload rendering yet.
</threat_model>

<verification>
- `pnpm --filter web i18n:build` is run as part of the gate but the generated `i18n/messages/{vi,en}.json` are NOT in this plan's `files_modified` and must not be committed here — Plan 06 regenerates and commits the canonical bundles. The per-feature `messages.ts` files (which ARE owned here) are the source of truth. This plan's `<verify>` asserts only that `i18n:check` passes against `messages.ts`, never that a committed bundle is current.
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0 after the plan.
- `apps/web/lib/api/schema.d.ts` is byte-identical to its pre-plan state (no backend regeneration).
- `apps/web/package.json` / `apps/web/pnpm-lock.yaml` change ONLY if `pnpm dlx shadcn add` added a runtime dep — otherwise unchanged.
</verification>

<success_criteria>
- Missing shadcn primitives installed; `components/states/*` trio exists; `features/triage/query-keys.ts` + the full `features/billing/` skeleton (with `{unavailable:true}` gap stubs) exist; eight e2e spec stubs + the two billing-hook Vitest specs exist; 320px viewport path chosen; `EN_SCAN_FILES` extended with the route-group layout; all frontend gates green; no backend endpoint added or schema regenerated; no committed i18n bundles.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-01-SUMMARY.md` (record: `SIDEBAR_COOKIE_NAME` value; whether `pnpm dlx shadcn add` touched `package.json`/lockfile; the chosen 320px viewport approach; confirmation that `EN_SCAN_FILES` now lists every Phase 5A path with the `(protected)/(app)/...` route-group layout so downstream plans don't touch it; the `frontend-design` note for the states trio).
</output>
