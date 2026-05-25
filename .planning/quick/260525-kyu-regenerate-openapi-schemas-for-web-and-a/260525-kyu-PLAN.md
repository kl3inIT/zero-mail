---
phase: 260525-kyu
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - apps/web/openapi/openapi.json
  - apps/web/lib/api/schema.d.ts
  - apps/admin/openapi/admin-spec.json
  - apps/admin/src/lib/api/admin-schema.d.ts
  - apps/web/**/*.ts
  - apps/web/**/*.tsx
  - apps/admin/src/**/*.ts
  - apps/admin/src/**/*.tsx
autonomous: true
requirements:
  - QUICK-OPENAPI-SYNC
must_haves:
  truths:
    - "apps/web typecheck (tsc --noEmit) passes against backend's current OpenAPI spec"
    - "apps/admin typecheck (tsc --noEmit) passes against backend's current OpenAPI spec"
    - "Generated schema files reflect the backend on main (no hand-edits)"
  artifacts:
    - path: "apps/web/openapi/openapi.json"
      provides: "Regenerated public OpenAPI spec snapshot from backend"
    - path: "apps/web/lib/api/schema.d.ts"
      provides: "Regenerated TypeScript types for /v3/api-docs/public consumed by apps/web"
    - path: "apps/admin/openapi/admin-spec.json"
      provides: "Regenerated admin OpenAPI spec snapshot from backend"
    - path: "apps/admin/src/lib/api/admin-schema.d.ts"
      provides: "Regenerated TypeScript types for /v3/api-docs/admin consumed by apps/admin"
  key_links:
    - from: "backend/api/build.gradle.kts (openApi task)"
      to: "apps/web/openapi/openapi.json"
      via: "./gradlew :backend:api:generateOpenApiDocs"
      pattern: "outputDir.set\\(rootProject.file\\(\"apps/web/openapi\"\\)\\)"
    - from: "live backend bootRun on :8080"
      to: "apps/admin/openapi/admin-spec.json"
      via: "pnpm --filter @zeromail/admin run generate-api (fetches /v3/api-docs/admin)"
      pattern: "ADMIN_API_SPEC_URL.*admin"
    - from: "apps/web/lib/api/schema.d.ts"
      to: "apps/web feature API/hook/component callers"
      via: "import type { paths, components } from '@/lib/api/schema'"
      pattern: "from ['\"].+lib/api/schema['\"]"
    - from: "apps/admin/src/lib/api/admin-schema.d.ts"
      to: "apps/admin feature API/hook/component callers"
      via: "import type { paths, components } from '@/lib/api/admin-schema'"
      pattern: "from ['\"].+lib/api/admin-schema['\"]"
---

<objective>
Regenerate the OpenAPI-derived TypeScript schemas for both `apps/web` and `apps/admin` after the recent `main` merge, then make both frontend type-checks green by fixing any FE consumer drift (NOT by hand-editing generated files or adding normalizers to paper over backend DTOs).

Purpose: Backend Java DTOs / controllers have drifted from what the FE schema files describe. Per CLAUDE.md MANDATORY rule, generated files (`schema.d.ts`, `admin-schema.d.ts`, `*-spec.json`) are never hand-edited — they must be regenerated from the live backend (or hermetic spec emit task), and FE consumers must be updated to match the new types.

Output:
- Refreshed `apps/web/openapi/openapi.json` + `apps/web/lib/api/schema.d.ts`.
- Refreshed `apps/admin/openapi/admin-spec.json` + `apps/admin/src/lib/api/admin-schema.d.ts`.
- `pnpm --filter web run typecheck` and `pnpm --filter @zeromail/admin run typecheck` both exit 0.
- Optional `apps/web/QUICK-NOTES.md` only if a type error reveals a likely backend DTO bug worth raising in a follow-up phase.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@apps/web/AGENTS.md
@apps/admin/AGENTS.md
@apps/web/scripts/generate-api.ts
@apps/admin/scripts/generate-api.ts
@apps/web/package.json
@apps/admin/package.json
@backend/api/build.gradle.kts

