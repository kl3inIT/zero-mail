package com.zeromail.api.controllers.cleanup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.zeromail.api.dto.cleanup.CleanupSenderActionRequest;
import com.zeromail.core.cleanup.usecases.CandidateQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanupControllerValidationTest {

    @Test
    void candidates_partialDateRangeFailsBeforeTenantLookup() {
        UnsubscribeCandidateController unsubscribeCandidateController =
                new UnsubscribeCandidateController(mock(CandidateQueryService.class));

        assertThatThrownBy(
                        () ->
                                unsubscribeCandidateController.listCandidates(
                                        null, "2026-06-01", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startDate and endDate must be supplied together");
    }

    @Test
    void labelFutureActionRequiresLabelName() {
        CleanupSenderActionRequest cleanupSenderActionRequest =
                new CleanupSenderActionRequest(
                        CleanupSenderActionRequest.Action.LABEL_FUTURE,
                        List.of("sender@example.test"),
                        " ");

        assertThatThrownBy(
                        () ->
                                CleanupSenderActionController.validateRequest(
                                        cleanupSenderActionRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("labelName is required for LABEL_FUTURE");
    }
}
