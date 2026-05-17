---
phase: 5C
review_cycle: 2
reviewers: [codex, opencode]
reviewed_at: 2026-05-13T14:36:33Z
plans_reviewed:
  - 05C-01-PLAN.md
  - 05C-02-PLAN.md
  - 05C-03-PLAN.md
  - 05C-04-PLAN.md
previous_cycle:
  reviewed_at: 2026-05-13T13:59:31Z
  high_count: 9
  high_ids: [Codex C1, Codex C2, Codex C3, Codex C4, Codex C5, Codex C6, Codex C7, Codex C8, OpenCode H1, OpenCode H2]
  replanned_in: 1a57f01
---

# Cross-AI Plan Review — Phase 5C (Cycle 2 — Replan Verification)

This cycle re-reviews the plans after replan commit `1a57f01` ("docs(05C): replan with cross-AI review feedback (--reviews mode)") to verify resolution of the 9 HIGHs raised in cycle 1.

## Codex Review

**Summary**

The replan is much stronger than the prior cycle: it addresses most schema, enum, backfill, sender extraction, TenantContext, and idempotency concerns directly, with good test intent. It is close, but I would not execute it unchanged. There are still a few plan-level contradictions that could send implementers down the wrong path, especially around digest transaction boundaries, digest window anchoring, Liquibase defaults, and cross-module ArchUnit/Modulith placement.

**Prior HIGH Resolution Audit**

| Prior HIGH | Status | Assessment |
|---|---:|---|
| Codex C1: `NotificationPreferenceEntity` PK conflict | FULLY RESOLVED | Plan 01 now uses inherited UUID `id` PK plus unique `(tenant_id, channel)`. |
| Codex C2: `ChannelType.EMAIL` DB mismatch | PARTIALLY RESOLVED | Main design is fixed to uppercase `EMAIL`, but Plan 01 Task 3 still says an OAuth test should assert `channel='email'`. Remove that lowercase reference. |
| Codex C3: existing tenants lack preferences | FULLY RESOLVED | Changeset 037 backfills `notification_preference` for all tenants with `ON CONFLICT DO NOTHING`. |
| Codex C4: sender extraction wrong writer | FULLY RESOLVED | Plan 01 now targets `GmailDeliveryProcessingService` and `insertObservedIfAbsent`, with Gmail `METADATA` + `From`. |
| Codex C5: analytics service cannot express closed digest window | FULLY RESOLVED | Plan 02 adds `TimeWindow(startInclusive, endExclusive)` and Plan 03 reuses it. |
| Codex C6: digest idempotency write order unsafe | PARTIALLY RESOLVED | Claim/send/mark split is specified, but Plan 03 later says to put `@Transactional(REQUIRES_NEW)` on `dispatchOne`, which conflicts with “send outside any DB transaction.” |
| Codex C7: worker TenantContext missing | FULLY RESOLVED | Plan 03 wraps per-tenant dispatch and reaper rows in `ScopedValue.where(TenantContext.TENANT, ...)`. |
| Codex C8: DB/JVM time-source drift | FULLY RESOLVED | Due SQL and Java `digestDayLocal` now share one injected `referenceInstant`. |
| OpenCode H1: D-07 missed-hour contradiction | FULLY RESOLVED | CONTEXT now explicitly locks “missed exact hour = skipped, no catch-up,” and Plan 04 surfaces it in settings copy. |
| OpenCode H2: single-transaction fanout double-sends | FULLY RESOLVED for original issue | Outer scheduler is no longer transactional and per-tenant failures are isolated. See C6/new concern for the remaining transaction wording conflict. |

**New Strengths**

- The plan now has a clear schema-first dependency chain and resolves the major JPA/DB mismatch.
- Analytics now uses explicit closed-open windows, deterministic tie-breaking, tenant-scoped JDBC, and privacy sweeps.
- The digest design has the right reliability layers: ShedLock, DB unique key, Resend idempotency header, and stuck-PENDING reaper.
- Resend assumptions are mostly sound: current docs confirm `CreateEmailOptions` supports HTML/text, `addHeader`, `addTag`, `CreateEmailResponse.getId()`, and `ResendException.getStatusCode()`.
- Spring cron `0 5 * * * *` is valid six-field Spring syntax, and ShedLock usage with `LockAssert.assertLocked()` matches current docs.

