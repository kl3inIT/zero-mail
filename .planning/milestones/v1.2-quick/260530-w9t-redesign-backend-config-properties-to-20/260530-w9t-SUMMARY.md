---
phase: quick-260530-w9t
plan: 01
status: complete
date: 2026-05-31
---

# Quick Task 260530-w9t — Backend config/properties redesign (Spring Boot 4 best practices)

Pure structure/namespace refactor — no runtime-effective value changed; behavior byte-identical.

## What shipped (commits)

| Commit | Task | Change |
|--------|------|--------|
| `ed198fb7` | 1 | Unify prefix `zeromail.*` → `zero-mail.*` across all 6 stray key families (session.cookie.secure, loadtest.enabled, e2e-stub.enabled ×5 files, admin.webauthn.*, triage.pending-abandoned-threshold, waitlist.ip-hash-pepper) — Java annotation/@Value + yml in the same commit |
| `af18f4fd` | 2 | Delete record-defaulted LLM platform values (keep only `api-key`), redundant `spring.autoconfigure.exclude` yml block (already covered by `excludeName`), no-op prod `format_sql`; annotate the intentional privacy defense-in-depth repeat |
| `87bc3b91` | 3a | Split god-object low-fan-out subtrees → per-feature records (Billing/Gmail/Admin) |
| `4557f52e` | 3b | Relocate LLM subtree → `core.llm.config.LlmProperties` (platform+byok), delete `ZeroMailCoreProperties` god-object |
| `5bd003ae` | 4 | Drop redundant `ZeroMail` class-name prefix on module records (Api/Worker/Drift/Chat Properties) |
| `f3789ba1` | 5 | Create `zero-mail-shared.yml` (Spring-AI privacy/suppression block) + `spring.config.import` in both base ymls + document convention bend in CONVENTIONS.md |
| `2bc7cd88` | fix | **Relocate `CryptoProperties` out of the invented `core.crypto.config` package into `core.shared.crypto`** next to its ciphers (PlatformSecretCipher/Hashing/RefreshTokenCryptoConfig) |

## The crypto fix (this session)

Tasks 1–5 were executed by a separate run (parallel session — usual pattern); it left the 6 commits but no SUMMARY and did not finalize. On review, one placement defect: the god-object split put `CryptoProperties` (a single 1-field record) in a **brand-new top-level package `com.zeromail.core.crypto.config`** that held nothing else — orphaned from the actual crypto code in `core.shared.crypto` / `core.gmail.persistence.crypto`.

**Audit of all 5 extracted records** — only crypto was misplaced:

| Record | Package | Feature root pre-existed? | Verdict |
|--------|---------|---------------------------|---------|
| LlmProperties | `core.llm.config` | ✅ 94 other files | OK |
| BillingProperties | `core.billing.config` | ✅ 62 | OK |
| GmailProperties | `core.gmail.config` | ✅ 37 | OK |
| AdminProperties | `core.admin.config` | ✅ 150 | OK |
| CryptoProperties | `core.crypto.config` | ❌ 0 — invented | **moved → `core.shared.crypto`** |

Bound key `zero-mail.crypto.refresh-token-key-base64` unchanged; masked `toString()` + `@Validated` preserved; empty `core.crypto` package removed; 3 consumer imports updated (RefreshTokenCryptoConfig, RecentInboxReadService, RecentInboxReadServiceTest).

## Verification

- ✅ Full compile gate green: `:backend:core:{compileJava,compileTestJava,compileAiEvalJava} :backend:api:{compileJava,compileTestJava} :backend:worker:{compileJava,compileTestJava}` (aiEval source set included — it is NOT covered by `:backend:core:test`).
- ✅ Config-binding + crypto-consumer tests green: `ZeroMailLlmPropertiesTest`, `ZeroMailLlmByokPropertiesBindingTest`, `RecentInboxReadServiceTest`, `RestClientConfigTest`.
- ✅ JetBrains `get_file_problems` clean on all touched Java files.
- ✅ Zero stray `zeromail.` property keys remain (only `id("zeromail.*")` Gradle plugin ids + `com.zeromail.*` packages, both correct).
- ✅ Highest-risk regression check (static pair-match): every `@ConditionalOnProperty(name="zero-mail.e2e-stub.enabled"/"zero-mail.loadtest.enabled")` matches its yml key under a single `zero-mail:` block; `@Value("${zero-mail.session.cookie.secure}")` matches api yml `zero-mail.session.cookie.secure` (env var name `ZEROMAIL_SESSION_COOKIE_SECURE` preserved, relaxed-binding maps it).
- ✅ No value drift: `LlmProperties` binding test confirms provider/base-url/4 models/timeouts resolve identically from record defaults after the yml literals were deleted.

## Not exhaustively run this session

- Live `bootRun` of both modules (api 8080 / worker 8081) and the full per-module test suites were not run end-to-end here — compile gate + targeted binding tests + static key-coherence cover the refactor's risk surface. Run `:backend:api:test :backend:worker:test :backend:core:test` for a full pass before merge if desired.
