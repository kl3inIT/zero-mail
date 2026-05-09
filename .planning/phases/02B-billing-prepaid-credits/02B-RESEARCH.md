# Phase 2B: Billing (Prepaid Credits) — Research

**Researched:** 2026-05-05
**Domain:** Postgres-backed credit ledger, atomic concurrency control, Vietnamese bank-transfer webhook (SePay), Spring Modulith package boundary, distributed lock for `@Scheduled` watchdog.
**Confidence:** HIGH — most discretion areas verified against in-repo code or official docs; **one critical SPEC contradiction found** (SePay does NOT use HMAC-SHA256; it uses an `Authorization: Apikey ...` static-secret header).

---

## Summary

This phase ships `core.billing` as a Spring Modulith leaf module with a single `CreditLedger` interface (consumed verbatim by Phase 2C), a 3-table schema (append-only journal + sidecar reservation + intent), atomic `reserve` via `pg_advisory_xact_lock(hashtext(tenant_id::text))`, a 60-second worker watchdog, a Vietnamese-bank-transfer webhook receiver, and HTTP 402 + i18n error mapping.

**Critical research finding (overrides SPEC.md hypothesis):** SePay's webhook authentication is a **static API key in the `Authorization` header** — `Authorization: Apikey YOUR_API_KEY` — not HMAC-SHA256 + `X-SePay-Signature`. SePay's official docs confirm three options (OAuth 2.0, API Key, no auth) and never mention HMAC. The plan-phase MUST update the SPEC's hypothesized HMAC approach to a constant-time API-key comparison. This is the largest planning-relevant correction in this research; everything else aligns with what `02B-CONTEXT.md` already locks.

