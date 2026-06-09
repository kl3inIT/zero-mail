---
phase: 10
reviewers: [codex]
reviewed_at: 2026-06-09T10:30:00Z
plans_reviewed: [10-01-PLAN.md, 10-02-PLAN.md, 10-03-PLAN.md, 10-04-PLAN.md, 10-05-PLAN.md, 10-06-PLAN.md]
---

# Cross-AI Plan Review — Phase 10 (Gmail Mailbox Foundation & Account Management)

> Reviewer: Codex CLI (codex-cli 0.137.0, default model). Single external reviewer this cycle (`--codex`).
> Each concern below was source-grounded against the actual Phase 10 plan files before being recorded. Grounding verdicts are annotated inline as **[GROUNDED]**, **[PARTIALLY GROUNDED]**, or **[NOT GROUNDED]** with the file/line evidence.

## Codex Review

### Summary

The plan set is unusually thorough and correctly focuses on the highest-risk invariants: migration safety, mailbox ownership, token-cache isolation, and OAuth flow separation. The main weakness is not lack of detail, but several contradictions between the intended security model and the concrete execution steps. The largest issues are Liquibase `MARK_RAN` on required schema, OAuth intent still riding URL params, reconnect being blocked by the disconnected-mailbox guard, and wave/dependency gaps that can make the phase fail or ship an incomplete AUD-04 surface.

### Strengths

- The central token-bleed threat is correctly identified: `GmailApiClientFactory.accessTokenCache` must be keyed by `gmailConnectionId`, not `tenantId`.
- The plan preserves AES-GCM AAD as `tenantId.toString()`, the right call for byte-identical token continuity.
- The migration research catches the constraint-vs-index distinction: `uq_gmail_connections_tenant_id` must be dropped as a constraint, not an index.
- Wave 0 validation is strong in intent: migration fixture, cache-isolation test, ownership test, OAuth intent routing, session-shim test all map to real failure modes.
- `MailboxRef(UUID tenantId, UUID gmailConnectionId)` is a good typed seam — accidental tenant-only Gmail access is harder to express.
- Privacy posture is mostly consistent: DTOs may expose mailbox metadata, logs do not emit raw email/tokens/bodies/prompts/completions.
- Keeping `buildClientForTenant` deprecated with an explicit allow-list is a pragmatic bridge into Phase 11.

### Concerns (with source-grounding verdicts)

- **[HIGH]** Changeset 119 uses `preConditions onFail: MARK_RAN` for a required structural migration. If the dedupe precondition fails, Liquibase records changeset 119 as applied while `is_primary`, `display_purpose`, and the new indexes do not exist — new application code then starts against a schema missing required columns. Should be `HALT`, not `MARK_RAN`.
  - **[GROUNDED]** `10-02-PLAN.md` lines 92, 103, 175 explicitly specify `onFail: MARK_RAN` and frame it as "fail-safe over corruption." For required structural DDL this is the wrong disposition — `MARK_RAN` marks the changeset applied without applying the columns the entity (Task 2) maps with `nullable = false`. App boot would then fail Hibernate schema validation or runtime inserts. Real correctness/availability bug.

- **[HIGH]** `10-05`/`10-06` contradict the locked OAuth decision by carrying `intent` and `targetMailboxId` as URL params on `/oauth2/authorization/google`. The plan says intent must not ride a tamperable URL param, but the trigger redirects include `?intent=add_mailbox` and `?targetMailboxId=...`. The resolver must derive intent from server-side session state.
  - **[PARTIALLY GROUNDED]** `10-06-PLAN.md` lines 106-107 do put `intent=add_mailbox` / `intent=reconnect_mailbox&targetMailboxId={id}` on the redirect URL, while `10-05-PLAN.md` line 18 `must_haves.truths` asserts intent is "never carried on a tamperable URL param." That is a genuine internal contradiction. Mitigating factors the plans DO specify: `initiatingTenantId` is read from the authenticated session, never a param (`10-05` line 112); the reconnect trigger pre-resolves ownership before redirecting (`10-06` line 107); the success handler re-validates ownership against the session. So the IDOR on `targetMailboxId` is largely closed, but a forged `intent` value on a direct hit to the authorization endpoint can still steer branch selection — the stated invariant and the implementation diverge. Worth fixing for defense-in-depth and to honor the documented contract.

