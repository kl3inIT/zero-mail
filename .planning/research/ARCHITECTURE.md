# Architecture Research

**Domain:** Multi-tenant AI Gmail-triage SaaS (Java 25 / Spring Boot 4 / Spring AI / Gradle KTS monorepo + Next.js)  
**Researched:** 2026-04-24  
**Confidence:** MEDIUM-HIGH
- HIGH on the coarse-grained backend module split: `backend/core` + `backend/api` + `backend/worker`
- HIGH on Spring Modulith as the mechanism to enforce package-based boundaries inside `backend/core`
- HIGH on the Spring AI `2.0.0-M5` adapter seams confirmed during Phase 2C (`OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(builder)`, provider-specific BYOK model selection)
- MEDIUM on the final shape of worker execution loops once real throughput is measured

---

## Executive Recommendation

Zero Mail v1 should use a **pragmatic modular monolith**:

1. `apps/web` for the Next.js frontend
2. `backend/core` for all shared backend logic and adapters
3. `backend/api` as the HTTP/OAuth/webhook executable
4. `backend/worker` as the async/scheduler executable

This is **not** a microservice architecture. It is still one backend system:
- one shared domain model
- one shared schema
- one shared release train
- no network API between `api` and `worker`

`api` and `worker` are just two thin runtime shells over the same `backend/core`.

---

## 1. Topology

```text
zero-mail/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── apps/
│   └── web/                         (Next.js frontend, pnpm + Turborepo)
└── backend/
    ├── core/                       (shared backend library, no executable main)
    ├── api/                        (Spring Boot executable: HTTP edge)
    └── worker/                     (Spring Boot executable: async/scheduled work)
```

### What each module means

| Module | Type | Purpose |
|---|---|---|
| `apps/web` | Node app | Next.js UI, consumes OpenAPI-generated client |
| `backend/core` | Java library | Domain model, use cases, ports, shared adapters, persistence mappings, Spring Modulith package boundaries |
| `backend/api` | Java executable | REST controllers, OAuth callbacks, payment webhooks, Gmail push endpoint, security, OpenAPI |
| `backend/worker` | Java executable | Outbox consumers, triage jobs, watch renewer, credit sweeper, analytics projectors |

---

## 2. Why This Shape

The previous plan split the backend into many Gradle modules (`identity`, `mail`, `rules`, `triage`, `billing`, `drafting`, `analytics`, adapters, app shell). That is clean on paper, but too much overhead for v1.

This new shape keeps the right separation at the points that matter most:

- **Web frontend vs backend**: separate toolchains
- **HTTP edge vs async worker runtime**: separate executables
- **Business boundaries**: enforced inside `backend/core` by package rules instead of many Gradle modules

This is the right tradeoff for a small team:
- fewer Gradle projects
- faster navigation and refactors
- less wiring noise
- still strong enough boundaries to stop spaghetti

---

## 3. What Lives in `backend/core`

`backend/core` is the shared backend library. It should not expose a `main()` entrypoint. It owns:

- domain model
- use cases / orchestration services
- port interfaces
- shared adapter implementations
- JPA entities / repositories
- Liquibase changelogs
- Spring Modulith package boundaries

### Recommended package layout inside `backend/core`

```text
backend/core/src/main/java/com/zeromail/
├── shared/
├── identity/
├── mail/
├── rules/
├── triage/
├── drafting/
├── billing/
├── analytics/
└── integration/
    ├── gmail/
    ├── llm/
    ├── persistence/
    └── payments/
```

### Practical reading of those packages

| Package | Owns |
|---|---|
| `shared` | IDs, money/credits, common errors, crypto helpers, clocks |
| `identity` | user, tenant, OAuth grant lifecycle |
| `mail` | Gmail account, watch expiry, history checkpoint, inbound notification records |
| `rules` | rule aggregate, matcher AST, compile-preview logic |
| `triage` | triage orchestrator, safety policy, action execution flow |
| `drafting` | draft generation flow and result model |
| `billing` | credit ledger, reserve/settle/release, top-up state |
| `analytics` | metadata-only projections and counters |
| `integration.gmail` | Gmail API adapter, push payload parsing, watch renew helper |
| `integration.llm` | Spring AI/OpenRouter/BYOK adapter |
| `integration.persistence` | repositories, outbox mappings, queue tables |
| `integration.payments` | Stripe/LemonSqueezy adapter |

