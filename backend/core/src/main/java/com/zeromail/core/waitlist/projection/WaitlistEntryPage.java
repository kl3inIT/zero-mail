package com.zeromail.core.waitlist.projection;

import java.util.List;

/**
 * Pageable list of {@link WaitlistEntryProjection}. Carries the total element count so the admin UI
 * can render an accurate pager without reading the underlying {@code Page} type.
 */
public record WaitlistEntryPage(
        List<WaitlistEntryProjection> items, long totalElements, int page, int size) {}
