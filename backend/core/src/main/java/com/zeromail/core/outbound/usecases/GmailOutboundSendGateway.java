package com.zeromail.core.outbound.usecases;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
@AllowedSendCallSite
public class GmailOutboundSendGateway implements OutboundSendGateway {

    private static final String USER_ID = "me";

    private final GmailApiClientFactory gmailApiClientFactory;

    public GmailOutboundSendGateway(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory =
                Objects.requireNonNull(gmailApiClientFactory, "gmailApiClientFactory");
    }

    @Override
    public OutboundSendResult send(OutboundSendCommand command) throws IOException {
        Objects.requireNonNull(command, "command must not be null");
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForMailbox(command.mailboxRef());
            Message sendResult =
                    gmail.users().messages().send(USER_ID, command.gmailMessage()).execute();
            return new OutboundSendResult(messageId(sendResult), threadId(sendResult));
        } catch (InvalidGrantException | IllegalStateException sendFailure) {
            throw new OutboundSendException(sendFailure);
        }
    }

    private static String messageId(Message sendResult) {
        return sendResult == null ? null : sendResult.getId();
    }

    private static String threadId(Message sendResult) {
        return sendResult == null ? null : sendResult.getThreadId();
    }
}
