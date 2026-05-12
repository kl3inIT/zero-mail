# Phase 5B: User Surface — AI Draft Replies - Pattern Map

**Mapped:** 2026-05-13
**Files analyzed:** ~22 new/modified units (backend/core, backend/api, apps/web, Liquibase)
**Analogs found:** 20 / 22 (2 partial — Redis lock util, on-demand draft service have no exact analog)

> Java 25 / Spring Boot 4, `backend/core` (package-modular: `domain/` `application/`(==`usecases/`) `projection/` `persistence/` `exception/`) + `backend/api` (`controllers/<domain>/`, `dto/<domain>/`) + `backend/worker`. Frontend Next.js 16 / React 19 in `apps/web`. The inbox-zero TypeScript clone is conceptual reference only — every excerpt below is from this repo.

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `core/triage/usecases/TriageGmailWriter.java` (modify — widen `saveDraft`/`draftMessage` for threading headers + `MimeMessage`) | service (Gmail-write boundary) | request-response (Gmail API) | itself (already exists) | exact |
| `core/triage/usecases/TriageOrchestratorService.java` (modify — supply original-message headers; host classify sub-step) | service (orchestrator) | event-driven (inbound msg) | itself | exact |
| `core/draft/application/GenerateThreadDraftService.java` (new) | service (use-case) | request-response (transform: LLM→Gmail) | `core/billing/usecases/BillingTopupService.java` + `TriageUndoService.java` (delete-then-recreate saga shape mirrors `TriageAuditSaga`) | role-match |
| `core/draft/application/ToneContextBuilder.java` (new) | service (Gmail read + sanitize) | batch / transform | `core/gmail/usecases/GmailPreviewReadService.java` (list/batch-get + budget + sanitize) | exact |
| `core/draft/domain/ToneContext.java`, `ReplyHeaders.java`, `GeneratedDraft.java` (new records) | model (value object) | — | `GmailPreviewReadService.GmailPreviewMessage` record; `core/llm/usecases/ToolCallResult.java` | exact |
| `core/draft/persistence/...` (new — persist `draftId` on triage-audit-shaped row) | persistence | CRUD | `core/triage/persistence/TriageAuditEntity.java` + `TriageAuditWriter.java` | exact |
| `core/thread/domain/ThreadReplyBucket.java` (new `IdentifiedEnum`) | model (enum state machine) | — | `core/billing/domain/CallSite.java`, `core/triage/domain/TriageDecision.java` | exact |
| `core/thread/persistence/ThreadReplyStatusEntity.java` + `...Repository.java` (new) | model / persistence | CRUD | `core/triage/persistence/TriageAuditEntity.java` (`@Entity` + `AbstractAuditableEntity`) | exact |
| `core/thread/application/ClassifyThreadReplyStatusService.java` (new — heuristic) | service (use-case) | event-driven (sub-step + Modulith reaction) | `core/triage/usecases/TriageOrchestratorService.java` (sub-step pattern); `core/triage/usecases/SenderSafetyNetService.java` (heuristic over Gmail labels) | role-match |
| `core/thread/projection/...AuditLogQueryService.java` / `NeedsReplyInboxQueryService.java` (new — JDBC cursor reads) | service (read-side) | CRUD (read) | `core/gmail/usecases/GmailPreviewReadService.java` (`JdbcTemplate.query` + `RowMapper`) | role-match |
| `core/triage/projection/...` audit-list read service (new) | service (read-side) | CRUD (read) | same as above | role-match |
| `db/changelog/changes/0XX-thread-reply-status.yaml` (new) | migration | — | `db/changelog/changes/025-triage-audit.yaml` (createTable + `createIndex` + partial `CREATE INDEX ... WHERE`) | exact |
| `api/controllers/triage/TriageAuditController.java` (modify — add `GET /api/triage/audit`) | controller | request-response (read) | `api/controllers/billing/BillingController.java` (GET) + itself | exact |
| `api/controllers/thread/ThreadDraftController.java` (new — `POST /api/threads/{gmailThreadId}/draft`, `POST .../resolve`, `GET /api/threads?bucket=`) | controller | request-response | `api/controllers/billing/BillingController.java`; `api/controllers/triage/SenderSafetyNetController.java` (path-var POST) | role-match |
| `api/dto/triage/AuditEntryResponse.java` + `AuditListResponse.java` (new records w/ `from(...)`) | model (DTO) | — | `api/dto/billing/BillingBalanceResponse.java` (`record` + static `from`) | exact |
| `api/dto/thread/ThreadDraftResponse.java`, `NeedsReplyRowResponse.java` (new) | model (DTO) | — | same | exact |
| Redis `SETNX` per-(tenant,thread) draft lock (inside `GenerateThreadDraftService` or a small helper) | utility | — | `core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java` (lock-token wrapper shape); **no Redis lock analog exists** | partial |
| `apps/web/features/needs-reply/api/needs-reply-api.ts` (new) | utility (HTTP client) | request-response | `apps/web/features/triage/api/triage-api.ts` | exact |
| `apps/web/features/needs-reply/query-keys.ts` (new) | utility (query key factory) | — | `apps/web/features/triage/query-keys.ts` | exact |
| `apps/web/features/needs-reply/hooks/useNeedsReplyInbox.ts`, `useGenerateDraft.ts`, `useMarkResolved.ts` (new) | hook | — | `apps/web/features/triage/hooks/useTriageAuditLog.ts` (infinite query / cursor), `useUndoAuditEntry.ts` (mutation + invalidate) | exact |
| `apps/web/features/needs-reply/components/NeedsReplyTable.tsx`, `NeedsReplyPageClient.tsx`, row/tab components (new) | component | — | `apps/web/features/triage/components/AuditTable.tsx`, `AuditRow.tsx`, `TriagePageClient.tsx`, `UndoButton.tsx` | exact |
| `apps/web/app/(protected)/(app)/needs-reply/page.tsx` (new) + sidebar nav item in `components/shell/AppSidebar.tsx` (modify) | route / config | — | `apps/web/app/(protected)/(app)/triage/page.tsx`; existing `AppSidebar.tsx` nav items | exact |
| `apps/web/features/triage/api/triage-api.ts` + `useTriageAuditLog.ts` (modify — remove the `unavailable` GAP sentinel, wire `GET /api/triage/audit`) | utility / hook | request-response | itself | exact |
| `apps/web/i18n/messages/{vi,en}.json` + `features/needs-reply/messages.ts` + `features/triage/messages.ts` (modify) | config (i18n) | — | `apps/web/features/triage/messages.ts` (feature i18n merge pattern) | exact |