- **[HIGH]** Reconnect appears impossible for disconnected mailboxes. `resolveOwnedConnectionOrThrow` is defined as 409 on `DISCONNECTED`, but `ConnectMailboxController.reconnect` and the success-handler reconnect branch both call it. GMA-04 needs reconnect to refresh a disconnected/invalid mailbox. Add a separate reconnect ownership method that permits reconnectable non-connected states.
  - **[GROUNDED]** `10-04-PLAN.md` lines 19/79/128 define `resolveOwnedConnectionOrThrow` to throw `MailboxDisconnectedException` (409) on `DISCONNECTED`. `10-06-PLAN.md` line 107 calls exactly that method before the reconnect redirect, and `10-05` line 141 calls it again in the reconnect branch. A disconnected mailbox is the canonical reconnect target → both call sites would 409 and make reconnect unreachable. Notably `10-04` line 157 already uses raw `findByIdAndTenantId` (not the throwing resolver) for disconnect idempotency, proving the authors know the resolver is too strict for non-CONNECTED flows — they just did not apply the same reasoning to reconnect. Real functional defect against GMA-04.

- **[HIGH]** Disconnected-state semantics are inconsistent across plans (409 on mailbox-scoped requests vs idempotent disconnect vs reconnect needing the disconnected row). Define endpoint-specific behavior: set-primary 409, reconnect allowed, disconnect idempotent, reads include disconnected metadata.
  - **[GROUNDED]** Same evidence as the prior concern plus `10-04` line 130 (set-primary calls `resolveOwnedConnectionOrThrow` → 409 on disconnected, correct) and line 157 (disconnect deliberately bypasses it → idempotent, correct) and `10-06` line 146 (`MailboxOwnershipSeamTest` expects 409 on disconnected). There is no single documented state matrix; reconnect is the one endpoint where the chosen guard is wrong. This is the design-level root cause behind the reconnect bug.

- **[HIGH]** `10-06-PLAN.md` has incorrect dependencies — uses `MailboxRef` (Plan 03) and OAuth intent behavior (Plan 05) but `depends_on` only lists `10-02` and `10-04`. Parallel wave-4 execution could compile/behave incorrectly. Should depend on `10-03` and probably `10-05`.
  - **[GROUNDED]** `10-06-PLAN.md` line 6 `depends_on: ["10-02", "10-04"]`. Body references `MailboxRef` from Plan 03 (lines 128, 136) and the resolver's intent-param contract from Plan 05 (lines 100, 106-107). Both 05 and 06 are `wave: 4`, so a parallel runner has no ordering guarantee that 03's `MailboxRef` and 05's resolver exist when 06 compiles/runs. Real dependency-ordering risk.

- **[HIGH]** Plan 05 may add `GmailConnectionService.addConnection(...)`/`reconnect(...)` but its `files_modified` excludes `GmailConnectionService.java`; Plan 04 lists those helpers as artifacts but its tasks do not fully implement them. Move the helpers explicitly into Plan 04, or include the core service file in Plan 05.
  - **[GROUNDED]** `10-05-PLAN.md` lines 7-12 `files_modified` lists only api/security files — no `GmailConnectionService.java` — yet Task 3 line 142 says "Add the `addConnection`/`reconnect` helper methods to `GmailConnectionService` if Plan 04 left them as forward-decls." `10-04-PLAN.md` line 80 lists `addConnection(...) / reconnect(...)` as produced artifacts, but Tasks 2-3 (lines 117-182) never specify their implementation (encrypt-and-insert for add, targeted-row update for reconnect). The helpers fall through the crack between the two plans. Real ownership/completeness gap.

- **[HIGH]** `resolveOwnedConnectionOrThrow` only treats `DISCONNECTED` as invalid; `NOT_CONNECTED` and `PENDING` should also fail closed for normal mailbox-scoped operations, else a mailbox with no usable grant flows into client construction / state changes.
  - **[GROUNDED]** `10-04-PLAN.md` line 128 checks only `status DISCONNECTED → 409`; `GmailConnectionStatus` has `CONNECTED/DISCONNECTED/PENDING/NOT_CONNECTED` (line 123). A `PENDING`/`NOT_CONNECTED` row would pass the guard and could reach `buildClientForMailbox` or set-primary. Valid fail-open gap.

- **[HIGH]** OAuth intent cleanup on failure is not covered. The shim writes `ZEROMAIL_OAUTH_INTENT` during `removeAuthorizationRequest` before token exchange completes; if auth fails, the success handler never removes it. A stale `add_mailbox`/`reconnect_mailbox` snapshot could affect a later callback. The failure handler should clear it, and Wave 0 should test that.
  - **[GROUNDED]** `10-05-PLAN.md` Task 1 line 88 writes the session attribute on `removeAuthorizationRequest` (pre-token-exchange). The one-shot removal lives only in the success handler (Task 3 line 138). No failure-handler cleanup is specified anywhere in Plan 05 (no `defaultFailureUrl`/failure-handler edit in `files_modified`). Stale-intent reuse is a real, untested path.

