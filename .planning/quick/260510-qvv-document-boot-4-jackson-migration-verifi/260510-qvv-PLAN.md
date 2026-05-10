---
quick_id: 260510-qvv
slug: document-boot-4-jackson-migration-verifi
status: complete
created: 2026-05-10
---

# Quick Task 260510-qvv: Document Boot 4/Jackson Migration Verification Guidance

## Goal

Document the Spring Boot 4 / Jackson 3 migration pitfall that caused the PR #22 backend CI failure: Jackson 3 moves most packages to `tools.jackson.*`, but `jackson-annotations` remains under `com.fasterxml.jackson.annotation.*`.

## Tasks

1. Verify current Spring Boot and Jackson documentation with Context7.
2. Confirm the local Gradle dependency graph for Jackson annotations.
3. Update `CLAUDE.md`, `AGENTS.md`, and the stack research source so future agents verify major library changes before editing imports or configuration.
4. Run lightweight text verification.