---

## Pattern Assignments

### `core/triage/usecases/TriageGmailWriter.java` (service, Gmail-write boundary) — MODIFY

**Analog:** itself. Widen `saveDraft(...)` and `draftMessage(...)` to take `ReplyHeaders` (inbound `Message-ID`, `References`, `Subject`, reply-to address). Replace the hand-rolled MIME string with `jakarta.mail.internet.MimeMessage`.

**Current draft-build to replace** (lines 81-102, 234-246):
```java
public String saveDraft(UUID tenantId, TriageActionResult.SaveDraft draftSpec, String gmailThreadId) throws IOException {
    return executeGmailWrite(tenantId, "saveDraft", gmail -> {
        Draft createdDraft = gmail.users().drafts()
            .create(USER_ID, new Draft().setMessage(draftMessage(draftSpec.instruction(), gmailThreadId)))
            .execute();
        logThreadWrite(tenantId, gmailThreadId);
        return createdDraft.getId();
    });
}
// ...
private static Message draftMessage(String instruction, String gmailThreadId) {
    String rawMimeMessage = "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n"
        + "Content-Transfer-Encoding: 8bit\r\n\r\n" + instruction;
    String encodedMimeMessage = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(rawMimeMessage.getBytes(StandardCharsets.UTF_8));
    return new Message().setThreadId(gmailThreadId).setRaw(encodedMimeMessage);
}
```
New `draftMessage` builds `jakarta.mail.internet.MimeMessage` (from `Session.getInstance(new Properties())`): `setFrom`, `setRecipients(TO, ...)`, `setSubject("Re: ..." )` (prefix only if not already `Re:`), `setHeader("In-Reply-To", ...)`, `setHeader("References", ...)`, `setText(body, "UTF-8")`, then `ByteArrayOutputStream` → `message.writeTo(...)` → `Base64.getUrlEncoder().withoutPadding().encodeToString(...)` → `new Message().setThreadId(gmailThreadId).setRaw(...)`. Keep `setThreadId` as defense-in-depth (D-01).

