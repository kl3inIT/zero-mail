# GitHub Copilot Instructions

## Review scope

When reviewing pull requests, **do not review or comment on files under**:

- `.planning/**` — GSD planning artifacts (phase plans, research, specs, verification reports). These are AI-generated workflow documents, not production code. They change frequently and are not subject to code review.
- `**/CLAUDE.md` — AI agent instructions.
- `**/MEMORY.md` and `**/memory/**` — persistent agent memory files.

Skip these paths entirely: no inline comments, no summary mentions, no suggestions. Focus reviews on source code under `backend/`, `apps/web/`, build configuration, and `.github/workflows/`.

## Project context

Zero Mail is a multi-tenant Gmail SaaS using Java 25 / Spring Boot 4 (backend) and Next.js 16 / React 19 (frontend). See `CLAUDE.md` for full stack and conventions.
