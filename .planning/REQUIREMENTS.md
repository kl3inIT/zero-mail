# Requirements: Zero Mail v1.1

**Defined:** 2026-05-17
**Milestone:** v1.1 — Email assistant chat + Settings page
**Core Value:** AI auto-triage that users trust with their real Gmail inbox. v1.1 extends this by adding a chat-based email assistant + assistant Settings UI **without weakening v1.0's trust posture** (no auto-send, no email-body persistence, single ArchUnit-carved-out send call site).

> v1.0 requirements (AUTH, FND, MAIL, BILL, LLM, RULE, TRG, DRFT, ANL, WEB) are SHIPPED and archived at `.planning/milestones/v1.0-REQUIREMENTS.md`. This document covers v1.1 net-new requirements only.

---

## v1 Requirements

### Chat Email Assistant — User-facing capability

- [ ] **CHAT-01**: User can open a streaming `/chat` route and have a multi-turn conversation with the AI assistant about their inbox and rules (per-turn streaming via SSE, stop button, error surfacing, empty state)
- [ ] **CHAT-02**: User can ask the assistant to list, explain, create, update, or delete rules in natural language; assistant routes through structured tools (rule CRUD) with rule-state always derived from the existing v1.0 rules engine
- [ ] **CHAT-03**: User can ask the assistant to search inbox, read a specific email, list labels, manage inbox (archive/label/mark-read), and create labels — all reusing v1.0 backend services with no new auto-action paths
- [ ] **CHAT-04**: User can ask the assistant to draft, send, reply to, or forward email; every send/reply/forward renders a preview card with recipient + subject + body + Edit + Send + Cancel; send only fires after explicit per-message user click
- [ ] **CHAT-05**: User can ask the assistant to save personal memory ("remember tôi đang work với Acme") and recall it later via search; user can manage knowledge-base snippets the assistant consults when drafting
- [ ] **CHAT-06**: User sees a confirmation preview card for any risky action (createRule, deleteRule, saveMemory, sendEmail, replyEmail, forwardEmail) with Edit/Send/Cancel; cards render in replay-mode "Sent ✓" state after confirmation
- [ ] **CHAT-07**: User sees the chat conversation history sidebar at GA; conversations persist per-tenant and survive page refresh
- [ ] **CHAT-08**: User's chat UI is Vietnamese-default with English secondary, matching the v1.0 i18n direction

### Settings Page — Provider/Model (AI Config)

- [ ] **SET-AI-01**: User can pick the AI provider per feature (chat, triage, draft) from the 4 v1.0 BYOK providers (OpenAI, Anthropic, Google GenAI, DeepSeek) and a curated model list per provider
- [ ] **SET-AI-02**: User can enter their BYOK API key per provider; key is AES-GCM encrypted at rest (reuses v1.0 LLM-04); key is never logged, never returned to frontend after save, zeroed on logout
- [ ] **SET-AI-03**: User can choose between "Use Zero Mail default (OpenRouter)" and "Use my key" independently per feature; per-feature cost estimate visible next to model picker
- [ ] **SET-AI-04**: User can test the BYOK connection (lightweight provider `models` endpoint call) before relying on the key

### Settings Page — Personalization (Your Voice + Knowledge)

- [ ] **SET-VOICE-01**: User can edit free-text writing style description (200–500 words) that influences AI draft tone
- [ ] **SET-VOICE-02**: User can edit free-text personal instructions ("About me") that gets injected into the system prompt for chat/triage/draft (XML-fenced + sanitized for prompt-injection sentinels + length cap 2000 chars)
- [ ] **SET-VOICE-03**: User can edit free-text email signature appended to AI drafts
- [ ] **SET-VOICE-04**: User can manage a list of titled knowledge-base snippets the AI consults when drafting
- [ ] **SET-VOICE-05**: User can pick a tone preset (professional / friendly / casual / formal / custom) as a quick baseline
- [ ] **SET-VOICE-06**: User can pick AI output language (VI / EN, default VI) — separate from UI language

### Settings Page — Behavior Toggles

- [ ] **SET-BEHV-01**: User can toggle auto-draft replies (master switch for v1.0 DRFT-01..04 background drafts)
- [ ] **SET-BEHV-02**: User can set a draft confidence threshold (0.0–1.0); AI only saves drafts ≥ threshold
- [ ] **SET-BEHV-03**: User can toggle daily digest (reuses v1.0 ANL-03 config)
- [ ] **SET-BEHV-04**: User can toggle sensitive-data protection (controls v1.0 LLM-05 PII redaction behavior; default ON)
- [ ] **SET-BEHV-05**: User can surface the shadow-mode toggle (reuses v1.0 TRG-07) from the assistant Settings page

### Settings Page — Sender Safety Net (VIP) UI

