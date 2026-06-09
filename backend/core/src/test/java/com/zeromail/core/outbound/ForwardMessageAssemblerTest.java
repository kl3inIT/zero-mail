package com.zeromail.core.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.outbound.usecases.ForwardMessageAssembler;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForwardMessageAssemblerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000091f0");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-0000000091f1");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, MAILBOX_ID);
    private static final String SOURCE_MESSAGE_ID = "gmail-source-1";
    private static final String MESSAGE_ID =
            "<00000000-0000-0000-0000-0000000091f0.k1@zero-mail.invalid>";
    private static final String ORIGINAL_BODY = "The original quarterly numbers are attached.";
    private static final String ORIGINAL_SUBJECT = "Q3 numbers";

    private final GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
    private final ForwardMessageAssembler assembler =
            new ForwardMessageAssembler(gmailApiClientFactory);

    @Test
    void forward_embeds_original_message_as_rfc822_and_preserves_message_id() throws Exception {
        stubRawSource(originalRawBase64Url());

        Message forward =
                assembler.buildForward(
                        MAILBOX_REF,
                        SOURCE_MESSAGE_ID,
                        List.of("recipient@example.com"),
                        List.of(),
                        "Fwd: " + ORIGINAL_SUBJECT,
                        "Sharing this with you.",
                        MESSAGE_ID);

        MimeMessage built = parse(forward.getRaw());
        assertThat(built.getHeader("Message-ID", null)).isEqualTo(MESSAGE_ID);
        assertThat(built.getSubject()).isEqualTo("Fwd: " + ORIGINAL_SUBJECT);
        assertThat(built.getRecipients(jakarta.mail.Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("recipient@example.com");

        // The forward must carry the ORIGINAL message — the bug was that it carried only the note.
        assertThat(built.getContentType()).startsWith("multipart/mixed");
        MimeMultipart multipart = (MimeMultipart) built.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat((String) multipart.getBodyPart(0).getContent())
                .isEqualTo("Sharing this with you.");
        assertThat(multipart.getBodyPart(1).getContentType()).startsWith("message/rfc822");

        MimeMessage embeddedOriginal = (MimeMessage) multipart.getBodyPart(1).getContent();
        assertThat(embeddedOriginal.getSubject()).isEqualTo(ORIGINAL_SUBJECT);
        assertThat(((String) embeddedOriginal.getContent())).contains(ORIGINAL_BODY);
    }

    @Test
    void blank_note_still_forwards_the_original() throws Exception {
        stubRawSource(originalRawBase64Url());

        Message forward =
                assembler.buildForward(
                        MAILBOX_REF,
                        SOURCE_MESSAGE_ID,
                        List.of("recipient@example.com"),
                        List.of(),
                        "Fwd: " + ORIGINAL_SUBJECT,
                        "",
                        MESSAGE_ID);

        MimeMessage built = parse(forward.getRaw());
        MimeMultipart multipart = (MimeMultipart) built.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat(multipart.getBodyPart(1).getContentType()).startsWith("message/rfc822");
    }

    private void stubRawSource(String rawBase64Url) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.Get getRequest = mock(Gmail.Users.Messages.Get.class);
        when(gmailApiClientFactory.buildClientForMailbox(MAILBOX_REF)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.get(eq("me"), eq(SOURCE_MESSAGE_ID))).thenReturn(getRequest);
        when(getRequest.setFormat(any())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new Message().setRaw(rawBase64Url));
    }

    private static String originalRawBase64Url() throws Exception {
        MimeMessage original = new MimeMessage(Session.getInstance(new Properties(), null));
        original.setFrom(new jakarta.mail.internet.InternetAddress("boss@example.com"));
        original.setRecipients(
                jakarta.mail.Message.RecipientType.TO,
                jakarta.mail.internet.InternetAddress.parse("me@example.com", false));
        original.setSubject(ORIGINAL_SUBJECT, StandardCharsets.UTF_8.name());
        original.setText(ORIGINAL_BODY, StandardCharsets.UTF_8.name());
        original.saveChanges();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
    }

    private static MimeMessage parse(String rawBase64Url) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(rawBase64Url);
        return new MimeMessage(
                Session.getInstance(new Properties(), null), new ByteArrayInputStream(decoded));
    }
}
