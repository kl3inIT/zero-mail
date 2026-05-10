# Zero Mail — Conventions

Project-wide coding conventions referenced by `CLAUDE.md` / `AGENTS.md`.

---

## 1. Thin controllers + service-owned `@Transactional`

Controllers never touch repositories directly. Transaction boundaries belong in `@Service` classes; controllers translate HTTP-shape ↔ domain-shape and forward to services. Response DTOs own projection/result-to-wire mapping through static `from(...)` factories, so controllers return `SomeResponse.from(domainResult)` instead of keeping private `toResponse(...)` helpers or constructing response DTOs inline. This keeps controllers cheap to test (no DB), centralizes transaction logic, and lets Spring Modulith + ArchUnit enforce domain boundaries cleanly. Any controller that injects a JPA repository directly creates a hidden transaction-scope bug and breaks domain isolation.

**Example:** `backend/api/src/main/java/com/zeromail/api/controllers/tenant/TenantStatusController.java`

```java
@GetMapping("/gmail/connection/status")
public GmailConnectionStatusResponse status() {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    GmailConnectionProjection projection = connectionService.currentStatus(tenantId);
    return GmailConnectionStatusResponse.from(projection);
}
```

**Anti-pattern:** controller injecting `UserRepository` or `GmailConnectionRepository` and calling `findById` / `save` directly — bypasses service-layer transaction boundary and exposes persistence internals to the HTTP layer. Also avoid controller-local `private static toResponse(...)` helpers or inline `new SomeResponse(...)` mapping when the response DTO can own a `from(...)` factory.

---

## 2. Backend domain package layout

`backend/core` uses package-based modular monolith boundaries. Do not create ambiguous `model`
packages for new backend code. Inside each bounded context, create only the responsibility folders
that are actually needed:

- `domain/` — core business vocabulary: value objects, domain enums, matcher/action concepts.
- `application/` — use-case services plus command inputs and operation results.
- `projection/` — read-side snapshots returned by query/list/status services.
- `exception/` — business/application exceptions owned by the domain.
- `persistence/` — JPA entities, repositories, converters, and `lowlevel/` SQL/JDBC helpers.

For example, rules code uses `core.rules.domain.MatcherNode`,
`core.rules.application.RuleCompileResult`, `core.rules.projection.RuleStatusProjection`, and
`core.rules.exception.RuleValidationException`. `Projection` means read state; `Result` means the
outcome of an operation. A `POST` can still return a projection when the API returns the current
state after a mutation.

`backend/api` keeps its horizontal layer layout (`controllers`, `dto`, `error`, `security`,
`config`), but controllers are grouped by domain under `api/controllers/<domain>/`. DTOs stay under
`api/dto/<domain>/`, and large aggregate DTO files are split into focused request/response records.

**Anti-pattern:** adding `core.<domain>.model.*`, naming read-side types `*View`, placing business
exceptions in `backend/api`, or keeping all endpoint contracts for a domain in one monolithic DTO
container such as `RuleDtos`.

---

## 3. Records for DTOs, classes for entities, Lombok-free

Java 25 records cover all DTO and value-object use cases — immutable, `equals`/`hashCode`/`toString` for free, exhaustive deconstruction patterns. Entities stay `class` because Hibernate proxies require a no-args constructor and mutable fields. Lombok is banned project-wide because it lags JDK releases by 3–12 months and Java 25 features (flexible constructors, module imports) can trip it. If a builder is needed, write an explicit nested `Builder` class.

**Example DTO:** `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java`

```java
public record MeResponse(String userId, String tenantId, String email,
        String onboardingStep, String preferredLanguage) {}
```

**Example entity:** `backend/core/src/main/java/com/zeromail/core/account/persistence/UserEntity.java`

```java
@Entity @Table(name = "users")
public class UserEntity extends AbstractTenantOwnedEntity {
    @Column(name = "google_subject", nullable = false, unique = true)
    private String googleSubject;
    protected UserEntity() {}  // no-args for Hibernate
    public UserEntity(UUID id, UUID tenantId, String googleSubject, String email) { ... }
}
```

**Anti-pattern:** `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` (Lombok annotations — forbidden). Using a `record` for a JPA entity (no-args constructor + proxy incompatibility). Storing entity state in an immutable record type.

---

## 4. Enum state machines via `OrderedEnum` / `IdentifiedEnum` + static `fromId` fail-loud

