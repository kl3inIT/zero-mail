# Phase 7: Chat Email Assistant — Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 7 ships the entire chat stack as one coherent capability: new `core.chat` Modulith module (6 Liquibase changelogs 041–046), single carved-out Gmail send call site (`AssistantSendExecutor`), ArchUnit count flip 0→1, full 20-tool catalog (7 read + 8 write-reversible + 3 confirm-required + 3 confirmed-send), confirmation state machine (Redis lease + CAS + same-tx audit + reconciliation cron), 3-layer `chat_message.parts` body ban, tenant isolation across long-lived SSE, hardened system prompt with XML-fenced personalization slot, and frontend `/chat` route with `@ai-sdk/react@3` + AI Elements primitives + Vietnamese-default chrome + chat history sidebar (list + open + soft-delete only).

Settings UI, hostile-corpus eval suite, Grafana dashboards, and v1.1 GA tag are **out of scope** (Phase 8).

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**17 requirements are locked.** See `07-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `07-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md Boundaries):**
- New Modulith module `core.chat` với sub-packages `domain/usecases/projection/persistence/exception/confirm/sanitize/llm`
- 6 Liquibase YAML changelogs 041–046 (`chat`, `chat_message` + body-ban trigger, `assistant_pending_action`, `assistant_send_audit`, `assistant_settings`, `assistant_memory`+`assistant_knowledge_snippet`)
- 20 tools wired (catalog locked)
- SSE bridge: `VercelProtocolEmitter` + heartbeat 15s + lifecycle hooks
- `ChatLlmGateway.streamChat(...)` (see D-02) + `SpringAiLlmModelClient` per-request `internalToolExecutionEnabled(false)`
- `ToolOutputSanitizer` + `ChatPersistenceContentBanTest` (ArchUnit) + `chat_message_body_ban` Postgres trigger — 3 layers ARCH-02
- `TenantAwareReactorScheduler` + ArchUnit Scheduler ban inside `..chat..`
- `ChatToolCallRegistry` + `ZeroMailChatMemory` (Spring AI M6 #3366/#5167 workaround)
- `AssistantSendExecutor` (single carved-out send call site, `@AllowedSendCallSite`)
- Confirmation state machine: Redis 5-min lease + optimistic concurrency + same-tx audit + reconciliation cron
- ArchUnit 0→1 flip: negative + positive paired tests + CI grep gate (count == 1)
- System prompt: XML-fenced personalization slot (empty at GA), sentinel stripping, length cap 2000
- Frontend `/chat` route + `features/chat/` + `@ai-sdk/react@3` + AI Elements primitives + `streamdown@2`
- Recipient-prominent preview cards + VIP banner (SET-SAFE-05) + "Added by AI" badge (req #17)
- Send button disabled until `chat_message.parts` persists tool-call message
- Replay-mode rendering cho confirmed cards
- Vietnamese-default chrome + Vietnamese-default AI output
- Chat history sidebar — list + open + soft-delete only

**Out of scope (from SPEC.md Boundaries):**
- Settings page UI, BYOK provider/model picker, personalization editing form, behavior toggles, safety-net management → **Phase 8**
- Hostile-corpus `aiEval` suite, Grafana dashboards, CASA evidence refresh, v1.1 GA tag → **Phase 8**
- Conversation rename + search → **v1.2**
- Image attachments → **v1.2**
- First-contact-domain friction → deferred (replaced by "outside source thread" rule req #17)
- `reconnectToStream` (vercel/ai#14027 crash); WebSockets/STOMP; auto-send rule-triggered; webhook rule actions; long-term raw email body persistence; hidden links in AI drafts; "auto-send if confidence ≥ X"; per-call provider via chat slash commands; local LLM; Vercel AI SDK `ai` package on Java; frontend AI SDK provider adapters — **out forever / v2+**

</spec_lock>

<decisions>
## Implementation Decisions

### Modulith Boundaries

- **D-01:** `core.chat` declares `@ApplicationModule(allowedDependencies = {"llm", "rules", "gmail", "triage", "tenant", "shared.persistence", "shared.lang", "shared.privacy"})`. Direct service calls to existing `usecases/` packages from each dependency — no new carved gateway interfaces beyond `ChatLlmGateway` (see D-02). Mirrors the `core.triage` precedent (broad direct deps); ArchUnit boundary tests verify only declared deps are imported. `billing` is **not** a declared dep — chat goes through `LlmGateway`/`ChatLlmGateway` which already wrap credit reservation.

### Streaming Orchestrator — Signature & Lifecycle

- **D-02:** **New `ChatLlmGateway` interface in `core.chat.usecases`** owns the streaming + caller-supplied-tool path. v1.0 `core.llm.usecases.LlmGateway` is **unchanged** — it stays synchronous, owns the email-content tool allow-list (`{label, archive, save_draft}`), and serves rules + triage + draft paths. Spring AI adapter for chat lives in `core.chat.llm.springai.*` (mirrors v1.0 `core.llm.gateway.springai` boundary; ALL `org.springframework.ai.*` imports confined there).
- **D-03:** SSE controller `POST /api/chat` lives in `backend/api/controllers/chat/ChatController.java` (CONVENTIONS #2: thin controller, service-owned `@Transactional`, controllers grouped under `controllers/<domain>/`). Returns `SseEmitter` (imperative); response DTOs own `from(...)` mapping. `SseEmitter.onCompletion/onTimeout/onError` → upstream Reactor `Disposable.dispose()` mandatory.
- **D-04:** Heartbeat `: keepalive\n\n` every 15s scheduled via Spring `TaskScheduler` bean (`@EnableScheduling`) + per-emitter `ScheduledFuture`. Cancel `ScheduledFuture` inside `onCompletion`/`onTimeout`/`onError`. No singleton iterating a shared emitter registry (race + ConcurrentModificationException risk).

### Confirmation State Machine

- **D-05:** Reconciliation cron (`@Scheduled(fixedRate=300000)`) lives in `backend/api` — chat is request-scoped, `backend/worker` is not touched in v1.1 (per research locked-in #6). Single-instance VPS; no `ShedLock` for v1.1. If/when scale to multi-instance → add `ShedLock` + Postgres advisory lock in a follow-up phase.
- **D-06:** Optimistic concurrency via `chat_message.parts.updated_at` compare-and-swap as locked in SPEC ARCH-03; `UNIQUE (chat_id, tool_call_id)` on `assistant_send_audit` enforces idempotent retry. Redis 5-min lease via Spring Data Redis `ValueOperations` (Lettuce under the hood). Lease commit BEFORE Gmail send call.

### Persistence Layer

- **D-07:** Mixed JPA + JDBC per CLAUDE.md "JPA for aggregates, JDBC for read-side and hot paths":
  - **JDBC** for `chat_message` (high write rate during streaming — one row per tool call/turn — plus history-sidebar read queries; JSONB `parts` envelope mapped via Spring Data JDBC custom converter)
  - **JPA** for `chat`, `assistant_pending_action`, `assistant_send_audit`, `assistant_settings`, `assistant_memory`, `assistant_knowledge_snippet` (state-machine + aggregate-shape benefits from Hibernate dirty-checking and `@Transactional` boundaries)
- **D-08:** `chat_message.parts` JSONB carries `schemaVersion: 1` on every envelope (SPEC constraint). Schema-version-aware deserialization in the converter from day one — even though only one version exists at GA, the seam prevents a costly v2 retrofit.

### Frontend Layout

- **D-09:** AI Elements primitives vendored at `apps/web/components/ai/*` (mirror shadcn pattern at `components/ui/*`). Add `components/ai/**` to ESLint + Prettier ignore globs alongside `components/ui/**` — treat both as copied primitive source. Path alias `@/components/ai/conversation`, `@/components/ai/message`, etc.
- **D-10:** Feature folder `apps/web/features/chat/` follows CONVENTIONS #8: `api/chat-api.ts`, `query-keys.ts` (only if cached data exists; `useChat` itself doesn't need TanStack Query), `hooks/use-*.ts` one-file-per-use-case, `components/*`, `messages.ts` (co-located i18n per `feedback_flat_folder_structure`), Playwright specs at `apps/web/e2e/chat/**`.
- **D-11:** `useChat({experimental_throttle: 100})` wired in `features/chat/hooks/use-chat.ts`. Vietnamese-default chrome via `next-intl` keys (vi + en bundles) in `features/chat/messages.ts`.

### Plan 0 Prototype

- **D-12:** **No de-risking prototype.** Skip the research-recommended 100-LoC Spring AI M6 streaming + tool-call verify. `ChatToolCallRegistry` workaround (SPEC ARCH-07) implemented directly in production code; if Spring AI bugs `#3366`/`#5167` cause the workaround to fail at runtime, fix in place when discovered. Risk accepted: workaround failure means executor design rework after partial implementation. Rationale: user prefers fast iteration over upfront verification (memory: `feedback_skip_derisking_spikes`).

### Claude's Discretion (deferred to researcher / planner)

- **D-13:** **Preview card composition shape** (1 generic `<PreviewCard>` + per-tool body slots vs. 6 standalone components). `gsd-phase-researcher` will consult Inbox Zero's `apps/web/utils/ai/assistant/*` preview card pattern and AI Elements `confirmation` primitive docs, then propose in `PLAN.md`. Constraint: must DRY the state-machine wiring (lease handling, persisted-message gating, replay-mode rendering, VIP banner, "Added by AI" badge) — drift across 6 cards is the failure mode to avoid.
- **D-14:** Internal sub-package layout under `core.chat` is mostly locked by SPEC ("`domain/usecases/projection/persistence/exception/confirm/sanitize/llm`"); planner has discretion on file-level grouping inside each sub-package.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Specs (locked)
- `.planning/phases/07-chat-email-assistant-backend-frontend-send-executor-archunit/07-SPEC.md` — Locked requirements (17), boundaries, constraints, acceptance criteria. **MUST read before planning.**
- `.planning/ROADMAP.md` §Phase 7 — Goal + success criteria + dependencies.

### Research Outputs
- `.planning/research/SUMMARY.md` — Executive summary, phase-split recommendation, top-7 pitfalls, open questions (resolved in SPEC.md).
- `.planning/research/STACK.md` — Backend zero new deps; frontend deps `ai@^6.0.184`, `@ai-sdk/react@^3.0.186`, `streamdown@^2.5.0`; AI Elements CLI install command.
- `.planning/research/ARCHITECTURE.md` — Modulith module shape, SSE bridge ordering, ArchUnit 3-layer carve-out, settings tabs layout, event policy.
- `.planning/research/PITFALLS.md` — Spring AI M6 streaming bugs `#3366`/`#5167`, race conditions, prompt injection, privacy regression, tenant leak.
- `.planning/research/FEATURES.md` — Table-stakes vs differentiators per surface; 20-tool catalog rationale.

### Project Conventions & Constraints
- `CLAUDE.md` — Backend code style (no abbreviations), tech stack (Java 25 / Spring Boot 4.0.6 / Spring AI 2.0.0-M6 / Spring MVC + virtual threads), hard "do not use" list, conventions index.
- `CONVENTIONS.md` §1 (thin controllers + service-owned `@Transactional`), §2 (backend domain package layout), §3 (records for DTOs, classes for entities, no Lombok), §5 (privacy logging format), §6 (direct calls vs Modulith events), §7 (UI primitive selection), §8 (frontend feature API/hooks/query keys), §9 (subproject-owned config).
- `TESTING.md` — Slice ladder, three-layer Spring AI testing, `@Tag("llm-eval")` discipline.

### Reference Implementation
- `../inbox-zero/` — Local clone of Inbox Zero source (TypeScript/Node — **product/UX reference only, do NOT port architecture**). Specifically inspect `apps/web/utils/ai/assistant/*` for confirmation state machine + preview card patterns; `apps/web/app/(app)/[emailAccountId]/chat/*` for `/chat` route shape.

### v1.0 Module Reference (callers / boundaries)
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java` — v1.0 LlmGateway interface (unchanged by Phase 7).
- `backend/core/src/main/java/com/zeromail/core/llm/package-info.java` — Modulith `@ApplicationModule` declaration to mirror for `core.chat`.
- `backend/core/src/main/java/com/zeromail/core/triage/package-info.java` — Broad `allowedDependencies` precedent for D-01.
- `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` — ArchUnit rule that flips at Phase 7 (must update to exclude `@AllowedSendCallSite`, paired with new `OnlyOneGmailSendCallSiteTest`).

### Liquibase Baseline
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — Master changelog (current head: `040-triage-audit-message-ref.yaml`). Phase 7 ships `041`–`046` and updates master atomically per changelog.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **v1.0 `LlmGateway` (synchronous path):** Drives rules + triage + draft. Phase 7 leaves it untouched; `ChatLlmGateway` is its streaming sibling, not a replacement.
- **`core.llm.gateway.springai.*` adapter pattern:** Existing `SpringAiLlmModelClient`, BYOK adapters, `SpringAiByokChatSupport` show the "Spring AI imports confined to adapter sub-package" boundary that `core.chat.llm.springai.*` must mirror.
- **AES-GCM token crypto (`gmail.persistence.crypto`):** Reused via `core.llm`; no Phase 7 change needed unless future BYOK chat-specific key is introduced (not in scope).
- **Spring Session Redis:** Provides Redis connection + Lettuce factory — reuse for confirmation lease keys; no new Redis client setup.
- **`TenantContext` ScopedValue (FND-01):** Wrapped by new `TenantAwareReactorScheduler` (SPEC ARCH-05) for long-lived SSE + tool fan-out paths.
- **`sender_safety_entry` table (TRG-07/08):** Read-only consumed by chat send preview for VIP banner (SET-SAFE-05). No schema change needed.
- **Modulith `package-info.java` precedent:** Every module declares `@ApplicationModule(displayName, allowedDependencies)` — pattern to repeat for `core.chat`.

### Established Patterns
- **Controllers under `backend/api/controllers/<domain>/`** (CONVENTIONS #2) — `controllers/chat/` is the home for `ChatController` + future chat-related endpoints.
- **DTOs under `backend/api/dto/<domain>/`** — `dto/chat/*` for SSE payloads + history responses.
- **Records for DTOs, classes for entities, no Lombok** (CONVENTIONS #3) — applies everywhere in `core.chat` + `api.dto.chat`.
- **JPA via Hibernate 7 with `@JdbcTypeCode(SqlTypes.JSON)`** for JSONB fields (v1.0 precedent in rules matcher persistence) — pattern Spring Data JDBC will mirror for `chat_message.parts`.
- **Liquibase YAML changelogs in `backend/core/src/main/resources/db/changelog/changes/`** + atomic include in `db.changelog-master.yaml` — current head 040; Phase 7 adds 041–046 sequentially.
- **Privacy logging: `event=<name> tenantId={}` + structured fields** — every log line in `core.chat` follows; never log message body / tool args containing bodies / LLM prompts/completions.

### Integration Points
- `core.chat` imports `core.llm.usecases.*` for non-streaming auxiliary paths (e.g., token-budget check reuse), and **does NOT** import `core.llm.gateway.springai.*` (Spring AI vendor SDK confinement).
- `AssistantSendExecutor` calls `gmail.users().messages().send(...)` via Google API client used elsewhere in `core.gmail.gateway.*`; flips ArchUnit `NoGmailSendAllowedTest` from `allowEmptyShould(true)` to "exclude `@AllowedSendCallSite`", paired with new `OnlyOneGmailSendCallSiteTest` asserting count == 1.
- `AssistantSendCompleted` Modulith event published `@TransactionalEventListener(AFTER_COMMIT)` after `assistant_send_audit` insert; analytics module subscribes (no event per SSE turn — per research locked-in #7).
- Frontend `apps/web/app/(protected)/(app)/chat/page.tsx` is the new route; mounts inside the same protected shell as `/dashboard`, `/rules`, `/needs-reply`.

</code_context>

<specifics>
## Specific Ideas

- **Inbox Zero parity for preview card UX** — recipient-prominent, Edit + Send + Cancel, VIP banner, "Added by AI" badge (req #17). Researcher should mine `inbox-zero/apps/web/utils/ai/assistant/*` for exact UX semantics before planner picks composition (D-13).
- **Vietnamese-default chrome** matches v1.0 i18n direction (`features/auth`, `features/onboarding` precedents). System prompt directive: "Reply in Vietnamese unless user writes English." `assistant_settings.ai_output_language` NULL → fallback Vietnamese.
- **Chat history sidebar:** list + open + soft-delete ONLY at GA. No rename, no search input present in DOM (round-1 decision locked). Retention forever for non-deleted conversations.
- **Personalization slot empty at Phase 7 GA:** `assistant_settings.personal_instructions` columns ship NULL by default in changelog 045; system prompt renders empty `<user_personalization></user_personalization>`. Phase 8 ships UI to populate.

</specifics>

<deferred>
## Deferred Ideas

- **Plan 0 prototype** (Spring AI M6 streaming + tool-call workaround verify) — declined by user; risk accepted (D-12). Re-evaluate if executor implementation hits Spring AI bug at scale.
- **`ShedLock` + Postgres advisory lock for reconciliation cron** — defer until multi-instance scale (D-05).
- **Preview card 1-generic vs 6-standalone composition** — deferred to researcher + planner with explicit constraint to DRY state-machine wiring (D-13).
- **Carved gateway interfaces** (`RulesAdminGateway`, `InboxQueryGateway`, `SafetyNetQuery`, `GmailSendPort`) — rejected for v1.1 in favor of direct service calls (D-01); revisit if Modulith boundary tests show coupling pain.
- **Conversation rename + search** → v1.2 (locked by SPEC + round-1).
- **Image attachments in chat** → v1.2.
- **First-contact-domain friction** → deferred indefinitely; replaced by simpler "outside source thread" badge (req #17).
- **`reconnectToStream`** → permanent non-feature (`vercel/ai#14027` crash); "Retry" button only.

</deferred>

---

*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Context gathered: 2026-05-17*
