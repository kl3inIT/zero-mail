---
phase: 02C-llm-gateway
plan: 05a
subsystem: llm-gateway
tags: [spring-ai, byok, openai-compatible, anthropic, ssrf, privacy]

requires:
  - phase: 02C-01
    provides: "BYOK persistence entities, repository, BYOKProvider enum, and RefreshTokenCipher reuse edge"
  - phase: 02C-03
    provides: "Spring-AI-free LlmGatewayImpl platform path, pure-Java LlmModelClient seam, model properties, and sanitization-first flow"
  - phase: 02C-04
    provides: "Action allow-list validation and tool-call safety path reused by BYOK responses"
provides:
  - "Pure-Java ByokLlmModelClient seam with per-call decrypted key and endpoint arguments"
  - "OpenAI-compatible and Anthropic Spring AI BYOK adapters using per-call/per-request credentials"
  - "SSRF endpoint validator for BYOK provider endpoints"
  - "LlmGatewayImpl BYOK short-circuit before the platform model path"
affects: [02C-05b, 02C-06, 02C-07, 02C-08, llm-gateway, billing, byok-rest]

tech-stack:
  added: []
  patterns:
    - "Spring AI imports stay confined to core.llm.gateway.springai adapters"
    - "BYOK plaintext keys are byte[] inputs zeroed by LlmGatewayImpl after each call"
    - "BYOK endpoints are canonicalized once and validated against HTTPS, DNS, private-address, and provider allow-list rules"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/service/ByokLlmModelClient.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokModelClient.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokModelClient.java
    - backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/byok/ByokEndpointValidatorTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java

key-decisions:
  - "Keep RefreshTokenCipher in core.gmail.persistence.crypto and inject it into LlmGatewayImpl for BYOK key-envelope decrypts."
  - "Use tenantByokCredentialsRepository instead of the plan's byokRepo literal to comply with the project no-repo-abbreviation Java naming rule."
  - "Do not extend Logback scrub filters in this plan because the BYOK call path never logs key headers, endpoint URLs, or model output content."

patterns-established:
  - "BYOK model calls are selected in LlmGatewayImpl after sanitization/tool allow-listing and before platform LlmModelClient invocation."
  - "Adapters validate endpoint policy as the first action before constructing Spring AI clients or runtime options."
  - "Plan 06 credit reserve/settle/release should wrap only the platform path; BYOK returns before that seam."

requirements-completed: [LLM-03]

duration: 47min
completed: 2026-05-07
---

# Phase 02C Plan 05a: BYOK Gateway Internals Summary

**BYOK gateway routing with per-call Spring AI credentials, endpoint SSRF validation, and metadata-only call logging.**

## Performance

- **Duration:** 47 min
- **Started:** 2026-05-07T14:37:11Z
- **Completed:** 2026-05-07T15:23:41Z
- **Tasks:** 1 TDD task
- **Files modified:** 8

## Accomplishments

- Added `ByokLlmModelClient` as a pure-Java service seam and implemented OpenAI-compatible plus Anthropic adapters under `core.llm.gateway.springai`.
- Added `ByokEndpointValidator` to reject non-HTTPS, userinfo/query/fragment, DNS-resolved private/link-local/loopback/metadata addresses, and non-vendor hosts unless operator opt-in allows public non-vendor endpoints.
- Updated `LlmGatewayImpl.chat()` so a tenant BYOK row routes through the matching BYOK client before the platform path, with `RefreshTokenCipher.decrypt(..., tenantId)` and `Arrays.fill(decryptedKey, 0)` in `finally`.
- Added focused tests for BYOK routing, platform fall-through, key AAD/decrypt behavior, log redaction, multi-tenant leakage, and endpoint rejection/opt-in behavior.

## Task Commits

1. **RED: BYOK routing and endpoint tests** - `3ad1957` (`test`)
2. **GREEN: BYOK gateway routing** - `ce485ea` (`feat`)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/llm/service/ByokLlmModelClient.java` - Pure-Java BYOK model-client seam.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokModelClient.java` - OpenAI-compatible adapter using `OpenAiApi.mutate()`, `OpenAiChatModel.mutate()`, and `ChatClient.create()`.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokModelClient.java` - Anthropic adapter using `AnthropicChatOptions.builder().apiKey().baseUrl()` as per-request options.
- `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java` - BYOK endpoint canonicalization and SSRF allow-list validator.
- `backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java` - Redacted no-payload BYOK validation exception.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` - BYOK row lookup, decrypt, adapter selection, key zeroing, and BYOK metadata logs.
- `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java` - Routing, privacy, AAD, and multi-tenant leakage coverage.
- `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokEndpointValidatorTest.java` - Endpoint policy coverage.

