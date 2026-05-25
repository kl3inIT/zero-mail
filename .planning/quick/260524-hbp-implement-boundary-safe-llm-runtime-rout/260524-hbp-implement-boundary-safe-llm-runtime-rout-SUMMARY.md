---
status: verified
completed: 2026-05-24
task: implement-boundary-safe-llm-runtime-routing
---

# Summary

Implemented boundary-safe runtime LLM routing so admin-managed routing rows can drive actual AI calls without making `core.llm` depend on `core.admin`.

## Changes

- Added the runtime-owned routing port under `core.llm.routing`.
- Adapted `admin.cat.usecases.LlmRouter` to implement the runtime `LlmRouteResolver` port.
- Wired routing into:
  - chat assistant default model selection
  - rule authoring / rule compiler
  - draft generation
  - rule preview semantic matching
  - triage runtime semantic matching
  - drift checks
- Added `zero-mail.llm.platform.draft-model` fallback configuration.
- Added Liquibase changeset `088-llm-runtime-routing-tasks.yaml` to expand routing feature constraints and seed runtime task defaults.
- Updated admin routing UI to show business-facing task labels:
  - Chat assistant
  - Draft content
  - Create rule
  - Test rule
  - Run rule
  - Choose AI action
  - Quality check
- Regenerated admin OpenAPI schema.

## Boundary Decision

`core.llm` owns the runtime vocabulary (`LlmRuntimeTask`, `ResolvedLlmRoute`, `LlmRouteResolver`). Admin remains a management adapter that stores and resolves catalog data. This prevents reverse dependency from LLM runtime code into admin catalog packages and keeps Spring Modulith boundaries clean.

## Verification

- `./gradlew.bat --no-daemon :backend:core:test --tests "*LlmGatewayRuntimeRoutingTest" --tests "*LlmGatewayBoundaryTest" --tests "*LlmRouterWalkOrderTest"` — PASS
- `./gradlew.bat --no-daemon :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava` — PASS
- `./gradlew.bat --no-daemon :backend:api:test --tests "*ApplicationModules*"` — PASS
- `pnpm --filter @zeromail/admin run generate-api` — PASS
- `pnpm --filter @zeromail/admin run typecheck` — PASS
- `pnpm --filter @zeromail/admin run lint` — PASS
- `pnpm --filter @zeromail/admin exec playwright test e2e/master-keys.spec.ts` — PASS
- Playwright manual check on `/master-keys` confirmed business labels are visible and technical enum names are not rendered.
