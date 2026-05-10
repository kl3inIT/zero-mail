<!-- GSD:project-start source:PROJECT.md -->

## Project

**Zero Mail (placeholder name)**

Zero Mail is a multi-tenant SaaS that helps busy professionals and founders reach inbox zero in Gmail by using AI to auto-triage, categorize, archive, and draft replies to incoming email based on user-defined natural-language rules. It is an architectural re-build inspired by Inbox Zero (https://github.com/elie222/inbox-zero), but with a Java 25 / Spring Boot 4 backend, Spring AI for model orchestration, and our own branding.

**Core Value:** **AI auto-triage that users trust with their real inbox.** If triage quality, safety (no destructive actions, no data leakage), and reliability aren't excellent, nothing else matters — users will uninstall the Gmail grant within a day.

### Constraints

- **Language/runtime**: Java 25 — locked by user directive.
- **Framework**: Spring Boot 4 — locked by user directive.
- **Build**: Gradle 9.x with Kotlin DSL — locked by user directive.
- **Versioning policy**: Prefer the latest stable versions compatible with the chosen deployment platform. Only use a pre-release when explicitly pinned by the user. Current exception: **Spring AI 2.0.0-M5**.
- **AI**: Spring AI **2.0.0-M5** for LLM orchestration (model abstraction, prompts, tool calls) — locked by user directive.
- **Structure**: Monorepo / multi-module Gradle project — locked by user directive. Backend topology is locked to **`backend/core` + `backend/api` + `backend/worker`**, with `apps/web` as the separate frontend module. Internal backend boundaries stay package-based inside `backend/core`, enforced by Spring Modulith verification and architectural tests.
- **Frontend**: Next.js / React as a separate module inside the monorepo — locked by product decision.
- **Mail provider (v1)**: Gmail / Google Workspace only, via Gmail API + Google Pub/Sub push — locked by product decision.
- **Distribution (v1)**: Self-hosted SaaS on a single VPS for the current deployment; managed cloud can be revisited later.
- **LLM routing**: Default via OpenRouter behind Spring AI; BYOK supported — locked by product decision.
- **Billing model**: Prepaid credits, pay-as-you-go; unit economics TBD — locked direction, details deferred.
- **Privacy**: No long-term storage of raw email bodies, LLM prompts/completions, or embeddings. Content always sanitized + truncated + prompt-injection-hardened before hitting any LLM — locked.
- **Write actions allowed in v1**: label, archive (skip inbox), save Gmail draft. **Auto-send is forbidden.**
- **Primary datastore**: PostgreSQL self-hosted on the same VPS as the app. Redis also runs on the same VPS for cache / session / rate-limit infrastructure only; vector DB is deferred.
- **Schema migrations**: Liquibase with YAML changelogs — locked by user directive.
- **Timeline**: Exploratory project — learning-oriented, no hard ship deadline. Favor architectural quality and defensibility over speed.

### Backend Code Style

- **Enterprise readability**: Backend Java code must use explicit, domain-revealing names for fields, parameters, locals, and lambda variables. Avoid opaque abbreviations such as `req`, `res`, `repo`, `svc`, `cfg`, `ctx`, `msg`, `err`, `ex`, `e`, `conn`, `tx`, or one-letter variables. Prefer names like `request`, `response`, `userRepository`, `gmailConnectionService`, `configurationProperties`, `tenantContext`, `gmailMessage`, `authenticationException`, `connection`, and `transactionTemplate`. Exceptions are allowed only for established technical acronyms (`ID`, `DTO`, `JPA`, `OAuth2`, `OIDC`, `URL`, `URI`, `HTTP`), generated API names, or intentionally ignored lambda parameters (`_`).
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

> Full tables, alternatives, version compatibility, and sources live in [`.planning/research/STACK.md`](.planning/research/STACK.md). Keep this section as the **prescriptive TL;DR** only.

- **JDK 25 LTS** (GA 2025-09-16) via Gradle toolchains.
- **Gradle 9.4.1** + **Kotlin DSL** + `libs.versions.toml` catalog, multi-project (not composite).
- **Spring Boot 4.0.6** (current GA — stay on 4.0.x for production).
- **Spring Framework 7.0.7**, **Spring Security 7.0.5**, **Jakarta Servlet 6.1**, **Jakarta Persistence 3.2**, **Jackson 3.1.2** (Boot-managed).
- **Spring AI 2.0.0-M5** via OpenAI, Anthropic, Google GenAI, and DeepSeek starters. Platform OpenRouter routing uses the OpenAI adapter with `base-url: https://openrouter.ai/api/v1`; official BYOK providers use their native Spring AI adapters where available. Keep all direct Spring AI usage inside one LLM adapter module — M5 → GA churn still possible.
- **No GCP hosting baseline** — do **not** add `spring-cloud-gcp` starters by default. Gmail push arrives as plain HTTP POSTs to a Spring MVC controller on the VPS.
- **PostgreSQL 17.6 self-hosted on VPS** + **Liquibase 5.0.2 (YAML changelogs)** + **Spring Data JPA (Hibernate 7)** for aggregates, **Spring Data JDBC** for read-side and hot paths, **JSONB + jsonb_path_ops** for rule matchers, **AES-GCM at app layer** for OAuth refresh-token encryption.
- **Redis 7.2 self-hosted on VPS** (Spring Data Redis + Lettuce) for rate limiting, idempotency, session store, per-tenant ChatModel cache — **NOT a queue**.
- **Queue = Postgres-backed** (single `outbox` + `processing_job` table with `SKIP LOCKED`). No Kafka, no RabbitMQ in v1. Pub/Sub already handles ingress retries.
- **Next.js 16.2.4 (App Router) + React 19.2.5** in `apps/web`, **pnpm 11.0.8 + Turborepo 2.9.6**, **TanStack Query 5.100.1**, **shadcn/ui + Tailwind CSS 4.2.4**, typed client via **OpenAPI codegen** (`openapi-typescript` 7.13.0 + `openapi-fetch` 0.17.0) from Spring's `springdoc-openapi` output.
- **Auth**: Spring Security OAuth2 Client (Google), **server-issued signed session cookie** (not stateless JWT). Same-origin to Next.js; `HttpOnly`, `SameSite=Lax`, `Secure`. Spring Session backed by Redis.
- **Deploy**: **single VPS** hosting reverse proxy + `apps/web` + `backend/api` + `backend/worker` + PostgreSQL + Redis. Public HTTPS endpoint for Gmail Pub/Sub push, OIDC token verification in the controller.
- **Container**: `eclipse-temurin:25-jre-noble` built via Spring Boot CDS + AOT layered images.
- **Observability**: Micrometer + **OpenTelemetry Java agent 2.16.0**, OTLP → **Grafana Cloud** (Tempo/Loki/Mimir). Spring AI prompt/completion capture **disabled**.

### Hard "do not use" list

- **Lombok** (lags JDK; use records + explicit builders).
- **Jackson 2.x assumptions** (Boot 4 ships Jackson 3.x).
- **Spring WebFlux** (use Spring MVC + virtual threads via `spring.threads.virtual.enabled=true`).
- **`javax.*`** packages (Jakarta-only).
- **Raw HTTP LLM calls or vendor SDK usage outside the Spring AI adapter**. Provider-specific BYOK client derivation is allowed only inside `core.llm.gateway.springai` when Spring AI M5 requires it.
- **Storing LLM prompts/completions in logs or DB** (privacy constraint).
- **Polling Gmail** (use Pub/Sub push + `users.watch` refresh).
- **`pgp_sym_encrypt` (pgcrypto) for OAuth tokens** (key in DB → key leak on DB leak; use app-layer AES-GCM).
- **Gradle Node plugin** for the Next.js build (slow, fights Turborepo cache).
- **Kafka / RabbitMQ in v1** (Pub/Sub + Postgres `SKIP LOCKED`).
- **Stateless JWT user sessions** (cookie + Redis-backed Spring Session).
- **Embedding store / vector DB in v1** (privacy constraint forbids embeddings of user mail).

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

> Detailed examples and anti-patterns live in [`CONVENTIONS.md`](CONVENTIONS.md). Read that file before introducing patterns in the listed areas.

1. **Thin controllers + service-owned `@Transactional`** — controllers translate HTTP ↔ core contracts and never inject repositories. Response DTOs own `from(...)` mapping.
2. **Backend domain package layout** — do not add ambiguous `core.<domain>.model.*`. Use `domain/` for business vocabulary, `application/` for use-case services/commands/results, `projection/` for read-side snapshots, `exception/` for business exceptions, and `persistence/` for DB concerns. `backend/api` keeps `controllers/`, `dto/`, `error/`, `security/`, `config`, but controllers are grouped under `controllers/<domain>/` and DTOs under `dto/<domain>/`.
3. **Records for DTOs, classes for entities, Lombok-free** — Java 25 records for all DTOs/value objects; entities stay `class` for Hibernate proxies; no Lombok anywhere.
4. **Enum state machines via `OrderedEnum` / `IdentifiedEnum` + static `fromId` fail-loud** — never use `ordinal()` for storage or comparison; `fromId` throws `NoSuchElementException` on unknown ids.
5. **Privacy logging format** — every log line is `event=<name> tenantId={}` + structured fields; no email, no Google subject, no token bytes, no message body, no prompts/completions.
6. **Direct calls vs Spring Modulith events** — use direct service calls for commands needing immediate results or transaction safety (OAuth provisioning, credit reserve/settle/release, Pub/Sub ingestion, account deletion cleanup). Use Spring Modulith events for in-process after-commit side effects such as message-observed → future triage/rules work, Gmail state changes, top-up credited, onboarding completed, and non-critical account-deleted reactions. Spring events do **not** cross `backend/api` ↔ `backend/worker` processes; cross-process handoff must use PostgreSQL-backed outbox / processing tables. Domain events shared by API/worker/future modules belong in `backend/core`, not `backend/api`.
7. **UI primitive selection** — check shadcn/ui first; install via `pnpm dlx shadcn@latest add <component>`; compose around `@/components/ui/*`. Treat `apps/web/components/ui/**` as copied primitive source (excluded from ESLint/Prettier).
8. **Frontend feature API/hooks/query keys/tests** — keep small feature HTTP functions in `features/<feature>/api/<feature>-api.ts`, TanStack Query key factories in `features/<feature>/query-keys.ts`, and one hook file per use case. Do not create query keys for mutation-only features unless they own cached data. Playwright specs live only in `apps/web/e2e/**`; Vitest feature tests live beside feature code or in `apps/web/__tests__/**` for app-wide contracts.
9. **Subproject-owned configuration files** — each runnable subproject owns its own runtime config. API-only properties belong in `backend/api/src/main/resources/application.yml`; worker-only properties belong in `backend/worker/src/main/resources/application.yml`; Next.js/web config belongs under `apps/web`. Shared typed config classes may live in `backend/core`, but each runnable module still declares its own values/defaults in its own config file.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase. Detailed research lives in [`.planning/research/ARCHITECTURE.md`](.planning/research/ARCHITECTURE.md).

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.

<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.

<!-- GSD:workflow-end -->

## Tooling

**JetBrains MCP first.** This project is opened in IntelliJ IDEA with the JetBrains MCP server attached, so its tools see the live project index (symbols, deps, problems). Prefer them over generic file/shell tools for project-aware operations:

- **Read/search code**: `mcp__jetbrains__get_file_text_by_path`, `mcp__jetbrains__search_in_files_by_text`, `mcp__jetbrains__search_in_files_by_regex`, `mcp__jetbrains__search_symbol`, `mcp__jetbrains__get_symbol_info` — over `Read` / `Grep` when you need symbol-aware results or are inspecting Java/Kotlin sources.
- **Diagnose**: `mcp__jetbrains__get_file_problems` after meaningful Java edits, before declaring done (matches existing memory rule).
- **Refactor/rename**: `mcp__jetbrains__rename_refactoring`, `mcp__jetbrains__replace_text_in_file`, `mcp__jetbrains__reformat_file` — over manual `Edit` for cross-file renames or formatting.
- **Build/run**: `mcp__jetbrains__build_project`, `mcp__jetbrains__execute_run_configuration`, `mcp__jetbrains__get_project_dependencies`, `mcp__jetbrains__get_project_modules` — over raw `gradle` shell calls when an existing run config or the module graph already answers the question.

Fall back to `Read` / `Grep` / `Edit` / `Bash` when JetBrains MCP is unavailable, when working outside Java/Kotlin (e.g. `.gitignore`, YAML, shell), or when the operation is purely text-level (simple file create, single-line edit, git commands).

**Postgres MCP Pro for database work.** PostgreSQL is the primary datastore (and the v1 queue). Prefer Postgres MCP tools over `psql` shell calls for any inspection, diagnostics, or query work:

- **Schema introspection**: `mcp__postgres__list_schemas`, `mcp__postgres__list_objects`, `mcp__postgres__get_object_details` — over hand-written `information_schema` queries when checking tables, columns, indexes, constraints.
- **Query diagnostics**: `mcp__postgres__explain_query`, `mcp__postgres__analyze_query_indexes`, `mcp__postgres__analyze_workload_indexes`, `mcp__postgres__get_top_queries` — when a query is slow, before adding an index, or when reviewing a Liquibase changelog that touches hot paths.
- **Health checks**: `mcp__postgres__analyze_db_health` — when investigating production-like issues (bloat, cache hit ratio, replication, vacuum).
- **Ad-hoc SQL**: `mcp__postgres__execute_sql` — for read-only verification queries against the dev DB. Never run destructive SQL through MCP without explicit user approval; prefer a Liquibase changelog for any schema change (per project policy).

**Playwright MCP for browser verification.** Frontend changes in `apps/web` must be verified in a real browser before declaring done — type-check passing is not enough (per project UX rule).

- **Verify UI**: `mcp__playwright__browser_navigate`, `mcp__playwright__browser_snapshot`, `mcp__playwright__browser_click`, `mcp__playwright__browser_fill_form`, `mcp__playwright__browser_take_screenshot` — drive the running `apps/web` dev server through the golden path + edge cases.
- **Debug**: `mcp__playwright__browser_console_messages`, `mcp__playwright__browser_network_requests`, `mcp__playwright__browser_evaluate` — when a UI change misbehaves, inspect console + network before guessing.
- **Auth flows**: use Playwright MCP to walk the OAuth login + Gmail-connect flow end-to-end whenever auth, session, or scope handling changes.

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
> The active profile lives in the user's global `~/.claude/CLAUDE.md` and is auto-loaded each session — keep this section as a placeholder to avoid duplication.

<!-- GSD:profile-end -->
