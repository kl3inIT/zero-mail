# Phase 5B: User Surface — AI Draft Replies - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 5B-user-surface-ai-draft-replies
**Areas discussed:** Threading headers & reply-target, Tone-matching from sent mail, Reply-status classifier + needs-reply inbox UX, Backend API surface

Mode: advisor (USER-PROFILE.md present; calibration tier = full_maturity from `Vendor Choices: thorough-evaluator`; NON_TECHNICAL_OWNER resolved **false** — `learning_style: guided` signal overridden by the developer's evident deep technical background, so gray areas were framed in technical terms). Requirements were already locked by `05B-SPEC.md` (7 reqs) — discussion covered HOW only. Four advisor-research subagents ran in parallel before the questions.

---

## Threading headers & reply-target

| Option | Description | Selected |
|--------|-------------|----------|
| B: threadId + In-Reply-To/References/Re: + reply-to-triaged-msg | Headers from `messages.get format=METADATA` (reuse triage metadata if available); MIME via `jakarta.mail` → base64url raw; keep `setThreadId`; retrofit `TriageGmailWriter.saveDraft`; reply to the inbound message triage acted on | ✓ |
| Walk thread for most-recent non-self message | Extra `threads.get` to pick reply-target more carefully — over-engineering for an edge case ingestion mostly prevents | |
| Defer to planner | Lock principles, leave reply-target selection to planner | |

**User's choice:** Option B (recommended).
**Notes:** Research confirmed `threadId` alone visually threads in the sender's own Gmail but Gmail docs require `In-Reply-To` + `References` + `Re:` Subject for reliable threading, for the sent copy, and for non-Gmail/IMAP clients. `METADATA` fetch avoids pulling the body (privacy-aligned).

---

## Tone-matching from sent mail

| Option | Description | Selected |
|--------|-------------|----------|
| C+: descriptors + 2–3 raw snippet examples | Locally computed style descriptors (~100 tok) + 2–3 quote/signature-stripped, sanitized sent snippets as fenced "style reference only" examples; ~5–8 sent messages fetched | ✓ |
| C-only: descriptors only | Lowest token / near-zero injection surface, but crude — misses nuance from real examples | |
| B-only: raw snippets only (3–8 bodies) | Simplest code; model picks up style implicitly; ~600–1200 tok/draft forever + larger injection surface | |

**User's choice:** Option C+ (recommended).
**Locked alongside (asked as a fixed sub-decision, not a choice):** `save_draft` tool schema stays `{ body: string }`; tone context goes in the prompt only (app→model direction). One-shot combined prompt — no separate metered "style summary" LLM call in v1. Sent-mail tone context never persisted, never logged.
**Notes:** The user's own sent mail is treated as untrusted input (carries quoted third-party text) — mitigation = quote/signature strip + existing `SanitizationPipeline` + fenced non-instruction framing.

---

## Reply-status classifier + needs-reply inbox UX

| Option | Description | Selected |
|--------|-------------|----------|
| Heuristic-only v1, sub-step in TriageOrchestrator + Modulith outbound event | last-msg `From` vs tenant + `SENT` label + Zero-Mail-draft-present; 0 LLM cost / 0 injection; `thread_reply_status` table; hybrid LLM later | (recommended) |
| Hybrid from the start | Heuristic fast-path + LLM for ~10–20% ambiguous threads; more accurate but adds code path + cost + injection-hardening in v1 | |
| Defer to researcher/planner | Treat mechanism as open; planner decides from research | ✓ |

**User's choice:** Defer the classifier *mechanism* to researcher/planner. The inbox UX recommendations (shadcn `Tabs` "To reply" / "Awaiting reply" [+ "Resolved"]; new sidebar item with TO_REPLY count badge; row contents = subject / other party / relative time / draft badge / "Open in Gmail" deep link / `Draft reply`→`Regenerate` / `Mark resolved`; Skeleton / "Inbox zero 🎉" / destructive `Alert` states; 320px single-column) and the placement/persistence recommendations (run as a sub-step in `TriageOrchestratorService` + Modulith reaction to outbound/draft-saved; `thread_reply_status` metadata table with `lastClassifiedMessageId` staleness key + `resolved`; partial index on unresolved TO_REPLY; account-deletion cleanup; accuracy bar ≥ ~85% on TO_REPLY/AWAITING) were accepted as the working baseline and recorded in CONTEXT.md.
**Notes:** Research stressed "awaiting-their-reply" is only detectable without scanning the whole mailbox by keying off threads Zero Mail already observed (`users.watch` covers INBOX + SENT) or touched with a draft. Inbox-zero reference files noted in CONTEXT.md canonical refs.

---

## Backend API surface

| Option | Description | Selected |
|--------|-------------|----------|
| Full recommendation | `GET /api/triage/audit` cursor-based (keyset `createdAt`+`auditId`, Spring Data JDBC); `POST /api/threads/{threadId}/draft` for generate+regenerate; regenerate = delete-then-recreate + Redis per-(tenant,thread) lock; no body in response; inbox from `thread_reply_status` projection (cursor-paginated) | (recommended) |
| Draft endpoint under `/api/triage/audit/{auditId}/draft` | Consistent with existing `/undo`, but inbox rows have no `auditId` → forces a second endpoint | |
| Page/offset for audit list | Spring `Pageable`, free total count, but violates "cursor for hot paths" convention + `OFFSET` drift + `COUNT(*)` on a growing append-only table | |
| Defer detailed shapes to planner | Lock principles (cursor + thread-keyed + delete-then-recreate + Redis lock + no body), leave concrete DTO/path/codec details to planner | ✓ |

**User's choice:** Defer detailed DTO/path/codec shapes to the planner; the principles (cursor pagination on the read side; thread-keyed `POST /api/threads/{threadId}/draft` for both entry points; regenerate = delete-then-recreate, never `drafts.update`/`send`; Redis per-(tenant,thread) lock since `drafts.create` isn't idempotent; minimal response with no draft body; needs-reply inbox served from the `thread_reply_status` projection, not the audit table) are locked and recorded in CONTEXT.md.
**Notes:** No cursor-pagination helper exists in `backend/` yet — that's new code.

---

## Claude's Discretion

- Reply-status classifier mechanism (heuristic-only v1 vs hybrid) — researcher/planner picks from the research; CONTEXT.md records heuristic-only v1 as the recommendation.
- Exact DTO field names, controller package name (`controllers/thread/` vs `controllers/draft/`), cursor codec details, Liquibase changelog id.
- Whether `thread_reply_status` includes the optional FYI / ACTIONED buckets in v1 or only the two action-driving buckets.
- Exact descriptor list and snippet count within the agreed envelope (~3 snippets / ~5–8 fetched / ~100-token budget).

## Deferred Ideas

- Reply reminders / follow-up sequences ("remind me if no reply in N days") — future phase / backlog.
- Bulk "draft all" across multiple threads — backlog.
- Snooze / archive / other thread-management actions from the needs-reply inbox — backlog.
- In-app draft preview + edit (would require `users.drafts.update`) — out of v1 by user decision.
- Cached LLM "style summary" per tenant (tone Option D) — revisit if drafts-per-tenant gets high.
- LLM-based reply-status classification beyond the ambiguous-thread hybrid — revisit after v1 heuristic accuracy is measured.
