---
phase: 08-admin-console-operator-tooling
plan: 8E
type: execute
wave: 2
depends_on:
  - 08-8A
files_modified:
  - backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/DeadLetterRequeueService.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/projection/QueueHealthSnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/projection/QueueDepthByType.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/projection/RetryDistributionBucket.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterPage.java
  - backend/core/src/main/java/com/zeromail/core/admin/queue/package-info.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/QueueHealthResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterPageResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/queue/RequeueRequest.java
  - apps/admin/src/routes/queue.tsx
  - apps/admin/src/features/queue/queue-api.ts
  - apps/admin/src/features/queue/query-keys.ts
  - apps/admin/src/features/queue/use-queue-health.ts
  - apps/admin/src/features/queue/use-dead-letters.ts
  - apps/admin/src/features/queue/use-requeue.ts
  - apps/admin/src/components/KpiCard.tsx
  - apps/admin/src/components/AutoRefreshIndicator.tsx
  - apps/admin/e2e/queue.spec.ts
  - backend/core/src/main/resources/db/changelog/changes/078-processing-job-extend.yaml
  - backend/core/src/main/java/com/zeromail/core/queue/domain/JobFailureReason.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/WorkerFailureReasonEnumOnlyTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/queue/QueueHealthQueryServiceSqlSpyTest.java

autonomous: true
requirements:
  - OPS-QUEUE-01
  - OPS-QUEUE-02

must_haves:
  truths:
    - "Operator can view /queue showing depth by job type, oldest-unleased job age, retry distribution histogram, failure rate (last 24h), and dead-letter count."
    - "Dashboard auto-refreshes every 10s via TanStack Query refetchInterval; pause toggle stops auto-refresh."
    - "All reads are aggregates over outbox + processing_job tables; payload_json never serialized to response."
    - "Operator can re-queue a dead-letter row without viewing or editing its payload; Re-queue resets retry count to 0, writes audit row DEAD_LETTER_REQUEUED."
    - "Re-queue confirmation uses ConfirmTwiceDialog with step-2 token = job ID first 8 chars (UI-SPEC line 203)."
    - "Dead-letter DTO has NO payloadJson field (DTO contract is the gate per SPEC OPS-QUEUE-02)."
    - "AdminPathBodyBanTest green over queue projections + DTOs."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java"
      provides: "Aggregator over outbox + processing_job tables; returns QueueHealthSnapshot (no per-row data)."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/DeadLetterRequeueService.java"
      provides: "Re-queue action: status='PENDING', attempts=0, locked_until=NULL; same-tx DEAD_LETTER_REQUEUED audit."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterRow.java"
      provides: "Projection record WITHOUT payloadJson field (DTO-as-gate per OPS-QUEUE-02)."
    - path: "apps/admin/src/components/KpiCard.tsx"
      provides: "Shared KPI display (tabular-num value + label + delta + optional sparkline)."
    - path: "apps/admin/src/components/AutoRefreshIndicator.tsx"
      provides: "Pulsing 6px accent dot + 'Updated Ns ago' text + pause toggle, reused in 8F /spend."
  key_links:
    - from: "AdminQueueController#health"
      to: "QueueHealthQueryService#snapshot"
      via: "aggregate SQL over outbox + processing_job"
      pattern: "QueueHealthQueryService"
    - from: "apps/admin/src/routes/queue.tsx"
      to: "useQuery refetchInterval 10_000"
      via: "TanStack Query auto-refresh"
      pattern: "refetchInterval"
---

<objective>
Deliver `/queue` worker queue health dashboard: real-time aggregates (depth by job type, oldest-unleased age, retry histogram, failure rate, dead-letter count), 10s auto-refresh, dead-letter table with re-queue action (no payload exposure), shared `<KpiCard>` and `<AutoRefreshIndicator>` components.

Output: Operator sees backend job-queue health with 10s freshness; can re-queue stuck dead-letter rows without payload leakage.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/08-admin-console-operator-tooling/08-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md
@.planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html
@.planning/phases/08-admin-console-operator-tooling/08-8A-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
</context>

<reviews_addendum_8E>
## Reviews-pass replan addendum — 2026-05-19 (Codex + OpenCode HIGHs incorporated)

