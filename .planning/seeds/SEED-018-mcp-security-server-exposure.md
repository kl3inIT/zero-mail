---
id: SEED-018
status: dormant
planted: 2026-05-26
planted_during: Spring AI Community repos research (post-Phase 08.1)
trigger_when: "when product decides to expose Zero Mail's Gmail tools as an MCP server (so Claude Desktop / Cursor / ChatGPT can use a user's inbox), OR when a strategic partner asks for programmatic API access via MCP"
scope: large
---

# SEED-018: MCP Server Exposure with `mcp-security`

## Why This Matters

Inbox Zero's roadmap hints at MCP server exposure — letting external AI assistants (Claude Desktop, Cursor, etc.) use a user's inbox through the standard Model Context Protocol with OAuth-gated tool access. This is a **plausible v2.x direction** for Zero Mail given:

- Zero Mail's tool catalog (read inbox + safe write actions through `OutboundSendGateway`) is already MCP-shaped.
- The product story "AI auto-triage that users trust" extends naturally to "your trusted email tools, usable by any AI agent you authorize."
- Strategic differentiation: most email SaaS doesn't expose programmatic AI access; doing it correctly is a moat.

`spring-ai-community/mcp-security` (v0.1.x, **Spring AI 2.x / Boot 4 ready**) provides:
- **MCP Server Security:** OAuth 2.0 resource server for MCP servers (`mcp-server-security-spring-boot` starter — drop-in with `spring.security.oauth2.resourceserver.jwt.issuer-uri` configured).
- **MCP Client Security:** OAuth 2.0 client flows for outbound MCP calls.
- **Spring Authorization Server module:** for issuing tokens for MCP workflows.
- Fine-grained access control for MCP tools and resources (per-tool scopes).

Stack match: 100% — same Spring Boot 4 / Spring AI 2.x stack as Zero Mail. The mcp-annotations work has graduated to `spring-projects/spring-ai` 2.x, so MCP server primitives are first-class in the Spring AI 2.x line.

## When to Surface

**Trigger:** any of these (NOT before, this is large):
- Product decides Zero Mail should expose an MCP server interface.
- Strategic partner / enterprise customer requires programmatic API access in MCP shape.
- v2.x post-launch when Zero Mail is stable and looking for moat-extending features.

## Why Not Now

- v1.2 is admin console + settings; MCP exposure is a product feature, not infrastructure.
- The existing `OutboundSendGateway` + tool catalog is the right primitive to wrap — but doing it right needs:
  - Per-tenant scope mapping (read inbox / archive / save_draft / send / forward each as separate OAuth scope).
  - Audit trail extension (`assistant_send_audit` needs to record "called via MCP by external agent X").
  - Rate-limit + budget gates per external agent identity, not just per tenant.
  - CASA / compliance review for "third-party AI agents acting on user mail."

Each of these is a meaningful phase. Don't start before product commits.

## Scope Estimate

**Large.** Likely 2-3 phases:
- **Phase 1:** MCP server scaffolding via `mcp-server-security-spring-boot`, scope model design, per-tenant authorization server setup.
- **Phase 2:** Tool exposure (start with read-only: `getMessage`, `searchInbox`, `getThread`), audit extension.
- **Phase 3:** Write-action exposure (gated by user-confirmed flow + extended audit + rate limits).

## Library vs In-house (decide at trigger time)

Reimplementing OAuth resource server filter chain for MCP transport (HTTP streamable + WebSocket variants) on top of Spring Security is **non-trivial**: per-tool scope enforcement, MCP-specific token introspection, and authorization-server flows for issuing tokens to external agents. The library handles this correctly.

**Recommendation:** **use the library when this becomes scope.** Rolling OAuth + MCP-protocol auth in-house is a footgun — Spring Security 7 + MCP transport spec compliance is exactly the kind of thing where a maintained library beats DIY.

## Architectural Constraints

- **Reuse `OutboundSendGateway`.** Every send call site MUST go through the gateway (ArchUnit-enforced today, count=1). MCP send tools = additional callers of the same gateway, not new send sites.
- **Privacy invariant survives.** Tool outputs going to external MCP clients are still bound by ARCH-02 (no email body in opaque persistence) — `ToolOutputSanitizer` must apply to MCP responses too.
- **OAuth scope per-tool.** Don't ship "all-or-nothing" MCP access. Granular scopes (e.g. `zeromail:inbox.read`, `zeromail:rules.write`, `zeromail:send`).
- **Audit row distinguishes "via chat" vs "via MCP."** Different surface = different audit metadata.

## References

- `spring-ai-community/mcp-security` (v0.1.x, Spring AI 2.x / Boot 4 ready)
- `mcp-server-security-spring-boot` sample: https://github.com/spring-ai-community/mcp-security/tree/main/samples/sample-mcp-server
- [MCP Authorization Spec 2025-06-18](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
- Inbox Zero MCP direction (reference repo at `../inbox-zero`)
- v1.1 Phase 7 ArchUnit single-send-call-site discipline (CLAUDE.md ARCH-01)
- Memory: `reference-ai-research-repos`
