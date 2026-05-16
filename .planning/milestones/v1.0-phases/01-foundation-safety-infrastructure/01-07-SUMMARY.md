---
phase: 01-foundation-safety-infrastructure
plan: 07
status: complete
---

# Plan 01-07 Summary — REST API surface + OpenAPI contract

## What was built

### Production code (11 files, all compile, all JetBrains-clean)

**Configuration:**
- `config/OpenApiConfig.java` — springdoc `OpenApiCustomizer` setting `info.title = "Zero Mail API"`, `info.version = "0.1.0"`.
- `config/GlobalExceptionHandler.java` — `@RestControllerAdvice` mapping `IllegalStateException → 409 conflict` and `IllegalArgumentException → 400 bad_request` problem-detail JSON.
- `application.yml` — `springdoc.api-docs.path=/v3/api-docs` (OpenAPI 3.1), `springdoc.swagger-ui.path=/swagger-ui.html`, `packages-to-scan=com.zeromail.api.controllers` to keep the published contract scoped to Phase 1 endpoints.

**DTOs (records):**
- `dto/MeResponse(userId, tenantId, email, onboardingStep)`
- `dto/TenantStatusResponse(connectionStatus, googleEmail)`
- `dto/SelectTemplateRequest(templateKey)` with `@Pattern(regexp = "archive-receipts|label-newsletters|pin-calendar")` Jakarta validation.

**Controllers (resolve current user via `UserRepository.findFirstByTenantId(UUID)` per WARNING-2):**
- `controllers/MeController` — `GET /me` returns `MeResponse` for the bound tenant's user.
- `controllers/TenantStatusController` — `GET /tenant/status` returns the gmail connection status, falls back to `NOT_CONNECTED`.
- `controllers/ConnectGmailController` — `POST /tenant/connect-gmail` redirects to `/oauth2/authorization/google-gmail` (the second-leg authorization endpoint wired in plan 01-05).
- `controllers/DisconnectController` — `POST /tenant/disconnect` flips the row to `DISCONNECTED` + sets `disconnected_at`.
- `controllers/AccountDeletionController` — `DELETE /me/account` (`@Transactional`) cascades through `onboarding_selections → gmail_connections → users → tenants` for the bound tenant.
- `controllers/OnboardingController` — `POST /onboarding/select-template` saves a row + advances `users.onboarding_step` to `TEMPLATE_SELECTED`; `POST /onboarding/complete` advances to `COMPLETE`. Both are `@Transactional`, both validate the request body, both use `findFirstByTenantId` and `UserEntity.advanceTo` (forward-only enum guard from plan 04).

### Tests (3 files, all green)

- `OpenApiSchemaTest` — `GET /v3/api-docs` returns valid OpenAPI 3.1 JSON; `info.version == "0.1.0"`; `paths` contains exactly `/me`, `/tenant/status`, `/tenant/connect-gmail`, `/tenant/disconnect`, `/me/account`, `/onboarding/select-template`, `/onboarding/complete`.
- `AccountDeletionE2ETest` — seeds `tenant + user + gmail_connection + onboarding_selection`, calls the controller directly, asserts zero rows remain in all four tables for that tenant.
- `OnboardingStateMachineTest` — drives `SIGNED_IN → TEMPLATE_SELECTED → COMPLETE` via the controller; asserts the persisted `onboarding_step` reflects each transition.

All tests extend `ApiPostgresTestBase` (introduced in plan 01-05) — singleton Postgres 17.6 testcontainer + in-memory `MapSessionRepository` so Spring Session resolves cookies without redis.

## Test execution

```
./gradlew :backend:api:test \
  --tests "com.zeromail.api.OpenApiSchemaTest" \
  --tests "com.zeromail.api.AccountDeletionE2ETest" \
  --tests "com.zeromail.api.OnboardingStateMachineTest"

OpenApiSchemaTest:           tests=1, failures=0, errors=0
AccountDeletionE2ETest:      tests=1, failures=0, errors=0
OnboardingStateMachineTest:  tests=1, failures=0, errors=0
```

## Threat mitigations from PLAN.md

| ID | Mitigation status |
|----|-------------------|
| T-07-cascade | ✅ AccountDeletionE2ETest asserts zero rows in all four tables; `@Transactional` ensures atomicity |
| T-F06 | ✅ Spec is GET-only; CSRF intentionally not required for `/v3/api-docs` |
| T-06-spec-scope | ✅ `springdoc.packages-to-scan = com.zeromail.api.controllers` confines the published spec to Phase 1 controllers |

## Acceptance criteria

| Criterion | Status |
|-----------|--------|
| `grep "0.1.0" OpenApiConfig.java` | ✅ |
| `grep "DeleteMapping(\"/me/account\")"` | ✅ |
| `grep "PostMapping(\"/onboarding/select-template\")"` | ✅ |
| `grep "GetMapping(\"/tenant/status\")"` | ✅ |
| `grep "PostMapping(\"/tenant/connect-gmail\")"` | ✅ |
| `grep "PostMapping(\"/tenant/disconnect\")"` | ✅ |
| `grep "findFirstByTenantId" MeController.java` | ✅ |
| `grep "findFirstByTenantId" OnboardingController.java` | ✅ |
| `grep "findFirstByTenantId" AccountDeletionController.java` | ✅ |
| `grep "findAll().stream().filter"` returns no matches | ✅ |
| `:backend:api:compileJava` exits 0 | ✅ |
| `:backend:api:test` for all three test classes exit 0 | ✅ |
