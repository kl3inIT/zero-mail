---
id: SEED-005
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning team accounts, shared inbox, or workspace collaboration"
scope: large
---

# SEED-005: Team Collaboration and Shared Email Workspace

## Why This Matters

Shortwave exposes team collaboration features: share live threads, private comments, assign next steps, shared labels, shared prompts/templates, and team-searchable archives. This moves the product from personal inbox automation into team workflow.

Inbox Zero appears more personal-assistant oriented. Team collaboration could be a differentiation path, but it changes tenant/user modeling, access control, audit requirements, and privacy expectations.

## When to Surface

**Trigger:** when planning team accounts, shared inbox, or workspace collaboration.

## Scope Estimate

**Large**. Requires workspace membership, roles, sharing rules, audit trails, and stricter permission checks.

## Candidate Product Shape

- Workspace users and roles.
- Share a thread with teammates without forwarding screenshots.
- Private internal comments on email threads.
- Assign owner / due date / done status.
- Shared labels and shared rule templates.
- Shared saved prompts/snippets.
- Team-wide audit log for AI and human actions.
- Permission boundaries: private mail stays private unless explicitly shared.

## Risks

- Tenant isolation becomes user-within-tenant isolation, not just tenant isolation.
- Need row-level authorization beyond current tenant filter.
- Need user-visible audit of who accessed/shared/commented.
- CASA/privacy docs must explain internal team sharing clearly.

## Breadcrumbs

- Shortwave homepage team collaboration section: https://www.shortwave.com/
- Shortwave shortcuts include share/assign commands: https://www.shortwave.com/docs/references/shortcuts/
- `.planning/PROJECT.md` — current v1 is multi-tenant but effectively single-seat Gmail account semantics.

## Notes

Do not mix this into Phase 6. It is likely a future paid/team milestone.
