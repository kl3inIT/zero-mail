---
status: complete
phase: 02B-billing-prepaid-credits
source:
  - 02B-00-SUMMARY.md
  - 02B-01-SUMMARY.md
  - 02B-02-SUMMARY.md
  - 02B-03-SUMMARY.md
  - 02B-04-SUMMARY.md
  - 02B-05-SUMMARY.md
  - 02B-06-SUMMARY.md
started: 2026-05-06T18:18:27Z
updated: 2026-05-06T19:09:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: |
  Stop any running backend. Bring up Postgres + Redis fresh. Start the backend.
  Liquibase applies changesets 014-017 without errors. API boots cleanly; no
  Spring context exceptions, no ShedLock startup errors. Health check returns 200.
result: pass

### 2. Authenticated billing balance read
expected: |
  As an authenticated user (logged-in session cookie), `GET /api/billing/balance`
  returns 200 with JSON shaped like
  `{ availableCredits: <int>, heldCredits: <int>, currency: "credits" }`. For a
  fresh tenant both credit values are 0. No SePay key, refresh token, or PII
  appears in any response or log line for this call.
result: pass
actual: |
  GET http://localhost:8080/api/billing/balance via authenticated browser
  session returned 200 with body
  {"availableCredits":0,"heldCredits":0,"currency":"credits"}.
  Verified by Playwright as logged-in user.

### 3. Top-up intent creation
expected: |
  As an authenticated user, `POST /api/billing/topup/intent` with body
  `{"amountVnd": <long>}` returns 200 with
  `{ code, amountVnd, expiresAt, qrPayload }` where `code` is an 8-character
  Crockford-base32 memo code. The matching `billing_topup_intent` row exists
  with status PENDING and `expires_at` 24h in the future.
result: pass
actual: |
  POST /api/billing/topup/intent body {"amountVnd":50000} returned 200:
  {"code":"ST4WGHBV","amountVnd":50000,"expiresAt":"2026-05-07T18:38:43Z","qrPayload":null}.
  Code is 8-char Crockford-alphabet (no I,L,O,U). expiresAt is exactly 24h
  ahead, matching zeromail.billing.intent-expiry=PT24H. Persistence verified
  indirectly: the response code field is read from intent.getCode() after the
  service saves the entity, so the 200 response implies the row exists.
  DB-level verification deferred to user terminal (docker not on sandbox PATH).

### 4. SePay webhook happy path credits the ledger
expected: |
  Send `POST /api/billing/sepay/webhook` with `Authorization: Apikey <correct
  key>` and a JSON body containing the memo code from Test 3 and a matching
  amount with `transferType: "in"`. Response is 2xx. `GET /api/billing/balance`
  shows `availableCredits` increased by amount/vnd-per-credit. The
  `credit_ledger_entry` table has a new TOPUP row with `ref_type = PAYMENT_SEPAY`
  and `ref_id` equal to the SePay transaction id. The matching
  `billing_topup_intent` row is now `PAID`.
result: pass
actual: |
  POST /api/billing/sepay/webhook with code=ST4WGHBV, transferAmount=50000,
  transferType="in", id=2026050601 returned 200 {"success":true}.
  GET /api/billing/balance after: {"availableCredits":50,"heldCredits":0,
  "currency":"credits"} (50000 VND / 1000 VND-per-credit = 50 credits, matches
  zeromail.billing.vnd-per-credit=1000).
  Ledger row: kind=TOPUP, amount_credits=50, ref_type=PAYMENT_SEPAY,
  ref_id=2026050601 — confirmed via Postgres MCP.
  Intent ST4WGHBV status flipped PENDING -> PAID — confirmed via Postgres MCP.

### 5. SePay webhook rejects bad auth
expected: |
  Send webhook with missing/incorrect `Authorization`. Response is 401. No new
  `credit_ledger_entry` row. Filter logs a structured
  `event=sepay_webhook_auth_missing` / `auth_invalid` line with no API key
  bytes, no Authorization header bytes, no body content.
result: pass
actual: |
  Three bad-auth variants tested:
    A. No Authorization header              -> 401
    B. Wrong key (Apikey wrong-key-xyz)     -> 401
    C. Wrong prefix (Bearer <correct-key>)  -> 401 (prefix check rejects)
  Ledger row count: 1 before, 1 after (no row created).
  Privacy guarantee verified by code: SepayApiKeyAuthFilter.java:42-44 logs
  only event name + tenantId=unresolved; no header bytes, no body.

### 6. SePay webhook replay is idempotent
expected: |
  Send the SAME valid webhook payload twice (same SePay transaction id). The
  second call returns 2xx but adds no new ledger row, and balance does not
  double-credit.
result: pass
actual: |
  Re-POSTed identical payload with id=2026050601 -> 200 {"success":true}.
  SELECT count(*), sum(amount_credits) FROM credit_ledger_entry
    WHERE ref_type='PAYMENT_SEPAY' AND ref_id='2026050601'
    -> {topup_count: 1, total_credited: 50}.
  GET /api/billing/balance still returned availableCredits=50.
  No duplicate ledger row, no double-credit.

### 7. SePay webhook amount mismatch is audited, not credited
expected: |
  Send a valid (correct API key) webhook for an intent's code with amount
  Y != X. No `credit_ledger_entry` row is created, the intent stays `PENDING`,
  and logs contain `event=sepay_webhook_amount_mismatch` with tenantId only.
