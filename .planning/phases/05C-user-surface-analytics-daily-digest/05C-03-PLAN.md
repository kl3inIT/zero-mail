---
phase: 05C
plan: 03
type: execute
wave: 2
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
  - backend/core/src/test/java/com/zeromail/core/arch/ResendBoundaryArchTest.java
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
    - "Worker scheduler claims a digest_delivery PENDING row for every (tenant, day) where digest_enabled=true AND the tenant's local hour matches digest_send_hour_local at the current tick"
    - "Running the dispatcher twice for the same (tenant, day) produces exactly one outbound Resend send call"
    - "Tenant with digest_enabled=false receives no dispatch regardless of activity"
    - "Zero-activity tenant with digest_enabled=true receives the digest with explicit 'no activity yesterday' wording"
    - "Resend SDK Idempotency-Key header carries tenantId:digestDayLocal — verified by mock"
    - "Resend SDK imports appear in exactly ONE package (worker.notification.email) — ArchUnit boundary fails on any other import site"
    - "DigestPayload record has no email-specific fields (no htmlBody, mimeType, subject)"
    - "GET /api/me/notifications + PATCH /api/me/notifications scope by TenantContext.currentOrThrow() and persist via NotificationPreferenceService"
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
      via: "UNIQUE-constraint-as-dedupe (D-09)"
      pattern: "claimPending"
    - from: "EmailNotificationChannel"
      to: "Resend SDK"
      via: "ResendEmailGateway with addHeader(\"Idempotency-Key\", ...)"
      pattern: "Idempotency-Key"
    - from: "DigestComposer"
      to: "AnalyticsSummaryQueryService.summarize"
      via: "Duration.ofHours(24) anchored at tenant local send moment"
      pattern: "summarize.*Duration\\.ofHours\\(24\\)"
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

Resend SDK `com.resend:resend-java:4.13.0` (verified at Maven Central per RESEARCH §1). Add to `libs.versions.toml`:
```
[versions]
resend = "4.13.0"
[libraries]
resend-java = { module = "com.resend:resend-java", version.ref = "resend" }
```
Then in `backend/worker/build.gradle.kts`: `implementation(libs.resend.java)` + `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")`.

Resend send signature: `new Resend(apiKey).emails().send(CreateEmailOptions.builder().from(...).to(...).subject(...).html(...).text(...).addHeader("Idempotency-Key", tenantId + ":" + digestDayLocal).build())` returns `CreateEmailResponse` (has `.getId()`). Throws `ResendException` with `.getStatusCode()`. 4xx (`400`/`401`/`403`/`422`/`404`) → permanent FAILED, no retry. `429`/`5xx`/network → transient FAILED, reaper-or-next-tick retry. Resend's own Idempotency-Key TTL is 24h, max 256 chars; `tenantId(36) + ":" + digestDayLocal(10) = 47 chars` is safely under.

DigestDispatchScheduler claim query (D-06 Postgres-side hour match):
```
SELECT np.tenant_id, t.time_zone, np.digest_send_hour_local, t.preferred_language
FROM notification_preference np
JOIN tenants t ON t.id = np.tenant_id
WHERE np.digest_enabled = true
  AND np.channel = 'email'
  AND EXTRACT(HOUR FROM (now() AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local
```

`digest_day_local` MUST be the tenant-local DATE on the send moment (per RESEARCH §4 + Open Question 5 — so any same-day tick collides regardless of which hour fires). Compute via `ZonedDateTime.now(ZoneId.of(tenant.timeZone)).toLocalDate()` at the moment of insert.

`DigestComposer` reuses `AnalyticsSummaryQueryService.summarize(tenantId, Duration.ofHours(24))`. The "prior 24h" window is `[localSendMoment − 24h, localSendMoment)` in tenant timezone — anchor at the send moment (`ZonedDateTime.now(zone)`), pass `Instant.now().minus(Duration.ofHours(24))` to the query.

Spring Boot 4 + virtual threads in worker: verify `spring.main.keep-alive: true` is set in `backend/worker/src/main/resources/application.yml` — RESEARCH §3 + Pitfall 2 flagged this. If absent, ADD it in this plan.

