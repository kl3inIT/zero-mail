# Phase 03: Rules Engine - Patterns

**Mapped:** 2026-05-10
**Purpose:** Existing code patterns Phase 3 executors should copy before inventing new structure.

## Backend Domain Shape

| Planned File/Area | Closest Existing Analog | Pattern to Reuse | Notes |
|-------------------|-------------------------|------------------|-------|
| `core.rules/package-info.java` | `backend/core/src/main/java/com/zeromail/core/llm/package-info.java`, `billing/package-info.java` | Parent package owns `@ApplicationModule`; sub-packages use simple package-info docs | Allowed deps should include `tenant`, `llm`, `gmail`, `onboarding`, `shared.persistence`, `shared.lang`. Avoid repository-level cross-domain shortcuts except explicitly planned service boundaries. |
| `core.rules/model/*` | `core.llm.model.Action`, `core.billing.model.CallSite`, `core.onboarding.model.OnboardingStep` | Records for value objects, enums implement `IdentifiedEnum`, static `fromId` throws `NoSuchElementException` | Do not use ordinal storage. |
| `RuleEntity`, `RuleTemplateEntity` | `TenantByokCredentialsEntity`, `OnboardingSelectionEntity`, `CreditReservationEntity` | JPA entity classes extend `AbstractTenantOwnedEntity`; protected no-arg constructor; explicit domain constructor | Do not redeclare `tenant_id` when extending base entity. |
| `RuleRepository`, `RuleTemplateRepository` | `OnboardingSelectionRepository`, `MailMessageObservedRepository`, billing repositories | Spring Data JPA repository with explicit tenant-qualified methods; native SQL only for bulk/order-sensitive paths | Bulk JPQL/native updates must include tenant predicate. |
| Liquibase rules schema | `018-tenant-byok-credentials.yaml`, `012-mail-message-observed-table.yaml`, `015-credit-reservation.yaml` | YAML changeset under `changes/`, included from `db.changelog-master.yaml`, FK to `tenants(id)`, rollback block | Use JSONB for matcher/action columns and GIN indexes where useful; add check constraints for rule/template status ids. |
| JSONB mapping | `MailMessageObservedEntity` array mapping, Phase 2A Yasson runtime decision | Hibernate JSON support through `@JdbcTypeCode(SqlTypes.JSON)` where records/maps are persisted | Add round-trip tests against Postgres, not only object-mapper unit tests. |
| Cross-domain onboarding reads | `OnboardingService` wrapping `OnboardingSelectionRepository`, `DomainBoundaryArchTests` | Owning domain service exposes a narrow read facade; new domains do not import another domain's `persistence` package | Template materialization in `core.rules` must call `OnboardingService.selectedEnabledTemplateKeys(...)`, not `OnboardingSelectionRepository` directly. |

## LLM Gateway and Compile

| Planned File/Area | Closest Existing Analog | Pattern to Reuse | Notes |
|-------------------|-------------------------|------------------|-------|
| `RuleCompilerService` | `LlmGatewayImpl`, `ByokService` | Service owns business logic and gateway call; log only event/tenant/reason metadata | `core.rules` imports `LlmGateway`, `CallSite`, `ToolCallResult`; no Spring AI imports. |
| Gateway compile schema changes | `AllowListedTools`, `SpringAiLlmModelClient`, `LlmTool` | Project-local `LlmTool` translated to Spring AI callback in adapter | If adding `rule_compile`, keep schemas in `core.llm` and adapter behavior in `core.llm.gateway.springai`. |
| Boundary tests | `LlmGatewayBoundaryTest`, `LlmRepositoryContentBanTest` | ArchUnit import bans and repository content-name bans | Add `RulesLlmBoundaryTest` to forbid `org.springframework.ai..` and vendor SDK imports in `core.rules`. |
| Safety exceptions | `SafetyViolationException`, `InvalidByokException`, `GlobalExceptionHandler` | Exceptions carry no raw content; API maps to stable `ErrorCodes` | Rule compile validation exceptions should not include prompt, completion, raw args, or mail content. |

## Gmail Preview

| Planned File/Area | Closest Existing Analog | Pattern to Reuse | Notes |
|-------------------|-------------------------|------------------|-------|
| Preview candidate query | `MailMessageObservedRepository` | Tenant-scoped repository query over observed message metadata | Add a method selecting recent message IDs by tenant/order, not raw message content. |
| Gmail read client | `GmailApiClientFactory`, worker Gmail usage | Refresh access token, build Gmail client, call read methods | Preview service must use read-only Gmail API calls. Do not inject any write/action executor. |
| Gmail connection state | `GmailConnectionService.currentStatus`, `markDisconnected` | Domain service owns Gmail connection state; no raw token logs | Preview failures from disconnected/revoked grants should map to safe API errors. |
| Privacy tests | `LlmGatewayObservabilityTest`, `SensitiveMarkerScrubFilterTest`, `LlmRepositoryContentBanTest` | Capture logs/spans and assert content sentinels absent | Use synthetic subjects/bodies only. Assert no raw headers/snippets/bodies/prompts/completions persist. |

## API Layer

