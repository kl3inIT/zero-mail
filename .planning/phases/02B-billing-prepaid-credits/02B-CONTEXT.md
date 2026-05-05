# Phase 2B: Billing (Prepaid Credits) - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2B builds the backend-only credit ledger that every later billable surface will hit: a `core.billing` Spring Modulith package with a `CreditLedger` interface (consumed verbatim by Phase 2C's `LlmGateway`), a single append-only `credit_ledger_entry` journal plus a `credit_reservation` sidecar table for fast watchdog scans, an atomic `reserve` operation that holds under concurrent contention via Postgres advisory locks, a SePay HMAC-signed webhook that idempotently credits TOPUPs, a 60-second worker-side watchdog that releases reservations older than 5 minutes, and a `GET /api/billing/balance` endpoint. UI rendering, payment-provider checkout flows, refunds, and per-action cost preview pages are explicitly Phase 5+ territory.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**9 requirements are locked.** See `02B-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `02B-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- `core.billing` package with `CreditLedger` interface + `CreditLedgerService` impl
- `CallSite` enum (`TRIAGE=1`, `DRAFT=2`, `PREVIEW=1`) implementing `IdentifiedEnum`
- `ReservationId`, `CreditBalance`, `BillingBalanceResponse` records
- Liquibase changesets `014-credit-ledger-entry.yaml` (and `015-billing-sepay-payment.yaml` if needed for SePay payload audit, owned by `discuss-phase`)
- `BillingController` with `GET /api/billing/balance` and `POST /api/billing/sepay/webhook`
- HMAC-SHA256 SePay signature verification with `SEPAY_WEBHOOK_SECRET` `:?` fail-fast env wiring
- VND → credits conversion via `@ConfigurationProperties("zero-mail.billing")` with `vnd-per-credit` rate
- `CreditReserveWatchdog` `@Scheduled` job in `backend/worker` (60s fixedRate, 5 min TTL)
- `InsufficientCreditsException` + global `ApiError` mapping to HTTP 402 + `error.billing.insufficient` i18n keys (vi/en)
- `IllegalLedgerStateException` for double-finalize
- BYOK exemption documented in `CreditLedger` Javadoc (no code in 2B; 2C enforces)
- `springdoc-openapi` contract update; `apps/web/lib/api/schema.d.ts` regenerated
- Spring Modulith `allowedDependencies` declaration for `core.billing`
- Concurrency invariant: 10-thread reserve test + watchdog idempotency test + SePay replay test
- `REQUIREMENTS.md` status flip for `BILL-01..BILL-07`

**Out of scope (from SPEC.md):**
- Refunds / chargeback automation — admin SQL or dedicated phase later
- Receipt / invoice PDF generation + email — SePay sends its own confirmation
- Credit bundles / package pricing UI — single fixed `vnd-per-credit` rate v1
- Per-action cost preview UI / multi-currency — VND-only; rendering is Phase 5
- Full billing UI page (`/settings/billing`) — Phase 5 owns UI
- LLM USD spend tracking + per-tenant daily spend cap — Phase 2C territory
- Anything related to `tenant_byok_credentials` table — Phase 2C SPEC owns it
- Refresh-token-style key rotation drill for `SEPAY_WEBHOOK_SECRET` — STATE.md Blockers
- Multi-tenant team / shared wallet — single-tenant individual prosumer model only
- Soft-warn at low balance threshold — hard reject only in v1
- Rate limiting on the SePay webhook beyond signature check — abuse handling is future concern

</spec_lock>

<decisions>
## Implementation Decisions

### A. Concurrency control for atomic reserve

