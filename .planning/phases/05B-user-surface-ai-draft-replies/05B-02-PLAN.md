---
phase: 05B-user-surface-ai-draft-replies
plan: 02
type: execute
wave: 2
depends_on: ["05B-00"]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java
  - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyStatus.java
  - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java
  - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java
  - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyBucketAttributeConverter.java
  - backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java
  - backend/core/src/main/java/com/zeromail/core/thread/usecases/ThreadReplyClassificationInput.java
  - backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java
  - backend/core/src/main/java/com/zeromail/core/thread/package-info.java
  - backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
autonomous: true
requirements: [DRFT-04]
must_haves:
  truths:
    - "Every thread Zero Mail observes (inbound via the orchestrator sub-step wired in Plan 03, outbound via a SENT-labelled MailOutboundObserved reaction, draft-saved via a ThreadDraftSaved reaction) gets a reply-status bucket: TO_REPLY or AWAITING_THEIR_REPLY"
    - "Classification never enumerates the mailbox — no messages.list / no in:sent search; it keys only off threads Zero Mail already observed (watch covers INBOX + SENT) or touched by saving a draft; the heuristic input is booleans + ids only"
    - "A thread that has a Zero-Mail draft stays in TO_REPLY with hasDraft=true (the draft is a convenience, not a resolution); the AWAITING_THEIR_REPLY bucket is only ever reached by a tenant-sent, non-auto-reply last message"
    - "Public bucket slugs are `to-reply` / `awaiting-their-reply`; enum ids TO_REPLY / AWAITING_THEIR_REPLY are internal"
    - "thread_reply_status persists metadata only: no bodies, subjects, participants, prompts, or completions"
    - "Re-classifying with an unchanged (tenantId, gmailThreadId, lastClassifiedMessageId) is a no-op; new inbound activity re-opens a resolved=true row"
    - "Account deletion purges thread_reply_status rows for that tenant via the FK ON DELETE CASCADE (no separate deleteByTenantId path)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java"
      provides: "IdentifiedEnum: TO_REPLY, AWAITING_THEIR_REPLY (FYI/ACTIONED reserved in the enum + CHECK constraint, not produced in v1); fail-loud fromId"
      contains: "implements IdentifiedEnum"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java"
      provides: "@Entity extending the tenant-owned/auditable base, bucket stored as varchar id via attribute converter"
      contains: "@Entity"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java"
      provides: "heuristic-only v1 classifier + idempotent upsert + Modulith after-commit reactions on ThreadDraftSaved / MailOutboundObserved"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java"
      provides: "metadata-only domain event (tenantId, gmailThreadId, draftId, observedAt) published by the draft path (Plan 03 wires the publish)"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java"
      to: "ThreadReplyStatusRepository (idempotent upsert)"
      via: "@ApplicationModuleListener on ThreadDraftSaved / MailOutboundObserved"
      pattern: "ApplicationModuleListener"
    - from: "backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java"
      to: "MailOutboundObserved (publish)"
      via: "publish when an observed message carries the SENT label"
      pattern: "MailOutboundObserved"
---

<objective>
Create the `core.thread` domain package: the `ThreadReplyBucket` `IdentifiedEnum` (`TO_REPLY` / `AWAITING_THEIR_REPLY`, with `FYI`/`ACTIONED` reserved in the enum and the CHECK constraint but not produced in v1); the `thread_reply_status` JPA entity + repository + attribute converter mapping the bucket to its varchar id; the heuristic-only `ClassifyThreadReplyStatusService` — exposed as a callable use case (`public classify(ThreadReplyClassificationInput)`) and invoked via Spring Modulith after-commit reactions on two events: `ThreadDraftSaved` (a Zero-Mail draft was created — Plan 03 wires the publish) and `MailOutboundObserved` (an observed message carried the `SENT` label — published here from `GmailDeliveryProcessingService`); plus the metadata-only `ThreadDraftSaved`/`MailOutboundObserved` event records. Account-deletion cleanup is the FK `ON DELETE CASCADE` (Plan 00) — no separate `deleteByTenantId` path. The triage-orchestrator inbound-message sub-step that calls `classify(...)` and the `triage → thread` Modulith edge are wired in Plan 03 (which depends on both Plan 01 and Plan 02), so this plan never touches `TriageOrchestratorService.java`. Per CONTEXT D-10..D-12 and the researcher's recommendation, this is heuristic-only v1; the LLM hybrid stays a deferred follow-up.

