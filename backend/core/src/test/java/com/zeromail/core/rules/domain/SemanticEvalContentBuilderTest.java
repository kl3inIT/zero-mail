package com.zeromail.core.rules.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SemanticEvalContentBuilderTest {

    private static RuleEvaluationInput input(
            String subjectExcerpt,
            String senderDomain,
            List<String> labelIds,
            List<String> categories,
            boolean hasAttachment,
            boolean listUnsubscribePresent,
            boolean newsletterIndicatorPresent,
            Instant internalDate) {
        return new RuleEvaluationInput(
                "sender@" + senderDomain,
                senderDomain,
                List.of("me@example.com"),
                List.of(),
                subjectExcerpt,
                labelIds,
                categories,
                internalDate,
                internalDate,
                hasAttachment,
                listUnsubscribePresent,
                newsletterIndicatorPresent,
                false,
                Optional.empty(),
                Set.of());
    }

    @Test
    void emits_the_canonical_runtime_format_with_all_metadata_fields() {
        String content =
                SemanticEvalContentBuilder.build(
                        input(
                                "Re: Invoice",
                                "stripe.com",
                                List.of("INBOX", "IMPORTANT"),
                                List.of("CATEGORY_UPDATES"),
                                true,
                                true,
                                false,
                                Instant.parse("2026-05-09T10:00:00Z")));

        assertThat(content)
                .isEqualTo(
                        """
                        subjectExcerpt=Re: Invoice
                        senderDomain=stripe.com
                        labelCount=2
                        categories=category_updates
                        hasAttachment=true
                        listUnsubscribePresent=true
                        newsletterIndicatorPresent=false
                        autoReplyIndicatorPresent=false
                        internalDate=2026-05-09T10:00:00Z""");
    }

    @Test
    void renders_every_boolean_flag_even_when_false_so_test_and_runtime_prompts_match() {
        // The old preview builder only appended a flag line when the flag was true, so a message
        // with no flags produced a shorter prompt than runtime. The shared builder must always emit
        // all three booleans.
        String content =
                SemanticEvalContentBuilder.build(
                        input(
                                "Hello",
                                "example.com",
                                List.of(),
                                List.of(),
                                false,
                                false,
                                false,
                                Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(content)
                .contains("hasAttachment=false")
                .contains("listUnsubscribePresent=false")
                .contains("newsletterIndicatorPresent=false")
                .contains("autoReplyIndicatorPresent=false")
                .contains("labelCount=0")
                .contains("categories=");
    }
}
