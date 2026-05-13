# Phase 5C: User Surface — Analytics & Daily Digest — Research

**Researched:** 2026-05-13
**Domain:** transactional email + Thymeleaf rendering + Postgres time-zone-aware scheduling + JDBC aggregation projections + TanStack Query analytics surface
**Confidence:** HIGH on libraries/versions, MEDIUM on architectural placement (planner discretion areas), **HIGH on one show-stopper schema gap** (see §0).

## Summary

CONTEXT.md and SPEC.md are locked exceptionally tightly (D-01 through D-25), so this research is implementation-fact-finding, not redebate. Two findings are load-bearing for the planner:

1. **`mail_message_observed` has NO `sender_email` column** (verified against Liquibase changeset `012-mail-message-observed-table.yaml` and `MailMessageObservedEntity.java`). D-18 Q3 ("top-3 senders by triaged-message count over `mail_message_observed.sender_email`") **cannot run as written** — the planner must pick a recovery path (add a column with future-only data, derive from `tenant_protected_sender_observation`, or take it from a new field that the Phase 2A ingestion path persists). This is the single highest-impact research finding and is fully scoped in §0 below.
2. Everything else in CONTEXT.md is implementable as locked. Libraries verified at current Maven Central versions: `com.resend:resend-java:4.13.0`, `net.javacrumbs.shedlock:shedlock-spring:7.7.0` (already pinned), Thymeleaf `3.1.5.RELEASE` (managed by `spring-boot-starter-thymeleaf:4.0.6`). Spring Boot 4 + virtual threads (`spring.threads.virtual.enabled=true`, already set in both `application.yml`) automatically wires `@Scheduled` onto `SimpleAsyncTaskScheduler`, so no `TaskScheduler` bean is required. Resend's `Idempotency-Key` HTTP header is verified to exist with a 24h TTL — sufficient for D-11.

**Primary recommendation:** Address §0 with the user before plan generation (this overrides locked D-18 Q3). For everything else, the planner can proceed using the recommendations below.

---

## 0. SHOW-STOPPER — `mail_message_observed.sender_email` does NOT exist

**Findings (HIGH confidence — verified against Liquibase changeset + JPA entity):**
- `mail_message_observed` columns: `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `history_id`, `label_ids text[]`, `internal_date`, `observed_at`. No `sender_email`, no `from`, no header columns.
- The `MailMessageObserved` integration event (`backend/core/.../gmail/event/MailMessageObserved.java`) also intentionally omits sender — the privacy posture keeps "From" out of the observation table to avoid leaking sender lists into the database that backs the privacy-sweep tests.
- The only table currently storing `sender_email` is `tenant_protected_sender_observation` (changeset 028), but only for senders that triggered the safety-net path — NOT every observed message. Using it for "top-3 senders by triaged-message count" would systematically bias toward protected senders only.
- `triage_audit` also lacks `sender_email` (changeset 025). No table answers Q3 as written.

**Why CONTEXT.md missed this:** the discussion-phase log lists `mail_message_observed.sender_email` as "Phase 2A schema" and "sanitized From"; the actual Phase 2A persisted record carries Gmail message-id metadata only. The privacy-sweep test deliberately verifies that `RAW_SENDER_EMAIL` does NOT survive into `triage_audit` rows, which confirms the same policy applied to ingestion writes.

**Recommended approaches for the planner (must surface to user before planning Q3):**
1. **Add `sender_email varchar(320)` to `mail_message_observed`** (new Liquibase changeset, NOT NULL after backfill OR nullable forever). Phase 2A code at the Gmail ingestion adapter knows the address — wiring it through is a small change. Privacy stays inside policy (sender is already owner-visible). **Recommended.** Top-3 senders only includes future data; the spec's "in this window" framing makes that acceptable for the first 7d after deploy.
2. **Derive top-3 senders from a join** through a sender-bearing table that is populated for every message. Currently no such table exists. Not viable without schema change.
3. **Use `tenant_protected_sender_observation.observation_count` as a proxy**. Biased to protected senders only; misleads the user. Reject.
4. **Redefine "top-3 senders" to mean "top-3 senders that triage actually acted on"** by joining `tenant_protected_sender_observation` with `triage_audit` over `tenant_id` + a new `gmail_message_id → sender_email` lookup. Still needs the lookup, so still requires schema change. Not simpler than (1).

**Recommendation:** option **(1)**. Planner ships the new column in the same migration wave as `tenants.time_zone` + `notification_preference` + `digest_delivery`, plus an additional task in Phase 2A's ingestion adapter to write `sender_email` going forward. Empty-state language in UI-SPEC ("No senders yet in this window.") already handles the first 7d gracefully.

**If the user rejects schema change:** descope Q3 from v1, ship 3 panels instead of 4 (volume, time saved, rule hits), and note the deferral in `Deferred Ideas`.

This must be resolved before the planner writes tasks.

---

## Project Constraints (from CLAUDE.md)

- Java 25 + Spring Boot 4.0.6 + Gradle 9.5.0 + Kotlin DSL + `libs.versions.toml` catalog — locked.
- Spring AI 2.0.0-M6 is the only LLM access; **not relevant to 5C** (no LLM calls in analytics or digest composition).
- **Do not use:** Lombok, WebFlux, `javax.*` (Jakarta-only), Spring WebFlux, Kafka/RabbitMQ in v1, GCP starters, stateless JWT, embedding store, raw HTTP LLM calls, polling Gmail, `pgp_sym_encrypt`, Gradle Node plugin.
- Backend naming style: domain-revealing names (`request` not `req`, etc.). Established acronyms (`ID`, `DTO`, `JPA`, `URL`, `HTTP`) are OK.
- Frontend uses `frontend-design` skill MANDATORY before any UI code.
- Vietnamese-first communication; technical terms in English.
- Convention 10: i18n source-of-truth is per-feature `messages.ts`, NOT direct edits to `i18n/messages/{vi,en}.json` (those are generated by `pnpm i18n:build`).
- `triage_audit.tenant_id` already has a `deleteCascade: true` FK to `tenants(id)` (changeset 025) — mirror this for new tables (D-16).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `/analytics` page render (4 panels, window chips, vi/en) | Frontend Server (Next.js RSC + Client) | — | Standard Phase 5A precedent: protected pages are RSC pages with client islands for interactivity (`Tabs` URL search-param sync). |
| `GET /api/analytics/summary?window=` | API / Backend (`backend/api`) | DB (Postgres JDBC) | Thin controller + service-owned `@Transactional(readOnly=true)` per Convention 1. |
| 4 aggregation queries (Q1-Q4) | DB (Postgres JDBC) | API (JdbcTemplate read-side service) | CQRS-lite: JPA for writes, JDBC for reads — D-18 mirrors `AuditLogQueryService`. |
| Hourly digest scheduler | `backend/worker` | DB (Postgres for ShedLock + claim row) | Worker process owns all `@Scheduled` jobs (existing pattern: 5 schedulers in `backend/worker/billing` + `worker/triage`). |
| Local-hour fanout (`AT TIME ZONE`) | DB (Postgres 17 IANA tzdata) | App layer (`ZoneId`/`ZonedDateTime` for window math only) | Postgres time-zone handling is the right tier — DST-safe via shipped tzdata; D-06 already locked. |
| `digest_delivery` PENDING insert (idempotency dedupe) | DB (UNIQUE constraint) | Worker (catches `DataIntegrityViolationException` SQLState `23505`) | UNIQUE-constraint-as-dedupe is the established Zero Mail pattern (billing top-up `code` UNIQUE precedent). |
| Resend HTTP dispatch | `backend/worker` (preferred) | — | The channel adapter lives where the scheduler lives so Resend SDK and ArchUnit boundary stay in one process. |
| Thymeleaf render (HTML + TXT) | Same module as `EmailNotificationChannel` | — | Templates resolve from classpath relative to that module's `src/main/resources/`. |
| `digest_enabled` / `digest_send_hour_local` reads/writes | API (`/api/me/notifications` controller) | `backend/core` notification service | Settings UI talks to the API process, not the worker. Service lives in `core.notification.usecases` so both API and worker can read it. |
| `tenants.time_zone` column | `backend/core` (entity host) | API + worker (both read) | Tenant-global property; `TenantEntity` is the owner. |
| Default-row insertion at OAuth provisioning | `backend/core/account/usecases/OAuthProvisioningService` | DB | D-17: same `PROPAGATION_REQUIRED` transaction as user + tenant + gmail-connection. |

---

## Standard Stack

### Core (verified via Maven Central `maven-metadata.xml`, 2026-05-13)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.resend:resend-java` | **4.13.0** | Transactional email send | Only first-party Java SDK for Resend; `release` tag dated 2026-04-15. `[VERIFIED: repo1.maven.org]` |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | **4.0.6** (Boot-managed) | Thymeleaf engine bootstrap | Brings Thymeleaf `3.1.5.RELEASE`; Spring Boot 4.0.6 is the current GA per `STACK.md`. `[VERIFIED: repo1.maven.org]` |
| `org.thymeleaf:thymeleaf` | **3.1.5.RELEASE** | Template engine | TEXT mode (`[(${...})]` inlining + `[# th:each]` blocks) supported for plaintext sibling templates. `[CITED: github.com/thymeleaf/thymeleaf-docs/3.1]` |
| `net.javacrumbs.shedlock:shedlock-spring` | **7.7.0** | Distributed scheduler lock | Already in project — `ShedLockConfig` wires `JdbcTemplateLockProvider.usingDbTime()`. No upgrade needed. `[VERIFIED: repo1.maven.org]` |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **3.0.3** | OpenAPI spec generation | Already in `libs.versions.toml`; auto-discovers new `@RestController`s without config. `[VERIFIED: gradle/libs.versions.toml]` |

### Supporting (already present — no install needed)

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Data JDBC (`JdbcTemplate`) | Boot-managed | D-18 read-side for analytics queries; mirrors `AuditLogQueryService`. |
| Spring Modulith 2.0.x (pinned snapshot) | repo-pinned | Optional `@ApplicationModuleListener` for D-12 dispatch transport. |
| Micrometer (`io.micrometer.core.instrument`) | Boot-managed | `Counter` for digest dispatched / failed / retried; existing precedent in `TriagePendingReaperJob`. |
| ShadCN/UI primitives (`tabs`, `card`, `skeleton`, `table`, `switch`, `select`, `separator`, `badge`, `button`, `sonner`, `tooltip`) | already installed | All 11 primitives verified present in `apps/web/components/ui/`. **Zero new installs.** `[VERIFIED: ls apps/web/components/ui/]` |
| TanStack Query v5 | Boot-managed | `useAnalyticsSummary(window)` hook. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `com.resend:resend-java:4.13.0` | Raw HTTP via Java 11 `HttpClient` against Resend REST | Avoids one dependency, but loses typed model + Idempotency-Key convenience. **Reject** — D-01 locked Resend, D-03 locks SDK isolation. |
| `4.13.0` | `4.14.1` (more recent on Maven Central) | 4.14.x is published but not yet `<release>` in `maven-metadata.xml` (stays `4.13.0`). Stick with the registered release. |
| Thymeleaf TEXT mode for plaintext | Generate plaintext manually from `MessageSource` keys + StringBuilder | Costs more code, allows divergence between HTML and TXT bundles. **Reject** — D-02 + UI-SPEC C lock single-`MessageSource` truth. |
| `fixedRate=3_600_000L` (current 5 schedulers) | `cron = "0 ? * * * *"` | Cron predicts hour-of-day exactly; `fixedRate` drifts by JVM start time. For an hour-aligned digest, cron is the correct fit. **Recommend** cron for D-05. |

