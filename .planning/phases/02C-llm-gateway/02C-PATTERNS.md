# Phase 02C: LLM Gateway — Pattern Map

**Mapped:** 2026-05-07
**Files analyzed:** 33 new + 6 modified
**Analogs found:** 33 / 33 strong matches in repo (3 files have no analog — flagged below)

> Reading guide for the planner. Each "Pattern Assignments" block names the new file, the closest analog already on disk, and the concrete excerpts to copy. Excerpts include file paths and line numbers so plan steps can quote them verbatim. Cross-cutting conventions are consolidated at the bottom under "Shared Patterns" — apply them to every Java/TS file in this phase without re-listing in each plan.

---

## File Classification

### Backend `core` — domain + persistence + gateway

| New file | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/core/.../core/llm/package-info.java` | modulith-boundary | declarative | `core/billing/package-info.java` | exact |
| `backend/core/.../core/llm/model/Action.java` | enum (IdentifiedEnum) | value | `core/billing/model/CallSite.java` | exact |
| `backend/core/.../core/llm/model/BYOKProvider.java` | enum (IdentifiedEnum) | value | `core/billing/model/CallSite.java` | exact |
| `backend/core/.../core/llm/model/ToolCallResult.java` | record DTO | value | `core/billing/model/CreditBalance.java` | role-match |
| `backend/core/.../core/llm/model/SanitizationContext.java` | record DTO | value | `core/billing/model/CreditBalance.java` | role-match |
| `backend/core/.../core/llm/model/CallSite.java` ref | (re-uses 2B `CallSite`) | — | — | — |
| `backend/core/.../core/llm/model/SafetyViolationException.java` | runtime exception | value | `core/billing/model/InsufficientCreditsException.java` | exact |
| `backend/core/.../core/llm/model/SanitizationException.java` | runtime exception | value | `core/billing/model/InsufficientCreditsException.java` | exact |
| `backend/core/.../core/llm/model/InvalidByokException.java` | runtime exception | value | `core/billing/model/InsufficientCreditsException.java` | exact |
| `backend/core/.../core/llm/service/LlmGateway.java` | service interface | request-response | `core/billing/service/CreditLedger.java` | exact |
| `backend/core/.../core/llm/service/LlmGatewayImpl.java` | service impl | request-response | `core/billing/service/CreditLedgerService.java` | exact |
| `backend/core/.../core/llm/service/ActionValidator.java` | utility (validation) | transform | `core/billing/service/SepayApiKeyVerifier.java` | role-match |
| `backend/core/.../core/llm/service/ByokService.java` | service impl | CRUD + outbound HTTP | `core/billing/service/BillingTopupService.java` | role-match |
| `backend/core/.../core/llm/persistence/TenantByokCredentialsEntity.java` | JPA entity | persistence | `core/billing/persistence/CreditReservationEntity.java` | exact |
| `backend/core/.../core/llm/persistence/TenantByokCredentialsRepository.java` | JpaRepository | CRUD | `core/billing/persistence/CreditReservationRepository.java` | exact |
| `backend/core/.../core/llm/gateway/sanitization/Sanitizer.java` | functional interface | transform | `core/shared/lang/IdentifiedEnum.java` | role-match (interface shape) |
| `backend/core/.../core/llm/gateway/sanitization/SanitizationPipeline.java` | orchestrator service | transform-pipeline | none — new pattern (Spring `List<Sanitizer>` `@Order` fold) | NO ANALOG |
| `backend/core/.../core/llm/gateway/sanitization/JsoupHtmlStripSanitizer.java` | sanitizer step | transform | none locally — Jsoup is in `libs.versions.toml` but unused | NO ANALOG (lib added) |
| `backend/core/.../core/llm/gateway/sanitization/NfcNormalizeSanitizer.java` | sanitizer step | transform | none — pure-JDK `Normalizer` step | NO ANALOG |
| `backend/core/.../core/llm/gateway/sanitization/UnicodeTagStripSanitizer.java` | sanitizer step | transform | none — pure regex step | NO ANALOG |
| `backend/core/.../core/llm/gateway/sanitization/JtokkitTruncateSanitizer.java` | sanitizer step | transform | none — jtokkit is new dep | NO ANALOG (lib added) |
| `backend/core/.../core/llm/gateway/springai/PlatformApiKey.java` | Spring AI `ApiKey` impl | value-supplier | `core/billing/service/SepayApiKeyVerifier.java` | role-match (per-call header derivation) |
| `backend/core/.../core/llm/gateway/springai/PlatformChatClientConfig.java` | `@Configuration` bean | wiring | `core/gmail/persistence/crypto/RefreshTokenCryptoConfig.java` | exact |
| `backend/core/.../core/llm/gateway/springai/BYOKChatModelFactory.java` | strategy interface | request-response | `core/billing/service/CreditLedger.java` (interface-shape) | role-match |
| `backend/core/.../core/llm/gateway/springai/OpenAiCompatibleByokFactory.java` | strategy impl | request-response (HTTP) | `core/gmail/service/GmailApiClientFactory.java` | role-match (per-call SDK builder) |
| `backend/core/.../core/llm/gateway/springai/AnthropicByokFactory.java` | strategy impl | request-response (HTTP) | `core/gmail/service/GmailApiClientFactory.java` | role-match |
| `backend/core/.../core/llm/persistence/crypto/ByokKeyCipher.java` (or relocate `RefreshTokenCipher`) | wrapper / relocated cipher | transform | `core/gmail/persistence/crypto/RefreshTokenCipher.java` | exact (verbatim reuse) |
| `backend/core/src/main/resources/db/changelog/changes/018-tenant-byok-credentials.yaml` | Liquibase changeset | schema | `db/changelog/changes/015-credit-reservation.yaml` | exact |
| `backend/core/src/main/resources/llm/golden-set.json` | fixture | data | none | NO ANALOG |
| `backend/core/src/main/resources/llm/golden-baseline.json` | fixture | data | none | NO ANALOG |

### Backend `api` — controllers, DTOs, exception mapping

| New file | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/api/.../api/controllers/llm/ByokController.java` | REST controller | request-response | `api/controllers/billing/BillingController.java` | exact |
| `backend/api/.../api/dto/llm/ByokValidateRequest.java` | record DTO | value | `api/dto/billing/TopupIntentRequest.java` | exact |
| `backend/api/.../api/dto/llm/ByokValidateResponse.java` | record DTO | value | `api/dto/billing/TopupIntentResponse.java` | exact |
| `backend/api/.../api/dto/llm/ByokSaveRequest.java` | record DTO | value | `api/dto/billing/TopupIntentRequest.java` | exact |
| `backend/api/.../api/dto/llm/ByokSaveResponse.java` | record DTO | value | `api/dto/billing/TopupIntentResponse.java` | exact |
| `backend/api/.../api/dto/llm/ByokCurrentResponse.java` | record DTO | value | `api/dto/billing/BillingBalanceResponse.java` | exact |
| `backend/api/.../api/config/GlobalExceptionHandler.java` (modify) | exception advice | request-response | self — extend existing handler | exact |
| `backend/api/.../api/error/ErrorCodes.java` (modify) | constants | value | self — append constants | exact |
| `backend/api/src/main/resources/application.yml` (modify) | config | wiring | self — append `zero-mail.llm.platform.*` block | exact |