**Awaiting-reply is bounded by SENT observation.** The `AWAITING_THEIR_REPLY` bucket is only detectable for threads where the user's reply showed up as a `SENT`-labelled message Zero Mail observed (via `users.watch` covering INBOX + SENT). If the user sends from a client that hasn't synced the `SENT` label yet at observation time, that thread sits in `TO_REPLY` until the next observation — a known accuracy limit, not a bug. We do NOT compensate with `messages.list(q="in:sent")` (that breaks the no-mailbox-enumeration invariant). The held-out classifier fixtures (Plan 07) include SENT-label-lag, multi-participant, group-thread, auto-reply, and DSN/bounce cases; the ≥85% TO_REPLY/AWAITING accuracy bar is a gated eval (Plan 07), not a "measure-and-document" — if it misses, the requirement ships partially complete with the LLM-hybrid follow-up, never with the bar quietly lowered.

Purpose: Powers the needs-reply inbox (read side comes in Plan 04, UI in Plan 06). The "no mailbox enumeration" and "metadata only" invariants are load-bearing for privacy and quota.
Output: New `core.thread` package (domain/persistence/usecases/event), the `MailOutboundObserved` event + its publish from `GmailDeliveryProcessingService`, Modulith package-info, after-commit reaction wiring.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
@backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java
@backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java
@backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
@backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java

<interfaces>
<!-- Extracted analog contracts the executor needs. Read the actual files for full detail. -->

`IdentifiedEnum` (core.shared.lang): `String id()`, `String labelKey()` (default `<ClassSimpleName>.<id>`); pattern: `static X fromId(String)` throwing `NoSuchElementException`; `id() == name()` invariant; stored as varchar via `@Convert` attribute converter.

