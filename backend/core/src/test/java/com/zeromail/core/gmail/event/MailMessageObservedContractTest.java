package com.zeromail.core.gmail.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.RecordComponent;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MailMessageObservedContractTest {

    private static final String MAIL_MESSAGE_OBSERVED =
            "com.zeromail.core.gmail.event.MailMessageObserved";
    private static final Pattern FORBIDDEN_CONTENT_FIELD =
            Pattern.compile("(?i)subject|snippet|body|sender.?name");

    @Test
    void mail_message_observed_event_contract_type_is_present() {
        assertFutureTypePresent(MAIL_MESSAGE_OBSERVED);
    }

    @Test
    void mail_message_observed_carries_only_stable_ids_and_timestamp() throws Exception {
        Class<?> eventClass = Class.forName(MAIL_MESSAGE_OBSERVED);

        assertThat(eventClass.isRecord()).isTrue();
        assertThat(Stream.of(eventClass.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("tenantId", "gmailMessageId", "gmailThreadId", "observedAt");
        assertThat(Stream.of(eventClass.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(componentName -> FORBIDDEN_CONTENT_FIELD.matcher(componentName).find());
        assertThat(Stream.of(eventClass.getDeclaredFields()).map(field -> field.getName()))
                .noneMatch(fieldName -> FORBIDDEN_CONTENT_FIELD.matcher(fieldName).find());
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
