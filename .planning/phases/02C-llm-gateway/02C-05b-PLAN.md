---
phase: 02C-llm-gateway
plan: 05b
type: execute
wave: 5
depends_on: [05a]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java
  - backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokCurrentResponse.java
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/ByokServiceTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokControllerIntegrationTest.java
autonomous: true
requirements: [LLM-03]
must_haves:
  truths:
    - "POST /api/llm/byok/validate accepts {provider, endpoint?, apiKey} and issues a server-side probe (GET /v1/models for openai-compatible, POST /v1/messages with max_tokens=1 for anthropic); returns {ok, models?, reason?}; the browser NEVER issues the validate call directly"
    - "POST /api/llm/byok saves only after the same payload validates; encrypts the key via existing RefreshTokenCipher (envelope = [key_version:int32 | nonce:12 | ciphertext], tenantId-bound AAD), upserts into tenant_byok_credentials"
    - "GET /api/llm/byok returns provider, optional endpoint host (just the host, no path/query), saved timestamp; NEVER returns decrypted key bytes"
    - "ByokService.validate(...) and ByokService.save(...) call ByokEndpointValidator (from Plan 05a) BEFORE issuing any outbound probe or persisting; SSRF allow-list rejects metadata-IP / RFC1918 / non-vendor-host endpoints (H-4)"
    - "GlobalExceptionHandler maps SafetyViolationException → 500 LLM_SAFETY_VIOLATION; SanitizationException → 500 LLM_SANITIZATION_FAILED; InvalidByokException → 400 LLM_BYOK_INVALID; existing 402 InsufficientCreditsException mapping is preserved"
    - "ErrorCodes constants added: LLM_SAFETY_VIOLATION, LLM_SANITIZATION_FAILED, LLM_BYOK_INVALID, LLM_BYOK_VALIDATE_FAILED"
    - "Privacy log on BYOK validate: event=byok_validate_attempted tenantId={} provider={} (no endpoint URL, no key bytes); event=byok_validate_succeeded tenantId={} provider={} modelsCount={}; event=byok_validate_failed tenantId={} provider={} reason={opaqueClass}"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java"
      provides: "@Service — validate(tenantId, payload) probes upstream provider; save(tenantId, payload) encrypts + upserts; current(tenantId) returns metadata-only DTO"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java"
      provides: "Thin REST controller — delegates everything to ByokService"
      contains: '@RequestMapping("/api/llm/byok")'
    - path: "backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java"
      provides: "4 new constants: LLM_SAFETY_VIOLATION, LLM_SANITIZATION_FAILED, LLM_BYOK_INVALID, LLM_BYOK_VALIDATE_FAILED"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java"
      to: "RefreshTokenCipher (existing core.gmail.persistence.crypto bean)"
      via: "encrypt(plaintextKey, tenantId) on save; ciphertext stored as encrypted_key BYTEA"
      pattern: "refreshTokenCipher\\.encrypt"
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java"
      to: "backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java"
      via: "validator.validateOpenAiCompatible(endpoint) / validator.validateAnthropic(endpoint) called BEFORE any outbound probe"
      pattern: "byokEndpointValidator\\.validate"
    - from: "backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java"
      to: "backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java"
      via: "InvalidByokException + SafetyViolationException + SanitizationException mappings"
      pattern: "@ExceptionHandler"
---

<objective>
Wave 4 BYOK REST surface (M-2 split — second of two wave-4 BYOK plans). Land `ByokService` (validate / save / current), `ByokController` (3 endpoints), 5 DTOs, 4 ErrorCodes constants, and 3 GlobalExceptionHandler mappings (SafetyViolationException, SanitizationException, InvalidByokException — InsufficientCreditsException already mapped from Phase 2B).

Purpose: this is the user-facing half of LLM-03. After this plan, a tenant can install / replace / inspect their BYOK credentials via the typed API surface. Plan 08 consumes this from the frontend. Plan 05a's `BYOKChatModelFactory` impls + `ByokEndpointValidator` + `LlmGatewayImpl` BYOK branch are already in place — this plan only adds the REST surface and wires `ByokService` to call `ByokEndpointValidator` BEFORE issuing any outbound probe (closes the SSRF surface end-to-end, per H-4).

