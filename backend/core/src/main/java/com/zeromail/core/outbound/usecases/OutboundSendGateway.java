package com.zeromail.core.outbound.usecases;

import java.io.IOException;

public interface OutboundSendGateway {

    OutboundSendResult send(OutboundSendCommand command) throws IOException;
}
