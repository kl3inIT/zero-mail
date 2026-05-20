# Phase 8: Bulk Unsubscribe Campaign — Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 58 planned files (5 Liquibase + 23 backend/core + 6 backend/api DTO/controller + 4 backend/worker + 4 tests + 13 frontend feature + 4 frontend pages + 1 sidebar update + 2 i18n + 1 Playwright spec)
**Analogs found:** 54 strong matches / 58 — 4 files are net-new patterns with adapted hybrid analogs (continuous-poll worker loop, RestClient one-click POST, mailto sender, recipient-validation rule).

> **Numbering correction (carried from RESEARCH.md):** Latest existing changelog is `040-triage-audit-message-ref.yaml`. Phase 8 changelogs **MUST start at `041`** (not `039` as CONTEXT.md D-09 ghi nhầm). Planner: dùng `041..045` cho 5 changelog.

---

## File Classification

### Group 1 — Liquibase changelogs (`backend/core/src/main/resources/db/changelog/changes/*.yaml`)

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `041-mail-message-observed-list-unsubscribe.yaml` | migration / ADD COLUMN | DDL forward-only | `032-mail-message-observed-sender-email.yaml` | exact (cùng table, cùng ADD COLUMN forward-only pattern) |
| `042-processing-job.yaml` | migration / createTable | DDL | `025-triage-audit.yaml` + `017-shedlock-table.yaml` | role-match (tenant FK + status enum CHECK + partial index pattern) |
| `043-sender-suppression.yaml` | migration / createTable | DDL | `027-tenant-sender-opt-in.yaml` | exact (per-tenant sender list + unique index per target) |
| `044-unsubscribe-campaign.yaml` | migration / createTable | DDL | `025-triage-audit.yaml` | role-match (campaign aggregate + FK to processing_job) |
| `045-unsubscribe-attempt.yaml` | migration / createTable + composite index | DDL | `025-triage-audit.yaml` | role-match (per-row state machine + FK + composite index) |

### Group 2 — `backend/core/cleanup/*` (new module)

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `core/cleanup/package-info.java` | Spring Modulith `@ApplicationModule` | static metadata | `core/analytics/package-info.java` | exact (same Modulith ApplicationModule pattern) |
| `core/cleanup/domain/UnsubscribeMethod.java` | enum (`OrderedEnum`) | value object | `core/onboarding/domain/OnboardingStep.java` | exact (`OrderedEnum` + `fromId` fail-loud — CONVENTIONS §4) |
| `core/cleanup/domain/UnsubscribeAttemptState.java` | enum (`OrderedEnum`) | state machine | `core/onboarding/domain/OnboardingStep.java` | exact |
| `core/cleanup/domain/SuppressionReason.java` | enum (`IdentifiedEnum`) | value object | `core/onboarding/domain/OnboardingStep.java` | role-match (unordered identity set) |
| `core/cleanup/domain/CampaignStatus.java` | enum (`OrderedEnum`) | state machine | same | exact |
| `core/cleanup/domain/UnsubscribeCampaignPolicy.java` | static policy class (constants) | value object | `core/triage/domain/TriageUndoPolicy.java` | exact (Duration constant + static factory) |
| `core/cleanup/persistence/UnsubscribeCampaignEntity.java` | JPA entity (write-side aggregate) | CRUD | `core/triage/persistence/TriageAuditEntity.java` | role-match (tenant-owned entity + state column + applied/reverted timestamps) |
| `core/cleanup/persistence/UnsubscribeCampaignRepository.java` | Spring Data JPA repo | CRUD | `core/triage/persistence/TriageAuditRepository.java` | exact |
| `core/cleanup/persistence/UnsubscribeAttemptEntity.java` | JPA entity (per-sender row) | CRUD | `core/triage/persistence/TriageAuditEntity.java` | role-match |
| `core/cleanup/persistence/UnsubscribeAttemptRepository.java` | Spring Data JPA repo | CRUD | `core/triage/persistence/TriageAuditRepository.java` | exact |
| `core/cleanup/persistence/SenderSuppressionEntity.java` | JPA entity | CRUD | `core/triage/persistence/TenantSenderOptInEntity.java` | exact (per-tenant single-target row) |
| `core/cleanup/persistence/SenderSuppressionRepository.java` | Spring Data JPA repo | CRUD | `core/triage/persistence/TenantSenderOptInRepository.java` | exact |
| `core/cleanup/persistence/ProcessingJobEntity.java` | JPA entity (generic outbox) | CRUD + status machine | `core/triage/persistence/TriageAuditEntity.java` | role-match |
| `core/cleanup/persistence/ProcessingJobRepository.java` | Spring Data JPA + `@Query` native SQL (SKIP LOCKED) | row-locked pickup | `core/triage/persistence/TriageAuditRepository.java` | role-match (native SQL claim pattern) |
| `core/cleanup/projection/UnsubscribeCandidateProjection.java` | read-side record | projection | `core/analytics/projection/TopSenderProjection.java` | exact (record + JdbcTemplate row mapper consumer) |
| `core/cleanup/projection/CampaignStatusProjection.java` | read-side record | projection | `core/triage/projection/AuditLogRow.java` | exact |
| `core/cleanup/projection/PerSenderAttemptProjection.java` | read-side record | projection | `core/triage/projection/AuditLogRow.java` | exact |
| `core/cleanup/projection/SenderSuppressionProjection.java` | read-side record | projection | `core/analytics/projection/TopSenderProjection.java` | exact |
| `core/cleanup/usecases/CandidateQueryService.java` | service / `@Transactional(readOnly=true)` + JdbcTemplate | read-side query (multi-SQL) | `core/analytics/usecases/AnalyticsSummaryQueryService.java` | exact (RESEARCH D-21) |
| `core/cleanup/usecases/CampaignPreviewService.java` | service / preview validation | request-response | `core/triage/usecases/TriageUndoService.java` (validation gate pattern) | role-match (cap check + risk-badge mapping) |
| `core/cleanup/usecases/CampaignExecuteService.java` | service / `@Transactional` (commit multi-row + processing_job INSERT) | command + transactional | `core/triage/persistence/TriageAuditWriter.java` (write seam) + `TriageUndoService.undo` (multi-row write) | role-match (D-04 transaction boundary) |
| `core/cleanup/usecases/CampaignStatusQueryService.java` | service / `@Transactional(readOnly=true)` | read-side query | `core/analytics/usecases/AnalyticsSummaryQueryService.java` | exact |
| `core/cleanup/usecases/CampaignRetryService.java` | service / state-reset command | command | `core/triage/usecases/TriageUndoService.java` | role-match |
| `core/cleanup/usecases/CampaignUndoService.java` | service / multi-message Gmail restore | command | `core/triage/usecases/TriageUndoService.java` | exact (UNDO_WINDOW + restoreToInbox + removeLabel) |
| `core/cleanup/usecases/UnsubscribeExecutor.java` | service / per-sender orchestrator | sequential step + atomic gate | `core/triage/usecases/TriageOrchestratorService.java` | role-match |
| `core/cleanup/usecases/UnsubscribeHttpClient.java` | HTTP boundary class | request-response (RFC 8058 POST) | `core/config/RestClientConfig.java` + `byok.../ByokValidationGateway.java` (BYOK RestClient) | **partial** — new pattern (single-file `RestClient` builder + `exchange()` status gate). Planner phải build từ RESEARCH section "Spring RestClient Configuration" pseudocode. |
| `core/cleanup/usecases/UnsubscribeMailtoSender.java` | Gmail-write boundary (`users().messages().send()`) | request-response | `core/triage/usecases/TriageGmailWriter.java` | role-match (sibling boundary class — D-05/D-06; analog provides `executeGmailWrite` wrapper + `GmailApiClientFactory` injection) |
| `core/cleanup/usecases/UnsubscribeMailtoUriParser.java` | static helper (RFC 6068) | transform | (no analog — Java built-in `URI`) | no analog (RESEARCH §RFC 6068 pseudocode) |
| `core/cleanup/usecases/UnsubscribeDomainThrottle.java` | Redis INCR + EXPIRE | rate-limit | (no exact analog — closest: `core.shared.lock.RedisDistributedLock` for `StringRedisTemplate` wiring) | role-match (Redis client usage) |
| `core/cleanup/usecases/SuppressionAutoAddService.java` | service / heuristic (reply ≥1/90d) | event-driven (audit-fed) | `core/triage/usecases/TriageAuditSaga.java` | role-match (audit row scan + add to other table) |
| `core/cleanup/usecases/SuppressionCrudService.java` | service / CRUD | CRUD | `core/notification/usecases/NotificationPreferencesService.java` (suspected analog) hoặc `core/triage/persistence/TenantSenderOptInRepository.java` direct | role-match |
| `core/cleanup/exception/CampaignCapExceededException.java` | business exception | exception | `core/triage/exception/TriageUndoException.java` | exact |
| `core/cleanup/exception/UndoWindowExpiredException.java` | business exception | exception | `core/triage/exception/TriageUndoException.java` | exact |
| `core/cleanup/exception/CampaignNotFoundException.java` | business exception | exception | `core/triage/exception/TriageAuditNotFoundException.java` | exact |
| `core/cleanup/exception/SuppressedSenderException.java` | business exception | exception | `core/triage/exception/TriageSafetyViolationException.java` | exact |

