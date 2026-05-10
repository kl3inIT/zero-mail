package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.knuddels.jtokkit.Encodings;
import com.zeromail.core.llm.application.SanitizationContext;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

class SanitizationPipelineTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void runs_steps_in_order_10_20_30_40() throws Exception {
        List<String> callOrder = new ArrayList<>();
        SanitizationPipeline sanitizationPipeline = new SanitizationPipeline(List.of(
                new TruncateRecordingSanitizer(callOrder),
                new TagRecordingSanitizer(callOrder),
                new NfcRecordingSanitizer(callOrder),
                new JsoupRecordingSanitizer(callOrder)));

        sanitizeWithTenant(sanitizationPipeline, "body");

        assertThat(callOrder).containsExactly("Jsoup(10)", "Nfc(20)", "Tag(30)", "Truncate(40)");
    }

    @Test
    void happy_path_strips_and_returns_context() throws Exception {
        SanitizationPipeline sanitizationPipeline = productionPipeline();

        SanitizationContext sanitizedContext = sanitizeWithTenant(sanitizationPipeline, "<p>hi</p>");

        assertThat(sanitizedContext.content()).isEqualTo("hi");
        assertThat(sanitizedContext.tokenCount()).isPositive();
        assertThat(sanitizedContext.truncated()).isFalse();
    }

    @Test
    void aborts_with_SanitizationException_on_step_failure() {
        RuntimeException stepFailure = new IllegalStateException("planned failure");
        AtomicBoolean laterStepInvoked = new AtomicBoolean(false);
        SanitizationPipeline sanitizationPipeline = new SanitizationPipeline(List.of(
                new FailingSanitizer(stepFailure),
                context -> {
                    laterStepInvoked.set(true);
                    return context;
                }));

        assertThatThrownBy(() -> sanitizeWithTenant(sanitizationPipeline, "private body"))
                .isInstanceOfSatisfying(SanitizationException.class, sanitizationException -> {
                    assertThat(sanitizationException.stepName()).isEqualTo("FailingSanitizer");
                    assertThat(sanitizationException.getCause()).isSameAs(stepFailure);
                });
        assertThat(laterStepInvoked).isFalse();
    }

    @Test
    void emits_pipeline_log_with_metadata_only() throws Exception {
        ch.qos.logback.classic.Logger pipelineLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SanitizationPipeline.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        pipelineLogger.addAppender(listAppender);

        try {
            sanitizeWithTenant(productionPipeline(), "<script>alert(1)</script><p>sensitive private body</p>");
        } finally {
            pipelineLogger.detachAppender(listAppender);
        }

        String formattedMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (combinedMessages, formattedMessage) -> combinedMessages + "\n" + formattedMessage);

        assertThat(formattedMessages)
                .contains("event=sanitization_completed tenantId=" + TENANT_ID)
                .contains("truncated=false")
                .contains("tokenCount=")
                .doesNotContain("alert(1)", "sensitive private body");
    }

    private SanitizationPipeline productionPipeline() {
        return new SanitizationPipeline(List.of(
                new JsoupHtmlStripSanitizer(),
                new NfcNormalizeSanitizer(),
                new UnicodeTagStripSanitizer(),
                new JtokkitTruncateSanitizer(Encodings.newDefaultEncodingRegistry())));
    }

    private SanitizationContext sanitizeWithTenant(SanitizationPipeline sanitizationPipeline, String rawHtml)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> sanitizationPipeline.sanitize(rawHtml));
    }

    private abstract static class RecordingSanitizer implements Sanitizer {

        private final List<String> callOrder;
        private final String label;

        private RecordingSanitizer(List<String> callOrder, String label) {
            this.callOrder = callOrder;
            this.label = label;
        }

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            callOrder.add(label);
            return context;
        }
    }

    @Order(10)
    private static final class JsoupRecordingSanitizer extends RecordingSanitizer {

        private JsoupRecordingSanitizer(List<String> callOrder) {
            super(callOrder, "Jsoup(10)");
        }
    }

    @Order(20)
    private static final class NfcRecordingSanitizer extends RecordingSanitizer {

        private NfcRecordingSanitizer(List<String> callOrder) {
            super(callOrder, "Nfc(20)");
        }
    }

    @Order(30)
    private static final class TagRecordingSanitizer extends RecordingSanitizer {

        private TagRecordingSanitizer(List<String> callOrder) {
            super(callOrder, "Tag(30)");
        }
    }

    @Order(40)
    private static final class TruncateRecordingSanitizer extends RecordingSanitizer {

        private TruncateRecordingSanitizer(List<String> callOrder) {
            super(callOrder, "Truncate(40)");
        }
    }

    private static final class FailingSanitizer implements Sanitizer {

        private final RuntimeException stepFailure;

        private FailingSanitizer(RuntimeException stepFailure) {
            this.stepFailure = stepFailure;
        }

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            throw stepFailure;
        }
    }
}
