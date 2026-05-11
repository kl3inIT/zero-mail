package com.zeromail.core.llm.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.RawToolCall;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmGatewayObservabilityTest {

    @Test
    void no_span_attribute_value_contains_prompt_or_completion_content() throws Exception {
        String sentinel = "SENTINEL_PROMPT_CONTENT_DO_NOT_LOG_X7Q9";
        InMemorySpanExporter inMemorySpanExporter = new InMemorySpanExporter();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(inMemorySpanExporter);
        LlmGateway gateway =
                gateway(
                        new EchoingModelClient(sentinel),
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        llmProperties(),
                        new AllowListedTools(),
                        observationRegistry);

        ScopedValue.where(TenantContext.TENANT, UUID.randomUUID().toString())
                .run(
                        () -> {
                            try {
                                gateway.chat(CallSite.PREVIEW, "<p>" + sentinel + "</p>");
                            } catch (RuntimeException ignoredRuntimeException) {
                                // Span metadata is the assertion target; the model result may be
                                // invalid here.
                            }
                        });

        List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();
        for (SpanData span : spans) {
            for (Map.Entry<String, String> attribute : span.attributes().entrySet()) {
                assertThat(attribute.getValue())
                        .as("span %s attribute %s leaked sentinel", span.name(), attribute.getKey())
                        .doesNotContain(sentinel);
            }
            for (EventData event : span.events()) {
                for (Map.Entry<String, String> attribute : event.attributes().entrySet()) {
                    assertThat(attribute.getValue()).doesNotContain(sentinel);
                }
            }
        }
    }

    private static LlmGateway gateway(
            LlmModelClient modelClient,
            SanitizationPipeline sanitizationPipeline,
            ZeroMailLlmProperties llmProperties,
            AllowListedTools allowListedTools,
            ObservationRegistry observationRegistry)
            throws Exception {
        Class<?> implementationClass =
                Class.forName("com.zeromail.core.llm.usecases.LlmGatewayImpl");
        java.lang.reflect.Constructor<?> constructor =
                implementationClass.getDeclaredConstructor(
                        LlmModelClient.class,
                        SanitizationPipeline.class,
                        ZeroMailLlmProperties.class,
                        AllowListedTools.class,
                        ActionValidator.class,
                        ObservationRegistry.class);
        constructor.setAccessible(true);
        return (LlmGateway)
                constructor.newInstance(
                        modelClient,
                        sanitizationPipeline,
                        llmProperties,
                        allowListedTools,
                        new ActionValidator(),
                        observationRegistry);
    }

    private static ZeroMailLlmProperties llmProperties() {
        return new ZeroMailLlmProperties(
                BYOKProvider.OPENAI,
                "https://openrouter.ai/api/v1",
                "test-platform-key",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini",
                null,
                null);
    }

    private static final class EchoingModelClient implements LlmModelClient {

        private final String sentinel;

        private EchoingModelClient(String sentinel) {
            this.sentinel = sentinel;
        }

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            return new LlmChatResult(
                    List.of(new RawToolCall("label", "{\"value\":\"" + sentinel + "\"}")),
                    new LlmUsage(11, 7, "stop"));
        }
    }

    private static final class PassThroughSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext(context.content(), 4, false, null);
        }
    }

    private static final class InMemorySpanExporter
            implements ObservationHandler<Observation.Context> {

        private final List<SpanData> finishedSpanItems = new ArrayList<>();

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            Map<String, String> attributes = new LinkedHashMap<>();
            for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
                attributes.put(keyValue.getKey(), keyValue.getValue());
            }
            for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
                attributes.put(keyValue.getKey(), keyValue.getValue());
            }
            finishedSpanItems.add(new SpanData(context.getName(), attributes, List.of()));
        }

        private List<SpanData> getFinishedSpanItems() {
            return finishedSpanItems;
        }
    }

    private record SpanData(String name, Map<String, String> attributes, List<EventData> events) {}

    private record EventData(Map<String, String> attributes) {}
}