### Group 3 — `backend/api/controllers/cleanup/*` + DTOs

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `api/controllers/cleanup/UnsubscribeCandidateController.java` | thin controller (`GET`) | request-response | `api/controllers/analytics/AnalyticsController.java` | exact (TenantContext + `@RequestParam window` + log line + Service call + `from(projection)`) |
| `api/controllers/cleanup/UnsubscribeCampaignController.java` | thin controller (`POST preview/execute`, `GET {id}`, `POST retry`, `POST undo`) | request-response | `api/controllers/triage/TriageAuditController.java` | exact (TenantContext + `@PathVariable UUID auditId` undo pattern reusable cho `undo` + `retry`) |
| `api/controllers/cleanup/SuppressionController.java` | thin controller (`GET/POST/DELETE`) | CRUD | `api/controllers/notifications/NotificationPreferencesController.java` (or `MeController.java`) | role-match (CRUD endpoints) |
| `api/dto/cleanup/UnsubscribeCandidateResponse.java` | record DTO | wire shape | `api/dto/triage/AuditEntryResponse.java` | exact (record + `from(projection)` static factory) |
| `api/dto/cleanup/UnsubscribeCandidateListResponse.java` | record DTO (wrapper) | wire shape | `api/dto/triage/AuditListResponse.java` | exact (wrapper với `List.copyOf(items)` + `from(page)`) |
| `api/dto/cleanup/CampaignPreviewRequest.java` | request DTO | wire shape | `api/dto/triage/UndoAuditResponse.java` (record DTO) | role-match (request record với `List<String> senderEmails`) |
| `api/dto/cleanup/CampaignPreviewResponse.java` | record DTO | wire shape | `api/dto/triage/AuditEntryResponse.java` | exact |
| `api/dto/cleanup/CampaignExecuteRequest.java` | request record | wire shape | same | exact |
| `api/dto/cleanup/CampaignExecuteResponse.java` | record DTO (jobId) | wire shape | `api/dto/triage/UndoAuditResponse.java` | exact |
| `api/dto/cleanup/CampaignStatusResponse.java` | record DTO (status + perSender[]) | wire shape | `api/dto/triage/AuditListResponse.java` | role-match |
| `api/dto/cleanup/PerSenderStateResponse.java` | record DTO | wire shape | `api/dto/triage/AuditEntryResponse.java` | exact |
| `api/dto/cleanup/SuppressionEntryResponse.java` | record DTO | wire shape | `api/dto/triage/AuditEntryResponse.java` | exact |
| `api/dto/cleanup/SuppressionAddRequest.java` | request record | wire shape | same | exact |
| `api/dto/cleanup/package-info.java` | package marker | static | `api/dto/triage/package-info.java` | exact |

### Group 4 — `backend/worker/scheduling/*` + `backend/worker/cleanup/*`

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `worker/cleanup/ProcessingJobWorker.java` | continuous-poll worker (virtual thread) | row-locked pickup | (no exact analog — combine `worker/triage/TriagePendingReaperBatch.java` for `JdbcTemplate` + `@PostConstruct` virtual-thread pattern from RESEARCH §"Worker poll loop pseudocode") | **partial** (new pattern — D-02 SKIP LOCKED `SELECT FOR UPDATE` loop) |
| `worker/cleanup/UnsubscribeCampaignHandler.java` | job-type dispatch handler | command | `core/triage/usecases/TriageOrchestratorService.java` (orchestrator pattern) | role-match |
| `worker/scheduling/ProcessingJobReaperBatch.java` | `@Scheduled` reaper | row UPDATE WHERE stale | `worker/notification/DigestPendingReaperJob.java` | exact (`@Scheduled(fixedDelay)` + `@SchedulerLock` + `LockAssert.assertLocked()` + tenant scoped JdbcTemplate UPDATE) — **NOTE D-18:** placement `worker/scheduling/` (generic), KHÔNG `worker/cleanup/` |
| `worker/scheduling/ProcessingJobPurgeBatch.java` | `@Scheduled` daily purge (D-25, 03:00 UTC, batch ≤1000) | row DELETE WHERE | `worker/triage/TriageAuditPurgeJob.java` + `worker/triage/TriageAuditPurgeBatch.java` | exact (split-pattern: Job class với `@Scheduled(cron)` + Batch class với `JdbcTemplate` CTE delete + loop `do { ... } while (selectedCount == BATCH_LIMIT)`) |

### Group 5 — Tests (ArchUnit + Privacy Sweep)

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `backend/core/src/test/java/com/zeromail/core/arch/GmailWriteBoundaryTest.java` (rename từ `TriageGmailWriteBoundaryTest.java`) | ArchUnit boundary | static | `core/arch/TriageGmailWriteBoundaryTest.java` | exact (extend allow-list: thêm `UnsubscribeMailtoSender` + thêm `send` vào `Gmail.Users.Messages` allowed methods — RESEARCH §"Rule 2") |
| `backend/core/src/test/java/com/zeromail/core/arch/CleanupHttpClientBoundaryTest.java` | ArchUnit boundary | static | `core/arch/TriageGmailWriteBoundaryTest.java` | role-match (cấm `RestClient.builder()` + `new HttpClient()` ngoài `UnsubscribeHttpClient.java` — RESEARCH §"Rule 1") |
| `backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java` | Logback `ListAppender` content sweep | static-assertion | `core/triage/TriagePrivacySweepTest.java` | exact (sibling pattern — UNS-09; mirror sentinel-token approach) |
| `backend/core/src/test/java/com/zeromail/core/cleanup/UnsubscribeMailtoSenderRecipientGuardTest.java` | unit test (semantic guard) | static-assertion | `core/triage/...` unit tests (project pattern) | role-match (RESEARCH §"Rule 5") |

### Group 6 — Frontend `apps/web/features/cleanup/*` (sub-feature folders)

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts` | openapi-fetch wrappers | request-response | `features/analytics/api/analytics-api.ts` + `features/notifications/api/notifications-api.ts` | exact (`api.GET/POST` + `unwrap` + `components['schemas']`) |
| `features/cleanup/unsubscribe-campaign/query-keys.ts` | TanStack key factory | static | `features/analytics/query-keys.ts` | exact (UI-SPEC D-13 đã lock shape) |
| `features/cleanup/unsubscribe-campaign/hooks/useCandidates.ts` | `useQuery` | read | `features/analytics/hooks/useAnalyticsSummary.ts` | exact (`staleTime: 60_000`) |
| `features/cleanup/unsubscribe-campaign/hooks/usePreviewCampaign.ts` | `useMutation` | command | `features/notifications/hooks/useUpdateNotificationPreferences.ts` | role-match (mutation + toast onSuccess/onError) |
| `features/cleanup/unsubscribe-campaign/hooks/useExecuteCampaign.ts` | `useMutation` | command | same | role-match |
| `features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus.ts` | `useQuery` với `refetchInterval` conditional (D-15) | polling | `features/analytics/hooks/useAnalyticsSummary.ts` (base shape) + UI-SPEC D-15 (refetchInterval callback) | role-match (polling pattern mới — không có analog trực tiếp) |
| `features/cleanup/unsubscribe-campaign/hooks/useRetrySender.ts` | `useMutation` | command | `features/notifications/hooks/useUpdateNotificationPreferences.ts` | exact |
| `features/cleanup/unsubscribe-campaign/hooks/useUndoCampaign.ts` | `useMutation` | command | same | exact |
| `features/cleanup/unsubscribe-campaign/messages.ts` | i18n source (`{ vi, en }`) | static (i18n source) | `features/notifications/messages.ts` | exact (CONVENTIONS §10) |
| `features/cleanup/suppression/api/suppression-api.ts` | openapi-fetch wrappers | CRUD | `features/notifications/api/notifications-api.ts` | exact |
| `features/cleanup/suppression/query-keys.ts` | TanStack key factory | static | `features/notifications/query-keys.ts` | exact |
| `features/cleanup/suppression/hooks/useSuppressionList.ts` | `useQuery` | read | `features/notifications/hooks/useNotificationPreferences.ts` | exact |
| `features/cleanup/suppression/hooks/useAddSuppression.ts` | `useMutation` | CRUD | `features/notifications/hooks/useUpdateNotificationPreferences.ts` | exact |
| `features/cleanup/suppression/hooks/useRemoveSuppression.ts` | `useMutation` | CRUD | same | exact |
| `features/cleanup/suppression/messages.ts` | i18n source | static | `features/notifications/messages.ts` | exact |
| `features/cleanup/unsubscribe-campaign/components/*.tsx` (12 components per UI-SPEC §"File structure") | client components | UI | UI-SPEC §"Component Composition Recipe" + `features/analytics/components/*` | role-match (composition recipe đã lock; analog cung cấp shadcn-import pattern) |
| `features/cleanup/suppression/components/*.tsx` (5 components) | client components | UI | same | role-match |

### Group 7 — Frontend pages + sidebar nav

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `apps/web/app/(protected)/(app)/cleanup/page.tsx` | redirect server component | redirect | (no analog — `redirect('/cleanup/unsubscribe-campaign')`; Next.js stdlib) | no analog (UI-SPEC §"Routing rules") |
| `apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/page.tsx` | server component + Suspense + client | request-response | `apps/web/app/(protected)/(app)/analytics/page.tsx` | exact (page heading + lead + `<Suspense fallback={Skeleton}><PageClient /></Suspense>`) |
| `apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/[jobId]/page.tsx` | server component (dynamic route) + UUID validate | request-response | same | role-match (UUID validate ở server boundary — UI-SPEC §"Routing rules") |
| `apps/web/app/(protected)/(app)/cleanup/suppression/page.tsx` | server component | request-response | same | exact |
| `apps/web/components/shell/AppSidebar.tsx` (MODIFY) | sidebar nav extend | static | `AppSidebar.tsx` line 36-55 `NavItem` type + `MAIL_NAV` array | exact (extend `MAIL_NAV` + extend `NavItem.labelKey` union — UI-SPEC §"Sidebar nav update"; recommend hướng A `children: NavItem[]`) |

### Group 8 — i18n bundles (generated, **không edit trực tiếp** — CONVENTIONS §10)

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `apps/web/i18n/messages/vi.json` (regen từ `pnpm i18n:build`) | generated bundle | static | (already exists) | n/a — không edit, regen từ `features/cleanup/*/messages.ts` |
| `apps/web/i18n/messages/en.json` (regen) | generated bundle | static | same | n/a |

**Effective key list:** UI-SPEC §"Copywriting Contract" liệt kê ~75 key dưới `cleanup.unsubscribe.*` + `cleanup.suppression.*` + `nav.cleanup` namespace. Planner viết toàn bộ vào `features/cleanup/unsubscribe-campaign/messages.ts` + `features/cleanup/suppression/messages.ts`; sidebar `nav.cleanup` keys vào `features/shell/messages.ts` (existing file).

### Group 9 — Playwright e2e

| Planned file | Role | Data Flow | Closest analog | Match Quality |
|--------------|------|-----------|----------------|---------------|
| `apps/web/e2e/cleanup/unsubscribe-campaign.spec.ts` | Playwright spec (golden path) | UI integration | `apps/web/e2e/analytics.spec.ts` + `apps/web/e2e/needs-reply.spec.ts` | role-match (UI-SPEC §"Playwright e2e (UNS-05 golden path)" lock 9-step flow; analog cung cấp `chrome-test-utils` + `openAuthenticatedRoute` + viewport loop pattern) |

---

## Pattern Assignments

### Group 1 — Liquibase changelogs

#### `041-mail-message-observed-list-unsubscribe.yaml` (ADD COLUMN, forward-only)

**Analog:** `backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml`

**Excerpt to mirror** (entire file — 18 lines):
```yaml
databaseChangeLog:
  - changeSet:
      id: 032-mail-message-observed-sender-email
      author: zeromail
      comment: >
        Adds future-only sanitized sender metadata for Phase 5C analytics. Existing observed
        message rows remain valid with NULL sender_email; analytics queries filter NULL values.
      changes:
        - addColumn:
            tableName: mail_message_observed
            columns:
              - column:
                  name: sender_email
                  type: varchar(320)
      rollback:
        - dropColumn:
            tableName: mail_message_observed
            columnName: sender_email
