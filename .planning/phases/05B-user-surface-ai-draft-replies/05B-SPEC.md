# Phase 5B: User Surface — AI Draft Replies — Specification

**Created:** 2026-05-13
**Ambiguity score:** 0.19 (gate: ≤ 0.20)
**Requirements:** 7 locked

## Goal

When the triage engine decides a `save_draft` action (and on explicit user request), Zero Mail generates an AI reply draft — tone-matched from the user's recent sent mail using in-request features only — and saves it in Gmail as a normal draft inside the correct thread with valid `In-Reply-To` and `References` headers; the user reviews, edits, and sends entirely in Gmail (no code path auto-sends), and `apps/web` surfaces a two-bucket "needs-reply" inbox (to-reply / awaiting-their-reply) plus a "Draft reply / Regenerate draft" action on both that inbox and the triage audit-log rows.

## Background

- **Draft creation already works for triage:** `TriageGmailWriter.saveDraft()` (`backend/core/.../triage/usecases/TriageGmailWriter.java`) calls `gmail.users().drafts().create()` and stores the returned `draftId` in `TriageAuditEntity.externalRef`. **It does NOT set `In-Reply-To` / `References` headers today** — drafts may not thread correctly in Gmail. Idempotency is handled by the PENDING→APPLIED audit saga.
- **LLM gateway exists:** `LlmGateway.chat(callSite, rawHtml)` (`backend/core/.../llm/usecases/LlmGateway.java`) wraps Spring AI `ChatClient` with a fixed tool allow-list `{label, archive, save_draft}`. The `save_draft` tool schema carries only a `body` field — no tone/style inputs. All direct Spring AI usage is isolated under `core.llm.gateway.springai`.
- **Audit model carries thread identity:** `TriageAuditEntity` has `gmailMessageId` (non-null), `gmailThreadId` (nullable), `actionArgsJson` (JSONB), `externalRef` (draftId). `TriageActionResult.SaveDraft(instruction, draftId, threadId)`.
- **5A web shell exists, audit-list endpoint does not:** `apps/web/features/triage/` has `AuditLog`, `AuditTable`, `UndoButton`, etc., but the backend GET `/api/triage/audit` list endpoint is a known 5A gap (`05A-GAPS.md`) — `getAuditLog()` currently returns `{ unavailable: true }`. Only `POST /api/triage/audit/{auditId}/undo` exists.
- **No tone/style or sent-mail-retrieval code exists.** No "needs-reply" / Reply-Zero classification exists.
- This phase depends on Phase 5A (UI shell), Phase 2C (LLM gateway), Phase 4 (triage + audit thread surface).

## Requirements

1. **Threaded Gmail draft (headers)**: A saved reply draft appears inside the original Gmail thread with valid threading headers.
   - Current: `TriageGmailWriter.saveDraft()` creates a draft but sets no `In-Reply-To` / `References` headers; threading is not guaranteed.
   - Target: Every reply draft created by Zero Mail (triage-initiated or user-initiated) sets `In-Reply-To` and `References` derived from the thread's RFC822 `Message-ID` headers. The chosen reply-target message and exact header-chain construction are a discuss-phase decision.
   - Acceptance: For a known multi-message test thread, the created draft (a) is returned by `users.threads.get` as part of that same `threadId`, and (b) has an `In-Reply-To` header equal to a `Message-ID` present in the thread and a `References` header containing the thread's prior `Message-ID`s.

2. **AI draft generation via LLM gateway**: Reply-draft bodies are produced through the existing Spring AI `LlmGateway`, never via raw HTTP / vendor SDK calls outside `core.llm.gateway.springai`.
   - Current: `LlmGateway.chat()` exists with a `save_draft` tool whose only field is `body`; no draft-composition prompt path.
   - Target: A draft-generation call path produces a reply body for a given thread context through `LlmGateway` (extending the `save_draft` tool / prompt as needed); generation is the same path for triage-initiated and user-initiated drafts.
   - Acceptance: Code inspection confirms all model calls for draft generation route through `core.llm.gateway` (ArchUnit-isolated Spring AI usage stays under `springai/`); a generated draft for a test thread is non-empty and contextually references the incoming message.

