---
id: SEED-013
status: dormant
planted: 2026-05-21
planted_during: Phase 8 admin console research detour into lordofthejars AI trust demos
trigger_when: "when seriously revisiting the 'no embeddings of user mail' constraint, or when product wants RAG/semantic-search over the user's inbox"
scope: large
---

# SEED-013: Cloaked + Salted Embeddings to Unlock Vector DB

## Why This Matters

Zero Mail's current constraint is hard: **no embeddings of user mail in v1** (see project CLAUDE.md Privacy section + Hard "do not use" list). The rationale is real — embedding inversion attacks (vec2text, Morris et al. 2023) let an attacker recover near-original text from leaked vectors, and Presidio PII redaction alone does **not** solve this.

But the constraint blocks an entire product surface: semantic mailbox search, "what did Sarah say last week about X", per-user RAG memory, cross-thread context. Competitors (Shortwave, Superhuman) ship this. If Zero Mail wants to compete on inbox-zero **and** AI memory, this becomes load-bearing.

Two techniques meaningfully change the threat model:
- **Cloaked AI / format-preserving encryption** (IronCore Labs, demonstrated in `lordofthejars/quarkus-cloaked-ai`): embeddings stored as ciphertext; similarity search works on encrypted vectors; key leak required to invert.
- **Salted/peppered embeddings** (`lordofthejars-ai/rag-salty-embeddings`): per-tenant salt added before embedding to break model-shared inversion paths.

Combined with Presidio NER redaction (entity-level) and a stricter scope (embed derived signals + sanitized bodies, never raw), the residual risk may become acceptable.

## When to Surface

**Trigger:** when product/user seriously asks for inbox semantic search, "ask my inbox" assistant, or cross-thread RAG memory — i.e. when the cost of the constraint exceeds the cost of revisiting it.

Also surface as soon as a v1.3+ milestone touches anything labeled "memory", "search", "context".

## Scope Estimate

**Large**. This is not a single phase. Minimum:
- Privacy phase: re-do DPIA, threat model, attacker model with cloaked + salted assumptions.
- ADR overriding the locked CLAUDE.md constraint (requires explicit user directive).
- Per-tenant key management (KMS, rotation, recovery, deletion-on-tenant-delete).
- Custom Presidio recognizers for VN locale (CMND/CCCD/STK/MST) — current recall is English-biased.
- Vector store selection + tenant-isolation hardening.
- Right-to-be-forgotten cascade across vector store.
- Eval suite for inversion-attack resistance.

## Candidate Product Shape

- Per-tenant encrypted vector store (Postgres pgvector + cloaked-ai layer, or external).
- Embed only derived signals (rule-match features, label predictions, summary lines) — never raw body.
- Sanitization pipeline: Presidio (with VN recognizers) → cloaked encryption → embed → store.
- Search path: query → cloaked encrypt → ANN → re-rank with policy filter (tenant + scope) → return references.

## Safety Rules

- Constraint is **locked**. This seed only triggers a re-decision phase, not an end-run around the lock.
- No raw body ever embedded, even with cloaking.
- No cross-tenant query path possible at storage level (not just filter-level).
- Inversion-resistance eval must run before any prod rollout.

## Open Questions

- Does cloaked-ai support Spring AI's `EmbeddingModel` abstraction or only LangChain4j? May need adapter.
- Performance cost of cloaked similarity search at our scale.
- Cost of per-tenant key rotation when an export/delete arrives.

## References

- `lordofthejars/quarkus-cloaked-ai`
- `lordofthejars-ai/rag-salty-embeddings`
- `lordofthejars-ai/ai-trust-demos/presidio-transformer`
- Memory: `reference-ai-research-repos`, `feedback-privacy-scope-email-content-only`