Domain enums never rely on `name()` for DB storage ordering or `ordinal()` for comparison. Implement `core.shared.lang.OrderedEnum` (carries `id()` + `weight()` + `labelKey()`) for ordered state machines, or `IdentifiedEnum` for unordered identity sets. Storage uses `id()` (which equals `name()` by the D-C2 invariant), so DB rows survive enum reordering via weight-gap inserts. Lookup uses a static `fromId(String)` that throws `NoSuchElementException` on unknown ids — never returns null, never silently maps to a default.

**Example:** `backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java`

```java
public enum OnboardingStep implements OrderedEnum {
    GMAIL_CONNECTED(10), TEMPLATE_SELECTED(20), COMPLETE(30);
    // ...
    public static OnboardingStep fromId(String id) {
        return Stream.of(values()).filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
```

**Anti-pattern:** `@Enumerated(EnumType.ORDINAL)` (breaks if enum order changes), `MyEnum.valueOf(input)` without a try/catch (throws `IllegalArgumentException` not `NoSuchElementException` — different failure contract), returning `Optional.empty()` for unknown ids and silently treating it as a default downstream.

---

## 5. Privacy logging format

Every log statement emits an opaque `event=` name plus structured fields — never raw email address, Google subject, OAuth refresh-token bytes, OAuth access-token bytes, message body, LLM prompt, or LLM completion. Tenant context is the only stable identifier and is logged as a UUID via `tenantId={}`. ArchUnit rules (Phase 1 FND-04) and a Logback scrub filter (FND-03) catch most violations at build/runtime, but the convention is the first line of defense.

**Example:** `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java`

```java
log.info("event=oauth_provisioning_complete tenantId={}", result.tenantId());
log.warn("event=oauth_settings_basic_missing tenantId={}", result.tenantId());
log.info("event=oauth_no_refresh_token_first_login");
```

**Anti-pattern:**

```java
log.info("provisioned user " + email + " sub=" + googleSubject);  // PII in log
log.error("Token decrypt failed: " + new String(tokenBytes));     // secret bytes in log
log.warn("Gmail body: " + emailBody);                             // content in log
```

---

## 6. Direct calls vs Spring Modulith events

Use direct service calls for commands that need an immediate result, strong transaction semantics, or fail-fast behavior. Controllers and workers should call `backend/core` services directly; examples include OAuth bundled provisioning, credit reservation/settlement/release, Pub/Sub delivery ingestion, account deletion cleanup, and Gmail connection status reads. Do not replace these command paths with events just to appear more decoupled.

Use Spring Modulith application events for in-process, after-commit side effects where the publishing module should not know who reacts. Good candidates are: `MailMessageObserved` → future rules/triage job creation, Gmail connection state changes → notifications/audit/projections, top-up credited → receipt/analytics/notification, onboarding completed → activation/analytics, and account deleted → non-critical post-cleanup side effects.

Spring application events are local to one Spring application context. They do not cross from `backend/api` to `backend/worker` when those apps run as separate processes. Cross-process handoff must stay durable through PostgreSQL-backed outbox / processing tables with idempotent workers, not plain Spring events. If an event is part of the domain contract and may be produced or consumed by API, worker, or future modules, define it in `backend/core`, not under `backend/api`.

**Anti-pattern:** eventifying synchronous commands such as `CreditLedger.reserve(...)`, using Spring events as an API-to-worker queue, or defining reusable domain events in `com.zeromail.api.*` so workers cannot publish/consume them cleanly.

---

## 7. UI primitive selection

Before building or refactoring UI, check whether shadcn/ui already provides the needed primitive (for example button, card, input, label, radio-group, toggle-group, tooltip, dialog, alert, separator, skeleton, badge). If the primitive exists and is not already present locally, install it from `apps/web` with `pnpm dlx shadcn@latest add <component>` and compose product-specific components around `@/components/ui/*` instead of hand-rolling the primitive.

Treat `apps/web/components/ui/**` as copied shadcn primitive source. These files are ignored by ESLint and Prettier; edit them only when intentionally customizing the local primitive contract.

---

## 8. Frontend feature API, hooks, query keys, and tests

Feature code in `apps/web/features/<feature>/` uses explicit ownership:

