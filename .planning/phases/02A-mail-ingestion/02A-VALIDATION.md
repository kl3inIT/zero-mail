---
phase: 02A
slug: mail-ingestion
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-28
---

# Phase 02A — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (backend); Vitest 4.1.5 (frontend) |
| **Config file** | `backend/build.gradle.kts` test task; `apps/web/vitest.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test :backend:api:test :backend:worker:test` |
| **Full suite command** | `./gradlew clean check && pnpm -F web run test:run && pnpm -F web run typecheck && pnpm -F web run lint && pnpm -F web run i18n:check` |
| **Estimated runtime** | ~210 seconds (backend ~120s, frontend test ~10s, type/lint/i18n ~35s) |

---

## Sampling Rate

- **After every task commit:** Run quick command (the relevant module subset)
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green; ApplicationModulesTest + DomainBoundaryArchTests pass
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

> Filled by gsd-planner during plan creation. Each task carries `<read_first>` + `<acceptance_criteria>` + `<automated>` blocks.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02A-00 | 02A-00-PLAN.md | 0 | MAIL-01..MAIL-06 | T-01, T-05 | RED scaffolds cover Pub/Sub OIDC, idempotency, Gmail history/watch, pause UX, and reconnect UX | Backend JUnit + Vitest scaffolds | `./gradlew clean check`; `pnpm -F web run test:run` | Yes | ✅ green |
| 02A-01 | 02A-01-PLAN.md | 1 | MAIL-01, MAIL-03, MAIL-05, MAIL-06 | T-05 | Schema, entities, enum, tenant-scoped claim semantics | JUnit persistence tests | `./gradlew :backend:core:test --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*" --tests "*GmailIngestionHealthTest*"` | Yes | ✅ green |
| 02A-02 | 02A-02-PLAN.md | 2 | MAIL-02, MAIL-03, MAIL-05 | T-05 | Gmail watch renewal and history processing are idempotent, tenant-bound, and covered for no-tenant-context global scans | Worker JUnit tests | `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` | Yes | ✅ green |
| 02A-03 | 02A-03-PLAN.md | 2 | MAIL-01, MAIL-04, MAIL-06 | T-01 | Pub/Sub OIDC rejects invalid tokens before business logic; API exposes pause and health | API integration tests | `./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" --tests "*MeControllerTest*" --tests "*TriagePauseControllerTest*" --tests "*PubSubIdempotencyTest*"` | Yes | ✅ green |
| 02A-04 | 02A-04-PLAN.md | 3 | MAIL-05, MAIL-06 | T-05 | Frontend pause banner, settings toggle, reconnect prompt, and i18n keys are wired | Vitest + type/lint/i18n | `pnpm -F web run test:run`; `pnpm -F web run typecheck`; `pnpm -F web run lint`; `pnpm -F web run i18n:check` | Yes | ✅ green |
| 02A-05 | 02A-05-PLAN.md | 4 | MAIL-01..MAIL-06 | T-01, T-05 | Final Nyquist sweep proves all automated acceptance gates are green | Full backend/frontend verification | `./gradlew clean check`; `pnpm -F web run test:run && pnpm -F web run typecheck && pnpm -F web run lint && pnpm -F web run i18n:check` | Yes | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 RED scaffolds (per RESEARCH.md `## Validation Architecture`):

