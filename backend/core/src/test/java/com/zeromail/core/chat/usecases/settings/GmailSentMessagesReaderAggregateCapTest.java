package com.zeromail.core.chat.usecases.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GmailSentMessagesReaderAggregateCapTest {

    @Test
    void capsAggregatePromptCharsAfterPerSampleTruncationAndLogsOnlyMetadata() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String body = "A".repeat(GmailSentMessagesReader.MAX_BODY_CHARS_PER_SAMPLE);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        configureGmail(gmailApiClientFactory, tenantId, sentMessages(50, body));
        GmailSentMessagesReader gmailSentMessagesReader =
                new GmailSentMessagesReader(gmailApiClientFactory);
        ListAppender<ILoggingEvent> logAppender = attachReaderLogAppender();

        try {
            List<GmailSentMessagesReader.SentMessageSummary> samples =
                    gmailSentMessagesReader.readRecentSent(tenantId, 50);

            int aggregateChars =
                    samples.stream().mapToInt(sample -> sample.bodyPlaintext().length()).sum();
            assertThat(aggregateChars)
                    .isLessThanOrEqualTo(GmailSentMessagesReader.MAX_AGGREGATE_PROMPT_CHARS);
            assertThat(samples).hasSize(15);
            assertThat(logAppender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(
                            message ->
                                    message.contains("event=voice.generate.aggregate_cap_applied"))
                    .singleElement()
                    .satisfies(
                            message -> {
                                assertThat(message).contains("originalSampleCount=50");
                                assertThat(message).contains("cappedSampleCount=15");
                                assertThat(message).contains("aggregateChars=60000");
                                assertThat(message).doesNotContain(body);
                            });
        } finally {
            detachReaderLogAppender(logAppender);
        }
    }

    @Test
    void emptySentFolderReturnsEmptyList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        configureGmail(gmailApiClientFactory, tenantId, List.of());
        GmailSentMessagesReader gmailSentMessagesReader =
                new GmailSentMessagesReader(gmailApiClientFactory);

        assertThat(gmailSentMessagesReader.readRecentSent(tenantId, 20)).isEmpty();
    }

    private static void configureGmail(
            GmailApiClientFactory gmailApiClientFactory, UUID tenantId, List<Message> messages)
            throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages gmailMessages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.List listRequest = mock(Gmail.Users.Messages.List.class);

        given(gmailApiClientFactory.buildClientForTenant(tenantId)).willReturn(gmail);
        given(gmail.users()).willReturn(users);
        given(users.messages()).willReturn(gmailMessages);
        given(gmailMessages.list("me")).willReturn(listRequest);
        given(listRequest.setQ(anyString())).willReturn(listRequest);
        given(listRequest.setMaxResults(anyLong())).willReturn(listRequest);
        given(listRequest.execute())
                .willReturn(new ListMessagesResponse().setMessages(messageReferences(messages)));

        for (Message message : messages) {
            Gmail.Users.Messages.Get getRequest = mock(Gmail.Users.Messages.Get.class);
            given(gmailMessages.get("me", message.getId())).willReturn(getRequest);
            given(getRequest.setFormat(anyString())).willReturn(getRequest);
            given(getRequest.execute()).willReturn(message);
        }
    }

    private static List<Message> sentMessages(int count, String body) {
        List<Message> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(sentMessage("sent-" + index, body));
        }
        return messages;
    }

    private static List<Message> messageReferences(List<Message> messages) {
        return messages.stream().map(message -> new Message().setId(message.getId())).toList();
    }

    private static Message sentMessage(String id, String body) {
        return new Message()
                .setId(id)
                .setPayload(
                        new MessagePart()
                                .setMimeType("multipart/alternative")
                                .setHeaders(
                                        List.of(
                                                header("From", "founder@example.test"),
                                                header("To", "vip@example.test"),
                                                header("Subject", "Sent sample")))
                                .setParts(
                                        List.of(
                                                new MessagePart()
                                                        .setMimeType("text/plain")
                                                        .setBody(
                                                                new MessagePartBody()
                                                                        .setData(encoded(body))))));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static String encoded(String body) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private static ListAppender<ILoggingEvent> attachReaderLogAppender() {
        ch.qos.logback.classic.Logger readerLogger =
                (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(GmailSentMessagesReader.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        readerLogger.addAppender(logAppender);
        return logAppender;
    }

    private static void detachReaderLogAppender(ListAppender<ILoggingEvent> logAppender) {
        ch.qos.logback.classic.Logger readerLogger =
                (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(GmailSentMessagesReader.class);
        readerLogger.detachAppender(logAppender);
    }
}
