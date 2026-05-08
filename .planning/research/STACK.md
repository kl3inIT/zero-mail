# Stack Research — Zero Mail

**Domain:** Multi-tenant AI Gmail-triage SaaS (Java 25 / Spring Boot 4 / Spring AI / Next.js)
**Researched:** 2026-04-24
**Overall confidence:** MEDIUM-HIGH overall. HIGH for Spring Boot 4.0.6, Liquibase YAML, and the GCP-managed runtime choices; MEDIUM on exact Spring AI 2.0.0-M5 APIs because the user explicitly chose a milestone build.

---

## TL;DR — The Prescriptive Stack

- **JDK 25 LTS** (GA 2025-09-16) managed via Gradle toolchains.
- **Gradle 9.4.1** with **Kotlin DSL**, `libs.versions.toml` version catalog, multi-project (not composite) build.
- **Spring Boot 4.0.6** (current GA; 4.1.0-RC1 exists — stay on 4.0.x for production).
- **Spring Framework 7.0.7**, **Spring Security 7.0.5**, **Jakarta Servlet 6.1**, **Jakarta Persistence 3.2**, **Jackson 3.1.2** (managed by Spring Boot 4.0.6).
- **Spring AI 2.0.0-M5** via OpenAI, Anthropic, Google GenAI, and DeepSeek starters. Platform OpenRouter routing uses the OpenAI adapter (`base-url: https://openrouter.ai/api/v1`); official BYOK providers use native Spring AI adapters where available. Keep all direct Spring AI usage inside the LLM adapter because M5 -> GA churn is still likely.
- **spring-cloud-gcp 8.0.2** — keep it for **Secret Manager** integration. Do **not** add `spring-cloud-gcp-starter-pubsub` by default: Gmail push still arrives as **plain HTTP POSTs to a Spring MVC controller**, so the Pub/Sub starter adds little unless the app later needs Pub/Sub publish/admin flows from Java.
- **PostgreSQL 17.6 on Cloud SQL** + **Liquibase 5.0.2 (YAML changelogs)** + **Spring Data JPA (Hibernate 7)** for aggregates, **Spring Data JDBC** for read-side and hot paths, **JSONB + jsonb_path_ops** for rule matchers, **pgcrypto / AES-GCM at app layer** for OAuth refresh-token encryption. Community PostgreSQL 18 exists, but Cloud SQL 18 is still Preview as of 2026-04-24.
- **Redis 7.2 on Memorystore** (Spring Data Redis + Lettuce) for rate limiting, idempotency keys, session store, and per-tenant ChatModel cache — NOT as a task queue. OSS Redis 8.x exists, but GCP's managed service currently tops out at 7.2.
- **Queue = Postgres-backed** (single `outbox` + `processing_job` table with `SKIP LOCKED`). No Kafka, no RabbitMQ in v1. Google Pub/Sub already handles ingress retries.
- **Next.js 16.2.4 (App Router) + React 19.2.5** in `apps/web`, **pnpm 10.33.2 + Turborepo 2.9.6**, **TanStack Query 5.100.1**, **shadcn/ui + Tailwind CSS 4.2.4**, typed client via **OpenAPI codegen (`openapi-typescript` 7.13.0 + `openapi-fetch` 0.17.0)** from Spring's `springdoc-openapi` output.
- **Auth**: Spring Security OAuth2 Client (Google), **server-issued signed session cookie** (not stateless JWT). Next.js sits behind the same origin; cookie is HttpOnly, SameSite=Lax.
- **Deploy**: **Google Cloud Run** (Pub/Sub push is natively OIDC-authenticated against Cloud Run URLs — zero glue). Cloud SQL Postgres, Memorystore Redis. Secret Manager for OAuth client secret + app-level encryption keys.
- **Container**: `eclipse-temurin:25-jre-noble` (production) built via Spring Boot's **CDS + AOT layered image** support; distroless for hardening if startup tuning matters more than debuggability.
- **Observability**: Micrometer + **OpenTelemetry Java agent 2.16.0**, push OTLP to **Grafana Cloud** (Tempo/Loki/Mimir) — cheapest and most vendor-neutral path in 2026. Keep prompt/completion capture disabled in Spring AI tracing.

---

## Recommended Stack

### Core Backend

