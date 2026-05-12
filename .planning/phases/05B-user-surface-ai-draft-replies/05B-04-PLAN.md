---
phase: 05B-user-surface-ai-draft-replies
plan: 04
type: execute
wave: 3
depends_on: ["05B-02"]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java
  - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java
  - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java
  - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPageQuery.java
  - backend/core/src/main/java/com/zeromail/core/triage/projection/package-info.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyRow.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPage.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPageQuery.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/package-info.java
  - backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java
  - backend/core/src/main/java/com/zeromail/core/shared/pagination/package-info.java
  - backend/core/src/main/java/com/zeromail/core/thread/usecases/MarkThreadResolvedService.java
autonomous: true
requirements: [DRFT-04]
must_haves:
  truths:
    - "GET-side audit-list and needs-reply-inbox queries are cursor (keyset) paginated — no OFFSET, no COUNT(*) on the growing tables"
    - "Both queries are tenant-scoped — tenant A never sees tenant B rows"
    - "The TO_REPLY count badge is served by the partial-index count query (countByBucketAndResolvedFalse)"
    - "Mark-resolved flips the thread_reply_status row's resolved flag for the current tenant only"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java"
      provides: "JdbcTemplate keyset query over triage_audit ordered by (created_at desc, audit_id desc) with action/since/until filters"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java"
      provides: "JdbcTemplate keyset query over thread_reply_status filtered by bucket + NOT resolved (or resolved for the Resolved tab) + the bucket count for the badge"
    - path: "backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java"
      provides: "opaque base64 (timestamp:uuid) cursor encode/decode helper, fail-loud on malformed input"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java"
      to: "triage_audit table"
      via: "JdbcTemplate.query keyset SQL"
      pattern: "from triage_audit"
    - from: "backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java"
      to: "thread_reply_status table"
      via: "JdbcTemplate.query keyset SQL filtered by bucket"
      pattern: "from thread_reply_status"
---

