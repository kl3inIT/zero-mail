---
slug: 9router-stream-cancel
status: resolved
trigger: chat_stream_failed
goal: find_and_fix
tdd_mode: false
created: 2026-05-25
branch_base: quick/260525-lt4-fix-accent-soft-contrast
branch_fix: debug/9router-stream-cancel
---

# 9router Multi-Step Chat Stream Cancel

## Symptoms

User-observed (Playwright-verified 2026-05-25):

- Single-step tool calls (`listLabels`, `listRules`, `saveDraft`, `sendEmail`, plain "xin chào") consistently succeed and render.
- Multi-step tool chains (`searchInbox → applyLabel`, or two `searchInbox` rounds) intermittently produce an EMPTY assistant turn — no text, no tool card, no error toast — chat status flips to `ready` as if nothing happened.
- Frequency: ~1-in-3 multi-step runs. Predates today's QT1-QT5 quicks.

Backend stack trace (repeated by user):

```
java.util.concurrent.CompletionException: com.openai.errors.OpenAIIoException: Request failed
  at com.openai.client.okhttp.OkHttpClient$executeAsync$1.onFailure(OkHttpClient.kt:71)
Caused by: java.io.InterruptedIOException: timeout
  at okhttp3.internal.connection.RealCall.timeoutExit(RealCall.kt:398)
Caused by: okhttp3.internal.http2.StreamResetException: stream was reset: CANCEL
  at okhttp3.internal.http2.Http2Stream.takeHeaders(Http2Stream.kt:148)
```

Reading: 9router (server) reset the HTTP/2 stream with CANCEL; OkHttp surfaces it as `InterruptedIOException: timeout`.

## Architecture Context

- **Provider:** Self-hosted gateway at `https://9router.zeromail.vn/v1` (Node.js HTTP/2 proxy). NOT OpenRouter.
- **Known 9router quirks:**
  - `ModelsProbeClient.java:41` — backends "do not implement h2c (observed against 9router)".
  - `RestClientConfig.java:37-58` — `cleartextRestClientBuilder` HTTP/1.1-only bean for 9router-style backends.
- **Streaming path uses OkHttp HTTP/2**, NOT the JDK HttpClient.
- Spring AI 2.0.0-M7 — `OpenAiChatModel` constructed manually at `SpringAiProviderChatClientFactory` WITHOUT a custom HTTP client → uses OpenAI Java SDK's OkHttp defaults.
- `application.yml` — `read-timeout: 30s` maps to `OpenAiChatOptions.timeout` → `X-Stainless-Timeout` HTTP header, NOT OkHttp client read/call timeout.
- `ChatOrchestrator.executeReadToolLoop` — multi-step loop opens a fresh HTTP/2 stream per iteration; CANCEL on any iteration kills the whole turn.
- FE `use-chat.ts` — `onData` only consumes persistence parts; error events silently dropped.

## Hypotheses

