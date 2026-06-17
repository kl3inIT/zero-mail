# Phase 10: Gmail Mailbox Foundation and Account Management - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-08
**Phase:** 10-gmail-mailbox-foundation-and-account-management
**Areas discussed:** OAuth flow split, Mailbox-scoped request guard, Duplicate-active + primary marker (DB shape), GmailApiClientFactory mailbox-aware
**Mode:** advisor (calibration tier `full_maturity`; NON_TECHNICAL_OWNER=false — technical owner, no product-outcome reframing). Each area researched by a parallel `gsd-advisor-researcher` agent.

> Note: `/gsd-spec-phase 10` ran earlier this session but did not finalize a SPEC.md. Three decisions from that round (migration scope, projection AAD, backend-only surface) are carried into CONTEXT.md as D-00a/b/c.

---

## OAuth flow split (first-login / add / reconnect)

| Option | Description | Selected |
|--------|-------------|----------|
| B+D+E: attributes-based intent | intent+targetMailboxId in `OAuth2AuthorizationRequest.attributes`, persisted by `AuthorizationRequestRepository`; path-separated triggers; session-presence discriminator; single bundled registration kept | ✓ |
| C: signed-state HMAC token | Self-contained signed state; survives session loss but duplicates framework `state` and matches the separate-endpoint architecture locked against | |
| A/E: callback inference + minimal param | Lightweight, no attribute plumbing; cannot target a specific mailbox for reconnect | |

**User's choice:** "best practice là được" (delegated to Claude) → **B+D+E** selected per research recommendation.
**Notes:** Only option preserving locked single-registration + framework CSRF/state while authenticating intent against the live session. Add path INSERTs a new row and branches before `OAuthProvisioningService`.

---

## Mailbox-scoped request guard (how much in Phase 10)

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal seam now, full ScopedValue filter Phase 11 | P10 ships `resolveOwnedConnectionOrThrow` with fixed 404/409 contract + path segment; full `MailboxContext` filter in P11 | ✓ |
| Full MailboxContext ScopedValue filter now | Build whole guard infra in P10 (no consumers yet to validate it) | |
| Service-layer validation only | Pass connectionId to each service; not fail-closed at edge | |

**User's choice:** Minimal seam now, full ScopedValue filter Phase 11.
**Notes:** Argument-resolvers/interceptors bind after the filter chain (too late for the Hibernate session per `GmailAccessGuard`); the filter-bound ScopedValue is the locked end-state, deferred to P11 where real consumers exist.

---

## Duplicate-active + primary marker (DB shape)

| Option | Description | Selected |
|--------|-------------|----------|
| A1+B1: partial unique indexes | Drop `uq_gmail_connections_tenant_id`; partial unique `(tenant_id, lower(google_email)) WHERE status='CONNECTED'`; `is_primary` boolean + partial unique `WHERE is_primary`; app-level catch for clear error | ✓ |
| A2+B1: normalized/hash email column | Stored normalized column for Gmail dot/plus dedupe; logic in Java, host-gating risk | |
| B2: primary pointer on new workspace table | `primary_connection_id` FK; needs a new account-mgmt table | |

**User's choice:** A1+B1.
**Notes:** DB-enforced (matches money/tenant invariant posture). Raw `sql:` changeset (Liquibase native createIndex can't do WHERE/lower). CONNECTED-scoping makes disconnect→reconnect legal. Token columns untouched → ciphertext byte-identical. A2 dot/plus normalization deferred.

---

## GmailApiClientFactory mailbox-aware

| Option | Description | Selected |
|--------|-------------|----------|
| A+C+E: MailboxRef + deprecated adapter + ArchUnit now | `buildClientForMailbox(MailboxRef(tenantId, connectionId))`; cache re-keyed by connectionId (AAD keeps tenantId); `buildClientForTenant` `@Deprecated` default adapter; non-empty ArchUnit allow-list shipped now | ✓ |
| B+E: two-UUID method + ArchUnit | `buildClientForConnectionId(tenantId, connectionId)`; two same-typed UUIDs = arg-swap footgun | |
| A signature now, defer ArchUnit → P11 | Rule would be a no-op until mailbox flows exist | |

**User's choice:** A+C+E.
**Notes:** Value object makes tenant-only call un-typable. Cache key changes but AES-GCM AAD stays `tenantId.toString()`. ArchUnit allow-list bites immediately (call sites already exist), drained per-consumer in Phase 11.

## Claude's Discretion

- OAuth flow split mechanism — user delegated; Claude chose B+D+E.

## Deferred Ideas

- All Phase 11 scope (ingestion routing, mailbox-owned rules, triage/outbound wiring, UI/switcher, Playwright).
- Gmail dot/plus normalization (research A2).
- Full `MailboxContext` ScopedValue filter (Phase 11).
- Phase 8 real-Gmail e2e smoke todo — reviewed, not folded (Phase 11 / pre-launch concern).
