---
phase: quick-260511-jrq
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - gradle/libs.versions.toml
  - gradlew
  - gradlew.bat
  - gradle/wrapper/gradle-wrapper.jar
  - gradle/wrapper/gradle-wrapper.properties
  - .github/workflows/e2e.yml
  - apps/web/package.json
  - package.json
  - pnpm-lock.yaml
  - CLAUDE.md
  - AGENTS.md
  - .planning/PROJECT.md
  - .planning/research/STACK.md
  - .planning/REQUIREMENTS.md
  - .planning/ROADMAP.md
  - .planning/research/PITFALLS.md
  - .planning/research/SUMMARY.md
  - .planning/research/ARCHITECTURE.md
  - .planning/phases/04-triage-convergence-hero/04-AI-SPEC.md
  - .planning/phases/04-triage-convergence-hero/04-RESEARCH.md
  - .planning/phases/04-triage-convergence-hero/04-CONTEXT.md
  - .planning/phases/04-triage-convergence-hero/04-03-PLAN.md
autonomous: true
requirements: [LLM-01]

must_haves:
  truths:
    - "gradle/libs.versions.toml pins springAi 2.0.0-M6 and googleAuthLibrary 1.47.0"
    - "Gradle wrapper is 9.5.0 (distributionUrl + scripts + jar regenerated)"
    - ".github/workflows/e2e.yml uses actions/upload-artifact@v7"
    - "apps/web/package.json + root package.json carry the PR #25 web bumps and pnpm-lock.yaml is consistent"
    - "backend/core compiles (main + test) against OpenAI SDK 4.34.0 / Anthropic SDK 2.30.0"
    - "Forward-looking docs reference Spring AI 2.0.0-M6 (not M5), Gradle 9.5.0, googleAuthLibrary 1.47.0"
    - "Dependabot major-version PRs #27 (eslint 10) and #26 (lint-staged 17) are NOT applied"
  artifacts:
    - path: "gradle/libs.versions.toml"
      provides: "Version pins for Spring AI M6 + googleAuthLibrary 1.47.0"
      contains: "2.0.0-M6"
    - path: "gradle/wrapper/gradle-wrapper.properties"
      provides: "Gradle 9.5.0 distribution + retry settings"
      contains: "gradle-9.5.0-bin.zip"
    - path: ".github/workflows/e2e.yml"
      provides: "upload-artifact v7"
      contains: "actions/upload-artifact@v7"
  key_links:
    - from: "gradle/libs.versions.toml (springAi M6)"
      to: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokModelClient.java"
      via: "transitive Anthropic Java SDK 2.30.0 — compile must pass"
      pattern: "com\\.anthropic\\.models\\.messages"
---

