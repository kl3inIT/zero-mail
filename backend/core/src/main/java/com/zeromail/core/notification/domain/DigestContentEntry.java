package com.zeromail.core.notification.domain;

/**
 * One message line inside a {@link DigestContentSection}. The sender and subject are sanitized
 * audit metadata; the summary is a one-line gist generated fresh at send time and never persisted.
 * {@code summary} is {@code null} when summarization was unavailable or did not cover this message
 * — the digest still shows sender and subject.
 */
public record DigestContentEntry(String senderEmail, String subject, String summary) {

    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }
}
