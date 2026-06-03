---
status: complete
created: 2026-05-23
task: security-alert-triage-fix
---

# Quick Task: Security Alert Triage Fix

## Scope

- Triage open GitHub Security alerts for `kl3inIT/zero-mail`.
- Fix true source-level CodeQL alerts without dismissing or bypassing checks.
- Reduce true Trivy image alerts by upgrading patched OS packages in API/worker runtime images.
- Verify locally where practical, push to `origin/main`, and check CI/Security workflow.

## Findings From GitHub API

- Dependabot open alerts: none.
- Secret scanning open alerts: none.
- CodeQL source alerts: log injection, two array-index guards, two unused parameters.
- Trivy image alerts: API/worker runtime images include older Ubuntu Noble packages with fixed versions available.

## Plan

1. Fix CodeQL Java alerts directly in source.
2. Add runtime package upgrade in backend API/worker Dockerfiles.
3. Run focused backend tests/build checks and Dockerfile syntax/build validation if feasible.
4. Commit, push, and confirm GitHub workflows.
