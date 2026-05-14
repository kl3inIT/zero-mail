# Draft Eval Fixtures

Synthetic fixture seeds for Phase 5B draft-reply eval dimensions.

Rules:

- Fixtures are synthetic only. Do not commit real Gmail subjects, addresses, bodies, prompts, completions, or token bytes.
- Reserved synthetic domains such as `synthetic.test` are allowed when an address-shaped value is required by MIME tests.
- The deterministic evals use these as the reference dataset inventory; LLM judge dimensions 1, 2, 3, and 5 reuse the same inventory once judge calibration is available.

Composition:

- Critical paths: scheduling, multi-question answer, polite decline.
- Failure-mode cases: hallucinated commitment trap, inbound injection, sent-tone injection, sensitive escalation, job-offer ambiguity, vendor break-up, legal-adjacent ambiguity.
- Threading edges: no prior `References`, already `Re:` prefixed subject, Vietnamese subject, missing `Message-ID`.
- Content edges: one-line thread, long inbound, Vietnamese reply, tone-context budget overflow, Gmail tone fetch failure.
- Adversarial pair: overlapping participant fixtures that must not bleed content across threads.
