---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 06
subsystem: web-mailbox-ui
tags: [web, mailbox-scope, account-menu, rules, audit, playwright, openapi]

requires:
  - phase: 11-05
    provides: active mailbox GET/PUT API, copy-rules API, mailbox-scoped read APIs, and rule gmailConnectionId DTO fields
provides:
  - generated web OpenAPI schema for mailbox and copy-rules contracts
  - mailbox feature API/query-key/hooks triad
  - AccountMenu mailbox switcher separate from workspace identity
  - active mailbox badge on mailbox-scoped read/write surfaces
  - copy-rules dialog in the rules workspace
  - mocked Playwright coverage for switch, rules, send-from, and audit provenance
affects: [apps-web, mailbox-switching, rules-ui, needs-reply, inbox, audit, analytics]

tech-stack:
  added: []
  patterns:
    - Generated OpenAPI types are consumed through feature API functions, not hand-written mirror DTOs.
    - Active mailbox switching invalidates mailbox-scoped TanStack Query keys in one mutation hook.
    - AccountMenu keeps the signed-in workspace identity and connected Gmail mailbox list as separate UI concepts.

key-files:
  created:
    - apps/web/features/mailbox/api/mailbox-api.ts
    - apps/web/features/mailbox/query-keys.ts
    - apps/web/features/mailbox/hooks/useMailboxList.ts
    - apps/web/features/mailbox/hooks/useActiveMailbox.ts
    - apps/web/features/mailbox/hooks/useSetActiveMailbox.ts
    - apps/web/features/mailbox/components/ActiveMailboxBadge.tsx
    - apps/web/features/rules/components/CopyRulesDialog.tsx
    - apps/web/e2e/mailbox-test-utils.ts
    - apps/web/e2e/mailbox-switch.spec.ts
    - apps/web/e2e/mailbox-rules.spec.ts
    - apps/web/e2e/mailbox-send-from.spec.ts
    - apps/web/__tests__/mailbox-hooks.test.ts
  modified:
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json
    - apps/web/openapi/spec.json
    - apps/web/components/shell/AppSidebar.tsx
    - apps/web/app/(protected)/(app)/settings/SettingsClient.tsx
    - apps/web/features/inbox/components/InboxPageClient.tsx
    - apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx
    - apps/web/features/needs-reply/components/GenerateDraftButton.tsx
    - apps/web/features/rules/api/rules-api.ts
    - apps/web/features/rules/components/RulesWorkspace.tsx
    - apps/web/features/rules/components/RulesWorkspace.test.tsx
    - apps/web/features/triage/api/triage-api.ts
    - apps/web/features/triage/components/AuditTable.tsx
    - apps/web/features/triage/components/AuditRow.tsx
    - apps/web/features/analytics/components/AnalyticsPageClient.tsx
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

key-decisions:
  - The old tenant-singular Gmail connect URL was removed from settings/sidebar flows; add/reconnect now goes through mailbox-aware URLs.
  - New mailbox mutation toasts use TanStack Query mutation meta, matching the app-wide QueryClient error/success convention.
  - Copy-rules clones into the active mailbox and leaves copied rules disabled for review.
  - Browser specs mock every endpoint their target pages read, keeping them CI-runnable while the real Gmail path remains human-verified.

requirements-completed: [UX-01, UX-02, UX-03, UX-04, UX-05, UX-06, VER-02]
requirements-pending-human: [VER-04]

duration: multi-session
completed: 2026-06-10
automation_status: passed
human_smoke_status: pending human verification
implementation_commit: 67a8d4a8
---

# Phase 11 Plan 06 Summary

**Mailbox switching is surfaced in the web app; live Gmail smoke remains the blocking human checkpoint.**

## Performance

- **Duration:** multi-session resume
- **Completed automated work:** 2026-06-10
- **Tasks automated:** 3
- **Human checkpoint:** Task 4 pending
- **Implementation commit:** `67a8d4a8` (`feat(11-06): surface mailbox switching in web app`)

## Accomplishments

- Regenerated the web OpenAPI artifacts from the backend-generated spec:
  - `apps/web/lib/api/schema.d.ts`
  - `apps/web/openapi/openapi.json`
  - `apps/web/openapi/spec.json`
- Added the `features/mailbox` triad:
  - typed mailbox API functions for list/get-active/set-active
  - mailbox query-key factory
  - `useMailboxList`, `useActiveMailbox`, and `useSetActiveMailbox`