**New Concerns**

- **HIGH: Digest window is anchored to cron execution time, not the configured send-hour boundary.**  
  Plan 03 uses `sendMoment = referenceInstant`; with cron at `20:05`, the digest window becomes `[yesterday 20:05, today 20:05)`, but SPEC requires `[yesterday 20:00, today 20:00)` for hour `20`. Compute `scheduledLocalDateTime = localDate.atTime(digestSendHourLocal, 0)` and convert that to `Instant`; use that as the digest window end.

- **HIGH: Plan 03 transaction instructions are internally contradictory.**  
  The must-have says Resend is called outside any DB transaction, but Task 3 action says to put `@Transactional(REQUIRES_NEW)` on `dispatchOne`. `dispatchOne` should not be transactional. Keep `REQUIRES_NEW` only on `claimPending`, `markSent`, and `markFailed`.

- **HIGH: `tenants.time_zone` Liquibase default is likely wrong.**  
  `defaultValueComputed: 'Asia/Ho_Chi_Minh'` is not the right shape for a string literal unless the generated SQL includes quoted text. Use `defaultValue: Asia/Ho_Chi_Minh` or an explicitly quoted computed expression.

- **MEDIUM: Modulith dependency direction is still muddy.**  
  Adding `"account"` to `core.notification` does not help if `core.account` imports notification services for deletion cascade. Expose notification use cases via a named interface and update the account module’s allowed dependencies, or use an account-deleted event.

- **MEDIUM: `ResendBoundaryArchTest` location/commands are inconsistent.**  
  The top-level file list says worker, but Task 1 and verification still reference `backend/core`. Put it only in `backend/worker` and run it with `:backend:worker:test`.

- **MEDIUM: Window-param behavior contradicts itself.**  
  Plan 02 alternates between “`?window=` is 400” and “`?window=` defaults to 7d.” Plan 04 says invalid values are canonicalized to 7d, while the threat model says invalid values hit backend 400. Pick one contract and align tests.

- **MEDIUM: Null recipient handling is contradictory.**  
  `EmailNotificationChannel` says first line `Objects.requireNonNull(recipientAddress)`, but tests expect `dispatch(payload, null)` to return `PermanentFailure("no_email_found")`. Implement an explicit null-or-blank guard instead.

- **MEDIUM: Locale/user lookup may duplicate tenants.**  
  `JOIN users u ON u.tenant_id = t.id` can produce duplicate due rows if a tenant ever has multiple users. Query the owner/primary user, enforce one user per tenant, or use a deterministic `LIMIT 1` lookup. Also define fallback for null/unsupported `preferred_language`.

- **LOW: Resend version policy is inconsistent.**  
  Interfaces say “pin current stable after Context7,” but behavior/done hardcode `4.13.0`. Either intentionally pin `4.13.0` with dependencyInsight evidence or leave the plan as “latest verified stable.”

**Suggestions**

1. Amend Plan 03 so the per-tenant worker is non-transactional; only claim/mark methods open `REQUIRES_NEW` transactions.
2. Change digest composition to use the configured local hour boundary, not raw `referenceInstant`, for the `[start, end)` analytics window.
3. Fix Liquibase string defaults and the stray lowercase `channel='email'` assertion.
4. Move `ResendBoundaryArchTest` fully to worker and update VALIDATION.md accordingly.
5. Normalize backend/frontend window semantics in one paragraph and make every test match it.
6. Add account-module dependency updates and the actual user repository file/method to the modified-file list.

**Risk Assessment**

Current risk is **HIGH until the plan text is amended**, mainly because the digest window boundary and transaction annotation contradiction are easy to implement incorrectly. After those fixes, the plan drops to **MEDIUM**: the remaining risk is mostly integration polish around Modulith boundaries, Resend dependency/version verification, and frontend contract consistency.

---