### Backend `worker` — drift detection job

| New file | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/worker/.../worker/llm/DriftDetectionJob.java` | scheduled job | event-driven | `worker/billing/CreditReserveWatchdog.java` + `worker/GmailWatchScheduler.java` | exact |
| `backend/worker/.../worker/llm/DriftFixtureLoader.java` | utility | file-I/O | none (one-off resource read) | role-match |
| `backend/worker/src/main/resources/application.yml` (modify) | config | wiring | self — append `zero-mail.llm.{platform,drift}` block | exact |

### Backend tests

| New test file | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/core/src/test/.../core/arch/LlmGatewayBoundaryTest.java` | ArchUnit | static | `core/arch/DomainBoundaryArchTests.java`, `core/billing/BillingDomainBoundaryArchTest.java` | exact |
| `backend/core/src/test/.../core/llm/service/LlmGatewayActionValidatorTest.java` | unit | transform | `core/gmail/persistence/crypto/RefreshTokenCipherTest.java` | exact |
| `backend/core/src/test/.../core/llm/gateway/sanitization/*StepTest.java` (4 files) | unit | transform | `core/gmail/persistence/crypto/RefreshTokenCipherTest.java` | exact |
| `backend/core/src/test/.../core/llm/service/LlmGatewayMultiTenantLeakTest.java` | integration | concurrent | `api/security/MultiTenantLeakIntegrationTest.java` | exact |
| `backend/core/src/test/.../core/llm/service/LlmGatewayCreditLifecycleTest.java` | integration | request-response | `core/billing/service/CreditLedgerSettleIdempotentTest.java` | exact |
| `backend/api/src/test/.../api/controllers/llm/ByokControllerIntegrationTest.java` | integration | HTTP | `api/controllers/billing/BillingInsufficientCreditsTest.java` | exact |
| `backend/worker/src/test/.../worker/llm/DriftDetectionJobTest.java` | unit (`@SpringBootTest` w/ `@MockBean ChatModel`) | event-driven | `worker/billing` test pattern + `RefreshTokenCipherTest` | role-match |

### Frontend `apps/web/features/llm/`

| New file | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `apps/web/features/llm/api/llm-api.ts` | API client | request-response | `apps/web/features/account/api/account-api.ts` | exact |
| `apps/web/features/llm/components/ByokForm.tsx` | client component | event-driven | `apps/web/features/account/components/DeleteAccountDialog.tsx` | role-match |
| `apps/web/features/llm/hooks/use-byok.ts` | TanStack mutation hook | request-response | `apps/web/features/account/hooks/useDeleteAccount.ts` | exact |
| `apps/web/features/llm/messages.ts` | i18n co-location | data | none yet (new convention from D-D5) | NO ANALOG (new pattern) |
| `apps/web/i18n/messages/{vi,en}.json` (modify) | i18n bundle | data | self — append `byok.*` and `error.llm.*` keys | exact |
| `apps/web/lib/api/schema.d.ts` (regenerate) | OpenAPI codegen output | — | self — `pnpm generate:api` task | exact |

### Build / config / docs

| Modified file | Role | Data Flow | Analog |
|---|---|---|---|
| `gradle/libs.versions.toml` | catalog | wiring | self (append `springAi`, `jtokkit` versions + libs) |
| `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase index | wiring | self (append `018-...` include) |
| `.planning/REQUIREMENTS.md` | spec doc | data | self (LLM-04 wording update) |

---

## Pattern Assignments

### `LlmGateway.java` (interface) + `LlmGatewayImpl.java` (impl)

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java` and `CreditLedgerService.java`.

This pair is the canonical "public interface + package-private impl" recipe in the repo. Mirror its three load-bearing properties: (1) interface lives in `core.<domain>.service` with full Javadoc that documents the cross-phase contract, (2) impl is package-private (`class CreditLedgerService implements CreditLedger`), (3) ArchUnit pin (`BillingDomainBoundaryArchTest#credit_ledger_service_not_instantiated_outside_billing_service`) prevents callers from depending on the impl class.

**Interface shape** (`CreditLedger.java` lines 11-105):

```java
/**
 * Prepaid credit ledger: the cross-phase contract that Phase 2C ({@code core.llm.LlmGateway})
 * imports verbatim. Callers depend on this interface only; {@link CreditLedgerService} owns
 * the package-private implementation.
 */
public interface CreditLedger {
    ReservationId reserve(UUID tenantId, CallSite callSite);
    void settle(ReservationId reservationId);
    void release(ReservationId reservationId);
    CreditBalance balance(UUID tenantId);
}
```