```

**What to change vs analog:**
- `id: 041-mail-message-observed-list-unsubscribe`
- Add 3 columns (per D-09): `list_unsubscribe_url VARCHAR(2048) NULL`, `list_unsubscribe_mailto VARCHAR(512) NULL`, `list_unsubscribe_one_click BOOLEAN NOT NULL DEFAULT false`
- Comment update: "Forward-only backfill (D-10). Rows ingested before Phase 8 keep NULL. Candidate query filters `(list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL)`. URL invariant: any non-NULL value begins with `https://` (D-11 parse-time guard)."

#### `042-processing-job.yaml`

**Analog:** `backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml`

**Excerpt to mirror** (lines 121-148 — CHECK constraints + partial index pattern):
```yaml
        - sql:
            comment: Triage decision values match TriageDecision ids.
            sql: ALTER TABLE triage_audit ADD CONSTRAINT ck_triage_audit_decision CHECK (decision IN ('PENDING','APPLIED','SHADOW_LOGGED','REJECTED_BY_SAFETY_NET','REJECTED_BY_SAFETY_POLICY','FAILED','REVERTED'))
        - createIndex:
            tableName: triage_audit
            indexName: idx_triage_audit_tenant_message
            columns:
              - column: { name: tenant_id }
              - column: { name: gmail_message_id }
        - sql:
            comment: Partial index for stale PENDING reclaim and reaper scans.
            sql: CREATE INDEX idx_triage_audit_pending_last_attempt ON triage_audit (last_attempt_at) WHERE decision = 'PENDING'
```

**What to change vs analog:**
- Use RESEARCH §"Liquibase DDL sketch (041-processing-job.yaml)" full schema (id UUID, tenant_id FK, job_type, payload JSONB, status, attempts, next_run_at, heartbeat_at, created_at, started_at, finished_at, failure_reason, updated_at) + `ck_processing_job_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED'))` + `ck_processing_job_job_type CHECK (job_type IN ('UNSUBSCRIBE_CAMPAIGN'))` + `idx_processing_job_pickup (status, next_run_at, created_at)` for SKIP LOCKED query + partial index `idx_processing_job_running_heartbeat ON processing_job (heartbeat_at) WHERE status = 'RUNNING'` for reaper scan.

#### `043-sender-suppression.yaml`

**Analog:** `backend/core/src/main/resources/db/changelog/changes/027-tenant-sender-opt-in.yaml` (closest by role)

**Pattern excerpts (from RESEARCH §"Changelog 042 — sender_suppression"):**
```yaml
- sql:
    sql: ALTER TABLE sender_suppression ADD CONSTRAINT ck_sender_suppression_one_target CHECK ((sender_email IS NOT NULL) <> (sender_domain IS NOT NULL))
- sql:
    sql: ALTER TABLE sender_suppression ADD CONSTRAINT ck_sender_suppression_reason CHECK (reason IN ('manual','replied','auto'))
- sql:
    comment: Unique per tenant per target.
    sql: CREATE UNIQUE INDEX ux_sender_suppression_email ON sender_suppression (tenant_id, sender_email) WHERE sender_email IS NOT NULL
- sql:
    sql: CREATE UNIQUE INDEX ux_sender_suppression_domain ON sender_suppression (tenant_id, sender_domain) WHERE sender_domain IS NOT NULL
```

**What to change vs analog:** Use RESEARCH full schema (id UUID PK, tenant_id FK delete-cascade, sender_email/sender_domain nullable XOR, reason, created_at) + 2 partial unique indexes per target (email vs domain).

#### `044-unsubscribe-campaign.yaml` + `045-unsubscribe-attempt.yaml`

**Analog:** `025-triage-audit.yaml` (tenant FK + applied_at/reverted_at + CHECK on decision values)

**What to change vs analog:**
- `044`: id UUID PK, tenant_id FK, job_id UUID FK→processing_job (D-19), status, applied_at, reverted_at, total_sender, total_history_msg, created_at + CHECK `status IN ('QUEUED','RUNNING','COMPLETED','FAILED')`.
- `045`: id UUID PK, campaign_id FK→unsubscribe_campaign (delete CASCADE), sender_email, sender_domain, unsubscribe_method CHECK `IN ('ONE_CLICK','MAILTO')`, state CHECK `IN ('PENDING','RUNNING','OK','FAILED')`, failure_reason, archived_message_count INT DEFAULT 0, started_at, finished_at + composite index `(campaign_id, state)`.

---

### Group 2 — `core.cleanup.*` (key files only — others mirror exact)

#### `core/cleanup/package-info.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/analytics/package-info.java`

**Excerpt to mirror** (entire 7 lines):
```java
@ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"triage", "gmail", "shared.persistence", "shared.lang"})
package com.zeromail.core.analytics;

import org.springframework.modulith.ApplicationModule;
```

**What to change vs analog:** Per D-17 + RESEARCH §"Spring Modulith Module Declaration":
```java
@ApplicationModule(
        displayName = "Cleanup",
        allowedDependencies = {"gmail", "triage", "analytics", "tenant",
            "shared.privacy", "shared.persistence", "shared.lang"})
package com.zeromail.core.cleanup;
```

#### `core/cleanup/domain/UnsubscribeMethod.java` (and sibling enums)

**Analog:** `backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java` (CONVENTIONS §4)

**Excerpt to mirror** (CONVENTIONS §4 example):
```java
public enum OnboardingStep implements OrderedEnum {
    GMAIL_CONNECTED(10), TEMPLATE_SELECTED(20), COMPLETE(30);
    // ...
    public static OnboardingStep fromId(String id) {
        return Stream.of(values()).filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
```

**What to change vs analog:**
- `UnsubscribeMethod`: `ONE_CLICK(10), MAILTO(20), NONE(30)` (with `IdentifiedEnum` since unordered).
- `UnsubscribeAttemptState`: `PENDING(10), RUNNING(20), OK(30), FAILED(40)` (`OrderedEnum`).
- `CampaignStatus`: `QUEUED(10), RUNNING(20), COMPLETED(30), FAILED(40)` (`OrderedEnum`).
- `SuppressionReason`: `MANUAL("manual"), REPLIED("replied"), AUTO("auto")` (`IdentifiedEnum`).

#### `core/cleanup/domain/UnsubscribeCampaignPolicy.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageUndoPolicy.java`

