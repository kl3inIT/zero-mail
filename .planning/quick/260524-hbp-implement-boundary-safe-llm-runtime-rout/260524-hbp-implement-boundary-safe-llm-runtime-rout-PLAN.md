---
status: planned
created: 2026-05-24
task: implement-boundary-safe-llm-runtime-routing
sources:
  - https://docs.spring.io/spring-ai/reference/api/chatclient.html
  - https://docs.spring.io/spring-ai/reference/api/chatmodel.html
  - https://github.com/spring-projects/spring-ai-examples/blob/main/model-context-protocol/sampling/README.md
---

# Quick Task: Boundary-Safe LLM Runtime Routing

## Scope

- Make admin-managed model defaults drive real runtime AI calls.
- Keep the routing owner out of the `admin` module. Admin is a management adapter, not the runtime owner.
- Avoid Spring Modulith dependency cycles and non-exposed package access.
- Preserve current behavior through fallback defaults while the routing table is incomplete.

## Current Problem

- The admin routing matrix stores `CHAT`, `TRIAGE`, and `DRAFT` defaults, but runtime calls do not currently consume `LlmRouter`.
- Rule compilation uses `zero-mail.llm.platform.compile-model` through `CallSite.PREVIEW`.
- Draft generation currently maps `CallSite.DRAFT` to `triageModel()`, so the admin `DRAFT` card does not control draft model selection.
- Importing `core.admin.cat.usecases.LlmRouter` directly into `core.llm` would be a boundary smell and can reintroduce Spring Modulith violations.

## Target Design

- Introduce a runtime-owned routing port inside the LLM boundary, such as `core.llm.routing`:
  - `LlmRuntimeTask`
  - `LlmRoutingTier`
  - `ResolvedLlmRoute`
  - `LlmRouteResolver`
- Move or bridge the existing catalog routing data behind that port.
- Admin screens call public routing-management use cases; runtime calls only the routing resolver.
- Map runtime tasks explicitly:
  - `CHAT_ASSISTANT`
  - `RULE_AUTHORING`
  - `RULE_PREVIEW_SEMANTIC`
  - `TRIAGE_SEMANTIC`
  - `DRAFT_GENERATION`
  - optional `DRIFT_CHECK`

## Plan

1. Add boundary-safe LLM routing vocabulary and resolver port.
2. Move the existing admin catalog router implementation behind the LLM-owned port or create a clean adapter without making `llm` depend on `admin`.
3. Add DB migration and seed data for missing runtime tasks, especially rule authoring and draft generation.
4. Wire `LlmGatewayImpl` platform calls to resolve task routes before falling back to legacy properties.
5. Update admin UI labels so operators configure real AI tasks, not misleading technical call sites.
6. Regenerate OpenAPI/admin schemas and update tests.

## Verification

- `./gradlew.bat --no-daemon :backend:api:test --tests "*ApplicationModules*"`
- `./gradlew.bat --no-daemon :backend:core:test --tests "*LlmRouter*" --tests "*LlmGateway*" --tests "*RuleCompile*"`
- `./gradlew.bat --no-daemon :backend:api:generateOpenApiDocs`
- `pnpm --filter @zeromail/admin generate-api`
- `pnpm --filter @zeromail/admin typecheck`

## Done When

- Admin routing changes affect actual rule compiler, triage semantic, draft generation, and chat assistant model selection where intended.
- `core.llm` does not import `core.admin.*`.
- Spring Modulith verification passes without bypasses.
- Existing fallback properties remain available for missing routing rows or inactive provider keys.