- **D-A1: Postgres advisory lock per tenant via `pg_advisory_xact_lock(hashtext(tenant_id::text))`.** Acquired at the start of every `reserve` transaction; auto-released on commit. Wraps the `SELECT SUM(amount_credits)` balance check + `INSERT credit_reservation + INSERT credit_ledger_entry RESERVE` in a single critical section. Why: cheapest correct option, scoped per-tenant (no cross-tenant contention), Postgres-native (no `ShedLock`-style library needed for this lock), zero impact on unrelated queries since advisory locks live in their own namespace. Trade-off: not visible in `pg_locks` standard views — must `pg_locks WHERE locktype='advisory'` to debug. ArchUnit guards: any `JdbcTemplate.queryForObject("SELECT pg_advisory*")` lives only in `core.billing.persistence`.
- **D-A2: `Propagation.REQUIRES_NEW` on `CreditLedgerService.reserve`.** Ensures an outer transaction failure (e.g., Phase 2C gateway exception path) cannot roll back a successful reserve. `settle` and `release` run in the caller's transaction (`Propagation.REQUIRED`) so caller controls finalization atomicity. Why: matches SPEC requirement #3; avoids ghost-reserve recovery via watchdog when caller could have settled cleanly.
- **D-A3: Concurrent-reserve test pattern locked.** 10 virtual threads × `reserve(tenantId, CallSite.TRIAGE)` against `available=5` → expected exactly 5 successes + 5 `InsufficientCreditsException`. Use `Thread.startVirtualThread` (Java 25 LoomFoundation pattern from Phase 1 FND-05) with `CountDownLatch` to release all threads simultaneously. Mirror of `MultiTenantLeakIntegrationTest`.

### B. Reservation tracking schema

- **D-B1: Sidecar `credit_reservation` table beside the journal — NOT journal-only.** Schema:
  - `id UUID PRIMARY KEY`
  - `tenant_id UUID NOT NULL` (FK → `tenants(id)` ON DELETE CASCADE)
  - `amount_credits INTEGER NOT NULL` (positive; original RESERVE amount)
  - `call_site VARCHAR(16) NOT NULL` (snapshot of `CallSite.id()` at reserve time — for audit if cost map changes)
  - `status VARCHAR(16) NOT NULL` — `PENDING | SETTLED | RELEASED` (implements `IdentifiedEnum`)
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `finalized_at TIMESTAMPTZ NULL` (set on settle/release transition)
  - `version BIGINT NOT NULL DEFAULT 0` (optimistic-lock; sidecar mutates per reservation lifecycle)
  - Partial index: `CREATE INDEX ON credit_reservation (created_at) WHERE status = 'PENDING'` — covers watchdog scan in O(stale-only).
  - B-tree on `(tenant_id, status)`.
- **D-B2: Journal stays append-only; sidecar mutates per reservation.** `reserve` txn: 1 INSERT into `credit_reservation` (status=PENDING) + 1 INSERT into `credit_ledger_entry` (kind=RESERVE, ref_id=reservation.id, amount_credits=-N). `settle` txn: UPDATE `credit_reservation SET status='SETTLED', finalized_at=now()` + INSERT `credit_ledger_entry` (kind=SETTLE, amount_credits=0, ref_id=reservation.id). `release` mirror. UNIQUE constraint on `credit_ledger_entry(ref_type, ref_id, kind)` from SPEC keeps SETTLE/RELEASE idempotent at journal layer; sidecar status transition uses optimistic lock + `IllegalLedgerStateException` on double-finalize.
- **D-B3: Watchdog query**: `SELECT id FROM credit_reservation WHERE status='PENDING' AND created_at < now() - INTERVAL '5 minutes' LIMIT 100 FOR UPDATE SKIP LOCKED` — uses `SKIP LOCKED` so two worker pods can each pick disjoint stale reservations without blocking. Fits the partial index above; expected to be a near-empty scan in steady state.

### C. SePay reference-code format + intent table

- **D-C1: Top-up intent table `billing_topup_intent`.** Schema:
  - `id UUID PRIMARY KEY`
  - `tenant_id UUID NOT NULL` (FK → tenants ON DELETE CASCADE)
  - `code VARCHAR(16) NOT NULL UNIQUE` — 8-character Crockford base32 (alphabet `0-9A-Z` excluding `ILOU`); collision retry up to 3 times before throwing.
  - `amount_vnd BIGINT NOT NULL` (locked at intent creation; webhook validates exact match)
  - `status VARCHAR(16) NOT NULL` — `PENDING | PAID | EXPIRED` (implements `IdentifiedEnum`)
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours'`
  - `paid_at TIMESTAMPTZ NULL`
  - `sepay_transaction_id VARCHAR(128) NULL` (set on webhook success; UNIQUE when not null for replay protection)
  - B-tree on `(status, expires_at)` covers expiry-sweep job; UNIQUE on `code` covers webhook resolve.