<objective>
Bump dependency pins (Spring AI 2.0.0-M5 → 2.0.0-M6, googleAuthLibrary 1.35.0 → 1.47.0), apply the four safe Dependabot PRs (#23 Gradle wrapper 9.5.0, #28 upload-artifact v7, #25 web minor/patch group; #24 is the Spring AI bump itself), verify backend/core + apps/web build cleanly, and sync forward-looking docs from M5 → M6. Hold the two major-version Dependabot PRs (#27 eslint 10, #26 lint-staged 17).

Purpose: Stay on the current Spring AI 2.0.0 milestone line and absorb low-risk dependency drift before Phase 04 work begins. The real verification target is the transitive OpenAI/Anthropic Java SDK bump against `AnthropicByokModelClient`.

Output: Updated version catalog + wrapper + workflow + web manifests + lockfile (one commit), and version-retargeted docs (one or two `docs:` commits — the orchestrator handles the PLAN/SUMMARY/STATE docs commit, not this plan).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md
@gradle/libs.versions.toml

<notes>
- Research is DONE — do not re-investigate Spring AI M6 changelog. M6 breaking changes (builder-only ChatOptions, PromptChatMemoryAdvisor removal, vector-store removals, OpenAiConnectionProperties rename, embedding encodingFormat enum) do NOT touch this codebase: every usage site under `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/` already uses `*ChatOptions.builder()`, and the project uses no vector stores / chat-memory advisors / embeddings and does not bind `spring.ai.openai.*` connection properties (it builds `OpenAiChatModel` manually).
- The only real risk: M6 bumps OpenAI Java SDK → 4.34.0 and Anthropic Java SDK → 2.30.0; `AnthropicByokModelClient` imports `com.anthropic.models.messages.{Model, ToolChoice, ToolChoiceAny}` directly → compile-verify of `backend/core` (main + test) is mandatory.
- 6 open Dependabot PRs: #24 (springAi M5→M6 = task 1), #23 (gradle-minor-patch: googleAuthLibrary 1.35.0→1.47.0 + Gradle wrapper 9.4.1→9.5.0), #25 (web-minor-patch group, 11 updates), #28 (actions/upload-artifact 5→7), #27 (eslint 9.39.4→10.3.0 — MAJOR, HOLD), #26 (lint-staged 16.4.0→17.0.4 — MAJOR, HOLD).
- PR #25 exact set — `apps/web/package.json`: `next` 16.2.4→16.2.6, `next-intl` ^4.11.0→^4.11.1, `react` 19.2.5→19.2.6, `react-dom` 19.2.5→19.2.6, `shadcn` ^4.6.0→^4.7.0, `tailwind-merge` ^3.5.0→^3.6.0, `eslint-config-next` 16.2.4→16.2.6; root `package.json`: `turbo` 2.9.9→2.9.12; plus matching `pnpm-lock.yaml` updates.
- PR #23 also writes `retries=0` / `retryBackOffMs=500` into `gradle/wrapper/gradle-wrapper.properties` and updates `gradlew` / `gradlew.bat` to the 9.5.0 templates.
- Do NOT `gh pr merge` any Dependabot PR — apply changes locally on the current branch (`gsd/phase-03-rules-engine`); the PRs become superseded/closeable by the maintainer.
- Do NOT commit PLAN.md / SUMMARY.md / STATE.md — the orchestrator owns that commit. This plan's commits are: (1) `chore(deps): ...` for task 1, (2) one or two `docs: ...` commits for task 2/3.
- Do NOT touch historical phase records: leave `.planning/phases/02C-llm-gateway/*` and `.planning/phases/03-rules-engine/*` unchanged; leave `.planning/STATE.md` historical log lines (the phase 2A/2C research line ~271, the `260508-g41` quick-task row ~286) alone.
</notes>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Bump version pins + apply safe Dependabot bumps, then verify builds</name>
  <files>gradle/libs.versions.toml, gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties, .github/workflows/e2e.yml, apps/web/package.json, package.json, pnpm-lock.yaml</files>
  <action>
1. Edit `gradle/libs.versions.toml`: change `springAi = "2.0.0-M5"` → `springAi = "2.0.0-M6"` and `googleAuthLibrary = "1.35.0"` → `googleAuthLibrary = "1.47.0"`. Leave every other pin (including `springModulith = "2.0.7-SNAPSHOT"`) untouched.
2. Apply PR #23's Gradle-wrapper bump to 9.5.0. Preferred: run the wrapper task twice so the jar + scripts regenerate from the new distribution — `./gradlew wrapper --gradle-version 9.5.0 --distribution-type bin` then `./gradlew wrapper --gradle-version 9.5.0 --distribution-type bin` again. If that is not workable in the executor environment, pull the four wrapper files straight from PR #23: `git fetch origin pull/23/head` then `git checkout FETCH_HEAD -- gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties` (or `gh pr checkout 23 -- <paths>` / `gh pr diff 23`). Ensure `gradle/wrapper/gradle-wrapper.properties` ends up with `distributionUrl=...gradle-9.5.0-bin.zip` plus the `retries=0` and `retryBackOffMs=500` lines that PR #23 adds.
3. Apply PR #28: in `.github/workflows/e2e.yml` change `actions/upload-artifact@v5` → `actions/upload-artifact@v7`.
4. Apply PR #25 web bumps: edit `apps/web/package.json` (`next` 16.2.4→16.2.6, `next-intl` ^4.11.0→^4.11.1, `react` 19.2.5→19.2.6, `react-dom` 19.2.5→19.2.6, `shadcn` ^4.6.0→^4.7.0, `tailwind-merge` ^3.5.0→^3.6.0, `eslint-config-next` 16.2.4→16.2.6) and root `package.json` (`turbo` 2.9.9→2.9.12), then run `pnpm install` to regenerate `pnpm-lock.yaml`. Alternative: `git fetch origin pull/25/head && git checkout FETCH_HEAD -- apps/web/package.json package.json pnpm-lock.yaml`. Either way `pnpm install --frozen-lockfile` must pass afterward.
5. Do NOT apply PR #27 (eslint 10 — major; drops legacy `.eslintrc`; `eslint-config-next` 16 compat unverified; would cascade into `eslint-config-prettier`/plugin bumps) or PR #26 (lint-staged 17 — major). Leave those two PRs open. Record the HOLD decision (with reasons) in SUMMARY.md.
6. Do NOT run `gh pr merge` on any Dependabot PR.
  </action>
  <verify>
  <automated>./gradlew :backend:core:compileJava :backend:core:compileTestJava</automated>
  Then, for broader confidence: `./gradlew check` (or at minimum `./gradlew compileJava` across all modules plus `./gradlew :backend:core:test --tests "*LlmGateway*" --tests "*SpringAi*"`). Web side: `pnpm install --frozen-lockfile` then `pnpm -C apps/web exec tsc --noEmit`, and if cheap `pnpm -C apps/web run lint`. If any Java file shows an error, run `mcp__jetbrains__get_file_problems` on it. If `compileJava`/`compileTestJava` or `tsc --noEmit` fails: STOP — surface the failure and root cause; do NOT proceed to Task 2.
  </verify>
  <done>`gradle/libs.versions.toml` pins `springAi = "2.0.0-M6"` + `googleAuthLibrary = "1.47.0"`; `gradle-wrapper.properties` points at `gradle-9.5.0-bin.zip` with the retry lines and `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` updated; `.github/workflows/e2e.yml` uses `actions/upload-artifact@v7`; `apps/web/package.json` + root `package.json` carry the exact PR #25 versions and `pnpm-lock.yaml` is consistent (`pnpm install --frozen-lockfile` passes); `./gradlew :backend:core:compileJava :backend:core:compileTestJava` passes and `pnpm -C apps/web exec tsc --noEmit` passes; PRs #27 and #26 untouched. Committed as `chore(deps): bump Spring AI 2.0.0-M6, googleAuthLibrary 1.47.0, Gradle 9.5.0, web minor/patch group, upload-artifact v7`.</done>
</task>

<task type="auto">
  <name>Task 2: Sync core-repo + research + planning docs M5 → M6</name>
  <files>CLAUDE.md, AGENTS.md, .planning/PROJECT.md, .planning/research/STACK.md, .planning/REQUIREMENTS.md, .planning/ROADMAP.md, .planning/research/PITFALLS.md, .planning/research/SUMMARY.md, .planning/research/ARCHITECTURE.md</files>
  <action>
Pure version retarget — replace `2.0.0-M5`→`2.0.0-M6`, `M5 → GA` / `M5→GA churn` / `M5 -> GA`→`M6 → GA` / `M6→GA churn` / `M6 -> GA`, `Spring AI M5 requires` / `when Spring AI M5`→`Spring AI M6` wording, and where a doc lists Gradle `9.4.1`→`9.5.0` / `googleAuthLibrary 1.35.0`→`1.47.0`. Keep all surrounding substance.
- `CLAUDE.md`: Constraints §"Versioning policy" exception line; the "AI: Spring AI ..." line; the Technology-Stack TL;DR Spring AI line; the "Hard do not use" list line about "when Spring AI M5 requires it"; the Gradle `9.4.1` mention in the stack TL;DR.
- `AGENTS.md`: same lines (mirror of CLAUDE.md).
- `.planning/PROJECT.md`: the two Constraints lines.
- `.planning/research/STACK.md`: every `2.0.0-M5` / `M5` / `M5→GA` / `M5 -> GA` occurrence; the `springAi = "2.0.0-M5"` snippet; the Gradle `9.4.1` mention; `googleAuthLibrary 1.35.0` if listed; in the Sources section replace the M5 release link with `https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M6 — Spring AI 2.0.0-M6 release (2026-05-08)`.
- `.planning/REQUIREMENTS.md`: LLM-01 "built on Spring AI 2.0.0-M5" → M6.
- `.planning/ROADMAP.md`: the Phase 2C bullet + Phase 2C section Goal / Research-flag lines + the JHipster note line — M5→M6. Do NOT change any phase checkbox / status.
- `.planning/research/PITFALLS.md`: Pitfall 23 — title/body "pinning 2.0.0-M5"→"2.0.0-M6"; the release-date sentence "released on April 27, 2026"→"M6 released on May 8, 2026" (M5 GA'd 2026-04-27); "Pin to exactly 2.0.0-M5"→"2.0.0-M6"; "M5 -> GA"→"M6 -> GA" throughout; the summary tables (~lines 698 and 816); the Sources link (~line 849) → M6 release URL.
- `.planning/research/SUMMARY.md`: the three M5 mentions.
- `.planning/research/ARCHITECTURE.md`: the M5 adapter-seams line.
Do NOT touch `.planning/phases/02C-llm-gateway/*`, `.planning/phases/03-rules-engine/*`, or `.planning/STATE.md` historical log lines.
  </action>
  <verify>
  <automated>grep -rn "2\.0\.0-M5" CLAUDE.md AGENTS.md .planning/PROJECT.md .planning/research/STACK.md .planning/REQUIREMENTS.md .planning/ROADMAP.md .planning/research/PITFALLS.md .planning/research/SUMMARY.md .planning/research/ARCHITECTURE.md</automated>
  Expect zero matches (every forward-looking M5 reference retargeted). Also `grep -rn "M5" .planning/research/PITFALLS.md` should show no remaining "Spring AI M5" / "M5 -> GA" / "pinning ... M5" references (a bare "M5" inside a historical date sentence is acceptable only if it reads "M5 GA'd 2026-04-27"). Spot-read each file's changed region to confirm substance is intact.
  </automated></verify>
  <done>No `2.0.0-M5` string remains in any of the listed core/research/planning docs; Spring AI references read `2.0.0-M6`; STACK.md + PITFALLS.md Sources sections include the M6 release URL (`https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M6`); Gradle `9.4.1` / `googleAuthLibrary 1.35.0` mentions updated where present; historical phase records and STATE.md log lines untouched; phase checkboxes/status in ROADMAP.md unchanged. Committed as a `docs:` commit (e.g. `docs: retarget Spring AI M5 → M6 across project + research docs`).</done>
</task>

<task type="auto">
  <name>Task 3: Sync Phase 04 docs M5 → M6</name>
  <files>.planning/phases/04-triage-convergence-hero/04-AI-SPEC.md, .planning/phases/04-triage-convergence-hero/04-RESEARCH.md, .planning/phases/04-triage-convergence-hero/04-CONTEXT.md, .planning/phases/04-triage-convergence-hero/04-03-PLAN.md</files>
  <action>
Pure version retarget in the Phase 04 forward-looking docs only. Replace `2.0.0-M5`→`2.0.0-M6`; `M5 → GA` / `M5→GA churn` / `mid M5→GA`→`M6 → GA` / `M6→GA churn` / `mid M6→GA`; the `spring-ai-bom:2.0.0-M5` snippet in `04-AI-SPEC.md`→`2.0.0-M6`; "M5 ships `org.springframework.ai.converter.BeanOutputConverter`. GA candidates may relocate"→keep substance, say "M6 ships ..."; "Spring AI 2.0.0-M5 emits OTel-compatible spans"→M6. Keep all surrounding substance — this is a pure version retarget, not a content rewrite. Do NOT touch any other phase folder.
  </action>
  <verify>
  <automated>grep -rn "2\.0\.0-M5\|M5 ships\|M5 → GA\|M5→GA" .planning/phases/04-triage-convergence-hero/</automated>
  Expect zero matches. Spot-read the changed regions of `04-AI-SPEC.md` (the bom snippet + BeanOutputConverter line + OTel-spans line) to confirm wording is intact.
  </automated></verify>
  <done>No `2.0.0-M5` / "M5 ships" / "M5 → GA" reference remains under `.planning/phases/04-triage-convergence-hero/`; the `04-AI-SPEC.md` bom snippet reads `spring-ai-bom:2.0.0-M6`; surrounding substance preserved; no other phase folder modified. Committed as a `docs:` commit (e.g. `docs(phase-04): retarget Spring AI M5 → M6`). May be folded into Task 2's commit instead if the executor prefers a single docs commit — either is acceptable.</done>
</task>

</tasks>

<verification>
- `gradle/libs.versions.toml` → `springAi = "2.0.0-M6"`, `googleAuthLibrary = "1.47.0"`.
- `gradle/wrapper/gradle-wrapper.properties` → `distributionUrl=...gradle-9.5.0-bin.zip` + `retries` / `retryBackOffMs` lines; `gradlew` / `gradlew.bat` / `gradle-wrapper.jar` regenerated.
- `.github/workflows/e2e.yml` → `actions/upload-artifact@v7`.
- `apps/web/package.json` + root `package.json` carry the exact PR #25 versions; `pnpm install --frozen-lockfile` passes.
- `./gradlew :backend:core:compileJava :backend:core:compileTestJava` passes (mandatory — proves OpenAI 4.34.0 / Anthropic 2.30.0 SDK compat). Broader `./gradlew check` passes.
- `pnpm -C apps/web exec tsc --noEmit` passes.
- `grep -rn "2\.0\.0-M5"` across the forward-looking docs returns nothing; PITFALLS.md / STACK.md Sources include the M6 release URL.
- Dependabot PRs #27 (eslint 10) and #26 (lint-staged 17) NOT applied; no `gh pr merge` run.
- Historical phase records (`02C-*`, `03-*`) and STATE.md log lines untouched.
</verification>

<success_criteria>
- Dependency pins bumped (Spring AI M6, googleAuthLibrary 1.47.0), Gradle wrapper at 9.5.0, upload-artifact at v7, web minor/patch group applied, lockfile consistent.
- `backend/core` compiles main + test; `apps/web` typechecks.
- All forward-looking docs reference Spring AI 2.0.0-M6 (none reference M5).
- Major-version Dependabot PRs held; their HOLD reasons recorded in SUMMARY.md.
- Commits: one `chore(deps): ...` (task 1) + one or two `docs: ...` (task 2 / task 3). PLAN/SUMMARY/STATE docs commit is the orchestrator's responsibility, not this plan's.
</success_criteria>

<output>
After completion, create `.planning/quick/260511-jrq-spring-ai-2-0-0-m6-upgrade-and-sync-safe/260511-jrq-SUMMARY.md` (using the summary template). Record: what bumped, the compile/typecheck verification result, and the explicit HOLD on Dependabot PRs #27 and #26 with reasons.
</output>
