package com.zeromail.core.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.GmailDeliveryProcessingService;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class GmailDeliveryProcessingSenderEmailTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000005c01");
    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000005c02");
    private static final UUID CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000005c03");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-sender";
    private static final String GMAIL_THREAD_ID = "gmail-thread-sender";

    @Test
    void processDelivery_extractsCanonicalSenderEmailFromGmailMetadataHeader() throws Exception {
        Scenario scenario =
                Scenario.withMessage(
                        new Message()
                                .setId(GMAIL_MESSAGE_ID)
                                .setThreadId(GMAIL_THREAD_ID)
                                .setLabelIds(List.of("INBOX"))
                                .setInternalDate(1_779_999_111_000L)
                                .setPayload(
                                        new MessagePart()
                                                .setHeaders(
                                                        List.of(
                                                                new MessagePartHeader()
                                                                        .setName("From")
                                                                        .setValue(
                                                                                "Alice Example <Alice@Example.COM>")))));
        scenario.observedInsertReturns(1);

        scenario.service().processDelivery(scenario.delivery());

        ArgumentCaptor<String> senderEmailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> senderNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(scenario.messageGetRequest()).setFormat("metadata");
        verify(scenario.messageGetRequest())
                .setMetadataHeaders(List.of("From", "List-Unsubscribe", "List-Unsubscribe-Post"));
        verify(scenario.observedRepository())
                .insertObservedIfAbsent(
                        eq(TENANT_ID),
                        eq(CONNECTION_ID),
                        eq(GMAIL_MESSAGE_ID),
                        eq(GMAIL_THREAD_ID),
                        eq(301L),
                        any(String[].class),
                        eq(1_779_999_111_000L),
                        senderEmailCaptor.capture(),
                        senderNameCaptor.capture(),
                        eq(null),
                        eq(null),
                        eq(false));
        assertThat(senderEmailCaptor.getValue()).isEqualTo("alice@example.com");
        assertThat(senderNameCaptor.getValue()).isEqualTo("Alice Example");
    }

    @Test
    void processDelivery_persistsNullSenderEmailWhenFromHeaderIsMissing() throws Exception {
        Scenario scenario =
                Scenario.withMessage(
                        new Message()
                                .setId(GMAIL_MESSAGE_ID)
                                .setThreadId(GMAIL_THREAD_ID)
                                .setLabelIds(List.of("INBOX"))
                                .setInternalDate(1_779_999_111_000L)
                                .setPayload(new MessagePart().setHeaders(List.of())));
        scenario.observedInsertReturns(1);

        scenario.service().processDelivery(scenario.delivery());

        verify(scenario.observedRepository())
                .insertObservedIfAbsent(
                        eq(TENANT_ID),
                        eq(CONNECTION_ID),
                        eq(GMAIL_MESSAGE_ID),
                        eq(GMAIL_THREAD_ID),
                        eq(301L),
                        any(String[].class),
                        eq(1_779_999_111_000L),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(false));
    }

    private record Scenario(
            PubSubDeliveryEntity delivery,
            MailMessageObservedRepository observedRepository,
            Gmail.Users.Messages.Get messageGetRequest,
            GmailDeliveryProcessingService service) {

        static Scenario withMessage(Message gmailMessage) throws Exception {
            PubSubDeliveryRepository deliveryRepository = mock(PubSubDeliveryRepository.class);
            MailMessageObservedRepository observedRepository =
                    mock(MailMessageObservedRepository.class);
            GmailConnectionService connectionService = mock(GmailConnectionService.class);
            GmailConnectionRepository connectionRepository = mock(GmailConnectionRepository.class);
            GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
            ApplicationEventPublisher applicationEventPublisher =
                    mock(ApplicationEventPublisher.class);
            PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
            TransactionStatus transactionStatus = mock(TransactionStatus.class);
            Gmail gmail = mock(Gmail.class);
            Gmail.Users gmailUsers = mock(Gmail.Users.class);
            Gmail.Users.History gmailHistory = mock(Gmail.Users.History.class);
            Gmail.Users.History.List historyListRequest = mock(Gmail.Users.History.List.class);
            Gmail.Users.Messages gmailMessages = mock(Gmail.Users.Messages.class);
            Gmail.Users.Messages.Get messageGetRequest = mock(Gmail.Users.Messages.Get.class);

            GmailConnectionEntity connection =
                    new GmailConnectionEntity(
                            CONNECTION_ID,
                            TENANT_ID,
                            "user@example.test",
                            GmailConnectionStatus.CONNECTED);
            connection.setRefreshTokenEncrypted(new byte[] {1, 2, 3});
            connection.setLastSyncedHistoryId(300L);
            PubSubDeliveryEntity delivery =
                    new PubSubDeliveryEntity(
                            DELIVERY_ID,
                            TENANT_ID,
                            CONNECTION_ID,
                            "pubsub-message",
                            400L,
                            "{\"historyId\":\"400\"}");
            Message historyMessage = new Message().setId(GMAIL_MESSAGE_ID);
            History history =
                    new History()
                            .setId(BigInteger.valueOf(301L))
                            .setMessagesAdded(
                                    List.of(new HistoryMessageAdded().setMessage(historyMessage)));

            when(connectionRepository.findByIdAndTenantId(CONNECTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(connection));
            when(gmailApiClientFactory.buildClientForMailbox(
                            new MailboxRef(TENANT_ID, CONNECTION_ID)))
                    .thenReturn(gmail);
            when(gmail.users()).thenReturn(gmailUsers);
            when(gmailUsers.history()).thenReturn(gmailHistory);
            when(gmailHistory.list("me")).thenReturn(historyListRequest);
            when(historyListRequest.setStartHistoryId(BigInteger.valueOf(300L)))
                    .thenReturn(historyListRequest);
            when(historyListRequest.setHistoryTypes(List.of("messageAdded")))
                    .thenReturn(historyListRequest);
            when(historyListRequest.setMaxResults(500L)).thenReturn(historyListRequest);
            when(historyListRequest.execute())
                    .thenReturn(new ListHistoryResponse().setHistory(List.of(history)));
            when(gmailUsers.messages()).thenReturn(gmailMessages);
            when(gmailMessages.get("me", GMAIL_MESSAGE_ID)).thenReturn(messageGetRequest);
            when(messageGetRequest.setFormat("metadata")).thenReturn(messageGetRequest);
            when(messageGetRequest.setMetadataHeaders(
                            List.of("From", "List-Unsubscribe", "List-Unsubscribe-Post")))
                    .thenReturn(messageGetRequest);
            when(messageGetRequest.setFields("id,threadId,labelIds,internalDate,payload/headers"))
                    .thenReturn(messageGetRequest);
            when(messageGetRequest.execute()).thenReturn(gmailMessage);
            when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
            doNothing().when(transactionManager).commit(transactionStatus);
            doNothing().when(transactionManager).rollback(transactionStatus);

            GmailDeliveryProcessingService service =
                    new GmailDeliveryProcessingService(
                            deliveryRepository,
                            observedRepository,
                            mock(
                                    com.zeromail.core.inbox.usecases.InboxProjectionWriteService
                                            .class),
                            connectionService,
                            connectionRepository,
                            gmailApiClientFactory,
                            applicationEventPublisher,
                            new EmailAddressCanonicalizer(),
                            transactionManager);
            return new Scenario(delivery, observedRepository, messageGetRequest, service);
        }

        void observedInsertReturns(int insertedCount) {
            when(observedRepository.insertObservedIfAbsent(
                            eq(TENANT_ID),
                            eq(CONNECTION_ID),
                            eq(GMAIL_MESSAGE_ID),
                            eq(GMAIL_THREAD_ID),
                            eq(301L),
                            any(String[].class),
                            eq(1_779_999_111_000L),
                            any(),
                            any(),
                            any(),
                            any(),
                            anyBoolean()))
                    .thenReturn(insertedCount);
        }
    }
}
