---
phase: 02A
review_cycle: 3
reviewers: [codex]
reviewed_at: 2026-04-29T10:16:54.2361054+07:00
replan_commit: 0b46c97
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 2
---

# Cross-AI Plan Review - Phase 02A (Cycle 3)

Only the Codex reviewer was requested and invoked for this cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

## Codex Review

### Summary

Cycle 3 resolves the three Cycle 2 HIGH concerns in the updated plan text. The plan set is substantially stronger: file tracking for Plan 03 is complete, invalid-grant disconnect is split from best-effort Gmail cleanup, and test-profile authenticated endpoints now have an explicit `TestSessionSupport` path. Remaining risk is concentrated in two new plan-level issues: tenant isolation for `mail_message_observed`, and Pub/Sub filter scoping outside the intended security chain.

### Strengths

- The Wave structure is coherent: schema first, worker/API in parallel, frontend after API, verification last.
- `PubSubIngestionService` correctly moves persistence out of the controller and uses unscoped `JdbcTemplate` only for the pre-tenant lookup.
- Native `INSERT ... ON CONFLICT DO NOTHING` is now consistently planned for delivery and observation idempotency.
- `markDisconnected()` now protects invalid-grant paths from best-effort `users.stop()` failures.
- Test-profile endpoint behavior is much better specified, including missing-auth negative tests.
- Plan 05 now requires all Wave 0 tests enabled and green, plus Modulith and ArchUnit verification.

### Prior HIGH Resolution

- **Plan 03 omitted helper files from `files_modified`: FULLY RESOLVED.**  
  Plan 03 now lists `IngestResult.java`, `GmailNotification.java`, `FlexibleLongDeserializer.java`, `GmailConnectionProjection.java`, `TestSessionSupport.java`, and the updated test files.

- **`disconnect()` best-effort `users.stop()` could block invalid-grant disconnect: FULLY RESOLVED.**  
  Plan 02 now introduces DB-only `markDisconnected(UUID)`, requires an explicit transaction boundary, makes user disconnect call `markDisconnected()` before `tryStopWatch()`, and requires invalid-grant paths to call `markDisconnected()` instead of `disconnect()`.

- **Test-profile `/me` and `/tenant/triage-pause` auth/tenant binding underspecified: FULLY RESOLVED.**  
  Plan 03 now defines `TestSessionSupport`, requires `X-Test-Subject` / `X-Test-Email`, binds `TenantContext`, excludes `/internal/pubsub/**`, adds missing-auth negative tests, and requires affected controller tests to import it.

### Concerns

- **HIGH - `mail_message_observed` loses automatic tenant isolation.**  
  Plan 01 explicitly says `MailMessageObservedEntity` does not extend `AbstractTenantOwnedEntity` because of composite PK incompatibility. That conflicts with the phase's cross-cutting tenant-scope invariant and leaves a tenant-owned audit table without `@TenantId` protection. Native inserts are explicit today, but the repository still extends `JpaRepository`, so future `findAll` / derived reads can cross tenants unless the plan adds a compensating guard.

- **HIGH - `PubSubOidcAuthFilter` scoping relies only on `securityMatcher`, but the filter is also a `@Component`.**  
  A servlet `Filter` bean can be auto-registered outside the Spring Security chain unless registration is disabled or `shouldNotFilter()` scopes it. If that happens, the Pub/Sub OIDC filter can run on `/me` and `/tenant/triage-pause`, returning 401 before `TestSessionSupport` or the normal user chain. The plan should explicitly prevent global servlet registration or add a path guard.

- **MEDIUM - Test-profile Pub/Sub properties are not globally accounted for.**  
  `PubSubSecurityConfig` is intentionally active under `test`, and `PubSubOidcAuthFilter` requires audience, service-account email, and cert URL properties. The plan does not clearly add test defaults for all API `@SpringBootTest` contexts, so unrelated API tests may fail at context startup unless every test supplies these properties.

- **MEDIUM - Worker Gmail/OAuth clients remain hard to test hermetically.**  
  Plan 00 introduces `MockGmailHistoryServer`, but Plan 02 hard-codes `https://oauth2.googleapis.com/token` and builds a standard Gmail client with trusted transport. Unless tests mock `GmailApiClientFactory`, the mock server cannot be used. The plan should define the seam explicitly.

