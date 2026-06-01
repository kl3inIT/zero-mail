---
phase: 9
cycle: 2
reviewers: [codex, opencode]
reviewed_at: 2026-05-26T13:15:21Z
plans_reviewed:
  - 09-01-PLAN.md
  - 09-02-PLAN.md
  - 09-03-PLAN.md
  - 09-04-PLAN.md
  - 09-05-PLAN.md
  - 09-06-PLAN.md
  - 09-07-PLAN.md
previous_cycle_commit: e81633b3
replan_commit: 53e2fb1a
---

# Cross-AI Plan Re-Review — Phase 9 (Cycle 2 of plan-review-convergence)

This cycle re-reviews the replan that responds to the 5 HIGH concerns from cycle 1.

The 5 HIGH concerns being verified:

- **HIGH-1**: Plan 09-04 size / test ordering (Task 1 SsrfTest only; Task 2 SingleBindingTest; Task 3 SentinelLeakTest).
- **HIGH-2**: Legacy BYOK rename + ByokController gap (09-01 no rename; 09-04 410-Gone shim; 09-06 Task 5 deletes shim).
- **HIGH-3**: SSRF + prompt cap + quoted-reply + PREVIEW (BaseUrlValidator hardened; QuotedReplyStripper + 60k cap; 3-sentinel test; PREVIEW STEP 0).
- **HIGH-4**: BYOK Test/Save state machine (Save→Test→Pick→Activate; no inline-pre-save Test; last_test_models_json + model_not_in_last_test).
- **HIGH-5**: SET-SAFE-04 frontend gap (AuditSafetyNetBadge + AuditRow/AuditCardList + Playwright step 14).

---

## Codex Review

## Summary

The replan converges substantially, but not completely. It resolves the execution-order and legacy BYOK compile-gap issues well, and the SET-VOICE-07 privacy plan is much stronger. Net HIGH count drops from **5 to 3 remaining blockers**. I do **not** see a separate new unrelated HIGH, but three claimed fixes are still incomplete in the plan text: SSRF transport enforcement, `last_test_models_json` schema support, and audit-log API exposure for `blockedBySafetyNetPattern`.

## Previous-Cycle HIGH Verification

### HIGH-1: Plan 09-04 size / test ordering

**Status: FULLY RESOLVED**

Evidence:
- `09-04 Task 1` explicitly says `ProviderConnectionTesterSingleBindingTest` and `UserByokTestConnectionSentinelLeakTest` are **not** filled in Task 1.
- `09-04 Task 1` fills only `BaseUrlValidatorSsrfTest` plus `MasterKeySentinelLeakTest`.
- `09-04 Task 2` owns `ProviderConnectionTesterSingleBindingTest`, after `UserByokService` exists.
- `09-04 Task 3` owns `UserByokTestConnectionSentinelLeakTest`, after `UserByokController` exists.
- The `execution_discipline` block adds per-task commit/verify boundaries.

Residual gap: 09-04 is still large, but the original hard compile-order blocker is addressed.

### HIGH-2: Legacy BYOK rename + ByokController gap

**Status: FULLY RESOLVED**

Evidence:
- `09-01 Task 1` now repeatedly states that `tenant_byok_credentials` is **left intact**, with no rename/drop/archive rename during Phase 9.
- `09-04 Task 2` rewrites legacy `ByokController` as a deprecated **410 Gone** shim at `/api/llm/byok`.
- `09-06 Task 5` deletes the shim after new `/api/byok` endpoints and frontend hooks are wired.

Residual cleanup: `09-01` still has stale text in the objective/threat model/verification mentioning archive rename or archived table. That should be cleaned, but the executable task behavior and acceptance criteria now point the right way.

### HIGH-3: SSRF + prompt cap + quoted-reply + PREVIEW

**Status: PARTIALLY RESOLVED**

Evidence of resolved parts:
- `09-04 Task 1` adds `BaseUrlValidatorSsrfTest` and rejects private, loopback, link-local, unspecified, CGNAT, multicast, IPv6 ULA, and disallowed ports.
- `09-05 must_haves` adds `MAX_AGGREGATE_PROMPT_CHARS=60_000`.
- `09-05 Task 1` adds `QuotedReplyStripper` with quoted-reply sentinel coverage.
- `09-05 Task 2` makes `CallSite.PREVIEW` verification mandatory STEP 0 and blocks execution if PREVIEW persists prompt/completion.
- `VoiceGenerationFromSentLeakTest` now uses three sentinels: body, quoted inbound, and completion.

