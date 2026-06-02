package com.zeromail.core.notification.domain;

import java.util.List;

/**
 * One IZ-style group of messages a single rule filed into the digest this week, used to render the
 * content section of the weekly digest email. Grouped by the rule-name snapshot captured at triage
 * time.
 */
public record DigestContentSection(String ruleName, List<DigestContentEntry> entries) {

    public DigestContentSection {
        entries = List.copyOf(entries);
    }
}
