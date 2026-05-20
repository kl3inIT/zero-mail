---
id: SEED-015
status: dormant
planted: 2026-05-21
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

## Open Questions

- Does `spring-ai-session` model multi-modal parts (text + tool-call + tool-result + draft-preview)? Current parts schema mixes these.
- License + Spring AI 2.0.0-M6 compatibility (library is young, 11 stars at time of capture).
- Migration cost on existing chat history if we ever go full migration.

## References

- `spring-ai-community/spring-ai-session`
- Project CLAUDE.md Privacy section (body carve-out rules)
- Memory: `reference-ai-research-repos`, `feedback-draft-body-carve-out-no-defense`, `project-v12-admin-webauthn-pivot`
