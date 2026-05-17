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
    - "The canonical i18n/messages/{vi,en}.json bundles are regenerated from every feature messages.ts (pnpm i18n:build) and committed in this closure plan — wave-1..3 plans did NOT commit them"
    - "A frontend-design visual-review note exists for every authenticated screen (shell, /triage all tabs, /billing, /billing top-up all states, /settings/privacy, converged rules/onboarding x3/settings)"
    - "WEB-01, WEB-03, WEB-04 are flipped to done in REQUIREMENTS.md; WEB-02 stays PARTIAL — the 5A portion (onboarding, rules+live-preview, triage audit log+undo*, billing*) is done, but the draft-review screen (5B), the analytics screen (5C), and the real audit-list & ledger-history backend endpoints all remain — so WEB-02 gets a partial annotation, NOT a full [x] flip"
    - "05A-VALIDATION.md is signed off (nyquist_compliant: true, all sign-off boxes checked, backend-surface gaps resolved-as-flagged)"
    - "The FOUR backend-surface gaps (triage-audit list, billing ledger-history, top-up intent-status/intentId, top-up bank-account fields not present in TopupIntentResponse) are recorded in a 05A-GAPS.md register with their documented degradation paths and no backend endpoint was added; schema.d.ts was never regenerated; the public privacy page and backend/ are untouched"
  artifacts:
    - path: ".planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md"
      provides: "Backend-surface gap register for the four confirmed-absent endpoints/fields + their 5A degradation paths"
      contains: "triage-audit"
    - path: ".planning/REQUIREMENTS.md"
      provides: "WEB-01/03/04 flipped to done; WEB-02 annotated partial (5A portion done; 5B/5C + backend gaps remain)"
      contains: "WEB-01"
  key_links:
    - from: ".planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md"
      to: "the full apps/web suite"
      via: "Validation Sign-Off checklist"
      pattern: "nyquist_compliant"
---

<objective>
Phase-closure plan: run the full `apps/web` suite green, roll up the per-screen `frontend-design` visual-review notes, record the four backend-surface gaps (triage-audit list, billing ledger-history, top-up intent-status/`intentId`, top-up bank-account fields absent from `TopupIntentResponse`) in a `05A-GAPS.md` register with their documented degradation paths, sign off `05A-VALIDATION.md`, flip WEB-01 / WEB-03 / WEB-04 to done in `REQUIREMENTS.md`, and annotate WEB-02 as PARTIAL (5A portion done; draft-review → 5B, analytics → 5C, real audit-list & ledger-history endpoints pending). No production code changes except a final `EN_SCAN_FILES` reconciliation if anything was missed.

Purpose: prove Phase 5A is complete to its acceptance criteria and the three success criteria, keep the requirement status honest (WEB-02 stays partial), and leave a clean trail for the downstream verifier and for Phases 5B/5C.
Output: green full suite, `05A-GAPS.md`, signed `05A-VALIDATION.md`, updated `REQUIREMENTS.md`, committed canonical i18n bundles.
</objective>

