---
phase: 05B-user-surface-ai-draft-replies
plan: 06
type: execute
wave: 6
depends_on: ["05B-05"]
files_modified:
  - apps/web/features/needs-reply/api/needs-reply-api.ts
  - apps/web/features/needs-reply/query-keys.ts
  - apps/web/features/needs-reply/hooks/useNeedsReplyInbox.ts
  - apps/web/features/needs-reply/hooks/useToReplyCount.ts
  - apps/web/features/needs-reply/hooks/useGenerateDraft.ts
  - apps/web/features/needs-reply/hooks/useMarkResolved.ts
  - apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx
  - apps/web/features/needs-reply/components/NeedsReplyTabs.tsx
  - apps/web/features/needs-reply/components/NeedsReplyTable.tsx
  - apps/web/features/needs-reply/components/NeedsReplyRow.tsx
  - apps/web/features/needs-reply/components/GenerateDraftButton.tsx
  - apps/web/features/needs-reply/messages.ts
  - apps/web/app/(protected)/(app)/needs-reply/page.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/features/triage/api/triage-api.ts
  - apps/web/features/triage/hooks/useTriageAuditLog.ts
  - apps/web/features/triage/components/AuditTable.tsx
  - apps/web/features/triage/components/AuditRow.tsx
  - apps/web/features/triage/messages.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
autonomous: true
requirements: [DRFT-04]
must_haves:
  truths:
    - "A 'Needs reply' sidebar nav item appears in the authenticated app shell, with a TO_REPLY count badge (untinted/hidden at 0)"
    - "/needs-reply renders a two-bucket Tabs view (To reply / Awaiting reply [+ optional Resolved]) with per-thread rows"
    - "Each row shows subject, the other party, relative last-activity time, a draft-status badge (No draft / Draft ready / Draft sent), an Open-in-Gmail external link, a Draft reply / Regenerate draft action, and Mark resolved"
    - "Clicking Draft reply / Regenerate draft calls POST /api/threads/{id}/draft; success -> toast + badge flips to Draft ready; 409 -> inline amber notice; failure -> destructive toast; no draft body is ever rendered"
    - "The /triage audit list is now live (GET /api/triage/audit, Load more via nextCursor) and each save_draft row also exposes the Draft reply / Regenerate draft action; the 5A GAP sentinels are removed"
    - "Loading / classifying-banner / empty (TO_REPLY 'Inbox zero') / empty (AWAITING) / error states render; the page is responsive to 320px"
  artifacts:
    - path: "apps/web/features/needs-reply/components/NeedsReplyTable.tsx"
      provides: "the table-shaped needs-reply list (raw shadcn table/tabs/badge/button/skeleton/alert/tooltip), reusing the features/triage AuditTable shape"
    - path: "apps/web/app/(protected)/(app)/needs-reply/page.tsx"
      provides: "the /needs-reply route mounting NeedsReplyPageClient"
    - path: "apps/web/features/needs-reply/api/needs-reply-api.ts"
      provides: "typed HTTP calls: GET /api/threads, POST /api/threads/{id}/draft, POST /api/threads/{id}/resolve"
  key_links:
    - from: "apps/web/components/shell/AppSidebar.tsx"
      to: "/needs-reply route"
      via: "new nav item + TO_REPLY count badge"
      pattern: "needs-reply"
    - from: "apps/web/features/triage/hooks/useTriageAuditLog.ts"
      to: "GET /api/triage/audit"
      via: "useInfiniteQuery consuming nextCursor; GAP sentinel removed"
      pattern: "/api/triage/audit"
    - from: "apps/web/features/needs-reply/hooks/useGenerateDraft.ts"
      to: "POST /api/threads/{gmailThreadId}/draft"
      via: "useMutation + invalidate needsReplyKeys + sonner toast"
      pattern: "/api/threads/.*/draft"
---

<objective>
Build the `apps/web` needs-reply surface: a `features/needs-reply/` feature folder (typed API, query-key factory, per-use-case hooks, raw-shadcn components), a `/needs-reply` route, a new "Needs reply" sidebar nav item with a TO_REPLY count badge, and the "Draft reply / Regenerate draft" action — surfaced both on the needs-reply rows and on the now-live `/triage` audit-log rows (wiring `useTriageAuditLog` to `GET /api/triage/audit` and removing the 5A GAP sentinels). Conform to the UI-SPEC: base teal token contract, raw shadcn `base-nova` primitives (no wrapper components), 4 type sizes, the dense-table row rhythm, all states (loading / classifying banner / empty / error / 320px), and the locked copy. Invoke the Anthropic `frontend-design` skill before writing any UI code.

