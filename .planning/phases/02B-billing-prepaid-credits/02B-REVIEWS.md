---
phase: 02B
review_cycle: 3
cycles_completed: 3
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
cycle3_high_breakdown: 3 new codex HIGHs + 1 carry-over PARTIAL (Wave 0 RED window)
cycle3_opencode_status: provider_error (skipped — only codex reviewed cycle 3)
cycle3_new_highs:
  - "version column type mismatch: new billing migrations declare version as bigint but AbstractAuditableEntity.version is Integer; spring.jpa.hibernate.ddl-auto=validate will fail"
  - "SEPAY_WEBHOOK_API_KEY:?msg placeholder is not reliable Spring fail-fast for plain strings — missing env can bind the literal '?...must...' and @NotBlank passes"
  - "SepayWebhookMismatchAuditEventTest uses code='MISMATCH' but 'I' is excluded by Crockford regex and service ignores referenceCode for lookup; will log unknown_code not amount_mismatch"
cycle3_addressed_at: 2026-05-06
cycle3_addressed_in: third replan with --reviews; mechanical edits only — no architecture changes
cycle3_high_resolution_map:
  CYCLE3-HIGH-1 (version column bigint vs int): 02B-01-schema-and-deps-PLAN.md (014/015/016 changesets all use type:int matching AbstractAuditableEntity.version Integer; must_haves truth + verify negative-grep assert no bigint anywhere)
  CYCLE3-HIGH-2 (SEPAY_WEBHOOK_API_KEY fail-fast): 02B-04-api-surface-PLAN.md + 02B-05-worker-schedulers-PLAN.md (bare ${SEPAY_WEBHOOK_API_KEY} with NO `:?` default — Spring placeholder resolution itself raises IllegalArgumentException at boot when env missing) + 02B-03-credit-ledger-service-PLAN.md (BillingProperties compact constructor sentinel-rejects values starting with ? / $ or containing 'must be set' / 'must be supplied' as defense-in-depth)
  CYCLE3-HIGH-3 (SepayWebhookMismatchAuditEventTest fixture): 02B-00-wave0-tests-PLAN.md File 13b (seed intent with code='ABCD2345' valid 8-char Crockford; payload code='ABCD2345' matches lookup with mismatching transferAmount=99000 vs amountVnd=50000; positive assert event=sepay_webhook_amount_mismatch + negative assert event=sepay_unknown_code; verify grep asserts ABCD2345 present AND 'MISMATCH' absent)
  CYCLE3-HIGH-4 (Wave 0 RED window): 02B-00-wave0-tests-PLAN.md (no further technical fix per user direction; cleanup applied — Task 1 prose rewritten to remove the contradictory '@Disabled keeps clean check GREEN' wording, now correctly states @Disabled only affects RUN time not COMPILE time, matching the frontmatter's RED-by-design statement)
cycle2_addressed_at: 2026-05-06
cycle2_addressed_in: second replan with --reviews; see "REVIEWS HIGH-N NEW — RESOLVED" markers in plans 03/04/06 and the explicit RED-window convention block at the top of plan 00
cycle2_high_resolution_map:
  CYCLE2-HIGH-1 (proxy self-invocation): 02B-03-credit-ledger-service-PLAN.md (TransactionTemplate.executeWithoutResult inside ScopedValue.where(...).run(...); applyWebhookForTenant collapsed into private applyTopupCreditTransactional with NO @Transactional; transactionTemplate constructor-injected)
  CYCLE2-HIGH-2 (SePay code vs referenceCode): 02B-03-credit-ledger-service-PLAN.md + 02B-04-api-surface-PLAN.md (extractIntentCode reads payload.code() first with content fallback; referenceCode is audit metadata only and persisted to BillingTopupTransactionEntity.referenceCode; idempotency key is the SePay transactionId; service signature reordered to applyWebhook(transactionId, code, referenceCode, content, transferType, amount))
  CYCLE2-HIGH-3 (ArchUnit .class literal cross-package): 02B-06-verification-closure-PLAN.md + 02B-00-wave0-tests-PLAN.md (BillingDomainBoundaryArchTest uses ArchUnit's haveFullyQualifiedName(String) FQN form targeting "com.zeromail.core.billing.service.CreditLedgerService" — no .class literal on a package-private cross-package type)
  CYCLE2-HIGH-4 (Wave-0 RED window PARTIAL): 02B-00-wave0-tests-PLAN.md (explicit phase-level convention block accepting the RED window between Plan 00 and Plan 06; intra-phase commits tagged [wave-0-red]; Plan 06 owns the final GREEN clean-check gate; scoped exception to Phase 2B only)
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

## Cycle 2 — Resolution Plan (Replan #2)

Date: 2026-05-06. Triggered by user `/gsd-plan-phase 2B --reviews` second replan. All four cycle-2 HIGHs addressed below; resolution markers `REVIEWS HIGH-N NEW — RESOLVED` (or `PARTIAL`) appear inline in the PLAN.md files.

### CYCLE2-HIGH-1 — `applyWebhook` proxy self-invocation — RESOLVED

**Plan 03 changes (02B-03-credit-ledger-service-PLAN.md):**

- `BillingTopupService` now constructor-injects `TransactionTemplate` alongside the existing repositories.
- The previous `applyWebhookForTenant(...)` `@Transactional` method was COLLAPSED into a private `applyTopupCreditTransactional(...)` method with NO `@Transactional` annotation.
- `applyWebhook(...)` now opens the transaction explicitly via `transactionTemplate.executeWithoutResult(...)` INSIDE the `ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString()).run(...)` block. Tenant binding therefore wraps the transaction (so JPA flushes see the bound `@TenantId`), AND there is no proxied self-invocation surface to bypass.
- Verify automated greps now assert `transactionTemplate.executeWithoutResult` is present AND `applyWebhookForTenant` is absent in the final `BillingTopupService.java`.

This is option (a) from the codex suggestion list: "collapse the two methods and start the tenant-bound transaction with `TransactionTemplate#execute` rather than annotation-driven AOP." We chose this over `@Lazy` self-injection because (i) it avoids the proxy self-injection idiom that confuses reviewers, (ii) `TransactionTemplate` is already a Spring-managed bean that's auto-configured by `JpaTransactionManager`, no extra `@Bean` needed, and (iii) the resulting code reads top-to-bottom: bind tenant, open transaction, do work.

### CYCLE2-HIGH-2 — SePay `code` vs `referenceCode` field semantics — RESOLVED

**Plan 03 changes (02B-03-credit-ledger-service-PLAN.md):**

- `BillingTopupService.applyWebhook(...)` signature reordered: `applyWebhook(long sepayTransactionId, String code, String referenceCode, String content, String transferType, long transferAmountVnd)`. `code` precedes `referenceCode` in the parameter list AND in the lookup logic.
- `extractIntentCode(...)` now reads SePay's `code` field FIRST (with `content` fallback) — NOT `referenceCode`.
- Updated Javadoc on `applyWebhook(...)` documents per SePay spec (developer.sepay.vn/en/sepay-webhooks/tich-hop-webhook): `code` is the SePay-detected payment code, `referenceCode` is the bank/SMS reference (audit metadata only). Idempotency key is the SePay `id` (transactionId) which UNIQUEs `credit_ledger_entry.ref_id`.
- `referenceCode` is still persisted onto `BillingTopupTransactionEntity.referenceCode` for SMS-trace forensics, but it is NOT used for intent lookup or idempotency.

**Plan 04 changes (02B-04-api-surface-PLAN.md):**

- `SepayWebhookController.receive(...)` now passes `payload.code()` BEFORE `payload.referenceCode()` when delegating to `billingTopupService.applyWebhook(...)`. Inline comment documents the SePay-spec rationale.
- `SepayWebhookPayload` record component order (already had `code` before `referenceCode`) requires no change — the bug was solely in the service signature and call site.

### CYCLE2-HIGH-3 — ArchUnit `.class` literal cross-package — RESOLVED

**Plan 06 changes (02B-06-verification-closure-PLAN.md):**

- Rule 2 (`credit_ledger_service_not_instantiated_outside_billing_service`) rewritten to use ArchUnit's string-FQN form: `noClasses().that().resideOutsideOfPackage("..core.billing.service..").should().dependOnClassesThat().haveFullyQualifiedName("com.zeromail.core.billing.service.CreditLedgerService")`. No `.class` literal needed; works across package boundaries against package-private types.
- Plan keeps the JUnit modifier-check assertion as an OPTIONAL tertiary guard, but explicitly notes it must live in a separate test class IN THE SAME PACKAGE (`com.zeromail.core.billing.service`) — never in `BillingDomainBoundaryArchTest` (which lives in the parent `com.zeromail.core.billing` package and would not compile).

**Plan 00 changes (02B-00-wave0-tests-PLAN.md):**

- Wave 0 RED scaffold for the same rule updated to use the FQN-string form so the scaffold compiles AS-IS once `@Disabled` is removed in Plan 06 (no `.class` literal anywhere in `BillingDomainBoundaryArchTest` at any point in the phase).

### CYCLE2-HIGH-4 — Wave 0 RED window — PARTIAL (process-accepted as phase-level convention)

**Plan 00 changes (02B-00-wave0-tests-PLAN.md):**

- New explicit phase-level convention block added to the frontmatter comment, accepting the RED window between Plan 00 and Plan 06 as a time-boxed, scoped-to-Phase-2B exception to the "every commit GREEN" rule. Reasons documented: (1) reflection-based scaffolds would force test rewrites instead of `@Disabled` flips; (2) Plan 06 owns the final GREEN gate; (3) intra-phase commits tagged with `[wave-0-red]` marker for CI/dashboard filtering; (4) Phases 2C onward revert to standard "every commit GREEN."
- This is the "explicit acceptance" path codex offered as an alternative to restructuring Plan 00 around reflection/string-FQN scaffolds. The `[wave-0-red]` commit-message tag operationalises codex's "or similar" suggestion for CI markers.


---

# Cycle 3 — Plan Reviews

**Reviewers:** codex (succeeded), opencode (provider error — skipped)
**Date:** 2026-05-06
**Plans state:** commit `d991d44` (after cycle-2 replan)

## Summary

| Reviewer | Overall Risk | HIGH | MEDIUM | LOW |
|----------|--------------|------|--------|-----|
| codex    | HIGH→MEDIUM after fixes | 3 new | 2 | 1 |
| opencode | (failed)     | —    | —      | —   |

## Cycle 2 HIGH Resolution Verdict (per codex)

| Cycle 2 HIGH | Verdict | Justification |
|---|---|---|
| 1. `applyWebhook` self-invocation bypasses `@Transactional` proxy | RESOLVED | Plan 03 now uses `TransactionTemplate.executeWithoutResult(...)` inside `ScopedValue.where(...)`, eliminating proxied self-invocation. Matches Spring's documented proxy limitation. |
| 2. SePay `code` / `referenceCode` semantics | RESOLVED | Plan 03/04 pass `code` before `referenceCode`; lookup tries `code` first with `content` fallback; `referenceCode` is audit metadata. Matches SePay docs. |
| 3. ArchUnit `.class` access on package-private type | RESOLVED | Plan 06 + Plan 00 use `haveFullyQualifiedName(String)` form. No `.class` literal across packages. |
| 4. Wave 0 RED compile window | PARTIALLY RESOLVED | RED window is now explicit and process-accepted, but not technically removed. Intermediate commits still fail `compileTestJava` by design. |

## Codex Review (Cycle 3)

> Source: `/tmp/gsd-review-codex-02B-cycle3.md`

**Strengths**

- The `TransactionTemplate` + `ScopedValue` pattern is now the right shape and matches an existing repo pattern in `GmailAccessGuard`.
- The SePay production flow now correctly treats `code` as intent lookup input and `referenceCode` as metadata.
- The webhook OpenAPI visibility, security-chain ordering, and `@Order(2)` placement are much cleaner than earlier revisions.
- The watchdog tenant-binding fix via `StaleReservation(tenantId, ...)` is a solid correction.
- The Plan 06 FQN-string ArchUnit rule avoids the package-private `.class` trap.

**Concerns**

- **HIGH (NEW):** Plan 01 declares `version` as `bigint` for new billing tables, but the repo's `AbstractAuditableEntity.version` is `Integer`, and existing migrations use `integer` / `int`. With `spring.jpa.hibernate.ddl-auto=validate`, this is likely to fail schema validation. Use `int` / `integer` for all new `version` columns.

- **HIGH (NEW):** `SEPAY_WEBHOOK_API_KEY:?message` is not a reliable Spring fail-fast pattern for a plain string secret. Spring's placeholder `:` syntax is a default value mechanism; missing env can bind the literal `?SEPAY_WEBHOOK_API_KEY must...`, and `@NotBlank` will pass. For SePay, unlike the Base64 refresh-token key, no semantic decoder will fail later. Use `${SEPAY_WEBHOOK_API_KEY}` with no default, or add explicit validation rejecting unresolved/default sentinel values.

- **HIGH (NEW):** `SepayWebhookMismatchAuditEventTest` still uses `code="MISMATCH"` / `referenceCode="MISMATCH"` semantics that conflict with the new service behavior. `MISMATCH` contains `I`, which is excluded by the Crockford regex, and the service ignores `referenceCode` for lookup. This test will log `unknown_code`, not `amount_mismatch`. Seed and send a valid 8-character code via `payload.code()` or content (e.g. `ABCD2345`).

- **MEDIUM:** `CreditLedgerEntryRepository` aggregate queries return `int`, but JPQL `SUM` over integer fields commonly returns `Long`. Use `long`/`Long` internally and range-check when constructing `CreditBalance`.

- **MEDIUM:** `FOR UPDATE SKIP LOCKED` in the watchdog stale scan is still overstated unless the select and processing share a transaction. ShedLock prevents concurrent workers, so correctness is fine, but the "two pods pick disjoint sets" claim is not really delivered by the current non-transactional tick.

- **LOW:** Several plan comments still say 16 or 7 Wave 0 tests after the count became 17 and API tests became 8. Will confuse execution and verification.

**Suggestions**

- Change all new billing `version` columns to `type: int` or `integer`, matching `AbstractAuditableEntity`.
- Replace the SePay env placeholder with a true unresolved-placeholder failure path, then keep the openapi/test overrides.
- Fix the mismatch-audit fixture to use a valid top-up code in `code` or `content`; do not rely on `referenceCode`.
- Remove the stale "`@Disabled` keeps check GREEN" wording from Plan 00, since the frontmatter now correctly says compile is RED by design.
- Either wrap the stale scan claim in a transaction or document that ShedLock is the actual multi-pod protection.

**Risk Assessment**

Overall risk: **HIGH until the three HIGH concerns are fixed; MEDIUM after fixes.** The revised architecture is close and the cycle-2 blockers are substantively addressed, but the `version` column mismatch can break Hibernate validation, the SePay secret placeholder can silently disable the intended fail-fast security property, and the mismatch audit test no longer matches the corrected SePay field semantics.

Sources checked: Spring transaction/proxy and placeholder docs via Context7, ShedLock docs via Context7, and SePay webhook docs at <https://developer.sepay.vn/en/sepay-webhooks/tich-hop-webhook>.

## OpenCode Review (Cycle 3)

> Provider returned error during invocation. OpenCode skipped this cycle.
> Stderr: `Error: "Provider returned error"` (model: `nemotron-3-super-free`)
> Likely cause: prompt size (~380KB including cycle 1+2 review history). Consider re-running with a leaner prompt if needed.

## Cycle 3 — Consolidated HIGH Concerns

1. **HIGH (NEW, codex):** `version` column type mismatch — billing migrations declare `bigint` but `AbstractAuditableEntity.version` is `Integer`; Hibernate `validate` will reject the schema.
2. **HIGH (NEW, codex):** `SEPAY_WEBHOOK_API_KEY:?…` placeholder isn't true Spring fail-fast for plain strings; `@NotBlank` may accept the literal default sentinel. Use `${SEPAY_WEBHOOK_API_KEY}` with no default.
3. **HIGH (NEW, codex):** `SepayWebhookMismatchAuditEventTest` seeds an invalid Crockford code (`MISMATCH` contains `I`) and relies on `referenceCode`, contradicting the cycle-2 field-order fix; will log `unknown_code` not `amount_mismatch`.
4. **HIGH (PARTIAL, carry-over from cycle 2):** Wave 0 `@Disabled` tests still committed RED between Plan 00 and Plan 06. Process-accepted, not technically resolved.

## Cycle 3 — Resolution Plan (Replan #3)

Date: 2026-05-06. Triggered by `/gsd-plan-phase 2B --reviews` third replan. All three new HIGHs resolved with mechanical edits; HIGH-4 left as previously documented (process-accepted phase-level convention scoped to Phase 2B; subsequent phases revert to "every commit GREEN").

### CYCLE3-HIGH-1 — `version` column `bigint` → `int` — RESOLVED

**Plan 01 changes (02B-01-schema-and-deps-PLAN.md):**

- All three new billing changesets (`014-credit-ledger-entry.yaml`, `015-credit-reservation.yaml`, `016-billing-topup-intent.yaml`) declare the `version` column as `type: int` (not `bigint`). Inline `# REVIEWS CYCLE-3 HIGH-1` comments explain the alignment with `AbstractAuditableEntity.version` (Java `Integer`) so Hibernate `ddl-auto=validate` accepts the schema.
- `must_haves.truths` adds an explicit assertion: every new billing changeset declares `version` as `int`, none `bigint`.
- The Task 1 verify automated grep adds a negative assertion `! grep -E "name: version,\s+type: bigint" ...` over all three changesets, so any drift in execution is caught by the verifier rather than by Hibernate at boot.

### CYCLE3-HIGH-2 — SePay webhook API key fail-fast — RESOLVED

**Plan 04 changes (02B-04-api-surface-PLAN.md):**

- `application.yml` `webhook-api-key` is now `${SEPAY_WEBHOOK_API_KEY}` (bare placeholder, NO `:?...` default). Inline comment documents that Spring's `${X:?msg}` syntax is a default-value mechanism for plain strings, not a true fail-fast operator. With the bare form, `PropertySourcesPlaceholderConfigurer` raises `IllegalArgumentException` at boot when the env is missing — the actual fail-fast behavior we want.
- The Task 3 verify automated grep asserts both presence (`webhook-api-key:\s*\$\{SEPAY_WEBHOOK_API_KEY\}\s*$`) and absence (`! grep -E 'SEPAY_WEBHOOK_API_KEY:\?'`) of the wrong form.

**Plan 05 changes (02B-05-worker-schedulers-PLAN.md):**

- Worker `application.yml` mirrors the api change: bare `${SEPAY_WEBHOOK_API_KEY}` with NO `:?` default. Frontmatter `must_haves.truths` updated to call out the asymmetry with `REFRESH_TOKEN_KEY_BASE64:?` (kept in `:?` form because the downstream Base64 decoder semantically catches a sentinel default; the SePay key has no such semantic catch, hence the bare-placeholder form).
- Task 1 verify automated grep asserts both presence (bare form) and absence (`:?` form).

**Plan 03 changes (02B-03-credit-ledger-service-PLAN.md):**

- `BillingProperties` record gains a defense-in-depth compact constructor that REJECTS sentinel-looking values for `sepay.webhookApiKey` — any value starting with `?` or `$`, or containing `must be set` / `must be supplied`, throws `IllegalStateException` with a clear remediation message. This is a SECOND layer behind the application.yml bare-placeholder fail-fast, in case some operator (or a wrong test profile) accidentally sets the env to a literal placeholder-default sentinel.
- `must_haves.truths` documents the sentinel-rejection contract; Task 2 verify automated grep asserts the literal `must be set` / `must be supplied` strings appear in `BillingProperties.java`.

The chosen approach (no-default placeholder + record-compact-ctor sentinel rejection) is option (a) from the codex suggestion list ("Use `${SEPAY_WEBHOOK_API_KEY}` with no default, or add explicit validation"). We did BOTH for defense-in-depth.

### CYCLE3-HIGH-3 — `SepayWebhookMismatchAuditEventTest` fixture — RESOLVED

**Plan 00 changes (02B-00-wave0-tests-PLAN.md):**

- File 13b (`SepayWebhookMismatchAuditEventTest.java`) fixture rewritten:
  - Seed `BillingTopupIntentEntity` with `code="ABCD2345"` (valid 8-char Crockford — alphabet `0-9 A-HJKMNP-TV-Z`, no I/L/O/U), `amountVnd=50000L`, `status=PENDING`.
  - Webhook payload sends `code="ABCD2345"` (matches seeded intent — drives lookup path), `transferAmount=99000L` (intentionally MISMATCHES intent's `amountVnd=50000`), `referenceCode="BANK-REF-XYZ"` (audit metadata only — must NOT influence lookup).
  - Test now correctly exercises the mismatch path: service finds the intent via `code`, computes amount mismatch, logs `event=sepay_webhook_amount_mismatch intentVnd=50000 actualVnd=99000`, and leaves the intent PENDING.
- Anti-regression: positive assertion `event=sepay_webhook_amount_mismatch` AND negative assertion that `event=sepay_unknown_code` is NOT in the log buffer.
- Task 2 verify automated grep adds presence-assertion of `code="ABCD2345"` AND absence-assertion of the old broken `"MISMATCH"` literal in the test source file.
- The corrected fixture is consistent with cycle-2's HIGH-2 resolution (`code` precedes `referenceCode`; `referenceCode` is audit metadata only).

### CYCLE3-HIGH-4 — Wave 0 RED window — PARTIAL (carry-over, no further action)

Per user direction: leave as-is. The phase-level RED-window convention block in Plan 00's frontmatter remains the authoritative process acceptance, scoped to Phase 2B only; Phases 2C onward revert to "every commit GREEN."

Cleanup applied: the prose under `Why @Disabled instead of compile-RED only` in Plan 00 Task 1 was rewritten to remove the contradictory claim that `@Disabled` keeps `./gradlew check` GREEN at compile time. The corrected wording explicitly says `@Disabled` only affects RUN time, not COMPILE time — matching the frontmatter's RED-by-design statement. Stale wording deleted.

### Cycle 3 MEDIUMs — sanity-checked, deferred

Per user direction the cycle-3 MEDIUMs (aggregate `int` vs `Long`, `FOR UPDATE SKIP LOCKED` watchdog framing, test-count drift 16/7 → 17/8) are NOT in scope of this replan. Recorded for sanity in the future-improvements list:

- `CreditLedgerEntryRepository` aggregate signatures should use `Long` internally to avoid `SUM` widening surprises; expose `int` only after explicit range validation.
- The watchdog `FOR UPDATE SKIP LOCKED` claim is cleanest if the select + processing share a transaction. ShedLock already provides multi-pod protection; the watchdog tick can be documented as relying on ShedLock for that property rather than overstating the SKIP-LOCKED semantics.
- A future pass should reconcile any remaining "16 / 7 Wave 0 tests" comments to the actual `17 / 8` counts (Plan 00 Task 2 already references 8; some scattered prose may still say 7).