**Reuse verbatim:** `executeGmailWrite(...)` wrapper (lines 167-184) — same `GoogleJsonResponseException`/`IOException` catch + `event=triage_gmail_write_failed tenantId={} op={}` log. `deleteDraft(...)` (lines 140-165) is already 404-idempotent — the regenerate path calls it as-is.

**Error handling pattern** (lines 167-184): catch `GoogleJsonResponseException` → `log.warn("event=triage_gmail_write_failed tenantId={} op={} status={}", ...)` → rethrow; catch `IOException` → `log.warn("event=triage_gmail_write_failed tenantId={} op={}", ...)` → rethrow. No email content, no body, no `draftId` bytes in any log line.

**Threading-header sourcing:** reuse the metadata-fetch shape from `GmailPreviewReadService.triageMessageGetRequest` (lines 314-324): `gmail.users().messages().get("me", id).setFormat("metadata").setFields("id,threadId,labelIds,internalDate,payload/headers").setMetadataHeaders(List.of("From","To","Cc","Subject", ...))`. Add `Message-ID`, `References` to the metadata-headers list. **Prefer reusing metadata already held by `TriageOrchestratorService`** (D-03) — only call `messages.get` when not already in hand.

---

### `core/draft/application/ToneContextBuilder.java` (service, Gmail read + sanitize) — NEW

**Analog:** `core/gmail/usecases/GmailPreviewReadService.java`.

**Class shape to copy** (lines 39-95): `@Service`; constructor-injected `JdbcTemplate` (if reading observed messages), `GmailConnectionRepository`, `GmailApiClientFactory`, `RefreshTokenCipher`, and an overloaded package-private ctor taking `Clock` for tests. `@Transactional(readOnly = true)` on the public read method.

**Batch-get pattern to copy** (lines 243-298): build `gmail.users().messages().list("me").setLabelIds(List.of("SENT")).setMaxResults(...)`, then `BatchRequest batchRequest = gmail.batch()` with a `JsonBatchCallback<Message>` (`onSuccess` puts into a `LinkedHashMap`, `onFailure` silently drops), `request.queue(batchRequest, callback)` per id, `batchRequest.execute()`; fall back to sequential `.execute()` on batch failure (lines 224-238). Honour a `Duration` fetch budget via `assertWithinBudget(deadline)` (lines 515-519, 222).

**Sanitize hook:** after stripping quoted replies (drop everything at/below `On … wrote:` / leading `>` blocks) and signatures (`-- ` delimiter), run each snippet through the existing `SanitizationPipeline` bean (`core/llm/gateway/sanitization/SanitizationPipeline.java` — Jsoup HTML strip → NFC normalize → unicode-tag strip → jtokkit truncate ~150 tok). On `TokenBudgetExceededException` (`core/llm/exception/`), degrade to descriptors-only — do not fail the draft.

**Result type:** a `record ToneContext(String descriptors, List<String> styleSnippets)` in `core/draft/domain/` — mirror the validated-record pattern of `GmailPreviewMessage` (compact ctor with `Objects.requireNonNull` + `List.copyOf`, lines 546-575).

**Privacy:** never persist `ToneContext`, never log snippet text — log only `event=tone_context_built tenantId={} snippetCount={}`.

---

### `core/draft/application/GenerateThreadDraftService.java` (service, use-case) — NEW

**Analogs:** `core/billing/usecases/BillingTopupService.java` (thin `@Service`, `@Transactional` on write methods, returns DTO-shaped result), `core/triage/usecases/TriageUndoService.java` (command/result records), `core/triage/usecases/TriageAuditSaga.java` (PENDING→APPLIED state lifecycle the delete-then-recreate path mirrors).