`AbstractAuditableEntity` / `AbstractTenantOwnedEntity` (core.shared.persistence): if the project has a tenant-owned base, extend it (don't redeclare `tenant_id`); otherwise extend `AbstractAuditableEntity` and add `tenant_id` with `@TenantId` mirroring the other tenant-owned entities. Classes (not records) for entities; no Lombok.

`MailMessageObserved` (core.gmail.event): the existing per-observed-message domain event (carries ids/metadata, no body). `MailOutboundObserved` (NEW, this plan) mirrors its shape but is published only for messages carrying the Gmail `SENT` label — `(UUID tenantId, String gmailThreadId, String gmailMessageId, Instant observedAt)`, no body. `GmailDeliveryProcessingService` already classifies/labels observed messages — add the `MailOutboundObserved` publish in the same path that publishes `MailMessageObserved`, gated on the `SENT` label being present.

Modulith after-commit reaction: `@ApplicationModuleListener void on(ThreadDraftSaved event)` / `void on(MailOutboundObserved event)` — see existing handlers of `MailMessageObserved`. `core.thread/package-info.java` declares `@ApplicationModule(displayName="Thread", allowedDependencies={ gmail, tenant, shared.persistence, shared.lang })` — set from what the classifier actually touches; NO crypto edge. The `triage → thread` edge and the `core.thread → shared.pagination` edge are added by Plan 03 (which owns the two parent `package-info.java` edits), not here.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ThreadReplyBucket enum + thread_reply_status entity/repo/converter + ThreadDraftSaved/MailOutboundObserved events + Modulith package-info</name>
  <files>backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java, backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyStatus.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyBucketAttributeConverter.java, backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java, backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java, backend/core/src/main/java/com/zeromail/core/thread/package-info.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java (IdentifiedEnum implementor — `id()`/`labelKey()`/`fromId` shape)
    - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageDecision.java (IdentifiedEnum stored as a CHECK-constraint-backed varchar)
    - backend/core/src/main/java/com/zeromail/core/llm/persistence/BYOKProviderAttributeConverter.java (attribute-converter pattern for an IdentifiedEnum)
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java (entity extending the auditable/tenant-owned base — `@Entity`/`@Table`/`@AttributeOverride`; getters/setters; class not record)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java + OrderedEnum.java
    - backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml (the columns this entity must map: `tenant_id`, `gmail_thread_id`, `bucket`, `last_classified_message_id`, `last_classified_at`, `has_draft`, `draft_id`, `resolved`, plus audit columns)
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java + package-info.java (event record shape + Modulith module declaration)
    - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java (the RED scaffold from Plan 00)
  </read_first>
  <behavior>
    - `ThreadReplyBucket implements IdentifiedEnum`: members `TO_REPLY`, `AWAITING_THEIR_REPLY` (v1); `FYI`, `ACTIONED` declared but not produced by v1 logic; `id()` returns `name()`; static `fromId(String)` throws `NoSuchElementException` on unknown; never `ordinal()` for storage. A `publicSlug()` helper (or a static map) maps the enum to its public slug: `TO_REPLY → "to-reply"`, `AWAITING_THEIR_REPLY → "awaiting-their-reply"`, `FYI → "fyi"`, `ACTIONED → "actioned"`; `static ThreadReplyBucket fromPublicSlug(String)` throws on unknown — the API/UI layer uses these slugs, never the enum names.
    - `ThreadReplyStatus` — a domain value object / projection-ish record (`tenantId`, `gmailThreadId`, `bucket`, `lastClassifiedMessageId`, `lastClassifiedAt`, `hasDraft`, `draftId`, `resolved`) for passing classification results around (entities stay classes).
    - `ThreadReplyStatusEntity` — `@Entity @Table(name="thread_reply_status")` extending the tenant-owned/auditable base; `@Convert` on `bucket` via `ThreadReplyBucketAttributeConverter`; getters/setters; no Lombok; no body/subject/participant columns.
    - `ThreadReplyStatusRepository extends JpaRepository<ThreadReplyStatusEntity, UUID>`: `Optional<ThreadReplyStatusEntity> findByGmailThreadId(String)` (tenant-filtered by Hibernate), `long countByBucketAndResolvedFalse(ThreadReplyBucket)` for the badge. NO `deleteByTenantId` — the FK `ON DELETE CASCADE` (Plan 00) is the sole cleanup mechanism.
    - `ThreadReplyBucketAttributeConverter implements AttributeConverter<ThreadReplyBucket,String>` — `convertToDatabaseColumn` = `id()`, `convertToEntityAttribute` = `fromId(...)`.
    - `ThreadDraftSaved` (in `core.thread.event`) — validated record `(UUID tenantId, String gmailThreadId, String draftId, Instant observedAt)`, payload-free of content. (Published by Plan 03's draft path; defined here so Plan 02's listener can reference it.)
    - `MailOutboundObserved` (in `core.gmail.event`) — validated record `(UUID tenantId, String gmailThreadId, String gmailMessageId, Instant observedAt)`, payload-free of content. Published from `GmailDeliveryProcessingService` (Task 2) when an observed message carries the `SENT` label.
    - `package-info.java` for `core.thread` declares `@ApplicationModule(displayName="Thread", allowedDependencies={ gmail, tenant, shared.persistence, shared.lang })` — minimal set. Do NOT add a `triage → thread` edge or a `thread → shared.pagination` edge here — Plan 03 owns both.
  </behavior>
  <action>
    Create the `core.thread.domain`, `core.thread.persistence`, `core.thread.event` packages with the enum (incl. the public-slug helpers), value object, entity, repository, converter, and `ThreadDraftSaved`. Add `MailOutboundObserved` to `core.gmail.event`. Create the per-domain `persistence/package-info.java` + `lowlevel/` marker if the repo uses that pattern. Create `core.thread/package-info.java` with the `@ApplicationModule` declaration. Add a pure-JVM `ThreadReplyBucketPersistenceTest` (mirroring `OnboardingStepEnumPersistenceTest`) asserting `name()` literals match the `ck_thread_reply_status_bucket` strings AND that the public slugs round-trip. Verify `ApplicationModulesTest` still passes.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ApplicationModules*" --tests "*ThreadReplyBucket*" --tests "*ThreadReplyStatus*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `ThreadReplyBucket` implements `IdentifiedEnum`, `id() == name()`, `fromId` throws on unknown; `publicSlug()`/`fromPublicSlug(...)` map to `to-reply`/`awaiting-their-reply`/`fyi`/`actioned` and round-trip; a persistence test asserts the `name()` literals == the `ck_thread_reply_status_bucket` strings
    - `ThreadReplyStatusEntity` extends the tenant-owned/auditable base, maps all `030` columns, uses the attribute converter for `bucket`, is a class (not record), no Lombok, no content columns
    - `ThreadReplyStatusRepository` exposes `findByGmailThreadId`, `countByBucketAndResolvedFalse` — and NO `deleteByTenantId` (FK cascade is the cleanup)
    - `ThreadDraftSaved` (`core.thread.event`) and `MailOutboundObserved` (`core.gmail.event`) are payload-free validated records
    - `core.thread/package-info.java` `@ApplicationModule` `allowedDependencies` is the minimal set; no `triage → thread` or `thread → shared.pagination` edge declared here; `ApplicationModulesTest` + `DomainBoundaryArchTests` pass
    - `mcp__jetbrains__get_file_problems` on the new Java files reports no problems
  </acceptance_criteria>
  <done>`core.thread` persistence layer + enum + the two events land; Modulith boundaries green.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: ClassifyThreadReplyStatusService (heuristic) + Modulith reactions + MailOutboundObserved publish</name>
  <files>backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java, backend/core/src/main/java/com/zeromail/core/thread/usecases/ThreadReplyClassificationInput.java, backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java (heuristic over Gmail label ids + sender canonicalization — the closest analog)
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java (where `MailMessageObserved` is published per observed message and where the message's Gmail label-id set is available — add the `MailOutboundObserved` publish there, gated on the `SENT` label id)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java (READ ONLY — the `@ApplicationModuleListener` inbound path; Plan 03 adds the `classify(...)` sub-step here, this plan does not edit it)
    - backend/core/src/main/java/com/zeromail/core/onboarding/usecases/*Listener*.java or any existing `@ApplicationModuleListener` (the after-commit reaction pattern)
    - backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java + backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java (Task 1)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-10, D-11, D-12; .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Pattern 4: Heuristic reply-status classification"
    - inbox-zero's `apps/web/utils/ai/reply/determine-thread-status.test.ts` (the heuristic cases to mirror — multi-participant, auto-reply, SENT-lag, group, DSN/bounce)
  </read_first>
  <behavior>
    - `ClassifyThreadReplyStatusService.classify(ThreadReplyClassificationInput input)` — `public` (so Plan 03's orchestrator sub-step calls it directly). Input (metadata-only, validated record): `tenantId`, `gmailThreadId`, `lastMessageId`, `lastMessageFromIsTenant` (bool — last message `From` == the tenant's own Gmail address, computed by the caller from already-held metadata; for a thread with several participants this is just "the most recent message is from the tenant", which is exactly the signal we want), `threadHasSentLabel` (bool — the thread carries the `SENT` label per already-observed metadata), `hasZeroMailDraft` (bool) + optional `zeroMailDraftId`, `lastMessageIsAutoReply` (bool — `Auto-Submitted: auto-replied` / `Precedence: bulk` / `From: <>` or MAILER-DAEMON DSN/bounce pattern, computed by the caller from header metadata). NEVER carries subjects, bodies, or participant strings beyond the booleans.
    - Heuristic v1 (the `TO_REPLY` ⇄ `AWAITING_THEIR_REPLY` split is the one that drives action; everything else falls to `TO_REPLY`):
      1. if `lastMessageFromIsTenant && threadHasSentLabel && !lastMessageIsAutoReply` → `AWAITING_THEIR_REPLY`
      2. else → `TO_REPLY`
      `hasDraft`/`draftId` are recorded from the input regardless of bucket — a thread with a Zero-Mail draft is `TO_REPLY` with `hasDraft=true` ("Draft ready" in the UI); the draft never moves a thread to `AWAITING_THEIR_REPLY`.
    - Idempotency: if a `thread_reply_status` row exists for `(tenantId, gmailThreadId)` with the same `lastClassifiedMessageId == lastMessageId`, return early (no upsert, no version bump). Otherwise upsert (`findByGmailThreadId` → update or `save` new): set `bucket`, `lastClassifiedMessageId`, `lastClassifiedAt = Instant.now(clock)`, `hasDraft`, `draftId`. On NEW inbound activity (the `lastMessageId` changed) when the existing row was `resolved=true`, set `resolved=false` (the thread re-enters the queue — UI-SPEC §"Destructive actions"); when there is no activity change, preserve `resolved`.
    - Modulith reactions inside `ClassifyThreadReplyStatusService`:
      - `@ApplicationModuleListener void on(ThreadDraftSaved event)` → re-`classify(...)` for `event.gmailThreadId()` with `hasZeroMailDraft=true, zeroMailDraftId=event.draftId()` (the other booleans from the last-known row / a single `threads.get(format=METADATA)` for the thread — never a list).
      - `@ApplicationModuleListener void on(MailOutboundObserved event)` → re-`classify(...)` for `event.gmailThreadId()` with `lastMessageFromIsTenant=true, threadHasSentLabel=true, lastMessageId=event.gmailMessageId()` and `lastMessageIsAutoReply` derived from a single `threads.get(format=METADATA)` of that message's headers (or false if unavailable) — so "awaiting" flips when the user sends, BUT only for sends Zero Mail observes (the SENT-lag limit above).
    - `GmailDeliveryProcessingService`: after publishing `MailMessageObserved` for an observed message, if the message's Gmail label-id set contains `SENT`, also publish `MailOutboundObserved(tenantId, gmailThreadId, gmailMessageId, observedAt)`. No new Gmail fetch — reuse the labels already on the processed message.
    - Never enumerate the mailbox: the only `classify(...)` entry points are (a) the orchestrator inbound sub-step (Plan 03), (b) the `ThreadDraftSaved` reaction, (c) the `MailOutboundObserved` reaction — all single-thread-scoped; no `messages.list`, no `q="in:sent"` anywhere in `core.thread`.
    - Log `event=thread_reply_classified tenantId={} gmailThreadId={} bucket={}` only — never message ids beyond opaque references, never content.
  </behavior>
  <action>
    Create `ClassifyThreadReplyStatusService` (`@Service`, ctor-injected `ThreadReplyStatusRepository` + `Clock` + the Gmail-client factory for the single `threads.get(format=METADATA)` used by the two reactions) with the `public classify(...)` method and `ThreadReplyClassificationInput` (validated record, metadata-only), plus the two `@ApplicationModuleListener` reactions. Add the `MailOutboundObserved` publish to `GmailDeliveryProcessingService` (gated on the `SENT` label). Do NOT edit `TriageOrchestratorService.java`; do NOT add a `triage → thread` edge; do NOT add a `deleteByTenantId` path (FK cascade does it). Turn `ClassifyThreadReplyStatusServiceTest` into real, passing assertions; add an account-deletion test asserting `thread_reply_status` rows are gone after the tenant is deleted (proving the FK cascade).
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ClassifyThreadReplyStatus*" --tests "*AccountDeletion*" --tests "*ApplicationModules*" --tests "*GmailDeliveryProcessing*" 2>&1 | tail -12</automated>
  </verify>
  <acceptance_criteria>
    - `ClassifyThreadReplyStatusServiceTest` passes: counterparty-last → `TO_REPLY`; tenant-last + `SENT` + not-auto-reply → `AWAITING_THEIR_REPLY`; auto-reply / DSN-bounce last → stays `TO_REPLY`; thread with a Zero-Mail draft → `TO_REPLY` + `hasDraft=true`; unchanged `(tenantId, gmailThreadId, lastClassifiedMessageId)` → no re-upsert (verified via repository call count / version unchanged); new inbound activity on a `resolved=true` row → `resolved=false`
    - The `ThreadDraftSaved` and `MailOutboundObserved` `@ApplicationModuleListener` reactions in `core.thread` re-classify single-thread-scoped; `TriageOrchestratorService.java` is NOT modified in this plan; no `triage → thread` edge added here
    - `GmailDeliveryProcessingService` publishes `MailOutboundObserved` exactly when an observed message carries the `SENT` label, reusing already-fetched labels (no new Gmail call); `GmailDeliveryProcessing*` tests still green
    - Account deletion removes all `thread_reply_status` rows for the tenant (FK-cascade deletion test green); no `deleteByTenantId` method exists
    - `grep -rn "messages().list\|q=\\\"in:sent\\\"\|in:sent" backend/core/src/main/java/com/zeromail/core/thread` returns nothing; no log line from the classifier carries an email body, subject, address, or Google subject
    - `./gradlew :backend:core:test :backend:api:test` green; `ApplicationModulesTest` + `DomainBoundaryArchTests` green; `mcp__jetbrains__get_file_problems` on touched files clean
  </acceptance_criteria>
  <done>Heuristic reply-status classification runs on draft-saved/outbound events (the only awaiting trigger is observed SENT mail — documented limit), idempotently, metadata-only, mailbox-scan-free, with a public `classify(...)` ready for Plan 03's orchestrator sub-step; cleanup is the FK cascade.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Gmail thread/message metadata → classifier input | last-message `From`/labels/headers are attacker-influenceable; only booleans derived from them cross into `core.thread` |
| `core.thread` persistence → DB | a metadata-only projection; must never store content |
| account-deletion path | GDPR/Limited-Use clean-deletion obligation — satisfied by the FK `ON DELETE CASCADE` |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-02-01 | Denial of Service | classifier reaching for `messages.list` / `q="in:sent"` over the whole mailbox to find "awaiting" threads | mitigate | Three single-thread-scoped entry points only (orchestrator sub-step — Plan 03; `ThreadDraftSaved` reaction; `MailOutboundObserved` reaction); each touches at most one `threads.get(format=METADATA)`; no `messages.list` / no `in:sent` search in `core.thread`; `grep` gate + `DomainBoundaryArchTests` |
| T-05B-02-02 | Information Disclosure | `thread_reply_status` columns leaking content | mitigate | Schema (Plan 00) has no body/subject/participant columns; `ThreadReplyClassificationInput` is booleans + ids only; `ThreadDraftSaved`/`MailOutboundObserved` are payload-free; log format metadata-only |
| T-05B-02-03 | Tampering | a crafted auto-reply / vacation-responder / DSN-bounce flipping a thread to `AWAITING_THEIR_REPLY` and hiding it from "to reply" | mitigate | `lastMessageIsAutoReply` (Auto-Submitted/Precedence:bulk + MAILER-DAEMON/`From: <>` patterns) keeps such last messages in `TO_REPLY`; the classifier-accuracy bar (≥85% TO_REPLY/AWAITING) is a GATED eval against the held-out fixture set (Plan 07) — not "measure and document"; a miss → requirement partially complete + LLM-hybrid follow-up, never a lowered bar |
| T-05B-02-04 | Information Disclosure | residual `thread_reply_status` rows after account deletion | mitigate | FK `ON DELETE CASCADE` on `tenant_id` (Plan 00) — single mechanism, no second `deleteByTenantId` to drift; a deletion test asserts zero rows remain |
| T-05B-02-05 | Tampering | cross-tenant write/read via the classifier | mitigate | `tenantId` from `TenantContext`; entity is tenant-owned (Hibernate `@TenantId` filter); the reactions carry the tenant id on the event |
| T-05B-02-06 | (accuracy, flagged) | `AWAITING_THEIR_REPLY` undercount because a SENT-label sync lag on the user's other client means Zero Mail never observed the send | mitigate (documented) | Watch covers INBOX + SENT, so most sends are observed; the residual lag is a known accuracy limit (the thread sits in `TO_REPLY` until the next observation); NOT compensated by mailbox enumeration; SENT-lag fixtures in the Plan 07 classifier eval; the UI/UAT copy frames "awaiting" as best-effort |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*ClassifyThreadReplyStatus*" --tests "*ThreadReplyBucket*" --tests "*ApplicationModules*" --tests "*DomainBoundary*" --tests "*GmailDeliveryProcessing*"` all green
- `grep -rn "messages().list\|in:sent" backend/core/src/main/java/com/zeromail/core/thread` returns nothing
- `git diff --name-only` for this plan does NOT include `TriageOrchestratorService.java`, `TriageAuditSaga.java`, `core/triage/package-info.java`, or `core/thread/.../shared.pagination` edits (Plan 01 owns the saga, Plan 03 owns the orchestrator sub-step + the parent package-info edges)
- Account-deletion test confirms `thread_reply_status` rows for the deleted tenant are gone; no `deleteByTenantId` method exists
- `mcp__jetbrains__get_file_problems` on all new `core.thread`/`core.gmail` files + `GmailDeliveryProcessingService.java` — no problems
</verification>

<success_criteria>
`core.thread` package exists with a metadata-only `thread_reply_status` projection, an `IdentifiedEnum` bucket with public slugs (`to-reply`/`awaiting-their-reply`), and a heuristic-only v1 classifier (public `classify(...)` + after-commit reactions on `ThreadDraftSaved` and `MailOutboundObserved`), idempotent and mailbox-scan-free; awaiting-reply is bounded by observed SENT mail (documented limit); account deletion purges rows via FK cascade. The triage inbound sub-step calling `classify(...)` is wired in Plan 03. Read side + UI follow in Plans 04/06.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-02-SUMMARY.md`
</output>
