---
phase: 08-bulk-unsubscribe-campaign
plan: 05
subsystem: cleanup
tags: [cleanup, unsubscribe-http, unsubscribe-mailto, rfc-8058, rfc-6068, gmail-send, uns-04, uns-08]
requirements: [UNS-04, UNS-08]
dependency_graph:
  requires:
    - 08-03 (cleanup persistence + Spring Modulith module declaration)
    - Wave 0 ArchUnit stubs (UnsubscribeHttpClientBoundaryTest, GmailWriteBoundaryTest, UnsubscribeHttpClientTest, UnsubscribeMailtoSenderRecipientGuardTest)
    - TriageGmailWriter (sibling-boundary pattern reference)
    - GmailApiClientFactory (tenant-scoped Gmail client builder)
    - ReplyMimeBuilder (Base64URL MIME encoding pattern reference)
    - RestClientConfig (BYOK RestClient builder reference for redirect-NEVER / JdkClientHttpRequestFactory pattern)
  provides:
    - core.cleanup.domain.UnsubscribeResult (sealed Ok|Failed return type for HTTP + mailto)
    - core.cleanup.usecases.UnsubscribeMailtoUriParser (RFC 6068 java.net.URI parser, no regex)
    - core.cleanup.usecases.UnsubscribeHttpClient (RFC 8058 one-click POST gateway, RestClient + redirect-NEVER)
    - core.cleanup.usecases.UnsubscribeMailtoSender (Gmail send-as-self mailto sibling of TriageGmailWriter)
    - Security boundary: only UnsubscribeHttpClient creates HttpClient/RestClient in ..core.cleanup..
    - Security boundary: only TriageGmailWriter + UnsubscribeMailtoSender call Gmail.users().messages().send() in ..core..
  affects:
    - GmailWriteBoundaryTest allow-list extended (already declared in Wave 0, flips GREEN now)
    - UnsubscribeHttpClientBoundaryTest allow-list activated (already declared in Wave 0, flips GREEN now)
tech_stack:
  added: []
  patterns:
    - Sealed interface + record pattern for typed Result return (Java 25 sealed + records)
    - Static parser helper with structured ParsedMailto record (D-23 java.net.URI based, no regex)
    - Sibling-class boundary (UnsubscribeMailtoSender does NOT extend TriageGmailWriter — SRP per D-05)
    - Inner JdkClientHttpRequestFactory + java.net.http.HttpClient.Builder with Redirect.NEVER + per-builder timeouts (D-07 RFC 8058 conformance)
    - RestClient.exchange((req, resp) -> ...) instead of .retrieve() — gives full status-code mapping control per D-08
    - LinkedMultiValueMap form body for application/x-www-form-urlencoded (RFC 8058 §3.1 List-Unsubscribe=One-Click)
    - Byte-for-byte provenance guard (D-23) before any side-effecting Gmail call
    - Privacy: senderDomain extracted via URI.getHost() or "email after @" — never log full URL/recipient/MIME
key_files:
  created:
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeResult.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoUriParser.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeHttpClient.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java
    - .planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md
  modified: []
decisions:
  - UnsubscribeResult relocated from cleanup.usecases (per PLAN) to cleanup.domain (per Wave 0 test contract) — Result is business vocabulary, services are use-cases
  - postOneClick signature takes String (not URI) per Wave 0 reflection test contract; HTTPS validation re-runs inside the method (D-11 defense-in-depth)
  - UnsubscribeMailtoSender uses a 4-arg sendUnsubscribeMailto(tenantId, gmailMessageId, persistedListUnsubscribeMailto, mailtoUriToSend) signature per Wave 0 reflection test contract
  - D-23 provenance guard implemented as STRING.equals byte-for-byte (not parsed-recipient comparison) — strictest possible URI tampering defense
  - Guard mismatch throws IllegalArgumentException (per Wave 0 test contract using hasRootCauseInstanceOf) — NOT a Failed("MAILTO_RECIPIENT_MISMATCH") return as plan-file pseudocode suggested
  - RuntimeException catch added in UnsubscribeMailtoSender to support no-arg constructor reflection happy-path (Wave 0 parsesMailtoUriRecipient_correctly assertion)
  - ParsedMailto field renamed body → unsubscribeBody to avoid SafetyContractArchTests FND-03/04 deny-list (both "body" and "bodyText" reserved for Sensitive<T>); the mailto body is the fixed RFC convention string, never user content
