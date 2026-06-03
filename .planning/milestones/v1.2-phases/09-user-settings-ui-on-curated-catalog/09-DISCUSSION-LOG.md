# Phase 9: User Settings UI on Curated Catalog — Discussion Log

**Discussed:** 2026-05-26
**Mode:** advisor (USER-PROFILE.md detected) + default
**Calibration tier:** full_maturity (Vendor Philosophy = thorough-evaluator)
**Non-technical owner:** false (technical developer profile)
**Outcome:** 4 gray areas discussed + 1 unsolicited deep-pivot (Inbox Zero alignment); SPEC.md and ROADMAP.md success criteria updated mid-discussion; 1 new requirement pulled forward from `SET-VOICE-FUT-03` → `SET-VOICE-07`.

---

## Gray Areas Presented

The user selected all 4 of the presented areas:

1. REST endpoint granularity — single mega vs three feature endpoints vs PATCH per-field
2. Knowledge snippets UX — drag-drop vs createdAt order vs dialog edit vs add-only
3. Tone preset `CUSTOM` semantics — textbox vs inferred-from-writing_style vs drop CUSTOM
4. Backend architecture — Spring Modulith boundary + test-connection reuse

---

## Area-by-Area Log

### Area 1 — REST endpoint granularity

| Option | Outcome |
|---|---|
| A. 3 feature PUTs (`/voice`, `/behavior`, `/ai`) | ✓ **Locked** as D-08 |
| B. 1 mega `PUT /api/settings` | Rejected — full payload per save, harder optimistic per-section |
| C. JSON Merge Patch (RFC 7396) | Rejected — non-idiomatic for Spring + openapi-typescript |
| D. Hybrid (3 GETs, 1 unified PUT) | Not picked |

Decision: idiomatic Spring + Jakarta Bean Validation + openapi-typescript chain; lowest delta from existing project patterns (Phase 8 `MasterKeyAdminService` followed the same shape).

### Area 2 — Knowledge snippets UX

The user did not answer the first round of options A/B/C/D directly. Instead, they asked Claude to check how Inbox Zero handles AI config first ("check xem các config ai này inbox zero làm thế nào tôi định làm theo ở version này đã"). This triggered a deep scout of `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/settings/SettingsTab.tsx`, `WritingStyleSetting.tsx`, `AboutSetting.tsx`, `DraftConfidenceSetting.tsx`, `KnowledgeBase.tsx`, `KnowledgeForm.tsx`, `settings/page.tsx`, and `prisma/schema.prisma` `model Knowledge`.

The Inbox Zero scout produced a pattern table covering layout, edit pattern, knowledge UX, confidence encoding, and BYOK position. The user then asked Claude to recommend; Claude recommended "Full IZ alignment + 1 deviation (keep BYOK at /ai)" with reasoning + tradeoffs per axis. The user picked that recommendation directly.

