# Phase 5A: User Surface — Web UI Core - Pattern Map

**Mapped:** 2026-05-12
**Files analyzed:** ~45 new/modified `apps/web` files
**Analogs found:** strong analog for ~40 / weak-or-none for ~5 (the three OpenAPI-gap surfaces + the shell host + shared `components/states/`)

> Scope note: 5A is **frontend-only** in `apps/web` (Next 16 App Router, React 19, `openapi-fetch`, TanStack Query v5, shadcn `base-nova`, Tailwind v4, `next-intl` vi/en, Phase 1.6 tokens). Every pattern below is an *existing* `apps/web` file. Read `node_modules/next/dist/docs/` before writing Next code (`apps/web/AGENTS.md`). Invoke the `frontend-design` skill before any UI and pass that rule into executor subagents.

---

## File Classification

### Route layouts / pages

| New/Modified File | Role | Data flow | Closest Analog | Match |
|---|---|---|---|---|
| `app/(protected)/layout.tsx` | route layout (rewrite → shell host) | RSC prefetch + dehydrate + i18n provider | itself (current thin version) + `features/account/api/account-api.ts` (`getCurrentUserCached`) | self-rewrite + role-match |
| `app/(protected)/onboarding/layout.tsx` | route layout (new, minimal/chrome-suppressed) | passthrough wrapper | `app/(public)/layout.tsx`, `app/(auth)/layout.tsx` | role-match |
| `app/(protected)/triage/page.tsx` | page (new) — `<Suspense>` → client `?tab=` reader | request-response → client search-param routing | `app/(protected)/rules/page.tsx` (thin page → feature workspace) | role-match |
| `app/(protected)/billing/page.tsx` | page (new) — balance + ledger (ledger gap-flagged) | request-response | `app/(protected)/rules/page.tsx` | role-match |
| `app/(protected)/billing/top-up/page.tsx` | page (new) — `<Suspense>` → client `?intentId=`/`?code=` reader | request-response + polling | `app/(protected)/rules/page.tsx` (page shell) + `e2e/onboarding-routes.spec.ts` for the multi-step funnel mental model | partial |
| `app/(protected)/settings/privacy/page.tsx` | page (new) — static i18n copy | request-response (no data) | `app/(public)/privacy/page.tsx`, `app/(protected)/settings/page.tsx` (Card-chain layout) | role-match |
| `app/(protected)/rules/page.tsx`, `settings/page.tsx`, `onboarding/*/page.tsx` | pages (modified — convergence only) | unchanged | themselves | self |

### Feature `api/` modules

