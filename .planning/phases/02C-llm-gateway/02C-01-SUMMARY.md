---
phase: 02C-llm-gateway
plan: 01
subsystem: llm-gateway
tags: [spring-ai, spring-modulith, liquibase, postgres, byok, archunit, jtokkit]

# Dependency graph
requires:
  - phase: 01.2
    provides: domain-owned persistence packages and ArchUnit boundary patterns
  - phase: 01.2.1
    provides: AbstractTenantOwnedEntity and IdentifiedEnum shared contracts
  - phase: 02B
    provides: billing module dependency for future LLM credit reservation
provides:
  - core.llm Spring Modulith package skeleton and import-boundary tests
  - tenant_byok_credentials Liquibase schema and BYOK JPA persistence
  - pure-Java LlmModelClient seam and LLM gateway model records
  - prompt-injection fixture corpus and disabled Wave 0 scaffold tests
affects: [02C-02, 02C-03, 02C-04, 02C-05a, 02C-05b, 02C-06, 02C-07, 02C-08]

# Tech tracking
tech-stack:
  added:
    - org.springframework.ai:spring-ai-bom:2.0.0-M4
    - org.springframework.ai:spring-ai-starter-model-openai:2.0.0-M4
    - org.springframework.ai:spring-ai-starter-model-anthropic:2.0.0-M4
    - com.knuddels:jtokkit:1.1.0
  patterns:
    - Strict ArchUnit import pins for Spring AI/vendor SDKs and sanitizer libraries
    - BYOK enum persistence via AttributeConverter and lowercase IdentifiedEnum ids
    - Pure-Java LlmModelClient seam between service contracts and Spring AI adapters

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/package-info.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/BYOKProvider.java
    - backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java
    - backend/core/src/main/resources/db/changelog/changes/018-tenant-byok-credentials.yaml
    - backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
  modified:
    - gradle/libs.versions.toml
    - backend/core/build.gradle.kts
    - backend/worker/build.gradle.kts
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
    - backend/api/src/test/java/com/zeromail/api/security/CorsIntegrationTest.java
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java

key-decisions:
  - "RefreshTokenCipher stays in core.gmail.persistence.crypto for Plan 01; core.llm declares a Modulith edge to gmail.persistence.crypto per D-A5."
  - "jsoup remains at 1.22.2; no confirmed regression required changing the existing version."
  - "Spring AI imports stay out of core.llm.service/model by using pure-Java seam records and LlmModelClient."
  - "backend/api/build.gradle.kts was inspected but not changed; API contexts see Spring AI through backend/core."

patterns-established:
  - "BYOKProvider uses lowercase ids with @JsonValue/@JsonCreator and AttributeConverter persistence, avoiding @Enumerated drift."
  - "Wave 0 scaffolds compile behind @Disabled and document which downstream plan removes each disable."
  - "Spring AI starter presence requires test-only placeholder keys in SpringBootTest contexts that do not exercise the gateway."

requirements-completed: [LLM-01]

# Metrics
duration: 45min
completed: 2026-05-07
---

# Phase 02C Plan 01: LLM Gateway Foundation Summary

**LLM gateway foundation with strict Spring AI import boundaries, BYOK credential persistence, and disabled Wave 0 safety scaffolds**

## Performance

- **Duration:** 45 min
- **Started:** 2026-05-07T11:40:39Z
- **Completed:** 2026-05-07T12:25:00Z
- **Tasks:** 1
- **Files modified:** 42

## Accomplishments

- Added the parent `core.llm` Modulith package plus model/service/persistence/gateway sub-packages.
- Added Spring AI OpenAI/Anthropic starter coordinates, the Spring AI BOM, and `jtokkit` 1.1.0.
- Created Liquibase 018 for one BYOK credential row per tenant, with provider check constraints and tenant cascade delete.
- Added BYOK entity/repository/converter plus JSON and Postgres round-trip tests.
- Added `LlmGatewayBoundaryTest` with 3 strict import rules and extended `DomainBoundaryArchTests` to include `core.llm.persistence` in 5 domain rules.
- Added pure-Java LLM seam records and prompt-injection fixtures for downstream sanitizer/gateway plans.

## Task Commits

1. **Task 1 wiring:** `882673d` (`feat`) add LLM gateway package skeleton, Gradle wiring, and Liquibase 018.
2. **Task 1 RED tests:** `1833694` (`test`) add failing Wave 0 tests and prompt-injection fixtures.
3. **Task 1 implementation:** `e9dc32f` (`feat`) implement BYOK persistence, seam contracts, stubs, and boundary tests.
4. **Rule 3 fix:** `0b23de9` (`fix`) unblock API verification with Spring AI test keys and no-redirect CORS test client.
5. **Rule 3 fix:** `e068cec` (`fix`) add Spring AI test keys to worker contexts.

## Files Created/Modified

