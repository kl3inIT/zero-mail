---
phase: 05B-user-surface-ai-draft-replies
plan: 05
type: execute
wave: 5
depends_on: ["05B-03", "05B-04"]
files_modified:
  - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditListResponse.java
  - backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/thread/package-info.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/ThreadDraftResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyListResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/thread/package-info.java
  - backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/lib/api/schema.d.ts
autonomous: true
requirements: [DRFT-02, DRFT-04]
must_haves:
  truths:
    - "GET /api/triage/audit returns a cursor-paginated list of audit entries including threadId / messageId / draftId, tenant-scoped"
    - "POST /api/threads/{gmailThreadId}/draft generates (or delete-then-recreates) a Gmail draft for that thread and returns {draftId, gmailThreadId, status, openInGmailUrl} — no draft body"
    - "A second concurrent draft request for the same thread returns HTTP 409"
    - "POST /api/threads/{gmailThreadId}/resolve flips the thread to resolved"
    - "GET /api/threads?bucket=...&cursor=&limit= returns the cursor-paginated needs-reply rows with live Gmail display fields"
    - "The new endpoints appear in the springdoc OpenAPI surface; apps/web schema.d.ts is regenerated"
    - "No 5B controller calls users.drafts.send or users.drafts.update; no draft body or email content in any response or error payload"
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java"
      provides: "GET /api/triage/audit added alongside the existing POST .../{auditId}/undo"
      contains: "/api/triage/audit"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java"
      provides: "POST /api/threads/{gmailThreadId}/draft + POST .../resolve"
      contains: "/api/threads"
    - path: "backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java"
      provides: "DraftGenerationInFlightException → 409, DraftGenerationFailedException/threading → 422 DRAFT_GENERATION_FAILED, malformed cursor / unknown bucket → 400, SafetyViolationException → 422 LLM_SAFETY_VIOLATION"
  key_links:
    - from: "backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java"
      to: "AuditLogQueryService (core.triage.projection)"
      via: "ctor-injected query service; from(...) on the response record"
      pattern: "AuditLogQueryService"
    - from: "backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java"
      to: "GenerateThreadDraftService / NeedsReplyInboxQueryService / MarkThreadResolvedService"
      via: "ctor injection; TenantContext.currentOrThrow() for the tenant"
      pattern: "GenerateThreadDraftService"
    - from: "apps/web/lib/api/schema.d.ts"
      to: "springdoc OpenAPI output"
      via: "pnpm --filter web generate:api after the endpoints land"
      pattern: "/api/triage/audit|/api/threads"
---

<objective>
Wire the Plan 03/04 services to REST: add `GET /api/triage/audit` to `TriageAuditController` (closing the 5A gap), create two `@RequestMapping("/api/threads")` controllers — `ThreadDraftController` (`POST /api/threads/{gmailThreadId}/draft`, `POST /api/threads/{gmailThreadId}/resolve`) and `NeedsReplyInboxController` (`GET /api/threads?bucket=&cursor=&limit=`, with live Gmail `threads.get(metadata)` per row batched in ONE `BatchRequest` for the display fields) — the DTO records (records with `from(...)`), the `GlobalExceptionHandler` + `ErrorCodes` branches (409 in-flight, 422 for threading/generation failure, 422 `LLM_SAFETY_VIOLATION`, 400 for malformed cursor / unknown bucket), the vi/en i18n keys for the new error codes, and regenerate the `apps/web` OpenAPI typed client. Public bucket slugs are `to-reply` / `awaiting-their-reply` (hyphenated) — via `ThreadReplyBucket.fromPublicSlug(...)`.

Purpose: Closes the backend half of WEB-02's draft-review portion (and the `GET /api/triage/audit` gap from 5A). Thin controllers, service-owned `@Transactional`, `from(...)` DTOs, privacy-logging format, no `drafts.send`/`drafts.update`.
Output: 2 new controllers (+1 modified), 5 DTO records, 2 package-infos, `GlobalExceptionHandler`/`ErrorCodes` edits, vi/en i18n, regenerated `schema.d.ts`.
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
@.planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
@backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
@backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
@backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java
</context>

<interfaces>
<!-- Read the actual files. Key contracts: -->