**Flow (D-15/D-16):** acquire Redis `SETNX` lock on key `draft:lock:{tenantId}:{gmailThreadId}` with short TTL → if held, throw a 409-mapped exception; → look up existing `draftId` for `(tenantId, gmailThreadId)` → if present, `triageGmailWriter.deleteDraft(tenantId, draftId)` (404-idempotent) → build tone context via `ToneContextBuilder` → sanitize inbound (`SanitizationPipeline`) → `llmGateway.chat(CallSite.DRAFT, sanitizedInbound)` — **reuse the existing `CallSite.DRAFT` (cost 2)**; do NOT add a new enum value (`core/billing/domain/CallSite.java` already has `DRAFT(2)`) — pass tone context in the prompt only, the `save_draft` tool stays `{body}` (D-08) → on `ToolCallResult{action=save_draft, args{body}}` → `triageGmailWriter.saveDraft(tenantId, replyHeaders+body, gmailThreadId)` → persist new `draftId` on the triage-audit-shaped row (`TriageAuditWriter` pattern) → upsert `thread_reply_status` (`hasDraft=true`, `draftId`, bucket). Return `record GenerateThreadDraftResult(String draftId, String gmailThreadId, DraftStatus status, String openInGmailUrl)` — no body in the result.

**Command/result record pattern** — copy `core/triage/usecases/UndoAuditCommand.java` / `UndoAuditResult.java` shape.

**LLM tool-call options** (system prompt + temp/maxTokens/toolChoice) live inside `core.llm.gateway.springai` — do NOT touch them here; call only `LlmGateway.chat(...)`. ArchUnit isolates Spring AI usage.

---

### `core/thread/persistence/ThreadReplyStatusEntity.java` + Liquibase changelog — NEW

**Entity analog:** `core/triage/persistence/TriageAuditEntity.java` — `class` (not record), extends `AbstractAuditableEntity` (`core/shared/persistence/`), `@Entity`, `@Table`, `@Id` UUID, `@Version`, getters/setters, no Lombok. Bucket column stored as the `IdentifiedEnum` id string (varchar) via attribute converter — see `core/llm/persistence/BYOKProviderAttributeConverter.java` for the converter pattern, and `core/triage/domain/TriageDecision.java` for an `IdentifiedEnum` with a CHECK-constraint-backed varchar column.

**Changelog analog:** `db/changelog/changes/025-triage-audit.yaml`:
```yaml
databaseChangeLog:
  - changeSet:
      id: 0XX-thread-reply-status
      author: zeromail
      comment: >
        Metadata-only thread reply-status projection. No email bodies, prompts, or completions.
      changes:
        - createTable:
            tableName: thread_reply_status
            columns:
              - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_thread_reply_status_tenant, references: tenants(id), deleteCascade: true }
              - column: { name: gmail_thread_id, type: varchar(255), constraints: { nullable: false } }
              - column: { name: bucket, type: varchar(32), constraints: { nullable: false } }
              - column: { name: last_classified_message_id, type: varchar(255) }
              - column: { name: last_classified_at, type: timestamptz }
              - column: { name: has_draft, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: draft_id, type: varchar(255) }
              - column: { name: resolved, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
              # + created_at / updated_at / version like 025
        - sql: { sql: "ALTER TABLE thread_reply_status ADD CONSTRAINT ck_thread_reply_status_bucket CHECK (bucket IN ('TO_REPLY','AWAITING_THEIR_REPLY','FYI','ACTIONED'))" }
        - sql: { sql: "CREATE UNIQUE INDEX ux_thread_reply_status_tenant_thread ON thread_reply_status (tenant_id, gmail_thread_id)" }
        - sql: { comment: "Partial index for the inbox-zero TO_REPLY count badge.", sql: "CREATE INDEX idx_thread_reply_status_to_reply ON thread_reply_status (tenant_id) WHERE bucket = 'TO_REPLY' AND NOT resolved" }
      rollback:
        - dropTable: { tableName: thread_reply_status }
```
(The `NULLS NOT DISTINCT` unique-index trick and `CREATE INDEX ... WHERE` partial-index sql blocks are both demonstrated in `025-triage-audit.yaml` lines 124-148.)

---

### `core/thread/application/ClassifyThreadReplyStatusService.java` (heuristic classifier) — NEW

