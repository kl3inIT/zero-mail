---
phase: 9
reviewers: [codex, opencode]
reviewed_at: 2026-05-26T12:41:46Z
plans_reviewed:
  - 09-01-PLAN.md
  - 09-02-PLAN.md
  - 09-03-PLAN.md
  - 09-04-PLAN.md
  - 09-05-PLAN.md
  - 09-06-PLAN.md
  - 09-07-PLAN.md
---

# Cross-AI Plan Review — Phase 9

## Codex Review

## Summary

The Phase 9 plans are unusually thorough: they decompose the work into sensible backend, frontend, privacy, BYOK, and verification waves; they preserve the key product decisions from discussion; and they put serious effort into tests and architecture gates. The main issue is not lack of rigor, but several execution-order contradictions and a few security/privacy gaps that would likely break CI or leave acceptance criteria incomplete unless fixed before implementation. I’d treat the plan set as strong but not yet execution-ready.

## Strengths

- The `/ai` pivot is consistently represented in most implementation plans: flat `SectionHeader` sections, `SettingCard` + `Dialog`, Knowledge table, and one BYOK card.
- SET-VOICE-07 gets the right level of scrutiny. `09-05-PLAN.md` includes sentinel-leak tests, no prompt/completion persistence, Spring AI observation hardening, rate limits, and explicit “user must Save” behavior.
- BYOK requirements are mostly well-modeled: encrypted at rest, no plaintext echo, provider allow-list, model + successful test gate before activation, tenant-wide fallback to curated catalog.
- The architecture tests are a good fit for this project’s risk model: sanitizer call-site confinement, knowledge write-site confinement, provider test single-binding, and BYOK package confinement.
- Frontend conventions are respected in `09-06-PLAN.md`: regenerate OpenAPI, typed client usage, shadcn primitives, TanStack Query meta-driven toasts, no hand-written DTO mirrors.
- The manual checkpoint in `09-07-PLAN.md` is appropriate because live Gmail and live provider BYOK cannot be fully proven in normal CI without unsafe credentials.

## Concerns

- **HIGH — `09-01-PLAN.md`, Task 1 / changeset 097:** renaming `tenant_byok_credentials` in Wave 0 can break existing code before `09-04` removes/replaces the legacy resolver, entity, service, and controller. Since `09-02`, `09-03`, and `09-05` can run after `09-01` but before `09-04`, the app may boot with JPA mappings or runtime code still expecting the old table.

- **HIGH — `09-05-PLAN.md`, dependencies:** `09-05` uses `ByokRateLimiter` “built in plan 09-04 Task 1” but declares only `depends_on: 09-01`. That is a hard compile-time dependency mismatch.

- **HIGH — `09-04-PLAN.md`, Task 1:** the verification plan references `UserByokService` and `UserByokController` before they exist. `ProviderConnectionTesterSingleBindingTest` expects `UserByokService` as an allowed caller in Task 1, while that class lands in Task 2. `UserByokTestConnectionSentinelLeakTest` discusses controller serialization, but `UserByokController` lands in Task 3.

- **HIGH — `09-04-PLAN.md` + `09-06-PLAN.md`, BYOK test/save state machine:** the user can test an inline unsaved key, get `models[]`, pick a model, then Save. But the backend rule says saving any field clears `last_test_result` and forces `active=false`; the frontend task also resets local test status on save. That means the successful inline test becomes unusable for activation unless the user tests again after saving. The UI order says `Test` before `Save`, but the activation rule effectively requires `Save` before `Test`.

- **HIGH — `09-04-PLAN.md`, BYOK URL validation:** `https://` plus localhost-dev allowance is not enough. User-controlled `baseUrl` is a server-side HTTP target, so this is an SSRF boundary. Private IPs, loopback aliases, link-local, internal DNS, and DNS rebinding need explicit handling.

- **HIGH — `09-06-PLAN.md`, SET-SAFE-04 frontend gap:** the plans add `blocked_by_safety_net_pattern` backend wiring in `09-03`, but `09-06` does not modify the triage audit UI components (`AuditRow.tsx`, `AuditCardList.tsx`, or equivalent). The requirement says the user sees a visual indicator in the audit log; the current frontend file list appears to omit that acceptance criterion.

