---
quick_task: 260428-0hx
title: "Rename core view records to projections"
status: complete
completed_at: "2026-04-28T00:29:52+07:00"
code_commit: 3ff9025
---

# Quick Task 260428-0hx Summary

## Result

Renamed backend core read-side `*View` records to `*Projection` and moved repeated controller response mapping into API DTO static factories.

## Changes

- Renamed `CurrentUserView` to `CurrentUserProjection`.
- Renamed `GmailConnectionView` to `GmailConnectionProjection`.
- Updated `AccountService` and `GmailConnectionService` to return projection records.
- Added `MeResponse.from(CurrentUserProjection)` and `GmailConnectionStatusResponse.from(GmailConnectionProjection)`.
- Updated `MeController` and `TenantStatusController` to call DTO factories instead of private `toResponse(...)` helpers.

## Verification

- `rg -n "CurrentUserView|GmailConnectionView|toResponse\\(" backend\\api\\src\\main\\java backend\\core\\src\\main\\java backend\\api\\src\\test\\java backend\\core\\src\\test\\java -g "!**/build/**"` returned no matches.
- `git diff --check -- backend/api/src/main/java backend/core/src/main/java .planning/quick/260428-0hx-rename-core-view-records-to-projections-/260428-0hx-PLAN.md` passed.
- `./gradlew.bat :backend:api:check --warning-mode all` passed.

## Notes

- The DTO factories intentionally keep dependency direction as `backend/api -> backend/core`; core does not import API DTOs.
- An earlier `:backend:api:check` attempt failed because Redis health was DOWN for integration tests. After starting Redis with `docker compose up -d redis`, the check passed.
- `REVIEW.md` remains untracked and was intentionally not staged.

## Commit

Code commit: `3ff9025` (`refactor(quick-260428-0hx): rename core views to projections`).