The key idea is: **bounded contexts still exist**, but they are package-based modules inside `backend/core`, not separate Gradle subprojects.

---

## 4. How Boundaries Are Enforced

`backend/core` should use **Spring Modulith** to enforce internal module boundaries.

### Enforcement pattern

1. Each bounded context root package gets a `package-info.java` with `@ApplicationModule`
2. Cross-context APIs are exposed via `@NamedInterface` packages/types where needed
3. A modularity test in `backend/core` runs:

```java
ApplicationModules.of(CoreModuleAnchor.class).verify();
```

This gives three benefits without creating many build modules:

- compiler-friendly package structure
- architectural rule checking in CI
- transactional application events / publication registry support

### What this means in practice

- `triage` can depend on `rules` and `billing` if explicitly allowed
- `analytics` consumes events but does not reach back into command logic
- `integration.*` packages implement ports and stay out of domain policy code

This is the main reason the coarse-grained module split is still safe: **the real architecture lives inside `backend/core` packages, not only in Gradle folders**.

---

## 5. `backend/api`: the HTTP Shell

`backend/api` should stay thin. It is the inbound HTTP edge of the system.

It owns:

- `@SpringBootApplication`
- Spring Security / OAuth2 login
- REST controllers
- request DTOs and OpenAPI exposure
- Gmail Pub/Sub push controller
- payment webhook endpoints
- HTTP-only wiring such as filters, CORS, CSRF, session handling

It should **not** own business rules. Its job is:

```text
HTTP request
→ authenticate / validate
→ call a core use case
→ return response
```

### API-specific responsibilities

| Concern | Lives in `backend/api`? | Why |
|---|---|---|
| OAuth callback endpoints | Yes | HTTP edge concern |
| Gmail push endpoint | Yes | Google pushes HTTP to your app |
| REST API for UI | Yes | frontend boundary |
| Payment webhooks | Yes | external HTTP boundary |
| Triage scoring rules | No | belongs in core |
| Credit ledger logic | No | belongs in core |

---

## 6. `backend/worker`: the Async Shell

`backend/worker` is the background runtime.

It owns:

- `@SpringBootApplication`
- scheduled jobs
- outbox consumers / job runners
- Gmail history processing loop
- watch renewal scheduler
- reservation sweeper / recovery jobs
- analytics/event projection workers

It should contain **runtime plumbing**, not a second copy of business logic.

### Worker-specific responsibilities

| Concern | Lives in `backend/worker`? | Why |
|---|---|---|
| Polling `triage_job` / outbox rows | Yes | background work |
| Renewing `users.watch` grants | Yes | scheduled work |
| Sweeping stuck credit reservations | Yes | scheduled recovery |
| Executing LLM-backed triage | Indirectly via core | worker triggers core orchestration |
| Defining rule thresholds | No | belongs in core |

---

## 7. Runtime Model

At runtime, this architecture looks like:

```text
Browser / Gmail / Payment Provider
          │
          ▼
     backend/api
          │
          ▼
      PostgreSQL
     (state + outbox)
          │
          ▼
    backend/worker
          │
          ├── Gmail API
          ├── OpenRouter / BYOK providers
          ├── Redis
          └── Payment provider APIs (when needed)
```

### Important clarification

Even if `backend/api` and `backend/worker` run as separate Cloud Run services, this is still one logical backend system because:

- both depend on the same `backend/core`
- both use the same schema and transaction boundaries
- neither owns a separate public contract
- they must version and ship together

So this is **not** “many services” in the microservice sense.

---

## 8. Key Data Flows

### 8.1 Inbound triage

```text
Gmail users.watch
→ Google Pub/Sub
→ HTTP push to backend/api
→ validate OIDC JWT
→ persist inbound notification + enqueue internal job/outbox row
→ backend/worker claims job
→ call Gmail history.list
→ emit one message-level processing unit
→ evaluate structural rules
→ if needed reserve credits + call LLM
→ settle/release credits
→ apply Gmail action
→ project analytics/audit
```