## OpenCode Review

## Cross-AI Plan Review — Phase 5C (Replan Cycle)

### Summary

The replan cycle addresses every prior HIGH concern with surgical precision. Plans 01–04 now include explicit Codex C1–C8 and OpenCode H1/H2 mitigations, each with dedicated test cases and architectural locks. The per-tenant `REQUIRES_NEW` transactional split, `ScopedValue` TenantContext binding, single-reference-instant clock fix, and D-07 missed-hour policy correction are correctly specified and consistently threaded through all dependent plans. The overall risk drops from MEDIUM-HIGH to **LOW-MEDIUM**, with remaining MEDIUM items being implementation-phase "verify this works" rather than architectural gaps.

---

### Prior HIGH Resolution Audit

| ID | Concern | Status | Evidence |
|----|---------|--------|----------|
| **C1** | `NotificationPreferenceEntity` PK conflicts with `AbstractTenantOwnedEntity` | **FULLY RESOLVED** | Plan 01 changeset 034: single UUID `id` PK + UNIQUE `(tenant_id, channel)`. Entity extends `AbstractTenantOwnedEntity` with inherited `@Id`, class-level `@UniqueConstraint`. `@IdClass` and `NotificationPreferenceId` explicitly banned. |
| **C2** | `ChannelType.EMAIL` enum-to-DB mismatch | **FULLY RESOLVED** | Plan 01: `ChannelType.EMAIL.id()` = `"EMAIL"` (uppercase). `@Enumerated(STRING)` stores `EMAIL`. Liquibase partial index `channel = 'EMAIL'`. Backfill `'EMAIL'` literal. |
| **C3** | Existing tenants get no notification_preference row | **FULLY RESOLVED** | Plan 01 changeset 037: `INSERT ... SELECT ... FROM tenants ON CONFLICT (tenant_id, channel) DO NOTHING`. `NotificationPreferenceBackfillTest` verifies idempotency. |
| **C4** | Sender extraction wired to wrong ingestion point | **FULLY RESOLVED** | Plan 01 Task 2: fixes `GmailDeliveryProcessingService` (the REAL writer). Changes `format=MINIMAL` → `format=METADATA` + `metadataHeaders=["From"]`. `GmailDeliveryProcessingSenderEmailTest` verifies. |
| **C5** | Analytics service can't express closed `[sendMoment-24h, sendMoment)` | **FULLY RESOLVED** | Plan 02: `TimeWindow(startInclusive, endExclusive)` record. Controller: `TimeWindow.endingAt(now, duration)`. Digest: `TimeWindow.between(sendMoment-24h, sendMoment)`. Closed-window boundary test. |
| **C6** | Single-transaction fanout causes double-sends on partial failure | **FULLY RESOLVED** | Plan 03: `scheduledDispatch()` NOT `@Transactional`. Per-tenant `@Transactional(REQUIRES_NEW)` via collaborator bean. Three separate transactional units: claim INSERT → Resend call (no tx) → markSent/markFailed. Isolation test proves tenant B failure doesn't roll back A/C. |
| **C7** | Worker lacks TenantContext binding | **FULLY RESOLVED** | Plan 03: each `dispatchOne` wrapped in `ScopedValue.where(TenantContext.TENANT, tenantId).run(...)`. Reaper also wraps per row. Test asserts correct `TenantContext.current()` during dispatch. |
| **C8** | Scheduler time source drift (Postgres `now()` vs Java `currentInstant`) | **FULLY RESOLVED** | Plan 03: `Supplier<Instant> currentInstant` bean. One `referenceInstant` per tick, passed to SQL (`?::timestamptz AT TIME ZONE`) AND Java (`atZone(...).toLocalDate()`). DB never uses `now()`. Test asserts both paths derive from same stubbed instant. |
| **H1** | D-07 missed-hour recovery claim contradicts D-06 exact-hour match | **FULLY RESOLVED** | D-07 updated: "SKIPPED with NO catch-up" for missed-hour. User-facing `sendHour.downtimeNote` in vi + en (Plan 04). |
| **H2** | Single-transaction fanout (same as C6) | **FULLY RESOLVED** | Per C6 analysis above. |

