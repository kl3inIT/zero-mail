---
status: in_progress
created: 2026-05-21
---

# Quick 4: Privacy-Safe LLM Usage Accounting

Goal: write metadata-only LLM usage records without prompt, completion, email body, request body, or response body persistence.

Steps:
- Extend `llm_call_audit` with `call_site` and `charged_credits`.
- Add a low-level writer for tenant/model/call-site/token/credential-source metadata.
- Record platform and BYOK chat/draft/rule-compile usage from `LlmUsage`.
- Return usage from semantic-intent evaluation and record it after ledger settlement.
- Add tests proving writes contain metadata and distinguish platform/BYOK charged credits.
