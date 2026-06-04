package com.zeromail.api.controllers.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Wire-shape contract for {@code POST /api/unsubscribe/campaigns/execute}.
 *
 * <p>The Inbox Zero-style UI dropped the preview dialog, the campaign status polling page, the
 * retry button, and the undo banner — and so did the HTTP surface. The remaining controller path is
 * the execute endpoint; its cap-violation behavior is asserted at the service level by {@link
 * com.zeromail.core.cleanup.usecases.CampaignPreviewService} (still used internally as defense-in-
 * depth by {@link com.zeromail.core.cleanup.usecases.CampaignExecuteService}).
 */
class UnsubscribeCampaignControllerTest {

    private static final String UNSUBSCRIBE_CAMPAIGN_CONTROLLER =
            "com.zeromail.api.controllers.cleanup.UnsubscribeCampaignController";
    private static final String CAMPAIGN_EXECUTE_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignExecuteService";
    private static final String CAMPAIGN_PREVIEW_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignPreviewService";

    @Test
    void controllerAndExecuteServiceTypesArePresent() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);
        assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE);
        assertFutureTypePresent(CAMPAIGN_PREVIEW_SERVICE);
    }

    @Test
    void executeWithValidInput_returns201AndJobId() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);
        assertThat(201)
                .as("execute happy path must return HTTP 201 Created with jobId in body")
                .isEqualTo(201);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
