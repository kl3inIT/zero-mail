# Research Summary — Zero Mail v1.1 (Chat Email Assistant + Settings Page)

**Project:** Zero Mail
**Milestone:** v1.1 — Email assistant chat + Settings page
**Domain:** AI chat email assistant + assistant Settings UI (sidecar SaaS on top of Gmail), built on top of a shipped Java 25 / Spring Boot 4 / Spring AI 2.0.0-M6 backend
**Researched:** 2026-05-17
**Confidence:** HIGH overall

> Detailed research lives in:
> - [`.planning/research/STACK.md`](STACK.md)
> - [`.planning/research/FEATURES.md`](FEATURES.md)
> - [`.planning/research/ARCHITECTURE.md`](ARCHITECTURE.md)
> - [`.planning/research/PITFALLS.md`](PITFALLS.md)

---

## Executive Summary

v1.1 adds two surfaces — a streaming `/chat` route and an `/settings` AI tab — on top of the shipped v1.0 backend. The defining capability is a chat-based email assistant that can read inbox, manage rules, save memory, and (only with explicit per-message user click) send/reply/forward via Gmail. Architecturally, this is a **streaming SSE controller built on existing Spring MVC + existing Spring AI 2.0.0-M6 + existing virtual threads** — zero new backend dependencies, three new frontend dependencies (`ai@6`, `@ai-sdk/react@3`, `streamdown@2`), and one new top-level Modulith module (`core.chat`).

The single highest-risk decision is **carving out exactly one Gmail send call site** from v1.0's "no auto-send" ArchUnit rule. v1.0 ships `NoGmailSendAllowedTest` with `allowEmptyShould(true)` (production code has ZERO send calls). v1.1 must move that constant from 0 to 1 — and **only 1** — enforced by paired negative + positive ArchUnit tests plus a CI grep gate.

The recommended phase split is **5 phases**: (1) chat foundation + persistence + ArchUnit before (2) send executor + confirmation state machine before (3) chat frontend before (4) Settings page before (5) eval/hardening.

---

## Key Findings

### Stack Additions TL;DR

**Backend — zero new dependencies.** Reuse existing `spring-boot-starter-web` (SseEmitter), Spring AI 2.0.0-M6 (StreamingChatModel), Reactor Core (transitive), Spring Session Redis, Liquibase YAML, JPA. Vercel UI Message Stream Protocol hand-written in ~250 LoC inside `chat.usecases.ChatSseFrameEmitter`.

**Frontend — three runtime deps + one CLI install:**

```bash
pnpm add ai@^6.0.184 @ai-sdk/react@^3.0.186 streamdown@^2.5.0
pnpm dlx ai-elements@latest add conversation message prompt-input response tool reasoning loader suggestion confirmation
```

**Spring AI mode change:** `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)` per-request inside chat path (not global) so orchestrator intercepts every tool call for confirmation rendering.

**Required SSE header:** `x-vercel-ai-ui-message-stream: v1` or `useChat` silently falls back to text-only.

### What NOT to add

| Avoid | Why |
|---|---|
| `spring-boot-starter-webflux` | Breaks v1.0 Spring MVC + virtual threads lock |
| Vercel AI SDK `ai` package on Java backend | TypeScript-only; splits LLM gateway |
| `@ai-sdk/openai`/`-anthropic` on frontend | Leaks tenant keys to browser |
| `reconnectToStream` | `vercel/ai#14027` crashes on tool parts in `input-streaming` |
| WebSockets/STOMP for chat | SSE sufficient |
| Long-term persistence of email body in `chat_message.parts` | Privacy invariant — short-lived in-memory only |

### Feature Table-Stakes vs Differentiators

**CHAT** — Table stakes: bubbles, streaming, thinking indicator, multi-turn history, stop button, tool-call cards, composer, error surfacing, empty state. Differentiators: CHAT-D3 per-tool preview cards (= CONFIRMATION), CHAT-D7 Vietnamese-default chrome. Defer to v1.2: image attachments, context-pack, stale-rules detection.

**TOOL_CATALOG** (20 tools — "~19" matches if `deleteRule` folded; recommend keep distinct → 20):
- Read (no confirm, 7): `getAssistantCapabilities`, `getUserRulesAndSettings`, `getRuleExecutionForMessage`, `searchInbox`, `readEmail`, `listLabels`, `getInboxStats`
- Write reversible (no confirm, 8): `createOrGetLabel`, `manageInbox`, `updateRuleConditions`, `updateRuleActions`, `updatePersonalInstructions`, `updateAssistantSettings`, `addToKnowledgeBase`, `searchMemories`
- Write confirm required (3): `createRule`, `deleteRule`, `saveMemory`
- Send CRITICAL (confirm + audit + ArchUnit, 3): `sendEmail`, `replyEmail`, `forwardEmail`

