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
# Architecture Research — Zero Mail v1.2 Delta (Admin Console Foundation + Settings UI)

**Domain:** Integration of an admin console (Phase 8) + the Settings UI on top of an admin-curated LLM catalog (Phase 9), bolted onto the shipped v1.0 + v1.1 Java 25 / Spring Boot 4 / Spring Modulith / Next.js 16 monorepo.
**Researched:** 2026-05-19
**Confidence:** HIGH on existing module layout, ArchUnit gates, Modulith allowedDependencies, Liquibase changelog cadence, controller-grouping convention, AES-GCM BYOK pattern, Spring Session Redis cookie auth, `@Sensitive` Logback scrub, `csrf().spa()` SPA-token pattern, OpenAPI codegen pipeline (all directly verified by reading `backend/core/src/main/java/com/zeromail/core/llm/**`, `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java`, `backend/core/src/main/resources/db/changelog/changes/0[1-4]*.yaml`). MEDIUM-HIGH on admin role storage placement (Spring Security authority model verified, role attached to user vs separate admin_user table is a design choice argued below). MEDIUM on Sync-from-`/models` flow (depends on which providers expose a `/models` listing endpoint at Spring AI M6 maturity — DeepSeek and OpenAI do; Anthropic does not, requires manual catalog seeding).

> **Scope.** This is the v1.2 architecture delta only. The v1.0 baseline (Modulith module list, Scoped Values tenant context, single LLM gateway, AES-GCM BYOK via `RefreshTokenCipher`, Liquibase YAML migrations, cookie sessions) AND the v1.1 delta (the `core.chat` Modulith module, `AssistantSendExecutor` send carve-out, `@AllowedSendCallSite` 3-layer gate, `csrf().spa()` SPA-token CSRF, AI Elements primitives in `apps/web`) are locked. This document only describes new packages, new controllers, new tables, new ArchUnit gates, and new dependency edges for the admin console foundation (Phase 8) and the Settings UI consumption layer (Phase 9). It explicitly does NOT re-research parts that already exist.

---