- **D-C2: Intent endpoint contract.** `POST /api/billing/topup/intent` body `{ amountVnd: long }` → response `{ code, amountVnd, expiresAt, qrPayload? }`. Backend creates intent, returns code + (optional placeholder) `qrPayload`. Phase 5 UI renders QR; Phase 2B exposes the API only. Rate-limit: max 5 PENDING intents per tenant simultaneously (older oldest-PENDING expires when 6th created — keeps a fresh cap, prevents intent-table bloat).
- **D-C3: Webhook resolution**: parse SePay payload's `referenceCode`, normalize to uppercase, lookup `billing_topup_intent WHERE code = ?`. If not found → 200 OK + `event=sepay_webhook_unknown_code` (don't tell SePay it's wrong; ack to stop retries; surface in admin metrics). If amount mismatch → 200 OK + `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` (treat as suspicious; do NOT credit). If expired → 200 OK + `event=sepay_webhook_intent_expired`. If happy path: same-txn UPDATE intent → PAID + INSERT TOPUP entry; UNIQUE on `sepay_transaction_id` makes replay a no-op via `DataIntegrityViolationException` caught at repository → 200 ack.
- **D-C4: Intent expiry sweep**: `BillingIntentExpirySweeper` `@Scheduled(fixedRate = 3_600_000)` (1 hour) in `backend/worker`. Marks `PENDING` intents past `expires_at` as `EXPIRED`. Idempotent. No financial impact (no ledger entry — intent never produced credits).

### D. 2B ↔ 2C lifecycle contract: explicit settle/release

- **D-D1: Phase 2C calls `settle(rid)` on success path; `release(rid)` on exception path.** Pattern locked for Phase 2C plan-phase to consume:
  ```java
  ReservationId rid = creditLedger.reserve(tenantId, CallSite.TRIAGE);
  try {
      var response = chatClient.call(prompt);
      creditLedger.settle(rid);
      return response;
  } catch (Exception e) {
      creditLedger.release(rid);
      throw;
  }
  ```
  Why: explicit, testable, traceable; LLM I/O does NOT live inside a DB transaction so `TransactionSynchronization` afterCommit/afterRollback would couple gateway to a transactional caller boundary that doesn't exist. The watchdog is the safety net for crashes between `reserve` and `settle/release`, NOT the steady-state finalizer.
- **D-D2: `settle` returns void; idempotent on repeat call.** Second `settle(rid)` on already-SETTLED reservation: no-op (UNIQUE constraint catches the duplicate journal entry, sidecar status already SETTLED so UPDATE rowcount=0 → no-op). Second `settle(rid)` on RELEASED reservation: `IllegalLedgerStateException` (forbidden transition).
- **D-D3: `release` returns void; idempotent on repeat call.** Mirror of D-D2.
- **D-D4: `IllegalLedgerStateException` is a `RuntimeException`** under `core.billing`; `GlobalExceptionHandler` maps to HTTP 500 + `code=BILLING_LEDGER_INVALID_STATE` (operator-visible signal — should not happen in normal flow). Not a 4xx; this is a programming-error class, not a user-recoverable condition.

### E. Test strategy for SePay webhook

- **D-E1: WireMock-free webhook receive tests** — webhook is *inbound*, no outbound SePay calls in 2B scope. Tests POST signed fixture payloads directly to `/api/billing/sepay/webhook` via `@AutoConfigureMockMvc` or `RestClient + LocalServerPort` (use the latter when `TenantContext` ScopedValue must be bound — same lesson as Phase 1 OAuth tests).
- **D-E2: Two test layers**:
  1. **Pure unit:** `SePaySignatureVerifier` HMAC-SHA256 unit test (no Spring context) — covers algorithm correctness, edge cases (missing header, wrong-length sig, wrong secret).
  2. **`@SpringBootTest` integration:** full webhook flow with synthetic JSON fixture + valid signature → assert ledger entry, intent transition, balance updated. Replay test posts the same payload twice → assert exactly one TOPUP entry. Bad-signature test asserts 401 without ledger touch.
