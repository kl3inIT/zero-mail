---
id: SEED-011
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning Milestone 1.1 admin UI, support tooling, or CASA operations"
scope: medium
---

# SEED-011: Admin, Support, and Compliance Console

## Why This Matters

The user mentioned Milestone 1.1 may include admin work. Zero Mail will need operational visibility after Phase 6: Gmail connection health, Pub/Sub delivery state, users.watch renewal, worker backlog, credit ledger health, CASA evidence, and support-safe tenant diagnostics.

This is not a Shortwave or Inbox Zero parity feature, but it is important for running a trust-first SaaS on a single VPS.

## When to Surface

**Trigger:** when planning Milestone 1.1 admin UI, support tooling, or CASA operations.

## Scope Estimate

**Medium**.

## Candidate Product Shape

- Admin dashboard for tenant health: connected/disconnected, last Gmail event, watch expiration, pause state.
- Worker queue/backlog panel for processing jobs and outbox status.
- Billing ledger health: balance, holds, stale reservations, webhook mismatch audit events.
- LLM spend/cap health by tenant without prompt/completion content.
- CASA evidence export: current scopes, privacy links, security controls, test evidence.
- Support-safe diagnostics: no raw email body, no prompt/completion, no token bytes.
- Incident runbook links and checklist completion status.

## Safety Rules

- Admin access is separate from normal user access and fully audited.
- No support staff can view raw email bodies unless a future privacy decision explicitly allows it.
- All admin logs use structured privacy-safe events.

## Breadcrumbs

- `.planning/ROADMAP.md` Phase 6 success criteria include production runbook and launch go/no-go.
- `docs/casa/` contains current CASA artifacts that need launch-hardening cleanup.
- `.planning/STATE.md` current focus: Phase 6 launch hardening and CASA-verified release readiness.

## Notes

This is likely the cleanest Milestone 1.1 feature after Phase 6 because it improves reliability without adding Google scopes.