For LLM gateway use:

```java
public interface LlmGateway {
    ToolCallResult chat(CallSite callSite, String rawHtml, List<ToolCallback> tools);
    ToolCallResult driftCheck(String prompt);  // D-E3 — bypasses ledger
}
```

**Impl shape — reserve/settle/release lifecycle that LlmGatewayImpl will replicate** (`CreditLedgerService.java` lines 25-64):

```java
@Service
class CreditLedgerService implements CreditLedger {

    private static final Logger log = LoggerFactory.getLogger(CreditLedgerService.class);

    private final CreditLedgerEntryRepository entryRepository;
    private final CreditReservationRepository reservationRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;

    CreditLedgerService(/*explicit ctor — no Lombok*/) { /* assignments */ }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
        /* ... */
        log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationUuid);
        return new ReservationId(reservationUuid);
    }
}
```

**LlmGatewayImpl wiring contract (D-A1, D-A3, D-E1)** — translate the JavaDoc-shape from `CreditLedger`:

```java
@Service
class LlmGatewayImpl implements LlmGateway {
    // Platform path: singleton ChatClient + dynamic ApiKey reading TenantContext
    private final ChatClient platformOpenAiClient;
    private final SanitizationPipeline sanitizationPipeline;
    private final ActionValidator actionValidator;
    private final CreditLedger creditLedger;                   // 2B integration
    private final TenantByokCredentialsRepository byokRepo;
    private final BYOKChatModelFactory openAiCompatByokFactory;
    private final BYOKChatModelFactory anthropicByokFactory;
    private final ZeroMailLlmProperties llmProperties;

    public ToolCallResult chat(CallSite callSite, String rawHtml, List<ToolCallback> tools) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        SanitizationContext sanitized = sanitizationPipeline.sanitize(rawHtml);
        Optional<TenantByokCredentialsEntity> byok = byokRepo.findByTenantId(tenantId);

        if (byok.isPresent()) {
            // BYOK path — no credit reserve (BILL-07)
            return callViaByokFactory(byok.get(), sanitized, callSite, tools);
        }
        ReservationId reservation = creditLedger.reserve(tenantId, callSite);
        try {
            ToolCallResult result = callViaPlatformClient(callSite, sanitized, tools);
            creditLedger.settle(reservation);
            return result;
        } catch (Exception failure) {
            creditLedger.release(reservation);
            throw failure;
        }
    }
}
```

The reserve/settle/release shape is dictated verbatim by `CreditLedger`'s D-D1 Javadoc (lines 16-32).

---

