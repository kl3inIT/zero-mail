---
phase: 05A-user-surface-web-ui-core
reviewed: 2026-05-12T00:00:00Z
depth: standard
files_reviewed: 84
files_reviewed_list:
  - apps/web/__tests__/architecture/feature-folders.test.ts
  - apps/web/__tests__/i18n/messages.contract.test.ts
  - apps/web/app/(protected)/(app)/billing/page.tsx
  - apps/web/app/(protected)/(app)/billing/top-up/page.tsx
  - apps/web/app/(protected)/(app)/layout.tsx
  - apps/web/app/(protected)/(app)/rules/page.tsx
  - apps/web/app/(protected)/(app)/settings/page.tsx
  - apps/web/app/(protected)/(app)/settings/privacy/page.tsx
  - apps/web/app/(protected)/(app)/triage/page.tsx
  - apps/web/app/(protected)/layout.tsx
  - apps/web/app/(protected)/onboarding/complete/CompleteClient.tsx
  - apps/web/app/(protected)/onboarding/complete/page.tsx
  - apps/web/app/(protected)/onboarding/gmail-connect/GmailConnectClient.tsx
  - apps/web/app/(protected)/onboarding/gmail-connect/page.tsx
  - apps/web/app/(protected)/onboarding/layout.tsx
  - apps/web/app/(protected)/onboarding/template-select/page.tsx
  - apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx
  - apps/web/components/shell/AppShell.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/components/shell/ChromeHeader.tsx
  - apps/web/components/states/EmptyState.tsx
  - apps/web/components/states/ErrorState.tsx
  - apps/web/components/states/LoadingState.tsx
  - apps/web/e2e/app-shell.spec.ts
  - apps/web/e2e/billing-balance.spec.ts
  - apps/web/e2e/billing-topup.spec.ts
  - apps/web/e2e/byok.spec.ts
  - apps/web/e2e/chrome-test-utils.ts
  - apps/web/e2e/connection-health.spec.ts
  - apps/web/e2e/onboarding-routes.spec.ts
  - apps/web/e2e/pause-toggle.spec.ts
  - apps/web/e2e/privacy-page.spec.ts
  - apps/web/e2e/rules.spec.ts
  - apps/web/e2e/triage-audit.spec.ts
  - apps/web/e2e/triage-shadow-senders.spec.ts
  - apps/web/features/auth/components/AuthTopBar.tsx
  - apps/web/features/billing/api/billing-api.ts
  - apps/web/features/billing/components/BalanceCard.tsx
  - apps/web/features/billing/components/CopyableField.tsx
  - apps/web/features/billing/components/LedgerHistory.tsx
  - apps/web/features/billing/components/LedgerTable.test.tsx
  - apps/web/features/billing/components/LedgerTable.tsx
  - apps/web/features/billing/components/TopupAmountForm.tsx
  - apps/web/features/billing/components/TopupClient.tsx
  - apps/web/features/billing/components/TopupExpired.tsx
  - apps/web/features/billing/components/TopupInstructions.tsx
  - apps/web/features/billing/components/TopupSuccess.tsx
  - apps/web/features/billing/hooks/useBillingBalance.ts
  - apps/web/features/billing/hooks/useCreateTopupIntent.ts
  - apps/web/features/billing/hooks/useLedgerHistory.ts
  - apps/web/features/billing/hooks/useTopupCreditWatch.ts
  - apps/web/features/billing/messages.ts
  - apps/web/features/billing/query-keys.ts
  - apps/web/features/onboarding/components/TemplateCard.tsx
  - apps/web/features/privacy/components/PrivacySections.tsx
  - apps/web/features/privacy/messages.ts
  - apps/web/features/rules/components/RuleList.tsx
  - apps/web/features/rules/components/RulePreviewPanel.tsx
  - apps/web/features/rules/components/RuleTemplateGallery.tsx
  - apps/web/features/shell/messages.ts
  - apps/web/features/triage/api/triage-api.ts
  - apps/web/features/triage/components/AuditCardList.tsx
  - apps/web/features/triage/components/AuditLog.test.tsx
  - apps/web/features/triage/components/AuditLog.tsx
  - apps/web/features/triage/components/AuditTable.tsx
  - apps/web/features/triage/components/PauseBanner.tsx
  - apps/web/features/triage/components/SenderSafetyNetList.test.tsx
  - apps/web/features/triage/components/SenderSafetyNetList.tsx
  - apps/web/features/triage/components/ShadowModeCard.tsx
  - apps/web/features/triage/components/TriagePageClient.tsx
  - apps/web/features/triage/components/UndoButton.tsx
  - apps/web/features/triage/hooks/useOptInSender.ts
  - apps/web/features/triage/hooks/useProtectedSenders.ts
  - apps/web/features/triage/hooks/useShadowMode.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.ts
  - apps/web/features/triage/hooks/useTriageAuditLog.ts
  - apps/web/features/triage/hooks/useTriagePauseState.ts
  - apps/web/features/triage/hooks/useUndoAuditEntry.ts
  - apps/web/features/triage/messages.ts
  - apps/web/features/triage/query-keys.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/package.json
  - apps/web/playwright.config.ts
  - apps/web/scripts/check-i18n.ts