<interfaces>
<!-- Codegen contract — extracted from the two scripts and backend build.gradle.kts. -->
<!-- Executor must use these exact entry points; do NOT improvise alternative paths. -->

WEB codegen (apps/web/scripts/generate-api.ts):
- Script name:        `pnpm --filter web run generate:api`  (= `tsx scripts/generate-api.ts`)
- Spec resolution order:
    1. If env `API_SPEC_URL` set    -> fetch that URL, write to `apps/web/openapi/spec.json`, use that.
    2. Else read `API_SPEC_PATH`    (defaults to `openapi/openapi.json` RELATIVE to apps/web).
- Output file:        `apps/web/lib/api/schema.d.ts`
- Cached spec file:   `apps/web/openapi/openapi.json` (the Gradle plugin writes here too)

ADMIN codegen (apps/admin/scripts/generate-api.ts):
- Script name:        `pnpm --filter @zeromail/admin run generate-api`  (NOTE: dash, NOT colon)
- Spec resolution order:
    1. If env `ADMIN_API_SPEC_PATH` set -> read that local file.
    2. Else fetch `ADMIN_API_SPEC_URL` (defaults to `http://localhost:8080/v3/api-docs/admin`).
- Output file:        `apps/admin/src/lib/api/admin-schema.d.ts`
- Cached spec file:   `apps/admin/openapi/admin-spec.json`

BACKEND Gradle hermetic spec task (backend/api/build.gradle.kts, `openApi { ... }`):
- Task name:          `:backend:api:generateOpenApiDocs` (from springdoc-openapi-gradle-plugin 1.9.0)
- Behavior:           Boots a hermetic backend on port 59280 with dummy creds, hits
                      `http://localhost:59280/v3/api-docs/public`, writes the result to
                      `apps/web/openapi/openapi.json`, then shuts the forked boot down.
- Important limitation: This task emits the PUBLIC spec only (audience = apps/web).
                        It does NOT emit the admin spec. Admin schema regen requires
                        a separately running backend on :8080 OR a manual fetch of
                        `/v3/api-docs/admin` from a hermetic boot, then pointing
                        `ADMIN_API_SPEC_PATH` at the saved file.

Typecheck commands:
- Web:    `pnpm --filter web run typecheck`                  (tsc --noEmit)
- Admin:  `pnpm --filter @zeromail/admin run typecheck`      (tsc --noEmit)
</interfaces>

<constraints_from_claudemd>
- MANDATORY: NEVER hand-edit `apps/web/lib/api/schema.d.ts`, `apps/admin/src/lib/api/admin-schema.d.ts`,
  `apps/web/openapi/openapi.json`, or `apps/admin/openapi/admin-spec.json`.
- When fixing FE type errors:
  * Update the FE consumer (feature API file, hook, component) to align with the new generated types.
  * Do NOT add normalizers in feature API files just to patch missing required / nullable fields —
    that signals a backend DTO bug; flag it in SUMMARY instead.
  * Do NOT hand-write mirror DTOs in feature API files; derive types from generated
    `components` / `paths` in `schema.d.ts` / `admin-schema.d.ts`.
- If a type error reveals a backend DTO bug (e.g. response field that should be `@Schema(requiredProperties = {...})`
  but is currently optional in the schema), record it in SUMMARY.md "Backend DTO follow-ups" section.
  Do NOT modify the backend in this quick task.
