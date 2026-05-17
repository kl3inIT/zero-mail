# Feature Research — v1.1: Chat Email Assistant + Settings Page

**Domain:** AI chat email assistant + assistant Settings UI (sidecar SaaS on top of Gmail)
**Researched:** 2026-05-17
**Overall confidence:** HIGH (Inbox Zero code inspected directly under `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/`; Spring AI 2.0.0-M6 + Vercel AI SDK / ai-elements verified via Context7)
**Scope:** **only the v1.1 milestone delta.** v1.0 shipped features (rules engine, triage hero, AI draft replies, BYOK, analytics, billing, web UI, onboarding) are described in `PROJECT.md` "Validated" and the previous `.planning/research/FEATURES.md` revision — **not re-researched here.**

---

## Executive Summary

v1.1 adds **two surfaces** on top of the shipped v1.0 backend:

1. **Chat-based email assistant** at a new `/chat` route — a multi-turn, tool-calling agent that can answer "what's in my inbox?", create/edit rules, search/read/label/archive email, save user-typed memories, and (under explicit user click) send/reply/forward email. Streams via Spring AI 2.0.0-M6 (`ChatClient.stream()`) on the backend, rendered with `@ai-sdk/react` `useChat` + the `ai-elements` component package on the frontend, using the Vercel Data Stream Protocol over SSE.
2. **Assistant Settings page** at `/settings` (or `/settings/ai`) — one screen that consolidates Provider/Model selection per feature (chat/triage/draft), Personalization (writing style + personal instructions + signature + knowledge base + tone + AI output language), Behavior toggles (auto-draft, draft confidence, follow-up reminders, daily digest, sensitive-data protection), and Sender Safety Net VIP management (UI surface for existing TRG-07..08).

The chat assistant is **not** a clone of Inbox Zero's full tool catalog (~30 tools). Zero Mail ports **~19 tools** chosen for v1.1: the ones that map onto v1.0 backend capabilities (rules engine, Gmail label/archive/draft writes, sender safety net) plus the **three user-confirmed send tools** (`sendEmail` / `replyEmail` / `forwardEmail`) — gated by a per-call UI preview card with an explicit Send button, audit-logged, and architecturally restricted to exactly one call site enforced by ArchUnit. We **do not** port sender categorization, learned patterns, calendar tools, attachment filing, browser-extension sync, hidden AI-draft links, or referral signatures — each has a specific reason called out in the Anti-Features section.

The **highest-risk feature** is the user-confirmed send flow. Inbox Zero's pattern is a clean state machine (`requiresConfirmation: true` → `pending` → `processing` → `confirmed`) backed by a server-side reservation lease that prevents double-send on UI replay. Zero Mail must implement this exact state machine — anything looser breaks the v1 "no auto-send" trust contract and the architectural invariant enforced since TRG-03.

The Settings page is the **lower-risk feature** but has dependencies on backend columns and config the v1.0 schema does not yet have. Personalization (writing style, personal instructions, signature, knowledge base, tone, output language) needs five new columns on `email_account` (or a dedicated `assistant_settings` aggregate); behavior toggles need three more booleans; sender safety net management exposes existing TRG-07..08 tables with no new schema. Provider/Model picker needs per-feature model overrides (chat-model, triage-model, draft-model) layered on top of the existing per-call model-pin mechanism in `LlmGateway`.

**For roadmap:** plan three sub-phases — (a) backend chat infrastructure + tool catalog + audit + ArchUnit, (b) Next.js chat UI on `@ai-sdk/react` + ai-elements with Vercel Data Stream Protocol contract, (c) Settings page wiring personalization columns + per-feature model picker + safety-net surface. Ordering is forced: (a) and (c-backend) must precede (b) and (c-frontend) since the OpenAPI client is the only API surface for the frontend.

---

## Feature Categories Overview (v1.1 only)

| Category | What it covers | Backend dep | Frontend dep | Risk |
|----------|---------------|-------------|--------------|------|
| **CHAT** | Streaming chat UI, tool-call cards, reasoning blocks, attachments, history sidebar | Spring AI ChatClient, `chat_session` + `chat_message` tables, SSE controller, Vercel Data Stream Protocol emitter | `@ai-sdk/react` v2 `useChat`, `ai-elements` package (`Conversation`, `Message`, `Reasoning`, `PromptInput`), tool-card components per-tool | HIGH (new streaming contract, new persistence) |
| **TOOL_CATALOG** | The 19 callable tools the assistant can invoke (search, read, label, manage, rule CRUD, memory, capabilities, confirmed send/reply/forward, personal instructions) | All tools route through existing v1.0 services; only 3 are new (memory store + capabilities snapshot + confirmed-send executor); ArchUnit carve-out for exactly 1 send call site | Tool-call rendering, confirm/edit/cancel cards | HIGH for confirmed-send tools, MEDIUM for read tools, LOW for rule tools (already validated in RULE-01..07) |
| **CONFIRMATION** | The state machine for risky tools: `requiresConfirmation` → preview card → user click → server action → audit row | New `assistant_send_audit` table, server-side reservation lease (Redis 5-min TTL), idempotency on `(chatId, toolCallId)`, ArchUnit single call-site grep | Preview card with Edit/Send/Cancel buttons, persisted-message check before allowing confirm | HIGH — trust contract surface |
| **SETTINGS_PROVIDER** | Provider/Model picker (OpenAI, Anthropic, Google GenAI, DeepSeek) per feature (chat, triage, draft) | Per-feature model override columns on `email_account` (or `assistant_settings`); already-shipped BYOK keys in `byok_credential`; per-call pin in `LlmGateway` | Select component per feature, model list per provider (curated, not free-form) | MEDIUM |
| **SETTINGS_PERSONALIZATION** | Writing style, personal instructions, signature, knowledge base, tone preset, AI output language (VI/EN) | New columns: `writing_style`, `personal_instructions`, `email_signature`, `tone_preset`, `ai_output_language`; new `assistant_knowledge_snippet` table; Liquibase YAML changeset | Textareas, language toggle, knowledge-snippet CRUD list | MEDIUM (mostly UI + schema) |
| **SETTINGS_BEHAVIOR** | Auto-draft toggle, draft confidence threshold, follow-up reminders, daily digest, sensitive-data protection | 3 new booleans on `email_account` (auto_draft_enabled, follow_up_reminders_enabled, sensitive_data_protection_enabled); reuse existing daily-digest config; new `draft_confidence_threshold` numeric | Switches + threshold slider | LOW |
| **SETTINGS_SAFETY** | Sender Safety Net VIP management UI (allow-list / never-archive / never-trash list) | Reuse existing TRG-07..08 tables (`sender_safety_entry`) — no new schema | List, add by email/domain, remove, search | LOW |

---

## CHAT Category

### Table Stakes — Chat UI (Users Expect These)