**Excerpt to mirror** (entire 17 lines):
```java
public final class TriageUndoPolicy {
    public static final Duration UNDO_WINDOW = Duration.ofDays(30);
    private TriageUndoPolicy() {}
    public static Instant undoableUntil(Instant appliedAt) {
        return Objects.requireNonNull(appliedAt, "appliedAt must not be null").plus(UNDO_WINDOW);
    }
}
```

**What to change vs analog:** Add constants `MAX_SENDERS_PER_CAMPAIGN = 25`, `MAX_HISTORY_MESSAGES_PER_CAMPAIGN = 2000`, `UNDO_WINDOW = Duration.ofDays(30)` (reuse same duration but redeclare per CONVENTIONS §1 module boundary), `UNSUBSCRIBED_LABEL_NAME = "Zero Mail/Unsubscribed"`.

#### `core/cleanup/persistence/UnsubscribeCampaignEntity.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java`

**Imports + entity declaration excerpt** (lines 1-22):
```java
package com.zeromail.core.triage.persistence;
// ...
@Entity
@Table(name = "triage_audit")
@AttributeOverride(name = "id", column = @Column(name = "audit_id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class TriageAuditEntity extends AbstractTenantOwnedEntity {
```

**What to change vs analog:**
- Package: `com.zeromail.core.cleanup.persistence`.
- `@Table(name = "unsubscribe_campaign")` + `@AttributeOverride(name = "id", column = @Column(name = "id"))` (PK uses `id`, not `audit_id`).
- Fields per D-09: `jobId UUID`, `status (varchar→CampaignStatus.id())`, `appliedAt Instant`, `revertedAt Instant`, `totalSenderCount int`, `totalHistoryMessageCount int`, `createdAt Instant`.
- `protected NoArgsCtor` (Hibernate) + explicit constructor (Lombok-free per CONVENTIONS §3).

#### `core/cleanup/usecases/CandidateQueryService.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/analytics/usecases/AnalyticsSummaryQueryService.java`

**Imports + service skeleton excerpt** (lines 20-40):
```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsSummaryQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSummaryQueryService.class);

    private static final String OBSERVED_VOLUME_SQL =
            """
                    SELECT count(*)
                    FROM mail_message_observed
                    WHERE tenant_id = ?
                      AND observed_at >= ?
                      AND observed_at < ?
                      AND 'INBOX' = ANY(label_ids)
                    """;
```

**Top-senders pattern (lines 62-74)** — closest shape to candidate query:
```java
private static final String TOP_SENDERS_SQL =
        """
                SELECT sender_email, count(*) AS c
                FROM mail_message_observed
                WHERE tenant_id = ?
                  AND observed_at >= ?
                  AND observed_at < ?
                  AND sender_email IS NOT NULL
                  AND 'INBOX' = ANY(label_ids)
                GROUP BY sender_email
                ORDER BY c DESC, sender_email ASC
                LIMIT 10
                """;
```

**What to change vs analog (per D-10 + D-21):**
- Service name `CandidateQueryService`, `@Transactional(readOnly = true)`.
- Add filter `(list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL)`.
- Add anti-join to `sender_suppression` (exclude suppressed senders + domains).
- Project to record `UnsubscribeCandidateProjection(senderEmail, senderDomain, messageCount, lastSeenAt, unsubscribeMethod, suppressed=false)`.
- `LIMIT 25` default (SPEC requirement 1 — bound by `MAX_SENDERS_PER_CAMPAIGN`).

#### `core/cleanup/usecases/UnsubscribeHttpClient.java`

**Analog:** Hybrid — no exact analog. Closest: `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java` (existing `RestClient.Builder` bean with `JdkClientHttpRequestFactory` + `Redirect.NEVER`). Pattern source: RESEARCH §"Spring RestClient Configuration cho UnsubscribeHttpClient" Approach A pseudocode (lines 301-355 of RESEARCH).

**Excerpt to mirror** (RESEARCH §"Bean wiring (recommended)"):
```java
@Component
public class UnsubscribeHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_RESPONSE_BODY_BYTES = 1024L * 1024L; // 1 MB

    private final RestClient unsubscribeRestClient;

    public UnsubscribeHttpClient() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.unsubscribeRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
    // ... postOneClick(URI) → UnsubscribeResult per RESEARCH §"Error-mapping summary table"
}
```

**What to change vs analog:** Build new class per RESEARCH pseudocode. Status gate per D-08: only `200/201/202/204` → OK; 3xx → `HTTP_3XX_REDIRECT`; 4xx → `HTTP_4XX_{code}`; 5xx → `HTTP_5XX_{code}`; `HttpConnectTimeoutException` → `TIMEOUT`; `IOException` → `NETWORK_ERROR`. Validate `url.startsWith("https://")` defensively (D-11).

#### `core/cleanup/usecases/UnsubscribeMailtoSender.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java`

**Imports + Gmail-write template excerpt** (lines 1-44 + 187-204):
```java
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TriageGmailWriter {

    private static final Logger log = LoggerFactory.getLogger(TriageGmailWriter.class);
    private static final String USER_ID = "me";

    private final GmailApiClientFactory gmailApiClientFactory;

    public TriageGmailWriter(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory = gmailApiClientFactory;
    }
// ...
    private <T> T executeGmailWrite(
            UUID tenantId, String operation, GmailWriteOperation<T> gmailWriteOperation)
            throws IOException {
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
            return gmailWriteOperation.execute(gmail);
        } catch (GoogleJsonResponseException googleResponseException) {
            log.warn(
                    "event=triage_gmail_write_failed tenantId={} op={} status={}",
                    tenantId, operation, googleResponseException.getStatusCode());
            throw googleResponseException;
        }
    }
```

**What to change vs analog (per D-05/D-06/D-23):**
- Package: `com.zeromail.core.cleanup.usecases`.
- Sibling boundary class — **NOT extend** `TriageGmailWriter` (D-05 SRP).
- Method `sendUnsubscribeMailto(UUID tenantId, URI mailtoUri, String persistedRecipientFromHeader) throws IOException`:
  - Validate `"mailto".equals(mailtoUri.getScheme())` (D-23).
  - Parse recipient via RFC 6068 (`java.net.URI` → `getRawSchemeSpecificPart()` → split at `?`).
  - Validate parsed recipient == `persistedRecipientFromHeader` (D-06).
  - Build MIME via `ReplyMimeBuilder` (reuse triage helper) with subject="unsubscribe", body="unsubscribe" (defaults; preserve URI subject/body params if present).
  - Call `gmail.users().messages().send(USER_ID, message).execute()` inside `executeGmailWrite(...)` wrapper.
  - Log `event=cleanup_unsubscribe_mailto_sent tenantId={} senderDomain={}` (privacy — no full sender email).
- Privacy: NEVER log raw recipient email; use `senderDomain` only (CONVENTIONS §5).

---

### Group 3 — `backend/api/controllers/cleanup/*`

#### `api/controllers/cleanup/UnsubscribeCandidateController.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java`

**Imports + controller excerpt** (lines 26-53 — entire `summary` handler):
```java
@RestController
@Tag(name = "analytics")
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private static final String DEFAULT_WINDOW_ID = "7d";

    private final AnalyticsSummaryQueryService analyticsSummaryQueryService;

    public AnalyticsController(AnalyticsSummaryQueryService analyticsSummaryQueryService) {
        this.analyticsSummaryQueryService = analyticsSummaryQueryService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
            @RequestParam(value = "window", required = false) String rawWindow) {
        AnalyticsWindow analyticsWindow = resolveWindow(rawWindow);
        UUID tenantId = TenantContext.currentTenantUuid();
        TimeWindow timeWindow = TimeWindow.endingAt(Instant.now(), analyticsWindow.duration());
        AnalyticsSummaryProjection projection =
                analyticsSummaryQueryService.summarize(tenantId, timeWindow);
        log.info("event=analytics_summary_requested tenantId={} window={}",
                tenantId, analyticsWindow.id());
        return AnalyticsSummaryResponse.from(projection, analyticsWindow);
    }
```

**What to change vs analog:**
- `@RequestMapping("/api/unsubscribe")` + `@Tag(name = "cleanup")`.
- `@GetMapping("/candidates")` accept `?window=30d&limit=25`.
- Log line: `event=cleanup_candidates_requested tenantId={} window={} limit={}`.
- Return `UnsubscribeCandidateListResponse.from(...)`.

#### `api/controllers/cleanup/UnsubscribeCampaignController.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java`

**Imports + 2-handler excerpt** (lines 40-62):
```java
@GetMapping("/api/triage/audit")
public AuditListResponse list(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Instant since,
        @RequestParam(required = false) Instant until) {
    UUID tenantId = TenantContext.currentTenantUuid();
    validateAuditCursor(cursor);
    AuditLogPageQuery query = new AuditLogPageQuery(limit, cursor, action, since, until);
    AuditLogPage page = auditLogQueryService.page(tenantId, query);
    log.info("event=triage_audit_listed tenantId={} limit={}", tenantId, query.limit());
    return AuditListResponse.from(page);
}

@PostMapping("/api/triage/audit/{auditId}/undo")
public UndoAuditResponse undo(@PathVariable UUID auditId) {
    UUID tenantId = TenantContext.currentTenantUuid();
    UndoAuditResult undoAuditResult =
            triageUndoService.undo(new UndoAuditCommand(auditId, tenantId));
    log.info("event=triage_undo_requested tenantId={} auditId={}", tenantId, auditId);
    return UndoAuditResponse.from(undoAuditResult);
}
```