### R-8E-H1 — Requeue semantics: separate `admin_requeue_count` (Codex HIGH)
**Decision (locked):** OPS-QUEUE-02 requirement language ("increments retry counter") and current plan ("resets attempts to 0") conflict. Resolution: BOTH semantics, on TWO columns:
- `processing_job.attempts` — RESET to 0 on requeue (gives worker a fresh retry budget so the job can drain; this is the existing in-plan behavior).
- `processing_job.admin_requeue_count` — INCREMENT by 1 on requeue (new column; tracks "how many times has an admin manually intervened on this job" so repeat-offender jobs surface in queue health KPIs).
Liquibase 078 (renamed from `054-processing-job-extend.yaml` per 8A R-H10) adds `admin_requeue_count INTEGER NOT NULL DEFAULT 0` and `last_failure_reason VARCHAR(100)` to `processing_job` if not already present. Update `DeadLetterRequeueService.requeue` UPDATE statement to set `attempts=0, locked_until=NULL, last_failed_at=NULL, admin_requeue_count=admin_requeue_count+1` and write audit row with `before_state_json={jobId, jobType, attemptsBeforeRequeue, lastFailureReason, adminRequeueCountBefore}` + `after_state_json={status:PENDING, attempts:0, adminRequeueCountAfter}`. Queue health `KpiCard` adds a 6th KPI "Admin-requeued (24h)" = COUNT WHERE admin_requeue_count > 0 AND last_requeued_at >= NOW() - 24h. Update Task 8E-01 acceptance criteria.

### R-8E-H2 — Failure rate 24h denominator (OpenCode MEDIUM→HIGH for KPI correctness)
**Decision:** `QueueHealthQueryService.snapshot()` failure-rate SQL becomes time-window bounded on BOTH numerator and denominator:
`failureRateLast24h = COUNT(*) FILTER (WHERE status='FAILED' AND last_failed_at >= NOW() - INTERVAL '24h') * 1.0 / NULLIF(COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '24h'), 0)`
This prevents the lifetime-rate asymptote-to-zero degeneration the reviewer flagged. Acceptance criterion 8E-01 amended: fixture of 100 PENDING rows from 30 days ago + 5 FAILED rows in last 24h + 95 SUCCEEDED rows in last 24h → failure rate = 5/100 = 5%, not 5/100k.

### R-8E-H3 — `last_failure_reason` sanitization at WORKER write time (Codex MEDIUM, OpenCode MEDIUM)
**Decision:** Worker code path that writes `last_failure_reason` MUST store enum-shaped short codes only, max 100 chars, NEVER raw exception text. New enum `JobFailureReason` (lives in `backend/core/src/main/java/com/zeromail/core/queue/domain/JobFailureReason.java`, added to Task 8E-01 files) with values like `DOWNSTREAM_TIMEOUT, GMAIL_API_RATE_LIMIT, ENCRYPTION_KEY_MISSING, VALIDATION_FAILED, UNKNOWN`. Worker error handlers map exceptions to enum BEFORE persistence. The schema column type stays VARCHAR(100) but a CHECK constraint validates value is one of the enum names. ArchUnit `WorkerFailureReasonEnumOnlyTest` (added to Task 8E-01 files): worker classes that UPDATE `processing_job.last_failure_reason` must reference `JobFailureReason.name()` only — no raw `Throwable.getMessage()` flows through.

### R-8E-H4 — SQL contract test verifying no `payload_json` selection (Codex MEDIUM)
**Decision:** Per Codex feedback, grep is weaker than runtime SQL contract. Add `QueueHealthQueryServiceSqlSpyTest` (added to Task 8E-01 files): integration test using a `JdbcTemplate` wrapper / Datasource proxy that captures every emitted SQL string during `snapshot()` + `deadLetterPage()` and asserts NONE contain `payload_json` / `payloadJson` tokens. This is the load-bearing SQL contract test the grep gate complements.

### R-8E-H5 — Per-job-type breakdown on failure rate (OpenCode LOW, accepted)
**Decision:** Defer per-job-type breakdown to v1.3+; KpiCard text already shows aggregate. Add a `// TODO v1.3+: per-job-type failure histogram` comment in `QueueHealthQueryService` so the deferral is discoverable. Task 8E acceptance unchanged.

