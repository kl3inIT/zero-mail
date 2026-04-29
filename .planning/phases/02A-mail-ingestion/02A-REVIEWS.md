---
phase: 02A
reviewers: [codex]
reviewed_at: 2026-04-29T09:36:40.8700833+07:00
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
---

# Cross-AI Plan Review - Phase 02A

## Codex Review

### Summary

The phase is well decomposed and covers the right major surfaces: schema, OIDC-secured Pub/Sub ingress, idempotent worker processing, watch renewal, pause UX, and final verification. The strongest parts are the explicit privacy floor, ack-fast controller design, and separation between API and worker responsibilities. The main risk is that several plans are internally inconsistent or cross-dependent in ways that will break compilation, tests, or tenant isolation unless corrected before execution.

### Strengths

- The ack-fast Pub/Sub design is appropriate for Pub/Sub push semantics and avoids Gmail API work inside the request path.
- OIDC verification is treated as a first-class security deliverable, with explicit wrong-audience, wrong-email, expired-token, and bad-signature coverage.
- The schema keeps `mail_message_observed` privacy-safe: no subject, sender, body, snippet, or recipient.
- Worker processing is correctly framed as idempotent and at-least-once, with monotonic history pointer advancement.
- The pause feature is scoped correctly for Phase 2A: persist and expose the flag now, enforce write-action gating later in Phase 4.
- The frontend plan preserves the unified reconnect prompt instead of exposing low-level ingestion-health causes to users.

### Concerns

- **HIGH** Plan 00 places `MockGoogleOidcServer` under `backend/worker/src/test`, but API tests rely on it. `backend:api:test` will not normally see worker test classes.

- **HIGH** Plan 00 says `@Disabled` Java tests can reference missing methods/classes and still compile. They cannot. `@Disabled` only skips execution, not compilation.

- **HIGH** Plans 00 and 03 conflict on test security: API integration tests use `@ActiveProfiles("test")` and expect 401 from the Pub/Sub filter, but Plan 03 puts `PubSubSecurityConfig` behind `@Profile("!test")`.

- **HIGH** Plan 02 and Plan 03 are both Wave 2, but Plan 03 uses `TenantService.setTriagePaused`, which Plan 02 adds. Plan 03 should depend on Plan 02 or the tenant-service change should move earlier.

- **HIGH** `PubSubDeliveryRepository.claimPendingBatch()` uses `FOR UPDATE SKIP LOCKED`, but the transaction ends when the repository method returns. Locks are released before `GmailDeliveryProcessingService.processDelivery()` runs, so multiple workers can claim the same rows.

- **HIGH** `PubSubIngestionService.ingestPushEnvelope()` starts a transaction before binding `TenantContext`, then performs tenant lookup and insert inside that transaction. With tenant-scoped entities, this risks broken filtering or wrong tenant context at EntityManager creation time.

- **HIGH** `GmailConnectionRepository.findByGoogleEmailLower()` queries a tenant-owned entity before tenant binding. If Hibernate tenant filtering applies, the lookup may return nothing or behave incorrectly. This needs an explicit unscoped lookup path, likely native SQL/JdbcTemplate.

- **HIGH** Plan 02 does not set `last_synced_history_id` from `watch_history_id` on initial watch success. If the first delivery starts at the webhook history id while `last_synced_history_id` is null, the first message window can be skipped.

- **HIGH** Idempotency via `observedRepository.save()` plus catching `DataIntegrityViolationException` is unsafe. JPA may throw on flush/commit, and the transaction may become rollback-only. Use native `INSERT ... ON CONFLICT DO NOTHING`.

- **HIGH** Plan 05 misses several Wave 0 tests in final verification: `MeControllerTest`, `TriagePauseControllerTest`, `PubSubIdempotencyTest`, and the skipped `ReconnectPrompt` tests. Disabled/skipped scaffolds should not count as GREEN closure.

- **HIGH** Plan 05 says `grep -c "Pub/Sub OIDC verification ceremony" .planning/STATE.md` must return 0, but then adds a new STATE entry containing the same phrase.

- **MEDIUM** `PubSubDeliveryEntity` extends `AbstractTenantOwnedEntity`, but the proposed `pubsub_delivery` DDL may be missing inherited columns such as `version` if the base class maps one.

- **MEDIUM** `MailMessageObservedEntity` intentionally does not extend `AbstractTenantOwnedEntity`, contradicting the canonical refs and losing automatic tenant filtering. If kept, every query must be explicitly tenant-scoped.