Purpose: Completes WEB-02's draft-review portion in the UI and closes the visible half of the 5A audit-list gap.
Output: `features/needs-reply/` (11 files), `/needs-reply` page, `AppSidebar` nav item, `features/triage` rewiring, vi/en i18n keys.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PROTOTYPE.html
@.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md
@apps/web/features/triage/components/AuditTable.tsx
@apps/web/features/triage/api/triage-api.ts
@apps/web/features/triage/query-keys.ts
@apps/web/features/triage/hooks/useTriageAuditLog.ts
</context>

<interfaces>
<!-- The typed contract comes from apps/web/lib/api/schema.d.ts (regenerated in Plan 05). Read it. -->

`apps/web/lib/api/schema.d.ts` (after Plan 05): `GET /api/threads` -> `{ items: NeedsReplyRowResponse[], nextCursor: string | null, toReplyCount: number }` where a row has `gmailThreadId`, `subject`, `otherParty`, `lastActivityAt`, `draftStatus: 'NO_DRAFT'|'DRAFT_READY'|'DRAFT_SENT'`, `resolved`, `openInGmailUrl`; `POST /api/threads/{gmailThreadId}/draft` -> `{ draftId, gmailThreadId, status: 'GENERATED'|'REGENERATED', openInGmailUrl }` (no body); `POST /api/threads/{gmailThreadId}/resolve`; `GET /api/triage/audit` -> `{ items: AuditEntryResponse[], nextCursor: string | null }` where an item has `auditId`, `gmailThreadId`, `gmailMessageId`, `ruleName`, `action`, `reason`, `decisionState`, `createdAt`, `draftId`.

`apps/web/features/triage/api/triage-api.ts` (existing): `import { api, ... } from '@/lib/api/client'`; `unwrap<T>(result, fallbackMessage)` helper; `jsonHeaders()/unsafeHeaders()`; `getAuditLog()` currently returns `{ unavailable: true }` (the 5A GAP sentinel, ~lines 75-81) — replace it with `api.GET('/api/triage/audit', { params: { query: { ...filters, cursor, limit } } })`.

`apps/web/features/triage/hooks/useTriageAuditLog.ts` (existing): currently short-circuits on the GAP sentinel (~lines 8-23) — rewrite to `useInfiniteQuery({ queryKey: triageKeys.auditLog(filters), queryFn: ({ pageParam }) => getAuditLog({ ...filters, cursor: pageParam, limit: 50 }), initialPageParam: null, getNextPageParam: (last) => last.nextCursor ?? undefined })` + a flatten helper.

`apps/web/components/ui/*` (installed): `tabs`, `table`, `badge`, `button`, `skeleton`, `alert`, `tooltip`, `sidebar`, `alert-dialog` (conditional), `sonner` — all present. `apps/web/components/states/{EmptyState,ErrorState,LoadingState}.tsx` (existing shared states). `apps/web/components/shell/AppSidebar.tsx` — the nav-item list to extend.

`useIsMobile` / a CSS branch for the 320px responsive split (the 5A `/triage` page already does this for the table-vs-card-list switch — mirror it).

