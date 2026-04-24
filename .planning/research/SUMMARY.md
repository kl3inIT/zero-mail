# Research Summary — Zero Mail

Synthesis of `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md`, `PITFALLS.md`. Consumed by requirements definition and the roadmapper.

## Key Stack (pinned, non-negotiable)

- Java 25 LTS · Spring Boot 4.0.6 · Spring AI **2.0.0-M4** behind `LlmGateway`
- spring-cloud-gcp 8.0.2 for **Secret Manager**; Gmail Pub/Sub push receiver stays a plain HTTP controller
- Gradle 9.4.1 + Kotlin DSL · Cloud SQL Postgres 17.6 + Liquibase 5.0.2 (YAML) + JPA · Memorystore Redis 7.2 (cache / session / rate-limit only — not in billing critical path)
- Monorepo layout locked to `apps/web` + `backend/core` + `backend/api` + `backend/worker`; internal backend boundaries enforced in `backend/core` via Spring Modulith + architecture tests
- Cloud Run + Cloud SQL + Memorystore + Secret Manager
- Next.js 16.2.4 / React 19.2.5 / Tailwind 4.2.4 / shadcn/ui / TanStack Query 5.100.1 / openapi-typescript 7.13.0; pnpm 10.33.2 + Turborepo 2.9.6 **outside Gradle**
- Cookie session (not JWT) · Virtual threads ON · **Scoped Values, never ThreadLocal** · no Lombok (Java records + pattern matching)

## v1 Feature Set

**Table stakes (T1–T20):** Google OAuth + Gmail scopes, Pub/Sub push ingestion, rule CRUD with NL → structured-matcher AST + live preview, Gmail write actions limited to **label / archive / save-draft** (never auto-send), per-action audit log + undo + global pause, metadata-only analytics, prepaid credit balance + BYOK, onboarding + daily digest.

**Lead differentiators to ship in v1:** conversational rule builder, template gallery, in-product privacy page, credits + "never auto-send" positioned as the trust story.

**Anti-features (16 locked out):** auto-send replies, Outlook/IMAP, RAG over mail bodies, full in-app mail client UI, native mobile, teams/seats, open-source distribution, attachment auto-filing.

## Build-Order Graph

```
Phase 1 — Foundation
  Scoped Values · log scrubbers · ArchUnit bans · @Sensitive wrapper
  Identity · Google OAuth · skeleton OpenAPI
  (kick off CASA restricted-scope verification NOW)
        │
        ├── Phase 2A — Mail Ingestion (Pub/Sub push, users.watch + 24h renewal, history.list, idempotent receiver)
        ├── Phase 2B — Billing (double-entry Postgres ledger, reserve/settle/release, credit hold watchdog)
        └── Phase 2C — LLM Gateway (sanitize → Unicode strip → injection guard → ChatClient cache → BYOK per-request options → metadata-only observability)
        │
        ▼
Phase 3 — Rules (NL → structured AST via Spring AI tool-call; evaluator runs w/o LLM; SEMANTIC_INTENT matchers batch into Phase 4)
        │
        ▼
Phase 4 — Triage Convergence (orchestrator, safety policy layer, audit, undo, shadow-mode default for first N triages, sender safety net) ← HERO
        │
        ├── Phase 5A — Drafting (tone-matched drafts, thread headers, In-Reply-To/References)
        ├── Phase 5B — Analytics (volume, time-saved, rule-hit metrics from metadata only)
        └── Phase 5C — Frontend (starts in parallel once Phase 1 OpenAPI stub exists)
        │
        ▼
Phase 6 — Polish + CASA-verified launch
```

## Top 5 Risks (phase-tagged)

1. **CASA OAuth restricted-scope verification (4–12 weeks, third-party lab, annual recert).** Submit at Phase 1 OAuth wiring, not before launch.
2. **`users.watch` silent 7-day expiry.** Daily `@Scheduled` renewal + per-tenant health alerting in Phase 2A.
3. **Prompt injection (Unicode tag smuggling, EchoLeak-class CVE-2025-32711).** Jsoup sanitize + NFC normalization + U+E0000–U+E007F strip + structured tool-call schema + per-action allow-list, all co-located in Phase 2C, gating Phase 4.
4. **ThreadLocal cross-tenant leak on virtual threads.** Scoped Values + ArchUnit ban on `ThreadLocal` + concurrent multi-tenant integration test from Phase 1.
5. **Body / prompt / completion logging.** `@Sensitive` wrapper + Logback scrub filter + ArchUnit ban on body/content field references; **must ship before Phase 2C**, not alongside it.

**Honorable mentions:** Pub/Sub OIDC verification, `invalid_grant` → tenant DISCONNECTED state, bounded history-404 recovery (no full mailbox rescan), credit reserve sweeper, 4k-token email cap + per-tenant daily spend cap, OpenRouter model pin + golden-set drift detection, draft `In-Reply-To`/`References` headers, shadow-mode default for new tenants' first N triages.

## Open Decisions (defer — do not lock in roadmap)

1. Credit unit economics — price per classify / draft / preview, and refund matrix per failure mode.
2. Tokenizer choice — model-family tokenizer vs. char heuristic; decide in Phase 2C.
3. **Vector DB stays out of v1** — privacy-incompatible; revisit only if stylometry feature (D2) later requires persistent derived features.
4. Observability vendor (Grafana Cloud recommended, not pinned).
5. Payment provider — Stripe vs. LemonSqueezy; pick in Phase 2B.
6. CASA tier — Tier 2 vs Tier 3, depends on whether any flow ever needs full `gmail` scope (v1 says no).
7. Managed-platform drift: Cloud SQL PostgreSQL 18 is still Preview and Memorystore tops out at Redis 7.2; revisit if you want community-latest engines instead of GCP-managed defaults.

## Research Flags for Roadmapper

- **Needs `/gsd-research-phase` before planning:**
  - Phase 2A — Gmail watch/history-id edge cases and OIDC push verification
  - Phase 2C — Spring AI 2.0.0-M4 exact builder API for per-request key + tokenizer strategy
  - Phase 5A — privacy-safe stylometry (only if D2 tone-matching is in scope)
- **Standard patterns — no extra research:** Phases 1, 2B, 3, 5B, 5C.

## Confidence

Stack: **MEDIUM-HIGH** · Features: **MEDIUM-HIGH** · Architecture: **MEDIUM-HIGH** (Spring AI 2.0.0-M4 BYOK builder API still needs in-code verification) · Pitfalls: **HIGH**. **Overall MEDIUM-HIGH — ready to define requirements and generate the roadmap, with one explicit review item around GCP-managed Postgres/Redis lag versus community-latest releases.**
