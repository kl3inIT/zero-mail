# Zero Mail — Privacy Policy (Draft)

> Draft for CASA submission. Final hosted version replaces all _TBD_ placeholders before
> "Submit for verification" is clicked in Google Cloud Console.

## What Zero Mail does with your Gmail data

- We request the Gmail `modify` scope only to **label**, **archive**, and **save drafts** based on rules you author. We **never auto-send**.
- We do **not** store raw email bodies, LLM prompts, LLM completions, or embeddings long-term. Message content is held only in short-lived in-memory caches during triage and is discarded after each request.
- Metadata we do store: sender, subject, thread id, triage action, rule id, timestamp. Enough to support audit + undo (Phase 4) and analytics (Phase 5) — not enough to reconstruct message content.

## Tokens and encryption

- OAuth refresh tokens are encrypted at rest with **AES-GCM-256**. Encryption keys live in **GCP Secret Manager** and never touch the application database.
- The encryption envelope binds `tenantId` as Additional Authenticated Data so a stolen ciphertext from one tenant cannot be replayed against another.

## User control

- **Disconnect Gmail** (in `/settings`): removes the grant; no further Gmail API calls are made on your behalf.
- **Delete account** (in `/settings`): cascades to all rows in `tenants`, `users`, `gmail_connections`, and `onboarding_selections`. Verified by an end-to-end integration test (`AccountDeletionE2ETest`).
- **Revoke at any time** from your Google Account's Third-Party Apps page. If Google returns `invalid_grant` on the next refresh, Zero Mail flips your connection to `DISCONNECTED` and prompts you to reconnect (AUTH-05; verified by `DisconnectOnInvalidGrantTest`).

## Logging and observability

- Sensitive content (email bodies, prompts, completions, refresh tokens, user-PII fragments) is held under a `Sensitive<T>` wrapper whose `toString()` redacts to `***REDACTED***`.
- A Logback `TurboFilter` (`SensitiveMarkerScrubFilter`) inspects every log event for stray `Sensitive(...)` tokens that bypassed the wrapper, and stamps `scrubbed=true` MDC keys for SOC alerting.
- ArchUnit tests (`SafetyContractArchTests`) prevent code from logging through types that have not been wrapped in `Sensitive`.
- A real-request synthetic-traffic integration test (`LogScrubSyntheticTrafficTest`) drives `/me`, `/tenant/status`, and `/onboarding/select-template` against seeded sentinel values and asserts zero leakage in the captured log stream.

## Data residency, retention, and incident response

- Primary datastore: PostgreSQL on Google Cloud SQL (region per the deployment record).
- Backup retention follows the Cloud SQL default (7 days) plus weekly snapshots; backups are stored in the same region as the primary instance.
- Incident response contact: _TBD — populated from the Google Cloud Console support-email field._

## Contact

- Privacy contact: _TBD_
- Security contact (responsible disclosure): _TBD_
