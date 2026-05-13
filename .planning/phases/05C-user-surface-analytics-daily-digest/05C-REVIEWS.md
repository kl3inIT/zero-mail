---
phase: 5C
reviewers: [codex, opencode]
reviewed_at: 2026-05-13T13:59:31Z
plans_reviewed:
  - 05C-01-PLAN.md
  - 05C-02-PLAN.md
  - 05C-03-PLAN.md
  - 05C-04-PLAN.md
---

# Cross-AI Plan Review — Phase 5C

## Codex Review

## Summary

Overall, the phase plan is strong in decomposition and test intent, but it is not execution-ready yet. The wave model is sensible and the privacy/idempotency themes are well-covered conceptually, but several concrete plan-level mismatches would either fail at implementation time or silently violate requirements: JPA mapping conflicts in Plan 01, missing preference backfill, digest window anchoring, scheduler transaction/idempotency ordering, tenant context in worker execution, and a few broken SQL/model assumptions.

## Strengths

- Clear dependency ordering: schema → analytics read side → digest backend → frontend is the right wave shape.
- Good privacy posture: sender email is explicitly allowed only in owner-facing UI/email, with log-scrub tests planned.
- Good deterministic analytics choices: fixed windows, fixed time-saved weights, alphabetical tie-breaks.
- Good channel-boundary intent: `DigestPayload` channel-free, Resend behind an adapter, ArchUnit boundary planned.
- Good frontend acceptance coverage: empty state, 320px, URL-driven window chips, optimistic rollback, i18n parity.

## Concerns

- **HIGH: `NotificationPreferenceEntity` mapping conflicts with the schema.**
  Plan 01 Task 2 says it extends `AbstractTenantOwnedEntity` and uses composite PK `(tenant_id, channel)`, but the existing base class inherits an `id @Id` column. The planned table has no `id`. Either add an `id` PK plus unique `(tenant_id, channel)`, or do not extend `AbstractTenantOwnedEntity` and map explicit `@Id @TenantId tenantId` like `MailMessageObservedEntity`.

- **HIGH: `ChannelType.EMAIL` persistence is inconsistent.**
  Plan 01 says `id() = "email"` but also says use `@Enumerated(EnumType.STRING)`. JPA will store `EMAIL`, while Liquibase indexes/queries use `channel = 'email'`. Pick one: use uppercase DB values everywhere, or add an `AttributeConverter<ChannelType,String>`.

- **HIGH: existing tenants will not get notification preferences.**
  Plan 01 backfills `tenants.time_zone`, but does not insert `notification_preference` rows for existing tenants. Plan 03 scheduler only scans `notification_preference`, so existing connected tenants receive no digest. Add a migration `INSERT ... SELECT tenants.id, 'email', true, 20 ... ON CONFLICT DO NOTHING`.

- **HIGH: sender extraction is aimed at the wrong ingestion point.**
  Plan 01 references `PubSubIngestionService`, but the actual observed-message write is in `GmailDeliveryProcessingService` / `MailMessageObservedRepository.insertObservedIfAbsent`. That Gmail metadata request currently sets fields without payload headers, so `sender_email` will stay null unless the plan adds metadata header retrieval for `From`.

- **HIGH: digest cannot satisfy the locked "closed prior 24h" window with the Plan 02 service shape.**
  Plan 02 exposes `summarize(UUID, Duration)` and computes `Instant.now().minus(window)` with no upper bound. Plan 03 needs `[sendMoment - 24h, sendMoment)`. Add `summarize(UUID tenantId, Instant windowStartInclusive, Instant windowEndExclusive)` and make web windows call it with `now`.

- **HIGH: scheduler idempotency write order is unsafe.**
  Plan 03 Task 3 wraps claim, compose, external Resend call, and status update in one `@Transactional` method. That defeats D-10's "row exists before channel side effect" guarantee. The claim transaction must commit before sending; `markSent` / `markFailed` must be separate transactions.

- **HIGH: worker tenant context is missing.**
  The digest scheduler processes tenant-owned JPA entities and likely user lookup, but the plan does not bind `TenantContext` per tenant. Wrap each tenant dispatch in `ScopedValue.where(TenantContext.TENANT, tenantId.toString())`.

- **HIGH: scheduler time source can drift.**
  Due-tenant SQL uses Postgres `now()`, while digest day/window uses Java `currentInstant`. Near hour boundaries or with DB/JVM clock skew, due selection and `digest_day_local` can disagree. Use one reference instant, passed into SQL as a parameter, or return DB-computed local date/hour from the due query.