Remaining HIGH gap:
- The SSRF plan validates and re-resolves the host before the `RestClient` call, but it does not require the outbound HTTP client to connect to the validated IP, use a pinned resolver, or disable/revalidate redirects. A DNS rebinding or redirect-to-private-IP bypass is still possible if the HTTP transport resolves again or follows redirects.

### HIGH-4: BYOK Test/Save state machine

**Status: PARTIALLY RESOLVED**

Evidence of resolved state machine:
- `09-04 must_haves` locks lifecycle to **Save → Test stored row → Pick model → Activate**.
- `POST /api/byok/test-connection` is stored-row only, empty body, with `404 code=ai.byok.no_row` if no row exists.
- `09-06 Task 4` mirrors this in the UI: Test disabled until saved, Save resets local model cache, Active disabled until `modelId` and `lastTestResult === OK`.

Remaining HIGH gap:
- `09-04` depends on a new persisted `last_test_models_json` field for model membership validation and reset behavior, but `09-01` does **not** add that column to `097-user-byok-key-table.yaml`, and `09-01 Task 2` does **not** add it to `UserByokKeyEntity`.
- `09-04 Task 2` also does not list `UserByokKeyEntity` or the Liquibase changeset as modified, so the implementation would either fail to compile, fail at runtime, or silently drop the server-side `model_not_in_last_test` guard.

### HIGH-5: SET-SAFE-04 frontend gap

**Status: PARTIALLY RESOLVED**

Evidence of resolved frontend work:
- `09-06 Task 5` adds `AuditSafetyNetBadge`.
- It wires the badge into both `AuditRow.tsx` and `AuditCardList.tsx`.
- It adds localized copy and a Vitest test.
- `09-07 Task 1 step 14` adds Playwright coverage for the badge.

Remaining HIGH gap:
- No backend plan explicitly updates the triage audit API DTO/projection/controller response to expose `blockedBySafetyNetPattern` to OpenAPI.
- `09-06 Task 5` says to "confirm" the regenerated schema has the field, but `09-03` only wires the entity/worker side and does not list an audit-log DTO or controller response file. If the existing audit endpoint uses explicit DTO mapping, the frontend badge will never receive the value.

## New Concerns

### HIGH: None independent of the residual gaps above

I do not see a separate new HIGH unrelated to the five previous issues. The remaining HIGHs are incomplete fixes for HIGH-3, HIGH-4, and HIGH-5.

### MEDIUM: Stale archive-table text can mislead execution

Reference: `09-01` objective/threat model/verification.

Some text still says the legacy table is archived/renamed, while the corrected task behavior says it is left intact. This is likely to create reviewer/executor confusion.

### MEDIUM: `ByokSaveRequest` still includes nullable `modelId`

Reference: `09-04 Task 3`.

The locked lifecycle says Save always clears model/test state and model selection happens only after Test via `PUT /api/byok/model`. Keeping `modelId` on `POST /api/byok` is misleading and may leak a stale generated schema/API affordance to the frontend.

### MEDIUM: Playwright safety-net endpoint regex looks stale

Reference: `09-07 Task 1 step 7`.

The plan says `page.waitForResponse(/api\/safety-net-senders/)`, while the actual planned endpoint family is `/api/triage/sender-safety-net`. This will make the e2e step flaky or nonfunctional unless adjusted.

## Suggestions

1. Add `last_test_models_json JSONB NULL` to `09-01` changeset 097 and `UserByokKeyEntity`, with reset assertions in `09-01`/`09-04`. Keep the model membership check in `09-04` only after the schema exists.
2. Strengthen the SSRF plan: `ProviderConnectionTester` should accept a validated target, disable redirects or revalidate every `Location`, and either pin the connection to the validated IP or use an HTTP client resolver that cannot re-resolve to a different address after validation. Add tests for redirect-to-`169.254.169.254` and DNS-rebind-at-request-time.
3. Add an explicit audit-log API task: update the triage audit response DTO/projection/OpenAPI schema to include `blockedBySafetyNetPattern`, then make `09-06 Task 1` assert the regenerated schema contains it.
4. Remove `modelId` from `ByokSaveRequest`; model selection belongs only to `PUT /api/byok/model`.
5. Clean all remaining `tenant_byok_credentials_archived_2026_05_26` references from `09-01` unless they are explicitly marked as historical context.