**Analogs:** `core/triage/usecases/TriageOrchestratorService.java` (invoke as a sub-step on the inbound-message path; supplies tenant + Gmail message), `core/triage/usecases/SenderSafetyNetService.java` (heuristic over Gmail label ids / sender canonicalization), Spring Modulith `@ApplicationModuleListener` after-commit reactions (see `core/gmail/event/MailMessageObserved.java` + its handlers for the event-shape; conventions doc rule #6).

**Heuristic v1 (D-10):** bucket = `AWAITING_THEIR_REPLY` if last message on the thread is `From` the tenant address AND thread carries `SENT`; else `TO_REPLY`. `hasDraft` if a Zero-Mail `draftId` exists for the thread. Idempotency key `(tenantId, gmailThreadId, lastClassifiedMessageId)` — skip the upsert if unchanged. Never enumerate the mailbox — key only off threads already observed via `users.watch` (INBOX+SENT) or touched by saving a draft. Log `event=thread_reply_classified tenantId={} bucket={}` only.

**Account-deletion cleanup:** add a `thread_reply_status` purge to the existing account-deletion path — see `core/account/usecases/AccountService.java` / `core/tenant/usecases/TenantService.java` (the FK `deleteCascade: true` in the changelog also covers it; add an explicit Modulith `AccountDeleted` reaction only if cascade is insufficient).

---

### `core/triage/projection/` & `core/thread/projection/` JDBC read services (cursor pagination) — NEW

**Analog:** `core/gmail/usecases/GmailPreviewReadService.findRecentObservedMessages` (lines 185-214).

**Pattern to copy:**
```java
return jdbcTemplate.query(
    """
    select audit_id, gmail_thread_id, gmail_message_id, rule_name_snapshot, action_type,
           reason, decision, external_ref, created_at
    from triage_audit
    where tenant_id = ?
      and (? is null or action_type = ?)
      and (? is null or created_at >= ?)
      and (created_at, audit_id) < (?, ?)        -- keyset over (created_at, audit_id) from decoded cursor
    order by created_at desc, audit_id desc
    limit ?
    """,
    (resultSet, rowNumber) -> /* map to AuditEntryRow record */,
    /* params */);
```
Cursor codec = opaque `base64(createdAt.toEpochMilli() + ":" + auditId)` — small private static encode/decode helpers in the read service. Read side uses `JdbcTemplate` directly (CQRS-lite convention; the existing `triage_audit` already has `idx_triage_audit_tenant_decided_at`). The needs-reply inbox query is the same shape over `thread_reply_status` filtered by `bucket` and `NOT resolved`; display fields (subject/participants/last-activity) are fetched live from Gmail `threads.get` (metadata) keyed by `threadId` per row — reuse the `GmailPreviewReadService` metadata-get shape, do not persist them.

---

### `api/controllers/triage/TriageAuditController.java` (add `GET /api/triage/audit`) — MODIFY

**Analog:** itself + `api/controllers/billing/BillingController.java`.

**Existing controller shape to extend:**
```java
@RestController
@Tag(name = "triage")
public class TriageAuditController {
    private static final Logger log = LoggerFactory.getLogger(TriageAuditController.class);
    private final TriageUndoService triageUndoService;       // + inject the new AuditLogQueryService
    public TriageAuditController(TriageUndoService triageUndoService) { ... }

    @PostMapping("/api/triage/audit/{auditId}/undo")
    public UndoAuditResponse undo(@PathVariable UUID auditId) {
        UUID tenantId = currentTenantId();
        UndoAuditResult result = triageUndoService.undo(new UndoAuditCommand(auditId, tenantId));
        log.info("event=triage_undo_requested tenantId={} auditId={}", tenantId, auditId);
        return UndoAuditResponse.from(result);
    }
    private static UUID currentTenantId() { return UUID.fromString(TenantContext.currentOrThrow()); }
}
```
Add:
```java
@GetMapping("/api/triage/audit")
public AuditListResponse audit(@RequestParam(defaultValue = "50") int limit,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) Instant since,
                               @RequestParam(required = false) Instant until) {
    UUID tenantId = currentTenantId();
    return AuditListResponse.from(auditLogQueryService.page(tenantId, limit, cursor, action, since, until));
}
```
Thin controller; service owns the query + `@Transactional(readOnly=true)`. `@Tag(name="triage")` for springdoc. `TenantContext.currentOrThrow()` for the tenant — never inject repositories into the controller (convention #1).

---

### `api/controllers/thread/ThreadDraftController.java` — NEW

**Analogs:** `api/controllers/billing/BillingController.java` (`@RestController` + `@RequestMapping("/api/...")` + `@Tag`), `api/controllers/triage/SenderSafetyNetController.java` (path-variable POST).

```java
@RestController
@Tag(name = "thread")
@RequestMapping("/api/threads")
public class ThreadDraftController {
    private final GenerateThreadDraftService generateThreadDraftService;
    private final NeedsReplyInboxQueryService needsReplyInboxQueryService;
    // ctor injection only

    @PostMapping("/{gmailThreadId}/draft")
    public ThreadDraftResponse draft(@PathVariable String gmailThreadId) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return ThreadDraftResponse.from(generateThreadDraftService.generateOrRegenerate(tenantId, gmailThreadId));
    }
    @PostMapping("/{gmailThreadId}/resolve")
    public NeedsReplyRowResponse resolve(@PathVariable String gmailThreadId) { ... }
    @GetMapping
    public NeedsReplyListResponse inbox(@RequestParam String bucket,
                                        @RequestParam(required=false) String cursor,
                                        @RequestParam(defaultValue="50") int limit) { ... }
}
```
The Redis-lock-held case throws an exception mapped to HTTP 409 by `api/config/GlobalExceptionHandler.java` (follow how existing domain exceptions there map to status codes — add a new branch). No draft body in any response.

---

### DTOs: `api/dto/triage/AuditEntryResponse.java`, `AuditListResponse.java`, `api/dto/thread/ThreadDraftResponse.java`, `NeedsReplyRowResponse.java` — NEW

**Analog:** `api/dto/billing/BillingBalanceResponse.java` — `record` + static `from(...)` mapper. Records-not-classes for DTOs (convention #3). `package-info.java` per dto subpackage (existing `dto/billing/package-info.java`). DTOs grouped under `dto/<domain>/` (`dto/triage/`, new `dto/thread/`).

```java
public record AuditListResponse(List<AuditEntryResponse> items, String nextCursor) {
    public static AuditListResponse from(AuditPage page) {
        return new AuditListResponse(page.rows().stream().map(AuditEntryResponse::from).toList(), page.nextCursor());
    }
}
public record ThreadDraftResponse(String draftId, String gmailThreadId, String status, String openInGmailUrl) {
    public static ThreadDraftResponse from(GenerateThreadDraftResult r) { return new ThreadDraftResponse(...); }
}
```

---

### Frontend — `apps/web/features/needs-reply/` — NEW

**API analog:** `apps/web/features/triage/api/triage-api.ts`. Copy the `import { api, xsrfHeader } from '@/lib/api/client'`, `import type { components } from '@/lib/api/schema'`, `jsonHeaders()` / `unsafeHeaders()` helpers, and the `unwrap<T>(result, fallbackMessage)` helper verbatim. Functions call `api.GET('/api/threads', { params: { query: { bucket, cursor, limit } } })`, `api.POST('/api/threads/{gmailThreadId}/draft', { params: { path: { gmailThreadId } }, headers: unsafeHeaders() })`, `api.POST('/api/threads/{gmailThreadId}/resolve', ...)`. **Regenerate the OpenAPI client (`pnpm --filter web generate:api` / `scripts/generate-api.ts`) after the backend endpoints land**, then drop the GAP sentinels in `triage-api.ts` (lines 75-81) and `useTriageAuditLog.ts` (lines 8-23) and wire `getAuditLog` to `api.GET('/api/triage/audit', ...)`.

**Query-keys analog:** `apps/web/features/triage/query-keys.ts`:
```ts
export const needsReplyKeys = {
  all: ['needs-reply'] as const,
  inbox: (bucket: string) => [...needsReplyKeys.all, 'inbox', bucket] as const,
  counts: () => [...needsReplyKeys.all, 'counts'] as const,
} as const;
```

**Infinite-query hook analog:** `useTriageAuditLog.ts` — `useInfiniteQuery({ queryKey, queryFn: ({pageParam}) => getInbox({bucket, cursor: pageParam}), initialPageParam: null as string|null, getNextPageParam: (last) => last.nextCursor ?? undefined })` + a `flatten...(data)` helper.

**Mutation hook analog:** `useUndoAuditEntry.ts`:
```ts
export function useGenerateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (gmailThreadId: string) => generateDraft(gmailThreadId),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: needsReplyKeys.all }); },
  });
}
```
Surface success/failure via `sonner` toast (UI-SPEC §7/§8 copy keys), 409 → inline amber notice.

**Components analog:** `apps/web/features/triage/components/AuditTable.tsx` (table-shaped, `bg-card rounded-lg border` wrapper, `Table`/`TableHeader`/`TableHead`/`TableRow`/`TableBody`/`TableCell` from `@/components/ui/table`, `useTranslations()` for column labels), `AuditRow.tsx` (per-row), `TriagePageClient.tsx` (`'use client'` page client wiring the query + states), `UndoButton.tsx` (per-row mutation button with loading state). Tabs = raw `@/components/ui/tabs`; states = `@/components/ui/skeleton`, `@/components/ui/alert`, plus the existing `components/states/{EmptyState,ErrorState,LoadingState}.tsx`. **No wrapper components** — raw shadcn primitives only (conventions #7, D-18).

**Route analog:** `apps/web/app/(protected)/(app)/triage/page.tsx` → new `apps/web/app/(protected)/(app)/needs-reply/page.tsx`. Add the "Needs reply" nav item (with `TO_REPLY` count badge, untinted/hidden at 0) to `apps/web/components/shell/AppSidebar.tsx` following the existing nav-item entries.

**i18n analog:** `apps/web/features/triage/messages.ts` (feature-local message merge) → new `features/needs-reply/messages.ts` under namespace `needsReply.*`; add `triage.*` keys for the new draft action + now-live audit list; mirror lock-step in `apps/web/i18n/messages/{vi,en}.json`; `pnpm i18n:check` must pass. Vietnamese is the default rendering.

---

## Shared Patterns

### Privacy logging
**Source:** every `core/...` service. Format: `log.info("event=<name> tenantId={} <structured fields>", tenantId, ...)` — see `TriageGmailWriter` (lines 248-262), `GmailPreviewReadService` (lines 162-165).
**Apply to:** `GenerateThreadDraftService`, `ToneContextBuilder`, `ClassifyThreadReplyStatusService`, all new controllers. **Never** log email bodies, addresses, subjects, Google subject, token bytes, `draftId` content beyond an id reference, prompts, or completions.

### Gmail-write boundary
**Source:** `core/triage/usecases/TriageGmailWriter.java` — the only triage class allowed to call Gmail write APIs (`TriageGmailWriteBoundaryTest` / `NoGmailSendAllowedTest` enforce it).
**Apply to:** all draft create/delete in this phase MUST go through `TriageGmailWriter`. Do NOT add `drafts.send` or `drafts.update` anywhere (DRFT-04).

### Single LLM chokepoint
**Source:** `core/llm/usecases/LlmGateway.java` — `chat(CallSite, String)`; Spring AI usage isolated to `core.llm.gateway.springai` (ArchUnit-enforced).
**Apply to:** `GenerateThreadDraftService` calls only `LlmGateway.chat(CallSite.DRAFT, sanitizedInbound)`. No raw HTTP, no vendor SDK, no widening the `{label, archive, save_draft}` tool allow-list. Tone context flows in the prompt only.

### Sanitization pipeline
**Source:** `core/llm/gateway/sanitization/SanitizationPipeline.java` (bean) + `core/llm/exception/TokenBudgetExceededException.java`.
**Apply to:** `ToneContextBuilder` (per sent-mail snippet, after quote/signature strip) and any inbound-message sanitize before the LLM call. On `TokenBudgetExceededException`, degrade — don't fail the draft.

### Thin controller + service-owned `@Transactional` + `from(...)` DTOs
**Source:** `api/controllers/billing/BillingController.java`, `api/dto/billing/BillingBalanceResponse.java`, `api/controllers/triage/TriageAuditController.java`.
**Apply to:** `TriageAuditController` (`GET`), new `ThreadDraftController`, all new `dto/triage/` + `dto/thread/` records. Controllers grouped `controllers/<domain>/`, DTOs `dto/<domain>/`. Tenant via `TenantContext.currentOrThrow()`.

### CQRS-lite read side
**Source:** `core/gmail/usecases/GmailPreviewReadService.findRecentObservedMessages` (`JdbcTemplate.query` + `RowMapper`).
**Apply to:** the `GET /api/triage/audit` query service and the needs-reply inbox query service — Spring Data JDBC, cursor/keyset pagination, no `OFFSET`, no `COUNT(*)` on growing tables.

### IdentifiedEnum + fail-loud `fromId`
**Source:** `core/billing/domain/CallSite.java`, `core/triage/domain/TriageDecision.java`.
**Apply to:** the new `ThreadReplyBucket` enum — `implements IdentifiedEnum`, `id()` = `name()`, static `fromId` throwing `NoSuchElementException`, stored as varchar id (never `ordinal()`), backed by a CHECK constraint in the changelog.

### Liquibase YAML changelog
**Source:** `db/changelog/changes/025-triage-audit.yaml` — `createTable` + `createIndex` + `sql:` for unique/partial indexes + `rollback: dropTable`.
**Apply to:** the new `0XX-thread-reply-status.yaml` changelog.

### Frontend feature folder
**Source:** `apps/web/features/triage/` — `api/<feature>-api.ts`, `query-keys.ts`, one hook per use case under `hooks/`, components under `components/`, `messages.ts` for i18n, raw shadcn primitives.
**Apply to:** new `apps/web/features/needs-reply/`. Playwright spec in `apps/web/e2e/needs-reply.spec.ts`; Vitest feature tests beside the feature code.

---

## No Analog Found

| File | Role | Data Flow | Reason / Mitigation |
|---|---|---|---|
| Redis `SETNX` per-(tenant,thread) draft lock | utility | — | No existing Redis-lock helper in the codebase (`spring-boot-starter-data-redis` is on the classpath per build files but no lock util). Planner should add a tiny helper (`StringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl)` → release by token compare) — shape mirrors `core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java` (lock-token wrapper) but over Redis. |
| `GenerateThreadDraftService` end-to-end (LLM→delete-then-recreate→persist→classify) | service (use-case) | transform/saga | No single existing service does exactly this; assembled from `BillingTopupService` (service shape) + `TriageAuditSaga` (delete-then-recreate / state lifecycle) + `LlmGateway` caller patterns in `core/triage/usecases/TriageOrchestratorService.java`. |
| `jakarta.mail.internet.MimeMessage` MIME build | (within `TriageGmailWriter`) | transform | The current `draftMessage` hand-rolls MIME — no `jakarta.mail` usage exists yet. New dependency `org.eclipse.angus:angus-mail` `2.0.4` + `jakarta.mail:jakarta.mail-api` `2.1.3` to add to `gradle/libs.versions.toml` per RESEARCH; standard `MimeMessage` API. |

---

## Metadata

**Analog search scope:** `backend/core/src/main/java/com/zeromail/core/{triage,llm,gmail,billing}/**`, `backend/api/src/main/java/com/zeromail/api/{controllers,dto}/**`, `backend/core/src/main/resources/db/changelog/changes/**`, `apps/web/features/triage/**`, `apps/web/components/{ui,shell,states}/**`, `apps/web/app/(protected)/**`.
**Files scanned (read in full):** `TriageGmailWriter.java`, `TriageAuditController.java`, `CallSite.java`, `025-triage-audit.yaml`, `GmailPreviewReadService.java`, `LlmGateway.java`, `BillingController.java`, `triage-api.ts`, `triage/query-keys.ts`, `useTriageAuditLog.ts`, `useUndoAuditEntry.ts`, `AuditTable.tsx`. Plus file-listing/grep recon across the modules above.
**Pattern extraction date:** 2026-05-13
</content>
</invoke>
