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

## Milestone: v1.1 — Email assistant chat (Phase 7 only)

**Shipped:** 2026-05-19
**Phases:** 1 (Phase 7) | **Plans:** 6 | **Tasks:** 31

### What Was Built

- **Chat backend (`core.chat` Modulith module):** 6 Liquibase changelogs (041–046), `ToolOutputSanitizer` runtime body-ban, `TenantAwareReactorScheduler` for tenant-safe fan-out, `ChatToolCallRegistry` + `ZeroMailChatMemory` adapter (workaround Spring AI #3366/#5167), SSE bridge with `VercelProtocolEmitter` + heartbeat + lifecycle, `LlmGateway.streamChat(...)` + `SpringAiLlmModelClient` with per-request `internalToolExecutionEnabled(false)`.
- **20 tools wired:** 7 read + 8 write-reversible + 3 confirm-required + 3 confirmed-send (sendEmail/replyEmail/forwardEmail). `AssistantSendExecutor` is the single carved-out send call site annotated `@AllowedSendCallSite`.
- **ArchUnit 3-layer carve-out:** paired negative + positive tests + CI grep gate that fail the build if Gmail send call-site count drifts from exactly 1. Atomic flip 0 → 1 landed in Wave 4 with the executor commit.
- **Confirmation state machine:** 5-min Redis lease + optimistic concurrency + persistence retry + same-tx audit row write + reconciliation cron. Per-race integration test covers double-click, stale `toolCallId`, confirm-during-stream.
- **3-layer body-ban defense for `chat_message.parts`:** `ToolOutputSanitizer` (runtime) + `ChatPersistenceContentBanTest` (ArchUnit) + `chat_message_body_ban` PostgreSQL trigger (DB). Multi-tenant chat leak integration test green.
- **Hardened system prompt:** XML-fenced personalization slot + suspicious-sender warning + evidence-vs-instruction separation. Confirmed sends to a sender-safety-net recipient render an extra-friction VIP banner on the preview card.
- **Frontend `/chat` route:** Next.js + `@ai-sdk/react@3` + AI Elements primitives + recipient-prominent preview cards + first-contact-domain friction + persisted-message-gating of Send + replay-mode rendering + Vietnamese-default chrome + chat history sidebar.

### What Worked

- **Three-layer defense for `chat_message.parts` body-ban** — runtime sanitizer + ArchUnit + DB trigger gave independent enforcement axes. CR-03 narrowed the trigger to the HTML regex gap (drop draft_body trigger overreach) — matched memory rule [[feedback-draft-body-carve-out-no-defense]].
- **Phase 7 as one coherent capability** — single phase landed backend module + send executor + ArchUnit flip + frontend in 6 plans across 4 days. No artificial phase split between backend and frontend.
- **`@AllowedSendCallSite` annotation + ArchUnit dual gate** — declarative carve-out beat manual "remember to update grep" maintenance. New send sites would have to be both annotated AND ArchUnit-allowlisted to compile.
- **Spring AI 2.0.0-M6 workaround via Zero Mail-owned `ChatToolCallRegistry`** — bypassed the framework bugs `spring-ai#3366`/`#5167` by reading from `chat_message.parts` directly instead of relying on Spring AI's broken in-memory cache.

### What Was Inefficient

- **Phase 8 scope discovery too late** — spec-phase 8 (2026-05-19) surfaced that Settings UI requires infrastructure (curated catalog source, per-feature picker schema) that didn't exist, AND that PR #40 brand palette shift (teal → purple) had silently invalidated visual baseline for *any* settings UI work. Discovery during /gsd-spec-phase 8 forced full Phase 8 defer to v1.2.
- **No hostile-corpus eval gate at v1.1 GA** — Phase 7 shipped chat surface with 3-layer technical defense but no behavioral validation. Eval suite design was bundled into Phase 8 which got deferred. v1.1 ships with implicit "trust the ArchUnit, no eval data."
- **Brand palette shift mid-milestone** (PR #40, 2026-05-19) — landed without milestone-level awareness. CSS variables propagated automatically but hardcoded color usage and visual hierarchy assumptions in Phase 7 chat UI now need v1.2 audit.
- **REQUIREMENTS.md "curated list only" Out-of-Scope lock** was aspirational, not enforced — v1.0 BYOK form has free-text model input and carries forward to v1.1 unchanged. Spec phase surfaced the contradiction.

### Patterns Established

- **3-layer defense for cross-cutting invariants** (runtime + arch test + DB trigger). Reusable template for future "this MUST never happen" rules.
- **Single-phase end-to-end capability** when scope is coherent and tightly coupled (Phase 7 = backend + executor + arch + frontend). Beats artificial backend/frontend split when contract surface is locked.
- **`@AllowedSendCallSite`-style declarative carve-out** for "exactly N instances allowed" invariants where N=1.
- **Solo-operator coherent-milestone preference** — interim YAML catalog rejected in favor of admin-console-first v1.2 sequencing. Saved to memory [[feedback-coherent-milestone-over-interim]].

### Key Lessons

- **Surface palette/brand-token changes at milestone level** — a single PR (#40) that flips primary color invalidates downstream UI work. Treat brand token changes like API breaking changes.
- **Spec-phase is the right gate to defer scope** — discovering Phase 8 doesn't fit during /gsd-spec-phase 8 → defer cleanly without writing SPEC.md is cheaper than locking spec then re-planning.
- **GA tag discipline = eval gate + ops dashboards + LAUNCH-GO-NOGO**, not just "merge to main." v1.1 ships `v1.1` tag without those — accept caveat in tag annotation, move discipline to v1.2.
- **For solo-operator projects, interim tooling rarely saves real work** — admin UI vs YAML edit are the same person's labor; build the proper tool when scope is coherent.

### Cost Observations

- **Sessions:** ~5 across the milestone (Phase 7 plan/execute + REVIEW + REVIEW-FIX + spec-phase 8 exit).
- **Model mix:** Opus 4.7 throughout (planning + execution); some Sonnet usage in executor subagents.
- **Notable:** Phase 7 had no LLM eval runs at GA — hostile-corpus deferred. Cost spike postponed to v1.2 hardening sweep.

---

## Milestone: v1.2 — Admin Console + User Settings UI

**Shipped:** 2026-06-01
**Phases:** 4 (8, 08.1, 9, + 08-bulk-unsubscribe) | **Plans:** 28 | **Tasks:** 62
**Requirements:** 70/73 complete (3 deferred to v1.3)
**Git range:** v1.1..HEAD — 525 commits, 2057 files (+198k / −24k), ~15 days

### What Was Built

- **Admin console (Phase 8):** WebAuthn passkey auth on a dedicated `@Order(1)` SecurityFilterChain, HMAC-chained append-only audit (DB-trigger-enforced), `AdminContext`↔`TenantContext` mutex, a standalone `apps/admin` Vite + React 19 SPA, master-key management for 6 providers, a curated catalog with 3-step Sync-from-`/models`, and metadata-only tenant/queue/spend dashboards behind ArchUnit + `AdminResponseBodyBanFilter` leak guards.
- **Rule actions (Phase 08.1):** DB-backed bilingual examples/personas catalog, expanded When/Then action schema, and runtime outbound execution of send/reply/forward behind one Auto-send setting + safety gates + a single ArchUnit-locked outbound gateway with fallback-to-draft.
- **User settings (Phase 9):** the single `/ai` surface (voice/behavior/updates/safety-net/BYOK) on the curated catalog, with in-memory-only generate-from-Sent privacy gates.
- **Bonus:** bulk-unsubscribe campaign (RFC 8058 + RFC 6068 gateways, throttled SKIP LOCKED dispatch, UNS-01..07).

### What Worked

- Parallel-agent execution landed real, verified code (08.1-06 outbound gateway shipped + verification PASS) even when the planning artifacts lagged — the code/tests were the source of truth.
- The privacy-by-ArchUnit posture extended cleanly to the admin surface: body/prompt/completion bans, single-send-call-site, and response-body failsafe all carried over from v1.0/v1.1.

### What Was Inefficient

- **Planning drift was severe:** `.planning/` declared "v1.2 in planning, Phase 08.1 in progress" while git was tagged through **v1.4.5** (388 commits past v1.2.0). Milestone artifacts (SUMMARY checkboxes, ROADMAP status, REQUIREMENTS traceability) were never kept in sync with the real branch, so the close required reconstructing 08.1-06's SUMMARY retroactively and re-deriving requirement status from code.
- **Runaway tagging:** an automated/parallel tag sequence inflated versions to v1.4.5 with no corresponding milestones; cleaned back to v1.2.0 at close.

### Patterns Established

- When a SUMMARY artifact is missing but the code + VERIFICATION exist, reconstruct the SUMMARY from commits/code rather than re-running the plan.
- Milestone-close requirement status is verified against the codebase (grep for the feature), not trusted from stale checkboxes.

### Key Lessons

- Keep planning artifacts and git tags in lockstep with execution — a 388-commit / 2-minor-version drift made the milestone close an archaeology exercise instead of a checkpoint.
- Tag discipline matters: auto-tagging without milestone gates produces meaningless version inflation.

### Cost Observations

- Long-running multi-session milestone with heavy parallel-agent work; exact model mix not instrumented. Notable: most rework at close was reconciliation overhead from planning drift, not feature work.

---

## Milestone: v1.3 — Gmail Workspace Foundation

**Shipped:** 2026-06-16
**Phases:** 2 (10, 11) | **Plans:** 12 | **Tasks:** ~40
**Requirements:** 43/43 complete
**Verification:** 11-UAT 10/10 PASS, live with two real Gmail mailboxes (2026-06-15)

### What Was Built

- **Phase 10 — Gmail mailbox foundation:** Liquibase 119 migration off the single-Gmail-per-tenant invariant (drop tenant-unique, add duplicate-active + primary partial indexes, backfill-to-primary preserving encrypted tokens/watch/history); mailbox-aware `GmailApiClientFactory.buildClientForMailbox` (cache re-keyed to `gmailConnectionId`, tenant adapter `@Deprecated` + ArchUnit allow-list); ownership seam `resolveOwnedConnectionOrThrow` (404/409); OAuth intent split (first-login vs add vs reconnect); connected-accounts REST (list / set-primary / disconnect / add / reconnect).
- **Phase 11 — Mailbox-scoped operation:** Liquibase 120-127 threading `gmail_connection_id` through Pub/Sub routing, observed/projection/event keys, per-connection history cursors, idempotency/template keys, `triage_audit` source/executing provenance, and global active-email uniqueness; mailbox-owned rules + copy-rules (clones disabled); `MailboxContext` ScopedValue + `MailboxBindingFilter` + active-mailbox endpoint + cross-account isolation tests + ArchUnit `findByTenantId` ban; web AccountMenu switcher (separate from workspace identity), `ActiveMailboxBadge`, copy-rules dialog, regenerated OpenAPI/feature-API types.

### What Worked

- **Wave 0 RED scaffolding + the two-CONNECTED-mailbox fixture** turned cross-account isolation into an executable contract drained incrementally across Waves 1-5.
- **`MailboxContext` ScopedValue mirrored the proven `TenantContext` pattern** — the ownership boundary (shared workspace vs isolated mailbox) became an ArchUnit-enforced compile-time invariant, not a convention.
- **Live UAT with two real Gmail mailboxes was the decisive gate.** It found two bugs that green CI missed and confirmed end-to-end isolation, confirmed-send-from-correct-mailbox, and clean privacy logs.

### What Was Inefficient

- **Tracking-doc drift again (same failure mode as v1.2).** STATE/ROADMAP/REQUIREMENTS froze at 2026-06-09 (Plan 11-05) while Plan 11-06 shipped 2026-06-10 and UAT live-verified 2026-06-15. Milestone close had to reconcile 9 "pending" requirements that were actually complete — the close was a reconciliation pass, not a checkpoint.
- **The 11-05 cross-account isolation test mocked the live Gmail path, so it missed the projection-read leak (T5, a blocker).** A unit/integration test that mocks the exact branch the bug lives in gives false confidence; the real-DB regression test added during UAT is what actually pins it.

### Patterns Established

- **Mirror `TenantContext` for any new request-scoped isolation axis** (ScopedValue + binding filter after tenant/before Hibernate + ownership-revalidating resolver + ArchUnit ban on the unscoped lookup).
- **Cross-cutting isolation needs a real-DB regression test, not a mock of the isolated path** — mocking the branch under test hides leaks.
- **Live multi-account UAT is mandatory before closing an isolation milestone** — CI green is necessary but not sufficient.

### Key Lessons

- **Update tracking docs at plan/UAT close, not at milestone close.** Two milestones running (v1.2, v1.3) ended in a reconciliation pass because docs lagged execution. Couple REQUIREMENTS flip + STATE update to plan completion and UAT sign-off.
- **GA tag has now slipped three milestones (v1.1 → v1.2 → v1.3).** Hostile-corpus eval, Grafana, CASA refresh, and LAUNCH-GO-NOGO keep deferring. OPS-FUT-04 should be scoped as its own milestone, not a perpetual carry-forward.
- **A test that mocks the path it's meant to protect is a liability.** Prefer real-DB / real-path regression tests for isolation invariants.

### Cost Observations

- **Sessions:** multi-session across Phases 10-11 + a multi-day live UAT (2026-06-13 → 2026-06-15); model mix not instrumented.
- **Notable:** the highest-value verification cost was manual live UAT with two real Gmail grants, which caught the projection leak and duplicate-add 500 that automated suites missed.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | ~30+ | 17 | Established Wave 0 RED test scaffolding, Spring Modulith JDBC event spine, ArchUnit safety floor, single-VPS deployment baseline, Vietnamese-default i18n. |
| v1.1 | ~5 | 1 (Phase 7 of 2) | Single-phase end-to-end coherent capability (backend + frontend + executor + arch + UI in one phase). Discovered spec-phase as proper scope-defer gate. Brand palette shift PR #40 surfaced as cross-cutting impact mid-milestone. |
| v1.2 | multi | 4 (8, 08.1, 9, +bonus) | Admin console on a second SecurityFilterChain + separate `apps/admin` SPA; privacy-by-ArchUnit extended to admin surface. Severe planning drift (388-commit / 2-minor tag inflation) made close an archaeology exercise. |
| v1.3 | multi | 2 (10, 11) | Second request-scoped isolation axis (`MailboxContext`) mirroring `TenantContext`; multi-Gmail workspace-shared / mailbox-isolated boundary. Live two-mailbox UAT caught a projection-read leak CI missed. Tracking-doc drift recurred. |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|--------------------|
| v1.0 | Backend `clean check` 4m15s; Frontend Vitest 39 files / 236 tests; Playwright 67 passed | Not measured (no JaCoCo gate) | jtokkit, jakarta.mail, ShedLock, next-mdx-remote, gray-matter, Resend SDK, Thymeleaf — all justified single-purpose adds. |

### Top Lessons (Verified Across Milestones)

1. **Wave 0 RED scaffolding catches contract drift early.** (v1.0, v1.3 — confirmed across phases.)
2. **Trust story must be CI-gated, not document-only** — ArchUnit + grep + privacy sweeps. (v1.0–v1.3, extended to admin and mailbox isolation axes.)
3. **Phase verifier + REQUIREMENTS.md flip should be coupled to avoid orphan requirements.** Still uncoupled — drift caused a reconciliation close in **both v1.2 and v1.3** (verified recurring failure mode, now top remediation candidate).
4. **Mirror the proven request-scoped isolation pattern (`TenantContext`) for new isolation axes** rather than inventing one. (v1.3 `MailboxContext`.)
5. **Isolation invariants need real-DB / real-path regression tests; mocking the protected path hides leaks.** (v1.3 projection-read leak.)
6. **GA tag discipline keeps slipping (v1.1 → v1.2 → v1.3).** Scope OPS-FUT-04 (eval + Grafana + CASA + LAUNCH-GO-NOGO + GA tag) as its own milestone, not a perpetual carry-forward.
