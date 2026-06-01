package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableException;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableReason;
import com.zeromail.core.llm.gateway.sanitization.JsoupSafeHtmlSanitizer;
import com.zeromail.core.shared.crypto.CryptoProperties;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecentInboxReadServiceTest {

    @Test
    void fetchPageRejectsUnsignedCursorBeforeRepositoryOrGmailAccess() {
        GmailConnectionRepository gmailConnectionRepository = mock(GmailConnectionRepository.class);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        RecentInboxReadService recentInboxReadService =
                service(gmailConnectionRepository, gmailApiClientFactory);
        String unsignedCursor =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("v1\n0\nnext-page-token".getBytes(StandardCharsets.UTF_8));

        RecentInboxUnavailableException inboxUnavailableException = null;
        try {
            recentInboxReadService.fetchPage(UUID.randomUUID(), unsignedCursor, 20);
        } catch (RecentInboxUnavailableException caughtInboxUnavailableException) {
            inboxUnavailableException = caughtInboxUnavailableException;
        }

        assertThat(inboxUnavailableException).isNotNull();
        assertThat(inboxUnavailableException.reason())
                .isEqualTo(RecentInboxUnavailableReason.INVALID_CURSOR);
        verifyNoInteractions(gmailConnectionRepository, gmailApiClientFactory);
    }

    @Test
    void inlineCidImageSourcesRewritesEncodedCidImageSources() throws Exception {
        MessagePart inlineImagePart =
                new MessagePart()
                        .setMimeType("image/png")
                        .setHeaders(
                                List.of(
                                        new MessagePartHeader()
                                                .setName("Content-ID")
                                                .setValue("<logo image@example.test>")))
                        .setBody(
                                new MessagePartBody()
                                        .setData(
                                                Base64.getUrlEncoder()
                                                        .withoutPadding()
                                                        .encodeToString(
                                                                "image-bytes"
                                                                        .getBytes(
                                                                                StandardCharsets
                                                                                        .UTF_8))));
        MessagePart payload = new MessagePart().setParts(List.of(inlineImagePart));
        Method inlineCidImageSources =
                RecentInboxReadService.class.getDeclaredMethod(
                        "inlineCidImageSources",
                        Gmail.class,
                        String.class,
                        MessagePart.class,
                        String.class);
        inlineCidImageSources.setAccessible(true);
        RecentInboxReadService recentInboxReadService =
                service(mock(GmailConnectionRepository.class), mock(GmailApiClientFactory.class));

        String renderedHtml =
                (String)
                        inlineCidImageSources.invoke(
                                recentInboxReadService,
                                mock(Gmail.class),
                                "gmail-message-1",
                                payload,
                                "<div><img src=\"cid:logo%20image@example.test\"></div>");

        assertThat(renderedHtml).contains("src=\"data:image/png;base64,");
        assertThat(renderedHtml).doesNotContain("cid:logo%20image@example.test");
    }

    private static CryptoProperties cryptoProperties() {
        return new CryptoProperties(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    private static RecentInboxReadService service(
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory) {
        return new RecentInboxReadService(
                gmailConnectionRepository,
                gmailApiClientFactory,
                cryptoProperties(),
                new JsoupSafeHtmlSanitizer(),
                org.mockito.Mockito.mock(
                        com.zeromail.core.inbox.usecases.InboxBackfillEnqueuer.class),
                org.mockito.Mockito.mock(
                        com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository.class));
    }
}
