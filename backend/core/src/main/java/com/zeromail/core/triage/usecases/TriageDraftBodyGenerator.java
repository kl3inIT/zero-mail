package com.zeromail.core.triage.usecases;

import java.util.UUID;

public interface TriageDraftBodyGenerator {

    String generate(
            UUID tenantId, String gmailThreadId, String inboundRawHtml, String inboundSubject);
}