Output: `ByokService` + `ByokController` + 5 DTOs + GlobalExceptionHandler mappings + ErrorCodes + 2 test files.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-RESEARCH.md
@.planning/phases/02C-llm-gateway/02C-AI-SPEC.md
@.planning/phases/02C-llm-gateway/02C-UI-SPEC.md
@.planning/phases/02C-llm-gateway/02C-05a-SUMMARY.md
@backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
@backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
@backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java
@backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsRepository.java
@backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java
@backend/core/src/main/java/com/zeromail/core/llm/model/BYOKProvider.java
@backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java
@backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java

<interfaces>
<!-- From Plan 01 -->
- `TenantByokCredentialsRepository#findByTenantId(UUID) → Optional<TenantByokCredentialsEntity>`
- `TenantByokCredentialsEntity` ctor `(UUID id, UUID tenantId, BYOKProvider provider, String endpoint, byte[] encryptedKey, short keyVersion)`; mutator `replaceKey(byte[] envelope, short keyVersion)`.
- `BYOKProvider` enum `{ANTHROPIC("anthropic"), OPENAI_COMPATIBLE("openai-compatible")}`.

<!-- From Plan 05a -->
- `com.zeromail.core.llm.byok.ByokEndpointValidator#validateAnthropic(String endpoint) → String` — returns canonicalized URL or throws `InvalidByokException`.
- `com.zeromail.core.llm.byok.ByokEndpointValidator#validateOpenAiCompatible(String endpoint) → String` — same shape.
- `com.zeromail.core.llm.model.InvalidByokException` — no-arg constructor; redacted (no endpoint echo).

<!-- Existing reusable -->
- `com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher#encrypt(byte[] plaintext, String tenantId) → byte[]`
- `com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher#decrypt(byte[] envelope, String tenantId) → byte[]`
- Existing 402 mapping for `InsufficientCreditsException` in GlobalExceptionHandler (Phase 2B; preserved here).