| Decision | Resolution |
|---|---|
| Knowledge ordering | `updated_at DESC` (matches Inbox Zero's `formatDateSimple(updatedAt)` column header) |
| Knowledge edit | Table + Dialog edit (NOT inline, NOT drag-drop) — matches `KnowledgeBase.tsx` |
| Knowledge uniqueness | `UNIQUE(tenant_id, title)` — matches `@@unique([emailAccountId, title])` |
| Knowledge delete | `ConfirmDialog` — matches IZ |
| Layout | Flat `<SectionHeader>` groups, NOT shadcn `<Tabs>` — matches `SettingsTab.tsx` |
| Edit pattern | `SettingCard` + Dialog edit — matches `WritingStyleSetting.tsx` |
| `SET-BEHV-02` confidence | Enum `LOW | MEDIUM | HIGH` `<Select>`, NOT slider — matches `DraftReplyConfidence` enum |
| BYOK position | **Deviation:** stays on `/ai` (NOT moved to `/settings`). Reason: Zero Mail is single-tenant-per-user; IZ split because of multi-account-per-user. |

This forced an immediate SPEC.md + ROADMAP.md update (committed atomically as `800f2b2a`) BEFORE continuing the discuss-phase. Decisions captured as D-01..D-06, D-07 (confidence enum), and D-16 (ByokForm migration).

### Area 3 — Tone preset `CUSTOM` semantics

The user clarified out-of-band (via clarification mechanism) and chose **Option 1** with an additional scope pull-forward:

- `CUSTOM` = use the saved `writing_style` instead of a fixed tone preset. NO `custom_tone_description` column.
- ADDITIONALLY: Phase 9 should include a "Generate from recent sent emails" button inside the writing-style edit Dialog. The action analyzes recent sent emails transiently, populates `writing_style` with a concise style guide, and lets the user edit before saving. **Persist only the generated style summary, not raw email bodies or LLM prompts/completions.**

Claude verified the scope-pull-forward against `SET-VOICE-FUT-03` in REQUIREMENTS.md (deferred to v1.3) and against the project privacy constraint, then locked:

| Decision | Resolution |
|---|---|
| `tone_preset = 'CUSTOM'` | System prompt uses `writing_style` only; preset descriptor omitted (D-09) |
| New requirement | `SET-VOICE-07` pulled into Phase 9 from `SET-VOICE-FUT-03` (D-10..D-12) |
| Endpoint | `POST /api/settings/voice/generate-from-sent` returning `{ generatedStyle: string }` ≤ 500 words |
| Privacy invariant | Raw email bodies + LLM prompt + LLM completion are in-memory-only; ArchUnit/integration test asserts no leak (D-11) |
| Rate limit | 3 generations / hour / tenant (D-12) |
| UX | User reviews populated textarea, must click Save explicitly (D-12) |

`REQUIREMENTS.md` was updated to add `SET-VOICE-07` to the active list and strike through `SET-VOICE-FUT-03` in the deferred section; Phase 9 traceability bumped to 20 reqs total.

### Area 4a — Spring Modulith boundary

| Option | Outcome |
|---|---|
| A. Keep in `core.chat` (new sub-package `core.chat.settings`) | ✓ **Locked** as D-13 |
| B. New top-level `core.tenant.settings` module | Rejected — would force migrating 4 entities + updating draft/rule worker imports for zero net architectural value in v1.2 |
| C. Hybrid (split between `core.chat` and new `core.usersettings`) | Rejected — 2 places to find settings is worse than 1 |

Decision: `assistant_settings`, `assistant_knowledge_snippet`, `AssistantMemoryService` already live in `core.chat`. Modulith named-interface `core.chat::settings-api` declares the boundary for outside callers. Safety net stays in `core.triage`.

### Area 4b — Test-connection code reuse

| Option | Outcome |
|---|---|
| A. Extract `ProviderConnectionTester` + thin user-side wrapper | ✓ **Locked** as D-14 |
| B. Extract + admin/user both call directly with `kind=admin|user` discriminator | Rejected — wrapper layer is cleaner for per-tenant vs per-admin rate-limit isolation |
| C. Duplicate per side | Rejected — sentinel-leak (ARCH-11) drift risk |

Decision: shared service in `core.llm.gateway.springai`; admin MKEY endpoint refactored to delegate (Phase 8 ARCH-11 sentinel-leak test stays green via the shared scrub); user-side wrapper enforces 10/hour/tenant rate-limit before delegating.

---

## Scope Creep / Deferred Ideas

The "Generate from recent sent emails" was originally a deferred idea (`SET-VOICE-FUT-03`). The user explicitly pulled it INTO Phase 9 scope, so it became `SET-VOICE-07` ACTIVE rather than staying deferred. No new deferred items were captured this round.

`SET-SAFE-02` and `SET-SAFE-03` remain deferred to v1.3 per the earlier spec-phase round-1 scope decision.

---

## Mid-Discussion Mutations

Unusual for a discuss-phase: SPEC.md and ROADMAP.md were edited and committed in the middle of the discussion, after the Inbox Zero pattern alignment decision (D-01..D-07). This is documented as a one-way pivot — the user did not undo any IZ-alignment choice afterwards.

Commits:
- `800f2b2a` — `spec(phase-9): pivot to Inbox Zero UI pattern alignment` (SPEC.md + ROADMAP.md)
- Subsequent `09-CONTEXT.md` + `09-DISCUSSION-LOG.md` commit captures the rest of the decisions.

---

## Claude's Discretion Items (no user vote needed)

- Modulith named-interface naming: `core.chat::settings-api` (Claude picks; standard Modulith pattern).
- shadcn primitive install ordering (`Dialog`, `Table`, `Switch`, `Select`, `Form`, `Badge`, `RadioGroup`) — left to plan-phase to enumerate.
- Liquibase changelog ordering: behavior columns + knowledge changes + safety net pattern_kind + audit row in separate changelogs ordered by domain (chat → triage) — left to plan-phase.
- Feature folder split: `features/ai/components/*Section.tsx` per IZ pattern; Knowledge subfeature lives at `features/knowledge/` mirroring IZ folder structure — left to plan-phase to confirm.
- Whether to invoke `/gsd:ui-phase 9` before `/gsd:plan-phase 9` — strongly recommended given the UI surface, but final call is the user's.

---

*Discussion log: 2026-05-26*