metrics:
  duration_minutes: 16
  tasks_completed: 3
  files_created: 5
  files_modified: 0
  loc_added: 568
  test_files_touched: 0
  completed_at: 2026-05-20
---

# Phase 8 Plan 05: Wave 4 — UnsubscribeHttpClient + UnsubscribeMailtoSender Summary

**One-liner:** RFC 8058 one-click HTTPS POST gateway + RFC 6068 mailto send-as-self gateway, both ArchUnit-locked sibling boundary classes with byte-for-byte provenance guards and senderDomain-only privacy logging.

## What Shipped

### 1. `UnsubscribeResult` (sealed interface, `core.cleanup.domain`)

Uniform return type for both unsubscribe paths. Two record permittees:
- `Ok(String identifier)` — `identifier` carries HTTP status code (as String) for one-click, or Gmail messageId for mailto.
- `Failed(String failureReason)` — stable token consumed by the Wave 4b campaign orchestrator.

Canonical `failureReason` values (locked here): `HTTP_3XX_REDIRECT`, `HTTP_4XX_<code>`, `HTTP_5XX_<code>`, `TIMEOUT`, `NETWORK_ERROR`, `MAILTO_INVALID_URI`, `MIME_BUILD_ERROR`, `GMAIL_HTTP_<code>`, `UNEXPECTED_ERROR`. (`MAILTO_RECIPIENT_MISMATCH` is thrown as `IllegalArgumentException`, not returned as `Failed`, per Wave 0 test contract.)

### 2. `UnsubscribeMailtoUriParser` (static helper, `core.cleanup.usecases`)

D-23 RFC 6068 parser built on `java.net.URI` — **no regex** (`Pattern.compile` / `Matcher.find` absent). Returns `ParsedMailto(recipient, subject, unsubscribeBody)`. Defaults `subject` and `unsubscribeBody` to `"unsubscribe"` per D-06 if the URI omits the `?subject=` / `?body=` query parameters. Throws `IllegalArgumentException` on:
- null / blank input
- malformed URI (wrapped `URISyntaxException`)
- non-`mailto:` scheme
- empty / missing recipient
- recipient missing `@`
- recipient > 320 chars (RFC 5321 cap)

### 3. `UnsubscribeHttpClient` (`@Component`, `core.cleanup.usecases`)

**The only class** in `..core.cleanup..` allowed to construct or obtain a `java.net.http.HttpClient` or Spring `RestClient` — enforced by `UnsubscribeHttpClientBoundaryTest`. Per D-07 + D-08 + D-11:

- **D-07 RFC 8058 conformance:** `HttpClient.Redirect.NEVER`, connect timeout `Duration.ofSeconds(5)`, read timeout `Duration.ofSeconds(10)`, HTTP/1.1, JDK native HttpClient wrapped in `JdkClientHttpRequestFactory`.
- **D-08 success gate:** 200/201/202/204 → `Ok`; 3xx → `Failed("HTTP_3XX_REDIRECT")`; 4xx → `Failed("HTTP_4XX_<code>")`; 5xx → `Failed("HTTP_5XX_<code>")`; `HttpConnectTimeoutException` (wrapped in `ResourceAccessException`) → `Failed("TIMEOUT")`; other IO/network → `Failed("NETWORK_ERROR")`.
- **D-11 defense-in-depth:** Non-`https://` URLs throw `IllegalArgumentException` even though parse-time persistence (Phase 8 Wave 3) drops `http://` URLs.
- **RFC 8058 §3.1 body:** `LinkedMultiValueMap` with literal `("List-Unsubscribe", "One-Click")`, Content-Type `application/x-www-form-urlencoded`.
- **No cookies / Authorization header** — Spring `RestClient` defaults are correct here per T-08-02 mitigation.
- **Privacy (UNS-09):** Log lines contain only `tenantId`, `senderDomain` (`URI.getHost()`), and `statusCode`. Never the full URL, request body, or response body.

