---
phase: 11
reviewers: [codex]
reviewed_at: 2026-06-09T09:25:58Z
plans_reviewed: [11-01-PLAN.md, 11-02-PLAN.md, 11-03-PLAN.md, 11-04-PLAN.md, 11-05-PLAN.md, 11-06-PLAN.md]
---

# Cross-AI Plan Review — Phase 11

## Codex Review

**Summary**

The plans are directionally strong and show good command of the phase's core invariant: every Gmail runtime path must move from tenant scope to tenant + mailbox scope without weakening privacy. The wave structure, migration-first thinking, AAD continuity, ArchUnit boundary work, and real-Gmail smoke are all appropriate. However, the plan set is not yet safely executable as written. Several waves intentionally permit compile-broken seams, Pub/Sub mailbox resolution may be ambiguous across tenants, the triage audit schema has naming/idempotency contradictions, and the backend API surface needed by the frontend is underplanned. Overall, this should be revised before execution.

**Strengths**

- Clear phase decomposition: schema foundation → ingestion/rules runtime → request binding → frontend integration.
- Correctly avoids a v1.3 unified all-mailboxes view and keeps action context tied to one active mailbox.
- Strong privacy posture: AAD is explicitly unchanged, raw email content is excluded from events/logs, and real-Gmail smoke includes log inspection.
- Good focus on hard invariants: same Gmail message id in two mailboxes, mailbox-owned rules, outbound send gateway, source/executing mailbox audit.
- ArchUnit allow-list draining is a useful enforcement mechanism for eliminating `buildClientForTenant` and `findByTenantId` drift.
- The two-CONNECTED-mailbox isolation fixture is the right test primitive for this phase.
- OpenAPI regeneration and typed frontend APIs are correctly called out instead of hand-editing generated schema.

**Concerns**

- **[HIGH] Compile-broken waves undermine execution.** Plan 01 says tests should compile but also allows `compileTestJava` to fail on future symbols. Plan 02 similarly allows event constructor seams to break compile until Plan 03. That makes intermediate verification unreliable and blocks parallel/autonomous execution.

- **[HIGH] Pub/Sub lookup may route to the wrong tenant.** Plan 03 resolves mailbox by `LOWER(google_email)` with `LIMIT 1`, while the text says uniqueness is per `(tenant,email)`. Gmail Pub/Sub push gives email/history id, not tenant. If the same Gmail account can be connected to two workspaces, this is a cross-tenant isolation flaw. The plan needs either global active-email uniqueness, per-connection routing, or explicit rejection of duplicate active Gmail grants across tenants.

- **[HIGH] `triage_audit` mailbox schema is internally inconsistent.** Plan 02 adds `source_mailbox_id` and `executing_mailbox_id`, but then says recreate the idempotency index with `gmail_connection_id`. That column is not clearly defined for `triage_audit`. Pick a concrete index shape, preferably including `executing_mailbox_id` and possibly `source_mailbox_id`, then align migration, writer, tests, and docs.

- **[HIGH] Backend API surface for rules/copy-rules is missing.** Plan 04 creates core `CopyRulesService`, but no API controller/DTO/OpenAPI contract is planned. Plan 06 expects generated schema and a frontend endpoint. The same gap exists for rule create/edit/preview/test DTOs that now need structured `gmailConnectionId`.

- **[HIGH] Outbound/chat caller migration is under-scoped and incorrectly ordered.** Plan 04 changes `OutboundSendCommand` to require `MailboxRef`, but MailboxContext/active-mailbox binding lands in Plan 05. Chat-confirmed sends, forward/reply assemblers, undo/revert, and rule-triggered send constructors are mentioned but not explicitly listed as modified files or test targets.

- **[HIGH] Ingestion scope is broader than the listed files.** Adding mailbox ids to `pubsub_delivery`, sync state, processing jobs, observed rows, and projection upserts likely requires repository/DAO/job payload changes beyond the files named in Plan 03. Watch renewal, history-lost handling, backfill jobs, ingestion health, and per-mailbox processing-job idempotency need explicit implementation targets.

- **[MEDIUM] Session-based active mailbox conflicts with the "sticky across devices" rationale.** Spring Session is lighter, but it is per browser session. If cross-device stickiness matters, use a persisted per-user/per-tenant active mailbox column. If session storage remains, namespace the attribute by tenant and document that cross-device persistence is intentionally not delivered.

- **[MEDIUM] Migration deployment risk is under-specified.** PK/index swaps on projection/audit/observed tables can lock hot tables. The plan needs preflight queries, queue/worker drain guidance, and an explicit strategy for in-flight `processing_job` rows before changing idempotency keys.

- **[MEDIUM] Error handling needs sharper boundaries.** Pub/Sub "unknown mailbox" should drop safely, but DB lookup failures must not be treated as unknown. Active mailbox resolution should define behavior for no connected mailbox, disconnected active mailbox, and disconnecting the currently active mailbox.