`TriageAuditController` (backend/api, existing): `@RestController @Tag(name="triage")`; has `POST /api/triage/audit/{auditId}/undo` → `UndoAuditResponse.from(triageUndoService.undo(...))`; `currentTenantId()` = `UUID.fromString(TenantContext.currentOrThrow())`. Add `GET /api/triage/audit` here — ctor-inject `AuditLogQueryService` alongside `TriageUndoService`.

`BillingController` / `BillingBalanceResponse` (backend/api): the thin-controller + `record … { static from(...) }` DTO pattern; `dto/<domain>/package-info.java` per sub-package; controllers grouped `controllers/<domain>/`.

`SenderSafetyNetController` (backend/api/controllers/triage): a path-variable `POST` example.

Spring MVC routing fact (no hedge): two `@RestController` classes both annotated `@RequestMapping("/api/threads")` do NOT collide as long as their method+path mappings are disjoint — Spring matches on (HTTP method + path pattern), and `POST /api/threads/{id}/draft` / `POST /api/threads/{id}/resolve` (ThreadDraftController) vs `GET /api/threads` (NeedsReplyInboxController) are all distinct. Keep them as two controllers; do not merge.

`GlobalExceptionHandler` (backend/api/error): how domain exceptions map to status + a localizable error code (`ErrorCodes` constants + the `ApiError` body — no human-readable strings server-side; the frontend localizes via `next-intl`). Add branches: `DraftGenerationInFlightException` → 409 + `DRAFT_GENERATION_IN_FLIGHT`; `DraftGenerationFailedException` (and `ThreadingHeaderInvalidException`/`MissingMessageIdException` if they reach here) → 422 + `DRAFT_GENERATION_FAILED` (no content in the payload — the issue is the request/inbound state, not server health, so 422 not 500; a client retry is pointless either way); a malformed cursor → 400 + `INVALID_CURSOR` (scope this narrowly — via a typed `InvalidCursorException` thrown at the controller boundary when `KeysetCursor.decode`/`UUID.fromString` fails, NOT by widening the existing generic `IllegalArgumentException` mapping); `SafetyViolationException` → **422 + `LLM_SAFETY_VIOLATION`** (the model produced an unsafe response — a request-level fault, not a server fault; 500 would falsely signal "server having a bad day" and could trigger client-side retry of a request guaranteed to fail again).

`GenerateThreadDraftService.generateOrRegenerate(GenerateThreadDraftCommand)` → `GenerateThreadDraftResult(draftId, gmailThreadId, status, openInGmailUrl)` (Plan 03). `AuditLogQueryService.page(UUID, AuditLogPageQuery)` → `AuditLogPage(items, nextCursor)` (Plan 04). `NeedsReplyInboxQueryService.page(UUID, NeedsReplyPageQuery)` → `NeedsReplyPage`; `.toReplyCount(UUID)` → long. `MarkThreadResolvedService.markResolved(UUID, String)`.

