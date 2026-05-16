---
phase: 05B-user-surface-ai-draft-replies
reviewed: 2026-05-13T00:00:00Z
depth: deep
files_reviewed: 38
files_reviewed_list:
  - apps/web/app/(protected)/(app)/needs-reply/page.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/features/needs-reply/api/needs-reply-api.ts
  - apps/web/features/needs-reply/components/GenerateDraftButton.tsx
  - apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx
  - apps/web/features/needs-reply/components/NeedsReplyRow.tsx
  - apps/web/features/needs-reply/components/NeedsReplyTable.tsx
  - apps/web/features/needs-reply/hooks/useGenerateDraft.ts
  - apps/web/features/needs-reply/hooks/useMarkResolved.ts
  - apps/web/features/needs-reply/hooks/useNeedsReplyInbox.ts
  - apps/web/features/needs-reply/hooks/useToReplyCount.ts
  - apps/web/features/needs-reply/messages.ts
  - apps/web/features/needs-reply/query-keys.ts
  - apps/web/features/triage/api/triage-api.ts
  - apps/web/features/triage/hooks/useTriageAuditLog.ts
  - apps/web/lib/use-hydrated.ts
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyListResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/ThreadDraftResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditListResponse.java
  - backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftBodyGenerator.java
  - backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftReplySourceLoader.java
  - backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java
  - backend/core/src/main/java/com/zeromail/core/draft/usecases/ToneContextBuilder.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
  - backend/core/src/main/java/com/zeromail/core/llm/domain/AllowListedTools.java
  - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java
  - backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java
  - backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java
  - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java
  - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java
  - backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java
findings:
  critical: 1
  warning: 8
  info: 4
  total: 13
status: issues_found
---

# Phase 05B: Code Review Report

**Reviewed:** 2026-05-13
**Depth:** deep
**Files Reviewed:** 38
**Status:** issues_found

## Summary

The needs-reply surface, on-demand draft generation, thread reply-status projection, and triage
audit log are well structured and largely faithful to the project conventions (thin controllers,
service-owned transactions, `@TenantId` discriminator scoping, structured privacy logging,
`SAVE_DRAFT_ONLY` tool profile, no auto-send). Multi-tenant scoping holds: JdbcTemplate read paths
filter `tenant_id = ?` explicitly and JPA paths run under `ScopedValue.where(TenantContext.TENANT, …)`
so Hibernate's `@TenantId` filter applies.

The notable defects are: (1) a real correctness gap in the on-demand draft path where a process
crash between the Gmail `drafts.create` call and the audit `markApplied` results in **duplicate
Gmail drafts on retry** — the audit PENDING→APPLIED guard runs *after* the non-idempotent Gmail
write, not before it; (2) a redundant/confusing double classification that stores a Gmail *draft id*
where a *message id* is expected; (3) several robustness and consistency issues in the frontend
(stale local draft-status state, a hard-coded 30-day undo expiry guess, an extra Gmail round-trip
for the sidebar badge). Details below.

## Critical Issues

### CR-01: On-demand draft generation can create duplicate Gmail drafts on retry/crash

**File:** `backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java:149-167`
**Issue:** `generateOrRegenerateWithTenantBound` calls `triageGmailWriter.saveDraft(...)` (which
hits the non-idempotent `users.drafts.create` Gmail API) and only *afterwards* runs
`persistAuditAndClassify`, which is where the audit PENDING→APPLIED idempotency record is written
(`insertPending` then `markApplied`). `TriageGmailWriter`'s own Javadoc states that
`users.drafts.create` "is intentionally guarded by the audit PENDING-to-APPLIED loop because Gmail
draft creation is not idempotent" — but in this path the guard is created *after* the create, so it
provides no protection. If the JVM dies (or the request times out and the client retries) between
`saveDraft` returning and `persistAuditAndClassify` committing, the next invocation re-acquires the
Redis lock (TTL 60s, or after expiry), reads `currentDraftId` (still the *old* id, since the audit
row was never written), and calls `saveDraft` again — producing a second draft for the same thread.
The Redis lock is best-effort (and returns empty when Redis is down, immediately throwing
`DraftGenerationInFlightException`), so it cannot be relied on for exactly-once semantics.
**Fix:** Reserve the audit PENDING row (and reclaim/lease it the way `TriageAuditSaga.reservePhase`
does) *before* calling `triageGmailWriter.saveDraft`, so a retry sees the in-flight/applied marker
and skips or resumes instead of re-creating. Alternatively, after `saveDraft` succeeds but before the
audit/classify transaction, persist `draft_id` on `thread_reply_status` in the same transaction that
records the audit, and on retry detect "a newer-than-old draft already exists on this thread" before
calling Gmail. Mirror the existing `TriageAuditSaga` reserve → gmail-write → finalize ordering.

## Warnings

### WR-01: `ThreadDraftSaved` handler stores a Gmail draft id as `lastClassifiedMessageId` and re-classifies redundantly