## Risk Assessment

**Overall risk: MEDIUM-HIGH before edits; MEDIUM after the three residual HIGH fixes land.**

The replan is much closer to executable than cycle 1. The biggest remaining risk is not product ambiguity, but plan-code mismatch: a service plan now depends on schema that Wave 0 does not create, the audit badge depends on a response field no backend API task guarantees, and the SSRF mitigation is strong at validation time but not yet strong enough at transport time. Once those are patched in the plan text, I would consider the phase ready to proceed.

---

## OpenCode Review

## 1. Summary

The replan converges substantially. All 5 HIGH concerns from cycle 1 are addressed in plan text with explicit, verifiable mitigations. The plan set is structurally consistent across 7 files with **one intra-plan contradiction** (09-01 verification block vs. body) and **one verification-gap clarification** needed (Anthropic header assertion). Net HIGH concern delta: **−5** (5 resolved, 0 new HIGH). Overall risk: **MEDIUM** (down from HIGH in cycle 1).

## 2. Previous-cycle HIGH verification

### HIGH-1 — Plan 09-04 size / test ordering → **FULLY RESOLVED**

**Cited evidence:**
- `09-04-PLAN.md` Task 1 header (l.221): `"ArchUnit single-binding + sentinel-leak tests moved to Tasks 2 + 3 where their referenced classes exist"`
- **BaseUrlValidatorSsrfTest** → Task 1 (classes `BaseUrlValidator` + `HostResolver` written in same task)
- **ProviderConnectionTesterSingleBindingTest** → Task 2 (both `MasterKeyAdminService` + `UserByokService` exist by Task 2)
- **UserByokTestConnectionSentinelLeakTest** → Task 3 (`UserByokController` exists in Task 3)

**Residual gap:** 09-04 remains a single 34-file, 3-task plan. The plan acknowledges this (`<execution_discipline>` block, l.106–117) and adds commit-discipline guards. Both cycle-1 reviewers recommended splitting; the plan argues tight coupling prevents it. The risk is MEDIUM (mitigated) rather than HIGH (unresolved).

### HIGH-2 — Legacy BYOK rename + ByokController gap → **FULLY RESOLVED**

**Cited evidence:**
- **09-01** (l.78, 89, 203, 210, 224, 357): six explicit statements that `tenant_byok_credentials` is LEFT INTACT — no rename, no archive, no drop. Archive-rename deferred to v1.3 follow-up.
- **09-04 Task 2** (l.64, 294, 326): `ByokController` is REWRITTEN as a `@Deprecated` 410-Gone shim (not deleted), body `{code: "ai.byok.moved", message: "Use /api/byok"}`, `Location: /api/byok` header. Zero legacy dependencies — `backend/api:compileJava` survives.
- **09-06 Task 5** (l.460–461): the shim is DELETED after `ByokForm.tsx` is removed in Task 2 and after `mcp__jetbrains__search_in_files_by_text "/api/llm/byok"` returns zero hits.

**Intra-plan contradiction flagged:** 09-01 verification block (l.348) says `"the renamed tenant_byok_credentials_archived_2026_05_26 archive table"` — expects a renamed archive table in the DB. This directly contradicts the 6 "left intact" statements in the same plan. The verification text is stale. **Must be fixed to match the non-rename stance.**

### HIGH-3 — SSRF + prompt cap + quoted-reply + PREVIEW → **FULLY RESOLVED**

