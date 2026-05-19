# Phase 7: Chat Email Assistant — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `07-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 07-chat-email-assistant-backend-frontend-send-executor-archunit
**Areas discussed:** Modulith allowedDependencies, Streaming orchestrator (signature + heartbeat + state-machine + persistence), AI Elements + frontend layout, Plan 0 prototype scope

---

## Modulith allowedDependencies

| Option | Description | Selected |
|--------|-------------|----------|
| Broad direct deps | `{llm, rules, gmail, triage, tenant, shared.persistence, shared.lang, shared.privacy}`. Match triage precedent. Direct calls into existing usecases services. No new gateway carve-out beyond `LlmGateway.streamChat()`. | ✓ |
| Narrow via carved gateways | Only `{llm, tenant, shared.*}`. Carve `RulesAdminGateway`, `InboxQueryGateway`, `SafetyNetQuery`, `GmailSendPort`. Adds 4 indirection layers + test surface. | |
| Hybrid: broad reads, carved send | Broad deps for reads; AssistantSendExecutor calls a carved `GmailSendPort`. | |

**User's choice:** Broad direct deps (Recommended).
**Notes:** Matches `core.triage` precedent. ArchUnit + cross-module tests verify only declared deps imported. `billing` not declared — chat goes through `(Chat)LlmGateway` which already wraps credits.

---

## Streaming Orchestrator — Sub 2a: streamChat() signature placement

| Option | Description | Selected |
|--------|-------------|----------|
| New `ChatLlmGateway` interface in `core.chat.usecases` | Clean taxonomy: v1.0 `LlmGateway` untouched (synchronous, gateway-owned tool list). New interface owns chat path. Spring AI adapter in `core.chat.llm.springai.*`. | ✓ |
| Extend v1.0 `LlmGateway` with `streamChat()` | Saves 1 file but couples chat shape (caller-supplied tools, streaming) with email-content shape (gateway-owned `{label, archive, save_draft}`). Violates interface segregation. | |

**User's choice:** New `ChatLlmGateway` interface.
**Notes:** Mirrors v1.0 `core.llm.gateway.springai` boundary — all Spring AI imports confined to `core.chat.llm.springai.*`.

---

## Streaming Orchestrator — Sub 2b: Heartbeat scheduler mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Per-emitter `ScheduledExecutorService` | Each `SseEmitter` self-schedules; cancel in `onCompletion`/`onTimeout`/`onError`. Inbox Zero-style. No shared state. | |
| Spring `TaskScheduler` bean + per-emitter `ScheduledFuture` | Inject `TaskScheduler`, schedule per-emitter, cancel on disconnect. Spring-managed, observable via actuator. Same pattern as option 1, container-managed. | ✓ |
| Singleton `@Scheduled` iterates registry | Shared mutable `Set<SseEmitter>`. Removal-on-disconnect racy. Anti-pattern. | |

**User's choice:** Spring `TaskScheduler` bean + per-emitter `ScheduledFuture`.
**Notes:** Container-managed; cancellation tied to SSE lifecycle hooks.

---

## Streaming Orchestrator — Sub 2c: Reconciliation cron home

| Option | Description | Selected |
|--------|-------------|----------|
| `backend/api` with `@Scheduled` | Chat is request-scoped; `backend/worker` not touched in v1.1. Single-instance VPS — no multi-pod coordination needed. | ✓ |
| `backend/worker` | Open `core.chat` in worker module path. Cleaner separation but breaks research's "worker not involved in v1.1" assumption. | |
| ShedLock + Postgres advisory lock | Defensive against multi-instance scale; overkill for single-VPS now. | |

**User's choice:** `backend/api` with `@Scheduled(fixedRate=300000)`.
**Notes:** `ShedLock` deferred until multi-instance scale arrives.

---

## Streaming Orchestrator — Sub 2d: Persistence layer

| Option | Description | Selected |
|--------|-------------|----------|
| All JPA | All 6 entities via Hibernate. `chat_message.parts` JSONB via `@JdbcTypeCode(SqlTypes.JSON)`. Faster ship; state machine benefits from dirty-checking. | |
| JDBC for `chat_message`, JPA for rest | `chat_message` has high write rate during streaming + replay reads → JDBC. Other 5 entities JPA. Two patterns in one module. | ✓ |
| All JDBC | Explicit but loses entity navigation; mismatches v1.0 precedent. | |

**User's choice:** JDBC for `chat_message`, JPA for the other 5.
**Notes:** Matches CLAUDE.md "JPA for aggregates, JDBC for read-side and hot paths".

---

## AI Elements + Frontend Layout — Sub 3a: AI Elements home

| Option | Description | Selected |
|--------|-------------|----------|
| `apps/web/components/ai/*` | Mirror shadcn pattern at `components/ui/*`. Add to ESLint/Prettier excluded paths. Path alias `@/components/ai/...`. | ✓ |
| `apps/web/components/ui/*` (mix with shadcn) | Saves a folder but different upgrade lifecycle (ai-elements has its own CLI). Loses clear ownership. | |
| Co-located in `features/chat/components/` | Matches ai-elements default install. Forces relocation if Phase 8 Settings needs a primitive. | |

**User's choice:** `apps/web/components/ai/*`.
**Notes:** Vendored copy; treat like shadcn primitives — excluded from ESLint/Prettier.

---

## AI Elements + Frontend Layout — Sub 3b: Preview card composition

| Option | Description | Selected |
|--------|-------------|----------|
| 1 generic `<PreviewCard>` + per-tool body slots | Generic wrapper handles state-machine wiring, VIP banner, "Added by AI" badge. Per-tool bodies inside. DRY state-machine wiring. | |
| 6 separate components, no shared wrapper | Maximum per-tool flexibility but duplicates state machine wiring 6× → drift risk. | |
| Defer to research/planner | Skip lock now; let `gsd-planner` consult Inbox Zero pattern + AI Elements `confirmation` primitive docs and propose in `PLAN.md`. | ✓ |

**User's choice:** Defer to research/planner.
**Notes:** Hard constraint passed to planner: must DRY the state-machine wiring across cards.

---

## Plan 0 Prototype Scope

User declined the prototype path before the location/provider/exit-gate sub-questions were answered.

**User's choice:** Skip Plan 0 entirely. "thôi k cần verify đâu cứ code thật đi lỗi thì tính sau."
**Notes:** Risk accepted: if Spring AI bugs `#3366`/`#5167` reproduce in production, executor design will be reworked after partial implementation. Saved to memory as `feedback_skip_derisking_spikes` to inform future GSD recommendations.

---

## Claude's Discretion

- Preview card composition shape — deferred to `gsd-phase-researcher` + `gsd-planner` with explicit DRY-state-machine constraint (D-13).
- File-level grouping inside `core.chat` sub-packages — SPEC locks the sub-package list (`domain/usecases/projection/persistence/exception/confirm/sanitize/llm`); planner picks file decomposition (D-14).

## Deferred Ideas

- Plan 0 prototype — declined; risk accepted (D-12).
- `ShedLock` + Postgres advisory lock — until multi-instance scale (D-05).
- Carved gateway interfaces (`RulesAdminGateway`, `InboxQueryGateway`, `SafetyNetQuery`, `GmailSendPort`) — rejected for v1.1; revisit if Modulith boundary tests show coupling pain (D-01).
- Conversation rename + search → v1.2 (SPEC + round-1 locked).
- Image attachments in chat → v1.2.
- First-contact-domain friction → replaced by "outside source thread" badge (req #17).
- `reconnectToStream` → permanent non-feature (vercel/ai#14027 crash).
