# Phase 5B: User Surface — AI Draft Replies - Context

**Gathered:** 2026-05-13
**Status:** Ready for planning

<domain>
## Phase Boundary

When the triage engine decides a `save_draft` action (and on explicit user request), Zero Mail generates an AI reply draft — tone-matched from the user's recent sent mail using in-request features only — and saves it in Gmail as a normal draft inside the correct thread with valid `In-Reply-To` and `References` headers, never auto-sent; review/edit/send happen entirely in Gmail. `apps/web` gains a two-bucket "needs-reply" inbox (to-reply / awaiting-their-reply) plus a "Draft reply / Regenerate draft" action on both that inbox and the triage audit-log rows, and the `GET /api/triage/audit` list endpoint that 5A left as a gap is built here.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**7 requirements are locked.** See `05B-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `05B-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- Threading-header (`In-Reply-To` / `References`) population for all Zero-Mail-created reply drafts, including retrofitting the existing triage `save_draft` path
- AI reply-draft generation via the existing Spring AI `LlmGateway` (extending the `save_draft` tool/prompt as needed)
- Tone matching from a bounded set of recent sent messages fetched in-request; privacy hardening of that context
- `GET /api/triage/audit` paginated list endpoint (closing the 5A gap) exposing thread/message/draft identifiers
- Backend endpoint to generate/regenerate a reply draft for a thread on demand
- `apps/web`: "Draft reply / Regenerate draft" action on triage audit-log rows and on needs-reply inbox rows
- `apps/web`: two-bucket needs-reply inbox view (`to-reply`, `awaiting-their-reply`) with per-thread draft status and "open in Gmail" links
- Backend per-thread reply-status classification + metadata-only persistence to power that inbox

**Out of scope (from SPEC.md):**
- In-app draft preview / edit / send — review, edit, and send happen entirely in Gmail
- Auto-send of any kind, including "auto-send after the user approves once"
- Reply reminders / follow-up sequences — deferred to backlog
- Bulk "draft all" across multiple threads — deferred to backlog
- Snooze / archive / other thread-management actions from the needs-reply inbox — deferred to backlog
- Persisted embeddings or any vector store of user mail
- Analytics screen and daily digest — Phase 5C

</spec_lock>

<decisions>
## Implementation Decisions

### Threading headers & reply-target
- **D-01:** Set `threadId` **and** `In-Reply-To` + `References` + a `Re:`-prefixed `Subject` (only prefix if not already present) + `To` on the outgoing MIME message. `threadId` alone is insufficient — Gmail docs require the RFC headers for reliable threading, for the sent copy, and for non-Gmail/IMAP clients. `setThreadId` stays as defense-in-depth.
- **D-02:** Reply-target = the inbound message that triage is acting on (the message Pub/Sub surfaced) — already non-self by construction. `In-Reply-To` = that message's `Message-ID`; `References` = original `References` header (if any) + that `Message-ID`. Do NOT walk the thread with `threads.get` to find "most recent non-self" message in v1 (over-engineering for an edge case ingestion mostly prevents).
- **D-03:** Source the needed headers (`Message-ID`, `References`, `Subject`, `From`) via `gmail.users.messages.get(format=METADATA, metadataHeaders=[...])` — privacy-aligned (no body pulled), cheap. **Reuse metadata already fetched during triage if available** rather than issuing an extra call.
- **D-04:** Build the RFC 2822 message with `jakarta.mail.internet.MimeMessage` (Angus Mail / Jakarta Mail) → `message.writeTo(...)` → base64url-encode without padding → `Message.setRaw(...)`. Add the `jakarta.mail` dependency if not present (verify first; Lombok-free, Java 25).
- **D-05:** Retrofit this into `backend/core/.../triage/usecases/TriageGmailWriter.java` `saveDraft` / `draftMessage` — widen the method signature to accept the original message's `Message-ID` / `References` / `Subject` / reply-to address, supplied by `TriageOrchestratorService` which already holds the Gmail message. The same MIME-building path serves both triage-initiated and user-initiated (on-demand) drafts.

