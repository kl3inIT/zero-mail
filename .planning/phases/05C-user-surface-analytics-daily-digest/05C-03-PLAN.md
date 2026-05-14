---
phase: 05C
plan: 03
type: execute
wave: 3
depends_on:
  - 05C-01
  - 05C-02
files_modified:
  - gradle/libs.versions.toml
  - backend/worker/build.gradle.kts
  - backend/worker/src/main/resources/application.yml
  - backend/worker/src/main/resources/i18n/digest_vi.properties
  - backend/worker/src/main/resources/i18n/digest_en.properties
  - backend/worker/src/main/resources/email-templates/digest/digest.html.thymeleaf
  - backend/worker/src/main/resources/email-templates/digest/digest.txt.thymeleaf
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestTotals.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestTopSender.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestRuleHit.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationChannel.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DispatchOutcome.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestClaimRecord.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java
  - backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java
  - backend/api/src/main/java/com/zeromail/api/dto/notifications/NotificationPreferencesResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/notifications/NotificationPreferencesUpdateRequest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/notifications/NotificationPreferencesControllerTest.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/email/ResendEmailGateway.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/email/ThymeleafDigestRenderer.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/config/DigestRendererConfig.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/config/NotificationProperties.java
  - backend/worker/src/test/java/com/zeromail/worker/arch/ResendBoundaryArchTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/DigestPayloadShapeArchTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestComposerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestIdempotencyTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/ThymeleafDigestRendererTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestMessageSourceParityTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/email/EmailNotificationChannelTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestPrivacySweepTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestPendingReaperJobTest.java
  - backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchWithNoopChannelTest.java
autonomous: true
requirements:
  - ANL-03
  - WEB-02
threat_refs:
  - T-05C-08
  - T-05C-09
  - T-05C-10
  - T-05C-11
  - T-05C-12
user_setup:
  - service: resend
    why: "Transactional email vendor for daily digest dispatch (D-01)"
    env_vars:
      - name: RESEND_API_KEY
        source: "Resend Dashboard → API Keys (https://resend.com/api-keys)"
    dashboard_config:
      - task: "Create account at resend.com and verify a sending domain (e.g., zero-mail.app)"
        location: "Resend Dashboard → Domains"
      - task: "Set the `from` address (e.g., notifications@zero-mail.app) on the verified domain"
        location: "Resend Dashboard → Domains → DNS records"
must_haves:
  truths:
    - "Worker scheduler claims a digest_delivery PENDING row for every (tenant, day) where digest_enabled=true AND the tenant's local hour matches digest_send_hour_local at the current tick (exact-hour match per D-06; missed hours are NOT recovered — see D-07 lock below)"
    - "Each per-tenant dispatch is composed of THREE separate `@Transactional(REQUIRES_NEW)` write units on `DigestDeliveryService` — `claimPending`, `markSent`, `markFailed` — and NOTHING else is transactional: `scheduledDispatch()` is NOT @Transactional and `dispatchOne(...)` is NOT @Transactional. Tenant N's Resend failure cannot roll back tenants 1..N-1's claim INSERTs / SENT UPDATEs because each FSM write already committed in its own physical transaction — REVIEW FIX (Codex C6 + OpenCode H2 + Codex Cycle-2 HIGH-A)"
    - "`dispatchOne(...)` is plain (NO `@Transactional` of any kind). The Resend HTTP call executes outside any DB transaction so a Postgres connection is NEVER held open across the ~1–3s network round-trip — REVIEW FIX (Codex Cycle-2 HIGH-A — transaction-boundary wording lock)"
    - "Digest content window anchors to the CONFIGURED send-hour boundary (`HH:00`), NOT the cron execution instant (`HH:05`). `sendMoment = digestDayLocal.atTime(tenant.sendHourLocal(), 0).atZone(tenantZone).toInstant()` so the analytics window passed to `DigestComposer` is `[yesterday HH:00, today HH:00)` as SPEC mandates, even though the cron ticks 5 minutes after the hour — REVIEW FIX (Codex Cycle-2 HIGH-B — digest window anchor)"
    - "Each per-tenant dispatch is wrapped in `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(...)` so JPA @TenantId listeners on DigestDeliveryEntity see the right tenant — REVIEW FIX (Codex C7 + OpenCode M2, S3)"
    - "Due-tenant SQL and the Java digest_day_local computation share a SINGLE reference Instant (passed in as a parameter from the scheduler tick) — Postgres now() drift vs Java currentInstant cannot disagree near hour boundaries — REVIEW FIX (Codex C8)"
    - "D-07 LOCK: missed-hour recovery is INTENTIONALLY OFF in v1 — if the worker is down through a tenant's exact send-hour tick, that tenant's digest for that day is skipped with no catch-up. Documented in CONTEXT D-07 update — REVIEW FIX (OpenCode H1)"
    - "Idempotency write order: (1) `digestDeliveryService.claimPending` opens REQUIRES_NEW, INSERTs PENDING, commits; (2) `notificationChannel.dispatch(payload, address)` calls Resend OUTSIDE any DB transaction (no surrounding @Transactional on `dispatchOne` or `scheduledDispatch`); (3) `digestDeliveryService.markSent` / `markFailed` opens its OWN REQUIRES_NEW, writes, commits. The three FSM mutators are the ONLY @Transactional surface for the dispatch path — REVIEW FIX (Codex C6 split + Codex Cycle-2 HIGH-A)"
    - "claimPending returns a DigestClaimRecord(deliveryId, tenantId, digestDayLocal, attemptCount, channel) — NOT a boolean — so downstream markSent(deliveryId, ...) has the row id available — REVIEW FIX (Codex MEDIUM claimPending shape)"
    - "Tenant locale comes from `users.preferred_language` (joined per tenant), NOT a non-existent `tenants.preferred_language` column — REVIEW FIX (Codex MEDIUM locale source)"
    - "Running the dispatcher twice for the same (tenant, day) produces exactly one outbound Resend send call"
    - "Tenant with digest_enabled=false receives no dispatch regardless of activity"
    - "Zero-activity tenant with digest_enabled=true receives the digest with explicit 'no activity yesterday' wording"
    - "Resend SDK Idempotency-Key header carries tenantId:digestDayLocal — verified by mock"
    - "Resend SDK imports appear in exactly ONE package (worker.notification.email) — ArchUnit boundary runs from a root/arch test source set that scans ALL modules (NOT scoped to a single sub-project) so the boundary check is non-vacuous on the actual SDK import site in backend/worker — REVIEW FIX (Codex MEDIUM ArchUnit module visibility)"
    - "DigestPayload record has no email-specific fields (no htmlBody, mimeType, subject)"
    - "DigestComposer null-email guard: if `userRepository.findEmailByTenantId(tenantId)` returns null OR empty, EmailNotificationChannel returns PermanentFailure('no_email_found') and the row is markFailed — NO NPE, NO malformed Resend call — REVIEW FIX (OpenCode M4 / S6)"
    - "CTA + opt-out URLs use `URI.create(baseAppUrl).resolve(\"analytics?source=digest&window=7d\")` form — trailing slash on baseAppUrl does NOT produce `//analytics` — REVIEW FIX (OpenCode L4)"
    - "Resend SDK version is verified for Spring Boot 4 / Java 25 compatibility via Context7 before commit — REVIEW FIX (OpenCode S8 / Codex LOW Resend version)"
    - "core.notification Modulith allowedDependencies INCLUDES `\"analytics\"` so DigestComposer can import AnalyticsSummaryQueryService without breaking the modulith-verification ArchUnit test — REVIEW FIX (OpenCode M1 / S4)"
    - "GET /api/me/notifications + PATCH /api/me/notifications scope by TenantContext.currentOrThrow() AT THE CONTROLLER ONLY and pass tenantId as an explicit parameter to NotificationPreferenceService"
    - "No sender_email, no email body, no `to:` address appears in worker logs during a dispatch run"
    - "Stuck-PENDING rows past PT30M get promoted to FAILED by the reaper"
  artifacts:
    - path: "backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java"
      provides: "@Scheduled(cron) + @SchedulerLock per-tick fanout"
    - path: "backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java"
      provides: "NotificationChannel implementation backed by Thymeleaf + Resend"
    - path: "backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java"
      provides: "Channel-free digest content record"
      contains: "record DigestPayload"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java"
      provides: "GET + PATCH /api/me/notifications"
    - path: "backend/worker/src/main/resources/email-templates/digest/digest.html.thymeleaf"
      provides: "HTML digest template, locale-driven via MessageSource"
  key_links:
    - from: "DigestDispatchScheduler"
      to: "DigestDeliveryService.claimPending"
      via: "UNIQUE-constraint-as-dedupe (D-09); claimPending now returns DigestClaimRecord (not boolean) — Codex MEDIUM"
      pattern: "claimPending.*DigestClaimRecord|DigestClaimRecord.*claimPending"
    - from: "DigestDispatchScheduler"
      to: "ScopedValue.where(TenantContext.TENANT, ...)"
      via: "per-tenant tenant-context binding — REVIEW FIX Codex C7 / OpenCode M2"
      pattern: "ScopedValue\\.where.*TENANT|TenantContext\\.TENANT"
    - from: "DigestDeliveryService.{claimPending, markSent, markFailed}"
      to: "@Transactional(propagation = Propagation.REQUIRES_NEW)"
      via: "FSM mutators each open their OWN REQUIRES_NEW transaction; dispatchOne is NOT @Transactional — Resend HTTP runs outside any DB tx — REVIEW FIX Codex C6 / OpenCode H2 / Codex Cycle-2 HIGH-A"
      pattern: "REQUIRES_NEW"
    - from: "EmailNotificationChannel"
      to: "Resend SDK"
      via: "ResendEmailGateway with addHeader(\"Idempotency-Key\", ...)"
      pattern: "Idempotency-Key"
    - from: "DigestComposer"
      to: "AnalyticsSummaryQueryService.summarize"
      via: "TimeWindow.between(sendMoment.minus(Duration.ofHours(24)), sendMoment) — closed [sendMoment-24h, sendMoment) per Codex C5"
      pattern: "summarize.*TimeWindow"
