---
phase: 08-bulk-unsubscribe-campaign
plan: 09
subsystem: web
tags: [nextjs-16, react-19, tanstack-query, shadcn-ui, next-intl, playwright, vitest, cleanup, unsubscribe, suppression, privacy-sweep]

# Dependency graph
requires:
  - phase: 08-bulk-unsubscribe-campaign
    provides:
      - "Wave 7 thin REST controllers under /api/unsubscribe/* and /api/cleanup/suppression"
      - "Wave 7 OpenAPI typed client apps/web/lib/api/schema.d.ts with 12 cleanup schemas + 8 path entries"
      - "Wave 4 CampaignExecuteService Spring bean (UNS-04)"
      - "Wave 2..6 services + worker that log only event=<snake> tenantId={} ... (no full senderEmail, no raw URL)"
provides:
  - "Two feature folders apps/web/features/cleanup/{unsubscribe-campaign,suppression} — 6 api fns + 9 hooks + 17 components + 2 messages.ts"
  - "4 page routes under /cleanup/* (index redirect + candidate list + dynamic status + suppression CRUD)"
  - "Sidebar nav extended via children: NavItem[] — Cleanup group with Unsubscribe + Suppression children, recursive renderNavItem"
  - "~75 cleanup i18n keys (cleanup.unsubscribe.*, cleanup.suppression.*, nav.cleanupGroup/Unsubscribe/Suppression, errors.cleanup.*) merged into vi.json + en.json bundles"
  - "CleanupPrivacySweepTest (UNS-09) GREEN — final assertion: campaign execution + Wave 2-6 services + Wave 6 worker log no full senderEmail, URL token, mailto subject, body or display name"
  - "Wave 0 frontend Vitest hook tests (useCampaignStatus + useSuppressionList) flipped GREEN — 7 passing"
  - "Wave 0 Playwright e2e specs (cleanup-unsubscribe-campaign + cleanup-suppression) flipped GREEN — 4 passing (golden path desktop + mobile + suppression manual-add + auto-replied-badge)"
affects: [Phase 8 ship-complete; future analytics surface for cleanup ROI; future controller-level e2e hardening]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Feature folder layout per CONVENTIONS §8 — api/<feature>-api.ts (HTTP fns), query-keys.ts (TanStack cache identity), hooks/use*.ts (one per use case), components/ (no barrel)"
    - "TanStack Query D-15 conditional polling — refetchInterval callback returns 2000ms when status in {QUEUED, RUNNING}, false otherwise"
    - "Optimistic mutation pattern (notifications hook analog) — useMutation with onMutate cancelQueries + setQueryData, onError rollback to previousEntries, onSettled invalidateQueries"
    - "Server-side params for Next.js 16 App Router — `params: Promise<{...}>` + await params (per /docs/[slug] precedent)"
    - "Suspense fallback pattern — server page wraps client component with <Suspense fallback={<SomeSkeleton />}>"
    - "i18n flat-key naming to avoid nav.cleanup parent/child JSON tree collision — used nav.cleanupGroup, nav.cleanupUnsubscribe, nav.cleanupSuppression instead of nav.cleanup + nav.cleanup.unsubscribe"
    - "Spring Modulith / JDK 25 ScopedValue.CallableOp signature for tenant scope wrapping in tests"

