---
phase: 02B
review_cycle: 1
reviewers: [codex, opencode]
reviewed_at: 2026-05-06
plans_reviewed:
  - 02B-00-wave0-tests-PLAN.md
  - 02B-01-schema-and-deps-PLAN.md
  - 02B-02-domain-model-PLAN.md
  - 02B-03-credit-ledger-service-PLAN.md
  - 02B-04-api-surface-PLAN.md
  - 02B-05-worker-schedulers-PLAN.md
  - 02B-06-verification-closure-PLAN.md
current_high: 0
addressed_at: 2026-05-06
addressed_in: replan with --reviews; see "REVIEWS HIGH-N — RESOLVED" markers in each PLAN.md
high_resolution_map:
  HIGH-1: 02B-00-wave0-tests-PLAN.md (depends_on:[02], honest RED-during-execution build state, Plan 06 owns final clean-check gate)
  HIGH-2: 02B-03-credit-ledger-service-PLAN.md (BillingTopupIntentTenantLookup projection + ScopedValue.where(TenantContext.TENANT,...) before any JPA write in applyWebhook)
  HIGH-3: 02B-04-api-surface-PLAN.md (SepayWebhookController @Hidden removed; @Tag(name="billing-webhook") so OpenAPI emits the path)
  HIGH-4: 02B-05-worker-schedulers-PLAN.md + 02B-03-credit-ledger-service-PLAN.md (BillingIntentExpirySweeper.sweep() declares @Transactional; expireStale repo method also annotated defensively)
  HIGH-5: 02B-03-credit-ledger-service-PLAN.md (AdvisoryLockJdbcHelper class + constructor + acquireTenantLock are public; ArchUnit guards JdbcTemplate boundary by package, not visibility)
  HIGH-6: 02B-03-credit-ledger-service-PLAN.md (core PostgresContainerTest gets zero-mail.billing.* DynamicPropertySource entries when BillingProperties is introduced)
  HIGH-7: 02B-03-credit-ledger-service-PLAN.md (createIntent rejects amountVnd<vndPerCredit; applyWebhook short-circuits if credits<=0 with event=sepay_topup_below_min_credits)
---

# Phase 2B Plan Reviews — Cycle 1

Reviewers: codex, opencode
Date: 2026-05-06

## Summary

| Reviewer | Overall Risk        | HIGH | MEDIUM | LOW |
|----------|---------------------|------|--------|-----|
| codex    | HIGH (→ MEDIUM after fixes) | 7    | 4      | 2   |
| opencode | LOW                 | 0    | 1      | 4   |

Codex finds the architecture directionally strong but flags seven blockers that would either break the build or break runtime flows (test compile-red, SePay tenant binding, OpenAPI contradiction, worker sweeper transaction, `AdvisoryLockJdbcHelper` visibility, missing core test billing properties, 0-credit top-ups). Opencode raises no HIGH concerns and assesses overall risk as LOW, with mostly polish-level suggestions around hashtext collision, advisory-lock observability, webhook response shape, and security chain ordering. The two reviewers diverge sharply on severity: codex inspected the plan code snippets in detail and surfaced concrete compile/runtime failures; opencode evaluated structure and patterns at a higher level.

## Codex Review

### Summary

The plans are unusually thorough and mostly align with the Phase 2B goal: a backend-only prepaid credit ledger with reserve/settle/release, SePay top-ups, insufficient-balance blocking, BYOK exemption, and worker cleanup. The architecture is sound in broad strokes, and the use of Postgres advisory locks, append-only journal rows, idempotency constraints, ShedLock, and module-boundary tests is directionally strong. However, several issues are serious enough that I would not execute these plans as-is. The main blockers are test-plan contradictions, tenant-context handling for SePay webhooks, transactional gaps in worker/repository operations, and a few implementation contradictions that will cause compile or runtime failures.

### Strengths

- Strong phase decomposition: schema, domain model, service implementation, API, worker, and verification are separated cleanly.
- The ledger semantics are clear: `RESERVE` debits availability, `SETTLE` finalizes, `RELEASE` restores availability.
- Advisory lock per tenant is a pragmatic concurrency choice for v1 and directly targets the double-reserve race.
- SePay API-key auth correction is incorporated, replacing the earlier HMAC assumption.
- Idempotency is considered at multiple layers: ledger uniqueness, SePay transaction uniqueness, settle/release no-op behavior.
- ShedLock usage is appropriate for multi-worker scheduled jobs; the documented `@EnableSchedulerLock`, `@SchedulerLock`, and JDBC provider pattern matches current ShedLock guidance.
- Spring Security chain ordering with `securityMatcher` is the right shape; the later correction to avoid `@Order` collision with Pub/Sub is important.
- BYOK boundary is correctly kept out of Phase 2B implementation and documented at the `CreditLedger` interface.

### Concerns

- **HIGH: Plan 00 assumes `@Disabled` prevents compile errors.** It does not. Disabled tests still compile, so tests importing future classes will break `compileTestJava`. This contradicts the later requirement that `clean check` remains green.

