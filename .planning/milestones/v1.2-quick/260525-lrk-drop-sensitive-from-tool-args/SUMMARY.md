---
quick_id: 260525-lrk
slug: drop-sensitive-from-tool-args
date: 2026-05-25
status: complete
---

# Summary: Drop Sensitive<String> wrapper from sendaction tool args body

## What changed

- `SendEmailToolArgs.body`: `Sensitive<String>` → `String`. Companion constructor removed.
- `ReplyEmailToolArgs.body`: `Sensitive<String>` → `String`. Companion constructor removed.
- `ForwardEmailToolArgs.additionalBody`: `Sensitive<String>` → `String`. Companion constructor removed.

## Root cause recap

`Sensitive<T>` is a record `record Sensitive<T>(T value)`. Spring AI's `JsonSchemaGenerator` (called in `ToolCallbackTranslator:36`) descends into record components, so the body field appeared in the JSON Schema as `{"type":"object","properties":{"value":"string"}}`. The LLM emitted `body: {"value": "..."}`, the backend stored it as a `LinkedHashMap` in `reservation.inputJson`, and `WriteToolArguments.text(input, "body")` called `.toString()` on the map — producing `{value=...}` (literal Map.toString format), which then went out via Gmail send.

Body is user-authored draft data, explicitly carved out as persistable in CLAUDE.md privacy scope, so the Sensitive wrapper at the LLM-facing boundary served no purpose.

## What's untouched (intentional)

- `AssistantSendCommand.body` keeps `Sensitive<String>` — internal command, never serialized to JSON Schema.
- `GmailMessageBuilder` keeps `command.body().value()` unwrapping.
- `ConfirmedSendToolHandlers` keeps `Sensitive.of(body(...))` wrap at the boundary into AssistantSendCommand.

## Verification

- `./gradlew :backend:core:compileJava :backend:core:compileTestJava` — pass.
- `mcp__jetbrains__get_file_problems` on all 3 changed files — clean.
- Manual: user verified the broken `{value=...}` body symptom; will re-test after restart.