i18n MessageSource for digest: separate from web. Bind via `spring.messages.basename: i18n/digest` in `backend/worker/.../application.yml` (or a dedicated `ReloadableResourceBundleMessageSource` bean if the worker already has a different basename). Files: `backend/worker/src/main/resources/i18n/digest_vi.properties` + `digest_en.properties`. Keys: per UI-SPEC §C subject, preheader, header.greeting, totals.{messages,timeSaved}, cta, topSenders.eyebrow, topRules.eyebrow, footer.{optOutPrompt,optOutLink,brand,legal}, zeroBody (and the .normal / .zero variants). Failing-loud on missing key: set `messageSource.setUseCodeAsDefaultMessage(false)` so the renderer fails-loud — `DigestMessageSourceParityTest` enforces.

`ResendBoundaryArchTest` (placed under `backend/core/src/test/java/com/zeromail/core/arch/`): `noClasses().that().resideOutsideOfPackage("..worker.notification.email..").should().dependOnClassesThat().resideInAnyPackage("com.resend..")` + because-string `NTF-01: Resend SDK imports MUST be confined to backend/worker/.../notification/email. EmailNotificationChannel is the single adapter; DigestPayload + DigestComposer + NotificationChannel are provider-free. NO EXEMPTION.` Mirrors `LlmGatewayBoundaryTest` verbatim.

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
    - `NotificationChannel` interface: single method `DispatchOutcome dispatch(DigestPayload payload)` where `DispatchOutcome` is a sealed interface `permits Success, TransientFailure, PermanentFailure` (or a simpler `record DispatchOutcome(boolean success, String externalId, String failureReason, boolean retryable)`)
    - `DigestComposer` `@Service`: `compose(UUID tenantId, ZoneId tenantZone, Locale tenantLocale, LocalDate digestDayLocal, URI baseAppUrl)` — calls `analyticsSummaryQueryService.summarize(tenantId, Duration.ofHours(24))`, computes `ctaUrl = baseAppUrl + "/analytics?source=digest&window=7d"`, `optOutUrl = baseAppUrl + "/settings?section=notifications&source=digest"`, sets `zeroActivity = (volumeObserved == 0 && volumeApplied == 0)`, returns `DigestPayload`
    - `DigestDeliveryService` `@Service` `@Transactional`: `claimPending(UUID tenantId, LocalDate digestDayLocal) → boolean` (true if INSERT succeeded, false if SQLState 23505 / DataIntegrityViolationException), `markSent(UUID deliveryId, ChannelType channel, String externalId)`, `markFailed(UUID deliveryId, String failureReason)`, `findStuckPending(Duration graceperiod) → List<DigestDeliveryEntity>` for the reaper
    - `NotificationPreferencesController`: `GET /api/me/notifications` returns email-channel pref for current tenant; `PATCH /api/me/notifications` validates `digestSendHourLocal` via `@Min(0) @Max(23)` then calls `notificationPreferenceService.updatePreference(tenantId, EMAIL, request.digestEnabled(), request.digestSendHourLocal())`; both endpoints require authenticated session
    - `ResendBoundaryArchTest`: copies `LlmGatewayBoundaryTest` shape — `noClasses().that().resideOutsideOfPackage("..worker.notification.email..").should().dependOnClassesThat().resideInAnyPackage("com.resend..")`; before Plan 02's Resend gateway lands the test passes vacuously, after Task 2 lands it still passes (only `EmailNotificationChannel` + `ResendEmailGateway` import Resend)
    - `DigestPayloadShapeArchTest`: fields-DSL banning `htmlBody`, `mimeType`, `subject`, `to`, `toAddress`, `htmlContent`, `bodyHtml`
    - `DigestComposerTest`: seeded `AnalyticsSummaryQueryService` mock — case (a) non-zero counts → zeroActivity=false, totals populated; case (b) zero counts → zeroActivity=true, topSenders + topRules are empty lists not null; case (c) ctaUrl ends with `?source=digest&window=7d`, optOutUrl with `?section=notifications&source=digest`
    - `NotificationPreferencesControllerTest`: `@WebMvcTest` + MockMvc — (a) GET unauthenticated → 401, (b) GET authenticated → 200 + JSON with channel/digestEnabled/digestSendHourLocal/timeZone, (c) PATCH with `digestSendHourLocal=24` → 400, (d) PATCH with valid body → 200 + persisted state, (e) PATCH from tenant A cannot modify tenant B's row (verified by mock-call assertion on TenantContext)
  </behavior>
  <action>Create `core.notification.domain` + `core.notification.usecases` Java files. `DigestPayload` is a Java record — NO Jackson annotations (Spring MVC default record serialization is enough per RESEARCH Pitfall 1). The boolean `zeroActivity` field is the load-bearing branch for the renderer (UI-SPEC §C "Zero-activity body"). `NotificationChannel` interface keeps the implementation choice deferred — Task 2 ships the only v1 implementation. `DigestDeliveryService.claimPending` uses the exception-catch idiom (matches Phase 02B billing precedent — read `BillingTopupIntent*Service` for the SQLState 23505 catch pattern). `NotificationPreferencesController` mirrors `TriageAuditController` for the auth + TenantContext extraction + thin-controller shape (Convention 1). The `time_zone` field is read-only in `NotificationPreferencesResponse` and NOT accepted by `PATCH`. `ResendBoundaryArchTest` + `DigestPayloadShapeArchTest` live in `core/src/test/java/.../arch/` (same package as `LlmGatewayBoundaryTest`). After Java edits run `mcp__jetbrains__get_file_problems` on touched files. Implements D-04 + D-09 + D-12 + WEB-02 analytics portion (preferences API).</action>
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
    - `ResendEmailGateway` `@Component`: constructor injection of `NotificationProperties`, lazily-or-eagerly creates `Resend client = new Resend(properties.email().resend().apiKey())`; method `send(String fromAddress, String toAddress, String subject, String htmlBody, String textBody, String idempotencyKey) → DispatchOutcome` — builds `CreateEmailOptions` with `.from(...)`, `.to(...)`, `.subject(...)`, `.html(...)`, `.text(...)`, `.addHeader("Idempotency-Key", idempotencyKey)`, `.addTag(new Tag("category", "digest"))`; catches `ResendException` and classifies: 4xx (400/401/403/404/422) → PermanentFailure with reason `resend_4xx_${statusCode}`; 429/5xx/network → TransientFailure with reason `resend_transient_${statusCode}`
    - `EmailNotificationChannel` `@Component` implements `NotificationChannel`: looks up `toAddress` for the tenant via `UserRepository.findEmailByTenantId(tenantId)` (or the existing equivalent — verify Phase 01.5 wiring; the email address is NOT in `DigestPayload` because the payload is channel-free); calls `thymeleafDigestRenderer.subject/renderHtml/renderText`; calls `resendEmailGateway.send(fromAddress, toAddress, subject, html, text, payload.tenantId() + ":" + payload.digestDayLocal())`; returns `DispatchOutcome`; logs `event=digest_dispatched tenantId={} digestDay={} externalId={}` on success and `event=digest_dispatch_failed tenantId={} digestDay={} reason={}` on failure — NO `to:`, NO body, NO sender_email
    - HTML + TXT templates (`digest.html.thymeleaf`, `digest.txt.thymeleaf`) implement UI-SPEC §C content list per locale via `MessageSource` keys (subject, preheader, header.greeting, totals.{messages,timeSaved}, cta, topSenders.eyebrow, topRules.eyebrow, footer.{optOutPrompt,optOutLink,brand,legal}, zeroBody, zeroSubject, zeroPreheader, zeroGreeting); template uses `th:fragment` for header/totals/footer; ALL CSS is inline (no `<style>` block — Gmail inliner is unreliable per UI-SPEC); two-column totals row uses `<table>` not `flex`/`grid` (Outlook compatibility); single CTA button with accent-teal `#0E5E5A` background per UI-SPEC §C
    - `digest_vi.properties` + `digest_en.properties` mirror UI-SPEC §C copy verbatim — every key in both files (parity); the vi values match the EN row's "VI" column word-for-word
    - `ThymeleafDigestRendererTest`: real `TemplateEngine`, fixed `DigestPayload` fixtures — assert (a) vi HTML contains `Hôm qua trên Zero Mail`, (b) en HTML contains `Yesterday on Zero Mail`, (c) zero-activity HTML contains the zero-activity body copy, (d) plaintext format `1. sender@domain.com  (47)` matches one of the rows
    - `DigestMessageSourceParityTest`: load both properties files, assert key-set equality (fail-loud on any key present in one and missing in the other) + assert `digestMessageSource.getMessage(key, args, locale)` returns the bundle value (NOT `??key_vi??`) for every locked key in both locales — fail-loud bridge for renderer
    - `EmailNotificationChannelTest`: Mockito-mock `Resend`, capture the `CreateEmailOptions` argument — assert `Idempotency-Key` header equals `${tenantId}:${digestDayLocal}`, assert `.html(...)` and `.text(...)` both non-empty, assert tag `category=digest` is set
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
    - `DigestDispatchScheduler` `@Component`: `@Scheduled(cron = "0 5 * * * *")` `@SchedulerLock(name = "digestDispatchScheduler", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")` annotated `scheduledDispatch()`; internal `dispatch()` method `@Transactional(propagation = Propagation.REQUIRED)` with `LockAssert.assertLocked()` as the first line; queries due tenants via the D-06 SQL (`AT TIME ZONE` Postgres-side hour match); for each tenant — (1) compute `digestDayLocal = ZonedDateTime.now(ZoneId.of(tenant.timeZone)).toLocalDate()`, (2) `digestDeliveryService.claimPending(tenantId, digestDayLocal)` — skip if false, (3) `payload = digestComposer.compose(...)`, (4) `outcome = notificationChannel.dispatch(payload)`, (5) on success → `markSent`; on failure → `markFailed`
    - `DigestPendingReaperJob` `@Component`: `@Scheduled(fixedDelay = 300_000L)` `@SchedulerLock(name = "digestPendingReaper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")`; promotes stuck-PENDING rows older than `PT30M` to FAILED with `failure_reason = "reaper_stuck_pending"`; mirrors `TriagePendingReaperJob` batch shape
    - `Supplier<Instant> currentInstant` injection: optional helper bean in `backend/worker/notification/config/` defaulting to `Instant::now` so tests can stub time (RESEARCH §11)
    - `DigestDispatchSchedulerTest` (Testcontainers): seeded 3 tenants — A (digest_enabled=true, hour=20, tz=`Asia/Ho_Chi_Minh`), B (digest_enabled=false, hour=20), C (digest_enabled=true, hour=8); inject a `Clock` or `Supplier<Instant>` stub that yields an instant whose `Asia/Ho_Chi_Minh` local hour is 20; mock `NotificationChannel.dispatch` to return Success; run `dispatch()`; assert exactly ONE `notificationChannel.dispatch` call with `tenantId = A`, and ONE row in `digest_delivery (tenant_id=A, status='SENT', digest_day_local=today_in_ho_chi_minh)` — neither B nor C produces a row
    - `DigestIdempotencyTest`: run `dispatch()` twice with the same `Supplier<Instant>` stub; assert `notificationChannel.dispatch` called exactly ONCE; assert `digest_delivery (tenant, today)` row count is exactly 1; the second call hits the UNIQUE constraint and skips
    - `DigestPendingReaperJobTest`: seed a `digest_delivery (PENDING, created_at=NOW()-PT45M)` row + a `digest_delivery (PENDING, created_at=NOW()-PT5M)` row; run reaper; assert the PT45M row is FAILED, the PT5M row remains PENDING
    - `DigestDispatchWithNoopChannelTest`: register a `NoopNotificationChannel` (test config) that returns Success without dispatching; run dispatch with one enabled tenant; assert NO Resend call (mocked) is made; assert `digest_delivery (tenant, today, SENT, channel='email')` row exists — proves D-04 channel substitution works
  </behavior>
  <action>Mirror `BillingIntentExpirySweeper` for the scheduler skeleton (read it first — `@Component` + `@Scheduled` + `@SchedulerLock` + `@Transactional(propagation = Propagation.REQUIRED)` + `LockAssert.assertLocked()` first line). The `findTenantsDueForDigest` JdbcTemplate query is per-D-06 with `EXTRACT(HOUR FROM (now() AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local`. `digestDayLocal` MUST be the tenant-local DATE, computed via `ZonedDateTime.now(currentInstant.get().atZone(ZoneId.of(tenant.timeZone))).toLocalDate()` — this is the load-bearing same-day-tick-collides invariant per RESEARCH §4 Open Question 5. The reaper mirrors `TriagePendingReaperJob` batch shape (BATCH_LIMIT loop + `event=digest_pending_reaped tenantId=system totalProcessed={}`). For tests, inject `Supplier<Instant>` (not `Clock` — project doesn't have a `Clock` bean) and stub it via `@TestConfiguration`. `DigestDispatchSchedulerTest` MUST use `Testcontainers Postgres` (not Mockito for repos) so the UNIQUE constraint is real. `DigestDispatchWithNoopChannelTest` registers a `NoopNotificationChannel` via `@TestConfiguration` that returns Success — proves the dispatcher doesn't care which channel is wired (D-04 substitution proof). After Java edits run `mcp__jetbrains__get_file_problems`. Implements D-05 + D-06 + D-07 + D-10 + D-11.</action>
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
| T-05C-09 | Tampering / Repudiation (duplicate sends) | Idempotency between worker crashes and Resend acknowledgement | mitigate | Four-layer crash safety: (1) ShedLock prevents 2 workers per tick, (2) Postgres UNIQUE prevents 2 ticks per (tenant, day), (3) `lockAtMostFor=PT20M` releases after worker death, (4) Resend `Idempotency-Key: tenantId:digestDayLocal` with 24h TTL dedupes server-side. `DigestIdempotencyTest` proves layers (1)–(3); SDK contract proves (4) |
| T-05C-10 | Information disclosure (cross-tenant via preferences API) | `NotificationPreferencesController` GET + PATCH | mitigate | TenantContext.currentOrThrow() extracted at controller; service queries scoped by tenantId; `NotificationPreferencesControllerTest` case (e) asserts tenant A cannot affect tenant B's row |
| T-05C-11 | Supply chain (Resend SDK) | `com.resend:resend-java:4.13.0` new dependency | accept | Vendor is locked by D-01; SDK isolated to one ArchUnit-enforced package; `ResendBoundaryArchTest` ensures no other code can import; renovate/dependabot already tracks SDK CVEs project-wide |
| T-05C-12 | Tampering (preferences PATCH validation) | `digestSendHourLocal` field | mitigate | `@Min(0) @Max(23)` Jakarta Validation; `time_zone` not editable in v1 (read-only field on response, not accepted by request); `NotificationPreferencesControllerTest` case (c) asserts 400 on out-of-range |

</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "ResendBoundaryArchTest" --tests "DigestPayloadShapeArchTest"` exits 0
- `./gradlew :backend:worker:test --tests "*Digest*" --tests "*Notification*" --tests "EmailNotificationChannelTest" --tests "ThymeleafDigestRendererTest"` exits 0
- `./gradlew :backend:api:test --tests "NotificationPreferencesControllerTest"` exits 0
- `./gradlew :backend:core:check :backend:api:check :backend:worker:check` BUILD SUCCESSFUL
- `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors
- Manual: `./gradlew :backend:api:bootRun` + `curl http://localhost:8080/v3/api-docs | jq '.paths["/api/me/notifications"]'` shows both GET + PATCH registered
- Manual: `grep -rn "com.resend" backend/` returns hits only under `backend/worker/.../notification/email/` (boundary visual confirmation)
</verification>

<success_criteria>
- Idempotency: 2 dispatch runs for the same (tenant, day) produce exactly 1 outbound Resend call (`DigestIdempotencyTest`)
- Opt-out: digest_enabled=false produces zero dispatches (`DigestDispatchSchedulerTest` tenant B case)
- Zero-activity: zeroActivity=true payloads still dispatch with the "no activity yesterday" copy (`DigestComposerTest` + `ThymeleafDigestRendererTest`)
- Resend Idempotency-Key header asserted (`EmailNotificationChannelTest`)
- Channel substitution works: NoopNotificationChannel makes the dispatcher succeed without Resend (`DigestDispatchWithNoopChannelTest`)
- vi + en parity (`DigestMessageSourceParityTest`)
- Boundary: Resend SDK imports only in `worker.notification.email` (`ResendBoundaryArchTest`)
- DigestPayload channel-free (`DigestPayloadShapeArchTest`)
- Privacy: no sender / body / `to:` in worker logs (`DigestPrivacySweepTest`)
- Preferences API: GET + PATCH work; PATCH validates 0–23 hour; tenant-scoped
- Reaper promotes stuck-PENDING > PT30M to FAILED
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
