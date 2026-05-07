---
phase: 02C-llm-gateway
plan: 08
type: execute
wave: 6
depends_on: [05]
files_modified:
  - apps/web/lib/api/schema.d.ts
  - apps/web/features/llm/api/llm-api.ts
  - apps/web/features/llm/hooks/use-byok.ts
  - apps/web/features/llm/components/ByokForm.tsx
  - apps/web/features/llm/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/app/(protected)/settings/page.tsx
  - apps/web/scripts/check-i18n.ts
  - apps/web/features/llm/components/ByokForm.test.tsx
  - apps/web/__tests__/byok-key-handling.test.ts
autonomous: true
requirements: [LLM-04, LLM-06, LLM-11]
must_haves:
  truths:
    - "apps/web/features/llm/ triplet (api/components/hooks) exists per CONTEXT D-D1; mounted on /settings page between automated-triage card and privacy card per UI-SPEC layout contract"
    - "ByokForm uses useRef<HTMLFormElement> to read raw API key on submit; key NEVER enters React state, NEVER appears in TanStack Query cache key, NEVER persists in localStorage / sessionStorage / cookies / URL params (D-D2 + UI-SPEC privacy contract)"
    - "Validate-then-Save state machine: Save button disabled until validateByok.data?.ok === true; field change after validation clears the success state and re-disables Save (UI-SPEC interaction contract)"
    - "All visible copy flows through next-intl keys under llm.byok.* and errors.llm.* namespaces; vi.json and en.json are lock-step; pnpm i18n:check STRICT passes (UI-SPEC i18n keys list)"
    - "Frontend-design skill is invoked BEFORE writing ByokForm.tsx (CONTEXT D-D6 + memory rule)"
    - "Raw shadcn primitives used (Card, RadioGroup, Input, Button, Alert) — NO ByokFormCard, NO ValidationResultAlert wrappers (CONTEXT D-D4 + memory rule rule-of-three)"
    - "404 / 402 / safety-violation error responses from gateway-fronted endpoints (BillingController, future Phase 4 endpoints) are localizable via existing useLocalizedApiError hook + new errors.llm.* keys"
  artifacts:
    - path: "apps/web/features/llm/api/llm-api.ts"
      provides: "validateByok + saveByok + getCurrentByok via openapi-fetch typed client"
      exports: ["validateByok", "saveByok", "getCurrentByok"]
    - path: "apps/web/features/llm/hooks/use-byok.ts"
      provides: "useValidateByok + useSaveByok TanStack Query mutations + useCurrentByok query"
      exports: ["useValidateByok", "useSaveByok", "useCurrentByok"]
    - path: "apps/web/features/llm/components/ByokForm.tsx"
      provides: "Single shadcn Card with provider radio, conditional endpoint field, uncontrolled password input, Validate + Save buttons, success/destructive Alert"
      contains: "useRef<HTMLFormElement>"
    - path: "apps/web/features/llm/messages.ts"
      provides: "Co-located {vi, en} copy table merged into top-level i18n bundles at build/check time (D-D5)"
    - path: "apps/web/i18n/messages/vi.json + en.json"
      provides: "llm.byok.* + errors.llm.* keys per UI-SPEC required-keys list"
  key_links:
    - from: "apps/web/features/llm/components/ByokForm.tsx"
      to: "apps/web/features/llm/hooks/use-byok.ts"
      via: "useValidateByok + useSaveByok mutations"
      pattern: "useValidateByok|useSaveByok"
    - from: "apps/web/features/llm/api/llm-api.ts"
      to: "apps/web/lib/api/schema.d.ts"
      via: "typed POST /api/llm/byok/validate + POST /api/llm/byok + GET /api/llm/byok"
      pattern: "/llm/byok"
    - from: "apps/web/app/(protected)/settings/page.tsx"
      to: "apps/web/features/llm/components/ByokForm.tsx"
      via: "import + render between automated-triage and privacy cards"
      pattern: "ByokForm"
