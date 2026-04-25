# Scopes Justification — `https://www.googleapis.com/auth/gmail.modify`

## Why this scope (minimum necessary)

Zero Mail's core function is automated triage: **labeling**, **archiving**, and **drafting replies** based on user-authored rules. These three actions require the `gmail.modify` scope; the read-only scope `gmail.readonly` would leave archive and label unavailable and force users into a supervisory role that defeats the product's value proposition. We considered and rejected `gmail.send` — see "What we do NOT do" below.

## What we do NOT do with this scope

- **No sending.** Auto-send is prohibited at the gateway layer (TRG-03, Phase 4) and enforced by an ArchUnit ban on the `SEND` action keyword in any rule executor. Drafts are saved into Gmail's Drafts mailbox and require human review before send.
- **No long-term storage of email bodies.** Body content touches only short-lived in-memory caches during LLM calls (Phase 2C) and is discarded after each request. Persistent storage retains only metadata: sender, subject, thread id, triage action, rule id, timestamp.
- **No extraction of content beyond metadata** needed for audit + analytics. We do not build a searchable archive of message content.
- **No embeddings of user mail.** Vector storage is deferred (CLAUDE.md privacy constraint); RAG is performed only over user-authored *rule text*, not user mail.

## Data handling posture

- **Refresh tokens**: encrypted at rest with **AES-GCM-256**, key held in **GCP Secret Manager** with `tenantId` as Additional Authenticated Data (Phase 1, plan 06). The application database never holds plaintext refresh tokens.
- **Tenant isolation**: enforced by `ScopedValue<String> TenantContext.TENANT` (Java 25) bound by `TenantBindingFilter` after Spring Security authentication, plus Hibernate `@TenantId` filters on every tenant-owned entity, plus an ArchUnit ban on `ThreadLocal` in tenant-scoped paths, plus a 100-tenant concurrent leak test on virtual threads (`MultiTenantLeakIntegrationTest`) that proves no cross-tenant observation under load (Phase 1, plans 02 + 05).
- **Log safety**: `Sensitive<T>` wrapper + Logback `SensitiveMarkerScrubFilter` (TurboFilter) + ArchUnit rules preventing email bodies, prompts, completions, or refresh tokens from reaching logs (Phase 1, plan 03). Verified at runtime by `LogScrubSyntheticTrafficTest` driving real `/me`, `/tenant/status`, `/onboarding/select-template` traffic against seeded sentinel values.

## Retention / deletion / revocation (AUTH-03, AUTH-05)

### AUTH-03 — Account-delete cascades (Phase 1, plan 07)

`DELETE /me/account` (`AccountDeletionController`, `@Transactional`) cascades through:
1. `onboarding_selections` (per tenant) → deleted
2. `gmail_connections` (per tenant) → deleted
3. `users` (per tenant, single-seat in v1) → deleted
4. `tenants` (the tenant itself) → deleted

Verified by `AccountDeletionE2ETest`, which seeds all four tables and asserts zero rows remain post-call.

### AUTH-05 — `invalid_grant` lazy DISCONNECTED + reconnect UX (Phase 1, plans 05 + 08)

Externally-revoked grants surface as `DISCONNECTED` on the *next* outbound request — Zero Mail does not poll Google for revocation state. The mechanism:

1. Spring Security's refresh-token client is wrapped by `DisconnectDetectingRefreshTokenClient` (delegates to `RestClientRefreshTokenTokenResponseClient` in Spring Security 7).
2. When Google returns `invalid_grant`, the wrapper publishes an `OAuth2TokenRefreshFailed(tenantId, "invalid_grant", at)` Spring application event and rethrows.
3. `GmailAccessGuard` (`@EventListener @Transactional`) handles the event by binding the tenant via `ScopedValue`, looking up the connection, flipping `gmail_connections.status = DISCONNECTED`, setting `disconnected_at`, and publishing `GmailConnectionRevokedEvent` for downstream UI reactions.
4. The frontend's `/settings` page polls `/tenant/status`; when the response shows `DISCONNECTED`, it renders `<ReconnectPrompt>` with a Reconnect-Gmail button that re-runs the `/oauth2/authorization/google-gmail` second-leg flow.

This means a user who revokes the grant in their Google Account dashboard sees the disconnected state and a reconnect affordance the next time they touch the app — without Zero Mail needing to poll Google for revocation. Verified by `DisconnectOnInvalidGrantTest` (publishing the event flips the row to `DISCONNECTED` in a single transaction).

## Operational evidence summary

| Claim | Evidence |
|-------|----------|
| No auto-send | TRG-03 + ArchUnit ban (Phase 4); product surface confirms |
| No long-term body storage | CLAUDE.md constraint; `LogScrubSyntheticTrafficTest` |
| AES-GCM refresh-token encryption | Plan 06 (`RefreshTokenCipher`, `NonceUniquenessTest`) |
| `@Sensitive` / ArchUnit log contract | Plan 03 (`SafetyContractArchTests`, `SensitiveMarkerScrubFilter`) |
| Multi-tenant safety under load | `MultiTenantLeakIntegrationTest` (100 tenants, virtual threads) |
| AUTH-03 delete cascade | `AccountDeletionE2ETest` |
| AUTH-05 lazy DISCONNECTED on `invalid_grant` | `DisconnectOnInvalidGrantTest` |
