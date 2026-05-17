# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — MVP

**Shipped:** 2026-05-15
**Phases:** 17 | **Plans:** 123 | **Tasks:** 221

### What Was Built

- **Backend:** Java 25 / Spring Boot 4.0.6 / Spring Modulith / Spring AI 2.0.0-M6, three-module split (`backend/core` + `api` + `worker`), Postgres 17 with Liquibase YAML, Redis 7 for session/cache/rate-limit, Postgres-backed queue with `SKIP LOCKED` (no Kafka/RabbitMQ).
- **Frontend:** Next.js 16 / React 19 / Tailwind 4 / shadcn/ui / TanStack Query, Vietnamese-default + English secondary i18n with CI parity gate, fully typed OpenAPI client.
- **AI safety stack:** Single `LlmGateway` with Jsoup → NFC → Unicode-tag-strip → jtokkit truncate → tool-call allow-list → BYOK encrypted at rest → per-tenant daily spend cap → golden-set drift detection. ArchUnit confines vendor SDKs to one adapter package.
- **Hero feature:** Triage orchestrator (rules in order → safety policy → Gmail label/archive/save-draft) with immutable audit + 30-day undo + tenant-wide shadow mode + sender safety net. Auto-send architecturally blocked (ArchUnit + safety policy + repo-wide grep gate).
- **Trust posture:** ScopedValue tenant context, `@Sensitive` Logback scrub end-to-end, multi-tenant virtual-thread leak test, no long-term storage of bodies/prompts/completions/embeddings, AES-GCM refresh-token + BYOK key encryption with per-call zeroing.
- **Launch:** Single-VPS deployment baseline, 50-tenant load test 4/4 invariants PASS, Playwright golden-path covers sign-up → connect → rule → push → triage → undo → draft → analytics, signed LAUNCH-GO-NOGO, `v1.0.0-rc1` tagged.

### What Worked