- **D-E3: Webhook fixture format**: synthetic JSON modeled after SePay's documented payload (verified by `gsd-research-phase` against `https://docs.sepay.vn/`). Plan-phase will pin the exact field names; if SePay docs are sparse, plan documents synthetic schema explicitly + flags risk for Phase 5 launch hardening to verify against live SePay.

### F. Deployment-secret hardening

- **D-F1: `SEPAY_WEBHOOK_SECRET` `:?` fail-fast in BOTH `backend/api` and `backend/worker` `application.yml`.** Same pattern as Phase 1.5 CR-04 fix for `REFRESH_TOKEN_KEY_BASE64`. Worker doesn't process the webhook (api does), but loads the same `application.yml` shape — adding the env there keeps boot semantics consistent and prevents future code-move surprises.
- **D-F2: Test profile injects `SEPAY_WEBHOOK_SECRET=test-secret-base64-equiv` via `@DynamicPropertySource`** so `:?` fail-fast doesn't crash every `@SpringBootTest`. Mirror of Phase 1 testing pattern.

### G. Spring Modulith package boundary

- **D-G1: `core.billing` package-info declares `@ApplicationModule(displayName="Billing", allowedDependencies={"tenant", "shared.persistence", "shared.lang"})`.** No edges to `account`, `gmail`, `onboarding`, `shared.privacy`. Phase 2C will declare `core.llm` with edge to `billing`. ArchUnit `DomainBoundaryArchTests` adds a per-domain rule for `core.billing` mirroring the existing pattern (gmail/account/onboarding/tenant).
- **D-G2: Sub-packages locked**: `core.billing.{model, service, persistence, persistence.lowlevel}` mirroring the Phase 1.2 per-domain shape. `CreditLedger` interface + `CallSite` enum + `ReservationId`/`CreditBalance` records → `model`. `CreditLedgerService` + `BillingTopupService` → `service`. Entities + repositories → `persistence`. Native-SQL advisory-lock helper → `persistence.lowlevel`.
- **D-G3: ArchUnit additions**: (a) `core.billing.CreditLedgerService` cannot be directly instantiated outside `core.billing.service` (callers depend on `CreditLedger` interface); (b) `CallSite` enum membership locked to `{TRIAGE, DRAFT, PREVIEW}` — prevents accidental BYOK addition by Phase 2C.

### H. Liquibase changeset ordering + cross-phase

- **D-H1: 2B claims changesets `014` and `015`** (this phase ships before 2C per user order). Allocation:
  - `014-credit-ledger-entry.yaml` — journal table + UNIQUE(ref_type, ref_id, kind) + BRIN(created_at) + B-tree(tenant_id, created_at)
  - `015-credit-reservation.yaml` — sidecar reservation table + partial index on PENDING + B-tree(tenant_id, status)
  - `016-billing-topup-intent.yaml` — top-up intent table + UNIQUE(code) + UNIQUE(sepay_transaction_id) WHERE NOT NULL
- **D-H2: Phase 2C plan-phase MUST renumber its `014-tenant-byok-credentials.yaml` to `017-tenant-byok-credentials.yaml`** (or current next-free at the time). 2B closing plan documents the new floor for 2C.

### I. Privacy-safe logging contract for billing flows

- **D-I1: SePay webhook handler logs**: `event=sepay_webhook_received` (no payload), `event=sepay_webhook_signature_invalid` (no header bytes), `event=sepay_webhook_unknown_code` (no code value — only count metric increments), `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` (numbers OK, no payload), `event=sepay_topup_credited tenantId={} credits={}` (no transactionId in log; transactionId stays in DB only).
- **D-I2: Watchdog log**: `event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}` — mirrors Phase 2A's privacy-safe pattern.
- **D-I3: Logback scrub filter extension**: if Phase 1's existing scrub patterns don't cover `signature=`, `payload=`, `referenceCode=` patterns, plan-phase adds them. Verify before assuming.

### Claude's Discretion

