---
phase: 02C-llm-gateway
plan: 02
subsystem: llm
tags: [sanitization, jsoup, unicode, jtokkit, prompt-injection, privacy-logging]

requires:
  - phase: 02C-01
    provides: Spring AI and jtokkit dependencies, LLM package skeleton, prompt-injection fixtures, Wave 0 sanitization scaffold
provides:
  - Ordered Jsoup -> NFC -> Unicode-control-strip -> jtokkit truncation sanitization pipeline
  - SanitizationContext metadata record and content-safe SanitizationException
  - Per-step, pipeline, corpus, and Wave 0 sanitization tests
affects: [02C-03, 02C-04, llm-gateway, triage, rules-engine]

tech-stack:
  added: []
  patterns:
    - Ordered Spring List<Sanitizer> fold with @Order values 10/20/30/40
    - Metadata-only sanitization logging through TenantContext
    - jtokkit CL100K_BASE hard-cap truncation at 3896 tokens

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/Sanitizer.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JsoupHtmlStripSanitizer.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/NfcNormalizeSanitizer.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/UnicodeTagStripSanitizer.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitConfig.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitTruncateSanitizer.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationException.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/JsoupHtmlStripSanitizerTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/NfcNormalizeSanitizerTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/UnicodeTagStripSanitizerTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/JtokkitTruncateSanitizerTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipelineTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/PromptInjectionCorpusTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationContext.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipelineWave0Test.java

key-decisions:
  - "UnicodeTagStripSanitizer strips [\\x{E0000}-\\x{E007F}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF], covering Unicode tag characters, zero-width controls, LRM/RLM, bidi overrides/isolates, and BOM."
  - "JtokkitConfig exposes a singleton EncodingRegistry bean; JtokkitTruncateSanitizer uses EncodingType.CL100K_BASE from jtokkit 1.1.0 with HARD_CAP_TOKENS=3896."
  - "SanitizationException uses a null message plus cause to avoid inheriting any cause message that might contain email bytes."

patterns-established:
  - "SanitizationPipeline constructor defensively sorts List<Sanitizer> with AnnotationAwareOrderComparator before folding steps."
  - "PromptInjectionCorpusTest decodes escaped Unicode fixture code units and expands the compact over-budget seed before running the real pipeline."

requirements-completed: [LLM-05, LLM-06, LLM-07, LLM-08]

duration: 13min
completed: 2026-05-07
---

# Phase 02C Plan 02: Sanitization Pipeline Summary

**Prompt-injection sanitization wall with ordered Jsoup, NFC, Unicode-control stripping, and jtokkit CL100K_BASE truncation.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-05-07T12:29:48Z
- **Completed:** 2026-05-07T12:43:00Z
- **Tasks:** 2
- **Files modified:** 16

## Accomplishments

- Replaced the Plan 01 sanitization placeholders with `SanitizationPipeline`, `Sanitizer`, four ordered sanitizer beans, `SanitizationContext`, and `SanitizationException`.
- Added zero-arg unit tests for each sanitizer step plus pipeline tests for ordering, fail-fast wrapping, and metadata-only logging.
- Exercised all five prompt-injection fixtures end-to-end and re-enabled the Plan 01 Wave 0 scaffold.

## Task Commits