---

### Prior MEDIUM Resolution Audit

| ID | Concern | Status | Evidence |
|----|---------|--------|----------|
| Codex M: `claimPending` returns `boolean` | **FULLY RESOLVED** | Plan 03: returns `DigestClaimRecord(deliveryId, tenantId, digestDayLocal, attemptCount, channel)` |
| Codex M: transient digest retry | **FULLY RESOLVED** | Schema has `next_attempt_at` column; v1 marks FAILED; reaper available for future retry flip |
| Codex M: locale from `tenants.preferred_language` (wrong table) | **FULLY RESOLVED** | Plan 03: joins `users u` and selects `u.preferred_language` |
| Codex M: `digest_delivery` lacks `external_id` | **FULLY RESOLVED** | Plan 01 changeset 035: `external_ref varchar(255) NULL` |
| Codex M: `ResendBoundaryArchTest` vacuously passes in `core` | **FULLY RESOLVED** | Plan 03: test placed in `backend/worker/src/test/java/.../arch/` — scans actual SDK import site |
| Codex M: Q1/Q3 may include SENT mail | **FULLY RESOLVED** | Plan 02: Q1/Q3 add `AND 'INBOX' = ANY(label_ids)`. Test case (g) verifies. |
| OpenCode M1: Modulith dep missing `analytics` | **FULLY RESOLVED** | Plan 03: `allowedDependencies={"analytics", "account", "tenant", "shared.persistence", "shared.lang"}` |
| OpenCode M2: TenantContext/ScopedValue (same as C7) | **FULLY RESOLVED** | Per C7. |
| OpenCode M3: `tenantId` param vs TenantContext inconsistency | **FULLY RESOLVED** | Plan 02: service signature `summarize(UUID tenantId, TimeWindow)`. Controller extracts `TenantContext.currentOrThrow()`. Worker passes explicit id. Tests assert param ≠ TenantContext. |
| OpenCode M4: null user email in channel | **FULLY RESOLVED** | Plan 03: scheduler short-circuits on null/blank address → `markFailed("no_email_found")`. Channel guards with `Objects.requireNonNull` + returns `PermanentFailure`. Test verifies Resend never called on null. |
| OpenCode M5: `core.analytics` module location ambiguous | **FULLY RESOLVED** | Plan 02 creates `core.analytics` as new Modulith module (not inlined). |
| OpenCode M6: subject-line timing leak | **PARTIALLY RESOLVED** | Flagged as v2 hardening in summary. No v1 action taken. Acceptable. |

---

### New Strengths

1. **Transactional boundary isolation is textbook correct.** The `scheduledDispatch()` → `dispatchOne()` → three `REQUIRES_NEW` FSM methods pattern cleanly solves the fundemental H2/C6 double-send risk. The test case that proves tenant B's failure doesn't roll back A and C is exactly the right proof.
2. **Clock-coherence architecture is elegant.** A single `Supplier<Instant>` bean, captured once per tick, threaded into both the SQL `EXTRACT(HOUR FROM (?::timestamptz ...))` and the Java `digest_day_local` math. Eliminates an entire class of near-hour-boundary bugs.
3. **Idempotency is now five-layer** — ShedLock → UNIQUE constraint → `REQUIRES_NEW` isolation → Resend Idempotency-Key → reaper. Each layer independently sufficient; any three can fail without double-send.
4. **User-facing honesty about operational limits.** The D-07 missed-hour policy is explicitly communicated via `sendHour.downtimeNote` in vi + en, referenced by `aria-describedby` on the Select trigger. This sets correct expectations instead of papering over a limitation.
5. **Test case density is excellent.** Every review fix has a dedicated test case (isolation case, ScopedValue case, single-instant case, null-email case, INBOX filter case, closed-window boundary case, empty-string window case).

---

### New Concerns

**HIGH — None.** All prior HIGHs are structurally resolved.

**MEDIUM**