<!-- Validate flow (SPEC #4) -->
- OpenAI-compatible: `GET ${endpoint}/v1/models` with `Authorization: Bearer ${apiKey}` → 200 + `{ data: [{id: "..."}, ...] }`.
- Anthropic: `POST ${endpoint or default}/v1/messages` with `x-api-key: ${apiKey}` + `anthropic-version: 2023-06-01` body `{model, max_tokens: 1, messages: [{role: "user", content: "."}]}` → 200 = valid.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ByokService + ByokController + 5 DTOs + GlobalExceptionHandler mappings + ErrorCodes + integration test</name>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (controller analog — PATTERNS.md "ByokController.java")
    - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentRequest.java (DTO record analog)
    - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentResponse.java (DTO record analog)
    - backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java (current-config DTO analog — PATTERNS.md "ByokCurrentResponse.java")
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java (lines 105-148 — privacy-safe exception logging + 402 mapping for InsufficientCreditsException to PRESERVE)
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java (existing constants — append pattern)
    - backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java (service analog with outbound HTTP call shape — PATTERNS.md "ByokService.java")
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java (controller integration test analog with WireMock-like upstream stub)
    - backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java (Plan 05a — wire BEFORE outbound probe)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-D1 through D-D6 — frontend; D-A5 cipher reuse; D-I2 byok logs)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "ByokController.java" + "GlobalExceptionHandler.java (modify)" + S-2 thin-controller)
    - .planning/phases/02C-llm-gateway/02C-UI-SPEC.md (Section "Copywriting Contract" + "i18n Keys")
  </read_first>
  <behavior>
    - Test 1 (ByokServiceTest#validate_openai_compatible_calls_v1_models): WireMock stub at `https://together.xyz/v1/models` returning 200 with `{data: [{id: "model-a"}, {id: "model-b"}]}`; ByokService.validate(tenantId, payload) returns `ByokValidateResponse(ok=true, models=["model-a","model-b"], reason=null)`. (Operator opt-in flag must be `true` for `together.xyz` to pass the validator — set in test.)
    - Test 2 (ByokServiceTest#validate_openai_compatible_failure): WireMock returns 401 → returns `ByokValidateResponse(ok=false, models=null, reason="upstream_rejected")` — reason is opaque (no upstream body bytes).
    - Test 3 (ByokServiceTest#validate_anthropic_calls_v1_messages): WireMock at `/v1/messages` POST with `max_tokens=1` body returning 200 → returns `ByokValidateResponse(ok=true, models=null, reason=null)`.
    - Test 4 (ByokServiceTest#save_encrypts_key_via_refresh_token_cipher): given a valid payload, save() stores a row whose encrypted_key BYTEA is NOT the plaintext key bytes; manual decrypt via RefreshTokenCipher returns the original plaintext.
    - Test 5 (ByokServiceTest#save_upserts_existing_row): tenant already has BYOK row → second save() updates encrypted_key + key_version + provider, does NOT create a duplicate (UNIQUE constraint enforced from Plan 01).
    - Test 6 (ByokServiceTest#current_returns_metadata_only): tenant with BYOK row → ByokCurrentResponse contains provider, optional endpointHost (e.g., `together.xyz` extracted from URL — no path/query), savedAt; encrypted_key bytes NEVER returned.
    - Test 7 (H-4 ByokServiceTest#anthropic_save_rejects_metadata_endpoint): `payload.endpoint="http://169.254.169.254/v1"` → save() throws `InvalidByokException` BEFORE any DB write.
    - Test 8 (H-4 ByokServiceTest#anthropic_save_rejects_rfc1918_endpoint): `payload.endpoint="http://10.0.0.5/v1"` → throws.
    - Test 9 (H-4 ByokServiceTest#anthropic_save_rejects_non_anthropic_host): `payload.endpoint="https://example.com"` with provider=ANTHROPIC → throws.
    - Test 10 (H-4 ByokServiceTest#openai_compat_accepts_when_operator_opt_in): `zeromail.llm.byok.allow-non-vendor-endpoints=true` + `payload.endpoint="https://together.xyz/v1"` + provider=OPENAI_COMPATIBLE → save() succeeds (after upstream probe also succeeds).
    - Test 11 (ByokControllerIntegrationTest#post_validate_returns_200_for_valid_key): full HTTP test via RestClient + LocalServerPort + TenantContext-binding test filter; asserts 200 + body shape.
    - Test 12 (ByokControllerIntegrationTest#post_save_returns_400_when_invalid_byok_exception_thrown): mock service to throw InvalidByokException → assert 400 + body code=`error.llm.byok.invalid`.
    - Test 13 (ByokControllerIntegrationTest#safety_violation_handler_returns_500): synthetic SafetyViolationException through a stub controller → assert 500 + body code=`error.llm.safety_violation`.
    - Test 14 (ByokControllerIntegrationTest#sanitization_failed_handler_returns_500): synthetic SanitizationException → 500 + code=`error.llm.sanitization_failed`.
    - Test 15 (ByokControllerIntegrationTest#insufficient_credits_still_returns_402): existing Phase 2B mapping preserved.
  </behavior>
  <action>
    1. **Create 5 DTOs** in `backend/api/src/main/java/com/zeromail/api/dto/llm/`:
       - `ByokValidateRequest(BYOKProvider provider, String endpoint, String apiKey)` — `@NotNull` on provider + apiKey; endpoint nullable. JSR-380 validation.
       - `ByokValidateResponse(boolean ok, List<String> models, String reason)` — record with defensive copy on models; reason is opaque (`"upstream_rejected"`, `"connection_failed"`, `"timeout"`, `"endpoint_rejected"`) — never raw upstream body.
       - `ByokSaveRequest(BYOKProvider provider, String endpoint, String apiKey)` — same shape.
       - `ByokSaveResponse(boolean ok, Instant savedAt)` — minimal.
       - `ByokCurrentResponse(BYOKProvider provider, String endpointHost, Instant savedAt)` — `endpointHost` is extracted via `URI.create(endpoint).getHost()`; null if no BYOK row.

       All as Java records per CLAUDE.md Conventions §2.

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java`** — `@Service`. Methods:
       - `validate(UUID tenantId, ByokValidateRequest payload)`:
         - **(H-4)** First: `String canonicalEndpoint = byokEndpointValidator.validate{Anthropic,OpenAiCompatible}(payload.endpoint())` per provider — throws `InvalidByokException` on SSRF / non-HTTPS / non-vendor host before any outbound HTTP.
         - Issues outbound HTTP via Spring `RestClient` (see `BillingTopupService` for analog). Branch on provider:
           - OPENAI_COMPATIBLE → `GET ${canonicalEndpoint}/v1/models` with `Authorization: Bearer ${payload.apiKey()}`. On 200 → parse → `ByokValidateResponse(true, modelIds, null)`. On non-2xx → `ByokValidateResponse(false, null, "upstream_rejected" | "connection_failed")`.
           - ANTHROPIC → `POST ${canonicalEndpoint}/v1/messages` with `x-api-key`, `anthropic-version: 2023-06-01`, body `{model: "claude-3-haiku-20240307", max_tokens: 1, messages: [{role: "user", content: "."}]}`. On 200 → ok=true.
         - All exceptions caught and translated to opaque reason — NEVER include upstream response body bytes in reason.
         - Privacy logs: `event=byok_validate_attempted tenantId={} provider={}`, `event=byok_validate_succeeded tenantId={} provider={} modelsCount={}`, `event=byok_validate_failed tenantId={} provider={} reason={}` (opaque tag, NOT exception message).
       - `save(UUID tenantId, ByokSaveRequest payload)`:
         - **(H-4)** First: re-run `byokEndpointValidator.validate{Anthropic,OpenAiCompatible}` on the endpoint. (Validate is also called in `validate(...)` but that's not a transactional barrier — re-run on save is the durable check that survives client behavior.)
         - Encrypts `payload.apiKey().getBytes(UTF_8)` via `refreshTokenCipher.encrypt(plaintext, tenantId.toString())`.
         - Upserts entity (find existing → mutate via `replaceKey(envelope, keyVersion)` or save new). Returns `ByokSaveResponse(true, Instant.now())`.
         - **Decision**: server-side accepts save without re-running the upstream probe (UI-SPEC says client gates the save button; server-side re-validate would double the cost). Document in service Javadoc.
       - `current(UUID tenantId)` — returns `Optional<ByokCurrentResponse>`; null if no row. Extracts host from endpoint via `URI.create(...).getHost()`. Never decrypts the key.
       - **`@Transactional`** on save (mutation) and current (read consistency). Validate is non-transactional.

    3. **Create `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java`** — `@RestController @RequestMapping("/api/llm/byok") @Tag(name="llm-byok")`. Per PATTERNS.md verbatim shape — thin controller, 3 endpoints (`POST /validate`, `POST` (save), `GET` (current)), `TenantContext.currentOrThrow()` per call, `byokService.{validate,save,currentForTenant}(...)`. NO `@Transactional`, NO repository injection.

    4. **Modify `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`** — append 3 new `@ExceptionHandler` methods after the existing `InsufficientCreditsException` one. PATTERNS.md "GlobalExceptionHandler.java (modify)" gives the pattern verbatim. Privacy invariant: `log.error("event=... reason={}", exception.getClass().getSimpleName())` — NEVER pass the exception object itself, NEVER pass `.getMessage()`.
       - `SafetyViolationException` → 500, code `LLM_SAFETY_VIOLATION`
       - `SanitizationException` → 500, code `LLM_SANITIZATION_FAILED`
       - `InvalidByokException` → 400, code `LLM_BYOK_INVALID`
       Preserve the existing `InsufficientCreditsException → 402` mapping verbatim.

    5. **Modify `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`** — append 4 constants:
       ```java
       public static final String LLM_SAFETY_VIOLATION = "error.llm.safety_violation";
       public static final String LLM_SANITIZATION_FAILED = "error.llm.sanitization_failed";
       public static final String LLM_BYOK_INVALID = "error.llm.byok.invalid";
       public static final String LLM_BYOK_VALIDATE_FAILED = "error.llm.byok.validate_failed";
       ```

    6. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/ByokServiceTest.java`** — Tests 1–10 above. WireMock (or Spring's `MockRestServiceServer`) for outbound HTTP stubs. Test 5 verifies UNIQUE upsert. Tests 7–10 cover H-4.

    7. **Create `backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokControllerIntegrationTest.java`** — RestClient + LocalServerPort + TenantContext-binding test pattern. Tests 11–15 above. Test 15 specifically asserts the existing 402 mapping for InsufficientCreditsException is unaffected by the new mappings.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "ByokServiceTest" :backend:api:test --tests "ByokControllerIntegrationTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 5 DTOs exist under `backend/api/src/main/java/com/zeromail/api/dto/llm/`.
    - `grep -c "public record " backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateRequest.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateResponse.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveRequest.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokCurrentResponse.java` returns `5`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` exists; `grep -c "refreshTokenCipher.encrypt" backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` returns `>= 1`.
    - `grep -c "byokEndpointValidator\.validate" backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` returns `>= 2` (called in both validate() and save() — H-4).
    - `grep -E "log\.(info|warn|error|debug).*payload\.apiKey\(\)|log\.(info|warn|error|debug).*payload\.endpoint\(\)" backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` returns no matches (no key/endpoint URL in logs).
    - File `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` exists; `grep -c "@RequestMapping.*api/llm/byok" backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` returns `1`; `grep -cE "@PostMapping|@GetMapping" backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` returns `>= 3`.
    - `grep -c "@ExceptionHandler(SafetyViolationException.class)\|@ExceptionHandler(SanitizationException.class)\|@ExceptionHandler(InvalidByokException.class)" backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` returns `3`.
    - `grep -c "@ExceptionHandler(InsufficientCreditsException.class)" backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` returns `1` (preserved).
    - `grep -c "LLM_SAFETY_VIOLATION\|LLM_SANITIZATION_FAILED\|LLM_BYOK_INVALID\|LLM_BYOK_VALIDATE_FAILED" backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` returns `4`.
    - `./gradlew :backend:core:test --tests "ByokServiceTest"` exits 0 — including H-4 tests (7–10).
    - `./gradlew :backend:api:test --tests "ByokControllerIntegrationTest"` exits 0.
    - `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 (full suite).
  </acceptance_criteria>
  <done>
    BYOK service + controller + 5 DTOs + 4 ErrorCodes constants + 3 GlobalExceptionHandler mappings land. Server-side validate flow probes upstream provider with no body leakage; save encrypts via existing RefreshTokenCipher and runs ByokEndpointValidator (H-4) BEFORE persisting; current returns metadata only. Frontend (Plan 08) can now consume the typed schema.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → POST /api/llm/byok/validate | Raw API key crosses; backend issues outbound probe; key is in-memory only for the request scope. |
| ByokService → upstream provider (OpenAI-compat / Anthropic) | Outbound HTTP carries the plaintext key in Authorization / x-api-key header. Validate response body MUST NOT leak into application logs or error responses. |
| ByokService → ByokEndpointValidator | All user-supplied endpoints must pass the SSRF allow-list before any outbound probe or DB write (H-4 — closed in Plan 05a; enforced here at the entry points). |
| RefreshTokenCipher boundary | Plaintext key crosses only into the cipher and back into the per-call `mutate()` builder argument (Plan 05a); never persisted in plaintext. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-03 | Information Disclosure (BYOK key leakage in logs / DB / error traces / metrics) | ByokService + GlobalExceptionHandler | mitigate | (1) `event=byok_validate_attempted tenantId={} provider={}` — no endpoint, no key. (2) GlobalExceptionHandler logs `exception.getClass().getSimpleName()` only — never the exception object or its message. (3) BYOK `encrypted_key BYTEA` always encrypted via `RefreshTokenCipher` envelope. (4) `ByokServiceTest#validate_openai_compatible_failure` asserts the upstream response body is NOT in the reason field. (5) Logback scrub filter from Phase 1 covers `apiKey=`, `Bearer`, `x-api-key=` — verify in Plan 07; extend if gaps found. |
| T-2C-09 | Spoofing / SSRF | ByokService.{validate,save} entry points | mitigate | H-4 — `byokEndpointValidator.validate{Anthropic,OpenAiCompatible}(payload.endpoint())` is the FIRST call in both methods. Tests `anthropic_save_rejects_metadata_endpoint`, `anthropic_save_rejects_rfc1918_endpoint`, `anthropic_save_rejects_non_anthropic_host`, `openai_compat_accepts_when_operator_opt_in` verify the gates. Rejected endpoint never reaches outbound HTTP, never reaches the DB. |
| T-2C-cipher-aad-mismatch | Tampering | RefreshTokenCipher reuse | mitigate | `tenantId.toString()` is the AAD passed to encrypt + decrypt — same value both sides per RefreshTokenCipher contract. ByokServiceTest#save asserts. If the AAD is wrong (e.g., another tenant's UUID), decrypt throws `AEADBadTagException` and the gateway call fails — zero risk of reading another tenant's key. |
| T-2C-byok-host-leak-in-current | Information Disclosure | ByokCurrentResponse | mitigate | Endpoint URL is parsed via `URI.create(endpoint).getHost()` — only the host, not the full URL with paths/queries. ByokServiceTest#current_returns_metadata_only asserts. |
| T-2C-validate-flow-amplification | DoS | POST /api/llm/byok/validate | accept | Endpoint is authenticated (TenantContext required) and rate-limited at the existing API filter chain. Upstream provider imposes their own rate limits. No additional gateway-side rate limit in v1. |
| T-2C-globalexceptionhandler-content-leak | Information Disclosure | New @ExceptionHandler methods | mitigate | All 3 new mappings follow the existing Phase 2B / Phase 1.1 pattern: `log.error("event=... reason={}", exception.getClass().getSimpleName())`. ByokControllerIntegrationTest#safety_violation_handler_returns_500 asserts the response body code is `error.llm.safety_violation` (no rejected action name, no model output). |
</threat_model>

<verification>
> Run all grep / shell acceptance checks via Git Bash (bash.exe), not PowerShell.

- `./gradlew :backend:core:test --tests "ByokServiceTest"` exits 0
- `./gradlew :backend:api:test --tests "ByokControllerIntegrationTest"` exits 0 (RestClient + LocalServerPort pattern verified)
- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 — full module test suite green
- ArchUnit `LlmGatewayBoundaryTest` + `DomainBoundaryArchTests` both pass — no Spring AI imports, new ByokController + DTOs don't introduce cross-module repo deps
- The existing 402 mapping for InsufficientCreditsException is unchanged (test asserts)
</verification>

<success_criteria>
- 3 endpoints (`POST /validate`, `POST` save, `GET` current) exposed under `/api/llm/byok`; thin controller delegates to ByokService.
- 4 ErrorCodes + 3 GlobalExceptionHandler mappings preserve privacy invariant (logger gets class name only).
- ByokService runs `ByokEndpointValidator` BEFORE any outbound HTTP / DB write (H-4 closed end-to-end).
- All ByokServiceTest (10 tests) + ByokControllerIntegrationTest (5 tests) assertions pass.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-05b-SUMMARY.md` documenting:
- Whether the Logback scrub filter needed extending to cover `Bearer ` / `x-api-key=` / `apiKey=` patterns (Plan 07 follow-up if so)
- The exact regex used by `URI.create(endpoint).getHost()` extraction for `endpointHost`
- Pointer for Plan 08: `apps/web/lib/api/schema.d.ts` should be regenerated via `pnpm generate:api` to pick up `/api/llm/byok/{validate,(save),(current)}` types
</output>
</content>
</invoke>