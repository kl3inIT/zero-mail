---
phase: 10
reviewers: [codex]
reviewed_at: 2026-06-09T03:54:36Z
convergence_cycle: 2
plans_reviewed: [10-01-PLAN.md, 10-02-PLAN.md, 10-03-PLAN.md, 10-04-PLAN.md, 10-05-PLAN.md, 10-06-PLAN.md]
cycle_1_high_count: 9
current_high_count: 2
---

# Cross-AI Plan Review — Phase 10 (Gmail Mailbox Foundation & Account Management) — Convergence Cycle 2

> Reviewer: Codex CLI (codex-cli 0.137.0, default model). Single external reviewer this cycle (`--codex`).
> This is convergence cycle 2: the 6 plans were revised to address the 9 HIGH concerns from cycle 1. The prompt asked Codex to (a) verify each cycle-1 HIGH against the REVISED plan text and (b) surface remaining/newly-introduced HIGHs.
> Every verdict below was source-grounded against the actual revised Phase 10 plan files before being recorded. Grounding annotations are inline.

## Codex Review

### Cycle-1 HIGH Resolution Verdicts

| # | Cycle-1 concern | Verdict | Evidence |
|---|-----------------|---------|----------|
| 1 | Migration 119 used `onFail: MARK_RAN` on required structural DDL | **RESOLVED** | `10-02-PLAN.md` Task 1 (lines 92, 103) now mandates `onFail: HALT`, explicitly rejects `MARK_RAN` ("this MUST be `onFail: HALT`, NOT `MARK_RAN`"), explains the missing-column boot failure, and the acceptance greps for `onFail: HALT` + absence of `MARK_RAN`. Threat register T-10-02-04 records the same. |
| 2 | Reconnect blocked by `resolveOwnedConnectionOrThrow` 409-on-DISCONNECTED | **RESOLVED** | `10-04-PLAN.md` adds `resolveReconnectableConnectionOrThrow` (lines 100-101, 151, 161) permitting DISCONNECTED/PENDING/NOT_CONNECTED/CONNECTED; `10-06-PLAN.md` Task 2 (line 110, 118) requires the reconnect endpoint to call the reconnectable resolver "NOT `resolveOwnedConnectionOrThrow`"; `10-05-PLAN.md` Task 3 (line 155) routes reconnect through it. Disconnected reconnect is reachable. |
| 3 | No single per-endpoint disconnected-state matrix | **RESOLVED** | `10-04-PLAN.md` "Mailbox State Matrix" (lines 61-77) is now the documented single source of truth: list includes all statuses, set-primary 409 on non-connected, disconnect idempotent (raw lookup), reconnect permits reconnectable states, factory build connected-only. Plan 01/06 tests align to it. |
| 4 | Plan 06 wrong `depends_on`; 05 & 06 both wave 4 with no edge | **RESOLVED** | `10-06-PLAN.md` frontmatter now `wave: 5`, `depends_on: ["10-02","10-03","10-04","10-05"]` (lines 5-6); objective dependency note (line 46) explains the move out of wave 4 so MailboxRef/seam/intent contract exist first. |
| 5 | `addConnection`/`reconnect` helpers fell between Plan 04 and 05 | **RESOLVED** | `10-04-PLAN.md` Task 4 (lines 208-235) fully implements both helpers with `GmailConnectionService.java` in `files_modified` (line 12); `10-05-PLAN.md` (lines 78, 156, 167) states Plan 05 only CALLS them and does NOT modify `GmailConnectionService.java` (file intentionally absent from its `files_modified`). |
| 6 | `resolveOwnedConnectionOrThrow` fail-open on PENDING/NOT_CONNECTED | **RESOLVED** | `10-04-PLAN.md` Task 2 (line 150) requires the guard to throw on "any status that is NOT `CONNECTED`" (`status != CONNECTED`), with acceptance (line 160) demanding it not be a DISCONNECTED-only check. State matrix row confirms 409 for NOT_CONNECTED/PENDING/DISCONNECTED on set-primary. |
| 7 | No OAuth intent cleanup on auth failure | **RESOLVED (for the auth-failure path)** — but see new HIGH-B | `10-05-PLAN.md` Task 4 (lines 182-184) adds unconditional `ZEROMAIL_OAUTH_INTENT` removal at the TOP of `LoginRedirectAuthenticationFailureHandler.onAuthenticationFailure` (before the switch, `getSession(false)`); `10-01-PLAN.md` Task 3 (line 150) adds the stale-intent-after-failure Wave 0 test. The classic `AuthenticationFailureHandler` path is closed. A sibling gap remains for success-handler-internal early failures (new HIGH-B below). |
| 8 | OAuth intent rides tamperable URL params (contradicts must_have / D-01) | **PARTIALLY-RESOLVED** — carried as current HIGH-A | Real mitigations added: `10-05-PLAN.md` Task 2 (lines 118-123) validates URL `intent` against the closed `{add_mailbox, reconnect_mailbox}` enum, requires an authenticated session, derives `initiatingTenantId` from SecurityContext, treats `targetMailboxId` as an untrusted hint re-validated on callback; Task 3 (line 151) adds the callback tenant check. BUT the branch still ORIGINATES from tamperable URL state: `10-06-PLAN.md` Task 2 (lines 109-110) emits `?intent=add_mailbox` / `?intent=reconnect_mailbox&targetMailboxId=...`, and `10-05` line 121 reads the URL `intent` param when authenticated. This still diverges from `10-05` must_have (line 19) and CONTEXT D-01 ("intent never rides a tamperable URL param"). |
| 9 | No callback-time tenant-identity check | **RESOLVED** | `10-05-PLAN.md` Task 3 (line 151) requires the success handler to assert live session tenant EQUALS `OAuthIntentSnapshot.initiatingTenantId()` before any add/reconnect write, log `event=oauth_intent_tenant_mismatch`, clear intent, and fail closed; acceptance (line 163) greps for the equality check + mismatch event. |