`GmailPreviewReadService` (core.gmail) — the `threads.get(format=METADATA)` shape for fetching a thread's subject / participants / last-activity time live per needs-reply row. Add a small read method there if one doesn't fit, or have the controller call the Gmail client via the existing factory — keep Gmail-read concerns out of `backend/api` proper if the project pattern forbids it (it likely does — add the method to `core.gmail.usecases`/`core.thread.projection` and call it from the controller).
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: GET /api/triage/audit + audit DTOs</name>
  <files>backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java, backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java, backend/api/src/main/java/com/zeromail/api/dto/triage/AuditListResponse.java</files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java (current shape — extend it)
    - backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java + dto/billing/package-info.java (record + `from(...)` + package-info pattern)
    - backend/api/src/main/java/com/zeromail/api/dto/triage/*.java (existing triage DTOs — match the package + naming)
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java + AuditLogRow.java + AuditLogPage.java + AuditLogPageQuery.java (Plan 04)
    - backend/api/src/test/java/.../TriageAuditControllerContractTest.java + AuditLogPaginationTest.java + AuditLogMultiTenantLeakTest.java (the RED tests)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-13; 05B-UI-SPEC.md §9
  </read_first>
  <action>
    Add `@GetMapping("/api/triage/audit")` to `TriageAuditController`: params `@RequestParam(defaultValue="50") int limit`, `@RequestParam(required=false) String cursor`, `@RequestParam(required=false) String action`, `@RequestParam(required=false) Instant since`, `@RequestParam(required=false) Instant until`; build `AuditLogPageQuery`, call `auditLogQueryService.page(currentTenantId(), query)`, return `AuditListResponse.from(page)`. Ctor-inject `AuditLogQueryService` alongside the existing `TriageUndoService`. Log `event=triage_audit_listed tenantId={} limit={}` only. Create `AuditEntryResponse` (record: `auditId`, `gmailThreadId`, `gmailMessageId`, `ruleName`, `action`, `reason`, `decisionState`, `createdAt`, `draftId`) with `static AuditEntryResponse from(AuditLogRow row)`, and `AuditListResponse` (record: `items: List<AuditEntryResponse>`, `nextCursor: String`) with `static from(AuditLogPage page)` mapping rows. Keep the controller thin (no repository injection); the query service owns `@Transactional(readOnly=true)`.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:api:test --tests "*TriageAuditController*" --tests "*AuditLogPagination*" --tests "*AuditLogMultiTenantLeak*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `GET /api/triage/audit` returns `{ items: [...], nextCursor }`; each item has `auditId`, `gmailThreadId`, `gmailMessageId`, `ruleName`, `action`, `reason`, `decisionState`, `createdAt`, `draftId`; `draftId` is non-null only on `save_draft` rows
    - `nextCursor` round-trips to the next page; passing a malformed `cursor` → HTTP 400 (handled in Task 2)
    - Tenant A's `GET /api/triage/audit` returns zero of tenant B's rows
    - Controller injects no repository; `@Tag(name="triage")` present so springdoc emits it
    - `mcp__jetbrains__get_file_problems` on the touched files clean
  </acceptance_criteria>
  <done>The 5A audit-list gap is closed; the now-live endpoint is in the OpenAPI surface (regen in Task 3).</done>
</task>

<task type="auto">
  <name>Task 2: ThreadDraftController + NeedsReplyInboxController + thread DTOs + error mapping</name>
  <files>backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java, backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java, backend/api/src/main/java/com/zeromail/api/controllers/thread/package-info.java, backend/api/src/main/java/com/zeromail/api/dto/thread/ThreadDraftResponse.java, backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyRowResponse.java, backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyListResponse.java, backend/api/src/main/java/com/zeromail/api/dto/thread/package-info.java, backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java, backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java</files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java + controllers/triage/SenderSafetyNetController.java (thin controller + path-var POST + `@RequestMapping("/api/...")` + `@Tag`)
    - backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java + error/ErrorCodes.java + error/ApiError* (how a domain exception → status + error code + localizable params; the existing generic `IllegalArgumentException` handler — do NOT widen it)
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java + GenerateThreadDraftCommand.java + GenerateThreadDraftResult.java + exception/*.java (Plan 03)
    - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java + NeedsReplyPageQuery.java + NeedsReplyRow.java (Plan 04) + core.thread.usecases.MarkThreadResolvedService
    - backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java (Plan 04 — `decode` throws `IllegalArgumentException` on malformed input; wrap that in a typed `InvalidCursorException` at the controller boundary)
    - backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java + exception/{MissingMessageIdException,ThreadingHeaderInvalidException}.java (Plan 01)
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java (the `threads.get(format=METADATA)` shape for live display fields)
    - backend/api/src/test/java/.../ThreadDraftControllerContractTest.java + DraftLockContentionTest.java (the RED tests)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-14, D-15, D-16, D-17, D-19; 05B-RESEARCH.md Open Question 4 (use `controllers/thread/`, `dto/thread/`)
  </read_first>
  <action>
    Create `controllers/thread/package-info.java` + `dto/thread/package-info.java` (mirror the billing sub-package shape; add `@NamedInterface` re-exposure if the project requires it). Build TWO controllers, both `@RequestMapping("/api/threads")` (this does not collide — see the routing fact in `<interfaces>`):
    - `ThreadDraftController` — `@RestController @Tag(name="thread-draft") @RequestMapping("/api/threads")`, ctor-inject `GenerateThreadDraftService` + `MarkThreadResolvedService`: `@PostMapping("/{gmailThreadId}/draft")` → `ThreadDraftResponse.from(generateThreadDraftService.generateOrRegenerate(new GenerateThreadDraftCommand(currentTenantId(), gmailThreadId)))`; `@PostMapping("/{gmailThreadId}/resolve")` → `markThreadResolvedService.markResolved(currentTenantId(), gmailThreadId)`, return 204 (or the updated row). Log `event=thread_draft_requested tenantId={} gmailThreadId={} status={}` / `event=thread_marked_resolved tenantId={} gmailThreadId={}` only.
    - `NeedsReplyInboxController` — `@RestController @Tag(name="thread-inbox") @RequestMapping("/api/threads")`, ctor-inject `NeedsReplyInboxQueryService` + the core.gmail thread-metadata read method: `@GetMapping` (maps `GET /api/threads`) with `@RequestParam String bucket`, `@RequestParam(required=false) String cursor`, `@RequestParam(defaultValue="50") int limit` (clamped 1..100), `@RequestParam(required=false, defaultValue="false") boolean resolved` → build `NeedsReplyPageQuery` (`ThreadReplyBucket.fromPublicSlug(bucket)` — the hyphenated public slug `to-reply` / `awaiting-their-reply`, case-insensitive; unknown → 400 `IllegalArgumentException`-typed → mapped, NOT 500), `page(...)`, then for the (≤100) rows fetch thread metadata in ONE `BatchRequest` of `threads.get(format=METADATA)` sub-requests (subject / participants-minus-self / last-activity) with a small `Duration` fetch budget — reuse `GmailPreviewReadService`'s `BatchRequest`/`JsonBatchCallback` helpers; a per-row fetch that fails or times out yields a degraded row (`subject=null`, `otherParty=null`, `lastActivityAt=null` — the ids/draftStatus/resolved still render); assemble `NeedsReplyRowResponse[]`, return `NeedsReplyListResponse`. **Quota note (carry into the SUMMARY + file a follow-up issue):** a `limit=50` page = ~50 Gmail quota units per inbox load (one `BatchRequest` HTTP call, ~50 sub-requests); plus ToneContextBuilder's ~5-8 units per draft generation. Within the free-tier budget for v1 scale but MUST be monitored post-launch; a short-TTL (1-5 min) in-memory cache of thread-metadata per `(tenantId, gmailThreadId)` is the documented optimization if it becomes a problem (deferred, not in v1 scope).
    DTOs (records + `from(...)`): `ThreadDraftResponse(String draftId, String gmailThreadId, String status, String openInGmailUrl)` — NO body field; `NeedsReplyRowResponse(String gmailThreadId, String subject, String otherParty, Instant lastActivityAt, String draftStatus /* NO_DRAFT | DRAFT_READY | DRAFT_SENT */, boolean resolved, String openInGmailUrl)` (`openInGmailUrl = "https://mail.google.com/mail/u/0/#all/" + gmailThreadId`; `draftStatus` derived from `hasDraft` + bucket: `AWAITING_THEIR_REPLY` → `DRAFT_SENT` if a draft exists, else `NO_DRAFT`; `TO_REPLY` with a draft → `DRAFT_READY`, without → `NO_DRAFT`); `NeedsReplyListResponse(List<NeedsReplyRowResponse> items, String nextCursor, long toReplyCount)`.
    `GlobalExceptionHandler`/`ErrorCodes`: add `DRAFT_GENERATION_IN_FLIGHT` (409 ← `DraftGenerationInFlightException`), `DRAFT_GENERATION_FAILED` (422 ← `DraftGenerationFailedException` and `MissingMessageIdException`/`ThreadingHeaderInvalidException` if they reach here), `INVALID_CURSOR` (400 ← a typed `InvalidCursorException` thrown at the controller boundary when `KeysetCursor.decode`/`UUID.fromString` fails — do NOT broaden the generic `IllegalArgumentException` handler), and map `LLM_SAFETY_VIOLATION` (422 ← `SafetyViolationException`). No email content / draft body / stack trace in any `ApiError` body.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:api:test --tests "*ThreadDraftController*" --tests "*DraftLockContention*" --tests "*GlobalExceptionHandler*" 2>&1 | tail -12</automated>
  </verify>
  <acceptance_criteria>
    - `POST /api/threads/{gmailThreadId}/draft` returns `{ draftId, gmailThreadId, status, openInGmailUrl }` (no body field) and a Gmail draft for that thread exists afterward (asserted via the stubbed Gmail client in the contract test)
    - A second concurrent `POST .../draft` while the Redis lock is held → HTTP 409 with error code `DRAFT_GENERATION_IN_FLIGHT`
    - `POST /api/threads/{gmailThreadId}/resolve` flips the row to `resolved` and returns 204
    - `GET /api/threads?bucket=to-reply` (the hyphenated public slug, case-insensitive; `awaiting-their-reply` likewise) returns `{ items: [...], nextCursor, toReplyCount }` with each item carrying subject/otherParty/lastActivityAt/draftStatus/resolved/openInGmailUrl fetched live from Gmail via a single `BatchRequest` (a per-row fetch failure → degraded row, ids still present); an unknown `bucket` → 400 (not 500)
    - Both `/api/threads` controllers load without an ambiguous-mapping error (asserted by the Spring context starting in the contract test); `ThreadDraftController` (`@Tag("thread-draft")`) owns the two POSTs, `NeedsReplyInboxController` (`@Tag("thread-inbox")`) owns the GET
    - Malformed `cursor` → 400 `INVALID_CURSOR` (via `InvalidCursorException`, not the generic IAE handler); a `DraftGenerationFailedException`/threading failure → 422 `DRAFT_GENERATION_FAILED` with no content in the payload; a `SafetyViolationException` → 422 `LLM_SAFETY_VIOLATION`
    - `grep -rn "drafts().send\|drafts().update" backend/api/src/main` returns nothing; no draft body in any response or error payload
    - `mcp__jetbrains__get_file_problems` on all new/touched `backend/api` files clean
  </acceptance_criteria>
  <done>On-demand draft + needs-reply inbox + mark-resolved REST endpoints land (two disjoint `/api/threads` controllers) with clean error mapping; no auto-send, no body leakage.</done>
</task>

<task type="auto">
  <name>Task 3: i18n keys for the new error codes + regenerate the apps/web OpenAPI client</name>
  <files>apps/web/i18n/messages/en.json, apps/web/i18n/messages/vi.json, apps/web/lib/api/schema.d.ts</files>
  <read_first>
    - apps/web/i18n/messages/en.json + vi.json (the existing `error.*` (or `errors.*`) namespace where backend error codes are localized — match the existing key shape, e.g. `error.triage.*` / `errors.<code>`)
    - apps/web/scripts/generate-api.ts (the OpenAPI codegen entry — reads `openapi/openapi.json` or hits the springdoc emit; how prior phases regenerated `schema.d.ts`)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Frontend — i18n analog" + the STATE.md notes on the springdoc Gradle emit port (use the existing hermetic emit task; do not require localhost:8080)
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java (the exact code strings to localize: `DRAFT_GENERATION_IN_FLIGHT`, `DRAFT_GENERATION_FAILED`, `INVALID_CURSOR`, `LLM_SAFETY_VIOLATION` if newly added)
  </read_first>
  <action>
    Add localized messages for the new error codes to both `apps/web/i18n/messages/en.json` and `vi.json` lock-step (same keys, both languages; Vietnamese is the default rendering) under the existing backend-error namespace — wording per UI-SPEC §7/§8 tone ("A draft is already being generated for this thread.", "Couldn't generate a draft. Try again in a moment.", a generic "Something went wrong." for `INVALID_CURSOR`/`LLM_SAFETY_VIOLATION` since those aren't user-facing copy points). Run the OpenAPI regen: `pnpm --filter web generate:api` (or the project's actual script) to refresh `apps/web/lib/api/schema.d.ts` with `/api/triage/audit`, `/api/threads/{gmailThreadId}/draft`, `/api/threads/{gmailThreadId}/resolve`, and `GET /api/threads`. Run `pnpm -C apps/web i18n:check` (must pass) and `pnpm -C apps/web tsc --noEmit` (must pass).
  </action>
  <verify>
    <automated>cd "$REPO/apps/web" && pnpm i18n:check && pnpm tsc --noEmit 2>&1 | tail -5 && grep -E "/api/triage/audit|/api/threads/\{gmailThreadId\}/draft|/api/threads" lib/api/schema.d.ts | head</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/i18n/messages/{en,vi}.json` both contain the new error-code keys (lock-step); `pnpm i18n:check` passes
    - `apps/web/lib/api/schema.d.ts` includes paths `/api/triage/audit` (GET), `/api/threads/{gmailThreadId}/draft` (POST), `/api/threads/{gmailThreadId}/resolve` (POST), `/api/threads` (GET) with the new response shapes
    - `pnpm -C apps/web tsc --noEmit` passes
  </acceptance_criteria>
  <done>Frontend has the typed contract for the new endpoints + localized error copy; Plan 06 builds the UI on it.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| HTTP client → new REST endpoints | untrusted query/path params (`gmailThreadId`, `cursor`, `limit`, `bucket`, `action`, `since`, `until`); session-derived tenant |
