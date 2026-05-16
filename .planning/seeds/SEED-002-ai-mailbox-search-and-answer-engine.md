---
id: SEED-002
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning post-CASA AI assistant, mailbox search, or semantic retrieval work"
scope: large
---

# SEED-002: AI Mailbox Search and Answer Engine

## Why This Matters

Shortwave's most strategically interesting feature is not simple keyword search. It lets users ask plain-language questions, runs AI-powered `about:"..."` search across email history, returns relevant threads, and then answers using email/calendar context. This turns the inbox into a personal knowledge base.

Inbox Zero appears focused on rule automation, reply tracking, cleanup, and assistant workflows; it does not position a full Shortwave-style AI search and answer engine as a core feature. This is a possible differentiation path for Zero Mail, but it conflicts with the current v1 privacy posture if implemented as full-history semantic search.

## When to Surface

**Trigger:** when planning post-CASA AI assistant, mailbox search, or semantic retrieval work.

Surface this seed if a milestone includes:

- "chat with my mailbox"
- semantic search
- full Gmail history import
- email memory
- vector search
- mailbox knowledge base
- AI answer citations back to threads

## Scope Estimate

**Large**. This likely needs its own milestone if we choose full-history indexing.

## Candidate Product Shape

- Search syntax bridge: support Gmail-style operators plus `about:"topic"` natural-language search.
- AI answer mode: answer a user question with cited source threads.
- Thread result chips: every answer exposes the underlying thread list, not just generated text.
- On-demand safe mode: use Gmail API `q=` and fetch only selected/relevant messages, then discard content.
- Full-index mode: optional paid feature that imports history and builds semantic index.

## Privacy and Compliance Decision

Two implementation tiers should be considered:

- **Tier A: privacy-preserving search** — Gmail API search + on-demand fetch + no persisted raw body/embedding. Lower risk, weaker relevance.
- **Tier B: Shortwave-style search** — sync/index message content and likely embeddings. Higher value, but requires updated privacy policy, CASA evidence, retention/deletion controls, encryption, employee-access controls, and explicit user opt-in.

Do not ship Tier B while still claiming "no long-term raw email body storage" or "no email embeddings".

## Breadcrumbs

- Shortwave AI assistant docs: https://www.shortwave.com/docs/guides/ai-assistant/
- Shortwave search docs: https://www.shortwave.com/docs/references/search/
- Shortwave homepage: https://www.shortwave.com/
- `.planning/phases/04-triage-convergence-hero/04-RESEARCH.md` — current Zero Mail privacy rule forbids long-term body, prompt, completion, or embedding storage.
- `docs/casa/` — must be revised if full mailbox indexing is introduced.

## Notes

This is one of the clearest "Shortwave has it, Inbox Zero does not clearly have it" opportunities. It should not block Phase 6 submission.
