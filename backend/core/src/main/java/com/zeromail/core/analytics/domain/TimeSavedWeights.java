package com.zeromail.core.analytics.domain;

import java.util.Map;
import java.util.Objects;

public final class TimeSavedWeights {

    public static final int LABEL_SECONDS = 10;
    public static final int ARCHIVE_SECONDS = 30;
    public static final int SAVE_DRAFT_SECONDS = 180;

    private static final String LABEL_ACTION_TYPE = "label";
    private static final String ARCHIVE_ACTION_TYPE = "archive";
    private static final String SAVE_DRAFT_ACTION_TYPE = "save_draft";

    private TimeSavedWeights() {}

    public static long computeSeconds(Map<String, Long> appliedByActionType) {
        Objects.requireNonNull(appliedByActionType, "appliedByActionType must not be null");
        return secondsFor(appliedByActionType, LABEL_ACTION_TYPE, LABEL_SECONDS)
                + secondsFor(appliedByActionType, ARCHIVE_ACTION_TYPE, ARCHIVE_SECONDS)
                + secondsFor(appliedByActionType, SAVE_DRAFT_ACTION_TYPE, SAVE_DRAFT_SECONDS);
    }

    private static long secondsFor(
            Map<String, Long> appliedByActionType, String actionType, int weightSeconds) {
        return appliedByActionType.getOrDefault(actionType, 0L) * weightSeconds;
    }
}
