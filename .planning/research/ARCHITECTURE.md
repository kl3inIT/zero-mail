# Architecture Research — Zero Mail v1.1 (Chat Email Assistant + Settings Page)

**Domain:** Integration of a streaming chat assistant + AI Settings page into a shipped Java 25 / Spring Boot 4 / Spring Modulith / Next.js 16 monorepo
**Researched:** 2026-05-17
**Confidence:** HIGH on existing v1.0 conventions (verified by direct read of `backend/core`, `backend/api`, `apps/web` source). HIGH on package layout/Modulith boundaries/ArchUnit pattern (read existing `package-info.java` and `NoGmailSendAllowedTest.java`). HIGH on Liquibase changelog naming convention (read `db.changelog-master.yaml` + `changes/0XX-*.yaml`). MEDIUM-HIGH on chat schema (mirrors Inbox Zero's `Chat`/`ChatMessage` Prisma models verbatim, ported to Postgres + JPA). MEDIUM on Spring AI 2.0.0-M6 tool-call SSE loop (verified via existing `SpringAiLlmModelClient.java` which already sets `internalToolExecutionEnabled(false)` for rule-compile, so the v1.1 chat path reuses the same primitive — see `STACK.md` for the full Spring AI verification).

> **Scope.** This is the v1.1 architecture delta only. The v1.0 architectural baseline (Modulith module list, Scoped Values tenant context, single LLM gateway, `SpringAiLlmModelClient` adapter, `RulesController` shape, `SecurityConfig.csrf().spa()` SPA-token CSRF, Liquibase YAML migrations, route groups in `apps/web/app/(protected)/(app)/`, feature folders with `api/components/hooks`) is locked. This document only describes new packages, new controllers, new tables, new ArchUnit gates, and new dependency edges — and explicitly does NOT re-research the parts that exist. The v1.0 architecture content prior to 2026-05-17 lives in this file's git history.

---

## System Overview (v1.1 additions on top of v1.0)

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              apps/web (Next.js 16, React 19)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   Route groups (existing):  app/(public)/   app/(auth)/   app/(protected)/(app)/      │
│                                                                                       │
│   NEW v1.1 routes:                                                                    │
│     app/(protected)/(app)/chat/page.tsx        ←  Chat surface                        │
│     app/(protected)/(app)/settings/page.tsx    ←  EXTEND existing (tabs)              │
│                                                                                       │
│   NEW v1.1 features:                                                                  │
│     features/chat/{api,components,hooks,query-keys.ts,messages.ts}                    │
│       └── components/ai-elements/**            ← copy-paste primitives (lint-ignored) │
│     features/assistant-settings/{api,components,hooks,query-keys.ts,messages.ts}      │
│                                                                                       │
│   useChat (@ai-sdk/react@3) → DefaultChatTransport({credentials:'include'})           │
│     → POST /api/chat (SSE, Vercel UI Message Stream Protocol)                         │
│     → POST /api/chat/{chatId}/confirm  (tool-approval reply)                          │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  HTTP same-origin behind reverse proxy
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/api (Spring MVC, Tomcat + virtual threads)              │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   Existing controllers:  rules/  triage/  gmail/  analytics/  billing/  llm/  ...     │
│                                                                                       │
│   NEW v1.1 controllers (controllers/chat/, dto/chat/):                                │
│     ChatStreamingController   POST /api/chat                  →  SseEmitter           │
│     ChatConfirmController     POST /api/chat/{chatId}/confirm →  ConfirmResponse      │
│     ChatHistoryController     GET  /api/chat                  →  list past chats      │
│                               GET  /api/chat/{chatId}/messages                        │
│                               DELETE /api/chat/{chatId}                               │
│                                                                                       │
│   NEW v1.1 controllers (controllers/assistant/, dto/assistant/):                      │
│     AssistantSettingsController  GET/PUT  /api/assistant/settings                     │
│     AssistantKnowledgeController CRUD     /api/assistant/knowledge-snippets           │
│     AssistantMemoryController    CRUD     /api/assistant/memories                     │
│                                                                                       │
│   Reuses: TenantContext, SecurityConfig csrf().spa(), existing session cookie.        │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  in-process service calls
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/core (Spring Modulith modules)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  Existing modules:                                                                    │
│   tenant  account  gmail  llm  rules  triage  draft  thread  analytics                │
│   billing  notification  onboarding  shared.*                                         │
│                                                                                       │
│  NEW v1.1 Modulith module:  com.zeromail.core.chat                                    │
│    Allowed deps: tenant, llm, rules, gmail, draft, thread, triage,                    │
│                  analytics, billing, shared.persistence, shared.lang,                 │
│                  shared.privacy                                                       │
│                                                                                       │
│  Sub-packages:                                                                        │
│   chat/                                                                               │
│    ├── package-info.java         @ApplicationModule(displayName="Chat")               │
│    ├── domain/                   ChatRole, ChatMessagePart, ChatToolName,             │
│    │                             AssistantSendActionType, ConfirmationState,          │
│    │                             ChatPartsJsonValidator, @AllowedSendCallSite         │
│    ├── usecases/                 ChatTurnService (the SSE orchestrator)               │
│    │                             ChatHistoryService                                   │
│    │                             ChatToolRegistry  (the 20-tool catalog)              │
│    │                             ChatToolExecutor  (executes tool by name)            │
│    │                             AssistantSendExecutor  (the ONLY Gmail send call)    │
│    │                             AssistantSettingsService                             │
│    │                             AssistantMemoryService                               │
│    │                             AssistantKnowledgeService                            │
│    │                             ConfirmationLeaseService (Redis-backed lease)        │
│    │                             ChatSseFrameEmitter (Vercel UI Message Stream)       │
│    │                             commands/results records                             │
│    ├── projection/               ChatHistoryEntry, ChatMessageProjection,             │
│    │                             AssistantSendAuditProjection                         │
│    ├── persistence/              ChatEntity, ChatMessageEntity,                       │
│    │                             AssistantPendingActionEntity,                        │
│    │                             AssistantSendAuditEntity,                            │
│    │                             AssistantSettingsEntity,                             │
│    │                             AssistantMemoryEntity,                               │
│    │                             AssistantKnowledgeSnippetEntity,                     │
│    │                             *Repository interfaces                               │
│    └── exception/                ChatNotFoundException, ConfirmationLeaseHeldException│
│                                  AssistantSettingsValidationException,                │
│                                  ToolUnknownException, SendNotApprovedException       │
│                                                                                       │
│  v1.1 changes to EXISTING modules:                                                    │
│   llm/   add LlmGateway.streamChat(...) → Flux<ChatResponse>                          │
│          add streaming overload to SpringAiLlmModelClient                             │
│          NO new sub-package; lives inside existing llm.usecases + llm.gateway.springai│
│   rules/ NO change — chat → rules is expressed by chat's allowedDependencies list.    │
│   gmail/ NO change — chat → gmail expressed by chat's allowedDependencies list.       │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  JDBC
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           PostgreSQL 17 (same VPS)                                    │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  NEW v1.1 tables (changelogs 041–046):                                                │
│   041-chat-conversation.yaml         chat                                             │
│   042-chat-message.yaml              chat_message  (parts JSONB)                      │
│   043-assistant-pending-action.yaml  assistant_pending_action                         │
│   044-assistant-send-audit.yaml      assistant_send_audit  (append-only)              │
│   045-assistant-settings.yaml        assistant_settings                               │
│   046-assistant-memory-and-kb.yaml   assistant_memory + assistant_knowledge_snippet   │
│                                                                                       │
│  All FK chains: chat.tenant_id → tenants(id) ON DELETE CASCADE                        │
│                 chat_message.chat_id → chat(id) ON DELETE CASCADE                     │
│                 *.tenant_id → tenants(id) ON DELETE CASCADE                           │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          ▲
                                          │  Redis 7 (same VPS)
                                          │
┌──────────────────────────────────────────────────────────────────────────────────────┐
│   Spring Session Redis (existing) + NEW: ConfirmationLease Redis key namespace        │
│     key: assistant:lease:{tenantId}:{chatId}:{toolCallId}    TTL=5min                 │
│   No queue use — chat is request-scoped streaming, no async job in v1.1.              │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities (v1.1 only)

| Component | Responsibility | Where it lives |
|-----------|----------------|----------------|
| `ChatStreamingController` | Accepts `POST /api/chat`, opens an `SseEmitter`, hands tenant context + request to `ChatTurnService`, never touches Spring AI directly. | `backend/api/.../controllers/chat/ChatStreamingController.java` |
| `ChatConfirmController` | Accepts `POST /api/chat/{chatId}/confirm`, resolves a pending action by `toolCallId`, delegates to `AssistantSendExecutor`. | `backend/api/.../controllers/chat/ChatConfirmController.java` |
| `ChatHistoryController` | List / get / delete chats for the current tenant. Read-only, no LLM. | `backend/api/.../controllers/chat/ChatHistoryController.java` |
| `ChatTurnService` | Orchestrates one chat turn: persists user message → invokes `LlmGateway.streamChat()` → walks tool-call loop → emits SSE frames via `ChatSseFrameEmitter` → persists assistant message on completion. | `backend/core/.../chat/usecases/ChatTurnService.java` |
| `ChatToolRegistry` | Static catalog of the 20 tools: name → JSON Schema input/output spec → `ChatToolHandler` reference. | `backend/core/.../chat/usecases/ChatToolRegistry.java` |
| `ChatToolExecutor` | Dispatches a parsed tool call to its handler. Handlers are package-private classes inside `chat.usecases.tools.*` (one file per tool family). | `backend/core/.../chat/usecases/ChatToolExecutor.java` |
| `AssistantSendExecutor` | **The ONLY caller of `Gmail.Users.Messages.send`.** Encapsulates the Confirmation state machine: reserve lease → execute Gmail call → write audit row → release lease → emit completion event. Carries the `@AllowedSendCallSite` annotation that ArchUnit greps for. | `backend/core/.../chat/usecases/AssistantSendExecutor.java` |
| `ConfirmationLeaseService` | Redis lease keyed on `(tenantId, chatId, toolCallId)`, 5-min TTL, `SET NX EX` semantics; prevents double-send on UI replay. | `backend/core/.../chat/usecases/ConfirmationLeaseService.java` |
| `ChatSseFrameEmitter` | Hand-written encoder for the Vercel UI Message Stream Protocol (12 event types). Writes JSON-over-SSE through an `SseEmitter`. NO Spring AI dependency. | `backend/core/.../chat/usecases/ChatSseFrameEmitter.java` |
| `AssistantSettingsService` | Reads / writes the `assistant_settings` row per tenant (writing style, signature, tone preset, AI output language, behavior toggles, per-feature model overrides). | `backend/core/.../chat/usecases/AssistantSettingsService.java` |
| `AssistantMemoryService` | CRUD on `assistant_memory` (the assistant's saved facts). | `backend/core/.../chat/usecases/AssistantMemoryService.java` |
| `AssistantKnowledgeService` | CRUD on `assistant_knowledge_snippet`. | `backend/core/.../chat/usecases/AssistantKnowledgeService.java` |
| `LlmGateway.streamChat(...)` | NEW method on the existing interface; returns `Flux<ChatResponse>` with per-request `internalToolExecutionEnabled(false)`. Confined to the existing `llm.gateway.springai` package — no Spring AI types leak. | `backend/core/.../llm/usecases/LlmGateway.java` (+ impl) |

---

## Recommended Project Structure

### Backend — new packages under `backend/core/src/main/java/com/zeromail/core/`

```
chat/
├── package-info.java                  # @ApplicationModule(displayName="Chat", allowedDependencies={...})
├── domain/                            # framework-free vocabulary
│   ├── package-info.java
│   ├── ChatRole.java                  # USER, ASSISTANT, SYSTEM (IdentifiedEnum)
│   ├── ChatMessagePart.java           # sealed: TextPart, ToolCallPart, ToolResultPart, ReasoningPart, DataPart
│   ├── ChatToolName.java              # IdentifiedEnum of 20 tool names (allow-list)
│   ├── AssistantSendActionType.java   # SEND, REPLY, FORWARD (IdentifiedEnum)
│   ├── ConfirmationState.java         # PENDING, PROCESSING, CONFIRMED, CANCELED, FAILED (IdentifiedEnum)
│   ├── ChatPartsJsonValidator.java    # validates ChatMessage.parts JSONB shape on read/write
│   └── AllowedSendCallSite.java       # marker annotation — ONLY for AssistantSendExecutor
├── usecases/
│   ├── package-info.java
│   ├── ChatTurnService.java           # public API: streamTurn(ChatTurnCommand) → SseEmitter
│   ├── ChatTurnCommand.java
│   ├── ChatTurnContext.java           # carries SseEmitter + persistence handles for one turn
│   ├── ChatHistoryService.java        # public API: list, get, delete chats
│   ├── ChatToolRegistry.java          # static catalog
│   ├── ChatToolExecutor.java
│   ├── ChatToolHandler.java           # interface: ToolCallResult execute(ChatToolCall, ChatTurnContext)
│   ├── tools/                         # one file per tool family
│   │   ├── RulesToolHandlers.java     # listRules/getRule/createRule/updateRuleConditions/updateRuleActions/deleteRule
│   │   ├── InboxToolHandlers.java     # searchInbox/readEmail/listLabels/createOrGetLabel/manageInbox
│   │   ├── MemoryToolHandlers.java    # saveMemory/searchMemories/addToKnowledgeBase
│   │   ├── SettingsToolHandlers.java  # updatePersonalInstructions/updateAssistantSettings
│   │   ├── CapabilitiesToolHandlers.java # getAssistantCapabilities/getInboxStats/getUserRulesAndSettings
│   │   └── SendToolHandlers.java      # sendEmail/replyEmail/forwardEmail → AssistantSendExecutor
│   ├── AssistantSendExecutor.java     # @AllowedSendCallSite — the only Gmail.send call site
│   ├── AssistantSendCommand.java
│   ├── ConfirmationLeaseService.java  # Redis SET NX EX 300
│   ├── ChatSseFrameEmitter.java       # Vercel UI Message Stream encoder
│   ├── AssistantSettingsService.java
│   ├── AssistantSettingsUpdateCommand.java
│   ├── AssistantMemoryService.java
│   ├── AssistantKnowledgeService.java
│   ├── ChatPersistencePort.java       # internal port over the persistence package
│   └── ChatPromptBuilder.java         # builds system prompt + history + context-pack
├── projection/
│   ├── package-info.java
│   ├── ChatHistoryEntry.java          # list view
│   ├── ChatMessageProjection.java     # read-side snapshot of a persisted turn
│   └── AssistantSendAuditProjection.java
├── persistence/
│   ├── package-info.java
│   ├── ChatEntity.java                # @Entity, AbstractTenantOwnedEntity
│   ├── ChatRepository.java
│   ├── ChatMessageEntity.java         # parts column is JSONB (@JdbcTypeCode(SqlTypes.JSON))
│   ├── ChatMessageRepository.java
│   ├── AssistantPendingActionEntity.java
│   ├── AssistantPendingActionRepository.java
│   ├── AssistantSendAuditEntity.java  # append-only
│   ├── AssistantSendAuditRepository.java
│   ├── AssistantSettingsEntity.java   # one row per tenant
│   ├── AssistantSettingsRepository.java
│   ├── AssistantMemoryEntity.java
│   ├── AssistantMemoryRepository.java
│   ├── AssistantKnowledgeSnippetEntity.java
│   └── AssistantKnowledgeSnippetRepository.java
└── exception/
    ├── package-info.java
    ├── ChatNotFoundException.java
    ├── ConfirmationLeaseHeldException.java
    ├── AssistantSettingsValidationException.java
    ├── ToolUnknownException.java
    └── SendNotApprovedException.java
```

### Backend — new files under existing modules

```
backend/core/src/main/java/com/zeromail/core/llm/
├── usecases/
│   ├── LlmGateway.java                          # ADD: Flux<ChatResponse> streamChat(ChatStreamRequest)
│   ├── LlmGatewayImpl.java                      # ADD: streamChat impl
│   └── ChatStreamRequest.java                   # NEW record
└── gateway/springai/
    └── SpringAiLlmModelClient.java              # ADD: Flux<ChatResponse> stream(...) — already uses internalToolExecutionEnabled(false)
```

### Backend — new files under `backend/api`

```
backend/api/src/main/java/com/zeromail/api/
├── controllers/
│   ├── chat/
│   │   ├── ChatStreamingController.java         # POST /api/chat → SseEmitter
│   │   ├── ChatConfirmController.java           # POST /api/chat/{chatId}/confirm
│   │   └── ChatHistoryController.java           # GET/DELETE /api/chat[...]
│   └── assistant/
│       ├── AssistantSettingsController.java     # GET/PUT /api/assistant/settings
│       ├── AssistantKnowledgeController.java    # CRUD /api/assistant/knowledge-snippets
│       └── AssistantMemoryController.java       # CRUD /api/assistant/memories
└── dto/
    ├── chat/
    │   ├── ChatTurnRequest.java                 # { chatId?, messages: UIMessage[] }
    │   ├── ChatConfirmRequest.java
    │   ├── ChatConfirmResponse.java
    │   ├── ChatHistoryItemResponse.java
    │   └── ChatMessageResponse.java
    └── assistant/
        ├── AssistantSettingsRequest.java
        ├── AssistantSettingsResponse.java
        ├── AssistantKnowledgeSnippetRequest.java
        ├── AssistantKnowledgeSnippetResponse.java
        ├── AssistantMemoryRequest.java
        └── AssistantMemoryResponse.java
```

### Backend — new test files (ArchUnit + invariants)

```
backend/core/src/test/java/com/zeromail/core/arch/
├── NoGmailSendAllowedTest.java                  # EXISTING — UPDATE: allow exactly 1 site
├── ChatModuleBoundaryTest.java                  # NEW — verifies allowedDependencies
└── AssistantSendCallSiteAllowlistTest.java      # NEW — verifies AssistantSendExecutor.@AllowedSendCallSite + grep guard
backend/core/src/test/java/com/zeromail/core/chat/
├── ChatPersistencePrivacyTest.java              # verifies no email body bytes in chat_message
├── ConfirmationLeaseServiceTest.java
├── ChatSseFrameEmitterTest.java
├── ChatToolRegistryTest.java                    # all 20 tools registered, no unknown names
├── AssistantSendExecutorAuditTest.java          # audit row written before Gmail call
└── AssistantSettingsServiceTest.java
```

### Frontend — new feature folder + route

```
apps/web/
├── app/(protected)/(app)/
│   ├── chat/
│   │   └── page.tsx                             # NEW route — empty shell that mounts <ChatApp />
│   └── settings/
│       └── page.tsx                             # MODIFY (tabs added — see decision below)
├── components/
│   └── ai-elements/                             # NEW — copy-paste primitives (pnpm dlx ai-elements@latest add ...)
│       ├── conversation.tsx
│       ├── message.tsx
│       ├── prompt-input.tsx
│       ├── response.tsx                         # uses streamdown
│       ├── tool.tsx
│       ├── reasoning.tsx
│       ├── loader.tsx
│       ├── suggestion.tsx
│       └── confirmation.tsx                     # used for sendEmail/replyEmail/forwardEmail
└── features/
    ├── chat/                                    # NEW feature folder (existing convention)
    │   ├── api/
    │   │   └── chat-api.ts                      # postChatTurn (SSE), confirmAction, listChats, deleteChat
    │   ├── components/
    │   │   ├── ChatApp.tsx                      # root client component, owns useChat
    │   │   ├── ChatHistorySidebar.tsx
    │   │   ├── ChatComposer.tsx                 # wraps PromptInput
    │   │   ├── tool-cards/                      # one card per tool
    │   │   │   ├── RuleToolCard.tsx
    │   │   │   ├── InboxSearchToolCard.tsx
    │   │   │   ├── EmailReadToolCard.tsx
    │   │   │   ├── MemoryToolCard.tsx
    │   │   │   ├── SettingsToolCard.tsx
    │   │   │   └── SendToolCard.tsx             # uses <Confirmation>
    │   │   └── EmptyState.tsx
    │   ├── hooks/
    │   │   ├── useChatHistory.ts
    │   │   ├── useChatTurn.ts                   # wraps @ai-sdk/react useChat
    │   │   └── usePersistedMessageIds.ts        # gate-confirm-on-persisted pattern
    │   ├── lib/
    │   │   └── ui-message-stream.ts             # protocol helpers + types
    │   ├── query-keys.ts                        # chat:list, chat:detail(id)
    │   └── messages.ts                          # Vietnamese + English strings (i18n)
    └── assistant-settings/                      # NEW feature folder
        ├── api/
        │   └── assistant-settings-api.ts        # generated openapi-fetch wrappers
        ├── components/
        │   ├── AssistantSettingsPage.tsx        # rendered by /settings when activeTab === 'ai'
        │   ├── ProviderModelSection.tsx         # per-feature model picker
        │   ├── PersonalizationSection.tsx       # writing style, signature, tone, language
        │   ├── BehaviorSection.tsx              # toggles + draft confidence slider
        │   ├── KnowledgeBaseSection.tsx
        │   └── SenderSafetyNetSection.tsx       # reuses triage tables (read-only schema)
        ├── hooks/
        │   ├── useAssistantSettings.ts
        │   ├── useUpdateAssistantSettings.ts
        │   ├── useKnowledgeSnippets.ts
        │   └── useAssistantMemories.ts
        ├── query-keys.ts
        └── messages.ts
```

### Structure rationale

- **`chat/` is a new top-level Modulith module, not a sub-package of `llm/`.** `llm/` is a horizontal capability (the gateway); `chat/` is a vertical domain (conversation persistence, tool registry, send executor, settings). Putting chat inside `llm/` would inflate `llm/`'s `allowedDependencies` to include rules + gmail + draft + thread + billing, destroying the gateway's narrow contract (currently 5 deps). Splitting into `chat.conversation` + `chat.tools` (option (c) in the question) is over-engineered for the call volume — the tool catalog is 20 entries, the SSE orchestrator and tool executor share state (the same `ChatTurnContext`), and Modulith does not reward sub-modules that always link together. **Decision: one `chat` module, sub-packages via the standard `domain/usecases/projection/persistence/exception` layout already used by every other v1.0 module** (verified by reading `rules/`, `triage/`, `billing/`, `llm/` package-info files).

- **`AssistantSendExecutor` lives in `chat.usecases`, not in `gmail.usecases`.** The send call site is a chat-domain concern (Confirmation state machine + audit + tool-call contract); putting it in `gmail.usecases` would either (a) require `gmail` to depend on `chat` for the audit/lease/event types, breaking the dependency direction, or (b) put the executor far away from the only thing that calls it. **The Gmail HTTP call itself** is delegated to `GmailApiClientFactory.client(tenantId).users().messages().send(...)` — `gmail.gateway` stays the only owner of the raw Gmail SDK; `chat.usecases` calls into `gmail.gateway` like every other domain that needs Gmail (`triage`, `draft` already do this).

- **Per-tool handlers are package-private classes inside `chat.usecases.tools.*`, dispatched through `ChatToolRegistry`.** Each tool maps to existing v1.0 services (`RuleManagementService`, `GmailDeliveryProcessingService`, `GenerateThreadDraftService`, etc.). The handler is the thin adapter between the LLM tool-call schema and the existing service contract. This keeps the LLM-facing surface (JSON schemas, validation, allow-list) in one place.

- **Settings page extends `/settings` with tabs, does NOT add `/settings/ai` / `/settings/personalization` sub-routes.** Per the user's locked memory rule "avoid single-purpose nested parents like `apps/web/content/docs/` or top-level `apps/web/messages/`; co-locate i18n in feature folder, hoist single-child folders to top level" — adding sub-routes for sections that all live on one screen is exactly the flat-folder violation that rule warns against. **Decision: ONE route `/settings/page.tsx`, a `<Tabs>` (shadcn primitive) with the existing General / Privacy tabs plus new AI / Personalization / Behavior / Safety Net tabs.** Privacy is already its own sub-route (`/settings/privacy`) — that is a v1.0 convention we leave alone, but we do NOT mint new sub-routes for v1.1.

- **`features/assistant-settings/` is a sibling of `features/llm/` (which exists for BYOK), not a sub-folder.** The BYOK form already lives in `features/llm/components/ByokForm.tsx` and is imported into `/settings/page.tsx`. Assistant settings is a different concern (per-feature model overrides, personalization, behavior) and belongs in its own feature. Both stay flat top-level features.

---

## Architectural Patterns

### Pattern 1: SSE controller in `backend/api` calls a non-reactive service in `backend/core`

**What:** `ChatStreamingController` opens an `SseEmitter`, hands it (wrapped in a `ChatTurnContext`) to `ChatTurnService.streamTurn(...)`. The service runs on a virtual thread (because `spring.threads.virtual.enabled=true`), walks the Spring AI tool-call loop synchronously, and pushes SSE frames through `ChatSseFrameEmitter` as it goes. When the LLM `Flux<ChatResponse>` produces deltas, they are subscribed in-loop (`.toIterable()` / `.toStream()`) — we do NOT propagate `Flux` to the controller, because that would force a `Flux<ServerSentEvent>` return type and entangle the LLM gateway's reactive type with every API surface.

**When to use:** Any new streaming endpoint where the orchestration is naturally imperative (tool-call loop, persistence between steps, lease checks). Use `Flux<ServerSentEvent>` return only when the producer is already a single `Flux` with no per-step branching.

**Trade-offs:**
- (+) Virtual threads make blocking SSE writes cheap (one stack frame per connection).
- (+) `ChatTurnService` stays a normal `@Service` with `@Transactional` boundaries — no reactive context propagation pain.
- (+) Existing `TenantContext` (ScopedValue) propagates naturally because the SSE write runs on the same virtual thread.
- (-) Manual subscription to the LLM `Flux` means we must remember to `dispose()` on client disconnect (handled in the `SseEmitter.onCompletion` / `onTimeout` callbacks).

**Example:**

```java
// backend/api/.../controllers/chat/ChatStreamingController.java
@RestController
@Tag(name = "chat")
@RequestMapping("/api/chat")
public class ChatStreamingController {

    private final ChatTurnService chatTurnService;

    public ChatStreamingController(ChatTurnService chatTurnService) {
        this.chatTurnService = chatTurnService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTurn(@Valid @RequestBody ChatTurnRequest request,
                                 HttpServletResponse response) {
        // required for non-Vercel backends so useChat parses our frames
        response.setHeader("x-vercel-ai-ui-message-stream", "v1");
        UUID tenantId = TenantContext.currentTenantUuid();
        SseEmitter emitter = new SseEmitter(0L); // no timeout — virtual thread is cheap
        emitter.onCompletion(() -> { /* metric */ });
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> { /* metric + close */ });
        chatTurnService.streamTurn(
                new ChatTurnCommand(tenantId, request.chatId(), request.messages()), emitter);
        return emitter;
    }
}
```

```java
// backend/core/.../chat/usecases/ChatTurnService.java — sketch
@Service
public class ChatTurnService {
    public void streamTurn(ChatTurnCommand command, SseEmitter sseEmitter) {
        ChatSseFrameEmitter frameEmitter = new ChatSseFrameEmitter(sseEmitter);
        // 1. persist user message; emit start frame
        // 2. while (toolCallLoop):
        //      a. for read-only tool → execute, emit tool-input-available + tool-output-available
        //      b. for send tool → emit tool-input-available with approval payload; STOP streaming
        //         (the next POST /api/chat/{id}/confirm will resume from a new SSE call)
        // 3. on plain text turn → subscribe to LlmGateway.streamChat(...), forward each
        //    ChatResponse.getResult().getOutput() as text-delta frames
        // 4. on completion: persist assistant ChatMessage with parts JSONB; emit finish; emitter.complete()
    }
}
```

### Pattern 2: Confirmation lease before destructive tool call

**What:** Every "send" tool returns `requiresConfirmation: true` and writes an `assistant_pending_action` row (`state=PENDING`). The client renders `<Confirmation>` and on click POSTs `/api/chat/{chatId}/confirm`. The controller calls `ConfirmationLeaseService.acquireOrFail(tenantId, chatId, toolCallId)` — Redis `SET NX EX 300` — then calls `AssistantSendExecutor.execute(...)`. On success, append-only audit row → release lease → return success. On Gmail-API failure: revert `pending_action` to `PENDING`, clear lease, return error.

**When to use:** Any tool that calls an external write API (Gmail send, future webhook actions). Read-only and reversible-mutation tools (label, archive, save_draft, rule CRUD) skip the lease/confirmation entirely.

**Trade-offs:**
- (+) Double-click on Send → second confirm gets `409 Conflict` instead of double-sending.
- (+) Crash between Gmail call and audit-row write is detectable (`pending_action.state=PROCESSING` + `leasedUntil < now()` is the recovery condition).
- (-) Adds Redis as a hard dep for send (already a project dep — no new dep).

### Pattern 3: Hand-written Vercel UI Message Stream Protocol encoder

**What:** `ChatSseFrameEmitter` is a small class (~250 LoC) that wraps `SseEmitter` and exposes typed methods (`start(messageId)`, `textStart(id)`, `textDelta(id, delta)`, `textEnd(id)`, `toolInputStart(toolCallId, toolName)`, `toolInputAvailable(toolCallId, toolName, inputJson)`, `toolOutputAvailable(toolCallId, output)`, `toolOutputError(toolCallId, errorText)`, `dataPart(name, dataJson)`, `finish()`, `done()`). Each method serializes to JSON (Jackson 3.x, already a Boot 4 dep) and emits via `emitter.send(SseEmitter.event().data(json))`.

**When to use:** This is the ONLY place that knows the Vercel wire format. Controllers must not emit raw SSE frames. Tool handlers must not call `emitter.send` directly — they return typed `ChatToolResult` records and the orchestrator turns those into frames.

**Trade-offs:**
- (+) Single place to evolve the protocol when Vercel ships v2.
- (+) Required `x-vercel-ai-ui-message-stream: v1` response header is set in one place (the controller sets it before delegating).
- (-) ~250 LoC of hand-written serialization; verified-and-tested against `@ai-sdk/react@3` (see `STACK.md`).

### Pattern 4: ArchUnit single-call-site allowlist via marker annotation

**What:** v1.0's `NoGmailSendAllowedTest` is a pure `noClasses()...should()` rule with `allowEmptyShould(true)` (production code has zero `Gmail.send` calls). v1.1 must allow **exactly one** call site. Express this in two complementary tests (both must pass; never replace the v1.0 zero-call-site test silently):

1. **`AssistantSendCallSiteAllowlistTest`** — ArchUnit positive test: every method that calls `Gmail.Users.Messages.send` or `Gmail.Users.Drafts.send` MUST be inside a class annotated `@AllowedSendCallSite`. Marker annotation lives in `chat.domain.AllowedSendCallSite` with retention=CLASS (visible to ArchUnit, invisible to runtime).
2. **`NoGmailSendAllowedTest` (UPDATED)** — same ArchCondition as v1.0, but `noClasses().that().areNotAnnotatedWith(AllowedSendCallSite.class).should(...)`. The original `because("TRG-03: Zero Mail v1 may label, archive, or save drafts, but must never send mail.")` rationale is updated to `"TRG-03 + v1.1 CHAT-D3: Zero Mail v1.1 may send mail only from the chat-confirmed AssistantSendExecutor call site, annotated with @AllowedSendCallSite."`.

This pairs with a repo-wide grep gate in CI: `! grep -rn "messages().send\|drafts().send" backend/core/src/main backend/api/src/main backend/worker/src/main | grep -v AssistantSendExecutor.java`.

**When to use:** Any architectural invariant that says "exactly N call sites of X." The annotation pattern is more refactor-safe than path-based allowlists (the test follows the class if you move it).

**Trade-offs:**
- (+) Refactor-friendly (renames do not break the test).
- (+) The annotation itself is the documentation — anyone reading `AssistantSendExecutor.java` sees `@AllowedSendCallSite` and knows this is the carve-out.
- (-) Slightly more boilerplate than a path-based test (define marker annotation + two tests).

### Pattern 5: Per-domain controllers grouped under `controllers/<domain>/`, DTOs under `dto/<domain>/`

**What:** Existing v1.0 convention (verified by directory listing). `controllers/rules/RulesController.java`, `controllers/triage/TriageAuditController.java`, etc. v1.1 follows this: all chat HTTP surfaces go under `controllers/chat/`, all assistant settings under `controllers/assistant/`. DTOs mirror: `dto/chat/`, `dto/assistant/`. NO new top-level package in `backend/api`.

**When to use:** Every new HTTP surface.

**Trade-offs:** None — this is the existing convention; deviating would force a CLAUDE.md amendment.

---

## Data Flow

### Chat turn (text-only, no tool call)

```
[User types into PromptInput]
    ↓
