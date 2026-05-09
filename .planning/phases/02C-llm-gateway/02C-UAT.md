---
status: diagnosed
phase: 02C-llm-gateway
source: [02C-01-SUMMARY.md, 02C-02-SUMMARY.md, 02C-03-SUMMARY.md, 02C-04-SUMMARY.md, 02C-05a-SUMMARY.md, 02C-05b-SUMMARY.md, 02C-06-SUMMARY.md, 02C-07-SUMMARY.md, 02C-08-SUMMARY.md]
started: 2026-05-09T20:52:23.6525899+07:00
updated: 2026-05-09T21:34:10.3281507+07:00
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running API, worker, and web dev servers. Start the backing services, then start the API, worker, and web app from a clean checkout state. Liquibase applies the BYOK credential changelog without errors, the API and worker boot with the expected LLM configuration behavior, and the settings page loads live data without startup exceptions.
result: pass

### 2. LLM Gateway Boundary Guard
expected: Running the backend verification for the gateway boundary rejects direct Spring AI or vendor SDK usage outside the gateway adapter, while normal API and worker contexts still compile and boot through the shared core module.
result: pass

### 3. Prompt Sanitization and Privacy Logging
expected: Submitting email-like content containing HTML, hidden Unicode controls, and prompt-injection text reaches the model only after HTML stripping, NFC normalization, hidden-control stripping, and token-bound truncation; logs and observations show metadata only, never raw body, prompt, completion, or token bytes.
result: pass

### 4. Tool-Call Allow-List Safety
expected: A model response that requests anything outside the allowed label, archive, or save_draft actions is rejected before any downstream action can run, and the user-facing/API error path reports an LLM safety failure without leaking rejected function names or arguments.
result: pass

### 5. BYOK Validation Surface
expected: On /settings, the AI provider key card appears after automated triage and before privacy. Selecting Anthropic or OpenAI Compatible, entering the required fields, and clicking Validate API key shows a clear success or invalid-key alert without putting the raw key in visible text, URL state, query keys, or browser storage.
result: issue
reported: "thanh cong hay that bai thi phai noi ro ra chu viec kiem tra nay de biet apikey va url api co hop le k ma vi du thanh cong thi bao apikey hop le mau xanh la"
severity: major

### 6. BYOK Save and Current Metadata
expected: Save API key stays disabled until validation succeeds. After saving, the password field clears, the saved state appears, and the current configuration shows only provider, endpoint host when present, and saved timestamp; no decrypted API key or endpoint path/query is returned or rendered.
result: pass

### 7. BYOK Gateway Routing and Credit Bypass
expected: When a tenant has a saved BYOK key, later LLM calls use that tenant's provider path, zero the decrypted key after the call, and do not reserve, settle, release, or deduct platform credits.
result: pass

### 8. Platform Credit Gate and 402 Prompt
expected: When a tenant has no BYOK key, platform LLM calls reserve and settle credits on success, release credits on model or safety failure, and return the localized platform-credits-depleted prompt when the tenant has insufficient prepaid credits.
result: pass

### 9. Drift Detection Operational Check
expected: With drift detection disabled, the worker skips the scheduled job. When enabled in a controlled environment, it runs the 20 synthetic golden fixtures through LlmGateway.driftCheck, compares against the committed baseline, and logs only aggregate drift counts with no fixture content.
result: pass

### 10. BYOK Settings Browser Flow
expected: In a real desktop and mobile browser, the /settings BYOK card keeps the single-column layout, visible labels, keyboard-operable provider selector, disabled/enabled button states, validation alerts, saved metadata, Vietnamese/English copy parity, and no console errors through the validate-then-save flow.
result: pass

## Summary

total: 10
passed: 9
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "On /settings, validation clearly communicates whether the API key and API URL are valid, with success styled as a green valid-key state and failure styled as an invalid-key state."
  status: failed
  reason: "User reported: thanh cong hay that bai thi phai noi ro ra chu viec kiem tra nay de biet apikey va url api co hop le k ma vi du thanh cong thi bao apikey hop le mau xanh la"
  severity: major
  test: 5
  root_cause: "ByokForm renders successful validation with the default Alert style and the llm.byok.validation.success copy only says the key was checked, not that the API key and endpoint/model are valid. There is no dedicated green success visual treatment or explicit success/failure wording tied to API key and API URL validity."
  artifacts:
    - path: "apps/web/features/llm/components/ByokForm.tsx"
      issue: "validationResult.ok renders <Alert role=\"status\"> without success-specific green styling or an explicit title/body structure for API key/API URL validity."
    - path: "apps/web/features/llm/messages.ts"
      issue: "llm.byok.validation.success copy is too vague for the validation purpose and does not say the API key/API URL is valid."
    - path: "apps/web/features/llm/components/ByokForm.test.tsx"
      issue: "tests assert the current vague success message and do not require green success semantics."
    - path: "apps/web/e2e/byok.spec.ts"
      issue: "browser flow checks for 'Key validated' but not explicit valid-key wording or green success styling."
  missing:
    - "Change success copy to explicitly state the API key and configured API endpoint/model are valid."
    - "Apply green success styling to validation success and save success alerts using existing semantic green tokens/classes."
    - "Update component and Playwright tests to require explicit valid-key wording and success styling."
  debug_session: "inline-gsd-verify-work-02C-2026-05-09"
