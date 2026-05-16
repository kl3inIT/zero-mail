---
phase: quick-260511-vok
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - build.gradle.kts
  - package.json
  - CONTRIBUTING.md
  - .git-blame-ignore-revs
  - aosp-format-sample.md
  - "backend/**/*.java"
autonomous: true
requirements: [QUICK-260511-VOK]
must_haves:
  truths:
    - "Running ./gradlew check fails on any backend Java file not in google-java-format AOSP (4-space) style"
    - "./gradlew spotlessApply reformats all backend Java to google-java-format AOSP style"
    - "Staging a backend/**/*.java file triggers spotlessApply via lint-staged on commit"
    - "git blame skips the bulk-reformat commit because its SHA is in .git-blame-ignore-revs"
    - "Contributing docs explain the AOSP formatter + IntelliJ plugin setup"
  artifacts:
    - path: "build.gradle.kts"
      provides: "Spotless plugin + googleJavaFormat().aosp() config for the three backend Java subprojects"
      contains: "com.diffplug.spotless"
    - path: ".git-blame-ignore-revs"
      provides: "git-blame ignore list referencing the pure-reformat commit SHA"
    - path: "CONTRIBUTING.md"
      provides: "Code formatting section (AOSP / Spotless / IntelliJ plugin)"
      contains: "spotlessApply"
  key_links:
    - from: "package.json lint-staged"
      to: "./gradlew spotlessApply"
      via: "backend/**/*.java glob entry"
      pattern: "backend/\\*\\*/\\*\\.java"
    - from: ".git-blame-ignore-revs"
      to: "Commit B (style: reformat backend Java ...)"
      via: "40-char SHA on its own line"
      pattern: "^[0-9a-f]{40}$"
---

<objective>
Adopt google-java-format in **AOSP style (4-space indent / 8-space continuation)** as the canonical Java formatter for the backend, perform a one-time repo-wide reformat, and wire enforcement so future commits stay formatted (Spotless via Gradle `check`, lint-staged pre-commit hook, `.git-blame-ignore-revs`).

Purpose: Consistent, machine-enforced Java formatting that survives across contributors and IDEs; isolate the bulk-reformat in its own commit so `git blame` / GitHub / IntelliJ ignore it.
Output: Updated root `build.gradle.kts` (Spotless), updated root `package.json` (lint-staged glob), new `CONTRIBUTING.md` with a Code formatting section, new `.git-blame-ignore-revs`, deleted `aosp-format-sample.md`, and ~150+ reformatted backend `.java` files. Three commits in a strict order (A: build/config + docs, B: pure reformat, C: blame-ignore-revs).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md
@build.gradle.kts
@settings.gradle.kts
@package.json
@.github/workflows/ci.yml

<facts>
<!-- Verified during planning — executor does not need to re-discover these. -->
- Root `build.gradle.kts` currently: `plugins { base }` + a `tasks.wrapper { gradleVersion = "9.4.1"; distributionType = ... }` block. Nothing else.
- `settings.gradle.kts`: `rootProject.name = "zero-mail"`, `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`, `include("backend:core", "backend:api", "backend:worker")`. Only three subprojects; `apps/web` is NOT a Gradle subproject.
- `package.json` already has: `"prepare": "husky"`, devDeps `husky@9.1.7` + `lint-staged@16.4.0`, and a `lint-staged` object whose entries are all `apps/web/**` globs (DO NOT touch those entries).
- `.husky/pre-commit` already contains `pnpm exec lint-staged` — no hook edits needed.
- CI (`.github/workflows/ci.yml`) runs `./gradlew --no-daemon check` for the backend job. `check` auto-depends on `spotlessCheck`, so CI gates formatting automatically. DO NOT modify `ci.yml`.
- Repo root has NO `README.md` and NO `CONTRIBUTING.md` — only `AGENTS.md`, `CLAUDE.md`, `CONVENTIONS.md`, `aosp-format-sample.md`. Per task spec, since CONTRIBUTING.md does not exist, create `CONTRIBUTING.md` at repo root with a "Code formatting" section (do NOT create a README just for this).
- `aosp-format-sample.md` at repo root is an untracked scratch file — `rm` it (use `git rm --cached` only if `git status` shows it tracked, which it should not be).
- Current branch is `gsd/phase-04-triage-convergence-hero`. STAY on it — do NOT create a branch.
- Run with worktree isolation: do not assume access to any parallel agent's uncommitted state.
</facts>