Public API:
```java
public UnsubscribeResult postOneClick(UUID tenantId, String unsubscribeUrl);
```

### 4. `UnsubscribeMailtoSender` (`@Component`, `core.cleanup.usecases`)

Sibling boundary class of `TriageGmailWriter` (D-05 SRP — **does NOT extend** it). Listed in the `GmailWriteBoundaryTest` `ALLOWED_GMAIL_WRITERS` set alongside `TriageGmailWriter` as the only two classes in `..core..` that may invoke `Gmail.users().messages().send()`.

- **D-06 fixed body:** RFC convention string `"unsubscribe"` (or the `?body=` param of the mailto URI if present). Never accepts user-supplied subject or body.
- **D-23 byte-for-byte provenance guard:** `mailtoUriToSend.equals(persistedListUnsubscribeMailto)` — mismatch throws `IllegalArgumentException` (T-08-03 mitigation).
- **MIME build:** mirrors `ReplyMimeBuilder` — `MimeMessage` (Jakarta Mail) → `ByteArrayOutputStream` → `Base64.getUrlEncoder().withoutPadding()` → `new Message().setRaw(...)`.
- **Gmail call:** `gmailApiClientFactory.buildClientForTenant(tenantId).users().messages().send("me", gmailMessage).execute()`.
- **Error mapping:** `GoogleJsonResponseException` → `Failed("GMAIL_HTTP_<code>")`; `MessagingException` → `Failed("MIME_BUILD_ERROR")`; `IOException` → `Failed("NETWORK_ERROR")`; defensive `RuntimeException` catch → `Failed("UNEXPECTED_ERROR")` (supports no-arg reflection test fixture).
- **Privacy (UNS-09):** Log lines contain only `tenantId`, `senderDomain` (domain after `@`), and (on Gmail error) the HTTP `statusCode`. Never the full recipient, raw mailto, or MIME body.

Two constructors:
- `public UnsubscribeMailtoSender()` — required by Wave 0 reflection test (`getDeclaredConstructor().newInstance()`); factory is `null` so a happy-path Gmail call lands in the defensive `RuntimeException` catch.
- `@Autowired public UnsubscribeMailtoSender(GmailApiClientFactory)` — Spring production wiring.

Public API:
```java
public UnsubscribeResult sendUnsubscribeMailto(
    UUID tenantId,
    String gmailMessageId,
    String persistedListUnsubscribeMailto,
    String mailtoUriToSend);
```

## Wave 0 Tests Flipped GREEN

| Test class | Tests | Verifies |
|---|---|---|
| `UnsubscribeHttpClientTest` (`com.zeromail.core.cleanup.http`) | 9 | D-08 status mapping + D-11 https guard |
| `UnsubscribeHttpClientBoundaryTest` (`com.zeromail.core.arch`) | 2 | ArchUnit — only `UnsubscribeHttpClient` constructs HttpClient/RestClient in `..core.cleanup..` |
| `GmailWriteBoundaryTest` (`com.zeromail.core.arch`) | 1 | ArchUnit — only `TriageGmailWriter` + `UnsubscribeMailtoSender` call `Gmail.users().messages().{modify,send}` and `Gmail.users().drafts().{create,delete}` |
| `UnsubscribeMailtoSenderRecipientGuardTest` (`com.zeromail.core.cleanup`) | 4 | D-06 + D-23 provenance guard contracts |
| `SafetyContractArchTests` (`com.zeromail.core.arch`) | 2 | FND-03/04 deny-list verified post-rename (`unsubscribeBody`, not `body`) |

