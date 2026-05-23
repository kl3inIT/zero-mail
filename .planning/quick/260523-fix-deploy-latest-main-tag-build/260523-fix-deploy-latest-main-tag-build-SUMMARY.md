---
status: complete
completed: 2026-05-23
commit: 80f1b2c6
---

# Summary

Fixed the tag deployment automation so `Deploy Latest Main` no longer waits for a tag-push build that GitHub will not create from `GITHUB_TOKEN`.

## Changes

- `Deploy Latest Main` now dispatches `Build and Push Images` manually for the newly created semver tag.
- The build wait loop now polls the `workflow_dispatch` run on the tag ref.
- `Deploy Prod` now accepts a successful tag image build from either `push` or `workflow_dispatch`.

## Verification

- `git diff --check` passed.
- Both edited workflow YAML files parsed successfully with PyYAML.
- Existing `v1.4.4` image build was found with the new poll query as `completed/success`.
- `Build and Push Images` for `v1.4.4` completed successfully.
- `Deploy Prod` for `v1.4.4` passed verify gates and is waiting on the `prod` environment approval.
