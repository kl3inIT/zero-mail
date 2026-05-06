# Zero Mail — Conventions

Project-wide coding conventions referenced by `CLAUDE.md` / `AGENTS.md`.

---

## 1. Thin controllers + service-owned `@Transactional`

Controllers map domain view-model records to wire DTOs via private `toResponse(...)` helpers and never touch repositories directly. Transaction boundaries belong in `@Service` classes; controllers translate HTTP-shape ↔ domain-shape and forward to services. This keeps controllers cheap to test (no DB), centralizes transaction logic, and lets Spring Modulith + ArchUnit enforce domain boundaries cleanly. Any controller that injects a JPA repository directly creates a hidden transaction-scope bug and breaks domain isolation.

**Example:** `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java`

```java
@GetMapping("/gmail/connection/status")
public GmailConnectionStatusResponse status() {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    GmailConnectionView view = connectionService.currentStatus(tenantId);
    return toResponse(view);
}

private static GmailConnectionStatusResponse toResponse(GmailConnectionView view) {
    return new GmailConnectionStatusResponse(view.status(), view.googleEmail());
}
```

**Anti-pattern:** controller injecting `UserRepository` or `GmailConnectionRepository` and calling `findById` / `save` directly — bypasses service-layer transaction boundary and exposes persistence internals to the HTTP layer.

---

## 2. Records for DTOs, classes for entities, Lombok-free

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

## 3. Enum state machines via `OrderedEnum` / `IdentifiedEnum` + static `fromId` fail-loud

Domain enums never rely on `name()` for DB storage ordering or `ordinal()` for comparison. Implement `core.shared.lang.OrderedEnum` (carries `id()` + `weight()` + `labelKey()`) for ordered state machines, or `IdentifiedEnum` for unordered identity sets. Storage uses `id()` (which equals `name()` by the D-C2 invariant), so DB rows survive enum reordering via weight-gap inserts. Lookup uses a static `fromId(String)` that throws `NoSuchElementException` on unknown ids — never returns null, never silently maps to a default.

**Example:** `backend/core/src/main/java/com/zeromail/core/onboarding/model/OnboardingStep.java`

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

## 4. Privacy logging format

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

## 5. UI primitive selection

Before building or refactoring UI, check whether shadcn/ui already provides the needed primitive (for example button, card, input, label, radio-group, toggle-group, tooltip, dialog, alert, separator, skeleton, badge). If the primitive exists and is not already present locally, install it from `apps/web` with `pnpm dlx shadcn@latest add <component>` and compose product-specific components around `@/components/ui/*` instead of hand-rolling the primitive.

Treat `apps/web/components/ui/**` as copied shadcn primitive source. These files are ignored by ESLint and Prettier; edit them only when intentionally customizing the local primitive contract.
