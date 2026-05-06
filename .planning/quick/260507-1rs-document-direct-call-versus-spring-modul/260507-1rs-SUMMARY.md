---
status: complete
quick_id: 260507-1rs
---

# Quick Task 260507-1rs Summary

Documented when to use direct service calls versus Spring Modulith events in Zero Mail.

## Files Modified

- `CONVENTIONS.md`
- `AGENTS.md`
- `CLAUDE.md`

## Key Guidance Added

- Direct calls stay preferred for commands needing immediate results or transaction safety.
- Spring Modulith events are for in-process after-commit side effects.
- Spring events do not cross `backend/api` and `backend/worker` when those apps run as separate processes.
- Cross-process handoff must use PostgreSQL-backed outbox / processing tables.
- Reusable domain events belong in `backend/core`, not `backend/api`.

## Verification

No automated tests were run. This task only updates Markdown documentation.