| # | Concern | Location | Detail |
|---|---------|----------|--------|
| M7 | `preferred_language` null edge case in digest scheduler | Plan 03 Task 3, `dispatchOne` step 3 | `Locale.forLanguageTag(tenant.preferredLanguage())` will NPE if `preferred_language` is NULL (pre-5C users with incomplete profiles). Add a null/blank guard with `"en"` fallback at the resolution point. |
| M8 | `TimeWindow.endingAt(Instant.now(), window.duration())` may produce extremely short windows at the API level | Plan 02 Task 2 | If `Instant.now()` is captured at the controller boundary and the service call is synchronous, the window covers literally `[now-duration, now)`. For `7d` this is meaningless drift, but `window=7d&_t=123456` could clock-cache. Not a bug for v1, but add a `@Min(1)` on window enum durations if any sub-day window is ever added. Flag this as a durability guard for future readers. |
| M9 | 037 backfill depends on `tenants` table existing + having rows; re-running on a fresh DB with zero tenants is a no-op that may confuse operators | Plan 01 Task 1 | The backfill is idempotent, but if run before any tenant exists, it silently inserts zero rows. When tenants are later provisioned, D-17 handles new ones. An operator reading the DB after deploy might wonder why `notification_preference` is empty until first login. Not a bug, but document in summary. |
| M10 | No explicit protection against `dispatchOne` taking longer than `lockAtMostFor=PT20M` | Plan 03 Task 3 | If the fanout takes >20 minutes (e.g., 1000 tenants at 2s each = 33min), ShedLock releases and another worker instance could claim the same tenants in a new tick. This would produce double-sends. Add a loop guard: check elapsed time and log a warning if approaching `lockAtMostFor - buffer`. |
| M11 | Gmail SDK `format=METADATA` + `metadataHeaders` API shape is an assumption | Plan 01 Task 2 | The plan delegates to Context7 verification. If the Gmail Java SDK 2.x doesn't support `metadataHeaders` as a parameter (or uses a different builder pattern), this breaks silently. The plan is correct to flag this, but the risk remains until the Context7 check completes. |

**LOW**

| # | Concern | Detail |
|---|---------|--------|
| L5 | M10 scale guard could be a single `LockAssert.assertLocked()` check inside `dispatchOne` — fails fast if lock expired | Adds a ~μs overhead per 200 tenants; negligible. Worth adding as a v1 hardening pattern. |
| L6 | `digestComposer.compose(...)` passes `sendMoment` not `referenceInstant` — these are the same value, but the parameter is confusingly named in the `DigestComposer` signature | Rename parameter to `referenceInstant` for clarity with Plan 03's load-bearing single-instant discipline. |
| L7 | `notifications-api.ts` fetches preferences on every page load of `/settings`; no `initialData` from server render | Prefs page could flash loading state for ~100ms. Add `initialData` from server-side cookie or RSC fetch. Deferrable. |

---

### Suggestions

**S1 (MEDIUM) — Null locale guard.** Add before `Locale.forLanguageTag(tenant.preferredLanguage())` in `DigestDispatchScheduler`:
```java
String language = tenant.preferredLanguage();
if (language == null || language.isBlank()) language = "en";
Locale locale = Locale.forLanguageTag(language);
```

**S2 (LOW) — `DigestComposer` parameter rename.** Rename `compose(..., Instant sendMoment, ...)` to `compose(..., Instant referenceInstant, ...)` for clarity across the single-instant-discipline code path. Same variable name in scheduler → composer → TimeWindow construction reduces cognitive load.

**S3 (LOW) — Add elapsed-time guard in fanout loop.** Before each `dispatchOne` call, check `Duration.between(referenceInstant, Instant.now())` against `lockAtMostFor` minus a buffer. Log warning at 80% of limit.

**S4 (LOW) — Document 037 no-op behavior in plan summary.** Note: "Backfill only inserts rows for existing tenants. On a fresh database with zero tenants, the changeset runs without error and inserts zero rows."

---

### Risk Assessment