**What to change vs analog:**
- 5 handlers per SPEC: `POST /api/unsubscribe/campaigns/preview`, `POST /api/unsubscribe/campaigns/execute`, `GET /api/unsubscribe/campaigns/{jobId}`, `POST /api/unsubscribe/campaigns/{jobId}/senders/{senderEmail}/retry`, `POST /api/unsubscribe/campaigns/{jobId}/undo`.
- Log lines: `event=cleanup_campaign_preview_requested`, `event=cleanup_campaign_execute_requested`, `event=cleanup_campaign_status_requested`, `event=cleanup_campaign_retry_requested`, `event=cleanup_campaign_undo_requested` (all with `tenantId={} jobId={}`).
- `@ExceptionHandler` for `CampaignCapExceededException` → HTTP 400 with `CAMPAIGN_TOO_MANY_SENDERS` / `CAMPAIGN_TOO_MANY_MESSAGES` code (pattern from `AnalyticsController.invalidWindow` lines 55-68).
- `@ExceptionHandler` for `UndoWindowExpiredException` → HTTP 410 with `UNDO_WINDOW_EXPIRED` (SPEC req 7).

#### DTOs in `api/dto/cleanup/*`

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java` + `AuditListResponse.java`

**Excerpt to mirror** (`AuditListResponse.java` entire 17 lines):
```java
public record AuditListResponse(List<AuditEntryResponse> items, String nextCursor) {

    public AuditListResponse {
        items = List.copyOf(items);
    }