[useChat({transport: DefaultChatTransport({credentials:'include'})})]
    ↓ POST /api/chat  body: { chatId?, messages: UIMessage[] }
[ChatStreamingController.streamTurn]                                ← virtual thread T1
    ↓ TenantContext bound by TenantBindingFilter
[ChatTurnService.streamTurn(command, sseEmitter)]
    ↓ persists user ChatMessage (parts JSONB)
    ↓ emit "start" frame
[LlmGateway.streamChat(systemPrompt, history, tools=[all 20], opts={internalToolExecutionEnabled=false, model=settings.chatModel, byok?})]
    ↓ delegates to SpringAiLlmModelClient.stream(...)              ← uses platformChatClient.prompt().stream()
    ↓ returns Flux<ChatResponse>
[ChatTurnService iterates Flux<ChatResponse> in-loop (toIterable())]
    ↓ for each text delta:
[ChatSseFrameEmitter.textDelta(id, delta)]                          → SSE frame to client
    ↓ on stream completion:
[Persist assistant ChatMessage (parts JSONB with full assembled text + token usage)]
    ↓ emit "finish" + "[DONE]"
[Client useChat appends assistant message; re-render]
```

### Chat turn with tool call (read-only tool, no confirmation)

```
[Same setup as above through LlmGateway.streamChat]
    ↓ Spring AI emits ChatResponse with toolCalls()
