---
phase: 05A-user-surface-web-ui-core
plan: 03
type: execute
wave: 3
depends_on: [01, 02]
files_modified:
  - apps/web/app/(protected)/(app)/triage/page.tsx
  - apps/web/features/triage/components/TriagePageClient.tsx
  - apps/web/features/triage/api/triage-api.ts
  - apps/web/features/triage/hooks/useTriageAuditLog.ts
  - apps/web/features/triage/hooks/useUndoAuditEntry.ts
  - apps/web/features/triage/hooks/useShadowMode.ts
  - apps/web/features/triage/hooks/useProtectedSenders.ts
  - apps/web/features/triage/hooks/useOptInSender.ts
  - apps/web/features/triage/components/AuditLog.tsx
  - apps/web/features/triage/components/AuditTable.tsx
  - apps/web/features/triage/components/AuditCardList.tsx
  - apps/web/features/triage/components/AuditRow.tsx
  - apps/web/features/triage/components/UndoButton.tsx
  - apps/web/features/triage/components/ShadowModeCard.tsx
  - apps/web/features/triage/components/SenderSafetyNetList.tsx
  - apps/web/features/triage/components/SenderRow.tsx
  - apps/web/features/triage/components/AuditLog.test.tsx
  - apps/web/features/triage/components/SenderSafetyNetList.test.tsx
  - apps/web/features/triage/messages.ts
  - apps/web/e2e/triage-audit.spec.ts
  - apps/web/e2e/triage-shadow-senders.spec.ts
autonomous: true
requirements: [WEB-01, WEB-02]
user_setup: []

must_haves:
  truths:
    - "A single /triage page (at app/(protected)/(app)/triage/page.tsx) renders inside the app shell with shadcn Tabs for Audit log / Shadow mode / Sender safety net, the active tab synced to a ?tab= searchParam so each tab is deep-linkable; shadow mode is page-level state, not a peer section (D-06)"
    - "The audit log is a responsive hybrid renderer — shadcn Table at >=md / card list below md sharing one row model — and renders correctly at 0 entries (empty state), 1 entry, and a page-full of entries; AuditLog accepts an OPTIONAL injected entries prop / hook seam so component tests can render populated rows without a real endpoint; the Reason field is never truncated into invisibility (it is the trust evidence) and shows in full on the card variant (D-16)"
    - "Pagination is cursor 'Load older entries' via useInfiniteQuery (the backend list would be cursor-paginated / append-only) — not numbered pages — with a subtle divider where entries cross the 30-day undo boundary; the divider position (between the last in-window entry and the first out-of-window entry) is asserted by a component test (D-17)"
    - "An entry within the 30-day undo window offers an outline Undo button -> an AlertDialog confirm naming the exact inverse Gmail change before POST /api/triage/audit/{auditId}/undo -> on success invalidates the audit + balance queries and the COMPONENT (not the hook) fires the toast; an entry past 30 days shows a muted non-interactive 'Undo window closed' label with a tooltip ('Triage actions can be undone for up to 30 days') — never hidden (D-18)"
    - "A tenant-wide shadow-mode toggle reads/writes /api/tenant/triage/shadow-mode and persists"
    - "The sender-safety-net list renders (including an empty state) and opting a sender in calls /api/triage/sender-safety-net/{senderEmail}/opt-in and updates the row"
    - "The triage-audit list endpoint does not exist on the backend; getAuditLog / useTriageAuditLog surface an explicit `{ unavailable: true }`-style state distinct from 'empty list'; the AuditLog renders an 'audit history not yet available' panel for that state (distinct from the 'no entries yet' empty panel); the e2e covers the REAL production state (the 'not yet available' panel), and the populated-rows path is covered by AuditLog.test.tsx with injected data; no backend endpoint is added and schema.d.ts is unchanged"
  artifacts:
    - path: "apps/web/app/(protected)/(app)/triage/page.tsx"
      provides: "Thin page: <Suspense> -> TriagePageClient (single /triage page + ?tab= reader, D-06)"
    - path: "apps/web/features/triage/components/TriagePageClient.tsx"
      provides: "shadcn Tabs driven by ?tab= searchParam (audit/shadow/senders), router.replace on change — single deep-linkable triage page (D-06)"
    - path: "apps/web/features/triage/components/AuditLog.tsx"
      provides: "Responsive audit renderer (Table >=md / card list <md sharing one row model), 30-day boundary divider, cursor 'Load older entries', undo affordances, never-clipped Reason, optional injected-entries seam for tests, distinct 'not yet available' state for the missing backend endpoint (D-16, D-17)"
    - path: "apps/web/features/triage/components/UndoButton.tsx"
      provides: "Outline Undo button -> AlertDialog naming the inverse Gmail change -> POST undo; toast fired by the component on success; muted 'Undo window closed' + tooltip past 30 days (D-18)"
    - path: "apps/web/features/triage/hooks/useTriageAuditLog.ts"
      provides: "useInfiniteQuery cursor pagination over the (gap-flagged, `{unavailable:true}`) audit list — initialPageParam/getNextPageParam (D-17)"
    - path: "apps/web/features/triage/components/ShadowModeCard.tsx"
      provides: "Shadow-mode toggle reading/writing /api/tenant/triage/shadow-mode with turn-off confirm"
    - path: "apps/web/features/triage/components/SenderSafetyNetList.tsx"
      provides: "Sender safety-net list with per-row opt-in and empty state"
  key_links:
    - from: "apps/web/features/triage/hooks/useUndoAuditEntry.ts"
      to: "/api/triage/audit/{auditId}/undo"
      via: "useMutation -> undoAuditEntry; onSuccess invalidates triageKeys.auditLog() + billingKeys.balance() (toast fired by the component)"
      pattern: "triage/audit"
    - from: "apps/web/features/triage/components/AuditCardList.tsx"
      to: "30-day undo boundary"
      via: "boundary divider between straddling entries; full-text Reason on every card (D-16, D-17)"
      pattern: "30"
    - from: "apps/web/features/triage/components/SenderSafetyNetList.tsx"
      to: "/api/triage/sender-safety-net/{senderEmail}/opt-in"
      via: "useOptInSender"
      pattern: "sender-safety-net"
    - from: "apps/web/features/triage/components/TriagePageClient.tsx"
      to: "?tab= searchParam"
      via: "useSearchParams + router.replace (deep-linkable tabs, D-06)"
      pattern: "useSearchParams"