- No docs commits from executor (per GSD conventions). SUMMARY.md is allowed.
- Each task = atomic commit.
</constraints_from_claudemd>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Regenerate web schema via hermetic Gradle spec task</name>
  <files>
    apps/web/openapi/openapi.json
    apps/web/lib/api/schema.d.ts
  </files>
  <action>
    Use the hermetic Gradle path because it is the canonical, repo-aware way to emit the public
    spec (no need to manage a long-running bootRun, and it already writes to the exact location
    the web codegen reads by default).

    Steps:
    1. From repo root, run the Gradle plugin task that boots a hermetic backend on :59280, hits
       `/v3/api-docs/public`, writes to `apps/web/openapi/openapi.json`, and shuts down:
       ```
       ./gradlew :backend:api:generateOpenApiDocs
       ```
       On Windows PowerShell use the same command (the wrapper handles `.bat` selection).
       The task may take ~30-120 seconds because it boots a full Spring context. Wait for
       `BUILD SUCCESSFUL`. If the task fails:
       - Read the failure output. If it complains about a port collision on 59280, check for
         a stuck `java` process holding the port and kill it, then retry.
       - If it complains about DB / Redis (the customBootRun args expect a real local Postgres
         on 5555 and Redis on 6379), start docker-compose (`docker compose up -d`) and retry.
       - Do NOT hand-edit `apps/web/openapi/openapi.json` to work around failures.

    2. Confirm the spec was rewritten (timestamp moved forward):
       ```
       git diff --stat apps/web/openapi/openapi.json
       ```
       Expect a non-zero diff (since main was merged and the spec drifted).

    3. Run the web TypeScript codegen against the freshly written cached spec:
       ```
       pnpm --filter web run generate:api
       ```
       The script's default `API_SPEC_PATH=openapi/openapi.json` reads the file Gradle just wrote.
       Do NOT pass `API_SPEC_URL` (no need — we already have a fresh local spec).

    4. Confirm the generated `apps/web/lib/api/schema.d.ts` changed:
       ```
       git diff --stat apps/web/lib/api/schema.d.ts
       ```

    5. Run `pnpm --filter web run typecheck`. CAPTURE the full error list — do not start fixing
       yet; first scan the errors to plan changes (avoid file-by-file blind edits that compound).
       Then fix FE consumer code per the rules below.

    Fixing FE errors — RULES (per CLAUDE.md, apps/web/AGENTS.md):
    - For each error in `apps/web/**/*.ts(x)`:
      * If the field was renamed / moved / removed in the new schema → update the consumer
        (feature API file in `features/<feature>/api/`, hook, component) to use the new field path.
      * If a generated field is now `undefined`-able and the consumer assumed required → add the
        appropriate guard (`if (!value) return …`) instead of `!` non-null assertion.
      * If a closed enum value was renamed → search for the old literal across `apps/web/**` and
        replace with the new generated enum literal. Use generated `components['schemas']['<Enum>']`
        rather than hardcoding the string union.
      * If the generated type now exposes a NEW required field that has no clean UI handling
        → render a minimal but truthful representation (e.g. show the value); do NOT silently
        drop it; do NOT add a normalizer to strip it.
      * If a type error looks like a backend DTO bug (missing `@Schema(requiredProperties)`,
        wrong nullability, missing `@JsonInclude` for a variant response, etc.) → leave the
        consumer using `?.` / fallback and record the suspected backend fix in SUMMARY.md's
        "Backend DTO follow-ups" section. Do NOT touch backend in this task.
    - FORBIDDEN: hand-editing `schema.d.ts`, adding a normalizer just to patch missing
      `required` / nullability, hand-writing a mirror DTO in any `features/<feature>/api/` file.

    6. Re-run `pnpm --filter web run typecheck` until it exits 0. Iterate as needed.

    7. Stage and commit the generated files together with any FE consumer fixes in a SINGLE
       atomic commit so reviewers see the schema change and the call-site delta together:
       ```
       git add apps/web/openapi/openapi.json apps/web/lib/api/schema.d.ts <touched FE files>
       git commit -m "chore(web): regenerate OpenAPI schema and align FE consumers"
       ```
  </action>
  <verify>
    <automated>cd apps/web; pnpm run typecheck</automated>
    Manual:
    - `git diff --stat HEAD~1 apps/web/openapi/openapi.json apps/web/lib/api/schema.d.ts` shows both regenerated.
    - No edits to `apps/web/lib/api/schema.d.ts` outside the one regen commit (i.e. the diff against HEAD~1
      is purely codegen output — no human lines like "// TODO" inside `schema.d.ts`).
    - No new files under `apps/web/features/**/api/*.ts` that hand-write DTOs.
  </verify>
  <done>
    `pnpm --filter web run typecheck` exits 0. Both `schema.d.ts` and `openapi.json` reflect the
    current backend `/v3/api-docs/public` output. All FE type errors caused by the regen are
    resolved at the call site (no normalizers, no hand edits to generated files). Any suspected
    backend DTO bugs are listed in SUMMARY.md "Backend DTO follow-ups".
  </done>