**File:** `backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java:67-79`
**Issue:** `GenerateThreadDraftService.persistAuditAndClassify` already calls
`classifyThreadReplyStatusService.classify(...)` with the correct `gmailMessageId`, and *then* the
service publishes `ThreadDraftSaved`, which triggers `on(ThreadDraftSaved)` to classify *again* with
`event.draftId()` passed in the `lastMessageId` position. A draft id is not a message id, so the
dedup check (`getLastClassifiedMessageId().equals(input.lastMessageId())`) never matches the row just
written, the entity is re-saved with `last_classified_message_id = <draftId>`, `last_classified_at`
is bumped a second time, and an extra `event=thread_reply_classified` log line is emitted. The
end-state bucket happens to be the same (`TO_REPLY`), so this is not data corruption, but it is
confusing logic that pollutes the keyset-pagination ordering column with a non-message-id value and
double-writes on every draft generation.
**Fix:** Either drop the direct `classify(...)` call in `GenerateThreadDraftService` and let the
event handler own classification (passing the real `gmailMessageId`, not `draftId`), or drop the
`@ApplicationModuleListener on(ThreadDraftSaved)` re-classification entirely. Do not pass `draftId`
where `lastMessageId` is expected.

### WR-02: Draft generation is fully unavailable whenever Redis is down

**File:** `backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java:144-148`
**Issue:** `redisDistributedLock.tryAcquire(...)` returns `Optional.empty()` both when the lock is
contended *and* when Redis itself is unavailable (`RedisDistributedLock.tryAcquire` logs
`event=redis_lock_unavailable` and returns empty). The service treats every empty as
`DraftGenerationInFlightException` (HTTP 409), so a Redis outage turns every "Draft reply" click into
a 409 with the user-facing message "A draft is already being generated for this thread." — which is
both wrong and confusing. Fail-closed on the lock is defensible, but it should surface as a transient
"try again later" condition, not "already in flight".
**Fix:** Distinguish `tryAcquire` returning empty-because-unavailable from empty-because-contended
(e.g. return an `enum`/`Optional<LockHandle>` plus an `available` flag, or throw a dedicated
`LockBackendUnavailableException`) and map the unavailable case to a 503 / retryable error code with
a distinct message.

### WR-03: Frontend hard-codes a 30-day undo expiry that the backend does not confirm

**File:** `apps/web/features/triage/api/triage-api.ts:71-76,110`
**Issue:** `mapAuditEntry` sets `undoableUntil: addDays(timestamp, 30)`. The backend response
(`AuditEntryResponse`) carries no undo-expiry field, so the UI invents one. If the real server-side
undo window differs (the `TriageUndoExpiredException` path implies a bounded window that may not be
30 days, and may differ per action type), the UI will offer an "Undo" affordance that the API rejects
with `error.triage.undo.expired`, or hide one that is still valid.
**Fix:** Expose the authoritative `undoableUntil` (or `undoWindowSeconds`) on `AuditEntryResponse`
from the backend and use it, instead of guessing client-side. If a backend change is out of scope,
at minimum derive the constant from a single shared source and document the coupling.

### WR-04: `NeedsReplyRow` local `draftStatus` state never re-syncs with refreshed props

**File:** `apps/web/features/needs-reply/components/NeedsReplyRow.tsx:33,41,49`
**Issue:** The row holds `draftStatus` in `useState(row.draftStatus)` and only ever advances it via
`onDraftReady` → `'DRAFT_READY'`. After `useGenerateDraft` invalidates `needsReplyKeys.all` and the
list refetches, the row component is reused (keyed by `gmailThreadId`), so the new `row.draftStatus`
prop is ignored — there is no `useEffect` syncing prop → state. If the server-side classification
ends up disagreeing (e.g. the draft was actually rejected, or the bucket flipped to `DRAFT_SENT`),
the badge stays stuck on the optimistic value until a full remount.
**Fix:** Drop the local state and render `row.draftStatus` directly (the query is already the source
of truth and is invalidated on success), or sync with `useEffect(() => setDraftStatus(row.draftStatus), [row.draftStatus])`.

### WR-05: `getToReplyCount` triggers a full inbox page + Gmail display fetch just to read a count

**File:** `apps/web/features/needs-reply/api/needs-reply-api.ts:137-144`; `backend/api/.../NeedsReplyInboxController.java:43-62`
**Issue:** `getToReplyCount()` calls `getNeedsReplyInbox({ bucket: 'to-reply', limit: 1 })`, which
on the backend runs the keyset query *and* `gmailPreviewReadService.fetchThreadDisplays(...)` for the
single returned row (a live Gmail batch round-trip with a 3-second budget) — purely to read
`toReplyCount` off the envelope. The sidebar badge (`AppSidebar` → `useToReplyCount`) thus issues a
Gmail call on every app load. There is no dedicated count endpoint even though the backend already
has `NeedsReplyInboxQueryService.toReplyCount(...)`.
**Fix:** Add a lightweight `GET /api/threads/to-reply-count` (or include the count on `/me`) backed
by `NeedsReplyInboxQueryService.toReplyCount`, and have `useToReplyCount` call that instead of a
1-row inbox page.

### WR-06: `ToneContextBuilder` per-message "fetch budget" is not enforced as an HTTP timeout

