package com.zeromail.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.analytics.domain.TimeSavedWeights;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeSavedWeightsTest {

    @Test
    void computes_seconds_from_known_applied_action_types() {
        long seconds =
                TimeSavedWeights.computeSeconds(
                        Map.of("label", 5L, "archive", 2L, "save_draft", 1L));

        assertThat(seconds).isEqualTo(290);
        assertThat(TimeSavedWeights.SAVE_DRAFT_SECONDS).isEqualTo(180);
    }

    @Test
    void ignores_unknown_action_types_and_empty_maps() {
        assertThat(TimeSavedWeights.computeSeconds(Map.of("unknown", 99L))).isZero();
        assertThat(TimeSavedWeights.computeSeconds(Map.of())).isZero();
    }
}