- **[HIGH]** The success handler should verify the current authenticated app session still matches `OAuthIntentSnapshot.initiatingTenantId`; the plan stores the initiating tenant but trusts it on callback. If the session is changed/reused/fixed between flow start and callback, the handler could write to the wrong tenant.
  - **[PARTIALLY GROUNDED]** `10-05-PLAN.md` line 140-141 binds `TenantContext.TENANT` to `snapshot.initiatingTenantId()` and runs the write under it. The plan does not specify a callback-time equality check between the live authenticated session's tenant and `initiatingTenantId` (Plan 06 line 107 mentions the handler "re-validates ownership against the session" for reconnect, but not a tenant-identity match for add). Adding an explicit "current session tenant must equal initiatingTenantId, else clear intent and fail closed" check is a cheap, correct hardening. Valid as defense-in-depth.

- **[MEDIUM]** `GmailClientLookupBoundaryTest` scopes only to `..core..`, but research identifies an API caller `api.chat.AssistantPendingActionReconciler` — the ArchUnit rule does not enforce the boundary app-wide. Broaden or allow-list.
  - Grounded against the plan's own research reference; reasonable to widen the rule or add an API-side rule.

- **[MEDIUM]** `buildClientForMailbox(MailboxRef)` resolves by id+tenant but does not require `CONNECTED` / non-null encrypted refresh token; public factory entry should fail loud before decrypt/build.
- **[MEDIUM]** Rollback for changeset 119 is only safe before multiple mailbox rows exist; once a tenant has two rows, re-adding `uq_gmail_connections_tenant_id` fails. Document rollback as pre-use only or add a halting precondition.
- **[MEDIUM]** Plans say `uq_gmail_conn_primary` enforces "exactly one primary," but a partial unique index enforces *at most one*. Rename assertions/tests to "at most one primary" plus "service/backfill creates one when applicable."
- **[MEDIUM]** AUD-04 admin/operator tenant inspection is not actually implemented — plans add a user-facing mailbox summary, no admin API/console surface. Either add an admin metadata-only endpoint or drop AUD-04 from Phase 10 success claims.
- **[MEDIUM]** Duplicate-active pre-check needs a tenant-scoped repo method (e.g. `existsByTenantIdAndGoogleEmailIgnoreCaseAndStatus(...)`); the existing `findByGoogleEmailIgnoreCase` sounds global and could leak/over-block across tenants.
- **[MEDIUM]** Wave 0 migration test may be fragile if `PostgresContainerTest` auto-applies all migrations before setup; seeding a pre-119 row usually needs a custom Liquibase runner / pre-119 fixture.
- **[MEDIUM]** Deferring OpenAPI regen conflicts with the repo convention that generated OpenAPI files update when DTOs/endpoints change; if deferred intentionally, confirm CI does not validate schema drift and record the exception.
- **[LOW]** `OAuthIntentSnapshot.intent` as raw `String` is weaker than an enum/sealed parser; unknown intent should fail closed and clear the session attribute.
- **[LOW]** Token-cache test plan suggests spying/mocking `refreshAccessToken`; if private/non-injectable it may force awkward changes. Prefer a fake HTTP token endpoint or explicit collaborator.
- **[LOW]** `display_purpose varchar` has no length/validation; add a bounded length in DB + service validation.

### Suggestions

- Change changeset 119 precondition behavior from `MARK_RAN` to `HALT`.
- Replace URL-carried OAuth intent with server-side intent storage at the trigger endpoint (validate ownership, store snapshot/intent in session, redirect without `intent`/`targetMailboxId`).
- Add a dedicated reconnect ownership method: 404 for missing/not-owned, allow disconnected/reconnectable states, reject only non-reauthorizable states; success handler still revalidates on callback.
- Define an explicit mailbox state matrix per endpoint (list / set-primary / disconnect / reconnect / factory build / legacy default lookup) and align tests + exceptions to it.
- Add OAuth failure-handler cleanup for `ZEROMAIL_OAUTH_INTENT` + tests for failed callback and stale-intent non-reuse.
- Add a callback-time tenant-consistency check (live session tenant must equal `initiatingTenantId`, else clear + fail closed).
- Broaden the ArchUnit rule to include `backend/api`, or add a second API rule for `buildClientForTenant`.
- Move `addConnection`/`reconnect` helper implementation explicitly into Plan 04 (encryption, duplicate mapping, status transitions), or add the core service file to Plan 05's `files_modified`.
- Add tests for: reconnecting a disconnected mailbox, unknown OAuth intent, stale intent after failure, direct authorization-URL tampering, `buildClientForTenant` throwing when a tenant has multiple connected mailboxes.
- Decide and document whether AUD-04 admin/operator inspection is in Phase 10.

