package com.zeromail.core.triage.usecases;

import com.zeromail.core.gmail.gateway.MailboxRef;
import java.util.UUID;

public interface TriageDraftBodyGenerator {

    String generate(
            UUID tenantId, String gmailThreadId, String inboundRawHtml, String inboundSubject);

    String generate(
            UUID tenantId,
            MailboxRef mailboxRef,
            String gmailThreadId,
            String inboundRawHtml,
            String inboundSubject);
}