3. **Tone matching from recent sent mail (in-request, not persisted)**: Generated drafts are conditioned on the user's recent sent-mail style.
   - Current: No sent-mail retrieval or tone/style logic exists; the model sees only the incoming thread.
   - Target: At draft-generation time the system fetches a bounded set of the user's recent sent messages via the Gmail API within the same request, derives lightweight style features/snippets, and feeds them to the model. No embeddings are persisted; no sent-mail content is stored in the DB. Counts and feature-extraction details are a discuss-phase decision.
   - Acceptance: With recent-sent-mail context available, the draft-generation request demonstrably incorporates sent-mail-derived input (verified by test/instrumentation on the request path); no DB row or persisted embedding holds sent-mail content after the request completes.

4. **Tone-context privacy hardening**: Sent-mail content used for tone matching is sanitized, truncated, prompt-injection-hardened, never persisted, and never logged.
   - Current: N/A — no such path exists.
   - Target: The sent-mail tone context passes through the project sanitize + truncate + prompt-injection-hardening pipeline before reaching the model; it is never written to the DB and never appears in logs (consistent with the project privacy constraint).
   - Acceptance: Code review + a test confirm the tone-context path runs the sanitizer/truncation/hardening; log assertions confirm no sent-mail body bytes are emitted; no persistence layer stores the content.

5. **No auto-send / no in-app send-or-edit**: Draft generation never sends mail and `apps/web` exposes no send or draft-edit action.
   - Current: `users.drafts.send()` is not called anywhere; gateway-layer auto-send block exists from Phase 4 (TRG-03).
   - Target: New 5B code adds no `users.drafts.send` and no `users.drafts.update` call; the web UI offers only "open in Gmail" + "Draft reply / Regenerate draft" — review, edit, and send happen entirely in Gmail.
   - Acceptance: Grep/ArchUnit confirms no `drafts.send` / `drafts.update` invocation in 5B code paths; UI inspection confirms no Send/Edit control on the draft surface; the existing gateway auto-send guard still blocks send.

6. **Manual "Draft reply / Regenerate draft" action**: Users can trigger (or re-trigger) draft generation for a thread from the web UI.
   - Current: No draft-trigger control exists in `apps/web`; no triage-audit list endpoint exists.
   - Target: A backend endpoint generates/regenerates a reply draft for a given thread; the action is reachable from (a) rows in the needs-reply inbox view (req 7) and (b) triage audit-log rows — which requires building the `GET /api/triage/audit` list endpoint (paginated, exposing `threadId` / `messageId` / `draftId`), closing the 5A gap. Regeneration replaces/refreshes the Gmail draft for that thread (without sending).
   - Acceptance: From a triage audit-log row and from a needs-reply inbox row, invoking "Draft reply / Regenerate draft" results in a Gmail draft for that thread (verified via Gmail API in test); `GET /api/triage/audit` returns a paginated list including thread/message/draft identifiers.

7. **Two-bucket needs-reply inbox**: `apps/web` shows threads that need the user's reply and threads awaiting someone else's reply.
   - Current: No "needs-reply" / Reply-Zero classification, data, or screen exists.
   - Target: A backend classification path labels threads into `to-reply` (user owes a reply) and `awaiting-their-reply` (user has replied / drafted, waiting on the other party), persisting only per-thread metadata (no bodies/prompts/completions); `apps/web` renders a needs-reply inbox with these two buckets, each row showing draft status and offering "open in Gmail" + "Draft reply / Regenerate draft". The classification mechanism, accuracy bar, and per-row management actions beyond those two are discuss-phase decisions. ⚠ Acceptance bar for classification quality is below minimum — planner treats the quality threshold as an assumption.
   - Acceptance: The needs-reply inbox renders both buckets without error at 0, 1, and many threads; threads with a Zero-Mail-created draft appear under `awaiting-their-reply` with their draft status; each row links to the thread in Gmail and exposes the regenerate action.

## Boundaries

