package com.zeromail.api.dto.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessage;
import com.zeromail.core.inbox.domain.MessageClass;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Phase 12 G1 (CAL-TRIAGE-02): pins {@code GmailInboxMessageResponse.from(...)} threading W4's
 * calendar classification onto the wire DTO, and proves {@code @Schema(allowableValues = {...})}
 * stays in lock-step with {@link MessageClass} via {@link #from_serializesAllowableValuesPerEnum}.
 */
class GmailInboxMessageResponseTest {

    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-06-25T15:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-20T08:00:00Z");

    @Test
    void from_propagatesCancelMessageClass() {
        RecentInboxMessage message =
                messageWithClassification(MessageClass.CANCEL, EVENT_TIMESTAMP);

        GmailInboxMessageResponse response = GmailInboxMessageResponse.from(message);

        assertThat(response.messageClass()).isEqualTo("CANCEL");
        assertThat(response.eventDt()).isEqualTo(EVENT_TIMESTAMP);
    }

    @Test
    void from_propagatesNullMessageClassAsNull() {
        RecentInboxMessage message = messageWithClassification(null, null);

        GmailInboxMessageResponse response = GmailInboxMessageResponse.from(message);

        assertThat(response.messageClass()).isNull();
        assertThat(response.eventDt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(MessageClass.class)
    void from_serializesAllowableValuesPerEnum(MessageClass messageClassification) {
        RecentInboxMessage message =
                messageWithClassification(messageClassification, EVENT_TIMESTAMP);

        GmailInboxMessageResponse response = GmailInboxMessageResponse.from(message);

        // The wire string MUST equal the enum id() verbatim — if a future contributor adds a fifth
        // MessageClass value the @Schema(allowableValues) list must be updated to match, and this
        // test is the tripwire that forces it.
        assertThat(response.messageClass()).isEqualTo(messageClassification.id());
    }

    private static RecentInboxMessage messageWithClassification(
            MessageClass messageClassification, Instant eventTimestamp) {
        return new RecentInboxMessage(
                "190000000000ca01",
                "thread-ca01",
                "Test cancel",
                "Snippet",
                "tester@example.com",
                List.of(),
                List.of(),
                RECEIVED_AT,
                List.of("INBOX"),
                List.of(),
                false,
                false,
                messageClassification,
                eventTimestamp);
    }
}
