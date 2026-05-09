---
phase: 03-rules-engine
status: in_progress
updated: 2026-05-09
privacy: verified
architecture: verified
---

# Phase 03 Verification

This report records the closure evidence for the rules engine phase. It is intentionally privacy-safe: examples and notes refer only to synthetic rule text, sanitized metadata field names, aggregate counts, and test names.

## Privacy and Architecture Closure

### Automated Checks

| Check | Command | Result |
| --- | --- | --- |
| LLM gateway, rules boundary, repository content ban, preview privacy tests | `.\gradlew.bat :backend:core:test --tests "LlmGatewayBoundaryTest" --tests "RulesBoundaryArchTest" --tests "LlmRepositoryContentBanTest" --tests "com.zeromail.core.rules.privacy.*"` | PASS in 48s |
| Spring AI/vendor imports in `core.rules` | `rg -n "org\.springframework\.ai|com\.openai|com\.anthropic" backend/core/src/main/java/com/zeromail/core/rules` | PASS: no matches |
| Gmail write/execution references in `core.rules` | `rg -n "core\.gmail\.(write|execution)|core\.triage\.(execution|actions)|users\.messages|users\.drafts|drafts\.create|messages\.modify|messages\.trash|messages\.send" backend/core/src/main/java/com/zeromail/core/rules` | PASS: no matches |
| Logging statements in rules/API/UI surface | `rg -n "log\.|Logger|LoggerFactory" backend/core/src/main/java/com/zeromail/core/rules backend/api/src/main/java/com/zeromail/api/controllers/rules apps/web/features/rules` | PASS: only sanitized compile/materialization event logs; no content-bearing arguments |
| Durable/content-sensitive field scan | `rg -n "prompt|completion|toolArguments|sourceText|messageBody|emailBody|rawHtml|rawEmail|decryptedKey|apiKey" backend/core/src/main/java/com/zeromail/core/rules/persistence backend/core/src/main/java/com/zeromail/core/rules/model backend/api/src/main/java/com/zeromail/api/controllers/rules apps/web/features/rules` | PASS after review: matches are rule author `sourceText`, request-body plumbing, tests, and validator reject-list names; no raw Gmail body, prompts, completions, tool args, token bytes, or keys are persisted/logged |
| Semantic matcher deferral visibility | `rg -n "SEMANTIC_INTENT|semantic" backend/core/src/main/java/com/zeromail/core/rules backend/api/src/main/java/com/zeromail/api/controllers/rules apps/web/features/rules` | PASS: `SEMANTIC_INTENT` is present, rejected unless deferred, and surfaced as a deferred preview state |

### Findings

- `core.rules` calls the project LLM gateway only through pure domain/service types; Spring AI and vendor SDK imports remain confined to the LLM adapter package.
- Preview services read sanitized Gmail preview metadata through `GmailPreviewReadService` and do not depend on Gmail write or action-execution packages.
- Rule persistence stores user-authored rule source text, normalized matcher AST, action intents, status, ordering, and template provenance. It does not store raw Gmail body content, prompts, completions, tool arguments, token bytes, or decrypted keys.
- Rules logs are event-style metadata logs using `event=<name> tenantId={}` shape. They do not include Gmail subject, body, prompt, completion, tool-argument, or token values.
- `SEMANTIC_INTENT` remains a Phase 4 boundary: Phase 3 can display/defer it, but deterministic evaluation does not claim semantic LLM matching.

### Residual Risk

| Risk | Owner | Follow-up |
| --- | --- | --- |
| Synthetic and architecture checks prove boundaries, not live production-model semantic quality. | Phase 4 triage | Run live semantic rule evaluation and shadow-mode quality checks before enabling runtime triage. |
