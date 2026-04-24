# Stack Research — Zero Mail

**Domain:** Multi-tenant AI Gmail-triage SaaS (Java 25 / Spring Boot 4 / Spring AI / Next.js)
**Researched:** 2026-04-24
**Overall confidence:** HIGH for backend core (Spring Boot 4, Spring AI, JDK 25, spring-cloud-gcp 8), MEDIUM for observability backend choice, MEDIUM for deployment target (multiple viable options).

---

## TL;DR — The Prescriptive Stack

- **JDK 25 LTS** (GA 2025-09-16) managed via Gradle toolchains.
- **Gradle 9.x** with **Kotlin DSL**, `libs.versions.toml` version catalog, multi-project (not composite) build.
- **Spring Boot 4.0.6** (current GA; 4.1.0-RC1 exists — stay on 4.0.x for production).
- **Spring Framework 7.0.7+**, **Spring Security 7**, **Jakarta Servlet 6.1**, **Jakarta Persistence 3.2**, **Jackson 3.0** (mandatory under Spring Boot 4).
- **Spring AI 1.0.5** via `spring-ai-starter-model-openai`, pointed at OpenRouter (`base-url: https://openrouter.ai/api/v1`). For Anthropic BYOK, add `spring-ai-starter-model-anthropic`.
- **spring-cloud-gcp 8.0.2** — first line that targets Spring Boot 4. Use `spring-cloud-gcp-starter-pubsub`. For Gmail watch, use **push subscriptions delivered to a plain Spring MVC controller** (not the pull subscriber) so scaling is handled by the HTTP layer.
- **PostgreSQL 17** + **Flyway 11** + **Spring Data JPA (Hibernate 7)** for aggregates, **Spring Data JDBC** for read-side and hot paths, **JSONB + jsonb_path_ops** for rule matchers, **pgcrypto / AES-GCM at app layer** for OAuth refresh-token encryption.
- **Redis 7.4** (Spring Data Redis + Lettuce) for rate limiting, idempotency keys, session store, and per-tenant ChatModel cache — NOT as a task queue.
- **Queue = Postgres-backed** (single `outbox` + `processing_job` table with `SKIP LOCKED`). No Kafka, no RabbitMQ in v1. Google Pub/Sub already handles ingress retries.
- **Next.js 15 (App Router) + React 19** in `apps/web`, **pnpm workspace + Turborepo**, **TanStack Query v5**, **shadcn/ui + Tailwind v4**, typed client via **OpenAPI codegen (`openapi-typescript` + `openapi-fetch`)** from Spring's `springdoc-openapi` output.
- **Auth**: Spring Security OAuth2 Client (Google), **server-issued signed session cookie** (not stateless JWT). Next.js sits behind the same origin; cookie is HttpOnly, SameSite=Lax.
- **Deploy**: **Google Cloud Run** (Pub/Sub push is natively OIDC-authenticated against Cloud Run URLs — zero glue). Cloud SQL Postgres, Memorystore Redis. Secret Manager for OAuth client secret + app-level encryption keys.
- **Container**: `eclipse-temurin:25-jre-noble` (production) built via Spring Boot's **CDS + AOT layered image** support; distroless for hardening if startup tuning matters more than debuggability.
- **Observability**: Micrometer + **OpenTelemetry Java agent (auto-instrumentation)**, push OTLP to **Grafana Cloud** (Tempo/Loki/Mimir) — cheapest and most vendor-neutral path in 2026. Spring AI's built-in `ChatModelObservationConvention` gives per-request LLM tracing out of the box.

---

## Recommended Stack

### Core Backend

