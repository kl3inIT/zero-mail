---
id: SEED-017
status: dormant
planted: 2026-05-26
planted_during: Spring AI Community repos research (post-Phase 08.1)
trigger_when: "when chat assistant tool catalog grows past ~20 tools, OR when LLM starts picking wrong tools due to catalog noise, OR when prompt token cost from tool definitions becomes noticeable in budget telemetry"
scope: small
---

# SEED-017: Spring AI Tool Search Tool — Dynamic Tool Discovery for Chat

## Why This Matters

Zero Mail v1.1 Phase 7 chat assistant ships **20 tools** (7 read + 8 write-reversible + 3 confirm-required + 3 confirmed-send). Phase 08.1 added 15 per-tool UI components for these. As v1.2+ adds settings tools, calendar tools, label management, contact lookup, scheduling — the catalog can easily double.

`spring-ai-community/spring-ai-tool-search-tool` (v2.1.0 on Maven Central, **Spring AI 2.x / Boot 4 ready**) implements Anthropic's [Tool Search Tool pattern](https://www.anthropic.com/engineering/advanced-tool-use). Instead of sending the full tool catalog upfront:

- Model receives only **1 tool** initially: `toolSearchTool`
- Model calls `toolSearchTool(query="weather")` to discover relevant tools
- Matching tool definitions expand into context on demand
- Model then invokes the discovered tools

**Claimed savings:** 34-64% token reduction; tool-selection accuracy also improves when models face fewer competing options.

## Why Not Adopt Now

At 20 tools the catalog isn't large enough to be the dominant token cost — system prompt + chat history + personalization slot already dominate. Adding the advisor introduces an extra round-trip (search → execute) which adds latency for simple requests where the right tool is obvious. The ROI inflection is roughly 20+ tools or when telemetry shows tool-definition tokens > 5K per request.

## When to Surface

**Trigger:** any of these:
- Chat tool catalog reaches **25+ tools**.
- Spend telemetry (when wired post-v1.2 Phase 8) shows prompt-token cost from tool definitions exceeding 5K tokens/request.
- Triage eval (SEED-014) flags tool-selection accuracy regressions when catalog grows.
- User complaints / observability shows LLM picking the wrong tool variant (e.g. confusing `sendEmail` vs `replyEmail` vs `forwardEmail`).

## Scope Estimate

**Small.** One plan:
- Add `tool-search-tool` + `tool-searcher-lucene` dependencies.
- Define a `ToolSearcher` bean (Lucene-only — see Privacy below).
- Wire `ToolSearchToolCallAdvisor` into the chat `ChatClient.Builder` in `core.chat` Modulith module.
- A/B harness comparing token cost + latency + tool-selection accuracy with/without.
- Eval guard: triage/draft tools MUST be discoverable for all reasonable user phrasings (covered by SEED-014 harness when both are in flight).

## Privacy / Architectural Constraints

**Use `tool-searcher-lucene` ONLY.** Avoid `tool-searcher-vectorstore`:
- Privacy constraint (CLAUDE.md ARCH-02) forbids embeddings of user mail. Tool descriptions themselves aren't user mail, so technically allowed — but adding a vector store is a slippery slope on a deferred constraint.
- Lucene is in-JVM, no external dependency. Matches v1 "no vector DB" lock.

**Regex searcher** (`tool-searcher-regex`) is a fallback for stable tool-naming conventions (e.g. `send_*`, `*_email`) but doesn't generalize across user phrasings.

## Library vs In-house (decide at trigger time)

Two paths:

- **Adopt library** — pull `tool-search-tool` + `tool-searcher-lucene`. Bean wiring is ~20 LOC. Library hooks into Spring AI's advisor chain correctly (the non-trivial part).
- **In-house** — reimplement the `ToolSearchToolCallAdvisor` pattern: register one synthetic `toolSearchTool` whose body Lucene-searches descriptions and injects matched tool definitions into the next request. ~150-200 LOC plus Lucene index management. Doable, but the Spring AI advisor lifecycle + recursive advisor handling is the part where library has real value (it's how `ChatClient` re-runs with newly discovered tools).

**Recommendation:** **adopt library** here. The Spring AI advisor-chain integration is the hard part, library does it correctly, our marginal value-add is zero. Lucene-only configuration sidesteps the Privacy concern below.

## Candidate Product Shape

- New module-internal package: `core.chat.toolsearch` with `LuceneToolSearcherConfig` + advisor wiring.
- Tool descriptions get a deliberate review pass — Lucene needs keyword-rich descriptions to surface the right tool.
- Observability: log `event=chat_tool_search_query tenantId={} query={} matchedCount={}` so we can debug "model can't find a tool" cases.

## References

- `spring-ai-community/spring-ai-tool-search-tool` (v2.1.0, Spring AI 2.x / Boot 4 ready)
- Anthropic blog: ["Smart Tool Selection: Achieving 34-64% Token Savings with Spring AI"](https://spring.io/blog/2025/12/11/spring-ai-tool-search-tools-tzolov)
- Anthropic [Advanced Tool Use](https://www.anthropic.com/engineering/advanced-tool-use)
- v1.1 Phase 7 tool registry (`ChatToolCallRegistry` in `core.chat`)
- Companion: [[SEED-014-triage-quality-eval-framework]] (regression guard for tool-selection accuracy)