- `gradle/libs.versions.toml` - Spring AI BOM/starter aliases and `jtokkit` version/library alias.
- `backend/core/build.gradle.kts` - Spring AI platform/starters, `jtokkit`, and jsoup on core classpath.
- `backend/worker/build.gradle.kts` - Spring AI platform/starters and `jtokkit` for future worker gateway use.
- `backend/core/src/main/java/com/zeromail/core/llm/**` - LLM package skeleton, BYOK model/persistence, pure-Java seam, and temporary Plan 01 contracts.
- `backend/core/src/main/resources/db/changelog/changes/018-tenant-byok-credentials.yaml` - BYOK credentials schema.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - includes changeset 018.
- `backend/core/src/test/java/com/zeromail/core/arch/**` - LLM import pins and cross-domain repository boundary expansion.
- `backend/core/src/test/java/com/zeromail/core/llm/**` - BYOK tests and disabled Wave 0 scaffolds.
- `backend/core/src/test/resources/llm/prompt-injection/**` - 5 prompt-injection fixtures.
- `backend/*/src/test/java/**/PostgresContainerTest.java` - test-only Spring AI placeholder keys.
- `backend/api/src/test/java/com/zeromail/api/security/CorsIntegrationTest.java` - no-redirect client for local CORS response verification.

## Decisions Made

- Kept `RefreshTokenCipher` at `core.gmail.persistence.crypto` and declared the allowed Modulith dependency from `core.llm`.
- Left `jsoup` at 1.22.2; no verified regression justified downgrading or changing the existing pin.
- Kept all public gateway/service/model contracts Spring AI-free. Spring AI adapters will live under `core.llm.gateway.springai` in later plans.
- Did not add direct Spring AI dependencies to `backend/api/build.gradle.kts`; the API module receives required classes through its dependency on `backend/core`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added Spring AI placeholder keys to test contexts**
- **Found during:** Task 1 verification
- **Issue:** Adding Spring AI starters caused Spring Boot test contexts in core, API, and worker modules to auto-configure model beans and fail without API keys.
- **Fix:** Added test-only `spring.ai.openai.api-key` and `spring.ai.anthropic.api-key` dynamic properties where SpringBootTest contexts boot without exercising the gateway adapter.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java`, `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java`, `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java`
- **Verification:** `.\gradlew.bat :backend:api:test`, `.\gradlew.bat :backend:worker:test`, and `.\gradlew.bat clean check`
- **Committed in:** `e9dc32f`, `0b23de9`, `e068cec`

**2. [Rule 3 - Blocking] Kept CORS actual-response test on the local response**
- **Found during:** Plan-level `clean check`
- **Issue:** `RestClient` followed the unauthenticated `/me` redirect to Google, so the test asserted against Google HTML headers instead of the API's CORS headers.
- **Fix:** Switched the test client to `JdkClientHttpRequestFactory` backed by a no-redirect `HttpClient`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/CorsIntegrationTest.java`
- **Verification:** `.\gradlew.bat :backend:api:test --tests "CorsIntegrationTest"` and full `.\gradlew.bat clean check`
- **Committed in:** `0b23de9`

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes were required to make the plan's new Spring AI classpath and full verification gate work. No production behavior was expanded beyond Plan 01.

## Known Stubs

- `backend/core/src/main/java/com/zeromail/core/llm/model/SanitizationContext.java:4` - temporary single-field placeholder; Plan 02 replaces it with the full sanitizer context.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java:4` - temporary interface contract; Plan 02 provides the concrete sanitizer.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java:4` - marker interface only; Plan 03 adds gateway methods and implementation.
- `backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java:4` - marker interface only; Plan 04 adds allow-list validation.
- `backend/core/src/test/java/com/zeromail/core/llm/**` - 3 Wave 0 scaffolds are intentionally `@Disabled` until Plans 02, 03, and 04 land the referenced behavior.

## Threat Flags

None - new security-relevant surfaces (LLM import boundary, BYOK schema, tokenizer/sanitizer dependency pinning) were already captured in the plan threat model.

## Authentication Gates

None.

## Issues Encountered

- Context7 MCP was unavailable during the final Spring AI property check due to transport failure. The documented CLI fallback was attempted but did not return usable docs in this environment; the Spring AI error message and existing core test-context fix provided the needed property names.
- Git Bash could run grep/static acceptance checks, but Gradle wrapper execution uses `.\gradlew.bat` on this Windows checkout.

## Verification

- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava :backend:core:test --tests "LlmGatewayBoundaryTest" --tests "DomainBoundaryArchTests" --tests "TenantByokCredentialsPersistenceWave0Test"` - passed.
- `.\gradlew.bat :backend:core:test --tests "BYOKProviderRoundTripPersistenceTest" --tests "BYOKProviderJsonTest" --tests "LlmGatewayBoundaryTest" --tests "TenantByokCredentialsPersistenceWave0Test" --tests "DomainBoundaryArchTests"` - passed.
- `.\gradlew.bat :backend:api:test --tests "CorsIntegrationTest"` - passed.
- `.\gradlew.bat :backend:api:test` - passed.
- `.\gradlew.bat :backend:worker:test` - passed.
- `.\gradlew.bat clean check` - passed.
- Git Bash acceptance greps - passed for library coordinates, Modulith boundary, BYOK converter/JSON/entity checks, strict ArchUnit rule count, zero Spring AI seam imports, 5 `core.llm.persistence` boundary references, 5 prompt-injection fixtures, and 3 disabled Wave 0 scaffolds.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `02C-02`. Downstream plans should replace the temporary sanitizer/gateway/validator stubs and remove the matching `@Disabled` annotations as each concrete behavior lands.

## Self-Check: PASSED

- Key created files exist on disk.
- Task commits found: `882673d`, `1833694`, `e9dc32f`, `0b23de9`, `e068cec`.
- Final verification command `.\gradlew.bat clean check` passed.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-07*