<kotlin_dsl_shape>
<!-- Reference shape for the Spotless block — executor MUST substitute the Context7-verified versions, not these placeholders. -->
Root `build.gradle.kts` after edit should look like (Kotlin DSL, matching existing style):

  plugins {
      base
      id("com.diffplug.spotless") version "<VERIFIED_SPOTLESS_VERSION>"
  }

  configure(listOf(project(":backend:core"), project(":backend:api"), project(":backend:worker"))) {
      apply(plugin = "com.diffplug.spotless")
      configure<com.diffplug.gradle.spotless.SpotlessExtension> {
          java {
              googleJavaFormat("<VERIFIED_GJF_VERSION>").aosp()
              formatAnnotations()
              removeUnusedImports()
          }
      }
  }

  tasks.wrapper {
      gradleVersion = "9.4.1"
      distributionType = Wrapper.DistributionType.BIN
  }

Notes:
- Use `configure(listOf(project(":backend:core"), ...))` (NOT a blanket `subprojects {}`) so only the three backend Java subprojects get Spotless; `apps/web` is not a subproject anyway but be explicit.
- Do NOT set `ratchetFrom` on this pass — full-repo reformat is intended. (`ratchetFrom("origin/main")` is a deliberately deferred follow-up, out of scope here.)
- If the executor finds a cleaner idiom (e.g. the `spotless { ... }` extension applied at root with per-project targets, or `allprojects`/`subprojects` filtered by `plugins.hasPlugin("java")`), it may use it — the requirement is: Spotless plugin available, AOSP google-java-format + formatAnnotations + removeUnusedImports applied to exactly the three backend Java subprojects, no ratchet, Kotlin DSL, builds clean.
</kotlin_dsl_shape>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Verify versions, wire Spotless + lint-staged + docs, delete scratch file (Commit A)</name>
  <files>build.gradle.kts, package.json, CONTRIBUTING.md, aosp-format-sample.md</files>
  <action>
  1. **Verify versions via Context7 FIRST** (project rule: check current docs before introducing version-sensitive deps). Use `mcp__context7__resolve-library-id` then `mcp__context7__query-docs` to confirm the current stable versions of (a) the Spotless Gradle plugin `com.diffplug.spotless` and (b) `google-java-format`. Pin to the latest stable of each. As of this writing Spotless is ~8.4.0 and google-java-format ~1.x — but CONFIRM, do not assume. If Context7 MCP is unavailable, fall back to the Gradle Plugin Portal page for `com.diffplug.spotless` and the google-java-format GitHub releases page; record which source was used in the SUMMARY.
  2. **Edit root `build.gradle.kts`**: add `id("com.diffplug.spotless") version "<VERIFIED>"` to the `plugins {}` block, and add a `configure(listOf(project(":backend:core"), project(":backend:api"), project(":backend:worker"))) { apply(plugin = "com.diffplug.spotless"); configure<com.diffplug.gradle.spotless.SpotlessExtension> { java { googleJavaFormat("<VERIFIED>").aosp(); formatAnnotations(); removeUnusedImports() } } }` block. Keep the existing `tasks.wrapper { ... }` block. Match Kotlin DSL style. NO `ratchetFrom`. Do NOT touch `.github/workflows/ci.yml` (its `./gradlew check` already gates `spotlessCheck`).
  3. **Verify the build script resolves**: run `./gradlew help` (or `./gradlew tasks --all | grep spotless` — expect `spotlessApply`, `spotlessCheck`, `spotlessJavaApply`, `spotlessJavaCheck` to appear for the backend subprojects). Confirm `check` lists `spotlessCheck` as a dependency. Then run `mcp__jetbrains__get_file_problems` on `build.gradle.kts` (per project tooling rule — JetBrains MCP for build files) and resolve any reported problems. If JetBrains MCP is unavailable in this agent's tool set (known upstream issue), note it in the SUMMARY and rely on `./gradlew help` succeeding as the equivalent gate.
  4. **Edit root `package.json`**: add a new entry to the existing `lint-staged` object: `"backend/**/*.java": "./gradlew spotlessApply -q"`. Leave every existing `apps/web/**` entry untouched. `.husky/pre-commit` already runs `pnpm exec lint-staged` and `"prepare": "husky"` auto-installs the hook on `pnpm install` — no hook edits needed. (Sanity-check `.husky/pre-commit` still invokes `lint-staged`; per inspection it does — if for some reason it does not, add `pnpm exec lint-staged` to it and mention this in the SUMMARY.) Optionally run `mcp__jetbrains__get_file_problems` on `package.json`.
  5. **Create `CONTRIBUTING.md` at repo root** (it does not exist; do NOT create a README for this) with a "## Code formatting" section. Content: backend Java is formatted with **google-java-format in AOSP style (4-space indent / 8-space continuation)**, enforced by the Spotless Gradle plugin; run `./gradlew spotlessApply` to auto-fix, `./gradlew spotlessCheck` (or `./gradlew check`) to verify; CI runs `./gradlew check` so unformatted Java fails the build; a pre-commit hook (Husky + lint-staged) runs `spotlessApply` on staged `backend/**/*.java`; IntelliJ IDEA users should install the **google-java-format** IDE plugin, enable it (Settings → google-java-format → Enable), and set its style to **AOSP** so Reformat Code (Ctrl+Alt+L) matches Spotless. Keep it short — a few sentences plus the commands.
  6. **Delete `aosp-format-sample.md`** at repo root. It is an untracked scratch file — just `rm aosp-format-sample.md` (use `git rm aosp-format-sample.md` only if `git status` shows it tracked).
  7. **Commit A** with message `build: add Spotless with google-java-format AOSP (4-space)` — stage ONLY: `build.gradle.kts`, `package.json`, `CONTRIBUTING.md`, and the deletion of `aosp-format-sample.md`. NO reformatted `.java` files in this commit (you have not run `spotlessApply` yet). Use `git add -A -- build.gradle.kts package.json CONTRIBUTING.md aosp-format-sample.md` then commit.
  </action>
  <verify>
    <automated>./gradlew tasks --all 2>&1 | grep -q spotlessApply && git log --oneline -1 | grep -qi "add Spotless" && grep -q "backend/\*\*/\*.java" package.json && test -f CONTRIBUTING.md && ! test -f aosp-format-sample.md && echo OK</automated>
  </verify>
  <done>Root `build.gradle.kts` has the Spotless plugin + AOSP google-java-format config (no ratchet) for the three backend subprojects; `./gradlew tasks` shows `spotlessApply`/`spotlessCheck`; `package.json` lint-staged has the `backend/**/*.java` glob with all `apps/web/**` entries intact; `CONTRIBUTING.md` exists with a Code formatting section; `aosp-format-sample.md` is deleted; Commit A exists with exactly those four path changes and NO `.java` files; `build.gradle.kts` has no outstanding JetBrains-reported problems (or unavailability noted).</done>
