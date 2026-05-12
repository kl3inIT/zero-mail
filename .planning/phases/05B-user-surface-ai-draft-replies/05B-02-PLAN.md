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
  - backend/core/src/main/java/com/zeromail/core/thread/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
  - backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java
autonomous: true
requirements: [DRFT-04]
must_haves:
  truths:
    - "Every thread Zero Mail observes (inbound triage, or a draft-saved event) gets a reply-status bucket: TO_REPLY or AWAITING_THEIR_REPLY"
    - "Classification never enumerates the mailbox — it keys only off already-observed threads"
    - "thread_reply_status persists metadata only: no bodies, subjects, participants, prompts, or completions"
    - "Re-classifying with an unchanged (tenantId, gmailThreadId, lastClassifiedMessageId) is a no-op"
    - "Account deletion purges thread_reply_status rows for that tenant"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java"
      provides: "IdentifiedEnum: TO_REPLY, AWAITING_THEIR_REPLY (FYI/ACTIONED reserved); fail-loud fromId"
      contains: "implements IdentifiedEnum"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java"
      provides: "@Entity extends AbstractAuditableEntity, bucket stored as varchar id via attribute converter"
      contains: "@Entity"
    - path: "backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java"
      provides: "heuristic-only v1 classifier + idempotent upsert + Modulith after-commit reaction on draft-saved/outbound events"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java"
      to: "ClassifyThreadReplyStatusService.classify"
      via: "sub-step on the inbound-message path"
      pattern: "ClassifyThreadReplyStatusService"
    - from: "backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java"
      to: "ThreadReplyStatusRepository (delete by tenant)"
      via: "account-deletion cleanup"
      pattern: "thread_reply_status|ThreadReplyStatusRepository"
---

<objective>
Create the `core.thread` domain package: the `ThreadReplyBucket` `IdentifiedEnum` (TO_REPLY / AWAITING_THEIR_REPLY, with FYI/ACTIONED reserved in the enum and the CHECK constraint but not produced in v1), the `thread_reply_status` JPA entity + repository + attribute converter mapping the bucket to its varchar id, and the heuristic-only `ClassifyThreadReplyStatusService` — invoked as a sub-step inside `TriageOrchestratorService` on the inbound-message path and via a Spring Modulith after-commit reaction on draft-saved / outbound-observed Gmail-state events. Wire account-deletion cleanup. Per CONTEXT D-10..D-12 and the researcher's recommendation, this is heuristic-only v1; the LLM hybrid stays a deferred follow-up.

Purpose: Powers the needs-reply inbox (read side comes in Plan 04, UI in Plan 06). The "no mailbox enumeration" and "metadata only" invariants are load-bearing for privacy and quota.
Output: New `core.thread` package (domain/persistence/usecases), Modulith package-info, orchestrator sub-step wiring, deletion cleanup.
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
@backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java
</context>

<interfaces>
<!-- Extracted analog contracts the executor needs. Read the actual files for full detail. -->

`IdentifiedEnum` (core.shared.lang): `String id()`, `String labelKey()` (default `<ClassSimpleName>.<id>`); pattern: `static X fromId(String)` throwing `NoSuchElementException`; `id() == name()` invariant; stored as varchar via `@Convert` attribute converter.

`AbstractAuditableEntity` (core.shared.persistence): `@MappedSuperclass` with `@Id` UUID, `@Version` long, `@CreatedDate`/`@LastModifiedDate` audit columns via `@EntityListeners(AuditingEntityListener.class)` on a tier-2 parent. New entities extend this and add their own columns + getters/setters; classes (not records) for entities; no Lombok.