- **MEDIUM - `GmailApiClientFactory.buildGmailClient` snippet is compile-fragile.**  
  `GoogleNetHttpTransport.newTrustedTransport()` throws checked exceptions. The planned method signature does not declare or handle them, so implementation needs a catch/wrap or `throws` adjustment.

- **MEDIUM - Watch renewal `FOR UPDATE SKIP LOCKED` does not claim the row.**  
  `findConnectionsNeedingWatchRenewal()` returns entities after the repository transaction releases locks. Multiple worker instances can renew the same connection concurrently. Duplicate `users.watch` calls are probably tolerable, but the plan should not imply the lock protects work after return.

- **MEDIUM - Native `String[]` binding for `text[]` may still need an explicit cast or JDBC array.**  
  `insertObservedIfAbsent` still uses `:labelIds` directly. The entity round-trip test is helpful, but the native insert path must be tested specifically.

- **LOW - Plan file tracking has smaller new misses.**  
  Plan 04 mentions regenerating `apps/web/openapi/openapi.json` but does not list it. Plan 05 edits `ROADMAP.md` but `files_modified` only lists `STATE.md`.

- **LOW - Push controller does not validate missing `messageId`.**  
  Valid Pub/Sub messages include it, but an authenticated malformed envelope with null `messageId` could hit a DB not-null error instead of the planned 200-drop behavior.

### Suggestions

- Keep `mail_message_observed` tenant-protected by using `@IdClass`, a surrogate ID plus unique `(tenant_id, gmail_message_id)`, or explicit repository restrictions plus ArchUnit tests banning unscoped reads.
- Make `PubSubOidcAuthFilter` non-globally registered, or implement `shouldNotFilter()` for non-`/internal/pubsub/**` paths.
- Add `backend/api/src/test/resources/application-test.yml` defaults for Pub/Sub OIDC properties, with integration tests overriding the cert URL to the mock JWKS server.
- Make `GmailApiClientFactory` configurable for token endpoint, Gmail root URL/transport, and clock, or require worker tests to inject a fake factory.
- Add a native-insert test that exercises `MailMessageObservedRepository.insertObservedIfAbsent` with multiple labels.
- Add `ROADMAP.md` and generated OpenAPI JSON to the relevant `files_modified` lists if those files are tracked.

### Risk Assessment

Overall risk: **HIGH** until the two new HIGH concerns are fixed. The phase design is otherwise solid and the Cycle 2 blockers are resolved, but tenant isolation and filter scoping are security-sensitive enough to block convergence.

### Current HIGH Concerns

- `mail_message_observed` is planned without `AbstractTenantOwnedEntity` / `@TenantId`, weakening tenant isolation for a tenant-owned audit table.
- `PubSubOidcAuthFilter` is a `@Component` without an explicit servlet-registration guard or `shouldNotFilter()`, so it may run outside `/internal/pubsub/**`.

CURRENT_HIGH_COUNT: 2

---

## Consensus Summary

Only Codex was invoked in Cycle 3, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 2's three HIGH concerns are fully resolved in the current plan text.
- The phase decomposition remains coherent and maps cleanly from schema to worker/API, frontend, and final verification.
- The plans now cover explicit RED scaffolds, native idempotency, DB-only disconnect handling, and test-profile authenticated endpoint behavior.

### Agreed Concerns

- HIGH: `mail_message_observed` is tenant-owned audit data but is planned without automatic `@TenantId` protection due to the composite primary key design.
- HIGH: `PubSubOidcAuthFilter` may be globally servlet-registered because it is a `@Component`; the plan needs an explicit guard so it only applies to `/internal/pubsub/**`.
- MEDIUM: Test-profile Pub/Sub properties, hermetic Gmail/OAuth seams, checked exception handling, watch renewal locking semantics, and native `text[]` binding need more precise implementation instructions.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle HIGH concerns: 3
- Fully resolved prior HIGH concerns: 3
- Partially resolved prior HIGH concerns: 0
- Unresolved prior HIGH concerns: 0
- New Cycle 3 HIGH concerns: 2
- Current unresolved HIGH concerns: 2
