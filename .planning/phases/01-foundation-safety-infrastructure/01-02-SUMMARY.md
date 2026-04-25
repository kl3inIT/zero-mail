---
phase: 01-foundation-safety-infrastructure
plan: 02
status: complete
completed: 2026-04-25
---

# Plan 01-02 — Tenant Isolation Primitives

## What shipped

### Source (Task 1 — committed in 53396a5)

- `com.zeromail.core.tenant.TenantContext` — `ScopedValue<String> TENANT`, `currentOrThrow()`, `currentOptional()`. ScopedValue is the only tenant-binding primitive in the codebase; `ThreadLocal` is build-broken (D-A3).
- `com.zeromail.core.tenant.TenantPrincipal` — record `(userId, tenantId, email) implements Serializable`.
- `com.zeromail.core.tenant.ScopedValueTenantResolver` — Hibernate `CurrentTenantIdentifierResolver<String>` + Spring Boot `HibernatePropertiesCustomizer`, registers itself as `MULTI_TENANT_IDENTIFIER_RESOLVER` so JPQL/Criteria are auto-filtered by `@TenantId` (D-B1).
- `com.zeromail.core.tenant.concurrency.TenantAwareTaskScope` — `StructuredTaskScope.open()` wrapper; every `fork(Callable<T>)` re-binds tenant via `ScopedValue.where(TenantContext.TENANT, tenant).call(...)`. The blessed fan-out primitive (D-B3).
- Spring Modulith package boundaries: `tenant` (no allowed deps), `persistence` (allowed dep: `tenant`), `persistence.lowlevel` (allow-listed for native SQL).
- `@Modulithic(systemName = "zero-mail")` on `Application.java`.

### Tests + ArchUnit (Task 2 — committed in 3bf6fd7)

- `backend/api/src/test/.../ApplicationModulesTest` — `ApplicationModules.of(Application.class).verify()` on the api module (kept out of `backend/core` to avoid reversed module dep — BLOCKER-2 locked).
- `backend/core/src/test/.../TenantContextTest` — unbound throws, `ScopedValue.where(...)` returns the bound value.
- `backend/core/src/test/.../TenantAwareTaskScopeTest` — 10 forked subtasks all observe parent tenant `tenant-A`.
- `backend/core/src/test/.../arch/TenantIsolationArchTests` — three rules:
  - `no_threadlocal` — fails on `ThreadLocal` import in `..api..`, `..worker..`, `..core..`.
  - `fanout_via_helper` — fails on `Thread.ofVirtual()`, `CompletableFuture.supplyAsync`, `CompletableFuture.runAsync` outside `..core.tenant.concurrency..`.
  - `no_native_sql` — fails on `EntityManager.createNativeQuery` outside `..core.persistence.lowlevel..`. Uses `DescribedPredicate` (callMethodWhere is not a functional interface in ArchUnit 1.3).

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.tenant.*" --tests "com.zeromail.core.arch.*"` → BUILD SUCCESSFUL.
- `./gradlew :backend:api:test --tests "com.zeromail.api.ApplicationModulesTest"` → BUILD SUCCESSFUL.
- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava` → BUILD SUCCESSFUL.
- Grep `ThreadLocal` and `Thread.ofVirtual` across `backend/**/src/main/**/*.java` → zero matches.
- `test -f backend/api/src/test/java/com/zeromail/api/ApplicationModulesTest.java` → PASS.
- `test ! -f backend/core/src/test/java/com/zeromail/core/ApplicationModulesTest.java` → PASS (Modulith test is in api, not core).

## Requirements satisfied

- **FND-01** — Tenant context propagation primitive (ScopedValue + TenantAwareTaskScope) in place.
- **FND-02** — ArchUnit ban on ThreadLocal + raw virtual-thread fan-out.
- **FND-05** — Backing primitives ready; the concurrent leak test that exercises authenticated request paths is owned by plan 01-05.

## Decisions implemented

- D-A3 — ScopedValue replaces ThreadLocal across api/worker/core.
- D-B1 — Hibernate `@TenantId` discriminator auto-filter wired via `ScopedValueTenantResolver`.
- D-B2 — Native SQL is allow-listed only inside `..core.persistence.lowlevel..`.
- D-B3 — `TenantAwareTaskScope` is the blessed fan-out primitive; everything else is build-broken.

## Notes for downstream plans

- Plan 01-04 entities should add `@TenantId String tenantId` and rely on the resolver (no manual `WHERE tenant_id = ?` in JPQL).
- Plan 01-05's `TenantBindingFilter` should bind `TenantContext.TENANT` via `ScopedValue.where(...).run(...)` around the filter chain — never `set()` (ScopedValue is immutable).
- Plan 01-05's FND-05 leak test will exercise the ArchUnit-protected primitive end-to-end through authenticated traffic.

## Files modified

See PLAN.md `files_modified` — all 12 files present and committed across 53396a5 (source) and 3bf6fd7 (tests).