| New/Modified File | Role | Data flow | Closest Analog | Match |
|---|---|---|---|---|
| `features/triage/api/triage-api.ts` (extend) | feature api module | CRUD over typed client | `features/rules/api/rules-api.ts` (full GET/POST/PUT/PATCH/DELETE + `unwrap`) | exact |
| `features/billing/api/billing-api.ts` (new) | feature api module | CRUD over typed client | `features/rules/api/rules-api.ts`; for the `getLedgerHistory`/intent-status **gaps** there is no analog (endpoints don't exist) | exact (for what exists) |

### Query-key factories

| New/Modified File | Role | Closest Analog | Match |
|---|---|---|---|
| `features/triage/query-keys.ts` (new) | query-key factory | `features/rules/query-keys.ts`, `features/gmail/query-keys.ts`, `features/account/query-keys.ts` | exact |
| `features/billing/query-keys.ts` (new) | query-key factory | `features/rules/query-keys.ts` | exact |

### TanStack hooks

| New/Modified File | Role | Data flow | Closest Analog | Match |
|---|---|---|---|---|
| `features/triage/hooks/useTriagePauseState.ts` (new — read) | read hook | `useQuery`, invalidate-only | `features/gmail/hooks/useTenantStatus.ts`, `features/account/hooks/useCurrentUser.ts` | exact |
| `features/triage/hooks/useToggleTriagePause.ts` (rewrite — optimistic) | mutation hook | `useMutation` w/ optimistic `onMutate`/`onError`/`onSettled` | `features/rules/hooks/use-rules.ts` → `useReorderRules` (cancel/snapshot/setQueryData/restore/invalidate) | exact |
| `features/triage/hooks/useUndoAuditEntry.ts` (new) | mutation hook | `useMutation` + invalidate on success | `features/rules/hooks/use-rules.ts` → `useDeleteRule` | exact |
| `features/triage/hooks/useShadowMode.ts` (new) | read+write pair | `useQuery` + `useMutation` | `features/llm/hooks/use-byok.ts` (read+write pair w/ co-located key factory) | role-match |
| `features/triage/hooks/useProtectedSenders.ts` (new) | read hook | `useQuery` (list) | `features/rules/hooks/use-rules.ts` → `useRules` | exact |
| `features/triage/hooks/useOptInSender.ts` (new) | mutation hook | `useMutation` + invalidate list | `features/rules/hooks/use-rules.ts` → `useUpdateRuleEnabled` | exact |
| `features/triage/hooks/useTriageAuditLog.ts` (new) | read hook — `useInfiniteQuery` | **BLOCKED** (no list endpoint) | no `useInfiniteQuery` analog exists in repo — closest shape: `useRules` (list); `useInfiniteQuery` API per Context7 | partial |
| `features/billing/hooks/useBillingBalance.ts` (new) | read hook w/ `refetchInterval≈45s`, `staleTime≈30s` | polling read | `features/gmail/hooks/useTenantStatus.ts` (base shape) — but it has **no** poll/staleTime override; add per D-11 | role-match |
| `features/billing/hooks/useCreateTopupIntent.ts` (new) | mutation hook | `useMutation` | `features/llm/hooks/use-byok.ts` → `useSaveByok` (bare `useMutation`) | exact |
| `features/billing/hooks/useTopupCreditWatch.ts` (new) | read hook — poll balance until credited/expired | polling-with-stop | `features/gmail/hooks/useTenantStatus.ts` base + `refetchInterval` callback (Context7 TanStack) | partial |
| `features/billing/hooks/useLedgerHistory.ts` (new) | read hook — `useInfiniteQuery` | **BLOCKED** (no ledger endpoint) | same as `useTriageAuditLog` | partial |

### Components

| New/Modified File | Role | Data flow | Closest Analog | Match |
|---|---|---|---|---|
| `components/shell/AppShell.tsx` (new, "use client") | layout shell | client, hosts SidebarProvider + Toaster | no analog — `app/(public)/layout.tsx` shows the provider-nesting idiom; `e2e/login-shell.spec.ts` exercises the public chrome | none (novel) |
| `components/shell/AppSidebar.tsx` (new, "use client") | nav component | client, `usePathname()` active state | `features/landing/components/TopBar.tsx` (active-link nav idiom) | partial |
| `components/shell/ChromeHeader.tsx` (new, "use client") | chrome widgets host | client, consumes 3 TanStack hooks | `app/(protected)/settings/page.tsx` (composes `useCurrentUser`+`useTenantStatus`+`useToggleTriagePause`+`ReconnectPromptGate`+`ConnectionHealthBadge` exactly the way the chrome must) | role-match |
| `components/states/{LoadingState,EmptyState,ErrorState}.tsx` (new) | shared UI | presentational | does NOT exist — consolidation source = the ad-hoc skeleton/empty markup inside `features/rules/components/RuleList.tsx` (`isLoading ? skeleton : rules.length===0 ? empty : list`) and `RulePreviewPanel.tsx` | partial (extract from existing) |
| `features/triage/components/PauseBanner.tsx` (rebase) | feature component | reads shared query key | itself (currently reads `useCurrentUser().triagePaused` → rebase onto `useTriagePauseState()`) + uses `Alert` (`components/ui/alert.tsx`) | self |
| `features/triage/components/{AuditLog,AuditTable,AuditCardList,AuditRow,UndoButton}.tsx` (new) | feature components — responsive hybrid renderer | list render + per-row mutation | `features/rules/components/RuleList.tsx` (list w/ per-row icon actions, Badge, Tooltip, Dialog confirm — the closest existing "evidence list with row actions") | role-match |
| `features/triage/components/ShadowModeCard.tsx` (new) | feature component — toggle + confirm | toggle + confirm dialog | `app/(protected)/settings/page.tsx` "Automated triage" Card (toggle inside a Card) + `RuleList.tsx` Dialog-confirm idiom | role-match |
| `features/triage/components/{SenderSafetyNetList,SenderRow}.tsx` (new) | feature components — list + per-row opt-in | list + per-row mutation | `features/rules/components/RuleList.tsx` | role-match |
| `features/billing/components/{BalanceCard,LedgerHistory,LedgerTable,TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired}.tsx` (new) | feature components | mixed (display / form / list / poll-driven) | `app/(protected)/settings/page.tsx` Card chains; `features/rules/components/RuleComposer.tsx` for the form-with-submit idiom; `RuleList.tsx` for `LedgerTable` row model; `ReconnectPrompt.tsx` for `Alert`-based `TopupExpired` | role-match |
| `features/gmail/components/ReconnectPrompt.tsx`, `ConnectionHealthBadge.tsx` | reused as-is in chrome | — | themselves | reuse |

### i18n / config / tests

| New/Modified File | Role | Closest Analog | Match |
|---|---|---|---|
| `features/triage/messages.ts` (extend), `features/billing/messages.ts` (new), `features/privacy/messages.ts` (new) | i18n message bundle | `features/rules/messages.ts`, `features/llm/messages.ts` | exact |
| `apps/web/i18n/messages/{vi,en}.json` | i18n bundles (generated/merged) | merged by `scripts/merge-feature-i18n.ts` | exact |
| `scripts/check-i18n.ts` (extend `EN_SCAN_FILES`) | config | itself (existing `EN_SCAN_FILES` array — add every new `app/(protected)/**/page.tsx` + `features/**/components/*.tsx`) | self |
| `playwright.config.ts` (modify — add 320px project?) | config | itself — currently only a `chromium` Desktop project; 320px is done per-spec via `page.setViewportSize({ width: 320 })` (see `e2e/mobile-topbar.spec.ts`), so a new project is optional | self |
| `e2e/triage.spec.ts`, `billing.spec.ts`, `topup.spec.ts`, `shell.spec.ts`, `privacy.spec.ts` (new) | Playwright specs | `e2e/rules.spec.ts` (full pattern: serial mode, `page.route('http://localhost:8080/**')` in-memory mock incl. `/me`, session+locale cookies, golden path), `e2e/mobile-topbar.spec.ts` (320px viewport pattern) | exact |
| `__tests__/**` or co-located `*.test.tsx` for new hooks | Vitest specs | `features/triage/hooks/useToggleTriagePause.test.tsx` (mocks `@tanstack/react-query` + the api module, `renderHook`+`act`), `features/llm/components/ByokForm.test.tsx`, `features/gmail/components/ReconnectPrompt.test.tsx` | exact |

---

## Pattern Assignments

### `app/(protected)/layout.tsx` (route layout — rewrite to shell host)

**Current state (the thing being replaced):**
```tsx
// app/(protected)/layout.tsx — TODAY
import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages } from 'next-intl/server';
import { PauseBanner } from '@/features/triage/components/PauseBanner';
import { QueryProvider } from '@/lib/query-client';

export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();
  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <QueryProvider>
        <PauseBanner />
        {children}
      </QueryProvider>
    </NextIntlClientProvider>
  );
}
```
Keep the `NextIntlClientProvider` + `QueryProvider` nesting. **Add:** `await cookies()` → read `sidebar_state`; a throwaway `new QueryClient()` + `Promise.all([qc.prefetchQuery(...)×3])` + `dehydrate(qc)`; wrap a new `"use client"` `<AppShell defaultSidebarOpen={...}>{children}` in `<HydrationBoundary state={...}>`. The `PauseBanner` moves *into* the shell subtree (chrome / page region), not the layout root. (D-01, D-10. TanStack #8479 — prefetched chrome queries must be consumed inside `<AppShell>`, not a deeper page boundary.)

**Server cache() fetch idiom to mirror** — `features/account/api/account-api.ts`:
```ts
export const getCurrentUserCached = cache(async (cookieHeader: string | undefined): Promise<CurrentUser> => {
  if (cookieHeader === undefined) return fetchCurrentUser();
  return fetchCurrentUser({ headers: { cookie: cookieHeader } });
});
// RSC callers MUST pass (await cookies()).toString() — cache() keys by primitive value.
```

---

### `features/triage/api/triage-api.ts` (extend) & `features/billing/api/billing-api.ts` (new)

**Analog:** `features/rules/api/rules-api.ts` — copy the `unwrap` helper and the per-call shape verbatim.

```ts
import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

function jsonHeaders(): HeadersInit { return { 'Content-Type': 'application/json', ...xsrfHeader() }; }
function unsafeHeaders(): HeadersInit { return { ...xsrfHeader() }; }

function unwrap<T>(result: { data?: T; error?: unknown; response: Response }, fallbackMessage: string): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);   // throw the typed ApiError so hooks switch on error.code
  }
  return result.data;
}

export async function listRules(): Promise<RuleListResponse> {
  const result = await api.GET('/api/rules', {});
  return unwrap(result, `/api/rules list failed: ${result.response.status}`);
}
export async function deleteRule(ruleId: string): Promise<void> {
  const result = await api.DELETE('/api/rules/{ruleId}', { params: { path: { ruleId } }, headers: unsafeHeaders() });
  if (result.error || !result.response.ok) throw result.error ?? new Error(`/api/rules/${ruleId} delete failed: ${result.response.status}`);
}
```
**Exact paths to use** (verified present in `lib/api/schema.d.ts`): `/tenant/triage-pause` (PUT — bare prefix), `/api/tenant/triage/shadow-mode`, `/api/triage/audit/{auditId}/undo` (POST), `/api/triage/sender-safety-net` (GET), `/api/triage/sender-safety-net/{senderEmail}/opt-in` (POST), `/api/billing/balance` (GET), `/api/billing/topup/intent` (POST).
**GAPS (verified absent):** triage-audit **list** endpoint, billing **ledger/transaction-history** list endpoint, top-up **intent-status** poll endpoint / `intentId` field, QR **image URL** (only a raw `qrPayload` EMV string). Plan these as "blocked-on-backend" sub-tasks per SPEC out-of-scope rule — do **not** add endpoints or regenerate `schema.d.ts`.

The existing tiny `features/triage/api/triage-api.ts` (only `setTriagePaused`) stays; extend it:
```ts
export async function setTriagePaused(paused: boolean): Promise<void> {
  const { error, response } = await api.PUT('/tenant/triage-pause', {
    body: { paused }, headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok) throw error ?? new Error(`/tenant/triage-pause failed: ${response.status}`);
}
```

---

### `features/triage/query-keys.ts` & `features/billing/query-keys.ts` (new)

**Analog:** `features/rules/query-keys.ts` / `features/gmail/query-keys.ts` — `as const` nested factory:
```ts
export const rulesKeys = {
  all: ['rules'] as const,
  list: () => [...rulesKeys.all, 'list'] as const,
  detail: (ruleId: string) => [...rulesKeys.all, 'detail', ruleId] as const,
  templates: () => [...rulesKeys.all, 'templates'] as const,
} as const;
```
→ `triageKeys = { all:['triage'], pauseState(), auditLog(), shadowMode(), protectedSenders() }`; `billingKeys = { all:['billing'], balance(), ledger(), topupIntent(code: string) }`. D-13: `triageKeys.pauseState()` is the **single** key for pause — chrome toggle, settings toggle, `PauseBanner` all read it.

---

### `features/triage/hooks/useTriagePauseState.ts` (new — read hook)

**Analog:** `features/gmail/hooks/useTenantStatus.ts` / `features/account/hooks/useCurrentUser.ts`:
```ts
'use client';
import { useQuery } from '@tanstack/react-query';
import { getTenantStatus } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';
export function useTenantStatus() {
  return useQuery({ queryKey: gmailQueryKeys.status(), queryFn: ({ signal }) => getTenantStatus({ signal }) });
}
```
Invalidate-only, no polling (D-12). Source the paused boolean from `/me` (`triagePaused`) or a dedicated pause read — but stored under `triageKeys.pauseState()`.

### `features/triage/hooks/useToggleTriagePause.ts` (rewrite — optimistic, D-13)

**Analog:** `features/rules/hooks/use-rules.ts` → `useReorderRules` (the only existing full optimistic-update hook). Copy its `onMutate`/`onError`/`onSettled` skeleton, classic `useQueryClient()` + 3-arg-callback form (matches the rest of the repo; do **not** adopt the v5.90 4-arg `context.client` form):
```ts
const queryClient = useQueryClient();
return useMutation({
  mutationFn: ({ entries }: ReorderRulesInput) => reorderRules({ entries }),
  onMutate: async ({ orderedRules }) => {
    await queryClient.cancelQueries({ queryKey: rulesKeys.list() });
    const previousList = queryClient.getQueryData<RuleListResponse>(rulesKeys.list());
    queryClient.setQueryData<RuleListResponse>(rulesKeys.list(), (currentList) => { /* ...next... */ });
    return { previousList };
  },
  onError: (_error, _variables, context) => {
    if (context?.previousList) queryClient.setQueryData(rulesKeys.list(), context.previousList);
  },
  onSettled: async () => { await queryClient.invalidateQueries({ queryKey: rulesKeys.list() }); },
});
```
Adapt: key = `triageKeys.pauseState()`, value = `boolean`; `onSettled` also `invalidateQueries({ queryKey: billingKeys.balance() })` (D-13). Drop the current `onSuccess → invalidate accountQueryKeys.me()` body.
**Update the test** `useToggleTriagePause.test.tsx` in lockstep — it currently asserts `invalidateQueries({ queryKey: accountQueryKeys.me() })`.

### `features/triage/hooks/useUndoAuditEntry.ts`, `useOptInSender.ts`, `useShadowMode.ts` mutation (new)

**Analog:** `use-rules.ts` → `useDeleteRule` / `useUpdateRuleEnabled` (mutation + invalidate-on-success):
```ts
export function useDeleteRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (ruleId: string) => deleteRule(ruleId),
    onSuccess: async (_data, ruleId) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({ queryKey: rulesKeys.detail(ruleId) });
    },
  });
}
```
`useUndoAuditEntry` `onSuccess` → invalidate `triageKeys.auditLog()` + `billingKeys.balance()` + `sonner` toast (D-18). `useOptInSender` → invalidate `triageKeys.protectedSenders()`. `useShadowMode` write → invalidate `triageKeys.shadowMode()`.

### `features/billing/hooks/useBillingBalance.ts` (new — polled read, D-11)

**Base analog:** `useTenantStatus`. **Add per D-11/Pitfall 5:** `refetchInterval: 45_000`, `refetchIntervalInBackground: false`, `staleTime: 30_000` (the global `QueryProvider` `staleTime` is 5 min — `lib/query-client.tsx` — so this override is mandatory). Plus callers `invalidateQueries({ queryKey: billingKeys.balance() })` after billable actions / top-up settle / pause toggle.

### `features/billing/hooks/useCreateTopupIntent.ts` (new — bare mutation)

**Analog:** `features/llm/hooks/use-byok.ts`:
```ts
export function useSaveByok() { return useMutation({ mutationFn: saveByok }); }
```
Also note `use-byok.ts` co-locates its key factory (`byokKeys`) in the hook file — that's an accepted variant, but for triage/billing put the factory in `query-keys.ts` per convention 8 (these features own cached data).

### `useTriageAuditLog.ts` / `useLedgerHistory.ts` (new — `useInfiniteQuery`, BLOCKED)

No `useInfiniteQuery` exists anywhere in the repo. Shape per Context7 TanStack docs (`getNextPageParam` / `initialPageParam`). Until the backend list endpoints exist, these are gap-flagged stubs; render the screens with the empty/error states (`components/states/`) and the data the screen *can* get (undo flow against `/api/triage/audit/{auditId}/undo` only).

---

### `components/shell/ChromeHeader.tsx` (new — chrome widgets)

**Analog:** `app/(protected)/settings/page.tsx` — it already composes the exact hook set the chrome needs:
```tsx
const me = useCurrentUser();
const status = useTenantStatus();
const disconnect = useDisconnectGmail();
const togglePause = useToggleTriagePause();
// ...
<ConnectionHealthBadge status={connStatus} />
<ReconnectPromptGate status={connStatus} ingestionHealth={ingestionHealth} onReconnect={reconnect} />
// pause toggle (custom switch markup w/ amber tokens):
<button type="button" aria-pressed={triagePaused} aria-label={t('settings.triage.pause.toggleLabel')}
  disabled={!me.data || togglePause.isPending} onClick={() => togglePause.mutate(!triagePaused)}
  className={cn('relative inline-flex h-6 w-11 ...', triagePaused ? 'border-warning bg-warning' : 'border-border bg-muted')}>
  <span className={cn('bg-background size-5 rounded-full ...', triagePaused ? 'translate-x-5' : 'translate-x-0')} />
</button>
const reconnect = () => { window.location.href = getApiUrl('/tenant/connect-gmail'); };
```
For 5A: replace the hand-rolled switch markup with the shadcn `switch` primitive (D-03), wrap the pause-OFF transition in an `alert-dialog` confirm (UI-SPEC Copywriting), `badge` for the balance pill, `tooltip`-wrapped colored dot for health, `dropdown-menu` for the user menu, and re-read pause from `useTriagePauseState()` not `me.data.triagePaused`. Reuse `ReconnectPrompt`/`ReconnectPromptGate` and `ConnectionHealthBadge` unchanged.

### `components/shell/AppSidebar.tsx` (new — flat nav, active = `usePathname()`)

**Analog (idiom only):** `features/landing/components/TopBar.tsx` for the active-link nav pattern; otherwise built from the shadcn `sidebar` block (`SidebarProvider`/`Sidebar collapsible="icon"`/`SidebarInset`/`SidebarTrigger`/`SidebarMenu`/`SidebarMenuItem`/`SidebarMenuButton`/`useSidebar`). Flat `SidebarMenu` only — no `SidebarMenuSub` (D-02, shadcn #5874). The `sidebar_state` cookie name is exported from the installed `components/ui/sidebar.tsx` as `SIDEBAR_COOKIE_NAME` — read it there after `shadcn add`, don't hard-code.

### `components/states/{LoadingState,EmptyState,ErrorState}.tsx` (new — extract & consolidate)

No analog exists. **Consolidation source** — the ad-hoc trio inside `features/rules/components/RuleList.tsx`:
```tsx
{isLoading ? (
  <div className="space-y-2">
    <div className="bg-muted h-16 animate-pulse rounded-lg" />
    <div className="bg-muted h-16 animate-pulse rounded-lg" />
  </div>
) : rules.length === 0 ? (
  <div className="rounded-lg border border-dashed p-4">
    <p className="font-medium">{t('rules.list.empty.heading')}</p>
    <p className="text-muted-foreground mt-1 text-sm">{t('rules.list.empty.body')}</p>
  </div>
) : ( /* list */ )}
```
Promote to: `<LoadingState>` (shadcn `Skeleton` rows/cards — `components/ui/skeleton.tsx` already installed), `<EmptyState heading body cta?>`, `<ErrorState heading body onRetry>` (the "Try again" button re-runs the query — UI-SPEC Copywriting). Then the rules/settings/onboarding convergence pass swaps the ad-hoc markup for these.

### `features/triage/components/{AuditTable,AuditCardList,AuditRow,UndoButton}.tsx` (new — responsive evidence list)

**Analog:** `features/rules/components/RuleList.tsx` — closest existing "list of records, each with badges + per-row actions + a Dialog confirm". Reuse its idioms: `<Badge variant=...>` for action chips, `<TooltipProvider>`/`<Tooltip>`/`<TooltipTrigger render={<Button .../>}>` for tooltip'd buttons, the `Dialog`+`DialogTrigger`+`DialogContent`+`DialogFooter`+`DialogClose` confirm pattern (for 5A use `alert-dialog` instead per UI-SPEC). Add: a `Table` (shadcn) renderer at `≥ md` + a card-list renderer below `md`, sharing one `AuditRow` row model; the **Reason** field full-text on cards/320px (D-16); a muted "Undo window closed" + `tooltip` past 30 days (D-18); a mono+muted 30-day boundary divider.

### `features/billing/components/*` (new)

`BalanceCard` — Card-chain from `settings/page.tsx`, balance figure in Display type. `TopupAmountForm` — form-with-submit idiom from `features/rules/components/RuleComposer.tsx`. `CopyableField` — small new primitive (copy button + "Copied" feedback) — rule-of-three may apply across account/code/amount; otherwise inline. `TopupExpired` — `Alert variant="warning"` like `features/gmail/components/ReconnectPrompt.tsx`:
```tsx
<Alert variant="warning">
  <AlertTitle>{t('connectionHealth.disconnected')}</AlertTitle>
  <AlertDescription>{t('connectionHealth.reconnectPrompt')}</AlertDescription>
  <AlertAction><button type="button" onClick={onReconnect} className={cn(buttonVariants({ variant:'outline', size:'sm' }))}>{t('settings.gmailConnection.reconnectCta')}</button></AlertAction>
</Alert>
```
`LedgerTable` — `RuleList.tsx` row model + shadcn `Table`. QR: render `qrPayload` (raw EMV string) via a small MIT QR component (`react-qr-code` ~ dependency-light) **or** show only the copyable bank fields + payload — adding a runtime dep needs a planner note (verify version on npm first).

### `features/triage/components/PauseBanner.tsx` (rebase, not rewrite)

Currently:
```tsx
const { data: user } = useCurrentUser();
if (!user?.triagePaused) return null;
// <Alert variant="warning" role="alert"> ... togglePause(false) ...
```
→ swap `useCurrentUser().triagePaused` for `useTriagePauseState()`. Keep the `Alert`/`AlertTitle`/`AlertDescription` markup and the `useToggleTriagePause()` write hook. This is the D-13 correctness contract — no local `useState`, no ad-hoc query keys.

### `app/(protected)/triage/page.tsx` & `billing/top-up/page.tsx` (new — `<Suspense>` + search-param reader)

**Analog (thin page → feature):** `app/(protected)/rules/page.tsx`:
```tsx
import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';
export default function RulesPage() {
  return <main className="mx-auto w-full max-w-6xl p-4 md:p-6"><RulesWorkspace /></main>;
}
```
For 5A: `export default function TriagePage() { return <Suspense fallback={<LoadingState/>}><TriagePageClient/></Suspense>; }` — `TriagePageClient` is `"use client"`, reads `useSearchParams().get('tab')`, drives shadcn `Tabs`, `router.replace('/triage?tab='+v, { scroll:false })` (D-06). Same for `top-up/page.tsx` reading `?intentId=`/`?code=` (D-15). `useSearchParams()` must be inside `<Suspense>` in Next 16 — verify exact rule in `node_modules/next/dist/docs/`.

### `app/(protected)/settings/privacy/page.tsx` (new — static i18n copy)

**Analog:** `app/(public)/privacy/page.tsx` for structure; `app/(protected)/settings/page.tsx` Card-chain for the in-shell section layout. Copy contract in `05A-UI-SPEC.md` §Copywriting (three mandatory points: no-stored-bodies, no-auto-send, BYOK) + a link to the untouched public `/privacy`. Note D-08: it's a `/settings/privacy` *segment* (its own route), not a top-level `(protected)/privacy` (would collide with the public path).

---

### i18n: `features/<feature>/messages.ts` (new/extend)

**Analog:** `features/rules/messages.ts` — flat `Record<key, { vi, en }>` exported `as const`:
```ts
export const rulesMessages = {
  'rules.page.title': { vi: 'Quy tắc', en: 'Rules' },
  'rules.list.empty.heading': { vi: 'Chưa có quy tắc', en: 'No rules yet' },
  // ... errors.* keys live here too
} as const;
```
`merge-feature-i18n.ts` finds every `features/**/messages.ts` and merges into `i18n/messages/{vi,en}.json`. New namespaces: `nav.*`, `shell.*`, `triage.*` (extend the existing `features/triage/messages.ts`), `billing.*`, `privacy.*`. Author vi + en in lockstep; `pnpm i18n:check` must pass.

### config: `scripts/check-i18n.ts` — extend `EN_SCAN_FILES`

It's an explicit array (currently lists every `app/(protected)/**/page.tsx`, error boundaries, and `features/**/components/*.tsx` that contain prose). **Add** every new file: `app/(protected)/triage/page.tsx`, `app/(protected)/billing/page.tsx`, `app/(protected)/billing/top-up/page.tsx`, `app/(protected)/settings/privacy/page.tsx`, `app/(protected)/layout.tsx` (if it gains prose), all new `components/shell/*.tsx`, `components/states/*.tsx`, and all new `features/{triage,billing}/components/*.tsx`. STRICT lint-staged gate — a missed file fails CI.

---

### Playwright specs: `e2e/{triage,billing,topup,shell,privacy}.spec.ts` (new)

**Analog:** `e2e/rules.spec.ts` — the canonical pattern:
- `test.describe.configure({ mode: 'serial' })`
- `page.route('http://localhost:8080/**', async (route) => { ... })` — in-memory mock keyed off `url.pathname` + `request.method()`; **always mock `/me`** (returns `triagePaused`, `gmailConnectionStatus`); `fulfillJson` / `fulfillProblem` (problem+json with a `code`) helpers
- session + locale cookies before navigation:
  ```ts
  await page.context().addCookies([
    { name: 'ZEROMAIL_SESSION', value: 'playwright-session', domain: 'localhost', path: '/', httpOnly: true, sameSite: 'Lax', secure: false },
    { name: 'NEXT_LOCALE', value: 'en', domain: 'localhost', path: '/', sameSite: 'Lax', secure: false },
  ]);
  ```
- `await page.goto('/rules', { waitUntil: 'domcontentloaded' }); await page.waitForLoadState('networkidle');`
- assertions via `getByRole`/`getByText`/`getByLabel`; horizontal-overflow check: `await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)` expect `false`.

**320px analog:** `e2e/mobile-topbar.spec.ts` — `await page.setViewportSize({ width: 320, height: 740 })` then assert key chrome elements visible. Cover golden path + key states (audit 0/1/page-full; undo in-window vs out-of-window; pause toggle persists; balance updates after simulated credit; health CONNECTED vs DISCONNECTED; shadow toggle; sender opt-in; top-up intent→instructions→credited→success; ledger empty+populated) on desktop AND 320px.

### Vitest specs: co-located `*.test.tsx` for new hooks

**Analog:** `features/triage/hooks/useToggleTriagePause.test.tsx` — `vi.hoisted` mocks for the api module + `@tanstack/react-query` (mock `useMutation`/`useQueryClient`), `renderHook` + `act`, assert the api fn was called and the right `invalidateQueries({ queryKey })` fired. Also `features/llm/components/ByokForm.test.tsx`, `features/gmail/components/ReconnectPrompt.test.tsx` for component tests. Note the "plain DOM `<button>` instead of shadcn `Button`" workaround comment in `ReconnectPrompt.tsx` (vitest @base-ui useRef boundary) — relevant if a new chrome control needs unit testing.

---

## Shared Patterns

### Typed API call
**Source:** `features/rules/api/rules-api.ts` (`unwrap`, `jsonHeaders`, `unsafeHeaders`), `lib/api/client.ts` (`api`, `xsrfHeader`).
**Apply to:** every new `features/*/api/*-api.ts`. Never ad-hoc `fetch` to backend routes. Mutating calls include `...xsrfHeader()` + `'Content-Type': 'application/json'`. Throw the structured `error` (typed `ApiError`) so hooks switch on `error.code`. Use the exact path string from `schema.d.ts` (mixed `/api/...` vs bare `/me`,`/tenant/...`,`/gmail/...` prefixes).

### Query-key factory
**Source:** `features/rules/query-keys.ts` / `features/gmail/query-keys.ts`.
**Apply to:** every feature that owns cached data (triage, billing). `as const` nested factory; one canonical key per cached resource (D-13 for pause).

### Optimistic mutation
**Source:** `features/rules/hooks/use-rules.ts` → `useReorderRules`.
**Apply to:** `useToggleTriagePause` (D-13). `cancelQueries` → snapshot → `setQueryData(next)` → `onError` restore → `onSettled` invalidate. Classic `useQueryClient()` + 3-arg-callback form (not v5.90 4-arg `context.client`).

### Invalidate-on-success mutation
**Source:** `features/rules/hooks/use-rules.ts` → `useDeleteRule` / `useCreateRule`.
**Apply to:** `useUndoAuditEntry`, `useOptInSender`, `useShadowMode` write, `useCreateTopupIntent` (+ toast via shadcn `sonner` for undo / top-up-credited per UI-SPEC).

### i18n new namespace
**Source:** `features/rules/messages.ts` + `scripts/merge-feature-i18n.ts` + `scripts/check-i18n.ts` (`EN_SCAN_FILES`).
**Apply to:** all new visible strings. Flat `Record<key,{vi,en}> as const`; add new component/page paths to `EN_SCAN_FILES`; `pnpm i18n:check` green.

### shadcn primitive selection
**Source:** convention 7 + `apps/web/AGENTS.md`. **Apply to:** all UI. Install `sidebar` (pulls `sheet`), `table`, `alert-dialog`, `switch`, `sonner`, `dropdown-menu` via `pnpm dlx shadcn@latest add` (from `apps/web`). Already installed: `alert`, `avatar`, `badge`, `button`, `card`, `checkbox`, `dialog`, `input`, `label`, `radio-group`, `separator`, `skeleton`, `tabs`, `textarea`, `toggle-group`, `toggle`, `tooltip`. `components/ui/**` is copied source, ESLint/Prettier-excluded. No wrapper components without rule-of-three (D-03).

### Page → feature-component split
**Source:** `app/(protected)/rules/page.tsx` (thin `<main>` + `<FeatureWorkspace/>`).
**Apply to:** `triage/page.tsx` (+ `<Suspense>`), `billing/page.tsx`, `billing/top-up/page.tsx` (+ `<Suspense>`).

### Reconnect / health affordance reuse
**Source:** `features/gmail/components/ReconnectPrompt.tsx` (`ReconnectPrompt`, `ReconnectPromptGate`, `shouldShowReconnectPrompt`), `ConnectionHealthBadge.tsx`, `features/gmail/hooks/useTenantStatus.ts`.
**Apply to:** the chrome health indicator + DISCONNECTED reconnect affordance — reuse, do not re-author.

### Playwright e2e harness
**Source:** `e2e/rules.spec.ts` (serial mode, `page.route('http://localhost:8080/**')` in-memory mock incl. `/me`, session+locale cookies, `fulfillJson`/`fulfillProblem`, golden path), `e2e/mobile-topbar.spec.ts` (320px viewport).
**Apply to:** every new authenticated-surface spec; cover golden path + key states on desktop + 320px.

---

## No / Weak Analog Found

| File | Role | Why | Fallback |
|---|---|---|---|
| `components/shell/AppShell.tsx` | layout shell host | Nothing in the repo composes `SidebarProvider` + `SidebarInset` + a persistent header + `Toaster`; the public `app/(public)/layout.tsx` only shows the provider-nesting *idiom* | Build from the shadcn `sidebar` block docs (Context7 / `ui.shadcn.com`); mirror the provider-nesting from `(protected)/layout.tsx` + `(public)/layout.tsx` |
| `components/shell/AppSidebar.tsx` | nav | Only `features/landing/components/TopBar.tsx` shows an active-link nav, and it's not a sidebar | shadcn `sidebar` block + `usePathname()` for active state; flat `SidebarMenu` only (D-02) |
| `components/states/{LoadingState,EmptyState,ErrorState}.tsx` | shared UI trio | Doesn't exist; every feature inlines its own loading/empty/error markup | Extract & generalize the trio inside `features/rules/components/RuleList.tsx`; back with shadcn `Skeleton` |
| `features/triage/hooks/useTriageAuditLog.ts`, `features/billing/hooks/useLedgerHistory.ts` | `useInfiniteQuery` read | No `useInfiniteQuery` usage anywhere in the repo; **and** the backend list endpoints don't exist (`schema.d.ts` confirmed) | Shape per Context7 TanStack `useInfiniteQuery` docs; gap-flag the missing endpoints (SPEC out-of-scope rule); render the screen on empty/error states + the data it can get |
| `features/billing/api/billing-api.ts` ledger / intent-status calls + QR image | feature api | Endpoints/fields absent from `schema.d.ts` | Gap-flag; degrade: poll `/api/billing/balance` for the credit signal; render `qrPayload` (raw EMV) via a small QR component or show copyable bank fields only |

---

## Metadata

**Analog search scope:** `apps/web/{app,features,components/ui,lib}`, `apps/web/{e2e,__tests__,scripts}`, `apps/web/{playwright.config.ts,AGENTS.md}`, `apps/web/lib/api/schema.d.ts` (path grep).
**Files scanned in detail:** `app/(protected)/layout.tsx`, `app/(protected)/{settings,rules}/page.tsx`, `features/rules/{query-keys.ts,api/rules-api.ts,hooks/use-rules.ts,messages.ts,components/RuleList.tsx}`, `features/triage/{api/triage-api.ts,hooks/useToggleTriagePause.ts,hooks/useToggleTriagePause.test.tsx,components/PauseBanner.tsx}`, `features/gmail/{query-keys.ts,api/gmail-api.ts,hooks/useTenantStatus.ts,components/ReconnectPrompt.tsx,components/ConnectionHealthBadge.tsx}`, `features/account/{query-keys.ts,api/account-api.ts,hooks/useCurrentUser.ts}`, `features/llm/hooks/use-byok.ts`, `lib/query-client.tsx`, `e2e/{rules.spec.ts,mobile-topbar.spec.ts}`, `playwright.config.ts`, `scripts/{check-i18n.ts,merge-feature-i18n.ts}`, `lib/api/schema.d.ts` (grep).
**Pattern extraction date:** 2026-05-12