| Technology | Version | Purpose | Why |
|---|---|---|---|
| Java | **25 LTS** (GA 2025-09-16) | Runtime | User-locked. LTS, virtual threads are stable, pattern matching + records + scoped values all mature. Spring Boot 4 supports Java 17–26. **HIGH**. |
| Spring Boot | **4.0.6** | App framework | User-locked. Current GA. Requires Spring Framework 7, Jakarta Servlet 6.1, Jackson 3. **HIGH** — verified via official Spring release notes + system requirements. |
| Spring Framework | **7.0.7** (managed by Boot BOM) | Core framework | Ambient — pulled by Boot 4.0.6. **HIGH**. |
| Spring Security | **7.0.5** (Boot-managed) | AuthN/Z, OAuth2 client | Google Workspace OAuth2, CSRF, session. **HIGH**. |
| Spring Data JPA | 4.0.x (Boot-managed, Hibernate 7.x) | ORM for aggregates | Write-side (rules, users, tenants, audit). **HIGH**. |
| Spring Data JDBC | 4.0.x | Read-side & hot paths | For triage-log lookups, analytics. Avoids N+1 / lazy-init traps in hot path. **MEDIUM** — optional; JPA alone is fine for v1. |
| Spring AI | **2.0.0-M5** | LLM orchestration | User-locked milestone. `spring-ai-starter-model-openai` still gives the OpenRouter/OpenAI path; native Anthropic, Google GenAI, and DeepSeek starters back official BYOK presets. Keep Spring AI surface area isolated to one adapter module because M5 -> GA changes are still possible. **MEDIUM-HIGH**. |
| spring-cloud-gcp | **8.0.2** | GCP integration (Secret Manager first, Pub/Sub optional) | **First line that supports Spring Boot 4**. Use `spring-cloud-gcp-starter-secretmanager` by default. Gmail push receiving itself stays a plain HTTP controller, so the Pub/Sub starter is optional rather than foundational. **HIGH**. |
| PostgreSQL | **17.6 on Cloud SQL** | Primary datastore | Cloud SQL PostgreSQL 18 is still Preview as of 2026-04-24. For a managed production v1 on GCP, 17.6 is the latest GA-safe line. **HIGH**. |
| Redis | **7.2 on Memorystore** | Cache, sessions, rate limit, idempotency | Not a queue. Memorystore currently supports Redis through 7.2, so this is the latest managed option on GCP even though OSS Redis 8.x exists. **HIGH**. |
| Liquibase | **5.0.2** | Schema migrations | User requested Liquibase + YAML. Spring Boot 4.0.6 BOM already manages Liquibase 5.0.2, so this is both latest and compatible. **HIGH**. |

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
| `spring-boot-starter-jdbc` | For Liquibase + Spring Data JDBC. |
| `spring-boot-starter-liquibase` | Liquibase runtime + Boot auto-configuration for YAML changelogs. |
| `spring-boot-docker-compose` (dev only) | Auto-starts Postgres + Redis locally. |
| `spring-cloud-gcp-starter-secretmanager` | Pull app secrets from GCP Secret Manager at boot. |

**Enable virtual threads:** `spring.threads.virtual.enabled=true`. Spring Boot 4 honors this for Tomcat, `@Async`, `@Scheduled`, and Spring AI HTTP calls, which is the single biggest latency win for an I/O-heavy triage service. **HIGH**.

### Spring AI Modules

| Artifact | Purpose | Notes |
|---|---|---|
| `org.springframework.ai:spring-ai-starter-model-openai` | **OpenRouter path** + direct OpenAI BYOK | Point `spring.ai.openai.base-url=https://openrouter.ai/api/v1` for the platform path; BYOK OpenAI/OpenRouter/custom OpenAI-compatible endpoints pass tenant key/base URL/model through the adapter. **HIGH** for the provider wiring; **MEDIUM** on exact M5 runtime builder APIs. |
| `org.springframework.ai:spring-ai-starter-model-anthropic` | Direct Anthropic BYOK | Native Anthropic BYOK uses `AnthropicChatOptions` with tenant key/base URL/model instead of converting Claude traffic through OpenAI-compatible requests. **MEDIUM-HIGH**. |
| `org.springframework.ai:spring-ai-starter-model-google-genai` | Direct Google GenAI BYOK | Native Google GenAI BYOK uses the Google GenAI `Client` + `GoogleGenAiChatModel`; model IDs remain user-entered and validated against the models endpoint. **MEDIUM**. |
| `org.springframework.ai:spring-ai-starter-model-deepseek` | Direct DeepSeek BYOK | Native DeepSeek BYOK uses `DeepSeekApi` + `DeepSeekChatModel`; endpoint defaults to `https://api.deepseek.com`. **MEDIUM**. |

