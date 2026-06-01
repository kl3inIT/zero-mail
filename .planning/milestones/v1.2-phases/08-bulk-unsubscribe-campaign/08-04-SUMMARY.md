---
phase: 08-bulk-unsubscribe-campaign
plan: 04
subsystem: cleanup
tags: [cleanup, candidate-query, suppression, gmail-ingest, list-unsubscribe, uns-01, uns-02]
requirements: [UNS-01, UNS-02]
dependency_graph:
  requires:
    - 08-03 (cleanup persistence layer + 4 entities + 4 repositories + converter)
    - Liquibase 041 (mail_message_observed list_unsubscribe_* columns)
    - Liquibase 043 (sender_suppression table)
    - GmailPreviewReadService (Phase 2A baseline)
    - AnalyticsSummaryQueryService (read-side query pattern reference)
  provides:
    - core.cleanup.usecases.CandidateQueryService (UNS-01 read-side)
    - core.cleanup.usecases.SuppressionCrudService (UNS-02 manual CRUD)
    - core.cleanup.usecases.SuppressionAutoAddService (UNS-02 reply heuristic)
    - core.cleanup.projection.UnsubscribeCandidateProjection
    - core.cleanup.projection.SenderSuppressionProjection
    - core.cleanup.projection.CampaignStatusProjection
    - core.cleanup.projection.PerSenderAttemptProjection
    - GmailPreviewReadService.extractListUnsubscribe (static, reusable extractor)
    - GmailPreviewReadService.ListUnsubscribeExtraction (extraction record)
    - Persistence path: List-Unsubscribe header → mail_message_observed.list_unsubscribe_*
  affects:
    - GmailDeliveryProcessingService now requests List-Unsubscribe metadata headers and forwards extraction to the repository
    - MailMessageObservedRepository.insertObservedIfAbsent now takes 3 extra params
tech_stack:
  added: []
  patterns:
    - Static parser + record extraction (extractListUnsubscribe → ListUnsubscribeExtraction)
    - CQRS-lite read-side service (@Service @Transactional(readOnly=true) + JdbcTemplate, mirroring AnalyticsSummaryQueryService)
    - XOR validated command record (AddSuppressionCommand enforces sender_email|sender_domain at the application boundary)
    - SQL anti-join via NOT EXISTS for suppression filtering (matches changelog 043 partial unique index strategy)
    - Tenant-scoped ScopedValue rebind in tests (TenantContext.TENANT for @TenantId resolution)
key_files:
  created:
    - backend/core/src/main/java/com/zeromail/core/cleanup/projection/UnsubscribeCandidateProjection.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/projection/SenderSuppressionProjection.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/projection/CampaignStatusProjection.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/projection/PerSenderAttemptProjection.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CandidateQueryService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/SuppressionCrudService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/SuppressionAutoAddService.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CandidateQueryServiceTest.java (Wave 0 RED → GREEN)
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/SuppressionServiceTest.java (Wave 0 RED → GREEN)
    - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
decisions:
  - D-11 HTTPS-only parse-time guard enforced by GmailPreviewReadService.extractListUnsubscribe — http:// dropped silently
  - D-21 module boundary respected — CandidateQueryService lives in core.cleanup.usecases, NOT shared with core.analytics.usecases
  - D-23 mailto parsed through java.net.URI (not regex), preserving query params for the unsubscribe worker
  - Privacy invariant: log boolean presence flags only for the new extractor; suppression services log target shape (email|domain) + reason id, never raw value
  - 90-day reply lookback window is a constant (Duration.ofDays(90)) in SuppressionAutoAddService, not a configurable property in v1
  - AddSuppressionCommand normalizes blank inputs to null so the entity XOR check sees a clean null
metrics:
  duration_minutes: ~140
  tasks_completed: 3
  files_changed: 13
  commit_count: 3
  completed_date: 2026-05-20
---

# Phase 8 Plan 04: Wave 3 — Candidate Query + Suppression CRUD + List-Unsubscribe Extraction Summary

Extract `List-Unsubscribe` (URL + mailto + RFC 8058 one-click flag) at Gmail ingest time and persist into the 3 columns shipped by Liquibase 041; ship the UNS-01 candidate query service (anti-join against `sender_suppression`) and the UNS-02 suppression CRUD + reply-based auto-add services with 4 read-side projection records to back the upcoming `GET /api/unsubscribe/candidates` endpoint.

## What Shipped

