---
status: planned
created: 2026-05-24
task: upgrade-spring-ai-to-2-0-0-m7-and-adapt
source: https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M7
---

# Quick Task: Upgrade Spring AI to 2.0.0-M7

## Scope

- Upgrade Zero Mail from Spring AI `2.0.0-M6` to `2.0.0-M7`.
- Keep Spring AI imports confined to `core.llm.gateway.springai`.
- Preserve the existing privacy contract: no prompt/completion logging and no raw email content persistence.
- Adapt code only where the M7 API or behavior changes require it.

## Release Notes Impact

- `ChatClient#prompt ignores chat options from prompt` is fixed in M7. Re-test every path that relies on per-call `OpenAiChatOptions` model, temperature, max token, or tool-choice overrides.
- `OpenAiChatOptions.AbstractBuilder#combineWith` and generic OpenAI options merging were fixed. Re-test OpenRouter/OpenAI-compatible runtime options because Zero Mail depends on per-call model overrides.
- OpenAI SDK base URL behavior documentation changed. Verify `https://openrouter.ai/api/v1` still reaches the expected `/chat/completions` endpoint shape.
- `ChatOptions` setters were removed. Confirm Zero Mail uses builders only; replace any setter usage if found.
- `ToolSpec` fluent API was introduced and `toolCallbacks(...)` is deprecated upstream directionally. Decide whether to migrate adapter tool registration now or keep the current callback path with a TODO.
- `ToolCallAdvisor` is now the default tool-call management option. Verify Zero Mail still disables internal tool execution and still enforces tool-call allow-lists after model output parsing.
- OpenAI streaming aggregation metadata and dropped chunk fixes may improve chat assistant streaming. Re-test assistant streaming with OpenRouter-compatible models.
- Gemini model defaults and Google GenAI Boot 4 support changed. Re-test Google BYOK adapter startup and a no-real-LLM unit path.

## Plan

1. Change `gradle/libs.versions.toml` Spring AI version from `2.0.0-M6` to `2.0.0-M7`.
2. Compile backend modules and fix any API breakage in `core.llm.gateway.springai`.
3. Run targeted LLM adapter tests, ArchUnit boundary tests, and backend compile/test slices.
4. Update `CLAUDE.md`, `AGENTS.md`, `.planning/research/STACK.md`, and any locked-version docs from M6 to M7 only after compile/tests pass.

## Verification

- `./gradlew.bat --no-daemon :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava`
- `./gradlew.bat --no-daemon :backend:core:test --tests "*LlmGateway*" --tests "*SpringAi*" --tests "*ChatLlmAdapterBoundaryTest*" --tests "*LlmGatewayBoundaryTest*"`
- `./gradlew.bat --no-daemon :backend:api:test --tests "*ApplicationModules*"`

## Done When

- The project builds on Spring AI `2.0.0-M7`.
- All Spring AI adapter paths still use the project-local `LlmModelClient` / `ByokLlmModelClient` seams.
- OpenRouter base URL, per-call model options, required tool calls, and streaming behavior are verified.
