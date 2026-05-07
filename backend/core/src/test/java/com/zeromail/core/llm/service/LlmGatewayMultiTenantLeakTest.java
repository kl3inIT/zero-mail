package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.IntStream;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.gateway.springai.ZeroMailLlmProperties;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.model.SanitizationContext;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;
import org.junit.jupiter.api.Test;

class LlmGatewayMultiTenantLeakTest {

    private record Seed(UUID tenantId, String rawHtml) {}

    @Test
    void concurrent_virtual_thread_requests_never_cross_tenant() throws Exception {
        int requestCount = 100;
        LlmGateway gateway = gateway();
        List<Seed> seeds = IntStream.range(0, requestCount)
                .mapToObj(index -> new Seed(UUID.randomUUID(), "hello-" + index))
                .toList();

        try (var scope = StructuredTaskScope.<ToolCallResult>open()) {
            var subtasks = seeds.stream()
                    .map(seed -> scope.fork(() -> ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                            .call(() -> gateway.chat(CallSite.PREVIEW, seed.rawHtml()))))
                    .toList();
            scope.join();
            for (int index = 0; index < requestCount; index++) {
                ToolCallResult toolCallResult = subtasks.get(index).get();
                assertThat(toolCallResult.args().get("boundTenantId"))
                        .isEqualTo(seeds.get(index).tenantId().toString());
            }
        }
    }

    private LlmGateway gateway() {
        return new LlmGatewayImpl(
                new TenantEchoLlmModelClient(),
                new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                new ZeroMailLlmProperties(
                        BYOKProvider.OPENAI_COMPATIBLE,
                        "https://openrouter.ai/api/v1",
                        "test-platform-key",
                        "openai/gpt-4o-mini",
                        "openai/gpt-4o-mini",
                        "openai/gpt-4o-mini",
                        null,
                        null),
                new AllowListedTools());
    }

    private static final class TenantEchoLlmModelClient implements LlmModelClient {

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            String tenantId = TenantContext.currentOrThrow();
            return new LlmChatResult(
                    List.of(new RawToolCall("label", "{\"boundTenantId\":\"" + tenantId + "\"}")),
                    new LlmUsage(1, 1, "stop"));
        }
    }

    private static final class PassThroughSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext(context.content(), 1, false, null);
        }
    }
}
