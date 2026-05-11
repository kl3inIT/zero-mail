/**
 * LLM Gateway domain (Phase 02C). Single chokepoint for all LLM traffic in Zero Mail.
 *
 * <p><b>Cross-phase contract.</b> Phase 3 (Rules Engine) and Phase 4 (Triage) import {@link
 * com.zeromail.core.llm.service.LlmGateway} verbatim and call {@code chat(callSite, rawHtml)} for
 * every LLM call.
 *
 * <p><b>Modulith boundary.</b> Allowed dependencies:
 *
 * <ul>
 *   <li>{@code tenant} - TenantContext ScopedValue resolution
 *   <li>{@code billing} - CreditLedger reserve/settle/release wiring (LLM-06)
 *   <li>{@code shared.persistence} - AbstractTenantOwnedEntity for TenantByokCredentialsEntity
 *   <li>{@code shared.lang} - IdentifiedEnum for Action and BYOKProvider
 *   <li>{@code gmail.persistence.crypto} - RefreshTokenCipher reuse for BYOK key encryption (D-A5)
 * </ul>
 *
 * <p><b>Sub-packages:</b>
 *
 * <ul>
 *   <li>{@code model} - public records, enums, exceptions (Action, BYOKProvider, ToolCallResult,
 *       SanitizationContext, *Exception)
 *   <li>{@code service} - public service contract (LlmGateway interface) + impl + ActionValidator +
 *       ByokService
 *   <li>{@code persistence} - TenantByokCredentialsEntity + Repository
 *   <li>{@code gateway.springai} - Spring AI vendor adapter (ArchUnit-isolated)
 *   <li>{@code gateway.sanitization} - sanitization pipeline (ArchUnit-isolated for jsoup +
 *       jtokkit)
 * </ul>
 */
@ApplicationModule(
        displayName = "LLM Gateway",
        allowedDependencies = {
            "tenant",
            "billing",
            "shared.persistence",
            "shared.lang",
            "gmail.persistence.crypto"
        })
package com.zeromail.core.llm;

import org.springframework.modulith.ApplicationModule;
