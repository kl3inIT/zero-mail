---
phase: 02A
slug: mail-ingestion
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-28
---

# Phase 02A — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (backend); Vitest 3.x (frontend) |
| **Config file** | `backend/build.gradle.kts` test task; `apps/web/vitest.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test :backend:api:test :backend:worker:test --offline` |
| **Full suite command** | `./gradlew clean check && pnpm -F web run test:run && pnpm -F web run lint && pnpm -F web run typecheck` |
| **Estimated runtime** | ~180 seconds (backend ~120s, frontend ~30s, lint+typecheck ~30s) |

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
| (TBD by planner) | | | | | | | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 RED scaffolds (per RESEARCH.md `## Validation Architecture`):

- [ ] `backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java` — OIDC verification: valid token PASSES, wrong audience/email/issuer/expired/bad-signature 401; non-`/internal/pubsub/**` path skips the filter
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java` — RestClient + LocalServerPort: end-to-end push receiver including OIDC + tenant lookup + dedup INSERT
- [ ] `backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java` — UNIQUE (tenant_id, pubsub_message_id) round-trip + ON CONFLICT DO NOTHING semantics + expired PROCESSING row reclaim
- [ ] `backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java` — composite PK round-trip + TEXT[] label_ids round-trip + `@TenantId` cross-tenant JPA-read isolation
- [ ] `backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java` — IdentifiedEnum contract: id() + fromId fail-loud
- [ ] `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java` — fan-out semantics, metadata fetch before INBOX filtering, history-404 → HISTORY_LOST, monotonic last_synced_history_id, ScopedValue binding per row
- [ ] `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java` — initial register, renew at <24h without advancing existing `last_synced_history_id`, HISTORY_LOST preserved on renewal, 3-strike unhealthy threshold, INBOX-only labelIds
- [ ] `backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java` — hermetic JWKS + signed synthetic ID tokens (testkit fixture)
- [ ] `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` — hermetic Gmail history.list + watch + stop responder
- [ ] `apps/web/features/triage/components/PauseBanner.test.tsx` — conditional render when triagePaused=true; unpause CTA wired
- [ ] `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` — me-key invalidation after success
- [ ] `apps/web/__tests__/architecture/phase-02a-files.test.ts` — file-presence guard for new feature folder + i18n key parity
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java` — Wave 0 RED scaffold: me_response_contains_triagePaused_field, me_response_contains_gmailConnectionStatus_with_ingestionHealth, me_response_json_shape_serializes_cleanly (@Disabled pending Plan 03 impl)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java` — Wave 0 RED scaffold: putTriagePause_true_persists_triage_paused, putTriagePause_false_clears_triage_paused (@Disabled pending Plan 03 impl)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java` — Wave 0 RED scaffold: duplicatePushMessage_sameMessageId_onlyOnePubSubDeliveryRow, unknownEmailAddress_returns200_noPubSubDeliveryRow (@Disabled pending Plan 03 impl)
- [ ] `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` — Wave 0 RED scaffold: renders when ingestionHealth is WATCH_UNHEALTHY/HISTORY_LOST, does NOT render when HEALTHY (it.skip pending Plan 04 impl)

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter
- [ ] Hermetic test fixtures in place: `MockGoogleOidcServer`, `MockGmailHistoryServer`
- [ ] PostgresContainerTest harness reused (Phase 01.2.1) — no new test infra
- [ ] `wave_0_complete: true` flipped after Wave 0 RED scaffolds land

**Approval:** pending