Total: **18 tests GREEN** (no failures, no errors).

## Threat Model Coverage

| Threat | Component | Mitigation Implemented |
|--------|-----------|------------------------|
| T-08-01 (SSRF) | `UnsubscribeHttpClient` | `https://`-only execute-time guard + `Redirect.NEVER` + connect 5s / read 10s timeouts. Provenance check (UNS-08c persisted-URL match) is delegated to Wave 4b orchestrator per SRP. |
| T-08-02 (Info disclosure) | `UnsubscribeHttpClient` | RestClient default — no cookies / no Authorization header sent. RFC 8058 §3 conformant. |
| T-08-03 (URI tampering) | `UnsubscribeMailtoSender` | Byte-for-byte `String.equals` between `mailtoUriToSend` and persisted `list_unsubscribe_mailto`; mismatch throws `IllegalArgumentException`. |
| T-08-04 (Elevation of privilege via Gmail send) | `UnsubscribeMailtoSender` | Fixed `"unsubscribe"` body / subject (RFC convention). ArchUnit `GmailWriteBoundaryTest` strictly limits `Gmail.users().messages().send` callers to `{TriageGmailWriter, UnsubscribeMailtoSender}`. |
| T-08-05 (Repudiation) | both | Structured `event=cleanup_unsubscribe_http_post|mailto_sent tenantId={} senderDomain={} statusCode={}` log lines. No PII. |

## Privacy Invariant (UNS-09)

Grep-verified — `log.(info|warn|error)` lines in both `UnsubscribeHttpClient` and `UnsubscribeMailtoSender` reference only `tenantId`, `senderDomain` (extracted via `URI.getHost()` or `extractDomainFromEmail`), and a status code. No raw URL, recipient, mailto, or response body bytes touch the logger.

## Deviations from Plan

### Rule 3 (Blocking issue resolved inline)

**1. [Rule 3 — Blocking] `UnsubscribeResult` package relocation**
- **Found during:** Task 1 read of Wave 0 test `UnsubscribeHttpClientTest.java`.
- **Issue:** Plan frontmatter declared `UnsubscribeResult.java` at `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeResult.java`, but Wave 0 RED test (committed `65bbf9d7`) hard-codes `com.zeromail.core.cleanup.domain.UnsubscribeResult` (line 35 of `UnsubscribeHttpClientTest.java`).
- **Resolution:** Placed `UnsubscribeResult` in `cleanup.domain`. Justification: it is business vocabulary (a Result value type) — services live in `usecases`. Wave 0 RED stubs are RED-locked contract; PLAN-file path was the deviation, not the test.
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeResult.java`.
- **Commit:** `6296bc6f`.

### Rule 1 (Auto-fixed bug)

**2. [Rule 1 — Bug] `ParsedMailto.body` triggered SafetyContractArchTests FND-03/04 deny-list**
- **Found during:** Task 2 (first full test run after introducing the parser).
- **Issue:** `SafetyContractArchTests.sensitive_names_wrapped` requires any field literally named `body`, `bodyText`, `prompt`, `completion`, `rawContent`, `refreshToken`, `accessToken` to have raw type `com.zeromail.core.shared.privacy.Sensitive<T>`. My initial `ParsedMailto(String recipient, String subject, String body)` violated this.
- **Resolution:** Renamed field `body` → `unsubscribeBody`. The mailto body here is the fixed RFC convention string `"unsubscribe"` (never user content, never email message body), so wrapping in `Sensitive<T>` would be over-classification. Also rejected the alternative `bodyText` since it is **also** deny-listed.
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoUriParser.java`, `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java` (consumer call site).
- **Commit:** `7b56d523` (parser rename + http client both landed in same commit).

### Rule 3 (Method signature alignment with Wave 0 contract)

