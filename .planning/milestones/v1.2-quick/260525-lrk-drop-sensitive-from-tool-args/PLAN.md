---
quick_id: 260525-lrk
slug: drop-sensitive-from-tool-args
date: 2026-05-25
status: in-progress
---

# Quick Task: Drop Sensitive<String> wrapper from sendaction tool args body

## Problem (CRITICAL — real outbound emails were corrupted)

User reported that an email sent via the chat assistant arrived in Gmail with the literal body `{value=Chào bạn, ... Thân mến,}` — i.e., a `Map.toString()` representation of an object instead of the actual email body.

Root cause: `SendEmailToolArgs.body`, `ReplyEmailToolArgs.body`, and `ForwardEmailToolArgs.additionalBody` were typed `Sensitive<String>`. `Sensitive` is itself a Java record `public record Sensitive<T>(T value)`. Spring AI's `JsonSchemaGenerator.generateForType(...)` (in `ToolCallbackTranslator.java:36`) descends into the record's components, so the JSON Schema delivered to the LLM looked like:

```json
{
  "properties": {
    "to":      { "type": "string" },
    "subject": { "type": "string" },
    "body":    { "type": "object", "properties": { "value": { "type": "string" } } }
  }
}
```

The LLM correctly fills the contract: `body: { "value": "Chào bạn, ..." }`. The tool call payload is then JSON-parsed into `Map<String, Object>` on the backend and stored in `reservation.inputJson`. `ConfirmedSendToolHandlers.body(...)` reads it via `WriteToolArguments.text(effectiveInput, "body")`, which calls `.toString()` on whatever value is at key `"body"` — that's a `LinkedHashMap` `{"value": "Chào bạn, ..."}` whose `toString()` returns `{value=Chào bạn, ...}`. That string is then wrapped in `Sensitive.of(...)`, passed to `AssistantSendCommand`, unwrapped in `GmailMessageBuilder`, and sent via Gmail.

## Fix

Drop `Sensitive<String>` from the LLM-facing tool args records — body is user-authored draft data, which the CLAUDE.md privacy scope explicitly carves out as persistable:

> Draft-body carve-out (chat assistant send/reply/forward): the assistant-drafted send/reply/forward body that the user reviews on the preview card before clicking Send IS persistable in `chat_message.parts` and `assistant_pending_action` for the lifetime of the conversation — it is user-authored draft data...

`Sensitive<String>` continues to wrap the body inside `AssistantSendCommand` (internal command record) so any internal logging path that String.valueOf's the command still gets `***REDACTED***`. The wrapper is also re-applied at `ConfirmedSendToolHandlers.toCommand(...)` via the existing `Sensitive.of(body(...))` line — no change needed there.

Files changed:

- `SendEmailToolArgs.java` — body: `Sensitive<String>` → `String`. Removed companion constructor (no longer needed).
- `ReplyEmailToolArgs.java` — body: `Sensitive<String>` → `String`. Removed companion constructor.
- `ForwardEmailToolArgs.java` — additionalBody: `Sensitive<String>` → `String`. Removed companion constructor.

Unchanged:

- `AssistantSendCommand.body` stays `Sensitive<String>` (internal command, not LLM-facing).
- `GmailMessageBuilder` keeps `command.body().value()` unwrapping.
- `ConfirmedSendToolHandlers` keeps `Sensitive.of(body(...))` wrap step.

## Verification

- `./gradlew :backend:core:compileJava :backend:core:compileTestJava` — pass.
- `mcp__jetbrains__get_file_problems` on all 3 changed files — clean.
- Manual browser verification: restart backend, send a real email through the assistant, confirm Gmail receives the actual body text (not `{value=...}`).
