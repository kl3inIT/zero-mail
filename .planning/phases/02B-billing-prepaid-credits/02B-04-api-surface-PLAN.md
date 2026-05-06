---
phase: 02B
plan: 04
type: execute
wave: 3
depends_on: [02, 03]
files_modified:
  - backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/billing/SepayWebhookPayload.java
  - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java
  - backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/config/BillingApiConfiguration.java
  - backend/api/src/main/resources/application.yml
  - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
  - backend/api/build.gradle.kts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/lib/api/schema.d.ts
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
autonomous: true
requirements: [BILL-01, BILL-05, BILL-06]
must_haves:
  truths:
    - "GET /api/billing/balance returns 200 + {availableCredits, heldCredits, currency: 'credits'} for an authenticated session; 401 for no session."
    - "POST /api/billing/topup/intent body {amountVnd: long} (with @Min(1) validation) returns {code, amountVnd, expiresAt, qrPayload: null} for the current tenant."
    - "POST /api/billing/sepay/webhook with Authorization: Apikey ${SEPAY_WEBHOOK_API_KEY} returns 200 {success: true}; missing or wrong API key returns 401 (no body); replay returns 200 with single ledger row."
    - "InsufficientCreditsException maps to HTTP 402 with code=error.billing.insufficient + params: Map.of() (no balance leak)."
    - "IllegalLedgerStateException maps to HTTP 500 with code=error.billing.ledger.invalidState."
    - "apps/web/i18n/messages/{vi,en}.json both contain error.billing.insufficient + error.billing.ledger.invalidState + error.billing.sepay.reference_invalid + error.billing.sepay.auth_invalid; pnpm i18n:check STRICT passes."
    - "apps/web/lib/api/schema.d.ts regenerated via pnpm generate:api contains paths['/api/billing/balance'], paths['/api/billing/topup/intent'], paths['/api/billing/sepay/webhook']."
    - "SEPAY_WEBHOOK_API_KEY resolves with :? fail-fast in application.yml; test profile injects via ApiPostgresTestBase @DynamicPropertySource."
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java"
      provides: "Session-auth thin controller: GET /balance + POST /topup/intent."
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java"
      provides: "API-key-auth webhook receiver: POST /api/billing/sepay/webhook."
    - path: "backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java"
      provides: "@Order(2) SecurityFilterChain on /api/billing/sepay/** with permitAll + SepayApiKeyAuthFilter before UsernamePasswordAuthenticationFilter (W6 — coexists with PubSubSecurityConfig @Order(1); SecurityConfig bumps to @Order(3))."
    - path: "backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java"
      provides: "OncePerRequestFilter using core.billing.service.SepayApiKeyVerifier; logs event=sepay_webhook_auth_invalid (no header bytes)."
    - path: "backend/api/src/main/resources/application.yml"
      provides: "zero-mail.billing.* config block + SEPAY_WEBHOOK_API_KEY :? fail-fast."
    - path: "apps/web/lib/api/schema.d.ts"
      provides: "Regenerated typed client containing the 3 new billing endpoints."
  key_links:
    - from: "BillingController.balance()"
      to: "CreditLedger.balance(tenantId)"
      via: "constructor-injected interface"
      pattern: "creditLedger.balance"
    - from: "SepayApiKeyAuthFilter"
      to: "core.billing.service.SepayApiKeyVerifier"
      via: "constructor injection"
      pattern: "verifier.verify(authorizationHeader)"
    - from: "GlobalExceptionHandler.onInsufficientCredits"
      to: "ApiError code=error.billing.insufficient + params: Map.of()"
      via: "@ExceptionHandler(InsufficientCreditsException.class)"
      pattern: "BILLING_INSUFFICIENT_CREDITS"
---

<objective>
Wire `core.billing` into the HTTP surface: 4 DTOs, 2 controllers, the @Order(1) webhook security chain + filter, ErrorCodes + GlobalExceptionHandler mappings, the test-base property injection for `:?` fail-fast, the api `application.yml` config block, the openapi-emit dummy property for hermetic schema generation, both i18n bundles, and the regenerated `apps/web/lib/api/schema.d.ts`. After this plan, all 7 Wave 0 api-tests turn GREEN.

Purpose: per CONTEXT D-G2, billing controllers live under `api/controllers/billing/` (parity with account/, gmail/, onboarding/ DTO group-by-domain from Phase 1.2.1 Plan 04). Per D-F1, both api and worker `application.yml` get `:?` fail-fast (worker side handled in Plan 05). Per RESEARCH §"Critical Override", SePay uses `Authorization: Apikey` static-secret — NOT HMAC.

