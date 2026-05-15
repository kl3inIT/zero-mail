---
phase: 02C-llm-gateway
plan: GAP-01
type: execute
mode: gap_closure
gap_closure: true
depends_on: [08]
files_modified:
  - apps/web/features/llm/components/ByokForm.tsx
  - apps/web/features/llm/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/features/llm/components/ByokForm.test.tsx
  - apps/web/e2e/byok.spec.ts
requirements: [LLM-03]
source: 02C-UAT.md
---

# Phase 02C Gap 01 Plan: Explicit BYOK Validation Result

## Objective

Close UAT Test 5 gap: the BYOK validation result must clearly tell the user whether the API key and configured API URL/model are valid. A successful validation must read as success, including green success styling, instead of a neutral checked-state message.

## Root Cause

`ByokForm` renders `validationResult.ok === true` with the default `Alert` appearance and the copy key `llm.byok.validation.success`. The current Vietnamese copy says only "Khóa đã được kiểm tra. Bạn có thể lưu cấu hình này.", which confirms that validation ran but does not explicitly say the API key and API URL/model are valid. The tests only assert this vague text, so the issue was not caught.

## Implementation Tasks

1. Update `apps/web/features/llm/messages.ts`.
   - Replace `llm.byok.validation.success` with explicit validity wording:
     - Vietnamese: "Khóa API và cấu hình API hợp lệ. Bạn có thể lưu cấu hình này."
     - English: "API key and API configuration are valid. You can save this configuration."
   - Keep vi/en semantic parity.

2. Update `apps/web/features/llm/components/ByokForm.tsx`.
   - Render validation success with green success styling using existing semantic tokens/classes, for example a green border/background/text treatment.
   - Keep `role="status"` and `aria-live="polite"`.
   - Preserve raw API key privacy: do not put key bytes in React state, query keys, logs, URL params, or rendered text.
   - Do not introduce a wrapper component unless repeated success-alert structure appears three or more times.

3. Regenerate i18n bundles.
   - Run `pnpm -C apps/web i18n:build` or the repo-approved equivalent so `apps/web/i18n/messages/{vi,en}.json` match `messages.ts`.

4. Update tests.
   - In `ByokForm.test.tsx`, assert the explicit valid API key/configuration wording.
   - Add an assertion that the success alert has the expected green success class or semantic success styling.
   - In `apps/web/e2e/byok.spec.ts`, update the browser assertion from generic "Key validated" to the explicit valid-key/configuration copy.

## Acceptance Criteria

- Success validation says the API key and API configuration/API URL are valid in Vietnamese and English.
- Success validation is visually green and distinct from destructive validation failure.
- Invalid validation still uses destructive styling and recovery copy.
- Save remains disabled until validation succeeds and is disabled again when fields change.
- Raw API key is still only read from the uncontrolled password input at validate/save time.
- No layout regression on 375x812 mobile.

## Verification

Run:

```powershell
pnpm -C apps/web i18n:check
pnpm -C apps/web exec vitest run features/llm/components/ByokForm.test.tsx __tests__/byok-key-handling.test.ts __tests__/i18n-erase-protection.test.ts
pnpm -C apps/web exec playwright test e2e/byok.spec.ts --reporter=line
```

Manual browser check:

- Open `/settings`.
- Enter a valid provider/model/API key/API URL combination.
- Click `Kiểm tra khóa API`.
- Confirm the validation result says the API key/configuration is valid and appears green.