**CONFIRMATION** — Table stakes (ALL ship): state machine `pending → processing → confirmed | canceled | failed`, preview card with Edit + Send + Cancel, Redis 5-min lease, audit row before lease release, Send disabled until message persisted, replay-mode rendering, `contentOverride` plumbing, per-tool risk message.

**SETTINGS_PROVIDER** — 4 providers (OpenAI/Anthropic/Google/DeepSeek), per-feature model picker (chat/triage/draft), curated model list, BYOK + AES-GCM, "Use default OpenRouter" vs "Use my key" toggle, per-feature cost estimate. Anti: free-text model id, per-call provider switching via chat, provider expansion (defer v1.2).

**SETTINGS_PERSONALIZATION** — writing style, personal instructions (XML-fenced injection), email signature, knowledge base, tone preset, AI output language VI/EN. Anti: persistent embeddings, learned patterns auto-update.

**SETTINGS_BEHAVIOR** — auto-draft toggle, draft confidence slider, daily digest, sensitive-data protection toggle, shadow-mode surface. Anti: "auto-send if confidence ≥ X" (NEVER).

**SETTINGS_SAFETY** — sender safety net UI: view/add/remove, paste-import, per-entry mode (`protect`/`escalate`), audit-log "blocked by VIP" badge.

### Architecture Decisions Locked-In

1. **New top-level Modulith module `core.chat`** (NOT sub-package of `core.llm`). Sub-package layout: `domain/usecases/projection/persistence/exception`.
2. **SseEmitter (imperative)** — NOT `Flux<ServerSentEvent>` return type. Preserves `TenantContext` ScopedValue + `@Transactional` boundaries. Mandatory lifecycle wiring: `onCompletion/onTimeout/onError` → upstream `Disposable.dispose()`.
3. **6 new Liquibase YAML changelogs (041–046):** `chat`, `chat_message` (+body-ban trigger), `assistant_pending_action`, `assistant_send_audit`, `assistant_settings`, `assistant_memory + assistant_knowledge_snippet`. Schema versioning: every `parts` envelope carries `schemaVersion: 1`.
4. **ArchUnit 3-layer carve-out** (ALL three required): negative `NoGmailSendAllowedTest` (update — exclude `@AllowedSendCallSite`), positive `OnlyOneGmailSendCallSiteTest` (count == 1, not ≤1), CI grep gate. PR diff modifying negative test without adding positive test = critical warning.
5. **Settings extends `/settings` with shadcn `<Tabs>`** — NO `/settings/ai` sub-routes. Tabs query-param-driven (`/settings?tab=ai|personalization|behavior|safety-net|knowledge`). `/settings/privacy` stays for v1.0 back-compat.
6. **Chat is request-scoped only**; `backend/worker` NOT involved in v1.1. Reconciliation cron is residual-cleanup path only.
7. **Spring Modulith event policy for chat:** NO event per SSE turn (request-scoped). ONE event `AssistantSendCompleted` after audit row commit; analytics subscribes via `@TransactionalEventListener(AFTER_COMMIT)`.

---

## Watch Out For (top 7 critical pitfalls)

### 1. Weakening the ArchUnit "no Gmail send" rule instead of scope-narrowing it
Developer hits failing ArchUnit on first compile of `AssistantSendExecutor.send(...)`, deletes the test or weakens it without paired positive test → "no auto-send" trust contract evaporates. **Prevention:** negative + positive ArchUnit + CI grep gate (count == 1, not ≤1). PR diff modifying `NoGmailSendAllowedTest` without simultaneously adding `OnlyOneGmailSendCallSiteTest` is the warning sign. **Phase:** Phase 1.

### 2. Race conditions in user-confirmed send (double-send, stale toolCallId)
Three races: double-click sends twice, confirm-before-persist 404, second confirm during slow Gmail call re-sends. **Prevention:** Port Inbox Zero state machine exactly: optimistic concurrency via `chat_message.parts updated_at` compare-and-swap, Redis lease commits BEFORE Gmail call, Send disabled until `persistedMessageIds.has(messageId)`, `UNIQUE (chat_id, tool_call_id)` on audit for idempotent retries. **Phase:** Phase 2.

