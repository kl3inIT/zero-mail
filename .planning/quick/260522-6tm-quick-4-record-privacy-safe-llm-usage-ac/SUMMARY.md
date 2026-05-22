---
status: complete
completed: 2026-05-21
---

# Summary

Implemented privacy-safe LLM usage accounting:
- Extended `llm_call_audit` with `call_site` and `charged_credits`.
- Added `LlmUsageRecorder` and JDBC writer for metadata-only audit rows.
- Recorded platform and BYOK chat/draft/rule-compile usage after successful calls.
- Updated semantic-intent evaluation to return token usage and record metadata after settlement.
- Added assertions for platform and BYOK audit rows without storing prompts/completions.

Verification:
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.usecases.LlmGatewayCreditLifecycleTest" --tests "com.zeromail.core.llm.usecases.LlmGatewayPlatformPathTest" --tests "com.zeromail.core.billing.usecases.CreditGrantServiceTest"` passed.
