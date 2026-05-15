---
status: complete
quick_id: 260507-ci
---

# Quick Task 260507-ci Summary

Added a general GitHub Actions CI workflow for backend and frontend checks.

## Sources Checked

- GitHub Actions docs via Context7.
- `actions/setup-java` docs via Context7.
- `actions/setup-node` docs via Context7.
- `pnpm/action-setup` docs via Context7.
- Gradle Actions repository/docs via web lookup.
- Node.js official release page checked: CI uses `actions/setup-node@v6` with `node-version: lts/*` + `check-latest: true`; `.nvmrc` major-20 pin removed so `package.json`'s `>=20.9.0` lower bound remains the project constraint.

## Files Modified

- `.github/workflows/ci.yml`
- `.github/workflows/i18n-check.yml`
- `.nvmrc`
- `.planning/quick/260507-add-github-ci-workflow/260507-ci-PLAN.md`
- `.planning/quick/260507-add-github-ci-workflow/260507-ci-SUMMARY.md`

## Verification

- `./gradlew.bat --no-daemon check` passed locally.
- `pnpm install --frozen-lockfile` passed locally.
- `pnpm --filter web run lint` passed locally.
- `pnpm --filter web run typecheck` passed locally.
- `pnpm --filter web run build` passed locally.
- Workflow YAML parsed successfully with `js-yaml`.

## Note

`pnpm --filter web run test` reported all 129 tests passing, then exited 1 because Vitest failed to terminate/start a fork worker on Windows with local Node `v24.14.0`. The CI job runs on Ubuntu and uses the latest Node release, so the test step remains enabled as the intended gate.