The researcher/planner/executor have flexibility within CLAUDE.md, SPEC.md, and the decisions above on:
- Exact SePay payload field names / signature header name (verify against `https://docs.sepay.vn/` in `gsd-research-phase` before plan-phase)
- Crockford base32 alphabet implementation (use `org.apache.commons.codec` if already on classpath, else hand-rolled — discuss-phase research will pick)
- `CreditReservationEntity` extending `AbstractAuditableEntity` vs `AbstractTenantOwnedEntity` (pick the latter — sidecar IS tenant-owned)
- Exact watchdog batch size cap (locked at 100 in D-B3 above, but can tune)
- Intent expiry sweeper batch size + interval tuning (1-hour fixedRate locked, batch size open)
- Whether `BillingController` lives in `backend/api/controllers/billing/` sub-folder (recommended for parity with `account/`, `onboarding/` DTO grouping from Phase 1.2.1)
- `vnd-per-credit` default value (recommend `1000` in `application.yml` — 1 credit = 1k VND ≈ $0.04 USD; adjust based on LLM cost model in Phase 2C)
- i18n key spelling for `error.billing.insufficient` (vi: "Số dư tín dụng không đủ — vui lòng nạp thêm để tiếp tục", en: "Insufficient credits — top up to continue") — copywriter pass acceptable

### Folded Todos

- **`2026-04-28-worker-application-yml-fail-fast-parity.md`** (CR-04 carryover from Phase 1.5 SECURITY.md): `backend/worker/src/main/resources/application.yml` line 10 still uses the old `${REFRESH_TOKEN_KEY_BASE64:${sm://...}}` pattern; api was hardened to `:?` fail-fast in Phase 1.5 Plan 08, worker was out-of-scope. Fold into 2B because this phase already touches `backend/worker/application.yml` to add `SEPAY_WEBHOOK_SECRET` — atomic edit, single plan, closes CR-04 parity gap. Plan-phase task: same `:?` fail-fast pattern + `@DynamicPropertySource` test-profile supply for both env vars.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-specific (locked)
- `.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md` — Locked requirements (9), boundaries (in/out), acceptance criteria (16). MUST read before planning.

### Project-level (in-repo, locked)
- `CLAUDE.md` §TL;DR, §Backend Code Style, §Conventions — Java 25, Spring Boot 4.0.6, PostgreSQL 17.6 self-hosted, Liquibase 5.0.2 YAML, no `spring-cloud-gcp` baseline, no Lombok, thin controllers + service-owned `@Transactional`, `IdentifiedEnum`/`OrderedEnum` + `fromId` fail-loud, privacy log format `event=opaque tenantId={}`, enterprise-readability variable naming.
- `.planning/PROJECT.md` — Privacy posture ("trust is the product"), prepaid-credits billing model, no auto-send write-action allow-list.
- `.planning/REQUIREMENTS.md` — `BILL-01..BILL-07` rows (status flip target).

### Prior-phase context (decisive for this phase)
- `.planning/phases/01-foundation-safety-infrastructure/01-CONTEXT.md` — Tenant isolation primitives (`TenantContext` ScopedValue, `@TenantId` discriminator, Hibernate filter, `MultiTenantLeakIntegrationTest` pattern); refresh-token AES-GCM envelope + `:?` fail-fast pattern (CR-04).
- `.planning/phases/01.2-domain-owned-persistence-restructuring/01.2-CONTEXT.md` — Modulith per-domain `{model, service, persistence, persistence.lowlevel}` shape; ArchUnit `DomainBoundaryArchTests` per-domain-rule pattern.
- `.planning/phases/01.2.1-shared-base-entity-and-enum-standard/01.2.1-CONTEXT.md` — `AbstractTenantOwnedEntity` + audit columns; `IdentifiedEnum` interface + `fromId` fail-loud; DTO group-by-domain reorg.
- `.planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/01.5-SECURITY.md` — CR-04 `:?` fail-fast pattern for `REFRESH_TOKEN_KEY_BASE64` (worker parity is folded into this phase).
- `.planning/phases/02A-mail-ingestion/02A-CONTEXT.md` — `OncePerRequestFilter @Order(1)` pattern for non-session endpoints (`PubSubSecurityConfig`); worker `@Scheduled` + `@SchedulerLock` pattern (`GmailWatchScheduler`); `RestClient + LocalServerPort` testing pattern when `TenantContext` ScopedValue must bind.
- `.planning/phases/02C-llm-gateway/02C-SPEC.md` — Phase 2C consumer contract: `gateway pre-call: if no BYOK for tenant → call Phase2B.CreditLedger.reserve(tenant, callSite.cost())`. Phase 2C plan-phase MUST renumber its `014-tenant-byok-credentials.yaml` to `017+` because 2B claims `014/015/016`.

