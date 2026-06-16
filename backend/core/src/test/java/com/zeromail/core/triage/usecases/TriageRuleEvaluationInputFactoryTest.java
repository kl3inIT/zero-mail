package com.zeromail.core.triage.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriageRuleEvaluationInputFactoryTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000011601");
    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000011602");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-factory";
    private static final String GMAIL_THREAD_ID = "gmail-thread-factory";
    private static final Instant OBSERVED_AT = Instant.parse("2026-06-10T03:06:36Z");

    private final GmailPreviewReadService gmailPreviewReadService =
            mock(GmailPreviewReadService.class);
    private final TriageRuleEvaluationInputFactory factory =
            new TriageRuleEvaluationInputFactory(gmailPreviewReadService);

    @Test
    void observed_event_fetch_uses_event_mailbox_instead_of_active_mailbox_context() {
        GmailPreviewMessage previewMessage = previewMessage();
        when(gmailPreviewReadService.fetchTriageInput(
                        TENANT_ID,
                        GMAIL_CONNECTION_ID,
                        GMAIL_MESSAGE_ID,
                        GMAIL_THREAD_ID,
                        OBSERVED_AT))
                .thenReturn(Optional.of(previewMessage));

        Optional<TriageRuleEvaluationInputFactory.TriageRuleEvaluationInput> result =
                factory.fetch(
                        new MailMessageObserved(
                                TENANT_ID,
                                GMAIL_CONNECTION_ID,
                                GMAIL_MESSAGE_ID,
                                GMAIL_THREAD_ID,
                                OBSERVED_AT));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().sanitizedSenderEmail()).isEqualTo("sender@example.test");
        verify(gmailPreviewReadService)
                .fetchTriageInput(
                        TENANT_ID,
                        GMAIL_CONNECTION_ID,
                        GMAIL_MESSAGE_ID,
                        GMAIL_THREAD_ID,
                        OBSERVED_AT);
    }

    private static GmailPreviewMessage previewMessage() {
        return new GmailPreviewMessage(
                GMAIL_MESSAGE_ID,
                GMAIL_THREAD_ID,
                "sender@example.test",
                "example.test",
                "Sender",
                List.of("owner@example.test"),
                List.of(),
                "Subject excerpt",
                "<rfc-message-id@example.test>",
                "",
                "",
                "sender@example.test",
                List.of("INBOX"),
                List.of(),
                OBSERVED_AT,
                OBSERVED_AT,
                false,
                false,
                null,
                null,
                false,
                false,
                false,
                Optional.empty(),
                Set.of());
    }
}