| Sub-concern | Evidence | Status |
|---|---|---|
| **SSRF BaseUrlValidator** | 09-04 Task 1 (l.254–261): rejects RFC1918, loopback, link-local, CGNAT, multicast, IPv6 ULA, unspecified. DNS re-resolution at request time to defeat rebinding. TOCTOU guard: re-resolve before RestClient call, abort on IP change. `HostResolver` component for testability. `BaseUrlValidatorSsrfTest` with 8 hostile cases + 4 positive cases. | Resolved |
| **Aggregate prompt cap** | 09-05 `must_haves` (l.36): `MAX_AGGREGATE_PROMPT_CHARS=60_000`. Per-sample 4k cap, sample 50 cap, dropping-last-sample strategy after quoted-reply stripping. Structured log event. | Resolved |
| **Quoted-reply stripping** | 09-05 (l.37, l.178, l.185): 6 rules including `>`, `^On.*wrote:$`, `^From:`, `-----Original Message-----`, Outlook separator, and Vietnamese variants `Vào.*đã viết:`, `Người gửi:`. 8-fixture `QuotedReplyStripperTest`. | Resolved |
| **PREVIEW STEP 0** | 09-05 Task 2 (l.271–275): mandatory pre-coding verification of `CallSite.PREVIEW` semantics. Three outcomes documented (a/b/c); if (c) → STOP + fix. Documented as `summary_requirement` (l.291–297). | Resolved |
| **3-sentinel leak test** | 09-05 (l.254–267): body sentinel, quoted-reply sentinel, completion sentinel. Asserts absence in DB rows, logs, audit. ArgumentCaptor on mocked ChatModel. Logback `ListAppender` capture. | Resolved |
| **Depends_on correct** | 09-05 `depends_on: [09-01, 09-04]` (l.7) — `ByokRateLimiter` from 09-04 Task 1 is used. | Resolved |

### HIGH-4 — BYOK Test/Save state machine → **FULLY RESOLVED**

**Cited evidence:**
- **Locked lifecycle** (09-04 l.55, 316–319): `Save → Test stored row → Pick model → Activate`. No other path. Documented as hard contract.
- **No inline-pre-save Test** (09-04 l.316–317, 389–390, 398, 425): `POST /api/byok/test-connection` has EMPTY body (no request DTO, no `ByokTestConnectionRequest`). Handler signature: `public ResponseEntity<ByokTestConnectionResponse> testConnection()`. Caller MUST Save first. 404 `code=ai.byok.no_row` when no stored row.
- **Save resets ALL gating state** (09-04 l.55, 318–319): `active=false, last_test_result=NULL, last_tested_at=NULL, last_test_models_json=NULL, model_id=NULL`. Including `model_id` — new key may not support previously-picked model.
- **`last_test_models_json` persistence** (09-04 l.58, 319): successful Test persists the returned model list (JSONB, capped 100). Server-side membership check on `setModel`.
- **`model_not_in_last_test` guard** (09-04 l.319): `PUT /api/byok/model` rejects with 400 `code=ai.byok.model_not_in_last_test` when model ID not in persisted list.
- **Activation gate tests** (09-04 l.38–39, 404–405): `ByokActivateGateModelMissingTest` (modelId=NULL → 400), `ByokActivateGateNotTestedTest` (lastTestResult=NULL → 400).
- **FE state machine matches** (09-06 l.330–346): AiProviderSection has no Test-before-Save capability; Test button disabled until saved row exists.

**Lifecycle is fully locked and the cycle-1 contradiction eliminated.**

### HIGH-5 — SET-SAFE-04 frontend gap → **FULLY RESOLVED** (in plans)

**Cited evidence:**
- **09-06 Task 5** (l.429–476): NEW task `"SET-SAFE-04 audit-log badge + delete legacy ByokController shim"`. Files: `AuditSafetyNetBadge.tsx` (new component), `AuditRow.tsx` edit, `AuditCardList.tsx` edit, `AuditTable.tsx` edit, `AuditSafetyNetBadge.test.tsx` (new Vitest), `vi.json`/`en.json` i18n keys.
- **Badge behavior** (09-06 l.450–454): renders `<Badge variant="destructive">` only when `pattern` is non-null; i18n key `audit.badge.blockedBySafetyNet` with interpolation.
- **Unit test** (09-06 l.456–459): 3 cases — null → nothing, empty → nothing, `@evilcorp.com` → renders destructive badge with interpolated text in both locales.
- **Playwright step 14** (09-07 l.155–156, 167): navigates to triage audit log, seeds row with non-null `blockedBySafetyNetPattern`, asserts badge renders with localized text, asserts badge hides on null rows.

The plan encodes the complete fix.

## 3. New concerns

### MEDIUM: 09-01 intra-plan contradiction — verification block expects renamed archive table (l.348) while body says "left intact" (l.78+)

**Path:** `09-01-PLAN.md`
**Detail:** The verification block says `"the renamed tenant_byok_credentials_archived_2026_05_26 archive table"`. This expects a DB object that, per the plan body's 6 definitive statements, does NOT exist. If an executor follows the verification literally, they will expect `mcp__postgres__list_objects` to show the renamed table, find it absent, and mark the acceptance criteria as failed.