| # | Feature | Why Expected | Complexity | Inbox-Zero Ref | Notes |
|---|---------|-------------|------------|----------------|-------|
| CHAT-T1 | Message bubbles with `user` and `assistant` roles, scrollable conversation, auto-scroll-to-bottom-on-new-message with manual scroll detection | Every chat UI since ChatGPT 2022 has these; missing = "broken" | **S** | `messages.tsx` → `Conversation` + `Message` from `ai-elements` | Use `@ai-sdk/react` `useChat` + `Conversation`/`ConversationContent`/`ConversationScrollButton` from `ai-elements` |
| CHAT-T2 | Streaming text rendering (token-by-token append, animated cursor at end while streaming) | "Thinking..." → text appearing word-by-word is the AI-chat-app baseline | **M** | Vercel Data Stream Protocol over SSE | Backend emits Vercel Data Stream Protocol envelopes; frontend `useChat` v2 consumes natively |
| CHAT-T3 | "Thinking..." indicator while the LLM is selected/loaded but no tokens have arrived yet | First-token latency is 200ms–2s for big models; users need feedback during that window | **S** | `messages.tsx` lines 79–90 (`status === "submitted"` branch) | Show `<Loader />` + "Đang nghĩ..." (VI) / "Thinking..." (EN) |
| CHAT-T4 | Multi-turn history (assistant remembers context within a chat session) | A "chat" that forgets the previous message is not a chat | **M** | `chat_session` + `chat_message` tables; messages array passed to `ChatClient.prompt(...).messages(...)` | Need persistence (new tables) — see Dependencies |
| CHAT-T5 | Stop/cancel generation button while streaming | When the assistant goes off-rails, user must be able to stop | **S** | `chat.tsx` line 11 (`SquareIcon` for stop) | `useChat` exposes `stop()`; backend needs to honor SSE cancel (close stream + cancel `Flux`) |
| CHAT-T6 | Tool-call cards rendered inline in conversation (not hidden) — one card per tool call, with collapsed/expanded states | If the assistant ran `searchInbox` users want to *see* what it searched | **M** | `tools.tsx` (1992 lines) renders per-tool cards; `SubtleToolCollapsible` for read tools, `CollapsibleToolCard` for write tools | One React component per tool; share `CollapsibleToolCard` chrome |
| CHAT-T7 | Tool input/output states: `input-available` (loading "Searching inbox...") → `output-available` (rendered result) → `error` (red banner) | The user needs to see progress for slow tools (inbox search can take 1–3s) | **S** | `message-part.tsx` lines 180–202 — `tool-searchInbox` state machine | Vercel SDK `part.state` is `input-streaming` → `input-available` → `output-available` |
| CHAT-T8 | Composer textarea with Enter-to-send, Shift+Enter newline, autosize, character/token counter on long messages | Standard chat input UX | **S** | `chat.tsx` lines 26–30 (`PromptInput`, `PromptInputTextarea`, `PromptInputSubmit`) | Use `ai-elements` `PromptInput*` components |
| CHAT-T9 | New chat button + chat history sidebar (list of previous sessions, click to load) | Without this, every refresh loses the conversation | **M** | `chat.tsx` line 8 (`HistoryIcon`, `PlusIcon`); `useChats` hook | Backend `chat_session` table; list endpoint paginated by `(emailAccountId, lastActivityAt)` |
| CHAT-T10 | Error surfacing: when a tool fails, show inline error card, not a silent retry; when LLM call fails (quota / model error / network), show retryable banner | Silent failures = users distrust the assistant | **S** | `message-part.tsx` lines 47–56 (`ErrorToolCard`); `chat-response-guard.ts` for user-visible tool failure messages | Use existing v1.0 LLM error envelope; map provider error codes → user-friendly messages |
| CHAT-T11 | Empty-state overview screen with example prompts ("Tìm email từ HR tuần này", "Tạo rule archive newsletter", "Reply email cuối cùng theo tone công việc") | Users do not know what to ask an AI without prompting | **S** | `overview.tsx` referenced in `messages.tsx` line 2 | 3–5 starter prompts, click to populate composer |
| CHAT-T12 | Send button disabled while streaming; submit-on-empty no-op | Prevents double-fire and empty messages | **S** | `chat.tsx` submit flow | Standard form discipline |

### Differentiators — Chat UI

| # | Feature | Value Proposition | Complexity | Inbox-Zero Ref | Notes |
|---|---------|-------------------|------------|----------------|-------|
| CHAT-D1 | **Reasoning blocks** — when the model returns reasoning tokens (Anthropic extended thinking, OpenRouter `reasoning.max_tokens`), render as collapsible "Show reasoning" block separate from main answer | Power users want to see *why* the assistant chose to archive vs label vs trash | **M** | `message-part.tsx` lines 79–88 (`Reasoning`/`ReasoningTrigger`/`ReasoningContent` from `ai-elements`); `chat.ts` line 56 `ASSISTANT_CHAT_REASONING_MAX_TOKENS = 100` | Only Anthropic + some OpenRouter routes emit reasoning today; gracefully hide block if `part.text` empty or `[REDACTED]` (line 81) |
| CHAT-D2 | **Inline email cards from search results** — when assistant returns `<email threadid="...">` or `<emails>` blocks in markdown, render as clickable email rows that deep-link to Gmail | Avoids dumping raw Gmail URLs into chat text | **M** | `assistant-inline-email-response.tsx`; `inline-email-card.tsx`; the `<emails>`/`<email>` parsing rule in system prompt (line 800–812) | Frontend parses assistant markdown; backend's system prompt instructs the LLM to emit this format |
| CHAT-D3 | **Per-tool inline preview** for write tools (rule create preview, send-email preview, save-memory preview) with Edit + Confirm / Cancel | Confirmation pattern from Inbox Zero is best-in-class for AI-with-write-access products | **L** | See full state machine under CONFIRMATION category | Critical for "no auto-send" trust contract — see CHAT-D3 ↔ CONFIRMATION-T1 link |
| CHAT-D4 | **Image attachments** (drag/drop or paste; max 5 files, 4MB each, image-only — JPEG/PNG/WebP/GIF) sent to multimodal models | "Help me reply to this screenshot of an invoice" workflow | **M** | `chat.tsx` lines 37–44 (MAX_FILES=5, MAX_FILE_SIZE=4MB, image-only); `preview-attachment.tsx` | Spring AI multimodal `Media` API; defer for v1.1 if scope tight (mark as **stretch goal**) |
| CHAT-D5 | **Context-pack injection** — first message in a session automatically includes inbox stats ("210 emails, 47 unread") and freshest rule snapshot, hidden from the visible message but in the model context | Assistant feels "aware" without the user explaining their inbox state | **M** | `chat.ts` lines 176–183 (`inboxContextMessage`); `loadFreshRuleContext` lines 326–353 | Backend builds the synthetic system message; never persist into `chat_message` table |
| CHAT-D6 | **Stale-rules detection** — assistant detects when shown rule state is older than current rule state, auto-refreshes via `getUserRulesAndSettings` instead of operating on stale view | Prevents the "I created the rule but you keep saying it doesn't exist" loop | **M** | `chat.ts` lines 100–104, 137–155; `chat-rule-state.ts` (RuleReadState pattern) | Optional for v1.1 if rule edits via chat are rare; **defer if scope tight** |
| CHAT-D7 | **Vietnamese-default chat experience** — all assistant chrome (status labels, error messages, confirm buttons, empty-state prompts) in Vietnamese; assistant replies in Vietnamese unless user writes in English (locked by AI output language setting) | Target market is Vietnam beta first (locked in v1.0 i18n direction) | **S** | n/a (Inbox Zero is English-only) | Reuse v1.0 i18n infra; system prompt sets output language from `assistant_settings.ai_output_language` |

### Anti-Features — Chat UI (Do NOT Port)