| Planned File/Area | Closest Existing Analog | Pattern to Reuse | Notes |
|-------------------|-------------------------|------------------|-------|
| `RulesController` | `ByokController`, billing controllers | Thin controller; maps request records to core command records; service owns transaction | Resolve tenant via `TenantContext.currentOrThrow()` in controller and pass UUID to service. |
| `api.dto.rules.*` | `api.dto.llm.*`, `api.dto.billing.*` | Java records with Jakarta validation and static `from(...)` response helpers | Do not return entities. Do not include raw mail content in preview DTOs. |
| Error mapping | `GlobalExceptionHandler`, `ErrorCodes` | Stable dotted error codes and localized frontend messages | Add rule compile ambiguous/invalid/sample-size/not-found conflicts. |
| OpenAPI generation | `backend/api/build.gradle.kts`, `apps/web/scripts/generate-api.ts` | `./gradlew :backend:api:generateOpenApiDocs` then `pnpm --filter web generate:api` | Plans touching API must update `openapi.json` and `schema.d.ts`. |

## Frontend

| Planned File/Area | Closest Existing Analog | Pattern to Reuse | Notes |
|-------------------|-------------------------|------------------|-------|
| `/rules` page | `app/(protected)/settings/page.tsx`, protected layout | Protected route, `main mx-auto w-full max-w-6xl p-6`, nested client workspace | Page shell can be server component; interactive workspace is client. |
| `features/rules/api/rules-api.ts` | `features/llm/api/llm-api.ts`, `features/triage/api/triage-api.ts` | `openapi-fetch` typed calls, `xsrfHeader()` for mutations | Use generated path/schema types only. |
| `features/rules/hooks/use-rules.ts` and `query-keys.ts` | `features/llm/hooks/use-byok.ts`, `features/account/api/keys.ts` | Query key factory and mutation hooks | Reorder uses optimistic update rollback; other mutations invalidate relevant keys. |
| Rules components | `ByokForm.tsx`, `PauseBanner.tsx`, `TemplateCard.tsx` | Raw shadcn primitives; no card inside card; i18n via `useTranslations()` | Use lucide icons for icon buttons; ensure accessible labels/tooltips. |
| i18n | `features/llm/messages.ts`, `merge-feature-i18n.ts`, `check-i18n.ts` | Feature-owned messages as source of truth; generated VI/EN bundles | Add `rules.*` and `errors.rules.*`; update EN scanner files. |
| Playwright | `apps/web/e2e/byok.spec.ts` | Mock API routes, set auth cookies, test desktop/mobile and no horizontal overflow | New `rules.spec.ts` covers create -> preview -> save -> enable/disable -> reorder -> edit -> delete. |

## Testing Patterns

| Planned Test | Existing Analog | Pattern to Reuse |
|--------------|-----------------|------------------|
| Migration/entity tests | `PostgresContainerTest`, `BYOKProviderRoundTripPersistenceTest`, `MailMessageObservedEntityTest` | Real Postgres, Liquibase enabled, Hibernate validate. |
| Service unit tests | `ActionValidatorTest`, `LlmGatewayCreditLifecycleTest`, billing service tests | Use fakes/mocks with explicit tenant IDs and no content-bearing logs. |
| API integration tests | `ByokControllerIntegrationTest`, billing controller tests | Authenticated requests through real Spring MVC stack; assert ProblemDetail code values. |
| Frontend invariant tests | `byok-key-handling.test.ts`, route group tests | Source-level guards for privacy/architecture and Testing Library component behavior. |
| Browser flow | `byok.spec.ts` | Route mocks for backend APIs plus mobile viewport overflow check. |

## Source Audit

| Source | Item | Covered By |
|--------|------|------------|
| GOAL | Author, preview, manage NL rules that compile to deterministic AST | Plans 01-09 |
| REQ RULE-01 | Plain-language rule authoring | Plans 02, 03, 08 |
| REQ RULE-02 | Structured LLM compile | Plans 02, 03 |
| REQ RULE-03 | Deterministic evaluator | Plan 04 |
| REQ RULE-04 | Semantic deferral | Plans 01, 04, 05, 08 |
| REQ RULE-05 | Preview recent messages | Plans 05, 07, 08 |
| REQ RULE-06 | CRUD/reorder/enable/delete | Plans 01, 03, 07, 08 |
| REQ RULE-07 | Template gallery/materialization | Plans 06, 08 |
| CONTEXT D-A1..D-A5 | Authoring and preview-before-enable | Plans 03, 07, 08 |
| CONTEXT D-B1..D-B4 | Safe evidence/preview privacy | Plans 05, 08 |
| CONTEXT D-C1..D-C4 | Template materialization/provenance/catalog | Plans 01, 06, 08 |
| CONTEXT D-D1..D-D4 | Ordering, dedupe, conflicts, saved-rule preview semantics | Plans 03, 04, 05, 08 |
| AI-SPEC | Eval dimensions, guardrails, privacy metrics | Plans 00, 02, 03, 04, 05, 09 |
| UI-SPEC | Rules page composition, copy, responsive/accessibility contract | Plan 08 |

---

*Phase: 03-rules-engine*
*Patterns mapped: 2026-05-10*
