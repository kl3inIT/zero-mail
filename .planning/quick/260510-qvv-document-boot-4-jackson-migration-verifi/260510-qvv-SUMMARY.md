---
quick_id: 260510-qvv
slug: document-boot-4-jackson-migration-verifi
status: complete
completed: 2026-05-10
---

# Quick Task 260510-qvv Summary

Updated project guidance for Spring Boot 4 / Jackson 3 migrations after PR #22 exposed the invalid `tools.jackson.annotation.*` assumption.

## Changes

- Added explicit migration guidance to `CLAUDE.md` and `AGENTS.md`.
- Updated `.planning/research/STACK.md`, the source for the stack TL;DR, with the same rule.
- Captured the verified exception: Jackson core/databind packages moved to `tools.jackson.*`, but `jackson-annotations` remains under `com.fasterxml.jackson.annotation.*`.

## Verification

- Queried Context7 Jackson docs and Spring Boot 4 docs.
- Ran `.\gradlew.bat --no-daemon :backend:core:dependencyInsight --dependency jackson-annotations --configuration compileClasspath`.
- Ran `rg -n "tools\.jackson\.annotation|jackson-annotations remains|Unverified Spring Boot 4" AGENTS.md CLAUDE.md .planning\research\STACK.md backend/core/src/main/java`.
- Ran `git diff --check`.
