---
status: in_progress
created: 2026-05-22
---

# Quick Verification Fix: Stabilize Credit Concurrency Tests

Goal: keep the beta credit verification gates green on current `main` after the shared Postgres test container started capping Hikari pools.

Steps:
- Confirm the failing tests are concurrency specs starved by the shared default pool cap, not production behavior regressions.
- Override the Hikari pool size only on the concurrency-heavy tests that intentionally start many simultaneous reservations/model calls.
- Rerun the backend credit/usage/API/worker gate and frontend billing gate.
- Commit the verification-only fix separately, excluding unrelated `.idea` changes.