- **MEDIUM — `09-02-PLAN.md` / controller tests:** several MVC tests are described as `@WebMvcTest` while also seeding tenants or asserting DB-backed tenant isolation. `@WebMvcTest` is controller-focused and normally uses mocked collaborators; tenant-isolation persistence checks should be service or `@SpringBootTest`/API integration tests. For Boot 4, mocks should use `@MockitoBean`, and MockMvcTester is available.

- **MEDIUM — Spring Security test coverage across `09-02`, `09-03`, `09-04`:** POST/PUT/DELETE controller tests do not explicitly mention authenticated sessions/authorities or CSRF. With Spring Security 7, secured mutating endpoints will commonly fail with 403 unless tests add the right user and CSRF token, or they may disable filters and accidentally miss security regressions.

- **MEDIUM — `09-04-PLAN.md`, model validation:** `UserByokService.setModel` says it requires the selected model to be in the list returned by the last test, but the schema only stores `last_test_result` and `last_tested_at`, not the model list. Either persist/cache the last OK model IDs with a TTL, re-test on model selection, or remove that server-side membership claim.

- **MEDIUM — `09-05-PLAN.md`, generate-from-sent prompt size/privacy:** each sample can be 4000 chars and `sampleSize` can be 50, so the prompt can reach roughly 200k chars before template overhead. Also, sent emails often include quoted inbound content. Add aggregate prompt caps and quoted-reply stripping, otherwise cost and privacy exposure are larger than intended.

- **MEDIUM — `09-01-PLAN.md`, Liquibase 095:** adding `UNIQUE(tenant_id, title)` to existing knowledge snippets may fail if duplicates already exist. The changeset should either preflight with `sqlCheck`, deduplicate deterministically, or fail with a clear migration message.

- **LOW — stale/spec inconsistencies:** some text still references removed endpoints or shapes: `GET/PUT /api/settings/ai`, `/ai?tab=provider`, “shadow mode” rather than “Pause triage”, `09-03` must-have says DELETE returns 200 while task behavior says 204, and `09-06` uses `apps/web/messages/*.json` while earlier context mentions `apps/web/i18n/messages`. These are small individually but can mislead executors.

## Suggestions

- Move the legacy BYOK table rename out of `09-01`. Safer options: leave `tenant_byok_credentials` intact until `09-04` removes legacy code, or create a compatibility view while both paths may coexist.

- Add `09-04` as a dependency for `09-05`, or extract the reusable rate limiter into `09-01`/a tiny shared plan before both BYOK and voice generation use it.

- Fix the BYOK lifecycle contract before coding. Recommended flow: Save key first, then Test stored row, then Pick model, then Activate. If inline pre-save testing stays, persist `last_test_result=OK` only when the saved payload exactly matches the tested payload.

- Add SSRF defenses to `BaseUrlValidator`: resolve host to IP, reject private/loopback/link-local/reserved ranges outside dev, handle DNS rebinding by resolving at request time, and restrict nonstandard ports if not explicitly allowed.

- Add the missing audit-log UI work to `09-06`: include the audit row/list component files, schema field rendering, localized badge copy, and a frontend test or Playwright assertion for `blocked_by_safety_net_pattern`.

- Adjust MVC tests to match the right slice. Use `@WebMvcTest` with `@MockitoBean` for controller mapping/validation/error-shape tests; use `@SpringBootTest` or service-level Testcontainers tests for tenant isolation and DB persistence. For `@DataJpaTest`, use `TestEntityManager` and `flush()`/`clear()` before read assertions.

- Make security setup explicit in controller tests: authenticated user/session, tenant context, authorities where relevant, and CSRF on POST/PUT/DELETE unless the app’s API security config intentionally disables CSRF for those endpoints.

- For SET-VOICE-07, strip quoted replies, cap aggregate sample text, and log only counts/metadata. Keep the existing sentinel tests, but add a case where quoted inbound content contains a sentinel to prove it is excluded or at least not persisted.

- Update the Spring AI observation plan to use the current property names discovered during execution. Current Spring AI 2.0 snapshot docs say `include-prompt` / `include-completion` were renamed to `log-prompt` / `log-completion`, with chat-client properties such as `spring.ai.chat.client.observations.log-prompt` and `spring.ai.chat.client.observations.log-completion`.

