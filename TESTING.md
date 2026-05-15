# Zero Mail — Testing Rules

Project-wide testing rules referenced by `CLAUDE.md` / `AGENTS.md`. Read this before writing or reviewing tests.

---

**Write tests for invariants, not for code shape.** A test earns its place if breaking it = user uninstalls, data leaks, money is wrong, or an LLM safety rule is bypassed. Otherwise skip it.

This project favors **architectural quality and defensibility over speed** (see `CLAUDE.md` → Constraints → Timeline). That does **not** mean test everything — it means test the things that, if broken, kill the product.

---

## 1. Must-test (always)

- **Safety / privacy invariants** — no auto-send, no PII in logs, no prompts/completions in DB or logs. Prefer **ArchUnit** over runtime tests where possible (cheaper, faster, stable).
- **Multi-tenant isolation** — every cross-tenant boundary that could leak data needs a leak test (`*MultiTenantLeakTest` pattern).
- **OAuth provisioning races, Pub/Sub idempotency, sanitization pipeline + prompt-injection corpus.**
- **Money path** — credit reserve/settle/release, concurrent reserve, Sepay verification, ledger uniqueness.
- **Crypto round-trips** — AES-GCM token cipher, nonce uniqueness.
- **Enum `fromId` fail-loud + state machines** — cheap, stable, encodes domain rules.

## 2. Do-not-test

- DTO shape, controller copy/path wording, framework behavior (Spring property binding, Jackson serialization, Hibernate cascade).
- "Service A calls service B" — implementation detail. Test the observable outcome instead.
- More than **one happy-path + one error-contract** integration test per controller endpoint. Privacy/tenant invariants belong in ArchUnit or use-case tests, not in 4 parallel controller test files.
- Token counts, latency, model-version-specific phrasing — change between Spring AI M6 → GA and across providers.

---

## 3. Spring Boot 4 — pick the smallest slice that proves the invariant

Default to plain JUnit. Climb the slice ladder only when the next level adds something real.

| Test type | When to use | What loads |
|---|---|---|
| **Plain JUnit + Mockito** | 80% of tests — pure domain logic, validators, sanitizers, enums, state machines | Nothing. No Spring context. |
| **`@WebMvcTest(XxxController.class)` + `@MockitoBean`** | Controller HTTP contract: status, body, validation, ProblemDetail mapping | MVC + Security + Jackson, no DB |
| **`@DataJpaTest` + Testcontainers Postgres** | Repository queries, JSONB matchers, JPQL/native SQL, unique constraints | JPA slice + real Postgres |
| **`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ServiceConnection`** | True end-to-end flows only: OAuth callback, Pub/Sub idempotency, session cookie | Full app context |

**Discipline:**

- **Never H2** — we run real Postgres in prod, run Testcontainers Postgres in tests.
- **Prefer `@ServiceConnection`** over manual `@DynamicPropertySource` (Boot 3.1+ idiom, less ceremony, auto-detects container type).
- **Use `@MockitoBean` / `@MockitoSpyBean`** — `@MockBean` is deprecated since Boot 3.4.
- **Enable Testcontainers reuse** in `~/.testcontainers.properties` to keep the loop fast.
- **DB hygiene** — tests roll back by default (`@Transactional` on `@DataJpaTest`). For tests that need committed state (race conditions, `SKIP LOCKED`), disable with `@Transactional(propagation = NOT_SUPPORTED)` and clean explicitly.
- **Keep `ApplicationModulesTest`** running — it's the only thing keeping package boundaries honest.

If you're tempted to put `@SpringBootTest` on a domain logic test, you're wrong — drop down a slice.

---

## 4. Spring AI — never call a real LLM in `./gradlew test`

Spring AI tests live in **three layers**. Do not mix them.

### Layer 1 — prompt builder / config unit tests (no model)

Build a `Prompt`, a `ChatClient.Builder`, or `OpenAiChatOptions` and assert on the rendered messages, options, model id, temperature, tool definitions. No `ChatModel` instantiated.

### Layer 2 — use-case tests with mocked `ChatModel` / gateway

This is where 90% of LLM-adjacent tests live. Pattern:

- Inject a fake `LlmModelClient` (or mock `ChatClient`) that returns a canned `ChatResponse` / `ToolCall`.
- Assert the use case (credit reserve, action validator, sanitizer pipeline, BYOK routing, observability log) behaves correctly on success / refusal / malformed JSON / tool-call output.
- Verify the **request** going out — system prompt id, sanitized user message, tool list, options — using `ArgumentCaptor` or a recording client.
- Never assert on free-form LLM output with `equals`.

### Layer 3 — real-LLM evaluation (excluded from default `test`)

Use these only when the test genuinely needs a real model (RAG relevancy, fact-checking, prompt drift detection). They are **not** unit tests — they are offline evals.

- Tag with `@Tag("llm-eval")`.
- Exclude from default `./gradlew test`. Add a dedicated `./gradlew llmEval` task.
- Gate execution with `@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")` so CI without keys silently skips.
- Set `temperature = 0.0`, pin a specific judge model, use Spring AI `Evaluator` (`RelevancyEvaluator`, `FactCheckingEvaluator`) — never assert on raw text equality.
- Treat as a **nightly / pre-release** signal, not a per-PR gate.

### Spring AI specifics

- Pin **structured output** with `.entity(SomeRecord.class)` and assert on the record fields, not raw string. Mock the `ChatModel` to return a fixture JSON string.
- **Tool-calling use cases** — assert which tools were offered (`toolCallbacks(...)`) and the `ToolCallback` was invoked with the right args. Don't assert on the LLM's wording.
- **Sanitization + prompt injection corpus** runs as plain unit tests (no LLM) — they pin the **input** pipeline, not model behavior.
- **`ChatResponse.toString()` ban** — there's already an ArchUnit test enforcing this. Don't break it. Privacy constraint forbids prompt/completion content in logs.
- **Property-binding tests for LLM config** (`ZeroMailLlmProperties`, `ApplicationYmlLlmConfigTest`) — keep ONE per properties class. Don't multiply.

---

## 5. Test discipline (apply everywhere)

- **Never modify an existing test to make it pass — fix the code.** Without this rule, the agent will weaken assertions to clear red.
- **Never update snapshots without explicit user instruction.**
- **For a reported bug:** write the failing test first, then fix.
- **Mock external APIs only** (Gmail, OpenRouter, Sepay, Pub/Sub). Never mock internal services or repositories — use Testcontainers instead.
- **Integration tests must hit real Postgres via Testcontainers** — not H2, not mocks.
- **Report exactly what you observe.** Do not mark a test passed if the assertion didn't run.
- **Don't add tests for impossible states** — matches the `do not add error handling for impossible states` rule in `CONVENTIONS.md`.
- **Wave/phase scaffolding tests should be promoted or deleted** after the phase ships — don't leave `@Disabled` placeholders rotting in the tree.
