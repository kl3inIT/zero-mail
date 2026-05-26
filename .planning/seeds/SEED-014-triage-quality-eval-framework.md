---
id: SEED-014
status: dormant
planted: 2026-05-21
last_refreshed: 2026-05-26
planted_during: Phase 8 admin console research detour into Spring AI Community repos
trigger_when: "before any phase that ships AI auto-triage, auto-categorize, or auto-draft to a real user inbox"
scope: medium
---

# SEED-014: Triage Quality Eval Framework (agent-judge based)

## Why This Matters

Zero Mail's core value statement (project CLAUDE.md) is literally:

> "AI auto-triage that users trust with their real inbox. If triage quality, safety (no destructive actions, no data leakage), and reliability aren't excellent, nothing else matters."

We currently have **no objective measurement** of triage quality. Shipping the email-content pipeline phase without an eval rubric means we discover regressions in production via user uninstalls — the most expensive feedback loop possible.

`spring-ai-community/agent-judge` (currently v0.9.1 on Maven Central) is a composable judge framework with **4 modules** — `agent-judge-core` (zero-deps), `agent-judge-exec` (build/test command judges), `agent-judge-llm` (Spring AI ChatModel judges), `agent-judge-file` (AST / POM / XML / text diff judges). Judges compose into a `Jury` with voting strategies (majority / unanimous / weighted). Compatible with Spring AI 2.0.0-M7 (Zero Mail's pinned version) via `agent-judge-llm`. Combined with a curated reference dataset (synthetic inboxes + opt-in real samples with redaction), we can:

- Score every rule-engine output (label / archive / save-draft) for correctness vs expected.
- Score every chat-assistant draft for tone, factual grounding, instruction-following.
- Track regression across model changes (OpenRouter routing changes, M6 → GA churn).
- Gate releases on min eval score.

Without this, "trust" is aspirational.

## When to Surface

**Trigger before:** any phase that ships AI auto-triage, auto-categorize, or auto-draft to a real user inbox. Specifically — before the v1.x phase that wires triage LLM calls into the rules engine for real Pub/Sub-delivered mail.

Also surface when adding a second LLM provider (BYOK launch) — we'll need cross-provider quality comparison.

## Scope Estimate

**Medium**. One phase:
- Reference dataset construction (synthetic emails covering: marketing, transactional, work, personal, edge cases, prompt-injection attempts, ambiguous-tone, multi-language VN/EN).
- Rubric design per dimension (correctness, tone, safety, prompt-injection resistance, instruction-following).
- `agent-judge` integration as Spring AI module — likely behind `@Tag("llm-eval")` Gradle task.
- CI gate: PR-level eval run on a stratified sample; nightly full-run.
- Dashboard: track scores over time per model + per rule.

## Candidate Product Shape

- `backend/core` module `eval/` (or separate `backend/eval`) with `RuleTriageJudge`, `DraftQualityJudge`, `SafetyJudge`.
- Reference dataset as fixture files (sanitized, never real user mail).
- Gradle task `evalLlm` separate from `test` per TESTING.md rule.
- Optional: admin console panel surfaces last eval run + score trend.

## Safety Rules

- Reference dataset = synthetic OR fully-redacted opt-in samples only. No raw user mail.
- Judge LLM provider may differ from target LLM provider (avoid self-grading bias).
- Eval prompts/completions follow same privacy rules as production (no logging of bodies).
- Eval results stored as scores + categorical findings, never raw model output content.

## Companion: `agent-bench` lifecycle pattern

`spring-ai-community/agent-bench` orchestrates benchmark runs with this shape:
`provide → setup → agent → post → grade → result.json`, graded by **cascaded jury tiers** (each tier with `policy: REJECT_ON_ANY_FAIL`). Don't adopt the whole framework — steal the lifecycle + cascaded tier policy for the triage eval harness:

- **Tier 1 (deterministic)** = safety/privacy judges. Any fail → reject. Examples: `NoDestructiveActionJudge`, `LogPrivacyLeakRegexJudge`, `BodyContentBanJudge`.
- **Tier 2 (LLM)** = semantic correctness. `LabelCorrectnessJudge`, `ToneFidelityJudge`, `PromptInjectionResistanceJudge`. Voting = majority.
- **Tier 3 (cross-provider)** = run same fixture against 2 providers (OpenAI vs Anthropic) for regression detection across BYOK swaps.

Fixture format: `eval/fixtures/inbox-<NN>/{email.json, expected.json}`. Result artifact format identical across model providers → enables side-by-side OpenRouter routing regression checks.

## Library vs In-house (decide at trigger time)

`agent-judge` is composable primitives (`Judge` interface + `Jury` voting) — the pattern is simple. Two paths:

- **Adopt library** — pull `agent-judge-core` + `agent-judge-llm`, write project-specific judges as `Judge` impls. ~50 LOC project code + library churn risk (v0.9.1 = pre-1.0, API may shift).
- **In-house** — reimplement `Judge` + `Jury` in `backend/eval` (~200 LOC, mostly interfaces + voting). Direct access to Zero Mail audit format, no external dep. Lose `agent-judge-llm`'s ChatModel wiring (rewrite a small wrapper around `LlmGateway`).

**Recommendation:** in-house if Zero Mail wants the framework wired into existing `LlmGateway` + audit primitives without an extra adapter layer. Library if speed-to-eval matters more than coupling control.

## Open Questions

- Use `agent-judge` direct, fork, or in-house? **Verified 2026-05-26:** Spring AI 2.0.0-M7 compatibility via `agent-judge-llm` module (uses `ChatModel` abstraction, no version-specific API). In-house viable since the pattern is small.
- Should triage eval block CI or only gate release branch?
- How to handle multi-language VN/EN rubric scoring without judge LLM bias.

## References

- `spring-ai-community/agent-judge` (v0.9.1 — `agent-judge-core` / `-exec` / `-llm` / `-file` modules)
- `spring-ai-community/agent-bench` (cascaded jury tier pattern + benchmark lifecycle)
- `spring-ai-community/awesome-spring-ai` (eval section)
- Project CLAUDE.md "Core Value" section + TESTING.md `@Tag("llm-eval")` discipline
- Memory: `reference-ai-research-repos`
