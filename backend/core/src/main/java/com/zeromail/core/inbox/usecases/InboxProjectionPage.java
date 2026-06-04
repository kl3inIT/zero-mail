package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import java.util.List;
import java.util.Objects;

/**
 * Result of {@link InboxProjectionReadService#fetchInboxPage(java.util.UUID, String, int)}. The
 * {@code dataSource} discriminator is locked to {@link InboxProjectionDataSource#PROJECTION} for
 * Wave 0; the orchestrator (Wave 1) wraps fallbacks with the other enum values.
 */
public record InboxProjectionPage(
        List<InboxProjectionMessage> items,
        String nextCursor,
        InboxProjectionDataSource dataSource) {

    public InboxProjectionPage {
        items = List.copyOf(items);
        Objects.requireNonNull(dataSource, "dataSource must not be null");
    }
}
