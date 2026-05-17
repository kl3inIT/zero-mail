# Stack Research — Zero Mail v1.1 (Chat Assistant + Settings Page)

**Domain:** Conversational AI assistant on top of existing Spring AI 2.0.0-M6 / Next.js 16 SaaS
**Researched:** 2026-05-17
**Overall confidence:** HIGH on frontend additions (verified npm + Context7 + reference repo Inbox Zero). HIGH on Spring MVC SSE patterns. MEDIUM-HIGH on Vercel UI Message Stream wire format (verified against ai@6 source + protocol docs). MEDIUM on Spring AI M6 user-controlled tool execution path (verified against 2.0-SNAPSHOT reference).

> **Scope of this document.** This is the **v1.1 delta**. The v1.0 baseline (Java 25 / Spring Boot 4.0.6 / Spring AI 2.0.0-M6 / PostgreSQL 17 / Redis 7 / Next.js 16.2 / React 19.2 / Tailwind 4 / shadcn/ui / TanStack Query / openapi-fetch / Liquibase 5 / virtual threads) is locked and validated — see git history of this file before 2026-05-17 for the full v1.0 stack tables. This document only catalogs what v1.1 **adds** or **changes**.

> **What v1.1 does not add to the backend stack:** no new Spring Boot starters, no new database, no new queue, no new auth flow, no new observability tool. The entire backend addition is "a streaming SSE controller built on existing Spring MVC + existing Spring AI 2.0.0-M6 + existing virtual threads." Backend changes are *architectural*, not *dependency*. See the new frontend dependencies below.

---

## TL;DR — Prescriptive v1.1 Additions

**Frontend (`apps/web/package.json`) — three new runtime dependencies:**

```bash
# from apps/web
pnpm add ai@^6.0.184 @ai-sdk/react@^3.0.186 streamdown@^2.5.0
```

**Frontend (`apps/web/components/ai-elements/**`) — copy-paste primitive registry, not an npm dep:**

```bash
# from apps/web — installs AI Elements components into apps/web/components/ai-elements/
pnpm dlx ai-elements@latest add conversation message prompt-input response tool reasoning loader suggestion confirmation
```

