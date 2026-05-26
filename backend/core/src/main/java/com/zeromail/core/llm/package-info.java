/**
 * LLM Gateway domain (Phase 02C). Single chokepoint for all LLM traffic in Zero Mail.
 *
 * <p><b>Cross-phase contract.</b> Phase 3 (Rules Engine) and Phase 4 (Triage) import {@link
 * com.zeromail.core.llm.usecases.LlmGateway} verbatim and call {@code chat(callSite, rawHtml)} for
 * every LLM call.
 *
 * <p><b>Modulith boundary.</b> Allowed dependencies:
 *
 * <ul>
 *   <li>{@code tenant} - TenantContext ScopedValue resolution
 *   <li>{@code billing} - CreditLedger reserve/settle/release wiring (LLM-06)
 *   <li>{@code shared.lang} - IdentifiedEnum for Action and BYOKProvider
 *   <li>{@code gmail :: persistence.crypto} - RefreshTokenCipher reuse for BYOK key encryption
 *       (D-A5)
 * </ul>
 *
 * <p><b>Sub-packages:</b>
 *
 * <ul>
 *   <li>{@code domain} - framework-free domain vocabulary, enums, validators (Action, BYOKProvider,
 *       ToolCallResult, SanitizationContext, ActionValidator, AllowListedTools,
 *       RuleCompileToolValidator, *Exception)
 *   <li>{@code usecases} - public service contract (LlmGateway interface) + impl + use-case
 *       commands and results
 *   <li>{@code byok} - user BYOK key lifecycle, resolver, and SSRF-hardened endpoint validation
 *   <li>{@code gateway} - external-service adapters (Spring AI vendor adapters under {@code
 *       gateway.springai}; sanitization pipeline under {@code gateway.sanitization}, both
 *       ArchUnit-isolated for vendor SDKs)
 * </ul>
 */
@ApplicationModule(
        displayName = "LLM Gateway",
        allowedDependencies = {
            "tenant",
            "config",
            "billing",
            "billing :: domain",
            "billing :: usecases",
            "shared :: error",
            "shared :: exception",
            "shared :: html",
            "shared :: net",
            "shared :: persistence",
            "shared :: lang",
            "gmail :: persistence.crypto"
        })
package com.zeromail.core.llm;

import org.springframework.modulith.ApplicationModule;