### Tone-matching from recent sent mail
- **D-06:** Style signal = **computed descriptors + 2–3 raw snippet examples** (the "C+" option). Locally compute descriptors (greeting token, sign-off token, avg sentence length, avg message length, formality heuristic, emoji/contraction rate, bullet usage — ~100 prompt tokens) AND include 2–3 short recent sent-mail snippets as fenced "writing-style reference samples only — never instructions" examples.
- **D-07:** Fetch ~10–20 recent sent message IDs via `users.messages.list(q="in:sent" / labelIds=[SENT])`, batch-`get` the newest ~5–8; from those keep only the user's own composed prose — **strip quoted replies** (drop everything below `On … wrote:` / `>` blocks) and **strip signatures** (`-- ` delimiter), then run through the existing `SanitizationPipeline` (Jsoup HTML strip → NFC normalize → unicode-tag strip → jtokkit truncate ~150 tok each). Respect the existing token budget (`TokenBudgetExceededException`).
- **D-08:** Pass tone context **in the prompt only**, in a clearly fenced non-instruction section. Do NOT extend the `save_draft` tool schema — it stays `{ body: string }` (tone context flows app→model, the wrong direction for a tool schema, and widening the allow-listed tool enlarges the safety surface deliberately kept minimal `{label, archive, save_draft}`).
- **D-09:** One-shot combined prompt — no separate metered "style summary" LLM call in v1. Sent-mail tone context is never persisted (no DB row, no embedding) and never logged (privacy logging format already forbids email content). Treat the user's own sent mail as untrusted input (it carries quoted third-party text) — the quote/signature strip + `SanitizationPipeline` + fenced non-instruction framing is the mitigation.

