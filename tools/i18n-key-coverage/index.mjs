#!/usr/bin/env node
/**
 * tools/i18n-key-coverage/index.mjs
 *
 * Phase 1.1 Plan 07 — Standalone wrapper around `pnpm --filter web i18n:check`.
 *
 * Why this wrapper exists:
 *   - VALIDATION.md "Wave 0 Requirements" mandates a runnable
 *     `tools/i18n-key-coverage` entry point so CI can call the gate without
 *     remembering the workspace filter syntax.
 *   - CLAUDE.md "Hybrid monorepo" forbids running pnpm from Gradle's Node
 *     plugin (slow, brittle on Windows). CI runs `./gradlew check` and
 *     `node tools/i18n-key-coverage/index.mjs` as INDEPENDENT steps.
 *
 * Exit code is the contract: 0 = clean, non-zero = drift / missing key /
 * EN-prose regression. The actual checks live in
 * apps/web/scripts/check-i18n.ts.
 */
import { spawnSync } from "node:child_process";

// The workspace project name is `zeromail-web` (apps/web/package.json#name).
// pnpm's `--filter` accepts either the package name OR a path glob; we use the
// path glob so this wrapper survives package-name renames.
const result = spawnSync("pnpm", ["--filter", "./apps/web", "i18n:check"], {
    stdio: "inherit",
    // Windows compatibility: pnpm.cmd resolution requires shell mode.
    shell: true,
});

if (result.error) {
    console.error("[i18n-key-coverage] failed to spawn pnpm:", result.error.message);
    console.error(
        "[i18n-key-coverage] hint: install pnpm 10.33.2+ and ensure it is on PATH " +
        "(see CLAUDE.md tech-stack section).",
    );
    process.exit(1);
}

process.exit(result.status ?? 1);
