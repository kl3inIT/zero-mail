# Review Findings — Quản lý LLM page (260522-37g)

> Defer list for items NOT applied during the fix-now sweep. Fixes that landed in
> `refactor(quick-37g)` are not repeated here — see the commit body for the applied set.

---

## Finding 01: `ModelsProbeClientTest` pre-existing two-arg constructor call (auto-fixed during Task 2)

**File:** `backend/core/src/test/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClientTest.java:29,53`
**Severity:** fix (auto-applied — Rule 3 blocking issue)
**Observation:** `ModelsProbeClient`'s constructor signature is `(RestClient.Builder, RestClient.Builder, ObjectMapper)` (two `Builder` parameters — cleartext + default), but `ModelsProbeClientTest` still called the older two-arg variant. `./gradlew :backend:core:compileTestJava` failed with two `constructor cannot be applied to given types` errors, blocking Task 2's new tests from running.
**Why deferred:** N/A — fix landed in the `test(quick-37g)` commit body because it was a Rule 3 blocking issue (couldn't run any new tests without compile-passing). Test now passes the same `restClientBuilder` for both args (HTTPS targets only — cleartext path not exercised).

## Finding 02: `RestClientConfig.restClientBuilder` missing `@Primary` annotation (auto-fixed during Task 2)

**File:** `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java:14-16`
**Severity:** fix (auto-applied — Rule 3 blocking issue)
**Observation:** Commit `7a6af6d7 fix(llm-probe): pick HTTP/1.1 builder for cleartext base URLs` added a second `RestClient.Builder` bean (`cleartextRestClientBuilder`) without marking either bean `@Primary`. Spring AI auto-configs (DeepSeek/OpenAI/etc.) autowire a bare `RestClient.Builder` and fail context startup with `NoUniqueBeanDefinitionException: expected single matching bean but found 2: restClientBuilder, cleartextRestClientBuilder`. This broke every `@SpringBootTest`-backed test in the project (including `OnboardingStepPersistenceTest`, the existing Phase 1.2.1 reference test).
**Why deferred:** N/A — fix landed in the `test(quick-37g)` commit body because it was a Rule 3 blocking issue (couldn't run either of the two ITs without context startup working). Added `@Primary` to the default builder so auto-configs resolve to it; the `cleartextRestClientBuilder` continues to require explicit `@Qualifier("cleartextRestClientBuilder")` from `ModelsProbeClient`. This is the minimum-risk fix — same behavior at runtime, restores context startup.

---

## Finding 03: `ProviderCatalogLookupRepository.isFeatureDefaultProvider` is now unused

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/lowlevel/ProviderCatalogLookupRepository.java:39-56`
**Severity:** note
**Observation:** The single-row variant `isFeatureDefaultProvider(LlmProvider, String)` has zero callers after this sweep removed its only caller (`ProviderMasterKeyResolver`'s private wrapper). The batch variant `findAllFeatureDefaultPairs()` is the canonical entry point now.
**Why deferred:** The repository helper is a public method on a `@Repository` bean — deleting it is a soft API break. The risk of a future ops tool wanting "is provider X the default for feature Y?" without paying the batch cost is real (e.g. a CLI smoke test). Leaving it parked with no callers carries near-zero maintenance cost (plain JDBC, no Hibernate involvement) and the class-level Javadoc already calls out the batch variant as the preferred entry point.
**Suggested fix:** Either (a) delete the method in a future sweep when we add a generic `@SuppressWarnings("unused")` policy check, or (b) keep indefinitely as a documented single-row lookup. No action this commit.

---

## Finding 04: `Phase B v2` shim methods on `MasterKeyAdminService` still in tree

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java` (`setFeatureDefault` always-throws shim plus `set/rotate` legacy single-key flow)
**Severity:** note
**Observation:** `setFeatureDefault` is a stub that always throws `UnsupportedOperationException` ("Legacy boolean-column feature-default flow removed in Phase B v2 …"). `set` and `rotate` still drive the priority=1 "canonical key" upsert path even though the v2 admin surface (`addKey` / `reorderKeys` / `revokeKey`) is the actively-used surface.
**Why deferred:** These methods are still wired to live API endpoints (see `/api/admin/master-keys/{provider}` PUT in the admin spec) that the legacy v1 admin UI used. Deleting them needs a controller-level removal pass that also touches `apps/admin` callers (`use-save-master-key.ts`, `use-rotate-master-key.ts`) and an OpenAPI regen. Outside this review's fix-now scope.
**Suggested fix:** Plan a small follow-up ("v1 master-keys endpoint sunset") that removes the controller endpoint, the service method, and the FE hook + regenerates `admin-schema.d.ts`. Track as a phase TODO once the v2 admin UI has been on prod for a sprint.

---

## Finding 05: `FeatureDefaultProviderService.set` silently drops `reason` arg

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/FeatureDefaultProviderService.java:40-49`
**Severity:** note
**Observation:** Both `set(...)` and `setProviderDefault(...)` accept a `reason` parameter but never pass it to `featureDefaultTierService.assign(...)` — which itself does not have a `reason` parameter, so the value is silently dropped before audit. The audit row for `CATALOG_FEATURE_DEFAULT_SET` therefore loses the operator-supplied justification on this code path.
**Why deferred:** Adding a `reason` argument to `FeatureDefaultTierService.assign(...)` is a 4-callsite signature change (the service method, the v2 controller, the legacy `FeatureDefaultProviderService.set` shim, and the test). The reason already flows into a different audit path on the v2 endpoint (the `assign` call site at the controller fills it through the `before/after state` map), so the user-visible audit completeness is preserved on the v2 surface. The legacy shim is on its way out (see Finding 04), so plumbing reason through a shim about to be deleted is wasted work.
**Suggested fix:** Delete this shim alongside Finding 04's sunset. If the shim must stay, add a single-line audit append from `FeatureDefaultProviderService.set(...)` that records the legacy-shim reason before delegating.

---

## Finding 06: `MasterKeyAdminService.addKey` priority race window

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java:156-161`
**Severity:** note
**Observation:** `addKey` computes the next priority by reading the current max priority for the provider (`max + 1`) inside the transaction. Two concurrent admin `addKey` calls for the same provider race: both read priority N, both insert at N+1, and the deferrable `uq_priority` constraint catches one of them at commit time with a `ConstraintViolationException` that bubbles up as an opaque 500.
**Why deferred:** The exposure window is small — admin console has one admin actor most of the time, and the deferrable unique constraint at least guarantees consistency. A proper fix needs a serialized DB-side counter or an advisory lock, neither of which is in scope for a review sweep. The user-visible symptom is "rare 500 on rapid double-submit", not data corruption.
**Suggested fix:** Catch `DataIntegrityViolationException` in the controller error handler and translate to a domain `ConcurrentPriorityAssignmentException` with code `409 Conflict` + a "please retry" message. Plan as part of the next master-keys hardening phase. Or: switch to a `SELECT max(priority) ... FOR UPDATE` pattern inside the same transaction.

---

## Finding 07: `MasterKeyAdminService.storeMasterKey` legacy upsert path bypasses the new failover chain

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java:495-552`
**Severity:** note
**Observation:** `storeMasterKey` only knows about the priority=1 "canonical" key — it ignores any priority=2/3 rows in the failover chain. If an admin uses the legacy `set` endpoint on a provider that already has a multi-key chain, it overwrites the priority=1 row in place but leaves the other keys untouched. The v2 admin surface (which always goes through `addKey`/`reorderKeys`) doesn't trigger this — but the legacy v1 hooks in `apps/admin/src/features/master-keys/use-save-master-key.ts` do.
**Why deferred:** Structural — the legacy surface assumes a single key per provider and the v2 schema assumes a list. The "right" fix is to delete the legacy surface (Finding 04). Patching the upsert to "leave non-priority-1 keys alone" is already what the code does; the conceptual mismatch is the legacy contract, not the implementation. Documenting here so a future reader knows why the two paths coexist.
**Suggested fix:** Sunset the legacy surface (Finding 04).

---

## Finding 08: Pre-existing arch-test red — `MasterKeyResolverConfinementTest`

**File:** `backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeyResolverConfinementTest.java`
**Severity:** note
**Observation:** The full `:core:test` run reports `MasterKeyResolverConfinementTest` failing with 10 violations of the rule "only `ProviderMasterKeyResolver` may depend on `LlmProviderMasterKeyRepository`". Violators include `LlmRouter` and `MasterKeyAdminService`. The dependencies were already in place before commit `cb559749` — git history confirms `LlmRouter` has imported `LlmProviderMasterKeyRepository` since the file's first version. This is wave-scaffolding red, not regression.
**Why deferred:** The arch rule and the production code disagree about which class is "the single reader". Either the rule needs to expand its allow-list to include the router + admin service (likely correct — the resolver is the BYOK cache layer; router + admin service own the priority chain), or the production code needs to introduce a thin facade. Both options are larger than this sweep's scope.
**Suggested fix:** Open a follow-up ticket to either (a) widen the arch rule's allow-list, or (b) introduce a `MasterKeyPriorityRepository` port that exposes only `findActiveByProviderOrderByPriority` and the priority-shift writes, keeping `LlmProviderMasterKeyRepository` confined to the resolver. Until then, the test stays red and gates only this specific assertion.

---

## Finding 09: Pre-existing red — `DeadLetterRequeueServiceTest` (3 tests failing)

**File:** `backend/core/src/test/java/com/zeromail/core/admin/queue/DeadLetterRequeueServiceTest.java`
**Severity:** note
**Observation:** 3 tests in this file fail on the full `:core:test` run. Not investigated in this sweep — the failures are out of scope (queue subsystem, not Quản lý LLM).
**Why deferred:** Out of scope. Queue work belongs to phase 8E, not 8B/8D's master-keys + catalog work.
**Suggested fix:** Triage as part of the next queue-touching commit. Could be the same `RestClient.Builder` / Spring AI auto-config drift if these tests boot a Spring context.

---

## Finding 10: `AddProviderKeyDialog` / `EditProviderKeyDialog` not reviewed against `frontend-design` skill

**File:** `apps/admin/src/components/AddProviderKeyDialog.tsx`, `EditProviderKeyDialog.tsx`, `AddCatalogModelDialog.tsx`
**Severity:** note
**Observation:** This sweep is a code-review sweep, not a visual/UX review. The three dialog components were spot-checked for hex literals (none found), `as any` (none found), and banned abbreviations (none found) but were NOT walked through the Anthropic `frontend-design` skill checklist (visual hierarchy, focus management, error placement, density).
**Why deferred:** Out of scope for a backend-leaning review sweep. The dialogs work functionally per the Playwright session (the user's `master-keys-after-fixes.png` artifact shows a working flow). Visual polish belongs to a UI-pass phase.
**Suggested fix:** When v1.2 Phase 9 (User Settings UI) lands, do a shared pass over admin dialogs at the same time — both surfaces use the same shadcn primitives and the same visual debt would compound.

---

## Finding 11: `ProviderCatalogLookupRepository.findAllFeatureDefaultPairs` returns `Set<String>` instead of a typed structure

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/lowlevel/ProviderCatalogLookupRepository.java:64-79`
**Severity:** note
**Observation:** The method returns `Set<String>` of `"PROVIDER|FEATURE"` strings, decoded at the call site via the static `pairKey(...)` helper. This is mildly fragile — the separator (`|`) is a magic character, and a future enum addition (e.g. a feature literally named `OPENROUTER|DRAFT`) would silently collide.
**Why deferred:** The string separator is a non-issue in practice (`Feature` is a closed enum: CHAT/TRIAGE/DRAFT) and converting to `Set<Pair<LlmProvider, Feature>>` would introduce a `record FeatureDefaultPair(LlmProvider, Feature)` type used by exactly one caller. Premature abstraction for a 3-element enum.
**Suggested fix:** If a third caller appears, introduce the typed pair. Otherwise leave alone.

---

## Finding 12: `LlmRouter.resolveTier` does not reuse the parent `resolve` memoization

**File:** `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/LlmRouter.java` (`resolveTier`)
**Severity:** note
**Observation:** When ops tooling calls `resolveTier` for each tier separately (e.g. to render "test PRIMARY only" + "test FALLBACK only" buttons), each call instantiates a fresh `activeKeyByProvider` map and a fresh `modelsById` map. If both calls go to the same provider, the active-key lookup happens twice across the two calls.
**Why deferred:** `resolveTier` is an ops tooling path, not a hot path. The expected call rate is "one button click per tier in the matrix detail view" — three calls per page load at most. The memoization win across calls is not worth the architectural cost of caching across method invocations (would need a request-scoped bean or an explicit `LlmRouterSession` aggregate). Within a single `resolveTier` call the new memoization still gives the expected one-fetch-per-provider behavior.
**Suggested fix:** No action. Document as ops behavior in the controller-level API docs if anyone reports it.

---

## Finding 13: `MasterKeyAdminService.storeMasterKey` uses `findByProviderOrderByPriority(...).stream().filter(priority == 1)` instead of a direct query

**File:** `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java:514-518`
**Severity:** note
**Observation:** Loads the full key list for the provider and filters in-memory for the priority=1 row, instead of a dedicated `findByProviderAndPriority(provider, 1)` query. Inefficient on providers with long failover chains, but currently providers have at most 3-5 keys so the actual cost is negligible. `LlmProviderMasterKeyRepository.findPrimaryActive(provider)` already does the right thing in JPQL — `storeMasterKey` should use it.
**Why deferred:** Premature optimization on a soon-to-be-deleted legacy path (see Finding 04).
**Suggested fix:** When sunsetting the legacy `set/rotate` flow, this code goes away with it.