    public static AuditListResponse from(AuditLogPage page) {
        return new AuditListResponse(
                page.items().stream().map(AuditEntryResponse::from).toList(), page.nextCursor());
    }
}
```

**What to change vs analog (CONVENTIONS §1 + §3):**
- All DTOs are `record`s.
- Wrapper response always has `from(projection)` static factory (CONVENTIONS §1).
- Defensive `List.copyOf(items)` in compact constructor.
- Package: `com.zeromail.api.dto.cleanup` + `api/dto/cleanup/package-info.java`.

---

### Group 4 — `backend/worker/*`

#### `worker/cleanup/ProcessingJobWorker.java`

**Analog (hybrid):** RESEARCH §"Worker poll loop pseudocode" (lines 499-562) — no exact codebase analog, but `worker/triage/TriagePendingReaperBatch.java` provides `JdbcTemplate` + `@Transactional` + tenant-scoped invocation patterns.

**Excerpt to mirror** (from RESEARCH §"Worker poll loop pseudocode"):
```java
@Component
public class ProcessingJobWorker {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private final JdbcTemplate jdbcTemplate;
    private final UnsubscribeCampaignHandler unsubscribeCampaignHandler;
    private volatile boolean shouldRun = true;

    @PostConstruct
    void startPolling() {
        Thread.ofVirtual().name("processing-job-worker").start(this::pollLoop);
    }

    @PreDestroy
    void stopPolling() { shouldRun = false; }

    @Transactional
    Optional<UUID> pickQueuedJob() {
        return jdbcTemplate.query(
                """
                SELECT id FROM processing_job
                WHERE status = 'QUEUED' AND next_run_at <= NOW()
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED LIMIT 1
                """, // ...
```

**What to change vs analog:** Build new class per pseudocode. Per RESEARCH §"Single-attempt semantic" (ASSUMPTION 2): single `processing_job` row per campaign + state-driven attempt loop. Tenant-scoped invocation pattern from `DigestPendingReaperJob.reap` lines 62-66 — `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)`.

#### `worker/scheduling/ProcessingJobReaperBatch.java` (placement D-18: `worker/scheduling/`, KHÔNG `worker/cleanup/`)

**Analog:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java`

**Excerpt to mirror** (lines 19-53):
```java
@Component
public class DigestPendingReaperJob {

    static final String LOCK_NAME = "digestPendingReaper";

    private static final Logger log = LoggerFactory.getLogger(DigestPendingReaperJob.class);
    private static final int BATCH_LIMIT = 500;
    private static final Duration STUCK_PENDING_GRACE_PERIOD = Duration.ofMinutes(30);

    private final JdbcTemplate jdbcTemplate;
    private final DigestDeliveryService digestDeliveryService;
    // ...
    @Scheduled(fixedDelay = 300_000L)
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduledReap() {
        LockAssert.assertLocked();
        reap();
    }
```

**What to change vs analog (per D-03 + D-18):**
- Package: `com.zeromail.worker.scheduling`.
- `STALE_HEARTBEAT = Duration.ofMinutes(5)` (D-03).
- `@Scheduled(fixedDelayString = "PT60S")` (RESEARCH §"Reaper batch").
- `@SchedulerLock(name = "processingJobReaper", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")`.
- SQL `UPDATE processing_job SET status='QUEUED', attempts = attempts + 1, heartbeat_at=NULL, updated_at=NOW(), next_run_at=NOW() WHERE status='RUNNING' AND heartbeat_at < NOW() - INTERVAL '5 minutes'`.
- Log line `event=processing_job_reaper_reaped count={}` (system-level — no tenantId per `DigestPendingReaperJob.reap` line 72 pattern).

#### `worker/scheduling/ProcessingJobPurgeBatch.java` (D-25, daily 03:00 UTC, retention 90d)

**Analog:** `worker/triage/TriageAuditPurgeJob.java` + `worker/triage/TriageAuditPurgeBatch.java` (split-pattern)

**`TriageAuditPurgeJob` excerpt to mirror** (entire 52 lines):
```java
@Component
public class TriageAuditPurgeJob {

    private static final int BATCH_LIMIT = 1_000;

    private final TriageAuditPurgeBatch triageAuditPurgeBatch;
    private final Counter purgedRowsCounter;
    // ...
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "triageAuditPurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledPurge() {
        purge();
    }

    public int purge() {
        int totalDeleted = 0;
        int selectedCount;
        do {
            TriageAuditPurgeBatch.PurgeBatchResult batchResult =
                    triageAuditPurgeBatch.purgeExpiredOnce(BATCH_LIMIT);
            selectedCount = batchResult.selectedCount();
            totalDeleted += batchResult.deletedCount();
        } while (selectedCount == BATCH_LIMIT);
        // ...
    }
}
```

**`TriageAuditPurgeBatch` CTE-delete excerpt** (lines 27-66):
```java
@Transactional(propagation = Propagation.REQUIRED)
public PurgeBatchResult purgeExpiredOnce(int batchLimit) {
    Instant cutoff = clock.instant().minus(AUDIT_RETENTION);
    return jdbcTemplate.queryForObject(
            """
    WITH expired_audit AS (
      SELECT audit_id FROM triage_audit
       WHERE decided_at < ?
         AND decision IN ('APPLIED','REVERTED','REJECTED_BY_SAFETY_NET','REJECTED_BY_SAFETY_POLICY','FAILED')
       ORDER BY decided_at ASC, audit_id ASC
       LIMIT ? FOR UPDATE SKIP LOCKED
    ),
    deleted_audit AS (
      DELETE FROM triage_audit
       WHERE audit_id IN (SELECT audit_id FROM expired_audit)
       RETURNING audit_id
    )
    SELECT (SELECT COUNT(*) FROM expired_audit) AS selected_count,
           (SELECT COUNT(*) FROM deleted_audit) AS deleted_count
    """, ...);
}
```

**What to change vs analog (per D-25):**
- Package: `com.zeromail.worker.scheduling` (D-18 sibling of reaper).
- `PROCESSING_JOB_RETENTION = Duration.ofDays(90)` (D-25, not 30 like triage_audit).
- `@Scheduled(cron = "0 0 3 * * *")` (03:00 UTC daily — D-25).
- `@SchedulerLock(name = "processingJobPurge", ...)`.
- Target table `processing_job`; filter `WHERE finished_at < ? AND status IN ('COMPLETED','FAILED')` (D-25).
- `BATCH_LIMIT = 1_000` (D-25).
- **IMPORTANT (D-25):** `unsubscribe_campaign` + `unsubscribe_attempt` keep forever — DO NOT delete from these tables.
- Counter metric `zero_mail.cleanup.processing_job_purge.rows_deleted_total`.

---

### Group 5 — Tests

#### `core/arch/GmailWriteBoundaryTest.java` (rename — extend allow-list)

**Analog:** `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java`

**Excerpt to extend** (lines 14-70 — entire file is the analog):
```java
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class TriageGmailWriteBoundaryTest {

    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.usecases.TriageGmailWriter";
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @ArchTest
    static final ArchRule only_triage_gmail_writer_calls_gmail_write_apis =
            classes()
                    .that()
                    .resideInAPackage("..core.triage..") // EXTEND → "..core.."
                    .should(new ArchCondition<JavaClass>(
                                    "call Gmail write APIs only from " + TRIAGE_GMAIL_WRITER) {
                        // EXTEND allow-list: include UnsubscribeMailtoSender
    private static boolean isGmailWriteCall(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        boolean messageModify =
                normalizedOwnerName.endsWith(GMAIL_MESSAGES_OWNER) && methodName.equals("modify");
        // EXTEND: also "send" for mailto
        boolean draftCreateOrDelete =
                normalizedOwnerName.endsWith(GMAIL_DRAFTS_OWNER)
                        && (methodName.equals("create") || methodName.equals("delete"));
        return messageModify || draftCreateOrDelete;
    }
```

**What to change vs analog (per RESEARCH §"Rule 2"):**
- Rename file/class → `GmailWriteBoundaryTest` (no longer triage-specific).
- Allow-list constant: `List.of("com.zeromail.core.triage.usecases.TriageGmailWriter", "com.zeromail.core.cleanup.usecases.UnsubscribeMailtoSender")`.
- `resideInAPackage("..core..")` (broaden from `..core.triage..`).
- `isGmailWriteCall`: add `|| (normalizedOwnerName.endsWith(GMAIL_MESSAGES_OWNER) && methodName.equals("send"))` — mailto path uses `users.messages.send`.

#### `core/arch/CleanupHttpClientBoundaryTest.java` (new ArchUnit rule per D-07/D-22)

**Analog:** `core/arch/TriageGmailWriteBoundaryTest.java` (pattern shape)

**Excerpt to mirror** (lines 22-60 — the rule shape):
```java
@ArchTest
static final ArchRule only_triage_gmail_writer_calls_gmail_write_apis =
        classes()
                .that()
                .resideInAPackage("..core.triage..")
                .should(new ArchCondition<JavaClass>(...) {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
                        if (javaClass.getName().equals(TRIAGE_GMAIL_WRITER)) return;
                        javaClass.getMethodCallsFromSelf().forEach(methodCall -> {
                            // detect forbidden call
                        });
                    }
                })
                .because("TRG-02: ...")
                .allowEmptyShould(true);
```

**What to change vs analog (per RESEARCH §"Rule 1"):**
- Package guard: `..core.cleanup..`.
- Allow-list: only `com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient`.
- Detect: `callMethod(RestClient.class, "builder")`, `callMethod(RestClient.class, "create")`, `callConstructor(java.net.http.HttpClient.class)`.
- `.because("UNS-08: HTTP unsubscribe traffic is centralized behind UnsubscribeHttpClient.")`.

#### `core/cleanup/CleanupPrivacySweepTest.java`

**Analog:** `backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java`

**Excerpt to mirror** (lines 49-104 — setup + log capture):
```java
@Import(TriagePrivacySweepTest.MeterRegistryTestConfiguration.class)
@SuppressWarnings("SqlResolve")
class TriagePrivacySweepTest extends PostgresContainerTest {

    private static final String GMAIL_MESSAGE_ID = "gmail-message-privacy-sweep";
    private static final String RAW_SENDER_EMAIL = "sweep.sender@example.test";
    private static final String EMAIL_SUBJECT_SENTINEL = "EMAIL_SUBJECT_SENTINEL_04_08";
    // ...
    private static final List<String> FORBIDDEN_CONTENT_TOKENS = List.of(
            SENDER_DISPLAY_NAME_SENTINEL, EMAIL_SUBJECT_SENTINEL, EMAIL_SNIPPET_SENTINEL,
            EMAIL_BODY_SENTINEL, EMAIL_DRAFT_BODY_SENTINEL, LLM_PROMPT_SENTINEL,
            LLM_COMPLETION_SENTINEL, RAW_SENDER_EMAIL);

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogCapture() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.addFilter(new SensitiveMarkerScrubFilter());
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }
```

**What to change vs analog (per SPEC UNS-09):**
- Class `CleanupPrivacySweepTest extends PostgresContainerTest`.
- Fixture: seed sender into `mail_message_observed` với `list_unsubscribe_url='https://provider.test/unsub?token=SENTINEL'` + `list_unsubscribe_mailto='mailto:unsub@provider.test?subject=SUBJECT_SENTINEL'`.
- `@MockitoBean` thay vì gọi real HTTP — mock `UnsubscribeHttpClient` + `UnsubscribeMailtoSender`.
- Run campaign fixture via `CampaignExecuteService` → `UnsubscribeCampaignHandler`.
- Assert: log lines từ `core.cleanup.*` không chứa `RAW_SENDER_EMAIL`, `EMAIL_SUBJECT_SENTINEL`, raw `list_unsubscribe_url` token, raw mailto subject params.
- Per CONVENTIONS §5: log lines format `event=cleanup_unsubscribe_*` + `tenantId={}` + `senderDomain={}` only (no full sender email).

---

### Group 6 — Frontend `apps/web/features/cleanup/*`

#### `features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts`

**Analog:** `apps/web/features/analytics/api/analytics-api.ts`

**Excerpt to mirror** (lines 1-77 — full pattern):
```typescript
import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type AnalyticsWindow = '7d' | '30d' | '90d';
type GeneratedAnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse'];
export type TopSenderResponse = components['schemas']['TopSenderResponse'];
// ...
function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchAnalyticsSummary(
  window: AnalyticsWindow,
): Promise<AnalyticsSummaryResponse> {
  const result = await api.GET('/api/analytics/summary', { params: { query: { window } } });
  return unwrap(result, `/api/analytics/summary failed: ${result.response.status}`);
}
```

**Also see:** `apps/web/features/notifications/api/notifications-api.ts` lines 1-37 (POST/PATCH with `jsonHeaders()` + `xsrfHeader()`).

**What to change vs analog:**
- Export types: `UnsubscribeCandidateResponse`, `CampaignPreviewRequest/Response`, `CampaignExecuteRequest/Response`, `CampaignStatusResponse`, `PerSenderStateResponse` (all from `components['schemas']`).
- Functions: `fetchCandidates(window, limit)`, `previewCampaign(body)`, `executeCampaign(body)`, `fetchCampaignStatus(jobId)`, `retrySender(jobId, senderEmail)`, `undoCampaign(jobId)`.
- POST/DELETE use `xsrfHeader()` per `notifications-api.ts` line 10-11.

#### `features/cleanup/unsubscribe-campaign/query-keys.ts`

**Analog:** `apps/web/features/analytics/query-keys.ts`

**Excerpt to mirror** (entire 7 lines):
```typescript
import type { AnalyticsWindow } from '@/features/analytics/api/analytics-api';

export const analyticsKeys = {
  all: ['analytics'] as const,
  summary: (window: AnalyticsWindow) => [...analyticsKeys.all, 'summary', window] as const,
} as const;
```

**What to change vs analog (UI-SPEC D-13 locked shape):**
```typescript
export const unsubscribeCampaignKeys = {
  all: ['cleanup', 'unsubscribe-campaign'] as const,
  candidates: (window: string) => [...unsubscribeCampaignKeys.all, 'candidates', window] as const,
  byId: (jobId: string) => [...unsubscribeCampaignKeys.all, 'detail', jobId] as const,
} as const;
```

#### `features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus.ts` (polling)

**Analog (hybrid):** `apps/web/features/analytics/hooks/useAnalyticsSummary.ts` base shape + UI-SPEC §"CampaignStatusPage" pattern.

**Excerpt to mirror** (lines 1-19):
```typescript
'use client';
import { useQuery } from '@tanstack/react-query';
import { fetchAnalyticsSummary, type AnalyticsWindow } from '@/features/analytics/api/analytics-api';
import { analyticsKeys } from '@/features/analytics/query-keys';

export function useAnalyticsSummary(window: AnalyticsWindow) {
  return useQuery({
    queryKey: analyticsKeys.summary(window),
    queryFn: () => fetchAnalyticsSummary(window),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
```

**What to change vs analog (per D-15 + UI-SPEC):**
```typescript
useQuery({
  queryKey: unsubscribeCampaignKeys.byId(jobId),
  queryFn: () => fetchCampaignStatus(jobId),
  refetchInterval: (query) => {
    const status = query.state.data?.status;
    return status === 'QUEUED' || status === 'RUNNING' ? 2000 : false;
  },
});
```

#### `features/cleanup/unsubscribe-campaign/hooks/useExecuteCampaign.ts` (and other mutations)

**Analog:** `apps/web/features/notifications/hooks/useUpdateNotificationPreferences.ts`

**Excerpt to mirror** (lines 1-80 — full mutation pattern with toast + invalidation):
```typescript
'use client';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
// ...
export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient();
  const t = useTranslations();
  return useMutation({
    mutationFn: updateNotificationPreferences,
    onSuccess: (saved) => {
      queryClient.setQueryData(notificationsKeys.preferences(), saved);
      toast.success(t('settings.notifications.toast.savedTitle'));
    },
    onError: (_err, variables, context) => {
      toast.error(t('settings.notifications.toast.errorTitle'), { /* retry */ });
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: notificationsKeys.preferences() });
    },
  });
}
```

**What to change vs analog:**
- `useExecuteCampaign`: `onSuccess` → `router.push('/cleanup/unsubscribe-campaign/{jobId}')` + toast (UI-SPEC `cleanup.unsubscribe.preview.submitOk`).
- `useRetrySender`: handle HTTP 409 specially — show toast `cleanup.unsubscribe.retry.alreadyOk` (SPEC UNS-06).
- `useUndoCampaign`: handle HTTP 410 → toast `cleanup.unsubscribe.undo.windowExpiredToast`.
- All mutations: invalidate relevant query keys via `unsubscribeCampaignKeys.byId(jobId)` or `unsubscribeCampaignKeys.candidates(window)`.

#### `features/cleanup/unsubscribe-campaign/messages.ts` + `features/cleanup/suppression/messages.ts`

**Analog:** `apps/web/features/notifications/messages.ts`

**Excerpt to mirror** (lines 1-55 of `notifications/messages.ts`):
```typescript
export const notificationsMessages = {
  'settings.notifications.title': {
    vi: 'Thông báo',
    en: 'Notifications',
  },
  // ... ~24 keys
} as const;
```

**What to change vs analog (per UI-SPEC §"Copywriting Contract" + D-16):**
- Two separate files — namespace `cleanup.unsubscribe.*` (~55 keys) and `cleanup.suppression.*` (~20 keys).
- Export names: `unsubscribeCampaignMessages` + `suppressionMessages`.
- Every key shape `{ vi: 'tiếng Việt', en: 'English' }` per CONVENTIONS §10.
- UI-SPEC has the full key list — planner copies verbatim, mirrors English from Vietnamese copy.

---

### Group 7 — Frontend pages + sidebar nav

#### `apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/page.tsx`

**Analog:** `apps/web/app/(protected)/(app)/analytics/page.tsx`

**Excerpt to mirror** (entire 30 lines):
```typescript
import { Suspense } from 'react';
import { getTranslations } from 'next-intl/server';
import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { AnalyticsPageClient } from '@/features/analytics/components/AnalyticsPageClient';

