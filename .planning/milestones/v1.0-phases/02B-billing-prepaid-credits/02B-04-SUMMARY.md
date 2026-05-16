---
phase: 02B-billing-prepaid-credits
plan: 04
subsystem: backend-api
tags: [billing, prepaid-credits, api, sepay, openapi, i18n]

requires:
  - phase: 02B-02
    provides: Billing model contracts and exceptions.
  - phase: 02B-03
    provides: CreditLedger, BillingTopupService, and SePay API-key verification.
provides:
  - Authenticated billing balance and top-up intent HTTP endpoints.
  - SePay webhook endpoint with API-key filter chain.
  - Billing exception mappings with privacy-preserving empty params.
  - OpenAPI and generated frontend TypeScript schema for billing endpoints.
affects: [02B-06-verification-closure, 02C-llm-gateway, apps-web]

tech-stack:
  added: []
  patterns:
    - Thin billing controllers delegating to core services.
    - Dedicated SecurityFilterChain for unauthenticated provider webhooks.
    - Testcontainers-backed API tests using RestClient against real servlet filters.

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java
    - backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/main/resources/application.yml
    - backend/api/build.gradle.kts
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/*.java
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json
    - apps/web/openapi/openapi.json
    - apps/web/lib/api/schema.d.ts

key-decisions:
  - "Billing configuration lives under the existing ZeroMailCoreProperties root using zeromail.billing.*, not a separate BillingProperties class."
  - "BillingWebhookSecurityConfig is @Order(2) because Pub/Sub already owns @Order(1); the normal user-session SecurityConfig is @Order(3)."
  - "spring.jpa.open-in-view is disabled in the API module so tenant-bound service transactions, not request-opened bootstrap sessions, own billing JPA access."

patterns-established:
  - "SePay webhook auth logs only event names and never logs Authorization header bytes."
  - "API billing tests truncate only billing-owned tables between tests to avoid cross-test ledger contamination while preserving shared tenant/session fixtures."
  - "OpenAPI generation uses dummy zeromail.billing.* properties in Gradle, then pnpm generate:api consumes apps/web/openapi/openapi.json."

requirements-completed: [BILL-01, BILL-05, BILL-06]

duration: 30min
completed: 2026-05-06
---

# Phase 02B Plan 04: API Surface Summary

**Billing HTTP surface is wired through Spring MVC, Spring Security, OpenAPI, i18n, and frontend schema generation.**

## Accomplishments

- Added `/api/billing/balance` and `/api/billing/topup/intent` via `BillingController`.
- Added `/api/billing/sepay/webhook` via `SepayWebhookController` and a dedicated API-key `SecurityFilterChain`.
- Enabled billing Wave 0 API tests and fixed their fixtures for tenant-filtered JPA reads, valid Crockford top-up codes, and per-test billing table cleanup.
- Regenerated `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts`; the generated schema includes all three billing paths.
- Added billing i18n keys to both `en.json` and `vi.json`.

## Commits

| Commit | Description |
|--------|-------------|
| `faa6d8d` | `feat(02B-04): add billing DTOs and error mappings` |
| `5fdb648` | `feat(02B-04): complete billing API surface` |

Supporting fixes needed for Plan 04 verification:

| Commit | Description |
|--------|-------------|
| `8f1f423` | `fix(02B): consolidate billing properties and migration checks` |
| `768b52a` | `fix(02B-05): make billing scheduler tests deterministic` |

## Verification

- `.\gradlew.bat :backend:api:compileJava :backend:api:compileTestJava` - PASS.
- `.\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.billing.*"` - PASS.
- `.\gradlew.bat :backend:api:generateOpenApiDocs` - PASS.
- `pnpm generate:api` - PASS.
- `pnpm --filter web i18n:check` - PASS.

## Deviations from Plan

- No `BillingApiConfiguration` remains. Billing properties are nested inside `ZeroMailCoreProperties`, honoring the project convention that core properties live under one root configuration file.
- The SePay filter chain uses `@Order(2)`, not `@Order(1)`, because Phase 02A's Pub/Sub push security chain already owns `@Order(1)`.
- API tests use `tools.jackson.databind.ObjectMapper` because Spring Boot 4 ships Jackson 3.

## Self-Check: PASSED

- API endpoints compile and are present in generated OpenAPI.
- All seven API Wave 0 billing tests pass.
- Error/i18n keys remain covered by the strict frontend i18n check.