| Technology | Version | Purpose | Why |
|---|---|---|---|
| Java | **25 LTS** (GA 2025-09-16) | Runtime | User-locked. LTS, virtual threads are stable, pattern matching + records + scoped values all mature. Spring Boot 4 supports Java 17–26. **HIGH**. |
| Spring Boot | **4.0.6** | App framework | User-locked. Current GA. Requires Spring Framework 7, Jakarta Servlet 6.1, Jackson 3. **HIGH** — verified via docs.spring.io. |
| Spring Framework | 7.0.7+ (managed by Boot BOM) | Core framework | Ambient — pulled by Boot 4. **HIGH**. |
| Spring Security | 7.0.x (Boot-managed) | AuthN/Z, OAuth2 client | Google Workspace OAuth2, CSRF, session. **HIGH**. |
| Spring Data JPA | 4.0.x (Boot-managed, Hibernate 7.x) | ORM for aggregates | Write-side (rules, users, tenants, audit). **HIGH**. |
| Spring Data JDBC | 4.0.x | Read-side & hot paths | For triage-log lookups, analytics. Avoids N+1 / lazy-init traps in hot path. **MEDIUM** — optional; JPA alone is fine for v1. |
| Spring AI | **1.0.5** | LLM orchestration | User-locked. `ChatClient`, advisors, tool calling, observation. OpenRouter works through the OpenAI module via `base-url` override. **HIGH**. |
| spring-cloud-gcp | **8.0.2** | GCP integration (Pub/Sub, Secret Manager) | **First line that supports Spring Boot 4** (v8.0.1 declared Boot 4 compatibility, Apr 2026). Earlier 6.x/7.x are Boot 3.x only. **HIGH**. |
| PostgreSQL | **17.x** | Primary datastore | User-locked. JSONB, `SKIP LOCKED`, logical replication, pg_stat_statements all battle-tested. **HIGH**. |
| Redis | **7.4** | Cache, sessions, rate limit, idempotency | Not a queue. Lettuce client via Spring Data Redis. **HIGH**. |
| Flyway | **11.x** | Schema migrations | Mature, imperative, fits team mental model of Postgres. **HIGH**. Prefer over Liquibase — XML/YAML migration spec is overhead we don't need for a single-DB app. |

### Spring Boot 4 Starters (required)