[ChatTurnService:
   for each toolCall:
       emit "tool-input-start" + "tool-input-available"
       result = ChatToolExecutor.dispatch(toolCall, context)
       emit "tool-output-available" (or "tool-output-error")
   if there are more tool calls or assistant wants to continue:
       build next Prompt with tool result as ToolResponseMessage, call LlmGateway.streamChat again
       continue loop]
    ↓ final text emitted via Pattern 1 above
[Client renders <Tool> card for each tool call; final message in <MessageResponse>]
```

### Chat turn with tool call (send tool, requires confirmation)

```
[As above, but ChatToolExecutor for sendEmail/replyEmail/forwardEmail:]
    ↓ does NOT execute the Gmail call
    ↓ writes assistant_pending_action row (state=PENDING)
    ↓ ChatSseFrameEmitter.toolInputAvailable(toolCallId, toolName, inputJson)
    ↓ ChatSseFrameEmitter.dataPart("data-approval-requested", { toolCallId, pendingActionId })
    ↓ ChatSseFrameEmitter.finish() + done()  ← stream closes; no tool-output yet
    ↓ persist assistant message; user-side <Confirmation> renders
[User clicks Send]
    ↓ frontend useChat triggers addToolApprovalResponse({approved: true})
    ↓ POST /api/chat/{chatId}/confirm  body: { toolCallId, approved: true, contentOverride? }
