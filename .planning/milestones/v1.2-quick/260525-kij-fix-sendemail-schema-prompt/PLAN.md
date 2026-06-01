---
quick_id: 260525-kij
slug: fix-sendemail-schema-prompt
date: 2026-05-25
status: in-progress
---

# Quick Task: Fix sendEmail tool not callable + assistant placeholder narration

## Problem

Two coupled bugs that prevent the chat assistant from invoking write-action tools, observed live by user:

**Bug A — `SendEmailToolArgs.replyToMessageId` is required but dead:**

`backend/core/.../sendaction/SendEmailToolArgs.java:7-14` declares `replyToMessageId` as a non-blank `String` field. Spring AI's `JsonSchemaGenerator.generateForType(...)` (in `ToolCallbackTranslator.java:36`) emits a JSON Schema with all 4 String fields marked `required`. The LLM, asked to send a brand-new email (no thread context), cannot construct a valid value for `replyToMessageId` and so silently skips the `sendEmail` tool, falling back to plain-text narration with placeholders like `[Điền ngày, giờ]`, `[Họ tên của bạn]`.

Grep confirms `replyToMessageId` is **never read** anywhere in `backend/core/src/main` outside the record itself — `AssistantSendCommand` only has `to / subject / body`. Dead field.

Compare inbox-zero `sendEmailToolInputSchema` (`apps/web/utils/ai/assistant/chat-inbox-tools.ts:103-113`): only `to / cc / bcc / subject / messageHtml` — no `messageId`. `replyEmail` has its own `messageId` field.

**Bug B — System prompt does not steer the model toward tool invocation:**

`XmlFencedPersonalizationRenderer.java:33-56` only enforces confirmation policy. There is no rule that says "when the user expresses send/reply/forward/draft intent, invoke the tool — do not narrate the email in plain text". The model defaults to describing the email with bracketed placeholders, which bypasses the editable PreviewCard UI entirely.

## Fix

**Change 1** — `backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/SendEmailToolArgs.java`:
Drop `replyToMessageId` from the record header, the compact-constructor validation, and the convenience constructor.

**Change 2** — `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatToolCatalog.java`:
Rewrite descriptions for `SEND_EMAIL`, `REPLY_EMAIL`, `FORWARD_EMAIL` so they steer the LLM toward the correct tool given thread context, and explicitly forbid plain-text drafts.

**Change 3** — `backend/core/src/main/java/com/zeromail/core/chat/sanitize/XmlFencedPersonalizationRenderer.java`:
Insert a "## Tool invocation policy (load-bearing)" section after the existing confirmation policy and before the personalization block. Forbids plain-text drafts with placeholders. Forces natural-language summary after read tools.

## Verification

- `./gradlew :backend:core:compileJava` — compile passes (record arity change has no callers).
- `mcp__jetbrains__get_file_problems` on 3 changed files — clean.
- Push and let CI run full suite (per user preference).

## Out of scope

- Frontend changes (input clear, button styling, dead Confirm) → QT2.
- Per-tool UI components (JSON-dump fix for read + writeReversible) → QT3.
- Adding any new field to `SendEmailToolArgs` (e.g., `cc`, `bcc`) — not requested, do not scope-creep.
