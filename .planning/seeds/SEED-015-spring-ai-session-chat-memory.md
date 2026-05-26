---
id: SEED-015
status: dormant
planted: 2026-05-21
last_refreshed: 2026-05-26
planted_during: Phase 8 admin console research detour into Spring AI Community repos
trigger_when: "before the next iteration of chat_message / assistant_pending_action schema, or when chat memory growth/retrieval becomes a problem"
scope: small-to-medium
---

# SEED-015: Spring AI Session for Chat Memory Layer

## Why This Matters

Zero Mail's chat assistant already has bespoke memory schema: `chat_message.parts`, `assistant_pending_action`, the body carve-out for user-authored draft data (see project CLAUDE.md Privacy section). It works for v1.1, but:

- Hand-rolled persistence + retrieval pattern with no published contract.
- Memory growth strategy ad-hoc (no summarization, no eviction, no window policy declared).
- No clear story for cross-session resumption beyond loading recent messages.
- Doesn't model assistant state (tool calls, partial actions) as first-class events.

`spring-ai-community/spring-ai-session` is **structured, event-sourced conversation memory** built for Spring AI. Event-sourced fits Zero Mail's audit/compliance posture (every chat action replayable, no destructive update on history). It may give us a ready abstraction instead of growing the bespoke layer.

This is **not** an immediate rewrite — current schema works. But the next time we extend chat memory (cross-session resume, summary-based window, multi-turn tool-call replay), check this library first before adding fields.

## When to Surface

**Trigger:** before the next non-trivial change to `chat_message` / `assistant_pending_action` schema. Examples that would trigger:
- Adding summarization or window eviction
- Cross-session resume / "continue this conversation" feature
- Multi-step tool-call workflows that need replay
- Audit log of assistant decisions for admin console (overlaps with v1.2 Phase 8 admin surface — already in flight)

## Scope Estimate

**Small-to-medium**. Two paths:
- **Lightweight:** adopt patterns + naming from `spring-ai-session` in the existing schema. Doc-only change.
- **Migration:** replace bespoke layer with the library. Liquibase changelog, repository rewrite, mapping layer for body carve-out distinction. Likely a full phase.

Start with the lightweight path; only migrate if the library gives clear ROI on a planned chat extension.

## Candidate Product Shape

- Review `spring-ai-session` event model vs current `chat_message.parts` design.
- Map current entity → library entity. Identify gaps (body carve-out, tenant isolation, draft preview state).
- ADR: keep bespoke vs migrate, with concrete cost/benefit on the trigger feature.

## Safety Rules

- Body carve-out distinction (draft data ALLOWED, extracted email content BANNED) must survive any migration. Library's storage model needs source-tagging or we layer it on top.
- Tenant isolation invariants (`tenantId` everywhere) must hold.
- Event-sourcing must not turn "delete tenant" into a 30-minute replay job — need snapshot/compaction story.

## Library Stack Confirmation (2026-05-26)

Verified directly from `spring-ai-session` README:
- Current version `0.3.0-SNAPSHOT` (BOM coords `org.springaicommunity:spring-ai-session-bom`).
- Requires **Spring AI 2.0.0-M4+** (Zero Mail is at M7 — compatible).
- Requires **Spring Boot 4.0.2+** (Zero Mail is at 4.0.6 — compatible).
- 4 modules: `spring-ai-session-management` (SPI + `SessionMemoryAdvisor` + compaction), `spring-ai-session-jdbc` (PostgreSQL/MySQL/H2 repository), `spring-ai-autoconfigure-session*`, `spring-ai-starter-session-jdbc`.
- Key primitive: `SessionMemoryAdvisor` cắm thẳng vào `ChatClient.defaultAdvisors(...)`. Trigger: `TurnCountTrigger(N)`. Strategy: `SlidingWindowCompactionStrategy` (turn-boundary-safe).
- Concept: every message = `SessionEvent` with identity + timestamp + branch label; multi-agent hierarchies via branch labels.

**No migration urgency:** v1.1 Phase 7 chat shipped with bespoke `chat_message.parts` + `ZeroMailChatMemory` adapter (workaround for Spring AI #3366/#5167). Library exists as the "official" answer to those exact problems but isn't currently exhibiting pain.

## Library vs In-house (decide at trigger time)

Two paths when the trigger fires:

- **Adopt library** — replace `ZeroMailChatMemory` + bespoke schema with `spring-ai-starter-session-jdbc`. Pro: turn-boundary-safe compaction comes free, well-tested SPI. Con: schema migration on live chat history, library's `SessionEvent` payload must accommodate body carve-out distinction (open question above), and we inherit the library's churn (`0.3.0-SNAPSHOT` = pre-1.0).
- **In-house extension** — keep bespoke layer, steal the patterns: `SessionEvent` shape (identity + timestamp + branch label), `TurnCountTrigger` + `SlidingWindowCompactionStrategy` algorithm, event-sourced no-destructive-update discipline. Implementation is moderate (turn-boundary compaction is the only tricky part — ~300 LOC).

**Recommendation:** **in-house** is the better fit here. Zero Mail already has the persistence schema, the body carve-out distinction, tenant isolation, and the M6-bug workaround. The library would add value if we were greenfielding chat memory, but reading library source as a reference and porting the compaction algorithm is lower-risk than swapping the live schema.

## Open Questions

- Does `spring-ai-session` model multi-modal parts (text + tool-call + tool-result + draft-preview)? Current parts schema mixes these. **Need to read source, not just README.**
- Does `SessionEvent` payload allow source-tagging for body carve-out distinction (extracted email content BANNED vs user-authored draft data ALLOWED)? Critical for any migration.
- Does it solve the underlying Spring AI #3366/#5167 (which `ZeroMailChatMemory` was a workaround for)? If yes, dropping the workaround is a separate win.
- Migration cost on existing chat history if we ever go full migration.

## References

- `spring-ai-community/spring-ai-session` (v0.3.0-SNAPSHOT, Boot 4 ready)
- Project CLAUDE.md Privacy section (body carve-out rules)
- v1.1 Phase 7 PLAN summaries (`ZeroMailChatMemory` workaround for Spring AI #3366/#5167)
- Memory: `reference-ai-research-repos`, `feedback-draft-body-carve-out-no-defense`, `project-v12-admin-webauthn-pivot`