| Factor | Rating | Rationale |
|--------|--------|-----------|
| Idempotency integrity | **LOW** | Five-layer protection; C6/H2 write-order fully fixed; all isolation cases tested |
| Tenant/isolation correctness | **LOW** | ScopedValue binding + explicit tenantId param + cross-tenant Q1–Q4 denial test |
| Clock/timezone correctness | **LOW** | Single reference instant, DST-safe `AT TIME ZONE`, DST-free v1 default, closed-window test |
| Privacy compliance | **LOW** | Analytics content-ban + privacy sweep for both endpoint + digest; sender email excluded from logs |
| Scheduler operational | **MEDIUM** | M10 (fanout taking >20min) is a real but low-probability risk; add S3 guard |
| Execution complexity | **MEDIUM** | ~80 files, 2 new Modulith modules, 1 new SDK, OpenAPI regen; procedural risks around Gmail SDK verification and Resend live deliverability tests |
| Remaining procedural risk | **MEDIUM** | M11 (Gmail SDK API shape) and live Resend domain verification are the two unverifiable-by-plan risks |

**Overall risk: LOW-MEDIUM.** The replan correctly identifies and resolves every prior HIGH and MEDIUM concern. No structural architectural defects remain. The remaining risk is operational: executor procedural risks (Context7 verification, Resend domain setup) and the M10 scale guard missing (easily added in execution). With S1–S4 applied during execution, risk drops to **LOW**.

---

## Consensus Summary

### Prior HIGH Resolution Audit (Cross-Reviewer Agreement)

| Prior HIGH | Codex Verdict | OpenCode Verdict | Final |
|---|---|---|---|
| Codex C1 (NotificationPreference PK) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| Codex C2 (ChannelType.EMAIL casing) | PARTIALLY RESOLVED (stray lowercase `channel='email'` in Plan 01 Task 3 OAuth test) | FULLY RESOLVED | **PARTIALLY RESOLVED** |
| Codex C3 (Tenant backfill) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| Codex C4 (Sender extraction writer) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| Codex C5 (Closed-window TimeWindow) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| Codex C6 (Idempotency / write-order) | PARTIALLY RESOLVED (Plan 03 text contradicts itself: `@Transactional(REQUIRES_NEW)` on `dispatchOne` vs "send outside any DB transaction") | FULLY RESOLVED | **PARTIALLY RESOLVED** |
| Codex C7 (Worker TenantContext / ScopedValue) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| Codex C8 (Clock-coherence single instant) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| OpenCode H1 (D-07 missed-hour policy) | FULLY RESOLVED | FULLY RESOLVED | **FULLY RESOLVED** |
| OpenCode H2 (Single-tx fanout double-send) | FULLY RESOLVED for original; transaction-wording conflict remains (see C6) | FULLY RESOLVED | **PARTIALLY RESOLVED** (rolls up to C6) |

**Net:** 7 of 9 prior HIGHs are FULLY RESOLVED by both reviewers. 2 of 9 are PARTIALLY RESOLVED (C2 — stray text; C6/H2 — internal contradiction in Plan 03 transaction wording).

### Agreed Strengths

- Schema-first dependency chain (Plan 01 → 02 → 03 → 04) with clean Modulith module boundaries.
- Five-layer idempotency stack (ShedLock → UNIQUE constraint → REQUIRES_NEW isolation → Resend Idempotency-Key → reaper).
- Single `Supplier<Instant>` reference-instant discipline threaded through SQL and Java — eliminates near-hour-boundary drift.
- ScopedValue/TenantContext binding correctly placed at per-tenant boundary; explicit `tenantId` param in service signatures (separation from context).
- Test case density: every prior review fix has a dedicated test case (isolation, ScopedValue, single-instant, null-email, INBOX filter, closed-window, empty-string window).
- Honest user-facing disclosure of D-07 missed-hour no-catch-up policy via `sendHour.downtimeNote` (vi + en).

### Agreed Concerns (New This Cycle)

**HIGH (raised by Codex only — OpenCode rated risk as LOW-MEDIUM and found no new HIGHs):**