Output: 17 files (mostly new). Schema.d.ts regen runs via the existing `pnpm generate:api` pipeline.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@CLAUDE.md
@CONVENTIONS.md
@backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java
@backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java
@backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java
@backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java
@backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
@backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
@backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailConnectionStatusResponse.java
@backend/api/src/main/resources/application.yml
@backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
@backend/api/build.gradle.kts
@apps/web/i18n/messages/vi.json
@apps/web/i18n/messages/en.json
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: 4 DTOs + BillingApiConfiguration + ErrorCodes + GlobalExceptionHandler</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/billing/SepayWebhookPayload.java,
    backend/api/src/main/java/com/zeromail/api/config/BillingApiConfiguration.java,
    backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java,
    backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  </files>
  <behavior>
    - 4 records with package-private + jackson-friendly accessors; TopupIntentRequest has @Min(1) long amountVnd; BillingBalanceResponse.from(CreditBalance) factory.
    - BillingApiConfiguration activates @EnableConfigurationProperties(BillingProperties.class).
    - ErrorCodes has 4 new constants matching i18n keys.
    - GlobalExceptionHandler has 2 new @ExceptionHandler methods using existing problem(...) helper; both pass params: Map.of() (no balance leak).
  </behavior>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 159–195 — DTO record shapes; lines 736–807 — ErrorCodes + GlobalExceptionHandler diff; cross-cutting Pattern 7 lines 1101–1106 — privacy invariant on params)
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailConnectionStatusResponse.java (record + static from(...) factory analog)
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java (existing constants — extend in place; alphabetical or logical grouping per existing style)
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java (lines 71–90 for @ExceptionHandler + problem(...) helper analog; verify problem(...) signature/arity in existing file)
    - backend/api/src/main/java/com/zeromail/api/ZeroMailApiApplication.java (verify entity-scan / component-scan roots — needs to include com.zeromail.core.billing else add explicit @EntityScan + @ComponentScan in BillingApiConfiguration)
  </read_first>
  <action>
**File 1: BillingBalanceResponse.java** (api/dto/billing/)
```java
package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.model.CreditBalance;

public record BillingBalanceResponse(int availableCredits, int heldCredits, String currency) {

    public static BillingBalanceResponse from(CreditBalance balance) {
        return new BillingBalanceResponse(balance.availableCredits(), balance.heldCredits(), "credits");
    }
}
```

**File 2: TopupIntentRequest.java**
```java
package com.zeromail.api.dto.billing;

import jakarta.validation.constraints.Min;

public record TopupIntentRequest(@Min(1) long amountVnd) {
}
```

**File 3: TopupIntentResponse.java**
```java
package com.zeromail.api.dto.billing;

import java.time.Instant;

public record TopupIntentResponse(String code, long amountVnd, Instant expiresAt, String qrPayload) {
}
```
(qrPayload always nullable in v1 — Phase 5 fills it.)