[ChatConfirmController.confirm]
    ↓ TenantContext bound
[ConfirmationLeaseService.acquireOrFail(tenantId, chatId, toolCallId)]      ← Redis SET NX EX 300
    ↓ (throws ConfirmationLeaseHeldException → HTTP 409 if already in flight)
[AssistantSendExecutor.execute(pendingActionId, contentOverride?)]           ← @AllowedSendCallSite class
    ↓ marks pending_action state=PROCESSING
    ↓ calls GmailApiClientFactory.client(tenantId).users().messages().send(...)
    ↓ on success: writes assistant_send_audit row (append-only)
    ↓ marks pending_action state=CONFIRMED with confirmationResult
    ↓ releases Redis lease
    ↓ emits Spring Modulith event "AssistantSendCompleted" (for analytics module)
    ↓ on failure: marks state=PENDING, releases lease, rethrows
[ChatConfirmController returns ChatConfirmResponse { state: CONFIRMED, gmailMessageId, threadId, sentAt }]
[Client updates <Confirmation> to "Sent" state]
```

### Settings update (no LLM)

```
[User toggles "Auto-draft replies" switch on AI tab]
    ↓ TanStack Query useUpdateAssistantSettings
    ↓ PUT /api/assistant/settings  body: { autoDraftEnabled: true }
[AssistantSettingsController.update]
    ↓ TenantContext bound
[AssistantSettingsService.update(tenantId, command)]
    ↓ Loads or creates assistant_settings row (one per tenant)
    ↓ Validates field-by-field (whitelisted keys, value-range checks)
    ↓ Saves and returns projection