Modulith after-commit reaction pattern: `@ApplicationModuleListener void on(SomeGmailStateChangedEvent e)` — see existing handlers of `core.gmail.event.MailMessageObserved` and the Phase 4 `TriageOrchestratorService` `@ApplicationModuleListener`. `core.thread`'s `package-info.java` declares `@ApplicationModule(displayName = "Thread", allowedDependencies = { ... })` — set the allowed deps from what the classifier actually touches (likely `gmail`, `tenant`, `shared.persistence`, `shared.lang`; NO crypto edge).
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ThreadReplyBucket enum + thread_reply_status entity/repository/converter + Modulith package-info</name>
  <files>backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java, backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyStatus.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java, backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyBucketAttributeConverter.java, backend/core/src/main/java/com/zeromail/core/thread/package-info.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java (IdentifiedEnum implementor — `id()`/`labelKey()`/`fromId` shape)
    - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageDecision.java (IdentifiedEnum stored as a CHECK-constraint-backed varchar)
    - backend/core/src/main/java/com/zeromail/core/llm/persistence/BYOKProviderAttributeConverter.java (attribute-converter pattern for an IdentifiedEnum)
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java (entity extending AbstractAuditableEntity — `@Entity`/`@Table`/`@AttributeOverride` for the inherited id if needed; getters/setters; class not record)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java + OrderedEnum.java
    - backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml (the column names this entity must map: `tenant_id`, `gmail_thread_id`, `bucket`, `last_classified_message_id`, `last_classified_at`, `has_draft`, `draft_id`, `resolved`, plus audit columns)
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/package-info.java + a `lowlevel/` marker if the domain uses one (mirror the per-domain pattern)
    - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java (the RED test)
  </read_first>
  <behavior>
    - `ThreadReplyBucket implements IdentifiedEnum`: members `TO_REPLY`, `AWAITING_THEIR_REPLY` (v1); `FYI`, `ACTIONED` may be declared but are not produced by v1 logic (CHECK constraint already allows all four); `id()` returns `name()`; static `fromId(String)` throws `NoSuchElementException` on unknown; never `ordinal()` for storage.
    - `ThreadReplyStatus` — a domain value object / projection-ish record (`tenantId`, `gmailThreadId`, `bucket`, `lastClassifiedMessageId`, `lastClassifiedAt`, `hasDraft`, `draftId`, `resolved`) used to pass classification results around (entities stay classes).
    - `ThreadReplyStatusEntity` — `@Entity @Table(name="thread_reply_status")` extending `AbstractAuditableEntity`; `@Convert` on `bucket` via `ThreadReplyBucketAttributeConverter`; `@TenantId`-annotated `tenantId` if that is how the other tenant-owned entities do it (check `AbstractTenantOwnedEntity` — if the project has a tenant-owned base, extend that instead and don't redeclare `tenant_id`); getters/setters; no Lombok.
    - `ThreadReplyStatusRepository extends JpaRepository<ThreadReplyStatusEntity, UUID>`: `Optional<ThreadReplyStatusEntity> findByGmailThreadId(String)` (tenant-filtered by Hibernate), `long countByBucketAndResolvedFalse(ThreadReplyBucket)` for the badge, and a bulk `@Modifying @Query("delete from ThreadReplyStatusEntity e where e.tenantId = :tenantId") int deleteByTenantId(UUID tenantId)` for account-deletion cleanup (mirror `OnboardingSelectionRepository.deleteByTenantId`).
    - `ThreadReplyBucketAttributeConverter implements AttributeConverter<ThreadReplyBucket,String>` — `convertToDatabaseColumn` = `id()`, `convertToEntityAttribute` = `fromId(...)`.
    - `package-info.java` for `core.thread` declares `@ApplicationModule(displayName="Thread", allowedDependencies={...})` with the minimal set the classifier actually needs; declare both edges atomically if any existing module already depends on `thread` (none does yet).
  </behavior>
  <action>
    Create the `core.thread.domain`, `core.thread.persistence` packages with the enum, value object, entity, repository, converter, and the per-domain `persistence/package-info.java` + `lowlevel/` marker if the repo uses that pattern. Create `core.thread/package-info.java` with the Modulith `@ApplicationModule` declaration. Verify `ApplicationModulesTest` still passes (you may need to add `thread` to the `allowedDependencies` of `triage` if the orchestrator sub-step creates a `triage → thread` edge — declare it in the same commit). Add a pure-JVM enum-name persistence test (`ThreadReplyBucketPersistenceTest` mirroring `OnboardingStepEnumPersistenceTest`) asserting `name()` literals match the CHECK-constraint strings.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ApplicationModules*" --tests "*ThreadReplyBucket*" --tests "*ThreadReplyStatus*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `ThreadReplyBucket` implements `IdentifiedEnum`, `id() == name()`, `fromId` throws `NoSuchElementException` on unknown; a persistence test asserts `name()` literals == the `ck_thread_reply_status_bucket` strings
    - `ThreadReplyStatusEntity` extends the appropriate auditable/tenant-owned base, maps all `030` columns, uses the attribute converter for `bucket`, is a class (not record), no Lombok
    - `ThreadReplyStatusRepository` exposes `findByGmailThreadId`, `countByBucketAndResolvedFalse`, and a bulk `deleteByTenantId` `@Modifying @Query`
    - `core.thread/package-info.java` has an `@ApplicationModule` with `allowedDependencies` limited to what's used; `ApplicationModulesTest` + `DomainBoundaryArchTests` pass
    - `mcp__jetbrains__get_file_problems` on the new Java files reports no problems
  </acceptance_criteria>
  <done>`core.thread` persistence layer + enum land; Modulith boundaries green.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: ClassifyThreadReplyStatusService (heuristic) + orchestrator sub-step + Modulith reaction + deletion cleanup</name>
  <files>backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java, backend/core/src/main/java/com/zeromail/core/thread/usecases/ThreadReplyClassificationInput.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java, backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java (heuristic over Gmail label ids + sender canonicalization — the closest analog)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java (the `@ApplicationModuleListener` inbound-message path; where to slot a classify sub-step after the existing triage work; what tenant + Gmail-message data it holds)
    - backend/core/src/main/java/com/zeromail/core/gmail/event/*.java (the Gmail-state / draft-saved / outbound-observed events available for an `@ApplicationModuleListener` reaction — if a draft-saved or message-sent event doesn't exist yet, decide: publish a new lightweight `core.triage` event `ThreadDraftSaved(tenantId, gmailThreadId, draftId, observedAt)` from `TriageGmailWriter`/the audit saga after the draft is created, carrying ids + timestamp only — no body)
    - backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java (existing `deleteCurrentTenant` cascade — add a `threadReplyStatusRepository.deleteByTenantId(...)` call; FK `deleteCascade:true` already covers it, but add the explicit call for parity with the other domains, or rely on cascade and document the choice)
    - backend/core/src/main/java/com/zeromail/core/onboarding/persistence/OnboardingSelectionRepository.java (`deleteByTenantId` bulk-query analog)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-10, D-11, D-12; .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Pattern 4: Heuristic reply-status classification"
  </read_first>
  <behavior>
    - `ClassifyThreadReplyStatusService.classify(ThreadReplyClassificationInput input)` where the input carries (metadata-only): `tenantId`, `gmailThreadId`, `lastMessageId`, `lastMessageFromIsTenant` (bool — last message `From` == the tenant's own Gmail address, computed by the caller from already-held metadata), `threadHasSentLabel` (bool), `hasZeroMailDraft` (bool) + optional `zeroMailDraftId`, plus `lastMessageIsAutoReply` (bool — `Auto-Submitted: auto-replied` / `Precedence: bulk` / known vacation pattern). NEVER carries subjects, bodies, or participant strings beyond what's needed for the booleans.
    - Heuristic v1: if `hasZeroMailDraft` → bucket `TO_REPLY`, `hasDraft=true` (the draft is a convenience, not a resolution — UI shows the `Draft ready` badge); else if `lastMessageFromIsTenant && threadHasSentLabel && !lastMessageIsAutoReply` → `AWAITING_THEIR_REPLY`; else → `TO_REPLY`.
    - Idempotency: if a `thread_reply_status` row exists for `(tenantId, gmailThreadId)` with the same `lastClassifiedMessageId == lastMessageId`, do nothing (return early). Otherwise upsert (`findByGmailThreadId` → update or `save` new): set `bucket`, `lastClassifiedMessageId`, `lastClassifiedAt = Instant.now(clock)`, `hasDraft`, `draftId`, preserve `resolved` on update (new activity may re-open it — see below).
    - On new inbound activity (a `lastMessageId` change), if the existing row was `resolved=true`, clear `resolved=false` (the thread re-enters the queue) — UI-SPEC §"Destructive actions" says `Mark resolved` is reversible on new activity.
    - Never enumerate the mailbox: the only entry points are (a) the orchestrator sub-step on an observed inbound message, and (b) a Modulith `@ApplicationModuleListener` on a draft-saved / outbound-observed event — both already scoped to a single known thread.
    - Log `event=thread_reply_classified tenantId={} gmailThreadId={} bucket={}` only — never the message id beyond an opaque reference, never any content.
  </behavior>
  <action>
    Create `ClassifyThreadReplyStatusService` (`@Service`, ctor-injected `ThreadReplyStatusRepository` + `Clock`) and `ThreadReplyClassificationInput` (validated record, metadata-only). Wire it as a sub-step in `TriageOrchestratorService`'s inbound-message handler (after the existing triage work, before/after the audit loop as fits the transaction boundary — keep it inside the same `@Transactional` scope as the audit write so a failure rolls back consistently, or document why it's separate). Add the after-commit Modulith reaction: an `@ApplicationModuleListener` on the draft-saved/outbound event (publish a new `ThreadDraftSaved` event from the audit saga or `TriageGmailWriter` if none exists — ids + timestamp only) that re-runs `classify(...)` so "awaiting" flips when the user sends and the draft badge shows after a draft is created. Add `threadReplyStatusRepository.deleteByTenantId(tenantId)` to the account-deletion path in `TenantService.deleteCurrentTenant` (or rely on FK cascade and add a comment + a deletion test asserting rows are gone). Make `ClassifyThreadReplyStatusServiceTest` pass.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ClassifyThreadReplyStatus*" --tests "*TriageOrchestrator*" --tests "*AccountDeletion*" --tests "*ApplicationModules*" 2>&1 | tail -12</automated>
  </verify>
  <acceptance_criteria>
    - `ClassifyThreadReplyStatusServiceTest` passes: counterparty-last + no draft → `TO_REPLY`; tenant-last + `SENT` + not-auto-reply → `AWAITING_THEIR_REPLY`; auto-reply last → stays `TO_REPLY`; thread with a Zero-Mail draft → `TO_REPLY` + `hasDraft=true`; unchanged `(tenantId, gmailThreadId, lastClassifiedMessageId)` → no re-upsert (verified via repository call count or version unchanged)
    - `TriageOrchestratorService` invokes `classify(...)` on the inbound path; a Modulith `@ApplicationModuleListener` re-classifies on draft-saved/outbound
    - Account deletion removes all `thread_reply_status` rows for the tenant (deletion test green)
    - No log line emitted by the classifier carries an email body, subject, address, or Google subject
    - `./gradlew :backend:core:test :backend:api:test` green; `ApplicationModulesTest` + `DomainBoundaryArchTests` green; `mcp__jetbrains__get_file_problems` on touched files clean
  </acceptance_criteria>
  <done>Heuristic reply-status classification runs on inbound + draft-saved events, idempotently, metadata-only, mailbox-scan-free; deletion cleanup wired.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Gmail thread metadata → classifier input | last-message `From`/labels/headers are attacker-influenceable; only booleans derived from them cross into `core.thread` |
| `core.thread` persistence → DB | a metadata-only projection; must never store content |
| account-deletion path | GDPR/Limited-Use clean-deletion obligation |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-02-01 | Denial of Service | classifier reaching for `messages.list` over the whole mailbox to find "awaiting" threads | mitigate | Only two entry points, both single-thread-scoped (orchestrator sub-step on an observed message; Modulith reaction on a draft-saved/outbound event keyed to a known `gmailThreadId`); no `messages.list` call in `core.thread`; ArchUnit/code-review gate |
| T-05B-02-02 | Information Disclosure | `thread_reply_status` columns leaking content | mitigate | Schema (Plan 00) has no body/subject/participant columns; the classifier input is booleans + ids only; `ThreadReplyClassificationInput` is a validated record reviewed to carry no content; log format metadata-only |
| T-05B-02-03 | Tampering | a crafted auto-reply / vacation-responder flipping a thread to `AWAITING_THEIR_REPLY` and hiding it from "to reply" | mitigate | `lastMessageIsAutoReply` (Auto-Submitted/Precedence:bulk + known patterns) keeps auto-reply last messages in `TO_REPLY`; classifier accuracy bar (≥85% TO_REPLY/AWAITING) measured against the held-out fixture set (Plan 07) |
| T-05B-02-04 | Information Disclosure | residual `thread_reply_status` rows after account deletion | mitigate | FK `deleteCascade: true` on `tenant_id` + an explicit `deleteByTenantId` call in the deletion path + a deletion test asserting zero rows remain |
| T-05B-02-05 | Tampering | cross-tenant write/read via the classifier | mitigate | `tenantId` from `TenantContext`; entity is tenant-owned (Hibernate `@TenantId` filter); `deleteByTenantId` is an explicit-`WHERE :tenantId` bulk query (no Hibernate-filter reliance on a native query) |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*ClassifyThreadReplyStatus*" --tests "*ThreadReplyBucket*" --tests "*ApplicationModules*" --tests "*DomainBoundary*"` all green
- `grep -rn "messages().list" backend/core/src/main/java/com/zeromail/core/thread` returns nothing
- Account-deletion test confirms `thread_reply_status` rows for the deleted tenant are gone
- `mcp__jetbrains__get_file_problems` on all new `core.thread` files + `TriageOrchestratorService.java` + `TenantService.java` — no problems
</verification>

<success_criteria>
`core.thread` package exists with a metadata-only `thread_reply_status` projection, an `IdentifiedEnum` bucket, and a heuristic-only v1 classifier that runs on observed inbound messages + draft-saved/outbound events, idempotently and without mailbox enumeration; account deletion purges the rows. Read side + UI follow in Plans 04/06.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-02-SUMMARY.md`
</output>
