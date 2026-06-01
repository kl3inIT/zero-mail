# Quick Task w9t — Backend Config/Properties Redesign Research

**Researched:** 2026-05-30
**Domain:** Spring Boot 4.0.6 / Spring Framework 7.0.7 externalized config + `@ConfigurationProperties` (JDK 25, Spring AI 2.0.0-M7)
**Confidence:** HIGH on mechanisms (verified against current Spring Boot 4.0.x docs + existing working codebase); MEDIUM on Spring AI M7 idiom (verified via docs + GitHub, M7 churn possible)
**Scope:** Verify the orchestrator's already-decided design against current docs and surface pitfalls. Not a redesign.

> **Tooling note:** Context7 MCP tools were stripped from this agent (upstream bug anthropics/claude-code#13898 — `tools:` frontmatter restriction), and `ctx7` CLI is not installed. Per policy I did NOT `npx --yes` an unverified package. Fell back to WebFetch against `docs.spring.io/spring-boot/4.0.x` + `docs.spring.io/spring-ai` and WebSearch. Several claims are additionally **VERIFIED by the live codebase** (the current setup already exercises the mechanism in question), which is stronger evidence than docs alone.

---

## TL;DR — Confirmed Mechanisms

1. **`spring.config.import: classpath:<name>.yml`** is the right Boot 4 mechanism for one shared yml on the `core` classpath. Use `optional:classpath:` to keep tests/openapi-emit robust. **Imported file values take precedence over the importing `application.yml`** [VERIFIED: docs.spring.io]. So the shared file should hold *correctness-locked* values you want to win, and per-app yml holds app-specific values.
2. **DO NOT put `spring.autoconfigure.exclude` in the imported file.** It has timing/profile edge cases (Boot issue #26858) when delivered via `spring.config.import`. The project **already excludes the GenAI embedding auto-configs via `excludeName` on `@SpringBootApplication`** (both Application classes, lines 13-16) — that is the timing-safe mechanism and it makes the yml `spring.autoconfigure.exclude` block **redundant**. Drop the yml block; keep the Java `excludeName`.
3. **`zero-mail` ≠ `zeromail`** to the binder [VERIFIED: docs.spring.io]. Relaxed binding normalizes case/`-`/`_` *within token boundaries* but never inserts/removes a word boundary. Migrating `@Value("${zeromail.session.cookie.secure}")` and `@ConditionalOnProperty(name="zeromail.loadtest.enabled")` → `zero-mail.*` is a **real behavior-preserving rename that must touch annotation + yml in the same commit**.
4. **`@ConfigurationPropertiesScan(basePackages="com.zeromail")` already discovers records in the `core` jar** — proven empirically: `ConfigurationPropertiesScan` scans the *named packages regardless of jar boundary*, and `ZeroMailCoreProperties` (in `com.zeromail.core.config`, inside the core jar) binds today. Splitting the god-object into per-feature records co-located in feature packages under `com.zeromail.core.*` needs **no `@EnableConfigurationProperties` registry** [VERIFIED: codebase].
5. **Record binding + `@Validated` + `@DefaultValue` + nested records is the recommended Boot 4 style** [VERIFIED: docs.spring.io] and the config-processor emits metadata for records with **no special config**. Secret masking (`toString` override) is a project responsibility — Boot gives no automatic masking.

---

## 1. Shared config file via `spring.config.import`

**Confirmed syntax** (put in BOTH `backend/api` and `backend/worker` `application.yml`):
```yaml
spring:
  config:
    import: optional:classpath:zero-mail-shared.yml
```
Place `zero-mail-shared.yml` in `backend/core/src/main/resources/` — it is on the classpath of both api and worker because `core` is a compile dependency of both.

**Verified semantics** [VERIFIED: docs.spring.io/spring-boot/reference/features/external-config.html]:
- `optional:` prefix is **not required** but recommended — without it, a missing file throws at startup. Keep it so the openapi-emit forked server and any slim test contexts don't break.
- **Imported file WINS over importing file** on conflicting keys: *"Values from the imported `dev.properties` will take precedence over the file that triggered the import."* → Put values you want to **lock** (cannot be accidentally overridden by an app yml) in the shared file.
- `classpath:` resolves the first match on the classpath. Since only `core` ships `zero-mail-shared.yml`, there's no ambiguity.

**What belongs in the shared file (correctness-locked, identical across api+worker today):**
```yaml
spring:
  ai:
    model:
      chat: none
      embedding: none
      embedding.text: none
      embedding.multimodal: none
      image: none
      audio.speech: none
      audio.transcription: none
      moderation: none
    chat:
      client:
        observations:
          log-prompt: false
          log-completion: false
      observations:
        log-prompt: false
        log-completion: false
```
This is currently **duplicated verbatim** in api yml (lines 65-86) and worker yml (lines 41-62) — the prime duplication target.

**What must NOT go in the shared file:**
- `spring.autoconfigure.exclude` (see §2).
- Anything app-specific (api has `spring.ai.openai.chat.observations.include-completion: false`; worker has `spring.ai.retry.max-attempts: 2`). Leave those in their own yml.
- Profile-specific documents (`---` + `spring.config.activate.on-profile`) — historically buggy when delivered via `spring.config.import` (Boot issue #26858). Keep profile-specific overrides in the per-app yml.

---

## 2. `spring.autoconfigure.exclude` — keep in Java, drop from yml

**Pitfall (the one real correctness risk in this task):** `spring.autoconfigure.exclude` supplied through a `spring.config.import`'ed file is resolved late relative to the auto-configuration decision, and profile-specific imported docs are not reliably considered (Boot issue #26858). Do not rely on it from the shared file.

**The codebase already solves this the timing-safe way.** Both `ZeroMailApiApplication` and `ZeroMailWorkerApplication` carry:
```java
@SpringBootApplication(
    scanBasePackages = {...},
    excludeName = {
        "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration"
    })
```
This `excludeName` is functionally equivalent to `spring.autoconfigure.exclude` and is evaluated at the correct point in the bootstrap. Therefore the `spring.autoconfigure.exclude` block in **both yml files (api lines 4-10, worker lines 4-10) is redundant** and should be **removed** — not moved to the shared file. The two FQNs are duplicated across 4 places today (2 yml + 2 Java); collapsing to the 2 Java declarations is the clean state. (Optional further dedup: a tiny shared `String[] EXCLUDED_AI_AUTOCONFIGS` constant in core referenced by both `@SpringBootApplication(excludeName=...)` — but `excludeName` needs compile-time constants, so a `static final String[]` works; low priority.)

**If you insist on yml-only:** put the exclude in each app's OWN `application.yml` (default location, resolved early), never in the imported shared file.

---

## 3. Prefix unification `zeromail.*` → `zero-mail.*` (real rename)

**Confirmed** [VERIFIED: docs.spring.io relaxed-binding]: relaxed binding maps kebab/camel/underscore/uppercase representations *of the same logical name* to one target (`first-name`/`firstName`/`first_name`/`FIRSTNAME`). It does **not** bridge a no-separator token to a kebab token: `zeromail` is one lowercase token; `zero-mail` canonicalizes to two segments. **Distinct prefixes.**

**Migration is behavior-changing unless annotation + yml move together.** Current split state:
- `zeromail.session.cookie.secure` — yml api line 91-97 (`zeromail:` block) + read by `@Value("${zeromail.session.cookie.secure}")` in `SecurityConfig`.
- `zeromail.loadtest.enabled` — `@ConditionalOnProperty(name="zeromail.loadtest.enabled")` (per task context).
- Everything else already lives under canonical `zero-mail.*` (api lines 98+, worker lines 65+) and binds to `@ConfigurationProperties(prefix="zero-mail")`.

**Rename checklist (same commit each):**
- yml key `zeromail.session.cookie.secure` → `zero-mail.session.cookie.secure` AND `@Value` placeholder → `${zero-mail.session.cookie.secure}` (use canonical kebab in the placeholder — Boot resolves `zero-mail` ⇒ also matches `zeroMail`/`ZEROMAIL`; do not write `${zeroMail...}` which would miss the kebab yml form).
- `@ConditionalOnProperty(name="zeromail.loadtest.enabled")` → `name="zero-mail.loadtest.enabled"` AND any yml/env that sets it.

**`@ConditionalOnProperty` + relaxed binding gotcha** [VERIFIED: docs]: the `name`/`prefix` attribute is also subject to relaxed binding — `name="zero-mail.loadtest.enabled"` will match `ZERO_MAIL_LOADTEST_ENABLED` env and `zeroMail...` yml. But it will **not** match a leftover `zeromail.loadtest.enabled` key. Grep for the old token across yml, `.env*`, docker-compose, systemd units, and CI before declaring done: `grep -rn 'zeromail\.' backend/ apps/ *.yml *.yaml docker-compose*`.

---

## 4. Splitting the god-object into per-feature records

**Current:** one `@ConfigurationProperties(prefix="zero-mail")` record `ZeroMailCoreProperties` (218 lines, in `com.zeromail.core.config`) with nested `crypto/gmail/billing/llm/admin` records.

**Target (confirmed safe):** N independent records, each its own `@ConfigurationProperties(prefix="zero-mail.<feature>")`, co-located in feature packages, e.g.:
- `com.zeromail.core.gmail.config.GmailProperties` → `zero-mail.gmail`
- `com.zeromail.core.<crypto pkg>.CryptoProperties` → `zero-mail.crypto`
- `...billing.config.BillingProperties` → `zero-mail.billing`
- `...llm.config.LlmProperties` → `zero-mail.llm`
- `...admin/ops config AdminProperties` → `zero-mail.admin`

**Why it just works** [VERIFIED: codebase + docs]:
- `@ConfigurationPropertiesScan(basePackages="com.zeromail")` scans the named package across **all jars** (core is a jar dependency). The existing `ZeroMailCoreProperties` already lives in the core jar and binds — this is empirical proof, not theory. New per-feature records anywhere under `com.zeromail.core.*` are auto-discovered. **No `@EnableConfigurationProperties` registry needed.**
- Record binding + `@Validated` + `@DefaultValue` + nested records is the recommended Boot 4 constructor-binding style [VERIFIED: docs]; *"Constructor binding can be used with records. Unless your record has multiple constructors, there is no need to use `@ConstructorBinding`."* The existing records already follow this exactly — split, don't rewrite the binding style.

**Carry-over invariants when splitting (do not regress):**
- **Secret masking:** `ZeroMailCoreProperties.toString()` and `BillingProperties.toString()` hand-mask secrets (`crypto=****`, `llm=****`, `lemonSqueezy=configured|not_configured`). Boot does **NOT** auto-mask [VERIFIED: docs — no masking guidance]. Each new record that holds a secret (`CryptoProperties.refreshTokenKeyBase64`, `LlmProperties...apiKey`, `LemonSqueezyProperties.apiKey/webhookSigningSecret`, `AdminAuditProperties.hmacKekBase64`) **must keep its own masked `toString()`**. Per STATE.md `BillingProperties masks SePay/LS secret in toString` is a locked decision — preserve it.
- **`@Validated` fail-loud:** `AdminSpendProperties.@Min(1) kAnonymityThreshold` and `@NotNull rowLevelClassificationSince` enforce at bind time (R-8F-H6/H9). Keep `@Validated` on each split record that had a constraint; a record with constraints but no `@Validated` silently skips validation.
- **`@NonNull` (JSpecify) on `toString()` return** is cosmetic (return-type nullness) — no binder interaction. No Boot 4 change here.
- **Defaults defined two ways today:** some via `@DefaultValue` annotation, some via compact-constructor null-coalescing (`provider == null ? "openai" : provider`). Both work; the compact-constructor style is needed where the default depends on blank-check (`isBlank()`), which `@DefaultValue` can't express. Keep the existing per-field choice — don't "simplify" blank-coalescing into `@DefaultValue`.

**Config-processor metadata** [VERIFIED: docs]: `spring-boot-configuration-processor` needs **nothing special** for records/nested records — annotate with `@ConfigurationProperties` and it emits `spring-configuration-metadata.json`. `additional-spring-configuration-metadata.json` is only for hints the processor can't infer (e.g., dynamic keys); the current static records don't need it. After splitting, the generated metadata keys change from `zero-mail.gmail.*` (nested) to the same `zero-mail.gmail.*` (top-level record) — IDE autocomplete stays intact.

---

## 5. Spring AI 2.0.0-M7 note on suppressing model beans

- `spring.ai.model.<modality>: none` is the **current, correct** idiom to disable a modality's auto-config when a starter is on the classpath [VERIFIED: docs.spring.io/spring-ai upgrade-notes + GH issue #363]. The pattern: set the modality property to a value that matches no provider on the classpath (`none`). There is **no cleaner Boot 4 idiom** — `none` is the sanctioned sentinel; this is by design so a present starter doesn't eagerly build a client needing credentials.
- M7-specific caution: GH #6150 reports M7 model auto-config **forces an API-key requirement** more aggressively than earlier milestones. This reinforces keeping the full `spring.ai.model.* = none` block centralized in the shared file so a missing `none` on any modality can't surprise either app with a credential demand. Keep the project rule: all real ChatModel construction stays inside `core.llm.gateway.springai`; the starters' auto-model beans stay off.
- `spring.ai.chat.client.observations.log-prompt/log-completion=false` + `spring.ai.chat.observations.log-prompt/log-completion=false` are the privacy-critical lines (CLAUDE.md: AI prompt/completion capture disabled). They MUST be in the shared file so neither module can lose them. Per STATE.md 02C-Plan03 this is a locked privacy invariant — centralizing it in the shared core yml is strictly safer than the current per-app duplication.

---

## Recommended end state (summary)

| File | Holds |
|------|-------|
| `backend/core/.../resources/zero-mail-shared.yml` (NEW) | `spring.ai.model.* = none` (8 lines) + `spring.ai.chat[.client].observations.log-prompt/completion = false` — the privacy/correctness-locked block, deduped from both apps. |
| `backend/api/.../application.yml` | `spring.config.import: optional:classpath:zero-mail-shared.yml`; api-only (`spring.ai.openai...include-completion`, CORS, session, springdoc, oauth full scopes, pubsub audience). **Remove** `spring.autoconfigure.exclude` block. Rename `zeromail.session.*` → `zero-mail.session.*`. |
| `backend/worker/.../application.yml` | `spring.config.import: optional:classpath:zero-mail-shared.yml`; worker-only (`spring.ai.retry`, `main.keep-alive`, notification, drift, worker pubsub topic). **Remove** `spring.autoconfigure.exclude` block. |
| Both `*Application.java` | Keep `excludeName` GenAI exclusion (the timing-safe exclude). |
| `com.zeromail.core.*` feature pkgs | N per-feature `@ConfigurationProperties(prefix="zero-mail.<feature>")` records replacing the single `ZeroMailCoreProperties`. Each secret-bearing record keeps a masked `toString()`; each constrained record keeps `@Validated`. |
| `SecurityConfig` `@Value` + any `@ConditionalOnProperty` | `zeromail.*` → `zero-mail.*` in the same commit as the yml key rename. |

---

## Pitfalls / Do-Not list

- **DO NOT** move `spring.autoconfigure.exclude` into the imported shared yml (late resolution / profile edge case, Boot #26858). Use the existing Java `excludeName` instead, or per-app yml only.
- **DO NOT** assume `zeromail` and `zero-mail` are the same prefix — they are not. Rename annotation + yml + env together; grep `zeromail\.` repo-wide before done.
- **DO NOT** drop a record's `@Validated` when splitting — constraints silently no-op without it.
- **DO NOT** drop the hand-written masked `toString()` on any record carrying a secret — Boot does no auto-masking; a default record `toString()` leaks `refreshTokenKeyBase64`, `apiKey`, `webhookSigningSecret`, `hmacKekBase64`.
- **DO NOT** rely on the WebFetch summarizer's claim that `@ConfigurationPropertiesScan` "doesn't scan dependency jars" — it scans the *named packages* across jars; the live app proves cross-jar binding works today.
- **DO NOT** write `${zeroMail.session.cookie.secure}` (camel) in a `@Value` placeholder; use canonical kebab `${zero-mail.session.cookie.secure}` so the kebab yml key is matched.
- **DO** keep `optional:` on the import so openapi-emit / slim test contexts don't fail on a missing classpath resource.
- **DO NOT** add profile-specific (`---` `on-profile`) documents inside the shared imported file; keep profile overrides in per-app yml.

---

## Assumptions Log

| # | Claim | Risk if wrong |
|---|-------|---------------|
| A1 | `@SpringBootApplication(excludeName=...)` is evaluated early enough to reliably exclude the GenAI embedding auto-configs (vs yml import). | LOW — this is the current working behavior; confirmed by the app booting with starters present and no embedding client built. |
| A2 | Splitting one `@ConfigurationProperties` record into N top-level records produces identical bound values (same `zero-mail.<feature>.*` keys). | LOW — keys are unchanged; only the Java owner of each subtree changes. Verify with a bind smoke test. |
| A3 | No leftover `zeromail.*` (no-dash) keys exist beyond `session.cookie.secure` + `loadtest.enabled`. | MEDIUM — must grep repo + env + docker-compose + systemd to confirm before rename. |

## Sources

**Primary (HIGH):**
- docs.spring.io/spring-boot/reference/features/external-config.html (4.0.x) — `spring.config.import` syntax, `optional:`, import-wins precedence, relaxed binding rules, `@ConfigurationProperties` constructor/record binding, `@ConfigurationPropertiesScan` package scanning, secret-masking absence.
- Live codebase — both Application classes (`excludeName`, `@ConfigurationPropertiesScan(basePackages="com.zeromail")`), `ZeroMailCoreProperties.java` (binds from core jar today), both `application.yml` (duplication + prefix split).
- docs.spring.io/spring-ai/reference/upgrade-notes.html + GH spring-ai #363 — `spring.ai.model.<modality>=none` sanctioned disable idiom.

**Secondary (MEDIUM):**
- GH spring-boot #26858 — profile-specific docs via `spring.config.import` edge cases.
- GH spring-ai #6150 — M7 forces API-key requirement (reinforces centralizing `=none`).

**Confidence:** mechanisms HIGH (docs + empirical codebase); Spring AI M7 idiom MEDIUM (M7 → GA churn possible).
**Valid until:** ~2026-06-30 (Boot 4.0.x stable; re-verify Spring AI on GA).