**Primary recommendation:**
1. Replace the `HMAC-SHA256 + X-SePay-Signature` design with `Authorization: Apikey ${SEPAY_WEBHOOK_API_KEY}` constant-time comparison via `MessageDigest.isEqual`. Keep `:?` fail-fast and `@DynamicPropertySource` test wiring — only the verifier algorithm changes.
2. Watchdog uses **ShedLock 7.x** (`net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template:7.7.0`) with a Liquibase-managed `shedlock` table — there is no current `@SchedulerLock` infra in the repo (verified by `Grep` on `SchedulerLock`/`ShedLock`/`net.javacrumbs` returning only this phase's planning docs).
3. Crockford base32 hand-rolled (~30 LOC) — `commons-codec` is NOT a direct dependency; pulling it in for one alphabet is overkill.
4. All other CONTEXT.md decisions (advisory lock, sidecar table, intent table, REQUIRES_NEW, virtual-thread test, fold CR-04) remain correct.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

(Copy from `02B-CONTEXT.md` `<decisions>` block; reproduced here verbatim because the planner consumes this section as authoritative.)

- **A. Concurrency control for atomic reserve**
  - **D-A1:** `pg_advisory_xact_lock(hashtext(tenant_id::text))` per-tenant inside the reserve transaction. Auto-released on commit. Wraps `SELECT SUM(amount_credits)` + `INSERT credit_reservation` + `INSERT credit_ledger_entry RESERVE`. ArchUnit guards advisory-lock SQL to `core.billing.persistence.lowlevel`.
  - **D-A2:** `Propagation.REQUIRES_NEW` on `CreditLedgerService.reserve`. `settle`/`release` run in caller's transaction (`Propagation.REQUIRED`).
  - **D-A3:** Concurrent-reserve test pattern — 10 virtual threads × `reserve(tenantId, CallSite.TRIAGE)` against `available=5` → exactly 5 successes + 5 `InsufficientCreditsException`. `Thread.startVirtualThread` + `CountDownLatch` (or `StructuredTaskScope`).

- **B. Reservation tracking schema**
  - **D-B1:** Sidecar `credit_reservation` table (id, tenant_id, amount_credits, call_site, status, created_at, finalized_at, version). Partial index `WHERE status = 'PENDING'` on `created_at`. B-tree on `(tenant_id, status)`.
  - **D-B2:** Journal append-only; sidecar mutates per reservation lifecycle. UNIQUE on `credit_ledger_entry(ref_type, ref_id, kind)` makes SETTLE/RELEASE idempotent at journal layer.
  - **D-B3:** Watchdog query: `SELECT id FROM credit_reservation WHERE status='PENDING' AND created_at < now() - INTERVAL '5 minutes' LIMIT 100 FOR UPDATE SKIP LOCKED`.

- **C. SePay referenceCode + intent table**
  - **D-C1:** `billing_topup_intent` (id, tenant_id, code, amount_vnd, status, created_at, expires_at, paid_at, sepay_transaction_id). 8-char Crockford base32 code (alphabet `0-9A-Z` excluding `ILOU`). Collision retry up to 3 times.
  - **D-C2:** `POST /api/billing/topup/intent` body `{ amountVnd: long }` → response `{ code, amountVnd, expiresAt, qrPayload? }`. Max 5 PENDING intents per tenant.
  - **D-C3:** Webhook resolution: parse SePay payload's `referenceCode`, normalize to uppercase, lookup `billing_topup_intent WHERE code = ?`. Unknown code/amount mismatch/expired → 200 OK + opaque event log. Happy path: same-txn UPDATE intent + INSERT TOPUP entry; UNIQUE on `sepay_transaction_id` makes replay a no-op.
  - **D-C4:** `BillingIntentExpirySweeper` `@Scheduled(fixedRate = 3_600_000)` (1 hour) marks expired PENDING intents.

- **D. 2B ↔ 2C lifecycle contract**
  - **D-D1:** Phase 2C calls `settle(rid)` on success path, `release(rid)` on exception path.
  - **D-D2/D3:** `settle`/`release` idempotent on repeat call; second call on opposite final state throws `IllegalLedgerStateException`.
  - **D-D4:** `IllegalLedgerStateException` is a `RuntimeException` mapped to HTTP 500 + `code=BILLING_LEDGER_INVALID_STATE`.

- **E. SePay webhook test strategy**
  - **D-E1:** No WireMock — webhook is inbound. Tests POST signed fixture payloads via `@AutoConfigureMockMvc` or `RestClient + LocalServerPort`.
  - **D-E2:** Two layers — pure-unit `SePaySignatureVerifier` test + `@SpringBootTest` integration.
  - **D-E3:** Synthetic JSON fixture verified against SePay docs — **see Critical Override below; the fixture and verifier shape change because SePay does not HMAC-sign**.

- **F. Deployment-secret hardening**
  - **D-F1:** `:?` fail-fast in BOTH `backend/api` and `backend/worker` `application.yml`.
  - **D-F2:** `@DynamicPropertySource` injects test value.

- **G. Spring Modulith package boundary**
  - **D-G1:** `@ApplicationModule(displayName="Billing", allowedDependencies={"tenant", "shared.persistence", "shared.lang"})`.
  - **D-G2:** Sub-packages `core.billing.{model, service, persistence, persistence.lowlevel}`.
  - **D-G3:** ArchUnit — `CreditLedgerService` cannot be instantiated outside `core.billing.service`; `CallSite` membership locked to `{TRIAGE, DRAFT, PREVIEW}`.

- **H. Liquibase changesets**
  - **D-H1:** 2B claims `014-credit-ledger-entry.yaml`, `015-credit-reservation.yaml`, `016-billing-topup-intent.yaml`. Add `017-shedlock-table.yaml` if ShedLock is selected (see Distributed-Lock decision below).
  - **D-H2:** Phase 2C renumbers BYOK changeset to `017+` (or `018+` if 017 is consumed by ShedLock).

- **I. Privacy logging**
  - **D-I1/D-I2/D-I3:** Opaque `event=` names, no payload bytes, no signature header bytes, no `transactionId` in logs.

### Claude's Discretion

- **SePay payload field names + auth header** — research-time delegation. **RESOLVED: see "Critical Override" below.**
- **Crockford base32 alphabet implementation** — `commons-codec` vs hand-rolled. **RESOLVED: hand-rolled. `commons-codec` is NOT in `libs.versions.toml` or any module's `build.gradle.kts`; only `logstash-logback-encoder` and `google-auth-library` pull it transitively, which we should not depend on.** Hand-rolled implementation is ~30 LOC and lives in `core.billing.service.TopupCodeGenerator`.
- **`CreditReservationEntity` base class** — `AbstractAuditableEntity` vs `AbstractTenantOwnedEntity`. **RESOLVED: `AbstractTenantOwnedEntity`** — sidecar IS tenant-owned per CONTEXT note.
- **Watchdog batch size** — locked at 100 in D-B3.
- **Intent expiry sweeper batch size** — open. Recommend 500 (1-hour sweep, low contention).
- **`BillingController` sub-folder grouping** — `backend/api/controllers/billing/` ✓ (parity with Phase 1.2.1 DTO group-by-domain).
- **`vnd-per-credit` default** — recommend `1000` (1 credit ≈ 1k VND ≈ $0.04 USD).
- **i18n key copy** — `vi: "Số dư tín dụng không đủ — vui lòng nạp thêm để tiếp tục"`, `en: "Insufficient credits — top up to continue"`.
- **Distributed lock for watchdog** — locked to **ShedLock 7.7.0** (see "Don't Hand-Roll" + "Architecture Patterns" sections). The CONTEXT phrasing `@SchedulerLock or equivalent` accepts this.

### Deferred Ideas (OUT OF SCOPE)

- Refunds / chargeback automation
- PDF receipts / invoice email
- Credit bundles / package pricing UI
- Per-action cost preview UI
- Multi-currency support
- `/settings/billing` page (Phase 5)
- Soft-warn at low-balance threshold
- Rate limiting on SePay webhook beyond auth check
- `SEPAY_WEBHOOK_API_KEY` rotation drill
- Admin-facing billing dashboard
- LLM USD spend tracking + per-tenant daily spend cap (Phase 2C)
- `tenant_byok_credentials` table (Phase 2C)

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BILL-01 | User can purchase prepaid credits via payment provider | Phase 2B replaces "Stripe or LemonSqueezy" REQUIREMENTS.md text with **SePay**; webhook receiver design verified against SePay docs (see Critical Override). |
| BILL-02 | Each billable action deducts credits via double-entry Postgres ledger | `credit_ledger_entry` schema locked in SPEC; UNIQUE constraint pattern verified against existing `pubsub_delivery` ON CONFLICT precedent. |
| BILL-03 | Credit reserve/settle/release prevents double-charge under concurrency | `pg_advisory_xact_lock` semantics confirmed via PostgreSQL 17 docs; 10-thread test pattern proven by `MultiTenantLeakIntegrationTest` (Phase 1 FND-05) using `StructuredTaskScope` (Java 25 stable API). |
| BILL-04 | Scheduled watchdog sweeps stale credit holds | `@Scheduled` already in worker (`GmailWatchScheduler`); ShedLock 7.7.0 verified Boot-4 compatible (no in-repo `@SchedulerLock` infra exists today — must add). |
| BILL-05 | User sees real-time credit balance | `BillingController.balance()` thin-controller pattern matches `TenantStatusController` (Phase 1 reference). |
| BILL-06 | System blocks billable actions when balance insufficient | HTTP 402 + `ApiError` shape verified — `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, dotted i18n key `error.billing.insufficient` matches `ErrorCodes.java` convention. |
| BILL-07 | BYOK actions do not consume platform credits | Documented at interface boundary (Javadoc + ArchUnit enum-membership lock). Phase 2C consumes the contract. |

---

## Critical Override: SePay Authentication is API Key, NOT HMAC

**This invalidates SPEC.md Requirement 5 + CONTEXT.md D-E2 algorithm choice. Plan-phase MUST adapt.**

### What CONTEXT/SPEC hypothesized
- `X-SePay-Signature` header carrying HMAC-SHA256(secret, body) in hex/base64.
- `SEPAY_WEBHOOK_SECRET` env var.
- `SePaySignatureVerifier` computing HMAC and comparing constant-time.

### What SePay actually does (verified against `developer.sepay.vn` and `docs.sepay.vn`, 2026-05-05)
- Three configurable options at webhook setup time: **OAuth 2.0** (token URL + client ID/secret), **API Key** (static secret), or **No auth** (IP allowlist only).
- API Key mode sends header: `Authorization: Apikey YOUR_API_KEY` (literal word `Apikey`, single space, then the secret).
- **No HMAC, no body signature, no replay nonce, no timestamp header.** [CITED: developer.sepay.vn/en/sepay-webhooks/tich-hop-webhook]
- SePay-recommended hardening: **IP allowlist** the SePay outbound IPs in addition to API key. [CITED: developer.sepay.vn/en/sepay-webhooks/lap-trinh-webhook]
- Success response contract: `200 OK` with body `{"success": true}`; failure: `{"success": false, "message": "..."}`. SePay retries non-2xx.

### Implementation impact

| Change area | Old (SPEC hypothesis) | New (research-verified) |
|-------------|----------------------|------------------------|
| Env var name | `SEPAY_WEBHOOK_SECRET` | `SEPAY_WEBHOOK_API_KEY` (rename for clarity) |
| Verifier class | `SePaySignatureVerifier` | `SePayApiKeyVerifier` |
| Verifier algorithm | `Mac.getInstance("HmacSHA256")` over body | `MessageDigest.isEqual(expected.getBytes(UTF_8), provided.getBytes(UTF_8))` |
| Filter behavior | Read header, recompute HMAC, compare | Read `Authorization` header, strip `Apikey ` prefix, constant-time compare |
| Test fixture | Pre-signed body | Body + `Authorization: Apikey test-secret-value` header |
| `ErrorCodes` | n/a | Add `BILLING_SEPAY_AUTH_INVALID = "error.billing.sepay.auth_invalid"` |
| Response contract | `200 OK` (empty) | `200 OK {"success": true}` on accept; `401` (no body) on bad key |
| Privacy logging | `event=sepay_webhook_signature_invalid` | `event=sepay_webhook_auth_invalid` (no header bytes — strip `Apikey ` and discard before logging) |

**Mitigations for the API-key-only model (vs HMAC):**
1. **Constant-time compare** still required (the API key is the only secret on the wire, so timing attacks against it remain a real concern). Use `MessageDigest.isEqual`.
2. **IP allowlist as a second layer** — recommended in SePay docs. Not in scope for v1 (per CONTEXT "rate limiting beyond signature check is deferred"); document as Phase 6 hardening item.
3. **Rotate the key** if SePay's outbound infra is compromised — already covered by the deferred "SEPAY_WEBHOOK rotation drill" item.

**Why constant-time still matters with API key:** any timing leak on the comparison lets an attacker iterate byte-by-byte. `MessageDigest.isEqual` (Java SE 6u17+) is documented as constant-time. [CITED: codahale.com/a-lesson-in-timing-attacks; pixelstech.net Arrays.equals vs MessageDigest.isEqual]

**Replay protection:** preserved — UNIQUE constraint on `billing_topup_intent.sepay_transaction_id` (and on `credit_ledger_entry(ref_type, ref_id, kind)`) makes a duplicate POST a no-op at the persistence layer. The auth scheme change does NOT affect idempotency.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Credit balance read | API / Backend (`BillingController`) | — | Tenant-scoped, requires session auth, thin controller delegates to service. |
| Credit reserve/settle/release | API / Backend (`CreditLedgerService`) — direct callers in 2B; consumer is `core.llm.LlmGateway` in 2C | Database (advisory lock + journal table) | All ledger operations are pure server-side; no client involvement. Atomicity owned by DB transaction + advisory lock. |
| SePay webhook receive | API / Backend (`SepayWebhookController`) | — | Inbound HTTP from SePay infrastructure to public HTTPS endpoint on the VPS. No frontend involvement. |
| Top-up intent creation | API / Backend (`POST /api/billing/topup/intent`) | — | Tenant-scoped session auth; returns code for user to copy into bank-transfer memo. |
| Stale-reserve watchdog | Worker (`backend/worker.billing.CreditReserveWatchdog`) | Database (`SKIP LOCKED` + ShedLock table) | Mirrors Phase 2A's `GmailWatchScheduler` placement. |
| Intent expiry sweep | Worker (`backend/worker.billing.BillingIntentExpirySweeper`) | Database | Same module + scheduling infra as the watchdog. |
| Top-up QR rendering | Frontend (Phase 5) | — | OUT of 2B scope; backend exposes `qrPayload` field but does not render. |
| Credit balance display | Frontend (Phase 5) | — | OUT of 2B scope; backend exposes `/api/billing/balance` only. |
| BYOK detection | API / Backend (`core.llm` Phase 2C) | — | OUT of 2B scope; 2C is responsible for skipping `reserve` when BYOK row exists. |

---

## Standard Stack

### Core (already on classpath via `libs.versions.toml` / Boot 4.0.6 BOM)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Framework | Locked. [VERIFIED: `gradle/libs.versions.toml` line 2] |
| Spring Framework | 7.0.7 | Core | BOM-managed. [CITED: `.planning/research/STACK.md`] |
| Spring Data JPA + Hibernate 7 | 4.0.x / 7.x | Aggregate persistence | Existing pattern for `UserEntity`, `OnboardingSelectionEntity`, etc. [VERIFIED: `backend/core/build.gradle.kts` line 11] |
| Liquibase | 5.0.2 | Schema migrations | Locked + already on classpath. [VERIFIED: `gradle/libs.versions.toml` line 9, `backend/core/build.gradle.kts` line 14] |
| Jakarta Validation 3.1 | (Boot-managed) | Bean validation | Already used by `ZeroMailApiProperties`. [VERIFIED: `backend/api/src/main/java/com/zeromail/api/config/ZeroMailApiProperties.java` line 11] |
| PostgreSQL JDBC | (Boot-managed) | Driver | Already runtime-only. [VERIFIED: all three module `build.gradle.kts`] |
| Logback + logstash-encoder | 9.0 | Structured JSON logs | [VERIFIED: `gradle/libs.versions.toml` line 11] |
| Spring Modulith | 2.0.7-SNAPSHOT | Module boundary enforcement | [VERIFIED: `gradle/libs.versions.toml` line 8] |
| ArchUnit | 1.4.2 | Architectural tests | [VERIFIED: `gradle/libs.versions.toml` line 10] |
| `java.security.MessageDigest` | JDK 25 | Constant-time API-key compare | JDK builtin, no dep. |
| `java.util.HexFormat` | JDK 17+ (stable in 25) | Hex-encoded test fixture support | JDK builtin. |
| `java.util.concurrent.StructuredTaskScope` | JDK 25 stable | Concurrent-reserve test scaffold | [VERIFIED: existing usage in `MultiTenantLeakIntegrationTest.java` line 5] |

### Supporting (NEW — to be added to `libs.versions.toml`)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **ShedLock Spring** | **7.7.0** | `@SchedulerLock` distributed-lock annotation | **Required** for `CreditReserveWatchdog` and `BillingIntentExpirySweeper` to be safe under N>1 worker pods. ShedLock 7.x is the Spring-Boot-4-compatible line. [VERIFIED: github.com/lukas-krecan/ShedLock README compatibility matrix as of 2026-05-05] |
| **ShedLock JDBC Provider** | **7.7.0** | Postgres `LockProvider` backend | Pairs with ShedLock Spring; uses a `shedlock` table managed via Liquibase. |

**Add to `libs.versions.toml`:**
```toml
[versions]
shedlock = "7.7.0"

[libraries]
shedlock-spring = { module = "net.javacrumbs.shedlock:shedlock-spring", version.ref = "shedlock" }
shedlock-provider-jdbc-template = { module = "net.javacrumbs.shedlock:shedlock-provider-jdbc-template", version.ref = "shedlock" }
```

**Add to `backend/worker/build.gradle.kts`:**
```kotlin
implementation(libs.shedlock.spring)
implementation(libs.shedlock.provider.jdbc.template)
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ShedLock | Hand-rolled Postgres advisory-lock-on-scheduler-name | Saves a dep but reinvents `@SchedulerLock` semantics + the locked_by/locked_at audit columns ShedLock gives free. Maintenance debt for ~30 LOC saved. **Not recommended.** |
| ShedLock | Native Postgres advisory lock + custom `@Scheduled` wrapper | Same tradeoff — duplicates ShedLock's lock-extension and locked_until safeguards. |
| `commons-codec` Base32 | Hand-rolled Crockford base32 | `commons-codec` is NOT a direct dep; pulling it in for one alphabet adds ~280 KB to the worker container for ~30 LOC saved. **Hand-rolled wins.** Java 25 has no built-in Base32. |
| HMAC-SHA256 webhook auth | API key static-secret auth | **Forced by SePay docs** — SePay does not offer HMAC. (See Critical Override.) |
| `WebClient` outbound to SePay | None | SePay's webhook is INBOUND only — no outbound calls in 2B scope. Phase 5 may add a "ping SePay sandbox" smoke test, deferred. |

**Installation summary (delta from current state):**
```bash
# Add ShedLock 7.7.0 to gradle/libs.versions.toml (above)
# Add 016/017 Liquibase changesets for tables (see Architecture Patterns)
# No npm changes — frontend regenerates schema.d.ts after springdoc emits new endpoints
```

**Version verification log (2026-05-05):**
- ShedLock 7.7.0 latest: [VERIFIED via github.com/lukas-krecan/ShedLock releases page; Boot-4 line is 7.x]
- Spring Boot 4.0.6: [VERIFIED via `libs.versions.toml`]
- Liquibase 5.0.2: [VERIFIED via `libs.versions.toml`]

---

## Architecture Patterns

### System Architecture Diagram

```
                                          ┌────────────────────────┐
                                          │  User's Bank App       │
                                          │  (transfers VND with   │
                                          │   memo = intent.code)  │
                                          └───────────┬────────────┘
                                                      │ bank-internal
                                                      ▼
                                          ┌────────────────────────┐
                                          │  SePay Aggregator      │
                                          │  (parses bank SMS)     │
                                          └───────────┬────────────┘
                                                      │ POST + Authorization: Apikey ...
                                                      │ (public HTTPS over reverse proxy)
                                                      ▼
                                ┌──────────────────────────────────────────┐
   ┌────────────────────┐       │  backend/api  (Spring MVC + virt threads)│
   │  Frontend (Phase 5)│──────►│                                          │
   │  GET /balance      │       │  ┌─────────────────────────────────┐     │
   │  POST /topup/intent│       │  │ @Order(1) BillingWebhookSecurity│     │  ───┐
   └────────────────────┘       │  │   matcher /api/billing/sepay/** │     │     │ permits webhook only,
                                │  │   permitAll + SepayApiKeyFilter │◄────┼──── │ verifies Authorization
                                │  └─────────────┬───────────────────┘     │     │ header constant-time
                                │                │ accepted                │     │
                                │                ▼                         │     │
                                │  ┌─────────────────────────────────┐     │     │
                                │  │ SepayWebhookController          │     │     │
                                │  │   @PostMapping ...sepay/webhook │     │     │
                                │  │   delegates to BillingTopupSvc  │     │     │
                                │  └─────────────┬───────────────────┘     │     │
                                │                │                         │     │
                                │  ┌─────────────────────────────────┐     │     │
                                │  │ BillingController               │     │     │
                                │  │   GET /api/billing/balance      │◄────┼─────┘ session-auth chain @Order(2)
                                │  │   POST /api/billing/topup/intent│     │
                                │  └─────────────┬───────────────────┘     │
                                │                │                         │
                                │                ▼                         │
                                │  ┌─────────────────────────────────┐     │
                                │  │ core.billing.service             │    │
                                │  │   CreditLedger (interface)       │    │
                                │  │   CreditLedgerService (impl)     │    │
                                │  │   BillingTopupService            │    │
                                │  │     - reserve  REQUIRES_NEW      │    │
                                │  │     - settle   REQUIRED          │    │
                                │  │     - release  REQUIRED          │    │
                                │  │     - balance  read-only         │    │
                                │  │     - createIntent / handleSePay │    │
                                │  └─────────────┬───────────────────┘     │
                                │                │ JPA + advisory lock      │
                                │                ▼                         │
                                │  ┌──────────────────────────────────┐    │
                                │  │ core.billing.persistence          │   │
                                │  │   CreditLedgerEntryEntity         │   │
                                │  │   CreditReservationEntity         │   │
                                │  │   BillingTopupIntentEntity        │   │
                                │  │   ↓                               │   │
                                │  │ core.billing.persistence.lowlevel │   │
                                │  │   AdvisoryLockHelper              │   │
                                │  │     - JdbcTemplate.execute(...)   │   │
                                │  │     - "SELECT pg_advisory_xact_lock(hashtext(?))" │
                                │  └──────────────────────────────────┘    │
                                └────────────────┬─────────────────────────┘
                                                 │
                                                 ▼
                                ┌──────────────────────────────────────────┐
                                │  PostgreSQL 17.6  (self-hosted, VPS)      │
                                │   credit_ledger_entry   (append-only)     │
                                │   credit_reservation    (sidecar mut.)    │
                                │   billing_topup_intent  (intent + sepay)  │
                                │   shedlock              (ShedLock cluster)│
                                └────────────────┬─────────────────────────┘
                                                 ▲
                                                 │ JPA + ShedLock JDBC + native SKIP LOCKED
                                ┌────────────────┴─────────────────────────┐
                                │  backend/worker  (Spring + virt threads) │
                                │   CreditReserveWatchdog @Scheduled 60s   │
                                │     @SchedulerLock("creditReserveWatch")│
                                │     SELECT … WHERE status='PENDING'      │
                                │       AND created_at < now()-5m          │
                                │       FOR UPDATE SKIP LOCKED LIMIT 100   │
                                │     → release(rid)                       │
                                │   BillingIntentExpirySweeper @Scheduled 1h│
                                │     UPDATE intent SET status='EXPIRED'   │
                                │       WHERE expires_at < now() ...       │
                                └──────────────────────────────────────────┘
```

**Trace of the primary use case (top-up):**
1. User clicks "Top up" → frontend `POST /api/billing/topup/intent {amountVnd:100000}` → `BillingController.createIntent()` → `BillingTopupService.createIntent(tenantId, vnd)` → INSERT row → return `{code:"7K3RXB2P", amountVnd:100000, expiresAt:..., qrPayload:null}`.
2. User opens bank app, transfers 100000 VND with memo `7K3RXB2P`.
3. SePay parses bank SMS within seconds, POSTs JSON `{id, gateway, transactionDate, accountNumber, code, content, transferType:"in", transferAmount:100000, accumulated, subAccount, referenceCode, description}` with `Authorization: Apikey ${SEPAY_WEBHOOK_API_KEY}`.
4. `@Order(1)` filter chain catches `/api/billing/sepay/webhook`, `SepayApiKeyFilter` constant-time-compares the API key, accepts.
5. `SepayWebhookController.receive(@RequestBody SepayWebhookPayload)` → `BillingTopupService.applyWebhook(payload)`:
   a. Extract `referenceCode` (or `content` as fallback per SePay docs); uppercase-normalize; lookup intent.
   b. Validate `transferType=="in"`, `transferAmount==intent.amountVnd`, `intent.status==PENDING`, `intent.expiresAt>now()`.
   c. Within one TX: UPDATE intent SET status=PAID, sepay_transaction_id=payload.id; INSERT credit_ledger_entry kind=TOPUP ref_type=PAYMENT_SEPAY ref_id=payload.id amount_credits=floor(amountVnd/vndPerCredit).
   d. UNIQUE on `(ref_type, ref_id, kind)` makes replay a `DataIntegrityViolationException` → caught at repo layer → return 200 anyway.
6. Return `{"success": true}` — SePay logs success, stops retrying.

### Recommended Project Structure

```
backend/core/src/main/java/com/zeromail/core/
├── billing/                                # NEW Modulith leaf
│   ├── package-info.java                   # @ApplicationModule(displayName="Billing", allowedDependencies={"tenant","shared.persistence","shared.lang"})
│   ├── model/                              # public API surface
│   │   ├── CallSite.java                   # enum implements IdentifiedEnum {TRIAGE,DRAFT,PREVIEW}
│   │   ├── CreditLedger.java               # interface (consumed by Phase 2C)
│   │   ├── ReservationId.java              # record (UUID wrapper)
│   │   ├── CreditBalance.java              # record (int availableCredits, int heldCredits)
│   │   ├── InsufficientCreditsException.java
│   │   ├── IllegalLedgerStateException.java
│   │   ├── CreditReservationStatus.java    # enum implements IdentifiedEnum {PENDING,SETTLED,RELEASED}
│   │   └── BillingTopupIntentStatus.java   # enum implements IdentifiedEnum {PENDING,PAID,EXPIRED}
│   ├── service/
│   │   ├── CreditLedgerService.java        # @Service implements CreditLedger
│   │   ├── BillingTopupService.java        # @Service createIntent + applyWebhook
│   │   ├── BillingProperties.java          # @ConfigurationProperties("zero-mail.billing") record
│   │   ├── TopupCodeGenerator.java         # hand-rolled Crockford base32
│   │   └── SepayApiKeyVerifier.java        # constant-time API-key compare
│   └── persistence/
│       ├── CreditLedgerEntryEntity.java    # extends AbstractTenantOwnedEntity
│       ├── CreditLedgerEntryRepository.java
│       ├── CreditReservationEntity.java    # extends AbstractTenantOwnedEntity
│       ├── CreditReservationRepository.java
│       ├── BillingTopupIntentEntity.java   # extends AbstractTenantOwnedEntity
│       ├── BillingTopupIntentRepository.java
│       └── lowlevel/
│           ├── package-info.java           # marker only (intra-domain)
│           └── AdvisoryLockHelper.java     # JdbcTemplate raw "SELECT pg_advisory_xact_lock(hashtext(?))"
└── shared/, account/, gmail/, onboarding/, tenant/  (unchanged)

backend/api/src/main/java/com/zeromail/api/
├── controllers/
│   └── billing/                            # NEW DTO group-by-domain (parity with account/, gmail/, onboarding/)
│       ├── BillingController.java          # GET /api/billing/balance + POST /api/billing/topup/intent
│       └── SepayWebhookController.java     # POST /api/billing/sepay/webhook
├── dto/
│   └── billing/                            # NEW
│       ├── BillingBalanceResponse.java     # record (int availableCredits, int heldCredits, String currency)
│       ├── TopupIntentRequest.java         # record (long amountVnd) + @Min validation
│       ├── TopupIntentResponse.java        # record (String code, long amountVnd, Instant expiresAt, String qrPayload)
│       └── SepayWebhookPayload.java        # record matching SePay JSON shape (see fields below)
├── security/
│   └── BillingWebhookSecurityConfig.java   # @Order(1) SecurityFilterChain @ /api/billing/sepay/**
│   └── SepayApiKeyFilter.java              # OncePerRequestFilter mirror of PubSubOidcAuthFilter
├── error/
│   └── ErrorCodes.java                     # ADD: BILLING_INSUFFICIENT_CREDITS, BILLING_LEDGER_INVALID_STATE, BILLING_SEPAY_REFERENCE_INVALID, BILLING_SEPAY_AUTH_INVALID
└── config/
    └── GlobalExceptionHandler.java         # ADD: @ExceptionHandler InsufficientCreditsException → 402; IllegalLedgerStateException → 500

backend/worker/src/main/java/com/zeromail/worker/
└── billing/                                # NEW package
    ├── CreditReserveWatchdog.java          # @Scheduled(fixedRate=60_000) + @SchedulerLock
    ├── BillingIntentExpirySweeper.java     # @Scheduled(fixedRate=3_600_000) + @SchedulerLock
    └── ShedLockConfig.java                 # @Bean LockProvider(JdbcTemplateLockProvider)

backend/core/src/main/resources/db/changelog/changes/
├── 014-credit-ledger-entry.yaml            # NEW
├── 015-credit-reservation.yaml             # NEW
├── 016-billing-topup-intent.yaml           # NEW
└── 017-shedlock-table.yaml                 # NEW (ShedLock standard DDL)

apps/web/i18n/messages/{vi,en}.json         # ADD: error.billing.insufficient + error.billing.ledger.invalidState + error.billing.sepay.reference_invalid + error.billing.sepay.auth_invalid
apps/web/lib/api/schema.d.ts                # AUTO-regen via pnpm generate:api
apps/web/scripts/check-i18n.ts EN_SCAN_FILES # ADD any new feature/billing/ scan paths if added (none in 2B scope)
```

### Pattern 1: `core.billing` Spring Modulith Leaf Module

**What:** Declare `@ApplicationModule` on `package-info.java` matching the verbatim Phase 1.2 form.

**When to use:** Required for every new top-level domain package; enforced by `ApplicationModulesTest`.

**Example (mirrors `core.gmail`):**
```java
// backend/core/src/main/java/com/zeromail/core/billing/package-info.java
@ApplicationModule(
    displayName = "Billing",
    allowedDependencies = {"tenant", "shared.persistence", "shared.lang"})
package com.zeromail.core.billing;

import org.springframework.modulith.ApplicationModule;
```

**Why these allowedDependencies and no others:**
- `tenant` — `TenantContext.currentOrThrow()` for resolving the current tenant.
- `shared.persistence` — `AbstractTenantOwnedEntity` / `AbstractAuditableEntity` base classes.
- `shared.lang` — `IdentifiedEnum` / `OrderedEnum` for the three new enums.
- **NOT** `account` / `gmail` / `onboarding` / `shared.privacy` — billing has no business reason to import any of these. (Phase 2C will declare `core.llm` with edge to `billing`.)

**Sub-package marker (intra-domain, no `@ApplicationModule`):**
```java
// backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java
/**
 * Sub-package marker. NOT an additional Modulith module — intra-domain only.
 * Holds raw-JDBC helpers (advisory lock SQL) that the rest of core.billing must NOT touch.
 * ArchUnit guards: only core.billing.persistence.lowlevel may use JdbcTemplate.
 */
package com.zeromail.core.billing.persistence.lowlevel;
```

### Pattern 2: `pg_advisory_xact_lock` per-tenant inside REQUIRES_NEW transaction

**What:** Call advisory lock inside the same TX as the SUM-balance check + INSERT, so the entire critical section is serialized for one tenant.

**When to use:** Reserve operation only. Settle/release rely on UNIQUE constraint idempotency, not serialization.

**Example:**
```java
// backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockHelper.java
@Component
class AdvisoryLockHelper {
    private final JdbcTemplate jdbcTemplate;

    AdvisoryLockHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Acquires a transaction-scoped advisory lock keyed by hashtext(tenantId).
     * Auto-released when the surrounding @Transactional commits or rolls back.
     *
     * <p><b>Collision risk:</b> hashtext returns int4 (32-bit signed); birthday-bound
     * collision starts becoming non-trivial above ~65k tenants. We accept the risk:
     * an accidental cross-tenant serialization is a correctness no-op (CredLedger ops
     * remain tenant-scoped via TenantContext + @TenantId filter), at worst a brief
     * latency hiccup. For >65k tenants, switch to two-key form
     * pg_advisory_xact_lock(NAMESPACE_KEY, hashtext(tenantId)) where NAMESPACE_KEY is
     * a billing-domain constant. [CITED: postgresql.org/docs/17/functions-admin]
     */
    void acquireTenantLock(UUID tenantId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtext(?))")) {
                statement.setString(1, tenantId.toString());
                statement.execute();
            }
            return null;
        });
    }
}
```

```java
// backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
@Service
class CreditLedgerService implements CreditLedger {
    private final CreditLedgerEntryRepository entryRepository;
    private final CreditReservationRepository reservationRepository;
    private final AdvisoryLockHelper advisoryLockHelper;

    CreditLedgerService(/* ... */) { /* assignments */ }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
        advisoryLockHelper.acquireTenantLock(tenantId);
        int available = computeAvailableCredits(tenantId);  // SUM(amount_credits) WHERE tenant_id=?
        if (available < callSite.cost()) {
            throw new InsufficientCreditsException();  // no balance number in payload (privacy)
        }
        UUID reservationId = UUID.randomUUID();
        CreditReservationEntity reservation = new CreditReservationEntity(
                reservationId, tenantId, callSite.cost(), callSite, CreditReservationStatus.PENDING);
        reservationRepository.save(reservation);
        CreditLedgerEntryEntity entry = CreditLedgerEntryEntity.reserve(
                UUID.randomUUID(), tenantId, callSite.cost(), reservationId);
        entryRepository.save(entry);
        return new ReservationId(reservationId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void settle(ReservationId reservationId) {
        CreditReservationEntity reservation = reservationRepository.findByIdInTenant(reservationId.value())
                .orElseThrow(() -> new IllegalLedgerStateException("Reservation not found"));
        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            throw new IllegalLedgerStateException("Cannot settle a released reservation");
        }
        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            return;  // idempotent no-op
        }
        reservation.markSettled();
        reservationRepository.save(reservation);
        CreditLedgerEntryEntity settleEntry = CreditLedgerEntryEntity.settle(
                UUID.randomUUID(), reservation.getTenantId(), reservation.getId());
        try {
            entryRepository.save(settleEntry);
        } catch (DataIntegrityViolationException duplicate) {
            // UNIQUE(ref_type, ref_id, kind) caught — second SETTLE is no-op
        }
    }
    // release() symmetric
}
```

### Pattern 3: Constant-time API-key verification filter (replaces HMAC verifier)

**What:** `@Order(1) SecurityFilterChain` with custom `OncePerRequestFilter` mirrors `PubSubOidcAuthFilter` shape.

**When to use:** SePay webhook endpoint only.

**Example:**
```java
// backend/api/src/main/java/com/zeromail/api/security/SepayApiKeyFilter.java
public class SepayApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SepayApiKeyFilter.class);
    private static final String AUTH_PREFIX = "Apikey ";

    private final byte[] expectedKeyBytes;

    public SepayApiKeyFilter(String expectedApiKey) {
        // Cache as bytes once so MessageDigest.isEqual gets pre-encoded inputs.
        this.expectedKeyBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/billing/sepay/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws IOException, ServletException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(AUTH_PREFIX)) {
            log.warn("event=sepay_webhook_auth_missing");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        byte[] providedKeyBytes = authorizationHeader.substring(AUTH_PREFIX.length())
                .getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKeyBytes, providedKeyBytes)) {
            log.warn("event=sepay_webhook_auth_invalid");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

```java
// backend/api/src/main/java/com/zeromail/api/security/BillingWebhookSecurityConfig.java
@Configuration
public class BillingWebhookSecurityConfig {

    @Bean
    SepayApiKeyFilter sepayApiKeyFilter(BillingProperties billingProperties) {
        return new SepayApiKeyFilter(billingProperties.sepay().webhookApiKey());
    }

    @Bean
    FilterRegistrationBean<SepayApiKeyFilter> sepayApiKeyFilterRegistration(SepayApiKeyFilter filter) {
        FilterRegistrationBean<SepayApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);  // mirror PubSubSecurityConfig idiom
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain sepayWebhookFilterChain(HttpSecurity http, SepayApiKeyFilter filter) {
        return http.securityMatcher("/api/billing/sepay/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

### Pattern 4: ShedLock-protected `@Scheduled` watchdog

**What:** Distributed lock prevents two worker pods from double-releasing reservations.

**When to use:** Both `CreditReserveWatchdog` and `BillingIntentExpirySweeper`.

**Example:**
```java
// backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class ShedLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()  // UTC from DB clock — no client-clock drift
                        .build());
    }
}
```

```java
// backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java
@Component
class CreditReserveWatchdog {

    private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdog.class);
    private static final int BATCH_LIMIT = 100;
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final CreditReservationRepository reservationRepository;
    private final CreditLedger creditLedger;

    CreditReserveWatchdog(/* ... */) { /* assignments */ }

    @Scheduled(fixedRate = 60_000L)
    @SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
    void tick() {
        List<UUID> staleIds = reservationRepository.findStalePendingIds(
                Instant.now().minus(STALE_THRESHOLD), BATCH_LIMIT);
        for (UUID reservationId : staleIds) {
            CreditReservationEntity reservation = reservationRepository.findById(reservationId).orElseThrow();
            ScopedValue.where(TenantContext.TENANT, reservation.getTenantId().toString())
                    .run(() -> {
                        try {
                            creditLedger.release(new ReservationId(reservationId));
                            long ageSeconds = Duration.between(reservation.getCreatedAt(), Instant.now()).toSeconds();
                            log.info("event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}",
                                    reservation.getTenantId(), reservationId, ageSeconds);
                        } catch (IllegalLedgerStateException alreadyFinalized) {
                            // Race: another tick or 2C settle landed between SELECT and release. Safe to ignore.
                        }
                    });
        }
    }
}
```

```sql
-- 015-credit-reservation.yaml repository projection
SELECT id FROM credit_reservation
 WHERE status = 'PENDING' AND created_at < ?
 ORDER BY created_at
 LIMIT ?
 FOR UPDATE SKIP LOCKED  -- belt-and-suspenders alongside ShedLock; covers same-pod intra-job concurrency too
```

### Pattern 5: SePay webhook payload record + handler

**What:** Records mirroring SePay's JSON contract; ConfigurationProperties for the API key + VND-per-credit; service handles idempotency at journal layer.

```java
// backend/api/src/main/java/com/zeromail/api/dto/billing/SepayWebhookPayload.java
public record SepayWebhookPayload(
        long id,                       // SePay transaction ID — used as ref_id for replay protection
        String gateway,                // bank brand name
        String transactionDate,        // string per SePay docs (timezone-bearing)
        String accountNumber,
        String code,                   // "Mã thanh toán" — may be null; not used in our flow
        String content,                // transfer description (often contains the intent code)
        String transferType,           // "in" or "out" — we accept "in" only
        long transferAmount,           // VND amount transferred
        long accumulated,              // bank account balance (ignored)
        String subAccount,             // virtual account (may be null)
        String referenceCode,          // SMS reference — may match our intent code
        String description             // full SMS — fallback parser source
) {}
```

```java
// backend/core/src/main/java/com/zeromail/core/billing/service/BillingProperties.java
@ConfigurationProperties(prefix = "zero-mail.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull SepayProperties sepay,
        @Min(1) @DefaultValue("1000") long vndPerCredit,
        @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
        @Valid @DefaultValue Duration intentExpiry
) {
    public record SepayProperties(@NotBlank String webhookApiKey) {}
}
```

```yaml
# backend/api/src/main/resources/application.yml (delta)
zero-mail:
  billing:
    sepay:
      webhook-api-key: ${SEPAY_WEBHOOK_API_KEY:?SEPAY_WEBHOOK_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
    vnd-per-credit: 1000
    max-pending-intents-per-tenant: 5
    intent-expiry: PT24H

# backend/worker/src/main/resources/application.yml (delta — same env var for parity)
zero-mail:
  billing:
    sepay:
      webhook-api-key: ${SEPAY_WEBHOOK_API_KEY:?SEPAY_WEBHOOK_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
```

### Pattern 6: Hand-rolled Crockford base32 8-char generator

**What:** Generate 8-char codes from a `SecureRandom` over the alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (32 chars; excludes `I`, `L`, `O`, `U`).

**Example:**
```java
// backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java
@Component
class TopupCodeGenerator {
    // Crockford base32 alphabet: digits + capitals minus I, L, O, U.
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    String generateUniqueCode(Predicate<String> isAvailable, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = newCode();
            if (isAvailable.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique top-up code after " + maxAttempts + " attempts");
    }

    private String newCode() {
        char[] characters = new char[CODE_LENGTH];
        for (int index = 0; index < CODE_LENGTH; index++) {
            characters[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(characters);
    }
}
```

### Anti-Patterns to Avoid

- **Don't put `pg_advisory_xact_lock` in `core.billing.service` directly.** ArchUnit ban on raw `JdbcTemplate` outside `core.billing.persistence.lowlevel` is a Phase 1.2 invariant. Wrap it in `AdvisoryLockHelper`.
- **Don't return numeric balance in `InsufficientCreditsException`.** Privacy invariant — the FE infers from the 402 status + i18n key only.
- **Don't compute HMAC over the SePay payload** — SePay does not sign requests. (This is the largest hypothetical anti-pattern from the SPEC.)
- **Don't use `ScheduledExecutorService` with hand-rolled `synchronized`** for the watchdog — fails under N>1 worker pods.
- **Don't store the raw SePay payload in a DB column.** The intent + ledger entry capture all the financially-relevant fields; the rest is logged-out.
- **Don't make `CreditLedger.balance` `@Transactional(REQUIRES_NEW)`.** Read-only; default propagation + Hibernate's `@TenantId` filter is sufficient.
- **Don't put `qrPayload` in the response if you don't have one.** Keep nullable. Phase 5 adds the QR generation; 2B exposes the API only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Distributed scheduler lock | Custom `synchronized` + Postgres lock table | **ShedLock 7.7.0** | Decade-mature; handles lock extension, lock-by, locked-until, multi-pod safely. Hand-rolling means re-implementing 600+ LOC. |
| Constant-time byte compare | `Arrays.equals` | `MessageDigest.isEqual` | `Arrays.equals` short-circuits on first inequality — timing-attack vulnerable. `MessageDigest.isEqual` is documented constant-time since Java 6u17. [CITED: codahale.com/a-lesson-in-timing-attacks] |
| Postgres advisory lock SQL | App-layer mutex / Redis lock | `pg_advisory_xact_lock` | Auto-released on commit/rollback; cluster-wide; no separate Redis dep. [CITED: postgresql.org/docs/17/explicit-locking, functions-admin] |
| HMAC algorithms | `MessageDigest.update(secret); update(body)` | `Mac.getInstance("HmacSHA256")` | (Moot for SePay — they don't use HMAC. But documenting for general guidance: `MessageDigest` is not HMAC; the secret-prefix anti-pattern is forgeable via length-extension on raw SHA-256.) |
| Timing-safe string compare | `String.equals` | `MessageDigest.isEqual` (after `.getBytes(UTF_8)`) | Same reason as `Arrays.equals` — short-circuits. |
| Batch claim under contention | App-layer "load all then filter" | `FOR UPDATE SKIP LOCKED` | Postgres-native; already proven in `PubSubDeliveryRepository.claimPendingBatch`. |
| JPA optimistic lock | Manual `version` column + `WHERE version=?` | `@Version` annotation | Hibernate-native; throws `OptimisticLockingFailureException` already mapped to 409 in `GlobalExceptionHandler`. |
| Validation on intent amount | Service-layer `if (amount<=0)` | `@Min(1) long amountVnd` on the request record | Existing `MethodArgumentNotValidException` handler already turns it into 400 + i18n field error. |

**Key insight:** Every "Don't Hand-Roll" item except Crockford base32 has a battle-tested artifact already either in the project or one Maven coordinate away. Crockford base32 is the only thing worth writing inline (single alphabet, 30 LOC, no dep weight).

---

## Common Pitfalls

### Pitfall 1: SePay `referenceCode` vs `content` field — which carries the intent code?

**What goes wrong:** SePay docs document both `referenceCode` ("Mã tham chiếu của tin nhắn sms") and `content` (transfer description) as candidates. In practice, banks render the user-typed memo into the `content` field, not `referenceCode` (which is bank-internal SMS metadata). If we extract from `referenceCode` only, real top-ups appear as `unknown_code`.

**Why it happens:** Naming ambiguity in SePay's docs; the field that "contains the user's input" is `content`, not `referenceCode`.

**How to avoid:**
1. Search for an 8-character Crockford-alphabet substring in BOTH `content` and `referenceCode` (with `content` checked first because that's where banks put it).
2. Uppercase-normalize before comparing.
3. Treat any case where neither field contains a known code as `event=sepay_webhook_unknown_code` + 200 OK.

**Warning signs:** SePay sandbox round-trip shows ledger TOPUPs at 0; logs show repeated `unknown_code` events.

**[ASSUMED]** This dual-field check is inferred from SePay docs + Vietnamese-bank SMS conventions; the planner SHOULD verify with the SePay sandbox or by reading `https://github.com/sepayvn/laravel-sepay` to confirm which field the official package keys off.

### Pitfall 2: Advisory lock visible only with `WHERE locktype='advisory'`

**What goes wrong:** Operator runs `SELECT * FROM pg_locks` to debug a stuck reserve, sees nothing, concludes "no lock contention" — when in fact the advisory lock is right there but filtered out by default views.

**Why it happens:** `pg_locks` returns ALL lock types. Most monitoring queries filter to `relation` locks. Advisory locks live in the `advisory` namespace.

**How to avoid:** Document the runbook query: `SELECT * FROM pg_locks WHERE locktype='advisory'`. Add a Micrometer counter `zero_mail.billing.reserve.advisory_lock_acquired_total` so prod monitoring catches it without DB introspection.

**Warning signs:** Reserve latency spikes during contention; `pg_locks` "looks empty" to the on-call.

### Pitfall 3: `hashtext` collision among adjacent UUIDs at scale

**What goes wrong:** `hashtext` returns int4 (32-bit). At ~65k tenants the birthday-bound collision probability becomes non-trivial. Two tenants colliding briefly serialize each other's reserves — correctness no-op (both flows are tenant-isolated by `@TenantId`), but a perceptible latency hit at peak.

**Why it happens:** 32-bit hash space < tenant count for a successful product.

**How to avoid:** Document the pivot path: switch to two-key advisory lock `pg_advisory_xact_lock(BILLING_NAMESPACE, hashtext(tenantId))` where `BILLING_NAMESPACE` is a domain-fixed int constant. Cuts collisions by 2^32 again. v1 stays single-key (we are far from 65k tenants).

**Warning signs:** Reserve p99 latency rising despite low traffic; advisory-lock-wait time visible in `pg_stat_activity`.

### Pitfall 4: `:?` fail-fast crashing every `@SpringBootTest`

**What goes wrong:** `${SEPAY_WEBHOOK_API_KEY:?...}` syntax fails property resolution at boot if the env var is missing. Test profile doesn't set it → every `@SpringBootTest` blue-screens before any test runs.

**Why it happens:** Spring property placeholder `:?` is fail-fast by design. Test profiles need an explicit injection point.

**How to avoid:** Add to `ApiPostgresTestBase` and worker `PostgresContainerTest`:
```java
r.add("zero-mail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
```
This is the same pattern Phase 1.5 used for `REFRESH_TOKEN_KEY_BASE64`. [VERIFIED: `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java` line 48; `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java` line 46]

**Warning signs:** Every `@SpringBootTest` in the module fails with `IllegalArgumentException: Could not resolve placeholder 'SEPAY_WEBHOOK_API_KEY'`.

### Pitfall 5: `springdoc-openapi-gradle-plugin` boot crashes when 2B adds new env vars

**What goes wrong:** The OpenAPI emit task in `backend/api/build.gradle.kts` (Phase 1.2.1 D-Plan 04) starts a real Spring Boot context with dummy env vars. If 2B adds a new `:?` fail-fast var, the emit task crashes — frontend codegen breaks.

**Why it happens:** OpenAPI emit needs the full app context to introspect controllers.

**How to avoid:** Add the new dummy values to the `customBootRun.args` list in `backend/api/build.gradle.kts`:
```kotlin
"--zero-mail.billing.sepay.webhook-api-key=openapi-emit"
```
[VERIFIED: existing pattern at `backend/api/build.gradle.kts` lines 51-60]

**Warning signs:** `pnpm generate:api` fails with "Application context failed to load"; Gradle `forkedSpringBootRun` exits non-zero during the openapi emit task.

### Pitfall 6: Settling a release reservation in a 2C exception path

**What goes wrong:** Phase 2C catches the chat exception, calls `release(rid)` — but a virtual-thread interrupt or an outer transaction rollback already triggered the watchdog, which already released the same rid. Second `release` fires `IllegalLedgerStateException`.

**Why it happens:** D-D2/D3 say `release` after `release` is idempotent (no-op), but `release` after `settle` is forbidden. The watchdog can race with 2C in the milliseconds between reserve and settle/release.

**How to avoid:** Watchdog already checks `status='PENDING'` → if 2C settled in the gap, watchdog finds nothing. If watchdog released first, 2C's `release` is no-op (D-D3). The only forbidden race is `settle-after-release` and `release-after-settle`, which the SPEC requires throw — by design. **Document for 2C:** wrap `release(rid)` in a try/catch for `IllegalLedgerStateException` ONLY if 2C wants to swallow that race; default behavior should be log + propagate (it indicates a real bug).

**Warning signs:** Phase 2C tests intermittently fail with `IllegalLedgerStateException`.

### Pitfall 7: ApiError `params` accidentally leaks balance number

**What goes wrong:** `InsufficientCreditsException.message = "Need 2 credits, have 1"` → operator-helpful but leaks the exact balance to the API consumer, violating SPEC.

**Why it happens:** Java `RuntimeException(String message)` constructor habits.

**How to avoid:** `InsufficientCreditsException()` with NO args; the exception class carries no balance; `GlobalExceptionHandler` writes `params: {}`. If operator wants the balance, they read the journal directly.

**Warning signs:** Integration test asserts response body has no `availableCredits` key — fails if leak slipped in.

### Pitfall 8: Liquibase changeset numbering collision with Phase 2C

**What goes wrong:** 2C SPEC reserves `014-tenant-byok-credentials.yaml`. 2B claims `014-016`. If 2C's plan-phase merges before 2B's, the schema fails to apply on a clean DB.

**Why it happens:** Two parallel sub-phases competing for the same monotonic numbering.

**How to avoid:** Document in 2B closing plan that the new floor is `017` (or `018` if ShedLock takes a slot). Phase 2C plan-phase MUST renumber on merge.

**Warning signs:** Liquibase fails on `db.changelog-master.yaml` apply with "duplicate changeset id".

### Pitfall 9: `@DynamicPropertySource` does not override `:?` placeholders during `forkedSpringBootRun`

**What goes wrong:** The `springdoc-openapi-gradle-plugin` forks a JVM that does NOT use `@DynamicPropertySource` (only the test framework does). Adding test fixture properties to `ApiPostgresTestBase` does NOT help the OpenAPI emit task.

**Why it happens:** Two different boot paths.

**How to avoid:** Always update BOTH:
1. `ApiPostgresTestBase.props()` — for `@SpringBootTest`.
2. `backend/api/build.gradle.kts` `customBootRun.args` — for OpenAPI emit.

**Warning signs:** Tests pass; `./gradlew :backend:api:openApi` fails on emit.

---

## Code Examples

### Top-up intent creation (controller + service)

```java
// backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final CreditLedger creditLedger;
    private final BillingTopupService billingTopupService;

    public BillingController(CreditLedger creditLedger, BillingTopupService billingTopupService) {
        this.creditLedger = creditLedger;
        this.billingTopupService = billingTopupService;
    }

    @GetMapping("/balance")
    public BillingBalanceResponse balance() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CreditBalance balance = creditLedger.balance(tenantId);
        return new BillingBalanceResponse(balance.availableCredits(), balance.heldCredits(), "credits");
    }

    @PostMapping("/topup/intent")
    public TopupIntentResponse createIntent(@Valid @RequestBody TopupIntentRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        BillingTopupIntentEntity intent = billingTopupService.createIntent(tenantId, request.amountVnd());
        return new TopupIntentResponse(intent.getCode(), intent.getAmountVnd(), intent.getExpiresAt(), null);
    }
}
```

### SePay webhook controller (delegates to service; success contract `{"success":true}`)

```java
// backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java
@RestController
@RequestMapping("/api/billing/sepay")
public class SepayWebhookController {

    private final BillingTopupService billingTopupService;

    public SepayWebhookController(BillingTopupService billingTopupService) {
        this.billingTopupService = billingTopupService;
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> receive(@RequestBody SepayWebhookPayload payload) {
        billingTopupService.applyWebhook(payload);
        return Map.of("success", true);  // SePay contract per docs.sepay.vn
    }
}
```

### `GlobalExceptionHandler` additions for billing exceptions

```java
// backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java (delta)
@ExceptionHandler(InsufficientCreditsException.class)
public ResponseEntity<ProblemDetail> onInsufficientCredits(InsufficientCreditsException exception) {
    log.warn("Insufficient credits: {}", exception.getClass().getSimpleName());
    return problem(
            HttpStatus.PAYMENT_REQUIRED,
            "Insufficient credits",
            "The tenant does not have enough credits to perform the requested action.",
            ErrorCodes.BILLING_INSUFFICIENT_CREDITS);
}

@ExceptionHandler(IllegalLedgerStateException.class)
public ResponseEntity<ProblemDetail> onLedgerInvalidState(IllegalLedgerStateException exception) {
    log.warn("Ledger invalid state: {}", exception.getClass().getSimpleName());
    return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ledger invalid state",
            "An invalid state transition was attempted on a credit reservation.",
            ErrorCodes.BILLING_LEDGER_INVALID_STATE);
}
```

```java
// backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java (delta)
public static final String BILLING_INSUFFICIENT_CREDITS    = "error.billing.insufficient";
public static final String BILLING_LEDGER_INVALID_STATE     = "error.billing.ledger.invalidState";
public static final String BILLING_SEPAY_REFERENCE_INVALID  = "error.billing.sepay.reference_invalid";
public static final String BILLING_SEPAY_AUTH_INVALID       = "error.billing.sepay.auth_invalid";
```

### Concurrent reserve test (mirrors Phase 1 `MultiTenantLeakIntegrationTest`)

```java
// backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java
class CreditLedgerConcurrentReserveTest extends PostgresContainerTest {

    @Autowired CreditLedger creditLedger;
    @Autowired CreditLedgerEntryRepository entryRepository;
    @Autowired TenantRepository tenantRepository;

    @Test
    void ten_concurrent_reserves_against_five_credits_yields_five_ok_five_insufficient() throws Exception {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, "concurrent-test"));
        seedTopup(tenantId, 5);

        List<Boolean> outcomes;
        try (var scope = StructuredTaskScope.<Boolean>open()) {
            var subtasks = IntStream.range(0, 10)
                    .mapToObj(i -> scope.fork(() ->
                            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                    .call(() -> {
                                        try {
                                            creditLedger.reserve(tenantId, CallSite.TRIAGE);
                                            return true;
                                        } catch (InsufficientCreditsException ignored) {
                                            return false;
                                        }
                                    })))
                    .toList();
            scope.join();
            outcomes = subtasks.stream().map(s -> s.get()).toList();
        }

        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
        assertThat(outcomes.stream().filter(b -> !b).count()).isEqualTo(5);
        assertThat(creditLedger.balance(tenantId).availableCredits()).isZero();
    }

    private void seedTopup(UUID tenantId, int credits) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> entryRepository.save(CreditLedgerEntryEntity.topup(
                        UUID.randomUUID(), tenantId, credits, "test-sepay-tx-" + tenantId)));
    }
}
```

### Liquibase 014 — `credit_ledger_entry`

```yaml
databaseChangeLog:
  - changeSet:
      id: 014-credit-ledger-entry
      author: zeromail
      changes:
        - createTable:
            tableName: credit_ledger_entry
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_credit_ledger_entry_tenant, referencedTableName: tenants, referencedColumnNames: id, deleteCascade: true } }
              - column: { name: kind, type: varchar(16), constraints: { nullable: false } }
              - column: { name: amount_credits, type: integer, constraints: { nullable: false } }
              - column: { name: ref_type, type: varchar(32), constraints: { nullable: false } }
              - column: { name: ref_id, type: varchar(128), constraints: { nullable: false } }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: version, type: bigint, defaultValueNumeric: 0, constraints: { nullable: false } }
        - addUniqueConstraint:
            constraintName: uk_credit_ledger_entry_ref
            tableName: credit_ledger_entry
            columnNames: ref_type, ref_id, kind
        - createIndex:
            indexName: idx_credit_ledger_entry_tenant_created
            tableName: credit_ledger_entry
            columns:
              - column: { name: tenant_id }
              - column: { name: created_at }
        - sql:
            sql: CREATE INDEX idx_credit_ledger_entry_brin_created ON credit_ledger_entry USING BRIN (created_at)
            dbms: postgresql
        - createIndex:
            indexName: idx_credit_ledger_entry_tenant_ref
            tableName: credit_ledger_entry
            columns:
              - column: { name: tenant_id }
              - column: { name: ref_type }
              - column: { name: ref_id }
      rollback:
        - dropTable: { tableName: credit_ledger_entry }
```

### Liquibase 017 — ShedLock table

```yaml
databaseChangeLog:
  - changeSet:
      id: 017-shedlock-table
      author: zeromail
      comment: ShedLock 7.x distributed-lock table for backend/worker @SchedulerLock
      changes:
        - createTable:
            tableName: shedlock
            columns:
              - column: { name: name, type: varchar(64), constraints: { primaryKey: true, nullable: false } }
              - column: { name: lock_until, type: timestamp, constraints: { nullable: false } }
              - column: { name: locked_at, type: timestamp, constraints: { nullable: false } }
              - column: { name: locked_by, type: varchar(255), constraints: { nullable: false } }
      rollback:
        - dropTable: { tableName: shedlock }
```

---

## Runtime State Inventory

This is a greenfield phase (no rename / refactor / migration), so most categories are empty. One non-trivial item: the `SEPAY_WEBHOOK_API_KEY` env var.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no existing billing tables. New tables `credit_ledger_entry`, `credit_reservation`, `billing_topup_intent`, `shedlock` are all NEW. | None — Liquibase changesets create from empty. |
| Live service config | **SePay merchant dashboard** must be configured to: (1) point its webhook URL at `https://${VPS_DOMAIN}/api/billing/sepay/webhook`; (2) select API Key auth; (3) be given the same value as `SEPAY_WEBHOOK_API_KEY`. NOT in git. | Manual one-time setup; document in deployment runbook. |
| OS-registered state | **Windows Task Scheduler / systemd / pm2** — the worker module already runs `GmailWatchScheduler` + `GmailHistoryProcessor`. Adding `CreditReserveWatchdog` + `BillingIntentExpirySweeper` does NOT require new OS-level registrations; they are `@Scheduled` beans inside the existing worker JVM. | None. |
| Secrets/env vars | **NEW: `SEPAY_WEBHOOK_API_KEY`** must be added to the deployment secret source (Docker secret / systemd credential / locked-down env file) on the VPS. Failing to do so makes both `backend/api` and `backend/worker` fail-fast at boot. | Document in deployment runbook; add to `.env.example`. |
| Build artifacts / installed packages | None — adding ShedLock 7.7.0 is a Maven coord update, no installed binary. `apps/web/lib/api/schema.d.ts` is regenerated automatically by `pnpm generate:api`. | None beyond the regen step. |

**The canonical question:** *After every file in the repo is updated, what runtime systems still have the old state?*
- The SePay merchant dashboard is the one external system that must be touched outside the repo. Document it in the closing plan's deployment notes.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | All ledger ops + ShedLock + intent table | ✓ (via Testcontainers in dev/test, self-hosted on VPS in prod) | 17.6 | — |
| ShedLock 7.7.0 | Watchdog + sweeper distributed lock | NEW dep — to add | 7.7.0 | `pg_advisory_xact_lock`-on-job-name (more code, same outcome) |
| Java 25 + virtual threads | Concurrent-reserve test + service code | ✓ | 25 LTS | — |
| `springdoc-openapi-gradle-plugin` | Frontend codegen | ✓ | 1.9.0 | — |
| SePay merchant account + webhook URL | Live top-up testing | External — outside this phase's setup scope | — | Synthetic JSON fixture for tests; SePay sandbox for Phase 6 launch hardening |
| Docker / docker-compose for local | Local Postgres | ✓ | — | — |

**Missing dependencies with no fallback:** None for code-completeness. SePay merchant dashboard configuration is needed for live end-to-end validation only — Phase 2B itself can ship and test against synthetic fixtures.

**Missing dependencies with fallback:** ShedLock can fall back to advisory-lock-by-name; not recommended.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Boot-managed) + Spring Boot Test 4.0.6 + Testcontainers 1.21.3 + AssertJ |
| Config file | None — auto-discovered. Per-module test base in `backend/{core,api,worker}/src/test/java/.../support/` |
| Quick run command | `./gradlew :backend:core:test --tests "com.zeromail.core.billing.*"` |
| Full suite command | `./gradlew clean check` |
| Frontend i18n parity | `pnpm i18n:check` (STRICT) |
| OpenAPI codegen | `pnpm generate:api` (after Gradle openApi task) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BILL-01 | SePay webhook accepted, payload parsed, TOPUP entry created | integration | `./gradlew :backend:api:test --tests "*SepayWebhookIntegrationTest*"` | ❌ Wave 0 |
| BILL-01 | Replay (same `id`) returns 200 + no duplicate ledger entry | integration | `./gradlew :backend:api:test --tests "*SepayReplayTest*"` | ❌ Wave 0 |
| BILL-01 | Bad API key returns 401, no ledger touch | integration | `./gradlew :backend:api:test --tests "*SepayBadAuthTest*"` | ❌ Wave 0 |
| BILL-01 | API-key constant-time compare unit test | unit | `./gradlew :backend:core:test --tests "*SepayApiKeyVerifierTest*"` | ❌ Wave 0 |
| BILL-02 | Liquibase 014/015/016 apply cleanly on Testcontainers | integration | existing `PostgresContainerTest` boots | ✓ (boot-level via existing infra) |
| BILL-02 | UNIQUE `(ref_type, ref_id, kind)` blocks duplicate | integration | `./gradlew :backend:core:test --tests "*CreditLedgerEntryUniqueTest*"` | ❌ Wave 0 |
| BILL-03 | 10 virtual threads × reserve(5 credits) → exactly 5 OK + 5 InsufficientCreditsException | integration | `./gradlew :backend:core:test --tests "*CreditLedgerConcurrentReserveTest*"` | ❌ Wave 0 |
| BILL-03 | settle twice → no-op | integration | `./gradlew :backend:core:test --tests "*CreditLedgerSettleIdempotentTest*"` | ❌ Wave 0 |
| BILL-03 | release after settle → IllegalLedgerStateException | integration | (same class as above) | ❌ Wave 0 |
| BILL-04 | Watchdog releases reservation older than 5 min | integration | `./gradlew :backend:worker:test --tests "*CreditReserveWatchdogTest*"` | ❌ Wave 0 |
| BILL-04 | Watchdog tick on already-released reservation = no-op | integration | (same class) | ❌ Wave 0 |
| BILL-05 | `GET /api/billing/balance` returns shape `{availableCredits, heldCredits, currency}` | integration | `./gradlew :backend:api:test --tests "*BillingBalanceControllerTest*"` | ❌ Wave 0 |
| BILL-05 | Tenant A cannot see tenant B's balance | integration | `./gradlew :backend:api:test --tests "*BillingBalanceMultiTenantLeakTest*"` | ❌ Wave 0 |
| BILL-06 | reserve insufficient → 402 + `code=BILLING_INSUFFICIENT_CREDITS` + no balance leak | integration | (test-only `@RestController` wraps `reserve` for HTTP-layer verification) | ❌ Wave 0 |
| BILL-06 | i18n keys `error.billing.insufficient` present in vi.json + en.json | unit/lint | `pnpm i18n:check` | ✓ (pipeline exists; keys MUST be added) |
| BILL-07 | `CreditLedger` interface Javadoc contains BYOK exemption sentence | unit/static | `./gradlew :backend:core:test --tests "*CallSiteEnumMembershipArchTest*"` (ArchUnit reads source) | ❌ Wave 0 |
| BILL-07 | `CallSite` enum has exactly `{TRIAGE, DRAFT, PREVIEW}` | ArchUnit | (same class) | ❌ Wave 0 |
| Modulith | `core.billing` package-info has correct `allowedDependencies` | ArchUnit | `./gradlew :backend:core:test --tests "*ApplicationModulesTest*" *DomainBoundaryArchTests*` | ✓ (existing tests; new rule needed) |
| Privacy | No log line contains raw payload bytes / `Authorization` header / transactionId | grep-based | `./gradlew :backend:api:test --tests "*BillingPrivacyLogScrubTest*"` (synthetic-traffic + log-output assertion) | ❌ Wave 0 |
| Cross-domain | `core.billing.service.CreditLedgerService` cannot be instantiated outside `core.billing.service` | ArchUnit | `./gradlew :backend:core:test --tests "*BillingDomainBoundaryArchTest*"` | ❌ Wave 0 |

### Sampling Rate (Nyquist)

- **Per task commit:** `./gradlew :backend:core:check :backend:api:check :backend:worker:check` (skips integration if no DB; runs unit + ArchUnit + static)
- **Per wave merge:** full module check including Testcontainers (`./gradlew clean check`)
- **Phase gate:** full suite + `pnpm i18n:check` STRICT + `pnpm generate:api` round-trip + `./gradlew :backend:api:openApi` hermetic emit + ArchUnit zero violations.

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java` — REQ BILL-03
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java` — REQ BILL-03
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java` — REQ BILL-01
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java` — Crockford alphabet correctness + collision retry
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java` — REQ BILL-02
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` — REQ BILL-07
- [ ] `backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java` — Modulith + ArchUnit ban on raw JdbcTemplate outside lowlevel
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java` — REQ BILL-01
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java` — REQ BILL-01
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java` — REQ BILL-01
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java` — REQ BILL-05
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java` — REQ BILL-05
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java` — Privacy invariant
- [ ] `backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java` — REQ BILL-04
- [ ] `backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java` — D-C4
- [ ] Update `apps/web/i18n/messages/{vi,en}.json` with `error.billing.*` keys
- [ ] Update `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` only if new feature/billing/ frontend files are added (not in 2B scope; skip)
- [ ] Update `ApiPostgresTestBase.props()` + worker `PostgresContainerTest.props()` with `zero-mail.billing.sepay.webhook-api-key=test-sepay-key-fixture`
- [ ] Update `backend/api/build.gradle.kts` `customBootRun.args` with dummy `--zero-mail.billing.sepay.webhook-api-key=openapi-emit`
- [ ] Update `libs.versions.toml` with `shedlock = "7.7.0"` + library entries

**No new framework install needed** — JUnit 5, Testcontainers, AssertJ, ArchUnit are already in place.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Spring Security session cookie (existing) for `/api/billing/balance` + `/api/billing/topup/intent`. SePay webhook uses static API key + IP allowlist (deferred). |
| V3 Session Management | yes | Existing Spring Session Redis for user-facing endpoints. Webhook is stateless (`SessionCreationPolicy.STATELESS`). |
| V4 Access Control | yes | Tenant-scoped via `TenantContext` ScopedValue + Hibernate `@TenantId` filter on all billing entities. ArchUnit test asserts no cross-tenant leak (mirror of FND-05). |
| V5 Input Validation | yes | Jakarta Validation (`@Min(1) long amountVnd`, `@NotBlank` on SePay payload fields). `MethodArgumentNotValidException` already mapped to 400 + i18n field-error. |
| V6 Cryptography | partial | **Constant-time compare via `MessageDigest.isEqual`** for the SePay API key. No symmetric encryption needed in this phase (refresh-token AES-GCM is reused unchanged from Phase 1). |
| V7 Error Handling & Logging | yes | Privacy log format `event=opaque tenantId={}`. `ApiError` body never leaks balance, transactionId, signature header, or payload bytes. |
| V8 Data Protection | yes | No raw bank account / phone / payment payload stored beyond what's audit-needed (`sepay_transaction_id`, `amount_vnd`, `code`). |
| V9 Communication | yes | HTTPS-only via reverse proxy (existing). `secure: true` cookie override deferred to Phase 6 hardening (per STATE.md Blockers). |
| V10 Malicious Code | n/a | — |
| V11 Business Logic | yes | Idempotency at journal layer; advisory lock prevents concurrent double-reserve. |
| V12 Files & Resources | n/a | No file upload in 2B. |
| V13 API & Web Services | yes | `springdoc-openapi` contract; `application/problem+json` error shape. |
| V14 Configuration | yes | `:?` fail-fast for `SEPAY_WEBHOOK_API_KEY`; no secret in repo; deployment-source-agnostic. |

### Known Threat Patterns for {Spring Boot 4 + Postgres + SePay webhook}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via payload field | Tampering | All persistence is Spring Data JPA / parameterized JPQL or `JdbcTemplate` with `?`-binding. Never string-concat user input into SQL. ArchUnit ban on raw `JdbcTemplate` outside `core.billing.persistence.lowlevel`. |
| Webhook replay | Tampering / Repudiation | UNIQUE `(ref_type, ref_id, kind)` constraint + UNIQUE `sepay_transaction_id WHERE NOT NULL` on intent. Replay → `DataIntegrityViolationException` → caught → 200 ack. |
| Timing attack on API key | Information Disclosure | `MessageDigest.isEqual` constant-time. |
| Spoofed webhook caller | Spoofing | Static API key (the only auth SePay supports). Recommend IP allowlist as Phase 6 hardening; cite SePay's official guidance. |
| Concurrent double-reserve | Tampering | `pg_advisory_xact_lock(hashtext(tenant_id))` + `Propagation.REQUIRES_NEW`. |
| Watchdog double-release | Tampering | UNIQUE `(ref_type='RESERVATION', ref_id, kind='RELEASE')` + ShedLock + `SKIP LOCKED` belt-and-suspenders. |
| Information disclosure via balance in error response | Information Disclosure | `InsufficientCreditsException` carries no message; `ApiError.params={}`. |
| Log bleed of payment details | Information Disclosure | Logback scrub filter (Phase 1) + opaque `event=` log convention; ArchUnit FND-04 deny-list. **Verify if `signature=`, `payload=`, `Authorization=` patterns need adding to scrub filter.** |
| Boot without secret | Denial of Service | `:?` fail-fast — boots loud-and-early instead of silently succeeding without auth. |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| HMAC-SHA256 + signature header (assumed in SPEC) | API key static-secret + constant-time compare | 2026-05-05 (this research) | All test fixtures, verifier class name, env var name change. Still constant-time, still `:?` fail-fast. |
| `@Scheduled` without distributed lock (current GmailWatchScheduler) | `@Scheduled` + `@SchedulerLock` (ShedLock 7.7.0) | 2026-05-05 (this phase introduces ShedLock) | New dep + new Liquibase changeset. GmailWatchScheduler should NOT be retrofitted in this phase — leave as-is per SPEC scope. |
| `commons-codec` Base32 | Hand-rolled Crockford base32 | 2026-05-05 | ~30 LOC; no new dep. |
| Stripe / LemonSqueezy (REQUIREMENTS.md text) | SePay (CONTEXT decision) | Phase 2B SPEC | REQUIREMENTS.md `BILL-01` text is now stale — closing plan updates it. |

**Deprecated/outdated:**
- `Mac.getInstance("HmacSHA256")` for SePay-related code: DO NOT add this — there is nothing to HMAC against.
- `commons-codec` direct dep: not needed.

---

## Project Constraints (from CLAUDE.md)

Extracted from `D:\study-materials-summer-2026\EXE202\zero-mail\CLAUDE.md` — the planner MUST honor these:

1. **Java 25 LTS, Spring Boot 4.0.6** — locked.
2. **Gradle 9.x Kotlin DSL** — locked.
3. **No Lombok** — use Java records for DTOs, plain classes (with `protected` no-args constructor for Hibernate) for entities. Hand-write any builder.
4. **No `spring-cloud-gcp` baseline** — secrets resolve from Docker secrets / systemd credentials / locked-down env files, NOT GCP Secret Manager.
5. **PostgreSQL 17.6 self-hosted on VPS** — no Cloud SQL.
6. **Liquibase 5.0.2 with YAML changelogs** — locked.
7. **Spring AI 2.0.0-M5** — N/A for this phase (no LLM).
8. **No raw email body / prompt / completion in logs** — `event=opaque tenantId={}` format mandatory; same for SePay payload bytes.
9. **Backend Java code uses domain-revealing names** — never `req`, `res`, `svc`, `ctx`, `tx`, `e`. Use `request`, `response`, `creditLedgerService`, `tenantContext`, `transactionTemplate`, `insufficientCreditsException`. Established acronyms (`ID`, `DTO`, `JPA`, `URL`, `URI`, `HTTP`) and intentionally ignored lambda params (`_`) are exceptions.
10. **Thin controllers + service-owned `@Transactional`** — controllers translate HTTP shape ↔ domain shape; never inject repositories; transactions live in `@Service`.
11. **Records for DTOs, classes for entities, Lombok-free** — verbatim.
12. **Enum state machines via `OrderedEnum` / `IdentifiedEnum` + static `fromId` fail-loud** — `CallSite`, `CreditReservationStatus`, `BillingTopupIntentStatus` all implement `IdentifiedEnum`. Each gets a static `fromId(String)` throwing `NoSuchElementException`. None of these enums are ordered (no forward-only state machine), so `IdentifiedEnum` (not `OrderedEnum`) is the right interface.
13. **Privacy logging format** — `event=` opaque names, `tenantId={}` UUID only, no PII. Verified by ArchUnit FND-04 deny-list + Logback scrub filter.
14. **UI primitive selection** — N/A (backend-only phase).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Banks render the user-typed memo into the SePay `content` field, not `referenceCode` | Pitfalls §1 | Real top-ups appear as `unknown_code` in logs; integration with SePay sandbox in Phase 6 will catch this; unit tests don't. **Recommend:** plan-phase reads `https://github.com/sepayvn/laravel-sepay` source to confirm. |
| A2 | `hashtext(text) → int4` is stable enough for 32-bit collision risk to be tolerable up to ~65k tenants | Pitfalls §3 | If `hashtext` changes implementation between PG major versions (per documented warning), advisory locks could miss in flight. Risk acceptable: PG 17.6 `hashtext` semantics are stable, and a planner that pivots to two-key form before scaling solves it. |
| A3 | The CR-04 carryover (`backend/worker` `:?` fail-fast for `REFRESH_TOKEN_KEY_BASE64`) is a no-op because the worker `application.yml` already has `:?` (verified at line 29) | Folded Todos | The "fold" is reduced to: also add `SEPAY_WEBHOOK_API_KEY:?` to worker `application.yml`. The CONTEXT-claimed Phase 1.5 worker pattern was apparently already fixed. **Recommend:** plan-phase verifies the existing worker yml line once more before declaring CR-04 closed. |
| A4 | ShedLock 7.7.0 is the latest 7.x line and is Boot-4-compatible | Standard Stack | If 7.x has a bug or 8.x ships during planning, version pin needs update. Verified 2026-05-05 against the GitHub README compatibility matrix; risk LOW. |
| A5 | `springdoc-openapi-gradle-plugin` 1.9.0 emit task picks up new `BillingController` and `SepayWebhookController` without manual `@Operation` / `@Tag` annotations | Pattern 1 | If the emit task fails on missing annotations, plan-phase needs an `@Tag(name="billing")` line on the controllers (matches Phase 1.2.1 D-Plan 04 pattern). |
| A6 | The Phase 1 Logback scrub filter does NOT yet have patterns matching `signature=`, `payload=`, `Authorization=`. CONTEXT D-I3 says "verify before assuming" | Common Pitfalls | If the patterns don't exist, plan-phase adds them. Low effort to verify; either way the privacy invariant is enforced by both the scrub filter AND the opaque `event=` convention. |
| A7 | `pg_advisory_xact_lock` releases on rollback (not just commit). Verified behavior in PG 17 — auto-released "at the end of the transaction" | Pattern 2 | If wrong, an exception path leaks the advisory lock until session end. PG docs are explicit: "automatically released at the end of the transaction." |

---

## Open Questions

1. **Does SePay's `content` or `referenceCode` field carry our intent code in real bank-transfer scenarios?**
   - What we know: SePay docs document both as candidates; `referenceCode` is "Mã tham chiếu của tin nhắn sms" (SMS-internal); `content` is the user-visible transfer description.
   - What's unclear: which field the laravel-sepay package keys off; whether all Vietnamese banks parse identically.
   - Recommendation: implement dual-field search (content first, referenceCode fallback). Phase 6 launch hardening verifies against live SePay sandbox.

2. **Should `qrPayload` in `TopupIntentResponse` ever be non-null in 2B?**
   - What we know: SPEC + CONTEXT explicitly defer Phase 5 UI; D-C2 shows `qrPayload?` as optional.
   - What's unclear: whether 2B should pre-compute the SePay QR string for parity with SePay's documented QR format.
   - Recommendation: leave `qrPayload = null` in 2B; Phase 5 either renders the QR client-side or asks 2B to extend the response shape.

3. **Should the SePay webhook accept POSTs with `application/x-www-form-urlencoded` or only `application/json`?**
   - What we know: SePay docs allow choosing the content type at webhook setup; default is `application/json`.
   - What's unclear: whether the Phase 6 SePay sandbox testing might accidentally use form-encoded.
   - Recommendation: use Spring's default content negotiation (`@RequestBody SepayWebhookPayload`); it accepts JSON. Document in deployment runbook: SePay dashboard MUST be set to `application/json`.

4. **Cross-domain invocation of `CreditLedger` from Phase 2C — module name in 2C's `allowedDependencies` array?**
   - What we know: Phase 2C will declare `core.llm` with edge to `billing`.
   - What's unclear: the literal — is it `"billing"` (top-level) or some sub-path?
   - Recommendation: top-level `"billing"`. Mirror of `"gmail"` / `"account"` / `"onboarding"` literals already in use.

---

## Sources

### Primary (HIGH confidence)
- **SePay developer docs** — `https://developer.sepay.vn/en/sepay-webhooks/tich-hop-webhook` and `/lap-trinh-webhook` — webhook payload schema, `Authorization: Apikey ...` auth header, `{"success": true}` response contract. [VERIFIED via WebFetch 2026-05-05]
- **SePay Vietnamese docs** — `https://docs.sepay.vn/tich-hop-webhooks.html` — corroborates the same schema in original Vietnamese.
- **PostgreSQL 17 docs** — `https://www.postgresql.org/docs/17/explicit-locking.html` §13.3.5 — advisory lock semantics, transaction-scope auto-release, `pg_locks` visibility.
- **PostgreSQL 17 admin functions** — `https://www.postgresql.org/docs/17/functions-admin.html` — `pg_advisory_xact_lock(bigint)` and `(int, int)` signatures.
- **ShedLock README** — `https://github.com/lukas-krecan/ShedLock` — 7.x Boot-4 compatibility, JDBC PostgreSQL setup, table DDL.
- **In-repo verification:**
  - `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java` — `@Order(1) SecurityFilterChain` template.
  - `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java` — `OncePerRequestFilter` template.
  - `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java` — `SKIP LOCKED` template.
  - `backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java` — `StructuredTaskScope` virtual-thread test template.
  - `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java` line 48 — `@DynamicPropertySource` test-secret pattern.
  - `backend/worker/src/main/resources/application.yml` line 29 — confirms worker already has `:?` fail-fast for `REFRESH_TOKEN_KEY_BASE64`.
  - `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` — `ResponseEntityExceptionHandler` extension + `ProblemDetail` shape.
  - `backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java` — `IdentifiedEnum` interface + `fromId` pattern.
  - `gradle/libs.versions.toml` — current dep pins.

### Secondary (MEDIUM confidence)
- `https://codahale.com/a-lesson-in-timing-attacks/` — `MessageDigest.isEqual` is constant-time since Java 6u17.
- `https://www.pixelstech.net/article/1431658986-arrays-equals()-vs-messagedigest-isequal()` — corroborating timing-attack analysis.
- `https://www.baeldung.com/shedlock-spring` — ShedLock Spring integration tutorial.
- `https://github.com/sepayvn/laravel-sepay` — official SePay PHP package; reading its source can resolve A1.

### Tertiary (LOW confidence)
- General training-data familiarity for: Vietnamese bank transfer memo conventions, JHipster ErrorConstants pattern (already adapted in `ErrorCodes.java`), Crockford base32 alphabet definition.

---

## Metadata

**Confidence breakdown:**
- Standard stack (ShedLock, JDK builtins, in-repo libs): **HIGH** — ShedLock + JDK + existing Boot deps verified against authoritative sources and `libs.versions.toml`.
- Architecture (advisory lock pattern, Modulith package shape, controller layout): **HIGH** — every pattern has a verified in-repo precedent.
- SePay integration: **MEDIUM-HIGH** — docs + dev portal confirmed `Authorization: Apikey` is the auth scheme; `content` vs `referenceCode` field semantics is the only soft spot (Pitfall 1, Assumption A1).
- Watchdog distributed lock: **HIGH** — ShedLock 7.x Boot-4 compatibility verified.
- Privacy invariants: **HIGH** — opaque `event=` log format is project-wide; FND-04 ArchUnit + Logback scrub already enforced.
- Cross-phase coupling (changeset numbering, 2C consumer contract): **HIGH** — directly verified in 2C SPEC and current changelog floor.

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (stable stack, but verify SePay docs again at Phase 6 launch hardening — they may add HMAC support in the next year, in which case the auth filter can upgrade.)

---

## RESEARCH COMPLETE

**Phase:** 2B - billing-prepaid-credits
**Confidence:** HIGH

### Key Findings
- **SePay does NOT use HMAC-SHA256** — uses `Authorization: Apikey YOUR_API_KEY` static-secret header. SPEC's HMAC design must be replaced by `MessageDigest.isEqual` constant-time API-key compare. This is the largest planning-relevant correction.
- **ShedLock 7.7.0 is the Boot-4-compatible distributed lock library** — required for the watchdog. Not currently in the repo (no `@SchedulerLock` infra exists today). Adds `shedlock` Liquibase changeset.
- **`commons-codec` is NOT a direct dependency** — Crockford base32 must be hand-rolled (~30 LOC). `libs.versions.toml` and all three `build.gradle.kts` confirmed via Read.
- **Worker module already has `:?` fail-fast for `REFRESH_TOKEN_KEY_BASE64`** at line 29 of its `application.yml`. The CR-04 fold reduces to "also add `SEPAY_WEBHOOK_API_KEY:?` for parity."
- **All in-repo patterns verified to extend cleanly** to billing: `@Order(1) SecurityFilterChain` template (PubSubSecurityConfig), `StructuredTaskScope` virtual-thread test (MultiTenantLeakIntegrationTest), `SKIP LOCKED` repository (PubSubDeliveryRepository), `@DynamicPropertySource` secret injection (ApiPostgresTestBase line 48), `IdentifiedEnum` + `fromId` fail-loud, thin-controllers + service-owned `@Transactional`, `ProblemDetail`-based `ApiError` shape.

### File Created
`.planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard stack | HIGH | ShedLock + JDK builtins + Boot 4 BOM all verified |
| Architecture | HIGH | Every pattern has an in-repo precedent verified by Read or Grep |
| Pitfalls | MEDIUM-HIGH | SePay `content` vs `referenceCode` field semantics is the one soft spot (Assumption A1) |
| SePay protocol | MEDIUM-HIGH | Auth scheme + payload schema confirmed via two SePay sources; field-routing nuance flagged |
| Privacy invariants | HIGH | Phase 1 enforcement layer (FND-04 ArchUnit + Logback scrub) directly applicable |

### Open Questions
1. SePay payload `content` vs `referenceCode` field choice — recommend reading `github.com/sepayvn/laravel-sepay` during plan-phase.
2. Logback scrub filter coverage of `signature=`, `payload=`, `Authorization=` — verify before declaring D-I3 closed.
3. `qrPayload` shape for Phase 5 — defer; null in 2B.
4. Phase 2C's `allowedDependencies` literal for billing — recommend top-level `"billing"`.

### Ready for Planning
Research complete. Planner can now create PLAN.md files. **Critical action: Plan-phase MUST replace the SPEC.md HMAC hypothesis with the API-key auth scheme verified here.**