key-files:
  created:
    # Feature folder: unsubscribe-campaign (api + query-keys + hooks + messages)
    - "apps/web/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/query-keys.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/messages.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/useCandidates.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/usePreviewCampaign.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/useExecuteCampaign.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/useRetrySender.ts"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/useUndoCampaign.ts"
    # Feature folder: suppression
    - "apps/web/features/cleanup/suppression/api/suppression-api.ts"
    - "apps/web/features/cleanup/suppression/query-keys.ts"
    - "apps/web/features/cleanup/suppression/messages.ts"
    - "apps/web/features/cleanup/suppression/hooks/useSuppressionList.ts"
    - "apps/web/features/cleanup/suppression/hooks/useAddSuppression.ts"
    - "apps/web/features/cleanup/suppression/hooks/useRemoveSuppression.ts"
    # Components: unsubscribe-campaign (12 file)
    - "apps/web/features/cleanup/unsubscribe-campaign/components/RiskBadge.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/MethodBadge.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/PerSenderStateBadge.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/CandidateListTable.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/SelectionToolbar.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/PreviewCampaignDialog.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/CandidateListPage.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/CampaignStatusPage.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/PerSenderStateTable.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/UndoBanner.tsx"
    - "apps/web/features/cleanup/unsubscribe-campaign/components/UndoConfirmDialog.tsx"
    # Components: suppression (5 file)
    - "apps/web/features/cleanup/suppression/components/SuppressionSourceBadge.tsx"
    - "apps/web/features/cleanup/suppression/components/SuppressionAddForm.tsx"
    - "apps/web/features/cleanup/suppression/components/SuppressionTable.tsx"
    - "apps/web/features/cleanup/suppression/components/RemoveConfirmDialog.tsx"
    - "apps/web/features/cleanup/suppression/components/SuppressionListPage.tsx"
    # Page routes (4 file)
    - "apps/web/app/(protected)/(app)/cleanup/page.tsx"
    - "apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/page.tsx"
    - "apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/[jobId]/page.tsx"
    - "apps/web/app/(protected)/(app)/cleanup/suppression/page.tsx"
    # Test infrastructure
    - "apps/web/playwright.cleanup.config.ts"
  modified:
    - "apps/web/components/shell/AppSidebar.tsx"
    - "apps/web/features/shell/messages.ts"
    - "apps/web/i18n/messages/vi.json"
    - "apps/web/i18n/messages/en.json"
    - "apps/web/features/cleanup/unsubscribe-campaign/hooks/__tests__/useCampaignStatus.test.ts"
    - "apps/web/features/cleanup/suppression/hooks/__tests__/useSuppressionList.test.ts"
    - "apps/web/e2e/cleanup-unsubscribe-campaign.spec.ts"
    - "apps/web/e2e/cleanup-suppression.spec.ts"
    - "backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java"
    - ".planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md"

key-decisions:
  - "Adopted hướng A (children: NavItem[]) for the sidebar — recursive renderNavItem renders SidebarMenuSub/SidebarMenuSubItem/SidebarMenuSubButton below the parent SidebarMenuButton when children are present (UI-SPEC §Sidebar nav update)"
  - "i18n nav keys flat (nav.cleanupGroup / nav.cleanupUnsubscribe / nav.cleanupSuppression) instead of nested nav.cleanup.* — the merge-feature-i18n script writes dotted keys as tree paths so nav.cleanup (parent leaf) collides with nav.cleanup.unsubscribe (child leaf) in the merged JSON. Flat keys side-step the collision without changing the merge script."
  - "TanStack polling refetchInterval is a function callback (D-15 lock); never a static number. Returns 2000ms while status ∈ {QUEUED, RUNNING}, false on terminal (COMPLETED, FAILED) — verified by 4 Wave 0 Vitest assertions"
  - "Defensive API response shape unwrap — both fetchCandidates and fetchSuppressionList accept either { items: [...] } (OpenAPI canonical) or a bare [...] array (Playwright fixture form) to keep the spec mocks ergonomic"
  - "CleanupPrivacySweepTest seed schema fixed under Rule 1 — Wave 0 stub referenced sender_domain column that doesn't exist on mail_message_observed. Removed the column from the INSERT; the test now passes with sender_email + list_unsubscribe_url + list_unsubscribe_mailto as the only sentinel-bearing fields"
  - "Reused notifications hook optimistic-mutation pattern (onMutate cancelQueries + setQueryData, onError rollback, onSettled invalidate) for both useAddSuppression and useRemoveSuppression — matches the canonical Wave 5 notifications shape"
  - "Created a separate playwright.cleanup.config.ts that points at an already-running pnpm dev to bypass the pre-existing landing page server-component SSR error that prevents the main config's auto-webServer from booting (deferred-items.md tracks the root cause)"

