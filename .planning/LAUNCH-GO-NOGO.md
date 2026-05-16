# Launch Go/No-Go - Zero Mail v1.0.0-rc1

*Decision artifact for the v1.0.0-rc1 release candidate. Owned by the project owner. Items checked once the corresponding evidence resolves.*

## Launch Mode

OAuth consent screen: **Testing** (100-user cap). Production move is gated on CASA, deferred to a post-launch track (see item (h)).

## Decision Checklist

- [x] **(a) Playwright golden-path spec green on RC tag.** Link to CI run: [Release Gates run #25903268052](https://github.com/kl3inIT/zero-mail/actions/runs/25903268052)
- [x] **(b) 50-tenant load test invariants all PASS.** Link to result: [`.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md`](./phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md)
- [x] **(c) Prompt-injection regression suite green on RC tag.** Re-verified by the `Gates / backend` job in `Release Gates` (it runs `./gradlew check`, which includes the prompt-injection suite from Phase 2C).
- [x] **(d) ArchUnit suite green on RC tag.** Re-verified by the same `Gates / backend` job (includes `DraftPathArchUnitTest`, `I18nArchUnitTest`, `LaunchProfileArchUnitTest` from Plan 06-01).
- [x] **(e) Spring Modulith `ApplicationModulesTest` green on RC tag.** Re-verified by `Gates / backend` (`ZeroMailApiApplicationModulesTest` runs as part of `check`).
- [x] **(f) LLM golden-set drift check green on RC tag.** Re-verified by `Gates / ai-eval` (`:backend:core:aiEval -PdeterministicOnly`).
- [x] **(g) Trust story re-affirmed in writing:** **auto-send forbidden**, **no stored bodies / prompts / completions**, **every triage action undoable**.
- [x] **(h) Launch mode = OAuth "Testing" (Production move deferred).** Tracking: [`.planning/seeds/SEED-012-casa-restricted-scope-verification.md`](./seeds/SEED-012-casa-restricted-scope-verification.md)

## Evidence

| Item | Artifact | Authoritative source |
|------|----------|----------------------|
| (a) | `Release Gates / golden-path` job | `.github/workflows/release.yml` |
| (b) | `06-LOAD-TEST-RESULT.md` (committed) + `Release Gates / loadtest` job | `.planning/phases/06-polish-casa-verified-launch/06-02-PLAN.md` Task 4 |
| (c) | `Gates / backend` job (includes prompt-injection regression) | Phase 2C |
| (d) | `Gates / backend` job (ArchUnit rules) | Phase 1 + 1.2 + Plan 06-01 |
| (e) | `Gates / backend` job (`ApplicationModulesTest`) | Phase 1.2 |
| (f) | `Gates / ai-eval` job (`aiEval -PdeterministicOnly`) | Phase 2C |
| (g) | This document | CLAUDE.md + REQUIREMENTS.md DRFT-04 + LLM-09 + PROJECT.md |
| (h) | This document + SEED-012 | D-09 |

## Notes

- Item (a) stays unchecked until the RC run-id and URL are known from a green `Release Gates` run.
- Item (b) points at committed load-test evidence from Plan 06-02 and stays linked here for the launch review.
- Items (c) through (f) are re-verified by the reusable `Gates` workflow on every RC tag push.
- Item (g) is load-bearing and must remain verbatim in lowercase as written here.
- Item (h) keeps OAuth in Testing mode until the CASA post-launch track closes SEED-012.
- The sign-off line stays as a placeholder comment until the operator completes the final release steps.
- The final `v1.0.0-rc1` tag is pushed exactly once on the signed commit after all boxes are checked.

## Sign-off

*Operator adds the sign-off line below as the LAST EDIT before pushing the rc1 tag. Format is exact and committed verbatim.*

✓ signed-off by @kl3inIT on 2026-05-15
