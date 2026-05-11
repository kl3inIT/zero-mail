# semantic-intent-eval

Wave 0 reserves the Phase 4 semantic-intent evaluation harness location.

- `fixtures/*.json` contains hand-authored sanitized messages, rulesets, intent lists, and ground truth.
- `cassettes/*.json` contains recorded structured LLM JSON responses for offline CI replay.
- Tests in this harness use `@Tag("semantic-intent-eval")`.
- Ownership follows `04-AI-SPEC.md` §5: the eval-auditor owns the 35-fixture dataset and cassette content; production traffic, raw user mail, prompts, and completions must never be committed here.

The `semanticIntentEval` Gradle task registration is deferred to plan 04-03.
