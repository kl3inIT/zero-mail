---
phase: 11
cycle: 2
reviewers: [codex]
reviewed_at: 2026-06-09T16:55:00Z
plans_reviewed: [11-01-PLAN.md, 11-02-PLAN.md, 11-03-PLAN.md, 11-04-PLAN.md, 11-05-PLAN.md, 11-06-PLAN.md]
prior_cycle: 1
---

# Cross-AI Plan Review — Phase 11 (Cycle 2 Re-Review)

This is **cycle 2**. The plans were revised to address the 6 HIGH concerns raised in cycle 1.
Codex re-reviewed the revised plan set to verify resolution and surface new concerns.

## Codex Review

**Summary**

The cycle-2 plans are materially stronger: they add compile-green RED tests, choose a concrete Pub/Sub ambiguity strategy, add copy-rules API work, enumerate more outbound paths, and broaden ingestion beyond the obvious delivery service. Codex would not approve them as-is yet. Several prior HIGHs are only partially closed because the revised text still has internal contradictions or misses existing compile/runtime call sites. The biggest remaining risks are compile-broken waves from unlisted Java files, wrong-mailbox reads in forward/draft assembly, incomplete rule test DTO coverage, and a Liquibase YAML comment issue that can break migration parsing.

### Cycle 1 HIGH Concern Resolution

1. **PARTIALLY RESOLVED — Compile-broken intermediate waves**
   The plans now explicitly require `compileTestJava` green, use reflection/probes for Wave 1 RED tests, add transitional event constructors in Plan 02, and update outbound command callers in Plan 04. Still open: Plan 02 changes `RuleEntity` constructor/projection behavior without listing `RuleStatusProjection`, `RuleManagementService`, or `RuleTemplateMaterializationService`; Plan 03 changes `InboxBackfillEnqueuer` without listing all current callers; Plan 04 says to update `TriageAuditSaga` but does not list it in `files_modified`.

2. **RESOLVED — Pub/Sub tenant resolution ambiguity**
   Plan 02 adds changeset 127 with a global partial unique index on `lower(google_email) WHERE status='CONNECTED'`, plus a HALT precondition for existing cross-tenant duplicates. Plan 03 then resolves Pub/Sub email to `TenantMailboxRef(tenantId, gmailConnectionId)` and treats `>1` rows as invariant corruption rather than relying on `LIMIT 1`.

3. **PARTIALLY RESOLVED — `triage_audit` schema/idempotency contradiction**
   The concrete implementation tasks align on `source_mailbox_id`, `executing_mailbox_id`, and `ux_triage_audit_idem(tenant_id, executing_mailbox_id, gmail_message_id, rule_id, action_type, args_hash) NULLS NOT DISTINCT`, which is the right fix. But Plan 02's `artifacts_this_phase_produces` still says `gmail_connection_id` is added to `triage_audit`, contradicting the task and acceptance criteria. That ambiguity must be removed before execution.

4. **PARTIALLY RESOLVED — Missing backend API surface for rules/copy-rules**
   Plan 05 now adds `POST /api/rules/copy`, `CopyRulesRequest`, `CopyRulesResponse`, and structured `gmailConnectionId` on create/update/draft-preview DTOs and `RuleResponse`. It still misses the existing rule test DTO path: `RuleTestMessageRequest` currently exists and is not listed for mailbox ownership, despite the prior concern explicitly calling out preview/test DTOs.

5. **PARTIALLY RESOLVED — Outbound/chat caller migration under-scoped and mis-ordered**
   The ordering problem is mostly addressed: `OutboundSendCommand` gets `MailboxRef`, three direct constructor call sites are enumerated, `AssistantSendExecutor` has a temporary Plan 04 path and a Plan 05 close-out, and undo is included. Scope is still incomplete: `ForwardMessageAssembler` currently fetches the original message via `buildClientForTenant`, and draft/reply read paths such as `DraftReplySourceLoader`/`ToneContextBuilder` remain unenumerated. Plan 04 also omits `TriageAuditSaga` from `files_modified` even though it must change.