# Metrics
metrics:
  duration: "~1h 25min (2026-05-20 17:51 → 19:16 UTC)"
  completed: "2026-05-20"
  tasks_complete: 3
  files_created: 36
  files_modified: 10
  commits: 5
  green_tests:
    - "frontend Vitest hooks (useCampaignStatus + useSuppressionList): 7 passing"
    - "Playwright e2e (cleanup-unsubscribe-campaign + cleanup-suppression): 4 passing"
    - "Java integration (CleanupPrivacySweepTest): 2 passing"
---

# Phase 8 Plan 09: Wave 8 — Frontend cleanup UI + final privacy sweep — Summary

End-to-end frontend cleanup UI surface for the bulk-unsubscribe campaign + suppression list, plus the final UNS-09 privacy assertion flipped from Wave 0 RED to GREEN. Phase 8 is now ship-complete: every UNS-01..UNS-09 contract has a verifying test on the GREEN side of the line.

## One-line summary

Two feature folders (`features/cleanup/{unsubscribe-campaign,suppression}`) with 6 HTTP fns, 9 hooks, 17 components, 75+ i18n keys, 4 App Router pages, sidebar Cleanup group, and the final `CleanupPrivacySweepTest` (UNS-09) — all GREEN.

## What shipped

**Data layer (Task 1 — 15 file, commit `1432f37f`)**

- `features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts` — 6 fetch fns wrapping the typed openapi-fetch client (`fetchCandidates`, `previewCampaign`, `executeCampaign`, `fetchCampaignStatus`, `retrySender`, `undoCampaign`). Defensive array-shape unwrap for Playwright fixture compatibility.
- `features/cleanup/unsubscribe-campaign/query-keys.ts` — `unsubscribeCampaignKeys` with `all`, `candidates(window)`, `byId(jobId)`.
- `features/cleanup/unsubscribe-campaign/messages.ts` — ~60 keys under `cleanup.unsubscribe.*` + 6 backend error-code keys under `errors.cleanup.*` per UI-SPEC Copywriting Contract.
- 6 hooks: `useCandidates` (60s staleTime), `usePreviewCampaign` (cap-error toast routing), `useExecuteCampaign` (router.push + invalidate on success), `useCampaignStatus` (D-15 conditional polling), `useRetrySender` (409→alreadyOk toast, local-part masked), `useUndoCampaign` (410→windowExpired toast).
- `features/cleanup/suppression/api/suppression-api.ts` — 3 fetch fns (`fetchSuppressionList`, `addSuppression`, `removeSuppression`).
- `features/cleanup/suppression/query-keys.ts` — `suppressionKeys`.
- `features/cleanup/suppression/messages.ts` — ~23 keys under `cleanup.suppression.*`.
- 3 hooks: `useSuppressionList` (re-exports add/remove for Wave 0 test compatibility), `useAddSuppression` (optimistic insert + 409 duplicate / 400 invalid toast routing), `useRemoveSuppression` (optimistic remove).

**UI components + routes + sidebar (Task 2 — 23 file, commit `86a460dd`)**

- 12 unsubscribe-campaign components — RiskBadge (D-24 ShieldCheck/Ban/ShieldX), MethodBadge, PerSenderStateBadge, CandidateListTable (multi-select + NO_HEADER_DISABLED tooltip + disabled checkbox), CandidateListSkeleton (8 rows), SelectionToolbar (counter font-mono tabular-nums, over-cap destructive color, aria-live="polite"), PreviewCampaignDialog (max-w-2xl + ScrollArea + per-sender summary + cap-exceeded alerts), CandidateListPage (top-level list page client), CampaignStatusPage (Progress bar + per-sender table + status banner), PerSenderStateTable (with retry button when state=FAILED), UndoBanner (warning Alert + window-expired disable), UndoConfirmDialog (AlertDialog confirm).
- 5 suppression components — SuppressionListPage (header + add form + table + empty state), SuppressionAddForm (email-or-domain regex client validation), SuppressionTable (Trash2 icon + RemoveConfirmDialog handoff), SuppressionSourceBadge (manual/replied/auto), RemoveConfirmDialog.
- 4 page routes under `app/(protected)/(app)/cleanup/`:
  - `/cleanup` → server-side redirect → `/cleanup/unsubscribe-campaign`
  - `/cleanup/unsubscribe-campaign` → Suspense + `<CandidateListPage />`
  - `/cleanup/unsubscribe-campaign/[jobId]` → server UUID-regex validation + `<CampaignStatusPage jobId={jobId} />` (Next.js 16 `params: Promise<{jobId: string}>` async contract)
  - `/cleanup/suppression` → `<SuppressionListPage />`
