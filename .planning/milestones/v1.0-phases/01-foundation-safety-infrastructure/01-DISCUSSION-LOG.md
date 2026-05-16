# Phase 1: Foundation & Safety Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `01-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-04-24
**Phase:** 01-foundation-safety-infrastructure
**Areas discussed:** A. Module scaffolding, B. Tenant-context plumbing, C. OAuth scope progression, D. Guided onboarding, E. Log-scrubbing, F. OpenAPI & frontend depth, G. Token encryption key design

---

## A. Module Scaffolding & Build Setup

### A1. Which modules to scaffold in Phase 1?

| Option | Description | Selected |
|--------|-------------|----------|
| Three backend + apps/web types | `backend/core` + `backend/api` active, `backend/worker` shell only, `apps/web` types + minimal routes | ✓ |
| All four modules, worker empty | All four scaffolded with real UI | |
| backend/core + backend/api only | Worker and apps/web deferred entirely | |

**User's choice:** Three backend + apps/web types (recommended).

### A2. Gradle Kotlin DSL build structure

| Option | Description | Selected |
|--------|-------------|----------|
| buildSrc convention plugins | Shared config via `zeromail.*-conventions` plugins | ✓ |
| Per-module boilerplate | Duplicate config per module, refactor later | |
| Included build (build-logic/) | Alternative to buildSrc for large projects | |

**User's choice:** buildSrc convention plugins (recommended).

### A3. Spring Modulith wiring timing

| Option | Description | Selected |
|--------|-------------|----------|
| Wire Modulith now | Dependencies + `tenant`/`auth`/`privacy` modules + verify test | ✓ |
| Defer to Phase 2A | Add when real bounded contexts exist | |
| Dependency only, no module definitions | Compromise option | |

**User's choice:** Wire Modulith now (recommended).

---

## B. Tenant-Context Plumbing

### B1. Where the tenant-id Scoped Value gets bound

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Security filter | OncePerRequestFilter after auth, reads tenantId from principal | ✓ |
| Method-level aspect (`@RequireTenant`) | AOP on controller entrypoints | |
| Servlet Filter before Security | Earliest binding, but no authenticated principal yet | |
| HandlerInterceptor | After Security, bypassed by async/streaming | |

**User's choice:** Spring Security filter (recommended).

### B2. Hibernate multi-tenancy mode

| Option | Description | Selected |
|--------|-------------|----------|
| DISCRIMINATOR + Scoped-Value resolver | Shared schema, `tenant_id` column, `@TenantId`, custom `CurrentTenantIdentifierResolver` | ✓ |
| SCHEMA mode | Schema-per-tenant — rejected by CLAUDE.md | |
| DATABASE mode | Database-per-tenant — too premature | |

**User's choice:** DISCRIMINATOR with Scoped-Value resolver (recommended). Confirmation of CLAUDE.md direction.

### B3. FND-05 cross-tenant leak test scope

| Option | Description | Selected |
|--------|-------------|----------|
| Both, ship propagation helper now | Request-path + virtual-thread fan-out test + `TenantAwareTaskScope` helper | ✓ |
| Request-path only in Phase 1 | Defer async-propagation test + helper to Phase 2A | |
| Write helper, defer test | Untested code guides later phases | |

**User's choice:** Both, ship propagation helper now (recommended).

---

## C. OAuth Scope Progression & DISCONNECTED Recovery

### C1. OAuth scope flow in Phase 1

| Option | Description | Selected |
|--------|-------------|----------|
| Two-step incremental | `openid profile email` at sign-in; `gmail.modify` on Connect Gmail | ✓ |
| Single-step at first login | All scopes requested together at sign-in | |

**User's choice:** Two-step incremental (recommended).

### C2. How `invalid_grant` → DISCONNECTED is detected

| Option | Description | Selected |
|--------|-------------|----------|
| Lazy now, proactive probe in Phase 2A | Wrap outbound Gmail calls; extend Phase 2A `users.watch` renewal job for proactive detection | ✓ |
| Proactive probe now | Daily scheduled job in Phase 1 hits tokeninfo/userinfo across all tenants | |
| Lazy only, never proactive | Simplest, but silently-revoked tenants stay CONNECTED in UI | |

**User's choice:** Lazy now, proactive in Phase 2A (recommended).

---

## D. Guided Onboarding (AUTH-06)

### D1. How Phase 1 handles the template-rule step

| Option | Description | Selected |
|--------|-------------|----------|
| Placeholder persistence | `onboarding_selections` table captures picks; Phase 3 materializes real rules | ✓ |
| Visual-only stub | Screen shown, Enable is no-op — user intent lost | |
| Skip template step, redefine AUTH-06 | Scope change to roadmap | |

**User's choice:** Placeholder persistence (recommended).

### D2. Onboarding state machine

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit `onboarding_step` enum column on users | Monotonic forward progress, trivial queries | ✓ |
| Derived from side-effects | No column, computed from joins + flags | |