- **HIGH: SePay webhook flow lacks tenant binding.** `BillingTopupService.applyWebhook()` looks up `BillingTopupIntentEntity` and writes tenant-owned ledger rows without a session `TenantContext`. With Hibernate tenant filtering, this likely fails or returns no intent. Webhook handling needs a tenant-filter-bypassing lookup projection, then `ScopedValue.where(TenantContext.TENANT, tenantId)` before JPA writes.

- **HIGH: OpenAPI contradiction for SePay webhook.** `SepayWebhookController` is annotated `@Hidden`, but acceptance requires `schema.d.ts` to include `/api/billing/sepay/webhook`. One of these must change.

- **HIGH: Worker sweeper lacks a transaction.** `BillingTopupIntentRepository.expireStale()` is `@Modifying`, but neither the repository method nor `BillingIntentExpirySweeper.sweep()` is clearly transactional. This can fail at runtime with a transaction-required error.

- **HIGH: `AdvisoryLockJdbcHelper` visibility is unresolved.** The snippet makes the class and method package-private in `persistence.lowlevel`, then imports it from `core.billing.service`, which cannot compile. The plan notes this but does not choose one final implementation.

- **HIGH: Core test config is missing billing properties.** Plan 03 introduces `BillingProperties` with required `sepay.webhookApiKey`, but only API and worker test bases get `DynamicPropertySource` updates. Core `@SpringBootTest` billing tests may fail to boot.

- **HIGH: 0-credit top-ups are not handled.** `credits = floor(amountVnd / vndPerCredit)` can be `0`, but `CreditLedgerEntryEntity.topup()` requires positive credits. Either reject too-small top-up intents up front or acknowledge the webhook without ledger credit and log for manual reconciliation.

- **MEDIUM: Finalization races need sharper handling.** Concurrent `settle`/`release` can rely on optimistic locking, but the plan does not explicitly test a settle-vs-release race. The watchdog also catches all `IllegalLedgerStateException`, which can hide "reservation not found" or real corruption.

- **MEDIUM: `FOR UPDATE SKIP LOCKED` in watchdog is overstated.** Without an enclosing transaction around claim and processing, locks from the native query are released immediately after the statement. ShedLock still protects multi-pod execution, but the `SKIP LOCKED` claim semantics are not doing what the plan claims.

- **MEDIUM: JPA aggregate return types may be wrong.** `SUM(int)` commonly returns `Long`, but repository methods return `int`. This should be verified and probably changed to `long`/`Long` then safely converted.

- **MEDIUM: Liquibase YAML may not be valid as written.** The inline FK syntax with `references: 'tenants(id)'` and `addCheckConstraint` needs validation against the repo's Liquibase version. Prefer known-good patterns from existing changesets or raw SQL for constraints.

- **MEDIUM: The ledger is not truly double-entry.** It is an append-only signed journal plus sidecar reservation table. That is probably fine for v1, but the plans should avoid implying accounting-grade double-entry unless there are explicit debit/credit accounts.

- **LOW: Counts drift across plans.** The phase alternates between 16 and 17 Wave 0 tests, and Plan 06 still says 16 in places. This will confuse execution and verification.

- **LOW: Some config is duplicated.** Plan 03 adds core `BillingConfiguration`, then API and worker add module-specific `@EnableConfigurationProperties` configs. If the apps already scan `com.zeromail.core`, this may be redundant or cause duplicate registration warnings.

### Suggestions

- Make Wave 0 tests compile-green by design: either use reflection/string-based assertions, place future-contract tests in an excluded source set, or land minimal production type skeletons before tests.
- Redesign SePay webhook handling around a native/projection lookup by code: resolve `{tenantId, intentId, status, amount}`, then bind `TenantContext` and perform JPA updates/ledger writes.
- Remove `@Hidden` from `SepayWebhookController` if OpenAPI generation must include it, or explicitly drop that endpoint from the schema acceptance criteria.
- Add `@Transactional` to `BillingIntentExpirySweeper.sweep()` or `BillingTopupIntentRepository.expireStale()`.
- Make `AdvisoryLockJdbcHelper` public with a public `acquireTenantLock(...)`, while keeping the `JdbcTemplate` ArchUnit ban scoped to package imports.
- Add `zero-mail.billing.*` properties to the core test base as soon as `BillingProperties` is introduced.
- Enforce minimum top-up amount: `amountVnd >= vndPerCredit`, or define the exact no-credit handling path.
- Add DB checks for ledger sign invariants: `TOPUP/RELEASE > 0`, `RESERVE < 0`, `SETTLE = 0`, and valid `ref_type`.
- Add at least one concurrent finalization test: settle-vs-release racing on the same reservation.
- Convert balance aggregate calculations to `long` internally and only expose `int` after range validation.
- Normalize the test count and requirements status language across all plans before execution.

### Risk Assessment

**Overall risk: HIGH until the blockers above are fixed; MEDIUM after fixes.** The design is defensible and likely to meet BILL-01 through BILL-07, but the current execution plans contain several issues that would either fail the build or break critical runtime flows, especially webhook tenant binding, disabled compile-red tests, worker transaction handling, and OpenAPI visibility. Once those are corrected, the remaining risk is mostly implementation complexity around concurrency and idempotency, which the planned tests are well-positioned to catch.