[Client invalidates assistant-settings query key; re-render]
```

### Key data flows summary

1. **Chat turn → SSE stream → message persistence.** User message in, tokens out, persisted at turn end. Tool calls interleave inline.
2. **Send confirmation → lease → audit.** Two HTTP round trips: streaming turn + confirm POST. Lease prevents double-fire. Audit row written before lease release.
3. **Settings update → single row write.** No streaming, no LLM, no Modulith event.
4. **Chat history list → simple projection.** Read-only paginated list keyed by `(tenant_id, updated_at DESC)`.

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0–50 tenants (v1.0 load-test green) | No changes. SSE on virtual threads is cheap; ~50 concurrent long-lived chat connections cost ~50 stack frames on the JVM, not 50 platform threads. PostgreSQL handles `chat_message` writes at 50 tenant × 10 messages/min easily. |
| 50–500 tenants | Index `(chat_id, created_at)` on `chat_message` for history list; the `chat` table index on `(tenant_id, updated_at DESC)` is enough up to ~10k chats per tenant. `assistant_pending_action` should add a partial index `WHERE state IN ('PENDING','PROCESSING')` to keep the lease-recovery sweep fast. Daily-digest analytics may want to join `assistant_send_audit` for the "sent N emails today" metric — current `analytics` module already has a similar aggregator and can pick this up by reading the audit table. |
| 500–5000 tenants | Move the `parts JSONB` storage scan path to a derived `chat_message_summary` projection if any UI shows "chats with code blocks" filtering. Likely not needed in v1.1. Consider compaction (the `ChatCompaction` model Inbox Zero has) only if a single chat exceeds ~50 turns — defer until observed. |
| 5000+ tenants | Partition `assistant_send_audit` by month if it exceeds ~10M rows (unlikely with user-confirmed-only send). Move chat compaction to a worker job (would be `backend/worker`'s first new responsibility post-v1.1). |

### Scaling priorities

1. **First bottleneck (expected):** PostgreSQL `chat_message` write throughput. Hot path is one INSERT per turn (~2 INSERTs counting user+assistant). With JSONB serialization that is ~5–20 ms per row depending on `parts` size. Mitigation: batch user + assistant message writes in one transaction at end of turn.
2. **Second bottleneck:** LLM provider rate limits. Spring AI's existing `LlmGateway` already routes through provider-specific clients with per-tenant credit reservation — no new bottleneck introduced by chat (chat just generates more traffic through the same gateway).
3. **Third bottleneck:** SSE connection count. Tomcat with virtual threads can hold thousands of long-lived connections; the real bound is OS file descriptors. Default Linux `nofile` cap (4096) should be raised to 65535 in deploy config before v1.1 ships if expected concurrent chat sessions > ~2000.

---

## Anti-Patterns

### Anti-Pattern 1: Putting the chat module inside `core.llm`

**What people do:** "Chat uses the LLM, so put it under `llm.chat.*`." Inflate `llm/` `allowedDependencies` to include rules + gmail + draft + thread + billing + chat persistence.

**Why it is wrong:** `llm/` is the gateway. Its narrow contract ("anyone in the codebase can call `LlmGateway.chat(...)`" + "only `llm.gateway.springai` may import `org.springframework.ai.*`") is what makes the M6 → GA migration small. Folding chat in destroys that — every chat-domain service ends up able to bypass the gateway and reach Spring AI directly.

**Do this instead:** New top-level Modulith module `core.chat` with its own `allowedDependencies` list. `chat → llm` is one of many domain → llm edges; the Spring AI confinement stays inside `llm.gateway.springai`.

### Anti-Pattern 2: Persisting raw email bodies inside `chat_message.parts`

**What people do:** Tool `readEmail` returns the full email body to the LLM; the SSE frame for `tool-output-available` carries the body; when the assistant turn is persisted, that frame's payload ends up in `chat_message.parts`. Now the privacy invariant is broken — there is now long-term storage of raw email content keyed by tenant.

**Why it is wrong:** Direct violation of the locked privacy carve-out in `CLAUDE.md` and `PROJECT.md`: "User-typed rule-builder assistant chat (chat messages + structured tool outputs) persists normally — it is UI configuration input, not extracted email content. Still forbidden inside chat: inlining email bodies into long-term assistant prompts (use short-lived in-memory cache) and embeddings of user mail."

**Do this instead:** `readEmail` tool returns metadata + short snippet (≤120 chars) for persistence; the full body lives in short-lived in-memory cache (TTL = single turn) keyed by `messageId`. The LLM sees the full body only inside the in-flight turn; the persisted `tool-output-available` part carries only the snippet. Enforce with `ChatPersistencePrivacyTest` (ArchUnit + JSON shape assertion that no `parts` entry's stringified payload exceeds 240 chars for any tool whose name starts with `readEmail` / `getEmail`).

### Anti-Pattern 3: Returning `Flux<ServerSentEvent>` from the chat controller

**What people do:** "Spring MVC can adapt `Flux<ServerSentEvent>` to SSE automatically — let's return the LLM `Flux<ChatResponse>` mapped through a transformation." This puts the LLM gateway's reactive type onto the HTTP surface.

**Why it is wrong:** The chat turn is not a pure transformation of a single `Flux`. It is a tool-call loop that interleaves: (a) LLM streaming, (b) tool execution (sync, blocking), (c) per-step persistence (sync, transactional), (d) lease checks (sync, Redis). Forcing all of that into a reactive operator chain makes `TenantContext` propagation harder (ScopedValue + Reactor context plumbing), makes `@Transactional` boundaries fuzzy, and entangles the LLM gateway's return type with every API surface.

**Do this instead:** Imperative `SseEmitter` return type. The orchestrator runs on a virtual thread, subscribes to the LLM `Flux` synchronously (`.toIterable()`), and writes frames inline. This is the documented Spring Boot 4 pattern when virtual threads are enabled.

### Anti-Pattern 4: Adding new `/settings/ai` + `/settings/personalization` sub-routes

**What people do:** Mint a sub-route per section because "URLs should match content."

**Why it is wrong:** Adds 4 new files (`page.tsx` × 4 + layout if shared), forces deep links into UI state the user does not navigate to via URL, multiplies the SSR cost per section, and conflicts with the user's locked memory rule against single-purpose nested parents. The existing `/settings/privacy` carve-out is justified because it is a separate trust statement; the v1.1 sections are all "AI behavior config."

**Do this instead:** Keep one `/settings/page.tsx`, mount a `<Tabs>` (shadcn primitive — already installed) with tab values `general | ai | personalization | behavior | safety-net | knowledge | privacy`. Privacy stays as its own sub-route for v1.0 back-compat (links from privacy emails point there); the other tabs are query-param-driven (`/settings?tab=ai`) for shareability without route proliferation.

### Anti-Pattern 5: Spring Modulith event for the SSE turn

**What people do:** "Chat turn produces an assistant message; that's an event. Let us emit `AssistantTurnCompleted` and let analytics react." Inside the SSE controller / orchestrator.

**Why it is wrong:** Spring Modulith events are best for after-commit, in-process cross-module reactions. The SSE turn is request-scoped, the analytics consumer needs only the audit row (which is written by `AssistantSendExecutor`, not by the turn orchestrator), and adding an event per turn just multiplies in-process work without value. The audit row IS the durable record.

**Do this instead:** Emit one Spring Modulith event only for the durable, cross-module-interesting moment: `AssistantSendCompleted` (after audit row write, inside `AssistantSendExecutor`). Analytics subscribes via `@TransactionalEventListener(AFTER_COMMIT)`. The SSE turn itself emits no events.

---

## Integration Points

### Cross-domain dependency map (chat module → existing modules)

| Caller (in `chat.usecases`) | Callee (existing v1.0 service) | Purpose | Module dep edge to add |
|------------------------------|--------------------------------|---------|------------------------|
| `RulesToolHandlers.listRules` | `RuleManagementService.listOrdered(tenantId)` | `listRules` tool | `chat → rules` |
| `RulesToolHandlers.getRule` | `RuleManagementService.get(tenantId, ruleId)` | `getRule` tool | `chat → rules` |
| `RulesToolHandlers.createRule` | `RuleCompilerService.compile(...)` + `RuleManagementService.create(...)` | `createRule` tool | `chat → rules` |
| `RulesToolHandlers.updateRuleConditions/Actions` | `RuleManagementService.update(...)` | rule edit tools | `chat → rules` |
| `RulesToolHandlers.deleteRule` | `RuleManagementService.delete(tenantId, ruleId)` | `deleteRule` tool | `chat → rules` |
| `RulesToolHandlers.previewRuleOnRecentInbox` | `RulePreviewService.previewDraft(...)` | `previewRuleOnRecentInbox` (defer-OK) | `chat → rules` |
| `InboxToolHandlers.searchInbox` | `GmailApiClientFactory.client(tenantId)` (new use) | `searchInbox` tool | `chat → gmail` |
| `InboxToolHandlers.readEmail` | `GmailApiClientFactory.client(tenantId)` + `SanitizationPipeline` (existing in `llm.gateway.sanitization`) | `readEmail` tool | `chat → gmail` + `chat → llm` (sanitization) |
| `InboxToolHandlers.listLabels/createOrGetLabel/manageInbox` | `GmailApiClientFactory.client(tenantId)` | label + bulk-archive tools | `chat → gmail` |
| `InboxToolHandlers.pauseAllRules/resumeAllRules` | `TriagePauseService` (existing) | `pauseAllRules` tool (defer-OK) | `chat → triage` |
| `CapabilitiesToolHandlers.getInboxStats` | `AnalyticsSummaryQueryService` (existing) | `getInboxStats` tool | `chat → analytics` |
| `CapabilitiesToolHandlers.getRuleExecutionForMessage` | `TriageAuditService` (existing) | `getRuleExecutionForMessage` tool | `chat → triage` |
| `MemoryToolHandlers.saveMemory/searchMemories` | `AssistantMemoryService` (new, in same module) | memory tools | none (intra-module) |
| `MemoryToolHandlers.addToKnowledgeBase` | `AssistantKnowledgeService` (new, in same module) | KB tool | none |
| `SettingsToolHandlers.updatePersonalInstructions/updateAssistantSettings` | `AssistantSettingsService` (new, in same module) | settings tools | none |
| `SendToolHandlers.sendEmail/replyEmail/forwardEmail` | `AssistantSendExecutor` (new, in same module) — internally uses `GmailApiClientFactory` + `GenerateThreadDraftService` for header stamping on reply | `chat → gmail` (already added) + `chat → draft` |
| `ChatTurnService` | `LlmGateway.streamChat(...)` (NEW method, existing interface) | every chat turn | `chat → llm` |
| `ChatTurnService` | `CreditLedger.reserve/settle/release` (existing) | per-turn credit accounting | `chat → billing` |
| `ChatPromptBuilder` | `AssistantSettingsService` + `AssistantMemoryService` + freshest `RuleManagementService.listOrdered` | builds system prompt + context-pack | `chat → rules`, `chat → llm` (sanitization) |

**Decision: cross-module access uses direct service calls, NOT Modulith `@NamedInterface` re-exports.**

Rationale: Modulith `@NamedInterface` is for cases where (a) a module exposes a *subset* of its public API to a specific other module, or (b) the implementation lives in an internal sub-package but should be callable from outside. The chat module's needs do not match either case — `RuleManagementService` is already public in `rules.usecases` (used by `triage`, by `RulesController` in `backend/api`, and by `onboarding`); the chat module needs the same public API, not a curated subset. **Add `chat` to the dependency lists already implied by being a normal service consumer** by listing the callee modules in `chat`'s own `allowedDependencies={...}` — which is exactly how `triage` works today.

`chat`'s `package-info.java` `allowedDependencies` list (final):

```java
@ApplicationModule(
        displayName = "Chat",
        allowedDependencies = {
            "tenant",
            "llm",
            "rules",
            "gmail",
            "draft",
            "thread",
            "triage",
            "analytics",
            "billing",
            "shared.persistence",
            "shared.lang",
            "shared.privacy"
        })