- Add Liquibase preconditions to 094..097: `columnExists`, `tableExists`, `uniqueConstraintExists`, duplicate-title checks, and clear rollback behavior. Avoid relying on assumptions about existing changelog 046 without a precondition.

## Risk Assessment

**Overall risk: HIGH as written.** The functional design is solid, but the current plan set has multiple execution blockers: the legacy BYOK table rename happens too early, `09-05` depends on `09-04` without declaring it, `09-04` tests reference classes before their tasks create them, and the audit-log UI acceptance criterion is missing from the frontend plan. The BYOK SSRF gap and the generate-from-sent aggregate/quoted-content gap are also real security/privacy issues. After fixing those, I’d downgrade the implementation risk to **MEDIUM** because the remaining plan structure, tests, and manual gates are strong.

Docs checked via Context7: Spring AI observability/upgrade notes, Spring Boot 4 testing docs, Spring Security 7 CSRF testing docs, and Liquibase YAML/preconditions docs.

---

## OpenCode Review

## Phase 9 Plan Review

### Summary

The Phase 9 plans are **comprehensive and well-structured**, with strong architectural discipline, clear dependency ordering across 4 waves, thorough threat modeling per plan, and robust privacy safeguards. The modular decomposition into 7 coordinated plans across waves 0→3 shows careful attention to dependency ordering. The Inbox Zero pattern alignment (SettingCard→Dialog, flat sections, enum-based confidence) is well-reasoned and consistently applied across backend + frontend plans. However, **plan 09-04 is dangerously large** (34 files, 3 tightly-coupled tasks) and risks execution quality degradation even with the commit-discipline mitigation, the `CallSite.PREVIEW` audit behavior is a **HIGH-risk unknown** that the sentinel-leak test must resolve before privacy sign-off, and there's a **cross-plan deletion gap** for the legacy `ByokController` that will cause compile failures if not coordinated.

---

### Strengths

- **Privacy-first architecture**: SET-VOICE-07 (09-05) uses three-mechanism Spring AI observation verification (POJO + Environment.containsProperty + observation snapshot), explicit `NOT_SUPPORTED` propagation, and a sentinel-seed leak test proving bodies/prompts/completions are never persisted. This is the strongest privacy gate in the project.

- **TWO distinct knowledge ArchUnit invariants** (09-02 Task 2): Separating the repository-write-site rule (`KnowledgeSnippetSingleWriteSiteTest`) from the append-callers rule (`AssistantKnowledgeAppendCallSiteTest`) enforces the invariant at both layers independently — minor diff in a PR won't silently break the call-chain gate.

- **DOMAIN suffix matching is anchored** (09-03 Task 2): The suffix check explicitly uses `senderEmail.endsWith(row.value)` where `row.value.startsWith("@")` is enforced, with a negative test for the substring trap (`acme.com@evil.com` must NOT match `@acme.com`). Most implementations get this wrong.

- **Per-provider HTTP header strategy** (09-04 Task 1): The Anthropic `x-api-key` + `anthropic-version: 2023-06-01` path is explicitly documented and independently verified against live Anthropic API docs. The 12-parameterized `ByokTestConnectionEnumOnlyTest` covering all 4 providers × 3 cases proves normalized output irrespective of provider.

- **Tenant isolation is opaque** (multiple plans): Cross-tenant 404 (never 403), domain-safety-net suffix check, knowledge-snippet access — all use opaque 404 to avoid existence leaks.

- **Wave ordering is correct**: Wave 0 (Liquibase + entities + stubs) before Wave 1 (services) before Wave 2 (FE) before Wave 3 (e2e + ArchUnit aggregate). No circular dependencies. Both FE-only plans (09-06) and e2e-only plan (09-07) correctly depend on all Wave-1 backend plans.

- **Legacy table archive-rename** (09-01 Task 1): The `tenant_byok_credentials_archived_2026_05_26` rename (not DROP) with documented deferred-DROP rationale is correct for solo-operator safety.

---

### Concerns