- **MEDIUM** `payload JSONB` is mapped as plain `String` without `@JdbcTypeCode(SqlTypes.JSON)` or a native cast. PostgreSQL may reject the insert depending on Hibernate binding.

- **MEDIUM** The watch retry policy is inconsistent. Context says retries continue after `WATCH_UNHEALTHY`; Plan 02 filters out `watch_consecutive_failures >= 3`, so unhealthy watches never self-recover.

- **MEDIUM** Reconnect recovery is incomplete. `clearForReconnect()` is added, but no plan updates the OAuth reconnect success handler to call it.

- **MEDIUM** Account deletion watch cleanup is mentioned in context, but the plans only extend `disconnect()`, not the account deletion path.

- **MEDIUM** Plan 03 returns `200 OK` for malformed Pub/Sub payloads, while Plan 00 expects `400`. Pick one behavior and align tests.

- **MEDIUM** Plan 04 references `settings.triage.pause.banner.body` in JSX but does not add that i18n key to the required key set.

- **MEDIUM** Plan 04 depends on OpenAPI-generated path types for `PUT /tenant/triage-pause`, but `schema.d.ts` is not listed in `files_modified`, and regen is optional.

- **LOW** Plan 00 repeatedly miscounts files: 16 files are listed, but the objective text describes totals that do not add up.

- **LOW** Plan 01 acceptance says `010` should contain `addColumn:` six times, but the YAML uses one `addColumn` with six column entries.

### Suggestions

- Move shared test fixtures to the module that uses them, or create a proper test-fixtures source set shared by API and worker tests.
- Decide whether Wave 0 tests should be compile-red or execution-red. Prefer execution-red tests that compile, using reflection or TODO-disabled bodies without missing symbol calls.
- Make Pub/Sub security active in integration tests, with test-only OIDC properties and a mock JWKS verifier hook.
- Split Pub/Sub ingestion into an unscoped tenant lookup followed by a tenant-bound transactional insert. Avoid starting the transaction before `TenantContext` is bound.
- Replace claim-by-select with atomic claim semantics: `UPDATE ... SET status='PROCESSING', locked_until=... WHERE id IN (...) RETURNING *`, or keep the claim and processing in one transaction.
- Add native repository methods for `INSERT ... ON CONFLICT DO NOTHING` for both `pubsub_delivery` and `mail_message_observed`.
- On watch success, initialize or advance `last_synced_history_id` to the returned `watch_history_id` when appropriate.
- Add the OAuth reconnect-handler update and account-deletion watch-stop update to the relevant plan.
- Include all files actually edited in each plan frontmatter, especially repositories, projections, generated OpenAPI schema, `ROADMAP.md`, and any `TenantContext` helper.
- In Plan 05, require disabled/skipped RED scaffolds to be enabled or explicitly documented as deferred before marking Nyquist complete.

### Risk Assessment

Overall risk: **HIGH**.

The architecture is directionally sound, but execution risk is high because several plan dependencies and transaction boundaries are wrong enough to cause test-profile failures, duplicate worker processing, tenant-context issues, or false verification closure. Fixing the ordering, tenant lookup, SKIP LOCKED claim semantics, and final verification criteria would reduce the phase to MEDIUM risk.

---

## Consensus Summary

Only the Codex reviewer was requested and invoked for this cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

### Agreed Strengths

- The phase covers the correct major delivery surfaces for Gmail ingestion: OIDC-protected Pub/Sub ingress, durable idempotency, worker processing, watch renewal, pause state, reconnect visibility, and verification.
- The privacy boundary is strong: observed-message storage intentionally avoids raw email content.
- The API/worker split and ack-fast push receiver are directionally appropriate for Pub/Sub push semantics.

### Agreed Concerns

- HIGH: Wave 0 test scaffolds have compile and module-visibility problems that can break the intended RED baseline before implementation starts.
- HIGH: Pub/Sub security tests and implementation disagree about whether the OIDC filter is active in the test profile.
- HIGH: Tenant binding and unscoped Gmail-account lookup need clearer transaction boundaries before Pub/Sub ingestion can be trusted in a multi-tenant app.
- HIGH: Worker claim/idempotency semantics are not yet robust enough for at-least-once Pub/Sub delivery.
- HIGH: Plan 05 verification can falsely close the phase because it omits or discounts several Wave 0 tests.

### Divergent Views

- None observed. Only one reviewer was invoked.
