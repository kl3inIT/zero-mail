---
phase: 02A
review_cycle: 4
reviewers: [codex]
reviewed_at: 2026-04-29T11:04:42.6856105+07:00
follow_up_to_cycle: 3
fix_commit: 312df88
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 2
---

# Cross-AI Plan Review - Phase 02A (Cycle 4)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `312df88` resolved the two Cycle 3 HIGH concerns at the plan-text level. This Cycle 4 review found two new HIGH concerns.

## Codex Review

**Summary**

I reviewed the repository plan artifacts only. The two Cycle 3 HIGH concerns are now addressed at the plan-text level. However, I found two new HIGH plan risks: the backend plans introduce Google/Gmail classes without adding the required Gradle dependencies, and the frontend MAIL-05 reconnect gate is aimed at the wrong component boundary, so `CONNECTED + HISTORY_LOST/WATCH_UNHEALTHY` may still not render the prompt.

**Prior HIGH Resolution**

- `mail_message_observed` tenant isolation: **FULLY RESOLVED.** Plan 01 now requires `MailMessageObservedEntity` to use `@IdClass` with explicit `@TenantId` on `tenantId`, and Plan 00 adds a cross-tenant JPA-read isolation test. Evidence: [02A-01-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-01-PLAN.md:529), [02A-01-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-01-PLAN.md:544), [02A-00-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-00-PLAN.md:267).
- `PubSubOidcAuthFilter` global registration/scoping: **FULLY RESOLVED.** Plan 03 explicitly says not to annotate the filter as `@Component`, creates it through `PubSubSecurityConfig`, disables servlet registration with `FilterRegistrationBean#setEnabled(false)`, and keeps `shouldNotFilter()` as defense in depth. Evidence: [02A-03-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-03-PLAN.md:402), [02A-03-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-03-PLAN.md:422), [02A-03-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-03-PLAN.md:442).

**Strengths**

- Strong Wave 0 test spine, especially tenant isolation, OIDC rejection, idempotency, and reconnect/pause UI contracts.
- `pubsub_delivery` claim is now atomic `UPDATE ... RETURNING`, avoiding the released-lock issue for history processing.
- `markDisconnected()` is DB-only and invalid-grant paths avoid best-effort `users.stop()`.
- Pub/Sub controller persistence is moved into `PubSubIngestionService`, preserving thin-controller boundaries.
- Final verification includes full backend/frontend suites, Modulith, ArchUnit, disabled-test checks, and state cleanup.

**Concerns**

- **HIGH - Missing Google/Gmail Gradle dependencies block compilation.** Plan 02 adds `GmailApiClientFactory` and Gmail API usage, and Plan 03 adds `TokenVerifier`, but neither plan lists `gradle/libs.versions.toml`, `backend/core/build.gradle.kts`, or `backend/api/build.gradle.kts`. Current build files contain no Google auth or Gmail API dependencies. Evidence: [02A-02-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-02-PLAN.md:155), [02A-03-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-03-PLAN.md:406), [backend/core/build.gradle.kts](D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/build.gradle.kts:8), [backend/api/build.gradle.kts](D:/study-materials-summer-2026/EXE202/zero-mail/backend/api/build.gradle.kts:9).
- **HIGH - MAIL-05 frontend gate may not render in the actual app.** Plan 04 assumes `ReconnectPrompt` owns the status gate, but the current component always renders when mounted, while the settings page only mounts it for `connStatus === 'DISCONNECTED'`. A `CONNECTED` account with `ingestionHealth=HISTORY_LOST` can still miss the prompt unless the parent mount condition is also changed. Evidence: [02A-04-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-04-PLAN.md:251), [ReconnectPrompt.tsx](D:/study-materials-summer-2026/EXE202/zero-mail/apps/web/features/gmail/components/ReconnectPrompt.tsx:25), [settings/page.tsx](D:/study-materials-summer-2026/EXE202/zero-mail/apps/web/app/(protected)/settings/page.tsx:93).
- **MEDIUM - Pub/Sub required properties are not globally accounted for.** Plan 03 makes Pub/Sub config active under `test` and fail-fast via env vars, but does not add test defaults or OpenAPI emit dummy args. `generateOpenApiDocs` currently only supplies OAuth/crypto dummy values. Evidence: [02A-03-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-03-PLAN.md:637), [backend/api/build.gradle.kts](D:/study-materials-summer-2026/EXE202/zero-mail/backend/api/build.gradle.kts:38).
- **MEDIUM - Gmail worker tests are still not clearly hermetic.** `MockGmailHistoryServer` is planned, but `GmailApiClientFactory` hardcodes token refresh URL and a standard trusted Gmail client; no injectable Gmail/token base URL is specified.
- **MEDIUM - `GoogleNetHttpTransport.newTrustedTransport()` compile handling is still underspecified.** The planned `buildGmailClient` signature does not catch or declare checked exceptions. Evidence: [02A-02-PLAN.md](D:/study-materials-summer-2026/EXE202/zero-mail/.planning/phases/02A-mail-ingestion/02A-02-PLAN.md:168).
- **LOW - Controller still lacks explicit missing `messageId` handling.** Authenticated malformed envelopes with null `messageId` can fall into DB not-null failure instead of 200-drop.
- **LOW - Plan 04 still mentions generated `apps/web/openapi/openapi.json` but does not list it in `files_modified`.**

