---
status: completed
quick_id: 260517-metadata-only-analytics
created: 2026-05-17
---

# Quick Task 260517-metadata-only-analytics: Metadata-Only Analytics

## Goal

Expand the Analytics page with richer controls and visualizations using only metadata already stored in Zero Mail. Do not add Gmail scopes, do not store message bodies, subjects, snippets, prompts, completions, or embeddings.

## Tasks

1. Extend backend analytics summary projections.
   - Add daily inbox load, action mix, domain load, category load, reply bucket counts, and no-rule-match counts.
   - Source only from `mail_message_observed`, `triage_audit`, and `thread_reply_status`.
   - Preserve old constructor compatibility for digest and controller tests.

2. Extend API DTOs and frontend API types.
   - Keep `/api/analytics/summary`.
   - Add optional metadata-only arrays/objects so old clients stay tolerant.

3. Improve Analytics UI with metadata-only panels.
   - Add load trend, action/category mix, reply buckets, and automation opportunities.
   - Keep existing window control and current panels.

4. Update tests and e2e mocks.
   - Cover zero states and seeded metadata.
   - Verify typecheck/lint and targeted backend/frontend tests where feasible.
