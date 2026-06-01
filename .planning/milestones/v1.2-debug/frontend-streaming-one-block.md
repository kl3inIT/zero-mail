---
status: resolved
trigger: "Frontend streaming still renders assistant response as one block"
created: "2026-05-29"
updated: "2026-05-29"
---

# Debug Session: frontend-streaming-one-block

## Symptoms

- expected_behavior: Assistant text should appear incrementally in the frontend as stream deltas arrive.
- actual_behavior: The frontend still renders the streamed assistant response as one block.
- error_messages: None reported.
- timeline: Reported after Phase 9 review/continuation work on 2026-05-29.
- reproduction: Send a chat message through the frontend streaming chat UI.

## Current Focus

- hypothesis: The stream is valid enough to finish, but backend/proxy/browser buffering plus frontend repaint throttling can make deltas arrive/render as a single visible block.
- test: Verified AI SDK UI Message Stream contract, local parser behavior, backend SSE writer, and frontend markdown response rendering.
- expecting: Start frame must carry messageId; SSE needs no-buffering/flush behavior; live assistant text should reveal incrementally even if multiple deltas are delivered together.
- next_action: Done; keep the regression tests in place.

## Evidence

- 2026-05-29: AI SDK docs and local `DefaultChatTransport`/`processUIMessageStream` source show `start` may update the assistant message id when `messageId` is present, and each `text-delta` calls the message update job immediately.
- 2026-05-29: Backend `VercelProtocolEmitter` emitted `{"type":"start"}` without `messageId`, while AI SDK custom-backend examples use `{"type":"start","messageId":"..."}`.
- 2026-05-29: Backend `ChatController` only set the Vercel protocol header; it did not set anti-buffering headers or explicitly flush the servlet response after each `SseEmitter.send`.
- 2026-05-29: Frontend `useChat` throttled message updates at 100ms and `ConversationPane` passed raw text directly to `Streamdown`, so fast/full-body-delivered chunks could paint as one block.
- 2026-05-29: Added Playwright regression where the mock API returns the full SSE body at once; UI now shows a partial prefix first and only later reveals the full assistant text.

## Eliminated

- The AI SDK parser itself does not wait for `finish` before appending text; local source appends every `text-delta` to the active text part and writes the message state.
- Spring AI `stream().chatResponse()` is the intended streaming API; docs describe emitted `ChatResponse` chunks as incremental. This bug was not fixed by changing provider APIs.

## Resolution

- root_cause: The implementation relied on ideal per-token network delivery and repaint timing. In practice, backend/proxy buffering and 100ms frontend throttling could collapse many UI Message Stream deltas into one visible render; the backend also missed the `messageId` field on the AI SDK `start` frame.
- fix: Added `messageId` to `start`, added SSE no-buffering headers and `flushBuffer()` after every backend frame, reduced frontend chat throttle to 16ms, and introduced `StreamingTextResponse` to reveal live assistant text incrementally without replaying persisted history.
- verification: `./gradlew.bat :backend:core:test --tests com.zeromail.core.chat.llm.VercelProtocolEmitterTest`; `./gradlew.bat :backend:api:test --tests com.zeromail.api.controllers.chat.ChatControllerStreamIT`; `pnpm --filter web test -- __tests__/chat/streaming-text-response.test.tsx --reporter verbose`; `pnpm --filter web lint -- e2e/chat/stream-happy-path.spec.ts features/chat/components/streaming-text-response.tsx features/chat/components/conversation-pane.tsx features/chat/hooks/use-chat.ts __tests__/chat/streaming-text-response.test.tsx`; `pnpm --filter web typecheck`; `pnpm --filter web test:e2e -- chat/stream-happy-path.spec.ts`.
- files_changed: backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java; backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java; apps/web/features/chat/hooks/use-chat.ts; apps/web/features/chat/components/conversation-pane.tsx; apps/web/features/chat/components/streaming-text-response.tsx; related unit/integration/e2e tests.