**File 4: SepayWebhookPayload.java** (mirror SePay's documented JSON per RESEARCH §"Pattern 5")
```java
package com.zeromail.api.dto.billing;

public record SepayWebhookPayload(
        long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String code,
        String content,
        String transferType,
        long transferAmount,
        long accumulated,
        String subAccount,
        String referenceCode,
        String description) {
}
```

**File 5: BillingApiConfiguration.java** (api/config/)

W8: Strip the redundant `@ComponentScan`. The api boot class `ZeroMailApiApplication.java` already component-scans `com.zeromail.core` (verified — see `ZeroMailApiApplication.java`). Adding it here causes double-registration warnings on context start. The ONLY thing this config needs is the `BillingProperties` binding (Spring does not auto-bind unannotated `@ConfigurationProperties` record types).

```java
package com.zeromail.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.billing.service.BillingProperties;

/**
 * Activates zero-mail.billing.* configuration binding inside the api module. The api boot
 * class ({@code ZeroMailApiApplication}) already component-scans {@code com.zeromail.core},
 * so no `@ComponentScan` is needed here.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingApiConfiguration {
}
```

**File 6: ErrorCodes.java** (modify in place — add 4 constants)
Append into the existing constants block (preserving the file's existing style):
```java
public static final String BILLING_INSUFFICIENT_CREDITS    = "error.billing.insufficient";
public static final String BILLING_LEDGER_INVALID_STATE    = "error.billing.ledger.invalidState";
public static final String BILLING_SEPAY_REFERENCE_INVALID = "error.billing.sepay.reference_invalid";
public static final String BILLING_SEPAY_AUTH_INVALID      = "error.billing.sepay.auth_invalid";
```

**File 7: GlobalExceptionHandler.java** (modify in place — add 2 @ExceptionHandler methods)
Add into the existing @RestControllerAdvice class (after the existing handler block, mirror the existing problem(...) helper signature exactly — read the file first to confirm whether problem(...) returns ResponseEntity<ApiError> or ResponseEntity<ProblemDetail> and copy the exact arity):

```java
@ExceptionHandler(com.zeromail.core.billing.model.InsufficientCreditsException.class)
public ResponseEntity<ProblemDetail> onInsufficientCredits(com.zeromail.core.billing.model.InsufficientCreditsException exception) {
    log.warn("Insufficient credits translated to 402: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.PAYMENT_REQUIRED,
        "Insufficient credits",
        "The current tenant balance is too low for this action.",
        ErrorCodes.BILLING_INSUFFICIENT_CREDITS);
}

@ExceptionHandler(com.zeromail.core.billing.model.IllegalLedgerStateException.class)
public ResponseEntity<ProblemDetail> onIllegalLedgerState(com.zeromail.core.billing.model.IllegalLedgerStateException exception) {
    log.error("Illegal ledger state transition translated to 500: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Ledger state invariant violated",
        "An internal billing-state transition was attempted in an invalid order.",
        ErrorCodes.BILLING_LEDGER_INVALID_STATE);
}
```

If HttpStatus.PAYMENT_REQUIRED is unavailable in this Spring version, use HttpStatus.valueOf(402). Verify the helper signature — if problem(...) accepts params: Map<String,Object>, pass Map.of() explicitly to make the privacy invariant visible in the source.

After saving, run `./gradlew :backend:api:compileJava` to ensure no symbol resolution errors.
  </action>
  <verify>
    <automated>./gradlew :backend:api:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q "BILLING_INSUFFICIENT_CREDITS" backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java; grep -q "InsufficientCreditsException" backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java; grep -qE "PAYMENT_REQUIRED|valueOf\(402\)" backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java; test -f backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java; grep -q "static BillingBalanceResponse from" backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java</automated>
  </verify>
  <done>4 DTO records exist; ErrorCodes has 4 new BILLING_* constants; GlobalExceptionHandler has 2 new @ExceptionHandler methods mapping 402 + 500; BillingApiConfiguration activates @EnableConfigurationProperties(BillingProperties.class); ./gradlew :backend:api:compileJava BUILD SUCCESSFUL.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: BillingController + SepayWebhookController + BillingWebhookSecurityConfig + SepayApiKeyAuthFilter</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java,
    backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java,
    backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java,
    backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java
  </files>
  <behavior>
    - BillingController has 2 @RequestMapping endpoints under /api/billing — GET /balance, POST /topup/intent. Inject CreditLedger (interface, not Service) + BillingTopupService. Use TenantContext.currentOrThrow().
    - SepayWebhookController POST /api/billing/sepay/webhook accepts @RequestBody SepayWebhookPayload, delegates to BillingTopupService.applyWebhook(...), returns Map.of("success", true).
    - BillingWebhookSecurityConfig is @Order(1) SecurityFilterChain on /api/billing/sepay/** matcher with permitAll + STATELESS + filter inserted before UsernamePasswordAuthenticationFilter. FilterRegistrationBean disables global servlet wiring.
    - SepayApiKeyAuthFilter extends OncePerRequestFilter; shouldNotFilter returns !getServletPath().startsWith("/api/billing/sepay/"); injects SepayApiKeyVerifier; on rejection sets 401, logs event=sepay_webhook_auth_invalid (no header bytes).
  </behavior>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java (thin-controller analog — copy @RestController @Tag + TenantContext.currentOrThrow() + constructor injection)
    - backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java (webhook controller analog — @PostMapping + @RequestBody envelope + privacy log line)
    - backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java (verbatim shape for BillingWebhookSecurityConfig — three-bean form: filter, FilterRegistrationBean disabled, SecurityFilterChain. Note: PubSub uses @Order(1); BillingWebhook uses @Order(2) — see W6 below)
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (CURRENT @Order(2) line 17 — THIS PLAN bumps to @Order(3) so SepayWebhook can take @Order(2))
    - backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java (verbatim shape for SepayApiKeyAuthFilter — OncePerRequestFilter + shouldNotFilter + setStatus(401) + privacy log)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 555–730 — controller + security + filter excerpts; copy the structure)
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Pattern 3" lines 540–615 — full SepayApiKeyFilter + BillingWebhookSecurityConfig skeleton; §"Critical Override" lines 113–150 — Authorization: Apikey literal prefix; SePay 200 OK {"success":true} response contract)
  </read_first>
  <action>
**File 8: BillingController.java** (api/controllers/billing/)
```java
package com.zeromail.api.controllers.billing;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.TopupIntentRequest;
import com.zeromail.api.dto.billing.TopupIntentResponse;
import com.zeromail.core.billing.model.CreditLedger;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.service.BillingTopupService;
import com.zeromail.core.tenant.TenantContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "billing")
@RequestMapping("/api/billing")
public class BillingController {

    private final CreditLedger creditLedger;
    private final BillingTopupService billingTopupService;

    public BillingController(CreditLedger creditLedger, BillingTopupService billingTopupService) {
        this.creditLedger = creditLedger;
        this.billingTopupService = billingTopupService;
    }

    @GetMapping("/balance")
    public BillingBalanceResponse balance() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return BillingBalanceResponse.from(creditLedger.balance(tenantId));
    }

    @PostMapping("/topup/intent")
    public TopupIntentResponse createIntent(@Valid @RequestBody TopupIntentRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        BillingTopupIntentEntity intent = billingTopupService.createIntent(tenantId, request.amountVnd());
        return new TopupIntentResponse(intent.getCode(), intent.getAmountVnd(), intent.getExpiresAt(), null);
    }
}
```

**File 9: SepayWebhookController.java** (api/controllers/billing/)
```java
package com.zeromail.api.controllers.billing;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.core.billing.service.BillingTopupService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SePay webhook receiver. Authenticated by `Authorization: Apikey` header at the @Order(2)
 * security chain (see BillingWebhookSecurityConfig). Service layer handles all business
 * invariants (replay protection, code lookup, amount validation, privacy-safe event logging).
 *
 * <p><b>OpenAPI visibility (REVIEWS HIGH-3 — RESOLVED):</b> This controller is intentionally
 * NOT {@code @Hidden}. Plan 06's acceptance criteria require {@code paths['/api/billing/sepay/webhook']}
 * to appear in the regenerated {@code apps/web/lib/api/schema.d.ts} so internal tooling
 * (admin replays, integration tests, future ops dashboards) can call it through the typed
 * client. Public-facing risk is mitigated by the API-key auth filter at the security chain
 * level — exposing the path in OpenAPI does not weaken authentication.
 */
@RestController
@Tag(name = "billing-webhook")
public class SepayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SepayWebhookController.class);

    private final BillingTopupService billingTopupService;

    public SepayWebhookController(BillingTopupService billingTopupService) {
        this.billingTopupService = billingTopupService;
    }

    @PostMapping("/api/billing/sepay/webhook")
    public Map<String, Object> receive(@RequestBody SepayWebhookPayload payload) {
        log.info("event=sepay_webhook_received");
        // REVIEWS HIGH-2 NEW: per the SePay webhook spec, `code` is the payment-code field
        // and is the primary intent-resolution input; `referenceCode` is the bank/SMS
        // reference and is audit metadata only. Pass them in that order — service-side
        // extractIntentCode reads `code` first with `content` fallback, NOT `referenceCode`.
        billingTopupService.applyWebhook(
                payload.id(),
                payload.code(),
                payload.referenceCode(),
                payload.content(),
                payload.transferType(),
                payload.transferAmount());
        return Map.of("success", true);
    }
}
```

**File 10: SepayApiKeyAuthFilter.java** (api/security/billing/)
```java
package com.zeromail.api.security.billing;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zeromail.core.billing.service.SepayApiKeyVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SepayApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SepayApiKeyAuthFilter.class);

    private final SepayApiKeyVerifier verifier;

    public SepayApiKeyAuthFilter(SepayApiKeyVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/billing/sepay/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws IOException, ServletException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!verifier.verify(authorizationHeader)) {
            // Never log header bytes — verifier already rejected; we only know it failed.
            if (authorizationHeader == null) {
                log.warn("event=sepay_webhook_auth_missing");
            } else {
                log.warn("event=sepay_webhook_auth_invalid");
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

**File 11: BillingWebhookSecurityConfig.java** (api/security/billing/)
```java
package com.zeromail.api.security.billing;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.zeromail.core.billing.service.SepayApiKeyVerifier;

@Configuration
public class BillingWebhookSecurityConfig {

    @Bean
    SepayApiKeyAuthFilter sepayApiKeyAuthFilter(SepayApiKeyVerifier verifier) {
        return new SepayApiKeyAuthFilter(verifier);
    }

    @Bean
    FilterRegistrationBean<SepayApiKeyAuthFilter> sepayApiKeyAuthFilterRegistration(SepayApiKeyAuthFilter filter) {
        FilterRegistrationBean<SepayApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    SecurityFilterChain sepayWebhookFilterChain(HttpSecurity http, SepayApiKeyAuthFilter filter) throws Exception {
        return http
                .securityMatcher("/api/billing/sepay/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

**W6 — Order assignment + SecurityConfig bump (REQUIRED):**

Current order map in the api module:
- `PubSubSecurityConfig.pubSubFilterChain` — @Order(1) (Phase 2A; matcher `/internal/pubsub/**`)
- `SecurityConfig.chain` — @Order(2) (verified at `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` line 17)

A second `@Order(1)` on `BillingWebhookSecurityConfig` would COLLIDE with PubSubSecurityConfig — Spring resolves ties non-deterministically and one chain would be silently shadowed depending on bean-scan order. Therefore:

1. `BillingWebhookSecurityConfig.sepayWebhookFilterChain` is `@Order(2)` (codified above).
2. THIS PLAN MUST also bump `SecurityConfig.chain` from `@Order(2)` to `@Order(3)` so the user-session chain runs last (catch-all behavior preserved). Edit `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` line 17:

```diff
-    @Order(2)
+    @Order(3)
     SecurityFilterChain chain(HttpSecurity http,
```

Final order: PubSub @Order(1) → Sepay @Order(2) → User-session @Order(3). Each chain has a unique `securityMatcher` so the order only matters for tie-breaking, but the explicit numbering makes the precedence intent unambiguous and prevents future collisions.
  </action>
  <verify>
    <automated>./gradlew :backend:api:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q '@RequestMapping("/api/billing")' backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java; grep -q "creditLedger.balance" backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java; grep -q "/api/billing/sepay/webhook" backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java; grep -q '"success", true' backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java; ! grep -q "@Hidden" backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java; ! grep -q "io.swagger.v3.oas.annotations.Hidden" backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java; grep -q "verifier.verify" backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java; grep -q "@Order(2)" backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java; ! grep -q "@Order(1)" backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java; grep -q "@Order(3)" backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java; ! grep -E "^\s*@Order\(2\)\s*$" backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java; grep -q '"/api/billing/sepay/\*\*"' backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java</automated>
  </verify>
  <done>BillingController has 2 endpoints under /api/billing; SepayWebhookController returns Map.of("success", true); SepayApiKeyAuthFilter delegates to SepayApiKeyVerifier and logs sepay_webhook_auth_invalid; BillingWebhookSecurityConfig declares @Order(1) chain with /api/billing/sepay/** matcher; ./gradlew :backend:api:compileJava BUILD SUCCESSFUL.</done>
</task>

<task type="auto">
  <name>Task 3: application.yml + ApiPostgresTestBase + build.gradle.kts (config + test wiring + openapi-emit)</name>
  <files>
    backend/api/src/main/resources/application.yml,
    backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java,
    backend/api/build.gradle.kts
  </files>
  <read_first>
    - backend/api/src/main/resources/application.yml (existing structure — confirm the zeromail vs zero-mail namespace; Phase 1.5 used zeromail (no hyphen) for crypto/gmail; CONTEXT D-F1 + RESEARCH §"Pattern 5" pin zero-mail.billing (with hyphen) to match BillingProperties prefix. ADD a sibling block — do NOT rename existing entries.)
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java (existing @DynamicPropertySource block; lines 26–52 set zeromail.crypto.refresh-token-key-base64 etc. — append three lines for billing)
    - backend/api/build.gradle.kts (lines 51–60 customBootRun.args from Phase 1.2.1 D-Plan 04 — append the dummy openapi-emit arg per Pitfall 5)
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Pattern 5" lines 723–739 — application.yml block; §"Pitfall 4" lines 843–855 — @DynamicPropertySource pattern; §"Pitfall 5" lines 858–869 — openapi-emit dummy arg)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 814–847 — application.yml + Cross-cutting Pattern 4 lines 1083–1088)
  </read_first>
  <action>
**File 12: backend/api/src/main/resources/application.yml** — append a new top-level `zero-mail:` block (or extend if already present):
```yaml
zero-mail:
  billing:
    sepay:
      webhook-api-key: ${SEPAY_WEBHOOK_API_KEY:?SEPAY_WEBHOOK_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
    vnd-per-credit: 1000
    max-pending-intents-per-tenant: 5
    intent-expiry: PT24H
```

**Verify the existing top-level YAML key style** before appending — if the file has an existing `zeromail:` parent (no hyphen), the new `zero-mail:` (with hyphen) sits alongside as a separate root key. Spring binds @ConfigurationProperties(prefix = "zero-mail.billing") independently of the existing `zeromail.*` prefix.

**File 13: ApiPostgresTestBase.java** — append four @DynamicPropertySource lines:
Inside the existing `static void props(DynamicPropertyRegistry r)` method, append:
```java
r.add("zero-mail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
r.add("zero-mail.billing.vnd-per-credit", () -> "1000");
r.add("zero-mail.billing.max-pending-intents-per-tenant", () -> "5");
r.add("zero-mail.billing.intent-expiry", () -> "PT24H");
```

**File 14: backend/api/build.gradle.kts** — append to existing customBootRun.args (or whatever name the openapi-emit task uses; Phase 1.2.1 D-Plan 04 created it). Add:
```kotlin
"--zero-mail.billing.sepay.webhook-api-key=openapi-emit",
"--zero-mail.billing.vnd-per-credit=1000",
"--zero-mail.billing.max-pending-intents-per-tenant=5",
"--zero-mail.billing.intent-expiry=PT24H",
```
The exact placement depends on the file structure — read backend/api/build.gradle.kts first; the args list is likely a `listOf("--key=value", ...)` block on the springdoc-openapi gradle plugin's customBootRun extension. If the project uses a different mechanism (e.g., `apiBootRun` task with `args` list), append there instead. Goal: `./gradlew :backend:api:openApi` (hermetic OpenAPI emit) MUST boot without :? fail-fast crashing on the new env var.

After all three changes saved, run `./gradlew :backend:api:openApi` to confirm hermetic OpenAPI emission still succeeds.
  </action>
  <verify>
    <automated>grep -q "SEPAY_WEBHOOK_API_KEY:?" backend/api/src/main/resources/application.yml; grep -q "vnd-per-credit: 1000" backend/api/src/main/resources/application.yml; grep -q "test-sepay-key-fixture" backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java; grep -q "zero-mail.billing.sepay.webhook-api-key=openapi-emit" backend/api/build.gradle.kts; ./gradlew :backend:api:openApi 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>application.yml has zero-mail.billing block with :? fail-fast on SEPAY_WEBHOOK_API_KEY; ApiPostgresTestBase injects all 4 zero-mail.billing.* test values; build.gradle.kts customBootRun.args includes the openapi-emit dummy values so hermetic OpenAPI emission still succeeds; ./gradlew :backend:api:openApi BUILD SUCCESSFUL.</done>
</task>

<task type="auto">
  <name>Task 4a: i18n bundles (vi+en) + apps/web/lib/api/schema.d.ts regen (W5 — split)</name>
  <files>
    apps/web/i18n/messages/vi.json,
    apps/web/i18n/messages/en.json,
    apps/web/lib/api/schema.d.ts
  </files>
  <read_first>
    - apps/web/i18n/messages/vi.json (existing structure — find or add error.billing namespace; existing error.* keys may be under nested `error: { billing: { insufficient: "..." } }` or flat dotted-key — match exactly the existing convention)
    - apps/web/i18n/messages/en.json (mirror the vi.json key tree; both files MUST have the same leaf-key set)
    - apps/web/scripts/check-i18n.ts (verify whether new keys need EN_SCAN_FILES additions for any new feature folder; in 2B no new frontend feature folder — schema.d.ts only — so EN_SCAN_FILES likely needs no changes)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md ("Claudes Discretion" lines 154–156 — i18n copy: vi="Số dư tín dụng không đủ — vui lòng nạp thêm để tiếp tục", en="Insufficient credits — top up to continue")
  </read_first>
  <action>
**Files 15+16: i18n bundles.** Add the 4 new keys to BOTH vi.json and en.json. Match the existing JSON structure (nested vs flat dotted) — if existing `error.gmail.disconnected` is at `error: { gmail: { disconnected: "..." } }`, place new keys at:
```
error: {
  billing: {
    insufficient: "...",
    ledger: { invalidState: "..." },
    sepay: { reference_invalid: "...", auth_invalid: "..." }
  }
}
```
If flat dotted, use the literal keys.

vi.json copy (CONTEXT verbatim where given, sane VI for the rest):
- error.billing.insufficient = "Số dư tín dụng không đủ — vui lòng nạp thêm để tiếp tục."
- error.billing.ledger.invalidState = "Lỗi nội bộ trong sổ tín dụng — vui lòng thử lại sau."
- error.billing.sepay.reference_invalid = "Mã tham chiếu giao dịch không hợp lệ."
- error.billing.sepay.auth_invalid = "Yêu cầu webhook không hợp lệ."

en.json:
- error.billing.insufficient = "Insufficient credits — top up to continue."
- error.billing.ledger.invalidState = "Internal billing-state error — please try again later."
- error.billing.sepay.reference_invalid = "Invalid transaction reference code."
- error.billing.sepay.auth_invalid = "Invalid webhook request."

After saving, run `pnpm i18n:check` STRICT — must pass (parity between vi/en).

**File 17: apps/web/lib/api/schema.d.ts regen.**
Run from repo root:
```
./gradlew :backend:api:openApi
pnpm --filter web generate:api
```
This regenerates apps/web/lib/api/schema.d.ts to include `paths['/api/billing/balance']`, `paths['/api/billing/topup/intent']`, `paths['/api/billing/sepay/webhook']` entries with proper request/response types pulled from the springdoc OpenAPI emit. Commit the regenerated file.

Smoke check the openapi-emit pipeline does not regress:
```
./gradlew :backend:api:openApi 2>&1 | tail -20
```
should print "BUILD SUCCESSFUL" — the customBootRun.args block from Task 3 supplies the dummy SEPAY env so the boot does not crash on :?.
  </action>
  <verify>
    <automated>grep -q "insufficient" apps/web/i18n/messages/vi.json; grep -q "insufficient" apps/web/i18n/messages/en.json; grep -q "/api/billing/balance" apps/web/lib/api/schema.d.ts; grep -q "/api/billing/sepay/webhook" apps/web/lib/api/schema.d.ts; pnpm i18n:check; ./gradlew :backend:api:openApi 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>vi.json and en.json both have the 4 error.billing.* keys; pnpm i18n:check STRICT passes; apps/web/lib/api/schema.d.ts contains paths for /api/billing/balance + /topup/intent + /sepay/webhook; ./gradlew :backend:api:openApi BUILD SUCCESSFUL.</done>
</task>

<task type="auto">
  <name>Task 4b: Flip Wave 0 api-tests off @Disabled (W5 — split; 8 files including SepayWebhookMismatchAuditEventTest from Plan 00 W7)</name>
  <files>
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
  </files>
  <read_first>
    - All 8 Wave 0 api-test files (Plan 00 Task 2 output, including the new SepayWebhookMismatchAuditEventTest from W7) — flip @Disabled off
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (Task 2 output — verify endpoints exist before flipping)
    - backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java (Task 2 output — verify @Order(2) wired before flipping SepayBadAuthTest)
  </read_first>
  <action>
For each of the 8 files in `backend/api/src/test/java/com/zeromail/api/controllers/billing/` (Plan 00 outputs — count is 8 because Plan 00 added `SepayWebhookMismatchAuditEventTest` per W7), remove the `@Disabled("Wave 0 RED scaffold — production class lands in Plan 04")` annotation lines. Tests run live now.

For BillingInsufficientCreditsTest — Plan 00 declared the test wraps reserve via a test-only @RestController. Either:
- Add the test controller as a static @TestConfiguration nested inside the test class with a @PostMapping("/test/billing/reserve") endpoint that calls creditLedger.reserve(...) and returns 200 (any response) — Spring's exception handler converts the InsufficientCreditsException → 402 + ApiError automatically.
- OR call BillingController's existing endpoints if any of them invoke reserve() in a path that is reachable in 2B (in 2B, none do — Phase 2C is the first caller). The test-only controller is the simpler path.

For SepayWebhookMismatchAuditEventTest — confirm the logback capture appender is the same one Plan 00 referenced for BillingPrivacyLogScrubTest (likely `@Import(LogbackTestCaptureConfig.class)` or an in-memory appender). The mismatch test asserts the OPPOSITE expectation from the scrub test (numbers MUST appear in this one event line), so a shared capture mechanism is fine.

Run `./gradlew :backend:api:test --tests "com.zeromail.api.controllers.billing.*"` — all 8 must pass. If any test failure surfaces, fix in this plan (do not defer).
  </action>
  <verify>
    <automated>find backend/api/src/test/java/com/zeromail/api/controllers/billing -name "*.java" | wc -l returns 8; ! grep -rE '@Disabled.*Wave 0' backend/api/src/test/java/com/zeromail/api/controllers/billing/; ./gradlew :backend:api:test --tests "com.zeromail.api.controllers.billing.*" 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>No @Disabled("Wave 0...") annotations remain in backend/api/src/test/java/com/zeromail/api/controllers/billing/*.java (all 8 files); ./gradlew :backend:api:test BUILD SUCCESSFUL with all 8 Wave 0 api-tests GREEN — including the new SepayWebhookMismatchAuditEventTest covering D-I1 dedicated mismatch coverage.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Public HTTPS → SepayWebhookController | Untrusted POST from SePay infrastructure; @Order(1) chain rejects without valid Authorization: Apikey before any business logic runs. |
| Browser session → BillingController | Authenticated tenant session; TenantContext binding from session-auth chain ensures cross-tenant reads impossible. |
| Frontend i18n bundle → user | Both vi.json and en.json must carry every error.billing.* key — partial bundles cause untranslated keys to leak to the UI. STRICT i18n:check enforces parity. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-04-01 | Spoofing | SePay webhook forgery (T1) | mitigate | @Order(1) SecurityFilterChain on /api/billing/sepay/** runs SepayApiKeyAuthFilter before any controller; SepayApiKeyVerifier uses MessageDigest.isEqual; SEPAY_WEBHOOK_API_KEY :? fail-fast at boot. |
| T-02B-04-02 | Information disclosure | Webhook key in logs (T5) | mitigate | SepayApiKeyAuthFilter only logs event=sepay_webhook_auth_invalid / event=sepay_webhook_auth_missing — never the header bytes. SepayWebhookController logs event=sepay_webhook_received with no payload bytes. Wave 0 BillingPrivacyLogScrubTest verifies no `Apikey ` substring leaks. |
| T-02B-04-03 | Information disclosure | Cross-tenant balance read (T4) | mitigate | BillingController.balance() reads TenantContext.currentOrThrow() from the SecurityContext-bound ScopedValue — never from request body / path. ArchUnit guard (Plan 06) bans @PathVariable UUID tenantId on BillingController. Wave 0 BillingBalanceMultiTenantLeakTest verifies via 10 concurrent virtual-thread requests. |
| T-02B-04-04 | Information disclosure | InsufficientCredits balance leak | mitigate | GlobalExceptionHandler.onInsufficientCredits passes Map.of() → no balance number in `params`; ApiError body shape verified by Wave 0 BillingInsufficientCreditsTest. |
| T-02B-04-05 | Denial of service | Intent-table flood (T7) | mitigate | BillingTopupService.createIntent (Plan 03) caps at 5 PENDING per tenant (auto-expire-oldest on 6th create per D-C2). |
| T-02B-04-06 | Tampering | i18n bundle drift | mitigate | pnpm i18n:check STRICT (Phase 1.3 Plan 07 closure) blocks commits where vi.json and en.json key sets differ. |
</threat_model>

<verification>
- 17 files modified at the declared paths (4 DTOs, 4 security/controller, 1 BillingApiConfiguration, ErrorCodes, GlobalExceptionHandler, application.yml, ApiPostgresTestBase, build.gradle.kts, vi.json, en.json, schema.d.ts).
- Wave 0 api-tests (7 files) flipped from @Disabled → @Test and all GREEN.
- ./gradlew :backend:api:check BUILD SUCCESSFUL.
- ./gradlew :backend:api:openApi BUILD SUCCESSFUL — hermetic emit picks up the new endpoints.
- pnpm --filter web generate:api regenerates schema.d.ts.
- pnpm i18n:check STRICT BUILD SUCCESSFUL.
</verification>

<success_criteria>
- 17 files committed; tests + build all GREEN.
- Frontend can typedly call the 3 new billing endpoints.
- Phase 5 future work has a stable contract to consume (no more shape drift expected).
- All Wave 0 api-tests durable GREEN gates.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-04-SUMMARY.md`.
</output>