**Tally:** 7 of 9 cycle-1 HIGHs fully RESOLVED (1, 2, 3, 4, 5, 6, 9). #7 resolved for the auth-failure path but exposed a sibling success-handler gap (current HIGH-B). #8 PARTIALLY-RESOLVED (current HIGH-A).

### Remaining or Newly-Introduced HIGH Concerns

- **[HIGH-A] OAuth branch authority still depends on tamperable URL params (partially-resolved #8).**
  - Source: `10-05-PLAN.md` Task 2 (authenticated request reads the URL `intent` param); `10-06-PLAN.md` Task 2 (lines 109-110) emits redirects carrying `intent=add_mailbox` / `intent=reconnect_mailbox` / `targetMailboxId=...`.
  - Why HIGH: closed-enum validation + authenticated-session gate + callback ownership/tenant re-checks materially reduce the IDOR/branch-steering risk, but the SELECTED OAuth branch is still driven by attacker-editable URL state, which does not satisfy D-01 / the Plan 05 must_have that intent must never ride a tamperable URL param. Stated invariant and implementation still diverge.
  - **[GROUNDED]** Confirmed against the revised text: `10-06` lines 109-110 carry the params on the redirect; `10-05` line 121 reads the URL `intent`; `10-05` line 19 + CONTEXT D-01 assert the opposite invariant.
  - Fix (Codex): `ConnectMailboxController` should create a server-side pending-intent snapshot (in the session) after the pre-resolve, then redirect to `/oauth2/authorization/google?reconnect=true` with NO `intent`/`targetMailboxId` params; the resolver stamps OAuth attributes ONLY from that server-side snapshot. An authenticated request with no valid server-side management intent should fail/redirect, not silently fall through to first-login provisioning.

- **[HIGH-B] Success-handler validation failures BEFORE the one-shot intent removal can still leave a stale OAuth intent (newly surfaced by the #7 fix).**
  - Source: `10-05-PLAN.md` Task 3 (line 150) reads/removes `ZEROMAIL_OAUTH_INTENT` "after extracting oidcUser/subject/email + loading the authorized client once + the existing scope/null-refresh checks"; Task 4 only clears the attribute in `onAuthenticationFailure`.
  - Why HIGH: the existing success handler runs its own scope-check and null-refresh-token checks (handler lines ~126-169) and can short-circuit/redirect BEFORE the planned removal point. If that short-circuit does not route through `LoginRedirectAuthenticationFailureHandler.onAuthenticationFailure`, the Task 4 cleanup never fires and the stale `add_mailbox`/`reconnect_mailbox` snapshot survives into the user's next callback — the exact bleed #7 set out to close, on a different exit path.
  - **[GROUNDED]** Confirmed: Task 3 line 150 places the one-shot removal after the scope/null-refresh checks; Task 4 cleanup is scoped only to the failure handler; no `try/finally`/early-removal is specified for success-handler-internal early exits.
  - Fix (Codex): read and remove `ZEROMAIL_OAUTH_INTENT` at the very TOP of `onAuthenticationSuccess` (before any scope/refresh validation), or wrap the handler body in a `try/finally` that removes the attribute on every success-handler exit path before any redirect/failure delegation. Add a Wave 0 case for "success-handler early-failure leaves no reusable intent."

### Other Concerns (MEDIUM / LOW)

- **[MEDIUM]** `10-03-PLAN.md` Task 3 places the app-wide `GmailClientLookupBoundaryTest` in `backend/core/src/test`, but it claims to also cover `backend/api` callers (`AssistantPendingActionReconciler`, `E2eStubGmailApiClientFactory`). Unless the core test runtime actually has the API classes on its classpath, `@AnalyzeClasses(packages = "com.zeromail")` will silently analyze only core classes and the API-tier boundary will NOT be enforced. Move the rule to a test source set / module where BOTH api and core main classes are importable, or verify the core test classpath includes `backend/api` main. (Partial regression risk against the cycle-1 MEDIUM that asked to broaden the rule app-wide — the intent is right, the placement may defeat it.)
- **[MEDIUM]** `10-04-PLAN.md` State Matrix says disconnect is a no-op for NOT_CONNECTED/PENDING, but Task 3 (lines 181-185) describes always calling `markDisconnected(mailboxRef)`, which would flip those rows to DISCONNECTED. Align the matrix wording and the implementation (either truly no-op on already-non-connected, or document that disconnect normalizes any state to DISCONNECTED).
- **[LOW]** Stale shorthand that could mislead the executor: `10-04-PLAN.md` objective (line 54) still summarizes `resolveOwnedConnectionOrThrow` as "409 disconnected" (the detailed Task 2 correctly says any non-CONNECTED); `10-05-PLAN.md` `key_links` (line 42-43) names `resolveOwnedConnectionOrThrow / addConnection` even though reconnect must use the reconnectable resolver. Detailed tasks are correct; clean up the shorthand to avoid drift.

### Risk Assessment

**Overall: HIGH.** Seven of the nine cycle-1 HIGHs are genuinely repaired in the plan text (migration HALT, reconnectable resolver, state matrix, dependency ordering + wave 5, helper ownership, non-CONNECTED fail-closed, callback tenant check). The residual risk is concentrated in OAuth intent handling: (A) branch authority still originates from tamperable URL params against the locked D-01 invariant, and (B) the #7 failure-handler fix left a sibling hole where success-handler-internal early validation failures can still leave a stale intent. Both are cheap, localized fixes (server-side intent snapshot + early/`finally` one-shot removal). Two MEDIUMs (ArchUnit classpath scope, disconnect no-op vs flip) are worth fixing before execution. **Current HIGH count: 2.**

---

## Consensus Summary

Only one external reviewer (Codex) ran this cycle, so "consensus" is Codex's findings filtered through a source-grounding pass against the six revised plan files. Codex verified that **7 of the 9 cycle-1 HIGHs are fully resolved** in the revised plans, **1 is partially resolved** (#8 → current HIGH-A), and the **#7 fix surfaced a sibling gap** (current HIGH-B). No verdict was fabricated or contradicted by the plans.

### Agreed Strengths (revisions that landed correctly)
- Migration 119 now `onFail: HALT` on required structural DDL — no false-applied changeset against a missing-column schema (cycle-1 #1 closed).
- Two-resolver design (`resolveOwnedConnectionOrThrow` CONNECTED-only vs `resolveReconnectableConnectionOrThrow` permits reconnectable states) + the documented per-endpoint State Matrix close cycle-1 #2, #3, and #6 together.
- Plan 06 moved to wave 5 with full `depends_on` — real ordering guarantee for MailboxRef / seam / intent contract (cycle-1 #4 closed).
- `addConnection`/`reconnect` now fully owned + implemented in Plan 04 with `GmailConnectionService.java` in its `files_modified`; Plan 05 only calls them (cycle-1 #5 closed).
- Callback-time `session tenant == initiatingTenantId` check added before any add/reconnect write (cycle-1 #9 closed).

### Agreed Concerns (current HIGHs — fix before execution)
1. **OAuth branch authority still rides tamperable URL params (HIGH-A, partially-resolved #8):** move intent to a server-side session snapshot created by `ConnectMailboxController`; redirect with no `intent`/`targetMailboxId`; resolver stamps attributes only from the snapshot; authenticated-but-no-valid-management-intent must fail closed, not fall through to first-login.
2. **Stale intent can survive success-handler early-failure (HIGH-B, new):** read+remove `ZEROMAIL_OAUTH_INTENT` at the very top of `onAuthenticationSuccess` or via `try/finally`, before the scope/null-refresh checks that can short-circuit; add a Wave 0 case.

### Divergent Views
No second reviewer this cycle, so no inter-reviewer divergence. The only internal tension is whether #8's partial mitigations (closed-enum validation + authenticated session + callback re-checks) are "good enough" despite the URL-param origin; Codex (and this grounding pass) hold the stricter line because the Plan 05 must_have and CONTEXT D-01 explicitly forbid intent on a tamperable URL param — so it is recorded as an unresolved HIGH rather than downgraded.