| Starter | Purpose |
|---|---|
| `spring-boot-starter-web` | REST (we don't need reactive for this workload — Gmail push is HTTP, LLM calls are I/O-bound but modest fan-out; use **virtual threads** instead). |
| `spring-boot-starter-security` | Base security. |
| `spring-boot-starter-oauth2-client` | Google OAuth login + refresh token management. |
| `spring-boot-starter-data-jpa` | ORM. |
| `spring-boot-starter-data-redis` | Redis via Lettuce. |
| `spring-boot-starter-validation` | Jakarta Validation 3.1 (JSR 380). |
| `spring-boot-starter-actuator` | `/actuator/health`, Prometheus, readiness/liveness. |
| `spring-boot-starter-jdbc` | For Flyway + Spring Data JDBC. |
| `spring-boot-docker-compose` (dev only) | Auto-starts Postgres + Redis locally. |
| `spring-cloud-gcp-starter-pubsub` | Pub/Sub publisher; push receiver is still a plain MVC controller. |
| `spring-cloud-gcp-starter-secretmanager` | Pull app secrets from GCP Secret Manager at boot. |

**Enable virtual threads:** `spring.threads.virtual.enabled=true`. Spring Boot 4 honors this for Tomcat, `@Async`, `@Scheduled`, and Spring AI HTTP calls, which is the single biggest latency win for an I/O-heavy triage service. **HIGH**.

### Spring AI Modules

| Artifact | Purpose | Notes |
|---|---|---|
| `org.springframework.ai:spring-ai-starter-model-openai` | **OpenRouter path** + direct OpenAI BYOK | Point `spring.ai.openai.base-url=https://openrouter.ai/api/v1`. Model IDs follow OpenRouter convention (`openai/gpt-4o-mini`, `anthropic/claude-3.5-sonnet`). **HIGH** — verified via Spring AI docs. |
| `org.springframework.ai:spring-ai-starter-model-anthropic` | Direct Anthropic BYOK | Only needed if you want to bypass OpenRouter when the user supplies an Anthropic key. Optional for v1 — OpenRouter covers Anthropic too. **MEDIUM**. |
| `org.springframework.ai:spring-ai-advisors-vector-store` | RAG advisors | **Skip in v1** — your privacy constraint forbids embedding storage. |
| `org.springframework.ai:spring-ai-model-chat-memory-repository-jdbc` | Persistent chat memory | **Skip in v1** — no long-term storage of LLM I/O. If you need short-lived draft context, keep it in Redis with TTL instead. |
| `org.springframework.ai:spring-ai-autoconfigure-model-chat-observation` | Micrometer observation for LLM calls | Auto-on via actuator; export to OTLP. **HIGH**. |

**OpenRouter + BYOK implementation pattern (verified):**

1. **Default tenant path (platform-paid):** Autoconfigured `ChatClient` bean with `spring.ai.openai.base-url=https://openrouter.ai/api/v1` and `spring.ai.openai.api-key=${OPENROUTER_KEY}`.
2. **BYOK per-request path:** Do **not** build a new `ChatClient` per request (GC pressure). Instead, inject the runtime key via request options:

   ```java
   chatClient.prompt(prompt)
       .options(OpenAiChatOptions.builder()
           .httpHeaders(Map.of("Authorization", "Bearer " + tenantKey))
           .model(tenantModelChoice)
           .build())
       .call();
   ```
   This is the documented Spring AI pattern (`OpenAiChatOptions.httpHeaders`) and overrides the autoconfigured API key at request time. **HIGH**.
3. **Alternative:** Custom `ApiKey` functional interface that reads from a `ThreadLocal<TenantContext>` — cleaner but requires owning the `OpenAiApi` bean. Use this if you want per-tenant `base-url` too (e.g., a tenant whose BYOK is direct OpenAI not via OpenRouter).

**Observability for LLM calls** (Spring AI 1.x):
- `spring.ai.chat.observations.include-prompt=false` and `include-completion=false` — **must stay false** for your privacy posture. Spans will record model, token counts, latency, stop reason, but **not** the content. **HIGH**.

### Gmail / Google Integration

| Library | Version | Purpose | Notes |
|---|---|---|---|
| `com.google.apis:google-api-services-gmail` | **v1-rev20250910-2.0.0** or latest (check Maven Central at impl time) | Gmail REST client | Generated client. Used for `users.watch`, `messages.get`, `labels`, `drafts`. **HIGH**. |
| `com.google.auth:google-auth-library-oauth2-http` | 1.29.x | OAuth2 credentials + ID-token verification | Used to **verify OIDC tokens** on Pub/Sub push requests (critical — without this, anyone can POST to your push endpoint). **HIGH**. |
| `com.google.cloud:google-cloud-pubsub` | Transitive via spring-cloud-gcp 8 | Native Pub/Sub client | Only needed for **publishing**. For push **receiving**, you just have a `@PostMapping` controller — Google POSTs JSON to it. **HIGH**. |
| `com.google.api-client:google-api-client` | Transitive | Shared infra | — |

**Gmail OAuth flow (prescribed):**
1. **Incremental authorization** — request narrow scopes first (profile + `gmail.readonly`), escalate to `gmail.modify` (never `gmail.send` in v1 — draft-only needs only `gmail.modify`) when the user enables triage.
2. **Offline access + refresh token** — `access_type=offline&prompt=consent` on the first grant so Google returns a refresh token. Store it AES-GCM-encrypted with a key from GCP Secret Manager / KMS; do **not** use pgcrypto's `pgp_sym_encrypt` (key lives in DB/connection, poor rotation story).
3. **Per-user Pub/Sub topic** is *not* necessary — use **one topic, one push subscription**, and let the push payload include `historyId` + `emailAddress`; dispatch to the right tenant server-side.
4. **Gmail `watch` must be renewed every 7 days** — scheduled job (`@Scheduled` on virtual thread) refreshes all active watches every 24 hours. Without this, triage silently dies on day 8 — classic pitfall.

### Persistence Details

| Concern | Choice | Rationale |
|---|---|---|
| ORM | **Spring Data JPA (Hibernate 7)** primary; **Spring Data JDBC** for read-heavy projections | JPA's identity/dirty-tracking shines on aggregates (User, Rule, TriageRun); JDBC is simpler for flat reads. **jOOQ is overkill** for a greenfield schema the team owns. |
| Migrations | **Flyway 11** | Imperative SQL files, no abstraction tax. Put under `src/main/resources/db/migration`. |
| Rule matchers | **JSONB column** with a `jsonb_path_ops` GIN index | Rules are structured-but-evolving (classifier output shape changes with prompt iteration). JSONB lets you add fields without a migration. |
| Audit log | Append-only table with `BRIN` index on `created_at` | Triage runs are time-series-ish; BRIN keeps it cheap at scale. |
| Token encryption | **AES-GCM at app layer**, key from **GCP Secret Manager / Cloud KMS** | Beats pgcrypto because keys never touch DB and rotation is a KMS operation, not a SQL migration. |
| Multi-tenancy | **Shared schema, discriminator column (`tenant_id`)** enforced by a Hibernate `@Filter` + JPA `@TenantId` (Hibernate 6.3+) | Simplest correct model. Schema-per-tenant is premature for v1. **HIGH**. |
| Connection pool | **HikariCP** (Boot default) | Keep default. |

### Async / Queue Strategy

**Recommendation: no external broker in v1.**

- **Ingress**: Google Pub/Sub push → your `/internal/pubsub/gmail` controller. Pub/Sub handles at-least-once, retries, DLQ. You get backpressure for free.
- **Internal fan-out**: Postgres table `triage_job` with columns `(id, tenant_id, external_id UNIQUE, status, attempts, locked_until, payload JSONB)`. A `@Scheduled` worker polls with:
  ```sql
  SELECT * FROM triage_job
  WHERE status = 'PENDING' AND locked_until < now()
  FOR UPDATE SKIP LOCKED
  LIMIT 50;
  ```
  Each worker runs on a **virtual thread**, so a single pod can comfortably hold 1000+ concurrent triage jobs.
- **Idempotency**: the Pub/Sub message's `messageId` is the dedup key. Unique constraint on `external_id` makes duplicate deliveries a no-op.
- **Why not Kafka/RabbitMQ**: adds ops surface, a second durability story, and another dashboard — for a workload whose QPS ceiling at the v1 user base is <50/s. Revisit if you hit multi-region or >500 msg/s sustained. **HIGH**.

### Caching / Session / Redis

Redis earns its keep for four specific jobs — not as a speculative cache:

1. **Rate limiting** per tenant + per action (Bucket4j or Spring's `RateLimiter`, backed by Lettuce).
2. **Idempotency keys** for user-initiated write actions (TTL 24h).
3. **Session store** (`spring-session-data-redis`) so horizontal scaling Just Works.
4. **Ephemeral LLM context** — short-lived draft context (TTL 30min) that must never go to Postgres per your privacy constraint.

**Not for:** user/rule caching (Postgres is plenty fast for that at this scale) and definitely not for job queueing.

### Frontend

| Technology | Version | Purpose | Why |
|---|---|---|---|
| Next.js | **15.x** (App Router) | Web app | Current stable. React Server Components are mature. **HIGH**. |
| React | **19.x** | UI lib | Actions + `use` hook + ref-as-prop landed; pairs with Next 15. **HIGH**. |
| TypeScript | 5.6+ | Type safety | Non-negotiable. |
| Tailwind CSS | **4.x** | Styling | v4 is Rust-based (Oxide), faster than v3. **HIGH**. |
| shadcn/ui | latest CLI (copy-in components) | UI primitives | Radix under the hood, full source in your repo — you own the code. Best DX for this workload. **HIGH**. |
| TanStack Query | **v5** | Server state | Standard. Pairs with OpenAPI-generated fetch client. **HIGH**. |
| `openapi-typescript` + `openapi-fetch` | latest | Typed API client | Generate types from Spring's OpenAPI doc. **Do not use tRPC** — the backend is Java, tRPC assumes a TS backend. **HIGH**. |
| `next-auth` / Auth.js | **NOT USED** | — | Auth is owned by Spring Boot. Next.js just reads the session cookie via a server action or `/api/me` call. **HIGH** — simpler than running two auth systems. |
| Zod | latest | Runtime validation of inputs | Pair with react-hook-form. |

### Monorepo & Build

| Piece | Choice | Notes |
|---|---|---|
| Layout | **Hybrid monorepo** — Gradle owns `apps/api` and its submodules; **pnpm workspace + Turborepo** owns `apps/web`. Top-level README documents both. | Do **not** try to make Gradle run Node. `com.github.node-gradle` exists but adds a layer that every frontend dev will hate. |
| Gradle | **9.x** with **Kotlin DSL** | User-locked. |
| Version catalog | **`gradle/libs.versions.toml`** | Single source of truth for versions across backend modules. **HIGH**. |
| Build structure | **Multi-project** (not composite) | Composite builds are for sharing plugin jars across unrelated repos. Multi-project is the right answer here. **HIGH**. |
| JDK provisioning | **Gradle toolchains** — declare Java 25 in root `build.gradle.kts`; Gradle auto-downloads from Foojay. | Prevents "works on my machine" JDK drift. **HIGH**. |
| Node version | Pinned via **`.nvmrc`** / Volta, enforced by Turborepo. | — |
| Docker | Multi-stage: one stage runs `./gradlew bootBuildImage` (or Jib) for API, another runs `pnpm build` for web. | Or keep them fully separate — two images, two Cloud Run services. **Preferred for v1**. |

**Suggested Gradle toolchain config (`build.gradle.kts` root):**

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    jvmToolchain(25) // only if any module uses Kotlin
}
```

### Deployment

**Prescribed: Google Cloud Run.** Rationale:

- **Pub/Sub push integrates natively with Cloud Run** — Pub/Sub signs requests with an OIDC token whose audience is your Cloud Run service URL. Your push controller just verifies the token. No VPC, no NAT, no glue.
- **Cloud SQL for Postgres 17** with private IP + Cloud SQL Auth Proxy sidecar (or the Spring Cloud GCP SQL starter).
- **Memorystore for Redis**.
- **Secret Manager** wired in via spring-cloud-gcp-starter-secretmanager.
- **Scale to zero** on dev/staging; min-instances ≥ 1 on prod to avoid cold-start on Pub/Sub pushes (LLM latency dominates anyway, but cold-start on Gmail push is user-visible because it delays triage).

**Alternatives (when to pick them):**

| Platform | When it wins |
|---|---|
| **Fly.io** | If you want global Postgres-near-user or WebSocket-heavy workloads — neither applies here. |
| **Railway / Render** | Faster first-deploy DX; pick for prototyping, migrate to Cloud Run once billing is live. |
| **Kubernetes (GKE Autopilot)** | Only if you need custom networking, >2 services, or compliance requires it. **Overkill for v1.** |

**Image base:** `eclipse-temurin:25-jre-noble` (MEDIUM) or `gcr.io/distroless/java25-debian12:nonroot` (MEDIUM) once distroless ships an official Java 25 tag. Build via **Spring Boot's `bootBuildImage` (Paketo Cloud Native Buildpacks)** — it auto-enables CDS and AOT layers in Spring Boot 4, cutting cold start by ~40%.

### Auth

**Prescribed pattern:**

- Spring Security `oauth2Login()` with Google provider. Scopes: `openid profile email` on first login; `https://www.googleapis.com/auth/gmail.modify` + `pubsub` added incrementally on triage activation.
- **Session cookie** (not JWT). Issued by Spring, stored in Redis via `spring-session-data-redis`. `HttpOnly`, `SameSite=Lax`, `Secure`. Next.js runs on the same root domain (or subdomain with cookie `domain=.zeromail.app`).
- Next.js **Server Components** forward the `Cookie` header to Spring using `fetch(…, { credentials: 'include' })` inside a Route Handler / Server Action.
- **CSRF**: Spring Security 7's cookie-based CSRF token + `X-XSRF-TOKEN` header. Next.js reads the cookie and echoes the header on mutating requests.
- **Do NOT use stateless JWT** for user sessions — you need instant revocation when users click "Disconnect Gmail", and refresh-token handling for Google is server-side anyway. JWT buys nothing here and costs you a revocation list.

### Observability

| Concern | Choice | Notes |
|---|---|---|
| Metrics | **Micrometer** → Prometheus endpoint via actuator → **OTLP** (via Micrometer's OTLP registry) | **HIGH** |
| Traces | **OpenTelemetry Java agent** (auto-instrumentation JAR attached at container start) | One env var enables it; auto-instruments Spring MVC, JDBC, Redis, Pub/Sub, HTTP clients used by Spring AI. **HIGH** |
| LLM-specific | Spring AI's `ChatModelObservationConvention` (auto-wired with `spring-ai-autoconfigure-model-chat-observation`) | Emits `gen_ai.*` OTel semconv spans. Do **not** include prompt/completion content. **HIGH** |
| Logs | Logback JSON encoder (`logstash-logback-encoder` 8.x) → stdout → Cloud Run Logs → OTLP forwarder to Grafana Loki | **HIGH** |
| Backend | **Grafana Cloud** (Tempo + Loki + Mimir) free tier or **Honeycomb** (trace-first, pricier). Grafana Cloud is the 2026 default for this scale. | **MEDIUM** — not technical, just cost/UX |
| Error tracking | **Sentry Java SDK 7.x** alongside OTel (captures exceptions with better grouping than raw traces) | Optional. **MEDIUM** |

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|---|---|---|
| Cloud Run | Fly.io / Railway | If GCP lock-in is unacceptable; Fly for global latency. Neither beats Cloud Run's native Pub/Sub push integration. |
| Postgres-backed queue | Kafka / RabbitMQ | Only if sustained >500 msg/s or cross-service event bus emerges. Not v1. |
| Spring Data JPA + JDBC | jOOQ | If you want full SQL control and the team enjoys Kotlin-first DSLs. Not worth onboarding cost for v1. |
| OpenRouter default | Direct provider SDKs (OpenAI, Anthropic) | If you outgrow routing overhead or need provider-specific features (Anthropic tool use, OpenAI structured outputs). Spring AI's module design already lets you swap without code change. |
| Flyway | Liquibase | If you need DB-agnostic migrations or reverse-engineer existing DBs. Neither applies. |
| Session cookies | Stateless JWT | If you had a purely mobile client with offline sessions. Web-only app → cookie is correct. |
| `openapi-typescript` codegen | tRPC / GraphQL | tRPC requires a TS backend (you have Java). GraphQL is overhead for a narrow REST surface; add later only if client query flexibility becomes a bottleneck. |
| pnpm + Turborepo | Nx | Nx is heavier and its plugin ecosystem is oriented to all-JS monorepos. You have one web app — Turborepo is lighter. |
| Shared-schema multi-tenancy | Schema-per-tenant / DB-per-tenant | Only at enterprise scale with strict data residency. Premature for prosumer v1. |

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| **Lombok** under Java 25 | Lombok lags JDK releases by 3–12 months; Java 25 adds features (flexible constructors, module imports) Lombok may trip on. Records + Java 25 pattern matching cover 90% of Lombok's use cases. | **Java records** + `@Builder` only where justified (via an explicit builder class, not Lombok). **HIGH**. |
| **Jackson 2.x** annotations | Spring Boot 4 ships **Jackson 3.0**; Jackson 2 is deprecated. Mixing throws at runtime. | Jackson 3 annotations (`com.fasterxml.jackson.annotation` → `tools.jackson.annotation` in 3.x namespace shift — verify at impl time). **HIGH**. |
| **Spring WebFlux** for this app | You have no streaming endpoints; LLM streaming can ride SSE on MVC. Reactive adds cognitive tax and worse debuggability. | Spring MVC + **virtual threads** (`spring.threads.virtual.enabled=true`). **HIGH**. |
| **javax.*** packages | Spring Boot 4 is Jakarta-only. | `jakarta.*` exclusively. **HIGH**. |
| **Manually-built ChatClient per request** for BYOK | GC pressure, lost advisors, breaks observation. | One `ChatClient`, override `api-key` per request via `httpHeaders` option or `ApiKey` functional interface. **HIGH**. |
| **Storing LLM prompts/completions in logs or DB** | Violates your explicit privacy constraint. | `include-prompt=false`, `include-completion=false` on Spring AI observations. Scrub logs. **HIGH**. |
| **Polling Gmail** | Kills API quota and user-perceived responsiveness. | Pub/Sub push + `users.watch` refresh job. **HIGH**. |
| **`pgp_sym_encrypt` (pgcrypto) for OAuth tokens** | Key lives in app config → if DB leaks, tokens leak too. | App-layer AES-GCM with KMS-managed key. **HIGH**. |
| **Running Gradle's Node plugin** for the Next.js build | Slow, buggy, fights with Turborepo's cache. | Separate pnpm workspace, CI runs Gradle + pnpm as independent steps. **HIGH**. |
| **Kafka / RabbitMQ in v1** | Ops cost >> value at this QPS. | Pub/Sub (ingress) + Postgres `SKIP LOCKED` (internal). **HIGH**. |
| **Stateless JWT user sessions** | Hard to revoke, redundant with Google OAuth refresh tokens you already track server-side. | Cookie + Redis-backed Spring Session. **HIGH**. |
| **Embedding store / vector DB in v1** | Your privacy constraint forbids storing embeddings of user email. | No RAG over user mail in v1. If you do prompt-side retrieval of *rules* (not mail), store rule text in Postgres; embeddings are unnecessary for <1000 rules per user. **HIGH**. |

## Stack Patterns by Variant

**If traffic stays <20 req/s per tenant:**
- Single Cloud Run service, min-instances=1, max=10.
- Single Postgres (Cloud SQL db-custom-2-4GB).
- Single Redis (Memorystore Basic tier).

**If you outgrow Cloud Run request timeouts (long triage batches):**
- Move the worker loop to **Cloud Run Jobs** (batch) triggered by Cloud Scheduler, not the HTTP service.
- Keep the HTTP service purely for Pub/Sub ingress + user API.

**If you go multi-region:**
- Promote the Postgres queue to Pub/Sub with ordering keys on `tenant_id`.
- Move session store from Redis to signed cookies with short TTL + CSRF (avoid cross-region Redis latency).

## Version Compatibility

| Package | Compatible With | Notes |
|---|---|---|
| Spring Boot 4.0.6 | Java 17–26, Gradle 8.14+ or 9.x, Spring Framework 7.0.7+, Jakarta Servlet 6.1 | **HIGH** — verified. |
| Spring AI 1.0.5 | Spring Boot 3.5+ and 4.0.x | OpenAI client's `base-url` override is the OpenRouter pattern. **HIGH**. |
| spring-cloud-gcp 8.0.2 | Spring Boot 4.0.x, Java 17+ (JDK 25 required for Native Image) | **First line** supporting Boot 4. Do NOT use 6.x or 7.x with Boot 4 — dependency resolution will explode. **HIGH**. |
| Hibernate 7.x | JDK 17+, Jakarta Persistence 3.2 | Ambient via Boot 4. **HIGH**. |
| Flyway 11.x | Postgres 17 | **HIGH**. |
| Next.js 15 | React 19, Node 20 / 22 LTS | **HIGH**. |
| Turborepo 2.x | pnpm 9+ | **HIGH**. |

## Installation (representative fragments)

**`gradle/libs.versions.toml`**

```toml
[versions]
springBoot = "4.0.6"
springAi = "1.0.5"
springCloudGcp = "8.0.2"
googleApiGmail = "v1-rev20250910-2.0.0"
googleAuth = "1.29.0"
flyway = "11.0.0"
otelAgent = "2.10.0"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "springBoot" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security", version.ref = "springBoot" }
spring-boot-starter-oauth2-client = { module = "org.springframework.boot:spring-boot-starter-oauth2-client", version.ref = "springBoot" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa", version.ref = "springBoot" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis", version.ref = "springBoot" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation", version.ref = "springBoot" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator", version.ref = "springBoot" }
spring-session-data-redis = { module = "org.springframework.session:spring-session-data-redis" }
spring-ai-starter-model-openai = { module = "org.springframework.ai:spring-ai-starter-model-openai", version.ref = "springAi" }
spring-cloud-gcp-pubsub = { module = "com.google.cloud:spring-cloud-gcp-starter-pubsub", version.ref = "springCloudGcp" }
spring-cloud-gcp-secretmanager = { module = "com.google.cloud:spring-cloud-gcp-starter-secretmanager", version.ref = "springCloudGcp" }
google-api-gmail = { module = "com.google.apis:google-api-services-gmail", version.ref = "googleApiGmail" }
google-auth-oauth2 = { module = "com.google.auth:google-auth-library-oauth2-http", version.ref = "googleAuth" }
flyway-postgres = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
```

**`apps/web/package.json`** (key deps only)

```json
{
  "dependencies": {
    "next": "15.x",
    "react": "19.x",
    "react-dom": "19.x",
    "@tanstack/react-query": "^5",
    "openapi-fetch": "^0.13",
    "tailwindcss": "^4",
    "zod": "^3.23"
  },
  "devDependencies": {
    "openapi-typescript": "^7",
    "turbo": "^2"
  }
}
```

## Sources

- https://spring.io/projects/spring-boot — Spring Boot 4.0.6 current GA, 4.1.0-RC1 preview. **HIGH**.
- https://docs.spring.io/spring-boot/system-requirements.html — Java 17–26, Gradle 8.14+ / 9.x, Spring Framework 7.0.7+, Servlet 6.1. **HIGH**.
- https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html — OpenAI chat client configuration including `base-url` override (OpenRouter pattern) and `httpHeaders` per-request API-key override (BYOK pattern). **HIGH**.
- https://spring.io/projects/spring-ai — Spring AI 1.0.5 current. **HIGH**.
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes — Spring Framework 7, Security 7, Servlet 6.1, Jackson 3, Gradle 9 support. **HIGH**.
- https://github.com/GoogleCloudPlatform/spring-cloud-gcp/releases — v8.0.2 (Apr 2026), first line with Spring Boot 4 compatibility. **HIGH**.
- https://en.wikipedia.org/wiki/Java_version_history — Java 25 LTS GA 2025-09-16, Java 26 GA 2026-03-17. **HIGH**.
- Training-data + framework familiarity for: Turborepo/pnpm layout, Grafana Cloud defaults, Sentry integration, HikariCP defaults, Hibernate `@TenantId`. **MEDIUM** — common-knowledge patterns, not version-sensitive.

## Confidence Summary

| Area | Confidence | Reason |
|---|---|---|
| Spring Boot 4.0.6 + Java 25 + Gradle 9 toolchain | **HIGH** | Verified via docs.spring.io/system-requirements. |
| Spring AI 1.0.5 + OpenRouter (base-url swap) + BYOK (httpHeaders / ApiKey) | **HIGH** | Verified via Spring AI reference docs. |
| spring-cloud-gcp 8.0.2 for Pub/Sub on Boot 4 | **HIGH** | Verified via GitHub releases (Apr 2026). |
| Postgres-backed queue with SKIP LOCKED | **HIGH** | Standard pattern; QPS envelope verified against v1 user base assumption. |
| Cloud Run as deployment target | **MEDIUM** | Technically excellent fit; other platforms also viable. The argument is native Pub/Sub push integration. |
| Next.js 15 + React 19 + shadcn + TanStack Query | **HIGH** | 2026 defaults, stable combination. |
| OpenAPI codegen (not tRPC) | **HIGH** | Forced by Java backend. |
| Cookie session (not JWT) | **HIGH** | Forced by need for instant revocation + refresh-token server-side handling. |
| Observability → Grafana Cloud via OTLP | **MEDIUM** | Technically standard; specific vendor choice is cost/taste. |
| Lombok avoidance under Java 25 | **MEDIUM** | Lombok has historically lagged JDK releases; verify at impl time — but records/pattern-matching in Java 25 remove most reasons to use it. |
| Jackson 3 mandatory on Spring Boot 4 | **HIGH** | Per release notes. |
| No vector DB / no embedding store in v1 | **HIGH** | Forced by privacy constraint in PROJECT.md. |

---
*Stack research for: AI Gmail-triage SaaS (Zero Mail)*
*Researched: 2026-04-24*
