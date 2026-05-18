---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 03
subsystem: backend
tags: [chat, spring-ai, gmail, rules, triage, reactor, sse, privacy, archunit]
requires:
  - phase: 07-02
    provides: Chat schema, message parts persistence, sanitizer, assistant settings, memory, and knowledge repositories
provides:
  - core.chat Modulith module declaration with the locked D-01 dependency set
  - 24-tool authoritative enum and catalog with 8/7/6/3 partition validation
  - Spring-AI-free chat streaming use-case contracts and Spring AI adapter confinement
  - Tenant-aware Reactor scheduler, raw tool-call registry, Vercel stream protocol emitter, and DB-backed chat memory
  - Eight tenant-scoped read tool handlers for Gmail, rules, sender safety, and assistant memories
affects: [phase-07, chat, spring-ai, assistant-tools, assistant-confirmation, frontend-chat]
tech-stack:
  added: []
  patterns:
    - Spring AI imports stay confined to core.chat.llm.springai while core.chat.usecases exposes pure Java contracts
    - Read tools resolve TenantContext at call time and accept the declared tenant only as a defensive cross-check
    - getMessage may return body content to the LLM in memory, but SanitizingSink strips body-shaped fields before persistence
key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/chat/package-info.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/ChatToolName.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatLlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatToolCatalog.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/TenantAwareReactorScheduler.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/ChatToolCallRegistry.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/ZeroMailChatMemory.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/ChatReadToolHandler.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/SearchInboxToolHandler.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/GetMessageToolHandler.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/SearchMemoriesToolHandler.java
    - backend/core/src/test/java/com/zeromail/core/chat/usecases/tools/ReadToolsIT.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatMessageJdbcRepository.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/ChatMessage.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/SendEmailToolArgs.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/ReplyEmailToolArgs.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/ForwardEmailToolArgs.java
key-decisions:
  - "The 24-tool authoritative list is locked in ChatToolName and ChatToolCatalog; createRule is confirm-required, and searchMemories is the eighth read tool."
  - "VercelProtocolEmitter uses a core-local FrameWriter instead of Spring MVC SseEmitter because backend/core must not depend on Spring MVC."
  - "ZeroMailChatMemory lives in core.chat.llm.springai because it implements Spring AI ChatMemory; all other chat contracts stay Spring-AI-free."
  - "GetMessageToolHandler serializes the in-memory decoded body as bodyText so the existing source-aware sanitizer strips it before chat_message persistence."
patterns-established:
  - "Read tool handlers implement ChatReadToolHandler and return JSON strings from synchronous, tenant-checked direct service/repository/API calls."
  - "Spring AI tool callbacks are schema-only callbacks with internalToolExecutionEnabled(false); execution remains owned by the orchestrator path."
  - "Raw tool-call deltas are assembled in ChatToolCallRegistry instead of trusting Spring AI aggregated toolCalls."
requirements-completed:
  - ARCH-05
  - ARCH-07
  - CHAT-02
  - CHAT-03
duration: 44min
completed: 2026-05-18
---

# Phase 07 Plan 03: Chat Module, Streaming LLM, and Read Tools Summary

**Spring-AI-confined chat streaming infrastructure with tenant-safe read tools and a locked 24-tool catalog**

## Performance

- **Duration:** 44 min
- **Started:** 2026-05-18T04:20:20Z
- **Completed:** 2026-05-18T05:05:19Z
- **Tasks:** 3/3
- **Files modified:** 45

## Accomplishments

- Declared `core.chat` as a Spring Modulith module and added chat domain IDs, roles, confirmation state, 24 tool names, and send-completed event contracts.
- Added pure Java streaming contracts, `ChatToolCatalog` validation, token counting, sanitized stream output, tenant-aware scheduling, raw tool-call reconstruction, Vercel protocol emission, and Spring AI adapter classes.
- Added DB-backed `ZeroMailChatMemory` with token-budget truncation and history replay from persisted `chat_message.parts`.
- Added eight read-only tool handlers covering Gmail search/message/labels/thread, rules list/get, sender safety lookup, and assistant memory search.
- Added integration tests proving raw tool-call assembly, stream event ordering, memory replay, multi-tenant stream isolation, read-tool JSON shapes, memory tenant isolation, and body-stripped persistence.

## Task Commits

Each task was committed atomically:

1. **Task 3.1: Modulith package-info + domain records + enums + event class** - `7f09576b` (feat)
2. **Task 3.2: Chat streaming LLM infrastructure** - `f1e37411` (feat)
3. **Task 3.3: 8 read-only tool handlers + ReadToolsIT** - `012cd206` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/chat/package-info.java` - declares the `core.chat` module and D-01 allowed dependencies.
- `backend/core/src/main/java/com/zeromail/core/chat/domain/ChatToolName.java` - locks the 24-tool authoritative list and category partition.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatToolCatalog.java` - validates exactly 24 distinct tool definitions with 8/7/6/3 partition.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatLlmGateway.java` - Spring-AI-free streaming LLM boundary.
- `backend/core/src/main/java/com/zeromail/core/chat/llm/TenantAwareReactorScheduler.java` - rebinds `TenantContext` through scheduled stream work.
- `backend/core/src/main/java/com/zeromail/core/chat/llm/ChatToolCallRegistry.java` - reconstructs raw tool calls from chunk deltas.
- `backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java` - emits ordered Vercel UI Message Stream Protocol frames through a core-local writer.
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/*.java` - contains all Spring AI imports for streaming, memory, model factory, and tool callback translation.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/*.java` - adds the eight read tool handlers and shared read-tool interface/helpers.
- `backend/core/src/test/java/com/zeromail/core/chat/llm/**/*.java` - verifies registry, Vercel stream ordering, memory replay, and multi-tenant stream isolation.
- `backend/core/src/test/java/com/zeromail/core/chat/usecases/tools/ReadToolsIT.java` - verifies all read tools, `searchMemories` tenant isolation, and sanitized persistence for `getMessage`.

## Decisions Made

- Kept `backend/core` free of Spring MVC by using `VercelProtocolEmitter.FrameWriter`; API will adapt `SseEmitter` in Plan 04.
- Kept `ZeroMailChatMemory` under `core.chat.llm.springai` because importing Spring AI `ChatMemory` anywhere else would violate the adapter boundary.
- Used the existing Gmail API client directly for Gmail read tools because v1.0 has partial preview services but no complete `searchInbox`/`getMessage`/`getThread` service surface.
- Serialized decoded message body as `bodyText` for LLM in-memory use so the existing source-aware body-stripper removes it before persistence without changing the Liquibase trigger.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced direct SseEmitter dependency with a core-local writer**
- **Found during:** Task 3.2 streaming infrastructure
- **Issue:** The plan described `VercelProtocolEmitter` wrapping `SseEmitter`, but `backend/core` intentionally has no Spring MVC compile dependency.
- **Fix:** Added a `FrameWriter` interface and kept the emitter in `core.chat.llm`; Plan 04 can adapt `SseEmitter` in `backend/api`.
- **Files modified:** `VercelProtocolEmitter.java`, `VercelProtocolEmitterTest.java`
- **Verification:** `VercelProtocolEmitterTest`, `ChatLlmAdapterBoundaryTest`, and the full 07-03 Gradle sweep passed.
- **Committed in:** `f1e37411`

**2. [Rule 2 - Missing Critical] Made getMessage body stripping align with the existing sanitizer**
- **Found during:** Task 3.3 read-tool persistence proof
- **Issue:** The plan named the field `decodedTextBody`, but the existing source-aware sanitizer and DB trigger strip exact body-shaped keys such as `bodyText`.
- **Fix:** Kept the Java record component as `decodedTextBody` but emitted JSON property `bodyText`; `ReadToolsIT` proves the LLM sees the body in memory and `chat_message.parts` persists without the body field or sentinel.
- **Files modified:** `GetMessageToolHandler.java`, `ReadToolsIT.java`
- **Verification:** `ReadToolsIT` and `ChatPersistenceContentBanTest` passed.
- **Committed in:** `012cd206`

---

**Total deviations:** 2 auto-fixed issues (1 blocking, 1 missing critical).
**Impact on plan:** Both changes preserve the planned architecture and privacy guarantees with no scope expansion.

## Issues Encountered

- Spring AI 2.0.0-M6 API shapes were verified against Context7/current docs and local Gradle source jars before implementing `OpenAiChatOptions`, `FunctionToolCallback`, and JSON schema generation.
- The `ReadToolsIT` fixture initially passed `Instant` directly through `JdbcTemplate`; PostgreSQL JDBC requires `Timestamp` for that insert path. The fixture was corrected and the test passed.
- JetBrains warnings for forward contracts and intentional future-use methods were resolved with narrow suppressions or small cleanups before commits.

## Verification

- `./gradlew :backend:core:spotlessApply :backend:core:test --tests "com.zeromail.core.chat.llm.*" --tests "com.zeromail.core.arch.*" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` - passed before Task 3.2 commit.
- JetBrains `build_project` for all Task 3.2 files - passed before Task 3.2 commit.
- `./gradlew :backend:core:spotlessApply :backend:core:test --tests "com.zeromail.core.chat.usecases.tools.ReadToolsIT" --tests "com.zeromail.core.arch.ChatPersistenceContentBanTest"` - passed before Task 3.3 commit.
- JetBrains `build_project` for all Task 3.3 files - passed before Task 3.3 commit.
- `./gradlew :backend:core:spotlessApply :backend:core:test --tests "com.zeromail.core.chat.llm.*" --tests "com.zeromail.core.chat.llm.springai.*" --tests "com.zeromail.core.chat.usecases.tools.ReadToolsIT" --tests "com.zeromail.core.chat.domain.ChatToolNameEnumTest" --tests "com.zeromail.core.arch.*" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` - passed.
- JetBrains file inspections for every new/modified 07-03 Java file - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04 can now build the public chat controllers and `ChatOrchestrator` on stable core contracts: the LLM gateway is Spring-AI-free, the stream sink contract is framework-neutral, the read handlers are synchronous tenant-checked services, and the read-tool body sanitization path is already proven end to end.

## Self-Check: PASSED

- Created files listed above exist in the working tree and are covered by task commits.
- Task commits `7f09576b`, `f1e37411`, and `012cd206` exist in `git log --grep=07-03`.
- Plan verification commands passed after all task commits.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*