6. **PARTIALLY RESOLVED — Ingestion scope broader than listed files**
   Plan 03 now explicitly covers watch renewal, history-lost, ingestion health, observed/projection rows, events, sync state, and per-mailbox backfill jobs. Remaining gap: changing `InboxBackfillEnqueuer.enqueueIfNotPending` requires updating current callers such as `OAuthProvisioningService` and `RecentInboxReadService`; those are not all listed in the ingestion plan, creating either a compile break or a surviving tenant-only enqueue path.

### New Concerns

- **[HIGH] Liquibase YAML uses XML-style comments.** Plan 02 asks for `<!-- DEPLOY -->` comment blocks inside YAML changelogs. YAML comments should use `#`; literal XML comments can make the changelog invalid or parsed incorrectly.

- **[HIGH] Rule entity/projection compile hole.** Plan 02 says `RuleEntity.gmailConnectionId` is surfaced through `toStatusProjection()`, but does not include `RuleStatusProjection` or the services constructing `new RuleEntity(...)`. That undermines the compile-green guarantee. (Overlaps cycle-1 HIGH #1.)

- **[HIGH] Forward/draft mailbox reads are still wrong-mailbox capable.** Migrating the final send gateway is not enough if the source MIME/thread context is fetched from the tenant primary. `ForwardMessageAssembler`, `DraftReplySourceLoader`, and related draft context readers need a `MailboxRef` or active mailbox resolution. (Overlaps cycle-1 HIGH #5.)

- **[HIGH] Backfill enqueue migration is not call-site complete.** `InboxBackfillEnqueuer` cannot safely switch to mailbox scope without updating OAuth provisioning/add-reconnect flows and read-triggered backfill callers in the same compile-safe wave. (Overlaps cycle-1 HIGH #6.)

- **[MEDIUM] `processing_job` mailbox scope is internally fuzzy.** Plan 02 adds a `gmail_connection_id` column, while Plan 03 discusses deduping via payload JSON. Pick one canonical DB predicate, preferably the real column, and use payload only as job data.

- **[MEDIUM] Global duplicate Gmail grant needs service-level error handling.** The global unique index solves isolation, but add/reconnect should pre-check or map the unique violation to a clear 409-style product error instead of surfacing an OAuth callback 500 after consent.

- **[MEDIUM] Session-only active mailbox weakens D-03's cross-device rationale.** The plan documents this limitation, which is good, but the original decision text mentioned cross-device stickiness. Confirm that v1.3 accepts per-session stickiness, or use a persisted per-user column.

### Suggestions

- Amend Plan 02 to include `RuleStatusProjection`, `RuleManagementService`, `RuleTemplateMaterializationService`, and any tests using the old `RuleEntity` constructor, or add a transitional constructor with an explicit removal plan.
- Delete every `triage_audit.gmail_connection_id` reference from Plan 02. Make `source_mailbox_id` and `executing_mailbox_id` the only schema terms, and name `executing_mailbox_id` as the locked idempotency axis everywhere.
- Add `RuleTestMessageRequest` and its controller path to Plan 05 so rule test runs receive structured mailbox scope or are explicitly bound through `MailboxContext`.
- Add `ForwardMessageAssembler`, `DraftReplySourceLoader`, `ToneContextBuilder`, and any forward/reply/draft source loaders to the mailbox migration. The source-message read and the send execution must use the same concrete mailbox.
- Add `OAuthProvisioningService` and all `InboxBackfillEnqueuer.enqueueIfNotPending(...)` callers to Plan 03 or keep a compile-safe overload that immediately delegates through a resolved `MailboxRef`.
- Replace `<!-- DEPLOY -->` with YAML-safe `# DEPLOY` comments or Liquibase `comment:` fields.
- Define `processing_job` mailbox idempotency precisely: column name, unique/dedup predicate, idempotency key format, payload JSON shape, and migration behavior for existing open jobs.

### Risk Assessment: HIGH

HIGH until the above amendments are made. The revised plan set is directionally sound and much closer than cycle 1, but it still has execution-blocking compile risks and at least one remaining wrong-mailbox data path in outbound forward/draft handling. After fixing the listed plan gaps, the residual risk would drop to MEDIUM, mainly due to the size of the schema migration and end-to-end mailbox isolation surface.

---

## Consensus Summary

Only one reviewer (Codex) was invoked this cycle, so consensus reflects that single independent re-review. Codex confirms the cycle-2 revisions are a clear improvement but the plan set is **not yet safely executable** — 1 of 6 prior HIGHs is fully closed (Pub/Sub tenant resolution), the other 5 are only partially resolved, and the revisions introduced compile/parse-level HIGH issues.

### Cycle 1 → Cycle 2 HIGH Resolution Snapshot

| # | Cycle 1 HIGH | Status |
|---|--------------|--------|
| 1 | Compile-broken intermediate waves | PARTIALLY RESOLVED |
| 2 | Pub/Sub tenant resolution ambiguity | **RESOLVED** |
| 3 | `triage_audit` schema/idempotency contradiction | PARTIALLY RESOLVED |
| 4 | Missing backend API surface for rules/copy-rules | PARTIALLY RESOLVED |
| 5 | Outbound/chat caller migration under-scoped/mis-ordered | PARTIALLY RESOLVED |
| 6 | Ingestion scope broader than listed files | PARTIALLY RESOLVED |

### Distinct Unresolved HIGH Concerns (cycle 2)

1. **Compile holes from unlisted Java call sites** — `RuleEntity` constructor/`RuleStatusProjection`/`RuleManagementService`/`RuleTemplateMaterializationService` and `TriageAuditSaga` not enumerated in `files_modified`; breaks the compile-green guarantee. (cycle-1 #1 + new "Rule entity/projection compile hole")
2. **`triage_audit` schema contradiction** — `artifacts_this_phase_produces` still lists `gmail_connection_id` on `triage_audit`, contradicting the locked `executing_mailbox_id` idempotency DDL. (cycle-1 #3)
3. **Missing `RuleTestMessageRequest` mailbox scope** — rule test/preview DTO path not bound to mailbox ownership / `MailboxContext`. (cycle-1 #4)
4. **Forward/draft wrong-mailbox reads** — `ForwardMessageAssembler`/`DraftReplySourceLoader`/`ToneContextBuilder` read source MIME/thread via tenant primary, not the active mailbox. (cycle-1 #5 + new HIGH)
5. **Backfill enqueue not call-site complete** — `InboxBackfillEnqueuer.enqueueIfNotPending` callers (`OAuthProvisioningService`, `RecentInboxReadService`) not migrated in the same wave. (cycle-1 #6 + new HIGH)
6. **Liquibase YAML XML-style comments** — `<!-- DEPLOY -->` inside YAML changelogs can break/incorrectly parse the changelog; use `#` or Liquibase `comment:`. (new HIGH)

### Agreed Strengths
- Concrete Pub/Sub isolation fix (global partial unique index + HALT precondition + `TenantMailboxRef`, no `LIMIT 1`).
- Compile-green RED test discipline with reflection probes and transitional event constructors.
- Copy-rules API surface (`POST /api/rules/copy`, request/response DTOs) and structured `gmailConnectionId` on rule DTOs now planned.
- Broadened ingestion scope (watch renewal, history-lost, ingestion health, per-mailbox backfill).

### Divergent Views
None — single reviewer this cycle.

### Recommended Next Step
Amend the plans to close the 6 distinct unresolved HIGH concerns, then re-review or proceed to execution once compile-safety, the `triage_audit` artifacts/DDL contradiction, forward/draft read-path mailbox scoping, backfill call-site completeness, the rule-test DTO, and the YAML comment format are corrected:
  /gsd-plan-phase 11 --reviews
