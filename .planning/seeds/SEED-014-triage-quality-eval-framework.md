---
id: SEED-014
status: dormant
planted: 2026-05-21
planted_during: Phase 8 admin console research detour into Spring AI Community repos
trigger_when: "before any phase that ships AI auto-triage, auto-categorize, or auto-draft to a real user inbox"
scope: medium
---

# SEED-014: Triage Quality Eval Framework (agent-judge based)

## Why This Matters

Zero Mail's core value statement (project CLAUDE.md) is literally:

> "AI auto-triage that users trust with their real inbox. If triage quality, safety (no destructive actions, no data leakage), and reliability aren't excellent, nothing else matters."

We currently have **no objective measurement** of triage quality. Shipping the email-content pipeline phase without an eval rubric means we discover regressions in production via user uninstalls — the most expensive feedback loop possible.

`spring-ai-community/agent-judge` is an LLM-as-judge framework for evaluating agent outputs. It fits Spring AI 2.0.0-M6 directly. Combined with a curated reference dataset (synthetic inboxes + opt-in real samples with redaction), we can:

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

## Open Questions

- Use `agent-judge` direct or fork? Spring AI 2.0.0-M6 compatibility unknown.
- Should triage eval block CI or only gate release branch?
- How to handle multi-language VN/EN rubric scoring without judge LLM bias.

## References

- `spring-ai-community/agent-judge`
- `spring-ai-community/awesome-spring-ai` (eval section)
- Project CLAUDE.md "Core Value" section
- Memory: `reference-ai-research-repos`