**OpenRouter + BYOK implementation pattern (prescribed):**

1. **Default tenant path (platform-paid):** Autoconfigured OpenAI-compatible client with `spring.ai.openai.base-url=https://openrouter.ai/api/v1` and the platform OpenRouter key.
2. **BYOK per-request path:** Keep Spring AI behind `LlmGateway` and pass tenant-specific model / auth overrides at request time rather than rebuilding full client graphs on every call.
3. **Milestone caution:** Because the project is pinned to **Spring AI 2.0.0-M5**, keep the M5 adapter seams isolated and re-verify them before any M5→GA upgrade. Phase 2C compile-tests currently verify `OpenAiChatModel.builder().options(...)`, `OpenAiChatOptions.builder().apiKey().baseUrl().model()`, `ChatClient.prompt().options(AnthropicChatOptions.builder()...)`, `GoogleGenAiChatModel.builder()`, and `DeepSeekChatModel.builder()`.

**Observability for LLM calls** (Spring AI 2.0.0-M5):
- Keep prompt/completion capture disabled. Spans should record provider, model, token counts, latency, and stop reason, but **never** raw content. **HIGH** as a policy; **MEDIUM** on exact property names until implementation locks the M5 APIs.

### Gmail / Google Integration

| Library | Version | Purpose | Notes |
|---|---|---|---|
| `com.google.apis:google-api-services-gmail` | **v1-rev20250331-2.0.0** | Gmail REST client | Generated client. Used for `users.watch`, `messages.get`, `labels`, `drafts`. **HIGH**. |
| `com.google.auth:google-auth-library-oauth2-http` | **1.35.0** | OAuth2 credentials + ID-token verification | Used to **verify OIDC tokens** on Pub/Sub push requests (critical — without this, anyone can POST to your push endpoint). **HIGH**. |
| `com.google.cloud:google-cloud-pubsub` | Optional | Native Pub/Sub client | Not required for the core Gmail push receiver. Add only if the app later needs Pub/Sub publish/admin flows from Java. For push **receiving**, you just have a `@PostMapping` controller — Google POSTs JSON to it. **HIGH**. |
| `com.google.api-client:google-api-client` | Transitive | Shared infra | — |

**Gmail OAuth flow (prescribed):**
1. **Incremental authorization** — request narrow scopes first (profile + `gmail.readonly`), escalate to `gmail.modify` (never `gmail.send` in v1 — draft-only needs only `gmail.modify`) when the user enables triage.
2. **Offline access + refresh token** — `access_type=offline&prompt=consent` on the first grant so Google returns a refresh token. Store it AES-GCM-encrypted with a key from GCP Secret Manager / KMS; do **not** use pgcrypto's `pgp_sym_encrypt` (key lives in DB/connection, poor rotation story).
3. **Provision Pub/Sub outside the app** — create the topic + push subscription via Terraform / `gcloud`, not on application startup. The app only receives authenticated HTTP pushes and renews Gmail watches.
4. **Per-user Pub/Sub topic** is *not* necessary — use **one topic, one push subscription**, and let the push payload include `historyId` + `emailAddress`; dispatch to the right tenant server-side.
5. **Gmail `watch` must be renewed every 7 days** — scheduled job (`@Scheduled` on virtual thread) refreshes all active watches every 24 hours. Without this, triage silently dies on day 8 — classic pitfall.

### Persistence Details

| Concern | Choice | Rationale |
|---|---|---|
| ORM | **Spring Data JPA (Hibernate 7)** primary; **Spring Data JDBC** for read-heavy projections | JPA's identity/dirty-tracking shines on aggregates (User, Rule, TriageRun); JDBC is simpler for flat reads. **jOOQ is overkill** for a greenfield schema the team owns. |
| Migrations | **Liquibase 5.0.2 with YAML changelogs** | User-directed. Put the master file at `src/main/resources/db/changelog/db.changelog-master.yaml` and fan out numbered YAML files with `includeAll`. |
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
| Next.js | **16.2.4** (App Router) | Web app | Current stable line verified on 2026-04-24. **HIGH**. |
| React | **19.2.5** | UI lib | Actions + `use` hook + ref-as-prop landed; pairs with Next 16. **HIGH**. |
| TypeScript | **6.0.3** | Type safety | Current stable line verified on 2026-04-24. Non-negotiable. |
| Tailwind CSS | **4.2.4** | Styling | Current stable v4 line. Oxide keeps the toolchain fast. **HIGH**. |
| shadcn/ui | latest CLI (copy-in components) | UI primitives | Radix under the hood, full source in your repo — you own the code. Best DX for this workload. **HIGH**. |
| TanStack Query | **5.100.1** | Server state | Current stable line. Pairs with the OpenAPI-generated fetch client. **HIGH**. |
| `openapi-typescript` + `openapi-fetch` | **7.13.0 + 0.17.0** | Typed API client | Generate types from Spring's OpenAPI doc. **Do not use tRPC** — the backend is Java, tRPC assumes a TS backend. **HIGH**. |
| `next-auth` / Auth.js | **NOT USED** | — | Auth is owned by Spring Boot. Next.js just reads the session cookie via a server action or `/api/me` call. **HIGH** — simpler than running two auth systems. |
| Zod | **4.3.6** | Runtime validation of inputs | Current stable line. Pair with react-hook-form. |