export default async function AnalyticsPage() {
  const t = await getTranslations();
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 md:p-6">
      <div className="border-foreground/10 flex flex-col gap-3 border-b pb-5 md:flex-row md:items-end md:justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.page.eyebrow')}
          </p>
          <h1 className="text-foreground text-3xl leading-tight font-semibold">
            {t('analytics.page.title')}
          </h1>
        </div>
        <p className="text-muted-foreground max-w-2xl text-sm leading-6 md:text-right">
          {t('analytics.page.description')}
        </p>
      </div>
      <Suspense fallback={<AnalyticsSkeleton />}>
        <AnalyticsPageClient />
      </Suspense>
    </div>
  );
}
```

**What to change vs analog (per UI-SPEC §"Page-level layouts"):**
- Container `max-w-7xl` → keep, but UI-SPEC says `max-w-screen-xl mx-auto` at `xl` breakpoint — verify which is correct (analog has `max-w-7xl`; both are valid Tailwind, planner picks one consistent).
- Heading uses `text-2xl` per UI-SPEC §"Typography" (not `text-3xl` from analog — UI-SPEC has different typography contract for Phase 8).
- Replace `AnalyticsSkeleton` + `AnalyticsPageClient` with `CandidateListSkeleton` + `CandidateListPage`.
- i18n keys: `cleanup.unsubscribe.list.title`, `cleanup.unsubscribe.list.lead`.

#### `apps/web/app/(protected)/(app)/cleanup/unsubscribe-campaign/[jobId]/page.tsx`

**Analog:** Same as above (`analytics/page.tsx`) + UI-SPEC §"Routing rules" (UUID validation at server boundary).

**What to change vs analog:**
- Add `params: { jobId: string }` from Next.js dynamic route.
- Server-side UUID validate; if not UUID → `notFound()`.
- Render `CampaignStatusPage` with `jobId` prop.

#### `apps/web/app/(protected)/(app)/cleanup/page.tsx`

**No analog** — Next.js 16 Server Component redirect.

```typescript
import { redirect } from 'next/navigation';
export default function CleanupIndexPage() {
  redirect('/cleanup/unsubscribe-campaign');
}
```

#### `apps/web/components/shell/AppSidebar.tsx` (MODIFY)

**Analog:** Lines 36-55 of itself (existing `NavItem` type + `MAIL_NAV`).

**Excerpt to extend:**
```typescript
type NavItem = {
  href: string;
  labelKey:
    | 'nav.ai' | 'nav.analytics' | 'nav.needsReply' | 'nav.rules'
    | 'nav.billing' | 'nav.settings' | 'nav.onboardingProgress';
  icon: typeof Inbox;
  badge?: 'needs-reply';
};

