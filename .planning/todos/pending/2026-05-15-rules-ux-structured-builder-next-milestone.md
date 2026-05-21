---
created: 2026-05-15T14:30:00+07:00
title: Rules UX structured When/Then builder for next milestone
area: product-ui
recommended_milestone: v1.1
resolves_phase: 7
reference_repo: ../inbox-zero
prototype: .planning/prototypes/rules-when-then-prototype.html
---

## Decision

Do not insert this into completed v1.0 phases. v1.0 is marked complete in `.planning/STATE.md`, with Phase 6 closed and release gates green. Treat this as the first candidate phase of the next milestone, tentatively:

`v1.1 Phase 1: Rules UX Structured Builder`

## Product Direction

Inbox Zero's rule builder is a strong reference for clarity:

- Rules list columns: `Enabled`, `Name`, `When`, `Then`.
- Create flow starts with natural-language description, then compiles to editable structure.
- Manual editor uses the visible mental model: `When I get an email` -> match conditions -> `Then` actions -> advanced options.

For Zero Mail, natural language must not be the authoritative saved rule. Natural language is only a compiler input. The persisted source of truth is the structured rule schema, with the original text retained only as `sourceText` or audit metadata.

## Scope For The Future Phase

- Redesign the rules UI around `When/Then` structure.
- Keep a `Describe` tab for natural-language authoring, but show compiled structure before save.
- Keep a `Manual` tab as the explicit structured editor.
- Update backend/API DTOs only if the current contract cannot represent editable structured rules cleanly.
- Preserve preview-before-enable and disabled-on-edit safety behavior.
- Preserve the v1 write-action allowlist: `label`, `archive`, `save_draft`.
- Explicitly block auto-send, forward, spam, delete, and webhook actions unless a separate security/product phase approves them.

## Acceptance Notes

- A non-technical user can understand each rule by scanning `When` and `Then`.
- The UI never asks the user to trust opaque prompt text as the rule source of truth.
- The AI compile path produces deterministic structured output or asks for clarification.
- Manual edits and AI-generated rules converge to the same schema.
- The static prototype exists at `.planning/prototypes/rules-when-then-prototype.html` and should be used as the starting visual reference, not production code.