</task>

<task type="auto">
  <name>Task 2: Regenerate admin schema via live bootRun + align FE consumers</name>
  <files>
    apps/admin/openapi/admin-spec.json
    apps/admin/src/lib/api/admin-schema.d.ts
  </files>
  <action>
    The springdoc Gradle plugin only emits `/v3/api-docs/public` (audience = web). For the admin
    audience (`/v3/api-docs/admin`) we need a backend listening on port 8080. Use the live
    bootRun path because the admin codegen script defaults to `http://localhost:8080/v3/api-docs/admin`
    — no env-var tweaking required.

    Steps:
    1. Start backend in background on :8080 (foreground in another terminal also works; this
       plan assumes a single agent session, so use background):
       ```
       ./gradlew :backend:api:bootRun
       ```
       Use the Bash tool's `run_in_background: true`. Capture the background id so you can
       shut it down at the end.

       Wait for the server to be ready. Poll every 2-3 seconds until either:
       - `curl -fsS http://localhost:8080/v3/api-docs/admin > /dev/null` exits 0, OR
       - The background log emits a "Started Application" / "Tomcat started on port 8080"
         line. Whichever comes first.
       Give it up to ~120 seconds (the boot is heavy: Liquibase + Hibernate + Modulith verify).

       If bootRun fails to start:
       - Check the background output for the actual error.
       - Most common: a stale Java process is already on :8080 from a previous session. Kill it
         (e.g. `netstat -ano | findstr :8080` on Windows, then `taskkill /F /PID <pid>`).
       - Or: Postgres/Redis not running — start docker-compose and retry.
       Do NOT fall back to hand-editing `admin-schema.d.ts` if bootRun cannot start.

    2. Run admin codegen against the live admin doc URL:
       ```
       pnpm --filter @zeromail/admin run generate-api
       ```
       The script's default `ADMIN_API_SPEC_URL=http://localhost:8080/v3/api-docs/admin` will
       hit the running backend, cache the JSON to `apps/admin/openapi/admin-spec.json`, then
       emit `apps/admin/src/lib/api/admin-schema.d.ts`.

    3. Confirm both files changed:
       ```
       git diff --stat apps/admin/openapi/admin-spec.json apps/admin/src/lib/api/admin-schema.d.ts
       ```

    4. Run `pnpm --filter @zeromail/admin run typecheck` and capture the full error list.
       Fix FE consumer code under `apps/admin/src/**` using the SAME RULES as Task 1:
       - Update consumers (route components, feature API files, hooks) to match new
         generated types.
       - Use `components['schemas']['<X>']` from the generated module rather than hand-written
         literal unions.
       - For closed enums whose values changed, search `apps/admin/src/**` for the old literal
         and replace with the generated literal.
       - Do NOT add normalizers in admin feature API files just to patch missing required
         / nullable fields — record suspected backend DTO bug in SUMMARY.md.
       - Do NOT hand-edit `admin-schema.d.ts` or `admin-spec.json`.
       - Admin uses TanStack Router, so route files under `apps/admin/src/routes/**` may need
         updates if a `loader` / `beforeLoad` consumes a renamed field. `routeTree.gen.ts`
         is generated by the router plugin — do not hand-edit it; it regenerates on next dev.

    5. Re-run `pnpm --filter @zeromail/admin run typecheck` until it exits 0.

    6. Shut down the background backend:
       - If you started it via `run_in_background: true`, kill the background process now.
       - Verify port 8080 is free (`curl -fsS http://localhost:8080` should fail with
         connection refused, not return a response).

    7. Stage and commit in a single atomic commit:
       ```
       git add apps/admin/openapi/admin-spec.json apps/admin/src/lib/api/admin-schema.d.ts <touched FE files>
       git commit -m "chore(admin): regenerate OpenAPI schema and align FE consumers"
       ```
  </action>
  <verify>
    <automated>cd apps/admin; pnpm run typecheck</automated>
    Manual:
    - `git diff --stat HEAD~1 apps/admin/openapi/admin-spec.json apps/admin/src/lib/api/admin-schema.d.ts`
      shows both regenerated.
    - `admin-schema.d.ts` diff is purely codegen output (no human comments / TODOs).
    - No new normalizers under `apps/admin/src/features/**/api/*.ts`.
    - Background bootRun process is no longer running (port 8080 free).
  </verify>
  <done>
    `pnpm --filter @zeromail/admin run typecheck` exits 0. Both `admin-spec.json` and
    `admin-schema.d.ts` reflect the current backend `/v3/api-docs/admin` output. All admin
    FE type errors are resolved at the call site (no normalizers, no hand edits to generated
    files). Background backend is shut down. Suspected backend DTO bugs (if any) are appended
    to SUMMARY.md "Backend DTO follow-ups".
  </done>