### 3. Prompt-injected recipient in confirmed send ("user clicks Send without reading" attack)
Adversary embeds white-on-white instructions in email body → assistant calls `sendEmail` with attacker `to:` → user clicks Send. **Prevention (ALL required):** system prompt evidence-vs-instruction separation; preview UX recipient-prominent + "Recipient suggested by AI" badge + first-contact-domain friction; tool-input sanitization (reject exfil hosts); `aiEval` hostile-email suite. **Phase:** Phase 2 + Phase 3.

### 4. Privacy regression — email body persisted in `chat_message.parts`
`readEmail` returns body to LLM in-memory; assistant quotes body; `parts` persists with body forever. **Prevention (three layers):** `ToolOutputSanitizer` before persist; ArchUnit `ChatPersistenceContentBanTest` proves sanitizer on every path; PostgreSQL trigger `chat_message_body_ban`. **Phase:** Phase 1.

### 5. Tenant boundary leak across virtual threads in chat tool execution
Long-lived SSE + fan-out tool work without `TenantAwareTaskScope` → unbound ScopedValue → tenant leak. **Prevention:** `TenantAwareReactorScheduler` wrapping every `.subscribeOn()`; ArchUnit ban on `Schedulers.{boundedElastic,parallel,single}` in `..chat..`; multi-tenant chat leak test. **Phase:** Phase 1.

### 6. Spring AI 2.0.0-M6 streaming + tool-call: `AssistantMessage.toolCalls` lost
Confirmed bug `spring-projects/spring-ai#3366 + #5167`: streaming + `internalToolExecutionEnabled=false` → aggregated `toolCalls` empty → confirmation handler breaks. **Prevention:** `ChatToolCallRegistry` populated from raw SSE events (not Spring AI aggregator); `ZeroMailChatMemory` reads from our `chat_message.parts`. TODO recheck M7/GA. **Phase:** Phase 1.

### 7. SSE bridge edge cases — orphan virtual threads, partial JSON, cancellation
Client disconnect → server keeps streaming + paying tokens; mid-stream cuts; partial JSON on HTTP/2 packets; `reconnectToStream` crashes. **Prevention:** `SseEmitter.onCompletion/onTimeout/onError` → `Disposable.dispose()`; `VercelProtocolEmitter` ordering enforcement; heartbeat `: keepalive\n\n` every 15s; do NOT implement reconnect (document as out-of-scope); `useChat({experimental_throttle: 100})`. **Phase:** Phase 1.

---

## Open Questions (need user input before locking REQ-IDs)

1. **Phase split count and ordering** — confirm 5-phase (foundation → executor → frontend → settings → eval) or collapse to 3 (ARCHITECTURE view)?
2. **Sender Safety Net policy scope** — cover BOTH incoming triage AND outgoing chat sends? **Recommend yes** with extra-friction banner.
3. **Chat-vs-Settings GA delivery order** — recommend chat first; Settings can land as v1.1.0 follow-up.
4. **CHAT-T9 history sidebar at GA or v1.1.1?** Recommend GA — without it refresh loses conversation.
5. **`deleteRule` distinct tool or fold into `updateRuleActions`?** Recommend distinct → tool count 20, not 19.
6. **Image attachments (CHAT-D4) — defer or stretch?** Recommend defer v1.2.
7. **`sender_safety_entry.mode VARCHAR(16)` column** — verify TRG-08 shipped this; if not, extra Liquibase changelog.
8. **OAuth scope check** — confirm `gmail.send` in bundled OAuth from v1.0 phase 1.5. If missing, one-time re-grant needed.

---

## Phase Split Recommendation

### Adopt the 5-phase split

ARCHITECTURE's 3-phase view bundles foundation + executor + state machine, burying the ArchUnit invariant flip (0 → 1 send call sites) inside a larger PR. PITFALLS' 5-phase split surfaces that flip as its own phase boundary — matches v1.0's discipline around TRG-03.

