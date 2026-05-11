package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.RawToolCall;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmGatewayWave0Test {

    @Test
    void exposes_gateway_contract_for_downstream_rules_and_triage() throws Exception {
        LlmGateway gateway =
                new LlmGatewayImpl(
                        new FixedLlmModelClient(),
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        new ZeroMailLlmProperties(
                                BYOKProvider.OPENAI,
                                "https://openrouter.ai/api/v1",
                                "test-platform-key",
                                "openai/gpt-4o-mini",
                                "openai/gpt-4o-mini",
                                "openai/gpt-4o-mini",
                                null,
                                null),
                        new AllowListedTools(),
                        new ActionValidator());

        assertThat(
                        ScopedValue.where(TenantContext.TENANT, UUID.randomUUID().toString())
                                .call(() -> gateway.chat(CallSite.PREVIEW, "hi"))
                                .action())
                .isNotNull();
    }

    private static final class FixedLlmModelClient implements LlmModelClient {

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            return new LlmChatResult(
                    List.of(new RawToolCall("archive", "{}")), new LlmUsage(1, 1, "stop"));
        }
    }

    private static final class PassThroughSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext(context.content(), 1, false, null);
        }
    }
}