- [x] `backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java` — OIDC verification: valid token PASSES, wrong audience/email/issuer/expired/bad-signature 401; non-`/internal/pubsub/**` path skips the filter (7/7 GREEN)
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java` — RestClient + LocalServerPort: end-to-end push receiver including OIDC + tenant lookup + dedup INSERT (6/6 GREEN)
- [x] `backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java` — UNIQUE (tenant_id, pubsub_message_id) round-trip + ON CONFLICT DO NOTHING semantics + expired PROCESSING row reclaim + global claim without TenantContext (5/5 GREEN)
- [x] `backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java` — composite PK round-trip + TEXT[] label_ids round-trip + `@TenantId` cross-tenant JPA-read isolation (4/4 GREEN)
- [x] `backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java` — IdentifiedEnum contract: id() + fromId fail-loud (4/4 GREEN)
- [x] `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java` — fan-out semantics, metadata fetch before INBOX filtering, history-404 -> HISTORY_LOST, monotonic last_synced_history_id, ScopedValue binding per row (7/7 GREEN)
- [x] `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java` — initial register, global renewal scan without TenantContext, renew at <24h without advancing existing `last_synced_history_id`, HISTORY_LOST preserved on renewal, 3-strike unhealthy threshold, INBOX-only labelIds (7/7 GREEN)
- [x] `backend/api/src/test/java/com/zeromail/api/support/MockGoogleOidcServer.java` — hermetic JWKS + signed synthetic ID tokens (testkit fixture)
- [x] `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` — hermetic Gmail history.list + watch + stop responder
- [x] `apps/web/features/triage/components/PauseBanner.test.tsx` — conditional render when triagePaused=true; unpause CTA wired (GREEN)
- [x] `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` — me-key invalidation after success (GREEN)
- [x] `apps/web/__tests__/architecture/phase-02a-files.test.ts` — file-presence guard for new feature folder + i18n key parity (GREEN)
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java` — Wave 0 scaffold enabled: me_response_contains_triagePaused_field, me_response_contains_gmailConnectionStatus_with_ingestionHealth, me_response_json_shape_serializes_cleanly (4/4 GREEN including missing-auth guard)
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java` — Wave 0 scaffold enabled: putTriagePause_true_persists_triage_paused, putTriagePause_false_clears_triage_paused (3/3 GREEN including missing-auth guard)
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java` — Wave 0 scaffold enabled: duplicatePushMessage_sameMessageId_onlyOnePubSubDeliveryRow, unknownEmailAddress_returns200_noPubSubDeliveryRow (2/2 GREEN)
- [x] `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` — Wave 0 scaffold enabled: renders when ingestionHealth is WATCH_UNHEALTHY/HISTORY_LOST, does NOT render when HEALTHY (GREEN, no `it.skip`)
- [x] `backend/api/src/test/java/com/zeromail/api/security/OAuthProvisioningRaceAtomicityTest.java` — Review hardening: ordinary login without refresh token preserves `HISTORY_LOST`, `last_synced_history_id`, and `watch_history_id` (3/3 GREEN)

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end Pub/Sub delivery on staging VPS | MAIL-01, MAIL-04 | Requires real GCP Pub/Sub topic + subscription pointed at deployed VPS endpoint with OIDC token signed by GCP | (1) Provision staging Pub/Sub topic + push subscription to `https://staging.zeromail.example/internal/pubsub/gmail`; (2) Connect a test Gmail account; (3) Send a test email to that account; (4) Observe `mail_message_observed` row appears within 10s; (5) Replay the same Pub/Sub message via gcloud CLI → verify ON CONFLICT DO NOTHING (no duplicate row). |
| `users.watch` 7-day expiry renewal | MAIL-02 | Cannot fast-forward 7 days in test; renewal is `< NOW() + INTERVAL '24 hours'` cron-driven | Manually backdate `gmail_connections.watch_expires_at` to `NOW() + 23 hours` for a test row; observe `GmailWatchScheduler` re-issues `users.watch` within 60s; verify `watch_renewed_at` advances. |
| Reconnect prompt UX after history-404 | MAIL-05 | Triggering Gmail's actual history-404 requires letting historyId age past 7 days OR forcing a new watch with a fresh historyId baseline; both impractical in CI | Local: stub `gmail.users().history().list(...)` to throw `HttpResponseException(404)`; verify `ingestion_health = HISTORY_LOST` + ReconnectPrompt visible in UI; click "Reconnect" → `/tenant/connect-gmail` flow runs. Smoke-tested with Playwright stubbed-server in CI; visual confirmation manual. |
| Pause toggle visual hierarchy in Settings + persistent banner | MAIL-06 | Visual quality / no-functional aspect | Open `/settings`, toggle Pause; verify Card section renders with token-aware styling; verify `<PauseBanner>` appears above page content non-dismissibly; click "Unpause" inline → toggle clears + banner hides. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter
- [x] Hermetic test fixtures in place: `MockGoogleOidcServer`, `MockGmailHistoryServer`
- [x] PostgresContainerTest harness reused (Phase 01.2.1) — no new test infra
- [x] `wave_0_complete: true` flipped after Wave 0 RED scaffolds land

**Approval:** automated closure complete; staging-only manual verifications remain pending below.

---

## Validation Audit 2026-05-05

| Metric | Count |
|--------|-------|
| Gaps found | 3 |
| Resolved | 3 |
| Escalated | 0 |

Resolved gaps:

- Added explicit `PubSubDeliveryEntityTest.globalClaimPendingBatch_withoutTenantContext_claimsRowsForWorkerFanout` coverage for the global worker claim path.
- Added explicit `GmailWatchSchedulerTest.tick_withoutTenantContext_processesGlobalRenewalScan` coverage for global watch-renewal scans.
- Added `OAuthProvisioningRaceAtomicityTest.ordinaryLogin_withoutRefreshToken_preservesHistoryLostConnectionState` to lock the ordinary-login vs explicit-reconnect distinction.

Additional validation repair:

- Narrowed `ControllerBoundaryArchTests.controllers_do_not_touch_entities` to project persistence entities only, fixing the false positive where Spring `ResponseEntity` matched `.*Entity`.

Fresh audit verification:

- `.\gradlew.bat clean check` — PASS.
- `pnpm -F web run test:run` — PASS: 27 files, 151 tests.
- `pnpm -F web run typecheck` — PASS.
- `pnpm -F web run lint` — PASS.
- `pnpm -F web run i18n:check` — PASS: vi/en parity, 318 leaf keys.

---

## Manual Verification Replay Summary

These items remain intentionally pending for a staging VPS / real Google environment:

1. End-to-end Pub/Sub delivery on staging VPS: provision a real push subscription to `/internal/pubsub/gmail`, send mail to a connected test account, verify one `mail_message_observed` row, then replay the same message and verify no duplicate row.
2. `users.watch` 7-day expiry renewal: backdate `gmail_connections.watch_expires_at` to within 24h, run the worker, and verify `watch_renewed_at` advances without corrupting `last_synced_history_id`.
3. Reconnect prompt after history-404: force/stub a Gmail history 404, verify `ingestion_health = HISTORY_LOST`, confirm `ReconnectPrompt` is visible, then click reconnect and verify the OAuth path runs.
4. Pause toggle visual hierarchy: open `/settings`, toggle pause, verify the persistent banner appears above protected page content, then unpause inline and confirm the banner hides.
