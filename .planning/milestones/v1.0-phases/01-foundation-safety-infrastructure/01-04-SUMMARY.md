---
phase: 01-foundation-safety-infrastructure
plan: 04
status: complete
completed: 2026-04-25
---

# Plan 01-04 — Liquibase YAML Baseline + JPA Entities

## What shipped

### Schema (Task 1)

- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — `includeAll` under `classpath:db/changelog/changes/`, `errorIfMissingOrEmpty: true`.
- 5 changesets:
  - `001-create-tenants` — `tenants(id uuid pk, display_name, created_at)`.
  - `002-create-users` — `users` + FK to tenants, `google_subject` unique, `onboarding_step varchar(32)` default `SIGNED_IN`, `idx_users_tenant_id`.
  - `003-create-gmail-connections` — table + bytea `refresh_token_encrypted` envelope column + **`uq_gmail_connections_tenant_id`** (AUTH-02: one Gmail per tenant) + `idx_gmail_conn_status`.
  - `004-create-onboarding-selections` — `uq(tenant_id, template_key)` so a tenant can't double-pick the same template.
  - `005-indexes` — `idx_gmail_conn_tenant_id`, `idx_onboarding_tenant_id`.
- `backend/api/src/main/resources/application.yml` extended with `spring.liquibase.change-log` + `spring.jpa.hibernate.ddl-auto: validate` (NIT-2: runtime config stays out of `core`).
- `docker-compose.yml` at repo root — `postgres:17.6` + `redis:7.2` for `spring-boot-docker-compose` dev support.

### Entities + repositories (Task 2)

- `OnboardingStep` enum: `SIGNED_IN | GMAIL_CONNECTED | TEMPLATE_SELECTED | COMPLETE` (D-D2).
- `GmailConnectionStatus` enum: `NOT_CONNECTED | PENDING | CONNECTED | DISCONNECTED`.
- `TenantEntity` — tenant root, no `@TenantId` on itself.
- `UserEntity` — `@TenantId tenantId`, forward-only `advanceTo(OnboardingStep)` that throws on regression.
- `GmailConnectionEntity` — `@TenantId tenantId`, `refresh_token_encrypted byte[]` (the field name is intentionally `refreshTokenEncrypted` so the FND-04 deny-list regex on `refreshToken` does not flag the ciphertext-only column; the unencrypted token never lives on this entity — plan 06 owns the cipher).
- `OnboardingSelectionEntity` — `@TenantId tenantId`, template_key + enabled flag.
- 4 Spring Data JPA repositories. **`UserRepository.findFirstByTenantId(UUID)`** is the canonical tenant-scoped accessor — controllers in plan 07 must use it instead of `findAll().filter(...)` (WARNING-2 locked).

### [BLOCKING] schema-push proof (Task 3)

- `CoreTestApplication` — minimal `@SpringBootApplication(scanBasePackages = "com.zeromail.core")` so `@SpringBootTest` can boot a Postgres-backed context inside the `core` module.
- `PostgresContainerTest` — singleton container pattern: one `PostgreSQLContainer<>("postgres:17.6")` started in `static{}`, shared across all subclasses for the JVM lifetime. `@DynamicPropertySource` wires datasource + Liquibase + `hibernate.ddl-auto=validate`, and excludes `RedisAutoConfiguration` + `SessionAutoConfiguration` (the test context has no Redis).
- `LiquibaseMigrationTest` — boots Spring against the real container, then walks JDBC `getMetaData().getTables(...)` and asserts `tenants`, `users`, `gmail_connections`, `onboarding_selections` are all present. Proves Liquibase applied the master changelog end-to-end.
- `GmailConnectionUniquenessTest` — AUTH-02: inserts two `gmail_connections` rows for the same tenant_id; second insert is rejected by `uq_gmail_connections_tenant_id`.
- `backend/core/build.gradle.kts` — added `spring-boot-starter-liquibase` (the plain `liquibase-core` artifact does not activate Boot 4's Liquibase autoconfig; CLAUDE.md already prescribed the starter).

## Verification

- `./gradlew :backend:core:processResources` → all 5 YAML changelogs packaged into `build/resources/main/db/changelog/changes/`.
- `./gradlew :backend:core:compileJava` → BUILD SUCCESSFUL.
- `./gradlew :backend:core:test --tests com.zeromail.core.persistence.LiquibaseMigrationTest --tests com.zeromail.core.persistence.GmailConnectionUniquenessTest` → BUILD SUCCESSFUL (real `postgres:17.6` Testcontainer, ~60 s).

## Requirements satisfied

- **FND-05** — Schema substrate ready; the multi-tenant leak integration test that exercises virtual-thread fan-out through authenticated traffic is owned by plan 01-05.
- **AUTH-02** — Database-enforced one-Gmail-per-tenant via `uq_gmail_connections_tenant_id`, asserted by integration test.

## Decisions implemented

- D-D1 — `onboarding_selections` table (id, tenant_id, template_key, enabled, created_at) with `uq(tenant_id, template_key)`.
- D-D2 — `OnboardingStep` enum (forward-only) backed by `users.onboarding_step varchar(32)` default `SIGNED_IN`.
- D-G2 — `gmail_connections.refresh_token_encrypted bytea` column for the `[key_version|nonce|ciphertext]` envelope written by plan 06. This plan only creates the column.

## Notes for downstream plans

- **Plan 01-05** binds `TenantContext.TENANT` via `ScopedValue.where(...).run(...)` after authentication; Hibernate's `@TenantId` resolver from plan 02 then auto-filters JPQL/Criteria queries against the entities created here.
- **Plan 01-06** must populate `refreshTokenEncrypted` via the AES-GCM-256 cipher; the column is `byte[]` and accepts the envelope format directly.
- **Plan 01-07** controllers use `UserRepository.findFirstByTenantId(UUID)` and `GmailConnectionRepository.findByTenantId(UUID)` — never `findAll`.
- **Plan 01-09** observability and CASA submission can rely on the schema being live via Liquibase (no schema-on-the-fly).

## Files modified

All 21 files from PLAN.md `files_modified` are present and committed across:

- Task 1 commit — Liquibase changelogs + boot config + docker-compose.
- Task 2 commit — JPA entities + 4 repositories + 2 enums.
- Task 3 commit — `spring-boot-starter-liquibase` + `CoreTestApplication` + `PostgresContainerTest` + `LiquibaseMigrationTest` + `GmailConnectionUniquenessTest`.