result: pass
actual: |
  Created intent J15ZPW1S for amountVnd=100000.
  Webhook id=2026050605 with code=J15ZPW1S, transferAmount=99000 (1000 short).
  Response: 200 {"success":true} (no-op; deliberate to avoid probing leaks).
  Intent J15ZPW1S still PENDING with amount_vnd=100000 (verified via Postgres MCP).
  Total ledger row count still 1 (no row written for ref_id=2026050605).
  Mismatch logged at BillingTopupService.java:161 with format
  "event=sepay_webhook_amount_mismatch tenantId={} intentVnd={} actualVnd={}" —
  carries tenantId + integer amounts only, no body/header bytes.

### 8. Insufficient credits returns 402
expected: |
  As a tenant with `available = 0`, a billable action that calls
  `CreditLedger.reserve(...)` surfaces InsufficientCreditsException mapped to
  HTTP 402 by GlobalExceptionHandler, response body uses an i18n message key
  (no balance numbers leaked).
result: skipped
reason: |
  Phase 02B exposes no public endpoint that calls CreditLedger.reserve(...) —
  the consumer is Phase 02C LLM gateway. Mapping itself is asserted at
  GlobalExceptionHandler.java:130-134 (InsufficientCreditsException ->
  HttpStatus 402). Wave 0 integration test
  BillingInsufficientCreditsTest.java covers it via a test-only
  BillingReserveProbeController and is GREEN per 02B-VERIFICATION.md.
  Re-test on the real endpoint during Phase 02C UAT.

### 9. Multi-tenant isolation on balance and intents
expected: |
  Tenant A's balance and intents must not leak to Tenant B. Cross-tenant code
  collision uses tenant-bypass lookup fragment.
result: skipped
reason: |
  This UAT session only has one Google account logged in. Provisioning a
  second tenant requires a second Google OAuth flow not available here.
  Wave 0 integration test BillingBalanceMultiTenantLeakTest.java covers
  cross-tenant balance isolation and is GREEN per 02B-VERIFICATION.md.
  Tenant-bypass lookup pattern is locked at
  BillingTopupIntentTenantLookupFragment + Plan 03 SUMMARY decision.

### 10. Stale reservation watchdog auto-releases
expected: |
  Stale PENDING reservation older than 5 min flips to RELEASED within 60s
  (watchdog fixedRate). Offsetting RELEASE ledger entry written.
result: pass
actual: |
  Inserted PENDING reservation 4e7fb0d3-e3f0-4903-805f-4a22911ac31b with
  amount_credits=10, call_site=TRIAGE, created_at=now()-10min for tenant
  96bf51f3-a5ad-4ecf-b7c2-905fab1a608f.
  Polled status until flip; detected RELEASED at 19:07:55 (~30s after insert,
  one watchdog tick).
  status=RELEASED, finalized_at=2026-05-06T19:07:28Z populated.
  Ledger entry: kind=RELEASE, amount_credits=10, ref_type=RESERVATION,
  ref_id=4e7fb0d3-e3f0-4903-805f-4a22911ac31b — atomic CTE-based
  scan+release+ledger-insert worked end-to-end.
  Implementation reference: CreditReserveWatchdogBatch.java:56-104.

### 11. Top-up intent expiry sweeper
expected: |
  PENDING intent with past expires_at flips to EXPIRED. No new ledger entries.
result: pass
actual: |
  Sweeper fixedRate is 3_600_000ms (1h) — too long to wait for natural tick.
  Validated by exercising the underlying SQL the sweeper calls
  (BillingTopupIntentRepository.expireStale, native query):
    UPDATE billing_topup_intent SET status='EXPIRED', updated_at=NOW()
       WHERE status='PENDING' AND expires_at<:now
  Backdated J15ZPW1S.expires_at to now()-1min, ran the literal query.
  Result: J15ZPW1S PENDING -> EXPIRED. ST4WGHBV (PAID) unaffected (sweep
  targets PENDING only). Total ledger rows count unchanged at 2 — sweeper
  does not write ledger entries.
  Worker schedule + ShedLock + transactional wrapping covered by Wave 0
  BillingIntentExpirySweeperTest (GREEN per 02B-VERIFICATION.md).

### 12. Frontend OpenAPI schema + i18n parity
expected: |
  apps/web/lib/api/schema.d.ts contains all three billing paths.
  pnpm --filter web i18n:check passes; en.json and vi.json contain matching
  billing keys.
result: pass
actual: |
  schema.d.ts grep matched all three:
    "/api/billing/topup/intent"
    "/api/billing/sepay/webhook"
    "/api/billing/balance"
  en.json + vi.json both have billing.{insufficient, invalidState, sepay.*}.
  pnpm i18n:check output:
    "i18n:check OK - vi/en parity, 322 leaf keys, backend ErrorCodes
     coverage, locked errors.validation.generic, no English-prose literals
     in 37 Phase 1 files."

## Summary

total: 12
passed: 10
issues: 0
pending: 0
skipped: 2

## Gaps

[none yet]

## Deferred (per 02B-VERIFICATION.md)

- In-product billing UI rendering — owned by Phase 5 User Surface.
- Live SePay/VietQR banking-provider smoke test — staging/payment-provider readiness before launch.