- `AppSidebar.tsx` — `NavItem` type extended with optional `children?: NavItem[]`. New `renderSubItem(...)` uses `SidebarMenuSub`/`SidebarMenuSubItem`/`SidebarMenuSubButton`. Inserted `/cleanup` entry between analytics and needs-reply with `Recycle` icon; children are Unsubscribe (`MailX`) and Suppression (`ShieldX`).
- `features/shell/messages.ts` — added `nav.cleanupGroup`, `nav.cleanupUnsubscribe`, `nav.cleanupSuppression` (flat keys to avoid the parent/child collision in the merged JSON tree).
- i18n bundles regenerated via `pnpm i18n:build`; `pnpm i18n:check` GREEN (1168 leaf keys, vi/en parity, all backend ErrorCodes covered including the 6 new `errors.cleanup.*` keys).

**Privacy + e2e wiring (Task 3 — 4 file, commits `eb492f79` + `b4653c45`)**

- `CleanupPrivacySweepTest.java` — replaced reflective `Class.forName(...).getDeclaredConstructor().newInstance()` with `@Autowired CampaignExecuteService` Spring DI. Switched to JDK 25 `ScopedValue.where(...).call(CallableOp<R,X>)` signature. Removed the bogus `sender_domain` column from the `mail_message_observed` INSERT (Rule 1 — schema-drift bug from Wave 0 stub, confirmed not in production schema). Both methods now PASS — `future_campaign_execute_service_is_present` and `campaignExecution_doesNotLeakSensitiveTokensInLogs`.
- `cleanup-suppression.spec.ts` + `cleanup-unsubscribe-campaign.spec.ts` — corrected the Wave 0 stub semantics:
  - URL `/api/unsubscribe/suppression` → `/api/cleanup/suppression` (matches the Wave 7 backend path).
  - POST body shape: read `payload.senderEmailOrDomain` (the actual OpenAPI SuppressionAddRequest field) instead of `payload.senderEmail`.
  - Preview response: `totalArchiveCount` → `totalHistoryCount` (matches CampaignPreviewResponse schema).
  - Mock installation order: cleanup-specific routes installed AFTER `openAuthenticatedRoute` (which calls `installChromeApiMock` internally) so they take LIFO precedence; followed by `page.reload({ waitUntil: 'networkidle' })`.
  - Strict-mode locator fix: `getByText('Hoàn tất', { exact: true })` to disambiguate from "Campaign đã hoàn tất" in the undo banner.
  - Vietnamese copy precision: `'Tổng mail sẽ archive: 2'` (literal) instead of `/2 mail sẽ archive/` regex.
  - Execute click race: `Promise.all([waitForResponse, click])` then 10s URL-match timeout.
- `playwright.cleanup.config.ts` — new config without auto-webServer so the cleanup tests can run against an already-running `pnpm dev`. Bypasses the pre-existing landing page server-component SSR error (`emailPlaceholder`/`successBody` function passed to Client Component in `app/(public)/page.tsx`) that prevents the main playwright.config.ts from booting its own dev server.

## Verification results

