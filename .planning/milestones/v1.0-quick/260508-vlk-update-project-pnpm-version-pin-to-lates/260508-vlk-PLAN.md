# Quick Task 260508-vlk: Update Project pnpm Version Pin to Latest Stable

## Goal

Align the project pnpm pin with the latest stable pnpm version so `pnpm i` works with the locally installed package manager.

## Tasks

1. Confirm the latest pnpm release and the current project version pins.
2. Update root package manager and engine configuration to pnpm 11.0.8.
3. Replace the legacy pnpm build-script policy with pnpm 11 `allowBuilds` decisions.
4. Update current stack references that prescribe the active pnpm version.
5. Verify `pnpm i` succeeds.

## Scope

Root pnpm configuration, active stack documentation, and quick-task tracking only. Historical phase plans and summaries are not rewritten.

## Verification

Run `pnpm i` from the repository root with pnpm 11.0.8.