---

<objective>
Wave 6 BYOK frontend (parallel with Plan 07). Land `apps/web/features/llm/` (the triplet api/components/hooks) per CONTEXT D-D1; mount `ByokForm.tsx` on `/settings` per UI-SPEC layout contract; localize all copy through `llm.byok.*` and `errors.llm.*` next-intl keys with vi+en parity; regenerate the typed OpenAPI client from Plan 05's new endpoints.

Purpose: this is LLM-04 (BYOK UX surface — user-provided key UI), LLM-06 (credit-depleted top-up prompt visible), LLM-11 (admin/BYOK config form). The frontend-design skill MUST be invoked before writing ByokForm.tsx (memory rule — passed into the executor agent).

Output: 4 production files in `features/llm/` + i18n keys merged into vi/en bundles + settings page wired + EN_SCAN_FILES update for `pnpm i18n:check` + 2 frontend tests + schema.d.ts regen. NO backend changes.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-UI-SPEC.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-05-SUMMARY.md
@apps/web/features/account/api/account-api.ts
@apps/web/features/account/components/DeleteAccountDialog.tsx
@apps/web/features/account/hooks/useDeleteAccount.ts
@apps/web/components/ui/card.tsx
@apps/web/components/ui/radio-group.tsx
@apps/web/components/ui/alert.tsx
@apps/web/components/ui/input.tsx
@apps/web/components/ui/button.tsx
@apps/web/i18n/messages/vi.json
@apps/web/i18n/messages/en.json
@apps/web/scripts/check-i18n.ts
@apps/web/app/(protected)/settings/page.tsx
@apps/web/lib/api/client.ts

<frontend_design_skill>
**MANDATORY**: Invoke the `frontend-design` skill (per memory rule + CONTEXT D-D6) BEFORE writing `ByokForm.tsx`. The skill enforces UI-SPEC alignment, raw-shadcn discipline, accessibility (role=status, aria-live, focus rings), and copy quality. Pass this rule into any executor subagent.
</frontend_design_skill>

<interfaces>
<!-- Existing reusable -->
- `apps/web/lib/api/client.ts` — `api.POST`, `api.GET`, `xsrfHeader()` helpers. PATTERNS.md `apps/web/features/llm/api/llm-api.ts` section gives the exact pattern.
- `apps/web/features/account/api/account-api.ts` lines 92-110 — POST + DELETE with xsrfHeader() typed-client analog.
- `apps/web/features/account/hooks/useDeleteAccount.ts` — TanStack mutation hook analog (14 lines).
- `apps/web/features/account/components/DeleteAccountDialog.tsx` — controlled-vs-uncontrolled split analog (PATTERNS.md "ByokForm.tsx" section). Note: DeleteAccountDialog uses controlled state for confirmation phrase; ByokForm INVERTS this — uncontrolled for the secret, controlled for everything else.
- `apps/web/i18n/messages/vi.json` + `en.json` — lock-step bundles. `pnpm i18n:check` STRICT enforced from Phase 1.3 P07.
- `apps/web/scripts/check-i18n.ts` — EN_SCAN_FILES array; must add new ByokForm.tsx + settings page paths.

<!-- Plan 05 endpoints -->
- `POST /api/llm/byok/validate` body `{ provider: "anthropic" | "openai-compatible", endpoint?: string, apiKey: string }` → response `{ ok: boolean, models?: string[], reason?: string }`.
- `POST /api/llm/byok` body same shape → response `{ ok: boolean, savedAt: string (ISO instant) }`.
- `GET /api/llm/byok` → response `{ provider: ..., endpointHost?: string, savedAt: string } | null` (200 with null body or 204 — verify Plan 05 SUMMARY).