**Backend — zero new dependencies.** Use existing Spring MVC `SseEmitter` (or `Flux<ServerSentEvent>` since `spring-boot-starter-webflux` is **not** a dep — only Reactor Core is needed and is already on the classpath via Spring AI's streaming API). The Vercel "UI Message Stream Protocol" is plain JSON-over-SSE; emit it by hand from a controller that consumes the existing `ChatModel.stream(Prompt)` `Flux<ChatResponse>`.

**Backend Spring AI mode change (no new dep) — user-controlled tool execution:**

```yaml
# Tool execution must be user-controlled so the chat preview/confirm UX can intercept
# Configure via ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)
# per-request inside the LLM gateway, not as a global property.
```

---

## What v1.1 Adds — Frontend Dependencies

### Core runtime packages (npm/pnpm)

| Package | Version | Purpose | Why |
|---|---|---|---|
| `ai` | **^6.0.184** (latest GA on 2026-05-17) | Vercel AI SDK core — `UIMessage` type, `DefaultChatTransport`, `ToolUIPart`, message-stream helpers used by `useChat` internally | Required peer of `@ai-sdk/react@3.x`. The npm `latest` dist-tag now points to v6; v5 is still maintained under the `ai-v5` tag (`5.0.188`). Reference repo `inbox-zero` uses `ai@6.0.168` — v6 is the production line as of May 2026. **HIGH** (verified `npm view ai`). |
| `@ai-sdk/react` | **^3.0.186** | React hooks (`useChat`, `experimental_useObject`) | Pairs with `ai@^6`. Internally depends on `ai@6.0.184`, `swr@^2.2.5`, `throttleit@2.1.0`. Peer dep: `react: ^18 \|\| ~19.0.1 \|\| ~19.1.2 \|\| ^19.2.1` — **compatible** with our `react@19.2.6`. **HIGH** (verified `npm view @ai-sdk/react@3.0.186 peerDependencies`). |
| `streamdown` | **^2.5.0** | Markdown renderer hardened against partial/incomplete tokens during streaming (handles half-closed code fences, unfinished tables, malformed links) | Drop-in replacement for `react-markdown` specifically built for AI streams. Reference repo `inbox-zero` ships it. AI Elements' `MessageResponse` component **uses Streamdown internally** — without `streamdown` installed, the AI Elements `response` / `message` components fall back to plain text. Peer dep: `react: ^18 \|\| ^19` — fine. **HIGH** (verified `npm view streamdown` + Context7 `/vercel/streamdown`). |

**Total runtime cost:** three top-level deps. `swr` and `throttleit` come in transitively via `@ai-sdk/react` (already not in the project — net 2 transitive additions). `@opentelemetry/api` comes in transitively via `ai` (lightweight, already commonly bundled).

### Component primitives (copy-paste, not a runtime dep)

**`ai-elements` is a CLI registry, not a runtime package.** It is a shadcn-style component generator that **writes source code into `apps/web/components/ai-elements/`** and that source then becomes part of the project (lint-ignored alongside `apps/web/components/ui/**`, same convention as raw shadcn primitives). There is no `ai-elements` npm dependency to track in `package.json`.

```bash
# Install all components (recommended for v1.1 — cheap, all components are small)
pnpm dlx ai-elements@latest

# OR install components piecewise (production discipline)
pnpm dlx ai-elements@latest add conversation
pnpm dlx ai-elements@latest add message
pnpm dlx ai-elements@latest add prompt-input
pnpm dlx ai-elements@latest add response       # uses streamdown internally
pnpm dlx ai-elements@latest add tool           # tool-call card with state lifecycle
pnpm dlx ai-elements@latest add reasoning      # collapsible "thinking" block
pnpm dlx ai-elements@latest add loader         # streaming spinner
pnpm dlx ai-elements@latest add suggestion     # quick-action chips
pnpm dlx ai-elements@latest add confirmation   # tool approval dialog (use for sendEmail/replyEmail/forwardEmail)
```

**Prerequisites that are already satisfied in `apps/web`:**
- ✓ Node.js 18+ (we run Node 22+)
- ✓ React 19 (`react@19.2.6`)
- ✓ Next.js 14+ with App Router (`next@16.2.6`)
- ✓ Tailwind CSS 4 (`tailwindcss@^4` + `@tailwindcss/postcss`)
- ✓ shadcn/ui initialized (`shadcn@^4.7.0` + `components/ui/**` populated)
- ✓ CSS Variables mode (shadcn default; required by AI Elements)
- ✓ `sonner` (`^2.0.7`) — required by AI Elements `Confirmation`/`Tool` toast paths

**AI Elements components used in v1.1 (verified each via Context7 `/vercel/ai-elements`):**

| Component | Source folder | Purpose in Zero Mail v1.1 |
|---|---|---|
| `Conversation` + `ConversationContent` + `ConversationScrollButton` + `ConversationEmptyState` + `ConversationDownload` | `components/ai-elements/conversation.tsx` | Scrollable chat container with stick-to-bottom autoscroll. Wraps the message list on `/chat`. |
| `Message` + `MessageContent` + `MessageResponse` | `components/ai-elements/message.tsx` | Per-turn message bubble. `MessageResponse` renders streamed assistant text through Streamdown. |
| `PromptInput` + `PromptInputBody` + `PromptInputTextarea` + `PromptInputSubmit` + `PromptInputFooter` + `PromptInputTools` + `PromptInputSelect` (model picker) | `components/ai-elements/prompt-input.tsx` | The input bar at the bottom. We will use the model-picker slot to surface per-feature model choice if v1.1 settings expose it in chat. |
| `Tool` + `ToolHeader` + `ToolContent` + `ToolInput` + `ToolOutput` | `components/ai-elements/tool.tsx` | Renders tool-invocation cards with `input-streaming` → `input-available` → `output-available` / `output-error` state. Used to visualize every tool call (`listRules`, `createRule`, `getEmail`, etc.). |
| `Reasoning` + `ReasoningTrigger` + `ReasoningContent` | `components/ai-elements/reasoning.tsx` | Collapsible "AI thought process" block. Useful for o1-style and Claude 3.7 thinking provider responses. Optional in v1.1. |
| `Loader` | `components/ai-elements/loader.tsx` | Simple spinner shown while `status === "submitted"`. |
| `Suggestion` + `Suggestions` | `components/ai-elements/suggestion.tsx` | Quick-action chips (e.g., "Create a rule for newsletters", "Show me top senders this week"). Optional. |
| `Confirmation` + `ConfirmationRequest` + `ConfirmationActions` + `ConfirmationAction` | `components/ai-elements/confirmation.tsx` | **Critical for v1.1 send safety.** Bound to `addToolApprovalResponse` from `useChat`. Renders the "AI wants to send this draft to X — Send / Cancel / Edit" dialog. Required for `sendEmail`/`replyEmail`/`forwardEmail` tools. |

### Tool-call rendering with confirm/cancel (the v1.1 send-safety story)

There is **no separate tool-call rendering library** to add. The combination is:

1. **Backend** marks the tool definition with `requireApproval: true` (Vercel AI SDK pattern). In our case, because we are not running the Vercel `ai` server-side helpers, we instead use **Spring AI's `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)` + custom tool-call serialization in the SSE stream** to emit the "approval-requested" state to the client.
2. **Frontend** `useChat({ ..., sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithApprovalResponses })` triggers a follow-up request once the user approves.
3. **UI** uses AI Elements `<Confirmation>` (or roll-your-own with shadcn `<AlertDialog>`) and calls `addToolApprovalResponse({ id, approved: true|false })` on click.
4. **State machine** on the message part: `input-streaming` → `input-available` → `approval-requested` → `output-available` | `output-denied`.

> **The "tool-call card with Confirm/Cancel" you asked about is `<Confirmation>` from AI Elements, wired to `addToolApprovalResponse` from `useChat`.** Both ship together in the AI SDK v6 / AI Elements 1.9 line.

---

## What v1.1 Adds — Backend

**Zero new Maven/Gradle dependencies.** Everything below is built from artifacts already on the v1.0 classpath.

### Existing artifacts used (no new entries in `libs.versions.toml`)

| Artifact (already present) | What v1.1 uses it for |
|---|---|
| `spring-boot-starter-web` (already a top-level dep) | `SseEmitter` / `Flux<ServerSentEvent>` return type on the new `/api/chat` `@PostMapping` controller. Spring MVC converts `Flux` to streaming SSE automatically when `produces=text/event-stream`. **HIGH** — verified via Spring Framework reference, `web/webmvc/mvc-ann-async.adoc`. |
| `spring-ai-starter-model-openai` (and the other three providers) | `ChatModel.stream(Prompt)` returns `Flux<ChatResponse>`. Already on classpath since v1.0 (LLM-01). |
| Reactor Core (transitively via Spring AI) | `Flux` is already on the classpath because `ChatModel#stream` returns `Flux<ChatResponse>`. No new `spring-boot-starter-webflux` dep needed; we stay on Spring MVC + virtual threads. |
| Jackson 3.1.2 (Boot-managed) | Serialize the 12 UI Message Stream Protocol envelope types to JSON for each `data: ...\n\n` SSE frame. |
| Spring Session Redis (already wired) | Reuses the cookie-based session for chat auth — `useChat`'s `DefaultChatTransport({ credentials: 'include' })` sends the existing session cookie. No JWT, no new auth path. |
| Postgres 17 + Liquibase 5 + Spring Data JPA (already wired) | Two new tables in v1.1: chat conversation history (`chat_conversation` + `chat_message`), audit row per confirmed send (`chat_send_audit`). Plain Liquibase YAML changelog — no new dep. |
| Spring Modulith events (already wired) | Reuse the existing event spine to fan out "chat-initiated send completed" → analytics module. |

### v1.1 backend architecture change (no dep): user-controlled tool execution

Spring AI normally executes tools internally — the model emits a tool call, Spring AI runs the `@Tool`-annotated method, feeds the result back, and the user only sees the final assistant text. **v1.1 disables this loop** so the chat UI can preview every tool call before execution (especially the three send tools).

Pattern verified against `https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/api/tools.html`:

```java
// Inside the LLM gateway (backend/core's llm.gateway.springai package).
// Per-request, build a ToolCallingChatOptions with internalToolExecutionEnabled(false).
// Then walk the tool-call loop yourself, emitting each step to the SSE writer:
//
//   ChatResponse response = chatModel.call(prompt);
//   while (response.hasToolCalls()) {
//       // 1. emit tool-call event over SSE so the UI shows the card
//       // 2. for "safe" tools (listRules, getEmail, etc.) — execute via
//       //    ToolCallingManager.executeToolCalls(prompt, response) immediately
//       // 3. for "approval-required" tools (sendEmail, replyEmail, forwardEmail) —
//       //    pause the stream until the client posts back addToolApprovalResponse
//       // 4. feed ToolExecutionResult.conversationHistory() back into a new Prompt
//       //    and call chatModel.call again
//   }
```

For **streaming** within a single LLM turn, swap `chatModel.call(prompt)` for `chatModel.stream(prompt)` and forward `Flux<ChatResponse>` items as `text-delta` SSE frames using the protocol below. The Spring AI 2.0.0-M6 streaming API is in `StreamingChatModel#stream(Prompt) -> Flux<ChatResponse>`. **HIGH** — verified via `/websites/spring_io_spring-ai_reference_2_0-snapshot`.

### Spring MVC SSE pattern (verified)

Three valid return-type choices on a `@PostMapping` controller, all producing `text/event-stream`. Verified against `https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html` and Spring Boot 4 reference:

| Return type | When to use | Notes |
|---|---|---|
| `SseEmitter` | Imperative writer pattern. Best fit when the SSE producer is a non-reactive thread loop (e.g., walking `chatModel.call(...)` in a `while (response.hasToolCalls())` loop) | Save the emitter, call `emitter.send(SseEmitter.event().data(payload))` from a worker thread, `emitter.complete()` at end. With `spring.threads.virtual.enabled=true` (already set in v1.0), the "worker thread" is a virtual thread — no extra `@Async` plumbing needed. |
| `Flux<ServerSentEvent<String>>` | Reactive pattern. Best fit when the producer is already a `Flux<ChatResponse>` from `ChatModel#stream` | Spring MVC auto-adapts `Flux` to SSE if `produces=MediaType.TEXT_EVENT_STREAM_VALUE`. Spring uses `ResponseBodyEmitter` under the hood and runs writes on the configured `AsyncTaskExecutor` (which is the virtual-thread executor when `spring.threads.virtual.enabled=true`). |
| `ResponseBodyEmitter` | Same as `SseEmitter` but without the SSE auto-format | We do **not** use this — `SseEmitter` is strictly better when the wire format is SSE. |

**Virtual-thread gotcha (verified):** When `spring.threads.virtual.enabled=true`, the Tomcat worker for an SSE request is a virtual thread. This is exactly what we want — long-lived SSE connections (LLM streams can run 30s+) cost approximately one stack frame, not one platform thread. No additional config required.

**No-WebFlux confirmation:** Returning `Flux<ServerSentEvent>` from a Spring **MVC** controller works because Spring MVC's `ReactiveAdapterRegistry` adapts Reactor `Flux` to `SseEmitter` automatically. **You do not need to add `spring-boot-starter-webflux`** — that would switch the whole app to Netty and break the existing Tomcat-based v1.0 setup.

### Vercel UI Message Stream Protocol — the wire format we must emit

The frontend `useChat` hook from `@ai-sdk/react@3` expects a specific SSE format. **There is no Java/Spring adapter library** — we hand-write the encoder in `backend/api`. The format is small (~12 event types) and stable. Verified via Context7 `/vercel/ai` `content/docs/04-ai-sdk-ui/50-stream-protocol.mdx` and `content/docs/03-ai-sdk-core/55-testing.mdx`.

**Required response headers:**

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
x-vercel-ai-ui-message-stream: v1     ← REQUIRED header for non-Vercel backends
```

**Event envelope** — every event is a single SSE `data:` line containing a JSON object:

```
data: {"type":"start","messageId":"msg-123"}\n\n
data: {"type":"text-start","id":"text-1"}\n\n
data: {"type":"text-delta","id":"text-1","delta":"Hello"}\n\n
data: {"type":"text-delta","id":"text-1","delta":" world"}\n\n
data: {"type":"text-end","id":"text-1"}\n\n
data: {"type":"finish"}\n\n
data: [DONE]\n\n
```

**Ordering rule (will cause runtime errors if violated):** every `text-delta` MUST be wrapped between a matching `text-start` and `text-end` with the same `id`. Source: `https://github.com/vercel/ai/blob/main/content/docs/07-reference/05-ai-sdk-errors/ai-ui-message-stream-error.mdx`. Same rule applies to tool parts (`tool-input-start` → `tool-input-delta` → `tool-input-available` → `tool-output-available`).

**Event types we will emit in v1.1** (verified subset — full catalog is larger):

| Type | When | Payload |
|---|---|---|
| `start` | First event of a turn | `{ type, messageId }` |
| `text-start` | New assistant text block | `{ type, id }` |
| `text-delta` | Each token chunk from Spring AI's `Flux<ChatResponse>` | `{ type, id, delta }` |
| `text-end` | Text block finished | `{ type, id }` |
| `tool-input-start` | Model started emitting a tool call | `{ type, toolCallId, toolName }` |
| `tool-input-delta` | Streaming arguments | `{ type, toolCallId, inputTextDelta }` |
| `tool-input-available` | Arguments fully parsed | `{ type, toolCallId, toolName, input }` |
| `tool-output-available` | Tool result | `{ type, toolCallId, output }` |
| `tool-output-error` | Tool threw | `{ type, toolCallId, errorText }` |
| `data-<custom>` | Custom data parts (e.g., `data-tenant-credit-balance`, `data-rule-preview`) | `{ type: "data-<name>", id?, data }` |
| `finish` | Turn ended | `{ type }` |
| `[DONE]` | Stream terminator | (literal `data: [DONE]\n\n`) |

For human-in-the-loop approval, the message-part state on the client moves through `input-streaming` → `input-available` → `approval-requested` → `output-available` | `output-denied`. The `approval-requested` state is what makes the `<Confirmation>` component render.

---

## What v1.1 Adds — Backend Persistence

Two new Liquibase YAML changelogs (no new library):

| Table | Owner module | Purpose |
|---|---|---|
| `chat_conversation` | `backend/core` (new `chat` package) | Per-tenant conversation root: `(id, tenant_id, title, created_at, updated_at)`. Title is the LLM-generated short summary. |
| `chat_message` | `backend/core` (new `chat` package) | Per-turn message: `(id, conversation_id, role, parts_jsonb, created_at)`. `parts_jsonb` is the `UIMessage.parts[]` array verbatim, so the frontend can replay history into `useChat({ initialMessages: ... })` without re-streaming. **Includes tool-call inputs/outputs.** |
| `chat_send_audit` | `backend/core` (`chat` package, but also queried by analytics) | Per confirmed send: `(id, tenant_id, conversation_id, message_id, tool_name, gmail_message_id, sent_at, draft_id_before, recipient_count)`. **Append-only**, never updated, never deleted within 30-day window. |

**Privacy note:** This is a deliberate carve-out from v1.0's "no LLM prompts/completions stored" rule, locked in `CLAUDE.md` and `PROJECT.md` ("User-typed rule-builder assistant chat (chat messages + structured tool outputs) persists normally — it is UI configuration input, not extracted email content"). The carve-out **explicitly excludes** inlining email bodies into stored chat messages: tools that fetch email content (`getEmail`, `listEmails`) must return short-lived summaries, not raw bodies, before any persistence.

---

## Development Tools (no changes)

No new dev dependencies. Existing toolchain — `vitest`, `playwright`, `eslint`, `typescript`, `openapi-typescript`, `openapi-fetch` — covers v1.1.

**Playwright coverage for v1.1:** golden-path E2E must include:
1. User sends "Create a rule for receipts" → assistant streams reasoning → emits `tool-input-available` for `createRule` → tool auto-executes (no approval) → `<Tool>` card shows success → DB row appears.
2. User sends "Send a thank-you reply to this thread" → assistant streams draft text → emits `tool-input-available` for `replyEmail` → `<Confirmation>` dialog renders → user clicks **Confirm** → `addToolApprovalResponse({approved: true})` → backend executes Gmail draft-send → audit row in `chat_send_audit`.
3. User sends the same prompt → clicks **Cancel** → `addToolApprovalResponse({approved: false})` → backend skips the Gmail call → no audit row, no Gmail state mutation.

---

## Alternatives Considered (and rejected)

| Recommended | Alternative | When Alternative Would Win | Why We Reject for v1.1 |
|---|---|---|---|
| `ai@^6.0.184` + `@ai-sdk/react@^3.0.186` | `ai@^5.0.188` (`ai-v5` dist-tag) | If `@ai-sdk/react@2.x` were the only stable line — it is not. v5 is still maintained but in maintenance mode. | v6 is the current `latest` tag on npm, used by Inbox Zero in production, and supported by AI Elements 1.9. Adopting v5 now means a forced migration in 3-6 months. |
| `ai@^6` | `ai@^7.0.0-beta.116` | If we wanted to track the bleeding edge | v7 is beta on the `beta` dist-tag. Our v1.0 LLM gateway is locked to a *milestone* (Spring AI 2.0.0-M6) — adding *another* pre-release dependency on the frontend doubles the migration burden. |
| AI Elements CLI (copy-paste primitives) | `npm install ai-elements@1.9.0` as runtime dep | If we wanted version-pinned upgrades of the components | The whole point of the shadcn-style model is that components become *your code* — we can edit them, restyle them, and translate strings (Vietnamese) without forking a runtime package. This matches our existing convention with `components/ui/**`. |
| `streamdown@^2.5.0` | `react-markdown@^9` + custom partial-token handling | If we needed an ecosystem older than 2024 | Streamdown is **the** Vercel-supported renderer for AI streams; AI Elements `MessageResponse` and `Response` components depend on it. Using `react-markdown` would require monkey-patching AI Elements or replacing both. |
| Hand-written UI Message Stream encoder (Java) | Look for a "Vercel AI SDK Java" adapter | If a maintained Java adapter existed | No production-grade Java adapter exists in the Vercel ecosystem (verified via Context7 search). The 12-event protocol is small and stable enough to hand-write in 1 file (~300 LoC) inside `backend/api/.../ChatStreamingController`. |
| Spring MVC `SseEmitter` / `Flux<ServerSentEvent>` | `spring-boot-starter-webflux` | If the whole app were reactive | v1.0 is MVC + virtual threads. Adding WebFlux would create dual web stacks (Tomcat + Netty), break existing servlet filters (security, MDC, `@Sensitive` logback scrubbers), and contradict the locked `CLAUDE.md` rule "Spring WebFlux (use Spring MVC + virtual threads via `spring.threads.virtual.enabled=true`)." |
| `Flux<ChatResponse>` from Spring AI 2.0.0-M6 `StreamingChatModel` | Direct vendor SDK streaming (OpenAI Java SDK, Anthropic Java SDK) | If Spring AI's stream wrapping introduced unacceptable latency | Direct vendor SDKs would violate `CLAUDE.md`'s `do not use` list ("Raw HTTP LLM calls or vendor SDK usage outside the Spring AI adapter"). Spring AI's `Flux<ChatResponse>` is the locked path. |
| Spring AI `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)` | Let Spring AI run all tools internally | If no tool needed user approval | Three tools (`sendEmail`, `replyEmail`, `forwardEmail`) **must** pause for user approval per v1.1 safety story. Disabling internal execution is the only Spring AI 2.0.0-M6 path that gives the chat UI a chance to intercept. |
| Cookie session via existing Spring Session Redis | Issue a separate JWT for SSE auth | If we wanted to skip the session round-trip | The cookie is already `HttpOnly + SameSite=Lax + Secure`. `useChat({ transport: new DefaultChatTransport({ credentials: 'include' }) })` sends it on every SSE `POST`. Anything else duplicates auth and violates `CLAUDE.md`'s "Stateless JWT user sessions (cookie + Redis-backed Spring Session)" do-not-use rule. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| **Vercel AI SDK `ai` package on the Java backend** | It is a TypeScript-only package; there is no JVM port. Any attempt to invoke `streamText`/`generateText` from Java means standing up a Node sidecar — splits the LLM gateway across two runtimes, breaks `LlmGateway`'s tenant context and credit-ledger interceptors. | Keep all LLM orchestration in **Spring AI 2.0.0-M6** inside `core.llm.gateway.springai` (already locked). The "Vercel" surface area is only the SSE wire format the frontend expects — emit it from a Spring MVC controller. |
| **`@ai-sdk/openai`, `@ai-sdk/anthropic`, `@ai-sdk/google`, etc. on the frontend** | These are *server-side* model adapters intended for Next.js API routes that would *bypass* our Java backend. Using them duplicates LLM auth, leaks tenant API keys to the browser, and breaks credit metering. | The frontend never speaks to a model provider. All model traffic goes through `POST /api/chat` on `backend/api` → `LlmGateway` → Spring AI → provider. `@ai-sdk/react` is the *only* `@ai-sdk/*` package the frontend needs. |
| **WebSockets / STOMP** for chat streaming | A WebSocket adds bidirectional state we do not need (chat is request → stream-response), forces a separate auth handshake, and breaks corporate proxies that block long-lived non-SSE connections. **And `@stomp/stompjs@^7.3.0` is already in `apps/web/package.json`** — that is for a different feature; do **not** repurpose it for chat. | SSE over the existing HTTPS endpoint via `SseEmitter` or `Flux<ServerSentEvent>`. `useChat` natively consumes SSE. |
| **`spring-boot-starter-webflux`** | Adds Netty alongside Tomcat, breaks all v1.0 servlet filters (security, `@Sensitive` logback, MDC, tenant Scoped Values), and contradicts a locked `CLAUDE.md` constraint. | `spring-boot-starter-web` (already present) + `SseEmitter` or `Flux<ServerSentEvent>` return type. Spring MVC handles both. |
| **Long-term persistence of LLM prompts/completions touching email content** | Locked privacy invariant. The chat-message persistence carve-out is **only** for user text + structured tool inputs/outputs. Email bodies fetched by `getEmail` tools must be summarized in-memory and discarded before being written to `chat_message.parts_jsonb`. | Store user messages and structured tool args/results in `chat_message`. For tool outputs that include email content, store a short metadata summary (subject, sender, date, ≤120 char snippet) — never the raw body. |
| **Streaming prompt/completion telemetry into logs or DB** | Same privacy invariant as v1.0 (LLM-09). Existing `@Sensitive` Logback scrub is the safety net. | Use existing Micrometer + OTel observability. Spans should record provider, model, token counts, latency — never content. |
| **`@vercel/ai-utils` or `@vercel/ai-sdk-*` on the backend (Node)** | Not applicable — we have no Node backend. | N/A. |
| **`ai-elements` as a runtime npm dep** | It is a **CLI** that scaffolds source code. `npm install ai-elements` would install the CLI as a runtime dep — bloat with no benefit. | `pnpm dlx ai-elements@latest add <component>` writes the source to `components/ai-elements/**`. Treat that folder like `components/ui/**` (already lint-ignored). |
| **`@ai-sdk/anthropic-tools` / experimental human-in-the-loop helpers on a Node server** | We have no Node server. The HITL workflow lives in Java (`ToolCallingChatOptions.internalToolExecutionEnabled(false)` + manual SSE emission) and React (`addToolApprovalResponse` + `<Confirmation>`). | Spring AI's user-controlled tool execution + the SSE protocol described above. |
| **Inbox Zero's `streamdown@2.5.0` markdown patches** | Use the public `streamdown` package — do not vendor Inbox Zero's local copy. | `pnpm add streamdown@^2.5.0`. |

---

## Stack Patterns by Variant

**If a tool is read-only (no side effects):**
- Use Spring AI's default internal tool execution (do **not** set `internalToolExecutionEnabled(false)` for that request)
- Emit `tool-input-available` then `tool-output-available` back-to-back in the SSE stream
- Examples: `listRules`, `getRule`, `getEmail`, `listEmails`, `getAnalytics`, `getCredits`, `listSenders`, `getMemory`

**If a tool mutates state but is non-destructive (label, archive, save draft, update rule):**
- Same as read-only — auto-execute. Mutations are reversible via existing v1.0 30-day undo (TRG-06). No approval card.
- Examples: `createRule`, `updateRule`, `deleteRule` (reversible), `addLabel`, `archive`, `saveDraft`, `updatePersonalInstructions`, `updateMemory`

**If a tool is **destructive or external-facing** (sends email):**
- Set `internalToolExecutionEnabled(false)` for the parent request
- Emit `tool-input-available` with `approval: { id }` data part
- **Pause the stream** until the next `POST /api/chat` carries the approval response
- On approval: execute via `ToolCallingManager.executeToolCalls` and emit `tool-output-available`
- On rejection: skip execution and emit `tool-output-denied`
- Examples: `sendEmail`, `replyEmail`, `forwardEmail` — the v1.1 high-risk set

**If the model supports thinking / reasoning (Claude 3.7 Sonnet, OpenAI o-series):**
- Emit `reasoning-start` / `reasoning-delta` / `reasoning-end` events alongside `text-*`
- Frontend `<Reasoning>` component collapses by default — opt-in disclosure for power users

---

## Version Compatibility Matrix

| Package | Compatible With | Notes |
|---|---|---|
| `ai@^6.0.184` | `@ai-sdk/react@^3.0.186`, `zod@^3.25.76 \|\| ^4.1.8` | We have `zod@4.4.3` — compatible. |
| `@ai-sdk/react@^3.0.186` | `react@^18 \|\| ~19.0.1 \|\| ~19.1.2 \|\| ^19.2.1` | We have `react@19.2.6` — compatible. |
| `@ai-sdk/react@^3.0.186` | `ai@6.0.184` (transitive dep, exact pin) | The two version-track together; do not mix `@ai-sdk/react@3` with `ai@5`. |
| `streamdown@^2.5.0` | `react@^18 \|\| ^19`, `react-dom@^18 \|\| ^19` | Compatible with our React 19.2.6. |
| `ai-elements@1.9.0` (CLI) | shadcn/ui initialized, Tailwind CSS 4, AI SDK installed | All prereqs satisfied. |
| Spring AI 2.0.0-M6 `StreamingChatModel#stream(Prompt)` | Reactor Core (transitive) | Already on classpath; no need to import `spring-boot-starter-webflux`. |
| Spring MVC `SseEmitter` | Spring Boot 4.0.6 + `spring-boot-starter-web` | Already on classpath. Works with `spring.threads.virtual.enabled=true`. |
| Spring MVC `Flux<ServerSentEvent>` return | Spring Framework 7.0.7's `ReactiveAdapterRegistry` | Works on MVC without WebFlux. |
| Vercel `useChat` SSE consumption | `text/event-stream` + `x-vercel-ai-ui-message-stream: v1` response header | The header is **mandatory** for non-Vercel backends. Missing it causes `useChat` to silently fall back to text-only mode. |
| `addToolApprovalResponse` (`@ai-sdk/react@3`) | `requireApproval: true` (Vercel server-side) **or** custom `approval-requested` state part (our Java backend) | We will emit the custom part — the frontend hook does not care whether the server is Node or Spring. |

---

## Integration Points (where v1.1 touches v1.0)

| Touch point | v1.1 change | Risk |
|---|---|---|
| `LlmGateway` (`core.llm.gateway.springai`) | Add `stream(Prompt)` method returning `Flux<ChatResponse>` + per-request `internalToolExecutionEnabled` flag | Localized to the gateway module — well within the ArchUnit-enforced single-adapter rule. |
| `backend/api` controllers | Add `ChatStreamingController` with `@PostMapping(path="/api/chat", produces=MediaType.TEXT_EVENT_STREAM_VALUE)` | New controller — no impact on existing endpoints. |
| Spring Session Redis | No code change | `useChat` sends the existing session cookie via `credentials: 'include'`. |
| Spring Security filter chain | Whitelist `/api/chat` for authenticated tenants only; CSRF — chat requests use the same session token, so the existing CSRF approach (per-form token) needs an SSE-aware exception or the same double-submit pattern as existing API endpoints | Verify existing CSRF config; v1.0 may already disable CSRF for `/api/**` if it is a same-origin JSON API. |
| `springdoc-openapi` (v1.0 OpenAPI generator) | Add a schema entry for `POST /api/chat` request body and a "see UI Message Stream Protocol" note in the response | The streaming response cannot be fully expressed in OpenAPI — document the protocol in `apps/web/lib/chat-protocol.md` and reference it. |
| `apps/web/scripts/generate-api.ts` | No change needed for the chat endpoint (streaming is not OpenAPI-modeled). Settings page endpoints **do** flow through OpenAPI as normal | Settings page reuses existing typed-client pattern. |
| ArchUnit rule TRG-03 ("zero send call sites") | Update to allow **exactly one** new call site: the chat-tool implementation of `sendEmail` / `replyEmail` / `forwardEmail` inside the approved branch of the HITL flow | Locked in by repo-wide grep + ArchUnit assertion; cannot regress without a test failure. |
| Liquibase | Add three new YAML changelogs (`chat_conversation`, `chat_message`, `chat_send_audit`) | Standard pattern; no migration risk. |

---

## Sources

**Context7 (HIGH confidence):**
- `/vercel/ai` — `useChat` v6 API, `DefaultChatTransport`, UI Message Stream Protocol event types, HITL tool approval, message parts state machine, headers required for non-Node backends. Fetched 2026-05-17.
- `/vercel/ai-elements` — Component catalog (`Conversation`, `Message`, `PromptInput`, `Tool`, `Reasoning`, `Loader`, `Suggestion`, `Confirmation`), CLI installation via `pnpm dlx ai-elements@latest add <component>`, prerequisites (React 19, Next 14+, Tailwind 4, shadcn). Fetched 2026-05-17.
- `/vercel/streamdown` — Drop-in replacement for `react-markdown`, partial-token handling. Fetched 2026-05-17.
- `/websites/spring_io_spring-ai_reference_2_0-snapshot` — `StreamingChatModel#stream(Prompt) → Flux<ChatResponse>`, `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)`, `ToolCallingManager.executeToolCalls`, `@Tool` annotation. Fetched 2026-05-17.
- `/spring-projects/spring-framework` and `/websites/spring_io_spring-framework_reference` — `SseEmitter`, `ResponseBodyEmitter`, `Flux<ServerSentEvent>` return adaptation in Spring MVC, reactive back-pressure with `AsyncTaskExecutor`. Fetched 2026-05-17.
- `/websites/spring_io_spring-boot_4_0-snapshot` — Virtual thread enablement via `spring.threads.virtual.enabled=true`, `SimpleAsyncTaskExecutor` with virtual threads behavior, `spring.main.keep-alive=true` caveat for `@Scheduled` daemon scheduler threads. Fetched 2026-05-17.

**npm registry (HIGH confidence, exact versions on 2026-05-17):**
- `npm view ai` → latest `6.0.184`; v5 latest under `ai-v5` tag = `5.0.188`; v7 beta under `beta` tag = `7.0.0-beta.116`. Peer dep: `zod ^3.25.76 || ^4.1.8`. Node ≥ 18.
- `npm view @ai-sdk/react` → latest `3.0.186`. Depends on `ai@6.0.184` (exact), `swr@^2.2.5`, `throttleit@2.1.0`, `@ai-sdk/provider-utils@4.0.27`. Peer dep: `react ^18 || ~19.0.1 || ~19.1.2 || ^19.2.1`.
- `npm view ai-elements` → CLI package, latest `1.9.0`.
- `npm view streamdown` → latest `2.5.0`. Peer dep: `react ^18 || ^19`.

**Local reference repo (HIGH confidence, mirrors production usage):**
- `D:/study materials summer 2026/EXE202/inbox-zero/apps/web/package.json` — production Inbox Zero on 2026-05-17 uses `ai@6.0.168`, `@ai-sdk/react@3.0.170`, `react@19.2.5`, `streamdown@2.5.0`, `use-stick-to-bottom@1.1.3` (the latter is already vendored inside AI Elements' `Conversation` component, so we do not need to install it separately).

**Existing v1.0 stack reference (validated, unchanged):**
- `apps/web/package.json` on `main` at 2026-05-17 — `react@19.2.6`, `next@16.2.6`, `zod@4.4.3`, `@tanstack/react-query@5.100.9`, `shadcn@^4.7.0`, `tailwindcss@^4`, `sonner@^2.0.7` (required by AI Elements Confirmation toasts).
- `CLAUDE.md` — locked do-not-use list (Lombok, WebFlux, raw vendor SDKs, stateless JWT, Kafka, embeddings), tool-call allow-list, privacy carve-outs for chat persistence.
- `.planning/research/STACK.md` (v1.0 history before this update) — full Java 25 / Spring Boot 4.0.6 / Spring AI 2.0.0-M6 backend stack and Next.js 16.2.4 frontend stack details.

---

*Stack research for: Zero Mail v1.1 — chat email assistant + AI settings page*
*Researched: 2026-05-17 by gsd-researcher (Context7 + npm + Inbox Zero reference + Spring AI 2.0-SNAPSHOT docs)*