## System Overview (v1.2 additions on top of v1.0 + v1.1)

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              apps/web (Next.js 16, React 19)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   Existing route groups:                                                              │
│     app/(public)/   app/(auth)/   app/(protected)/(app)/                              │
│                                                                                       │
│   NEW v1.2 route group:                                                               │
│     app/(protected)/(admin)/                  ← NEW route group (admin shell)         │
│       ├── layout.tsx                          ← ROLE_ADMIN gate (server component)    │
│       ├── admin/page.tsx                      ← dashboard (spend, queue health)       │
│       ├── admin/catalog/                                                              │
│       │     ├── page.tsx                      ← provider list                         │
│       │     ├── [providerId]/page.tsx         ← model list per provider               │
│       │     └── feature-bindings/page.tsx     ← chat/triage/draft/compile model pins  │
│       ├── admin/master-keys/page.tsx          ← AES-GCM master-key CRUD               │
│       ├── admin/tenants/                                                              │
│       │     ├── page.tsx                      ← tenant search (read-only)             │
│       │     └── [tenantId]/page.tsx           ← tenant detail (read-only)             │
│       ├── admin/queue/page.tsx                ← worker queue health (read-only)       │
│       ├── admin/audit/page.tsx                ← admin action log (read-only)          │
│       └── admin/spend/page.tsx                ← global LLM spend dashboard            │
│                                                                                       │
│   EXTEND v1.1 routes (no new routes):                                                 │
│     app/(protected)/(app)/settings/page.tsx                                           │
│       └── add ai/personalization/behavior/safety-net tabs                             │
│       └── ai tab consumes /api/settings/catalog (admin-curated)                       │
│                                                                                       │
│   NEW v1.2 features:                                                                  │
│     features/admin-catalog/{api,components,hooks,query-keys.ts,messages.ts}           │
│     features/admin-master-keys/{api,components,hooks,query-keys.ts,messages.ts}       │
│     features/admin-tenants/{api,components,hooks,query-keys.ts,messages.ts}           │
│     features/admin-queue/{api,components,hooks,query-keys.ts,messages.ts}             │
│     features/admin-audit/{api,components,hooks,query-keys.ts,messages.ts}             │
│     features/admin-spend/{api,components,hooks,query-keys.ts,messages.ts}             │
│     features/assistant-settings/  ← v1.1 deferred reqs land here in Phase 9           │
│                                                                                       │
│   middleware.ts (existing) extended to redirect /admin/* away on non-admin session    │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  HTTP same-origin behind reverse proxy
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/api (Spring MVC, Tomcat + virtual threads)              │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   Existing controllers:  rules/  triage/  gmail/  analytics/  billing/  llm/  chat/   │
│                          assistant/  ...                                              │
│                                                                                       │
│   NEW v1.2 controllers (controllers/admin/, dto/admin/):                              │
│     AdminCatalogController         CRUD /api/admin/catalog/providers, /models         │
│                                    POST /api/admin/catalog/providers/{id}/sync-models │
│     AdminFeatureBindingController  GET/PUT /api/admin/catalog/feature-bindings        │
│     AdminMasterKeyController       CRUD /api/admin/master-keys (per provider)         │
│                                    POST /api/admin/master-keys/{provider}/rotate      │
│                                    POST /api/admin/master-keys/{provider}/test        │
│     AdminTenantController          GET /api/admin/tenants (search, paginate)          │
│                                    GET /api/admin/tenants/{tenantId} (read-only)      │
│     AdminQueueController           GET /api/admin/queue/health                        │
│     AdminAuditController           GET /api/admin/audit (filter+paginate)             │
│     AdminSpendController           GET /api/admin/spend/global, /by-tenant, /by-model │
│                                                                                       │
│   NEW v1.2 user-facing controller (read curated catalog):                             │
│     SettingsCatalogController      GET /api/settings/catalog                          │
│                                    (returns admin-curated subset filtered to features │
│                                     the current tenant is allowed to override)        │
│                                                                                       │
│   NEW security layer:                                                                 │
│     AdminAccessVoter (or @PreAuthorize("hasRole('ADMIN')"))                           │
│     /api/admin/** → requires ROLE_ADMIN                                               │
│     Existing SecurityFilterChain (Order 3) extended with new authorizeHttpRequests    │
│     entry; NO new filter chain unless admin needs different CSRF/CORS rules           │
│     (it does not).                                                                    │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  in-process service calls
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/core (Spring Modulith modules)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  Existing modules:                                                                    │
│   tenant  account  gmail  llm  rules  triage  draft  thread  analytics                │
│   billing  notification  onboarding  chat (v1.1)  shared.*                            │
│                                                                                       │
│  NEW v1.2 Modulith module:  com.zeromail.core.admin                                   │
│    Allowed deps: tenant, account, llm, billing, analytics, gmail, triage,             │
│                  chat (read-only projections), shared.persistence, shared.lang,       │
│                  shared.privacy                                                       │
│                                                                                       │
│  Sub-packages of admin/:                                                              │
│   admin/                                                                              │
│    ├── package-info.java        @ApplicationModule(displayName="Admin Console")       │
│    ├── domain/                  AdminRole, AdminAction, AdminAuditEventType,          │
│    │                            CatalogProvider, CatalogModel, FeatureBindingKey,     │
│    │                            ProviderMasterKeyState                                │
│    ├── usecases/                                                                      │
│    │   ├── catalog/             ProviderCatalogService, ModelCatalogService,          │
│    │   │                        FeatureBindingService, CatalogSyncService             │
│    │   ├── masterkey/           MasterKeyService, MasterKeyRotationService,           │
│    │   │                        MasterKeyTestService                                  │
│    │   ├── tenantview/          AdminTenantQueryService (read-only)                   │
│    │   ├── queue/               WorkerQueueHealthService (read-only)                  │
│    │   ├── spend/               GlobalSpendQueryService                               │
│    │   └── audit/               AdminAuditLogger, AdminAuditQueryService              │
│    ├── projection/              CatalogProviderProjection, CatalogModelProjection,    │
│    │                            FeatureBindingProjection, MasterKeyStateProjection,   │
│    │                            TenantSummaryProjection, QueueHealthProjection,       │
│    │                            GlobalSpendProjection, AdminAuditEntryProjection,     │
│    │                            CuratedCatalogProjection (for user Settings)          │
│    ├── persistence/             LlmProviderCatalogEntity, LlmModelCatalogEntity,      │
│    │                            LlmFeatureBindingEntity, LlmProviderMasterKeyEntity,  │
│    │                            AdminAuditEntity, *Repository interfaces              │
│    └── exception/               CatalogValidationException, MasterKeyValidation...    │
│                                                                                       │
│  NEW v1.2 module:  com.zeromail.core.settings.catalog (or settings sub-package)       │
│    Purpose: thin read-side that exposes the admin-curated catalog to per-tenant       │
│             Settings UI. Lives in core because it crosses chat/triage/draft feature   │
│             concerns. ONE service: CuratedCatalogQueryService.                        │
│    Allowed deps: admin (projection), tenant, shared.lang.                             │
│                                                                                       │
│  v1.2 changes to EXISTING modules:                                                    │
│   llm/    ADD: ProviderMasterKeyResolver (reads admin.persistence master-key table)   │
│           NO change to LlmGateway shape.                                              │
│           The platform-default API keys move from application.yml properties to       │
│           the new admin-managed master-key table (with config-fallback for dev).      │
│   account/ ADD: UserEntity.role column (UUID? AccountRole enum) OR new                │
│            user_admin_grant table — see decision below.                               │
│   chat/   NO change. Chat catalog binding still reads through new                     │
│           CuratedCatalogQueryService inside AssistantSettingsService.                 │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  JDBC
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           PostgreSQL 17 (same VPS)                                    │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  NEW v1.2 tables (changelogs 048–056):                                                │
│   048-admin-role.yaml                ALTER users ADD COLUMN role                      │
│   049-llm-provider-catalog.yaml      llm_provider_catalog                             │
│   050-llm-model-catalog.yaml         llm_model_catalog                                │
│   051-llm-feature-binding.yaml       llm_feature_binding                              │
│   052-llm-provider-master-key.yaml   llm_provider_master_key (+ rotation history row) │
│   053-admin-audit.yaml               admin_audit (append-only)                        │
│   054-catalog-seed.yaml              seed: openai/anthropic/google-genai/deepseek     │
│                                            + initial known-good models                │
│   055-catalog-sync-job.yaml          processing_job augment (job_type catalog_sync)   │
│   056-admin-master-key-backfill.yaml backfill: move application.yml platform keys     │
│                                       into llm_provider_master_key (dev-only path)    │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          ▲
                                          │  Redis 7 (same VPS)
                                          │
┌──────────────────────────────────────────────────────────────────────────────────────┐
│   Existing: Spring Session Redis + per-tenant ChatModel cache + assistant:lease:* +   │
│             rate-limit buckets.                                                       │
│   NEW v1.2: admin:catalog:sync:{provider}     ← SET NX EX 300, prevents duplicate     │
│                                                  sync clicks during in-flight job     │
│             admin:masterkey:test:{provider}   ← rate-limit test-connection (60/min)   │
│   NO worker queue use for hot paths — admin actions are synchronous request-scoped    │
│   except sync-from-/models, which dispatches a processing_job.                        │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Decisions (the load-bearing ones)

### D1. Admin lives in `core.admin` (new module), NOT inside `core.llm`

**Decision:** Create new top-level Modulith module `com.zeromail.core.admin`.

**Why.** `core.llm` is the **horizontal gateway** — every domain calls it. Its current `allowedDependencies` is narrow (`tenant`, `billing`, `shared.persistence`, `shared.lang`, `gmail.persistence.crypto`) by design — that narrowness is what makes the Spring AI 2.0.0-M6 → GA migration small. Folding admin in inflates `llm/` to depend on `account` (admin RBAC), `analytics` (spend), `triage` (queue read), `chat` (tenant view) — that broadens the gateway from "anyone calls in" to "knows about every domain," destroying the contract.

Catalog management, master-key rotation, audit, tenant read-views, and queue health are all admin-domain concerns. They orchestrate **across** the existing modules — exactly the shape v1.1 chose for `core.chat`. Same rationale, same pattern.

**Alternative considered (rejected):** `core.llm.admin` sub-package. Rejected because (a) Modulith treats sub-packages as part of the parent module's API surface; (b) ArchUnit's `DomainPurityArchTest` would have to whitelist admin-specific framework deps inside `llm/`; (c) the tenant-view + audit + queue-health responsibilities are non-LLM, so the package name would lie about what's in it.

**What `core.llm` does gain:** ONE new file — `ProviderMasterKeyResolver` — that reads from `admin.persistence.LlmProviderMasterKeyRepository`. That goes inside `llm.gateway.springai` (where existing platform-key resolution already lives) and adds `admin` to `llm`'s `allowedDependencies` list. This is the only crack in `llm`'s narrow contract, and it is justified: master keys are the data `llm` already consumes today (currently from `application.yml`), just sourced differently.

### D2. Admin RBAC: `users.role` column + Spring Security authority, NOT a separate `admin_user` table

**Decision:** Add `role` column to existing `users` table (`USER` | `ADMIN` | `SUPPORT`), surface as Spring Security `GrantedAuthority` (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_SUPPORT`) from the OAuth provisioning step. Enforce with `@PreAuthorize("hasRole('ADMIN')")` on admin controllers plus a single `authorizeHttpRequests` matcher `.requestMatchers("/api/admin/**").hasRole("ADMIN")` in `SecurityConfig`.

**Why.**
- **One identity per Google account.** Admins are Zero Mail employees logging in with Google, same OAuth bundled-scopes flow. A separate `admin_user` table forces dual-login or stub-Gmail-connection workarounds.
- **Spring Security has a first-class authority model.** Re-inventing role checks via custom annotations / `ArchUnit` gates is strictly worse — Spring's `MethodSecurityInterceptor` already short-circuits at the AOP boundary with full audit-log integration.
- **No new auth provider.** Admin uses the same Google OAuth, same Spring Session Redis cookie, same `TenantBindingFilter`, same `csrf().spa()` SPA-token. The admin's *own* tenant (the one that gets provisioned when a Zero Mail employee signs up with their own Google account) coexists with the admin role grant — admins can also be ordinary users of the product.
- **Role grant mechanism is an email allowlist read at provisioning time.** A new `admin.email-allowlist` config property in `application.yml` (and the Postgres-backed `tenant_property` or a new `admin_email_allowlist` table for runtime updates without redeploy) is consulted inside `GoogleOAuthSuccessHandler` to elevate `role=ADMIN` if the email matches. Initial bootstrap is a single config-file entry; subsequent grants/revokes happen through the admin console itself (an admin promotes another email via `/api/admin/grant-admin`).

**Alternative considered (rejected): separate `admin_user` table.** Pros: hard separation of user data and admin grants; revoke admin without touching the user record. Cons (decisive): doubles the OAuth flow (admin login vs user login), forces a second `ROLE_ADMIN` check that doesn't see `TenantContext`, and breaks the "admin is also a user" prosumer-friendly model. Vetoed.

**Alternative considered (rejected): a Spring Security `oauth2Login` UserService that reads role from Google Workspace group membership.** Pros: no DB column. Cons: requires Workspace API scope expansion (not in current bundle, would re-trigger CASA review), couples role to Google's directory, and the project explicitly avoids GCP-specific dependencies (`STACK.md` no-GCP-baseline). Vetoed.

**ArchUnit gate to keep this honest:** new `AdminAccessOnlyOnAdminControllersTest` — any class under `controllers/admin/` MUST be class-annotated with `@PreAuthorize` OR have every public method `@PreAuthorize`-annotated. Forces explicit role gating at every entry point.

### D3. Catalog persistence: 3 tables (`llm_provider_catalog`, `llm_model_catalog`, `llm_feature_binding`)

**Decision:** Normalize provider + model + feature-binding into three tables, NOT a single JSONB blob.

**Why.**
- **Filter + sort.** Admin UI needs to filter models by provider, by capability (supports-tools, supports-streaming, context-window-min), by status (enabled/disabled). JSONB queries are doable but harder to index than a normal columnar schema, and the cardinality is small (4 providers × ~30 models = 120 rows). No reason to JSONB it.
- **Foreign keys keep it consistent.** `llm_feature_binding.model_id → llm_model_catalog.id ON DELETE RESTRICT` means an admin can't disable a model that's still pinned as the default for a feature without first re-pointing the binding. A JSONB column gives no such guarantee.
- **Versioning is per-row.** Each model row carries `release_date`, `pricing_input_usd_per_mtok`, `pricing_output_usd_per_mtok`, `context_window_tokens`, `supports_tools`, `supports_streaming`, `is_deprecated`, `provider_model_id` (the vendor's wire ID, e.g. `gpt-4o-2024-08-06`). Updating pricing for one model is a single `UPDATE` against one row.

**Schema sketch:**

```yaml
# 049-llm-provider-catalog.yaml
- createTable:
    tableName: llm_provider_catalog
    columns:
      - column: { name: id, type: uuid, constraints: { primaryKey: true } }
      - column: { name: provider_key, type: varchar(32), constraints: { nullable: false, unique: true } }
        # openai / anthropic / google-genai / deepseek (matches BYOKProvider.id())
      - column: { name: display_name, type: varchar(64), constraints: { nullable: false } }
      - column: { name: enabled, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
      - column: { name: byok_supported, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
      - column: { name: models_listing_endpoint, type: varchar(256) }
        # e.g. https://api.openai.com/v1/models — nullable; null means manual catalog only
      - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: version, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }

# 050-llm-model-catalog.yaml
- createTable:
    tableName: llm_model_catalog
    columns:
      - column: { name: id, type: uuid, constraints: { primaryKey: true } }
      - column:
          name: provider_id
          type: uuid
          constraints: { nullable: false, foreignKeyName: fk_llm_model_catalog_provider, references: llm_provider_catalog(id) }
      - column: { name: provider_model_id, type: varchar(128), constraints: { nullable: false } }
        # vendor wire id: gpt-4o-2024-08-06, claude-3-5-sonnet-20241022, etc.
      - column: { name: display_name, type: varchar(128), constraints: { nullable: false } }
      - column: { name: enabled, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
      - column: { name: context_window_tokens, type: int, constraints: { nullable: false } }
      - column: { name: supports_tools, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
      - column: { name: supports_streaming, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
      - column: { name: pricing_input_usd_per_mtok, type: numeric(10,4) }
      - column: { name: pricing_output_usd_per_mtok, type: numeric(10,4) }
      - column: { name: is_deprecated, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
      - column: { name: release_date, type: date }
      - column: { name: notes, type: text }
        # admin-authored notes shown to users in the Settings AI tab tooltip
      - column: { name: synced_at, type: timestamptz }
        # last time the row came from a provider /models call; null = hand-added
      - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: version, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }
- addUniqueConstraint:
    tableName: llm_model_catalog
    columnNames: provider_id, provider_model_id
    constraintName: uq_llm_model_catalog_provider_modelid

# 051-llm-feature-binding.yaml
- createTable:
    tableName: llm_feature_binding
    columns:
      - column: { name: id, type: uuid, constraints: { primaryKey: true } }
      - column: { name: feature_key, type: varchar(32), constraints: { nullable: false, unique: true } }
        # chat / triage / draft / rule-compile / semantic-intent — IdentifiedEnum FeatureKey
      - column:
          name: default_model_id
          type: uuid
          constraints: { nullable: false, foreignKeyName: fk_llm_feature_binding_model, references: llm_model_catalog(id) }
      - column: { name: user_override_allowed, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
      - column: { name: byok_allowed, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
      - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: version, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }
```

**Why `provider_model_id` is separate from `id`:** vendor IDs change naming (`gpt-4o` → `gpt-4o-2024-08-06`); we keep a stable internal UUID + the wire ID as a versioned field. Bindings reference internal UUID — a model deprecation does not break bindings until the admin re-points.

**Catalog seed (changelog 054)** ships with the current v1.0/v1.1 known-good combos: OpenAI `gpt-4o-mini` + `gpt-4o`, Anthropic `claude-3-5-sonnet-20241022` + `claude-3-5-haiku-20241022`, Google `gemini-1.5-pro` + `gemini-1.5-flash`, DeepSeek `deepseek-chat`. This is the safety net so the system boots even if /models sync fails.

### D4. Master-key storage: new `llm_provider_master_key` table, reuse `RefreshTokenCipher` (AES-GCM)

**Decision:** New table `llm_provider_master_key`, one row per provider, key envelope encrypted with the existing app-layer `RefreshTokenCipher` (AES-GCM, key version, AAD = provider key). DO NOT extend `tenant_byok_credentials` — that table is tenant-scoped (FK to `tenants`), and master keys are tenant-less (platform-wide).

**Why.**
- **AES-GCM at app layer is the project's locked crypto story** (CLAUDE.md "no `pgp_sym_encrypt`"). Reusing `RefreshTokenCipher` means one crypto codepath, one key-rotation procedure, one ArchUnit gate.
- **Rotation history matters.** Master-key rotation needs to track: current encrypted key, previous encrypted key (for grace window), key_version, rotated_by (admin user UUID), rotated_at. A simple `(provider, encrypted_key, key_version, rotated_by, rotated_at)` shape covers it; rotation is a single transactional UPDATE that bumps `key_version` + writes an `admin_audit` row.
- **Plaintext never persists.** `RefreshTokenCipher.decrypt(...)` returns plaintext bytes that the `ProviderMasterKeyResolver` hands to Spring AI's per-request ChatClient (existing per-tenant cache key scheme already wipes the key after the request — same as BYOK in v1.0).

**Schema sketch (changelog 052):**

```yaml
- createTable:
    tableName: llm_provider_master_key
    columns:
      - column: { name: id, type: uuid, constraints: { primaryKey: true } }
      - column:
          name: provider_id
          type: uuid
          constraints: { nullable: false, unique: true, uniqueConstraintName: uq_llm_provider_master_key_provider, foreignKeyName: fk_llm_provider_master_key_provider, references: llm_provider_catalog(id) }
      - column: { name: encrypted_key, type: bytea, constraints: { nullable: false } }
      - column: { name: key_version, type: smallint, constraints: { nullable: false } }
      - column: { name: previous_encrypted_key, type: bytea }
        # grace window: keep previous after rotation for in-flight requests holding the old key
      - column: { name: previous_key_version, type: smallint }
      - column: { name: rotated_by_user_id, type: uuid, constraints: { foreignKeyName: fk_llm_provider_master_key_rotated_by, references: users(id) } }
      - column: { name: rotated_at, type: timestamptz }
      - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
      - column: { name: version, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }
```

**Sensitive-log scrub:** `LlmProviderMasterKeyEntity.encryptedKey` and `.previousEncryptedKey` are `@Sensitive` (existing Logback scrub catches it). The entity NEVER exposes the decrypted plaintext through a getter — `ProviderMasterKeyResolver` is the only consumer and it hands the plaintext directly into the Spring AI options builder, then zeroes the buffer.

**Test-connection flow:**
```
[Admin clicks "Test connection" for OpenAI]
    ↓ POST /api/admin/master-keys/openai/test
[AdminMasterKeyController.test(provider)]
    ↓ rate-limit check Redis admin:masterkey:test:openai (max 60/min)
[MasterKeyTestService.test(provider)]
    ↓ decrypt key via RefreshTokenCipher (in-memory only)
    ↓ build a one-off Spring AI ChatClient with the key
    ↓ send a 3-token "ping" prompt to the cheapest model for that provider
    ↓ on success: write admin_audit row (action=MASTER_KEY_TEST_OK, target=provider)
    ↓ on failure: write admin_audit row (action=MASTER_KEY_TEST_FAILED, target=provider, error_kind=...)
    ↓ return AdminMasterKeyTestResponse { ok, latencyMs, modelTested, errorKind? }
```

### D5. Sync-from-`/models`: dispatched as a `processing_job`, NOT synchronous

**Decision:** Admin clicks "Sync models" → POST `/api/admin/catalog/providers/{providerId}/sync-models` → backend writes a `processing_job(job_type=catalog_sync, payload={providerId})` and returns `202 Accepted` with a `jobId`. The worker (`backend/worker`) picks it up via SKIP LOCKED, calls the provider's `/v1/models` endpoint, upserts `llm_model_catalog` rows. Admin UI polls `GET /api/admin/jobs/{jobId}` every 2s for status.

**Why.**
- **Provider `/models` calls can take 2–15 seconds** for cold connections. Holding an HTTP request thread for that is bad UX (admin sees a spinner with no progress; if it times out, the catalog is half-synced).
- **Already have the queue.** v1.0 ships a Postgres `processing_job` table with SKIP LOCKED in `backend/worker`. Adding a new `job_type=catalog_sync` is one new handler class. Don't introduce a parallel async mechanism.
- **Redis idempotency lease** (`admin:catalog:sync:{provider}` SET NX EX 300) prevents double-dispatch if the admin double-clicks. The lease is independent of the processing-job row; it short-circuits the controller before the row is written.
- **Resilient to mid-sync failure.** A processing_job that fails halfway leaves the catalog in a consistent intermediate state — we upsert per-model in its own transaction, so a network blip between models 17 and 18 leaves models 1–17 updated and 18–N untouched. Re-running the job is idempotent (UPSERT by `(provider_id, provider_model_id)`).

**Worker handler (new `backend/worker` code):**
```java
@Component
public class CatalogSyncJobHandler implements ProcessingJobHandler {
    @Override public String jobType() { return "catalog_sync"; }
    @Override public void handle(ProcessingJobRecord job) {
        UUID providerId = UUID.fromString(job.payload().get("providerId").asText());
        // calls into core.admin.usecases.catalog.CatalogSyncService.runForProvider(providerId)
    }
}
```

Sync logic lives in `core.admin.usecases.catalog.CatalogSyncService` — the worker is a thin dispatcher.

**Audit:** every sync run (success or fail) writes an `admin_audit` row (`action=CATALOG_SYNC_RUN`, `actor=<admin user>`, `target=<provider>`, `meta={modelsAdded,modelsUpdated,modelsDeprecated,durationMs,errorKind?}`).

### D6. Admin audit log: separate `admin_audit` table, NOT unified with triage audit

**Decision:** New table `admin_audit`. Distinct from v1.0's `triage_audit` and v1.1's `assistant_send_audit`.

**Why three audit tables instead of one unified `audit_log`?**
- **Different actors, different schemas.** Triage audit: actor=system (rule engine), target=Gmail message. Send audit: actor=user-confirmed, target=Gmail message. Admin audit: actor=admin user, target=catalog row / master key / tenant record / config setting. A unified shape would need polymorphic `actor_type` + `actor_id` + `target_type` + `target_id` + `meta JSONB`, and queries against it would be slower than three focused tables.
- **Retention requirements differ.** Triage audit = 30-day rolling window with auto-prune. Send audit = append-only, no auto-prune (the trust story requires it). Admin audit = append-only, indefinite (compliance / forensics). Mixing them in one table forces a single retention policy that's wrong for two of three.
- **Query patterns differ.** Triage audit is queried per-tenant per-day for UI. Send audit is queried per-chat for the audit UI. Admin audit is queried globally with filters (admin user, action type, target type, date range) — a different index strategy.

**Schema sketch (changelog 053):**

```yaml
- createTable:
    tableName: admin_audit
    columns:
      - column: { name: id, type: uuid, constraints: { primaryKey: true } }
      - column:
          name: actor_user_id
          type: uuid
          constraints: { nullable: false, foreignKeyName: fk_admin_audit_actor, references: users(id) }
      - column: { name: actor_email_snapshot, type: varchar(320), constraints: { nullable: false } }
        # captured at write time so deleting the admin's user row doesn't erase the audit trail
      - column: { name: action_type, type: varchar(64), constraints: { nullable: false } }
        # CATALOG_PROVIDER_UPDATED, CATALOG_MODEL_TOGGLED, FEATURE_BINDING_CHANGED,
        # MASTER_KEY_ROTATED, MASTER_KEY_TEST_OK, MASTER_KEY_TEST_FAILED,
        # CATALOG_SYNC_RUN, TENANT_VIEWED, ADMIN_GRANTED, ADMIN_REVOKED
      - column: { name: target_type, type: varchar(32) }
        # provider | model | feature-binding | master-key | tenant | user
      - column: { name: target_id, type: varchar(64) }
      - column: { name: diff, type: jsonb }
        # { before: {...}, after: {...} } for mutation actions; null for view actions
      - column: { name: client_ip, type: varchar(64) }
      - column: { name: user_agent, type: varchar(256) }
      - column: { name: occurred_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
- createIndex:
    indexName: idx_admin_audit_actor_occurred
    tableName: admin_audit
    columns: [ { column: { name: actor_user_id } }, { column: { name: occurred_at, descending: true } } ]
- createIndex:
    indexName: idx_admin_audit_action_occurred
    tableName: admin_audit
    columns: [ { column: { name: action_type } }, { column: { name: occurred_at, descending: true } } ]
```

**ArchUnit gate:** new `AdminMutationsMustAuditTest` — any method on a `controllers/admin/*` controller that has HTTP verb `POST`, `PUT`, `PATCH`, `DELETE` MUST call `AdminAuditLogger.log(...)`. Prevents "forgot to audit" silently shipping.

### D7. Tenant read-only views: Spring Data JDBC projections, ArchUnit-banned from JPA writes

**Decision:** `core.admin.usecases.tenantview.AdminTenantQueryService` uses Spring Data JDBC (already a project dep — used by analytics for read paths) to project tenant summaries. The repository in `admin.persistence.AdminTenantQueryRepository` is a `@Repository`-annotated interface extending `org.springframework.data.repository.Repository<TenantSummaryProjection, UUID>` with only `find...` methods — NO `save` / `delete`. ArchUnit verifies admin's tenant-read path has zero `EntityManager` / `JpaRepository` / `@Modifying` references for tenant entities.

**Why JPA-free reads here.**
- **Cross-domain query.** Tenant detail needs columns from `tenants`, `users`, `gmail_connection`, `tenant_byok_credentials` (status only, not key bytes), `credit_ledger_entry` aggregates, `triage_audit` counts, recent `chat` activity. JPA forces either N+1 fetches or fetch-graph annotations that complicate the entity definitions. A single SQL with joins is clearer.
- **Read-only guarantee at the type level.** A `Repository` (not `CrudRepository`) interface with only query methods physically can't write. The ArchUnit gate makes that contract enforceable across refactors.
- **Body content stays out.** The projection records have no `body` / `bodyText` / `parts` fields. ArchUnit `AdminProjectionPrivacyTest` rejects any projection record whose name suggests body content (regex `(?i)body|content|prompt|completion`) — extends the v1.1 chat persistence privacy gate to admin reads.

**Spend dashboard:** same pattern — Spring Data JDBC against `credit_ledger_entry` joined with `llm_model_catalog` (for pricing) and `tenants` (for grouping). The aggregation runs as a parameterized SQL query, not a JPQL.

### D8. Worker queue health: read-only projection over existing `processing_job` table — NO new schema

**Decision:** `WorkerQueueHealthService` runs read-only Spring Data JDBC queries against the existing v1.0 `processing_job` + `mail_message_observed` + `pubsub_delivery` tables and the Modulith event publication table. Returns counts grouped by `(job_type, state)`, oldest-unstarted timestamp per job_type, and stuck-job count (`state=PROCESSING AND leased_until < now()`).

**Why not new schema.**
- The queue *is* the schema. Adding a `queue_health_snapshot` table would either be a denormalization that's stale (write-on-trigger) or a duplication that's racy (write-on-cron).
- The dashboard refresh interval is "user clicks refresh" or 30s auto-poll — query latency on `processing_job` with proper indexes is sub-50ms even at v1.2 scale (50 tenants × low hundreds of jobs/day).
- Existing indexes on `(state, leased_until)` (added in v1.0 for the worker SKIP LOCKED scan) make this query cheap.

**Three projection records:**
- `QueueDepthByJobTypeProjection(jobType, state, count)`
- `OldestUnstartedByJobTypeProjection(jobType, oldestQueuedAt, ageSeconds)`
- `StuckJobProjection(jobId, jobType, state, leasedUntil, attempts)`

### D9. Settings AI tab consumes a **curated** catalog via `SettingsCatalogController`

**Decision:** New user-facing endpoint `GET /api/settings/catalog` returns the admin-curated catalog filtered to:
- providers with `enabled=true`,
- models with `enabled=true AND is_deprecated=false`,
- feature bindings where `user_override_allowed=true`.

The response shape is the **user-facing projection** (`CuratedCatalogProjection`) — admin-only fields (`pricing_*`, `synced_at`, `notes` if internal) are stripped.

**Why not have Settings UI hit `/api/admin/catalog/...` directly with a public ACL?**
- **Privilege separation by URL prefix is auditable.** `/api/admin/**` is `hasRole('ADMIN')` and that's it. Mixing user-readable subsets into admin endpoints with conditional field stripping is a leak waiting to happen.
- **Different shape.** Admin sees pricing, deprecated flags, sync metadata. Users see only what they can choose. Two endpoints, two DTOs, no `?adminMode=true` flag.
- **Cache differently.** User catalog is cached per-locale with a long TTL (changes are admin-rare). Admin catalog is uncached.

`CuratedCatalogQueryService` lives in `core.admin.usecases.catalog` (it queries admin's tables) but is exposed via `backend/api`'s `SettingsCatalogController`, which sits under the existing user `/api/settings/*` controller group — NOT under `/api/admin/*`. The controller path determines ACL, not the underlying service.

### D10. Frontend `/admin/*` routes are a SEPARATE route group `(admin)`, NOT layered onto `(app)`

**Decision:** New route group `apps/web/app/(protected)/(admin)/` with its own `layout.tsx` that:
1. Server-side reads the session (existing `getServerSession()` or equivalent).
2. Checks the `role` claim from `/api/me` (extend the existing `MeResponse` DTO with `role`).
3. Redirects to `/` (or `/403`) if `role !== 'ADMIN'`.
4. Renders a distinct admin shell (top nav: "Catalog • Master Keys • Tenants • Queue • Spend • Audit") — different from the user app shell.

**Why a separate route group.**
- **Different navigation.** Admin nav is not "Rules / Chat / Settings"; it's "Catalog / Master Keys / Tenants / Queue / Spend / Audit." Sharing the `(app)` layout forces a conditional nav with role-based filtering, which is uglier and easier to bug.
- **Different middleware concern.** `middleware.ts` already handles `(protected)` redirect-to-login. Adding `(admin)` lets us layer a second middleware check (admin role → admin layout) without touching the user app path. Existing protected check still runs; admin check is an additional layer.
- **Different visual language.** Admin pages don't need the purple Zero Mail brand chrome — they should look like a workshop console (denser tables, less brand). A separate layout makes that natural.
- **No deep-link confusion.** A user accidentally visiting `/admin/catalog` gets the redirect; they don't see a partial admin page render with permission errors.

Existing user routes are unchanged. The Settings AI tab in `(app)/settings` reads from `/api/settings/catalog` — it does NOT cross into `/admin/*`.

---

## Component Responsibilities (v1.2 only)

| Component | Responsibility | Where it lives |
|-----------|----------------|----------------|
| `AdminCatalogController` | CRUD on providers + models; trigger sync-from-`/models` (returns 202 + jobId). | `backend/api/.../controllers/admin/AdminCatalogController.java` |
| `AdminFeatureBindingController` | GET + PUT for `(featureKey → defaultModel, userOverrideAllowed, byokAllowed)` bindings. | `backend/api/.../controllers/admin/AdminFeatureBindingController.java` |
| `AdminMasterKeyController` | Per-provider master-key CRUD + rotate + test. | `backend/api/.../controllers/admin/AdminMasterKeyController.java` |
| `AdminTenantController` | Read-only tenant search + detail. | `backend/api/.../controllers/admin/AdminTenantController.java` |
| `AdminQueueController` | Read-only `processing_job` health view. | `backend/api/.../controllers/admin/AdminQueueController.java` |
| `AdminAuditController` | Read-only `admin_audit` paginated query. | `backend/api/.../controllers/admin/AdminAuditController.java` |
| `AdminSpendController` | Global + by-tenant + by-model LLM spend aggregates. | `backend/api/.../controllers/admin/AdminSpendController.java` |
| `SettingsCatalogController` | User-facing curated catalog GET. | `backend/api/.../controllers/settings/SettingsCatalogController.java` |
| `ProviderCatalogService` | Provider CRUD + toggle. Writes `admin_audit` on mutation. | `backend/core/.../admin/usecases/catalog/ProviderCatalogService.java` |
| `ModelCatalogService` | Model CRUD + toggle + manual add. | `backend/core/.../admin/usecases/catalog/ModelCatalogService.java` |
| `FeatureBindingService` | Feature → model pin + override flags. | `backend/core/.../admin/usecases/catalog/FeatureBindingService.java` |
| `CatalogSyncService` | The actual /models call + upsert loop. Called BOTH from the admin controller (for tiny one-off catalogs that don't need a job) AND from the worker job handler. Idempotent. | `backend/core/.../admin/usecases/catalog/CatalogSyncService.java` |
| `MasterKeyService` | Create + read (status only, never plaintext) + delete. Encrypts via `RefreshTokenCipher`. | `backend/core/.../admin/usecases/masterkey/MasterKeyService.java` |
| `MasterKeyRotationService` | Rotate (encrypt new → move old to `previous_*` → bump version → audit). Grace window cleanup is a worker job (out of v1.2). | `backend/core/.../admin/usecases/masterkey/MasterKeyRotationService.java` |
| `MasterKeyTestService` | Decrypt + cheap /ping LLM call through Spring AI; never logs the key. | `backend/core/.../admin/usecases/masterkey/MasterKeyTestService.java` |
| `ProviderMasterKeyResolver` | NEW in `core.llm.gateway.springai`. The one place that reads `llm_provider_master_key` and supplies plaintext key bytes to Spring AI options. | `backend/core/.../llm/gateway/springai/ProviderMasterKeyResolver.java` |
| `AdminTenantQueryService` | Spring Data JDBC projections of tenant data; NO writes; NO body content. | `backend/core/.../admin/usecases/tenantview/AdminTenantQueryService.java` |
| `WorkerQueueHealthService` | Read-only aggregation over `processing_job`. | `backend/core/.../admin/usecases/queue/WorkerQueueHealthService.java` |
| `GlobalSpendQueryService` | Spend rollups joining `credit_ledger_entry` × `llm_model_catalog` × `tenants`. | `backend/core/.../admin/usecases/spend/GlobalSpendQueryService.java` |
| `AdminAuditLogger` | One-line API: `log(action, target, diff)` reads `TenantContext` + Spring Security principal + request meta and writes one row. | `backend/core/.../admin/usecases/audit/AdminAuditLogger.java` |
| `AdminAuditQueryService` | Paginated filter query. | `backend/core/.../admin/usecases/audit/AdminAuditQueryService.java` |
| `CuratedCatalogQueryService` | User-facing read (curated subset). | `backend/core/.../admin/usecases/catalog/CuratedCatalogQueryService.java` |
| `CatalogSyncJobHandler` | Worker dispatcher for `job_type=catalog_sync`. Thin wrapper that calls `CatalogSyncService`. | `backend/worker/.../jobs/CatalogSyncJobHandler.java` |
| `AdminAccessVoter` (if needed) | If Spring Security `@PreAuthorize("hasRole('ADMIN')")` is not enough — only add if discoveries during execution show role grants need per-tenant context (probably not). | `backend/api/.../security/AdminAccessVoter.java` |

---

## Recommended Project Structure

### Backend — new packages under `backend/core/src/main/java/com/zeromail/core/`

```
admin/
├── package-info.java                  # @ApplicationModule(displayName="Admin Console", allowedDependencies={...})
├── domain/                            # framework-free vocabulary
│   ├── package-info.java
│   ├── AdminRole.java                 # IdentifiedEnum: USER, ADMIN, SUPPORT (SUPPORT = read-only admin)
│   ├── AdminActionType.java           # IdentifiedEnum of all auditable action types
│   ├── FeatureKey.java                # IdentifiedEnum: CHAT, TRIAGE, DRAFT, RULE_COMPILE, SEMANTIC_INTENT
│   ├── ProviderKey.java               # value object — wraps the 4 provider identifiers (matches BYOKProvider)
│   ├── ModelCapability.java           # bit-flag style record: supportsTools, supportsStreaming, contextWindow
│   ├── CatalogValidationRules.java    # pure validation of provider_model_id shape, pricing ranges, etc.
│   └── MasterKeyValidator.java        # checks key looks like a key for that provider (sk-... for OpenAI, etc.)
├── usecases/
│   ├── package-info.java
│   ├── catalog/
│   │   ├── ProviderCatalogService.java
│   │   ├── ModelCatalogService.java
│   │   ├── FeatureBindingService.java
│   │   ├── CatalogSyncService.java
│   │   ├── CuratedCatalogQueryService.java
│   │   └── commands/results records
│   ├── masterkey/
│   │   ├── MasterKeyService.java
│   │   ├── MasterKeyRotationService.java
│   │   ├── MasterKeyTestService.java
│   │   └── commands/results records
│   ├── tenantview/
│   │   ├── AdminTenantQueryService.java
│   │   └── TenantSearchQuery.java
│   ├── queue/
│   │   └── WorkerQueueHealthService.java
│   ├── spend/
│   │   ├── GlobalSpendQueryService.java
│   │   └── SpendBucket.java
│   └── audit/
│       ├── AdminAuditLogger.java
│       └── AdminAuditQueryService.java
├── projection/
│   ├── package-info.java
│   ├── CatalogProviderProjection.java
│   ├── CatalogModelProjection.java
│   ├── FeatureBindingProjection.java
│   ├── MasterKeyStateProjection.java    # { provider, hasKey, keyVersion, lastRotatedAt, lastTestStatus }
│   ├── TenantSummaryProjection.java
│   ├── TenantDetailProjection.java
│   ├── QueueDepthProjection.java
│   ├── OldestUnstartedProjection.java
│   ├── StuckJobProjection.java
│   ├── GlobalSpendProjection.java
│   ├── AdminAuditEntryProjection.java
│   └── CuratedCatalogProjection.java    # user-facing — stripped of admin-only fields
├── persistence/
│   ├── package-info.java
│   ├── LlmProviderCatalogEntity.java
│   ├── LlmProviderCatalogRepository.java
│   ├── LlmModelCatalogEntity.java
│   ├── LlmModelCatalogRepository.java
│   ├── LlmFeatureBindingEntity.java
│   ├── LlmFeatureBindingRepository.java
│   ├── LlmProviderMasterKeyEntity.java       # encrypted bytea fields are @Sensitive
│   ├── LlmProviderMasterKeyRepository.java
│   ├── AdminAuditEntity.java                  # append-only
│   ├── AdminAuditRepository.java
│   └── jdbc/                                  # Spring Data JDBC read-side
│       ├── AdminTenantQueryRepository.java    # read-only, no save/delete
│       ├── QueueHealthQueryRepository.java
│       └── GlobalSpendQueryRepository.java
└── exception/
    ├── package-info.java
    ├── CatalogValidationException.java
    ├── ProviderInUseException.java            # can't disable provider with active bindings
    ├── ModelInUseException.java               # can't delete model that's a feature default
    ├── MasterKeyMissingException.java
    ├── MasterKeyTestFailedException.java
    └── AdminAccessDeniedException.java
```

### Backend — changes to existing modules

```
backend/core/src/main/java/com/zeromail/core/
├── account/
│   └── persistence/UserEntity.java                  # ADD: @Column role (AdminRole)
├── account/usecases/OAuthProvisioningService.java   # ADD: consult AdminEmailAllowlist, set role=ADMIN if listed
├── account/projection/CurrentUserProjection.java    # ADD: role field
└── llm/
    ├── package-info.java                            # ADD allowedDependencies: "admin"
    └── gateway/springai/
        └── ProviderMasterKeyResolver.java           # NEW — reads admin.persistence.LlmProviderMasterKeyRepository
```

### Backend — new files under `backend/api`

```
backend/api/src/main/java/com/zeromail/api/
├── controllers/
│   ├── admin/
│   │   ├── AdminCatalogController.java
│   │   ├── AdminFeatureBindingController.java
│   │   ├── AdminMasterKeyController.java
│   │   ├── AdminTenantController.java
│   │   ├── AdminQueueController.java
│   │   ├── AdminAuditController.java
│   │   ├── AdminSpendController.java
│   │   └── AdminGrantController.java          # POST /api/admin/grant-admin (promote a user)
│   └── settings/
│       └── SettingsCatalogController.java     # GET /api/settings/catalog (user-facing)
├── dto/
│   ├── admin/
│   │   ├── CatalogProviderRequest.java + Response.java
│   │   ├── CatalogModelRequest.java + Response.java
│   │   ├── FeatureBindingRequest.java + Response.java
│   │   ├── MasterKeyCreateRequest.java + Response.java
│   │   ├── MasterKeyTestResponse.java
│   │   ├── TenantSummaryResponse.java
│   │   ├── TenantDetailResponse.java
│   │   ├── QueueHealthResponse.java
│   │   ├── GlobalSpendResponse.java
│   │   ├── AdminAuditPageResponse.java
│   │   └── CatalogSyncJobAcceptedResponse.java
│   └── settings/
│       └── CuratedCatalogResponse.java
└── security/
    └── AdminEmailAllowlistProperties.java     # @ConfigurationProperties("zeromail.admin")
```

### Backend — new files under `backend/worker`

```
backend/worker/src/main/java/com/zeromail/worker/
└── jobs/
    └── CatalogSyncJobHandler.java             # dispatcher for job_type=catalog_sync
```

### Backend — new test files

```
backend/core/src/test/java/com/zeromail/core/arch/
├── AdminAccessOnlyOnAdminControllersTest.java     # all admin controllers @PreAuthorize-gated
├── AdminMutationsMustAuditTest.java               # write verbs on admin controllers call AdminAuditLogger
├── AdminProjectionPrivacyTest.java                # admin projections have no body/content fields
└── AdminTenantViewIsReadOnlyTest.java             # AdminTenantQueryRepository has no save/delete

backend/core/src/test/java/com/zeromail/core/admin/
├── CatalogSyncServiceIdempotencyTest.java
├── MasterKeyRotationServiceTest.java               # grace window correctness
├── MasterKeyTestServiceTest.java                   # mocked Spring AI, no real LLM
├── AdminAuditLoggerTest.java
├── CuratedCatalogQueryServiceTest.java             # admin-only fields stripped
└── FeatureBindingServiceRestrictTest.java          # can't delete model with active binding
```

### Frontend — new route group + features

```
apps/web/
├── app/(protected)/(admin)/
│   ├── layout.tsx                              # role gate + admin shell
│   ├── admin/page.tsx                          # dashboard
│   ├── admin/catalog/page.tsx                  # providers list
│   ├── admin/catalog/[providerId]/page.tsx     # models per provider
│   ├── admin/catalog/feature-bindings/page.tsx
│   ├── admin/master-keys/page.tsx
│   ├── admin/tenants/page.tsx
│   ├── admin/tenants/[tenantId]/page.tsx
│   ├── admin/queue/page.tsx
│   ├── admin/audit/page.tsx
│   └── admin/spend/page.tsx
├── app/(protected)/(app)/settings/page.tsx     # MODIFY — add 4 tabs; AI tab pulls from /api/settings/catalog
├── features/
│   ├── admin-catalog/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-master-keys/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-tenants/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-queue/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-audit/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-spend/{api,components,hooks,query-keys.ts,messages.ts}
│   ├── admin-shell/                            # cross-admin shell (nav, layout pieces)
│   └── assistant-settings/{api,components,hooks,query-keys.ts,messages.ts}    # Phase 9
└── middleware.ts                               # MODIFY — add admin role check for /admin/*
```

### Structure rationale (admin module placement)

- **`admin/` is a new top-level Modulith module, sibling of `chat/`.** Same reasoning v1.1 used: catalog management, master-key rotation, audit, tenant read-views, queue health, and spend reporting are cross-cutting concerns that touch every other module. Folding them inside any one existing module (`llm/`, `account/`, `analytics/`) inflates that module's `allowedDependencies` and destroys its narrow contract.

- **`admin.usecases.catalog/`, `admin.usecases.masterkey/`, `admin.usecases.tenantview/`, etc. are sub-packages, NOT sub-modules.** Spring Modulith treats sub-packages as part of the parent module's internals. The directory split is organizational; the Modulith boundary is at `admin/`. This is the same pattern `core.chat.usecases.tools.*` uses in v1.1.

- **`ProviderMasterKeyResolver` lives in `core.llm.gateway.springai/`, NOT in `core.admin.usecases.masterkey/`.** The resolver's *consumer* is Spring AI's option builder, which already lives in `llm.gateway.springai`. Putting the resolver there keeps the Spring AI confinement zone intact (the resolver imports `org.springframework.ai.*` to call `withApiKey(...)`; if it lived in `admin/`, the admin module would have to allow Spring AI imports, which is exactly what we're avoiding). The resolver depends on `admin.persistence.LlmProviderMasterKeyRepository` — that's the one new dependency edge in `llm`'s `allowedDependencies`.

- **`CuratedCatalogQueryService` lives in `core.admin.usecases.catalog/`, NOT in a new `core.settings/` module.** A separate "settings" module that just re-exports a curated view of admin's catalog is a one-class module — the kind of premature split that violates the project's flat-folder rule. Instead, the admin module owns both the admin-facing and user-facing read services for the same data; the URL prefix (`/api/admin/*` vs `/api/settings/*`) and the DTO shape determine the privilege level.

- **`SettingsCatalogController` lives in `backend/api/.../controllers/settings/`, NOT in `controllers/admin/`.** The controller path determines URL prefix determines ACL. Putting the curated-catalog endpoint under `controllers/admin/` would either require `/api/admin/settings-catalog` (wrong URL for a user endpoint) or break the `/api/admin/** → hasRole('ADMIN')` matcher (wrong ACL).

- **Frontend `(admin)` route group is a sibling of `(app)`, NOT a sub-folder of it.** Two reasons: (1) the user's locked memory rule against single-purpose nested parents (a nested `(app)/admin/` would be exactly that for one admin shell); (2) the admin shell is a different visual surface (denser, less brand, no Vietnamese-first translation requirement since admins read English).

- **`features/admin-*` are flat top-level features, NOT one `features/admin/{catalog,master-keys,...}` super-folder.** Same flat-folder rule. Each admin capability is a feature in its own right; the `admin-` prefix is naming, not hierarchy.

---

## Architectural Patterns

### Pattern A1: Admin controller class-level `@PreAuthorize` + service-level audit

**What:** Every controller in `controllers/admin/` is class-annotated `@PreAuthorize("hasRole('ADMIN')")`. Inside each write handler, the first line in the service (NOT the controller) calls `AdminAuditLogger.log(action, target, diff)` before the mutation. The logger captures actor + request meta from `SecurityContextHolder` + `RequestContextHolder`.

**Why:** Two reasons to put audit in the service, not the controller. (1) The controller doesn't know what to record (it doesn't have the "before" state for a diff). (2) If a refactor moves the controller, the audit follows the service — it's an invariant of the use case, not of the HTTP edge.

**Example:**

```java
@RestController
@RequestMapping("/api/admin/catalog/models")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-catalog")
public class AdminCatalogModelController {

    private final ModelCatalogService modelCatalogService;

    public AdminCatalogModelController(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    @PutMapping("/{modelId}")
    public CatalogModelResponse update(
            @PathVariable UUID modelId,
            @Valid @RequestBody CatalogModelUpdateRequest request) {
        CatalogModelProjection updated = modelCatalogService.update(modelId, request.toCommand());
        return CatalogModelResponse.from(updated);
    }
}
```

```java
@Service
public class ModelCatalogService {
    public CatalogModelProjection update(UUID modelId, ModelUpdateCommand command) {
        LlmModelCatalogEntity before = modelCatalogRepository.findById(modelId)
                .orElseThrow(() -> new CatalogValidationException("Model not found: " + modelId));
        ModelDiff diff = ModelDiff.between(before, command);  // pure
        // audit BEFORE the mutation — failed audit aborts the change
        adminAuditLogger.log(AdminActionType.CATALOG_MODEL_UPDATED, "model", modelId.toString(), diff.asJson());
        before.applyUpdate(command);
        LlmModelCatalogEntity saved = modelCatalogRepository.save(before);
        return CatalogModelProjection.from(saved);
    }
}
```

**Trade-offs:**
- (+) Consistent gate. Any new admin endpoint that forgets `@PreAuthorize` fails `AdminAccessOnlyOnAdminControllersTest`.
- (+) Audit row exists even if the mutation fails later (the row says "attempted update," which is more useful than "no record").
- (-) The audit row is written in the same transaction as the mutation — so if the mutation rolls back, the audit also rolls back. Acceptable for v1.2 (compliance doesn't yet require "failed-attempts" audit; existing audit semantics for triage are also "successful actions only").

### Pattern A2: AES-GCM master-key access via a single resolver class

**What:** Only `ProviderMasterKeyResolver` decrypts the master key. All Spring AI ChatClient construction in `core.llm.gateway.springai` goes through it. The decrypted plaintext is a local variable in one method, handed to Spring AI's options builder, and goes out of scope. There is no field, cache, or static holding plaintext.

**Why:** Same crypto discipline as v1.0 BYOK. The decrypted key only exists for the duration of a single LLM request. ArchUnit gate `NoMasterKeyPlaintextHeldTest` rejects any field of type `byte[]` or `String` named like `(?i)apiKey|masterKey|providerKey|plainKey` outside `ProviderMasterKeyResolver`.

### Pattern A3: Spring Data JDBC read-side for admin views, JPA for admin writes

**What:** Catalog mutations use JPA (`LlmModelCatalogRepository extends JpaRepository<...>`). Admin views (tenant detail, queue health, spend) use Spring Data JDBC (`AdminTenantQueryRepository extends Repository<TenantDetailProjection, UUID>`). The JDBC interface only declares query methods — no `save`, no `delete`.

**Why:** Reads are cross-domain joins that would force JPA fetch-graph annotations on entities that don't otherwise need them. Writes are aggregate-internal — JPA's `@Transactional` + dirty checking is the cheaper code path.

### Pattern A4: Processing-job dispatch for sync, Redis lease for click-debounce

**What:** Admin clicks "Sync models" → controller acquires Redis `admin:catalog:sync:{provider}` lease (SET NX EX 300) → writes `processing_job(job_type=catalog_sync, payload={providerId})` → returns 202 + jobId. Worker picks up via SKIP LOCKED. Admin UI polls `/api/admin/jobs/{jobId}` for status.

**Why:** Same `processing_job` table v1.0's mail ingestion uses. No new queue infrastructure. Redis lease prevents the click-twice race; the processing_job row is the durable record.

### Pattern A5: User-facing curated catalog endpoint stays under `/api/settings/*`

**What:** `GET /api/settings/catalog` (NOT `/api/admin/catalog`) returns the curated subset. The controller calls `CuratedCatalogQueryService` in `core.admin.usecases.catalog/` — different DTO shape than admin's catalog response, no pricing or sync metadata.

**Why:** URL path determines ACL. Users hit `/api/settings/*` which is `.authenticated()` (no role). The service layer can be shared across admin and user reads because the service returns a projection, and the controller/DTO determines what fields ship to the client.

---

## Data Flow

### Admin sync-from-/models (async)

```
[Admin clicks "Sync OpenAI models"]
    ↓
[POST /api/admin/catalog/providers/{providerId}/sync-models]
    ↓ @PreAuthorize hasRole(ADMIN) passes
[AdminCatalogController.sync(providerId)]
    ↓
[CatalogSyncService.dispatch(providerId)]
    ↓ ConfirmationLeaseService.acquireOrFail("admin:catalog:sync:openai", 300s)  ← Redis SET NX EX
    ↓ ProcessingJobRepository.save(new ProcessingJob(jobType=catalog_sync, payload={providerId}, state=QUEUED))
    ↓ AdminAuditLogger.log(CATALOG_SYNC_REQUESTED, "provider", providerId, null)
    ↓ return CatalogSyncJobAcceptedResponse { jobId, providerId, acceptedAt }
[Client renders "Syncing... (job 12345)" + starts polling]

[backend/worker poll loop]
    ↓ SELECT ... FROM processing_job WHERE state='QUEUED' AND job_type='catalog_sync' FOR UPDATE SKIP LOCKED LIMIT 1
    ↓ marks state=PROCESSING, leased_until=now()+5min
[CatalogSyncJobHandler.handle(record)]
    ↓ calls CatalogSyncService.runForProvider(providerId)
[CatalogSyncService.runForProvider]
    ↓ decrypts master key for provider via ProviderMasterKeyResolver
    ↓ HTTP GET https://api.openai.com/v1/models with Bearer key
    ↓ parses { data: [{id, object, created, owned_by}, ...] }
    ↓ for each model in response:
        ↓ UPSERT llm_model_catalog (provider_id, provider_model_id, ...)
        ↓ if model already present: SET synced_at=now(); pricing/context fields NOT auto-touched
                                    (admin manually maintains pricing because /models doesn't return it)
        ↓ if model new: INSERT with sensible defaults, enabled=false (admin must explicitly enable)
    ↓ for each existing row NOT in response and NOT in the seed list:
        ↓ SET is_deprecated=true, enabled=false (don't DELETE — bindings might reference)
    ↓ marks processing_job state=COMPLETED, completed_at=now()
    ↓ writes admin_audit row (CATALOG_SYNC_RUN, "provider", providerId, {modelsAdded, modelsUpdated, modelsDeprecated})
    ↓ releases Redis lease (admin:catalog:sync:openai)
[Client poll detects state=COMPLETED]
    ↓ invalidates admin-catalog query key
    ↓ re-fetches model list — updated
```

### Settings AI tab catalog binding (user-facing)

```
[User navigates to Settings → AI tab]
    ↓
[features/assistant-settings/hooks/useCatalog.ts]
    ↓ TanStack Query useQuery({ queryKey: ['settings-catalog'], queryFn: api.GET('/api/settings/catalog') })
[GET /api/settings/catalog]
    ↓ .authenticated() passes (cookie session)
[SettingsCatalogController.get]
    ↓ TenantContext bound by TenantBindingFilter
[CuratedCatalogQueryService.getForCurrentTenant()]
    ↓ Spring Data JDBC query joining llm_provider_catalog × llm_model_catalog × llm_feature_binding
    ↓ filters: provider.enabled=true AND model.enabled=true AND model.is_deprecated=false
    ↓ groups by feature_key where user_override_allowed=true
    ↓ returns CuratedCatalogProjection { features: [{ featureKey, defaultModel, allowedModels, byokAllowed }] }
[Client renders per-feature dropdowns with allowedModels]

[User picks "Anthropic claude-3-5-sonnet" for chat]
    ↓ TanStack Query useMutation
[PUT /api/assistant/settings]
    ↓ body: { chatModel: "<model-uuid>" }     ← note: we store the catalog UUID, not the wire id
[AssistantSettingsController.update]
    ↓ TenantContext bound
[AssistantSettingsService.update]
    ↓ validate chatModel UUID exists in llm_model_catalog and is allowed for chat
    ↓ save assistant_settings row
[Next chat turn]
    ↓ ChatPromptBuilder reads tenant's assistant_settings.chat_model
    ↓ resolves UUID → provider + provider_model_id via llm_model_catalog
    ↓ resolves provider master key via ProviderMasterKeyResolver
    ↓ Spring AI request uses { model: providerModelId, apiKey: <decrypted bytes> }
```

### Admin tenant view (read-only)

```
[Admin searches "founder@example.com"]
    ↓
[GET /api/admin/tenants?q=founder@example.com]
    ↓ @PreAuthorize hasRole(ADMIN) passes
[AdminTenantController.search]
    ↓ AdminAuditLogger.log(TENANT_SEARCHED, "search", "founder@example.com", null)
[AdminTenantQueryService.search(query)]
    ↓ Spring Data JDBC: SELECT t.id, t.created_at, u.email, gc.status, ... FROM tenants t JOIN users u ON ... WHERE u.email ILIKE '%founder@example.com%' LIMIT 50
    ↓ returns List<TenantSummaryProjection>     ← no body content; no token bytes
[Client renders paginated table]

[Admin clicks a row]
    ↓
[GET /api/admin/tenants/{tenantId}]
    ↓ AdminAuditLogger.log(TENANT_VIEWED, "tenant", tenantId, null)
[AdminTenantQueryService.detail(tenantId)]
    ↓ joins tenants + users + gmail_connection + tenant_byok_credentials (status only) + 
            credit_ledger_entry aggregates + triage_audit counts + chat counts
    ↓ returns TenantDetailProjection
[Client renders tenant detail with sections: Identity, Gmail, BYOK Status, Billing, Triage Activity, Chat Activity]
```

### Admin master-key rotation

```
[Admin clicks "Rotate OpenAI master key" with new key in form]
    ↓
[POST /api/admin/master-keys/openai/rotate body: { newKey: "sk-..." }]
    ↓
[AdminMasterKeyController.rotate]
[MasterKeyRotationService.rotate(provider, newKeyPlaintext)]
    ↓ MasterKeyValidator.checkShape(provider, newKeyPlaintext)
    ↓ optional: MasterKeyTestService.testWithPlaintext(provider, newKeyPlaintext)  ← ping the provider
    ↓ inside one @Transactional:
        ↓ load existing LlmProviderMasterKeyEntity
        ↓ entity.previousEncryptedKey ← entity.encryptedKey
        ↓ entity.previousKeyVersion ← entity.keyVersion
        ↓ entity.encryptedKey ← RefreshTokenCipher.encrypt(newKeyPlaintext, aad=providerKey)
        ↓ entity.keyVersion ← entity.keyVersion + 1
        ↓ entity.rotatedByUserId ← currentAdminUser.id
        ↓ entity.rotatedAt ← now()
        ↓ AdminAuditLogger.log(MASTER_KEY_ROTATED, "master-key", providerKey, { fromVersion, toVersion })
    ↓ zero newKeyPlaintext buffer
    ↓ return MasterKeyStateResponse { provider, keyVersion, lastRotatedAt }
[Client renders "Rotated successfully — version 7"]

[Next LLM request using OpenAI]
    ↓ ProviderMasterKeyResolver loads the row, decrypts encryptedKey
    ↓ in-flight requests holding the OLD plaintext (zeroed-out by request scope) are unaffected
    ↓ next request uses new key
[Grace-window cleanup — out of v1.2 scope]
```

---

## Anti-Patterns

### Anti-Pattern A1: Hardcoded model lists in Settings UI

**What:** Frontend `apps/web/features/assistant-settings/components/ProviderModelSection.tsx` ships a static `const MODELS = { openai: ['gpt-4o', 'gpt-4o-mini'], anthropic: [...] }`.

**Why it is wrong:** Every model deprecation forces a frontend release. v1.0/v1.1 already shipped a hardcoded model list inside `features/llm/ByokForm.tsx`; v1.2 is the chance to fix that AND build a paved path for the new Settings AI tab. If the AI tab also hardcodes, we've shipped the bug a second time.

**Do this instead:** Settings AI tab pulls from `/api/settings/catalog` exclusively. BYOK form (existing `features/llm/`) is migrated in Phase 9 to the same endpoint. No model name appears in TypeScript outside `apps/web/lib/api/schema.d.ts` (which is generated from the backend DTO that returns the catalog).

### Anti-Pattern A2: Single unified `audit_log` table for triage + send + admin

**What:** "Three audit tables is too many — unify them." A single `audit_log` with `actor_type | actor_id | target_type | target_id | meta_json` covers everything.

**Why it is wrong:** Different retention (30-day rolling vs. indefinite). Different actors (system vs. user vs. admin). Different query patterns (per-tenant/day vs. per-chat vs. global filter). Different privacy invariants (triage audit must never carry body content; admin audit must capture before/after diffs that ARE meta-only by construction). Unifying forces every query into a `WHERE actor_type = 'admin'` discriminator and every retention policy into a per-row check.

**Do this instead:** Three tables, three retention policies, three privacy gates. `admin_audit` is its own thing.

### Anti-Pattern A3: Admin RBAC via a custom `@Admin` annotation enforced only by ArchUnit

**What:** "Spring Security feels heavy — let's annotate admin controllers with our own `@Admin`, write an ArchUnit rule that `@Admin` methods must be inside `/api/admin/**` controllers, and call it done."

**Why it is wrong:** ArchUnit runs at build time. A runtime path that bypasses `@Admin` (a new controller someone forgets to annotate, a programmatic `RequestMappingHandlerMapping` registration, a Spring Cloud Function endpoint) doesn't get checked. Spring Security's `@PreAuthorize` runs at every request — including bypass paths. The cost of one extra annotation per controller is trivial.

**Do this instead:** Class-level `@PreAuthorize("hasRole('ADMIN')")` + path matcher `.requestMatchers("/api/admin/**").hasRole("ADMIN")` + ArchUnit `AdminAccessOnlyOnAdminControllersTest` that verifies the annotation is present. Three independent gates.

### Anti-Pattern A4: Synchronous sync-from-/models holding the admin HTTP request

**What:** "It's just one HTTP call to OpenAI — let the admin wait." `AdminCatalogController.sync` calls `CatalogSyncService.runForProvider` directly and returns the new catalog in the response.

**Why it is wrong:** /models responses for OpenAI can take 2–15s; Anthropic doesn't have a /models endpoint (we'd silently no-op or 404 — both bad UX); on slow networks 30s+ is realistic. Tomcat virtual threads make holding the connection cheap, but the user's spinner is the actual UX concern. Worse, a half-completed sync that the admin browser-closes leaves the catalog in an undefined state (which models were upserted before the connection dropped?).

**Do this instead:** Dispatch `processing_job`, return 202 + jobId, poll for status. Same pattern v1.0 already uses for mail ingestion. The worker is the durable runner.

### Anti-Pattern A5: Storing master-key plaintext in environment variables / `application.yml` "for v1.2 transition"

**What:** "We already have `spring.ai.openai.api-key=${OPENAI_API_KEY}` in `application.yml`. Let's keep that as the master key source and skip the new `llm_provider_master_key` table for v1.2 — admin UI just edits a config file."

**Why it is wrong:** (1) Defeats the entire point of admin master-key management (rotation, audit, test, multi-admin governance). (2) `application.yml` master keys can't be rotated without a redeploy. (3) Spreads the secret across deployment env vars, the deployer's shell history, and the VPS process listing. (4) Forces the admin UI to be either read-only (useless) or to ship a YAML editor (worse — admin shouldn't have shell-equivalent power).

**Do this instead:** Migrate platform keys to `llm_provider_master_key` in changelog 056. `application.yml` keeps the `${OPENAI_API_KEY:}` style env vars ONLY for dev-mode bootstrap (the new resolver falls back to env var if no row exists yet — so a fresh checkout still boots). Production keys come from the table, set via the admin UI on first run.

### Anti-Pattern A6: One Spring Security filter chain per privilege level

**What:** "Admin is a different privilege; let's add a second `SecurityFilterChain` for `/api/admin/**` at `@Order(2)`, before the existing `@Order(3)` user chain."

**Why it is wrong:** Spring Security filter chains route the request entirely — adding a second chain duplicates the OAuth2 login config, CSRF config, exception handling, and `TenantBindingFilter` placement. Two chains drift over time; a CSRF token fix in one is forgotten in the other.

**Do this instead:** ONE filter chain, ONE filter ordering, role-checks via `authorizeHttpRequests` matchers AND `@PreAuthorize`. The existing chain at `@Order(3)` is extended with `.requestMatchers("/api/admin/**").hasRole("ADMIN")` plus `.requestMatchers("/api/settings/catalog").authenticated()` (the user-facing curated endpoint stays under the existing "anyRequest authenticated" default but listing it explicitly is documentation).

### Anti-Pattern A7: Frontend admin routes nested inside `(app)`

**What:** `apps/web/app/(protected)/(app)/admin/page.tsx` — admin pages share the user app's layout, persistent chrome, brand palette.

**Why it is wrong:** Forces the user app's persistent chrome (pause toggle, credit balance, connection health) to render for admins who don't need it. Forces a conditional `if (role==='ADMIN') { showAdminLink }` in the user nav. Forces every admin page to "look like" the user app, which fights the workshop-console UX admin pages want. Most importantly, it violates the project's flat-folder rule by nesting a single-purpose `admin/` segment inside the user app group.

**Do this instead:** Sibling route group `(admin)`. Separate `layout.tsx` with admin shell. Server-side role check at the layout level.

---

## Integration Points

### Cross-domain dependency map (admin module → existing modules)

| Caller (in `admin.usecases.*`) | Callee | Purpose | Module dep edge |
|---|---|---|---|
| `ProviderCatalogService` / `ModelCatalogService` / `FeatureBindingService` | own persistence repos | catalog CRUD | none |
| `CatalogSyncService` | `ProviderMasterKeyResolver` (in `core.llm.gateway.springai`) | decrypt key to call provider /models | `admin → llm` |
| `MasterKeyService` / `MasterKeyRotationService` | `RefreshTokenCipher` (in `core.gmail.persistence.crypto`) | AES-GCM enc/dec | `admin → gmail.persistence.crypto` |
| `MasterKeyTestService` | `LlmGateway` (existing) | mini ping prompt | `admin → llm` |
| `AdminTenantQueryService` | own JDBC read repos (cross-table reads via SQL, NOT cross-module service calls) | tenant detail | none at JPA layer; SQL touches `tenants`, `users`, `gmail_connection`, `credit_ledger_entry`, `triage_audit`, `chat` tables read-only |
| `WorkerQueueHealthService` | own JDBC read repo over `processing_job` | queue health | none at JPA layer |
| `GlobalSpendQueryService` | own JDBC read repo joining `credit_ledger_entry × llm_model_catalog × tenants` | spend rollup | `admin → billing` (read), `admin → analytics` (for time bucketing helpers) |
| `AdminAuditLogger` | Spring Security `SecurityContextHolder` + `RequestContextHolder` | capture actor + request meta | none (Spring infra) |
| `AdminAuditQueryService` | own repo | audit search | none |
| `CuratedCatalogQueryService` | own repos | user-facing catalog read | none |
| `AssistantSettingsService` (existing v1.1) | `CuratedCatalogQueryService` (NEW v1.2) | resolve model UUID → wire id at request time | `chat → admin` (read-only projection only) |

**Final `admin/package-info.java` `allowedDependencies` list:**

```java
@ApplicationModule(
        displayName = "Admin Console",
        allowedDependencies = {
            "tenant",
            "account",
            "llm",
            "billing",
            "analytics",
            "gmail.persistence.crypto",
            "shared.persistence",
            "shared.lang",
            "shared.privacy"
        })
package com.zeromail.core.admin;
```

**Why `admin` does NOT depend on `chat` or `triage`:** admin only READS those modules' tables, through Spring Data JDBC queries. Spring Modulith's dependency rules apply to Java-level package imports — a Spring Data JDBC repository in `admin.persistence.jdbc` that selects from `chat_message` doesn't import any `com.zeromail.core.chat.*` class, so no Modulith edge is needed. (This is the same way `analytics` reads `triage_audit` rows without depending on the `triage` module.)

**Why `chat → admin` (new edge):** `AssistantSettingsService` needs to validate that the user's `chat_model` setting is a UUID that exists in `llm_model_catalog`. That validation call goes through `CuratedCatalogQueryService` in `admin/`. `chat`'s `allowedDependencies` adds `"admin"`.

### v1.2 changes to EXISTING module `package-info.java` files

| Module | Change | Why |
|---|---|---|
| `llm` | ADD `"admin"` to `allowedDependencies`. | New `ProviderMasterKeyResolver` reads `LlmProviderMasterKeyRepository` from `admin.persistence`. |
| `chat` | ADD `"admin"` to `allowedDependencies`. | `AssistantSettingsService` validates model UUID against the catalog via `CuratedCatalogQueryService`. |
| `account` | NO change to `allowedDependencies`. | Adding `role` column is an internal schema change; no new outgoing deps. |
| `billing`, `triage`, `analytics`, `gmail`, `rules`, `draft` | NO change. | Admin reads their tables via JDBC SQL; no Java-package imports. |

### External service integrations (no new external services in v1.2)

| Service | Integration Pattern | Notes |
|---|---|---|
| Provider /models endpoints (OpenAI, Google GenAI, DeepSeek) | New: HTTPS GET via standard Java `HttpClient` from inside `CatalogSyncService` | Anthropic has NO /models endpoint → manual catalog only for Anthropic, surface this in admin UI. Sync timeout = 30s, retried by `processing_job` standard retry. |
| Spring AI 2.0.0-M6 LLM providers | Existing — `LlmGateway` adds master-key path | New `ProviderMasterKeyResolver` replaces `application.yml`-sourced platform keys with table-sourced keys. |
| PostgreSQL 17 | Existing — Liquibase YAML + JPA writes + Spring Data JDBC reads | Nine new changelogs (048–056). |
| Redis 7 | Existing — Spring Session + chat lease | Two new key namespaces: `admin:catalog:sync:{provider}`, `admin:masterkey:test:{provider}`. |
| Spring Session Redis | Unchanged | Admin uses the same session cookie. |
| Spring Modulith event spine | Unchanged | No new events in v1.2 (admin actions are audit-row-only). |

### Internal boundaries (cross-module / cross-process)

| Boundary | Communication | Notes |
|---|---|---|
| `backend/api` admin controllers ↔ `backend/core` admin services | Direct in-process service injection | Same pattern as v1.0/v1.1. |
| `admin.usecases.catalog` ↔ `llm.gateway.springai.ProviderMasterKeyResolver` | Direct call | `admin → llm` dep edge. |
| `chat.usecases.AssistantSettingsService` ↔ `admin.usecases.catalog.CuratedCatalogQueryService` | Direct call | `chat → admin` dep edge (NEW). |
| `backend/api` ↔ `backend/worker` (catalog sync) | Postgres `processing_job` table with SKIP LOCKED | Existing pattern (mail ingestion uses it). |
| Frontend `(admin)` shell ↔ `/api/admin/*` | Typed OpenAPI client (`api.GET` / `api.POST`) | Same generated-types pipeline. No raw fetch except SSE (not used by admin in v1.2). |
| Frontend `(app)/settings/page.tsx` ↔ `/api/settings/catalog` | Typed OpenAPI client | Curated catalog endpoint emits its own DTO schema. |

---

## ArchUnit Gate Strategy (v1.2)

Six new ArchUnit tests. All complement (not replace) the existing v1.0 + v1.1 gates.

1. **`AdminAccessOnlyOnAdminControllersTest`** — every class under `com.zeromail.api.controllers.admin..` MUST be class-annotated with `@PreAuthorize` (any value) OR have every public method `@PreAuthorize`-annotated. Catches "forgot to gate an admin endpoint."

2. **`AdminMutationsMustAuditTest`** — every method annotated with `@PostMapping`, `@PutMapping`, `@PatchMapping`, or `@DeleteMapping` in `controllers.admin..` MUST transitively call `AdminAuditLogger.log(...)`. Implemented via ArchUnit's `MethodCallTarget` reachability check.

3. **`AdminProjectionPrivacyTest`** — every record class in `core.admin.projection..` MUST NOT have a field whose name matches `(?i)body|content|prompt|completion|emailBody|messageBody|rawText`. Catches accidental body content leaking into admin views.

4. **`AdminTenantViewIsReadOnlyTest`** — every interface in `core.admin.persistence.jdbc..` whose name contains "Query" MUST extend `org.springframework.data.repository.Repository<...>` (NOT `CrudRepository` / `JpaRepository`) AND MUST NOT declare methods starting with `save`, `delete`, `update`, `insert`, `merge`.

5. **`NoMasterKeyPlaintextHeldTest`** — outside `core.llm.gateway.springai.ProviderMasterKeyResolver`, no class may have a field of type `byte[]` or `java.lang.String` whose name matches `(?i)apiKey|masterKey|providerKey|plainKey|decryptedKey`. The resolver itself MUST NOT declare such a field at class scope (only as a method-local).

6. **`AdminModuleBoundaryTest`** — verifies the `admin/package-info.java` `allowedDependencies` list matches the documented set in this file. Implemented via Spring Modulith's `ApplicationModules.verify()` already, but a specific test asserts the expected list to catch silent drift.

**Existing gates that remain unchanged and continue to pass in v1.2:**
- `NoGmailSendAllowedTest` + `AssistantSendCallSiteAllowlistTest` (v1.1 send-carve-out)
- `ChatPersistencePrivacyTest` (v1.1 chat body-ban)
- `DomainPurityArchTest` (v1.0 framework-free domain)
- `NoThreadLocalInRequestPathTest` (v1.0 Scoped Values)
- `SensitiveLogScrubArchTest` (v1.0 `@Sensitive` log gate)
- All Modulith `ApplicationModules.verify()` boundary tests

---

## Schema Sketches (concise — full YAML at execution time)

| Changelog | Table / Change | Purpose |
|---|---|---|
| `048-add-user-role.yaml` | ALTER `users` ADD `role varchar(16) NOT NULL DEFAULT 'USER'` + CHECK constraint `role IN ('USER','ADMIN','SUPPORT')` | RBAC source of truth. |
| `049-llm-provider-catalog.yaml` | `llm_provider_catalog` (4 seed rows in 054) | Provider master list. |
| `050-llm-model-catalog.yaml` | `llm_model_catalog` + unique `(provider_id, provider_model_id)` | Model master list. |
| `051-llm-feature-binding.yaml` | `llm_feature_binding` keyed on `feature_key` (chat / triage / draft / rule-compile / semantic-intent) | Default model per feature + override flags. |
| `052-llm-provider-master-key.yaml` | `llm_provider_master_key` (encrypted bytea, key_version, previous_*, rotated_by_user_id) | AES-GCM master keys. |
| `053-admin-audit.yaml` | `admin_audit` (actor_user_id, actor_email_snapshot, action_type, target_type, target_id, diff JSONB, client_ip, occurred_at) + indexes on `(actor_user_id, occurred_at DESC)` and `(action_type, occurred_at DESC)` | Append-only admin trail. |
| `054-catalog-seed.yaml` | Seed: 4 providers + ~10 known-good models + 5 default feature bindings | Boot in a known-good state without /models sync. |
| `055-catalog-sync-job.yaml` | DML: insert into `processing_job_kind` (or similar lookup) if such a table exists, otherwise no-op | Registers `catalog_sync` as a valid job type. |
| `056-admin-master-key-backfill.yaml` | Conditional backfill: if `application.yml` env vars are set, the boot path writes them into `llm_provider_master_key` on first startup (Spring `ApplicationRunner`, NOT Liquibase SQL — Liquibase can't read env vars cleanly) | Dev → prod transition. |

**`db.changelog-master.yaml` append:**

```yaml
  - include: { file: changes/048-add-user-role.yaml, relativeToChangelogFile: true }
  - include: { file: changes/049-llm-provider-catalog.yaml, relativeToChangelogFile: true }
  - include: { file: changes/050-llm-model-catalog.yaml, relativeToChangelogFile: true }
  - include: { file: changes/051-llm-feature-binding.yaml, relativeToChangelogFile: true }
  - include: { file: changes/052-llm-provider-master-key.yaml, relativeToChangelogFile: true }
  - include: { file: changes/053-admin-audit.yaml, relativeToChangelogFile: true }
  - include: { file: changes/054-catalog-seed.yaml, relativeToChangelogFile: true }
  - include: { file: changes/055-catalog-sync-job.yaml, relativeToChangelogFile: true }
```

(Note: 056 backfill is bootstrap code, not a Liquibase changelog — runs once in `ApplicationRunner`.)

---

## Suggested Build Order (Phase 8 → Phase 9)

### Phase 8 — Admin Console Foundation (`SEED-011` + `OPS-02`)

**Goal:** Production-shaped admin module with RBAC + catalog + master keys + audit + tenant view + queue health + spend dashboard. NO user-facing Settings tab work yet.

**Sub-phase A — Backend foundation (no UI yet)**

1. Changelog 048 — `users.role` + CHECK constraint.
2. `AdminRole` enum (`USER` | `ADMIN` | `SUPPORT`) implementing `IdentifiedEnum`.
3. `UserEntity.role` field + repository methods.
4. `OAuthProvisioningService` reads `AdminEmailAllowlistProperties` and assigns role on provisioning.
5. `CurrentUserProjection.role` + `/api/me` response includes role.
6. `SecurityConfig` extension: `.requestMatchers("/api/admin/**").hasRole("ADMIN")`.
7. `@EnableMethodSecurity` (if not already on) so `@PreAuthorize` works at method level.

**Sub-phase B — Catalog backend**

8. Changelogs 049–051 (provider, model, feature-binding tables) + 054 (seed).
9. `core.admin` Modulith module skeleton with `package-info.java`.
10. `admin.persistence.*` entities + JPA repositories.
11. `admin.usecases.catalog.*` services (`ProviderCatalogService`, `ModelCatalogService`, `FeatureBindingService`, `CuratedCatalogQueryService`).
12. `admin.usecases.audit.AdminAuditLogger` + changelog 053 (admin_audit table) + `AdminAuditEntity`.
13. `controllers/admin/AdminCatalogController` + `AdminFeatureBindingController` + DTOs.
14. ArchUnit tests: `AdminAccessOnlyOnAdminControllersTest`, `AdminMutationsMustAuditTest`, `AdminModuleBoundaryTest`.
15. Slice tests: catalog CRUD + audit row written on each mutation.

**Sub-phase C — Master keys backend**

16. Changelog 052 + 056 backfill `ApplicationRunner`.
17. `LlmProviderMasterKeyEntity` (`@Sensitive` on encrypted_key fields) + repository.
18. `admin.usecases.masterkey.*` (`MasterKeyService`, `MasterKeyRotationService`, `MasterKeyTestService`).
19. `core.llm.gateway.springai.ProviderMasterKeyResolver` — the one decryption point.
20. `LlmGatewayImpl` switches platform-key source from `application.yml` to `ProviderMasterKeyResolver`.
21. `controllers/admin/AdminMasterKeyController` + DTOs.
22. ArchUnit: `NoMasterKeyPlaintextHeldTest`.
23. Slice tests: rotation + test-connection + master-key revoke + audit.

**Sub-phase D — Catalog sync (async)**

24. Changelog 055 + `CatalogSyncJobHandler` in `backend/worker`.
25. `admin.usecases.catalog.CatalogSyncService` — the actual sync loop, idempotent UPSERTs.
26. Admin controller endpoint `POST /api/admin/catalog/providers/{id}/sync-models` → 202 + jobId.
27. `GET /api/admin/jobs/{jobId}` status endpoint (or extend existing job-status endpoint if one exists).
28. Slice tests: sync idempotency, error recovery, Anthropic-no-/models graceful path.

**Sub-phase E — Tenant view + Queue health + Spend (read-side)**

29. `admin.persistence.jdbc.*` Spring Data JDBC interfaces.
30. `admin.usecases.tenantview.AdminTenantQueryService` + `admin.usecases.queue.WorkerQueueHealthService` + `admin.usecases.spend.GlobalSpendQueryService`.
31. Controllers: `AdminTenantController`, `AdminQueueController`, `AdminSpendController`, `AdminAuditController`.
32. ArchUnit: `AdminProjectionPrivacyTest`, `AdminTenantViewIsReadOnlyTest`.
33. Slice tests: pagination, privacy gate, no body content in any projection.

**Sub-phase F — Admin frontend**

34. New route group `apps/web/app/(protected)/(admin)/`.
35. `middleware.ts` admin role check.
36. `apps/web/features/admin-shell/` (layout pieces, nav).
37. `apps/web/features/admin-catalog/`, `admin-master-keys/`, `admin-tenants/`, `admin-queue/`, `admin-audit/`, `admin-spend/`.
38. Regenerate OpenAPI client (`pnpm --filter web generate:api`).
39. Playwright E2E: admin role redirect, catalog edit + audit appears, master-key test, queue dashboard renders.

**Phase 8 success criteria:**
- A non-admin user cannot reach `/admin/*` (frontend redirect + backend 403).
- An admin can edit a provider/model/feature-binding and the change is audited.
- An admin can rotate a master key and the next LLM request uses the new key.
- The user Settings page still works exactly as before (no v1.1 functionality changed).
- All ArchUnit gates pass.

### Phase 9 — Settings UI on Curated Catalog (carries forward 19 deferred v1.1 reqs)

**Goal:** Ship the 4-tab Settings UI (Personalization, Behavior, Safety Net, AI Provider/Model) consuming the admin-curated catalog. NO new admin work in this phase.

**Sub-phase A — Backend: user-facing catalog + settings**

40. `SettingsCatalogController` (`GET /api/settings/catalog`) + `CuratedCatalogResponse` DTO.
41. `AssistantSettingsService.update` validates `chatModel` / `triageModel` / `draftModel` UUIDs against `llm_feature_binding` allowed models.
42. `chat → admin` dep edge added to `chat/package-info.java`.
43. Regenerate OpenAPI client.

**Sub-phase B — Frontend: 4 tabs**

44. `apps/web/features/assistant-settings/` skeleton.
45. AI Provider/Model tab (SET-AI-01..04): per-feature dropdowns from `/api/settings/catalog`; BYOK form migrated to consume curated catalog (replaces hardcoded model lists); default-vs-BYOK toggle; test-connection button.
46. Personalization tab (SET-VOICE-01..06): writing style, personal instructions, signature, knowledge base CRUD, tone preset, output language VI/EN.
47. Behavior tab (SET-BEHV-01..05): auto-draft master toggle, confidence threshold, daily digest, sensitive-data protection, shadow-mode surface.
48. Safety Net tab (SET-SAFE-01..04): VIP add/remove, paste-import, per-entry mode, audit log VIP-blocked badge.
49. Vietnamese + English strings (i18n parity gate).
50. Playwright E2E: per-feature model swap → next chat turn uses new model; BYOK migration from hardcoded list to curated catalog; tab switching is query-param-driven.

**Sub-phase C — Migration & cleanup**

51. Remove hardcoded model lists from `features/llm/ByokForm.tsx` (the existing v1.0 list) — all model names now flow from the catalog.
52. Regenerate OpenAPI client one final time.
53. Documentation update: admin runbook for first-time master-key setup, catalog seeding, role grant.

**Phase 9 success criteria:**
- Settings AI tab shows only models the admin has enabled for that feature.
- Disabling a model in admin → user Settings dropdown loses that option on next page load (no caching beyond TanStack's normal stale-time).
- All 19 deferred v1.1 reqs (SET-AI-01..04, SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04) validated.
- No model name string appears in any `.ts`/`.tsx` outside generated schema.

### Dependency rationale (build order)

- **Phase 8.A (RBAC) blocks all other admin work** — without role gating, every admin endpoint is publicly accessible from staging.
- **Phase 8.B (catalog backend) blocks 8.C, 8.D, 8.E, and Phase 9.** Master keys reference the provider catalog. Sync writes into the model catalog. Spend joins on the model catalog. Settings reads the catalog.
- **Phase 8.C (master keys) blocks 8.D** — sync needs a working key to call /models.
- **Phase 8.D (sync) and 8.E (read-side) are independent of each other** — can run in parallel.
- **Phase 8.F (frontend) blocks Phase 9.A** — Phase 9.A piggybacks on the same OpenAPI codegen pipeline; you want the admin DTOs stable before generating user-facing DTOs against the same schema.
- **Phase 9 cannot start until Phase 8 ships** — the Settings AI tab requires a curated catalog to read.

---

## Confidence & Validation

| Area | Confidence | Why |
|---|---|---|
| `admin` as new Modulith module | HIGH | Identical pattern to v1.1's `chat` module; verified `allowedDependencies` shape against existing module package-info files. |
| RBAC via `users.role` + `@PreAuthorize` | HIGH | Spring Security canonical pattern; verified existing `SecurityConfig` is single-chain and `@EnableMethodSecurity` is the standard enabler. |
| Catalog 3-table normalization | HIGH | Standard SQL design; FK + unique constraints prevent the failure modes a JSONB blob would have. |
| AES-GCM master keys via `RefreshTokenCipher` | HIGH | Reuses verified v1.0 crypto class (`gmail.persistence.crypto`); same pattern that protects BYOK keys today. |
| Async sync via `processing_job` | HIGH | Existing v1.0 queue infrastructure; one new job_type, one new handler. |
| Three audit tables (triage/send/admin) | MEDIUM-HIGH | Different actors/retention/queries justify separation; unified table is feasible but trades clarity for storage micro-optimization that doesn't matter at v1.2 scale. |
| Spring Data JDBC for admin reads | HIGH | Existing pattern (analytics module already does this); ArchUnit makes the read-only contract enforceable. |
| `(admin)` route group sibling of `(app)` | HIGH | Next.js 16 standard pattern; matches user's locked flat-folder rule. |
| `/api/settings/catalog` as separate user-facing endpoint | HIGH | URL-prefix → ACL is the cleanest privilege boundary. |
| Provider /models endpoint shapes | MEDIUM | OpenAI and DeepSeek expose /v1/models; Google GenAI exposes /v1beta/models. Anthropic does NOT — verified by Anthropic API docs (no /models endpoint at Messages API v1). Catalog must accommodate manual-only providers. |
| Role grant via email allowlist + admin UI | MEDIUM-HIGH | Bootstrap-by-config + runtime-grant pattern is standard; the runtime grant endpoint `/api/admin/grant-admin` is a small surface and audit-logged. |

---

## Files Read / Sources

**Direct codebase read (HIGH confidence):**
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/llm/package-info.java` — `allowedDependencies` shape and rationale comment.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/llm/domain/BYOKProvider.java` — provider enum used to align `llm_provider_catalog.provider_key`.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/account/persistence/UserEntity.java` — entity shape to extend with `role`.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — current single-chain `@Order(3)` config; `.requestMatchers("/api/admin/**")` extension point.
- `D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/resources/db/changelog/changes/018-tenant-byok-credentials.yaml` — Liquibase pattern for encrypted bytea + key_version columns (master-key schema mirrors this).
- Liquibase changelog index 001–047 (latest: 047-chat-forbidden-html-expanded.yaml) — next free changelog id starts at 048.
- Package-info inventory of `core/*/package-info.java` (14 existing modules including v1.1's `chat`) — confirms the new-module pattern.
- `D:/study-materials-summer-2026/EXE202/zero-mail/CONVENTIONS.md` §§1–8 — thin-controllers, package layout, records-vs-classes, enums, privacy logging, events-vs-direct-calls, shadcn-first, feature-folder pattern.
- `D:/study-materials-summer-2026/EXE202/zero-mail/CLAUDE.md` — locked constraints (Java 25, Spring Boot 4, no Lombok, no WebFlux, no Kafka, AES-GCM, no JWT, no GCP starters).

**Sibling research (already locked):**
- `.planning/research/ARCHITECTURE.md` (v1.0 baseline + v1.1 delta lines 1–1229) — Modulith pattern, `core.chat` precedent, ArchUnit gates, send carve-out, JSONB privacy gate.
- `.planning/research/STACK.md` — locked stack (Postgres 17, Redis 7, Spring AI M6 confinement, Spring Data JDBC for reads).
- `.planning/PROJECT.md` lines 19–40 — v1.2 milestone scope, SEED-011 activation, deferred v1.1 reqs to carry into Phase 9.
- `.planning/STATE.md` — current position (Phase 8 not started; defining requirements).
- `.planning/MILESTONES.md` — v1.2 sequencing decision: Phase 1 (admin) before Settings (now Phase 8 → Phase 9 per ROADMAP).

**Reference repo (inbox-zero) — NOT a code source:**
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/admin/` — UX reference for admin nav structure (mapped to Next.js 16 `(admin)` route group pattern).

---

*Architecture research for: Zero Mail v1.2 — admin console foundation + Settings UI on curated catalog, integrating into existing Spring Boot 4 + Spring Modulith + Next.js 16 monorepo*
*Researched: 2026-05-19 by gsd-researcher (direct codebase read + sibling research integration + verified Modulith/ArchUnit patterns)*