### Monorepo & Build

| Piece | Choice | Notes |
|---|---|---|
| Layout | **Hybrid monorepo** — Gradle owns `backend/core`, `backend/api`, and `backend/worker`; **pnpm workspace + Turborepo** owns `apps/web`. | `backend/core` is the shared backend library. `backend/api` and `backend/worker` are thin executable shells over that shared core. Do **not** try to make Gradle run Node. |
| Gradle | **9.x** with **Kotlin DSL** | User-locked. |
| Version catalog | **`gradle/libs.versions.toml`** | Single source of truth for versions across backend modules. **HIGH**. |
| Build structure | **Multi-project** (not composite) | Composite builds are for sharing plugin jars across unrelated repos. Multi-project is the right answer here. **HIGH**. |
| JDK provisioning | **Gradle toolchains** — declare Java 25 in root `build.gradle.kts`; Gradle auto-downloads from Foojay. | Prevents "works on my machine" JDK drift. **HIGH**. |
| Node version | Pinned via **`.nvmrc`** / Volta, enforced by Turborepo. | — |
| Backend shape | **Pragmatic modular monolith** | Keep bounded contexts as package-based Spring Modulith modules **inside `backend/core`** instead of exploding them into many Gradle modules on day one. |
| Docker | Build one image for `backend/api`, one for `backend/worker`, and one for `apps/web` only if frontend is deployed separately. | `api` and `worker` should version/release together even if they run as separate Cloud Run services. |

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
- **Cloud SQL for Postgres 17.6** with private IP + Cloud SQL Auth Proxy sidecar (or the Spring Cloud GCP SQL starter). Revisit PostgreSQL 18 when Cloud SQL moves it out of Preview.
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

- Spring Security `oauth2Login()` with Google provider. Scopes: `openid profile email` on first login; `https://www.googleapis.com/auth/gmail.modify` added incrementally on triage activation.
- **Session cookie** (not JWT). Issued by Spring, stored in Redis via `spring-session-data-redis`. `HttpOnly`, `SameSite=Lax`, `Secure`. Next.js runs on the same root domain (or subdomain with cookie `domain=.zeromail.app`).
- Next.js **Server Components** forward the `Cookie` header to Spring using `fetch(…, { credentials: 'include' })` inside a Route Handler / Server Action.
- **CSRF**: Spring Security 7's cookie-based CSRF token + `X-XSRF-TOKEN` header. Next.js reads the cookie and echoes the header on mutating requests.
- **Do NOT use stateless JWT** for user sessions — you need instant revocation when users click "Disconnect Gmail", and refresh-token handling for Google is server-side anyway. JWT buys nothing here and costs you a revocation list.

### Observability