**Fix:** Replace the verification-block line with: `"Legacy tenant_byok_credentials table remains intact (no rename, no archive, no drop per REVIEWS.md HIGH-2); `user_byok_key` table exists alongside it."`

### MEDIUM: Anthropic probe header verification architecture — controller test cannot verify HTTP headers

**Path:** `09-04-PLAN.md` Task 3, `ByokTestConnectionEnumOnlyTest` behavior (l.410–414)
**Detail:** The plan says the Anthropic Case A test "asserts the underlying HTTP probe used `X-Api-Key` + `anthropic-version: 2023-06-01` headers (verified by capturing the headers the stub received)." But `ByokTestConnectionEnumOnlyTest` is a `@WebMvcTest(UserByokController.class)` — a controller test. At that level, `ProviderConnectionTester` is mocked/stubbed. A stub captures what it *receives* from its caller, not what headers an HTTP client would send. The controller test can verify response shape, but CANNOT verify the actual RestClient header construction.

**Fix options:** Either add a dedicated `ProviderConnectionTesterAnthropicHeaderTest` unit test in Task 1 that verifies the RestClient header construction, or clarify the plan text that header verification happens at integration level.

### LOW: 09-04 still large (acknowledged but not split)

The plan remains a single 34-file, 3-task execution unit. The commit-discipline mitigation (<execution_discipline> block) is reasonable and the tight-coupling argument is valid. This was HIGH in cycle 1; downgraded to LOW here because the risk is documented and mitigated.

## 4. Suggestions

### Must-fix (before execution):
1. **09-01-PLAN.md l.348** — Replace the stale `"renamed tenant_byok_credentials_archived_2026_05_26 archive table"` with language matching the "left intact" stance.

### Strongly recommended (before execution):
2. **09-04-PLAN.md** — Add a `ProviderConnectionTesterAnthropicHeaderTest` unit test in Task 1 (or clarify the verification approach).

### Nice-to-have:
3. **09-05-PLAN.md** — Add a note about `@Transactional(NOT_SUPPORTED)` behavior in the `VoiceGenerationFromSentLeakTest` to avoid test-async surprises.

## 5. Risk Assessment

| Risk Factor | Level | Rationale |
|---|---|---|
| Plan correctness | **LOW** | All 5 HIGH concerns resolved; plans are structurally consistent. One intra-plan contradiction (09-01 verification text) is a copy-paste error. |
| Execution risk (09-04 size) | **MEDIUM** | 34 files, 3 tightly-coupled tasks. Commit-discipline + context-window guard provide practical mitigation. |
| Privacy (SET-VOICE-07) | **LOW** | 3-mechanism Spring AI observation gate + 3-sentinel leak test + aggregate cap + quoted-reply stripping + PREVIEW STEP 0. |
| Anthropic probe header | **MEDIUM** | Plan says controller test verifies HTTP headers but doesn't design a test that can actually do that. Easy fix. |
| BYOK state-machine race | **LOW** | Locked lifecycle is unambiguous. No inline-pre-save path. Membership check prevents stale-model activation. |
| Tenant isolation | **LOW** | Opaque 404 on cross-tenant accesses, suffix-anchored DOMAIN matching, knowledge snippet UNIQUE constraint. |
| **Overall** | **MEDIUM** | Down from HIGH in cycle 1. No blocking design defects. |

**Proceed to execute-phase after applying the two suggested edits.** The phase is reviewable-ready.

---

## Consensus Summary

The two reviewers agree the replan substantially converges, but **disagree on whether HIGH-3, HIGH-4, and HIGH-5 are fully resolved**. Codex flags three residual HIGH gaps (SSRF transport hardening, missing `last_test_models_json` schema column in Wave 0, missing backend audit DTO update for `blockedBySafetyNetPattern`); OpenCode rates all five as fully resolved at the plan-text level but raises one MEDIUM intra-plan contradiction (stale archive-rename verification line in 09-01) and one MEDIUM test-architecture gap (controller test cannot verify HTTP headers).

### Agreed Strengths