### Risk Assessment

Overall: **MEDIUM-HIGH**. Architecture direction is sound and validation targets the right failure modes, but the plans contain several correctness/security contradictions in the highest-risk areas: required-migration skip behavior, OAuth intent transport, disconnected-reconnect behavior, stale session intent, and dependency ordering. Fixing those before execution drops the phase to medium risk; executing as written risks a blocked migration, incomplete account management, or an OAuth path that violates the intended anti-IDOR design.

---

## Consensus Summary

Only one external reviewer (Codex) ran this cycle, so "consensus" is Codex's findings filtered through a source-grounding pass against the six plan files. Of the 9 HIGH concerns Codex raised, **7 are fully grounded** and **2 are partially grounded** (defense-in-depth hardening against a stated-but-not-fully-enforced invariant). None were fabricated or contradicted by the plans.

### Agreed Strengths
- Connection-scoped token cache (`gmailConnectionId`-keyed) correctly fixes the cross-mailbox token-bleed threat.
- Byte-identical AES-GCM token continuity (AAD stays `tenantId.toString()`) is preserved through the migration.
- The `DROP CONSTRAINT` (not `DROP INDEX`) distinction for `uq_gmail_connections_tenant_id` is correct (verified against `003-create-gmail-connections.yaml` style).
- Typed `MailboxRef` seam and the fail-closed `resolveOwnedConnectionOrThrow` ownership pattern are sound anti-IDOR foundations.

### Agreed Concerns (highest priority — fix before execution)
1. **Migration `MARK_RAN` on required schema (HIGH, grounded):** change changeset 119 dedupe precondition to `HALT`. `MARK_RAN` can mark the changeset applied while the `NOT NULL` `is_primary` / `display_purpose` columns + indexes are absent → app boots against a missing-column schema.
2. **Reconnect blocked by the disconnected guard (HIGH, grounded):** `resolveOwnedConnectionOrThrow` 409s on `DISCONNECTED`, but Plan 06 and Plan 05's reconnect path call it on exactly the disconnected mailbox they intend to reconnect → GMA-04 reconnect is unreachable. Add a reconnect-specific ownership method that permits reconnectable states; success handler still revalidates.
3. **Disconnected-state matrix undefined (HIGH, grounded):** define per-endpoint semantics (set-primary 409, reconnect allowed, disconnect idempotent, reads include disconnected metadata) — root cause behind concern #2.
4. **Plan 06 missing dependencies (HIGH, grounded):** add `10-03` (and likely `10-05`) to `depends_on`; both are wave 4, so parallel execution has no ordering guarantee for `MailboxRef` / the resolver.
5. **`addConnection`/`reconnect` helpers fall between Plan 04 and Plan 05 (HIGH, grounded):** neither plan fully owns their implementation; assign them to Plan 04 explicitly or add the core service file to Plan 05's `files_modified`.
6. **`resolveOwnedConnectionOrThrow` fail-open on `PENDING`/`NOT_CONNECTED` (HIGH, grounded):** guard only checks `DISCONNECTED`; non-usable grants can reach client construction.
7. **No OAuth intent cleanup on auth failure (HIGH, grounded):** stale `ZEROMAIL_OAUTH_INTENT` survives a failed callback; add failure-handler cleanup + Wave 0 test.
8. **OAuth intent on URL params contradicts Plan 05's stated invariant (HIGH, partially grounded):** `targetMailboxId` IDOR is mitigated by ownership re-validation, but the `intent` value still rides the URL against a `must_haves` truth that says it must not; honor the contract or update it.
9. **No callback-time tenant-identity check (HIGH, partially grounded):** verify the live session tenant equals `initiatingTenantId` before writing; cheap hardening.

### Divergent Views
No second reviewer this cycle, so no inter-reviewer divergence. The only internal tension is between Codex's HIGH framing of the OAuth-intent-on-URL concern (#8) and the partial mitigations the plans already specify (session-derived `initiatingTenantId` + ownership re-validation) — captured above as partially grounded rather than a clean HIGH.