package com.zeromail.core.chat;
```

This is wider than any existing module, BUT that is the correct shape for a chat orchestrator — it is the cross-cutting concern that wires the user's natural-language intent to every other capability. The alternative (a chat-tools application service in some neutral package) just relocates the same dependencies and obscures intent.

### v1.1 changes to EXISTING module `package-info.java` files

| Module | Change | Why |
|--------|--------|-----|
| `llm` | NO change to `allowedDependencies`. Add `streamChat(...)` method to `LlmGateway`, impl in `LlmGatewayImpl`, streaming impl inside existing `SpringAiLlmModelClient`. | Adding chat does NOT broaden llm's dependency surface. |
| `rules` | NO change. `chat → rules` adds an incoming edge to `rules`, which Modulith expresses by `chat`'s `allowedDependencies` listing `"rules"` — `rules`'s own `allowedDependencies` only lists what `rules` calls *out* to. | Spring Modulith dependency rules are directional: a module declares its *outgoing* deps, not its *incoming* callers. |
| `gmail`, `draft`, `triage`, `analytics`, `billing` | NO change. Same reasoning. | Same as above. |

### External service integrations (no new external services in v1.1)

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Spring AI 2.0.0-M6 LLM providers (OpenAI / Anthropic / Google GenAI / DeepSeek / OpenRouter) | Existing — via `LlmGateway.streamChat` adds `Flux<ChatResponse>` path through existing adapters | All vendor SDK imports stay inside `llm.gateway.springai` package. `internalToolExecutionEnabled(false)` already used in v1.0 `SpringAiLlmModelClient.chatOptions(...)` (verified at line 66) — reuse the same pattern for chat. |
| Gmail API | Existing — `GmailApiClientFactory.client(tenantId)` | New caller is `AssistantSendExecutor` (the only one allowed to call `.send()`). Existing OAuth refresh and `RefreshTokenCipher` story unchanged. |
| PostgreSQL 17 | Existing — Liquibase YAML changelogs + Spring Data JPA + Hibernate 7 with `@JdbcTypeCode(SqlTypes.JSON)` for JSONB | Six new changelogs (041–046). Aligned with `chat_message.parts` JSONB pattern from rules engine's `matcher_ast` JSONB column (verified in `021-rules-engine-schema.yaml`). |
| Redis 7 | Existing (Spring Session + cache) — add `assistant:lease:*` key namespace | TTL=300s via `SET NX EX`. No new client library; reuse `RedisTemplate` from Spring Data Redis. |
| Resend (daily digest) | Unchanged | Chat does NOT email; no Resend touch in v1.1. |
| Spring Modulith event spine | Existing — JDBC publication table | New event: `AssistantSendCompleted`. Subscriber: `analytics` module's existing `@TransactionalEventListener(AFTER_COMMIT)` infrastructure. |
| Spring Session Redis | Existing — cookie-based session | `useChat` sends the cookie via `DefaultChatTransport({credentials: 'include'})`. NO new auth path. |

### Internal boundaries (cross-module / cross-process)

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `backend/api` controllers ↔ `backend/core` services | Direct in-process service injection (existing pattern) | Same as `RulesController → RuleCompilerService`. NO Modulith events between api and core; api just calls into core. |
| `chat.usecases` ↔ `rules.usecases` | Direct call: `chat → rules` via dependency-allow-list edge | Chat tool handlers inject `RuleManagementService`, `RuleCompilerService`, `RulePreviewService` directly. |
| `chat.usecases` ↔ `gmail.gateway` | Direct call via `GmailApiClientFactory` | Existing gateway is already public; new consumer does not need a NamedInterface. |
| `chat.usecases.AssistantSendExecutor` → `analytics` | Spring Modulith event `AssistantSendCompleted` published after audit row commit | Analytics listens via `@TransactionalEventListener(AFTER_COMMIT)`. Decoupled because analytics does not block the user; it counts. |
| `backend/api` ↔ `backend/worker` | None for v1.1 — chat is request-scoped; no worker involvement | If background compaction is added later it goes through the existing `outbox` / `processing_job` table pattern. |
| Frontend `useChat` ↔ Backend `/api/chat` | SSE over HTTPS, same-origin (no CORS), session cookie auth | Vercel UI Message Stream Protocol v1; required header `x-vercel-ai-ui-message-stream: v1` set by `ChatStreamingController` before delegating. |
| Frontend `<Confirmation>` ↔ Backend `/api/chat/{chatId}/confirm` | Plain JSON POST, session cookie, CSRF-protected via `csrf().spa()` SPA-token (existing pattern in `SecurityConfig` line 53) | The `addToolApprovalResponse` from `@ai-sdk/react` is routed through a `chat-api.ts` wrapper that adds the SPA CSRF token — same as every other write in `apps/web`. |

---

## Schema Sketches

### `chat` table — changelog `041-chat-conversation.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 041-chat-conversation
      author: zeromail
      changes:
        - createTable:
            tableName: chat
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_chat_tenant, references: tenants(id), deleteCascade: true }
              - column: { name: title, type: varchar(200) }                       # LLM-generated short summary; nullable
              - column: { name: last_seen_rules_revision, type: int }             # for stale-rule detection (CHAT-D6); nullable
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - createIndex:
            indexName: idx_chat_tenant_updated
            tableName: chat
            columns:
              - column: { name: tenant_id }
              - column: { name: updated_at, descending: true }
```

### `chat_message` table — changelog `042-chat-message.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 042-chat-message
      author: zeromail
      changes:
        - createTable:
            tableName: chat_message
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: chat_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_chat_message_chat, references: chat(id), deleteCascade: true }
              - column:
                  name: tenant_id                                                  # denormalized for tenant-scoped scans
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_chat_message_tenant, references: tenants(id), deleteCascade: true }
              - column: { name: role, type: varchar(16), constraints: { nullable: false } }   # USER, ASSISTANT, SYSTEM (rarely persisted)
              - column: { name: parts, type: jsonb, constraints: { nullable: false } }       # UIMessage.parts[] verbatim — see validator
              - column: { name: token_usage, type: jsonb }                                   # { promptTokens, completionTokens, totalTokens, model } — assistant only
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - createIndex:
            indexName: idx_chat_message_chat_created
            tableName: chat_message
            columns:
              - column: { name: chat_id }
              - column: { name: created_at }
```

**`parts` shape — `ChatPartsJsonValidator.java` enforces on read/write:**

```jsonc
[
  { "type": "text", "text": "..." },
  { "type": "tool-call", "toolCallId": "tc_1", "toolName": "createRule", "input": { ... } },
  { "type": "tool-result", "toolCallId": "tc_1", "output": { ... }, "state": "output-available" },
  { "type": "reasoning", "text": "..." }                                            // optional
]
```

**Privacy assertion (enforced by `ChatPersistencePrivacyTest`):** for any `tool-result` part whose `toolName` is in `{readEmail, getEmail, searchInbox.fullBody, ...}`, the persisted `output` field must be a shape-validated metadata-only summary (no `body`/`bodyText`/`bodyHtml`/`mimeContent` key beyond a 120-char snippet field).

### `assistant_pending_action` — changelog `043-assistant-pending-action.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 043-assistant-pending-action
      author: zeromail
      changes:
        - createTable:
            tableName: assistant_pending_action
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_pending_action_tenant, references: tenants(id), deleteCascade: true }
              - column:
                  name: chat_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_pending_action_chat, references: chat(id), deleteCascade: true }
              - column: { name: tool_call_id, type: varchar(64), constraints: { nullable: false } }
              - column: { name: action_type, type: varchar(16), constraints: { nullable: false } }    # SEND, REPLY, FORWARD
              - column: { name: state, type: varchar(16), constraints: { nullable: false } }          # PENDING, PROCESSING, CONFIRMED, CANCELED, FAILED
              - column: { name: leased_until, type: timestamptz }                                     # nullable; populated when state=PROCESSING
              - column: { name: payload, type: jsonb, constraints: { nullable: false } }              # { to, subject, body, cc?, bcc?, threadRef? } — sanitized
              - column: { name: confirmation_result, type: jsonb }                                    # nullable; { gmailMessageId, threadId, sentAt }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - addUniqueConstraint:
            tableName: assistant_pending_action
            columnNames: chat_id, tool_call_id
            constraintName: uq_assistant_pending_action_chat_tool
        - createIndex:                                                                          # for orphan-recovery sweep
            indexName: idx_assistant_pending_action_pending_state
            tableName: assistant_pending_action
            columns:
              - column: { name: state }
              - column: { name: leased_until }
            where: state IN ('PENDING','PROCESSING')
```

### `assistant_send_audit` — changelog `044-assistant-send-audit.yaml`

**Append-only.** No UPDATE, no DELETE within the 30-day window. Per question §5 — PII consideration: recipients ARE stored (not hashed) because the user already authored them and consented to send; `subject` is stored as-is; **body is NOT stored** (we have the Gmail message ID; the user can look it up in Gmail). All chat/user metadata is FK'd so account-deletion cascade nukes it.

```yaml
databaseChangeLog:
  - changeSet:
      id: 044-assistant-send-audit
      author: zeromail
      changes:
        - createTable:
            tableName: assistant_send_audit
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_send_audit_tenant, references: tenants(id), deleteCascade: true }
              - column:
                  name: chat_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_send_audit_chat, references: chat(id), deleteCascade: true }
              - column:
                  name: chat_message_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_send_audit_message, references: chat_message(id), deleteCascade: true }
              - column: { name: tool_call_id, type: varchar(64), constraints: { nullable: false } }
              - column: { name: action_type, type: varchar(16), constraints: { nullable: false } }    # SEND, REPLY, FORWARD
              - column: { name: gmail_message_id, type: varchar(64), constraints: { nullable: false } }
              - column: { name: gmail_thread_id, type: varchar(64), constraints: { nullable: false } }
              - column: { name: recipient_count, type: int, constraints: { nullable: false } }        # length(to) + length(cc) + length(bcc)
              - column: { name: recipients, type: text, constraints: { nullable: false } }            # comma-joined plain addresses; @Sensitive in entity to suppress log scrub
              - column: { name: subject, type: varchar(998) }                                         # RFC-2822 max
              - column: { name: user_confirmed_at, type: timestamptz, constraints: { nullable: false } }   # when the confirm POST hit the server
              - column: { name: sent_at, type: timestamptz, constraints: { nullable: false } }       # when Gmail send returned
              - column: { name: draft_id_before, type: varchar(64) }                                 # nullable; if reply was a saved draft
              - column: { name: model, type: varchar(64), constraints: { nullable: false } }         # the chat model that proposed
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - createIndex:
            indexName: idx_assistant_send_audit_tenant_sent
            tableName: assistant_send_audit
            columns:
              - column: { name: tenant_id }
              - column: { name: sent_at, descending: true }
        - createIndex:
            indexName: idx_assistant_send_audit_chat
            tableName: assistant_send_audit
            columns: [ { column: { name: chat_id } } ]