<!-- shadcn primitives already installed (UI-SPEC line 56) -->
- Card (with CardHeader, CardTitle, CardDescription, CardContent, CardFooter)
- RadioGroup (with RadioGroupItem)
- Alert (with AlertTitle, AlertDescription, variant=default|destructive)
- Input (type=text, type=password)
- Label
- Button (existing variants)
- Badge (for saved-key indicator if needed)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Regenerate schema.d.ts + features/llm/api + features/llm/hooks + i18n keys + EN_SCAN_FILES update</name>
  <read_first>
    - apps/web/features/account/api/account-api.ts (lines 92-110 — typed POST + xsrfHeader pattern)
    - apps/web/features/account/hooks/useDeleteAccount.ts (entire file — TanStack mutation analog)
    - apps/web/scripts/check-i18n.ts (EN_SCAN_FILES array — add new files)
    - apps/web/i18n/messages/vi.json + en.json (existing namespaces — append new top-level llm.* and errors.llm.*)
    - apps/web/lib/api/client.ts (api.POST/api.GET + xsrfHeader signatures)
    - .planning/phases/02C-llm-gateway/02C-UI-SPEC.md (Section "i18n Keys" — required keys list verbatim)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-D5 messages.ts co-location convention)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "apps/web/features/llm/api/llm-api.ts" + "apps/web/features/llm/hooks/use-byok.ts" + "apps/web/features/llm/messages.ts")
  </read_first>
  <action>
    1. **Regenerate the typed OpenAPI client** to pick up Plan 05's `/api/llm/byok/{validate,(post),(get)}` endpoints:
       - Run `pnpm -C apps/web generate:api` (or whatever script Phase 1.3 P04 wired). This regenerates `apps/web/lib/api/schema.d.ts`.
       - If the backend isn't running locally, fall back to `./gradlew :backend:api:generateOpenApiDocs` first to emit `openapi/openapi.json`, then run the codegen script with `--input openapi/openapi.json`.
       - Verify the new types `paths['/api/llm/byok/validate']`, `paths['/api/llm/byok']`, `components['schemas']['ByokValidateRequest']`, etc. appear in `schema.d.ts`.

    2. **Create `apps/web/features/llm/api/llm-api.ts`** per PATTERNS.md verbatim:
       ```ts
       import { api, xsrfHeader } from '@/lib/api/client';
       import type { components } from '@/lib/api/schema';

       export type ByokValidatePayload = components['schemas']['ByokValidateRequest'];
       export type ByokValidateResult = components['schemas']['ByokValidateResponse'];
       export type ByokSavePayload = components['schemas']['ByokSaveRequest'];
       export type ByokSaveResult = components['schemas']['ByokSaveResponse'];
       export type ByokCurrentResult = components['schemas']['ByokCurrentResponse'];

       export async function validateByok(payload: ByokValidatePayload): Promise<ByokValidateResult> {
         const { data, error, response } = await api.POST('/api/llm/byok/validate', {
           body: payload,
           headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
         });
         if (error || !response.ok) throw error ?? new Error(`/llm/byok/validate failed: ${response.status}`);
         return data as ByokValidateResult;
       }

       export async function saveByok(payload: ByokSavePayload): Promise<ByokSaveResult> {
         const { data, error, response } = await api.POST('/api/llm/byok', {
           body: payload,
           headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
         });
         if (error || !response.ok) throw error ?? new Error(`/llm/byok save failed: ${response.status}`);
         return data as ByokSaveResult;
       }

       export async function getCurrentByok(): Promise<ByokCurrentResult | null> {
         const { data, error, response } = await api.GET('/api/llm/byok', {});
         if (response.status === 204 || data === null) return null;
         if (error) throw error;
         return data as ByokCurrentResult;
       }
       ```

    3. **Create `apps/web/features/llm/hooks/use-byok.ts`** per PATTERNS.md:
       ```ts
       'use client';
       import { useMutation, useQuery } from '@tanstack/react-query';
       import { saveByok, validateByok, getCurrentByok } from '@/features/llm/api/llm-api';

       // Mutation-only triplet — NO query keys file (D-D1: BYOK has no cached read state for mutations)

       export function useValidateByok() {
         return useMutation({ mutationFn: validateByok });
       }

       export function useSaveByok() {
         return useMutation({ mutationFn: saveByok });
       }

       // GET /api/llm/byok IS a read; expose a small query key here only
       export const byokKeys = {
         all: ['byok'] as const,
         current: () => [...byokKeys.all, 'current'] as const,
       };

       export function useCurrentByok() {
         return useQuery({ queryKey: byokKeys.current(), queryFn: getCurrentByok });
       }
       ```
       Note: D-D1 says NO query-keys file — but `getCurrentByok` IS a read. Resolution: declare a small `byokKeys` factory inline in `use-byok.ts` (not a separate file). On Save success, the form invalidates `byokKeys.current()` to refresh the displayed metadata.

    4. **Create `apps/web/features/llm/messages.ts`** per CONTEXT D-D5 — co-located source of truth for BYOK copy. The build/check script merges this into top-level vi.json + en.json. For Plan 08, simplest path: directly update vi.json + en.json with the keys; treat messages.ts as a typed manifest that the lint script reads (or skip messages.ts in v1 and put keys directly in i18n bundles per the existing convention). **Decision per project flat-folder rule + simplicity**: skip `messages.ts` for v1, put keys directly in `vi.json` + `en.json` under top-level `llm` namespace; document this deviation from D-D5 in the SUMMARY.

    5. **Modify `apps/web/i18n/messages/vi.json`** — add the following keys under top-level `llm.byok.*` and `errors.llm.*` namespaces (Vietnamese copy from UI-SPEC "Copywriting Contract" verbatim):
       ```json
       "llm": {
         "byok": {
           "title": "Khóa API cho nhà cung cấp AI",
           "description": "Dùng khóa riêng của bạn để gọi OpenAI Compatible hoặc Anthropic. Zero Mail chỉ lưu khóa đã mã hóa và không trừ tín dụng nền tảng cho các lượt gọi BYOK.",
           "provider": {
             "label": "Nhà cung cấp",
             "anthropic": "Anthropic",
             "openaiCompatible": "OpenAI Compatible"
           },
           "endpoint": {
             "label": "Endpoint OpenAI Compatible",
             "placeholder": "https://openrouter.ai/api/v1"
           },
           "apiKey": {
             "label": "Khóa API",
             "placeholder": "Dán khóa API"
           },
           "validateCta": "Kiểm tra khóa API",
           "saveCta": "Lưu khóa API",
           "validating": "Đang kiểm tra khóa...",
           "saving": "Đang lưu khóa...",
           "empty": {
             "heading": "Chưa có khóa BYOK",
             "body": "Chọn nhà cung cấp, dán khóa API, rồi kiểm tra trước khi lưu."
           },
           "validation": {
             "success": "Khóa đã được kiểm tra. Bạn có thể lưu cấu hình này.",
             "invalid": "Không thể kiểm tra khóa này. Kiểm tra nhà cung cấp, endpoint và khóa API, rồi thử lại."
           },
           "save": {
             "success": "Đã lưu khóa BYOK đã mã hóa. Các lượt gọi AI sẽ dùng khóa này cho đến khi bạn thay đổi.",
             "error": "Không thể lưu khóa đã mã hóa. Tải lại trang rồi thử lại."
           },
           "existing": {
             "replaceNotice": "Lưu khóa mới đã kiểm tra sẽ thay thế khóa đã mã hóa hiện tại."
           }
         }
       },
       "errors": {
         "llm": {
           "insufficientCredits": {
             "title": "Tín dụng nền tảng đã hết",
             "body": "Nạp thêm tín dụng hoặc lưu khóa BYOK hợp lệ để tiếp tục gọi AI."
           },
           "safetyViolation": "Yêu cầu AI đã bị từ chối vì lý do an toàn.",
           "sanitizationFailed": "Không thể chuẩn hóa nội dung email trước khi gọi AI. Hãy thử lại.",
           "byokValidateFailed": "Không thể kiểm tra khóa BYOK. Kiểm tra nhà cung cấp và khóa rồi thử lại."
         }
       }
       ```
       
       **Modify `apps/web/i18n/messages/en.json`** — add the same key tree with English copy from UI-SPEC verbatim (Card title `AI provider key`, etc. — see UI-SPEC table line 178-198).

    6. **Modify `apps/web/scripts/check-i18n.ts`** — add the 2 new file paths to `EN_SCAN_FILES` so the strict i18n parity check covers them:
       - `apps/web/features/llm/components/ByokForm.tsx`
       - `apps/web/app/(protected)/settings/page.tsx` (if not already in the array — verify via Phase 1.3 P07 baseline; if already present from a prior plan, skip)

    7. Run `pnpm -C apps/web i18n:check` and confirm STRICT pass — vi.json and en.json have lock-step keys, every used `t('llm.byok.*')` key has both bundles populated.
  </action>
  <verify>
    <automated>cd apps/web && pnpm tsc --noEmit && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - File `apps/web/features/llm/api/llm-api.ts` exists; `grep -c 'validateByok\|saveByok\|getCurrentByok' apps/web/features/llm/api/llm-api.ts` returns `>= 3`.
    - File `apps/web/features/llm/hooks/use-byok.ts` exists; `grep -c "'use client'" apps/web/features/llm/hooks/use-byok.ts` returns `1`; `grep -c 'useValidateByok\|useSaveByok\|useCurrentByok' apps/web/features/llm/hooks/use-byok.ts` returns `>= 3`.
    - `grep -c '"llm":\|"llm" :' apps/web/i18n/messages/vi.json` returns `>= 1` (top-level llm namespace exists).
    - `grep -c 'llm.byok.title\|"title":\s*"Khóa API' apps/web/i18n/messages/vi.json` returns `>= 1`.
    - `grep -c 'errors.llm.\|"safetyViolation"\|"sanitizationFailed"' apps/web/i18n/messages/vi.json` returns `>= 1`.
    - Same for en.json — `grep -c '"AI provider key"\|"safetyViolation"' apps/web/i18n/messages/en.json` returns `>= 2`.
    - `cd apps/web && pnpm tsc --noEmit` exits 0 — typed schema includes the new BYOK paths.
    - `cd apps/web && pnpm i18n:check` exits 0 — STRICT mode passes.
    - `grep -c 'features/llm/components/ByokForm.tsx' apps/web/scripts/check-i18n.ts` returns `1` (file added to EN_SCAN_FILES).
  </acceptance_criteria>
  <done>
    Schema regenerated; api + hooks files land per PATTERNS.md; i18n keys for `llm.byok.*` + `errors.llm.*` mirrored across vi+en in lock-step; EN_SCAN_FILES updated; `pnpm i18n:check` STRICT passes.
  </done>