**File:** `backend/core/src/main/java/com/zeromail/core/draft/usecases/ToneContextBuilder.java:222-261`
**Issue:** `FETCH_BUDGET` (2s) is only checked via `assertWithinBudget(deadline)` *between* Gmail
`messages().get(...)` calls. A single slow Gmail response (or the initial `messages().list`) can
block well past the budget, and since this runs inside the user-facing `POST /api/threads/{id}/draft`
request (`DraftBodyGenerator.generateWithTenantBound`), it directly extends draft latency under Gmail
slowness. `assertWithinBudget` cannot interrupt an in-flight call.
**Fix:** Configure a connect/read timeout on the Gmail HTTP transport used for tone fetching (or run
the tone fetch with a bounded executor and `Future.get(timeout)`), so a single hung call is capped.

### WR-07: `GmailDeliveryProcessingService.processDelivery` catches `Exception` broadly and treats decryption / NPE as a retryable Gmail failure

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java:118-137,74-81`
**Issue:** The outer `catch (Exception processingException)` funnels *everything* not explicitly
matched (including a `NullPointerException` from `connection.getRefreshTokenEncrypted()` being null,
a `RefreshTokenCipher` decrypt failure, or a programming error) into `handleRetryableFailure`, which
silently retries up to 3 times then marks the delivery `DEAD` — with no log of the exception class
(`handleRetryableFailure` only logs `event=gmail_delivery_retry`/`dead` with attempt count). A
permanently broken connection row will burn three retries and disappear with no diagnostic. Also note
the broad `catch (Exception)` swallows the exception object entirely.
**Fix:** Log `processingException.getClass().getSimpleName()` (class only — privacy rule) in
`handleRetryableFailure` / the catch block, and separate clearly non-retryable causes (null/blank
encrypted token, decrypt failure) into a fast `DEAD` path instead of three pointless retries.

### WR-08: `AuditLogQueryService` resolved/non-resolved index mismatch and `NeedsReplyInboxQueryService` resolved-only query bypasses the composite index prefix

**File:** `backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java:40-58`; `backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml:82-87`
**Issue:** `idx_thread_reply_status_inbox` is `(tenant_id, bucket, resolved, last_classified_at DESC NULLS LAST, gmail_thread_id DESC)`. The `resolvedOnly` branch of `page(...)` queries
`where tenant_id = ? and resolved = true order by last_classified_at desc nulls last, gmail_thread_id desc`
— it skips `bucket`, so Postgres cannot use the ordering portion of that index for the resolved tab
and will fall back to a sort. (Performance is out of v1 review scope, but this is a concrete index
design / query mismatch worth recording for when the resolved tab ships.)
**Fix:** Either add a dedicated `(tenant_id, resolved, last_classified_at DESC NULLS LAST, gmail_thread_id DESC)`
index for the resolved view, or reorder the composite index columns so the resolved-only query can
still use the ordering prefix.

## Info

### IN-01: `messages.ts` ships duplicate string keys for the same text

**File:** `apps/web/features/needs-reply/messages.ts:122-133,150-157`
**Issue:** `needsReply.toast.draftSaved` / `needsReply.toast.draftFailed` and
`errors.draft.generation.in_flight` / `errors.draft.generation.failed` carry essentially the same
copy as `needsReply.notice.draftInFlight` and the toast strings. Redundant keys drift apart over
time.
**Fix:** Consolidate to one key per message and reference it from both the toast and the error map.

### IN-02: Sidebar needs-reply badge has no accessible label

**File:** `apps/web/components/shell/AppSidebar.tsx:114-118`
**Issue:** `SidebarMenuBadge` renders the bare count with no `aria-label` (e.g. "3 threads need a
reply"), so screen readers announce just a number adjacent to the nav link.
**Fix:** Add `aria-label={t('nav.needsReply') + ': ' + visibleToReplyCount}` (or a dedicated plural
key) to the badge.

### IN-03: `getShadowMode` is a stub returning a fixed `false` default (documented GAP)

**File:** `apps/web/features/triage/api/triage-api.ts:148-153`
**Issue:** Already flagged in-code as a `// GAP`: there is no shadow-mode read endpoint, so the UI
starts from `enabled: false` regardless of server state. If a user has shadow mode on, a fresh page
load will show it off until they toggle it. Recording for tracking; the comment is honest about it.
**Fix:** Add a shadow-mode read endpoint (or include the flag on `/me`) and drop the stub.

### IN-04: `NeedsReplyInboxController.list` re-reads `toReplyCount` on every page fetch

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java:60-61`
**Issue:** `needsReplyInboxQueryService.toReplyCount(tenantId)` runs on *every* `/api/threads` call,
including each "load more" page within an `useInfiniteQuery`, even though the frontend only consumes
`toReplyCount` from page 0 (`latestToReplyCount` reads `data.pages[0]`). Cheap (partial-index count)
but unnecessary repetition.
**Fix:** Either accept it as negligible, or only compute the count when `cursor` is absent (first
page) and return `null`/omit it otherwise.

---

_Reviewed: 2026-05-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