| # | Feature | Why Inbox Zero Has It | Why Zero Mail Does NOT | Alternative |
|---|---------|----------------------|------------------------|-------------|
| CHAT-A1 | **Messaging-channel hint** ("This conversation is also visible in Slack/Telegram") | Inbox Zero ships a Slack/Telegram messaging bot integration | Out of scope for v1.1 (no messaging platform — SEED-007 deferred to v2) | Remove `MessagingChannelHint` import |
| CHAT-A2 | **Calendar tools** (`getCalendarEvents`) | Inbox Zero has optional Google Calendar integration | Out of scope (PROJECT.md "no GCP starter"; calendar scope deferred per SEED-001 Track A; no Calendar OAuth scope in v1) | Remove `getCalendarEvents` from tool list |
| CHAT-A3 | **Sender categorization tools** (`getAccountOverview`, `getSenderCategoryOverview`, `startSenderCategorization`, `getSenderCategorizationStatus`, `manageSenderCategory`) | Inbox Zero has a "categorize all senders" bulk feature | Defer to v1.2 — feature drawer is wide (UI list + backend job + state machine + redis progress); not a v1.1 differentiator | Note in PROJECT.md as deferred |
| CHAT-A4 | **Attachment filing tools** (`readAttachment`, attachment-to-Drive automation) | Inbox Zero auto-files attachments to Google Drive | Out of scope (no Google Drive OAuth, no Drive integration in v1.1) | None |
| CHAT-A5 | **`updateLearnedPatterns` + `getLearnedPatterns`** | Inbox Zero learns from user rule corrections to refine rule matching | No learning loop in v1.1 — would require persisting more user-mail metadata than the privacy posture allows; defer to v2 (see SEED-001 anti-feature list) | None — leave LearnedPatterns out of tool catalog |
| CHAT-A6 | **Sync-to-extension setting / SyncToExtension tool** | Inbox Zero has a Chrome extension that mirrors rules into Gmail's native UI | No browser extension in v1.1 (scope: SaaS web app only) | None |
| CHAT-A7 | **Hidden AI-draft links setting** (auto-inject tracking pixel into AI drafts) | Inbox Zero offers this for analytics | **Trust violation** — hidden links in user-drafted email is exactly the kind of "AI did something I didn't see" that breaks trust. Documented in `feedback_bundled_oauth_scopes.md` memory as a refused pattern. | None — never ship |
| CHAT-A8 | **Referral signature toggle** (auto-append "Sent with Inbox Zero" to AI drafts) | Inbox Zero growth loop | **Marketing chrome, not user value** — users will not knowingly opt in; default-on is dishonest. Zero Mail growth is separate from product. | Don't include in Settings |
| CHAT-A9 | **Multi-rule selection setting** (allow rules to act on N>1 matching message via LLM) | Inbox Zero has this as an advanced option | Defer to v1.2 (advanced feature, hard to evaluate quality, easy to defer) | Keep single-rule-per-message in v1.1 |
| CHAT-A10 | **Two-way prompt-file ↔ database sync** for personal instructions (the source-of-truth tension Inbox Zero ARCHITECTURE.md flags as "messy") | Inbox Zero evolved this organically | Don't build it — Zero Mail's structured rule AST (validated in v1.0 RULE-01..07) is the source of truth. Chat `updatePersonalInstructions` writes to `assistant_settings.personal_instructions` only; nothing syncs back to rules. | One-way: chat → settings.personal_instructions only |
| CHAT-A11 | **Inline file attachments other than images** (PDF, docx) | Inbox Zero hints at PDF attachments for multimodal models | Defer — image-only is enough for v1.1, PDF parsing adds 3–4 dependencies + a sanitization story | Image-only validation in upload flow |

---

## TOOL_CATALOG Category

The assistant's tool catalog is **the contract between the chat UI and v1.0 backend capabilities.** Each tool is one allowed action the assistant can request; the LLM never reaches a Gmail/DB call directly.

### Must-Have Tools (Table Stakes) — 19 tools for v1.1

| # | Tool name | Category | Confirm? | What it does | Maps to v1.0 backend | Complexity (new code) |
|---|-----------|----------|---------|--------------|---------------------|----------------------|
| TOOL-T1 | `getAssistantCapabilities` | Read | No | Returns "what tools/settings does this assistant support right now" so the model can answer "what can you do?" without hallucinating | New read-only service over a static capability map | **S** |
| TOOL-T2 | `getUserRulesAndSettings` | Read | No | Returns the user's current rules (id, name, when, then, enabled) + settings snapshot | `RuleRepository.findByEmailAccountIdOrderByPriority` + `EmailAccount` row | **S** |
| TOOL-T3 | `getRuleExecutionForMessage` | Read | No | Given a `messageId`, returns "which rule(s) matched, why, and what happened" | Existing v1.0 audit table (TRG-05) | **S** |
| TOOL-T4 | `searchInbox` | Read | No | Gmail search by query (`from:`, `subject:`, `is:unread`, `after:`, etc.); returns max 20 results with `(messageId, threadId, subject, from, snippet, date, isUnread)` — bodies NOT returned to assistant | Gmail API `users.messages.list` + per-message metadata fetch; short-lived in-memory cache only (PROJECT.md privacy carve-out) | **M** |
| TOOL-T5 | `readEmail` | Read | No | Given a `messageId` (from search), returns subject + from + to + date + content (sanitized + truncated to 4k tokens as in v1.0 LLM-05..08) | Gmail API `users.messages.get` + existing sanitization pipeline; content kept in-memory only | **M** |
| TOOL-T6 | `listLabels` | Read | No | Returns Gmail labels available on this account | Gmail API `users.labels.list` | **S** |
| TOOL-T7 | `createOrGetLabel` | Write (label) | No | Given a label name, returns labelId; creates if missing | Gmail API `users.labels.create` (idempotent on name) | **S** |
| TOOL-T8 | `manageInbox` | Write (label/archive/read) | No (direct user-request execution) | Bulk operation: archive_threads, label_threads, mark_read_threads on a list of `threadIds`; also `bulk_archive_senders` (archive all threads from a sender list) | Gmail API batch; existing label/archive paths already audited under TRG-01..05 | **M** |
| TOOL-T9 | `createRule` | Write (rule) | **Yes (preview card)** | Creates a new rule from NL → AST; emits `requiresConfirmation: true` + AST preview; only commits after user clicks "Create & enable" | Existing v1.0 NL→AST compiler (RULE-01..02) + `RuleService.create` | **M** |
| TOOL-T10 | `updateRuleConditions` | Write (rule) | Direct (no preview) but bounded | Updates an existing rule's `when` clause | Existing v1.0 `RuleService.updateConditions` | **S** |
| TOOL-T11 | `updateRuleActions` | Write (rule) | Direct (no preview) but bounded | Updates an existing rule's `then` clause | Existing v1.0 `RuleService.updateActions`; reject any action type outside {label, archive, save_draft} | **S** |
| TOOL-T12 | `deleteRule` | Write (rule) | **Yes (single-button confirm — soft-confirm in card)** | Deletes a rule by id | Existing v1.0 `RuleService.delete` | **S** |
| TOOL-T13 | `updatePersonalInstructions` | Write (settings) | No | Appends or replaces `assistant_settings.personal_instructions` (mode: `append` | `replace`) | New write on personalization column | **S** |
| TOOL-T14 | `updateAssistantSettings` | Write (settings) | No | Generic key-path setter for supported settings (writing_style, signature, tone_preset, ai_output_language, behavior toggles, draft_confidence_threshold) | New write on `assistant_settings` (or `email_account` columns); schema-validated allowed-path list | **M** |
| TOOL-T15 | `addToKnowledgeBase` | Write (knowledge) | No | Appends a titled snippet to the knowledge base | New `assistant_knowledge_snippet` table | **S** |
| TOOL-T16 | `saveMemory` | Write (memory) | **Yes (preview card, deduplicated)** | Saves a fact the user asked to remember (e.g., "Tôi đang work với Acme Corp về dự án X") | New `assistant_memory` table; dedup on `content` hash within emailAccount | **M** |
| TOOL-T17 | `searchMemories` | Read | No | Returns the assistant's memories matching a query string (substring + recency) | New read on `assistant_memory` | **S** |
| TOOL-T18 | `sendEmail` | **Send (HIGH RISK)** | **Yes (preview card, edit + Send + Cancel)** | Composes a new email; returns `pendingAction` payload with `requiresConfirmation: true`; only sends on per-message user click | New `assistantSendExecutor` — the ONLY send call site in v1.1 codebase; ArchUnit enforced; audit row written before Gmail call | **L** |
| TOOL-T19 | `replyEmail` | **Send (HIGH RISK)** | **Yes (preview card, edit + Send + Cancel)** | Composes a reply to a given `messageId`; same confirmation flow as sendEmail; uses Gmail thread headers (`In-Reply-To`, `References`) from referenced message | Same executor as sendEmail; reuses v1.0 DRFT-02 header-stamping | **L** |
| TOOL-T20 | `forwardEmail` | **Send (HIGH RISK)** | **Yes (preview card, edit + Send + Cancel)** | Forwards a `messageId` to recipients with optional note; same confirmation flow | Same executor as sendEmail | **L** |