---

<objective>
Build the `/triage` surface (page at `app/(protected)/(app)/triage/page.tsx`, inside the app shell): a single page with shadcn `Tabs` for Audit log / Shadow mode / Sender safety net, the active tab synced to a `?tab=` searchParam for deep-linking (D-06). Implement the audit log + undo (responsive Table/card hybrid renderer with an optional injected-entries seam for tests, 30-day boundary, AlertDialog confirm naming the inverse Gmail change, toast fired by the component — D-16/D-17/D-18), the tenant-wide shadow-mode toggle (D-06), and the sender-safety-net list with per-row opt-in. The triage-audit *list* endpoint does not exist on the backend (confirmed against `TriageAuditController`) — `getAuditLog`/`useTriageAuditLog` surface an explicit `{ unavailable: true }` state distinct from an empty list, the `AuditLog` renders an "audit history not yet available" panel for that state, the e2e covers that real production state, and the populated-rows rendering path is covered by `AuditLog.test.tsx` with injected fixture data (Playwright network mocks can't exercise populated rows through a stub that calls no endpoint). Do not add a backend endpoint or regenerate `schema.d.ts`.

Purpose: WEB-02 (the triage-audit-log-with-undo + shadow-mode + sender-safety-net portions — note WEB-02 stays partial after 5A; draft-review is 5B, analytics is 5C, and the real audit-list endpoint remains backend work).
Output: `/triage` page + `TriagePageClient`, extended `triage-api.ts`, the audit/shadow/sender hooks, the audit/shadow/sender components, `AuditLog`/`SenderSafetyNetList` Vitest specs (with injected data), the `triage-audit` and `triage-shadow-senders` Playwright specs, extended `triage` i18n.
</objective>

<reviewer_response>
Cross-AI review:
- #1 (Codex HIGH — gap-stub vs. mocked-e2e mismatch): (a) populated-audit-rows coverage moved to a Vitest component test (`AuditLog.test.tsx`) that renders `AuditLog` with INJECTED fixture data via an optional prop / hook seam; (b) the Playwright e2e (`triage-audit.spec.ts`) for the audit feature now targets the REAL production state — the "audit history not yet available" panel — not mocked populated rows; (c) `getAuditLog`/`useTriageAuditLog` surface an explicit `{ unavailable: true }` state distinct from "empty list" so the UI renders "not yet available" vs. "no entries yet" differently; (d) 05A-VALIDATION.md's WEB-02 audit row is updated to reflect this split.
- #9 (Codex MEDIUM — toast i18n friction): `useUndoAuditEntry` only mutates the cache / returns status; the success/error toast is fired in `UndoButton`/`AuditRow` (the component, where `useTranslations` is available) — stated explicitly in the action.
- #8 (OpenCode MEDIUM): explicit acceptance criteria added — the 30-day boundary divider position (component test); Reason never clipped on the card variant (component test).
- Note: the `/triage` shell-presence smoke check that Plan 02 used to own now lives in this plan's `triage-audit.spec.ts` (assert the sidebar + chrome render on `/triage`).
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

<task type="auto">
  <name>Task 1: Extend triage-api.ts + add the audit/undo/shadow/sender hooks (audit list gap-flagged, `{unavailable:true}` sentinel)</name>
  <read_first>
    - apps/web/features/rules/api/rules-api.ts (`unwrap`/`jsonHeaders`/`unsafeHeaders` — copy verbatim into triage-api.ts)
    - apps/web/features/triage/api/triage-api.ts (current `setTriagePaused` only — extend)
    - apps/web/lib/api/client.ts, apps/web/lib/api/schema.d.ts (grep the exact path strings: `/api/tenant/triage/shadow-mode` (GET+PUT), `/api/triage/audit/{auditId}/undo` (POST), `/api/triage/sender-safety-net` (GET), `/api/triage/sender-safety-net/{senderEmail}/opt-in` (POST); confirm the response component shapes; confirm NO audit-list path exists)
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java + the SenderSafetyNetController / shadow-mode controller in backend/api (confirm exact contract; the audit-list endpoint is absent)
    - apps/web/features/rules/hooks/use-rules.ts -> `useDeleteRule` / `useUpdateRuleEnabled` (mutation + invalidate-on-success), `useRules` (list query)
    - apps/web/features/llm/hooks/use-byok.ts (read+write pair idiom for useShadowMode)
    - apps/web/features/triage/query-keys.ts (the `triageKeys` factory from Plan 01), apps/web/features/billing/query-keys.ts, apps/web/features/billing/api/billing-api.ts (the `{unavailable:true}` sentinel pattern from Plan 01's `getLedgerHistory` — mirror it for `getAuditLog`), apps/web/components/ui/sonner.tsx (toast — used in components, not hooks)
    - node_modules/@tanstack/react-query (`useInfiniteQuery` API: `initialPageParam`, `getNextPageParam`) — there is no `useInfiniteQuery` analog in the repo
    - 05A-CONTEXT.md D-13 (single pause key — do not touch it here), D-16, D-17, D-18; 05A-PATTERNS.md sections "features/triage/api/triage-api.ts (extend)", "useUndoAuditEntry / useOptInSender / useShadowMode mutation", "useTriageAuditLog / useLedgerHistory (BLOCKED)"
    - 05A-RESEARCH.md A4 (audit-list absent — confirmed), Open Question 1 + Open Question 4 (what identifiers an audit entry exposes — unknown; the row model binds to whatever the mocked/eventual shape provides; if no Gmail message id, text only)
  </read_first>
  <action>
    Extend `apps/web/features/triage/api/triage-api.ts` (mirror `rules-api.ts`'s `unwrap`): add `getShadowMode()` -> GET `/api/tenant/triage/shadow-mode`, `setShadowMode(enabled: boolean)` -> PUT `/api/tenant/triage/shadow-mode` (jsonHeaders), `undoAuditEntry(auditId: string)` -> POST `/api/triage/audit/{auditId}/undo` (unsafeHeaders, typed path param — pass the id through the openapi-fetch typed `params.path`, NEVER string-interpolate it into the URL), `getProtectedSenders()` -> GET `/api/triage/sender-safety-net`, `optInSender(senderEmail: string)` -> POST `/api/triage/sender-safety-net/{senderEmail}/opt-in` (unsafeHeaders, typed path param — same: encode through `params.path`, never interpolate). Add a GAP-FLAGGED `getAuditLog(...)` with a comment: `// GAP: no backend triage-audit list endpoint as of 05A — see 05A-RESEARCH.md A4 / 05A-SPEC.md out-of-scope; do NOT add an endpoint or regenerate schema.d.ts`. Define a local TypeScript `AuditEntry` row-model interface for the fields the UI consumes (id, timestamp, action, ruleName, reason, an optional message-ref { subject, sender, optional gmailMessageId }, an `undoableUntil`/`undoable` indicator); `getAuditLog` is implemented as a stub that resolves to a typed `{ unavailable: true }` sentinel page (NOT a typed-empty list) — the screen distinguishes "not yet available" from "no entries". Document this in the SUMMARY as a flagged gap.
    Create the hooks:
      - `hooks/useTriageAuditLog.ts` — `useInfiniteQuery` (shape per Context7 TanStack: `queryKey: triageKeys.auditLog()`, `queryFn` -> `getAuditLog`, `initialPageParam`, `getNextPageParam`). Expose to callers a way to tell the `{unavailable:true}` first page apart from an empty list. GAP comment at top.
      - `hooks/useUndoAuditEntry.ts` — `useMutation({ mutationFn: undoAuditEntry, onSuccess: () => { invalidate triageKeys.auditLog(); invalidate billingKeys.balance(); } })` (D-18). The hook does NOT call `toast(...)` — the component fires the toast on success/error (review #9).
      - `hooks/useShadowMode.ts` — a read+write pair like `use-byok.ts`: `useQuery({ queryKey: triageKeys.shadowMode(), queryFn: getShadowMode })` + `useMutation({ mutationFn: setShadowMode, onSuccess: () => invalidate triageKeys.shadowMode() })`.
      - `hooks/useProtectedSenders.ts` — `useQuery({ queryKey: triageKeys.senderSafetyNet(), queryFn: getProtectedSenders })` (matches `useRules`).
      - `hooks/useOptInSender.ts` — `useMutation({ mutationFn: optInSender, onSuccess: () => invalidate triageKeys.senderSafetyNet() })` (matches `useUpdateRuleEnabled`).
    Do NOT edit `apps/web/scripts/check-i18n.ts` — Plan 01 already registered every Phase 5A triage component/page path (using the `(app)/` route-group path) in `EN_SCAN_FILES`. No UI components in this task — no `frontend-design` invocation needed here.
  </action>
  <verify>
    <automated>cd apps/web && pnpm typecheck && pnpm lint && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/features/triage/api/triage-api.ts` exports `getShadowMode`, `setShadowMode`, `undoAuditEntry`, `getProtectedSenders`, `optInSender`, plus a gap-flagged `getAuditLog` and an exported `AuditEntry` row-model type; calls only the five real triage paths (no audit-list path string); path params for `undoAuditEntry`/`optInSender` go through the typed `params.path`, never string interpolation.
    - `getAuditLog` returns a typed `{ unavailable: true }` sentinel page (not a typed-empty list).
    - `apps/web/features/triage/hooks/{useTriageAuditLog,useUndoAuditEntry,useShadowMode,useProtectedSenders,useOptInSender}.ts` all exist; `useUndoAuditEntry.onSuccess` invalidates `triageKeys.auditLog()` AND `billingKeys.balance()` and does NOT call `toast`; `useTriageAuditLog.ts` carries a comment referencing 05A-RESEARCH.md A4 and exposes the `unavailable`-vs-empty distinction.
    - `apps/web/lib/api/schema.d.ts` is unchanged.
    - `cd apps/web && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
  </acceptance_criteria>
  <done>Triage api + hooks exist; audit-list gap flagged with a `{unavailable:true}` sentinel; toast deferred to components; gates green; no backend endpoint added.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Build the /triage page + Tabs + audit hybrid renderer (with injected-data seam) + shadow-mode card + sender-safety-net list</name>
  <behavior>
    - AuditLog: accepts an OPTIONAL `entries`/`pages` prop (or a hook-injection seam) — when not provided it uses `useTriageAuditLog`; given the `{unavailable:true}` state -> renders the "audit history not yet available" panel (a distinct variant, NOT the empty panel); given 0 entries -> renders the EmptyState ("No triage activity yet" / UI-SPEC body); given 1 entry -> renders one row/card; given a page-full -> renders all + a "Load older entries" affordance (useInfiniteQuery `fetchNextPage`); renders a 30-day boundary divider BETWEEN the last in-window entry and the first out-of-window entry; at >=md uses the Table renderer, below md uses the card renderer with the Reason shown IN FULL. (test: AuditLog.test.tsx — render with INJECTED 0/1/N-entry fixtures (no real endpoint) AND with the `{unavailable:true}` state; assert: the "not yet available" panel for the unavailable state; the empty state for 0; row count for 1/N; the boundary divider sits between the last in-window and first out-of-window entry; Reason is rendered in full (not clipped) on the card variant.)
    - UndoButton/AuditRow: an entry inside the 30-day window shows an outline "Undo" button -> clicking opens an AlertDialog whose body names the inverse Gmail change verbatim from the backend's computed inverse-action string -> confirm calls useUndoAuditEntry.mutate(auditId) -> on success the row's undo affordance is removed AND the COMPONENT fires a `sonner` toast (translated via `useTranslations`); an entry past 30 days shows a muted, non-interactive "Undo window closed" label with a tooltip "Triage actions can be undone for up to 30 days" (never hidden).
    - ShadowModeCard: shows the current shadow-mode state from useShadowMode; turning shadow mode ON does not confirm; turning it OFF opens a light confirm ("Turn off shadow mode?" / "Turn off shadow mode" / "Keep shadow mode on"); when ON, an info/blue "Shadow mode on" badge is visible.
    - SenderSafetyNetList: given 0 senders -> EmptyState ("No protected senders yet" / UI-SPEC body); given senders -> a row per sender with an opt-in control; opting a sender in calls useOptInSender.mutate(senderEmail) and the row reflects the new opted-in state. (test: SenderSafetyNetList.test.tsx — empty state + populated list + opt-in click invokes the mutation.)
    - TriagePageClient: reads useSearchParams().get('tab'), drives shadcn Tabs, on change calls router.replace('/triage?tab='+value, { scroll:false }); default tab when ?tab= absent or invalid = 'audit'.
  </behavior>
  <read_first>
    - apps/web/app/(protected)/(app)/rules/page.tsx (thin page -> feature workspace idiom: `<main className="mx-auto w-full max-w-6xl p-4 md:p-6">...</main>` — note rules is now under (app)/ per Plan 02)
    - apps/web/features/rules/components/RuleList.tsx (the closest "list of records, each with badges + per-row actions + a Dialog confirm"; `<Badge variant=...>`, `<TooltipProvider>`/`<Tooltip>`, the Dialog-confirm idiom — for 5A use `alert-dialog` instead per UI-SPEC; also the inline loading/empty trio now superseded by `@/components/states/*`)
    - apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx (Plan 01 — use these for all list states; the "not yet available" panel is a distinct variant/composition, not the EmptyState)
    - apps/web/components/ui/{tabs,table,alert-dialog,switch,badge,tooltip,sonner}.tsx
    - apps/web/app/(protected)/(app)/settings/page.tsx ("Automated triage" Card with a toggle — the layout idiom for ShadowModeCard)
    - apps/web/features/triage/hooks/{useTriageAuditLog,useUndoAuditEntry,useShadowMode,useProtectedSenders,useOptInSender}.ts (Task 1), apps/web/features/triage/api/triage-api.ts (the `AuditEntry` row-model type)
    - apps/web/features/triage/messages.ts (extend with `triage.*` keys), apps/web/features/triage/hooks/useToggleTriagePause.test.tsx (the Vitest harness idiom for the new component tests), apps/web/features/gmail/components/ReconnectPrompt.tsx (note the "plain DOM <button> instead of shadcn Button in tests" workaround comment — relevant if a control needs unit testing)
    - 05A-CONTEXT.md D-06, D-16, D-17, D-18; 05A-UI-SPEC.md sections Copywriting (audit empty state, undo confirm dialog naming the inverse change, "Undo window closed", 30-day boundary marker, shadow-mode label/states/confirm, sender empty state, "Remove sender" destructive confirm, primary CTAs, Display type usage), Color (action Badge color map, shadow-mode info-blue badge, status discipline), Typography (12/14/20/28; mono for timestamps), Spacing (dense Table py-2 at >=md, lg card padding below md, 40/44px touch targets), Responsive (>=md Table / <md card list / 320px card + Tabs may scroll horizontally, Reason full text), Visual Hierarchy (the audit Table/card list is the focal element of /triage)
    - 05A-PATTERNS.md sections "features/triage/components/{AuditTable,AuditCardList,AuditRow,UndoButton}.tsx", "ShadowModeCard.tsx", "{SenderSafetyNetList,SenderRow}.tsx", "app/(protected)/triage/page.tsx & billing/top-up/page.tsx (Suspense + search-param reader)" (note the page now lives under `(app)/`)
    - 05A-RESEARCH.md Pattern 4 (`?tab=` under `<Suspense>`), Pitfall 2 (useSearchParams needs `<Suspense>` in Next 16 — verify in node_modules/next/dist/docs/), Pitfall 6 (privacy: render only fields the backend returns; no console.log of audit data; link to Gmail only if a message id is present)
    - node_modules/next/dist/docs/ — `useSearchParams` + `<Suspense>` requirement in Next 16 (read before writing this code)
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing any of these components; record `frontend-design` visual-review notes (desktop + 320px, light + dark) for: the `/triage` page (each tab), the audit Table renderer, the audit card renderer, the shadow-mode card, and the sender-safety-net list — in the SUMMARY.
    Create `app/(protected)/(app)/triage/page.tsx` — a thin page mirroring `(app)/rules/page.tsx`: `export default function TriagePage() { return <Suspense fallback={<LoadingState/>}><TriagePageClient/></Suspense>; }`. Create `features/triage/components/TriagePageClient.tsx` (`"use client"`): reads `useSearchParams().get('tab')` (default `'audit'` when absent/invalid), drives a shadcn `<Tabs value={tab} onValueChange={(v) => router.replace('/triage?tab='+v, { scroll:false })}>` with three `TabsTrigger`/`TabsContent`: Audit log (`<AuditLog/>`), Shadow mode (`<ShadowModeCard/>`), Sender safety net (`<SenderSafetyNetList/>`). The audit Table/card list is the page's focal element (UI-SPEC Visual Hierarchy).
    Create `features/triage/components/AuditLog.tsx` — accepts an OPTIONAL injected-entries prop (or hook seam) for tests; when not injected it uses `useTriageAuditLog`. While loading -> `<LoadingState variant="rows"/>`; on error -> `<ErrorState heading body onRetry={refetch}/>` (copy from UI-SPEC); on the `{unavailable:true}` state -> a CLEARLY-WORDED "audit history not yet available" panel (a distinct variant/composition — not the EmptyState — copy from UI-SPEC; flag this state in the SUMMARY as the documented degradation path for the missing backend endpoint); on 0 entries -> `<EmptyState heading body/>` (UI-SPEC audit copy); otherwise picks the renderer responsively (`AuditTable` at >=md, `AuditCardList` below md — a CSS/`hidden md:block` switch or a media-query hook, your call) sharing one `AuditRow` row model; render a mono+muted full-width "Older than 30 days — undo no longer available" divider between the last in-window entry and the first out-of-window entry (D-17); a "Load older entries" button calling `fetchNextPage` (D-17, cursor pagination — NOT numbered pages).
    Create `features/triage/components/{AuditTable,AuditCardList,AuditRow,UndoButton}.tsx`: `AuditTable` = shadcn `Table` with columns Date/time (mono), Message ref (subject + sender, truncated, link to Gmail only if `gmailMessageId` present — privacy default text-only otherwise), Rule, Action (`Badge` with the UI-SPEC color map), Reason (truncated-with-expand), Undo; dense `py-2` rows. `AuditCardList` = one card per entry, header = Action `Badge` + timestamp, body = message ref + rule + **full** Reason text, footer = `<UndoButton/>` or the muted "Undo window closed" label. `AuditRow` = the shared row-model + truncation/undo-eligibility logic in ONE place. `UndoButton` = outline `Button` "Undo" -> `alert-dialog` confirm whose body renders the backend's computed inverse-action string verbatim (UI-SPEC copy "Undo this triage action?" / confirm "Undo this action" accent / cancel "Keep it"); confirm -> `useUndoAuditEntry().mutate(auditId)`; on success the affordance is removed AND the COMPONENT fires a `sonner` toast "Undone — ..." translated via `useTranslations`. Past 30 days: a muted non-interactive label "Undo window closed" + a `tooltip` "Triage actions can be undone for up to 30 days" (never hidden — D-18).
    Create `features/triage/components/ShadowModeCard.tsx` — a Card (idiom from `(app)/settings/page.tsx`) with a shadcn `switch` bound to `useShadowMode`; ON does not confirm; OFF opens an `alert-dialog` light confirm (UI-SPEC "Turn off shadow mode?" / "Turn off shadow mode" / "Keep shadow mode on"); when ON show an info/blue `Badge` "Shadow mode on" and the UI-SPEC ON helper text; loading -> `<LoadingState/>`; error -> `<ErrorState onRetry/>`.
    Create `features/triage/components/{SenderSafetyNetList,SenderRow}.tsx` — `SenderSafetyNetList` uses `useProtectedSenders`; loading -> `<LoadingState/>`; error -> `<ErrorState onRetry/>`; 0 -> `<EmptyState/>` (UI-SPEC sender copy); otherwise a `SenderRow` per sender (sender email shown — owner-visible; an opt-in control: `Button`/`switch` calling `useOptInSender().mutate(senderEmail)`; the row reflects the new opted-in state; a "Remove sender" destructive `alert-dialog` if the remove action is exposed by the endpoint — UI-SPEC copy "Stop protecting {email}?" / "Remove sender" destructive). Render only fields the backend returns; no `console.log` of sender/audit data.
    Create the Vitest specs `apps/web/features/triage/components/AuditLog.test.tsx` and `SenderSafetyNetList.test.tsx` per the behavior block (mock the hooks like `useToggleTriagePause.test.tsx` mocks `@tanstack/react-query`, OR — preferred for AuditLog — render `AuditLog` with the optional injected-entries prop so no endpoint is needed; assert: the "not yet available" panel for the unavailable state; the empty state for 0; row count for 1/N; the boundary divider sits between the last in-window and first out-of-window entry; Reason rendered in full on the card variant; for senders: empty + populated + opt-in invokes the mutation). Extend `apps/web/features/triage/messages.ts` with all new `triage.*` keys (vi + en lock-step), and run `pnpm --filter web i18n:build` locally (do NOT edit `EN_SCAN_FILES` and do NOT commit the generated bundles — Plan 01 owns the list, Plan 06 owns the bundle commit).
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- features/triage/components</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/app/(protected)/(app)/triage/page.tsx` renders `<Suspense>` around `TriagePageClient`; `TriagePageClient.tsx` reads `useSearchParams().get('tab')`, drives shadcn `Tabs` with three tabs, and calls `router.replace('/triage?tab='+v, { scroll:false })` on change; default tab = `'audit'`.
    - `AuditLog.tsx` accepts an optional injected-entries prop/seam; renders the "audit history not yet available" panel for the `{unavailable:true}` state (distinct from the empty panel), `EmptyState` at 0 entries, the responsive Table/card renderer otherwise, the 30-day boundary divider between the last in-window and first out-of-window entry, and a "Load older entries" affordance.
    - `UndoButton`/`AuditRow` show an "Undo" button -> `alert-dialog` confirm (body = backend inverse-action string) -> `useUndoAuditEntry` on confirm; on success the COMPONENT fires a translated toast; a muted "Undo window closed" label + tooltip for out-of-window entries (never hidden).
    - `ShadowModeCard.tsx` toggles `useShadowMode` with no confirm on ON and an `alert-dialog` confirm on OFF; an info-blue "Shadow mode on" badge when ON.
    - `SenderSafetyNetList.tsx` renders `EmptyState` at 0 senders and a `SenderRow` per sender; opt-in invokes `useOptInSender`.
    - `apps/web/features/triage/components/{AuditLog,SenderSafetyNetList}.test.tsx` exist and pass; `AuditLog.test.tsx` covers populated rows via injected data (no real endpoint), the `{unavailable:true}` panel, the empty state, the boundary-divider position, and the full-Reason-on-card check.
    - No hardcoded English literals in the new `features/triage/components/*` or `app/(protected)/(app)/triage/page.tsx` (via `pnpm --filter web i18n:check`); all strings resolve from `triage.*`.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review notes and the documented audit-list degradation path ("audit history not yet available" panel, populated path tested with injected data).
  </acceptance_criteria>
  <done>/triage page + Tabs + audit hybrid (with injected-data seam) + shadow-mode + sender list built; tests pass (populated rows via injected data); gates green; visual reviews recorded; audit-list gap degraded to a distinct "not yet available" panel.</done>
</task>

<task type="auto">
  <name>Task 3: Implement the triage-audit and triage-shadow-senders Playwright specs</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (serial mode; `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me`; `fulfillJson`/`fulfillProblem`; session+locale cookies; horizontal-overflow check)
    - apps/web/e2e/mobile-topbar.spec.ts (320px viewport pattern); apps/web/playwright.config.ts (the 320px approach from 05A-01-SUMMARY)
    - apps/web/e2e/{triage-audit,triage-shadow-senders}.spec.ts (the Plan 01 stubs to fill in)
    - 05A-VALIDATION.md section "Per-Task Verification Map" rows for "Triage audit + undo" and "Shadow mode + sender net"
    - 05A-RESEARCH.md section "Validation Architecture" Test Map — note the audit-list e2e covers the REAL production "not yet available" state (no real endpoint; the populated path is a Vitest component test); the backend gap is flagged in a spec comment
    - apps/web/features/triage/api/triage-api.ts (the real endpoint paths for shadow-mode + sender-safety-net + undo)
  </read_first>
  <action>
    Fill in the two Plan-01 stubs using the `e2e/rules.spec.ts` harness (serial mode, in-memory mock keyed on pathname+method, always mock `/me`, session+locale cookies before `goto`, `waitForLoadState('networkidle')`):
      - `e2e/triage-audit.spec.ts`: a top-of-file comment flags that the triage-audit *list* endpoint does not exist on the backend, so this e2e covers the REAL production state (the "audit history not yet available" panel) — the populated-rows path is covered by `AuditLog.test.tsx` with injected data (05A-RESEARCH.md A4). Cases: (1) navigate to `/triage` — assert the app shell (sidebar + chrome region) renders on this page (the shell-presence smoke check that used to live in Plan 02); (2) assert the "audit history not yet available" panel renders on the Audit log tab (at 1280px AND 320px); (3) `?tab=` deep-links — `/triage?tab=shadow` selects the shadow tab on initial load; (4) the shadow-mode and sender-safety-net tabs are still exercised in `triage-shadow-senders.spec.ts`. Run at 1280px and 320px (no horizontal scroll).
      - `e2e/triage-shadow-senders.spec.ts`: navigate to `/triage?tab=shadow`. (1) toggle shadow mode OFF -> the light confirm appears -> confirm -> a `PUT /api/tenant/triage/shadow-mode` is sent and the UI reflects the new state; toggle ON -> no confirm; the info-blue "Shadow mode on" badge shows when ON. (2) navigate to `/triage?tab=senders`: with the sender list mocked empty -> the "No protected senders yet" empty state renders; with senders mocked -> rows render; click opt-in on a sender -> a `POST /api/triage/sender-safety-net/{senderEmail}/opt-in` is sent and the row reflects opted-in. Also assert the `?tab=` deep-link selects the right tab on initial load. Run at 1280px and 320px.
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- triage-audit triage-shadow-senders</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/{triage-audit,triage-shadow-senders}.spec.ts` contain real (non-skipped) assertions covering the behaviors above at 1280px and 320px.
    - `e2e/triage-audit.spec.ts` has a top-of-file comment flagging the absent audit-list endpoint and that it covers the production "not yet available" state; it asserts the app shell renders on `/triage`, the "not yet available" panel on the audit tab, and `?tab=` deep-linking.
    - `e2e/triage-shadow-senders.spec.ts` asserts a `PUT /api/tenant/triage/shadow-mode` (with the turn-off confirm), a `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`, the sender empty state, and `?tab=` deep-linking.
    - `pnpm --filter web test:e2e` passes (including these two specs).
  </acceptance_criteria>
  <done>Triage behaviors covered by passing Playwright specs at desktop + 320px; the audit e2e targets the production "not yet available" state and flags the gap; the shell-presence check for `/triage` lives here.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | Audit-undo, shadow-mode read/write, sender-safety-net read + opt-in cross here via the typed `openapi-fetch` client + session cookie + XSRF header. |
| backend response strings → React render | Audit entry fields (subject, sender, rule name, inverse-action reason text), sender emails are rendered on `/triage`. |
| URL searchParams → app state | `?tab=` is read by `TriagePageClient`. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-08 | Tampering / CSRF | `undoAuditEntry` / `setShadowMode` / `optInSender` mutating calls | mitigate | All go through `lib/api/client.ts` with `xsrfHeader()`; no raw cross-origin `fetch`; the undo + shadow + opt-in e2e specs assert the request method/path; path params go through the typed `params.path`, not string interpolation. |
| T-05A-09 | Information disclosure | audit entries / sender emails rendered on `/triage` | mitigate | Render only fields the backend explicitly returns as owner-visible (subject/sender truncated text, rule name, the backend-computed inverse-action string, the sender email); no email body; link to Gmail only if a `gmailMessageId` is present; no `console.log` of audit/sender data (Pitfall 6). |
| T-05A-10 | XSS via rendered backend strings | audit subject/sender/reason, sender email | mitigate | All rendered as React text children — auto-escaped; no dangerously-set-inner-HTML React prop; no HTML interpolation of any backend string. |
| T-05A-11 | Open redirect / injection via `?tab=` | `TriagePageClient` reading `useSearchParams().get('tab')` | mitigate | `?tab=` is validated against the fixed allow-list {`audit`,`shadow`,`senders`}; any other value falls back to `'audit'`; it is never used to build a URL or HTML. |

No high-severity threats — frontend-only; all backend access via the typed client; all rendered backend strings React-escaped; `?tab=` allow-listed; no dangerously-set-inner-HTML React prop.
</threat_model>

<verification>
- `pnpm --filter web i18n:build` is run as part of the gate but the generated `i18n/messages/{vi,en}.json` are NOT in this plan's `files_modified` and must not be committed here — Plan 06 regenerates and commits the canonical bundles. The per-feature `messages.ts` files (which ARE owned here) are the source of truth.
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged; the triage-audit list endpoint gap is documented in `triage-api.ts`, `useTriageAuditLog.ts`, the `AuditLog` "not yet available" panel, the e2e spec comment, and the SUMMARY.
- No new runtime dependency in `apps/web/package.json`.
- Manual: load `/triage?tab=audit|shadow|senders` in a real browser at 1280px and 320px, light + dark — no horizontal scroll; the "audit history not yet available" panel on the audit tab; shadow/sender tabs work.
</verification>

<success_criteria>
- `/triage` (under `(app)/`) is a single shell-hosted page with deep-linkable `Tabs`; the audit log degrades to a distinct "not yet available" panel for the missing backend endpoint, with the populated-rows path covered by a component test using injected data; in-window undo calls the undo endpoint with a confirm naming the inverse change + a component-fired toast, out-of-window shows a finite "Undo window closed" label; shadow mode reads/writes its endpoint with a turn-off confirm; the sender list renders (incl. empty) and opt-in calls its endpoint; the audit-list backend gap is flagged; all gates green; visual reviews recorded.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-03-SUMMARY.md` (record: the `frontend-design` visual-review notes; the documented audit-list degradation path; the `AuditEntry` row-model shape chosen; the `AuditLog` injected-data seam shape; the resolved value of Open Questions 1 + 4 if anything was learned from the backend).
</output>
