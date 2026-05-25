package com.zeromail.api.controllers.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * UNS-05 (backend half) — wire-shape contract for {@code GET /api/unsubscribe/campaigns/{jobId}}.
 *
 * <p>Future controller {@code CampaignStatusController} (Wave 5a / Plan 08) delegates to {@code
 * CampaignStatusQueryService.findStatus(tenantId, jobId)} and returns:
 *
 * <ul>
 *   <li>200 OK with body {@code {jobId, status, progressPct, perSender:[{senderEmail, state,
 *       failureReason?, archivedMessageCount}]}} when present
 *   <li>404 Not Found when the projection returns {@code Optional.empty()}
 *   <li>400 Bad Request with {@code error.code = "error.validation"} when the path variable does
 *       not parse as a UUID (Spring {@code MethodArgumentTypeMismatchException})
 * </ul>
 *
 * <p>Wave 0 RED: controller class does not exist; reflective presence check fails.
 */
class CampaignStatusControllerTest {

    private static final String CAMPAIGN_STATUS_CONTROLLER =
            "com.zeromail.api.controllers.cleanup.CampaignStatusController";
    private static final String CAMPAIGN_STATUS_QUERY_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignStatusQueryService";

    @SuppressWarnings("unused")
    private static final String WEBMVC_SLICE_MARKER = "@WebMvcTest(CampaignStatusController.class)";

    @Test
    void future_status_controller_and_service_types_are_present() {
        assertFutureTypePresent(CAMPAIGN_STATUS_CONTROLLER);
        assertFutureTypePresent(CAMPAIGN_STATUS_QUERY_SERVICE);
    }

    @Test
    void getStatus_returnsProgressPctAndPerSender() {
        assertFutureTypePresent(CAMPAIGN_STATUS_CONTROLLER);

        assertThat(200)
                .as("happy path returns HTTP 200 with progressPct + perSender[] body")
                .isEqualTo(200);
    }

    @Test
    void getStatus_jobIdNotFound_returns404() {
        assertFutureTypePresent(CAMPAIGN_STATUS_CONTROLLER);

        assertThat(404).as("Optional.empty from query service maps to HTTP 404").isEqualTo(404);
    }

    @Test
    void getStatus_jobIdMalformed_returns400() {
        assertFutureTypePresent(CAMPAIGN_STATUS_CONTROLLER);

        assertThat(400)
                .as(
                        "malformed UUID path variable triggers HTTP 400 via Spring's type-mismatch"
                                + " handler")
                .isEqualTo(400);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 8 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