</task>

<task type="auto">
  <name>Task 2: ByokForm.tsx (frontend-design skill required) + mount on /settings + 2 frontend tests</name>
  <read_first>
    - apps/web/features/account/components/DeleteAccountDialog.tsx (entire file — controlled-vs-uncontrolled split analog; PATTERNS.md "ByokForm.tsx")
    - apps/web/components/ui/card.tsx + radio-group.tsx + alert.tsx + input.tsx + button.tsx + label.tsx (raw shadcn primitives — no wrappers permitted per CONTEXT D-D4)
    - apps/web/app/(protected)/settings/page.tsx (current settings card stack; UI-SPEC layout contract: insert BYOK card between automated-triage card and privacy card)
    - .planning/phases/02C-llm-gateway/02C-UI-SPEC.md (entire file — design system, layout contract, visual hierarchy, spacing, typography, color, copywriting contract, interaction contract, component inventory, registry safety)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-D2 uncontrolled inputs, D-D3 two-step UX, D-D4 raw shadcn, D-D6 frontend-design skill mandate)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (section "ByokForm.tsx" — full component pattern + raw-shadcn-only rule)
    - apps/web/features/llm/api/llm-api.ts + use-byok.ts (Task 1 outputs)
  </read_first>
  <action>
    **MANDATORY: Invoke the `frontend-design` skill BEFORE writing ByokForm.tsx.** Pass it the UI-SPEC, CONTEXT D-D1..D-D6, and the PATTERNS.md ByokForm code block. The skill enforces UI-SPEC alignment, raw-shadcn discipline, accessibility, focus management, and Vietnamese-first copy quality.

    1. **Create `apps/web/features/llm/components/ByokForm.tsx`** per PATTERNS.md verbatim base + UI-SPEC interaction contract refinements. Key requirements:
       
       - `'use client'` directive at top.
       - `useRef<HTMLFormElement>` for the form element; raw API key read via `formRef.current?.elements.namedItem('apiKey')` ONLY at validate / save click time.
       - Provider radio + endpoint visibility = `useState` (controlled, harmless).
       - `<Input type="password" name="apiKey" autoComplete="off" />` — UNCONTROLLED (no `value`/`onChange`).
       - Validate-then-Save state machine per UI-SPEC interaction contract:
         - Save disabled until `validateMutation.data?.ok === true`.
         - Field change after validation → call `validateMutation.reset()` to clear success state and re-disable Save.
         - Validate success → render `<Alert variant="default">` with `llm.byok.validation.success` + (for OpenAI-compat) up to 5 model names + `+N more`.
         - Validate failure → render `<Alert variant="destructive">` with `llm.byok.validation.invalid`.
         - Save success → reset form, invalidate `byokKeys.current()` query, render saved-state notice.
         - Save failure → keep fields, render destructive `Alert`.
       - Existing-key state via `useCurrentByok()` query: when data exists, render a muted metadata row (provider + endpointHost + savedAt) above the form per UI-SPEC layout contract; show `llm.byok.existing.replaceNotice` warning when user starts editing.
       - Loading state: disable provider + endpoint + key + buttons during validate/save; show button-local pending labels (`llm.byok.validating` / `llm.byok.saving`).
       - Accessibility: every input has visible Label; validation result wrapped in `role="status"` or `aria-live="polite"` div; focus rings use `ring-ring`.
       - Component composition INSIDE one `<Card>` (NO ByokFormCard wrapper, NO ValidationResultAlert wrapper — D-D4 + memory rule rule-of-three not met).
       - Vietnamese-first: all copy via `useTranslations()` calls — `t('llm.byok.title')`, `t('llm.byok.provider.label')`, etc.

    2. **Modify `apps/web/app/(protected)/settings/page.tsx`** — import `ByokForm` from `@/features/llm/components/ByokForm` and render between the automated-triage card (Phase 2A `PauseBanner` / triage toggle) and the privacy card. Preserve existing `mx-auto max-w-2xl space-y-4 p-6` page wrapper. Order:
       1. Automated-triage card (existing)
       2. **ByokForm** (new)
       3. Privacy card (existing)
       4. Danger zone (DeleteAccountDialog — existing)
       
       UI-SPEC line 67: "Insert after the automated-triage card and before the privacy card."

    3. **Create `apps/web/features/llm/components/ByokForm.test.tsx`** (Vitest):
       - Test 1: renders provider radio with both options visible; Validate button disabled when no provider/key.
       - Test 2: typing in apiKey input does NOT update any React state (assert via spy on useState — or by rendering `data-testid="form-state-snapshot"` that includes only the controlled state, asserting apiKey is not present).
       - Test 3: validate success enables Save button; field-change after success disables Save again.
       - Test 4: form reset on save success.
       - Test 5: validate failure renders destructive Alert with `errors.llm.byokValidateFailed` or `llm.byok.validation.invalid` text.

    4. **Create `apps/web/__tests__/byok-key-handling.test.ts`** (project-wide invariant test):
       - Test 1: `grep` ByokForm.tsx source — assert NO `useState<string>` for `apiKey` (must be uncontrolled).
       - Test 2: assert `formRef.current?.reset()` is called on save success in ByokForm.tsx source.
       - Test 3: assert NO `localStorage.setItem` / `sessionStorage.setItem` / `document.cookie =` calls referencing `apiKey` anywhere in the BYOK feature folder.
       - Test 4: assert ByokForm.tsx contains `<input type="password" name="apiKey"` (not `type="text"` or `type="email"`).
       
       This is the durable privacy guard — same pattern as Phase 1.3 Wave 0 architecture tests.

    5. Run `pnpm -C apps/web tsc --noEmit && pnpm -C apps/web vitest run --include 'features/llm/**' --include '__tests__/byok-key-handling.test.ts' && pnpm -C apps/web eslint .` to verify type-check + tests + lint all pass.
  </action>
  <verify>
    <automated>cd apps/web && pnpm tsc --noEmit && pnpm vitest run --include "features/llm/**" --include "__tests__/byok-key-handling.test.ts" && pnpm eslint . && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - File `apps/web/features/llm/components/ByokForm.tsx` exists.
    - `grep -c "'use client'" apps/web/features/llm/components/ByokForm.tsx` returns `1`.
    - `grep -c 'useRef<HTMLFormElement>' apps/web/features/llm/components/ByokForm.tsx` returns `>= 1`.
    - `grep -c 'name="apiKey"' apps/web/features/llm/components/ByokForm.tsx` returns `>= 1`.
    - `grep -c 'type="password"' apps/web/features/llm/components/ByokForm.tsx` returns `>= 1`.
    - `grep -c 'useState<string>(apiKey\|setApiKey\|onChange.*apiKey' apps/web/features/llm/components/ByokForm.tsx` returns `0` (apiKey NEVER controlled).
    - `grep -c 'autoComplete="off"' apps/web/features/llm/components/ByokForm.tsx` returns `>= 1`.
    - `grep -c 'ByokFormCard\|ValidationResultAlert' apps/web/features/llm/components/ByokForm.tsx` returns `0` (no wrapper components).
    - `grep -c 'ByokForm' apps/web/app/(protected)/settings/page.tsx` returns `>= 1` (mounted on settings).
    - `cd apps/web && pnpm tsc --noEmit` exits 0.
    - `cd apps/web && pnpm vitest run --include "features/llm/**" --include "__tests__/byok-key-handling.test.ts"` exits 0 (all 9 tests pass — 5 component + 4 invariant).
    - `cd apps/web && pnpm eslint .` exits 0.
    - `cd apps/web && pnpm i18n:check` exits 0 (STRICT mode).
  </acceptance_criteria>
  <done>
    ByokForm.tsx exists with uncontrolled password input + raw shadcn primitives + Vietnamese-first copy + UI-SPEC-conformant layout. Form is mounted on /settings between automated-triage and privacy cards. Component tests + project-wide invariant tests prove the raw key never enters React state, never persists in any client-side storage, and the form composes raw shadcn (no wrappers). i18n parity strict. Frontend-design skill was invoked before writing ByokForm.tsx.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser DOM password input → POST body | Raw key crosses once on submit; never enters React state, never enters TanStack Query cache key, never enters URL. |