---

<objective>
Ship the channel-agnostic daily-digest pipeline + the preferences API: `DigestPayload` record + `NotificationChannel` interface (D-04) + `DigestComposer` (reuses `AnalyticsSummaryQueryService` from Plan 02 with a 24h window per D-19); `EmailNotificationChannel` + `ResendEmailGateway` + `ThymeleafDigestRenderer` (D-01 D-02 D-03) all confined to `backend/worker/notification/email/` so the ArchUnit `ResendBoundaryArchTest` passes; `DigestDispatchScheduler` + `DigestPendingReaperJob` with ShedLock 7.7.0 (D-05 D-06 D-07 D-11); `digest_delivery` FSM service (`claimPending` → `markSent` / `markFailed`) using the UNIQUE-constraint-as-dedupe pattern (D-09 D-10); `NotificationPreferencesController` + `GET`/`PATCH /api/me/notifications` endpoints calling `NotificationPreferenceService` from Plan 01; HTML + plaintext Thymeleaf templates with single-template-locale-driven render via Spring `MessageSource` (D-02) and matching i18n properties files for vi + en covering every subject/preheader/header/CTA/footer key per UI-SPEC §C.

Purpose: closes ANL-03 + the backend half of WEB-02 (Notifications subsection). Plan 04 (frontend) consumes the new REST endpoints after regenerating the OpenAPI typed client.

Output: 1 new Gradle dependency (Resend Java SDK 4.13.0), 1 worker config flag (`spring.main.keep-alive: true` per RESEARCH §3), 7 worker source files (config + scheduler + reaper + channel + gateway + renderer + properties), 4 core source files (DigestPayload + supporting records + NotificationChannel + DigestComposer + DigestDeliveryService), 3 api source files (controller + 2 DTOs), 2 i18n properties files, 2 Thymeleaf templates, 10 backend test files (covering every Wave 0 entry from VALIDATION.md §"Backend (Java) — backend/worker" + the 2 ArchUnit boundary tests under core).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-UI-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-VALIDATION.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-01-PLAN.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-02-PLAN.md
@CLAUDE.md
@CONVENTIONS.md

<!-- Templates the executor MUST read before editing -->
@backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
@backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java
@backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
@backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
@backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java
@backend/worker/src/main/resources/application.yml
@gradle/libs.versions.toml
</context>

<interfaces>
<!-- Critical contracts the executor needs without re-exploring -->

`@Scheduled(cron = "0 5 * * * *")` is the Boot 4 six-field form (`sec min hour day month dow`); runs at minute 5 every hour. Existing 5 workers use `fixedRate=3_600_000L` — this plan introduces cron (per D-05 + RESEARCH §3 recommendation). Existing schedulers (and their lock names, do NOT collide): `billingIntentExpirySweeper` (60min fixedRate), `triagePendingReaper` (5min fixedDelay), `triageEventRetryJob`, `triageEventCleanupJob`, `triageAuditPurgeJob`. The new `digestDispatchScheduler` cron `0 5 * * * *` (hh:05) does not collide.

`@SchedulerLock(name = "digestDispatchScheduler", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")` — `PT20M` exceeds worst-case dispatch duration at 5 req/s × ~1000 tenants (17min).

`LockAssert.assertLocked()` MUST be the first line inside the `@Transactional` `dispatch()` method — catches AOP misconfiguration immediately.

Resend SDK `com.resend:resend-java` — **REVIEW FIX (OpenCode S8 + Codex LOW)**: BEFORE pinning the version, run Context7 to fetch the current stable release notes: `mcp__context7__resolve-library-id "resend java sdk"` then `query-docs` "Resend Java SDK 4.x latest stable Spring Boot 4 compatibility Java 25 bytecode". Pin to whatever is the current stable release at planning time (4.13.0 is a known floor — newer is acceptable if compatible). Verify (a) no `javax.*` transitive deps (Boot 4 is Jakarta-only — load-bearing per CLAUDE.md "do not use" list), (b) no Jackson 2.x transitive (Boot 4 ships Jackson 3.x — verify via `./gradlew :backend:worker:dependencyInsight --dependency jackson-core` after adding), (c) the version's `CreateEmailOptions` builder has `.addHeader(...)` and `.addTag(...)` for Idempotency-Key + tag category. Document the chosen version + Context7 evidence in `05C-03-SUMMARY.md`. Add to `libs.versions.toml`:
```
[versions]
resend = "<verified stable>"      # e.g. "4.13.0" or newer per Context7
[libraries]
resend-java = { module = "com.resend:resend-java", version.ref = "resend" }
```
Then in `backend/worker/build.gradle.kts`: `implementation(libs.resend.java)` + `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")`.

Resend send signature: `new Resend(apiKey).emails().send(CreateEmailOptions.builder().from(...).to(...).subject(...).html(...).text(...).addHeader("Idempotency-Key", tenantId + ":" + digestDayLocal).build())` returns `CreateEmailResponse` (has `.getId()`). Throws `ResendException` with `.getStatusCode()`. 4xx (`400`/`401`/`403`/`422`/`404`) → permanent FAILED, no retry. `429`/`5xx`/network → transient FAILED, reaper-or-next-tick retry. Resend's own Idempotency-Key TTL is 24h, max 256 chars; `tenantId(36) + ":" + digestDayLocal(10) = 47 chars` is safely under.

DigestDispatchScheduler claim query (D-06 Postgres-side hour match) — REVIEW FIXES (Codex C2 uppercase EMAIL + Codex C8 single reference instant + Codex MEDIUM locale via users join):
```
SELECT np.tenant_id, t.time_zone, np.digest_send_hour_local, u.preferred_language
FROM notification_preference np
JOIN tenants t ON t.id = np.tenant_id
JOIN users u ON u.tenant_id = t.id                        -- Codex MEDIUM: locale lives on users, not tenants
WHERE np.digest_enabled = true
  AND np.channel = 'EMAIL'                                -- Codex C2: uppercase matches JPA @Enumerated(STRING) storage
  AND EXTRACT(HOUR FROM (?::timestamptz AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local
  --                       ^^^^^^^^^^^^^ JDBC param 1 = referenceInstant (single source of truth — Codex C8)
```

The `referenceInstant` is captured ONCE at the top of `scheduledDispatch()` as `Instant referenceInstant = currentInstant.get();` (where `currentInstant` is the injected `Supplier<Instant>` test-stubbable bean — default `Instant::now`). It is passed BOTH to the JDBC claim query (above) AND to the Java side `referenceInstant.atZone(ZoneId.of(tenant.timeZone)).toLocalDate()` for `digestDayLocal`. The DB no longer uses `now()`. This guarantees the "hour match" and the "digest day" derive from the same moment, eliminating the Postgres-clock vs JVM-clock drift Codex flagged in C8.