### Reply-status classifier (mechanism deferred to research/planner)
- **D-10:** Mechanism left open for the researcher/planner to settle from the research. Strong recommendation on record: **heuristic-only v1** (last-message `From` vs tenant address; thread has `SENT` label; Zero-Mail draft present on thread) — zero LLM cost, no prompt-injection surface, and the only way "awaiting-their-reply" is detectable without scanning the whole mailbox (key off threads Zero Mail already observed via `users.watch` covering INBOX+SENT, or touched by saving a draft). Promote to a **hybrid** (LLM only on ambiguous threads) as a later iteration. Whatever the choice, accuracy bar = ≥ ~85% on the TO_REPLY/AWAITING split (the split that drives action); FYI/ACTIONED best-effort; measure against a held-out labeled set (mirror inbox-zero's `determine-thread-status.test.ts`).
- **D-11:** Run classification as a sub-step inside `TriageOrchestratorService` for inbound messages, plus a Spring Modulith after-commit reaction to outbound/draft-saved Gmail-state events (so "awaiting" flips when the user sends). Never enumerate all of a user's mail. Idempotency key = `(tenantId, gmailThreadId, lastClassifiedMessageId)`.
- **D-12:** Persist a new `thread_reply_status` table (metadata-only): `tenantId`, `gmailThreadId`, `bucket` (IdentifiedEnum: TO_REPLY / AWAITING_THEIR_REPLY [+ optional FYI/ACTIONED]), `lastClassifiedMessageId`, `lastClassifiedAt`, `hasDraft`, `draftId`, `resolved` (bool, for user "Mark resolved"); unique `(tenantId, gmailThreadId)`; partial index `WHERE bucket='TO_REPLY' AND NOT resolved`. Liquibase YAML changelog. Clean up on account deletion (Modulith account-deleted reaction). No bodies/prompts/completions stored.

### Backend API surface (detailed DTO shapes deferred to planner)
- **D-13:** `GET /api/triage/audit` — **cursor-based pagination** (opaque base64 keyset over `(createdAt, auditId)`; `?limit=&cursor=&action=&since=&until=`; response `{ items: [...], nextCursor }`), implemented on the Spring Data JDBC read side per the project's "cursor for hot paths" / CQRS-lite convention. NOT page/offset (`OFFSET` drift + `COUNT(*)` on an append-only growing table + convention violation). Response item fields: `auditId`, `gmailThreadId`, `gmailMessageId`, `ruleName`, `action`, `reason`, `decisionState`, `createdAt`, `draftId` (= `externalRef`, nullable, only meaningful when `action=save_draft`). No free-text search in 5B. `from(...)` mapper on the response record.
- **D-14:** On-demand draft trigger = **`POST /api/threads/{gmailThreadId}/draft`** (thread-keyed sub-resource, new `controllers/thread/` or `controllers/draft/` package per convention). One endpoint serves both the audit-log row (supplies its `gmailThreadId`) and the needs-reply inbox row. NOT `POST /api/triage/audit/{auditId}/draft` (inbox rows have no `auditId`) and NOT `POST /api/drafts` (implies multiplicity the domain forbids — exactly one Zero-Mail draft per thread).
- **D-15:** Regenerate semantics = **delete-then-recreate**, never `users.drafts.update`, never `users.drafts.send` (SPEC req 5). Flow: look up the thread's existing draft state for the tenant → if it has a `draftId`, call `TriageGmailWriter.deleteDraft(draftId)` (already 404-idempotent, so a stale id is harmless) → generate new body via `LlmGateway` → `saveDraft(...)` with threading headers → persist the new `draftId`. Guarantees at most one Zero-Mail draft per thread, mirroring the existing PENDING→APPLIED audit saga.
- **D-16:** `users.drafts.create` is not idempotent — guard the request with a per-`(tenantId, gmailThreadId)` lock in Redis (`SETNX` + short TTL; Redis is already the idempotency/rate-limit store) so a double-clicked "Regenerate" can't race two `drafts.create` calls and orphan one. If the lock is held, return `409` (or the in-flight result). Response = `{ draftId, gmailThreadId, status (GENERATED/REGENERATED), openInGmailUrl }` — no draft body returned (privacy + "review only in Gmail").
- **D-17:** The needs-reply inbox is served from the `thread_reply_status` projection (cursor-paginated, same convention as D-13), e.g. `GET /api/threads?bucket=to-reply&cursor=&limit=` — NOT derived from `TriageAuditEntity` (the audit table is action-log-shaped and can't represent un-triaged "awaiting" threads). Inbox display fields (subject, participants, last-activity time) are fetched live from Gmail `threads.get` (metadata format) keyed by `threadId` so no extra metadata is persisted.

### Needs-reply inbox UX (apps/web)
- **D-18:** Layout = raw shadcn `Tabs` — "To reply" / "Awaiting reply" (+ optional "Resolved"), count badge on each trigger. New top-level sidebar item ("Needs reply") in the Phase 5A authenticated app shell, with a TO_REPLY count badge (the product's "inbox zero progress" signal, fed by the partial-index count query). No custom wrapper components — reuse the existing `features/triage` `AuditTable`-style table shape; new code under `features/needs-reply/` with TanStack Query key factory + per-use-case hooks.
- **D-19:** Each row shows: subject, the other party (not self), relative last-activity time, draft-status badge (`No draft` / `Draft ready` / `Draft sent`), an "Open in Gmail" external deep link (`https://mail.google.com/mail/u/0/#all/<threadId>`), the action button (`Draft reply` when no draft → `Regenerate draft` when a draft exists), and a secondary `Mark resolved` (X icon). Keeping body rendering in Gmail is intentional — good for privacy and OAuth scope.
- **D-20:** States: loading = shadcn `Skeleton` rows; empty (TO_REPLY) = "Inbox zero 🎉 — nothing needs a reply"; empty (AWAITING) = "Nothing awaiting"; error = shadcn `Alert` (destructive) + retry; a "classifying…" banner if a backfill/recompute is in flight. Responsive to 320px: single-column rows, participants + time collapse to a second line, action button + Gmail link become icon-only, tabs horizontally scrollable (`useIsMobile`/CSS branch). Use the `frontend-design` skill when building this UI.

### Claude's Discretion
- Reply-status classifier mechanism (heuristic-only vs hybrid) — researcher/planner picks from the research; recommendation is heuristic-only v1 (D-10).
- Exact DTO field names, package names (`controllers/thread/` vs `controllers/draft/`), cursor codec details, Liquibase changelog id — planner decides within the conventions above.
- Whether `thread_reply_status` includes the optional FYI / ACTIONED buckets in v1 or only the two action-driving buckets.
- Exact descriptor list and snippet count (within the ~3 snippets / ~5–8 fetched / ~100-token budget envelope).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/phases/05B-user-surface-ai-draft-replies/05B-SPEC.md` — locked requirements, boundaries, acceptance criteria. MUST read before planning.

### Project-level
- `.planning/PROJECT.md` — product, core value (trust/safety), constraints
- `.planning/REQUIREMENTS.md` — DRFT-01..04, WEB-02 (draft-review portion)
- `.planning/ROADMAP.md` §"Phase 5B" — goal, depends-on, success criteria
- `CLAUDE.md` — language/runtime/framework locks, backend code style, hard "do not use" list, conventions (thin controllers, domain package layout, records-not-Lombok, enum state machines, privacy logging format, direct calls vs Modulith events, shadcn-first)
- `CONVENTIONS.md` — detailed examples for the conventions above
- `.planning/research/STACK.md`, `.planning/research/ARCHITECTURE.md` — stack/arch background

### Prior-phase context (depends-on)
- `.planning/phases/05A-user-surface-web-ui-core/05A-CONTEXT.md` + `05A-SPEC.md` + `05A-GAPS.md` — app shell, `features/triage` patterns, the missing `GET /api/triage/audit` gap this phase closes
- `.planning/phases/04-triage-convergence-hero/04-CONTEXT.md` — triage orchestrator, audit model, safety policy / auto-send block, `TriageGmailWriter`, `TriageAuditSaga`
- `.planning/phases/02C-llm-gateway/02C-CONTEXT.md` — `LlmGateway`, `AllowListedTools`, `SanitizationPipeline`, Spring AI adapter isolation, token budget
- `.planning/phases/01.6-brand-identity-design-tokens-and-landing-page/` — design tokens used by the app shell

### External reference repo (read-only, on disk)
- `D:\study materials summer 2026\EXE202\inbox-zero` — Reply Zero reference implementation. Specifically: `apps/web/utils/ai/reply/determine-thread-status.ts`, `apps/web/utils/reply-tracker/handle-outbound.ts`, `apps/web/utils/reply-tracker/draft-tracking.ts`, `apps/web/prisma/migrations/20250202092329_reply_tracker/migration.sql` (the `ThreadTracker` schema), `apps/web/app/(app)/[emailAccountId]/reply-zero/page.tsx` + `ReplyTrackerEmails.tsx` (tabbed UX). Inspiration only — Zero Mail re-implements in Java/Spring with its own privacy posture (no bodies persisted).

### Key source files to modify/extend
- `backend/core/.../triage/usecases/TriageGmailWriter.java` — `saveDraft` / `draftMessage` (retrofit threading headers + MimeMessage)
- `backend/core/.../triage/usecases/TriageOrchestratorService.java` — supplies original-message headers; hosts the reply-status classification sub-step
- `backend/core/.../triage/usecases/TriageAuditSaga.java` / `TriageAuditEntity` — draft state lifecycle
- `backend/core/.../llm/usecases/LlmGateway.java`, `backend/core/.../llm/domain/AllowListedTools.java`, `backend/core/.../llm/gateway/sanitization/SanitizationPipeline.java`
- `backend/api/.../controllers/triage/TriageAuditController.java`, `backend/api/.../dto/triage/` — audit-list endpoint + DTOs
- `apps/web/features/triage/` (existing `AuditLog`/`AuditTable`/`UndoButton`), new `apps/web/features/needs-reply/`

### Gmail API docs
- Gmail API: Manage threads — https://developers.google.com/workspace/gmail/api/guides/threads (threading header requirements)
- Gmail API: `users.messages.get` + Format — https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/get , https://developers.google.com/workspace/gmail/api/reference/rest/v1/Format
- Gmail API: batch requests & quota — https://developers.google.com/gmail/api/guides/batch , https://developers.google.com/workspace/gmail/api/reference/quota

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TriageGmailWriter.saveDraft()` / `deleteDraft()` — draft create (returns draftId) + 404-idempotent delete; the delete-then-recreate regenerate path is built on these.
- `LlmGateway.chat(callSite, rawHtml)` + `AllowListedTools` (`save_draft` = `{body}`) + Spring AI adapter under `core.llm.gateway.springai` — the only sanctioned model path; reuse for draft generation (new call site) and any classifier LLM use.
- `SanitizationPipeline` (Jsoup strip → NFC → unicode-tag strip → jtokkit truncate) + `TokenBudgetExceededException` — reuse for sent-mail tone snippets.
- `TriageAuditEntity` (`gmailMessageId`, `gmailThreadId`, `actionArgsJson` JSONB, `externalRef`=draftId, decision states) + `TriageAuditSaga` (PENDING→APPLIED) — pattern for draft state lifecycle; the new endpoint reuses this shape.
- Phase 5A app shell + `features/triage` `AuditLog`/`AuditTable`/`UndoButton` + persistent chrome — the needs-reply inbox reuses the table shape and adds a sidebar item; the audit-list page becomes live once `GET /api/triage/audit` exists.
- Redis (Spring Data Redis + Lettuce) — already the idempotency/rate-limit store; reuse `SETNX`+TTL for the per-(tenant,thread) draft lock.
- Spring Modulith events + Postgres outbox/processing_job — event mechanism for outbound/draft-saved → reclassify; cross-process (api↔worker) handoff stays on Postgres tables.

### Established Patterns
- Thin controllers, service-owned `@Transactional`, response DTOs own `from(...)`; controllers grouped `controllers/<domain>/`, DTOs `dto/<domain>/`.
- CQRS-lite: Spring Data JPA for writes, Spring Data JDBC for reads/hot paths (the audit-list + inbox queries are read-side JDBC).
- Enum state machines via `IdentifiedEnum` + static `fromId` fail-loud; never `ordinal()` for storage.
- Privacy logging: `event=<name> tenantId={}` + structured fields; no email content/addresses/Google subject/token bytes/bodies/prompts/completions.
- Direct service calls for transaction-critical commands; Modulith events for after-commit side effects (reclassification fits the latter).
- Liquibase YAML changelogs for all schema changes (the `thread_reply_status` table).
- Frontend: shadcn primitives first (`pnpm dlx shadcn@latest add ...`), raw primitives not custom wrappers; feature folders own `api/`, `query-keys.ts`, one hook per use case; Playwright specs in `apps/web/e2e/**`; `frontend-design` skill before writing UI.
- Subproject-owned config: worker-only props in `backend/worker/.../application.yml`, api-only in `backend/api/.../application.yml`.

### Integration Points
- `TriageOrchestratorService` → `TriageGmailWriter.saveDraft` (now passes original-message headers) and → new reply-status classification sub-step writing `thread_reply_status`.
- New `POST /api/threads/{threadId}/draft` controller → `GenerateThreadDraftService` use-case → `LlmGateway` + `TriageGmailWriter` (delete-then-recreate) + Redis lock + audit/draft-state persistence.
- New `GET /api/triage/audit` controller → read-side JDBC query service over `triage_audit`.
- New needs-reply inbox endpoint(s) → read-side query over `thread_reply_status` + live Gmail `threads.get` for display fields.
- `apps/web` new sidebar nav item + `features/needs-reply/` consuming the typed OpenAPI client (regenerate codegen after the new endpoints land).
- Account-deletion cleanup must also purge `thread_reply_status` rows.

</code_context>

<specifics>
## Specific Ideas

- The user explicitly framed drafts as **primarily automatic** (triggered when a rule/triage action decides `save_draft`), with the manual "Draft reply / Regenerate draft" as a **fallback** — the needs-reply inbox is the place users *review* the existence/status of drafts, then open Gmail to actually read/edit/send.
- The needs-reply inbox is explicitly a **two-bucket** view: "to-reply" (you owe a reply) + "awaiting-their-reply" (waiting on the other party) — modeled on Inbox Zero's Reply Zero.
- Review/edit/send is **100% in Gmail** — no in-app preview, no `users.drafts.update`, no send. This is a deliberate, locked simplification that keeps DRFT-04 airtight.

</specifics>

<deferred>
## Deferred Ideas

- Reply reminders / follow-up sequences ("remind me if no reply in N days") — its own future phase / backlog.
- Bulk "draft all" across multiple threads — backlog.
- Snooze / archive / other thread-management actions from the needs-reply inbox — backlog.
- In-app draft preview + edit (would require `users.drafts.update`) — out of v1 by user decision; revisit only if "review in Gmail" proves insufficient.
- Cached LLM "style summary" per tenant (Option D from tone research) — revisit if telemetry shows high drafts-per-tenant making per-draft snippet cost material.
- LLM-based reply-status classification beyond the ambiguous-thread hybrid — revisit after v1 heuristic accuracy is measured.

</deferred>

---

*Phase: 5B-user-surface-ai-draft-replies*
*Context gathered: 2026-05-13*
