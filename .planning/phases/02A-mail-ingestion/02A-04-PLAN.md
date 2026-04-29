---
phase: 02A-mail-ingestion
plan: "04"
type: execute
wave: 3
depends_on:
  - "02A-02"
  - "02A-03"
files_modified:
  - apps/web/features/triage/api/triagePause.ts
  - apps/web/features/triage/api/keys.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.ts
  - apps/web/features/triage/components/PauseBanner.tsx
  - apps/web/features/gmail/components/ReconnectPrompt.tsx
  - apps/web/features/account/api/me.ts
  - apps/web/lib/api/schema.d.ts
  - apps/web/app/(protected)/layout.tsx
  - apps/web/app/(protected)/settings/page.tsx
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/scripts/check-i18n.ts
autonomous: true
requirements:
  - MAIL-05
  - MAIL-06

must_haves:
  truths:
    - "PauseBanner renders in (protected)/layout.tsx when triagePaused=true"
    - "PauseBanner is non-dismissible and has an inline Unpause button"
    - "Settings page has a Pause automated triage Card section with toggle"
    - "useToggleTriagePause invalidates accountKeys.me() on success"
    - "ReconnectPrompt shows when status!=CONNECTED OR ingestionHealth!=HEALTHY"
    - "ReconnectPrompt.test.tsx has its ingestionHealth gate tests enabled and GREEN"
    - "i18n parity: vi.json and en.json both contain settings.triage.pause.* keys including banner.body"
    - "pnpm i18n:check exits 0 after this plan"
  artifacts:
    - path: "apps/web/features/triage/components/PauseBanner.tsx"
      provides: "Non-dismissible warning banner for paused state"
      contains: "triagePaused"
    - path: "apps/web/features/triage/hooks/useToggleTriagePause.ts"
      provides: "TanStack Query mutation that invalidates me key"
      contains: "invalidateQueries"
    - path: "apps/web/features/gmail/components/ReconnectPrompt.tsx"
      provides: "Extended gate: status!=CONNECTED || ingestionHealth!=HEALTHY"
      contains: "ingestionHealth"
  key_links:
    - from: "apps/web/app/(protected)/layout.tsx"
      to: "PauseBanner"
      via: "conditional render when triagePaused===true"
      pattern: "PauseBanner|triagePaused"
    - from: "useToggleTriagePause"
      to: "accountKeys.me()"
      via: "invalidateQueries in onSuccess callback"
      pattern: "accountKeys\\.me\\(\\)"
    - from: "ReconnectPrompt"
      to: "ingestionHealth"
      via: "shouldShowReconnect boolean gate"
      pattern: "ingestionHealth.*HEALTHY|shouldShowReconnect"
---

<objective>
Implement the frontend features: PauseBanner, triage-pause toggle in settings, ReconnectPrompt extension, i18n keys. This plan runs in Wave 3 after backend API (Plan 03) is complete.

Purpose: Close MAIL-05 (reconnect prompt extension for ingestion health) and MAIL-06 (user-visible pause toggle + banner).

