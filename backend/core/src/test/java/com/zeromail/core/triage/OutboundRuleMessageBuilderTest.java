package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.outbound.usecases.ForwardMessageAssembler;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.usecases.OutboundRuleMessageBuilder;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundRuleMessageBuilderTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000008401");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000008402");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, MAILBOX_ID);
    private static final String IDEMPOTENCY_KEY = "triage-audit-8401";
    private static final String SOURCE_MESSAGE_ID = "gmail-message-1";
    private static final String EXPECTED_MESSAGE_ID =
            "<00000000-0000-0000-0000-000000008401.triage-audit-8401@zero-mail.invalid>";

    private final ForwardMessageAssembler forwardMessageAssembler =
            mock(ForwardMessageAssembler.class);
    private final OutboundRuleMessageBuilder messageBuilder =
            new OutboundRuleMessageBuilder(forwardMessageAssembler);

    @Test
    void send_reply_and_send_email_preserve_deterministic_message_id_after_save_changes()
            throws Exception {
        assertMessageId(
                messageBuilder.build(
                        new TriageActionResult.SendReply(
                                "Reply body", "gmail-message-1", "gmail-thread-1"),
                        replyHeaders(),
                        "Original subject",
                        SOURCE_MESSAGE_ID,
                        MAILBOX_REF,
                        TENANT_ID,
                        IDEMPOTENCY_KEY));
        assertMessageId(
                messageBuilder.build(
                        new TriageActionResult.SendEmail(
                                List.of("to@example.com"),
                                List.of("cc@example.com"),
                                List.of("bcc@example.com"),
                                "Status",
                                "Body"),
                        null,
                        null,
                        SOURCE_MESSAGE_ID,
                        MAILBOX_REF,
                        TENANT_ID,
                        IDEMPOTENCY_KEY));
    }

    @Test
    void forward_delegates_to_assembler_with_source_message_and_deterministic_id()
            throws Exception {
        Message assembledForward = new Message().setRaw("forward-raw");
        when(forwardMessageAssembler.buildForward(
                        eq(MAILBOX_REF),
                        eq(SOURCE_MESSAGE_ID),
                        eq(List.of("recipient@example.com")),
                        eq(List.of()),
                        eq("Fwd: Quarterly planning"),
                        eq("Forward body"),
                        eq(EXPECTED_MESSAGE_ID)))
                .thenReturn(assembledForward);

        Message result =
                messageBuilder.build(
                        new TriageActionResult.ForwardEmail(
                                List.of("recipient@example.com"), "Forward body"),
                        null,
                        "Quarterly planning",
                        SOURCE_MESSAGE_ID,
                        MAILBOX_REF,
                        TENANT_ID,
                        IDEMPOTENCY_KEY);

        assertThat(result).isSameAs(assembledForward);
        verify(forwardMessageAssembler)
                .buildForward(
                        eq(MAILBOX_REF),
                        eq(SOURCE_MESSAGE_ID),
                        eq(List.of("recipient@example.com")),
                        eq(List.of()),
                        eq("Fwd: Quarterly planning"),
                        eq("Forward body"),
                        eq(EXPECTED_MESSAGE_ID));
    }

    private static ReplyHeaders replyHeaders() {
        return ReplyHeaders.of(
                "<inbound@example.com>",
                "<previous@example.com>",
                "Original subject",
                "founder@example.com",
                "gmail-thread-1");
    }

    private static void assertMessageId(Message gmailMessage) throws Exception {
        MimeMessage mimeMessage = ReplyMimeBuilder.parseBase64UrlMime(gmailMessage.getRaw());

        assertThat(mimeMessage.getHeader("Message-ID", null)).isEqualTo(EXPECTED_MESSAGE_ID);
    }
}
