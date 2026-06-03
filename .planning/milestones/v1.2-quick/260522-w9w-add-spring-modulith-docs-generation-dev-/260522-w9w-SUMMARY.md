# Quick Task 260522-w9w Summary

## Scope

Compared Zero Mail against the Spring Modulith talk/demo practices and implemented the low-risk optional pieces:

- Spring Modulith documentation generation from the existing API module verification test.
- Spring Modulith actuator dependency for the API app, relying on existing actuator exposure config.
- A focused core `@ApplicationModuleTest` + `Scenario` test for the `thread` module.

## Changes

- `gradle/libs.versions.toml`: added `spring-modulith-actuator`.
- `backend/api/build.gradle.kts`: added the actuator dependency.
- `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java`: verifies modules and writes docs via `Documenter`.
- `backend/core/src/test/java/com/zeromail/core/ZeroMailCoreModuleTestApplication.java`: adds a root test app for core modules.
- `backend/core/src/test/java/com/zeromail/core/thread/ThreadModuleScenarioTest.java`: adds a Modulith Scenario test without bypassing automatic verification.
- `backend/core/src/main/java/com/zeromail/core/llm/package-info.java`: fixes named-interface dependency syntax to `gmail :: persistence.crypto`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/package-info.java`: exposes a narrow `persistence.crypto` named interface.

## Follow-up Fixes

The original core verification failure was fixed without disabling Modulith verification.

- Broke cycles caused by config depending on LLM/billing domain types.
- Moved `OnboardingStep` ownership to account to break `account <-> onboarding`.
- Moved admin-owned master-key helper classes out of `llm.gateway.springai.admin`.
- Moved chat model cache listener to the chat module.
- Replaced `llm -> admin` and `llm -> rules` type dependencies with local mappings/constants.
- Replaced `triage -> draft` concrete dependency with `TriageDraftBodyGenerator` port.
- Converted `shared.*` submodules into named interfaces under a single `shared` module.
- Added named interfaces for intended API packages such as `domain`, `usecases`, `projection`, `gateway`, and `events`.
- Removed direct `chat/draft -> *.persistence` cross-module usage by adding owner-module use cases:
  - `SenderSafetyEntryService`
  - `TriageDraftAuditService`
  - `ClassifyThreadReplyStatusService.currentDraftId(...)`
- Changed `ThreadModuleScenarioTest` into a pure `ApplicationModules.verify()` test so it checks boundaries without unrelated Spring context auto-config noise.

## Verification

```powershell
.\gradlew.bat :backend:core:test
```

Result: PASS.

```powershell
.\gradlew.bat :backend:api:test --tests com.zeromail.api.ZeroMailApiApplicationModulesTest
```

Result: PASS.

```powershell
.\gradlew.bat :backend:core:test --tests com.zeromail.core.thread.ThreadModuleScenarioTest --tests com.zeromail.core.draft.GenerateThreadDraftServiceTest
```

Result: PASS.

Final boundary status:

- Unique cycles: 0
- Non-exposed package dependencies: 0
- Invalid explicit module dependencies: 0
- Invalid sub-module references: 0

## Impact

The fixes are architectural. The main behavior paths were kept stable and verified by the existing core test suite. The only notable implementation changes are dependency inversion/facade moves around BYOK lookup, sender safety lookup/removal, draft audit reservation, and draft-id lookup.