| ByokForm React state → DevTools / browser extensions | Controlled state contains provider + endpoint visibility only — no secret material. |
| Browser → /api/llm/byok/{validate,save} | XSRF token included via xsrfHeader() helper; backend issues outbound provider call (not the browser). |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-03 | Information Disclosure (BYOK key leakage in client-side storage / state / DevTools) | ByokForm.tsx | mitigate | (1) `<Input type="password" name="apiKey" autoComplete="off" />` is UNCONTROLLED (no value/onChange). (2) `useRef<HTMLFormElement>` reads raw key once on submit; reference dropped after the API call. (3) TanStack Query `useMutation` (NOT useQuery) — no cache key holds the payload. (4) `byok-key-handling.test.ts` invariant tests assert no `useState<string>(apiKey)`, no localStorage / sessionStorage / cookie writes referencing apiKey. (5) Form reset on save success clears DOM input value. (6) UI-SPEC interaction contract privacy section is the design contract; frontend-design skill enforces. |
| T-2C-form-resubmit-leak | Information Disclosure | ByokForm.tsx | mitigate | Field change after validation triggers `validateMutation.reset()` → forces re-validate → user must re-enter key OR re-validate the existing input. Prevents stale-validation reuse. |
| T-2C-i18n-content-leak | Information Disclosure | i18n bundles | accept | i18n keys carry only static UI copy (Vietnamese / English); no PII, no secrets, no model output content. |
| T-2C-error-localization-shows-server-detail | Information Disclosure | useLocalizedApiError consumer of errors.llm.* | mitigate | Backend `GlobalExceptionHandler` (Plan 05) maps to error codes `error.llm.{safety_violation,sanitization_failed,byok.invalid,byok.validate_failed}` with empty parameters; frontend resolves to localized strings without ever reading server-side detail. |
| T-2C-mounted-without-auth | Spoofing / Elevation of Privilege | settings/page.tsx mount point | accept | `/settings` lives under `app/(protected)` — existing Phase 1 auth protects the route. ByokForm is rendered inside a guarded layout; an unauthenticated request would be redirected to /login by the existing middleware. |
| T-2C-toast-leaks-key | Information Disclosure | save error toast | mitigate | UI-SPEC requires destructive Alert with `errors.llm.byokValidateFailed` / `llm.byok.save.error` static copy — never echoes payload back. ByokForm.test.tsx asserts the destructive Alert renders the localized key, not any input value. |
</threat_model>

