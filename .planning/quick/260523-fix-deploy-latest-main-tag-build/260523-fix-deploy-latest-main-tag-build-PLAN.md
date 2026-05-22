---
status: in_progress
created: 2026-05-23
---

# Fix Deploy Latest Main Tag Build Trigger

## Goal

Make `Deploy Latest Main` reliable after it creates a semver tag with `GITHUB_TOKEN`.

## Problem

GitHub does not trigger another workflow from a tag push created by the default `GITHUB_TOKEN`, so `Deploy Latest Main` waits for a `Build and Push Images` push run that never appears.

`Deploy Prod` also only verifies push-triggered image builds, so it would reject a manually dispatched tag build even when images were published correctly.

## Plan

1. Change `Deploy Latest Main` to dispatch `Build and Push Images` manually for the newly created tag.
2. Poll the manually dispatched build by `workflow_dispatch` + tag ref.
3. Update `Deploy Prod` verification to accept a successful tag image build from either `push` or `workflow_dispatch`.
4. Validate the YAML text and GitHub runs.
5. Commit and push the workflow fix.