Critical rule: **ack the external push fast** in `backend/api`; heavy work happens in `backend/worker`.

### 8.2 User requests a draft

```text
Browser
→ backend/api
→ core drafting use case
→ reserve credits
→ call LLM
→ create Gmail draft
→ settle credits
→ return draft metadata
```

This can stay sync in v1 if latency is acceptable. If not, convert it to the same outbox/worker pattern later.

### 8.3 Payment webhook

```text
Payment provider webhook
→ backend/api
→ verify signature
→ core billing command
→ ledger settlement
→ balance available to UI and worker flows
```

---

## 9. Persistence and Async Model

### PostgreSQL responsibilities

Postgres remains the source of truth for:

- users / tenants / account linkage
- rules
- audit history
- billing ledger
- draft metadata
- inbox processing state
- outbox / `processing_job` tables

### Redis responsibilities

Redis stays out of the billing critical path. It is only for:

- rate limiting
- short-lived idempotency keys
- caches
- ephemeral worker coordination if truly needed

### Internal queue model

Use Postgres-backed jobs with `FOR UPDATE SKIP LOCKED` for v1.

This keeps the system simple:

- Pub/Sub handles external ingress retries
- Postgres handles internal durability / claiming
- `backend/worker` scales horizontally without needing Kafka/RabbitMQ yet

---

## 10. Dependency Rules

At the Gradle level:

```text
backend/api    → backend/core
backend/worker → backend/core
apps/web       → generated OpenAPI client
```

Forbidden:

```text
backend/api    ↔ backend/worker   (no direct compile dependency)
apps/web       → backend/core     (frontend talks only through HTTP/OpenAPI)
```

At the package level inside `backend/core`:

- `shared` can be used widely
- bounded contexts talk through explicit interfaces/events
- `integration.*` implements ports, not policies
- no package reaches across boundaries unless Modulith rules allow it

---

## 11. Why This Is Better Than the Old Deep Split

The older plan had better theoretical isolation, but too much ceremony:

- too many Gradle modules for a solo/small v1
- more build wiring
- more “where does this class live?” friction
- harder refactors while requirements are still moving

This new shape gives the important wins with less cost:

- separate frontend toolchain
- separate HTTP and worker runtimes
- package-enforced modularity inside shared core
- future split path remains available

---

## 12. Scaling Path

| Scale | Adjustment |
|---|---|
| 0-1k users | Small `api` + `worker`, one Postgres, one Redis |
| 1k-10k users | Scale `worker` independently from `api`; keep same core |
| 10k-100k users | Externalize selected events to Kafka/Pub/Sub if Postgres queue becomes the bottleneck |
| 100k+ | Revisit true service extraction only where ownership, throughput, or compliance requires it |

The likely first scaling lever is **more worker replicas**, not more architectural fragmentation.

---

## 13. Implementation Order

1. Create the monorepo structure: `apps/web`, `backend/core`, `backend/api`, `backend/worker`
2. In `backend/core`, create package roots: `shared`, `identity`, `mail`, `rules`, `triage`, `drafting`, `billing`, `analytics`, `integration.*`
3. Add Spring Modulith modularity verification tests in `backend/core`
4. Bring up `backend/api` with security, OpenAPI skeleton, OAuth, and Gmail push endpoint
5. Bring up `backend/worker` with outbox/job claiming and watch renewer
6. Wire the triage path end-to-end through `backend/core`
7. Add billing, then drafting, then analytics

---

## 14. Sources

- Spring Modulith docs via Context7:
  - `ApplicationModules.of(...).verify()` for modularity verification
  - `@NamedInterface` for explicit internal API surfaces
  - `@ApplicationModule` for package-based module metadata
- Gmail API push docs and `users.watch` docs
- Spring Boot 4.0.6 system requirements and release notes
- Existing stack research in [STACK.md](./STACK.md)

---

## Bottom Line

The architecture is now:

**a pragmatic modular monolith with split API/worker executables**

not:

- a giant single Spring module with no boundaries
- a many-module DDD maze
- a microservice system

That is the right level of structure for Zero Mail v1.