- **Spring Modulith JDBC event spine** for cross-module commands (e.g. `MailMessageObserved` → `TriageOrchestratorService.@ApplicationModuleListener`) — gave us async decoupling without Kafka, in-process events for `backend/core`, and DB-backed cross-process handoff via outbox/processing tables.
- **ArchUnit + repo-wide grep as the auto-send safety floor.** A single negative test (`NoGmailSendAllowedTest`) plus a grep gate proved zero `messages.send` / `drafts.send` / `drafts.update` call sites in production code. Trust story stays defensible.
- **Phase 1 safety scaffolding paid for itself.** Every later phase inherited Scoped Values, `@Sensitive`, Logback scrub, ArchUnit bans without re-litigation. `MultiTenantLeakIntegrationTest` was cited as PASS in 4 different downstream verifications.
- **3-source requirement traceability.** REQUIREMENTS.md ↔ phase VERIFICATION.md ↔ SUMMARY frontmatter cross-reference caught the Phase 1 paperwork gap that would have been invisible with single-source verification.
- **Inbox Zero pivot.** Closing Phase 1.4 (two-leg `google-gmail` registration) when a simpler bundled-OAuth model (Phase 1.5) emerged saved cycles. The "close phase without ship, supersede with next" pattern works.
- **Bundled OAuth scopes upfront** (vs Google's incremental authorization two-leg pattern) — better UX, simpler architecture, matches Inbox Zero. Saved as feedback memory.
- **Yolo + JetBrains MCP + Postgres MCP + Playwright MCP loop.** Symbol-aware refactors, schema introspection without `psql`, browser-verified UI changes — cut iteration time materially vs raw shell.

### What Was Inefficient

- **VERIFICATION.md generation gap.** 4 of 17 phases (01, 01.3, 02C, 06) shipped without a top-level VERIFICATION.md. Code/tests were green, but the audit workflow flagged 11 Phase 1 REQ-IDs as "orphaned" because no VERIFICATION.md anchored them. Cost a closure pass at milestone-audit time.
- **REQUIREMENTS.md checkbox drift.** AUTH-03..06 + FND-01..07 stayed `[ ]` long after Phase 1 closed, because nothing in the execute-phase loop required flipping them. Phase verifier should auto-flip on PASS.
- **Stale VALIDATION.md flags.** 5 phases (01.1, 01.2, 01.4, 01.6, 06) never had `nyquist_compliant: true` / `wave_0_complete: true` flipped at closure even though tests passed. Documentation hygiene drift.
- **Phase 1.4 wasted cycles** before the Inbox Zero realization. The mismatched-account two-leg architecture was over-engineered for the actual user model. Lesson: spend more time studying the reference repo before designing.
- **Quick-task accretion.** 32 `.planning/quick/` directories accumulated over the milestone; none had completion SUMMARYs. Useful execution traces but they should auto-archive on completion, not pile up.
- **Code-review delegation skipped on Phases 4 / 5A / 5B.** Codex runtime couldn't auto-spawn `gsd-code-reviewer`. Recommended `$gsd-code-review --depth=standard` was punted — risk of latent issues not caught.
- **Frontend-design skill discovery late.** Took until Phase 1.4 to consistently invoke `frontend-design` before UI work. Earlier discipline would have prevented the wrapper-primitive bloat that 1.5 had to deflate.

### Patterns Established

- **Bundle login + Gmail scopes in one OAuth flow.** Reject Google's incremental-authorization two-leg pattern (saved as feedback memory).
- **`OrderedEnum` + `IdentifiedEnum` + static `fromId` fail-loud** instead of `ordinal()` for enum storage/comparison. Phase 1.2.1 standardized; downstream phases (2A/2B/2C/3/4) followed without prompt.
- **Direct calls vs Spring Modulith events split:** direct service calls for transactional commands (OAuth provisioning, credit reserve/settle/release, Pub/Sub ingestion); Modulith events for in-process after-commit side effects. Documented in CONVENTIONS.md.
- **Privacy logging format:** `event=<name> tenantId={}` + structured fields; never email/subject/token/body/prompt/completion. Repo-wide grep tests enforce.
- **Vietnamese-first communication style.** Mirror Vietnamese in prose; English in code, class names, technical terms; expect code-switching mid-sentence (saved as user memory).
- **`/gsd:phase --insert <N>` for tactical mid-milestone re-plans** (1.1, 1.2, 1.2.1, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2 inserted phases). Decimal phase numbering kept the urgency clear.
- **Wave 0 RED test scaffolding** as the first plan in every phase. Tests compile but fail by design; subsequent plans flip them green incrementally. Caught contract drift early in 12+ phases.
- **HTML prototype + UI-SPEC.md per UI phase.** Every `/gsd-ui-phase` produced a self-contained `<phase>-PROTOTYPE.html` for visual review before planning (saved as feedback memory).

### Key Lessons

1. **Backfill VERIFICATION.md as part of phase closure, not as a separate audit step.** When a phase ships without one, downstream milestone audit treats its requirements as orphaned. The phase-verifier should be required, not optional.
2. **Auto-flip REQUIREMENTS.md checkboxes from VERIFICATION.md status.** Manual flip drift is high; the SDK should do it.
3. **Architecture phases (no REQ-IDs) still need a closure marker.** Phase 1.3 / Phase 6 are validation/scaffold work; the milestone-audit workflow currently flags them as "missing VERIFICATION.md" with no escape hatch. Either generate a thin VERIFICATION.md or mark the phase explicitly `verification: not_required` in PLAN.md.
4. **Inbox Zero is a UX/feature reference, never a code reference.** Java/Spring + TS/Node ports badly; mental models port well. Re-read inbox-zero/ before designing any user-facing flow.
5. **Single-VPS baseline forces good queue/cache/session decisions.** Postgres `SKIP LOCKED` + Redis Lettuce + ShedLock removed three vendor decisions (Kafka, RabbitMQ, Memcached, vector DB). Fewer moving pieces → simpler ops story for launch.
6. **Trust posture is checkable, not claim-able.** ArchUnit + grep + privacy-sweep tests turn the trust story into compile-time + CI gates. The LAUNCH-GO-NOGO can cite specific tests, not just promises.
7. **Vietnamese-default i18n is non-trivial.** Phase 1.1's stable error-code-with-params contract (no server-built localized strings) was the right call but took an entire phase + CI parity gate to lock down. Plan budget appropriately for any non-English-default product.
8. **Spring AI 2.0.0-M6 → GA churn is real.** Confining all Spring AI usage to `core.llm.gateway.springai` was the right hedge — when M6 → GA happens, only one module changes.

### Cost Observations

- **Sessions:** ~30+ across the milestone (estimate; not tracked precisely).
- **Model mix:** predominantly Opus 4.6 / 4.7 with planning/research subagents on Opus, executors on Sonnet/Haiku per `gsd-sdk` profile.
- **Notable:** Phase 4 (triage hero) and Phase 5B (draft replies) had the highest LLM-eval costs because they required real classifier accuracy (DRFT-04 gated by 22-fixture eval; passed 22/22 = 100%). Drift detection is cron-disabled by default in dev.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | ~30+ | 17 | Established Wave 0 RED test scaffolding, Spring Modulith JDBC event spine, ArchUnit safety floor, single-VPS deployment baseline, Vietnamese-default i18n. |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|--------------------|
| v1.0 | Backend `clean check` 4m15s; Frontend Vitest 39 files / 236 tests; Playwright 67 passed | Not measured (no JaCoCo gate) | jtokkit, jakarta.mail, ShedLock, next-mdx-remote, gray-matter, Resend SDK, Thymeleaf — all justified single-purpose adds. |

### Top Lessons (Verified Across Milestones)

*(Need v1.1 to cross-validate. Track these as candidates:)*
1. Wave 0 RED scaffolding catches contract drift early.
2. Trust story must be CI-gated, not document-only.
3. Phase verifier + REQUIREMENTS.md flip should be coupled to avoid orphan requirements.