- [ ] **SET-SAFE-01**: User can view, add, and remove sender safety net entries (email or domain pattern) via the Settings page (exposes existing v1.0 TRG-07..08 tables to end users for the first time)
- [ ] **SET-SAFE-02**: User can paste-import multiple entries at once with a parsed preview before save
- [ ] **SET-SAFE-03**: User can pick per-entry mode (`protect` = never auto-act, `escalate` = notify but don't act)
- [ ] **SET-SAFE-04**: User sees a visual indicator in the audit log when a rule was blocked by the safety net ("Was going to archive, blocked by VIP rule for ceo@acme.com")
- [ ] **SET-SAFE-05**: Chat-confirmed send/reply/forward to a VIP-listed recipient shows an extra-friction banner ("Recipient is on your safety net — confirm anyway?") on the preview card (extends TRG-07..08 to outgoing pathway)

### Architecture Invariants — must hold at v1.1 GA

- [ ] **ARCH-01**: Exactly ONE Gmail send call site exists in the codebase (the `AssistantSendExecutor` in `core.chat.confirm.send` annotated with `@AllowedSendCallSite`); enforced by paired negative + positive ArchUnit tests + CI grep gate that fails the build if count != 1
- [ ] **ARCH-02**: `chat_message.parts` JSONB cannot persist email body content; enforced by `ToolOutputSanitizer` (runtime) + `ChatPersistenceContentBanTest` (ArchUnit) + `chat_message_body_ban` PostgreSQL trigger (DB) — three independent layers
- [ ] **ARCH-03**: Confirmation state machine handles all three races (double-click, stale toolCallId, confirm-during-stream) — verified by per-race integration test + 5-min Redis lease + `UNIQUE (chat_id, tool_call_id)` on audit table for idempotent retries
- [ ] **ARCH-04**: Every confirmed send produces exactly one `assistant_send_audit` row written in the same transaction as the state flip; reconciliation cron handles residuals from crashes
- [ ] **ARCH-05**: Tenant isolation holds across the long-lived SSE connection + tool fan-out work; enforced by `TenantAwareReactorScheduler` + ArchUnit ban on `Schedulers.{boundedElastic,parallel,single}` inside `..chat..` + multi-tenant chat leak integration test
- [ ] **ARCH-06**: Personalization (`personal_instructions`, `writing_style`) is sandboxed against prompt injection: XML-fenced injection slot in system prompt + length cap + sentinel stripping (e.g., known prompt-injection markers and markdown headers) + hostile-corpus eval before GA
- [ ] **ARCH-07**: Spring AI 2.0.0-M6 streaming + tool-call confirmation works end-to-end despite known bugs `spring-ai#3366`/`#5167` via Zero Mail-owned `ChatToolCallRegistry` + `ZeroMailChatMemory` adapter reading from `chat_message.parts` directly

---

## Future Requirements (v1.2 candidates)

### Provider Expansion
- **SET-AI-EXP-01**: Bedrock provider via `@ai-sdk/amazon-bedrock`
- **SET-AI-EXP-02**: Azure OpenAI provider via `@ai-sdk/azure`
- **SET-AI-EXP-03**: Groq, Perplexity, native OpenRouter, OpenAI-compatible (for self-hosted), Google Vertex (Workspace orgs) — expand from 4 to ~11 providers

### Operational Surfaces
- **OPS-01**: Waitlist + semi-automated OAuth test-user provisioning (`/waitlist` form, admin paste-to-Google-Console flow) — deferred from v1.1
- **OPS-02**: Admin/Support/Compliance Console (SEED-011) — tenant health, worker queue, billing ledger, LLM spend per tenant
- **OPS-03**: CASA production verification (SEED-012) — unblock OAuth Testing-mode 100-user cap and 7-day re-consent expiration

### Chat Enhancements
- **CHAT-FUT-01**: Image attachments (CHAT-D4) — multimodal via Spring AI `Media` API
- **CHAT-FUT-02**: Reasoning blocks (CHAT-D1) — for Anthropic/select OpenRouter routes
- **CHAT-FUT-03**: Context-pack injection (CHAT-D5) — inbox stats + rule snapshot in system prompt
- **CHAT-FUT-04**: Stale-rules detection (CHAT-D6)
- **CHAT-FUT-05**: 30-second soft undo on send (CONFIRM-D1) — Gmail Send API limitation

### Personalization Differentiators
- **SET-VOICE-FUT-01**: Knowledge snippet auto-tagging (suggested when to apply)
- **SET-VOICE-FUT-02**: Per-recipient tone (formal for boss, casual for friends)
- **SET-VOICE-FUT-03**: Voice import from past sent mail (in-memory only, immediate discard)

### Tool Extensions (defer from v1.1)
- **TOOL-FUT-01**: Learned patterns (requires learning loop + persisted derived features — privacy review needed)
- **TOOL-FUT-02**: Multi-rule selection (advanced — allow AI to apply multiple rules per email)
- **TOOL-FUT-03**: Browser extension sync (Inbox Zero Tabs extension)
- **TOOL-FUT-04**: Sender categorization tools
- **TOOL-FUT-05**: Calendar tools (require new Google Calendar scope)
- **TOOL-FUT-06**: Attachment reading tools (require Drive scope or attachment storage)

---

## Out of Scope

Explicit exclusions for v1.1. Each row carries the reason so we don't silently re-add later.

| Feature | Reason |
|---------|--------|
| Auto-send (rule-triggered, no per-message user click) | Permanent — locked architectural invariant from v1.0; single bad auto-send is trust-ending. Auto-send opt-in deferred to v2 with per-rule approval flow |
| Webhook actions (custom URLs called from rules) | Not in v1 write-allow-list; defer to v2 |
| Long-term persistence of raw email body, email-content LLM prompts/completions, or embeddings | Permanent privacy invariant from v1.0 (carve-out for user-typed chat config and structured tool I/O is locked in `CLAUDE.md` Constraints) |
| Hidden links in AI drafts | Trust violation — Zero Mail never obscures URLs in drafts |
| Referral signature ("Drafted by Zero Mail" link) | Marketing chrome, not Zero Mail's GTM |
| "Auto-send if confidence ≥ X" toggle | Locked NO — same reason as auto-send above |
| Per-rule safety net override | Defeats the safety net's purpose |
| Free-text model id input | Typos → silent failures; curated list only |
| Per-call provider switching via chat slash commands | Per-call model pin (existing v1.0 LLM-02) is sufficient |
| Local LLM (Ollama) support | Out of scope; defer to v2 |
| Image attachments in chat | Defer to v1.2 (Spring AI multimodal works but adds upload-validation/sanitization scope) |
| `reconnectToStream` for chat | `vercel/ai#14027` crashes; document as explicit non-feature; "Retry" button instead |
| WebSockets / STOMP for chat | SSE sufficient; `@stomp/stompjs` already present for a different feature |
| Vercel AI SDK `ai` package on Java backend | TypeScript-only; would split LLM gateway |
| Frontend-side AI SDK provider adapters (`@ai-sdk/openai` etc.) | Would leak tenant keys to browser |

---

## Traceability

Phase-to-requirement mapping (populated by roadmapper on 2026-05-17 during v1.1 roadmap creation).

| Requirement | Phase | Status |
|-------------|-------|--------|
| CHAT-01 | Phase 7 | Pending |
| CHAT-02 | Phase 7 | Pending |
| CHAT-03 | Phase 7 | Pending |
| CHAT-04 | Phase 7 | Pending |
| CHAT-05 | Phase 7 | Pending |
| CHAT-06 | Phase 7 | Pending |
| CHAT-07 | Phase 7 | Pending |
| CHAT-08 | Phase 7 | Pending |
| ARCH-01 | Phase 7 | Pending |
| ARCH-02 | Phase 7 | Pending |
| ARCH-03 | Phase 7 | Pending |
| ARCH-04 | Phase 7 | Pending |
| ARCH-05 | Phase 7 | Pending |
| ARCH-06 | Phase 7 | Pending |
| ARCH-07 | Phase 7 | Pending |
| SET-SAFE-05 | Phase 7 | Pending |
| SET-AI-01 | Phase 8 | Pending |
| SET-AI-02 | Phase 8 | Pending |
| SET-AI-03 | Phase 8 | Pending |
| SET-AI-04 | Phase 8 | Pending |
| SET-VOICE-01 | Phase 8 | Pending |
| SET-VOICE-02 | Phase 8 | Pending |
| SET-VOICE-03 | Phase 8 | Pending |
| SET-VOICE-04 | Phase 8 | Pending |
| SET-VOICE-05 | Phase 8 | Pending |
| SET-VOICE-06 | Phase 8 | Pending |
| SET-BEHV-01 | Phase 8 | Pending |
| SET-BEHV-02 | Phase 8 | Pending |
| SET-BEHV-03 | Phase 8 | Pending |
| SET-BEHV-04 | Phase 8 | Pending |
| SET-BEHV-05 | Phase 8 | Pending |
| SET-SAFE-01 | Phase 8 | Pending |
| SET-SAFE-02 | Phase 8 | Pending |
| SET-SAFE-03 | Phase 8 | Pending |
| SET-SAFE-04 | Phase 8 | Pending |

**Coverage:**
- v1.1 requirements: 35 total (8 CHAT + 4 SET-AI + 6 SET-VOICE + 5 SET-BEHV + 5 SET-SAFE + 7 ARCH)
- Mapped to phases: 35
- Phase 7: 16 requirements (CHAT-01..08, ARCH-01..07, SET-SAFE-05)
- Phase 8: 19 requirements (SET-AI-01..04, SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04)
- Unmapped: 0

---

*Requirements defined: 2026-05-17*
*Last updated: 2026-05-17 — roadmap created with 2-phase split (Phase 7 chat + Phase 8 settings/hardening/GA); all 35 requirements mapped*