**Suggestions**

- Add Gradle dependency updates explicitly: version catalog library aliases plus `google-auth-library-oauth2-http` where `TokenVerifier` is used, and Gmail API/client dependencies where `GmailApiClientFactory` lives.
- In Plan 04, update the settings-page mount condition or render `ReconnectPrompt` unconditionally and let it self-gate; add a page-level test for `CONNECTED + HISTORY_LOST`.
- Add `application-test.yml` or `@DynamicPropertySource` instructions for Pub/Sub properties, and add OpenAPI dummy args in `backend/api/build.gradle.kts`.
- Make `GmailApiClientFactory` test-configurable for token URL, Gmail root URL/transport, and clock, or require worker tests to inject a fake factory.
- Add wrong-issuer OIDC test coverage and a missing-`messageId` malformed-envelope test.

**Risk Assessment**

Overall risk: **HIGH**. The two prior security HIGHs are closed, but the current plan set still has a compile-blocking dependency gap and a user-visible MAIL-05 gap that can leave history-loss recovery invisible in the actual app.

CURRENT_HIGH_COUNT: 2

### Current HIGH Concerns

- Missing Google/Gmail Gradle dependency changes for the new API/core classes, likely blocking `compileJava`.
- ReconnectPrompt ingestion-health gate is planned at the component level, but the current parent only mounts it for `DISCONNECTED`, so `CONNECTED + HISTORY_LOST/WATCH_UNHEALTHY` may not show the reconnect prompt.

---

## Consensus Summary

Only Codex was invoked in Cycle 4, so the consensus summary reflects a single external review.

### Agreed Strengths

- The two Cycle 3 HIGH concerns are fully resolved at the current plan-text level.
- The Wave 0 test spine is strong, especially around tenant isolation, OIDC rejection, idempotency, pause behavior, and reconnect behavior.
- The phase decomposition remains coherent, and final verification now includes backend, frontend, architecture, and disabled-test checks.

### Agreed Concerns

- HIGH: New Google/Gmail and token-verifier code is planned, but the required Gradle dependency updates are not listed in the plans.
- HIGH: The MAIL-05 reconnect prompt may still be hidden for `CONNECTED + HISTORY_LOST/WATCH_UNHEALTHY` because the current settings page only mounts `ReconnectPrompt` for `DISCONNECTED`.
- MEDIUM: Pub/Sub test properties, hermetic Gmail/OAuth seams, checked exception handling, malformed message handling, and generated OpenAPI file tracking still need tighter plan instructions.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 3 HIGH concerns: 2
- Fully resolved prior HIGH concerns: 2
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 4 HIGH concerns: 2
- Current unresolved HIGH concerns: 2