### `Action.java` and `BYOKProvider.java` (IdentifiedEnum)

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java` (lines 18-45).

**Pattern to copy** — id-bearing enum + fail-loud `fromId`:

```java
package com.zeromail.core.llm.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum Action implements IdentifiedEnum {

    LABEL("label"),
    ARCHIVE("archive"),
    SAVE_DRAFT("save_draft");

    private final String functionName;

    Action(String functionName) {
        this.functionName = functionName;
    }

    @Override
    public String id() {
        // D-C2 invariant says id() == name(); but discussion D-A in CONTEXT recommends
        // lower-snake to match function names. Recommend: keep id()==name() and add a
        // separate functionName() accessor — see Claude's Discretion in CONTEXT.
        return name();
    }

    public String functionName() {
        return functionName;
    }

    public static Action fromId(String id) {
        return Stream.of(values())
                .filter(action -> action.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Action id: " + id));
    }

    public static Action fromFunctionName(String name) {
        return Stream.of(values())
                .filter(action -> action.functionName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Action functionName: " + name));
    }
}
```

The `fromId` shape and `NoSuchElementException` choice are mandated by `IdentifiedEnum.java` Javadoc (lines 21-39).

---

### `SafetyViolationException.java`, `SanitizationException.java`, `InvalidByokException.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java` (entire file, 16 lines).

**Pattern to copy verbatim:**

```java
/**
 * Thrown when ... .
 *
 * <p><b>Privacy invariant:</b> this exception carries no [content/key/etc.]. The HTTP layer
 * maps it to <STATUS> with {@code code="error.llm.<...>"} and empty parameters so the
 * frontend localizes without reading [...] from the error response.
 */
public class SafetyViolationException extends RuntimeException {
    public SafetyViolationException() { super(); }
}
```

Critical: NO message field, NO content payload — Phase 2C D-C4 says exception MUST NOT contain rejected action name or model output.

---

### `ToolCallResult.java`, `SanitizationContext.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java` and `api/dto/billing/BillingBalanceResponse.java` (lines 1-10) for the record + factory pattern.

```java
public record ToolCallResult(Action action, Map<String, Object> args) {

    public ToolCallResult {
        // Defensive copy + null-checks at the value boundary (per project records convention).
        java.util.Objects.requireNonNull(action, "action");
        args = args == null ? Map.of() : Map.copyOf(args);
    }
}
```

```java
public record SanitizationContext(
        String content,
        int tokenCount,
        boolean truncated,
        Map<String, Object> stepMetadata) {

    public static SanitizationContext initial(String rawHtml) {
        return new SanitizationContext(rawHtml, 0, false, Map.of());
    }

    public SanitizationContext withContent(String newContent) {
        return new SanitizationContext(newContent, tokenCount, truncated, stepMetadata);
    }

    public SanitizationContext withTokenCount(int newTokenCount, boolean wasTruncated) {
        return new SanitizationContext(content, newTokenCount, wasTruncated, stepMetadata);
    }
}
```

---

### `TenantByokCredentialsEntity.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java` (lines 1-75).

**Imports + class header pattern** (lines 1-18):

```java
package com.zeromail.core.llm.persistence;

import java.time.Instant;
import java.util.UUID;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_byok_credentials")
public class TenantByokCredentialsEntity extends AbstractTenantOwnedEntity {
```

**Field + constructor pattern** (lines 19-50 of `CreditReservationEntity.java`):

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private BYOKProvider provider;

    @Column(name = "endpoint", length = 512)
    private String endpoint;

    @Column(name = "encrypted_key", nullable = false)
    private byte[] encryptedKey;          // envelope from RefreshTokenCipher

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    protected TenantByokCredentialsEntity() { /* Hibernate */ }

    public TenantByokCredentialsEntity(
            UUID id, UUID tenantId, BYOKProvider provider, String endpoint,
            byte[] encryptedKey, short keyVersion) {
        super(id, tenantId);                   // AbstractTenantOwnedEntity binds @TenantId
        this.provider = provider;
        this.endpoint = endpoint;
        this.encryptedKey = encryptedKey;
        this.keyVersion = keyVersion;
    }
```

`AbstractTenantOwnedEntity` (lines 22-39) supplies the `@TenantId @Column("tenant_id")` field; do NOT redeclare it.

---

### `TenantByokCredentialsRepository.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java` (entire file, 9 lines).

```java
package com.zeromail.core.llm.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantByokCredentialsRepository
        extends JpaRepository<TenantByokCredentialsEntity, UUID> {

    Optional<TenantByokCredentialsEntity> findByTenantId(UUID tenantId);
}
```

---

### `018-tenant-byok-credentials.yaml` (Liquibase changeset)

**Analog:** `backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml` (entire file, 79 lines).

**Pattern to copy** (lines 1-79) — replace table/column block, keep changeSet header + FK + check + index style. Specifically copy:

- ChangeSet id format (`015-credit-reservation` → `018-tenant-byok-credentials`)
- `id uuid` PK + `tenant_id uuid NOT NULL` with `foreignKeyName: fk_tenant_byok_credentials_tenant`, `references: tenants(id)`, `deleteCascade: true` (lines 9-22)
- `created_at` / `updated_at` `timestamptz defaultValueComputed: now()` blocks (lines 38-49)
- `version int defaultValueNumeric: 0` (lines 53-58) — required for `AbstractAuditableEntity`'s optimistic locking
- `sql:` check constraint pattern for provider whitelist (lines 59-64): `CHECK (provider IN ('anthropic','openai-compatible'))`
- `rollback: dropTable` block (lines 76-78)

Then ADD a UNIQUE constraint `uq_tenant_byok_credentials_tenant ON (tenant_id)` per CONTEXT D-G1 ("one BYOK row per tenant").

Then **append the include line** to `db.changelog-master.yaml` after line 51 (the existing `017-shedlock-table.yaml` entry).

---

### `ByokKeyCipher.java` (or relocate `RefreshTokenCipher`)

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` (entire file, 76 lines) — to be REUSED VERBATIM.

CONTEXT D-A5 says reuse `RefreshTokenCipher` for BYOK encryption. Decision-point for plan-phase per Deferred Ideas: relocate to `core.shared.crypto` (cleaner long-term, touches Phase 1.5 callers + Modulith config) vs declare cross-package edge in `core.llm/package-info.java#allowedDependencies`.

If relocating, the bean wiring already exists at `RefreshTokenCryptoConfig.java` (lines 14-23). The only change: package + imports.

The cipher's encrypt/decrypt signature (lines 36-74) is exactly what BYOK needs:

```java
byte[] encrypt(byte[] plaintext, String tenantId);
byte[] decrypt(byte[] envelope, String tenantId);
```

`tenantId` AAD binding (lines 43, 69) is already correct — no change. The envelope shape `[key_version:int32 | nonce:12 | ciphertext]` (lines 45-49) matches the `encrypted_key BYTEA` + `key_version SMALLINT` columns in 018 (note: the int32 version is also stored in the envelope; `key_version` column is denormalized for index/query convenience).

---

### `LlmGatewayBoundaryTest.java` (ArchUnit)

**Analog:** combine `backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java` (lines 29-93) + `backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java` (lines 11-64).

**Cross-domain repo rule pattern** (`DomainBoundaryArchTests.java` lines 84-93) — extend the existing array to include `core.llm`:

```java
    @ArchTest
    static final ArchRule llm_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.llm..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.gmail.persistence..",
                            "..core.tenant.persistence..",
                            "..core.billing.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");
```

Then ALSO update each existing rule's `resideInAnyPackage(...)` array to include `..core.llm.persistence..`.

**Vendor-SDK / Spring AI isolation rule** (mirroring `BillingDomainBoundaryArchTest.java` lines 28-39 "credit_ledger_service_not_instantiated_outside_billing_service"):

```java
    @Test
    void spring_ai_only_in_gateway_springai() {
        var importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");

        noClasses()
                .that().resideOutsideOfPackage("..core.llm.gateway.springai..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.ai..",
                        "com.openai..",
                        "com.anthropic..")
                .because("LLM-01: Spring AI / vendor SDK imports isolated to gateway.springai")
                .check(importedClasses);
    }

    @Test
    void jsoup_and_jtokkit_only_in_gateway_sanitization() {
        var importedClasses = /* same import */;
        noClasses()
                .that().resideOutsideOfPackage("..core.llm.gateway.sanitization..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.jsoup..", "com.knuddels.jtokkit..")
                .check(importedClasses);
    }

    @Test
    void llm_gateway_impl_not_instantiated_outside_llm_service() {
        // Mirror credit_ledger_service_not_instantiated_outside_billing_service
        // (BillingDomainBoundaryArchTest.java lines 28-39)
    }
```

---

### `ByokController.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java` (entire file, 53 lines).

**Pattern to copy verbatim** — header + tenantId extraction + thin delegation:

```java
@RestController
@Tag(name = "llm-byok")
@RequestMapping("/api/llm/byok")
public class ByokController {

    private final ByokService byokService;

    public ByokController(ByokService byokService) {
        this.byokService = byokService;
    }

    @GetMapping
    public ByokCurrentResponse current() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return byokService.currentForTenant(tenantId);    // service-side maps to DTO
    }

    @PostMapping("/validate")
    public ByokValidateResponse validate(@Valid @RequestBody ByokValidateRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return byokService.validate(tenantId, request);
    }

    @PostMapping
    public ByokSaveResponse save(@Valid @RequestBody ByokSaveRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return byokService.save(tenantId, request);
    }
}
```

`tenantId` is resolved exactly like `BillingController.balance()` (line 37-38): `UUID.fromString(TenantContext.currentOrThrow())`. Controller never opens a transaction; never holds a reference to repositories.

The `private static <DTO> toResponse(...)` helper from `BillingController.java` lines 49-52 is the explicit DTO-conversion idiom — apply if `ByokService` returns entities; preferred is service-side conversion since `BillingController` tolerates entity leakage today.

---

### `GlobalExceptionHandler.java` (modify)

**Analog:** `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` lines 130-148 (`onInsufficientCredits` + `onIllegalLedgerState`) — the exact 402/500 mapping shape.

**Pattern to add** (after line 148):

```java
    @ExceptionHandler(SafetyViolationException.class)
    public ResponseEntity<ProblemDetail> onSafetyViolation(SafetyViolationException exception) {
        log.error("event=llm_safety_violation reason={}", exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "LLM safety violation",
                "The LLM gateway rejected an action outside the allow-list.",
                ErrorCodes.LLM_SAFETY_VIOLATION);
    }

    @ExceptionHandler(SanitizationException.class)
    public ResponseEntity<ProblemDetail> onSanitization(SanitizationException exception) {
        log.error("event=llm_sanitization_failed reason={}", exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "LLM input sanitization failed",
                "The pre-call sanitization pipeline aborted.",
                ErrorCodes.LLM_SANITIZATION_FAILED);
    }
```

**Critical:** copy the privacy posture from `onDataIntegrity` (lines 105-118): `log.warn("... {}", exception.getClass().getSimpleName())` — pass the CLASS NAME ONLY to the logger, NEVER the throwable itself. Logback would otherwise render the stack trace + wrapped exception messages.

The 402 mapping for `InsufficientCreditsException` (lines 130-138) ALREADY EXISTS — Phase 2C does not re-map it; LlmGateway's hard-reject path simply lets that handler do the work.

`ErrorCodes.java` constants to add (file lines 17-30 show the pattern):

```java
    public static final String LLM_SAFETY_VIOLATION  = "error.llm.safety_violation";
    public static final String LLM_SANITIZATION_FAILED = "error.llm.sanitization_failed";
    public static final String LLM_BYOK_VALIDATE_FAILED = "error.llm.byok.validate_failed";
    public static final String LLM_BYOK_INVALID = "error.llm.byok.invalid";
```

---

### `DriftDetectionJob.java`

**Analog:** dual — `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java` (lines 25-56) for the ShedLock pattern, and `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` (lines 50-58) for the `@Scheduled` cron + per-iteration `ScopedValue.where(TenantContext.TENANT, ...)` binding.

**ShedLock + `@Scheduled` cron pattern** (`CreditReserveWatchdog.java` lines 43-55):

```java
@Component
public class DriftDetectionJob {

    private static final Logger log = LoggerFactory.getLogger(DriftDetectionJob.class);

    private final LlmGateway llmGateway;
    private final DriftFixtureLoader fixtureLoader;
    private final boolean enabled;
    private final Counter driftCount;

    public DriftDetectionJob(LlmGateway llmGateway,
                             DriftFixtureLoader fixtureLoader,
                             ZeroMailWorkerProperties props,
                             MeterRegistry meterRegistry) {
        this.llmGateway = llmGateway;
        this.fixtureLoader = fixtureLoader;
        this.enabled = props.llm().drift().enabled();
        this.driftCount = Counter.builder("zero_mail.llm.drift.detected_total")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "llmDriftDetectionJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
    public void scheduledTick() {
        if (!enabled) { return; }
        run();
    }

    public void run() {
        // ... compare baseline vs live; increment driftCount; structured log
        log.info("event=drift_check_run total={} drifted={}", total, drifted);
    }
}
```

The drift job does NOT need per-fixture `ScopedValue.where(TenantContext.TENANT, ...)` because there is no real tenant — but if the implementation reuses `LlmGateway.driftCheck(...)` (D-E3), bind a synthetic UUID per CONTEXT canonical_refs.

`ShedLockConfig.java` (entire file, 33 lines) is already wired in `backend/worker` — DO NOT duplicate.

---

### `LlmGatewayMultiTenantLeakTest.java`

**Analog:** `backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java` (lines 24-74).

**Pattern to copy verbatim** — `StructuredTaskScope` + 100 concurrent virtual-thread requests asserting cross-tenant isolation:

```java
    @Test
    void concurrent_virtual_thread_requests_never_cross_tenant() throws Exception {
        int N = 100;
        List<Seed> seeds = IntStream.range(0, N).mapToObj(i -> seedTenant("t-" + i)).toList();

        try (var scope = StructuredTaskScope.<ToolCallResult>open()) {
            var subtasks = seeds.stream()
                    .map(seed -> scope.fork(() ->
                            ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                                    .call(() -> llmGateway.chat(CallSite.PREVIEW, "hello", List.of()))))
                    .toList();
            scope.join();
            // Assert each subtask's result correlates to its OWN tenant — verify by
            // mocking the ChatModel to echo the bound tenantId, and assert seeds[i] sees
            // its own UUID back, not another tenant's.
        }
    }
```

The seed tenant and `ScopedValue.where(TenantContext.TENANT, ...)` patterns (lines 55-64) are exactly what BYOK leak-test needs: write 2 BYOK rows for tenants A and B with distinct keys, run 100 concurrent gateway calls, assert no call ever uses A's key for B.

---

### `apps/web/features/llm/api/llm-api.ts`

**Analog:** `apps/web/features/account/api/account-api.ts` lines 92-110 (POST + DELETE with `xsrfHeader()` + `api.METHOD` typed client).

**Pattern to copy verbatim** — `api.POST` from `openapi-fetch`:

```ts
import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

type ApiError = components['schemas']['ApiError'];

export interface ByokValidatePayload {
  provider: 'anthropic' | 'openai-compatible';
  endpoint?: string;
  apiKey: string;
}

export interface ByokValidateResult {
  ok: boolean;
  models?: string[];
  reason?: string;
}

export async function validateByok(payload: ByokValidatePayload): Promise<ByokValidateResult> {
  const { data, error, response } = await api.POST('/llm/byok/validate', {
    body: payload,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok) throw error ?? new Error(`/llm/byok/validate failed: ${response.status}`);
  return data as ByokValidateResult;
}

export async function saveByok(payload: ByokValidatePayload): Promise<{ ok: boolean; savedAt: string }> {
  const { data, error, response } = await api.POST('/llm/byok', {
    body: payload,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok) throw error ?? new Error(`/llm/byok save failed: ${response.status}`);
  return data as { ok: boolean; savedAt: string };
}
```

`xsrfHeader()` is the existing CSRF helper at `@/lib/api/client` (proven in `account-api.ts` lines 93, 103).

---

### `apps/web/features/llm/hooks/use-byok.ts`

**Analog:** `apps/web/features/account/hooks/useDeleteAccount.ts` (entire file, 14 lines) + `apps/web/features/onboarding/hooks/useSelectTemplate.ts` (entire file, 14 lines).

**Pattern to copy verbatim:**

```ts
'use client';

import { useMutation } from '@tanstack/react-query';
import { saveByok, validateByok } from '@/features/llm/api/llm-api';

export function useValidateByok() {
  return useMutation({
    mutationFn: validateByok,
    // No onSuccess invalidation — validate is read-only probe, no cache.
  });
}

export function useSaveByok() {
  return useMutation({
    mutationFn: saveByok,
    // No queryClient invalidation needed because BYOK has no cached read state in
    // Phase 2C (current-config display is fetched on-mount via a separate hook).
    // CONTEXT D-D1: BYOK is mutation-only feature, no query-keys file required.
  });
}
```

Per CONTEXT D-D1: NO `query-keys.ts` for `features/llm/` (mutation-only feature).

---

### `apps/web/features/llm/components/ByokForm.tsx`

**Analog:** `apps/web/features/account/components/DeleteAccountDialog.tsx` (entire file, 64 lines) for the controlled-vs-uncontrolled split.

**Critical pattern** (per CONTEXT D-D2): the `<input type="password" name="apiKey">` must be UNCONTROLLED (no `value`/`onChange`); ONLY provider + endpoint visibility are controlled state.

`DeleteAccountDialog.tsx` (lines 22-63) uses controlled `useState` for the typed-confirmation phrase — that pattern is INVERTED here: provider radio + endpoint visibility = `useState`, raw key = `useRef` + read-on-submit.

```tsx
'use client';

import { useTranslations } from 'next-intl';
import { useRef, useState } from 'react';
import { useValidateByok, useSaveByok } from '@/features/llm/hooks/use-byok';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';

type Provider = 'anthropic' | 'openai-compatible';

export function ByokForm() {
  const t = useTranslations();
  const [provider, setProvider] = useState<Provider>('openai-compatible');
  const [endpoint, setEndpoint] = useState('');
  const formRef = useRef<HTMLFormElement>(null);

  const validateMutation = useValidateByok();
  const saveMutation = useSaveByok();

  const onValidate = () => {
    const apiKey = (formRef.current?.elements.namedItem('apiKey') as HTMLInputElement)?.value ?? '';
    validateMutation.mutate({ provider, endpoint: provider === 'openai-compatible' ? endpoint : undefined, apiKey });
    // Raw key drops out of scope after this call.
  };

  const onSave = () => {
    const apiKey = (formRef.current?.elements.namedItem('apiKey') as HTMLInputElement)?.value ?? '';
    saveMutation.mutate({ provider, endpoint: provider === 'openai-compatible' ? endpoint : undefined, apiKey }, {
      onSuccess: () => formRef.current?.reset(),
    });
  };

  const canSave = validateMutation.data?.ok === true;

  return (
    <Card>
      <form ref={formRef} onSubmit={(e) => e.preventDefault()}>
        <RadioGroup value={provider} onValueChange={(v: Provider) => setProvider(v)}>
          <Label><RadioGroupItem value="openai-compatible" /> {t('byok.provider.openaiCompatible')}</Label>
          <Label><RadioGroupItem value="anthropic" /> {t('byok.provider.anthropic')}</Label>
        </RadioGroup>

        {provider === 'openai-compatible' && (
          <Input type="text" value={endpoint} onChange={(e) => setEndpoint(e.target.value)}
                 placeholder={t('byok.endpoint.placeholder')} />
        )}

        {/* UNCONTROLLED — raw secret never enters React state */}
        <Input type="password" name="apiKey" autoComplete="off"
               placeholder={t('byok.apiKey.placeholder')} />

        <Button onClick={onValidate} disabled={validateMutation.isPending}>
          {t('byok.validate.cta')}
        </Button>
        <Button onClick={onSave} disabled={!canSave || saveMutation.isPending}>
          {t('byok.save.cta')}
        </Button>

        {validateMutation.data?.ok === true && (
          <Alert variant="default">
            <AlertTitle>{t('byok.validate.success')}</AlertTitle>
            {validateMutation.data.models && (
              <AlertDescription>{validateMutation.data.models.length} {t('byok.validate.modelsAvailable')}</AlertDescription>
            )}
          </Alert>
        )}
        {validateMutation.data?.ok === false && (
          <Alert variant="destructive">
            <AlertTitle>{t('byok.validate.failed')}</AlertTitle>
          </Alert>
        )}
      </form>
    </Card>
  );
}
```

**Raw shadcn primitives only** per CONTEXT D-D4 + memory rule "raw shadcn first": NO `ByokFormCard` wrapper, NO `ValidationResultAlert` wrapper. The shadcn `Alert` shape comes from `apps/web/components/ui/alert.tsx` lines 24-71 (`Alert` + `AlertTitle` + `AlertDescription`); `RadioGroup` from `radio-group.tsx` lines 1-38.

**Frontend-design skill MUST be invoked before writing this file** per CONTEXT D-D6 + memory rule.

---

### `application.yml` (api + worker — modify)

**Analog:** `backend/api/src/main/resources/application.yml` lines 67-86.

**Pattern to copy** — `:?` fail-fast for the platform secret (lines 70-74 show the established `REFRESH_TOKEN_KEY_BASE64` shape):

```yaml
  # api/application.yml — append under `zeromail:` block
  llm:
    platform:
      provider: openai-compatible
      base-url: https://openrouter.ai/api/v1
      api-key: ${ZEROMAIL_LLM_PLATFORM_API_KEY:?ZEROMAIL_LLM_PLATFORM_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
      compile-model: openai/gpt-4o-mini
      drift-model: openai/gpt-4o-mini
      triage-model: openai/gpt-4o-mini

# Top-level (CONTEXT D-I5)
spring:
  ai:
    chat:
      client:
        observations:
          log-prompt: false
          log-completion: false
      observations:
        log-prompt: false
        log-completion: false
```

`worker/application.yml` adds the SAME `zeromail.llm.platform.*` block PLUS:

```yaml
  llm:
    drift:
      enabled: ${ZEROMAIL_LLM_DRIFT_ENABLED:false}
```

**Critical:** the `:?` placeholder shape (line 74 of api/application.yml) is the canonical fail-fast pattern for any deployment-secret env var. The bare-placeholder shape `${SEPAY_WEBHOOK_API_KEY}` (line 83) PLUS the defensive `BillingProperties` ctor check (`ZeroMailCoreProperties.java` lines 50-64) is an alternative — pick `:?` for `ZEROMAIL_LLM_PLATFORM_API_KEY` since it's simpler and matches `REFRESH_TOKEN_KEY_BASE64`.

---

### `gradle/libs.versions.toml` (modify)

**Pattern** — append after current entries:

```toml
[versions]
# ...existing entries...
springAi = "2.0.0-M4"
jtokkit  = "1.1.0"   # verify latest stable at plan-phase per CONTEXT Claude's Discretion

[libraries]
spring-ai-bom = { module = "org.springframework.ai:spring-ai-bom", version.ref = "springAi" }
spring-ai-starter-model-openai = { module = "org.springframework.ai:spring-ai-starter-model-openai", version.ref = "springAi" }
spring-ai-starter-model-anthropic = { module = "org.springframework.ai:spring-ai-starter-model-anthropic", version.ref = "springAi" }
jtokkit = { module = "com.knuddels:jtokkit", version.ref = "jtokkit" }
```

`springAi` is ALREADY DECLARED at line 3 — re-use, do not duplicate.

---

## Shared Patterns

### Pattern S-1: Privacy logging contract (`event=opaque tenantId={}`)

**Source:** convention #4 in `CLAUDE.md`; concrete example `CreditLedgerService.java` line 62 (`log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationUuid)`).

**Apply to:** every Java file in this phase that emits a log line. NO content, NO prompt/completion, NO tool-call args content, NO endpoint URL on BYOK validate, NO model output. Examples:

```java
log.info("event=llm_call_started tenantId={} callSite={} provider={} model={}",
         tenantId, callSite, provider, model);
log.info("event=llm_call_succeeded tenantId={} callSite={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}",
         tenantId, callSite, latencyMs, promptTokens, completionTokens, stopReason, truncated);
log.warn("event=llm_call_failed tenantId={} callSite={} reason={}",
         tenantId, callSite, exception.getClass().getSimpleName());
log.info("event=byok_validate_attempted tenantId={} provider={}", tenantId, provider);
log.info("event=sanitization_completed tenantId={} truncated={} tokenCount={}",
         tenantId, truncated, tokenCount);
log.info("event=drift_check_run total={} drifted={}", total, drifted);
```

**Anti-pattern** that `GlobalExceptionHandler.java` lines 105-118 specifically flags: passing the `Throwable` to logger (renders stack trace + wrapped exception messages, leaks SQL state / PII). Always pass `exception.getClass().getSimpleName()` only.

### Pattern S-2: Thin controller + service-owned `@Transactional`

**Source:** convention #1 in `CLAUDE.md`; concrete example `BillingController.java` (entire file).

**Apply to:** `ByokController.java`. Controller never declares `@Transactional`, never injects repositories, never opens DB transactions. Service (`ByokService`, `LlmGatewayImpl`) owns `@Transactional(propagation = Propagation.REQUIRED)` per `CreditLedgerService.java` lines 67-68.

### Pattern S-3: Records-for-DTOs / classes-for-entities Lombok-free

**Source:** convention #2 in `CLAUDE.md`; concrete examples `TopupIntentRequest.java` (record DTO), `CreditReservationEntity.java` (mutable class with `protected` no-args ctor for Hibernate).

**Apply to:** all `*Request`/`*Response` files (records); `TenantByokCredentialsEntity` (class, `protected TenantByokCredentialsEntity()` Hibernate ctor + explicit public ctor — see lines 34-48 of `CreditReservationEntity.java`).

### Pattern S-4: Enterprise-readability variable naming

**Source:** `CLAUDE.md` §"Backend Code Style".

**Apply to:** every Java file. Concrete examples in `GlobalExceptionHandler.java` parameters (`exception` not `e`/`ex`, `request`/`response` not `req`/`res`). Enforce in plan actions: `tenantContext` not `ctx`, `byokService` not `svc`, `chatResponse` not `resp`, `gmailMessage` not `msg`, `transactionTemplate` not `tx`. Lambda variables follow same rule (`gmailConnection -> ...` not `c -> ...`).

### Pattern S-5: `:?` fail-fast for deployment secrets

**Source:** `application.yml` lines 70-74 (REFRESH_TOKEN_KEY_BASE64), lines 76-78 (PUBSUB_*).

**Apply to:** `ZEROMAIL_LLM_PLATFORM_API_KEY` in BOTH `api/application.yml` AND `worker/application.yml` (worker needs it for `DriftDetectionJob`).

### Pattern S-6: `TenantContext.currentOrThrow()` resolution at every entry point

**Source:** `TenantContext.java` lines 11-15; widely used (e.g. `BillingController.java` lines 37, 43; `CreditLedgerSettleIdempotentTest.java` lines 32, 45).

**Apply to:** `ByokController` methods, `LlmGatewayImpl.chat(...)`, `ByokService` methods, `PlatformApiKey.getValue()` (D-A1 — resolve dynamic key at HTTP send time, not bean-construction). Never accept tenantId as a constructor field — always read from `TenantContext` per call.

### Pattern S-7: Per-domain modulith `package-info.java`

**Source:** `core/billing/package-info.java` (entire file, 30 lines).

**Apply to:** `core/llm/package-info.java`. Allowed dependencies for LLM domain:

```java
@ApplicationModule(
        displayName = "LLM Gateway",
        allowedDependencies = {"tenant", "billing", "shared.persistence", "shared.lang", "gmail.persistence.crypto"})
package com.zeromail.core.llm;

import org.springframework.modulith.ApplicationModule;
```

The `gmail.persistence.crypto` edge captures `RefreshTokenCipher` reuse (D-A5) — drop this edge if planner relocates the cipher to `core.shared.crypto` per the Deferred Idea.

### Pattern S-8: Refresh-token cipher reuse for any AES-GCM-256 secret

**Source:** `RefreshTokenCipher.java` (entire file) + `RefreshTokenCryptoConfig.java` (entire file).

**Apply to:** BYOK key encryption. The `encrypt(byte[], String)` / `decrypt(byte[], String)` API is sufficient — no new cipher class needed. The Phase 2C `TenantByokCredentialsEntity#encryptedKey BYTEA` column stores the same `[key_version:int32 | nonce:12 | ciphertext]` envelope.

### Pattern S-9: ArchUnit per-domain rule extension

**Source:** `DomainBoundaryArchTests.java` lines 84-93 (`billing_no_cross_domain_repos` — note that adding a new domain requires updating EVERY OTHER rule's exclusion array per the comment at lines 19-22).

**Apply to:** add `llm_no_cross_domain_repos` rule + extend each existing rule's `resideInAnyPackage(...)` array to add `..core.llm.persistence..`.

### Pattern S-10: TanStack Query mutation triplet

**Source:** memory rule + `apps/web/features/account/{api,components,hooks}/`.

**Apply to:** `features/llm/{api,components,hooks}/`. Per CONTEXT D-D1: NO `query-keys.ts` for mutation-only feature. Per CONTEXT D-D5: `messages.ts` lives co-located.

---

## No Analog Found

Files with no close existing match — planner uses RESEARCH.md / AI-SPEC.md patterns instead:

| File | Role | Why no analog |
|---|---|---|
| `core/llm/gateway/sanitization/{Jsoup,Nfc,UnicodeTagStrip,JtokkitTruncate}Sanitizer.java` | sanitizer step | First Spring AI / Jsoup integration in the repo. Use AI-SPEC pipeline shape; pure-JDK / library calls per step. |
| `core/llm/gateway/sanitization/SanitizationPipeline.java` | orchestrator | First "ordered `List<Sanitizer>` fold" in the repo. Use Spring's `@Order` + `OrderComparator` (CONTEXT D-B1). |
| `core/llm/gateway/springai/PlatformChatClientConfig.java` and `*ChatModelFactory*` | Spring AI bean wiring | First Spring AI usage. Verify `OpenAiApi#mutate()` / `AnthropicApi#mutate()` / `ApiKey` interface via Context7 at plan-phase per CONTEXT external specs list. RESEARCH.md `MultiModelService` example is the closest reference. |
| `backend/core/src/main/resources/llm/golden-set.json` + `golden-baseline.json` | drift fixtures | First fixture of this kind. Structure dictated by CONTEXT D-H1/D-H2 (synthesized data, no PII). |
| `apps/web/features/llm/messages.ts` | i18n co-location | First time a feature folder owns its translation contributions. Convention dictated by CONTEXT D-D5; format = `{vi, en}` shape merged build-time into `i18n/messages/{vi,en}.json`. |

---

## Metadata

**Analog search scope:** `backend/core`, `backend/api`, `backend/worker`, `apps/web/features`, `apps/web/components/ui`, `backend/*/src/main/resources`, `backend/*/src/test`.

**Files scanned:** ~120 backend Java files + 38 frontend TS/TSX files + 17 Liquibase changesets + application.yml × 2 + libs.versions.toml.

**Strongest analog clusters identified:**
1. `core.billing` (Phase 2B) — closest peer module; same multi-package shape, same modulith pattern, same cross-phase contract style. Use as the structural template for `core.llm`.
2. `RefreshTokenCipher` — reused verbatim for BYOK encryption (D-A5).
3. `BillingController` + `BillingInsufficientCreditsTest` — controller + 402 mapping shape for `ByokController` + LLM credit-cap integration test.
4. `GmailWatchScheduler` + `CreditReserveWatchdog` — `@Scheduled` + ShedLock pattern for `DriftDetectionJob`.
5. `MultiTenantLeakIntegrationTest` — `StructuredTaskScope` + 100-virtual-thread isolation test for `LlmGatewayMultiTenantLeakTest`.

**Pattern extraction date:** 2026-05-07.

*Phase: 02C-llm-gateway*
*PATTERNS.md authored 2026-05-07 by `gsd-pattern-mapper`*