findings:
  critical: 1
  warning: 8
  info: 4
  total: 13
status: issues_found
---

# Phase 05A: Code Review Report

**Reviewed:** 2026-05-12
**Depth:** standard
**Files Reviewed:** 84
**Status:** issues_found

## Summary

Phase 05A wires the protected web app shell, billing top-up flow, triage audit/shadow/sender surfaces, and the onboarding funnel against a partially-implemented backend (several documented GAP stubs). The code is generally clean, i18n parity holds (vi/en leaf-key sets are identical), and the GAP degradation paths are deliberate and well-commented. Findings concentrate on: module-level mutable state shared across the request lifecycle, two React Query observers sharing one cache key with divergent polling configs, unguarded `sessionStorage` writes, hardcoded mock content shipped in the onboarding UI, and a few duplicate-key / type-escape-hatch quality issues.

## Critical Issues

### CR-01: Module-level mutable `shadowModeSnapshot` is shared state (cross-request / stale-read hazard)

**File:** `apps/web/features/triage/api/triage-api.ts:48,88-100`
**Issue:** `let shadowModeSnapshot: ShadowModeState = { ... }` is a module-scoped mutable singleton. `getShadowMode()` returns it and `setShadowMode()` mutates it. Module scope in Next.js is process-wide, not per-request. Today the only caller (`useShadowModeState`) is a `'use client'` hook so it executes in the browser, where the singleton is per-tab — but nothing enforces that, and any future server-side prefetch of `triageKeys.shadowMode()` (the `(app)` layout already prefetches three other queries the same way) would leak one tenant's last-toggled shadow-mode value into another tenant's SSR render. Even client-side, the snapshot can go stale relative to the React Query cache, so a remount reads a value newer/older than `triageKeys.shadowMode()`. The React Query cache already holds the authoritative value via `setQueryData` in `useSetShadowMode`; the snapshot is redundant and unsafe.
**Fix:** Remove `shadowModeSnapshot`. Have `getShadowMode()` return the known default `{ enabled: false, readUnavailable: true }` (the GAP comment already says "starts from a known false default"); let `setShadowMode()` return only the PATCH response. Persist post-write state exclusively through the React Query cache (`useSetShadowMode` already does `setQueryData(triageKeys.shadowMode(), state)`).

```ts
// triage-api.ts — drop the singleton
export async function getShadowMode(): Promise<ShadowModeState> {
  return { enabled: false, readUnavailable: true };
}

export async function setShadowMode(enabled: boolean): Promise<ShadowModeState> {
  const result = await api.PATCH('/api/tenant/triage/shadow-mode', {
    body: { enabled },
    headers: jsonHeaders(),
  });
  const data = unwrap(result, `/api/tenant/triage/shadow-mode failed: ${result.response.status}`);
  return { enabled: data.enabled ?? enabled, readUnavailable: false };
}
```

## Warnings

### WR-01: Two `useQuery` observers share `billingKeys.balance()` with conflicting `refetchInterval`