- **[MEDIUM] Frontend scope is too narrow.** `AppSidebar.tsx` and `CopyRulesDialog.tsx` are not enough to guarantee inbox, needs-reply, rules, audit, analytics, and send previews render active-mailbox provenance correctly. The actual feature components/query hooks that display or refetch those surfaces should be named.

- **[LOW] Requirement IDs drift.** Plans reference `VER-01`, but Phase 11 requirements list `VER-02..04`. Clean this up to avoid false completion accounting.

**Suggestions**

- Require every plan to leave `compileJava` and `compileTestJava` green. RED tests should compile and fail at assertion/runtime, or be introduced in the same plan as the minimal production contract they compile against.

- Decide the Gmail Pub/Sub tenant-resolution model before implementation. Add a migration/precondition/test for duplicate active Gmail addresses across tenants, or introduce a routing mechanism that makes tenant resolution unambiguous.

- Rewrite the triage audit migration contract with exact columns and index DDL. Example: `source_mailbox_id uuid NOT NULL`, `executing_mailbox_id uuid NOT NULL`, and idempotency on `(tenant_id, executing_mailbox_id, gmail_message_id, rule_id, action_type, args_hash) NULLS NOT DISTINCT`, unless there is a clear reason to include both mailbox ids.

- Add a backend API plan section for mailbox-owned rules and copy-rules: controller, request/response DTOs, OpenAPI annotations, ownership validation, and frontend generated-type consumption.

- Split outbound migration into explicit substeps: core command/gateway, triage caller, chat caller, forward/reply assemblers, undo/revert, and audit failure handling. Put active-mailbox-dependent API callers after MailboxContext exists.

- Add concrete tasks for watch renewal, history-lost handling, ingestion health, `processing_job` payload/idempotency, and inbox sync-state consumers.

- Namespace the active mailbox session attribute by tenant, or choose a persisted column if cross-device stickiness is required.

- Expand Plan 06's file list to include the actual inbox, needs-reply, rules, audit, analytics, and send-preview components touched by active-mailbox provenance.

**Risk Assessment: HIGH**

The architecture is sound, but the current plan set has several execution blockers and isolation-sensitive gaps. The highest-risk items are ambiguous Pub/Sub tenant resolution, compile-broken intermediate waves, inconsistent audit idempotency schema, and missing API/caller coverage for rules and outbound sends. Once those are corrected, the plan likely drops to MEDIUM risk because the remaining complexity is mostly broad refactoring plus careful migration/testing rather than new product ambiguity.

---

## Consensus Summary

Only one reviewer (Codex) was invoked for this cycle, so consensus reflects that single independent review. The review confirms the architecture and phase decomposition are sound but flags the plan set as **not yet safely executable** without revision.

### Agreed Strengths

- Correct core invariant: tenant → tenant + mailbox scoping across every Gmail runtime path without weakening privacy.
- Migration-first sequencing, unchanged AAD, ArchUnit allow-list draining, and the two-CONNECTED-mailbox isolation fixture.
- Privacy posture preserved (no raw email content/prompts in events or logs); real-Gmail smoke includes log inspection.

### Agreed Concerns (highest priority)

1. **[HIGH] Compile-broken intermediate waves** — Plans 01/02 permit `compileTestJava` / event-constructor seams to break until later plans, undermining per-wave verification and parallel execution.
2. **[HIGH] Pub/Sub tenant resolution ambiguity** — `LOWER(google_email)` + `LIMIT 1` can route to the wrong tenant when the same Gmail account is connected to two workspaces; needs global active-email uniqueness, per-connection routing, or explicit rejection of duplicate active grants.
3. **[HIGH] `triage_audit` schema/idempotency contradiction** — `source_mailbox_id`/`executing_mailbox_id` added but idempotency index references undefined `gmail_connection_id`; pick concrete DDL and align migration/writer/tests/docs.
4. **[HIGH] Missing backend API surface for rules/copy-rules** — core `CopyRulesService` has no controller/DTO/OpenAPI contract; rule create/edit/preview/test DTOs need structured `gmailConnectionId`.
5. **[HIGH] Outbound/chat caller migration under-scoped and mis-ordered** — `OutboundSendCommand` requires `MailboxRef` before `MailboxContext` exists; chat sends, forward/reply assemblers, undo/revert, rule-triggered send constructors not enumerated.
6. **[HIGH] Ingestion scope broader than listed files** — mailbox ids across `pubsub_delivery`, sync state, processing jobs, observed rows, projection upserts; watch renewal, history-lost, backfill, ingestion health, per-mailbox job idempotency need explicit targets.

Plus MEDIUM items (session vs cross-device active-mailbox stickiness, hot-table migration locking, error-boundary definitions, narrow frontend file list) and a LOW requirement-ID drift (`VER-01` vs `VER-02..04`).

### Divergent Views

None — single reviewer this cycle.

### Recommended Next Step

Revise the plans before execution, then optionally re-review:
  /gsd-plan-phase 11 --reviews