- **MEDIUM: `claimPending` returns `boolean`, but later steps need a delivery ID.**
  Plan 03 calls `markSent(UUID deliveryId, ...)`, but `claimPending` only returns true/false. Return a claim record containing `deliveryId`, `tenantId`, `digestDayLocal`, attempt count, and channel.

- **MEDIUM: transient digest retry is promised but not modeled.**
  Plan 03 classifies 429/5xx/network as transient, but a `FAILED` row with unique `(tenant_id, digest_day_local)` prevents a later insert. Add `next_attempt_at`/retry state, or explicitly state v1 is no-retry after provider failure.

- **MEDIUM: digest locale query is wrong.**
  Plan 03 due SQL selects `t.preferred_language`, but the existing column is `users.preferred_language`. Join `users` or add an account-domain lookup service.

- **MEDIUM: `digest_delivery` schema lacks `external_id`.**
  Plan 03 `markSent(... externalId)` and logs provider IDs, but Plan 01 changeset 035 has no provider/external id column. Add `external_ref` or remove the parameter.

- **MEDIUM: Resend ArchUnit test may pass vacuously.**
  Putting `ResendBoundaryArchTest` under `backend/core:test` likely cannot scan `backend/worker` classes in a multi-project Gradle build. Move it to a root/architecture test that imports all modules, or add a CI grep gate.

- **MEDIUM: top-sender and volume queries may include SENT mail.**
  Current ingestion stores messages with `INBOX` or `SENT`. Plan 02 Q1/Q3 count all `mail_message_observed` rows. If analytics means incoming triaged mail, filter by `label_ids` containing `INBOX`.

- **LOW: Resend version and vendor rationale need refresh.**
  The plan pins `resend-java 4.13.0`; current upstream appears newer. Either use the latest stable compatible version or document why 4.13.0 is intentionally pinned.

## Suggestions

1. Revise Plan 01 before execution: fix `NotificationPreferenceEntity` PK mapping, enum storage, existing-tenant backfill, and the actual Gmail history sender extraction path.
2. Change analytics query API to accept a concrete `TimeWindow(startInclusive, endExclusive)`; reuse it for both web presets and digest.
3. Split digest dispatch into separate transactional units: claim-and-commit, send outside transaction, mark outcome.
4. Bind `TenantContext` inside the scheduler loop before touching tenant-owned repositories.
5. Make the due query and Java date math share the same reference instant.
6. Add retry semantics or remove transient-retry claims from Plan 03.
7. Move cross-module ArchUnit boundaries to a place that actually sees all module classes.
8. Add a test fixture proving SENT messages do not pollute analytics, or explicitly accept that they count.

## Risk Assessment

**Overall risk: MEDIUM-HIGH.** The product design is coherent, but the implementation plan has several hard correctness issues in schema/JPA mapping, time-window semantics, scheduler transaction boundaries, and tenant context. Fixing those now is much cheaper than discovering them during execution. Once those are corrected, the phase risk drops to medium: mainly operational email delivery and frontend/backend integration polish.

Sources checked for library-sensitive assumptions: Spring scheduling cron format, ShedLock lock behavior, PostgreSQL `AT TIME ZONE`, and Resend Java SDK docs/GitHub.

---

## OpenCode Review

## Summary