const MAIL_NAV: NavItem[] = [
  { href: '/rules', labelKey: 'nav.rules', icon: ListChecks },
  { href: '/ai', labelKey: 'nav.ai', icon: Sparkles },
  { href: '/analytics', labelKey: 'nav.analytics', icon: BarChart3 },
  { href: '/needs-reply', labelKey: 'nav.needsReply', icon: MailQuestion, badge: 'needs-reply' },
];
```

**What to change vs analog (per UI-SPEC §"Sidebar nav update" — recommend hướng A):**
- Extend `NavItem.labelKey` union: add `| 'nav.cleanup' | 'nav.cleanup.unsubscribe' | 'nav.cleanup.suppression'`.
- Add optional `children?: NavItem[]` to `NavItem` type.
- Insert `MAIL_NAV` entry after analytics, before needs-reply: `{ href: '/cleanup', labelKey: 'nav.cleanup', icon: Recycle, children: [{ href: '/cleanup/unsubscribe-campaign', labelKey: 'nav.cleanup.unsubscribe', icon: ... }, { href: '/cleanup/suppression', labelKey: 'nav.cleanup.suppression', icon: ... }] }`.
- Import `Recycle` from `lucide-react`.
- Extend `renderNavItem` to render `SidebarMenuSub` (shadcn primitive) when `item.children` defined.

---

### Group 9 — Playwright e2e

#### `apps/web/e2e/cleanup/unsubscribe-campaign.spec.ts`

**Analog (hybrid):** `apps/web/e2e/analytics.spec.ts` + `apps/web/e2e/needs-reply.spec.ts`

**Excerpt to mirror** (`analytics.spec.ts` lines 1-44 — setup + assertions):
```typescript
import { expect, test } from '@playwright/test';
import {
  createChromeMockState, expectAppShellChrome, installChromeApiMock,
  openAuthenticatedRoute, seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`analytics panels and window switching at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState();
    await openAuthenticatedRoute(page, '/analytics', state);
    await expectAppShellChrome(page, { sidebarVisible: viewport.name === 'desktop' });
    await expect(page.getByRole('heading', { name: 'Analytics' })).toBeVisible();
    // ...
  });
}
```

**What to change vs analog (per UI-SPEC §"Playwright e2e (UNS-05 golden path)"):**
- 9-step golden path: (1) goto `/cleanup/unsubscribe-campaign`, (2) assert 3 fixture rows, (3) check 2 SAFE rows, (4) click "Xem trước campaign", (5) assert preview content, (6) click "Execute campaign", (7) wait for `status=COMPLETED` (polling), (8) assert undo banner, (9) goto suppression page + add entry.
- Use `chrome-test-utils.openAuthenticatedRoute` + mock candidate + preview + execute + status endpoints via `installChromeApiMock`.
- Two viewport loop (desktop + mobile) per analog pattern.

---

## Shared Patterns

### Authentication / Tenant context

**Source:** `backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java` line 47-48
**Apply to:** All `api/controllers/cleanup/*` files

```java
UUID tenantId = TenantContext.currentTenantUuid();
```

Spring Security session enforces auth at filter layer. Controllers extract `tenantId` from `TenantContext` (`ScopedValues`-backed, Phase 1).

### Service-owned transaction (CONVENTIONS §1)

**Source:** `backend/core/src/main/java/com/zeromail/core/analytics/usecases/AnalyticsSummaryQueryService.java` (read) + `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java` (write)
**Apply to:** All `core/cleanup/usecases/*Service.java` files

```java
@Service
public class CandidateQueryService {
    @Transactional(readOnly = true)
    public List<UnsubscribeCandidateProjection> findCandidates(UUID tenantId, ...) { ... }
}

@Service
public class CampaignExecuteService {
    @Transactional  // D-04: single transaction commits campaign + N attempts + 1 processing_job
    public CampaignExecuteResult execute(UUID tenantId, CampaignExecuteCommand command) { ... }
}
```

### DTO `from(projection)` static factory (CONVENTIONS §1)

**Source:** `backend/api/src/main/java/com/zeromail/api/dto/triage/AuditListResponse.java` lines 12-15
**Apply to:** All `api/dto/cleanup/*Response.java` files

```java
public static AuditListResponse from(AuditLogPage page) {
    return new AuditListResponse(
            page.items().stream().map(AuditEntryResponse::from).toList(), page.nextCursor());
}
```

Controllers return `Response.from(serviceResult)` — never inline `new SomeResponse(...)`.

### Records for DTOs / classes for entities / no Lombok (CONVENTIONS §3)

**Source:** `backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java` (record) + `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java` (class)
**Apply to:** All `api/dto/cleanup/*` (records) + all `core/cleanup/persistence/*Entity.java` (classes with `protected NoArgsCtor`)

### Enum state machine (CONVENTIONS §4)

**Source:** `backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java`
**Apply to:** `UnsubscribeMethod`, `UnsubscribeAttemptState`, `CampaignStatus`, `SuppressionReason` in `core/cleanup/domain/`

`OrderedEnum` for state machines (`UnsubscribeAttemptState`, `CampaignStatus`); `IdentifiedEnum` for identity sets (`UnsubscribeMethod`, `SuppressionReason`). `fromId` throws `NoSuchElementException` on unknown id.

### Privacy logging format (CONVENTIONS §5)

**Source:** `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java` line 49-51
**Apply to:** ALL `core/cleanup/*` + `api/controllers/cleanup/*` + `worker/cleanup/*` + `worker/scheduling/*` log statements

```java
log.info("event=analytics_summary_requested tenantId={} window={}",
        tenantId, analyticsWindow.id());
```

**Cleanup-specific event vocabulary:**
- `event=cleanup_candidates_requested`, `event=cleanup_campaign_preview_requested`, `event=cleanup_campaign_executed`, `event=cleanup_campaign_step_started`, `event=cleanup_campaign_step_ok`, `event=cleanup_campaign_step_failed`, `event=cleanup_campaign_completed`, `event=cleanup_campaign_undo_requested`, `event=cleanup_unsubscribe_http_post`, `event=cleanup_unsubscribe_mailto_sent`, `event=cleanup_throttle_deferred`, `event=processing_job_picked`, `event=processing_job_reaper_reaped`, `event=processing_job_purged`.
- Always include `tenantId={}` (UUID). Never include full `senderEmail` — use `senderDomain={}` only (CONVENTIONS §5 + SPEC UNS-09).

### Direct calls vs Spring Modulith events (CONVENTIONS §6)

**Source:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java` (direct service call from API)
**Apply to:** All `core/cleanup/usecases/*` — Phase 8 uses **direct service calls only** (per RESEARCH ASSUMPTION 4 — defer event publish). API controller → `CampaignExecuteService` direct; worker → `UnsubscribeCampaignHandler` direct. No Spring events between API and worker (must use `processing_job` outbox per CONVENTIONS §6 cross-process rule).

### Frontend feature folder (CONVENTIONS §8)

**Source:** `apps/web/features/notifications/` + `apps/web/features/analytics/` (folder structure)
**Apply to:** `apps/web/features/cleanup/unsubscribe-campaign/*` + `apps/web/features/cleanup/suppression/*`

```
features/cleanup/unsubscribe-campaign/
  api/unsubscribe-campaign-api.ts
  query-keys.ts
  hooks/{useCandidates,usePreviewCampaign,useExecuteCampaign,useCampaignStatus,useRetrySender,useUndoCampaign}.ts
  components/...
  messages.ts
features/cleanup/suppression/
  api/suppression-api.ts
  query-keys.ts
  hooks/{useSuppressionList,useAddSuppression,useRemoveSuppression}.ts
  components/...
  messages.ts
```

No barrel files. Direct imports.

### i18n per-feature source (CONVENTIONS §10)

**Source:** `apps/web/features/notifications/messages.ts`
**Apply to:** `features/cleanup/unsubscribe-campaign/messages.ts` + `features/cleanup/suppression/messages.ts`

Per CONVENTIONS §10: edit ONLY `messages.ts` (per-feature source); `i18n/messages/{vi,en}.json` is generated by `pnpm i18n:build` and must not be edited directly. `nav.cleanup` key goes in existing `features/shell/messages.ts`.

### shadcn primitive selection (CONVENTIONS §7 + `apps/web/AGENTS.md`)

**Source:** `apps/web/components.json` + UI-SPEC §"Registry Safety"
**Apply to:** All `features/cleanup/*/components/*.tsx`

All 28 primitives listed in UI-SPEC §"Registry Safety" already exist in `apps/web/components/ui/`. **No new `pnpm dlx shadcn@latest add ...` per UI-SPEC.**

---

## No Analog Found

Files với no close codebase analog (planner uses RESEARCH.md sections + UI-SPEC):

| File | Role | Data Flow | Reason | Where to get pattern |
|------|------|-----------|--------|----------------------|
| `core/cleanup/usecases/UnsubscribeHttpClient.java` | HTTP boundary | request-response | First `RestClient` use in `core.cleanup`; existing `RestClientConfig` is a global bean (BYOK), not a single-file boundary. | RESEARCH §"Spring RestClient Configuration cho UnsubscribeHttpClient" Approach A (pseudocode complete). |
| `core/cleanup/usecases/UnsubscribeMailtoUriParser.java` | RFC 6068 parser | transform | Java built-in `URI` — no project-level mailto parser. | RESEARCH §"RFC 6068 — `mailto:` URI Parsing" (pseudocode with `URI.create(...).getRawSchemeSpecificPart()` + split at `?`). |
| `core/cleanup/usecases/UnsubscribeDomainThrottle.java` | Redis INCR + EXPIRE rate-limit | rate-limit | No existing throttle pattern; project uses Redis only for sessions + locks. | RESEARCH §"Redis Throttle Bucket Pattern" (Lua script + Lettuce `StringRedisTemplate`). |
| `worker/cleanup/ProcessingJobWorker.java` | continuous-poll virtual-thread worker | row-locked pickup | Project has only `@Scheduled` batches — no SKIP LOCKED + continuous poll worker yet. | RESEARCH §"Worker poll loop pseudocode" (lines 499-562). |
| `apps/web/features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus.ts` | TanStack `refetchInterval` conditional | polling | No existing polling hook (project has long-lived queries only). | UI-SPEC §"CampaignStatusPage" snippet (lock-step with D-15). |
| `apps/web/app/(protected)/(app)/cleanup/page.tsx` | redirect Server Component | redirect | Pattern is one-liner Next.js stdlib — no precedent. | UI-SPEC §"Routing rules": `redirect('/cleanup/unsubscribe-campaign')`. |

---

## Pattern Density / Cross-References Cheat-Sheet (planner-actionable)

| Concern | Single source of truth |
|---------|-----------------------|
| Liquibase changelog `id:` numbering | RESEARCH §"Database Schema Migrations" — Phase 8 starts at **041** (CONTEXT D-09 wrong about 039) |
| `processing_job` table DDL | RESEARCH §"Liquibase DDL sketch (041-processing-job.yaml)" |
| `sender_suppression` table DDL | RESEARCH §"Changelog 042 — sender_suppression" |
| `unsubscribe_campaign` + `unsubscribe_attempt` DDL | RESEARCH §"Changelog 043 / 044" (in-progress — planner verifies in `043-unsubscribe-campaign.yaml` section continues past line 900 of RESEARCH) |
| Worker poll loop SQL + virtual-thread structure | RESEARCH §"Worker poll loop pseudocode" |
| Reaper batch `@Scheduled` SQL | RESEARCH §"Reaper batch" |
| RestClient configuration | RESEARCH §"Spring RestClient Configuration cho UnsubscribeHttpClient" Approach A |
| HTTP error → failureReason mapping | RESEARCH §"Error-mapping summary table" |
| Mailto URI parsing | RESEARCH §"RFC 6068 — mailto: URI Parsing" |
| Redis throttle keys + Lua script | RESEARCH §"Redis Throttle Bucket Pattern" |
| Modulith `package-info.java` | RESEARCH §"Spring Modulith Module Declaration cho `core.cleanup`" |
| ArchUnit rules | RESEARCH §"ArchUnit Rules cho `core.cleanup.*`" (5 rules) |
| Frontend feature folder layout | UI-SPEC §"File structure (locked CONTEXT D-13)" |
| Risk badge palette | UI-SPEC §"Risk badge palette (LOCKED in CONTEXT D-15 mapping)" |
| i18n key list (full) | UI-SPEC §"Copywriting Contract" — ~75 keys |
| Playwright golden path | UI-SPEC §"Playwright e2e (UNS-05 golden path)" — 9 steps |
| Status polling cadence | UI-SPEC §"CampaignStatusPage" + CONTEXT D-15 |
| Sidebar nav extension (hướng A) | UI-SPEC §"Sidebar nav update" — recommend `children: NavItem[]` extension |
| `processing_job` payload schema | CONTEXT D-19 — `{"campaignId": "uuid", "schemaVersion": 1}` |
| Throttle key format | CONTEXT D-20 — `throttle:unsubscribe:domain:{tenantId}:{domain}:{60s\|1h}` |
| Reaper + purge placement | CONTEXT D-18 + D-25 — `worker/scheduling/` (generic, not `worker/cleanup/`) |
| Purge retention | CONTEXT D-25 — 90 ngày `processing_job` only; audit tables forever |

---

## Metadata

**Analog search scope:**
- `backend/core/src/main/java/com/zeromail/core/{triage,analytics,gmail,onboarding,notification,shared}/**`
- `backend/core/src/main/resources/db/changelog/changes/*.yaml` (40 files)
- `backend/api/src/main/java/com/zeromail/api/controllers/**` + `api/dto/**`
- `backend/core/src/test/java/com/zeromail/core/{arch,triage}/**` (privacy sweep + ArchUnit)
- `backend/worker/src/main/java/com/zeromail/worker/{triage,notification,billing,scheduling}/**`
- `apps/web/features/{analytics,notifications,gmail,triage}/**`
- `apps/web/app/(protected)/(app)/{analytics,settings,billing,needs-reply}/**`
- `apps/web/e2e/{analytics,needs-reply,rules}.spec.ts`
- `apps/web/components/shell/AppSidebar.tsx`

**Files scanned:** 58 (representative of full search space; stopped at 3–5 strong matches per planned file).

**Pattern extraction date:** 2026-05-19

**Read-only constraint honored:** No source files modified. Only `08-PATTERNS.md` written.

---

## PATTERN MAPPING COMPLETE