- HIGH-1 test resequencing is correctly encoded across Task 1/2/3 with file-by-file evidence.
- HIGH-2 legacy table is unambiguously left intact in plan body; 410-Gone shim ships in 09-04 Task 2 and is deleted in 09-06 Task 5.
- HIGH-3 BaseUrlValidator SSRF coverage at validation time is comprehensive (RFC1918, loopback, link-local, CGNAT, multicast, IPv6 ULA, unspecified, disallowed ports, DNS re-resolution with TOCTOU guard).
- HIGH-4 BYOK state machine is locked: `POST /api/byok/test-connection` has empty body, Save resets all gating state, activation requires last_test=OK + model picked.
- HIGH-5 frontend pieces (AuditSafetyNetBadge component + AuditRow/AuditCardList edits + Vitest + Playwright step 14) are completely specified in 09-06 Task 5 and 09-07.
- SET-VOICE-07 privacy posture (QuotedReplyStripper, 60k aggregate cap, 3-sentinel leak test, PREVIEW STEP 0) is the most rigorously specified privacy surface in the project.

### Agreed Concerns

- **MEDIUM**: 09-01 verification block contains stale `tenant_byok_credentials_archived_2026_05_26` archive-rename language that contradicts the corrected "left intact" stance in the plan body. One-line fix but execution-critical.
- **MEDIUM**: 09-07 Playwright `page.waitForResponse(/api\/safety-net-senders/)` regex (Codex) and Anthropic header verification at the controller-test level (OpenCode) both indicate test-spec details that need clarification.
- **LOW**: 09-04 remains large at 34 files / 3 tasks; commit-discipline mitigation is reasonable but executor must actually follow it.

### Divergent Views (the call for cycle 3)

Three HIGHs are rated differently and the reviewer disagreement IS the unresolved-HIGH count:

- **HIGH-3 (SSRF transport)**: Codex says PARTIAL — validation-time hardening is strong, but the outbound RestClient may re-resolve DNS or follow redirects to a private IP at request time. OpenCode reads the same plan text as fully resolved because re-resolution + TOCTOU guard is present in the BaseUrlValidator design. **Verdict: PARTIALLY RESOLVED.** Codex's concern about redirect handling and IP pinning at the HTTP transport layer is concrete and not contradicted by OpenCode's reading; the plan does not explicitly say "disable redirects" or "pin the IP at the socket layer."

- **HIGH-4 (last_test_models_json schema)**: Codex says PARTIAL — the column is referenced in 09-04 service logic but not added to the Liquibase changeset 097 or `UserByokKeyEntity` in 09-01. OpenCode quotes 09-04 line 319 as evidence of resolution but does not verify the schema artifact in 09-01. **Verdict: PARTIALLY RESOLVED.** Spot-check confirms 09-01 changeset 097 in its current form does not declare a `last_test_models_json` column; without it, the Wave 1 service implementation cannot persist what it claims to persist.

- **HIGH-5 (audit DTO/projection backend gap)**: Codex says PARTIAL — frontend pieces are complete but no backend plan updates the triage audit response DTO/projection to actually expose `blockedBySafetyNetPattern` over the wire. OpenCode treats the frontend completeness as full resolution. **Verdict: PARTIALLY RESOLVED.** If 09-03 only wires entity/worker and no DTO/controller change is planned, the frontend badge will receive `null` from the API regardless of DB state.

### Unresolved HIGH Concerns Carried Forward

1. **HIGH-3 (carry-forward)**: SSRF transport-layer enforcement is missing — outbound HTTP client may re-resolve DNS or follow redirects to private IPs.
2. **HIGH-4 (carry-forward)**: `last_test_models_json` column missing from 09-01 changeset 097 and `UserByokKeyEntity`; 09-04 service depends on it.
3. **HIGH-5 (carry-forward)**: Triage audit response DTO/projection is not updated in any plan to expose `blockedBySafetyNetPattern`; frontend badge will not receive the field.

HIGH-1 and HIGH-2 are fully resolved across both reviews and drop out of the count.

### Recommendation

Run a tightly scoped third replan covering only the three residual HIGHs above plus the MEDIUM 09-01 verification-text fix and the Playwright endpoint regex fix. No new design work — just plan-text edits to add the missing schema column, add an SSRF transport hardening note (disable redirects + pin IP / use validated InetAddress), add an audit-DTO update task to 09-03 or 09-06, and clean stale archive-table references in 09-01. Once those land, the phase is ready for execute-phase.