### In-code anchors (current state to extend)
- `backend/core/src/main/java/com/zeromail/core/` — new `billing/` sibling to `account/`, `gmail/`, `onboarding/`, `tenant/`, `shared/`. Sub-packages mirror Phase 1.2 per-domain shape.
- `backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java` — sidecar `CreditReservationEntity` + journal `CreditLedgerEntryEntity` + intent `BillingTopupIntentEntity` extend this.
- `backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java` — `CallSite`, reservation `Status`, intent `Status` all implement.
- `backend/core/src/main/resources/db/changelog/changes/` — next-free is `014`; allocation `014-credit-ledger-entry.yaml`, `015-credit-reservation.yaml`, `016-billing-topup-intent.yaml`.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — append the three new changeset includes.
- `backend/api/src/main/java/com/zeromail/api/controllers/` — new `billing/BillingController.java` (parity with account/, onboarding/ sub-folder grouping); add `billing/SepayWebhookController.java` if SePay webhook path is split for `@Order` security chain reasons.
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — extend or sibling `BillingWebhookSecurityConfig` adding `@Order(1)` filter chain for `/api/billing/sepay/webhook` matcher with custom HMAC verifier (mirror of `PubSubSecurityConfig` pattern from Phase 2A).
- `backend/api/src/main/java/com/zeromail/api/dto/` — new `billing/` sub-package: `BillingBalanceResponse`, `TopupIntentRequest`, `TopupIntentResponse`.
- `backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java` (or wherever `ApiError` mapping lives) — add `InsufficientCreditsException → 402` and `IllegalLedgerStateException → 500`.
- `backend/api/src/main/resources/application.yml` AND `backend/worker/src/main/resources/application.yml` — both add `SEPAY_WEBHOOK_SECRET:?` fail-fast; worker also gets `REFRESH_TOKEN_KEY_BASE64:?` (CR-04 carryover fold).
- `backend/worker/src/main/java/com/zeromail/worker/billing/` (new package) — `CreditReserveWatchdog` + `BillingIntentExpirySweeper` `@Scheduled` jobs.
- `apps/web/i18n/messages/{vi,en}.json` — add `error.billing.insufficient` keys; `pnpm i18n:check` STRICT must pass.
- `apps/web/lib/api/schema.d.ts` — regenerated by `pnpm generate:api` after `springdoc-openapi` task picks up new endpoints.

### External specs (re-fetch via Context7 or `gsd-research-phase` at implementation time)
- **SePay docs** — `https://docs.sepay.vn/` (canonical) — verify webhook payload schema, signature header name (`X-SePay-Signature` is hypothesis), HMAC algorithm (SHA-256 vs other), reference-code field name. **Recommended:** `gsd-research-phase 2B` before planning.
- **PostgreSQL `pg_advisory_xact_lock`** — `https://www.postgresql.org/docs/17/explicit-locking.html` §13.3.5 — semantics, hashtext key collision rate, transaction-scope behavior.
- **Postgres `SKIP LOCKED`** — already used in Phase 2A (`PubSubDeliveryRepository`); pattern locked.
- **ShedLock library** (or alternative) — `https://github.com/lukas-krecan/ShedLock` — for `@SchedulerLock` on watchdog; verify it's already in `libs.versions.toml` or add. Phase 2A's `GmailWatchScheduler` may already use it — reuse same artifact.