<reviewer_response>
Cross-AI review:
- #6 (Codex HIGH — WEB-02 overclaiming): WEB-02 is NOT flipped to `[x]`. It gets a partial annotation like `WEB-02 | Phase 5A / 5B / 5C | 5A portion done (onboarding, rules+live-preview, triage audit log+undo*, billing*); draft-review→5B, analytics→5C; *audit-list & ledger-history backend endpoints pending`. WEB-01/03/04 flip fully. `must_haves` reflects this honest status.
- #10 (Codex MEDIUM — Windows/PowerShell + closure hygiene): all `grep` in `<verify>`/`<automated>` is replaced with PowerShell `Select-String` (or a Node script). An explicit `git diff --exit-code -- apps/web/lib/api/schema.d.ts` (plus `app/(public)/privacy/page.tsx` and `backend/`) check is added to prove "no backend endpoint / schema changed". "fix minimal issue if suite red" is tightened: if the full suite is red, fix only a trivial, in-scope, owned issue (a missed `EN_SCAN_FILES` path, a flaky e2e selector, a stale snapshot, an obviously-broken import in a doc-only file); anything non-trivial (a real production bug, a behavior change, new scope) routes back to the owning plan or a follow-up — do not absorb new scope in closure.
- #5 (Codex HIGH — bank fields): the bank-account-fields gap is now the FOURTH entry in `05A-GAPS.md`.
- #7 (MEDIUM): this plan owns the canonical `i18n/messages/{vi,en}.json` commit; wave-1..3 plans only ran `i18n:build` locally for their own gates and did not commit the generated bundles.
</reviewer_response>

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
    - 05A-VALIDATION.md (the "Full suite command", the "Per-Task Verification Map" status column, the "Validation Sign-Off" checklist, the "Backend-Surface Gaps" section — now four gaps)
    - 05A-01..05-SUMMARY.md (the `frontend-design` visual-review notes recorded per plan; the documented gap degradation paths incl. the bank-fields gap; the route-group decision & any `EN_SCAN_FILES` reconciliation note; any QR-dependency note; the resolved Open Questions)
    - 05A-RESEARCH.md sections "Open Questions" (1–5) and "Environment Availability" (the confirmed-absent endpoints) and "Assumptions Log" A4/A6
    - 05A-SPEC.md section "Acceptance Criteria" (the checks) and the ROADMAP §"Phase 5A" three Success Criteria
    - apps/web/scripts/check-i18n.ts (final reconciliation if Plan 02's route-group fallback or a converged screen changed paths)
  </read_first>
  <action>
    First run `cd apps/web && pnpm i18n:build` to regenerate the canonical `i18n/messages/{vi,en}.json` bundles from every feature `messages.ts` touched across Phase 5A (the per-feature `messages.ts` files are the source of truth; this plan owns the regenerated bundle commit so wave-1..3 plans didn't fight over them). Then run `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e`. If the full suite is red, fix ONLY a trivial, in-scope, owned issue (a missed `EN_SCAN_FILES` path, a flaky e2e selector, a stale snapshot, an obviously-broken import in a doc-only file) and re-run until all five gates are green. If anything non-trivial surfaces — a real production bug, a behavior change, a missing component, new scope — STOP, do NOT absorb it here; record it in the SUMMARY and route it back to the owning plan or a follow-up plan. (No `frontend-design` invocation needed — this plan writes no UI.)
    Run the no-backend-change diff checks (from the repo root): `git diff --exit-code -- apps/web/lib/api/schema.d.ts`, `git diff --exit-code -- "apps/web/app/(public)/privacy/page.tsx"`, `git diff --exit-code -- backend/`. All three MUST exit 0 (no changes across all of Phase 5A). If any is non-zero, STOP and flag it — 5A is frontend-only and adds no backend endpoint.
    Create `.planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md` — a register of the FOUR confirmed backend-surface gaps, each with: the missing endpoint/field, where it was confirmed absent (`TriageAuditController` / `BillingController` / `TopupIntentResponse` in `lib/api/schema.d.ts`), the requirement it partially serves (WEB-02), the degradation path actually shipped in 5A, and the explicit note that 5A added no backend endpoint and did not regenerate `schema.d.ts`:
      1. **Triage-audit list endpoint** — absent (`TriageAuditController` only has `…/audit/{auditId}/undo`). Degradation: `getAuditLog` returns a `{unavailable:true}` sentinel; `AuditLog` renders an "audit history not yet available" panel (distinct from the empty panel); the undo flow + empty/error states still ship; the populated-rows path is covered by `AuditLog.test.tsx` with injected data; the e2e covers the production "not yet available" state.
      2. **Billing ledger / transaction-history list endpoint** — absent (`BillingController` only has `/balance` + `/topup/intent`). Degradation: `useLedgerHistory` returns the `{unavailable:true}` sentinel; `LedgerHistory` renders a "transaction history isn't available yet" panel (distinct from the empty panel); `LedgerTable` populated path covered by `LedgerTable.test.tsx` with injected data; the e2e covers the production state.
      3. **Top-up intent-status endpoint / `intentId` field** — absent. Degradation: `?intentId=` rehydration (D-15) falls back to `?code=` + `sessionStorage`; the "credited" signal is inferred from `/api/billing/balance` rising (no status poll).
      4. **Top-up bank-account fields not present in `TopupIntentResponse`** — `TopupIntentResponse` carries only `code`/`amountVnd`/`expiresAt`/`qrPayload`; there is no `accountNumber`/`accountName`/`bankName`/`transferContent` (the `accountNumber` in the schema belongs to `SepayWebhookPayload`). Degradation: the top-up instructions screen shows the VietQR `qrPayload` (QR image + copyable EMV text) + the transfer `code` + the `amountVnd` + the `expiresAt` countdown only, with "scan this QR with your banking app" guidance; showing the raw bank account/name as separate copyable fields would require a static frontend config constant/env (the SePay merchant account is fixed config) OR a backend change — both out of 5A's frontend-only scope; logged here, not added.
    Reference 05A-RESEARCH.md A4/A6 and the SPEC out-of-scope rule. Also record (from the plan SUMMARYs) the resolved values of RESEARCH Open Questions 1–5, the route-group decision (split vs. fallback), and whether a QR dependency was added (+ version).
    Compile, in this plan's SUMMARY, a single rolled-up list of every `frontend-design` visual-review note from the plan SUMMARYs, mapped to its screen: shell + chrome, `/triage` (audit table / audit cards / shadow-mode card / sender list), `/billing` (balance + ledger), `/billing/top-up` (amount / instructions / success / expired), `/settings/privacy`, converged rules workspace, the three onboarding routes, settings page — desktop + 320px, light + dark. If any screen is missing a note, STOP and route back to the owning plan rather than fabricating one.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e</automated>
  </verify>
  <acceptance_criteria>
    - `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
    - `git diff --exit-code -- apps/web/lib/api/schema.d.ts`, `git diff --exit-code -- "apps/web/app/(public)/privacy/page.tsx"`, and `git diff --exit-code -- backend/` all exit 0 (no Phase-5A changes).
    - `.planning/phases/05A-user-surface-web-ui-core/05A-GAPS.md` exists and records all FOUR backend-surface gaps with their confirm-location, the requirement served, the shipped degradation path, and the no-new-endpoint / no-schema-regeneration note; it references 05A-RESEARCH.md A4/A6.
    - This plan's SUMMARY contains a rolled-up `frontend-design` visual-review note list covering every authenticated screen (shell, `/triage` tabs, `/billing`, `/billing/top-up` states, `/settings/privacy`, converged rules/onboarding×3/settings), each noting desktop + 320px and light + dark; no screen is missing a note.
    - The canonical `apps/web/i18n/messages/{vi,en}.json` are regenerated and staged for commit in this plan.
    - Any non-trivial issue surfaced during the suite run is recorded in the SUMMARY and routed back to a plan/follow-up — not absorbed here.
  </acceptance_criteria>
  <done>Full suite green; no-backend-change diff checks pass; four-gap register written; visual-review notes rolled up; canonical i18n bundles regenerated.</done>
</task>

<task type="auto">
  <name>Task 2: Sign off 05A-VALIDATION.md + flip WEB-01/03/04 and annotate WEB-02 partial in REQUIREMENTS.md</name>
  <read_first>
    - 05A-VALIDATION.md (frontmatter `nyquist_compliant`/`wave_0_complete`; the "Per-Task Verification Map" status column; the "Validation Sign-Off" checklist; the "Backend-Surface Gaps" + "Approval" lines)
    - .planning/REQUIREMENTS.md (the WEB-01..WEB-04 checkboxes and the status table — locate the exact lines)
    - 05A-GAPS.md (Task 1 — referenced from the validation sign-off)
    - 05A-SPEC.md section "Acceptance Criteria" + the ROADMAP §"Phase 5A" Success Criteria (the bar being signed off against)
    - the five plan SUMMARYs (to confirm each requirement's tasks landed)
  </read_first>
  <action>
    Update `.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md`: set `nyquist_compliant: true` and `wave_0_complete: true` in the frontmatter; mark the "Per-Task Verification Map" status column ✅ for every row (or document any ⚠️/❌ with a follow-up reference if Task 1 surfaced one — do NOT mark green what isn't); check every box in the "Validation Sign-Off" checklist; in the "Backend-Surface Gaps" section note that gaps (1)–(4) are resolved-as-flagged and point to `05A-GAPS.md`; set "Approval: signed off (gsd-plan-execution, <date>)" — only do this if Task 1's suite is genuinely green and the diff checks passed.
    Update `.planning/REQUIREMENTS.md`:
      - flip `- [ ] **WEB-01**` → `- [x] **WEB-01**` (Next 16 frontend in `apps/web` consuming the typed client — done);
      - flip `- [ ] **WEB-03**` → `- [x] **WEB-03**` (in-product privacy page — done);
      - flip `- [ ] **WEB-04**` → `- [x] **WEB-04**` (persistent pause/balance/health chrome — done);
      - DO NOT flip WEB-02 to `[x]`. Keep its checkbox UNCHECKED and update its line + the status-table row to read: "5A portion done (onboarding, rules+live-preview, triage audit log+undo*, billing*); draft-review → 5B, analytics → 5C; *audit-list & ledger-history backend endpoints pending — see 05A-GAPS.md". (If the project convention requires a phase-tag column rather than free text, set WEB-02's phase to "5A / 5B / 5C" and put the partial note in the status cell.) State in the SUMMARY exactly how WEB-02 was annotated.
      - update the "Phase 5A" status-table rows from "Pending" to "Done" for WEB-01/03/04 and "5A portion done" for WEB-02.
    Do a final `EN_SCAN_FILES` reconciliation in `apps/web/scripts/check-i18n.ts` only if Task 1 revealed a missed path (e.g. Plan 02's route-group fallback changed a path) — otherwise leave it.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:check ; cd ../.. ; Select-String -Path .planning/REQUIREMENTS.md -Pattern "WEB-0[1-4]"</automated>
  </verify>
  <acceptance_criteria>
    - `05A-VALIDATION.md` frontmatter has `nyquist_compliant: true` and `wave_0_complete: true`; the "Validation Sign-Off" checklist is fully checked; the "Approval" line is signed off; the "Backend-Surface Gaps" section points to `05A-GAPS.md` and lists all four gaps as resolved-as-flagged.
    - `.planning/REQUIREMENTS.md`: WEB-01, WEB-03, WEB-04 are checked `[x]`; WEB-02 is NOT checked — it carries the "5A portion done; draft-review → 5B, analytics → 5C; audit-list & ledger-history backend endpoints pending" annotation; the "Phase 5A" status-table rows reflect Done for WEB-01/03/04 and "5A portion done" for WEB-02; the SUMMARY states the exact WEB-02 annotation used.
    - `cd apps/web && pnpm i18n:check` exits 0.
    - No production code changed in this task beyond an optional `EN_SCAN_FILES` reconciliation.
  </acceptance_criteria>
  <done>Validation signed off; WEB-01/03/04 flipped, WEB-02 annotated partial (not flipped); REQUIREMENTS.md reflects the honest status.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| (none new) | This plan changes only `.planning/*` docs + the canonical i18n bundles + an optional `check-i18n.ts` reconciliation; no runtime code, no backend access, no rendered strings. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-21 | Repudiation / accuracy | the WEB-01/03/04 flip + WEB-02 partial annotation + validation sign-off | mitigate | The flip is gated on a genuinely-green full suite (Task 1) + the no-backend-change diff checks + every screen having a `frontend-design` note; the four-gap register documents what was deferred so the sign-off doesn't over-claim; WEB-02 stays UNCHECKED with an explicit 5B/5C + backend-gaps deferral note — closure can't mark partial work as done. |

No high-severity threats — closure/documentation only; no frontend code, no backend access, no rendered backend strings, no dangerously-set-inner-HTML React prop, no searchParam handling.
</threat_model>

<verification>
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `git diff --exit-code -- apps/web/lib/api/schema.d.ts` / `"apps/web/app/(public)/privacy/page.tsx"` / `backend/` all exit 0 across all of Phase 5A; no new backend endpoint.
- `05A-GAPS.md` exists with all four gaps; `05A-VALIDATION.md` is signed off; `REQUIREMENTS.md` reflects WEB-01/03/04 done and WEB-02 partial-not-flipped; the canonical `apps/web/i18n/messages/{vi,en}.json` are committed by this plan.
</verification>

<success_criteria>
- The full `apps/web` suite is green; every authenticated screen has a `frontend-design` visual-review note; the four backend-surface gaps are recorded with documented degradation paths; `05A-VALIDATION.md` is signed off; WEB-01 / WEB-03 / WEB-04 are flipped to done and WEB-02 is annotated PARTIAL (not flipped) in `REQUIREMENTS.md`; no backend endpoint was added, `schema.d.ts` was never regenerated, and the public privacy page + `backend/` are untouched.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-06-SUMMARY.md` (record: the final suite result; the no-backend-change diff-check results; the rolled-up visual-review-note list; the exact WEB-02 annotation used in REQUIREMENTS.md; any follow-up flagged during the suite run; the resolved Open Questions 1–5; the route-group decision; whether a QR dep was added).
</output>
