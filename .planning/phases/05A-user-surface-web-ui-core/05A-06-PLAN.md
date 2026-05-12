---
phase: 05A-user-surface-web-ui-core
plan: 06
type: execute
wave: 4
depends_on: [01, 02, 03, 04, 05]
files_modified:
  - .planning/REQUIREMENTS.md
  - .planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
  - .planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md
  - apps/web/scripts/check-i18n.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
autonomous: true
requirements: [WEB-01, WEB-02, WEB-03, WEB-04]
user_setup: []

must_haves:
  truths:
    - "The full apps/web suite is green: pnpm --filter web typecheck && lint && test && i18n:check && test:e2e all pass"
    - "The canonical i18n/messages/{vi,en}.json bundles are regenerated from every feature messages.ts (pnpm i18n:build) and committed in this closure plan"
    - "A frontend-design visual-review note exists for every authenticated screen (shell, /triage all tabs, /billing, /billing top-up all states, /settings/privacy, converged rules/onboarding x3/settings)"
    - "WEB-01, WEB-02 (5A portion), WEB-03, WEB-04 are flipped to done in REQUIREMENTS.md"
    - "05A-VALIDATION.md is signed off (nyquist_compliant: true, all sign-off boxes checked, backend-surface gaps resolved/flagged)"
    - "The three backend-surface gaps (triage-audit list, billing ledger-history, top-up intent-status/intentId) are recorded in a 05A-GAPS.md register with their documented degradation paths and no backend endpoint was added"
  artifacts:
    - path: ".planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md"
      provides: "Backend-surface gap register for the three confirmed-absent endpoints + their 5A degradation paths"
      contains: "triage-audit"
    - path: ".planning/REQUIREMENTS.md"
      provides: "WEB-01..WEB-04 flipped to done (5A portion of WEB-02)"
      contains: "WEB-01"
  key_links:
    - from: ".planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md"
      to: "the full apps/web suite"
      via: "Validation Sign-Off checklist"
      pattern: "nyquist_compliant"
---

<objective>
Phase-closure plan: run the full `apps/web` suite green, roll up the per-screen `frontend-design` visual-review notes, record the three backend-surface gaps (triage-audit list, billing ledger-history, top-up intent-status/`intentId`) in a `05A-GAPS.md` register with their documented degradation paths, sign off `05A-VALIDATION.md`, and flip WEB-01 / WEB-02 (5A portion) / WEB-03 / WEB-04 to done in `REQUIREMENTS.md`. No production code changes except a final `EN_SCAN_FILES` reconciliation if anything was missed.