1. **HIGH: `CallSite.PREVIEW` audit behavior is an unknown** (09-05 Task 2, `<read_first>`). The plan says to "verify PREVIEW CallSite path — assume PREVIEW skips prompt/completion columns" but if PREVIEW actually writes `prompt`/`completion` columns containing the LEAK_SENTINEL completion from the mocked ChatModel, the sentinel-leak test will fail. The test adapts to both behaviors (`"or PREVIEW row contains no prompt/completion columns"`), but if PREVIEW writes these columns in a non-nullable form (improbable but not checked at planning time), the privacy invariant breaks. **Mitigation:** Verify PREVIEW's behavior as the very first step of 09-05 execution, before writing any other code. If PREVIEW writes prompt/completion, pause execution and fix the PREVIEW path before proceeding.

2. **HIGH: Plan 09-04 is too large for reliable execution** (~34 files, 3 tightly-coupled tasks). The plan acknowledges this with commit-discipline boundaries, but the tight coupling means a mistake in Task 1 (ProviderConnectionTester signature) cascades through Tasks 2 (UserByokService) and 3 (controllers). The topple point is the `ConnectionTestResult` record shape — if it changes between Task 1 and Task 2 (e.g. adding a field), the earlier commit requires amending. **Mitigation:** Consider splitting into 09-04a (extraction + admin refactor + helpers) and 09-04b (service + resolver + controller + cost). The natural boundary is after the `ProviderConnectionTester` extraction — that's what Plan 08.1 should have done with the outbound gateway.

3. **HIGH: Legacy `ByokController.java` deletion ownership is ambiguous.** 09-04 Task 2 says "Delete the legacy `TenantByokProviderCredentialResolver` class + legacy `TenantByokCredentialsEntity` / `ByokService` / `apps/web/features/llm/components/ByokForm.tsx`" — but `ByokController` is a `@RestController` at `backend/api/.../controllers/llm/ByokController.java` that still has `@RequestMapping("/api/llm/byok")` routes. If Plan 09-04 deletes the controller without Plan 09-06 having created the new endpoints yet (09-06 correctly depends on all 09-02 through 09-05), the `backend/api` subproject will fail to compile during the interval between 09-04 completion and 09-06 execution. **Fix:** Either (a) delete `ByokController` in 09-04 Task 2 and add a note that `backend/api` won't compile until 09-06 lands (acceptable if Wave-2 plans know this), or (b) keep `ByokController` as a dead-end shim that returns 410 Gone redirecting to `/api/byok/*` and delete it in 09-06 alongside the FE removal.

4. **MEDIUM: Modulith cross-module dependency for `SensitiveDataRedactor`** (09-02 Task 3). The plan injects `AssistantSettingsJpaRepository` into `SensitiveDataRedactor` (which lives in `core.llm.redaction`). If the Modulith validation (`NamedInterface` verification) is enforced between `core.llm` and `core.chat`, this injection will fail at startup. The plan references D-13 which says safety net stays in `core.triage` but doesn't address this cross. **Fix:** Add an explicit Modulith `uses` declaration in `core.llm::redaction` → `core.chat::persistence`, or add the `sensitive_data_protection` check as a parameter passed from the triage pipeline (caller reads both settings), not injected into the redactor itself.

5. **MEDIUM: ArchUnit `PersonalizationSanitizer` single-call-site test is fragile** (09-02 Task 1). Asserting "exactly TWO callers" will break when a third caller is added in v1.3 (e.g. a new chat tool or template pipeline). A better pattern is a 5-line allow-list with a future-maintainer override mechanism. Same concern for the `AssistantKnowledgeService.append` ArchUnit rule. **Suggestion:** Use `should().onlyBeCalledFromClassesThat().haveSimpleName("AssistantPersonalInstructionsService").or().haveSimpleName("SettingsVoiceService")` with a `@SuppressArchTest` escape hatch documented in the test, rather than asserting exact cardinality.

6. **MEDIUM: Anthropic probe path base URL may be wrong** (09-04 Task 1). The canonical base URL for Anthropic is listed as `https://api.anthropic.com/v1`. The GET /v1/models call would hit `https://api.anthropic.com/v1/v1/models` — note the double `/v1/`. The correct Anthropic base URL for the /v1/models endpoint is `https://api.anthropic.com` (root), not `https://api.anthropic.com/v1`. **Check:** Verify that the `baseUrl` stored in the BYOK row is appended with `/models`, not `/v1/models`, when the base URL already includes `/v1`.

