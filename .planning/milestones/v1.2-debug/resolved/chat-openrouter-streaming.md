---
status: resolved
trigger: "Backend chat must call OpenRouter/Spring AI streaming directly; current chat stream hangs or fails when using OpenRouter models."
created: 2026-05-19
updated: 2026-05-19
---

# Debug Session: Chat OpenRouter Streaming

## Symptoms

- Expected behavior: backend `/api/chat` uses real streaming model calls and emits Vercel UI message stream frames until assistant text/tool calls finish.
- Actual behavior: after submitting chat, UI stays in streaming state (`Dừng phản hồi`) or previously emitted `The assistant stream failed`.
- Error messages: dev log previously showed `BadRequestException: 400: Provider returned error`; latest run with `openai/gpt-5.4-nano` logs `event=chat_llm_stream_start` but no finish/error.
- Timeline: started while testing Phase 7 chat route after API/worker restart and OpenRouter model changes.
- Reproduction: run API dev on `:8080`, web production on `:3000`, Google login, submit a `/chat` message.

## Current Focus

- hypothesis: OpenRouter rejects Spring AI's generated JSON Schema for no-argument chat tools because the root object schema lacks `properties`.
- test: normalize no-argument object schemas to include `properties: {}`, then run Spring AI streaming probes and browser chat verification.
- expecting: Spring AI streaming succeeds with the full Zero Mail tool catalog and `/api/chat` emits a 200 text/event-stream response that renders in the UI.
- next_action: none
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-05-19T03:05+07:00
  observation: Playwright `/chat` POST returned 200 SSE but only user message persisted, then assistant error in earlier run.
- timestamp: 2026-05-19T03:05+07:00
  observation: API dev log showed `event=chat_llm_stream_start ... modelId=openai/gpt-5.4-nano` followed by `BadRequestException: 400: Provider returned error` in earlier run.
- timestamp: 2026-05-19T03:17+07:00
  observation: Direct OpenRouter HTTP streaming for `openai/gpt-5.4-nano` with `temperature`, `max_tokens`, `stream_options.include_usage=false`, and simple tools returned HTTP 200.
- timestamp: 2026-05-19T03:21+07:00
  observation: Latest backend run with `openai/gpt-5.4-nano` logs `event=chat_llm_stream_start` and UI remains in streaming state; no backend finish/error log appears.
- timestamp: 2026-05-19T03:21+07:00
  observation: Direct OpenRouter `openai/gpt-5.4` returned 404 for this local key/policy: no endpoints matching guardrail/data policy.
- timestamp: 2026-05-19T03:24+07:00
  observation: JVM probe using Spring AI/OpenAI Java SDK streaming with `openai/gpt-5.4-nano` and no tools passed.
- timestamp: 2026-05-19T03:24+07:00
  observation: JVM probe using Spring AI/OpenAI Java SDK streaming with `openai/gpt-5.4-nano` and the full Zero Mail tool catalog failed with `BadRequestException: 400 Provider returned error`.
- timestamp: 2026-05-19T03:34+07:00
  observation: Individual tool probe isolated failures to `listLabels` and `listRules`, both backed by empty args records.
- timestamp: 2026-05-19T03:36+07:00
  observation: Spring AI generated no-arg tool schema as an object with `additionalProperties: false` but no `properties` member.
- timestamp: 2026-05-19T03:37+07:00
  observation: Direct OpenRouter request with that exact schema reproduced HTTP 400: object schema missing properties for function `listLabels`; adding `properties: {}` made the request succeed.
- timestamp: 2026-05-19T03:43+07:00
  observation: Added backend schema normalization in `ToolCallbackTranslator` and a regression test for empty-record tool args.
- timestamp: 2026-05-19T03:44+07:00
  observation: Manual Spring AI/OpenRouter streaming probe passed for no tools, full Zero Mail tool catalog, and each individual tool.
- timestamp: 2026-05-19T03:49+07:00
  observation: Playwright verified `/chat` after Google login; `/api/chat` returned 200 `text/event-stream` and assistant text rendered in the page.
- timestamp: 2026-05-19T04:02+07:00
  observation: Fixed frontend new-chat URL race by waiting for persistence ack before replacing the route; rebuilt/restarted web production and Playwright showed no console errors, chat detail 200, and persisted USER/ASSISTANT message pair.

## Eliminated

- hypothesis: frontend route/session is missing
  reason: `/chat` loads after Google login; `/api/chat` POST reaches backend and starts model stream.
- hypothesis: model ID `openai/gpt-5.4-nano` is unavailable on OpenRouter
  reason: direct OpenRouter model list contains it and direct streaming request returns HTTP 200.
- hypothesis: `temperature`, `max_tokens`, or basic `stream_options` alone break OpenRouter
  reason: direct OpenRouter requests with those fields returned HTTP 200.
- hypothesis: Spring AI/OpenAI Java SDK streaming is categorically incompatible with OpenRouter
  reason: the same SDK streaming path passes when no tools are registered.

## Resolution

- root_cause: OpenRouter requires object JSON Schemas to include a `properties` member. Spring AI generated no-argument record schemas without `properties`, so OpenRouter rejected streaming requests whenever `listLabels` or `listRules` were included in the tool catalog.
- fix: Keep the backend chat path on `StreamingChatModel.stream(...)`; normalize generated object tool schemas in `ToolCallbackTranslator` by adding `properties: {}` when the root schema is an object and the member is missing.
- verification: `ToolCallbackTranslatorTest`, targeted chat backend tests, manual Spring AI/OpenRouter streaming probe, `pnpm --filter web typecheck`, `pnpm --filter web lint`, `pnpm --filter web build`, and Playwright `/chat` browser test passed.
- files_changed: `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/ToolCallbackTranslator.java`, `backend/core/src/test/java/com/zeromail/core/chat/llm/springai/ToolCallbackTranslatorTest.java`, `backend/core/src/test/java/com/zeromail/core/chat/llm/springai/OpenRouterStreamingProbeTest.java`, `apps/web/features/chat/components/conversation-pane.tsx`, `CONVENTIONS.md`, `AGENTS.md`, `CLAUDE.md`.