## Decisions Made

- Spring AI M4 seam details were verified through Context7 and local compilation. Final imports used are `org.springframework.ai.openai.api.OpenAiApi`, `org.springframework.ai.openai.OpenAiChatModel`, `org.springframework.ai.openai.OpenAiChatOptions`, `org.springframework.ai.anthropic.AnthropicChatOptions`, and `org.springframework.ai.chat.client.ChatClient`.
- `RefreshTokenCipher` remains referenced from `core.gmail.persistence.crypto`; no relocation was needed because Plan 01 already declared the allowed dependency edge.
- Endpoint host extraction uses `URI.create(canonicalEndpoint).getHost()` after `endpoint.trim().replaceAll("/+$", "")`; there is no regex for host extraction. Missing scheme, userinfo, query string, or fragment is rejected before DNS resolution.
- Logback scrub filters were not extended in this plan. The new BYOK logs contain tenant/provider/model/tokens/latency/truncation only, and tests assert no key bytes, endpoint URL, or output content appear.
- Plan 06 should add `creditLedger.reserve / settle / release` around the platform call site only; the BYOK branch intentionally returns before that seam for BYOK billing skip.
- Plan 08 should regenerate `apps/web/lib/api/schema.d.ts` via `pnpm generate:api` after Plan 05b lands the BYOK REST endpoints.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Project Convention] Replaced plan-local `byokRepo` naming with explicit repository naming**
- **Found during:** Task 1
- **Issue:** The plan acceptance grep expected `byokRepo.findByTenantId`, but AGENTS/PROJECT Java style explicitly forbids `repo` abbreviations.
- **Fix:** Implemented the same lookup as `tenantByokCredentialsRepository.findByTenantId(tenantId)` and verified the equivalent acceptance grep.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`
- **Verification:** `grep -c "tenantByokCredentialsRepository.findByTenantId" ...` returned `1`; focused BYOK and gateway tests passed.
- **Committed in:** `ce485ea`

---

**Total deviations:** 1 auto-fixed convention/security-safety adjustment.
**Impact on plan:** Behavior matches the plan while complying with the project-wide Java naming rule.

## Acceptance Results

- `ByokLlmModelClient.java` exists, has one `LlmChatResult call(...)`, and has zero `org.springframework.ai` imports.
- Both Spring AI adapters implement `ByokLlmModelClient`; `OpenAiCompatibleByokModelClient` contains `OpenAiApi...mutate`; `AnthropicByokModelClient` contains `AnthropicChatOptions.builder`.
- Gateway BYOK branch has repository lookup, cipher decrypt, BYOK start/success logs, no `decryptedKey` logging, and `Arrays.fill(decryptedKey, ...)`.
- Endpoint validator blocked-literal grep returned `6` lines after documenting each blocked range separately.
- The plan literal `byokRepo.findByTenantId` intentionally returns `0`; the AGENTS-compliant equivalent `tenantByokCredentialsRepository.findByTenantId` returns `1`.

## Verification

- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "LlmGatewayByokRoutingTest" --tests "ByokEndpointValidatorTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayBoundaryTest"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "LlmGatewayByokRoutingTest" --tests "LlmGateway*" --tests "ByokEndpointValidatorTest" --tests "DomainBoundaryArchTests"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:worker:test`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:api:testClasses`
- Timed out: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test :backend:api:test :backend:worker:test` after 10 minutes without output.
- Timed out: `./gradlew.bat --no-daemon --max-workers=1 :backend:api:test` after 7 minutes without output.

## Known Stubs

None. Stub scan matches were false positives from null guards, local-reference cleanup, structured log placeholders, and test JSON payloads.

## Threat Flags

None beyond the planned BYOK outbound endpoint/key boundary already covered by T-2C-03 and T-2C-09.

## Issues Encountered

- Full backend test execution is currently not practical in this environment: combined backend tests and standalone API tests timed out. Plan-owned core tests, gateway boundary tests, worker tests, and API test compilation passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 05b. The gateway internals now expose the validator/exception/client seams that the REST BYOK validate/save/current surface can use.

## Self-Check: PASSED

- Verified summary and all created files exist on disk.
- Verified task commits `3ad1957` and `ce485ea` exist in git history.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-07*
