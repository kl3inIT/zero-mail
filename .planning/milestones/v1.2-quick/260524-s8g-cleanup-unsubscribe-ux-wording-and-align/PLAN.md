---
status: completed
created: 2026-05-24
---

# Cleanup Unsubscribe UX And Working Set Alignment

## Goal

Make the cleanup unsubscribe screen understandable for users and align list, preview, and execution around the same recent Gmail working set.

## Scope

- Rename user-facing copy away from "campaign", "RFC 8058", and "suppression".
- Reduce "protect sender" prominence by moving it out of the primary row actions.
- Reuse a shared recent-100 Gmail working-set query for cleanup candidate list, preview, and worker execution.
- Keep backend safety constraints: no body persistence, no unsubscribe URL logging, no clicking body links, per-sender archive only after unsubscribe succeeds.

## Verification

- Backend targeted tests for cleanup candidate/preview working-set behavior.
- Web i18n build/check and typecheck.
- Real-browser cleanup page smoke test when local services allow it.