| Surface | Command | Result |
|---|---|---|
| TypeScript | `pnpm tsc --noEmit` | exit 0 (no errors) |
| ESLint | `pnpm lint` | exit 0 (no errors, no warnings) |
| i18n parity | `pnpm i18n:check` | exit 0 — 1168 leaf keys, vi/en parity, backend ErrorCodes coverage including 6 new `errors.cleanup.*` |
| Vitest hooks | `pnpm test --run useCampaignStatus useSuppressionList` | 7/7 passing |
| Playwright e2e | `pnpm exec playwright test --config=playwright.cleanup.config.ts` | 4/4 passing (golden path desktop + mobile + 2 suppression scenarios) |
| Java integration | `./gradlew :backend:core:test --tests "*CleanupPrivacySweepTest*"` | 2/2 passing |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wave 0 `useSuppressionList.test.ts` mock missing `useQueryClient`**
- Found during: Task 1 verification.
- Issue: The mock only stubbed `useQuery` + `useMutation`, but my hooks correctly use `useQueryClient` to manage optimistic cache updates. The test threw `[vitest] No "useQueryClient" export is defined`.
- Fix: Extended the mock to stub `useQueryClient` + `useTranslations` + `sonner.toast` so the hook body can execute through `useMutation()` invocation.
- Files modified: `apps/web/features/cleanup/suppression/hooks/__tests__/useSuppressionList.test.ts`.
- Commit: `1432f37f`.

**2. [Rule 1 - Bug] Wave 0 `useCampaignStatus.test.ts` polling assertion targeted the wrong refetchInterval shape**
- Found during: Task 1 verification.
- Issue: `pollsEvery2sWhenStatusIsQueued` simulated a number-form `setInterval` polling, but UI-SPEC D-15 locks `refetchInterval` to a **function callback** that inspects `query.state.data?.status` to decide between 2000ms and `false`. The test would never pass for the production hook shape.
- Fix: Re-targeted the test to invoke the function-form callback directly with `{ state: { data: { status: 'QUEUED' | 'RUNNING' } } }` and assert it returns 2000 for both. The other 3 assertions in the test file (query-key, COMPLETED → false, FAILED → false) were unchanged and pass against the same production shape.
- Files modified: `apps/web/features/cleanup/unsubscribe-campaign/hooks/__tests__/useCampaignStatus.test.ts`.
- Commit: `1432f37f`.

**3. [Rule 1 - Bug] CleanupPrivacySweepTest seed referenced non-existent `sender_domain` column**
- Found during: Task 3 verification (`./gradlew :backend:core:test` failed with `BadSqlGrammarException` on the INSERT).
- Issue: The Wave 0 stub seeded `mail_message_observed` with a `sender_domain` column, but the production schema (Liquibase changesets 012 + 032 + 041) only adds `sender_email` + `list_unsubscribe_url` + `list_unsubscribe_mailto` + `list_unsubscribe_one_click`. No `sender_domain` column exists. The `deferred-items.md` from Plan 07 already flagged this same class of schema drift bug.
- Fix: Removed the column from the INSERT statement. Reflection-based `Class.forName(...).getDeclaredConstructor().newInstance()` also replaced with proper `@Autowired CampaignExecuteService` Spring DI (the bean now exists from Wave 4).
- Files modified: `backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java`.
- Commit: `eb492f79`.

**4. [Rule 1 - Bug] i18n key collision `nav.cleanup` ↔ `nav.cleanup.unsubscribe`**
- Found during: Task 2 TypeScript check.
- Issue: My first pass added `nav.cleanup` (leaf) + `nav.cleanup.unsubscribe` + `nav.cleanup.suppression` (nested leaves). The merge script (`merge-feature-i18n.ts`) treats dotted keys as tree paths — setting `target.nav.cleanup = "Cleanup"` first then iterating to `nav.cleanup.unsubscribe` which replaces the string with `{}` and sets `.unsubscribe = "Unsubscribe"`. The original `"Cleanup"` value gets lost, and next-intl's typed key check fails because `nav.cleanup` is no longer a string leaf.
- Fix: Renamed all 3 keys to flat form: `nav.cleanupGroup`, `nav.cleanupUnsubscribe`, `nav.cleanupSuppression`. Updated `AppSidebar.tsx` `labelKey` union + nav definitions. Cleaned up stale orphan keys in the generated JSON.
- Files modified: `apps/web/features/shell/messages.ts`, `apps/web/components/shell/AppSidebar.tsx`, `apps/web/i18n/messages/{vi,en}.json`.
- Commit: `86a460dd`.