- **`GmailPreviewReadService.extractListUnsubscribe`** — static extractor parsing the RFC 2369 `List-Unsubscribe` header value into a `(url, mailto, oneClick)` triple. HTTPS-only guard at parse time (D-11); mailto parsed via `java.net.URI` (D-23) preserving `?subject=&body=` params; one-click flag requires both the HTTPS URL AND `List-Unsubscribe-Post: List-Unsubscribe=One-Click`. URL capped at 2048 chars, mailto at 512.
- **`GmailDeliveryProcessingService`** now requests `List-Unsubscribe` + `List-Unsubscribe-Post` metadata headers and forwards the extraction through to the repository on every observation insert.
- **`MailMessageObservedRepository.insertObservedIfAbsent`** signature extended with the 3 new fields. Liquibase 041 column defaults remain `NULL`/`false` so legacy data forward-compat (D-10).
- **4 projection records** — `UnsubscribeCandidateProjection`, `SenderSuppressionProjection`, `CampaignStatusProjection`, `PerSenderAttemptProjection`. All Java 25 records with explicit Objects.requireNonNull invariants; the `CampaignStatusProjection` compact constructor defensively copies the per-sender list.
- **`CandidateQueryService`** — `@Service @Transactional(readOnly=true)` + `JdbcTemplate`, anti-joins `sender_suppression` by either email or domain inside `[now - window, now)`, uses `BOOL_OR` to detect ONE_CLICK across the group, and hard-caps the limit at `UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN`. Privacy log surface: `event=cleanup_candidates_queried` with count only.
- **`SuppressionCrudService`** — `addManual` (idempotent — returns existing projection on conflict), `list`, `findById`, `remove`. XOR enforced via `AddSuppressionCommand` record so the violation never reaches the entity layer.
- **`SuppressionAutoAddService.scanAndAutoAdd`** — scans `triage_audit` (`action_type='SAVE_DRAFT' AND decision='APPLIED'`) inside a 90-day window, inserts `SenderSuppressionEntity` rows with `reason=REPLIED` for new sender emails. SQL-level anti-join prevents duplicate inserts; idempotent across re-runs.

## Acceptance Criteria — Verification

| Criterion | Result |
|----------|--------|
| `GmailPreviewReadService` extracts + persists 3 columns with D-11 HTTPS-only guard | PASS (literal `"https://"` + `List-Unsubscribe=One-Click` present in source) |
| `CandidateQueryService` SQL with anti-join + BOOL_OR ships | PASS (SQL constant contains `NOT EXISTS` + `BOOL_OR` + `sender_suppression`) |
| `SuppressionCrudService` 3 methods (`addManual`/`list`/`remove`) | PASS — additionally ships `findById` |
| `SuppressionAutoAddService.scanAndAutoAdd` 90-day heuristic | PASS (`Duration.ofDays(90)` constant; SQL anti-join) |
| 4 projection records land under `core.cleanup.projection` | PASS |
| `CandidateQueryServiceTest` flip GREEN (4 functional tests + 1 type check) | PASS |
| `SuppressionServiceTest` flip GREEN (6 tests covering add/list/remove + auto-add) | PASS |
| `grep core.cleanup.application` returns empty (D-21) | PASS |
| Privacy log scan — no raw `senderEmail`/URL/mailto/full token in log lines | PASS |
| Existing Gmail preview / ingest tests no regression | PASS (3 updated tests still GREEN) |

## Deviations from Plan

### Auto-fixed Issues

1. **[Rule 3 — Blocking] Wave 0 stubs used reflection placeholders incompatible with the production API**
   - **Found during:** Task 2 + Task 3 verification.
   - **Issue:** `CandidateQueryServiceTest` instantiated the future class via `Class.forName(...).getDeclaredConstructor().newInstance()` (no-arg) and invoked `findCandidates(UUID)` via reflection; `SuppressionServiceTest` similarly invoked `addSenderEmail`, `addSenderDomain`, `add`, `removeById`, `runAutoAddCycle` reflectively. These method names / signatures did not match the spec-defined production API (`findCandidates(tenantId, window, limit)`, `addManual(tenantId, command)`, `remove(tenantId, id)`, `scanAndAutoAdd(tenantId)`).
   - **Fix:** Replaced reflection plumbing with `@Autowired` bean lookup; rewrote each test to invoke the real production methods. Spec line 263–268 requires test method names to remain (which they do); the reflective glue was a Wave 0 RED scaffolding artifact, not a contract.
   - **Files modified:** `CandidateQueryServiceTest.java`, `SuppressionServiceTest.java`
   - **Commits:** ee9454ae (CandidateQueryService test), ac23e15d (SuppressionService test)

2. **[Rule 3 — Blocking] Wave 0 stubs inserted into a non-existent `mail_message_observed.sender_domain` column**
   - **Found during:** Task 2 first test run.
   - **Issue:** The Wave 0 stub seeded `(sender_email, sender_domain, list_unsubscribe_url, …)` but `mail_message_observed` has no `sender_domain` column — that field is computed in SQL via `split_part(sender_email, '@', 2)` inside `CandidateQueryService`.
   - **Fix:** Helper now extracts the domain from the email and embeds it into the URL / mailto so the SQL split still produces the expected value.
   - **Files modified:** `CandidateQueryServiceTest.java`
   - **Commit:** ee9454ae

