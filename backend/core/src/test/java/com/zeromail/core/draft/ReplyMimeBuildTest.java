package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ReplyMimeBuildTest {

    private static final String MIME_MESSAGE = "jakarta.mail.internet.MimeMessage";
    private static final String INBOUND_MESSAGE_ID = "<message-123@example.com>";
    private static final String PRIOR_REFERENCES =
            "<root-message@example.com> <parent-message@example.com>";
    private static final String REPLY_TO_ADDRESS = "founder@example.com";
    private static final String GMAIL_THREAD_ID = "gmail-thread-123";

    @Test
    void jakarta_mail_mime_message_is_available_at_test_runtime() {
        assertThatCode(() -> Class.forName(MIME_MESSAGE)).doesNotThrowAnyException();
    }

    @Test
    void reply_mime_builder_sets_threading_headers_and_base64url_raw() throws Exception {
        String rawMime =
                ReplyMimeBuilder.buildBase64UrlMime(
                        headers("Quarterly planning", PRIOR_REFERENCES, REPLY_TO_ADDRESS),
                        "Thanks, I will review this and reply in Gmail.");
        MimeMessage parsedMime = ReplyMimeBuilder.parseBase64UrlMime(rawMime);

        assertThat(rawMime).doesNotContain("=");
        assertThat(rawMime).matches("[A-Za-z0-9_-]+");
        assertThat(parsedMime.getHeader("In-Reply-To", null)).isEqualTo(INBOUND_MESSAGE_ID);
        assertThat(parsedMime.getHeader("References", null))
                .isEqualTo(PRIOR_REFERENCES + " " + INBOUND_MESSAGE_ID);
        assertThat(parsedMime.getSubject()).isEqualTo("Re: Quarterly planning");
        assertThat(parsedMime.getRecipients(jakarta.mail.Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly(REPLY_TO_ADDRESS);
    }

    @Test
    void reply_mime_builder_handles_reference_and_subject_variants() throws Exception {
        MimeMessage withoutPriorReferences =
                ReplyMimeBuilder.parseBase64UrlMime(
                        ReplyMimeBuilder.buildBase64UrlMime(
                                headers("Re: Existing reply", null, REPLY_TO_ADDRESS), "Body"));

        assertThat(withoutPriorReferences.getHeader("References", null))
                .isEqualTo(INBOUND_MESSAGE_ID);
        assertThat(withoutPriorReferences.getSubject()).isEqualTo("Re: Existing reply");
    }

    @Test
    void utf8_subject_round_trips_without_mojibake() throws Exception {
        String rawMime =
                ReplyMimeBuilder.buildBase64UrlMime(
                        headers("Lịch họp tuần này", null, REPLY_TO_ADDRESS), "Body");
        MimeMessage parsedMime = ReplyMimeBuilder.parseBase64UrlMime(rawMime);
        String decodedRawMime =
                new String(Base64.getUrlDecoder().decode(rawMime), StandardCharsets.UTF_8);

        assertThat(parsedMime.getSubject()).isEqualTo("Re: Lịch họp tuần này");
        assertThat(decodedRawMime).contains("Subject: =?UTF-8?");
    }

    @Test
    void localized_reply_prefixes_are_documented_as_cosmetic_double_prefixes() throws Exception {
        MimeMessage parsedMime =
                ReplyMimeBuilder.parseBase64UrlMime(
                        ReplyMimeBuilder.buildBase64UrlMime(
                                headers("AW: Notes", null, REPLY_TO_ADDRESS), "Body"));

        assertThat(parsedMime.getSubject()).isEqualTo("Re: AW: Notes");
    }

    @Test
    void reply_mime_builder_fails_closed_without_message_id() {
        ReplyHeaders headersWithoutMessageId =
                ReplyHeaders.of(null, null, "Subject", REPLY_TO_ADDRESS, GMAIL_THREAD_ID);

        assertThatThrownBy(
                        () -> ReplyMimeBuilder.buildBase64UrlMime(headersWithoutMessageId, "Body"))
                .isInstanceOf(MissingMessageIdException.class)
                .hasMessage(null);
    }

    @Test
    void reply_mime_builder_strictly_parses_reply_to_address() {
        assertThatThrownBy(
                        () ->
                                ReplyMimeBuilder.buildBase64UrlMime(
                                        headers("Subject", null, "bad address <broken@example.com"),
                                        "Body"))
                .isInstanceOf(AddressException.class);
    }

    @Test
    void reply_headers_require_thread_id_and_reply_to_but_allow_missing_message_id() {
        ReplyHeaders headersWithoutMessageId =
                ReplyHeaders.of(" ", null, "Subject", REPLY_TO_ADDRESS, GMAIL_THREAD_ID);

        assertThat(headersWithoutMessageId.hasMessageId()).isFalse();
        assertThatThrownBy(
                        () ->
                                ReplyHeaders.of(
                                        INBOUND_MESSAGE_ID, null, "Subject", " ", GMAIL_THREAD_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ReplyHeaders.of(
                                        INBOUND_MESSAGE_ID, null, "Subject", REPLY_TO_ADDRESS, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ReplyHeaders headers(String subject, String references, String replyToAddress) {
        return ReplyHeaders.of(
                INBOUND_MESSAGE_ID, references, subject, replyToAddress, GMAIL_THREAD_ID);
    }
}
