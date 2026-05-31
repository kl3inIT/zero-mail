---
quick_id: 260531-tkb
status: complete
---

# Quick Task 260531-tkb — Fix rules test tabs + Inbox-Zero-style tester

## Problem (from user screenshots + logs)
- Crash: `cannot execute INSERT in a read-only transaction` when the test tab ran
  the LLM-confirm step.
- Confusing two-step UX (free deterministic run + separate "Run LLM" button) and a
  misleading "check Gmail and credits" error on every failure.
- Custom-email tester never called the LLM, so semantic rules stayed "deferred".
- LLM parse failure: model returned `{"results":...}` not `{"nodeMatches":...}`.
- User then asked to rework the Gmail tab to mirror Inbox Zero's `ProcessRules`:
  load recent emails, test all or each one individually.

## Tasks
1. Read-only-tx fix — preview entry points run read-write so credit-ledger
   settle/release (Propagation.REQUIRED) can write.
2. Custom-mail tester resolves semantic intents via one LLM call.
3. Semantic-intent system prompt pins the exact `nodeMatches` output contract.
4. Collapse the two-step preview into a single always-semantic run; drop the
   deferred CTA/stat; split credit/Gmail/server errors; surface enabled-rule count.
5. New backend endpoints: `GET /api/rules/test/messages` (free list) +
   `POST /api/rules/test/message` (per-message eval, 1 credit) via `fetchTriageInput`.
6. Frontend `GmailRuleTester` (IZ `ProcessRules` pattern): load 10/20 recent emails,
   "Test tất cả" with Stop, per-row "Test"/"Test lại" with inline verdict + actions.

## Constraints
Java 25 / Spring Boot 4 / Spring AI 2.0.0-M7; enterprise-readable names; shadcn
primitives + tokens; no global UI skill; OpenAPI regen on DTO change; no
prompt/completion logging; preview path keeps sanitized subject+flags content.