### Local references
- `D:/study-materials-summer-2026/inbox-zero/` — reference repo. Inbox-Zero uses Stripe (not SePay) but its credit ledger pattern (if present) may inform reserve/settle/release shape. **Caveat:** their Node.js implementation is a UX reference, not a code reference — Java idioms differ. Check `apps/web/utils/billing/` if exists.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AbstractTenantOwnedEntity`** (`core.shared.persistence`): all three new billing entities (`CreditLedgerEntryEntity`, `CreditReservationEntity`, `BillingTopupIntentEntity`) extend this — automatic `tenant_id` discriminator + audit columns + `@TenantId` filter.
- **`IdentifiedEnum`** (`core.shared.lang`): `CallSite`, `CreditReservationStatus`, `BillingTopupIntentStatus` all implement — provides `id()`, `labelKey()`, static `fromId` fail-loud (NoSuchElementException) per Phase 1.2.1 D-B5.
- **`TenantContext.currentOrThrow()`** (`core.tenant`): all `BillingController` methods and `CreditLedger.balance(tenantId)` resolve tenant via this ScopedValue (Phase 1 FND-02).
- **`PubSubOidcAuthFilter`** + **`PubSubSecurityConfig`** (Phase 2A): mirror this pattern for `BillingWebhookSecurityConfig` — `@Order(1) SecurityFilterChain`, `permitAll` matcher on `/api/billing/sepay/webhook`, custom HMAC filter before any other security.
- **`GmailWatchScheduler`** + **`GmailHistoryProcessor`** (Phase 2A `backend/worker`): mirror for `CreditReserveWatchdog` + `BillingIntentExpirySweeper` — `@Scheduled` + `@SchedulerLock` (or whatever Phase 2A uses) + privacy-safe `event=...` logging + Micrometer counter.
- **`RefreshTokenCipher`** (Phase 1 D-G1 — AES-GCM envelope): NOT directly reused in 2B (no encrypted column needed for billing — Phase 2C reuses it for BYOK keys). Listed for awareness so plan-phase doesn't accidentally duplicate the cipher.
- **`MultiTenantLeakIntegrationTest`** (Phase 1 FND-05): pattern for tenant-isolation test on `GET /api/billing/balance` — mirror with virtual-thread concurrent calls from two tenants asserting no cross-leak.

### Established Patterns
- **Per-domain Modulith package shape**: `model/`, `service/`, `persistence/`, `persistence/lowlevel/` — locked Phase 1.2/1.2.1, applied to gmail/account/onboarding/tenant. Apply verbatim to `core.billing`.
- **Liquibase changeset numbering**: monotonic; current floor is 013 from Phase 2A; 2B claims 014-016; 2C plan-phase renumbers BYOK to 017+.
- **`JpaAuditingConfig` + `TestJpaAuditingConfig`** (Phase 1.2.1 D-Plan 03): both production and test config required — `@CreatedDate`/`@LastModifiedDate` fail to bind under test profile without the test mirror.
- **`:?` fail-fast for deployment secrets** (Phase 1.5 CR-04): `${SEPAY_WEBHOOK_SECRET:?clear-message}` in `application.yml`; `@DynamicPropertySource` in test base supplies value.
- **Thin controllers + service-owned `@Transactional`** (CLAUDE.md Conventions §1): `BillingController.balance()` calls `creditLedger.balance(tenantId)`; controller never opens a transaction; no repository injection in controllers.
- **Records-for-DTOs / classes-for-entities Lombok-free** (CLAUDE.md Conventions §2): `BillingBalanceResponse`, `TopupIntentRequest`, `TopupIntentResponse`, `ReservationId`, `CreditBalance` are records; entities are mutable classes with `protected` no-args constructor.
- **`event=opaque tenantId={}` privacy log format** (CLAUDE.md Conventions §4): all billing/webhook/watchdog logs follow.
- **ArchUnit `DomainBoundaryArchTests` per-domain rule pattern** (Phase 1.2 D-Plan 06): add `core.billing` rule.

### Integration Points
- **Phase 2C → Phase 2B**: `LlmGateway` imports `core.billing.CreditLedger` interface + `core.billing.CallSite` enum. Phase 2C SPEC's contract sentence is satisfied by D-D1 above.
- **Phase 5 → Phase 2B**: future `/settings/billing` page calls `GET /api/billing/balance` + `POST /api/billing/topup/intent`. Phase 5 wires the QR display + payment-pending state polling. Phase 2B exposes only the API.
- **`GlobalExceptionHandler`** (`backend/api`): adds two new mappings — `InsufficientCreditsException → 402 BILLING_INSUFFICIENT_CREDITS` and `IllegalLedgerStateException → 500 BILLING_LEDGER_INVALID_STATE`. Uses Phase 1.1 `ApiError` contract (no human-readable strings server-side; frontend localizes via i18n keys).
- **`springdoc-openapi-gradle-plugin`** (Phase 1.2.1 D-Plan 04): hermetic spec emit picks up new endpoints automatically. After plan, run `pnpm generate:api` in `apps/web` to regenerate `schema.d.ts`.

</code_context>

<specifics>
## Specific Ideas

- **SePay-as-MoR rationale**: User-locked the provider as SePay (Vietnam-domestic bank-transfer aggregator) over Stripe / LemonSqueezy. SePay is a Vietnamese fintech that sits between user bank transfers and the merchant; user types a `referenceCode` into their bank-transfer memo, SePay's bank-API integration parses the memo, and SePay forwards a webhook to the merchant. There is no SePay SDK on Maven Central — integration is plain HTTP webhook + signature verification.
- **Credit unit semantics from spec interview**: TRIAGE=1, DRAFT=2, PREVIEW=1; LLM USD spend tracked separately at Phase 2C platform-side (not user-facing). BYOK=0 by gateway-skipping reserve entirely (no enum member).
- **Watchdog runs in `backend/worker`, not `backend/api`** — explicit user choice during spec phase, mirrors Phase 2A pattern.
- **No UI in Phase 2B** — backend-only; Phase 5 ships the `/settings/billing` page. `BillingController` exposes the API surface only.

</specifics>

<deferred>
## Deferred Ideas

- **Refund / chargeback automation** — admin SQL script v1; dedicated phase post-launch if dispute volume warrants.
- **PDF receipt / invoice email** — SePay's own email confirmation suffices for v1; PDF generation deferred to Phase 6 launch hardening or separate phase.
- **Credit bundles / package pricing** — single fixed `vnd-per-credit` rate v1; "buy 100 credits for 50k VND" packaging is Phase 5 marketing surface.
- **Per-action cost preview UI** — Phase 5 territory; expose constants endpoint when Phase 5 needs it.
- **Multi-currency support (USD/EUR top-up)** — VND-only v1; SePay is VN-domestic. International payment provider (Stripe) re-evaluation deferred.
- **`/settings/billing` page** — Phase 5 user-surface phase.
- **Soft-warn at low-balance threshold (e.g., 90% of typical daily spend)** — hard reject only v1; threshold tuning is Phase 5 telemetry.
- **Rate limiting on the SePay webhook beyond signature check** — abuse handling (signature replay attack mitigation, IP allowlist, etc.) is future security-hardening phase.
- **`SEPAY_WEBHOOK_SECRET` rotation drill** — captured in STATE.md Blockers under the same umbrella as `REFRESH_TOKEN_KEY_BASE64` rotation drill.
- **Admin-facing billing dashboard** — operator-side analytics (total credits issued, MRR-equivalent, refund rate) is post-v1 ops territory.
- **LLM USD spend tracking + per-tenant daily spend cap** — orthogonal concern owned by Phase 2C (it's the platform-side cost guard; integer-credits ledger here is user-facing only).
- **Anything related to `tenant_byok_credentials` table** — Phase 2C SPEC owns the table; Phase 2B only documents the BYOK exemption clause in `CreditLedger` Javadoc.

### Reviewed Todos (not folded)

- **`2026-04-28-wr-06-test-profile-securityconfig-slice.md`** (test-profile SecurityConfig slice for OAuth filter chain coverage) — Reviewed; tangential. WR-06 is about user-OAuth filter chain coverage; Phase 2B's SePay webhook filter chain is a separate `@Order(1)` matcher and gets its own integration tests. Folding WR-06 into 2B would conflate two security concerns. Defer to dedicated test-infrastructure phase.

</deferred>

---

*Phase: 2B-billing-prepaid-credits*
*Context gathered: 2026-05-05*
