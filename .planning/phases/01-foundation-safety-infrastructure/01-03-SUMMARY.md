---
phase: 01-foundation-safety-infrastructure
plan: 03
status: complete
completed: 2026-04-25
---

# Plan 01-03 — Log-Safety Contract

## What shipped

### Privacy primitives (Task 1)

- `Sensitive<T>` record — null-rejecting compact ctor; `toString()` returns the literal `***REDACTED***`; static factory `Sensitive.of(...)`. The primary redaction contract: anywhere a `Sensitive<?>` is interpolated into a string ({} placeholder, concatenation, `String.valueOf`), the underlying value never appears.
- `SensitiveJacksonModule` — `SimpleModule` registering a serializer that emits `"***REDACTED***"` for any `Sensitive<?>` field. Annotated `@Component` (Spring Boot 4 Jackson auto-config picks up `Module` beans automatically; `@JsonComponent` is not on `core`'s classpath).
- `SensitiveMarkerScrubFilter` — Logback `TurboFilter` that detects stray `Sensitive(...)` tokens and stamps `scrubbed=true` / `scrub_reason=sensitive_marker` on MDC. **Constraint:** the filter does not rewrite the rendered message because Logback's single-arg dispatch (`logger.info(format, arg)`) hands the TurboFilter a transient one-shot argument array; mutation cannot propagate to the LoggingEvent. The MDC stamp is the observable defense-in-depth signal for SOC alerting; redaction itself stays delivered by `Sensitive.toString()`.
- `logback-spring.xml` — `<turboFilter>` registers the scrub filter; `LogstashEncoder` JSON output exposes `scrubbed` + `scrub_reason` MDC keys via `<includeMdcKeyName>`.
- `privacy/package-info.java` — `@ApplicationModule(displayName = "Privacy", allowedDependencies = {})`.

### Build-time + test-time guards (Task 2)

- `SafetyContractArchTests`:
  - `sensitive_names_wrapped` — any field named in the deny-list (`body|bodyText|prompt|completion|rawContent|refreshToken|accessToken`) whose raw type isn't `Sensitive` fails the build. Currently no fields match; `allowEmptyShould(true)` keeps the rule a tripwire for plan 04+.
  - `no_sensitive_in_logger` — `ArchCondition<JavaClass>` walks every method call; fails if any `org.slf4j.Logger` method receives a `Sensitive` parameter.
- `buildSrc/src/main/kotlin/zeromail.sensitive-log-guard.gradle.kts` — convention plugin with two tasks:
  - `sensitiveLogGuard` — scans `src/main/java/**/*.java`, fails on `Logger.info|debug|warn|error|trace(...)` calls referencing the wider deny-list (`body|bodyText|prompt|completion|rawContent|refreshToken|accessToken|authorizationCode|idToken|gmailMessagePayload`) or `Sensitive` outside `com.zeromail.core.privacy`. Wired into `check`.
  - `sensitiveLogGuardNegativeFixture` — scans `src/test/resources/archfixtures/`, INVERTS the result: passes only if violations are detected. Proves the guard is not a no-op.
- `backend/core/src/test/resources/archfixtures/UnsafeSensitiveLoggingFixture.java` — fixture under `resources/` (never compiled), 5 canned unsafe calls (body, refreshToken, prompt, completion, accessToken). Each must trigger a violation.
- `backend/core/build.gradle.kts` — applies `zeromail.sensitive-log-guard`.

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.privacy.*"` → BUILD SUCCESSFUL (5 tests).
- `./gradlew :backend:core:test --tests "com.zeromail.core.arch.SafetyContractArchTests"` → BUILD SUCCESSFUL.
- `./gradlew :backend:core:sensitiveLogGuard` → `✓ no unsafe Logger calls in production sources`.
- `./gradlew :backend:core:sensitiveLogGuardNegativeFixture` → `✓ detected 5 expected violation(s)`.
- Full `:backend:core:test :backend:api:test` → BUILD SUCCESSFUL (no regression of plan 01-02 tests).

## Requirements satisfied

- **FND-03** — Sensitive payloads cannot reach logs unredacted via the `Sensitive.toString()` contract; runtime grep-for-bodies proof against authenticated traffic is owned by plan 01-09.
- **FND-04** — Build-time logger guard fails on deny-listed identifiers, including a self-test (negative fixture) proving the guard is enforceable.

## Decisions implemented

- D-E1 — `Sensitive<T>` wrapper + Jackson redacting serializer + Logback marker filter.
- D-E2 — Build-time guard for unsafe `Logger.*` calls; ArchUnit rule for unwrapped deny-listed field names.
- D-E3 — Broad runtime regex scanning remains explicitly out of scope (deferred). The narrow build-time logger guard satisfies FND-04's enforceable surface.

## Notes for downstream plans

- **Plan 01-04**: when the `gmail_connections` entity adds the refresh-token column, declare it as `Sensitive<String> refreshToken`. The `sensitive_names_wrapped` rule will block any `String refreshToken` regression.
- **Plan 01-05**: never log raw OAuth response payloads. The OAuth refresh client should pass `Sensitive.of(refreshToken)` if a token must appear in any log statement; the `no_sensitive_in_logger` rule will then fail unless the call is rewritten.
- **Plan 01-09**: the FND-03 runtime proof drives synthetic-traffic body-grep tests through real authenticated endpoints and asserts no body/prompt/completion text reaches the captured log stream.

## Files modified

See PLAN.md `files_modified` — all 10 files committed across:

- `b... feat(01-03): add Sensitive wrapper + Jackson module + Logback scrub filter`
- `f14e8be build(01-03): add SafetyContract ArchUnit rules + sensitiveLogGuard plugin`