**In scope:**
- Threading-header (`In-Reply-To` / `References`) population for all Zero-Mail-created reply drafts, including retrofitting the existing triage `save_draft` path.
- AI reply-draft generation via the existing Spring AI `LlmGateway` (extending the `save_draft` tool/prompt as needed).
- Tone matching from a bounded set of recent sent messages fetched in-request; privacy hardening of that context.
- `GET /api/triage/audit` paginated list endpoint (closing the 5A gap) exposing thread/message/draft identifiers.
- Backend endpoint to generate/regenerate a reply draft for a thread on demand.
- `apps/web`: "Draft reply / Regenerate draft" action on triage audit-log rows and on needs-reply inbox rows.
- `apps/web`: two-bucket needs-reply inbox view (`to-reply`, `awaiting-their-reply`) with per-thread draft status and "open in Gmail" links.
- Backend per-thread reply-status classification + metadata-only persistence to power that inbox.

**Out of scope:**
- In-app draft preview / edit / send — review, edit, and send happen entirely in Gmail (keeps DRFT-04 airtight, avoids `users.drafts.update`/`send`).
- Auto-send of any kind, including "auto-send after the user approves once" — forbidden by project constraint.
- Reply reminders / follow-up sequences ("remind me if no reply in N days") — deferred to backlog.
- Bulk "draft all" across multiple threads — deferred to backlog.
- Snooze / archive / other thread-management actions from the needs-reply inbox — deferred to backlog.
- Persisted embeddings or any vector store of user mail — forbidden by project constraint.
- Analytics screen and daily digest — Phase 5C.

## Constraints

- All model calls for draft generation and reply-status classification go through `core.llm.gateway`; direct Spring AI usage stays ArchUnit-isolated under `springai/`. No raw HTTP / vendor SDK LLM calls.
- No long-term storage of raw email bodies, sent-mail tone context, prompts, completions, or embeddings; tone context must be sanitized + truncated + prompt-injection-hardened before the model. Reply-status persistence is per-thread metadata only.
- Privacy logging format applies: `event=<name> tenantId={}` + structured fields; no email content, addresses, Google subject, token bytes, or prompts/completions in logs.
- Gmail writes limited to the v1 allow-list (label / archive / save-draft); `users.drafts.send` and `users.drafts.update` are not added.
- Gmail-only mail provider; Gmail API + existing Pub/Sub ingestion model unchanged.
- Backend Java enterprise-readability naming conventions; records for DTOs, classes for entities, Lombok-free; Java 25 / Spring Boot 4 / Spring AI 2.0.0-M6.
- Frontend uses `apps/web` Next.js 16 / React 19, typed OpenAPI client, shadcn/ui primitives first; `frontend-design` skill applies to new UI.

## Acceptance Criteria

- [ ] A Zero-Mail-created reply draft (triage-initiated and user-initiated) appears under the original `threadId` in Gmail with `In-Reply-To` matching a thread `Message-ID` and `References` containing the prior chain.
- [ ] The existing triage `save_draft` path is updated to set the threading headers (not just the new on-demand path).
- [ ] Draft generation for a test thread produces a non-empty body that references the incoming message, routed through `LlmGateway`.
- [ ] Draft generation incorporates recent-sent-mail-derived context, fetched in-request; no DB row or persisted embedding retains sent-mail content afterward.
- [ ] The tone-context path runs sanitize + truncate + prompt-injection hardening; no sent-mail body bytes appear in logs.
- [ ] No 5B code path calls `users.drafts.send` or `users.drafts.update`; the existing gateway auto-send guard still blocks send; the web UI exposes no Send/Edit control.
- [ ] `GET /api/triage/audit` returns a paginated list including `threadId` / `messageId` / `draftId`.
- [ ] "Draft reply / Regenerate draft" works from both a triage audit-log row and a needs-reply inbox row, producing a Gmail draft for that thread (verified via Gmail API).
- [ ] The needs-reply inbox renders both `to-reply` and `awaiting-their-reply` buckets without error at 0, 1, and many threads; threads with a Zero-Mail draft show under `awaiting-their-reply` with draft status and a Gmail link.

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                        |
|--------------------|-------|------|--------|--------------------------------------------------------------|
| Goal Clarity       | 0.85  | 0.75 | ✓      | Auto-draft on triage save_draft + manual fallback + 2-bucket inbox; review in Gmail |
| Boundary Clarity   | 0.80  | 0.70 | ✓      | Explicit out-of-scope: in-app edit/send, reminders, bulk, snooze, auto-send |
| Constraint Clarity | 0.80  | 0.65 | ✓      | Privacy (no persist sent context/embeddings), threading headers, gateway-only, no drafts.send/update |
| Acceptance Criteria| 0.74  | 0.70 | ⚠*     | Solid pass/fail set; needs-reply classification quality bar deferred to discuss-phase |
| **Ambiguity**      | 0.19  | ≤0.20| ✓      |                                                              |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption). *Acceptance dimension meets its 0.70 minimum overall; req 7's classification-quality threshold specifically is the soft spot — flagged as an assumption for the planner.

