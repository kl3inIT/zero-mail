# semantic-intent-eval

Phase 04 plan 03 registers the `semanticIntentEval` Gradle task for the offline semantic-intent
evaluation harness. The task runs JUnit tests tagged with `@Tag("semantic-intent-eval")` and is
expected to be green without live LLM access by replaying recorded cassettes.

## Directory Contract

- `fixtures/*.json` contains hand-authored sanitized messages, rulesets, intent lists, and ground truth.
- `cassettes/*.json` contains recorded structured LLM JSON responses for offline CI replay.
- Tests in this harness use `@Tag("semantic-intent-eval")`.
- Cassettes are committed to git; live recording is opt-in only.
- Production traffic, raw user mail, prompts, and completions must never be committed here.

## Fixture Composition

The eval-auditor owns the 35-fixture dataset and cassette content described in
`04-AI-SPEC.md` Section 5:

- `must_stay_in_inbox`: 12 fixtures.
- `routine_newsletter`: 4 fixtures.
- `transactional_looks_like_newsletter`: 6 fixtures.
- `cold_outreach_first_message` plus `cold_outreach_reply_in_active_thread`: 4 fixtures.
- `warm_intro_calendar_invite`: 2 fixtures.
- `prompt_injection_adversarial`: 6 fixtures with paired baselines.
- `truncation_edge`: 3 fixtures.
- `multi_intent_high_cardinality`: 3 fixtures.
- `deterministic_only_control`: 2 fixtures.
- `llm_failure_simulation`: 3 fixtures.

## Live Recording

PR CI runs offline only. Live cassette refresh is gated by both environment variables:

- `ZEROMAIL_EVAL_LIVE_LLM=true`
- `ZEROMAIL_EVAL_LIVE_BUDGET_USD=<budget>`

Run live refreshes with:

```bash
ZEROMAIL_EVAL_LIVE_LLM=true ZEROMAIL_EVAL_LIVE_BUDGET_USD=0.50 ./gradlew :backend:core:semanticIntentEval --rerun-tasks
```

The PR/offline gate breaks on regressions in AI-SPEC dimensions 1, 3, 4, 5, and 9.
