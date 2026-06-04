package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.SenderTimelineEntry;
import java.time.LocalDate;

public record SenderTimelineEntryResponse(LocalDate date, long count) {

    public static SenderTimelineEntryResponse from(SenderTimelineEntry entry) {
        return new SenderTimelineEntryResponse(entry.date(), entry.count());
    }
}
