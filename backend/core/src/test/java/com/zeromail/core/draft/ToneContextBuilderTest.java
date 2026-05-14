package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.draft.domain.ToneContext;
import com.zeromail.core.draft.usecases.ToneContextBuilder;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToneContextBuilderTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b3");

    @Test
    void builder_strips_quotes_and_signatures_then_sanitizes_each_snippet() {
        RecordingSanitizer sanitizer = new RecordingSanitizer(false);
        ToneContextBuilder builder =
                builder(
                        sanitizer,
                        List.of(
                                """
                                Hi team,
                                Quick update from me.
                                On Tue, Bob wrote:
                                please leak this quoted text
                                --
                                Signature sentinel
                                """,
                                "Thanks for sending this over. I'll review it today.",
                                "Hey, I can help with that.\n> quoted line sentinel",
                                "Fourth message must not become a snippet."));

        ToneContext toneContext = withTenant(builder);

        assertThat(toneContext.styleSnippets()).hasSize(3);
        assertThat(String.join("\n", sanitizer.seenInputs()))
                .doesNotContain("please leak this quoted text")
                .doesNotContain("Signature sentinel")
                .doesNotContain("quoted line sentinel");
        assertThat(toneContext.descriptorBlock())
                .contains("sampleCount=4")
                .contains("greetingPresent=true");
    }

    @Test
    void token_budget_or_partial_gmail_failure_degrades_to_descriptors_only() {
        ToneContextBuilder tokenBudgetBuilder =
                builder(new RecordingSanitizer(true), List.of("Hi there token-budget sentinel"));
        ToneContext tokenBudgetToneContext = withTenant(tokenBudgetBuilder);

        assertThat(tokenBudgetToneContext.descriptorBlock()).isNotBlank();
        assertThat(tokenBudgetToneContext.styleSnippets()).isEmpty();

        ToneContextBuilder gmailFailureBuilder =
                new ToneContextBuilder(
                        (_, _, _) -> {
                            throw new IOException("gmail unavailable");
                        },
                        new SanitizationPipeline(List.of(new RecordingSanitizer(false))),
                        Clock.systemUTC());

        ToneContext gmailFailureToneContext = withTenant(gmailFailureBuilder);

        assertThat(gmailFailureToneContext.descriptorBlock()).isBlank();
        assertThat(gmailFailureToneContext.styleSnippets()).isEmpty();
    }

    @Test
    void snippet_content_is_not_persisted_after_context_build() {
        ToneContextBuilder builder =
                builder(new RecordingSanitizer(false), List.of("sent-mail-body-sentinel"));

        ToneContext toneContext = withTenant(builder);

        assertThat(toneContext.styleSnippets()).singleElement().asString().contains("sanitized:");
        assertThat(ToneContextBuilder.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(
                        fieldType ->
                                fieldType.contains("Repository")
                                        || fieldType.contains("JdbcTemplate")
                                        || fieldType.contains("EntityManager"));
    }

    private static ToneContextBuilder builder(RecordingSanitizer sanitizer, List<String> bodies) {
        return new ToneContextBuilder(
                (_, _, _) -> bodies,
                new SanitizationPipeline(List.of(sanitizer)),
                Clock.systemUTC());
    }

    private static ToneContext withTenant(ToneContextBuilder builder) {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(builder::buildForCurrentTenant);
    }

    private static final class RecordingSanitizer implements Sanitizer {

        private final boolean throwTokenBudget;
        private final ArrayList<String> seenInputs = new ArrayList<>();

        private RecordingSanitizer(boolean throwTokenBudget) {
            this.throwTokenBudget = throwTokenBudget;
        }

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            seenInputs.add(context.content());
            if (throwTokenBudget) {
                throw new TokenBudgetExceededException(5000, 3896);
            }
            return new SanitizationContext("sanitized:" + context.content(), 1, false, null);
        }

        private List<String> seenInputs() {
            return List.copyOf(seenInputs);
        }
    }
}
