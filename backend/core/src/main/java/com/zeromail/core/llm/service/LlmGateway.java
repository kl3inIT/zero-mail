package com.zeromail.core.llm.service;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.application.RuleCompileGatewayResult;
import com.zeromail.core.llm.application.ToolCallResult;

/**
 * Single chokepoint for all LLM traffic in Zero Mail. Phase 3 (Rules Engine) and Phase 4 (Triage)
 * import this interface verbatim.
 *
 * <p><b>Cross-phase contract.</b> {@link #chat(CallSite, String)} sanitizes input through the Plan
 * 02 pipeline, prepends the fixed system prompt declaring email content as data, uses the
 * gateway-owned fixed tool allow-list {label, archive, save_draft}, enforces tool-call allow-list
 * validation in Plan 04, routes through BYOK or the platform path in Plan 05, and wraps platform
 * calls with Phase 2B credit reservation in Plan 06.
 *
 * <p><b>Privacy invariant.</b> Implementations MUST NOT log, persist, or expose raw email content,
 * prompt text, or completion text. Observation spans carry metadata only: provider, model, token
 * counts, latency, stop reason, and truncation state.
 */
public interface LlmGateway {

  // Gateway owns tools; callers cannot pass arbitrary tool definitions.
  ToolCallResult chat(CallSite callSite, String rawHtml);

  /**
   * Rules-engine compile path. The gateway owns the rule-compile tool profile; callers pass only a
   * sanitized compiler payload and never pass arbitrary tool definitions.
   */
  RuleCompileGatewayResult compileRule(CallSite callSite, String compilerPayload);

  /**
   * Drift detection path. It uses the same sanitization pipeline, system prompt, and allow-list as
   * {@link #chat(CallSite, String)}, but is pinned to the drift model and bypasses future user
   * credit reservation.
   */
  ToolCallResult driftCheck(String rawEmailFixture);
}
