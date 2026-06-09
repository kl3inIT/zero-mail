---
phase: 10
reviewers: [codex]
reviewed_at: 2026-06-09T04:34:00Z
convergence_cycle: 3
plans_reviewed: [10-01-PLAN.md, 10-02-PLAN.md, 10-03-PLAN.md, 10-04-PLAN.md, 10-05-PLAN.md, 10-06-PLAN.md]
cycle_1_high_count: 9
cycle_2_high_count: 2
current_high_count: 0
---

# Cross-AI Plan Review — Phase 10 (Gmail Mailbox Foundation & Account Management) — Convergence Cycle 3 (FINAL)

> Reviewer: Codex CLI (codex-cli 0.137.0, model cx/gpt-5.5, reasoning effort xhigh). Single external reviewer this cycle (`--codex`).
> This is convergence cycle 3 (final): the 6 plans were revised to address the 2 remaining HIGH concerns from cycle 2 — HIGH-A (OAuth branch authority moved fully server-side per CONTEXT D-01) and HIGH-B (stale-intent removal at the top of the success handler). The prompt asked Codex to (a) verify each cycle-2 HIGH against the REVISED plan text and (b) surface any remaining/newly-introduced HIGHs, grounded in the actual revised plan files.
> The first codex invocation died on a transport reconnection failure (5/5 retries) before producing any review; it was re-run cleanly and the review below is the successful run (exit 0). No verdict is fabricated.

## Codex Review

### Cycle-2 HIGH Resolution Verdicts

| Cycle-2 concern | Verdict | Evidence |
|---|---|---|
| **A.** OAuth branch authority still rode tamperable URL params | **RESOLVED** | Plan 05 `must_haves`: "Intent branch AUTHORITY is derived ENTIRELY from a server-side pending-intent snapshot... It NEVER reads `request.getParameter("intent"/"targetMailboxId"/"initiatingTenantId")`." Plan 05 Task 2 repeats the implementation rule: "The resolver MUST NOT call `servletRequest.getParameter("intent")` / `"targetMailboxId"` / `"initiatingTenantId"` for any branch decision," then requires reading `ZEROMAIL_OAUTH_PENDING_INTENT` from the session, validating it against the closed enum, stamping `.attributes(...)`, and clearing the pending key one-shot. Plan 06 Task 2 closes the trigger side: each add/reconnect endpoint stores the snapshot in the Redis-backed `HttpSession` under `ZEROMAIL_OAUTH_PENDING_INTENT`, then 302-redirects carrying ONLY `?reconnect=true` (NO `intent`, NO `targetMailboxId`); the reconnect endpoint pre-resolves ownership before writing the snapshot. Matches the requested server-side-only authority flow (honors D-01). |
| **B.** Stale OAuth intent could survive a success-handler-internal early failure | **RESOLVED** | Plan 05 `must_haves`: "The success handler reads-and-removes `ZEROMAIL_OAUTH_INTENT` at the very TOP of `onAuthenticationSuccess`... BEFORE any scope-check or null-refresh-token validation, so EVERY success-handler exit path... clears the intent." Plan 05 Task 3 gives the concrete order: "as the FIRST statement after casting the token... BEFORE loading the authorized client / the scope check / the null-refresh check," read the snapshot and immediately `removeAttribute("ZEROMAIL_OAUTH_INTENT")`; allows an equivalent `try/finally`. Acceptance criteria require source order before the `gmail_scope_required` throw and before the null-refresh `sendRedirect(".../google?reconnect=true")` early return. Plan 01 Task 3 adds the Wave 0 test "stale-intent-cleared-after-success-handler-early-failure." |

### Cycle-2 MEDIUM Re-check

- **ArchUnit app-wide classpath scope: RESOLVED.** Plan 03 Task 3 explicitly scopes the rule "APP-WIDE — `classes().that().resideInAPackage("com.zeromail..")` — NOT just `..core..`," cites API-tier callers (`AssistantPendingActionReconciler`, `E2eStubGmailApiClientFactory`), and its acceptance criteria require the allow-list to match live callers across BOTH modules.
- **Disconnect no-op-vs-flip alignment: RESOLVED.** Plan 04's State Matrix says disconnect uses "raw `findByIdAndTenantId` (idempotent, NOT a resolver)" with `NOT_CONNECTED`/`PENDING` as "no-op," `CONNECTED` as "disconnects," `DISCONNECTED` as "no-op (idempotent)." Task 3 aligns the implementation (raw lookup, preserve stop → revoke → flip for a connected row, re-disconnect idempotent), and acceptance criteria require a second disconnect to be a no-op.