**User's choice:** Explicit enum (recommended).

---

## E. Log-Scrubbing Policy

### E1 (initial). Scrub strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Belt-and-suspenders | `@Sensitive` + ArchUnit + full regex pattern scan | (not picked) |
| Allow-list only | Contract-based, no runtime scan | (not picked) |
| Pattern scan only | Weakest option | |

**User's freeform response:** "Phase 1: defer advanced log scrubbing. Follow JHipster-style baseline logging first: Spring AOP logging aspect for service/debug tracing + Logback configuration with structured/centralized logging readiness. Keep logs safe by convention for now: no email bodies, prompts, tokens, or raw user content in logs. Design Logback output so it can later plug into Grafana/Loki or Logstash/ELK. Revisit sensitive wrapper + scrub filter in a later security/observability phase."

**Assistant flagged conflict:** FND-03 and FND-04 are explicitly mapped to Phase 1 in REQUIREMENTS.md and are the stated purpose of Phase 1 in ROADMAP.md. Deferring the entire structural contract conflicts with "ship safety infrastructure **before** phases that handle sensitive content."

### E1-revised. Reconciling JHipster baseline with FND-03/FND-04

| Option | Description | Selected |
|--------|-------------|----------|
| Baseline + minimal safety contract | JHipster AOP + structured Logback **and** `@Sensitive` + ArchUnit + thin scrub filter; full regex scan deferred | ✓ |
| Defer FND-03/FND-04 to later phase | Roadmap / requirements change | |
| Full belt-and-suspenders as originally planned | Ignore deferral preference | |

**User's choice:** Baseline + minimal safety contract (recommended compromise).

### E2. Thin filter behavior on match

| Option | Description | Selected |
|--------|-------------|----------|
| Redact + structured marker | `[REDACTED]` inline, `scrubbed=true`, `scrub_reason=sensitive_marker` | ✓ |
| Redact only | No structured marker, no alerting signal | |
| Drop the whole log line | Silent, loses both content and signal | |

**User's choice:** Redact + structured marker (recommended).

---

## F. OpenAPI Skeleton & Frontend Depth

### F1. Phase 1 OpenAPI endpoint surface

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 1 real endpoints only | No 501 stubs; spec versioned via `info.version` per phase | ✓ |
| Phase 1 + stubs for every planned endpoint | Premature shapes, false confidence | |
| Phase 1 + tags indicating future phases | Buys nothing over (a) | |

**User's choice:** Phase 1 real endpoints only (recommended).

### F2. `apps/web` depth in Phase 1

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum UI to exercise the contract | Scaffold + `/login` + `/onboarding` + `/settings` routes | ✓ |
| Types only, no UI | Violates FND-06 in spirit; breaks AUTH-06 verification | |
| Full scaffolding, login only | Would need `/onboarding` + `/settings` added back for AUTH-03/05/06 | |

**User's choice:** Minimum UI to exercise the contract (recommended).

---

## G. Token Encryption Key Design

### G1. Key design for OAuth refresh tokens

| Option | Description | Selected |
|--------|-------------|----------|
| Per-tenant DEK wrapped by KMS master (envelope) | Strongest blast-radius + rotation story; modest KMS cost | |
| Single global AES-GCM key from Secret Manager | Simplest; one key leak exposes all tenants | ✓ |
| Direct Cloud KMS encrypt/decrypt per token op | No key material in app; worst latency and cost | |

**User's choice:** Single global AES-GCM key (deviation from recommendation — simplicity prioritized over blast-radius containment for v1 prosumer scope). Accepted.

### G2. Key rotation schema design

| Option | Description | Selected |
|--------|-------------|----------|
| Design schema for rotation, ship v1 key | `{key_version, nonce, ciphertext}` envelope from day one | ✓ |
| Just ciphertext + nonce, no version column | Cheapest now, painful migration later | |

**User's choice:** Design schema for rotation, ship v1 key (recommended).

---

## Claude's Discretion

- Package naming inside `backend/core` beyond the Modulith module seams
- Liquibase changelog file naming/numbering
- ArchUnit rule organization (single class vs split)
- Redis session key prefix / TTL (defaults acceptable)
- CSRF token storage mechanism (Spring Security 7 default)
- Test framework specifics (JUnit 5 + Testcontainers)
- Docker image layering beyond Spring Boot CDS+AOT defaults

## Deferred Ideas

- Full runtime regex log pattern-scan — deferred to dedicated observability/security phase
- Proactive OAuth revocation probe — deferred to Phase 2A
- Key rotation job — schema ready, job deferred until operationally needed
- tenantId-bound MDC for log correlation — deferred to observability phase
- `@RequireTenant` controller aspect — rejected, kept as potential secondary safety net
- `gmail.readonly`-first scope progression — rejected; `gmail.modify` is needed for Phase 4 writes