```

**Why not hash recipients?** Per `CLAUDE.md` privacy carve-out, chat persistence is allowed. The `to` address was typed by the user (or confirmed by them). Storing the plaintext lets us answer the user's most-asked support question: "did the assistant send to the right person?" The risk is mitigated by `@Sensitive` annotation on the field so logs are scrubbed (existing Logback scrub filter handles it).

### `assistant_settings` — changelog `045-assistant-settings.yaml`

**One row per tenant.** Mix of personalization (textareas) + behavior (booleans/numerics) + per-feature model overrides. Knowledge snippets are a separate table because they are 1-to-N.

```yaml
databaseChangeLog:
  - changeSet:
      id: 045-assistant-settings
      author: zeromail
      changes:
        - createTable:
            tableName: assistant_settings
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, unique: true, uniqueConstraintName: uq_assistant_settings_tenant, foreignKeyName: fk_assistant_settings_tenant, references: tenants(id), deleteCascade: true }
              # Personalization
              - column: { name: writing_style, type: text }
              - column: { name: personal_instructions, type: text }
              - column: { name: email_signature, type: text }
              - column: { name: tone_preset, type: varchar(32) }                                       # FRIENDLY, FORMAL, CONCISE, ...
              - column: { name: ai_output_language, type: varchar(8) }                                 # vi, en
              # Behavior
              - column: { name: auto_draft_enabled, type: boolean, defaultValueBoolean: false }
              - column: { name: draft_confidence_threshold, type: numeric(3,2), defaultValueNumeric: 0.70 }
              - column: { name: follow_up_reminders_enabled, type: boolean, defaultValueBoolean: false }
              - column: { name: sensitive_data_protection_enabled, type: boolean, defaultValueBoolean: true }
              - column: { name: daily_digest_enabled, type: boolean, defaultValueBoolean: true }
              # Per-feature model overrides (nullable → falls back to platform default)
              - column: { name: chat_model, type: varchar(128) }
              - column: { name: triage_model, type: varchar(128) }
              - column: { name: draft_model, type: varchar(128) }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
```

### `assistant_memory` + `assistant_knowledge_snippet` — changelog `046-assistant-memory-and-kb.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 046-assistant-memory-and-kb-v1
      author: zeromail
      changes:
        - createTable:
            tableName: assistant_memory
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_memory_tenant, references: tenants(id), deleteCascade: true }
              - column: { name: chat_id, type: uuid, constraints: { foreignKeyName: fk_assistant_memory_chat, references: chat(id), deleteOnNull: SET_NULL } }
              - column: { name: content, type: text, constraints: { nullable: false } }
              - column: { name: content_hash, type: varchar(64), constraints: { nullable: false } }   # for dedup within tenant
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - addUniqueConstraint:
            tableName: assistant_memory
            columnNames: tenant_id, content_hash
            constraintName: uq_assistant_memory_tenant_hash
        - createIndex:
            indexName: idx_assistant_memory_tenant
            tableName: assistant_memory
            columns: [ { column: { name: tenant_id } } ]
        - createTable:
            tableName: assistant_knowledge_snippet
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints: { nullable: false, foreignKeyName: fk_assistant_kb_tenant, references: tenants(id), deleteCascade: true }
              - column: { name: title, type: varchar(200), constraints: { nullable: false } }
              - column: { name: body, type: text, constraints: { nullable: false } }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - createIndex:
            indexName: idx_assistant_kb_tenant
            tableName: assistant_knowledge_snippet
            columns: [ { column: { name: tenant_id } } ]
```

### `db.changelog-master.yaml` patch

Append six new `<include>` entries (relative to existing file's pattern — the last v1.0 entry is `040-triage-audit-message-ref.yaml`):

```yaml
  - include: { file: changes/041-chat-conversation.yaml, relativeToChangelogFile: true }
  - include: { file: changes/042-chat-message.yaml, relativeToChangelogFile: true }
  - include: { file: changes/043-assistant-pending-action.yaml, relativeToChangelogFile: true }
  - include: { file: changes/044-assistant-send-audit.yaml, relativeToChangelogFile: true }
  - include: { file: changes/045-assistant-settings.yaml, relativeToChangelogFile: true }
  - include: { file: changes/046-assistant-memory-and-kb.yaml, relativeToChangelogFile: true }
```

---

## ArchUnit Gate Strategy (v1.1 send call-site)

### The v1.0 invariant we must NOT break

`NoGmailSendAllowedTest` currently has `allowEmptyShould(true)` — production code is required to have ZERO calls to `Gmail.Users.Messages.send` or `Gmail.Users.Drafts.send`. This test is the load-bearing safety gate behind the entire "no auto-send" trust posture (TRG-03). It is referenced in CASA documentation and in the launch GO / NOGO signoff.

### The v1.1 carve-out we must add

Exactly one new call site: `AssistantSendExecutor.send(...)`, `AssistantSendExecutor.reply(...)`, `AssistantSendExecutor.forward(...)`. These three methods are the implementation of the three chat tools `sendEmail` / `replyEmail` / `forwardEmail`. They are gated by `ConfirmationLeaseService` and only reachable from `/api/chat/{chatId}/confirm` after explicit user click.

### Concrete strategy (3 tests, NOT just the existing one updated)

**Test 1 — `chat.domain.AllowedSendCallSite` marker annotation** (file: `backend/core/src/main/java/com/zeromail/core/chat/domain/AllowedSendCallSite.java`):

```java
package com.zeromail.core.chat.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker for the single class permitted to call {@code Gmail.Users.Messages.send} /
 * {@code Gmail.Users.Drafts.send}. Enforced by {@link
 * com.zeromail.core.arch.NoGmailSendAllowedTest} and {@link
 * com.zeromail.core.arch.AssistantSendCallSiteAllowlistTest}.
 *
 * <p>Adding this annotation to a new class is an architecturally significant decision
 * — review against TRG-03 + v1.1 CONFIRMATION state machine before doing so.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AllowedSendCallSite {}
```

**Test 2 — UPDATE `NoGmailSendAllowedTest`** (file already exists at `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java`):

```java
// only the rule definition changes — leave the rest of the file structure as-is
@ArchTest
static final ArchRule no_unannotated_code_calls_gmail_send_apis =
        noClasses()
                .that()
                .areNotAnnotatedWith(AllowedSendCallSite.class)
                .should(
                        new ArchCondition<JavaClass>(
                                "call Gmail.Users.Messages.send or Gmail.Users.Drafts.send") {
                            @Override
                            public void check(
                                    JavaClass javaClass, ConditionEvents conditionEvents) {
                                javaClass
                                        .getMethodCallsFromSelf()
                                        .forEach(
                                                methodCall -> {
                                                    /* unchanged condition body */
                                                });
                            }
                        })
                .because(
                        "TRG-03 (v1.0) + CONFIRMATION-T1 (v1.1): Zero Mail v1.1 may send mail "
                                + "only from a single class annotated @AllowedSendCallSite, "
                                + "reachable only via the user-confirmed chat tool flow.")
                .allowEmptyShould(true);
```

**Test 3 — NEW `AssistantSendCallSiteAllowlistTest`** (file: `backend/core/src/test/java/com/zeromail/core/arch/AssistantSendCallSiteAllowlistTest.java`):

```java
package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zeromail.core.chat.domain.AllowedSendCallSite;
import com.zeromail.core.chat.usecases.AssistantSendExecutor;
import org.junit.jupiter.api.Test;

class AssistantSendCallSiteAllowlistTest {

    @Test
    void exactly_one_class_carries_the_allowed_send_call_site_marker() {
        JavaClasses production =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");
        long markedClassCount =
                production.stream()
                        .filter(
                                javaClass ->
                                        javaClass.isAnnotatedWith(AllowedSendCallSite.class))
                        .count();
        assertThat(markedClassCount)
                .as(
                        "exactly one class may be annotated @AllowedSendCallSite — currently expected: "
                                + "com.zeromail.core.chat.usecases.AssistantSendExecutor")
                .isEqualTo(1L);
    }

    @Test
    void the_marker_lives_on_assistant_send_executor() {
        assertThat(AssistantSendExecutor.class.isAnnotationPresent(AllowedSendCallSite.class))
                .as("AssistantSendExecutor must carry @AllowedSendCallSite — this is the v1.1 carve-out")
                .isTrue();
    }
}
```

**Repo-wide grep gate (CI script, complements the ArchUnit tests):**

```bash
# .github/workflows/grep-no-send.yml (or equivalent step in existing gradle CI)
! grep -RnE "messages\(\)\.send|drafts\(\)\.send" \
    backend/core/src/main/java \
    backend/api/src/main/java \
    backend/worker/src/main/java \
    | grep -vE "AssistantSendExecutor\.java"