<objective>
Build the CQRS-lite read side for the audit list and the needs-reply inbox: a `core.triage.projection.AuditLogQueryService` (JDBC keyset query over `triage_audit`, closing the 5A `GET /api/triage/audit` gap), a `core.thread.projection.NeedsReplyInboxQueryService` (JDBC keyset query over `thread_reply_status` by bucket + a `countByBucketAndResolvedFalse` for the sidebar badge), a shared `KeysetCursor` codec helper in `core.shared.pagination`, and a `MarkThreadResolvedService` (flips `resolved` on the current tenant's row). Inbox *display* fields (subject, participants, last-activity) are NOT persisted — Plan 05's controller (or this query service) fetches them live from Gmail `threads.get(metadata)` per row; this plan provides the projection rows (ids + bucket + draft status + `lastClassifiedAt`).

Purpose: Read endpoints (Plan 05) and the needs-reply UI (Plan 06) depend on these query services. Cursor pagination (no `OFFSET`/`COUNT(*)`) is a project convention (D-13/D-17).
Output: `core.triage.projection` + `core.thread.projection` packages, `KeysetCursor`, `MarkThreadResolvedService`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md
@backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
@backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
</context>

<interfaces>
<!-- Read the actual files for column names + the existing RowMapper style. -->

`GmailPreviewReadService.findRecentObservedMessages` (core.gmail.usecases): the `jdbcTemplate.query("""...""", (rs, n) -> mapRow(rs), params...)` + private `RowMapper` style to copy; also its `messages.get(format=METADATA)` shape if this service fetches display fields directly (Plan 05's controller may do that instead — coordinate, but default to "controller fetches display fields, query service returns ids/metadata").

`triage_audit` columns (Plan 4's `025-triage-audit.yaml` + `029-triage-revert-pending.yaml`): `audit_id`, `tenant_id`, `gmail_thread_id`, `gmail_message_id`, `rule_name_snapshot`, `action_type`, `reason`, `decision`, `external_ref`, `decided_at`, `created_at`, plus revert/pending columns — confirm exact names by reading the changelogs + `TriageAuditEntity`. Existing index `idx_triage_audit_tenant_decided_at`.

`thread_reply_status` columns (Plan 00's `030-thread-reply-status.yaml`): `id`, `tenant_id`, `gmail_thread_id`, `bucket`, `last_classified_message_id`, `last_classified_at`, `has_draft`, `draft_id`, `resolved`, `created_at`, `updated_at`, `version`. Indexes: `ux_thread_reply_status_tenant_thread`, partial `idx_thread_reply_status_to_reply`.

`ThreadReplyStatusRepository` (core.thread.persistence, Plan 02): `countByBucketAndResolvedFalse(ThreadReplyBucket)` for the badge, `findByGmailThreadId(String)` for mark-resolved.

`TenantContext.currentOrThrow()` → tenant id string; query services take `UUID tenantId` as the first param.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: KeysetCursor codec + AuditLogQueryService (closes the GET /api/triage/audit gap)</name>
  <files>backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java, backend/core/src/main/java/com/zeromail/core/shared/pagination/package-info.java, backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java, backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java, backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java, backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPageQuery.java, backend/core/src/main/java/com/zeromail/core/triage/projection/package-info.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java (the `jdbcTemplate.query` + `RowMapper` + `@Transactional(readOnly=true)` pattern)
    - backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml + 029-triage-revert-pending.yaml + backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java (exact `triage_audit` column names)
    - backend/core/src/main/java/com/zeromail/core/rules/projection/*.java or any existing `projection/` package (the read-side package shape + `package-info.java` + any `@NamedInterface("api")` re-exposure if used)
    - backend/api/src/test/java/.../AuditLogPaginationTest.java + TriageAuditControllerContractTest.java + AuditLogMultiTenantLeakTest.java (the RED tests this service must satisfy via the controller)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-13; .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"core/triage/projection ... cursor pagination"
  </read_first>
  <behavior>
    - `KeysetCursor` — `String encode(Instant ts, UUID id)` = `Base64.getUrlEncoder().withoutPadding().encodeToString((ts.toEpochMilli() + ":" + id).getBytes(UTF_8))`; `Optional<KeysetCursor> decode(String cursor)` — returns empty for null/blank, throws `IllegalArgumentException` for malformed (bad base64, missing `:`, unparseable epoch/UUID) so the controller can 400 it. Exposes `Instant timestamp()` + `UUID id()`. `core.shared.pagination/package-info.java` declares a leaf `@ApplicationModule(displayName="Pagination", allowedDependencies={})`.
    - `AuditLogPageQuery` — validated record `(int limit, String cursor, String action, Instant since, Instant until)`; `limit` clamped to a sane range (e.g. 1..100, default 50).
    - `AuditLogRow` — record `(UUID auditId, String gmailThreadId, String gmailMessageId, String ruleName, String action, String reason, String decisionState, Instant createdAt, String draftId)` (`draftId` = `external_ref`, nullable, meaningful only when `action == save_draft`).
    - `AuditLogPage` — record `(List<AuditLogRow> items, String nextCursor)`.
    - `AuditLogQueryService.page(UUID tenantId, AuditLogPageQuery query)` — `@Transactional(readOnly=true)`; SQL: `select audit_id, gmail_thread_id, gmail_message_id, rule_name_snapshot, action_type, reason, decision, external_ref, created_at from triage_audit where tenant_id = ? and (? is null or action_type = ?) and (? is null or created_at >= ?) and (? is null or created_at < ?) and (created_at, audit_id) < (?, ?) order by created_at desc, audit_id desc limit ?+1` — when `limit+1` rows return, drop the last, set `nextCursor = KeysetCursor.encode(last-kept.createdAt, last-kept.auditId)`; else `nextCursor = null`. The cursor predicate is `(created_at, audit_id) < (cursorTs, cursorId)` only when a cursor is supplied (decoded), else omitted. `RowMapper` maps to `AuditLogRow`. Never `OFFSET`, never `COUNT(*)`.
  </behavior>
  <action>
    Create `KeysetCursor` + its leaf module in `core.shared.pagination`. Create the `core.triage.projection` package with `AuditLogQueryService`, `AuditLogRow`, `AuditLogPage`, `AuditLogPageQuery`, and `package-info.java` (declare `@ApplicationModule` if `core.triage` isn't already auto-detected as one — the triage module already exists, so this is a sub-package; add a `@NamedInterface` re-exposure if the project's pattern requires it for `backend/api` to consume the projection records). Add `shared.pagination` to `core.triage`'s `allowedDependencies` if needed. Make the (RED, in `backend/api`) audit-list tests pass once Plan 05's controller wires this service.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*KeysetCursor*" --tests "*AuditLogQuery*" --tests "*ApplicationModules*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `KeysetCursor.encode/decode` round-trips; malformed input → `IllegalArgumentException`; null/blank → empty
    - `AuditLogQueryService.page(...)` returns at most `limit` rows ordered `(created_at desc, audit_id desc)`; `nextCursor` decodes back to the keyset of the last returned row; SQL contains no `OFFSET` and no `COUNT(*)`; the query filters by `tenant_id`, optional `action_type`, optional `created_at` range
    - `AuditLogRow.draftId` is populated from `external_ref` and is null for non-`save_draft` rows
    - `core.shared.pagination/package-info.java` is a leaf `@ApplicationModule`; `ApplicationModulesTest` + `DomainBoundaryArchTests` green
    - `mcp__jetbrains__get_file_problems` on new files clean
  </acceptance_criteria>
  <done>Cursor codec + audit-list read service land; the 5A `GET /api/triage/audit` gap can now be closed by Plan 05.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: NeedsReplyInboxQueryService + MarkThreadResolvedService</name>
  <files>backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java, backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyRow.java, backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPage.java, backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPageQuery.java, backend/core/src/main/java/com/zeromail/core/thread/projection/package-info.java, backend/core/src/main/java/com/zeromail/core/thread/usecases/MarkThreadResolvedService.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java (Task 1 — the keyset query shape to mirror)
    - backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml (column names + the partial index)
    - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java (Plan 02 — `countByBucketAndResolvedFalse`, `findByGmailThreadId`)
    - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java (Plan 02 — `fromId`)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java (a simple command-style write service for the `MarkThreadResolvedService` shape)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-17, D-19; .planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md §"Key Screens" (the row data the projection feeds)
  </read_first>
  <behavior>
    - `NeedsReplyPageQuery` — record `(ThreadReplyBucket bucket, boolean resolvedOnly, int limit, String cursor)`; the "To reply" tab → `bucket=TO_REPLY, resolvedOnly=false`; "Awaiting reply" → `bucket=AWAITING_THEIR_REPLY, resolvedOnly=false`; optional "Resolved" tab → `resolvedOnly=true` (any bucket). `limit` clamped 1..100, default 50.
    - `NeedsReplyRow` — record `(String gmailThreadId, String bucket, boolean hasDraft, String draftId, Instant lastClassifiedAt, boolean resolved)` — ids + status only; subject/participants/last-activity are fetched live by the controller from Gmail `threads.get(metadata)`, NOT here.
    - `NeedsReplyPage` — record `(List<NeedsReplyRow> items, String nextCursor)`.
    - `NeedsReplyInboxQueryService.page(UUID tenantId, NeedsReplyPageQuery query)` — `@Transactional(readOnly=true)`; SQL: `select gmail_thread_id, bucket, has_draft, draft_id, last_classified_at, resolved from thread_reply_status where tenant_id = ? and ( (? then bucket = ? and not resolved) or (resolvedOnly then resolved) ) and (last_classified_at, gmail_thread_id) < (?, ?) order by last_classified_at desc nulls last, gmail_thread_id desc limit ?+1` — keyset over `(last_classified_at desc, gmail_thread_id desc)`; `nextCursor` from `KeysetCursor.encode(last-kept.lastClassifiedAt, /* deterministic UUID-or-hash of threadId */ ...)` — note `gmail_thread_id` is a string not a UUID, so either use a `(epochMilli:threadId)` text cursor variant or add a `KeysetCursor` overload for `(Instant, String)`; pick one and keep it consistent. No `OFFSET`, no `COUNT(*)` for paging.
    - `NeedsReplyInboxQueryService.toReplyCount(UUID tenantId)` — delegates to `ThreadReplyStatusRepository.countByBucketAndResolvedFalse(TO_REPLY)` (uses the partial index) for the sidebar badge.
    - `MarkThreadResolvedService.markResolved(UUID tenantId, String gmailThreadId)` — `@Transactional`; `findByGmailThreadId(...)` (tenant-filtered) → set `resolved=true` → save; if no row, no-op or a clean not-found exception (planner's call — recommend no-op so a benign housekeeping action never errors). Logs `event=thread_marked_resolved tenantId={} gmailThreadId={}` only.
  </behavior>
  <action>
    Create the `core.thread.projection` package mirroring `core.triage.projection`'s keyset-query shape, plus `MarkThreadResolvedService` in `core.thread.usecases`. If `KeysetCursor` needs a `(Instant, String)` overload for the string `gmail_thread_id` key, add it in Task 1's file (or here) and keep one codec. `package-info.java` for `core.thread.projection` (or rely on the existing `core.thread` module — sub-package; add `@NamedInterface` re-exposure if needed for `backend/api`). Run `ApplicationModulesTest`.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*NeedsReplyInboxQuery*" --tests "*MarkThreadResolved*" --tests "*ApplicationModules*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `NeedsReplyInboxQueryService.page(...)` returns at most `limit` rows for the requested bucket/resolved filter, keyset-ordered; SQL has no `OFFSET`/`COUNT(*)` for paging; tenant-scoped by `tenant_id`
    - `toReplyCount(tenantId)` uses `countByBucketAndResolvedFalse(TO_REPLY)` (partial index)
    - `NeedsReplyRow` carries only ids/status — no subject/participant/body columns selected
    - `MarkThreadResolvedService.markResolved(...)` flips `resolved` for the current tenant's row only; logs metadata-only
    - `ApplicationModulesTest` + `DomainBoundaryArchTests` green; `mcp__jetbrains__get_file_problems` on new files clean
  </acceptance_criteria>
  <done>Needs-reply inbox read service + count badge + mark-resolved land; Plan 05 wires them to controllers.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| `?cursor=` / `?action=` / `?since=` / `?until=` / `?bucket=` query params (from Plan 05 controller) | untrusted client input into the read queries |
| read queries → `triage_audit` / `thread_reply_status` | tenant-scoped append-only tables |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-04-01 | Information Disclosure | cross-tenant data leak via the audit-list / inbox queries | mitigate | Every query takes `UUID tenantId` (from `TenantContext` in the controller) and has `where tenant_id = ?` as the first predicate; multi-tenant-leak contract tests (`AuditLogMultiTenantLeakTest`) assert tenant A sees zero of tenant B's rows |
| T-05B-04-02 | Tampering | malformed/forged cursor causing a SQL error or unbounded scan | mitigate | `KeysetCursor.decode` is fail-loud (`IllegalArgumentException`) → controller maps to HTTP 400; the keyset predicate uses parameterized values only; `limit` clamped 1..100 |
| T-05B-04-03 | Denial of Service | `OFFSET`/`COUNT(*)` on append-only growing tables (`triage_audit`, `thread_reply_status`) | mitigate | Keyset pagination only — no `OFFSET`, no `COUNT(*)` for paging; the badge count uses the partial index `idx_thread_reply_status_to_reply` (cheap) |
| T-05B-04-04 | Tampering | `?action=` filter accepting arbitrary strings | accept | `action_type = ?` is parameterized; an unknown value simply returns zero rows — no injection, low value; no enum-validation needed at the query layer (the controller may still validate) |
| T-05B-04-05 | Information Disclosure | projection rows accidentally selecting content columns | mitigate | `thread_reply_status` has no content columns by design (Plan 00); `AuditLogRow`/`NeedsReplyRow` explicitly enumerate id/metadata columns only — code review + the contract tests assert the response shape carries no body/subject/participant |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*KeysetCursor*" --tests "*AuditLogQuery*" --tests "*NeedsReplyInboxQuery*" --tests "*MarkThreadResolved*" --tests "*ApplicationModules*"` all green
- `grep -rni "offset\b\|count(\*)" backend/core/src/main/java/com/zeromail/core/triage/projection backend/core/src/main/java/com/zeromail/core/thread/projection` returns nothing (paging) — the only `count` allowed is the repository's `countByBucketAndResolvedFalse`
- `mcp__jetbrains__get_file_problems` on all new projection + pagination + `MarkThreadResolvedService` files — no problems
</verification>

<success_criteria>
Read side complete: cursor-paginated `AuditLogQueryService` (closes the 5A gap) + `NeedsReplyInboxQueryService` + `toReplyCount` badge query + `MarkThreadResolvedService`, all tenant-scoped, keyset-paginated, projection rows metadata-only. Plan 05 wires them to REST.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-04-SUMMARY.md`
</output>