</task>

</tasks>

<verification>
Phase-level checks (run after both tasks):

1. Generated artifacts are byte-fresh from codegen:
   ```
   git log --oneline -2
   ```
   Expect 2 atomic commits (web regen, admin regen) — schema.d.ts / admin-schema.d.ts diffs are
   contained within those commits, not split across multiple. Re-running the codegen scripts on
   a clean tree should produce a no-op diff.

2. Both type-checks pass:
   ```
   pnpm --filter web run typecheck
   pnpm --filter @zeromail/admin run typecheck
   ```
   Both exit 0.

3. No hand edits to generated files:
   ```
   git log -p HEAD~2..HEAD -- apps/web/lib/api/schema.d.ts apps/admin/src/lib/api/admin-schema.d.ts \
     apps/web/openapi/openapi.json apps/admin/openapi/admin-spec.json
   ```
   Should look like full file regeneration only (no surgical mid-file diffs that would indicate
   a hand edit).

4. No new normalizer files / hand-written DTO mirrors:
   ```
   git diff --stat HEAD~2..HEAD -- apps/web/features apps/admin/src/features
   ```
   Any new files should be legitimate consumer fixes, not "<feature>-normalizer.ts" or
   "<feature>-dto.ts" mirrors.

5. SUMMARY.md (if backend DTO follow-ups exist) lists each suspected backend bug with: which
   endpoint, which field, what the FE had to work around, and the proposed backend fix (e.g.
   "add `@Schema(requiredProperties = {\"x\"})` to ResponseDto"). Do NOT include actual schema
   bytes, just the contract delta.
</verification>

<success_criteria>
- [ ] `pnpm --filter web run typecheck` exits 0.
- [ ] `pnpm --filter @zeromail/admin run typecheck` exits 0.
- [ ] `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts` were regenerated this session.
- [ ] `apps/admin/openapi/admin-spec.json` and `apps/admin/src/lib/api/admin-schema.d.ts` were regenerated this session.
- [ ] Two atomic commits exist (one per app), each pairing the regen with the matching FE consumer fixes.
- [ ] No edits to `schema.d.ts`, `admin-schema.d.ts`, `openapi.json`, or `admin-spec.json` outside the codegen output of the commit.
- [ ] No new normalizers or hand-written DTO mirrors were introduced under `apps/web/features/**` or `apps/admin/src/features/**`.
- [ ] Any suspected backend DTO bugs (missing `@Schema(requiredProperties)`, wrong nullability, etc.) discovered during typecheck fixing are captured in SUMMARY.md "Backend DTO follow-ups" — backend code itself was NOT modified.
- [ ] Background `bootRun` (Task 2) is shut down; port 8080 free.
</success_criteria>

<output>
After completion, create `.planning/quick/260525-kyu-regenerate-openapi-schemas-for-web-and-a/260525-kyu-SUMMARY.md` with:
- What changed (one paragraph per app, plus file count from `git diff --stat HEAD~2..HEAD`).
- "Backend DTO follow-ups" section (or "None observed"): each suspected backend fix as
  endpoint + field + proposed `@Schema` / nullability change.
- Replay commands (for future quick tasks): hermetic web regen + live admin regen.
- Anything notable that future quick tasks should know (e.g. "bootRun took 90s on this machine"
  or "had to kill stale Java PID on :8080").
</output>