Deferred to discuss-phase (treat as open "how" decisions, not gaps): which thread message is the reply target and exact `References` chain construction; how many recent sent messages and what style features; whether the `save_draft` tool schema gains tone fields or it's prompt-only; the reply-status classification mechanism and its accuracy bar; per-row inbox actions beyond "open in Gmail" / "Regenerate draft" (e.g. "mark as handled").

## Interview Log

| Round | Perspective     | Question summary                                  | Decision locked                                                                 |
|-------|-----------------|---------------------------------------------------|---------------------------------------------------------------------------------|
| 1     | Researcher      | Where does the "Draft reply" trigger live?        | Drafts are primarily automatic on triage `save_draft`; UI shows/reviews via thread + audit views; manual "Draft reply / Regenerate" is a fallback |
| 1     | Researcher      | Relationship to existing triage `save_draft`?     | 5B = on-demand path **and** retrofits `In-Reply-To`/`References` into the existing triage `save_draft` writer |
| 1     | Researcher      | In-app draft preview/review?                       | Gmail-only — no in-app preview/edit/send                                        |
| 2     | Simplifier      | Reply-Zero scope: minimal vs needs-reply inbox?   | Include a needs-reply inbox                                                      |
| 2     | Simplifier      | Tone-match requirement level?                      | Lock "use recent sent mail in-request, no persisted embeddings"; counts/extraction → discuss-phase |
| 2     | Researcher      | Does 5B build `GET /api/triage/audit`?            | → revisited round 5                                                              |
| 3     | Boundary Keeper | How is "needs reply" determined?                  | Two buckets: `to-reply` + `awaiting-their-reply`                                 |
| 3     | Boundary Keeper | Send/Edit in app?                                 | No — review/edit/send 100% in Gmail; no `drafts.send`/`drafts.update`            |
| 3     | Boundary Keeper | What's out of scope?                               | Reply reminders/follow-up, bulk "draft all", snooze/archive from inbox, auto-send-after-approval — all out |
| 4     | Failure Analyst | Which message do we reply to (threading)?         | → discuss-phase; locked requirement: draft must land in the correct Gmail thread with valid headers |
| 4     | Failure Analyst | Tone-context privacy as acceptance criterion?     | Locked: sanitized + truncated + prompt-injection-hardened + never persisted + never logged |
| 4     | Failure Analyst | Inbox row actions / classifier accuracy gate?     | → discuss-phase                                                                  |
| 5     | Seed Closer     | Where must the manual trigger live?               | Both the needs-reply inbox view **and** triage audit-log rows → 5B builds `GET /api/triage/audit` list endpoint |
| 5     | Seed Closer     | Classifier pass bar?                              | → discuss-phase (flagged as assumption in Ambiguity Report)                      |

---

*Phase: 05B-user-surface-ai-draft-replies*
*Spec created: 2026-05-13*
*Next step: /gsd-discuss-phase 5B — implementation decisions (reply-target selection & header chain, sent-mail sampling & style features, reply-status classifier design, audit-list & draft-trigger endpoint shapes, inbox UX)*
