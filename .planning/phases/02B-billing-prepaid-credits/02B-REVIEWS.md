---
phase: 02B
review_cycle: 2
cycles_completed: 2
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
current_high: 4
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

# Cycle 2 — Plan Reviews

Reviewers: codex, opencode
Date: 2026-05-06

## Summary

| Reviewer | Overall Risk        | HIGH | MEDIUM | LOW |
|----------|---------------------|------|--------|-----|
| codex    | MEDIUM              | 3 (new) + 1 (cycle-1 partial) | — | — |
| opencode | LOW                 | 0 (new); cycle-1 HIGHs all RESOLVED | — | — |

## Cycle 1 HIGH Resolution Verdicts

Verdicts mediate codex's stricter view (codex inspected snippets line-by-line; opencode evaluated at structural level).

| HIGH | Topic | Codex verdict | Opencode verdict | Final verdict |
|------|-------|---------------|------------------|---------------|
| HIGH-1 | Wave 0 `@Disabled` compile-RED | PARTIALLY RESOLVED (process accepted: tests committed RED, Plan 06 owns final GREEN gate; no technical fix to source) | RESOLVED | **PARTIALLY RESOLVED** (codex's stricter reading: the build is still RED during execution, gate moved to Plan 06) |
| HIGH-2 | SePay tenant binding | RESOLVED (`BillingTopupIntentTenantLookup` projection + `ScopedValue.where(TenantContext.TENANT,...)`) | RESOLVED | **RESOLVED** |
| HIGH-3 | OpenAPI `@Hidden` contradiction | RESOLVED (`@Hidden` removed, `@Tag(name="billing-webhook")` added) | RESOLVED | **RESOLVED** |
| HIGH-4 | Worker sweeper transaction | RESOLVED (`@Transactional` on both `sweep()` and `expireStale`) | RESOLVED | **RESOLVED** |
| HIGH-5 | `AdvisoryLockJdbcHelper` visibility | RESOLVED (class + ctor + method `public`; ArchUnit ban scoped to package) | RESOLVED | **RESOLVED** |
| HIGH-6 | Core test billing properties | RESOLVED (`zero-mail.billing.*` `DynamicPropertySource` added to core `PostgresContainerTest`) | RESOLVED | **RESOLVED** |
| HIGH-7 | 0-credit top-ups | RESOLVED (`createIntent` rejects below min; `applyWebhook` short-circuits with `event=sepay_topup_below_min_credits`) | RESOLVED | **RESOLVED** |

## Codex Review (Cycle 2)

### Cycle 1 HIGH Resolution Verdict

| Cycle 1 HIGH | Status |
|---|---|
| HIGH-1 (`@Disabled` compile-RED) | PARTIALLY RESOLVED |
| HIGH-2 (SePay tenant binding) | RESOLVED |
| HIGH-3 (OpenAPI `@Hidden`) | RESOLVED |
| HIGH-4 (Worker sweeper transaction) | RESOLVED |
| HIGH-5 (`AdvisoryLockJdbcHelper` visibility) | RESOLVED |
| HIGH-6 (Core test billing properties) | RESOLVED |
| HIGH-7 (0-credit top-ups) | RESOLVED |

HIGH-1 is partially resolved: the plans now openly accept that Wave 0 commits land RED and defer the green-build gate to Plan 06. This is a process acceptance, not a technical fix — `compileTestJava` still fails between Plan 00 and Plan 06, which violates the original convention that "every commit is green." The other six HIGHs have concrete plan-level fixes.

### Summary

The replanned Phase 2B is materially better than Cycle 1. Tenant-context handling, OpenAPI surface, worker transactions, advisory-lock visibility, core test wiring, and 0-credit guards are all properly addressed. The new pattern using `ScopedValue.where(TenantContext.TENANT, tenantId)` around webhook ledger writes is the right shape and matches existing tenant-aware paths.

Three new blockers surfaced on a closer inspection of Plan 03 / Plan 04 / Plan 06 snippets that will fail at compile or runtime if executed verbatim. They are localized and cheap to fix, but they are real.

### Strengths

- `BillingTopupIntentTenantLookup` projection cleanly bypasses the Hibernate tenant filter for webhook resolution, then re-binds context before any JPA write — exactly the requested shape.
- `applyWebhookForTenant` is correctly extracted as the actual transactional boundary; the outer `applyWebhook` is now a tenant-context wrapper.
- Plan 06 has an explicit "GREEN gate" section that owns the build state, making the Wave-0 RED window explicit rather than hidden.
- `AdvisoryLockJdbcHelper` is now `public` with a public `acquireTenantLock`, and the ArchUnit rule guards `JdbcTemplate` import by package, not visibility — the right separation.
- Core `PostgresContainerTest` gets `zero-mail.billing.*` `DynamicPropertySource` entries the moment `BillingProperties` is introduced, removing the test-boot foot-gun.
- Min-credit guard is now enforced on both edges: `createIntent` rejects sub-`vndPerCredit` amounts, and `applyWebhook` short-circuits with `event=sepay_topup_below_min_credits` if a stray webhook still produces 0 credits.
- `SepayWebhookController` exposes the path under `@Tag(name="billing-webhook")`; `schema.d.ts` will include it without leaking other webhook internals.

### Concerns

- **HIGH (NEW): `applyWebhook` → `applyWebhookForTenant` self-invocation bypasses the `@Transactional` proxy.** Plan 03's revised `BillingTopupService` calls `applyWebhookForTenant(...)` from the same bean. Spring's CGLIB/JDK proxy only intercepts external calls, so the inner method's `@Transactional` annotation will not start a transaction. The `ScopedValue.where(TenantContext.TENANT, tenantId).run(() -> applyWebhookForTenant(...))` pattern needs to either (a) inject self via `@Lazy` and call through the proxy, or (b) collapse the two methods and start the tenant-bound transaction with `TransactionTemplate#execute` rather than annotation-driven AOP.

- **HIGH (NEW): SePay payload field-order is wrong.** Plan 04's `SepayWebhookRequest` record places `code` before `referenceCode`, but per SePay's documented payload, `code` is the optional internal order code while `referenceCode` is the bank-issued reference and is mandatory. Several earlier snippets (and the Cycle-1 SePay correction note) treat `referenceCode` as the primary idempotency key. Either the record's parameter order or the parser/lookup logic — whichever the plan now relies on — must agree. As written, lookups by `code` will hit `null` for many real payloads and the unique constraint on `referenceCode` will not protect what callers think it does.

- **HIGH (NEW): `BillingDomainBoundaryArchTest` references `CreditLedgerService.class` from outside its package.** Plan 06's ArchUnit test is placed in `com.zeromail.core.billing.arch` (or similar) and writes `noClasses().that().areNotAssignableTo(CreditLedgerService.class)...`. But `CreditLedgerService` is package-private in `com.zeromail.core.billing.service` (the plan keeps the implementation class non-public on purpose). A `.class` literal across package boundaries on a package-private type will not compile. The ArchUnit rule must either (a) target the public `CreditLedger` interface, (b) use the fully-qualified-name string form (`JavaClass.Predicates.assignableTo("com.zeromail.core.billing.service.CreditLedgerService")`), or (c) move the test into the same package.

- **HIGH (PARTIAL from cycle 1): Wave 0 tests still committed RED.** HIGH-1 is process-resolved, not technically resolved. Plan 06 owns the green-build gate, and intermediate commits will fail `compileTestJava`. Acceptable if the team explicitly endorses RED-during-execution as a phase rule, but it is not a clean GREEN gate at every commit.

### Suggestions

- For the proxy self-invocation: the simplest fix is to keep `applyWebhook` as the only public method, drop the inner `@Transactional`, and wrap the body in `transactionTemplate.execute(status -> { ScopedValue.where(...).run(...); })` — this keeps the tenant binding inside the transaction and avoids self-invocation entirely.
- For the SePay record: re-check the latest SePay docs (Cycle-1 already corrected from HMAC → API key; the field semantics are the next layer). Make `referenceCode` non-null and the canonical idempotency key in both the record's component order and `BillingTopupTransactionEntity.referenceCode`.
- For the ArchUnit test: prefer string-FQN matching for cross-package package-private targets, or expose a marker interface (`CreditLedgerInternal`) that the impl implements and the test references.
- Consider documenting the Wave-0 RED window as a phase-level convention in `PHASE-OVERVIEW.md` so reviewers don't keep re-flagging it.

### Risk Assessment

**Overall risk: MEDIUM.** Cycle 1's structural blockers are gone. The three new HIGHs are all localized to specific snippets and each has a one-line fix path. The lingering Wave-0 RED window is a process choice rather than a defect. After these three fixes, residual risk is concurrency-test fidelity and Liquibase-syntax confirmation — both already on the MEDIUM list.

## OpenCode Review (Cycle 2)

### Summary

All seven Cycle 1 HIGH concerns have been properly addressed in the replanned phase. The fixes are concrete, localized, and consistent across the affected plans (00, 03, 04, 05, 06). The plans now read as executable: tenant context is bound before any JPA write in webhook flows, OpenAPI exposes `/api/billing/sepay/webhook` under `billing-webhook` tag, the worker sweeper is transactional, `AdvisoryLockJdbcHelper` is public with a properly-scoped ArchUnit guard, core tests get billing properties, and the 0-credit edge case is guarded on both ends.

### Strengths

- **HIGH-1 (Wave 0 RED)**: Plan 00 now explicitly states `depends_on:[02]` and acknowledges RED-during-execution; Plan 06 owns the final clean-check gate. The phase decomposes the build-state lifecycle honestly.
- **HIGH-2 (SePay tenant binding)**: The `BillingTopupIntentTenantLookup` projection + `ScopedValue.where(TenantContext.TENANT, tenantId)` pattern is the right shape and matches the existing tenant-aware code paths. The two-step (lookup unfiltered, bind, write) is well-documented.
- **HIGH-3 (OpenAPI)**: `@Hidden` removed, replaced with `@Tag(name="billing-webhook")`. `schema.d.ts` will now include the path. Acceptance criterion in Plan 06 aligns.
- **HIGH-4 (Worker transaction)**: `@Transactional` is now on `BillingIntentExpirySweeper.sweep()` and defensively also on `BillingTopupIntentRepository.expireStale`. No transaction-required runtime risk.
- **HIGH-5 (AdvisoryLockJdbcHelper)**: Class, constructor, and `acquireTenantLock` are all public. The ArchUnit rule bans `JdbcTemplate` imports by package, not by visibility — the correct separation of concerns.
- **HIGH-6 (Core test properties)**: `PostgresContainerTest` in core gets `zero-mail.billing.*` `DynamicPropertySource` entries when `BillingProperties` is introduced. Core `@SpringBootTest` billing tests will boot.
- **HIGH-7 (0-credit top-ups)**: `createIntent` rejects `amountVnd < vndPerCredit`; `applyWebhook` short-circuits with `event=sepay_topup_below_min_credits` if credits<=0. Belt-and-suspenders.

### Concerns

No new HIGH concerns. Previously raised MEDIUM/LOW items (hashtext collision documentation, advisory-lock observability counter, webhook response shape verification, security chain ordering verification) remain in scope as polish but do not block execution.

### Suggestions

- Carry forward the cycle 1 polish items (Micrometer counter for advisory lock contention, exact `{"success": true}` response shape verification against SePay docs, security-chain matcher specificity check) into Plan 06 verification or a follow-up phase.
- Document the Wave-0 RED window as an explicit phase-level convention so future reviewers understand it is intentional.

### Risk Assessment

**Overall Risk Level: LOW.**

All seven Cycle 1 HIGH concerns are addressed with concrete plan-level fixes. The plans are executable as-is. Residual risk is implementation-level (concurrency edge cases, Liquibase syntax) and well-covered by Wave 0 tests.

## Consolidated Cycle 2 HIGH Concerns

Deduped across reviewers. Counts only newly-raised HIGHs in cycle 2 plus any partially-resolved HIGHs from cycle 1, per the convergence inclusion rule.

- **HIGH (codex, NEW):** `BillingTopupService.applyWebhook` calls `applyWebhookForTenant` on `this` — Spring's `@Transactional` proxy does not intercept self-invocation, so the inner method's transaction never starts. Fix: use `TransactionTemplate#execute` inside the `ScopedValue.where(...).run(...)` block, or inject self via `@Lazy` and call through the proxy.
- **HIGH (codex, NEW):** `SepayWebhookRequest` record orders `code` before `referenceCode`, but per SePay's payload `referenceCode` is the mandatory bank-issued reference and the canonical idempotency key, while `code` is optional. The record's component order or the lookup/uniqueness logic must agree. As written, lookups by `code` will hit null for many real payloads.
- **HIGH (codex, NEW):** `BillingDomainBoundaryArchTest` uses a `.class` literal (`CreditLedgerService.class`) on a package-private class from outside its package — won't compile. Fix: target the public `CreditLedger` interface, use ArchUnit's string-FQN form, or move the test into the same package.
- **HIGH (codex, PARTIAL from cycle 1):** Wave 0 `@Disabled` tests are still committed RED. Plan 06 now owns the final clean-check gate, but intermediate commits between Plan 00 and Plan 06 fail `compileTestJava`. Process-accepted, not technically resolved.