Output: features/triage/* (api/keys/hooks/components), ReconnectPrompt gate extension, me.ts + generated schema extension, settings toggle, i18n keys.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/02A-mail-ingestion/02A-RESEARCH.md
@.planning/phases/02A-mail-ingestion/02A-PATTERNS.md

<interfaces>
<!-- Existing frontend patterns to follow -->
From apps/web/features/gmail/hooks/useDisconnectGmail.ts (mutation hook pattern):
```typescript
'use client';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { disconnectGmail } from '@/features/gmail/api/disconnect';
import { accountKeys } from '@/features/account/api/keys';

export function useDisconnectGmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => disconnectGmail(),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.me() }),
  });
}
```

From apps/web/features/account/api/keys.ts (key factory):
```typescript
export const accountKeys = {
  me: () => ['me'] as const,
};
```

From apps/web/features/gmail/components/ReconnectPrompt.tsx (existing component — read full):
- Already uses <Alert variant="warning">
- Gate condition currently: status !== 'CONNECTED'
- CTA: /tenant/connect-gmail

From apps/web/lib/api/client.ts:
```typescript
export { api } from './generated'; // or similar
// api.PUT('/tenant/triage-pause', { body: { paused } })
```

From apps/web/features/account/api/me.ts (getCurrentUser function):
- Returns CurrentUser type
- Need to extend CurrentUser to include triagePaused and gmailConnectionStatus.ingestionHealth

From apps/web/app/(protected)/layout.tsx (read full — must add PauseBanner):
- Server Component that fetches current user
- Add <PauseBanner> conditional render

From apps/web/app/(protected)/settings/page.tsx (read full — must add pause toggle Card):
- Client component
- Add shadcn <Switch>-style toggle section

i18n context:
- vi.json and en.json live at apps/web/i18n/messages/
- scripts/check-i18n.ts has EN_SCAN_FILES array
- New files using i18n keys must be added to EN_SCAN_FILES
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: frontend-design skill + triage feature folder + me.ts extension + ReconnectPrompt gate</name>
  <files>
    apps/web/features/triage/api/triagePause.ts,
    apps/web/features/triage/api/keys.ts,
    apps/web/features/triage/hooks/useToggleTriagePause.ts,
    apps/web/features/triage/components/PauseBanner.tsx,
    apps/web/features/account/api/me.ts,
    apps/web/lib/api/schema.d.ts,
    apps/web/features/gmail/components/ReconnectPrompt.tsx,
    apps/web/i18n/messages/vi.json,
    apps/web/i18n/messages/en.json,
    apps/web/scripts/check-i18n.ts
  </files>

  <read_first>
    - INVOKE frontend-design skill BEFORE writing any JSX (per project memory: feedback_frontend_design_skill.md)
    - apps/web/features/gmail/hooks/useDisconnectGmail.ts (mutation hook pattern to copy)
    - apps/web/features/gmail/components/ReconnectPrompt.tsx (full file — gate condition to extend)
    - apps/web/features/account/api/me.ts (full file — extend CurrentUser type)
    - apps/web/features/account/api/keys.ts (accountKeys.me() key factory)
    - apps/web/i18n/messages/vi.json (current keys — add settings.triage.pause.* block)
    - apps/web/i18n/messages/en.json (same)
    - apps/web/scripts/check-i18n.ts (EN_SCAN_FILES array — add new files)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 10 frontend structure)
    - .planning/phases/02A-mail-ingestion/02A-CONTEXT.md (D-D3 ReconnectPrompt gate, D-E5 PauseBanner)
    - CLAUDE.md (Conventions: raw shadcn first, no barrel index.ts, deep imports)
  </read_first>

  <action>
FIRST: Invoke the `frontend-design` skill by reading `.claude/skills/SKILL.md` or `.agents/skills/SKILL.md` if present, or the `frontend-design` skill file directly. Apply its visual quality and design-token guidelines to all JSX in this task.

**`apps/web/features/triage/api/keys.ts`** — key factory following `accountKeys` pattern:
```typescript
export const triageKeys = {
  pause: () => ['triage', 'pause'] as const,
};
```

**`apps/web/features/triage/api/triagePause.ts`** — API call using `api.PUT`. Follow exact pattern from `disconnect.ts` analog:
```typescript
import { api } from '@/lib/api/client';

export async function setTriagePaused(paused: boolean): Promise<void> {
  const { error } = await api.PUT('/tenant/triage-pause', {
    body: { paused },
  });
  if (error) throw error;
}
```

**`apps/web/features/triage/hooks/useToggleTriagePause.ts`** — follow `useDisconnectGmail.ts` exactly:
```typescript
'use client';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { setTriagePaused } from '@/features/triage/api/triagePause';
import { accountKeys } from '@/features/account/api/keys';

export function useToggleTriagePause() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.me() }),
  });
}
```

**`apps/web/features/triage/components/PauseBanner.tsx`** — Per D-E5: non-dismissible `<Alert variant="warning">`. Use `useCurrentUser()` hook to read `triagePaused`. Use `useToggleTriagePause()` for the unpause CTA. Plain `<button>` (NOT `<Button>`) for vitest compatibility per Phase 01.4 pattern.

```typescript
'use client';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import { useTranslations } from 'next-intl';

export function PauseBanner() {
  const { data: user } = useCurrentUser();
  const t = useTranslations();
  const { mutate: togglePause } = useToggleTriagePause();

  if (!user?.triagePaused) return null;

  return (
    <Alert variant="warning" role="alert">
      <AlertTitle>{t('settings.triage.pause.banner.heading')}</AlertTitle>
      <AlertDescription className="flex items-center justify-between">
        <span>{t('settings.triage.pause.banner.body')}</span>
        <button
          onClick={() => togglePause(false)}
          className="ml-4 underline font-medium"
        >
          {t('settings.triage.pause.banner.unpause')}
        </button>
      </AlertDescription>
    </Alert>
  );
}
```

`banner.body` is mandatory and must be present in both message bundles so no `as never` cast is needed.

**`apps/web/features/account/api/me.ts`** — READ full current file. Extend the `CurrentUser` type (or interface, whichever is used) to add:
```typescript
triagePaused: boolean;
gmailConnectionStatus: {
  status: string;
  ingestionHealth: string;
  googleEmail: string;
} | null;
```
If `CurrentUser` is derived from `schema.d.ts` OpenAPI types, the type change happens automatically after `pnpm generate:api`. In that case, update any local type overrides or `Partial<>` wrappers. If `getCurrentUser` has a manually-typed return, add the two fields.

**`apps/web/features/gmail/components/ReconnectPrompt.tsx`** — READ the full current file. Extend the gate condition per D-D3:

Find the condition that determines whether to show the prompt. It currently checks something like `status !== 'CONNECTED'`. Change to:
```typescript
const shouldShowReconnect =
  user?.gmailConnectionStatus?.status !== 'CONNECTED' ||
  user?.gmailConnectionStatus?.ingestionHealth !== 'HEALTHY';
```

Single copy (D-D3: user doesn't need to distinguish the root cause). No new i18n keys — existing copy from Phase 01.5 is sufficient. CTA still points to `/tenant/connect-gmail`.

**i18n keys** — ADD these keys to both `vi.json` and `en.json`. Find the `settings` namespace in each file and add a `triage.pause` nested block:

```json
"settings": {
  ...existing keys...,
  "triage": {
    "pause": {
      "title": "Tự động xử lý email",
      "body": "Khi tắt, Zero Mail sẽ không tự động xử lý email mới",
      "toggleLabel": "Tạm dừng tự động xử lý",
      "banner": {
        "heading": "Tự động xử lý đang tạm dừng",
        "body": "Email mới vẫn được ghi nhận, nhưng Zero Mail sẽ không thực hiện hành động tự động.",
        "unpause": "Bật lại"
      }
    }
  }
}
```

English version:
```json
"settings": {
  ...existing keys...,
  "triage": {
    "pause": {
      "title": "Automated triage",
      "body": "When off, Zero Mail won't automatically process new emails",
      "toggleLabel": "Pause automated triage",
      "banner": {
        "heading": "Automated triage is paused",
        "body": "New mail is still observed, but Zero Mail will not run automated actions.",
        "unpause": "Resume"
      }
    }
  }
}
```

**`apps/web/scripts/check-i18n.ts`** — READ the current `EN_SCAN_FILES` array. ADD these files per P-07 mitigation:
- `features/triage/components/PauseBanner.tsx`
- `app/(protected)/settings/page.tsx` (if not already there)
  </action>

  <verify>
    <automated>cd /d/study-materials-summer-2026/EXE202/zero-mail && pnpm -F web run i18n:check 2>&1 | grep -E "pass|fail|error|missing|parity" | head -10</automated>
  </verify>

  <acceptance_criteria>
    - `apps/web/features/triage/api/triagePause.ts` contains `api.PUT('/tenant/triage-pause'`
    - `apps/web/features/triage/hooks/useToggleTriagePause.ts` contains `accountKeys.me()` and `invalidateQueries`
    - `apps/web/features/triage/components/PauseBanner.tsx` contains `variant="warning"` and does NOT use `<Button>` (plain `<button>`)
    - `apps/web/features/gmail/components/ReconnectPrompt.tsx` contains `ingestionHealth` in the gate condition
    - `apps/web/i18n/messages/vi.json` contains `settings.triage.pause.banner.unpause` key (traverse: vi["settings"]["triage"]["pause"]["banner"]["unpause"])
    - `apps/web/i18n/messages/vi.json` and `en.json` contain `settings.triage.pause.banner.body`
    - `apps/web/i18n/messages/en.json` contains the matching English key
    - `apps/web/scripts/check-i18n.ts` EN_SCAN_FILES array contains `PauseBanner.tsx`
    - `pnpm -F web run i18n:check` exits 0
    - `pnpm -F web run typecheck` exits 0
  </acceptance_criteria>

  <done>Triage feature folder created; PauseBanner + hook + api done; ReconnectPrompt gate extended; generated schema/me type updated; i18n keys added; i18n:check passes</done>
</task>

<task type="auto">
  <name>Task 2: Settings toggle + protected layout PauseBanner + OpenAPI regen + frontend test green</name>
  <files>
    apps/web/app/(protected)/layout.tsx,
    apps/web/app/(protected)/settings/page.tsx
  </files>

  <read_first>
    - INVOKE frontend-design skill BEFORE writing any JSX
    - apps/web/app/(protected)/layout.tsx (full file — READ BEFORE editing)
    - apps/web/app/(protected)/settings/page.tsx (full file — READ BEFORE editing)
    - apps/web/features/triage/components/PauseBanner.tsx (just created in Task 1)
    - .planning/phases/02A-mail-ingestion/02A-CONTEXT.md (D-E5: Settings toggle Card section, persistent banner)
    - CLAUDE.md (Conventions: raw shadcn first, no custom wrapper primitives)
  </read_first>

  <action>
**`apps/web/app/(protected)/layout.tsx`** — READ the current file. This is likely a Server Component. Add `<PauseBanner />` conditionally inside the layout, just below the header (or above the main content area). Import `PauseBanner` from `@/features/triage/components/PauseBanner`.

The PauseBanner is a Client Component that reads from TanStack Query cache — it self-manages its visibility. The layout just needs to render it unconditionally (the component returns null when not paused):

```tsx
// In the layout JSX, after any existing header component:
<PauseBanner />
<main className="...existing classes...">{children}</main>
```

If layout is a Server Component that pre-fetches user data, `PauseBanner` can read from the already-populated TanStack Query cache (hydrated by the layout's `prefetchQuery`). No new server-fetch needed.

**`apps/web/app/(protected)/settings/page.tsx`** — READ the current file. Add a new Card section for the pause toggle. Use raw shadcn `<Card>`, `<CardHeader>`, `<CardContent>` + a `<Switch>` from shadcn (or a plain toggle button if `<Switch>` is not installed).

Check if `@/components/ui/switch` exists:
```bash
ls apps/web/components/ui/switch.tsx 2>/dev/null || echo "not installed"
```

If `Switch` is available: use it with `checked={user?.triagePaused}` and `onCheckedChange={(checked) => togglePause(checked)}`.

If not available: use a plain `<button>` with toggle semantics (add `aria-pressed`). Do NOT install new shadcn primitives in this plan — use what exists.

The section structure (using token-aware className, raw shadcn):
```tsx
<Card>
  <CardHeader>
    <CardTitle>{t('settings.triage.pause.title')}</CardTitle>
    <CardDescription>{t('settings.triage.pause.body')}</CardDescription>
  </CardHeader>
  <CardContent className="flex items-center justify-between">
    <span className="text-sm font-medium">{t('settings.triage.pause.toggleLabel')}</span>
    {/* Switch or button toggle */}
    <Switch
      checked={user?.triagePaused ?? false}
      onCheckedChange={(checked) => togglePause(checked)}
      aria-label={t('settings.triage.pause.toggleLabel')}
    />
  </CardContent>