| Concern | Choice | Notes |
|---|---|---|
| Metrics | **Micrometer** → Prometheus endpoint via actuator → **OTLP** (via Micrometer's OTLP registry) | **HIGH** |
| Traces | **OpenTelemetry Java agent** (auto-instrumentation JAR attached at container start) | One env var enables it; auto-instruments Spring MVC, JDBC, Redis, Pub/Sub, HTTP clients used by Spring AI. **HIGH** |
| LLM-specific | Spring AI chat observations wired through Boot + Micrometer | Emits `gen_ai.*`-style spans/metrics. Do **not** include prompt/completion content. **MEDIUM-HIGH** — exact M5 wiring should be re-checked at implementation time |
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
| Liquibase YAML | Flyway | If the team decides it prefers SQL-only migrations later. For this project the user explicitly chose Liquibase YAML, so treat Flyway as the alternative, not the default. |
| Session cookies | Stateless JWT | If you had a purely mobile client with offline sessions. Web-only app → cookie is correct. |
| `openapi-typescript` codegen | tRPC / GraphQL | tRPC requires a TS backend (you have Java). GraphQL is overhead for a narrow REST surface; add later only if client query flexibility becomes a bottleneck. |
| pnpm + Turborepo | Nx | Nx is heavier and its plugin ecosystem is oriented to all-JS monorepos. You have one web app — Turborepo is lighter. |
| Shared-schema multi-tenancy | Schema-per-tenant / DB-per-tenant | Only at enterprise scale with strict data residency. Premature for prosumer v1. |

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| **Lombok** under Java 25 | Lombok lags JDK releases by 3–12 months; Java 25 adds features (flexible constructors, module imports) Lombok may trip on. Records + Java 25 pattern matching cover 90% of Lombok's use cases. | **Java records** + `@Builder` only where justified (via an explicit builder class, not Lombok). **HIGH**. |
| **Jackson 2.x** assumptions | Spring Boot 4.0.6 BOM is already on **Jackson 3.1.x**; clinging to Jackson 2-era assumptions creates migration bugs. | Jackson 3.x APIs and annotations only; verify any namespace or module changes at implementation time. **HIGH**. |
| **Spring WebFlux** for this app | You have no streaming endpoints; LLM streaming can ride SSE on MVC. Reactive adds cognitive tax and worse debuggability. | Spring MVC + **virtual threads** (`spring.threads.virtual.enabled=true`). **HIGH**. |
| **javax.*** packages | Spring Boot 4 is Jakarta-only. | `jakarta.*` exclusively. **HIGH**. |
| **Raw HTTP LLM calls or vendor SDK usage outside the Spring AI adapter** | Bypasses gateway privacy, tool-call, and observation controls. | Keep provider-specific BYOK client derivation inside `core.llm.gateway.springai`; every caller uses `LlmGateway`. **HIGH**. |
| **Storing LLM prompts/completions in logs or DB** | Violates your explicit privacy constraint. | `log-prompt=false`, `log-completion=false` on Spring AI observations. Scrub logs. **HIGH**. |
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
| Spring AI 2.0.0-M5 | Spring Boot 4.0.x / 4.1.x | OpenAI-compatible `base-url` override still enables the OpenRouter path. **MEDIUM-HIGH** — milestone caveat remains. |
| spring-cloud-gcp 8.0.2 | Spring Boot 4.0.x, Java 17+ | **First line** supporting Boot 4. Use Secret Manager starter by default; Pub/Sub starter is optional for this architecture. **HIGH**. |
| Hibernate 7.2.x | JDK 17+, Jakarta Persistence 3.2 | Ambient via Boot 4.0.6. **HIGH**. |
| Liquibase 5.0.2 | Spring Boot 4.0.6 BOM-managed | **HIGH**. |
| Next.js 16.2.4 | React 19.2.5, Node 20.9+ | **HIGH**. |
| Turborepo 2.9.6 | pnpm 10.33.2 | **HIGH**. |

## Installation (representative fragments)

**`gradle/libs.versions.toml`**

```toml
[versions]
springBoot = "4.0.6"
springAi = "2.0.0-M5"
springCloudGcp = "8.0.2"
googleApiGmail = "v1-rev20250331-2.0.0"
googleAuth = "1.35.0"
liquibase = "5.0.2"
otelAgent = "2.16.0"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "springBoot" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security", version.ref = "springBoot" }
spring-boot-starter-oauth2-client = { module = "org.springframework.boot:spring-boot-starter-oauth2-client", version.ref = "springBoot" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa", version.ref = "springBoot" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis", version.ref = "springBoot" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation", version.ref = "springBoot" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator", version.ref = "springBoot" }
spring-boot-starter-liquibase = { module = "org.springframework.boot:spring-boot-starter-liquibase", version.ref = "springBoot" }
spring-session-data-redis = { module = "org.springframework.session:spring-session-data-redis" }
spring-ai-starter-model-openai = { module = "org.springframework.ai:spring-ai-starter-model-openai", version.ref = "springAi" }
spring-cloud-gcp-secretmanager = { module = "com.google.cloud:spring-cloud-gcp-starter-secretmanager", version.ref = "springCloudGcp" }
google-api-gmail = { module = "com.google.apis:google-api-services-gmail", version.ref = "googleApiGmail" }
google-auth-oauth2 = { module = "com.google.auth:google-auth-library-oauth2-http", version.ref = "googleAuth" }
liquibase-core = { module = "org.liquibase:liquibase-core", version.ref = "liquibase" }
```

**`apps/web/package.json`** (key deps only)

```json
{
  "dependencies": {
    "next": "16.2.4",
    "react": "19.2.5",
    "react-dom": "19.2.5",
    "@tanstack/react-query": "5.100.1",
    "openapi-fetch": "0.17.0",
    "tailwindcss": "4.2.4",
    "zod": "4.3.6"
  },
  "devDependencies": {
    "openapi-typescript": "7.13.0",
    "turbo": "2.9.6"
  }
}
```

## Sources

- https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now — Spring Boot 4.0.6 current GA as of 2026-04-24. **HIGH**.
- https://docs.spring.io/spring-boot/system-requirements.html — Java 17–26, Gradle 8.14+ / 9.x, Spring Framework 7.0.7+, Servlet 6.1. **HIGH**.
- https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M5 — Spring AI 2.0.0-M5 release (2026-04-27). **HIGH**.
- https://docs.spring.io/spring-ai/reference/getting-started.html — Spring AI 2.0.x supports Spring Boot 4.0.x / 4.1.x. **HIGH**.
- https://docs.liquibase.com/concepts/changelogs/yaml-format.html — Liquibase YAML changelog support. **HIGH**.
- https://github.com/GoogleCloudPlatform/spring-cloud-gcp/releases/tag/v8.0.2 — spring-cloud-gcp 8.0.2 (2026-04-10), Boot 4 line. **HIGH**.
- https://cloud.google.com/sql/docs/postgres/db-versions — Cloud SQL PostgreSQL 17.6 is current GA; PostgreSQL 18 is Preview as of 2026-04-24. **HIGH**.
- https://cloud.google.com/memorystore/docs/redis/supported-versions — Memorystore supports Redis up to 7.2 as of 2026-04-24. **HIGH**.
- https://services.gradle.org/versions/current — Gradle 9.4.1 current stable. **HIGH**.
- npm registry on 2026-04-24 for `next`, `react`, `react-dom`, `@tanstack/react-query`, `tailwindcss`, `openapi-typescript`, `openapi-fetch`, `turbo`, `pnpm`, `zod`. **HIGH**.
- Training-data + framework familiarity for: Grafana Cloud defaults, Sentry integration, HikariCP defaults, Hibernate `@TenantId`. **MEDIUM** — common-knowledge patterns, not version-sensitive.

## Confidence Summary

| Area | Confidence | Reason |
|---|---|---|
| Spring Boot 4.0.6 + Java 25 + Gradle 9.4.1 toolchain | **HIGH** | Verified via official Spring docs + Gradle current-version endpoint. |
| Spring AI 2.0.0-M5 + OpenRouter (base-url swap) + BYOK abstraction | **MEDIUM-HIGH** | The provider path and M5 builder/options seams are compile-tested; M5→GA churn remains the main risk. |
| spring-cloud-gcp 8.0.2 for Secret Manager on Boot 4 | **HIGH** | Verified via GitHub releases (Apr 2026). |
| Postgres-backed queue with SKIP LOCKED | **HIGH** | Standard pattern; QPS envelope verified against v1 user base assumption. |
| Cloud Run as deployment target | **MEDIUM-HIGH** | Technically excellent fit; the main tradeoff is GCP managed-service lag versus community-latest Postgres/Redis versions. |
| Next.js 16.2.4 + React 19.2.5 + shadcn + TanStack Query 5.100.x | **HIGH** | Current stable frontend stack verified from official docs/npm registry on 2026-04-24. |
| OpenAPI codegen (not tRPC) | **HIGH** | Forced by Java backend. |
| Cookie session (not JWT) | **HIGH** | Forced by need for instant revocation + refresh-token server-side handling. |
| Observability → Grafana Cloud via OTLP | **MEDIUM** | Technically standard; specific vendor choice is cost/taste. |
| Lombok avoidance under Java 25 | **MEDIUM** | Lombok has historically lagged JDK releases; verify at impl time — but records/pattern-matching in Java 25 remove most reasons to use it. |
| Jackson 3 mandatory on Spring Boot 4 | **HIGH** | Per release notes. |
| No vector DB / no embedding store in v1 | **HIGH** | Forced by privacy constraint in PROJECT.md. |

---
*Stack research for: AI Gmail-triage SaaS (Zero Mail)*
*Researched: 2026-04-24*