**Installation (Gradle Kotlin DSL `backend/worker/build.gradle.kts`):**

Add to `libs.versions.toml`:

```toml
[versions]
resend = "4.13.0"

[libraries]
resend-java = { module = "com.resend:resend-java", version.ref = "resend" }
```

Add to `backend/worker/build.gradle.kts` (and `backend/core/build.gradle.kts` if the email channel lives in core — see §12):

```kotlin
implementation(libs.resend.java)
implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
```

---

## Architecture Patterns

### System Architecture Diagram

```
                                    apps/web (Next.js RSC + Client)
                                              │
                       ┌──────────────────────┼──────────────────────┐
                       │                      │                      │
                       ▼                      ▼                      ▼
            GET /analytics            GET /settings          (digest CTA URL)
                       │                      │                      │
                       └──────────────────────┼──────────────────────┘
                                              │  (typed OpenAPI client)
                                              ▼
                              backend/api  (Spring MVC + virtual threads)
                                              │
                       ┌──────────────────────┼──────────────────────┐
                       │                      │                      │
                       ▼                      ▼                      ▼
        AnalyticsController         NotificationsPreferencesCtrl    (existing)
                       │                      │
                       ▼                      ▼
        AnalyticsSummaryQueryService    NotificationPreferenceService
        (JDBC, readOnly=true)           (JPA)
                       │                      │
                       └──────────┬───────────┘
                                  ▼
                            PostgreSQL 17
                                  ▲
                                  │ (claim PENDING, dispatch SENT/FAILED)
                                  │
                       ┌──────────┴───────────┐
                       │                      │
                       ▼                      ▼
        DigestDispatchScheduler         DigestPendingReaperJob
        @Scheduled(cron) +              @Scheduled(fixedDelay) +
        @SchedulerLock                  @SchedulerLock
                       │                      │
                       └──────────┬───────────┘
                                  ▼
                       backend/worker (Spring + virtual threads)
                                  │
                                  ▼
                       EmailNotificationChannel
                       (Thymeleaf render + Resend SDK send)
                                  │
                                  ▼
                       Resend HTTPS  (Idempotency-Key: ${tenantId}:${digestDayLocal})
```

### Component Responsibilities

| Component | Type | Module | Responsibility |
|-----------|------|--------|----------------|
| `AnalyticsController` | `@RestController` | `backend/api/controllers/analytics/` | Map `?window=` → service, return `AnalyticsSummaryResponse.from(projection)`. |
| `AnalyticsSummaryQueryService` | `@Service` | `backend/core/analytics/projection/` (recommended — see §12) | 4 sequential JDBC queries, `@Transactional(readOnly=true)`. |
| `NotificationPreferencesController` | `@RestController` | `backend/api/controllers/notifications/` | `GET /api/me/notifications`, `PATCH /api/me/notifications`. |
| `NotificationPreferencesService` | `@Service` | `backend/core/notification/usecases/` | JPA read + write of `notification_preference`; `@Transactional`. |
| `NotificationPreferenceEntity` | JPA `class` | `backend/core/notification/persistence/` | Composite PK `(tenant_id, channel)`. Extends `AbstractTenantOwnedEntity` (auto `tenant_id` + auditing). |
| `DigestDeliveryEntity` | JPA `class` | `backend/core/notification/persistence/` | UNIQUE `(tenant_id, digest_day_local)`. Status FSM `PENDING|SENT|FAILED` as `IdentifiedEnum`. |
| `ChannelType` | `enum implements IdentifiedEnum` | `backend/core/notification/domain/` | v1 single id `"email"`. See §12 for cardinality decision. |
| `DigestPayload` | record | `backend/core/notification/domain/` | Channel-free: `locale`, `totals`, `topSenders`, `topRules`, `ctaUrl`, `optOutUrl`. No `htmlBody`, no `mimeType`. |
| `NotificationChannel` | interface | `backend/core/notification/usecases/` | `dispatch(DigestPayload)`. |
| `EmailNotificationChannel` | `@Component` | `backend/worker/notification/email/` (recommended — see §12) | Implements `NotificationChannel`; renders templates via Thymeleaf; calls Resend. |
| `DigestDispatchScheduler` | `@Component` | `backend/worker/notification/` | `@Scheduled(cron="0 5 * * * *")` + `@SchedulerLock`; fanout query → for-each tenant → claim+dispatch+settle. |
| `DigestPendingReaperJob` | `@Component` | `backend/worker/notification/` | `@Scheduled(fixedDelay=300_000L)` + `@SchedulerLock`; promotes stuck PENDING → FAILED past `PT30M`. |

### Recommended Project Structure (delta only)

```
backend/core/src/main/java/com/zeromail/core/
├── analytics/                              # NEW — see §12 recommendation
│   ├── projection/
│   │   ├── AnalyticsSummaryQueryService.java
│   │   ├── AnalyticsSummaryProjection.java
│   │   ├── TopSenderProjection.java
│   │   ├── RuleHitProjection.java
│   │   └── TimeSavedWeights.java           # 10/30/180 constants
│   └── package-info.java                   # Modulith @ApplicationModule + allowedDependencies
└── notification/                            # NEW
    ├── domain/
    │   ├── ChannelType.java                 # IdentifiedEnum {"email"} (v1)
    │   ├── DigestDeliveryStatus.java        # IdentifiedEnum {PENDING, SENT, FAILED}
    │   └── DigestPayload.java               # record — channel-free
    ├── usecases/
    │   ├── NotificationChannel.java         # interface
    │   ├── NotificationPreferenceService.java
    │   └── DigestComposer.java              # builds DigestPayload from AnalyticsSummary
    ├── persistence/
    │   ├── NotificationPreferenceEntity.java
    │   ├── NotificationPreferenceRepository.java
    │   ├── DigestDeliveryEntity.java
    │   └── DigestDeliveryRepository.java
    └── package-info.java

backend/worker/src/main/java/com/zeromail/worker/
└── notification/
    ├── DigestDispatchScheduler.java
    ├── DigestPendingReaperJob.java
    └── email/                              # ArchUnit-isolated; Resend SDK lives only here
        ├── EmailNotificationChannel.java
        ├── ResendEmailGateway.java         # thin wrapper around Resend SDK
        └── ThymeleafDigestRenderer.java    # HTML+TXT renderer

backend/worker/src/main/resources/
└── email-templates/
    └── digest/
        ├── digest.html.thymeleaf            # locale-driven via MessageSource
        └── digest.txt.thymeleaf

backend/api/src/main/java/com/zeromail/api/controllers/
├── analytics/
│   └── AnalyticsController.java
└── notifications/
    └── NotificationPreferencesController.java

backend/api/src/main/java/com/zeromail/api/dto/
├── analytics/
│   └── AnalyticsSummaryResponse.java        # record with from(...) factory
└── notifications/
    ├── NotificationPreferencesResponse.java
    └── NotificationPreferencesRequest.java

apps/web/features/
├── analytics/
│   ├── api/analytics-api.ts
│   ├── query-keys.ts
│   ├── hooks/useAnalyticsSummary.ts
│   ├── components/AnalyticsPageClient.tsx
│   ├── components/VolumePanel.tsx
│   ├── components/TimeSavedPanel.tsx
│   ├── components/TopSendersPanel.tsx
│   ├── components/RuleHitsPanel.tsx
│   └── messages.ts                          # per-feature i18n source
└── notifications/
    ├── api/notifications-api.ts
    ├── query-keys.ts
    ├── hooks/useNotificationPreferences.ts
    ├── hooks/useUpdateNotificationPreferences.ts
    ├── components/NotificationsSection.tsx
    └── messages.ts
```

### Pattern 1: 4-query JDBC read-side service (template `AuditLogQueryService`)

```java
// Source: existing backend/core/.../triage/projection/AuditLogQueryService.java pattern
@Service
public class AnalyticsSummaryQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsSummaryQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryProjection summarize(UUID tenantId, Duration window) {
        Instant windowStart = Instant.now().minus(window);
        long volumeObserved = queryVolumeObserved(tenantId, windowStart);
        VolumeApplied appliedBreakdown = queryAppliedByActionType(tenantId, windowStart);
        long timeSavedSeconds = TimeSavedWeights.compute(appliedBreakdown);
        List<TopSenderProjection> topSenders = queryTopSenders(tenantId, windowStart);
        List<RuleHitProjection> ruleHits = queryRuleHits(tenantId, windowStart);
        return new AnalyticsSummaryProjection(...);
    }
}
```

### Pattern 2: ShedLock-protected scheduler (template `BillingIntentExpirySweeper`)

```java
// Source: existing backend/worker/billing/BillingIntentExpirySweeper.java
@Component
public class DigestDispatchScheduler {

    @Scheduled(cron = "0 5 * * * *")               // hh:05 every hour (planner picks offset)
    @SchedulerLock(
            name = "digestDispatchScheduler",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT15M")
    public void scheduledDispatch() {
        dispatch();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void dispatch() {
        LockAssert.assertLocked();                  // catches AOP misconfiguration early
        List<UUID> dueTenants = preferenceRepository.findTenantsDueForDigestNow();
        for (UUID tenantId : dueTenants) { dispatchOne(tenantId); }
    }
}
```

### Pattern 3: UNIQUE-constraint-as-dedupe

```java
// Source: billing top-up `code` UNIQUE precedent
public boolean claimPending(UUID tenantId, LocalDate digestDayLocal) {
    try {
        jdbcTemplate.update("""
            INSERT INTO digest_delivery
                (id, tenant_id, digest_day_local, status, attempt_count, created_at)
            VALUES (?, ?, ?, 'PENDING', 1, now())
            """, UUID.randomUUID(), tenantId, digestDayLocal);
        return true;
    } catch (DataIntegrityViolationException duplicateKey) {
        // SQLState 23505 — row already exists; either SENT or in flight.
        log.info("event=digest_already_claimed tenantId={} digestDay={}",
                tenantId, digestDayLocal);
        return false;
    }
}
```

### Pattern 4: Resend Java SDK call (CONFIRMED via Context7)

