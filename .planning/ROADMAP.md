# Roadmap: Zero Mail

## Milestones

- [x] **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- [ ] **v1.1 — Email assistant chat + Settings page** — Phases 7, 8 (started 2026-05-17)

## Overview (v1.1)

v1.1 ships a streaming `/chat` route and an assistant `/settings` page on top of the locked v1.0 backend. The defining capability is a chat-based email assistant that can read inbox, manage rules, save memory, and (only with explicit per-message user click) send/reply/forward via Gmail — without weakening v1.0's trust posture. Two phases, deliberately minimal: Phase 7 lands the entire chat stack (backend `core.chat` Modulith module, single send executor, ArchUnit carve-out flip 0→1, frontend `/chat`) as one coherent capability; Phase 8 lands the Settings UI, BYOK + personalization + behavior + safety-net surfaces, the hostile-corpus eval, and the v1.1 GA tag. No parallelization — Phase 8 strictly depends on Phase 7 (Settings consumes the same LLM gateway, personalization slot, and safety-net data that chat exercises end-to-end).

## Phases

**Phase Numbering:**
- Integer phases (7, 8): Planned milestone work, continuing from v1.0's last integer phase (6)
- Decimal phases (e.g., 7.1): Reserved for urgent insertions discovered during execution

- [ ] **Phase 7: Chat Email Assistant (Backend + Frontend + Send Executor + ArchUnit flip 0→1)** - Ship the entire chat stack as one phase: new `core.chat` Modulith module (6 Liquibase changelogs 041-046), `ToolOutputSanitizer`, `TenantAwareReactorScheduler`, `ChatToolCallRegistry`, `ZeroMailChatMemory` adapter (workaround Spring AI #3366/#5167), SSE bridge with `VercelProtocolEmitter` + heartbeat + lifecycle, `LlmGateway.streamChat(...)` + `SpringAiLlmModelClient` with per-request `internalToolExecutionEnabled(false)`, all 20 tools wired (7 read + 8 write-reversible + 3 confirm-required + 3 confirmed-send), `AssistantSendExecutor` as the single carved-out send call site (annotated `@AllowedSendCallSite`), confirmation state machine (Redis 5-min lease + optimistic concurrency + persistence retry + same-tx audit + reconciliation cron), ArchUnit 3-layer carve-out flips count 0→1, hardened system prompt (XML-fenced personalization slot + suspicious-sender warning + evidence-vs-instruction separation), frontend `/chat` route with `@ai-sdk/react@3` + AI Elements primitives + recipient-prominent preview cards + VIP banner + first-contact-domain friction + persisted-message-gating of Send + replay-mode rendering + Vietnamese-default chrome + chat history sidebar at GA
- [ ] **Phase 8: Assistant Settings Page + Hardening + Eval + v1.1 GA** - Settings page at `/settings` using shadcn `<Tabs>` (NO sub-routes, query-param-driven), sections for Provider/Model + Personalization + Behavior + Safety Net; BYOK mask-only contract + sentinel-leak test + logout eviction; 4 providers (OpenAI/Anthropic/Google/DeepSeek) with per-feature model picker (chat/triage/draft); personalization columns wired (writing style, personal instructions, signature, knowledge base, tone preset, AI output language VI/EN) with XML-fenced injection + length cap + sanitization; 5 behavior toggles wired to existing v1.0 backends; safety-net UI for view/add/remove + paste-import + per-entry mode + audit-log "blocked by VIP" badge; hardening pass (hostile-corpus `aiEval` suite — 15 hostile email + 10 hostile personal_instructions + VIP send refusal + VI/EN language); Grafana dashboards (lease residuals, audit-vs-state mismatch, ordering violations, leak counters); CASA evidence refresh; README/CONTRIBUTING updates for send-call-site discipline; v1.1 GA tag + launch GO/NOGO checklist

## Phase Details

### Phase 7: Chat Email Assistant (Backend + Frontend + Send Executor + ArchUnit flip 0→1)
**Goal**: A user can open `/chat`, hold a multi-turn streaming conversation with the AI assistant about their inbox and rules, drive 20 tools (read inbox, manage rules, save memory, draft/send/reply/forward email), and confirm every risky action through a preview card that renders before any side-effect — all without persisting email body content in `chat_message.parts`, without weakening v1.0's "no auto-send" invariant (Gmail send call sites move from exactly 0 to exactly 1 — the `AssistantSendExecutor`), and without leaking tenant context across the long-lived SSE connection.
**Depends on**: v1.0 (LLM gateway, rules engine, triage audit, sender safety net `TRG-07..08` tables, AES-GCM token crypto, Spring Session Redis, Scoped Values + ArchUnit invariants) — no v1.1 dependency
**Requirements**: CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, CHAT-07, CHAT-08, ARCH-01, ARCH-02, ARCH-03, ARCH-04, ARCH-05, ARCH-06, ARCH-07, SET-SAFE-05
**Success Criteria** (what must be TRUE):
  1. A user can navigate to `/chat`, type a multi-turn conversation in Vietnamese-default chrome, see the assistant stream replies token-by-token over SSE, invoke any of the 20 tools (7 read / 8 write-reversible / 3 confirm-required / 3 confirmed-send), and observe streaming/tool-call/confirmation/error states render correctly with a Cancel button that drops the stream within one frame.
  2. When the assistant calls `sendEmail` / `replyEmail` / `forwardEmail` / `createRule` / `deleteRule` / `saveMemory`, a preview card renders with Edit / Send / Cancel, the Send button stays disabled until the message has been persisted, and the underlying side-effect fires exactly once on user click — verified by a per-race integration test covering double-click, stale `toolCallId`, and confirm-during-stream; every confirmed send produces exactly one `assistant_send_audit` row written in the same transaction as the state flip (ARCH-03, ARCH-04).
  3. Exactly ONE Gmail send call site exists in the codebase (the `AssistantSendExecutor` in `core.chat.confirm.send` annotated `@AllowedSendCallSite`); paired negative + positive ArchUnit tests + CI grep gate fail the build if the count drifts from 1, and v1.0's "no auto-send" trust contract still holds for every non-chat code path (ARCH-01).
  4. A reviewer can grep `chat_message.parts` JSONB content for any tenant after a synthetic conversation containing `readEmail` tool calls and find zero email body content; enforced by three independent layers — `ToolOutputSanitizer` (runtime), `ChatPersistenceContentBanTest` (ArchUnit), `chat_message_body_ban` PostgreSQL trigger (DB) — and a multi-tenant chat leak integration test proves tenant context never leaks across the long-lived SSE connection or tool fan-out (ARCH-02, ARCH-05).
  5. The system prompt sandboxes user-supplied personalization (`personal_instructions`, `writing_style`) inside an XML-fenced injection slot with length cap + sentinel stripping (`[SYSTEM]`, `</s>`, `### system`, `<|im_start|>`, markdown headers); a confirmed send/reply/forward to a recipient on the v1.0 sender-safety-net renders an extra-friction VIP banner ("Recipient is on your safety net — confirm anyway?") on the preview card before the Send button is enabled (ARCH-06, SET-SAFE-05).
  6. The chat conversation history sidebar lists prior conversations per tenant, survives page refresh, and renders confirmed cards in replay-mode "Sent ✓" state with no re-execution; Spring AI 2.0.0-M6 streaming + tool-call confirmation works end-to-end despite known bugs `spring-ai#3366`/`#5167` via the Zero Mail-owned `ChatToolCallRegistry` + `ZeroMailChatMemory` adapter reading from `chat_message.parts` directly (CHAT-07, ARCH-07).
**Plans**: TBD (decompose via `/gsd:plan-phase 7` after `/gsd:discuss-phase 7`)
**Research flag**: COMPLETE — see `.planning/research/SUMMARY.md`, `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md`, `PITFALLS.md`. Spring AI M6 streaming + tool-call confirmation flagged MEDIUM-HIGH; build a 100-LoC orchestrator prototype before committing to the full executor design.
**UI hint**: yes

### Phase 8: Assistant Settings Page + Hardening + Eval + v1.1 GA
**Goal**: A user can navigate to `/settings`, pick their AI provider/model per feature (chat / triage / draft) from 4 BYOK providers, enter and test their BYOK key, edit their personalization (writing style + personal instructions + signature + knowledge base + tone preset + AI output language VI/EN), toggle 5 behavior switches, and manage the sender safety net (view/add/remove + paste-import + per-entry mode + audit "blocked by VIP" badge) — with every personalization input prompt-injection-hardened, every BYOK key AES-GCM-encrypted + never returned to the frontend + zeroed on logout, and a hostile-corpus `aiEval` suite gating GA against 15 hostile emails + 10 hostile personal_instructions + VIP send refusal + VI/EN output language fidelity.
**Depends on**: Phase 7 (Settings reuses the LLM gateway + per-feature model picker + XML-fenced personalization slot + chat send executor exercised end-to-end during Phase 7; the safety-net audit-log badge depends on Phase 7's `assistant_send_audit` schema)
**Requirements**: SET-AI-01, SET-AI-02, SET-AI-03, SET-AI-04, SET-VOICE-01, SET-VOICE-02, SET-VOICE-03, SET-VOICE-04, SET-VOICE-05, SET-VOICE-06, SET-BEHV-01, SET-BEHV-02, SET-BEHV-03, SET-BEHV-04, SET-BEHV-05, SET-SAFE-01, SET-SAFE-02, SET-SAFE-03, SET-SAFE-04
**Success Criteria** (what must be TRUE):
  1. A user opens `/settings`, navigates the four tabs (Provider/Model, Personalization, Behavior, Safety Net) via shadcn `<Tabs>` with query-param-driven state (`/settings?tab=ai|personalization|behavior|safety-net`) — no sub-routes — and `/settings/privacy` from v1.0 stays back-compat reachable.
  2. A user can pick the AI provider per feature (chat / triage / draft) from the 4 BYOK providers (OpenAI / Anthropic / Google GenAI / DeepSeek), choose between "Use Zero Mail default (OpenRouter)" and "Use my key" independently per feature with a per-feature cost estimate visible next to the model picker, enter their BYOK API key (AES-GCM encrypted at rest via v1.0 LLM-04 path), test the connection via a lightweight provider `models` endpoint call, and verify the key is never logged + never returned to the frontend after save + zeroed on logout (mask-only contract with sentinel-leak test green).
  3. A user can edit free-text writing style (200–500 words), personal instructions ≤ 2000 chars (XML-fenced + sentinel-stripped + length-capped + sanitized before injection into chat/triage/draft system prompts), email signature, titled knowledge-base snippets the AI consults during drafting, a tone preset (professional / friendly / casual / formal / custom), and AI output language (VI / EN, default VI) — and a hostile-corpus `aiEval` suite (10 hostile personal_instructions) proves the assistant refuses to ignore prior instructions or leak prompt scaffolding under attack.
  4. A user can toggle 5 behavior switches (auto-draft replies master, draft confidence threshold 0.0–1.0, daily digest, sensitive-data protection default ON, shadow-mode toggle from v1.0 TRG-07) and observe each toggle drive the corresponding v1.0 backend (DRFT, ANL-03, LLM-05, TRG-07) within one request cycle; no new backend behavior, only surface wiring.
  5. A user can view + add + remove sender safety-net entries (email or domain pattern) exposing the v1.0 TRG-07..08 tables to end users for the first time, paste-import multiple entries with a parsed preview before save, pick per-entry mode (`protect` = never auto-act, `escalate` = notify but don't act), and see a visual "blocked by VIP rule for ceo@acme.com" badge on the v1.0 audit log when a rule was blocked by the safety net.
  6. A v1.1 GA release candidate passes the full hardening sweep: hostile-corpus `aiEval` (15 hostile emails + 10 hostile personal_instructions + VIP send refusal + VI/EN output language fidelity) gates merge; Grafana dashboards (lease residuals, audit-vs-state mismatch, ordering violations, leak counters, BUDGET_EXHAUSTED rate) are wired; CASA evidence refreshed for the new chat surface; README/CONTRIBUTING updated to document the single-send-call-site discipline; LAUNCH-GO-NOGO checklist signed against the v1.0 trust story (never auto-sends rule-triggered, no stored bodies, undoable actions, single confirmed-send call site) and the v1.1 GA tag is cut.
**Plans**: TBD (decompose via `/gsd:plan-phase 8` after `/gsd:discuss-phase 8`)
**Research flag**: PARTIAL — Settings UI, BYOK, personalization, safety-net UI well-covered in v1.0 patterns (shadcn + TanStack Query + openapi-fetch). Hardening + `aiEval` harness needs design work: `v1.0 LLM-11` golden-set drift doesn't cover hostile scenarios; build a 50-LoC harness prototype before locking the suite.
**UI hint**: yes

## External Track (not a phase)

**CASA production verification (SEED-012)** remains deferred from v1.0; v1.0 + v1.1 both ship under OAuth Testing mode. Phase 8 only refreshes the CASA evidence package for the new chat surface — it does not block on lab sign-off.

## Progress

**Execution Order:**
Phase 7 → Phase 8

Parallelization: None. Phase 8 strictly depends on Phase 7 (Settings consumes the same LLM gateway, personalization slot, and safety-net data that chat exercises end-to-end; the hardening eval + GA tag wraps both phases).

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 0/TBD | Pending discuss-phase | - |
| 8. Settings + Hardening + GA | v1.1 | 0/TBD | Not started | - |

---

*v1.0 archived 2026-05-15. v1.1 started 2026-05-17 — phases 7-8 cover Email assistant chat + Settings page. Next: `/gsd:discuss-phase 7` → `/gsd:plan-phase 7`.*