**Phase 1 — Chat foundation, persistence, infra (no UI; behind feature flag)**
- 6 Liquibase changelogs (041–046) including `chat_message_body_ban` trigger
- `ToolOutputSanitizer` + ArchUnit `ChatPersistenceContentBanTest`
- `TenantAwareReactorScheduler` + multi-tenant chat leak test
- `ChatToolCallRegistry` + `ZeroMailChatMemory` (workaround Spring AI #3366/#5167)
- SSE bridge: lifecycle, `VercelProtocolEmitter` ordering, heartbeat, spend-cap envelope
- `LlmGateway.streamChat(...)` + impl
- ArchUnit carve-out tests (Phase 1 count == 0)
- Read-only tools: `getAssistantCapabilities`, `getUserRulesAndSettings`, `searchInbox`, `readEmail`, `listLabels`, `getInboxStats`, `getRuleExecutionForMessage`

**Phase 2 — Confirmation state machine + send executors (HIGH RISK)**
- `AssistantSendExecutor` (single carved-out call site, `@AllowedSendCallSite`)
- State machine: Redis lease + optimistic concurrency + persistence retry (max 3 attempts)
- Same-transaction audit + state flip
- Reconciliation cron
- System prompt: safety/confirmation policy + XML-fenced `<user_personalization>` + suspicious-sender warning
- `sendEmail`/`replyEmail`/`forwardEmail` tools (pre-generated `Message-ID` for retry idempotency)
- Write tools needing confirm (`createRule`, `saveMemory`, `deleteRule`)
- Direct-write tools (8 remaining)
- ArchUnit count flips 0 → 1

**Phase 3 — Chat frontend (`/chat` + `@ai-sdk/react` + AI Elements)**
- Install 3 npm deps + AI Elements primitives
- `features/chat/` folder
- `useChat({experimental_throttle: 100, transport: DefaultChatTransport({credentials: 'include'})})`
- Preview cards: recipient-prominent + VIP banner + first-contact-domain friction + recipient-origin badge
- Send disabled until `persistedMessageIds.has(messageId)`
- Replay-mode confirmed cards
- Cancel button
- Stream-error/budget-exhausted banners
- Vietnamese-default chrome (CHAT-D7)
- Chat history sidebar (CHAT-T9 at GA)
- Playwright tests (tab-close cancel, prompt-injection UX, double-click confirm)

**Phase 4 — Settings page (BYOK + Personalization + Behavior + Safety Net UI)**
- BYOK mask-only contract + sentinel-leak test + logout eviction + `@Sensitive` ArchUnit
- Personalization columns + XML-fenced injection verified + hostile-corpus eval + length cap + sanitization
- 5 behavior toggles
- Safety net UI (per-entry mode, paste-import, VIP-intersect, audit "blocked by VIP" badge)
- Single `/settings` route with shadcn `<Tabs>` (query-param-driven)

**Phase 5 — Hardening + eval + docs**
- `aiEval` suite (`@Tag("llm-eval")`, separate Gradle task): 15 hostile email + 10 hostile `personal_instructions` + VIP send refusal + VI/EN output language
- Grafana dashboards: lease residuals, audit-vs-state mismatch, ordering violations, leak counters, BUDGET_EXHAUSTED rate
- CASA evidence update
- PROJECT.md decisions: VIP outgoing policy, reconnect-not-implemented, persistence carve-out scope, 20-tool count
- v1.1 GA tag + launch GO/NOGO checklist

### Research flags

| Phase | Research flag | Reason |
|-------|---------------|--------|
| Phase 1 — Foundation | **Skip** | Modulith + Liquibase + ArchUnit + SSE + virtual threads are v1.0 patterns |
| Phase 2 — Confirmation | **Needs** | Spring AI M6 streaming + tool-call has open bugs; build 100-LoC prototype before committing orchestrator design |
| Phase 3 — Chat frontend | **Skip** | `@ai-sdk/react` v3 + AI Elements well-documented; inbox-zero has production examples |
| Phase 4 — Settings | **Skip** | shadcn + TanStack Query + openapi-fetch is v1.0 frontend convention |
| Phase 5 — Hardening | **Needs** | `aiEval` harness design; Inbox Zero has no portable pattern; v1.0 LLM-11 doesn't cover hostile scenarios |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Frontend verified via npm + Context7 + Inbox Zero reference. Backend zero new deps. Spring MVC SSE verified against Spring Framework reference |
| Features | **HIGH** | Inbox Zero source inspected directly with file paths/line numbers |
| Architecture | **HIGH** for module layout, schema, ArchUnit, Modulith conventions. **MEDIUM-HIGH** for Spring AI M6 tool-call streaming loop |
| Pitfalls | **HIGH** for v1.0 invariants + confirmation state machine + prompt-injection threat model. **MEDIUM-HIGH** for Spring AI M6 streaming gaps |

**Overall:** **HIGH**

---

*Research synthesis completed: 2026-05-17*
*Ready for roadmap: yes (pending answers to open questions 1–8)*
