---
phase: 08-admin-console-operator-tooling
plan: 8B
subsystem: admin-master-keys
tags: [spring-ai, spring-security, spring-data-redis, liquibase, aes-gcm, react, tanstack-router, playwright]
requires:
  - phase: 08-8A
    provides: admin auth chain, AdminContext, audit writer, apps/admin shell, admin OpenAPI group
provides:
  - Encrypted six-provider platform master-key storage with provider-bound AES-GCM AAD
  - Masked-only admin master-key API and Vite admin UI
  - Redis-backed edit-session and test-connection rate limits
  - Provider /models probe returning enum-only results
  - Provider master-key resolver plus cache invalidation on rotation
  - Master-key sentinel leak and resolver-confinement gates
affects: [08D-curated-catalog, 09-user-settings-ui, chat-platform-llm-routing, triage-platform-llm-routing]
tech-stack:
  added: [PlatformSecretCipher, Spring TransactionalEventListener, Redis StringRedisTemplate counters]
  patterns:
    - AdminRequestBody marker for plaintext incoming admin request carve-outs
    - providerSecretVersion separated from kekVersion for cache invalidation
    - ProviderMasterKeyResolver as sole llm_provider_master_key reader
key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/058-llm-provider-master-key.yaml
    - backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java
    - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java
    - apps/admin/src/components/MaskedSecretField.tsx
    - apps/admin/src/routes/_authenticated/master-keys.tsx
    - apps/admin/src/routes/_authenticated/master-keys.$provider.tsx
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java
    - backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
    - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java
    - apps/admin/src/components/AdminLayout.tsx
key-decisions:
  - "LlmProvider primary key uses @Enumerated(EnumType.STRING), not AttributeConverter, because Hibernate 7 rejects converters on @Id and provider.id()==name() is locked."
  - "Cache invalidation uses providerSecretVersion plus synchronous AFTER_COMMIT listener; kekVersion remains only the cipher KEK selector."
  - "OpenAPI codegen remains a running-backend step; 8B keeps raw admin fetch wrappers with a TODO until /v3/api-docs/admin is available in dev."
patterns-established:
  - "Admin plaintext request DTOs are annotated @AdminRequestBody and excluded from response/body-shape scans."
  - "Master-key audit JSON is a closed metadata shape: masked_key, kek_version, provider_secret_version, last_rotated_at, provider, key_format."
requirements-completed:
  - MKEY-01
  - MKEY-02
  - MKEY-03
  - MKEY-04
  - MKEY-05
  - MKEY-06
  - MKEY-07
  - MKEY-08
  - ARCH-11
duration: "continuation completed 2026-05-20T03:01:33Z"
completed: 2026-05-20
---

# Phase 08 Plan 8B: Master Keys Summary

**Encrypted platform LLM master-key management with masked-only admin APIs, Redis edit gates, provider probes, and admin UI.**

## Performance

- **Duration:** Multi-session execution; inline continuation completed at 2026-05-20T03:01:33Z after executor quota failure.
- **Tasks:** 3/3 complete.
- **Files modified:** 40+ source, test, migration, and admin UI files.

## Accomplishments

- Added `llm_provider_master_key` schema with six seeded providers, nullable pre-key rows, `provider_secret_version`, deprecated 8B feature-default flags, valid provider/format constraints, and OpenRouter launch defaults.
- Added `PlatformSecretCipher`, `MasterKeyAdminService`, Redis edit-session and rate-limit services, enum-only provider `/models` probe, masked projections, sentinel scanner, and resolver-confinement ArchUnit gate.
- Wired platform chat model creation to `ProviderMasterKeyResolver`, `providerSecretVersion` cache keys, and synchronous `AFTER_COMMIT` cache eviction.
- Added `/api/admin/master-keys/**` controller/DTOs and the `apps/admin` master-key list/detail flows with masked-only refresh and Playwright coverage.

## Task Commits