### R-8E-H6 — Liquibase numbering offset (cross-plan from 8A R-H10)
**Decision:** Optional `054-processing-job-extend.yaml` → `078-processing-job-extend.yaml`. Append to db.changelog-master.yaml include list in numeric order.

---

## Cycle 3 reviews-pass addendum — 2026-05-19 (HIGH-4 autonomous gate)

### R-8E-H7 — Gate 8E autonomous=true on Phase8E2ESmokeTest green (closes cycle-2 HIGH-4 for 8E)
**Decision:** Frontmatter `autonomous: true` remains for 8E, BUT post-execution acceptance is extended: `Phase8E2ESmokeTest` (defined in 8A R-H13) step 7 (Queue requeue) MUST be green after 8E lands. If the smoke test fails at step 7, the executor halts and surfaces the failure to the operator. Acceptance: post-8E, `./gradlew :backend:api:test --tests "*Phase8E2ESmokeTest*" -Dphase8.smoke.steps=1-7` exits 0 (steps 1-3 covered by 8A, step 4 by 8B, step 5 by 8D, step 6 by 8C, step 7 by 8E; step 8 still pending 8F).

### R-8E-H8 — Carry forward 8A ownership matrix for `processing_job` schema (closes cycle-2 HIGH-3 propagation)
**Decision:** 8E's Liquibase 078 changeset MUST cite `docs/ops/admin-shared-file-ownership.md` line for the `db.changelog-master.yaml` row in its YAML comment header (`# Owner: 8E (per docs/ops/admin-shared-file-ownership.md)`). This makes the cross-plan changelog merge audit-able.