```java
// Source: /resend/resend-java README
Resend resend = new Resend(apiKey);
CreateEmailOptions emailOptions = CreateEmailOptions.builder()
        .from("Zero Mail <notifications@zero-mail.app>")
        .to(userEmail)
        .subject(subject)                                   // from MessageSource
        .html(renderedHtml)                                 // Thymeleaf
        .text(renderedPlaintext)                            // Thymeleaf TEXT mode
        .addHeader("Idempotency-Key", tenantId + ":" + digestDayLocal)
        .addTag(new Tag("category", "digest"))              // optional, for Resend dashboard
        .build();
try {
    CreateEmailResponse response = resend.emails().send(emailOptions);
    return Result.success(response.getId());
} catch (ResendException resendException) {
    int statusCode = resendException.getStatusCode();
    return Result.failure(statusCode, resendException.getMessage());
}
```

### Anti-Patterns to Avoid

- **Don't import Resend SDK from anywhere except the one isolated package.** Add a new `ResendBoundaryArchTest` mirroring `LlmGatewayBoundaryTest.spring_ai_only_in_gateway_springai` (§8 below).
- **Don't put `htmlBody` / `mimeType` / Resend-specific fields on `DigestPayload`.** It defeats D-04.
- **Don't render Thymeleaf inside the scheduler.** Render inside `EmailNotificationChannel` only — the scheduler hands a `DigestPayload` and never sees email-specific content.
- **Don't use `LocaleContextHolder` to switch locale.** Pass a `Locale` argument explicitly into the renderer so vi/en is per-tenant deterministic regardless of HTTP context.
- **Don't log the `to:` address.** Per Convention 5 — `event=digest_dispatched tenantId={} digestDay={}` only; the actual email address is in Resend's API call payload, never in logs.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Distributed scheduler locking | DB-row-as-mutex with manual TTL | `@SchedulerLock` + ShedLock 7.7.0 (already wired) | Crash-safe via `lockAtMostFor`; clock-coupled via `usingDbTime()`; ArchUnit ban on hand-rolled mutexes makes review fail otherwise. |
| Email dedupe | App-side hashmap, Redis SETNX, or in-memory tracker | Postgres `UNIQUE(tenant_id, digest_day_local)` + Resend `Idempotency-Key` (24h TTL) | Two defenses, both durable; Redis path violates D-13. |
| Tenant-local-time math at fanout | Java `ZonedDateTime` per tenant in app | Postgres `EXTRACT(HOUR FROM (now() AT TIME ZONE tz))` | Postgres ships IANA tzdata; DST-safe; one query selects all due tenants. App `ZonedDateTime` is still used for window-bound math inside the digest body (D-06). |
| HTML email layout | React Email port to Java, raw String templating | Thymeleaf 3.1.5 with `MessageSource` + TEXT mode sibling | Thymeleaf is Boot-managed; one `MessageSource` keyspace covers both HTML and TXT. |
| CSS inlining | Manual `<style>` blocks | Inline `style="..."` attributes directly in template (Gmail's inliner is unreliable per UI-SPEC) | UI-SPEC C explicitly bans `<style>` blocks. |
| HTTP retry / backoff | Hand-rolled `try / Thread.sleep(...)` loop | Reaper job + next-tick re-attempt + Resend's own 24h Idempotency-Key TTL | Aligns with `TriagePendingReaperJob` precedent; no in-thread blocking on virtual-thread carriers. |

**Key insight:** the digest pipeline has FOUR layers of crash safety stacked: (1) ShedLock prevents two workers running the same tick; (2) Postgres UNIQUE prevents two ticks claiming the same `(tenant, day)`; (3) `lockAtMostFor` releases the lock after worker death; (4) Resend Idempotency-Key dedupes server-side if the worker crashed between dispatch and status UPDATE. Don't add a fifth.

---

## Common Pitfalls

### Pitfall 1: Spring Boot 4 + Jackson 3 namespace drift

**What goes wrong:** importing `tools.jackson.annotation.JsonCreator` (does not exist) when migrating from Boot 3.
**Why it happens:** Boot 4 moved Jackson core to `tools.jackson.*` but kept annotations on `com.fasterxml.jackson.annotation.*`. CLAUDE.md hard-warns on this.
**How to avoid:** `AnalyticsSummaryResponse` and `NotificationPreferencesResponse` records do not need Jackson annotations at all — Spring MVC's default record serialization works. If a custom serializer is ever needed, verify the package via Context7 before importing.
**Warning signs:** compile errors saying "package `tools.jackson.annotation` does not exist."

### Pitfall 2: Virtual threads + `@Scheduled` keep-alive

**What goes wrong:** worker process exits silently because all virtual threads are daemon threads.
**Why it happens:** Boot 4 `spring.threads.virtual.enabled=true` makes scheduled tasks run on virtual threads, which are daemons.
**How to avoid:** verify `spring.main.keep-alive=true` is set in `backend/worker/application.yml`. **TODO for planner:** confirm whether it is already set; current snippet does not show it. If not present, this is a new line. `[VERIFIED: Spring Boot 4.0.3 docs via Context7]`
**Warning signs:** worker logs `Started ZeroMailWorkerApplication...` then exits immediately.

### Pitfall 3: ShedLock SpEL injection with `lockAtMostFor`

**What goes wrong:** typo in cron format (Spring 6-field vs Quartz 7-field cron) makes job never fire.
**Why it happens:** Spring's `@Scheduled` uses 6-field cron (`second minute hour day month day-of-week`); ShedLock examples online sometimes show 5-field.
**How to avoid:** use the existing format from `BillingIntentExpirySweeper`. For "every hour at minute 5": `cron = "0 5 * * * *"`. Also call `LockAssert.assertLocked()` first line inside the locked method — catches AOP misconfiguration immediately.

### Pitfall 4: Thymeleaf web auto-config bleeding into worker

**What goes wrong:** `spring-boot-starter-thymeleaf` adds a `ThymeleafViewResolver` MVC bean that competes with Spring MVC's existing setup in `backend/api`, causing test classpath conflicts.
**Why it happens:** the starter auto-registers a web view resolver if `spring-webmvc` is on the classpath.
**How to avoid:** keep Thymeleaf in `backend/worker` only (the worker doesn't serve HTTP responses, so the view resolver bean is harmless there). If templates ever need to live in `backend/core`, exclude `ThymeleafAutoConfiguration` and define a standalone `TemplateEngine` bean explicitly.

### Pitfall 5: DST edge cases (`Asia/Ho_Chi_Minh` is DST-free, but planner should test Europe)

**What goes wrong:** at the autumn DST shift, the local hour "02:00" happens twice; at the spring shift, it doesn't happen at all. A tenant with `digest_send_hour_local = 2` and `time_zone = "Europe/Berlin"` would either get 0 or 2 digests on those days.
**Why it happens:** `EXTRACT(HOUR FROM (now() AT TIME ZONE 'Europe/Berlin'))` reports the wall-clock hour Postgres computes — at spring-forward, hour 2 is simply skipped (zero digests that day for hour=2 tenants); at autumn-fall-back, hour 2 happens twice but the UNIQUE constraint catches the second one (saved by D-09). **So fall-back is safe; spring-forward silently skips.**
**How to avoid:** for **v1 Vietnam beta**, this is moot (`Asia/Ho_Chi_Minh` has no DST). Document the spring-forward edge case so v2 multi-TZ rollout has a planned mitigation (e.g., always also check hour-1 if last-night had a spring-forward shift). Recommend a single unit test fixture for `Europe/Berlin` autumn-fall-back to verify the UNIQUE constraint catches the duplicate.

### Pitfall 6: Top-3 ties + alphabetical secondary sort

**What goes wrong:** if Q3/Q4 SQL says `ORDER BY count DESC LIMIT 3` without a secondary key, Postgres returns ties in physical-storage order — test fixtures flake when row order shifts.
**Why it happens:** Postgres makes no guarantee on tie order without an explicit tie-breaker.
**How to avoid:** D-22 already locked alphabetical secondary sort. Write SQL as `ORDER BY c DESC, sender_email ASC LIMIT 3` (and `rule_name_snapshot ASC` for Q4). Test fixtures depend on deterministic order.

### Pitfall 7: i18n bundle is generated

**What goes wrong:** planner asks executor to edit `apps/web/i18n/messages/en.json` and `vi.json` — change gets clobbered on next `pnpm build`.
**Why it happens:** Convention 10 — those JSONs are generated by `merge-feature-i18n.ts` from per-feature `messages.ts`.
**How to avoid:** new strings go in `apps/web/features/analytics/messages.ts` and `apps/web/features/notifications/messages.ts`. `pnpm i18n:build` then `pnpm i18n:check` validates. Backend digest template strings go in `backend/worker/src/main/resources/messages_vi.properties` + `messages_en.properties` (or the existing `MessageSource` config — planner verifies).

### Pitfall 8: `check-i18n.ts` `EN_SCAN_FILES` is hand-maintained

**What goes wrong:** new English prose lands in `features/analytics/components/*.tsx` and ships, because the scanner doesn't look at those files.
**Why it happens:** `EN_SCAN_FILES` is an explicit allowlist of files-to-scan, not a glob.
**How to avoid:** the planner MUST add every new `apps/web/features/analytics/components/*.tsx` and `apps/web/features/notifications/components/*.tsx` to `EN_SCAN_FILES` as a task in the planning. Also adjust if any new `app/(protected)/(app)/analytics/page.tsx` lands there.

---

## Code Examples

Verified against current files in this repo. **Read these before planning tasks.**

### `AuditLogQueryService` shape (template for `AnalyticsSummaryQueryService`)

```java
// Source: backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java
@Service
public class AuditLogQueryService {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional(readOnly = true)
    public AuditLogPage page(UUID tenantId, AuditLogPageQuery query) {
        // ...JDBC composition...
    }
}
```

### `BillingIntentExpirySweeper` shape (template for `DigestDispatchScheduler`)

```java
// Source: backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
@Component
public class BillingIntentExpirySweeper {
    @Scheduled(fixedRate = 3_600_000L)
    @SchedulerLock(name = "billingIntentExpirySweeper",
                   lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void scheduledSweep() { sweep(); }

    @Transactional(propagation = Propagation.REQUIRED)
    public void sweep() {
        int rowsExpired = intentRepository.expireStale(Instant.now());
        if (rowsExpired > 0) {
            log.info("event=billing_intent_expiry_sweep rowsExpired={}", rowsExpired);
        }
    }
}
```

### `TriagePendingReaperJob` shape (template for `DigestPendingReaperJob`)

```java
// Source: backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java
@Component
public class TriagePendingReaperJob {
    @Scheduled(fixedDelay = 300_000L)
    @SchedulerLock(name = "triagePendingReaper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduledReap() { reap(); }

    public int reap() {
        int totalProcessed = 0;
        int selectedCount;
        do {
            TriagePendingReaperBatch.ReaperBatchResult batchResult =
                    triagePendingReaperBatch.reapStuckPendingOnce(BATCH_LIMIT);
            selectedCount = batchResult.selectedCount();
            totalProcessed += batchResult.reapedCount();
        } while (selectedCount == BATCH_LIMIT);
        log.info("event=triage_pending_reaped tenantId=system totalProcessed={}", totalProcessed);
        return totalProcessed;
    }
}
```

### `LlmGatewayBoundaryTest` shape (template for `ResendBoundaryArchTest`)

```java
// Source: backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
@Test
void resend_sdk_only_in_notification_email() {
    JavaClasses importedClasses = importProductionClasses();
    noClasses()
            .that().resideOutsideOfPackage("..worker.notification.email..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.resend..")
            .because("NTF-01: Resend SDK imports MUST be confined to "
                    + "backend/worker/.../notification/email. "
                    + "EmailNotificationChannel is the single adapter; DigestPayload + "
                    + "DigestComposer + NotificationChannel are provider-free. NO EXEMPTION.")
            .check(importedClasses);
}
```

### `OAuthProvisioningService.provisionBundledOAuth` integration point for D-17

```java
// Source: backend/core/.../account/usecases/OAuthProvisioningService.java
// FIRST-LOGIN PATH (line ~150) — INSIDE the same bundledTransaction.executeWithoutResult block:
ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(() -> bundledTransaction.executeWithoutResult(_ -> {
            tenantService.createTenant(tenantId, email);          // (existing)
            // (existing user save, gmail upsert, onboarding advance)
            // ⬇ ADD HERE — D-17 hook ⬇
            // tenantService.setTimeZoneIfAbsent(tenantId, "Asia/Ho_Chi_Minh");
            // notificationPreferenceService.insertDefaults(tenantId, ChannelType.EMAIL,
            //         /* enabled */ true, /* hour */ 20);
        }));
```

The default-row insertion is two new method calls on services that should live in `core.tenant.usecases` and `core.notification.usecases` respectively. They both join the existing `PROPAGATION_REQUIRED` transaction (default propagation, since they're called from within an already-active TX).

---

## Runtime State Inventory

**Trigger reason:** Phase 5C is not a rename — but it does change OAuth provisioning behavior (D-17) and ADD new state to existing services. The relevant categories are:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Existing `tenants` rows have no `time_zone` (column doesn't exist yet) — the Liquibase changeset's `defaultValueComputed: 'Asia/Ho_Chi_Minh'` backfills atomically | Code edit only — the default applies on `ALTER TABLE ADD COLUMN` for all existing rows. |
| Stored data | Existing `tenants` have no row in `notification_preference` (table doesn't exist yet) — new tenants will get one from D-17; old tenants will NOT | Data migration: planner adds a Liquibase changeset (or one-time SQL) `INSERT INTO notification_preference (tenant_id, channel, digest_enabled, digest_send_hour_local) SELECT id, 'email', true, 20 FROM tenants WHERE id NOT IN (SELECT tenant_id FROM notification_preference WHERE channel='email')`. Must run AFTER the table is created. |
| Live service config | None — Resend API key is a secret env var, not in a UI. ShedLock table is Liquibase-managed (changeset 017). | None. |
| OS-registered state | None — `@Scheduled` schedulers are JVM-resident; no OS task scheduler entries. | None. |
| Secrets/env vars | `RESEND_API_KEY` will be NEW. Bound under `zero-mail.notification.email.resend.api-key` per Convention 9 (each runnable subproject owns its config; the property is worker-only since the API process never calls Resend). | Add to `backend/worker/.../application.yml` with `${RESEND_API_KEY:?RESEND_API_KEY env var is required}` defense-in-depth pattern; document in deployment runbook. |
| Build artifacts | None — new module is `notification`, existing modules are unaffected. | None. |

**Canonical question:** *After the migration runs and code ships, what runtime systems still have the old state?* → Old tenants without a `notification_preference` row. The backfill SQL above handles that in the same migration wave.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 17 with IANA tzdata | D-06 `AT TIME ZONE`, D-09 UNIQUE | ✓ | 17.6 self-hosted on VPS per STACK.md | — |
| Resend API account | Email dispatch | ✗ (must register at `resend.com`) | — | None for v1 — D-01 locks Resend. Free tier 3K/mo covers Vietnam beta. |
| Verified sending domain on Resend | Required for `from: notifications@zero-mail.app` | ✗ | — | Resend's `onboarding@resend.dev` testing sender (degrades trust). Planner needs to add domain-verification as a deploy-runbook step. |
| Java 25 JDK | Build + run | ✓ | 25 LTS | — |
| Spring Boot 4.0.6 | Existing | ✓ | 4.0.6 | — |
| ShedLock 7.7.0 | Distributed locking | ✓ | 7.7.0 already pinned | — |
| Thymeleaf 3.1.5 | Template render | ✓ (Boot-managed) | 3.1.5.RELEASE via starter 4.0.6 | — |

**Missing dependencies with no fallback:**
- Resend account + verified sending domain (`zero-mail.app` or similar) — production blocker, planner must surface as a non-code task in the deploy checklist.

**Missing dependencies with fallback:**
- None for code.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Mockito + AssertJ + Testcontainers Postgres (`PostgresContainerTest` base class — already used by `TriagePrivacySweepTest`) |
| Backend config file | Per-subproject `build.gradle.kts` |
| Frontend framework | Vitest + Playwright + jsdom |
| Frontend config | `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| Quick run command (backend) | `./gradlew :backend:core:test :backend:api:test :backend:worker:test --tests "*Analytics*" --tests "*Digest*" --tests "*Notification*"` |
| Quick run command (frontend) | `pnpm --filter apps/web test analytics notifications` |
| Full suite command | `./gradlew check && pnpm --filter apps/web check` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ANL-01 | `/analytics` renders 4 panels with `7d` default; window switch re-fetches | playwright | `pnpm --filter apps/web playwright test e2e/analytics.spec.ts` | ❌ Wave 0 |
| ANL-01 | 4 panels handle zero-data empty states without NaN | vitest | `pnpm --filter apps/web test -- AnalyticsPanels.test.tsx` | ❌ Wave 0 |
| ANL-02 | `GET /api/analytics/summary?window=7d|30d|90d` returns typed shape | integration (Spring MVC test) | `./gradlew :backend:api:test --tests AnalyticsControllerContractTest` | ❌ Wave 0 |
| ANL-02 | `AnalyticsSummaryQueryService` reads only `triage_audit` + `mail_message_observed` | grep-style boundary test | `./gradlew :backend:core:test --tests AnalyticsRepositoryContentBanTest` | ❌ Wave 0 |
| ANL-02 | Time-saved formula: N×10 + M×30 + K×180 | unit (fixture) | `./gradlew :backend:core:test --tests TimeSavedWeightsTest` | ❌ Wave 0 |
| ANL-02 | Reverted rows excluded from time-saved | unit (fixture) | `./gradlew :backend:core:test --tests AnalyticsSummaryQueryServiceTest` | ❌ Wave 0 |
| ANL-02 | Privacy sweep: no `sender_email`, `gmail_message_id`, prompts in logs | integration | `./gradlew :backend:core:test --tests AnalyticsPrivacySweepTest` | ❌ Wave 0 |
| ANL-03 | Digest job sends exactly one outbound per `(tenant, day)` | integration | `./gradlew :backend:worker:test --tests DigestDispatchSchedulerTest` | ❌ Wave 0 |
| ANL-03 | Re-running job for same `(tenant, day)` → at most one dispatch (idempotency) | integration | `./gradlew :backend:worker:test --tests DigestIdempotencyTest` | ❌ Wave 0 |
| ANL-03 | `digest_enabled=false` → no dispatch | integration | `./gradlew :backend:worker:test --tests DigestDispatchSchedulerTest` | ❌ Wave 0 |
| ANL-03 | Zero-activity day still dispatches with "no activity" body | integration | `./gradlew :backend:worker:test --tests DigestComposerTest` | ❌ Wave 0 |
| ANL-03 | HTML + TXT bodies render from same `MessageSource` keys | unit (snapshot) | `./gradlew :backend:worker:test --tests ThymeleafDigestRendererTest` | ❌ Wave 0 |
| ANL-03 | vi + en bodies parity (no missing keys) | i18n gate | `pnpm --filter apps/web i18n:check` + `./gradlew :backend:worker:test --tests DigestMessageSourceParityTest` | ❌ Wave 0 (frontend gate exists, backend new) |
| ANL-03 | Resend Idempotency-Key header carries `tenantId:digestDay` | integration (Resend mock) | `./gradlew :backend:worker:test --tests EmailNotificationChannelTest` | ❌ Wave 0 |
| ANL-03 | Privacy sweep: no email body / sender_email / `to:` in logs | integration | `./gradlew :backend:worker:test --tests DigestPrivacySweepTest` | ❌ Wave 0 |
| WEB-02 (analytics portion) | `/settings` Notifications subsection: opt-out + send-hour selector | playwright | `pnpm --filter apps/web playwright test e2e/settings-notifications.spec.ts` | ❌ Wave 0 |
| WEB-02 (analytics portion) | Optimistic update + toast on settings change | vitest | `pnpm --filter apps/web test -- NotificationsSection.test.tsx` | ❌ Wave 0 |
| D-03 | Resend SDK imports confined to `..worker.notification.email..` | ArchUnit | `./gradlew :backend:core:test --tests ResendBoundaryArchTest` | ❌ Wave 0 |
| D-04 | `DigestPayload` has no `htmlBody`/`mimeType`/email-specific fields | ArchUnit (no-classes-have-field) | `./gradlew :backend:core:test --tests DigestPayloadShapeArchTest` | ❌ Wave 0 |
| D-04 | `NoopNotificationChannel` makes the digest job succeed without dispatch | integration | `./gradlew :backend:worker:test --tests DigestDispatchWithNoopChannelTest` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** the corresponding focused test command from the table.
- **Per wave merge:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test` + `pnpm --filter apps/web check`.
- **Phase gate:** `./gradlew check && pnpm --filter apps/web check && pnpm --filter apps/web i18n:check` all green.

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/arch/ResendBoundaryArchTest.java` — mirrors `LlmGatewayBoundaryTest`.
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/DigestPayloadShapeArchTest.java` — ArchUnit "no fields named `htmlBody`/`mimeType`/`subject`" on `DigestPayload`.
- [ ] `backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsSummaryQueryServiceTest.java` — Testcontainers Postgres, seeded fixtures.
- [ ] `backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsRepositoryContentBanTest.java` — ArchUnit ban on `..mail_message_observed.body*` or similar.
- [ ] `backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsPrivacySweepTest.java` — mirrors `TriagePrivacySweepTest` (same `PostgresContainerTest` base, same `SensitiveMarkerScrubFilter`).
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java` — `@WebMvcTest` + MockMvc.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java` — Testcontainers + injected `Clock`.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/DigestIdempotencyTest.java` — double-run, assert ≤1 Resend SDK call.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/DigestComposerTest.java` — zero-activity branch.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/ThymeleafDigestRendererTest.java` — snapshot HTML + TXT vi + en.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/DigestMessageSourceParityTest.java` — fail-loud on missing key in vi or en bundle.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/email/EmailNotificationChannelTest.java` — mock Resend client, assert `Idempotency-Key` header.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/notification/DigestPrivacySweepTest.java` — `ListAppender` + `SensitiveMarkerScrubFilter`.
- [ ] `apps/web/features/analytics/__tests__/AnalyticsPanels.test.tsx` — empty-state + window-switch.
- [ ] `apps/web/features/notifications/__tests__/NotificationsSection.test.tsx` — optimistic toggle.
- [ ] `apps/web/e2e/analytics.spec.ts` — full window-switch flow.
- [ ] `apps/web/e2e/settings-notifications.spec.ts` — opt-out + send-hour persistence.
- [ ] Framework install: none — all infra (JUnit, Testcontainers, Vitest, Playwright) is present.

---

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (inherits) | Existing Spring Security OAuth2 Client + Spring Session — `/api/analytics/summary` and `/api/me/notifications` both require an authenticated session. No new auth surface in 5C. |
| V3 Session Management | yes (inherits) | Existing `HttpOnly`+`SameSite=Lax`+`Secure` session cookie, Redis-backed. No 5C change. |
| V4 Access Control | **yes (NEW)** | Both new endpoints MUST scope by `TenantContext.currentOrThrow()` and join WHERE `tenant_id = ?` in every JDBC query. `TenantIsolationArchTests` already enforces. |
| V5 Input Validation | **yes (NEW)** | `window` param: enum `{7d, 30d, 90d}` only; reject everything else with `400`. `digest_send_hour_local`: `@Min(0) @Max(23)` via Jakarta Validation. |
| V6 Cryptography | no | No new crypto — Resend API key transmitted to Resend over HTTPS by the SDK. |
| V7 Error Handling and Logging | **yes (NEW)** | Privacy logging format (Convention 5) — `Analytics*PrivacySweepTest` + `Digest*PrivacySweepTest` enforce. |
| V8 Data Protection | yes | No new sensitive data stored. `notification_preference` rows contain only `(tenant_id, channel, enabled, hour, timezone)`. `digest_delivery` contains `(tenant_id, day, status, channel, attempt_count, dispatched_at, failure_reason)` — `failure_reason` MUST NOT include email body bytes (only `4xx ${code}` opaque tokens). |
| V9 Communication | yes | Resend SDK uses HTTPS by default. |
| V12 API & Web Service | **yes (NEW)** | OpenAPI spec via existing springdoc 3.0.3 auto-discovers the new controllers — verify that `/v3/api-docs` includes them after first build; `pnpm generate:api` then regenerates the typed client. |

### Known Threat Patterns for Spring MVC + Postgres + Resend

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant analytics leak (tenant A sees tenant B's data) | Information disclosure | `TenantContext.currentOrThrow()` extracted at controller, passed as first JDBC parameter; ArchUnit `TenantIsolationArchTests` already enforces JdbcTemplate queries include `tenant_id = ?`. |
| Email-bomb (attacker triggers many digests to victim address) | DoS / Spam | NOT possible — digest dispatch is scheduler-driven, not user-triggered. Send hour is the tenant's OWN preference, gated by `digest_enabled`. |
| Resend API key leak in logs | Information disclosure | Resend SDK accepts the key via constructor `new Resend(apiKey)`; never log the `Resend` bean. Env var `RESEND_API_KEY` itself is in deploy secrets only. |
| SSRF via opt-out URL templating | Tampering | The opt-out URL is server-built from a fixed `${app.base-url}` config + `/settings?section=notifications` literal. No user input feeds into the URL. |
| Replay attack on `Idempotency-Key` | Replay | Resend enforces 24h TTL on idempotency keys; the dedupe is exactly the desired behavior, not an attack. |
| XSS in HTML email | Tampering | Thymeleaf escapes `${...}` by default (use `[(${...})]` in TEXT mode, which also escapes); rule names and sender_emails are the only user-influenceable variables, and both are sanitized at write time per Phase 4 + 2A invariants. |
| Privacy: digest body contains sender_email | (intentional disclosure to data owner) | Owner-visible by product design (UI-SPEC + CONTEXT.md D-25). Server logs MUST NOT include it — enforced by `Digest*PrivacySweepTest`. |

---

## Per-Focus-Area Findings (mirrors `<research_focus_areas>` 1–12)

### 1. Resend Java SDK (D-01, D-03, D-11)

**Findings:**
- **Latest stable version: `com.resend:resend-java:4.13.0`** (release date 2026-04-15 per Maven Central `maven-metadata.xml`). `[VERIFIED: repo1.maven.org/maven2/com/resend/resend-java/maven-metadata.xml]`
- **Constructor:** `new Resend(apiKey)` accepts the API key directly. The SDK does NOT read env vars itself; the caller is responsible. `[VERIFIED: Context7 /resend/resend-java]`
- **Send signature:** `resend.emails().send(CreateEmailOptions)` returning `CreateEmailResponse` (has `.getId()`). Builder supports `.from()`, `.to()`, `.subject()`, `.html()`, `.text()` (BOTH at once for dual-body), `.replyTo()`, `.addHeader(name, value)`, `.addTag(new Tag(name, value))`. `[VERIFIED: Context7]`
- **Idempotency-Key:** sent via `.addHeader("Idempotency-Key", "...")`. Resend keeps idempotency keys server-side for **24 hours**; max length 256 chars; must be unique per request. Compatible with D-11's `tenant_id:digest_day_local` form (under 256 chars: 36+1+10 = 47 chars). `[CITED: resend.com/docs/dashboard/emails/idempotency-keys]`
- **Error handling:** `ResendException.getStatusCode()` returns the HTTP status. **Recommended retry classification:**
  - `400`, `401`, `403`, `422`: permanent — log + mark `FAILED`, do not retry.
  - `404`: domain/email not found — permanent, mark `FAILED`.
  - `429`: rate-limited — transient, mark `FAILED` with `429` reason, reaper re-PENDINGs after `PT30M`. Resend default rate limit is **5 req/sec per team**; for Vietnam beta at <5K tenants × 1 digest/day = ~208 sends/hour peak, this is well below the cap. Free-tier daily quota is also subject to `x-resend-daily-quota` header.
  - `500`, `502`, `503`, `504`, network/timeout: transient — mark `FAILED`, reaper re-attempts.
- **Free tier:** 3K/month covers Vietnam-beta volume per CONTEXT.md D-01.

**Recommended approach for planner:**
- Add `libs.resend.java = "com.resend:resend-java:4.13.0"` to `libs.versions.toml`.
- Inject API key via `@ConfigurationProperties` nested under `zero-mail.notification.email.resend.apiKey`, bound to env `RESEND_API_KEY` in worker `application.yml` with `${RESEND_API_KEY:?RESEND_API_KEY env var is required}` defense.
- `ResendEmailGateway` is a thin `@Component` wrapper that creates the `Resend` client lazily (or once at construction) and exposes `send(DigestEmail) → Result<String, EmailFailure>`.
- Classify 4xx vs 5xx in `EmailNotificationChannel`; only 5xx/network/429 reach the reaper.

**Open questions:** none — fully scoped.

### 2. Thymeleaf 3.1.x for email rendering (D-02)

**Findings:**
- Spring Boot 4.0.6 manages Thymeleaf **3.1.5.RELEASE** via `spring-boot-starter-thymeleaf:4.0.6`. `[VERIFIED: repo1.maven.org]`
- **TEXT template mode** is the supported plaintext path: file extension `.txt` (or any non-html extension if the template resolver is configured); inlining via `[(${...})]` (escaped) or `[[${...}]]` (unescaped — DON'T use); blocks via `[# th:each="..."] ... [/]`. `[CITED: github.com/thymeleaf/thymeleaf-docs/3.1 — "Textual template modes"]`
- The Spring Email pattern is documented at `github.com/thymeleaf/thymeleaf-docs/blob/master/docs/articles/springmail.md` — uses `TemplateEngine` directly (not the MVC `ViewResolver`) with `ClassLoaderTemplateResolver` pointed at `email-templates/`. The `Context` object carries variables + Locale; `MessageSource` integration via Spring's `LocaleResolver` is replaceable by passing the Locale explicitly into `context.setLocale(locale)`.
- `#{key}` syntax fail-loud on missing messages: Spring's default `MessageSource` returns `??key_locale??` placeholder strings if the key is missing — this can leak into emails. Set `messageSource.setUseCodeAsDefaultMessage(false)` AND wrap the `TemplateEngine` execution to fail-fast on placeholder text in tests.

**Recommended approach for planner:**
- Define a dedicated `TemplateEngine` bean for digest rendering in `backend/worker/notification/email/`:
  ```java
  @Bean
  public TemplateEngine digestTemplateEngine(MessageSource messageSource) {
      ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
      htmlResolver.setPrefix("email-templates/digest/");
      htmlResolver.setSuffix(".html.thymeleaf");
      htmlResolver.setTemplateMode(TemplateMode.HTML);
      htmlResolver.setCharacterEncoding("UTF-8");
      ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
      textResolver.setPrefix("email-templates/digest/");
      textResolver.setSuffix(".txt.thymeleaf");
      textResolver.setTemplateMode(TemplateMode.TEXT);
      textResolver.setCharacterEncoding("UTF-8");
      SpringTemplateEngine engine = new SpringTemplateEngine();
      engine.addTemplateResolver(htmlResolver);
      engine.addTemplateResolver(textResolver);
      engine.setTemplateEngineMessageSource(messageSource);
      return engine;
  }
  ```
- Use ONE template per format (`digest.html.thymeleaf`, `digest.txt.thymeleaf`); locale is per-call via `context.setLocale(...)`. Avoids the "one template per locale × format" 4-file approach (UI-SPEC §C lists both as options — single-template with locale-driven `MessageSource` is cleaner).
- Use `th:fragment="header"`, `th:fragment="footer"`, `th:fragment="totals"` for HTML composition. Skip fragment composition in the TEXT template (TEXT mode supports `[# th:insert="..."]` but plain inline is simpler at this scope).
- Backend i18n bundle: `backend/worker/src/main/resources/i18n/digest_vi.properties` + `digest_en.properties`. Boot 4 picks them up via `MessageSource` auto-configuration if `spring.messages.basename=i18n/digest` is set (or wire a dedicated `ReloadableResourceBundleMessageSource` bean).

**Open questions:** none.

### 3. Spring `@Scheduled` + ShedLock 7.7.0 in Boot 4 (D-05, D-06, D-07)

**Findings:**
- Boot 4 with `spring.threads.virtual.enabled=true` (already set in both `backend/api` and `backend/worker` `application.yml`) auto-wires `SimpleAsyncTaskScheduler` running on **virtual threads**. `@Scheduled` methods run on virtual threads automatically — no `TaskScheduler` bean needed. `[VERIFIED: Context7 /spring-projects/spring-boot/v4.0.3]`
- **Daemon-thread keep-alive risk:** virtual threads are daemons. Boot 4 docs explicitly recommend `spring.main.keep-alive=true` to prevent the JVM exiting if all threads become daemons. The current `backend/worker/application.yml` does NOT show this property — planner MUST verify and add.
- `@SchedulerLock(name=..., lockAtLeastFor=..., lockAtMostFor=...)` works unchanged with Boot 4. Format string: ISO-8601 duration (`PT1M`, `PT15M`). `lockAtMostFor` is the safety net for worker death; should exceed worst-case dispatch duration. With Resend at 5 req/s, 5000 tenants ≈ 1000s ≈ 17 min — set `lockAtMostFor=PT20M` to be safe with margin.
- Cron format is Spring's 6-field (sec min hour day month dow). `@Scheduled(cron = "0 5 * * * *")` runs at minute 5 of every hour — picks a free slot among the existing 5 schedulers (Billing sweep is fixedRate, not hour-aligned; the new cron at `0 5 * * * *` doesn't collide).
- ShedLock's `usingDbTime()` (already in `ShedLockConfig`) couples lock timestamps to the Postgres clock, avoiding worker-clock drift. No change needed.

**Recommended approach for planner:**
- Cron form `@Scheduled(cron = "0 5 * * * *")` is correct for hour-aligned hourly digest (D-05). Use it instead of `fixedRate=3_600_000L` — the latter drifts by worker start time.
- `@SchedulerLock(name = "digestDispatchScheduler", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")`.
- Insert `LockAssert.assertLocked()` as the first line inside `dispatch()` — catches AOP misconfiguration in dev.
- **Add `spring.main.keep-alive: true` to `backend/worker/application.yml`** (one new line). Planner verifies it isn't already present.
- IANA tzdata: Postgres 17 ships with it (PG release notes); no separate install.

**Open questions:** none.

### 4. Postgres `AT TIME ZONE` semantics + DST (D-06)

**Findings:**
- The fanout query shape `WHERE EXTRACT(HOUR FROM (now() AT TIME ZONE np.time_zone))::int = np.digest_send_hour_local` is correct against Postgres 17 with bundled IANA tzdata.
- Vietnam (`Asia/Ho_Chi_Minh`) has had no DST since 1975. v1 Vietnam-beta is 100% safe.
- **Future DST edges (for v2):**
  - *Spring forward (e.g. `Europe/Berlin` last Sunday of March, 02:00 → 03:00):* the hour "02" doesn't exist on that day. A tenant with `digest_send_hour_local=2` gets **zero digests that day** because no tick will match. Mitigation: in v2, run the scheduler at `cron = "0 5,35 * * * *"` (twice per hour) and check `digest_send_hour_local IN (this_hour, this_hour-1)` if last-night had a spring-forward shift. **NOT in 5C scope**, but document as a v2 known issue.
  - *Fall back (e.g. `Europe/Berlin` last Sunday of October, 03:00 → 02:00):* the hour "02" happens twice. A tenant with `digest_send_hour_local=2` will be selected by both ticks. **The UNIQUE constraint on `digest_delivery (tenant_id, digest_day_local)` catches the second** — `digest_day_local` is the same `DATE` in both ticks, so the second `INSERT` fails with `23505`. Safe in both v1 and v2.

**Recommended approach for planner:**
- Implement the query as documented in D-06. Index recommendation: the partial index `WHERE digest_enabled=true AND channel='email'` (D-20) is enough; the `EXTRACT` is a function expression and won't use the partial index for the hour-match part, but the partial cuts the candidate set to opted-in tenants only (typically the vast majority), then the in-memory predicate is cheap.
- Add a `DigestDispatchSchedulerDstTest` integration test (Testcontainers Postgres) seeding two tenants in `Europe/Berlin` and running the dispatcher across a synthetic fall-back date; assert exactly one row in `digest_delivery` for each tenant.
- Document spring-forward edge in `CONTEXT.md` deferred ideas — v2 will need scheduler-frequency change.

**Open questions:** the resolution at scheduler granularity is hourly; what about a tenant who changes `digest_send_hour_local` mid-day? Their preference is read fresh on each tick. If they change from `20` to `21` at 20:30 local, they'll still get the 20:00-tick digest (already claimed) AND the 21:00-tick (new row, new `(tenant, day)` mismatch). **Bug:** they get two digests on transition day. Resolution: include `digest_send_hour_local` in the `digest_delivery` UNIQUE? — no, that allows abuse. Better: store `digest_day_local` as a `DATE` so any tick on the same calendar day collides. **Recommendation:** the planner makes `digest_day_local` the DATE in tenant-local time (not server-UTC date) — then any same-day tick collides, and the only way to get two digests on day X is if X is a fall-back DST day (already addressed).

### 5. JDBC read-side shape for the 4 analytics queries (D-18, D-20)

**Findings (verified against Liquibase changesets 012 + 025):**

`triage_audit` existing indexes:
- `pk: audit_id` (single-column UUID PK).
- `ux_triage_audit_idem` UNIQUE `(tenant_id, gmail_message_id, rule_id, action_type, args_hash) NULLS NOT DISTINCT` — irrelevant to analytics.
- `idx_triage_audit_tenant_message (tenant_id, gmail_message_id)` — irrelevant.
- `idx_triage_audit_tenant_decided_at (tenant_id, decided_at)` — **HITS Q1's `WHERE tenant_id=? AND decided_at >= ?` and Q2's same.** D-20 says "present, verify covers `applied_at IS NOT NULL` filters" — confirmed present; the index supports the predicate, but the actual filter `applied_at IS NOT NULL AND reverted_at IS NULL` is post-index. For typical tenant volume, the `(tenant_id, decided_at)` range scan returns small N (last 90d), so the filter is cheap.
- `idx_triage_audit_pending_last_attempt` partial on `last_attempt_at WHERE decision='PENDING'` — irrelevant to analytics.

`mail_message_observed` existing indexes:
- `pk_mail_message_observed (tenant_id, gmail_message_id)`.
- `idx_mail_message_observed_at_brin` BRIN on `observed_at` — **wrong shape for analytics**; BRIN is for ordered time-series scans where rows are physically ordered by `observed_at`. With multi-tenant interleaving, BRIN selectivity drops. For per-tenant time-window queries we want a btree.

**Missing indexes that D-20 requires:**
- ❌ `idx_mail_message_observed_tenant_observed_at btree (tenant_id, observed_at)` — **MISSING**, needed for Q1 volume scan.
- ❌ `idx_triage_audit_tenant_rule_decided (tenant_id, rule_name_snapshot, decided_at)` — **MISSING**, needed for Q4 GROUP BY without filesort.
- ❌ Q3 sender index — **N/A until §0 resolves** (`sender_email` column doesn't exist). Once added, recommend `(tenant_id, sender_email, observed_at)` btree (Q3 groups by sender, scans count, filters by window).

**Recommended approach for planner:**
- Liquibase changeset (new, sequence 035): create the three missing btree indexes above.
- Drop `idx_mail_message_observed_at_brin` only if storage pressure matters — leave in for now (BRIN is small).
- Q1 / Q2 / Q4 SQL exactly as D-18 (use `decided_at`, not `created_at` — Q4 docs say `decided_at >= ?`; double-check during execution that the existing `idx_triage_audit_tenant_decided_at` is used by `EXPLAIN ANALYZE`).
- Q4 `count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL)` — Postgres 17 supports filter-aggregates natively, no fallback to sort. With the new `(tenant_id, rule_name_snapshot, decided_at)` index, this is an index-only scan for the group-by + filter.
- For Q3 (once `sender_email` exists), `ORDER BY c DESC, sender_email ASC LIMIT 3` is deterministic. The covering index `(tenant_id, sender_email, observed_at)` supports the GROUP BY but doesn't avoid sorting the aggregate result — for top-3 of typically 20-200 unique senders per tenant per week, sort cost is trivial.

**Open questions:** none — all index recommendations are pre-determined.

### 6. Idempotency table + FSM (D-09, D-10, D-11)

**Findings:**
- `INSERT ... ON CONFLICT DO NOTHING` is the SQL-native alternative to catching `DataIntegrityViolationException`. Both work; choosing between them is a style call.
- Spring's `DataIntegrityViolationException` is the typed wrapper around SQLState `23505` (unique violation) — catchable cleanly. The existing billing pattern uses the exception-catch idiom.
- **Recommended exception-catch approach** (matches existing Zero Mail style, e.g. billing top-up `code`):
  - Pros: explicit; log line `event=digest_already_claimed tenantId={} digestDay={}` is natural; SQL is plain INSERT.
  - Cons: exceptions for control flow.
- Resend Idempotency-Key TTL is **24h** (confirmed). For a digest, "yesterday's day" is fixed at compose-time, so 24h coverage is exactly the worst-case retry window. Sufficient.
- Reaper grace period: `TriagePendingReaperJob` uses an abandoned threshold of **30 minutes** (per the comment in `025-triage-audit.yaml` and the job itself). Mirror this for `DigestPendingReaperJob` — `PT30M` for D-11.
- The unhandled gap is the (dispatch succeeded → UPDATE crashed) crash window. The Idempotency-Key is the ONLY mitigation. The reaper would never see that row (it's in `PENDING` → it can either go to FAILED on next tick OR get the UPDATE retried). Wait — if the worker crashed AFTER Resend acknowledged but BEFORE the local UPDATE, then:
  - Next tick fires (1 hour later), finds the row in PENDING, reaper hits PT30M threshold, promotes to FAILED.
  - Next-day tick attempts INSERT → succeeds (different `digest_day_local`).
  - But the original day is now `FAILED` — should we retry? Resend already sent the digest. Retrying would double-send EXCEPT Resend's 24h Idempotency-Key catches it. **Safe** — at most one digest reaches the user even in this worst case.

**Recommended approach for planner:**
- Use exception-catch idiom (matches house style).
- `digest_delivery` columns: `id uuid PK`, `tenant_id uuid FK ON DELETE CASCADE`, `digest_day_local date`, `status varchar(16)` (IdentifiedEnum), `channel varchar(16)` (`'email'` for v1), `attempt_count int`, `dispatched_at timestamptz`, `failure_reason varchar(255)`, `created_at`, `updated_at`, `version int`. UNIQUE `(tenant_id, digest_day_local)`.
- Reaper job mirrors `TriagePendingReaperJob` shape — paged batch of stuck-PENDING rows, promote to FAILED, no retry of dispatch in the reaper itself (next hourly tick retries via fresh INSERT if `digest_day_local` is still "today" — but typically the row is for "yesterday", so no retry; explicit retry job is separate, see §12 #5).

**Open questions:** retry policy for FAILED rows — see §12 #5.

### 7. Spring Modulith 2.0.x `@ApplicationModuleListener` (D-12, optional)

**Findings:**
- `@ApplicationModuleListener` = `@Async` + `@Transactional` + `@TransactionalEventListener` combined. Runs after the publishing transaction commits, in a new async transaction. `[VERIFIED: Context7 /spring-projects/spring-modulith]`
- Supports `id = "..."` for explicit listener identifier (per-listener dedupe in the `event_publication` table). If the listener bean is moved or renamed without a stable `id`, Modulith treats it as a new listener and won't dedupe old events.
- Event Publication Registry retries failed listeners via `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration)` — a small scheduled job in the same worker.

**Recommended approach for planner: DIRECT call, NOT Modulith.** Reasoning:
- The dispatch path is already idempotent at the DB layer (D-09 UNIQUE) and at the Resend layer (Idempotency-Key). Adding Modulith adds a third dedupe (per `event`+`listener-id`) but for transport, not business correctness.
- The scheduler is `@Transactional` already; after the `PENDING` row commits, we can directly call `notificationChannel.dispatch(payload)` and `UPDATE digest_delivery SET status='SENT'` in a follow-up transaction. The `@ApplicationModuleListener` "after-commit" property gives us nothing new here.
- Modulith's value is for cross-module decoupling; for this phase, the scheduler and the channel are both in `backend/worker/notification/` — same module, no boundary to bridge.

**Open questions:** if a future v2 adds a Zalo channel, would Modulith be useful? Yes — the scheduler would publish `DigestDueEvent(tenantId, day, locale)` and each channel module would listen independently. For v1, single channel, no benefit. **Defer to v2.**

### 8. Architectural boundaries (D-03, D-15, D-17)

**Findings:**
- The existing ArchUnit boundary test for Spring AI is `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java` (read above). It uses `noClasses().that().resideOutsideOfPackage("..core.llm.gateway.springai..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", ...).check(importedClasses)`. The boundary test imports production classes only (`ImportOption.DoNotIncludeTests`).
- ArchUnit dependency is already on the test classpath (used by 7+ existing tests in `backend/core/src/test/java/.../arch/`).
- D-15 next Liquibase changeset numbers: existing last is `031-thread-reply-status-resolved-index.yaml`. **Next sequence numbers for 5C: 032, 033, 034, 035** (planner assigns). The new files will live in `backend/core/src/main/resources/db/changelog/changes/`.
- D-17 atomicity: `OAuthProvisioningService.provisionBundledOAuth` (FIRST-LOGIN PATH lines ~110-160) opens `bundledTransaction.executeWithoutResult(...)` with `PROPAGATION_REQUIRED`. The new `tenantService.setTimeZoneIfAbsent(...)` and `notificationPreferenceService.insertDefaults(...)` calls land INSIDE that block, BEFORE `gmailConnectionService.upsert(...)`. Existing pattern: methods called from within the active TX inherit `PROPAGATION_REQUIRED` automatically because Spring's default for `@Transactional` is `REQUIRED`.

**Recommended approach for planner:**
- Create `backend/core/src/test/java/com/zeromail/core/arch/ResendBoundaryArchTest.java` mirroring `LlmGatewayBoundaryTest` shape verbatim (one test method `resend_sdk_only_in_notification_email`).
- Create `DigestPayloadShapeArchTest` (also under `core/arch/`) using ArchUnit's `fields()` DSL: `fields().that().areDeclaredIn(DigestPayload.class).should().notHaveName("htmlBody").andShould().notHaveName("mimeType").andShould().notHaveName("subject")`.
- Liquibase changesets: numbered `032-tenants-time-zone.yaml`, `033-notification-preference.yaml`, `034-digest-delivery.yaml`, `035-analytics-supporting-indexes.yaml` (or one consolidated `032-5c-notifications-and-analytics.yaml` if the planner prefers atomic phase migrations — the existing project uses one-per-concern).

**Open questions:** none.

### 9. Frontend (D-21, plus UI-SPEC.md alignment)

**Findings:**
- All shadcn primitives UI-SPEC needs are **already installed** in `apps/web/components/ui/`: `tabs`, `card`, `skeleton`, `table`, `switch`, `select`, `separator`, `badge`, `button`, `sonner`, `tooltip`. **Zero new `pnpm dlx shadcn@latest add ...` commands.** `[VERIFIED: ls apps/web/components/ui/]`
- TanStack Query key pattern from `features/triage/query-keys.ts`:
  ```ts
  export const triageKeys = {
    all: ['triage'] as const,
    auditLog: (filters?: Record<string, unknown>) =>
      filters ? ([...triageKeys.all, 'audit-log', filters] as const)
              : ([...triageKeys.all, 'audit-log'] as const),
    // ...
  } as const;
  ```
  Mirror as `analyticsKeys.summary(window)` and `notificationsKeys.preferences()`.
- Empty / loading / error: 5A's `(protected)/error.tsx` route-level error boundary already exists. Per-panel error granularity is NOT required (UI-SPEC §A "Error state" — route-level handles it).
- i18n namespace plan (high-level, to live in per-feature `messages.ts` files):
  - `analytics.page.title`, `analytics.page.subhead`, `analytics.window.7d|30d|90d`, `analytics.lastRefreshed`, `analytics.volume.{eyebrow,title,supplementary,tooltip,empty}`, `analytics.timeSaved.{eyebrow,title,tooltip}`, `analytics.topSenders.{eyebrow,title,empty}`, `analytics.ruleHits.{eyebrow,title,column.{rule,decisions,applied,reverted},empty}`, `analytics.loading`, `analytics.error.{heading,body,retry}`.
  - `digest.subject.{normal,zero}` (templated with `{count}`, `{minutes}`), `digest.preheader.{normal,zero}`, `digest.header.greeting.{normal,zero}`, `digest.totals.{messages,timeSaved}`, `digest.cta`, `digest.topSenders.{eyebrow}`, `digest.topRules.{eyebrow}`, `digest.zeroBody`, `digest.footer.{optOutPrompt,optOutLink,brand,legal}`.
  - `settings.notifications.{title,description,toggle.{label,helperOn,helperOff},sendHour.{label,helper},timeZone.{label,tooltip},toast.{savedTitle,errorTitle,retry}}`.
- `EN_SCAN_FILES`: planner MUST add (at least): `app/(protected)/(app)/analytics/page.tsx`, all `features/analytics/components/*.tsx`, all `features/notifications/components/*.tsx`.

**Recommended approach for planner:**
- TanStack Query key: `analyticsKeys.summary(window: '7d'|'30d'|'90d') = ['analytics', 'summary', window] as const`. `staleTime: 60_000`, `refetchOnWindowFocus: false`.
- Window chip `Tabs` value binds to `useSearchParams().get('window') ?? '7d'`; on change, `router.replace(?window=...)` with `{ scroll: false }`.
- Optimistic mutation for `Switch` and `Select`: copy 5A's pause-toggle recipe (`onMutate` → `setQueryData`; `onError` → rollback; `onSettled` → `invalidateQueries`).
- Two feature folders: `apps/web/features/analytics/` and `apps/web/features/notifications/`, each with `api/`, `query-keys.ts`, `hooks/`, `components/`, `messages.ts`. No barrel files (per Convention 8).

**Open questions:** none.

### 10. OpenAPI / typed client (apps/web auto-generated)

**Findings:**
- springdoc-openapi 3.0.3 is already in `gradle/libs.versions.toml` as `springdoc-openapi-starter-webmvc-ui`. It auto-discovers any `@RestController` and exposes the spec at `/v3/api-docs` (default path). No code change needed to surface `AnalyticsController` or `NotificationPreferencesController`.
- `pnpm generate:api` script lives at `apps/web/scripts/generate-api.ts` (referenced in `apps/web/package.json` line 10). It uses `openapi-typescript:7.13.0` to produce typed paths. Output location is the standard typed-client location (not inspected here — planner verifies).
- Boot 4 + springdoc 3.0.3: no known drift. springdoc 3.x is the Spring Boot 3+ line; verify the version pinning in `libs.versions.toml` against current springdoc releases if any deprecation warnings surface during build.

**Recommended approach for planner:**
- Last task in the backend wave: `./gradlew :backend:api:bootRun` briefly, GET `/v3/api-docs`, confirm both new endpoints appear.
- First task in the frontend wave: `pnpm generate:api` to refresh the typed-client. The frontend hooks then import from the regenerated client.

**Open questions:** none.

### 11. Test infrastructure

**Findings:**
- `TriagePrivacySweepTest` (already read above) is the model:
  - Extends `PostgresContainerTest` (Testcontainers base class).
  - Attaches a Logback `ListAppender` with a `SensitiveMarkerScrubFilter` to the root logger.
  - Defines a list of forbidden tokens (sentinel strings like `EMAIL_BODY_SENTINEL_04_08`); after the system runs, asserts none appear in: (a) audit DB rows, (b) captured log lines, (c) Micrometer metric tags.
  - Uses `@MockitoBean` to stub external clients (Gmail, LLM) and verify they get called correctly with sanitized values only.
- Worker scheduler tests can inject a `Clock` bean — Spring Boot has a `Clock` auto-config when `spring.task.scheduling.clock` is configured. Easier: inject a `Supplier<Instant>` into the scheduler and stub it in tests. **Project convention check needed** — current schedulers call `Instant.now()` directly, no injectable `Clock`. Recommend adding a `@Bean Supplier<Instant> currentInstant` in `backend/worker` config, defaulting to `Instant::now`, and stub it in tests.
- Email rendering snapshot test: assert on the rendered HTML/TXT strings. Use AssertJ `contains(...)` for specific tokens (subject, sender email, rule names, CTA URL, opt-out URL). DO NOT use full-document equality — Thymeleaf whitespace is hard to control.
- Playwright e2e auth helper: existing `apps/web/e2e/` (paths in `EN_SCAN_FILES` confirm Playwright tests for triage/billing/needs-reply exist). Planner reads `apps/web/e2e/setup/` (or equivalent) to find the auth-stub utility.

**Recommended approach for planner:**
- `Analytics*PrivacySweepTest` and `Digest*PrivacySweepTest`: copy-paste `TriagePrivacySweepTest` shape; replace sentinels to include `sender_email`, `digest_body_sentinel`, `to_address_sentinel`.
- For `DigestDispatchScheduler` deterministic tests, inject a `Supplier<Instant>` and provide a fixed instant in the test config.
- Thymeleaf renderer test: use the real `TemplateEngine` (not mocked) and a fixed `Context`; assert on the resulting String.
- `EmailNotificationChannelTest`: mock the `Resend` SDK using Mockito; capture the `CreateEmailOptions` argument; assert the `Idempotency-Key` header is present and correctly formatted.

**Open questions:** does the existing test infrastructure already inject a `Clock`? Planner verifies by searching for `Clock` bean in `backend/worker`. If absent, adding one is a small task.

### 12. Module / package layout open decisions

For each CONTEXT.md "Claude's Discretion" item, here is the planner-ready recommendation with one-line rationale:

| # | Decision | Recommendation | Rationale |
|---|----------|---------------|-----------|
| 1 | `EmailNotificationChannel` location: `backend/core/.../notification/channel/email/` vs `backend/worker/.../notification/...`? | **`backend/worker/notification/email/`** | The channel imports the Resend SDK; Resend should not appear in `backend/core` to keep the API process classpath free of the dispatch dependency. ArchUnit boundary is simplest when SDK lives only in worker. |
| 2 | Email templates location: `backend/worker/src/main/resources/email-templates/digest/{vi,en}/` vs `backend/core/.../notification/templates/...`? | **`backend/worker/src/main/resources/email-templates/digest/`** (single set, locale via MessageSource) | Co-located with the renderer; ClassLoaderTemplateResolver picks them up automatically. No vi/en split — one template per format, locale-driven. |
| 3 | `AnalyticsSummaryQueryService`: `core.triage.projection` vs new `core.analytics.projection`? | **New `core.analytics.projection`** | The query touches `triage_audit` AND `mail_message_observed`, spanning two existing Modulith modules (`triage` and `gmail`). Creating `core.analytics` with `allowedDependencies = {triage, gmail, shared.persistence}` gives a clean Modulith boundary and a place for the digest composer (`core.notification` depends on `core.analytics`, not on `core.triage` directly). |
| 4 | `ChannelType` enum cardinality: v1-only `{EMAIL}` vs forward-shaped `{EMAIL, ZALO, TELEGRAM, IN_APP}` with ArchUnit guard? | **v1-only `{EMAIL}` as `IdentifiedEnum`** with `id() = "email"` | YAGNI. Adding the other ids later is a 4-line change. Forward-shaping invites half-baked enum members with no implementation, which violates "no `null` returns" convention. ArchUnit guard against new implementations is unneeded when there's only one declared id. |
| 5 | Retry job: separate `@Scheduled` vs folded into the hourly dispatch tick? | **Folded into the hourly dispatch tick + separate reaper for stuck-PENDING** | The hourly dispatch already runs every hour; a FAILED row from yesterday is irrelevant by today (the `digest_day_local` is yesterday's date — won't re-INSERT). The reaper handles stuck-PENDING. A "retry-FAILED-from-today" job is only useful if FAILED happens late in the day with hours remaining — for that case, a 15-minute fixedDelay retry job inside `backend/worker/notification/` is cheap. **Recommend: separate `DigestRetryJob` with `fixedDelay = 900_000L` (15min) and `lockAtMostFor = PT10M`**, scoped to rows where `status='FAILED' AND digest_day_local = current_date(tenant_tz) AND attempt_count < 3`. |
| 6 | React Email-style component composition vs raw Thymeleaf | **Raw Thymeleaf with `th:fragment` for header / footer / CTA** | Locked by D-02 (Thymeleaf). Fragment composition for HTML is reasonable; the TEXT template stays inline (TEXT mode supports fragments but adds parse cost for no value at this scope). |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Java mail clients (Apache Commons Email, Spring's `JavaMailSender`) | Resend SDK with HTTP-native idempotency keys | Resend GA 2023; Java SDK from late 2023 | Dual-body (HTML + TXT) in one builder; first-party tagging; built-in suppression list; no SMTP socket babysitting. |
| Stateless JWT sessions for SSR sites | Server-issued signed session cookie + Redis-backed Spring Session | Project lock (CLAUDE.md) | Already in place from Phase 5A. Not changed by 5C. |
| Cron expressions in 5-field format | Spring 6-field cron format | Spring 4.0+ | `@Scheduled(cron = "0 5 * * * *")` — first field is seconds. |
| Platform threads for scheduled tasks | Virtual threads via `spring.threads.virtual.enabled=true` | Boot 3.2+ (production-ready in Boot 4) | Already enabled in both `backend/api` and `backend/worker`. `@Scheduled` runs on virtual threads automatically. |
| Postgres BRIN for time-range scans | Btree `(tenant_id, time_col)` for multi-tenant time ranges | Long-standing | BRIN is good for single-tenant ordered tables; multi-tenant ones need btree. The existing BRIN on `mail_message_observed.observed_at` is suboptimal — add btree per D-20. |

**Deprecated/outdated:**
- `JavaMailSender` for transactional email — fine for internal infra, but external delivery wants a provider with built-in suppression, retry, and idempotency (Resend / Postmark / SES). Project locked on Resend.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Resend SDK accepts API key via constructor only (not env var) | §1 | LOW — well-documented Java pattern. If the SDK also reads env, our property binding still works. |
| A2 | Resend `Idempotency-Key` HTTP header is supported in the Java SDK via `addHeader(...)` | §1 | LOW — confirmed for raw HTTP API; SDK header passthrough is the standard convention and the existing `addHeader` example confirms it works for arbitrary headers. |
| A3 | Boot 4 `spring.main.keep-alive=true` is required when `@Scheduled` + virtual threads are the only non-daemon work | §3 | MEDIUM — verified via Boot 4 docs. If missing, worker exits silently. Adding the property is a one-line fix. |
| A4 | The 30-minute reaper grace period is correct for digest dispatch (matches `TriagePendingReaperJob`) | §6 | LOW — duration is configurable, mirrors existing pattern. |
| A5 | `tenants` table has no `time_zone` column today | §0, §6 | NONE — VERIFIED by reading `TenantEntity.java` and changelog grep (no migration adds it). |
| A6 | `mail_message_observed` has NO `sender_email` column today | §0 | NONE — VERIFIED by reading changeset `012-mail-message-observed-table.yaml` and `MailMessageObservedEntity.java`. **This is the §0 show-stopper.** |
| A7 | i18n: `EN_SCAN_FILES` in `check-i18n.ts` is hand-maintained | §pitfall-8, §9 | NONE — VERIFIED by reading `apps/web/scripts/check-i18n.ts`. |
| A8 | Resend free tier is 3K/mo and 5 rps per team — adequate for v1 | §1 | LOW — verified via Resend docs. Vietnam-beta volume well below cap. |
| A9 | Postgres 17 ships with current IANA tzdata | §4 | LOW — standard Postgres release content. |
| A10 | Backend `MessageSource` can drive Thymeleaf TEXT-mode templates with `#{key}` resolution | §2 | LOW — standard Spring+Thymeleaf integration. |

---

## Open Questions

1. **`mail_message_observed.sender_email` — must resolve before planning.**
   - What we know: column doesn't exist (verified); D-18 Q3 references it.
   - What's unclear: user's tolerance for schema change vs Q3 descope.
   - Recommendation: surface §0 to user, prefer option (1) — add the column.

2. **Worker `spring.main.keep-alive=true` — verify presence.**
   - What we know: not seen in the worker `application.yml` snippet shown above.
   - What's unclear: whether the property is set elsewhere (profile-specific YAML, env var).
   - Recommendation: planner verifies and adds if missing.

3. **`Clock` / `Supplier<Instant>` injection convention.**
   - What we know: existing schedulers use `Instant.now()` directly.
   - What's unclear: any project-wide test convention for time stubbing.
   - Recommendation: planner adds `Supplier<Instant>` bean in `backend/worker/notification/` config for testable schedulers.

4. **Resend `from:` domain.**
   - What we know: must be a verified domain on Resend.
   - What's unclear: domain choice (`notifications@zero-mail.app`, `digest@zero-mail.app`, etc.).
   - Recommendation: planner picks a stable address; deploy runbook covers Resend domain verification.

5. **Tenant changes `digest_send_hour_local` mid-day → double digest on transition day?**
   - What we know: scheduler reads preference fresh each tick; UNIQUE is on `(tenant_id, digest_day_local)` where `digest_day_local` is a DATE.
   - What's unclear: is `digest_day_local` the tenant-local date OR the UTC date at moment of insert?
   - Recommendation: store the **tenant-local-day DATE** so same-day ticks collide regardless of which hour they fire. Add a unit test fixture for the mid-day preference change.

---

## Sources

### Primary (HIGH confidence)
- Context7 `/resend/resend-java` — Java SDK API surface (`emails().send()`, `CreateEmailOptions.builder()`, `addHeader`).
- Context7 `/llmstxt/resend_llms-full_txt` — Resend platform docs (Idempotency-Key header, 24h TTL, 256-char max, 5 rps rate limit, free-tier daily quota).
- Context7 `/thymeleaf/thymeleaf-docs` — TEXT mode templates, Spring email pattern.
- Context7 `/spring-projects/spring-boot/v4.0.3` — Boot 4 `@Scheduled` + virtual threads + `spring.main.keep-alive`.
- Context7 `/spring-projects/spring-modulith` — `@ApplicationModuleListener`, `event_publication` retry, `id =` parameter.
- Context7 `/lukas-krecan/shedlock` — `@SchedulerLock`, `LockAssert.assertLocked()`, cron+lockAtMostFor pattern.
- Maven Central `repo1.maven.org/maven2/com/resend/resend-java/maven-metadata.xml` — version 4.13.0 confirmed.
- Maven Central `repo1.maven.org/maven2/net/javacrumbs/shedlock/shedlock-spring/maven-metadata.xml` — version 7.7.0 confirmed.
- Maven Central `repo1.maven.org/maven2/org/thymeleaf/thymeleaf/maven-metadata.xml` — version 3.1.5.RELEASE confirmed.
- Repo file: `backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml` — confirms NO `sender_email` column.
- Repo file: `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java` — same.
- Repo file: `backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml` — existing `(tenant_id, decided_at)` btree index.
- Repo file: `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java` — template shape for D-18.
- Repo file: `backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java` — template for D-05.
- Repo file: `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java` — template for D-11 reaper.
- Repo file: `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java` — template for D-03 ArchUnit boundary.
- Repo file: `backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java` — template for D-25 sweep tests.
- Repo file: `backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java` — host for D-17 default-row insert.
- Repo file: `apps/web/scripts/check-i18n.ts` — `EN_SCAN_FILES` hand-maintained allowlist.
- Repo file: `apps/web/features/triage/query-keys.ts` and `apps/web/features/triage/messages.ts` — pattern templates for `features/analytics/` and `features/notifications/`.

### Secondary (MEDIUM confidence)
- `resend.com/docs/dashboard/emails/idempotency-keys` (cited via Context7) — verified 24h TTL and 256-char max.
- `resend.com/docs/api-reference/rate-limit` (cited via Context7) — verified 5 rps + free-tier daily quota header.

### Tertiary (LOW confidence)
- None.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions all verified at Maven Central; templates exist in-repo.
- Architecture: MEDIUM-HIGH — placement recommendations are reasoned but planner discretion; one major gap (§0).
- Pitfalls: HIGH — most pitfalls are direct doc-quotes or in-repo precedent.
- §0 schema gap: HIGH — verified by reading the actual files.

**Research date:** 2026-05-13
**Valid until:** 2026-06-13 (30 days; library versions move slowly enough that the recommendations hold).

## RESEARCH COMPLETE