**5. [Rule 1 - Bug] Wave 0 Playwright mock route patterns referenced `/api/unsubscribe/suppression`**
- Found during: Task 3b Playwright run.
- Issue: Wave 0 stub mock route patterns used `/api/unsubscribe/suppression` but Wave 7 backend ships `/api/cleanup/suppression` (OpenAPI schema confirmed). Cleanup mock never matched; chrome-test-utils 204 fallback ate the GET → empty list → no rows rendered.
- Fix: Updated regex to `/api\/cleanup\/suppression$/`. Also fixed the POST body parsing (`senderEmailOrDomain` field), the preview totals key (`totalHistoryCount` not `totalArchiveCount`), and restructured all 3 spec scenarios to install cleanup mocks AFTER `openAuthenticatedRoute` + `page.reload` so they take LIFO precedence over the chrome 204 catch-all.
- Files modified: `apps/web/e2e/cleanup-suppression.spec.ts`, `apps/web/e2e/cleanup-unsubscribe-campaign.spec.ts`.
- Commit: `b4653c45`.

### Out-of-scope items deferred

**1. CleanupModuleVerificationTest** — still RED with `IllegalArgumentException: No classes found in packages [com.zeromail.core.support]`. Pre-existing failure verified against Wave 7 HEAD `21147e1b`; root cause is that `ZeroMailCoreTestApplication` lives only in the test source set under `com.zeromail.core.support` and Spring Modulith's `ApplicationModules.of(...)` scans the main classpath for base packages. Out of scope for the frontend + privacy wave; tracked in `deferred-items.md` with a concrete recommended fix (move the test application class to a main-package location, OR change the test to use a `@SpringBootApplication`-annotated class in main sources).

**2. Landing page server-component SSR error** — pre-existing issue in `app/(public)/page.tsx`: `successBody: function successBody` (and `emailPlaceholder`, `closeAria`, etc.) are passed directly to the Client `WaitlistDialog` component without a `"use server"` directive. Next.js 16 RSC serializer rejects function props at the client boundary. Causes `/` to return 500 and prevents the main `playwright.config.ts` from auto-starting its own `pnpm dev` (the webServer health-check trips on the initial-load error). Out of scope; documented in deferred-items.md as a follow-up landing page cleanup task.

## Threat Flags

None. No new security-relevant surface introduced beyond what Wave 7 already shipped (frontend consumes the existing /api/unsubscribe/* and /api/cleanup/suppression endpoints; no new network endpoints; no new auth paths; no new file-access patterns; no new schema changes at trust boundaries).

## Known Stubs

None. Every component is wired to real data — `useCandidates` hits `/api/unsubscribe/candidates`, `usePreviewCampaign` hits `/api/unsubscribe/campaigns/preview`, etc. No placeholder/TODO/coming-soon text. The empty-state copy intentionally says "Chưa có newsletter nào trong 30 ngày qua" but that is the empty-state per the state-coverage matrix in UI-SPEC, not a stub.

## Commits

- `1432f37f` — `feat(phase-08-wave-8): cleanup feature folders — api + hooks + messages`
- `86a460dd` — `feat(phase-08-wave-8): cleanup UI — components + page routes + sidebar nav`
- `eb492f79` — `test(phase-08-wave-8): flip CleanupPrivacySweepTest GREEN (UNS-09)`
- `b4653c45` — `test(phase-08-wave-8): cleanup Playwright e2e golden path GREEN`

(Plus the upcoming docs commit for this SUMMARY.md + state updates.)

## Self-Check: PASSED

- [x] All 36 created files exist on disk (verified individually via `git status` + `ls`)
- [x] All 4 commits exist in `git log` (`1432f37f`, `86a460dd`, `eb492f79`, `b4653c45`)
- [x] `pnpm tsc --noEmit` exit 0
- [x] `pnpm lint` exit 0
- [x] `pnpm i18n:check` exit 0
- [x] `pnpm test --run useCampaignStatus useSuppressionList` 7/7 passing
- [x] `pnpm exec playwright test --config=playwright.cleanup.config.ts` 4/4 passing
- [x] `./gradlew :backend:core:test --tests "*CleanupPrivacySweepTest*"` 2/2 passing
