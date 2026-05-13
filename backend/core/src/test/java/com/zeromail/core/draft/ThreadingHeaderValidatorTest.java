package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import com.zeromail.core.triage.usecases.ThreadingHeaderValidator;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ThreadingHeaderValidatorTest {

    private static final String INBOUND_MESSAGE_ID = "<message-123@example.com>";
    private static final String GMAIL_THREAD_ID = "gmail-thread-123";

    @Test
    void validator_accepts_complete_threading_headers() throws Exception {
        MimeMessage mimeMessage = validMimeMessage();

        assertThatCode(() -> ThreadingHeaderValidator.validate(mimeMessage, GMAIL_THREAD_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void validator_rejects_missing_or_malformed_threading_headers() throws Exception {
        MimeMessage missingHeaders = new MimeMessage(Session.getInstance(new Properties()));
        missingHeaders.setText("Body", "UTF-8");
        missingHeaders.saveChanges();

        assertThatThrownBy(() -> ThreadingHeaderValidator.validate(missingHeaders, GMAIL_THREAD_ID))
                .isInstanceOf(ThreadingHeaderInvalidException.class)
                .hasMessage(null);
    }

    @Test
    void validator_rejects_subject_without_reply_prefix() throws Exception {
        MimeMessage mimeMessage = validMimeMessage();
        mimeMessage.setSubject("Plain subject", "UTF-8");
        mimeMessage.saveChanges();

        assertThatThrownBy(() -> ThreadingHeaderValidator.validate(mimeMessage, GMAIL_THREAD_ID))
                .isInstanceOf(ThreadingHeaderInvalidException.class);
    }

    @Test
    void validator_rejects_thread_id_mismatch_before_drafts_create() throws Exception {
        MimeMessage mimeMessage = validMimeMessage();
        Message gmailMessage = new Message().setThreadId("wrong-thread");

        assertThatThrownBy(
                        () ->
                                ThreadingHeaderValidator.validate(
                                        mimeMessage, gmailMessage, GMAIL_THREAD_ID))
                .isInstanceOf(ThreadingHeaderInvalidException.class);
    }

    private static MimeMessage validMimeMessage() throws Exception {
        return ReplyMimeBuilder.buildMimeMessage(
                ReplyHeaders.of(
                        INBOUND_MESSAGE_ID,
                        "<root-message@example.com>",
                        "Planning",
                        "founder@example.com",
                        GMAIL_THREAD_ID),
                "Body");
    }
}
