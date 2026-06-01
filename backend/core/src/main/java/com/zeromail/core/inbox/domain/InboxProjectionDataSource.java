package com.zeromail.core.inbox.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Where the inbox list page was served from. Becomes the {@code dataSource} discriminator on the
 * Gmail inbox page response in Phase B Wave 3; the frontend renders a "syncing" banner for {@link
 * #SYNCING} and logs (no UI change) for {@link #LIVE_GMAIL}.
 *
 * <ul>
 *   <li>{@link #PROJECTION} — happy path; rows came from {@code gmail_inbox_projection}.
 *   <li>{@link #LIVE_GMAIL} — fallback; projection lacked enough fresh rows so the orchestrator
 *       (Wave 1) called Gmail directly.
 *   <li>{@link #SYNCING} — projection empty and backfill in flight; frontend shows the syncing
 *       indicator instead of a list.
 * </ul>
 */
public enum InboxProjectionDataSource implements IdentifiedEnum {
    PROJECTION,
    LIVE_GMAIL,
    SYNCING;

    @Override
    public String id() {
        return name();
    }

    public static InboxProjectionDataSource fromId(String id) {
        return Stream.of(values())
                .filter(source -> source.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown InboxProjectionDataSource id: " + id));
    }
}