</reviews_addendum_8E>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8E-01: QueueHealthQueryService + DeadLetterRequeueService + projections + audit-row + AdminPathBodyBan compliance</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/DeadLetterRequeueService.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/projection/QueueHealthSnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/projection/QueueDepthByType.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/projection/RetryDistributionBucket.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterPage.java,
    backend/core/src/main/java/com/zeromail/core/admin/queue/package-info.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java (lines 14-80 — outbox/processing_job query idioms + claim queries),
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java + AuditLogRow.java (page projection idiom),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C13,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-QUEUE-01/02
  </read_first>
  <behavior>
    - QueueHealthSnapshot record: List(QueueDepthByType) depthByType (jobType, pendingCount, processingCount); Duration oldestUnleasedJobAge; List(RetryDistributionBucket) retryHistogram (attempts bucket 0/1/2/3/4+, count); double failureRateLast24h; int deadLetterCount; Instant snapshotAt.
    - QueueDepthByType record: (String jobType, int pendingCount, int processingCount).
    - RetryDistributionBucket record: (int attemptsBucket, int rowCount).
    - DeadLetterRow record: (UUID jobId, String jobType, String lastFailureReason, int retryCount, Instant lastFailedAt, Instant createdAt) — explicitly NO payloadJson field.
    - DeadLetterPage record: (List(DeadLetterRow) rows, String nextCursor, int totalEstimate).
    - QueueHealthQueryService.snapshot() returns QueueHealthSnapshot via single multi-statement batch over outbox + processing_job; queries: GROUP BY job_type+status for depth; MIN(created_at) WHERE status=PENDING AND (locked_until IS NULL OR locked_until LT NOW) for oldest; FLOOR(LEAST(attempts,4)) GROUP for histogram; FILTER WHERE status=FAILED AND last_failed_at GTE NOW - 24h over total for rate; COUNT WHERE status=DEAD_LETTER. NEVER selects payload_json.
    - QueueHealthQueryService.deadLetterPage(cursor, limit) returns DeadLetterPage; SELECT id, job_type, last_failure_reason, attempts, last_failed_at, created_at FROM processing_job WHERE status=DEAD_LETTER ORDER BY last_failed_at DESC. NEVER selects payload_json.
    - DeadLetterRequeueService.requeue(UUID jobId, String reason): AdminContext.currentOrThrow(); @Transactional UPDATE processing_job SET status=PENDING, attempts=0, locked_until=NULL, last_failed_at=NULL WHERE id=:jobId AND status=DEAD_LETTER; writes DEAD_LETTER_REQUEUED audit row with before_state_json={jobId, jobType, attemptsBeforeRequeue, lastFailureReason} (NO payload_json), after_state_json={status:PENDING, attempts:0}, reason. Returns 0 if no row updated (idempotent).
    - All projection records and service signatures contain zero references to payload_json / payloadJson; AdminPathBodyBanTest stays green.
  </behavior>
  <action>
    Use Spring Data JDBC NamedParameterJdbcTemplate for aggregate queries (single connection, multi-statement); per CONVENTIONS §6 read-side hot paths use Spring Data JDBC not JPA. Per RESEARCH Pitfall 7 (audit exfiltration): last_failure_reason is a short stored enum/string set by worker (max 100 chars) — NOT raw exception stack trace. If existing processing_job schema lacks last_failure_reason column, add a Liquibase 054-processing-job-extend.yaml changeset (executor first verifies current schema via mcp__postgres__list_objects; only create if missing). Log lines follow privacy format event=queue_health_snapshot dlqCount={} oldestPendingSec={}. DeadLetterRequeueService writes audit via AdminAuditWriter from 8A. Requeue UPDATE intentionally clears attempts=0 per OPS-QUEUE-02 so the job gets fresh retry budget; worker resumes drain on next poll.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.queue.*"</automated>
  </verify>
  <done>
    QueueHealthSnapshot returns correct aggregates over fixture data; DeadLetterRequeueService re-queues row + writes audit; payload_json never appears in any service or projection signature.
  </done>
  <acceptance_criteria>
    - Fixture: 10 PENDING + 5 PROCESSING + 3 DEAD_LETTER + 2 outbox PENDING → QueueHealthSnapshot.depthByType shows correct counts; deadLetterCount=3.
    - Fixture: PENDING row created 5 min ago, locked_until NULL → oldestUnleasedJobAge approx 5 min.
    - DeadLetterRequeueService.requeue(jobId, "transient downstream") UPDATEs row to PENDING+attempts=0 + writes 1 DEAD_LETTER_REQUEUED audit row with payload-free before_state_json.
    - Second call on same jobId returns 0 (idempotent, no audit written).
    - grep -rE "payload[_J]son" backend/core/src/main/java/com/zeromail/core/admin/queue/ returns 0 hits.
    - AdminPathBodyBanTest green over queue projection package.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8E-02: AdminQueueController + DTOs + apps/admin /queue route with KpiCard + AutoRefreshIndicator + dead-letter table + Re-queue ConfirmTwiceDialog</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/queue/QueueHealthResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterPageResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterRowResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/queue/RequeueRequest.java,
    apps/admin/src/routes/queue.tsx,
    apps/admin/src/features/queue/queue-api.ts,
    apps/admin/src/features/queue/query-keys.ts,
    apps/admin/src/features/queue/use-queue-health.ts,
    apps/admin/src/features/queue/use-dead-letters.ts,
    apps/admin/src/features/queue/use-requeue.ts,
    apps/admin/src/components/KpiCard.tsx,
    apps/admin/src/components/AutoRefreshIndicator.tsx,
    apps/admin/e2e/queue.spec.ts
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java (controller idiom),
    apps/web/components/ui/card.tsx + table.tsx + chart.tsx + badge.tsx + button.tsx (primitives copied in 8A),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C13, §C14,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md (§/queue + §Component Composition + §Interaction Patterns auto-refresh),
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (queue screen visual reference)
  </read_first>
  <behavior>
    - AdminQueueController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/queue"):
      - GET /health returns QueueHealthResponse.
      - GET /dead-letters?cursor=&limit= returns DeadLetterPageResponse.
      - POST /dead-letters/{jobId}/requeue body RequeueRequest{reason} returns 204.
    - No endpoint accepts payload_json input or returns it.
    - RequeueRequest: @NotBlank @Size(min=8,max=500) @NoSentinelLeak String reason.
    - DTOs are records with @Schema; explicit field allowlist; AdminPathBodyBanTest stays green.
    - KpiCard props: {label: string, value: string|number, hint?: string, delta?: {sign:'up'|'down'|'flat', value:string}, tabular?: boolean}. Renders font-variant-numeric: tabular-nums (UI-SPEC §Typography) when tabular.
    - AutoRefreshIndicator props: {lastUpdatedAt: Date, intervalMs: number, paused: boolean, onPauseToggle: () => void}. Renders pulsing 6px accent dot + monospace "Updated Ns ago" + pause toggle (UI-SPEC §Interaction Patterns line 211). Respects prefers-reduced-motion (disable pulse).
    - apps/admin /queue route layout:
      - Top row: 5 KpiCard instances (Outbox depth, Oldest unleased age, Retry rate, Failure rate 24h, Dead-letter count).
      - Trend chart: stub (single-value gauge or sparkline) for 8E v1; full backend time-series deferred — render Skeleton placeholder with comment "TODO v1.3+: backend time-series for trend chart".
      - Dead-letter table: paginated columns (jobId first-8-char mono, job_type, last_failure_reason, retry_count, last_failed_at relative); right column has Re-queue button per row.
      - AutoRefreshIndicator top-right with 10s interval.
      - Re-queue opens ConfirmTwiceDialog with step-2 token = first 8 chars of jobId (UI-SPEC line 203). Final button label "Re-queue job" (less-destructive variant: secondary button + accent strip, not red, per UI-SPEC line 199 pattern).
    - TanStack Query: useQuery({queryKey: queueKeys.health(), queryFn: ..., refetchInterval: 10_000, refetchIntervalInBackground: false}) — pause when document.hidden (UI-SPEC §Interaction Pattern 2).
    - Playwright queue.spec.ts: login -> /queue -> KpiCards render with fixture values -> wait 10s -> counters update -> click Re-queue on fixture DLQ row -> ConfirmTwiceDialog with `Type "abc12345" to confirm` step -> submit reason -> toast with audit-row link.
  </behavior>
  <action>
    Implement per PATTERNS §C13/§C14 + UI-SPEC. Controllers + DTOs straightforward. KpiCard and AutoRefreshIndicator are first-time composed components — they live in apps/admin/src/components/ (root), NOT in feature folder, because they're reused on /spend in 8F. Per CLAUDE.md memory feedback_raw_shadcn_first: both justified by 3+ call sites — KpiCard used 4+ times on /queue + 4+ times on /spend (UI-SPEC §Component Composition line 235); AutoRefreshIndicator used on /queue + /spend. Recharts time-series chart: stub for 8E (single-value gauge or sparkline); full backend time-series deferred (RESEARCH does not include OPS-QUEUE-01 trend column; skip cleanly). Reduced-motion: useMediaQuery('(prefers-reduced-motion: reduce)') — if reduced, disable pulse animation but keep indicator dot visible. Playwright spec stubs /api/admin/queue/* responses to deterministic fixture so refetch tick is observable.
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.AdminQueueController*" && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/admin e2e -- --grep "queue"</automated>
  </verify>
  <done>
    KpiCards + AutoRefreshIndicator render; 10s auto-refresh ticks; Re-queue confirm-twice writes audit row; payload never appears in DOM or network; AdminPathBodyBanTest green over queue DTOs.
  </done>
  <acceptance_criteria>
    - GET /api/admin/queue/health returns QueueHealthResponse with 5 KPI values + dead-letter count.
    - GET /api/admin/queue/dead-letters returns DeadLetterPageResponse; row has no payloadJson field (JSON inspection).
    - POST /api/admin/queue/dead-letters/{jobId}/requeue body {reason:"short"} (7 chars) returns 400; with {reason:"transient worker stall"} returns 204.
    - Playwright queue.spec.ts: KpiCards render with mocked counts; AutoRefreshIndicator counter increments on 10s tick; ConfirmTwiceDialog step-2 token is first 8 chars of jobId; re-queue toast appears with audit-row link.
    - Network panel inspection during /queue page load: no response body contains a string field named payloadJson or payload_json.
    - AdminPathBodyBanTest green over /api/admin/queue/** DTOs.
  </acceptance_criteria>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin browser to /api/admin/queue/** | Read-only aggregates + re-queue action; payload never crosses |
| backend/api to outbox + processing_job | Aggregate-only queries; SELECT lists exclude payload_json column |
| Re-queue UPDATE | @Transactional with audit row insert in same tx |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-45 | Information Disclosure | payload_json leaks via queue DTO | mitigate | DTO contract has no payloadJson field; QueueHealthQueryService SQL never selects the column; AdminPathBodyBanTest scans queue projections + DTOs for forbidden field names |
| T-08-46 | Tampering | Re-queue without audit | mitigate | DeadLetterRequeueService writes DEAD_LETTER_REQUEUED audit row in same @Transactional as UPDATE; rollback removes both |
| T-08-47 | Elevation of Privilege | Admin edits payload via re-queue | mitigate | Re-queue endpoint accepts only jobId + reason; no payload field in RequeueRequest |
| T-08-48 | Denial of Service | 10s auto-refresh storms backend | mitigate | TanStack refetchIntervalInBackground=false; refetch pauses when document.hidden (UI-SPEC §Interaction Pattern 2); single admin tab worst-case |
| T-08-49 | Repudiation | Re-queue with insufficient reason | mitigate | RequeueRequest.reason min 8 chars + @NoSentinelLeak guard; ConfirmTwiceDialog also enforces client-side |
| T-08-SC | Tampering | No new npm/pip/cargo installs in 8E | accept | KpiCard + AutoRefreshIndicator use existing primitives + lucide-react icons already in apps/admin |

</threat_model>

<verification>

```bash
./gradlew :backend:core:test :backend:api:test --tests "*AdminQueue*" --tests "*Queue*"
pnpm --filter @zeromail/admin test:unit
pnpm --filter @zeromail/admin e2e -- --grep "queue"

# Verify DTO has no payload field
grep -rE "payload[_J]son" backend/api/src/main/java/com/zeromail/api/dto/admin/queue/  # expect 0
grep -rE "payload[_J]son" backend/core/src/main/java/com/zeromail/core/admin/queue/  # expect 0

# Verify AdminPathBodyBanTest covers queue DTO package
./gradlew :backend:core:test --tests "*AdminPathBodyBanTest*"
```

</verification>

<success_criteria>
- [ ] QueueHealthQueryService returns correct aggregates over outbox + processing_job (no payload selects)
- [ ] DeadLetterRow DTO has no payloadJson field
- [ ] DeadLetterRequeueService writes audit row + re-queues with attempts=0
- [ ] AdminQueueController exposes /health + /dead-letters + /requeue under @PreAuthorize ADMIN
- [ ] KpiCard + AutoRefreshIndicator shared components shipped (justified by 3+ call sites)
- [ ] /queue page auto-refreshes every 10s; pauses on document.hidden + reduced-motion
- [ ] Re-queue uses ConfirmTwiceDialog with first-8-char jobId step-2 token
- [ ] Playwright queue spec green
- [ ] AdminPathBodyBanTest green over queue projection + DTO packages
- [ ] (reviews-pass) Liquibase 078 adds `admin_requeue_count INTEGER NOT NULL DEFAULT 0` + `last_failure_reason VARCHAR(100)` to `processing_job`
- [ ] (reviews-pass) Requeue increments `admin_requeue_count` (NOT resets) while resetting `attempts=0`; 6th KPI surfaces admin-requeued count
- [ ] (reviews-pass) Failure rate denominator is 24h-bounded (`created_at >= NOW() - INTERVAL '24h'`) not lifetime
- [ ] (reviews-pass) `JobFailureReason` enum gates `last_failure_reason` writes; `WorkerFailureReasonEnumOnlyTest` ArchUnit green
- [ ] (reviews-pass) `QueueHealthQueryServiceSqlSpyTest` asserts no emitted SQL string contains `payload_json` / `payloadJson` tokens
- [ ] (cycle-3) Post-8E execution: `Phase8E2ESmokeTest` steps 1-7 green; step 7 (Queue requeue) is 8E's contribution gate
- [ ] (cycle-3) Liquibase 078 YAML header cites `docs/ops/admin-shared-file-ownership.md` for the `db.changelog-master.yaml` include row
</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8E-SUMMARY.md` when done.
</output>
