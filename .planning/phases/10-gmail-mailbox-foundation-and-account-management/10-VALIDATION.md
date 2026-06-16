---
phase: 10
slug: gmail-mailbox-foundation-and-account-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `10-RESEARCH.md` § Validation Architecture. The highest-risk invariants —
> tenant/mailbox isolation, fail-closed ownership, token-cache correctness, migration backfill
> integrity, AAD continuity — map to concrete automated checks below.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test 4.x; Testcontainers Postgres for migration/repo tests; `RestClient + @LocalServerPort` for HTTP (NOT MockMvc — so servlet filters / ScopedValue bind) |
| **Config file** | Gradle `:backend:core:test` / `:backend:api:test`; project skills `spring-jpa-testing`, `spring-security-testing`, `spring-mvc-testing` encode Boot 4 / Security 7 API specifics |
| **Quick run command** | `./gradlew :backend:core:test --tests "*GmailConnection*" --tests "*MailboxArch*"` |
| **Full suite command** | `./gradlew :backend:core:test :backend:api:test` |
| **Estimated runtime** | ~Testcontainers-bound (first run pulls Postgres image); steady-state core+api suite minutes-scale |

---

## Sampling Rate

- **After every task commit:** Run the area-specific quick run (`--tests "*<Feature>*"`) for the touched code.
- **After every plan wave:** Run `./gradlew :backend:core:test :backend:api:test`.
- **Before `/gsd-verify-work`:** Full suite green AND the migration test against a real old-single-account fixture must pass.
- **Max feedback latency:** area quick run = seconds-to-low-minutes; full suite per wave merge.

---

## Per-Task Verification Map

| Requirement | Wave | Behavior | Test Type | Automated Command | File Exists |
|-------------|------|----------|-----------|-------------------|-------------|
| VER-01 / WSP-02 | 0→ | Migration preserves old single-Gmail tenant; backfills exactly one primary; tokens byte-identical | integration (Testcontainers + Liquibase) | `./gradlew :backend:core:test --tests "*Migration119*"` | ❌ W0 |
| GMA-06 | 0→ | Two CONNECTED mailboxes coexist; duplicate active email fails closed (23505 → 409) | integration (@DataJpaTest + real DB) | `./gradlew :backend:core:test --tests "*DuplicateActiveEmail*"` | ❌ W0 |
| GMA-03 | 0→ | Exactly-one-primary enforced; set-primary clears old + sets new transactionally | integration | `./gradlew :backend:core:test --tests "*SetPrimary*"` | ❌ W0 |
| WSP-05/06 | 0→ | `resolveOwnedConnectionOrThrow` → 404 not-owned/missing, 409 disconnected | integration (RestClient + @LocalServerPort) | `./gradlew :backend:api:test --tests "*MailboxOwnership*"` | ❌ W0 |
| D-10 (token cache) | 0→ | Cache keyed by gmailConnectionId; mailbox B never gets mailbox A's token | unit (mocked refresh) | `./gradlew :backend:core:test --tests "*GmailApiClientFactory*"` | partial |
| D-11 (AAD continuity) | 0→ | Existing ciphertext decrypts after migration (AAD = tenantId unchanged) | integration | `./gradlew :backend:core:test --tests "*RefreshTokenCipherContinuity*"` | ❌ W0 |
| GMA-07 / WR-06 | 0→ | Three OAuth intents route through success/failure handlers correctly | slice (test-profile SecurityConfig) | `./gradlew :backend:api:test --tests "*OAuthIntentRouting*"` | ❌ W0 |
| D-01 (intent shim) | 0→ | Intent survives callback via session (incl. Redis-dirty `setAttribute`) | integration | `./gradlew :backend:api:test --tests "*IntentCarryingRepository*"` | ❌ W0 |
| GMA-05 | 0→ | Disconnect calls users.stop + revoke + status flip; idempotent; primary handling | unit + integration | `./gradlew :backend:core:test --tests "*GmailConnectionServiceDisconnect*"` | partial |
| D-13 | 0→ | ArchUnit: only non-empty allow-list calls `buildClientForTenant` | arch | `./gradlew :backend:core:test --tests "*GmailClientLookupBoundary*"` | ❌ W0 |
| Privacy | 0→ | No raw email/token in logs on add/reconnect/disconnect | review + log-assert | existing privacy-sweep pattern (`TriagePrivacySweepTest`) | pattern exists |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky — set per task during execution.*

---

## Wave 0 Requirements

- [ ] `Migration119Test` — Testcontainers Postgres, apply through changeset 119, assert backfill + byte-identical tokens (VER-01)
- [ ] `DuplicateActiveEmailTest` — partial unique index fires; constraint-name → 409 (GMA-06)
- [ ] `SetPrimaryTransactionalTest` + exactly-one-primary index (GMA-03)
- [ ] `MailboxOwnershipSeamTest` — 404/409 contract (WSP-05/06)
- [ ] `GmailApiClientFactoryMailboxCacheTest` — two-mailbox token isolation (D-10)
- [ ] `RefreshTokenCipherContinuityTest` — AAD unchanged (D-11)
- [ ] `OAuthIntentRoutingTest` + test-profile SecurityConfig slice (GMA-07 / WR-06)
- [ ] `IntentCarryingRepositoryTest` — session survival + Redis dirty `setAttribute` (D-01)
- [ ] `GmailClientLookupBoundaryTest` — ArchUnit allow-list (D-13)
- [ ] Shared fixture: an "old single-account" `gmail_connections` seed for migration/continuity tests

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real Google OAuth add-mailbox / reconnect round-trip | GMA-01/04/07 | Requires live Google consent + real refresh token; not mockable in unit suite | Deferred to Phase 11 real-Gmail e2e smoke on dev VPS (per CONTEXT — not a Phase 10 backend-foundation gate). Phase 10 proves intent routing via test-profile SecurityConfig slice instead. |

---

## Validation Sign-Off

- [ ] All tasks have an automated verify or a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references above
- [ ] No watch-mode flags in any test command
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
