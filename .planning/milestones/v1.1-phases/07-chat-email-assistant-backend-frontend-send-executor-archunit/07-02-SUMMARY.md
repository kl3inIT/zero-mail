---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 02
subsystem: database
tags: [postgres, liquibase, chat, privacy, archunit, sanitizer, jdbc, jpa]
requires:
  - phase: 07-01
    provides: Wave 0 chat privacy, Gmail send, Reactor, Spring AI boundary, and fixture gates
provides:
  - Chat persistence schema 041-046 with source-aware chat_message body-ban trigger
  - Chat message parts schema v1 domain records, JSON converter, and JDBC repository
  - Assistant pending action, unified action audit, settings, memory, and knowledge JPA repositories
  - Source-aware tool output sanitizer and XML-fenced personalization renderer
affects: [phase-07, chat, privacy, assistant-confirmation, assistant-memory, gmail-send]
tech-stack:
  added: []
  patterns:
    - Source-aware body bans are enforced at sanitizer, ArchUnit, and Postgres trigger layers
    - Chat message JSONB uses explicit schemaVersion dispatch through ChatPartsSchemaV1
    - High-write chat_message persistence uses JdbcTemplate plus an explicit RowMapper/converter
key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/chat/persistence/PostgresJsonPathPreflightIT.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/parts/ChatMessageParts.java
    - backend/core/src/main/java/com/zeromail/core/chat/domain/parts/AssistantTextPart.java
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatMessageJdbcRepository.java
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsJsonConverter.java
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsSchemaV1.java
    - backend/core/src/test/java/com/zeromail/core/chat/persistence/ChatMessageBodyBanTriggerSourceAwareIT.java
  modified:
    - backend/core/src/main/resources/db/changelog/changes/041-chat.yaml
    - backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml
    - backend/core/src/main/resources/db/changelog/changes/043-assistant-pending-action.yaml
    - backend/core/src/main/resources/db/changelog/changes/044-assistant-action-audit.yaml
    - backend/core/src/main/resources/db/changelog/changes/045-assistant-settings.yaml
    - backend/core/src/main/resources/db/changelog/changes/046-assistant-memory-knowledge.yaml
    - backend/core/src/main/java/com/zeromail/core/chat/sanitize/ToolOutputSanitizer.java
    - backend/core/src/main/java/com/zeromail/core/chat/sanitize/XmlFencedPersonalizationRenderer.java
key-decisions:
  - "042 ships chat_message, recursive JSONB detectors, reject_chat_message_with_body(), and chat_message_body_ban as one changeSet for MEDIUM-3 atomicity."
  - "Source-aware privacy boundary is encoded by tool part type: email-read outputs reject body fields, send/draft arguments preserve user-authored draft bodies."
  - "Confirmation CAS stays on assistant_pending_action.parts_updated_at plus state; chat_message has no updated_at column."
  - "assistant_action_audit is the unified table for confirmed-send and write-reversible tools, with SEND_IN_FLIGHT supported from day one."
patterns-established:
  - "BodyContentBanViolationException is translated at the JDBC repository boundary whenever the Postgres trigger message contains Chat persistence violation."
  - "Future chat message schema changes add a new dispatcher branch instead of silently accepting unknown schemaVersion values."
requirements-completed:
  - ARCH-02
  - ARCH-06
  - CHAT-05
  - CHAT-07
duration: 1h 4m
completed: 2026-05-18
---

# Phase 07 Plan 02: Chat Persistence and Sanitization Summary

**Source-aware chat persistence schema, sanitizer, JSONB message parts, and repository tests for the assistant chat backend**

## Performance

- **Duration:** 1h 4m
- **Started:** 2026-05-18T02:52:23Z
- **Completed:** 2026-05-18T03:56:10Z
- **Tasks:** 4/4
- **Files modified:** 51

## Accomplishments

- Validated the trigger-ready Postgres 17 JSONB traversal approach before relying on it in Liquibase.
- Added Liquibase 041-046 and wired them into the master changelog, including the single-changeSet 042 body-ban trigger.
- Added chat message part records, `AssistantTextPart`, source-aware `ToolOutputSanitizer`, personalization sanitizer, XML-fenced renderer, and chat exceptions.
- Added JPA entities/repositories for chat, pending actions, audit, settings, memory, and knowledge snippets.
- Added the JDBC `ChatMessageJdbcRepository`, schemaVersion-aware converter, row mapper, and persistence integration tests proving the source-aware draft carve-out.

## Task Commits

Each task was committed atomically:

1. **Task 2.0: Postgres JSONPath/body-ban preflight** - `44f3a69b` (test)
2. **Task 2.1: Liquibase schema 041-046** - `0c345792` (feat)
3. **Task 2.2: Chat parts and sanitizers** - `e6b138ee` (feat)
4. **Task 2.3: Persistence repositories and ITs** - `4d5a5bcc` (feat)

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml` - creates `chat_message`, recursive JSONB detectors, and the source-aware body-ban trigger in one changeSet.
- `backend/core/src/main/resources/db/changelog/changes/043-assistant-pending-action.yaml` - owns pending-action state, `parts_updated_at`, and `draft_body` for confirmation replay.
- `backend/core/src/main/resources/db/changelog/changes/044-assistant-action-audit.yaml` - creates unified action audit with `tool_category`, `tool_name`, `state`, and `in_flight_at`.
- `backend/core/src/main/java/com/zeromail/core/chat/domain/parts/*.java` - defines schema v1 persisted part records including `AssistantTextPart`.
- `backend/core/src/main/java/com/zeromail/core/chat/sanitize/*.java` - strips extracted email bodies, preserves user-authored draft bodies, and renders fenced personalization slots.
- `backend/core/src/main/java/com/zeromail/core/chat/persistence/*.java` - adds JPA entities/repositories plus JDBC message persistence and JSON converter.
- `backend/core/src/test/java/com/zeromail/core/chat/persistence/*.java` - proves migrations, trigger behavior, schema dispatch, repositories, and body-ban exception translation.

## Decisions Made

- Kept body detection source-aware by tool part type rather than field name alone, matching the draft-body carve-out in project privacy constraints.
- Used recursive PL/pgSQL JSONB traversal instead of unsupported recursive JSONPath constructs for Postgres 17 compatibility.
- Assigned trigger `RAISE EXCEPTION` errors to SQLSTATE `23514` so Spring treats the privacy trigger as a data integrity violation while the repository still translates by message defensively.
- Added narrow `unused` suppressions for JPA/repository members intentionally shipped for later Phase 7 waves.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed Postgres forbidden-HTML detection and trigger SQL state**
- **Found during:** Task 2.3 persistence verification
- **Issue:** The original `<(script|iframe)\b` pattern did not match Postgres regex semantics in the failing IT, and custom trigger raises mapped to `UncategorizedSQLException`.
- **Fix:** Switched to `candidate #>> '{}'` plus a Postgres-compatible tag regex and added `USING ERRCODE = '23514'` to trigger violations.
- **Files modified:** `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml`
- **Verification:** `ChatMessageBodyBanTriggerIT`, `ChatMessageBodyBanTriggerSourceAwareIT`, and `LiquibaseMigrationTest` passed.
- **Committed in:** `4d5a5bcc`

**2. [Rule 3 - Blocking] Hardened JDBC repository mapping against driver behavior**
- **Found during:** Task 2.3 persistence verification
- **Issue:** PostgreSQL JDBC did not support `ResultSet.getObject("created_at", Instant.class)`, and body-ban exception translation only caught one Spring exception subclass.
- **Fix:** Converted timestamps through `getTimestamp(...).toInstant()` and translated any `DataAccessException` containing the trigger message to `BodyContentBanViolationException`.
- **Files modified:** `ChatMessageRowMapper.java`, `ChatMessageJdbcRepository.java`
- **Verification:** `ChatMessageJdbcRepositoryIT` passed and reloaded schemaVersion 1 envelopes.
- **Committed in:** `4d5a5bcc`

---

**Total deviations:** 2 auto-fixed blocking issues.
**Impact on plan:** Both fixes strengthened the planned privacy and persistence invariants without changing scope.

## Issues Encountered

- Running multiple `:backend:core:test` invocations concurrently caused Gradle test-result output collisions (`EOFException` / missing `in-progress-results-generic.bin`). The affected checks were rerun sequentially and passed.
- JetBrains SQL inspections did not resolve the fresh Liquibase-created `chat_message` table until runtime migrations ran. The production JDBC class uses a narrow `SqlResolve` suppression; Gradle/Testcontainers verification proves the SQL.

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.chat.persistence.*"` - passed.
- `./gradlew :backend:core:test --tests "com.zeromail.core.support.LiquibaseMigrationTest"` - passed.
- `./gradlew :backend:core:test --tests "com.zeromail.core.chat.sanitize.*" --tests "com.zeromail.core.chat.persistence.*" --tests "com.zeromail.core.support.LiquibaseMigrationTest" --tests "com.zeromail.core.arch.ChatPersistenceContentBanTest" --tests "com.zeromail.core.arch.ChatNoReactorSchedulerTest" --tests "com.zeromail.core.arch.ChatLlmAdapterBoundaryTest" --tests "com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest"` - passed.
- JetBrains project build for touched Java files - passed.
- JetBrains file inspections for touched Java files - passed.
- `git diff --check` - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 2 can now declare `core.chat`, build the use-case interfaces and Spring AI adapter, and consume the schemaVersion-aware persistence layer. The body-content ban is active at sanitizer, ArchUnit, and database trigger layers.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*