7. **MEDIUM: Cost endpoint fragility** (09-04 Task 3). The `GET /api/settings/ai/cost?window=7d` uses a raw `SUM` over `llm_call_audit`. If this table is large (tens of thousands of rows per tenant), a full scan even with the index on `(tenant_id, created_at)` can be slow. For a single-VPS deployment this is likely fine at v1.2 scale, but there's no pagination, no caching, and no query timeout documented. **Suggestion:** Add a `@Cacheable` with a 5-minute TTL and a statement timeout annotation (or JDBC query timeout) in `AiCostQueryService` to prevent a long-running cost query from blocking a worker thread.

8. **LOW: Plaintext key `Arrays.fill` after handoff is insufficient** (09-04 Task 2). The plan says `Arrays.fill(plaintextKey, (byte) 0)` after handoff to `SpringAiChatModelFactory`. However, the JVM may move the byte array in memory (GC) and leave copies in the old generation. For single-VPS deployment this is acceptable threat modeling (T-09-04-11 disposition = "mitigate" already), but the plan should note this is a defence-in-depth measure rather than a cryptographic guarantee.

9. **LOW: 09-06 doesn't list `pnpm dlx shadcn add` for any missing primitives.** The plan asserts all primitives are already installed (confirmed by research), but if the runtime environment has a different version of `components/ui/`, one missing import will break the entire FE build. **Suggestion:** Add a `pnpm dlx shadcn@latest add dialog card switch select table badge tooltip separator` as a safety net step in 09-06 Task 2 before starting FE component work — `shadcn add` is idempotent and won't re-install existing components.

10. **LOW: The DOMAIN encoding verification in Playwright** (09-07 Task 1, step 7) checks the response JSON and DOM text for literal `@evilcorp.com`. But the existing `SenderSafetyNetList` component may URL-decode the GET response internally (e.g. via `decodeURIComponent`). If so, the test passes even with double-encoding on the wire. The network-response assertion (`page.waitForResponse`) is the stronger check — the DOM-text assertion is secondary. This is correctly designed, just note that the network-response assertion is the authoritative one.

---

### Suggestions

