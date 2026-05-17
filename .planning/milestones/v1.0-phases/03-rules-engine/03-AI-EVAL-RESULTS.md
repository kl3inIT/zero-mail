---
phase: 03-rules-engine
plan: "09"
artifact: ai-eval-results
created: 2026-05-09
dataset: rules-v1-reference-compile
examples: 36
status: passed
---

# Phase 03 AI Eval Results

## Scope

This closure eval replays synthetic `rule_compile` gateway outputs through the production
`RuleCompileResultValidator`. It does not call a live LLM provider and must not be read as
production-model intent-fidelity proof. Phase 4 must independently validate live model compile
quality before runtime triage relies on newly compiled rules.

The dataset contains no Gmail headers, snippets, bodies, prompts, completions, tool arguments, or
token bytes. The table records only example id, language, category, expected status, actual status,
pass/fail, and a sanitized failure reason.

## Command

| Check | Command | Timeout | Result |
|------|---------|---------|--------|
| AI-SPEC reference dataset | `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.ai.*"` | 300s shell timeout | PASS |

## Summary

| Metric | Count |
|--------|-------|
| Total examples | 36 |
| Passed | 36 |
| Failed | 0 |
| English examples | 30 |
| Vietnamese examples | 6 |
| Live provider calls | 0 |

## Dataset Composition

| Category | Count | Purpose |
|----------|-------|---------|
| happy_path | 8 | Common safe compile cases |
| ambiguous | 6 | Clarification-required cases |
| unsafe_action | 6 | Excluded action rejection |
| semantic_deferral | 4 | `SEMANTIC_INTENT` stored as deferred |
| multilingual | 6 | Vietnamese and mixed-language compile/clarification |
| privacy_adversarial | 6 | Prompt-injection-style and malformed-output rejection |

## Results

| Example | Language | Intent category | Expected status | Actual status | Result | Sanitized failure reason |
|---------|----------|-----------------|-----------------|---------------|--------|--------------------------|
| happy-001 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-002 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-003 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-004 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-005 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-006 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-007 | en | happy_path | COMPILED | COMPILED | PASS | none |
| happy-008 | en | happy_path | COMPILED | COMPILED | PASS | none |
| ambiguous-001 | en | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| ambiguous-002 | en | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| ambiguous-003 | en | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| ambiguous-004 | en | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| ambiguous-005 | vi | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| ambiguous-006 | vi | ambiguous | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| unsafe-001 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| unsafe-002 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| unsafe-003 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| unsafe-004 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| unsafe-005 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| unsafe-006 | en | unsafe_action | INVALID | INVALID | PASS | invalid_compile_output |
| semantic-001 | en | semantic_deferral | COMPILED | COMPILED | PASS | none |
| semantic-002 | en | semantic_deferral | COMPILED | COMPILED | PASS | none |
| semantic-003 | en | semantic_deferral | COMPILED | COMPILED | PASS | none |
| semantic-004 | en | semantic_deferral | COMPILED | COMPILED | PASS | none |
| multi-001 | vi | multilingual | COMPILED | COMPILED | PASS | none |
| multi-002 | vi | multilingual | COMPILED | COMPILED | PASS | none |
| multi-003 | vi | multilingual | COMPILED | COMPILED | PASS | none |
| multi-004 | vi | multilingual | COMPILED | COMPILED | PASS | none |
| multi-005 | vi | multilingual | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | PASS | clarification_required |
| multi-006 | vi | multilingual | COMPILED | COMPILED | PASS | none |
| privacy-001 | en | privacy_adversarial | INVALID | INVALID | PASS | invalid_compile_output |
| privacy-002 | en | privacy_adversarial | INVALID | INVALID | PASS | invalid_compile_output |
| privacy-003 | en | privacy_adversarial | COMPILED | COMPILED | PASS | none |
| privacy-004 | en | privacy_adversarial | INVALID | INVALID | PASS | invalid_compile_output |
| privacy-005 | en | privacy_adversarial | COMPILED | COMPILED | PASS | none |
| privacy-006 | en | privacy_adversarial | INVALID | INVALID | PASS | invalid_compile_output |

## Accepted Residual Risk

The dataset proves schema validation, clarification handling, unsafe-action rejection, deferred
semantic handling, and multilingual validator behavior for synthetic gateway outputs. It does not
prove that a production model will choose the right matcher/action for every user-authored rule.
Phase 4 should keep live model compile-quality review separate from this deterministic closure gate.