**File:** `apps/web/features/billing/hooks/useTopupCreditWatch.ts:33-46` + `apps/web/features/billing/hooks/useBillingBalance.ts:11-19`
**Issue:** `useBillingBalance` (mounted permanently via `ChromeHeader`'s `BalancePill`, and again by `BalanceCard`) registers a fixed `refetchInterval: 45_000` on key `['billing','balance']`. On the top-up page, `useTopupCreditWatch` registers a *second* observer on the *same key* with a dynamic `refetchInterval` that returns `false` once credited/expired. React Query keeps a separate refetch timer per observer, so the credit watch's "stop polling once credited" intent is undermined — the other observer keeps refetching, and more importantly the two observers race on `staleTime`/refetch scheduling, making the credited-detection latency nondeterministic. It usually still resolves (balance rises → `isCredited` flips), but the design is fragile.
**Fix:** Either give the credit watch its own key (e.g. `[...billingKeys.balance(), 'topup-watch']`) with its own `queryFn`, or move the "watch" concern into `useBillingBalance` via an options arg and have a single observer. Prefer a dedicated key so the polling lifecycle is self-contained.

### WR-02: Unguarded `sessionStorage` writes can throw and crash the top-up flow

**File:** `apps/web/features/billing/components/TopupClient.tsx:52,64,79`
**Issue:** `readStoredIntentJson` carefully wraps `sessionStorage.getItem` in try/catch, but `restart()`, `handleIntentCreated()`, and `handleCredited()` call `sessionStorage.removeItem(...)` / `sessionStorage.setItem(...)` directly. In private-browsing modes, when storage is disabled, or when the quota is exceeded, these throw `SecurityError`/`QuotaExceededError` synchronously inside a React event handler, breaking the whole interaction (and `setItem` with a large `qrPayload` is a realistic quota case).
**Fix:** Wrap each mutation in a `safeSessionStorage` helper that swallows errors, mirroring `readStoredIntentJson`:

```ts
function safeRemove(key: string) { try { window.sessionStorage.removeItem(key); } catch { /* ignore */ } }
function safeSet(key: string, value: string) { try { window.sessionStorage.setItem(key, value); } catch { /* ignore */ } }
```

### WR-03: Hardcoded mock content shipped in the onboarding "Connect Gmail" UI

**File:** `apps/web/app/(protected)/onboarding/gmail-connect/GmailConnectClient.tsx:63,69,54-56`
**Issue:** The card renders literal `"12,431 messages"` (line 63), `"read · modify · drafts"` (line 69), and shows the **CONNECTED** status badge unconditionally (lines 50-56) regardless of the real Gmail connection state from `me.data`. This is placeholder/prototype data presented to the user as live information. It also evades the i18n hardcoded-EN scanner because `check-i18n.ts` scans the route file `gmail-connect/page.tsx`, not `GmailConnectClient.tsx`. For a product whose core value is "AI users trust with their real inbox", showing fabricated sync counts in the connect screen is a trust regression.
**Fix:** Drive these from real data (tenant status / message-count API) or remove the stat rows until the data exists. Move the prose strings into the i18n bundle, and add `GmailConnectClient.tsx` (and the other onboarding `*Client.tsx` files) to `EN_SCAN_FILES` in `check-i18n.ts` so the scanner covers them.

### WR-04: Duplicate React keys when sender email is missing

**File:** `apps/web/features/triage/components/SenderSafetyNetList.tsx:65`
**Issue:** `key={sender.senderEmail ?? 'unknown-sender'}` — if the backend returns two protected senders with no `senderEmail` (both nullable per the generated type), React gets duplicate keys, causing reconciliation bugs (wrong row state, lost focus). The `useOptInSender` optimistic update also matches senders by `senderEmail`, so a null email there silently no-ops.
**Fix:** Use the array index as a fallback discriminator: `key={sender.senderEmail ?? \`sender-${index}\`}` (and map with `(sender, index)`), or filter out senders without an email before rendering.

### WR-05: Optimistic `setQueryData` immediately followed by `invalidateQueries` on the same key

**File:** `apps/web/features/triage/hooks/useOptInSender.ts:14-25` and `apps/web/features/triage/hooks/useShadowMode.ts:18-21`
**Issue:** Both `onSuccess` handlers call `queryClient.setQueryData(key, ...)` and then `await queryClient.invalidateQueries({ queryKey: key })`. The invalidate triggers an immediate refetch that overwrites the value just set, so the `setQueryData` work is dead code on the happy path (and for shadow-mode, `getShadowMode` returns a stale snapshot — see CR-01 — so the refetch can actually *regress* the UI back to `enabled: false`).
**Fix:** Pick one strategy. Either trust the mutation response (`setQueryData` only, no invalidate) or invalidate only (drop the `setQueryData`). Given the shadow-mode read endpoint doesn't exist, prefer `setQueryData`-only there.

### WR-06: `getNextPageParam` references a field that the page type never populates

**File:** `apps/web/features/billing/hooks/useLedgerHistory.ts:18` (with `apps/web/features/billing/api/billing-api.ts:18-24`)
**Issue:** `getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined`, but `LedgerHistoryPage` is `LedgerHistoryUnavailablePage` whose `nextCursor` is the literal `null` — so this always returns `undefined`. Harmless today, but it diverges from `useTriageAuditLog`, which correctly short-circuits on `lastPage.unavailable`. When the real ledger endpoint lands, a developer copying the audit-log pattern will expect the `unavailable` guard here and it won't be present.
**Fix:** Mirror `useTriageAuditLog`: `getNextPageParam: (lastPage) => (lastPage.unavailable ? undefined : (lastPage.nextCursor ?? undefined))`.

### WR-07: `isLoading` vs `isPending` used inconsistently for the same query

**File:** `apps/web/components/shell/ChromeHeader.tsx:110` and `apps/web/features/billing/components/BalanceCard.tsx:48`
**Issue:** `BalancePill` branches on `balance.isLoading` while `BalanceCard` branches on `balance.isPending` for the identical `useBillingBalance()` query. With the SSR prefetch + `HydrationBoundary` in the `(app)` layout, the cache is hydrated, so `isPending` is `false` immediately but `isLoading` (`isPending && isFetching`) can flicker `true` during the post-hydration background refetch — producing a skeleton in the header while the card shows real data. Inconsistent loading semantics on the same data source.
**Fix:** Standardize on `isPending` (no data yet) for the "show skeleton" decision in both components.

### WR-08: `MessageRef` builds a Gmail deep link from a message ID using the thread-fragment format

**File:** `apps/web/features/triage/components/AuditRow.tsx:71-79`
**Issue:** `https://mail.google.com/mail/u/0/#inbox/${gmailMessageId}` — the `#inbox/<id>` fragment expects a *thread* ID; passing a Gmail *message* ID often fails to open the conversation (Gmail falls back to the inbox or an empty view). Also `u/0` hardcodes the first signed-in Google account, which won't match the tenant's account for multi-account users. This is a user-facing dead/wrong link on the audit log.
**Fix:** Either link via search (`#search/rfc822msgid:<Message-Id>`) if the RFC822 ID is available, or use `#all/<threadId>` once the backend exposes the thread ID; drop the `u/0` segment or resolve the correct account index.

## Info

### IN-01: `as never` type-escape for translation keys

**File:** `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx:84-85`
**Issue:** `t(tpl.titleKey as never)` / `t(tpl.descKey as never)` defeats next-intl's typed-key checking, so a typo in `templates[].titleKey` won't be caught at compile time.
**Fix:** Type `titleKey`/`descKey` as `MessageKeys` (or `Parameters<typeof t>[0]`) on the `templates` array literal so the cast is unnecessary.

### IN-02: AppSidebar logo links to `/rules` instead of a home/dashboard route

**File:** `apps/web/components/shell/AppSidebar.tsx:67`
**Issue:** The brand logo's `href="/rules"` is non-obvious — most users expect the logo to go to a dashboard/triage landing. Not a bug, but worth confirming it's intentional.
**Fix:** If intentional, leave a comment; otherwise point it at `/triage` (the first nav item) or a dedicated dashboard.

### IN-03: `getAuditLog` ignores its `cursor` argument

**File:** `apps/web/features/triage/api/triage-api.ts:80-83`
**Issue:** `void options;` discards the `cursor` that `useTriageAuditLog` passes via `pageParam`. This is deliberate (the endpoint doesn't exist yet) and well-commented, so it's fine — flagging only so the GAP is not forgotten when the real endpoint lands.
**Fix:** None now; when implementing, thread `options.cursor` into the request.

### IN-04: `LedgerHistory` renders a `sr-only` empty `LedgerTable` purely as a structural placeholder

**File:** `apps/web/features/billing/components/LedgerHistory.tsx:62-64`
**Issue:** A visually-hidden `<LedgerTable rows={[]} />` is mounted alongside the `EmptyState` — presumably so `data-testid="ledger-table"` is queryable by e2e tests even when empty. This couples production markup to test selectors and ships an always-empty table to screen-reader users (it's `sr-only`, so SR users will hear an empty table). Low impact.
**Fix:** Drop the hidden table; assert on the `EmptyState`/`ledger-unavailable-panel` testids in e2e instead.

---

_Reviewed: 2026-05-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
