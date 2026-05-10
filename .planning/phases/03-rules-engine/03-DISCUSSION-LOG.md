# Phase 03: rules-engine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log preserves the alternatives considered.

**Date:** 2026-05-10
**Phase:** 03-rules-engine
**Areas discussed:** Rule authoring and edit loop, Preview evidence and privacy boundary, Template materialization behavior, Rule ordering and match semantics

---

## Rule Authoring and Edit Loop

### Primary Authoring Surface

| Option | Description | Selected |
|--------|-------------|----------|
| NL first | Users write plain English; compiled matcher/action details are shown for review. | yes |
| Split editor | Plain English and editable structured conditions/actions sit side by side. | |
| Builder first | Structured rule builder is primary and AI only helps generate it. | |

**User's choice:** NL first.
**Notes:** The authoring input accepts English and Vietnamese and detects the user's language from the rule text. Compiled matcher/action details should be shown for review in the same language where possible. Ambiguous rules ask a clarification question instead of guessing. Phase 3 still compiles only to the locked deterministic matcher/action vocabulary, with `SEMANTIC_INTENT` stored but not evaluated at runtime.

### Ambiguity Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Inline clarification before save | Show one focused question in the editor, let the user answer, then recompile. | yes |
| Save as draft with warning | Persist the rule disabled and mark it needs clarification. | |
| Use best-effort compile | Compile the safest deterministic interpretation and show warnings. | |

**User's choice:** Inline clarification before save.
**Notes:** Do not save guessed rules.

### Compiled Details Editability

| Option | Description | Selected |
|--------|-------------|----------|
| Review-only details | Users can read the structured matcher/action summary, but edits happen by changing the natural-language rule and recompiling. | yes |
| Limited manual edits | Users can tweak safe fields like label name, sender domain, subject text, enabled actions, and sample size. | |
| Full structured editor | Users can directly edit matcher groups and action nodes. | |

**User's choice:** Review-only details.
**Notes:** The natural-language rule text is the editable source of truth.

### Save and Enable Path

| Option | Description | Selected |
|--------|-------------|----------|
| Save disabled, enable after preview | Save the compiled rule disabled by default; user previews, then explicitly enables it. | yes |
| Save and enable after review | User can save enabled immediately from the compile review screen, with preview optional. | |
| Separate draft state | Compiled rules stay as drafts until user chooses Save rule, then can be enabled separately. | |

**User's choice:** Save disabled, enable after preview.
**Notes:** Enabling requires an explicit preview step first.

---

## Preview Evidence and Privacy Boundary

### Preview Row Evidence

| Option | Description | Selected |
|--------|-------------|----------|
| Safe summary + evidence chips | Show sanitized sender/domain, subject excerpt, date, current labels, proposed actions, and matched matcher clauses. | yes |
| Aggregate first | Show counts and proposed actions first, with expandable safe summaries only when needed. | |
| Detailed mailbox-like rows | Show sanitized subject/snippet-like previews plus evidence. | |

**User's choice:** Safe summary plus evidence chips.
**Notes:** No full HTML rendering.

### Body and Snippet Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Header-first only | Prefer headers/metadata and subject excerpts; only fetch/sanitize/truncate body when a matcher needs it. | yes |
| Always fetch sanitized snippet | Every preview row includes a short sanitized snippet. | |
| Body evidence on demand | Show metadata rows first; user can expand a row to fetch request-scoped body evidence. | |

**User's choice:** Header-first only.
**Notes:** Body-derived evidence is fetched only when required by a matcher.

### Semantic Intent Display

| Option | Description | Selected |
|--------|-------------|----------|
| Deferred chip | Show it as a visible deferred semantic check chip with no true/false match. | yes |
| Warning section | Put all semantic parts in a separate warning panel above results. | |
| Hide from preview rows | Mention it only in compiled details. | |

**User's choice:** Deferred chip.
**Notes:** `SEMANTIC_INTENT` is not evaluated as true/false in Phase 3.

### Preview Summary

| Option | Description | Selected |
|--------|-------------|----------|
| Impact summary | Show sample size, matched count, proposed action counts, deferred count, and no Gmail changes were made. | yes |
| Message list first | Lead with individual matched/unmatched rows, with summary secondary. | |
| Risk summary | Lead with warnings only: too many matches, no matches, semantic deferrals, body-derived evidence. | |

**User's choice:** Impact summary.
**Notes:** The summary must explicitly state that no Gmail changes were made.

---