`next-intl` `useTranslations()` — new namespace `needsReply.*` + additions under `triage.*` (the new draft-reply action labels + the now-live audit list copy: `Load more`, `That's everything.`); mirrored lock-step in `apps/web/i18n/messages/{vi,en}.json`; `pnpm i18n:check` must pass; Vietnamese is the default rendering.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: features/needs-reply/ — API + query keys + hooks + i18n + rewire live audit log</name>
  <files>apps/web/features/needs-reply/api/needs-reply-api.ts, apps/web/features/needs-reply/query-keys.ts, apps/web/features/needs-reply/hooks/useNeedsReplyInbox.ts, apps/web/features/needs-reply/hooks/useToReplyCount.ts, apps/web/features/needs-reply/hooks/useGenerateDraft.ts, apps/web/features/needs-reply/hooks/useMarkResolved.ts, apps/web/features/needs-reply/messages.ts, apps/web/features/triage/api/triage-api.ts, apps/web/features/triage/hooks/useTriageAuditLog.ts, apps/web/features/triage/messages.ts, apps/web/i18n/messages/en.json, apps/web/i18n/messages/vi.json</files>
  <read_first>
    - apps/web/features/triage/api/triage-api.ts (the `api.GET`/`api.POST` + `unwrap` + headers pattern; the `getAuditLog()` GAP sentinel to replace)
    - apps/web/features/triage/query-keys.ts (the key-factory shape)
    - apps/web/features/triage/hooks/useTriageAuditLog.ts (the GAP-sentinel short-circuit to rewrite into a real `useInfiniteQuery`) + useUndoAuditEntry.ts (the mutation+invalidate pattern)
    - apps/web/features/triage/messages.ts (feature-local i18n merge pattern)
    - apps/web/lib/api/schema.d.ts (the regenerated types from Plan 05 — confirm the exact path/param/response shapes)
    - apps/web/i18n/messages/en.json + vi.json (the namespace structure; where `triage.*` lives)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md §"Copywriting Contract" (the exact strings) + §"Key Screens" (which hook drives which state)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Frontend — features/needs-reply/"
  </read_first>
  <behavior>
    - `needs-reply-api.ts`: `getNeedsReplyInbox({ bucket, cursor, limit, resolved })` -> `api.GET('/api/threads', { params: { query: { bucket, cursor, limit, resolved } } })` via `unwrap`; `generateDraft(gmailThreadId)` -> `api.POST('/api/threads/{gmailThreadId}/draft', { params: { path: { gmailThreadId } }, headers: unsafeHeaders() })`; `markResolved(gmailThreadId)` -> `api.POST('/api/threads/{gmailThreadId}/resolve', { params: { path: { gmailThreadId } }, headers: unsafeHeaders() })`. The `generateDraft` call surfaces a 409 distinctly (inspect the status / error code so the hook can show the inline amber notice rather than the generic destructive toast).
    - `query-keys.ts`: `needsReplyKeys = { all: ['needs-reply'], inbox: (bucket, resolved) => [...all, 'inbox', bucket, resolved], count: () => [...all, 'count'] }`. (No mutation-only keys for `generateDraft`/`markResolved` — they invalidate `needsReplyKeys.all` per convention #8.)
    - `useNeedsReplyInbox(bucket, resolved)` -> `useInfiniteQuery` over `getNeedsReplyInbox`, `getNextPageParam: last => last.nextCursor ?? undefined`, plus a `flatten` helper. It does NOT drive the sidebar badge.
    - `useToReplyCount()` -> a **separate, lightweight** `useQuery({ queryKey: needsReplyKeys.count(), queryFn: getToReplyCount, staleTime: 60_000 })` that hits a cheap counts source — either a dedicated `GET /api/threads/count` (if Plan 05 added one) OR `getNeedsReplyInbox({ bucket: 'to-reply', limit: 1, resolved: false })` and reads `toReplyCount` off the response (the projection's `countByBucketAndResolvedFalse` is the partial-index count — no per-row Gmail fetch). The sidebar `<AppSidebar>` calls `useToReplyCount()`, NOT `useNeedsReplyInbox`, so a route change anywhere in the app does NOT trigger a full inbox load + per-row Gmail metadata fan-out — just one cheap count query (cached 60s).
    - `useGenerateDraft()` -> `useMutation({ mutationFn: generateDraft, onSuccess: () => { invalidate needsReplyKeys.all; toast "Draft saved in Gmail — review and send it there." }, onError: (e) => e is 409 ? /* let the component show the inline notice */ : toast.error "Couldn't generate a draft. Try again in a moment." })`.
    - `useMarkResolved()` -> `useMutation({ mutationFn: markResolved, onSuccess: () => invalidate needsReplyKeys.all })`.
    - Rewire `features/triage/api/triage-api.ts` `getAuditLog(filters)` to call `GET /api/triage/audit` (cursor-paginated); rewrite `features/triage/hooks/useTriageAuditLog.ts` to a real `useInfiniteQuery` with `nextCursor`; **remove the 5A GAP sentinels** (the `{ unavailable: true }` return + the hook short-circuit).
    - i18n: add the `needsReply.*` namespace (page heading/subtitle, tab labels, per-row CTA labels including in-progress, `Mark resolved`, `Open in Gmail`, the three draft-status badge labels, the recompute banner, the three empty-state heading/body pairs, the error heading/body/retry, the success/failure/409 toast+notice copy — all verbatim from UI-SPEC §Copywriting Contract) and the `triage.*` additions (`Load more`, `That's everything.`, the draft-reply action labels) to both `en.json` and `vi.json` lock-step. `messages.ts` files merge the feature namespace.
  </behavior>
  <action>
    Create `features/needs-reply/api/needs-reply-api.ts` (incl. `getToReplyCount`), `query-keys.ts`, the FOUR hook files (`useNeedsReplyInbox`, `useToReplyCount`, `useGenerateDraft`, `useMarkResolved`), and `messages.ts`. Rewrite `features/triage/api/triage-api.ts` `getAuditLog` + `features/triage/hooks/useTriageAuditLog.ts` to consume `GET /api/triage/audit` and drop the GAP sentinels. Add all new i18n keys to `en.json` + `vi.json` (lock-step — Vietnamese is the default rendering; flag in the SUMMARY that the new safety-related vi error/notice copy should be reviewed by a Vietnamese speaker, not just machine-translated). Component/e2e tests use MSW (or the project's existing API-fixture harness) to mock `GET /api/threads` / `POST /api/threads/{id}/draft` — never a live backend. Run `pnpm -C apps/web tsc --noEmit` + `pnpm -C apps/web i18n:check` (both must pass).
  </action>
  <verify>
    <automated>cd "$REPO/apps/web" && pnpm tsc --noEmit 2>&1 | tail -5 && pnpm i18n:check && pnpm vitest run features/needs-reply features/triage 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `features/needs-reply/api/needs-reply-api.ts` exports `getNeedsReplyInbox`, `generateDraft`, `markResolved` typed against `schema.d.ts`; `generateDraft` distinguishes a 409
    - `useNeedsReplyInbox` is an `useInfiniteQuery` consuming `nextCursor`; `useToReplyCount` is a separate light `useQuery` (60s `staleTime`) that does NOT trigger the per-row Gmail fan-out and is the only thing the sidebar badge subscribes to; `useGenerateDraft`/`useMarkResolved` are mutations that invalidate `needsReplyKeys.all` (which also re-fetches the cheap count)
    - `features/triage/api/triage-api.ts` no longer returns `{ unavailable: true }`; `useTriageAuditLog` is a real `useInfiniteQuery` over `GET /api/triage/audit`; the GAP sentinel comments/code are gone
    - `apps/web/i18n/messages/{en,vi}.json` contain the full `needsReply.*` namespace + the `triage.*` additions, lock-step; `pnpm i18n:check` passes
    - `pnpm -C apps/web tsc --noEmit` passes
  </acceptance_criteria>
  <done>The data layer for the needs-reply feature + the rewired live audit-log hook + all i18n land.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: features/needs-reply/ components + /needs-reply route + sidebar nav item + triage audit-row draft action</name>
  <files>apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx, apps/web/features/needs-reply/components/NeedsReplyTabs.tsx, apps/web/features/needs-reply/components/NeedsReplyTable.tsx, apps/web/features/needs-reply/components/NeedsReplyRow.tsx, apps/web/features/needs-reply/components/GenerateDraftButton.tsx, apps/web/app/(protected)/(app)/needs-reply/page.tsx, apps/web/components/shell/AppSidebar.tsx, apps/web/features/triage/components/AuditTable.tsx, apps/web/features/triage/components/AuditRow.tsx</files>
  <read_first>
    - INVOKE the Anthropic `frontend-design` skill FIRST — per the project memory rule, before writing any UI code (and pass the rule into any sub-agent).
    - apps/web/features/triage/components/AuditTable.tsx + AuditRow.tsx + TriagePageClient.tsx + UndoButton.tsx (the table-shaped list + per-row + page-client + per-row-mutation-button shapes to mirror; the 320px table-vs-card-list branch)
    - apps/web/app/(protected)/(app)/triage/page.tsx (the route shape to mirror for /needs-reply)
    - apps/web/components/shell/AppSidebar.tsx (the existing nav items + how a count badge attaches to a nav item slot)
    - apps/web/components/states/{EmptyState,ErrorState,LoadingState}.tsx + apps/web/components/ui/{tabs,table,badge,button,skeleton,alert,tooltip}.tsx (the raw primitives to compose — NO wrapper components)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md (§Design System, §Spacing, §Typography, §Color, §Visual Hierarchy, §Key Screens & States, §Copywriting Contract — the full visual contract) + 05B-PROTOTYPE.html (the visual reference)
    - apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx (the RED Vitest test from Plan 00 — make it pass) + apps/web/e2e/needs-reply.spec.ts (un-`fixme` the golden path)
  </read_first>
  <behavior>
    - `NeedsReplyPageClient.tsx` (`'use client'`): page heading "Needs reply" (20px/600) + the muted subtitle; mounts `NeedsReplyTabs`; manages the active tab (`to-reply` default / `awaiting-reply` / optional `resolved`) — keep the tab in the URL (`?tab=`) mirroring `/triage`'s `?tab=`; renders the active bucket's `NeedsReplyTable` with its loading/classifying/empty/error states.
    - `NeedsReplyTabs.tsx`: raw shadcn `Tabs` — triggers `To reply` / `Awaiting reply` / (optional) `Resolved`, each with a count badge (the TO_REPLY badge accent-tinted, dropped/hidden at 0); active trigger has the accent underline.
    - `NeedsReplyTable.tsx`: at `>= md` a dense `Table` (reuse the `features/triage` `AuditTable` shape — `bg-card rounded-lg border` wrapper, `Table`/`TableHeader`/`TableHead`/`TableRow`/`TableBody`/`TableCell`, `py-2` cell padding); columns Subject (Body 14/500, truncates) · Other party (Body 14/400 muted) · Last activity (mono 12px relative time + absolute on hover via `Tooltip`) · Draft status (`No draft` muted `Badge` / `Draft ready` blue-soft / `Draft sent` green-soft) · Actions (right-aligned: `GenerateDraftButton`, an Open-in-Gmail external-link icon button -> `row.openInGmailUrl`, opens new tab; a `Mark resolved` X-icon button calling `useMarkResolved`). Below `md`: single-column card rows — line1 = subject (truncates) + draft-status badge; line2 = other party + relative time (muted 12px); line3 = action button (icon-only, 44px hit area) + Gmail icon + resolve icon. Loading = `Skeleton` rows; the amber "Updating your needs-reply list…" banner above a (possibly stale) list when a recompute/backfill is in flight; empty TO_REPLY = `EmptyState` "Inbox zero" (the prototype's heading) / "Nothing needs a reply right now." (green-soft check accent); empty AWAITING = "Nothing awaiting" / "No threads are waiting on the other party."; error = `Alert` `destructive` "Couldn't load your needs-reply list" + "Try again" re-running the query. On the AWAITING tab the `Draft reply` action is generally hidden or shown disabled with a tooltip (planner's discretion) and the rows read visually calmer.
    - `NeedsReplyRow.tsx`: one row's rendering (desktop `TableRow` cells or the mobile card layout) given a `NeedsReplyRowResponse`.
    - `GenerateDraftButton.tsx`: a neutral default/secondary `Button` (NOT accent — many per page); label `Draft reply` when `draftStatus === 'NO_DRAFT'`, `Regenerate draft` otherwise; on click -> `useGenerateDraft().mutate(gmailThreadId)` -> loading state (spinner, disabled, label -> "Generating…"/"Regenerating…"); on success the row's draft-status badge flips to `Draft ready` (blue-soft) and the success toast fires; on **409** show the inline amber notice on the row "A draft is already being generated for this thread." IMMEDIATELY and keep the button DISABLED for a short cooldown (≈3-5s) before re-enabling — so the user can't rapid-re-click into a 409 loop; on failure the destructive toast + the badge unchanged. **No draft body is ever fetched or rendered.** When a draft already exists, gate `Regenerate draft` behind an `alert-dialog` confirm ("Replace the current draft? The draft currently in Gmail will be discarded and a new one generated. Cancel / Replace draft.") — required per UI-SPEC §Destructive; no confirm for the first draft.
    - `app/(protected)/(app)/needs-reply/page.tsx`: the route shell (mirror `/triage/page.tsx`) mounting `NeedsReplyPageClient`.
    - `AppSidebar.tsx`: add a "Needs reply" nav item (following the existing nav-item entries — icon + label + active-state styling) with a `TO_REPLY` count badge in the block's badge slot fed by `useToReplyCount()` (the cheap dedicated count query — NOT `useNeedsReplyInbox`, so a route change anywhere never triggers a full inbox load + per-row Gmail fan-out); badge hidden/untinted at 0.
    - `features/triage` `AuditTable.tsx` / `AuditRow.tsx`: on each `save_draft` audit row, render the same `GenerateDraftButton` (passing the row's `gmailThreadId`) alongside the existing `Undo`; non-`save_draft` rows do not show it. Add the `Load more` affordance (consumes `nextCursor` from `useTriageAuditLog`) + the "That's everything." end-of-list line. Reuse `features/needs-reply/hooks/useGenerateDraft` — do not duplicate the mutation.
  </behavior>
  <action>
    Invoke `frontend-design` first. Build the five `features/needs-reply/components/*` files (raw shadcn primitives only — no custom wrappers; reuse the `features/triage` table shape and the shared `states/` components), the `/needs-reply` route page, the `AppSidebar` nav item + badge, and the `features/triage` audit-row draft-action + `Load more` additions. Make `NeedsReplyTable.test.tsx` (Vitest) pass and un-`fixme` `apps/web/e2e/needs-reply.spec.ts`'s golden path (navigate to `/needs-reply`, see the two-bucket Tabs, click `Draft reply`, see the success toast, see the badge flip). Verify visual quality in a real browser via Playwright MCP per the project UX rule (golden path + the 320px breakpoint + the empty/error states, light and dark themes) — type-check passing is not enough. Run `pnpm -C apps/web tsc --noEmit`, `pnpm -C apps/web lint`, `pnpm -C apps/web vitest run`, and `pnpm -C apps/web i18n:check` (all must pass).
  </action>
  <verify>
    <automated>cd "$REPO/apps/web" && pnpm tsc --noEmit 2>&1 | tail -5 && pnpm lint 2>&1 | tail -5 && pnpm vitest run 2>&1 | tail -10 && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `NeedsReplyTable.test.tsx` passes: renders both buckets at 0 / 1 / many; loading shows `Skeleton`; the amber classifying banner renders above a stale list; TO_REPLY empty shows the "Inbox zero" heading; AWAITING empty shows "Nothing awaiting"; error shows the destructive `Alert` + "Try again"; a row exposes `Draft reply`/`Regenerate draft`, the Open-in-Gmail link to `https://mail.google.com/mail/u/0/#all/<threadId>`, the draft-status badge, and `Mark resolved`
    - `/needs-reply` route renders under the Phase 5A app shell; the sidebar shows a "Needs reply" item with a TO_REPLY badge (hidden/untinted at 0)
    - `/triage` audit list is live (no `{ unavailable: true }`), paginates via `Load more`, and `save_draft` rows show the `Draft reply`/`Regenerate draft` action
    - Clicking the draft action calls `POST /api/threads/{id}/draft`; success -> toast "Draft saved in Gmail — review and send it there." + badge -> `Draft ready`; 409 -> inline amber notice; failure -> destructive toast; no draft body rendered anywhere; no Send/Edit control exists on any draft surface
    - Components use only raw shadcn primitives (`tabs`/`table`/`badge`/`button`/`skeleton`/`alert`/`tooltip` + shared `states/`) — no new wrapper components; base teal token contract (not the `.zm-proto` skin); 4 type sizes; responsive to 320px; React text rendering only (no raw-HTML injection of any thread subject / participant string)
    - `apps/web/e2e/needs-reply.spec.ts` golden path is no longer `fixme` and passes (or is documented env-blocked with a replay command per the prior-phase convention); Playwright MCP visual review done (golden path + 320px + empty/error)
    - `pnpm tsc --noEmit` + `pnpm lint` + `pnpm vitest run` + `pnpm i18n:check` all pass
  </acceptance_criteria>
  <done>The needs-reply inbox + sidebar item + the live audit list with the draft action are shipped, visually verified, all gates green.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser to backend REST (typed client) | the new endpoints; session cookie auth (unchanged) |
| rendered UI to user | must never render a draft body, must offer no Send/Edit control |
| external Gmail deep link | `https://mail.google.com/mail/u/0/#all/<threadId>` opens a new tab |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-06-01 | Information Disclosure | the UI fetching or rendering a draft body / email content | mitigate | No API call returns a draft body (Plan 05's `ThreadDraftResponse` has none); the UI never requests one; the only "confirmation" surface is a `sonner` toast; review/edit/send is 100% in Gmail (UI-SPEC hard constraint) — `NeedsReplyTable.test.tsx` asserts no body rendering |
| T-05B-06-02 | Elevation of Privilege | a Send / draft-edit / auto-send control sneaking into the UI | mitigate | The UI exposes only `Draft reply`/`Regenerate draft`, `Open in Gmail`, `Mark resolved` — no Send, no Edit; acceptance criterion explicitly checks "no Send/Edit control"; there is no `drafts.send`/`drafts.update` endpoint to call (Plan 05) |
| T-05B-06-03 | Tampering | the external Gmail deep link as a navigation-injection vector | mitigate | The `openInGmailUrl` is server-constructed (`"https://mail.google.com/mail/u/0/#all/" + gmailThreadId`); the `gmailThreadId` is a Gmail-issued id from the projection, not free user input; render as an anchor with `target="_blank" rel="noopener noreferrer"` |
| T-05B-06-04 | Information Disclosure | email content / error text in client-side logs or toasts | mitigate | Toasts use the locked UI-SPEC copy (generic "Couldn't generate a draft…", "Draft saved in Gmail…") — never raw error text or a stack trace; `ApiError` bodies from Plan 05 carry codes + safe params only; the 409 inline notice is fixed copy |
| T-05B-06-05 | Tampering | XSS via a thread subject / other-party rendered in a row | mitigate | React escapes text content by default; the subject / other-party are rendered as plain JSX text children (no raw-HTML injection API used anywhere in the feature); they're sourced from Gmail `threads.get(metadata)` server-side and passed as strings |
| T-05B-06-06 | Denial of Service (quota) | the sidebar `TO_REPLY` badge triggering a full inbox load + per-row Gmail `threads.get` fan-out on EVERY route change in the app | mitigate | The badge subscribes only to `useToReplyCount()` — a cheap dedicated count query (the projection's partial-index `countByBucketAndResolvedFalse`, no per-row Gmail fetch) cached 60s; `useNeedsReplyInbox` (which does the fan-out) is mounted only on `/needs-reply`, not in the shell |
| T-05B-06-07 | Information Disclosure / safety | machine-translated Vietnamese copy for a safety-related error/notice misleading the user | mitigate | New `needsReply.*`/`triage.*` keys land lock-step in both `en.json` + `vi.json`; the SUMMARY flags the safety-related vi strings (the 409 notice, the draft-failure toast) for human review by a Vietnamese speaker before release |
</threat_model>

<verification>
- `pnpm -C apps/web tsc --noEmit` + `pnpm -C apps/web lint` + `pnpm -C apps/web vitest run` + `pnpm -C apps/web i18n:check` all pass
- `pnpm -C apps/web e2e -- needs-reply` passes (or documented env-blocked with a replay command)
- `grep -rn "unavailable" apps/web/features/triage` returns nothing (GAP sentinels removed)
- Playwright MCP visual review: `/needs-reply` golden path + 320px + empty/error states render correctly in both light and dark themes
- No raw-HTML injection API and no Send/Edit control on any draft surface
</verification>

<success_criteria>
The needs-reply two-bucket inbox, the "Needs reply" sidebar item + TO_REPLY badge, the "Draft reply / Regenerate draft" action (on the inbox rows and the now-live `/triage` audit rows), and the live `GET /api/triage/audit` list all ship in `apps/web` — raw shadcn, UI-SPEC-conformant, all states covered, 320px-responsive, no draft body / Send / Edit anywhere. WEB-02's draft-review portion is complete in the UI.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-06-SUMMARY.md`
</output>
