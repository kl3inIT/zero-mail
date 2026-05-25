---
quick_id: 260525-kij
slug: fix-sendemail-schema-prompt
date: 2026-05-25
status: complete
---

# Summary: Fix sendEmail tool not callable + assistant placeholder narration

## What changed

- `SendEmailToolArgs.java` — dropped dead `replyToMessageId` field. Record arity now 3 (`to`, `subject`, `body`). No downstream code reads this field; it only existed in the record's own validation.
- `ChatToolCatalog.java` — rewrote tool descriptions for `SEND_EMAIL` / `REPLY_EMAIL` / `FORWARD_EMAIL` so the LLM sees clear guidance on which to call, and is explicitly told NOT to narrate placeholders in plain text.
- `XmlFencedPersonalizationRenderer.java` — added a new "Tool invocation policy (load-bearing)" section to the chat system prompt. Forbids plain-text drafts with placeholders, mandates natural-language summary after read tools.

## Root cause

Spring AI's `JsonSchemaGenerator.generateForType(SendEmailToolArgs.class)` (called from `ToolCallbackTranslator.java:36`) emits a JSON Schema with every Java `String` field marked `required`. `replyToMessageId` was required-non-blank in the record but never consumed by the executor (`AssistantSendCommand` only has `to/subject/body`). The LLM, asked to send a brand-new email with no thread context, could not produce a valid `replyToMessageId` and so skipped the `sendEmail` tool entirely — falling back to narrating the email in plain assistant text with bracketed placeholders, which bypasses the editable PreviewCard UI.

## Verification

- `./gradlew :backend:core:compileJava` — pass.
- `./gradlew :backend:core:compileTestJava` — pass (no test referenced `replyToMessageId`).
- `mcp__jetbrains__get_file_problems` on all 3 changed files — clean.
- Full test suite deferred to CI (per user preference).

## Not done in this task

- Frontend input/button UI bugs → QT2.
- Per-tool UI components (read + writeReversible JSON dump) → QT3.