## Template Materialization Behavior

### Initial Enabled State

| Option | Description | Selected |
|--------|-------------|----------|
| Materialize disabled | Create real template-derived rules exactly once, but disabled until the user previews and enables each one. | yes |
| Materialize enabled after one global preview | Create all selected templates disabled, run one combined preview, then let the user enable all at once. | |
| Materialize enabled immediately | Existing onboarding selection means consent to enable. | |

**User's choice:** Materialize disabled.
**Notes:** Template-derived rules require preview before enablement.

### Materialization Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| Rules API read | First `GET /api/rules` materializes templates idempotently, so every consumer sees the same initialized state. | yes |
| Rules page visit only | The frontend route triggers materialization. | |
| Background job after onboarding | Worker creates rules after onboarding completes. | |

**User's choice:** Rules API read.
**Notes:** The API is the source of truth.

### Edited Template Rules

| Option | Description | Selected |
|--------|-------------|----------|
| Keep origin, mark customized | Preserve `templateOrigin`, add customized metadata, and never overwrite edited rules during future materialization. | yes |
| Drop template origin | Treat edited template rules as normal user-created rules. | |
| Fork on edit | Keep the original template disabled and create a new user-owned copy. | |

**User's choice:** Keep origin, mark customized.
**Notes:** `templateOrigin` means provenance, not a default state. Template-derived rules preserve origin/template version for attribution and future admin/template management, but edited rules are marked customized and never overwritten by future materialization.

### Template Catalog Representation

| Option | Description | Selected |
|--------|-------------|----------|
| Code-defined catalog with persisted origin/version | Keep the v1 catalog in code, and store `templateKey`, `templateVersion`, and `customized` on rules. | |
| Database template catalog | Add template catalog tables now. | yes |
| Rule rows only | Store just `templateOrigin` on created rules, with no explicit version/customized fields. | |

**User's choice:** Database template catalog.
**Notes:** Phase 3 should add catalog tables for v1 starter templates, but the admin surface for managing templates, versions, deprecations, and migrations is deferred.

---

## Rule Ordering and Match Semantics

### Hit Policy

| Option | Description | Selected |
|--------|-------------|----------|
| All matching rules, ordered actions | Evaluate rules in user order; every enabled matching rule can contribute safe actions, with preview showing overlaps and duplicate actions. | yes |
| First match wins | Evaluate in user order and stop at the first matching rule. | |
| Per-rule stop option | Default all matching rules, but allow a rule to stop later rules. | |

**User's choice:** All matching rules, ordered actions.
**Notes:** This applies to Phase 3 preview and future Phase 4 triage.

### Duplicate Actions

| Option | Description | Selected |
|--------|-------------|----------|
| Deduplicate identical actions | Merge exact duplicate safe actions, show which rules contributed them, and preserve ordered evidence. | yes |
| Show duplicates, execute once later | Preview shows every duplicate proposal, while Phase 4 execution deduplicates. | |
| Treat duplicates as conflict | Require user cleanup before enabling. | |

**User's choice:** Deduplicate identical actions.
**Notes:** Preserve contributing rule provenance.

### Conflicting Actions

| Option | Description | Selected |
|--------|-------------|----------|
| Warn, but allow enable | Show conflict/risk warnings in preview, but allow the user to enable because Phase 3 does not execute writes. | yes |
| Block enable on conflicts | Require user to edit/reorder/disable rules before enabling. | |
| No conflict concept | Since all actions are safe intents, just list the combined actions. | |

**User's choice:** Warn, but allow enable.
**Notes:** The warning is evidence for user trust; Phase 3 still performs no Gmail writes.

### Disabled Rules in Preview

| Option | Description | Selected |
|--------|-------------|----------|
| Saved-rule preview includes disabled current rule only | Previewing a disabled rule tests that rule plus other enabled rules, but disabled unrelated rules are ignored. | yes |
| Preview exactly one rule | Preview tests only the selected draft/saved rule, ignoring the rest of the rules list. | |
| Preview all saved rules regardless of enabled state | Useful for analysis, but confusing because disabled rules look active. | |

**User's choice:** Saved-rule preview includes disabled current rule only.
**Notes:** Preview includes the current disabled rule plus enabled siblings.

---

## The Agent's Discretion

- Exact AST record names, DTO names, schema field names, database constraint names, and UI layout details.

## Deferred Ideas

- Admin surface to manage the template catalog, template versions, deprecation behavior, and migration behavior.
