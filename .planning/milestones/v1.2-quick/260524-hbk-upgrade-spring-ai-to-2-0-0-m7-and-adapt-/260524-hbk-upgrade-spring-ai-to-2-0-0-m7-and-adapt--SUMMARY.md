---
status: complete
completed: 2026-05-24
task: upgrade-spring-ai-to-2-0-0-m7-and-adapt
source: https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M7
---

# Summary

Upgraded Zero Mail's Spring AI dependency from `2.0.0-M6` to `2.0.0-M7`.

## Changes

- Updated `gradle/libs.versions.toml` to Spring AI `2.0.0-M7`.
- Migrated platform and BYOK `ChatClient` adapter tool registration from deprecated `toolCallbacks(...)` to M7 `tools(toolSpec -> toolSpec.callbacks(...))`.
- Updated focused Spring AI adapter tests to mock the M7 `tools(...)` API.
- Excluded Google GenAI embedding auto-configuration in API, worker, and Postgres-backed tests because Zero Mail does not use embeddings and M7 now eagerly validates embedding connection details.
- Synced Spring AI version references in `CLAUDE.md`, `AGENTS.md`, `.planning/research/STACK.md`, and `TESTING.md`.

## Verification

- `./gradlew.bat --no-daemon :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava`
- `./gradlew.bat --no-daemon :backend:core:test --tests "*LlmGateway*" --tests "*SpringAi*" --tests "*ChatLlmAdapterBoundaryTest*" --tests "*LlmGatewayBoundaryTest*"`
- `./gradlew.bat --no-daemon :backend:api:test --tests "*ApplicationModules*"`

## Notes

- Streaming chat still uses `OpenAiChatOptions.toolCallbacks(...)` in `Prompt` options because that is the Spring AI `StreamingChatModel` path, not the deprecated `ChatClientRequestSpec.toolCallbacks(...)` path.
- No Spring Modulith boundary violations were introduced.