1. **Task 8B-01 RED:** `29981fd5` test failing master key storage gates.
2. **Task 8B-01 GREEN:** `010b55a5` add encrypted provider master key storage.
3. **Task 8B-02 RED:** `b5ee1cfd` add failing master key resolver service tests.
4. **Task 8B-02 GREEN:** `38aef121` implement master key resolver and rotation services.
5. **Task 8B-03 RED:** `124773d7` add failing admin master key UI contract.
6. **Task 8B-03 GREEN:** `76276301` add admin master key API and UI.
7. **Rule 1 fix:** `b3511e88` map master key provider id without converter.
8. **Rule 1 fix:** `1defe8fd` render master key provider link without asChild.

## Verification

- Context7 docs checked: Spring AI 2.0.0-M6 provider adapter configuration, Spring Modulith event semantics, and Spring Data Redis counter/TTL idioms.
- `./gradlew :backend:core:test --tests "com.zeromail.core.account.OAuthProvisioningDefaultsTest" --tests "com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyLiquibaseContractTest"` passed after the Hibernate mapping fix.
- `./gradlew :backend:core:test :backend:api:test --tests "*MasterKey*" --tests "*SentinelLeak*" --tests "*MasterKeyResolverConfinement*"` passed.
- `pnpm --filter @zeromail/admin build` passed.
- `pnpm --filter @zeromail/admin test:unit` passed.
- `pnpm --filter @zeromail/admin e2e -- --grep "master-keys"` passed.
- Sentinel scan over `backend/core` and `backend/api` build reports/test-results/logs returned no raw key-shape findings.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed AttributeConverter from enum primary key**
- **Found during:** Plan-level backend verification.
- **Issue:** Hibernate 7 failed all JPA test contexts with `'AttributeConverter' not allowed for attribute 'provider' annotated '@Id'`.
- **Fix:** Mapped `LlmProviderMasterKeyEntity.provider` with `@Enumerated(EnumType.STRING)`. This preserves the DB string shape because `LlmProvider.id()` equals `name()`.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java`
- **Verification:** Targeted context boot and full 8B backend verification passed.
- **Committed in:** `b3511e88`

**2. [Rule 1 - Bug] Removed unsupported `asChild` prop from admin Button**
- **Found during:** Playwright e2e; Vite web server emitted a React console error.
- **Issue:** The copied Base UI `Button` primitive does not implement shadcn `asChild`; React forwarded it to the DOM.
- **Fix:** Rendered the back link as `Link` with `buttonVariants(...)`.
- **Files modified:** `apps/admin/src/routes/_authenticated/master-keys.$provider.tsx`
- **Verification:** Admin build and master-keys Playwright flow passed without the previous console warning.
- **Committed in:** `1defe8fd`

**Total deviations:** 2 auto-fixed bugs. **Impact:** Both fixes were required for correctness; no scope expansion.

## Issues Encountered

- The first 8B executor hit `429 Too Many Requests` after producing partial task work. Continuation ran inline on the same branch and preserved the partial commits/working tree.
- `pnpm --filter @zeromail/admin generate-api` failed with `ECONNREFUSED` because no backend API server was running at `localhost:8080`. The 8A summary already records `admin-schema.d.ts` as a temporary hand-authored stub until `/v3/api-docs/admin` is available during an API-backed dev run.
- No Gradle `liquibaseUpdate` task exists in the current project task graph, so the plan's `:backend:core:liquibaseUpdate -Pdb=local` command could not be run. Liquibase shape is covered by `LlmProviderMasterKeyLiquibaseContractTest` and the JPA boot tests above.

## User Setup Required

None for local code execution. Production still requires the platform secret KEK material from 8A before real master keys can be encrypted.

## Next Phase Readiness

8D can migrate the deprecated 8B feature-default flags into its normalized `feature_default_provider` table. 8C/8E/8F can reuse the admin API, request-body carve-out, masked-only UI, and audit/error handling patterns.

---
*Phase: 08-admin-console-operator-tooling*
*Completed: 2026-05-20*