`digest_day_local` MUST be the tenant-local DATE on the send moment (per RESEARCH §4 + Open Question 5 — so any same-day tick collides regardless of which hour fires). Compute via `referenceInstant.atZone(ZoneId.of(tenant.timeZone)).toLocalDate()` at the moment of insert — same `referenceInstant` as the SQL above.

`DigestComposer` reuses `AnalyticsSummaryQueryService.summarize(tenantId, TimeWindow.between(sendMoment.minus(Duration.ofHours(24)), sendMoment))` — closed `[sendMoment-24h, sendMoment)` interval per Plan 02's Codex C5 signature change. **REVIEW FIX (Codex Cycle-2 HIGH-B — send-hour anchor):** `sendMoment` is the per-tenant local-anchored Instant computed via `digestDayLocal.atTime(tenant.sendHourLocal(), 0).atZone(tenantZone).toInstant()` — i.e. the configured send-hour boundary (`HH:00`), NOT raw `referenceInstant` (which is `HH:05` because the cron fires at `:05`). This guarantees the analytics window aligns with the SPEC's `[yesterday HH:00, today HH:00)` contract even though the cron tick happens 5 minutes after the hour.

Spring Boot 4 + virtual threads in worker: verify `spring.main.keep-alive: true` is set in `backend/worker/src/main/resources/application.yml` — RESEARCH §3 + Pitfall 2 flagged this. If absent, ADD it in this plan.

i18n MessageSource for digest: separate from web. Bind via `spring.messages.basename: i18n/digest` in `backend/worker/.../application.yml` (or a dedicated `ReloadableResourceBundleMessageSource` bean if the worker already has a different basename). Files: `backend/worker/src/main/resources/i18n/digest_vi.properties` + `digest_en.properties`. Keys: per UI-SPEC §C subject, preheader, header.greeting, totals.{messages,timeSaved}, cta, topSenders.eyebrow, topRules.eyebrow, footer.{optOutPrompt,optOutLink,brand,legal}, zeroBody (and the .normal / .zero variants). Failing-loud on missing key: set `messageSource.setUseCodeAsDefaultMessage(false)` so the renderer fails-loud — `DigestMessageSourceParityTest` enforces.

`ResendBoundaryArchTest` — **REVIEW FIX (Codex MEDIUM "ResendBoundaryArchTest may pass vacuously")**: place this test in the worker module (`backend/worker/src/test/java/com/zeromail/worker/arch/ResendBoundaryArchTest.java`) — NOT in `backend/core` — because the Resend SDK import physically lives in `backend/worker/.../notification/email/ResendEmailGateway.java` and ArchUnit's `ClassFileImporter().importPackages(...)` only sees classes on the test module's classpath. Placing the test in `core` would make it vacuously pass (core never imports Resend regardless). The test imports the worker's notification packages and asserts `noClasses().that().resideOutsideOfPackage("..worker.notification.email..").should().dependOnClassesThat().resideInAnyPackage("com.resend..")`. Because-string: `NTF-01: Resend SDK imports MUST be confined to backend/worker/.../notification/email. EmailNotificationChannel is the single adapter; DigestPayload + DigestComposer + NotificationChannel are provider-free. NO EXEMPTION.` Mirrors `LlmGatewayBoundaryTest` shape, but placed in the module that actually owns the SDK import.

`DigestPayloadShapeArchTest` STAYS in `backend/core/src/test/java/.../arch/` because the `DigestPayload` record itself lives in `core.notification.domain` — that's the right scope to assert the record's shape.

`DigestPayloadShapeArchTest`: ArchUnit `fields()` DSL — `fields().that().areDeclaredIn(DigestPayload.class).should().notHaveName("htmlBody").andShould().notHaveName("mimeType").andShould().notHaveName("subject").check(importedClasses)` + because-string `D-04: DigestPayload is channel-free; email-specific fields MUST live inside EmailNotificationChannel only.`

Privacy logging: every log line uses `event=<name> tenantId={} digestDay={}` only. NO `to:` address, NO body, NO sender_email. `DigestPrivacySweepTest` mirrors `TriagePrivacySweepTest` with `ListAppender` + `SensitiveMarkerScrubFilter`; sentinels include `digest-sentinel-to-address@example.com`, `digest-body-sentinel-05C`, `sender-sentinel-05C@example.com`.

Endpoint shapes for `/api/me/notifications`:
- `GET /api/me/notifications` → 200 `NotificationPreferencesResponse(channel: "email", digestEnabled: bool, digestSendHourLocal: int, timeZone: String)` for the current tenant + email channel
- `PATCH /api/me/notifications` body `NotificationPreferencesUpdateRequest(digestEnabled: bool, digestSendHourLocal: int)` with `@Min(0) @Max(23)` on the hour → 200 with updated `NotificationPreferencesResponse`, OR 400 with validation error
- Both require authenticated session; tenant id from `TenantContext.currentOrThrow()`

