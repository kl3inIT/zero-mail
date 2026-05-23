package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.usecases.OutboundRuleMessageBuilder;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundRuleMessageBuilderTest {

    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000008401");
    private static final String IDEMPOTENCY_KEY = "triage-audit-8401";
    private static final String EXPECTED_MESSAGE_ID =
            "<00000000-0000-0000-0000-000000008401.triage-audit-8401@zero-mail.invalid>";

    private final OutboundRuleMessageBuilder messageBuilder = new OutboundRuleMessageBuilder();

    @Test
    void outbound_rule_messages_preserve_deterministic_message_id_after_save_changes()
            throws Exception {
        assertMessageId(
                messageBuilder.build(
                        new TriageActionResult.SendReply(
                                "Reply body", "gmail-message-1", "gmail-thread-1"),
                        replyHeaders(),
                        "Original subject",
                        TENANT_ID,
                        IDEMPOTENCY_KEY));
        assertMessageId(
                messageBuilder.build(
                        new TriageActionResult.ForwardEmail(
                                List.of("recipient@example.com"), "Forward body"),
                        null,
                        "Quarterly planning",
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
                        TENANT_ID,
                        IDEMPOTENCY_KEY));
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