1. **Task 1 RED:** `7e60e56` test(02C-02): add failing sanitizer step tests
2. **Task 1 GREEN:** `a789fd2` feat(02C-02): implement sanitizer step pipeline components
3. **Task 2 RED:** `781498a` test(02C-02): add failing sanitization pipeline tests
4. **Task 2 GREEN:** `7ee254b` feat(02C-02): implement sanitization pipeline orchestrator

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/Sanitizer.java` - Functional interface for pipeline steps.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java` - Ordered service fold, fail-fast wrapping, metadata-only log.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JsoupHtmlStripSanitizer.java` - `@Order(10)` Jsoup `Safelist.none()` stripping.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/NfcNormalizeSanitizer.java` - `@Order(20)` NFC normalization.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/UnicodeTagStripSanitizer.java` - `@Order(30)` hidden Unicode/control stripping.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitConfig.java` - Singleton `EncodingRegistry` bean.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitTruncateSanitizer.java` - `@Order(40)` `CL100K_BASE` token-boundary truncation.
- `backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationContext.java` - Metadata record with defensive `stepMetadata` copy.
- `backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationException.java` - Content-safe runtime exception carrying `stepName` and cause.
- `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/*Test.java` - Step, pipeline, corpus, and Wave 0 tests.

## Decisions Made

- Final Unicode strip pattern: `[\\x{E0000}-\\x{E007F}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]`.
- jtokkit is the already-pinned `1.1.0`; truncation uses `EncodingType.CL100K_BASE` through a singleton `@Configuration` bean (`JtokkitConfig#encodingRegistry`).
- `NfcNormalizeSanitizer` did not require special surrogate-pair handling; Java `Normalizer.normalize(..., Form.NFC)` leaves non-combining supplementary pairs intact, and the jtokkit step owns token-boundary/multibyte safety.
- Sample log shape from tests: `event=sanitization_completed tenantId=00000000-0000-0000-0000-000000000001 truncated=false tokenCount=1`.
- Plan 03 should inject `SanitizationPipeline` into `LlmGatewayImpl` and call `sanitize(rawHtml)` as the first operation inside the tenant-bound gateway entry, before constructing any Spring AI request or model prompt.

## Verification

- PASS: `bash -lc "cmd.exe /c gradlew.bat :backend:core:test --tests *Sanitiz* --tests PromptInjectionCorpusTest"`
- PASS: `bash -lc "cmd.exe /c gradlew.bat :backend:core:test --tests LlmGatewayBoundaryTest"`
- PASS: `bash -lc "cmd.exe /c gradlew.bat :backend:core:test"`
- PASS: Task 1 grep acceptance checks for `@Order`, `Safelist.none()`, `Form.NFC`, `CL100K_BASE`, `HARD_CAP_TOKENS = 3896`, and no content-bearing `SanitizationException` super call.
- PASS: Task 2 grep acceptance checks for `List<Sanitizer>`, `event=sanitization_completed`, no `content()` logging, test counts, and no remaining `@Disabled` in the Wave 0 test.
- PASS: JetBrains file-problem checks and targeted file rebuild for edited Java files.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Privacy] SanitizationException message could inherit cause text**
- **Found during:** Task 2 (pipeline fail-fast implementation)
- **Issue:** The plan's `super(cause)` shape allows `RuntimeException#getMessage()` to inherit `cause.toString()`, which can include a sanitizer failure message.
- **Fix:** Used `super(null, cause)` so the exception has no message while preserving `getCause()`.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationException.java`
- **Verification:** Targeted pipeline tests pass; grep confirms no content-bearing super call.
- **Committed in:** `7ee254b`

**2. [Rule 1 - Test Fixture Bug] Corpus fixtures needed decoding/expansion to exercise intended hostility**
- **Found during:** Task 2 (corpus test implementation)
- **Issue:** Plan 01 fixtures store Unicode controls as escaped `\uXXXX` code units and the over-budget fixture as a compact seed, so a naive load would not exercise hidden controls or truncation.
- **Fix:** `PromptInjectionCorpusTest` decodes escaped Unicode code units for the relevant fixtures and repeats the over-budget seed before sanitizing.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/PromptInjectionCorpusTest.java`
- **Verification:** `PromptInjectionCorpusTest` passes and asserts stripped controls plus `truncated=true`.
- **Committed in:** `781498a`

**3. [Rule 3 - Blocking] Re-enabled Wave 0 SpringBootTest had no discoverable core test configuration**
- **Found during:** Task 2 verification
- **Issue:** Removing `@Disabled` exposed a Spring Boot initialization error because the test package cannot discover a `@SpringBootConfiguration` upward.
- **Fix:** Pinned `@SpringBootTest(classes = {...})` to the minimal sanitizer context.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipelineWave0Test.java`
- **Verification:** `SanitizationPipelineWave0Test` passes with the plan-level sanitization subset.
- **Committed in:** `7ee254b`

---

**Total deviations:** 3 auto-fixed (1 missing critical privacy, 1 fixture bug, 1 blocking test configuration)
**Impact on plan:** All fixes preserve the intended sanitization scope and strengthen privacy/test fidelity without adding Wave 3 gateway, BYOK, REST, credit, or frontend behavior.

## Known Stubs

None.

## Issues Encountered

- Git Bash could not execute the CRLF `gradlew` script directly. Verification was run from Git Bash through `cmd.exe /c gradlew.bat ...`, which satisfies the plan's Git Bash shell requirement while using the Windows wrapper.
- One initial quoted `--tests "*Sanitiz*"` command was mangled by nested PowerShell/Git Bash/CMD escaping and returned no tests; rerun without that escaping passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 02C-03. The gateway implementation can inject `SanitizationPipeline`, call it first under `TenantContext`, and consume `SanitizationContext.tokenCount()` / `truncated()` for metadata-only observability.

## Self-Check: PASSED

- Confirmed summary, key production files, and key corpus test exist on disk.
- Confirmed task commits exist: `7e60e56`, `a789fd2`, `781498a`, `7ee254b`.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-07*