These are **well-structured, internally consistent plans** with strong privacy enforcement, a clean dependency chain (Wave 1→4), and thorough testing coverage. The schema, endpoint, dispatcher, and frontend work are sensibly modularized, and the channel-agnostic notification layer is a forward-looking design choice. **However, the plans contain one architecturally critical defect** (transaction boundary causing double-sends on partial failure) and **one factual error** (D-07's missed-hour claim contradicts the D-06 query design). Several Medium-severity gaps (Modulith dependencies, TenantContext in workers, textual inconsistencies) need resolution before execution. The risk is **HIGH** if the transaction boundary issue ships as-written, but **MANAGEABLE** with the corrections suggested below.

## Strengths

- **Four-layer digest idempotency**: ShedLock cluster lock → Postgres UNIQUE constraint → Resend Idempotency-Key header → stuck-PENDING reaper. Each layer operates independently; any two can fail and the system still produces ≤1 send per (tenant, day). This is the correct architectural approach for the key reliability concern.
- **Privacy enforcement is comprehensive**: ArchUnit content-ban tests for analytics SQL (no body/prompt/completion columns), privacy-sweep tests with sentinel addresses for both analytics endpoint and digest jobs, logging invariants checked at compile time *and* runtime. The §0 sender_email skip gate (`WHERE sender_email IS NOT NULL`) correctly handles pre-fix historical rows.
- **DST correctness is solid**: Postgres-side `AT TIME ZONE` for the hour-match query (IANA tzdata-shipped, DST-safe) + Java `ZonedDateTime` for window math. The v1 Vietnam default (UTC+7, DST-free) minimizes surface area for clock bugs. The `digest_day_local = ZonedDateTime.now(zone).toLocalDate()` race-closure rule (same-calendar-day ticks always collide on UNIQUE) is correctly specified.
- **Clean dependency chain**: Wave 1 (schema) → Wave 2 (read-side) → Wave 3 (write-side + dispatcher) → Wave 4 (frontend). No circularities. Each wave's outputs are contract-tested by downstream waves.
- **i18n lock-step enforcement is layered**: `DigestMessageSourceParityTest` for email templates + `pnpm i18n:check` (strict mode via `EN_SCAN_FILES`) for web UI. Fail-loud MessageSource configuration catches missing keys at render time, not in production.
- **Plans reference existing code patterns** extensively and correctly (`AuditLogQueryService` shape, `BillingIntentExpirySweeper` skeleton, `TriagePrivacySweepTest` template, Phase 5A optimistic mutation recipe).

## Concerns

### HIGH

**H1 — D-07 factual error: missed-hour digest recovery is impossible under D-06**

*Location*: Plan 03, D-07 (CONTEXT.md decisions section)

The claim query (D-06) uses `EXTRACT(HOUR FROM (now() AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local`. This is an **exact-hour match**. D-07 claims "If a tenant's digest_send_hour_local = 20 and the worker is down 19:30–20:30 local, the 21:00 tick still claims the (tenant, digest-day) row and sends." **This is incorrect.** At 21:00 local, `EXTRACT(HOUR) = 21`, `digest_send_hour_local = 20` → `21 = 20` → **FALSE**. The tenant is not claimed.

**Impact**: If the worker misses a tenant's exact send-hour tick (due to downtime, VM restart, ShedLock contention), that tenant's digest for that day is skipped entirely with no catch-up. The "worst-case lateness = ~59 minutes" statement in D-07 only applies to *delay within the same clock hour*, not to *missed-hour recovery*. D-07 is contradictory with D-06.

**H2 — Single-transaction fanout loop causes double-sends on partial failure**

*Location*: Plan 03, Task 3 (`DigestDispatchScheduler`)

The `dispatch()` method is annotated `@Transactional(propagation = Propagation.REQUIRED)` and iterates over ALL due tenants in a single loop. If tenant N's Resend call throws, the **entire** transaction rolls back — including the `claimPending` INSERTs and `markSent` UPDATEs for tenants 1..N-1. Those tenants already received the email (Resend HTTP call completed outside transaction control). On the next cron tick, no SENT row exists (rolled back), so `claimPending` succeeds, and tenants 1..N-1 receive a **second** digest. Each tenant's dispatch MUST be in its own transaction.

### MEDIUM

**M1 — `core.notification` Modulith dependencies omit `core.analytics`**

`DigestComposer` (in `core.notification.usecases`) calls `AnalyticsSummaryQueryService.summarize()` in `core.analytics`. Without `"analytics"` in `allowedDependencies`, Spring Modulith's verification test will fail.

**M2 — TenantContext/ScopedValue not addressed in worker scheduler**

The scheduler runs outside any HTTP context. `AbstractTenantOwnedEntity` likely uses `@TenantId` which reads from `TenantContext` (typically a `ScopedValue`). When `DigestDeliveryService.claimPending()` saves a `DigestDeliveryEntity` via JPA, the `@TenantId` listener needs an active tenant context — otherwise it either NPEs, defaults to an unset value, or (worst case) silently writes the wrong tenant_id. Wrap each tenant's dispatch in `ScopedValue.where(TenantContext.TENANT, tenantId).call(...)`.

**M3 — Textual inconsistency: tenantId parameter vs TenantContext in query service**

Plan 02 Task 1 behavior says `summarize(UUID tenantId, Duration window)` — explicit parameter. T-05C-04 says "Every query's first JDBC param is `TenantContext.currentOrThrow()`". These contradict. Resolve to avoid executor confusion (worker has no HTTP context).

**M4 — Null user email in EmailNotificationChannel not handled**

If `userRepository.findEmailByTenantId(tenantId)` returns `null` (zombie account), the code NPEs or sends malformed request to Resend. Guard with explicit check + `markFailed("no_email_found")`.

**M5 — `core.analytics` Modulith module location is ambiguous**

Plan 02 unambiguously creates the new module, but CONTEXT.md lists "core.analytics module vs inline in core.triage.projection" as Claude's Discretion. Pick one and make all plans consistent (ties to M1).

**M6 — Subject-line timing leak (borderline)**

Subject likely contains `digest_day_local` date — fine. But send-hour timing could reveal tenant's timezone to an intercepting attacker. Borderline v2 hardening; flag for awareness.

### LOW

**L1 — Empty-string `?window=` parameter may bypass default**

Spring Boot's default behavior for empty query parameters depends on `spring.mvc.default-empty-strings` (false in Spring 6.x default). If `?window=` is sent, the parameter is present but empty and may not fall through to `defaultValue`. Handle explicitly.

**L2 — Sequential fan-out loop may not scale past ~200 tenants**

Resend free tier rate limits (~2-5 req/s) mean 200 sequential calls take 40-100 seconds. Not actionable for v1 (<100 tenant Vietnam beta) but document as known scaling ceiling for Phase 6.

**L3 — Liquibase changeset sequence numbering may conflict**

Sequential numbering assumes no other branch introduced changesets 032–035. Standard monorepo risk.

**L4 — CTA URL construction uses string concatenation**

If `baseAppUrl` ends with `/`, `URI.create(baseAppUrl + "/analytics?...")` produces `//analytics`. Use `URI.resolve()` or strip trailing slash.

## Suggestions

### Critical (address before execution)

**S1 — Per-tenant transaction boundaries for digest scheduler**

Replace the single `@Transactional` fanout with per-tenant transactions. The outer `scheduledDispatch()` is NOT `@Transactional`; inner `dispatchOne(tenantId, ...)` is `@Transactional(propagation = REQUIRES_NEW)`. Catch exceptions in the loop so one tenant's failure doesn't abort the rest.

**S2 — Fix D-07 / clarify digest delivery recovery semantics**

Either (a) accept single-hour-only and document "missed exact-hour tick = day's digest skipped, no retroactive catch-up" (matches the architecture), or (b) modify the claim query to also accept tenants where `digest_day_local < today()` and no SENT row exists for catch-up. Recommend (a) for v1.

**S3 — Wrap per-tenant dispatch in ScopedValue for TenantContext**

```java
ScopedValue.where(TenantContext.TENANT, tenant.tenantId())
    .call(() -> dispatchOne(tenant.tenantId(), ...));
```

### High Priority

**S4 — Fix Modulith dependency declaration**

`allowedDependencies = {"analytics", "tenant", "shared.persistence", "shared.lang"}`.

**S5 — Resolve tenantId parameter vs TenantContext inconsistency**

Make the plan text explicit: service NEVER reads TenantContext internally; controller extracts `tenantId` and passes explicitly. Update T-05C-04 to match.

**S6 — Guard against null user email in EmailNotificationChannel**

Return `PermanentFailure("no_email_found")` instead of NPE / malformed request.

### Medium Priority

**S7 — Add empty-string window param guard**

Handle `?window=` at controller level with clear error.

**S8 — Verify Resend SDK compatibility with Boot 4 / Java 25**

Add a Context7 check before adding to `libs.versions.toml`. Verify Java 25 bytecode + transitive deps (no javax / older Jackson).

## Risk Assessment

| Factor | Rating | Rationale |
|--------|--------|-----------|
| Privacy compliance | **LOW** | ArchUnit + sweep tests + content-ban tests are comprehensive. |
| Idempotency integrity | **MEDIUM** | Four-layer approach is strong, but H2 introduces a legitimate double-send risk. With S1 → LOW. |
| Cross-tenant isolation | **LOW** | Tenant-scoped queries; Modulith boundaries. |
| Data correctness (analytics) | **LOW** | Formula-driven with fixture tests. |
| Operational reliability | **MEDIUM** | H1 (missed-hour silent loss) and H2 (partial-failure double sends) are fixable. |
| Execution complexity | **MEDIUM** | 4 plans, ~80 files, 2 new Modulith modules, 1 new SDK, OpenAPI regen. |

**Overall risk level: MEDIUM → LOW after applying S1, S2, S3, S4.**

The plans are well-crafted in design philosophy, but the transaction boundary issue (H2) is a show-stopper that must be resolved before execution. The D-07 factual error (H1) is less critical but creates an inaccurate operational expectation. The TenantContext gap (M2) and Modulith dependency omission (M1) will cause build or runtime failures if not addressed.

---

## Consensus Summary

Both reviewers (Codex and OpenCode) classify the plans as well-decomposed and privacy-conscious but **not execution-ready** without targeted fixes. They independently surface the same most-critical issue (single-transaction scheduler causing double-sends) and overlap on TenantContext binding and Modulith dependency declarations.

### Agreed Strengths

- Wave-based dependency ordering (schema → analytics → digest → frontend) is sound.
- Privacy posture is strong: ArchUnit content-ban + sender_email logging sweep + sentinel tests.
- Channel-agnostic abstraction (`DigestPayload` channel-free + `NotificationChannel` interface) is correctly forward-looking.
- Reuse of existing project patterns (`AuditLogQueryService`, `BillingIntentExpirySweeper`, `TriagePrivacySweepTest`, Phase 5A optimistic-mutation recipe) is exemplary.
- Frontend acceptance coverage (empty states, 320px responsive, URL-driven window chips, vi+en parity) is thorough.

### Agreed Concerns (raised by both reviewers — highest priority)

1. **HIGH: Single-transaction fanout in DigestDispatchScheduler causes double-sends.** Each tenant's claim → dispatch → mark must be in its own transaction (Codex C6 = OpenCode H2). **Fix S1.**
2. **HIGH: Worker scheduler lacks TenantContext / ScopedValue binding.** JPA `@TenantId` auto-population needs an active tenant context per tenant dispatch (Codex C7 = OpenCode M2 escalated to HIGH-equivalent). **Fix S3.**

### Divergent Views (worth investigating)

- **Codex flags 5 additional HIGHs that OpenCode classifies as lower / missed:**
  - JPA entity PK mapping conflict (`AbstractTenantOwnedEntity` adds `id` column, but planned table has none).
  - `ChannelType.EMAIL` enum-to-DB mismatch (`@Enumerated(STRING)` stores `EMAIL` but Liquibase queries `'email'`).
  - Existing tenants do not get `notification_preference` backfill — Plan 01 only backfills `tenants.time_zone`.
  - Sender extraction wired to `PubSubIngestionService` but actual writer is `GmailDeliveryProcessingService`; metadata-only Gmail GET doesn't return `From` header without explicit format=METADATA + metadataHeaders param.
  - Analytics service shape `summarize(UUID, Duration)` can't express the closed `[sendMoment-24h, sendMoment)` digest window; needs explicit start+end instants.
- **OpenCode flags D-07 contradiction (HIGH H1)** that Codex missed: D-07's "missed-hour recovery" claim is impossible under D-06's exact-hour match query.
- **Codex flags scheduler time-source drift (HIGH C8)** — Postgres `now()` for the due-tenant SQL vs Java `currentInstant` for `digest_day_local`; OpenCode does not raise.
- **Codex flags `claimPending` return type mismatch** with downstream `markSent(UUID deliveryId, ...)` — `boolean` cannot supply the delivery id; OpenCode does not raise.
- **Codex flags Q1/Q3 may include SENT mail** (no `INBOX` label filter) — OpenCode does not raise; product-definition question worth resolving.
- **OpenCode flags subject-line timing leak (M6)** and empty-string `?window=` parameter (L1) that Codex did not surface.

### Recommended Next Step

Run `/gsd-plan-phase 5C --reviews` to incorporate this feedback. Priority order:

1. **Block-on-execute:** S1 (per-tenant transactions), S3 (ScopedValue tenant binding), Codex C1 (JPA mapping), Codex C2 (enum DB value), Codex C3 (tenant backfill), Codex C4 (real ingestion writer + Gmail metadata header), Codex C5 (window start+end), Codex C8 (single reference instant), S4 (Modulith `analytics` dependency).
2. **Clarify-before-execute:** S2 (D-07 missed-hour policy lock), M3/S5 (tenantId parameter vs TenantContext text alignment), Codex's `INBOX` filter question, `claimPending` return type contract, `digest_delivery.external_id` column add.
3. **Defer / document:** L1–L4 (empty-string param guard, scale ceiling, changeset numbering, URI build), M6 (subject timing leak).
