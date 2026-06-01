package com.zeromail.core.inbox.usecases;

import java.util.Objects;

/**
 * Mirror of {@code RecentInboxReadService.RecentInboxLabel} for projection-sourced rows. The
 * projection only stores label IDs ({@code label_ids} text[]); the {@code name} is empty until the
 * orchestrator (Wave 1) optionally enriches it from Gmail's labels.list cache.
 */
public record InboxProjectionLabel(String id, String name) {

    public InboxProjectionLabel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