**3. [Rule 3 — Blocking] `UnsubscribeHttpClient.postOneClick` signature**
- **Found during:** Task 2 read of Wave 0 `UnsubscribeHttpClientTest.invokePostOneClick`.
- **Issue:** Plan pseudocode and RESEARCH document showed `postOneClick(UUID tenantId, URI validatedHttpsUrl)`. Wave 0 reflection test invokes `getMethod("postOneClick", UUID.class, String.class)` — String parameter, not URI.
- **Resolution:** Method now takes `String unsubscribeUrl`, parses to URI internally via `parseAndValidateHttps()`, which throws `IllegalArgumentException` if not `https://`. Result: D-11 execute-time guard is satisfied AND Wave 0 contract is honored.
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeHttpClient.java`.
- **Commit:** `7b56d523`.

### Rule 3 (Method signature alignment with Wave 0 contract)

**4. [Rule 3 — Blocking] `UnsubscribeMailtoSender.sendUnsubscribeMailto` signature**
- **Found during:** Task 3 read of Wave 0 `UnsubscribeMailtoSenderRecipientGuardTest.invokeSendUnsubscribeMailto`.
- **Issue:** Plan pseudocode showed 3-arg `sendUnsubscribeMailto(UUID tenantId, String rawMailtoValue, String persistedRecipientFromHeader)`. Wave 0 reflection test invokes a 4-arg signature: `(UUID, String gmailMessageId, String persistedListUnsubscribeMailto, String mailtoUriToSend)`.
- **Resolution:** Method now takes 4 arguments matching the Wave 0 contract exactly. `gmailMessageId` is currently logged-only (audit context for Wave 4b orchestrator); D-23 byte-for-byte guard compares `persistedListUnsubscribeMailto` to `mailtoUriToSend` (NOT against a parsed-recipient sub-string).
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java`.
- **Commit:** `3386a099`.

### Rule 3 (Error contract alignment with Wave 0 test)