- `api/<feature>-api.ts` contains the feature's small HTTP functions. Split into multiple API files only when the feature grows into distinct resources or the file becomes hard to scan.
- `query-keys.ts` contains TanStack Query key factories for cached server data. Keep this file outside `api/` because query keys describe cache identity, not transport.
- `hooks/useX.ts` stays one hook per use case. Hooks own TanStack Query behavior such as `queryKey`, `queryFn`, mutation invalidation, optimistic updates, and error behavior.
- Do not create a `query-keys.ts` file for mutation-only features unless the feature actually owns cached query data.
- Query keys are named for cached data, not UI actions. Example: `accountQueryKeys.me()` is correct for `/me`; a mutation that toggles triage pause invalidates `accountQueryKeys.me()` if the paused state is returned by `/me`.
- Keep feature roots barrel-free. Import concrete files directly.

Tests are split by runtime:

- Vitest unit/component tests that belong to one feature may live beside that feature under `features/**`. App-wide contract tests live in `apps/web/__tests__/**`.
- Playwright browser tests live only in `apps/web/e2e/**`.
- Do not put Playwright specs under `__tests__/`; Vitest and Playwright use different runners.

**Example:**

```text
features/account/
  api/account-api.ts
  query-keys.ts
  hooks/useCurrentUser.ts
  hooks/useDeleteAccount.ts
  hooks/useUpdateLanguage.ts
  components/DeleteAccountDialog.tsx
```

---

## 9. Subproject-owned configuration files

Each runnable subproject owns its own runtime configuration file. Do not move API-only,
worker-only, or web-only properties into another module's configuration file just to avoid
duplication.

Backend examples:

- API process properties belong in `backend/api/src/main/resources/application.yml`.
- Worker process properties belong in `backend/worker/src/main/resources/application.yml`.
- Shared typed configuration classes may live in `backend/core` when both API and worker bind the
  same namespace, but each runnable module still declares the values/defaults it needs in its own
  `application.yml`.

Frontend examples:

- Next.js environment and build/runtime configuration belongs under `apps/web`.
- Backend Spring properties do not belong in `apps/web`, and frontend-only settings do not belong in
  backend `application.yml` files.

**Anti-pattern:** adding worker scheduler flags to API `application.yml`, adding API session/OAuth
properties to worker `application.yml`, or creating a single monorepo-wide properties file that
every subproject must parse.

---

## 10. Frontend i18n: per-feature `messages.ts` + generated locale bundles

The runtime i18n bundles `apps/web/i18n/messages/{vi,en}.json` are **generated artifacts**, not
source-of-truth files. The source of truth is per-feature `apps/web/features/<feature>/messages.ts`,
which is merged into the locale JSON bundles by `apps/web/scripts/merge-feature-i18n.ts`.

### Where to add new strings

- **Add or edit keys only in** `apps/web/features/<feature>/messages.ts`.
- Each `messages.ts` exports an object whose top-level shape is `{ vi: {...}, en: {...} }`.
- Co-locating strings with feature code is the standard pattern (see Convention 8); the merge step
  is what makes them visible to next-intl at runtime.

### Generated outputs

- `apps/web/i18n/messages/vi.json` and `apps/web/i18n/messages/en.json` are written by
  `pnpm i18n:build` (which runs `merge-feature-i18n.ts`). The header comment in each generated
  file marks them as `DO NOT EDIT MANUALLY`.
- **Editing the JSON files directly is forbidden** - the next `pnpm build` will overwrite the change.
- If a string needs to ship in only one locale (e.g. a Vietnamese-only legal page), still author it
  inside the feature's `messages.ts` with the other locale set to a fallback or empty string.

### Build pipeline

- `pnpm i18n:build` regenerates the bundles from every `features/**/messages.ts`.
- `pnpm build` runs `i18n:build` automatically via `prebuild`, so production bundles are always in
  sync with feature source.
- `pnpm i18n:check` is a drift detector - it regenerates the bundles into a temp file and fails if
  the output differs from the committed JSON. Run it locally before opening a PR; CI runs it as
  part of the standard frontend gate.

### Anti-patterns

- Editing `apps/web/i18n/messages/vi.json` or `en.json` to fix a translation. Edit `messages.ts`
  in the relevant feature folder instead.
- Forgetting to add a key to `messages.ts` and assuming it will be picked up - if a key is not in
  any feature's `messages.ts`, it disappears from the bundle on the next `i18n:build`.
- Putting feature-scoped strings into a global `apps/web/messages.ts` instead of
  `apps/web/features/<feature>/messages.ts`. The merge script walks per-feature files; centralized
  strings break the co-location convention and are easier to leave stale when a feature is deleted.
