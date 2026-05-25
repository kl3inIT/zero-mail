package com.zeromail.api.e2estub;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class E2eStubGmailApiClientFactoryTest {

    @Test
    void createDraftStoresDecodedTextBodyFromQuotedPrintableMime() throws Exception {
        E2eStubGmailApiClientFactory gmailApiClientFactory =
                new E2eStubGmailApiClientFactory(
                        "client-id", "client-secret", coreProperties(), null, null);
        gmailApiClientFactory.seedMessage(
                new SeedMessageRequest(
                        "tenant-1",
                        "gmail-message-1",
                        "gmail-thread-1",
                        "sender@example.com",
                        "Receipt",
                        "Inbound body"));
        ReplyHeaders replyHeaders =
                ReplyHeaders.of(
                        "<gmail-message-1@example.com>",
                        "",
                        "Receipt",
                        "sender@example.com",
                        "gmail-thread-1");
        String encodedMimeMessage =
                ReplyMimeBuilder.buildBase64UrlMime(replyHeaders, E2eStubChatModel.CANNED_TEXT);
        Gmail gmail = gmailApiClientFactory.buildGmailClient("access-token");

        gmail.users()
                .drafts()
                .create(
                        "me",
                        new Draft()
                                .setMessage(
                                        new Message()
                                                .setThreadId("gmail-thread-1")
                                                .setRaw(encodedMimeMessage)))
                .execute();

        E2eStubGmailApiClientFactory.SeededDraft seededDraft =
                gmailApiClientFactory.findDraft("gmail-message-1");
        assertThat(seededDraft.body()).contains(E2eStubChatModel.CANNED_TEXT);
        assertThat(seededDraft.body()).doesNotContain("=E2=80=94");
    }

    @Test
    void listMessagesReturnsPageTokenForLazyLoading() throws Exception {
        E2eStubGmailApiClientFactory gmailApiClientFactory =
                new E2eStubGmailApiClientFactory(
                        "client-id", "client-secret", coreProperties(), null, null);
        for (int messageIndex = 1; messageIndex <= 5; messageIndex++) {
            gmailApiClientFactory.seedMessage(
                    new SeedMessageRequest(
                            "tenant-1",
                            "gmail-message-" + messageIndex,
                            "gmail-thread-" + messageIndex,
                            "sender-" + messageIndex + "@example.com",
                            "Subject " + messageIndex,
                            "Body " + messageIndex));
        }
        Gmail gmail = gmailApiClientFactory.buildGmailClient("access-token");

        ListMessagesResponse firstPage =
                gmail.users()
                        .messages()
                        .list("me")
                        .setLabelIds(List.of("INBOX"))
                        .setMaxResults(2L)
                        .execute();
        ListMessagesResponse secondPage =
                gmail.users()
                        .messages()
                        .list("me")
                        .setLabelIds(List.of("INBOX"))
                        .setMaxResults(2L)
                        .setPageToken(firstPage.getNextPageToken())
                        .execute();
        ListMessagesResponse thirdPage =
                gmail.users()
                        .messages()
                        .list("me")
                        .setLabelIds(List.of("INBOX"))
                        .setMaxResults(2L)
                        .setPageToken(secondPage.getNextPageToken())
                        .execute();

        assertThat(firstPage.getMessages()).hasSize(2);
        assertThat(firstPage.getNextPageToken()).isEqualTo("2");
        assertThat(secondPage.getMessages()).hasSize(2);
        assertThat(secondPage.getNextPageToken()).isEqualTo("4");
        assertThat(thirdPage.getMessages()).hasSize(1);
        assertThat(thirdPage.getNextPageToken()).isNull();
    }

    private static ZeroMailCoreProperties coreProperties() {
        return new ZeroMailCoreProperties(
                new ZeroMailCoreProperties.CryptoProperties(
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
                // GmailApiClientFactory dereferences properties.gmail().apiRootUrl() in its
                // constructor — passing null here NPEs before the test body runs.
                new ZeroMailCoreProperties.GmailProperties(
                        "https://gmail.googleapis.com/",
                        URI.create("https://oauth2.googleapis.com/token")),
                null,
                null,
                null);
    }
}