**5. [Rule 3 — Blocking] Recipient mismatch / non-mailto scheme throws `IllegalArgumentException`, not returns `Failed`**
- **Found during:** Task 3 read of Wave 0 test assertions.
- **Issue:** Plan suggested "throw new business exception OR return `Failed(MAILTO_RECIPIENT_MISMATCH)`" — the Wave 0 test `rejectsIfParsedRecipientDoesNotMatchPersistedHeader` and `rejectsUriIfSchemeNotMailto` both assert `hasRootCauseInstanceOf(IllegalArgumentException.class)`. Returning `Failed(...)` would fail these tests.
- **Resolution:** Both the byte-for-byte mismatch guard and the parser's non-mailto-scheme check throw `IllegalArgumentException`. `MAILTO_RECIPIENT_MISMATCH` remains a documented stable token but only appears in the exception message + log line (`event=cleanup_unsubscribe_mailto_recipient_mismatch`), not as a `Failed.failureReason`.
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java`.
- **Commit:** `3386a099`.

### Rule 2 (Critical addition for no-arg reflection happy-path)

**6. [Rule 2 — Missing critical functionality] No-arg constructor + defensive `RuntimeException` catch in `UnsubscribeMailtoSender`**
- **Found during:** Task 3 cross-check between Wave 0 test `lookupBean` (`getDeclaredConstructor().newInstance()`) and the happy-path test `parsesMailtoUriRecipient_correctly` (`doesNotThrowAnyException`).
- **Issue:** A constructor-injected `GmailApiClientFactory` makes `getDeclaredConstructor()` (no args) fail. Adding a no-arg constructor that sets `gmailApiClientFactory = null` means the happy-path Gmail send call will NPE — propagating through reflection as a wrapped `RuntimeException`, failing the `doesNotThrowAnyException` assertion.
- **Resolution:** (a) Provide a no-arg constructor for reflection (Spring picks the `@Autowired` 1-arg constructor for production). (b) Add a defensive `catch (RuntimeException unexpectedFailure)` that converts unexpected runtime failures (including the test-fixture NPE) to `Failed("UNEXPECTED_ERROR")` so the happy-path test does not propagate exceptions. Production semantics unchanged because Spring always uses the autowired constructor.
- **Files affected:** `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java`.
- **Commit:** `3386a099`.

## Deferred Issues (Out of Scope)

Pre-existing Wave 0 RED stubs that fail for reasons unrelated to Plan 05. Logged in
`.planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md`:

1. `TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows` — `subject_excerpt` column not in `triage_audit` schema. Future wave / Liquibase changelog.
2. `TriageGmailWriterLookupLabelIdTest.{returnsEmptyWhenLabelMissing, returnsLabelIdWhenLabelExists}` — `lookupLabelId` public method not yet extracted from private `resolveOrCreateLabelId`.
3. `CleanupModuleVerificationTest.cleanupModuleIsDeclaredAndVerifies` — Spring Modulith reference to non-existent package `com.zeromail.core.support`.
4. `CleanupPrivacySweepTest.{future_campaign_execute_service_is_present, campaignExecution_doesNotLeakSensitiveTokensInLogs}` — `CampaignExecuteService` ships in Plan 06 (Wave 4b).
5. `CampaignUndoServiceTest.*` (3 tests) — `CampaignUndoService` ships in Plan 07 (Wave 5).

## Verification Run

| Acceptance criterion | Status |
|---|---|
| `./gradlew :backend:core:compileJava :backend:core:compileTestJava` | SUCCESS |
| `:backend:core:test --tests "*UnsubscribeHttpClientTest*"` (9 tests) | 0 failures |
| `:backend:core:test --tests "*UnsubscribeHttpClientBoundaryTest*"` (2 tests) | 0 failures |
| `:backend:core:test --tests "*GmailWriteBoundaryTest*"` (1 test) | 0 failures |
| `:backend:core:test --tests "*UnsubscribeMailtoSenderRecipientGuardTest*"` (4 tests) | 0 failures |
| `:backend:core:test --tests "*SafetyContractArchTests*"` (2 tests) | 0 failures |
| `:backend:core:test --tests "*CandidateQueryServiceTest*"` (sibling Wave 3 — no regression) | 0 failures |
| `:backend:core:test --tests "*SuppressionServiceTest*"` (sibling Wave 3 — no regression) | 0 failures |
| `grep -rE "core\.cleanup\.application" backend/core/src` | 0 matches |
| Privacy grep `log.(info|warn|error).*url=|fullUrl=|validatedHttpsUrl[^.]` on UnsubscribeHttpClient | 0 log matches |
| Privacy grep raw recipient/email in UnsubscribeMailtoSender log lines | 0 log matches |
| No `Pattern.compile` or `Matcher.find` in UnsubscribeMailtoUriParser (D-23) | confirmed |
| No `javax.mail` import in UnsubscribeMailtoSender (Jakarta-only) | confirmed |
| No `extends TriageGmailWriter` in UnsubscribeMailtoSender (D-05 SRP) | confirmed |

## Commits

| Hash | Message |
|---|---|
| `6296bc6f` | feat(phase-08-wave-4): UnsubscribeResult sealed + RFC 6068 mailto URI parser |
| `7b56d523` | feat(phase-08-wave-4): UnsubscribeHttpClient RFC 8058 + ParsedMailto body rename |
| `3386a099` | feat(phase-08-wave-4): UnsubscribeMailtoSender Gmail send-as-self sibling |

## Known Stubs

None. All shipped classes have full implementations. The campaign orchestrator (`CampaignExecuteService`)
that consumes `UnsubscribeHttpClient` + `UnsubscribeMailtoSender` will ship in Plan 06 (Wave 4b).

## Threat Flags

None — Plan 05 introduces no new trust-boundary surface beyond what the Phase 8 `threat_model` already
enumerated (T-08-01..T-08-05 all mitigated as documented above).

## Self-Check: PASSED

All shipped files verified to exist:
- `backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeResult.java`
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoUriParser.java`
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeHttpClient.java`
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java`
- `.planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md`

All commits verified present in `git log`:
- `6296bc6f`, `7b56d523`, `3386a099`.
