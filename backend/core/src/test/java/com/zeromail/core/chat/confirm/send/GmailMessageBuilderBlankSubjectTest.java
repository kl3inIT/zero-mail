package com.zeromail.core.chat.confirm.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.shared.privacy.Sensitive;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression: an empty subject must produce a valid Gmail MIME message with no Subject header (so
 * Gmail renders "(no subject)", matching native compose) instead of throwing and blocking the send.
 */
class GmailMessageBuilderBlankSubjectTest {

    private final GmailMessageBuilder gmailMessageBuilder = new GmailMessageBuilder();

    @Test
    void blank_subject_builds_mime_without_subject_header() throws Exception {
        Message gmailMessage =
                gmailMessageBuilder.build(command(""), "<message-blank@zero-mail.invalid>");

        assertThat(parseSubject(gmailMessage)).isNull();
    }

    @Test
    void null_subject_does_not_throw() {
        assertThatCode(
                        () ->
                                gmailMessageBuilder.build(
                                        command(null), "<message-null@zero-mail.invalid>"))
                .doesNotThrowAnyException();
    }

    @Test
    void non_blank_subject_is_preserved() throws Exception {
        Message gmailMessage =
                gmailMessageBuilder.build(
                        command("Quarterly planning"), "<message-set@zero-mail.invalid>");

        assertThat(parseSubject(gmailMessage)).isEqualTo("Quarterly planning");
    }

    private static String parseSubject(Message gmailMessage) throws Exception {
        byte[] decodedMime = Base64.getUrlDecoder().decode(gmailMessage.getRaw());
        MimeMessage parsedMessage =
                new MimeMessage(
                        Session.getInstance(new Properties(), null),
                        new ByteArrayInputStream(decodedMime));
        return parsedMessage.getSubject();
    }

    private static AssistantSendCommand command(String subject) {
        return new AssistantSendCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tool-send-blank-subject",
                ChatToolName.SEND_EMAIL,
                "recipient@acme.test",
                null,
                null,
                subject,
                Sensitive.of("Body text the user reviewed."),
                null,
                null,
                null,
                false,
                Map.of("state", "preview"),
                "confirm-test-blank-subject");
    }
}