`time_zone` is NOT editable in v1 per D-14 + UI-SPEC — the PATCH does NOT accept it, GET returns it for display only.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Channel abstraction + DigestComposer + DigestDeliveryService + ArchUnit boundary tests + NotificationPreferencesController</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestTotals.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestTopSender.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestRuleHit.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationChannel.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java,
    backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java,
    backend/api/src/main/java/com/zeromail/api/dto/notifications/NotificationPreferencesResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/notifications/NotificationPreferencesUpdateRequest.java,
    backend/core/src/test/java/com/zeromail/core/arch/ResendBoundaryArchTest.java,
    backend/core/src/test/java/com/zeromail/core/arch/DigestPayloadShapeArchTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestComposerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/notifications/NotificationPreferencesControllerTest.java
  </files>
  <read_first>
    backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java,
    backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java,
    backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (D-04 D-09 D-10 D-12 D-19 D-22 D-25),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§"Pattern 3 UNIQUE-constraint-as-dedupe", §8 ArchUnit, §12 #4)
  </read_first>
  <behavior>
    - `DigestPayload` record: `Locale locale, UUID tenantId, LocalDate digestDayLocal, DigestTotals totals, List<DigestTopSender> topSenders, List<DigestRuleHit> topRules, URI ctaUrl, URI optOutUrl, boolean zeroActivity` — channel-free (NO htmlBody / mimeType / subject / `to:` address)
    - `DigestTotals(long volumeObserved, long volumeApplied, long timeSavedSeconds)`
    - `DigestTopSender(String senderEmail, long count)` + `DigestRuleHit(String ruleName, long applied, long reverted)`
    - `DispatchOutcome` (extracted into its own file per cleanup) — sealed interface `permits Success, TransientFailure, PermanentFailure` where `Success(String externalId)` carries Resend's `email_id`, `TransientFailure(String reason)` indicates 429/5xx/network (retryable per reaper schedule), `PermanentFailure(String reason)` indicates 4xx / no-email-found (NOT retryable). Plan 03 v1 currently does NOT retry transient failures automatically — the row is marked FAILED and a reaper may flip it back if desired in a future iteration. Adding `next_attempt_at` to `digest_delivery` (Plan 01) leaves the schema option open.
    - `NotificationChannel` interface: single method `DispatchOutcome dispatch(DigestPayload payload, String recipientAddress)` — REVIEW FIX (OpenCode M4): the recipient address is passed as a separate parameter (NOT inside DigestPayload, keeping payload channel-free per D-04). Caller (scheduler) is responsible for resolving the address via `userRepository.findEmailByTenantId(tenantId)` and short-circuiting with `PermanentFailure("no_email_found")` BEFORE invoking dispatch when the address is null or blank.
    - `DigestClaimRecord` record (NEW, REVIEW FIX Codex MEDIUM): `record DigestClaimRecord(UUID deliveryId, UUID tenantId, LocalDate digestDayLocal, int attemptCount, ChannelType channel)` — returned by `claimPending` (replaces the previous `boolean` return). Carries everything downstream `markSent` / `markFailed` need without re-querying.
    - `DigestComposer` `@Service`: `compose(UUID tenantId, ZoneId tenantZone, Locale tenantLocale, LocalDate digestDayLocal, Instant sendMoment, URI baseAppUrl) → DigestPayload` — calls `analyticsSummaryQueryService.summarize(tenantId, TimeWindow.between(sendMoment.minus(Duration.ofHours(24)), sendMoment))` (Codex C5 closed-window shape). **REVIEW FIX (OpenCode L4)** — builds URIs via `URI.create(baseAppUrl.toString()).resolve("analytics?source=digest&window=7d")` (NOT string concatenation) so `baseAppUrl` ending in `/` doesn't produce `//analytics`. Specifically: normalize `baseAppUrl` to end with `/` if it doesn't, then `URI.resolve("analytics?...")` strips the leading slash naturally — write a small helper `buildAppUrl(URI base, String relativePath)` in the composer. Sets `zeroActivity = (volumeObserved == 0 && volumeApplied == 0)`. Returns `DigestPayload`.
    - `DigestDeliveryService` `@Service`: methods now split across two transactional boundaries (REVIEW FIX Codex C6 / OpenCode H2 / S1):
      - `@Transactional(propagation = Propagation.REQUIRES_NEW) DigestClaimRecord claimPending(UUID tenantId, LocalDate digestDayLocal) throws DigestAlreadyClaimedException` — INSERT a `PENDING` row; on UNIQUE-violation (`DataIntegrityViolationException` SQLState 23505) throw `DigestAlreadyClaimedException` (or return `null` — pick one; sealed result type preferred for clarity). On success, returns the populated `DigestClaimRecord`. This transaction COMMITS before the Resend call so the row is durable.
      - `@Transactional(propagation = Propagation.REQUIRES_NEW) void markSent(UUID deliveryId, ChannelType channel, String externalRef)` — UPDATE `status='SENT', dispatched_at=now(), channel=?, external_ref=?` for the given `deliveryId`. Separate transaction.
      - `@Transactional(propagation = Propagation.REQUIRES_NEW) void markFailed(UUID deliveryId, String failureReason)` — UPDATE `status='FAILED', failure_reason=?`. Separate transaction.
      - `@Transactional(readOnly = true) List<DigestDeliveryEntity> findStuckPending(Duration gracePeriod)` — for the reaper.
      - `@Transactional(propagation = Propagation.REQUIRES_NEW) void deleteForTenant(UUID tenantId)` — for account-deletion cascade (Plan 01 D-16).
    - `NotificationPreferencesController`: `GET /api/me/notifications` returns email-channel pref for current tenant; `PATCH /api/me/notifications` validates `digestSendHourLocal` via `@Min(0) @Max(23)` then calls `notificationPreferenceService.updatePreference(tenantId, EMAIL, request.digestEnabled(), request.digestSendHourLocal())`; both endpoints require authenticated session
    - `ResendBoundaryArchTest`: copies `LlmGatewayBoundaryTest` shape — `noClasses().that().resideOutsideOfPackage("..worker.notification.email..").should().dependOnClassesThat().resideInAnyPackage("com.resend..")`; before Plan 02's Resend gateway lands the test passes vacuously, after Task 2 lands it still passes (only `EmailNotificationChannel` + `ResendEmailGateway` import Resend)
    - `DigestPayloadShapeArchTest`: fields-DSL banning `htmlBody`, `mimeType`, `subject`, `to`, `toAddress`, `htmlContent`, `bodyHtml`
    - `DigestComposerTest`: seeded `AnalyticsSummaryQueryService` mock — case (a) non-zero counts → zeroActivity=false, totals populated; case (b) zero counts → zeroActivity=true, topSenders + topRules are empty lists not null; case (c) ctaUrl ends with `?source=digest&window=7d`, optOutUrl with `?section=notifications&source=digest`
    - `NotificationPreferencesControllerTest`: `@WebMvcTest` + MockMvc — (a) GET unauthenticated → 401, (b) GET authenticated → 200 + JSON with channel/digestEnabled/digestSendHourLocal/timeZone, (c) PATCH with `digestSendHourLocal=24` → 400, (d) PATCH with valid body → 200 + persisted state, (e) PATCH from tenant A cannot modify tenant B's row (verified by mock-call assertion on TenantContext)
  </behavior>
  <action>Create `core.notification.domain` + `core.notification.usecases` Java files. **REVIEW FIX (OpenCode M1 / S4)**: in `core.notification/package-info.java` declare `@ApplicationModule(displayName="Notification", allowedDependencies={"analytics", "account", "tenant", "shared.persistence", "shared.lang"})` — including `"analytics"` is load-bearing because `DigestComposer` calls `AnalyticsSummaryQueryService.summarize(...)`; without this Spring Modulith verification will fail. `DigestPayload` is a Java record — NO Jackson annotations (Spring MVC default record serialization is enough per RESEARCH Pitfall 1). The boolean `zeroActivity` field is the load-bearing branch for the renderer (UI-SPEC §C "Zero-activity body"). `NotificationChannel.dispatch(payload, recipientAddress)` takes the address as a second parameter (NOT inside payload) per OpenCode M4. `DigestDeliveryService.claimPending` returns `DigestClaimRecord` (NOT boolean) per Codex MEDIUM — uses the exception-catch idiom (matches Phase 02B billing precedent — read `BillingTopupIntent*Service` for the SQLState 23505 catch pattern). All three FSM methods (`claimPending`, `markSent`, `markFailed`) MUST be `@Transactional(propagation = Propagation.REQUIRES_NEW)` so the scheduler can compose them as three separate transactional units per tenant (Codex C6 split). `NotificationPreferencesController` mirrors `TriageAuditController` for the auth + TenantContext extraction + thin-controller shape (Convention 1); controller extracts `tenantId = TenantContext.currentOrThrow()` and passes explicitly to the service (Plan 02 M3/S5 alignment — service NEVER reads TenantContext internally). The `time_zone` field is read-only in `NotificationPreferencesResponse` and NOT accepted by `PATCH`. `ResendBoundaryArchTest` is placed in `backend/worker/src/test/java/com/zeromail/worker/arch/` per Codex MEDIUM module-visibility fix; `DigestPayloadShapeArchTest` stays in `backend/core/src/test/java/.../arch/`. After Java edits run `mcp__jetbrains__get_file_problems` on touched files. Implements D-04 + D-09 + D-12 + WEB-02 analytics portion (preferences API) + REVIEWS Codex C6 / OpenCode H2 / S1 transactional split, OpenCode M1 / S4 Modulith dep, OpenCode M3 / S5 tenantId, OpenCode M4 / S6 recipient param, Codex MEDIUM claimPending shape + ArchUnit module location.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "ResendBoundaryArchTest" --tests "DigestPayloadShapeArchTest" :backend:worker:test --tests "DigestComposerTest" :backend:api:test --tests "NotificationPreferencesControllerTest" -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 4 named tests run green. `DigestPayload` record has none of the banned email-specific fields. `NotificationChannel` interface compiles. `NotificationPreferencesController` `GET` + `PATCH` work via WebMvcTest. Validation rejects out-of-range hour. ArchUnit `ResendBoundaryArchTest` passes vacuously (no Resend imports yet); will continue to pass after Task 2 lands the SDK in `worker/notification/email/`. `mcp__jetbrains__get_file_problems` 0 errors on touched files.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Resend Java SDK dep + Thymeleaf templates + i18n + EmailNotificationChannel + ResendEmailGateway + ThymeleafDigestRenderer + parity test + privacy sweep + renderer test + channel test</name>
  <files>
    gradle/libs.versions.toml,
    backend/worker/build.gradle.kts,
    backend/worker/src/main/resources/application.yml,
    backend/worker/src/main/resources/i18n/digest_vi.properties,
    backend/worker/src/main/resources/i18n/digest_en.properties,
    backend/worker/src/main/resources/email-templates/digest/digest.html.thymeleaf,
    backend/worker/src/main/resources/email-templates/digest/digest.txt.thymeleaf,
    backend/worker/src/main/java/com/zeromail/worker/notification/config/DigestRendererConfig.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/config/NotificationProperties.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/email/ResendEmailGateway.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/email/ThymeleafDigestRenderer.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/ThymeleafDigestRendererTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestMessageSourceParityTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/email/EmailNotificationChannelTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestPrivacySweepTest.java
  </files>
  <read_first>
    gradle/libs.versions.toml,
    backend/worker/build.gradle.kts,
    backend/worker/src/main/resources/application.yml,
    backend/worker/src/main/java/com/zeromail/worker/ZeroMailWorkerApplication.java,
    backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-UI-SPEC.md (§C Daily digest email — full content list and copywriting contract),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (D-01 D-02 D-03 D-25),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§1 Resend SDK, §2 Thymeleaf, §Pitfall-2 keep-alive)
  </read_first>
  <behavior>
    - `libs.versions.toml` gains `resend = "4.13.0"` under `[versions]` and `resend-java = { module = "com.resend:resend-java", version.ref = "resend" }` under `[libraries]`
    - `backend/worker/build.gradle.kts` gains `implementation(libs.resend.java)` and `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")`
    - `backend/worker/src/main/resources/application.yml` gains `spring.main.keep-alive: true` (verify absent before adding — per RESEARCH §3) AND `spring.messages.basename: i18n/digest` (or merge with existing `spring.messages.basename` if already present — read the file) AND `zero-mail.notification.email.resend.api-key: ${RESEND_API_KEY:?RESEND_API_KEY env var is required}` AND `zero-mail.notification.email.from-address: notifications@zero-mail.app` AND `zero-mail.notification.app-base-url: ${APP_BASE_URL:http://localhost:3000}`
    - `NotificationProperties` `@ConfigurationProperties("zero-mail.notification")` record nested under `ZeroMailCoreProperties` style: `record NotificationProperties(EmailProperties email, URI appBaseUrl)` + `record EmailProperties(ResendProperties resend, String fromAddress)` + `record ResendProperties(String apiKey)` (or whatever the existing core-properties idiom prefers — read the Phase 02B billing precedent)
    - `DigestRendererConfig` `@Configuration` defines a `@Bean TemplateEngine digestTemplateEngine(MessageSource messageSource)` exactly as RESEARCH §2 — two `ClassLoaderTemplateResolver` instances (HTML mode for `.html.thymeleaf`, TEXT mode for `.txt.thymeleaf`), both pointing at `email-templates/digest/`, `setTemplateEngineMessageSource(messageSource)` for `#{key}` resolution; also defines a dedicated `MessageSource digestMessageSource` that fails-loud (`setUseCodeAsDefaultMessage(false)`) on missing key
    - `ThymeleafDigestRenderer` `@Component`: methods `renderHtml(DigestPayload payload) → String`, `renderText(DigestPayload payload) → String`, `subject(DigestPayload payload) → String` (subject is a single MessageSource lookup with locale + zeroActivity branch, NOT a Thymeleaf render); both render methods construct a Thymeleaf `Context`, call `context.setLocale(payload.locale())`, set variables from the payload, and return the rendered string
    - `ResendEmailGateway` `@Component`: constructor injection of `NotificationProperties`, lazily-or-eagerly creates `Resend client = new Resend(properties.email().resend().apiKey())`; method `send(String fromAddress, String toAddress, String subject, String htmlBody, String textBody, String idempotencyKey) → DispatchOutcome` — builds `CreateEmailOptions` with `.from(...)`, `.to(...)`, `.subject(...)`, `.html(...)`, `.text(...)`, `.addHeader("Idempotency-Key", idempotencyKey)`, `.addTag(new Tag("category", "digest"))`; catches `ResendException` and classifies: 4xx (400/401/403/404/422) → `new PermanentFailure("resend_4xx_" + statusCode)`; 429/5xx/network → `new TransientFailure("resend_transient_" + statusCode)`. On success returns `new Success(response.getId())` so the Resend `email_id` flows through to `digest_delivery.external_ref`.
    - `EmailNotificationChannel` `@Component` implements `NotificationChannel.dispatch(DigestPayload payload, String recipientAddress)`: **REVIEW FIX (OpenCode M4 / S6)** — first line guards `Objects.requireNonNull(recipientAddress, "recipient")`; if blank (`recipientAddress.isBlank()`) return `new PermanentFailure("no_email_found")` IMMEDIATELY (the scheduler is supposed to short-circuit at step 5 of `dispatchOne` before calling here, but the channel double-checks because the contract is `NotificationChannel` interface — defense in depth). Then calls `thymeleafDigestRenderer.subject/renderHtml/renderText` to build the message body; calls `resendEmailGateway.send(properties.email().fromAddress(), recipientAddress, subject, html, text, payload.tenantId() + ":" + payload.digestDayLocal())`; returns the gateway's `DispatchOutcome`; logs `event=digest_dispatched tenantId={} digestDay={} externalId={}` on success and `event=digest_dispatch_failed tenantId={} digestDay={} reason={}` on failure — NO `to:` address, NO body, NO sender_email, NO subject (subject may include date but not addresses).
    - **REVIEW FIX (OpenCode M6 borderline / acceptance)** — the digest subject may contain `digest_day_local`; the send-hour timing is a non-content-side-channel concern flagged as v2 hardening. Document in summary; no v1 action.
    - HTML + TXT templates (`digest.html.thymeleaf`, `digest.txt.thymeleaf`) implement UI-SPEC §C content list per locale via `MessageSource` keys (subject, preheader, header.greeting, totals.{messages,timeSaved}, cta, topSenders.eyebrow, topRules.eyebrow, footer.{optOutPrompt,optOutLink,brand,legal}, zeroBody, zeroSubject, zeroPreheader, zeroGreeting); template uses `th:fragment` for header/totals/footer; ALL CSS is inline (no `<style>` block — Gmail inliner is unreliable per UI-SPEC); two-column totals row uses `<table>` not `flex`/`grid` (Outlook compatibility); single CTA button with accent-teal `#0E5E5A` background per UI-SPEC §C
    - `digest_vi.properties` + `digest_en.properties` mirror UI-SPEC §C copy verbatim — every key in both files (parity); the vi values match the EN row's "VI" column word-for-word
    - `ThymeleafDigestRendererTest`: real `TemplateEngine`, fixed `DigestPayload` fixtures — assert (a) vi HTML contains `Hôm qua trên Zero Mail`, (b) en HTML contains `Yesterday on Zero Mail`, (c) zero-activity HTML contains the zero-activity body copy, (d) plaintext format `1. sender@domain.com  (47)` matches one of the rows
    - `DigestMessageSourceParityTest`: load both properties files, assert key-set equality (fail-loud on any key present in one and missing in the other) + assert `digestMessageSource.getMessage(key, args, locale)` returns the bundle value (NOT `??key_vi??`) for every locked key in both locales — fail-loud bridge for renderer
    - `EmailNotificationChannelTest`: Mockito-mock `Resend`, capture the `CreateEmailOptions` argument — assert `Idempotency-Key` header equals `${tenantId}:${digestDayLocal}`, assert `.html(...)` and `.text(...)` both non-empty, assert tag `category=digest` is set. **NEW (OpenCode M4 / S6)** — additional case: invoke `dispatch(payload, null)` → assert returns `PermanentFailure` with reason `no_email_found`, assert Resend client was NEVER called; invoke `dispatch(payload, "")` → same outcome.
    - `DigestPrivacySweepTest`: mirrors `TriagePrivacySweepTest` — `ListAppender` + sensitive sentinels (`digest-sentinel-to-address@example.com`, `body-sentinel-05C`, `sender-sentinel-05C@example.com`); after a full dispatch run (mocked Resend so no real HTTP), assert sentinels never appear in captured log lines
  </behavior>
  <action>Read `backend/worker/src/main/resources/application.yml` first to determine if `spring.main.keep-alive` and `spring.messages.basename` are already present — if `spring.messages.basename` is set to a different namespace, define a dedicated `digestMessageSource` bean in `DigestRendererConfig` with `setBasename("i18n/digest")` instead of changing the global. Verbatim copy `LlmGatewayBoundaryTest` shape into `ResendBoundaryArchTest` (already created in Task 1 — verify it still passes after Resend imports land here). For Thymeleaf, use ONE HTML template + ONE TXT template + per-locale `MessageSource` resolution via `context.setLocale(...)` (RESEARCH §2 single-template recommendation, NOT per-locale-per-format 4-file approach). The `subject` MessageSource key has args `{volumeApplied}` and `{timeSavedSeconds}` — format the minutes server-side before passing to MessageSource so the bundle stays free of math (`Xh YYm` formatter is a small helper in `ThymeleafDigestRenderer`). The CTA URL `${app.base-url}/analytics?source=digest&window=7d` and opt-out URL `${app.base-url}/settings?section=notifications&source=digest` are built in `DigestComposer` (Task 1) NOT in the renderer — the renderer only emits the URL it receives in `DigestPayload`. NEVER log the `to:` address — `EmailNotificationChannel` MUST resolve the address via `userRepository.findEmailByTenantId(tenantId)` (or equivalent) and pass it directly to `ResendEmailGateway.send` without ever calling `log.info(... toAddress)`. After Java + YAML + property-file edits run `mcp__jetbrains__get_file_problems` on every touched Java file. Implements D-01 + D-02 + D-03 + D-25.</action>
  <verify>
    <automated>./gradlew :backend:worker:test --tests "ThymeleafDigestRendererTest" --tests "DigestMessageSourceParityTest" --tests "EmailNotificationChannelTest" --tests "DigestPrivacySweepTest" :backend:core:test --tests "ResendBoundaryArchTest" -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 5 named tests green. `gradle/libs.versions.toml` lists `resend = "4.13.0"`. `backend/worker/build.gradle.kts` declares both new dependencies. Templates render vi + en correctly. `MessageSource` fails loud on missing key. `Idempotency-Key` header asserted by mock. ArchUnit boundary still passes (only `email` package imports Resend). Privacy sweep green. `mcp__jetbrains__get_file_problems` 0 errors on touched Java files.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: DigestDispatchScheduler + DigestPendingReaperJob + full idempotency + DST/Noop integration tests</name>
  <files>
    backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestIdempotencyTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestPendingReaperJobTest.java,
    backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchWithNoopChannelTest.java
  </files>
  <read_first>
    backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java,
    backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java,
    backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java,
    backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (D-05 D-06 D-07 D-08 D-10 D-11),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§3 ShedLock + cron, §4 AT TIME ZONE + DST, §6 idempotency FSM)
  </read_first>
  <behavior>
    - `DigestDispatchScheduler` `@Component`. **REVIEW FIX (Codex C6 / OpenCode H2 / S1 — per-tenant transactions):**
      - `scheduledDispatch()` method is `@Scheduled(cron = "0 5 * * * *")` + `@SchedulerLock(name = "digestDispatchScheduler", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")` and is **NOT** annotated `@Transactional` — outer fanout is transaction-free.
      - First line: `LockAssert.assertLocked();` (Boot 4 + ShedLock 7.7.0).
      - Second line (Codex C8 single reference instant): `Instant referenceInstant = currentInstant.get();`
      - Query due tenants via JdbcTemplate using the REVIEW FIX claim SQL above (`?::timestamptz AT TIME ZONE t.time_zone` with `referenceInstant` as the JDBC param 1; channel = `'EMAIL'` uppercase; joins `users u ON u.tenant_id = t.id` for `u.preferred_language`). Returns a list of `DueTenant(tenantId, timeZone, sendHourLocal, preferredLanguage)` rows.
      - **For each `DueTenant`, call `dispatchOne(dueTenant, referenceInstant)` inside a try/catch that LOGS and continues** — one tenant's failure MUST NOT abort the loop (Codex C6 / OpenCode H2). Log `event=digest_tenant_failed tenantId={}` only — no body, no email, no stack chain in INFO (DEBUG/ERROR is acceptable for full stack).
    - `dispatchOne(DueTenant tenant, Instant referenceInstant)` method on a separate `@Component DigestDispatchTenantWorker` collaborator. **REVIEW FIX (Codex Cycle-2 HIGH-A — `dispatchOne` MUST NOT be `@Transactional`):** this method is plain (NO `@Transactional` annotation of any kind). The Resend HTTP call inside it executes outside any DB transaction. The three FSM mutators (`claimPending`, `markSent`, `markFailed`) on `DigestDeliveryService` are the ONLY methods that open their own `@Transactional(propagation = Propagation.REQUIRES_NEW)` boundaries — each opens, writes its single row, commits. Because each FSM call goes through a Spring-managed bean (`digestDeliveryService` is injected, not invoked via `this`), the AOP proxy fires correctly and each `REQUIRES_NEW` actually opens a new physical transaction. The collaborator pattern is used purely to keep the scheduler thin — it has no transactional semantics of its own:
      - **REVIEW FIX (Codex C7 / OpenCode M2 / S3 — ScopedValue tenant binding):** wrap the entire body in `ScopedValue.where(TenantContext.TENANT, tenant.tenantId().toString()).run(() -> { ... })` (or `.call(...)` if a return value is needed) BEFORE any JPA operation. This ensures `AbstractTenantOwnedEntity`'s `@TenantId` listener sees the correct tenant when `DigestDeliveryEntity` is saved via `claimPending` / `markSent` / `markFailed`. Without this binding, the listener either NPEs or writes the wrong tenant_id.
      - Inside the scope:
        1. `ZoneId tenantZone = ZoneId.of(tenant.timeZone());`
        2. `LocalDate digestDayLocal = referenceInstant.atZone(tenantZone).toLocalDate();` (Codex C8 — Java digest_day_local derived from the SAME referenceInstant that the SQL EXTRACT(HOUR) used). **REVIEW FIX (Codex Cycle-2 HIGH-B — digest window MUST anchor to the configured send-hour, not the cron execution instant):** the cron fires at `:05` so `referenceInstant` is ~5 minutes past the hour; using it directly as `sendMoment` would produce a window `[yesterday HH:05, today HH:05)` instead of the SPEC-required `[yesterday HH:00, today HH:00)`. Derive the anchored send moment instead: `LocalDateTime scheduledLocalDateTime = digestDayLocal.atTime(tenant.sendHourLocal(), 0);` then `Instant sendMoment = scheduledLocalDateTime.atZone(tenantZone).toInstant();` — `sendMoment` now lands on the tenant-local hour boundary (e.g. `20:00 Asia/Ho_Chi_Minh`), and the analytics window passed to `DigestComposer.compose(..., sendMoment, ...)` becomes `[sendMoment - 24h, sendMoment)` = `[yesterday 20:00, today 20:00)` as SPEC §"Digest content window" mandates.
        3. `Locale locale = Locale.forLanguageTag(tenant.preferredLanguage());`
        4. `DigestClaimRecord claim = digestDeliveryService.claimPending(tenant.tenantId(), digestDayLocal);` — in its OWN `REQUIRES_NEW` transaction (commits before next step). On `DigestAlreadyClaimedException` (UNIQUE violation): log `event=digest_already_claimed tenantId={} digestDay={}` and return.
        5. Resolve recipient address OUTSIDE any DB transaction: `Optional<String> maybeAddress = userRepository.findEmailByTenantId(tenant.tenantId());`. **REVIEW FIX (OpenCode M4 / S6):** if absent or blank → call `digestDeliveryService.markFailed(claim.deliveryId(), "no_email_found")` (separate `REQUIRES_NEW` transaction), log `event=digest_no_email_found tenantId={}`, return — NO Resend call.
        6. `DigestPayload payload = digestComposer.compose(tenant.tenantId(), tenantZone, locale, digestDayLocal, sendMoment, properties.appBaseUrl());` (no DB tx — read-only analytics queries run inside the composer's own `@Transactional(readOnly=true)`).
        7. `DispatchOutcome outcome = notificationChannel.dispatch(payload, maybeAddress.get());` — **OUTSIDE any DB transaction** (Codex C6 — third bullet of the write-order invariant). The Resend HTTP call is the only side effect; if it throws, the claim row stays PENDING and the reaper handles it later.
        8. `switch (outcome) { case Success s -> digestDeliveryService.markSent(claim.deliveryId(), ChannelType.EMAIL, s.externalId()); case PermanentFailure p -> digestDeliveryService.markFailed(claim.deliveryId(), p.reason()); case TransientFailure t -> digestDeliveryService.markFailed(claim.deliveryId(), t.reason()); }` — each in its own `REQUIRES_NEW` transaction.
    - `DigestPendingReaperJob` `@Component`: `@Scheduled(fixedDelay = 300_000L)` `@SchedulerLock(name = "digestPendingReaper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")`; promotes stuck-PENDING rows older than `PT30M` to FAILED with `failure_reason = "reaper_stuck_pending"`; mirrors `TriagePendingReaperJob` batch shape. The reaper wraps each row's mark in `ScopedValue.where(TenantContext.TENANT, row.tenantId().toString())` so the JPA UPDATE has the right tenant context.
    - `Supplier<Instant> currentInstant` injection: helper bean in `backend/worker/notification/config/` defaulting to `Instant::now` so tests can stub time (RESEARCH §11). REVIEW FIX (Codex C8): the scheduler calls `currentInstant.get()` EXACTLY ONCE per scheduledDispatch tick and threads that value through to BOTH the SQL claim query and the Java digest_day_local math.
    - `DigestDispatchSchedulerTest` (Testcontainers): seeded 3 tenants — A (digest_enabled=true, hour=20, tz=`Asia/Ho_Chi_Minh`), B (digest_enabled=false, hour=20), C (digest_enabled=true, hour=8); seed corresponding `users` rows with `preferred_language='vi'`/`'en'` etc.; inject a `Supplier<Instant>` stub that yields an instant whose `Asia/Ho_Chi_Minh` local hour is 20; mock `NotificationChannel.dispatch` to return `Success("res_test_abc")`; run `scheduledDispatch()`; assert exactly ONE `notificationChannel.dispatch` call with `payload.tenantId() = A`, and ONE row in `digest_delivery (tenant_id=A, status='SENT', channel='EMAIL', digest_day_local=today_in_ho_chi_minh, external_ref='res_test_abc')` — neither B nor C produces a row. **NEW (Codex C6 / OpenCode H2 isolation case):** seed 3 enabled tenants A, B, C; mock channel.dispatch to throw RuntimeException ONLY for tenant B; run scheduledDispatch — assert digest_delivery has SENT rows for A and C (NOT rolled back by B's failure) and a FAILED row for B. **NEW (Codex C7 / OpenCode M2 ScopedValue case):** assert that within `dispatchOne` the `TenantContext.current()` returns the tenant's id (via a verification listener bean exposed in @TestConfiguration). **NEW (Codex C8 single-instant case):** capture both the SQL claim query parameter AND the saved `digest_delivery.digest_day_local` value; assert that `digest_day_local == stubbedInstant.atZone(tenant.timeZone).toLocalDate()` AND that the SQL query was called with `stubbedInstant` (NOT `now()`). **NEW (Codex Cycle-2 HIGH-B send-hour-anchor case):** stub the `Supplier<Instant>` to yield a moment whose `Asia/Ho_Chi_Minh` local time is `20:05` (i.e. the realistic cron-fires-at-`:05` skew) for a tenant with `sendHourLocal=20, timeZone=Asia/Ho_Chi_Minh`; capture the `TimeWindow` passed to `AnalyticsSummaryQueryService.summarize` (via a `@SpyBean` or a verification listener on `DigestComposer`); assert `window.endExclusive()` equals `digestDayLocal.atTime(20, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()` (i.e. `20:00`, NOT `20:05`) — proves the analytics window anchors to the configured hour boundary even though the reference instant is `:05`.
    - `DigestIdempotencyTest`: run `scheduledDispatch()` twice with the same `Supplier<Instant>` stub; assert `notificationChannel.dispatch` called exactly ONCE; assert `digest_delivery (tenant, today)` row count is exactly 1; the second call hits the UNIQUE constraint and skips with `event=digest_already_claimed`.
    - `DigestPendingReaperJobTest`: seed a `digest_delivery (PENDING, created_at=NOW()-PT45M)` row + a `digest_delivery (PENDING, created_at=NOW()-PT5M)` row; run reaper; assert the PT45M row is FAILED, the PT5M row remains PENDING.
    - `DigestDispatchWithNoopChannelTest`: register a `NoopNotificationChannel` (test config) that returns `Success("noop-ref")` without dispatching; run dispatch with one enabled tenant; assert NO Resend call (mocked) is made; assert `digest_delivery (tenant, today, SENT, channel='EMAIL', external_ref='noop-ref')` row exists — proves D-04 channel substitution works.
  </behavior>
  <action>Mirror `BillingIntentExpirySweeper` for the scheduler skeleton (read it first — `@Component` + `@Scheduled` + `@SchedulerLock`). **REVIEW-FIX divergence from the precedent (Codex C6 / OpenCode H2 + Codex Cycle-2 HIGH-A — locked):** the outer `scheduledDispatch()` is NOT `@Transactional` — the precedent's `@Transactional(propagation = Propagation.REQUIRED)` is the very pattern flagged as unsafe for fanout. `dispatchOne(...)` is ALSO NOT `@Transactional` — putting `@Transactional(REQUIRES_NEW)` on `dispatchOne` would wrap the Resend HTTP call in a DB transaction, which contradicts the must-have invariant "Resend is called outside any DB transaction" and risks holding a Postgres connection open across a ~1–3s network call. Instead, the three FSM mutators `claimPending`, `markSent`, `markFailed` on `DigestDeliveryService` each carry `@Transactional(propagation = Propagation.REQUIRES_NEW)` — they are the ONLY transactional surface for the dispatch path. Extract `dispatchOne` into a separate `@Component DigestDispatchTenantWorker` that the scheduler injects so the scheduler stays thin; the collaborator pattern is for clarity, NOT to make `@Transactional` proxy-fire (because the method has no `@Transactional` to fire). The proxy that DOES need to fire is the one around `digestDeliveryService` — which works because the scheduler / worker invoke it via injection, not via `this`. The `findTenantsDueForDigest` JdbcTemplate query is per the REVIEW-FIX SQL above (`'EMAIL'` uppercase; `?::timestamptz AT TIME ZONE` form with referenceInstant param; users join for locale). `digestDayLocal` MUST be the tenant-local DATE computed from `referenceInstant.atZone(ZoneId.of(tenant.timeZone)).toLocalDate()` — same reference instant as the SQL (Codex C8). Wrap `dispatchOne`'s body in `ScopedValue.where(TenantContext.TENANT, tenant.tenantId().toString()).run(() -> ...)` (Codex C7 / OpenCode M2). The reaper mirrors `TriagePendingReaperJob` batch shape (BATCH_LIMIT loop + `event=digest_pending_reaped tenantId=system totalProcessed={}`), also wrapped in ScopedValue per row. For tests, inject `Supplier<Instant>` (not `Clock` — project doesn't have a `Clock` bean) and stub it via `@TestConfiguration`. `DigestDispatchSchedulerTest` MUST use `Testcontainers Postgres` (not Mockito for repos) so the UNIQUE constraint is real. The new isolation case + ScopedValue case + single-instant case are MANDATORY proofs that the review fixes work. `DigestDispatchWithNoopChannelTest` registers a `NoopNotificationChannel` via `@TestConfiguration` that returns `Success("noop-ref")` — proves the dispatcher doesn't care which channel is wired (D-04 substitution proof) and the `external_ref` column round-trips. After Java edits run `mcp__jetbrains__get_file_problems`. Implements D-05 + D-06 + D-07 (locked as no-catch-up; see CONTEXT D-07 update below) + D-10 + D-11 + REVIEWS Codex C6 / C7 / C8 / Codex MEDIUM ArchUnit + OpenCode H1 (D-07 lock) / H2 / M2 / S1 / S3.</action>
  <verify>
    <automated>./gradlew :backend:worker:test --tests "DigestDispatchSchedulerTest" --tests "DigestIdempotencyTest" --tests "DigestPendingReaperJobTest" --tests "DigestDispatchWithNoopChannelTest" -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 4 named tests green. `DigestDispatchScheduler` runs at cron `0 5 * * * *` under `digestDispatchScheduler` ShedLock name (no collision with existing 5 schedulers). `LockAssert.assertLocked()` first line of `dispatch()`. Idempotency proven by 2-run test (≤1 dispatch). Reaper promotes PT30M+ stuck PENDING. Noop channel proves D-04 channel-substitution works. `mcp__jetbrains__get_file_problems` 0 errors on touched files. `./gradlew :backend:worker:check` BUILD SUCCESSFUL.
  </done>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| client → `/api/me/notifications` (GET + PATCH) | Untrusted body crosses here — must be validated; tenant scoping via TenantContext |
| `DigestDispatchScheduler` → `NotificationChannel` (Resend) | Outbound HTTP to a third-party service; SDK confined to one package; no body content in logs |
| Worker JVM → Postgres | New `notification_preference` + `digest_delivery` writes; UNIQUE-constraint-as-dedupe is the security-relevant idempotency boundary |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05C-08 | Information disclosure (logs) | EmailNotificationChannel + ResendEmailGateway logging | mitigate | `event=digest_dispatched tenantId={} digestDay={} externalId={}` only; NO `to:`, NO subject body, NO sender_email; `DigestPrivacySweepTest` asserts sentinels never reach captured log lines |
| T-05C-09 | Tampering / Repudiation (duplicate sends) | Idempotency between worker crashes and Resend acknowledgement | mitigate | Five-layer crash safety: (1) ShedLock prevents 2 workers per tick, (2) Postgres UNIQUE prevents 2 ticks per (tenant, day), (3) `lockAtMostFor=PT20M` releases after worker death, (4) Resend `Idempotency-Key: tenantId:digestDayLocal` with 24h TTL dedupes server-side, (5) **REVIEW FIX (Codex C6 / OpenCode H2)** — per-tenant `REQUIRES_NEW` transactional units prevent partial-failure double-send (tenant N's failure can no longer roll back tenants 1..N-1's claim INSERTs / SENT UPDATEs). `DigestIdempotencyTest` proves layers (1)–(3); SDK contract proves (4); the new `DigestDispatchSchedulerTest` isolation case proves (5) |
| T-05C-10 | Information disclosure (cross-tenant via preferences API) | `NotificationPreferencesController` GET + PATCH | mitigate | TenantContext.currentOrThrow() extracted at controller; service queries scoped by tenantId; `NotificationPreferencesControllerTest` case (e) asserts tenant A cannot affect tenant B's row |
| T-05C-11 | Supply chain (Resend SDK) | `com.resend:resend-java:4.13.0` new dependency | accept | Vendor is locked by D-01; SDK isolated to one ArchUnit-enforced package; `ResendBoundaryArchTest` ensures no other code can import; renovate/dependabot already tracks SDK CVEs project-wide |
| T-05C-12 | Tampering (preferences PATCH validation) | `digestSendHourLocal` field | mitigate | `@Min(0) @Max(23)` Jakarta Validation; `time_zone` not editable in v1 (read-only field on response, not accepted by request); `NotificationPreferencesControllerTest` case (c) asserts 400 on out-of-range |

</threat_model>

<decision_amendments>

## CONTEXT.md decision amendment — D-07 (REVIEW FIX OpenCode H1)

**Before this plan ships, the next discuss-phase iteration (or this plan's executor as part of summary) MUST update `05C-CONTEXT.md` D-07 from the current text to:**

> **D-07 (LOCKED v1):** Worst-case lateness is ~59 minutes for a tenant whose `digest_send_hour_local` matches the *current* tick (worker queues a few minutes behind the cron). **If the worker is down through a tenant's exact send-hour tick, that tenant's digest for that day is SKIPPED with NO catch-up** — the next tick will see `EXTRACT(HOUR FROM (referenceInstant AT TIME ZONE t.time_zone))::int != digest_send_hour_local` for that tenant and not claim a row. The "missed-hour recovery" claim in the previous D-07 wording was inaccurate under D-06's exact-hour match query (OpenCode H1 finding) and is now removed. A catch-up mode (extend the claim query to also accept tenants where `digest_day_local < today_local` and no SENT row exists for that day) is reserved for v2.

This is the load-bearing operational expectation lock — v1 ships with daily digests delivered ONLY on the exact local send-hour tick. Communicate this on the `/settings → Notifications` helper text so users with chronically restarted workers understand the trade-off.

</decision_amendments>

<verification>
- `./gradlew :backend:core:test --tests "DigestPayloadShapeArchTest"` exits 0
- `./gradlew :backend:worker:test --tests "ResendBoundaryArchTest" --tests "*Digest*" --tests "*Notification*" --tests "EmailNotificationChannelTest" --tests "ThymeleafDigestRendererTest"` exits 0
- `./gradlew :backend:api:test --tests "NotificationPreferencesControllerTest"` exits 0
- `./gradlew :backend:core:check :backend:api:check :backend:worker:check` BUILD SUCCESSFUL
- `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors
- Manual: `./gradlew :backend:api:bootRun` + `curl http://localhost:8080/v3/api-docs | jq '.paths["/api/me/notifications"]'` shows both GET + PATCH registered
- Manual: `grep -rn "com.resend" backend/` returns hits only under `backend/worker/.../notification/email/` (boundary visual confirmation)
</verification>

<success_criteria>
- Idempotency: 2 dispatch runs for the same (tenant, day) produce exactly 1 outbound Resend call (`DigestIdempotencyTest`)
- **Per-tenant isolation: tenant B's failure does NOT roll back tenants A and C's SENT rows (`DigestDispatchSchedulerTest` isolation case) — REVIEW FIX Codex C6 / OpenCode H2**
- **Transaction-boundary discipline: `scheduledDispatch()` and `dispatchOne(...)` are NOT `@Transactional`; only `DigestDeliveryService.{claimPending, markSent, markFailed}` are `@Transactional(REQUIRES_NEW)`. Resend HTTP call runs outside any DB transaction (verified by reading the source — no `@Transactional` on the scheduler or worker bean). — REVIEW FIX Codex Cycle-2 HIGH-A**
- **Digest content window anchors to the configured send-hour boundary (`HH:00`), not the cron execution instant (`HH:05`). `DigestDispatchSchedulerTest` send-hour-anchor case asserts `TimeWindow.endExclusive()` equals `digestDayLocal.atTime(sendHourLocal, 0).atZone(tenantZone).toInstant()` — REVIEW FIX Codex Cycle-2 HIGH-B**
- **TenantContext: `dispatchOne` sees the right tenant via ScopedValue.where (`DigestDispatchSchedulerTest` ScopedValue case) — REVIEW FIX Codex C7 / OpenCode M2**
- **Single reference instant: claim SQL and `digest_day_local` both derive from the same `currentInstant.get()` value (`DigestDispatchSchedulerTest` single-instant case) — REVIEW FIX Codex C8**
- **claimPending returns DigestClaimRecord (NOT boolean); markSent persists `external_ref` (`DigestDispatchWithNoopChannelTest` asserts `external_ref='noop-ref'`) — REVIEW FIX Codex MEDIUM**
- **Null/blank recipient address path returns PermanentFailure('no_email_found') and skips Resend (`EmailNotificationChannelTest` null case) — REVIEW FIX OpenCode M4 / S6**
- **D-07 LOCKED: missed-hour silently skipped, no catch-up (CONTEXT.md updated below) — REVIEW FIX OpenCode H1**
- **core.notification Modulith declares `allowedDependencies={"analytics", ...}` so DigestComposer's analytics import is verification-clean — REVIEW FIX OpenCode M1 / S4**
- **ResendBoundaryArchTest lives in worker module so it scans the actual SDK import site (non-vacuous) — REVIEW FIX Codex MEDIUM ArchUnit location**
- Opt-out: digest_enabled=false produces zero dispatches (`DigestDispatchSchedulerTest` tenant B case)
- Zero-activity: zeroActivity=true payloads still dispatch with the "no activity yesterday" copy (`DigestComposerTest` + `ThymeleafDigestRendererTest`)
- Resend Idempotency-Key header asserted (`EmailNotificationChannelTest`)
- Channel substitution works: NoopNotificationChannel makes the dispatcher succeed without Resend (`DigestDispatchWithNoopChannelTest`)
- vi + en parity (`DigestMessageSourceParityTest`)
- Boundary: Resend SDK imports only in `worker.notification.email` (`ResendBoundaryArchTest` in worker module)
- DigestPayload channel-free (`DigestPayloadShapeArchTest`)
- Privacy: no sender / body / `to:` in worker logs (`DigestPrivacySweepTest`)
- Preferences API: GET + PATCH work; PATCH validates 0–23 hour; tenant-scoped via controller-extracted explicit tenantId param
- Reaper promotes stuck-PENDING > PT30M to FAILED
- CTA + opt-out URI built via `URI.resolve()` — trailing-slash baseAppUrl doesn't produce `//analytics` (OpenCode L4)
- Resend SDK version verified for Spring Boot 4 / Java 25 via Context7 before commit (OpenCode S8)
</success_criteria>

<output>
After completion, create `.planning/phases/05C-user-surface-analytics-daily-digest/05C-03-SUMMARY.md` capturing:
- Whether `spring.main.keep-alive: true` was already set in worker yml (preexisting vs added)
- The exact cron string + scheduler lock names chosen (cron offset to avoid collision)
- The single-template vs per-locale-per-format choice and rationale (RESEARCH §2 vs §C alternative)
- Resend Idempotency-Key behaviour on a mock-replay (verified by EmailNotificationChannelTest)
- DST handling: confirm v1 Vietnam-only `Asia/Ho_Chi_Minh` is DST-free; flag Europe/Berlin spring-forward as v2 known issue
- Whether `userRepository.findEmailByTenantId` already existed or was added; if added, the file + Phase 01.5 owner trail
</output>
