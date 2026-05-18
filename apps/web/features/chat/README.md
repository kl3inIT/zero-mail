# Chat Feature

`/chat` uses the Vercel AI SDK UI message protocol through `DefaultChatTransport`.
The transport body is adapted to the Spring endpoint shape:
`{ chatId, userText }`.

`reconnectToStream` is intentionally not surfaced in the UI for Phase 7. The
phase plan locks stream resume off because the current AI SDK behavior is not a
product requirement and failed reconnects could confuse confirmation state.
Cancel remains a local Stop button only; history replay comes from PostgreSQL via
`GET /api/chat/{id}`.

Preview cards render from persisted `chat_message.parts` and, for send/reply/
forward replay, draft body fields in the tool input or pending-action snapshot.
The frontend never reloads Gmail or Redis to reconstruct a replay card.
