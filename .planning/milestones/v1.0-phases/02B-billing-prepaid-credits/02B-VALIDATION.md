---
phase: 2B
slug: billing-prepaid-credits
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-05
audited: 2026-05-07
---

# Phase 2B — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `02B-RESEARCH.md` §"Validation Architecture" (Nyquist).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Boot 4.0.6 managed) + Spring Boot Test + Testcontainers 1.21.3 + AssertJ + ArchUnit |
| **Config file** | None — auto-discovered. Per-module test base in `backend/{core,api,worker}/src/test/java/.../support/` |
| **Quick run command** | `./gradlew :backend:core:test --tests "com.zeromail.core.billing.*"` |
| **Full suite command** | `./gradlew clean check` |
| **Frontend i18n parity** | `pnpm i18n:check` (STRICT) |
| **OpenAPI codegen round-trip** | `./gradlew :backend:api:openApi && pnpm --filter web generate:api` |
| **Estimated runtime (full)** | ~180–240 seconds (Testcontainers Postgres boot + concurrent-reserve test) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :backend:core:check :backend:api:check :backend:worker:check` (unit + ArchUnit + static; skips integration if DB unavailable)
- **After every plan wave:** `./gradlew clean check` (full suite incl. Testcontainers)
- **Before `/gsd-verify-work` (phase gate):** full suite + `pnpm i18n:check` STRICT + `pnpm generate:api` round-trip + `./gradlew :backend:api:openApi` hermetic emit + ArchUnit zero violations
- **Max feedback latency (per-task):** ~30 seconds (unit + ArchUnit only)

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|-----------|-------------------|-------------|--------|
| BILL-01 | SePay webhook accepted, payload parsed, TOPUP entry created | integration | `./gradlew :backend:api:test --tests "*SepayWebhookIntegrationTest*"` | ✅ | ✅ green |
| BILL-01 | Replay (same `id`) returns 200 + no duplicate ledger entry | integration | `./gradlew :backend:api:test --tests "*SepayReplayTest*"` | ✅ | ✅ green |
| BILL-01 | Bad / missing `Authorization: Apikey` returns 401, no ledger touch | integration | `./gradlew :backend:api:test --tests "*SepayBadAuthTest*"` | ✅ | ✅ green |
| BILL-01 | API-key constant-time compare unit test | unit | `./gradlew :backend:core:test --tests "*SepayApiKeyVerifierTest*"` | ✅ | ✅ green |
| BILL-02 | Liquibase 014/015/016 (and 017 for ShedLock) apply cleanly on Testcontainers | integration | existing `PostgresContainerTest` boot pulls all changesets | ✅ (boot-level) | ✅ green |
| BILL-02 | UNIQUE `(ref_type, ref_id, kind)` blocks duplicate insert | integration | `./gradlew :backend:core:test --tests "*CreditLedgerEntryUniqueTest*"` | ✅ | ✅ green |
| BILL-03 | 10 virtual threads × `reserve(TRIAGE)` on `available=5` → exactly 5 OK + 5 `InsufficientCreditsException` | integration | `./gradlew :backend:core:test --tests "*CreditLedgerConcurrentReserveTest*"` | ✅ | ✅ green |
| BILL-03 | `settle` twice → no-op (UNIQUE wins) | integration | `./gradlew :backend:core:test --tests "*CreditLedgerSettleIdempotentTest*"` | ✅ | ✅ green |
| BILL-03 | `release` after `settle` → `IllegalLedgerStateException` | integration | (same class as above) | ✅ | ✅ green |
| BILL-04 | Watchdog releases reservation older than 5 min | integration | `./gradlew :backend:worker:test --tests "*CreditReserveWatchdogTest*"` | ✅ | ✅ green |
| BILL-04 | Watchdog tick on already-released reservation = no-op | integration | (same class) | ✅ | ✅ green |
| BILL-04 | `BillingIntentExpirySweeper` flips PENDING → EXPIRED idempotently | integration | `./gradlew :backend:worker:test --tests "*BillingIntentExpirySweeperTest*"` | ✅ | ✅ green |
| BILL-05 | `GET /api/billing/balance` returns shape `{availableCredits, heldCredits, currency}` | integration | `./gradlew :backend:api:test --tests "*BillingBalanceControllerTest*"` | ✅ | ✅ green |
| BILL-05 | Tenant A cannot see tenant B's balance | integration | `./gradlew :backend:api:test --tests "*BillingBalanceMultiTenantLeakTest*"` | ✅ | ✅ green |
| BILL-06 | `reserve` insufficient → 402 + `code=BILLING_INSUFFICIENT_CREDITS` + no balance number leak | integration | `./gradlew :backend:api:test --tests "*BillingInsufficientCredits*"` (test-only `@RestController` wraps `reserve` for HTTP-layer verification) | ✅ | ✅ green |
| BILL-06 | i18n keys `error.billing.insufficient` in `vi.json` + `en.json` | static | `pnpm i18n:check` (STRICT) | ✅ | ✅ green |
| BILL-07 | `CreditLedger` interface Javadoc contains BYOK exemption clause | static | `./gradlew :backend:core:test --tests "*CallSiteEnumMembershipArchTest*"` | ✅ | ✅ green |
| BILL-07 | `CallSite` enum has exactly `{TRIAGE, DRAFT, PREVIEW}` | ArchUnit | (same class) | ✅ | ✅ green |
| Modulith | `core.billing` `package-info` has correct `allowedDependencies` | ArchUnit | `./gradlew :backend:core:test --tests "*ApplicationModulesTest*" *DomainBoundaryArchTests*` | ✅ | ✅ green |
| Privacy | No log line contains raw payload bytes / `Authorization` header / `transactionId` | log-assertion | `./gradlew :backend:api:test --tests "*BillingPrivacyLogScrubTest*"` (synthetic traffic + log-output capture) | ✅ | ✅ green |
| Boundary | `CreditLedgerService` cannot be instantiated outside `core.billing.service` | ArchUnit | `./gradlew :backend:core:test --tests "*BillingDomainBoundaryArchTest*"` | ✅ | ✅ green |
| Boundary | Raw `JdbcTemplate` usage banned outside `core.billing.persistence.lowlevel` | ArchUnit | (same class as above) | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 stubs (failing tests created BEFORE implementation, per Nyquist contract):

- [x] `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java` — REQ BILL-03
- [x] `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java` — REQ BILL-03 (settle/release/double-finalize)
- [x] `backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java` — REQ BILL-01 (constant-time compare unit)
- [x] `backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java` — Crockford base32 8-char alphabet + collision retry
- [x] `backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java` — REQ BILL-02
- [x] `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` — REQ BILL-07
- [x] `backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java` — Modulith + raw-JdbcTemplate ban + service-instantiation ban
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java` — REQ BILL-01
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java` — REQ BILL-01 idempotency
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java` — REQ BILL-01 401 path
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java` — REQ BILL-05
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java` — REQ BILL-05 isolation
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java` — privacy invariant
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java` — REQ BILL-01 mismatch audit event
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java` — REQ BILL-06 HTTP 402 mapping
- [x] `backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java` — REQ BILL-04
- [x] `backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java` — D-C4 (CONTEXT)
- [x] Update `apps/web/i18n/messages/{vi,en}.json` with `error.billing.insufficient` (+ any other `error.billing.*` keys planner introduces)
- [x] Update `ApiPostgresTestBase.props()` + worker `PostgresContainerTest.props()` with `zeromail.billing.sepay.webhook-api-key=test-sepay-key-fixture` (so `:?` fail-fast doesn't crash `@SpringBootTest`)
- [x] Update `backend/api/build.gradle.kts` `customBootRun.args` with dummy `--zeromail.billing.sepay.webhook-api-key=openapi-emit` (so `springdoc-openapi` hermetic emit boots — Pitfall 9 mitigation)
- [x] Add `shedlock = "7.7.0"` (and library entries `shedlock-spring`, `shedlock-provider-jdbc-template`) to `gradle/libs.versions.toml` plus `017-shedlock-table.yaml` Liquibase changeset

**No new test-framework install needed** — JUnit 5, Testcontainers, AssertJ, ArchUnit are all on the classpath via Boot 4.0.6 BOM and existing module wiring.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live SePay sandbox round-trip (real bank transfer → real webhook) | BILL-01 (operational) | Cannot Testcontainers SePay; depends on a real Vietnamese bank-transfer + SePay account | Phase 5 launch hardening: configure SePay sandbox merchant, send 10k VND test transfer with reference code, observe webhook receipt + ledger TOPUP entry. Tracked in STATE.md Blockers. |
| `pg_advisory_xact_lock` collision rate at scale | BILL-03 (operational) | Requires production traffic distribution to observe `hashtext(uuid)` collision frequency | Phase 6 ops: enable `pg_locks WHERE locktype='advisory'` sampling job, alert if two unrelated tenants ever share a hashtext slot for >1s. |
| Operator dashboard for `event=sepay_webhook_unknown_code` reconciliation | BILL-01 (operational) | No admin UI in 2B; manual SQL queries on `billing_topup_intent` for now | Document SQL recipe in STATE.md Operator Runbook subsection. |

---

## Validation Sign-Off

- [x] All requirements have at least one automated verify command OR a Wave 0 stub
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all `❌ W0` references in the table above
- [x] No watch-mode flags (`--watch`, `--continuous`) in any test command
- [x] Per-task feedback latency < 60s (unit + ArchUnit only)
- [x] `nyquist_compliant: true` set in frontmatter once Wave 0 is complete
- [x] `wave_0_complete: true` set in frontmatter once Wave 0 stubs land

**Approval:** validated 2026-05-07 (Plan 06 closure: `./gradlew clean check` + `pnpm i18n:check` + `pnpm generate:api` all GREEN; no `@Disabled` annotations remain in billing tests).

---

## Validation Audit 2026-05-07

| Metric | Count |
|--------|-------|
| Requirements audited | 22 (BILL-01..07 expanded + Modulith + Privacy + Boundary) |
| COVERED | 22 |
| PARTIAL | 0 |
| MISSING | 0 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated to manual-only | 0 |

**Evidence:**
- All 17 Wave 0 test files exist on disk under `backend/{core,api,worker}/src/test/.../billing/`.
- `grep "@Disabled"` across billing test trees → zero matches (Plan 06 enabled the boundary + enum tests).
- Plan 06 SUMMARY records `./gradlew clean check`, `pnpm i18n:check`, `pnpm generate:api` all PASS.
- i18n: `error.billing.insufficient` present in both `apps/web/i18n/messages/en.json:511` and `vi.json:511`; nested `billing.ledger.invalidState` and `billing.sepay.*` keys also present.
- ShedLock 7.7.0 wired into `gradle/libs.versions.toml`; changesets `014..017` present in `backend/core/src/main/resources/db/changelog/changes/`.
- Test fixtures `zeromail.billing.sepay.webhook-api-key` wired in `ApiPostgresTestBase.java:69`, `PostgresContainerTest.java:54`, and `backend/api/build.gradle.kts:61`.
- Bonus tests beyond Wave 0: `SepayConcurrentDeliveryTest`, `SepayMemoExtractionTest` (added during plan 04 execution; raise coverage above the baseline contract).

**Verdict:** Phase 02B is **NYQUIST-COMPLIANT**. No new tests generated.

---

*Phase: 02B-billing-prepaid-credits*
*Validation strategy: 2026-05-05*
*Source: `02B-RESEARCH.md` §"Validation Architecture"*