3. **[Rule 3 — Blocking] `triage_audit` seed used wrong column names**
   - **Found during:** Task 3 first test run.
   - **Issue:** Wave 0 stub referenced `subject_excerpt` (not a column) and `matcher_evidence` (not a column). Correct column names from changelog 040 are `sanitized_subject` and `sanitized_sender_email`.
   - **Fix:** Rewrote the seed insert against the real schema (`sanitized_subject`, `sanitized_sender_email`, no `matcher_evidence`).
   - **Files modified:** `SuppressionServiceTest.java`
   - **Commit:** ac23e15d

4. **[Rule 3 — Blocking] `java.time.Instant` direct bind into PreparedStatement caused PSQLException**
   - **Found during:** Task 2 + Task 3 test runs.
   - **Issue:** PostgreSQL JDBC driver refuses to infer SQL type for raw `Instant` (`Can't infer the SQL type to use for an instance of java.time.Instant`).
   - **Fix:** Wrap all `Instant` test parameters in `java.sql.Timestamp.from(...)`.
   - **Files modified:** `CandidateQueryServiceTest.java`, `SuppressionServiceTest.java`
   - **Commits:** ee9454ae, ac23e15d

5. **[Rule 3 — Blocking] Hibernate `@TenantId` rejected suppression inserts without bound tenant context**
   - **Found during:** Task 3 second test run.
   - **Issue:** `SenderSuppressionEntity` extends `AbstractTenantOwnedEntity` (`@TenantId`-managed). Insert through `JpaRepository.save` requires `TenantContext.TENANT` ScopedValue bound to the inserting tenant; without it Hibernate throws `PropertyValueException: assigned tenant id differs from current tenant id [<seed-tenant> != 00000000-0000-0000-0000-000000000000]`.
   - **Fix:** Wrap each suppression service invocation in `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(...)`.
   - **Files modified:** `SuppressionServiceTest.java`
   - **Commit:** ac23e15d

All five items are mechanical Wave 0 → Wave 2 glue fixes — none touched the spec-defined behavior of the production services. None were architectural (Rule 4) and none warranted a human checkpoint.

## Deferred Issues (Pre-existing, Out of Scope)

These tests were failing on `git stash` baseline (i.e. before any work in this plan) and continue to fail after my changes — they reference future Wave 4 / Wave 5 production classes that this plan does not ship:

- `TriageGmailWriterLookupLabelIdTest.returnsEmptyWhenLabelMissing` + `returnsLabelIdWhenLabelExists` — `TriageGmailWriter.lookupLabelId(UUID, String)` not yet implemented.
- `TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows` — depends on Wave 4 writer + Plan 07 cleanup undo flow.
- `CleanupModuleVerificationTest.cleanupModuleIsDeclaredAndVerifies` — Spring Modulith verifier fails to load `com.zeromail.core.support` (test-support package without main-source classes; needs verifier scoping update later).
- `CleanupPrivacySweepTest.future_campaign_execute_service_is_present` + `campaignExecution_doesNotLeakSensitiveTokensInLogs` — Wave 4 `CampaignExecuteService` not yet shipped.
- `UnsubscribeHttpClientBoundaryTest.unsubscribeHttpClient_classMustExist` — Wave 4 HTTP client not yet shipped.

These are tracked for the Wave 4 / Wave 5 / Plan 07 executors; this plan logged them rather than touching them per the executor scope boundary.

## Threat Flags

None. The 3 new persistence columns (`list_unsubscribe_url`, `list_unsubscribe_mailto`, `list_unsubscribe_one_click`) all flow through the same Gmail ingest path that already passes the privacy invariant; the read-side query is tenant-scoped via `WHERE tenant_id = ?` + `@TenantId` discriminator; the suppression CRUD respects the same tenant filter on every repository call.

The List-Unsubscribe extractor is the new attack surface, but it is read-only at ingest time (the actual `POST` happens in a later wave) — the parse-time HTTPS guard (D-11) makes it impossible to persist an `http://` value that would later be POSTed without TLS. The mailto parser uses `java.net.URI` so a malformed mailto returns `null` rather than reaching the DB.

## Known Stubs

None. All shipped services implement the spec contract; no placeholder data flows to the UI from this plan (the controller layer lands in Wave 5).

## Self-Check: PASSED

**Files exist:**
- `backend/core/src/main/java/com/zeromail/core/cleanup/projection/UnsubscribeCandidateProjection.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/projection/SenderSuppressionProjection.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/projection/CampaignStatusProjection.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/projection/PerSenderAttemptProjection.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CandidateQueryService.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/SuppressionCrudService.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/SuppressionAutoAddService.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java` (modified) — FOUND
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` (modified) — FOUND
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` (modified) — FOUND

**Commits exist:**
- `c0dea44c` — FOUND (Gmail List-Unsubscribe ingest)
- `ee9454ae` — FOUND (CandidateQueryService + projections)
- `ac23e15d` — FOUND (suppression services)