1. OkHttp client read/call timeout too short for 9router. — Considered, deprioritized (Spring AI M7's `OpenAiChatModel.Builder` does not expose an OkHttp client hook without leaving the adapter boundary; touching the SDK transport invites destabilizing every other LLM call).
2. **No retry on transient I/O.** — Confirmed root cause. Multi-step turns hit several discrete HTTP/2 streams, each one a new chance for an upstream-initiated `RST_STREAM(CANCEL)`. The orchestrator gave up on the first failure.
3. 9router HTTP/2 quirks — Possible contributor; (2)'s mitigation makes it observable but recoverable.
4. FE swallows the failure event — Real, but a separate concern. After the orchestrator retry, the residual rate is low enough that we tolerate it until a dedicated FE pass.

## Root Cause

**The orchestrator did not retry transient HTTP/2 stream cancels.** The OpenAI Java SDK + OkHttp surfaces an upstream `RST_STREAM(CANCEL)` as `CompletionException → OpenAIIoException("Request failed") → InterruptedIOException("timeout") → okhttp3.internal.http2.StreamResetException`. Multi-step tool turns multiply the exposure (one HTTP/2 stream per `executeReadToolLoop` iteration), so a 1-stream failure rate that is benign for single-step turns becomes a 1-in-3 visible failure for multi-step turns. The original `ChatOrchestrator.stream` task catches the top-level `RuntimeException`, emits `chat_stream_failed`, and finishes — with no exception-class branching and no retry budget.

## Fix

Three-layer mitigation, all inside `core.chat.llm.springai` + `core.chat.usecases` (no changes to the OpenAI SDK transport, no new HTTP-client construction, no breaking the streaming-only constraint):

1. **`TransientStreamFailureClassifier`** (new) — walks the cause chain and matches `OpenAIIoException`, `InterruptedIOException`, and OkHttp's `okhttp3.internal.http2.StreamResetException` by fully qualified name (to avoid importing an `internal` package). Deliberately excludes `OpenAIServiceException` (logical 4xx/5xx) so credit settlement and bad-request shapes never silently retry. Has a 16-link guard against pathological cause chains.
2. **`SpringAiStreamingChatModelClient`** — on subscription `onError`, classifies the failure: transient → `emitError("chat_stream_transient", ...)`, terminal → `emitError("chat_stream_failed", ...)`. Audited via `event=chat_llm_stream_failed tenantId=… errorClass=… rootClass=… transient=…`, privacy-safe (no message body).
3. **`ChatOrchestrator.streamOneIteration`** — wraps each iteration in an `InterceptingSink` that BUFFERS `emitError` instead of forwarding it. After the gateway completes, the orchestrator inspects the outcome: transient + budget remaining → retry the same iteration (audited via `event=chat_stream_retry_attempt`); non-transient or budget exhausted → flush the error to the downstream sink so the FE state is consistent.
4. **`ZeroMailChatProperties.transientStreamRetryMaxAttempts`** (new, default `1`) — single retry per iteration. Bounded; no silent credit inflation; user-configurable via `zero-mail.chat.transient-stream-retry-max-attempts`.

Frontend retry affordance (H4) deferred — not strictly needed because the empty-turn symptom collapses by the retry's success rate, and the rare terminal flush is already emitted as `chat_stream_failed`.

## Evidence

- Backend trace + symptom rate confirm transient HTTP/2 CANCEL on multi-step turns.
- Spring AI M7 `OpenAiChatModel.Builder` (per pulled M7 source at `.planning/debug/OpenAiChatModel-m7-source.java`) accepts an `OpenAIClient`/`OpenAIClientAsync` but not a custom OkHttp client; replacing the SDK transport would require leaving the Spring AI abstraction — rejected.
- Targeted compile + tests: `./gradlew :backend:core:compileJava :backend:core:compileTestJava` green; `./gradlew :backend:core:test --tests TransientStreamFailureClassifierTest --tests ChatOrchestratorTransientStreamRetryIT` green (BUILD SUCCESSFUL in 51s).

## Files Touched

Production:
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/TransientStreamFailureClassifier.java` (new)
- `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java`
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ZeroMailChatProperties.java`

Tests:
- `backend/core/src/test/java/com/zeromail/core/chat/llm/springai/TransientStreamFailureClassifierTest.java` (new)
- `backend/core/src/test/java/com/zeromail/core/chat/usecases/ChatOrchestratorTransientStreamRetryIT.java` (new)
- `backend/core/src/test/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactoryTest.java` (constructor update for the new properties arity)

## Resolution

Bounded transient-stream-retry implemented at the orchestrator layer with an inline classifier in the Spring AI adapter. Default budget = 1 retry per iteration; configurable via `zero-mail.chat.transient-stream-retry-max-attempts`. Audited, bounded, privacy-safe, streaming-only. Deterministic test reproduces the 9router `RST_STREAM(CANCEL)` shape through a Mockito-stubbed `ChatLlmGateway`.

Next steps for the user:
- Restart `backend/api` to pick up the new orchestrator wiring.
- Manual smoke: rerun the "tìm 1 email mới nhất rồi gắn nhãn X" prompt several times; expect zero empty turns (or, on the rare double-failure, a clear `chat_stream_failed` event).
- A follow-up could surface `chat_stream_failed` as a Sonner toast / inline retry affordance in `use-chat.ts` (H4) — not blocking.