## OpenCode Review

### Summary

The implementation plans for Phase 2B are comprehensive and well-structured, following established patterns in the codebase while properly addressing all requirements (BILL-01 through BILL-07). The plans demonstrate strong attention to privacy, security, concurrency safety, and testability. The research-driven correction regarding SePay's authentication mechanism (API key vs HMAC) is properly implemented.

### Strengths

- **Proper Layering**: Clean separation between API controllers, services, and persistence layers following existing patterns
- **Privacy-First Approach**: All logging follows the `event=opaque tenantId={}` format with no PII or sensitive data leakage
- **Concurrency Safety**: Uses `pg_advisory_xact_lock` with `Propagation.REQUIRES_NEW` for atomic reserve operations, verified by concurrent test scaffolds
- **Idempotency**: Properly implemented through database UNIQUE constraints and careful exception handling
- **Test Coverage**: Comprehensive Wave 0 RED tests covering all requirements, including edge cases like replay protection and insufficient funds
- **SePay Integration**: Correctly implements API key authentication with constant-time comparison per verified SePay documentation
- **Modulith Compliance**: Proper package-info declarations with correct allowedDependencies
- **Observability**: Includes Micrometer counters for watchdog operations
- **Dependency Management**: Properly adds ShedLock 7.7.0 for distributed locking of scheduled jobs

### Concerns (with Severity)

- **MEDIUM**: The advisory lock approach using `hashtext(tenantId)` has potential collision risk at scale (>65k tenants), though documented as acceptable for v1
- **LOW**: Reserve operation uses `Propagation.REQUIRES_NEW` which could theoretically create orphaned reserves if outer transaction fails after reserve but before settle/release - mitigated by watchdog safety net
- **LOW**: Need to verify Liquibase check constraint syntax compatibility with version 5.0.2
- **LOW**: Webhook controller returns `Map.of("success", true)` - should verify this exactly matches SePay's expected response format
- **LOW**: Verify security chain ordering (webhook @Order(2) with existing security bumped to @Order(3)) doesn't cause unintended behavior

### Suggestions

- Add explicit documentation about the hashtext collision risk and migration path to two-key advisory lock for future scaling
- Consider adding a Micrometer counter for advisory lock acquisition attempts to monitor contention
- Add explicit validation that SePay webhook returns exactly `{"success": true}` as per their documentation
- Ensure the `@Order(1)` webhook security chain doesn't interfere with other security chains by verifying the security matcher specificity
- Consider adding a health check endpoint for billing service status

### Risk Assessment

**Overall Risk Level: LOW**

**Justification**:
- All core requirements (BILL-01 through BILL-07) are properly addressed
- Privacy and security concerns are thoroughly handled with constant-time comparisons, proper logging, and input validation
- Concurrency safety is verified through test scaffolds and implemented with appropriate locking mechanisms
- The plans follow established patterns in the codebase and maintain proper layering
- Test coverage is comprehensive including edge cases
- External dependencies (SePay) are properly isolated and authenticated
- The few identified risks are either documented as acceptable for v1 or have appropriate mitigation strategies

The implementation plans are well-prepared to successfully deliver the Phase 2B goals of establishing a secure, reliable prepaid credits billing system with proper SePay integration, concurrency controls, and API endpoints.

## Consolidated HIGH Concerns

All seven HIGHs are codex-only; opencode raised none. None are resolved (this is the first review cycle).

- **HIGH (codex):** Wave 0 `@Disabled` tests still compile-link against unimplemented classes, breaking `compileTestJava` despite plan 00's claim that `clean check` stays green.
- **HIGH (codex):** SePay webhook (`BillingTopupService.applyWebhook`) reads/writes tenant-scoped entities without binding `TenantContext`, so Hibernate tenant filters will hide intents or block ledger writes.
- **HIGH (codex):** `SepayWebhookController` is `@Hidden` from OpenAPI but Plan 06 acceptance requires `/api/billing/sepay/webhook` to appear in `schema.d.ts` — one side must change.
- **HIGH (codex):** Worker sweeper (`BillingIntentExpirySweeper.sweep` / `BillingTopupIntentRepository.expireStale`) is `@Modifying` without a clearly enclosing `@Transactional`, will fail at runtime with transaction-required.
- **HIGH (codex):** `AdvisoryLockJdbcHelper` is shown as package-private in `persistence.lowlevel` yet imported from `core.billing.service` — won't compile; final visibility/placement is unresolved.
- **HIGH (codex):** Plan 03 introduces required `BillingProperties` (incl. `sepay.webhookApiKey`) but only API/worker test bases get `DynamicPropertySource` overrides; core `@SpringBootTest` billing tests will fail to boot.
- **HIGH (codex):** `credits = floor(amountVnd / vndPerCredit)` can be `0`, but `CreditLedgerEntryEntity.topup()` requires positive credits — webhook will throw on too-small SePay deposits.
