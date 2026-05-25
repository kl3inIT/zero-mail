package com.zeromail.core.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.event.MailOutboundObserved;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.GmailDeliveryProcessingService;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import com.zeromail.core.shared.privacy.Sensitive;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class GmailDeliveryProcessingServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b2");
    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b3");
    private static final UUID CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000005b4");
    private static final String GMAIL_THREAD_ID = "gmail-thread-sent-only";
    private static final String GMAIL_MESSAGE_ID = "gmail-message-sent-only";

    @Test
    void process_delivery_publishes_outbound_event_for_sent_only_observation() throws Exception {
        PubSubDeliveryRepository deliveryRepository = mock(PubSubDeliveryRepository.class);
        MailMessageObservedRepository observedRepository =
                mock(MailMessageObservedRepository.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        GmailConnectionRepository connectionRepository = mock(GmailConnectionRepository.class);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        RefreshTokenCipher refreshTokenCipher = mock(RefreshTokenCipher.class);
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        EmailAddressCanonicalizer emailAddressCanonicalizer = new EmailAddressCanonicalizer();
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
        connection.setLastSyncedHistoryId(100L);
        PubSubDeliveryEntity delivery =
                new PubSubDeliveryEntity(
                        DELIVERY_ID, TENANT_ID, "pubsub-message", 200L, "{\"historyId\":\"200\"}");
        Message historyMessage = new Message().setId(GMAIL_MESSAGE_ID);
        History history =
                new History()
                        .setId(BigInteger.valueOf(101L))
                        .setMessagesAdded(
                                List.of(new HistoryMessageAdded().setMessage(historyMessage)));
        Message gmailMessage =
                new Message()
                        .setId(GMAIL_MESSAGE_ID)
                        .setThreadId(GMAIL_THREAD_ID)
                        .setLabelIds(List.of("SENT"))
                        .setInternalDate(1_779_999_999_000L);

        when(connectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(connection));
        when(refreshTokenCipher.decrypt(
                        connection.getRefreshTokenEncrypted(), TENANT_ID.toString()))
                .thenReturn("refresh-token".getBytes(StandardCharsets.UTF_8));
        when(gmailApiClientFactory.refreshAccessToken("refresh-token"))
                .thenReturn(
                        new GmailApiClientFactory.TokenRefreshResult(
                                Sensitive.of("access-token"),
                                Instant.parse("2026-05-12T12:00:00Z")));
        when(gmailApiClientFactory.buildGmailClient("access-token")).thenReturn(gmail);
        when(gmail.users()).thenReturn(gmailUsers);
        when(gmailUsers.history()).thenReturn(gmailHistory);
        when(gmailHistory.list("me")).thenReturn(historyListRequest);
        when(historyListRequest.setStartHistoryId(BigInteger.valueOf(100L)))
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
        when(observedRepository.insertObservedIfAbsent(
                        eq(TENANT_ID),
                        eq(GMAIL_MESSAGE_ID),
                        eq(GMAIL_THREAD_ID),
                        eq(101L),
                        any(String[].class),
                        eq(1_779_999_999_000L),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(false)))
                .thenReturn(1);

        new GmailDeliveryProcessingService(
                        deliveryRepository,
                        observedRepository,
                        connectionService,
                        connectionRepository,
                        gmailApiClientFactory,
                        refreshTokenCipher,
                        applicationEventPublisher,
                        emailAddressCanonicalizer,
                        transactionManager)
                .processDelivery(delivery);

        verify(historyListRequest, never()).setLabelId("INBOX");
        verify(connectionRepository).updateLastSyncedHistoryIdMonotonic(TENANT_ID, 200L);
        verify(deliveryRepository).updateStatus(DELIVERY_ID, "PROCESSED");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anySatisfy(
                        publishedEvent -> {
                            assertThat(publishedEvent).isInstanceOf(MailOutboundObserved.class);
                            MailOutboundObserved outboundObserved =
                                    (MailOutboundObserved) publishedEvent;
                            assertThat(outboundObserved.tenantId()).isEqualTo(TENANT_ID);
                            assertThat(outboundObserved.gmailThreadId()).isEqualTo(GMAIL_THREAD_ID);
                            assertThat(outboundObserved.gmailMessageId())
                                    .isEqualTo(GMAIL_MESSAGE_ID);
                        });
    }

    @Test
    void process_delivery_marks_missing_refresh_token_dead_without_retry() throws Exception {
        PubSubDeliveryRepository deliveryRepository = mock(PubSubDeliveryRepository.class);
        MailMessageObservedRepository observedRepository =
                mock(MailMessageObservedRepository.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        GmailConnectionRepository connectionRepository = mock(GmailConnectionRepository.class);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        RefreshTokenCipher refreshTokenCipher = mock(RefreshTokenCipher.class);
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        EmailAddressCanonicalizer emailAddressCanonicalizer = new EmailAddressCanonicalizer();
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);

        GmailConnectionEntity connection =
                new GmailConnectionEntity(
                        CONNECTION_ID,
                        TENANT_ID,
                        "user@example.test",
                        GmailConnectionStatus.CONNECTED);
        connection.setLastSyncedHistoryId(100L);
        PubSubDeliveryEntity delivery =
                new PubSubDeliveryEntity(
                        DELIVERY_ID, TENANT_ID, "pubsub-message", 200L, "{\"historyId\":\"200\"}");

        when(connectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(connection));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doNothing().when(transactionManager).commit(transactionStatus);
        doNothing().when(transactionManager).rollback(transactionStatus);

        new GmailDeliveryProcessingService(
                        deliveryRepository,
                        observedRepository,
                        connectionService,
                        connectionRepository,
                        gmailApiClientFactory,
                        refreshTokenCipher,
                        applicationEventPublisher,
                        emailAddressCanonicalizer,
                        transactionManager)
                .processDelivery(delivery);

        verify(deliveryRepository).updateStatus(DELIVERY_ID, "DEAD");
        verify(deliveryRepository, never()).releaseForRetry(eq(DELIVERY_ID), any());
        verify(refreshTokenCipher, never()).decrypt(any(), any());
        verify(gmailApiClientFactory, never()).refreshAccessToken(any());
    }
}