- **HIGH-A: Plan 03 transaction-boundary text is internally contradictory.** Must-have says "Resend called outside any DB transaction" but Task 3 instructs putting `@Transactional(REQUIRES_NEW)` on `dispatchOne`. Implementers will produce either a wrapping transaction around the network call (defect) or have to interpret the plan. Fix: keep `REQUIRES_NEW` only on `claimPending`, `markSent`, `markFailed`; `dispatchOne` must NOT be transactional. (This is the residue of C6/H2.)
- **HIGH-B: Digest window anchored to cron execution instant, not configured send-hour boundary.** Plan 03 uses `sendMoment = referenceInstant`; with cron at `:05`, the window becomes `[yesterday HH:05, today HH:05)` instead of the SPEC-required `[yesterday HH:00, today HH:00)`. Fix: derive `scheduledLocalDateTime = localDate.atTime(digestSendHourLocal, 0)`, convert to Instant, use that as window end.
- **HIGH-C: `tenants.time_zone` Liquibase default likely wrong shape.** `defaultValueComputed: 'Asia/Ho_Chi_Minh'` is not a valid SQL expression; should be `defaultValue: Asia/Ho_Chi_Minh` (string literal default) or a properly quoted computed expression. Will fail or produce wrong default at column-add time.

**MEDIUM (cross-reviewer overlap, not duplicates):**

- Modulith dependency direction still muddy for `account` ↔ `notification` (Codex): expose notification via named interface OR use account-deleted event.
- `ResendBoundaryArchTest` location text inconsistent (Codex): file list says worker, but Task 1/verification still reference `backend/core`. Move fully to worker.
- Window-param normalization contract split (Codex): Plan 02 alternates between "400 on empty `?window=`" and "default to 7d"; Plan 04 says auto-correct; threat model says 400. Pick one and align tests.
- `EmailNotificationChannel` null guard text contradicts itself (Codex): `Objects.requireNonNull(recipientAddress)` vs test expecting `PermanentFailure("no_email_found")` on null. Implement explicit null-or-blank guard returning PermanentFailure.
- `preferred_language` null guard missing at digest scheduler (OpenCode M7 / S1): `Locale.forLanguageTag(null)` NPEs; needs fallback to `"en"`.
- Locale/user lookup may multi-row (Codex): `JOIN users u ON u.tenant_id = t.id` can duplicate if multi-user tenant exists; need `LIMIT 1`/owner predicate AND fallback for null `preferred_language`.
- `lockAtMostFor=PT20M` fanout timeout risk (OpenCode M10): if tenants × per-tenant time > 20m, another instance could double-claim; add elapsed-time guard / `LockAssert.assertLocked()` inside loop.
- Gmail SDK `format=METADATA + metadataHeaders` API shape is an unverified assumption (OpenCode M11) — flagged for Context7 verification at execution time.

**LOW:**

- Resend SDK version policy inconsistent (Codex L): some sections say "pin current stable after Context7", others hardcode `4.13.0`. Pick one stance.
- `DigestComposer` parameter named `sendMoment` should be renamed `referenceInstant` for clarity (OpenCode L6).
- `/settings/notifications` page has no `initialData` (OpenCode L7) — flash of loading.

### Divergent Views

- **Risk rating divergence:** Codex rates risk as **HIGH until Plan 03 amended** (drops to MEDIUM after fixes); OpenCode rates risk as **LOW-MEDIUM** overall. Source of divergence: Codex treats the Plan 03 transaction-wording contradiction (C6 residue) and digest-window-anchor bug as ship-blocking HIGH; OpenCode treats both as resolved or implementation-phase polish. Codex's reading is more conservative and correct on the plan text as written — implementers would have to disambiguate.
- **Window normalization contract:** Codex sees this as an unresolved contradiction across Plan 02/04; OpenCode does not call it out. Adopt Codex's reading and pick one canonicalization rule.

### Recommended Action

Replan one more pass to address the three Codex HIGHs (A: transaction-wording contradiction; B: digest window cron-vs-send-hour anchor; C: Liquibase string-default shape) plus the C2 stray lowercase `channel='email'` test assertion. After those fixes the plan should be execution-ready at LOW-MEDIUM risk.
