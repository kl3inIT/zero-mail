---
quick_task: 260427-8xs
title: "Project-wide problem sweep for backend and frontend"
status: complete
completed_at: "2026-04-27T06:52:26+07:00"
code_commit: 8944e0c
---

# Quick Task 260427-8xs Summary

## Result

Completed a JetBrains `get_file_problems` sweep across the project source/config set listed in `source-files.txt` and fixed the actionable backend/frontend findings.

## Changes

- Narrowed `ZeroMailCoreProperties` from duplicate `zeromail` binding to `zeromail.crypto` while preserving the existing config key `zeromail.crypto.refresh-token-key-base64`.
- Cleared broken Javadocs and OpenAPI raw-type warnings.
- Fixed ignored promises in `useCompleteOnboarding` cache invalidation.
- Cleaned unused imports, unused parameters, unnecessary `throws Exception`, nullness issues, preview/API warnings, and IDE-only SQL datasource warnings in tests.
- Kept intentional/noisy project-wide findings out of code changes unless they had a narrow suppression point.

## Verification

- JetBrains `build_project`: success, no problems.
- JetBrains `get_file_problems`: modified files rechecked clean after fixes.
- `./gradlew check`: passed.
- `pnpm --dir apps/web exec tsc --noEmit`: passed.
- `pnpm --dir apps/web run lint`: passed.
- `pnpm --dir apps/web run i18n:check`: passed.
- `pnpm --dir apps/web exec vitest run`: passed, 19 files / 122 tests.

## Notes

- `package.json` engine metadata landed separately in commit `1edcb5a`; it is outside this sweep's code commit.
- `REVIEW.md` remains untracked and was intentionally not staged.