```

This grep catches non-Java callers (Groovy scripts, future Kotlin) and double-protects against someone gaming the annotation by importing a wrapper.

**Why three layers (annotation + ArchUnit + grep) and not one?**

Each layer fails differently:
- Annotation alone: silently accepted if someone adds it to a second class.
- ArchUnit alone: someone could write the call inside an inner class that gets a different `JavaClass` representation.
- Grep alone: false positives on test code (e.g., the existing `RuleActionType.valueOf("SEND")` test).

Together they are robust: ArchUnit checks the call graph, grep catches non-Java/inner-class edge cases, and the annotation existence check prevents the second-class-marked-with-annotation cheat.

---

## Suggested Build Order (Phase Split)

The question asks how to split into shippable phases and what blocks what. Recommended phase split:

### Phase A — Backend chat foundation (NO sending, NO frontend yet)

**Goal:** Production-shaped chat backend with persistence + tool catalog (read-only + safe mutations) + SSE controller, EXCLUDING the send executor. Lay all schema. Unblocks everything else.

**What lands:**
1. Liquibase changelogs 041–046 (all six tables).
2. `chat` Modulith module skeleton: `package-info.java` with `allowedDependencies`, sub-packages (`domain/usecases/projection/persistence/exception`).
3. `chat.persistence.*` entities + repositories.
4. `LlmGateway.streamChat(...)` interface addition + `LlmGatewayImpl.streamChat(...)` impl + `SpringAiLlmModelClient` streaming overload. **No new vendor SDK imports.**
5. `ChatTurnService` + `ChatSseFrameEmitter` + `ChatToolRegistry` + `ChatToolExecutor`.
6. Read-only tool handlers (TOOL-T1, T2, T3, T4, T5, T6, T17 — `getAssistantCapabilities`, `getUserRulesAndSettings`, `getRuleExecutionForMessage`, `searchInbox`, `readEmail`, `listLabels`, `searchMemories`).
7. Safe mutation tool handlers (TOOL-T7, T8, T13, T14, T15, T16 — `createOrGetLabel`, `manageInbox`, `updatePersonalInstructions`, `updateAssistantSettings`, `addToKnowledgeBase`, `saveMemory`).
8. Rule-CRUD tool handlers (TOOL-T9, T10, T11, T12 — `createRule`, `updateRuleConditions`, `updateRuleActions`, `deleteRule`).
9. `ChatStreamingController` (POST `/api/chat`) + `ChatHistoryController` (list / get / delete) + `AssistantSettingsController` + `AssistantMemoryController` + `AssistantKnowledgeController`.
10. Unit + slice tests: `ChatModuleBoundaryTest`, `ChatToolRegistryTest`, `ChatPersistencePrivacyTest`, `ConfirmationLeaseServiceTest` (lease impl lands here even though we will not use it until Phase C), `AssistantSettingsServiceTest`.

**Blocks:** Phase B (no UI without the SSE protocol stabilized) and Phase C (no send confirmation without the audit/lease infra). Frontend cannot generate the OpenAPI client for Settings / Memory / Knowledge endpoints without these controllers.

**Does NOT block:** UI prototype work for chat layout (can be done in parallel using `ai-elements` against the Vercel ai-sdk-tools demo stream).

### Phase B — Frontend chat surface + Settings page UI (NO send)

**Goal:** Working `/chat` route with streaming, tool cards, and history sidebar. Settings page tabs render and read/write the new endpoints. Send tool cards render as "(disabled — Phase C)" placeholders.

**What lands:**
1. Frontend dependencies: `pnpm add ai@^6 @ai-sdk/react@^3 streamdown@^2.5`.
2. AI Elements primitive install: `pnpm dlx ai-elements@latest add conversation message prompt-input response tool reasoning loader suggestion`.
3. `apps/web/features/chat/` feature folder fully built (api, components, hooks, query-keys, messages).
4. `apps/web/app/(protected)/(app)/chat/page.tsx` route shell mounting `<ChatApp />`.
5. `apps/web/features/assistant-settings/` feature folder fully built.
6. `apps/web/app/(protected)/(app)/settings/page.tsx` MODIFIED to add `<Tabs>` (AI, Personalization, Behavior, Safety Net, Knowledge).
7. Playwright E2E: golden path = "Create a rule for receipts" → assistant streams → `<Tool>` card renders → rule appears in DB.
8. Vietnamese-default i18n strings for chat chrome + settings tabs.

**Blocks:** Phase C requires Phase B's `<Confirmation>` component scaffolding to be present (it ships as part of the AI Elements add). The send tool card needs the same chrome.

**Does NOT block:** Backend send executor + ArchUnit work can start in parallel with Phase B, as long as the API contract is locked.

### Phase C — Send executor + Confirmation + ArchUnit carve-out

**Goal:** The three send tools work end-to-end with the Confirmation state machine. ArchUnit + grep enforce the single call site. Audit row written per send.

**What lands:**
1. `AllowedSendCallSite` annotation (new file in `chat.domain`).
2. `AssistantSendExecutor` (new file in `chat.usecases`) carrying `@AllowedSendCallSite`. Wires `GmailApiClientFactory` (already a public gateway) + `ConfirmationLeaseService` (built in Phase A) + audit-row write.
3. Send tool handlers (`SendToolHandlers.java`) — call `AssistantSendExecutor` from the chat tool dispatcher.
4. `ChatConfirmController` (POST `/api/chat/{chatId}/confirm`) wiring `ConfirmationLeaseService` + `AssistantSendExecutor`.
5. Spring Modulith event class `AssistantSendCompleted` + analytics subscriber (existing `@TransactionalEventListener` infra).
6. ArchUnit: UPDATE `NoGmailSendAllowedTest` per Strategy above. NEW `AssistantSendCallSiteAllowlistTest`. CI grep step.
7. Frontend: `apps/web/features/chat/components/tool-cards/SendToolCard.tsx` wires `<Confirmation>` to `addToolApprovalResponse` from `useChat`. Persisted-message-id gate (CONFIRMATION-T2 pattern).
8. Playwright E2E: full send golden path (compose → confirm → audit row appears in DB) and cancel path (compose → cancel → no audit, no Gmail mutation).
9. Update `PROJECT.md` Constraints: re-affirm "Auto-send forbidden" with the chat carve-out note already-locked in CLAUDE.md.

**Blocks:** Nothing downstream — this is the last phase of v1.1.

**Does NOT block:** Phase A and Phase B work that does not touch send tools.

### Build-order rationale summary

| Phase | Why this order |
|-------|----------------|
| A first | Schema + Modulith module + SSE + read-only tools are needed by everything else. OpenAPI generation for Settings depends on the controllers landing here. |
| B second | Frontend has nothing to call until Phase A controllers exist. AI Elements installation pulls in `<Confirmation>` even before we wire send. |
| C last | Lowest-risk position for the highest-risk feature. Send + ArchUnit + audit ship as one tight unit so the trust posture is intact at the v1.1 boundary. |

**Alternative orderings considered and rejected:**

- **A → C → B (backend-complete first, then frontend):** Pushes UI risk to the end and the user cannot see anything until late. Frontend chat work is largely independent once the SSE contract is locked, and the streaming protocol is the riskiest contract — landing it early in Phase A and using it in Phase B exercises the protocol.
- **All-in-one mega-phase:** Too large; impossible to QA. The send carve-out is a CASA-relevant trust change and deserves its own GO / NOGO checkpoint.
- **C → A → B (start with send):** Cannot work — send executor depends on `ConfirmationLeaseService`, `AssistantPendingActionEntity`, and the chat persistence model, all of which are Phase A deliverables.

---

## Sources

**Existing v1.0 codebase (HIGH confidence — direct file reads):**
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/llm/package-info.java` — Modulith allowed-deps pattern (line 32–40).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/rules/package-info.java` — same pattern (line 8–17).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/triage/package-info.java` — same pattern (line 5–19).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java` — gateway interface contract (cross-phase callers; privacy invariant; tool allow-list).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java` — verified `internalToolExecutionEnabled(false)` already in use (line 66) — the streaming chat path will reuse this exact options builder.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` — Scoped Values resolver (`currentTenantUuid()` at line 20–22) — used by every new controller.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java` — controller pattern (`@RestController @Tag @RequestMapping`; constructor injection; `TenantContext.currentTenantUuid()` in handler bodies).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — `/api/**` matcher (line 19–20), `csrf().spa()` SPA-token pattern (line 53–56), `HttpStatusEntryPoint(UNAUTHORIZED)` for API (line 60).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` — existing send-gate ArchUnit rule, full structure that v1.1 amends (line 23–61).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java` — pattern for module-scoped `noClasses().that().resideInAnyPackage(...).should(...)` (lines 31–73).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — Liquibase master changelog include pattern (line 1–50; 40 changesets through `040-triage-audit-message-ref.yaml`).
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/resources/db/changelog/changes/021-rules-engine-schema.yaml` — JSONB column pattern for `matcher_ast` (line 44–47).
- `D:/study-materials-summer-2026/EXE202/zero-mail/apps/web/app/(protected)/(app)/settings/page.tsx` — existing settings page composing General/Privacy + BYOK form (line 45–50 — the tabs decision builds on this).
- Directory listings of `apps/web/features/` (16 existing feature folders, e.g. `ai/`, `analytics/`, `auth/`, `billing/`, `gmail/`, `llm/`, `rules/`, `triage/`) — confirms the feature-folder convention for `features/chat/` and `features/assistant-settings/`.
- `apps/web/AGENTS.md` — locks the shadcn primitive rule used by the `<Tabs>` + `<Confirmation>` decisions.

**Reference repo (inbox-zero) — MEDIUM-HIGH confidence as a structural reference, NOT a code source:**
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/prisma/schema.prisma` lines 1026–1087 — `Chat` / `ChatMessage` / `ChatCompaction` / `ChatMemory` model shapes ported to Postgres + JPA. Verified that `ChatMessage.parts` is JSONB (line 1051) — matches our `chat_message.parts JSONB` decision.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/ai/assistant/chat.ts` — Confirmation state machine reference (already validated in `FEATURES.md` lines 144–198).

**v1.1 sibling research (already locked):**
- `.planning/research/STACK.md` — Spring MVC SSE + Spring AI 2.0.0-M6 user-controlled tool execution + Vercel UI Message Stream Protocol verification (Context7-sourced; HIGH confidence; the SSE-controller pattern here is derived from that doc's verified pieces).
- `.planning/research/FEATURES.md` — 20-tool catalog with confirm-vs-direct categorization (drives the tool-handler split inside `chat.usecases.tools.*`). Confirmation state machine (lines 144–198) — drives the `assistant_pending_action` + `ConfirmationLeaseService` design here.
- `.planning/PROJECT.md` — privacy carve-out for chat persistence (line 156) and write-action allow-list (line 157) — drives the "no email bodies in `chat_message.parts`" anti-pattern.

**Project policy docs:**
- `D:/study-materials-summer-2026/EXE202/zero-mail/CLAUDE.md` — Modulith verification + ArchUnit + privacy carve-out for chat (locked) + write-actions policy (locked) + "Spring AI usage confined to one adapter package" (locked).

---

*Architecture research for: Zero Mail v1.1 — chat email assistant + AI settings page integration into existing Spring Boot 4 + Spring Modulith + Next.js 16 monorepo*
*Researched: 2026-05-17 by gsd-researcher (direct codebase read + sibling research integration + verified ArchUnit/Modulith patterns)*