| controller → Gmail `threads.get` (live display fields) | per-row Gmail read for the inbox |
| error responses → client | must not carry email content / draft body / stack traces |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-05-01 | Spoofing / Information Disclosure | cross-tenant access via the new endpoints | mitigate | Every endpoint derives the tenant from `TenantContext.currentOrThrow()` (session, not a request param); the underlying queries are `where tenant_id = ?`; multi-tenant-leak contract tests on `GET /api/triage/audit` and the inbox endpoint |
| T-05B-05-02 | Tampering | malformed `cursor` / out-of-range `limit` / unknown `bucket` causing errors or unbounded scans | mitigate | `KeysetCursor.decode` fail-loud → wrapped in `InvalidCursorException` → 400 `INVALID_CURSOR`; `limit` clamped 1..100; `ThreadReplyBucket.fromPublicSlug(...)` throws on unknown → 400 (not 500); all keyset predicates parameterized |
| T-05B-05-03 | Tampering / Elevation of Privilege | a new Gmail-write call site (`drafts.send`/`drafts.update`) or auto-send sneaking into a controller | mitigate | Controllers only call `GenerateThreadDraftService`/`MarkThreadResolvedService`/query services — no Gmail-write API in `backend/api`; `grep` gate; `DraftPathArchUnitTest` (Plan 03) enforces the structural invariant downstream |
| T-05B-05-04 | Tampering (resource leak) | double-clicked `POST .../draft` orphaning a draft | mitigate | The service's per-`(tenantId, gmailThreadId)` Redis lock (Plan 03) → `DraftGenerationInFlightException` → HTTP 409 `DRAFT_GENERATION_IN_FLIGHT`; `DraftLockContentionTest` asserts it |
| T-05B-05-05 | Information Disclosure | email content / draft body / Google subject / token bytes / stack traces in a response or error payload | mitigate | `ThreadDraftResponse` has no body field; `NeedsReplyRowResponse` carries only subject/other-party/time/status (the subject is shown by design in the inbox UI per UI-SPEC — display-only, never persisted); `ApiError` body carries error codes + safe params only (no raw text/stack); privacy-logging format on the controllers; `mcp__jetbrains__get_file_problems` + code review |
| T-05B-05-06 | Denial of Service | per-row Gmail `threads.get` fan-out on a large inbox page | mitigate | `limit` clamped (≤100); use the `BatchRequest` pattern + a `Duration` fetch budget (reuse `GmailPreviewReadService`'s); the projection query is keyset-bounded so the page size is fixed |
| T-05B-05-07 | Tampering | new error-code mapping accidentally broadening the existing `IllegalArgumentException` → 400 mapping | mitigate | `INVALID_CURSOR` is wired via a typed `InvalidCursorException` thrown at the controller boundary, NOT by widening the generic IAE handler; `GlobalExceptionHandler` tests verify the existing 400 behaviors are unchanged |
</threat_model>

<verification>
- `./gradlew :backend:api:test --tests "*TriageAuditController*" --tests "*ThreadDraftController*" --tests "*DraftLockContention*" --tests "*AuditLogMultiTenantLeak*" --tests "*GlobalExceptionHandler*"` all green
- `./gradlew clean check` (backend) green
- `grep -rn "drafts().send\|drafts().update" backend/api/src/main` returns nothing
- `pnpm -C apps/web i18n:check` + `pnpm -C apps/web tsc --noEmit` pass; `schema.d.ts` has the four new paths
- `mcp__jetbrains__get_file_problems` on all new/touched `backend/api` files — no problems
</verification>

<success_criteria>
`GET /api/triage/audit` (cursor-paginated, threadId/messageId/draftId), `POST /api/threads/{gmailThreadId}/draft` (delete-then-recreate, 409 on contention, no body returned), `POST .../resolve`, and `GET /api/threads?bucket=...` (cursor-paginated, live Gmail display fields, `toReplyCount` badge) are live in the OpenAPI surface as two disjoint `/api/threads` controllers; vi/en error copy added; `apps/web` typed client regenerated. The backend half of WEB-02's draft-review portion is done.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-05-SUMMARY.md`
</output>