**Tool count: 20 listed.** The PROJECT.md target of "~19 tools" matches if `deleteRule` is folded into `updateRuleActions` semantics (set actions to `[]` + disable). Recommended: keep `deleteRule` as a distinct tool with explicit confirm — it's a destructive operation users will phrase as "xóa rule X" and the model should not have to discover the trick of "edit actions to empty".

### Nice-to-Have Tools (Differentiators, defer if scope tight)

| # | Tool name | Why nice | Complexity | Recommendation |
|---|-----------|---------|------------|----------------|
| TOOL-D1 | `getInboxStats` | Lets assistant proactively summarize ("you have 47 unread; 12 are 'To Reply'") | **S** | Include — used in CHAT-D5 context-pack; backend already has the data |
| TOOL-D2 | `previewRuleOnRecentInbox` | Lets assistant run the new rule against last 50 messages and show "this would archive 8" | **M** | Reuse v1.0 RULE-05 (side-effect-free preview) — high value, low new code |
| TOOL-D3 | `pauseAllRules` / `resumeAllRules` | "Pause everything for the next 2 hours" via chat | **S** | Reuse v1.0 MAIL-06 global pause toggle; trivial |
| TOOL-D4 | `addVipSender` / `removeVipSender` | Manage sender safety net via chat | **S** | Reuse v1.0 TRG-07..08 tables; expose what Settings UI exposes |

### Anti-Feature Tools (Do NOT Port)

| # | Tool name (in Inbox Zero) | Why NOT in Zero Mail v1.1 |
|---|--------------------------|---------------------------|
| TOOL-A1 | `getCalendarEvents` | No Calendar scope — see CHAT-A2 |
| TOOL-A2 | `readAttachment` | No attachment support in v1.1 — see CHAT-A4 |
| TOOL-A3 | `getLearnedPatterns` / `updateLearnedPatterns` | No learning loop in v1.1 — see CHAT-A5 |
| TOOL-A4 | `manageSenderCategory` / `getSenderCategoryOverview` / `startSenderCategorization` / `getSenderCategorizationStatus` / `getAccountOverview` | Sender categorization deferred — see CHAT-A3 |
| TOOL-A5 | `updateAssistantSettingsCompat` (fallback duplicate) | Inbox Zero has two settings tools because of schema-evolution churn; Zero Mail starts fresh with one strict schema, no need for compat fallback |
| TOOL-A6 | Webhook action tool / webhook automations | Webhook actions are explicitly NOT in v1 write-allow-list (PROJECT.md: only label/archive/save_draft + chat-confirmed send/reply/forward) |

---

## CONFIRMATION Category (the CRITICAL pattern)

This is the v1.1 milestone's load-bearing safety mechanism. "User-confirmed send" is the only carve-out from v1.0's "no auto-send" architectural rule, and it must be unambiguously a per-message, per-click action — never a rule-firing-triggered send.

### State Machine

```
LLM emits tool call (sendEmail / replyEmail / forwardEmail / createRule / saveMemory / deleteRule)
   │
   ▼
Backend tool executor:
   - Validates input schema
   - Writes a `pending_action` row to `assistant_pending_action` with state=pending, leasedUntil=null
   - Returns tool output:
       {
         requiresConfirmation: true,
         confirmationState: "pending",
         pendingAction: { to, subject, messageHtml | content, cc?, bcc? },
         reference: { messageId?, threadId?, from? },  // for reply/forward
         riskMessages: [ ... ]                          // for createRule
       }
   - DOES NOT call Gmail API yet
   │
   ▼
Frontend chat UI renders preview card with:
   - Edit button (opens textarea on body)
   - Cancel button (calls a cancel action that marks pending_action row as canceled)
   - Send / Confirm button (DISABLED until message is persisted to `chat_message` table — see "disableConfirm" reasoning below)
   │
   ▼ user clicks Send
   │
Frontend calls confirm action: confirmAssistantEmailAction({ chatId, toolCallId, actionType, contentOverride? })
   │
   ▼
Backend confirm action:
   1. RESERVATION: SELECT pending_action FOR UPDATE; if state=processing AND leasedUntil > now() → return error "already in progress"
                   else SET state=processing, leasedUntil=now()+5min in same tx
   2. EXECUTE: call Gmail API (send/reply/forward) — outside the tx
   3. AUDIT: write `assistant_send_audit` row {emailAccountId, chatId, toolCallId, actionType, to, subject, messageId, threadId, sentAt}
   4. PERSIST: update pending_action state=confirmed, confirmationResult={messageId, threadId, sentAt}
   5. RETURN: { success: true, confirmationState: "confirmed", confirmationResult: { actionType, messageId, threadId, to, subject, confirmedAt } }
   6. On Gmail-API failure: revert pending_action state to pending, clear leasedUntil, return error to UI
   │
   ▼
Frontend renders "Sent ✓" state on card; subsequent UI replays of this tool-call rendering see confirmationState="confirmed" + confirmationResult, render in "already sent" mode (no Send button)
```

### Why `disableConfirm` and "isPersistedMessage" exist

The Inbox Zero pattern (`message-part.tsx` lines 691–700, `tools.tsx` lines 720–789) **disables the Send button until the chat message containing this tool call has been persisted to the DB.** Reason: if the user clicks Send before the assistant turn finishes streaming and gets persisted, the toolCallId might not yet exist on the server, so the confirm endpoint would 404. The frontend tracks `persistedMessageIds` (a `Set<string>`) via the `ChatProvider`; only after the SSE stream completes and the message is saved does the Send button enable.

**Zero Mail implementation note:** Same pattern is required. Backend persists `chat_message` rows after the SSE turn completes (`onStepFinish` / completion callback). Frontend tracks persisted IDs in a context and gates the Send button on `disableConfirm || !isPersistedMessage`.

### Why a server-side lease

If the user double-clicks Send (network slow, button feedback weak), without a server-side lease the same email gets sent twice. Inbox Zero uses a 5-minute lease (`CONFIRMATION_PROCESSING_LEASE_MS = 5 * 60 * 1000` in `assistant-chat.ts` line 38) — once leased, a second confirm call returns "already in progress" instead of re-sending. Zero Mail should use Redis for this lease (already in stack), keyed on `(emailAccountId, chatId, toolCallId)`.

### ArchUnit Enforcement

PROJECT.md line 157: "Auto-send forbidden" architecturally. v1.0 enforced this with `TRG-03` ArchUnit test + repo-wide grep that asserts **zero send call sites** in the codebase.

v1.1 carve-out: **exactly one** send call site — the `assistantSendExecutor` inside the chat confirm path. ArchUnit rule must be updated to:

```
public static final ArchRule onlyOneSendCallSite = classes()
    .that().areAnnotatedWith(GmailSendCallSite.class)
    .should().haveSimpleName("AssistantSendExecutor")
    .andShould().resideInAPackage("..chat.confirm..");
// Plus a grep gate in CI: count of `gmailService.send(` must equal 1.
```

### State Machine — Confirmation Features

| # | Feature | Why expected | Complexity | Inbox-Zero Ref |
|---|---------|-------------|------------|----------------|
| CONFIRM-T1 | `requiresConfirmation: true` flag on tool output for send/reply/forward/createRule/saveMemory/deleteRule | The single field that triggers preview-card rendering | **S** | `tools.tsx` line 540 (`getOutputField<boolean>(output, "requiresConfirmation") === true`) |
| CONFIRM-T2 | Preview card with Edit + Send + Cancel buttons | Standard UX for "review before commit" | **M** | `tools.tsx` lines 511–793 (`EmailActionResult`) |
| CONFIRM-T3 | Server-side reservation lease via Redis (5-min TTL) on `(chatId, toolCallId)`; second confirm returns 409 | Prevents double-send | **M** | `assistant-chat.ts` lines 34–44, 156–173 |
| CONFIRM-T4 | Audit row written to `assistant_send_audit` before Gmail API call returns; row links `chatId` + `toolCallId` + resulting `messageId` | Compliance + undo + ArchUnit enforcement | **S** | New table; analogous to v1.0 TRG-05 |
| CONFIRM-T5 | Send button disabled until chat message is persisted (`persistedMessageIds.has(messageId)` check) | Prevents 404 from clicking too fast | **S** | `tools.tsx` line 551; `messages.tsx` line 30, 67 |
| CONFIRM-T6 | "Already sent" state on replay: card renders with green checkmark, no Send button, links to sent message in Gmail | Idempotent UI when chat history reloads | **S** | `tools.tsx` lines 552–556, 664–672 |
| CONFIRM-T7 | `contentOverride` in confirm payload — if user edited the body via the Edit button, send the edited body, not the LLM's original | Edit-before-send is the whole point of the preview | **S** | `tools.tsx` lines 611–617 |
| CONFIRM-T8 | Cancel action: marks pending_action canceled, removes preview card, allows assistant to acknowledge | User must be able to back out without sending anything | **S** | (Inbox Zero does not have an explicit Cancel action — Zero Mail should add one since it's expected) |
| CONFIRM-T9 | Per-tool risk message ("This rule can send email automatically. Review it before enabling.") shown above preview when `riskMessages` non-empty | Explains why confirmation is required | **S** | `tools.tsx` lines 1080–1098 (`PendingCreateRuleCardContent`) |
| CONFIRM-T10 | After confirmation, success toast + assistant text update ("Email sent.") | Closes the loop | **S** | `tools.tsx` lines 633–636, 1774–1777 |

### Differentiators — Confirmation

| # | Feature | Value | Complexity | Recommendation |
|---|---------|-------|------------|----------------|
| CONFIRM-D1 | **Soft 30-second undo** after a confirmed send (display "Đã gửi · Hoàn tác" link for 30s; if clicked within window, attempts to delete from sent + recall via Gmail draft) | Symmetry with v1.0 30-day undo on triage; reduces "oh no I sent the wrong one" panic | **L** | Defer to v1.2 — Gmail Send API does not support reliable unsend; would need to implement client-side delay-then-send pattern (Gmail's "Undo Send" feature is client-side delay). Out of scope for v1.1 backend. |
| CONFIRM-D2 | **Diff view on rule edit** ("you changed: 'newsletter' → 'newsletter OR promotional' in conditions") | Helps users review before confirm | **S** | Already in Inbox Zero (`tools.tsx` lines 1531–1571 `ViewChangesCollapsible`) — easy port |
| CONFIRM-D3 | **Suspicious-sender warning** — when chat is triggered by an email asking for setup/credentials/webhook from an unexpected sender, system prompt warns user before acting | Protects against prompt-injection / social-engineering via incoming mail | **S** | `chat.ts` line 720–723 (`Write and confirmation policy` system-prompt section); include in our system prompt |

### Anti-Features — Confirmation

| Anti-feature | Reason |
|--------------|--------|
| "Confirm send by typing 'yes'" or any text-based confirm | Confirm must be a deliberate UI click, not chat-text — text confirms can be triggered by prompt-injected email content the assistant reads |
| Auto-confirm if confidence > 95% | Defeats the architectural invariant. Confirmation is a trust boundary, not a UX inconvenience to optimize away. |
| Bulk-confirm multiple pending sends | Each send requires its own click. If user wants to send 10 emails, click 10 times. Prevents accidental fan-out. |
| Skip-confirm for replies to threads the user originated | Sounds reasonable; in practice, "user originated this thread" is hard to verify and one bad assumption = trust loss. |

---

## SETTINGS_PROVIDER Category

### Table Stakes

| # | Feature | Why expected | Complexity | Notes |
|---|---------|-------------|------------|-------|
| SET-P1 | Provider list: OpenAI, Anthropic, Google GenAI, DeepSeek (the 4 BYOK-supported providers from v1.0 LLM-03) | Users with existing API keys want to pick their preferred provider | **S** | Static enum; matches v1.0 BYOK starter list |
| SET-P2 | Per-feature model picker — separate model selection for `chat`, `triage`, `draft` (so chat can use a fast/cheap model while triage uses higher-quality) | Cost vs quality is a per-task tradeoff | **M** | 3 columns `chat_model_id`, `triage_model_id`, `draft_model_id` on `assistant_settings` (or `email_account`); each is `(providerId, modelId)` pair |
| SET-P3 | Curated model list per provider (e.g., for Anthropic: `claude-opus-4-5`, `claude-sonnet-4-5`, `claude-haiku-4`; for OpenAI: `gpt-5`, `gpt-5-mini`, `gpt-5-nano`) — not free-form text input | Prevents users typing typos and breaking AI features | **S** | Static catalog in code; backend rejects model ids not in catalog |
| SET-P4 | BYOK key entry per provider with AES-GCM encryption at rest (already v1.0 LLM-04) — provider key field + "Test connection" button | Users need to know their key works before relying on it | **M** | Reuse v1.0 `byok_credential` table; "Test connection" hits provider `models` endpoint (lightweight, no token cost) |
| SET-P5 | "Use Zero Mail default (OpenRouter)" toggle vs "Use my key" — explicit two-mode | Users mix platform credits + BYOK across features (e.g., chat = BYOK Anthropic, triage = platform OpenRouter) | **S** | Boolean `byok_enabled_for_feature` per feature |
| SET-P6 | Per-feature cost estimate ("≈ 12 credits / 1000 messages for chat with sonnet-4-5") shown next to model picker | Cost transparency is a trust feature | **M** | Multiply v1.0 LLM cost table × default token estimates per feature |

### Differentiators

| # | Feature | Value | Complexity |
|---|---------|-------|------------|
| SET-PD1 | Per-call model pin via chat ("dùng claude-opus cho câu này") — chat respects override for one turn | Power users want surgical control | **M** — already supported by v1.0 LLM-02 per-call pin; expose via chat UI hint |
| SET-PD2 | Show last-30-days usage by model (which model used how many credits, which BYOK key sent how many tokens) | Cost analytics | **M** — link to v1.0 analytics page filtered by model |

### Anti-Features

| Anti-feature | Reason |
|--------------|--------|
| Free-text model id input | Typos → silent failures; LLM gateway rejects unknown ids anyway; bad UX |
| Per-call provider switching via chat ("/openai gpt-5 please") | Inbox Zero allows this; Zero Mail's per-call pin is enough; slash commands add UI complexity |
| Bedrock / Azure / Vertex / Groq / Perplexity providers in v1.1 | Out of scope (PROJECT.md `Deferred to v1.2: provider expansion`) |
| Local LLM (Ollama) support | Out of scope; defer to v2 |

---

## SETTINGS_PERSONALIZATION Category

### Table Stakes

| # | Feature | Why expected | Complexity | Inbox-Zero Ref | Storage | Dependency on v1.0 |
|---|---------|-------------|------------|----------------|---------|--------------------|
| SET-P1 | **Writing style** — free-text "Describe how you write" (200–500 words; influences AI draft tone) | Drafts that sound like you require a style description | **S** | `WritingStyleSetting.tsx` | New column `assistant_settings.writing_style TEXT NULLABLE` | **Allowed** by privacy carve-out (user-typed input persists) |
| SET-P2 | **Personal instructions** (a.k.a. "About") — free-text "Tell the AI about you" (role, company, current projects); injected into system prompt for chat + triage + draft | Context that doesn't fit in rules but shapes every AI decision | **S** | `AboutSetting.tsx`, `update-personal-instructions-tool.ts` | New column `assistant_settings.personal_instructions TEXT NULLABLE` | Allowed (user-typed) |
| SET-P3 | **Email signature** — free-text signature appended to AI drafts (separate from Gmail's native signature, since Gmail signature doesn't always work via API) | Without a signature, AI drafts look unsigned | **S** | `PersonalSignatureSetting.tsx` | New column `assistant_settings.email_signature TEXT NULLABLE` | Allowed (user-typed) |
| SET-P4 | **Knowledge base** — list of titled snippets (e.g., "Pricing for Acme deal", "Hours of operation", "Refund policy") the AI consults when drafting | Reusable reference material — "always include pricing when asked" | **M** | `DraftKnowledgeSetting.tsx`, `add-to-knowledge-base-tool.ts` | New table `assistant_knowledge_snippet (id, email_account_id, title, content, created_at)` | Allowed (user-typed) |
| SET-P5 | **Tone preset** — picker among `professional` / `friendly` / `casual` / `formal` / `custom`; sets a default tone hint for AI drafts | Quick way to set baseline without writing 200 words of writing style | **S** | Inbox Zero doesn't have this as a discrete setting (relies on writing_style only); Zero Mail adds it | New column `assistant_settings.tone_preset VARCHAR(32)` | None |
| SET-P6 | **AI output language** — toggle `VI` / `EN` (default VI for Vietnam beta); locks the language all AI features respond in | Zero Mail is Vietnamese-default; need an explicit override for English-speaking users | **S** | Inbox Zero is English-only — Zero Mail-specific | New column `assistant_settings.ai_output_language VARCHAR(8)` | Reuse v1.0 i18n locale infra |

### Differentiators

| # | Feature | Value | Complexity |
|---|---------|-------|------------|
| SET-PD1 | **Knowledge snippet auto-tagging** — when user adds a snippet, AI suggests when it should be used ("This looks like pricing info — apply when emails mention 'cost' or 'pricing'") | Reduces "AI never used my snippet" frustration | **M** — defer to v1.2 |
| SET-PD2 | **Per-recipient tone** — "When emailing my-boss@acme.com use formal; when emailing friends use casual" | Real productivity win but recipe complexity climbs | **L** — defer to v2 (would need contact-rule table) |
| SET-PD3 | **Voice import from past sent mail** — analyzes user's last N sent emails (in-memory only, immediate discard) to seed writing_style | One-time onboarding boost | **L** — defer; analyzing sent mail at scale needs careful privacy framing; v1.2 candidate |

### Anti-Features

| Anti-feature | Reason |
|--------------|--------|
| Persistent embeddings of user's past sent mail | Locked OUT by PROJECT.md privacy constraint |
| "Learned patterns" auto-updating tone per-recipient | Requires learning loop and persisted derived features (CHAT-A5); defer to v2 |
| Hidden AI-draft tracking link toggle | CHAT-A7 — trust violation, never ship |
| Referral signature toggle | CHAT-A8 — marketing chrome |

---

## SETTINGS_BEHAVIOR Category

### Table Stakes

| # | Feature | Why expected | Complexity | Inbox-Zero Ref | Storage | Dependency on v1.0 |
|---|---------|-------------|------------|----------------|---------|--------------------|
| SET-B1 | **Auto-draft replies enabled** — master switch for the v1.0 DRFT-01..04 AI draft feature; off = no AI drafts saved to Gmail | Some users want chat-only, no auto-drafts in their drafts folder | **S** | `DraftReplies.tsx` | New column `assistant_settings.auto_draft_enabled BOOLEAN DEFAULT true` | Wires to existing DRFT-04 |
| SET-B2 | **Draft confidence threshold** — slider 0.0–1.0; AI only saves a draft if its self-reported confidence ≥ threshold | Users want to suppress low-quality drafts | **S** | `DraftConfidenceSetting.tsx` | New column `assistant_settings.draft_confidence_threshold NUMERIC(3,2) DEFAULT 0.50` | Requires DRFT pipeline to emit confidence (small new piece of v1.0 DRFT) |
| SET-B3 | **Follow-up reminders** — for sent emails that don't get a reply within N days, surface a "Cần follow-up?" notification | Reply Zero parity (light) | **M** | `FollowUpRemindersSetting.tsx` | New column `assistant_settings.follow_up_reminders_enabled BOOLEAN DEFAULT false` + worker job that scans audit log | Defer to v1.2 if worker scope tight; **easy v1.1 stretch** |
| SET-B4 | **Daily digest** — enable the v1.0 ANL-03 email digest from this Settings page (it's currently in Settings but scattered) | Single Settings page = single config surface | **S** | `DigestSetting.tsx` | Reuse v1.0 ANL-03 config | Just UI wiring |
| SET-B5 | **Sensitive data protection** — when ON, AI redacts apparent emails/phone/credit-cards/SSNs from prompts before sending to provider (already in v1.0 LLM-05..08 but defaults to ON; setting lets user turn off for low-sensitivity accounts) | Compliance-conscious users want to see + control the redaction | **S** | (Inbox Zero doesn't expose this as a setting — Zero Mail adds for trust posture) | New column `assistant_settings.sensitive_data_protection_enabled BOOLEAN DEFAULT true` | Toggles existing v1.0 LLM-05 sanitizer behavior |
| SET-B6 | **Shadow mode toggle** — exposes v1.0 TRG-07 tenant-wide opt-in shadow mode (rules simulate, don't write); already in Settings v1.0, surface here for one-stop config | Lets new users dry-run rules safely | **S** | (Zero Mail-specific; ported from v1.0 TRG-07) | Reuse v1.0 TRG-07 column | Just UI wiring |

### Anti-Features

| Anti-feature | Reason |
|--------------|--------|
| "Auto-send replies if confidence ≥ X" toggle | **NEVER.** PROJECT.md line 122: auto-send is "single bad auto-send is trust-ending; opt-in narrow auto-send deferred to v2". |
| "Pause AI for the weekend" scheduler | Defer — schedule-based pause is feature creep; users can use global pause manually |
| "Snooze a specific email" | Inbox primitive that's not in v1.1 scope (SEED-004 deferred) |
| "Set custom action types" (run JavaScript, call my webhook) | Not in v1 write-allow-list; defer to v2 |

---

## SETTINGS_SAFETY Category (Sender Safety Net UI)

### Background

v1.0 shipped TRG-07..08:
- **Shadow mode** — tenant-wide opt-in where all rules SIMULATE actions instead of executing; audit log records the simulated decision; no Gmail write happens.
- **Sender safety net** — per-tenant list of senders/domains where automation is explicitly OPT-IN (default: rules do NOT auto-act on these senders even if they match). Used for VIPs ("never archive emails from my CEO automatically").

v1.0 has the DB tables and the safety policy enforcement, but **no end-user UI for managing the sender safety net** — entries can only be set via internal admin tooling. v1.1 closes this gap.

### Table Stakes

| # | Feature | Why expected | Complexity | Storage | Dependency on v1.0 |
|---|---------|-------------|------------|---------|--------------------|
| SET-S1 | **Sender safety list** — view + add + remove entries (email or domain); shows count of "saves" (times this entry prevented an action) | Users need control over "who is sacred" | **M** | Existing v1.0 table `sender_safety_entry` | Reuse v1.0 TRG-08 |
| SET-S2 | **Add-by-paste** — user pastes "vip@acme.com, ceo@startup.com, *@board.example" and gets a parsed preview before save | Bulk add is critical for VIPs at first setup | **S** | Same table | Same |
| SET-S3 | **Per-entry mode** — `protect` (never auto-act) vs `escalate` (notify user when rule wanted to act, but don't act) | Two safety levels, two intents | **S** | Need new column `mode VARCHAR(16)` on `sender_safety_entry` (or already present in TRG-08?) | Verify v1.0 schema; backfill if needed |
| SET-S4 | **Visual indicator in audit log** when a rule was blocked by safety net ("Was going to archive, blocked by VIP rule for ceo@acme.com") | Closes the loop — user sees the safety net working | **S** | Existing v1.0 audit log + new badge in `apps/web/triage` view | Reuse v1.0 TRG-05 + small frontend change |

### Anti-Features

| Anti-feature | Reason |
|--------------|--------|
| "Per-rule safety override" (this rule can act even on VIPs) | Defeats the safety net's purpose; if a rule should act, remove the sender from the safety list |
| Automatic safety-list seeding from contacts | Privacy + scope creep — defer |

---

## Feature Dependencies

```
v1.0 (shipped)
  ├── LLM gateway (LLM-01..11)
  │     └──────────────── used by → CHAT (Spring AI ChatClient), TOOL_CATALOG (tool-call wrapper)
  ├── Rules engine (RULE-01..07)
  │     └──────────────── exposed by → TOOL-T2/T9/T10/T11/T12 (rule CRUD tools)
  ├── Gmail label/archive/draft (TRG-01..04)
  │     └──────────────── exposed by → TOOL-T7/T8 (label/manageInbox tools)
  ├── Audit + undo (TRG-05..06)
  │     └──────────────── exposed by → TOOL-T3 (getRuleExecutionForMessage), CONFIRM-T4 (assistant_send_audit reuses pattern)
  ├── Sender safety net DB (TRG-07..08)
  │     └──────────────── exposed by → SETTINGS_SAFETY (UI only — no new backend)
  ├── BYOK credentials (LLM-03..04)
  │     └──────────────── exposed by → SETTINGS_PROVIDER (per-feature key picker)
  └── Per-call model pin (LLM-02)
        └──────────────── exposed by → SETTINGS_PROVIDER SET-P2 (per-feature default model)

v1.1 NEW
  ├── chat_session + chat_message tables ──────── required by ──→ CHAT-T4 (multi-turn), CHAT-T9 (history sidebar)
  ├── assistant_settings table (or columns on email_account) ── required by ──→ SETTINGS_PERSONALIZATION (T1..T6), SETTINGS_BEHAVIOR (B1..B6), SETTINGS_PROVIDER (P2 per-feature model)
  ├── assistant_knowledge_snippet table ──────── required by ──→ SET-P4 + TOOL-T15
  ├── assistant_memory table ──────────────────── required by ──→ TOOL-T16/T17 (save/search memory)
  ├── assistant_pending_action table ─────────── required by ──→ CONFIRMATION (state machine persistence)
  ├── assistant_send_audit table ──────────────── required by ──→ CONFIRM-T4 (mandatory before Gmail send)
  ├── AssistantSendExecutor class (the ONLY new Gmail send call site; ArchUnit-bound) ── required by ──→ TOOL-T18/T19/T20
  ├── Spring AI ChatClient adapter inside backend/core/llm/gateway/springai ── required by ──→ CHAT-T2 (streaming), TOOL-CATALOG (tool callbacks)
  ├── SSE controller emitting Vercel Data Stream Protocol envelopes ── required by ──→ CHAT-T2 + frontend useChat() v2 compatibility
  ├── apps/web `/chat` route + ChatProvider + useChat() v2 + ai-elements ── required by ──→ all CHAT-T* features
  └── apps/web `/settings/ai` route + per-section forms ── required by ──→ all SETTINGS_* features

Cross-feature
  ── CHAT-D3 (per-tool preview cards) DEPENDS ON CONFIRMATION state machine
  ── TOOL-T16 (saveMemory) DEPENDS ON CONFIRMATION (requires user confirm to persist memory)
  ── TOOL-T14 (updateAssistantSettings) DEPENDS ON SETTINGS_PERSONALIZATION + SETTINGS_BEHAVIOR schema being live first
  ── SET-PD1 (per-call model pin via chat) DEPENDS ON v1.0 LLM-02 (already shipped)
```

---

## MVP Definition for v1.1

### Launch With (v1.1 GA)

Minimum viable for the v1.1 milestone:

- [ ] **CHAT-T1..T8, T10..T12** — core chat experience (history sidebar T9 can be a follow-up if scope tight, but strongly recommended at GA)
- [ ] **TOOL-T1..T17, T18..T20** — all 19 must-have tools live (including the 3 confirmed-send tools)
- [ ] **CONFIRM-T1..T7, T9, T10** — full confirmation state machine end-to-end (CONFIRM-T8 Cancel can be a v1.1.1 follow-up)
- [ ] **SET-P1..P5** — provider/model picker for 4 providers, per-feature, with BYOK; SET-P6 cost estimate can be follow-up
- [ ] **SET-P1..P5 (personalization)** — writing style, personal instructions, signature, knowledge base, tone preset, AI output language
- [ ] **SET-B1, B2, B4, B5, B6** — behavior toggles (auto-draft, confidence threshold, daily digest enable, sensitive data, shadow mode surface)
- [ ] **SET-S1..S4** — sender safety net UI
- [ ] **CHAT-D7** — Vietnamese-default chat chrome (mandatory per v1.0 i18n direction)
- [ ] **ArchUnit single send call site test** + repo-wide grep updated to allow exactly 1 (down from 0)

### Add After Validation (v1.1.x patch releases)

- [ ] CHAT-T9 chat history sidebar (if punted from GA)
- [ ] CHAT-D1 reasoning blocks (works only with Anthropic + select OpenRouter routes)
- [ ] CHAT-D2 inline email cards from `<email>` markdown
- [ ] CHAT-D6 stale-rules detection (only matters once rule-edit-via-chat is a frequent flow)
- [ ] CONFIRM-T8 Cancel action
- [ ] CONFIRM-D2 rule edit diff view
- [ ] CONFIRM-D3 suspicious-sender warning in system prompt
- [ ] SET-PD1 per-call model pin via chat (UI already supports it server-side)
- [ ] SET-PD2 last-30-days usage by model
- [ ] SET-B3 follow-up reminders
- [ ] TOOL-D1..D4 nice-to-have tools

### Defer to v1.2+

- [ ] CHAT-D4 image attachments (multimodal)
- [ ] CHAT-D5 context-pack injection (inbox stats + rule snapshot) — implement if assistant feels "blind" to inbox state in user testing
- [ ] CONFIRM-D1 30-second soft undo on send
- [ ] SET-PD1/D2/D3 personalization differentiators
- [ ] All anti-features remain explicitly deferred or rejected

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority | Notes |
|---------|------------|--------------------|----------|-------|
| TOOL-T18/T19/T20 (send/reply/forward + CONFIRMATION) | HIGH | HIGH | **P1** | The defining v1.1 capability; high care budget required |
| CHAT-T1..T8 (core chat UI + streaming) | HIGH | MEDIUM | **P1** | Without this no chat exists |
| TOOL-T2/T9/T10/T11/T12 (rule CRUD via chat) | HIGH | LOW | **P1** | Backend already exists from v1.0 RULE-* |
| TOOL-T4/T5/T6/T7/T8 (search/read/label/manage) | HIGH | MEDIUM | **P1** | Backend mostly exists; new is the privacy-bounded read pipeline |
| SET-P1..P5 (provider picker + per-feature model) | HIGH | MEDIUM | **P1** | Differentiates Zero Mail from competitors who lock you in |
| SET-P1..P5 (personalization columns) | HIGH | MEDIUM | **P1** | Drafts without personalization feel generic |
| SET-S1..S4 (sender safety net UI) | MEDIUM | LOW | **P1** | Closes v1.0 UX gap on TRG-07..08 |
| SET-B1, B2, B4, B5, B6 (behavior toggles) | MEDIUM | LOW | **P1** | Mostly UI on existing backend |
| TOOL-T13/T14/T15/T16/T17 (settings + memory + knowledge tools) | MEDIUM | MEDIUM | **P1** | Required for chat to act as a config surface |
| TOOL-T1/T3 (capabilities + rule execution lookup) | MEDIUM | LOW | **P1** | Cheap, makes assistant more honest |
| CHAT-T9 (chat history sidebar) | MEDIUM | MEDIUM | **P2** | Important but cuttable if backend persistence slips |
| CHAT-D7 (Vietnamese chrome) | HIGH (for target market) | LOW | **P1** | Locked by v1.0 i18n direction |
| CHAT-D1 (reasoning blocks) | LOW (most models won't emit) | LOW | **P2** | Nice when available |
| CHAT-D2 (inline email cards) | MEDIUM | MEDIUM | **P2** | Quality-of-life win |
| CHAT-D3 (per-tool preview cards) | HIGH | covered by CONFIRMATION | **P1** | Same feature as CONFIRMATION-T1..T10 |
| CHAT-D4 (image attachments) | LOW for v1.1 | MEDIUM | **P3** | Defer to v1.2 |
| CHAT-D5 (context-pack injection) | MEDIUM | MEDIUM | **P2** | Add if assistant feels blind in user testing |
| CHAT-D6 (stale-rules detection) | LOW (rare flow) | MEDIUM | **P3** | Defer |
| TOOL-D1..D4 (nice-to-have tools) | LOW–MEDIUM | LOW | **P2** | Cheap adds; defer |
| SET-PD1..PD3 (personalization differentiators) | MEDIUM | MEDIUM–HIGH | **P3** | Defer |
| SET-B3 (follow-up reminders) | MEDIUM | MEDIUM | **P2** | Add as v1.1.x if worker capacity exists |
| CONFIRM-D1 (30s soft undo) | LOW (Gmail Send API limitation) | HIGH | **P3** | Defer |
| CONFIRM-D2 (rule edit diff view) | LOW–MEDIUM | LOW | **P2** | Cheap port from Inbox Zero |
| CONFIRM-D3 (suspicious-sender warning) | MEDIUM | LOW | **P2** | Cheap; add to system prompt |

---

## Competitor Feature Analysis (v1.1-scope only)

| Feature | Inbox Zero | Shortwave | Superhuman | Zero Mail v1.1 plan |
|---------|------------|-----------|------------|---------------------|
| Chat email assistant | Yes — ~30 tools, streaming, confirmation pattern on send | Yes — semantic search + chat over full mailbox history (requires full body storage) | Yes — Auto-Draft + Instant Reply via per-recipient learning (in-client) | **Port Inbox Zero's confirmation pattern + ~19-tool subset; no full-history semantic search (privacy)** |
| Provider/Model picker | Yes — 8+ providers including Bedrock/Azure/Groq/Perplexity | Hidden (Shortwave hosts) | Hidden (Superhuman hosts) | **Yes — 4 providers (OpenAI/Anthropic/Google/DeepSeek) + per-feature model picker; expanded providers deferred to v1.2** |
| User-confirmed send | Yes — `requiresConfirmation: true` state machine | Yes — explicit Send button in chat | Yes — Auto-Draft is review-then-send | **Yes — port Inbox Zero state machine exactly; ArchUnit-enforced single call site** |
| Personal instructions / writing style | Yes (separate settings) | Implicit via chat memory | Implicit via Auto-Draft training | **Yes — both as discrete fields; "writing style" + "personal instructions" remain conceptually separate** |
| Knowledge base | Yes (`DraftKnowledgeSetting`) | Implicit via mailbox semantic search | No | **Yes — explicit titled snippets table** |
| Sender safety net UI | Partial (in advanced settings) | No (relies on AI judgment) | No | **Yes — dedicated section; surfaces existing TRG-07..08** |
| Learned patterns / auto-tuning | Yes — `LearnedPatternsSetting` | Yes — semantic learning | Yes — per-recipient tone learning | **No — defer to v2 (privacy posture forbids the embeddings these need)** |
| Browser extension sync | Yes — `SyncToExtensionSetting` | Yes — native client | Yes — native client | **No — Zero Mail is web SaaS only in v1.x** |
| Calendar integration | Yes (`getCalendarEvents`) | Yes (deep) | Yes (Meeting Briefs) | **No — defer (SEED-006), no Calendar OAuth scope in v1** |
| Hidden draft tracking links | Yes (`HiddenAiDraftLinksSetting`, opt-in) | No | No | **No — never (trust violation, anti-feature)** |
| Multi-rule selection | Yes (advanced toggle) | Implicit (AI picks) | Implicit | **No — defer to v1.2 (single-rule-per-message in v1.1)** |
| Vietnamese-language chat | No | No | No | **Yes — default; matches v1.0 i18n direction** |

---

## Sources

**Primary (HIGH confidence — code inspected):**
- Inbox Zero `apps/web/components/assistant-chat/chat.tsx`, `messages.tsx`, `message-part.tsx`, `tools.tsx`, `types.ts` — full chat UI implementation
- Inbox Zero `apps/web/utils/ai/assistant/chat.ts` — system prompt + tool registration + caching + provider policies
- Inbox Zero `apps/web/utils/ai/assistant/tools/rules/*` — rule tool implementations (createRule, updateRuleConditions, etc.)
- Inbox Zero `apps/web/utils/ai/assistant/tools/settings/*` — settings tool implementations
- Inbox Zero `apps/web/utils/ai/assistant/chat-inbox-tools.ts` — sendEmail/replyEmail/forwardEmail/searchInbox/readEmail/manageInbox schemas + zod input validation
- Inbox Zero `apps/web/utils/actions/assistant-chat.ts` — server actions for the confirmation flow (reservation lease pattern, idempotency, error handling)
- Inbox Zero `apps/web/app/(app)/[emailAccountId]/assistant/settings/SettingsTab.tsx` + all `*Setting.tsx` files — full reference for Settings categorization
- Inbox Zero `ARCHITECTURE.md` — "AI Personal Assistant" section on prompt-file vs db-rules tradeoff
- Zero Mail `.planning/PROJECT.md` lines 17–32 (Current Milestone v1.1) and lines 109–114 (Active scope)
- Zero Mail `.planning/seeds/SEED-001-future-ai-email-workspace-features.md` (Track A privacy-preserving assistant features)
- Zero Mail `.planning/seeds/SEED-003-screen-aware-ai-assistant-command-center.md` (chat UI / command center seed)
- Zero Mail `CLAUDE.md` "Project / Constraints" (v1 trust posture, no auto-send, privacy carve-outs, locked tech stack)

**Secondary (MEDIUM confidence — verified via library docs):**
- Vercel AI SDK 5+ / ai-elements package — `Conversation`, `Message`, `Reasoning`, `PromptInput`, `Loader` components; Vercel Data Stream Protocol envelopes; `@ai-sdk/react` v2 `useChat` (Context7 `/vercel/ai` library id, multiple versions)
- Spring AI 2.0.0-M6 — `ChatClient.stream()` returns `Flux<ChatResponse>`; tool callbacks via `@Tool` + `ToolCallbackProvider`; SSE emission via Spring MVC `produces=MediaType.TEXT_EVENT_STREAM_VALUE` (project's locked stack per `CLAUDE.md`)

**Confidence by section:**
- CHAT (UI features): HIGH (Inbox Zero code is direct evidence)
- TOOL_CATALOG: HIGH (Inbox Zero code + Zero Mail v1.0 backend known)
- CONFIRMATION: HIGH (Inbox Zero code is unambiguous; state machine lifted directly)
- SETTINGS_PROVIDER: MEDIUM-HIGH (4 providers + per-feature picker is straightforward extension of v1.0 LLM-02..03; Zero-Mail-specific UI shape)
- SETTINGS_PERSONALIZATION: HIGH (Inbox Zero `SettingsTab.tsx` gives exact categorization; Zero Mail-specific additions like AI output language are obvious)
- SETTINGS_BEHAVIOR: HIGH (same)
- SETTINGS_SAFETY: HIGH (Zero Mail v1.0 TRG-07..08 already shipped; this is pure UI work over known tables)
- Anti-features: HIGH (each anti-feature has a documented reason in CLAUDE.md, PROJECT.md, or Inbox Zero ARCHITECTURE.md)

---

*Feature research for: AI chat email assistant + Settings page (v1.1 milestone)*
*Researched: 2026-05-17*