Purpose: prove Phase 5A is complete to its acceptance criteria and the three success criteria, and leave a clean trail for the downstream verifier and for Phases 5B/5C.
Output: green full suite, `05A-GAPS.md`, signed `05A-VALIDATION.md`, updated `REQUIREMENTS.md`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/05A-user-surface-web-ui-core/05A-SPEC.md
@.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
@.planning/phases/05A-user-surface-web-ui-core/05A-RESEARCH.md
@.planning/phases/05A-user-surface-web-ui-core/05A-01-SUMMARY.md
@.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md
@.planning/phases/05A-user-surface-web-ui-core/05A-03-SUMMARY.md
@.planning/phases/05A-user-surface-web-ui-core/05A-04-SUMMARY.md
@.planning/phases/05A-user-surface-web-ui-core/05A-05-SUMMARY.md
@CLAUDE.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Run the full apps/web suite green + roll up visual-review notes + write the backend-surface gap register</name>
  <read_first>
    - 05A-VALIDATION.md (the "Full suite command", the "Per-Task Verification Map" status column, the "Validation Sign-Off" checklist, the "Backend-Surface Gaps" section)
    - 05A-01-SUMMARY.md / 05A-02-SUMMARY.md / 05A-03-SUMMARY.md / 05A-04-SUMMARY.md / 05A-05-SUMMARY.md (the `frontend-design` visual-review notes recorded per plan; the documented gap degradation paths; any `EN_SCAN_FILES` notes; any QR-dependency note; the resolved Open Questions)
    - 05A-RESEARCH.md sections "Open Questions" (1–5) and "Environment Availability" (the three confirmed-absent endpoints) and "Assumptions Log" A4/A6
    - 05A-SPEC.md section "Acceptance Criteria" (the 12 checks) and the ROADMAP §"Phase 5A" three Success Criteria
    - apps/web/scripts/check-i18n.ts (final reconciliation if any new component path was missed)
  </read_first>
  <action>
    First run `cd apps/web && pnpm i18n:build` to regenerate the canonical `i18n/messages/{vi,en}.json` bundles from every feature `messages.ts` touched across Phase 5A (the per-feature `messages.ts` files are the source of truth; Plan 06 owns the regenerated bundles to avoid wave-3 plans fighting over them). Then run `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e`. If anything is red, fix the minimal issue (e.g. a missed `EN_SCAN_FILES` path, a flaky e2e selector, a stale snapshot) and re-run until all five gates are green. If a real production bug surfaces that is bigger than a trivial fix, STOP and flag it for a follow-up plan rather than ballooning this closure plan — record it in the SUMMARY. (No `frontend-design` invocation needed — this plan writes no UI.)
    Create `.planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md` — a register of the three confirmed backend-surface gaps, each with: the missing endpoint/field, where it was confirmed absent (`TriageAuditController` / `BillingController` / `TopupIntentResponse` in `lib/api/schema.d.ts`), the requirement it partially serves (WEB-02), the degradation path actually shipped in 5A (audit screen: undo flow + empty/error + "audit history not yet available" state, list response mocked in e2e; billing: balance + top-up + ledger empty/"coming soon", ledger mocked in e2e; top-up: `?code=` + sessionStorage rehydration, balance-rise as the credit signal), and the explicit note that 5A added no backend endpoint and did not regenerate `schema.d.ts`. Reference 05A-RESEARCH.md A4/A6 and the SPEC out-of-scope rule. Also record (from the plan SUMMARYs) the resolved values of RESEARCH Open Questions 1–5 and whether a QR dependency was added.
    Compile, in this plan's SUMMARY, a single rolled-up list of every `frontend-design` visual-review note from the plan SUMMARYs, mapped to its screen: shell + chrome, `/triage` (audit table / audit cards / shadow-mode card / sender list), `/billing` (balance + ledger), `/billing/top-up` (amount / instructions / success / expired), `/settings/privacy`, converged rules workspace, the three onboarding routes, settings page — desktop + 320px, light + dark. If any screen is missing a note, STOP and route back to the owning plan rather than fabricating one.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e</automated>
  </verify>
  <acceptance_criteria>
    - `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
    - `.planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md` exists and records the three backend-surface gaps with their confirm-location, the requirement served, the shipped degradation path, and the no-new-endpoint / no-schema-regeneration note; it references 05A-RESEARCH.md A4/A6.
    - This plan's SUMMARY contains a rolled-up `frontend-design` visual-review note list covering every authenticated screen (shell, `/triage` tabs, `/billing`, `/billing/top-up` states, `/settings/privacy`, converged rules/onboarding×3/settings), each noting desktop + 320px and light + dark; no screen is missing a note.
    - `apps/web/lib/api/schema.d.ts` is unchanged; no new backend endpoint was added in any 5A plan.
  </acceptance_criteria>
  <done>Full suite green; gap register written; visual-review notes rolled up.</done>
</task>

<task type="auto">
  <name>Task 2: Sign off 05A-VALIDATION.md + flip WEB-01..WEB-04 in REQUIREMENTS.md</name>
  <read_first>
    - 05A-VALIDATION.md (frontmatter `nyquist_compliant`/`wave_0_complete`; the "Per-Task Verification Map" status column; the "Validation Sign-Off" checklist; the "Backend-Surface Gaps" + "Approval" lines)
    - .planning/REQUIREMENTS.md (the WEB-01..WEB-04 checkboxes around line 98–101 and the status table around line 204–207)
    - 05A-GAPS.md (Task 1 — referenced from the validation sign-off)
    - 05A-SPEC.md section "Acceptance Criteria" + the ROADMAP §"Phase 5A" Success Criteria (the bar being signed off against)
    - the five plan SUMMARYs (to confirm each requirement's tasks landed)
  </read_first>
  <action>
    Update `.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md`: set `nyquist_compliant: true` and `wave_0_complete: true` in the frontmatter; mark the "Per-Task Verification Map" status column ✅ for every row (or document any ⚠️/❌ with a follow-up reference if Task 1 surfaced one — do NOT mark green what isn't); check every box in the "Validation Sign-Off" checklist; in the "Backend-Surface Gaps" section note that gaps (1)–(3) are resolved-as-flagged and point to `05A-GAPS.md`; set "Approval: signed off (gsd-plan-execution, <date>)" — only do this if Task 1's suite is genuinely green.
    Update `.planning/REQUIREMENTS.md`: flip `- [ ] **WEB-01**` → `- [x] **WEB-01**` (Next 16 frontend in `apps/web` consuming the typed client — done); flip `- [ ] **WEB-02**` → `- [x] **WEB-02**` BUT keep its scope note accurate — the 5A portion (onboarding, rule CRUD + live preview, triage audit log + undo, billing) is done; the draft-review screen (5B) and analytics screen (5C) remain; update the status-table row for WEB-02 to reflect "5A portion done; draft-review → 5B, analytics → 5C" (do NOT claim the whole of WEB-02 if 5B/5C haven't shipped — if the project convention is to only check the box when fully complete, leave WEB-02 unchecked and instead annotate the status table that the 5A portion is complete; pick whichever matches the existing REQUIREMENTS.md convention and state the choice in the SUMMARY); flip `- [ ] **WEB-03**` → `- [x] **WEB-03**` (in-product privacy page — done); flip `- [ ] **WEB-04**` → `- [x] **WEB-04**` (persistent pause/balance/health chrome — done); update the "Phase 5A" status-table rows (lines ~204–207) from "Pending" to "Done" / "5A portion done" accordingly.
    Do a final `EN_SCAN_FILES` reconciliation in `apps/web/scripts/check-i18n.ts` only if Task 1 revealed a missed path (otherwise leave it).
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:check && grep -E "WEB-0[1-4]" ../../.planning/REQUIREMENTS.md</automated>
  </verify>
  <acceptance_criteria>
    - `05A-VALIDATION.md` frontmatter has `nyquist_compliant: true` and `wave_0_complete: true`; the "Validation Sign-Off" checklist is fully checked; the "Approval" line is signed off; the "Backend-Surface Gaps" section points to `05A-GAPS.md`.
    - `.planning/REQUIREMENTS.md`: WEB-01, WEB-03, WEB-04 are checked `[x]`; WEB-02 is handled per the existing convention (checked with an accurate scope note, or left unchecked with a "5A portion done" annotation) — the SUMMARY states which; the "Phase 5A" status-table rows reflect Done / 5A-portion-done.
    - `cd apps/web && pnpm i18n:check` exits 0.
    - No production code changed in this task beyond an optional `EN_SCAN_FILES` reconciliation.
  </acceptance_criteria>
  <done>Validation signed off; WEB-01..04 reflected in REQUIREMENTS.md per the existing convention.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| (none new) | This plan changes only `.planning/*` docs + an optional `check-i18n.ts` reconciliation; no runtime code, no backend access, no rendered strings. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-21 | Repudiation / accuracy | the WEB-01..04 flip + validation sign-off | mitigate | The flip is gated on a genuinely-green full suite (Task 1) and on every screen having a `frontend-design` note; the gap register documents what was deferred so the sign-off is not over-claiming; WEB-02's scope note keeps the 5B/5C deferral explicit. |

No high-severity threats — closure/documentation only; no frontend code, no backend access, no rendered backend strings, no dangerously-set-inner-HTML React prop, no searchParam handling.
</threat_model>

<verification>
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged across all of Phase 5A; `app/(public)/privacy/page.tsx` unchanged; no new backend endpoint.
- `05A-GAPS.md` exists; `05A-VALIDATION.md` is signed off; `REQUIREMENTS.md` reflects WEB-01..04.
</verification>

<success_criteria>
- The full `apps/web` suite is green; every authenticated screen has a `frontend-design` visual-review note; the three backend-surface gaps are recorded with documented degradation paths; `05A-VALIDATION.md` is signed off; WEB-01 / WEB-02 (5A portion) / WEB-03 / WEB-04 are reflected in `REQUIREMENTS.md`; no backend endpoint was added and `schema.d.ts` was never regenerated.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-06-SUMMARY.md` (record: the final suite result; the rolled-up visual-review-note list; how WEB-02 was handled in REQUIREMENTS.md; any follow-up flagged during the suite run; the resolved Open Questions 1–5).
</output>