</task>

<task type="auto">
  <name>Task 2: One-time repo-wide reformat (Commit B)</name>
  <files>backend/**/*.java</files>
  <action>
  1. Run `./gradlew spotlessApply`. This reformats all backend Java to google-java-format AOSP style — expect a large diff (~150+ files flip from 2-space GOOGLE to 4-space AOSP; ~98 already AOSP-ish stay put; a few odd files normalize). This is expected and intended.
  2. Verify the reformat is complete and clean: `./gradlew spotlessCheck` MUST report no violations.
  3. **Run `./gradlew check`** — this runs `spotlessCheck` (must be clean) AND the existing ArchUnit + Spring Modulith verification tests (`ZeroMailApiApplicationModulesTest`, domain-boundary ArchTests, etc.). This confirms the reformat broke nothing semantically. It must pass green. (If `check` is slow, that's fine — let it run; do not skip it.)
  4. **Commit B** with message `style: reformat backend Java to google-java-format AOSP (4-space)` — stage ONLY the reformatted `.java` files: `git add -A -- 'backend/**/*.java'` (or `git add backend/` after confirming `git status` shows only `.java` changes under `backend/`). Nothing else may be in this commit. Verify with `git show --stat HEAD` that every changed path ends in `.java` and lives under `backend/`.
  5. **Capture Commit B's full 40-char SHA**: `git rev-parse HEAD` — record it; Task 3 needs it.
  </action>
  <verify>
    <automated>./gradlew spotlessCheck >/dev/null 2>&1 && git show --stat HEAD | grep -qE '\.java' && ! (git show --stat HEAD | grep -E '^\s' | grep -vqE '(\.java|files? changed|insertion|deletion)') && echo OK</automated>
  </verify>
  <done>`./gradlew spotlessApply` ran; `./gradlew spotlessCheck` is clean (zero violations); `./gradlew check` passes (Spotless + ArchUnit + Spring Modulith all green — semantically intact); Commit B exists containing ONLY reformatted `backend/**/*.java` files (no build files, no docs, no config); Commit B's full SHA is captured for Task 3.</done>
</task>

<task type="auto">
  <name>Task 3: Add .git-blame-ignore-revs (Commit C)</name>
  <files>.git-blame-ignore-revs</files>
  <action>
  1. Create `.git-blame-ignore-revs` at repo root. Format: 1–2 comment lines (lines starting with `#`) explaining what the file is, then the **full 40-char SHA of Commit B** (from Task 2 step 5) on its own line. Example content:

     # Revisions intentionally ignored by `git blame` (see `git config blame.ignoreRevsFile`).
     # Bulk mechanical reformats only — no semantic changes.
     #
     # style: reformat backend Java to google-java-format AOSP (4-space)
     <COMMIT_B_FULL_SHA>

  2. (Optional, document in SUMMARY but don't commit a config change unless trivial) Note that contributors can run `git config blame.ignoreRevsFile .git-blame-ignore-revs` locally; GitHub picks the file up automatically. IntelliJ also reads it. Do NOT add a repo-level git config commit — just the file.
  3. **Commit C** with message `chore: add .git-blame-ignore-revs for the AOSP reformat` — stage ONLY `.git-blame-ignore-revs`.
  4. Final verification: `git log --oneline -3` shows the three commits in order — C (HEAD) `chore: add .git-blame-ignore-revs ...`, B `style: reformat backend Java ...`, A `build: add Spotless ...`. And `grep -E '^[0-9a-f]{40}$' .git-blame-ignore-revs` returns exactly Commit B's SHA (`git rev-parse HEAD~1` should match the SHA line in the file).
  </action>
  <verify>
    <automated>test -f .git-blame-ignore-revs && BSHA=$(git rev-parse HEAD~1) && grep -qx "$BSHA" .git-blame-ignore-revs && git log --oneline -3 | head -1 | grep -qi "git-blame-ignore-revs" && git log --oneline -3 | sed -n '2p' | grep -qi "reformat backend Java" && git log --oneline -3 | sed -n '3p' | grep -qi "add Spotless" && echo OK</automated>
  </verify>
  <done>`.git-blame-ignore-revs` exists at repo root with explanatory comment lines + exactly Commit B's full 40-char SHA on its own line; Commit C exists containing only that file; `git log --oneline -3` shows C → B → A in the correct order; the SHA in the file equals `git rev-parse HEAD~1`.</done>
</task>

</tasks>

<verification>
- `./gradlew check` passes — runs `spotlessCheck` (clean post-reformat) AND existing ArchUnit + Spring Modulith verification (`ZeroMailApiApplicationModulesTest` etc.), confirming the reformat is semantically inert.
- `./gradlew spotlessCheck` reports zero violations.
- `git log --oneline -3` shows the three commits in order: C `chore: add .git-blame-ignore-revs ...` (HEAD), B `style: reformat backend Java ...`, A `build: add Spotless ...`.
- `.git-blame-ignore-revs` contains exactly Commit B's full 40-char SHA (`git rev-parse HEAD~1`).
- `package.json` `lint-staged` has the new `backend/**/*.java` → `./gradlew spotlessApply -q` entry; all pre-existing `apps/web/**` entries unchanged.
- `CONTRIBUTING.md` exists at repo root with a "Code formatting" section (Spotless / AOSP / `spotlessApply` / IntelliJ google-java-format plugin in AOSP mode).
- `aosp-format-sample.md` no longer exists.
- `.github/workflows/ci.yml` is unmodified.
- `mcp__jetbrains__get_file_problems` on `build.gradle.kts` reports no problems (or its unavailability is noted in the SUMMARY with `./gradlew help` succeeding as the substitute gate).
- Still on branch `gsd/phase-04-triage-convergence-hero` (no new branch created).
</verification>

<success_criteria>
google-java-format AOSP (4-space) is the canonical backend Java formatter, enforced via Spotless on `./gradlew check` (and therefore CI) and via a lint-staged pre-commit hook on `backend/**/*.java`; the entire backend Java tree has been reformatted in a single isolated commit whose SHA is recorded in `.git-blame-ignore-revs`; contributing docs explain the workflow and IntelliJ setup; the throwaway sample file is gone; nothing else changed; three commits land in the A → B → C order.
</success_criteria>

<output>
After completion, create `.planning/quick/260511-vok-adopt-google-java-format-aosp-4-space-fo/260511-vok-SUMMARY.md` documenting: verified Spotless + google-java-format versions (and the source — Context7 vs fallback), the three commit SHAs, approximate count of reformatted files, any deviations (e.g. JetBrains MCP unavailability, husky hook already-wired confirmation), and confirmation that `./gradlew check` is green.
</output>
