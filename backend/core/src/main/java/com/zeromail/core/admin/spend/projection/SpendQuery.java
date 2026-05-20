package com.zeromail.core.admin.spend.projection;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Request shape for {@code SpendAggregateQueryService}. The from/to window must be 90 days or less
 * per OPS-SPEND-01 + T-08-53 (DoS mitigation).
 *
 * @param from inclusive start of the range
 * @param to exclusive end of the range
 * @param providers optional filter — empty/absent means all providers
 * @param features optional filter — empty/absent means all features
 */
public record SpendQuery(
        Instant from,
        Instant to,
        Optional<Set<LlmProvider>> providers,
        Optional<Set<Feature>> features) {

    /** Maximum range per OPS-SPEND-01. Server-side guard; client-side guard fires earlier. */
    public static final Duration MAX_RANGE = Duration.ofDays(90);

    public SpendQuery {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        providers = providers == null ? Optional.empty() : providers;
        features = features == null ? Optional.empty() : features;
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
        Duration range = Duration.between(from, to);
        if (range.compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException(
                    "error.admin.spend_range_too_wide: requested range "
                            + range.toDays()
                            + " days exceeds maximum "
                            + MAX_RANGE.toDays()
                            + " days");
        }
    }
}