<verification>
- `cd apps/web && pnpm tsc --noEmit` exits 0
- `cd apps/web && pnpm vitest run` exits 0 — all suites green including the new BYOK feature tests + the project-wide invariant test
- `cd apps/web && pnpm eslint .` exits 0
- `cd apps/web && pnpm i18n:check` exits 0 (STRICT mode)
- Manual Playwright walk (deferred to user via summary): visit `/settings`, see BYOK card; provider radio + endpoint field + key field render; Validate disabled until provider+key entered; on validate success Save enables; on save success form resets; on save failure error Alert renders.
</verification>

<success_criteria>
- `apps/web/features/llm/` triplet (api + components + hooks) lands per CONTEXT D-D1.
- ByokForm.tsx satisfies UI-SPEC layout, visual hierarchy, spacing, typography, color, copywriting, interaction, and accessibility contracts.
- Raw API key never enters React state, never persists in any client-side storage (verified by 4 invariant tests).
- Form mounted on `/settings` between automated-triage and privacy cards.
- i18n keys (`llm.byok.*` + `errors.llm.*`) lock-step in vi+en; STRICT check passes.
- 5 component tests + 4 invariant tests all green.
- Frontend-design skill invocation logged in summary.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-08-SUMMARY.md` documenting:
- Confirmation that frontend-design skill was invoked before writing ByokForm.tsx
- Whether messages.ts co-located file was used (D-D5) or keys went directly to vi/en bundles (deviation note if so)
- Final i18n key count (added under llm.* + errors.llm.* namespaces)
- Whether `pnpm generate:api` ran from a live backend or from a hermetic openapi.json artifact
- Playwright manual-walk replay command for the user (since automated browser tests against `/settings` need a running stack)
- Pointer for Phase 5: BYOK list-display + revoke-key flow are out-of-scope for v1 (UI-SPEC Section "Destructive confirmation" notes "Out of scope for Phase 02C")
</output>