</Card>
```

Use `useCurrentUser()` hook for `user` and `useToggleTriagePause()` for `togglePause`.

**After both files are written:** Regenerate OpenAPI artifacts using the repo's hermetic pipeline, then regenerate frontend types:
```bash
./gradlew :backend:api:generateOpenApiDocs
pnpm -F web generate:api
```
This updates `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts` without requiring a manually running backend because `backend/api/build.gradle.kts` configures the springdoc OpenAPI Gradle plugin. If generation is blocked by unrelated local service or Docker issues, manually update `apps/web/lib/api/schema.d.ts` for `/tenant/triage-pause`, `triagePaused`, and `gmailConnectionStatus`, then record the regen blocker in the plan summary.

**Enable ReconnectPrompt Wave 0 tests:** remove `it.skip` from `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` now that the ingestionHealth gate is implemented. These tests must run GREEN before this plan completes.

**Run full frontend test suite to confirm Wave 0 tests are now GREEN:**
```bash
pnpm -F web run test:run 2>&1 | grep -E "FAIL|PASS|PauseBanner|useToggle|phase-02a" | head -20
```
All 4 Wave 0 frontend test files should now be GREEN.
  </action>

  <verify>
    <automated>cd /d/study-materials-summer-2026/EXE202/zero-mail && pnpm -F web run test:run 2>&1 | grep -E "FAIL|PASS" | tail -10</automated>
  </verify>

  <acceptance_criteria>
    - `apps/web/app/(protected)/layout.tsx` imports `PauseBanner` and renders `<PauseBanner />` somewhere in the JSX
    - `apps/web/app/(protected)/settings/page.tsx` contains `settings.triage.pause.title` i18n key reference
    - `apps/web/app/(protected)/settings/page.tsx` uses `useToggleTriagePause` hook
    - `pnpm -F web run typecheck` exits 0
    - `pnpm -F web run test:run` shows `PauseBanner.test.tsx` PASS
    - `pnpm -F web run test:run` shows `useToggleTriagePause.test.tsx` PASS
    - `pnpm -F web run test:run` shows `phase-02a-files.test.ts` PASS (all files now exist)
    - `pnpm -F web run test:run` shows `ReconnectPrompt.test.tsx` PASS and `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` contains no `it.skip`
    - `pnpm -F web run lint` exits 0
  </acceptance_criteria>

  <done>Protected layout has PauseBanner; settings has pause toggle Card; all 4 Wave 0 frontend tests GREEN; typecheck + lint pass</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Frontend → /tenant/triage-pause | Authenticated user action; server validates session |
| Frontend render of gmailConnectionStatus | ingestionHealth enum value shown to owner only |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07 | Elevation of Privilege | useToggleTriagePause → PUT /tenant/triage-pause | mitigate | Backend validates session cookie + TenantContext; frontend just calls the endpoint with session credentials; no cross-tenant access possible |
| T-10 | Information Disclosure | ingestionHealth display | accept | D-D3: ReconnectPrompt unified gate shows same copy for WATCH_UNHEALTHY and HISTORY_LOST — raw enum value not rendered as user-visible text |
</threat_model>

<verification>
After this plan:
- `pnpm -F web run test:run` — all 4 Wave 0 frontend tests GREEN (PauseBanner, useToggleTriagePause, phase-02a-files, ReconnectPrompt)
- `pnpm -F web run typecheck` exits 0
- `pnpm -F web run lint` exits 0
- `pnpm -F web run i18n:check` exits 0
- `apps/web/app/(protected)/layout.tsx` contains `<PauseBanner />`
</verification>

<success_criteria>
All Wave 3 frontend files exist. PauseBanner is non-dismissible with unpause CTA. Settings page has pause toggle Card section. ReconnectPrompt gate extended to check ingestionHealth. i18n keys parity maintained. All 4 Wave 0 frontend tests are GREEN.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-04-SUMMARY.md`
</output>