### Remaining or Newly-Introduced HIGH Concerns

**None.** Codex found no remaining HIGH grounded in the revised text. Both cycle-2 HIGHs are addressed in both implementation tasks and Wave 0 validation, and the prior dependency/symbol-ownership gaps around Plan 04/05/06 stay closed: Plan 04 owns `addConnection`/`reconnect`, Plan 05 only calls them, Plan 06 is wave 5 with `depends_on: [10-02, 10-03, 10-04, 10-05]`.

### Other Concerns (MEDIUM / LOW)

- **[LOW]** Plan 05 describes authenticated-without-snapshot as "stamps no intent" and the success handler treats absent intent as first-login for genuine anonymous logins. Implementation should not accidentally run first-login provisioning for an already-authenticated management request that has no snapshot. The text points in the right direction; worth watching in review.
- **[LOW]** Plan 03 Task 3 says "9 known legacy FQNs" but lists more than nine names (item 9 lists both `ToneContextBuilder` and `DraftReplySourceLoader`, plus possible API callers). The task correctly requires a live grep + minimal allow-list, so this is bookkeeping risk, not HIGH.

### Risk Assessment

**Overall: LOW-MEDIUM.** The final revisions directly address the OAuth authority and stale-intent hazards with concrete source-order requirements, session-key separation (`ZEROMAIL_OAUTH_PENDING_INTENT` pre-authorize vs `ZEROMAIL_OAUTH_INTENT` callback-survival), no URL branch authority, controller-side pending snapshots, resolver-side validation/one-shot clearing, failure-handler cleanup, and Wave 0 tests. Remaining risk is mostly execution discipline: following the exact source-order and the authenticated-no-snapshot behavior. **Current HIGH count: 0.**

---

## Consensus Summary

Only one external reviewer (Codex) ran this cycle, so "consensus" is Codex's findings filtered through a source-grounding pass against the six revised plan files. Codex verified that **both cycle-2 HIGHs are now fully RESOLVED** (A: OAuth branch authority is derived entirely from the server-side `ZEROMAIL_OAUTH_PENDING_INTENT` session snapshot, never from URL params, honoring D-01; B: the callback intent is read-and-removed at the very top of `onAuthenticationSuccess` / in a `try/finally` before any short-circuiting validation), and that **both cycle-2 MEDIUMs are RESOLVED** (app-wide ArchUnit scope; disconnect no-op-vs-flip alignment). No remaining or newly-introduced HIGH was found. Two LOWs remain as execution-discipline watch items.

### Agreed Strengths (revisions that landed correctly)
- OAuth branch authority is fully server-side: Plan 06 controllers write the snapshot and redirect with no branch-bearing URL params; Plan 05 resolver reads/validates/clears the snapshot and never reads request params (cycle-2 HIGH-A closed, D-01 honored).
- Success-handler one-shot intent removal is at the very top / in `try/finally`, before the scope-check throw and the null-refresh early `sendRedirect; return`, with a dedicated Wave 0 test (cycle-2 HIGH-B closed).
- App-wide ArchUnit `GmailClientLookupBoundaryTest` covers both `backend/core` and `backend/api` callers (cycle-2 MEDIUM closed).
- Disconnect state matrix and implementation now agree on raw idempotent lookup (cycle-2 MEDIUM closed).
- Cross-plan symbol ownership is clean: Plan 04 owns + implements `addConnection`/`reconnect`; Plan 05 only calls them; Plan 06 is wave 5 with full `depends_on`.

### Agreed Concerns
None at HIGH severity. Two LOW execution-discipline watch items (authenticated-no-snapshot must not trigger first-login provisioning; ArchUnit allow-list bookkeeping verified against a live grep).

### Divergent Views
No second reviewer this cycle, so no inter-reviewer divergence. Convergence reached: 0 HIGH concerns against the revised plans.