- `useSetActiveMailbox` invalidates mailbox-scoped caches for inbox, needs-reply, rules, audit/protected senders, and analytics so active mailbox switches do not render stale data.
- Updated `AppSidebar` AccountMenu so the workspace identity remains at the top while connected Gmail mailboxes render in a separate accounts group with active marker, primary/status labels, Switch actions, and Add Gmail.
- Removed the legacy `/api/tenant/connect-gmail` path from the settings/sidebar reconnect surfaces and replaced it with mailbox-aware connect/reconnect URL helpers.
- Added `ActiveMailboxBadge` and surfaced active mailbox context on inbox, needs-reply, rules, audit/history, analytics, and draft-generation controls.
- Added `CopyRulesDialog` to the rules workspace, wired through the generated copy-rules API contract, cloning rules into the active mailbox disabled by default.
- Extended rules/audit/needs-reply UI wiring so source and executing mailbox labels render when the backend DTO or test fixture includes those optional fields.
- Added i18n coverage for mailbox-related backend error codes and rebuilt message artifacts.

## New Test Handles

- `mailbox-switch-{id}`
- `mailbox-add-gmail`
- `mailbox-active-marker`
- `copy-rules-button`

## Playwright Coverage Added

- `apps/web/e2e/mailbox-switch.spec.ts` verifies AccountMenu mailbox listing, active marker movement, inbox/needs-reply refetch after switch, and 320px switcher reachability.
- `apps/web/e2e/mailbox-rules.spec.ts` verifies active-mailbox rules and copy-rules into the active mailbox with copied rules disabled.
- `apps/web/e2e/mailbox-send-from.spec.ts` verifies draft/send preview and audit provenance labels.
- `apps/web/e2e/mailbox-test-utils.ts` centralizes the mailbox-scoped endpoint mocks used by those specs.

## Browser Verification

Playwright MCP verified the live dev UI at `http://localhost:3000` with mocked mailbox data:

- Inbox initially showed Founder Gmail data, then switching to Support Gmail refetched the inbox to the support mailbox message.
- AccountMenu showed the workspace identity separately from the connected Gmail mailbox list.
- Rules rendered with the Support Gmail active badge; copy-rules opened with Founder Gmail as source and Support Gmail as target, then increased the rule count from 1 to 2.
- Needs Reply rendered Support Gmail, generated a draft, and showed `Draft from support@example.com.` with the Support Gmail active badge.
- Rules history/audit text contained `Source: support@example.com` and `Executing: support@example.com`.

## Limitations

- Richer audit/source/executing mailbox labels only render where the current generated backend DTO exposes those optional fields or the Playwright mock includes them. Some read DTOs still do not expose full provenance everywhere; the UI handles the fields when present without inventing client-side mailbox data.
- Real Gmail verification is not complete. Plan 11-06 remains blocked on the Task 4 human smoke using two real Gmail grants on the dev VPS.

## Verification

- `pnpm --filter web test -- mailbox-hooks` - passed.
- `pnpm --filter web exec tsc --noEmit` - passed.
- `pnpm --filter web run i18n:check` - passed.
- `pnpm --filter web run i18n:build` - passed after adding mailbox error messages.
- `pnpm --filter web exec playwright test e2e/mailbox-switch.spec.ts e2e/mailbox-rules.spec.ts e2e/mailbox-send-from.spec.ts` - passed.
- Targeted ESLint on touched web files - passed with one existing `react-hooks/incompatible-library` warning in `InboxPageClient.tsx` for TanStack Virtual `useVirtualizer`.
- Playwright MCP browser verification - passed for switch, copy-rules, draft preview, and audit provenance.
- `git diff --cached --check` - passed before the implementation commit.

## User Setup Required

Task 4 real-Gmail smoke is still pending. The human verifier must use the dev VPS with two real Gmail mailboxes connected and confirm:

1. Add a second Gmail mailbox through AccountMenu -> Add Gmail -> Google consent.
2. Send a fresh email to each connected Gmail address and verify each appears only under its own active mailbox.
3. Switch active mailbox and verify inbox, needs-reply, rules, and audit re-render without stale data.
4. Trigger a send/reply from each mailbox and verify the correct Gmail Sent account receives it.
5. Confirm previews showed the correct source and executing mailbox.
6. Confirm logs contain no raw email, subject, body, prompt, completion, or token values, only tenantId/gmailConnectionId/status metadata.

## Self-Check

AUTOMATED PASSED - OpenAPI regeneration, mailbox feature triad, AccountMenu switcher, copy-rules UI, active mailbox badges, mocked Playwright coverage, and browser MCP verification satisfy the automated parts of Plan 11-06.

HUMAN PENDING - VER-04 real-Gmail multi-mailbox smoke must pass before Phase 11 can be marked complete.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Automated work completed: 2026-06-10*