1. **Add a `ByokController` shim** (reference concern #3): In 09-04 Task 2, instead of deleting `ByokController`, add a `@Deprecated` shim that returns HTTP 410 Gone with a `Location: /api/byok` header and a structured body `{code: "ai.byok.moved", message: "Use /api/byok instead"}`. Delete in 09-06 alongside the FE ByokForm. This prevents a compile gap between 09-04 and 09-06.

2. **Pre-verify `CallSite.PREVIEW`** first (reference concern #1): Before Task 2 in 09-05, run a one-line grep for the PREVIEW audit insertion to settle option-(a) vs option-(b). Document the result in the 09-05 sentinel-leak test's `@BeforeEach` as a comment so future maintainers know the assumption.

3. **Make ArchUnit allow-lists additive, not cardinal** (reference concern #5): Change the single-call-site assertions to use `should().onlyBeCalledFromClassesThat().haveSimpleName("approved_name_1").or().haveSimpleName("approved_name_2")` with a Javadoc `@implNote` listing the allowed callers and the policy for adding more. This survives v1.3 additions without test changes.

4. **Avoid double `/v1` in Anthropic base URL** (reference concern #6): In `AiProviderSection.tsx` (09-06 Task 4) and `BaseUrlValidator` (09-04 Task 1), normalize the base URL so that if a user enters `https://api.anthropic.com/v1` and the probe path appends `/models`, it doesn't produce `https://api.anthropic.com/v1/v1/models`. Either strip trailing `/v1` from the auto-filled URL, or build the probe URL differently.

5. **Add a cost query timeout** (reference concern #7): Add `@Transactional(timeout = 5)` (or JDBC `setQueryTimeout`) to `AiCostQueryService.totalUsdLast7Days` to protect against accidental full-table scans on a busy tenant.

---

### Risk Assessment

**Overall risk: MEDIUM**

**Justification:** The plans are thorough but face three execution risks:

| Risk Factor | Level | Explanation |
|---|---|---|
| Privacy guarantee (SET-VOICE-07) | MEDIUM | `CallSite.PREVIEW` audit behavior is unknown until execution; if it writes prompt/completion columns unexpectly, the sentinel-leak test catches it but execution delays. The three-mechanism observation gate (A/B/C) correctly addresses the silent-key-bind regression from WARNING #6. |
| Plan 09-04 size | HIGH | 34 files, 3 tightly-coupled tasks. Even with commit discipline, a mid-execution discovery (e.g., `ConnectionTestResult` needs a field that cascades through all 3 tasks) requires amending an already-committed Task 1. Split recommendation. |
| Legacy controller deletion | MEDIUM | Ambiguous ownership of `ByokController` deletion creates a compilation gap between 09-04 and 09-06. Easy fix with the 410-Gone shim. |
| Anthropic probe base URL | LOW | Possible double `/v1` in the probe URL path. Easy to verify and fix in execution. |
| Cross-module dependency | LOW | `SensitiveDataRedactor` → `AssistantSettingsJpaRepository` may violate Modulith boundaries. Easy to fix by injecting the flag through the service layer instead. |
| Test coverage | LOW | 34 stubs across all layers, ArchUnit aggregate with 5-6 rules, Playwright golden path. Manual checkpoint for live BYOK and SET-VOICE-07. Coverage is appropriate for the risk profile. |

**The plans are reviewable-ready after one structural fix** (splitting 09-04 into 09-04a/09-04b or confirming the commit-discipline plan is acceptable to the executor). The privacy, security, and architectural invariants are well-specified and testable.

---

## Consensus Summary

Two independent AI reviewers (Codex via GPT-class, OpenCode via deepseek-v4-flash-free) converge on the assessment that Phase 9 plans are architecturally sound and privacy-conscious but contain several execution-order and security-boundary defects that should be addressed before code execution begins.

### Agreed Strengths

- **Strong privacy posture for SET-VOICE-07** — sentinel-leak tests, in-memory-only enforcement, Spring AI observation hardening, and explicit no-persistence semantics for prompts/completions/raw email bodies (both reviewers).
- **Wave/dependency decomposition is clear** — Wave 0 (Liquibase + entities) → Wave 1 (services) → Wave 2 (FE) → Wave 3 (e2e + ArchUnit aggregate); no circular dependencies (both reviewers).
- **BYOK security baseline** — AES-GCM encryption via `RefreshTokenCipher`, no plaintext echo, masked display, model-pick-AND-test gate before activation (both reviewers).
- **ArchUnit invariants** — sanitizer call-site confinement, knowledge write-site confinement, BYOK package confinement, single-binding for `ProviderConnectionTester` (both reviewers).
- **Frontend conventions respected** — regenerated OpenAPI types, typed openapi-fetch client, shadcn primitives, TanStack Query meta-driven toasts, no hand-written DTO mirrors (Codex).
- **Inbox Zero pattern fidelity** — flat `SectionHeader` groups, `SettingCard` + `Dialog`, enum-based confidence, Knowledge table (both reviewers, OpenCode emphasis).
- **Manual checkpoint in 09-07** — appropriate given live Gmail + live BYOK provider can't be safely automated in CI (Codex).

### Agreed Concerns (HIGH — both reviewers raised related issues)

1. **HIGH — Plan 09-04 execution risk.**
   - Codex: 09-04 Task 1 verification references `UserByokService`/`UserByokController` before they exist; Task 2/3 land them.
   - OpenCode: 34 files, 3 tightly-coupled tasks; a `ConnectionTestResult` shape change cascades and forces commit amends.
   - **Joint recommendation:** split 09-04 into 09-04a (extraction + helpers) and 09-04b (service + resolver + controller + cost), OR explicitly resequence tests so they verify classes that already exist within the same task.

2. **HIGH — Legacy BYOK / `ByokController` cross-plan compilation gap.**
   - Codex: Wave-0 rename of `tenant_byok_credentials` in 09-01 lands before 09-04 removes the legacy resolver/entity/service; intervening plans may boot with stale JPA mappings.
   - OpenCode: legacy `ByokController` ownership for deletion is ambiguous; deleting it in 09-04 before 09-06 lands new endpoints will break `backend/api` compile.
   - **Joint recommendation:** defer the legacy rename out of 09-01 OR keep `ByokController` as a `@Deprecated` 410-Gone shim until 09-06 removes it alongside `ByokForm.tsx`.

3. **HIGH — Privacy/security gaps in BYOK + SET-VOICE-07.**
   - Codex: SSRF boundary on user-supplied `baseUrl` (private IPs, loopback aliases, link-local, DNS rebinding); SET-VOICE-07 aggregate prompt size (potential ~200k chars) + quoted-reply inclusion not stripped.
   - OpenCode: `CallSite.PREVIEW` may write `prompt`/`completion` columns from the mocked ChatModel; the sentinel-leak assertion is adaptive but the actual PREVIEW behavior must be verified first.
   - **Joint recommendation:** harden `BaseUrlValidator` with private-CIDR rejection + per-request DNS resolution; add aggregate prompt cap + quoted-reply stripping to 09-05; pre-verify `CallSite.PREVIEW` audit behavior as the first execution step of 09-05.

4. **HIGH — BYOK test/save state machine contradiction (Codex only, but high impact).**
   - Inline pre-save Test returns `models[]`, but Save resets `last_test_result` and forces `active=false`, making the just-completed test unusable for activation.
   - **Recommendation:** lock the contract — either Save→Test→Pick→Activate, or persist `last_test_result=OK` only when the saved payload exactly matches the tested payload.

5. **HIGH — SET-SAFE-04 frontend gap (Codex only).**
   - Backend wires `blocked_by_safety_net_pattern` in 09-03 but 09-06 has no triage audit UI changes to render it. Acceptance criterion will fail.
   - **Recommendation:** add audit row/list component edits + localized badge copy + Playwright assertion to 09-06.

### Agreed Concerns (MEDIUM — same theme)

- **Spring Security 7 / Spring Boot 4 test slice rigor** — Codex flags `@WebMvcTest` mixed with DB-backed tenant-isolation assertions; CSRF + authenticated session setup missing on POST/PUT/DELETE. OpenCode does not raise this explicitly but neither contradicts.
- **Spring AI observation property names** — Codex notes Spring AI 2.0 snapshot renamed `include-prompt`/`include-completion` → `log-prompt`/`log-completion` (chat-client scope).
- **Liquibase 095 `UNIQUE(tenant_id, title)`** — duplicate-title rows in existing data could fail the migration; add `sqlCheck` preflight or deterministic dedup (Codex).
- **Cost endpoint fragility** — `SUM(amount_usd)` over `llm_call_audit` without query timeout or cache risks blocking a worker thread (OpenCode).
- **Modulith cross-module injection** — `SensitiveDataRedactor` injecting `AssistantSettingsJpaRepository` may violate `core.llm` → `core.chat` boundary (OpenCode).
- **ArchUnit cardinality assertions** — exact-N caller counts are brittle; prefer name-based allow-lists (OpenCode).
- **Anthropic probe base URL** — possible double `/v1/v1/models` if auto-filled URL already ends in `/v1` (OpenCode).
- **`UserByokService.setModel` membership check** — server can't validate the picked model is in the last test result because the test response isn't persisted (Codex).

### Divergent Views

- **Overall risk rating.** Codex calls the plan-set risk **HIGH as written** (multiple execution blockers); OpenCode calls it **MEDIUM** (concedes 09-04 size is HIGH but treats the others as easy execution-time fixes).
  - **Reconciliation:** the unresolved HIGH defects are similar in both reviews; the rating difference is mostly tone. Treat overall as **HIGH-while-unresolved**, **MEDIUM after the joint recommendations above land**.
- **Whether `ByokController` should be deleted in 09-04 or shimmed.** OpenCode strongly prefers the 410-Gone shim; Codex prefers leaving the legacy table intact in 09-01 (a different layer of the same gap).
  - **Reconciliation:** the two suggestions are complementary, not in conflict — both layers can be applied together (defer the table rename AND keep `ByokController` as a shim) for maximum safety.
- **Defense-in-depth on plaintext key zeroization (LOW).** OpenCode flags `Arrays.fill` as insufficient against GC moves; Codex did not raise it.
  - **Reconciliation:** keep as documentation-only note; threat model disposition is already `mitigate`.
